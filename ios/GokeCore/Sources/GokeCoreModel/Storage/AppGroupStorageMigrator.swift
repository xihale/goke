import Foundation

/// Summary returned by `AppGroupStorageMigrator.migrateIfNeeded` for diagnostics and unit tests.
public struct AppGroupMigrationReport: Sendable, Equatable {
    public let migratedDatabase: Bool
    public let copiedCompanionItems: Int
    public let migratedUserDefaultsKeys: Int

    public init(
        migratedDatabase: Bool = false,
        copiedCompanionItems: Int = 0,
        migratedUserDefaultsKeys: Int = 0
    ) {
        self.migratedDatabase = migratedDatabase
        self.copiedCompanionItems = copiedCompanionItems
        self.migratedUserDefaultsKeys = migratedUserDefaultsKeys
    }
}

/// Safe, idempotent one-time migrator from legacy sandbox fallback storage
/// (`Application Support/GokeData/` + `UserDefaults.standard`) into the newly enabled
/// App Group container (`goke.db`, `-wal`, `-shm`, caches, attachments, and `app.goke.ios.*` prefs).
///
/// Design guarantees:
/// - Host app only (`!isExtension`); widgets/extensions never trigger migration.
/// - Never overwrites a populated App Group database or existing App Group `UserDefaults` values.
/// - Copies DB + WAL + SHM via `.migrating` staging files before swapping into place.
/// - Leaves the fallback directory completely intact as a backup and writes a `.migrated_from_fallback_v1`
///   marker so subsequent launches incur a single fast `stat` (~0.05 ms).
public enum AppGroupStorageMigrator: Sendable {
    public static let fileMigrationMarkerName = ".migrated_from_fallback_v1"
    public static let userDefaultsMigrationMarkerKey = "app.goke.ios.appGroupMigration.userDefaults.v1"

    private static let databaseFileNames = ["goke.db", "goke.db-wal", "goke.db-shm"]
    private static let derivedCacheFileNames: Set<String> = [
        LaunchTimetableCache.defaultFileName,
        "widget-snapshot.json",
        HotWaterWidgetSnapshot.defaultFileName,
    ]
    private static let examSelfHealVersionKey = "goke_exam_self_heal_v1"

    /// Keys previously stored in `UserDefaults.standard` when App Group was unavailable.
    public static func shouldMigrateUserDefaultsKey(_ key: String) -> Bool {
        if key == userDefaultsMigrationMarkerKey || key == "app.goke.ios.pendingDeepLink" {
            return false
        }
        return key.hasPrefix("app.goke.ios.") || key == examSelfHealVersionKey
    }

    /// Runs safe, idempotent file and `UserDefaults` migration synchronously if needed.
    @discardableResult
    public static func migrateIfNeeded(
        fallbackRootURL: URL,
        targetRootURL: URL,
        sourceDefaults: UserDefaults?,
        targetDefaults: UserDefaults?,
        isAppGroupAvailable: Bool,
        isExtension: Bool,
        fileManager: FileManager = .default
    ) -> AppGroupMigrationReport {
        guard isAppGroupAvailable, !isExtension else {
            return AppGroupMigrationReport()
        }

        let (didMigrateDB, replacedEmptyDB, copiedCompanions) = migrateFilesIfNeeded(
            from: fallbackRootURL,
            to: targetRootURL,
            fileManager: fileManager
        )

        let migratedKeys = migrateUserDefaultsIfNeeded(
            from: sourceDefaults,
            to: targetDefaults,
            replacedEmptyDB: replacedEmptyDB
        )

        return AppGroupMigrationReport(
            migratedDatabase: didMigrateDB,
            copiedCompanionItems: copiedCompanions,
            migratedUserDefaultsKeys: migratedKeys
        )
    }

    // MARK: - Filesystem & SQLite Migration

