package app.goke.droprelay

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import app.goke.core.campus.CampusDropRelayDefaults
import app.goke.core.campus.DropCodePreview
import app.goke.core.campus.DropRelayFile
import app.goke.core.campus.DropRelayHealth
import app.goke.core.campus.DropRelayIndex
import app.goke.core.campus.DropRelayLogic
import app.goke.data.ErrorReporter
import app.goke.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

data class DropRelayState(
    val index: DropRelayIndex? = null,
    val health: DropRelayHealth? = null,
    val discoveredShareUrl: String = "",
    val discoveredDropUrl: String = "",
    val loadingIndex: Boolean = false,
    val indexError: DropRelayErrorKind? = null,
    val lastSyncedAt: Long = 0L,
    val peekingCode: Boolean = false,
    val codePreview: DropCodePreview? = null,
    val codeError: DropRelayErrorKind? = null,
    val pendingSendUris: List<Uri> = emptyList(),
)

/**
 * DropRelay 资料库与快传控制器：管理本地索引缓存、校园网探活、6 位码预检、DownloadManager 下载、
 * 课程资源本地匹配以及系统分享菜单 `<activity-alias>` 开关。
 */
class DropRelayManager(
    private val context: Context,
    private val settings: SettingsStore,
    val campusDefaults: CampusDropRelayDefaults,
    private val errors: ErrorReporter,
    private val client: DropRelayClient = DropRelayClient(),
) {
    private val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
    private val _state = MutableStateFlow(DropRelayState())
    val state: StateFlow<DropRelayState> = _state.asStateFlow()

    fun effectiveShareUrl(rawSetting: String = settings.value.dropRelayShareUrl): String =
        DropRelayLogic.normalizeBaseUrl(rawSetting)
            ?: DropRelayLogic.normalizeBaseUrl(campusDefaults.shareUrl)
            ?: DropRelayLogic.normalizeBaseUrl(_state.value.discoveredShareUrl)
            ?: ""

    fun effectiveDropUrl(rawSetting: String = settings.value.dropRelayDropUrl): String =
        DropRelayLogic.normalizeBaseUrl(rawSetting)
            ?: DropRelayLogic.normalizeBaseUrl(campusDefaults.dropUrl)
            ?: DropRelayLogic.normalizeBaseUrl(_state.value.discoveredDropUrl)
            ?: ""

    fun hasCampusDefaults(): Boolean = campusDefaults.shareUrl.isNotBlank() || campusDefaults.dropUrl.isNotBlank()

    /** 启动后异步水合本地缓存索引（零网络 IO），供课程详情页秒算「相关资料」。 */
    fun hydrate() {
        val raw = runCatching { if (cacheFile.isFile) cacheFile.readText(Charsets.UTF_8) else null }.getOrNull()
        val parsed = raw?.let { runCatching { DropRelayLogic.parseIndexJson(it) }.getOrNull() }
        val syncedAt = sp.getLong(KEY_SYNCED_AT, 0L)
        if (parsed != null) {
            _state.update { it.copy(index = parsed, lastSyncedAt = syncedAt) }
        }
        syncShareSheetAlias(settings.value.dropShareSheetEnabled)
    }

    fun matchingFilesForCourse(courseName: String): List<DropRelayFile> {
        val files =
            _state.value.index
                ?.files
                .orEmpty()
        return DropRelayLogic.matchCourseResources(files, courseName)
    }

    suspend fun refreshIndex(force: Boolean = false) {
        val shareUrl = effectiveShareUrl()
        if (shareUrl.isBlank()) {
            _state.update { it.copy(loadingIndex = false, indexError = DropRelayErrorKind.INVALID_URL) }
            return
        }
        _state.update { it.copy(loadingIndex = true, indexError = null) }
        val cachedEtag = if (force) null else sp.getString(KEY_ETAG, null)
        val cachedLastMod = if (force) null else sp.getString(KEY_LAST_MODIFIED, null)
        try {
            val (fetchResult, health) =
                coroutineScope {
                    val idxDeferred = async { client.fetchIndex(shareUrl, etag = cachedEtag, lastModified = cachedLastMod) }
                    val healthDeferred = async { runCatching { client.fetchHealth(shareUrl) }.getOrNull() }
                    idxDeferred.await() to healthDeferred.await()
                }
            val now = System.currentTimeMillis()
            val peerDrop =
                health
                    ?.discoveredPeerOrigin()
                    ?.takeIf { health.mode.equals("share", ignoreCase = true) || it.contains("drop", ignoreCase = true) }
                    ?.let { DropRelayLogic.normalizeBaseUrl(it) }
                    .orEmpty()
            when (fetchResult) {
                IndexFetchResult.NotModified -> {
                    sp.edit().putLong(KEY_SYNCED_AT, now).apply()
                    _state.update {
                        it.copy(
                            health = health ?: it.health,
                            discoveredDropUrl = peerDrop.ifBlank { it.discoveredDropUrl },
                            loadingIndex = false,
                            indexError = null,
                            lastSyncedAt = now,
                        )
                    }
                }

                is IndexFetchResult.Updated -> {
                    withContext(Dispatchers.IO) {
                        runCatching { cacheFile.writeText(fetchResult.rawJson, Charsets.UTF_8) }
                    }
                    sp
                        .edit()
                        .putString(KEY_ETAG, fetchResult.etag)
                        .putString(KEY_LAST_MODIFIED, fetchResult.lastModified)
                        .putLong(KEY_SYNCED_AT, now)
                        .apply()
                    _state.update {
                        it.copy(
                            index = fetchResult.index,
                            health = health ?: it.health,
                            discoveredDropUrl = peerDrop.ifBlank { it.discoveredDropUrl },
                            loadingIndex = false,
                            indexError = null,
                            lastSyncedAt = now,
                        )
                    }
                }
            }
        } catch (e: DropRelayException) {
            _state.update { it.copy(loadingIndex = false, indexError = e.kind) }
        } catch (t: Throwable) {
            errors.record("droprelay.refreshIndex", t)
            _state.update { it.copy(loadingIndex = false, indexError = DropRelayErrorKind.UNREACHABLE) }
        }
    }

    suspend fun probeHealth(): DropRelayHealth? {
        val shareUrl = effectiveShareUrl()
        val dropUrl = effectiveDropUrl()
        val target = shareUrl.ifBlank { dropUrl }
        if (target.isBlank()) return null
        val health = runCatching { client.fetchHealth(target) }.getOrNull()
        if (health != null) {
            val peer = DropRelayLogic.normalizeBaseUrl(health.discoveredPeerOrigin()).orEmpty()
            _state.update { prev ->
                prev.copy(
                    health = health,
                    discoveredDropUrl = if (health.mode.equals("share", true) && peer.isNotBlank()) peer else prev.discoveredDropUrl,
                    discoveredShareUrl = if (health.mode.equals("drop", true) && peer.isNotBlank()) peer else prev.discoveredShareUrl,
                )
            }
        }
        return health
    }

    suspend fun peekCode(rawInput: String) {
        val dropUrl = effectiveDropUrl()
        if (dropUrl.isBlank()) {
            _state.update { it.copy(peekingCode = false, codeError = DropRelayErrorKind.INVALID_URL, codePreview = null) }
            return
        }
        val code = DropRelayLogic.extractDropCode(rawInput, dropUrl)
        if (code == null) {
            _state.update { it.copy(peekingCode = false, codeError = DropRelayErrorKind.NOT_FOUND, codePreview = null) }
            return
        }
        _state.update { it.copy(peekingCode = true, codeError = null) }
        try {
            val preview = client.peekDropCode(dropUrl, code)
            _state.update { it.copy(peekingCode = false, codePreview = preview, codeError = null) }
        } catch (e: DropRelayException) {
            _state.update { it.copy(peekingCode = false, codePreview = null, codeError = e.kind) }
        } catch (t: Throwable) {
            errors.record("droprelay.peekCode", t)
            _state.update { it.copy(peekingCode = false, codePreview = null, codeError = DropRelayErrorKind.UNREACHABLE) }
        }
    }

    fun clearCodePreview() {
        _state.update { it.copy(codePreview = null, codeError = null) }
    }

    fun enqueueDownload(
        url: String,
        fileName: String,
    ): Result<Long> =
        runCatching {
            val dm =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: error("DownloadManager unavailable")
            val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "download" }
            val request =
                DownloadManager
                    .Request(Uri.parse(url))
                    .setTitle(safeName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Goke/$safeName")
            dm.enqueue(request)
        }

    fun setPendingSendUris(uris: List<Uri>) {
        _state.update { it.copy(pendingSendUris = uris) }
    }

    fun consumePendingSendUris(): List<Uri> {
        val current = _state.value.pendingSendUris
        if (current.isNotEmpty()) {
            _state.update { it.copy(pendingSendUris = emptyList()) }
        }
        return current
    }

    fun syncShareSheetAlias(enabled: Boolean) {
        runCatching {
            val component = ComponentName(context, SHARE_ALIAS_CLASS)
            val newState =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            context.packageManager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun setShareSheetEnabled(enabled: Boolean) {
        settings.update { it.copy(dropShareSheetEnabled = enabled) }
        syncShareSheetAlias(enabled)
    }

    companion object {
        private const val PREFS_NAME = "droprelay_cache"
        private const val CACHE_FILE_NAME = "droprelay_index.json"
        private const val KEY_ETAG = "etag"
        private const val KEY_LAST_MODIFIED = "last_modified"
        private const val KEY_SYNCED_AT = "synced_at"
        const val SHARE_ALIAS_CLASS = "app.goke.DropShareAlias"
    }
}
