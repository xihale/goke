package app.goke.core.jxfw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** 课程成绩条目（供成绩查询与加权均绩统计展示）。 */
@Serializable
data class JxfwGrade(
    val term: String,
    val code: String = "",
    val name: String,
    val credit: Double? = null,
    val score: String = "",
    val gpa: Double? = null,
    val category: String = "",
    val studyMode: String = "",
    val examType: String = "",
    val status: String = "",
) {
    val filterKey: String get() = "$term|$code|$name"
}

/** 教务通知来源：公开教务处官网公告或登录态站内个人消息。 */
@Serializable
enum class JxfwNoticeSource {
    PUBLIC,
    INTERNAL,
}

/** 教务通知条目（数据源无关；仅本机缓存，不入 Repository/Sync）。 */
@Serializable
data class JxfwNotice(
    val id: String,
    val title: String,
    val date: String = "",
    val url: String = "",
    val source: JxfwNoticeSource = JxfwNoticeSource.PUBLIC,
)

/** 教务通知增量去重与多源合并纯函数工具（不含任何协议与网络细节）。 */
object JxfwNoticeDiff {
    const val MAX_SEEN_IDS = 100
    const val MAX_CACHED_NOTICES = 30

    data class DiffResult(
        val newNotices: List<JxfwNotice>,
        val updatedSeenIds: List<String>,
    )

    /**
     * 计算新增通知并更新已见 ID 集合（保序去重，最多保留 [maxSeenIds] 条）。
     *
     * - [isInitialBaseline] 为 true 时视为首次基线：全部写入已见集合，不产生新增提醒。
     * - 严格按 [JxfwNotice.id] 去重，不假设列表日期单调递减（兼容置顶公告）。
     */
    fun diffNew(
        incoming: List<JxfwNotice>,
        seenIds: List<String>,
        isInitialBaseline: Boolean,
        maxSeenIds: Int = MAX_SEEN_IDS,
    ): DiffResult {
        val seenSet = seenIds.toHashSet()
        val dedupedIncoming = incoming.filter { it.id.isNotBlank() }.distinctBy { it.id }
        val newOnes =
            if (isInitialBaseline) {
                emptyList()
            } else {
                dedupedIncoming.filter { it.id !in seenSet }
            }
        val merged = LinkedHashSet<String>()
        for (item in dedupedIncoming) {
            merged += item.id
        }
        for (id in seenIds) {
            if (id.isNotBlank()) merged += id
        }
        val limit = maxSeenIds.coerceAtLeast(dedupedIncoming.size)
        return DiffResult(
            newNotices = newOnes,
            updatedSeenIds = merged.take(limit),
        )
    }

    /** 合并公开公告与站内消息：站内消息优先置前，按 [JxfwNotice.id] 去重，保留最近 [maxItems] 条。 */
    fun mergeSources(
        publicNotices: List<JxfwNotice>,
        internalNotices: List<JxfwNotice>,
        maxItems: Int = MAX_CACHED_NOTICES,
    ): List<JxfwNotice> {
        val seen = HashSet<String>()
        val result = ArrayList<JxfwNotice>()
        for (item in internalNotices) {
            if (item.id.isNotBlank() && seen.add(item.id)) result += item
        }
        for (item in publicNotices) {
            if (item.id.isNotBlank() && seen.add(item.id)) result += item
        }
        return result.take(maxItems)
    }
}

/** 成绩汇总统计（总学分、已修/通过学分、参评学分、加权均绩、课程数）。 */
data class JxfwGradeSummary(
    val totalCredits: Double,
    val gpaCredits: Double,
    val weightedGpa: Double?,
    val count: Int,
    val earnedCredits: Double = 0.0,
)

/** 学年学期与成绩统计纯本地计算工具（不含任何协议解析）。 */
object JxfwParse {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * 由日期推导学年学期代码：8–12 月为当年第 1 学期（YYYY01），1 月仍属上学期开头，
     * 2–7 月为第 2 学期（(YYYY-1)02）。例：2026-10-25 → "202601"，2027-01-05 → "202601"，2027-03-01 → "202602"。
     */
    fun currentTerm(today: LocalDate): String =
        when {
            today.monthValue >= 8 -> "${today.year}01"
            today.monthValue == 1 -> "${today.year - 1}01"
            else -> "${today.year - 1}02"
        }

    private val FAIL_SCORE_TOKENS =
        setOf("不及格", "不合格", "不通过", "未通过", "缺考", "旷考", "作弊", "违纪", "取消资格", "缓考")

    private val PLACEHOLDER_ZERO_SCORES = setOf("", "0", "0.0", "缓考", "缺考", "旷考")

