package app.mintcal.lanweb

import android.content.Context
import app.mintcal.data.Repository
import app.mintcal.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

data class LanWebUiState(
    val running: Boolean = false,
    val port: Int = 17531,
    val lanIp: String? = null,
    val pin: String = "",
    val expiresAtMs: Long? = null,
    val lastActivityMs: Long? = null,
    val lastError: String? = null,
) {
    val url: String? get() = lanIp?.let { "http://$it:$port" }
    val adbCommand: String get() = "adb reverse tcp:$port tcp:$port"
    val adbUrl: String get() = "http://127.0.0.1:$port"
}

/**
 * 局域网电脑端管理会话控制器：按需启停 [LanWebServer] 与前台服务 [LanWebService]，
 * 生成一次性 6 位连接码，并在连续 20 分钟无请求后自动关闭。
 */
class LanWebManager internal constructor(
    private val context: Context,
    private val repo: Repository,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    constructor(context: Context, repo: Repository) : this(
        context = context.applicationContext ?: context,
        repo = repo,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        clock = System::currentTimeMillis,
    )

    private val mutex = Mutex()
    private val random = SecureRandom()
    private var server: LanWebServer? = null
    private var watchdogJob: Job? = null

    private val _state = MutableStateFlow(LanWebUiState(lanIp = detectLanIpv4()))
    val state: StateFlow<LanWebUiState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            mutex.withLock {
                startInternal()
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                stopInternal()
            }
        }
    }

    fun regeneratePin() {
        val nextPin = generatePin()
        var activeState: LanWebUiState? = null
        _state.update { s ->
            if (!s.running) {
                s
            } else {
                s.copy(pin = nextPin).also { activeState = it }
            }
        }
        activeState?.let(::refreshNotification)
    }

    fun refreshNetwork() {
        val ip = detectLanIpv4()
        var activeState: LanWebUiState? = null
        _state.update { s ->
            val updated = s.copy(lanIp = ip)
            if (updated.running && ip != s.lanIp) activeState = updated
            updated
        }
        activeState?.let(::refreshNotification)
    }

    private fun startInternal() {
        if (server?.isRunning == true) return
        stopServerOnly()

        val lanIp = detectLanIpv4()
        val pin = generatePin()
        var started: LanWebServer? = null
        var lastErr: Throwable? = null

        val candidatePorts = (DEFAULT_PORT..MAX_FALLBACK_PORT).toList() + 0
        for (candidate in candidatePorts) {
            val srv =
                LanWebServer(
                    repo = repo,
                    assetOpener = { path -> context.assets.open(path) },
                    host = "0.0.0.0",
                    port = candidate,
                    pinProvider = { _state.value.pin },
                    onActivity = { recordActivity() },
                    clock = clock,
                )
            val result = runCatching { srv.start() }
            if (result.isSuccess) {
                started = srv
                break
            } else {
                lastErr = result.exceptionOrNull()
                srv.stop()
            }
        }

        if (started == null) {
            _state.update {
                it.copy(
                    running = false,
                    lanIp = lanIp,
                    pin = "",
                    expiresAtMs = null,
                    lastActivityMs = null,
                    lastError = lastErr?.message ?: lastErr?.let { e -> e::class.simpleName } ?: "启动失败",
                )
            }
            return
        }

        server = started
        val now = clock()
        val expiresAt = now + IDLE_TIMEOUT_MS
        _state.value =
            LanWebUiState(
                running = true,
                port = started.boundPort,
                lanIp = lanIp,
                pin = pin,
                expiresAtMs = expiresAt,
                lastActivityMs = now,
                lastError = null,
            )
        startWatchdog()
        LanWebService.start(context)
    }

    private fun stopInternal() {
        stopServerOnly()
        _state.update {
            it.copy(
                running = false,
                pin = "",
                expiresAtMs = null,
                lastActivityMs = null,
            )
        }
        LanWebService.stop(context)
        Notifications.cancelLanWebNotification(context)
    }

    private fun stopServerOnly() {
        watchdogJob?.cancel()
        watchdogJob = null
        server?.stop()
        server = null
    }

    private fun recordActivity() {
        val now = clock()
        val expiresAt = now + IDLE_TIMEOUT_MS
        _state.update { s ->
            if (!s.running) s else s.copy(lastActivityMs = now, expiresAtMs = expiresAt)
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob =
            scope.launch {
                while (isActive) {
                    val s = _state.value
                    if (!s.running) break
                    val expiresAt = s.expiresAtMs ?: break
                    val remaining = expiresAt - clock()
                    if (remaining <= 0L) {
                        mutex.withLock {
                            val latest = _state.value
                            if (latest.running && (latest.expiresAtMs ?: 0L) <= clock()) {
                                stopInternal()
                            }
                        }
                        break
                    }
                    delay(remaining.coerceAtMost(WATCHDOG_POLL_MS))
                }
            }
    }

    private fun refreshNotification(s: LanWebUiState) {
        Notifications.updateLanWebNotification(
            ctx = context,
            url = s.url ?: s.adbUrl,
            pin = s.pin,
            stopIntent = LanWebService.stopPendingIntent(context),
        )
    }

    private fun generatePin(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    companion object {
        const val DEFAULT_PORT = 17531
        const val MAX_FALLBACK_PORT = 17541
        const val IDLE_TIMEOUT_MS = 20L * 60_000L
        private const val WATCHDOG_POLL_MS = 30_000L

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
