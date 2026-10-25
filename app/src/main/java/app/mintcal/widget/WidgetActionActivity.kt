package app.mintcal.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.mintcal.DeepLinks
import app.mintcal.MainActivity
import app.mintcal.core.change.Edits
import app.mintcal.mintApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 小组件列表点击的无界面跳板：「完成」在后台直接提交变更，「打开」转给主界面的待办抽屉。
 * 窗口透明、onCreate 里就 finish，用户基本看不到。
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(TodoWidgetProvider.EXTRA_TASK_ID)
        if (id != null) {
            val app = mintApp
            when (intent.getStringExtra(TodoWidgetProvider.EXTRA_OP)) {
                TodoWidgetProvider.OP_DONE -> {
                    app.scope.launch {
                        app.repo.ready.first { it }
                        val snap = app.repo.snapshot.value
                        val task = snap.tasks.firstOrNull { it.id == id && it.deletedAt == null }
                        if (task != null && !task.done) {
                            app.repo.commit(Edits.toggleTask(snap, task, true, System.currentTimeMillis(), LocalDateTime.now(), task.title))
                        }
                    }
                }

                else -> {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, DeepLinks.taskUri(id), this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
