package app.goke.core.util

/** 解码教务接口与网页片段中的常见 HTML 实体（命名实体与十进制/十六进制数字字符引用）。 */
object HtmlEntities {
    private val ENTITY_RE = Regex("""&([a-zA-Z]+|#\d+|#[xX][0-9a-fA-F]+);""")

    private val NAMED =
        mapOf(
            "nbsp" to " ",
            "ensp" to " ",
            "emsp" to " ",
            "lt" to "<",
            "gt" to ">",
            "amp" to "&",
            "quot" to "\"",
            "apos" to "'",
            "ldquo" to "\u201C",
            "rdquo" to "\u201D",
            "lsquo" to "\u2018",
            "rsquo" to "\u2019",
            "mdash" to "\u2014",
            "ndash" to "\u2013",
            "hellip" to "\u2026",
            "middot" to "\u00B7",
            "bull" to "\u2022",
            "times" to "\u00D7",
            "divide" to "\u00F7",
            "plusmn" to "\u00B1",
            "le" to "\u2264",
            "ge" to "\u2265",
            "ne" to "\u2260",
            "deg" to "\u00B0",
        )

    fun decode(s: String): String {
        if ('&' !in s) return s
        return ENTITY_RE.replace(s) { m ->
            val key = m.groupValues[1]
            NAMED[key]
                ?: NAMED[key.lowercase()]
                ?: decodeNumeric(key)
                ?: m.value
        }
    }

    private fun decodeNumeric(key: String): String? {
        if (!key.startsWith("#")) return null
        val cp =
            if (key.length > 2 && (key[1] == 'x' || key[1] == 'X')) {
                key.substring(2).toIntOrNull(16)
            } else {
                key.substring(1).toIntOrNull(10)
            } ?: return null
        if (cp <= 0 || !Character.isValidCodePoint(cp) || cp in 0xD800..0xDFFF) return null
        return String(Character.toChars(cp))
    }
}
