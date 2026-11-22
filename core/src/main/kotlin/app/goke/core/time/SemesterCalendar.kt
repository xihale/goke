package app.goke.core.time

import app.goke.core.model.PeriodTime
import app.goke.core.model.Semester
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 学期的周次 / 节次计算。周次 = floor((日期 − 第 1 周周一) / 7) + 1。 */
class SemesterCalendar(
    val semester: Semester,
) {
    private val base: LocalDate = semester.week1Monday.with(DayOfWeek.MONDAY)

    val totalWeeks: Int get() = semester.totalWeeks

    // 排序一次缓存：period()/startTime() 在每次课程展开里逐节调用，原来每口都重新 sortedBy
    val periods: List<PeriodTime> = semester.periods.sortedBy { it.index }

    /** 日期所在周次，学期开始前为 ≤0。 */
    fun weekOf(date: LocalDate): Int = Math.floorDiv(ChronoUnit.DAYS.between(base, date), 7L).toInt() + 1

    /** 第 week 周的星期 dow（1..7）对应的日期。 */
    fun dateOf(
        week: Int,
        dow: Int,
    ): LocalDate = base.plusDays(((week - 1) * 7L) + (dow - 1))

    fun weekStart(week: Int): LocalDate = dateOf(week, 1)

    fun weekEnd(week: Int): LocalDate = dateOf(week, 7)

    /** 今天所在周次，限制在 1..totalWeeks；返回是否在学期内。 */
    fun currentWeek(today: LocalDate): Int = weekOf(today).coerceIn(1, totalWeeks)

    fun isInSemester(date: LocalDate): Boolean = weekOf(date) in 1..totalWeeks

    fun period(index: Int): PeriodTime? = periods.firstOrNull { it.index == index }

    fun startTime(period: Int): LocalTime? = period(period)?.start

    fun endTime(period: Int): LocalTime? = period(period)?.end

    /** 起始时间落在哪一节（精确匹配开始时间，否则取最近的不晚于它的节次）。 */
    fun periodAtOrBefore(time: LocalTime): Int? = periods.lastOrNull { !it.start.isAfter(time) }?.index

    fun periodEndingAtOrAfter(time: LocalTime): Int? = periods.firstOrNull { !it.end.isBefore(time) }?.index
}

object WeekSets {
    fun all(total: Int): Set<Int> = (1..total).toSet()

    fun odd(total: Int): Set<Int> = (1..total).filter { it % 2 == 1 }.toSet()

    fun even(total: Int): Set<Int> = (1..total).filter { it % 2 == 0 }.toSet()

    fun range(
        from: Int,
        to: Int,
    ): Set<Int> = (from..to).toSet()

    /** 把周次集合格式化为 `1-16`、`1,3,5-9` 这样的语言无关文本。 */
    fun format(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val parts = mutableListOf<String>()
        var s = sorted[0]
        var p = s
        for (i in 1..sorted.size) {
            val cur = sorted.getOrNull(i)
            if (cur != null && cur == p + 1) {
                p = cur
                continue
            }
            parts +=
                if (s == p) {
                    "$s"
                } else if (p == s + 1) {
                    "$s,$p"
                } else {
                    "$s-$p"
                }
            if (cur != null) {
                s = cur
                p = cur
            }
        }
        return parts.joinToString(",")
    }

    /** 解析 `1-16`、`1,3,5-9`、`单`/`双`（相对 total）。无法解析的片段被忽略。 */
    fun parse(
        text: String,
        total: Int = 30,
    ): Set<Int> {
        val t = text.trim()
        if (t.isEmpty()) return emptySet()
        val result = linkedSetOf<Int>()
        for (raw in t.split(',', '，', ' ', '、')) {
            val part = raw.trim().removeSuffix("周")
            if (part.isEmpty()) continue
            val odd = part.endsWith("单")
            val even = part.endsWith("双")
            val core = part.removeSuffix("单").removeSuffix("双")
            val range = core.split('-', '–', '~', '－')
            val a = range.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
            val b = if (range.size > 1) range[1].trim().toIntOrNull() ?: continue else a
            for (w in minOf(a, b)..minOf(maxOf(a, b), total.coerceAtLeast(maxOf(a, b)))) {
                if (odd && w % 2 == 0) continue
                if (even && w % 2 == 1) continue
                result += w
            }
        }
        return result
    }
}
