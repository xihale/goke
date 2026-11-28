import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Drag / Selection / Sheet Draft Models

private enum TaskBlockDragMode: Equatable {
    case move
    case resizeTop
    case resizeBottom
}

private struct DraggingTaskBlockState: Equatable {
    let task: GokeTask
    let originStart: LocalDateTime
    let originMinutes: Int
    let mode: TaskBlockDragMode
    var previewStart: LocalDateTime
    var previewMinutes: Int
    var rawDeltaY: CGFloat
}

private struct BoxSelectState: Equatable {
    let startDayIndex: Int
    let startPeriod: Int
    var endDayIndex: Int
    var endPeriod: Int
}

private struct BoxSelectTodoDraft: Identifiable, Hashable {
    var id: String { "\(dates.first?.description ?? ""):\(startTime.description)-\(endTime.description):\(dates.count)" }
    let dates: [LocalDate]
    let startTime: LocalTime
    let endTime: LocalTime
}

private struct DragSnapKey: Equatable {
    let dayOfWeek: Int
    let startPeriod: Int
    let endPeriod: Int
}

private struct ReschedulePickDraft: Identifiable, Hashable {
    var id: String { "\(course.id):\(sourceWeek)->\(targetWeek):\(targetDayOfWeek):\(targetStartPeriod)-\(targetEndPeriod)" }
    let course: Course
    let sourceWeek: Int
    let initialRoom: String
    let targetWeek: Int
    let targetDayOfWeek: Int
    let targetStartPeriod: Int
    let targetEndPeriod: Int
}

private struct EmptySlotDraft: Identifiable, Hashable {
    var id: String { "\(week):\(dayOfWeek):\(startPeriod)-\(endPeriod)" }
    let week: Int
    let dayOfWeek: Int
    let startPeriod: Int
    let endPeriod: Int
}

private enum TimetableActiveSheet: Identifiable {
    case weekPicker
    case export
    case previewCourse(CourseOccurrence)
    case emptySlot(EmptySlotDraft)
    case boxSelectTodo(BoxSelectTodoDraft)
    case rescheduleConfirm(ReschedulePickDraft)

    var id: String {
        switch self {
        case .weekPicker: "weekPicker"
        case .export: "export"
        case .previewCourse(let occ): "preview:\(occ.id)"
        case .emptySlot(let draft): "empty:\(draft.id)"
        case .boxSelectTodo(let draft): "box:\(draft.id)"
        case .rescheduleConfirm(let draft): "reschedule:\(draft.id)"
        }
    }
}

private enum PendingTimetableSheetAction {
    case openTask(String)
    case openCourseEdit(String)
    case startReschedule(course: Course, preferredSourceWeek: Int?, initialRoom: String)
}

private struct TimetableCacheBaseKey: Equatable, Sendable {
    let timetableRevision: UInt64
    let semesterId: String?
    let defaultBlockMinutes: Int
}

private struct TimetableCacheKey: Equatable {
    let base: TimetableCacheBaseKey
    let selectedWeek: Int
    let ready: Bool
}

private struct TimetableTodoCountsKey: Equatable {
    let snapshotRevision: UInt64
    let semesterId: String?
    let ready: Bool
}

private struct TimetableLiveClockKey: Equatable {
    let isVisible: Bool
    let shouldTick: Bool
    let intervalSeconds: Int
}

private struct TimetablePagerReloadToken: Equatable {
    let base: TimetableCacheBaseKey
    let today: LocalDate
    let actualCurrentWeek: Int
    let isLiveCurrentWeekVisible: Bool
    let shouldTick: Bool
    let intervalSeconds: Int
    let showWeekend: Bool
    let showTeacher: Bool
    let sessionTodoCounts: [CourseOccurrenceKey: Int]
    let reschedulePickingCourseId: String?
}

@MainActor
private final class WeekLayoutCacheBox {
    private var baseKey: TimetableCacheBaseKey?
    private var layouts: [Int: TimetableWeekLayout] = [:]

    func invalidate() {
        baseKey = nil
        layouts.removeAll(keepingCapacity: true)
    }

    func layout(
        for week: Int,
        semester: Semester,
        key: TimetableCacheBaseKey,
        liveSnapshot: AppSnapshot
    ) -> TimetableWeekLayout {
        if baseKey != key {
            baseKey = key
            layouts.removeAll(keepingCapacity: true)
        }
        if let cached = layouts[week], cached.semesterId == semester.id {
            return cached
        }
        let built = TimetableLayout.buildWeek(
            snapshot: liveSnapshot,
            semester: semester,
            week: week,
            defaultBlockMinutes: key.defaultBlockMinutes,
            includeTimedEvents: true
        ) ?? Self.fallbackLayout(week: week, semester: semester)
        layouts[week] = built
        return built
    }

    func missingWeeks(
        around selectedWeek: Int,
        totalWeeks: Int,
        semesterId: String,
        key: TimetableCacheBaseKey
    ) -> [Int] {
        let low = max(1, selectedWeek - 2)
        let high = min(totalWeeks, selectedWeek + 2)
        if baseKey != key {
            return Array(low...high)
        }
        return (low...high).filter { layouts[$0]?.semesterId != semesterId }
    }

    func merge(
        _ built: [Int: TimetableWeekLayout],
        for key: TimetableCacheBaseKey,
        around selectedWeek: Int
    ) {
        if baseKey != key {
            baseKey = key
            layouts.removeAll(keepingCapacity: true)
        }
        for (week, layout) in built {
            if layouts[week]?.semesterId != layout.semesterId {
                layouts[week] = layout
            }
        }
        if layouts.count > 12 {
            layouts = layouts.filter { abs($0.key - selectedWeek) <= 4 }
        }
    }

    private static func fallbackLayout(week: Int, semester: Semester) -> TimetableWeekLayout {
        let semCal = SemesterCalendar(semester: semester)
        let fallbackColumns = (1...7).map { dow in
            TimetableColumn(
                dayOfWeek: dow,
                date: semCal.dateOf(week: week, dayOfWeek: dow),
                slots: [],
                flexiblePills: []
            )
        }
        return TimetableWeekLayout(
            semesterId: semester.id,
            week: week,
            startDate: semCal.weekStart(week),
            endDate: semCal.weekEnd(week),
            periods: semester.periods,
            columns: fallbackColumns
        )
    }
}

// MARK: - TimetableView

