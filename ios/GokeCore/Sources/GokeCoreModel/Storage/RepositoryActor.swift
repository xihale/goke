import Foundation

/// Actor-isolated single-source-of-truth repository matching Android's `Repository.kt`.
/// All mutations go through `commit(_:record:)`, preserving undo/redo, oplog, and `pendingPurged` sync tombstones.
public actor RepositoryActor {
    public static let trashRetentionDays: Int64 = 30

    private let store: SQLiteStore
    private let idGenerator: @Sendable () -> String
    private let clock: @Sendable () -> Int64

    public private(set) var snapshot: AppSnapshot = AppSnapshot()
    public private(set) var history: [OpRow] = []
    public private(set) var isReady: Bool = false
    public private(set) var trackPurges: Bool = false

    private static let trimOpsInterval = 32
    private static let launchCacheDebounceNanoseconds: UInt64 = 150_000_000

    private var launchCacheURL: URL?
    private var launchCacheTask: Task<Void, Never>?
    private var lastWrittenCachePayload: LaunchTimetableCache?

    private var historyLoaded: Bool = false
    private var commitsSinceTrim: Int = 0
    private var continuations: [UUID: AsyncStream<AppSnapshot>.Continuation] = [:]

    public init(
        store: SQLiteStore,
        launchCacheURL: URL? = nil,
        idGenerator: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
        clock: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.store = store
        self.launchCacheURL = launchCacheURL
        self.idGenerator = idGenerator
        self.clock = clock
    }

    public func setTrackPurges(_ enabled: Bool) {
        self.trackPurges = enabled
    }

    /// Configures (or disables when `nil`) automatic off-main-thread `LaunchTimetableCache` refreshes.
    public func setLaunchCacheURL(_ url: URL?) {
        self.launchCacheURL = url
        self.lastWrittenCachePayload = nil
    }

    /// Immediately flushes the current snapshot to `launchCacheURL` (if configured), cancelling any pending debounced write.
    public func flushLaunchCacheNow() async {
        launchCacheTask?.cancel()
        launchCacheTask = nil
        guard let url = launchCacheURL else { return }
        let snap = snapshot
        let nowDate = Date(timeIntervalSince1970: Double(clock()) / 1000.0)
        let previous = lastWrittenCachePayload
        let updated = await Task.detached(priority: .utility) {
            Self.persistLaunchCache(snapshot: snap, to: url, now: nowDate, previousPayload: previous)
        }.value
        if let updated {
            lastWrittenCachePayload = updated
        } else if snap.live().currentSemester == nil {
            lastWrittenCachePayload = nil
        }
    }

    /// Subscribes to snapshot updates; immediately yields the current snapshot if already loaded.
    public func snapshotStream() -> AsyncStream<AppSnapshot> {
        let token = UUID()
        let current = snapshot
        let ready = isReady
        return AsyncStream { continuation in
            continuations[token] = continuation
            if ready {
                continuation.yield(current)
            }
            continuation.onTermination = { [weak self] _ in
                Task { await self?.removeContinuation(token) }
            }
        }
    }

    private func removeContinuation(_ token: UUID) {
        continuations.removeValue(forKey: token)
    }

    private func publish(_ next: AppSnapshot, immediateCacheRefresh: Bool = false) {
        snapshot = next
        for continuation in continuations.values {
            continuation.yield(next)
        }
        scheduleLaunchCacheRefresh(for: next, immediate: immediateCacheRefresh)
    }

    private func scheduleLaunchCacheRefresh(for next: AppSnapshot, immediate: Bool) {
        guard let url = launchCacheURL else { return }
        launchCacheTask?.cancel()
        let nowDate = Date(timeIntervalSince1970: Double(clock()) / 1000.0)
        let previous = lastWrittenCachePayload
        let delay = immediate ? UInt64(0) : Self.launchCacheDebounceNanoseconds
        launchCacheTask = Task.detached(priority: .utility) { [weak self] in
            if delay > 0 {
                try? await Task.sleep(nanoseconds: delay)
            }
            guard !Task.isCancelled else { return }
            let written = Self.persistLaunchCache(
                snapshot: next,
                to: url,
                now: nowDate,
                previousPayload: previous
            )
            guard !Task.isCancelled else { return }
            await self?.recordWrittenLaunchCache(written, hadActiveSemester: next.live().currentSemester != nil)
        }
    }

    private func recordWrittenLaunchCache(_ payload: LaunchTimetableCache?, hadActiveSemester: Bool) {
        if let payload {
            lastWrittenCachePayload = payload
        } else if !hadActiveSemester {
            lastWrittenCachePayload = nil
        }
    }

    private static func persistLaunchCache(
        snapshot: AppSnapshot,
        to url: URL,
        now: Date,
        previousPayload: LaunchTimetableCache?
    ) -> LaunchTimetableCache? {
        let payload = LaunchTimetableCache.buildPayload(from: snapshot, now: now)
        if payload.activeSemester == nil {
            LaunchTimetableCache.invalidate(at: url)
            return nil
        }
        if let previousPayload,
           previousPayload.contentEquals(payload),
           FileManager.default.fileExists(atPath: url.path)
        {
            return previousPayload
        }
        do {
            try LaunchTimetableCache.savePayloadSync(payload, to: url)
            return payload
        } catch {
            return nil
        }
    }

    /// Loads records from SQLite (purging trash older than 30 days directly in SQL), ensures a default semester exists,
    /// and schedules an asynchronous `LaunchTimetableCache` refresh when `launchCacheURL` is configured.
    @discardableResult
    public func load() async throws -> AppSnapshot {
        let now = clock()
        let cutoff = now - Self.trashRetentionDays * 24 * 3_600_000
        var snap = try await store.loadSnapshot(purgeDeletedBefore: cutoff)
        snap = try await withDefaultSemester(snap, now: now)
        isReady = true
        publish(snap, immediateCacheRefresh: true)
        return snap
    }

    /// Loads the full snapshot and precomputes `live()` and `deletedRecordCount` off `@MainActor`.
    public func loadWithDerived() async throws -> (snapshot: AppSnapshot, live: AppSnapshot, deletedRecordCount: Int) {
        let snap = try await load()
        let live = snap.live()
        let deletedCount = snap.deletedRecordCount
        return (snap, live, deletedCount)
    }

    @discardableResult
    public func ensureHistoryLoaded(limit: Int = 100) async throws -> [OpRow] {
        if !historyLoaded {
            history = try await store.ops(limit: limit)
            historyLoaded = true
        }
        return history
    }

    public func undoHistory(limit: Int = 100) async throws -> UndoHistory {
        let ops = try await ensureHistoryLoaded(limit: limit)
        let purged = try await store.loadPurged()
        return UndoHistory(ops: ops, pendingPurged: purged)
    }

    /// Applies `changeSet`, persists touched records + optional oplog entry, and broadcasts the new snapshot.
    @discardableResult
    public func commit(_ changeSet: ChangeSet, record: Bool = true) async throws -> ChangeSet {
        let now = clock()
        let (next, upserts) = snapshot.applyingWithUpserts(changeSet, at: now)
        let op: OpRow?
        if record && !changeSet.isEmpty {
            op = OpRow(
                id: changeSet.id,
                createdAt: now,
                description: changeSet.description,
                source: changeSet.source.rawValue,
                json: try changeSet.toJSON(),
                undoneAt: nil
            )
        } else {
            op = nil
        }

        var trimKeep: Int? = nil
        if op != nil {
            commitsSinceTrim += 1
            if commitsSinceTrim >= Self.trimOpsInterval {
                trimKeep = 200
                commitsSinceTrim = 0
            }
        }

        try await store.write(upserts: upserts, op: op, trimOpsKeep: trimKeep)
        publish(next)
        if let op, historyLoaded {
            history.removeAll { $0.id == op.id }
            history.insert(op, at: 0)
            if history.count > 100 {
                history.removeLast(history.count - 100)
            }
        }
        return changeSet
    }

    /// Undoes an operation by `opId` if its touched records still match post-apply content.
    public func undo(opId: String) async throws -> Bool {
        let recent = try await store.ops(limit: 100)
        guard let op = recent.first(where: { $0.id == opId && $0.undoneAt == nil }),
              let cs = op.decodedChangeSet(),
              snapshot.canUndo(cs)
        else { return false }

        let now = clock()
        let (next, upserts, deleteIds) = snapshot.undoingWithMutations(cs, at: now)
        let tombstones = UndoHistory.purgeTombstones(forUndoing: cs, at: now, trackPurges: trackPurges)
        try await store.write(
            upserts: upserts,
            deletes: deleteIds,
            markUndone: (id: opId, undoneAt: now),
            purged: tombstones
        )
        publish(next)
        if historyLoaded {
            history = try await store.ops(limit: 100)
        }
        return true
    }

    /// Redoes a previously undone operation by `opId` if its touched records still match pre-apply content.
    public func redo(opId: String) async throws -> Bool {
        let recent = try await store.ops(limit: 100)
        guard let op = recent.first(where: { $0.id == opId && $0.undoneAt != nil }),
              let cs = op.decodedChangeSet(),
              snapshot.canRedo(cs)
        else { return false }

        let now = clock()
        let (next, upserts) = snapshot.applyingWithUpserts(cs, at: now)
        try await store.write(
            upserts: upserts,
            markUndone: (id: opId, undoneAt: nil)
        )
        publish(next)
        if historyLoaded {
            history = try await store.ops(limit: 100)
        }
        return true
    }

    /// Applies remote records (strictly newer or missing locally) without writing to oplog.
    @discardableResult
    public func applyRemote(_ records: [Record]) async throws -> Int {
        let localById = Dictionary(snapshot.allRecords().map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        let toWrite = records.filter { remote in
            guard let local = localById[remote.id] else { return true }
            return remote.updatedAt > local.updatedAt
        }
        guard !toWrite.isEmpty else { return 0 }
        try await store.write(upserts: toWrite)
        publish(snapshot.putBatch(toWrite))
        return toWrite.count
    }

    /// Discards all local records without tombstones (used on first sync of a pristine local DB).
    public func discardAllLocal() async throws {
        let allIds = snapshot.allRecords().map(\.id)
        try await store.write(deletes: allIds)
        publish(AppSnapshot())
    }

    public func ensureDefaultSemester() async throws {
        let next = try await withDefaultSemester(snapshot, now: clock())
        if next != snapshot {
            publish(next)
        }
    }

    public func pendingPurged() async throws -> [Record] {
        try await store.loadPurged()
    }

    public func clearPurged(ids: [String]) async throws {
        try await store.clearPurged(ids: ids)
    }

    public func restoreFromTrash(_ record: Record) async throws {
        let now = clock()
        let restored = record.withMeta(updatedAt: now, deletedAt: nil)
        try await store.write(upserts: [restored])
        publish(snapshot.put(restored))
    }

    public func purge(_ records: [Record]) async throws {
        let tombstones = trackPurges ? records.filter { $0.deletedAt != nil } : []
        let ids = records.map(\.id)
        try await store.write(deletes: ids, purged: tombstones)
        publish(snapshot.purgeIds(Set(ids)))
    }

    public func replaceAll(with newSnapshot: AppSnapshot) async throws {
        try await store.clearAll()
        try await store.write(upserts: newSnapshot.allRecords())
        history = []
        publish(newSnapshot)
    }

    private func withDefaultSemester(_ snap: AppSnapshot, now: Int64) async throws -> AppSnapshot {
        if snap.semesters.contains(where: { $0.deletedAt == nil }) {
            return snap
        }
        let sem = Semester(
            id: idGenerator(),
            name: "2026–2027 学年第一学期",
            week1Monday: GdutPeriods.week1Monday,
            totalWeeks: 20,
            periods: GdutPeriods.default,
            isCurrent: true,
            updatedAt: now
        )
        try await store.write(upserts: [.semester(sem)])
        return snap.put(.semester(sem))
    }
}
