import AppIntents
import Foundation
import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

// MARK: - Interactive Toggle Task AppIntent

/// Interactive WidgetKit `AppIntent` that toggles a `GokeTask`'s completion state
/// directly from the Home Screen via the shared App Group SQLite database using low-memory mode.
struct ToggleWidgetTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "切换待办完成状态"
    static let description: IntentDescription? = IntentDescription("在桌面小组件中直接勾选或取消完成待办。")
    static let openAppWhenRun: Bool = false

    @Parameter(title: "任务 ID")
    var taskId: String

    init() {
        self.taskId = ""
    }

    init(taskId: String) {
        self.taskId = taskId
    }

    func perform() async throws -> some IntentResult {
        guard !taskId.isEmpty else { return .result() }
        let container = AppGroupContainer.shared
        // Open SQLite with `lowMemory: true` (mmap_size=0, minimal page cache) so we stay safely
        // under iOS's 30 MB WidgetExtension Jetsam cap, and keep LaunchTimetableCache in sync.
        let store = try SQLiteStore(path: container.databaseURL.path, deferOpen: false, lowMemory: true)
        let repo = RepositoryActor(
            store: store,
            launchCacheURL: container.launchTimetableCacheFileURL
        )
        let snapshot = try await repo.load()

        guard let target = snapshot.live().tasks.first(where: { $0.id == taskId }) else {
            return .result()
        }

        let now = LocalDateTime.now()
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []

        if target.done {
            let reopened = TaskLogic.reopen(target, nowMillis: nowMillis)
            changes.append(.modify(before: .task(target), after: .task(reopened)))
        } else {
            let (finished, next) = TaskLogic.complete(target, nowMillis: nowMillis, now: now)
            changes.append(.modify(before: .task(target), after: .task(finished)))
            if let next {
                changes.append(.add(after: .task(next)))
            }
        }

        let cs = ChangeSet(
            description: target.done ? "小组件恢复待办 \(target.title)" : "小组件完成待办 \(target.title)",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        try await repo.commit(cs, record: true)
        let updated = await repo.snapshot
        let settings = container.readWidgetSnapshot()?.settings ?? .default
        try? container.writeWidgetSnapshot(updated, settings: settings, reloadTimelines: true)
        return .result()
    }
}

// MARK: - Todo Checklist Widget

/// Minimalist Mint Paper Todo Checklist Widget (Medium / Large):
/// Pure paper surface with crisp 24pt rounded-square `MintCheck` targets and zero row-box clutter.
struct TodoChecklistWidget: Widget {
    static let kind = "app.goke.widget.todoChecklist"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: WidgetSnapshotProvider()) { entry in
            TodoChecklistWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.theme.paper
                }
        }
        .contentMarginsDisabled()
        .configurationDisplayName("待办清单")
        .description("在桌面直接勾选完成待办，或轻点进入详情。")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - View

private struct TodoChecklistWidgetView: View {
    let entry: GokeWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode

    private var theme: GokeTheme { entry.theme }
    private var isAccented: Bool { WidgetFormat.isAccentedMode(renderingMode) }

    var body: some View {
        let tasks = entry.checklistTasks
        let isLarge = family == .systemLarge
        // Medium (141pt): 24 hdr + 6 gap + 4×24 + 3×4 = 138pt <= 141pt.
        // Large (348pt): up to 10 rows (24 + 8 + 10×24 + 9×6 = 326pt <= 348pt), or 9 rows + overflow footer (319pt).
        let showOverflowFooter = isLarge && tasks.count > 10
        let maxRows = isLarge ? (showOverflowFooter ? 9 : 10) : 4
        let visible = Array(tasks.prefix(maxRows))
        let hiddenCount = max(0, tasks.count - visible.count)

        VStack(alignment: .leading, spacing: 0) {
            // Quiet editorial header
            HStack(alignment: .center, spacing: 6) {
                Text("待办")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(isAccented ? Color.primary : theme.ink)
                if !tasks.isEmpty {
                    Text("\(tasks.count)")
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                }
                Spacer(minLength: 0)
                WidgetQuickAddButton(theme: theme, isAccented: isAccented)
            }

            Spacer().frame(height: isLarge ? 8 : 6)

            if visible.isEmpty {
                Spacer(minLength: 0)
                Text("暂无待办")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.bottom, 12)
                Spacer(minLength: 0)
            } else {
                VStack(alignment: .leading, spacing: isLarge ? 6 : 4) {
                    ForEach(visible) { task in
                        interactiveTaskRow(task)
                    }
                }
                if showOverflowFooter, hiddenCount > 0 {
                    Text("+\(hiddenCount) 项未显示")
                        .font(.system(size: 11, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .padding(.top, 8)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(WidgetDeepLink.todo)
    }

    private func interactiveTaskRow(_ task: GokeTask) -> some View {
        let overdue = TaskLogic.isOverdue(task, now: entry.localNow)

        return HStack(spacing: 4) {
            WidgetMintCheckButton(
                task: task,
                overdue: overdue,
                theme: theme,
                isAccented: isAccented
            )
            .padding(.leading, WidgetMetrics.checkOpticalLeading)

            Link(destination: WidgetDeepLink.todoItem(task.id)) {
                HStack(spacing: 6) {
                    Text(task.title)
                        .font(.system(size: WidgetMetrics.taskTitleSize, weight: .medium))
                        .foregroundStyle(isAccented ? Color.primary : theme.ink)
                        .lineLimit(1)
                        .layoutPriority(1)
                    Spacer(minLength: 4)
                    if let due = task.due {
                        let label = task.dueHasTime && due.date == entry.localNow.date
                            ? WidgetFormat.hhmm(due.time)
                            : WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: due.date)
                        Text(label)
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(
                                isAccented
                                    ? (overdue ? Color.primary : Color.secondary)
                                    : (overdue ? theme.error : theme.muted)
                            )
                            .lineLimit(1)
                            .widgetAccentable(overdue)
                    }
                }
                .frame(minHeight: WidgetMetrics.checkButtonHitSize)
                .contentShape(Rectangle())
            }
        }
    }
}
