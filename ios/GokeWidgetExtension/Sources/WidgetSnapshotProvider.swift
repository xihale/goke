import AppIntents
import Foundation
import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

// MARK: - Shared Timeline Entry

/// Shared immutable widget context built once per timeline refresh so multiple `GokeWidgetEntry`
/// instances share theme, settings, and per-week/per-day slices without retaining any `AppSnapshot`.
private final class WidgetTimelineBuilder {
    let liveSnapshot: AppSnapshot
    let settings: AppSettings
    let theme: GokeTheme
    let currentSemester: Semester?
    let semesterCalendar: SemesterCalendar?
    let listColorById: [String: Int]

    private var weekLayoutCache: [Int: TimetableWeekLayout] = [:]
    private var dayAgendaCache: [LocalDate: [AgendaItem]] = [:]
    private var upcomingCoursesCacheByDay: [LocalDate: [CourseOccurrence]] = [:]

    init(payload: WidgetSnapshotPayload?) {
        let live = (payload?.snapshot ?? AppSnapshot()).live()
        let settings = payload?.settings ?? .default
        self.liveSnapshot = live
        self.settings = settings
        self.theme = GokeTheme.resolve(settings: settings)
        self.currentSemester = live.currentSemester
        self.semesterCalendar = live.currentSemester.map(SemesterCalendar.init(semester:))
        var colorMap: [String: Int] = [:]
        colorMap.reserveCapacity(live.lists.count)
        for list in live.lists where list.deletedAt == nil {
            colorMap[list.id] = list.colorIndex
        }
        self.listColorById = colorMap
    }

    func resolveTaskColorIndex(_ task: GokeTask) -> Int {
        if task.listId == nil && task.isExam {
            return 2
        }
        if let listId = task.listId, let idx = listColorById[listId] {
            return idx
        }
        return 1
    }

