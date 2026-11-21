package app.mintcal.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.mintcal.DeepLinks
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import app.mintcal.mintApp
import app.mintcal.ui.hm
import app.mintcal.ui.monthDayWeekday
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「今日课表」小组件：清爽日期 + 周次胶囊表头，按时间列出今天的课，正在上的那节薄荷底高亮；
 * 没课时显示轻松颜文字提示。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.mintApp
        val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
        val views = build(context, snap, app.settings.value)
        for (id in appWidgetIds) manager.updateAppWidget(id, views)
        if (snap != null) scheduleTick(context, snap, app.settings.value)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val app = context.mintApp
            if (app.repo.ready.value) refreshAll(context, app.repo.snapshot.value, app.settings.value)
        }
    }

    companion object {
        const val ACTION_TICK = "app.mintcal.widget.SCHEDULE_TICK"

        fun refreshAll(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ScheduleWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx, snap, st)
            for (id in ids) mgr.updateAppWidget(id, views)
            scheduleTick(ctx, snap, st)
        }

        private fun scheduleTick(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ) {
            val next =
                Agenda
                    .forDay(snap, now.toLocalDate(), st.defaultBlockMinutes)
                    .filterIsInstance<CourseItem>()
                    .flatMap { listOf(it.occ.start, it.occ.end) }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?: now
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay()
                        .plusMinutes(1)
            val pi =
                PendingIntent.getBroadcast(
                    ctx,
                    7,
                    Intent(ctx, ScheduleWidgetProvider::class.java).setAction(ACTION_TICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }

        fun build(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_schedule)
            val today = now.toLocalDate()
            val live = snap?.live()
            views.setTextViewText(R.id.sched_title, ctx.monthDayWeekday(today))
            val weekBadge = WidgetKit.weekBadge(ctx, live?.currentSemester, today)
            if (weekBadge != null) {
                views.setViewVisibility(R.id.sched_week, View.VISIBLE)
                views.setTextViewText(R.id.sched_week, weekBadge)
            } else {
                views.setViewVisibility(R.id.sched_week, View.GONE)
            }

            val courses =
                live
                    ?.let { s ->
                        Agenda.forDay(s, today, st.defaultBlockMinutes).filterIsInstance<CourseItem>()
                    }.orEmpty()
            val rows = intArrayOf(R.id.sched_row0, R.id.sched_row1, R.id.sched_row2, R.id.sched_row3, R.id.sched_row4, R.id.sched_row5)
            val times = intArrayOf(R.id.sched_time0, R.id.sched_time1, R.id.sched_time2, R.id.sched_time3, R.id.sched_time4, R.id.sched_time5)
            val ends = intArrayOf(R.id.sched_end0, R.id.sched_end1, R.id.sched_end2, R.id.sched_end3, R.id.sched_end4, R.id.sched_end5)
            val names = intArrayOf(R.id.sched_name0, R.id.sched_name1, R.id.sched_name2, R.id.sched_name3, R.id.sched_name4, R.id.sched_name5)
            val metas = intArrayOf(R.id.sched_meta0, R.id.sched_meta1, R.id.sched_meta2, R.id.sched_meta3, R.id.sched_meta4, R.id.sched_meta5)

            if (courses.isEmpty()) {
                views.setViewVisibility(R.id.sched_empty, View.VISIBLE)
                views.setTextViewText(R.id.sched_empty, if (snap == null) "" else ctx.getString(R.string.widget_schedule_empty))
            } else {
                views.setViewVisibility(R.id.sched_empty, View.GONE)
            }

            val shown = courses.filter { it.occ.end.isAfter(now) }.ifEmpty { courses.takeLast(6) }.take(6)
            val openTimetable = WidgetKit.mainActivityIntent(ctx, 40, DeepLinks.timetableUri())

            for (i in rows.indices) {
                val c = shown.getOrNull(i)
                if (c == null) {
                    views.setViewVisibility(rows[i], View.GONE)
                    continue
                }
                val o = c.occ
                val current = !o.start.isAfter(now) && o.end.isAfter(now)
                // 课程块穿植物色软彩底；正在上的那节按设计改用薄荷容器高亮（DESIGN.md「小组件正在进行的课程高亮」）
                views.setViewVisibility(rows[i], View.VISIBLE)
                views.setInt(
                    rows[i],
                    "setBackgroundResource",
                    if (current) R.drawable.widget_row_now_bg else WidgetKit.courseBgRes(o.course.colorIndex),
                )
                val fg =
                    ctx.getColor(if (current) R.color.widget_now_fg else WidgetKit.courseFgRes(o.course.colorIndex))
                views.setTextColor(times[i], fg)
                views.setTextColor(ends[i], WidgetKit.softFg(fg))
                views.setTextColor(names[i], fg)
                views.setTextColor(metas[i], WidgetKit.softFg(fg))
                views.setTextViewText(times[i], o.start.hm())
                views.setTextViewText(ends[i], o.end.hm())
                views.setTextViewText(names[i], o.course.name)
                val meta =
                    listOfNotNull(
                        o.room.takeIf { it.isNotBlank() },
                        if (current) ctx.getString(R.string.widget_ongoing) else null,
                    ).joinToString(" · ")
                views.setTextViewText(metas[i], meta)
                views.setOnClickPendingIntent(
                    rows[i],
                    WidgetKit.overlayIntent(
                        ctx,
                        41 + i,
                        WidgetOverlayActivity.courseIntent(ctx, o.course.id, o.date.toString(), o.startPeriod, o.endPeriod),
                    ),
                )
            }

            val remaining = courses.count { it.occ.end.isAfter(now) } - shown.size
            if (remaining > 0) {
                views.setViewVisibility(R.id.sched_more, View.VISIBLE)
                views.setTextViewText(R.id.sched_more, ctx.getString(R.string.widget_schedule_more, remaining))
            } else {
                views.setViewVisibility(R.id.sched_more, View.GONE)
            }

            views.setOnClickPendingIntent(R.id.sched_header, openTimetable)
            views.setOnClickPendingIntent(R.id.sched_empty, openTimetable)
            views.setOnClickPendingIntent(R.id.sched_more, openTimetable)
            views.setOnClickPendingIntent(R.id.widget_root, openTimetable)
            return views
        }
    }
}
