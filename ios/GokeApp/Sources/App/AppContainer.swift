import Foundation
import GokeAdapterLoader
import GokeCore
import Observation
import GokeShared
#if canImport(BackgroundTasks)
import BackgroundTasks
#endif
#if canImport(UIKit)
import UIKit
#endif
#if canImport(UserNotifications)
import UserNotifications
#endif

// MARK: - Keychain-Backed SyncStateStore

/// `GokeCore.SyncStateStore` bridge storing secret Bearer credentials exclusively in iOS Keychain
/// and non-secret sync cursor metadata in `AppGroupContainer.userDefaults`.
actor KeychainSyncStateStore: SyncStateStore {
    static let cursorStorageKey = "app.goke.ios.sync.cursorState.v1"
    private static let roleKeychainKey: KeychainKey = .custom("sync.role")

    private let keychain: KeychainStore
    private let appGroup: AppGroupContainer
    private let cursorKey: String

    init(
        keychain: KeychainStore = .shared,
        appGroup: AppGroupContainer = .shared,
        cursorKey: String = KeychainSyncStateStore.cursorStorageKey
    ) {
        self.keychain = keychain
        self.appGroup = appGroup
        self.cursorKey = cursorKey
    }

    func loadCredentials() async -> SyncCredentials? {
        guard let token = (try? await keychain.get(.syncBearerToken)) ?? nil,
              !token.isEmpty,
              let serverURL = (try? await keychain.get(.syncServerURL)) ?? nil,
              !serverURL.isEmpty,
              let username = (try? await keychain.get(.syncUsername)) ?? nil,
              !username.isEmpty
        else {
            return nil
        }
        let role = ((try? await keychain.get(Self.roleKeychainKey)) ?? nil) ?? "user"
        return SyncCredentials(
            account: SyncAccount(serverURL: serverURL, username: username, role: role),
            token: token
        )
    }

    func saveCredentials(_ credentials: SyncCredentials?) async {
        if let credentials {
            try? await keychain.set(credentials.token, for: .syncBearerToken)
            try? await keychain.set(credentials.account.serverURL, for: .syncServerURL)
            try? await keychain.set(credentials.account.username, for: .syncUsername)
            try? await keychain.set(credentials.account.role, for: Self.roleKeychainKey)
        } else {
            try? await keychain.delete(.syncBearerToken)
            try? await keychain.delete(.syncServerURL)
            try? await keychain.delete(.syncUsername)
            try? await keychain.delete(Self.roleKeychainKey)
        }
    }

    func loadState() async -> SyncCursorState {
        guard let data = appGroup.userDefaults.data(forKey: cursorKey),
              let decoded = try? MintJSON.makeDecoder().decode(SyncCursorState.self, from: data)
        else {
            return SyncCursorState()
        }
        return decoded
    }

    func saveState(_ state: SyncCursorState) async {
        guard let data = try? MintJSON.makeEncoder().encode(state) else { return }
        appGroup.userDefaults.set(data, forKey: cursorKey)
    }
}

// MARK: - SyncStateCoordinator

/// `@MainActor @Observable` coordinator wrapping `GokeCore.SyncEngine` for UI binding,
/// debounced post-commit sync, and remote-update side-effect notifications.
@MainActor
@Observable
final class SyncStateCoordinator {
    static let defaultDebounceDelay: Duration = .seconds(4)

    @ObservationIgnored let engine: SyncEngine
    @ObservationIgnored private let repository: RepositoryActor

    private(set) var account: SyncAccount?
    private(set) var cursorState: SyncCursorState = SyncCursorState()
    private(set) var isSyncing: Bool = false
    private(set) var lastOutcome: SyncOutcome?
    private(set) var lastError: String?

    var isSignedIn: Bool { account != nil }
    var needsRelogin: Bool { cursorState.needsRelogin }

    /// Invoked on `@MainActor` whenever a sync pass applies remote changes into `RepositoryActor`.
    @ObservationIgnored var onRemoteDataApplied: (@MainActor (AppSnapshot) -> Void)?

    @ObservationIgnored private nonisolated(unsafe) var debounceTask: Task<Void, Never>?

    init(engine: SyncEngine, repository: RepositoryActor) {
        self.engine = engine
        self.repository = repository
    }

    deinit {
        debounceTask?.cancel()
    }

    /// Loads persisted credentials and cursor state from `SyncEngine` and configures purge tracking.
    func refreshState() async {
        let creds = await engine.currentCredentials()
        let state = await engine.currentState()
        account = creds?.account
        cursorState = state
        lastError = state.lastError
        await repository.setTrackPurges(creds != nil)
    }