    func buildEntry(
        date: Date,
        isPlaceholder: Bool,
        timeZone: TimeZone = .current
    ) -> GokeWidgetEntry {
        let localNow = LocalDateTime.fromDate(date, in: timeZone)
        let today = localNow.date

        // Only surface `currentWeekNumber` when today actually falls within the semester dates (hides clamped week during vacation).
        let isInSemester = semesterCalendar?.isInSemester(today) ?? false
        let displayWeekNumber = semesterCalendar?.currentWeek(today: today)
        let currentWeekNumber = isInSemester ? displayWeekNumber : nil

        let weekLayout: TimetableWeekLayout?
        if let displayWeekNumber {
            if let cached = weekLayoutCache[displayWeekNumber] {
                weekLayout = cached
            } else if let built = TimetableLayout.buildWeek(
                snapshot: liveSnapshot,
                semester: currentSemester,
                week: displayWeekNumber,
                defaultBlockMinutes: settings.defaultBlockMinutes,
                includeTimedEvents: false
            ) {
                weekLayoutCache[displayWeekNumber] = built
                weekLayout = built
            } else {
                weekLayout = nil
            }
        } else {
            weekLayout = nil
        }

        let weekDateRangeText: String?
        if let firstDate = weekLayout?.columns.first?.date,
           let lastDate = weekLayout?.columns.last?.date {
            weekDateRangeText = "\(firstDate.month)/\(firstDate.day)–\(lastDate.month)/\(lastDate.day)"
        } else {
            weekDateRangeText = nil
        }

        // Single AgendaEngine.forDay expansion yields BOTH today's courses AND timed .block items.
        let dayItems: [AgendaItem]
        if let cached = dayAgendaCache[today] {
            dayItems = cached
        } else {
            let built = AgendaEngine.forDay(
                snapshot: liveSnapshot,
                date: today,
                defaultBlockMinutes: settings.defaultBlockMinutes,
                includeDoneTasks: false
            )
            dayAgendaCache[today] = built
            dayItems = built
        }

        var todayCourses: [CourseOccurrence] = []
        var remainingTodayCourses: [CourseOccurrence] = []
        var remainingSchedule: [WidgetScheduleSlot] = []
        for item in dayItems {
            switch item {
            case .course(let occ):
                todayCourses.append(occ)
                if occ.end > localNow {
                    remainingTodayCourses.append(occ)
                    remainingSchedule.append(.course(occ))
                }
            case .block(let task, let start, let end):
                if !task.done && task.source != .PARCEL && end > localNow {
                    let colorIdx = resolveTaskColorIndex(task)
                    remainingSchedule.append(
                        .block(WidgetTimedBlock(task: task, start: start, end: end, colorIndex: colorIdx))
                    )
                }
            case .event, .task:
                break
            }
        }
        remainingSchedule.sort { lhs, rhs in
            if lhs.start != rhs.start { return lhs.start < rhs.start }
            return lhs.end < rhs.end
        }

        // 7-day course horizon cached per calendar day, serving both `nextUp` and `upcomingCourses(limit: 4)`.
        let horizonCourses: [CourseOccurrence]
        if let cached = upcomingCoursesCacheByDay[today] {
            horizonCourses = cached
        } else if let sem = currentSemester {
            let built = CourseExpander.occurrences(
                semester: sem,
                courses: liveSnapshot.courses,
                exceptions: liveSnapshot.exceptions,
                from: today,
                to: today.addingDays(7)
            )
            upcomingCoursesCacheByDay[today] = built
            horizonCourses = built
        } else {
            horizonCourses = []
        }

        let upcoming = Array(horizonCourses.filter { $0.end > localNow }.prefix(5))
        let nextUp: NextUp?
        if let firstOcc = upcoming.first {
            let related = liveSnapshot.tasks
                .filter { !$0.done && $0.deletedAt == nil && $0.source != .PARCEL && $0.courseId == firstOcc.course.id }
                .sorted { lhs, rhs in
                    switch (lhs.due, rhs.due) {
                    case (let a?, let b?): return a < b
                    case (_?, nil): return true
                    case (nil, _?): return false
                    case (nil, nil): return lhs.priority > rhs.priority
                    }
                }
            nextUp = NextUp(occurrence: firstOcc, tasks: related)
        } else {
            nextUp = nil
        }

        // Today/overdue open tasks (for TodayOverviewWidget) vs all open checklist tasks (for TodoChecklistWidget).
        let todayTasks = TaskLogic.filter(liveSnapshot.tasks, smartList: .TODAY, now: localNow)
            .filter { !$0.done && $0.source != .PARCEL }
        let todayIds = Set(todayTasks.map(\.id))
        let restTasks = TaskLogic.filter(liveSnapshot.tasks, smartList: .ALL, now: localNow)
            .filter { !$0.done && $0.source != .PARCEL && !todayIds.contains($0.id) }
        let checklistTasks = todayTasks + restTasks

        return GokeWidgetEntry(
            date: date,
            localNow: localNow,
            isPlaceholder: isPlaceholder,
            settings: settings,
            theme: theme,
            currentSemesterName: currentSemester?.name,
            currentWeekNumber: currentWeekNumber,
            displayWeekNumber: displayWeekNumber,
            weekDateRangeText: weekDateRangeText,
            nextUp: nextUp,
            todayCourses: todayCourses,
            remainingTodayCourses: remainingTodayCourses,
            upcomingCoursesCache: upcoming,
            remainingTodaySchedule: remainingSchedule,
            todayTasks: todayTasks,
            checklistTasks: checklistTasks,
            currentWeekLayout: weekLayout
        )
    }
}

/// Lightweight, immutable, `Sendable` timeline entry shared across all Goke WidgetKit widgets.
/// Precomputes display slices and retains ZERO `AppSnapshot` copies to stay well under iOS's 30 MB Jetsam cap.
struct GokeWidgetEntry: TimelineEntry, Sendable {
    let date: Date
    let localNow: LocalDateTime
    let isPlaceholder: Bool
    let settings: AppSettings
    let theme: GokeTheme
    let currentSemesterName: String?
    /// Non-nil ONLY when today falls inside the active semester dates (nil during vacation).
    let currentWeekNumber: Int?
    /// Clamped week number used for mini timetable preview even during vacation.
    let displayWeekNumber: Int?
    /// Short date span for the displayed timetable week (e.g. `"11/24–11/30"`).
    let weekDateRangeText: String?
    let nextUp: NextUp?
    let todayCourses: [CourseOccurrence]
    let remainingTodayCourses: [CourseOccurrence]
    let upcomingCoursesCache: [CourseOccurrence]
    let remainingTodaySchedule: [WidgetScheduleSlot]
    /// Overdue + due-today open tasks (non-parcel), matching Android `Today` / `TodoWidgetProvider`.
    let todayTasks: [GokeTask]
    /// All open tasks (today/overdue first, followed by upcoming/inbox; non-parcel).
    let checklistTasks: [GokeTask]
    let currentWeekLayout: TimetableWeekLayout?

