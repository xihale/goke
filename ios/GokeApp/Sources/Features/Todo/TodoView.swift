import Foundation
import GokeCore
import GokeShared
import SwiftUI

// MARK: - Todo Filter Selection

private enum TodoFilter: Hashable, Sendable {
    case timeline
    case smart(SmartList)
    case list(String)
    case course(String)
}

private enum TodoSortMode: String, Hashable, Sendable, CaseIterable {
    case standard
    case priority
    case createdAt

    var title: String {
        switch self {
        case .standard: "按截止时间"
        case .priority: "按优先级"
        case .createdAt: "按创建时间"
        }
    }
}

private struct BulkRescheduleRequest: Identifiable, Hashable, Sendable {
    let id = UUID()
    let taskIds: [String]
}

private enum TodoViewSheet: Identifiable {
    case bulkReschedule(BulkRescheduleRequest)

    var id: String {
        switch self {
        case .bulkReschedule(let req):
            return "bulkReschedule:\(req.id.uuidString)"
        }
    }
}

private struct TodoSortedTimelineSection: Identifiable {
    let bucket: TaskBucket
    let tasks: [GokeTask]
    var id: TaskBucket { bucket }
}

private struct TodoSnapshotIndices {
    let sortedLists: [TaskList]
    let listsById: [String: TaskList]
    let coursesById: [String: Course]
    let subtasksByParent: [String: [GokeTask]]
    let subtaskProgressByParent: [String: (done: Int, total: Int)]
}

private struct TodoDerivedViewData {
    let indices: TodoSnapshotIndices
    let effectiveDueByTaskId: [String: LocalDateTime]
    let overdueCount: Int
    let timelineSections: [TodoSortedTimelineSection]
    let smartTasks: [GokeTask]
    let listTasks: [GokeTask]
    let courseTasks: [GokeTask]
    let visibleTopTasks: [GokeTask]
}

@MainActor
private final class TodoDerivedCacheBox {
    private struct QueryKey: Equatable {
        let snapshotRevision: UInt64
        let focus: GokeFocusFilterConfiguration
        let selectedFilter: TodoFilter
        let selectedPriority: Int?
        let searchQuery: String
        let showCompleted: Bool
        let sortMode: TodoSortMode
        let nowMinute: LocalDateTime
    }

    private var cachedIndicesRevision: UInt64?
    private var cachedIndices: TodoSnapshotIndices?
    private var cachedQueryKey: QueryKey?
    private var cachedData: TodoDerivedViewData?

    func resolve(
        snapshotRevision: UInt64,
        live: AppSnapshot,
        focus: GokeFocusFilterConfiguration,
        selectedFilter: TodoFilter,
        selectedPriority: Int?,
        searchQuery: String,
        showCompleted: Bool,
        sortMode: TodoSortMode,
        nowMinute: LocalDateTime
    ) -> TodoDerivedViewData {
        let key = QueryKey(
            snapshotRevision: snapshotRevision,
            focus: focus,
            selectedFilter: selectedFilter,
            selectedPriority: selectedPriority,
            searchQuery: searchQuery,
            showCompleted: showCompleted,
            sortMode: sortMode,
            nowMinute: nowMinute
        )
        if key == cachedQueryKey, let cachedData {
            return cachedData
        }

        let indices: TodoSnapshotIndices
        if cachedIndicesRevision == snapshotRevision, let existing = cachedIndices {
            indices = existing
        } else {
            let sortedLists = live.lists.sorted { $0.sort < $1.sort }
            let listsById = Dictionary(uniqueKeysWithValues: live.lists.map { ($0.id, $0) })
            let coursesById = Dictionary(uniqueKeysWithValues: live.courses.map { ($0.id, $0) })
            var subtasksByParent: [String: [GokeTask]] = [:]
            for t in live.tasks where t.deletedAt == nil {
                if let pid = t.parentId {
                    subtasksByParent[pid, default: []].append(t)
                }
            }
            var subtaskProgressByParent: [String: (done: Int, total: Int)] = [:]
            subtaskProgressByParent.reserveCapacity(subtasksByParent.count)
            for key in subtasksByParent.keys {
                subtasksByParent[key]?.sort { $0.sortOrder < $1.sortOrder }
                if let subs = subtasksByParent[key], !subs.isEmpty {
                    var doneCount = 0
                    for sub in subs where sub.done { doneCount += 1 }
                    subtaskProgressByParent[key] = (done: doneCount, total: subs.count)
                }
            }
            indices = TodoSnapshotIndices(
                sortedLists: sortedLists,
                listsById: listsById,
                coursesById: coursesById,
                subtasksByParent: subtasksByParent,
                subtaskProgressByParent: subtaskProgressByParent
            )
            cachedIndicesRevision = snapshotRevision
            cachedIndices = indices
        }

        let filteredTasks = Self.filteredBaseTasks(
            from: live,
            focus: focus,
            selectedPriority: selectedPriority,
            searchText: searchQuery,
            listsById: indices.listsById,
            subtasksByParent: indices.subtasksByParent
        )

        var effectiveDueByTaskId: [String: LocalDateTime] = [:]
        effectiveDueByTaskId.reserveCapacity(filteredTasks.count)
        let currentSemester = live.currentSemester
        for task in filteredTasks where task.parentId == nil {
            if let due = TaskLogic.effectiveDue(
                task: task,
                semester: currentSemester,
                courses: live.courses,
                exceptions: live.exceptions,
                now: nowMinute
            ) {
                effectiveDueByTaskId[task.id] = due
            }
        }
        let dueLookup: (GokeTask) -> LocalDateTime? = { effectiveDueByTaskId[$0.id] }

        // Single TaskLogic.timeline pass feeds both overdueCount and timeline sections.
        let rawTimelineSections = TaskLogic.timeline(filteredTasks, now: nowMinute, dueOf: dueLookup)
        let overdueCount = rawTimelineSections.first(where: { $0.bucket == .OVERDUE })?.tasks.count ?? 0

        let timelineSections: [TodoSortedTimelineSection]
        let smartTasks: [GokeTask]
        let listTasks: [GokeTask]
        let courseTasks: [GokeTask]
        let visibleTopTasks: [GokeTask]

        switch selectedFilter {
        case .timeline:
            let visibleRaw = showCompleted
                ? rawTimelineSections
                : rawTimelineSections.filter { $0.bucket != .DONE }
            timelineSections = visibleRaw.map { section in
                let ordered = (section.bucket == .DONE || sortMode == .standard)
                    ? section.tasks
                    : Self.sortTasks(section.tasks, sortMode: sortMode, dueOf: dueLookup)
                return TodoSortedTimelineSection(bucket: section.bucket, tasks: ordered)
            }
            smartTasks = []
            listTasks = []
            courseTasks = []
            visibleTopTasks = timelineSections.flatMap(\.tasks)

        case .smart(let smartList):
            timelineSections = []
            let rawFiltered = TaskLogic.filter(
                filteredTasks,
                smartList: smartList,
                semester: currentSemester,
                courses: live.courses,
                exceptions: live.exceptions,
                now: nowMinute
            )
            smartTasks = (smartList == .DONE || sortMode == .standard)
                ? rawFiltered
                : Self.sortTasks(rawFiltered, sortMode: sortMode, dueOf: dueLookup)
            listTasks = []
            courseTasks = []
            visibleTopTasks = smartTasks

        case .list(let listId):
            timelineSections = []
            smartTasks = []
            let baseListTasks = TaskLogic.topLevel(filteredTasks).filter {
                $0.listId == listId && (showCompleted || !$0.done)
            }
            listTasks = Self.sortTasks(baseListTasks, sortMode: sortMode, dueOf: dueLookup)
            courseTasks = []
            visibleTopTasks = listTasks

        case .course(let courseId):
            timelineSections = []
            smartTasks = []
            listTasks = []
            let scopedTasks: [GokeTask] = if let target = indices.coursesById[courseId] {
                TaskLogic.forCourse(filteredTasks, courses: live.courses, target: target, includeDone: showCompleted)
            } else {
                TaskLogic.topLevel(filteredTasks).filter { $0.courseId == courseId && (showCompleted || !$0.done) }
            }
            courseTasks = Self.sortTasks(scopedTasks, sortMode: sortMode, dueOf: dueLookup)
            visibleTopTasks = courseTasks
        }

        let result = TodoDerivedViewData(
            indices: indices,
            effectiveDueByTaskId: effectiveDueByTaskId,
            overdueCount: overdueCount,
            timelineSections: timelineSections,
            smartTasks: smartTasks,
            listTasks: listTasks,
            courseTasks: courseTasks,
            visibleTopTasks: visibleTopTasks
        )
        cachedQueryKey = key
        cachedData = result
        return result
    }

