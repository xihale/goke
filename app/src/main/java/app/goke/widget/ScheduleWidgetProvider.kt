package app.goke.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.goke.DeepLinks
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.CourseItem
import app.goke.core.model.Snapshot
import app.goke.data.Settings
import app.goke.gokeApp
import app.goke.ui.hm
import app.goke.ui.monthDayWeekday
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「今日课表」小组件：清爽日期 + 周次表头，按时间列出今天的课；
 * 已下课置灰，正在上的课按已过时长渐进置灰，没课时显示轻松颜文字提示。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.gokeApp
        val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
        val st = app.settings.value
        for (id in appWidgetIds) {
            val compact = WidgetKit.isCompactWidth(manager, id)
            manager.updateAppWidget(id, build(context, snap, st, compact = compact))
        }
        if (snap != null) scheduleTick(context, snap, st)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val app = context.gokeApp
        val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
        val st = app.settings.value
        appWidgetManager.updateAppWidget(appWidgetId, build(context, snap, st, compact = WidgetKit.isCompactWidth(newOptions)))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val app = context.gokeApp
            if (app.repo.ready.value) refreshAll(context, app.repo.snapshot.value, app.settings.value)
        }
    }

    companion object {
        const val ACTION_TICK = "app.goke.widget.SCHEDULE_TICK"

        fun refreshAll(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ScheduleWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) {
                val compact = WidgetKit.isCompactWidth(mgr, id)
                mgr.updateAppWidget(id, build(ctx, snap, st, compact = compact))
            }
            scheduleTick(ctx, snap, st)
        }

        private fun scheduleTick(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ) {
            val courses =
                Agenda
                    .forDay(snap, now.toLocalDate(), st.defaultBlockMinutes)
                    .filterIsInstance<CourseItem>()
            val hasOngoing = courses.any { WidgetKit.timePhase(it.occ.start, it.occ.end, now) == WidgetKit.TimePhase.ONGOING }
            val nextTransition =
                courses
                    .flatMap { listOf(it.occ.start, it.occ.end) }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?: now
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay()
                        .plusMinutes(1)
            val next =
                if (hasOngoing) {
                    minOf(nextTransition, now.plusMinutes(WidgetKit.ONGOING_TICK_MINUTES))
                } else {
                    nextTransition
                }
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
            compact: Boolean = false,
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_schedule)
            val today = now.toLocalDate()
            val live = snap?.live()
            views.setTextViewText(R.id.sched_title, ctx.monthDayWeekday(today))
            val weekBadge = WidgetKit.weekBadge(ctx, live?.currentSemester, today)
            if (!compact && weekBadge != null) {
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
            val progresses =
                intArrayOf(
                    R.id.sched_progress0,
                    R.id.sched_progress1,
                    R.id.sched_progress2,
                    R.id.sched_progress3,
                    R.id.sched_progress4,
                    R.id.sched_progress5,
                )
            val timeCols =
                intArrayOf(
                    R.id.sched_time_col0,
                    R.id.sched_time_col1,
                    R.id.sched_time_col2,
                    R.id.sched_time_col3,
                    R.id.sched_time_col4,
                    R.id.sched_time_col5,
                )
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

            val shown =
                if (courses.size <= 6) {
                    courses
                } else {
                    val firstActive = courses.indexOfFirst { it.occ.end.isAfter(now) }
                    if (firstActive < 0) {
                        courses.takeLast(6)
                    } else {
                        courses.drop(firstActive.coerceAtMost(courses.size - 6)).take(6)
                    }
                }
            val openTimetable = WidgetKit.mainActivityIntent(ctx, 40, DeepLinks.timetableUri())

            for (i in rows.indices) {
                val c = shown.getOrNull(i)
                if (c == null) {
                    views.setViewVisibility(rows[i], View.GONE)
                    continue
                }
                val o = c.occ
                val phase = WidgetKit.timePhase(o.start, o.end, now)
                val progress = WidgetKit.timeProgress(o.start, o.end, now)
                views.setViewVisibility(rows[i], View.VISIBLE)
                val fg =
                    when (phase) {
                        WidgetKit.TimePhase.FINISHED -> {
                            views.setInt(rows[i], "setBackgroundResource", R.drawable.widget_course_done_bg)
                            views.setViewVisibility(progresses[i], View.GONE)
                            ctx.getColor(R.color.widget_course_done_fg)
                        }

                        WidgetKit.TimePhase.ONGOING -> {
                            views.setInt(rows[i], "setBackgroundResource", WidgetKit.courseBgRes(o.course.colorIndex))
                            views.setViewVisibility(progresses[i], View.VISIBLE)
                            views.setProgressBar(progresses[i], WidgetKit.PROGRESS_MAX, WidgetKit.progressPermille(progress), false)
                            WidgetKit.courseFg(ctx, o.course.colorIndex)
                        }

                        WidgetKit.TimePhase.UPCOMING -> {
                            views.setInt(rows[i], "setBackgroundResource", WidgetKit.courseBgRes(o.course.colorIndex))
                            views.setViewVisibility(progresses[i], View.GONE)
                            WidgetKit.courseFg(ctx, o.course.colorIndex)
                        }
                    }
                views.setViewVisibility(timeCols[i], if (compact) View.GONE else View.VISIBLE)
                views.setTextColor(times[i], fg)
                views.setTextColor(ends[i], WidgetKit.softFg(fg))
                views.setTextColor(names[i], fg)
                views.setTextColor(metas[i], WidgetKit.softFg(fg))
                views.setTextViewText(times[i], o.start.hm())
                views.setTextViewText(ends[i], o.end.hm())
                views.setTextViewText(names[i], o.course.name)
                val meta =
                    if (compact) {
                        listOfNotNull(o.start.hm(), o.room.takeIf { it.isNotBlank() }).joinToString(" · ")
                    } else {
                        o.room.trim()
                    }
                views.setTextViewText(metas[i], meta)
                views.setViewVisibility(metas[i], if (meta.isNotEmpty()) View.VISIBLE else View.GONE)
                views.setOnClickPendingIntent(
                    rows[i],
                    WidgetKit.overlayIntent(
                        ctx,
                        41 + i,
                        WidgetOverlayActivity.courseIntent(ctx, o.course.id, o.date.toString(), o.startPeriod, o.endPeriod),
                    ),
                )
            }

            val remaining = courses.count { it.occ.end.isAfter(now) } - shown.count { it.occ.end.isAfter(now) }
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
