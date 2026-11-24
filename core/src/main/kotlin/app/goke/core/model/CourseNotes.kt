package app.goke.core.model

import app.goke.core.time.WeekSets

/**
 * 课程备注解析与整理：把教务按周导入的「第X周：授课内容」与用户手动备注分离，
 * 便于课程详情卡片优先呈现当周详情，同时保留全部备注查看与快速补充能力。
 */
object CourseNotes {
    private val WEEK_LINE_RE = Regex("""^第([\d,\-]+)周：(.*)$""")
    private const val MAX_WEEKS = 30

    data class Parsed(
        val currentWeekNotes: List<String>,
        val generalNotes: List<String>,
        val otherWeekLines: List<String>,
    ) {
        val focusedNotes: List<String>
            get() = (currentWeekNotes + generalNotes).distinct()

        val hasOtherWeeks: Boolean
            get() = otherWeekLines.isNotEmpty()
    }

    fun parse(
        note: String,
        week: Int?,
        maxWeeks: Int = MAX_WEEKS,
    ): Parsed {
        if (note.isBlank()) return Parsed(emptyList(), emptyList(), emptyList())
        val currentWeek = mutableListOf<String>()
        val general = mutableListOf<String>()
        val otherWeeks = mutableListOf<String>()
        for (raw in note.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = WEEK_LINE_RE.matchEntire(line)
            if (m == null) {
                general += line
                continue
            }
            val ws = WeekSets.parse(m.groupValues[1], maxWeeks)
            val text = m.groupValues[2].trim()
            if (week != null && week in ws) {
                if (text.isNotEmpty()) currentWeek += text
            } else {
                otherWeeks += line
            }
        }
        return Parsed(
            currentWeekNotes = currentWeek.distinct(),
            generalNotes = general.distinct(),
            otherWeekLines = otherWeeks.distinct(),
        )
    }

    fun summaryForWeek(
        note: String,
        week: Int?,
        maxWeeks: Int = MAX_WEEKS,
    ): String = parse(note, week, maxWeeks).focusedNotes.joinToString("\n")

    fun stripWeekPrefix(line: String): String {
        val trimmed = line.trim()
        val m = WEEK_LINE_RE.matchEntire(trimmed) ?: return trimmed
        return m.groupValues[2].trim()
    }

    fun appendUserNote(
        existingNote: String,
        addition: String,
    ): String {
        val clean = addition.trim()
        if (clean.isEmpty()) return existingNote
        val cur = existingNote.trim()
        if (cur.isEmpty()) return clean
        val existingLines = cur.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (clean in existingLines) return existingNote
        return "$cur\n$clean"
    }
}
