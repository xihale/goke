package app.mintcal.core.export

import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.Event
import app.mintcal.core.model.Record
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.util.MintJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** JSON 导出 / 备份格式。 */
@Serializable
data class ExportBundle(
    val app: String = "mintcal",
    val version: Int = 1,
    val exportedAt: Long,
    val snapshot: Snapshot,
) {
    fun toJson(pretty: Boolean = true): String = (if (pretty) PRETTY else MintJson).encodeToString(serializer(), this)

    companion object {
        private val PRETTY = Json(MintJson) { prettyPrint = true }

        fun fromJson(text: String): ExportBundle {
            val root = MintJson.parseToJsonElement(text).jsonObject
            if ("snapshot" in root) {
                return MintJson.decodeFromJsonElement(serializer(), root)
            }
            if (root["app"]?.jsonPrimitive?.contentOrNull == "mintcal-web" && root["records"] is JsonArray) {
                return fromLegacyWebJson(root)
            }
            return MintJson.decodeFromJsonElement(serializer(), root)
        }

        private fun fromLegacyWebJson(root: JsonObject): ExportBundle {
            val exportedAt =
                root["exportedAt"]?.jsonPrimitive?.let { p ->
                    p.longOrNull ?: p.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                } ?: 0L
            val semesters = mutableListOf<Semester>()
            val courses = mutableListOf<Course>()
            val exceptions = mutableListOf<CourseException>()
            val events = mutableListOf<Event>()
            val tasks = mutableListOf<Task>()
            val lists = mutableListOf<TaskList>()
            for (el in root.getValue("records").jsonArray) {
                val rec = runCatching { MintJson.decodeFromJsonElement(Record.serializer(), el) }.getOrNull() ?: continue
                when (rec) {
                    is Semester -> semesters += rec
                    is Course -> courses += rec
                    is CourseException -> exceptions += rec
                    is Event -> events += rec
                    is Task -> tasks += rec
                    is TaskList -> lists += rec
                }
            }
            return ExportBundle(
                app = "mintcal-web",
                version = 1,
                exportedAt = exportedAt,
                snapshot = Snapshot(semesters, courses, exceptions, events, tasks, lists),
            )
        }
    }
}
