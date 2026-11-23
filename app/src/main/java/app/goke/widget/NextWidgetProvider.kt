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
import app.goke.core.agenda.NextUps
import app.goke.core.model.Snapshot
import app.goke.core.todo.SmartList
import app.goke.core.todo.TaskLogic
import app.goke.data.Settings
import app.goke.gokeApp
import app.goke.ui.hm
import app.goke.ui.monthDay
import java.time.LocalDateTime
import java.time.ZoneId

/** 「下一件事」紧凑 4×1 胶囊小组件：下一节课 / 日程块 / 待取快递 / 今日待办 + 一键开启专注与新建。 */
class NextWidgetProvider : AppWidgetProvider() {
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

    internal data class Summary(
        val badge: String,
        val sub: String?,
        val title: String,
        val barColor: Int,
        val uri: Uri,
        val ongoing: Boolean = false,
        val progress: Float = 0f,
        val taskId: String? = null,
        val courseId: String? = null,
        val eventId: String? = null,
        val courseDate: String? = null,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
    )

    companion object {
        const val ACTION_TICK = "app.goke.widget.NEXT_TICK"

        fun refreshAll(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextWidgetProvider::class.java))
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
            val dayItems = Agenda.forDay(snap, now.toLocalDate(), st.defaultBlockMinutes)
            val hasOngoing =
                dayItems.any { item ->
                    when (item) {
                        is CourseItem -> WidgetKit.timePhase(item.occ.start, item.occ.end, now) == WidgetKit.TimePhase.ONGOING
                        is BlockItem -> !item.task.done && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        is EventItem -> !item.allDay && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        else -> false
                    }
                }
            val nextTransition =
                dayItems
                    .flatMap { item ->
                        when (item) {
                            is CourseItem -> listOf(item.occ.start, item.occ.end)
                            is BlockItem -> if (!item.task.done) listOf(item.start, item.end) else emptyList()
                            is EventItem -> if (!item.allDay) listOf(item.start, item.end) else emptyList()
                            else -> emptyList()
                        }
                    }.filter { it.isAfter(now) }
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
                    27,
                    Intent(ctx, NextWidgetProvider::class.java).setAction(ACTION_TICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }

