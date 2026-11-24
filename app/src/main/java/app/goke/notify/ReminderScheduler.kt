package app.goke.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.AgendaItem
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.TaskItem
import app.goke.core.model.CourseNotes
import app.goke.core.model.EventKind
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 静音闹钟挂在 [MuteReceiver]， EXTRA_MUTE_KEY 只是让相邻 PendingIntent 互不覆盖的区分位。 */
private const val EXTRA_MUTE_KEY = "mute_key"
private const val ACTION_PREFIX_MUTE = "mute"

/** 一条待触发的提醒。 */
data class Reminder(
    val triggerAt: LocalDateTime,
    val title: String,
    val text: String,
    val key: String,
    /** 待办提醒才有：通知上显示「完成 / 稍后」按钮、点击直达该待办。 */
    val taskId: String? = null,
    /** 持续提醒：同一条通知的铃声循环，直到完成、稍后或关掉。 */
    val persistent: Boolean = false,
    /** 重要提醒（如考试）：走独立高优通知渠道与最高优先级。 */
    val important: Boolean = false,
    /** 课程提醒直达课程页。 */
    val courseId: String? = null,
)

/**
 * 提醒调度：把未来 7 天内最早的若干条提醒交给 AlarmManager。
 * 数据 / 设置变化时由 [app.goke.GokeApp] 重新调度；开机、改时区时由 [BootReceiver] 触发应用进程重建。
 */
