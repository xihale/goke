import Foundation

/// A class period interval within a semester timetable.
public struct PeriodTime: Sendable, Hashable, Codable, Identifiable {
    public var id: Int { index }
    public let index: Int
    public let start: LocalTime
    public let end: LocalTime

    public init(index: Int, start: LocalTime, end: LocalTime) {
        self.index = index
        self.start = start
        self.end = end
    }
}

/// GDUT standard 12-period timetable schedule (08:30 / 13:50 / 18:30).
public enum GdutPeriods {
    public static let `default`: [PeriodTime] = [
        PeriodTime(index: 1, start: LocalTime(hour: 8, minute: 30), end: LocalTime(hour: 9, minute: 15)),
        PeriodTime(index: 2, start: LocalTime(hour: 9, minute: 20), end: LocalTime(hour: 10, minute: 5)),
        PeriodTime(index: 3, start: LocalTime(hour: 10, minute: 25), end: LocalTime(hour: 11, minute: 10)),
        PeriodTime(index: 4, start: LocalTime(hour: 11, minute: 15), end: LocalTime(hour: 12, minute: 0)),
        PeriodTime(index: 5, start: LocalTime(hour: 13, minute: 50), end: LocalTime(hour: 14, minute: 35)),
        PeriodTime(index: 6, start: LocalTime(hour: 14, minute: 40), end: LocalTime(hour: 15, minute: 25)),
        PeriodTime(index: 7, start: LocalTime(hour: 15, minute: 30), end: LocalTime(hour: 16, minute: 15)),
        PeriodTime(index: 8, start: LocalTime(hour: 16, minute: 30), end: LocalTime(hour: 17, minute: 15)),
        PeriodTime(index: 9, start: LocalTime(hour: 17, minute: 20), end: LocalTime(hour: 18, minute: 5)),
        PeriodTime(index: 10, start: LocalTime(hour: 18, minute: 30), end: LocalTime(hour: 19, minute: 15)),
        PeriodTime(index: 11, start: LocalTime(hour: 19, minute: 20), end: LocalTime(hour: 20, minute: 5)),
        PeriodTime(index: 12, start: LocalTime(hour: 20, minute: 10), end: LocalTime(hour: 20, minute: 55)),
    ]

    /// Default Week 1 Monday (`2026-08-31`).
    public static let week1Monday = LocalDate(year: 2026, month: 8, day: 31)
}
