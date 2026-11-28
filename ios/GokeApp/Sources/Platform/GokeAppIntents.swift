import Foundation
import GokeCore
import Observation
import GokeShared
#if canImport(AppIntents)
import AppIntents
#endif

public extension Notification.Name {
    /// Posted when an AppIntent mutates the shared SQLite store so foreground `AppContainer` can reload.
    static let gokeIntentDidMutateData = Notification.Name("app.goke.ios.intentDidMutateData")
    /// Posted when `OpenTabIntent` requests switching to a specific root tab.
    static let gokeIntentDidRequestTab = Notification.Name("app.goke.ios.intentDidRequestTab")
    /// Posted when an iOS Focus Filter updates Goke's active focus constraints.
    static let gokeFocusFilterDidChange = Notification.Name("app.goke.ios.focusFilterDidChange")
}

/// Active iOS Focus Filter state applied by `SetFocusFilterIntent`.
public struct GokeFocusFilterConfiguration: Codable, Sendable, Equatable {
    public var hideNonAcademicTasks: Bool
    public var muteNonUrgentReminders: Bool
    public var focusTag: String?

    public init(
        hideNonAcademicTasks: Bool = false,
        muteNonUrgentReminders: Bool = false,
        focusTag: String? = nil
    ) {
        self.hideNonAcademicTasks = hideNonAcademicTasks
        self.muteNonUrgentReminders = muteNonUrgentReminders
        self.focusTag = focusTag
    }

    public static let inactive = GokeFocusFilterConfiguration()
}

/// Main-actor observable bridge delivering AppIntent tab navigation and Focus Filter state to the SwiftUI app shell.
@MainActor
@Observable
public final class GokeIntentBridge {
    public static let shared = GokeIntentBridge()
    private static let focusStorageKey = "app.goke.ios.focusFilter.v1"

    public var pendingTabRawValue: String?
    public var focusFilter: GokeFocusFilterConfiguration

    private let container: AppGroupContainer

    public init(container: AppGroupContainer = .shared) {
        self.container = container
        if let data = container.userDefaults.data(forKey: Self.focusStorageKey),
           let decoded = try? MintJSON.makeDecoder().decode(GokeFocusFilterConfiguration.self, from: data) {
            self.focusFilter = decoded
        } else {
            self.focusFilter = .inactive
        }
    }

    public func requestTab(_ tabRawValue: String) {
        pendingTabRawValue = tabRawValue
        NotificationCenter.default.post(
            name: .gokeIntentDidRequestTab,
            object: nil,
            userInfo: ["tab": tabRawValue]
        )
    }

    public func consumePendingTab() -> String? {
        let value = pendingTabRawValue
        pendingTabRawValue = nil
        return value
    }

    public func applyFocusFilter(_ config: GokeFocusFilterConfiguration) {
        guard focusFilter != config else { return }
        focusFilter = config
        if let data = try? MintJSON.makeEncoder().encode(config) {
            container.userDefaults.set(data, forKey: Self.focusStorageKey)
        }
        NotificationCenter.default.post(name: .gokeFocusFilterDidChange, object: nil)
    }
}

/// Shared standalone repository helper allowing AppIntents to read/write SQLite safely even in the background.
enum GokeIntentRepositoryHelper {
    static func loadSnapshot(container: AppGroupContainer = .shared) async throws -> (RepositoryActor, AppSnapshot, AppSettings) {
        let store = try SQLiteStore(path: container.databaseURL.path, lowMemory: true)
        let repo = RepositoryActor(store: store, launchCacheURL: container.launchTimetableCacheFileURL)
        let snapshot = try await repo.load()
        let settings: AppSettings
        if let data = container.userDefaults.data(forKey: AppSettingsStore.storageKey),
           let decoded = try? MintJSON.makeDecoder().decode(AppSettings.self, from: data) {
            settings = decoded
        } else {
            settings = .default
        }
        return (repo, snapshot, settings)
    }

