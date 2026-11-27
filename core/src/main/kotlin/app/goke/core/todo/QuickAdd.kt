package app.goke.core.todo

import app.goke.core.model.Reminders
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class QuickAddConfig(
    /** 触发普通/持续提醒的关键词（去空白；为空表示禁用关键词简写）。默认「提醒」。 */
    val remindKeyword: String = DEFAULT_REMIND_KEYWORD,
    /** 命中普通「提醒」时的默认提前分钟列表（自动去重排序，空则回退到 [DEFAULT_REMIND_MINUTES]）。 */
    val remindMinutesList: List<Int> = DEFAULT_REMIND_MINUTES,
    /** 命中「提醒!」/「提醒！」时的提前分钟列表（空则沿用 [effectiveRemindMinutes]）。 */
    val persistentRemindMinutesList: List<Int> = DEFAULT_REMIND_MINUTES,
    /** 「提醒!」/「提醒！」是否开启持续提醒（一直响，直到完成或关掉）。 */
    val bangPersistent: Boolean = true,
) {
    val normalizedKeyword: String get() = remindKeyword.trim()
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(null, remindMinutesList).ifEmpty { DEFAULT_REMIND_MINUTES }
    val effectivePersistentRemindMinutes: List<Int>
        get() = Reminders.normalize(null, persistentRemindMinutesList).ifEmpty { effectiveRemindMinutes }

    companion object {
        const val DEFAULT_REMIND_KEYWORD = "提醒"

        /** 15 分钟前 + 准时（经 Reminders.normalize 升序归一化为 [0, 15]）。 */
        val DEFAULT_REMIND_MINUTES: List<Int> = listOf(0, 15)
        val DEFAULT = QuickAddConfig()
    }
}

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
    /** 由快速输入解析出的提前提醒分钟列表（已归一化升序）；空表示未写提醒语法。 */
    val remindMinutesList: List<Int> = emptyList(),
    /** 是否开启持续提醒（需配合非空 [remindMinutesList] 生效）。 */
    val remindPersistent: Boolean = false,
)

enum class QuickTokenKind { DATE, TIME, PRIORITY, LIST, TAG, COURSE, REMIND }

/** 输入里被识别的一段原文。 */
data class QuickToken(
    val text: String,
    val kind: QuickTokenKind,
)

/** 一行快速输入：识别日期 / 时间 / 清单（#）/ 标签（@）/ 优先级（!高 !中 !低）/ 提醒。 */
object QuickAdd {
    private val CN = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

    private const val REMIND_LB = "(^|[\\s,，。.;；:：、(\\[【（])"
    private const val REMIND_BANG = "([!！](?!(?:高|中|低|[123])(?![0-9])))?"
    private const val REMIND_RB = "(?=$|[\\s,，。.;；:：、)\\]】）#@/]|[!！](?:高|中|低|[123])(?![0-9]))"
    private const val DUR_NUM = "(\\d{1,4}|[一二两三四五六七八九十]{1,3})"
    private const val DUR_UNIT = "(分钟|分|小时|时|天|min|m|h|d)"
    private const val PERIOD = "(清晨|早晨|早间|早上|上午|中午|午间|午后|下午|傍晚|晚间|晚上|夜间|夜里|半夜|深夜|凌晨)"
    private const val HOUR_PART = "(\\d{1,2}|[一二两三四五六七八九十]{1,3})"
    private const val CN_MIN = "(?:[零〇][一二三四五六七八九]|[一二三四五]?十[一二三四五六七八九]?|[一二三四五六七八九](?=分))"
    private const val MIN_PART = "(半|一刻|两刻|二刻|三刻|\\d{1,2}(?![A-Za-z])|$CN_MIN)"

    private val PM_PERIODS = setOf("下午", "午后", "傍晚", "晚上", "晚间", "夜间", "夜里")
    private val NIGHT_TWELVE_PERIODS = setOf("晚上", "夜间", "夜里")
    private val EARLY_MORNING_PERIODS = setOf("凌晨", "半夜", "深夜")
    private val LATE_NIGHT_PERIODS = setOf("半夜", "深夜")
    private val NOON_PERIODS = setOf("中午", "午间")

