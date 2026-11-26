package app.goke.share

import app.goke.core.export.SharedCourse
import app.goke.core.export.SharedCourseException
import app.goke.core.export.SharedSemester
import app.goke.sync.SyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 课表快速分享相关错误。[status] 为 HTTP 状态码，网络错误时为 0。 */
class ShareException(
    message: String,
    val status: Int = 0,
) : Exception(message)

@Serializable
data class SharedTimetableBody(
    val semester: SharedSemester,
    val courses: List<SharedCourse>,
    val exceptions: List<SharedCourseException> = emptyList(),
)

@Serializable
data class PutShareRequest(
    val studentId: String,
    val manageToken: String? = null,
    val name: String,
    val expiresAt: Long,
    val timetable: SharedTimetableBody,
)

@Serializable
data class PutShareResponse(
    val code: String,
    val shareUrl: String,
    val manageToken: String? = null,
    val name: String,
    val updatedAt: Long,
    val expiresAt: Long,
    val experimental: Boolean = true,
)

@Serializable
data class ShareStatusRequest(
    val studentId: String,
    val manageToken: String,
)

@Serializable
data class ShareStatusResponse(
    val exists: Boolean = false,
    val code: String? = null,
    val shareUrl: String? = null,
    val name: String? = null,
    val semesterName: String? = null,
    val updatedAt: Long? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class DeleteShareRequest(
    val studentId: String,
    val manageToken: String,
)

@Serializable
data class DeleteShareResponse(
    val deleted: Boolean = false,
)

@Serializable
private data class ShareErrorBody(
    val error: String = "",
    val message: String = "",
)

/** 课表快速分享 HTTP 客户端：只依赖 HttpURLConnection + kotlinx.serialization。 */
class TimetableShareClient(
    baseUrl: String,
) {
    private val base = SyncClient.normalizeUrl(baseUrl) ?: throw ShareException("服务器地址不正确")
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun putShare(req: PutShareRequest): PutShareResponse =
        call("PUT", "/share", json.encodeToString(PutShareRequest.serializer(), req))
            ?.let { json.decodeFromString(PutShareResponse.serializer(), it) }
            ?: fail()

    suspend fun status(req: ShareStatusRequest): ShareStatusResponse =
        call("POST", "/share/status", json.encodeToString(ShareStatusRequest.serializer(), req))
            ?.let { json.decodeFromString(ShareStatusResponse.serializer(), it) }
            ?: fail()

    suspend fun delete(req: DeleteShareRequest): DeleteShareResponse =
        call("DELETE", "/share", json.encodeToString(DeleteShareRequest.serializer(), req))
            ?.let { json.decodeFromString(DeleteShareResponse.serializer(), it) }
            ?: fail()

    private fun fail(): Nothing = throw ShareException("服务器返回了空响应")

    private suspend fun call(
        method: String,
        path: String,
        body: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            val conn = URL("$base/api/v1$path").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { json.decodeFromString(ShareErrorBody.serializer(), text).message }.getOrNull().orEmpty()
                    throw ShareException(msg.ifBlank { "服务器返回 $code" }, code)
                }
                text.ifBlank { null }
            } catch (e: ShareException) {
                throw e
            } catch (e: IOException) {
                throw ShareException("无法连接到服务器：${e.message ?: e.javaClass.simpleName}")
            } finally {
                conn.disconnect()
            }
        }
}
