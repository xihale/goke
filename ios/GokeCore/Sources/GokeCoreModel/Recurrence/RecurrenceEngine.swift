import Foundation

/// Expands RRULE-subset recurrences (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), matching Android `RecurrenceEngine`.
public enum RecurrenceEngine {
    public static let maxIterations = 6000

    /// Generates all dates starting from `anchor` that fall within `[from, to]` (inclusive).
    /// Matches Android semantics: `count` counts generated dates starting from `anchor` before `exDates` filtering.
    public static func dates(
        rule: Recurrence,
        anchor: LocalDate,
        from: LocalDate,
        to: LocalDate
    ) -> [LocalDate] {
        let interval = max(1, rule.interval)
        var result: [LocalDate] = []
        var produced = 0
        let limit: LocalDate = rule.until.map { min($0, to) } ?? to

        func accept(_ d: LocalDate) -> Bool {
            if d < anchor { return true }
            if let until = rule.until, d > until { return false }
            if let count = rule.count, produced >= count { return false }
            produced += 1
            if !rule.exDates.contains(d) && d >= from && d <= to {
                result.append(d)
            }
            return d <= limit
        }

        var iter = 0
        switch rule.freq {
        case .DAILY:
            var k = 0
            while iter < maxIterations {
                iter += 1
                let d = anchor.addingDays(k * interval)
                if !accept(d) { break }
                k += 1
            }

        case .WEEKLY:
            let days = (rule.byDays.isEmpty ? [anchor.dayOfWeek] : rule.byDays.sorted())
            let firstWeek = anchor.startOfWeekMonday
            var k = 0
            outer: while iter < maxIterations {
                iter += 1
                let ws = firstWeek.addingWeeks(k * interval)
                for dow in days {
                    let d = ws.addingDays(dow - 1)
                    if d < anchor { continue }
                    if !accept(d) { break outer }
                }
                k += 1
            }

        case .MONTHLY:
            let days = (rule.byDays.isEmpty ? [anchor.day] : rule.byDays.sorted())
            let anchorMonthStart = anchor.withDayOfMonth(1)
            var k = 0
            outer: while iter < maxIterations {
                iter += 1
                let m = anchorMonthStart.addingMonths(k * interval)
                if m > limit { break }
                for day in days {
                    if day < 1 || day > m.lengthOfMonth { continue }
                    if !accept(m.withDayOfMonth(day)) { break outer }
                }
                k += 1
            }

        case .YEARLY:
            let months = (rule.byMonths.isEmpty ? [anchor.month] : rule.byMonths.sorted())
            let days = (rule.byDays.isEmpty ? [anchor.day] : rule.byDays.sorted())
            let anchorYearStart = LocalDate(year: anchor.year, month: 1, day: 1)
            var k = 0
            outer: while iter < maxIterations {
                iter += 1
                let y = anchorYearStart.addingYears(k * interval)
                if y > limit { break }
                for mo in months where (1...12).contains(mo) {
                    let md = LocalDate(year: y.year, month: mo, day: 1)
                    if md > limit { break }
                    for day in days {
                        if day < 1 || day > md.lengthOfMonth { continue }
                        if !accept(md.withDayOfMonth(day)) { break outer }
                    }
                }
                k += 1
            }
        }
        return result
    }

    /// Returns the next occurrence date strictly after `after` (searches up to 8 years ahead).
    public static func next(
        rule: Recurrence,
        anchor: LocalDate,
        after: LocalDate
    ) -> LocalDate? {
        dates(rule: rule, anchor: anchor, from: after.addingDays(1), to: after.addingYears(8)).first
    }
}
