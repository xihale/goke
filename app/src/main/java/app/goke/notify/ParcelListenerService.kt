package app.goke.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.goke.gokeApp
import kotlinx.coroutines.launch

/**
 * 快递取件通知识别（用户在设置里开启并授权「通知使用权」后才工作）。
 * 只读取通知文本并在本地用规则识别；识别与入库逻辑统一委托给 [ParcelCapture]。
 */
class ParcelListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = gokeApp
        if (!app.settings.value.parcelEnabled) return
        if (sbn.packageName == packageName) return
        val ex = sbn.notification.extras ?: return
        val text =
            listOfNotNull(
                ex.getCharSequence(Notification.EXTRA_TITLE),
                ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT),
            ).joinToString("，") { it.toString() }
        if (text.isBlank()) return
        app.scope.launch {
            ParcelCapture.ingest(this@ParcelListenerService, text)
        }
    }
}