/// Android 1:1 Clean Paper Grid "课表" tab with 120Hz precomputed `TimetableWeekLayout`,
/// scoped current-week live heartbeat, in-place `CourseQuickPreviewSheet`,
/// `TimetableWeekPickerSheet`, long-press box-select, and `TaskBlock` move/resize gestures.
///
/// Zero ViewModels: derives layout directly from `AppContainer`, routes via `AppRouter`,
/// and commits all mutations through `ActionDispatcher`.
struct TimetableView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage("goke.timetable.showWeekend") private var showWeekend: Bool = true
    /// `nil` follows the active semester's `clampedCurrentWeek` so Frame 1 renders directly on the real week
    /// with zero Week-1 initial layout or `.onChange` jump.
    @State private var selectedWeekOverride: Int? = nil
    @State private var didRenderInitialFrame: Bool = false
    @State private var today: LocalDate = .today()
    @State private var pickerNonEmptyWeeks: Set<Int> = []
    @State private var dismissedEmptyBanner: Bool = false
    @State private var activeSheet: TimetableActiveSheet?
    @State private var reschedulePickingCourse: Course?
    @State private var rescheduleSourceWeek: Int = 1
    @State private var rescheduleInitialRoom: String = ""
    @State private var pendingSheetAction: PendingTimetableSheetAction?
    @State private var weekCache = WeekLayoutCacheBox()
    @State private var sessionTodoCounts: [CourseOccurrenceKey: Int] = [:]
    @State private var isGridDragging: Bool = false

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    init() {}

    private var currentSemester: Semester? {
        container.liveSnapshot.currentSemester
    }

    private var hasSemesterCourses: Bool {
        guard let sem = currentSemester else { return false }
        return container.liveSnapshot.courses.contains { $0.semesterId == sem.id }
    }

    private var totalWeeks: Int {
        max(1, currentSemester?.totalWeeks ?? 20)
    }

    /// Unclamped week number relative to `week1Monday` (can be `<= 0` before semester or `> totalWeeks` after semester).
    private var actualCurrentWeek: Int {
        guard let sem = currentSemester else { return 1 }
        return SemesterCalendar(semester: sem).weekOf(today)
    }

    /// Clamped page index in `1...totalWeeks` for initial page and tab-reselect jump.
    private var clampedCurrentWeek: Int {
        min(max(1, actualCurrentWeek), totalWeeks)
    }

    private var selectedWeek: Int {
        guard let override = selectedWeekOverride else { return clampedCurrentWeek }
        return min(max(1, override), totalWeeks)
    }

    private var selectedWeekBinding: Binding<Int> {
        Binding(
            get: { selectedWeek },
            set: { newValue in
                selectedWeekOverride = min(max(1, newValue), totalWeeks)
            }
        )
    }

    private var selectedWeekDateRangeText: String {
        guard let sem = currentSemester else { return "" }
        let cal = SemesterCalendar(semester: sem)
        let s = cal.weekStart(selectedWeek)
        let e = cal.weekEnd(selectedWeek)
        return "\(s.month)/\(s.day)–\(e.month)/\(e.day)"
    }

    private var cacheBaseKey: TimetableCacheBaseKey {
        TimetableCacheBaseKey(
            timetableRevision: container.timetableRevision,
            semesterId: currentSemester?.id,
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes
        )
    }

    private var cacheKey: TimetableCacheKey {
        TimetableCacheKey(
            base: cacheBaseKey,
            selectedWeek: selectedWeek,
            ready: didRenderInitialFrame
        )
    }

    private var todoCountsKey: TimetableTodoCountsKey {
        TimetableTodoCountsKey(
            snapshotRevision: container.snapshotRevision,
            semesterId: currentSemester?.id,
            ready: didRenderInitialFrame
        )
    }

    private var isLiveTimetableVisible: Bool {
        scenePhase == .active
            && router.selectedTab == .timetable
            && selectedWeek == actualCurrentWeek
    }

    private var shouldTickLiveTimetableClock: Bool {
        !performance.shouldPauseCosmeticTimers
    }

    private var liveClockIntervalSeconds: Int {
        performance.isDegraded ? 60 : 30
    }

    var body: some View {
        VStack(spacing: 0) {
            timetableHeader

            if let sem = currentSemester {
                timetableBody(semester: sem)
            } else if !container.isHydrated {
                hydratingGridShell
            } else {
                emptySemesterView
            }
        }
        .background { theme.paper.ignoresSafeArea() }
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $activeSheet, onDismiss: flushPendingSheetAction) { sheet in
            switch sheet {
            case .weekPicker:
                TimetableWeekPickerSheet(
                    selectedWeek: selectedWeekBinding,
                    currentWeek: actualCurrentWeek,
                    totalWeeks: totalWeeks,
                    semester: currentSemester,
                    nonEmptyWeeks: pickerNonEmptyWeeks
                )
                .gokePaperSheet(theme, detents: [.medium])

            case .export:
                TimetableExportSheet(initialWeek: selectedWeek)
                    .gokePaperSheet(theme, detents: [.large])

            case .previewCourse(let occ):
                CourseQuickPreviewSheet(
                    occurrence: occ,
                    semester: currentSemester,
                    onEditCourse: { courseId in
                        pendingSheetAction = .openCourseEdit(courseId)
                        activeSheet = nil
                    },
                    onCancelWeek: { targetOcc in
                        activeSheet = nil
                        cancelSingleWeek(targetOcc)
                    },
                    onStartReschedule: { targetOcc in
                        pendingSheetAction = .startReschedule(
                            course: targetOcc.course,
                            preferredSourceWeek: targetOcc.originalWeek,
                            initialRoom: targetOcc.room
                        )
                        activeSheet = nil
                    },
                    onRestoreOriginal: { targetOcc in
                        activeSheet = nil
                        restoreOriginalOccurrence(targetOcc)
                    },
                    onOpenTask: { taskId in
                        pendingSheetAction = .openTask(taskId)
                        activeSheet = nil
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])

            case .emptySlot(let draft):
                CourseEditView(
                    courseId: nil,
                    initialDayOfWeek: draft.dayOfWeek,
                    initialStartPeriod: draft.startPeriod,
                    initialEndPeriod: draft.endPeriod,
                    initialWeek: draft.week,
                    isSheet: true
                )
                .gokePaperSheet(theme, detents: [.large])

            case .boxSelectTodo(let draft):
                BoxSelectTimedTaskSheet(draft: draft)
                    .gokePaperSheet(theme, detents: [.medium])

            case .rescheduleConfirm(let draft):
                ReschedulePickConfirmSheet(
                    draft: draft,
                    semester: currentSemester,
                    onPickOnGrid: {
                        pendingSheetAction = .startReschedule(
                            course: draft.course,
                            preferredSourceWeek: draft.sourceWeek,
                            initialRoom: draft.initialRoom
                        )
                        activeSheet = nil
                    },
                    onConfirmed: { targetWeek in
                        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                            reschedulePickingCourse = nil
                            selectedWeekOverride = min(max(1, targetWeek), totalWeeks)
                        }
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])
            }
        }
        .task(id: cacheKey) {
            await rebuildVisibleWeekLayouts()
        }
        .task(id: todoCountsKey) {
            await rebuildSessionTodoCounts()
        }
        .onAppear {
            if !didRenderInitialFrame {
                DispatchQueue.main.async {
                    didRenderInitialFrame = true
                }
            }
            consumeRequestedWeekIfNeeded()
            consumePendingRescheduleIfNeeded()
        }
        .onChange(of: router.requestedTimetableWeek) { _, _ in
            consumeRequestedWeekIfNeeded()
        }
        .onChange(of: router.pendingRescheduleCourseId) { _, _ in
            consumePendingRescheduleIfNeeded()
        }
        .onChange(of: router.reselectCount(for: .timetable)) { _, _ in
            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                selectedWeekOverride = clampedCurrentWeek
            }
        }
        .onChange(of: currentSemester?.id) { oldId, newId in
            guard oldId != newId else { return }
            selectedWeekOverride = nil
            isGridDragging = false
            weekCache.invalidate()
            dismissedEmptyBanner = false
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                let currentToday = LocalDate.today()
                if today != currentToday {
                    today = currentToday
                }
            }
        }
        .onChange(of: router.selectedTab) { _, tab in
            if tab == .timetable {
                let currentToday = LocalDate.today()
                if today != currentToday {
                    today = currentToday
                }
            }
        }
    }

    // MARK: - Android 1:1 64pt Left-Aligned Paper Header

    private var timetableHeader: some View {
        HStack(alignment: .center, spacing: 8) {
            if currentSemester != nil {
                HStack(alignment: .center, spacing: 8) {
                    Button {
                        if let sem = currentSemester {
                            pickerNonEmptyWeeks = TimetableLayout.nonEmptyWeeks(
                                snapshot: container.liveSnapshot,
                                semester: sem
                            )
                        } else {
                            pickerNonEmptyWeeks = []
                        }
                        activeSheet = .weekPicker
                    } label: {
                        VStack(alignment: .leading, spacing: 1) {
                            HStack(spacing: 3) {
                                Text("第 \(selectedWeek) 周")
                                    .font(.system(size: 22, weight: .semibold))
                                    .foregroundStyle(theme.ink)
                                    .monospacedDigit()
                                    .lineLimit(1)
                                    .contentTransition(.numericText())
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 12.5, weight: .semibold))
                                    .foregroundStyle(theme.muted)
                            }
                            if !selectedWeekDateRangeText.isEmpty {
                                Text(selectedWeekDateRangeText)
                                    .font(.system(size: 12, weight: .regular))
                                    .foregroundStyle(theme.muted)
                                    .monospacedDigit()
                                    .lineLimit(1)
                            }
                        }
                        .padding(.vertical, 2)
                        .frame(minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("选择周次，当前第 \(selectedWeek) 周 \(selectedWeekDateRangeText)")

                    if selectedWeek != actualCurrentWeek && actualCurrentWeek >= 1 && actualCurrentWeek <= totalWeeks {
                        GokeReturnPill("回本周") {
                            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                selectedWeekOverride = actualCurrentWeek
                            }
                        }
                        .transition(simplifyMotion ? .opacity : .scale(scale: 0.88).combined(with: .opacity))
                    }
                }
                .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: selectedWeek)
            } else {
                Text("课表")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
            }

            Spacer(minLength: 4)

            HStack(spacing: 0) {
                if currentSemester != nil {
                    Button {
                        router.present(.courseEdit())
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundStyle(theme.ink)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("添加课程")

                    Button {
                        activeSheet = .export
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundStyle(theme.ink)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("导出与分享课表")
                }

                if currentSemester != nil || container.isHydrated {
                    Button {
                        router.push(.editTimetable)
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundStyle(theme.ink)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("编辑课表与学期管理")
                }
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .frame(height: 64)
        .background(theme.paper)
    }

    private func flushPendingSheetAction() {
        guard let action = pendingSheetAction else { return }
        pendingSheetAction = nil
        switch action {
        case .openTask(let taskId):
            router.present(.todoEdit(taskId: taskId))
        case .openCourseEdit(let courseId):
            router.push(.courseEdit(courseId: courseId))
        case .startReschedule(let course, let preferredSourceWeek, let initialRoom):
            beginReschedulePicking(
                course: course,
                preferredSourceWeek: preferredSourceWeek,
                initialRoom: initialRoom
            )
        }
    }

    // MARK: - Main Paged Timetable Body

    private func timetableBody(semester: Semester) -> some View {
        let currentWeek = actualCurrentWeek
        let liveVisible = isLiveTimetableVisible
        let liveShouldTick = shouldTickLiveTimetableClock
        let liveInterval = liveClockIntervalSeconds
        let currentToday = today

        return VStack(spacing: 0) {
            if let picking = reschedulePickingCourse {
                reschedulePickingBanner(course: picking)
                    .padding(.horizontal, GokeSpacing.md)
                    .padding(.bottom, GokeSpacing.xs)
                    .transition(simplifyMotion ? .opacity : .move(edge: .top).combined(with: .opacity))
            }

            if !hasSemesterCourses && !dismissedEmptyBanner {
                emptyTimetableBanner
                    .transition(simplifyMotion ? .opacity : .move(edge: .top).combined(with: .opacity))
            }

            let reloadToken = TimetablePagerReloadToken(
                base: cacheBaseKey,
                today: currentToday,
                actualCurrentWeek: currentWeek,
                isLiveCurrentWeekVisible: liveVisible,
                shouldTick: liveShouldTick,
                intervalSeconds: liveInterval,
                showWeekend: showWeekend,
                showTeacher: settingsStore.settings.showTeacher,
                sessionTodoCounts: sessionTodoCounts,
                reschedulePickingCourseId: reschedulePickingCourse?.id
            )
            GokeHorizontalPager(
                selection: selectedWeekBinding,
                bounds: 1...totalWeeks,
                preloadNeighbors: didRenderInitialFrame,
                isScrollEnabled: !isGridDragging,
                reloadToken: reloadToken
            ) { week in
                let layout = layoutForWeek(week, semester: semester)
                if week == currentWeek {
                    TimetableCurrentWeekHost(
                        isVisible: liveVisible,
                        shouldTick: liveShouldTick,
                        intervalSeconds: liveInterval,
                        layout: layout,
                        today: currentToday,
                        actualCurrentWeek: currentWeek,
                        showWeekend: showWeekend,
                        showTeacher: settingsStore.settings.showTeacher,
                        sessionTodoCounts: sessionTodoCounts,
                        reschedulePickingCourse: reschedulePickingCourse,
                        onDayChanged: { nextDay in
                            if today != nextDay {
                                today = nextDay
                            }
                        },
                        onPreviewCourse: { occ in
                            activeSheet = .previewCourse(occ)
                        },
                        onOpenEvent: { eventId in
                            router.present(.eventEdit(eventId: eventId))
                        },
                        onOpenTask: { taskId in
                            router.present(.todoEdit(taskId: taskId))
                        },
                        onTapEmptySlot: { dayOfWeek, period in
                            let maxPeriod = layout.periods.last?.index ?? 12
                            activeSheet = .emptySlot(EmptySlotDraft(
                                week: week,
                                dayOfWeek: dayOfWeek,
                                startPeriod: period,
                                endPeriod: min(period + 1, maxPeriod)
                            ))
                        },
                        onBoxSelectCreate: { dates, startTime, endTime in
                            activeSheet = .boxSelectTodo(BoxSelectTodoDraft(
                                dates: dates,
                                startTime: startTime,
                                endTime: endTime
                            ))
                        },
                        onPickRescheduleSlot: { dayOfWeek, startPeriod, endPeriod in
                            guard let picking = reschedulePickingCourse else { return }
                            activeSheet = .rescheduleConfirm(ReschedulePickDraft(
                                course: picking,
                                sourceWeek: rescheduleSourceWeek,
                                initialRoom: rescheduleInitialRoom,
                                targetWeek: week,
                                targetDayOfWeek: dayOfWeek,
                                targetStartPeriod: startPeriod,
                                targetEndPeriod: endPeriod
                            ))
                        },
                        onUpdateTaskBlock: { task, newStart, newMinutes in
                            updateTaskBlock(task, newStart: newStart, newMinutes: newMinutes)
                        },
                        onGridDragActiveChanged: { isActive in
                            if isGridDragging != isActive {
                                isGridDragging = isActive
                            }
                        }
                    )
                } else {
                    TimetableWeekPageView(
                        layout: layout,
                        now: nil,
                        today: currentToday,
                        actualCurrentWeek: currentWeek,
                        showWeekend: showWeekend,
                        showTeacher: settingsStore.settings.showTeacher,
                        sessionTodoCounts: sessionTodoCounts,
                        reschedulePickingCourse: reschedulePickingCourse,
                        onPreviewCourse: { occ in
                            activeSheet = .previewCourse(occ)
                        },
                        onOpenEvent: { eventId in
                            router.present(.eventEdit(eventId: eventId))
                        },
                        onOpenTask: { taskId in
                            router.present(.todoEdit(taskId: taskId))
                        },
                        onTapEmptySlot: { dayOfWeek, period in
                            let maxPeriod = layout.periods.last?.index ?? 12
                            activeSheet = .emptySlot(EmptySlotDraft(
                                week: week,
                                dayOfWeek: dayOfWeek,
                                startPeriod: period,
                                endPeriod: min(period + 1, maxPeriod)
                            ))
                        },
                        onBoxSelectCreate: { dates, startTime, endTime in
                            activeSheet = .boxSelectTodo(BoxSelectTodoDraft(
                                dates: dates,
                                startTime: startTime,
                                endTime: endTime
                            ))
                        },
                        onPickRescheduleSlot: { dayOfWeek, startPeriod, endPeriod in
                            guard let picking = reschedulePickingCourse else { return }
                            activeSheet = .rescheduleConfirm(ReschedulePickDraft(
                                course: picking,
                                sourceWeek: rescheduleSourceWeek,
                                initialRoom: rescheduleInitialRoom,
                                targetWeek: week,
                                targetDayOfWeek: dayOfWeek,
                                targetStartPeriod: startPeriod,
                                targetEndPeriod: endPeriod
                            ))
                        },
                        onUpdateTaskBlock: { task, newStart, newMinutes in
                            updateTaskBlock(task, newStart: newStart, newMinutes: newMinutes)
                        },
                        onGridDragActiveChanged: { isActive in
                            if isGridDragging != isActive {
                                isGridDragging = isActive
                            }
                        }
                    )
                }
            }
        }
    }

    private var emptyTimetableBanner: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: GokeSpacing.sm) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("本学期还没有课程")
                        .font(GokeTypography.bodyMedium.weight(.semibold))
                        .foregroundStyle(theme.ink)
                    Text("点按空白格添加，或从教务/文件导入")
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                }
                Spacer(minLength: 4)
                Button {
                    withAnimation(GokeMotion.dismiss(reduceMotion: simplifyMotion)) {
                        dismissedEmptyBanner = true
                    }
                } label: {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(theme.muted)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("收起空课表提示")
            }

            HStack(spacing: GokeSpacing.sm) {
                Button {
                    router.push(.jxfw)
                } label: {
                    Label("登录教务", systemImage: "arrow.down.doc")
                        .font(GokeTypography.labelMedium.weight(.semibold))
                        .foregroundStyle(theme.onAccent)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 34)
                        .background(theme.accent, in: Capsule())
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)

                Button {
                    router.push(.importFile())
                } label: {
                    Label("导入文件", systemImage: "doc.badge.plus")
                        .font(GokeTypography.labelMedium.weight(.semibold))
                        .foregroundStyle(theme.onAccentContainer)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 34)
                        .background(theme.accentContainer, in: Capsule())
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private func reschedulePickingBanner(course: Course) -> some View {
        HStack(spacing: GokeSpacing.sm) {
            Image(systemName: "hand.tap")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(theme.accent)

            Text("点按或拖动格子，为「\(course.name)」选新时段")
                .font(.footnote.weight(.medium))
                .foregroundStyle(theme.ink)
                .lineLimit(2)

            Spacer(minLength: GokeSpacing.xs)

            Button {
                withAnimation(GokeMotion.dismiss(reduceMotion: simplifyMotion)) {
                    reschedulePickingCourse = nil
                }
            } label: {
                Text("取消")
                    .font(GokeTypography.labelMedium.weight(.semibold))
                    .foregroundStyle(theme.onAccentContainer)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 32)
                    .background(theme.accentContainer, in: Capsule())
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.mintCompact)
        }
        .padding(.horizontal, GokeSpacing.md)
        .padding(.vertical, GokeSpacing.xs)
        .gokeGlassCard(cornerRadius: GokeRadius.lg, tint: theme.accent)
    }

    private var emptySemesterView: some View {
        GokeEmptyState(
            systemImage: "calendar.day.timeline.left",
            title: "还没有课表",
            subtitle: nil,
            primaryActionTitle: "登录教务",
            onPrimaryAction: {
                router.push(.jxfw)
            },
            secondaryActionTitle: "新建学期",
            onSecondaryAction: {
                router.push(.editTimetable)
            }
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// Calm Mint Paper grid shell rendered ONLY on a first-ever cold launch before SQLite hydration finishes,
    /// avoiding a jarring `"还没有课表"` flash while maintaining identical visual geometry.
    private var hydratingGridShell: some View {
        let days = showWeekend ? ["一", "二", "三", "四", "五", "六", "日"] : ["一", "二", "三", "四", "五"]
        let periods = GdutPeriods.default
        let boundaries = Set(TimetableLayout.groupBoundaries(periodCount: periods.count))
        let rowHeight: CGFloat = 60
        let groupGap: CGFloat = CGFloat(TimetableLayout.defaultGroupGap)

        return VStack(spacing: 0) {
            HStack(spacing: 0) {
                Color.clear
                    .frame(width: 36, height: 46)
                ForEach(days, id: \.self) { label in
                    Text(label)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(theme.muted.opacity(0.65))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
            .background { theme.paper }

            ScrollView(.vertical, showsIndicators: false) {
                HStack(alignment: .top, spacing: 0) {
                    VStack(spacing: 0) {
                        ForEach(periods) { period in
                            VStack(spacing: 1) {
                                Text("\(period.index)")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(theme.muted.opacity(0.55))
                                    .monospacedDigit()
                                Text(period.start.description)
                                    .font(.system(size: 9))
                                    .foregroundStyle(theme.muted.opacity(0.4))
                                    .monospacedDigit()
                                Text(period.end.description)
                                    .font(.system(size: 9))
                                    .foregroundStyle(theme.muted.opacity(0.3))
                                    .monospacedDigit()
                            }
                            .padding(.top, 5)
                            .frame(width: 36, height: rowHeight, alignment: .top)

                            if boundaries.contains(period.index) {
                                Color.clear
                                    .frame(width: 36, height: groupGap)
                            }
                        }
                    }

                    VStack(spacing: 0) {
                        ForEach(periods) { period in
                            Color.clear
                                .frame(maxWidth: .infinity)
                                .frame(height: rowHeight)
                            if boundaries.contains(period.index) {
                                Rectangle()
                                    .fill(theme.outlineVariant.opacity(0.45))
                                    .frame(height: 1)
                                    .frame(maxWidth: .infinity, minHeight: groupGap, maxHeight: groupGap)
                            }
                        }
                    }
                }
            }
            .scrollDisabled(true)
            .background { theme.paper }
        }
        .accessibilityHidden(true)
    }

    // MARK: - Layout Cache (120Hz Paging)

    private func layoutForWeek(_ week: Int, semester: Semester) -> TimetableWeekLayout {
        weekCache.layout(
            for: week,
            semester: semester,
            key: cacheBaseKey,
            liveSnapshot: container.liveSnapshot
        )
    }

    private func rebuildVisibleWeekLayouts() async {
        guard didRenderInitialFrame else { return }
        guard let sem = currentSemester else {
            weekCache.invalidate()
            return
        }
        let key = cacheBaseKey
        let targetWeek = selectedWeek
        let maxWeeks = totalWeeks
        let missing = weekCache.missingWeeks(
            around: targetWeek,
            totalWeeks: maxWeeks,
            semesterId: sem.id,
            key: key
        )
        guard !missing.isEmpty else { return }

        let live = container.liveSnapshot
        let built = await Task.detached(priority: .utility) {
            var results: [Int: TimetableWeekLayout] = [:]
            results.reserveCapacity(missing.count)
            for w in missing {
                if Task.isCancelled { return results }
                if let layout = TimetableLayout.buildWeek(
                    snapshot: live,
                    semester: sem,
                    week: w,
                    defaultBlockMinutes: key.defaultBlockMinutes,
                    includeTimedEvents: true
                ) {
                    results[w] = layout
                }
            }
            return results
        }.value

        guard !Task.isCancelled, cacheBaseKey == key else { return }
        weekCache.merge(built, for: key, around: targetWeek)
    }

    private func rebuildSessionTodoCounts() async {
        guard didRenderInitialFrame else { return }
        guard let sem = currentSemester else {
            if !sessionTodoCounts.isEmpty {
                sessionTodoCounts = [:]
            }
            return
        }
        let live = container.liveSnapshot
        if !live.tasks.contains(where: { $0.courseId != nil && !$0.done }) {
            if !sessionTodoCounts.isEmpty {
                sessionTodoCounts = [:]
            }
            return
        }
        let computed = await Task.detached(priority: .utility) {
            TaskLogic.occurrenceCounts(
                live.tasks,
                semester: sem,
                courses: live.courses,
                exceptions: live.exceptions,
                now: LocalDateTime.now()
            )
        }.value
        guard !Task.isCancelled else { return }
        if sessionTodoCounts != computed {
            sessionTodoCounts = computed
        }
    }

    private func consumeRequestedWeekIfNeeded() {
        if let requested = router.requestedTimetableWeek {
            router.requestedTimetableWeek = nil
            let clamped = min(max(1, requested), totalWeeks)
            selectedWeekOverride = clamped
        }
    }

    private func consumePendingRescheduleIfNeeded() {
        guard let courseId = router.consumePendingRescheduleCourseId(),
              let course = container.liveSnapshot.courses.first(where: { $0.id == courseId })
        else { return }
        beginReschedulePicking(course: course, preferredSourceWeek: nil, initialRoom: course.room)
    }

    private func beginReschedulePicking(course: Course, preferredSourceWeek: Int?, initialRoom: String) {
        let resolvedSourceWeek: Int
        if let preferred = preferredSourceWeek {
            resolvedSourceWeek = preferred
        } else if course.weeks.contains(selectedWeek) {
            resolvedSourceWeek = selectedWeek
        } else if course.weeks.contains(actualCurrentWeek) {
            resolvedSourceWeek = actualCurrentWeek
        } else {
            resolvedSourceWeek = course.weeks.sorted().first ?? selectedWeek
        }
        rescheduleSourceWeek = resolvedSourceWeek
        rescheduleInitialRoom = initialRoom
        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
            reschedulePickingCourse = course
        }
    }

    // MARK: - Course & Task Mutations via ActionDispatcher

    private func cancelSingleWeek(_ occ: CourseOccurrence) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let existingEx = container.liveSnapshot.exceptions.first {
            $0.courseId == occ.course.id && $0.week == occ.originalWeek
        }
        let ex = CourseException(
            id: existingEx?.id ?? UUID().uuidString.lowercased(),
            courseId: occ.course.id,
            week: occ.originalWeek,
            type: .CANCEL,
            updatedAt: nowMillis
        )
        let change: Change = if let existingEx {
            .modify(before: .courseException(existingEx), after: .courseException(ex))
        } else {
            .add(after: .courseException(ex))
        }
        let cs = ChangeSet(
            description: "第 \(occ.originalWeek) 周停课「\(occ.course.name)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [change]
        )
        Task {
            _ = await actions.commit(cs, label: "已停课第 \(occ.originalWeek) 周「\(occ.course.name)」")
        }
    }

    private func restoreOriginalOccurrence(_ occ: CourseOccurrence) {
        guard let existingEx = container.liveSnapshot.exceptions.first(where: {
            $0.courseId == occ.course.id && $0.week == occ.originalWeek
        }) else {
            actions.showToast(message: "未找到调课记录")
            return
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs = ChangeSet(
            description: "恢复第 \(occ.originalWeek) 周「\(occ.course.name)」原安排",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.remove(before: .courseException(existingEx))]
        )
        Task {
            let ok = await actions.commit(cs, label: "已恢复第 \(occ.originalWeek) 周原安排")
            if ok {
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    selectedWeekOverride = min(max(1, occ.originalWeek), totalWeeks)
                }
            }
        }
    }

    private func updateTaskBlock(_ task: GokeTask, newStart: LocalDateTime, newMinutes: Int) {
        let maxDuration = max(180, task.blockMinutes ?? 180)
        let clampedMinutes = max(15, min(maxDuration, newMinutes))
        let endDt = newStart.addingMinutes(clampedMinutes)
        guard task.blockStart != newStart || task.blockMinutes != clampedMinutes || task.due != endDt || !task.dueHasTime else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var updated = task
        updated.blockStart = newStart
        updated.blockMinutes = clampedMinutes
        updated.due = endDt
        updated.dueHasTime = true
        updated.updatedAt = nowMillis
        let endTime = endDt.time.description
        let cs = ChangeSet(
            description: "调整「\(task.title)」至 \(newStart.time.description)–\(endTime)",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.modify(before: .task(task), after: .task(updated))]
        )
        Task {
            _ = await actions.commit(
                cs,
                label: "已调整「\(task.title)」至 \(newStart.time.description)–\(endTime)"
            )
        }
    }
}

