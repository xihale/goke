package app.goke.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.goke.gokeApp
import kotlinx.coroutines.launch

/**
 * 快递短信直读接收器（用户在快递页面授予「接收短信」权限后生效）。
 * 绕开 Android 15+ 对含数字短信通知的敏感通知脱敏（RECEIVE_SENSITIVE_NOTIFICATIONS），纯本地解析不上传。
 */
class ParcelSmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.gokeApp
        if (!app.settings.value.parcelEnabled) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val grouped =
            messages
                .groupBy { it.displayOriginatingAddress.orEmpty() }
                .values
                .map { parts -> parts.joinToString("") { it.displayMessageBody.orEmpty() } }
                .filter { it.isNotBlank() }
        if (grouped.isEmpty()) return
        val pending = goAsync()
        app.scope.launch {
            try {
                ParcelCapture.ingestCandidates(context.applicationContext, grouped)
            } finally {
                pending.finish()
            }
        }
    }
}
