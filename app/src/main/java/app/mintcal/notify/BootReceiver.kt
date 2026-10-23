package app.mintcal.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mintcal.mintApp

/** 开机 / 升级 / 改时间或时区后：应用进程启动时会自动重新排提醒、刷新小组件，这里只需确保进程被拉起。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        context.mintApp
    }
}
