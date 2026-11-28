import Foundation
import GokeCoreModel
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

// MARK: - HTTP Transport Abstraction (Linux + iOS/macOS)

/// Single HTTP header pair preserving duplicate header names (such as multiple `Set-Cookie` headers).
public struct HTTPHeader: Sendable, Hashable, Codable {
    public var name: String
    public var value: String

    public init(name: String, value: String) {
        self.name = name
        self.value = value
    }
}

/// Platform-independent HTTP request passed to `HTTPTransport`.
public struct HTTPRequest: Sendable, Hashable {
    public var method: String
    public var url: URL
    public var headers: [HTTPHeader]
    public var body: Data?
    public var followRedirects: Bool
    public var timeoutInterval: TimeInterval

    public init(
        method: String = "GET",
        url: URL,
        headers: [HTTPHeader] = [],
        body: Data? = nil,
        followRedirects: Bool = true,
        timeoutInterval: TimeInterval = 30
    ) {
        self.method = method
        self.url = url
        self.headers = headers
        self.body = body
        self.followRedirects = followRedirects
        self.timeoutInterval = timeoutInterval
    }

    public init(
        method: String = "GET",
        url: URL,
        headerMap: [String: String],
        body: Data? = nil,
        followRedirects: Bool = true,
        timeoutInterval: TimeInterval = 30
    ) {
        self.method = method
        self.url = url
        self.headers = headerMap.sorted(by: { $0.key < $1.key }).map { HTTPHeader(name: $0.key, value: $0.value) }
        self.body = body
        self.followRedirects = followRedirects
        self.timeoutInterval = timeoutInterval
    }

    public func firstHeader(_ name: String) -> String? {
        let lower = name.lowercased()
        return headers.first(where: { $0.name.lowercased() == lower })?.value
    }
}

/// Platform-independent HTTP response returned by `HTTPTransport`.
public struct HTTPResponse: Sendable, Hashable {
    public var statusCode: Int
    public var url: URL
    public var headers: [HTTPHeader]
    public var body: Data

    public init(
        statusCode: Int,
        url: URL,
        headers: [HTTPHeader] = [],
        body: Data = Data()
    ) {
        self.statusCode = statusCode
        self.url = url
        self.headers = headers
        self.body = body
    }

    public func firstHeader(_ name: String) -> String? {
        let lower = name.lowercased()
        return headers.first(where: { $0.name.lowercased() == lower })?.value
    }

    public func allHeaders(_ name: String) -> [String] {
        let lower = name.lowercased()
        return headers.filter { $0.name.lowercased() == lower }.map(\.value)
    }

    public var bodyString: String {
        String(decoding: body, as: UTF8.self)
    }
}

/// Injectable network transport so production uses `URLSession` while tests run hermetically on Linux and Apple.
public protocol HTTPTransport: Sendable {
    func send(_ request: HTTPRequest) async throws -> HTTPResponse
}

private final class TransportSessionDelegate: NSObject, URLSessionTaskDelegate, Sendable {
    private let allowRedirects: Bool

    init(allowRedirects: Bool) {
        self.allowRedirects = allowRedirects
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(allowRedirects ? request : nil)
    }

    #if canImport(Security)
    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        CampusTls.handleAuthenticationChallenge(challenge, completionHandler: completionHandler)
    }
    #endif
}

/// Production `URLSession`-backed `HTTPTransport` compatible with both Apple platforms and Linux `FoundationNetworking`.
public final class URLSessionHTTPTransport: HTTPTransport, @unchecked Sendable {
    private let redirectSession: URLSession
    private let noRedirectSession: URLSession
    private let redirectDelegate: TransportSessionDelegate
    private let noRedirectDelegate: TransportSessionDelegate

