import Foundation
import GokeCore
import Observation
import SwiftUI

/// Persistent root tabs matching Android's 5-tab navigation shell.
enum GokeTab: String, CaseIterable, Identifiable, Hashable, Sendable {
    case today
    case timetable
    case calendar
    case todo
    case me

    var id: String { rawValue }

    var title: String {
        switch self {
        case .today: "今日"
        case .timetable: "课表"
        case .calendar: "日历"
        case .todo: "待办"
        case .me: "我的"
        }
    }

    var systemImage: String {
        switch self {
        case .today: "sun.max"
        case .timetable: "tablecells"
        case .calendar: "calendar"
        case .todo: "checkmark.circle"
        case .me: "person"
        }
    }
}

/// Typed navigation destinations pushed onto each tab's `NavigationPath`.
enum GokeRoute: Hashable, Sendable {
    case editTimetable
    case periods(semesterId: String? = nil)
    case courseDetail(courseId: String, zoomSourceId: String? = nil)
    case courseEdit(courseId: String? = nil)
    case todoEdit(
        taskId: String? = nil,
        initialTitle: String? = nil,
        initialDate: LocalDate? = nil,
        courseId: String? = nil
    )
    case eventEdit(eventId: String? = nil, initialDate: LocalDate? = nil)
    case jxfw
    case jxfwNotices
    case courseSelection
    case grades
    case physicalTest
    case classrooms
    case campusResources(initialTab: Int = 0, initialQuery: String = "", initialCode: String = "")
    case hotWater(autoToggle: Bool = false)
    case hotWaterBills
    case sync
    case calendarSync
    case dataManagement
    case importFile(url: URL? = nil)
    case trash
    case history
    case reliability
    case appearance
    case preferences
    case reminders
    case widgetsGallery
    case about
}

/// Modal sheets presented from the root shell regardless of active tab.
enum GokeSheet: Identifiable, Hashable, Sendable {
    case quickAdd(initialText: String? = nil, initialDate: LocalDate? = nil)
    case batchAdd(lines: [String])
    case courseEdit(courseId: String? = nil)
    case todoEdit(
        taskId: String? = nil,
        initialTitle: String? = nil,
        initialDate: LocalDate? = nil,
        courseId: String? = nil
    )
    case eventEdit(eventId: String? = nil, initialDate: LocalDate? = nil)
    case morningTidy
    case rescheduleTask(taskId: String)
    case importFile(url: URL)
    case timetableExport

    var id: String {
        switch self {
        case .quickAdd(let text, let date):
            "quickAdd:\(text ?? ""):\(date?.description ?? "")"
        case .batchAdd(let lines):
            "batchAdd:\(lines.count):\(lines.first ?? "")"
        case .courseEdit(let courseId):
            "courseEdit:\(courseId ?? "new")"
        case .todoEdit(let taskId, let title, let date, let courseId):
            "todoEdit:\(taskId ?? "new"):\(title ?? ""):\(date?.description ?? ""):\(courseId ?? "")"
        case .eventEdit(let eventId, let date):
            "eventEdit:\(eventId ?? "new"):\(date?.description ?? "")"
        case .morningTidy:
            "morningTidy"
        case .rescheduleTask(let taskId):
            "rescheduleTask:\(taskId)"
        case .importFile(let url):
            "importFile:\(url.absoluteString)"
        case .timetableExport:
            "timetableExport"
        }
    }
}

/// Optional placement context carried alongside `GokeSheet.quickAdd` without changing
/// the `.quickAdd(initialText:initialDate:)` enum arity.
struct QuickAddContext: Equatable, Hashable, Sendable {
    var defaultStartDate: LocalDate?
    var defaultDueDate: LocalDate?
    var defaultBucket: TaskBucket?
    var defaultListId: String?

    init(
        defaultStartDate: LocalDate? = nil,
        defaultDueDate: LocalDate? = nil,
        defaultBucket: TaskBucket? = nil,
        defaultListId: String? = nil
    ) {
        self.defaultStartDate = defaultStartDate
        self.defaultDueDate = defaultDueDate
        self.defaultBucket = defaultBucket
        self.defaultListId = defaultListId
    }
}

/// Main-actor observable navigation router owning the 5 root tabs, per-tab `NavigationPath`s,
/// global modal sheet presentation, `goke://` deep-link routing, and `GokeIntentBridge` observation.
@MainActor
@Observable
public final class AppRouter {
    var selectedTab: GokeTab

    var todayPath = NavigationPath()
    var timetablePath = NavigationPath()
    var calendarPath = NavigationPath()
    var todoPath = NavigationPath()
    var mePath = NavigationPath()

    var activeSheet: GokeSheet?
    var quickAddContext: QuickAddContext?