    /** 成绩条目是否明确标记为缓考。 */
    fun isDeferredGrade(grade: JxfwGrade): Boolean =
        grade.status.contains("缓考") ||
            grade.examType.contains("缓考") ||
            grade.score.trim() == "缓考"

    /** 成绩条目是否尚无分数（既无成绩文本也无绩点，如待出分或缓考占位）。 */
    fun isUngradedGrade(grade: JxfwGrade): Boolean = grade.score.isBlank() && grade.gpa == null

    /** 找出被后续通过成绩替代的缓考/零分占位记录 `filterKey`。 */
    fun supersededGradeKeys(grades: List<JxfwGrade>): Set<String> {
        if (grades.size < 2) return emptySet()
        val result = HashSet<String>()
        val byCourse = grades.groupBy { it.code.trim().ifEmpty { it.name.trim() } }
        for ((courseKey, group) in byCourse) {
            if (courseKey.isEmpty() || group.size < 2) continue
            if (group.none { isPassedGrade(it) }) continue
            for (g in group) {
                val isZeroPlaceholder = g.gpa == 0.0 && g.score.trim() in PLACEHOLDER_ZERO_SCORES
                if (isDeferredGrade(g) || isUngradedGrade(g) || isZeroPlaceholder) {
                    result += g.filterKey
                }
            }
        }
        return result
    }

    /** 判断某条成绩是否已取得学分（正学分且绩点 > 0 或成绩通过）。 */
    fun isPassedGrade(grade: JxfwGrade): Boolean {
        if (isDeferredGrade(grade)) return false
        val c = grade.credit ?: return false
        if (c <= 0.0) return false
        grade.gpa?.let { return it > 0.0 }
        val s = grade.score.trim()
        if (s.isEmpty()) return false
        s.toDoubleOrNull()?.let { return it >= 60.0 }
        return s !in FAIL_SCORE_TOKENS
    }

    /** 列出成绩列表里所有非空课程类别与修读方式（去重保序）。 */
    fun gradeCategories(grades: List<JxfwGrade>): List<String> =
        grades
            .flatMap { listOf(it.category.trim(), it.studyMode.trim()) }
            .filter { it.isNotEmpty() }
            .distinct()

    /** 按排除键与排除类别过滤成绩列表。 */
    fun filterGrades(
        grades: List<JxfwGrade>,
        excludedKeys: Set<String> = emptySet(),
        excludedCategories: Set<String> = emptySet(),
    ): List<JxfwGrade> {
        if (excludedKeys.isEmpty() && excludedCategories.isEmpty()) return grades
        return grades.filter { g ->
            g.filterKey !in excludedKeys &&
                (g.category.isBlank() || g.category !in excludedCategories) &&
                (g.studyMode.isBlank() || g.studyMode !in excludedCategories)
        }
    }

    /** 汇总成绩列表的总学分、已修学分、参评绩点学分、学分加权均绩与课程数量（自动跳过缓考、未出分与已替代记录）。 */
    @JvmOverloads
    fun summarizeGrades(
        grades: List<JxfwGrade>,
        excludedKeys: Set<String> = emptySet(),
        excludedCategories: Set<String> = emptySet(),
        supersededKeys: Set<String> = supersededGradeKeys(grades),
    ): JxfwGradeSummary {
        val active = filterGrades(grades, excludedKeys, excludedCategories)
        var totalCredits = 0.0
        var earnedCredits = 0.0
        var gpaCredits = 0.0
        var weightedPoints = 0.0
        for (g in active) {
            if (isDeferredGrade(g) || isUngradedGrade(g) || g.filterKey in supersededKeys) continue
            val c = g.credit ?: continue
            if (c <= 0.0) continue
            totalCredits += c
            if (isPassedGrade(g)) earnedCredits += c
            val p = g.gpa ?: continue
            gpaCredits += c
            weightedPoints += c * p
        }
        val weightedGpa = if (gpaCredits > 0.0) weightedPoints / gpaCredits else null
        return JxfwGradeSummary(
            totalCredits = totalCredits,
            gpaCredits = gpaCredits,
            weightedGpa = weightedGpa,
            count = active.size,
            earnedCredits = earnedCredits,
        )
    }

    /** 学年学期代码转中文标签："202601" → "2026-2027 第一学期"，未知格式原样返回。 */
    fun termLabel(termCode: String): String {
        val trimmed = termCode.trim()
        if (trimmed.length != 6) return termCode
        val year = trimmed.substring(0, 4).toIntOrNull() ?: return termCode
        return when (trimmed.substring(4, 6)) {
            "01" -> "$year-${year + 1} 第一学期"
            "02" -> "$year-${year + 1} 第二学期"
            else -> termCode
        }
    }
}
