import Foundation
import GokeCoreModel

public struct ImportResult<T: Sendable>: Sendable {
    public let value: T?
    public let warnings: [String]
    public let error: String?

    public var isSuccess: Bool { value != nil && error == nil }

    public init(value: T?, warnings: [String] = [], error: String? = nil) {
        self.value = value
        self.warnings = warnings
        self.error = error
    }

    public static func failure(_ error: String, warnings: [String] = []) -> ImportResult<T> {
        ImportResult(value: nil, warnings: warnings, error: error)
    }
}

public struct IcsComponent: Sendable, Hashable {
    public let type: String
    public let uid: String
    public let summary: String
    public let start: LocalDateTime?
    public let end: LocalDateTime?
    public let allDay: Bool
    public let due: LocalDateTime?
    public let rrule: String?
    public let exDates: Set<LocalDate>
    public let location: String
    public let description: String
    public let alarmMinutes: Int?
    public let status: String?
    public let priority: Int?
    public let completed: Bool

    public init(
        type: String,
        uid: String,
        summary: String,
        start: LocalDateTime?,
        end: LocalDateTime?,
        allDay: Bool,
        due: LocalDateTime?,
        rrule: String?,
        exDates: Set<LocalDate>,
        location: String,
        description: String,
        alarmMinutes: Int?,
        status: String?,
        priority: Int?,
        completed: Bool
    ) {
        self.type = type
        self.uid = uid
        self.summary = summary
        self.start = start
        self.end = end
        self.allDay = allDay
        self.due = due
        self.rrule = rrule
        self.exDates = exDates
        self.location = location
        self.description = description
        self.alarmMinutes = alarmMinutes
        self.status = status
        self.priority = priority
        self.completed = completed
    }
}

public struct IcsCalendar: Sendable, Hashable {
    public let prodId: String
    public let timeZone: String?
    public let components: [IcsComponent]

    public var events: [IcsComponent] { components.filter { $0.type == "VEVENT" } }
    public var todos: [IcsComponent] { components.filter { $0.type == "VTODO" } }

    public init(prodId: String, timeZone: String?, components: [IcsComponent]) {
        self.prodId = prodId
        self.timeZone = timeZone
        self.components = components
    }
}

public struct TimetableImport: Sendable, Hashable {
    public let courses: [Course]
    public let maxWeek: Int
    public let skipped: [String]

    public init(courses: [Course], maxWeek: Int, skipped: [String]) {
        self.courses = courses
        self.maxWeek = maxWeek
        self.skipped = skipped
    }
}

public enum Palette {
    public static let size = 8

    public static func assign(names: [String], startAt: Int = 0) -> [String: Int] {
        var map: [String: Int] = [:]
        var next = startAt
        for n in names where map[n] == nil {
            map[n] = next % size
            next += 1
        }
        return map
    }
}

