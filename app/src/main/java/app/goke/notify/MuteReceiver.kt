package app.goke.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * 上课自动静音：开课切「完全勿扰」，下课恢复。恢复值记录在 SharedPreferences，
 * 只有从「非勿扰」进入勿扰时才记录，避免把用户手动开的勿扰又给关掉。
 * 没有勿扰权限时静默跳过（设置页有引导授权）。
 */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        val sp = context.getSharedPreferences("mute", Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_MUTE -> {
                if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE &&
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                ) {
                    sp.edit().putInt(PREV_FILTER, nm.currentInterruptionFilter).apply()
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }

            ACTION_UNMUTE -> {
                val prev = sp.getInt(PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                sp.edit().remove(PREV_FILTER).apply()
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    nm.setInterruptionFilter(prev)
                }
            }
        }
    }

    companion object {
        const val ACTION_MUTE = "app.goke.action.MUTE"
        const val ACTION_UNMUTE = "app.goke.action.UNMUTE"
        private const val PREV_FILTER = "prev_filter"
    }
}
