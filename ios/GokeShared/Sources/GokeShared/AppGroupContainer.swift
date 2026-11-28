import Foundation
import GokeCoreModel
#if canImport(WidgetKit)
import WidgetKit
#endif

/// Compact JSON payload shared between `GokeApp` and `GokeWidgetExtension` via App Group container.
public struct WidgetSnapshotPayload: Codable, Sendable, Equatable {
    public static let currentSchemaVersion = 1

    public let schemaVersion: Int
    public let generatedAtMillis: Int64
    public let snapshot: AppSnapshot
    public let settings: AppSettings

    public init(
        schemaVersion: Int = WidgetSnapshotPayload.currentSchemaVersion,
        generatedAtMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        snapshot: AppSnapshot,
        settings: AppSettings
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtMillis = generatedAtMillis
        self.snapshot = snapshot
        self.settings = settings
    }
}

/// Resolves shared App Group storage (`group.app.goke.shared`) with graceful fallback to
/// `Application Support` / `Documents` when free Apple ID sideloading strips App Group entitlements.
public final class AppGroupContainer: Sendable {
    public static let appGroupIdentifier = "group.app.goke.shared"
    public static let shared = AppGroupContainer()

    public let groupIdentifier: String
    public let isAppGroupAvailable: Bool
    public let rootURL: URL

    private let cachedDatabaseURL: URL
    private let cachedWidgetSnapshotURL: URL
    private let cachedHotWaterWidgetSnapshotURL: URL
    private let cachedLaunchTimetableCacheURL: URL
    private let cachedAttachmentsDirectoryURL: URL
    private nonisolated(unsafe) let cachedUserDefaults: UserDefaults
    private let dirLock = NSLock()
    private nonisolated(unsafe) var ensuredDirectories: Set<String> = []
    private static let migrationLock = NSLock()
    private nonisolated(unsafe) static var migratedRootPaths: Set<String> = []

    public init(
        groupIdentifier: String = AppGroupContainer.appGroupIdentifier,
        fileManager: FileManager = .default,
        bundle: Bundle = .main
    ) {
        let fallbackBase =
            fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        let fallbackRootURL = fallbackBase.appendingPathComponent("GokeData", isDirectory: true)

        if let resolved = Self.resolveAppGroup(
            preferred: groupIdentifier,
            fileManager: fileManager,
            bundle: bundle
        ) {
            self.groupIdentifier = resolved.identifier
            self.isAppGroupAvailable = true
            self.rootURL = resolved.url
        } else {
            self.groupIdentifier = groupIdentifier
            self.isAppGroupAvailable = false
            self.rootURL = fallbackRootURL
        }
        self.cachedDatabaseURL = rootURL.appendingPathComponent("goke.db", isDirectory: false)
        self.cachedWidgetSnapshotURL = rootURL.appendingPathComponent("widget-snapshot.json", isDirectory: false)
        self.cachedHotWaterWidgetSnapshotURL = rootURL.appendingPathComponent(
            HotWaterWidgetSnapshot.defaultFileName,
            isDirectory: false
        )
        self.cachedLaunchTimetableCacheURL = rootURL.appendingPathComponent(
            LaunchTimetableCache.defaultFileName,
            isDirectory: false
        )
        self.cachedAttachmentsDirectoryURL = rootURL.appendingPathComponent("attachments", isDirectory: true)
        if isAppGroupAvailable, let suite = UserDefaults(suiteName: self.groupIdentifier) {
            self.cachedUserDefaults = suite
        } else {
            self.cachedUserDefaults = .standard
        }

        if isAppGroupAvailable && !Self.isAppExtension(bundle: bundle) {
            Self.migrateFallbackDataOnceIfNeeded(
                from: fallbackRootURL,
                to: rootURL,
                targetDefaults: cachedUserDefaults,
                fileManager: fileManager
            )
        }
    }

    private static func isAppExtension(bundle: Bundle) -> Bool {
        bundle.bundlePath.hasSuffix(".appex")
    }

    private static func migrateFallbackDataOnceIfNeeded(
        from fallbackRootURL: URL,
        to targetRootURL: URL,
        targetDefaults: UserDefaults,
        fileManager: FileManager
    ) {
        let key = targetRootURL.standardizedFileURL.path
        migrationLock.lock()
        let alreadyChecked = migratedRootPaths.contains(key)
        migrationLock.unlock()
        if alreadyChecked { return }

        _ = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackRootURL,
            targetRootURL: targetRootURL,
            sourceDefaults: .standard,
            targetDefaults: targetDefaults,
            isAppGroupAvailable: true,
            isExtension: false,
            fileManager: fileManager
        )

