package app.mintcal.core.sync

import app.mintcal.core.model.Record
import app.mintcal.core.model.Snapshot
import app.mintcal.core.util.MintJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** 与服务端交换的一条记录（见 server/openapi.yaml 的 Record）。 */
@Serializable
data class WireRecord(
    val id: String,
    val kind: String,
    val data: JsonObject,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val seq: Long = 0,
)

object SyncCodec {
    fun encode(r: Record): WireRecord =
        WireRecord(
            id = r.id,
            kind = r::class.simpleName ?: "",
            data = MintJson.parseToJsonElement(MintJson.encodeToString(Record.serializer(), r)).jsonObject,
            updatedAt = r.updatedAt,
            deletedAt = r.deletedAt,
        )

    /** 解码失败（例如来自更新版本 App 的未知类型）返回 null，调用方应跳过。 */
    fun decode(w: WireRecord): Record? = runCatching { MintJson.decodeFromJsonElement(Record.serializer(), w.data) }.getOrNull()
}

/** 同步的纯逻辑：推哪些、收哪些、首次登录如何避免产生重复的默认学期。 */
object SyncPlanner {
    /** 本地在 [watermark] 之后改动过的记录（含已删除的墓碑），按时间升序。 */
    fun pending(
        snapshot: Snapshot,
        watermark: Long,
    ): List<Record> = snapshot.allRecords().filter { it.updatedAt > watermark }.sortedBy { it.updatedAt }

    /** 远程记录中应当写入本地的：本地没有，或远程 updatedAt 更新（相同时保留本地）。 */
    fun incoming(
        snapshot: Snapshot,
        remote: List<Record>,
    ): List<Record> {
        val local = snapshot.allRecords().associateBy { it.id }
        return remote.filter { r ->
            val l = local[r.id]
            l == null || r.updatedAt > l.updatedAt
        }
    }

    /**
     * 本地是否「还没有用户数据」：没有课程、事件、待办、清单，且最多只有一个（自动创建的）学期。
     * 首次登录到已有数据的服务器时，用它判断能否丢弃本地的默认学期，避免出现两个学期。
     */
    fun isPristine(snapshot: Snapshot): Boolean {
        val s = snapshot.live()
        return s.courses.isEmpty() &&
            s.exceptions.isEmpty() &&
            s.events.isEmpty() &&
            s.tasks.isEmpty() &&
            s.lists.isEmpty() &&
            s.semesters.size <= 1
    }
}
