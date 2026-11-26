package app.goke.core.export

import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.time.SemesterCalendar
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 纯 Kotlin OOXML `.xlsx` 课表网格导出器（每周一个工作表，含薄荷色块与冲突标记）。 */
object XlsxTimetableWriter {
    private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // 与 Theme.kt LightCourse 保持一致的 ARGB Hex（OOXML 使用 AARRGGBB）
    private val COURSE_COLORS =
        listOf(
            "FFD9E8EF" to "FF3B6173",
            "FFBFDCD5" to "FF2C5B53",
            "FFDAD8EE" to "FF4F4D7C",
            "FFF3EBCF" to "FF75662F",
            "FFF7DFD2" to "FF85503A",
            "FFDFE8DA" to "FF4B5F49",
            "FFF4D9E0" to "FF83465A",
            "FFD2D8EA" to "FF454F7A",
        )
    private const val CONFLICT_BG = "FFFCE9E7"
    private const val CONFLICT_FG = "FF7A2A22"
    private const val HEADER_BG = "FFF2F3F5"
    private const val HEADER_FG = "FF333333"
    private const val BORDER_COLOR = "FFD0D7DE"

    private const val STYLE_EMPTY = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_COURSE_BASE = 2
    private val STYLE_CONFLICT = STYLE_COURSE_BASE + COURSE_COLORS.size

    fun write(
        snapshot: Snapshot,
        includeTeacher: Boolean = true,
    ): ByteArray {
        val live = snapshot.live()
        val sem = live.currentSemester ?: error("no_current_semester")
        return write(sem, live.courses, live.exceptions, includeTeacher)
    }

