package app.mintcal.core.agenda

import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.recurrence.CourseOccurrence
import java.time.LocalDateTime

/** 「下一件事」：下一节课，以及要在这节课之前交的待办。 */
data class NextUp(
    val occurrence: CourseOccurrence,
    /** 关联到这门课且未完成的待办（含「下节课前」） */
    val tasks: List<Task>,
)

object NextUp {
    /** 接下来 [horizonDays] 天内第一节开始时间晚于 [now]（或正在上）的课；没有返回 null。 */
    fun find(
        snap: Snapshot,
        now: LocalDateTime,
        horizonDays: Long = 7,
    ): NextUp? {
        val s = snap.live()
        val sem = s.currentSemester ?: return null
        val from = now.toLocalDate()
        val occ =
            CourseExpander
                .occurrences(sem, s.courses, s.exceptions, from, from.plusDays(horizonDays))
                .firstOrNull { it.end.isAfter(now) } ?: return null
        val related =
            s.tasks.filter {
                it.parentId == null && !it.done && it.courseId != null &&
                    (it.courseId == occ.course.id || s.courses.firstOrNull { c -> c.id == it.courseId }?.name == occ.course.name)
            }
        return NextUp(occ, related)
    }
}
