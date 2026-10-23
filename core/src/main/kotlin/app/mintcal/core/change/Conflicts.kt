package app.mintcal.core.change

import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.Snapshot
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.recurrence.EventExpander
import app.mintcal.core.time.SemesterCalendar
import java.time.LocalDateTime

data class Conflict(
    val aId: String,
    val aTitle: String,
    val bId: String,
    val bTitle: String,
    val start: LocalDateTime,
)

/** 冲突检测：同时考虑课程、日程（不含全天）。只报告涉及 [focusIds] 的冲突。 */
object Conflicts {
    private data class Slot(
        val ownerId: String,
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
    )

    fun detect(
        snapshot: Snapshot,
        focusIds: Set<String>,
        limit: Int = 30,
    ): List<Conflict> {
        val live = snapshot.live()
        val semester = live.currentSemester ?: return emptyList()
        val cal = SemesterCalendar(semester)
        val from = cal.weekStart(1)
        val to = cal.weekEnd(cal.totalWeeks)
        val slots = mutableListOf<Slot>()
        for (o in CourseExpander.occurrences(semester, live.courses, live.exceptions, from, to)) {
            slots += Slot(o.course.id, o.course.name, o.start, o.end)
        }
        for (e in live.events.filter { !it.allDay }) {
            for (o in EventExpander.occurrences(e, from, to)) slots += Slot(e.id, e.title, o.start, o.end)
        }
        val focus = slots.filter { it.ownerId in focusIds }
        val result = mutableListOf<Conflict>()
        val seen = mutableSetOf<String>()
        for (a in focus) {
            for (b in slots) {
                if (a.ownerId == b.ownerId) continue
                if (a.start < b.end && b.start < a.end) {
                    val key = listOf(a.ownerId, b.ownerId).sorted().joinToString("|") + a.start.toLocalDate()
                    if (seen.add(key)) {
                        result += Conflict(a.ownerId, a.title, b.ownerId, b.title, maxOf(a.start, b.start))
                        if (result.size >= limit) return result
                    }
                }
            }
        }
        return result
    }

    fun focusIds(cs: ChangeSet): Set<String> =
        cs.changes
            .filter { it !is Change.Remove }
            .map { it.record }
            .filter { it is Course || it is Event }
            .map { it.id }
            .toSet()
}