    private static func migrateFilesIfNeeded(
        from fallbackRootURL: URL,
        to targetRootURL: URL,
        fileManager: FileManager
    ) -> (migratedDB: Bool, replacedEmptyDB: Bool, copiedCompanions: Int) {
        guard fallbackRootURL.standardizedFileURL.path != targetRootURL.standardizedFileURL.path else {
            return (false, false, 0)
        }

        let markerURL = targetRootURL.appendingPathComponent(fileMigrationMarkerName, isDirectory: false)
        if fileManager.fileExists(atPath: markerURL.path) {
            return (false, false, 0)
        }

        let fallbackDBURL = fallbackRootURL.appendingPathComponent("goke.db", isDirectory: false)
        guard fileSizeBytes(at: fallbackDBURL, fileManager: fileManager) > 0 else {
            return (false, false, 0)
        }

        let targetDBURL = targetRootURL.appendingPathComponent("goke.db", isDirectory: false)
        let targetState = SQLiteStore.inspectContentState(atPath: targetDBURL.path, fileManager: fileManager)

        let shouldReplaceEmptyDB: Bool
        switch targetState {
        case .missingOrZeroBytes:
            shouldReplaceEmptyDB = false
        case .emptySchemaOnly:
            // Recover gracefully if an earlier launch or extension created an empty schema-only DB
            // before migration ran, as long as the fallback DB actually holds user records.
            let fallbackState = SQLiteStore.inspectContentState(atPath: fallbackDBURL.path, fileManager: fileManager)
            guard fallbackState == .hasData else {
                writeMarker(at: markerURL, directory: targetRootURL, fileManager: fileManager)
                return (false, false, 0)
            }
            shouldReplaceEmptyDB = true
        case .hasData:
            // Target App Group DB already holds real data — never overwrite it.
            writeMarker(at: markerURL, directory: targetRootURL, fileManager: fileManager)
            return (false, false, 0)
        case .unreadable:
            // Conservative safety: never overwrite an unreadable target DB.
            return (false, false, 0)
        }

        if !fileManager.fileExists(atPath: targetRootURL.path) {
            do {
                try fileManager.createDirectory(at: targetRootURL, withIntermediateDirectories: true)
            } catch {
                return (false, false, 0)
            }
        }

        // Stage `goke.db` (and `-wal` / `-shm` if present) into `.migrating` temp files first
        // so an interrupted copy can never leave a half-written database in place.
        var stagedPairs: [(staged: URL, final: URL)] = []
        do {
            for name in databaseFileNames {
                let src = fallbackRootURL.appendingPathComponent(name, isDirectory: false)
                let finalURL = targetRootURL.appendingPathComponent(name, isDirectory: false)
                let stagedURL = targetRootURL.appendingPathComponent("\(name).migrating", isDirectory: false)
                if fileManager.fileExists(atPath: stagedURL.path) {
                    try? fileManager.removeItem(at: stagedURL)
                }
                if name == "goke.db" || fileManager.fileExists(atPath: src.path) {
                    try fileManager.copyItem(at: src, to: stagedURL)
                    stagedPairs.append((stagedURL, finalURL))
                }
            }

            // Clear any existing target DB + WAL/SHM files so no stale WAL pairs with the migrated DB.
            for name in databaseFileNames {
                let existing = targetRootURL.appendingPathComponent(name, isDirectory: false)
                if fileManager.fileExists(atPath: existing.path) {
                    try fileManager.removeItem(at: existing)
                }
            }

            // Move staged files into final positions.
            for pair in stagedPairs {
                try fileManager.moveItem(at: pair.staged, to: pair.final)
            }
        } catch {
            // Clean up staging temp files on failure; fallback remains completely untouched.
            for name in databaseFileNames {
                let stagedURL = targetRootURL.appendingPathComponent("\(name).migrating", isDirectory: false)
                try? fileManager.removeItem(at: stagedURL)
            }
            return (false, false, 0)
        }

        let copiedCompanions = copyCompanionItems(
            from: fallbackRootURL,
            to: targetRootURL,
            overwriteDerivedCaches: shouldReplaceEmptyDB,
            fileManager: fileManager
        )

        writeMarker(at: markerURL, directory: targetRootURL, fileManager: fileManager)
        return (true, shouldReplaceEmptyDB, copiedCompanions)
    }

