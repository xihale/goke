import Foundation

/// Week/period calendar math for a semester: `weekOf(date) = floorDiv(days(week1Monday, date), 7) + 1`.
public struct SemesterCalendar: Sendable, Hashable {
    public let semester: Semester
    public let base: LocalDate
    public let periods: [PeriodTime]

    public init(semester: Semester) {
        self.semester = semester
        self.base = semester.week1Monday.startOfWeekMonday
        self.periods = semester.periods.sorted { $0.index < $1.index }
    }

    public var totalWeeks: Int { semester.totalWeeks }

    /// Week number of `date` (<= 0 before Week 1).
    public func weekOf(_ date: LocalDate) -> Int {
        let diff = base.daysUntil(date)
        let q = diff >= 0 ? diff / 7 : ((diff - 6) / 7)
        return q + 1
    }

    /// Date for `(week, dayOfWeek)` where `dayOfWeek` is 1 = Monday ... 7 = Sunday.
    public func dateOf(week: Int, dayOfWeek: Int) -> LocalDate {
        base.addingDays((week - 1) * 7 + (dayOfWeek - 1))
    }

    public func weekStart(_ week: Int) -> LocalDate {
        dateOf(week: week, dayOfWeek: 1)
    }

    public func weekEnd(_ week: Int) -> LocalDate {
        dateOf(week: week, dayOfWeek: 7)
    }

    /// Clamped current week within `1...totalWeeks`.
    public func currentWeek(today: LocalDate) -> Int {
        let w = weekOf(today)
        return min(max(w, 1), max(1, totalWeeks))
    }

    public func isInSemester(_ date: LocalDate) -> Bool {
        let w = weekOf(date)
        return w >= 1 && w <= totalWeeks
    }

    public func period(_ index: Int) -> PeriodTime? {
        periods.first { $0.index == index }
    }

    public func startTime(period index: Int) -> LocalTime? {
        period(index)?.start
    }

    public func endTime(period index: Int) -> LocalTime? {
        period(index)?.end
    }

    public func periodAtOrBefore(_ time: LocalTime) -> Int? {
        periods.last { $0.start <= time }?.index
    }

    public func periodEndingAtOrAfter(_ time: LocalTime) -> Int? {
        periods.first { $0.end >= time }?.index
    }
}

public enum WeekSets {
    public static func all(_ total: Int) -> Set<Int> {
        total >= 1 ? Set(1...total) : []
    }

    public static func odd(_ total: Int) -> Set<Int> {
        total >= 1 ? Set((1...total).filter { $0 % 2 == 1 }) : []
    }

    public static func even(_ total: Int) -> Set<Int> {
        total >= 1 ? Set((1...total).filter { $0 % 2 == 0 }) : []
    }

    public static func range(_ from: Int, _ to: Int) -> Set<Int> {
        from <= to ? Set(from...to) : []
    }

    /// Formats a set of week numbers as `1-16`, `1,3,5-9`, etc.
    public static func format(_ weeks: Set<Int>) -> String {
        guard !weeks.isEmpty else { return "" }
        let sorted = weeks.sorted()
        var parts: [String] = []
        var s = sorted[0]
        var p = s
        for i in 1...sorted.count {
            let cur = i < sorted.count ? sorted[i] : nil
            if let cur, cur == p + 1 {
                p = cur
                continue
            }
            if s == p {
                parts.append("\(s)")
            } else if p == s + 1 {
                parts.append("\(s),\(p)")
            } else {
                parts.append("\(s)-\(p)")
            }
            if let cur {
                s = cur
                p = cur
            }
        }
        return parts.joined(separator: ",")
    }

    /// Parses strings like `1-16`, `1,3,5-9`, `1-16单`, `2-16双`.
    public static func parse(_ text: String, total: Int = 30) -> Set<Int> {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return [] }
        var result = Set<Int>()
        let tokens = t.split { $0 == "," || $0 == "，" || $0 == " " || $0 == "、" }
        for raw in tokens {
            var part = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if part.hasSuffix("周") { part.removeLast() }
            guard !part.isEmpty else { continue }
            let isOdd = part.hasSuffix("单")
            let isEven = part.hasSuffix("双")
            if isOdd || isEven { part.removeLast() }
            let rangeParts = part.split { $0 == "-" || $0 == "–" || $0 == "~" || $0 == "－" }
            guard let firstStr = rangeParts.first,
                  let a = Int(firstStr.trimmingCharacters(in: .whitespaces))
            else { continue }
            let b: Int
            if rangeParts.count > 1 {
                guard let parsedB = Int(rangeParts[1].trimmingCharacters(in: .whitespaces)) else { continue }
                b = parsedB
            } else {
                b = a
            }
            let lo = min(a, b)
            let hi = min(max(a, b), max(total, max(a, b)))
            if lo <= hi {
                for w in lo...hi {
                    if isOdd && w % 2 == 0 { continue }
                    if isEven && w % 2 == 1 { continue }
                    result.insert(w)
                }
            }
        }
        return result
    }
}
