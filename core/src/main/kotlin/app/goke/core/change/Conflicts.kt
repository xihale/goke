package app.goke.core.change

import app.goke.core.model.Course
import app.goke.core.model.Event
import app.goke.core.model.Freq
import app.goke.core.model.Snapshot
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.EventExpander
import app.goke.core.time.SemesterCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class Conflict(
    val aId: String,
    val aTitle: String,
    val bId: String,
    val bTitle: String,
    val start: LocalDateTime,
)

/** 冲突检测：同时考虑课程、日程（不含全天）。只报告涉及 [focusIds] 的冲突。 */
object Conflicts {
    private data class Slot(
        val ownerId: String,
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
    )

    fun detect(
        snapshot: Snapshot,
        focusIds: Set<String>,
        limit: Int = 30,
    ): List<Conflict> {
        val live = snapshot.live()
        val semester = live.currentSemester ?: return emptyList()
        val cal = SemesterCalendar(semester)
        val from = cal.weekStart(1)
        val to = cal.weekEnd(cal.totalWeeks)
        val slots = mutableListOf<Slot>()
        for (o in CourseExpander.occurrences(semester, live.courses, live.exceptions, from, to)) {
            slots += Slot(o.course.id, o.course.name, o.start, o.end)
        }
        for (e in live.events.filter { !it.allDay }) {
            for (o in EventExpander.occurrences(e, from, to)) slots += Slot(e.id, e.title, o.start, o.end)
        }
        val focus = slots.filter { it.ownerId in focusIds }
        val result = mutableListOf<Conflict>()
        val seen = mutableSetOf<String>()
        for (a in focus) {
            for (b in slots) {
                if (a.ownerId == b.ownerId) continue
                if (a.start < b.end && b.start < a.end) {
                    val key = listOf(a.ownerId, b.ownerId).sorted().joinToString("|") + a.start.toLocalDate()
                    if (seen.add(key)) {
                        result += Conflict(a.ownerId, a.title, b.ownerId, b.title, maxOf(a.start, b.start))
                        if (result.size >= limit) return result
                    }
                }
            }
        }
        return result
    }

    fun focusIds(cs: ChangeSet): Set<String> =
        cs.changes
            .filter { it !is Change.Remove }
            .map { it.record }
            .filter { it is Course || it is Event }
            .map { it.id }
            .toSet()
}

private data class Occ(
    val ownerId: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/** 时间块与别的事重叠的一次：[title] 是被挡住的那位（课程名或另一条待办）。 */
data class SlotClash(
    val at: LocalDateTime,
    val title: String,
    val course: Boolean,
)

/**
 * 待办时间块（含重复展开）与课程 / 其它已排待办的重叠检查。
 * [instances] 是要落地的日期（重复规则已展开）；[excludeId] 是正在编辑的待办自己。
 */
fun clashesOf(
    snapshot: Snapshot,
    excludeId: String?,
    instances: List<LocalDate>,
    start: LocalTime,
    minutes: Int,
    limit: Int = 6,
): List<SlotClash> {
    if (instances.isEmpty() || minutes <= 0) return emptyList()
    val live = snapshot.live()
    val from = instances.min()
    val to = instances.max()
    val taken = mutableListOf<Occ>()
    live.currentSemester?.let { sem ->
        for (o in CourseExpander.occurrences(sem, live.courses, live.exceptions, from, to)) {
            taken += Occ(o.course.id, o.course.name, o.start, o.end)
        }
    }
    for (t in live.tasks) {
        if (t.id == excludeId || t.done) continue
        val b = t.blockStart ?: continue
        taken += Occ(t.id, t.title, b, b.plusMinutes((t.blockMinutes ?: 60).toLong()))
    }
    val out = mutableListOf<SlotClash>()
    val seen = mutableSetOf<String>()
    for (d in instances) {
        val a0 = LocalDateTime.of(d, start)
        val a1 = a0.plusMinutes(minutes.toLong())
        for (s in taken) {
            if (a0 < s.end && s.start < a1 && seen.add(s.ownerId + d)) {
                out += SlotClash(maxOf(a0, s.start), s.title, s.ownerId !in live.tasks.map { it.id })
            }
        }
    }
    return out.sortedBy { it.at }.take(limit)
}

/** 把重复规则展开成要落地的日期（含起始日），最多 [cap] 个、最多往后半年。 */
fun repeatInstances(
    date: LocalDate,
    freq: Freq?,
    until: LocalDate?,
    cap: Int = 60,
): List<LocalDate> {
    if (freq == null) return listOf(date)
    val end = minOf(until ?: date.plusMonths(6), date.plusMonths(6))
    val step = until?.plusDays(1)?.let { ChronoUnit.DAYS.between(date, it) } ?: Long.MAX_VALUE
    val out = mutableListOf<LocalDate>()
    var i = 0L
    while (out.size < cap) {
        val d =
            when (freq) {
                Freq.DAILY -> date.plusDays(i)
                Freq.WEEKLY -> date.plusWeeks(i)
                Freq.MONTHLY -> date.plusMonths(i)
                Freq.YEARLY -> date.plusYears(i)
            }
        if (d > end || i >= step) break
        out += d
        i++
    }
    return out.ifEmpty { listOf(date) }
}
