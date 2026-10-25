package app.mintcal.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 一条待触发的提醒。 */
data class Reminder(
    val triggerAt: LocalDateTime,
    val title: String,
    val text: String,
    val key: String,
    /** 待办提醒才有：通知上显示「完成 / 稍后」按钮、点击直达该待办。 */
    val taskId: String? = null,
)

/**
 * 提醒调度：把未来 7 天内最早的若干条提醒交给 AlarmManager。
 * 数据 / 设置变化时由 [app.mintcal.MintCalApp] 重新调度；开机、改时区时由 [BootReceiver] 触发应用进程重建。
 */
class ReminderScheduler(
    private val ctx: Context,
) {
    private val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun reschedule(
        snap: Snapshot,
        st: Settings,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        cancelAll()
        val list = compute(ctx, snap, st, now).take(MAX)
        val zone = ZoneId.systemDefault()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        list.forEachIndexed { i, r ->
            val intent =
                Intent(ctx, ReminderReceiver::class.java)
                    .putExtra(ReminderReceiver.EXTRA_TITLE, r.title)
                    .putExtra(ReminderReceiver.EXTRA_TEXT, r.text)
                    .putExtra(ReminderReceiver.EXTRA_ID, r.key.hashCode())
                    .putExtra(ReminderReceiver.EXTRA_TASK_ID, r.taskId)
            val pi = PendingIntent.getBroadcast(ctx, i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val at =
                r.triggerAt
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            runCatching {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            }
        }
    }

    /** 自检用：[delaySeconds] 秒后发一条测试提醒（走与真实提醒相同的闹钟 → 接收器 → 通知链路）。 */
    fun scheduleTest(
        title: String,
        text: String,
        delaySeconds: Int,
    ): Boolean {
        val intent =
            Intent(ctx, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                .putExtra(ReminderReceiver.EXTRA_ID, TEST_ID)
        val pi = PendingIntent.getBroadcast(ctx, TEST_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at = System.currentTimeMillis() + delaySeconds * 1000L
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        return runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.isSuccess
    }

    private fun cancelAll() {
        for (i in 0 until MAX) {
            val pi =
                PendingIntent.getBroadcast(
                    ctx,
                    i,
                    Intent(ctx, ReminderReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
                )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    companion object {
        const val MAX = 40

        /** 自检测试提醒的请求码，须落在 0 until MAX 之外，免得被重新调度时取消。 */
        const val TEST_ID = 9001
        private val ALL_DAY_REMIND_AT = LocalTime.of(9, 0)
        private val DIGEST_AT = LocalTime.of(7, 30)

        /** 纯计算：未来 7 天内、晚于 now 的提醒，按触发时间排序。 */
        fun compute(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime,
        ): List<Reminder> {
            val from = now.toLocalDate()
            val days = Agenda.range(snap, from, from.plusDays(8), st.defaultBlockMinutes, includeDoneTasks = false)
            val out = mutableListOf<Reminder>()
            for ((date, items) in days) {
                if (st.dailyDigest) {
                    val courses = items.count { it is CourseItem }
                    val tasks = items.count { it is TaskItem && !it.task.done }
                    if (courses + tasks > 0) {
                        out += Reminder(date.atTime(DIGEST_AT), ctx.getString(R.string.digest_title), ctx.getString(R.string.today_summary, courses, tasks), "digest$date")
                    }
                }
                for (item in items) {
                    when (item) {
                        is CourseItem -> {
                            val c = item.occ.course
                            val minutes = c.remindMinutes ?: st.courseRemindMinutes.takeIf { st.remindCourse }
                            if (minutes != null) {
                                val loc = item.occ.room.ifBlank { null }
                                // 「课前要交」的待办一并写进提醒：上课前最后一次想起来
                                val due =
                                    snap.tasks
                                        .filter { t ->
                                            t.deletedAt == null && t.parentId == null && !t.done && t.dueBeforeNextClass && t.courseId != null &&
                                                (t.courseId == c.id || snap.courses.firstOrNull { x -> x.id == t.courseId }?.name == c.name)
                                        }.map { it.title }
                                val extra = if (due.isEmpty()) "" else " · " + ctx.getString(R.string.remind_course_tasks, due.take(3).joinToString("、"))
                                out +=
                                    Reminder(
                                        item.occ.start.minusMinutes(minutes.toLong()),
                                        c.name,
                                        ctx.getString(R.string.remind_course_text, "%02d:%02d".format(item.occ.start.hour, item.occ.start.minute), loc ?: ctx.getString(R.string.none)) + extra,
                                        "c${c.id}${item.occ.start}",
                                    )
                            }
                        }

                        is EventItem -> {
                            val e = item.event
                            val minutes = e.remindMinutes
                            if (minutes != null && st.remindEvent && !(item.allDay && item.start.toLocalDate() != date)) {
                                val base = if (item.allDay) date.atTime(ALL_DAY_REMIND_AT) else item.start
                                val text = if (item.allDay) ctx.getString(R.string.all_day) else "%02d:%02d".format(item.start.hour, item.start.minute)
                                out += Reminder(base.minusMinutes(minutes.toLong()), e.title, text + (e.location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), "e${e.id}${item.start}")
                            }
                        }

                        is TaskItem -> {
                            val t = item.task
                            val minutes = t.remindMinutes
                            if (minutes != null && st.remindTask && !t.done) {
                                val base = if (item.hasTime) item.due else date.atTime(ALL_DAY_REMIND_AT)
                                out += Reminder(base.minusMinutes(minutes.toLong()), t.title, ctx.getString(R.string.remind_task_text), "t${t.id}${item.due}", t.id)
                            }
                        }

                        is BlockItem -> {
                            val t = item.task
                            val minutes = t.remindMinutes
                            if (minutes != null && st.remindTask && !t.done) {
                                out += Reminder(item.start.minusMinutes(minutes.toLong()), t.title, ctx.getString(R.string.remind_task_text), "b${t.id}${item.start}", t.id)
                            }
                        }
                    }
                }
            }
            return out.filter { it.triggerAt.isAfter(now) }.sortedBy { it.triggerAt }.distinctBy { it.key }
        }
    }
}
