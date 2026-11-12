package app.mintcal.widget

import android.app.AlarmManager
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
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.NextUps
import app.mintcal.core.model.Snapshot
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.data.Settings
import app.mintcal.mintApp
import app.mintcal.ui.hm
import app.mintcal.ui.monthDay
import java.time.LocalDateTime
import java.time.ZoneId

/** 「下一件事」紧凑 4×1 胶囊小组件：下一节课 / 日程块 / 待取快递 / 今日待办 + 一键开启专注与新建。 */
class NextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.mintApp
        val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
        val st = app.settings.value
        val views = build(context, snap, st)
        for (id in appWidgetIds) manager.updateAppWidget(id, views)
        if (snap != null) scheduleTick(context, snap, st)
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

    private data class Summary(
        val badge: String,
        val sub: String?,
        val title: String,
        val barColor: Int,
        val uri: Uri,
    )

    companion object {
        const val ACTION_TICK = "app.mintcal.widget.NEXT_TICK"

        fun refreshAll(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextWidgetProvider::class.java))
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
                    .flatMap { item ->
                        when (item) {
                            is CourseItem -> listOf(item.occ.start, item.occ.end)
                            is BlockItem -> if (!item.task.done) listOf(item.start, item.end) else emptyList()
                            else -> emptyList()
                        }
                    }.filter { it.isAfter(now) }
                    .minOrNull()
                    ?: now
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay()
                        .plusMinutes(1)
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

        private fun pickSummary(
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

            // 1. 当前正在进行的课程或时间块
            for (item in dayItems) {
                when (item) {
                    is CourseItem -> {
                        val o = item.occ
                        if (!o.start.isAfter(now) && o.end.isAfter(now)) {
                            return Summary(
                                badge = ctx.getString(R.string.widget_ongoing),
                                sub = listOfNotNull("${o.start.hm()}–${o.end.hm()}", o.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                                title = o.course.name,
                                barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                                uri = DeepLinks.timetableUri(),
                            )
                        }
                    }

                    is BlockItem -> {
                        if (!item.task.done && !item.start.isAfter(now) && item.end.isAfter(now)) {
                            return Summary(
                                badge = ctx.getString(R.string.widget_ongoing),
                                sub = "${item.start.hm()}–${item.end.hm()}",
                                title = item.task.title,
                                barColor = WidgetKit.priorityColor(ctx, item.task.priority),
                                uri = DeepLinks.taskUri(item.task.id),
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
                            (it is BlockItem && !it.task.done && it.start.isAfter(now))
                    }.minByOrNull { it.sortKey }
            if (upcomingTimed is CourseItem) {
                val o = upcomingTimed.occ
                return Summary(
                    badge = o.start.hm(),
                    sub = o.room.takeIf { it.isNotBlank() },
                    title = o.course.name,
                    barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                    uri = DeepLinks.timetableUri(),
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
                    uri = DeepLinks.timetableUri(),
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
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_next)
            val s = pickSummary(ctx, snap, st, now)
            views.setInt(R.id.next_bar, "setColorFilter", s.barColor)
            views.setTextViewText(R.id.next_badge, s.badge)
            if (!s.sub.isNullOrBlank()) {
                views.setViewVisibility(R.id.next_sub, View.VISIBLE)
                views.setTextViewText(R.id.next_sub, s.sub)
            } else {
                views.setViewVisibility(R.id.next_sub, View.GONE)
            }
            views.setTextViewText(R.id.next_title, s.title)

            views.setOnClickPendingIntent(R.id.next_body, WidgetKit.mainActivityIntent(ctx, 50, s.uri))
            views.setOnClickPendingIntent(R.id.widget_root, WidgetKit.mainActivityIntent(ctx, 50, s.uri))
            views.setOnClickPendingIntent(R.id.next_add, WidgetKit.mainActivityIntent(ctx, 52, DeepLinks.addUri()))
            return views
        }
    }
}
