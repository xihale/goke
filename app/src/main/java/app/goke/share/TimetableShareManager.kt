package app.goke.share

import app.goke.core.campus.JxfwAdapter
import app.goke.core.export.SharedTimetable
import app.goke.data.ErrorReporter
import app.goke.data.Repository
import app.goke.sync.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimetableShareUiState(
    val serverUrl: String = "",
    val defaultServerUrl: String = "",
    val displayName: String = "",
    val manualStudentId: String = "",
    val jxfwStudentId: String? = null,
    val code: String? = null,
    val shareUrl: String? = null,
    val boundStudentId: String? = null,
    val semesterName: String? = null,
    val updatedAt: Long? = null,
    val expiresAt: Long? = null,
    val expiryPresetDays: Int = 30,
    val busy: Boolean = false,
    val lastError: String? = null,
) {
    val hasActiveShare: Boolean get() = !code.isNullOrBlank() && !shareUrl.isNullOrBlank()
    val effectiveStudentId: String get() = jxfwStudentId?.takeIf { it.isNotBlank() } ?: manualStudentId
    val effectiveServerUrl: String get() = serverUrl.ifBlank { defaultServerUrl }
    val isCustomServer: Boolean get() = serverUrl.isNotBlank() && serverUrl != defaultServerUrl
}

