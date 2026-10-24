package app.mintcal.core.todo

import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Task
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.recurrence.RecurrenceEngine
import app.mintcal.core.util.IdGenerator
import app.mintcal.core.util.Ids
import java.time.LocalDate
import java.time.LocalDateTime

enum class SmartList { TODAY, PLANNED, ALL, DONE, OVERDUE }

object TaskLogic {
    /** 只看顶层任务（子任务挂在父任务下）。 */
    fun topLevel(tasks: List<Task>): List<Task> = tasks.filter { it.deletedAt == null && it.parentId == null }

    fun subtasks(
        tasks: List<Task>,
        parentId: String,
    ): List<Task> = tasks.filter { it.deletedAt == null && it.parentId == parentId }.sortedBy { it.sortOrder }

    /** 子任务进度 (完成数, 总数)。 */
    fun progress(
        tasks: List<Task>,
        parentId: String,
    ): Pair<Int, Int> {
        val subs = subtasks(tasks, parentId)
        return subs.count { it.done } to subs.size
    }

    fun dueDate(t: Task): LocalDate? = t.due?.toLocalDate()

    fun isOverdue(
        t: Task,
        now: LocalDateTime,
    ): Boolean {
        if (t.done) return false
        val d = t.due ?: return false
        return if (t.dueHasTime) d.isBefore(now) else d.toLocalDate().isBefore(now.toLocalDate())
    }

    fun filter(
        tasks: List<Task>,
        which: SmartList,
        now: LocalDateTime,
    ): List<Task> {
        val today = now.toLocalDate()
        val top = topLevel(tasks)
        val list =
            when (which) {
                // 已完成的今天任务保留在区内（排序沉底、划线），与「今日」页行为一致，勾选不消失
                SmartList.TODAY -> top.filter { (!it.done && isOverdue(it, now)) || dueDate(it) == today || it.startDate == today || it.blockStart?.toLocalDate() == today }

                SmartList.PLANNED -> top.filter { !it.done && dueDate(it)?.let { d -> d.isAfter(today) && !d.isAfter(today.plusDays(7)) } == true }

                SmartList.ALL -> top.filter { !it.done }

                SmartList.DONE -> top.filter { it.done }

                SmartList.OVERDUE -> top.filter { isOverdue(it, now) }
            }
        return list.sortedWith(compareBy<Task>({ it.done }, { it.due == null }, { it.due }, { -it.priority }, { it.sortOrder }))
    }

    /**
     * 完成任务。普通任务只标记完成；重复任务「完成后重新计算下一次」：本次标记完成，同时生成下一次实例。
     * 返回 (完成后的任务, 下一次实例或 null)。
     */
    fun complete(
        task: Task,
        nowMillis: Long,
        now: LocalDateTime,
        ids: IdGenerator = Ids,
    ): Pair<Task, Task?> {
        val finished = task.copy(done = true, doneAt = nowMillis, updatedAt = nowMillis)
        val rule: Recurrence = task.repeat ?: return finished to null
        val anchorDate = (task.due?.toLocalDate() ?: task.startDate ?: now.toLocalDate())
        val nextDate = RecurrenceEngine.next(rule.copy(count = null), anchorDate, maxOf(anchorDate, now.toLocalDate().minusDays(0))) ?: return finished to null
        val shift =
            java.time.temporal.ChronoUnit.DAYS
                .between(anchorDate, nextDate)
        val next =
            task.copy(
                id = ids.next(),
                done = false,
                doneAt = null,
                due = task.due?.plusDays(shift),
                startDate = task.startDate?.plusDays(shift),
                blockStart = null,
                updatedAt = nowMillis,
                repeat = rule.copy(count = rule.count?.minus(1)).takeIf { rule.count == null || rule.count > 1 },
            )
        return finished to next
    }

    fun reopen(
        task: Task,
        nowMillis: Long,
    ): Task = task.copy(done = false, doneAt = null, updatedAt = nowMillis)

    /** 关联课程且「下节课前」的任务，截止为该课程下一次上课的开始时间。 */
    fun effectiveDue(
        task: Task,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        now: LocalDateTime,
    ): LocalDateTime? {
        if (task.dueBeforeNextClass && task.courseId != null && semester != null) {
            CourseExpander.nextOccurrence(semester, courses, exceptions, task.courseId, now)?.let { return it.start }
        }
        return task.due
    }

    /** 时间块：结束时间 = 开始 + 预计耗时（未填则用默认分钟数）。 */
    fun blockEnd(
        task: Task,
        defaultMinutes: Int = 30,
    ): LocalDateTime? = task.blockStart?.plusMinutes((task.blockMinutes ?: task.estimateMinutes ?: defaultMinutes).toLong())
}
