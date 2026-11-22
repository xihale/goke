package app.goke.core.agenda

import app.goke.core.model.Snapshot
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.time.SemesterCalendar

enum class CellKind { NORMAL, ADDED, MODIFIED, REMOVED }

data class TimetableCell(
    val occ: CourseOccurrence,
    val kind: CellKind,
)

/** 课表一周的单元格；传入 Dry Run 之后的快照时，标出新增 / 修改 / 删除（用于「在课表上预览」）。 */
object TimetableDiff {
    private fun occurrences(
        s: Snapshot,
        week: Int,
    ): List<CourseOccurrence> {
        val live = s.live()
        val sem = live.currentSemester ?: return emptyList()
        return CourseExpander.occurrencesInWeek(sem, live.courses, live.exceptions, week)
    }

    private fun sig(o: CourseOccurrence) = "${o.course.id}|${o.date}|${o.startPeriod}|${o.endPeriod}|${o.room}|${o.course.name}"

    fun week(
        before: Snapshot,
        after: Snapshot?,
        week: Int,
    ): List<TimetableCell> {
        val b = occurrences(before, week)
        if (after == null) return b.map { TimetableCell(it, CellKind.NORMAL) }
        val a = occurrences(after, week)
        val aSigs = a.map(::sig).toSet()
        val bSigs = b.map(::sig).toSet()
        val oldIds =
            before
                .live()
                .courses
                .map { it.id }
                .toSet()
        return b.map { TimetableCell(it, if (sig(it) in aSigs) CellKind.NORMAL else CellKind.REMOVED) } +
            a.filter { sig(it) !in bSigs }.map { TimetableCell(it, if (it.course.id in oldIds) CellKind.MODIFIED else CellKind.ADDED) }
    }

    fun weekOfDate(
        s: Snapshot,
        date: java.time.LocalDate,
    ): Int? = s.live().currentSemester?.let { SemesterCalendar(it).weekOf(date) }
}