// MARK: - Scoped Current-Week Live Clock Host

/// Scopes the 30s/60s live `now` tick exclusively to the active current week page so
/// `TimetableView.body` and the other 19 weeks never rebuild on timer ticks, and pauses
/// completely whenever the tab is inactive, backgrounded, or cosmetic timers are paused.
private struct TimetableCurrentWeekHost: View {
    let isVisible: Bool
    let shouldTick: Bool
    let intervalSeconds: Int
    let layout: TimetableWeekLayout
    let today: LocalDate
    let actualCurrentWeek: Int
    let showWeekend: Bool
    let showTeacher: Bool
    let sessionTodoCounts: [CourseOccurrenceKey: Int]
    let reschedulePickingCourse: Course?
    let onDayChanged: (LocalDate) -> Void
    let onPreviewCourse: (CourseOccurrence) -> Void
    let onOpenEvent: (String) -> Void
    let onOpenTask: (String) -> Void
    let onTapEmptySlot: (Int, Int) -> Void
    let onBoxSelectCreate: ([LocalDate], LocalTime, LocalTime) -> Void
    let onPickRescheduleSlot: (Int, Int, Int) -> Void
    let onUpdateTaskBlock: (GokeTask, LocalDateTime, Int) -> Void
    let onGridDragActiveChanged: (Bool) -> Void

    @State private var currentNow: LocalDateTime = Self.minuteQuantizedNow()

    private static func minuteQuantizedNow() -> LocalDateTime {
        let raw = LocalDateTime.now()
        return LocalDateTime(
            date: raw.date,
            time: LocalTime(hour: raw.time.hour, minute: raw.time.minute, second: 0)
        )
    }

    private var clockKey: TimetableLiveClockKey {
        TimetableLiveClockKey(
            isVisible: isVisible,
            shouldTick: shouldTick,
            intervalSeconds: intervalSeconds
        )
    }

    var body: some View {
        TimetableWeekPageView(
            layout: layout,
            now: currentNow,
            today: currentNow.date,
            actualCurrentWeek: actualCurrentWeek,
            showWeekend: showWeekend,
            showTeacher: showTeacher,
            sessionTodoCounts: sessionTodoCounts,
            reschedulePickingCourse: reschedulePickingCourse,
            onPreviewCourse: onPreviewCourse,
            onOpenEvent: onOpenEvent,
            onOpenTask: onOpenTask,
            onTapEmptySlot: onTapEmptySlot,
            onBoxSelectCreate: onBoxSelectCreate,
            onPickRescheduleSlot: onPickRescheduleSlot,
            onUpdateTaskBlock: onUpdateTaskBlock,
            onGridDragActiveChanged: onGridDragActiveChanged
        )
        .task(id: clockKey) {
            guard clockKey.isVisible else { return }
            let freshNow = Self.minuteQuantizedNow()
            if freshNow != currentNow {
                currentNow = freshNow
            }
            if freshNow.date != today {
                onDayChanged(freshNow.date)
            }
            guard clockKey.shouldTick else { return }
            let step = Double(max(60, clockKey.intervalSeconds))
            while !Task.isCancelled {
                let nowEpoch = Date().timeIntervalSince1970
                let delay = max(1.0, step - nowEpoch.truncatingRemainder(dividingBy: step) + 0.1)
                try? await Task.sleep(for: .seconds(delay))
                guard !Task.isCancelled else { break }
                let ticked = Self.minuteQuantizedNow()
                if ticked != currentNow {
                    currentNow = ticked
                }
                if ticked.date != today {
                    onDayChanged(ticked.date)
                }
            }
        }
    }
}

// MARK: - Single Week Grid Page (120Hz Clean Paper Grid)

private struct TimetableWeekPageView: View {
    let layout: TimetableWeekLayout
    let now: LocalDateTime?
    let today: LocalDate
    let actualCurrentWeek: Int
    let showWeekend: Bool
    let showTeacher: Bool
    let sessionTodoCounts: [CourseOccurrenceKey: Int]
    let reschedulePickingCourse: Course?
    let onPreviewCourse: (CourseOccurrence) -> Void
    let onOpenEvent: (String) -> Void
    let onOpenTask: (String) -> Void
    let onTapEmptySlot: (Int, Int) -> Void
    let onBoxSelectCreate: ([LocalDate], LocalTime, LocalTime) -> Void
    let onPickRescheduleSlot: (Int, Int, Int) -> Void
    let onUpdateTaskBlock: (GokeTask, LocalDateTime, Int) -> Void
    let onGridDragActiveChanged: (Bool) -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var colorScheme

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @ScaledMetric(relativeTo: .body) private var scaledBaseRow: CGFloat = 60
    @GestureState private var isBoxSelectArmed: Bool = false
    @GestureState private var isTaskDragArmed: Bool = false
    @GestureState private var activeTaskDrag: DraggingTaskBlockState? = nil
    @GestureState private var activeBoxSelect: BoxSelectState? = nil
    @State private var stackedClusterSlots: [TimetableSlotFrame]?
    @State private var pendingClusterAction: (() -> Void)?
    @State private var suppressNextTaskTapId: String?
    @State private var suppressNextEmptyTap: Bool = false

    private var isBoxSelectSessionActive: Bool {
        isBoxSelectArmed || activeBoxSelect != nil
    }

    private var isTaskDragSessionActive: Bool {
        isTaskDragArmed || activeTaskDrag != nil
    }

    private var isAnyGridDragActive: Bool {
        isBoxSelectSessionActive || isTaskDragSessionActive
    }

    private let railWidth: CGFloat = 36
    private let groupGap: CGFloat = CGFloat(TimetableLayout.defaultGroupGap)
    private let blockCornerRadius: CGFloat = 12
    private let minLaneWidth: CGFloat = 30
    private let weekdayLabels = ["一", "二", "三", "四", "五", "六", "日"]

    /// Android parity (`BaseRowH = 60.dp * fontScale.coerceIn(1f, 1.35f)`).
    private var rowHeight: CGFloat {
        min(max(60, scaledBaseRow), 81)
    }

    private var activeSnapKey: DragSnapKey? {
        if let box = activeBoxSelect {
            return DragSnapKey(
                dayOfWeek: box.endDayIndex,
                startPeriod: min(box.startPeriod, box.endPeriod),
                endPeriod: max(box.startPeriod, box.endPeriod)
            )
        }
        if let taskDrag = activeTaskDrag {
            return DragSnapKey(
                dayOfWeek: taskDrag.previewStart.date.dayOfWeek,
                startPeriod: taskDrag.previewStart.time.totalMinutes,
                endPeriod: taskDrag.previewMinutes
            )
        }
        return nil
    }

    private var visibleColumns: [TimetableColumn] {
        showWeekend ? layout.columns : layout.columns.filter { $0.dayOfWeek <= 5 }
    }

    private var periodCount: Int {
        max(1, layout.periods.count)
    }

    private var totalGridHeight: CGFloat {
        CGFloat(
            TimetableLayout.totalGridHeight(
                periodCount: periodCount,
                rowHeight: Double(rowHeight),
                groupGap: Double(groupGap)
            )
        )
    }

    private var nowTimeY: CGFloat? {
        guard layout.week == actualCurrentWeek, let now else { return nil }
        guard let y = TimetableLayout.calcNowY(
            periods: layout.periods,
            time: now.time,
            rowHeight: Double(rowHeight),
            groupGap: Double(groupGap)
        ) else { return nil }
        return CGFloat(y)
    }

    private func periodTopY(_ periodIndex: Int) -> CGFloat {
        CGFloat(
            TimetableLayout.periodTop(
                periodIndex: periodIndex,
                periodCount: periodCount,
                rowHeight: Double(rowHeight),
                groupGap: Double(groupGap)
            )
        )
    }

    private func periodIndex(atY y: CGFloat) -> Int {
        guard !layout.periods.isEmpty else { return 1 }
        var best = layout.periods[0].index
        var bestDist = CGFloat.greatestFiniteMagnitude
        for p in layout.periods {
            let centerY = periodTopY(p.index) + rowHeight * 0.5
            let dist = abs(y - centerY)
            if dist < bestDist {
                bestDist = dist
                best = p.index
            }
        }
        return best
    }

    private func periodIndexHit(atY y: CGFloat) -> Int? {
        for p in layout.periods {
            let top = periodTopY(p.index)
            if y >= top && y <= top + rowHeight {
                return p.index
            }
        }
        return nil
    }

    private func slotVerticalBounds(startPeriod: Int, endPeriod: Int) -> (top: CGFloat, height: CGFloat) {
        let clampedStart = min(max(1, startPeriod), periodCount)
        let clampedEnd = min(max(clampedStart, endPeriod), periodCount)
        let top = periodTopY(clampedStart)
        let bottom = periodTopY(clampedEnd) + rowHeight
        return (top, max(rowHeight, bottom - top))
    }

    private func syncedPhase(
        start: LocalDateTime,
        end: LocalDateTime,
        done: Bool,
        cardTop: CGFloat,
        cardHeight: CGFloat
    ) -> TimePhase {
        if done { return .finished }
        if layout.week < actualCurrentWeek { return .finished }
        if layout.week > actualCurrentWeek { return .upcoming }
        guard let now else { return .upcoming }
        let base = CourseProgress.phase(start: start, end: end, now: now)
        if case .ongoing = base, let nowY = nowTimeY, cardHeight > 0 {
            let progress = Double(min(max(0, (nowY - cardTop) / cardHeight), 1))
            return .ongoing(progress: progress)
        }
        return base
    }

    private func phaseForCourse(_ occ: CourseOccurrence, cardTop: CGFloat, cardHeight: CGFloat) -> TimePhase {
        syncedPhase(start: occ.start, end: occ.end, done: false, cardTop: cardTop, cardHeight: cardHeight)
    }

    private func phaseForBlock(
        start: LocalDateTime,
        end: LocalDateTime,
        done: Bool,
        cardTop: CGFloat,
        cardHeight: CGFloat
    ) -> TimePhase {
        syncedPhase(start: start, end: end, done: done, cardTop: cardTop, cardHeight: cardHeight)
    }

    private func stackPeekOffset(depth: Int, maxDepth: Int) -> (dx: CGFloat, dy: CGFloat) {
        if maxDepth <= 1 {
            return (2.5, 2.25)
        }
        return depth == 1 ? (1.5, 1.75) : (2.75, 3.25)
    }