    private static func filteredBaseTasks(
        from live: AppSnapshot,
        focus: GokeFocusFilterConfiguration,
        selectedPriority: Int?,
        searchText: String,
        listsById: [String: TaskList],
        subtasksByParent: [String: [GokeTask]]
    ) -> [GokeTask] {
        var result = live.tasks.filter { $0.deletedAt == nil }
        if focus.hideNonAcademicTasks {
            result = result.filter { $0.courseId != nil }
        }
        if let tag = focus.focusTag?.trimmingCharacters(in: .whitespacesAndNewlines), !tag.isEmpty {
            result = result.filter { $0.tags.contains(tag) }
        }
        if let p = selectedPriority {
            result = result.filter { $0.parentId != nil || $0.priority == p }
        }
        let parsedQuery = TodoSearch.parse(searchText)
        if !parsedQuery.isEmpty {
            result = result.filter { task in
                if task.parentId != nil { return true }
                let listName = task.listId.flatMap { listsById[$0]?.name }
                let subs = subtasksByParent[task.id] ?? []
                return TodoSearch.matches(
                    query: parsedQuery,
                    task: task,
                    subtasks: subs,
                    listName: listName
                )
            }
        }
        return result
    }

    private static func sortTasks(
        _ tasks: [GokeTask],
        sortMode: TodoSortMode,
        dueOf: (GokeTask) -> LocalDateTime?
    ) -> [GokeTask] {
        tasks.sorted { lhs, rhs in
            if lhs.done != rhs.done { return !lhs.done }
            switch sortMode {
            case .standard:
                let ld = dueOf(lhs)
                let rd = dueOf(rhs)
                if (ld == nil) != (rd == nil) { return ld != nil }
                if let ld, let rd, ld != rd { return ld < rd }
                if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
                return lhs.sortOrder < rhs.sortOrder
            case .priority:
                if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
                let ld = dueOf(lhs)
                let rd = dueOf(rhs)
                if (ld == nil) != (rd == nil) { return ld != nil }
                if let ld, let rd, ld != rd { return ld < rd }
                return lhs.sortOrder < rhs.sortOrder
            case .createdAt:
                if lhs.updatedAt != rhs.updatedAt { return lhs.updatedAt > rhs.updatedAt }
                return lhs.sortOrder < rhs.sortOrder
            }
        }
    }
}

// MARK: - TodoView

/// Paper-row Todo tab matching Android `TodoScreen.kt` & `DESIGN.md`:
/// - Borderless rows resting directly on `theme.paper` with crisp `0.5pt` hairlines and ≥6-row density.
/// - Structured search (`#清单 @标签 !高` + subtask title match via `TodoSearch`) & Priority filter.
/// - Multi-select mode with floating `BulkBar` (Reschedule / Move to List / Priority / Complete / Delete).
/// - Collapsible timeline buckets (default collapsed: `LATER, DONE`), per-section `+` quick-add,
///   inline `"整理"` action on `OVERDUE` header (`>= 2`), and 100-item `DONE` truncation.
/// - Inline subtask expansion (`SubRow` + `InlineSubAdd`) and specialized Exam / Dropped rows.
struct TodoView: View {
    private static let maxDoneVisible = 100

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    @State private var derivedCache = TodoDerivedCacheBox()
    @State private var clockTick: Int = 0

    @State private var selectedFilter: TodoFilter = .timeline
    @State private var selectedPriority: Int? = nil
    @State private var sortMode: TodoSortMode = .standard
    @State private var showCompleted: Bool = true
    @State private var showListTray: Bool = false
    @State private var showPriorityTray: Bool = false
    @State private var isSearching: Bool = false
    @State private var searchText: String = ""
    @FocusState private var isSearchFocused: Bool

    @State private var collapsedBuckets: Set<TaskBucket> = [.LATER, .DONE]
    @State private var expandedSubtaskParents: Set<String> = []
    @State private var inlineSubtaskDrafts: [String: String] = [:]

    @State private var isSelecting: Bool = false
    @State private var selectedIds: Set<String> = []

    @State private var showNewListAlert: Bool = false
    @State private var newListName: String = ""
    @State private var newListColorIndex: Int = 1
    @State private var activeSheet: TodoViewSheet?

    @State private var swipeCommitTrigger: Int = 0

    init() {}

    private var hasActiveFilter: Bool {
        selectedFilter != .timeline
            || selectedPriority != nil
            || !showCompleted
            || !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var activeListIdForQuickAdd: String? {
        if case .list(let id) = selectedFilter { return id }
        return nil
    }

    var body: some View {
        let _ = clockTick
        let now = LocalDateTime.now()
        let nowMinute = LocalDateTime(
            date: now.date,
            time: LocalTime(hour: now.time.hour, minute: now.time.minute, second: 0)
        )
        let live = container.liveSnapshot
        let focus = container.intentBridge.focusFilter
        let derived = derivedCache.resolve(
            snapshotRevision: container.snapshotRevision,
            live: live,
            focus: focus,
            selectedFilter: selectedFilter,
            selectedPriority: selectedPriority,
            searchQuery: searchText,
            showCompleted: showCompleted,
            sortMode: sortMode,
            nowMinute: nowMinute
        )
        let sortedLists = derived.indices.sortedLists
        let coursesById = derived.indices.coursesById
        let listsById = derived.indices.listsById
        let visibleTopTasks = derived.visibleTopTasks
        let visibleSelectedTasks = isSelecting
            ? visibleTopTasks.filter { selectedIds.contains($0.id) }
            : []
        let overdueCount = derived.overdueCount
        let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)

        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                todoHeaderBar(
                    live: live,
                    sortedLists: sortedLists,
                    listsById: listsById,
                    coursesById: coursesById,
                    overdueCount: overdueCount,
                    visibleTopTasks: visibleTopTasks,
                    visibleSelectedCount: visibleSelectedTasks.count
                )

                if isSearching && !isSelecting {
                    collapsibleSearchBar
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.bottom, GokeSpacing.xs)
                        .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
                }

                if !isSelecting {
                    compactFilterStrip(
                        sortedLists: sortedLists,
                        listsById: listsById,
                        coursesById: coursesById
                    )
                }