    public init(configuration: URLSessionConfiguration = .ephemeral) {
        let redirectConfig = (configuration.copy() as? URLSessionConfiguration) ?? .ephemeral
        redirectConfig.httpShouldSetCookies = false
        redirectConfig.httpCookieAcceptPolicy = .never
        let redirectDel = TransportSessionDelegate(allowRedirects: true)
        self.redirectDelegate = redirectDel
        self.redirectSession = URLSession(configuration: redirectConfig, delegate: redirectDel, delegateQueue: nil)

        let noRedirectConfig = (configuration.copy() as? URLSessionConfiguration) ?? .ephemeral
        noRedirectConfig.httpShouldSetCookies = false
        noRedirectConfig.httpCookieAcceptPolicy = .never
        let noRedirectDel = TransportSessionDelegate(allowRedirects: false)
        self.noRedirectDelegate = noRedirectDel
        self.noRedirectSession = URLSession(configuration: noRedirectConfig, delegate: noRedirectDel, delegateQueue: nil)
    }

    public func send(_ request: HTTPRequest) async throws -> HTTPResponse {
        var urlRequest = URLRequest(url: request.url)
        urlRequest.httpMethod = request.method
        urlRequest.timeoutInterval = request.timeoutInterval
        urlRequest.httpBody = request.body
        for header in request.headers {
            urlRequest.addValue(header.value, forHTTPHeaderField: header.name)
        }

        let session = request.followRedirects ? redirectSession : noRedirectSession
        let fallbackURL = request.url

        return try await withCheckedThrowingContinuation { continuation in
            let task = session.dataTask(with: urlRequest) { data, response, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let http = response as? HTTPURLResponse else {
                    continuation.resume(
                        throwing: URLError(.badServerResponse)
                    )
                    return
                }
                var headers: [HTTPHeader] = []
                for (rawKey, rawVal) in http.allHeaderFields {
                    let key = String(describing: rawKey)
                    let value = String(describing: rawVal)
                    headers.append(HTTPHeader(name: key, value: value))
                }
                let finalURL = http.url ?? fallbackURL
                continuation.resume(
                    returning: HTTPResponse(
                        statusCode: http.statusCode,
                        url: finalURL,
                        headers: headers,
                        body: data ?? Data()
                    )
                )
            }
            task.resume()
        }
    }
}

// MARK: - Sync Errors

/// Errors raised by `SyncClient` and `SyncEngine`.
public enum SyncError: Error, Sendable, Equatable, LocalizedError {
    case invalidServerURL(String)
    case unauthorized(message: String)
    case httpError(statusCode: Int, code: String, message: String)
    case emptyResponse
    case networkError(String)
    case decodingError(String)
    case notLoggedIn

    public var isUnauthorized: Bool {
        if case .unauthorized = self { return true }
        return false
    }

    public var statusCode: Int {
        switch self {
        case .unauthorized: return 401
        case .httpError(let code, _, _): return code
        default: return 0
        }
    }

    public var errorDescription: String? {
        switch self {
        case .invalidServerURL(let url):
            return "Invalid server URL: \(url)"
        case .unauthorized(let message):
            return message.isEmpty ? "Unauthorized (401)" : message
        case .httpError(let statusCode, _, let message):
            return message.isEmpty ? "Server returned HTTP \(statusCode)" : message
        case .emptyResponse:
            return "Server returned an empty response"
        case .networkError(let detail):
            return detail
        case .decodingError(let detail):
            return detail
        case .notLoggedIn:
            return "Not signed in to sync server"
        }
    }
}

// MARK: - Sync API DTOs (`server/openapi.yaml`)

public struct HealthResponse: Sendable, Hashable, Codable {
    public var status: String

    public init(status: String = "ok") {
        self.status = status
    }
}

public struct SyncUser: Sendable, Hashable, Codable {
    public var id: Int64
    public var username: String
    public var role: String

    public init(id: Int64 = 0, username: String = "", role: String = "user") {
        self.id = id
        self.username = username
        self.role = role
    }

    private enum CodingKeys: String, CodingKey {
        case id, username, role
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.username = try c.decodeIfPresent(String.self, forKey: .username) ?? ""
        self.role = try c.decodeIfPresent(String.self, forKey: .role) ?? "user"
    }
}