    var body: some View {
        let cols = visibleColumns

        VStack(spacing: 0) {
            weekdayHeader(columns: cols)

            ScrollView(.vertical, showsIndicators: false) {
                GeometryReader { proxy in
                    let dayAreaWidth = max(140, proxy.size.width - railWidth)
                    let colWidth = dayAreaWidth / CGFloat(max(1, cols.count))

                    HStack(alignment: .top, spacing: 0) {
                        periodRail

                        ZStack(alignment: .topLeading) {
                            gridCanvas(columns: cols, colWidth: colWidth)

                            emptyCellTapOverlay(columns: cols, colWidth: colWidth, dayAreaWidth: dayAreaWidth)

                            if let box = activeBoxSelect {
                                boxSelectOverlay(box: box, columns: cols, colWidth: colWidth)
                            }

                            ForEach(Array(cols.enumerated()), id: \.element.id) { colIndex, column in
                                ForEach(column.slots) { slot in
                                    slotView(
                                        slot: slot,
                                        column: column,
                                        colIndex: colIndex,
                                        colWidth: colWidth,
                                        dayAreaWidth: dayAreaWidth,
                                        columns: cols
                                    )
                                }
                            }

                            currentTimeIndicator(columns: cols, colWidth: colWidth, dayAreaWidth: dayAreaWidth)
                        }
                        .frame(width: dayAreaWidth, height: totalGridHeight, alignment: .topLeading)
                        .coordinateSpace(name: "timetableGrid")
                        .gokeDragSnapFeedback(trigger: activeSnapKey)
                    }
                }
                .frame(height: totalGridHeight)
                .padding(.bottom, 168)
            }
            .scrollDisabled(isAnyGridDragActive)
            .background { theme.paper }
            .onChange(of: isBoxSelectSessionActive) { wasActive, isActive in
                if !wasActive && isActive {
                    GokeHaptics.prepareDragSession()
                    GokeHaptics.longPressStarted()
                }
            }
            .onChange(of: isTaskDragSessionActive) { wasActive, isActive in
                if !wasActive && isActive {
                    GokeHaptics.prepareDragSession()
                    GokeHaptics.longPressStarted()
                }
            }
            .onChange(of: isAnyGridDragActive) { _, isActive in
                onGridDragActiveChanged(isActive)
            }
            .onDisappear {
                onGridDragActiveChanged(false)
            }
        }
        .sheet(
            isPresented: Binding(
                get: { stackedClusterSlots != nil },
                set: { if !$0 { stackedClusterSlots = nil } }
            ),
            onDismiss: {
                let action = pendingClusterAction
                pendingClusterAction = nil
                action?()
            }
        ) {
            if let cluster = stackedClusterSlots {
                TimetableStackedClusterSheet(
                    slots: cluster,
                    onSelectCourse: { occ in
                        pendingClusterAction = { onPreviewCourse(occ) }
                        stackedClusterSlots = nil
                    },
                    onSelectEvent: { eventId in
                        pendingClusterAction = { onOpenEvent(eventId) }
                        stackedClusterSlots = nil
                    },
                    onSelectTask: { taskId in
                        pendingClusterAction = { onOpenTask(taskId) }
                        stackedClusterSlots = nil
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])
            }
        }
    }

    // MARK: - Day Header

    private func weekdayHeader(columns: [TimetableColumn]) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .frame(width: railWidth, height: 46)