                paperTaskScroll(
                    derived: derived,
                    now: nowMinute,
                    focus: focus
                )
            }

            if isSelecting {
                bulkActionBar(
                    selectedTasks: visibleSelectedTasks,
                    allVisibleTasks: visibleTopTasks,
                    lists: sortedLists,
                    now: now
                )
                .transition(simplifyMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            }
        }
        .overlay(alignment: .bottomTrailing) {
            if !isSelecting {
                GokeMintFab(accessibilityLabel: "快速添加待办") {
                    router.presentQuickAdd(defaultListId: activeListIdForQuickAdd)
                }
                .padding(.trailing, GokeSpacing.lg)
                .padding(.bottom, GokeSpacing.lg)
                .transition(simplifyMotion ? .opacity : .scale(scale: 0.85).combined(with: .opacity))
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .alert("新建清单", isPresented: $showNewListAlert) {
            TextField("清单名称", text: $newListName)
            Button("取消", role: .cancel) {}
            Button("创建") { createTaskList() }
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .bulkReschedule(let req):
                RescheduleTaskSheet(taskIds: req.taskIds) {
                    exitSelectionMode()
                }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                clockTick &+= 1
            }
        }
        .onChange(of: router.reselectCount(for: .todo)) { _, _ in
            withAnimation(GokeMotion.standard(reduceMotion: reduceMotion)) {
                selectedFilter = .timeline
                selectedPriority = nil
                showListTray = false
                showPriorityTray = false
                isSearching = false
                isSearchFocused = false
                searchText = ""
                exitSelectionMode()
            }
        }
    }

    // MARK: - 64pt Left-Aligned Paper Header (Android TodoTopBar Parity)

    private func todoHeaderBar(
        live: AppSnapshot,
        sortedLists: [TaskList],
        listsById: [String: TaskList],
        coursesById: [String: Course],
        overdueCount: Int,
        visibleTopTasks: [GokeTask],
        visibleSelectedCount: Int
    ) -> some View {
        HStack(alignment: .center, spacing: GokeSpacing.xs) {
            if isSelecting {
                Text("已选 \(visibleSelectedCount) 项")
                    .font(GokeTypography.titleLarge)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)

                let selectableIds = Set(visibleTopTasks.filter { !$0.done }.map(\.id))
                let allSelected = !selectableIds.isEmpty && selectableIds.isSubset(of: selectedIds)
                Button(allSelected ? "取消全选" : "全选") {
                    if allSelected {
                        selectedIds.subtract(selectableIds)
                    } else {
                        selectedIds.formUnion(selectableIds)
                    }
                }
                .font(GokeTypography.labelLarge.weight(.medium))
                .foregroundStyle(theme.accent)
                .frame(minHeight: 44)
                .padding(.horizontal, 6)
                .buttonStyle(.mintCompact)

                Button("完成") {
                    exitSelectionMode()
                }
                .font(GokeTypography.labelLarge.weight(.semibold))
                .foregroundStyle(theme.ink)
                .frame(minHeight: 44)
                .padding(.horizontal, 6)
                .buttonStyle(.mintCompact)
            } else {
                Menu {
                    filterMenuItems(
                        lists: sortedLists,
                        courses: live.courses,
                        overdueCount: overdueCount
                    )
                } label: {
                    HStack(spacing: 4) {
                        Text(activeFilterTitle(listsById: listsById, coursesById: coursesById))
                            .font(GokeTypography.titleLarge)
                            .foregroundStyle(theme.ink)
                            .lineLimit(1)
                            .truncationMode(.tail)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(theme.muted)
                    }
                    .frame(minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .accessibilityLabel("切换待办清单或智能视图")

                Spacer(minLength: 0)

                // Right icon 1: Collapsible Search toggle (quiet theme.ink)
                Button {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        if isSearching {
                            isSearching = false
                            isSearchFocused = false
                            searchText = ""
                        } else {
                            isSearching = true
                            showListTray = false
                            showPriorityTray = false
                            isSearchFocused = true
                        }
                    }
                } label: {
                    Image(systemName: isSearching ? "xmark" : "magnifyingglass")
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(theme.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel(isSearching ? "关闭搜索" : "搜索待办")

                // Right icon 2: Quiet overflow menu (Sort / Multi-select / Completed / Tools)
                Menu {
                    Button {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            isSelecting = true
                            selectedIds = []
                            showListTray = false
                            showPriorityTray = false
                        }
                    } label: {
                        Label("批量管理", systemImage: "checklist")
                    }

                    Menu {
                        Picker("排序", selection: Binding(
                            get: { sortMode },
                            set: { next in
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    sortMode = next
                                }
                            }
                        )) {
                            ForEach(TodoSortMode.allCases, id: \.self) { mode in
                                Text(mode.title).tag(mode)
                            }
                        }
                    } label: {
                        Label("排序：\(sortMode.title)", systemImage: "arrow.up.arrow.down")
                    }

                    Button {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            showCompleted.toggle()
                        }
                    } label: {
                        Label(
                            showCompleted ? "隐藏已完成" : "显示已完成",
                            systemImage: showCompleted ? "eye.slash" : "eye"
                        )
                    }

                    Divider()

                    Button {
                        router.present(.morningTidy)
                    } label: {
                        Label("整理逾期", systemImage: "sunrise")
                    }

                    Button {
                        router.present(.batchAdd(lines: []))
                    } label: {
                        Label("批量添加", systemImage: "text.badge.plus")
                    }

                    Button {
                        router.present(.todoEdit())
                    } label: {
                        Label("详细新建", systemImage: "plus.square")
                    }

                    Divider()

                    Button {
                        promptNewList(listCount: live.lists.count)
                    } label: {
                        Label("新建清单…", systemImage: "folder.badge.plus")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(theme.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("更多待办操作")
            }
        }
        .padding(.leading, GokeSpacing.lg)
        .padding(.trailing, GokeSpacing.sm)
        .frame(height: 64)
        .background(theme.paper)
    }

    private var collapsibleSearchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(theme.muted)

            TextField("搜索待办", text: $searchText)
                .font(.body)
                .foregroundStyle(theme.ink)
                .focused($isSearchFocused)
                .submitLabel(.search)

            if !searchText.isEmpty {
                Button {
                    searchText = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(theme.muted)
                        .frame(width: 40, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("清除搜索词")
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .frame(minHeight: 44)
        .background(Capsule().fill(theme.surface))
    }

    // MARK: - Paper Scroll Content

    private func paperTaskScroll(
        derived: TodoDerivedViewData,
        now: LocalDateTime,
        focus: GokeFocusFilterConfiguration
    ) -> some View {
        let showFocusBanner = focus.hideNonAcademicTasks || (focus.focusTag?.isEmpty == false)
        let coursesById = derived.indices.coursesById
        let listsById = derived.indices.listsById
        let subtasksByParent = derived.indices.subtasksByParent
        let subtaskProgressByParent = derived.indices.subtaskProgressByParent
        let effectiveDueByTaskId = derived.effectiveDueByTaskId

        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if showFocusBanner {
                    focusFilterBanner(focus: focus)
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.vertical, GokeSpacing.xs)
                }

                switch selectedFilter {
                case .timeline:
                    let sections = derived.timelineSections
                    if sections.isEmpty {
                        emptyStateView
                    } else {
                        ForEach(sections) { section in
                            let isCollapsed = collapsedBuckets.contains(section.bucket)
                            let orderedTasks = section.tasks
                            paperSectionHeader(
                                bucket: section.bucket,
                                title: bucketTitle(section.bucket),
                                count: orderedTasks.count,
                                isCollapsed: isCollapsed,
                                showTidyAction: section.bucket == .OVERDUE && orderedTasks.count >= 2,
                                showQuickAdd: isDroppableBucket(section.bucket) && !isSelecting,
                                onToggleCollapse: { toggleBucket(section.bucket) },
                                onQuickAdd: {
                                    let today = now.date
                                    switch section.bucket {
                                    case .TODAY:
                                        router.presentQuickAdd(
                                            defaultStartDate: today,
                                            defaultBucket: .TODAY,
                                            defaultListId: activeListIdForQuickAdd
                                        )
                                    case .TOMORROW:
                                        router.presentQuickAdd(
                                            defaultDueDate: today.addingDays(1),
                                            defaultBucket: .TOMORROW,
                                            defaultListId: activeListIdForQuickAdd
                                        )
                                    case .THIS_WEEK:
                                        router.presentQuickAdd(
                                            defaultDueDate: today.addingDays(2),
                                            defaultBucket: .THIS_WEEK,
                                            defaultListId: activeListIdForQuickAdd
                                        )
                                    case .LATER:
                                        router.presentQuickAdd(
                                            defaultDueDate: today.addingDays(7),
                                            defaultBucket: .LATER,
                                            defaultListId: activeListIdForQuickAdd
                                        )
                                    default:
                                        router.presentQuickAdd(
                                            defaultBucket: section.bucket,
                                            defaultListId: activeListIdForQuickAdd
                                        )
                                    }
                                }
                            )

                            if !isCollapsed {
                                let displayTasks = section.bucket == .DONE
                                    ? Array(orderedTasks.prefix(Self.maxDoneVisible))
                                    : orderedTasks
                                Group {
                                    ForEach(displayTasks) { task in
                                        taskRowWithSubtasks(
                                            task: task,
                                            now: now,
                                            effectiveDue: effectiveDueByTaskId[task.id],
                                            coursesById: coursesById,
                                            listsById: listsById,
                                            subtasks: subtasksByParent[task.id] ?? [],
                                            subtaskProgress: subtaskProgressByParent[task.id]
                                        )
                                    }
                                    if section.bucket == .DONE && orderedTasks.count > Self.maxDoneVisible {
                                        Text("仅显示最近 \(Self.maxDoneVisible) 项，共 \(orderedTasks.count) 项")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundStyle(theme.muted)
                                            .frame(maxWidth: .infinity, alignment: .center)
                                            .padding(.vertical, GokeSpacing.sm)
                                    }
                                }
                                .transition(
                                    GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                                        ? .opacity
                                        : .opacity.combined(with: .move(edge: .top))
                                )
                            }
                        }
                    }

                case .smart(let smartList):
                    let filtered = derived.smartTasks
                    if filtered.isEmpty {
                        emptyStateView
                    } else {
                        let displayTasks = smartList == .DONE
                            ? Array(filtered.prefix(Self.maxDoneVisible))
                            : filtered
                        ForEach(displayTasks) { task in
                            taskRowWithSubtasks(
                                task: task,
                                now: now,
                                effectiveDue: effectiveDueByTaskId[task.id],
                                coursesById: coursesById,
                                listsById: listsById,
                                subtasks: subtasksByParent[task.id] ?? [],
                                subtaskProgress: subtaskProgressByParent[task.id]
                            )
                        }
                        if smartList == .DONE && filtered.count > Self.maxDoneVisible {
                            Text("仅显示最近 \(Self.maxDoneVisible) 项，共 \(filtered.count) 项")
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.vertical, GokeSpacing.sm)
                        }
                    }

                case .list:
                    let listTasks = derived.listTasks
                    if listTasks.isEmpty {
                        emptyStateView
                    } else {
                        ForEach(listTasks) { task in
                            taskRowWithSubtasks(
                                task: task,
                                now: now,
                                effectiveDue: effectiveDueByTaskId[task.id],
                                coursesById: coursesById,
                                listsById: listsById,
                                subtasks: subtasksByParent[task.id] ?? [],
                                subtaskProgress: subtaskProgressByParent[task.id]
                            )
                        }
                    }

                case .course:
                    let courseTasks = derived.courseTasks
                    if courseTasks.isEmpty {
                        emptyStateView
                    } else {
                        ForEach(courseTasks) { task in
                            taskRowWithSubtasks(
                                task: task,
                                now: now,
                                effectiveDue: effectiveDueByTaskId[task.id],
                                coursesById: coursesById,
                                listsById: listsById,
                                subtasks: subtasksByParent[task.id] ?? [],
                                subtaskProgress: subtaskProgressByParent[task.id]
                            )
                        }
                    }
                }
            }
            .padding(.bottom, 104)
        }
        .background(theme.paper)
        .scrollDismissesKeyboard(.interactively)
        .gokeSwipeCommitFeedback(trigger: swipeCommitTrigger)
    }

    // MARK: - Compact Android-Style Filter Strip (No Duplicate Smart-List Bar)

    private func compactFilterStrip(
        sortedLists: [TaskList],
        listsById: [String: TaskList],
        coursesById: [String: Course]
    ) -> some View {
        let activeListId: String? = {
            if case .list(let id) = selectedFilter { return id }
            return nil
        }()
        let activeScopeChipTitle: String? = {
            switch selectedFilter {
            case .timeline, .list: return nil
            case .smart(let smart): return smartListTitle(smart)
            case .course(let courseId): return coursesById[courseId]?.name ?? "课程待办"
            }
        }()

        return VStack(alignment: .leading, spacing: 6) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            if !sortedLists.isEmpty {
                                Button {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        showListTray.toggle()
                                        if showListTray { showPriorityTray = false }
                                    }
                                } label: {
                                    HStack(spacing: 5) {
                                        Image(systemName: "folder")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundStyle(activeListId != nil ? theme.accent : theme.muted)
                                        Text(activeListId.flatMap { listsById[$0]?.name } ?? "全部清单")
                                            .font(GokeTypography.labelMedium.weight(activeListId != nil ? .semibold : .medium))
                                            .foregroundStyle(activeListId != nil ? theme.accent : theme.ink)
                                            .lineLimit(1)
                                        Image(systemName: "chevron.down")
                                            .font(.system(size: 10, weight: .semibold))
                                            .foregroundStyle(theme.muted)
                                            .rotationEffect(.degrees(showListTray ? 180 : 0))
                                    }
                                    .padding(.horizontal, 12)
                                    .frame(minHeight: 36)
                                    .background(
                                        Capsule().fill(
                                            activeListId != nil ? theme.accentContainer.opacity(0.65) : theme.surfaceHighest
                                        )
                                    )
                                }
                                .buttonStyle(.mintCompact)
                            }

                            Button {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    showPriorityTray.toggle()
                                    if showPriorityTray { showListTray = false }
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: "flag")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundStyle(selectedPriority.flatMap { theme.priorityColor($0) } ?? theme.muted)
                                    Text(selectedPriority.map { priorityLabel($0) } ?? "优先级")
                                        .font(GokeTypography.labelMedium.weight(selectedPriority != nil ? .semibold : .medium))
                                        .foregroundStyle(selectedPriority.flatMap { theme.priorityColor($0) } ?? theme.ink)
                                        .lineLimit(1)
                                    Image(systemName: "chevron.down")
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundStyle(theme.muted)
                                        .rotationEffect(.degrees(showPriorityTray ? 180 : 0))
                                }
                                .padding(.horizontal, 12)
                                .frame(minHeight: 36)
                                .background(
                                    Capsule().fill(
                                        selectedPriority != nil
                                            ? (theme.priorityColor(selectedPriority ?? 0) ?? theme.accent).opacity(0.14)
                                            : theme.surfaceHighest
                                    )
                                )
                            }
                            .buttonStyle(.mintCompact)

                            if let scopeTitle = activeScopeChipTitle {
                                dismissibleFilterPill(title: scopeTitle) {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        selectedFilter = .timeline
                                    }
                                }
                            }

                            if sortMode != .standard {
                                dismissibleFilterPill(title: sortMode.title) {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        sortMode = .standard
                                    }
                                }
                            }

                            if !showCompleted {
                                dismissibleFilterPill(title: "隐藏已完成") {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        showCompleted = true
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.vertical, 2)
                    }

                    if showListTray && !sortedLists.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                listTrayChip("全部清单", listId: nil, activeListId: activeListId)
                                ForEach(sortedLists) { list in
                                    listTrayChip(list.name, listId: list.id, activeListId: activeListId)
                                }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                        }
                        .background(
                            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                .fill(theme.surface)
                        )
                        .padding(.horizontal, GokeSpacing.lg)
                        .transition(
                            GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                                ? .opacity
                                : .opacity.combined(with: .move(edge: .top))
                        )
                    }

                    if showPriorityTray {
                        HStack(spacing: 6) {
                            priorityTrayChip("全部", level: nil)
                            priorityTrayChip("高", level: 3)
                            priorityTrayChip("中", level: 2)
                            priorityTrayChip("低", level: 1)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .background(
                            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                .fill(theme.surface)
                        )
                        .padding(.horizontal, GokeSpacing.lg)
                        .transition(
                            GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                                ? .opacity
                                : .opacity.combined(with: .move(edge: .top))
                        )
                    }
                }
                .padding(.bottom, 6)
                .background(theme.paper)
    }

    private func listTrayChip(_ title: String, listId: String?, activeListId: String?) -> some View {
        let active = activeListId == listId
        return Button {
            withAnimation(GokeMotion.snappy(policy: performance)) {
                if let listId {
                    selectedFilter = .list(listId)
                } else {
                    selectedFilter = .timeline
                }
                showListTray = false
            }
        } label: {
            Text(title)
                .font(.caption.weight(active ? .semibold : .medium))
                .foregroundStyle(active ? theme.onAccentContainer : theme.ink)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(active ? theme.accentContainer : theme.paper)
                )
        }
        .buttonStyle(.mintCompact)
    }

    private func priorityTrayChip(_ title: String, level: Int?) -> some View {
        let active = selectedPriority == level
        let tint = level.flatMap { theme.priorityColor($0) } ?? theme.accent
        return Button {
            withAnimation(GokeMotion.snappy(policy: performance)) {
                selectedPriority = level
                showPriorityTray = false
            }
        } label: {
            Text(title)
                .font(.caption.weight(active ? .semibold : .medium))
                .foregroundStyle(active ? theme.onAccent : tint)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(active ? tint : theme.paper)
                )
        }
        .buttonStyle(.mintCompact)
    }

    private func dismissibleFilterPill(title: String, onClear: @escaping () -> Void) -> some View {
        Button(action: onClear) {
            HStack(spacing: 4) {
                Text(title)
                    .font(GokeTypography.labelMedium.weight(.semibold))
                    .lineLimit(1)
                Image(systemName: "xmark")
                    .font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(theme.onAccentContainer)
            .padding(.horizontal, 11)
            .frame(minHeight: 36)
            .background(Capsule().fill(theme.accentContainer))
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel("清除筛选 \(title)")
    }

    // MARK: - Collapsible Paper Section Header

    private func paperSectionHeader(
        bucket: TaskBucket,
        title: String,
        count: Int,
        isCollapsed: Bool,
        showTidyAction: Bool,
        showQuickAdd: Bool,
        onToggleCollapse: @escaping () -> Void,
        onQuickAdd: @escaping () -> Void
    ) -> some View {
        let isOverdue = bucket == .OVERDUE
        let isDone = bucket == .DONE
        let titleColor: Color = isOverdue ? theme.removed : (isDone ? theme.muted : theme.ink)
        let pillBg: Color = isOverdue ? theme.removed.opacity(0.14) : theme.surfaceHigh
        let pillFg: Color = isOverdue ? theme.removed : theme.muted

        return HStack(spacing: 6) {
            Button(action: onToggleCollapse) {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(titleColor.opacity(0.75))
                        .frame(width: 14)
                        .rotationEffect(.degrees(isCollapsed ? -90 : 0))

                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(titleColor)

                    Text("\(count)")
                        .font(.caption2.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(pillFg)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(pillBg))
                }
                .frame(minHeight: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            Spacer(minLength: 0)

            if showTidyAction {
                Button("整理") {
                    router.present(.morningTidy)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(theme.removed)
                .padding(.horizontal, 10)
                .frame(minHeight: 32)
                .background(Capsule().fill(theme.removed.opacity(0.12)))
                .buttonStyle(.mintCompact)
            }

            if showQuickAdd {
                Button(action: onQuickAdd) {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 40, height: 40)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("添加到\(title)")
            }
        }
        .padding(.horizontal, GokeSpacing.lg)
        .padding(.top, 14)
        .padding(.bottom, 4)
    }

    // MARK: - Task Row + Inline Subtasks + Paper Swipe

    @ViewBuilder
    private func taskRowWithSubtasks(
        task: GokeTask,
        now: LocalDateTime,
        effectiveDue: LocalDateTime?,
        coursesById: [String: Course],
        listsById: [String: TaskList],
        subtasks: [GokeTask],
        subtaskProgress: (done: Int, total: Int)?
    ) -> some View {
        let isOverdue = TaskLogic.isOverdue(task, due: effectiveDue, now: now)
        let swapSwipe = container.settingsStore.settings.swapSwipe
        let isExpanded = expandedSubtaskParents.contains(task.id)
        let isSelected = selectedIds.contains(task.id)

        VStack(spacing: 0) {
            PaperSwipeRow(
                enabled: !isSelecting,
                swapSwipe: swapSwipe,
                isDone: task.done,
                onSwipeComplete: {
                    swipeCommitTrigger &+= 1
                    toggleTaskCompletion(task, now: now)
                },
                onSwipeReschedule: {
                    swipeCommitTrigger &+= 1
                    router.present(.rescheduleTask(taskId: task.id))
                }
            ) { shouldSuppressTap in
                TodoTaskRow(
                    task: task,
                    effectiveDue: effectiveDue,
                    isOverdue: isOverdue,
                    now: now,
                    course: task.courseId.flatMap { coursesById[$0] },
                    list: task.listId.flatMap { listsById[$0] },
                    subtaskProgress: subtaskProgress,
                    isSubtasksExpanded: isExpanded,
                    isSelecting: isSelecting,
                    isSelected: isSelected,
                    onToggleComplete: {
                        guard !shouldSuppressTap() else { return }
                        if isSelecting {
                            toggleSelection(task.id)
                        } else {
                            toggleTaskCompletion(task, now: now)
                        }
                    },
                    onOpenDetail: {
                        guard !shouldSuppressTap() else { return }
                        if isSelecting {
                            toggleSelection(task.id)
                        } else {
                            router.present(.todoEdit(taskId: task.id))
                        }
                    },
                    onToggleExpandSubtasks: subtasks.isEmpty ? nil : {
                        guard !shouldSuppressTap() else { return }
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            if isExpanded {
                                expandedSubtaskParents.remove(task.id)
                            } else {
                                expandedSubtaskParents.insert(task.id)
                            }
                        }
                    }
                )
                .padding(.leading, GokeSpacing.lg - 11)
                .padding(.trailing, GokeSpacing.lg)
                .padding(.vertical, 6)
                .background(isSelected ? theme.accentContainer.opacity(0.35) : theme.paper)
                .contentShape(Rectangle())
                .contextMenu {
                    Button {
                        toggleTaskCompletion(task, now: now)
                    } label: {
                        Label(
                            task.done ? "重新打开" : "标记完成",
                            systemImage: task.done ? "arrow.uturn.backward" : "checkmark.circle"
                        )
                    }
                    Button {
                        router.present(.rescheduleTask(taskId: task.id))
                    } label: {
                        Label("改期", systemImage: "calendar.badge.clock")
                    }
                    Button {
                        router.present(.todoEdit(taskId: task.id))
                    } label: {
                        Label("编辑详情", systemImage: "pencil")
                    }
                    if !task.done {
                        Button {
                            withAnimation(GokeMotion.snappy(policy: performance)) {
                                isSelecting = true
                                selectedIds = [task.id]
                            }
                        } label: {
                            Label("多选", systemImage: "checklist")
                        }
                    }
                    Divider()
                    Button(role: .destructive) {
                        deleteTask(task)
                    } label: {
                        Label("删除", systemImage: "trash")
                    }
                }
            }

            // Inline expanded SubRow + InlineSubAdd
            if isExpanded && !subtasks.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(subtasks) { sub in
                        HStack(spacing: 2) {
                            GokeCheckBox(
                                isChecked: sub.done,
                                priority: sub.priority,
                                boxSize: 19,
                                accessibilityLabel: sub.done ? "标记子任务未完成" : "标记子任务已完成"
                            ) {
                                toggleTaskCompletion(sub, now: now)
                            }

                            Button {
                                router.present(.todoEdit(taskId: sub.id))
                            } label: {
                                Text(sub.title)
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(sub.done ? theme.muted : theme.ink)
                                    .strikethrough(sub.done)
                                    .lineLimit(2)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)
                            .animation(GokeMotion.press(policy: performance), value: sub.done)
                        }
                        .padding(.leading, 34)
                        .padding(.trailing, GokeSpacing.lg)
                    }

                    // InlineSubAdd input
                    let draftBinding = Binding<String>(
                        get: { inlineSubtaskDrafts[task.id] ?? "" },
                        set: { inlineSubtaskDrafts[task.id] = $0 }
                    )
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(theme.accent)
                        TextField("添加子任务…", text: draftBinding)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                            .onSubmit {
                                commitInlineSubtask(parent: task, existingCount: subtasks.count)
                            }
                        if !(inlineSubtaskDrafts[task.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Button("添加") {
                                commitInlineSubtask(parent: task, existingCount: subtasks.count)
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(theme.accent)
                            .padding(.horizontal, 8)
                            .frame(minHeight: 36)
                            .buttonStyle(.mintCompact)
                        }
                    }
                    .padding(.leading, 46)
                    .padding(.trailing, GokeSpacing.lg)
                    .padding(.vertical, 6)
                }
                .transition(
                    GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                        ? .opacity
                        : .opacity.combined(with: .move(edge: .top))
                )
            }
        }
    }

    // MARK: - Floating BulkBar (Multi-Select Mode)

    private func bulkActionBar(
        selectedTasks: [GokeTask],
        allVisibleTasks: [GokeTask],
        lists: [TaskList],
        now: LocalDateTime
    ) -> some View {
        let hasSelection = !selectedTasks.isEmpty
        return HStack(spacing: 4) {
            bulkBarButton("改期", systemImage: "calendar.badge.clock", disabled: !hasSelection) {
                activeSheet = .bulkReschedule(BulkRescheduleRequest(taskIds: selectedTasks.map(\.id)))
            }

            Menu {
                Button("收集箱") {
                    bulkMoveToList(selectedTasks, listId: nil, listName: "收集箱")
                }
                ForEach(lists) { list in
                    Button(list.name) {
                        bulkMoveToList(selectedTasks, listId: list.id, listName: list.name)
                    }
                }
            } label: {
                bulkBarLabel("清单", systemImage: "folder", disabled: !hasSelection)
            }
            .disabled(!hasSelection)

            Menu {
                Button("高") { bulkSetPriority(selectedTasks, priority: 3) }
                Button("中") { bulkSetPriority(selectedTasks, priority: 2) }
                Button("低") { bulkSetPriority(selectedTasks, priority: 1) }
                Button("无") { bulkSetPriority(selectedTasks, priority: 0) }
            } label: {
                bulkBarLabel("优先级", systemImage: "flag", disabled: !hasSelection)
            }
            .disabled(!hasSelection)

            bulkBarButton("完成", systemImage: "checkmark.circle", tint: theme.added, disabled: !hasSelection) {
                bulkComplete(selectedTasks, allVisible: allVisibleTasks, now: now)
            }

            bulkBarButton("删除", systemImage: "trash", tint: theme.removed, disabled: !hasSelection) {
                bulkDelete(selectedTasks)
            }
        }
        .padding(.horizontal, GokeSpacing.md)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .strokeBorder(theme.outlineVariant.opacity(0.45), lineWidth: 0.5)
        )
        .padding(.horizontal, GokeSpacing.md)
        .padding(.bottom, 12)
    }

    private func bulkBarButton(
        _ title: String,
        systemImage: String,
        tint: Color? = nil,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            bulkBarLabel(title, systemImage: systemImage, tint: tint, disabled: disabled)
        }
        .buttonStyle(.mintCompact)
        .disabled(disabled)
    }

    private func bulkBarLabel(
        _ title: String,
        systemImage: String,
        tint: Color? = nil,
        disabled: Bool
    ) -> some View {
        VStack(spacing: 3) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .semibold))
            Text(title)
                .font(.caption2.weight(.medium))
        }
        .foregroundStyle(disabled ? theme.muted.opacity(0.45) : (tint ?? theme.ink))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }

    // MARK: - Focus Banner & Empty States

    private func focusFilterBanner(focus: GokeFocusFilterConfiguration) -> some View {
        HStack(spacing: GokeSpacing.sm) {
            Image(systemName: "moon.stars.fill")
                .foregroundStyle(theme.tertiary)
            Text(focus.hideNonAcademicTasks ? "仅课程待办" : "仅 #\(focus.focusTag ?? "")")
                .font(.caption.weight(.medium))
                .foregroundStyle(theme.ink)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(theme.tertiaryContainer.opacity(0.5), in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
    }

    private var emptyStateView: some View {
        Group {
            if hasActiveFilter {
                GokeEmptyState(
                    systemImage: "line.3.horizontal.decrease.circle",
                    title: "无匹配待办",
                    primaryActionTitle: "清除筛选",
                    onPrimaryAction: {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            selectedFilter = .timeline
                            selectedPriority = nil
                            sortMode = .standard
                            showCompleted = true
                            showListTray = false
                            showPriorityTray = false
                            isSearching = false
                            isSearchFocused = false
                            searchText = ""
                        }
                    },
                    secondaryActionTitle: "新建",
                    onSecondaryAction: { router.present(.quickAdd()) }
                )
            } else {
                GokeEmptyState(
                    systemImage: "checklist",
                    title: "暂无待办",
                    primaryActionTitle: "快速添加",
                    onPrimaryAction: { router.present(.quickAdd()) },
                    secondaryActionTitle: "批量添加",
                    onSecondaryAction: { router.present(.batchAdd(lines: [])) }
                )
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 56)
    }

    // MARK: - Filter Menus

    @ViewBuilder
    private func filterMenuItems(
        lists: [TaskList],
        courses: [Course],
        overdueCount: Int
    ) -> some View {
        Section("智能清单") {
            filterMenuButton("时间轴", systemImage: "calendar.day.timeline.leading", filter: .timeline)
            filterMenuButton("今日", systemImage: "sun.max", filter: .smart(.TODAY))
            filterMenuButton("计划", systemImage: "calendar", filter: .smart(.PLANNED))
            filterMenuButton("全部", systemImage: "tray.full", filter: .smart(.ALL))
            filterMenuButton(
                overdueCount > 0 ? "逾期 (\(overdueCount))" : "逾期",
                systemImage: "exclamationmark.circle",
                filter: .smart(.OVERDUE)
            )
            filterMenuButton("已完成", systemImage: "checkmark.circle", filter: .smart(.DONE))
        }
        if !lists.isEmpty {
            Section("清单") {
                ForEach(lists) { list in
                    filterMenuButton(list.name, systemImage: "list.bullet", filter: .list(list.id))
                }
            }
        }
        let linkedCourses = TodoQuickHelpers.pickerCourses(
            from: courses,
            currentSemesterId: container.liveSnapshot.currentSemester?.id
        )
        if !linkedCourses.isEmpty {
            Section("课程待办") {
                ForEach(linkedCourses) { course in
                    filterMenuButton(course.name, systemImage: "book.closed", filter: .course(course.id))
                }
            }
        }
        Divider()
        Button {
            promptNewList(listCount: lists.count)
        } label: {
            Label("新建清单…", systemImage: "folder.badge.plus")
        }
    }

    private func filterMenuButton(_ title: String, systemImage: String, filter: TodoFilter) -> some View {
        Button {
            withAnimation(GokeMotion.snappy(reduceMotion: reduceMotion)) {
                selectedFilter = filter
            }
        } label: {
            Label(title, systemImage: selectedFilter == filter ? "checkmark" : systemImage)
        }
    }

    // MARK: - Filtering & Helpers

    private func toggleBucket(_ bucket: TaskBucket) {
        withAnimation(GokeMotion.snappy(policy: performance)) {
            if collapsedBuckets.contains(bucket) {
                collapsedBuckets.remove(bucket)
            } else {
                collapsedBuckets.insert(bucket)
            }
        }
    }

    private func isDroppableBucket(_ bucket: TaskBucket) -> Bool {
        switch bucket {
        case .TODAY, .TOMORROW, .THIS_WEEK, .LATER, .INBOX: true
        case .OVERDUE, .DONE: false
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    private func exitSelectionMode() {
        withAnimation(GokeMotion.snappy(policy: performance)) {
            isSelecting = false
            selectedIds = []
        }
    }

    private func activeFilterTitle(
        listsById: [String: TaskList],
        coursesById: [String: Course]
    ) -> String {
        switch selectedFilter {
        case .timeline: "待办"
        case .smart(let smart): smartListTitle(smart)
        case .list(let listId): listsById[listId]?.name ?? "清单"
        case .course(let courseId): coursesById[courseId]?.name ?? "课程待办"
        }
    }

    private func promptNewList(listCount: Int) {
        newListName = ""
        newListColorIndex = (listCount + 1) % max(theme.courseColors.count, 1)
        showNewListAlert = true
    }

    // MARK: - Mutations (Single-Undo Atomic ChangeSets)

    private func toggleTaskCompletion(_ task: GokeTask, now: LocalDateTime) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs: ChangeSet
        let label: String
        if task.done {
            let reopened = TaskLogic.reopen(task, nowMillis: nowMillis)
            cs = ChangeSet(
                description: "重新打开「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.modify(before: .task(task), after: .task(reopened))]
            )
            label = "已重新打开「\(task.title)」"
        } else {
            let (finished, next) = TaskLogic.complete(task, nowMillis: nowMillis, now: now)
            var changes: [Change] = [.modify(before: .task(task), after: .task(finished))]
            if let next {
                changes.append(.add(after: .task(next)))
            }
            cs = ChangeSet(
                description: "完成「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: changes
            )
            label = next != nil ? "已完成并生成下一次重复任务" : "已完成「\(task.title)」"
        }
        Task {
            await actions.commit(cs, label: label)
        }
    }

    private func commitInlineSubtask(parent: GokeTask, existingCount: Int) {
        let trimmed = (inlineSubtaskDrafts[parent.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        inlineSubtaskDrafts[parent.id] = ""
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let sub = GokeTask(
            title: trimmed,
            listId: parent.listId,
            parentId: parent.id,
            sortOrder: existingCount,
            source: .MANUAL,
            updatedAt: nowMillis
        )
        let cs = ChangeSet(
            description: "添加子任务「\(trimmed)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.add(after: .task(sub))]
        )
        Task {
            await actions.commit(cs, label: "已添加子任务")
        }
    }

    private func bulkMoveToList(_ tasks: [GokeTask], listId: String?, listName: String) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let changes: [Change] = tasks.map { t in
            var next = t
            next.listId = listId
            next.updatedAt = nowMillis
            return .modify(before: .task(t), after: .task(next))
        }
        let cs = ChangeSet(
            description: "移动 \(tasks.count) 项待办至「\(listName)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已移至「\(listName)」")
            if ok { exitSelectionMode() }
        }
    }

    private func bulkSetPriority(_ tasks: [GokeTask], priority: Int) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let changes: [Change] = tasks.map { t in
            var next = t
            next.priority = priority
            next.updatedAt = nowMillis
            return .modify(before: .task(t), after: .task(next))
        }
        let cs = ChangeSet(
            description: "批量设置 \(tasks.count) 项待办优先级",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已更新优先级")
            if ok { exitSelectionMode() }
        }
    }

    private func bulkComplete(_ tasks: [GokeTask], allVisible: [GokeTask], now: LocalDateTime) {
        let undone = tasks.filter { !$0.done }
        guard !undone.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        for t in undone {
            let (finished, next) = TaskLogic.complete(t, nowMillis: nowMillis, now: now)
            changes.append(.modify(before: .task(t), after: .task(finished)))
            if let next {
                changes.append(.add(after: .task(next)))
            }
        }
        let undoneIds = Set(undone.map(\.id))
        let todayRemaining = allVisible.filter {
            !$0.done && !undoneIds.contains($0.id) && ($0.due?.date == now.date || $0.startDate == now.date)
        }
        let clearedToday = todayRemaining.isEmpty
            && undone.contains(where: { $0.due?.date == now.date || $0.startDate == now.date })
        let label = clearedToday ? "今日待办已全部完成！" : "已完成 \(undone.count) 项待办"

        let cs = ChangeSet(
            description: "批量完成 \(undone.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok { exitSelectionMode() }
        }
    }

    private func bulkDelete(_ tasks: [GokeTask]) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        for task in tasks {
            changes.append(.remove(before: .task(task)))
            for sub in TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: task.id) {
                changes.append(.remove(before: .task(sub)))
            }
        }
        let cs = ChangeSet(
            description: "批量删除 \(tasks.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已将 \(tasks.count) 项移入回收站")
            if ok { exitSelectionMode() }
        }
    }

    private func deleteTask(_ task: GokeTask) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let subtasks = TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: task.id)
        var changes: [Change] = [.remove(before: .task(task))]
        for sub in subtasks {
            changes.append(.remove(before: .task(sub)))
        }
        let cs = ChangeSet(
            description: "删除「\(task.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            await actions.commit(cs, label: "已移入回收站")
        }
    }

    private func createTaskList() {
        let trimmed = newListName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let list = TaskList(
            name: trimmed,
            colorIndex: newListColorIndex,
            sort: container.liveSnapshot.lists.count,
            updatedAt: nowMillis
        )
        let cs = ChangeSet(
            description: "新建清单「\(trimmed)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.add(after: .taskList(list))]
        )
        Task {
            await actions.commit(cs, label: "已创建清单「\(trimmed)」")
        }
    }

    private func bucketTitle(_ bucket: TaskBucket) -> String {
        switch bucket {
        case .OVERDUE: "已逾期"
        case .TODAY: "今天"
        case .TOMORROW: "明天"
        case .THIS_WEEK: "本周"
        case .LATER: "以后"
        case .INBOX: "收集箱"
        case .DONE: "已完成"
        }
    }

    private func smartListTitle(_ smart: SmartList) -> String {
        switch smart {
        case .TODAY: "今日待办"
        case .PLANNED: "未来 7 天"
        case .ALL: "全部未完成"
        case .DONE: "已完成"
        case .OVERDUE: "已逾期"
        }
    }

    private func priorityLabel(_ p: Int) -> String {
        switch p {
        case 3...: "高"
        case 2: "中"
        case 1: "低"
        default: "无"
        }
    }
}