    /// Schedules a debounced silent sync after local edits (default: 4 seconds).
    func scheduleDebouncedSync(delay: Duration = SyncStateCoordinator.defaultDebounceDelay) {
        guard isSignedIn, !needsRelogin else { return }
        debounceTask?.cancel()
        debounceTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: delay)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            _ = await self.syncNow(silent: true)
        }
    }

    func cancelDebouncedSync() {
        debounceTask?.cancel()
        debounceTask = nil
    }

    /// Signs into a self-hosted Goke server, persists credentials to Keychain, and runs initial sync.
    @discardableResult
    func login(
        serverURL: String,
        username: String,
        password: String,
        deviceName: String
    ) async throws -> SyncOutcome {
        cancelDebouncedSync()
        isSyncing = true
        lastError = nil
        defer { isSyncing = false }

        do {
            let outcome = try await engine.login(
                serverURL: serverURL,
                username: username,
                password: password,
                deviceName: deviceName
            )
            await syncPostRun(outcome: outcome)
            return outcome
        } catch {
            await refreshState()
            lastError = error.localizedDescription
            throw error
        }
    }

    /// Revokes the remote token (best-effort), clears local Keychain credentials, and keeps local SQLite data.
    func logout() async {
        cancelDebouncedSync()
        await engine.logout()
        account = nil
        cursorState = SyncCursorState()
        lastOutcome = nil
        lastError = nil
    }

    func changePassword(oldPassword: String, newPassword: String) async throws {
        try await engine.changePassword(oldPassword: oldPassword, newPassword: newPassword)
    }

    /// Runs a Pull-then-Push LWW sync cycle, returning `nil` on failure (recording `lastError`).
    @discardableResult
    func syncNow(silent: Bool = false) async -> SyncOutcome? {
        do {
            return try await runSync(silent: silent)
        } catch {
            if !silent {
                lastError = error.localizedDescription
            }
            return nil
        }
    }

    /// Runs a Pull-then-Push LWW sync cycle, rethrowing `SyncError` for explicit UI feedback.
    @discardableResult
    func syncNowThrowing() async throws -> SyncOutcome {
        try await runSync(silent: false)
    }

    private func runSync(silent: Bool) async throws -> SyncOutcome {
        guard isSignedIn else {
            throw SyncError.notLoggedIn
        }
        cancelDebouncedSync()
        isSyncing = true
        lastError = nil
        defer { isSyncing = false }

        do {
            let outcome = try await engine.syncNow()
            await syncPostRun(outcome: outcome)
            if !silent {
                GokeFeedback.play(.success)
            }
            return outcome
        } catch {
            await refreshState()
            lastError = error.localizedDescription
            throw error
        }
    }

    func cloudMCPEndpoint() async -> String? {
        await engine.cloudMCPEndpoint()
    }

    func cloudMCPConfigJSON() async -> String? {
        await engine.cloudMCPConfigJSON()
    }

    private func syncPostRun(outcome: SyncOutcome) async {
        lastOutcome = outcome
        await refreshState()
        if outcome.appliedCount > 0 {
            let updated = await repository.snapshot
            onRemoteDataApplied?(updated)
        }
    }
}

// MARK: - AppContainer

/// Root `@MainActor @Observable` dependency and lifecycle container for `GokeApp`.
///
/// Mirrors Android's ViewModel-free `LocalApp` architecture: holds the single `RepositoryActor`,
/// `AppSnapshot`, platform bridges, campus clients, `AppRouter`, `ActionDispatcher`, and `SyncStateCoordinator`.
@MainActor
@Observable
public final class AppContainer {
    static let syncBackgroundTaskIdentifier = "app.goke.ios.sync"
    static let remindersBackgroundTaskIdentifier = "app.goke.ios.reminders"
    private static let examSelfHealVersionKey = "goke_exam_self_heal_v1"

    @ObservationIgnored let appGroup: AppGroupContainer
    @ObservationIgnored let settingsStore: AppSettingsStore
    @ObservationIgnored let keychain: KeychainStore
    @ObservationIgnored let repository: RepositoryActor
    @ObservationIgnored let syncCoordinator: SyncStateCoordinator
    @ObservationIgnored let campusAdapterFactory: any CampusAdapterFactory = CampusAdapterLoader.makeFactory()
    @ObservationIgnored lazy var jxfwClient: any JxfwAdapterClient = campusAdapterFactory.makeJxfwClient()
    @ObservationIgnored lazy var tzcsClient: any TzcsAdapterClient = campusAdapterFactory.makeTzcsClient()
    @ObservationIgnored lazy var jwcwxClient: any JwcwxAdapterClient = campusAdapterFactory.makeJwcwxClient()
    @ObservationIgnored let eventKitBridge: EventKitBridge
    @ObservationIgnored let reminderScheduler: ReminderScheduler
    @ObservationIgnored let liveActivityManager: LiveActivityManager
    @ObservationIgnored let intentBridge: GokeIntentBridge
    @ObservationIgnored let errorLog: GokeErrorLog
    @ObservationIgnored let router: AppRouter
    @ObservationIgnored let actions: ActionDispatcher

    var syncEngine: SyncEngine { syncCoordinator.engine }

    private(set) var snapshot: AppSnapshot = AppSnapshot()
    private(set) var snapshotRevision: UInt64 = 0
    /// Monotonic revision that increments ONLY when Timetable-relevant records change
    /// (`LaunchTimetableCache.contentEquals` diff), preventing Frame-1 Timetable re-renders
    /// when background SQLite hydration matches the seeded launch cache.
    private(set) var timetableRevision: UInt64 = 0
    private(set) var liveSnapshot: AppSnapshot = AppSnapshot()
    private(set) var deletedRecordCount: Int = 0
    /// `false` until full SQLite `loadWithDerived()` finishes during `bootstrap()`.
    private(set) var isHydrated: Bool = false
    /// `true` when `init()` synchronously seeded `snapshot` / `liveSnapshot` from the binary-plist launch cache.
    private(set) var hasLaunchCache: Bool = false
    private(set) var isBootstrapped: Bool = false
    private(set) var bootstrapError: String?
    private(set) var isForegroundRefreshing: Bool = false
    private(set) var recentErrors: [GokeErrorEntry] = []

    /// Pre-resolved clamped current week (`1...totalWeeks`) from the seeded or hydrated active semester,
    /// allowing `TimetableView` to land directly on the active week on Frame 1 with zero Week-1 jump.
    var initialTimetableWeek: Int {
        guard let sem = liveSnapshot.currentSemester else { return 1 }
        let total = max(1, sem.totalWeeks)
        let current = SemesterCalendar(semester: sem).weekOf(.today())
        return max(1, min(total, current))
    }

