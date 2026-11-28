import Foundation
import GokeCoreModel
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// HTTP client for the self-hosted Goke sync server (`server/openapi.yaml`).
public struct SyncClient: Sendable {
    public let baseURL: String
    public let token: String?
    private let transport: any HTTPTransport
    private let timeoutInterval: TimeInterval

    public init(
        baseURL: String,
        token: String? = nil,
        transport: any HTTPTransport = URLSessionHTTPTransport(),
        timeoutInterval: TimeInterval = 60
    ) throws {
        guard let normalized = Self.normalizeURL(baseURL) else {
            throw SyncError.invalidServerURL(baseURL)
        }
        self.baseURL = normalized
        self.token = token
        self.transport = transport
        self.timeoutInterval = timeoutInterval
    }

    /// Normalizes a user-entered server URL: defaults to `https://`, trims trailing slashes and mistaken `/admin`.
    public static func normalizeURL(_ input: String) -> String? {
        var s = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return nil }
        if !s.contains("://") {
            s = "https://\(s)"
        }
        while s.hasSuffix("/") {
            s.removeLast()
        }
        if s.hasSuffix("/admin") {
            s = String(s.dropLast("/admin".count))
            while s.hasSuffix("/") {
                s.removeLast()
            }
        }
        guard let url = URL(string: s),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              let host = url.host,
              !host.isEmpty
        else {
            return nil
        }
        return s
    }

    public static func isInsecure(_ url: String) -> Bool {
        url.lowercased().hasPrefix("http://")
    }

    // MARK: - Endpoints

    public func healthz() async throws -> HealthResponse {
        let data = try await send(method: "GET", fullPath: "/healthz", body: nil, requiresAuth: false)
        return try decode(HealthResponse.self, from: data)
    }

    public func login(username: String, password: String, deviceName: String) async throws -> LoginResponse {
        let payload = try encode(LoginRequest(username: username, password: password, deviceName: deviceName))
        let data = try await send(method: "POST", fullPath: "/api/v1/login", body: payload, requiresAuth: false)
        return try decode(LoginResponse.self, from: data)
    }

    public func logout() async throws {
        _ = try await send(method: "POST", fullPath: "/api/v1/logout", body: nil, requiresAuth: true)
    }

    public func me() async throws -> MeResponse {
        let data = try await send(method: "GET", fullPath: "/api/v1/me", body: nil, requiresAuth: true)
        return try decode(MeResponse.self, from: data)
    }

    public func changePassword(oldPassword: String, newPassword: String) async throws {
        let payload = try encode(ChangePasswordRequest(oldPassword: oldPassword, newPassword: newPassword))
        _ = try await send(method: "POST", fullPath: "/api/v1/me/password", body: payload, requiresAuth: true)
    }

    public func pull(since: Int64, limit: Int = 500) async throws -> PullResponse {
        let clampedLimit = max(1, min(limit, 1000))
        let data = try await send(
            method: "GET",
            fullPath: "/api/v1/sync/pull?since=\(since)&limit=\(clampedLimit)",
            body: nil,
            requiresAuth: true
        )
        return try decode(PullResponse.self, from: data)
    }

    public func push(records: [WireRecord]) async throws -> PushResponse {
        let payload = try encode(PushRequest(records: records))
        let data = try await send(method: "POST", fullPath: "/api/v1/sync/push", body: payload, requiresAuth: true)
        return try decode(PushResponse.self, from: data)
    }

    // MARK: - Transport & Coding

    private func send(
        method: String,
        fullPath: String,
        body: Data?,
        requiresAuth: Bool
    ) async throws -> Data {
        guard let url = URL(string: baseURL + fullPath) else {
            throw SyncError.invalidServerURL(baseURL + fullPath)
        }
        var headers: [HTTPHeader] = [
            HTTPHeader(name: "Accept", value: "application/json"),
        ]
        if requiresAuth, let token, !token.isEmpty {
            headers.append(HTTPHeader(name: "Authorization", value: "Bearer \(token)"))
        }
        if body != nil {
            headers.append(HTTPHeader(name: "Content-Type", value: "application/json; charset=utf-8"))
        }

        let request = HTTPRequest(
            method: method,
            url: url,
            headers: headers,
            body: body,
            followRedirects: true,
            timeoutInterval: timeoutInterval
        )

        let response: HTTPResponse
        do {
            response = try await transport.send(request)
        } catch let err as SyncError {
            throw err
        } catch {
            throw SyncError.networkError(error.localizedDescription)
        }

        if !(200...299).contains(response.statusCode) {
            let parsedError = try? MintJSON.makeDecoder().decode(SyncErrorBody.self, from: response.body)
            let code = parsedError?.error ?? ""
            let message = parsedError?.message ?? ""
            if response.statusCode == 401 {
                throw SyncError.unauthorized(message: message)
            }
            throw SyncError.httpError(statusCode: response.statusCode, code: code, message: message)
        }

        return response.body
    }

    private func encode<T: Encodable>(_ value: T) throws -> Data {
        do {
            return try MintJSON.makeEncoder().encode(value)
        } catch {
            throw SyncError.decodingError("Failed to encode request: \(error.localizedDescription)")
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let trimmed = String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw SyncError.emptyResponse
        }
        do {
            return try MintJSON.makeDecoder().decode(type, from: data)
        } catch {
            throw SyncError.decodingError("Failed to decode \(type): \(error.localizedDescription)")
        }
    }
}
