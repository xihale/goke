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
import app.goke.core.agenda.AgendaItem
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.model.Snapshot
import app.goke.core.recurrence.CourseExpander
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
            val size = WidgetKit.sizeClass(context, manager, id)
            manager.updateAppWidget(id, build(context, snap, st, size = size))
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
        appWidgetManager.updateAppWidget(appWidgetId, build(context, snap, st, size = WidgetKit.sizeClass(context, newOptions)))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val app = context.gokeApp
            if (app.repo.ready.value) {
                val snap = app.repo.snapshot.value
                val st = app.settings.value
                refreshAll(context, snap, st)
                TodoWidgetProvider.refreshMini(context)
                ParcelWidgetProvider.refreshMini(context)
            }
        }
    }

    internal enum class BadgeTone { MUTED, CODE, OVERDUE }

    internal data class Summary(
        val badge: String = "",
        val badgeTone: BadgeTone = BadgeTone.MUTED,
        val startTime: String? = null,
        val endTime: String? = null,
        val sub: String? = null,
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
    ) {
        fun timeRangeText(): String? =
            when {
                startTime != null && endTime != null -> "$startTime–$endTime"
                startTime != null -> startTime
                else -> null
            }
    }

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
                val size = WidgetKit.sizeClass(ctx, mgr, id)
                mgr.updateAppWidget(id, build(ctx, snap, st, size = size))
            }
            scheduleTick(ctx, snap, st)
        }

        internal fun scheduleTick(
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

        private fun timeRange(
            start: LocalDateTime,
            end: LocalDateTime,
        ): String = if (end.isAfter(start)) "${start.hm()}–${end.hm()}" else start.hm()

        private fun summaryForTimed(
            ctx: Context,
            item: AgendaItem,
            now: LocalDateTime,
            ongoing: Boolean,
            listColor: Map<String, Int> = emptyMap(),
        ): Summary? =
            when (item) {
                is CourseItem -> {
                    val o = item.occ
                    Summary(
                        startTime = o.start.hm(),
                        endTime = o.end.hm(),
                        sub = o.room.takeIf { it.isNotBlank() },
                        title = o.course.name,
                        barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                        uri = DeepLinks.courseUri(o.course.id),
                        ongoing = ongoing,
                        progress = if (ongoing) WidgetKit.timeProgress(o.start, o.end, now) else 0f,
                        courseId = o.course.id,
                        courseDate = o.date.toString(),
                        startPeriod = o.startPeriod,
                        endPeriod = o.endPeriod,
                    )
                }

                is BlockItem -> {
                    val t = item.task
                    val code = WidgetKit.parcelCode(t)
                    val colorIdx = listColor[t.listId] ?: 1
                    Summary(
                        badge = code.orEmpty(),
                        badgeTone = if (code != null) BadgeTone.CODE else BadgeTone.MUTED,
                        startTime = item.start.hm(),
                        endTime = if (item.end.isAfter(item.start)) item.end.hm() else null,
                        sub = if (code != null) null else WidgetKit.taskLocation(ctx, t),
                        title = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title,
                        barColor = if (code != null) WidgetKit.priorityColor(ctx, t.priority) else WidgetKit.courseFg(ctx, colorIdx),
                        uri = DeepLinks.taskUri(t.id),
                        ongoing = ongoing,
                        progress = if (ongoing) WidgetKit.timeProgress(item.start, item.end, now) else 0f,
                        taskId = t.id,
                    )
                }

                is EventItem -> {
                    Summary(
                        startTime = item.start.hm(),
                        endTime = if (item.end.isAfter(item.start)) item.end.hm() else null,
                        sub = WidgetKit.eventLocation(ctx, item.event),
                        title = item.event.title,
                        barColor = WidgetKit.courseFg(ctx, item.event.colorIndex),
                        uri = DeepLinks.todayUri(),
                        ongoing = ongoing,
                        progress = if (ongoing) WidgetKit.timeProgress(item.start, item.end, now) else 0f,
                        eventId = item.event.id,
                    )
                }

                else -> {
                    null
                }
            }

        internal fun pickSummary(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime,
        ): Summary = pickSummaries(ctx, snap, st, now, limit = 1).first()

        internal fun pickSummaries(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime,
            limit: Int = 2,
        ): List<Summary> {
            val accent = ctx.getColor(R.color.widget_accent)
            if (snap == null) {
                return listOf(
                    Summary(
                        badge = ctx.getString(R.string.widget_next_label),
                        title = ctx.getString(R.string.widget_next_empty),
                        barColor = accent,
                        uri = DeepLinks.todayUri(),
                    ),
                )
            }
            val live = snap.live()
            val today = now.toLocalDate()
            val listColor = live.lists.associate { it.id to it.colorIndex }
            val dayItems = Agenda.forDay(live, today, st.defaultBlockMinutes)
            val result = mutableListOf<Summary>()
            val seenTaskIds = HashSet<String>()
            val seenCourses = HashSet<String>()

            fun addUnique(summary: Summary) {
                if (result.size >= limit) return
                summary.taskId?.let { if (!seenTaskIds.add(it)) return }
                if (summary.courseId != null) {
                    val key = "${summary.courseId}_${summary.courseDate}_${summary.startPeriod}"
                    if (!seenCourses.add(key)) return
                }
                result += summary
            }

            // 1. 当前正在进行的课程、日程或时间块
            dayItems
                .filter { item ->
                    when (item) {
                        is CourseItem -> WidgetKit.timePhase(item.occ.start, item.occ.end, now) == WidgetKit.TimePhase.ONGOING
                        is BlockItem -> !item.task.done && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        is EventItem -> !item.allDay && WidgetKit.timePhase(item.start, item.end, now) == WidgetKit.TimePhase.ONGOING
                        else -> false
                    }
                }.forEach { item -> summaryForTimed(ctx, item, now, ongoing = true, listColor = listColor)?.let(::addUnique) }

            // 2. 今天接下来即将开始的课程、日程或时间块
            if (result.size < limit) {
                dayItems
                    .filter {
                        (it is CourseItem && it.occ.start.isAfter(now)) ||
                            (it is BlockItem && !it.task.done && it.start.isAfter(now)) ||
                            (it is EventItem && !it.allDay && it.start.isAfter(now))
                    }.forEach { item -> summaryForTimed(ctx, item, now, ongoing = false, listColor = listColor)?.let(::addUnique) }
            }

            // 3. 待取快递
            if (result.size < limit) {
                for (parcel in TaskLogic.topLevel(live.tasks)) {
                    if (result.size >= limit) break
                    if (!parcel.done && WidgetKit.isParcel(parcel) && parcel.id !in seenTaskIds) {
                        val code = WidgetKit.parcelCode(parcel) ?: ctx.getString(R.string.widget_parcel_title)
                        addUnique(
                            Summary(
                                badge = code,
                                badgeTone = BadgeTone.CODE,
                                sub = ctx.getString(R.string.widget_parcel_name),
                                title = WidgetKit.cleanTitleWithCode(ctx, parcel),
                                barColor = WidgetKit.priorityColor(ctx, parcel.priority),
                                uri = DeepLinks.taskUri(parcel.id),
                                taskId = parcel.id,
                            ),
                        )
                    }
                }
            }

            // 4. 逾期或今日未完成待办（有时间块的日程仅在步骤 1–2 按起止时间展示，已结束不回流为待办）
            if (result.size < limit) {
                val overdue = TaskLogic.filter(live.tasks, SmartList.OVERDUE, now).filter { !it.done && it.blockStart == null }
                val todayTasks = TaskLogic.filter(live.tasks, SmartList.TODAY, now).filter { !it.done && it.blockStart == null && it !in overdue }
                for (task in overdue + todayTasks) {
                    if (result.size >= limit) break
                    if (task.id !in seenTaskIds) {
                        val isOverdue = task in overdue
                        addUnique(
                            Summary(
                                badge = ctx.getString(if (isOverdue) R.string.bucket_overdue else R.string.tab_todo),
                                badgeTone = if (isOverdue) BadgeTone.OVERDUE else BadgeTone.MUTED,
                                sub = WidgetKit.taskLocation(ctx, task),
                                title = task.title,
                                barColor = WidgetKit.priorityColor(ctx, task.priority, isOverdue),
                                uri = DeepLinks.taskUri(task.id),
                                taskId = task.id,
                            ),
                        )
                    }
                }
            }

            // 5. 未来 7 天内的下几节课
            if (result.size < limit) {
                live.currentSemester?.let { sem ->
                    CourseExpander
                        .occurrences(sem, live.courses, live.exceptions, today, today.plusDays(7))
                        .filter { it.end.isAfter(now) }
                        .forEach { o ->
                            if (result.size < limit) {
                                addUnique(
                                    Summary(
                                        startTime = o.start.hm(),
                                        endTime = o.end.hm(),
                                        sub = listOfNotNull(ctx.monthDay(o.date), o.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                                        title = o.course.name,
                                        barColor = WidgetKit.courseFg(ctx, o.course.colorIndex),
                                        uri = DeepLinks.courseUri(o.course.id),
                                        courseId = o.course.id,
                                        courseDate = o.date.toString(),
                                        startPeriod = o.startPeriod,
                                        endPeriod = o.endPeriod,
                                    ),
                                )
                            }
                        }
                }
            }

            if (result.isEmpty()) {
                result +=
                    Summary(
                        badge = ctx.getString(R.string.widget_next_label),
                        title = ctx.getString(R.string.widget_next_empty),
                        barColor = accent,
                        uri = DeepLinks.todayUri(),
                    )
            }
            return result
        }

        private fun pendingIntentFor(
            ctx: Context,
            s: Summary,
            requestCode: Int,
        ): PendingIntent =
            when {
                s.taskId != null -> {
                    WidgetKit.overlayIntent(ctx, requestCode, WidgetOverlayActivity.taskIntent(ctx, s.taskId))
                }

                s.courseId != null -> {
                    WidgetKit.overlayIntent(
                        ctx,
                        requestCode,
                        WidgetOverlayActivity.courseIntent(ctx, s.courseId, s.courseDate, s.startPeriod, s.endPeriod),
                    )
                }

                s.eventId != null -> {
                    WidgetKit.overlayIntent(ctx, requestCode, WidgetOverlayActivity.eventIntent(ctx, s.eventId))
                }

                else -> {
                    WidgetKit.mainActivityIntent(ctx, requestCode, s.uri)
                }
            }

        private fun bindMiniCard(
            ctx: Context,
            views: RemoteViews,
            s: Summary,
            cardId: Int,
            progressId: Int,
            titleId: Int,
            timeId: Int,
            subId: Int,
            requestCode: Int,
        ) {
            views.setViewVisibility(cardId, View.VISIBLE)
            if (s.ongoing) {
                views.setViewVisibility(progressId, View.VISIBLE)
                views.setProgressBar(progressId, WidgetKit.PROGRESS_MAX, WidgetKit.progressPermille(s.progress), false)
            } else {
                views.setViewVisibility(progressId, View.GONE)
            }
            views.setTextViewText(titleId, s.title)
            val timeText = s.timeRangeText() ?: s.badge.takeIf { it.isNotBlank() }
            if (timeText != null) {
                views.setViewVisibility(timeId, View.VISIBLE)
                views.setTextViewText(timeId, timeText)
            } else {
                views.setViewVisibility(timeId, View.GONE)
            }
            val subText = s.sub?.takeIf { it.isNotBlank() }
            if (subText != null) {
                views.setViewVisibility(subId, View.VISIBLE)
                views.setTextViewText(subId, subText)
            } else {
                views.setViewVisibility(subId, View.GONE)
            }
            views.setOnClickPendingIntent(cardId, pendingIntentFor(ctx, s, requestCode))
        }

        private fun buildMiniStack(
            ctx: Context,
            summaries: List<Summary>,
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_next_stack)
            val first = summaries.first()
            bindMiniCard(
                ctx,
                views,
                first,
                cardId = R.id.next_card0,
                progressId = R.id.next_progress0,
                titleId = R.id.next_title0,
                timeId = R.id.next_time0,
                subId = R.id.next_sub0,
                requestCode = 50,
            )
            val second = summaries.getOrNull(1)
            if (second != null) {
                bindMiniCard(
                    ctx,
                    views,
                    second,
                    cardId = R.id.next_card1,
                    progressId = R.id.next_progress1,
                    titleId = R.id.next_title1,
                    timeId = R.id.next_time1,
                    subId = R.id.next_sub1,
                    requestCode = 51,
                )
            } else {
                views.setViewVisibility(R.id.next_card1, View.GONE)
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                WidgetKit.mainActivityIntent(ctx, 53, DeepLinks.timetableUri()),
            )
            return views
        }

        internal fun build(
            ctx: Context,
            snap: Snapshot?,
            st: Settings,
            now: LocalDateTime = LocalDateTime.now(),
            size: WidgetKit.SizeClass = WidgetKit.SizeClass(),
        ): RemoteViews {
            if (size.ultraCompactWidth && !size.compactHeight) {
                return buildMiniStack(ctx, pickSummaries(ctx, snap, st, now, limit = 2))
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_next)
            val s = pickSummary(ctx, snap, st, now)
            if (s.ongoing) {
                views.setViewVisibility(R.id.next_progress, View.VISIBLE)
                views.setProgressBar(R.id.next_progress, WidgetKit.PROGRESS_MAX, WidgetKit.progressPermille(s.progress), false)
            } else {
                views.setViewVisibility(R.id.next_progress, View.GONE)
            }
            views.setViewVisibility(R.id.next_bar, if (size.compactWidth) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.next_add, if (size.compactWidth) View.GONE else View.VISIBLE)
            val showLeftTime = !size.compactWidth && s.startTime != null
            views.setViewVisibility(R.id.next_time_col, if (showLeftTime) View.VISIBLE else View.GONE)
            if (showLeftTime) {
                views.setTextViewText(R.id.next_time, s.startTime)
                if (s.endTime != null) {
                    views.setViewVisibility(R.id.next_end, View.VISIBLE)
                    views.setTextViewText(R.id.next_end, s.endTime)
                } else {
                    views.setViewVisibility(R.id.next_end, View.GONE)
                }
            }
            val padStart = WidgetKit.dp(ctx, if (size.ultraCompactWidth) 6 else 14)
            val padEnd = WidgetKit.dp(ctx, if (size.ultraCompactWidth) 6 else 12)
            val padV = WidgetKit.dp(ctx, if (size.ultraCompactWidth) 6 else 8)
            views.setViewPadding(R.id.next_content, padStart, padV, padEnd, padV)
            views.setInt(R.id.next_bar, "setColorFilter", s.barColor)
            val showBadge = !size.ultraCompactWidth && s.badge.isNotEmpty()
            views.setViewVisibility(R.id.next_badge, if (showBadge) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.next_badge, s.badge)
            when (s.badgeTone) {
                BadgeTone.CODE -> {
                    val pxH = WidgetKit.dp(ctx, 6)
                    val pxV = WidgetKit.dp(ctx, 1)
                    views.setInt(R.id.next_badge, "setBackgroundResource", R.drawable.widget_pill_bg)
                    views.setViewPadding(R.id.next_badge, pxH, pxV, pxH, pxV)
                    views.setTextColor(R.id.next_badge, ctx.getColor(R.color.widget_pill_fg))
                }

                BadgeTone.OVERDUE -> {
                    views.setInt(R.id.next_badge, "setBackgroundResource", 0)
                    views.setViewPadding(R.id.next_badge, 0, 0, 0, 0)
                    views.setTextColor(R.id.next_badge, ctx.getColor(R.color.widget_overdue))
                }

                BadgeTone.MUTED -> {
                    views.setInt(R.id.next_badge, "setBackgroundResource", 0)
                    views.setViewPadding(R.id.next_badge, 0, 0, 0, 0)
                    views.setTextColor(R.id.next_badge, ctx.getColor(R.color.widget_muted))
                }
            }
            val timeRange = s.timeRangeText()
            if (size.ultraCompactWidth) {
                val inlineTime = timeRange ?: s.badge.takeIf { it.isNotBlank() }
                if (inlineTime != null) {
                    views.setViewVisibility(R.id.next_time_inline, View.VISIBLE)
                    views.setTextViewText(R.id.next_time_inline, inlineTime)
                } else {
                    views.setViewVisibility(R.id.next_time_inline, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.next_time_inline, View.GONE)
            }
            val subText =
                when {
                    size.ultraCompactWidth -> s.sub?.takeIf { it.isNotBlank() }
                    size.compactWidth -> listOfNotNull(s.sub?.takeIf { it.isNotBlank() }, timeRange).joinToString(" · ").ifEmpty { null }
                    else -> s.sub?.takeIf { it.isNotBlank() }
                }
            if (subText != null) {
                views.setViewVisibility(R.id.next_sub, View.VISIBLE)
                views.setTextViewText(R.id.next_sub, subText)
            } else {
                views.setViewVisibility(R.id.next_sub, View.GONE)
            }
            views.setTextViewText(R.id.next_title, s.title)

            val bodyPending = pendingIntentFor(ctx, s, 50)
            views.setOnClickPendingIntent(R.id.next_body, bodyPending)
            views.setOnClickPendingIntent(R.id.next_time_col, bodyPending)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                WidgetKit.mainActivityIntent(ctx, 53, DeepLinks.timetableUri()),
            )
            views.setOnClickPendingIntent(R.id.next_add, WidgetKit.overlayIntent(ctx, 52, WidgetOverlayActivity.addIntent(ctx)))
            return views
        }
    }
}
