package app.goke.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.goke.DeepLinks
import app.goke.R
import app.goke.core.todo.SmartList
import app.goke.core.todo.TaskLogic
import app.goke.gokeApp
import app.goke.ui.dueLabel
import java.time.LocalDateTime

/** 「待办清单」小组件：逾期 + 今天的未完成待办；点圆角方框直接完成，点行进编辑页，点＋新建。 */
@Suppress("DEPRECATION")
class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        var hasMini = false
        for (id in appWidgetIds) {
            val size = WidgetKit.sizeClass(context, manager, id)
            if (size.isMini) hasMini = true
            manager.updateAppWidget(id, build(context, id, size = size))
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
        if (hasMini && context.gokeApp.repo.ready.value) {
            NextWidgetProvider.scheduleTick(context, context.gokeApp.repo.snapshot.value, context.gokeApp.settings.value)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val size = WidgetKit.sizeClass(context, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, build(context, appWidgetId, size = size))
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widget_list)
        if (size.isMini && context.gokeApp.repo.ready.value) {
            NextWidgetProvider.scheduleTick(context, context.gokeApp.repo.snapshot.value, context.gokeApp.settings.value)
        }
    }

    data class Row(
        val id: String,
        val title: String,
        val code: String?,
        val due: String?,
        val priority: Int,
        val overdue: Boolean,
        val note: String? = null,
        val listName: String? = null,
        val hasRemind: Boolean = false,
        val repeating: Boolean = false,
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
            for (id in ids) {
                val size = WidgetKit.sizeClass(ctx, mgr, id)
                mgr.updateAppWidget(id, build(ctx, id, size = size))
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        fun refreshMini(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            for (id in ids) {
                val size = WidgetKit.sizeClass(ctx, mgr, id)
                if (size.isMini) mgr.updateAppWidget(id, build(ctx, id, size = size))
            }
        }

        /** 当前该显示的行：先逾期、再今天（均只取未完成的顶层待办）。 */
        fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.gokeApp
            if (!app.repo.ready.value) return emptyList()
            val snap =
                app.repo.snapshot.value
                    .live()
            val overdue = TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now).filter { !it.done }
            val today = TaskLogic.filter(snap.tasks, SmartList.TODAY, now).filter { !it.done && it !in overdue }
            val listNames = snap.lists.associate { it.id to it.name }
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
                    note =
                        if (code != null) {
                            null
                        } else {
                            t.note
                                .lineSequence()
                                .map { it.trim() }
                                .firstOrNull { it.isNotEmpty() }
                                ?.take(80)
                        },
                    listName = t.listId?.let { listNames[it] } ?: ctx.getString(R.string.bucket_inbox),
                    hasRemind = t.effectiveRemindMinutes.isNotEmpty(),
                    repeating = t.repeat != null,
                )
            }
        }

        internal fun build(
            ctx: Context,
            widgetId: Int,
            now: LocalDateTime = LocalDateTime.now(),
            size: WidgetKit.SizeClass = WidgetKit.SizeClass(),
        ): RemoteViews {
            if (size.isMini) {
                val app = ctx.gokeApp
                val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
                return NextWidgetProvider.build(ctx, snap, app.settings.value, now, size)
            }
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
                Intent(ctx, TodoWidgetService::class.java).setData(Uri.parse("goke://widget/todo/$widgetId")),
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
            val openTodo = WidgetKit.mainActivityIntent(ctx, 10, DeepLinks.todoUri())
            views.setOnClickPendingIntent(R.id.widget_header, openTodo)
            views.setOnClickPendingIntent(R.id.widget_empty, openTodo)
            views.setOnClickPendingIntent(R.id.widget_root, openTodo)
            views.setOnClickPendingIntent(R.id.widget_add, WidgetKit.overlayIntent(ctx, 11, WidgetOverlayActivity.addIntent(ctx)))
            return views
        }
    }
}
