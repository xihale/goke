import Foundation
import Testing
@testable import GokeCoreModel

@Suite("AppGroup Fallback Migration")
struct AppGroupMigrationTests {
    private func makeTempDir(_ label: String) throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("goke-appgroup-mig-\(label)-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func seedDatabase(at dbURL: URL, taskTitle: String) async throws {
        let store = try SQLiteStore(path: dbURL.path, deferOpen: false)
        let repo = RepositoryActor(store: store)
        _ = try await repo.load()
        let task = GokeTask(
            id: "task-\(UUID().uuidString)",
            title: taskTitle,
            updatedAt: 1_700_000_000_000
        )
        try await repo.commit(
            ChangeSet(description: "seed", source: .MANUAL, changes: [.add(after: .task(task))]),
            record: true
        )
    }

    @Test("Migrates fallback SQLite DB, WAL/SHM, companion caches, subdirectories, and UserDefaults cleanly")
    func migratesFallbackToEmptyAppGroup() async throws {
        let root = try makeTempDir("fresh")
        defer { try? FileManager.default.removeItem(at: root) }

        let fallbackDir = root.appendingPathComponent("FallbackGokeData", isDirectory: true)
        let appGroupDir = root.appendingPathComponent("AppGroup", isDirectory: true)
        try FileManager.default.createDirectory(at: fallbackDir, withIntermediateDirectories: true)

        let fallbackDB = fallbackDir.appendingPathComponent("goke.db")
        try await seedDatabase(at: fallbackDB, taskTitle: "高等数学作业")

        // Create companion files & subdirectories inside fallback GokeData
        try Data("cache-bytes".utf8).write(
            to: fallbackDir.appendingPathComponent(LaunchTimetableCache.defaultFileName)
        )
        try Data("{\"ok\":true}".utf8).write(
            to: fallbackDir.appendingPathComponent(HotWaterWidgetSnapshot.defaultFileName)
        )
        let todoAttachmentsDir = fallbackDir.appendingPathComponent("todo_attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: todoAttachmentsDir, withIntermediateDirectories: true)
        try Data("img-bytes".utf8).write(to: todoAttachmentsDir.appendingPathComponent("pic1.jpg"))

        let backupsDir = fallbackDir.appendingPathComponent("Backups", isDirectory: true)
        try FileManager.default.createDirectory(at: backupsDir, withIntermediateDirectories: true)
        try Data("backup-json".utf8).write(to: backupsDir.appendingPathComponent("auto.json"))

        // Configure isolated source & target UserDefaults suites
        let srcSuiteName = "test.goke.src.\(UUID().uuidString)"
        let dstSuiteName = "test.goke.dst.\(UUID().uuidString)"
        let srcDefaults = try #require(UserDefaults(suiteName: srcSuiteName))
        let dstDefaults = try #require(UserDefaults(suiteName: dstSuiteName))
        defer {
            srcDefaults.removePersistentDomain(forName: srcSuiteName)
            dstDefaults.removePersistentDomain(forName: dstSuiteName)
        }

        let settingsBlob = Data("{\"preset\":\"mint\"}".utf8)
        srcDefaults.set(settingsBlob, forKey: "app.goke.ios.settings.v1")
        srcDefaults.set(Data("{\"dormLabel\":\"东十三\"}".utf8), forKey: "app.goke.ios.hotwater.config.v1")
        srcDefaults.set(1234, forKey: "app.goke.ios.hotwater.canUseAmount.v1")
        srcDefaults.set(true, forKey: "goke_exam_self_heal_v1")
        srcDefaults.set("goke://quickadd", forKey: "app.goke.ios.pendingDeepLink")
        srcDefaults.set("unrelated", forKey: "com.apple.system.unrelated")

        let report = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: appGroupDir,
            sourceDefaults: srcDefaults,
            targetDefaults: dstDefaults,
            isAppGroupAvailable: true,
            isExtension: false
        )

        #expect(report.migratedDatabase == true)
        #expect(report.copiedCompanionItems >= 4)
        #expect(report.migratedUserDefaultsKeys == 4)

        // Verify migrated DB has the real task
        let migratedStore = try SQLiteStore(path: appGroupDir.appendingPathComponent("goke.db").path, deferOpen: false)
        let loaded = try await migratedStore.loadSnapshot()
        #expect(loaded.tasks.map(\.title) == ["高等数学作业"])

        // Verify companion files & subdirectories copied
        #expect(FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent(LaunchTimetableCache.defaultFileName).path))
        #expect(FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent(HotWaterWidgetSnapshot.defaultFileName).path))
        #expect(FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent("todo_attachments/pic1.jpg").path))
        #expect(FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent("Backups/auto.json").path))
        #expect(FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent(AppGroupStorageMigrator.fileMigrationMarkerName).path))

        // Verify fallback stays intact as backup
        #expect(FileManager.default.fileExists(atPath: fallbackDB.path))

        // Verify UserDefaults migration (and exclusions)
        #expect(dstDefaults.data(forKey: "app.goke.ios.settings.v1") == settingsBlob)
        #expect(dstDefaults.integer(forKey: "app.goke.ios.hotwater.canUseAmount.v1") == 1234)
        #expect(dstDefaults.bool(forKey: "goke_exam_self_heal_v1") == true)
        #expect(dstDefaults.object(forKey: "app.goke.ios.pendingDeepLink") == nil)
        #expect(dstDefaults.object(forKey: "com.apple.system.unrelated") == nil)

        // Idempotency: mutate App Group DB + prefs, re-run migration -> must NOT overwrite!
        try await seedDatabase(at: appGroupDir.appendingPathComponent("goke.db"), taskTitle: "新增任务")
        dstDefaults.set(9999, forKey: "app.goke.ios.hotwater.canUseAmount.v1")

        let secondReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: appGroupDir,
            sourceDefaults: srcDefaults,
            targetDefaults: dstDefaults,
            isAppGroupAvailable: true,
            isExtension: false
        )
        #expect(secondReport == AppGroupMigrationReport())
        let reloaded = try await migratedStore.loadSnapshot()
        #expect(Set(reloaded.tasks.map(\.title)) == Set(["高等数学作业", "新增任务"]))
        #expect(dstDefaults.integer(forKey: "app.goke.ios.hotwater.canUseAmount.v1") == 9999)
    }

    @Test("Replaces empty schema-only or zero-byte App Group DB with populated fallback DB, never overwrites populated App Group DB")
    func handlesEmptySchemaAndPopulatedTargets() async throws {
        let root = try makeTempDir("states")
        defer { try? FileManager.default.removeItem(at: root) }

        let fallbackDir = root.appendingPathComponent("Fallback", isDirectory: true)
        let emptySchemaGroupDir = root.appendingPathComponent("EmptySchemaGroup", isDirectory: true)
        let populatedGroupDir = root.appendingPathComponent("PopulatedGroup", isDirectory: true)
        let zeroByteGroupDir = root.appendingPathComponent("ZeroByteGroup", isDirectory: true)
        for dir in [fallbackDir, emptySchemaGroupDir, populatedGroupDir, zeroByteGroupDir] {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }

        // Seed fallback with real user data
        try await seedDatabase(at: fallbackDir.appendingPathComponent("goke.db"), taskTitle: "原始真实数据")

        // 1) Empty schema-only target DB (e.g. opened once before migration existed)
        _ = try SQLiteStore(path: emptySchemaGroupDir.appendingPathComponent("goke.db").path, deferOpen: false)
        let emptyReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: emptySchemaGroupDir,
            sourceDefaults: nil,
            targetDefaults: nil,
            isAppGroupAvailable: true,
            isExtension: false
        )
        #expect(emptyReport.migratedDatabase == true)
        let recoveredStore = try SQLiteStore(path: emptySchemaGroupDir.appendingPathComponent("goke.db").path, deferOpen: false)
        let recovered = try await recoveredStore.loadSnapshot()
        #expect(recovered.tasks.map(\.title) == ["原始真实数据"])

        // 2) Zero-byte target DB
        try Data().write(to: zeroByteGroupDir.appendingPathComponent("goke.db"))
        let zeroReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: zeroByteGroupDir,
            sourceDefaults: nil,
            targetDefaults: nil,
            isAppGroupAvailable: true,
            isExtension: false
        )
        #expect(zeroReport.migratedDatabase == true)

        // 3) Populated target App Group DB must NEVER be overwritten
        try await seedDatabase(at: populatedGroupDir.appendingPathComponent("goke.db"), taskTitle: "AppGroup现有数据")
        let populatedReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: populatedGroupDir,
            sourceDefaults: nil,
            targetDefaults: nil,
            isAppGroupAvailable: true,
            isExtension: false
        )
        #expect(populatedReport.migratedDatabase == false)
        let existingStore = try SQLiteStore(path: populatedGroupDir.appendingPathComponent("goke.db").path, deferOpen: false)
        let existing = try await existingStore.loadSnapshot()
        #expect(existing.tasks.map(\.title) == ["AppGroup现有数据"])
    }

    @Test("WidgetExtension and unavailable App Group never trigger migration")
    func skipsForExtensionOrUnavailableAppGroup() async throws {
        let root = try makeTempDir("guard")
        defer { try? FileManager.default.removeItem(at: root) }

        let fallbackDir = root.appendingPathComponent("Fallback", isDirectory: true)
        let appGroupDir = root.appendingPathComponent("AppGroup", isDirectory: true)
        try FileManager.default.createDirectory(at: fallbackDir, withIntermediateDirectories: true)
        try await seedDatabase(at: fallbackDir.appendingPathComponent("goke.db"), taskTitle: "用户数据")

        let extReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: appGroupDir,
            sourceDefaults: nil,
            targetDefaults: nil,
            isAppGroupAvailable: true,
            isExtension: true
        )
        #expect(extReport == AppGroupMigrationReport())
        #expect(!FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent("goke.db").path))

        let unavailReport = AppGroupStorageMigrator.migrateIfNeeded(
            fallbackRootURL: fallbackDir,
            targetRootURL: appGroupDir,
            sourceDefaults: nil,
            targetDefaults: nil,
            isAppGroupAvailable: false,
            isExtension: false
        )
        #expect(unavailReport == AppGroupMigrationReport())
        #expect(!FileManager.default.fileExists(atPath: appGroupDir.appendingPathComponent("goke.db").path))
    }
}
