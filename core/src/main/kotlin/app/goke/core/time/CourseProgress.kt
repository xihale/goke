package app.goke.core.time

import java.time.Duration
import java.time.LocalDateTime

/** 课程或定时日程相对于当前时刻的时间阶段。 */
enum class TimePhase {
    UPCOMING,
    ONGOING,
    FINISHED,
}

/** 课程与定时日程的时间进度纯计算，供主应用与桌面小组件共用。 */
object CourseProgress {
    /**
     * 计算 [now] 在 `[start, end)` 区间内的进度比例 `[0f, 1f]`：
     * - 未开始（`now <= start`）返回 `0f`
     * - 已结束（`now >= end`）返回 `1f`
     * - 进行中按已过毫秒与总时长毫秒之比线性插值
     */
    fun fraction(
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ): Float {
        if (!end.isAfter(start)) return if (!now.isBefore(end)) 1f else 0f
        if (!now.isAfter(start)) return 0f
        if (!now.isBefore(end)) return 1f
        val totalMs = Duration.between(start, end).toMillis()
        if (totalMs <= 0L) return 1f
        val elapsedMs = Duration.between(start, now).toMillis()
        return (elapsedMs.toDouble() / totalMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** 判定 [now] 处于未开始、进行中还是已结束（与 `!start.isAfter(now) && end.isAfter(now)` 口径一致）。 */
    fun phase(
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ): TimePhase =
        when {
            !end.isAfter(start) -> if (!now.isBefore(end)) TimePhase.FINISHED else TimePhase.UPCOMING
            now.isBefore(start) -> TimePhase.UPCOMING
            !now.isBefore(end) -> TimePhase.FINISHED
            else -> TimePhase.ONGOING
        }

    /** 百分比整数 `0..100`（供进度条与无障碍状态播报使用）。 */
    fun percent(
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ): Int = (fraction(start, end, now) * 100f).toInt().coerceIn(0, 100)
}
