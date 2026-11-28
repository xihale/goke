import Foundation

/// Temporal phase of a course or timed block relative to the current instant.
public enum TimePhase: Sendable, Hashable {
    case upcoming
    case ongoing(progress: Double)
    case finished

    public var isOngoing: Bool {
        if case .ongoing = self { return true }
        return false
    }

    public var isFinished: Bool {
        self == .finished
    }

    public var progress: Double {
        switch self {
        case .upcoming: return 0.0
        case .ongoing(let value): return min(1.0, max(0.0, value))
        case .finished: return 1.0
        }
    }
}

/// Pure time-progress calculations shared by app timetable/agenda views and WidgetKit extensions.
public enum CourseProgress {
    /// Returns `[0.0, 1.0]` progress of `now` within `[start, end)`.
    public static func fraction(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime) -> Double {
        phase(start: start, end: end, now: now).progress
    }

    /// Classifies `now` as `.upcoming`, `.ongoing(progress:)`, or `.finished` for `[start, end)`.
    public static func phase(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime) -> TimePhase {
        if end <= start {
            return now >= end ? .finished : .upcoming
        }
        if now < start { return .upcoming }
        if now >= end { return .finished }
        let total = Double(start.secondsUntil(end))
        if total <= 0 { return .finished }
        let elapsed = Double(start.secondsUntil(now))
        return .ongoing(progress: min(1.0, max(0.0, elapsed / total)))
    }

    /// Integer percentage `0...100` for progress bars and accessibility labels.
    public static func percent(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime) -> Int {
        min(100, max(0, Int(fraction(start: start, end: end, now: now) * 100.0)))
    }
}

/// Geometry of a visible timetable block used for concentric current-time hairline clipping.
public struct TimetableNowBarBlock: Sendable, Hashable {
    public let left: Double
    public let top: Double
    public let right: Double
    public let bottom: Double
    public let cornerRadius: Double
    public let isStackedFront: Bool

    public init(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        cornerRadius: Double,
        isStackedFront: Bool = false
    ) {
        self.left = left
        self.top = top
        self.right = right
        self.bottom = bottom
        self.cornerRadius = cornerRadius
        self.isStackedFront = isStackedFront
    }
}

/// Horizontal `[startX, endX]` span of the current-time bar after concentric corner insetting.
public struct TimetableNowBarSpan: Sendable, Hashable {
    public let startX: Double
    public let endX: Double

    public init(startX: Double, endX: Double) {
        self.startX = startX
        self.endX = endX
    }
}

/// Content inside a single precomputed timetable grid block.
public enum TimetableSlotKind: Sendable, Hashable {
    case course(CourseOccurrence)
    case taskBlock(task: GokeTask, start: LocalDateTime, end: LocalDateTime)
    case event(EventOccurrence)
}

/// Precomputed, `Equatable` slot frame for 120Hz SwiftUI Canvas/grid timetable rendering.
public struct TimetableSlotFrame: Sendable, Hashable, Identifiable {
    public let id: String
    public let kind: TimetableSlotKind
    public let dayOfWeek: Int
    public let startPeriod: Int
    public let endPeriod: Int
    /// Zero-based horizontal lane index when overlapping blocks share a day column.
    public let laneIndex: Int
    /// Total overlapping lanes in this block's collision cluster (>= 1).
    public let laneCount: Int
    /// Normalized vertical start in `0.0...1.0` across total periods.
    public let normalizedY: Double
    /// Normalized vertical height in `0.0...1.0` across total periods.
    public let normalizedHeight: Double
    /// Optional botanical color index for `.taskBlock` (iris `2` for uncategorized exam, else list color or `1`).
    public let listColorIndex: Int?
    /// Identifier of the connected overlap cluster on this day column (shared by every transitively overlapping slot).
    public let clusterId: String
    /// Total number of slots in this connected overlap cluster (>= 1).
    public let clusterSize: Int
    /// Whether this slot is the front anchor card when rendering the cluster in stacked mode.
    public let isStackFront: Bool
    /// Stacked depth for narrow columns (`0` = front card, `1`..`2` = visible back peek layer, `nil` = tucked behind front card but reachable via cluster sheet).
    public let stackDepth: Int?
    /// Maximum visible back-peek depth in this slot's cluster (`0` if unstacked, else `1`..`2`).
    public let clusterMaxDepth: Int

