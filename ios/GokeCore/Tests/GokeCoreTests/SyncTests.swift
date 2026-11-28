import Foundation
import Testing
@testable import GokeCore

private actor MockHTTPTransport: HTTPTransport {
    typealias Handler = @Sendable (HTTPRequest) async throws -> HTTPResponse

    private var recorded: [HTTPRequest] = []
    private let handler: Handler

    init(handler: @escaping Handler) {
        self.handler = handler
    }

    func send(_ request: HTTPRequest) async throws -> HTTPResponse {
        recorded.append(request)
        return try await handler(request)
    }

    func requests() -> [HTTPRequest] {
        recorded
    }
}

private final class StepClock: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Int64

    init(start: Int64) {
        self.value = start
    }

    func next() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        value += 1000
        return value
    }

    func current() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
}

private func makeJSONResponse(_ value: some Encodable, statusCode: Int = 200, url: URL) throws -> HTTPResponse {
    let data = try MintJSON.makeEncoder().encode(value)
    return HTTPResponse(
        statusCode: statusCode,
        url: url,
        headers: [HTTPHeader(name: "Content-Type", value: "application/json")],
        body: data
    )
}

@Suite("GokeCore Sync Client & Pull-then-Push LWW Engine")
struct SyncTests {

    private func tempDBPath() -> String {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("goke-sync-tests-\(UUID().uuidString)", isDirectory: true)
        return dir.appendingPathComponent("goke.db").path
    }

    @Test("SyncClient normalizes server URLs and covers all auth/sync REST endpoints")
    func syncClientEndpointsAndNormalization() async throws {
        #expect(SyncClient.normalizeURL("goke.example.com/") == "https://goke.example.com")
        #expect(SyncClient.normalizeURL("http://127.0.0.1:8080/admin/") == "http://127.0.0.1:8080")
        #expect(SyncClient.normalizeURL("   ") == nil)
        #expect(SyncClient.isInsecure("http://localhost:8080"))
        #expect(!SyncClient.isInsecure("https://goke.example.com"))

        let transport = MockHTTPTransport { req in
            switch (req.method, req.url.path) {
            case ("GET", "/healthz"):
                return try makeJSONResponse(HealthResponse(status: "ok"), url: req.url)
            case ("POST", "/api/v1/login"):
                let body = try MintJSON.makeDecoder().decode(LoginRequest.self, from: req.body ?? Data())
                #expect(body.username == "alice")
                #expect(body.deviceName == "iPhone 16")
                return try makeJSONResponse(
                    LoginResponse(token: "tok-123", user: SyncUser(id: 7, username: "alice", role: "user")),
                    url: req.url
                )
            case ("GET", "/api/v1/me"):
                #expect(req.firstHeader("Authorization") == "Bearer tok-123")
                return try makeJSONResponse(
                    MeResponse(id: 7, username: "alice", role: "user", cursor: 42),
                    url: req.url
                )
            case ("POST", "/api/v1/me/password"):
                #expect(req.firstHeader("Authorization") == "Bearer tok-123")
                let body = try MintJSON.makeDecoder().decode(ChangePasswordRequest.self, from: req.body ?? Data())
                #expect(body.oldPassword == "oldpass")
                #expect(body.newPassword == "newpass123")
                return HTTPResponse(statusCode: 204, url: req.url)
            case ("POST", "/api/v1/logout"):
                #expect(req.firstHeader("Authorization") == "Bearer tok-123")
                return HTTPResponse(statusCode: 204, url: req.url)
            default:
                return HTTPResponse(statusCode: 404, url: req.url)
            }
        }

        let anon = try SyncClient(baseURL: "goke.example.com/admin", transport: transport)
        #expect(try await anon.healthz().status == "ok")
        let loginRes = try await anon.login(username: "alice", password: "pw", deviceName: "iPhone 16")
        #expect(loginRes.token == "tok-123")

        let authed = try SyncClient(baseURL: "https://goke.example.com", token: loginRes.token, transport: transport)
        let me = try await authed.me()
        #expect(me.cursor == 42)
        try await authed.changePassword(oldPassword: "oldpass", newPassword: "newpass123")
        try await authed.logout()
    }

