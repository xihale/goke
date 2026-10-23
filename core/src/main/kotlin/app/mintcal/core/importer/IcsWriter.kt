package app.mintcal.core.importer

import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.Event
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.time.SemesterCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** ICS 导出：日程、课程（按连续周次段 + RRULE）、待办（VTODO）。 */
object IcsWriter {
    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val D = DateTimeFormatter.BASIC_ISO_DATE

    fun write(
        snapshot: Snapshot,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val live = snapshot.live()
        val sb = StringBuilder()

        fun line(s: String) {
            sb.append(fold(s)).append("\r\n")
        }
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//MintCal//MintCal 1.0//ZH")
        line("CALSCALE:GREGORIAN")
        val stamp = utc(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC), ZoneOffset.UTC)
        for (e in live.events) writeEvent(e, zone, stamp, ::line)
        live.currentSemester?.let { sem -> writeCourses(sem, live.courses, live.exceptions, zone, stamp, ::line) }
        for (t in live.tasks) writeTask(t, zone, stamp, ::line)
        line("END:VCALENDAR")
        return sb.toString()
    }

    private fun utc(
        local: LocalDateTime,
        zone: ZoneId,
    ): String = local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(DT)

    private fun writeAlarm(
        minutes: Int?,
        summary: String,
        line: (String) -> Unit,
    ) {
        if (minutes == null) return
        line("BEGIN:VALARM")
        line("ACTION:DISPLAY")
        line("TRIGGER:-PT${minutes}M")
        line("DESCRIPTION:${escape(summary)}")
        line("END:VALARM")
    }

    private fun writeEvent(
        e: Event,
        zone: ZoneId,
        stamp: String,
        line: (String) -> Unit,
    ) {
        line("BEGIN:VEVENT")
        line("UID:${e.id}@mintcal")
        line("DTSTAMP:$stamp")
        if (e.allDay) {
            line("DTSTART;VALUE=DATE:${e.start.toLocalDate().format(D)}")
            line("DTEND;VALUE=DATE:${e.end.toLocalDate().plusDays(1).format(D)}")
        } else {
            line("DTSTART:${utc(e.start, zone)}")
            line("DTEND:${utc(e.end, zone)}")
        }
        line("SUMMARY:${escape(e.title)}")
        if (e.location.isNotBlank()) line("LOCATION:${escape(e.location)}")
        if (e.note.isNotBlank()) line("DESCRIPTION:${escape(e.note)}")
        e.recurrence?.let { r ->
            line("RRULE:${rrule(r, zone, e.start)}")
            if (r.exDates.isNotEmpty()) {
                val v = r.exDates.sorted().joinToString(",") { if (e.allDay) it.format(D) else utc(LocalDateTime.of(it, e.start.toLocalTime()), zone) }
                line(if (e.allDay) "EXDATE;VALUE=DATE:$v" else "EXDATE:$v")
            }
        }
        writeAlarm(e.remindMinutes, e.title, line)
        line("END:VEVENT")
    }

    internal fun rrule(
        r: Recurrence,
        zone: ZoneId,
        start: LocalDateTime,
    ): String {
        val parts = mutableListOf("FREQ=${r.freq.name}")
        if (r.interval > 1) parts += "INTERVAL=${r.interval}"
        if (r.freq == Freq.WEEKLY && r.byDays.isNotEmpty()) {
            val names = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
            parts += "BYDAY=" + r.byDays.sorted().joinToString(",") { names[it - 1] }
        }
        r.until?.let { parts += "UNTIL=${utc(LocalDateTime.of(it, java.time.LocalTime.of(23, 59, 59)), zone)}" }
        r.count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }

    private fun writeCourses(
        sem: Semester,
        courses: List<Course>,
        exceptions: List<CourseException>,
        zone: ZoneId,
        stamp: String,
        line: (String) -> Unit,
    ) {
        val cal = SemesterCalendar(sem)
        for (c in courses.filter { it.semesterId == sem.id }) {
            val exByWeek = exceptions.filter { it.courseId == c.id && it.deletedAt == null }.associateBy { it.week }
            val desc = "第${c.startPeriod} - ${c.endPeriod}节" + (if (c.room.isNotBlank()) "\n${c.room}" else "") + (if (c.teacher.isNotBlank()) "\n${c.teacher}" else "")
            val sTime = cal.startTime(c.startPeriod) ?: continue
            val eTime = cal.endTime(c.endPeriod) ?: continue
            // 连续周次段
            val sorted = c.weeks.sorted()
            val runs = mutableListOf<List<Int>>()
            var cur = mutableListOf<Int>()
            for (w in sorted) {
                if (cur.isNotEmpty() && w != cur.last() + 1) {
                    runs += cur
                    cur = mutableListOf()
                }
                cur += w
            }
            if (cur.isNotEmpty()) runs += cur
            for ((idx, run) in runs.withIndex()) {
                val first = LocalDateTime.of(cal.dateOf(run.first(), c.dayOfWeek), sTime)
                val last = LocalDateTime.of(cal.dateOf(run.first(), c.dayOfWeek), eTime)
                line("BEGIN:VEVENT")
                line("UID:${c.id}-$idx@mintcal")
                line("DTSTAMP:$stamp")
                line("DTSTART:${utc(first, zone)}")
                line("DTEND:${utc(last, zone)}")
                line("SUMMARY:${escape(c.name)}")
                line("LOCATION:${escape(listOf(c.room, c.teacher).filter { it.isNotBlank() }.joinToString(" "))}")
                line("DESCRIPTION:${escape(desc)}")
                if (run.size > 1) line("RRULE:FREQ=WEEKLY;COUNT=${run.size}")
                val exWeeks = run.filter { exByWeek.containsKey(it) }
                if (exWeeks.isNotEmpty()) line("EXDATE:" + exWeeks.joinToString(",") { utc(LocalDateTime.of(cal.dateOf(it, c.dayOfWeek), sTime), zone) })
                writeAlarm(c.remindMinutes, c.name, line)
                line("END:VEVENT")
            }
            for ((w, ex) in exByWeek) {
                if (ex.type != ExceptionType.RESCHEDULE || w !in c.weeks) continue
                val nw = ex.newWeek ?: w
                val nd = ex.newDayOfWeek ?: c.dayOfWeek
                val st = cal.startTime(ex.newStartPeriod ?: c.startPeriod) ?: continue
                val et = cal.endTime(ex.newEndPeriod ?: c.endPeriod) ?: continue
                line("BEGIN:VEVENT")
                line("UID:${ex.id}@mintcal")
                line("DTSTAMP:$stamp")
                line("DTSTART:${utc(LocalDateTime.of(cal.dateOf(nw, nd), st), zone)}")
                line("DTEND:${utc(LocalDateTime.of(cal.dateOf(nw, nd), et), zone)}")
                line("SUMMARY:${escape(c.name)}")
                line("LOCATION:${escape(ex.newRoom ?: c.room)}")
                line("END:VEVENT")
            }
        }
    }

    private fun writeTask(
        t: Task,
        zone: ZoneId,
        stamp: String,
        line: (String) -> Unit,
    ) {
        line("BEGIN:VTODO")
        line("UID:${t.id}@mintcal")
        line("DTSTAMP:$stamp")
        line("SUMMARY:${escape(t.title)}")
        if (t.note.isNotBlank()) line("DESCRIPTION:${escape(t.note)}")
        t.due?.let { d ->
            if (t.dueHasTime) line("DUE:${utc(d, zone)}") else line("DUE;VALUE=DATE:${d.toLocalDate().format(D)}")
        }
        t.startDate?.let { line("DTSTART;VALUE=DATE:${it.format(D)}") }
        val p =
            when (t.priority) {
                3 -> 1
                2 -> 5
                1 -> 9
                else -> 0
            }
        if (p > 0) line("PRIORITY:$p")
        line(if (t.done) "STATUS:COMPLETED" else "STATUS:NEEDS-ACTION")
        writeAlarm(t.remindMinutes, t.title, line)
        line("END:VTODO")
    }

    internal fun escape(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\n")
            .replace("\n", "\\n")

    /** 75 字节折行（按 UTF-8 字节计，不拆分字符）。 */
    internal fun fold(line: String): String {
        val sb = StringBuilder()
        var bytes = 0
        for (ch in line.codePoints().toArray()) {
            val s = String(Character.toChars(ch))
            val n = s.toByteArray(Charsets.UTF_8).size
            if (bytes + n > 74) {
                sb.append("\r\n ")
                bytes = 1
            }
            sb.append(s)
            bytes += n
        }
        return sb.toString()
    }
}
