package app.goke.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
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
import app.goke.ui.monthDay
import app.goke.ui.monthDayWeekday
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「今日课表」小组件：清爽日期 + 周次表头，可滚动列出今天未结束的课；
 * 已下课直接隐藏，正在上的课按已过时长渐进置灰，支持自由缩放到任意网格尺寸。
 */
@Suppress("DEPRECATION")
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
            manager.updateAppWidget(
                id,
                WidgetKit.buildResponsive(context, manager, id) { size ->
                    build(context, id, snap, st, size = size)
                },
            )
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.sched_list)
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
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetKit.buildResponsive(context, newOptions) { size ->
                build(context, appWidgetId, snap, st, size = size)
            },
        )
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.sched_list)
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

    internal data class Row(
        val key: String,
        val courseId: String,
        val courseDate: String,
        val startPeriod: Int,
        val endPeriod: Int,
        val startTime: String,
        val endTime: String,
        val name: String,
        val room: String,
        val colorIndex: Int,
        val phase: WidgetKit.TimePhase,
        val progress: Float,
    )

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
                mgr.updateAppWidget(
                    id,
                    WidgetKit.buildResponsive(ctx, mgr, id) { size ->
                        build(ctx, id, snap, st, size = size)
                    },
                )
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.sched_list)
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

        internal fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.gokeApp
            if (!app.repo.ready.value) return emptyList()
            return buildRows(app.repo.snapshot.value, app.settings.value, now)
        }

        internal fun buildRows(
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val today = now.toLocalDate()
            val live = snap.live()
            return Agenda
                .forDay(live, today, st.defaultBlockMinutes)
                .filterIsInstance<CourseItem>()
                .filter { it.occ.end.isAfter(now) }
                .map { item ->
                    val o = item.occ
                    Row(
                        key = "s_${o.course.id}_${o.start}",
                        courseId = o.course.id,
                        courseDate = o.date.toString(),
                        startPeriod = o.startPeriod,
                        endPeriod = o.endPeriod,
                        startTime = o.start.hm(),
                        endTime = o.end.hm(),
                        name = o.course.name,
                        room = o.room.trim(),
                        colorIndex = o.course.colorIndex,
                        phase = WidgetKit.timePhase(o.start, o.end, now),
                        progress = WidgetKit.timeProgress(o.start, o.end, now),
                    )
                }
        }

        internal fun build(
            ctx: Context,
            widgetId: Int,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
            size: WidgetKit.SizeClass = WidgetKit.SizeClass(),
        ): RemoteViews {
            if (size.isMini) return NextWidgetProvider.build(ctx, snap, st, now, size)
            val views = RemoteViews(ctx.packageName, R.layout.widget_schedule)
            val today = now.toLocalDate()
            val live = snap?.live()
            views.setTextViewText(R.id.sched_title, if (size.compactWidth) ctx.monthDay(today) else ctx.monthDayWeekday(today))
            views.setTextViewTextSize(R.id.sched_title, TypedValue.COMPLEX_UNIT_SP, if (size.compactWidth) 14f else 15f)
            views.setViewPadding(
                R.id.sched_header,
                WidgetKit.dp(ctx, if (size.compactWidth) 12 else 16),
                0,
                WidgetKit.dp(ctx, if (size.compactWidth) 10 else 12),
                WidgetKit.dp(ctx, 6),
            )
            val weekBadge = WidgetKit.weekBadge(ctx, live?.currentSemester, today)
            if (!size.compactWidth && weekBadge != null) {
                views.setViewVisibility(R.id.sched_week, View.VISIBLE)
                views.setTextViewText(R.id.sched_week, weekBadge)
            } else {
                views.setViewVisibility(R.id.sched_week, View.GONE)
            }

            views.setTextViewText(R.id.sched_empty, if (snap == null) "" else ctx.getString(R.string.widget_schedule_empty))
            views.setRemoteAdapter(
                R.id.sched_list,
                Intent(ctx, ScheduleWidgetService::class.java)
                    .setData(Uri.parse("goke://widget/schedule/$widgetId?compact=${size.compactWidth}")),
            )
            views.setEmptyView(R.id.sched_list, R.id.sched_empty)
            views.setPendingIntentTemplate(
                R.id.sched_list,
                PendingIntent.getActivity(
                    ctx,
                    400 + widgetId,
                    Intent(ctx, WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

            val openTimetable = WidgetKit.mainActivityIntent(ctx, 40, DeepLinks.timetableUri())
            views.setOnClickPendingIntent(R.id.sched_header, openTimetable)
            views.setOnClickPendingIntent(R.id.sched_empty, openTimetable)
            views.setOnClickPendingIntent(R.id.widget_root, openTimetable)
            return views
        }
    }
}
