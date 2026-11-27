package app.goke.core.jxfw

import app.goke.core.agenda.Agenda
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Course
import app.goke.core.model.CourseNotes
import app.goke.core.model.EventKind
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.time.SemesterCalendar
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import java.time.LocalTime

/** 一次同步的结果：要落库的变更集，加上给用户看的统计。 */
data class JxfwPlan(
    val changeSet: ChangeSet,
    val courseCount: Int = 0,
    val examCount: Int = 0,
    val exceptionCount: Int = 0,
    val extraCount: Int = 0,
    val skippedReason: String? = null,
) {
    val isNoop: Boolean get() = changeSet.isEmpty
}

/** 通用课程考试识别规则（本地编辑与同步共用，不含任何协议细节）。 */
object JxfwSync {
    const val COURSE_PREFIX = "jxfw-course/"
    const val EXCEP_PREFIX = "jxfw-excep/"
    const val EXAM_PREFIX = "jxfw-exam/"
    const val COURSE_EXAM_PREFIX = "jxfw-course-exam/"
    const val EXAM_COURSE_PREFIX = "jxfw-exam-course/"
    const val COURSE_EXAM_COURSE_PREFIX = "jxfw-remark-exam/"
    const val EXAM_COLOR_INDEX = 2

    private val EXAM_REMARK_INCLUDE_RE =
        Regex(
            """考试|测验|小测|机考|机试|笔试|口试|补考|重考|缓考|随堂考|结课考|实验考|(?:期中|期末|随堂|结课)(?:测试|考核)|实验考核|\b(?:midterm|final\s+exam|quiz)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val EXAM_REMARK_EXCLUDE_RE =
        Regex("""复习|答疑|辅导|讲评|讲解|动员|不考|免考|无考|取消""")

    /** 判断课程备注是否代表考试安排（如期中考试、随堂测验等），同时排除考前复习/答疑/讲评及软件测试等课程主题。 */
    fun isExamRemark(remark: String): Boolean {
        val text = remark.trim()
        if (text.isEmpty()) return false
        if (!EXAM_REMARK_INCLUDE_RE.containsMatchIn(text)) return false
        return !EXAM_REMARK_EXCLUDE_RE.containsMatchIn(text)
    }

    /** 根据课程名称或备注/详情判断是否应自动归为考试类型。 */
    fun shouldClassifyCourseAsExam(
        name: String,
        note: String,
    ): Boolean {
        if (isExamRemark(name)) return true
        if (note.isBlank()) return false
        return note.lines().any { line ->
            val content = CourseNotes.stripWeekPrefix(line)
            content.isNotEmpty() && isExamRemark(content)
        }
    }

    private val EXAM_PREFIX_CAPTURE_RE =
        Regex("""^((?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*(.*)$""")
    private val EXAM_NAME_PREFIX_RE =
        Regex("""^(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*""")
    private val EXAM_NAME_SUFFIX_RE =
        Regex("""\s*[:：·\-—]?\s*(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)$""")

    data class ExamDisplay(
        val badgeFull: String,
        val badgeShort: String,
        val title: String,
    )

    private fun shortenExamBadge(badgeFull: String): String =
        when {
            "期中" in badgeFull -> "期中"
            "期末" in badgeFull -> "期末"
            "随堂" in badgeFull -> "随堂"
            "结课" in badgeFull -> "结课"
            "补考" in badgeFull -> "补考"
            "重考" in badgeFull -> "重考"
            "缓考" in badgeFull -> "缓考"
            "机考" in badgeFull || "机试" in badgeFull || "上机" in badgeFull -> "机考"
            "实验" in badgeFull -> "实验"
            "测验" in badgeFull || "小测" in badgeFull || "测试" in badgeFull -> "测验"
            else -> badgeFull.take(2)
        }

    /** 拆分展示用考试徽标与去前缀后的干净课名，统一全半角括号。 */
    fun splitExamDisplay(
        rawName: String,
        fallbackBadge: String,
    ): ExamDisplay {
        val trimmed = rawName.trim()
        val m = EXAM_PREFIX_CAPTURE_RE.find(trimmed)
        val prefix =
            m
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val rest =
            m
                ?.groupValues
                ?.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val cleanTitle = (rest ?: trimmed).replace('（', '(').replace('）', ')')
        val badgeFull = prefix ?: fallbackBadge.trim().ifEmpty { "考试" }
        return ExamDisplay(
            badgeFull = badgeFull,
            badgeShort = shortenExamBadge(badgeFull),
            title = cleanTitle,
        )
    }

    private val VERTICAL_PAREN_RE = Regex("""^\([A-Za-z0-9+\-]{1,2}\)""")
    private val VERTICAL_ASCII_RE = Regex("""^[A-Za-z0-9+\-]{1,3}""")

    /** 将标题按直立纵中横切分为竖排 token：短括号后缀 `(3)` 与 `<=3` 位 ASCII 串保持整体直立。 */
    fun tokenizeVerticalTitle(raw: String): List<String> {
        val norm = raw.trim().replace('（', '(').replace('）', ')')
        if (norm.isEmpty()) return emptyList()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < norm.length) {
            val rest = norm.substring(i)
            val paren = VERTICAL_PAREN_RE.find(rest)
            if (paren != null) {
                tokens += paren.value
                i += paren.value.length
                continue
            }
            val ascii = VERTICAL_ASCII_RE.find(rest)
            if (ascii != null) {
                tokens += ascii.value
                i += ascii.value.length
                continue
            }
            val ch = norm[i]
            if (!ch.isWhitespace()) {
                tokens += ch.toString()
            }
            i++
        }
        return tokens
    }

    /** 格式化为竖排多行文本，超出 [maxTokens] 时末尾收为 `…`。 */
    fun formatVerticalText(
        raw: String,
        maxTokens: Int,
    ): String {
        val tokens = tokenizeVerticalTitle(raw)
        if (tokens.isEmpty() || maxTokens <= 0) return ""
        val limited =
            if (tokens.size > maxTokens) {
                if (maxTokens == 1) listOf("…") else tokens.take(maxTokens - 1) + "…"
            } else {
                tokens
            }
        return limited.joinToString("\n")
    }

    /** 归一化课程/考试名：剥离前后缀的考试标记、统一全半角括号与空白，用于随堂考/同课考试去重与升层。 */
    fun normalizeCourseNameForExam(raw: String): String {
        var s =
            raw
                .trim()
                .replace('（', '(')
                .replace('）', ')')
                .replace(Regex("""\s+"""), "")
        var prev: String
        do {
            prev = s
            s = s.replace(EXAM_NAME_PREFIX_RE, "").replace(EXAM_NAME_SUFFIX_RE, "").trim()
        } while (s != prev && s.isNotEmpty())
        return s.lowercase()
    }

    /** 判断考试与常规课程是否属于同一门课。 */
    fun isSameCourseForExam(
        examName: String,
        courseName: String,
    ): Boolean {
        val a = normalizeCourseNameForExam(examName)
        val b = normalizeCourseNameForExam(courseName)
        return a.isNotEmpty() && a == b
    }

    private val EXAM_SEAT_RE = Regex("""座位号\s*([^\s·,，;；]+)""")

    fun extractExamSeat(note: String): String? =
        EXAM_SEAT_RE
            .find(note)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun formatExamLocation(
        location: String,
        note: String,
        seatSuffix: String,
    ): String? {
        val loc = location.trim()
        val seat =
            extractExamSeat(note)?.let { s ->
                val suffix = seatSuffix.trim()
                if (suffix.isEmpty()) s else "$s$suffix"
            }
        return when {
            loc.isNotEmpty() && seat != null -> "$loc · $seat"
            loc.isNotEmpty() -> loc
            seat != null -> seat
            else -> null
        }
    }

    /** 将旧版导入的考试待办迁移为考试课程，避免历史考试在待办里显示为逾期。 */
    fun migrateExamTasksToCourses(
        snap: Snapshot,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet? {
        val legacy =
            snap.tasks.filter {
                it.deletedAt == null &&
                    it.source == Source.IMPORT &&
                    (it.id.startsWith(EXAM_PREFIX) || it.id.startsWith(COURSE_EXAM_PREFIX))
            }
        if (legacy.isEmpty()) return null
        val sem = snap.currentSemester
        val cal = sem?.let { SemesterCalendar(it) }
        val maxP = cal?.periods?.lastOrNull()?.index ?: 1
        val changes = mutableListOf<Change>()
        for (t in legacy) {
            changes += Change.Remove(t)
            if (sem == null || cal == null) continue
            val isOfficial = t.id.startsWith(EXAM_PREFIX)
            val targetId =
                if (isOfficial) {
                    EXAM_COURSE_PREFIX + t.id.removePrefix(EXAM_PREFIX)
                } else {
                    COURSE_EXAM_COURSE_PREFIX + t.id.removePrefix(COURSE_EXAM_PREFIX)
                }
            if (snap.courses.any { it.id == targetId }) continue

            var week: Int? = null
            var dow: Int? = null
            var sp = 1.coerceAtMost(maxP)
            var ep = sp
            var startTime: LocalTime? = null
            var endTime: LocalTime? = null
            var resolved = false

            if (!isOfficial) {
                val parts = t.id.removePrefix(COURSE_EXAM_PREFIX).split("/")
                val w = parts.getOrNull(1)?.toIntOrNull()
                val d = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..7 }
                val ps =
                    parts
                        .getOrNull(3)
                        ?.split('-', '_')
                        ?.mapNotNull { it.toIntOrNull() }
                        .orEmpty()
                if (w != null && d != null && ps.isNotEmpty()) {
                    week = w
                    dow = d
                    sp = ps.min().coerceIn(1, maxP)
                    ep = ps.max().coerceIn(sp, maxP)
                    resolved = true
                }
            }
            if (!resolved) {
                val date = t.blockStart?.toLocalDate() ?: t.startDate ?: t.due?.toLocalDate()
                if (date != null) {
                    week = cal.weekOf(date)
                    dow = date.dayOfWeek.value
                    val st = t.blockStart?.toLocalTime()
                    val mins = t.blockMinutes
                    if (st != null && mins != null) {
                        val et = t.blockStart.plusMinutes(mins.toLong()).toLocalTime()
                        val rows = Agenda.blockRows(cal.periods, st, et)
                        sp = (rows?.first ?: 1).coerceIn(1, maxP)
                        ep = (rows?.last ?: sp).coerceIn(sp, maxP)
                        startTime = st
                        endTime = et
                    }
                    resolved = true
                }
            }
            if (!resolved || week == null || dow == null) continue

            changes +=
                Change.Add(
                    Course(
                        id = targetId,
                        semesterId = sem.id,
                        name = t.title,
                        room = t.extra["location"].orEmpty(),
                        dayOfWeek = dow,
                        startPeriod = sp,
                        endPeriod = ep,
                        weeks = setOf(week),
                        colorIndex = EXAM_COLOR_INDEX,
                        remindMinutes = t.remindMinutes,
                        remindMinutesList = t.remindMinutesList,
                        note = t.note,
                        source = Source.IMPORT,
                        kind = EventKind.EXAM,
                        remindPersistent = t.remindPersistent,
                        startTime = startTime,
                        endTime = endTime,
                    ),
                )
        }
        return ChangeSet(ids.next(), "教务考试转为课程", Source.IMPORT, now, changes)
    }
}