    public var hasOverlap: Bool { clusterSize > 1 || laneCount > 1 }

    public init(
        id: String,
        kind: TimetableSlotKind,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        laneIndex: Int,
        laneCount: Int,
        normalizedY: Double,
        normalizedHeight: Double,
        listColorIndex: Int? = nil,
        clusterId: String = "",
        clusterSize: Int = 1,
        isStackFront: Bool = true,
        stackDepth: Int? = 0,
        clusterMaxDepth: Int = 0
    ) {
        self.id = id
        self.kind = kind
        self.dayOfWeek = dayOfWeek
        self.startPeriod = startPeriod
        self.endPeriod = endPeriod
        self.laneIndex = laneIndex
        self.laneCount = laneCount
        self.normalizedY = normalizedY
        self.normalizedHeight = normalizedHeight
        self.listColorIndex = listColorIndex
        self.clusterId = clusterId.isEmpty ? id : clusterId
        self.clusterSize = max(1, clusterSize)
        self.isStackFront = isStackFront
        self.stackDepth = stackDepth
        self.clusterMaxDepth = max(0, clusterMaxDepth)
    }
}

/// Clean-Grid rule: flexible windows > 3h (e.g. parcel pickup 14:00-22:00) fold into header pills.
public struct TimetableFlexiblePill: Sendable, Hashable, Identifiable {
    public let id: String
    public let task: GokeTask
    public let start: LocalDateTime
    public let end: LocalDateTime

    public init(id: String, task: GokeTask, start: LocalDateTime, end: LocalDateTime) {
        self.id = id
        self.task = task
        self.start = start
        self.end = end
    }
}

/// Precomputed layout for a single day column (1 = Monday ... 7 = Sunday).
public struct TimetableColumn: Sendable, Hashable, Identifiable {
    public var id: Int { dayOfWeek }
    public let dayOfWeek: Int
    public let date: LocalDate
    public let slots: [TimetableSlotFrame]
    public let flexiblePills: [TimetableFlexiblePill]
    /// Unfinished parcel tasks scheduled on this day (rendered as day-header badge pills).
    public let dayParcels: [GokeTask]

    public init(
        dayOfWeek: Int,
        date: LocalDate,
        slots: [TimetableSlotFrame],
        flexiblePills: [TimetableFlexiblePill],
        dayParcels: [GokeTask] = []
    ) {
        self.dayOfWeek = dayOfWeek
        self.date = date
        self.slots = slots
        self.flexiblePills = flexiblePills
        self.dayParcels = dayParcels
    }

    /// Returns every slot in the full connected overlap cluster containing `slot`, ordered chronologically.
    public func cluster(for slot: TimetableSlotFrame) -> [TimetableSlotFrame] {
        let members = slots.filter { $0.clusterId == slot.clusterId }
        guard !members.isEmpty else { return [slot] }
        return members.sorted { lhs, rhs in
            if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
            if lhs.isStackFront != rhs.isStackFront { return lhs.isStackFront }
            if lhs.laneIndex != rhs.laneIndex { return lhs.laneIndex < rhs.laneIndex }
            return lhs.id < rhs.id
        }
    }
}

/// Immutable, `Equatable`, `Sendable` precomputed timetable layout for one week.
public struct TimetableWeekLayout: Sendable, Hashable, Identifiable {
    public var id: Int { week }
    public let semesterId: String
    public let week: Int
    public let startDate: LocalDate
    public let endDate: LocalDate
    public let periods: [PeriodTime]
    public let columns: [TimetableColumn]

    public init(
        semesterId: String,
        week: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        periods: [PeriodTime],
        columns: [TimetableColumn]
    ) {
        self.semesterId = semesterId
        self.week = week
        self.startDate = startDate
        self.endDate = endDate
        self.periods = periods
        self.columns = columns
    }
}

/// Builds precomputed `TimetableWeekLayout` off the main actor so SwiftUI `body` performs zero layout math.
public enum TimetableLayout {
    /// Threshold above which a task time-block folds into a header pill (Clean-Grid rule: > 3 hours).
    public static let flexibleWindowMinutesThreshold = 180
    /// Morning/afternoon/evening rhythm gap matching Android `TimetableScreen.kt` (`GroupGap = 14.dp`).
    public static let defaultGroupGap: Double = 14.0