    init(
        date: Date,
        localNow: LocalDateTime,
        isPlaceholder: Bool,
        settings: AppSettings,
        theme: GokeTheme,
        currentSemesterName: String?,
        currentWeekNumber: Int?,
        displayWeekNumber: Int?,
        weekDateRangeText: String?,
        nextUp: NextUp?,
        todayCourses: [CourseOccurrence],
        remainingTodayCourses: [CourseOccurrence],
        upcomingCoursesCache: [CourseOccurrence],
        remainingTodaySchedule: [WidgetScheduleSlot],
        todayTasks: [GokeTask],
        checklistTasks: [GokeTask],
        currentWeekLayout: TimetableWeekLayout?
    ) {
        self.date = date
        self.localNow = localNow
        self.isPlaceholder = isPlaceholder
        self.settings = settings
        self.theme = theme
        self.currentSemesterName = currentSemesterName
        self.currentWeekNumber = currentWeekNumber
        self.displayWeekNumber = displayWeekNumber
        self.weekDateRangeText = weekDateRangeText
        self.nextUp = nextUp
        self.todayCourses = todayCourses
        self.remainingTodayCourses = remainingTodayCourses
        self.upcomingCoursesCache = upcomingCoursesCache
        self.remainingTodaySchedule = remainingTodaySchedule
        self.todayTasks = todayTasks
        self.checklistTasks = checklistTasks
        self.currentWeekLayout = currentWeekLayout
    }

    init(
        date: Date,
        payload: WidgetSnapshotPayload?,
        isPlaceholder: Bool = false,
        timeZone: TimeZone = .current
    ) {
        self = WidgetTimelineBuilder(payload: payload)
            .buildEntry(date: date, isPlaceholder: isPlaceholder, timeZone: timeZone)
    }

    /// Upcoming (or ongoing) courses within the next 7 days, precomputed chronologically.
    func upcomingCourses(limit: Int, horizonDays: Int = 7) -> [CourseOccurrence] {
        guard limit > 0 else { return [] }
        return Array(upcomingCoursesCache.prefix(limit))
    }

    /// Whether courses were scheduled today and every one of them has already finished.
    var hasFinishedAllTodayCourses: Bool {
        !todayCourses.isEmpty && remainingTodayCourses.isEmpty
    }

    /// Returns whether `occurrence` is currently in progress at `localNow`.
    func isInProgress(_ occurrence: CourseOccurrence) -> Bool {
        occurrence.start <= localNow && occurrence.end > localNow
    }
}

// MARK: - Timeline Provider

/// Shared WidgetKit `TimelineProvider` reading `AppGroupContainer.shared.readWidgetSnapshot()`.
/// Generates smart transition entries at today's class/block boundaries, mid-class progress ticks,
/// and guaranteed midnight rollover without burning WidgetKit's daily reload budget.
struct WidgetSnapshotProvider: TimelineProvider, Sendable {
    typealias Entry = GokeWidgetEntry

