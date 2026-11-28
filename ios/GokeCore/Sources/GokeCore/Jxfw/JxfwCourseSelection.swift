import Foundation
import GokeCoreModel

// MARK: - Academic Course Selection Domain Models

/// Open elective plan time window.
public struct XkOpenPlan: Sendable, Hashable, Codable {
    public var jxjhdm: String
    public var jhfxdm: String
    public var planType: String
    public var planName: String
    public var startTime: String
    public var endTime: String

    public init(
        jxjhdm: String,
        jhfxdm: String = "",
        planType: String = "",
        planName: String,
        startTime: String = "",
        endTime: String = ""
    ) {
        self.jxjhdm = jxjhdm
        self.jhfxdm = jhfxdm
        self.planType = planType
        self.planName = planName
        self.startTime = startTime
        self.endTime = endTime
    }
}

/// Course-selection portal window status.
public struct XkWindowStatus: Sendable, Hashable, Codable {
    public var xkjd: String
    public var xnxqdm: String
    public var isCreditWallet: Bool
    public var canDrop: Bool
    public var maxCourses: Int?
    public var headerNotice: String
    public var openPlans: [XkOpenPlan]

    public var isOpen: Bool {
        let trimmedStage = xkjd.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedTerm = xnxqdm.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmedStage.isEmpty && trimmedStage != "0" && !trimmedTerm.isEmpty && trimmedTerm != "-1"
    }

    public init(
        xkjd: String,
        xnxqdm: String,
        isCreditWallet: Bool = false,
        canDrop: Bool = false,
        maxCourses: Int? = nil,
        headerNotice: String = "",
        openPlans: [XkOpenPlan] = []
    ) {
        self.xkjd = xkjd
        self.xnxqdm = xnxqdm
        self.isCreditWallet = isCreditWallet
        self.canDrop = canDrop
        self.maxCourses = maxCourses
        self.headerNotice = headerNotice
        self.openPlans = openPlans
    }
}

/// Credit wallet quota.
public struct XkCreditWallet: Sendable, Hashable, Codable {
    public var totalCredits: Double
    public var usedCredits: Double

    public var availableCredits: Double {
        max(0.0, totalCredits - usedCredits)
    }

    public init(totalCredits: Double, usedCredits: Double) {
        self.totalCredits = totalCredits
        self.usedCredits = usedCredits
    }
}

/// Single scheduled class session.
public struct XkSessionSlot: Sendable, Hashable, Codable {
    public var kcrwdm: String
    public var week: Int
    public var dayOfWeek: Int
    public var periods: [Int]
    public var room: String
    public var roomType: String
    public var roomCode: String
    public var teacher: String
    public var teachingType: String
    public var classGroup: String
    public var content: String
    public var departmentCode: String
    public var hours: String

    public init(
        kcrwdm: String,
        week: Int,
        dayOfWeek: Int,
        periods: [Int],
        room: String = "",
        roomType: String = "",
        roomCode: String = "",
        teacher: String = "",
        teachingType: String = "",
        classGroup: String = "",
        content: String = "",
        departmentCode: String = "",
        hours: String = ""
    ) {
        self.kcrwdm = kcrwdm
        self.week = week
        self.dayOfWeek = dayOfWeek
        self.periods = periods
        self.room = room
        self.roomType = roomType
        self.roomCode = roomCode
        self.teacher = teacher
        self.teachingType = teachingType
        self.classGroup = classGroup
        self.content = content
        self.departmentCode = departmentCode
        self.hours = hours
    }
}

/// Aggregated schedule summary grouped by `(dayOfWeek, periods, room)`.
public struct XkScheduleSummary: Sendable, Hashable, Codable {
    public var dayOfWeek: Int
    public var periods: [Int]
    public var weeks: Set<Int>
    public var room: String
    public var campusHint: String

    public init(
        dayOfWeek: Int,
        periods: [Int],
        weeks: Set<Int>,
        room: String = "",
        campusHint: String = ""
    ) {
        self.dayOfWeek = dayOfWeek
        self.periods = periods
        self.weeks = weeks
        self.room = room
        self.campusHint = campusHint
    }
}

/// Origin of an occupied timetable slot used during course-selection conflict checks.
public enum XkConflictSource: String, Sendable, Hashable, Codable {
    case JXFW
    case LOCAL_TIMETABLE
}

/// Single occupied period used for conflict detection.
public struct XkOccupiedSlot: Sendable, Hashable, Codable {
    public var week: Int
    public var dayOfWeek: Int
    public var period: Int
    public var courseName: String
    public var source: XkConflictSource

