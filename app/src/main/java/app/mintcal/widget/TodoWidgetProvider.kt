package app.mintcal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import app.mintcal.DeepLinks
import app.mintcal.MainActivity
import app.mintcal.R
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.mintApp
import app.mintcal.ui.dueLabel
import java.time.LocalDateTime

/** 「待办清单」小组件：逾期 + 今天的未完成待办；点圈直接完成，点标题进抽屉，点＋新建。 */
class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) manager.updateAppWidget(id, build(context, id))
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }

    /** 一行要显示的待办。 */
    data class Row(
        val id: String,
        val title: String,
        val due: String?,
        val overdue: Boolean,
    )

    companion object {
        const val EXTRA_OP = "op"
        const val EXTRA_TASK_ID = "task_id"
        const val OP_DONE = "done"
        const val OP_OPEN = "open"
        private const val MAX_ROWS = 40

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, build(ctx, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        /** 当前该显示的行：先逾期、再今天（均只取未完成的顶层待办）。 */
        fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.mintApp
            if (!app.repo.ready.value) return emptyList()
            val snap =
                app.repo.snapshot.value
                    .live()
            val overdue = TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now).filter { !it.done }
            val today = TaskLogic.filter(snap.tasks, SmartList.TODAY, now).filter { !it.done && it !in overdue }
            val date = now.toLocalDate()
            return (overdue + today).take(MAX_ROWS).map { t ->
                val due = TaskLogic.effectiveDue(t, snap.currentSemester, snap.courses, snap.exceptions, now)
                Row(t.id, t.title, due?.let { ctx.dueLabel(it, t.dueHasTime, date) }, t in overdue)
            }
        }

        private fun build(
            ctx: Context,
            widgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_todo)
            views.setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_todo_title, rows(ctx).size))
            views.setRemoteAdapter(
                R.id.widget_list,
                Intent(ctx, TodoWidgetService::class.java).setData(Uri.parse("mintcal://widget/$widgetId")),
            )
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            // 行内点击：模板是无界面跳板 Activity（任何系统版本都允许从小组件点击启动），行里用 fillIn 区分「完成 / 打开」
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(
                    ctx,
                    widgetId,
                    Intent(ctx, WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setOnClickPendingIntent(R.id.widget_title, activity(ctx, 1, DeepLinks.todoUri()))
            views.setOnClickPendingIntent(R.id.widget_add, activity(ctx, 2, DeepLinks.addUri()))
            return views
        }

        private fun activity(
            ctx: Context,
            code: Int,
            uri: Uri,
        ): PendingIntent =
            PendingIntent.getActivity(
                ctx,
                code,
                Intent(Intent.ACTION_VIEW, uri, ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
