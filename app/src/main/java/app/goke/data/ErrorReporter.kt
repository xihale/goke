package app.goke.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.goke.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class ErrorReport(
    val id: String,
    val timestampMs: Long,
    val source: String,
    val summary: String,
    val detail: String,
    val fatal: Boolean,
    val acknowledged: Boolean = false,
)

/**
 * 全局异常记录与诊断弹窗状态管理：
 * 1. 捕获未处理线程崩溃（持久化至本地，下次启动弹出 Modal 供复制反馈）；
 * 2. 捕获协程或关键异步路径上的意外异常（实时弹出 Modal，并持久化历史记录）。
 */
class ErrorReporter(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(ErrorReport.serializer())
    private var reportFile: File? = null
    private var appContext: Context? = null
    private val breadcrumbs = ArrayDeque<String>(MAX_BREADCRUMBS)

    private val _reports = MutableStateFlow<List<ErrorReport>>(emptyList())
    val reports: StateFlow<List<ErrorReport>> = _reports.asStateFlow()

    private val _pendingModal = MutableStateFlow<ErrorReport?>(null)
    val pendingModal: StateFlow<ErrorReport?> = _pendingModal.asStateFlow()

    val coroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            record(source = "Coroutine", throwable = throwable, fatal = false)
        }

    fun attach(context: Context) {
        val appCtx = context.applicationContext ?: context
        val file = File(appCtx.filesDir, FILE_NAME)
        synchronized(lock) {
            appContext = appCtx
            reportFile = file
            val loaded = readLocked(file)
            _reports.value = loaded
            _pendingModal.value = loaded.firstOrNull { !it.acknowledged }
        }
        installUncaughtHandler()
    }

    /** 记录脱敏的运行步骤面包屑，异常上报时附带最近轨迹以便排查。 */
    fun breadcrumb(
        tag: String,
        message: String,
    ) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(clock()))
        val line = "$time [$tag] ${message.replace('\n', ' ').take(240)}"
        synchronized(lock) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(line)
        }
    }

    fun record(
        source: String,
        throwable: Throwable,
        fatal: Boolean = false,
        threadName: String = Thread.currentThread().name,
    ) {
        if (throwable is CancellationException) return
        val now = clock()
        val summary = buildSummary(throwable)
        val detail = formatDetail(source = source, throwable = throwable, fatal = fatal, timestampMs = now, threadName = threadName)
        synchronized(lock) {
            val current = _reports.value
            val latest = current.firstOrNull()
            if (latest != null &&
                !latest.acknowledged &&
                latest.source == source &&
                latest.summary == summary &&
                now - latest.timestampMs in 0..DEDUP_WINDOW_MS
            ) {
                return
            }
            val entry =
                ErrorReport(
                    id = UUID.randomUUID().toString(),
                    timestampMs = now,
                    source = source,
                    summary = summary,
                    detail = detail,
                    fatal = fatal,
                    acknowledged = false,
                )
            val updated = (listOf(entry) + current).take(MAX_REPORTS)
            _reports.value = updated
            _pendingModal.value = entry
            writeLocked(reportFile, updated)
        }
    }

    fun dismissPending() {
        synchronized(lock) {
            val current = _reports.value
            if (current.none { !it.acknowledged }) {
                _pendingModal.value = null
                return
            }
            val updated = current.map { if (it.acknowledged) it else it.copy(acknowledged = true) }
            _reports.value = updated
            _pendingModal.value = null
            writeLocked(reportFile, updated)
        }
    }

    fun clearHistory() {
        synchronized(lock) {
            _reports.value = emptyList()
            _pendingModal.value = null
            reportFile?.let { file ->
                runCatching { if (file.exists()) file.delete() }
            }
        }
    }

    private fun installUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val base = if (previous is GokeUncaughtHandler) previous.previous else previous
        Thread.setDefaultUncaughtExceptionHandler(GokeUncaughtHandler(base, this))
    }

    private fun readLocked(file: File): List<ErrorReport> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) emptyList() else json.decodeFromString(listSerializer, raw).take(MAX_REPORTS)
        }.getOrDefault(emptyList())
    }

    private fun writeLocked(
        file: File?,
        items: List<ErrorReport>,
    ) {
        file ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val encoded = json.encodeToString(listSerializer, items)
            file.writeText(encoded, Charsets.UTF_8)
        }
    }

    private fun buildSummary(throwable: Throwable): String {
        val name = throwable::class.java.simpleName.ifBlank { throwable::class.java.name }
        val msg = throwable.message?.trim()?.takeIf { it.isNotEmpty() }
        return if (msg != null) "$name: $msg" else name
    }

    private fun formatDetail(
        source: String,
        throwable: Throwable,
        fatal: Boolean,
        timestampMs: Long,
        threadName: String,
    ): String {
        val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
        val abi =
            Build.SUPPORTED_ABIS
                .firstOrNull()
                .orEmpty()
                .ifBlank { "unknown" }
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val stack =
            throwable.stackTraceToString().let { raw ->
                if (raw.length > MAX_STACK_CHARS) {
                    raw.take(MAX_STACK_CHARS) + "\n... (truncated)"
                } else {
                    raw
                }
            }
        val recentCrumbs = synchronized(lock) { breadcrumbs.toList() }
        val networkInfo = currentNetworkSummary()
        return buildString {
            appendLine("=== Goke Error Report ===")
            appendLine("Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Device: $device ($abi)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Network: $networkInfo")
            appendLine("Time: $timeText")
            appendLine("Source: $source (${if (fatal) "fatal" else "non-fatal"})")
            appendLine("Thread: $threadName")
            if (recentCrumbs.isNotEmpty()) {
                appendLine()
                appendLine("=== Recent Breadcrumbs ===")
                recentCrumbs.forEach { appendLine(it) }
            }
            appendLine()
            append(stack.trimEnd())
        }
    }

    private fun currentNetworkSummary(): String {
        val ctx = appContext ?: return "unknown"
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unavailable"
            val network = cm.activeNetwork ?: return "none"
            val caps = cm.getNetworkCapabilities(network) ?: return "none"
            val transports =
                buildList {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
                }
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val base = if (transports.isEmpty()) "OTHER" else transports.joinToString("+")
            if (validated) "$base (validated)" else "$base (unvalidated)"
        }.getOrElse { "unknown (${it.javaClass.simpleName})" }
    }

    private class GokeUncaughtHandler(
        val previous: Thread.UncaughtExceptionHandler?,
        private val reporter: ErrorReporter,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            try {
                reporter.record(
                    source = "UncaughtException",
                    throwable = throwable,
                    fatal = true,
                    threadName = thread.name,
                )
            } catch (_: Throwable) {
                // 避免记录异常时再次抛出覆盖原始崩溃
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val FILE_NAME = "error_reports.json"
        private const val MAX_REPORTS = 20
        private const val MAX_BREADCRUMBS = 40
        private const val MAX_STACK_CHARS = 24_000
        private const val DEDUP_WINDOW_MS = 1_500L
    }
}
