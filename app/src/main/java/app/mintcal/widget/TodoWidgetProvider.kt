package app.mintcal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import app.mintcal.DeepLinks
import app.mintcal.R
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.mintApp
import app.mintcal.ui.dueLabel
import java.time.LocalDateTime

/** 「待办清单」小组件：逾期 + 今天的未完成待办；点圆角方框直接完成，点行进编辑页，点＋新建。 */
@Suppress("DEPRECATION")
class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) manager.updateAppWidget(id, build(context, id))
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }

    data class Row(
        val id: String,
        val title: String,
        val code: String?,
        val due: String?,
        val priority: Int,
        val overdue: Boolean,
    )

    companion object {
        const val EXTRA_OP = "op"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_URI = "uri"
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
                val code = WidgetKit.parcelCode(t)
                val cleanTitle = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title
                Row(
                    id = t.id,
                    title = cleanTitle,
                    code = code,
                    // 快递行已有取件码胶囊占宽，省略行尾时间以免把驿站名挤成两字
                    due = if (code != null) null else due?.let { ctx.dueLabel(it, t.dueHasTime, date) },
                    priority = t.priority,
                    overdue = t in overdue,
                )
            }
        }

        fun build(
            ctx: Context,
            widgetId: Int,
            now: LocalDateTime = LocalDateTime.now(),
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_todo)
            val count = rows(ctx, now).size
            views.setTextViewText(R.id.widget_title, ctx.getString(R.string.tab_todo))
            if (count > 0) {
                views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                views.setTextViewText(R.id.widget_count, count.toString())
            } else {
                views.setViewVisibility(R.id.widget_count, View.GONE)
            }
            views.setRemoteAdapter(
                R.id.widget_list,
                Intent(ctx, TodoWidgetService::class.java).setData(Uri.parse("mintcal://widget/todo/$widgetId")),
            )
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(
                    ctx,
                    100 + widgetId,
                    Intent(ctx, WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setOnClickPendingIntent(R.id.widget_header, WidgetKit.mainActivityIntent(ctx, 10, DeepLinks.todoUri()))
            views.setOnClickPendingIntent(R.id.widget_add, WidgetKit.mainActivityIntent(ctx, 11, DeepLinks.addUri()))
            return views
        }
    }
}