    @Test("First sync discards local default semester when pristine and server has data")
    func firstSyncPristineCheck() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let clock = StepClock(start: 1_700_000_000_000)
        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store, clock: { clock.next() })
        let initial = try await repo.load()
        #expect(initial.semesters.count == 1)
        let localDefaultId = initial.semesters[0].id
        #expect(SyncPlanner.isPristine(initial))

        let remoteSem = Semester(
            id: "sem-remote-1",
            name: "2026-2027 秋季",
            week1Monday: LocalDate(year: 2026, month: 9, day: 1),
            updatedAt: 1_700_000_005_000
        )
        let remoteSemWire = try WireRecord.encode(.semester(remoteSem), seq: 1)

        // Include an unknown future `_t` record to verify safe skipping.
        let unknownWire = WireRecord(
            id: "future-1",
            kind: "FutureEntity",
            data: .object(["_t": .string("futureEntity"), "id": .string("future-1")]),
            updatedAt: 1_700_000_006_000,
            seq: 2
        )

        let transport = MockHTTPTransport { req in
            switch (req.method, req.url.path) {
            case ("POST", "/api/v1/login"):
                return try makeJSONResponse(
                    LoginResponse(token: "tok-a", user: SyncUser(id: 1, username: "u1", role: "user")),
                    url: req.url
                )
            case ("GET", "/api/v1/me"):
                return try makeJSONResponse(MeResponse(id: 1, username: "u1", role: "user", cursor: 2), url: req.url)
            case ("GET", "/api/v1/sync/pull"):
                return try makeJSONResponse(
                    PullResponse(records: [remoteSemWire, unknownWire], cursor: 2, more: false),
                    url: req.url
                )
            case ("POST", "/api/v1/sync/push"):
                return try makeJSONResponse(PushResponse(accepted: [], rejected: [], cursor: 2), url: req.url)
            default:
                return HTTPResponse(statusCode: 404, url: req.url)
            }
        }

        let stateStore = InMemorySyncStateStore()
        let engine = SyncEngine(repository: repo, stateStore: stateStore, transport: transport, clock: { clock.next() })
        let outcome = try await engine.login(serverURL: "https://sync.local", username: "u1", password: "pw", deviceName: "iPhone")

        #expect(outcome.appliedCount == 1)
        #expect(outcome.cursor == 2)
        let snap = await repo.snapshot
        // Local auto-created default semester was discarded; only remote semester remains.
        #expect(snap.semesters.count == 1)
        #expect(snap.semesters[0].id == "sem-remote-1")
        #expect(snap.semesters[0].id != localDefaultId)

        let mcpConfig = await engine.cloudMCPConfigJSON()
        #expect(mcpConfig?.contains("https://sync.local/mcp") == true)
        #expect(mcpConfig?.contains("Bearer tok-a") == true)
    }

    @Test("Paginated pull, chunked push (>500), rejected overwrite, and watermark advance")
    func paginatedPullChunkedPushAndRejectedConvergence() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let clock = StepClock(start: 1_700_000_000_000)
        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store, clock: { clock.next() })
        _ = try await repo.load()

        // Commit 505 local tasks so push must chunk into 2 batches (500 + 5, plus default semester = 506).
        var adds: [Change] = []
        for i in 1...505 {
            adds.append(.add(after: .task(GokeTask(id: "local-t-\(i)", title: "Task \(i)"))))
        }
        try await repo.commit(ChangeSet(id: "op-bulk", description: "Bulk", changes: adds))

        let localSnap = await repo.snapshot
        let t1Local = try #require(localSnap.tasks.first(where: { $0.id == "local-t-1" }))

        // Server rejects local-t-1 with a strictly newer remote version.
        var serverWinningT1 = t1Local
        serverWinningT1.title = "Server Winner Title"
        serverWinningT1.updatedAt = t1Local.updatedAt + 99_000
        let rejectedWire = try WireRecord.encode(.task(serverWinningT1), seq: 10)

        let page1Task = GokeTask(id: "remote-p1", title: "Page 1 Task", updatedAt: 1_700_000_000_100)
        let page2Task = GokeTask(id: "remote-p2", title: "Page 2 Task", updatedAt: 1_700_000_000_200)
        let page1Wire = try WireRecord.encode(.task(page1Task), seq: 1)
        let page2Wire = try WireRecord.encode(.task(page2Task), seq: 2)

        actor BatchTracker {
            var pushSizes: [Int] = []
            func record(_ size: Int) { pushSizes.append(size) }
            func all() -> [Int] { pushSizes }
        }
        let tracker = BatchTracker()

        let transport = MockHTTPTransport { req in
            switch (req.method, req.url.path) {
            case ("GET", "/api/v1/me"):
                return try makeJSONResponse(MeResponse(id: 1, username: "u1", role: "user", cursor: 2), url: req.url)
            case ("GET", "/api/v1/sync/pull"):
                let query = req.url.query ?? ""
                #expect(query.contains("limit=500"))
                if query.contains("since=0") {
                    return try makeJSONResponse(PullResponse(records: [page1Wire], cursor: 1, more: true), url: req.url)
                } else {
                    #expect(query.contains("since=1"))
                    return try makeJSONResponse(PullResponse(records: [page2Wire], cursor: 2, more: false), url: req.url)
                }
            case ("POST", "/api/v1/sync/push"):
                let pushReq = try MintJSON.makeDecoder().decode(PushRequest.self, from: req.body ?? Data())
                await tracker.record(pushReq.records.count)
                let containsT1 = pushReq.records.contains { $0.id == "local-t-1" }
                return try makeJSONResponse(
                    PushResponse(
                        accepted: pushReq.records.filter { $0.id != "local-t-1" }.map(\.id),
                        rejected: containsT1 ? [rejectedWire] : [],
                        cursor: 10
                    ),
                    url: req.url
                )
            default:
                return HTTPResponse(statusCode: 404, url: req.url)
            }
        }

        let stateStore = InMemorySyncStateStore(
            credentials: SyncCredentials(
                account: SyncAccount(serverURL: "https://sync.local", username: "u1"),
                token: "tok-1"
            )
        )
        let engine = SyncEngine(repository: repo, stateStore: stateStore, transport: transport, clock: { clock.next() })
        let outcome = try await engine.syncNow()

        let pushSizes = await tracker.all()
        // 505 tasks + 1 default semester = 506 records (pulled records have older updatedAt than watermark=0? No, watermark=0 so all 508 records!)
        // Wait: pulled records also have updatedAt > 0, so outgoing = 508 -> [500, 8].
        #expect(pushSizes.count == 2)
        #expect(pushSizes[0] == 500)
        #expect(pushSizes[1] > 0)
        #expect(outcome.pulledCount == 2)
        #expect(outcome.rejectedCount == 1)
        #expect(outcome.cursor == 2)
        #expect(outcome.watermark == serverWinningT1.updatedAt)

        // Rejected record overwrote local task.
        let finalSnap = await repo.snapshot
        #expect(finalSnap.tasks.first(where: { $0.id == "local-t-1" })?.title == "Server Winner Title")
        #expect(finalSnap.tasks.contains(where: { $0.id == "remote-p1" }))
        #expect(finalSnap.tasks.contains(where: { $0.id == "remote-p2" }))
    }

    @Test("pendingPurged tombstone blocks stale pull resurrection and pushes deletion to server")
    func pendingPurgedTombstoneConvergence() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let clock = StepClock(start: 1_700_000_000_000)
        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store, clock: { clock.next() })
        await repo.setTrackPurges(true)
        _ = try await repo.load()

        // Add then undo -> creates a pendingPurged tombstone for "t-purged".
        let task = GokeTask(id: "t-purged", title: "Temp Task")
        try await repo.commit(ChangeSet(id: "op-1", description: "Add", changes: [.add(after: .task(task))]))
        let addUpdatedAt = try #require((await repo.snapshot).tasks.first?.updatedAt)
        let undone = try await repo.undo(opId: "op-1")
        #expect(undone)
        let tombstones = try await repo.pendingPurged()
        #expect(tombstones.count == 1)
        #expect(tombstones[0].deletedAt != nil)

        // Server returns a stale live version of "t-purged" (older than tombstone).
        let staleRemote = GokeTask(id: "t-purged", title: "Stale Server Copy", updatedAt: addUpdatedAt)
        let staleWire = try WireRecord.encode(.task(staleRemote), seq: 5)

        actor PushCapture {
            var pushed: [WireRecord] = []
            func append(_ items: [WireRecord]) { pushed.append(contentsOf: items) }
            func all() -> [WireRecord] { pushed }
        }
        let capture = PushCapture()

        let transport = MockHTTPTransport { req in
            switch (req.method, req.url.path) {
            case ("GET", "/api/v1/sync/pull"):
                return try makeJSONResponse(PullResponse(records: [staleWire], cursor: 5, more: false), url: req.url)
            case ("POST", "/api/v1/sync/push"):
                let body = try MintJSON.makeDecoder().decode(PushRequest.self, from: req.body ?? Data())
                await capture.append(body.records)
                return try makeJSONResponse(
                    PushResponse(accepted: body.records.map(\.id), rejected: [], cursor: 6),
                    url: req.url
                )
            default:
                return HTTPResponse(statusCode: 404, url: req.url)
            }
        }

        let stateStore = InMemorySyncStateStore(
            credentials: SyncCredentials(account: SyncAccount(serverURL: "https://sync.local", username: "u1"), token: "tok"),
            state: SyncCursorState(cursor: 4, watermark: addUpdatedAt)
        )
        let engine = SyncEngine(repository: repo, stateStore: stateStore, transport: transport, clock: { clock.next() })
        _ = try await engine.syncNow()

        // Stale remote version did NOT resurrect "t-purged".
        let snap = await repo.snapshot
        #expect(snap.find(id: "t-purged") == nil)

        // Tombstone was pushed to server and cleared from local pendingPurged queue.
        let pushedRecords = await capture.all()
        let pushedTombstone = try #require(pushedRecords.first(where: { $0.id == "t-purged" }))
        #expect(pushedTombstone.deletedAt != nil)
        #expect((try await repo.pendingPurged()).isEmpty)
    }

    @Test("HTTP 401 maps to SyncError.unauthorized and marks needsRelogin")
    func unauthorizedSetsNeedsRelogin() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store)
        _ = try await repo.load()

        let transport = MockHTTPTransport { req in
            try makeJSONResponse(
                SyncErrorBody(error: "unauthorized", message: "token revoked"),
                statusCode: 401,
                url: req.url
            )
        }

        let stateStore = InMemorySyncStateStore(
            credentials: SyncCredentials(account: SyncAccount(serverURL: "https://sync.local", username: "u1"), token: "expired"),
            state: SyncCursorState(cursor: 10, watermark: 100)
        )
        let engine = SyncEngine(repository: repo, stateStore: stateStore, transport: transport)

        var caught: SyncError?
        do {
            _ = try await engine.syncNow()
        } catch let err as SyncError {
            caught = err
        }
        #expect(caught == .unauthorized(message: "token revoked"))

        let state = await engine.currentState()
        #expect(state.needsRelogin)
        #expect(state.lastError == "token revoked")
    }

    @Test("Newer remote edit overrides local pendingPurged tombstone and logout preserves local data")
    func newerRemoteBeatsTombstoneAndLogoutPreservesLocalData() async throws {
        let path = tempDBPath()
        defer { try? FileManager.default.removeItem(at: URL(fileURLWithPath: path).deletingLastPathComponent()) }

        let clock = StepClock(start: 1_700_000_000_000)
        let store = try SQLiteStore(path: path)
        let repo = RepositoryActor(store: store, clock: { clock.next() })
        await repo.setTrackPurges(true)
        _ = try await repo.load()

        let task = GokeTask(id: "t-race", title: "Original")
        try await repo.commit(ChangeSet(id: "op-add", description: "Add", changes: [.add(after: .task(task))]))
        #expect(try await repo.undo(opId: "op-add"))
        let tombstoneAt = try #require((try await repo.pendingPurged()).first?.updatedAt)

        // Another device modified "t-race" AFTER our local purge tombstone.
        let newerRemote = GokeTask(id: "t-race", title: "Edited on Web", updatedAt: tombstoneAt + 50_000)
        let newerWire = try WireRecord.encode(.task(newerRemote), seq: 11)

        actor LogoutTracker {
            var called = false
            func mark() { called = true }
            func wasCalled() -> Bool { called }
        }
        let logoutTracker = LogoutTracker()

        let transport = MockHTTPTransport { req in
            switch (req.method, req.url.path) {
            case ("GET", "/api/v1/sync/pull"):
                return try makeJSONResponse(PullResponse(records: [newerWire], cursor: 11, more: false), url: req.url)
            case ("POST", "/api/v1/sync/push"):
                let body = try MintJSON.makeDecoder().decode(PushRequest.self, from: req.body ?? Data())
                // Stale tombstone must NOT be pushed once newer remote revived the record.
                #expect(!body.records.contains(where: { $0.id == "t-race" && $0.deletedAt != nil }))
                return try makeJSONResponse(PushResponse(accepted: body.records.map(\.id), rejected: [], cursor: 11), url: req.url)
            case ("POST", "/api/v1/logout"):
                await logoutTracker.mark()
                return HTTPResponse(statusCode: 204, url: req.url)
            default:
                return HTTPResponse(statusCode: 404, url: req.url)
            }
        }

        let stateStore = InMemorySyncStateStore(
            credentials: SyncCredentials(account: SyncAccount(serverURL: "https://sync.local", username: "u1"), token: "tok"),
            state: SyncCursorState(cursor: 10, watermark: tombstoneAt)
        )
        let engine = SyncEngine(repository: repo, stateStore: stateStore, transport: transport, clock: { clock.next() })
        _ = try await engine.syncNow()

        let snap = await repo.snapshot
        #expect(snap.tasks.first(where: { $0.id == "t-race" })?.title == "Edited on Web")
        #expect((try await repo.pendingPurged()).isEmpty)

        await engine.logout()
        #expect(await logoutTracker.wasCalled())
        #expect(await engine.currentCredentials() == nil)
        #expect(await repo.trackPurges == false)
        // Local data preserved after logout.
        #expect((await repo.snapshot).tasks.contains(where: { $0.id == "t-race" }))
    }
}
