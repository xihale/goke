package app.mintcal.core.importer

import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Course
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.util.IdGenerator
import app.mintcal.core.util.Ids

/** 把导入结果与现有数据对比，生成变更集（重复导入时更新而不是叠加）。 */
object ImportPlanner {
    fun planCourses(
        snapshot: Snapshot,
        semester: Semester,
        imported: List<Course>,
        now: Long,
        description: String,
        replaceImported: Boolean = true,
        ids: IdGenerator = Ids,
    ): ChangeSet {
        val existing = snapshot.courses.filter { it.deletedAt == null && it.semesterId == semester.id && it.source == Source.IMPORT }
        val pool = if (replaceImported) existing.toMutableList() else mutableListOf()
        val changes = mutableListOf<Change>()
        for (c in imported) {
            val match = pool.firstOrNull { it.name == c.name && it.dayOfWeek == c.dayOfWeek && it.startPeriod == c.startPeriod && it.endPeriod == c.endPeriod }
            if (match != null) {
                pool.remove(match)
                val merged = c.copy(id = match.id, colorIndex = match.colorIndex)
                if (merged.withMeta(0, null) != match.withMeta(0, null)) changes += Change.Modify(match, merged)
            } else changes += Change.Add(c)
        }
        for (left in pool) changes += Change.Remove(left)
        return ChangeSet(id = ids.next(), description = description, source = Source.IMPORT, createdAt = now, changes = changes)
    }

    fun planAdds(records: List<app.mintcal.core.model.Record>, now: Long, description: String, ids: IdGenerator = Ids): ChangeSet =
        ChangeSet(ids.next(), description, Source.IMPORT, now, records.map { Change.Add(it) })
}
