package app.goke.core.todo

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
    /** 「/课程名」= 下节该课前截止（调用方按名字匹配课程）。 */
    val courseName: String? = null,
    /** 被识别并从标题里剔除的原文片段（供输入框高亮、点标签回写）。 */
    val tokens: List<QuickToken> = emptyList(),
)

enum class QuickTokenKind { DATE, TIME, PRIORITY, LIST, TAG, COURSE }

/** 输入里被识别的一段原文。 */
data class QuickToken(
    val text: String,
    val kind: QuickTokenKind,
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

    fun parse(
        input: String,
        now: LocalDateTime,
    ): QuickAddResult {
        var text = input.trim()
        var priority = 0
        var listName: String? = null
        val tags = mutableListOf<String>()
        val tokens = mutableListOf<QuickToken>()
        var courseName: String? = null

        fun eat(
            src: String,
            value: String,
            kind: QuickTokenKind,
        ): String {
            tokens += QuickToken(value.trim(), kind)
            return src.replace(value, " ")
        }

        Regex("[!！](高|中|低|[123])").find(text)?.let {
            priority =
                when (it.groupValues[1]) {
                    "高", "3" -> 3
                    "中", "2" -> 2
                    else -> 1
                }
            text = eat(text, it.value, QuickTokenKind.PRIORITY)
        }
        Regex("#([^\\s#@!！，,。.？?]+)[，,。.？?]*").find(text)?.let {
            listName = it.groupValues[1]
            text = eat(text, it.value, QuickTokenKind.LIST)
        }
        Regex("@([^\\s#@!！，,。.？?]+)[，,。.？?]*").findAll(text).toList().forEach {
            tags += it.groupValues[1]
            text = eat(text, it.value, QuickTokenKind.TAG)
        }

        // 「/概率论」：必须在开头或空白之后，避免吃掉 10/26 这类日期
        Regex("(?:^|\\s)/([^\\s#@!！/，,。.？?]+)").find(text)?.let {
            courseName = it.groupValues[1]
            text = eat(text, it.value, QuickTokenKind.COURSE)
        }

        var date: LocalDate? = null
        var time: LocalTime? = null
        var impliedPm = false
        val today = now.toLocalDate()

        // 「周末」先于「周X」处理，否则星期正则会把「末」当作未知字
        Regex("(下下|下|这|本)?周末").find(text)?.let { m ->
            var d = today.with(DayOfWeek.SATURDAY)
            if (d.isBefore(today)) d = d.plusWeeks(1)
            date =
                d.plusWeeks(
                    when (m.groupValues[1]) {
                        "下" -> 1L
                        "下下" -> 2L
                        else -> 0L
                    },
                )
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        Regex("(下下|下|这|本)?(周|星期|礼拜)([一二三四五六日天1-7])").find(text)?.let { m ->
            val dow =
                when (m.groupValues[3]) {
                    "一", "1" -> 1
                    "二", "2" -> 2
                    "三", "3" -> 3
                    "四", "4" -> 4
                    "五", "5" -> 5
                    "六", "6" -> 6
                    else -> 7
                }
            val weekOffset =
                when (m.groupValues[1]) {
                    "下" -> 1L
                    "下下" -> 2L
                    else -> 0L
                }
            var d = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset).plusDays((dow - 1).toLong())
            if (weekOffset == 0L && d.isBefore(today)) d = d.plusWeeks(1)
            date = d
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        val relative = Regex("(大后天|后天|明天|明早|明晚|今天|今晚|今早|今日|明日)").find(text)
        if (relative != null) {
            date =
                when (relative.value) {
                    "大后天" -> today.plusDays(3)
                    "后天" -> today.plusDays(2)
                    "明天", "明日", "明早", "明晚" -> today.plusDays(1)
                    else -> today
                }
            if (relative.value.endsWith("晚")) {
                time = LocalTime.of(20, 0)
                impliedPm = true
            }
            if (relative.value.endsWith("早")) time = LocalTime.of(8, 0)
            text = eat(text, relative.value, QuickTokenKind.DATE)
        }
        // 单说「下周」= 下周一（「下周三」这类已在上一步被吃掉）
        Regex("(下下|下)周(?!\\s*[一二三四五六日天])").find(text)?.let { m ->
            date = today.with(DayOfWeek.MONDAY).plusWeeks(if (m.groupValues[1] == "下下") 2L else 1L)
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        Regex("(\\d{1,3}|[一二两三四五六七八九十]{1,3})天后").find(text)?.let { m ->
            cnNumber(m.groupValues[1])?.let { date = today.plusDays(it.toLong()) }
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        Regex("(这|本)?月底").find(text)?.let { m ->
            date = today.withDayOfMonth(today.lengthOfMonth())
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        Regex("(\\d{1,2})月(\\d{1,2})[日号]?").find(text)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val da = m.groupValues[2].toInt()
            runCatching {
                var d = LocalDate.of(today.year, mo, da)
                if (d.isBefore(today)) d = d.plusYears(1)
                date = d
            }
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(text)?.let { m ->
            runCatching { date = LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        // M/D、M-D（放在 ISO 之后，否则 2026-10-24 会被切成 10-24）
        Regex("(\\d{1,2})[/-](\\d{1,2})").find(text)?.let { m ->
            runCatching {
                var d = LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                if (d.isBefore(today)) d = d.plusYears(1)
                date = d
            }
            text = eat(text, m.value, QuickTokenKind.DATE)
        }
        // 「N号」= 本月 N 日，已过则下个月（「27号」）
        Regex("(\\d{1,2})\\s*号").find(text)?.let { m ->
            runCatching {
                var d = LocalDate.of(today.year, today.month, m.groupValues[1].toInt())
                if (!d.isAfter(today)) d = d.plusMonths(1)
                date = d
                text = eat(text, m.value, QuickTokenKind.DATE)
            }
        }
        // 时间
        Regex("(早上|上午|中午|下午|傍晚|晚上|凌晨)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*(?:点|:|：)\\s*(半|一刻|三刻|\\d{1,2}(?![A-Za-z]))?\\s*分?").find(text)?.let { m ->
            val period = m.groupValues[1]
            var h = cnNumber(m.groupValues[2]) ?: -1
            val mm =
                when (val g = m.groupValues[3]) {
                    "半" -> 30
                    "一刻" -> 15
                    "三刻" -> 45
                    "" -> 0
                    else -> g.toIntOrNull() ?: 0
                }
            if (h in 0..24) {
                if ((period == "下午" || period == "晚上" || period == "傍晚") && h < 12) h += 12
                if (period == "中午" && h < 11) h += 12
                // 「今晚8点」：日期词已经暗示了下午，别把 8 点当成早上八点
                if (period.isEmpty() && impliedPm && h in 1..11) h += 12
                if (h == 24) h = 0
                time = LocalTime.of(h, mm.coerceIn(0, 59))
                text = eat(text, m.value, QuickTokenKind.TIME)
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
        return QuickAddResult(title, due, hasTime, priority, listName, tags, courseName, tokens)
    }

    /**
     * 多行粘贴（群通知、老师发的作业清单）：每行一条；缩进或 `- ` 开头的行是上一条的子任务；
     * `1.`、`1、`、`•` 这类序号会被去掉。
     */
    fun parseLines(
        input: String,
        now: LocalDateTime,
    ): List<QuickBatchItem> {
        val out = mutableListOf<QuickBatchItem>()
        var lastTop: Int? = null
        for (raw in input.lines()) {
            if (raw.isBlank()) continue
            val indented = raw.startsWith(" ") || raw.startsWith("\t") || raw.startsWith("　")
            val dash = Regex("^\\s*[-*]\\s+").containsMatchIn(raw)
            val body = raw.trim().replace(Regex("^([-*•·]|\\d{1,2}[.、)）])\\s*"), "").trim()
            if (body.isEmpty()) continue
            val parent = if ((indented || dash) && lastTop != null) lastTop else null
            out += QuickBatchItem(parse(body, now), parent)
            if (parent == null) lastTop = out.lastIndex
        }
        return out
    }
}

/** 多行解析的一条；[parentIndex] 指向同一批里的父条目（子任务）。 */
data class QuickBatchItem(
    val result: QuickAddResult,
    val parentIndex: Int?,
)
