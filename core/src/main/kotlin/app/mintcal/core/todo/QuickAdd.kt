package app.mintcal.core.todo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class QuickAddResult(
    val title: String,
    val due: LocalDateTime?,
    val dueHasTime: Boolean,
    val priority: Int,
    val listName: String?,
    val tags: List<String>,
)

/** 一行快速输入：识别日期 / 时间 / 清单（#）/ 标签（@）/ 优先级（!高 !中 !低）。 */
object QuickAdd {
    private val CN = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

    fun cnNumber(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return null
        if (s == "十") return 10
        if (s.startsWith("十")) return 10 + (CN[s.getOrNull(1) ?: return 10] ?: return null)
        val ten = s.indexOf('十')
        if (ten > 0) {
            val a = CN[s[0]] ?: return null
            val b = if (ten + 1 < s.length) CN[s[ten + 1]] ?: return null else 0
            return a * 10 + b
        }
        return if (s.length == 1) CN[s[0]] else null
    }

    fun parse(input: String, now: LocalDateTime): QuickAddResult {
        var text = input.trim()
        var priority = 0
        var listName: String? = null
        val tags = mutableListOf<String>()

        Regex("[!！](高|中|低|[123])").find(text)?.let {
            priority = when (it.groupValues[1]) { "高", "3" -> 3; "中", "2" -> 2; else -> 1 }
            text = text.replace(it.value, " ")
        }
        Regex("#(\\S+)").find(text)?.let { listName = it.groupValues[1]; text = text.replace(it.value, " ") }
        Regex("@(\\S+)").findAll(text).toList().forEach { tags += it.groupValues[1]; text = text.replace(it.value, " ") }

        var date: LocalDate? = null
        var time: LocalTime? = null
        val today = now.toLocalDate()

        // 日期
        val relative = Regex("(大后天|后天|明天|明早|明晚|今天|今晚|今早|今日|明日)").find(text)
        if (relative != null) {
            date = when (relative.value) {
                "大后天" -> today.plusDays(3); "后天" -> today.plusDays(2)
                "明天", "明日", "明早", "明晚" -> today.plusDays(1)
                else -> today
            }
            if (relative.value.endsWith("晚")) time = LocalTime.of(20, 0)
            if (relative.value.endsWith("早")) time = LocalTime.of(8, 0)
            text = text.replace(relative.value, " ")
        }
        Regex("(下下|下|这|本)?(周|星期|礼拜)([一二三四五六日天1-7])").find(text)?.let { m ->
            val dow = when (m.groupValues[3]) { "一", "1" -> 1; "二", "2" -> 2; "三", "3" -> 3; "四", "4" -> 4; "五", "5" -> 5; "六", "6" -> 6; else -> 7 }
            val weekOffset = when (m.groupValues[1]) { "下" -> 1L; "下下" -> 2L; else -> 0L }
            var d = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset).plusDays((dow - 1).toLong())
            if (weekOffset == 0L && d.isBefore(today)) d = d.plusWeeks(1)
            date = d
            text = text.replace(m.value, " ")
        }
        Regex("(\\d{1,2})月(\\d{1,2})[日号]?").find(text)?.let { m ->
            val mo = m.groupValues[1].toInt(); val da = m.groupValues[2].toInt()
            runCatching {
                var d = LocalDate.of(today.year, mo, da)
                if (d.isBefore(today)) d = d.plusYears(1)
                date = d
            }
            text = text.replace(m.value, " ")
        }
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(text)?.let { m ->
            runCatching { date = LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
            text = text.replace(m.value, " ")
        }
        // 时间
        Regex("(早上|上午|中午|下午|晚上|凌晨)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*(?:点|:|：)\\s*(半|\\d{1,2})?\\s*分?").find(text)?.let { m ->
            val period = m.groupValues[1]
            var h = cnNumber(m.groupValues[2]) ?: -1
            val mm = when (val g = m.groupValues[3]) { "半" -> 30; "" -> 0; else -> g.toIntOrNull() ?: 0 }
            if (h in 0..24) {
                if ((period == "下午" || period == "晚上") && h < 12) h += 12
                if (period == "中午" && h < 11) h += 12
                if (h == 24) h = 0
                time = LocalTime.of(h, mm.coerceIn(0, 59))
                text = text.replace(m.value, " ")
            }
        }
        val hasTime = time != null
        var due: LocalDateTime? = null
        if (date != null || time != null) {
            var d = date ?: today
            if (date == null && time != null && LocalDateTime.of(d, time).isBefore(now)) d = d.plusDays(1)
            due = LocalDateTime.of(d, time ?: LocalTime.MIDNIGHT)
        }
        val title = text.replace(Regex("\\s+"), " ").trim().ifEmpty { input.trim() }
        return QuickAddResult(title, due, hasTime, priority, listName, tags)
    }
}