    @ObservationIgnored private let isInMemory: Bool
    @ObservationIgnored private var hasAppliedSnapshot: Bool = false
    @ObservationIgnored private var lastObservedUndoneOpId: String?
    @ObservationIgnored private var lastTimetablePayload: LaunchTimetableCache?
    @ObservationIgnored private var isBootstrapping: Bool = false
    @ObservationIgnored private var hasEnteredBackground: Bool = false
    @ObservationIgnored private var hasRegisteredBackgroundTasks: Bool = false
    @ObservationIgnored private var isRootViewMounted: Bool = false
    @ObservationIgnored private var pendingQuickSnoozePayload: QuickSnoozePayload?
    @ObservationIgnored private nonisolated(unsafe) var snapshotStreamTask: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var eventKitObserverTask: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var deferredStartupTask: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var deferredForegroundTask: Task<Void, Never>?

    #if canImport(UserNotifications)
    @ObservationIgnored private var notificationDelegate: ReminderNotificationDelegate?
    #endif

    #if canImport(UIKit)
    @MainActor
    private final class BackgroundTaskToken {
        var id: UIBackgroundTaskIdentifier = .invalid

        func end() {
            guard id != .invalid else { return }
            let toEnd = id
            id = .invalid
            UIApplication.shared.endBackgroundTask(toEnd)
        }
    }
    #endif

    init(
        inMemory: Bool = false,
        appGroup: AppGroupContainer = .shared,
        keychain: KeychainStore = .shared,
        settingsStore: AppSettingsStore? = nil,
        eventKitBridge: EventKitBridge? = nil,
        reminderScheduler: ReminderScheduler = .shared,
        liveActivityManager: LiveActivityManager = .shared,
        intentBridge: GokeIntentBridge = .shared,
        errorLog: GokeErrorLog = .shared,
        router: AppRouter = AppRouter()
    ) {
        self.isInMemory = inMemory
        self.appGroup = appGroup
        self.keychain = keychain
        let resolvedSettings = settingsStore ?? AppSettingsStore(container: appGroup)
        self.settingsStore = resolvedSettings
        GokeFeedback.isEnabledProvider = { [weak resolvedSettings] in
            resolvedSettings?.settings.hapticFeedback ?? true
        }
        GokeFeedback.isSoundEnabledProvider = { [weak resolvedSettings] in
            resolvedSettings?.settings.soundFeedback ?? true
        }
        self.eventKitBridge = eventKitBridge ?? EventKitBridge(container: appGroup)
        self.reminderScheduler = reminderScheduler
        self.liveActivityManager = liveActivityManager
        self.intentBridge = intentBridge
        self.errorLog = errorLog
        self.router = router

        // Zero synchronous SQLite WAL open, CREATE TABLE, or directory-creation I/O on @MainActor:
        // SQLiteStore(lazyPath:) opens lazily inside RepositoryActor off the main thread.
        let dbPath = inMemory
            ? ":memory:"
            : appGroup.rootURL.appendingPathComponent("goke.db", isDirectory: false).path
        let sqliteStore = SQLiteStore(lazyPath: dbPath)
        let launchCacheURL: URL? = inMemory ? nil : appGroup.launchTimetableCacheFileURL

        let repo = RepositoryActor(store: sqliteStore, launchCacheURL: launchCacheURL)
        self.repository = repo

        // Seed Frame-1 Timetable state synchronously from the compact binary-plist cache (~1-3ms).
        if !inMemory,
           let cachedPayload = appGroup.readLaunchTimetableCache(),
           cachedPayload.activeSemester != nil
        {
            let seeded = cachedPayload.toPartialSnapshot()
            self.snapshot = seeded
            self.liveSnapshot = seeded
            self.deletedRecordCount = 0
            self.hasLaunchCache = true
            self.lastTimetablePayload = cachedPayload
        }

        let syncStore: any SyncStateStore = inMemory
            ? InMemorySyncStateStore()
            : KeychainSyncStateStore(keychain: keychain, appGroup: appGroup)
        let engine = SyncEngine(repository: repo, stateStore: syncStore)
        let coordinator = SyncStateCoordinator(engine: engine, repository: repo)
        self.syncCoordinator = coordinator

        let dispatcher = ActionDispatcher(
            repository: repo,
            settingsStore: resolvedSettings,
            appGroup: appGroup,
            reminderScheduler: reminderScheduler,
            liveActivityManager: liveActivityManager
        )
        self.actions = dispatcher

        dispatcher.onSnapshotChanged = { [weak self] snap in
            guard let self else { return }
            let hadSnapshot = self.hasAppliedSnapshot
            let previousLive = self.liveSnapshot
            let previousDeletedCount = self.deletedRecordCount
            self.applySnapshot(snap)
            guard hadSnapshot else { return }
            let nextLive = self.liveSnapshot
            let nextDeletedCount = self.deletedRecordCount
            Task { @MainActor [weak self] in
                guard let self else { return }
                let undoneId = self.actions.lastUndoneOpId
                let isUndo = undoneId != nil && undoneId != self.lastObservedUndoneOpId
                self.lastObservedUndoneOpId = undoneId
                self.emitMutationFeedback(
                    previousLive: previousLive,
                    previousDeletedCount: previousDeletedCount,
                    nextLive: nextLive,
                    nextDeletedCount: nextDeletedCount,
                    isUndo: isUndo
                )
            }
        }
        dispatcher.onRequestDebouncedSync = { [weak coordinator] in
            coordinator?.scheduleDebouncedSync()
        }
        coordinator.onRemoteDataApplied = { [weak self] updatedSnapshot in
            guard let self else { return }
            self.applySnapshot(updatedSnapshot)
            self.actions.refreshSideEffects(snapshot: self.liveSnapshot, triggerSync: false)
        }
    }