            ForEach(columns) { col in
                let isToday = col.date == today
                let dayFg = isToday ? theme.onAccentContainer : theme.muted

                VStack(spacing: 1.5) {
                    Text(weekdayLabels[max(0, min(6, col.dayOfWeek - 1))])
                        .font(.system(size: 12, weight: isToday ? .semibold : .medium))
                        .foregroundStyle(dayFg)

                    Text("\(col.date.month)/\(col.date.day)")
                        .font(.system(size: 10, weight: isToday ? .medium : .regular))
                        .monospacedDigit()
                        .foregroundStyle(dayFg)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
                .padding(.vertical, 2)
                .background {
                    if isToday {
                        RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                            .fill(theme.accentContainer)
                    }
                }
                .padding(2)
            }
        }
        .background { theme.paper }
    }

    // MARK: - Period Rail & Clean Paper Grid Canvas

    private var periodRail: some View {
        let boundaries = Set(TimetableLayout.groupBoundaries(periodCount: periodCount))
        return VStack(spacing: 0) {
            ForEach(layout.periods) { period in
                VStack(spacing: 1) {
                    Text("\(period.index)")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(theme.ink)
                        .monospacedDigit()
                    Text(period.start.description)
                        .font(.system(size: 9, weight: .regular))
                        .foregroundStyle(theme.muted)
                        .monospacedDigit()
                    Text(period.end.description)
                        .font(.system(size: 9, weight: .regular))
                        .foregroundStyle(theme.muted.opacity(0.75))
                        .monospacedDigit()
                }
                .padding(.top, 5)
                .frame(width: railWidth, height: rowHeight, alignment: .top)

                if boundaries.contains(period.index) {
                    Color.clear
                        .frame(width: railWidth, height: groupGap)
                }
            }
        }
        .background { theme.paper }
    }

    private func gridCanvas(columns: [TimetableColumn], colWidth: CGFloat) -> some View {
        Canvas { context, size in
            // Android Clean Paper Grid: zero per-period horizontal gridlines;
            // only 1pt outlineVariant dividers centered inside the 14pt morning/afternoon/evening gaps.
            let boundaries = TimetableLayout.groupBoundaries(periodCount: periodCount)
            for boundary in boundaries {
                let y = periodTopY(boundary) + rowHeight + groupGap * 0.5
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(path, with: .color(theme.outlineVariant.opacity(0.55)), lineWidth: 0.5)
            }
        }
        .allowsHitTesting(false)
    }

    // MARK: - Empty Cell Tap & Long-Press Box-Select Overlay

    private func computeBoxSelect(
        drag: DragGesture.Value,
        columns: [TimetableColumn],
        colWidth: CGFloat
    ) -> BoxSelectState? {
        guard !columns.isEmpty, !layout.periods.isEmpty else { return nil }
        let startCol = min(max(0, Int(drag.startLocation.x / max(1, colWidth))), columns.count - 1)
        let currentCol = min(max(0, Int(drag.location.x / max(1, colWidth))), columns.count - 1)
        let startP = periodIndex(atY: drag.startLocation.y)
        let currentP = periodIndex(atY: drag.location.y)
        return BoxSelectState(
            startDayIndex: startCol,
            startPeriod: startP,
            endDayIndex: reschedulePickingCourse != nil ? startCol : currentCol,
            endPeriod: currentP
        )
    }

    private func commitBoxSelect(_ box: BoxSelectState, columns: [TimetableColumn]) {
        let minCol = min(box.startDayIndex, box.endDayIndex)
        let maxCol = max(box.startDayIndex, box.endDayIndex)
        let minP = min(box.startPeriod, box.endPeriod)
        let maxP = max(box.startPeriod, box.endPeriod)
        guard minCol >= 0, maxCol < columns.count else { return }

        if reschedulePickingCourse != nil {
            let dayOfWeek = columns[minCol].dayOfWeek
            onPickRescheduleSlot(dayOfWeek, minP, maxP)
        } else {
            let selectedDates = (minCol...maxCol).map { columns[$0].date }
            let startT = layout.periods.first(where: { $0.index == minP })?.start ?? LocalTime(hour: 8, minute: 30)
            let endT = layout.periods.first(where: { $0.index == maxP })?.end ?? LocalTime(hour: min(23, startT.hour + 1), minute: startT.minute)
            onBoxSelectCreate(selectedDates, startT, endT)
        }
    }

    @ViewBuilder
    private func emptyCellTapOverlay(
        columns: [TimetableColumn],
        colWidth: CGFloat,
        dayAreaWidth: CGFloat
    ) -> some View {
        // Long-press then drag in stationary `"timetableGrid"` coordinates with `@GestureState` cancellation safety.
        // Never attach a bare `DragGesture` over the grid so vertical scrolling and horizontal week swiping stay native.
        let boxSelectGesture = LongPressGesture(minimumDuration: 0.35, maximumDistance: 8)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("timetableGrid")))
            .updating($isBoxSelectArmed) { value, state, _ in
                if case .second(true, _) = value {
                    state = true
                }
            }
            .updating($activeBoxSelect) { value, state, _ in
                guard case .second(true, let drag?) = value else { return }
                state = computeBoxSelect(drag: drag, columns: columns, colWidth: colWidth)
            }
            .onChanged { value in
                if case .second(true, _) = value {
                    suppressNextEmptyTap = true
                }
            }
            .onEnded { value in
                guard case .second(true, let drag?) = value,
                      let box = computeBoxSelect(drag: drag, columns: columns, colWidth: colWidth)
                else {
                    suppressNextEmptyTap = false
                    return
                }
                suppressNextEmptyTap = true
                commitBoxSelect(box, columns: columns)
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(250))
                    suppressNextEmptyTap = false
                }
            }

        Color.clear
            .frame(width: dayAreaWidth, height: totalGridHeight)
            .contentShape(Rectangle())
            .onTapGesture(coordinateSpace: .local) { location in
                if suppressNextEmptyTap {
                    suppressNextEmptyTap = false
                    return
                }
                guard !columns.isEmpty,
                      let periodIdx = periodIndexHit(atY: location.y)
                else { return }
                let colIdx = min(max(0, Int(location.x / max(1, colWidth))), columns.count - 1)
                let col = columns[colIdx]
                if let picking = reschedulePickingCourse {
                    let slot = snappedPickSlot(
                        dayOfWeek: col.dayOfWeek,
                        startPeriod: periodIdx,
                        picking: picking
                    )
                    onPickRescheduleSlot(slot.dayOfWeek, slot.startPeriod, slot.endPeriod)
                } else {
                    onTapEmptySlot(col.dayOfWeek, periodIdx)
                }
            }
            .simultaneousGesture(boxSelectGesture)
    }

    private func boxSelectOverlay(box: BoxSelectState, columns: [TimetableColumn], colWidth: CGFloat) -> some View {
        let minCol = min(box.startDayIndex, box.endDayIndex)
        let maxCol = max(box.startDayIndex, box.endDayIndex)
        let minP = min(box.startPeriod, box.endPeriod)
        let maxP = max(box.startPeriod, box.endPeriod)
        let bounds = slotVerticalBounds(startPeriod: minP, endPeriod: maxP)
        let x = CGFloat(minCol) * colWidth + 2
        let w = max(20, CGFloat(maxCol - minCol + 1) * colWidth - 4)
        let h = max(24, bounds.height - 4)

        return RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous)
            .fill(theme.accent.opacity(0.24))
            .overlay {
                RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous)
                    .stroke(theme.accent, lineWidth: 2)
            }
            .frame(width: w, height: h)
            .offset(x: x, y: bounds.top + 2)
            .allowsHitTesting(false)
    }

    private func snappedPickSlot(dayOfWeek: Int, startPeriod: Int, picking: Course) -> DragSnapKey {
        let span = max(1, picking.endPeriod - picking.startPeriod + 1)
        let minP = layout.periods.first?.index ?? 1
        let maxP = layout.periods.last?.index ?? 12
        let clampedStart = min(max(minP, startPeriod), max(minP, maxP - span + 1))
        let clampedEnd = min(maxP, clampedStart + span - 1)
        return DragSnapKey(dayOfWeek: dayOfWeek, startPeriod: clampedStart, endPeriod: clampedEnd)
    }

    // MARK: - Slot Card + Connected-Cluster Stack Peeks

    private func shouldStackCluster(_ slot: TimetableSlotFrame, colWidth: CGFloat) -> Bool {
        slot.clusterSize > 1 && ((colWidth - 3.0) / CGFloat(max(1, slot.clusterSize))) < minLaneWidth
    }

    @ViewBuilder
    private func slotView(
        slot: TimetableSlotFrame,
        column: TimetableColumn,
        colIndex: Int,
        colWidth: CGFloat,
        dayAreaWidth: CGFloat,
        columns: [TimetableColumn]
    ) -> some View {
        let shouldStack = shouldStackCluster(slot, colWidth: colWidth)
        let flatten3D = performance.shouldFlatten3DStacking || performance.shouldSimplifyMotion
        let isLastColumn = colIndex == columns.count - 1
        let fullCluster: [TimetableSlotFrame] = shouldStack ? column.cluster(for: slot) : [slot]
        let bounds = slotVerticalBounds(startPeriod: slot.startPeriod, endPeriod: slot.endPeriod)

        if shouldStack && !slot.isStackFront {
            if !flatten3D, let depth = slot.stackDepth, depth >= 1 && depth <= 2 {
                // Render each visible back stack layer at its true vertical span, clamped inside dayAreaWidth so Sunday never clips.
                let maxDepth = max(1, slot.clusterMaxDepth)
                let offset = stackPeekOffset(depth: depth, maxDepth: maxDepth)
                let frontShift: CGFloat = isLastColumn ? -2.0 : -1.0
                let width = max(20, colWidth - 3)
                let rawX = CGFloat(colIndex) * colWidth + 1.5 + frontShift + offset.dx
                let baseX = min(rawX, max(0, dayAreaWidth - width - 0.5))
                let baseY = bounds.top + 2.0 + offset.dy
                let height = max(24, min(bounds.height - 4.0, totalGridHeight - baseY))
                let peekBg = stackBackColor(for: slot, depth: depth, cardTop: baseY, cardHeight: height)

                Button {
                    if let picking = reschedulePickingCourse {
                        let snapped = snappedPickSlot(dayOfWeek: slot.dayOfWeek, startPeriod: slot.startPeriod, picking: picking)
                        onPickRescheduleSlot(snapped.dayOfWeek, snapped.startPeriod, snapped.endPeriod)
                    } else {
                        stackedClusterSlots = fullCluster
                    }
                } label: {
                    RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous)
                        .fill(peekBg)
                        .shadow(
                            color: Color.black.opacity(colorScheme == .dark ? 0.24 : 0.08),
                            radius: 1.5,
                            x: 0.5,
                            y: 1
                        )
                        .frame(width: width, height: height)
                }
                .buttonStyle(.mintCompact)
                .offset(x: baseX, y: baseY)
                .zIndex(Double(3 - depth) * 0.25)
            } else {
                // Items beyond 2 visible back layers remain 100% reachable via front card's `+N` cluster sheet.
                EmptyView()
            }
        } else {
            let hiddenCount = max(0, fullCluster.count - 1)
            let laneWidth = shouldStack ? colWidth : (colWidth / CGFloat(max(1, slot.laneCount)))
            let frontShiftX: CGFloat = (shouldStack && !flatten3D) ? (isLastColumn ? -2.0 : -1.0) : 0.0
            let baseX = max(0, CGFloat(colIndex) * colWidth + (shouldStack ? 0 : CGFloat(slot.laneIndex) * laneWidth) + 1.5 + frontShiftX)
            let baseY = bounds.top + 2.0
            let width = max(20, min(laneWidth - 3, dayAreaWidth - baseX - 0.5))
            let height = max(24, bounds.height - 4.0)

            let isBeingDraggedTask: Bool = {
                guard case .taskBlock(let task, _, _) = slot.kind else { return false }
                return activeTaskDrag?.task.id == task.id
            }()
            let dragOffsetY: CGFloat = {
                guard isBeingDraggedTask, let drag = activeTaskDrag else { return 0 }
                switch drag.mode {
                case .move:
                    return min(max(-baseY, drag.rawDeltaY), max(0, totalGridHeight - baseY - height))
                case .resizeTop:
                    return min(height - 24, max(-baseY, drag.rawDeltaY))
                case .resizeBottom:
                    return 0
                }
            }()
            let displayHeight: CGFloat = {
                guard isBeingDraggedTask, let drag = activeTaskDrag else { return height }
                switch drag.mode {
                case .move:
                    return height
                case .resizeTop:
                    let clampedTopDelta = min(height - 24, max(-baseY, drag.rawDeltaY))
                    return max(24, height - clampedTopDelta)
                case .resizeBottom:
                    return max(24, min(totalGridHeight - baseY, height + drag.rawDeltaY))
                }
            }()

            let openStackedCluster: (() -> Void)? = shouldStack && fullCluster.count > 1
                ? { stackedClusterSlots = fullCluster }
                : nil

            ZStack(alignment: .topLeading) {
                switch slot.kind {
                case .course(let occ):
                    courseSlotCard(
                        occ: occ,
                        cardWidth: width,
                        cardTop: baseY,
                        cardHeight: height,
                        stackedOverlapCount: shouldStack ? fullCluster.count : 1,
                        onTapStackedOverride: openStackedCluster
                    )

                case .event(let ev):
                    eventSlotCard(
                        ev: ev,
                        slot: slot,
                        cardTop: baseY,
                        cardHeight: height,
                        onTapStackedOverride: openStackedCluster
                    )

                case .taskBlock(let task, let start, let end):
                    taskBlockCard(
                        task: task,
                        start: start,
                        end: end,
                        slot: slot,
                        baseY: baseY,
                        cardHeight: height,
                        isDragging: isBeingDraggedTask,
                        onTapStackedOverride: openStackedCluster
                    )
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if shouldStack && hiddenCount > 0 {
                    let badgeInk: Color = {
                        switch slot.kind {
                        case .course(let occ):
                            return CourseProgressStyle.foreground(
                                activeFg: theme.courseColor(index: occ.course.colorIndex).foreground,
                                phase: phaseForCourse(occ, cardTop: baseY, cardHeight: height),
                                theme: theme
                            )
                        case .event(let ev):
                            return theme.courseColor(index: ev.event.colorIndex).foreground
                        case .taskBlock(let task, let start, let end):
                            let pair = theme.courseColor(index: slot.listColorIndex ?? 1)
                            return CourseProgressStyle.foreground(
                                activeFg: pair.foreground,
                                phase: phaseForBlock(start: start, end: end, done: task.done, cardTop: baseY, cardHeight: height),
                                theme: theme
                            )
                        }
                    }()
                    Text("+\(hiddenCount)")
                        .font(.system(size: 9.5, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(badgeInk)
                        .padding(.horizontal, 4.5)
                        .padding(.vertical, 1)
                        .background {
                            Capsule().fill(badgeInk.opacity(0.16))
                        }
                        .padding(4)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: width, height: displayHeight, alignment: .topLeading)
            .offset(x: baseX, y: baseY + dragOffsetY)
            .scaleEffect(isBeingDraggedTask && activeTaskDrag?.mode == .move ? 1.02 : 1.0)
            .animation(GokeMotion.interactiveDrag(reduceMotion: simplifyMotion), value: isBeingDraggedTask)
            .zIndex(isBeingDraggedTask ? 20 : (shouldStack ? 2 : 1))
        }
    }

    private func stackBackColor(for slot: TimetableSlotFrame, depth: Int, cardTop: CGFloat, cardHeight: CGFloat) -> Color {
        let colorIndex: Int
        let finished: Bool
        switch slot.kind {
        case .course(let occ):
            colorIndex = occ.course.colorIndex
            finished = phaseForCourse(occ, cardTop: cardTop, cardHeight: cardHeight).isFinished
        case .event(let ev):
            colorIndex = ev.event.colorIndex
            finished = phaseForBlock(start: ev.start, end: ev.end, done: false, cardTop: cardTop, cardHeight: cardHeight).isFinished
        case .taskBlock(let task, let start, let end):
            colorIndex = slot.listColorIndex ?? 1
            finished = phaseForBlock(start: start, end: end, done: task.done, cardTop: cardTop, cardHeight: cardHeight).isFinished
        }
        let pair = theme.courseColor(index: colorIndex)
        let baseBg = finished ? CourseProgressStyle.mutedBackground(activeBg: pair.background, theme: theme) : pair.background
        if colorScheme == .dark {
            return CourseProgressStyle.lerp(baseBg, theme.surface, fraction: min(0.36, 0.16 * Double(depth)))
        } else {
            let target = finished ? theme.muted : pair.foreground
            return CourseProgressStyle.lerp(baseBg, target, fraction: min(0.20, 0.09 * Double(depth)))
        }
    }

    // MARK: - Event Card

    private func eventSlotCard(
        ev: EventOccurrence,
        slot: TimetableSlotFrame,
        cardTop: CGFloat,
        cardHeight: CGFloat,
        onTapStackedOverride: (() -> Void)?
    ) -> some View {
        let pair = theme.courseColor(index: ev.event.colorIndex)
        let phase = phaseForBlock(start: ev.start, end: ev.end, done: false, cardTop: cardTop, cardHeight: cardHeight)
        let inkColor = CourseProgressStyle.foreground(activeFg: pair.foreground, phase: phase, theme: theme)

        return Button {
            if let picking = reschedulePickingCourse {
                let snapped = snappedPickSlot(dayOfWeek: slot.dayOfWeek, startPeriod: slot.startPeriod, picking: picking)
                onPickRescheduleSlot(snapped.dayOfWeek, snapped.startPeriod, snapped.endPeriod)
            } else if let onTapStackedOverride {
                onTapStackedOverride()
            } else {
                onOpenEvent(ev.event.id)
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(ev.event.title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(inkColor)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                if !ev.event.location.isEmpty {
                    Text(ev.event.location)
                        .font(.system(size: 9.5, weight: .medium))
                        .foregroundStyle(inkColor.opacity(0.85))
                        .lineLimit(2)
                }
            }
            .padding(.horizontal, 5)
            .padding(.vertical, 5)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                CourseProgressBackground(activeBg: pair.background, phase: phase, axis: .vertical)
                    .clipShape(RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous))
            }
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - TaskBlock Card (Stationary Grid Coordinates + @GestureState + Real Minute-Per-Pt)

    private func ptPerMinute(for start: LocalDateTime, end: LocalDateTime) -> Double {
        let dur = max(15, Int(start.secondsUntil(end) / 60))
        if let y0 = TimetableLayout.calcNowY(
            periods: layout.periods,
            time: start.time,
            rowHeight: Double(rowHeight),
            groupGap: Double(groupGap)
        ),
            let y1 = TimetableLayout.calcNowY(
                periods: layout.periods,
                time: end.time,
                rowHeight: Double(rowHeight),
                groupGap: Double(groupGap)
            ),
            y1 - y0 >= 8.0
        {
            return (y1 - y0) / Double(dur)
        }
        let durations = layout.periods.map { max(15, $0.end.totalMinutes - $0.start.totalMinutes) }
        let avgPeriodMins = durations.isEmpty ? 45.0 : Double(durations.reduce(0, +)) / Double(durations.count)
        return Double(rowHeight) / avgPeriodMins
    }

    private func computeTaskDragState(
        task: GokeTask,
        start: LocalDateTime,
        minutes: Int,
        baseY: CGFloat,
        cardHeight: CGFloat,
        drag: DragGesture.Value
    ) -> DraggingTaskBlockState {
        let localStartY = drag.startLocation.y - baseY
        let edgeZone = min(14.0, cardHeight * 0.25)
        let mode: TaskBlockDragMode
        if localStartY <= edgeZone {
            mode = .resizeTop
        } else if localStartY >= cardHeight - edgeZone {
            mode = .resizeBottom
        } else {
            mode = .move
        }

        let originEnd = start.addingMinutes(minutes)
        let pxPerMin = max(0.25, ptPerMinute(for: start, end: originEnd))
        let rawDeltaMins = Double(drag.translation.height) / pxPerMin
        let snappedDelta = Int((rawDeltaMins / 15.0).rounded()) * 15

        let originStartMin = start.time.totalMinutes
        let originEndMin = min(24 * 60, originStartMin + minutes)

        let nextStart: LocalDateTime
        let nextMinutes: Int

        let maxResizeMinutes = max(180, minutes)

        switch mode {
        case .move:
            let maxStartMin = max(0, 24 * 60 - minutes)
            let clampedStartMin = min(max(0, originStartMin + snappedDelta), maxStartMin)
            nextStart = LocalDateTime(
                date: start.date,
                time: LocalTime(hour: clampedStartMin / 60, minute: clampedStartMin % 60)
            )
            nextMinutes = minutes

        case .resizeTop:
            let minStartMin = max(0, originEndMin - maxResizeMinutes)
            let maxStartMin = max(minStartMin, originEndMin - 15)
            let clampedStartMin = min(max(minStartMin, originStartMin + snappedDelta), maxStartMin)
            nextStart = LocalDateTime(
                date: start.date,
                time: LocalTime(hour: clampedStartMin / 60, minute: clampedStartMin % 60)
            )
            nextMinutes = max(15, min(maxResizeMinutes, originEndMin - clampedStartMin))

        case .resizeBottom:
            let minEndMin = min(24 * 60, originStartMin + 15)
            let maxEndMin = min(24 * 60, originStartMin + maxResizeMinutes)
            let clampedEndMin = min(max(minEndMin, originEndMin + snappedDelta), maxEndMin)
            nextStart = start
            nextMinutes = max(15, min(maxResizeMinutes, clampedEndMin - originStartMin))
        }

        return DraggingTaskBlockState(
            task: task,
            originStart: start,
            originMinutes: minutes,
            mode: mode,
            previewStart: nextStart,
            previewMinutes: nextMinutes,
            rawDeltaY: drag.translation.height
        )
    }

    @ViewBuilder
    private func taskBlockCard(
        task: GokeTask,
        start: LocalDateTime,
        end: LocalDateTime,
        slot: TimetableSlotFrame,
        baseY: CGFloat,
        cardHeight: CGFloat,
        isDragging: Bool,
        onTapStackedOverride: (() -> Void)?
    ) -> some View {
        let pair = theme.courseColor(index: slot.listColorIndex ?? 1)
        let phase = isDragging ? .upcoming : phaseForBlock(start: start, end: end, done: task.done, cardTop: baseY, cardHeight: cardHeight)
        let inkColor = CourseProgressStyle.foreground(activeFg: pair.foreground, phase: phase, theme: theme)
        let minutes = max(15, Int(start.secondsUntil(end) / 60))
        let displayStart = isDragging ? (activeTaskDrag?.previewStart ?? start) : start
        let displayMinutes = isDragging ? (activeTaskDrag?.previewMinutes ?? minutes) : minutes
        let displayEnd = displayStart.addingMinutes(displayMinutes)
        let allowDrag = reschedulePickingCourse == nil && onTapStackedOverride == nil

        let taskDragGesture = LongPressGesture(minimumDuration: 0.35, maximumDistance: 8)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("timetableGrid")))
            .updating($isTaskDragArmed) { value, state, _ in
                if allowDrag, case .second(true, _) = value {
                    state = true
                }
            }
            .updating($activeTaskDrag) { value, state, _ in
                guard allowDrag, case .second(true, let drag?) = value else { return }
                state = computeTaskDragState(
                    task: task,
                    start: start,
                    minutes: minutes,
                    baseY: baseY,
                    cardHeight: cardHeight,
                    drag: drag
                )
            }
            .onChanged { value in
                guard allowDrag, case .second(true, _) = value else { return }
                if suppressNextTaskTapId != task.id {
                    suppressNextTaskTapId = task.id
                }
            }
            .onEnded { value in
                guard allowDrag, case .second(true, let drag?) = value else {
                    if suppressNextTaskTapId == task.id {
                        suppressNextTaskTapId = nil
                    }
                    return
                }
                suppressNextTaskTapId = task.id
                let finalState = computeTaskDragState(
                    task: task,
                    start: start,
                    minutes: minutes,
                    baseY: baseY,
                    cardHeight: cardHeight,
                    drag: drag
                )
                onUpdateTaskBlock(finalState.task, finalState.previewStart, finalState.previewMinutes)
                let draggedId = task.id
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(250))
                    if suppressNextTaskTapId == draggedId {
                        suppressNextTaskTapId = nil
                    }
                }
            }

        let cardButton = Button {
            if suppressNextTaskTapId == task.id {
                suppressNextTaskTapId = nil
                return
            }
            if let picking = reschedulePickingCourse {
                let snapped = snappedPickSlot(dayOfWeek: slot.dayOfWeek, startPeriod: slot.startPeriod, picking: picking)
                onPickRescheduleSlot(snapped.dayOfWeek, snapped.startPeriod, snapped.endPeriod)
            } else if let onTapStackedOverride {
                onTapStackedOverride()
            } else {
                onOpenTask(task.id)
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                if task.isExam {
                    Text("考")
                        .font(.system(size: 8.5, weight: .bold))
                        .foregroundStyle(inkColor)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 0.5)
                        .background(inkColor.opacity(0.18), in: Capsule())
                }

                Text(task.title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(inkColor)
                    .strikethrough(task.done)
                    .lineLimit(minutes >= 75 ? 3 : 2)
                    .multilineTextAlignment(.leading)

                if minutes >= 75, !task.note.isEmpty {
                    Text(task.note)
                        .font(.system(size: 9.5, weight: .regular))
                        .foregroundStyle(inkColor.opacity(0.82))
                        .lineLimit(1)
                }

                Spacer(minLength: 0)

                Text(
                    isDragging
                        ? "\(displayStart.time.description)–\(displayEnd.time.description)"
                        : displayStart.time.description
                )
                .font(.system(size: 9, weight: isDragging ? .semibold : .medium))
                .foregroundStyle(inkColor.opacity(isDragging ? 1.0 : 0.78))
                .monospacedDigit()
                .lineLimit(1)
            }
            .padding(.horizontal, 5)
            .padding(.vertical, 5)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                CourseProgressBackground(activeBg: pair.background, phase: phase, axis: .vertical)
                    .clipShape(RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous))
                    .overlay {
                        if isDragging {
                            RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous)
                                .stroke(theme.accent, lineWidth: 1.5)
                        }
                    }
                    .shadow(
                        color: (isDragging && !performance.shouldDisableGlassAndBlur) ? Color.black.opacity(0.16) : .clear,
                        radius: isDragging ? 8 : 0,
                        y: 4
                    )
            }
            .overlay(alignment: .top) {
                if isDragging {
                    Capsule()
                        .fill(inkColor.opacity(0.5))
                        .frame(width: 18, height: 3)
                        .padding(.top, 2)
                }
            }
            .overlay(alignment: .bottom) {
                if isDragging {
                    Capsule()
                        .fill(inkColor.opacity(0.5))
                        .frame(width: 18, height: 3)
                        .padding(.bottom, 2)
                }
            }
        }
        .buttonStyle(.mintCompact)

        if allowDrag {
            cardButton.simultaneousGesture(taskDragGesture)
        } else {
            cardButton
        }
    }

    // MARK: - CourseBlock Card (Clean Tap Only, Smart Title/Room Fitting, Android 1:1 Parity)

    private func courseSlotCard(
        occ: CourseOccurrence,
        cardWidth: CGFloat,
        cardTop: CGFloat,
        cardHeight: CGFloat,
        stackedOverlapCount: Int = 1,
        onTapStackedOverride: (() -> Void)? = nil
    ) -> some View {
        let pair = theme.courseColor(index: occ.course.colorIndex)
        let phase = phaseForCourse(occ, cardTop: cardTop, cardHeight: cardHeight)
        let inkColor = CourseProgressStyle.foreground(activeFg: pair.foreground, phase: phase, theme: theme)
        let span = max(1, occ.endPeriod - occ.startPeriod + 1)
        let isWide = cardWidth >= 62
        let hPad: CGFloat = isWide ? 6 : 4.5
        let vPad: CGFloat = (span == 1 && occ.course.isExam) ? 4 : 5
        let availableTextWidth = max(24, cardWidth - hPad * 2)
        let todoCount = sessionTodoCounts[CourseOccurrenceKey(courseId: occ.course.id, originalWeek: occ.originalWeek)] ?? 0
        let examDisplay = occ.course.isExam ? JxfwSyncMapper.splitExamDisplay(occ.course.name) : nil
        let examSeat = occ.course.isExam ? JxfwSyncMapper.extractExamSeat(from: occ.course.note).map { "\($0)号" } : nil

        let titleMaxLines: Int = {
            if examDisplay != nil {
                return span >= 3 ? 4 : (span == 2 ? 3 : 1)
            }
            if span >= 3 { return 4 }
            if span == 2 { return stackedOverlapCount > 1 ? 3 : 4 }
            return stackedOverlapCount > 1 ? 1 : 2
        }()

        let rawTitle = examDisplay?.title ?? occ.course.name
        let fittedTitle = TimetableTextFitter.fitTitle(
            rawTitle,
            maxWidth: availableTextWidth,
            baseSize: 11,
            maxLines: titleMaxLines
        )

        let rawRoomDisplay: String = {
            if let examLoc = examDisplay != nil && isWide
                ? JxfwSyncMapper.formatExamLocation(
                    location: TodayFormatters.smartFoldRoom(occ.room),
                    note: occ.course.note,
                    seatSuffix: "号"
                )
                : nil
            {
                return "@\(examLoc)"
            }
            let base = TimetableTextFitter.normalizeRoom(occ.room)
            if base.isEmpty { return "" }
            return isWide && examDisplay == nil ? "@\(base)" : base
        }()

        let roomMaxLines: Int = {
            if span >= 3 { return 3 }
            if span == 2 { return 2 }
            // On 1-period blocks, allow 2 room lines only when the title fits on 1 line; otherwise 1 line so title never truncates.
            return fittedTitle.estimatedLines <= 1 ? 2 : 1
        }()

        let fittedRoom = TimetableTextFitter.fitRoom(
            rawRoomDisplay,
            maxWidth: availableTextWidth,
            baseSize: isWide ? 10 : 9.5
        )

        return Button {
            if let picking = reschedulePickingCourse {
                let snapped = snappedPickSlot(dayOfWeek: occ.date.dayOfWeek, startPeriod: occ.startPeriod, picking: picking)
                onPickRescheduleSlot(snapped.dayOfWeek, snapped.startPeriod, snapped.endPeriod)
            } else if let onTapStackedOverride {
                onTapStackedOverride()
            } else {
                onPreviewCourse(occ)
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                if let examDisplay {
                    Text(isWide ? examDisplay.badgeFull : examDisplay.badgeShort)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(inkColor)
                        .padding(.horizontal, 4.5)
                        .padding(.vertical, 1)
                        .background {
                            Capsule().fill(inkColor.opacity(0.18))
                        }

                    Text(fittedTitle.text)
                        .font(.system(size: fittedTitle.fontSize, weight: .bold))
                        .tracking(0)
                        .foregroundStyle(inkColor)
                        .lineLimit(titleMaxLines)
                        .minimumScaleFactor(0.84)
                        .multilineTextAlignment(.leading)
                        .layoutPriority(1)

                    if !fittedRoom.text.isEmpty {
                        Text(fittedRoom.text)
                            .font(.system(size: fittedRoom.fontSize, weight: .medium))
                            .tracking(0)
                            .foregroundStyle(inkColor.opacity(0.85))
                            .lineLimit(roomMaxLines)
                            .minimumScaleFactor(0.78)
                    }
                    if !isWide, let examSeat, span >= 2 {
                        Text(examSeat)
                            .font(.system(size: 9.5, weight: .medium))
                            .foregroundStyle(inkColor.opacity(0.75))
                            .lineLimit(1)
                    }
                } else {
                    Text(fittedTitle.text)
                        .font(.system(size: fittedTitle.fontSize, weight: .bold))
                        .tracking(0)
                        .foregroundStyle(inkColor)
                        .lineLimit(titleMaxLines)
                        .minimumScaleFactor(0.84)
                        .multilineTextAlignment(.leading)
                        .layoutPriority(1)

                    if !fittedRoom.text.isEmpty {
                        Text(fittedRoom.text)
                            .font(.system(size: fittedRoom.fontSize, weight: .regular))
                            .tracking(0)
                            .foregroundStyle(inkColor.opacity(0.85))
                            .lineLimit(roomMaxLines)
                            .minimumScaleFactor(0.78)
                    }
                }

                if showTeacher && span >= 2 && !occ.course.teacher.isEmpty {
                    Text(occ.course.teacher)
                        .font(.system(size: 9.5, weight: .regular))
                        .foregroundStyle(inkColor.opacity(0.75))
                        .lineLimit(1)
                }

                if occ.rescheduled {
                    Text("调课")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(inkColor)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, hPad)
            .padding(.vertical, vPad)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                CourseProgressBackground(activeBg: pair.background, phase: phase, axis: .vertical)
                    .clipShape(RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous))
                    .overlay {
                        if stackedOverlapCount > 1 {
                            RoundedRectangle(cornerRadius: blockCornerRadius, style: .continuous)
                                .stroke(inkColor.opacity(0.14), lineWidth: 0.75)
                        }
                    }
            }
            .overlay(alignment: .topTrailing) {
                if todoCount > 0 {
                    CourseStickyNoteTab(
                        count: todoCount,
                        finished: phase.isFinished,
                        simplifyMotion: performance.shouldSimplifyMotion
                    )
                    .offset(x: 2, y: -2)
                }
            }
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - Current Time Line (Android 1.25pt Rounded Hairline, No Heavy Dot)

    @ViewBuilder
    private func currentTimeIndicator(columns: [TimetableColumn], colWidth: CGFloat, dayAreaWidth: CGFloat) -> some View {
        if let colIdx = columns.firstIndex(where: { $0.date == today }),
           let yPos = nowTimeY
        {
            let yPosDouble = Double(yPos)
            let column = columns[colIdx]
            let colStartX = Double(colIdx) * Double(colWidth)
            let colEndX = colStartX + Double(colWidth)
            let outerInset = 3.0
            let flatten3D = performance.shouldFlatten3DStacking || performance.shouldSimplifyMotion
            let isLastColumn = colIdx == columns.count - 1

            let nowBarBlocks: [TimetableNowBarBlock] = column.slots.compactMap { slot in
                let shouldStack = shouldStackCluster(slot, colWidth: colWidth)
                if shouldStack && !slot.isStackFront { return nil }
                let laneWidth = shouldStack ? Double(colWidth) : (Double(colWidth) / Double(max(1, slot.laneCount)))
                let frontShiftX = (shouldStack && !flatten3D) ? (isLastColumn ? -2.0 : -1.0) : 0.0
                let bounds = slotVerticalBounds(startPeriod: slot.startPeriod, endPeriod: slot.endPeriod)
                let left = max(0.0, colStartX + (shouldStack ? 0.0 : Double(slot.laneIndex) * laneWidth) + 1.5 + frontShiftX)
                let top = Double(bounds.top) + 2.0
                let right = left + max(20.0, min(laneWidth - 3.0, Double(dayAreaWidth) - left - 0.5))
                let bottom = top + max(24.0, Double(bounds.height) - 4.0)
                return TimetableNowBarBlock(
                    left: left,
                    top: top,
                    right: right,
                    bottom: bottom,
                    cornerRadius: Double(blockCornerRadius),
                    isStackedFront: shouldStack && slot.isStackFront
                )
            }

            let span = TimetableLayout.calcNowBarSpan(
                defaultStartX: colStartX + outerInset,
                defaultEndX: colEndX - outerInset,
                y: yPosDouble,
                edgeInset: 1.5,
                blocks: nowBarBlocks
            )
            let barWidth = max(4.0, CGFloat(span.endX - span.startX))

            Capsule()
                .fill(theme.accent.opacity(0.52))
                .frame(width: barWidth, height: 1.25)
                .offset(x: CGFloat(span.startX), y: yPos - 0.625)
                .allowsHitTesting(false)
        }
    }
}

