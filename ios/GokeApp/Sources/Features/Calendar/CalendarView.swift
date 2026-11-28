import GokeCore
import GokeShared
import SwiftUI

// MARK: - Calendar Display Models

private enum CalendarDisplayMode: String, Hashable, Sendable {
    case month
    case week
}

private struct CalendarPreviewDate: Identifiable, Equatable, Sendable {
    let date: LocalDate
    var id: String { date.description }
}

private struct CalendarCellData: Identifiable, Equatable, Sendable {
    let date: LocalDate
    let isCurrentMonth: Bool
    let isToday: Bool
    let marks: DayMarks

    var id: LocalDate { date }
}

private struct CalendarGridData: Equatable, Sendable {
    let mode: CalendarDisplayMode
    let anchorDate: LocalDate
    let today: LocalDate
    let weekStartsMonday: Bool
    let rowCount: Int
    let cells: [CalendarCellData]
    let agendaByDate: [LocalDate: [AgendaItem]]

    var headerTitle: String {
        switch mode {
        case .month:
            return "\(anchorDate.year)年\(anchorDate.month)月"
        case .week:
            guard let first = cells.first?.date, let last = cells.last?.date else {
                return "\(anchorDate.year)年\(anchorDate.month)月"
            }
            if first.month == last.month {
                return "\(first.month)月\(first.day)日 – \(last.day)日"
            }
            return "\(first.month)月\(first.day)日 – \(last.month)月\(last.day)日"
        }
    }

    static func build(
        mode: CalendarDisplayMode,
        anchorDate: LocalDate,
        today: LocalDate,
        weekStartsMonday: Bool,
        defaultBlockMinutes: Int,
        liveSnapshot: AppSnapshot
    ) -> CalendarGridData {
        let startDate: LocalDate
        let totalCells: Int
        let rowCount: Int

        switch mode {
        case .month:
            let firstOfMonth = LocalDate(year: anchorDate.year, month: anchorDate.month, day: 1)
            let daysInMonth = firstOfMonth.lengthOfMonth
            let leadingDays: Int = weekStartsMonday
                ? (firstOfMonth.dayOfWeek - 1)
                : (firstOfMonth.dayOfWeek % 7)
            let totalNeeded = leadingDays + daysInMonth
            // Dynamic 4 / 5 / 6 rows (fixes 4-row February when Monday-first)
            rowCount = max(4, (totalNeeded + 6) / 7)
            totalCells = rowCount * 7
            startDate = firstOfMonth.addingDays(-leadingDays)

        case .week:
            let offset: Int = weekStartsMonday
                ? (anchorDate.dayOfWeek - 1)
                : (anchorDate.dayOfWeek % 7)
            startDate = anchorDate.addingDays(-offset)
            rowCount = 1
            totalCells = 7
        }

        let endDate = startDate.addingDays(totalCells - 1)
        let agendaMap = AgendaEngine.range(
            snapshot: liveSnapshot,
            from: startDate,
            to: endDate,
            defaultBlockMinutes: defaultBlockMinutes
        )
        let marksMap = AgendaEngine.marks(from: agendaMap)

        var cells: [CalendarCellData] = []
        cells.reserveCapacity(totalCells)
        for index in 0..<totalCells {
            let d = startDate.addingDays(index)
            let inPeriod = mode == .week || (d.year == anchorDate.year && d.month == anchorDate.month)
            let marks = marksMap[d] ?? DayMarks(courses: 0, events: 0, tasks: 0, doneTasks: 0)
            cells.append(
                CalendarCellData(
                    date: d,
                    isCurrentMonth: inPeriod,
                    isToday: d == today,
                    marks: marks
                )
            )
        }

        return CalendarGridData(
            mode: mode,
            anchorDate: anchorDate,
            today: today,
            weekStartsMonday: weekStartsMonday,
            rowCount: rowCount,
            cells: cells,
            agendaByDate: agendaMap
        )
    }
}

private struct CalendarGridBaseKey: Equatable {
    let mode: CalendarDisplayMode
    let pageAnchorBase: LocalDate
    let today: LocalDate
    let weekStartsMonday: Bool
    let defaultBlockMinutes: Int
    let snapshotRevision: UInt64
}