    /// Extracts the parcel pickup code from unified metadata keys (`code` -> `pickupCode` -> legacy `parcel_code`).
    public static func parcelCode(of task: GokeTask) -> String? {
        for key in ["code", "pickupCode", "parcel_code"] {
            if let value = task.extra[key]?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty {
                return value
            }
        }
        return nil
    }

    /// Extracts the parcel pickup station/place from unified metadata keys (`place` -> `pickupPlace` -> legacy `station`).
    public static func parcelPlace(of task: GokeTask) -> String? {
        for key in ["place", "pickupPlace", "station"] {
            if let value = task.extra[key]?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty {
                return value
            }
        }
        return nil
    }

    /// Extracts the parcel courier/company from unified metadata keys (`company` -> legacy `courier`).
    public static func parcelCompany(of task: GokeTask) -> String? {
        for key in ["company", "courier"] {
            if let value = task.extra[key]?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty {
                return value
            }
        }
        return nil
    }

    /// Returns whether `task` represents a parcel pickup item (excluded from grid blocks, shown in day header badge).
    /// Matches Android `TimetableScreen.kt` (`parcelCode() != null || source == Source.PARCEL`).
    public static func isParcelTask(_ task: GokeTask) -> Bool {
        if task.source == .PARCEL { return true }
        return parcelCode(of: task) != nil
    }

    /// Session rhythm boundaries matching Android `groupBoundaries(periodCount)`.
    public static func groupBoundaries(periodCount: Int) -> [Int] {
        if periodCount > 8 { return [4, 8] }
        if periodCount > 4 { return [4] }
        return []
    }

    /// Top Y offset for 1-based `periodIndex` including `groupGap` breaks after periods 4 and 8.
    public static func periodTop(
        periodIndex: Int,
        periodCount: Int,
        rowHeight: Double,
        groupGap: Double = defaultGroupGap
    ) -> Double {
        var gaps = 0
        if periodCount > 4 && periodIndex > 4 { gaps += 1 }
        if periodCount > 8 && periodIndex > 8 { gaps += 1 }
        return rowHeight * Double(max(0, periodIndex - 1)) + groupGap * Double(gaps)
    }

    /// Total grid height including morning/afternoon/evening group gaps.
    public static func totalGridHeight(
        periodCount: Int,
        rowHeight: Double,
        groupGap: Double = defaultGroupGap
    ) -> Double {
        let count = max(1, periodCount)
        let gaps = groupBoundaries(periodCount: count).count
        return Double(count) * rowHeight + Double(gaps) * groupGap
    }

    /// Maps wall-clock `time` to vertical Y coordinate in points, linearly interpolating across periods,
    /// inter-period breaks, and morning/afternoon/evening `groupGap`s (1:1 port of Android `calcTimetableNowY`).
    public static func calcNowY(
        periods: [PeriodTime],
        time: LocalTime,
        rowHeight: Double,
        groupGap: Double = defaultGroupGap
    ) -> Double? {
        let n = periods.count
        guard n > 0 else { return nil }
        var isSorted = true
        for i in 1..<n where periods[i - 1].index > periods[i].index {
            isSorted = false
            break
        }
        let ordered = isSorted ? periods : periods.sorted { $0.index < $1.index }
        guard let first = ordered.first, let last = ordered.last else { return nil }
        if time < first.start || time > last.end { return nil }

        for i in 0..<n {
            let p = ordered[i]
            let rowTop = periodTop(periodIndex: p.index, periodCount: n, rowHeight: rowHeight, groupGap: groupGap)
            if time >= p.start && time <= p.end {
                let totalSec = max(1, p.end.totalSeconds - p.start.totalSeconds)
                let elapsedSec = min(max(0, time.totalSeconds - p.start.totalSeconds), totalSec)
                return rowTop + rowHeight * (Double(elapsedSec) / Double(totalSec))
            }
            if i + 1 < n {
                let next = ordered[i + 1]
                if time > p.end && time < next.start {
                    let gapStart = rowTop + rowHeight
                    let gapEnd = periodTop(periodIndex: next.index, periodCount: n, rowHeight: rowHeight, groupGap: groupGap)
                    if gapEnd <= gapStart { return gapStart }
                    let gapTotal = max(1, next.start.totalSeconds - p.end.totalSeconds)
                    let gapElapsed = min(max(0, time.totalSeconds - p.end.totalSeconds), gapTotal)
                    return gapStart + (gapEnd - gapStart) * (Double(gapElapsed) / Double(gapTotal))
                }
            }
        }
        return nil
    }

