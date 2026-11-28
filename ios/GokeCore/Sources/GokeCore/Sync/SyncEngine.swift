import Foundation
import GokeCoreModel

/// Pull-then-Push LWW sync engine matching Android's `SyncManager.kt`.
public actor SyncEngine {
    public static let defaultPullLimit = 500
    public static let defaultPushBatchSize = 500

    private let repository: RepositoryActor
    private let stateStore: any SyncStateStore
    private let transport: any HTTPTransport
    private let clock: @Sendable () -> Int64
    private let pullLimit: Int
    private let pushBatchSize: Int

    private var activeSyncTask: Task<SyncOutcome, Error>?

    public init(
        repository: RepositoryActor,
        stateStore: any SyncStateStore = InMemorySyncStateStore(),
        transport: any HTTPTransport = URLSessionHTTPTransport(),
        clock: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        pullLimit: Int = SyncEngine.defaultPullLimit,
        pushBatchSize: Int = SyncEngine.defaultPushBatchSize
    ) {
        self.repository = repository
        self.stateStore = stateStore
        self.transport = transport
        self.clock = clock
        self.pullLimit = max(1, min(pullLimit, 1000))
        self.pushBatchSize = max(1, min(pushBatchSize, 1000))
    }

    public func currentState() async -> SyncCursorState {
        await stateStore.loadState()
    }

    public func currentCredentials() async -> SyncCredentials? {
        await stateStore.loadCredentials()
    }

    public func cloudMCPEndpoint() async -> String? {
        guard let creds = await stateStore.loadCredentials() else { return nil }
        return "\(creds.account.serverURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/mcp"
    }

    public func cloudMCPConfigJSON() async -> String? {
        guard let creds = await stateStore.loadCredentials(), !creds.token.isEmpty else { return nil }
        let endpoint = "\(creds.account.serverURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/mcp"
        let root: JSONValue = .object([
            "mcpServers": .object([
                "goke": .object([
                    "type": .string("http"),
                    "url": .string(endpoint),
                    "headers": .object([
                        "Authorization": .string("Bearer \(creds.token)"),
                    ]),
                ]),
            ]),
        ])
        return try? MintJSON.encodeToString(root, pretty: true)
    }

    /// Authenticates against `serverURL`, persists credentials, enables purge tracking, and runs an initial sync.
    @discardableResult
    public func login(
        serverURL: String,
        username: String,
        password: String,
        deviceName: String
    ) async throws -> SyncOutcome {
        let client = try SyncClient(baseURL: serverURL, token: nil, transport: transport)
        let response = try await client.login(
            username: username.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            deviceName: String(deviceName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
        )
        let account = SyncAccount(
            serverURL: client.baseURL,
            username: response.user.username,
            role: response.user.role
        )
        await stateStore.saveCredentials(SyncCredentials(account: account, token: response.token))
        await stateStore.saveState(SyncCursorState())
        await repository.setTrackPurges(true)
        return try await syncNow()
    }

    /// Signs out (best-effort remote token revocation), clears credentials/sync cursors, and preserves local data.
    public func logout() async {
        if let creds = await stateStore.loadCredentials(),
           let client = try? SyncClient(baseURL: creds.account.serverURL, token: creds.token, transport: transport)
        {
            _ = try? await client.logout()
        }
        await stateStore.saveCredentials(nil)
        await stateStore.saveState(SyncCursorState())
        await repository.setTrackPurges(false)
    }

    public func changePassword(oldPassword: String, newPassword: String) async throws {
        guard let creds = await stateStore.loadCredentials() else {
            throw SyncError.notLoggedIn
        }
        let client = try SyncClient(baseURL: creds.account.serverURL, token: creds.token, transport: transport)
        try await client.changePassword(oldPassword: oldPassword, newPassword: newPassword)
    }

    /// Runs a full Pull-then-Push LWW sync cycle. Concurrent callers coalesce onto the in-flight sync.
    @discardableResult
    public func syncNow() async throws -> SyncOutcome {
        if let active = activeSyncTask {
            return try await active.value
        }
        let task = Task<SyncOutcome, Error> {
            try await self.performSync()
        }
        activeSyncTask = task
        defer { activeSyncTask = nil }
        return try await task.value
    }

    private func performSync() async throws -> SyncOutcome {
        guard let creds = await stateStore.loadCredentials() else {
            throw SyncError.notLoggedIn
        }
        var state = await stateStore.loadState()
        let client = try SyncClient(baseURL: creds.account.serverURL, token: creds.token, transport: transport)

        var appliedCount = 0
        var pulledCount = 0
        var pushedCount = 0
        var rejectedCount = 0

        do {
            // 0. First-sync pristine check: avoid creating duplicate default semesters when server already has data.
            if state.cursor == 0 {
                let me = try await client.me()
                let snap = await repository.snapshot
                if me.cursor > 0 && SyncPlanner.isPristine(snap) {
                    try await repository.discardAllLocal()
                }
            }

            // 1. Paginated pull (`since = state.cursor`, `limit = pullLimit`).
            // Keep local pendingPurged tombstones from being resurrected by stale server versions.
            let waitingTombstones = Dictionary(
                (try await repository.pendingPurged()).map { ($0.id, $0) },
                uniquingKeysWith: { a, b in a.updatedAt >= b.updatedAt ? a : b }
            )
            while true {
                let page = try await client.pull(since: state.cursor, limit: pullLimit)
                pulledCount += page.records.count
                let decoded = page.records.compactMap { $0.decodeRecord() }.filter { remote in
                    guard let tombstone = waitingTombstones[remote.id] else { return true }
                    return remote.updatedAt > tombstone.updatedAt
                }
                appliedCount += try await repository.applyRemote(decoded)
                state.cursor = page.cursor
                await stateStore.saveState(state)
                if !page.more {
                    break
                }
            }

            try await repository.ensureDefaultSemester()

            // 2. Chunked push (local records with `updatedAt > watermark` + live `pendingPurged` tombstones).
            let snap = await repository.snapshot
            let purged = try await repository.pendingPurged()
            let stalePurgedIds = purged.filter { snap.find(id: $0.id) != nil }.map(\.id)
            if !stalePurgedIds.isEmpty {
                try await repository.clearPurged(ids: stalePurgedIds)
            }
            let tombstones = purged.filter { snap.find(id: $0.id) == nil }
            let changed = SyncPlanner.pending(snapshot: snap, watermark: state.watermark)
            var newWatermark = state.watermark
            let outgoing = changed + tombstones

            var index = 0
            while index < outgoing.count {
                let end = min(index + pushBatchSize, outgoing.count)
                let batch = Array(outgoing[index..<end])
                let wireBatch = try batch.map { try WireRecord.encode($0) }
                let response = try await client.push(records: wireBatch)
                pushedCount += batch.count

                let rejected = response.rejected.compactMap { $0.decodeRecord() }
                rejectedCount += rejected.count
                appliedCount += try await repository.applyRemote(rejected)
                if let maxRejected = rejected.map(\.updatedAt).max() {
                    newWatermark = max(newWatermark, maxRejected)
                }
                index = end
            }

            if let maxChanged = changed.map(\.updatedAt).max() {
                newWatermark = max(newWatermark, maxChanged)
            }
            state.watermark = newWatermark
            if !tombstones.isEmpty {
                try await repository.clearPurged(ids: tombstones.map(\.id))
            }

            let now = clock()
            state.lastSyncAt = now
            state.lastError = nil
            state.needsRelogin = false
            await stateStore.saveState(state)

            return SyncOutcome(
                appliedCount: appliedCount,
                pulledCount: pulledCount,
                pushedCount: pushedCount,
                rejectedCount: rejectedCount,
                cursor: state.cursor,
                watermark: state.watermark,
                syncedAt: now
            )
        } catch let error as SyncError {
            if error.isUnauthorized {
                state.needsRelogin = true
                state.lastError = error.errorDescription
            } else {
                state.lastError = error.errorDescription
            }
            await stateStore.saveState(state)
            throw error
        } catch {
            state.lastError = error.localizedDescription
            await stateStore.saveState(state)
            throw SyncError.networkError(error.localizedDescription)
        }
    }
}