    func placeholder(in context: Context) -> GokeWidgetEntry {
        Self.samplePlaceholderEntry(now: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (GokeWidgetEntry) -> Void) {
        let now = Date()
        if let payload = AppGroupContainer.shared.readWidgetSnapshot() {
            completion(GokeWidgetEntry(date: now, payload: payload, isPlaceholder: false))
        } else if context.isPreview {
            completion(Self.samplePlaceholderEntry(now: now))
        } else {
            completion(GokeWidgetEntry(date: now, payload: nil, isPlaceholder: false))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<GokeWidgetEntry>) -> Void) {
        let now = Date()
        let payload = AppGroupContainer.shared.readWidgetSnapshot()
        let builder = WidgetTimelineBuilder(payload: payload)
        let transitionDates = Self.computeTransitionDates(
            now: now,
            liveSnapshot: builder.liveSnapshot,
            settings: builder.settings
        )

        var entries: [GokeWidgetEntry] = []
        entries.reserveCapacity(transitionDates.count + 1)
        entries.append(builder.buildEntry(date: now, isPlaceholder: false))
        for date in transitionDates {
            entries.append(builder.buildEntry(date: date, isPlaceholder: false))
        }

        // Reload right after the last precomputed transition (typically 00:01 after midnight rollover)
        // instead of burning WidgetKit's daily reload budget every 30 minutes.
        let nextReload: Date
        if let lastTransition = transitionDates.last {
            nextReload = lastTransition.addingTimeInterval(60)
        } else {
            nextReload = now.addingTimeInterval(4 * 3600)
        }

        completion(Timeline(entries: entries, policy: .after(nextReload)))
    }

    /// Computes today's upcoming class/block transition timestamps, optional 15-min progress ticks
    /// during any ongoing session, plus a guaranteed midnight rollover entry.
    static func computeTransitionDates(
        now: Date,
        payload: WidgetSnapshotPayload?,
        timeZone: TimeZone = .current
    ) -> [Date] {
        let live = (payload?.snapshot ?? AppSnapshot()).live()
        let settings = payload?.settings ?? .default
        return computeTransitionDates(now: now, liveSnapshot: live, settings: settings, timeZone: timeZone)
    }

    static func computeTransitionDates(
        now: Date,
        liveSnapshot snapshot: AppSnapshot,
        settings: AppSettings,
        timeZone: TimeZone = .current
    ) -> [Date] {
        let localNow = LocalDateTime.fromDate(now, in: timeZone)
        let tomorrowStart = localNow.date.addingDays(1).atStartOfDay().toDate(in: timeZone)
        var intraDayCandidates: [Date] = []

        func appendOngoingTicks(start: Date, end: Date) {
            guard start <= now && end > now else { return }
            let tickInterval: TimeInterval = 15 * 60
            var tick = now.addingTimeInterval(tickInterval)
            var count = 0
            while tick < end.addingTimeInterval(-60) && count < 4 {
                intraDayCandidates.append(tick)
                tick = tick.addingTimeInterval(tickInterval)
                count += 1
            }
        }

        if let sem = snapshot.currentSemester {
            let todayCourses = CourseExpander.occurrences(
                semester: sem,
                courses: snapshot.courses,
                exceptions: snapshot.exceptions,
                from: localNow.date,
                to: localNow.date
            )
            for occ in todayCourses {
                let startDate = occ.start.toDate(in: timeZone)
                let endDate = occ.end.toDate(in: timeZone)
                if startDate > now && startDate < tomorrowStart {
                    intraDayCandidates.append(startDate)
                }
                if endDate > now && endDate < tomorrowStart {
                    intraDayCandidates.append(endDate)
                }
                appendOngoingTicks(start: startDate, end: endDate)
            }
        }

        // Timed task blocks work regardless of whether a semester is configured.
        let blockDefaultMinutes = settings.defaultBlockMinutes
        for task in snapshot.tasks where task.deletedAt == nil && !task.done && task.source != .PARCEL {
            guard let blockStart = task.blockStart,
                  blockStart.date == localNow.date,
                  let blockEnd = TaskLogic.blockEnd(task: task, defaultMinutes: blockDefaultMinutes)
            else { continue }
            let startDate = blockStart.toDate(in: timeZone)
            let endDate = blockEnd.toDate(in: timeZone)
            if startDate > now && startDate < tomorrowStart {
                intraDayCandidates.append(startDate)
            }
            if endDate > now && endDate < tomorrowStart {
                intraDayCandidates.append(endDate)
            }
            appendOngoingTicks(start: startDate, end: endDate)
        }

        var result = Array(Set(intraDayCandidates).sorted().prefix(15))
        if tomorrowStart > now {
            result.append(tomorrowStart)
        }
        return result
    }

    /// Builds a realistic sample placeholder entry for the iOS Widget Gallery preview.
    static func samplePlaceholderEntry(now: Date) -> GokeWidgetEntry {
        let today = LocalDate.today()
        let week1Monday = today.startOfWeekMonday.addingWeeks(-5)
        let sem = Semester(
            id: "preview-sem",
            name: "2026 秋季学期",
            week1Monday: week1Monday,
            totalWeeks: 20,
            periods: GdutPeriods.default,
            isCurrent: true
        )
        let dow = today.dayOfWeek
        let nextDow = dow < 5 ? dow + 1 : 1
        let courses: [Course] = [
            Course(
                id: "preview-c1",
                semesterId: sem.id,
                name: "高等数学 A(1)",
                teacher: "陈老师",
                room: "教2-301",
                dayOfWeek: dow,
                startPeriod: 1,
                endPeriod: 2,
                weeks: WeekSets.all(16),
                colorIndex: 1
            ),
            Course(
                id: "preview-c2",
                semesterId: sem.id,
                name: "数据结构与算法",
                teacher: "林老师",
                room: "实验楼 B402",
                dayOfWeek: dow,
                startPeriod: 5,
                endPeriod: 6,
                weeks: WeekSets.all(16),
                colorIndex: 0
            ),
            Course(
                id: "preview-c3",
                semesterId: sem.id,
                name: "大学英语",
                teacher: "王老师",
                room: "教4-108",
                dayOfWeek: nextDow,
                startPeriod: 3,
                endPeriod: 4,
                weeks: WeekSets.all(16),
                colorIndex: 2
            ),
        ]
        let tasks: [GokeTask] = [
            GokeTask(
                id: "preview-t1",
                title: "高数第三章习题",
                priority: 2,
                due: today.atTime(hour: 22, minute: 0),
                dueHasTime: true,
                courseId: "preview-c1"
            ),
            GokeTask(
                id: "preview-t2",
                title: "提交数据结构实验报告",
                priority: 3,
                due: today.atTime(hour: 23, minute: 59),
                dueHasTime: true,
                courseId: "preview-c2"
            ),
        ]
        let snapshot = AppSnapshot(
            semesters: [sem],
            courses: courses,
            events: [],
            tasks: tasks
        )
        let payload = WidgetSnapshotPayload(snapshot: snapshot, settings: .default)
        return GokeWidgetEntry(date: now, payload: payload, isPlaceholder: true)
    }
}

// MARK: - Shared Widget Formatting, Deep Links & Controls

enum WidgetDeepLink {
    static let today = URL(string: "goke://today")!
    static let timetable = URL(string: "goke://timetable")!
    static let todo = URL(string: "goke://todo")!
    static let quickAdd = URL(string: "goke://quickadd")!