// MARK: - PaperSwipeRow

/// Borderless paper swipe gesture matching Android `TodoRows.kt`:
/// Tracks horizontal axis lock in `@State` (backed by `@GestureState` cancellation sentinel)
/// so vertical `ScrollView` lock NEVER triggers a swipe on `.onEnded`, cancelled gestures ALWAYS
/// snap back to `0`, threshold haptics never lock out on retreat, and taps never pass through.
/// Right-swipe (or swapped) = Complete (green `added` 18% tint),
/// left-swipe = Reschedule (iris purple `tertiary` 18% tint). Zero destructive Delete on swipe.
private struct PaperSwipeRow<Content: View>: View {
    private enum SwipeAxis: Sendable {
        case undecided
        case horizontal
        case vertical
    }

    let enabled: Bool
    let swapSwipe: Bool
    let isDone: Bool
    let onSwipeComplete: () -> Void
    let onSwipeReschedule: () -> Void
    let content: (@escaping () -> Bool) -> Content

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @GestureState private var isGestureActive: Bool = false

    private let triggerThreshold: CGFloat = 76
    private let maxSwipeOffset: CGFloat = 110
    @State private var offsetX: CGFloat = 0
    @State private var swipeAxis: SwipeAxis = .undecided
    @State private var didCrossThreshold: Bool = false
    @State private var suppressTapUntil: TimeInterval = 0