    deinit {
        snapshotStreamTask?.cancel()
        eventKitObserverTask?.cancel()
        deferredStartupTask?.cancel()
        deferredForegroundTask?.cancel()
    }

    /// Creates an isolated in-memory `AppContainer` suitable for SwiftUI `#Preview` and unit tests.
    static func preview() -> AppContainer {
        AppContainer(inMemory: true)
    }

    /// Single-entry snapshot publisher updating `snapshot`, cached `liveSnapshot`, `deletedRecordCount`,
    /// monotonic `snapshotRevision`, and Timetable-scoped `timetableRevision` while deduplicating
    /// identical assignments and keeping Frame-1 Timetable stable when initial hydration matches the launch cache.
    private func applySnapshot(
        _ next: AppSnapshot,
        precomputedLive: AppSnapshot? = nil,
        precomputedDeletedCount: Int? = nil,
        precomputedTimetablePayload: LaunchTimetableCache? = nil
    ) {
        if hasAppliedSnapshot, next == snapshot {
            return
        }
        let nextLive = precomputedLive ?? next.live()
        let nextDeletedCount = precomputedDeletedCount ?? next.deletedRecordCount

        let timetableChanged: Bool
        if !isHydrated, let prevPayload = lastTimetablePayload {
            let nextPayload = precomputedTimetablePayload ?? LaunchTimetableCache.buildPayload(from: nextLive)
            timetableChanged = !prevPayload.contentEquals(nextPayload)
            lastTimetablePayload = nextPayload
        } else {
            timetableChanged = Self.hasFullTimetableChange(from: liveSnapshot, to: nextLive)
            lastTimetablePayload = nil
        }

        hasAppliedSnapshot = true
        snapshot = next
        if liveSnapshot != nextLive {
            liveSnapshot = nextLive
        }
        if deletedRecordCount != nextDeletedCount {
            deletedRecordCount = nextDeletedCount
        }
        snapshotRevision &+= 1
        if timetableChanged {
            timetableRevision &+= 1
        }
    }

    private func emitMutationFeedback(
        previousLive: AppSnapshot,
        previousDeletedCount: Int,
        nextLive: AppSnapshot,
        nextDeletedCount: Int,
        isUndo: Bool
    ) {
        if isUndo {
            GokeFeedback.play(.undo)
            return
        }
        let prevTasksById = Dictionary(uniqueKeysWithValues: previousLive.tasks.map { ($0.id, $0) })
        var newlyCompletedIds = Set<String>()
        var hasUncheckedTask = false
        for task in nextLive.tasks {
            if let prev = prevTasksById[task.id] {
                if !prev.done && task.done {
                    newlyCompletedIds.insert(task.id)
                } else if prev.done && !task.done {
                    hasUncheckedTask = true
                }
            }
        }
        if !newlyCompletedIds.isEmpty {
            if GokeFeedback.isClearingTodayTasks(snapshot: previousLive, completingIds: newlyCompletedIds) {
                GokeFeedback.play(.celebrate)
            } else {
                GokeFeedback.play(.taskComplete)
            }
        } else if hasUncheckedTask {
            GokeFeedback.play(.softTick)
        } else if nextDeletedCount > previousDeletedCount {
            GokeFeedback.play(.delete)
        } else if previousLive != nextLive {
            GokeFeedback.play(.success)
        }
    }

    /// Compares all Timetable-relevant records across all dates (not clipped to `today ± 14d`)
    /// so edits/additions/deletions/undos in distant weeks always invalidate `TimetableView` caches.
    private static func hasFullTimetableChange(from prev: AppSnapshot, to next: AppSnapshot) -> Bool {
        if prev.semesters != next.semesters
            || prev.courses != next.courses
            || prev.exceptions != next.exceptions
            || prev.lists != next.lists
        {
            return true
        }
        let prevTimedEvents = prev.events.filter { !$0.allDay }
        let nextTimedEvents = next.events.filter { !$0.allDay }
        if prevTimedEvents != nextTimedEvents {
            return true
        }
        let prevBlockTasks = prev.tasks.filter {
            $0.parentId == nil && $0.blockStart != nil && !TimetableLayout.isParcelTask($0)
        }
        let nextBlockTasks = next.tasks.filter {
            $0.parentId == nil && $0.blockStart != nil && !TimetableLayout.isParcelTask($0)
        }
        return prevBlockTasks != nextBlockTasks
    }

    /// Loads the full SQLite snapshot plus derived `liveSnapshot`, `deletedRecordCount`, and
    /// `LaunchTimetableCache` payload entirely off `@MainActor`.
    private nonisolated static func loadDerivedWithTimetablePayload(
        from repository: RepositoryActor
    ) async throws -> (
        snapshot: AppSnapshot,
        live: AppSnapshot,
        deletedRecordCount: Int,
        timetablePayload: LaunchTimetableCache
    ) {
        let derived = try await repository.loadWithDerived()
        let payload = LaunchTimetableCache.buildPayload(from: derived.live)
        return (derived.snapshot, derived.live, derived.deletedRecordCount, payload)
    }

    // MARK: - Lifecycle Bootstrap & Foreground/Background Hooks

