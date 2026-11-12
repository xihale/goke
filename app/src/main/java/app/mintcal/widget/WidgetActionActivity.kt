package app.mintcal.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.mintcal.DeepLinks
import app.mintcal.MainActivity
import app.mintcal.core.change.Edits
import app.mintcal.mintApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * 小组件列表点击的无界面跳板：
 * - 「完成」在后台直接提交变更，并立即刷新所有相关小组件（跳过 800ms 防抖，实现毫秒级消隐）；
 * - 「打开」转给主界面的待办编辑页或对应深链页。
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val op = intent.getStringExtra(TodoWidgetProvider.EXTRA_OP)
        val id = intent.getStringExtra(TodoWidgetProvider.EXTRA_TASK_ID)
        val rawUri = intent.getStringExtra(TodoWidgetProvider.EXTRA_URI)
        val app = mintApp

        when {
            op == TodoWidgetProvider.OP_DONE && id != null -> {
                app.scope.launch {
                    app.repo.ready.first { it }
                    val snap = app.repo.snapshot.value
                    val task = snap.tasks.firstOrNull { it.id == id && it.deletedAt == null }
                    if (task != null && !task.done) {
                        app.repo.commit(Edits.toggleTask(snap, task, true, System.currentTimeMillis(), LocalDateTime.now(), task.title))
                        val nextSnap = app.repo.snapshot.value
                        val st = app.settings.value
                        withContext(Dispatchers.Default) {
                            TodoWidgetProvider.refreshAll(app)
                            ParcelWidgetProvider.refreshAll(app)
                            TodayWidgetProvider.refreshAll(app, nextSnap, st)
                            NextWidgetProvider.refreshAll(app, nextSnap, st)
                        }
                    }
                }
            }

            id != null -> {
                startActivity(
                    Intent(Intent.ACTION_VIEW, DeepLinks.taskUri(id), this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }

            !rawUri.isNullOrBlank() -> {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(rawUri), this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