    fun cnNumber(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return null
        val cleaned = s.trimStart('零', '〇')
        if (cleaned.isEmpty()) return 0
        if (cleaned == "十") return 10
        if (cleaned.startsWith("十")) {
            val b = cleaned.getOrNull(1) ?: return 10
            return if (cleaned.length == 2) 10 + (CN[b] ?: return null) else null
        }
        val ten = cleaned.indexOf('十')
        if (ten > 0) {
            val a = CN[cleaned[0]] ?: return null
            val b =
                when {
                    ten + 1 == cleaned.length -> 0
                    ten + 2 == cleaned.length -> CN[cleaned[ten + 1]] ?: return null
                    else -> return null
                }
            return a * 10 + b
        }
        return if (cleaned.length == 1) CN[cleaned[0]] else null
    }

    private fun parseDurationMinutes(
        numStr: String,
        unit: String,
    ): Int? {
        val n = cnNumber(numStr) ?: return null
        val mins =
            when (unit.lowercase()) {
                "分钟", "分", "min", "m" -> n
                "小时", "时", "h" -> n * 60
                "天", "d" -> n * 1440
                else -> return null
            }
        return mins.takeIf { it in 0..10080 }
    }

    private fun adjustHour(
        period: String,
        rawHour: Int,
        impliedPm: Boolean,
    ): Int? {
        if (rawHour !in 0..24) return null
        var h = rawHour
        when {
            period in PM_PERIODS -> {
                if (period in NIGHT_TWELVE_PERIODS && h == 12) {
                    h = 0
                } else if (h in 1..11) {
                    h += 12
                }
            }

            period in EARLY_MORNING_PERIODS -> {
                if (h == 12) {
                    h = 0
                } else if (period in LATE_NIGHT_PERIODS && h in 10..11) {
                    h += 12
                }
            }

            period in NOON_PERIODS -> {
                if (h in 1..10) h += 12
            }

            period.isEmpty() && impliedPm && h in 1..11 -> {
                h += 12
            }
        }
        if (h == 24) h = 0
        return h.takeIf { it in 0..23 }
    }

