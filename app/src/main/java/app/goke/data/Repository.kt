package app.goke.data

import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.apply
import app.goke.core.change.canRedo
import app.goke.core.change.canUndo
import app.goke.core.change.record
import app.goke.core.change.undo
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Event
import app.goke.core.model.GdutPeriods
import app.goke.core.model.Record
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.sync.SyncPlanner
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 数据仓库：持有内存快照（StateFlow），所有写入都是「变更集」——写库 + 记录历史 + 更新快照，
 * 因此撤销、预览、导入、AI 修改走同一条路径。
 */
class Repository(
    private val db: MintDb,
    private val ids: IdGenerator = Ids,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _history = MutableStateFlow<List<OpRow>>(emptyList())
    val history: StateFlow<List<OpRow>> = _history.asStateFlow()

    /** 操作历史按需加载：从未打开过历史页就不读 oplog（冷启动路径上省掉 100 条大 JSON 的解析）。 */
    private var historyLoaded = false

    /** 打开历史页（或撤销页）时调用；之后每次提交都会刷新内存中的历史。 */
    suspend fun ensureHistoryLoaded() =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!historyLoaded) {
                    _history.value = db.ops(100)
                    historyLoaded = true
                }
            }
        }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val lock = Mutex()

    suspend fun load() =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val records = db.loadRecords()
                // 一次性分组构建快照（旧实现逐条 put()：每条都全表线性查重 + 整表复制，O(N²)）
                val semesters = ArrayList<Semester>()
                val courses = ArrayList<Course>()
                val exceptions = ArrayList<CourseException>()
                val events = ArrayList<Event>()
                val tasks = ArrayList<Task>()
                val lists = ArrayList<TaskList>()
                val now = clock()
                // 30 天前删除的回收站内容顺带清理（物理删除，与旧行为一致）
                val cutoff = now - TRASH_DAYS * 24L * 3600_000L
                val expiredIds = ArrayList<String>()
                for (r in records) {
                    if ((r.deletedAt ?: Long.MAX_VALUE) < cutoff) {
                        expiredIds.add(r.id)
                        continue
                    }
                    when (r) {
                        is Semester -> semesters.add(r)
                        is Course -> courses.add(r)
                        is CourseException -> exceptions.add(r)
                        is Event -> events.add(r)
                        is Task -> tasks.add(r)
                        is TaskList -> lists.add(r)
                    }
                }
                if (expiredIds.isNotEmpty()) db.write(emptyList(), expiredIds)
                var snap = Snapshot(semesters, courses, exceptions, events, tasks, lists)
                snap = withDefaultSemester(snap, now)
                _snapshot.value = snap
                // 历史不在这里读：ensureHistoryLoaded() 由历史页首次进入时触发
                _ready.value = true
            }
        }

    private fun withDefaultSemester(
        snap: Snapshot,
        now: Long,
    ): Snapshot {
        if (snap.semesters.any { it.deletedAt == null }) return snap
        val sem = Semester(ids.next(), "2026–2027 学年第一学期", GdutPeriods.week1Monday, 20, GdutPeriods.default, true, now)
        db.write(listOf(sem), emptyList())
        return snap.put(sem)
    }

    /** 登录了同步服务器时为 true：此时被物理删除的记录要以墓碑形式留存，等待告诉服务器。 */
    @Volatile var trackPurges: Boolean = false

    /** 应用来自服务器的记录：不写操作历史，只写入比本地新（或本地没有）的版本。返回实际写入的条数。 */
    suspend fun applyRemote(records: List<Record>): Int =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val toWrite = SyncPlanner.incoming(_snapshot.value, records)
                if (toWrite.isEmpty()) return@withLock 0
                db.write(toWrite, emptyList())
                var s = _snapshot.value
                for (r in toWrite) s = s.put(r)
                _snapshot.value = s
                toWrite.size
            }
        }

    /** 首次登录到已有数据的服务器时，丢弃本地自动创建的默认学期（不留墓碑，也不会推送）。 */
    suspend fun discardAllLocal() =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val all = _snapshot.value.allRecords()
                db.write(emptyList(), all.map { it.id })
                _snapshot.value = Snapshot()
            }
        }

    /** 同步后若本地没有任何学期（服务器上也没有），补回默认学期。 */
    suspend fun ensureDefaultSemester() =
        withContext(Dispatchers.IO) {
            lock.withLock { _snapshot.value = withDefaultSemester(_snapshot.value, clock()) }
        }

    suspend fun pendingPurged(): List<Record> = withContext(Dispatchers.IO) { lock.withLock { db.loadPurged() } }

    suspend fun clearPurged(ids: Collection<String>) = withContext(Dispatchers.IO) { lock.withLock { db.clearPurged(ids) } }

    /** 应用变更集。[record] 为 false 时不进入历史（例如恢复备份）。 */
    suspend fun commit(
        cs: ChangeSet,
        record: Boolean = true,
    ): ChangeSet =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val now = clock()
                val next = _snapshot.value.apply(cs, now)
                val ids = cs.changes.map { it.record.id }.distinct()
                val upserts = ids.mapNotNull { next.find(it) }
                val op = if (record && !cs.isEmpty) OpRow(cs.id, now, cs.description, cs.source.name, cs.toJson(), null) else null
                db.write(upserts, emptyList(), op)
                if (op != null) db.trimOps()
                _snapshot.value = next
                // 历史从未被加载过就不再回读 oplog：编辑路径上省掉一次 100 条大 JSON 的查询+解析
                if (op != null && historyLoaded) _history.value = db.ops(100)
                cs
            }
        }

    /** 某次操作当前能否撤销 / 重做（状态比对，见 [canUndo]、[canRedo]）。 */
    fun undoable(
        op: OpRow,
        snapshot: Snapshot = _snapshot.value,
    ): Boolean = op.undoneAt == null && runCatching { snapshot.canUndo(ChangeSet.fromJson(op.json)) }.getOrDefault(false)

    fun redoable(
        op: OpRow,
        snapshot: Snapshot = _snapshot.value,
    ): Boolean = op.undoneAt != null && runCatching { snapshot.canRedo(ChangeSet.fromJson(op.json)) }.getOrDefault(false)

    /** 撤销某次操作。若之后的操作改动了同样的内容则返回 false，不做任何事。可连续撤销多步。 */
    suspend fun undo(opId: String): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val op = db.ops(100).firstOrNull { it.id == opId && it.undoneAt == null } ?: return@withLock false
                val cs = ChangeSet.fromJson(op.json)
                if (!_snapshot.value.canUndo(cs)) return@withLock false
                val now = clock()
                val next = _snapshot.value.undo(cs, now)
                val touched = cs.changes.map { it.record.id }.distinct()
                val deletes = cs.changes.filterIsInstance<Change.Add>().map { it.after.id }
                val upserts = touched.filter { it !in deletes }.mapNotNull { next.find(it) }
                val tombstones = if (trackPurges) cs.changes.filterIsInstance<Change.Add>().map { it.after.withMeta(now, now) } else emptyList()
                db.write(upserts, deletes, null, opId to now, tombstones)
                _snapshot.value = next
                if (historyLoaded) _history.value = db.ops(100)
                true
            }
        }

    /** 重做一个已撤销的操作；当前内容已和撤销后不同则返回 false。 */
    suspend fun redo(opId: String): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val op = db.ops(100).firstOrNull { it.id == opId && it.undoneAt != null } ?: return@withLock false
                val cs = ChangeSet.fromJson(op.json)
                if (!_snapshot.value.canRedo(cs)) return@withLock false
                val now = clock()
                val next = _snapshot.value.apply(cs, now)
                val upserts =
                    cs.changes
                        .map { it.record.id }
                        .distinct()
                        .mapNotNull { next.find(it) }
                db.write(upserts, emptyList(), null, opId to null)
                _snapshot.value = next
                if (historyLoaded) _history.value = db.ops(100)
                true
            }
        }

    suspend fun restoreFromTrash(r: Record) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val now = clock()
                val restored = r.withMeta(now, null)
                db.write(listOf(restored), emptyList())
                _snapshot.value = _snapshot.value.put(restored)
            }
        }

    suspend fun purge(records: Collection<Record>) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val tombstones = if (trackPurges) records.filter { it.deletedAt != null } else emptyList()
                db.write(emptyList(), records.map { it.id }, purged = tombstones)
                var s = _snapshot.value
                for (r in records) s = s.purge(r)
                _snapshot.value = s
            }
        }

    /** 用备份整体替换数据（会清空历史）。 */
    suspend fun replaceAll(snapshot: Snapshot) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                db.clearAll()
                db.write(snapshot.allRecords(), emptyList())
                _snapshot.value = snapshot
                _history.value = emptyList()
            }
        }

    companion object {
        const val TRASH_DAYS = 30
    }
}