    /// Incremented whenever the user re-taps an already-selected root tab at depth 0 (scroll-to-top / jump-to-today).
    private(set) var reselectCounts: [GokeTab: Int] = [:]

    /// Deep-link hints consumed by Timetable / Calendar screens when opened with explicit query parameters.
    var requestedTimetableWeek: Int?
    var requestedCalendarDate: LocalDate?

    /// Course ID handed off to `TimetableView` to enter cross-week grid reschedule-picking mode.
    var pendingRescheduleCourseId: String?

    @ObservationIgnored private nonisolated(unsafe) var intentObserverTask: Task<Void, Never>?

    init(initialTab: GokeTab = .timetable) {
        self.selectedTab = initialTab
    }

    deinit {
        intentObserverTask?.cancel()
    }

    // MARK: - Tab & Path Operations

    func path(for tab: GokeTab) -> NavigationPath {
        switch tab {
        case .today: todayPath
        case .timetable: timetablePath
        case .calendar: calendarPath
        case .todo: todoPath
        case .me: mePath
        }
    }

    func setPath(_ path: NavigationPath, for tab: GokeTab) {
        switch tab {
        case .today: todayPath = path
        case .timetable: timetablePath = path
        case .calendar: calendarPath = path
        case .todo: todoPath = path
        case .me: mePath = path
        }
    }

    func pathBinding(for tab: GokeTab) -> Binding<NavigationPath> {
        Binding(
            get: { self.path(for: tab) },
            set: { self.setPath($0, for: tab) }
        )
    }

    func isAtRoot(_ tab: GokeTab) -> Bool {
        path(for: tab).isEmpty
    }

    func reselectCount(for tab: GokeTab) -> Int {
        reselectCounts[tab, default: 0]
    }

    func selectTab(_ tab: GokeTab, popToRoot shouldPopToRoot: Bool = false) {
        if selectedTab == tab {
            if !isAtRoot(tab) {
                popToRoot(in: tab)
            } else {
                reselectCounts[tab, default: 0] += 1
            }
            return
        }
        selectedTab = tab
        if shouldPopToRoot {
            popToRoot(in: tab)
        }
    }

    func handleTabReselect(_ tab: GokeTab) {
        if !isAtRoot(tab) {
            popToRoot(in: tab)
        } else {
            reselectCounts[tab, default: 0] += 1
        }
    }

    func push(_ route: GokeRoute, in tab: GokeTab? = nil) {
        let targetTab = tab ?? selectedTab
        if selectedTab != targetTab {
            selectedTab = targetTab
        }
        var current = path(for: targetTab)
        current.append(route)
        setPath(current, for: targetTab)
    }

    /// Pushes `CourseDetailView` onto `tab` (or `selectedTab`) with an optional disambiguated
    /// `zoomSourceId` for `.matchedTransitionSource` -> `.navigationTransition(.zoom)` handoff.
    func pushCourseDetail(id: String, zoomSourceId: String? = nil, in tab: GokeTab? = nil) {
        push(.courseDetail(courseId: id, zoomSourceId: zoomSourceId), in: tab)
    }

    func pop(in tab: GokeTab? = nil) {
        let targetTab = tab ?? selectedTab
        var current = path(for: targetTab)
        guard !current.isEmpty else { return }
        current.removeLast()
        setPath(current, for: targetTab)
    }

    func popToRoot(in tab: GokeTab? = nil) {
        let targetTab = tab ?? selectedTab
        var current = path(for: targetTab)
        guard !current.isEmpty else { return }
        current.removeLast(current.count)
        setPath(current, for: targetTab)
    }

    /// Switches to the Timetable tab (popping to root so the grid is visible) and queues `courseId`
    /// for interactive cross-week reschedule slot picking.
    func startTimetableReschedulePicking(courseId: String) {
        pendingRescheduleCourseId = courseId
        activeSheet = nil
        selectTab(.timetable, popToRoot: true)
    }

    /// Consumes and clears `pendingRescheduleCourseId` once `TimetableView` enters picking mode.
    @discardableResult
    func consumePendingRescheduleCourseId() -> String? {
        let id = pendingRescheduleCourseId
        pendingRescheduleCourseId = nil
        return id
    }

    // MARK: - Sheet Presentation

    func present(_ sheet: GokeSheet) {
        quickAddContext = nil
        activeSheet = sheet
    }

