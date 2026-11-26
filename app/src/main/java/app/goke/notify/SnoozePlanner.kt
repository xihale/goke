package app.goke.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.todo.TaskLogic
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 稍后提醒的分段策略：按距离目标开始节点的时间远近分类。 */
enum class SnoozeScaleMode {
    /** 已到/已过目标时间，或无固定时间：给常用基准延后档。 */
    STANDARD,

    /** 距离目标较近（≤ 2 小时）：等步长线性分段，收尾于目标开始节点。 */
    LINEAR,

    /** 距离目标较远（> 2 小时）：倍增指数分段，兼顾短时延后与长周期节点。 */
    EXPONENTIAL,
}

/** 一条候选稍后提醒选项。 */
data class SnoozeOption(
    val triggerAt: LocalDateTime,
    val delayMinutes: Int,
    val isAtTarget: Boolean = false,
    val isTomorrowMorning: Boolean = false,
)

/** 稍后提醒计算结果（含分类模式、目标开始节点、剩余分钟与候选列表）。 */
data class SnoozePlan(
    val mode: SnoozeScaleMode,
    val targetAt: LocalDateTime?,
    val remainingMinutes: Int?,
    val options: List<SnoozeOption>,
)

/** 快捷稍后提醒规划与闹钟调度。 */
object SnoozePlanner {
    const val CLOSE_THRESHOLD_MINUTES = 120
    const val MAX_CUSTOM_MINUTES = 10080
    const val DEFAULT_ACTION_SNOOZE_MS = 60 * 60 * 1000L
    private val ALL_DAY_REMIND_AT = LocalTime.of(9, 0)
    private val STANDARD_DELAYS = listOf(10, 30, 60, 180)
    private val SHORT_FALLBACK_DELAYS = listOf(5, 10, 30)
    private val EXPONENTIAL_LADDER = listOf(15, 30, 60, 120, 240, 480, 720, 1440, 2880, 5760, 10080, 20160)