    private static func copyCompanionItems(
        from sourceDir: URL,
        to targetDir: URL,
        overwriteDerivedCaches: Bool,
        fileManager: FileManager
    ) -> Int {
        guard let entries = try? fileManager.contentsOfDirectory(
            at: sourceDir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            return 0
        }

        let dbNames = Set(databaseFileNames)
        var copiedCount = 0

        for srcURL in entries {
            let name = srcURL.lastPathComponent
            if dbNames.contains(name)
                || name == fileMigrationMarkerName
                || name.hasSuffix(".migrating")
            {
                continue
            }

            let isDir = (try? srcURL.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
            let dstURL = targetDir.appendingPathComponent(name, isDirectory: isDir)

            if isDir {
                copiedCount += mergeDirectory(from: srcURL, to: dstURL, fileManager: fileManager)
            } else {
                let dstExists = fileManager.fileExists(atPath: dstURL.path)
                let shouldOverwrite = overwriteDerivedCaches && derivedCacheFileNames.contains(name)
                if dstExists {
                    guard shouldOverwrite else { continue }
                    try? fileManager.removeItem(at: dstURL)
                }
                if (try? fileManager.copyItem(at: srcURL, to: dstURL)) != nil {
                    copiedCount += 1
                }
            }
        }

        return copiedCount
    }

    private static func mergeDirectory(
        from sourceDir: URL,
        to targetDir: URL,
        fileManager: FileManager
    ) -> Int {
        if !fileManager.fileExists(atPath: targetDir.path) {
            do {
                try fileManager.createDirectory(at: targetDir, withIntermediateDirectories: true)
            } catch {
                return 0
            }
        }

        guard let entries = try? fileManager.contentsOfDirectory(
            at: sourceDir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            return 0
        }

        var copiedCount = 0
        for srcURL in entries {
            let isDir = (try? srcURL.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
            let dstURL = targetDir.appendingPathComponent(srcURL.lastPathComponent, isDirectory: isDir)
            if isDir {
                copiedCount += mergeDirectory(from: srcURL, to: dstURL, fileManager: fileManager)
            } else if !fileManager.fileExists(atPath: dstURL.path) {
                if (try? fileManager.copyItem(at: srcURL, to: dstURL)) != nil {
                    copiedCount += 1
                }
            }
        }
        return copiedCount
    }

    private static func fileSizeBytes(at url: URL, fileManager: FileManager) -> Int64 {
        guard fileManager.fileExists(atPath: url.path),
              let attrs = try? fileManager.attributesOfItem(atPath: url.path),
              let size = (attrs[.size] as? NSNumber)?.int64Value
        else {
            return 0
        }
        return size
    }

    private static func writeMarker(at markerURL: URL, directory: URL, fileManager: FileManager) {
        if !fileManager.fileExists(atPath: directory.path) {
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        let payload = Data("migrated_v1\n".utf8)
        try? payload.write(to: markerURL, options: .atomic)
    }

    // MARK: - UserDefaults Migration

    private static func migrateUserDefaultsIfNeeded(
        from sourceDefaults: UserDefaults?,
        to targetDefaults: UserDefaults?,
        replacedEmptyDB: Bool
    ) -> Int {
        guard let source = sourceDefaults,
              let target = targetDefaults,
              source !== target
        else {
            return 0
        }

        var copied = 0
        if !target.bool(forKey: userDefaultsMigrationMarkerKey) {
            let snapshot = source.dictionaryRepresentation()
            for (key, value) in snapshot where shouldMigrateUserDefaultsKey(key) {
                if target.object(forKey: key) == nil {
                    target.set(value, forKey: key)
                    copied += 1
                }
            }
            target.set(true, forKey: userDefaultsMigrationMarkerKey)
        }

        if replacedEmptyDB {
            if let sourceFlag = source.object(forKey: examSelfHealVersionKey) {
                target.set(sourceFlag, forKey: examSelfHealVersionKey)
            } else {
                target.removeObject(forKey: examSelfHealVersionKey)
            }
        }

        return copied
    }
}
