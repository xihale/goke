package app.goke.core.campus

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

@Serializable
data class DropRelayFile(
    val path: String,
    val name: String = "",
    val size: Long = 0L,
    val mtime: Long = 0L,
    val ih: String = "",
) {
    val normalizedPath: String
        get() = path.trim().trimStart('/')

    val displayName: String
        get() = name.ifBlank { normalizedPath.substringAfterLast('/') }
}

@Serializable
data class DropRelayIndex(
    val version: Int = 1,
    val generatedAt: Long = 0L,
    val totalBytes: Long = 0L,
    val files: List<DropRelayFile> = emptyList(),
)

@Serializable
data class DropRelayLimits(
    val maxFiles: Int = 64,
    val maxRooms: Int = 8,
    val maxRoomsPerIP: Int = 2,
    val roomIdleSec: Int = 7200,
    val roomHardSec: Int = 21600,
)

@Serializable
data class DropRelayHealth(
    val ok: Boolean = false,
    val version: Int = 1,
    val mode: String = "",
    val capabilities: List<String> = emptyList(),
    val peerOrigin: String = "",
    val shareOrigin: String = "",
    val dropOrigin: String = "",
    val rooms: Int = 0,
    val swarms: Int = 0,
    val seedActive: Int = 0,
    val seedBytes: Long = 0L,
    val loadRatio: Double = 0.0,
    val p2pWaitMs: Long = 0L,
    val limits: DropRelayLimits? = null,
) {
    fun discoveredPeerOrigin(): String =
        when {
            peerOrigin.isNotBlank() -> peerOrigin
            mode.equals("share", ignoreCase = true) -> dropOrigin
            mode.equals("drop", ignoreCase = true) -> shareOrigin
            else -> ""
        }
}

@Serializable
data class DropCodeFile(
    val name: String,
    val path: String = "",
    val size: Long = 0L,
    val ih: String = "",
)

@Serializable
data class DropCodeResponse(
    val files: List<DropCodeFile> = emptyList(),
)

data class DropCodePreview(
    val code: String,
    val files: List<DropCodeFile>,
) {
    val totalBytes: Long
        get() = files.sumOf { it.size }
}

sealed interface ShareBrowseEntry {
    val sortName: String

    data class Dir(
        val path: String,
        val name: String,
        val fileCount: Int,
        val totalBytes: Long,
    ) : ShareBrowseEntry {
        override val sortName: String get() = name
    }

    data class FileItem(
        val file: DropRelayFile,
    ) : ShareBrowseEntry {
        override val sortName: String get() = file.displayName
    }
}

