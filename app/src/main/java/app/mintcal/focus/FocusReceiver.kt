package app.mintcal.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mintcal.mintApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 倒计时到点的精确闹钟：进程被杀也会被拉起，按目标时刻补记专注会话并通知。 */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_FINISH) return
        val app = context.mintApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repo.ready.first { it }
                app.focus.completeByAlarm()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FINISH = "app.mintcal.action.FOCUS_FINISH"
    }
}
