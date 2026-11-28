import Foundation
import GokeCore
#if canImport(CoreImage)
import CoreImage
import CoreImage.CIFilterBuiltins
#endif
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Quick Share Error & Wire Models (`goke.xeed.ink`)

public struct TimetableShareError: Error, Sendable, Equatable, LocalizedError {
    public let message: String
    public let status: Int

    public init(_ message: String, status: Int = 0) {
        self.message = message
        self.status = status
    }

    public var errorDescription: String? { message }
}

public struct SharedTimetableBody: Sendable, Hashable, Codable {
    public let semester: SharedSemester
    public let courses: [SharedCourse]
    public let exceptions: [SharedCourseException]

    public init(
        semester: SharedSemester,
        courses: [SharedCourse],
        exceptions: [SharedCourseException] = []
    ) {
        self.semester = semester
        self.courses = courses
        self.exceptions = exceptions
    }
}

public struct PutShareRequest: Sendable, Hashable, Codable {
    public let studentId: String
    public let manageToken: String?
    public let name: String
    public let expiresAt: Int64
    public let timetable: SharedTimetableBody

    public init(
        studentId: String,
        manageToken: String? = nil,
        name: String,
        expiresAt: Int64,
        timetable: SharedTimetableBody
    ) {
        self.studentId = studentId
        self.manageToken = manageToken
        self.name = name
        self.expiresAt = expiresAt
        self.timetable = timetable
    }
}

public struct PutShareResponse: Sendable, Hashable, Codable {
    public let code: String
    public let shareUrl: String
    public let manageToken: String?
    public let name: String
    public let updatedAt: Int64
    public let expiresAt: Int64
    public let experimental: Bool

    private enum CodingKeys: String, CodingKey {
        case code, shareUrl, manageToken, name, updatedAt, expiresAt, experimental
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.code = try c.decode(String.self, forKey: .code)
        self.shareUrl = try c.decode(String.self, forKey: .shareUrl)
        self.manageToken = try c.decodeIfPresent(String.self, forKey: .manageToken)
        self.name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.expiresAt = try c.decodeIfPresent(Int64.self, forKey: .expiresAt) ?? 0
        self.experimental = try c.decodeIfPresent(Bool.self, forKey: .experimental) ?? true
    }
}

public struct ShareStatusRequest: Sendable, Hashable, Codable {
    public let studentId: String
    public let manageToken: String

    public init(studentId: String, manageToken: String) {
        self.studentId = studentId
        self.manageToken = manageToken
    }
}

public struct ShareStatusResponse: Sendable, Hashable, Codable {
    public let exists: Bool
    public let code: String?
    public let shareUrl: String?
    public let name: String?
    public let semesterName: String?
    public let updatedAt: Int64?
    public let expiresAt: Int64?

    private enum CodingKeys: String, CodingKey {
        case exists, code, shareUrl, name, semesterName, updatedAt, expiresAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.exists = try c.decodeIfPresent(Bool.self, forKey: .exists) ?? false
        self.code = try c.decodeIfPresent(String.self, forKey: .code)
        self.shareUrl = try c.decodeIfPresent(String.self, forKey: .shareUrl)
        self.name = try c.decodeIfPresent(String.self, forKey: .name)
        self.semesterName = try c.decodeIfPresent(String.self, forKey: .semesterName)
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt)
        self.expiresAt = try c.decodeIfPresent(Int64.self, forKey: .expiresAt)
    }
}

public struct DeleteShareRequest: Sendable, Hashable, Codable {
    public let studentId: String
    public let manageToken: String

    public init(studentId: String, manageToken: String) {
        self.studentId = studentId
        self.manageToken = manageToken
    }
}

public struct DeleteShareResponse: Sendable, Hashable, Codable {
    public let deleted: Bool

    private enum CodingKeys: String, CodingKey {
        case deleted
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.deleted = try c.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
    }
}

private struct ShareErrorBody: Decodable {
    let error: String?
    let message: String?
}

// MARK: - HTTP Client (`goke.xeed.ink` `/api/v1/share`)

public struct TimetableShareClient: Sendable {
    public static let defaultServerURL = "https://goke.xeed.ink"

    private let baseURL: String
    private let session: URLSession

    public init(baseURL: String = TimetableShareClient.defaultServerURL, session: URLSession = .shared) throws {
        guard let normalized = Self.normalizeServerURL(baseURL) else {
            throw TimetableShareError("服务器地址不正确")
        }
        self.baseURL = normalized
        self.session = session
    }

    public static func normalizeServerURL(_ raw: String) -> String? {
        var trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if !trimmed.contains("://") {
            trimmed = "https://\(trimmed)"
        }
        while trimmed.hasSuffix("/") {
            trimmed.removeLast()
        }
        guard let comps = URLComponents(string: trimmed),
              let scheme = comps.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              let host = comps.host, !host.isEmpty
        else {
            return nil
        }
        return trimmed
    }

    public func putShare(_ request: PutShareRequest) async throws -> PutShareResponse {
        try await send(method: "PUT", path: "/share", body: request)
    }

    public func status(_ request: ShareStatusRequest) async throws -> ShareStatusResponse {
        try await send(method: "POST", path: "/share/status", body: request)
    }