public struct SyncAccount: Sendable, Hashable, Codable {
    public var serverURL: String
    public var username: String
    public var role: String

    public init(serverURL: String, username: String, role: String = "user") {
        self.serverURL = serverURL
        self.username = username
        self.role = role
    }
}

public struct SyncCredentials: Sendable, Hashable, Codable {
    public var account: SyncAccount
    public var token: String

    public init(account: SyncAccount, token: String) {
        self.account = account
        self.token = token
    }
}

public struct LoginRequest: Sendable, Hashable, Codable {
    public var username: String
    public var password: String
    public var deviceName: String

    public init(username: String, password: String, deviceName: String) {
        self.username = username
        self.password = password
        self.deviceName = deviceName
    }
}

public struct LoginResponse: Sendable, Hashable, Codable {
    public var token: String
    public var user: SyncUser

    public init(token: String, user: SyncUser) {
        self.token = token
        self.user = user
    }
}

public struct MeResponse: Sendable, Hashable, Codable {
    public var id: Int64
    public var username: String
    public var role: String
    public var cursor: Int64

    public init(id: Int64 = 0, username: String = "", role: String = "user", cursor: Int64 = 0) {
        self.id = id
        self.username = username
        self.role = role
        self.cursor = cursor
    }

    private enum CodingKeys: String, CodingKey {
        case id, username, role, cursor
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.username = try c.decodeIfPresent(String.self, forKey: .username) ?? ""
        self.role = try c.decodeIfPresent(String.self, forKey: .role) ?? "user"
        self.cursor = try c.decodeIfPresent(Int64.self, forKey: .cursor) ?? 0
    }
}

public struct ChangePasswordRequest: Sendable, Hashable, Codable {
    public var oldPassword: String
    public var newPassword: String

    public init(oldPassword: String, newPassword: String) {
        self.oldPassword = oldPassword
        self.newPassword = newPassword
    }
}

public struct PullResponse: Sendable, Hashable, Codable {
    public var records: [WireRecord]
    public var cursor: Int64
    public var more: Bool

    public init(records: [WireRecord] = [], cursor: Int64 = 0, more: Bool = false) {
        self.records = records
        self.cursor = cursor
        self.more = more
    }

    private enum CodingKeys: String, CodingKey {
        case records, cursor, more
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.records = try c.decodeIfPresent([WireRecord].self, forKey: .records) ?? []
        self.cursor = try c.decodeIfPresent(Int64.self, forKey: .cursor) ?? 0
        self.more = try c.decodeIfPresent(Bool.self, forKey: .more) ?? false
    }
}

public struct PushRequest: Sendable, Hashable, Codable {
    public var records: [WireRecord]

    public init(records: [WireRecord]) {
        self.records = records
    }
}

public struct PushResponse: Sendable, Hashable, Codable {
    public var accepted: [String]
    public var rejected: [WireRecord]
    public var cursor: Int64

    public init(accepted: [String] = [], rejected: [WireRecord] = [], cursor: Int64 = 0) {
        self.accepted = accepted
        self.rejected = rejected
        self.cursor = cursor
    }

    private enum CodingKeys: String, CodingKey {
        case accepted, rejected, cursor
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.accepted = try c.decodeIfPresent([String].self, forKey: .accepted) ?? []
        self.rejected = try c.decodeIfPresent([WireRecord].self, forKey: .rejected) ?? []
        self.cursor = try c.decodeIfPresent(Int64.self, forKey: .cursor) ?? 0
    }
}

public struct SyncErrorBody: Sendable, Hashable, Codable {
    public var error: String
    public var message: String

    public init(error: String = "", message: String = "") {
        self.error = error
        self.message = message
    }

    private enum CodingKeys: String, CodingKey {
        case error, message
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.error = try c.decodeIfPresent(String.self, forKey: .error) ?? ""
        self.message = try c.decodeIfPresent(String.self, forKey: .message) ?? ""
    }
}

