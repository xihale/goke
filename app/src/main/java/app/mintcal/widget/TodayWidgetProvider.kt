package app.mintcal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.mintcal.MainActivity
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.AgendaItem
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import app.mintcal.mintApp
import app.mintcal.ui.hm
import app.mintcal.ui.monthDayWeekday
import java.time.LocalDate
import java.time.LocalDateTime

/** 「今日」小组件：今天的课程、日程与待办，点击打开应用。 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.mintApp
        val ready = app.repo.ready.value
        val snap = app.repo.snapshot.value
        for (id in appWidgetIds) manager.updateAppWidget(id, build(context, if (ready) snap else null, app.settings.value))
    }

    companion object {
        fun refreshAll(ctx: Context, snap: Snapshot, st: Settings) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx, snap, st)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun line(ctx: Context, item: AgendaItem): String = when (item) {
            is CourseItem -> "${item.occ.start.hm()}  ${item.occ.course.name}" + item.occ.room.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            is EventItem -> (if (item.allDay) ctx.getString(R.string.all_day) else item.start.hm()) + "  " + item.event.title
            is TaskItem -> (if (item.hasTime) item.due.hm() else "☐") + "  " + item.task.title
            is BlockItem -> "${item.start.hm()}  ${item.task.title}"
        }

        fun build(ctx: Context, snap: Snapshot?, st: Settings, now: LocalDateTime = LocalDateTime.now()): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_today)
            val today: LocalDate = now.toLocalDate()
            views.setTextViewText(R.id.widget_title, ctx.getString(R.string.tab_today) + " · " + ctx.monthDayWeekday(today))
            val body = if (snap == null) ""
            else {
                val items = Agenda.forDay(snap, today, st.defaultBlockMinutes).filter {
                    !(it is TaskItem && it.task.done) && !(it is BlockItem && it.task.done)
                }
                if (items.isEmpty()) ctx.getString(R.string.widget_empty) else items.take(6).joinToString("\n") { line(ctx, it) }
            }
            views.setTextViewText(R.id.widget_body, body)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
