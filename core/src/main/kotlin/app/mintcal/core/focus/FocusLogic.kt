package app.mintcal.core.focus

import app.mintcal.core.model.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 专注统计：按天分组、累计与时段分布。全部纯函数，时区由调用方传入（默认系统时区）。 */
object FocusLogic {
    /** 按开始时刻倒序（最新在前）。 */
    fun sorted(sessions: List<FocusSession>): List<FocusSession> = sessions.sortedByDescending { it.startedAt }

    /** 某任务（null = 自由专注）的会话，按开始时刻倒序。 */
    fun ofTask(
        sessions: List<FocusSession>,
        taskId: String?,
    ): List<FocusSession> = sorted(sessions.filter { it.taskId == taskId })

    /** 累计专注毫秒（各自扣除暂停）。 */
    fun totalMillis(sessions: List<FocusSession>): Long = sessions.sumOf { it.focusMillis }

    /**
     * 按天分组（开始时刻所在日）。返回 LinkedHashMap，键按日期倒序，值天内在倒序。
     * 「做这个任务时在哪些时段专注」的直接答案：一天一行，行内是各段起止。
     */
    fun byDay(
        sessions: List<FocusSession>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<FocusSession>> {
        val out = LinkedHashMap<LocalDate, MutableList<FocusSession>>()
        for (s in sorted(sessions)) {
            val day = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
            out.getOrPut(day) { mutableListOf() }.add(s)
        }
        // sorted() 已按开始时刻倒序，插入序即日期倒序
        return out
    }

    /**
     * 24 小时分布：按开始时刻所在小时累计专注毫秒（下标 0–23）。
     * 「上午还是深夜专注」的直方图数据。
     */
    fun byHour(
        sessions: List<FocusSession>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LongArray {
        val out = LongArray(24)
        for (s in sessions) {
            val h = Instant.ofEpochMilli(s.startedAt).atZone(zone).hour
            out[h] += s.focusMillis
        }
        return out
    }
}
