package app.goke.mcp

import app.goke.advanced.AdvancedController
import app.goke.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.Inet4Address
import java.net.NetworkInterface

data class McpState(
    val prefs: McpPrefs = McpPrefs(),
    val running: Boolean = false,
    val boundPort: Int = McpPrefs.DEFAULT_PORT,
    val token: String = "",
    val lanIp: String? = null,
    val requestCount: Int = 0,
    val lastRequestAt: Long? = null,
    val lastError: String? = null,
) {
    val endpointHost: String
        get() = if (prefs.allowLan) (lanIp ?: "0.0.0.0") else "127.0.0.1"

    val endpointUrl: String
        get() = "http://$endpointHost:${if (running) boundPort else prefs.port}/mcp"
}

/**
 * 本地 MCP 服务的生命周期控制器：监听 [McpStore] 配置变化启停 [LocalMcpServer]，
 * 并提供一键复制给 Claude Code / Cursor / Cherry Studio / T3 Code 的标准 `mcpServers` JSON。
 */
class LocalMcpManager(
    private val repo: Repository,
    private val advanced: AdvancedController,
    private val store: McpStore,
    private val scope: CoroutineScope,
) {
    private val tools = LocalMcpTools(repo, advanced)
    private val mutex = Mutex()
    private var server: LocalMcpServer? = null
    private val prettyJson = Json { prettyPrint = true }

    private val _state =
        MutableStateFlow(
            McpState(
                prefs = store.prefs.value,
                boundPort = store.prefs.value.port,
            ),
        )
    val state: StateFlow<McpState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val token = store.ensureToken()
            val lanIp = detectLanIpv4()
            _state.update { it.copy(token = token, lanIp = lanIp) }
            store.prefs.collect { prefs ->
                reconcile(prefs)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        store.update { it.copy(enabled = enabled) }
    }

    fun setAllowLan(allowLan: Boolean) {
        store.update { it.copy(allowLan = allowLan) }
    }

    fun setPort(port: Int) {
        store.update { it.copy(port = port.coerceIn(1024, 65535)) }
    }

    fun refreshNetwork() {
        val ip = detectLanIpv4()
        _state.update { it.copy(lanIp = ip) }
    }

    fun regenerateToken(): String {
        val next = store.regenerateToken()
        _state.update { it.copy(token = next) }
        return next
    }

    fun clientConfigJson(): String {
        val s = _state.value
        val token = s.token.ifBlank { store.ensureToken() }
        val root =
            buildJsonObject {
                putJsonObject("mcpServers") {
                    putJsonObject("mintgdut") {
                        put("type", "http")
                        put("url", s.endpointUrl)
                        putJsonObject("headers") {
                            put("Authorization", "Bearer $token")
                        }
                    }
                }
            }
        return prettyJson.encodeToString(
            kotlinx.serialization.json.JsonObject
                .serializer(),
            root,
        )
    }

    private suspend fun reconcile(prefs: McpPrefs) =
        mutex.withLock {
            server?.stop()
            server = null
            val lanIp = detectLanIpv4()
            val token = store.ensureToken()
            if (!prefs.enabled) {
                _state.update {
                    it.copy(
                        prefs = prefs,
                        running = false,
                        boundPort = prefs.port,
                        token = token,
                        lanIp = lanIp,
                        lastError = null,
                    )
                }
                return@withLock
            }
            val bindHost = if (prefs.allowLan) "0.0.0.0" else "127.0.0.1"
            val srv =
                LocalMcpServer(
                    tools = tools,
                    host = bindHost,
                    port = prefs.port,
                    tokenProvider = { _state.value.token.ifBlank { store.ensureToken() } },
                    onRequest = {
                        val now = System.currentTimeMillis()
                        _state.update { it.copy(requestCount = it.requestCount + 1, lastRequestAt = now) }
                    },
                )
            runCatching { srv.start() }
                .onSuccess {
                    server = srv
                    _state.update {
                        it.copy(
                            prefs = prefs,
                            running = true,
                            boundPort = srv.boundPort,
                            token = token,
                            lanIp = lanIp,
                            lastError = null,
                        )
                    }
                }.onFailure { err ->
                    srv.stop()
                    _state.update {
                        it.copy(
                            prefs = prefs,
                            running = false,
                            boundPort = prefs.port,
                            token = token,
                            lanIp = lanIp,
                            lastError = err.message ?: err::class.simpleName,
                        )
                    }
                }
        }

    companion object {
        fun detectLanIpv4(): String? =
            runCatching {
                val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
                var fallback: String? = null
                while (ifaces.hasMoreElements()) {
                    val iface = ifaces.nextElement()
                    if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                    val name = iface.name.lowercase()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) {
                                return ip
                            }
                            if (fallback == null) fallback = ip
                        }
                    }
                }
                fallback
            }.getOrNull()
    }
}
