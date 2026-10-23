package app.mintcal.core.importer

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 工作表里一个合并区域（行列均从 1 开始，含端点）。 */
data class CellRange(
    val r1: Int,
    val c1: Int,
    val r2: Int,
    val c2: Int,
)

data class XlsxSheet(
    val name: String,
    /** (行, 列) → 文本，行列从 1 开始 */
    val cells: Map<Pair<Int, Int>, String>,
    val merges: List<CellRange>,
) {
    val maxRow: Int get() = cells.keys.maxOfOrNull { it.first } ?: 0
    val maxCol: Int get() = cells.keys.maxOfOrNull { it.second } ?: 0

    fun text(
        r: Int,
        c: Int,
    ): String = cells[r to c].orEmpty()

    /** 含 (r,c) 的合并区域。 */
    fun mergeAt(
        r: Int,
        c: Int,
    ): CellRange? = merges.firstOrNull { r in it.r1..it.r2 && c in it.c1..it.c2 }
}

/** 轻量 xlsx 解析：java.util.zip + XML，不引入 POI。 */
object XlsxReader {
    fun read(bytes: ByteArray): ImportResult<List<XlsxSheet>> = read(ByteArrayInputStream(bytes))

    fun read(input: InputStream): ImportResult<List<XlsxSheet>> {
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels"))) files[e.name] = zip.readBytes()
                    e = zip.nextEntry
                }
            }
        } catch (ex: Exception) {
            return ImportResult.failure("not_xlsx")
        }
        val wb = files["xl/workbook.xml"] ?: return ImportResult.failure("not_xlsx")
        return try {
            val shared = files["xl/sharedStrings.xml"]?.let(::parseShared).orEmpty()
            val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parseRels).orEmpty()
            val sheets = mutableListOf<XlsxSheet>()
            val warnings = mutableListOf<String>()
            val sheetEls = doc(wb).getElementsByTagName("sheet")
            for (i in 0 until sheetEls.length) {
                val el = sheetEls.item(i) as Element
                val name = el.getAttribute("name")
                val rid = el.getAttribute("r:id").ifEmpty { el.getAttribute("id") }
                val target = rels[rid]?.let { t -> if (t.startsWith("/")) t.removePrefix("/") else "xl/$t" } ?: "xl/worksheets/sheet${i + 1}.xml"
                val bytes = files[target]
                if (bytes == null) {
                    warnings += "missing_sheet:$name"
                    continue
                }
                sheets += parseSheet(name, bytes, shared)
            }
            ImportResult(sheets, warnings)
        } catch (ex: Exception) {
            ImportResult.failure("bad_xlsx:${ex.message}")
        }
    }

    private fun doc(bytes: ByteArray) =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                isNamespaceAware = false
            }.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun textOf(n: Node): String {
        val sb = StringBuilder()

        fun walk(x: Node) {
            if (x.nodeType == Node.ELEMENT_NODE) {
                if (x.nodeName == "rPh" || x.nodeName == "phoneticPr") return
                if (x.nodeName == "t") {
                    sb.append(x.textContent)
                    return
                }
            }
            val ch = x.childNodes
            for (i in 0 until ch.length) walk(ch.item(i))
        }
        walk(n)
        return sb.toString()
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val list = doc(bytes).getElementsByTagName("si")
        return (0 until list.length).map { textOf(list.item(it)) }
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val list = doc(bytes).getElementsByTagName("Relationship")
        return (0 until list.length).associate {
            val e = list.item(it) as Element
            e.getAttribute("Id") to e.getAttribute("Target")
        }
    }

    private fun parseSheet(
        name: String,
        bytes: ByteArray,
        shared: List<String>,
    ): XlsxSheet {
        val d = doc(bytes)
        val cells = HashMap<Pair<Int, Int>, String>()
        val cs = d.getElementsByTagName("c")
        for (i in 0 until cs.length) {
            val c = cs.item(i) as Element
            val (r, col) = parseRef(c.getAttribute("r")) ?: continue
            val type = c.getAttribute("t")
            val text =
                when (type) {
                    "s" -> {
                        c
                            .getElementsByTagName("v")
                            .item(0)
                            ?.textContent
                            ?.trim()
                            ?.toIntOrNull()
                            ?.let { shared.getOrNull(it) }
                            .orEmpty()
                    }

                    "inlineStr" -> {
                        textOf(c)
                    }

                    else -> {
                        c
                            .getElementsByTagName("v")
                            .item(0)
                            ?.textContent
                            .orEmpty()
                    }
                }
            if (text.isNotBlank()) cells[r to col] = text.replace("\r\n", "\n").replace('\r', '\n')
        }
        val merges = mutableListOf<CellRange>()
        val ms = d.getElementsByTagName("mergeCell")
        for (i in 0 until ms.length) {
            val ref = (ms.item(i) as Element).getAttribute("ref")
            val a = ref.substringBefore(':')
            val b = ref.substringAfter(':', a)
            val p1 = parseRef(a) ?: continue
            val p2 = parseRef(b) ?: continue
            merges += CellRange(p1.first, p1.second, p2.first, p2.second)
        }
        return XlsxSheet(name, cells, merges)
    }

    /** `B12` → (12, 2) */
    internal fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("^([A-Za-z]+)(\\d+)$").find(ref.trim()) ?: return null
        var col = 0
        for (ch in m.groupValues[1].uppercase()) col = col * 26 + (ch - 'A' + 1)
        return m.groupValues[2].toInt() to col
    }
}
