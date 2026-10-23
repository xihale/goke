package app.mintcal.core.agenda

import app.mintcal.core.model.Event
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.recurrence.CourseOccurrence
import app.mintcal.core.recurrence.EventExpander
import app.mintcal.core.todo.TaskLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 某一天里要展示的条目：课程、日程、待办（截止 / 排入的时间块）。 */
sealed interface AgendaItem {
    /** 排序时刻；全天事项排最前，无具体时间的待办排最后。 */
    val sortKey: LocalDateTime
    val rank: Int
}

data class CourseItem(val occ: CourseOccurrence) : AgendaItem {
    override val sortKey get() = occ.start
    override val rank get() = 1
}

data class EventItem(val event: Event, val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean) : AgendaItem {
    override val sortKey get() = if (allDay) start.toLocalDate().atStartOfDay() else start
    override val rank get() = if (allDay) 0 else 1
}

/** 当天截止的待办（若带具体时间则按时间排，否则排到末尾）。 */
data class TaskItem(val task: Task, val due: LocalDateTime, val hasTime: Boolean) : AgendaItem {
    override val sortKey get() = if (hasTime) due else due.toLocalDate().atTime(LocalTime.MAX)
    override val rank get() = if (hasTime) 1 else 2
}

/** 排入日历的待办时间块（虚线块）。 */
data class BlockItem(val task: Task, val start: LocalDateTime, val end: LocalDateTime) : AgendaItem {
    override val sortKey get() = start
    override val rank get() = 1
}

data class DayMarks(val courses: Int, val events: Int, val tasks: Int) {
    val total get() = courses + events + tasks
}

object Agenda {
    /** 展开 [from, to] 范围每天的条目。传入的快照应已过滤删除项（内部也会过滤）。 */
    fun range(
        snapshot: Snapshot, from: LocalDate, to: LocalDate, defaultBlockMinutes: Int = 60,
        includeDoneTasks: Boolean = true,
    ): Map<LocalDate, List<AgendaItem>> {
        val s = snapshot.live()
        val out = HashMap<LocalDate, MutableList<AgendaItem>>()
        fun add(d: LocalDate, item: AgendaItem) {
            if (d.isBefore(from) || d.isAfter(to)) return
            out.getOrPut(d) { mutableListOf() }.add(item)
        }
        val sem = s.currentSemester
        if (sem != null) {
            for (o in CourseExpander.occurrences(sem, s.courses, s.exceptions, from, to)) add(o.date, CourseItem(o))
        }
        for (o in EventExpander.occurrences(s.events, from, to)) {
            val all = o.event.allDay
            var d = o.start.toLocalDate()
            val last = if (all) o.end.toLocalDate() else if (o.end.toLocalTime() == LocalTime.MIDNIGHT && o.end.isAfter(o.start)) o.end.toLocalDate().minusDays(1) else o.end.toLocalDate()
            while (!d.isAfter(last) && !d.isAfter(to)) {
                add(d, EventItem(o.event, o.start, o.end, all))
                d = d.plusDays(1)
            }
        }
        for (t in TaskLogic.topLevel(s.tasks)) {
            if (t.done && !includeDoneTasks) continue
            val due = TaskLogic.effectiveDue(t, sem, s.courses, s.exceptions, from.atStartOfDay())
            if (t.blockStart != null) {
                val end = TaskLogic.blockEnd(t, defaultBlockMinutes)!!
                add(t.blockStart.toLocalDate(), BlockItem(t, t.blockStart, end))
            } else if (due != null) {
                add(due.toLocalDate(), TaskItem(t, due, t.dueHasTime || t.dueBeforeNextClass))
            }
        }
        return out.mapValues { (_, v) -> v.sortedWith(compareBy({ it.rank.takeIf { r -> r == 0 } ?: 1 }, { it.sortKey }, { it.rank })) }
    }

    fun forDay(snapshot: Snapshot, date: LocalDate, defaultBlockMinutes: Int = 60): List<AgendaItem> =
        range(snapshot, date, date, defaultBlockMinutes)[date].orEmpty()

    fun marks(snapshot: Snapshot, from: LocalDate, to: LocalDate): Map<LocalDate, DayMarks> =
        range(snapshot, from, to).mapValues { (_, items) ->
            DayMarks(
                items.count { it is CourseItem },
                items.count { it is EventItem },
                items.count { (it is TaskItem && !it.task.done) || (it is BlockItem && !it.task.done) },
            )
        }
}
