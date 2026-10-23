package app.mintcal.core.importer

import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.util.IdGenerator
import app.mintcal.core.util.Ids
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class TimetableImport(
    val courses: List<Course>,
    /** 导入内容里出现的最大周次，用于提示用户学期周数不够。 */
    val maxWeek: Int,
    /** 无法归入节次、被跳过的条目名称。 */
    val skipped: List<String>,
)

object Palette {
    const val SIZE = 8

    /** 按名称首次出现顺序分配课程色：同名同色，不同名尽量不同色。 */
    fun assign(
        names: List<String>,
        startAt: Int = 0,
    ): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        var next = startAt
        for (n in names) {
            if (n !in map) {
                map[n] = next % SIZE
                next++
            }
        }
        return map
    }
}

object IcsImporter {
    private val PERIOD_RE = Regex("第\\s*(\\d+)\\s*[-–~－]\\s*(\\d+)\\s*节")

    /** 是否像课程表（WakeUp 导出，或描述里带「第 a - b 节」）。 */
    fun looksLikeTimetable(cal: IcsCalendar): Boolean =
        cal.prodId.contains("WakeUp", ignoreCase = true) ||
            cal.events.count { PERIOD_RE.containsMatchIn(it.description) } * 2 >= cal.events.size.coerceAtLeast(1)

    /** ICS 里最早事件所在周的周一，用作「第 1 周周一」的建议值。 */
    fun suggestWeek1Monday(cal: IcsCalendar): LocalDate? =
        cal.events
            .mapNotNull { it.start?.toLocalDate() }
            .minOrNull()
            ?.with(DayOfWeek.MONDAY)

    fun toCourses(
        cal: IcsCalendar,
        semester: Semester,
        now: Long,
        ids: IdGenerator = Ids,
        zone: ZoneId = ZoneId.of("Asia/Shanghai"),
    ): ImportResult<TimetableImport> {
        val sc = SemesterCalendar(semester)
        val warnings = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        data class Key(
            val name: String,
            val teacher: String,
            val room: String,
            val dow: Int,
            val sp: Int,
            val ep: Int,
        )
        val weeksByKey = LinkedHashMap<Key, MutableSet<Int>>()
        val remindByKey = HashMap<Key, Int?>()
        var maxWeek = 0
        for (ev in cal.events) {
            val start = ev.start ?: continue
            val end = ev.end ?: start
            val m = PERIOD_RE.find(ev.description)
            val lines = ev.description.split('\n').map { it.trim() }
            val detail = if (m != null) lines.drop(1) else lines
            val room = detail.getOrNull(0).orEmpty()
            val teacher = detail.getOrNull(1).orEmpty().replace(",", "、")
            val sp: Int?
            val ep: Int?
            if (m != null) {
                sp = m.groupValues[1].toInt()
                ep = m.groupValues[2].toInt()
            } else {
                sp = sc.periodAtOrBefore(start.toLocalTime())
                ep = sc.periodEndingAtOrAfter(end.toLocalTime())
            }
            if (sp == null || ep == null || ep < sp) {
                skipped += ev.summary
                continue
            }
            val dates = occurrenceDates(ev, start, zone)
            val key = Key(ev.summary, teacher, room.ifEmpty { ev.location.takeIf { m == null }.orEmpty() }, start.dayOfWeek.value, sp, ep)
            val set = weeksByKey.getOrPut(key) { linkedSetOf() }
            for (d in dates) {
                val w = sc.weekOf(d)
                if (w >= 1) {
                    set += w
                    maxWeek = maxOf(maxWeek, w)
                }
            }
            remindByKey[key] = ev.alarmMinutes?.takeIf { it >= 0 }
        }
        val colors = Palette.assign(weeksByKey.keys.map { it.name })
        val courses =
            weeksByKey
                .map { (k, w) ->
                    Course(
                        id = ids.next(),
                        semesterId = semester.id,
                        name = k.name,
                        teacher = k.teacher,
                        room = k.room,
                        dayOfWeek = k.dow,
                        startPeriod = k.sp,
                        endPeriod = k.ep,
                        weeks = w.toSortedSet(),
                        colorIndex = colors[k.name] ?: 0,
                        remindMinutes = remindByKey[k],
                        source = Source.IMPORT,
                        updatedAt = now,
                    )
                }.filter { it.weeks.isNotEmpty() }
        if (maxWeek > semester.totalWeeks) warnings += "weeks_exceed:$maxWeek"
        return ImportResult(TimetableImport(courses, maxWeek, skipped), warnings)
    }