    /// Presents the keyboard-docked QuickAdd overlay with rich default placement context.
    func presentQuickAdd(
        initialText: String? = nil,
        anchorDate: LocalDate? = nil,
        defaultStartDate: LocalDate? = nil,
        defaultDueDate: LocalDate? = nil,
        defaultBucket: TaskBucket? = nil,
        defaultListId: String? = nil
    ) {
        quickAddContext = QuickAddContext(
            defaultStartDate: defaultStartDate,
            defaultDueDate: defaultDueDate,
            defaultBucket: defaultBucket,
            defaultListId: defaultListId
        )
        activeSheet = .quickAdd(
            initialText: initialText,
            initialDate: defaultDueDate ?? anchorDate ?? defaultStartDate
        )
    }

    func dismissSheet() {
        activeSheet = nil
        quickAddContext = nil
    }

    /// Active `.quickAdd` sheet, hosted in `GokeOverlaySheetContainer` instead of a system modal `.sheet`.
    var activeQuickAddSheet: GokeSheet? {
        if case .quickAdd = activeSheet { return activeSheet }
        return nil
    }

    var sheetBinding: Binding<GokeSheet?> {
        Binding(
            get: { self.activeSheet },
            set: { next in
                self.activeSheet = next
                if next == nil {
                    self.quickAddContext = nil
                }
            }
        )
    }

    /// Modal `.sheet` binding excluding `.quickAdd` (which uses the keyboard-docked overlay host).
    var modalSheetBinding: Binding<GokeSheet?> {
        Binding(
            get: {
                guard let sheet = self.activeSheet else { return nil }
                if case .quickAdd = sheet { return nil }
                return sheet
            },
            set: { next in
                if next == nil {
                    if case .quickAdd = self.activeSheet {
                        return
                    }
                    self.activeSheet = nil
                    self.quickAddContext = nil
                } else {
                    self.quickAddContext = nil
                    self.activeSheet = next
                }
            }
        )
    }

    // MARK: - Deep-Link & Shortcut Routing

