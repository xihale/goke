import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

/// Lifecycle phase of a Live Activity tracking the current or upcoming class session.
public enum ClassActivityPhase: String, Codable, Hashable, Sendable {
    case upcoming
    case inProgress
    case ended
}

/// Shared Live Activity state payload rendered in Dynamic Island & Lock Screen.
/// Kept in `GokeApp/Sources/Shared` so both `GokeApp` and `GokeWidgetExtension` compile against it.
public struct GokeClassActivityState: Codable, Hashable, Sendable {
    public var phase: ClassActivityPhase
    public var startDate: Date
    public var endDate: Date
    public var room: String
    public var nextCourseName: String?
    public var nextCourseRoom: String?
    public var pendingTaskCount: Int

    public init(
        phase: ClassActivityPhase,
        startDate: Date,
        endDate: Date,
        room: String,
        nextCourseName: String? = nil,
        nextCourseRoom: String? = nil,
        pendingTaskCount: Int = 0
    ) {
        self.phase = phase
        self.startDate = startDate
        self.endDate = endDate
        self.room = room
        self.nextCourseName = nextCourseName
        self.nextCourseRoom = nextCourseRoom
        self.pendingTaskCount = pendingTaskCount
    }
}

#if canImport(ActivityKit)
/// ActivityKit attributes for Goke's current/upcoming class Live Activity.
@available(iOS 16.2, *)
public struct GokeClassActivityAttributes: ActivityAttributes, Sendable, Hashable {
    public typealias ContentState = GokeClassActivityState

    public let courseId: String
    public let courseName: String
    public let teacher: String
    public let colorIndex: Int
    public let periodRangeText: String
    public let weekNumber: Int
    public var isExam: Bool

    public init(
        courseId: String,
        courseName: String,
        teacher: String,
        colorIndex: Int,
        periodRangeText: String,
        weekNumber: Int,
        isExam: Bool = false
    ) {
        self.courseId = courseId
        self.courseName = courseName
        self.teacher = teacher
        self.colorIndex = colorIndex
        self.periodRangeText = periodRangeText
        self.weekNumber = weekNumber
        self.isExam = isExam
    }
}
#else
/// Non-ActivityKit fallback definition so shared models parse cleanly on platforms without ActivityKit.
public struct GokeClassActivityAttributes: Codable, Hashable, Sendable {
    public typealias ContentState = GokeClassActivityState

    public let courseId: String
    public let courseName: String
    public let teacher: String
    public let colorIndex: Int
    public let periodRangeText: String
    public let weekNumber: Int
    public var isExam: Bool

    public init(
        courseId: String,
        courseName: String,
        teacher: String,
        colorIndex: Int,
        periodRangeText: String,
        weekNumber: Int,
        isExam: Bool = false
    ) {
        self.courseId = courseId
        self.courseName = courseName
        self.teacher = teacher
        self.colorIndex = colorIndex
        self.periodRangeText = periodRangeText
        self.weekNumber = weekNumber
        self.isExam = isExam
    }
}
#endif
