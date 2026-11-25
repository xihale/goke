package app.goke.core.jxfw

import kotlinx.serialization.Serializable

/** 单项体测成绩（身高体重 BMI、肺活量、50 米跑等）。 */
@Serializable
data class TzcsItem(
    val name: String,
    val rawValue: String = "",
    val itemScore: String = "",
    val grade: String = "",
    val weightedScore: Double? = null,
    val bonusScore: Double? = null,
)

/** 单个学年的体测成绩报告。 */
@Serializable
data class TzcsYearReport(
    val year: Int,
    val studentName: String = "",
    val studentNo: String = "",
    val totalScore: String = "",
    val totalGrade: String = "",
    val items: List<TzcsItem> = emptyList(),
    val historyYears: List<Int> = emptyList(),
)

/** 历年体测汇总（学年数、已出成绩学年数、历年最高分与最新已出成绩报告）。 */
data class TzcsSummary(
    val yearCount: Int,
    val recordedYearCount: Int,
    val bestScore: Double?,
    val bestYear: Int?,
    val latestReport: TzcsYearReport?,
)

/** 体测报告 UI 汇总与过滤辅助函数（不含任何协议解析）。 */
object TzcsParse {
    private val MALE_ONLY_ITEM_KEYS = listOf("1000米", "引体向上")
    private val FEMALE_ONLY_ITEM_KEYS = listOf("800米", "仰卧起坐")

    /** 该学年是否已有任意有效体测成绩记录（用于默认选中最新已出成绩的学年）。 */
    fun hasRecordedScores(report: TzcsYearReport): Boolean {
        if (report.totalGrade.isNotBlank()) return true
        val totalNum = report.totalScore.toDoubleOrNull()
        if (totalNum != null && totalNum > 0.0) return true
        return report.items.any { it.isRecorded() }
    }

    /** 过滤各学年报告中不适用的项目，返回仅供展示的新列表（不改动缓存原文）。 */
    fun filterApplicableItems(reports: List<TzcsYearReport>): List<TzcsYearReport> {
        val opposite =
            when {
                reports.any { r -> r.items.any { it.isRecorded() && MALE_ONLY_ITEM_KEYS.any { k -> k in it.name } } } -> FEMALE_ONLY_ITEM_KEYS
                reports.any { r -> r.items.any { it.isRecorded() && FEMALE_ONLY_ITEM_KEYS.any { k -> k in it.name } } } -> MALE_ONLY_ITEM_KEYS
                else -> emptyList()
            }
        return reports.map { r ->
            r.copy(items = r.items.filterNot { item -> item.isEmptyRow() || opposite.any { it in item.name } })
        }
    }

    private fun TzcsItem.isRecorded(): Boolean = rawValue.isNotBlank() || itemScore.isNotBlank() || grade.isNotBlank()

    private fun TzcsItem.isEmptyRow(): Boolean = rawValue.isBlank() && itemScore.isBlank() && grade.isBlank() && weightedScore == null && bonusScore == null

    /** 按学年合并新旧体测报告（同一年份以新拉取数据为准），按学年倒序排列。 */
    fun mergeReports(
        existing: List<TzcsYearReport>,
        incoming: List<TzcsYearReport>,
    ): List<TzcsYearReport> {
        if (incoming.isEmpty()) return existing.sortedByDescending { it.year }
        val byYear = linkedMapOf<Int, TzcsYearReport>()
        for (r in existing) byYear[r.year] = r
        for (r in incoming) byYear[r.year] = r
        return byYear.values.sortedByDescending { it.year }
    }

    /** 汇总历年体测报告的最高总分与有效学年统计。 */
    fun summarizeReports(reports: List<TzcsYearReport>): TzcsSummary {
        val sorted = reports.sortedByDescending { it.year }
        val recorded = sorted.filter(::hasRecordedScores)
        var bestScore: Double? = null
        var bestYear: Int? = null
        for (r in recorded) {
            val score = r.totalScore.toDoubleOrNull() ?: continue
            if (score > 0.0 && (bestScore == null || score > bestScore)) {
                bestScore = score
                bestYear = r.year
            }
        }
        return TzcsSummary(
            yearCount = sorted.size,
            recordedYearCount = recorded.size,
            bestScore = bestScore,
            bestYear = bestYear,
            latestReport = recorded.firstOrNull() ?: sorted.firstOrNull(),
        )
    }
}
