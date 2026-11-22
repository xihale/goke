package app.goke.core.model

import kotlinx.serialization.Serializable

/**
 * 某一时刻的完整数据快照（不含已删除之外的过滤，调用方按需取 live）。
 * 高级修改的 Dry Run 在快照上执行，ChangeSet 在快照上应用与撤销。
 */
@Serializable
data class Snapshot(
    val semesters: List<Semester> = emptyList(),
    val courses: List<Course> = emptyList(),
    val exceptions: List<CourseException> = emptyList(),
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
) {
    val currentSemester: Semester?
        get() =
            semesters.firstOrNull { it.isCurrent && it.deletedAt == null }
                ?: semesters.firstOrNull { it.deletedAt == null }

    /** 无墓碑时 [live] 直接返回自身，避免每次调用都复制 6 个列表。按实例缓存（copy 后重算）。 */
    private val hasDeleted: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        semesters.any { it.deletedAt != null } ||
            courses.any { it.deletedAt != null } ||
            exceptions.any { it.deletedAt != null } ||
            events.any { it.deletedAt != null } ||
            tasks.any { it.deletedAt != null } ||
            lists.any { it.deletedAt != null }
    }

    fun live(): Snapshot =
        if (!hasDeleted) {
            this
        } else {
            Snapshot(
                semesters.filter { it.deletedAt == null },
                courses.filter { it.deletedAt == null },
                exceptions.filter { it.deletedAt == null },
                events.filter { it.deletedAt == null },
                tasks.filter { it.deletedAt == null },
                lists.filter { it.deletedAt == null },
            )
        }

    fun allRecords(): List<Record> = semesters + courses + exceptions + events + tasks + lists

    fun find(id: String): Record? = allRecords().firstOrNull { it.id == id }

    /** 插入或替换（按 id）。 */
    fun put(r: Record): Snapshot =
        when (r) {
            is Semester -> copy(semesters = semesters.upsert(r))
            is Course -> copy(courses = courses.upsert(r))
            is CourseException -> copy(exceptions = exceptions.upsert(r))
            is Event -> copy(events = events.upsert(r))
            is Task -> copy(tasks = tasks.upsert(r))
            is TaskList -> copy(lists = lists.upsert(r))
        }

    /** 物理移除（仅用于撤销「新增」）。 */
    fun purge(r: Record): Snapshot =
        when (r) {
            is Semester -> copy(semesters = semesters.filterNot { it.id == r.id })
            is Course -> copy(courses = courses.filterNot { it.id == r.id })
            is CourseException -> copy(exceptions = exceptions.filterNot { it.id == r.id })
            is Event -> copy(events = events.filterNot { it.id == r.id })
            is Task -> copy(tasks = tasks.filterNot { it.id == r.id })
            is TaskList -> copy(lists = lists.filterNot { it.id == r.id })
        }

    private fun <T : Record> List<T>.upsert(item: T): List<T> {
        val idx = indexOfFirst { it.id == item.id }
        return if (idx < 0) this + item else toMutableList().also { it[idx] = item }
    }
}

/** 实体类型名（用于变更集展示）。 */
enum class EntityType { SEMESTER, COURSE, EXCEPTION, EVENT, TASK, LIST }

val Record.entityType: EntityType
    get() =
        when (this) {
            is Semester -> EntityType.SEMESTER
            is Course -> EntityType.COURSE
            is CourseException -> EntityType.EXCEPTION
            is Event -> EntityType.EVENT
            is Task -> EntityType.TASK
            is TaskList -> EntityType.LIST
        }

val Record.displayTitle: String
    get() =
        when (this) {
            is Semester -> {
                name
            }

            is Course -> {
                name
            }

            is CourseException -> {
                "#$week"
            }

            is Event -> {
                title
            }

            is Task -> {
                title
            }

            is TaskList -> {
                name
            }
        }
