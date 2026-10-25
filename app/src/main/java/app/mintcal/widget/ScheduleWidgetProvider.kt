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
import app.mintcal.MainActivity
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
 * 「今日课表」小组件：表头 = 学期 / 日期 / 周几，其下按时间列出今天的课，正在上的那节高亮；
 * 没课时显示一句轻松的话。纯 RemoteViews（不依赖 Glance，少一个依赖）。
 * 当前课的高亮靠课程开始 / 结束时刻的一次不精确闹钟刷新，数据变动时由 [app.mintcal.MintCalApp] 防抖刷新。
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

        private val PALETTE =
            intArrayOf(0xFF4FA796.toInt(), 0xFF5B8DEF.toInt(), 0xFFE0A030.toInt(), 0xFFD9695F.toInt(), 0xFF9B7BD0.toInt(), 0xFF4DB6D0.toInt(), 0xFFE07FA8.toInt(), 0xFF8AA64A.toInt())

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

        /** 下一个课程开始 / 结束时刻刷新一次，让「正在上」的高亮跟上。 */
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
                    // 今天没有更多了：明早 0 点后刷新为明天
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
            val head = listOfNotNull(snap?.currentSemester?.name?.takeIf { it.isNotBlank() }, ctx.monthDayWeekday(today)).joinToString(" · ")
            views.setTextViewText(R.id.sched_title, head)
            val courses =
                snap
                    ?.let { s ->
                        Agenda.forDay(s, today, st.defaultBlockMinutes).filterIsInstance<CourseItem>()
                    }.orEmpty()
            val rows = intArrayOf(R.id.sched_row0, R.id.sched_row1, R.id.sched_row2, R.id.sched_row3, R.id.sched_row4, R.id.sched_row5)
            val bars = intArrayOf(R.id.sched_bar0, R.id.sched_bar1, R.id.sched_bar2, R.id.sched_bar3, R.id.sched_bar4, R.id.sched_bar5)
            val names = intArrayOf(R.id.sched_name0, R.id.sched_name1, R.id.sched_name2, R.id.sched_name3, R.id.sched_name4, R.id.sched_name5)
            val metas = intArrayOf(R.id.sched_meta0, R.id.sched_meta1, R.id.sched_meta2, R.id.sched_meta3, R.id.sched_meta4, R.id.sched_meta5)
            if (courses.isEmpty()) {
                views.setViewVisibility(R.id.sched_empty, View.VISIBLE)
                views.setTextViewText(R.id.sched_empty, if (snap == null) "" else ctx.getString(R.string.widget_schedule_empty))
            } else {
                views.setViewVisibility(R.id.sched_empty, View.GONE)
            }
            // 已经结束的课不占位：优先展示正在上 / 之后的，最多 6 条
            val shown = courses.filter { it.occ.end.isAfter(now) }.ifEmpty { courses.takeLast(6) }.take(6)
            val openToday =
                PendingIntent.getActivity(
                    ctx,
                    0,
                    Intent(Intent.ACTION_VIEW, Uri.parse("mintcal://today"), ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            for (i in rows.indices) {
                val c = shown.getOrNull(i)
                if (c == null) {
                    views.setViewVisibility(rows[i], View.GONE)
                    continue
                }
                val o = c.occ
                val current = !o.start.isAfter(now) && o.end.isAfter(now)
                views.setViewVisibility(rows[i], View.VISIBLE)
                views.setInt(rows[i], "setBackgroundResource", if (current) R.drawable.widget_row_now_bg else R.drawable.widget_row_bg)
                views.setInt(bars[i], "setBackgroundColor", PALETTE[Math.floorMod(o.course.colorIndex, PALETTE.size)])
                views.setTextViewText(names[i], o.course.name)
                val meta =
                    listOfNotNull(
                        o.start.hm() + "–" + o.end.hm(),
                        o.room.takeIf { it.isNotBlank() },
                        if (current) ctx.getString(R.string.widget_schedule_now) else null,
                    ).joinToString(" · ")
                views.setTextViewText(metas[i], meta)
                // 点课行打开 app 的今日视图（RemoteViews 不支持横向滚动，周视图不可行）
                views.setOnClickPendingIntent(rows[i], openToday)
            }
            val remaining = courses.count { it.occ.end.isAfter(now) } - shown.size
            if (remaining > 0) {
                views.setViewVisibility(R.id.sched_more, View.VISIBLE)
                views.setTextViewText(R.id.sched_more, ctx.getString(R.string.widget_schedule_more, remaining))
            } else {
                views.setViewVisibility(R.id.sched_more, View.GONE)
            }
            val open =
                PendingIntent.getActivity(
                    ctx,
                    1,
                    Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
