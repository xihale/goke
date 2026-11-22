package app.goke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.goke.core.model.Record
import app.goke.core.util.MintJson

/** 操作历史一行：保存完整变更集 JSON，便于撤销。 */
data class OpRow(
    val id: String,
    val createdAt: Long,
    val description: String,
    val source: String,
    val json: String,
    val undoneAt: Long?,
)

/**
 * 本地存储：所有实体存成一张「records」表（JSON + 元数据），与 core 的快照模型一一对应，
 * 另有「oplog」保存每次变更集。见 ADR-0002。
 */
class MintDb(
    context: Context,
    name: String = "goke.db",
) : SQLiteOpenHelper(context, name, null, 2) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, kind TEXT NOT NULL, json TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)")
        db.execSQL("CREATE TABLE oplog (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, description TEXT NOT NULL, source TEXT NOT NULL, json TEXT NOT NULL, undone_at INTEGER)")
        db.execSQL("CREATE INDEX idx_oplog_created ON oplog(created_at)")
        createPurged(db)
    }

    /** 登录同步服务器后，被物理删除（例如撤销「新增」）但还没告诉服务器的记录，以删除墓碑的形式暂存。 */
    private fun createPurged(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS purged (id TEXT PRIMARY KEY, json TEXT NOT NULL)")
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) createPurged(db)
    }

    fun loadRecords(): List<Record> {
        val out = ArrayList<Record>()
        readableDatabase.rawQuery("SELECT json FROM records", null).use { c ->
            while (c.moveToNext()) {
                runCatching { MintJson.decodeFromString(Record.serializer(), c.getString(0)) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    /** 事务内：写入记录、删除记录、追加操作历史。 */
    fun write(
        upserts: Collection<Record>,
        deletes: Collection<String>,
        op: OpRow? = null,
        markUndone: Pair<String, Long?>? = null,
        purged: Collection<Record> = emptyList(),
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in upserts) {
                val v =
                    ContentValues().apply {
                        put("id", r.id)
                        put("kind", r::class.simpleName ?: "")
                        put("json", MintJson.encodeToString(Record.serializer(), r))
                        put("updated_at", r.updatedAt)
                        if (r.deletedAt != null) put("deleted_at", r.deletedAt) else putNull("deleted_at")
                    }
                db.insertWithOnConflict("records", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (id in deletes) db.delete("records", "id = ?", arrayOf(id))
            if (op != null) {
                val v =
                    ContentValues().apply {
                        put("id", op.id)
                        put("created_at", op.createdAt)
                        put("description", op.description)
                        put("source", op.source)
                        put("json", op.json)
                        if (op.undoneAt != null) put("undone_at", op.undoneAt) else putNull("undone_at")
                    }
                db.insertWithOnConflict("oplog", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (r in purged) {
                val v =
                    ContentValues().apply {
                        put("id", r.id)
                        put("json", MintJson.encodeToString(Record.serializer(), r))
                    }
                db.insertWithOnConflict("purged", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (markUndone != null) {
                db.execSQL("UPDATE oplog SET undone_at = ? WHERE id = ?", arrayOf<Any?>(markUndone.second, markUndone.first))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun ops(limit: Int = 100): List<OpRow> {
        val out = ArrayList<OpRow>()
        readableDatabase
            .rawQuery(
                "SELECT id, created_at, description, source, json, undone_at FROM oplog ORDER BY created_at DESC LIMIT ?",
                arrayOf(limit.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    out += OpRow(c.getString(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), if (c.isNull(5)) null else c.getLong(5))
                }
            }
        return out
    }

    fun loadPurged(): List<Record> {
        val out = ArrayList<Record>()
        readableDatabase.rawQuery("SELECT json FROM purged", null).use { c ->
            while (c.moveToNext()) {
                runCatching { MintJson.decodeFromString(Record.serializer(), c.getString(0)) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    fun clearPurged(ids: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) db.delete("purged", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("records", null, null)
            db.delete("oplog", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 只保留最近 [keep] 条操作历史，防止无限增长。 */
    fun trimOps(keep: Int = 200) {
        writableDatabase.execSQL(
            "DELETE FROM oplog WHERE id NOT IN (SELECT id FROM oplog ORDER BY created_at DESC LIMIT ?)",
            arrayOf(keep),
        )
    }
}