// MARK: - Smart Timetable Title & Room Fitter (Android Parity)

@MainActor
private enum TimetableTextFitter {
    struct FittedTitle {
        let text: String
        let fontSize: CGFloat
        let estimatedLines: Int
    }

    struct FittedRoom {
        let text: String
        let fontSize: CGFloat
    }

    private struct TitleFitKey: Hashable {
        let text: String
        let widthBucket: Int
        let fontSizeBucket: Int
        let maxLines: Int
    }

    private struct RoomFitKey: Hashable {
        let text: String
        let widthBucket: Int
        let fontSizeBucket: Int
    }

    private static let maxCacheEntries = 512
    private static var titleCache: [TitleFitKey: FittedTitle] = [:]
    private static var roomCache: [RoomFitKey: FittedRoom] = [:]

    #if canImport(UIKit)
    private struct FontKey: Hashable {
        let sizeHalfPt: Int
        let isBold: Bool
    }

    private static var fontCache: [FontKey: UIFont] = [:]

    private static func cachedFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let key = FontKey(sizeHalfPt: Int((size * 2).rounded()), isBold: weight == .bold)
        if let cached = fontCache[key] {
            return cached
        }
        let created = UIFont.systemFont(ofSize: size, weight: weight)
        fontCache[key] = created
        return created
    }
    #endif

    /// Replaces leading single-char prefix + ASCII space (e.g. `"实 4-407A"` -> `"实\u{202F}4-407A"`)
    /// so the building character never orphans on its own line.
    static func glueLeadingSingleChar(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 3 else { return trimmed }
        let firstIdx = trimmed.startIndex
        let secondIdx = trimmed.index(after: firstIdx)
        if trimmed[firstIdx].isLetter && trimmed[secondIdx] == " " {
            let rest = trimmed[trimmed.index(after: secondIdx)...].drop(while: { $0 == " " })
            if !rest.isEmpty {
                return "\(trimmed[firstIdx])\u{202F}\(rest)"
            }
        }
        return trimmed
    }

    static func normalizeRoom(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let separators = CharacterSet(charactersIn: ",，;；、/")
        let parts = trimmed
            .components(separatedBy: separators)
            .map { glueLeadingSingleChar($0) }
            .filter { !$0.isEmpty }
        if parts.isEmpty { return glueLeadingSingleChar(trimmed) }
        if parts.count == 1 { return parts[0] }
        if parts.count == 2 { return "\(parts[0]) / \(parts[1])" }
        return glueLeadingSingleChar(TodayFormatters.smartFoldRoom(trimmed))
    }

    static func fitRoom(_ text: String, maxWidth: CGFloat, baseSize: CGFloat) -> FittedRoom {
        guard !text.isEmpty, maxWidth > 0 else {
            return FittedRoom(text: glueLeadingSingleChar(text), fontSize: baseSize)
        }
        let key = RoomFitKey(
            text: text,
            widthBucket: Int((maxWidth * 2).rounded()),
            fontSizeBucket: Int((baseSize * 2).rounded())
        )
        if let cached = roomCache[key] {
            return cached
        }
        let glued = glueLeadingSingleChar(text)
        let result: FittedRoom
        #if canImport(UIKit)
        if !glued.isEmpty && !glued.contains(" / ") {
            var matched: FittedRoom?
            for step in stride(from: baseSize, through: max(8.0, baseSize - 1.5), by: -0.5) {
                let w = measureSingleLineWidth(glued, fontSize: step, weight: .regular)
                if w <= maxWidth {
                    matched = FittedRoom(text: glued, fontSize: step)
                    break
                }
            }
            result = matched ?? FittedRoom(text: glued, fontSize: max(8.0, baseSize - 1.5))
        } else {
            result = FittedRoom(text: glued, fontSize: baseSize)
        }
        #else
        result = FittedRoom(text: glued, fontSize: baseSize)
        #endif
        if roomCache.count >= maxCacheEntries {
            roomCache.removeAll(keepingCapacity: true)
        }
        roomCache[key] = result
        return result
    }

    static func fitTitle(_ raw: String, maxWidth: CGFloat, baseSize: CGFloat, maxLines: Int) -> FittedTitle {
        guard !raw.isEmpty, maxWidth > 0 else {
            return FittedTitle(text: glueLeadingSingleChar(raw), fontSize: baseSize, estimatedLines: 1)
        }
        let safeMaxLines = max(1, maxLines)
        let key = TitleFitKey(
            text: raw,
            widthBucket: Int((maxWidth * 2).rounded()),
            fontSizeBucket: Int((baseSize * 2).rounded()),
            maxLines: safeMaxLines
        )
        if let cached = titleCache[key] {
            return cached
        }
        let normalized = glueLeadingSingleChar(raw)
        let result = computeFitTitle(
            normalized,
            maxWidth: maxWidth,
            baseSize: baseSize,
            safeMaxLines: safeMaxLines
        )
        if titleCache.count >= maxCacheEntries {
            titleCache.removeAll(keepingCapacity: true)
        }
        titleCache[key] = result
        return result
    }

    private static func computeFitTitle(
        _ normalized: String,
        maxWidth: CGFloat,
        baseSize: CGFloat,
        safeMaxLines: Int
    ) -> FittedTitle {
        guard !normalized.isEmpty else {
            return FittedTitle(text: normalized, fontSize: baseSize, estimatedLines: 1)
        }
        #if canImport(UIKit)
        // Step 1: Try fitting cleanly on 1 line by stepping down up to 1.5pt (e.g. `劳动教育` on 42pt -> 10pt fits 1 line!).
        for step in stride(from: baseSize, through: max(9.5, baseSize - 1.5), by: -0.5) {
            if measureSingleLineWidth(normalized, fontSize: step, weight: .bold) <= maxWidth {
                return FittedTitle(text: normalized, fontSize: step, estimatedLines: 1)
            }
        }
        // Step 2: Multi-line fitting — try baseSize down to 9.0pt without truncation or 1-char trailing orphan.
        for step in stride(from: baseSize, through: max(9.0, baseSize - 2.0), by: -0.5) {
            let lines = estimateWrappedLineCount(normalized, fontSize: step, weight: .bold, maxWidth: maxWidth)
            if lines <= safeMaxLines {
                // Balance 4-char CJK titles (e.g. `劳动教育` -> `劳动\n教育`) when 3+1 would orphan the last char.
                if lines == 2 && normalized.count == 4 && normalized.allSatisfy({ $0.isLetter }) {
                    let mid = normalized.index(normalized.startIndex, offsetBy: 2)
                    let left = String(normalized[..<mid])
                    let right = String(normalized[mid...])
                    if measureSingleLineWidth(left, fontSize: step, weight: .bold) <= maxWidth,
                       measureSingleLineWidth(right, fontSize: step, weight: .bold) <= maxWidth
                    {
                        return FittedTitle(text: "\(left)\n\(right)", fontSize: step, estimatedLines: 2)
                    }
                }
                return FittedTitle(text: normalized, fontSize: step, estimatedLines: lines)
            }
        }
        let fallbackSize = max(9.0, baseSize - 2.0)
        return FittedTitle(
            text: normalized,
            fontSize: fallbackSize,
            estimatedLines: min(safeMaxLines, estimateWrappedLineCount(normalized, fontSize: fallbackSize, weight: .bold, maxWidth: maxWidth))
        )
        #else
        return FittedTitle(text: normalized, fontSize: baseSize, estimatedLines: normalized.count > 4 ? 2 : 1)
        #endif
    }

    #if canImport(UIKit)
    private static func measureSingleLineWidth(_ text: String, fontSize: CGFloat, weight: UIFont.Weight) -> CGFloat {
        let font = cachedFont(size: fontSize, weight: weight)
        let size = (text as NSString).size(withAttributes: [.font: font])
        return ceil(size.width)
    }

    private static func estimateWrappedLineCount(
        _ text: String,
        fontSize: CGFloat,
        weight: UIFont.Weight,
        maxWidth: CGFloat
    ) -> Int {
        let font = cachedFont(size: fontSize, weight: weight)
        let rect = (text as NSString).boundingRect(
            with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font],
            context: nil
        )
        return max(1, Int(ceil(rect.height / max(1, font.lineHeight))))
    }
    #endif
}

// MARK: - Course Quick-Preview Sheet (Android CourseSheet 1:1 Parity)

private struct CourseQuickPreviewSheet: View {
    let occurrence: CourseOccurrence
    let semester: Semester?
    let onEditCourse: (String) -> Void
    let onCancelWeek: (CourseOccurrence) -> Void
    let onStartReschedule: (CourseOccurrence) -> Void
    let onRestoreOriginal: (CourseOccurrence) -> Void
    let onOpenTask: (String) -> Void

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var isBindingTodos: Bool = false
    @State private var showTodosExpanded: Bool = true
    @State private var bindSearchText: String = ""
    @State private var showQuickAddTodo: Bool = false
    @State private var copiedNoteToast: Bool = false