    static func todoItem(_ id: String) -> URL {
        URL(string: "goke://todo/\(id)") ?? todo
    }

    static func course(_ id: String) -> URL {
        URL(string: "goke://course?id=\(id)") ?? timetable
    }
}

enum WidgetFormat {
    static func hhmm(_ time: LocalTime) -> String {
        String(format: "%02d:%02d", time.hour, time.minute)
    }

    static func timeRange(start: LocalTime, end: LocalTime) -> String {
        "\(hhmm(start))–\(hhmm(end))"
    }

    static func periodRange(start: Int, end: Int) -> String {
        start == end ? "第\(start)节" : "\(start)-\(end)节"
    }

    static func shortDate(_ date: LocalDate) -> String {
        "\(date.month)月\(date.day)日"
    }

    static func weekdayShort(_ dayOfWeek: Int) -> String {
        switch dayOfWeek {
        case 1: "一"
        case 2: "二"
        case 3: "三"
        case 4: "四"
        case 5: "五"
        case 6: "六"
        case 7: "日"
        default: ""
        }
    }

    static func weekdayLabel(_ dayOfWeek: Int) -> String {
        "周\(weekdayShort(dayOfWeek))"
    }

    static func relativeDayLabel(from today: LocalDate, to target: LocalDate) -> String {
        let diff = today.daysUntil(target)
        switch diff {
        case ..<0: return "已逾期"
        case 0: return "今天"
        case 1: return "明天"
        case 2: return "后天"
        default: return "\(diff)天后"
        }
    }

    /// Deterministic FNV-1a color index (0..<8) so colors never shuffle across widget process restarts.
    static func stableColorIndex(for key: String) -> Int {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in key.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return Int(hash % 8)
    }

    /// Safe bounded countdown `Text` that freezes cleanly at `0:00` once `end` passes
    /// (never reverses and counts UP like `Text(date, style: .timer)`).
    static func countdownText(start: Date, end: Date, fallback: String = "0:00") -> Text {
        if end > start {
            return Text(timerInterval: start...end, countsDown: true)
        }
        return Text(fallback)
    }

    /// Returns true on iOS 18+ Tinted / Accented Home Screen rendering modes so widgets drop
    /// opaque pastel fills in favor of clean borders/tints and `.widgetAccentable()` highlights.
    static func isAccentedMode(_ renderingMode: WidgetRenderingMode) -> Bool {
        if #available(iOS 18.0, *) {
            return renderingMode != .fullColor
        }
        return false
    }