    public init(
        week: Int,
        dayOfWeek: Int,
        period: Int,
        courseName: String,
        source: XkConflictSource
    ) {
        self.week = week
        self.dayOfWeek = dayOfWeek
        self.period = period
        self.courseName = courseName
        self.source = source
    }
}

/// Aggregated schedule conflict for a selectable course.
public struct XkConflict: Sendable, Hashable, Codable {
    public var courseName: String
    public var dayOfWeek: Int
    public var periods: [Int]
    public var weeks: Set<Int>
    public var source: XkConflictSource

    public init(
        courseName: String,
        dayOfWeek: Int,
        periods: [Int],
        weeks: Set<Int>,
        source: XkConflictSource
    ) {
        self.courseName = courseName
        self.dayOfWeek = dayOfWeek
        self.periods = periods
        self.weeks = weeks
        self.source = source
    }
}

/// Selectable or enrolled course item aggregated with schedule summaries and conflict info.
public struct XkCourseItem: Sendable, Hashable, Codable, Identifiable {
    public var id: String { kcrwdm }

    public var kcrwdm: String
    public var kcdm: String
    public var jxbdm: String
    public var kcptdm: String
    public var name: String
    public var projectSuffix: String
    public var credit: Double?
    public var teacher: String
    public var capacity: Int?
    public var enrolledCount: Int?
    public var category: String
    public var subCategory: String
    public var enrolled: Bool
    public var sessions: [XkSessionSlot]
    public var scheduleSummary: [XkScheduleSummary]
    public var classGroup: String
    public var campusHint: String
    public var conflicts: [XkConflict]

    public var displayName: String {
        let suffix = projectSuffix.trimmingCharacters(in: .whitespacesAndNewlines)
        return suffix.isEmpty ? name : "\(name) [\(projectSuffix)]"
    }

    public var remainingSeats: Int? {
        guard let capacity, let enrolledCount else { return nil }
        return max(0, capacity - enrolledCount)
    }

    public var isFull: Bool {
        remainingSeats.map { $0 <= 0 } ?? false
    }

    public var hasConflict: Bool {
        !conflicts.isEmpty
    }

    public init(
        kcrwdm: String,
        kcdm: String = "",
        jxbdm: String = "",
        kcptdm: String = "",
        name: String,
        projectSuffix: String = "",
        credit: Double? = nil,
        teacher: String = "",
        capacity: Int? = nil,
        enrolledCount: Int? = nil,
        category: String = "",
        subCategory: String = "",
        enrolled: Bool = false,
        sessions: [XkSessionSlot] = [],
        scheduleSummary: [XkScheduleSummary] = [],
        classGroup: String = "",
        campusHint: String = "",
        conflicts: [XkConflict] = []
    ) {
        self.kcrwdm = kcrwdm
        self.kcdm = kcdm
        self.jxbdm = jxbdm
        self.kcptdm = kcptdm
        self.name = name
        self.projectSuffix = projectSuffix
        self.credit = credit
        self.teacher = teacher
        self.capacity = capacity
        self.enrolledCount = enrolledCount
        self.category = category
        self.subCategory = subCategory
        self.enrolled = enrolled
        self.sessions = sessions
        self.scheduleSummary = scheduleSummary
        self.classGroup = classGroup
        self.campusHint = campusHint
        self.conflicts = conflicts
    }
}

/// Supplementary task/curriculum metadata.
public struct XkExtraMeta: Sendable, Hashable, Codable {
    public var courseCode: String
    public var englishName: String
    public var studyMode: String
    public var plannedHours: String
    public var gradingMode: String
    public var platformName: String
    public var classGroup: String

    public init(
        courseCode: String = "",
        englishName: String = "",
        studyMode: String = "",
        plannedHours: String = "",
        gradingMode: String = "",
        platformName: String = "",
        classGroup: String = ""
    ) {
        self.courseCode = courseCode
        self.englishName = englishName
        self.studyMode = studyMode
        self.plannedHours = plannedHours
        self.gradingMode = gradingMode
        self.platformName = platformName
        self.classGroup = classGroup
    }
}

/// Lazy-loaded course detail (bilingual syllabus, teaching calendar, and extra metadata).
public struct XkCourseDetail: Sendable, Hashable, Codable {
    public var kcrwdm: String
    public var kcptdm: String
    public var zhSyllabus: String
    public var enSyllabus: String
    public var calendar: [XkSessionSlot]
    public var extra: XkExtraMeta?

