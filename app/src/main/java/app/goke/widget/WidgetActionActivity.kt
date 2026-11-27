package app.goke.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.goke.MainActivity
import app.goke.core.change.Edits
import app.goke.gokeApp
import app.goke.ui.FeedbackEvent
import app.goke.ui.isClearingTodayTasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * 小组件列表点击的无界面跳板：
 * - 「完成」在后台直接提交变更，并立即刷新所有相关小组件（跳过 800ms 防抖，实现毫秒级消隐）；
 * - 「打开」优先拉起桌面悬浮层 [WidgetOverlayActivity]，能在桌面直接查看与完成就不进主应用。
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val op = intent.getStringExtra(TodoWidgetProvider.EXTRA_OP)
        val id = intent.getStringExtra(TodoWidgetProvider.EXTRA_TASK_ID)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val dateStr = intent.getStringExtra(EXTRA_DATE)
        val startPeriod = intent.getIntExtra(EXTRA_START_PERIOD, -1).takeIf { it > 0 }
        val endPeriod = intent.getIntExtra(EXTRA_END_PERIOD, -1).takeIf { it > 0 }
        val rawUri = intent.getStringExtra(TodoWidgetProvider.EXTRA_URI)
        val app = gokeApp

        when {
            op == TodoWidgetProvider.OP_DONE && id != null -> {
                app.scope.launch {
                    app.repo.ready.first { it }
                    val snap = app.repo.snapshot.value
                    val task = snap.tasks.firstOrNull { it.id == id && it.deletedAt == null }
                    if (task != null && !task.done) {
                        val nowDt = LocalDateTime.now()
                        val clearedToday = isClearingTodayTasks(snap, setOf(task.id), nowDt)
                        app.feedback.play(if (clearedToday) FeedbackEvent.CELEBRATE else FeedbackEvent.TASK_COMPLETE)
                        app.repo.commit(Edits.toggleTask(snap, task, true, System.currentTimeMillis(), nowDt, task.title))
                        withContext(Dispatchers.Default) {
                            WidgetKit.refreshAllWidgets(app)
                        }
                    }
                }
            }

            id != null -> {
                startActivity(WidgetOverlayActivity.taskIntent(this, id))
            }

            !courseId.isNullOrBlank() -> {
                startActivity(WidgetOverlayActivity.courseIntent(this, courseId, dateStr, startPeriod, endPeriod))
            }

            !eventId.isNullOrBlank() -> {
                startActivity(WidgetOverlayActivity.eventIntent(this, eventId))
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

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_START_PERIOD = "start_period"
        const val EXTRA_END_PERIOD = "end_period"
    }
}