    fun write(
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException> = emptyList(),
        includeTeacher: Boolean = true,
    ): ByteArray {
        val cal = SemesterCalendar(semester)
        val strings = LinkedHashMap<String, Int>()

        fun intern(s: String): Int = strings.getOrPut(s) { strings.size }

        val totalWeeks = semester.totalWeeks.coerceAtLeast(1)
        val sheetXmls =
            (1..totalWeeks).map { week ->
                val name = "第${week}周"
                val xml = buildSheetXml(cal, semester, courses, exceptions, week, includeTeacher, ::intern)
                name to xml
            }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun put(
                entryName: String,
                content: String,
            ) {
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("[Content_Types].xml", buildContentTypes(sheetXmls.size))
            put("_rels/.rels", RELS_XML)
            put("xl/workbook.xml", buildWorkbookXml(sheetXmls.map { it.first }))
            put("xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheetXmls.size))
            put("xl/styles.xml", buildStylesXml())
            put("xl/sharedStrings.xml", buildSharedStringsXml(strings.keys))
            sheetXmls.forEachIndexed { idx, (_, xml) ->
                put("xl/worksheets/sheet${idx + 1}.xml", xml)
            }
        }
        return baos.toByteArray()
    }

    private fun buildSheetXml(
        cal: SemesterCalendar,
        semester: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        week: Int,
        includeTeacher: Boolean,
        intern: (String) -> Int,
    ): String {
        val periods = cal.periods
        val occsByDay =
            CourseExpander
                .occurrencesInWeek(semester, courses, exceptions, week)
                .groupBy { it.date.dayOfWeek.value }

        val merges = mutableListOf<String>()
        val rowsSb = StringBuilder()

        // Row 1: 表头
        rowsSb.append("<row r=\"1\" ht=\"28\" customHeight=\"1\">")
        rowsSb.append(strCell(1, 1, STYLE_HEADER, intern("节次\\星期")))
        for (dow in 1..7) {
            val d = cal.dateOf(week, dow)
            val label = "${DAY_NAMES[dow - 1]} ${d.monthValue}/${d.dayOfMonth}"
            rowsSb.append(strCell(1, dow + 1, STYLE_HEADER, intern(label)))
        }
        rowsSb.append("</row>")

        // 预先为每天每节计算覆盖的课程出现，避免任何重叠 mergeCell
        val dayCovering =
            (1..7).associateWith { dow ->
                val dayOccs = occsByDay[dow].orEmpty()
                periods.map { p -> dayOccs.filter { p.index in it.startPeriod..it.endPeriod } }
            }

        // 记录每天每节的单元格内容与样式，以及合并起点
        data class CellSpec(
            val style: Int,
            val sharedIdx: Int?,
        )
        val grid = Array(periods.size) { arrayOfNulls<CellSpec>(8) }
        for (dow in 1..7) {
            val col = dow + 1
            val covering = dayCovering[dow].orEmpty()
            var rIdx = 0
            while (rIdx < periods.size) {
                val list = covering[rIdx]
                when {
                    list.isEmpty() -> {
                        grid[rIdx][dow] = CellSpec(STYLE_EMPTY, null)
                        rIdx++
                    }

                    list.size > 1 -> {
                        val text = list.joinToString("\n───\n") { formatOccurrence(it, includeTeacher) }
                        grid[rIdx][dow] = CellSpec(STYLE_CONFLICT, intern(text))
                        rIdx++
                    }

                    else -> {
                        val occ = list[0]
                        var endRIdx = rIdx
                        while (endRIdx + 1 < periods.size && covering[endRIdx + 1].size == 1 && covering[endRIdx + 1][0] == occ) {
                            endRIdx++
                        }
                        val style = STYLE_COURSE_BASE + (((occ.course.colorIndex % 8) + 8) % 8)
                        val textIdx = intern(formatOccurrence(occ, includeTeacher))
                        grid[rIdx][dow] = CellSpec(style, textIdx)
                        for (k in (rIdx + 1)..endRIdx) {
                            grid[k][dow] = CellSpec(style, null)
                        }
                        if (endRIdx > rIdx) {
                            val colL = colLetter(col)
                            merges += "$colL${rIdx + 2}:$colL${endRIdx + 2}"
                        }
                        rIdx = endRIdx + 1
                    }
                }
            }
        }

        // Rows 2..(periods.size + 1)
        for ((pIdx, p) in periods.withIndex()) {
            val row = pIdx + 2
            rowsSb.append("<row r=\"$row\" ht=\"44\" customHeight=\"1\">")
            rowsSb.append(strCell(row, 1, STYLE_HEADER, intern("${p.index}\n${p.start}-${p.end}")))
            for (dow in 1..7) {
                val spec = grid[pIdx][dow] ?: CellSpec(STYLE_EMPTY, null)
                if (spec.sharedIdx != null) {
                    rowsSb.append(strCell(row, dow + 1, spec.style, spec.sharedIdx))
                } else {
                    rowsSb.append(emptyCell(row, dow + 1, spec.style))
                }
            }
            rowsSb.append("</row>")
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView tabSelected=\"${if (week == 1) 1 else 0}\" workbookViewId=\"0\">")
        sb.append("<pane xSplit=\"1\" ySplit=\"1\" topLeftCell=\"B2\" activePane=\"bottomRight\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews>")
        sb.append("<sheetFormatPr defaultRowHeight=\"44\"/>")
        sb.append("<cols>")
        sb.append("<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"8\" width=\"18\" customWidth=\"1\"/>")
        sb.append("</cols>")
        sb.append("<sheetData>").append(rowsSb).append("</sheetData>")
        if (merges.isNotEmpty()) {
            sb.append("<mergeCells count=\"${merges.size}\">")
            for (ref in merges) {
                sb.append("<mergeCell ref=\"$ref\"/>")
            }
            sb.append("</mergeCells>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun formatOccurrence(
        occ: CourseOccurrence,
        includeTeacher: Boolean,
    ): String {
        val parts = mutableListOf(occ.course.name)
        if (occ.room.isNotBlank()) parts += occ.room
        if (includeTeacher && occ.course.teacher.isNotBlank()) parts += occ.course.teacher
        return parts.joinToString("\n")
    }

    private fun strCell(
        row: Int,
        col: Int,
        style: Int,
        sharedIdx: Int,
    ): String = "<c r=\"${colLetter(col)}$row\" t=\"s\" s=\"$style\"><v>$sharedIdx</v></c>"

    private fun emptyCell(
        row: Int,
        col: Int,
        style: Int,
    ): String = "<c r=\"${colLetter(col)}$row\" s=\"$style\"/>"

    private fun colLetter(col: Int): String {
        var n = col
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun buildContentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append(
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>",
        )
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sb.append(
            "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>",
        )
        for (i in 1..sheetCount) {
            sb.append(
                "<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>",
            )
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun buildWorkbookXml(sheetNames: List<String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sb.append("<sheets>")
        sheetNames.forEachIndexed { idx, name ->
            val id = idx + 1
            sb.append("<sheet name=\"${escapeXml(name)}\" sheetId=\"$id\" r:id=\"rId$id\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            sb.append(
                "<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>",
            )
        }
        sb.append(
            "<Relationship Id=\"rId${sheetCount + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>",
        )
        sb.append(
            "<Relationship Id=\"rId${sheetCount + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>",
        )
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildSharedStringsXml(strings: Collection<String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append(
            "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${strings.size}\" uniqueCount=\"${strings.size}\">",
        )
        for (s in strings) {
            sb.append("<si><t xml:space=\"preserve\">${escapeXml(s)}</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        // Fonts: 0=default, 1=header(bold), 2..9=courses, 10=conflict
        sb.append("<fonts count=\"${3 + COURSE_COLORS.size}\">")
        sb.append("<font><sz val=\"10\"/><color rgb=\"$HEADER_FG\"/><name val=\"sans-serif\"/></font>")
        sb.append("<font><b/><sz val=\"10\"/><color rgb=\"$HEADER_FG\"/><name val=\"sans-serif\"/></font>")
        for ((_, fg) in COURSE_COLORS) {
            sb.append("<font><sz val=\"10\"/><color rgb=\"$fg\"/><name val=\"sans-serif\"/></font>")
        }
        sb.append("<font><sz val=\"10\"/><color rgb=\"$CONFLICT_FG\"/><name val=\"sans-serif\"/></font>")
        sb.append("</fonts>")

        // Fills: 0=none, 1=gray125, 2=header, 3..10=courses, 11=conflict
        sb.append("<fills count=\"${4 + COURSE_COLORS.size}\">")
        sb.append("<fill><patternFill patternType=\"none\"/></fill>")
        sb.append("<fill><patternFill patternType=\"gray125\"/></fill>")
        sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$HEADER_BG\"/></patternFill></fill>")
        for ((bg, _) in COURSE_COLORS) {
            sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$bg\"/></patternFill></fill>")
        }
        sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$CONFLICT_BG\"/></patternFill></fill>")
        sb.append("</fills>")

        // Borders: 0=none, 1=thin #D0D7DE
        sb.append("<borders count=\"2\">")
        sb.append("<border><left/><right/><top/><bottom/><diagonal/></border>")
        sb.append("<border>")
        sb.append("<left style=\"thin\"><color rgb=\"$BORDER_COLOR\"/></left>")
        sb.append("<right style=\"thin\"><color rgb=\"$BORDER_COLOR\"/></right>")
        sb.append("<top style=\"thin\"><color rgb=\"$BORDER_COLOR\"/></top>")
        sb.append("<bottom style=\"thin\"><color rgb=\"$BORDER_COLOR\"/></bottom>")
        sb.append("<diagonal/></border>")
        sb.append("</borders>")

        sb.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")

        // cellXfs: 0=empty, 1=header, 2..9=courses, 10=conflict
        sb.append("<cellXfs count=\"${3 + COURSE_COLORS.size}\">")
        sb.append(
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>",
        )
        sb.append(
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>",
        )
        for (i in COURSE_COLORS.indices) {
            val fontId = 2 + i
            val fillId = 3 + i
            sb.append(
                "<xf numFmtId=\"0\" fontId=\"$fontId\" fillId=\"$fillId\" borderId=\"1\" xfId=\"0\" " +
                    "applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
                    "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>",
            )
        }
        val conflictFontId = 2 + COURSE_COLORS.size
        val conflictFillId = 3 + COURSE_COLORS.size
        sb.append(
            "<xf numFmtId=\"0\" fontId=\"$conflictFontId\" fillId=\"$conflictFillId\" borderId=\"1\" xfId=\"0\" " +
                "applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>",
        )
        sb.append("</cellXfs>")
        sb.append("</styleSheet>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private const val RELS_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"
}
