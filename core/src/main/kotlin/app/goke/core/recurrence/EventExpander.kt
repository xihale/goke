package app.goke.core.recurrence

import app.goke.core.model.Event
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class EventOccurrence(
    val event: Event,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val date: LocalDate get() = start.toLocalDate()
}

/** 重复规则展开（RRULE 常用子集）。 */
object RecurrenceEngine {
    private const val MAX_ITER = 6000

    /**
     * 生成以 [anchor] 为首次出现日期的规则，在 [from, to]（含）内的所有日期。
     * count 从首次出现起算；exDates 中的日期被排除（不占 count）。
     */
    fun dates(
        rule: Recurrence,
        anchor: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val interval = rule.interval.coerceAtLeast(1)
        val result = mutableListOf<LocalDate>()
        var produced = 0
        val limit = rule.until?.let { minOf(it, to) } ?: to

        fun accept(d: LocalDate): Boolean {
            // 返回 false 表示应停止
            if (d.isBefore(anchor)) return true
            if (rule.until != null && d.isAfter(rule.until)) return false
            if (rule.count != null && produced >= rule.count) return false
            produced++
            if (d !in rule.exDates && !d.isBefore(from) && !d.isAfter(to)) result += d
            return !d.isAfter(limit)
        }
        var iter = 0
        when (rule.freq) {
            Freq.DAILY -> {
                var k = 0L
                while (iter++ < MAX_ITER) {
                    val d = anchor.plusDays(k * interval)
                    if (!accept(d)) break
                    k++
                }
            }

            Freq.WEEKLY -> {
                val days = (rule.byDays.ifEmpty { setOf(anchor.dayOfWeek.value) }).sorted()
                val firstWeek = anchor.with(DayOfWeek.MONDAY)
                var k = 0L
                outer@ while (iter++ < MAX_ITER) {
                    val ws = firstWeek.plusWeeks(k * interval)
                    for (dow in days) {
                        val d = ws.plusDays((dow - 1).toLong())
                        if (d.isBefore(anchor)) continue
                        if (!accept(d)) break@outer
                    }
                    k++
                }
            }

            Freq.MONTHLY -> {
                val days = (rule.byDays.ifEmpty { setOf(anchor.dayOfMonth) }).sorted()
                var k = 0L
                outer@ while (iter++ < MAX_ITER) {
                    val m = anchor.withDayOfMonth(1).plusMonths(k * interval)
                    if (m.isAfter(limit)) break
                    for (d in days) {
                        // 小月没有 31 日这类日子：跳过，不挪到月末
                        if (d > m.lengthOfMonth()) continue
                        if (!accept(m.withDayOfMonth(d))) break@outer
                    }
                    k++
                }
            }

            Freq.YEARLY -> {
                val months = (rule.byMonths.ifEmpty { setOf(anchor.monthValue) }).sorted()
                val days = (rule.byDays.ifEmpty { setOf(anchor.dayOfMonth) }).sorted()
                var k = 0L
                outer@ while (iter++ < MAX_ITER) {
                    // 从那年 1 月起算：byMonths 可能选了早于起始月的月份
                    val y = anchor.withDayOfMonth(1).withMonth(1).plusYears(k * interval)
                    if (y.isAfter(limit)) break
                    for (mo in months) {
                        val md = y.withMonth(mo)
                        if (md.isAfter(limit)) break
                        for (d in days) {
                            if (d > md.lengthOfMonth()) continue
                            if (!accept(md.withDayOfMonth(d))) break@outer
                        }
                    }
                    k++
                }
            }
        }
        return result
    }

    /** 严格晚于 [after] 的下一次出现日期。 */
    fun next(
        rule: Recurrence,
        anchor: LocalDate,
        after: LocalDate,
    ): LocalDate? = dates(rule, anchor, after.plusDays(1), after.plusYears(8)).firstOrNull()
}

object EventExpander {
    fun occurrences(
        event: Event,
        from: LocalDate,
        to: LocalDate,
    ): List<EventOccurrence> {
        if (event.deletedAt != null) return emptyList()
        val duration = Duration.between(event.start, event.end).let { if (it.isNegative) Duration.ZERO else it }
        val rule = event.recurrence
        val starts: List<LocalDateTime> =
            if (rule == null) {
                listOf(event.start)
            } else {
                // 多日事件向前多取一些，使跨入区间的出现也能命中
                val back = duration.toDays() + 1
                RecurrenceEngine
                    .dates(rule, event.start.toLocalDate(), from.minusDays(back), to)
                    .map { LocalDateTime.of(it, event.start.toLocalTime()) }
            }
        return starts
            .map { EventOccurrence(event, it, it.plus(duration)) }
            .filter { !it.end.toLocalDate().isBefore(from) && !it.start.toLocalDate().isAfter(to) }
            // 结束于 00:00 的非全天事件不占用次日
            .filter { it.end.toLocalDate() != from || it.end.toLocalTime() != java.time.LocalTime.MIDNIGHT || it.start.toLocalDate() == from || event.allDay }
            .sortedBy { it.start }
    }

    fun occurrences(
        events: List<Event>,
        from: LocalDate,
        to: LocalDate,
    ): List<EventOccurrence> = events.flatMap { occurrences(it, from, to) }.sortedWith(compareBy({ it.start }, { it.event.title }))
}