    /// Returns the set of weeks (`1...semester.totalWeeks`) that have at least one course occurrence
    /// in a single O(courses * weeks + exceptions) pass.
    public static func nonEmptyWeeks(snapshot: AppSnapshot, semester: Semester) -> Set<Int> {
        let total = max(1, semester.totalWeeks)
        let cal = SemesterCalendar(semester: semester)
        var exByKey: [CourseExpander.ExceptionKey: CourseException] = [:]
        if !snapshot.exceptions.isEmpty {
            exByKey.reserveCapacity(snapshot.exceptions.count)
            for ex in snapshot.exceptions where ex.deletedAt == nil {
                exByKey[CourseExpander.ExceptionKey(courseId: ex.courseId, week: ex.week)] = ex
            }
        }
        var result = Set<Int>()
        result.reserveCapacity(total)
        for c in snapshot.courses where c.deletedAt == nil && c.semesterId == semester.id {
            let baseHasClock = (c.startLocalTime != nil || cal.startTime(period: c.startPeriod) != nil)
                && (c.endLocalTime != nil || cal.endTime(period: c.endPeriod) != nil)
            for w in c.weeks {
                let ex = exByKey.isEmpty ? nil : exByKey[CourseExpander.ExceptionKey(courseId: c.id, week: w)]
                if ex?.type == .CANCEL { continue }
                let isRescheduled = ex?.type == .RESCHEDULE
                if isRescheduled {
                    let sp = ex?.newStartPeriod ?? c.startPeriod
                    let ep = ex?.newEndPeriod ?? c.endPeriod
                    let useCustomClock = ex?.newStartPeriod == nil && ex?.newEndPeriod == nil
                    let hasClock = ((useCustomClock ? c.startLocalTime : nil) ?? cal.startTime(period: sp)) != nil
                        && ((useCustomClock ? c.endLocalTime : nil) ?? cal.endTime(period: ep)) != nil
                    guard hasClock else { continue }
                    let targetWeek = ex?.newWeek ?? w
                    let dow = ex?.newDayOfWeek ?? c.dayOfWeek
                    let resolvedWeek = (1...7).contains(dow) ? targetWeek : cal.weekOf(cal.dateOf(week: targetWeek, dayOfWeek: dow))
                    if resolvedWeek >= 1 && resolvedWeek <= total {
                        result.insert(resolvedWeek)
                    }
                } else {
                    guard baseHasClock else { continue }
                    let resolvedWeek = (1...7).contains(c.dayOfWeek) ? w : cal.weekOf(cal.dateOf(week: w, dayOfWeek: c.dayOfWeek))
                    if resolvedWeek >= 1 && resolvedWeek <= total {
                        result.insert(resolvedWeek)
                    }
                }
            }
            if result.count == total {
                break
            }
        }
        return result
    }

    private struct RawSlot {
        let id: String
        let kind: TimetableSlotKind
        let startPeriod: Int
        let endPeriod: Int
        let listColorIndex: Int?

        var kindRank: Int {
            switch kind {
            case .course: return 0
            case .event: return 1
            case .taskBlock: return 2
            }
        }
    }

