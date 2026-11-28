import Foundation
import GokeCore
import GokeShared
#if canImport(ActivityKit)
import ActivityKit
#endif

public enum LiveActivityError: Error, Sendable, Equatable, LocalizedError {
    case unsupportedPlatform
    case activitiesDisabled
    case noUpcomingCourse

    public var errorDescription: String? {
        switch self {
        case .unsupportedPlatform:
            "当前设备不支持实时活动。"
        case .activitiesDisabled:
            "系统设置中未开启 Goke 的实时活动权限。"
        case .noUpcomingCourse:
            "近期没有即将开始或进行中的课程。"
        }
    }
}

/// Manages Goke's current/upcoming class Live Activity (`GokeClassActivityAttributes`).
public actor LiveActivityManager {
    public static let shared = LiveActivityManager()

    /// Starts a Live Activity when a class is within this lead window before start (default: 45 minutes).
    public let leadWindowMinutes: Int

    public init(leadWindowMinutes: Int = 45) {
        self.leadWindowMinutes = leadWindowMinutes
    }

    /// Returns whether Live Activities are enabled for this app in iOS Settings.
    public func areActivitiesEnabled() -> Bool {
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *) {
            return ActivityAuthorizationInfo().areActivitiesEnabled
        }
        #endif
        return false
    }

    /// Returns the `courseId` currently displayed in an active Live Activity, if any.
    public func activeCourseId() -> String? {
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *) {
            return Activity<GokeClassActivityAttributes>.activities.first?.attributes.courseId
        }
        #endif
        return nil
    }

    /// Reconciles the active Live Activity against the latest `AppSnapshot`.
    /// Starts or updates an activity when a course is in progress or starts within `leadWindowMinutes`;
    /// ends stale activities when no course is imminent.
    public func sync(
        snapshot: AppSnapshot,
        now: LocalDateTime = .now(),
        timeZone: TimeZone = .current
    ) async {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *), ActivityAuthorizationInfo().areActivitiesEnabled else {
            return
        }
        guard let nextUp = NextUps.find(snapshot: snapshot, now: now, horizonDays: 1) else {
            await endAll()
            return
        }

        let occ = nextUp.occurrence
        let secondsUntilStart = now.secondsUntil(occ.start)
        let isInProgress = occ.start <= now && occ.end > now
        let isWithinLeadWindow = secondsUntilStart > 0 && secondsUntilStart <= Int64(leadWindowMinutes * 60)

        guard isInProgress || isWithinLeadWindow else {
            await endAll()
            return
        }

        let following = NextUps.find(snapshot: snapshot, now: occ.end, horizonDays: 1)?.occurrence
        _ = try? await startOrUpdate(
            occurrence: occ,
            pendingTaskCount: nextUp.tasks.count,
            followingOccurrence: following,
            now: now,
            timeZone: timeZone
        )
        #endif
    }

    /// Explicitly starts or updates a Live Activity for a specific `CourseOccurrence`.
    @discardableResult
    public func startOrUpdate(
        occurrence: CourseOccurrence,
        pendingTaskCount: Int = 0,
        followingOccurrence: CourseOccurrence? = nil,
        now: LocalDateTime = .now(),
        timeZone: TimeZone = .current
    ) async throws -> String {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *) else {
            throw LiveActivityError.unsupportedPlatform
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            throw LiveActivityError.activitiesDisabled
        }

        let startDate = occurrence.start.toDate(in: timeZone)
        let endDate = occurrence.end.toDate(in: timeZone)
        let phase: ClassActivityPhase
        if now >= occurrence.end {
            phase = .ended
        } else if now >= occurrence.start {
            phase = .inProgress
        } else {
            phase = .upcoming
        }

        let periodRangeText = occurrence.startPeriod == occurrence.endPeriod
            ? "第\(occurrence.startPeriod)节"
            : "第\(occurrence.startPeriod)-\(occurrence.endPeriod)节"

        let attributes = GokeClassActivityAttributes(
            courseId: occurrence.course.id,
            courseName: occurrence.course.name,
            teacher: occurrence.course.teacher,
            colorIndex: occurrence.course.colorIndex,
            periodRangeText: periodRangeText,
            weekNumber: occurrence.week,
            isExam: occurrence.course.isExam
        )
        let state = GokeClassActivityState(
            phase: phase,
            startDate: startDate,
            endDate: endDate,
            room: Self.formattedVenue(for: occurrence),
            nextCourseName: followingOccurrence?.course.name,
            nextCourseRoom: followingOccurrence.map(Self.formattedVenue(for:)),
            pendingTaskCount: pendingTaskCount
        )
        let staleDate: Date? = switch phase {
        case .upcoming:
            startDate
        case .inProgress:
            endDate
        case .ended:
            nil
        }
        let content = ActivityContent(state: state, staleDate: staleDate)

        let existing = Activity<GokeClassActivityAttributes>.activities
        if let match = existing.first(where: {
            $0.attributes == attributes
                && $0.content.state.startDate == startDate
                && $0.content.state.endDate == endDate
        }) {
            for other in existing where other.id != match.id {
                await other.end(nil, dismissalPolicy: .immediate)
            }
            await match.update(content)
            return match.id
        }

        for other in existing {
            await other.end(nil, dismissalPolicy: .immediate)
        }
        let created = try Activity<GokeClassActivityAttributes>.request(
            attributes: attributes,
            content: content,
            pushType: nil
        )
        return created.id
        #else
        throw LiveActivityError.unsupportedPlatform
        #endif
    }

    private static func formattedVenue(for occurrence: CourseOccurrence) -> String {
        let baseRoom = occurrence.room.trimmingCharacters(in: .whitespacesAndNewlines)
        let isExam = occurrence.course.isExam || ExamTextHelper.isExamRemark(occurrence.course.name)
        guard isExam else { return baseRoom }
        if ExamTextHelper.extractExamSeat(from: baseRoom) == nil,
           let seat = ExamTextHelper.extractExamSeat(from: occurrence.course.note),
           !seat.isEmpty
        {
            return baseRoom.isEmpty ? "座位 \(seat)" : "\(baseRoom) · 座位 \(seat)"
        }
        return baseRoom
    }

    /// Ends all active Goke class Live Activities immediately.
    public func endAll() async {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *) else { return }
        for activity in Activity<GokeClassActivityAttributes>.activities {
            var endedState = activity.content.state
            endedState.phase = .ended
            let finalContent = ActivityContent(state: endedState, staleDate: nil)
            await activity.end(finalContent, dismissalPolicy: .immediate)
        }
        #endif
    }
}