    public init(
        kcrwdm: String,
        kcptdm: String = "",
        zhSyllabus: String = "",
        enSyllabus: String = "",
        calendar: [XkSessionSlot] = [],
        extra: XkExtraMeta? = nil
    ) {
        self.kcrwdm = kcrwdm
        self.kcptdm = kcptdm
        self.zhSyllabus = zhSyllabus
        self.enSyllabus = enSyllabus
        self.calendar = calendar
        self.extra = extra
    }
}

/// Categorized failure kinds for enroll/drop actions.
public enum XkFailureKind: String, Sendable, Hashable, Codable {
    case FULL
    case CONFLICT
    case CREDIT_LIMIT
    case OUT_OF_WINDOW
    case ALREADY_ENROLLED
    case NOT_DROPPABLE
    case OTHER
}

/// Result of an enroll or drop request.
public enum XkActionResult: Sendable, Hashable {
    case success(raw: String)
    case failure(reason: String, kind: XkFailureKind)
}

// MARK: - Pure Course-Selection Schedule Summary & Conflict Detector

public enum JxfwXkParse {
    private struct ScheduleGroupKey: Hashable {
        let dayOfWeek: Int
        let periods: [Int]
        let room: String
    }

    private struct SlotLookupKey: Hashable {
        let week: Int
        let dayOfWeek: Int
        let period: Int
    }

    private struct ConflictGroupKey: Hashable {
        let courseName: String
        let dayOfWeek: Int
    }

    private struct ConflictAccum {
        var periods: Set<Int> = []
        var weeks: Set<Int> = []
        var source: XkConflictSource
    }

    /// Expands local semester `Course` records into occupied slots (ignoring soft-deleted records).
    public static func localCoursesToOccupiedSlots(_ courses: [Course]) -> [XkOccupiedSlot] {
        var out: [XkOccupiedSlot] = []
        for course in courses {
            guard course.deletedAt == nil,
                  (1...7).contains(course.dayOfWeek),
                  course.startPeriod <= course.endPeriod
            else {
                continue
            }
            for week in course.weeks {
                for period in course.startPeriod...course.endPeriod {
                    out.append(
                        XkOccupiedSlot(
                            week: week,
                            dayOfWeek: course.dayOfWeek,
                            period: period,
                            courseName: course.name,
                            source: .LOCAL_TIMETABLE
                        )
                    )
                }
            }
        }
        return out
    }

    /// Summarizes session slots by `(dayOfWeek, periods, room)` and infers campus hints.
    public static func summarizeSchedule(_ slots: [XkSessionSlot]) -> [XkScheduleSummary] {
        var grouped: [ScheduleGroupKey: [XkSessionSlot]] = [:]
        for slot in slots {
            let key = ScheduleGroupKey(
                dayOfWeek: slot.dayOfWeek,
                periods: slot.periods,
                room: slot.room.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            grouped[key, default: []].append(slot)
        }
        return grouped.map { key, group in
            let sampleCode = group.first { !$0.roomCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }?.roomCode ?? ""
            let weeks = Set(group.map(\.week))
            return XkScheduleSummary(
                dayOfWeek: key.dayOfWeek,
                periods: key.periods,
                weeks: weeks,
                room: key.room,
                campusHint: inferCampusHint(roomCode: sampleCode, roomName: key.room)
            )
        }.sorted { lhs, rhs in
            if lhs.dayOfWeek != rhs.dayOfWeek { return lhs.dayOfWeek < rhs.dayOfWeek }
            let lp = lhs.periods.first ?? 0
            let rp = rhs.periods.first ?? 0
            if lp != rp { return lp < rp }
            return lhs.room < rhs.room
        }
    }

    /// Formats a set of week numbers as compact range text, e.g. `{1,2,3,5,7,8}` -> `"1-3,5,7-8周"`.
    public static func formatWeeksRange(_ weeks: Set<Int>) -> String {
        let sorted = weeks.filter { $0 > 0 }.sorted()
        guard let first = sorted.first else { return "" }
        var parts: [String] = []
        var start = first
        var end = first
        for w in sorted.dropFirst() {
            if w == end + 1 {
                end = w
            } else {
                parts.append(start == end ? "\(start)" : "\(start)-\(end)")
                start = w
                end = w
            }
        }
        parts.append(start == end ? "\(start)" : "\(start)-\(end)")
        return parts.joined(separator: ",") + "周"
    }

    /// Merges course items with enrolled IDs, session slots, and dual-source conflict detection.
    /// Enrolled courses never report self-conflicts.
    public static func mergeCourseData(
        items: [XkCourseItem],
        enrolledIds: Set<String>,
        slotsByTask: [String: [XkSessionSlot]],
        jxfwOccupied: [XkOccupiedSlot],
        localOccupied: [XkOccupiedSlot]
    ) -> [XkCourseItem] {
        var occupiedIndex: [SlotLookupKey: [XkOccupiedSlot]] = [:]
        for occ in jxfwOccupied + localOccupied {
            let key = SlotLookupKey(week: occ.week, dayOfWeek: occ.dayOfWeek, period: occ.period)
            occupiedIndex[key, default: []].append(occ)
        }

        return items.map { item in
            let isEnrolled = item.enrolled || enrolledIds.contains(item.kcrwdm)
            let slots = slotsByTask[item.kcrwdm] ?? []
            let summary = summarizeSchedule(slots)

            let teacher: String
            if !item.teacher.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                teacher = item.teacher
            } else {
                var seen = Set<String>()
                var teachers: [String] = []
                for s in slots {
                    let t = s.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !t.isEmpty, seen.insert(t).inserted {
                        teachers.append(t)
                    }
                }
                teacher = teachers.joined(separator: ",")
            }

            let classGroup: String
            if !item.classGroup.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                classGroup = item.classGroup
            } else {
                classGroup = slots.first { !$0.classGroup.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }?.classGroup ?? ""
            }

            let campusHint: String
            if !item.campusHint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                campusHint = item.campusHint
            } else {
                campusHint = summary.first { !$0.campusHint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }?.campusHint ?? ""
            }