    public static func buildWeek(
        snapshot: AppSnapshot,
        semester: Semester? = nil,
        week: Int,
        defaultBlockMinutes: Int = 30,
        includeTimedEvents: Bool = true
    ) -> TimetableWeekLayout? {
        let live = snapshot.live()
        guard let sem = semester ?? live.currentSemester else { return nil }
        let cal = SemesterCalendar(semester: sem)
        let periods = cal.periods
        guard !periods.isEmpty else { return nil }
        var periodRowMap: [Int: Int] = [:]
        periodRowMap.reserveCapacity(periods.count)
        var minPeriod = periods[0].index
        var maxPeriod = periods[0].index
        for (offset, period) in periods.enumerated() {
            if periodRowMap[period.index] == nil {
                periodRowMap[period.index] = offset
            }
            if period.index < minPeriod { minPeriod = period.index }
            if period.index > maxPeriod { maxPeriod = period.index }
        }
        let totalRows = Double(periods.count)
        let startDate = cal.weekStart(week)
        let endDate = cal.weekEnd(week)

        let courses = CourseExpander.occurrencesInWeek(
            semester: sem,
            courses: live.courses,
            exceptions: live.exceptions,
            week: week
        )
        let events = includeTimedEvents
            ? EventExpander.occurrences(events: live.events, from: startDate, to: endDate).filter { !$0.event.allDay }
            : []

        var listColorById: [String: Int] = [:]
        listColorById.reserveCapacity(live.lists.count)
        for list in live.lists where listColorById[list.id] == nil {
            listColorById[list.id] = list.colorIndex
        }

        var rawByDay: [Int: [RawSlot]] = [:]
        var pillsByDay: [Int: [TimetableFlexiblePill]] = [:]
        var parcelsByDay: [Int: [GokeTask]] = [:]
        for occ in courses {
            let dow = occ.date.dayOfWeek
            let clampedStart = min(max(minPeriod, occ.startPeriod), maxPeriod)
            let clampedEnd = min(max(clampedStart, occ.endPeriod), maxPeriod)
            rawByDay[dow, default: []].append(
                RawSlot(
                    id: "course:\(occ.id)",
                    kind: .course(occ),
                    startPeriod: clampedStart,
                    endPeriod: clampedEnd,
                    listColorIndex: nil
                )
            )
        }

        for ev in events {
            guard ev.date >= startDate && ev.date <= endDate else { continue }
            guard let rows = AgendaEngine.blockRows(periods: periods, start: ev.start.time, end: ev.end.time) else { continue }
            let dow = ev.date.dayOfWeek
            rawByDay[dow, default: []].append(
                RawSlot(
                    id: "event:\(ev.id)",
                    kind: .event(ev),
                    startPeriod: rows.lowerBound,
                    endPeriod: rows.upperBound,
                    listColorIndex: nil
                )
            )
        }

        for task in TaskLogic.topLevel(live.tasks) {
            if isParcelTask(task) {
                guard !task.done else { continue }
                let parcelDate = task.blockStart?.date ?? task.due?.date
                if let parcelDate, parcelDate >= startDate && parcelDate <= endDate {
                    parcelsByDay[parcelDate.dayOfWeek, default: []].append(task)
                }
                continue
            }

            guard let blockStart = task.blockStart,
                  let blockEnd = TaskLogic.blockEnd(task: task, defaultMinutes: defaultBlockMinutes)
            else { continue }

            let durationSecs = max(60, blockStart.secondsUntil(blockEnd))
            let durationMinutes = Int(durationSecs / 60)

            var occDates: [LocalDate] = []
            if let rule = task.repeat {
                occDates = RecurrenceEngine.dates(
                    rule: rule,
                    anchor: blockStart.date,
                    from: startDate,
                    to: endDate
                )
            } else if blockStart.date >= startDate && blockStart.date <= endDate {
                occDates = [blockStart.date]
            }

            let resolvedListColor: Int = if task.listId == nil && task.isExam {
                2
            } else if let lid = task.listId, let idx = listColorById[lid] {
                idx
            } else {
                1
            }

            for occDate in occDates {
                let occStart = LocalDateTime(date: occDate, time: blockStart.time)
                let occEnd = occStart.addingSeconds(durationSecs)
                let dow = occDate.dayOfWeek
                if durationMinutes > flexibleWindowMinutesThreshold {
                    if !task.done {
                        pillsByDay[dow, default: []].append(
                            TimetableFlexiblePill(
                                id: "pill:\(task.id)#\(occStart.description)",
                                task: task,
                                start: occStart,
                                end: occEnd
                            )
                        )
                    }
                } else if let rows = AgendaEngine.blockRows(periods: periods, start: occStart.time, end: occEnd.time) {
                    rawByDay[dow, default: []].append(
                        RawSlot(
                            id: "block:\(task.id)#\(occStart.description)",
                            kind: .taskBlock(task: task, start: occStart, end: occEnd),
                            startPeriod: rows.lowerBound,
                            endPeriod: rows.upperBound,
                            listColorIndex: resolvedListColor
                        )
                    )
                }
            }
        }

        var columns: [TimetableColumn] = []
        columns.reserveCapacity(7)
        for dow in 1...7 {
            let date = cal.dateOf(week: week, dayOfWeek: dow)
            let slots = layoutDayColumn(
                dayOfWeek: dow,
                rawSlots: rawByDay[dow] ?? [],
                periodRowMap: periodRowMap,
                totalRows: totalRows
            )
            let pills = (pillsByDay[dow] ?? []).sorted { $0.start < $1.start }
            let parcels = (parcelsByDay[dow] ?? []).sorted { lhs, rhs in
                if let ld = lhs.due, let rd = rhs.due, ld != rd { return ld < rd }
                return lhs.sortOrder < rhs.sortOrder
            }
            columns.append(
                TimetableColumn(
                    dayOfWeek: dow,
                    date: date,
                    slots: slots,
                    flexiblePills: pills,
                    dayParcels: parcels
                )
            )
        }

        return TimetableWeekLayout(
            semesterId: sem.id,
            week: week,
            startDate: startDate,
            endDate: endDate,
            periods: periods,
            columns: columns
        )
    }

