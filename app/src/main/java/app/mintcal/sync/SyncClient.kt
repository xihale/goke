package app.mintcal.sync

import app.mintcal.core.sync.WireRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 同步相关错误。[status] 为 HTTP 状态码，网络错误时为 0。 */
class SyncException(
    message: String,
    val status: Int = 0,
) : Exception(message) {
    val unauthorized: Boolean get() = status == 401
}

@Serializable
data class SyncUser(
    val id: Long = 0,
    val username: String = "",
    val role: String = "user",
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: SyncUser,
)

@Serializable
data class MeResponse(
    val id: Long = 0,
    val username: String = "",
    val role: String = "user",
    val cursor: Long = 0,
)

@Serializable
data class PullResponse(
    val records: List<WireRecord> = emptyList(),
    val cursor: Long = 0,
    val more: Boolean = false,
)

@Serializable
data class PushResponse(
    val accepted: List<String> = emptyList(),
    val rejected: List<WireRecord> = emptyList(),
    val cursor: Long = 0,
)

@Serializable
private data class ErrorBody(
    val error: String = "",
    val message: String = "",
)

@Serializable
private data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
)

@Serializable
private data class PushRequest(
    val records: List<WireRecord>,
)

@Serializable
private data class PasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

/** 自建同步服务（server/openapi.yaml）的 HTTP 客户端。只依赖 HttpURLConnection，不引入网络库。 */
class SyncClient(
    baseUrl: String,
    private val token: String? = null,
) {
    private val base = normalizeUrl(baseUrl) ?: throw SyncException("服务器地址不正确")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(
        username: String,
        password: String,
        deviceName: String,
    ): LoginResponse = call("POST", "/login", json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password, deviceName)))?.let { json.decodeFromString<LoginResponse>(it) } ?: fail()

    suspend fun logout() {
        call("POST", "/logout", null)
    }

    suspend fun me(): MeResponse = call("GET", "/me", null)?.let { json.decodeFromString<MeResponse>(it) } ?: fail()

    suspend fun changePassword(
        old: String,
        new: String,
    ) {
        call("POST", "/me/password", json.encodeToString(PasswordRequest.serializer(), PasswordRequest(old, new)))
    }

    suspend fun pull(
        since: Long,
        limit: Int = 500,
    ): PullResponse = call("GET", "/sync/pull?since=$since&limit=$limit", null)?.let { json.decodeFromString<PullResponse>(it) } ?: fail()

    suspend fun push(records: List<WireRecord>): PushResponse =
        call("POST", "/sync/push", json.encodeToString(PushRequest.serializer(), PushRequest(records)))?.let { json.decodeFromString<PushResponse>(it) } ?: fail()

    private fun fail(): Nothing = throw SyncException("服务器返回了空响应")

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
                conn.readTimeout = 60_000
                conn.setRequestProperty("Accept", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { json.decodeFromString(ErrorBody.serializer(), text).message }.getOrNull().orEmpty()
                    throw SyncException(msg.ifBlank { "服务器返回 $code" }, code)
                }
                text.ifBlank { null }
            } catch (e: SyncException) {
                throw e
            } catch (e: IOException) {
                throw SyncException("无法连接到服务器：${e.message ?: e.javaClass.simpleName}")
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        /**
         * 规范化服务器地址：补全协议（缺省 https）、去掉末尾斜杠和误填的 /admin。
         * 无法识别时返回 null。
         */
        fun normalizeUrl(input: String): String? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "https://$s"
            s = s.trimEnd('/')
            if (s.endsWith("/admin")) s = s.removeSuffix("/admin").trimEnd('/')
            val ok = runCatching { URL(s) }.getOrNull()?.let { (it.protocol == "http" || it.protocol == "https") && it.host.isNotEmpty() } ?: false
            return if (ok) s else null
        }

        fun isInsecure(url: String): Boolean = url.startsWith("http://")
    }
}
