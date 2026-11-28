package app.goke.droprelay

import app.goke.core.campus.DropRelayIndex
import app.goke.core.campus.DropRelayLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class DropRelayErrorKind {
    INVALID_URL,
    UNREACHABLE,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER_BUSY,
    BAD_RESPONSE,
}

class DropRelayException(
    val kind: DropRelayErrorKind,
    val status: Int = 0,
    message: String = kind.name,
    cause: Throwable? = null,
) : Exception(message, cause)

sealed interface IndexFetchResult {
    data object NotModified : IndexFetchResult

    data class Updated(
        val index: DropRelayIndex,
        val rawJson: String,
        val etag: String?,
        val lastModified: String?,
    ) : IndexFetchResult
}

/** DropRelay 极简 HTTP 客户端：仅依赖 HttpURLConnection + kotlinx.serialization。 */
class DropRelayClient {
    suspend fun fetchIndex(
        shareBaseUrl: String,
        etag: String? = null,
        lastModified: String? = null,
    ): IndexFetchResult =
        withContext(Dispatchers.IO) {
            val base =
                DropRelayLogic.normalizeBaseUrl(shareBaseUrl)
                    ?: throw DropRelayException(DropRelayErrorKind.INVALID_URL, message = "Invalid URL: $shareBaseUrl")
            val primary = requestGet("$base/api/index.json", etag = etag, lastModified = lastModified)
            val resp =
                if (primary.status == 404) {
                    requestGet("$base/app/manifest.json", etag = etag, lastModified = lastModified)
                } else {
                    primary
                }
            if (resp.status == 304) return@withContext IndexFetchResult.NotModified
            if (resp.status !in 200..299) {
                throw mapHttpError(resp.status)
            }
            val parsed =
                runCatching { DropRelayLogic.parseIndexJson(resp.body) }
                    .getOrElse {
                        throw DropRelayException(
                            DropRelayErrorKind.BAD_RESPONSE,
                            resp.status,
                            "Parse error: ${it.message ?: it.javaClass.simpleName}",
                            it,
                        )
                    }
            val normalizedJson = DropRelayLogic.json.encodeToString(DropRelayIndex.serializer(), parsed)
            IndexFetchResult.Updated(
                index = parsed,
                rawJson = normalizedJson,
                etag = resp.etag,
                lastModified = resp.lastModified,
            )
        }

    private fun mapHttpError(status: Int): DropRelayException =
        when (status) {
            404 -> DropRelayException(DropRelayErrorKind.NOT_FOUND, status, "HTTP 404")
            429 -> DropRelayException(DropRelayErrorKind.RATE_LIMITED, status, "HTTP 429")
            503 -> DropRelayException(DropRelayErrorKind.SERVER_BUSY, status, "HTTP 503")
            else -> DropRelayException(DropRelayErrorKind.BAD_RESPONSE, status, "HTTP $status")
        }

    private data class HttpResp(
        val status: Int,
        val body: String,
        val etag: String?,
        val lastModified: String?,
    )

    private fun requestGet(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): HttpResp {
        val conn =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw DropRelayException(DropRelayErrorKind.UNREACHABLE, message = describeIoError(e), cause = e)
            }
        try {
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (!etag.isNullOrBlank()) conn.setRequestProperty("If-None-Match", etag)
            if (!lastModified.isNullOrBlank()) conn.setRequestProperty("If-Modified-Since", lastModified)
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResp(
                status = status,
                body = body,
                etag = conn.getHeaderField("ETag")?.trim()?.takeIf { it.isNotEmpty() },
                lastModified = conn.getHeaderField("Last-Modified")?.trim()?.takeIf { it.isNotEmpty() },
            )
        } catch (e: DropRelayException) {
            throw e
        } catch (e: IOException) {
            throw DropRelayException(DropRelayErrorKind.UNREACHABLE, message = describeIoError(e), cause = e)
        } finally {
            conn.disconnect()
        }
    }

    private fun describeIoError(e: IOException): String =
        when (e) {
            is UnknownHostException -> "DNS: ${e.message.orEmpty().ifBlank { "unknown host" }}"
            is SocketTimeoutException -> "Timeout: ${e.message.orEmpty().ifBlank { "timed out" }}"
            is SSLException -> "TLS: ${e.message.orEmpty().ifBlank { e.javaClass.simpleName }}"
            is ConnectException -> "Connect: ${e.message.orEmpty().ifBlank { "refused" }}"
            else -> "${e.javaClass.simpleName}: ${e.message.orEmpty().ifBlank { "io error" }}"
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Goke-Android/DropRelay"
    }
}
