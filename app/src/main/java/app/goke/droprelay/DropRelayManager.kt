package app.goke.droprelay

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.goke.core.campus.CampusDropRelayDefaults
import app.goke.core.campus.DropRelayFile
import app.goke.core.campus.DropRelayIndex
import app.goke.core.campus.DropRelayLogic
import app.goke.data.ErrorReporter
import app.goke.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

data class DropRelayState(
    val index: DropRelayIndex? = null,
    val loadingIndex: Boolean = false,
    val indexError: DropRelayErrorKind? = null,
    val indexErrorDetail: String? = null,
    val lastSyncedAt: Long = 0L,
)

/** DropRelay 资料库与快传控制器：管理本地索引缓存、DownloadManager 下载与课程资源本地匹配。 */
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
            ?: ""

    fun effectiveDropUrl(rawSetting: String = settings.value.dropRelayDropUrl): String =
        DropRelayLogic.normalizeBaseUrl(rawSetting)
            ?: DropRelayLogic.normalizeBaseUrl(campusDefaults.dropUrl)
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
            _state.update {
                it.copy(
                    loadingIndex = false,
                    indexError = DropRelayErrorKind.INVALID_URL,
                    indexErrorDetail = null,
                )
            }
            return
        }
        _state.update { it.copy(loadingIndex = true, indexError = null, indexErrorDetail = null) }
        val hasLocalIndex = _state.value.index != null
        val cachedEtag = if (force || !hasLocalIndex) null else sp.getString(KEY_ETAG, null)
        val cachedLastMod = if (force || !hasLocalIndex) null else sp.getString(KEY_LAST_MODIFIED, null)
        try {
            val fetchResult = client.fetchIndex(shareUrl, etag = cachedEtag, lastModified = cachedLastMod)
            val now = System.currentTimeMillis()
            when (fetchResult) {
                IndexFetchResult.NotModified -> {
                    sp.edit().putLong(KEY_SYNCED_AT, now).apply()
                    _state.update {
                        it.copy(
                            loadingIndex = false,
                            indexError = null,
                            indexErrorDetail = null,
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
                            loadingIndex = false,
                            indexError = null,
                            indexErrorDetail = null,
                            lastSyncedAt = now,
                        )
                    }
                }
            }
        } catch (e: DropRelayException) {
            Log.w(TAG, "refreshIndex($shareUrl) failed: ${e.kind} ${e.message}", e.cause ?: e)
            errors.record("droprelay.refreshIndex", e)
            _state.update {
                it.copy(
                    loadingIndex = false,
                    indexError = e.kind,
                    indexErrorDetail = e.message,
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "refreshIndex($shareUrl) unexpected error", t)
            errors.record("droprelay.refreshIndex", t)
            _state.update {
                it.copy(
                    loadingIndex = false,
                    indexError = DropRelayErrorKind.UNREACHABLE,
                    indexErrorDetail = t.message ?: t.javaClass.simpleName,
                )
            }
        }
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

    companion object {
        private const val TAG = "DropRelay"
        private const val PREFS_NAME = "droprelay_cache"
        private const val CACHE_FILE_NAME = "droprelay_index.json"
        private const val KEY_ETAG = "etag"
        private const val KEY_LAST_MODIFIED = "last_modified"
        private const val KEY_SYNCED_AT = "synced_at"
    }
}
