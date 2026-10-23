package app.mintcal.core.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** ICS 里的一个 VEVENT / VTODO（解析后的原始形态）。 */
data class IcsComponent(
    val type: String,
    val uid: String,
    val summary: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val allDay: Boolean,
    val due: LocalDateTime?,
    val rrule: String?,
    val exDates: Set<LocalDate>,
    val location: String,
    val description: String,
    val alarmMinutes: Int?,
    val status: String?,
    val priority: Int?,
    val completed: Boolean,
)

data class IcsCalendar(
    val prodId: String,
    val timeZone: String?,
    val components: List<IcsComponent>,
) {
    val events: List<IcsComponent> get() = components.filter { it.type == "VEVENT" }
    val todos: List<IcsComponent> get() = components.filter { it.type == "VTODO" }
}

/** 宽松的 ICS 解析器（RFC 5545 子集），时间统一转换为目标时区的本地时间。 */
object IcsParser {
    fun parse(
        text: String,
        targetZone: ZoneId = ZoneId.of("Asia/Shanghai"),
    ): ImportResult<IcsCalendar> {
        if (!text.contains("BEGIN:VCALENDAR")) return ImportResult.failure("not_ics")
        val warnings = mutableListOf<String>()
        val lines = unfold(text)
        var prodId = ""
        var tz: String? = null
        val comps = mutableListOf<IcsComponent>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("PRODID") -> {
                    prodId = valueOf(line)
                }

                line.startsWith("X-WR-TIMEZONE") -> {
                    tz = valueOf(line)
                }

                line == "BEGIN:VEVENT" || line == "BEGIN:VTODO" -> {
                    val type = line.removePrefix("BEGIN:")
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i] != "END:$type") {
                        body += lines[i]
                        i++
                    }
                    parseComponent(type, body, targetZone, warnings)?.let { comps += it }
                }
            }
            i++
        }
        return ImportResult(IcsCalendar(prodId, tz, comps), warnings)
    }

    private fun parseComponent(
        type: String,
        body: List<String>,
        zone: ZoneId,
        warnings: MutableList<String>,
    ): IcsComponent? {
        var uid = ""
        var summary = ""
        var location = ""
        var description = ""
        var start: LocalDateTime? = null
        var end: LocalDateTime? = null
        var due: LocalDateTime? = null
        var allDay = false
        var rrule: String? = null
        var alarm: Int? = null
        var status: String? = null
        var priority: Int? = null
        var completed = false
        val ex = mutableSetOf<LocalDate>()
        var inAlarm = false
        for (l in body) {
            if (l == "BEGIN:VALARM") {
                inAlarm = true
                continue
            }
            if (l == "END:VALARM") {
                inAlarm = false
                continue
            }
            val name = l.substringBefore(':').substringBefore(';').uppercase()
            if (inAlarm) {
                if (name == "TRIGGER") alarm = parseTriggerMinutes(valueOf(l))
                continue
            }
            when (name) {
                "UID" -> {
                    uid = valueOf(l)
                }

                "SUMMARY" -> {
                    summary = unescape(valueOf(l))
                }

                "LOCATION" -> {
                    location = unescape(valueOf(l))
                }

                "DESCRIPTION" -> {
                    description = unescape(valueOf(l))
                }

                "RRULE" -> {
                    rrule = valueOf(l)
                }

                "STATUS" -> {
                    status = valueOf(l)
                }

                "PRIORITY" -> {
                    priority = valueOf(l).toIntOrNull()
                }

                "COMPLETED", "PERCENT-COMPLETE" -> {
                    if (name == "COMPLETED" || valueOf(l) == "100") completed = true
                }

                "DTSTART" -> {
                    parseDt(l, zone)?.let { (dt, ad) ->
                        start = dt
                        allDay = ad
                    } ?: warnings.add("bad DTSTART: $l")
                }

                "DTEND" -> {
                    parseDt(l, zone)?.let { end = it.first }
                }

                "DUE" -> {
                    parseDt(l, zone)?.let { due = it.first }
                }

                "EXDATE" -> {
                    valueOf(l).split(',').forEach { v ->
                        parseDt("EXDATE" + l.substringAfter("EXDATE").substringBefore(':') + ":" + v, zone)?.let { ex += it.first.toLocalDate() }
                    }
                }
            }
        }
        if (summary.isBlank() && start == null && due == null) return null
        return IcsComponent(type, uid, summary, start, end, allDay, due, rrule, ex, location, description, alarm, status, priority, completed)
    }

    /** 解析 DTSTART 等行，返回 (本地时间, 是否全天)。 */
    private fun parseDt(
        line: String,
        zone: ZoneId,
    ): Pair<LocalDateTime, Boolean>? {
        val head = line.substringBefore(':')
        val value = line.substringAfter(':').trim()
        val params = head.split(';').drop(1).associate { p -> p.substringBefore('=').uppercase() to p.substringAfter('=', "") }
        return try {
            if (params["VALUE"] == "DATE" || (value.length == 8 && !value.contains('T'))) {
                LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() to true
            } else {
                val ldt = LocalDateTime.parse(value.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                when {
                    value.endsWith("Z") -> {
                        ldt.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime() to false
                    }

                    params["TZID"] != null -> {
                        val z = runCatching { ZoneId.of(params["TZID"]!!) }.getOrDefault(zone)
                        ldt.atZone(z).withZoneSameInstant(zone).toLocalDateTime() to false
                    }

                    else -> {
                        ldt to false
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** `-PT18M`、`-PT1H`、`-P1D` → 提前分钟数（正数）。 */
    internal fun parseTriggerMinutes(v: String): Int? {
        val m = Regex("^(-?)P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(v.trim()) ?: return null
        val neg = m.groupValues[1] == "-"
        val d = m.groupValues[2].toIntOrNull() ?: 0
        val h = m.groupValues[3].toIntOrNull() ?: 0
        val mi = m.groupValues[4].toIntOrNull() ?: 0
        val total = d * 1440 + h * 60 + mi
        return if (neg) total else -total
    }

    private fun valueOf(line: String): String = line.substringAfter(':', "").trim()

    /** 折行还原：以空格或制表符开头的行接到上一行。 */
    internal fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if (raw.isEmpty()) continue
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    internal fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** 解析 RRULE 文本为键值。 */
    fun rruleParts(rrule: String): Map<String, String> = rrule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()

    /** UNTIL 值（可能带 Z）转换为目标时区的本地日期时间。 */
    fun parseUntil(
        v: String,
        zone: ZoneId,
    ): LocalDateTime? =
        try {
            if (v.length == 8) {
                LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atTime(LocalTime.MAX)
            } else {
                val ldt = LocalDateTime.parse(v.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                if (v.endsWith("Z")) ldt.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime() else ldt
            }
        } catch (e: Exception) {
            null
        }
}