    private static func layoutDayColumn(
        dayOfWeek: Int,
        rawSlots: [RawSlot],
        periodRowMap: [Int: Int],
        totalRows: Double
    ) -> [TimetableSlotFrame] {
        guard !rawSlots.isEmpty else { return [] }
        let sortedForClusters = rawSlots.sorted { lhs, rhs in
            if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
            if lhs.kindRank != rhs.kindRank { return lhs.kindRank < rhs.kindRank }
            if lhs.endPeriod != rhs.endPeriod { return lhs.endPeriod > rhs.endPeriod }
            return lhs.id < rhs.id
        }

        // Partition into connected overlap clusters.
        var clusters: [[RawSlot]] = []
        var currentCluster: [RawSlot] = []
        var clusterMaxEnd = -1

        for slot in sortedForClusters {
            if currentCluster.isEmpty {
                currentCluster = [slot]
                clusterMaxEnd = slot.endPeriod
            } else if slot.startPeriod <= clusterMaxEnd {
                currentCluster.append(slot)
                clusterMaxEnd = max(clusterMaxEnd, slot.endPeriod)
            } else {
                clusters.append(currentCluster)
                currentCluster = [slot]
                clusterMaxEnd = slot.endPeriod
            }
        }
        if !currentCluster.isEmpty {
            clusters.append(currentCluster)
        }

        var frames: [TimetableSlotFrame] = []
        frames.reserveCapacity(sortedForClusters.count)

        let maxStackBackLayers = 2

        for (clusterIdx, cluster) in clusters.enumerated() {
            let clusterId = "\(dayOfWeek)-\(clusterIdx)"
            let clusterSize = cluster.count

            // Lane assignment (side-by-side & non-overlapping reuse):
            // Sort by startPeriod FIRST (kindRank tie-breaker at the same startPeriod, NOT globally before startPeriod)
            // so disjoint earlier items never get stranded in a back lane as blank unlabeled rectangles.
            let laneOrdered = cluster.sorted { lhs, rhs in
                if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
                if lhs.kindRank != rhs.kindRank { return lhs.kindRank < rhs.kindRank }
                if lhs.endPeriod != rhs.endPeriod { return lhs.endPeriod > rhs.endPeriod }
                return lhs.id < rhs.id
            }

            var laneEnds: [Int] = []
            var laneById: [String: Int] = [:]
            for slot in laneOrdered {
                if let reuseLane = laneEnds.firstIndex(where: { $0 < slot.startPeriod }) {
                    laneEnds[reuseLane] = slot.endPeriod
                    laneById[slot.id] = reuseLane
                } else {
                    let newLane = laneEnds.count
                    laneEnds.append(slot.endPeriod)
                    laneById[slot.id] = newLane
                }
            }
            let laneCount = max(1, laneEnds.count)

            // Android parity (TimetableScreen.kt:1828-1875):
            // In stacked mode, pick ONE front anchor (courses first, then earliest startPeriod, then longest span),
            // and pack remaining members into up to 2 non-overlapping back peek layers sorted by startPeriod.
            // Items beyond 2 simultaneous back layers get stackDepth = nil (tucked cleanly behind the front card)
            // while remaining 100% reachable via `column.cluster(for:)` sharing `clusterId`.
            let stackOrdered = cluster.sorted { lhs, rhs in
                if lhs.kindRank != rhs.kindRank { return lhs.kindRank < rhs.kindRank }
                if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
                if lhs.endPeriod != rhs.endPeriod { return lhs.endPeriod > rhs.endPeriod }
                return lhs.id < rhs.id
            }
            let frontId = stackOrdered.first?.id
            var stackDepthById: [String: Int?] = [:]
            var clusterMaxDepth = 0
            if let frontId {
                stackDepthById[frontId] = 0
            }
            if clusterSize > 1 {
                let backOrdered = stackOrdered.dropFirst().sorted { lhs, rhs in
                    if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
                    if lhs.endPeriod != rhs.endPeriod { return lhs.endPeriod < rhs.endPeriod }
                    return lhs.id < rhs.id
                }
                var layerEnds = Array(repeating: -1, count: maxStackBackLayers)
                for slot in backOrdered {
                    if let layer = layerEnds.firstIndex(where: { $0 < slot.startPeriod }) {
                        layerEnds[layer] = max(layerEnds[layer], slot.endPeriod)
                        let depth = layer + 1
                        stackDepthById[slot.id] = depth
                        clusterMaxDepth = max(clusterMaxDepth, depth)
                    } else {
                        stackDepthById[slot.id] = nil
                    }
                }
                clusterMaxDepth = max(1, clusterMaxDepth)
            }

            for slot in laneOrdered {
                let lane = laneById[slot.id] ?? 0
                let isFront = (slot.id == frontId)
                let depth = stackDepthById[slot.id] ?? (isFront ? 0 : nil)
                let startRow = periodRowMap[slot.startPeriod] ?? max(0, slot.startPeriod - 1)
                let endRow = periodRowMap[slot.endPeriod] ?? max(startRow, slot.endPeriod - 1)
                let normY = Double(startRow) / totalRows
                let normH = Double(max(1, endRow - startRow + 1)) / totalRows
                frames.append(
                    TimetableSlotFrame(
                        id: slot.id,
                        kind: slot.kind,
                        dayOfWeek: dayOfWeek,
                        startPeriod: slot.startPeriod,
                        endPeriod: slot.endPeriod,
                        laneIndex: lane,
                        laneCount: laneCount,
                        normalizedY: normY,
                        normalizedHeight: normH,
                        listColorIndex: slot.listColorIndex,
                        clusterId: clusterId,
                        clusterSize: clusterSize,
                        isStackFront: isFront,
                        stackDepth: depth,
                        clusterMaxDepth: clusterMaxDepth
                    )
                )
            }
        }
        return frames
    }