private struct CalendarGridWindowKey: Equatable {
    let base: CalendarGridBaseKey
    let pageOffset: Int
}

private struct CalendarPagerReloadToken: Equatable {
    let base: CalendarGridBaseKey
    let selectedDate: LocalDate
    let simplifyMotion: Bool
}

@MainActor
private final class CalendarGridCacheBox {
    private var baseKey: CalendarGridBaseKey?
    private var grids: [Int: CalendarGridData] = [:]

    func clear() {
        baseKey = nil
        grids.removeAll(keepingCapacity: true)
    }

    func grid(
        for offset: Int,
        key: CalendarGridBaseKey,
        anchorDate: LocalDate,
        liveSnapshot: AppSnapshot
    ) -> CalendarGridData {
        if baseKey != key {
            baseKey = key
            grids.removeAll(keepingCapacity: true)
        }
        if let cached = grids[offset] {
            return cached
        }
        let built = CalendarGridData.build(
            mode: key.mode,
            anchorDate: anchorDate,
            today: key.today,
            weekStartsMonday: key.weekStartsMonday,
            defaultBlockMinutes: key.defaultBlockMinutes,
            liveSnapshot: liveSnapshot
        )
        grids[offset] = built
        return built
    }

    func prewarm(
        offsets: [Int],
        key: CalendarGridBaseKey,
        anchorForOffset: (Int) -> LocalDate,
        liveSnapshot: AppSnapshot
    ) {
        if baseKey != key {
            baseKey = key
            grids.removeAll(keepingCapacity: true)
        }
        for offset in offsets where grids[offset] == nil {
            grids[offset] = CalendarGridData.build(
                mode: key.mode,
                anchorDate: anchorForOffset(offset),
                today: key.today,
                weekStartsMonday: key.weekStartsMonday,
                defaultBlockMinutes: key.defaultBlockMinutes,
                liveSnapshot: liveSnapshot
            )
        }
        let keep = Set(offsets)
        if grids.count > keep.count + 4 {
            grids = grids.filter { keep.contains($0.key) }
        }
    }
}

private enum CalendarPendingDismissAction {
    case pushCourse(String)
    case openEvent(Event)
    case presentSheet(GokeSheet)
}

private enum CalendarActiveSheet: Identifiable {
    case datePicker
    case dayPreview(CalendarPreviewDate)
    case eventDetail(Event)

    var id: String {
        switch self {
        case .datePicker: "datePicker"
        case .dayPreview(let wrapper): "dayPreview:\(wrapper.id)"
        case .eventDetail(let event): "eventDetail:\(event.id)"
        }
    }
}

// MARK: - CalendarView (Android CalendarScreen.kt 1:1 Parity)