    /** 解析待办的目标开始节点：优先使用显式透传时刻，其次日程块起点，再次生效截止时刻（全天默认 09:00）。 */
    fun resolveTargetAt(
        task: Task,
        snap: Snapshot,
        now: LocalDateTime,
        explicitTargetAtMs: Long? = null,
    ): LocalDateTime? {
        if (explicitTargetAtMs != null && explicitTargetAtMs > 0L) {
            return Instant
                .ofEpochMilli(explicitTargetAtMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        }
        task.blockStart?.let { return it }
        val effDue = TaskLogic.effectiveDue(task, snap.currentSemester, snap.courses, snap.exceptions, now) ?: return null
        return if (task.dueHasTime || task.dueBeforeNextClass) {
            effDue
        } else {
            effDue.toLocalDate().atTime(ALL_DAY_REMIND_AT)
        }
    }

    /** 根据当前时刻与目标开始节点的距离，生成线性 / 指数 / 标准稍后提醒候选列表。 */
    fun plan(
        now: LocalDateTime,
        targetAt: LocalDateTime?,
    ): SnoozePlan {
        val remainingSeconds = targetAt?.let { ChronoUnit.SECONDS.between(now, it) }
        if (targetAt == null || remainingSeconds == null || remainingSeconds <= 0L) {
            val options =
                buildList {
                    STANDARD_DELAYS.forEach { m ->
                        add(SnoozeOption(triggerAt = now.plusMinutes(m.toLong()), delayMinutes = m))
                    }
                    val tomorrowNineAm = now.toLocalDate().plusDays(1).atTime(ALL_DAY_REMIND_AT)
                    val toMorning = ChronoUnit.MINUTES.between(now, tomorrowNineAm).toInt()
                    if (toMorning >= 240) {
                        add(SnoozeOption(triggerAt = tomorrowNineAm, delayMinutes = toMorning, isTomorrowMorning = true))
                    }
                }
            return SnoozePlan(
                mode = SnoozeScaleMode.STANDARD,
                targetAt = targetAt,
                remainingMinutes = null,
                options = options,
            )
        }

        val remainingMin = ((remainingSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
        if (remainingMin == 1) {
            val options =
                buildList {
                    add(SnoozeOption(triggerAt = targetAt, delayMinutes = 1, isAtTarget = true))
                    SHORT_FALLBACK_DELAYS.forEach { m ->
                        add(SnoozeOption(triggerAt = now.plusMinutes(m.toLong()), delayMinutes = m))
                    }
                }
            return SnoozePlan(
                mode = SnoozeScaleMode.LINEAR,
                targetAt = targetAt,
                remainingMinutes = 1,
                options = options,
            )
        }

        if (remainingMin <= CLOSE_THRESHOLD_MINUTES) {
            val step =
                when {
                    remainingMin <= 5 -> 1
                    remainingMin <= 10 -> 2
                    remainingMin <= 15 -> 3
                    remainingMin <= 30 -> 5
                    remainingMin <= 50 -> 10
                    remainingMin <= 90 -> 15
                    else -> 20
                }
            val guard = maxOf(1, (step + 1) / 2)
            val options =
                buildList {
                    var m = step
                    while (m <= remainingMin - guard) {
                        add(SnoozeOption(triggerAt = now.plusMinutes(m.toLong()), delayMinutes = m))
                        m += step
                    }
                    add(SnoozeOption(triggerAt = targetAt, delayMinutes = remainingMin, isAtTarget = true))
                }
            return SnoozePlan(
                mode = SnoozeScaleMode.LINEAR,
                targetAt = targetAt,
                remainingMinutes = remainingMin,
                options = options,
            )
        }

        val minStep =
            when {
                remainingMin <= 480 -> 15
                remainingMin <= 1440 -> 30
                remainingMin <= 4320 -> 60
                else -> 120
            }
        val maxCap = (remainingMin * 0.85).toInt()
        var picks = EXPONENTIAL_LADDER.filter { it in minStep..maxCap }
        if (picks.size > 6 && 720 in picks && 1440 in picks) {
            picks = picks.filterNot { it == 720 }
        }
        if (picks.size > 6) {
            picks = picks.takeLast(6)
        }
        if (picks.isEmpty()) {
            picks = listOf(15, 30, 60).filter { it < remainingMin }.ifEmpty { listOf(15) }
        }
        return SnoozePlan(
            mode = SnoozeScaleMode.EXPONENTIAL,
            targetAt = targetAt,
            remainingMinutes = remainingMin,
            options = picks.map { m -> SnoozeOption(triggerAt = now.plusMinutes(m.toLong()), delayMinutes = m) },
        )
    }

    /** 安排一次性稍后提醒闹钟，并立即停止当前响铃/通知。 */
    fun scheduleSnooze(
        ctx: Context,
        taskId: String,
        notifId: Int,
        title: String,
        text: String,
        persistent: Boolean,
        important: Boolean,
        triggerAt: LocalDateTime,
        targetAt: LocalDateTime? = null,
    ) {
        val zone = ZoneId.systemDefault()
        val atMs = triggerAt.atZone(zone).toInstant().toEpochMilli()
        val targetMs = targetAt?.atZone(zone)?.toInstant()?.toEpochMilli()
        scheduleSnoozeAtEpoch(
            ctx = ctx,
            taskId = taskId,
            notifId = notifId,
            title = title,
            text = text,
            persistent = persistent,
            important = important,
            triggerAtMs = atMs,
            targetAtMs = targetMs,
        )
    }

    internal fun scheduleSnoozeAtEpoch(
        ctx: Context,
        taskId: String,
        notifId: Int,
        title: String,
        text: String,
        persistent: Boolean,
        important: Boolean,
        triggerAtMs: Long,
        targetAtMs: Long? = null,
    ) {
        val effectiveId = if (notifId != 0) notifId else ("snooze:$taskId").hashCode()
        val again =
            Intent(ctx, ReminderReceiver::class.java)
                .setData(Uri.parse("goke://snooze/$effectiveId"))
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                .putExtra(ReminderReceiver.EXTRA_ID, effectiveId)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, persistent)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, important)
                .apply {
                    if (targetAtMs != null && targetAtMs > 0L) {
                        putExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, targetAtMs)
                    }
                }
        val pi = PendingIntent.getBroadcast(ctx, taskId.hashCode(), again, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        }
        ReminderAlertService.stop(ctx, notifId)
        if (notifId != 0) {
            runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(notifId) }
        }
    }
}
