package app.mintcal.core.export

import app.mintcal.core.model.Snapshot
import app.mintcal.core.util.MintJson
import kotlinx.serialization.Serializable

/** JSON 导出 / 备份格式。 */
@Serializable
data class ExportBundle(
    val app: String = "mintcal",
    val version: Int = 1,
    val exportedAt: Long,
    val snapshot: Snapshot,
) {
    fun toJson(pretty: Boolean = true): String =
        kotlinx.serialization.json.Json(MintJson) { prettyPrint = pretty }.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): ExportBundle = MintJson.decodeFromString(serializer(), text)
    }
}
