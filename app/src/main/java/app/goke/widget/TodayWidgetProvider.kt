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
import android.view.View
import android.widget.RemoteViews
import app.goke.DeepLinks
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.TaskItem
import app.goke.core.model.Snapshot
import app.goke.core.todo.TaskLogic
import app.goke.data.Settings
import app.goke.gokeApp
import app.goke.ui.hm
import app.goke.ui.monthDay
import app.goke.ui.monthDayWeekday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 「今日安排」小组件：统一可滚动时间线（课程 + 时间块 + 今日待办 + 待取快递），支持桌面直接打勾。 */
@Suppress("DEPRECATION")
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.gokeApp
        val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
        val st = app.settings.value
        for (id in appWidgetIds) {
            val size = WidgetKit.sizeClass(context, manager, id)
            manager.updateAppWidget(id, build(context, id, snap, st, size = size))
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.today_list)
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
            build(context, appWidgetId, snap, st, size = WidgetKit.sizeClass(context, newOptions)),
        )
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.today_list)
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

    data class Row(
        val key: String,
        val time: String,
        val endTime: String? = null,
        val title: String,
        val sub: String?,
        val code: String?,
        /** 课程 / 日程的植物色序号；待办行用 -1 表示无彩底。 */
        val colorIndex: Int,
        val priority: Int,
        val overdue: Boolean,
        val ongoing: Boolean,
        val finished: Boolean = false,
        val progress: Float = 0f,
        val taskId: String?,
        val targetUri: String,
        val courseId: String? = null,
        val eventId: String? = null,
        val courseDate: String? = null,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
    )

    companion object {
        const val ACTION_TICK = "app.goke.widget.TODAY_TICK"
        private const val MAX_ROWS = 40

        fun refreshAll(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) {
                val size = WidgetKit.sizeClass(ctx, mgr, id)
                mgr.updateAppWidget(id, build(ctx, id, snap, st, size = size))
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.today_list)
            scheduleTick(ctx, snap, st)
        }

        private fun scheduleTick(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ) {
            val dayItems = Agenda.forDay(snap, now.toLocalDate(), st.defaultBlockMinutes)
            val hasOngoing =
                dayItems.any { item ->
                    when (item) {
                        is CourseItem -> WidgetKit.timePhase(item.occ.start, item.occ.end, now) == WidgetKit.TimePhase.ONGOING
                        is EventItem -> !item.allDay && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        is BlockItem -> !item.task.done && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        else -> false
                    }
                }
            val transitions =
                dayItems
                    .flatMap { item ->
                        when (item) {
                            is CourseItem -> listOf(item.occ.start, item.occ.end)
                            is EventItem -> if (!item.allDay) listOf(item.start, item.end) else emptyList()
                            is BlockItem -> if (!item.task.done) listOf(item.start, item.end) else emptyList()
                            else -> emptyList()
                        }
                    }.filter { it.isAfter(now) }
            val nextTransition =
                transitions.minOrNull()
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
                    17,
                    Intent(ctx, TodayWidgetProvider::class.java).setAction(ACTION_TICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }

        fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.gokeApp
            if (!app.repo.ready.value) return emptyList()
            return buildRows(ctx, app.repo.snapshot.value, app.settings.value, now)
        }

        fun buildRows(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val live = snap.live()
            val today = now.toLocalDate()
            val items =
                Agenda.forDay(live, today, st.defaultBlockMinutes).filter { item ->
                    when (item) {
                        is CourseItem -> item.occ.end.isAfter(now)
                        is TaskItem -> !item.task.done
                        is BlockItem -> !item.task.done
                        is EventItem -> true
                    }
                }
            val listColor = live.lists.associate { it.id to it.colorIndex }
            val shownTaskIds = HashSet<String>()
            val result = mutableListOf<Row>()

            for (item in items) {
                when (item) {
                    is CourseItem -> {
                        val o = item.occ
                        val phase = WidgetKit.timePhase(o.start, o.end, now)
                        val progress = WidgetKit.timeProgress(o.start, o.end, now)
                        val sub = o.room.takeIf { it.isNotBlank() }
                        result +=
                            Row(
                                key = "c_${o.course.id}_${o.start}",
                                time = o.start.hm(),
                                endTime = o.end.hm(),
                                title = o.course.name,
                                sub = sub,
                                code = null,
                                colorIndex = o.course.colorIndex,
                                priority = 0,
                                overdue = false,
                                ongoing = phase == WidgetKit.TimePhase.ONGOING,
                                finished = phase == WidgetKit.TimePhase.FINISHED,
                                progress = progress,
                                taskId = null,
                                targetUri = DeepLinks.courseUri(o.course.id).toString(),
                                courseId = o.course.id,
                                courseDate = o.date.toString(),
                                startPeriod = o.startPeriod,
                                endPeriod = o.endPeriod,
                            )
                    }

                    is EventItem -> {
                        val phase =
                            if (item.allDay) WidgetKit.TimePhase.UPCOMING else WidgetKit.timePhase(item.start, item.end, now)
                        val progress =
                            if (item.allDay) 0f else WidgetKit.timeProgress(item.start, item.end, now)
                        result +=
                            Row(
                                key = "e_${item.event.id}_${item.start}",
                                time = if (item.allDay) ctx.getString(R.string.all_day) else item.start.hm(),
                                endTime = if (!item.allDay && item.end.isAfter(item.start)) item.end.hm() else null,
                                title = item.event.title,
                                sub = WidgetKit.eventLocation(ctx, item.event),
                                code = null,
                                colorIndex = item.event.colorIndex,
                                priority = 0,
                                overdue = false,
                                ongoing = phase == WidgetKit.TimePhase.ONGOING,
                                finished = phase == WidgetKit.TimePhase.FINISHED,
                                progress = progress,
                                taskId = null,
                                targetUri = DeepLinks.todayUri().toString(),
                                eventId = item.event.id,
                            )
                    }

                    is BlockItem -> {
                        val t = item.task
                        shownTaskIds += t.id
                        val code = WidgetKit.parcelCode(t)
                        val isCourseStyle = code == null
                        val phase = WidgetKit.timePhase(item.start, item.end, now)
                        val finished = t.done || phase == WidgetKit.TimePhase.FINISHED
                        val ongoing = !finished && phase == WidgetKit.TimePhase.ONGOING
                        val cleanTitle = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title
                        result +=
                            Row(
                                key = "b_${t.id}",
                                time = item.start.hm(),
                                endTime = if (item.end.isAfter(item.start)) item.end.hm() else null,
                                title = cleanTitle,
                                sub = if (isCourseStyle) WidgetKit.taskLocation(ctx, t) else null,
                                code = code,
                                colorIndex = if (isCourseStyle) (listColor[t.listId] ?: 1) else -1,
                                priority = t.priority,
                                overdue = false,
                                ongoing = ongoing,
                                finished = finished,
                                progress = if (finished) 1f else WidgetKit.timeProgress(item.start, item.end, now),
                                taskId = t.id,
                                targetUri = DeepLinks.taskUri(t.id).toString(),
                            )
                    }

                    is TaskItem -> {
                        val t = item.task
                        shownTaskIds += t.id
                        val code = WidgetKit.parcelCode(t)
                        val cleanTitle = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title
                        val overdue = item.hasTime && item.due.isBefore(now)
                        result +=
                            Row(
                                key = "t_${t.id}",
                                time = if (item.hasTime) item.due.hm() else ctx.getString(R.string.tab_todo),
                                title = cleanTitle,
                                sub = null,
                                code = code,
                                colorIndex = -1,
                                priority = t.priority,
                                overdue = overdue,
                                ongoing = false,
                                taskId = t.id,
                                targetUri = DeepLinks.taskUri(t.id).toString(),
                            )
                    }
                }
            }

            // 尚未排进今天但仍待取的快递，也放在今日清单底部，避免漏拿
            for (t in TaskLogic.topLevel(live.tasks)) {
                if (!t.done && t.id !in shownTaskIds && WidgetKit.isParcel(t)) {
                    val code = WidgetKit.parcelCode(t)
                    result +=
                        Row(
                            key = "p_${t.id}",
                            time = ctx.getString(R.string.widget_parcel_title),
                            title = WidgetKit.cleanTitleWithCode(ctx, t),
                            sub = null,
                            code = code,
                            colorIndex = -1,
                            priority = t.priority,
                            overdue = false,
                            ongoing = false,
                            taskId = t.id,
                            targetUri = DeepLinks.taskUri(t.id).toString(),
                        )
                }
            }

            return result.take(MAX_ROWS)
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
            val views = RemoteViews(ctx.packageName, R.layout.widget_today)
            val today: LocalDate = now.toLocalDate()
            views.setTextViewText(R.id.widget_title, if (size.compactWidth) ctx.monthDay(today) else ctx.monthDayWeekday(today))
            val weekBadge = WidgetKit.weekBadge(ctx, snap?.live()?.currentSemester, today)
            if (!size.compactWidth && weekBadge != null) {
                views.setViewVisibility(R.id.today_week_sep, View.VISIBLE)
                views.setViewVisibility(R.id.today_week, View.VISIBLE)
                views.setTextViewText(R.id.today_week, weekBadge)
            } else {
                views.setViewVisibility(R.id.today_week_sep, View.GONE)
                views.setViewVisibility(R.id.today_week, View.GONE)
            }
            views.setRemoteAdapter(
                R.id.today_list,
                Intent(ctx, TodayWidgetService::class.java)
                    .setData(Uri.parse("goke://widget/today/$widgetId?compact=${size.compactWidth}")),
            )
            views.setEmptyView(R.id.today_list, R.id.today_empty)
            views.setPendingIntentTemplate(
                R.id.today_list,
                PendingIntent.getActivity(
                    ctx,
                    200 + widgetId,
                    Intent(ctx, WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            val openToday = WidgetKit.mainActivityIntent(ctx, 20, DeepLinks.todayUri())
            views.setOnClickPendingIntent(R.id.today_header, openToday)
            views.setOnClickPendingIntent(R.id.today_empty, openToday)
            views.setOnClickPendingIntent(R.id.widget_root, openToday)
            views.setOnClickPendingIntent(R.id.today_add, WidgetKit.overlayIntent(ctx, 21, WidgetOverlayActivity.addIntent(ctx)))
            return views
        }
    }
}