            let conflicts: [XkConflict] = (isEnrolled || slots.isEmpty)
                ? []
                : detectConflicts(courseName: item.name, slots: slots, occupiedIndex: occupiedIndex)

            var updated = item
            updated.enrolled = isEnrolled
            updated.teacher = teacher
            updated.sessions = slots
            updated.scheduleSummary = summary
            updated.classGroup = classGroup
            updated.campusHint = campusHint
            updated.conflicts = conflicts
            return updated
        }
    }

    private static func detectConflicts(
        courseName: String,
        slots: [XkSessionSlot],
        occupiedIndex: [SlotLookupKey: [XkOccupiedSlot]]
    ) -> [XkConflict] {
        var grouped: [ConflictGroupKey: ConflictAccum] = [:]
        for slot in slots {
            for period in slot.periods {
                let hits = occupiedIndex[SlotLookupKey(week: slot.week, dayOfWeek: slot.dayOfWeek, period: period)] ?? []
                for hit in hits where hit.courseName != courseName {
                    let key = ConflictGroupKey(courseName: hit.courseName, dayOfWeek: slot.dayOfWeek)
                    var accum = grouped[key] ?? ConflictAccum(source: hit.source)
                    if hit.source == .JXFW {
                        accum.source = .JXFW
                    }
                    accum.periods.insert(period)
                    accum.weeks.insert(slot.week)
                    grouped[key] = accum
                }
            }
        }
        return grouped.map { key, accum in
            XkConflict(
                courseName: key.courseName,
                dayOfWeek: key.dayOfWeek,
                periods: accum.periods.sorted(),
                weeks: accum.weeks,
                source: accum.source
            )
        }.sorted { lhs, rhs in
            if lhs.dayOfWeek != rhs.dayOfWeek { return lhs.dayOfWeek < rhs.dayOfWeek }
            let lp = lhs.periods.first ?? 0
            let rp = rhs.periods.first ?? 0
            if lp != rp { return lp < rp }
            return lhs.courseName < rhs.courseName
        }
    }

    private static func inferCampusHint(roomCode: String, roomName: String) -> String {
        let code = roomCode.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = roomName.trimmingCharacters(in: .whitespacesAndNewlines)
        if code.hasPrefix("01") || name.contains("(城)") { return "大学城" }
        if code.hasPrefix("02") || name.contains("(龙)") { return "龙洞" }
        if code.hasPrefix("03") || name.contains("(东)") { return "东风路" }
        if code.hasPrefix("04") || name.contains("(番)") { return "番禺" }
        if code.hasPrefix("108291461") || name.contains("(揭)") || name.contains("揭阳") { return "揭阳" }
        return ""
    }
}
