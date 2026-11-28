import Foundation

public enum Source: String, Sendable, Hashable, Codable, CaseIterable {
    case MANUAL
    case CODE
    case AI
    case IMPORT
    case PARCEL
}

public enum Freq: String, Sendable, Hashable, Codable, CaseIterable {
    case DAILY
    case WEEKLY
    case MONTHLY
    case YEARLY
}

public enum EventKind: String, Sendable, Hashable, Codable, CaseIterable {
    case NORMAL
    case EXAM
    case COUNTDOWN
}

public enum ExceptionType: String, Sendable, Hashable, Codable, CaseIterable {
    case CANCEL
    case RESCHEDULE
}

public enum EntityType: String, Sendable, Hashable, Codable, CaseIterable {
    case SEMESTER
    case COURSE
    case EXCEPTION
    case EVENT
    case TASK
    case LIST
}

/// Normalizes reminder lead offsets (in minutes): merges legacy `primary` with `list`,
/// filters out negative values, deduplicates, and sorts ascending.
public enum Reminders {
    public static func normalize(primary: Int?, list: [Int]) -> [Int] {
        var seen = Set<Int>()
        var result: [Int] = []
        if let primary, primary >= 0, seen.insert(primary).inserted {
            result.append(primary)
        }
        for item in list where item >= 0 {
            if seen.insert(item).inserted {
                result.append(item)
            }
        }
        return result.sorted()
    }

    public static func normalize(_ list: [Int]) -> [Int] {
        normalize(primary: nil, list: list)
    }
}

public struct Recurrence: Sendable, Hashable, Codable {
    public var freq: Freq
    public var interval: Int
    public var byDays: Set<Int>
    public var until: LocalDate?
    public var count: Int?
    public var exDates: Set<LocalDate>
    public var byMonths: Set<Int>

    public init(
        freq: Freq,
        interval: Int = 1,
        byDays: Set<Int> = [],
        until: LocalDate? = nil,
        count: Int? = nil,
        exDates: Set<LocalDate> = [],
        byMonths: Set<Int> = []
    ) {
        self.freq = freq
        self.interval = interval
        self.byDays = byDays
        self.until = until
        self.count = count
        self.exDates = exDates
        self.byMonths = byMonths
    }

    private enum CodingKeys: String, CodingKey {
        case freq, interval, byDays, until, count, exDates, byMonths
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.freq = try c.decode(Freq.self, forKey: .freq)
        self.interval = try c.decodeIfPresent(Int.self, forKey: .interval) ?? 1
        self.byDays = Set(try c.decodeIfPresent([Int].self, forKey: .byDays) ?? [])
        self.until = try c.decodeIfPresent(LocalDate.self, forKey: .until)
        self.count = try c.decodeIfPresent(Int.self, forKey: .count)
        self.exDates = Set(try c.decodeIfPresent([LocalDate].self, forKey: .exDates) ?? [])
        self.byMonths = Set(try c.decodeIfPresent([Int].self, forKey: .byMonths) ?? [])
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(freq, forKey: .freq)
        try c.encode(interval, forKey: .interval)
        try c.encode(byDays.sorted(), forKey: .byDays)
        try c.encode(until, forKey: .until)
        try c.encode(count, forKey: .count)
        try c.encode(exDates.sorted(), forKey: .exDates)
        try c.encode(byMonths.sorted(), forKey: .byMonths)
    }
}

public struct Attachment: Sendable, Hashable, Codable {
    public var name: String
    public var mime: String
    public var file: String

    public var isImage: Bool {
        mime.lowercased().hasPrefix("image/")
    }

    public init(name: String, mime: String, file: String) {
        self.name = name
        self.mime = mime
        self.file = file
    }
}

