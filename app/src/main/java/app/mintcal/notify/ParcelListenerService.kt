package app.mintcal.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Source
import app.mintcal.mintApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 快递取件通知识别（用户在设置里开启并授权「通知使用权」后才工作）。
 * 只读取通知文本并在本地用规则识别，不读取短信；识别出的取件信息变成一条待办。
 */
class ParcelListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = mintApp
        if (!app.settings.value.parcelEnabled) return
        if (sbn.packageName == packageName) return
        val ex = sbn.notification.extras ?: return
        val text =
            listOfNotNull(
                ex.getCharSequence(Notification.EXTRA_TITLE),
                ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT),
            ).joinToString("，") { it.toString() }
        if (text.isBlank()) return
        // 全系统每条通知都会进到这里：识别（逐条规则的正则）与查重都丢到 Default，
        // 回调线程（服务的 Main）只做文本提取，高频通知不再挤住监听服务
        app.scope.launch(Dispatchers.Default) {
            val info = app.parcelRules.value.parse(text, LocalDateTime.now()) ?: return@launch
            app.repo.ready.first { it }
            val snap =
                app.repo.snapshot.value
                    .live()
            val dup = snap.tasks.any { it.source == Source.PARCEL && it.extra["code"] == info.code && it.extra["place"] == info.place && !it.done }
            if (dup) return@launch
            val task = ParcelTasks.build(this@ParcelListenerService, info)
            val cs = Edits.save(snap, task, getString(R.string.parcel_op_desc), System.currentTimeMillis())
            app.repo.commit(cs)
            Notifications.postParcelCaptured(
                this@ParcelListenerService,
                task.id,
                info.code,
                info.place.ifBlank { info.company },
            )
        }
    }
}
