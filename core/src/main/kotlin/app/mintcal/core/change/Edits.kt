package app.mintcal.core.change

import app.mintcal.core.model.Course
import app.mintcal.core.model.Record
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.IdGenerator
import app.mintcal.core.util.Ids
import java.time.LocalDate
import java.time.LocalDateTime

/** 手动编辑也走变更集：保存 / 删除（带级联）/ 完成待办 / 复制学期，统一可撤销、可记录。 */
object Edits {
    fun save(
        s: Snapshot,
        r: Record,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val before = s.find(r.id)?.takeIf { it.deletedAt == null }
        val change = if (before == null) Change.Add(r) else Change.Modify(before, r)
        return ChangeSet(ids.next(), description, Source.MANUAL, now, listOf(change))
    }

    fun saveAll(
        s: Snapshot,
        records: List<Record>,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val changes =
            records.map { r ->
                val before = s.find(r.id)?.takeIf { it.deletedAt == null }
                if (before == null) Change.Add(r) else Change.Modify(before, r)
            }
        return ChangeSet(ids.next(), description, Source.MANUAL, now, changes)
    }

    /** 删除并级联：课程 → 其调课记录；待办 → 子任务；清单 → 其下待办改为无清单。 */
    fun delete(
        s: Snapshot,
        r: Record,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val live = s.live()
        val changes = mutableListOf<Change>()
        changes += Change.Remove(live.find(r.id) ?: r)
        when (r) {
            is Course -> {
                live.exceptions.filter { it.courseId == r.id }.forEach { changes += Change.Remove(it) }
            }

            is Task -> {
                live.tasks.filter { it.parentId == r.id }.forEach { changes += Change.Remove(it) }
            }

            is TaskList -> {
                live.tasks.filter { it.listId == r.id }.forEach { changes += Change.Modify(it, it.copy(listId = null)) }
            }

            else -> {}
        }
        return ChangeSet(ids.next(), description, Source.MANUAL, now, changes)
    }

    /** 批量改任务字段，合成一个变更集（一次撤销）。无变化的任务跳过。 */
    fun updateTasks(
        tasks: List<Task>,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
        transform: (Task) -> Task,
    ): ChangeSet {
        val changes =
            tasks.mapNotNull { t ->
                val n = transform(t)
                if (n == t) null else Change.Modify(t, n)
            }
        return ChangeSet(ids.next(), description, Source.MANUAL, now, changes)
    }

    /**
     * 同区手动排序：把 [draggedId] 插到 [beforeId] 前面，整区重写 sortOrder（idx * 10）。
     * 只对同区可见排序生效——分区仍先按截止 / 优先级排，手动序在相同截止 + 优先级内生效。
     */
    fun reorder(
        bucketTasks: List<Task>,
        draggedId: String,
        beforeId: String,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val moved = bucketTasks.firstOrNull { it.id == draggedId } ?: return ChangeSet(ids.next(), description, Source.MANUAL, now)
        val seq = bucketTasks.filter { it.id != draggedId }.toMutableList()
        val at = seq.indexOfFirst { it.id == beforeId }.coerceAtLeast(0)
        seq.add(at, moved)
        val byId = seq.associateBy { it.id }
        return updateTasks(bucketTasks, description, now, ids) { t ->
            val idx = byId.entries.indexOfFirst { it.key == t.id }
            val n = if (idx >= 0) idx * 10 else t.sortOrder
            if (n != t.sortOrder) t.copy(sortOrder = n) else t
        }
    }

    /** 改期：[date] 为 null 清除日期（回收集箱）。带时间的保留原时刻，若改到今天且原时刻已过且指定 [clearTimeIfPast] 则转为全天，避免继续卡在逾期。 */
    fun reschedule(
        tasks: List<Task>,
        date: LocalDate?,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
        clearTimeIfPast: LocalDateTime? = null,
    ): ChangeSet =
        updateTasks(tasks, description, now, ids) { t ->
            if (date == null) {
                t.copy(due = null, dueHasTime = false, startDate = null, blockStart = null, dueBeforeNextClass = false)
            } else {
                val origTime = if (t.dueHasTime) t.due?.toLocalTime() else null
                val dropPastTime =
                    origTime != null &&
                        clearTimeIfPast != null &&
                        LocalDateTime.of(date, origTime).isBefore(clearTimeIfPast)
                val time = if (origTime != null && !dropPastTime) origTime else java.time.LocalTime.of(23, 59)
                t.copy(
                    due = LocalDateTime.of(date, time),
                    dueHasTime = t.dueHasTime && !dropPastTime,
                    startDate = null,
                    blockStart = null,
                    dueBeforeNextClass = false,
                )
            }
        }

    /** 「不做了」：放弃而非完成。 */
    fun dropTasks(
        tasks: List<Task>,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet = updateTasks(tasks.filter { !it.done }, description, now, ids) { it.copy(done = true, dropped = true, doneAt = now) }

    /** 变成另一条任务的子任务（只允许一层，且被拖的任务不能自带子任务）。条件不满足返回空变更集。 */
    fun makeSubtask(
        s: Snapshot,
        task: Task,
        parent: Task,
        description: String,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val ok = task.id != parent.id && parent.parentId == null && task.parentId != parent.id && s.live().tasks.none { it.parentId == task.id }
        if (!ok) return ChangeSet(ids.next(), description, Source.MANUAL, now, emptyList())
        val order =
            (
                s
                    .live()
                    .tasks
                    .filter { it.parentId == parent.id }
                    .maxOfOrNull { it.sortOrder } ?: 0
            ) + 1
        return updateTasks(listOf(task), description, now, ids) { it.copy(parentId = parent.id, listId = parent.listId, sortOrder = order) }
    }

    /** 完成 / 取消完成待办；重复任务完成后生成下一次实例。 */
    fun toggleTask(
        s: Snapshot,
        task: Task,
        done: Boolean,
        now: Long,
        nowDt: LocalDateTime,
        description: String,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val changes = mutableListOf<Change>()
        if (done) {
            val (finished, next) = TaskLogic.complete(task, now, nowDt, ids)
            changes += Change.Modify(task, finished)
            if (next != null) changes += Change.Add(next)
        } else {
            changes += Change.Modify(task, TaskLogic.reopen(task, now))
        }
        return ChangeSet(ids.next(), description, Source.MANUAL, now, changes)
    }

    /** 把当前学期的课程复制到新学期（不复制调课记录），新学期成为当前学期。 */
    fun copyToNextSemester(
        s: Snapshot,
        from: Semester,
        name: String,
        week1Monday: LocalDate,
        now: Long,
        description: String,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val live = s.live()
        val newSem = from.copy(id = ids.next(), name = name, week1Monday = week1Monday, isCurrent = true, updatedAt = now)
        val changes = mutableListOf<Change>()
        live.semesters.filter { it.isCurrent }.forEach { changes += Change.Modify(it, it.copy(isCurrent = false)) }
        changes += Change.Add(newSem)
        live.courses.filter { it.semesterId == from.id }.forEach {
            changes += Change.Add(it.copy(id = ids.next(), semesterId = newSem.id, updatedAt = now))
        }
        return ChangeSet(ids.next(), description, Source.MANUAL, now, changes)
    }
}