public struct Semester: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var name: String
    public var week1Monday: LocalDate
    public var totalWeeks: Int
    public var periods: [PeriodTime]
    public var isCurrent: BooleanLiteralType
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public init(
        id: String = UUID().uuidString.lowercased(),
        name: String,
        week1Monday: LocalDate,
        totalWeeks: Int = 20,
        periods: [PeriodTime] = GdutPeriods.default,
        isCurrent: Bool = true,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil
    ) {
        self.id = id
        self.name = name
        self.week1Monday = week1Monday
        self.totalWeeks = totalWeeks
        self.periods = periods
        self.isCurrent = isCurrent
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> Semester {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, week1Monday, totalWeeks, periods, isCurrent, updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.name = try c.decode(String.self, forKey: .name)
        self.week1Monday = try c.decode(LocalDate.self, forKey: .week1Monday)
        self.totalWeeks = try c.decodeIfPresent(Int.self, forKey: .totalWeeks) ?? 20
        self.periods = try c.decodeIfPresent([PeriodTime].self, forKey: .periods) ?? GdutPeriods.default
        self.isCurrent = try c.decodeIfPresent(Bool.self, forKey: .isCurrent) ?? true
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(week1Monday, forKey: .week1Monday)
        try c.encode(totalWeeks, forKey: .totalWeeks)
        try c.encode(periods, forKey: .periods)
        try c.encode(isCurrent, forKey: .isCurrent)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

public struct Course: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var semesterId: String
    public var name: String
    public var teacher: String
    public var room: String
    /// 1 = Monday ... 7 = Sunday
    public var dayOfWeek: Int
    public var startPeriod: Int
    public var endPeriod: Int
    public var weeks: Set<Int>
    public var colorIndex: Int
    public var remindMinutes: Int?
    public var remindMinutesList: [Int]
    public var kind: EventKind
    public var remindPersistent: Bool
    /// Optional custom start clock (`"HH:mm"`), used by official exams whose time does not align to period boundaries.
    public var startTime: String?
    /// Optional custom end clock (`"HH:mm"`).
    public var endTime: String?
    public var note: String
    public var source: Source
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public var effectiveRemindMinutes: [Int] {
        Reminders.normalize(primary: remindMinutes, list: remindMinutesList)
    }

    public var isExam: Bool {
        kind == .EXAM
    }

    public var startLocalTime: LocalTime? {
        startTime.flatMap(LocalTime.parse)
    }

    public var endLocalTime: LocalTime? {
        endTime.flatMap(LocalTime.parse)
    }

    public init(
        id: String = UUID().uuidString.lowercased(),
        semesterId: String,
        name: String,
        teacher: String = "",
        room: String = "",
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeks: Set<Int>,
        colorIndex: Int = 0,
        remindMinutes: Int? = nil,
        note: String = "",
        source: Source = .MANUAL,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil,
        remindMinutesList: [Int] = [],
        kind: EventKind = .NORMAL,
        remindPersistent: Bool = false,
        startTime: String? = nil,
        endTime: String? = nil
    ) {
        self.id = id
        self.semesterId = semesterId
        self.name = name
        self.teacher = teacher
        self.room = room
        self.dayOfWeek = dayOfWeek
        self.startPeriod = startPeriod
        self.endPeriod = endPeriod
        self.weeks = weeks
        self.colorIndex = colorIndex
        self.remindMinutes = remindMinutes
        self.remindMinutesList = Reminders.normalize(remindMinutesList)
        self.kind = kind
        self.remindPersistent = remindPersistent
        self.startTime = startTime
        self.endTime = endTime
        self.note = note
        self.source = source
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> Course {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, semesterId, name, teacher, room, dayOfWeek, startPeriod, endPeriod, weeks
        case colorIndex, remindMinutes, remindMinutesList, kind, remindPersistent, startTime, endTime
        case note, source, updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.semesterId = try c.decode(String.self, forKey: .semesterId)
        self.name = try c.decode(String.self, forKey: .name)
        self.teacher = try c.decodeIfPresent(String.self, forKey: .teacher) ?? ""
        self.room = try c.decodeIfPresent(String.self, forKey: .room) ?? ""
        self.dayOfWeek = try c.decode(Int.self, forKey: .dayOfWeek)
        self.startPeriod = try c.decode(Int.self, forKey: .startPeriod)
        self.endPeriod = try c.decode(Int.self, forKey: .endPeriod)
        self.weeks = Set(try c.decodeIfPresent([Int].self, forKey: .weeks) ?? [])
        self.colorIndex = try c.decodeIfPresent(Int.self, forKey: .colorIndex) ?? 0
        let decodedRemindMinutes = try c.decodeIfPresent(Int.self, forKey: .remindMinutes)
        self.remindMinutes = decodedRemindMinutes
        if let decodedList = try c.decodeIfPresent([Int].self, forKey: .remindMinutesList) {
            self.remindMinutesList = Reminders.normalize(decodedList)
        } else {
            self.remindMinutesList = Reminders.normalize(primary: decodedRemindMinutes, list: [])
        }
        self.kind = try c.decodeIfPresent(EventKind.self, forKey: .kind) ?? .NORMAL
        self.remindPersistent = try c.decodeIfPresent(Bool.self, forKey: .remindPersistent) ?? false
        self.startTime = try c.decodeIfPresent(String.self, forKey: .startTime)
        self.endTime = try c.decodeIfPresent(String.self, forKey: .endTime)
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.source = try c.decodeIfPresent(Source.self, forKey: .source) ?? .MANUAL
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(semesterId, forKey: .semesterId)
        try c.encode(name, forKey: .name)
        try c.encode(teacher, forKey: .teacher)
        try c.encode(room, forKey: .room)
        try c.encode(dayOfWeek, forKey: .dayOfWeek)
        try c.encode(startPeriod, forKey: .startPeriod)
        try c.encode(endPeriod, forKey: .endPeriod)
        try c.encode(weeks.sorted(), forKey: .weeks)
        try c.encode(colorIndex, forKey: .colorIndex)
        try c.encode(remindMinutes, forKey: .remindMinutes)
        try c.encode(remindMinutesList, forKey: .remindMinutesList)
        try c.encode(kind, forKey: .kind)
        try c.encode(remindPersistent, forKey: .remindPersistent)
        try c.encodeIfPresent(startTime, forKey: .startTime)
        try c.encodeIfPresent(endTime, forKey: .endTime)
        try c.encode(note, forKey: .note)
        try c.encode(source, forKey: .source)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

public struct CourseException: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var courseId: String
    public var week: Int
    public var type: ExceptionType
    public var newWeek: Int?
    public var newDayOfWeek: Int?
    public var newStartPeriod: Int?
    public var newEndPeriod: Int?
    public var newRoom: String?
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public init(
        id: String = UUID().uuidString.lowercased(),
        courseId: String,
        week: Int,
        type: ExceptionType,
        newWeek: Int? = nil,
        newDayOfWeek: Int? = nil,
        newStartPeriod: Int? = nil,
        newEndPeriod: Int? = nil,
        newRoom: String? = nil,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil
    ) {
        self.id = id
        self.courseId = courseId
        self.week = week
        self.type = type
        self.newWeek = newWeek
        self.newDayOfWeek = newDayOfWeek
        self.newStartPeriod = newStartPeriod
        self.newEndPeriod = newEndPeriod
        self.newRoom = newRoom
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> CourseException {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, courseId, week, type, newWeek, newDayOfWeek, newStartPeriod, newEndPeriod, newRoom
        case updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.courseId = try c.decode(String.self, forKey: .courseId)
        self.week = try c.decode(Int.self, forKey: .week)
        self.type = try c.decode(ExceptionType.self, forKey: .type)
        self.newWeek = try c.decodeIfPresent(Int.self, forKey: .newWeek)
        self.newDayOfWeek = try c.decodeIfPresent(Int.self, forKey: .newDayOfWeek)
        self.newStartPeriod = try c.decodeIfPresent(Int.self, forKey: .newStartPeriod)
        self.newEndPeriod = try c.decodeIfPresent(Int.self, forKey: .newEndPeriod)
        self.newRoom = try c.decodeIfPresent(String.self, forKey: .newRoom)
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(courseId, forKey: .courseId)
        try c.encode(week, forKey: .week)
        try c.encode(type, forKey: .type)
        try c.encode(newWeek, forKey: .newWeek)
        try c.encode(newDayOfWeek, forKey: .newDayOfWeek)
        try c.encode(newStartPeriod, forKey: .newStartPeriod)
        try c.encode(newEndPeriod, forKey: .newEndPeriod)
        try c.encode(newRoom, forKey: .newRoom)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

public struct Event: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var title: String
    public var start: LocalDateTime
    public var end: LocalDateTime
    public var allDay: Bool
    public var location: String
    public var note: String
    public var colorIndex: Int
    public var category: String
    public var remindMinutes: Int?
    public var remindMinutesList: [Int]
    public var recurrence: Recurrence?
    public var kind: EventKind
    public var source: Source
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public var effectiveRemindMinutes: [Int] {
        Reminders.normalize(primary: remindMinutes, list: remindMinutesList)
    }

    public var isExam: Bool {
        kind == .EXAM
    }

    public init(
        id: String = UUID().uuidString.lowercased(),
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Bool = false,
        location: String = "",
        note: String = "",
        colorIndex: Int = 4,
        category: String = "",
        remindMinutes: Int? = nil,
        recurrence: Recurrence? = nil,
        kind: EventKind = .NORMAL,
        source: Source = .MANUAL,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil,
        remindMinutesList: [Int] = []
    ) {
        self.id = id
        self.title = title
        self.start = start
        self.end = end
        self.allDay = allDay
        self.location = location
        self.note = note
        self.colorIndex = colorIndex
        self.category = category
        self.remindMinutes = remindMinutes
        self.remindMinutesList = Reminders.normalize(remindMinutesList)
        self.recurrence = recurrence
        self.kind = kind
        self.source = source
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> Event {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, start, end, allDay, location, note, colorIndex, category
        case remindMinutes, remindMinutesList, recurrence, kind, source, updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decode(String.self, forKey: .title)
        self.start = try c.decode(LocalDateTime.self, forKey: .start)
        self.end = try c.decode(LocalDateTime.self, forKey: .end)
        self.allDay = try c.decodeIfPresent(Bool.self, forKey: .allDay) ?? false
        self.location = try c.decodeIfPresent(String.self, forKey: .location) ?? ""
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.colorIndex = try c.decodeIfPresent(Int.self, forKey: .colorIndex) ?? 4
        self.category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        let decodedRemindMinutes = try c.decodeIfPresent(Int.self, forKey: .remindMinutes)
        self.remindMinutes = decodedRemindMinutes
        if let decodedList = try c.decodeIfPresent([Int].self, forKey: .remindMinutesList) {
            self.remindMinutesList = Reminders.normalize(decodedList)
        } else {
            self.remindMinutesList = Reminders.normalize(primary: decodedRemindMinutes, list: [])
        }
        self.recurrence = try c.decodeIfPresent(Recurrence.self, forKey: .recurrence)
        self.kind = try c.decodeIfPresent(EventKind.self, forKey: .kind) ?? .NORMAL
        self.source = try c.decodeIfPresent(Source.self, forKey: .source) ?? .MANUAL
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(start, forKey: .start)
        try c.encode(end, forKey: .end)
        try c.encode(allDay, forKey: .allDay)
        try c.encode(location, forKey: .location)
        try c.encode(note, forKey: .note)
        try c.encode(colorIndex, forKey: .colorIndex)
        try c.encode(category, forKey: .category)
        try c.encode(remindMinutes, forKey: .remindMinutes)
        try c.encode(remindMinutesList, forKey: .remindMinutesList)
        try c.encode(recurrence, forKey: .recurrence)
        try c.encode(kind, forKey: .kind)
        try c.encode(source, forKey: .source)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

public struct TaskList: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var name: String
    public var colorIndex: Int
    public var sort: Int
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public init(
        id: String = UUID().uuidString.lowercased(),
        name: String,
        colorIndex: Int = 1,
        sort: Int = 0,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil
    ) {
        self.id = id
        self.name = name
        self.colorIndex = colorIndex
        self.sort = sort
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> TaskList {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, colorIndex, sort, updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.name = try c.decode(String.self, forKey: .name)
        self.colorIndex = try c.decodeIfPresent(Int.self, forKey: .colorIndex) ?? 1
        self.sort = try c.decodeIfPresent(Int.self, forKey: .sort) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(colorIndex, forKey: .colorIndex)
        try c.encode(sort, forKey: .sort)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

/// Named `GokeTask` in Swift to avoid shadowing `_Concurrency.Task`.
/// Serializes on the wire as `_t: "task"` / `kind: "Task"`.
public struct GokeTask: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var title: String
    public var note: String
    public var listId: String?
    public var tags: [String]
    /// 0 none / 1 low / 2 medium / 3 high
    public var priority: Int
    public var startDate: LocalDate?
    public var due: LocalDateTime?
    public var dueHasTime: Bool
    public var estimateMinutes: Int?
    public var done: Bool
    public var doneAt: Int64?
    public var dropped: Bool
    public var parentId: String?
    public var `repeat`: Recurrence?
    public var remindMinutes: Int?
    public var remindMinutesList: [Int]
    public var remindPersistent: Bool
    public var courseId: String?
    public var dueBeforeNextClass: Bool
    public var blockStart: LocalDateTime?
    public var blockMinutes: Int?
    public var sortOrder: Int
    public var source: Source
    public var extra: [String: String]
    public var attachments: [Attachment]
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var userId: String?

    public static let extraCourseWeekKey = "courseWeek"

    public var effectiveRemindMinutes: [Int] {
        Reminders.normalize(primary: remindMinutes, list: remindMinutesList)
    }

    /// Original teaching week pinned to a single class session (`CourseOccurrence.originalWeek`).
    /// `nil` means the task is not pinned to a specific session.
    public var courseWeek: Int? {
        guard let raw = extra[Self.extraCourseWeekKey], let val = Int(raw), val > 0 else {
            return nil
        }
        return val
    }

    public var isExam: Bool {
        extra["kind"] == EventKind.EXAM.rawValue
            || id.hasPrefix("jxfw-exam/")
            || id.hasPrefix("jxfw-course-exam/")
    }

    /// Returns a copy with updated course linkage and optional single-session week pin (`extra["courseWeek"]`).
    public func withCourseLink(
        courseId: String?,
        courseWeek: Int? = nil,
        dueBeforeNextClass: Bool = false
    ) -> GokeTask {
        var copy = self
        var nextExtra = copy.extra
        if courseId != nil, let courseWeek, courseWeek > 0 {
            nextExtra[Self.extraCourseWeekKey] = String(courseWeek)
        } else {
            nextExtra.removeValue(forKey: Self.extraCourseWeekKey)
        }
        copy.courseId = courseId
        copy.dueBeforeNextClass = courseId != nil && dueBeforeNextClass
        copy.extra = nextExtra
        return copy
    }

    public init(
        id: String = UUID().uuidString.lowercased(),
        title: String,
        note: String = "",
        listId: String? = nil,
        tags: [String] = [],
        priority: Int = 0,
        startDate: LocalDate? = nil,
        due: LocalDateTime? = nil,
        dueHasTime: Bool = false,
        estimateMinutes: Int? = nil,
        done: Bool = false,
        doneAt: Int64? = nil,
        dropped: Bool = false,
        parentId: String? = nil,
        repeat recurrence: Recurrence? = nil,
        remindMinutes: Int? = nil,
        remindPersistent: Bool = false,
        courseId: String? = nil,
        dueBeforeNextClass: Bool = false,
        blockStart: LocalDateTime? = nil,
        blockMinutes: Int? = nil,
        sortOrder: Int = 0,
        source: Source = .MANUAL,
        extra: [String: String] = [:],
        attachments: [Attachment] = [],
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil,
        userId: String? = nil,
        remindMinutesList: [Int] = []
    ) {
        self.id = id
        self.title = title
        self.note = note
        self.listId = listId
        self.tags = tags
        self.priority = priority
        self.startDate = startDate
        self.due = due
        self.dueHasTime = dueHasTime
        self.estimateMinutes = estimateMinutes
        self.done = done
        self.doneAt = doneAt
        self.dropped = dropped
        self.parentId = parentId
        self.`repeat` = recurrence
        self.remindMinutes = remindMinutes
        self.remindMinutesList = Reminders.normalize(remindMinutesList)
        self.remindPersistent = remindPersistent
        self.courseId = courseId
        self.dueBeforeNextClass = dueBeforeNextClass
        self.blockStart = blockStart
        self.blockMinutes = blockMinutes
        self.sortOrder = sortOrder
        self.source = source
        self.extra = extra
        self.attachments = attachments
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.userId = userId
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> GokeTask {
        var copy = self
        copy.updatedAt = updatedAt
        copy.deletedAt = deletedAt
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, note, listId, tags, priority, startDate, due, dueHasTime, estimateMinutes
        case done, doneAt, dropped, parentId, `repeat`, remindMinutes, remindMinutesList, remindPersistent
        case courseId, dueBeforeNextClass, blockStart, blockMinutes, sortOrder, source
        case extra, attachments, updatedAt, deletedAt, userId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decode(String.self, forKey: .title)
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.listId = try c.decodeIfPresent(String.self, forKey: .listId)
        self.tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        self.priority = try c.decodeIfPresent(Int.self, forKey: .priority) ?? 0
        self.startDate = try c.decodeIfPresent(LocalDate.self, forKey: .startDate)
        self.due = try c.decodeIfPresent(LocalDateTime.self, forKey: .due)
        self.dueHasTime = try c.decodeIfPresent(Bool.self, forKey: .dueHasTime) ?? false
        self.estimateMinutes = try c.decodeIfPresent(Int.self, forKey: .estimateMinutes)
        self.done = try c.decodeIfPresent(Bool.self, forKey: .done) ?? false
        self.doneAt = try c.decodeIfPresent(Int64.self, forKey: .doneAt)
        self.dropped = try c.decodeIfPresent(Bool.self, forKey: .dropped) ?? false
        self.parentId = try c.decodeIfPresent(String.self, forKey: .parentId)
        self.`repeat` = try c.decodeIfPresent(Recurrence.self, forKey: .`repeat`)
        let decodedRemindMinutes = try c.decodeIfPresent(Int.self, forKey: .remindMinutes)
        self.remindMinutes = decodedRemindMinutes
        if let decodedList = try c.decodeIfPresent([Int].self, forKey: .remindMinutesList) {
            self.remindMinutesList = Reminders.normalize(decodedList)
        } else {
            self.remindMinutesList = Reminders.normalize(primary: decodedRemindMinutes, list: [])
        }
        self.remindPersistent = try c.decodeIfPresent(Bool.self, forKey: .remindPersistent) ?? false
        self.courseId = try c.decodeIfPresent(String.self, forKey: .courseId)
        self.dueBeforeNextClass = try c.decodeIfPresent(Bool.self, forKey: .dueBeforeNextClass) ?? false
        self.blockStart = try c.decodeIfPresent(LocalDateTime.self, forKey: .blockStart)
        self.blockMinutes = try c.decodeIfPresent(Int.self, forKey: .blockMinutes)
        self.sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        self.source = try c.decodeIfPresent(Source.self, forKey: .source) ?? .MANUAL
        self.extra = try c.decodeIfPresent([String: String].self, forKey: .extra) ?? [:]
        self.attachments = try c.decodeIfPresent([Attachment].self, forKey: .attachments) ?? []
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        self.userId = try c.decodeIfPresent(String.self, forKey: .userId)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(note, forKey: .note)
        try c.encode(listId, forKey: .listId)
        try c.encode(tags, forKey: .tags)
        try c.encode(priority, forKey: .priority)
        try c.encode(startDate, forKey: .startDate)
        try c.encode(due, forKey: .due)
        try c.encode(dueHasTime, forKey: .dueHasTime)
        try c.encode(estimateMinutes, forKey: .estimateMinutes)
        try c.encode(done, forKey: .done)
        try c.encode(doneAt, forKey: .doneAt)
        try c.encode(dropped, forKey: .dropped)
        try c.encode(parentId, forKey: .parentId)
        try c.encode(`repeat`, forKey: .`repeat`)
        try c.encode(remindMinutes, forKey: .remindMinutes)
        try c.encode(remindMinutesList, forKey: .remindMinutesList)
        try c.encode(remindPersistent, forKey: .remindPersistent)
        try c.encode(courseId, forKey: .courseId)
        try c.encode(dueBeforeNextClass, forKey: .dueBeforeNextClass)
        try c.encode(blockStart, forKey: .blockStart)
        try c.encode(blockMinutes, forKey: .blockMinutes)
        try c.encode(sortOrder, forKey: .sortOrder)
        try c.encode(source, forKey: .source)
        try c.encode(extra, forKey: .extra)
        try c.encode(attachments, forKey: .attachments)
        try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(deletedAt, forKey: .deletedAt)
        try c.encode(userId, forKey: .userId)
    }
}

/// Polymorphic domain entity matching Android's `sealed interface Record` with `_t` JSON discriminator.
public enum Record: Sendable, Hashable, Codable, Identifiable {
    case semester(Semester)
    case course(Course)
    case courseException(CourseException)
    case event(Event)
    case taskList(TaskList)
    case task(GokeTask)

    public var id: String {
        switch self {
        case .semester(let v): return v.id
        case .course(let v): return v.id
        case .courseException(let v): return v.id
        case .event(let v): return v.id
        case .taskList(let v): return v.id
        case .task(let v): return v.id
        }
    }

    public var updatedAt: Int64 {
        switch self {
        case .semester(let v): return v.updatedAt
        case .course(let v): return v.updatedAt
        case .courseException(let v): return v.updatedAt
        case .event(let v): return v.updatedAt
        case .taskList(let v): return v.updatedAt
        case .task(let v): return v.updatedAt
        }
    }

    public var deletedAt: Int64? {
        switch self {
        case .semester(let v): return v.deletedAt
        case .course(let v): return v.deletedAt
        case .courseException(let v): return v.deletedAt
        case .event(let v): return v.deletedAt
        case .taskList(let v): return v.deletedAt
        case .task(let v): return v.deletedAt
        }
    }

    public var userId: String? {
        switch self {
        case .semester(let v): return v.userId
        case .course(let v): return v.userId
        case .courseException(let v): return v.userId
        case .event(let v): return v.userId
        case .taskList(let v): return v.userId
        case .task(let v): return v.userId
        }
    }

    /// Wire JSON `_t` discriminator value.
    public var discriminator: String {
        switch self {
        case .semester: return "semester"
        case .course: return "course"
        case .courseException: return "courseException"
        case .event: return "event"
        case .taskList: return "taskList"
        case .task: return "task"
        }
    }

    /// Sync server / SQLite `kind` column name (matches Kotlin `r::class.simpleName`).
    public var syncKind: String {
        switch self {
        case .semester: return "Semester"
        case .course: return "Course"
        case .courseException: return "CourseException"
        case .event: return "Event"
        case .taskList: return "TaskList"
        case .task: return "Task"
        }
    }

    public var entityType: EntityType {
        switch self {
        case .semester: return .SEMESTER
        case .course: return .COURSE
        case .courseException: return .EXCEPTION
        case .event: return .EVENT
        case .taskList: return .LIST
        case .task: return .TASK
        }
    }

    public var displayTitle: String {
        switch self {
        case .semester(let v): return v.name
        case .course(let v): return v.name
        case .courseException(let v): return "#\(v.week)"
        case .event(let v): return v.title
        case .taskList(let v): return v.name
        case .task(let v): return v.title
        }
    }

    public func withMeta(updatedAt: Int64, deletedAt: Int64?) -> Record {
        switch self {
        case .semester(let v): return .semester(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        case .course(let v): return .course(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        case .courseException(let v): return .courseException(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        case .event(let v): return .event(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        case .taskList(let v): return .taskList(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        case .task(let v): return .task(v.withMeta(updatedAt: updatedAt, deletedAt: deletedAt))
        }
    }

    /// Content equality ignoring `updatedAt` and `deletedAt` metadata.
    public func hasSameContent(as other: Record) -> Bool {
        withMeta(updatedAt: 0, deletedAt: nil) == other.withMeta(updatedAt: 0, deletedAt: nil)
    }

    private enum DiscriminatorKey: String, CodingKey {
        case type = "_t"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DiscriminatorKey.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "semester":
            self = .semester(try Semester(from: decoder))
        case "course":
            self = .course(try Course(from: decoder))
        case "courseException":
            self = .courseException(try CourseException(from: decoder))
        case "event":
            self = .event(try Event(from: decoder))
        case "taskList":
            self = .taskList(try TaskList(from: decoder))
        case "task":
            self = .task(try GokeTask(from: decoder))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unknown Record _t discriminator: \(type)"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: DiscriminatorKey.self)
        try container.encode(discriminator, forKey: .type)
        switch self {
        case .semester(let v): try v.encode(to: encoder)
        case .course(let v): try v.encode(to: encoder)
        case .courseException(let v): try v.encode(to: encoder)
        case .event(let v): try v.encode(to: encoder)
        case .taskList(let v): try v.encode(to: encoder)
        case .task(let v): try v.encode(to: encoder)
        }
    }

    /// Decodes a concrete `Record` directly from `data` using an out-of-band `kind` (`syncKind` or `_t` discriminator),
    /// avoiding the double keyed-container parse of `Record.init(from:)`. Falls back to `Record.self` for unknown kinds.
    public static func decodeDirect(
        kind: String,
        from data: Data,
        using decoder: JSONDecoder = MintJSON.makeDecoder()
    ) throws -> Record {
        switch kind {
        case "Semester", "semester":
            return .semester(try decoder.decode(Semester.self, from: data))
        case "Course", "course":
            return .course(try decoder.decode(Course.self, from: data))
        case "CourseException", "courseException":
            return .courseException(try decoder.decode(CourseException.self, from: data))
        case "Event", "event":
            return .event(try decoder.decode(Event.self, from: data))
        case "TaskList", "taskList":
            return .taskList(try decoder.decode(TaskList.self, from: data))
        case "Task", "task":
            return .task(try decoder.decode(GokeTask.self, from: data))
        default:
            return try decoder.decode(Record.self, from: data)
        }
    }
}