    /// Performs one-time launch hydration: opens SQLite & decodes full state off `@MainActor` without
    /// blocking Frame-1 Timetable rendering, then defers all non-essential startup services (notifications,
    /// EventKit observer, one-time exam self-heal, widgets, cloud sync, debug bridge, hot-water arrears)
    /// past first frame (~350ms at `.utility` priority).
    func bootstrap() async {
        guard !isBootstrapped, !isBootstrapping else { return }
        isBootstrapping = true
        defer { isBootstrapping = false }

        var loadedSnapshot: AppSnapshot?
        let repo = self.repository
        do {
            let loaded = try await Task.detached(priority: .userInitiated) {
                try await Self.loadDerivedWithTimetablePayload(from: repo)
            }.value
            loadedSnapshot = loaded.snapshot
            applySnapshot(
                loaded.snapshot,
                precomputedLive: loaded.live,
                precomputedDeletedCount: loaded.deletedRecordCount,
                precomputedTimetablePayload: loaded.timetablePayload
            )
            bootstrapError = nil
        } catch {
            bootstrapError = error.localizedDescription
            await errorLog.record(source: "Bootstrap.Load", error: error)
        }

        isHydrated = true
        consumePendingControlDeepLink()
        router.consumePendingIntentTab(from: intentBridge)
        isBootstrapped = true
        startSnapshotStream()

        let initialSnapshot = loadedSnapshot
        deferredStartupTask?.cancel()
        deferredStartupTask = Task(priority: .utility) { @MainActor [weak self] in
            // Let Frame-1 Timetable finish compositing and accept initial taps/swipes at 60/120fps
            // before running non-essential startup services.
            do {
                try await Task.sleep(for: .milliseconds(350))
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }

            #if DEBUG && canImport(UIKit)
            UIApplication.shared.isIdleTimerDisabled = true
            DebugBridgeServer.shared.start(router: self.router, container: self)
            #endif

            await self.reminderScheduler.registerCategories()
            guard !Task.isCancelled else { return }

            self.router.startObservingIntentBridge(self.intentBridge)
            self.startEventKitObserver()

            if let initialSnapshot {
                await self.runStartupExamSelfHealIfNeeded(loaded: initialSnapshot)
            }
            if let initErr = self.bootstrapError {
                await self.errorLog.record(source: "Bootstrap.SQLite", summary: initErr)
            }
            await self.refreshErrorLog()
            guard !Task.isCancelled else { return }

            await self.syncCoordinator.refreshState()
            guard !Task.isCancelled else { return }

            self.actions.refreshSideEffects(snapshot: self.liveSnapshot, triggerSync: false)
            await self.syncHotWaterWidgetSnapshotIfBound()

            if self.syncCoordinator.isSignedIn, !self.syncCoordinator.needsRelogin {
                if await self.syncCoordinator.syncNow(silent: true) == nil,
                   let err = self.syncCoordinator.lastError
                {
                    await self.errorLog.record(source: "Sync.Bootstrap", summary: err)
                    await self.refreshErrorLog()
                }
            }

            await self.checkHotWaterArrearsIfBound()
        }
    }

    /// Refreshes local database, settings, intents, widgets, reminders, Live Activity, and cloud sync
    /// when returning to foreground from `.background`. Skips cold-launch duplicate reload and transient `.inactive` wakes.
    func handleForegroundRefresh() async {
        guard isBootstrapped, !isBootstrapping, !isForegroundRefreshing else { return }
        guard hasEnteredBackground else {
            router.consumePendingIntentTab(from: intentBridge)
            consumePendingControlDeepLink()
            return
        }

        hasEnteredBackground = false
        isForegroundRefreshing = true
        defer { isForegroundRefreshing = false }

        settingsStore.reloadFromDisk()
        router.consumePendingIntentTab(from: intentBridge)
        consumePendingControlDeepLink()

        let repo = self.repository
        do {
            let reloaded = try await Task.detached(priority: .userInitiated) {
                try await Self.loadDerivedWithTimetablePayload(from: repo)
            }.value
            applySnapshot(
                reloaded.snapshot,
                precomputedLive: reloaded.live,
                precomputedDeletedCount: reloaded.deletedRecordCount,
                precomputedTimetablePayload: reloaded.timetablePayload
            )
        } catch {
            await errorLog.record(source: "ForegroundRefresh.Load", error: error)
            await refreshErrorLog()
        }

        deferredForegroundTask?.cancel()
        deferredForegroundTask = Task(priority: .utility) { @MainActor [weak self] in
            guard let self, !Task.isCancelled else { return }
            await self.syncCoordinator.refreshState()
            guard !Task.isCancelled else { return }

            self.actions.refreshSideEffects(snapshot: self.liveSnapshot, triggerSync: false)
            await self.syncHotWaterWidgetSnapshotIfBound()

            if self.syncCoordinator.isSignedIn, !self.syncCoordinator.needsRelogin {
                if await self.syncCoordinator.syncNow(silent: true) == nil,
                   let err = self.syncCoordinator.lastError
                {
                    await self.errorLog.record(source: "Sync.Foreground", summary: err)
                    await self.refreshErrorLog()
                }
            }

            await self.checkHotWaterArrearsIfBound()
        }
    }

