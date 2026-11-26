package app.goke.notify

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.goke.gokeApp
import kotlinx.coroutines.launch

/**
 * 快递取件通知识别（用户在设置里开启并授权「通知使用权」后才工作）。
 * 只读取通知文本并在本地用规则识别；识别与入库逻辑统一委托给 [ParcelCapture]。
 */
class ParcelListenerService : NotificationListenerService() {
    private var unlockReceiver: BroadcastReceiver? = null
    private var lastScreenScanElapsedMs: Long = 0L

    override fun onListenerConnected() {
        isConnected = true
        registerUnlockCatchUp()
        if (gokeApp.settings.value.parcelEnabled) {
            ParcelCapture.scheduleInboxCatchUp(this)
        }
    }

    override fun onListenerDisconnected() {
        isConnected = false
        unregisterUnlockCatchUp()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gokeApp.settings.value.parcelEnabled) {
            runCatching { requestRebind(ComponentName(this, ParcelListenerService::class.java)) }
        }
    }

    override fun onDestroy() {
        isConnected = false
        unregisterUnlockCatchUp()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = gokeApp
        if (!app.settings.value.parcelEnabled) return
        if (sbn.packageName == packageName) return
        val candidates = extractCandidates(sbn.notification)
        if (candidates.isEmpty()) return
        val wl = ParcelCapture.acquireWakeLock(this)
        app.scope.launch {
            try {
                val captured = ParcelCapture.ingestCandidates(this@ParcelListenerService, candidates)
                if (!captured && candidates.any { ParcelCapture.isRedactedText(it) }) {
                    // 息屏/锁屏或 Android 15+ 脱敏时，延迟兜底扫一次系统短信收件箱
                    ParcelCapture.scheduleInboxCatchUp(this@ParcelListenerService, retryDelayMs = 1_200L)
                }
            } finally {
                ParcelCapture.releaseWakeLock(wl)
            }
        }
    }

    private fun registerUnlockCatchUp() {
        if (unlockReceiver != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val action = intent.action ?: return
                    if (!gokeApp.settings.value.parcelEnabled) return
                    val nowElapsed = SystemClock.elapsedRealtime()
                    if (action == Intent.ACTION_SCREEN_ON && nowElapsed - lastScreenScanElapsedMs < SCREEN_SCAN_THROTTLE_MS) {
                        return
                    }
                    lastScreenScanElapsedMs = nowElapsed
                    ParcelCapture.scheduleInboxCatchUp(this@ParcelListenerService)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            unlockReceiver = receiver
        }
    }

    private fun unregisterUnlockCatchUp() {
        val r = unlockReceiver ?: return
        unlockReceiver = null
        runCatching { unregisterReceiver(r) }
    }

    companion object {
        private const val SCREEN_SCAN_THROTTLE_MS = 10_000L

        @Volatile
        var isConnected: Boolean = false
            internal set

        internal fun extractCandidates(notification: Notification): List<String> {
            val out = LinkedHashSet<String>()
            val ex = notification.extras
            if (ex != null) {
                val title =
                    ex
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                val bigText =
                    ex
                        .getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                val text =
                    ex
                        .getCharSequence(Notification.EXTRA_TEXT)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                val body = bigText.ifEmpty { text }
                val primary = listOf(title, body).filter { it.isNotEmpty() }.joinToString("，")
                if (primary.isNotEmpty()) out += primary
                if (body.isNotEmpty()) out += body

                ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { rawLine ->
                    val line = rawLine?.toString()?.trim().orEmpty()
                    if (line.isNotEmpty()) {
                        if (title.isNotEmpty()) out += "$title，$line"
                        out += line
                    }
                }

                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages?.forEach { msg ->
                    val msgText =
                        msg.text
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                    val sender =
                        msg.person
                            ?.name
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                    if (msgText.isNotEmpty()) {
                        if (sender.isNotEmpty()) out += "$sender：$msgText"
                        out += msgText
                    }
                }

                val subText =
                    ex
                        .getCharSequence(Notification.EXTRA_SUB_TEXT)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                if (subText.isNotEmpty()) out += subText
            }
            val ticker =
                notification.tickerText
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            if (ticker.isNotEmpty()) out += ticker
            return out.toList()
        }
    }
}