    init(
        enabled: Bool,
        swapSwipe: Bool,
        isDone: Bool,
        onSwipeComplete: @escaping () -> Void,
        onSwipeReschedule: @escaping () -> Void,
        @ViewBuilder content: @escaping (@escaping () -> Bool) -> Content
    ) {
        self.enabled = enabled
        self.swapSwipe = swapSwipe
        self.isDone = isDone
        self.onSwipeComplete = onSwipeComplete
        self.onSwipeReschedule = onSwipeReschedule
        self.content = content
    }

    private func shouldSuppressTap() -> Bool {
        swipeAxis == .horizontal
            || abs(offsetX) > 4
            || ProcessInfo.processInfo.systemUptime < suppressTapUntil
    }

    private func resetSwipeState(animated: Bool) {
        swipeAxis = .undecided
        didCrossThreshold = false
        if offsetX != 0 {
            if animated {
                withAnimation(GokeMotion.interactiveDrag(policy: performance)) {
                    offsetX = 0
                }
            } else {
                offsetX = 0
            }
        }
    }

    var body: some View {
        let activeOffset = enabled ? offsetX : 0
        let absOffset = abs(activeOffset)
        let thresholdProgress = min(1.0, absOffset / triggerThreshold)
        let iconScale: CGFloat = absOffset >= triggerThreshold ? 1.12 : (0.75 + 0.30 * thresholdProgress)
        let swipingRight = activeOffset > 0
        let isCompleteDirection = swapSwipe ? !swipingRight : swipingRight
        let bgColor: Color = activeOffset == 0
            ? Color.clear
            : (isCompleteDirection ? theme.added.opacity(0.18) : theme.tertiary.opacity(0.18))
        let iconColor: Color = isCompleteDirection ? theme.added : theme.tertiary
        let iconName: String = isCompleteDirection
            ? (isDone ? "arrow.uturn.backward" : "checkmark")
            : "calendar.badge.clock"

        let swipeCornerRadius = min(GokeRadius.md, absOffset * 0.5)
        let backdropOpacity = min(1.0, Double(absOffset) / 24.0)

        ZStack {
            if absOffset > 0.5 {
                HStack {
                    if swipingRight {
                        Image(systemName: iconName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(iconColor)
                            .scaleEffect(iconScale)
                            .padding(.leading, 22)
                        Spacer()
                    } else {
                        Spacer()
                        Image(systemName: iconName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(iconColor)
                            .scaleEffect(iconScale)
                            .padding(.trailing, 22)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(bgColor)
                .opacity(backdropOpacity)
            }

            content(shouldSuppressTap)
                .offset(x: activeOffset)
                .allowsHitTesting(swipeAxis != .horizontal && absOffset < 4)
        }
        .clipShape(RoundedRectangle(cornerRadius: swipeCornerRadius, style: .continuous))
        .onChange(of: isGestureActive) { _, active in
            if !active {
                if swipeAxis == .horizontal || abs(offsetX) > 4 {
                    suppressTapUntil = ProcessInfo.processInfo.systemUptime + 0.25
                }
                resetSwipeState(animated: true)
            }
        }
        .onChange(of: enabled) { _, isEnabled in
            if !isEnabled {
                resetSwipeState(animated: false)
            }
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 20, coordinateSpace: .local)
                .updating($isGestureActive) { _, state, _ in
                    state = true
                }
                .onChanged { value in
                    guard enabled else { return }
                    let dx = value.translation.width
                    let dy = value.translation.height
                    if swipeAxis == .undecided {
                        guard abs(dx) >= 12 || abs(dy) >= 12 else { return }
                        if abs(dx) > abs(dy) * 1.4 {
                            swipeAxis = .horizontal
                        } else {
                            swipeAxis = .vertical
                            return
                        }
                    }
                    guard swipeAxis == .horizontal else { return }
                    let clamped = max(-maxSwipeOffset, min(maxSwipeOffset, dx))
                    offsetX = clamped
                    let mag = abs(clamped)
                    if mag >= triggerThreshold && !didCrossThreshold {
                        didCrossThreshold = true
                        GokeHaptics.swipeCommit()
                    } else if mag < triggerThreshold - 16 && didCrossThreshold {
                        didCrossThreshold = false
                    }
                }
                .onEnded { value in
                    let wasHorizontal = (swipeAxis == .horizontal)
                    let finalOffset = offsetX
                    if wasHorizontal && (abs(finalOffset) > 4 || abs(value.translation.width) > 12) {
                        suppressTapUntil = ProcessInfo.processInfo.systemUptime + 0.28
                    }
                    resetSwipeState(animated: true)

                    guard enabled, wasHorizontal, abs(finalOffset) >= triggerThreshold else { return }
                    let right = finalOffset > 0
                    let completeAction = swapSwipe ? !right : right
                    if completeAction {
                        onSwipeComplete()
                    } else {
                        onSwipeReschedule()
                    }
                }
        )
    }
}

// MARK: - TodoTaskRow

private struct TodoTaskRow: View {
    let task: GokeTask
    let effectiveDue: LocalDateTime?
    let isOverdue: Bool
    let now: LocalDateTime
    let course: Course?
    let list: TaskList?
    let subtaskProgress: (done: Int, total: Int)?
    let isSubtasksExpanded: Bool
    let isSelecting: Bool
    let isSelected: Bool
    let onToggleComplete: () -> Void
    let onOpenDetail: () -> Void
    let onToggleExpandSubtasks: (() -> Void)?

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance

    var body: some View {
        let listTint = list.map { theme.courseColor(index: $0.colorIndex).foreground }

        HStack(alignment: .center, spacing: 2) {
            if isSelecting {
                Button(action: onToggleComplete) {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: 22))
                        .foregroundStyle(isSelected ? theme.accent : theme.outline)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            } else {
                GokeCheckBox(
                    isChecked: task.done,
                    priority: task.priority,
                    listColor: listTint,
                    accessibilityLabel: task.done ? "标记为未完成" : "标记为已完成",
                    onToggle: onToggleComplete
                )
            }

            Button(action: onOpenDetail) {
                VStack(alignment: .leading, spacing: 3) {
                    headlineView

                    if !task.note.isEmpty {
                        Text(task.note)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }

                    metadataRow
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)
            .gokeCompletedOpacity(task.done)
            .animation(GokeMotion.press(policy: performance), value: task.done)

            // Trailing Subtask Expand Chevron (>=44pt hit target)
            if let onToggleExpandSubtasks, !isSelecting {
                Button(action: onToggleExpandSubtasks) {
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(theme.muted)
                        .rotationEffect(.degrees(isSubtasksExpanded ? 180 : 0))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel(isSubtasksExpanded ? "收起子任务" : "展开子任务")
            }
        }
    }

    @ViewBuilder
    private var headlineView: some View {
        if task.isExam {
            // Exam row: split exam badge + clean title
            let display = JxfwSyncMapper.splitExamDisplay(task.title)
            HStack(spacing: 6) {
                GokeBadge(display.badgeShort, tone: .tertiary)
                Text(display.title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(task.done ? theme.muted : theme.ink)
                    .strikethrough(task.done)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
        } else {
            Text(task.title)
                .font(GokeTypography.bodyLarge)
                .foregroundStyle(task.done ? theme.muted : theme.ink)
                .strikethrough(task.done)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
    }

    @ViewBuilder
    private var metadataRow: some View {
        let hasMetadata = task.dropped
            || effectiveDue != nil
            || course != nil
            || list != nil
            || subtaskProgress != nil
            || task.`repeat` != nil
            || task.blockStart != nil
            || !task.tags.isEmpty
            || !task.attachments.isEmpty

        if hasMetadata {
            HStack(spacing: 6) {
                if task.dropped {
                    Text("已放弃")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(theme.muted)
                }

                if let due = effectiveDue {
                    HStack(spacing: 3) {
                        Image(systemName: task.dueBeforeNextClass ? "bell.badge" : "calendar")
                        Text(
                            TodoDateFormatter.dueLabel(
                                due: due,
                                hasTime: task.dueHasTime || task.dueBeforeNextClass,
                                now: now,
                                dueBeforeNextClass: task.dueBeforeNextClass
                            )
                        )
                        .monospacedDigit()
                    }
                    .font(.caption)
                    .foregroundStyle(isOverdue ? theme.removed : (due.date == now.date ? theme.accent : theme.muted))
                }

                if task.`repeat` != nil {
                    Image(systemName: "repeat")
                        .font(.caption2)
                        .foregroundStyle(theme.muted)
                }

                if let subtaskProgress {
                    Label("\(subtaskProgress.done)/\(subtaskProgress.total)", systemImage: "checklist")
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                }

                if !task.attachments.isEmpty {
                    let imageCount = task.attachments.filter(\.isImage).count
                    Label(
                        "\(task.attachments.count)",
                        systemImage: imageCount > 0 ? "photo" : "paperclip"
                    )
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                }

                if let blockStart = task.blockStart {
                    Label(TodoDateFormatter.hhmm(blockStart.time), systemImage: "clock")
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                }

                if let course {
                    let courseLabel = task.courseWeek.map { "\(course.name) · 第\($0)周" } ?? course.name
                    GokeBadge(courseLabel, tone: .course(course.colorIndex))
                } else if let list {
                    GokeBadge(list.name, tone: .muted)
                }

                if let firstTag = task.tags.first {
                    Text("@\(firstTag)")
                        .font(.caption)
                        .foregroundStyle(theme.secondary)
                }
            }
            .lineLimit(1)
        }
    }
}

// MARK: - Todo Date Formatting Helpers

enum TodoDateFormatter {
    static func hhmm(_ time: LocalTime) -> String {
        String(format: "%02d:%02d", time.hour, time.minute)
    }

    static func shortDate(_ date: LocalDate) -> String {
        "\(date.month)月\(date.day)日"
    }

    static func dueLabel(
        due: LocalDateTime,
        hasTime: Bool,
        now: LocalDateTime,
        dueBeforeNextClass: Bool = false
    ) -> String {
        let diff = now.date.daysUntil(due.date)
        let dayPart: String
        switch diff {
        case ..<0: dayPart = shortDate(due.date)
        case 0: dayPart = "今天"
        case 1: dayPart = "明天"
        case 2: dayPart = "后天"
        default: dayPart = shortDate(due.date)
        }
        let prefix = dueBeforeNextClass ? "课前 " : ""
        if hasTime {
            return "\(prefix)\(dayPart) \(hhmm(due.time))"
        }
        return "\(prefix)\(dayPart)"
    }
}
