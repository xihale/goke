package app.goke.sync

import android.os.Build
import app.goke.core.sync.SyncCodec
import app.goke.core.sync.SyncPlanner
import app.goke.data.ErrorReporter
import app.goke.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class SyncUiState(
    val account: SyncAccount? = null,
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val needsRelogin: Boolean = false,
)

/**
 * 同步管理：登录 / 退出，以及「先拉后推」的按记录同步（冲突按 updatedAt 较新者胜，见 ADR-0005）。
 * 本地模式（未登录）下什么都不做；App 始终只读写本地数据库。
 */
class SyncManager(
    private val repo: Repository,
    private val store: SyncStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val errors: ErrorReporter? = null,
) {
    private val mutex = Mutex()
    private var pending: Job? = null
    private val prettyJson = Json { prettyPrint = true }
    private val _state = MutableStateFlow(snapshotState(false))
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        repo.trackPurges = store.account.value != null
    }

    /** 后台线程校验 Keystore 令牌是否仍在；若已失效则清理登录态，避免在启动主线程触碰 Keystore。 */
    fun hydrate() {
        if (store.account.value != null && store.token() == null) {
            store.signOut()
            repo.trackPurges = false
            publish()
        }
    }

    private fun snapshotState(syncing: Boolean) = SyncUiState(store.account.value, syncing, store.lastSyncAt, store.lastError, store.needsRelogin)

    private fun publish(syncing: Boolean = false) {
        _state.value = snapshotState(syncing)
    }

    val lastServerUrl: String get() = store.lastServerUrl
    val lastUsername: String get() = store.lastUsername

    fun cloudMcpEndpoint(): String? = store.account.value?.let { "${it.serverUrl.trimEnd('/')}/mcp" }

    fun cloudMcpConfigJson(): String? {
        val acct = store.account.value ?: return null
        val token = store.token()?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = "${acct.serverUrl.trimEnd('/')}/mcp"
        val root =
            buildJsonObject {
                putJsonObject("mcpServers") {
                    putJsonObject("goke") {
                        put("type", "http")
                        put("url", endpoint)
                        putJsonObject("headers") {
                            put("Authorization", "Bearer $token")
                        }
                    }
                }
            }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    /** 登录并立刻同步一次。失败时抛出 [SyncException]。 */
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        val url = SyncClient.normalizeUrl(serverUrl) ?: throw SyncException("服务器地址不正确")
        val res = SyncClient(url).login(username.trim(), password, deviceName())
        store.lastServerUrl = url
        store.lastUsername = res.user.username
        store.signIn(SyncAccount(url, res.user.username, res.user.role), res.token)
        repo.trackPurges = true
        publish()
        syncNow()
    }

    /** 退出登录（尽力通知服务器注销本设备），本地数据保留。 */
    suspend fun logout() {
        val acct = store.account.value
        val token = store.token()
        if (acct != null && token != null) runCatching { SyncClient(acct.serverUrl, token).logout() }
        pending?.cancel()
        store.signOut()
        repo.trackPurges = false
        publish()
    }

    suspend fun changePassword(
        old: String,
        new: String,
    ) {
        val acct = store.account.value ?: throw SyncException("尚未登录")
        val token = store.token() ?: throw SyncException("尚未登录")
        SyncClient(acct.serverUrl, token).changePassword(old, new)
    }

    /** 数据变化或打开 App 时调用：稍后（防抖）同步一次。未登录或登录失效时无操作。 */
    fun requestSync(delayMs: Long = 4000) {
        if (store.account.value == null || store.needsRelogin) return
        pending?.cancel()
        pending =
            scope.launch {
                delay(delayMs)
                try {
                    syncNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SyncException) {
                    // 预期同步失败已写入状态
                } catch (e: Throwable) {
                    errors?.record("Sync.requestSync", e)
                }
            }
    }

    /** 立即同步。返回从服务器写入本地的记录数；失败抛出 [SyncException]（同时记录到状态里）。 */
    suspend fun syncNow(): Int =
        mutex.withLock {
            // UI 入口（requestSync）跑在 Main.immediate：编解码与规划是纯 CPU 的几百条 JSON 往返，
            // 统一下沉到 Default，只让 publish() 的状态更新自然回到调用方
            withContext(Dispatchers.Default) {
                val acct = store.account.value ?: return@withContext 0
                val token = store.token() ?: return@withContext 0
                publish(syncing = true)
                try {
                    val client = SyncClient(acct.serverUrl, token)
                    var applied = 0

                    // 首次同步：服务器已有数据而本地只有自动创建的默认学期时，丢弃本地默认学期，避免出现两个学期。
                    if (store.cursor == 0L && client.me().cursor > 0 && SyncPlanner.isPristine(repo.snapshot.value)) {
                        repo.discardAllLocal()
                    }

                    // 1. 拉取。本地已物理删除、但墓碑还没推送的记录（例如撤销了「新增」），不能被服务器上的旧版本带回来，
                    //    除非别的设备在那之后又改过它（remote 更新）。
                    val waitingTombstones = repo.pendingPurged().associateBy { it.id }
                    while (true) {
                        val page = client.pull(store.cursor)
                        val remote =
                            page.records.mapNotNull(SyncCodec::decode).filter { r ->
                                val t = waitingTombstones[r.id]
                                t == null || r.updatedAt > t.updatedAt
                            }
                        applied += repo.applyRemote(remote)
                        store.cursor = page.cursor
                        if (!page.more) break
                    }
                    repo.ensureDefaultSemester()

                    // 2. 推送：本地水位线之后改动的记录 + 已被物理删除但服务器还不知道的墓碑
                    val snap = repo.snapshot.value
                    val purged = repo.pendingPurged()
                    val stale = purged.filter { snap.find(it.id) != null }.map { it.id } // 又被重做回来的，不再需要墓碑
                    if (stale.isNotEmpty()) repo.clearPurged(stale)
                    val tombstones = purged.filter { snap.find(it.id) == null }
                    val changed = SyncPlanner.pending(snap, store.watermark)
                    var newMark = store.watermark
                    val outgoing = changed + tombstones
                    for (batch in outgoing.chunked(500)) {
                        val res = client.push(batch.map(SyncCodec::encode))
                        val rejected = res.rejected.mapNotNull(SyncCodec::decode)
                        applied += repo.applyRemote(rejected)
                        newMark = maxOf(newMark, rejected.maxOfOrNull { it.updatedAt } ?: 0)
                    }
                    newMark = maxOf(newMark, changed.maxOfOrNull { it.updatedAt } ?: 0)
                    store.watermark = newMark
                    if (tombstones.isNotEmpty()) repo.clearPurged(tombstones.map { it.id })

                    store.lastSyncAt = clock()
                    store.lastError = null
                    store.needsRelogin = false
                    publish()
                    applied
                } catch (e: SyncException) {
                    if (e.unauthorized) {
                        store.needsRelogin = true
                        store.lastError = "登录已失效，请重新登录"
                    } else {
                        store.lastError = e.message
                    }
                    publish()
                    throw e
                } catch (e: CancellationException) {
                    publish()
                    throw e
                } catch (e: Throwable) {
                    store.lastError = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
                    publish()
                    errors?.record("Sync.syncNow", e)
                    throw e
                }
            }
        }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60)
}
