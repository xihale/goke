package app.mintcal.data

import app.mintcal.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** 一次版本检查的结果。 */
data class ReleaseInfo(
    val version: String,
    val url: String,
    val notes: String?,
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Newer(
        val info: ReleaseInfo,
    ) : UpdateCheckResult

    data object Error : UpdateCheckResult
}

/**
 * 从 GitHub Releases 检查最新版本。
 *
 * - 零额外依赖：复用已有的 kotlinx-serialization + java.net.HttpURLConnection。
 * - 支持 SemVer 预发布版（如 0.0.1-beta.1）：当前为预发布版时同时追踪预发布与正式版；当前为正式版时仅提醒正式版。
 * - 默认按天静默检查，仅在有前台 UI 且到周期时触发。
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val fetchReleasesJson: suspend () -> String = { defaultFetchReleasesJson() },
) {
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** 当前安装包版本（去掉可能的 v 前缀）。 */
    fun currentVersion(): String = currentVersionProvider().removePrefix("v").removePrefix("V")

    /** 判断候选版本是否高于当前安装版本。 */
    fun isNewerThanCurrent(candidateVersion: String): Boolean = isNewerVersion(candidateVersion, currentVersion())

    /** 覆盖安装升级后清理已失效的待更新标记，避免离线时仍误弹旧版本提示。 */
    fun clearStalePendingUpdate() {
        val pending = settings.value.pendingUpdateVersion
        if (pending.isNotBlank() && !isNewerVersion(pending, currentVersion())) {
            settings.update {
                it.copy(
                    pendingUpdateVersion = "",
                    pendingUpdateUrl = "",
                    pendingUpdateNotes = "",
                    dismissedUpdateVersion = "",
                )
            }
        }
    }

    /** 标记某版本在自动弹窗中已被用户处理（去更新 / 以后再说），同版本不再自动弹窗。 */
    fun dismissUpdatePrompt(version: String) {
        if (version.isBlank()) return
        settings.update { it.copy(dismissedUpdateVersion = version) }
    }

    /** 前台启动时按设置周期静默检查更新，失败不上报。 */
    suspend fun maybeAutoCheck(nowMs: Long = System.currentTimeMillis()): UpdateCheckResult? =
        withContext(Dispatchers.IO) {
            clearStalePendingUpdate()
            val st = settings.value
            val periodMs =
                when (st.updateInterval) {
                    UpdateInterval.OFF -> return@withContext null
                    UpdateInterval.STARTUP -> 0L
                    UpdateInterval.DAILY -> TimeUnit.DAYS.toMillis(1)
                    UpdateInterval.WEEKLY -> TimeUnit.DAYS.toMillis(7)
                }
            if (periodMs > 0L && nowMs - st.lastUpdateCheckAt < periodMs) return@withContext null
            checkNow(nowMs)
        }

    /** 立即检查一次更新并更新设置中的待更新状态。 */
    suspend fun checkNow(nowMs: Long = System.currentTimeMillis()): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            _checking.value = true
            try {
                val response = fetchReleasesJson()
                val releases = json.decodeFromString<List<GithubRelease>>(response)
                val current = currentVersion()
                val allowPrerelease = parseSemVer(current)?.preRelease?.isNotEmpty() == true

                val best =
                    releases
                        .asSequence()
                        .filter { !it.draft && it.tagName.isNotBlank() }
                        .mapNotNull { release ->
                            val version = release.tagName.removePrefix("v").removePrefix("V")
                            val parsed = parseSemVer(version) ?: return@mapNotNull null
                            val isPre = release.prerelease || parsed.preRelease.isNotEmpty()
                            if (!allowPrerelease && isPre) return@mapNotNull null
                            if (!isNewerVersion(version, current)) return@mapNotNull null
                            Triple(parsed, version, release)
                        }.maxWithOrNull { a, b -> compareSemVer(a.first, b.first) }

                if (best != null) {
                    val info =
                        ReleaseInfo(
                            version = best.second,
                            url = best.third.htmlUrl,
                            notes =
                                best.third.body
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() },
                        )
                    settings.update {
                        it.copy(
                            lastUpdateCheckAt = nowMs,
                            pendingUpdateVersion = info.version,
                            pendingUpdateUrl = info.url,
                            pendingUpdateNotes = info.notes.orEmpty(),
                        )
                    }
                    UpdateCheckResult.Newer(info)
                } else {
                    settings.update {
                        it.copy(
                            lastUpdateCheckAt = nowMs,
                            pendingUpdateVersion = "",
                            pendingUpdateUrl = "",
                            pendingUpdateNotes = "",
                            dismissedUpdateVersion = "",
                        )
                    }
                    UpdateCheckResult.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 网络、解析或 captive portal 异常一律降级，避免冷启动或手动检查崩溃
                UpdateCheckResult.Error
            } finally {
                _checking.value = false
            }
        }

    companion object {
        const val PROJECT_URL = "https://github.com/xihale/mintcal"
        private const val RELEASES_API_URL = "https://api.github.com/repos/xihale/mintcal/releases?per_page=10"
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private suspend fun defaultFetchReleasesJson(): String =
            withContext(Dispatchers.IO) {
                var conn: HttpURLConnection? = null
                try {
                    conn = URL(RELEASES_API_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "mintcal-android")
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    conn.inputStream.use { it.readBytes().decodeToString() }
                } finally {
                    conn?.disconnect()
                }
            }
    }
}

internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
)

/** 去掉 git describe（-<n>-g<sha>、-dirty）与构建变体（-debug、-bench）后缀。 */
internal fun stripBuildSuffixes(raw: String): String =
    raw
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .replace(Regex("""-(debug|bench)$"""), "")
        .replace(Regex("""-dirty$"""), "")
        .replace(Regex("""-\d+-g[0-9a-fA-F]+$"""), "")

internal fun parseSemVer(raw: String): SemVer? {
    val clean = stripBuildSuffixes(raw)
    if (clean.isEmpty()) return null
    val dash = clean.indexOf('-')
    val coreStr = if (dash >= 0) clean.substring(0, dash) else clean
    val preStr = if (dash >= 0) clean.substring(dash + 1) else null

    val parts = coreStr.split('.')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
        return null
    }
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val pre =
        if (preStr == null) {
            emptyList()
        } else {
            val tokens = preStr.split('.')
            if (tokens.any { it.isEmpty() }) return null
            tokens
        }
    return SemVer(major, minor, patch, pre)
}

internal fun compareSemVer(
    a: SemVer,
    b: SemVer,
): Int {
    if (a.major != b.major) return a.major.compareTo(b.major)
    if (a.minor != b.minor) return a.minor.compareTo(b.minor)
    if (a.patch != b.patch) return a.patch.compareTo(b.patch)

    val aPre = a.preRelease
    val bPre = b.preRelease
    if (aPre.isEmpty() && bPre.isEmpty()) return 0
    if (aPre.isEmpty()) return 1
    if (bPre.isEmpty()) return -1

    val minSize = minOf(aPre.size, bPre.size)
    for (i in 0 until minSize) {
        val ai = aPre[i]
        val bi = bPre[i]
        if (ai == bi) continue
        val an = ai.toIntOrNull()
        val bn = bi.toIntOrNull()
        return when {
            an != null && bn != null -> an.compareTo(bn)
            an != null -> -1
            bn != null -> 1
            else -> ai.compareTo(bi)
        }
    }
    return aPre.size.compareTo(bPre.size)
}

/** 按 SemVer 2.0 比较版本号，支持预发布段及本地 git describe 后缀；无法解析时返回 false。 */
internal fun isNewerVersion(
    latest: String,
    current: String,
): Boolean {
    val l = parseSemVer(latest) ?: return false
    val c = parseSemVer(current) ?: return false
    return compareSemVer(l, c) > 0
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val body: String? = null,
)