    static func commitAndNotify(
        repo: RepositoryActor,
        changeSet: ChangeSet,
        settings: AppSettings,
        container: AppGroupContainer = .shared
    ) async throws {
        try await repo.commit(changeSet, record: true)
        await repo.flushLaunchCacheNow()
        let updated = await repo.snapshot
        try? container.writeWidgetSnapshot(updated, settings: settings, reloadTimelines: true)
        NotificationCenter.default.post(name: .gokeIntentDidMutateData, object: nil)
    }
}

#if canImport(AppIntents)

// MARK: - Root Tab Enum

public enum GokeAppTab: String, AppEnum, Sendable, CaseIterable {
    case today
    case timetable
    case calendar
    case todo
    case me

    public static let typeDisplayRepresentation: TypeDisplayRepresentation = "Goke 页面"

    public static let caseDisplayRepresentations: [GokeAppTab: DisplayRepresentation] = [
        .today: DisplayRepresentation(title: "今日", image: .init(systemName: "sun.max")),
        .timetable: DisplayRepresentation(title: "课表", image: .init(systemName: "calendar.day.timeline.left")),
        .calendar: DisplayRepresentation(title: "日历", image: .init(systemName: "calendar")),
        .todo: DisplayRepresentation(title: "待办", image: .init(systemName: "checklist")),
        .me: DisplayRepresentation(title: "我的", image: .init(systemName: "person")),
    ]
}

// MARK: - 1. AddTaskIntent

public struct AddTaskIntent: AppIntent {
    public static let title: LocalizedStringResource = "新建待办"
    public static let description: IntentDescription? = IntentDescription("快速向 Goke 添加一条新的待办事项。")
    public static let openAppWhenRun: Bool = false

    @Parameter(title: "任务标题")
    public var title: String

    @Parameter(title: "截止时间")
    public var dueDate: Date?

    @Parameter(title: "优先级 (0-3)", default: 0)
    public var priority: Int

    @Parameter(title: "备注")
    public var note: String?

    public init() {}

    public init(title: String, dueDate: Date? = nil, priority: Int = 0, note: String? = nil) {
        self.title = title
        self.dueDate = dueDate
        self.priority = priority
        self.note = note
    }

    public func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return .result(value: "", dialog: "请提供待办标题。")
        }

        let (repo, _, settings) = try await GokeIntentRepositoryHelper.loadSnapshot()
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let localDue = dueDate.map { LocalDateTime.fromDate($0) }
        let clampedPriority = min(max(priority, 0), 3)

        let task = GokeTask(
            title: trimmed,
            note: note?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            priority: clampedPriority,
            due: localDue,
            dueHasTime: localDue != nil,
            source: .MANUAL,
            updatedAt: nowMillis
        )
        let cs = ChangeSet(
            description: "新建待办：\(trimmed)",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.add(after: .task(task))]
        )
        try await GokeIntentRepositoryHelper.commitAndNotify(repo: repo, changeSet: cs, settings: settings)
        return .result(value: task.id, dialog: "已添加到 Goke：\(trimmed)")
    }
}

// MARK: - 3. NextCourseIntent

public struct NextCourseIntent: AppIntent {
    public static let title: LocalizedStringResource = "查看下一节课"
    public static let description: IntentDescription? = IntentDescription("查询 Goke 课表中当前或接下来最近的一节课程。")
    public static let openAppWhenRun: Bool = false

    public init() {}

    public func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        let (_, snapshot, _) = try await GokeIntentRepositoryHelper.loadSnapshot()
        let now = LocalDateTime.now()
        guard let nextUp = NextUps.find(snapshot: snapshot, now: now, horizonDays: 7) else {
            return .result(value: "", dialog: "未来 7 天内没有排课。")
        }

