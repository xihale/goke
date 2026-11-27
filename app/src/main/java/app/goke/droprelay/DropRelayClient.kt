package app.goke.droprelay

import app.goke.core.campus.DropCodePreview
import app.goke.core.campus.DropCodeResponse
import app.goke.core.campus.DropRelayHealth
import app.goke.core.campus.DropRelayIndex
import app.goke.core.campus.DropRelayLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
) : Exception(message)

sealed interface IndexFetchResult {
    data object NotModified : IndexFetchResult

    data class Updated(
        val index: DropRelayIndex,
        val rawJson: String,
        val etag: String?,
        val lastModified: String?,
    ) : IndexFetchResult
}

/** DropRelay 极简 HTTP 客户端：仅依赖 HttpURLConnection + kotlinx.serialization，校园网短超时。 */
class DropRelayClient {
    suspend fun fetchIndex(
        shareBaseUrl: String,
        etag: String? = null,
        lastModified: String? = null,
    ): IndexFetchResult =
        withContext(Dispatchers.IO) {
            val base = DropRelayLogic.normalizeBaseUrl(shareBaseUrl) ?: throw DropRelayException(DropRelayErrorKind.INVALID_URL)
            val primary = requestGet("$base/api/index.json", etag = etag, lastModified = lastModified, connectMs = 4_000, readMs = 10_000)
            val resp =
                if (primary.status == 404) {
                    requestGet("$base/app/manifest.json", etag = etag, lastModified = lastModified, connectMs = 4_000, readMs = 10_000)
                } else {
                    primary
                }
            if (resp.status == 304) return@withContext IndexFetchResult.NotModified
            if (resp.status !in 200..299) {
                throw mapHttpError(resp.status)
            }
            val parsed =
                runCatching { DropRelayLogic.parseIndexJson(resp.body) }
                    .getOrElse { throw DropRelayException(DropRelayErrorKind.BAD_RESPONSE, resp.status, it.message ?: "parse error") }
            val normalizedJson = DropRelayLogic.json.encodeToString(DropRelayIndex.serializer(), parsed)
            IndexFetchResult.Updated(
                index = parsed,
                rawJson = normalizedJson,
                etag = resp.etag,
                lastModified = resp.lastModified,
            )
        }

    suspend fun fetchHealth(baseUrl: String): DropRelayHealth =
        withContext(Dispatchers.IO) {
            val base = DropRelayLogic.normalizeBaseUrl(baseUrl) ?: throw DropRelayException(DropRelayErrorKind.INVALID_URL)
            val resp = requestGet("$base/api/health", connectMs = 3_500, readMs = 4_000)
            if (resp.status !in 200..299) throw mapHttpError(resp.status)
            runCatching { DropRelayLogic.json.decodeFromString(DropRelayHealth.serializer(), resp.body) }
                .getOrElse { throw DropRelayException(DropRelayErrorKind.BAD_RESPONSE, resp.status, it.message ?: "parse error") }
        }

    suspend fun peekDropCode(
        dropBaseUrl: String,
        code: String,
    ): DropCodePreview =
        withContext(Dispatchers.IO) {
            val base = DropRelayLogic.normalizeBaseUrl(dropBaseUrl) ?: throw DropRelayException(DropRelayErrorKind.INVALID_URL)
            val normalizedCode = code.trim().uppercase()
            val encoded = URLEncoder.encode(normalizedCode, "UTF-8")
            val resp = requestGet("$base/api/code?c=$encoded", connectMs = 4_000, readMs = 6_000)
            if (resp.status !in 200..299) throw mapHttpError(resp.status)
            val parsed =
                runCatching { DropRelayLogic.json.decodeFromString(DropCodeResponse.serializer(), resp.body) }
                    .getOrElse { throw DropRelayException(DropRelayErrorKind.BAD_RESPONSE, resp.status, it.message ?: "parse error") }
            DropCodePreview(code = normalizedCode, files = parsed.files)
        }

    private fun mapHttpError(status: Int): DropRelayException =
        when (status) {
            404 -> DropRelayException(DropRelayErrorKind.NOT_FOUND, status)
            429 -> DropRelayException(DropRelayErrorKind.RATE_LIMITED, status)
            503 -> DropRelayException(DropRelayErrorKind.SERVER_BUSY, status)
            else -> DropRelayException(DropRelayErrorKind.BAD_RESPONSE, status)
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
        connectMs: Int,
        readMs: Int,
    ): HttpResp {
        val conn =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw DropRelayException(DropRelayErrorKind.UNREACHABLE, message = e.message ?: "connect failed")
            }
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.setRequestProperty("Accept", "application/json")
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
            throw DropRelayException(DropRelayErrorKind.UNREACHABLE, message = e.message ?: "io error")
        } finally {
            conn.disconnect()
        }
    }
}