/** DropRelay 协议解析、路径编码、目录投影、课程资源匹配与下载分流纯逻辑。 */
object DropRelayLogic {
    const val DIRECT_DOWNLOAD_LIMIT_BYTES: Long = 200L * 1024L * 1024L
    const val CODE_LENGTH: Int = 6
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val CODE_CHAR_SET = CODE_ALPHABET.toSet()
    private val URL_CODE_REGEX = Regex("""https?://[^\s/]+/([23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6})(?:[/?#\s]|$)""", RegexOption.IGNORE_CASE)
    private val STANDALONE_CODE_REGEX = Regex("""(?<![A-Za-z0-9])([23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6})(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
    private val COURSE_PAREN_REGEX = Regex("""[（(\[【][^）)\]】]*[）)\]】]""")
    private val COURSE_SUFFIX_REGEX = Regex("""(?:[（(]?[上下中前后][）)]?|[IVXⅠⅡⅢⅣ]+|\d+|[A-Za-z])$""")
    private val TOKEN_SPLIT_REGEX = Regex("""[\s\-_/·、,，。:：;；]+""")

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$portPart"
    }

    fun parseIndexJson(raw: String): DropRelayIndex {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val files = json.decodeFromString(ListSerializer(DropRelayFile.serializer()), trimmed)
            return DropRelayIndex(
                version = 1,
                generatedAt = files.maxOfOrNull { it.mtime } ?: 0L,
                totalBytes = files.sumOf { it.size },
                files = files,
            )
        }
        return json.decodeFromString(DropRelayIndex.serializer(), trimmed)
    }

    fun encodePathSegments(path: String): String =
        path
            .trim()
            .trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }

    fun fileWebseedUrl(
        baseUrl: String,
        path: String,
    ): String {
        val base = normalizeBaseUrl(baseUrl) ?: baseUrl.trimEnd('/')
        return "$base/${encodePathSegments(path)}"
    }

    fun fileBrowserUrl(
        baseUrl: String,
        path: String,
    ): String {
        val base = normalizeBaseUrl(baseUrl) ?: baseUrl.trimEnd('/')
        return "$base/#/${encodePathSegments(path)}"
    }

    fun dirZipUrl(
        baseUrl: String,
        dirPath: String,
    ): String {
        val base = normalizeBaseUrl(baseUrl) ?: baseUrl.trimEnd('/')
        val encoded = encodePathSegments(dirPath)
        return if (encoded.isEmpty()) "$base/?zip=1" else "$base/$encoded?zip=1"
    }

    fun dropRoomUrl(
        baseUrl: String,
        code: String,
    ): String {
        val base = normalizeBaseUrl(baseUrl) ?: baseUrl.trimEnd('/')
        return "$base/${code.trim().uppercase(Locale.ROOT)}"
    }

    fun dropSendWebViewUrl(baseUrl: String): String {
        val base = normalizeBaseUrl(baseUrl) ?: baseUrl.trimEnd('/')
        return "$base/?client=goke"
    }

    fun shouldPreferBrowserP2p(
        fileSize: Long,
        health: DropRelayHealth?,
    ): Boolean = fileSize > DIRECT_DOWNLOAD_LIMIT_BYTES || (health != null && health.p2pWaitMs > 0L)

    fun isValidDropCode(raw: String): Boolean {
        val upper = raw.trim().uppercase(Locale.ROOT)
        return upper.length == CODE_LENGTH && upper.all { it in CODE_CHAR_SET }
    }

    /** 从任意文本或链接中提取 6 位取件码（输入框与剪贴板使用）。 */
    fun extractDropCode(
        text: String,
        dropBaseUrl: String = "",
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (isValidDropCode(trimmed)) return trimmed.uppercase(Locale.ROOT)
        extractDropCodeFromUrl(trimmed, dropBaseUrl)?.let { return it }
        return STANDALONE_CODE_REGEX
            .find(trimmed)
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.ROOT)
    }

    /** 仅从明确包含 DropRelay 链接的文本中提取取件码（用于外部分享文本分流，避免误判普通待办文字）。 */
    fun extractDropCodeFromUrl(
        text: String,
        dropBaseUrl: String = "",
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val configuredHost = normalizeBaseUrl(dropBaseUrl)?.let { runCatching { URI(it).host?.lowercase(Locale.ROOT) }.getOrNull() }
        for (match in URL_CODE_REGEX.findAll(trimmed)) {
            val fullUrl = match.value.trim()
            val code = match.groupValues[1].uppercase(Locale.ROOT)
            val host = runCatching { URI(fullUrl.substringBefore(' ')).host?.lowercase(Locale.ROOT) }.getOrNull() ?: continue
            if (configuredHost != null && host == configuredHost) {
                return code
            }
            if (host.startsWith("drop.")) {
                return code
            }
        }
        return null
    }

    fun listDirectory(
        files: List<DropRelayFile>,
        currentDir: String,
    ): List<ShareBrowseEntry> {
        val prefix = currentDir.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val dirAgg = linkedMapOf<String, Pair<Int, Long>>()
        val directFiles = mutableListOf<ShareBrowseEntry.FileItem>()

        for (file in files) {
            val norm = file.normalizedPath
            if (prefix.isNotEmpty() && !norm.startsWith(prefix)) continue
            val rest = norm.removePrefix(prefix)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            if (slash < 0) {
                directFiles.add(ShareBrowseEntry.FileItem(file))
            } else {
                val dirName = rest.substring(0, slash)
                val prev = dirAgg[dirName] ?: (0 to 0L)
                dirAgg[dirName] = (prev.first + 1) to (prev.second + file.size.coerceAtLeast(0L))
            }
        }

        val dirs =
            dirAgg.entries
                .map { (name, agg) ->
                    ShareBrowseEntry.Dir(
                        path = if (prefix.isEmpty()) name else "$prefix$name",
                        name = name,
                        fileCount = agg.first,
                        totalBytes = agg.second,
                    )
                }.sortedBy { it.sortName.lowercase(Locale.ROOT) }

        return dirs + directFiles.sortedBy { it.sortName.lowercase(Locale.ROOT) }
    }

    fun searchFiles(
        files: List<DropRelayFile>,
        query: String,
    ): List<DropRelayFile> {
        val tokens =
            query
                .trim()
                .lowercase(Locale.ROOT)
                .split(TOKEN_SPLIT_REGEX)
                .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return files
        return files.filter { file ->
            val hay = "${file.normalizedPath} ${file.displayName}".lowercase(Locale.ROOT)
            tokens.all { it in hay }
        }
    }

    /** 基于课程名做轻量本地分词/子串匹配，用于课程详情页「相关资料」联动（纯本地零网络）。 */
    fun matchCourseResources(
        files: List<DropRelayFile>,
        courseName: String,
    ): List<DropRelayFile> {
        val raw = courseName.trim()
        if (raw.length < 2 || files.isEmpty()) return emptyList()

        val cleaned =
            COURSE_PAREN_REGEX
                .replace(raw, "")
                .trim()
                .let { COURSE_SUFFIX_REGEX.replace(it, "").trim() }
                .ifBlank { raw }
        val candidates =
            buildList {
                add(raw.lowercase(Locale.ROOT))
                if (cleaned.length >= 2) add(cleaned.lowercase(Locale.ROOT))
                TOKEN_SPLIT_REGEX
                    .split(cleaned)
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.length >= 2 }
                    .forEach { add(it) }
            }.distinct()

        return files.filter { file ->
            val hay = "${file.normalizedPath} ${file.displayName}".lowercase(Locale.ROOT)
            candidates.any { token -> token in hay }
        }
    }

    fun formatFileSize(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", b / 1024.0)
            b < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
