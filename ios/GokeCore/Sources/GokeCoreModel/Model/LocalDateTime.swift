import Foundation

/// Timezone-naive ISO-8601 calendar date (`YYYY-MM-DD`), matching `java.time.LocalDate`.
public struct LocalDate: Sendable, Hashable, Comparable, Codable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    public init?(validatingYear year: Int, month: Int, day: Int) {
        guard (1...12).contains(month) else { return nil }
        let maxDay = LocalDate.daysInMonth(year: year, month: month)
        guard (1...maxDay).contains(day) else { return nil }
        self.year = year
        self.month = month
        self.day = day
    }

    public static let min = LocalDate(year: -999_999, month: 1, day: 1)

    public static func isLeapYear(_ year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    public static func daysInMonth(year: Int, month: Int) -> Int {
        switch month {
        case 1, 3, 5, 7, 8, 10, 12: return 31
        case 4, 6, 9, 11: return 30
        case 2: return isLeapYear(year) ? 29 : 28
        default: return 0
        }
    }

    public var lengthOfMonth: Int {
        LocalDate.daysInMonth(year: year, month: month)
    }

    /// Days since 1970-01-01 using Howard Hinnant's civil calendar algorithm.
    public var epochDays: Int64 {
        let y = Int64(month <= 2 ? year - 1 : year)
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = Int64(month + (month > 2 ? -3 : 9))
        let doy = (153 * mp + 2) / 5 + Int64(day) - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    public static func fromEpochDays(_ z: Int64) -> LocalDate {
        let z1 = z + 719_468
        let era = (z1 >= 0 ? z1 : z1 - 146_096) / 146_097
        let doe = z1 - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp + (mp < 10 ? 3 : -9)
        let finalY = y + (m <= 2 ? 1 : 0)
        return LocalDate(year: Int(finalY), month: Int(m), day: Int(d))
    }

    /// ISO day of week: 1 = Monday ... 7 = Sunday.
    public var dayOfWeek: Int {
        let rem = ((epochDays + 3) % 7 + 7) % 7
        return Int(rem) + 1
    }

    public var startOfWeekMonday: LocalDate {
        addingDays(-(dayOfWeek - 1))
    }

    public var endOfWeekSunday: LocalDate {
        addingDays(7 - dayOfWeek)
    }

    public func addingDays(_ days: Int) -> LocalDate {
        LocalDate.fromEpochDays(epochDays + Int64(days))
    }

    public func addingWeeks(_ weeks: Int) -> LocalDate {
        LocalDate.fromEpochDays(epochDays + Int64(weeks) * 7)
    }

    public func addingMonths(_ months: Int) -> LocalDate {
        let totalMonths = year * 12 + (month - 1) + months
        let newYear = totalMonths >= 0 ? totalMonths / 12 : ((totalMonths + 1) / 12 - 1)
        let newMonth = totalMonths - newYear * 12 + 1
        let clampedDay = Swift.min(day, LocalDate.daysInMonth(year: newYear, month: newMonth))
        return LocalDate(year: newYear, month: newMonth, day: clampedDay)
    }

    public func addingYears(_ years: Int) -> LocalDate {
        let newYear = year + years
        let clampedDay = Swift.min(day, LocalDate.daysInMonth(year: newYear, month: month))
        return LocalDate(year: newYear, month: month, day: clampedDay)
    }

    public func withDayOfMonth(_ newDay: Int) -> LocalDate {
        LocalDate(year: year, month: month, day: newDay)
    }

    public func withMonth(_ newMonth: Int) -> LocalDate {
        let clampedDay = Swift.min(day, LocalDate.daysInMonth(year: year, month: newMonth))
        return LocalDate(year: year, month: newMonth, day: clampedDay)
    }

    public func daysUntil(_ other: LocalDate) -> Int {
        Int(other.epochDays - epochDays)
    }

    public func atStartOfDay() -> LocalDateTime {
        LocalDateTime(date: self, time: .midnight)
    }

    public func atTime(_ time: LocalTime) -> LocalDateTime {
        LocalDateTime(date: self, time: time)
    }

    public func atTime(hour: Int, minute: Int, second: Int = 0) -> LocalDateTime {
        LocalDateTime(date: self, time: LocalTime(hour: hour, minute: minute, second: second))
    }

    public static func today(in timeZone: TimeZone = .current) -> LocalDate {
        LocalDateTime.fromDate(Date(), in: timeZone).date
    }

    @inline(__always)
    fileprivate static func isASCIIWhitespace(_ b: UInt8) -> Bool {
        b == 32 || b == 9 || b == 10 || b == 13
    }

    @inline(__always)
    fileprivate static func parseASCIIInt(
        _ buf: UnsafeBufferPointer<UInt8>,
        start: Int,
        end: Int
    ) -> Int? {
        guard start < end else { return nil }
        var idx = start
        var negative = false
        if buf[idx] == 45 { // '-'
            negative = true
            idx += 1
            guard idx < end else { return nil }
        }
        var value = 0
        while idx < end {
            let b = buf[idx]
            guard b >= 48 && b <= 57 else { return nil }
            let digit = Int(b - 48)
            let (mul, o1) = value.multipliedReportingOverflow(by: 10)
            let (sum, o2) = mul.addingReportingOverflow(digit)
            if o1 || o2 { return nil }
            value = sum
            idx += 1
        }
        return negative ? -value : value
    }

    fileprivate static func parseUTF8(_ buf: UnsafeBufferPointer<UInt8>, start rawStart: Int, end rawEnd: Int) -> LocalDate? {
        var s = rawStart
        var e = rawEnd
        while s < e && isASCIIWhitespace(buf[s]) { s += 1 }
        while e > s && isASCIIWhitespace(buf[e - 1]) { e -= 1 }
        let len = e - s
        if len == 8 {
            var allDigits = true
            var i = s
            while i < e {
                let b = buf[i]
                if b < 48 || b > 57 { allDigits = false; break }
                i += 1
            }
            if allDigits {
                guard let y = parseASCIIInt(buf, start: s, end: s + 4),
                      let m = parseASCIIInt(buf, start: s + 4, end: s + 6),
                      let d = parseASCIIInt(buf, start: s + 6, end: s + 8)
                else { return nil }
                return LocalDate(validatingYear: y, month: m, day: d)
            }
        }
        // Standard YYYY-MM-DD (supports optional leading '-' for negative years)
        var firstDash = -1
        var secondDash = -1
        var i = s
        if i < e && buf[i] == 45 { i += 1 }
        while i < e {
            if buf[i] == 45 {
                if firstDash < 0 { firstDash = i }
                else if secondDash < 0 { secondDash = i }
                else { return nil }
            }
            i += 1
        }
        guard firstDash > s, secondDash > firstDash + 1, secondDash + 1 < e else { return nil }
        guard let y = parseASCIIInt(buf, start: s, end: firstDash),
              let m = parseASCIIInt(buf, start: firstDash + 1, end: secondDash),
              let d = parseASCIIInt(buf, start: secondDash + 1, end: e)
        else { return nil }
        return LocalDate(validatingYear: y, month: m, day: d)
    }

    public static func parse(_ text: String) -> LocalDate? {
        var copy = text
        return copy.withUTF8 { buf in
            parseUTF8(buf, start: 0, end: buf.count)
        }
    }

    public var basicISOString: String {
        guard year >= 0 && year <= 9999 && month >= 0 && month <= 99 && day >= 0 && day <= 99 else {
            return String(format: "%04d%02d%02d", year, month, day)
        }
        return String(unsafeUninitializedCapacity: 8) { buf in
            buf[0] = UInt8(48 + (year / 1000) % 10)
            buf[1] = UInt8(48 + (year / 100) % 10)
            buf[2] = UInt8(48 + (year / 10) % 10)
            buf[3] = UInt8(48 + year % 10)
            buf[4] = UInt8(48 + (month / 10) % 10)
            buf[5] = UInt8(48 + month % 10)
            buf[6] = UInt8(48 + (day / 10) % 10)
            buf[7] = UInt8(48 + day % 10)
            return 8
        }
    }

    public var description: String {
        guard year >= 0 && year <= 9999 && month >= 0 && month <= 99 && day >= 0 && day <= 99 else {
            return String(format: "%04d-%02d-%02d", year, month, day)
        }
        return String(unsafeUninitializedCapacity: 10) { buf in
            buf[0] = UInt8(48 + (year / 1000) % 10)
            buf[1] = UInt8(48 + (year / 100) % 10)
            buf[2] = UInt8(48 + (year / 10) % 10)
            buf[3] = UInt8(48 + year % 10)
            buf[4] = 45 // '-'
            buf[5] = UInt8(48 + (month / 10) % 10)
            buf[6] = UInt8(48 + month % 10)
            buf[7] = 45 // '-'
            buf[8] = UInt8(48 + (day / 10) % 10)
            buf[9] = UInt8(48 + day % 10)
            return 10
        }
    }

    public static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        if lhs.year != rhs.year { return lhs.year < rhs.year }
        if lhs.month != rhs.month { return lhs.month < rhs.month }
        return lhs.day < rhs.day
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let parsed = LocalDate.parse(raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid LocalDate: \(raw)")
        }
        self = parsed
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

/// Timezone-naive ISO-8601 wall-clock time (`HH:mm` or `HH:mm:ss`), matching `java.time.LocalTime`.
public struct LocalTime: Sendable, Hashable, Comparable, Codable, CustomStringConvertible {
    public let hour: Int
    public let minute: Int
    public let second: Int

    public init(hour: Int, minute: Int, second: Int = 0) {
        self.hour = hour
        self.minute = minute
        self.second = second
    }

    public init?(validatingHour hour: Int, minute: Int, second: Int = 0) {
        guard (0...23).contains(hour), (0...59).contains(minute), (0...59).contains(second) else { return nil }
        self.hour = hour
        self.minute = minute
        self.second = second
    }

    public static let midnight = LocalTime(hour: 0, minute: 0, second: 0)
    public static let endOfDay = LocalTime(hour: 23, minute: 59, second: 59)

    public var totalMinutes: Int { hour * 60 + minute }
    public var totalSeconds: Int { hour * 3600 + minute * 60 + second }

    fileprivate static func parseUTF8(_ buf: UnsafeBufferPointer<UInt8>, start rawStart: Int, end rawEnd: Int) -> LocalTime? {
        var s = rawStart
        var e = rawEnd
        while s < e && LocalDate.isASCIIWhitespace(buf[s]) { s += 1 }
        while e > s && LocalDate.isASCIIWhitespace(buf[e - 1]) { e -= 1 }
        // Strip fractional seconds if present (e.g. "08:30:00.123")
        var dot = s
        while dot < e {
            if buf[dot] == 46 { // '.'
                e = dot
                break
            }
            dot += 1
        }
        var firstColon = -1
        var secondColon = -1
        var i = s
        while i < e {
            if buf[i] == 58 { // ':'
                if firstColon < 0 { firstColon = i }
                else if secondColon < 0 { secondColon = i }
                else { return nil }
            }
            i += 1
        }
        guard firstColon > s, firstColon + 1 < e else { return nil }
        if secondColon < 0 {
            guard let h = LocalDate.parseASCIIInt(buf, start: s, end: firstColon),
                  let m = LocalDate.parseASCIIInt(buf, start: firstColon + 1, end: e)
            else { return nil }
            return LocalTime(validatingHour: h, minute: m, second: 0)
        } else {
            guard secondColon > firstColon + 1, secondColon + 1 < e,
                  let h = LocalDate.parseASCIIInt(buf, start: s, end: firstColon),
                  let m = LocalDate.parseASCIIInt(buf, start: firstColon + 1, end: secondColon),
                  let sec = LocalDate.parseASCIIInt(buf, start: secondColon + 1, end: e)
            else { return nil }
            return LocalTime(validatingHour: h, minute: m, second: sec)
        }
    }

    public static func parse(_ text: String) -> LocalTime? {
        var copy = text
        return copy.withUTF8 { buf in
            parseUTF8(buf, start: 0, end: buf.count)
        }
    }

    public var description: String {
        guard hour >= 0 && hour <= 99 && minute >= 0 && minute <= 99 && second >= 0 && second <= 99 else {
            if second == 0 {
                return String(format: "%02d:%02d", hour, minute)
            }
            return String(format: "%02d:%02d:%02d", hour, minute, second)
        }
        if second == 0 {
            return String(unsafeUninitializedCapacity: 5) { buf in
                buf[0] = UInt8(48 + (hour / 10) % 10)
                buf[1] = UInt8(48 + hour % 10)
                buf[2] = 58 // ':'
                buf[3] = UInt8(48 + (minute / 10) % 10)
                buf[4] = UInt8(48 + minute % 10)
                return 5
            }
        }
        return String(unsafeUninitializedCapacity: 8) { buf in
            buf[0] = UInt8(48 + (hour / 10) % 10)
            buf[1] = UInt8(48 + hour % 10)
            buf[2] = 58 // ':'
            buf[3] = UInt8(48 + (minute / 10) % 10)
            buf[4] = UInt8(48 + minute % 10)
            buf[5] = 58 // ':'
            buf[6] = UInt8(48 + (second / 10) % 10)
            buf[7] = UInt8(48 + second % 10)
            return 8
        }
    }

    public static func < (lhs: LocalTime, rhs: LocalTime) -> Bool {
        lhs.totalSeconds < rhs.totalSeconds
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let parsed = LocalTime.parse(raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid LocalTime: \(raw)")
        }
        self = parsed
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

/// Timezone-naive ISO-8601 date-time (`YYYY-MM-DDTHH:mm`), matching `java.time.LocalDateTime`.
public struct LocalDateTime: Sendable, Hashable, Comparable, Codable, CustomStringConvertible {
    public let date: LocalDate
    public let time: LocalTime

    public init(date: LocalDate, time: LocalTime) {
        self.date = date
        self.time = time
    }

    public init(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0) {
        self.date = LocalDate(year: year, month: month, day: day)
        self.time = LocalTime(hour: hour, minute: minute, second: second)
    }

    public var year: Int { date.year }
    public var month: Int { date.month }
    public var day: Int { date.day }
    public var hour: Int { time.hour }
    public var minute: Int { time.minute }
    public var second: Int { time.second }
    public var dayOfWeek: Int { date.dayOfWeek }

    /// Seconds relative to 1970-01-01T00:00:00 (timezone-naive).
    public var naiveEpochSeconds: Int64 {
        date.epochDays * 86_400 + Int64(time.totalSeconds)
    }

    public static func fromNaiveEpochSeconds(_ seconds: Int64) -> LocalDateTime {
        let day: Int64
        let rem: Int64
        if seconds >= 0 {
            day = seconds / 86_400
            rem = seconds % 86_400
        } else {
            day = (seconds - 86_399) / 86_400
            rem = seconds - day * 86_400
        }
        let h = Int(rem / 3600)
        let m = Int((rem % 3600) / 60)
        let s = Int(rem % 60)
        return LocalDateTime(date: LocalDate.fromEpochDays(day), time: LocalTime(hour: h, minute: m, second: s))
    }

    public func addingSeconds(_ seconds: Int64) -> LocalDateTime {
        LocalDateTime.fromNaiveEpochSeconds(naiveEpochSeconds + seconds)
    }

    public func addingMinutes(_ minutes: Int) -> LocalDateTime {
        addingSeconds(Int64(minutes) * 60)
    }

    public func addingHours(_ hours: Int) -> LocalDateTime {
        addingSeconds(Int64(hours) * 3600)
    }

    public func addingDays(_ days: Int) -> LocalDateTime {
        LocalDateTime(date: date.addingDays(days), time: time)
    }

    public func secondsUntil(_ other: LocalDateTime) -> Int64 {
        other.naiveEpochSeconds - naiveEpochSeconds
    }

    public func toDate(in timeZone: TimeZone = .current) -> Date {
        let naive = naiveEpochSeconds
        let guess1 = Date(timeIntervalSince1970: TimeInterval(naive - Int64(timeZone.secondsFromGMT())))
        let offset1 = Int64(timeZone.secondsFromGMT(for: guess1))
        let candidate = Date(timeIntervalSince1970: TimeInterval(naive - offset1))
        let offset2 = Int64(timeZone.secondsFromGMT(for: candidate))
        if offset2 == offset1 {
            return candidate
        }
        return Date(timeIntervalSince1970: TimeInterval(naive - offset2))
    }

    public static func fromDate(_ instant: Date, in timeZone: TimeZone = .current) -> LocalDateTime {
        let offset = Int64(timeZone.secondsFromGMT(for: instant))
        let unixSeconds = Int64(floor(instant.timeIntervalSince1970))
        return LocalDateTime.fromNaiveEpochSeconds(unixSeconds + offset)
    }

    public static func now(in timeZone: TimeZone = .current) -> LocalDateTime {
        fromDate(Date(), in: timeZone)
    }

    /// Leniently parses `2026-09-01T08:30`, `2026-09-01 08:30`, `2026-09-01T08:30:00`, or date-only `2026-09-01` (as `00:00`).
    public static func parseLenient(_ text: String) -> LocalDateTime? {
        var copy = text
        return copy.withUTF8 { buf in
            var s = 0
            var e = buf.count
            while s < e && LocalDate.isASCIIWhitespace(buf[s]) { s += 1 }
            while e > s && LocalDate.isASCIIWhitespace(buf[e - 1]) { e -= 1 }
            guard s < e else { return nil }
            var sep = -1
            var i = s
            while i < e {
                let b = buf[i]
                if b == 84 || b == 32 { // 'T' or ' '
                    sep = i
                    break
                }
                i += 1
            }
            if sep >= 0 {
                guard let d = LocalDate.parseUTF8(buf, start: s, end: sep),
                      let t = LocalTime.parseUTF8(buf, start: sep + 1, end: e)
                else { return nil }
                return LocalDateTime(date: d, time: t)
            }
            guard let d = LocalDate.parseUTF8(buf, start: s, end: e) else { return nil }
            return d.atStartOfDay()
        }
    }

    public var description: String {
        "\(date.description)T\(time.description)"
    }

    public static func < (lhs: LocalDateTime, rhs: LocalDateTime) -> Bool {
        if lhs.date != rhs.date { return lhs.date < rhs.date }
        return lhs.time < rhs.time
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let parsed = LocalDateTime.parseLenient(raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid LocalDateTime: \(raw)")
        }
        self = parsed
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

/// Shared JSON codec configuration matching Android's `MintJson` (`encodeDefaults = true`, `_t` discriminator, sorted keys optional).
public enum MintJSON {
    public static func makeEncoder(pretty: Bool = false) -> JSONEncoder {
        let encoder = JSONEncoder()
        var formatting: JSONEncoder.OutputFormatting = [.withoutEscapingSlashes]
        if pretty {
            formatting.insert(.prettyPrinted)
            formatting.insert(.sortedKeys)
        }
        encoder.outputFormatting = formatting
        return encoder
    }

    public static func makeDecoder() -> JSONDecoder {
        JSONDecoder()
    }

    public static func encodeToString<T: Encodable>(_ value: T, pretty: Bool = false) throws -> String {
        let data = try makeEncoder(pretty: pretty).encode(value)
        return String(decoding: data, as: UTF8.self)
    }

    public static func decodeFromString<T: Decodable>(_ type: T.Type, from text: String) throws -> T {
        try makeDecoder().decode(type, from: Data(text.utf8))
    }
}

/// Type-safe Sendable JSON AST used by `WireRecord.data` and legacy import paths.
public enum JSONValue: Sendable, Hashable, Codable {
    case null
    case bool(Bool)
    case int(Int64)
    case double(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    public var objectValue: [String: JSONValue]? {
        if case .object(let dict) = self { return dict }
        return nil
    }

    public var arrayValue: [JSONValue]? {
        if case .array(let arr) = self { return arr }
        return nil
    }

    public var stringValue: String? {
        if case .string(let s) = self { return s }
        return nil
    }

    public var int64Value: Int64? {
        switch self {
        case .int(let v): return v
        case .double(let d): return Int64(d)
        default: return nil
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let b = try? container.decode(Bool.self) {
            self = .bool(b)
        } else if let i = try? container.decode(Int64.self) {
            self = .int(i)
        } else if let d = try? container.decode(Double.self) {
            self = .double(d)
        } else if let s = try? container.decode(String.self) {
            self = .string(s)
        } else if let arr = try? container.decode([JSONValue].self) {
            self = .array(arr)
        } else if let obj = try? container.decode([String: JSONValue].self) {
            self = .object(obj)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON value")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .null: try container.encodeNil()
        case .bool(let b): try container.encode(b)
        case .int(let i): try container.encode(i)
        case .double(let d): try container.encode(d)
        case .string(let s): try container.encode(s)
        case .array(let arr): try container.encode(arr)
        case .object(let obj): try container.encode(obj)
        }
    }
}
