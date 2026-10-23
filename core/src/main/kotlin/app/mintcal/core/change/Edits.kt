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