        let occ = nextUp.occurrence
        let isOngoing = occ.start <= now && occ.end > now
        let prefix = isOngoing ? "正在上课" : "下一节课"
        let dayLabel = occ.date == now.date
            ? "今天"
            : (occ.date == now.date.addingDays(1) ? "明天" : occ.date.description)
        let roomPart = occ.room.isEmpty ? "" : "，教室 \(occ.room)"
        let taskPart = nextUp.tasks.isEmpty ? "" : "，有 \(nextUp.tasks.count) 项课程待办"
        let summary = "\(prefix)：\(occ.course.name)（\(dayLabel) \(occ.start.time.description)–\(occ.end.time.description)\(roomPart)\(taskPart)）"
        return .result(value: summary, dialog: "\(summary)")
    }
}

// MARK: - 4. OpenTabIntent

public struct OpenTabIntent: AppIntent {
    public static let title: LocalizedStringResource = "打开 Goke 页面"
    public static let description: IntentDescription? = IntentDescription("启动 Goke 并直接跳转到指定标签页。")
    public static let openAppWhenRun: Bool = true

    @Parameter(title: "页面", default: .today)
    public var tab: GokeAppTab

    public init() {}

    public init(tab: GokeAppTab) {
        self.tab = tab
    }

    public func perform() async throws -> some IntentResult {
        await MainActor.run {
            GokeIntentBridge.shared.requestTab(tab.rawValue)
        }
        return .result()
    }
}

// MARK: - 5. SetFocusFilterIntent (iOS Focus Filter)

public struct SetFocusFilterIntent: AppIntents.SetFocusFilterIntent {
    public static let title: LocalizedStringResource = "设置 Goke 专注过滤"
    public static let description: IntentDescription? = IntentDescription("在开启系统专注模式时，过滤非学业待办或静音非紧急提醒。")

    @Parameter(title: "仅显示课程与学业相关待办", default: false)
    public var hideNonAcademicTasks: Bool

    @Parameter(title: "静音非紧急通知", default: false)
    public var muteNonUrgentReminders: Bool

    @Parameter(title: "聚焦标签（可选）")
    public var focusTag: String?

    public var displayRepresentation: DisplayRepresentation {
        var parts: [String] = []
        if hideNonAcademicTasks { parts.append("仅学业待办") }
        if muteNonUrgentReminders { parts.append("仅紧急提醒") }
        if let tag = focusTag?.trimmingCharacters(in: .whitespacesAndNewlines), !tag.isEmpty {
            parts.append("#\(tag)")
        }
        let subtitle = parts.isEmpty ? "默认视图" : parts.joined(separator: " · ")
        return DisplayRepresentation(title: "Goke 专注过滤", subtitle: "\(subtitle)")
    }

    public init() {}

    public func perform() async throws -> some IntentResult {
        let normalizedTag = focusTag?.trimmingCharacters(in: .whitespacesAndNewlines)
        let config = GokeFocusFilterConfiguration(
            hideNonAcademicTasks: hideNonAcademicTasks,
            muteNonUrgentReminders: muteNonUrgentReminders,
            focusTag: normalizedTag?.isEmpty == false ? normalizedTag : nil
        )
        await MainActor.run {
            GokeIntentBridge.shared.applyFocusFilter(config)
        }
        return .result()
    }
}

// MARK: - 6. GokeShortcutsProvider

public struct GokeShortcutsProvider: AppShortcutsProvider {
    public static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NextCourseIntent(),
            phrases: [
                "用\(.applicationName)看下一节课",
                "查一下\(.applicationName)下节课",
                "Next class in \(.applicationName)",
            ],
            shortTitle: "下一节课",
            systemImageName: "book.closed"
        )
        AppShortcut(
            intent: AddTaskIntent(),
            phrases: [
                "在\(.applicationName)新建待办",
                "用\(.applicationName)记一个任务",
                "Add task in \(.applicationName)",
            ],
            shortTitle: "新建待办",
            systemImageName: "plus.circle"
        )
        AppShortcut(
            intent: OpenTabIntent(),
            phrases: [
                "打开\(.applicationName)今日日程",
                "Open \(.applicationName)",
            ],
            shortTitle: "打开 Goke",
            systemImageName: "calendar"
        )
    }
}
#endif
