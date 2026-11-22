package app.goke.core.importer

import app.goke.core.model.Course
import app.goke.core.model.Semester
import app.goke.core.model.Source
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids

/**
 * 课程表.xlsx 导入器（本人整理的格式）：每周一张工作表，12 行节次 × 7 列星期，
 * 单元格为「课程名 + 换行 + 教室」，连续节次用合并单元格。
 * 把相同「课程名 + 教室 + 教师 + 星期 + 节次」跨周聚合成周次集合，写成课程规则。
 */
object XlsxTimetableImporter {
    private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val WEEK_RE = Regex("(\\d+)")
    private val PERIOD_RE = Regex("(\\d+)")

    private data class Cell(
        val week: Int,
        val dow: Int,
        val sp: Int,
        val ep: Int,
        val name: String,
        val room: String,
        val teacher: String,
    )

    fun import(
        bytes: ByteArray,
        semester: Semester,
        now: Long,
        ids: IdGenerator = Ids,
    ): ImportResult<TimetableImport> {
        val sheets = XlsxReader.read(bytes)
        if (sheets.value == null) return ImportResult(null, sheets.warnings, sheets.errors)
        return fromSheets(sheets.value, semester, now, ids).let { it.copy(warnings = sheets.warnings + it.warnings) }
    }

    fun fromSheets(
        sheets: List<XlsxSheet>,
        semester: Semester,
        now: Long,
        ids: IdGenerator = Ids,
    ): ImportResult<TimetableImport> {
        val warnings = mutableListOf<String>()
        val raw = mutableListOf<Cell>()
        for ((idx, sheet) in sheets.withIndex()) {
            val week =
                WEEK_RE
                    .find(sheet.name)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: (idx + 1)
            val header = findHeader(sheet)
            if (header == null) {
                warnings += "no_header:${sheet.name}"
                continue
            }
            val dayCols = header.first
            val headerRow = header.second
            val labelCol = (dayCols.values.minOrNull() ?: 2) - 1
            // 行 → 节次
            val rowPeriod = HashMap<Int, Int>()
            for (r in headerRow + 1..sheet.maxRow) {
                val label = sheet.text(r, labelCol.coerceAtLeast(1))
                rowPeriod[r] = PERIOD_RE
                    .find(label)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: (r - headerRow)
            }
            for ((dow, col) in dayCols) {
                for (r in headerRow + 1..sheet.maxRow) {
                    val text = sheet.text(r, col).trim()
                    if (text.isEmpty()) continue
                    val merge = sheet.mergeAt(r, col)
                    if (merge != null && (merge.r1 != r || merge.c1 != col)) continue
                    val r2 = merge?.r2 ?: r
                    val sp = rowPeriod[r] ?: continue
                    val ep = rowPeriod[r2] ?: sp
                    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    raw += Cell(week, dow, sp, maxOf(sp, ep), lines.first(), lines.getOrNull(1).orEmpty(), lines.getOrNull(2).orEmpty())
                }
            }
        }
        if (raw.isEmpty()) return ImportResult(null, warnings, listOf("no_courses"))

        // 未合并但相邻的相同内容，合并为连续节次
        val merged = mutableListOf<Cell>()
        for (g in raw.groupBy { listOf(it.week, it.dow, it.name, it.room, it.teacher) }.values) {
            val sorted = g.sortedBy { it.sp }
            var cur = sorted.first()
            for (c in sorted.drop(1)) {
                if (c.sp <= cur.ep + 1) {
                    cur = cur.copy(ep = maxOf(cur.ep, c.ep))
                } else {
                    merged += cur
                    cur = c
                }
            }
            merged += cur
        }

        data class Key(
            val name: String,
            val room: String,
            val teacher: String,
            val dow: Int,
            val sp: Int,
            val ep: Int,
        )
        val byKey = LinkedHashMap<Key, MutableSet<Int>>()
        for (c in merged.sortedWith(compareBy({ it.week }, { it.dow }, { it.sp }))) {
            byKey.getOrPut(Key(c.name, c.room, c.teacher, c.dow, c.sp, c.ep)) { linkedSetOf() } += c.week
        }
        val colors = Palette.assign(byKey.keys.map { it.name })
        val courses =
            byKey.map { (k, w) ->
                Course(
                    id = ids.next(),
                    semesterId = semester.id,
                    name = k.name,
                    teacher = k.teacher,
                    room = k.room,
                    dayOfWeek = k.dow,
                    startPeriod = k.sp,
                    endPeriod = k.ep,
                    weeks = w.toSortedSet(),
                    colorIndex = colors[k.name] ?: 0,
                    source = Source.IMPORT,
                    updatedAt = now,
                )
            }
        val maxWeek = courses.maxOf { it.weeks.max() }
        if (maxWeek > semester.totalWeeks) warnings += "weeks_exceed:$maxWeek"
        return ImportResult(TimetableImport(courses, maxWeek, emptyList()), warnings)
    }

    /** 找到含「周一…周日」的表头行，返回 (星期 → 列) 与表头行号。 */
    private fun findHeader(sheet: XlsxSheet): Pair<Map<Int, Int>, Int>? {
        for (r in 1..minOf(sheet.maxRow, 5)) {
            val cols = LinkedHashMap<Int, Int>()
            for (c in 1..sheet.maxCol) {
                val t = sheet.text(r, c).trim()
                val idx = DAY_NAMES.indexOfFirst { t == it || t == it.replace("周", "星期") || t.endsWith(it) }
                if (idx >= 0) cols[idx + 1] = c
            }
            if (cols.size >= 5) return cols to r
        }
        return null
    }
}
