package app.goke.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.goke.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 一次版本检查的结果。 */
data class ReleaseInfo(
    val version: String,
    val url: String,
    val notes: String?,
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val apkSha256: String = "",
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Newer(
        val info: ReleaseInfo,
    ) : UpdateCheckResult

    data object Error : UpdateCheckResult
}

enum class DownloadError {
    NETWORK,
    VERIFY,
    UNAVAILABLE,
}

enum class InstallLaunchResult {
    LAUNCHED,
    NEEDS_PERMISSION,
    FAILED,
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val version: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateDownloadState

    data class ReadyToInstall(
        val version: String,
        val file: File,
        val needsInstallPermission: Boolean = false,
    ) : UpdateDownloadState

    data class Failed(
        val version: String,
        val error: DownloadError,
    ) : UpdateDownloadState
}

/**
 * 从落地页（goke.xeed.ink）检查最新版本并下载安装 ARMv8 APK。
 *
 * - 零额外依赖：复用已有的 kotlinx-serialization + java.net.HttpURLConnection。
 * - 支持 SemVer 预发布版（如 0.0.1-beta.3）：当前为预发布版时同时追踪预发布与正式版；当前为正式版时仅提醒正式版。
 * - 下载落入 cacheDir/updates/ 并校验 SHA-256，随后通过 FileProvider 调起系统安装器。
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val fetchReleasesJson: suspend () -> String = { defaultFetchReleasesJson() },
    private val openDownloadStream: suspend (String) -> RemoteStream = { url -> defaultOpenDownloadStream(url) },
) {
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    /** 当前安装包版本（去掉可能的 v 前缀）。 */
    fun currentVersion(): String = currentVersionProvider().removePrefix("v").removePrefix("V")

    /** 判断候选版本是否高于当前安装版本。 */
    fun isNewerThanCurrent(candidateVersion: String): Boolean = isNewerVersion(candidateVersion, currentVersion())

    /** 覆盖安装升级后清理已失效的待更新标记与过期缓存包，避免离线时仍误弹旧版本提示。 */
    fun clearStalePendingUpdate(cacheDir: File? = null) {
        val pending = settings.value.pendingUpdateVersion
        if (pending.isNotBlank() && !isNewerVersion(pending, currentVersion())) {
            settings.update {
                it.copy(
                    pendingUpdateVersion = "",
                    pendingUpdateUrl = "",
                    pendingUpdateNotes = "",
                    pendingUpdateApkUrl = "",
                    pendingUpdateApkSize = 0L,
                    pendingUpdateApkSha256 = "",
                    dismissedUpdateVersion = "",
                )
            }
            cacheDir?.let { File(it, UPDATES_DIR).deleteRecursively() }
        }
    }

    /** 标记某版本在自动弹窗中已被用户处理，同版本不再自动弹窗。 */
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
                val releases = parseManifest(response)
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
                    val release = best.third
                    val info =
                        ReleaseInfo(
                            version = best.second,
                            url = release.htmlUrl.ifBlank { LANDING_URL },
                            notes =
                                release.body
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() },
                            apkUrl = release.apkUrl.trim(),
                            apkSize = release.apkSize.coerceAtLeast(0L),
                            apkSha256 = release.apkSha256.trim().lowercase(),
                        )
                    settings.update {
                        it.copy(
                            lastUpdateCheckAt = nowMs,
                            pendingUpdateVersion = info.version,
                            pendingUpdateUrl = info.url,
                            pendingUpdateNotes = info.notes.orEmpty(),
                            pendingUpdateApkUrl = info.apkUrl,
                            pendingUpdateApkSize = info.apkSize,
                            pendingUpdateApkSha256 = info.apkSha256,
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
                            pendingUpdateApkUrl = "",
                            pendingUpdateApkSize = 0L,
                            pendingUpdateApkSha256 = "",
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

    /**
     * 下载指定版本 APK 到 [updatesDir] 并校验大小与 SHA-256。
     * 若本地已有同版本且校验通过的 APK，直接复用避免重复流量消耗。
     */
    suspend fun downloadApk(
        info: ReleaseInfo,
        updatesDir: File,
    ): File? =
        withContext(Dispatchers.IO) {
            val active = _downloadState.value
            if (active is UpdateDownloadState.Downloading && active.version == info.version) {
                return@withContext null
            }
            val url = info.apkUrl.trim()
            if (url.isBlank()) {
                _downloadState.value = UpdateDownloadState.Failed(info.version, DownloadError.UNAVAILABLE)
                return@withContext null
            }

            updatesDir.mkdirs()
            val safeVer = info.version.replace(Regex("""[^0-9A-Za-z._-]"""), "_")
            val targetFile = File(updatesDir, "Goke-$safeVer-arm64-v8a.apk")
            if (isCachedApkValid(targetFile, info.apkSize, info.apkSha256)) {
                pruneOtherFiles(updatesDir, keepName = targetFile.name)
                _downloadState.value = UpdateDownloadState.ReadyToInstall(info.version, targetFile)
                return@withContext targetFile
            }

            val partFile = File(updatesDir, "${targetFile.name}.part")
            partFile.delete()
            _downloadState.value = UpdateDownloadState.Downloading(info.version, 0L, info.apkSize)

            try {
                val remote = openDownloadStream(url)
                val total = remote.contentLength.takeIf { it > 0L } ?: info.apkSize
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesRead = 0L
                var lastReportBytes = 0L
                var lastReportAt = System.currentTimeMillis()

                remote.stream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            bytesRead += n
                            val now = System.currentTimeMillis()
                            if (bytesRead - lastReportBytes >= 64 * 1024 || now - lastReportAt >= 120L) {
                                _downloadState.value = UpdateDownloadState.Downloading(info.version, bytesRead, total)
                                lastReportBytes = bytesRead
                                lastReportAt = now
                            }
                        }
                    }
                }
                remote.close()

                val expectedSha = info.apkSha256.trim().lowercase()
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                val sizeMismatch = info.apkSize > 0L && bytesRead != info.apkSize
                val shaMismatch = expectedSha.isNotEmpty() && actualSha != expectedSha
                if (bytesRead <= 0L || sizeMismatch || shaMismatch) {
                    partFile.delete()
                    _downloadState.value = UpdateDownloadState.Failed(info.version, DownloadError.VERIFY)
                    return@withContext null
                }

                pruneOtherFiles(updatesDir, keepName = partFile.name)
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.delete()
                    _downloadState.value = UpdateDownloadState.Failed(info.version, DownloadError.NETWORK)
                    return@withContext null
                }
                _downloadState.value = UpdateDownloadState.ReadyToInstall(info.version, targetFile)
                targetFile
            } catch (e: CancellationException) {
                partFile.delete()
                _downloadState.value = UpdateDownloadState.Idle
                throw e
            } catch (_: Exception) {
                partFile.delete()
                _downloadState.value = UpdateDownloadState.Failed(info.version, DownloadError.NETWORK)
                null
            }
        }

    /** 下载（若尚未就绪）并调起 Android 系统包安装器。 */
    suspend fun downloadAndInstall(
        context: Context,
        info: ReleaseInfo,
    ): InstallLaunchResult {
        val file = downloadApk(info, File(context.cacheDir, UPDATES_DIR)) ?: return InstallLaunchResult.FAILED
        return launchSystemInstaller(context, info.version, file)
    }

    /** 调起系统安装器安装已下载好的 [apkFile]；若未授权“安装未知应用”，引导至系统授权页。 */
    fun launchSystemInstaller(
        context: Context,
        version: String,
        apkFile: File,
    ): InstallLaunchResult {
        if (!apkFile.exists()) {
            _downloadState.value = UpdateDownloadState.Failed(version, DownloadError.NETWORK)
            return InstallLaunchResult.FAILED
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _downloadState.value =
                UpdateDownloadState.ReadyToInstall(
                    version = version,
                    file = apkFile,
                    needsInstallPermission = true,
                )
            val permIntent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (runCatching { context.startActivity(permIntent) }.isSuccess) {
                InstallLaunchResult.NEEDS_PERMISSION
            } else {
                InstallLaunchResult.FAILED
            }
        }

        _downloadState.value =
            UpdateDownloadState.ReadyToInstall(
                version = version,
                file = apkFile,
                needsInstallPermission = false,
            )
        val launched =
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, APK_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            }.isSuccess
        return if (launched) InstallLaunchResult.LAUNCHED else InstallLaunchResult.FAILED
    }

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    companion object {
        const val LANDING_URL = "https://goke.xeed.ink"
        const val PROJECT_URL = "https://github.com/xihale/goke"
        private const val RELEASES_MANIFEST_URL = "https://goke.xeed.ink/downloads/latest.json"
        private const val UPDATES_DIR = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private fun parseManifest(raw: String): List<RemoteRelease> {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                return json.decodeFromString<List<RemoteRelease>>(trimmed)
            }
            val latest = json.decodeFromString<LatestManifest>(trimmed)
            val entries = listOfNotNull(latest.stable, latest.latestAny)
            if (entries.isNotEmpty()) return entries
            return if (latest.tagName.isNotBlank()) {
                listOf(
                    RemoteRelease(
                        tagName = latest.tagName,
                        htmlUrl = latest.htmlUrl,
                        prerelease = latest.prerelease,
                        draft = latest.draft,
                        body = latest.body,
                        apkUrl = latest.apkUrl,
                        apkSize = latest.apkSize,
                        apkSha256 = latest.apkSha256,
                    ),
                )
            } else {
                emptyList()
            }
        }

        private suspend fun defaultFetchReleasesJson(): String =
            withContext(Dispatchers.IO) {
                val conn = openRedirectedConnection(RELEASES_MANIFEST_URL, "application/json")
                try {
                    conn.inputStream.use { it.readBytes().decodeToString() }
                } finally {
                    conn.disconnect()
                }
            }

        private suspend fun defaultOpenDownloadStream(url: String): RemoteStream =
            withContext(Dispatchers.IO) {
                val conn = openRedirectedConnection(url, "*/*")
                RemoteStream(
                    stream = conn.inputStream,
                    contentLength = conn.contentLengthLong,
                    onClose = { conn.disconnect() },
                )
            }

        private fun openRedirectedConnection(
            startUrl: String,
            accept: String,
        ): HttpURLConnection {
            var currentUrl = startUrl
            repeat(5) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", accept)
                conn.setRequestProperty("User-Agent", "goke-android/${BuildConfig.VERSION_NAME}")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) throw IOException("Empty redirect location")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IOException("HTTP $code")
                }
                return conn
            }
            throw IOException("Too many redirects")
        }
    }
}

class RemoteStream(
    val stream: InputStream,
    val contentLength: Long = -1L,
    private val onClose: () -> Unit = {},
) {
    fun close() = onClose()
}

private fun isCachedApkValid(
    file: File,
    expectedSize: Long,
    expectedSha256: String,
): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    if (expectedSize > 0L && file.length() != expectedSize) return false
    val sha = expectedSha256.trim().lowercase()
    if (sha.isEmpty()) return true
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    return actual == sha
}

private fun pruneOtherFiles(
    dir: File,
    keepName: String,
) {
    dir.listFiles()?.forEach { f ->
        if (f.name != keepName) f.deleteRecursively()
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
private data class RemoteRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = UpdateChecker.LANDING_URL,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val body: String? = null,
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("apk_size") val apkSize: Long = 0L,
    @SerialName("apk_sha256") val apkSha256: String = "",
)

@Serializable
private data class LatestManifest(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = UpdateChecker.LANDING_URL,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val body: String? = null,
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("apk_size") val apkSize: Long = 0L,
    @SerialName("apk_sha256") val apkSha256: String = "",
    val stable: RemoteRelease? = null,
    @SerialName("latest_any") val latestAny: RemoteRelease? = null,
)