    /** 展开一个 VEVENT 的所有出现日期（只支持每周 / 每 n 周；无 RRULE 即单次）。 */
    private fun occurrenceDates(
        ev: IcsComponent,
        start: LocalDateTime,
        zone: ZoneId,
    ): List<LocalDate> {
        val rule = ev.rrule ?: return listOf(start.toLocalDate())
        val parts = IcsParser.rruleParts(rule)
        val freq = parts["FREQ"] ?: return listOf(start.toLocalDate())
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { IcsParser.parseUntil(it, zone) }
        val stepDays =
            when (freq) {
                "WEEKLY" -> 7L * interval
                "DAILY" -> 1L * interval
                else -> return listOf(start.toLocalDate())
            }
        val out = mutableListOf<LocalDate>()
        var k = 0L
        while (out.size < 400) {
            val dt = start.plusDays(k * stepDays)
            if (until != null && dt.isAfter(until)) break
            if (count != null && k >= count) break
            if (until == null && count == null && k >= 60) break
            if (dt.toLocalDate() !in ev.exDates) out += dt.toLocalDate()
            k++
        }
        return out
    }

    fun toEvents(
        cal: IcsCalendar,
        now: Long,
        ids: IdGenerator = Ids,
        zone: ZoneId = ZoneId.of("Asia/Shanghai"),
    ): ImportResult<List<Event>> {
        val out = mutableListOf<Event>()
        for (ev in cal.events) {
            val start = ev.start ?: continue
            var end = ev.end ?: if (ev.allDay) start else start.plusHours(1)
            if (ev.allDay && end.isAfter(start)) end = end.minusDays(1)
            if (end.isBefore(start)) end = start
            out +=
                Event(
                    id = ids.next(),
                    title = ev.summary.ifBlank { "(无标题)" },
                    start = start,
                    end = end,
                    allDay = ev.allDay,
                    location = ev.location,
                    note = ev.description,
                    colorIndex = 4,
                    remindMinutes = ev.alarmMinutes?.takeIf { it >= 0 },
                    recurrence = ev.rrule?.let { parseRecurrence(it, start, ev.exDates, zone) },
                    kind = EventKind.NORMAL,
                    source = Source.IMPORT,
                    updatedAt = now,
                )
        }
        return ImportResult(out)
    }

    fun toTasks(
        cal: IcsCalendar,
        now: Long,
        ids: IdGenerator = Ids,
    ): ImportResult<List<Task>> {
        val out =
            cal.todos.map { t ->
                val due = t.due ?: t.end
                Task(
                    id = ids.next(),
                    title = t.summary.ifBlank { "(无标题)" },
                    note = t.description,
                    priority =
                        when (t.priority) {
                            in 1..4 -> 3
                            5 -> 2
                            in 6..9 -> 1
                            else -> 0
                        },
                    due = due,
                    dueHasTime = due != null && !t.allDay && due.toLocalTime() != java.time.LocalTime.MIDNIGHT,
                    startDate = t.start?.toLocalDate(),
                    done = t.completed || t.status == "COMPLETED",
                    source = Source.IMPORT,
                    updatedAt = now,
                    remindMinutes = t.alarmMinutes?.takeIf { it >= 0 },
                )
            }
        return ImportResult(out)
    }

    fun parseRecurrence(
        rrule: String,
        start: LocalDateTime,
        exDates: Set<LocalDate>,
        zone: ZoneId,
    ): Recurrence? {
        val p = IcsParser.rruleParts(rrule)
        val freq =
            when (p["FREQ"]) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                "YEARLY" -> Freq.YEARLY
                else -> return null
            }
        val days =
            p["BYDAY"]
                ?.split(',')
                ?.mapNotNull {
                    when (it.takeLast(2)) {
                        "MO" -> 1
                        "TU" -> 2
                        "WE" -> 3
                        "TH" -> 4
                        "FR" -> 5
                        "SA" -> 6
                        "SU" -> 7
                        else -> null
                    }
                }?.toSet()
                .orEmpty()
        return Recurrence(
            freq = freq,
            interval = p["INTERVAL"]?.toIntOrNull() ?: 1,
            byDays = if (freq == Freq.WEEKLY) days else emptySet(),
            until = p["UNTIL"]?.let { IcsParser.parseUntil(it, zone)?.toLocalDate() },
            count = p["COUNT"]?.toIntOrNull(),
            exDates = exDates,
        )
    }
}