class ReminderScheduler(
    private val ctx: Context,
) {
    private val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 一段自动静音窗口：[silent] 为 true 表示考试期间完全静音（无震动），否则为普通课程仅震动。 */
    data class MuteSlot(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val silent: Boolean,
    )

    /** 一次完整排期：提醒列表 + 静音槽位 + 精确闹钟权限。与上次相同则完全不动 AlarmManager。 */
    private data class Schedule(
        val reminders: List<Reminder>,
        val mutes: List<MuteSlot>,
        val canExact: Boolean,
    )

    @Volatile
    private var lastSchedule: Schedule? = null

    /**
     * 数据 / 设置变化后重新排期。挂起：整个函数跑在 [Dispatchers.Default]，
     * Agenda 展开（每门课 × 每周 occurrences）与约 200+ 次 Binder 调用不再挤主线程。
     * 计划没变时（改了与提醒无关的内容）零 Binder 调用。
     */
    suspend fun reschedule(
        snap: Snapshot,
        st: Settings,
        now: LocalDateTime = LocalDateTime.now(),
    ) = withContext(Dispatchers.Default) {
        val from = now.toLocalDate()
        // Agenda 展开只做一次：提醒与静音槽共用（旧实现 compute 和 scheduleMutes 各展开一遍 8 天）
        val days = Agenda.range(snap, from, from.plusDays(8), st.defaultBlockMinutes, includeDoneTasks = false)
        val zone = ZoneId.systemDefault()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val all = compute(ctx, days, snap, st, now)
        val list =
            if (all.size <= MAX) {
                all
            } else {
                val importantOnes = all.filter { it.important }
                val remainingSlots = (MAX - importantOnes.size).coerceAtLeast(0)
                (importantOnes.take(MAX) + all.filterNot { it.important }.take(remainingSlots)).sortedBy { it.triggerAt }
            }
        val mutes = computeMuteSlots(days, st, now)
        val schedule = Schedule(list, mutes, canExact)
        if (schedule == lastSchedule) return@withContext
        val hadMutes = !lastSchedule?.mutes.isNullOrEmpty()
        lastSchedule = schedule
        cancelAll()
        stopLingering(snap)
        list.forEachIndexed { i, r ->
            val notifId = if (r.persistent && r.taskId != null) persistentNotifId(r.taskId, r.key.startsWith("b")) else r.key.hashCode()
            val intent =
                Intent(ctx, ReminderReceiver::class.java)
                    .putExtra(ReminderReceiver.EXTRA_TITLE, r.title)
                    .putExtra(ReminderReceiver.EXTRA_TEXT, r.text)
                    .putExtra(ReminderReceiver.EXTRA_ID, notifId)
                    .putExtra(ReminderReceiver.EXTRA_TASK_ID, r.taskId)
                    .putExtra(ReminderReceiver.EXTRA_COURSE_ID, r.courseId)
                    .putExtra(ReminderReceiver.EXTRA_PERSISTENT, r.persistent)
                    .putExtra(ReminderReceiver.EXTRA_IMPORTANT, r.important)
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
        // 旧静音槽只有在上次排过（或这次要排）时才需要取消；autoMute 关闭时走这条路径清干净
        if (hadMutes || mutes.isNotEmpty()) cancelMutes()
        mutes.forEachIndexed { i, slot ->
            val mode = if (slot.silent) MuteReceiver.MODE_SILENT else MuteReceiver.MODE_VIBRATE
            val startPi = mutePi(ACTION_PREFIX_MUTE + i, MuteReceiver.ACTION_MUTE, MUTE_BASE + 2 * i, mode)
            val endPi = mutePi(ACTION_PREFIX_MUTE + i, MuteReceiver.ACTION_UNMUTE, MUTE_BASE + 2 * i + 1)
            val hasAdjacentNext = mutes.getOrNull(i + 1)?.start == slot.end
            runCatching {
                if (slot.start.isAfter(now)) setAlarm(slot.start, zone, canExact, startPi)
                if (!hasAdjacentNext && slot.end.isAfter(now)) setAlarm(slot.end, zone, canExact, endPi)
            }
        }
    }

    private fun muteIntent(action: String): Intent = Intent(ctx, MuteReceiver::class.java).setAction(action)

    private fun mutePi(
        key: String,
        action: String,
        requestCode: Int,
        mode: String? = null,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            requestCode,
            muteIntent(action).putExtra(EXTRA_MUTE_KEY, key).apply {
                if (mode != null) putExtra(MuteReceiver.EXTRA_MODE, mode)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun setAlarm(
        at: LocalDateTime,
        zone: ZoneId,
        canExact: Boolean,
        pi: PendingIntent,
    ) {
        val ms = at.atZone(zone).toInstant().toEpochMilli()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    /** 待办已完成、删掉或关掉持续提醒时，停掉还在循环的铃声。 */
    private fun stopLingering(snap: Snapshot) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        for (t in snap.tasks) {
            if (t.done || t.deletedAt != null || !t.remindPersistent || t.effectiveRemindMinutes.isEmpty()) {
                nm.cancel(persistentNotifId(t.id, false))
                nm.cancel(persistentNotifId(t.id, true))
            }
        }
    }

    private fun cancelMutes() {
        for (i in 0 until MAX_MUTE_PAIRS) {
            cancelMuteSlot(MUTE_BASE + 2 * i, MuteReceiver.ACTION_MUTE)
            cancelMuteSlot(MUTE_BASE + 2 * i + 1, MuteReceiver.ACTION_UNMUTE)
        }
    }

    private fun cancelMuteSlot(
        requestCode: Int,
        action: String,
    ) {
        val pi =
            PendingIntent.getBroadcast(
                ctx,
                requestCode,
                muteIntent(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
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

        /** 静音闹钟对的请求码基址：提醒占用 0 until MAX，这里错开。 */
        const val MUTE_BASE = 100
        const val MAX_MUTE_PAIRS = 80

        /** 自检测试提醒的请求码，须落在 0 until MAX 之外，免得被重新调度时取消。 */
        const val TEST_ID = 9001
        private const val EXAM_PERSISTENT_MAX_MINUTES = 120
        private val ALL_DAY_REMIND_AT = LocalTime.of(9, 0)

        /** 持续提醒的通知号。完成或关掉之后靠这个号停掉铃声，不跟触发时间走。 */
        fun persistentNotifId(
            taskId: String,
            block: Boolean,
        ): Int = ("nag:$taskId:${if (block) 1 else 0}").hashCode()

        /** 纯计算：未来 7 天内、晚于 now 的提醒，按触发时间排序。 */
        fun compute(
            ctx: Context,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime,
        ): List<Reminder> {
            val from = now.toLocalDate()
            return compute(ctx, Agenda.range(snap, from, from.plusDays(8), st.defaultBlockMinutes, includeDoneTasks = false), snap, st, now)
        }

        /** 同上，但接受已展开的 8 天日程（调用方要与静音槽共用同一次展开时用这个）。 */
        fun compute(
            ctx: Context,
            days: Map<LocalDate, List<AgendaItem>>,
            snap: Snapshot,
            st: Settings,
            now: LocalDateTime,
        ): List<Reminder> {
            val out = mutableListOf<Reminder>()
            for ((date, items) in days) {
                if (st.dailyDigest) {
                    val courses = items.count { it is CourseItem }
                    val tasks =
                        items
                            .mapNotNull {
                                when {
                                    it is TaskItem && !it.task.done -> it.task.id
                                    it is BlockItem && !it.task.done -> it.task.id
                                    else -> null
                                }
                            }.toSet()
                            .size
                    if (courses + tasks > 0) {
                        out += Reminder(date.atTime(LocalTime.of(st.digestHour, st.digestMinute)), ctx.getString(R.string.digest_title), ctx.getString(R.string.today_summary, courses, tasks), "digest$date")
                    }
                }
                for (item in items) {
                    when (item) {
                        is CourseItem -> {
                            val c = item.occ.course
                            val isExam = c.isExam
                            val enabled = if (isExam) st.remindExam else st.remindCourse
                            val minutesList =
                                if (!enabled) {
                                    emptyList()
                                } else if (isExam) {
                                    c.effectiveRemindMinutes.ifEmpty { st.effectiveExamRemindMinutes }
                                } else {
                                    c.effectiveRemindMinutes.ifEmpty { st.effectiveCourseRemindMinutes }
                                }
                            if (minutesList.isNotEmpty()) {
                                val loc =
                                    item.occ.room
                                        .trim()
                                        .takeIf { it.isNotEmpty() }
                                val teacher = c.teacher.trim().takeIf { st.showTeacher && it.isNotEmpty() }
                                val weekDetail =
                                    CourseNotes
                                        .summaryForWeek(c.note, item.occ.originalWeek)
                                        .replace("\n", " · ")
                                        .trim()
                                        .takeIf { it.isNotEmpty() }
                                // 「课前要交」的待办一并写进提醒：上课前最后一次想起来
                                val due =
                                    snap.tasks
                                        .filter { t ->
                                            t.deletedAt == null && t.parentId == null && !t.done && t.dueBeforeNextClass && t.courseId != null &&
                                                (t.courseId == c.id || snap.courses.firstOrNull { x -> x.id == t.courseId }?.name == c.name)
                                        }.map { it.title }
                                val dueText = if (due.isEmpty()) null else ctx.getString(R.string.remind_course_tasks, due.take(3).joinToString("、"))
                                val timeText = "%02d:%02d".format(item.occ.start.hour, item.occ.start.minute)
                                val detail = listOfNotNull(loc, teacher, weekDetail, dueText).joinToString(" · ")
                                val text = if (detail.isNotEmpty()) ctx.getString(R.string.remind_course_text, timeText, detail) else timeText
                                val title =
                                    if (isExam && !c.name.contains("考")) {
                                        ctx.getString(R.string.remind_exam_title, c.name)
                                    } else {
                                        c.name
                                    }
                                for (minutes in minutesList) {
                                    val persistent =
                                        if (isExam) {
                                            c.remindPersistent || (st.examRemindPersistent && minutes <= EXAM_PERSISTENT_MAX_MINUTES)
                                        } else {
                                            c.remindPersistent
                                        }
                                    out +=
                                        Reminder(
                                            triggerAt = item.occ.start.minusMinutes(minutes.toLong()),
                                            title = title,
                                            text = text,
                                            key = "c${c.id}${item.occ.start}:$minutes",
                                            persistent = persistent,
                                            important = isExam,
                                            courseId = c.id,
                                        )
                                }
                            }
                        }

                        is EventItem -> {
                            val e = item.event
                            val isExam = e.kind == EventKind.EXAM
                            val enabled = (isExam && st.remindExam) || st.remindEvent
                            val minutesList = if (isExam) e.effectiveRemindMinutes.ifEmpty { st.effectiveExamRemindMinutes } else e.effectiveRemindMinutes
                            if (minutesList.isNotEmpty() && enabled && !(item.allDay && item.start.toLocalDate() != date)) {
                                val base = if (item.allDay) date.atTime(ALL_DAY_REMIND_AT) else item.start
                                val text = if (item.allDay) ctx.getString(R.string.all_day) else "%02d:%02d".format(item.start.hour, item.start.minute)
                                val fullText = text + (e.location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                                val title = if (isExam && !e.title.contains("考")) ctx.getString(R.string.remind_exam_title, e.title) else e.title
                                for (minutes in minutesList) {
                                    val persistent = isExam && st.examRemindPersistent && minutes <= EXAM_PERSISTENT_MAX_MINUTES
                                    out += Reminder(base.minusMinutes(minutes.toLong()), title, fullText, "e${e.id}${item.start}:$minutes", persistent = persistent, important = isExam)
                                }
                            }
                        }

                        is TaskItem -> {
                            val t = item.task
                            val isExam = t.isExam
                            val minutesList = if (isExam) t.effectiveRemindMinutes.ifEmpty { st.effectiveExamRemindMinutes } else t.effectiveRemindMinutes
                            val enabled = (isExam && st.remindExam) || st.remindTask || (t.source == Source.PARCEL && st.parcelEnabled)
                            if (minutesList.isNotEmpty() && enabled && !t.done) {
                                val base = if (item.hasTime) item.due else date.atTime(ALL_DAY_REMIND_AT)
                                val text = if (isExam) examTaskText(ctx, t, if (item.hasTime) item.due else null) else ctx.getString(R.string.remind_task_text)
                                for (minutes in minutesList) {
                                    val persistent = t.remindPersistent || (isExam && st.examRemindPersistent && minutes <= EXAM_PERSISTENT_MAX_MINUTES)
                                    out += Reminder(base.minusMinutes(minutes.toLong()), t.title, text, "t${t.id}${item.due}:$minutes", t.id, persistent, important = isExam)
                                }
                            }
                        }

                        is BlockItem -> {
                            val t = item.task
                            val isExam = t.isExam
                            val minutesList = if (isExam) t.effectiveRemindMinutes.ifEmpty { st.effectiveExamRemindMinutes } else t.effectiveRemindMinutes
                            val enabled = (isExam && st.remindExam) || st.remindTask || (t.source == Source.PARCEL && st.parcelEnabled)
                            if (minutesList.isNotEmpty() && enabled && !t.done) {
                                val text = if (isExam) examTaskText(ctx, t, item.start) else ctx.getString(R.string.remind_task_text)
                                for (minutes in minutesList) {
                                    val persistent = t.remindPersistent || (isExam && st.examRemindPersistent && minutes <= EXAM_PERSISTENT_MAX_MINUTES)
                                    out += Reminder(item.start.minusMinutes(minutes.toLong()), t.title, text, "b${t.id}${item.start}:$minutes", t.id, persistent, important = isExam)
                                }
                            }
                        }
                    }
                }
            }
            return out.filter { it.triggerAt.isAfter(now) }.sortedBy { it.triggerAt }.distinctBy { it.key }
        }

        private fun examTaskText(
            ctx: Context,
            t: Task,
            at: LocalDateTime?,
        ): String {
            val timePart = at?.let { "%02d:%02d".format(it.hour, it.minute) }
            val loc = t.extra["location"]?.trim()?.takeIf { it.isNotEmpty() }
            val notePart = t.note.trim().takeIf { it.isNotEmpty() }
            val detail = listOfNotNull(timePart, loc, notePart).distinct().joinToString(" · ")
            return detail.ifEmpty { ctx.getString(R.string.remind_task_text) }
        }

        /**
         * 上课 / 考试自动静音槽位计算：
         * - 普通课程：[MuteSlot.silent] = false（保持震动）。
         * - 考试课程、考试时间块与考试日程：[MuteSlot.silent] = true（完全静音，无震动）。
         * 重叠时段按扫描线合并，同时间段内完全静音优先于震动；最多保留 [MAX_MUTE_PAIRS] 段。
         */
        internal fun computeMuteSlots(
            days: Map<LocalDate, List<AgendaItem>>,
            st: Settings,
            now: LocalDateTime,
        ): List<MuteSlot> {
            if (!st.autoMute) return emptyList()
            val raw = ArrayList<MuteSlot>()
            for ((_, items) in days) {
                for (item in items) {
                    val slot =
                        when (item) {
                            is CourseItem -> {
                                val o = item.occ
                                if (o.end.isAfter(now)) MuteSlot(o.start, o.end, silent = o.course.isExam) else null
                            }

                            is BlockItem -> {
                                if (!item.task.done && item.task.isExam && item.end.isAfter(item.start) && item.end.isAfter(now)) {
                                    MuteSlot(item.start, item.end, silent = true)
                                } else {
                                    null
                                }
                            }

                            is EventItem -> {
                                if (item.event.kind == EventKind.EXAM && !item.allDay && item.end.isAfter(item.start) && item.end.isAfter(now)) {
                                    MuteSlot(item.start, item.end, silent = true)
                                } else {
                                    null
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    if (slot != null) raw += slot
                }
            }
            if (raw.isEmpty()) return emptyList()
            val bounds = raw.flatMap { listOf(it.start, it.end) }.distinct().sorted()
            val merged = ArrayList<MuteSlot>()
            for (i in 0 until bounds.lastIndex) {
                val a = bounds[i]
                val b = bounds[i + 1]
                if (!b.isAfter(a)) continue
                var covered = false
                var silent = false
                for (s in raw) {
                    if (!s.start.isAfter(a) && !s.end.isBefore(b)) {
                        covered = true
                        if (s.silent) {
                            silent = true
                            break
                        }
                    }
                }
                if (!covered) continue
                val prev = merged.lastOrNull()
                if (prev != null && prev.end == a && prev.silent == silent) {
                    merged[merged.lastIndex] = prev.copy(end = b)
                } else {
                    merged += MuteSlot(a, b, silent)
                }
            }
            return merged.take(MAX_MUTE_PAIRS)
        }
    }
}