/// RFC 5545 ICS parser, exporter, and course/event/task converter matching Android `IcsParser` + `IcsWriter` + `IcsImporter`.
public enum ICSCodec {
    public static let shanghaiTimeZone = TimeZone(identifier: "Asia/Shanghai") ?? TimeZone(secondsFromGMT: 8 * 3600)!
    private static let utcTimeZone = TimeZone(secondsFromGMT: 0)!
    private static let triggerRegex = try! NSRegularExpression(pattern: "^(-?)P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
    private static let periodRegex = try! NSRegularExpression(pattern: "第\\s*(\\d+)\\s*[-–~－]\\s*(\\d+)\\s*节")

    // MARK: - Parse

    public static func parse(
        _ text: String,
        targetZone: TimeZone = shanghaiTimeZone
    ) -> ImportResult<IcsCalendar> {
        guard text.contains("BEGIN:VCALENDAR") else {
            return .failure("not_ics")
        }
        var warnings: [String] = []
        let lines = unfold(text)
        var prodId = ""
        var tz: String?
        var comps: [IcsComponent] = []
        var i = 0
        while i < lines.count {
            let line = lines[i]
            if line.hasPrefix("PRODID") {
                prodId = valueOf(line)
            } else if line.hasPrefix("X-WR-TIMEZONE") {
                tz = valueOf(line)
            } else if line == "BEGIN:VEVENT" || line == "BEGIN:VTODO" {
                let type = String(line.dropFirst("BEGIN:".count))
                var body: [String] = []
                i += 1
                while i < lines.count && lines[i] != "END:\(type)" {
                    body.append(lines[i])
                    i += 1
                }
                if let comp = parseComponent(type: type, body: body, zone: targetZone, warnings: &warnings) {
                    comps.append(comp)
                }
            }
            i += 1
        }
        return ImportResult(value: IcsCalendar(prodId: prodId, timeZone: tz, components: comps), warnings: warnings)
    }

    private static func parseComponent(
        type: String,
        body: [String],
        zone: TimeZone,
        warnings: inout [String]
    ) -> IcsComponent? {
        var uid = ""
        var summary = ""
        var location = ""
        var description = ""
        var start: LocalDateTime?
        var end: LocalDateTime?
        var due: LocalDateTime?
        var allDay = false
        var rrule: String?
        var alarm: Int?
        var status: String?
        var priority: Int?
        var completed = false
        var ex = Set<LocalDate>()
        var inAlarm = false

        for l in body {
            if l == "BEGIN:VALARM" { inAlarm = true; continue }
            if l == "END:VALARM" { inAlarm = false; continue }
            let head = l.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
            let name = (head.split(separator: ";", maxSplits: 1).first.map(String.init) ?? "").uppercased()
            if inAlarm {
                if name == "TRIGGER" {
                    alarm = parseTriggerMinutes(valueOf(l))
                }
                continue
            }
            switch name {
            case "UID": uid = valueOf(l)
            case "SUMMARY": summary = unescape(valueOf(l))
            case "LOCATION": location = unescape(valueOf(l))
            case "DESCRIPTION": description = unescape(valueOf(l))
            case "RRULE": rrule = valueOf(l)
            case "STATUS": status = valueOf(l)
            case "PRIORITY": priority = Int(valueOf(l))
            case "COMPLETED", "PERCENT-COMPLETE":
                if name == "COMPLETED" || valueOf(l) == "100" { completed = true }
            case "DTSTART":
                if let parsed = parseDt(l, zone: zone) {
                    start = parsed.dt
                    allDay = parsed.allDay
                } else {
                    warnings.append("bad DTSTART: \(l)")
                }
            case "DTEND":
                if let parsed = parseDt(l, zone: zone) { end = parsed.dt }
            case "DUE":
                if let parsed = parseDt(l, zone: zone) { due = parsed.dt }
            case "EXDATE":
                let paramsPart = head.dropFirst("EXDATE".count)
                for v in valueOf(l).split(separator: ",") {
                    if let parsed = parseDt("EXDATE\(paramsPart):\(v)", zone: zone) {
                        ex.insert(parsed.dt.date)
                    }
                }
            default:
                break
            }
        }
        if summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && start == nil && due == nil {
            return nil
        }
        return IcsComponent(
            type: type,
            uid: uid,
            summary: summary,
            start: start,
            end: end,
            allDay: allDay,
            due: due,
            rrule: rrule,
            exDates: ex,
            location: location,
            description: description,
            alarmMinutes: alarm,
            status: status,
            priority: priority,
            completed: completed
        )
    }

    private static func parseDt(_ line: String, zone: TimeZone) -> (dt: LocalDateTime, allDay: Bool)? {
        guard let colonIdx = line.firstIndex(of: ":") else { return nil }
        let head = String(line[..<colonIdx])
        let value = String(line[line.index(after: colonIdx)...]).trimmingCharacters(in: .whitespacesAndNewlines)
        var params: [String: String] = [:]
        for p in head.split(separator: ";").dropFirst() {
            let kv = p.split(separator: "=", maxSplits: 1)
            let k = String(kv[0]).uppercased()
            let v = kv.count > 1 ? String(kv[1]) : ""
            params[k] = v
        }
        if params["VALUE"] == "DATE" || (value.count == 8 && !value.contains("T")) {
            guard let date = LocalDate.parse(value) else { return nil }
            return (date.atStartOfDay(), true)
        }
        let isUTC = value.hasSuffix("Z")
        let clean = isUTC ? String(value.dropLast()) : value
        guard let ldt = parseBasicDateTime(clean) else { return nil }
        if isUTC {
            let instant = ldt.toDate(in: utcTimeZone)
            return (LocalDateTime.fromDate(instant, in: zone), false)
        }
        if let tzid = params["TZID"], let sourceZone = TimeZone(identifier: tzid) {
            let instant = ldt.toDate(in: sourceZone)
            return (LocalDateTime.fromDate(instant, in: zone), false)
        }
        return (ldt, false)
    }

    private static func parseBasicDateTime(_ text: String) -> LocalDateTime? {
        guard text.count == 15 else { return nil }
        let chars = Array(text)
        guard chars[8] == "T" else { return nil }
        guard let y = Int(String(chars[0..<4])),
              let mo = Int(String(chars[4..<6])),
              let d = Int(String(chars[6..<8])),
              let h = Int(String(chars[9..<11])),
              let mi = Int(String(chars[11..<13])),
              let s = Int(String(chars[13..<15])),
              let date = LocalDate(validatingYear: y, month: mo, day: d),
              let time = LocalTime(validatingHour: h, minute: mi, second: s)
        else { return nil }
        return LocalDateTime(date: date, time: time)
    }

    public static func parseTriggerMinutes(_ value: String) -> Int? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let m = triggerRegex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) else {
            return nil
        }
        func group(_ idx: Int) -> String? {
            let r = m.range(at: idx)
            guard r.location != NSNotFound, let sr = Range(r, in: trimmed) else { return nil }
            return String(trimmed[sr])
        }
        let neg = group(1) == "-"
        let d = group(2).flatMap(Int.init) ?? 0
        let h = group(3).flatMap(Int.init) ?? 0
        let mi = group(4).flatMap(Int.init) ?? 0
        let total = d * 1440 + h * 60 + mi
        return neg ? total : -total
    }

    public static func unfold(_ text: String) -> [String] {
        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        var out: [String] = []
        for raw in normalized.split(separator: "\n", omittingEmptySubsequences: false) {
            if raw.isEmpty { continue }
            if (raw.hasPrefix(" ") || raw.hasPrefix("\t")) && !out.isEmpty {
                out[out.count - 1] += String(raw.dropFirst())
            } else {
                out.append(String(raw))
            }
        }
        return out
    }

    public static func unescape(_ s: String) -> String {
        var out = ""
        let chars = Array(s)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == "\\" && i + 1 < chars.count {
                let next = chars[i + 1]
                if next == "n" || next == "N" {
                    out.append("\n")
                } else {
                    out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
        return out
    }

    public static func escape(_ s: String) -> String {
        s
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    /// Folds an ICS content line to <= 75 UTF-8 bytes per line without splitting scalars.
    public static func fold(_ line: String) -> String {
        var out = ""
        var bytes = 0
        for scalar in line.unicodeScalars {
            let str = String(scalar)
            let n = str.utf8.count
            if bytes + n > 74 {
                out += "\r\n "
                bytes = 1
            }
            out += str
            bytes += n
        }
        return out
    }

    public static func rruleParts(_ rrule: String) -> [String: String] {
        var map: [String: String] = [:]
        for part in rrule.split(separator: ";") {
            let kv = part.split(separator: "=", maxSplits: 1)
            if kv.count == 2 {
                map[String(kv[0]).uppercased()] = String(kv[1])
            }
        }
        return map
    }

    public static func parseUntil(_ value: String, zone: TimeZone) -> LocalDateTime? {
        let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if v.count == 8, let d = LocalDate.parse(v) {
            return d.atTime(.endOfDay)
        }
        let isUTC = v.hasSuffix("Z")
        let clean = isUTC ? String(v.dropLast()) : v
        guard let ldt = parseBasicDateTime(clean) else { return nil }
        if isUTC {
            return LocalDateTime.fromDate(ldt.toDate(in: utcTimeZone), in: zone)
        }
        return ldt
    }

    private static func valueOf(_ line: String) -> String {
        guard let idx = line.firstIndex(of: ":") else { return "" }
        return String(line[line.index(after: idx)...]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Write

    public static func write(
        snapshot: AppSnapshot,
        zone: TimeZone = shanghaiTimeZone,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> String {
        let live = snapshot.live()
        var lines: [String] = []
        func emit(_ s: String) {
            lines.append(fold(s))
        }

        emit("BEGIN:VCALENDAR")
        emit("VERSION:2.0")
        emit("PRODID:-//Goke//Goke 1.0//ZH")
        emit("CALSCALE:GREGORIAN")

        let nowDate = Date(timeIntervalSince1970: TimeInterval(nowMillis) / 1000.0)
        let stamp = formatUTC(LocalDateTime.fromDate(nowDate, in: utcTimeZone), zone: utcTimeZone)

        for e in live.events {
            writeEvent(e, zone: zone, stamp: stamp, emit: emit)
        }
        if let sem = live.currentSemester {
            writeCourses(semester: sem, courses: live.courses, exceptions: live.exceptions, zone: zone, stamp: stamp, emit: emit)
        }
        for t in live.tasks {
            writeTask(t, zone: zone, stamp: stamp, emit: emit)
        }
        emit("END:VCALENDAR")
        return lines.joined(separator: "\r\n") + "\r\n"
    }

    /// Exports only the timetable (`VEVENT`s for courses & rescheduled exceptions + Goke semester metadata),
    /// omitting standalone calendar events and tasks (matches Android `IcsWriter.writeTimetable`).
    public static func writeTimetable(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException] = [],
        zone: TimeZone = shanghaiTimeZone,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> String {
        var lines: [String] = []
        func emit(_ s: String) {
            lines.append(fold(s))
        }

        emit("BEGIN:VCALENDAR")
        emit("VERSION:2.0")
        emit("PRODID:-//Goke//Goke 1.0//ZH")
        emit("CALSCALE:GREGORIAN")
        emit("X-WR-CALNAME:\(escape(semester.name))")
        emit("X-GOKE-SEMESTER:\(escape(semester.name))")
        emit("X-GOKE-WEEK1-MONDAY:\(semester.week1Monday.description)")
        emit("X-GOKE-TOTAL-WEEKS:\(semester.totalWeeks)")

        let nowDate = Date(timeIntervalSince1970: TimeInterval(nowMillis) / 1000.0)
        let stamp = formatUTC(LocalDateTime.fromDate(nowDate, in: utcTimeZone), zone: utcTimeZone)
        let liveCourses = courses.filter { $0.deletedAt == nil }
        let liveExceptions = exceptions.filter { $0.deletedAt == nil }
        writeCourses(semester: semester, courses: liveCourses, exceptions: liveExceptions, zone: zone, stamp: stamp, emit: emit)
        emit("END:VCALENDAR")
        return lines.joined(separator: "\r\n") + "\r\n"
    }

    /// Convenience overload exporting only the current semester timetable from an `AppSnapshot`.
    public static func writeTimetable(
        snapshot: AppSnapshot,
        zone: TimeZone = shanghaiTimeZone,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> String? {
        let live = snapshot.live()
        guard let sem = live.currentSemester else { return nil }
        return writeTimetable(
            semester: sem,
            courses: live.courses,
            exceptions: live.exceptions,
            zone: zone,
            nowMillis: nowMillis
        )
    }

    public static func rrule(_ r: Recurrence, zone: TimeZone) -> String {
        var parts = ["FREQ=\(r.freq.rawValue)"]
        if r.interval > 1 { parts.append("INTERVAL=\(r.interval)") }
        if r.freq == .WEEKLY && !r.byDays.isEmpty {
            let names = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
            let joined = r.byDays.sorted().compactMap { (1...7).contains($0) ? names[$0 - 1] : nil }.joined(separator: ",")
            if !joined.isEmpty { parts.append("BYDAY=\(joined)") }
        }
        if r.freq == .YEARLY && !r.byMonths.isEmpty {
            parts.append("BYMONTH=" + r.byMonths.sorted().map(String.init).joined(separator: ","))
        }
        if (r.freq == .MONTHLY || r.freq == .YEARLY) && !r.byDays.isEmpty {
            parts.append("BYMONTHDAY=" + r.byDays.sorted().map(String.init).joined(separator: ","))
        }
        if let until = r.until {
            parts.append("UNTIL=\(formatUTC(until.atTime(.endOfDay), zone: zone))")
        }
        if let count = r.count {
            parts.append("COUNT=\(count)")
        }
        return parts.joined(separator: ";")
    }

    private static func formatUTC(_ local: LocalDateTime, zone: TimeZone) -> String {
        let instant = local.toDate(in: zone)
        let u = LocalDateTime.fromDate(instant, in: utcTimeZone)
        return String(format: "%04d%02d%02dT%02d%02d%02dZ", u.year, u.month, u.day, u.hour, u.minute, u.second)
    }

    private static func writeAlarm(minutes: Int?, summary: String, emit: (String) -> Void) {
        guard let minutes else { return }
        emit("BEGIN:VALARM")
        emit("ACTION:DISPLAY")
        emit("TRIGGER:-PT\(minutes)M")
        emit("DESCRIPTION:\(escape(summary))")
        emit("END:VALARM")
    }

    private static func writeEvent(_ e: Event, zone: TimeZone, stamp: String, emit: (String) -> Void) {
        emit("BEGIN:VEVENT")
        emit("UID:\(e.id)@goke")
        emit("DTSTAMP:\(stamp)")
        if e.allDay {
            emit("DTSTART;VALUE=DATE:\(e.start.date.basicISOString)")
            emit("DTEND;VALUE=DATE:\(e.end.date.addingDays(1).basicISOString)")
        } else {
            emit("DTSTART:\(formatUTC(e.start, zone: zone))")
            emit("DTEND:\(formatUTC(e.end, zone: zone))")
        }
        emit("SUMMARY:\(escape(e.title))")
        if !e.location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            emit("LOCATION:\(escape(e.location))")
        }
        if !e.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            emit("DESCRIPTION:\(escape(e.note))")
        }
        if let r = e.recurrence {
            emit("RRULE:\(rrule(r, zone: zone))")
            if !r.exDates.isEmpty {
                let vals = r.exDates.sorted().map { d in
                    e.allDay ? d.basicISOString : formatUTC(d.atTime(e.start.time), zone: zone)
                }.joined(separator: ",")
                emit(e.allDay ? "EXDATE;VALUE=DATE:\(vals)" : "EXDATE:\(vals)")
            }
        }
        writeAlarm(minutes: e.remindMinutes, summary: e.title, emit: emit)
        emit("END:VEVENT")
    }

    private static func writeCourses(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException],
        zone: TimeZone,
        stamp: String,
        emit: (String) -> Void
    ) {
        let cal = SemesterCalendar(semester: semester)
        for c in courses where c.semesterId == semester.id {
            var exByWeek: [Int: CourseException] = [:]
            for ex in exceptions where ex.courseId == c.id && ex.deletedAt == nil {
                exByWeek[ex.week] = ex
            }
            var desc = "第\(c.startPeriod) - \(c.endPeriod)节"
            if !c.room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { desc += "\n\(c.room)" }
            if !c.teacher.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { desc += "\n\(c.teacher)" }
            guard let sTime = cal.startTime(period: c.startPeriod),
                  let eTime = cal.endTime(period: c.endPeriod)
            else { continue }

            let sortedWeeks = c.weeks.sorted()
            var runs: [[Int]] = []
            var cur: [Int] = []
            for w in sortedWeeks {
                if let last = cur.last, w != last + 1 {
                    runs.append(cur)
                    cur = []
                }
                cur.append(w)
            }
            if !cur.isEmpty { runs.append(cur) }

            for (idx, run) in runs.enumerated() {
                guard let firstWeek = run.first else { continue }
                let firstStart = cal.dateOf(week: firstWeek, dayOfWeek: c.dayOfWeek).atTime(sTime)
                let firstEnd = cal.dateOf(week: firstWeek, dayOfWeek: c.dayOfWeek).atTime(eTime)
                emit("BEGIN:VEVENT")
                emit("UID:\(c.id)-\(idx)@goke")
                emit("DTSTAMP:\(stamp)")
                emit("DTSTART:\(formatUTC(firstStart, zone: zone))")
                emit("DTEND:\(formatUTC(firstEnd, zone: zone))")
                emit("SUMMARY:\(escape(c.name))")
                let loc = [c.room, c.teacher].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.joined(separator: " ")
                emit("LOCATION:\(escape(loc))")
                emit("DESCRIPTION:\(escape(desc))")
                if run.count > 1 {
                    emit("RRULE:FREQ=WEEKLY;COUNT=\(run.count)")
                }
                let exWeeks = run.filter { exByWeek[$0] != nil }
                if !exWeeks.isEmpty {
                    let exVals = exWeeks.map { w in
                        formatUTC(cal.dateOf(week: w, dayOfWeek: c.dayOfWeek).atTime(sTime), zone: zone)
                    }.joined(separator: ",")
                    emit("EXDATE:\(exVals)")
                }
                writeAlarm(minutes: c.remindMinutes, summary: c.name, emit: emit)
                emit("END:VEVENT")
            }

            for (w, ex) in exByWeek.sorted(by: { $0.key < $1.key }) {
                guard ex.type == .RESCHEDULE, c.weeks.contains(w) else { continue }
                let nw = ex.newWeek ?? w
                let nd = ex.newDayOfWeek ?? c.dayOfWeek
                guard let st = cal.startTime(period: ex.newStartPeriod ?? c.startPeriod),
                      let et = cal.endTime(period: ex.newEndPeriod ?? c.endPeriod)
                else { continue }
                emit("BEGIN:VEVENT")
                emit("UID:\(ex.id)@goke")
                emit("DTSTAMP:\(stamp)")
                emit("DTSTART:\(formatUTC(cal.dateOf(week: nw, dayOfWeek: nd).atTime(st), zone: zone))")
                emit("DTEND:\(formatUTC(cal.dateOf(week: nw, dayOfWeek: nd).atTime(et), zone: zone))")
                emit("SUMMARY:\(escape(c.name))")
                emit("LOCATION:\(escape(ex.newRoom ?? c.room))")
                emit("END:VEVENT")
            }
        }
    }

    private static func writeTask(_ t: GokeTask, zone: TimeZone, stamp: String, emit: (String) -> Void) {
        emit("BEGIN:VTODO")
        emit("UID:\(t.id)@goke")
        emit("DTSTAMP:\(stamp)")
        emit("SUMMARY:\(escape(t.title))")
        if !t.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            emit("DESCRIPTION:\(escape(t.note))")
        }
        if let due = t.due {
            if t.dueHasTime {
                emit("DUE:\(formatUTC(due, zone: zone))")
            } else {
                emit("DUE;VALUE=DATE:\(due.date.basicISOString)")
            }
        }
        if let startDate = t.startDate {
            emit("DTSTART;VALUE=DATE:\(startDate.basicISOString)")
        }
        let icsPriority: Int
        switch t.priority {
        case 3: icsPriority = 1
        case 2: icsPriority = 5
        case 1: icsPriority = 9
        default: icsPriority = 0
        }
        if icsPriority > 0 { emit("PRIORITY:\(icsPriority)") }
        emit(t.done ? "STATUS:COMPLETED" : "STATUS:NEEDS-ACTION")
        writeAlarm(minutes: t.remindMinutes, summary: t.title, emit: emit)
        emit("END:VTODO")
    }

    // MARK: - Importer Helpers

    public static func looksLikeTimetable(_ cal: IcsCalendar) -> Bool {
        if cal.prodId.localizedCaseInsensitiveContains("WakeUp") { return true }
        let matched = cal.events.filter { ev in
            periodRegex.firstMatch(in: ev.description, range: NSRange(ev.description.startIndex..., in: ev.description)) != nil
        }.count
        return matched * 2 >= max(1, cal.events.count)
    }

    public static func suggestWeek1Monday(_ cal: IcsCalendar) -> LocalDate? {
        cal.events.compactMap { $0.start?.date }.min()?.startOfWeekMonday
    }

    private struct CourseKey: Hashable {
        let name: String
        let teacher: String
        let room: String
        let dow: Int
        let sp: Int
        let ep: Int
    }

    public static func toCourses(
        _ cal: IcsCalendar,
        semester: Semester,
        now: Int64,
        nextId: () -> String = { UUID().uuidString.lowercased() },
        zone: TimeZone = shanghaiTimeZone
    ) -> ImportResult<TimetableImport> {
        let sc = SemesterCalendar(semester: semester)
        var warnings: [String] = []
        var skipped: [String] = []
        var keyOrder: [CourseKey] = []
        var weeksByKey: [CourseKey: Set<Int>] = [:]
        var remindByKey: [CourseKey: Int?] = [:]
        var maxWeek = 0

        for ev in cal.events {
            guard let start = ev.start else { continue }
            let end = ev.end ?? start
            let match = periodRegex.firstMatch(in: ev.description, range: NSRange(ev.description.startIndex..., in: ev.description))
            let lines = ev.description.split(separator: "\n", omittingEmptySubsequences: false).map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            let detail = match != nil ? Array(lines.dropFirst()) : lines
            let room = detail.first ?? ""
            let teacher = (detail.count > 1 ? detail[1] : "").replacingOccurrences(of: ",", with: "、")

            let sp: Int?
            let ep: Int?
            if let m = match,
               let r1 = Range(m.range(at: 1), in: ev.description),
               let r2 = Range(m.range(at: 2), in: ev.description) {
                sp = Int(ev.description[r1])
                ep = Int(ev.description[r2])
            } else {
                sp = sc.periodAtOrBefore(start.time)
                ep = sc.periodEndingAtOrAfter(end.time)
            }
            guard let startP = sp, let endP = ep, endP >= startP else {
                skipped.append(ev.summary)
                continue
            }

            let dates = occurrenceDates(ev: ev, start: start, zone: zone)
            let resolvedRoom = room.isEmpty ? (match == nil ? ev.location : "") : room
            let key = CourseKey(name: ev.summary, teacher: teacher, room: resolvedRoom, dow: start.dayOfWeek, sp: startP, ep: endP)
            if weeksByKey[key] == nil {
                keyOrder.append(key)
                weeksByKey[key] = []
            }
            for d in dates {
                let w = sc.weekOf(d)
                if w >= 1 {
                    weeksByKey[key, default: []].insert(w)
                    maxWeek = max(maxWeek, w)
                }
            }
            remindByKey[key] = ev.alarmMinutes.flatMap { $0 >= 0 ? $0 : nil }
        }

        let colors = Palette.assign(names: keyOrder.map(\.name))
        let courses = keyOrder.compactMap { k -> Course? in
            let weeks = weeksByKey[k] ?? []
            guard !weeks.isEmpty else { return nil }
            return Course(
                id: nextId(),
                semesterId: semester.id,
                name: k.name,
                teacher: k.teacher,
                room: k.room,
                dayOfWeek: k.dow,
                startPeriod: k.sp,
                endPeriod: k.ep,
                weeks: weeks,
                colorIndex: colors[k.name] ?? 0,
                remindMinutes: remindByKey[k] ?? nil,
                source: .IMPORT,
                updatedAt: now
            )
        }
        if maxWeek > semester.totalWeeks {
            warnings.append("weeks_exceed:\(maxWeek)")
        }
        return ImportResult(value: TimetableImport(courses: courses, maxWeek: maxWeek, skipped: skipped), warnings: warnings)
    }

    private static func occurrenceDates(ev: IcsComponent, start: LocalDateTime, zone: TimeZone) -> [LocalDate] {
        guard let rule = ev.rrule else { return [start.date] }
        let parts = rruleParts(rule)
        guard let freq = parts["FREQ"] else { return [start.date] }
        let interval = max(1, parts["INTERVAL"].flatMap(Int.init) ?? 1)
        let count = parts["COUNT"].flatMap(Int.init)
        let until = parts["UNTIL"].flatMap { parseUntil($0, zone: zone) }
        let stepDays: Int
        switch freq {
        case "WEEKLY": stepDays = 7 * interval
        case "DAILY": stepDays = interval
        default: return [start.date]
        }
        var out: [LocalDate] = []
        var k = 0
        while out.count < 400 {
            let dt = start.addingDays(k * stepDays)
            if let until, dt > until { break }
            if let count, k >= count { break }
            if until == nil && count == nil && k >= 60 { break }
            if !ev.exDates.contains(dt.date) { out.append(dt.date) }
            k += 1
        }
        return out
    }

    public static func toEvents(
        _ cal: IcsCalendar,
        now: Int64,
        nextId: () -> String = { UUID().uuidString.lowercased() },
        zone: TimeZone = shanghaiTimeZone
    ) -> ImportResult<[Event]> {
        var out: [Event] = []
        for ev in cal.events {
            guard let start = ev.start else { continue }
            var end = ev.end ?? (ev.allDay ? start : start.addingHours(1))
            if ev.allDay && end > start { end = end.addingDays(-1) }
            if end < start { end = start }
            let trimmedTitle = ev.summary.trimmingCharacters(in: .whitespacesAndNewlines)
            out.append(
                Event(
                    id: nextId(),
                    title: trimmedTitle.isEmpty ? "(无标题)" : ev.summary,
                    start: start,
                    end: end,
                    allDay: ev.allDay,
                    location: ev.location,
                    note: ev.description,
                    colorIndex: 4,
                    remindMinutes: ev.alarmMinutes.flatMap { $0 >= 0 ? $0 : nil },
                    recurrence: ev.rrule.flatMap { parseRecurrence($0, exDates: ev.exDates, zone: zone) },
                    kind: .NORMAL,
                    source: .IMPORT,
                    updatedAt: now
                )
            )
        }
        return ImportResult(value: out)
    }

    public static func toTasks(
        _ cal: IcsCalendar,
        now: Int64,
        nextId: () -> String = { UUID().uuidString.lowercased() }
    ) -> ImportResult<[GokeTask]> {
        let out = cal.todos.map { t -> GokeTask in
            let due = t.due ?? t.end
            let trimmedTitle = t.summary.trimmingCharacters(in: .whitespacesAndNewlines)
            let priority: Int
            switch t.priority {
            case .some(1...4): priority = 3
            case .some(5): priority = 2
            case .some(6...9): priority = 1
            default: priority = 0
            }
            return GokeTask(
                id: nextId(),
                title: trimmedTitle.isEmpty ? "(无标题)" : t.summary,
                note: t.description,
                priority: priority,
                startDate: t.start?.date,
                due: due,
                dueHasTime: due != nil && !t.allDay && due?.time != .midnight,
                done: t.completed || t.status == "COMPLETED",
                remindMinutes: t.alarmMinutes.flatMap { $0 >= 0 ? $0 : nil },
                source: .IMPORT,
                updatedAt: now
            )
        }
        return ImportResult(value: out)
    }

    public static func parseRecurrence(
        _ rrule: String,
        exDates: Set<LocalDate>,
        zone: TimeZone = shanghaiTimeZone
    ) -> Recurrence? {
        let p = rruleParts(rrule)
        guard let freqRaw = p["FREQ"], let freq = Freq(rawValue: freqRaw) else { return nil }
        let days = Set(
            (p["BYDAY"] ?? "").split(separator: ",").compactMap { token -> Int? in
                switch String(token.suffix(2)) {
                case "MO": return 1
                case "TU": return 2
                case "WE": return 3
                case "TH": return 4
                case "FR": return 5
                case "SA": return 6
                case "SU": return 7
                default: return nil
                }
            }
        )
        let monthDays = Set(
            (p["BYMONTHDAY"] ?? "").split(separator: ",").compactMap { token -> Int? in
                guard let d = Int(token.trimmingCharacters(in: .whitespaces)), (1...31).contains(d) else { return nil }
                return d
            }
        )
        let months = Set(
            (p["BYMONTH"] ?? "").split(separator: ",").compactMap { token -> Int? in
                guard let m = Int(token.trimmingCharacters(in: .whitespaces)), (1...12).contains(m) else { return nil }
                return m
            }
        )
        let byDays: Set<Int>
        switch freq {
        case .WEEKLY: byDays = days
        case .MONTHLY, .YEARLY: byDays = monthDays
        case .DAILY: byDays = []
        }
        return Recurrence(
            freq: freq,
            interval: p["INTERVAL"].flatMap(Int.init) ?? 1,
            byDays: byDays,
            until: p["UNTIL"].flatMap { parseUntil($0, zone: zone)?.date },
            count: p["COUNT"].flatMap(Int.init),
            exDates: exDates,
            byMonths: freq == .YEARLY ? months : []
        )
    }
}