    private var live: AppSnapshot { container.liveSnapshot }

    private var currentCourse: Course {
        live.courses.first(where: { $0.id == occurrence.course.id }) ?? occurrence.course
    }

    private var pair: CourseColorPair {
        theme.courseColor(index: currentCourse.colorIndex)
    }

    private var occurrenceTodos: [GokeTask] {
        TaskLogic.forOccurrence(
            live.tasks,
            semester: semester,
            courses: live.courses,
            exceptions: live.exceptions,
            occurrence: occurrence,
            now: .now(),
            includeDone: false
        )
        .sorted { lhs, rhs in
            if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
            return lhs.sortOrder < rhs.sortOrder
        }
    }

    private var bindableTasks: [GokeTask] {
        let all = TaskLogic.topLevel(live.tasks).filter { !$0.done && $0.source != .PARCEL }
        let q = bindSearchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let filtered = q.isEmpty
            ? all
            : all.filter { $0.title.lowercased().contains(q) || $0.note.lowercased().contains(q) }
        return filtered.sorted { lhs, rhs in
            let lBound = isBoundToThisOccurrence(lhs)
            let rBound = isBoundToThisOccurrence(rhs)
            if lBound != rBound { return lBound }
            return lhs.sortOrder < rhs.sortOrder
        }
    }

    private var courseNamesById: [String: String] {
        Dictionary(live.courses.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
    }

    private func isBoundToThisOccurrence(_ task: GokeTask) -> Bool {
        TaskLogic.matchesOccurrence(
            task: task,
            occurrence: occurrence,
            semester: semester,
            courses: live.courses,
            exceptions: live.exceptions,
            now: .now()
        )
    }

    var body: some View {
        Group {
            if isBindingTodos {
                bindingTodosView
                    .transition(.opacity)
            } else {
                previewContentView
                    .transition(.opacity)
            }
        }
        .background { theme.paper.ignoresSafeArea() }
        .sheet(isPresented: $showQuickAddTodo) {
            TodoEditView(
                taskId: nil,
                initialTitle: nil,
                initialDate: occurrence.date,
                courseId: currentCourse.id,
                courseWeek: occurrence.originalWeek,
                isSheet: true
            )
        }
    }

    private var previewContentView: some View {
        let examDisplay = currentCourse.isExam ? JxfwSyncMapper.splitExamDisplay(currentCourse.name) : nil
        let periodRangeText = occurrence.startPeriod == occurrence.endPeriod
            ? "第\(occurrence.startPeriod)节"
            : "第\(occurrence.startPeriod)–\(occurrence.endPeriod)节"
        let timeSummary = "\(TodayFormatters.weekdayLabel(occurrence.date.dayOfWeek)) \(occurrence.start.time.description)–\(occurrence.end.time.description) · \(periodRangeText)"
        let maxW = semester?.totalWeeks ?? CourseNotes.defaultMaxWeeks
        let primaryNotes = CourseNotes.parse(currentCourse.note, week: occurrence.originalWeek, maxWeeks: maxW)
        let parsedNotes: CourseNotes.Parsed = {
            if !primaryNotes.focusedNotes.isEmpty || occurrence.week == occurrence.originalWeek {
                return primaryNotes
            }
            return CourseNotes.parse(currentCourse.note, week: occurrence.week, maxWeeks: maxW)
        }()
        let weeksSummary = WeekSets.format(currentCourse.weeks)

        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Header: 4pt x 22pt botanical foreground accent bar + title + exam badge + occurrence time (zero navbar bloat)
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .center, spacing: 10) {
                        RoundedRectangle(cornerRadius: 2, style: .continuous)
                            .fill(pair.foreground)
                            .frame(width: 4, height: 22)

                        if let examDisplay {
                            Text(examDisplay.badgeFull)
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(pair.foreground)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(pair.background, in: Capsule())
                        }

                        Text(examDisplay?.title ?? currentCourse.name)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(theme.ink)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundStyle(theme.muted.opacity(0.85))
                                .frame(width: 44, height: 44)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.mintCompact)
                        .padding(.trailing, -10)
                        .padding(.vertical, -8)
                        .accessibilityLabel("关闭")
                    }

                    Text("第 \(occurrence.week) 周 · \(timeSummary)")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                        .monospacedDigit()

                    if occurrence.rescheduled {
                        let origSpan = currentCourse.startPeriod == currentCourse.endPeriod
                            ? "第\(currentCourse.startPeriod)节"
                            : "第\(currentCourse.startPeriod)–\(currentCourse.endPeriod)节"
                        Text("原安排：第 \(occurrence.originalWeek) 周 \(TodayFormatters.weekdayLabel(currentCourse.dayOfWeek)) \(origSpan)")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(theme.accent)
                            .monospacedDigit()
                    }
                }

                // Tappable Attributes Card -> jumps to CourseEditView
                if !occurrence.room.isEmpty || !currentCourse.teacher.isEmpty || !weeksSummary.isEmpty {
                    Button {
                        onEditCourse(currentCourse.id)
                    } label: {
                        VStack(spacing: 10) {
                            if !occurrence.room.isEmpty {
                                attributeRow(icon: "mappin.and.ellipse", label: "教室", value: occurrence.room)
                            }
                            if !currentCourse.teacher.isEmpty {
                                attributeRow(icon: "person", label: "教师", value: currentCourse.teacher)
                            }
                            if !weeksSummary.isEmpty {
                                attributeRow(icon: "calendar", label: "周次", value: "第 \(weeksSummary) 周")
                            }
                        }
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                .fill(theme.surface)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                    }
                    .buttonStyle(.mintRow)
                }

                // Occurrence-Scoped Course Todos Section
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 4) {
                        Text("本节待办")
                            .font(GokeTypography.bodyMedium.weight(.semibold))
                            .foregroundStyle(theme.ink)

                        if !occurrenceTodos.isEmpty {
                            Text("\(occurrenceTodos.count)")
                                .font(GokeTypography.labelSmall.weight(.bold))
                                .monospacedDigit()
                                .foregroundStyle(theme.stickyFg)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(theme.stickyBg, in: Capsule())
                        }

                        Spacer(minLength: 4)

                        if !occurrenceTodos.isEmpty {
                            Button {
                                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                    showTodosExpanded.toggle()
                                }
                            } label: {
                                Image(systemName: showTodosExpanded ? "eye.slash" : "eye")
                                    .font(.subheadline)
                                    .foregroundStyle(theme.muted)
                                    .frame(minWidth: 44, minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                            .accessibilityLabel(showTodosExpanded ? "折叠待办" : "展开待办")
                        }

                        Button {
                            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                isBindingTodos = true
                            }
                        } label: {
                            Label("关联", systemImage: "link")
                                .font(GokeTypography.labelMedium.weight(.semibold))
                                .foregroundStyle(theme.onAccentContainer)
                                .padding(.horizontal, 10)
                                .frame(minHeight: 30)
                                .background(theme.accentContainer, in: Capsule())
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)

                        Button {
                            showQuickAddTodo = true
                        } label: {
                            Image(systemName: "plus")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(theme.onAccent)
                                .frame(width: 28, height: 28)
                                .background(theme.accent, in: Circle())
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("为本节课新建待办")
                    }

                    if occurrenceTodos.isEmpty {
                        Text("还没有待办")
                            .font(.caption)
                            .foregroundStyle(theme.muted)
                            .padding(.bottom, 4)
                    } else if showTodosExpanded {
                        VStack(spacing: 8) {
                            ForEach(occurrenceTodos) { task in
                                HStack(spacing: 10) {
                                    GokeCheckBox(isChecked: task.done, priority: task.priority) {
                                        Task { _ = await actions.completeTask(id: task.id) }
                                    }
                                    Button {
                                        onOpenTask(task.id)
                                    } label: {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(task.title)
                                                .font(GokeTypography.bodyMedium.weight(.medium))
                                                .foregroundStyle(theme.ink)
                                                .lineLimit(2)
                                                .multilineTextAlignment(.leading)
                                            if task.dueBeforeNextClass {
                                                Text("下节课前截止")
                                                    .font(GokeTypography.labelSmall)
                                                    .foregroundStyle(theme.accent)
                                            }
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.mintRow)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                        .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 8)
                .padding(.bottom, 12)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                        .fill(theme.surface)
                )

                // Occurrence-Scoped Course Notes
                if !parsedNotes.focusedNotes.isEmpty {
                    let noteText = parsedNotes.focusedNotes.joined(separator: "\n")
                    Button {
                        #if canImport(UIKit)
                        UIPasteboard.general.string = noteText
                        #endif
                        copiedNoteToast = true
                        actions.showToast(message: "已复制课程备注")
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Label("备注", systemImage: "note.text")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Text(copiedNoteToast ? "已复制" : "复制")
                                    .font(.caption2)
                                    .foregroundStyle(theme.accent)
                            }
                            Text(noteText)
                                .font(.footnote)
                                .foregroundStyle(theme.ink)
                                .multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                .fill(theme.surface)
                        )
                    }
                    .buttonStyle(.mintRow)
                }

                // One-Tap Action Row matching Android hierarchy: 编辑 prominent, 调课 tonal accent, 本周停课 quiet neutral tonal
                VStack(spacing: 10) {
                    HStack(spacing: 10) {
                        actionPill(title: "编辑", icon: "pencil", tint: theme.onAccent, bg: theme.accent) {
                            onEditCourse(currentCourse.id)
                        }
                        actionPill(title: "调课", icon: "arrow.left.arrow.right", tint: theme.onAccentContainer, bg: theme.accentContainer) {
                            onStartReschedule(occurrence)
                        }
                        actionPill(title: "本周停课", icon: "calendar.badge.minus", tint: theme.muted, bg: theme.surfaceHigh) {
                            onCancelWeek(occurrence)
                        }
                    }

                    if occurrence.rescheduled {
                        Button {
                            onRestoreOriginal(occurrence)
                        } label: {
                            Label("恢复第 \(occurrence.originalWeek) 周原安排", systemImage: "arrow.uturn.backward")
                                .font(GokeTypography.bodyMedium.weight(.semibold))
                                .monospacedDigit()
                                .foregroundStyle(theme.onAccentContainer)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(
                                    RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                        .fill(theme.accentContainer)
                                )
                                .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                        }
                        .buttonStyle(.mintCompact)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 24)
        }
    }

    private var bindingTodosView: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button {
                    withAnimation(GokeMotion.dismiss(reduceMotion: simplifyMotion)) {
                        isBindingTodos = false
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.subheadline.weight(.semibold))
                        Text("返回")
                            .font(.subheadline.weight(.medium))
                    }
                    .foregroundStyle(theme.accent)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)

                Spacer()

                Text("关联已有待办")
                    .font(GokeTypography.titleMedium)
                    .foregroundStyle(theme.ink)

                Spacer()

                Button {
                    dismiss()
                } label: {
                    Text("完成")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(theme.accent)
                        .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 6)

            if TaskLogic.topLevel(live.tasks).filter({ !$0.done && $0.source != .PARCEL }).count > 5 {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(theme.muted)
                    TextField("搜索待办标题…", text: $bindSearchText)
                        .textFieldStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }

            if bindableTasks.isEmpty {
                GokeEmptyState(
                    systemImage: "checklist",
                    title: "暂无可关联待办",
                    subtitle: nil
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(bindableTasks) { task in
                            let bound = isBoundToThisOccurrence(task)
                            let otherCourseHint: String? = {
                                guard let cid = task.courseId, !bound else { return nil }
                                let cname = courseNamesById[cid] ?? "其他课程"
                                if let cw = task.courseWeek {
                                    return "已关联：\(cname) 第\(cw)周"
                                }
                                return "已关联：\(cname)"
                            }()

                            HStack(spacing: 12) {
                                GokeCheckBox(isChecked: bound, priority: task.priority) {
                                    toggleBind(task: task, bind: !bound)
                                }
                                Button {
                                    toggleBind(task: task, bind: !bound)
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(task.title)
                                            .font(GokeTypography.bodyLarge)
                                            .foregroundStyle(theme.ink)
                                            .lineLimit(2)
                                            .multilineTextAlignment(.leading)
                                        if let otherCourseHint {
                                            Text(otherCourseHint)
                                                .font(.caption)
                                                .monospacedDigit()
                                                .foregroundStyle(theme.muted)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
            }
        }
    }

    private func toggleBind(task: GokeTask, bind: Bool) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var updated: GokeTask
        if bind {
            let autoBeforeNext = task.due == nil && task.blockStart == nil
            updated = task.withCourseLink(
                courseId: currentCourse.id,
                courseWeek: occurrence.originalWeek,
                dueBeforeNextClass: task.dueBeforeNextClass || autoBeforeNext
            )
        } else {
            updated = task.withCourseLink(courseId: nil, courseWeek: nil, dueBeforeNextClass: false)
        }
        updated.updatedAt = nowMillis
        let desc = bind
            ? "关联待办「\(task.title)」到第 \(occurrence.originalWeek) 周「\(currentCourse.name)」"
            : "取消关联待办「\(task.title)」"
        let cs = ChangeSet(
            description: desc,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.modify(before: .task(task), after: .task(updated))]
        )
        Task {
            _ = await actions.commit(cs, label: bind ? "已关联到本节课" : "已取消关联")
        }
    }

    private func attributeRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(theme.muted)
                .frame(width: 20)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(theme.ink)
                .multilineTextAlignment(.trailing)
        }
    }

    private func actionPill(
        title: String,
        icon: String,
        tint: Color,
        bg: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                Text(title)
                    .font(GokeTypography.labelMedium.weight(.semibold))
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(bg, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
        }
        .buttonStyle(.mintCompact)
    }
}

// MARK: - Box-Select Timed Task Creator Sheet

private struct BoxSelectTimedTaskSheet: View {
    let draft: BoxSelectTodoDraft

    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var title: String = ""
    @State private var note: String = ""

    private var durationMinutes: Int {
        max(15, min(720, (draft.endTime.totalSeconds - draft.startTime.totalSeconds) / 60))
    }

    private var displayEndTime: LocalTime {
        LocalDateTime(date: draft.dates.first ?? .today(), time: draft.startTime)
            .addingMinutes(durationMinutes)
            .time
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.md) {
                    VStack(spacing: GokeSpacing.sm) {
                        TextField("待办标题", text: $title)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))

                        TextField("备注 / 地点（可选）", text: $note)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                    }
                    .padding(GokeSpacing.md)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))

                    VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                        Text("时间")
                            .font(GokeTypography.labelLarge)
                            .foregroundStyle(theme.muted)
                            .padding(.horizontal, 4)

                        VStack(spacing: 12) {
                            HStack {
                                Text("日期")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                if draft.dates.count == 1, let d = draft.dates.first {
                                    Text("\(d.month)月\(d.day)日 \(TodayFormatters.weekdayLabel(d.dayOfWeek))")
                                        .font(GokeTypography.bodyMedium.weight(.medium))
                                        .monospacedDigit()
                                        .foregroundStyle(theme.ink)
                                } else if let first = draft.dates.first, let last = draft.dates.last {
                                    Text("\(first.month)/\(first.day)–\(last.month)/\(last.day)（共 \(draft.dates.count) 天）")
                                        .font(GokeTypography.bodyMedium.weight(.medium))
                                        .monospacedDigit()
                                        .foregroundStyle(theme.ink)
                                }
                            }

                            HStack {
                                Text("时间")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Text("\(draft.startTime.description)–\(displayEndTime.description)（\(durationMinutes) 分钟）")
                                    .font(GokeTypography.bodyMedium.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundStyle(theme.ink)
                            }
                        }
                        .padding(GokeSpacing.md)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background { theme.paper.ignoresSafeArea() }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("新建待办")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        createTimedTasks()
                    }
                    .fontWeight(.semibold)
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func createTimedTasks() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return }
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        for date in draft.dates {
            let startDt = LocalDateTime(date: date, time: draft.startTime)
            let task = GokeTask(
                title: trimmedTitle,
                note: trimmedNote,
                blockStart: startDt,
                blockMinutes: durationMinutes,
                updatedAt: nowMillis
            )
            changes.append(.add(after: .task(task)))
        }
        let label = draft.dates.count > 1
            ? "已创建 \(draft.dates.count) 个时间块「\(trimmedTitle)」"
            : "已创建「\(trimmedTitle)」"
        let cs = ChangeSet(
            description: label,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok { dismiss() }
        }
    }
}

