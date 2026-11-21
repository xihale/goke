package app.mintcal.core.recurrence

import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Semester
import app.mintcal.core.time.SemesterCalendar
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
                val s = cal.courseStart(c, week, dow, sp) ?: continue
                val e = cal.courseEnd(c, week, dow, ep) ?: continue
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
        return out.sortedWith(compareBy({ it.start }, { it.course.name }))
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

    /** 某课程下一次上课（严格晚于 after）。 */
    fun nextOccurrence(
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        courseId: String,
        after: LocalDateTime,
    ): CourseOccurrence? {
        val cal = SemesterCalendar(semester)
        val list =
            occurrences(
                semester,
                courses.filter { it.id == courseId },
                exceptions,
                after.toLocalDate(),
                cal.weekEnd(cal.totalWeeks),
            )
        return list.firstOrNull { it.start.isAfter(after) }
    }
}