    /// Flushes widget snapshot off `@MainActor`, runs optional daily auto-backup, and schedules background refresh tasks.
    func handleEnterBackground() {
        hasEnteredBackground = true
        scheduleBackgroundRefresh()

        // Never overwrite widget-snapshot.json, launch cache, or today's auto-backup with the partial
        // pre-hydration LaunchTimetableCache (which lacks regular todos, events, and other semesters).
        guard isHydrated else { return }

        let currentLiveSnapshot = liveSnapshot
        let currentFullSnapshot = snapshot
        let currentSettings = settingsStore.settings
        let group = appGroup
        let kc = keychain
        let repo = repository
        let backupsDir = appGroup.backupsDirectoryURL

        #if canImport(UIKit)
        let token = BackgroundTaskToken()
        token.id = UIApplication.shared.beginBackgroundTask(withName: "GokeBackgroundFlush") {
            Task { @MainActor in
                token.end()
            }
        }
        Task.detached(priority: .utility) {
            await repo.flushLaunchCacheNow()
            try? group.writeWidgetSnapshot(currentLiveSnapshot, settings: currentSettings, reloadTimelines: true)
            await HotWaterStore(keychain: kc, appGroup: group).syncWidgetSnapshotFromPersistence(reloadTimelines: true)
            if currentSettings.autoBackup {
                Self.writeDailyAutoBackupIfNeeded(
                    snapshot: currentFullSnapshot,
                    keepCount: currentSettings.backupKeep,
                    backupsDirectory: backupsDir
                )
            }
            await token.end()
        }
        #else
        Task.detached(priority: .utility) {
            await repo.flushLaunchCacheNow()
            try? group.writeWidgetSnapshot(currentLiveSnapshot, settings: currentSettings, reloadTimelines: true)
            await HotWaterStore(keychain: kc, appGroup: group).syncWidgetSnapshotFromPersistence(reloadTimelines: true)
            if currentSettings.autoBackup {
                Self.writeDailyAutoBackupIfNeeded(
                    snapshot: currentFullSnapshot,
                    keepCount: currentSettings.backupKeep,
                    backupsDirectory: backupsDir
                )
            }
        }
        #endif
    }

    /// Handles external mutations posted by AppIntents (`.gokeIntentDidMutateData`).
    func handleExternalDataMutation() async {
        settingsStore.reloadFromDisk()
        let repo = self.repository
        do {
            let reloaded = try await Task.detached(priority: .userInitiated) {
                try await Self.loadDerivedWithTimetablePayload(from: repo)
            }.value
            applySnapshot(
                reloaded.snapshot,
                precomputedLive: reloaded.live,
                precomputedDeletedCount: reloaded.deletedRecordCount,
                precomputedTimetablePayload: reloaded.timetablePayload
            )
            actions.refreshSideEffects(snapshot: liveSnapshot, triggerSync: true)
        } catch {
            await errorLog.record(source: "AppIntent.Load", error: error)
            await refreshErrorLog()
        }
    }

    // MARK: - Diagnostics & Error Log

    func recordError(source: String, error: any Error, isFatal: Bool = false) {
        let log = errorLog
        Task { @MainActor [weak self] in
            await log.record(source: source, error: error, isFatal: isFatal)
            await self?.refreshErrorLog()
        }
    }

    func recordError(source: String, summary: String, detail: String = "", isFatal: Bool = false) {
        let log = errorLog
        Task { @MainActor [weak self] in
            await log.record(source: source, summary: summary, detail: detail, isFatal: isFatal)
            await self?.refreshErrorLog()
        }
    }

    func refreshErrorLog() async {
        recentErrors = await errorLog.entries()
    }

    func acknowledgeErrors() async {
        await errorLog.acknowledgeAll()
        await refreshErrorLog()
    }

    func clearErrorLog() async {
        await errorLog.clear()
        await refreshErrorLog()
    }

    /// Handles incoming `goke://` deep links or imported file URLs.
    func handleOpenURL(_ url: URL) {
        if url.scheme?.lowercased() == "goke" {
            let key = "app.goke.ios.pendingDeepLink"
            if appGroup.userDefaults.string(forKey: key) == url.absoluteString {
                appGroup.userDefaults.removeObject(forKey: key)
            }
        }
        _ = router.handleDeepLink(url)
    }

    private func consumePendingControlDeepLink() {
        let key = "app.goke.ios.pendingDeepLink"
        guard let raw = appGroup.userDefaults.string(forKey: key), !raw.isEmpty else {
            return
        }
        appGroup.userDefaults.removeObject(forKey: key)
        if let url = URL(string: raw) {
            _ = router.handleDeepLink(url)
        }
    }

    /// Refreshes reminders, widgets, and Live Activity after the user changes `AppSettings`.
    func notifySettingsChanged() {
        guard isHydrated else { return }
        actions.refreshSideEffects(snapshot: liveSnapshot, triggerSync: false)
    }

    /// Syncs persisted Hot Water binding/balance state into `AppGroupContainer` (`hotwater-widget.json`
    /// + `userDefaults`) without initializing `HotWaterAdapterBackend` / `CoreBluetooth`.
    private func syncHotWaterWidgetSnapshotIfBound() async {
        await HotWaterStore(keychain: keychain, appGroup: appGroup)
            .syncWidgetSnapshotFromPersistence(reloadTimelines: true)
    }

