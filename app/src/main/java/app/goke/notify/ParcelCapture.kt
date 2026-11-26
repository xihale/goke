package app.goke.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.PowerManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.goke.R
import app.goke.core.change.Edits
import app.goke.core.model.Source
import app.goke.core.parcel.ParcelInfo
import app.goke.gokeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** 快递取件文本入库（通知监听、短信广播与短信收件箱兜底共用）：串行化解析、查重并生成待办与通知。 */
object ParcelCapture {
    private const val WAKE_LOCK_TAG = "goke:parcel_capture"
    private const val WAKE_LOCK_TIMEOUT_MS = 15_000L
    private const val SCAN_PREFS = "parcel_sms_scan"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val KEY_SEEN_IDS = "seen_ids"
    private const val INITIAL_LOOKBACK_MS = 24L * 3600_000L
    private const val OVERLAP_MS = 60_000L
    private const val MAX_SCAN_ROWS = 50
    private const val MAX_SEEN_IDS = 200

    private val mutex = Mutex()
    private val scanMutex = Mutex()
    private val _redactedDetected = MutableStateFlow(false)
    val redactedDetected: StateFlow<Boolean> = _redactedDetected.asStateFlow()

    fun hasReadSmsPermission(ctx: Context): Boolean = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasSmsPermissions(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            hasReadSmsPermission(ctx)

    internal fun acquireWakeLock(ctx: Context): PowerManager.WakeLock? =
        runCatching {
            val pm = ctx.getSystemService(PowerManager::class.java) ?: return@runCatching null
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()

    internal fun releaseWakeLock(wl: PowerManager.WakeLock?) {
        runCatching {
            if (wl?.isHeld == true) wl.release()
        }
    }

    private suspend inline fun <T> withWakeLock(
        ctx: Context,
        crossinline block: suspend () -> T,
    ): T {
        val wl = acquireWakeLock(ctx)
        return try {
            block()
        } finally {
            releaseWakeLock(wl)
        }
    }

    fun isRedactedText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val sysId = Resources.getSystem().getIdentifier("redacted_notification_message", "string", "android")
        if (sysId != 0) {
            val sysText = runCatching { Resources.getSystem().getString(sysId) }.getOrNull()
            if (!sysText.isNullOrBlank() && t.contains(sysText)) return true
        }
        return t.contains("敏感通知内容") ||
            t.contains("Sensitive notification content", ignoreCase = true)
    }

    fun noteIfRedacted(text: String): Boolean {
        if (!isRedactedText(text)) return false
        _redactedDetected.value = true
        return true
    }

    suspend fun ingest(
        ctx: Context,
        text: String,
    ): Boolean = ingestCandidates(ctx, listOf(text))

    suspend fun ingestCandidates(
        ctx: Context,
        candidates: List<String>,
        smsDateMs: Long? = null,
    ): Boolean =
        withWakeLock(ctx) {
            withContext(Dispatchers.Default) {
                val app = ctx.gokeApp
                if (!app.settings.value.parcelEnabled) return@withContext false
                var info: ParcelInfo? = null
                val now = LocalDateTime.now()
                for (raw in candidates) {
                    val t = raw.trim()
                    if (t.isEmpty()) continue
                    if (noteIfRedacted(t)) continue
                    info = app.parcelRules.value.parse(t, now)
                    if (info != null) break
                }
                val parsed = info ?: return@withContext false
                mutex.withLock {
                    app.repo.ready.first { it }
                    val rawSnap = app.repo.snapshot.value
                    val snap = rawSnap.live()
                    val dupLive =
                        snap.tasks.any {
                            it.source == Source.PARCEL &&
                                it.extra["code"] == parsed.code &&
                                it.extra["place"] == parsed.place &&
                                !it.done
                        }
                    val dupHandledForSms =
                        smsDateMs != null &&
                            rawSnap.tasks.any {
                                it.source == Source.PARCEL &&
                                    it.extra["code"] == parsed.code &&
                                    it.extra["place"] == parsed.place &&
                                    (!it.done || it.updatedAt >= smsDateMs - OVERLAP_MS)
                            }
                    if (dupLive || dupHandledForSms) return@withLock false
                    val task = ParcelTasks.build(ctx, parsed)
                    val cs = Edits.save(snap, task, ctx.getString(R.string.parcel_op_desc), System.currentTimeMillis())
                    app.repo.commit(cs)
                    Notifications.postParcelCaptured(
                        ctx,
                        task.id,
                        parsed.code,
                        parsed.place.ifBlank { parsed.company },
                    )
                    true
                }
            }
        }

    /**
     * 兜底扫描最近收件箱短信：解决息屏锁屏隐藏通知内容、Android 15+ 敏感通知脱敏，
     * 或部分系统在息屏/后台延迟/拦截广播时漏读取件短信的问题。
     */
    suspend fun scanRecentSmsInbox(ctx: Context): Int =
        scanMutex.withLock {
            val appCtx = ctx.applicationContext
            val app = appCtx.gokeApp
            if (!app.settings.value.parcelEnabled || !hasReadSmsPermission(appCtx)) return@withLock 0
            withWakeLock(appCtx) {
                withContext(Dispatchers.IO) {
                    val sp = appCtx.getSharedPreferences(SCAN_PREFS, Context.MODE_PRIVATE)
                    val lastScanMs = sp.getLong(KEY_LAST_SCAN_MS, 0L)
                    val seenIds =
                        sp
                            .getString(KEY_SEEN_IDS, "")
                            .orEmpty()
                            .split(',')
                            .mapNotNull { it.toLongOrNull() }
                            .toMutableList()
                    val seenSet = seenIds.toHashSet()
                    val nowMs = System.currentTimeMillis()
                    val sinceMs =
                        if (lastScanMs > 0L) {
                            maxOf(lastScanMs - OVERLAP_MS, nowMs - INITIAL_LOOKBACK_MS)
                        } else {
                            nowMs - INITIAL_LOOKBACK_MS
                        }
                    val rows = mutableListOf<Triple<Long, String, Long>>()
                    var maxDate = lastScanMs
                    val queried =
                        runCatching {
                            appCtx.contentResolver
                                .query(
                                    Telephony.Sms.Inbox.CONTENT_URI,
                                    arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
                                    "${Telephony.Sms.DATE} >= ?",
                                    arrayOf(sinceMs.toString()),
                                    "${Telephony.Sms.DATE} ASC",
                                )?.use { cursor ->
                                    val idCol = cursor.getColumnIndex(Telephony.Sms._ID)
                                    val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
                                    val dateCol = cursor.getColumnIndex(Telephony.Sms.DATE)
                                    if (idCol >= 0 && bodyCol >= 0 && dateCol >= 0) {
                                        while (cursor.moveToNext() && rows.size < MAX_SCAN_ROWS) {
                                            val id = cursor.getLong(idCol)
                                            val body = cursor.getString(bodyCol).orEmpty()
                                            val date = cursor.getLong(dateCol)
                                            if (date > maxDate) maxDate = date
                                            if (id !in seenSet && body.isNotBlank()) {
                                                rows += Triple(id, body, date)
                                            }
                                        }
                                    }
                                    true
                                } ?: false
                        }.getOrDefault(false)

                    var captured = 0
                    for ((id, body, date) in rows) {
                        if (ingestCandidates(appCtx, listOf(body), smsDateMs = date)) {
                            captured++
                        }
                        if (seenSet.add(id)) seenIds += id
                    }
                    val trimmedIds =
                        if (seenIds.size > MAX_SEEN_IDS) {
                            seenIds.takeLast(MAX_SEEN_IDS)
                        } else {
                            seenIds
                        }
                    val nextScanMs = if (queried) maxOf(maxDate, nowMs) else maxDate
                    sp
                        .edit()
                        .putLong(KEY_LAST_SCAN_MS, nextScanMs)
                        .putString(KEY_SEEN_IDS, trimmedIds.joinToString(","))
                        .apply()
                    captured
                }
            }
        }

    fun scheduleInboxCatchUp(
        ctx: Context,
        retryDelayMs: Long = 0L,
    ) {
        val appCtx = ctx.applicationContext
        val app = appCtx.gokeApp
        if (!app.settings.value.parcelEnabled || !hasReadSmsPermission(appCtx)) return
        val wl = acquireWakeLock(appCtx)
        app.scope.launch {
            try {
                val first = scanRecentSmsInbox(appCtx)
                if (first == 0 && retryDelayMs > 0L) {
                    delay(retryDelayMs)
                    scanRecentSmsInbox(appCtx)
                }
            } finally {
                releaseWakeLock(wl)
            }
        }
    }
}