    private static let examPrefixRegex = try! NSRegularExpression(
        pattern: #"^((?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*(.*)$"#
    )

    /// Formats an exam course location cleanly with extracted seat number when present.
    static func examLocationLine(room: String, note: String) -> String {
        let loc = room.trimmingCharacters(in: .whitespacesAndNewlines)
        let seat = ExamTextHelper.extractExamSeat(from: note)
        switch (loc.isEmpty, seat) {
        case (false, let s?): return "\(loc) · 座位 \(s)"
        case (false, nil): return loc
        case (true, let s?): return "座位 \(s)"
        case (true, nil): return ""
        }
    }

    /// Splits a raw exam title into its deduplicated badge (`badgeFull` / 2-char `badgeShort`)
    /// and clean title without redundant prefixes.
    static func splitExamDisplay(_ rawName: String, fallbackBadge: String = "考试") -> WidgetExamDisplay {
        let trimmed = rawName.trimmingCharacters(in: .whitespacesAndNewlines)

        var extractedPrefix: String?
        var extractedRest: String?
        if let match = examPrefixRegex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
            if let r1 = Range(match.range(at: 1), in: trimmed) {
                let p = String(trimmed[r1]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !p.isEmpty { extractedPrefix = p }
            }
            if let r2 = Range(match.range(at: 2), in: trimmed) {
                let rest = String(trimmed[r2]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !rest.isEmpty { extractedRest = rest }
            }
        }

        let cleanTitle = (extractedRest ?? trimmed)
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
        let fallback = fallbackBadge.trimmingCharacters(in: .whitespacesAndNewlines)
        let badgeFull = extractedPrefix ?? (fallback.isEmpty ? "考试" : fallback)
        return WidgetExamDisplay(
            badgeFull: badgeFull,
            badgeShort: shortenExamBadge(badgeFull),
            title: cleanTitle
        )
    }

    private static func shortenExamBadge(_ badgeFull: String) -> String {
        if badgeFull.contains("期中") { return "期中" }
        if badgeFull.contains("期末") { return "期末" }
        if badgeFull.contains("随堂") { return "随堂" }
        if badgeFull.contains("结课") { return "结课" }
        if badgeFull.contains("补考") { return "补考" }
        if badgeFull.contains("重考") { return "重考" }
        if badgeFull.contains("缓考") { return "缓考" }
        if badgeFull.contains("机考") || badgeFull.contains("机试") || badgeFull.contains("上机") { return "机考" }
        if badgeFull.contains("实验") { return "实验" }
        if badgeFull.contains("测验") || badgeFull.contains("小测") || badgeFull.contains("测试") { return "测验" }
        return String(badgeFull.prefix(2))
    }

    /// Resolves concise metadata for a scheduled `.block` task (location or primary tag).
    static func blockMetadata(for task: GokeTask) -> String {
        let loc = (task.extra["location"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !loc.isEmpty { return loc }
        if let tag = task.tags.first(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
            return tag
        }
        return "日程块"
    }
}

/// Shared layout tokens unifying padding, concentric corner radii, typography, and alignment
/// across all Goke Home Screen widgets (verified against iPhone 11 Small 169×169, Medium 360×169,
/// Large 360×376 with `.contentMarginsDisabled()`).
enum WidgetMetrics {
    /// Editorial outer padding for `NextClass`, `TodayOverview`, `TodoChecklist`, and `HotWater`
    /// (inner canvas: Small 141×141, Medium 332×141, Large 332×348).
    static let editorialPadding: CGFloat = 14
    /// Compact grid padding for `WeeklyTimetableWidget`
    /// (inner canvas: Small 149×147, Medium 340×147, Large 340×354).
    static let gridPaddingH: CGFloat = 10
    static let gridPaddingV: CGFloat = 11
    /// Concentric inner card radius inside `editorialPadding` of ~22–24pt outer widget radius.
    static let cardRadius: CGFloat = 10
    /// Concentric right-side full-height action tile radius (`HotWaterWidget` Medium).
    static let tileRadius: CGFloat = 12
    /// Unified `+` quick-add button diameter across `TodayOverviewWidget` and `TodoChecklistWidget`.
    static let quickAddSize: CGFloat = 24
    /// Unified leading time column width across `TodayOverviewWidget` and `NextClassWidget`.
    static let timeColumnWidth: CGFloat = 42
    /// Unified task title font size across `TodayOverviewWidget` and `TodoChecklistWidget`.
    static let taskTitleSize: CGFloat = 13
    /// Interactive checkbox hit frame (`24×24`) and visual square (`16×16`) with `-4pt` optical lead.
    static let checkButtonHitSize: CGFloat = 24
    static let checkBoxSize: CGFloat = 16
    static let checkOpticalLeading: CGFloat = -4
}

/// Shared 24×24pt circular `+` quick-add button used across Todo and Today widgets.
struct WidgetQuickAddButton: View {
    let theme: GokeTheme
    let isAccented: Bool

    var body: some View {
        Link(destination: WidgetDeepLink.quickAdd) {
            Image(systemName: "plus")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(isAccented ? Color.primary : theme.onAccentContainer)
                .frame(width: WidgetMetrics.quickAddSize, height: WidgetMetrics.quickAddSize)
                .background(
                    isAccented ? Color.primary.opacity(0.14) : theme.accentContainer,
                    in: Circle()
                )
                .widgetAccentable()
        }
    }
}

/// Shared 24×24pt interactive rounded-square `MintCheck` checkbox (16×16 visual box) for Todo & Today widgets.
/// Pair with `.padding(.leading, WidgetMetrics.checkOpticalLeading)` (`-4pt`) so the visual box left edge
/// aligns flush at `x = 0pt` with the widget header.
struct WidgetMintCheckButton: View {
    let task: GokeTask
    let overdue: Bool
    let theme: GokeTheme
    let isAccented: Bool

    var body: some View {
        let strokeColor: Color = if isAccented {
            overdue ? Color.primary : Color.secondary
        } else {
            overdue ? theme.error : (theme.priorityColor(task.priority) ?? theme.outline)
        }

        Button(intent: ToggleWidgetTaskIntent(taskId: task.id)) {
            ZStack {
                if task.done {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .fill(isAccented ? Color.primary : theme.accent)
                        .frame(width: WidgetMetrics.checkBoxSize, height: WidgetMetrics.checkBoxSize)
                    Image(systemName: "checkmark")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(isAccented ? Color.black : theme.onAccent)
                } else {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .strokeBorder(strokeColor, lineWidth: 1.5)
                        .frame(width: WidgetMetrics.checkBoxSize, height: WidgetMetrics.checkBoxSize)
                }
            }
            .frame(width: WidgetMetrics.checkButtonHitSize, height: WidgetMetrics.checkButtonHitSize)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .widgetAccentable(task.done || overdue)
    }
}

/// Split exam display metadata (badge + clean title without redundant prefix).
struct WidgetExamDisplay: Sendable, Equatable, Hashable {
    let badgeFull: String
    let badgeShort: String
    let title: String
}

/// Geometry helper ensuring circular widgets and `nxn` square slots render true 1:1 circles
/// without elliptical stretching across different device sizes.
struct WidgetCircleMetrics: Sendable, Equatable {
    let diameter: CGFloat
    let isSquareSlot: Bool
    let isCompactWidth: Bool

    init(size: CGSize) {
        let w = max(1, size.width)
        let h = max(1, size.height)
        self.diameter = min(w, h)
        let ratio = max(w, h) / min(w, h)
        self.isSquareSlot = (h >= w) ? (ratio <= 1.45) : (ratio <= 1.35)
        self.isCompactWidth = w < 155
    }
}

/// Timed `.block` item projected for WidgetKit today schedule rows.
struct WidgetTimedBlock: Sendable, Hashable, Identifiable {
    let task: GokeTask
    let start: LocalDateTime
    let end: LocalDateTime
    let colorIndex: Int

    var id: String { "block:\(task.id)#\(start.description)" }

    var durationMinutes: Int {
        max(1, Int(start.secondsUntil(end) / 60))
    }
}

/// Unified remaining-today schedule entry (course or timed `.block`) for `TodayOverviewWidget`.
enum WidgetScheduleSlot: Sendable, Hashable, Identifiable {
    case course(CourseOccurrence)
    case block(WidgetTimedBlock)

    var id: String {
        switch self {
        case .course(let occ): return "course:\(occ.id)"
        case .block(let block): return block.id
        }
    }

    var start: LocalDateTime {
        switch self {
        case .course(let occ): return occ.start
        case .block(let block): return block.start
        }
    }

    var end: LocalDateTime {
        switch self {
        case .course(let occ): return occ.end
        case .block(let block): return block.end
        }
    }
}