        migrationLock.lock()
        migratedRootPaths.insert(key)
        migrationLock.unlock()
    }

    /// Resolves the active App Group container, trying `preferred` first and then any rewritten
    /// `com.apple.security.application-groups` identifier embedded in `embedded.mobileprovision`
    /// (e.g. `group.XTL-<TEAM>.app.goke.shared` under xtool 2.0+ or `group.app.goke.shared.<TEAM>` under SideStore/AltStore).
    public static func resolveAppGroup(
        preferred: String = AppGroupContainer.appGroupIdentifier,
        fileManager: FileManager = .default,
        bundle: Bundle = .main
    ) -> (identifier: String, url: URL)? {
        if let directURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: preferred) {
            return (preferred, directURL)
        }
        for candidate in provisioningAppGroupIdentifiers(in: bundle) where candidate != preferred {
            if let url = fileManager.containerURL(forSecurityApplicationGroupIdentifier: candidate) {
                return (candidate, url)
            }
        }
        return nil
    }

    /// Extracts `Entitlements["com.apple.security.application-groups"]` from `embedded.mobileprovision` if present.
    public static func provisioningAppGroupIdentifiers(in bundle: Bundle = .main) -> [String] {
        guard let url = bundle.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let xmlData = extractEmbeddedPlistData(from: data),
              let plist = try? PropertyListSerialization.propertyList(from: xmlData, options: [], format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String]
        else {
            return []
        }
        return groups.filter { !$0.isEmpty }
    }

    private static func extractEmbeddedPlistData(from profileData: Data) -> Data? {
        let startMarker = Data("<plist".utf8)
        let endMarker = Data("</plist>".utf8)
        guard let startRange = profileData.range(of: startMarker),
              let endRange = profileData.range(of: endMarker, in: startRange.lowerBound..<profileData.endIndex)
        else {
            return nil
        }
        return profileData.subdata(in: startRange.lowerBound..<endRange.upperBound)
    }

    /// Primary SQLite WAL database path (`goke.db`).
    public var databaseURL: URL {
        ensureDirectoryOnce(rootURL)
        return cachedDatabaseURL
    }

    /// Shared JSON snapshot written for `GokeWidgetExtension` timelines.
    public var widgetSnapshotURL: URL {
        ensureDirectoryOnce(rootURL)
        return cachedWidgetSnapshotURL
    }

    /// Compact binary-plist cache (`launch_timetable_cache.plist`) used for instant Frame-1 cold launch.
    public var launchTimetableCacheURL: URL {
        ensureDirectoryOnce(rootURL)
        return cachedLaunchTimetableCacheURL
    }

    /// Direct URL to `launch_timetable_cache.plist` without performing any directory-creation I/O.
    public var launchTimetableCacheFileURL: URL {
        cachedLaunchTimetableCacheURL
    }

    /// Synchronously reads the binary-plist launch timetable cache via `.mappedIfSafe` (~1–3 ms) with zero directory I/O.
    public func readLaunchTimetableCache() -> LaunchTimetableCache? {
        LaunchTimetableCache.loadPayloadSync(from: cachedLaunchTimetableCacheURL)
    }

    /// Synchronously reads the binary-plist launch timetable cache as a minimal `AppSnapshot` ready for Frame-1 Timetable.
    public func readLaunchTimetableSnapshot() -> AppSnapshot? {
        LaunchTimetableCache.loadSync(from: cachedLaunchTimetableCacheURL)
    }

    /// Synchronously writes `launch_timetable_cache.plist` atomically from `snapshot`.
    public func writeLaunchTimetableCache(from snapshot: AppSnapshot, now: Date = Date()) throws {
        ensureDirectoryOnce(rootURL)
        try LaunchTimetableCache.saveSync(from: snapshot, to: cachedLaunchTimetableCacheURL, now: now)
    }

    /// Builds and atomically writes `launch_timetable_cache.plist` off-main-thread at `.utility` priority.
    @discardableResult
    public func writeLaunchTimetableCacheAsync(from snapshot: AppSnapshot, now: Date = Date()) -> Task<Void, Never> {
        LaunchTimetableCache.saveAsync(from: snapshot, to: cachedLaunchTimetableCacheURL, now: now)
    }

    /// Removes `launch_timetable_cache.plist` if present.
    public func invalidateLaunchTimetableCache() {
        LaunchTimetableCache.invalidate(at: cachedLaunchTimetableCacheURL)
    }

    /// Local attachment storage directory (excluded from cloud sync).
    public var attachmentsDirectoryURL: URL {
        ensureDirectoryOnce(cachedAttachmentsDirectoryURL)
        return cachedAttachmentsDirectoryURL
    }

    /// User-visible auto-backup directory inside `Documents/Backups` (surfaced in Apple Files app).
    public var backupsDirectoryURL: URL {
        let docs =
            FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? rootURL
        let dir = docs.appendingPathComponent("Backups", isDirectory: true)
        ensureDirectoryOnce(dir)
        return dir
    }

    /// Shared `UserDefaults` suite when App Group is active; otherwise falls back to `.standard`.
    public var userDefaults: UserDefaults {
        cachedUserDefaults
    }

    /// Atomically writes the shared widget snapshot JSON (stripping soft-deleted tombstones)
    /// and triggers WidgetKit timeline reload.
    public func writeWidgetSnapshot(
        _ snapshot: AppSnapshot,
        settings: AppSettings,
        reloadTimelines: Bool = true
    ) throws {
        let payload = WidgetSnapshotPayload(snapshot: snapshot.live(), settings: settings)
        try writeWidgetPayload(payload, reloadTimelines: reloadTimelines)
    }

    /// Atomically writes a pre-built `WidgetSnapshotPayload` to `widgetSnapshotURL`.
    public func writeWidgetPayload(
        _ payload: WidgetSnapshotPayload,
        reloadTimelines: Bool = true
    ) throws {
        ensureDirectoryOnce(rootURL)
        let data = try MintJSON.makeEncoder(pretty: false).encode(payload)
        try data.write(to: cachedWidgetSnapshotURL, options: .atomic)
        if reloadTimelines {
            reloadAllWidgets()
        }
    }

    /// Reads the latest shared widget snapshot JSON if present and decodable.
    public func readWidgetSnapshot() -> WidgetSnapshotPayload? {
        guard let data = try? Data(contentsOf: cachedWidgetSnapshotURL) else {
            return nil
        }
        return try? MintJSON.makeDecoder().decode(WidgetSnapshotPayload.self, from: data)
    }

    /// Shared JSON snapshot file for `HotWaterWidget` (`hotwater-widget.json`).
    public var hotWaterWidgetSnapshotURL: URL {
        ensureDirectoryOnce(rootURL)
        return cachedHotWaterWidgetSnapshotURL
    }

    /// Atomically persists `HotWaterWidgetSnapshot` to BOTH `hotwater-widget.json` (cross-process reliable
    /// without `cfprefsd` caching lag) AND `userDefaults` (legacy compatibility), then reloads `HotWaterWidget`.
    public func writeHotWaterWidgetSnapshot(
        _ snapshot: HotWaterWidgetSnapshot,
        reloadTimelines: Bool = true
    ) {
        ensureDirectoryOnce(rootURL)
        try? snapshot.save(to: cachedHotWaterWidgetSnapshotURL)
        snapshot.save(to: cachedUserDefaults)
        if reloadTimelines {
            reloadHotWaterWidget()
        }
    }

    /// Removes persisted `HotWaterWidgetSnapshot` from both `hotwater-widget.json` and `userDefaults`.
    public func clearHotWaterWidgetSnapshot(reloadTimelines: Bool = true) {
        try? FileManager.default.removeItem(at: cachedHotWaterWidgetSnapshotURL)
        cachedUserDefaults.removeObject(forKey: HotWaterWidgetSnapshot.storageKey)
        if reloadTimelines {
            reloadHotWaterWidget()
        }
    }

    /// Reads the latest `HotWaterWidgetSnapshot`, preferring the atomic shared JSON file and falling back
    /// to `userDefaults` (picking the fresher entry if both exist).
    public func readHotWaterWidgetSnapshot() -> HotWaterWidgetSnapshot {
        let fromFile = HotWaterWidgetSnapshot.load(from: cachedHotWaterWidgetSnapshotURL)
        let fromDefaults = HotWaterWidgetSnapshot.loadIfPresent(from: cachedUserDefaults)
        switch (fromFile, fromDefaults) {
        case let (fileSnap?, defaultsSnap?):
            let fileTs = max(fileSnap.lastCheckAt, fileSnap.lastActionAt)
            let defaultsTs = max(defaultsSnap.lastCheckAt, defaultsSnap.lastActionAt)
            if defaultsTs > fileTs {
                return defaultsSnap
            }
            return fileSnap
        case let (fileSnap?, nil):
            return fileSnap
        case let (nil, defaultsSnap?):
            return defaultsSnap
        case (nil, nil):
            return HotWaterWidgetSnapshot()
        }
    }

    /// Triggers WidgetKit to refresh `HotWaterWidget` immediately (plus all timelines as fallback).
    public func reloadHotWaterWidget() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: HotWaterWidgetSnapshot.widgetKind)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    /// Triggers WidgetKit to refresh all registered Goke widgets.
    public func reloadAllWidgets() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    private func ensureDirectoryOnce(_ url: URL) {
        let path = url.path
        dirLock.lock()
        let alreadyEnsured = ensuredDirectories.contains(path)
        dirLock.unlock()
        if alreadyEnsured { return }

        let fm = FileManager.default
        if !fm.fileExists(atPath: path) {
            try? fm.createDirectory(at: url, withIntermediateDirectories: true)
        }
        dirLock.lock()
        ensuredDirectories.insert(path)
        dirLock.unlock()
    }
}