// MARK: - Sync State & Persistence

public struct SyncCursorState: Sendable, Hashable, Codable {
    public var cursor: Int64
    public var watermark: Int64
    public var lastSyncAt: Int64?
    public var lastError: String?
    public var needsRelogin: Bool

    public init(
        cursor: Int64 = 0,
        watermark: Int64 = 0,
        lastSyncAt: Int64? = nil,
        lastError: String? = nil,
        needsRelogin: Bool = false
    ) {
        self.cursor = cursor
        self.watermark = watermark
        self.lastSyncAt = lastSyncAt
        self.lastError = lastError
        self.needsRelogin = needsRelogin
    }
}

public struct SyncOutcome: Sendable, Hashable {
    public var appliedCount: Int
    public var pulledCount: Int
    public var pushedCount: Int
    public var rejectedCount: Int
    public var cursor: Int64
    public var watermark: Int64
    public var syncedAt: Int64

    public init(
        appliedCount: Int = 0,
        pulledCount: Int = 0,
        pushedCount: Int = 0,
        rejectedCount: Int = 0,
        cursor: Int64 = 0,
        watermark: Int64 = 0,
        syncedAt: Int64 = 0
    ) {
        self.appliedCount = appliedCount
        self.pulledCount = pulledCount
        self.pushedCount = pushedCount
        self.rejectedCount = rejectedCount
        self.cursor = cursor
        self.watermark = watermark
        self.syncedAt = syncedAt
    }
}

/// Persistence seam for sync credentials and cursors (backed by Keychain/UserDefaults in iOS app, in-memory in tests).
public protocol SyncStateStore: Sendable {
    func loadCredentials() async -> SyncCredentials?
    func saveCredentials(_ credentials: SyncCredentials?) async
    func loadState() async -> SyncCursorState
    func saveState(_ state: SyncCursorState) async
}

/// Actor-isolated in-memory `SyncStateStore` suitable for tests and ephemeral sessions.
public actor InMemorySyncStateStore: SyncStateStore {
    private var credentials: SyncCredentials?
    private var state: SyncCursorState

    public init(credentials: SyncCredentials? = nil, state: SyncCursorState = SyncCursorState()) {
        self.credentials = credentials
        self.state = state
    }

    public func loadCredentials() async -> SyncCredentials? {
        credentials
    }

    public func saveCredentials(_ credentials: SyncCredentials?) async {
        self.credentials = credentials
    }

    public func loadState() async -> SyncCursorState {
        state
    }

    public func saveState(_ state: SyncCursorState) async {
        self.state = state
    }
}

// MARK: - Pure Sync Planner (matches Kotlin `SyncPlanner`)

public enum SyncPlanner {
    /// Local records modified after `watermark` (including tombstones), ordered ascending by `updatedAt`.
    public static func pending(snapshot: AppSnapshot, watermark: Int64) -> [Record] {
        snapshot
            .allRecords()
            .filter { $0.updatedAt > watermark }
            .sorted { $0.updatedAt < $1.updatedAt }
    }

    /// Remote records that should be written locally: absent locally or strictly newer `updatedAt`.
    public static func incoming(snapshot: AppSnapshot, remote: [Record]) -> [Record] {
        let localById = Dictionary(
            snapshot.allRecords().map { ($0.id, $0) },
            uniquingKeysWith: { _, latest in latest }
        )
        return remote.filter { remoteRec in
            guard let local = localById[remoteRec.id] else { return true }
            return remoteRec.updatedAt > local.updatedAt
        }
    }

    /// True iff the local DB has no user data yet (no live courses, exceptions, events, tasks, lists, and at most 1 semester).
    public static func isPristine(_ snapshot: AppSnapshot) -> Bool {
        let live = snapshot.live()
        return live.courses.isEmpty
            && live.exceptions.isEmpty
            && live.events.isEmpty
            && live.tasks.isEmpty
            && live.lists.isEmpty
            && live.semesters.count <= 1
    }
}