    public func delete(_ request: DeleteShareRequest) async throws -> DeleteShareResponse {
        try await send(method: "DELETE", path: "/share", body: request)
    }

    private func send<Req: Encodable, Res: Decodable>(
        method: String,
        path: String,
        body: Req
    ) async throws -> Res {
        guard let url = URL(string: "\(baseURL)/api/v1\(path)") else {
            throw TimetableShareError("服务器地址不正确")
        }
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.timeoutInterval = 30
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        urlRequest.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try JSONEncoder().encode(body)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch {
            throw TimetableShareError("无法连接到服务器：\(error.localizedDescription)")
        }

        guard let http = response as? HTTPURLResponse else {
            throw TimetableShareError("服务器返回了无效响应")
        }
        guard (200...299).contains(http.statusCode) else {
            let errBody = try? JSONDecoder().decode(ShareErrorBody.self, from: data)
            let msg = errBody?.message?.trimmingCharacters(in: .whitespacesAndNewlines)
            throw TimetableShareError(
                (msg?.isEmpty == false ? msg! : "服务器返回 \(http.statusCode)"),
                status: http.statusCode
            )
        }
        guard !data.isEmpty else {
            throw TimetableShareError("服务器返回了空响应", status: http.statusCode)
        }
        do {
            return try JSONDecoder().decode(Res.self, from: data)
        } catch {
            throw TimetableShareError("解析服务器响应失败：\(error.localizedDescription)", status: http.statusCode)
        }
    }
}

// MARK: - Local Share Record Persistence

public struct TimetableShareRecord: Sendable, Hashable, Codable {
    public var serverUrl: String
    public var displayName: String
    public var boundStudentId: String
    public var code: String
    public var shareUrl: String
    public var semesterName: String
    public var updatedAt: Int64
    public var expiresAt: Int64

    public init(
        serverUrl: String,
        displayName: String,
        boundStudentId: String,
        code: String,
        shareUrl: String,
        semesterName: String,
        updatedAt: Int64,
        expiresAt: Int64
    ) {
        self.serverUrl = serverUrl
        self.displayName = displayName
        self.boundStudentId = boundStudentId
        self.code = code
        self.shareUrl = shareUrl
        self.semesterName = semesterName
        self.updatedAt = updatedAt
        self.expiresAt = expiresAt
    }
}

public enum TimetableShareStorage {
    private static let recordKey = "goke.timetableShare.activeRecord"
    private static let serverUrlKey = "goke.timetableShare.customServerUrl"
    private static let manageTokenKey = KeychainKey.custom("timetable.share.manageToken")

    public static func loadRecord() -> TimetableShareRecord? {
        guard let data = UserDefaults.standard.data(forKey: recordKey) else { return nil }
        return try? JSONDecoder().decode(TimetableShareRecord.self, from: data)
    }

    public static func saveRecord(_ record: TimetableShareRecord, manageToken: String?) async {
        if let data = try? JSONEncoder().encode(record) {
            UserDefaults.standard.set(data, forKey: recordKey)
        }
        if let token = manageToken, !token.isEmpty {
            try? await KeychainStore.shared.set(token, for: manageTokenKey)
        }
    }

    public static func loadManageToken() async -> String? {
        try? await KeychainStore.shared.get(manageTokenKey)
    }

    public static func clearRecord() async {
        UserDefaults.standard.removeObject(forKey: recordKey)
        try? await KeychainStore.shared.delete(manageTokenKey)
    }

    public static func loadCustomServerURL() -> String {
        UserDefaults.standard.string(forKey: serverUrlKey) ?? ""
    }

    public static func saveCustomServerURL(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || TimetableShareClient.normalizeServerURL(trimmed) == TimetableShareClient.defaultServerURL {
            UserDefaults.standard.removeObject(forKey: serverUrlKey)
        } else {
            UserDefaults.standard.set(trimmed, forKey: serverUrlKey)
        }
    }
}

// MARK: - CoreImage QR Code Generator

public enum TimetableQRCodeGenerator {
    #if canImport(UIKit) && canImport(CoreImage)
    /// `CIContext` is thread-safe per Apple documentation; reuse one shared instance to avoid GPU/Metal state churn.
    private nonisolated(unsafe) static let sharedContext = CIContext()
    private static let cacheLock = NSLock()
    private nonisolated(unsafe) static var lastCacheKey: String?
    private nonisolated(unsafe) static var lastCachedImage: UIImage?

    public static func makeUIImage(from string: String, scale: CGFloat = 8) -> UIImage? {
        let cacheKey = "\(scale)|\(string)"
        if let cached = cacheLock.withLock({ lastCacheKey == cacheKey ? lastCachedImage : nil }) {
            return cached
        }
        guard let data = string.data(using: .utf8) else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let outputImage = filter.outputImage else { return nil }
        let transformed = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cgImage = sharedContext.createCGImage(transformed, from: transformed.extent) else { return nil }
        let image = UIImage(cgImage: cgImage)
        cacheLock.withLock {
            lastCacheKey = cacheKey
            lastCachedImage = image
        }
        return image
    }
    #endif
}
