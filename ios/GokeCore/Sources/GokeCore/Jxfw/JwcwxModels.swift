import Foundation

// MARK: - Empty Classroom Domain Models (matches `core/jxfw/JwcwxClassroom.kt`)

public struct ClassroomBuilding: Sendable, Hashable, Codable {
    public var code: String
    public var number: String
    public var name: String
    public var campusCode: String
    public var campusName: String

    public init(
        code: String,
        number: String = "",
        name: String,
        campusCode: String = "",
        campusName: String = ""
    ) {
        self.code = code
        self.number = number
        self.name = name
        self.campusCode = campusCode
        self.campusName = campusName
    }
}

public struct ClassroomRoom: Sendable, Hashable, Codable {
    public var code: String
    public var name: String
    public var capacity: Int?

    public init(code: String, name: String, capacity: Int? = nil) {
        self.code = code
        self.name = name
        self.capacity = capacity
    }
}

public enum OccupancyType: String, Sendable, Hashable, Codable {
    case COURSE
    case BORROWED
    case LOCKED
    case EXAM
    case OTHER
}

public struct ClassroomOccupancy: Sendable, Hashable, Codable {
    public var id: String
    public var roomCode: String
    public var roomName: String
    public var periods: [Int]
    public var periodCode: String
    public var courseName: String
    public var teacher: String
    public var className: String
    public var note: String
    public var dayOfWeek: Int?
    public var week: Int?
    public var type: OccupancyType
    public var count: Int?

    public init(
        id: String,
        roomCode: String,
        roomName: String = "",
        periods: [Int],
        periodCode: String = "",
        courseName: String = "",
        teacher: String = "",
        className: String = "",
        note: String = "",
        dayOfWeek: Int? = nil,
        week: Int? = nil,
        type: OccupancyType = .OTHER,
        count: Int? = nil
    ) {
        self.id = id
        self.roomCode = roomCode
        self.roomName = roomName
        self.periods = periods
        self.periodCode = periodCode
        self.courseName = courseName
        self.teacher = teacher
        self.className = className
        self.note = note
        self.dayOfWeek = dayOfWeek
        self.week = week
        self.type = type
        self.count = count
    }
}