// MARK: - Week Picker Sheet (Empty-Week Dimming + M/d Start Dates)

private struct TimetableWeekPickerSheet: View {
    @Binding var selectedWeek: Int
    let currentWeek: Int
    let totalWeeks: Int
    let semester: Semester?
    let nonEmptyWeeks: Set<Int>

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    private var semesterSubtitle: String {
        let name = semester?.name ?? "当前学期"
        return "\(name) · 共 \(totalWeeks) 周"
    }

    private func weekStartLabel(_ week: Int) -> String {
        guard let sem = semester else { return "" }
        let start = SemesterCalendar(semester: sem).weekStart(week)
        return "\(start.month)/\(start.day)"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.md) {
                    Text(semesterSubtitle)
                        .font(GokeTypography.bodyMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)

                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(1...max(1, totalWeeks), id: \.self) { week in
                            let isSelected = week == selectedWeek
                            let isCurrent = week == currentWeek
                            let isEmpty = !nonEmptyWeeks.isEmpty && !nonEmptyWeeks.contains(week)
                            let dimAlpha: Double = (isEmpty && !isSelected && !isCurrent) ? 0.56 : 1.0

                            Button {
                                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                    selectedWeek = week
                                }
                                dismiss()
                            } label: {
                                VStack(spacing: 3) {
                                    Text("\(week)")
                                        .font(GokeTypography.titleMedium.weight(isSelected || isCurrent ? .semibold : .medium))
                                        .monospacedDigit()
                                    Text(isCurrent ? "本周" : weekStartLabel(week))
                                        .font(GokeTypography.labelSmall.weight(isCurrent ? .semibold : .regular))
                                        .monospacedDigit()
                                }
                                .foregroundStyle(
                                    isSelected
                                        ? theme.onAccent
                                        : (isCurrent ? theme.onAccentContainer : theme.ink)
                                )
                                .frame(maxWidth: .infinity)
                                .frame(height: 54)
                                .background(
                                    RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                        .fill(
                                            isSelected
                                                ? theme.accent
                                                : (isCurrent ? theme.accentContainer : theme.surface)
                                        )
                                )
                                .contentShape(RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                                .opacity(dimAlpha)
                            }
                            .buttonStyle(.mintCompact)
                        }
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background { theme.paper.ignoresSafeArea() }
            .navigationTitle("切换周次")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
                if selectedWeek != currentWeek && currentWeek >= 1 && currentWeek <= totalWeeks {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("回本周") {
                            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                selectedWeek = currentWeek
                            }
                            dismiss()
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
        }
    }
}

// MARK: - Cross-Week Reschedule Confirmation Sheet (with GokeConflictCard + Re-Pick + Restore)

private struct ReschedulePickConfirmSheet: View {
    let draft: ReschedulePickDraft
    let semester: Semester?
    let onPickOnGrid: () -> Void
    let onConfirmed: (Int) -> Void

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var sourceWeek: Int = 1
    @State private var newRoom: String = ""
    @State private var didInit: Bool = false

    private var selectableSourceWeeks: [Int] {
        let sorted = draft.course.weeks.sorted()
        if !sorted.isEmpty {
            return sorted
        }
        return Array(1...max(1, semester?.totalWeeks ?? 20))
    }

    private var sameSlotAsOriginal: Bool {
        draft.targetWeek == sourceWeek
            && draft.targetDayOfWeek == draft.course.dayOfWeek
            && draft.targetStartPeriod == draft.course.startPeriod
            && draft.targetEndPeriod == draft.course.endPeriod
    }

    private var clashes: [CourseOccurrence] {
        guard let sem = semester else { return [] }
        let live = container.liveSnapshot
        return CourseExpander
            .occurrencesInWeek(semester: sem, courses: live.courses, exceptions: live.exceptions, week: draft.targetWeek)
            .filter { other in
                other.date.dayOfWeek == draft.targetDayOfWeek
                    && other.startPeriod <= draft.targetEndPeriod
                    && other.endPeriod >= draft.targetStartPeriod
                    && !(other.course.id == draft.course.id && other.originalWeek == sourceWeek)
                    && !(draft.course.isExam && !other.course.isExam && JxfwSyncMapper.isSameCourseForExam(draft.course.name, other.course.name))
            }
    }

    private var targetDateText: String {
        guard let sem = semester else { return "" }
        let d = SemesterCalendar(semester: sem).dateOf(week: draft.targetWeek, dayOfWeek: draft.targetDayOfWeek)
        return "\(d.month)/\(d.day)"
    }

    private var targetClockText: String {
        guard let sem = semester else { return "" }
        let s = sem.periods.first(where: { $0.index == draft.targetStartPeriod })?.start.description
        let e = sem.periods.first(where: { $0.index == draft.targetEndPeriod })?.end.description
        if let s, let e { return "\(s)–\(e)" }
        return ""
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Clickable target summary card -> re-pick on grid
                    Button(action: onPickOnGrid) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("第 \(draft.targetWeek) 周 · \(TodayFormatters.weekdayLabel(draft.targetDayOfWeek)) \(targetDateText)")
                                    .font(.headline)
                                    .foregroundStyle(theme.accent)
                                    .monospacedDigit()
                                Spacer()
                                Label("重选格", systemImage: "square.grid.2x2")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(theme.accent)
                            }

                            let spanText = draft.targetStartPeriod == draft.targetEndPeriod
                                ? "第\(draft.targetStartPeriod)节"
                                : "第\(draft.targetStartPeriod)–\(draft.targetEndPeriod)节"
                            Text(targetClockText.isEmpty ? spanText : "\(spanText) · \(targetClockText)")
                                .font(.subheadline)
                                .foregroundStyle(theme.ink)
                                .monospacedDigit()

                            if !sameSlotAsOriginal {
                                let origSpan = draft.course.startPeriod == draft.course.endPeriod
                                    ? "第\(draft.course.startPeriod)节"
                                    : "第\(draft.course.startPeriod)–\(draft.course.endPeriod)节"
                                Text("原安排：第 \(sourceWeek) 周 \(TodayFormatters.weekdayLabel(draft.course.dayOfWeek)) \(origSpan)")
                                    .font(.caption)
                                    .foregroundStyle(theme.muted)
                                    .monospacedDigit()
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                    }
                    .buttonStyle(.mintRow)

                    // Source week picker & room field
                    VStack(spacing: 12) {
                        HStack {
                            Text("原周次")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Picker("原周次", selection: $sourceWeek) {
                                ForEach(selectableSourceWeeks, id: \.self) { w in
                                    Text("第 \(w) 周").tag(w)
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(theme.accent)
                        }
                        .frame(minHeight: 36)

                        Rectangle()
                            .fill(theme.outlineVariant.opacity(0.45))
                            .frame(height: 0.5)

                        HStack {
                            Text("教室")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            TextField("留空沿用原教室", text: $newRoom)
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.ink)
                                .multilineTextAlignment(.trailing)
                        }
                        .frame(minHeight: 36)
                    }
                    .padding(14)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))

                    // Live Clash Detection Card (Wave 1 GokeConflictCard)
                    if !clashes.isEmpty {
                        GokeConflictCard(title: "与已有课程时间冲突") {
                            ForEach(clashes.prefix(4)) { other in
                                let otherSpan = other.startPeriod == other.endPeriod
                                    ? "第\(other.startPeriod)节"
                                    : "第\(other.startPeriod)–\(other.endPeriod)节"
                                Text("\(other.course.name)（\(otherSpan)）")
                                    .font(.subheadline)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.ink)
                            }
                        }
                    }

                    Button {
                        commitReschedule()
                    } label: {
                        Text(sameSlotAsOriginal ? "恢复原安排" : "确认调课")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(theme.onAccent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(theme.accent, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                    }
                    .buttonStyle(.mintCompact)
                }
                .padding(20)
            }
            .background { theme.paper.ignoresSafeArea() }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("调课 · \(draft.course.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .onAppear {
                guard !didInit else { return }
                didInit = true
                sourceWeek = draft.sourceWeek
                newRoom = draft.initialRoom.isEmpty ? draft.course.room : draft.initialRoom
            }
        }
    }

    private func commitReschedule() {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let existing = container.liveSnapshot.exceptions.first {
            $0.courseId == draft.course.id && $0.week == sourceWeek
        }
        let trimmedRoom = newRoom.trimmingCharacters(in: .whitespacesAndNewlines)
        let roomUnchanged = trimmedRoom.isEmpty || trimmedRoom == draft.course.room

        // Smart same-as-original handling matching Android ReschedulePanel
        if sameSlotAsOriginal && roomUnchanged {
            if let existing {
                let cs = ChangeSet(
                    description: "恢复第 \(sourceWeek) 周「\(draft.course.name)」原安排",
                    source: .MANUAL,
                    createdAt: nowMillis,
                    changes: [.remove(before: .courseException(existing))]
                )
                Task {
                    let ok = await actions.commit(cs, label: "已恢复原安排")
                    if ok {
                        onConfirmed(sourceWeek)
                        dismiss()
                    }
                }
            } else {
                actions.showToast(message: "与原安排相同，未作修改")
                onConfirmed(sourceWeek)
                dismiss()
            }
            return
        }

        let ex = CourseException(
            id: existing?.id ?? UUID().uuidString.lowercased(),
            courseId: draft.course.id,
            week: sourceWeek,
            type: .RESCHEDULE,
            newWeek: draft.targetWeek,
            newDayOfWeek: draft.targetDayOfWeek,
            newStartPeriod: draft.targetStartPeriod,
            newEndPeriod: draft.targetEndPeriod,
            newRoom: trimmedRoom.isEmpty || trimmedRoom == draft.course.room ? nil : trimmedRoom,
            updatedAt: nowMillis
        )
        let change: Change = if let existing {
            .modify(before: .courseException(existing), after: .courseException(ex))
        } else {
            .add(after: .courseException(ex))
        }
        let summary = "第 \(draft.targetWeek) 周 \(TodayFormatters.weekdayLabel(draft.targetDayOfWeek)) 第\(draft.targetStartPeriod)–\(draft.targetEndPeriod)节"
        let cs = ChangeSet(
            description: "跨周调课「\(draft.course.name)」至\(summary)",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [change]
        )
        Task {
            let ok = await actions.commit(cs, label: "已调至\(summary)")
            if ok {
                onConfirmed(draft.targetWeek)
                dismiss()
            }
        }
    }
}

// MARK: - Sticky-Note Course Todo Badge (Wave 1 Theme Tokens)

private struct CourseStickyNoteTab: View {
    let count: Int
    let finished: Bool
    let simplifyMotion: Bool

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        HStack(spacing: 1.5) {
            Image(systemName: "note.text")
                .font(.system(size: 8, weight: .bold))
            Text("\(count)")
                .font(.system(size: 9, weight: .bold))
                .monospacedDigit()
        }
        .foregroundStyle(theme.stickyFg)
        .padding(.horizontal, 4)
        .padding(.vertical, 1.5)
        .background {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .fill(theme.stickyBg)
                .overlay {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(theme.stickyBorder, lineWidth: 0.5)
                }
        }
        .opacity(finished ? 0.78 : 1.0)
        .rotationEffect(.degrees(simplifyMotion ? 0 : -4))
        .accessibilityLabel("本节课关联 \(count) 项待办")
        .allowsHitTesting(false)
    }
}

// MARK: - Stacked Conflict Cluster Sheet (Android SlotSheet 1:1 Parity)

private struct TimetableStackedClusterSheet: View {
    let slots: [TimetableSlotFrame]
    let onSelectCourse: (CourseOccurrence) -> Void
    let onSelectEvent: (String) -> Void
    let onSelectTask: (String) -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private var sortedSlots: [TimetableSlotFrame] {
        slots.sorted { lhs, rhs in
            if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
            return lhs.laneIndex < rhs.laneIndex
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(sortedSlots) { slot in
                        switch slot.kind {
                        case .course(let occ):
                            let pair = theme.courseColor(index: occ.course.colorIndex)
                            let examDisplay = occ.course.isExam ? JxfwSyncMapper.splitExamDisplay(occ.course.name) : nil
                            let kindLabel = occ.course.isExam ? "考试" : "课程"
                            Button {
                                onSelectCourse(occ)
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                                        .fill(pair.foreground)
                                        .frame(width: 4, height: 32)
                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack(spacing: 6) {
                                            if let examDisplay {
                                                Text(examDisplay.badgeShort)
                                                    .font(.caption2.weight(.bold))
                                                    .foregroundStyle(pair.foreground)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(pair.background, in: Capsule())
                                            }
                                            Text(examDisplay?.title ?? occ.course.name)
                                                .font(GokeTypography.bodyLarge.weight(.semibold))
                                                .foregroundStyle(theme.ink)
                                        }
                                        Text("\(occ.start.time.description)–\(occ.end.time.description) · \(kindLabel)\(occ.room.isEmpty ? "" : " · \(occ.room)")")
                                            .font(.caption)
                                            .foregroundStyle(theme.muted)
                                            .monospacedDigit()
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                                .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                            }
                            .buttonStyle(.mintRow)

                        case .event(let ev):
                            let pair = theme.courseColor(index: ev.event.colorIndex)
                            Button {
                                onSelectEvent(ev.event.id)
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                                        .fill(pair.foreground)
                                        .frame(width: 4, height: 32)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(ev.event.title)
                                            .font(GokeTypography.bodyLarge.weight(.semibold))
                                            .foregroundStyle(theme.ink)
                                        Text("\(ev.start.time.description)–\(ev.end.time.description) · 日程\(ev.event.location.isEmpty ? "" : " · \(ev.event.location)")")
                                            .font(.caption)
                                            .foregroundStyle(theme.muted)
                                            .monospacedDigit()
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                                .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                            }
                            .buttonStyle(.mintRow)

                        case .taskBlock(let task, let start, let end):
                            let pair = theme.courseColor(index: slot.listColorIndex ?? 1)
                            Button {
                                onSelectTask(task.id)
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                                        .fill(pair.foreground)
                                        .frame(width: 4, height: 32)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(task.title)
                                            .font(GokeTypography.bodyLarge.weight(.semibold))
                                            .foregroundStyle(theme.ink)
                                            .strikethrough(task.done)
                                        Text("\(start.time.description)–\(end.time.description) · 待办")
                                            .font(.caption)
                                            .foregroundStyle(theme.muted)
                                            .monospacedDigit()
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                                .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                            }
                            .buttonStyle(.mintRow)
                        }
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background { theme.paper.ignoresSafeArea() }
            .navigationTitle("同时段安排（\(slots.count) 项）")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .gokePaperSheet(theme, detents: [.medium, .large])
    }
}
