package app.goke.jxfw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.goke.gokeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 每周日晚的闹钟：后台拉下周课表（含停课 / 调课 / 补课）与考试安排。 */
class JxfwReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.gokeApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + app.errors.coroutineExceptionHandler).launch {
            try {
                if (app.repo.snapshot.value.semesters
                        .isEmpty()
                ) {
                    app.repo.load()
                }
                app.jxfw.weeklySync(auto = true)
            } catch (_: JxfwException) {
                // 失败已记录在状态里，用户下次打开会看到
            } finally {
                pending.finish()
            }
        }
    }
}