    /// Avoids initializing `HotWaterClient.shared` + `HotWaterAdapterBackend` during cold launch for users
    /// who have not bound a campus hot-water account.
    private func checkHotWaterArrearsIfBound() async {
        guard let rawUserId = (try? await keychain.get(HotWaterStore.userIdKeychainKey)) ?? nil,
              !rawUserId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return
        }
        await HotWaterArrearsScheduler.checkForegroundIfDue(
            client: .shared,
            repository: repository
        )
    }

    /// Lightweight, idempotent one-time self-heal for databases synced by older builds:
    /// - Resets any regular `jxfw-course/` course mistakenly marked `kind == .EXAM` back to `.NORMAL`.
    /// - Migrates legacy `jxfw-course-exam/` courses (and legacy exam tasks) to `jxfw-exam-course/`
    ///   while deduping against existing target IDs.
    /// Persists silently (`record: false`) only when records actually need repair, and records a
    /// one-time version flag in `UserDefaults` (`goke_exam_self_heal_v1`) so subsequent launches skip the scan.
    private func runStartupExamSelfHealIfNeeded(loaded: AppSnapshot) async {
        if !isInMemory, appGroup.userDefaults.bool(forKey: Self.examSelfHealVersionKey) {
            return
        }

        var current = loaded
        var didModify = false
        var hadError = false

        if let migration = JxfwSyncMapper.migrateExamTasksToCourses(snapshot: current),
           !migration.isEmpty
        {
            do {
                _ = try await repository.commit(migration, record: false)
                current = await repository.snapshot
                didModify = true
            } catch {
                hadError = true
                await errorLog.record(source: "Bootstrap.ExamSelfHeal", error: error)
                return
            }
        }

        // Catch any remaining legacy/misclassified jxfw course rows regardless of `source`.
        var extraChanges: [Change] = []
        var existingIds = Set(current.courses.map(\.id))

        for course in current.courses where course.deletedAt == nil {
            if course.id.hasPrefix(JxfwSyncMapper.coursePrefix), course.kind == .EXAM {
                var healed = course
                healed.kind = .NORMAL
                healed.remindPersistent = false
                if healed != course {
                    extraChanges.append(.modify(before: .course(course), after: .course(healed)))
                }
            } else if course.id.hasPrefix(JxfwSyncMapper.courseExamPrefix) {
                extraChanges.append(.remove(before: .course(course)))
                let suffix = String(course.id.dropFirst(JxfwSyncMapper.courseExamPrefix.count))
                let targetId = JxfwSyncMapper.examCoursePrefix + suffix
                if !existingIds.contains(targetId) {
                    var migrated = course
                    migrated.id = targetId
                    migrated.kind = .EXAM
                    migrated.remindPersistent = true
                    extraChanges.append(.add(after: .course(migrated)))
                    existingIds.insert(targetId)
                }
            }
        }

        if !extraChanges.isEmpty {
            let healChangeSet = ChangeSet(
                description: "自动修复教务课程考试标记",
                source: .IMPORT,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                changes: extraChanges
            )
            do {
                _ = try await repository.commit(healChangeSet, record: false)
                current = await repository.snapshot
                didModify = true
            } catch {
                hadError = true
                await errorLog.record(source: "Bootstrap.ExamSelfHeal", error: error)
            }
        }

        if didModify {
            applySnapshot(current)
        }
        if !hadError, !isInMemory {
            appGroup.userDefaults.set(true, forKey: Self.examSelfHealVersionKey)
        }
    }

    // MARK: - BGTaskScheduler Registration & Execution

    /// Registers `app.goke.ios.sync`, `app.goke.ios.reminders`, and `app.goke.ios.hotwater.arrears`
    /// handlers with `BGTaskScheduler` without eagerly instantiating heavy campus clients.
    /// Call once during app launch (`GokeApp.init()`).
    func registerBackgroundTasks() {
        guard !hasRegisteredBackgroundTasks else { return }
        hasRegisteredBackgroundTasks = true

        #if canImport(BackgroundTasks) && os(iOS)
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.syncBackgroundTaskIdentifier,
            using: nil
        ) { [weak self] task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self?.dispatchSyncBackgroundTask(refreshTask)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.remindersBackgroundTaskIdentifier,
            using: nil
        ) { [weak self] task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self?.dispatchRemindersBackgroundTask(refreshTask)
        }

        let repo = self.repository
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: HotWaterArrearsScheduler.backgroundTaskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            let box = UncheckedBGTaskBox(task: refreshTask)
            let work = Task { @MainActor in
                let client = HotWaterClient.shared
                await client.ensureHydrated()
                let snap = client.state
                guard snap.isBound, snap.config.arrearsTodoEnabled else {
                    HotWaterArrearsScheduler.scheduleNextRefresh(enabled: false)
                    return true
                }
                HotWaterArrearsScheduler.scheduleNextRefresh(enabled: true)
                let res = await client.refreshStatusAndCheckArrears(probeBle: false, repository: repo)
                return res.ok || res.errorCode != .networkError
            }
            refreshTask.expirationHandler = {
                work.cancel()
            }
            Task {
                let success = await work.value
                box.task.setTaskCompleted(success: success)
            }
        }
        #endif
    }

    /// Submits next `BGAppRefreshTaskRequest` entries for background cloud sync and rolling notification refresh.
    func scheduleBackgroundRefresh() {
        #if canImport(BackgroundTasks) && os(iOS)
        if syncCoordinator.isSignedIn, !syncCoordinator.needsRelogin {
            let syncRequest = BGAppRefreshTaskRequest(identifier: Self.syncBackgroundTaskIdentifier)
            syncRequest.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
            try? BGTaskScheduler.shared.submit(syncRequest)
        }

        let reminderRequest = BGAppRefreshTaskRequest(identifier: Self.remindersBackgroundTaskIdentifier)
        reminderRequest.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 60 * 60)
        try? BGTaskScheduler.shared.submit(reminderRequest)
        #endif
    }

    /// Executes a background Pull-then-Push sync cycle and refreshes widgets/reminders if data changed.
    func performBackgroundSync() async -> Bool {
        if !isBootstrapped {
            if let loaded = try? await repository.load() {
                applySnapshot(loaded)
            }
            await syncCoordinator.refreshState()
        }
        guard syncCoordinator.isSignedIn, !syncCoordinator.needsRelogin else {
            return true
        }
        let outcome = await syncCoordinator.syncNow(silent: true)
        if let updated = try? await repository.load() {
            applySnapshot(updated)
            let live = liveSnapshot
            let currentSettings = settingsStore.settings
            let group = appGroup
            await Task.detached(priority: .utility) {
                try? group.writeWidgetSnapshot(live, settings: currentSettings, reloadTimelines: true)
            }.value
            _ = await reminderScheduler.reschedule(snapshot: live, settings: currentSettings)
            await liveActivityManager.sync(snapshot: live)
        }
        return outcome != nil
    }

    /// Rebuilds the rolling 60-slot local notification window, widget snapshot, and Live Activity in the background.
    func performBackgroundReminderRefresh() async -> Bool {
        settingsStore.reloadFromDisk()
        guard let loaded = try? await repository.load() else {
            return false
        }
        applySnapshot(loaded)
        let live = liveSnapshot
        let currentSettings = settingsStore.settings
        let group = appGroup
        await Task.detached(priority: .utility) {
            try? group.writeWidgetSnapshot(live, settings: currentSettings, reloadTimelines: true)
        }.value
        _ = await reminderScheduler.reschedule(snapshot: live, settings: currentSettings, force: true)
        await liveActivityManager.sync(snapshot: live)
        return true
    }

    #if canImport(BackgroundTasks) && os(iOS)
    private struct UncheckedBGTaskBox: @unchecked Sendable {
        let task: BGAppRefreshTask
    }

    private nonisolated func dispatchSyncBackgroundTask(_ task: BGAppRefreshTask) {
        let box = UncheckedBGTaskBox(task: task)
        let work = Task { @MainActor [weak self] in
            guard let self else { return false }
            self.scheduleBackgroundRefresh()
            return await self.performBackgroundSync()
        }
        task.expirationHandler = {
            work.cancel()
        }
        Task {
            let success = await work.value
            box.task.setTaskCompleted(success: success)
        }
    }

    private nonisolated func dispatchRemindersBackgroundTask(_ task: BGAppRefreshTask) {
        let box = UncheckedBGTaskBox(task: task)
        let work = Task { @MainActor [weak self] in
            guard let self else { return false }
            self.scheduleBackgroundRefresh()
            return await self.performBackgroundReminderRefresh()
        }
        task.expirationHandler = {
            work.cancel()
        }
        Task {
            let success = await work.value
            box.task.setTaskCompleted(success: success)
        }
    }
    #endif

    // MARK: - Internal Observers & Auto-Backup

    private func startSnapshotStream() {
        snapshotStreamTask?.cancel()
        let repo = self.repository
        snapshotStreamTask = Task { @MainActor [weak self] in
            let stream = await repo.snapshotStream()
            for await next in stream {
                guard !Task.isCancelled else { break }
                self?.applySnapshot(next)
            }
        }
    }

    private func startEventKitObserver() {
        eventKitObserverTask?.cancel()
        let bridge = self.eventKitBridge
        eventKitObserverTask = Task { @MainActor [weak self] in
            let stream = bridge.storeChangedStream()
            for await _ in stream {
                guard !Task.isCancelled else { break }
                guard let self else { break }
                self.actions.refreshSideEffects(
                    snapshot: self.liveSnapshot,
                    triggerSync: false,
                    debounce: .seconds(1)
                )
            }
        }
    }

    func installNotificationDelegate() {
        #if canImport(UserNotifications)
        guard notificationDelegate == nil else { return }
        let delegate = ReminderNotificationDelegate(scheduler: reminderScheduler) { [weak self] event in
            await self?.handleNotificationAction(event)
        }
        delegate.install()
        notificationDelegate = delegate
        #endif
    }

    /// Marks the root SwiftUI scene (`QuickSnoozeHostModifier`) as mounted and replays any
    /// cold-launch notification Quick-Snooze trigger that arrived before the view hierarchy appeared.
    func markRootViewMounted() {
        guard !isRootViewMounted else { return }
        isRootViewMounted = true
        guard let payload = pendingQuickSnoozePayload else { return }
        pendingQuickSnoozePayload = nil
        Task { @MainActor in
            await Task.yield()
            NotificationCenter.default.post(
                name: .gokePresentQuickSnooze,
                object: nil,
                userInfo: ["payload": payload]
            )
        }
    }

    private func handleNotificationAction(_ event: ReminderActionEvent) async {
        switch event {
        case .completeTask(let taskId):
            _ = await actions.completeTask(id: taskId)
        case .openDeepLink(let url):
            _ = router.handleDeepLink(url)
        case .presentQuickSnooze(let payload):
            if isRootViewMounted {
                NotificationCenter.default.post(
                    name: .gokePresentQuickSnooze,
                    object: nil,
                    userInfo: ["payload": payload]
                )
            } else {
                pendingQuickSnoozePayload = payload
            }
        case .snoozed:
            break
        }
    }

    private nonisolated static func writeDailyAutoBackupIfNeeded(
        snapshot: AppSnapshot,
        keepCount: Int,
        backupsDirectory: URL
    ) {
        let fm = FileManager.default
        try? fm.createDirectory(at: backupsDirectory, withIntermediateDirectories: true)

        let today = LocalDate.today().description
        let targetURL = backupsDirectory.appendingPathComponent("goke-backup-\(today).json", isDirectory: false)
        if !fm.fileExists(atPath: targetURL.path) {
            let bundle = ExportBundle(
                exportedAt: Int64(Date().timeIntervalSince1970 * 1000),
                snapshot: snapshot
            )
            if let encoded = try? BackupCodec.encode(bundle, pretty: true),
               let data = encoded.data(using: .utf8)
            {
                try? data.write(to: targetURL, options: .atomic)
            }
        }

        let limit = max(1, keepCount)
        guard let entries = try? fm.contentsOfDirectory(
            at: backupsDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else {
            return
        }
        let backups = entries
            .filter { $0.lastPathComponent.hasPrefix("goke-backup-") && $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
        if backups.count > limit {
            for stale in backups.dropFirst(limit) {
                try? fm.removeItem(at: stale)
            }
        }
    }
}
