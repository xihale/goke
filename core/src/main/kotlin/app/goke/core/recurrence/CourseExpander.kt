package app.goke.core.recurrence

import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.ExceptionType
import app.goke.core.model.Semester
import app.goke.core.time.SemesterCalendar
import java.time.LocalDate
import java.time.LocalDateTime

/** 课程在某一周某一天的一次具体出现。 */
data class CourseOccurrence(
    val course: Course,
    val week: Int,
    val date: LocalDate,
    val startPeriod: Int,
    val endPeriod: Int,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val room: String,
    val rescheduled: Boolean,
    val originalWeek: Int = week,
)

object CourseExpander {
    /** 展开 [from, to]（含）内所有课程出现，按开始时间排序。课程按「规则」存储，查询时按周展开。 */
    fun occurrences(
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        from: LocalDate,
        to: LocalDate,
    ): List<CourseOccurrence> {
        val cal = SemesterCalendar(semester)
        val exByKey = exceptions.filter { it.deletedAt == null }.groupBy { it.courseId to it.week }
        val out = mutableListOf<CourseOccurrence>()
        for (c in courses) {
            if (c.deletedAt != null || c.semesterId != semester.id) continue
            for (w in c.weeks.sorted()) {
                val ex = exByKey[c.id to w]?.lastOrNull()
                if (ex?.type == ExceptionType.CANCEL) continue
                val week = if (ex?.type == ExceptionType.RESCHEDULE) ex.newWeek ?: w else w
                val dow = if (ex?.type == ExceptionType.RESCHEDULE) ex.newDayOfWeek ?: c.dayOfWeek else c.dayOfWeek
                val sp = if (ex?.type == ExceptionType.RESCHEDULE) ex.newStartPeriod ?: c.startPeriod else c.startPeriod
                val ep = if (ex?.type == ExceptionType.RESCHEDULE) ex.newEndPeriod ?: c.endPeriod else c.endPeriod
                val date = cal.dateOf(week, dow)
                if (date.isBefore(from) || date.isAfter(to)) continue
                val useCustomClock =
                    ex?.type != ExceptionType.RESCHEDULE ||
                        (ex.newStartPeriod == null && ex.newEndPeriod == null)
                val startClock = (if (useCustomClock) c.startTime else null) ?: cal.startTime(sp)
                val endClock = (if (useCustomClock) c.endTime else null) ?: cal.endTime(ep)
                val s = startClock?.let { date.atTime(it) } ?: continue
                val e = endClock?.let { date.atTime(it) } ?: continue
                out +=
                    CourseOccurrence(
                        course = c,
                        week = week,
                        date = date,
                        startPeriod = sp,
                        endPeriod = ep,
                        start = s,
                        end = e,
                        room = (if (ex?.type == ExceptionType.RESCHEDULE) ex.newRoom else null) ?: c.room,
                        rescheduled = ex?.type == ExceptionType.RESCHEDULE,
                        originalWeek = w,
                    )
            }
        }
        return promoteExamOccurrences(out).sortedWith(compareBy({ it.start }, { it.course.name }))
    }

    /** 将同一天同课程且时间/节次重叠的常规课次提升并归并到考试层，消除随堂考与常规课的假性时间冲突。 */
    private fun promoteExamOccurrences(out: List<CourseOccurrence>): List<CourseOccurrence> {
        val exams = out.filter { it.course.isExam }
        if (exams.isEmpty()) return out
        val examRooms = MutableList(exams.size) { exams[it].room }
        val suppressedIndices = HashSet<Int>()
        for ((idx, occ) in out.withIndex()) {
            if (occ.course.isExam) continue
            val examIdx =
                exams.indexOfFirst { e ->
                    e.date == occ.date &&
                        JxfwSync.isSameCourseForExam(e.course.name, occ.course.name) &&
                        ((occ.start < e.end && e.start < occ.end) || (occ.startPeriod <= e.endPeriod && e.startPeriod <= occ.endPeriod))
                }
            if (examIdx >= 0) {
                suppressedIndices += idx
                if (examRooms[examIdx].isBlank() && occ.room.isNotBlank()) {
                    examRooms[examIdx] = occ.room
                }
            }
        }
        if (suppressedIndices.isEmpty()) return out
        var nextExamIdx = 0
        val result = ArrayList<CourseOccurrence>(out.size - suppressedIndices.size)
        for ((idx, occ) in out.withIndex()) {
            if (idx in suppressedIndices) continue
            if (occ.course.isExam) {
                val room = examRooms[nextExamIdx++]
                result += if (room != occ.room) occ.copy(room = room) else occ
            } else {
                result += occ
            }
        }
        return result
    }

    fun occurrencesInWeek(
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        week: Int,
    ): List<CourseOccurrence> {
        val cal = SemesterCalendar(semester)
        return occurrences(semester, courses, exceptions, cal.weekStart(week), cal.weekEnd(week))
    }

    /** 某课程下一次上课（严格晚于 after），自动归并同学期同名的多时段课程。 */
    fun nextOccurrence(
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        courseId: String,
        after: LocalDateTime,
    ): CourseOccurrence? {
        val cal = SemesterCalendar(semester)
        val anchor = courses.firstOrNull { it.id == courseId && it.deletedAt == null }
        val matching =
            courses.filter { c ->
                c.id == courseId ||
                    (anchor != null && c.deletedAt == null && c.semesterId == anchor.semesterId && c.name.trim() == anchor.name.trim())
            }
        val list =
            occurrences(
                semester,
                matching,
                exceptions,
                after.toLocalDate(),
                cal.weekEnd(cal.totalWeeks),
            )
        return list.firstOrNull { it.start.isAfter(after) }
    }
}
