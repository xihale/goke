package app.goke.lanweb

import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Record
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.sync.SyncCodec
import app.goke.core.sync.WireRecord
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import app.goke.core.util.MintJson
import app.goke.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 局域网电脑端控制台 HTTP 服务端：基于 JDK [ServerSocket]，提供静态 Web 资源、
 * 6 位连接码登录、以及与云端服务端协议一致的增量同步接口（写入走 [Repository.commit]，支持手机端撤销）。
 */
internal class LanWebServer(
    private val repo: Repository,
    private val assetOpener: (String) -> InputStream?,
    private val host: String = "0.0.0.0",
    private val port: Int = DEFAULT_PORT,
    private val pinProvider: () -> String,
    private val onActivity: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val ids: IdGenerator = Ids,
) {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    private val sessionUserId: Long = nextSessionId(clock())
    private val random = SecureRandom()
    private val activeTokens = ConcurrentHashMap.newKeySet<String>()
    private val rateLimiter = PinRateLimiter(maxAttempts = MAX_PIN_FAILS, lockoutMs = LOCKOUT_MS)
    private val syncMutex = Mutex()
    private val seqTracker = SessionSeqTracker(clock)

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
        activeTokens.clear()
        serverScope.cancel()
    }

    private suspend fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = SOCKET_TIMEOUT_MS
            val clientIp = sock.inetAddress?.hostAddress ?: "unknown"
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()
            val req =
                try {
                    readHttpRequest(input, output)
                } catch (_: PayloadTooLargeException) {
                    writeApiError(output, 413, "Payload Too Large", "too_large", "请求体太大")
                    return
                } catch (_: Throwable) {
                    return
                } ?: return

            if (req.method == "OPTIONS") {
                writeResponse(output, 204, "No Content", extraHeaders = securityHeaders(), body = ByteArray(0))
                return
            }

            if (req.path.startsWith("/api/")) {
                handleApi(req, clientIp, output)
            } else {
                handleStatic(req, output)
            }
        }
    }

    private fun handleStatic(
        req: HttpRequest,
        output: OutputStream,
    ) {
        if (req.method != "GET" && req.method != "HEAD") {
            writeResponse(
                output,
                405,
                "Method Not Allowed",
                extraHeaders = securityHeaders() + ("Allow" to "GET, HEAD"),
                contentType = "text/plain; charset=utf-8",
                body = "Method Not Allowed".toByteArray(),
            )
            return
        }

        if (req.path != "/" && !req.path.startsWith("/app/")) {
            writeResponse(
                output,
                404,
                "Not Found",
                extraHeaders = securityHeaders(),
                contentType = "text/plain; charset=utf-8",
                body = "Not Found".toByteArray(),
            )
            return
        }

        val assetPath = sanitizeAppAssetPath(req.path)
        if (assetPath == null) {
            writeResponse(
                output,
                403,
                "Forbidden",
                extraHeaders = securityHeaders(),
                contentType = "text/plain; charset=utf-8",
                body = "Forbidden".toByteArray(),
            )
            return
        }

        val bytes =
            try {
                assetOpener(assetPath)?.use { it.readBytes() }
            } catch (_: IOException) {
                null
            }
        if (bytes == null) {
            writeResponse(
                output,
                404,
                "Not Found",
                extraHeaders = securityHeaders(),
                contentType = "text/plain; charset=utf-8",
                body = "Not Found".toByteArray(),
            )
            return
        }

        val headers = securityHeaders() + ("Cache-Control" to "no-cache")
        val mime = mimeTypeFor(assetPath)
        if (req.method == "HEAD") {
            writeHeadResponse(output, 200, "OK", headers, mime, bytes.size)
        } else {
            writeResponse(output, 200, "OK", headers, mime, bytes)
        }
    }

    private suspend fun handleApi(
        req: HttpRequest,
        clientIp: String,
        output: OutputStream,
    ) {
        when {
            req.method == "GET" && req.path == "/api/v1/mode" -> {
                val payload =
                    buildJsonObject {
                        put("mode", "lan")
                        put("auth", "pin")
                    }
                writeApiJson(output, 200, "OK", payload)
            }

            req.method == "POST" && req.path == "/api/v1/login" -> {
                handleLogin(req, clientIp, output)
            }

            req.method == "POST" && req.path == "/api/v1/logout" -> {
                val token = requireAuth(req, output) ?: return
                onActivity()
                activeTokens.remove(token)
                writeResponse(
                    output,
                    204,
                    "No Content",
                    extraHeaders = apiHeaders(),
                    body = ByteArray(0),
                )
            }

            req.method == "GET" && req.path == "/api/v1/me" -> {
                requireAuth(req, output) ?: return
                onActivity()
                val cursor =
                    syncMutex.withLock {
                        seqTracker.reconcile(repo.snapshot.value)
                        seqTracker.currentSeq
                    }
                val payload =
                    buildJsonObject {
                        put("id", sessionUserId)
                        put("username", LAN_USERNAME)
                        put("role", "user")
                        put("mode", "lan")
                        put("cursor", cursor)
                    }
                writeApiJson(output, 200, "OK", payload)
            }

            req.method == "POST" && req.path == "/api/v1/me/password" -> {
                requireAuth(req, output) ?: return
                onActivity()
                writeApiError(
                    output,
                    400,
                    "Bad Request",
                    "unsupported",
                    "局域网临时会话不支持修改密码",
                )
            }

            req.method == "GET" && req.path == "/api/v1/sync/pull" -> {
                requireAuth(req, output) ?: return
                onActivity()
                handlePull(req, output)
            }

            req.method == "POST" && req.path == "/api/v1/sync/push" -> {
                requireAuth(req, output) ?: return
                onActivity()
                handlePush(req, output)
            }

            else -> {
                writeApiError(output, 404, "Not Found", "not_found", "接口不存在")
            }
        }
    }

    private fun handleLogin(
        req: HttpRequest,
        clientIp: String,
        output: OutputStream,
    ) {
        val now = clock()
        if (rateLimiter.isBlocked(clientIp, now)) {
            writeApiError(
                output,
                429,
                "Too Many Requests",
                "too_many_attempts",
                "尝试次数过多，请 30 秒后再试",
                extraHeaders = listOf("Retry-After" to (LOCKOUT_MS / 1000L).toString()),
            )
            return
        }

        val body =
            runCatching {
                MintJson.decodeFromString(LoginRequest.serializer(), req.body.toString(Charsets.UTF_8))
            }.getOrNull()
        if (body == null) {
            writeApiError(output, 400, "Bad Request", "bad_request", "请求格式不正确")
            return
        }

        val expectedPin = pinProvider()
        val providedPin = body.password.trim()
        val pinMatch =
            expectedPin.isNotEmpty() &&
                MessageDigest.isEqual(
                    providedPin.toByteArray(Charsets.UTF_8),
                    expectedPin.toByteArray(Charsets.UTF_8),
                )
        if (!pinMatch) {
            rateLimiter.recordFailure(clientIp, now)
            writeApiError(output, 401, "Unauthorized", "invalid_credentials", "连接码不正确")
            return
        }

        rateLimiter.reset(clientIp)
        onActivity()
        val token = generateToken()
        activeTokens.add(token)

        val payload =
            buildJsonObject {
                put("token", token)
                putJsonObject("user") {
                    put("id", sessionUserId)
                    put("username", LAN_USERNAME)
                    put("role", "user")
                    put("mode", "lan")
                }
            }
        writeApiJson(output, 200, "OK", payload)
    }

    private suspend fun handlePull(
        req: HttpRequest,
        output: OutputStream,
    ) {
        val since = req.queryParams["since"]?.toLongOrNull() ?: 0L
        val rawLimit = req.queryParams["limit"]?.toIntOrNull()
        val limit =
            when {
                rawLimit == null || rawLimit <= 0 -> DEFAULT_PULL_LIMIT
                rawLimit > MAX_PULL_LIMIT -> MAX_PULL_LIMIT
                else -> rawLimit
            }

        val resp =
            syncMutex.withLock {
                seqTracker.reconcile(repo.snapshot.value)
                val matched = seqTracker.since(since)
                val page = matched.take(limit)
                val more = matched.size > limit
                val cursor = if (page.isEmpty()) since else page.last().seq
                val wireRecords = page.map { SyncCodec.encode(it.record).copy(seq = it.seq) }
                PullResponse(records = wireRecords, cursor = cursor, more = more)
            }
        val jsonBytes = MintJson.encodeToString(PullResponse.serializer(), resp).toByteArray(Charsets.UTF_8)
        writeResponse(output, 200, "OK", extraHeaders = apiHeaders(), contentType = JSON_CONTENT_TYPE, body = jsonBytes)
    }

    private suspend fun handlePush(
        req: HttpRequest,
        output: OutputStream,
    ) {
        val body =
            runCatching {
                MintJson.decodeFromString(PushRequest.serializer(), req.body.toString(Charsets.UTF_8))
            }.getOrNull()
        if (body == null) {
            writeApiError(output, 400, "Bad Request", "bad_request", "请求格式不正确")
            return
        }
        if (body.records.size > MAX_PUSH_BATCH) {
            writeApiError(output, 400, "Bad Request", "batch_too_large", "一次最多推送 1000 条记录")
            return
        }

        val decodedPairs = ArrayList<Pair<WireRecord, Record>>(body.records.size)
        for (w in body.records) {
            if (w.id.isBlank() || w.id.length > 64 || w.kind.isBlank() || w.kind.length > 32 || w.updatedAt <= 0) {
                writeApiError(output, 400, "Bad Request", "bad_record", "记录格式不正确：${w.id}")
                return
            }
            val decoded = SyncCodec.decode(w)?.withMeta(w.updatedAt, w.deletedAt)
            if (decoded == null || decoded.id != w.id) {
                writeApiError(output, 400, "Bad Request", "bad_record", "记录格式不正确：${w.id}")
                return
            }
            decodedPairs += w to decoded
        }

        val resp =
            syncMutex.withLock {
                seqTracker.reconcile(repo.snapshot.value)
                val currentById =
                    repo.snapshot.value
                        .allRecords()
                        .associateByTo(HashMap()) { it.id }

                val accepted = mutableListOf<String>()
                val rejected = mutableListOf<WireRecord>()
                val changes = mutableListOf<Change>()

                for ((wire, incoming) in decodedPairs) {
                    val local = currentById[incoming.id]
                    if (local != null && sameRecordState(local, incoming)) {
                        accepted += wire.id
                        continue
                    }
                    if (local != null && incoming.updatedAt <= local.updatedAt) {
                        val seq = seqTracker.seqOf(local.id) ?: seqTracker.currentSeq
                        rejected += SyncCodec.encode(local).copy(seq = seq)
                        continue
                    }
                    val change = buildChange(local, incoming)
                    if (change != null) {
                        changes += change
                        currentById[incoming.id] = incoming
                    }
                    accepted += wire.id
                }

                if (changes.isNotEmpty()) {
                    val cs =
                        ChangeSet(
                            id = ids.next(),
                            description = "电脑端编辑",
                            source = Source.MANUAL,
                            createdAt = clock(),
                            changes = changes,
                        )
                    repo.commit(cs)
                    seqTracker.reconcile(repo.snapshot.value)
                }

                PushResponse(
                    accepted = accepted,
                    rejected = rejected,
                    cursor = seqTracker.currentSeq,
                )
            }

        val jsonBytes = MintJson.encodeToString(PushResponse.serializer(), resp).toByteArray(Charsets.UTF_8)
        writeResponse(output, 200, "OK", extraHeaders = apiHeaders(), contentType = JSON_CONTENT_TYPE, body = jsonBytes)
    }

    private fun buildChange(
        local: Record?,
        incoming: Record,
    ): Change? =
        when {
            local == null && incoming.deletedAt == null -> {
                Change.Add(incoming)
            }

            local == null -> {
                Change.Remove(incoming)
            }

            local.deletedAt == null && incoming.deletedAt != null -> {
                Change.Remove(local)
            }

            local.withMeta(0, null) == incoming.withMeta(0, null) && local.deletedAt == incoming.deletedAt -> {
                null
            }

            else -> {
                Change.Modify(local, incoming)
            }
        }

    private fun sameRecordState(
        local: Record,
        incoming: Record,
    ): Boolean =
        local.updatedAt == incoming.updatedAt &&
            (local.deletedAt == null) == (incoming.deletedAt == null) &&
            local.withMeta(0, null) == incoming.withMeta(0, null)

    private fun requireAuth(
        req: HttpRequest,
        output: OutputStream,
    ): String? {
        val header = req.headers["authorization"]
        if (header.isNullOrBlank() || !header.startsWith("Bearer ", ignoreCase = true)) {
            writeApiError(output, 401, "Unauthorized", "unauthorized", "请先登录")
            return null
        }
        val token = header.substring("Bearer ".length).trim()
        if (token.isEmpty() || !activeTokens.contains(token)) {
            writeApiError(output, 401, "Unauthorized", "unauthorized", "登录已失效，请重新登录")
            return null
        }
        return token
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun writeApiJson(
        out: OutputStream,
        status: Int,
        reason: String,
        payload: JsonElement,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ) {
        val bytes = MintJson.encodeToString(JsonElement.serializer(), payload).toByteArray(Charsets.UTF_8)
        writeResponse(
            out = out,
            status = status,
            reason = reason,
            extraHeaders = apiHeaders() + extraHeaders,
            contentType = JSON_CONTENT_TYPE,
            body = bytes,
        )
    }

    private fun writeApiError(
        out: OutputStream,
        status: Int,
        reason: String,
        code: String,
        message: String,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ) {
        val payload =
            buildJsonObject {
                put("error", code)
                put("message", message)
            }
        writeApiJson(out, status, reason, payload, extraHeaders)
    }

    private fun apiHeaders(): List<Pair<String, String>> = securityHeaders() + ("Cache-Control" to "no-store")

    private fun securityHeaders(): List<Pair<String, String>> =
        listOf(
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
            "Referrer-Policy" to "no-referrer",
            "Content-Security-Policy" to CSP_POLICY,
        )

    private fun writeResponse(
        out: OutputStream,
        status: Int,
        reason: String,
        extraHeaders: List<Pair<String, String>> = emptyList(),
        contentType: String? = null,
        body: ByteArray,
    ) {
        writeHeaderBlock(out, status, reason, extraHeaders, contentType, body.size)
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun writeHeadResponse(
        out: OutputStream,
        status: Int,
        reason: String,
        extraHeaders: List<Pair<String, String>>,
        contentType: String,
        contentLength: Int,
    ) {
        writeHeaderBlock(out, status, reason, extraHeaders, contentType, contentLength)
        out.flush()
    }

    private fun writeHeaderBlock(
        out: OutputStream,
        status: Int,
        reason: String,
        extraHeaders: List<Pair<String, String>>,
        contentType: String?,
        contentLength: Int,
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
        sb.append("Content-Length: ").append(contentLength).append("\r\n")
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
    }

    private class PayloadTooLargeException : RuntimeException()

    private class HttpRequest(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
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
        val rawTarget = parts[1]
        val path = rawTarget.substringBefore('?').ifEmpty { "/" }
        val query = rawTarget.substringAfter('?', "")
        val queryParams = parseQueryParams(query)

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
        return HttpRequest(method, path, queryParams, headers, body)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val rawKey = if (eq >= 0) pair.substring(0, eq) else pair
            val rawVal = if (eq >= 0) pair.substring(eq + 1) else ""
            val key = runCatching { URLDecoder.decode(rawKey, Charsets.UTF_8.name()) }.getOrDefault(rawKey)
            val value = runCatching { URLDecoder.decode(rawVal, Charsets.UTF_8.name()) }.getOrDefault(rawVal)
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input, MAX_LINE_BYTES) ?: throw EOFException("Unexpected EOF in chunk size")
            val hex = sizeLine.substringBefore(';').trim()
            if (hex.isEmpty()) continue
            val chunkSize = hex.toIntOrNull(16) ?: throw EOFException("Invalid chunk size")
            if (chunkSize == 0) {
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

    @Serializable
    private data class LoginRequest(
        val username: String = "",
        val password: String = "",
        val deviceName: String = "",
    )

    @Serializable
    private data class PullResponse(
        val records: List<WireRecord>,
        val cursor: Long,
        val more: Boolean,
    )

    @Serializable
    private data class PushRequest(
        val records: List<WireRecord> = emptyList(),
    )

    @Serializable
    private data class PushResponse(
        val accepted: List<String>,
        val rejected: List<WireRecord>,
        val cursor: Long,
    )

    companion object {
        const val DEFAULT_PORT = 17531
        private const val LAN_USERNAME = "本机 (局域网)"
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_PIN_FAILS = 5
        private const val LOCKOUT_MS = 30_000L
        private const val DEFAULT_PULL_LIMIT = 500
        private const val MAX_PULL_LIMIT = 1000
        private const val MAX_PUSH_BATCH = 1000
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val CSP_POLICY =
            "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; " +
                "img-src 'self' data:; form-action 'self'; manifest-src 'self'; base-uri 'none'; frame-ancestors 'none'"

        private val lastSessionId = AtomicLong(0L)

        private fun nextSessionId(now: Long): Long = lastSessionId.updateAndGet { prev -> maxOf(now, prev + 1L) }

        internal fun sanitizeAppAssetPath(rawPath: String): String? {
            if (rawPath == "/") return "lanweb/app/index.html"
            if (!rawPath.startsWith("/app/")) return null
            val rawRel = rawPath.removePrefix("/app/")
            if (rawRel.isEmpty()) return null
            if ('\\' in rawRel || '\u0000' in rawRel) return null
            val decoded = decodePercentStrict(rawRel) ?: return null
            if ('\\' in decoded || '\u0000' in decoded) return null
            val segments = decoded.split('/')
            if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
            return "lanweb/app/" + segments.joinToString("/")
        }

        private fun decodePercentStrict(input: String): String? {
            if ('%' !in input) return input
            val out = ByteArrayOutputStream(input.length)
            var i = 0
            while (i < input.length) {
                val ch = input[i]
                if (ch == '%') {
                    if (i + 2 >= input.length) return null
                    val hi = Character.digit(input[i + 1], 16)
                    val lo = Character.digit(input[i + 2], 16)
                    if (hi < 0 || lo < 0) return null
                    out.write((hi shl 4) or lo)
                    i += 3
                } else {
                    val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                    out.write(bytes)
                    i++
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }

        internal fun mimeTypeFor(path: String): String =
            when (path.substringAfterLast('.', "").lowercase()) {
                "html" -> "text/html; charset=utf-8"
                "js", "mjs" -> "application/javascript; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                "png" -> "image/png"
                "webmanifest" -> "application/manifest+json; charset=utf-8"
                "json" -> "application/json; charset=utf-8"
                "ico" -> "image/x-icon"
                else -> "application/octet-stream"
            }
    }
}

/** 单会话单调递增 seq 追踪器：将 [Snapshot] 的实时状态变化映射为供 Web 端增量拉取的游标序列。 */
private class SessionSeqTracker(
    private val clock: () -> Long,
) {
    data class Entry(
        val seq: Long,
        val record: Record,
    )

    var currentSeq: Long = 0L
        private set

    private val entries = LinkedHashMap<String, Entry>()

    fun reconcile(snapshot: Snapshot) {
        val all = snapshot.allRecords()
        val liveIds = HashSet<String>(all.size * 2)
        for (r in all) {
            liveIds.add(r.id)
            val prev = entries[r.id]
            if (prev == null || prev.record != r) {
                currentSeq += 1L
                entries[r.id] = Entry(currentSeq, r)
            }
        }
        // 若手机端物理移除了记录（例如撤销「新增」或清空回收站），补一条墓碑通知 Web 端删除。
        val now = clock()
        for (id in entries.keys.toList()) {
            val prev = entries[id] ?: continue
            if (id !in liveIds && prev.record.deletedAt == null) {
                val ts = maxOf(now, prev.record.updatedAt + 1L)
                currentSeq += 1L
                entries[id] = Entry(currentSeq, prev.record.withMeta(ts, ts))
            }
        }
    }

    fun since(seq: Long): List<Entry> = entries.values.filter { it.seq > seq }.sortedBy { it.seq }

    fun seqOf(id: String): Long? = entries[id]?.seq
}

/** 按客户端 IP 限制 PIN 登录尝试：连续失败 [maxAttempts] 次后锁定 [lockoutMs] 毫秒并返回 429。 */
private class PinRateLimiter(
    private val maxAttempts: Int,
    private val lockoutMs: Long,
) {
    private data class Bucket(
        var fails: Int = 0,
        var lockedUntilMs: Long = 0L,
    )

    private val buckets = HashMap<String, Bucket>()

    @Synchronized
    fun isBlocked(
        key: String,
        nowMs: Long,
    ): Boolean {
        val b = buckets[key] ?: return false
        if (b.lockedUntilMs > 0L) {
            if (nowMs < b.lockedUntilMs) return true
            buckets.remove(key)
        }
        return false
    }

    @Synchronized
    fun recordFailure(
        key: String,
        nowMs: Long,
    ) {
        val b = buckets.getOrPut(key) { Bucket() }
        if (b.lockedUntilMs in 1L..nowMs) {
            b.fails = 0
            b.lockedUntilMs = 0L
        }
        b.fails += 1
        if (b.fails >= maxAttempts) {
            b.lockedUntilMs = nowMs + lockoutMs
        }
    }

    @Synchronized
    fun reset(key: String) {
        buckets.remove(key)
    }
}
