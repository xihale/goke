import Foundation
import Testing
@testable import GokeCore

private final class StepClock: @unchecked Sendable {
    private let lock = NSLock()
    private var current: Int64

    init(start: Int64) {
        self.current = start
    }

    func next() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        current += 1000
        return current
    }
}

@Suite("SQLite WAL Storage, RepositoryActor & Undo/Redo")
struct StorageUndoTests {

    private func tempDBPath() -> String {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("goke-core-tests-\(UUID().uuidString)", isDirectory: true)
        return dir.appendingPathComponent("goke.db").path
    }

    @Test("SQLiteStore enables WAL mode and persists across reopen")
    func sqliteWalPersistence() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        do {
            let store = try SQLiteStore(path: path)
            let mode = try await store.journalMode()
            #expect(mode.lowercased() == "wal")

            let task = GokeTask(id: "t-wal", title: "WAL Test", updatedAt: 12345)
            try await store.write(upserts: [.task(task)])
        }

        let reopened = try SQLiteStore(path: path)
        let loaded = try await reopened.loadRecords()
        #expect(loaded.count == 1)
        #expect(loaded.first?.id == "t-wal")
    }

    @Test("RepositoryActor commit, undo, redo, content-equality guard, and pendingPurged tracking")
    func repositoryUndoRedoAndPurgedTombstones() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let clock = StepClock(start: 1_700_000_000_000)
        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store, clock: { clock.next() })
        await repo.setTrackPurges(true)

        let initialSnap = try await repo.load()
        // Auto-creates default semester
        #expect(initialSnap.semesters.count == 1)

        // 1. Add a task via ChangeSet
        let task = GokeTask(id: "t-1", title: "买牛奶")
        let addCS = ChangeSet(id: "op-add", description: "Add task", changes: [.add(after: .task(task))])
        try await repo.commit(addCS)

        var snap = await repo.snapshot
        #expect(snap.tasks.count == 1)
        #expect(snap.tasks[0].title == "买牛奶")

        // 2. Modify the task
        let modifiedTask = GokeTask(id: "t-1", title: "买燕麦奶")
        let modCS = ChangeSet(
            id: "op-mod",
            description: "Edit task",
            changes: [.modify(before: .task(task), after: .task(modifiedTask))]
        )
        try await repo.commit(modCS)

        // Content-equality guard: cannot undo op-add while op-mod is still applied
        let blockedUndo = try await repo.undo(opId: "op-add")
        #expect(!blockedUndo)

        // Undo op-mod succeeds
        let undoneMod = try await repo.undo(opId: "op-mod")
        #expect(undoneMod)
        snap = await repo.snapshot
        #expect(snap.tasks[0].title == "买牛奶")

        // Now undo op-add succeeds, purges task, and writes pendingPurged tombstone because trackPurges == true
        let undoneAdd = try await repo.undo(opId: "op-add")
        #expect(undoneAdd)
        snap = await repo.snapshot
        #expect(snap.tasks.isEmpty)

        let pending = try await repo.pendingPurged()
        #expect(pending.count == 1)
        #expect(pending[0].id == "t-1")
        #expect(pending[0].deletedAt != nil)

        // UndoHistory suppresses stale remote records with updatedAt <= tombstone.updatedAt
        let history = try await repo.undoHistory()
        let staleRemote = Record.task(GokeTask(id: "t-1", title: "Stale", updatedAt: pending[0].updatedAt - 1))
        let newerRemote = Record.task(GokeTask(id: "t-1", title: "Newer", updatedAt: pending[0].updatedAt + 1))
        #expect(history.isSuppressedByPendingPurge(staleRemote))
        #expect(!history.isSuppressedByPendingPurge(newerRemote))

        // Redo op-add then op-mod restores final state
        #expect(try await repo.redo(opId: "op-add"))
        #expect(try await repo.redo(opId: "op-mod"))
        snap = await repo.snapshot
        #expect(snap.tasks.first?.title == "买燕麦奶")

        // Clear purged
        try await repo.clearPurged(ids: ["t-1"])
        #expect(try await repo.pendingPurged().isEmpty)
    }

    @Test("RepositoryActor auto-purges trash older than 30 days on load and applies LWW remote updates")
    func trashAutoPurgeAndRemoteLWW() async throws {
        let store = try SQLiteStore(path: ":memory:")
        let now: Int64 = 1_800_000_000_000
        let thirtyOneDaysMs: Int64 = 31 * 24 * 3_600_000
        let fiveDaysMs: Int64 = 5 * 24 * 3_600_000

        let expired = GokeTask(id: "t-old", title: "Expired", updatedAt: now - thirtyOneDaysMs, deletedAt: now - thirtyOneDaysMs)
        let recentTrash = GokeTask(id: "t-recent", title: "Recent Trash", updatedAt: now - fiveDaysMs, deletedAt: now - fiveDaysMs)
        let liveTask = GokeTask(id: "t-live", title: "Local V2", updatedAt: now - 1000)
        try await store.write(upserts: [.task(expired), .task(recentTrash), .task(liveTask)])

        let repo = RepositoryActor(store: store, clock: { now })
        let snap = try await repo.load()

        #expect(snap.find(id: "t-old") == nil)
        #expect(snap.find(id: "t-recent") != nil)

        // LWW remote apply: older ignored, newer wins
        let staleRemote = Record.task(GokeTask(id: "t-live", title: "Remote V1", updatedAt: now - 2000))
        let newerRemote = Record.task(GokeTask(id: "t-live", title: "Remote V3", updatedAt: now + 500))
        let written1 = try await repo.applyRemote([staleRemote])
        #expect(written1 == 0)
        let written2 = try await repo.applyRemote([newerRemote])
        #expect(written2 == 1)
        if case .task(let t) = await repo.snapshot.find(id: "t-live") {
            #expect(t.title == "Remote V3")
        } else {
            Issue.record("Expected live task")
        }
    }

    @Test("RepositoryActor snapshotStream yields initial and updated snapshots")
    func snapshotStreamEmitsUpdates() async throws {
        let store = try SQLiteStore(path: ":memory:")
        let repo = RepositoryActor(store: store, clock: { 1000 })
        try await repo.load()

        let stream = await repo.snapshotStream()
        var iterator = stream.makeAsyncIterator()
        let first = await iterator.next()
        #expect(first?.semesters.count == 1)
        #expect(first?.tasks.isEmpty == true)

        let task = GokeTask(id: "t-stream", title: "Stream Task")
        try await repo.commit(ChangeSet(id: "op-s", changes: [.add(after: .task(task))]))
        let second = await iterator.next()
        #expect(second?.tasks.count == 1)
        #expect(second?.tasks[0].id == "t-stream")
    }

    @Test("AppSnapshot.putBatch, deletedRecordCount, and SQLiteStore in-transaction trimOpsKeep")
    func batchUpsertAndTrimOpsKeep() async throws {
        let store = try SQLiteStore(path: ":memory:")
        let repo = RepositoryActor(store: store, clock: { 2000 })
        try await repo.load()

        let batch: [Record] = (1...20).map { i in
            .task(GokeTask(id: "bt-\(i)", title: "Batch \(i)", updatedAt: Int64(2000 + i), deletedAt: i <= 3 ? 2500 : nil))
        }
        let applied = try await repo.applyRemote(batch)
        #expect(applied == 20)
        let snap = await repo.snapshot
        #expect(snap.tasks.count == 20)
        #expect(snap.deletedRecordCount == 3)
        #expect(snap.live().tasks.count == 17)

        // Verify in-transaction trimOpsKeep prunes oplog down to `keep` rows.
        for i in 1...5 {
            let op = OpRow(id: "op-\(i)", createdAt: Int64(3000 + i), description: "d\(i)", source: "USER", json: "{}", undoneAt: nil)
            try await store.write(op: op, trimOpsKeep: i == 5 ? 2 : nil)
        }
        let remainingOps = try await store.ops(limit: 10)
        #expect(remainingOps.count == 2)
        #expect(remainingOps.map(\.id) == ["op-5", "op-4"])
    }

    @Test("SQLiteStore opens lazily by default and supports kind-switched loadSnapshot with SQL trash purge")
    func sqliteLazyOpenAndOptimizedLoadSnapshot() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        // Lazy init does NOT touch disk or open SQLite handle
        let lazyStore = SQLiteStore(lazyPath: path)
        #expect(await !lazyStore.isOpen)
        #expect(!FileManager.default.fileExists(atPath: path))

        let sem = Semester(id: "sem-1", name: "2026秋", week1Monday: LocalDate(year: 2026, month: 9, day: 7))
        let course = Course(id: "c-1", semesterId: "sem-1", name: "高等数学", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1, 2, 3])
        let exc = CourseException(id: "ex-1", courseId: "c-1", week: 2, type: .CANCEL)
        let ev = Event(id: "ev-1", title: "讲座", start: LocalDateTime(year: 2026, month: 9, day: 10, hour: 19, minute: 0), end: LocalDateTime(year: 2026, month: 9, day: 10, hour: 21, minute: 0))
        let list = TaskList(id: "l-1", name: "学习", colorIndex: 2)
        let liveTask = GokeTask(id: "t-1", title: "作业", listId: "l-1", updatedAt: 5000)
        let expiredTask = GokeTask(id: "t-expired", title: "旧垃圾", updatedAt: 1000, deletedAt: 1000)
        let recentTrash = GokeTask(id: "t-recent-trash", title: "近垃圾", updatedAt: 4000, deletedAt: 4000)

        // First actor call opens SQLite lazily
        try await lazyStore.write(upserts: [
            .semester(sem),
            .course(course),
            .courseException(exc),
            .event(ev),
            .taskList(list),
            .task(liveTask),
            .task(expiredTask),
            .task(recentTrash),
        ])
        #expect(await lazyStore.isOpen)

        let snap = try await lazyStore.loadSnapshot(purgeDeletedBefore: 2000)
        #expect(snap.semesters.map(\.id) == ["sem-1"])
        #expect(snap.courses.map(\.id) == ["c-1"])
        #expect(snap.exceptions.map(\.id) == ["ex-1"])
        #expect(snap.events.map(\.id) == ["ev-1"])
        #expect(snap.lists.map(\.id) == ["l-1"])
        #expect(Set(snap.tasks.map(\.id)) == ["t-1", "t-recent-trash"])
        #expect(snap.find(id: "t-expired") == nil)

        // Verify direct decode supports both syncKind and discriminator spellings
        let encodedCourse = try MintJSON.makeEncoder().encode(Record.course(course))
        #expect(try Record.decodeDirect(kind: "Course", from: encodedCourse) == .course(course))
        #expect(try Record.decodeDirect(kind: "course", from: encodedCourse) == .course(course))
        #expect(try Record.decodeDirect(kind: "unknown_legacy", from: encodedCourse) == .course(course))
    }

    @Test("LaunchTimetableCache binary plist round-trip filters active timetable window and RepositoryActor refreshes it")
    func launchTimetableCacheRoundTripAndRepositoryAutoRefresh() async throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("goke-launch-cache-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let cacheURL = dir.appendingPathComponent(LaunchTimetableCache.defaultFileName)
        let nowDate = LocalDate(year: 2026, month: 9, day: 15).atTime(hour: 10, minute: 0).toDate(in: .current)

        let activeSem = Semester(id: "sem-active", name: "当前学期", week1Monday: LocalDate(year: 2026, month: 9, day: 7), isCurrent: true)
        let oldSem = Semester(id: "sem-old", name: "旧学期", week1Monday: LocalDate(year: 2026, month: 3, day: 2), isCurrent: false)
        let activeCourse = Course(
            id: "c-active",
            semesterId: "sem-active",
            name: "数据结构",
            teacher: "张老师",
            room: "教1-202",
            dayOfWeek: 2,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [1, 2, 3, 4],
            startTime: "10:25",
            endTime: "12:00"
        )
        let oldCourse = Course(id: "c-old", semesterId: "sem-old", name: "旧课程", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1])
        let activeExc = CourseException(id: "ex-active", courseId: "c-active", week: 2, type: .RESCHEDULE, newWeek: 3, newDayOfWeek: 4, newStartPeriod: 5, newEndPeriod: 6, newRoom: "实验楼301")
        let oldExc = CourseException(id: "ex-old", courseId: "c-old", week: 1, type: .CANCEL)

        let nearbyEvent = Event(
            id: "ev-near",
            title: "组会",
            start: LocalDateTime(year: 2026, month: 9, day: 16, hour: 14, minute: 0),
            end: LocalDateTime(year: 2026, month: 9, day: 16, hour: 15, minute: 30)
        )
        let farEvent = Event(
            id: "ev-far",
            title: "三个月后大会",
            start: LocalDateTime(year: 2026, month: 12, day: 20, hour: 14, minute: 0),
            end: LocalDateTime(year: 2026, month: 12, day: 20, hour: 15, minute: 0)
        )
        let repeatingEvent = Event(
            id: "ev-rep",
            title: "每周跑步",
            start: LocalDateTime(year: 2026, month: 8, day: 1, hour: 18, minute: 0),
            end: LocalDateTime(year: 2026, month: 8, day: 1, hour: 19, minute: 0),
            recurrence: Recurrence(freq: .WEEKLY, interval: 1, byDays: [3])
        )
        let allDayEvent = Event(
            id: "ev-allday",
            title: "中秋节",
            start: LocalDateTime(year: 2026, month: 9, day: 17, hour: 0, minute: 0),
            end: LocalDateTime(year: 2026, month: 9, day: 17, hour: 23, minute: 59),
            allDay: true
        )

        let nearBlockTask = GokeTask(
            id: "t-block-near",
            title: "复习线代",
            listId: "list-study",
            blockStart: LocalDateTime(year: 2026, month: 9, day: 15, hour: 15, minute: 0),
            blockMinutes: 60
        )
        let farBlockTask = GokeTask(
            id: "t-block-far",
            title: "遥远时间块",
            blockStart: LocalDateTime(year: 2026, month: 11, day: 15, hour: 15, minute: 0),
            blockMinutes: 60
        )
        let repeatingBlockTask = GokeTask(
            id: "t-block-rep",
            title: "每日背词",
            repeat: Recurrence(freq: .DAILY, interval: 1),
            blockStart: LocalDateTime(year: 2026, month: 8, day: 1, hour: 21, minute: 0),
            blockMinutes: 30
        )
        let unscheduledTodo = GokeTask(id: "t-unscheduled", title: "买洗衣液")
        let syncedAndroidParcel = GokeTask(
            id: "t-parcel-synced",
            title: "丰巢取件",
            due: LocalDateTime(year: 2026, month: 9, day: 15, hour: 22, minute: 0),
            blockStart: LocalDateTime(year: 2026, month: 9, day: 15, hour: 14, minute: 0),
            blockMinutes: 480,
            source: .PARCEL,
            extra: ["code": "12-3-4567", "place": "南区菜鸟"]
        )
        let deletedCourse = Course(id: "c-deleted", semesterId: "sem-active", name: "已删课程", dayOfWeek: 5, startPeriod: 1, endPeriod: 2, weeks: [1], deletedAt: 100)
        let studyList = TaskList(id: "list-study", name: "学习", colorIndex: 3)

        let fullSnapshot = AppSnapshot(
            semesters: [activeSem, oldSem],
            courses: [activeCourse, oldCourse, deletedCourse],
            exceptions: [activeExc, oldExc],
            events: [nearbyEvent, farEvent, repeatingEvent, allDayEvent],
            tasks: [nearBlockTask, farBlockTask, repeatingBlockTask, unscheduledTodo, syncedAndroidParcel],
            lists: [studyList]
        )

        // Build, save as binary plist, and load synchronously via .mappedIfSafe
        try LaunchTimetableCache.saveSync(from: fullSnapshot, to: cacheURL, now: nowDate)
        let loadedPayload = try #require(LaunchTimetableCache.loadPayloadSync(from: cacheURL, now: nowDate))
        #expect(loadedPayload.schemaVersion == LaunchTimetableCache.currentSchemaVersion)
        #expect(loadedPayload.activeSemester == activeSem)
        #expect(loadedPayload.courses == [activeCourse])
        #expect(loadedPayload.exceptions == [activeExc])
        #expect(Set(loadedPayload.events.map(\.id)) == ["ev-near", "ev-rep"])
        #expect(Set(loadedPayload.blockTasks.map(\.id)) == ["t-block-near", "t-block-rep"])
        #expect(loadedPayload.lists == [studyList])

        let partial = try #require(LaunchTimetableCache.loadSync(from: cacheURL, now: nowDate))
        #expect(partial.currentSemester == activeSem)
        #expect(partial.courses == [activeCourse])
        #expect(partial.exceptions == [activeExc])
        #expect(Set(partial.tasks.map(\.id)) == ["t-block-near", "t-block-rep"])

        // Verify >7-day staleness expiry rejects outdated launch cache payloads
        let staleDate = nowDate.addingTimeInterval(8 * 86_400)
        #expect(loadedPayload.isExpired(now: staleDate))
        #expect(LaunchTimetableCache.loadPayloadSync(from: cacheURL, now: staleDate) == nil)
        #expect(LaunchTimetableCache.loadSync(from: cacheURL, now: staleDate) == nil)

        // Ensure TimetableLayout renders cleanly from the partial snapshot
        let layout = TimetableLayout.buildWeek(snapshot: partial, week: 2)
        #expect(layout != nil)

        // Verify RepositoryActor wires loadWithDerived + commit -> LaunchTimetableCache refresh
        let dbPath = dir.appendingPathComponent("repo.db").path
        let repoCacheURL = dir.appendingPathComponent("repo_launch_cache.plist")
        let store = SQLiteStore(lazyPath: dbPath)
        let repo = RepositoryActor(
            store: store,
            launchCacheURL: repoCacheURL,
            clock: { Int64(nowDate.timeIntervalSince1970 * 1000) }
        )

        let derived = try await repo.loadWithDerived()
        #expect(derived.live.currentSemester != nil)
        #expect(derived.deletedRecordCount == 0)
        await repo.flushLaunchCacheNow()
        let cachedAfterLoad = try #require(LaunchTimetableCache.loadSync(from: repoCacheURL, now: nowDate))
        #expect(cachedAfterLoad.currentSemester?.id == derived.live.currentSemester?.id)

        let semId = try #require(derived.live.currentSemester?.id)
        let newCourse = Course(id: "c-repo", semesterId: semId, name: "操作系统", dayOfWeek: 3, startPeriod: 1, endPeriod: 2, weeks: [1, 2])
        try await repo.commit(ChangeSet(id: "op-c", description: "Add course", changes: [.add(after: .course(newCourse))]))
        await repo.flushLaunchCacheNow()

        let cachedAfterCommit = try #require(LaunchTimetableCache.loadSync(from: repoCacheURL, now: nowDate))
        #expect(cachedAfterCommit.courses.map(\.id) == ["c-repo"])

        LaunchTimetableCache.invalidate(at: repoCacheURL)
        #expect(LaunchTimetableCache.loadSync(from: repoCacheURL, now: nowDate) == nil)
    }

    @Test("SQLiteStore lowMemory mode opens cleanly and supports full CRUD")
    func sqliteStoreLowMemoryMode() async throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("goke-lowmem-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let path = dir.appendingPathComponent("lowmem.db").path

        let store = try SQLiteStore(path: path, deferOpen: false, lowMemory: true)
        #expect(store.lowMemory == true)
        #expect(await store.isOpen)

        let task = GokeTask(id: "t-lowmem", title: "低内存模式读写验证")
        try await store.write(upserts: [.task(task)])
        let loaded = try await store.loadSnapshot()
        #expect(loaded.tasks.map(\.id) == ["t-lowmem"])

        let lazyLowMem = SQLiteStore(lazyPath: path, lowMemory: true)
        #expect(lazyLowMem.lowMemory == true)
        #expect(await !lazyLowMem.isOpen)
        let reopened = try await lazyLowMem.loadSnapshot()
        #expect(reopened.tasks.map(\.id) == ["t-lowmem"])
    }
}