/// Unified paper-surface Calendar screen matching Android `CalendarScreen.kt`.
/// Features a 64pt paper header with date picker dialog & `GokeSegmentedPill` (`月 / 周`),
/// a 24pt rounded `theme.surface` grid card with UIKit-backed `GokeHorizontalPager` month/week paging,
/// dynamic 4/5/6 rows, `1.5pt` today ring, 3-shape Canvas indicators, long-press day preview sheet,
/// live minute ticker, and unified botanical pastel `GokeAgendaRow` blocks + frameless paper `GokeTaskRow`s.
struct CalendarView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    @State private var mode: CalendarDisplayMode = .month
    @State private var selectedDate: LocalDate = .today()
    @State private var pageAnchorBase: LocalDate = .today()
    @State private var pageOffset: Int = 0
    @State private var minuteBucket: Int64 = Int64(Date().timeIntervalSince1970 / 60)
    @State private var today: LocalDate = .today()
    @State private var gridCache = CalendarGridCacheBox()
    @State private var activeSheet: CalendarActiveSheet?
    @State private var pendingDismissAction: CalendarPendingDismissAction?

    init() {}

    private var weekStartsMonday: Bool { settingsStore.settings.weekStartsMonday }
    private var simplifyMotion: Bool { reduceMotion || performance.shouldSimplifyMotion }
    private var isMinuteTickerActive: Bool {
        scenePhase == .active && router.selectedTab == .calendar
    }

    private var isShowingDayPreview: Bool {
        if case .dayPreview = activeSheet { return true }
        return false
    }

    private var gridBaseKey: CalendarGridBaseKey {
        CalendarGridBaseKey(
            mode: mode,
            pageAnchorBase: pageAnchorBase,
            today: today,
            weekStartsMonday: weekStartsMonday,
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes,
            snapshotRevision: container.snapshotRevision
        )
    }

    private var gridWindowKey: CalendarGridWindowKey {
        CalendarGridWindowKey(base: gridBaseKey, pageOffset: pageOffset)
    }

    private func weekStart(of date: LocalDate) -> LocalDate {
        let offset = weekStartsMonday ? (date.dayOfWeek - 1) : (date.dayOfWeek % 7)
        return date.addingDays(-offset)
    }

    private func anchorDate(forPageOffset offset: Int, mode: CalendarDisplayMode) -> LocalDate {
        switch mode {
        case .month:
            return LocalDate(year: pageAnchorBase.year, month: pageAnchorBase.month, day: 1)
                .addingMonths(offset)
        case .week:
            return weekStart(of: pageAnchorBase).addingDays(offset * 7)
        }
    }

    private func pageOffset(for date: LocalDate, mode: CalendarDisplayMode) -> Int {
        switch mode {
        case .month:
            return (date.year - pageAnchorBase.year) * 12 + (date.month - pageAnchorBase.month)
        case .week:
            return weekStart(of: pageAnchorBase).daysUntil(weekStart(of: date)) / 7
        }
    }

    private func resolvedGrid(for offset: Int) -> CalendarGridData {
        gridCache.grid(
            for: offset,
            key: gridBaseKey,
            anchorDate: anchorDate(forPageOffset: offset, mode: mode),
            liveSnapshot: container.liveSnapshot
        )
    }

    private var weekdayHeaders: [String] {
        weekStartsMonday
            ? ["一", "二", "三", "四", "五", "六", "日"]
            : ["日", "一", "二", "三", "四", "五", "六"]
    }

    var body: some View {
        let _ = minuteBucket
        let live = container.liveSnapshot
        let grid = resolvedGrid(for: pageOffset)
        let now = LocalDateTime.now()
        let dayItems = grid.agendaByDate[selectedDate] ?? AgendaEngine.forDay(
            snapshot: live,
            date: selectedDate,
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes
        )
        let listColors = Dictionary(uniqueKeysWithValues: live.lists.map { ($0.id, $0.colorIndex) })

        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                calendarHeader(title: grid.headerTitle)
                    .padding(.horizontal, GokeSpacing.lg)

                ScrollView {
                    VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                        calendarGridCard(activeGrid: grid)
                        dayAgendaSection(
                            date: selectedDate,
                            items: dayItems,
                            now: now,
                            listColors: listColors
                        )
                    }
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.top, GokeSpacing.xs)
                    .padding(.bottom, 104)
                }
            }
            .background(theme.paper.ignoresSafeArea())

            ZStack {
                if !isShowingDayPreview {
                    GokeMintFab {
                        router.presentQuickAdd(anchorDate: selectedDate)
                    }
                    .padding(.trailing, GokeSpacing.lg)
                    .padding(.bottom, GokeSpacing.lg)
                    .transition(simplifyMotion ? .opacity : .scale(scale: 0.85).combined(with: .opacity))
                }
            }
            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: !isShowingDayPreview)
        }
        .toolbar(.hidden, for: .navigationBar)
        .task(id: gridWindowKey) {
            recomputeGridWindow()
        }
        .task(id: isMinuteTickerActive) {
            guard isMinuteTickerActive else { return }
            let nowDate = LocalDate.today()
            if today != nowDate {
                today = nowDate
            }
            let currentBucket = Int64(Date().timeIntervalSince1970 / 60)
            if minuteBucket != currentBucket {
                minuteBucket = currentBucket
            }
            await runMinuteTicker()
        }
        .onAppear {
            consumeRequestedDateIfNeeded()
        }
        .onChange(of: router.requestedCalendarDate) { _, _ in
            consumeRequestedDateIfNeeded()
        }
        .onChange(of: router.reselectCount(for: .calendar)) { _, _ in
            goto(today)
        }
        .onChange(of: mode) { _, _ in
            var tx = Transaction()
            tx.disablesAnimations = true
            withTransaction(tx) {
                pageAnchorBase = selectedDate
                pageOffset = 0
                gridCache.clear()
            }
        }
        .onChange(of: pageOffset) { _, newOffset in
            syncSelectedDateForPage(offset: newOffset)
        }
        .sheet(
            item: $activeSheet,
            onDismiss: {
                guard let pending = pendingDismissAction else { return }
                pendingDismissAction = nil
                switch pending {
                case .pushCourse(let courseId):
                    router.pushCourseDetail(id: courseId)
                case .openEvent(let event):
                    activeSheet = .eventDetail(event)
                case .presentSheet(let sheet):
                    router.present(sheet)
                }
            }
        ) { sheet in
            switch sheet {
            case .datePicker:
                CalendarDatePickerSheet(
                    initialDate: selectedDate,
                    onSelect: { picked in
                        activeSheet = nil
                        goto(picked)
                    },
                    onCancel: {
                        activeSheet = nil
                    }
                )
                .gokePaperSheet(theme, detents: [.height(460), .large])

            case .dayPreview(let wrapper):
                let entries = grid.agendaByDate[wrapper.date] ?? AgendaEngine.forDay(
                    snapshot: live,
                    date: wrapper.date,
                    defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes
                )
                CalendarDayPreviewSheet(
                    date: wrapper.date,
                    entries: entries,
                    now: now,
                    showTeacher: settingsStore.settings.showTeacher,
                    listColors: listColors,
                    onOpenCourse: { courseId in
                        pendingDismissAction = .pushCourse(courseId)
                        activeSheet = nil
                    },
                    onOpenEvent: { eventId in
                        if let event = live.events.first(where: { $0.id == eventId }) {
                            pendingDismissAction = .openEvent(event)
                        }
                        activeSheet = nil
                    },
                    onOpenTask: { taskId in
                        pendingDismissAction = .presentSheet(.todoEdit(taskId: taskId))
                        activeSheet = nil
                    },
                    onToggleTask: { task, done in
                        toggleTask(task, done: done, now: now)
                    },
                    onCreateNew: {
                        let target = wrapper.date
                        pendingDismissAction = .presentSheet(.todoEdit(initialDate: target))
                        activeSheet = nil
                    },
                    onViewDay: {
                        let target = wrapper.date
                        activeSheet = nil
                        goto(target)
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])

            case .eventDetail(let event):
                GokeEventDetailSheet(
                    event: event,
                    snapshot: container.snapshot,
                    onDismiss: { activeSheet = nil },
                    onOpenConvertedTask: { taskId in
                        pendingDismissAction = .presentSheet(.todoEdit(taskId: taskId))
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])
            }
        }
    }

    // MARK: - 64pt Paper Header

    private func calendarHeader(title: String) -> some View {
        HStack(alignment: .center, spacing: GokeSpacing.sm) {
            Button {
                activeSheet = .datePicker
            } label: {
                HStack(spacing: 4) {
                    Text(title)
                        .font(GokeTypography.titleLarge)
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .contentTransition(simplifyMotion ? .identity : .numericText())
                        .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: title)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            GokeSegmentedPill(
                options: [(.month, "月"), (.week, "周")],
                selection: $mode,
                height: 36
            )
            .frame(width: 112)
        }
        .frame(height: 64)
    }

    // MARK: - 24pt Rounded White Calendar Grid Card

    private func calendarGridCard(activeGrid: CalendarGridData) -> some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
        let gridHeight = CGFloat(activeGrid.rowCount) * 48 + CGFloat(max(0, activeGrid.rowCount - 1)) * 2

        return VStack(spacing: 4) {
            HStack(spacing: 0) {
                ForEach(weekdayHeaders, id: \.self) { label in
                    Text(label)
                        .font(GokeTypography.labelSmall)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                }
            }

            GokeHorizontalPager(
                selection: $pageOffset,
                bounds: nil,
                preloadNeighbors: true,
                isScrollEnabled: true,
                reloadToken: CalendarPagerReloadToken(
                    base: gridBaseKey,
                    selectedDate: selectedDate,
                    simplifyMotion: simplifyMotion
                )
            ) { offset in
                let pageGrid = resolvedGrid(for: offset)
                LazyVGrid(columns: columns, spacing: 2) {
                    ForEach(pageGrid.cells) { cell in
                        CalendarDayCellView(
                            cell: cell,
                            isSelected: cell.date == selectedDate,
                            simplifyMotion: simplifyMotion,
                            onTap: { goto(cell.date) },
                            onLongPress: {
                                GokeHaptics.longPressStarted()
                                activeSheet = .dayPreview(CalendarPreviewDate(date: cell.date))
                            }
                        )
                        .equatable()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .frame(height: gridHeight)
            .clipped()
            .animation(GokeMotion.standard(reduceMotion: simplifyMotion), value: gridHeight)
        }
        .padding(.horizontal, GokeSpacing.sm)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.surface)
        )
        .clipped()
    }

    // MARK: - Unified Selected-Day Agenda Section

    private func dayAgendaSection(
        date: LocalDate,
        items: [AgendaItem],
        now: LocalDateTime,
        listColors: [String: Int]
    ) -> some View {
        let counts = Self.dayCounts(from: items)
        let titleText = "\(date.month)月\(date.day)日 \(TodayFormatters.weekdayLabel(date.dayOfWeek))"
        var detailParts: [String] = []
        if counts.scheduled > 0 { detailParts.append("\(counts.scheduled) 项日程") }
        if counts.pending > 0 { detailParts.append("\(counts.pending) 项待办") }
        if counts.done > 0 { detailParts.append("\(counts.done) 项已完成") }

        return VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            HStack(alignment: .center, spacing: GokeSpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(titleText)
                        .font(GokeTypography.titleMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)
                    if !detailParts.isEmpty {
                        Text(detailParts.joined(separator: " · "))
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                ZStack {
                    if date != today {
                        GokeReturnPill("回今天") {
                            goto(today)
                        }
                        .transition(simplifyMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.92)))
                    }
                }
                .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: date == today)
            }
            .padding(.top, GokeSpacing.xs)

            if items.isEmpty {
                GokeEmptyState(
                    systemImage: "tray",
                    title: "这一天没有安排"
                )
                .frame(maxWidth: .infinity)
            } else {
                ForEach(items) { item in
                    GokeAgendaRow(
                        item: item,
                        now: now,
                        showTeacher: settingsStore.settings.showTeacher,
                        listColors: listColors,
                        onOpenCourse: { courseId in
                            router.pushCourseDetail(id: courseId)
                        },
                        onOpenEvent: { eventId in
                            if let event = container.liveSnapshot.events.first(where: { $0.id == eventId }) {
                                activeSheet = .eventDetail(event)
                            }
                        },
                        onOpenTask: { taskId in
                            router.present(.todoEdit(taskId: taskId))
                        },
                        onToggleTask: { task, done in
                            toggleTask(task, done: done, now: now)
                        }
                    )
                }
            }
        }
    }

    // MARK: - Navigation & Helpers

    private func consumeRequestedDateIfNeeded() {
        guard let requested = router.requestedCalendarDate else { return }
        router.requestedCalendarDate = nil
        goto(requested)
    }

    private func goto(_ date: LocalDate) {
        let targetOffset = pageOffset(for: date, mode: mode)
        let samePage = targetOffset == pageOffset
        let anim = samePage
            ? GokeMotion.press(reduceMotion: simplifyMotion)
            : GokeMotion.snappy(reduceMotion: simplifyMotion)
        withAnimation(anim) {
            selectedDate = date
            pageOffset = targetOffset
        }
    }

    private func syncSelectedDateForPage(offset: Int) {
        let pageAnchor = anchorDate(forPageOffset: offset, mode: mode)
        switch mode {
        case .month:
            if selectedDate.year != pageAnchor.year || selectedDate.month != pageAnchor.month {
                let clampedDay = min(selectedDate.day, pageAnchor.lengthOfMonth)
                selectedDate = LocalDate(year: pageAnchor.year, month: pageAnchor.month, day: clampedDay)
            }
        case .week:
            let nextWeekStart = weekStart(of: pageAnchor)
            let nextWeekEnd = nextWeekStart.addingDays(6)
            if selectedDate < nextWeekStart || selectedDate > nextWeekEnd {
                let dayIndex = weekStart(of: selectedDate).daysUntil(selectedDate)
                let clampedIndex = min(6, max(0, dayIndex))
                selectedDate = nextWeekStart.addingDays(clampedIndex)
            }
        }
    }

    private func recomputeGridWindow() {
        let key = gridBaseKey
        let targetOffsets = [pageOffset - 2, pageOffset - 1, pageOffset, pageOffset + 1, pageOffset + 2]
        gridCache.prewarm(
            offsets: targetOffsets,
            key: key,
            anchorForOffset: { anchorDate(forPageOffset: $0, mode: key.mode) },
            liveSnapshot: container.liveSnapshot
        )
    }

    private func toggleTask(_ task: GokeTask, done: Bool, now: LocalDateTime) {
        let snap = container.snapshot
        let blockMins = settingsStore.settings.defaultBlockMinutes
        Task {
            await GokeTaskToggle.toggle(
                task: task,
                done: done,
                snapshot: snap,
                now: now,
                defaultBlockMinutes: blockMins,
                actions: actions
            )
        }
    }

    private func runMinuteTicker() async {
        while !Task.isCancelled {
            let nowEpoch = Date().timeIntervalSince1970
            let nextMinuteDelay = max(1.0, 60.0 - nowEpoch.truncatingRemainder(dividingBy: 60.0) + 0.1)
            try? await Task.sleep(for: .seconds(nextMinuteDelay))
            guard !Task.isCancelled else { break }
            let nowDate = LocalDate.today()
            if today != nowDate {
                today = nowDate
            }
            minuteBucket = Int64(Date().timeIntervalSince1970 / 60)
        }
    }

    static func dayCounts(from items: [AgendaItem]) -> (scheduled: Int, pending: Int, done: Int) {
        var scheduled = 0
        var pendingIds = Set<String>()
        var doneIds = Set<String>()
        for item in items {
            switch item {
            case .course, .event:
                scheduled += 1
            case .task(let t, _, _):
                if t.done { doneIds.insert(t.id) } else { pendingIds.insert(t.id) }
            case .block(let t, _, _):
                if t.done { doneIds.insert(t.id) } else { pendingIds.insert(t.id) }
            }
        }
        return (scheduled, pendingIds.count, doneIds.count)
    }
}

