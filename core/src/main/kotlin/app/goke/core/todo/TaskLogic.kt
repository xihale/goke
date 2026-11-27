package app.goke.core.todo

import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Recurrence
import app.goke.core.model.Semester
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.recurrence.RecurrenceEngine
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import java.time.LocalDate
import java.time.LocalDateTime

enum class SmartList { TODAY, PLANNED, ALL, DONE, OVERDUE }

/** 待办时间线的分区，顺序即显示顺序。 */
enum class Bucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER, INBOX, DONE }

data class TimelineSection(
    val bucket: Bucket,
    val tasks: List<Task>,
)

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
        if (t.done || t.blockStart != null) return false
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

    /** 任务落在哪个分区。[due] 传「生效截止」（已考虑下节课前）。 */
    fun bucketOf(
        task: Task,
        due: LocalDateTime?,
        now: LocalDateTime,
    ): Bucket {
        if (task.done) return Bucket.DONE
        val today = now.toLocalDate()
        if (due != null) {
            val hasTime = task.dueHasTime || task.dueBeforeNextClass
            val date = due.toLocalDate()
            return when {
                if (hasTime) due.isBefore(now) else date.isBefore(today) -> Bucket.OVERDUE
                date == today -> Bucket.TODAY
                date == today.plusDays(1) -> Bucket.TOMORROW
                !date.isAfter(endOfWeek(today)) -> Bucket.THIS_WEEK
                else -> Bucket.LATER
            }
        }
        val start = task.startDate ?: task.blockStart?.toLocalDate()
        if (start != null) {
            return when {
                !start.isAfter(today) -> Bucket.TODAY
                start == today.plusDays(1) -> Bucket.TOMORROW
                !start.isAfter(endOfWeek(today)) -> Bucket.THIS_WEEK
                else -> Bucket.LATER
            }
        }
        return Bucket.INBOX
    }

    private fun endOfWeek(today: LocalDate): LocalDate = today.with(java.time.DayOfWeek.SUNDAY)

    /**
     * 单一时间线：逾期 / 今天 / 明天 / 本周 / 之后 / 收集箱（无日期）/ 已完成，只返回非空分区。
     * 只含顶层任务；[dueOf] 用于「下节课前」这类生效截止。
     */
    fun timeline(
        tasks: List<Task>,
        now: LocalDateTime,
        dueOf: (Task) -> LocalDateTime? = { it.due },
    ): List<TimelineSection> {
        val groups = topLevel(tasks).groupBy { bucketOf(it, dueOf(it), now) }
        return Bucket.entries.mapNotNull { b ->
            val list = groups[b].orEmpty()
            if (list.isEmpty()) {
                null
            } else {
                val sorted =
                    if (b == Bucket.DONE) {
                        list.sortedByDescending { it.doneAt ?: 0L }
                    } else {
                        list.sortedWith(compareBy<Task>({ dueOf(it) == null }, { dueOf(it) }, { -it.priority }, { it.sortOrder }))
                    }
                TimelineSection(b, sorted)
            }
        }
    }

    /** 把任务拖进某分区时应设置的日期；null = 清除日期（收集箱）；该分区无法落点（逾期 / 已完成 / 本周已无可选日）返回 [NO_DROP]。 */
    fun dropDate(
        bucket: Bucket,
        today: LocalDate,
    ): LocalDate? =
        when (bucket) {
            Bucket.TODAY -> today
            Bucket.TOMORROW -> today.plusDays(1)
            Bucket.THIS_WEEK -> endOfWeek(today).takeIf { !it.isBefore(today.plusDays(2)) } ?: NO_DROP
            Bucket.LATER -> today.plusDays(7)
            Bucket.INBOX -> null
            Bucket.OVERDUE, Bucket.DONE -> NO_DROP
        }

    /** [dropDate] 的「不可落点」哨兵。 */
    val NO_DROP: LocalDate = LocalDate.MIN

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
    ): Task = task.copy(done = false, doneAt = null, dropped = false, updatedAt = nowMillis)

    /** 判断任务 [task] 是否关联到课程 [target]（按同 ID 或同学期同名多时段课程归并）。 */
    fun matchesCourse(
        task: Task,
        target: Course,
        courseById: Map<String, Course>,
    ): Boolean {
        val cid = task.courseId ?: return false
        if (cid == target.id) return true
        val linked = courseById[cid] ?: return false
        return linked.semesterId == target.semesterId && linked.name.trim() == target.name.trim()
    }

    /** 列出关联到课程 [target] 的顶层任务（默认仅未完成）。 */
    fun forCourse(
        tasks: List<Task>,
        courses: List<Course>,
        target: Course,
        includeDone: Boolean = false,
    ): List<Task> {
        val byId = courses.filter { it.deletedAt == null }.associateBy { it.id }
        return tasks.filter { t ->
            t.deletedAt == null &&
                t.parentId == null &&
                (includeDone || !t.done) &&
                matchesCourse(t, target, byId)
        }
    }

    /** 解析任务 [task] 对应的单一具体课次；无关联或已取消/找不到时返回 null。 */
    fun targetOccurrence(
        task: Task,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        now: LocalDateTime,
    ): CourseOccurrence? {
        val cid = task.courseId ?: return null
        val sem = semester ?: return null
        val cw = task.courseWeek
        if (cw != null) {
            return CourseExpander.occurrence(sem, courses, exceptions, cid, cw)
        }
        if (task.dueBeforeNextClass || (task.due == null && task.blockStart == null)) {
            return CourseExpander.nextOccurrence(sem, courses, exceptions, cid, now)
        }
        val anchor = task.blockStart ?: task.due ?: return null
        val anchorCourse = courses.firstOrNull { it.id == cid && it.deletedAt == null }
        val matching =
            courses.filter { c ->
                c.deletedAt == null &&
                    (
                        c.id == cid ||
                            (anchorCourse != null && c.semesterId == anchorCourse.semesterId && c.name.trim() == anchorCourse.name.trim())
                    )
            }
        val anchorDate = anchor.toLocalDate()
        val onDate = CourseExpander.occurrences(sem, matching, exceptions, anchorDate, anchorDate)
        if (onDate.isNotEmpty()) {
            val sameSlot = onDate.filter { it.course.id == cid }
            val pool = sameSlot.ifEmpty { onDate }
            return pool.firstOrNull { !it.end.isBefore(anchor) } ?: pool.first()
        }
        return CourseExpander.nextOccurrence(sem, courses, exceptions, cid, anchor.minusSeconds(1))
    }

    /** 判断任务 [task] 是否属于具体课次 [occ]。 */
    fun matchesOccurrence(
        task: Task,
        occ: CourseOccurrence,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        now: LocalDateTime,
    ): Boolean {
        val cid = task.courseId ?: return false
        val cw = task.courseWeek
        if (cw != null) {
            return cid == occ.course.id && cw == occ.originalWeek
        }
        val target = targetOccurrence(task, semester, courses, exceptions, now) ?: return false
        return target.course.id == occ.course.id && target.originalWeek == occ.originalWeek
    }

    /** 列出属于具体课次 [occ] 的顶层任务（默认仅未完成）。 */
    fun forOccurrence(
        tasks: List<Task>,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        occ: CourseOccurrence,
        now: LocalDateTime,
        includeDone: Boolean = false,
    ): List<Task> =
        tasks.filter { t ->
            t.deletedAt == null &&
                t.parentId == null &&
                (includeDone || !t.done) &&
                matchesOccurrence(t, occ, semester, courses, exceptions, now)
        }

    /** 统计每个具体课次 `(courseId, originalWeek)` 上的未完成顶层课程待办数。 */
    fun occurrenceCounts(
        tasks: List<Task>,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        now: LocalDateTime,
    ): Map<Pair<String, Int>, Int> {
        val sem = semester ?: return emptyMap()
        val byId = courses.filter { it.deletedAt == null }.associateBy { it.id }
        val nextCache = HashMap<Any, CourseOccurrence?>()
        val counts = HashMap<Pair<String, Int>, Int>()
        for (t in tasks) {
            if (t.deletedAt != null || t.parentId != null || t.done) continue
            val cid = t.courseId ?: continue
            val cw = t.courseWeek
            val occ =
                if (cw != null) {
                    CourseExpander.occurrence(sem, courses, exceptions, cid, cw)
                } else if (t.dueBeforeNextClass || (t.due == null && t.blockStart == null)) {
                    val key: Any = byId[cid]?.let { it.semesterId to it.name.trim() } ?: cid
                    if (nextCache.containsKey(key)) {
                        nextCache[key]
                    } else {
                        CourseExpander.nextOccurrence(sem, courses, exceptions, cid, now).also { nextCache[key] = it }
                    }
                } else {
                    targetOccurrence(t, sem, courses, exceptions, now)
                }
            if (occ != null) {
                val occKey = occ.course.id to occ.originalWeek
                counts[occKey] = (counts[occKey] ?: 0) + 1
            }
        }
        return counts
    }

    /** 关联课程且「下节课前」的任务，截止为该课程指定课次或下一次上课的开始时间。 */
    fun effectiveDue(
        task: Task,
        semester: Semester?,
        courses: List<Course>,
        exceptions: List<CourseException>,
        now: LocalDateTime,
    ): LocalDateTime? {
        if (task.dueBeforeNextClass && task.courseId != null && semester != null) {
            val cw = task.courseWeek
            if (cw != null) {
                return CourseExpander.occurrence(semester, courses, exceptions, task.courseId, cw)?.start ?: task.due
            }
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
