package app.mintcal.core.agenda

import app.mintcal.core.model.Task
import app.mintcal.core.todo.TaskLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 一天里的空档。 */
data class Gap(
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val minutes: Int get() = java.time.Duration.between(start, end).toMinutes().toInt()
}

/** 建议把 [task] 排进 [gap] 开头的 [minutes] 分钟。 */
data class GapSuggestion(
    val gap: Gap,
    val task: Task,
    val minutes: Int,
)

object FreeGaps {
    /** 当天占用时段（课程、有具体时间的日程、已排的时间块）。 */
    private fun busy(
        items: List<AgendaItem>,
        defaultMinutes: Int,
    ): List<Pair<LocalDateTime, LocalDateTime>> =
        items.mapNotNull {
            when (it) {
                is CourseItem -> it.occ.start to it.occ.end
                is EventItem -> if (it.allDay) null else it.start to it.end
                is BlockItem -> it.start to it.end
                is TaskItem -> if (it.hasTime) it.due to it.due.plusMinutes(defaultMinutes.toLong()) else null
            }
        }

    /** [day] 里不被占用、晚于 [now]、不短于 [minMinutes] 的空档（只看 [dayStart]–[dayEnd]）。 */
    fun gaps(
        items: List<AgendaItem>,
        day: LocalDate,
        now: LocalDateTime,
        defaultMinutes: Int = 30,
        minMinutes: Int = 30,
        dayStart: LocalTime = LocalTime.of(8, 0),
        dayEnd: LocalTime = LocalTime.of(22, 0),
    ): List<Gap> {
        var cursor = maxOf(day.atTime(dayStart), now)
        val end = day.atTime(dayEnd)
        val out = mutableListOf<Gap>()
        for ((s, e) in busy(items, defaultMinutes).sortedBy { it.first }) {
            if (s.isAfter(cursor)) {
                val g = Gap(cursor, minOf(s, end))
                if (g.start.isBefore(g.end) && g.minutes >= minMinutes) out += g
            }
            if (e.isAfter(cursor)) cursor = e
            if (!cursor.isBefore(end)) break
        }
        if (cursor.isBefore(end)) {
            val g = Gap(cursor, end)
            if (g.minutes >= minMinutes) out += g
        }
        return out
    }

    /**
     * 为今天最近的一个空档挑一条有估时、还没排进日历的待办：优先更紧的截止、更高的优先级，
     * 且估时不超过空档长度。没有合适的返回 null（没估时的不推荐，避免噪音）。
     */
    fun suggest(
        gaps: List<Gap>,
        tasks: List<Task>,
        now: LocalDateTime,
    ): GapSuggestion? {
        val candidates =
            TaskLogic
                .topLevel(tasks)
                .filter { !it.done && it.blockStart == null && (it.estimateMinutes ?: 0) > 0 }
                .sortedWith(compareBy<Task>({ it.due == null }, { it.due }, { -it.priority }))
        for (g in gaps) {
            val t = candidates.firstOrNull { it.estimateMinutes!! <= g.minutes } ?: continue
            return GapSuggestion(g, t, t.estimateMinutes!!)
        }
        return null
    }
}
