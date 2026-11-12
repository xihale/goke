package app.mintcal.mcp

import app.mintcal.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest

/**
 * 轻量无状态 MCP Streamable HTTP 服务端（JSON-RPC 2.0 over HTTP/1.1）。
 * 默认仅绑定 `127.0.0.1` 供本机 AI Agent 连接，开启局域网开关后绑定 `0.0.0.0`，全程强制 Bearer 令牌鉴权。
 */
internal class LocalMcpServer(
    private val tools: LocalMcpTools,
    private val host: String = "127.0.0.1",
    private val port: Int = McpPrefs.DEFAULT_PORT,
    private val tokenProvider: () -> String,
    private val onRequest: () -> Unit = {},
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    val boundPort: Int
        get() = serverSocket?.localPort ?: port

    val isRunning: Boolean
        get() = serverSocket?.let { !it.isClosed } == true

    fun start() {
        if (isRunning) return
        val socket =
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(host), port), 32)
            }
        serverSocket = socket
        acceptJob =
            serverScope.launch {
                while (isActive && !socket.isClosed) {
                    val client =
                        try {
                            socket.accept()
                        } catch (_: SocketException) {
                            break
                        } catch (_: Throwable) {
                            continue
                        }
                    launch { handleClient(client) }
                }
            }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        serverScope.cancel()
    }

    private suspend fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 15_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()
            val req =
                try {
                    readHttpRequest(input, output)
                } catch (_: PayloadTooLargeException) {
                    writeResponse(output, 413, "Payload Too Large", contentType = "text/plain; charset=utf-8", body = "Payload Too Large".toByteArray())
                    return
                } catch (_: Throwable) {
                    return
                } ?: return

            val path = req.path.substringBefore('?').ifEmpty { "/" }

            if (req.method == "OPTIONS") {
                writeResponse(output, 204, "No Content", extraHeaders = corsHeaders(), body = ByteArray(0))
                return
            }

            if (req.method == "GET" && path == "/health") {
                val health =
                    buildJsonObject {
                        put("ok", true)
                        put("name", SERVER_NAME)
                        put("version", BuildConfig.VERSION_NAME)
                    }
                writeJson(output, 200, "OK", health)
                return
            }

            if (path != "/mcp" && path != "/") {
                writeResponse(output, 404, "Not Found", contentType = "text/plain; charset=utf-8", body = "Not Found".toByteArray())
                return
            }

            if (!authorized(req.headers["authorization"], tokenProvider())) {
                writeResponse(
                    output,
                    401,
                    "Unauthorized",
                    extraHeaders = corsHeaders() + ("WWW-Authenticate" to "Bearer realm=\"mintgdut-mcp\""),
                    contentType = "application/json; charset=utf-8",
                    body = """{"error":"unauthorized"}""".toByteArray(),
                )
                return
            }

            // Streamable HTTP 规范：无状态 JSON 服务端不支持服务端主动 SSE 流时，GET /mcp 返回 405；DELETE /mcp 返回 200。
            if (req.method == "GET") {
                writeResponse(
                    output,
                    405,
                    "Method Not Allowed",
                    extraHeaders = corsHeaders() + ("Allow" to "POST, DELETE, OPTIONS"),
                    body = ByteArray(0),
                )
                return
            }
            if (req.method == "DELETE") {
                writeResponse(output, 200, "OK", extraHeaders = corsHeaders(), body = ByteArray(0))
                return
            }
            if (req.method != "POST") {
                writeResponse(
                    output,
                    405,
                    "Method Not Allowed",
                    extraHeaders = corsHeaders() + ("Allow" to "POST, DELETE, OPTIONS"),
                    body = ByteArray(0),
                )
                return
            }

            onRequest()

            val parsed =
                try {
                    json.parseToJsonElement(req.body.toString(Charsets.UTF_8))
                } catch (e: Throwable) {
                    val err = rpcError(JsonNull, -32700, "Parse error: ${e.message}")
                    writeJson(output, 200, "OK", err)
                    return
                }

            when (parsed) {
                is JsonObject -> {
                    val resp = dispatchMessage(parsed)
                    if (resp == null) {
                        writeResponse(output, 202, "Accepted", extraHeaders = corsHeaders(), body = ByteArray(0))
                    } else {
                        writeJson(output, 200, "OK", resp)
                    }
                }

                is JsonArray -> {
                    if (parsed.isEmpty()) {
                        writeJson(output, 200, "OK", rpcError(JsonNull, -32600, "Invalid Request: empty batch"))
                        return
                    }
                    val responses = mutableListOf<JsonObject>()
                    for (item in parsed) {
                        val obj = item as? JsonObject
                        if (obj == null) {
                            responses += rpcError(JsonNull, -32600, "Invalid Request")
                        } else {
                            dispatchMessage(obj)?.let { responses += it }
                        }
                    }
                    if (responses.isEmpty()) {
                        writeResponse(output, 202, "Accepted", extraHeaders = corsHeaders(), body = ByteArray(0))
                    } else {
                        writeJson(output, 200, "OK", JsonArray(responses))
                    }
                }

                else -> {
                    writeJson(output, 200, "OK", rpcError(JsonNull, -32600, "Invalid Request"))
                }
            }
        }
    }

    private suspend fun dispatchMessage(msg: JsonObject): JsonObject? {
        val hasId = "id" in msg && msg["id"] !is JsonNull
        val id = msg["id"] ?: JsonNull
        val method = (msg["method"] as? JsonPrimitive)?.contentOrNull
        if (method.isNullOrBlank()) {
            return if (hasId) rpcError(id, -32600, "Invalid Request: missing method") else null
        }
        // JSON-RPC 通知（无 id，如 notifications/initialized）不产生响应体
        if (!hasId || method.startsWith("notifications/")) {
            return null
        }
        val params = (msg["params"] as? JsonObject) ?: JsonObject(emptyMap())
        return try {
            val result = handleRpcMethod(method, params)
            rpcResult(id, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcMethodException) {
            rpcError(id, e.code, e.message)
        } catch (e: Throwable) {
            rpcError(id, -32603, e.message ?: "Internal error")
        }
    }

    private suspend fun handleRpcMethod(
        method: String,
        params: JsonObject,
    ): JsonElement =
        when (method) {
            "initialize" -> {
                val clientVer = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
                val negotiated = if (clientVer in SUPPORTED_VERSIONS) clientVer!! else DEFAULT_PROTOCOL_VERSION
                buildJsonObject {
                    put("protocolVersion", negotiated)
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {
                            put("listChanged", false)
                        }
                    }
                    putJsonObject("serverInfo") {
                        put("name", SERVER_NAME)
                        put("version", BuildConfig.VERSION_NAME)
                    }
                    put(
                        "instructions",
                        "薄荷广工本地 MCP 服务：可直接查询与修改手机上的课表、日程时间块与待办事项。" +
                            "需要执行批量调课、停课、作息调整或复杂筛选修改时，请先调用 get_script_sdk 查看 QuickJS 沙箱 API，再通过 preview_script 预览变更与冲突检查，确认后用 apply_script 提交。所有修改均记录在操作历史中，支持随时撤销。",
                    )
                }
            }

            "ping" -> {
                buildJsonObject {}
            }

            "tools/list" -> {
                buildJsonObject {
                    putJsonArray("tools") {
                        for (t in tools.definitions) {
                            add(
                                buildJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("inputSchema", t.inputSchema)
                                },
                            )
                        }
                    }
                }
            }

            "tools/call" -> {
                val toolName =
                    (params["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: throw RpcMethodException(-32602, "Missing tool name")
                val args = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                try {
                    val out = tools.call(toolName, args)
                    buildJsonObject {
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", json.encodeToString(JsonElement.serializer(), out))
                                },
                            )
                        }
                        put("isError", false)
                    }
                } catch (e: McpToolError) {
                    buildJsonObject {
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", e.message)
                                },
                            )
                        }
                        put("isError", true)
                    }
                }
            }

            else -> {
                throw RpcMethodException(-32601, "Method not found: $method")
            }
        }

    private fun authorized(
        header: String?,
        expectedToken: String,
    ): Boolean {
        if (header.isNullOrBlank() || expectedToken.isBlank()) return false
        val prefix = "Bearer "
        if (!header.startsWith(prefix, ignoreCase = true)) return false
        val provided = header.substring(prefix.length).trim()
        return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expectedToken.toByteArray(Charsets.UTF_8))
    }

    private fun rpcResult(
        id: JsonElement,
        result: JsonElement,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun rpcError(
        id: JsonElement,
        code: Int,
        message: String,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

    private fun writeJson(
        out: OutputStream,
        status: Int,
        reason: String,
        payload: JsonElement,
    ) {
        val bytes = json.encodeToString(JsonElement.serializer(), payload).toByteArray(Charsets.UTF_8)
        writeResponse(
            out = out,
            status = status,
            reason = reason,
            extraHeaders = corsHeaders(),
            contentType = "application/json; charset=utf-8",
            body = bytes,
        )
    }

    private fun writeResponse(
        out: OutputStream,
        status: Int,
        reason: String,
        extraHeaders: List<Pair<String, String>> = emptyList(),
        contentType: String? = null,
        body: ByteArray,
    ) {
        val sb = StringBuilder()
        sb
            .append("HTTP/1.1 ")
            .append(status)
            .append(' ')
            .append(reason)
            .append("\r\n")
        if (contentType != null) {
            sb.append("Content-Type: ").append(contentType).append("\r\n")
        }
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in extraHeaders) {
            sb
                .append(k)
                .append(": ")
                .append(v)
                .append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun corsHeaders(): List<Pair<String, String>> =
        listOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "POST, GET, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Authorization, Content-Type, Accept, Mcp-Session-Id, MCP-Protocol-Version",
        )

    private class RpcMethodException(
        val code: Int,
        override val message: String,
    ) : RuntimeException(message)

    private class PayloadTooLargeException : RuntimeException()

    private class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private fun readHttpRequest(
        input: InputStream,
        output: OutputStream,
    ): HttpRequest? {
        val requestLine = readAsciiLine(input, MAX_LINE_BYTES) ?: return null
        if (requestLine.isEmpty()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val path = parts[1]

        val headers = LinkedHashMap<String, String>()
        var headerCount = 0
        while (true) {
            val line = readAsciiLine(input, MAX_LINE_BYTES) ?: throw EOFException("Unexpected EOF in headers")
            if (line.isEmpty()) break
            if (++headerCount > 64) throw PayloadTooLargeException()
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers[key] = value
            }
        }

        if (headers["expect"]?.equals("100-continue", ignoreCase = true) == true) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        val transferEncoding = headers["transfer-encoding"]?.lowercase().orEmpty()
        val body =
            when {
                transferEncoding.contains("chunked") -> {
                    readChunkedBody(input)
                }

                headers.containsKey("content-length") -> {
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    if (len < 0 || len > MAX_BODY_BYTES) throw PayloadTooLargeException()
                    readExact(input, len)
                }

                else -> {
                    ByteArray(0)
                }
            }
        return HttpRequest(method, path, headers, body)
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input, MAX_LINE_BYTES) ?: throw EOFException("Unexpected EOF in chunk size")
            val hex = sizeLine.substringBefore(';').trim()
            if (hex.isEmpty()) continue
            val chunkSize = hex.toIntOrNull(16) ?: throw EOFException("Invalid chunk size")
            if (chunkSize == 0) {
                // 消费尾部空行或 trailer headers
                while (true) {
                    val trailer = readAsciiLine(input, MAX_LINE_BYTES) ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            if (out.size() + chunkSize > MAX_BODY_BYTES) throw PayloadTooLargeException()
            out.write(readExact(input, chunkSize))
            readAsciiLine(input, MAX_LINE_BYTES)
        }
        return out.toByteArray()
    }

    private fun readExact(
        input: InputStream,
        len: Int,
    ): ByteArray {
        if (len == 0) return ByteArray(0)
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw EOFException("Unexpected EOF in body")
            off += n
        }
        return buf
    }

    private fun readAsciiLine(
        input: InputStream,
        maxBytes: Int,
    ): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (buf.size() == 0) null else buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            if (b == '\n'.code) {
                return buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            if (buf.size() >= maxBytes) throw PayloadTooLargeException()
            buf.write(b)
        }
    }

    companion object {
        private const val SERVER_NAME = "mintgdut-local"
        private const val DEFAULT_PROTOCOL_VERSION = "2025-03-26"
        private val SUPPORTED_VERSIONS = setOf("2025-06-18", "2025-03-26", "2024-11-05")
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }
}