        internal fun pickSummary(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime,
        ): Summary {
            val accent = ctx.getColor(R.color.widget_accent)
            if (snap == null) {
                return Summary(
                    badge = ctx.getString(R.string.widget_next_label),
                    sub = null,
                    title = ctx.getString(R.string.widget_next_empty),
                    barColor = accent,
                    uri = DeepLinks.todayUri(),
                )
            }
            val live = snap.live()
            val today = now.toLocalDate()
            val dayItems = Agenda.forDay(live, today, st.defaultBlockMinutes)

            // 1. 当前正在进行的课程或时间块（用进度置灰底取代「进行中」标签）
            for (item in dayItems) {
                when (item) {
                    is CourseItem -> {
                        val o = item.occ
                        if (WidgetKit.timePhase(o.start, o.end, now) == WidgetKit.TimePhase.ONGOING) {
                            return Summary(
                                badge = "–${o.end.hm()}",
                                sub = o.room.takeIf { it.isNotBlank() } ?: o.start.hm(),
                                title = o.course.name,
                                barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                                uri = DeepLinks.courseUri(o.course.id),
                                ongoing = true,
                                progress = WidgetKit.timeProgress(o.start, o.end, now),
                                courseId = o.course.id,
                                courseDate = o.date.toString(),
                                startPeriod = o.startPeriod,
                                endPeriod = o.endPeriod,
                            )
                        }
                    }

                    is BlockItem -> {
                        if (!item.task.done && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING) {
                            return Summary(
                                badge = "–${item.end.hm()}",
                                sub = item.start.hm(),
                                title = item.task.title,
                                barColor = WidgetKit.priorityColor(ctx, item.task.priority),
                                uri = DeepLinks.taskUri(item.task.id),
                                ongoing = true,
                                progress = WidgetKit.timeProgress(item.start, item.end, now),
                                taskId = item.task.id,
                            )
                        }
                    }

                    is EventItem -> {
                        if (!item.allDay && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING) {
                            return Summary(
                                badge = "–${item.end.hm()}",
                                sub = WidgetKit.eventLocation(ctx, item.event) ?: item.start.hm(),
                                title = item.event.title,
                                barColor = WidgetKit.courseFg(ctx, item.event.colorIndex),
                                uri = DeepLinks.todayUri(),
                                ongoing = true,
                                progress = WidgetKit.timeProgress(item.start, item.end, now),
                                eventId = item.event.id,
                            )
                        }
                    }

                    else -> {}
                }
            }

            // 2. 今天接下来最早开始的课程或时间块
            val upcomingTimed =
                dayItems
                    .filter {
                        (it is CourseItem && it.occ.start.isAfter(now)) ||
                            (it is BlockItem && !it.task.done && it.start.isAfter(now)) ||
                            (it is EventItem && !it.allDay && it.start.isAfter(now))
                    }.minByOrNull { it.sortKey }
            if (upcomingTimed is CourseItem) {
                val o = upcomingTimed.occ
                return Summary(
                    badge = o.start.hm(),
                    sub = o.room.takeIf { it.isNotBlank() },
                    title = o.course.name,
                    barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                    uri = DeepLinks.courseUri(o.course.id),
                    courseId = o.course.id,
                    courseDate = o.date.toString(),
                    startPeriod = o.startPeriod,
                    endPeriod = o.endPeriod,
                )
            }
            if (upcomingTimed is BlockItem) {
                val t = upcomingTimed.task
                val code = WidgetKit.parcelCode(t)
                return Summary(
                    badge = code ?: upcomingTimed.start.hm(),
                    sub = if (code != null) upcomingTimed.start.hm() else null,
                    title = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title,
                    barColor = WidgetKit.priorityColor(ctx, t.priority),
                    uri = DeepLinks.taskUri(t.id),
                    taskId = t.id,
                )
            }
            if (upcomingTimed is EventItem) {
                return Summary(
                    badge = upcomingTimed.start.hm(),
                    sub = WidgetKit.eventLocation(ctx, upcomingTimed.event),
                    title = upcomingTimed.event.title,
                    barColor = WidgetKit.courseFg(ctx, upcomingTimed.event.colorIndex),
                    uri = DeepLinks.todayUri(),
                    eventId = upcomingTimed.event.id,
                )
            }

            // 3. 待取快递（大号取件码直接上胶囊）
            val parcel = TaskLogic.topLevel(live.tasks).firstOrNull { !it.done && WidgetKit.isParcel(it) }
            if (parcel != null) {
                val code = WidgetKit.parcelCode(parcel) ?: ctx.getString(R.string.widget_parcel_title)
                return Summary(
                    badge = code,
                    sub = ctx.getString(R.string.widget_parcel_name),
                    title = WidgetKit.cleanTitleWithCode(ctx, parcel),
                    barColor = WidgetKit.priorityColor(ctx, parcel.priority),
                    uri = DeepLinks.taskUri(parcel.id),
                    taskId = parcel.id,
                )
            }

            // 4. 逾期或今日未完成待办
            val overdue = TaskLogic.filter(live.tasks, SmartList.OVERDUE, now).firstOrNull { !it.done }
            val topTask = overdue ?: TaskLogic.filter(live.tasks, SmartList.TODAY, now).firstOrNull { !it.done }
            if (topTask != null) {
                return Summary(
                    badge = ctx.getString(if (overdue != null) R.string.bucket_overdue else R.string.tab_todo),
                    sub = null,
                    title = topTask.title,
                    barColor = WidgetKit.priorityColor(ctx, topTask.priority, overdue != null),
                    uri = DeepLinks.taskUri(topTask.id),
                    taskId = topTask.id,
                )
            }

            // 5. 未来 7 天内的下一节课
            val nextCourse = NextUps.find(live, now)
            if (nextCourse != null) {
                val o = nextCourse.occurrence
                return Summary(
                    badge = ctx.monthDay(o.date),
                    sub = listOfNotNull(o.start.hm(), o.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                    title = o.course.name,
                    barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                    uri = DeepLinks.courseUri(o.course.id),
                    courseId = o.course.id,
                    courseDate = o.date.toString(),
                    startPeriod = o.startPeriod,
                    endPeriod = o.endPeriod,
                )
            }

            return Summary(
                badge = ctx.getString(R.string.widget_next_label),
                sub = null,
                title = ctx.getString(R.string.widget_next_empty),
                barColor = accent,
                uri = DeepLinks.todayUri(),
            )
        }

        fun build(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
            compact: Boolean = false,
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_next)
            val s = pickSummary(ctx, snap, st, now)
            if (s.ongoing) {
                views.setViewVisibility(R.id.next_progress, View.VISIBLE)
                views.setProgressBar(R.id.next_progress, WidgetKit.PROGRESS_MAX, WidgetKit.progressPermille(s.progress), false)
            } else {
                views.setViewVisibility(R.id.next_progress, View.GONE)
            }
            views.setViewVisibility(R.id.next_bar, if (compact) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.next_add, if (compact) View.GONE else View.VISIBLE)
            views.setInt(R.id.next_bar, "setColorFilter", s.barColor)
            views.setTextViewText(R.id.next_badge, s.badge)
            if (!s.sub.isNullOrBlank()) {
                views.setViewVisibility(R.id.next_sub, View.VISIBLE)
                views.setTextViewText(R.id.next_sub, s.sub)
            } else {
                views.setViewVisibility(R.id.next_sub, View.GONE)
            }
            views.setTextViewText(R.id.next_title, s.title)

            val bodyPending =
                when {
                    s.taskId != null -> {
                        WidgetKit.overlayIntent(ctx, 50, WidgetOverlayActivity.taskIntent(ctx, s.taskId))
                    }

                    s.courseId != null -> {
                        WidgetKit.overlayIntent(
                            ctx,
                            50,
                            WidgetOverlayActivity.courseIntent(ctx, s.courseId, s.courseDate, s.startPeriod, s.endPeriod),
                        )
                    }

                    s.eventId != null -> {
                        WidgetKit.overlayIntent(ctx, 50, WidgetOverlayActivity.eventIntent(ctx, s.eventId))
                    }

                    else -> {
                        WidgetKit.mainActivityIntent(ctx, 50, s.uri)
                    }
                }
            views.setOnClickPendingIntent(R.id.next_body, bodyPending)
            views.setOnClickPendingIntent(R.id.widget_root, bodyPending)
            views.setOnClickPendingIntent(R.id.next_add, WidgetKit.overlayIntent(ctx, 52, WidgetOverlayActivity.addIntent(ctx)))
            return views
        }
    }
}