// MARK: - Day Cell & 3-Shape Canvas Indicators (Android CalendarScreen.kt:802-890, 1110-1147 Parity)

private struct CalendarDayCellView: View, Equatable {
    let cell: CalendarCellData
    let isSelected: Bool
    let simplifyMotion: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void

    @Environment(\.gokeTheme) private var theme
    @State private var didTriggerLongPress: Bool = false

    nonisolated static func == (lhs: CalendarDayCellView, rhs: CalendarDayCellView) -> Bool {
        lhs.cell == rhs.cell
            && lhs.isSelected == rhs.isSelected
            && lhs.simplifyMotion == rhs.simplifyMotion
    }

    private var hasMarks: Bool {
        cell.marks.scheduled > 0 || cell.marks.tasks > 0 || cell.marks.doneTasks > 0
    }

    var body: some View {
        let fgColor: Color = {
            if isSelected { return theme.onAccent }
            if cell.isToday { return theme.accent }
            if !cell.isCurrentMonth { return theme.muted.opacity(0.45) }
            return theme.ink
        }()
        let markAlpha: Double = cell.isCurrentMonth ? 1.0 : 0.45

        Button {
            if didTriggerLongPress {
                didTriggerLongPress = false
                return
            }
            onTap()
        } label: {
            VStack(spacing: 2) {
                ZStack {
                    if isSelected {
                        Circle()
                            .fill(theme.accent)
                            .frame(width: 30, height: 30)
                            .transition(simplifyMotion ? .opacity : .scale(scale: 0.85).combined(with: .opacity))
                    } else if cell.isToday {
                        Circle()
                            .strokeBorder(theme.accent, lineWidth: 1.5)
                            .frame(width: 30, height: 30)
                    }

                    Text("\(cell.date.day)")
                        .font(.system(size: 14, weight: (cell.isToday || isSelected) ? .bold : .regular))
                        .monospacedDigit()
                        .foregroundStyle(fgColor)
                }
                .frame(width: 30, height: 30)
                .animation(GokeMotion.press(reduceMotion: simplifyMotion), value: isSelected)

                if hasMarks {
                    CalendarThreeShapeIndicators(
                        marks: cell.marks,
                        scheduleColor: theme.accent.opacity(markAlpha),
                        pendingColor: theme.accent.opacity(markAlpha),
                        doneColor: theme.muted.opacity(0.85 * markAlpha)
                    )
                    .frame(width: 36, height: 9)
                } else {
                    Color.clear.frame(width: 36, height: 9)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.45, maximumDistance: 8)
                .onEnded { _ in
                    didTriggerLongPress = true
                    onLongPress()
                }
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(cell.date.month)月\(cell.date.day)日")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction(named: "预览当日日程", onLongPress)
    }
}

/// 3-shape Canvas indicators matching Android `CalendarScreen.kt:CalendarIndicators`:
/// 1. `scheduled > 0`: 1 capsule bar (`6×3pt`)
/// 2. `tasks > 0`: up to `3 - count` hollow rings (`r = 2.6pt`, `1pt` stroke)
/// 3. `doneTasks > 0`: up to `3 - count` filled dots (`r = 2pt`)
private struct CalendarThreeShapeIndicators: View {
    let marks: DayMarks
    let scheduleColor: Color
    let pendingColor: Color
    let doneColor: Color

    private var indicators: [Int] {
        var list: [Int] = []
        if marks.scheduled > 0 {
            list.append(0)
        }
        let pendingSlots = min(marks.tasks, 3 - list.count)
        if pendingSlots > 0 {
            for _ in 0..<pendingSlots { list.append(1) }
        }
        let doneSlots = min(marks.doneTasks, 3 - list.count)
        if doneSlots > 0 {
            for _ in 0..<doneSlots { list.append(2) }
        }
        return list
    }

    var body: some View {
        let shapes = indicators
        Canvas { context, size in
            guard !shapes.isEmpty else { return }
            let barW: CGFloat = 6.0
            let barH: CGFloat = 3.0
            let ringR: CGFloat = 2.6
            let dotR: CGFloat = 2.0
            let gap: CGFloat = 3.0

            var total: CGFloat = 0
            for k in shapes {
                total += (k == 0 ? barW : (k == 1 ? ringR * 2 : dotR * 2))
            }
            total += gap * CGFloat(shapes.count - 1)

            var x = (size.width - total) / 2.0
            let midY = size.height / 2.0

            for k in shapes {
                switch k {
                case 0:
                    let rect = CGRect(x: x, y: midY - barH / 2.0, width: barW, height: barH)
                    context.fill(
                        Path(roundedRect: rect, cornerRadius: barH / 2.0),
                        with: .color(scheduleColor)
                    )
                    x += barW + gap
                case 1:
                    let rect = CGRect(x: x, y: midY - ringR, width: ringR * 2, height: ringR * 2)
                    context.stroke(
                        Path(ellipseIn: rect),
                        with: .color(pendingColor),
                        lineWidth: 1.0
                    )
                    x += ringR * 2 + gap
                default:
                    let rect = CGRect(x: x, y: midY - dotR, width: dotR * 2, height: dotR * 2)
                    context.fill(
                        Path(ellipseIn: rect),
                        with: .color(doneColor)
                    )
                    x += dotR * 2 + gap
                }
            }
        }
    }
}

// MARK: - Date Picker Dialog Sheet (HIG Graphical DatePicker)

private struct CalendarDatePickerSheet: View {
    let initialDate: LocalDate
    let onSelect: (LocalDate) -> Void
    let onCancel: () -> Void

    @Environment(\.gokeTheme) private var theme
    @State private var pickedDate: Date

    init(
        initialDate: LocalDate,
        onSelect: @escaping (LocalDate) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.initialDate = initialDate
        self.onSelect = onSelect
        self.onCancel = onCancel
        var comps = DateComponents()
        comps.year = initialDate.year
        comps.month = initialDate.month
        comps.day = initialDate.day
        let d = Calendar.current.date(from: comps) ?? Date()
        self._pickedDate = State(initialValue: d)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                DatePicker(
                    "选择日期",
                    selection: $pickedDate,
                    displayedComponents: [.date]
                )
                .datePickerStyle(.graphical)
                .tint(theme.accent)
                .padding(.horizontal, GokeSpacing.md)
                .padding(.bottom, GokeSpacing.md)
            }
            .scrollBounceBehavior(.basedOnSize)
            .background(theme.paper.ignoresSafeArea())
            .navigationTitle("跳转日期")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确定") {
                        let comps = Calendar.current.dateComponents([.year, .month, .day], from: pickedDate)
                        let result = LocalDate(
                            year: comps.year ?? initialDate.year,
                            month: comps.month ?? initialDate.month,
                            day: comps.day ?? initialDate.day
                        )
                        onSelect(result)
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}

// MARK: - Long-Press Day Preview Sheet (Android CalendarScreen.kt:340-404 Parity)

private struct CalendarDayPreviewSheet: View {
    let date: LocalDate
    let entries: [AgendaItem]
    let now: LocalDateTime
    let showTeacher: Bool
    let listColors: [String: Int]
    let onOpenCourse: (String) -> Void
    let onOpenEvent: (String) -> Void
    let onOpenTask: (String) -> Void
    let onToggleTask: (GokeTask, Bool) -> Void
    let onCreateNew: () -> Void
    let onViewDay: () -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let counts = CalendarView.dayCounts(from: entries)
        let detailText: String? = {
            var parts: [String] = []
            if counts.scheduled > 0 { parts.append("\(counts.scheduled) 项日程") }
            if counts.pending > 0 { parts.append("\(counts.pending) 项待办") }
            if counts.done > 0 { parts.append("\(counts.done) 项已完成") }
            return parts.isEmpty ? nil : parts.joined(separator: " · ")
        }()
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: GokeSpacing.sm) {
                Text("\(date.month)月\(date.day)日 \(TodayFormatters.weekdayLabel(date.dayOfWeek))")
                    .font(GokeTypography.titleLarge)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
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
            .padding(.bottom, detailText == nil ? 12 : 0)

            if let detailText {
                Text(detailText)
                    .font(GokeTypography.bodyMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .padding(.top, 4)
                    .padding(.bottom, 12)
            }

            if entries.isEmpty {
                GokeEmptyState(
                    systemImage: "tray",
                    title: "这一天没有安排"
                )
                .frame(maxWidth: .infinity)
                .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: GokeSpacing.sm) {
                        ForEach(entries) { entry in
                            GokeAgendaRow(
                                item: entry,
                                now: now,
                                showTeacher: showTeacher,
                                listColors: listColors,
                                onOpenCourse: onOpenCourse,
                                onOpenEvent: onOpenEvent,
                                onOpenTask: onOpenTask,
                                onToggleTask: onToggleTask
                            )
                        }
                    }
                    .padding(.bottom, GokeSpacing.sm)
                }
            }

            Rectangle()
                .fill(theme.outlineVariant.opacity(0.45))
                .frame(height: 0.5)

            HStack {
                Button(action: onCreateNew) {
                    Text("新建")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                        .foregroundStyle(theme.accent)
                        .frame(minHeight: 44)
                        .padding(.horizontal, GokeSpacing.sm)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)

                Spacer()

                Button(action: onViewDay) {
                    Text("查看当天")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                        .foregroundStyle(theme.accent)
                        .frame(minHeight: 44)
                        .padding(.horizontal, GokeSpacing.sm)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }
            .padding(.top, GokeSpacing.xs)
        }
        .padding(.horizontal, GokeSpacing.lg)
        .padding(.top, 20)
        .padding(.bottom, GokeSpacing.md)
        .background(theme.paper.ignoresSafeArea())
    }
}