/** 课表快速分享（实验性）状态与云端操作管理。 */
class TimetableShareManager(
    private val repo: Repository,
    private val store: TimetableShareStore,
    private val jxfw: JxfwAdapter,
    private val defaultServerUrlProvider: () -> String? = { DEFAULT_SERVER_URL },
    private val errors: ErrorReporter? = null,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://goke.xeed.ink"
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(snapshotState(busy = false, lastError = null))
    val state: StateFlow<TimetableShareUiState> = _state.asStateFlow()

    private fun snapshotState(
        busy: Boolean,
        lastError: String?,
    ): TimetableShareUiState {
        val jxfwUser =
            jxfw.state.value.username
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val defaultUrl = defaultServerUrlProvider()?.trim().orEmpty()
        return TimetableShareUiState(
            serverUrl = store.serverUrl,
            defaultServerUrl = defaultUrl,
            displayName = store.displayName,
            manualStudentId = store.manualStudentId,
            jxfwStudentId = jxfwUser,
            code = store.code,
            shareUrl = store.shareUrl,
            boundStudentId = store.boundStudentId,
            semesterName = store.semesterName,
            updatedAt = store.updatedAt,
            expiresAt = store.expiresAt,
            expiryPresetDays = store.expiryPresetDays,
            busy = busy,
            lastError = lastError,
        )
    }

    private fun normalizedDefaultUrl(): String? = defaultServerUrlProvider()?.let { SyncClient.normalizeUrl(it) }

    private fun persistServerUrl(normalizedUrl: String): String = if (normalizedUrl == normalizedDefaultUrl()) "" else normalizedUrl

    fun syncFromEnvironment() {
        _state.value = snapshotState(busy = _state.value.busy, lastError = _state.value.lastError)
    }

    fun updateDraft(
        displayName: String? = null,
        manualStudentId: String? = null,
        serverUrl: String? = null,
        expiryPresetDays: Int? = null,
    ) {
        if (displayName != null) store.displayName = displayName
        if (manualStudentId != null) store.manualStudentId = manualStudentId
        if (serverUrl != null) {
            val trimmed = serverUrl.trim()
            val normalized = SyncClient.normalizeUrl(trimmed)
            store.serverUrl =
                when {
                    trimmed.isEmpty() -> ""
                    normalized != null && normalized == normalizedDefaultUrl() -> ""
                    else -> trimmed
                }
        }
        if (expiryPresetDays != null) store.expiryPresetDays = expiryPresetDays
        _state.value = snapshotState(busy = _state.value.busy, lastError = null)
    }

    suspend fun publishOrUpdate(
        displayName: String,
        studentIdOverride: String? = null,
        expiresAt: Long,
        serverUrlOverride: String? = null,
    ): PutShareResponse =
        mutex.withLock {
            val trimmedName = displayName.trim()
            val effectiveId =
                (
                    jxfw.state.value.username
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: studentIdOverride?.trim()?.takeIf { it.isNotEmpty() }
                        ?: store.manualStudentId.trim()
                )
            if (effectiveId.isEmpty()) throw ShareException("请先填写学号")

            val rawUrl = (serverUrlOverride ?: store.serverUrl).ifBlank { defaultServerUrlProvider().orEmpty() }
            val normalizedUrl = SyncClient.normalizeUrl(rawUrl) ?: throw ShareException("请填写正确的服务器地址")

            store.displayName = trimmedName
            if (jxfw.state.value.username
                    .isNullOrBlank() && studentIdOverride != null
            ) {
                store.manualStudentId = studentIdOverride.trim()
            }
            val persistedServerUrl = persistServerUrl(normalizedUrl)
            store.serverUrl = persistedServerUrl

            _state.value = snapshotState(busy = true, lastError = null)
            try {
                val snap = repo.snapshot.value
                val payload = SharedTimetable.fromSnapshot(snap, trimmedName, expiresAt = expiresAt)
                val token = store.manageToken()?.takeIf { it.isNotBlank() }
                val res =
                    TimetableShareClient(normalizedUrl).putShare(
                        PutShareRequest(
                            studentId = effectiveId,
                            manageToken = token,
                            name = trimmedName,
                            expiresAt = expiresAt,
                            timetable =
                                SharedTimetableBody(
                                    semester = payload.semester,
                                    courses = payload.courses,
                                    exceptions = payload.exceptions,
                                ),
                        ),
                    )
                store.saveActive(
                    serverUrl = persistedServerUrl,
                    displayName = res.name,
                    boundStudentId = effectiveId,
                    code = res.code,
                    shareUrl = res.shareUrl,
                    semesterName = payload.semester.name,
                    updatedAt = res.updatedAt,
                    expiresAt = res.expiresAt,
                    manageToken = res.manageToken,
                )
                _state.value = snapshotState(busy = false, lastError = null)
                res
            } catch (e: ShareException) {
                _state.value = snapshotState(busy = false, lastError = e.message)
                throw e
            } catch (e: Throwable) {
                errors?.record("TimetableShare.publish", e)
                val msg = e.message ?: "发布失败"
                _state.value = snapshotState(busy = false, lastError = msg)
                throw ShareException(msg)
            }
        }

    suspend fun refreshStatus(serverUrlOverride: String? = null): Boolean =
        mutex.withLock {
            val studentId =
                store.boundStudentId?.takeIf { it.isNotBlank() }
                    ?: jxfw.state.value.username
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    ?: store.manualStudentId.trim()
            val token = store.manageToken()?.takeIf { it.isNotBlank() }
            if (studentId.isEmpty() || token == null) return@withLock false

            val rawUrl = (serverUrlOverride ?: store.serverUrl).ifBlank { defaultServerUrlProvider().orEmpty() }
            val normalizedUrl = SyncClient.normalizeUrl(rawUrl) ?: return@withLock false

            _state.value = snapshotState(busy = true, lastError = null)
            try {
                val res = TimetableShareClient(normalizedUrl).status(ShareStatusRequest(studentId, token))
                if (!res.exists) {
                    store.clearActive()
                    _state.value = snapshotState(busy = false, lastError = null)
                    return@withLock false
                }
                val code = res.code ?: store.code
                val shareUrl = res.shareUrl ?: store.shareUrl
                if (code != null && shareUrl != null) {
                    store.saveActive(
                        serverUrl = persistServerUrl(normalizedUrl),
                        displayName = res.name ?: store.displayName,
                        boundStudentId = studentId,
                        code = code,
                        shareUrl = shareUrl,
                        semesterName = res.semesterName ?: store.semesterName,
                        updatedAt = res.updatedAt ?: (store.updatedAt ?: System.currentTimeMillis()),
                        expiresAt = res.expiresAt ?: (store.expiresAt ?: 0L),
                        manageToken = null,
                    )
                }
                _state.value = snapshotState(busy = false, lastError = null)
                true
            } catch (e: ShareException) {
                if (e.status == 403 || e.status == 404) {
                    store.clearActive()
                }
                _state.value = snapshotState(busy = false, lastError = e.message)
                false
            } catch (e: Throwable) {
                errors?.record("TimetableShare.status", e)
                _state.value = snapshotState(busy = false, lastError = e.message)
                false
            }
        }

    suspend fun deleteCloud(serverUrlOverride: String? = null): Boolean =
        mutex.withLock {
            val studentId =
                store.boundStudentId?.takeIf { it.isNotBlank() }
                    ?: jxfw.state.value.username
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    ?: store.manualStudentId.trim()
            val token = store.manageToken()?.takeIf { it.isNotBlank() }
            if (studentId.isEmpty() || token == null) {
                store.clearActive()
                _state.value = snapshotState(busy = false, lastError = null)
                return@withLock false
            }

            val rawUrl = (serverUrlOverride ?: store.serverUrl).ifBlank { defaultServerUrlProvider().orEmpty() }
            val normalizedUrl = SyncClient.normalizeUrl(rawUrl) ?: throw ShareException("请填写正确的服务器地址")

            _state.value = snapshotState(busy = true, lastError = null)
            try {
                val res = TimetableShareClient(normalizedUrl).delete(DeleteShareRequest(studentId, token))
                store.clearActive()
                _state.value = snapshotState(busy = false, lastError = null)
                res.deleted
            } catch (e: ShareException) {
                if (e.status == 404) {
                    store.clearActive()
                }
                _state.value = snapshotState(busy = false, lastError = e.message)
                throw e
            } catch (e: Throwable) {
                errors?.record("TimetableShare.delete", e)
                val msg = e.message ?: "删除失败"
                _state.value = snapshotState(busy = false, lastError = msg)
                throw ShareException(msg)
            }
        }
}
