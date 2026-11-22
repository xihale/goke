package app.goke.core.change

import app.goke.core.model.Record
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.util.MintJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 单条变更：新增 / 修改（前后值）/ 删除。 */
@Serializable
sealed interface Change {
    @Serializable
    @SerialName("add")
    data class Add(
        val after: Record,
    ) : Change

    @Serializable
    @SerialName("modify")
    data class Modify(
        val before: Record,
        val after: Record,
    ) : Change

    @Serializable
    @SerialName("remove")
    data class Remove(
        val before: Record,
    ) : Change
}

val Change.record: Record
    get() =
        when (this) {
            is Change.Add -> after
            is Change.Modify -> after
            is Change.Remove -> before
        }

/**
 * 变更集：所有写操作（手动、导入、代码、AI）统一产出它。
 * Dry Run 只生成变更集不落库；预览与应用使用同一个变更集，所见即所得。
 */
@Serializable
data class ChangeSet(
    val id: String,
    val description: String = "",
    val source: Source = Source.MANUAL,
    val createdAt: Long = 0,
    val changes: List<Change> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val returnValue: String? = null,
) {
    val isEmpty: Boolean get() = changes.isEmpty()
    val addCount: Int get() = changes.count { it is Change.Add }
    val modifyCount: Int get() = changes.count { it is Change.Modify }
    val removeCount: Int get() = changes.count { it is Change.Remove }

    fun toJson(): String = MintJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): ChangeSet = MintJson.decodeFromString(serializer(), text)
    }
}

/** 应用变更集：按序 upsert / 软删除，并统一 updatedAt。幂等。 */
fun Snapshot.apply(
    cs: ChangeSet,
    at: Long = cs.createdAt,
): Snapshot {
    var s = this
    for (c in cs.changes) {
        s =
            when (c) {
                is Change.Add -> s.put(c.after.withMeta(at, null))
                is Change.Modify -> s.put(c.after.withMeta(at, c.after.deletedAt))
                is Change.Remove -> s.put(c.before.withMeta(at, at))
            }
    }
    return s
}

/** 撤销变更集（反向应用）：新增被物理移除，修改还原为之前的值，删除被恢复。 */
fun Snapshot.undo(
    cs: ChangeSet,
    at: Long,
): Snapshot {
    var s = this
    for (c in cs.changes.asReversed()) {
        s =
            when (c) {
                is Change.Add -> s.purge(c.after)
                is Change.Modify -> s.put(c.before.withMeta(at, c.before.deletedAt))
                is Change.Remove -> s.put(c.before.withMeta(at, null))
            }
    }
    return s
}

private fun Record.sameContent(o: Record) = withMeta(0, null) == o.withMeta(0, null)

/**
 * 能否安全撤销 [cs]：每条记录当前的状态必须仍然等于该变更集应用后的状态。
 * 若之后又有别的操作改动了同一条记录，则不允许单独撤销，避免覆盖后来的修改。
 */
fun Snapshot.canUndo(cs: ChangeSet): Boolean {
    if (cs.isEmpty) return false
    return cs.changes.groupBy { it.record.id }.values.all { list ->
        val cur = find(list.last().record.id)
        when (val c = list.last()) {
            is Change.Add -> cur != null && cur.deletedAt == null && cur.sameContent(c.after)
            is Change.Modify -> cur != null && cur.deletedAt == c.after.deletedAt && cur.sameContent(c.after)
            is Change.Remove -> cur != null && cur.deletedAt != null
        }
    }
}

/** 能否安全重做 [cs]（已被撤销）：每条记录当前状态必须仍等于变更集应用前的状态。 */
fun Snapshot.canRedo(cs: ChangeSet): Boolean {
    if (cs.isEmpty) return false
    return cs.changes.groupBy { it.record.id }.values.all { list ->
        val cur = find(list.first().record.id)
        when (val c = list.first()) {
            is Change.Add -> cur == null
            is Change.Modify -> cur != null && cur.deletedAt == c.before.deletedAt && cur.sameContent(c.before)
            is Change.Remove -> cur != null && cur.deletedAt == null && cur.sameContent(c.before)
        }
    }
}

/** 把「脚本 / AI 产出的新快照」与旧快照对比，得到变更列表（只对比未删除的记录）。 */
object SnapshotDiff {
    fun diff(
        before: Snapshot,
        after: Snapshot,
    ): List<Change> {
        val out = mutableListOf<Change>()

        fun <T : Record> cmp(
            b: List<T>,
            a: List<T>,
        ) {
            val bm = b.filter { it.deletedAt == null }.associateBy { it.id }
            val am = a.filter { it.deletedAt == null }.associateBy { it.id }
            for ((id, rec) in am) {
                val old = bm[id]
                if (old == null) {
                    out += Change.Add(rec)
                } else if (old.withMeta(0, null) != rec.withMeta(0, null)) {
                    out += Change.Modify(old, rec)
                }
            }
            for ((id, old) in bm) if (id !in am) out += Change.Remove(old)
        }
        cmp(before.semesters, after.semesters)
        cmp(before.lists, after.lists)
        cmp(before.courses, after.courses)
        cmp(before.exceptions, after.exceptions)
        cmp(before.events, after.events)
        cmp(before.tasks, after.tasks)
        return out
    }
}
