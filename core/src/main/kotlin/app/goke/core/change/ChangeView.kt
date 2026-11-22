package app.goke.core.change

import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.EntityType
import app.goke.core.model.Event
import app.goke.core.model.Record
import app.goke.core.model.Semester
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.model.displayTitle
import app.goke.core.model.entityType
import app.goke.core.time.WeekSets

enum class ChangeOp { ADD, MODIFY, REMOVE }

/** 字段键由界面映射为本地化文案（core 不带界面文字）。 */
enum class FieldKey {
    NAME,
    TEACHER,
    ROOM,
    DAY,
    PERIODS,
    WEEKS,
    COLOR,
    REMIND,
    NOTE,
    START,
    END,
    ALL_DAY,
    LOCATION,
    CATEGORY,
    RECURRENCE,
    LIST,
    PRIORITY,
    DUE,
    ESTIMATE,
    DONE,
    BLOCK,
    TAGS,
    SEMESTER,
}

data class FieldDiff(
    val key: FieldKey,
    val before: String?,
    val after: String?,
)

/** 变更的「人话」结构化视图：操作、实体类型、标题、字段差异（修改）/ 摘要（新增、删除）。 */
data class ChangeView(
    val op: ChangeOp,
    val type: EntityType,
    val title: String,
    val recordId: String,
    val summary: List<FieldDiff>,
)

object ChangeViews {
    fun of(cs: ChangeSet): List<ChangeView> = cs.changes.map { of(it) }

    fun of(c: Change): ChangeView =
        when (c) {
            is Change.Add -> {
                ChangeView(ChangeOp.ADD, c.after.entityType, c.after.displayTitle, c.after.id, fields(c.after).map { FieldDiff(it.first, null, it.second) })
            }

            is Change.Remove -> {
                ChangeView(ChangeOp.REMOVE, c.before.entityType, c.before.displayTitle, c.before.id, fields(c.before).map { FieldDiff(it.first, it.second, null) })
            }

            is Change.Modify -> {
                val b = fields(c.before).toMap()
                val a = fields(c.after).toMap()
                val diffs = (b.keys + a.keys).filter { b[it] != a[it] }.map { FieldDiff(it, b[it], a[it]) }
                ChangeView(ChangeOp.MODIFY, c.after.entityType, c.after.displayTitle, c.after.id, diffs)
            }
        }

    private fun fields(r: Record): List<Pair<FieldKey, String>> =
        when (r) {
            is Course -> {
                listOf(
                    FieldKey.NAME to r.name,
                    FieldKey.TEACHER to r.teacher,
                    FieldKey.ROOM to r.room,
                    FieldKey.DAY to r.dayOfWeek.toString(),
                    FieldKey.PERIODS to "${r.startPeriod}-${r.endPeriod}",
                    FieldKey.WEEKS to WeekSets.format(r.weeks),
                    FieldKey.COLOR to r.colorIndex.toString(),
                    FieldKey.REMIND to (r.remindMinutes?.toString() ?: ""),
                ).filter { it.second.isNotEmpty() }
            }

            is Event -> {
                listOf(
                    FieldKey.NAME to r.title,
                    FieldKey.START to r.start.toString(),
                    FieldKey.END to r.end.toString(),
                    FieldKey.ALL_DAY to r.allDay.toString(),
                    FieldKey.LOCATION to r.location,
                    FieldKey.CATEGORY to r.category,
                    FieldKey.COLOR to r.colorIndex.toString(),
                    FieldKey.REMIND to (r.remindMinutes?.toString() ?: ""),
                    FieldKey.RECURRENCE to (r.recurrence?.let { "${it.freq}/${it.interval}" } ?: ""),
                ).filter { it.second.isNotEmpty() }
            }

            is Task -> {
                listOf(
                    FieldKey.NAME to r.title,
                    FieldKey.NOTE to r.note,
                    FieldKey.LIST to (r.listId ?: ""),
                    FieldKey.PRIORITY to r.priority.toString(),
                    FieldKey.DUE to (r.due?.toString() ?: ""),
                    FieldKey.ESTIMATE to (r.estimateMinutes?.toString() ?: ""),
                    FieldKey.DONE to r.done.toString(),
                    FieldKey.BLOCK to (r.blockStart?.toString() ?: ""),
                    FieldKey.TAGS to r.tags.joinToString(","),
                ).filter { it.second.isNotEmpty() }
            }

            is TaskList -> {
                listOf(FieldKey.NAME to r.name)
            }

            is Semester -> {
                listOf(FieldKey.NAME to r.name, FieldKey.START to r.week1Monday.toString(), FieldKey.WEEKS to r.totalWeeks.toString())
            }

            is CourseException -> {
                listOf(FieldKey.WEEKS to r.week.toString(), FieldKey.NAME to r.type.name)
            }
        }
}