    /// Routes `goke://` custom URLs and local file URLs (`.ics`, `.json`, `.xlsx`) to the right tab, route, or sheet.
    @discardableResult
    func handleDeepLink(_ url: URL) -> Bool {
        if url.isFileURL {
            present(.importFile(url: url))
            return true
        }

        guard url.scheme?.lowercased() == "goke" else {
            return false
        }

        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let host = (url.host ?? "").lowercased()
        let pathSegments = url.pathComponents.filter { $0 != "/" }

        func queryValue(_ name: String) -> String? {
            components?.queryItems?
                .first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame })?
                .value
        }

        let targetId = queryValue("id") ?? pathSegments.first

        switch host {
        case "today", "":
            selectTab(.today, popToRoot: true)
            return true

        case "timetable", "schedule":
            selectTab(.timetable, popToRoot: true)
            if let weekStr = queryValue("week"), let week = Int(weekStr), week > 0 {
                requestedTimetableWeek = week
            }
            return true

        case "calendar":
            selectTab(.calendar, popToRoot: true)
            if let dateStr = queryValue("date"), let date = LocalDate.parse(dateStr) {
                requestedCalendarDate = date
            }
            return true

        case "todo":
            selectTab(.todo, popToRoot: true)
            if let id = targetId, !id.isEmpty {
                if id.lowercased() == "new" {
                    let parsedDate = queryValue("date").flatMap(LocalDate.parse)
                    present(.todoEdit(
                        taskId: nil,
                        initialTitle: queryValue("title"),
                        initialDate: parsedDate,
                        courseId: queryValue("courseId")
                    ))
                } else {
                    present(.todoEdit(taskId: id))
                }
            }
            return true

        case "task":
            selectTab(.todo, popToRoot: false)
            if let id = targetId, !id.isEmpty {
                if id.lowercased() == "new" {
                    let parsedDate = queryValue("date").flatMap(LocalDate.parse)
                    present(.todoEdit(
                        taskId: nil,
                        initialTitle: queryValue("title"),
                        initialDate: parsedDate,
                        courseId: queryValue("courseId")
                    ))
                } else {
                    present(.todoEdit(taskId: id))
                }
            } else {
                present(.quickAdd())
            }
            return true

        case "course":
            selectTab(.timetable, popToRoot: false)
            if let id = targetId, !id.isEmpty {
                push(.courseDetail(courseId: id), in: .timetable)
            }
            return true

        case "event":
            selectTab(.calendar, popToRoot: false)
            present(.eventEdit(eventId: targetId))
            return true

        case "add", "quickadd":
            let parsedDate = queryValue("date").flatMap(LocalDate.parse)
            if let rawText = queryValue("text") {
                let rawLines = rawText
                    .components(separatedBy: .newlines)
                    .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                if rawLines.count > 1 {
                    selectTab(.todo, popToRoot: false)
                    present(.batchAdd(lines: rawLines))
                } else {
                    present(.quickAdd(
                        initialText: rawLines.first?.trimmingCharacters(in: .whitespacesAndNewlines),
                        initialDate: parsedDate
                    ))
                }
            } else {
                present(.quickAdd(initialDate: parsedDate))
            }
            return true

        case "jxfw":
            selectTab(.me, popToRoot: true)
            push(.jxfw, in: .me)
            return true

        case "notices", "jxfwnotices":
            selectTab(.me, popToRoot: true)
            push(.jxfwNotices, in: .me)
            return true

        case "droprelay", "resources", "campusresources":
            selectTab(.me, popToRoot: true)
            let code = queryValue("code") ?? ""
            let query = queryValue("q") ?? queryValue("query") ?? ""
            let tab = Int(queryValue("tab") ?? "") ?? (code.isEmpty ? 0 : 1)
            push(.campusResources(initialTab: tab, initialQuery: query, initialCode: code), in: .me)
            return true

        case "xsxk", "courseselection":
            selectTab(.me, popToRoot: true)
            push(.courseSelection, in: .me)
            return true

        case "grades":
            selectTab(.me, popToRoot: true)
            push(.grades, in: .me)
            return true

        case "tzcs", "physicaltest":
            selectTab(.me, popToRoot: true)
            push(.physicalTest, in: .me)
            return true

        case "classrooms", "jwcwx":
            selectTab(.me, popToRoot: true)
            push(.classrooms, in: .me)
            return true

        case "hotwater":
            let wasOnMeWithStack = selectedTab == .me && !mePath.isEmpty
            selectTab(.me, popToRoot: true)
            let isBills = pathSegments.first?.lowercased() == "bills"
            let shouldToggle = queryValue("action")?.lowercased() == "toggle"
            let applyRoutes = { @MainActor [weak self] in
                guard let self else { return }
                if isBills {
                    self.push(.hotWater(autoToggle: false), in: .me)
                    self.push(.hotWaterBills, in: .me)
                } else {
                    self.push(.hotWater(autoToggle: shouldToggle), in: .me)
                }
            }
            if wasOnMeWithStack {
                Task { @MainActor in
                    applyRoutes()
                }
            } else {
                applyRoutes()
            }
            return true

        case "sync":
            selectTab(.me, popToRoot: true)
            push(.sync, in: .me)
            return true

        case "widgets":
            selectTab(.me, popToRoot: true)
            push(.widgetsGallery, in: .me)
            return true

        case "appearance":
            selectTab(.me, popToRoot: true)
            push(.appearance, in: .me)
            return true

        case "preferences", "prefs":
            selectTab(.me, popToRoot: true)
            push(.preferences, in: .me)
            return true

        case "me", "settings":
            selectTab(.me, popToRoot: true)
            return true

        default:
            return false
        }
    }

    /// Handles iOS Home Screen Quick Action shortcut item identifiers declared in `Info.plist`.
    @discardableResult
    func handleShortcutItemType(_ type: String) -> Bool {
        switch type {
        case "app.goke.ios.shortcut.add":
            present(.quickAdd())
            return true
        case "app.goke.ios.shortcut.today":
            selectTab(.today, popToRoot: true)
            return true
        default:
            return false
        }
    }

    // MARK: - AppIntents Bridge Observation

    /// Consumes any queued tab request from `GokeIntentBridge` and observes future `.gokeIntentDidRequestTab` posts.
    func startObservingIntentBridge(_ bridge: GokeIntentBridge = .shared) {
        consumePendingIntentTab(from: bridge)
        intentObserverTask?.cancel()
        intentObserverTask = Task.detached(priority: .userInitiated) { [weak self, weak bridge] in
            for await _ in NotificationCenter.default.notifications(named: .gokeIntentDidRequestTab) {
                guard !Task.isCancelled else { break }
                await MainActor.run {
                    if let bridge {
                        self?.consumePendingIntentTab(from: bridge)
                    }
                }
            }
        }
    }

    func stopObservingIntentBridge() {
        intentObserverTask?.cancel()
        intentObserverTask = nil
    }

    func consumePendingIntentTab(from bridge: GokeIntentBridge = .shared) {
        guard let rawTab = bridge.consumePendingTab() else { return }
        applyIntentTab(rawTab)
    }

    private func applyIntentTab(_ rawValue: String) {
        guard let tab = GokeTab(rawValue: rawValue.lowercased()) else { return }
        selectTab(tab, popToRoot: false)
    }
}

// MARK: - Zoom Transition Environment Key

private struct GokeZoomNamespaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    /// Per-tab `NavigationStack` namespace used by `.matchedTransitionSource` and `.navigationTransition(.zoom)`.
    var gokeZoomNamespace: Namespace.ID? {
        get { self[GokeZoomNamespaceKey.self] }
        set { self[GokeZoomNamespaceKey.self] = newValue }
    }
}