    /// Computes the horizontal `[startX, endX]` span for today's current-time indicator bar at vertical
    /// coordinate `y`, concentrically insetting endpoints when `y` passes through a visible block's
    /// rounded corner bands so the hairline never pokes outside the rounded corners.
    public static func calcNowBarSpan(
        defaultStartX: Double,
        defaultEndX: Double,
        y: Double,
        edgeInset: Double,
        blocks: [TimetableNowBarBlock]
    ) -> TimetableNowBarSpan {
        var leftBound: Double?
        var rightBound: Double?
        for b in blocks {
            if y < b.top || y > b.bottom { continue }
            let w = b.right - b.left
            let h = b.bottom - b.top
            if w <= 0 || h <= 0 { continue }
            let r = min(b.cornerRadius, w * 0.5, h * 0.5)
            let inset = min(max(0, edgeInset), r)
            let rInner = max(0, r - inset)
            let dy: Double
            if y < b.top + r {
                dy = b.top + r - y
            } else if y > b.bottom - r {
                dy = y - (b.bottom - r)
            } else {
                dy = 0
            }
            let dxInner = dy < rInner ? sqrt(rInner * rInner - dy * dy) : 0
            let innerLeft = b.left + r - dxInner
            let innerRight = b.right - r + dxInner
            if b.left <= defaultStartX {
                leftBound = leftBound.map { min($0, innerLeft) } ?? innerLeft
            }
            if b.right >= defaultEndX || b.isStackedFront {
                rightBound = rightBound.map { max($0, innerRight) } ?? innerRight
            }
        }
        let startX = min(defaultEndX, leftBound.map { max(defaultStartX, $0) } ?? defaultStartX)
        let endX = min(defaultEndX, max(startX, rightBound.map { min(defaultEndX, $0) } ?? defaultEndX))
        return TimetableNowBarSpan(startX: startX, endX: endX)
    }
}