    fun parse(
        input: String,
        now: LocalDateTime,
        config: QuickAddConfig = QuickAddConfig.DEFAULT,
    ): QuickAddResult {
        var text = input.trim()
        var priority = 0
        var listName: String? = null
        val tags = mutableListOf<String>()
        val tokens = mutableListOf<QuickToken>()
        var courseName: String? = null
        val remindOffsets = mutableListOf<Int>()
        var remindPersistent = false

        fun eat(
            src: String,
            value: String,
            kind: QuickTokenKind,
        ): String {
            tokens += QuickToken(value.trim(), kind)
            return src.replace(value, " ")
        }

        fun eatRange(
            src: String,
            range: IntRange,
            kind: QuickTokenKind,
        ): String {
            tokens += QuickToken(src.substring(range).trim(), kind)
            return src.replaceRange(range, " ")
        }

        val kw = config.normalizedKeyword
        if (kw.isNotEmpty()) {
            val escKw = Regex.escape(kw)
            val prefixRegex =
                Regex(
                    "${REMIND_LB}(提前\\s*(?:(半)\\s*(?:小时|时)|${DUR_NUM}\\s*${DUR_UNIT})\\s*${escKw}${REMIND_BANG})${REMIND_RB}",
                    RegexOption.IGNORE_CASE,
                )
            val suffixRegex =
                Regex(
                    "${REMIND_LB}(${escKw}\\s*(?:(半)\\s*(?:小时|时)|${DUR_NUM}\\s*${DUR_UNIT})${REMIND_BANG})${REMIND_RB}",
                    RegexOption.IGNORE_CASE,
                )
            val standaloneRegex = Regex("${REMIND_LB}(${escKw}${REMIND_BANG})${REMIND_RB}")

            fun consumeOffsetPattern(regex: Regex) {
                var from = 0
                while (from <= text.length) {
                    val m = regex.find(text, from) ?: break
                    val mins =
                        if (m.groupValues[3] == "半") {
                            30
                        } else {
                            parseDurationMinutes(m.groupValues[4], m.groupValues[5])
                        }
                    val tokenRange = m.groups[2]!!.range
                    if (mins == null) {
                        from = tokenRange.last + 1
                        continue
                    }
                    remindOffsets += mins
                    if (m.groupValues[6].isNotEmpty() && config.bangPersistent) {
                        remindPersistent = true
                    }
                    text = eatRange(text, tokenRange, QuickTokenKind.REMIND)
                    from = 0
                }
            }

            consumeOffsetPattern(prefixRegex)
            consumeOffsetPattern(suffixRegex)

            while (true) {
                val m = standaloneRegex.find(text) ?: break
                val hasBang = m.groupValues[3].isNotEmpty()
                if (hasBang) {
                    remindOffsets += config.effectivePersistentRemindMinutes
                    if (config.bangPersistent) remindPersistent = true
                } else {
                    remindOffsets += config.effectiveRemindMinutes
                }
                text = eatRange(text, m.groups[2]!!.range, QuickTokenKind.REMIND)
            }
        }

        Regex("(?<![!！])[!！](高|中|低|[123])(?![0-9])").find(text)?.let {
            priority =
                when (it.groupValues[1]) {
                    "高", "3" -> 3
                    "中", "2" -> 2
                    else -> 1
                }
            text = eatRange(text, it.range, QuickTokenKind.PRIORITY)
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
        // 时间：先解析「差X分Y点」，再解析标准中/混合时间
        var parsedExplicitTime = false
        Regex("$PERIOD?\\s*差\\s*(一刻|半|\\d{1,2}|[零〇一二两三四五六七八九十]{1,3})\\s*(?:分钟|分)?\\s*(?:到)?\\s*$PERIOD?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*(?:点钟?|时)")
            .find(text)
            ?.let { m ->
                val period = m.groupValues[1].ifEmpty { m.groupValues[3] }
                val subMin =
                    when (val g = m.groupValues[2]) {
                        "一刻" -> 15
                        "半" -> 30
                        else -> cnNumber(g)
                    }
                val targetHour = cnNumber(m.groupValues[4])?.let { adjustHour(period, it, impliedPm) }
                if (subMin != null && subMin in 1..59 && targetHour != null) {
                    time = LocalTime.of(targetHour, 0).minusMinutes(subMin.toLong())
                    text = eatRange(text, m.range, QuickTokenKind.TIME)
                    parsedExplicitTime = true
                }
            }
        if (!parsedExplicitTime) {
            Regex("$PERIOD?\\s*$HOUR_PART\\s*(?:(?:点|时)\\s*(?:过\\s*)?(?:$MIN_PART\\s*(?:分钟|分)?|整|钟)?|(?:[:：])\\s*(\\d{1,2}(?![A-Za-z]))\\s*(?:分钟|分)?)")
                .find(text)
                ?.let { m ->
                    val period = m.groupValues[1]
                    val hour = cnNumber(m.groupValues[2])?.let { adjustHour(period, it, impliedPm) }
                    val minStr = m.groupValues[3].ifEmpty { m.groupValues[4] }
                    val mm =
                        when (minStr) {
                            "半", "两刻", "二刻" -> 30
                            "一刻" -> 15
                            "三刻" -> 45
                            "" -> 0
                            else -> cnNumber(minStr)?.takeIf { it in 0..59 }
                        }
                    if (hour != null && mm != null) {
                        time = LocalTime.of(hour, mm)
                        text = eatRange(text, m.range, QuickTokenKind.TIME)
                    }
                }
        }
        val hasTime = time != null
        var due: LocalDateTime? = null
        if (date != null || time != null) {
            var d = date ?: today
            if (date == null && time != null && LocalDateTime.of(d, time).isBefore(now)) d = d.plusDays(1)
            due = LocalDateTime.of(d, time ?: LocalTime.MIDNIGHT)
        }
        val finalRemind = Reminders.normalize(null, remindOffsets)
        val finalPersistent = remindPersistent && finalRemind.isNotEmpty()
        val title = text.replace(Regex("\\s+"), " ").trim().ifEmpty { input.trim() }
        return QuickAddResult(
            title = title,
            due = due,
            dueHasTime = hasTime,
            priority = priority,
            listName = listName,
            tags = tags,
            courseName = courseName,
            tokens = tokens,
            remindMinutesList = finalRemind,
            remindPersistent = finalPersistent,
        )
    }

    /**
     * 多行粘贴（群通知、老师发的作业清单）：每行一条；缩进或 `- ` 开头的行是上一条的子任务；
     * `1.`、`1、`、`•` 这类序号会被去掉。
     */
    fun parseLines(
        input: String,
        now: LocalDateTime,
        config: QuickAddConfig = QuickAddConfig.DEFAULT,
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
            out += QuickBatchItem(parse(body, now, config), parent)
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
