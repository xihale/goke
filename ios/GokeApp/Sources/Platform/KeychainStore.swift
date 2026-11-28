import Foundation
#if canImport(Security)
import Security
#endif

/// Strongly typed Keychain account identifiers for Goke secrets.
/// Secrets are never written to `UserDefaults` or SQLite snapshots.
public enum KeychainKey: Sendable, Hashable {
    case syncBearerToken
    case syncServerURL
    case syncUsername
    case jxfwAccount
    case jxfwPassword
    case tzcsPassword
    case custom(String)

    public var account: String {
        switch self {
        case .syncBearerToken: "sync.bearerToken"
        case .syncServerURL: "sync.serverURL"
        case .syncUsername: "sync.username"
        case .jxfwAccount: "campus.jxfw.account"
        case .jxfwPassword: "campus.jxfw.password"
        case .tzcsPassword: "campus.tzcs.password"
        case .custom(let raw): raw
        }
    }
}

public enum KeychainError: Error, Sendable, Equatable, LocalizedError {
    case encodingFailed
    case unexpectedData
    case osStatus(Int32, String)
    case unavailable

    public var errorDescription: String? {
        switch self {
        case .encodingFailed:
            "无法将凭据编码为 UTF-8 数据。"
        case .unexpectedData:
            "钥匙串返回的数据格式无效。"
        case .osStatus(let status, let message):
            "钥匙串操作失败 (\(status))：\(message)"
        case .unavailable:
            "当前平台不支持系统钥匙串。"
        }
    }
}

/// Actor-isolated Security framework Keychain wrapper (`kSecClassGenericPassword`).
public actor KeychainStore {
    public static let shared = KeychainStore()

    public let service: String
    public let accessGroup: String?

    public init(service: String = "app.goke.ios.keychain", accessGroup: String? = nil) {
        self.service = service
        self.accessGroup = accessGroup
    }

    // MARK: - Typed Key Convenience

    public func set(_ value: String, for key: KeychainKey) throws {
        try set(value, account: key.account)
    }

    public func get(_ key: KeychainKey) throws -> String? {
        try get(account: key.account)
    }

    public func setData(_ data: Data, for key: KeychainKey) throws {
        try setData(data, account: key.account)
    }

    public func getData(_ key: KeychainKey) throws -> Data? {
        try getData(account: key.account)
    }

    public func delete(_ key: KeychainKey) throws {
        try delete(account: key.account)
    }

    public func contains(_ key: KeychainKey) -> Bool {
        (try? getData(account: key.account)) != nil
    }

    // MARK: - Raw Account Operations

    public func set(_ value: String, account: String) throws {
        guard let data = value.data(using: .utf8) else {
            throw KeychainError.encodingFailed
        }
        try setData(data, account: account)
    }

    public func get(account: String) throws -> String? {
        guard let data = try getData(account: account) else { return nil }
        guard let string = String(data: data, encoding: .utf8) else {
            throw KeychainError.unexpectedData
        }
        return string
    }

    public func setData(_ data: Data, account: String) throws {
        #if canImport(Security)
        var query = baseQuery(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess {
            return
        }
        if updateStatus != errSecItemNotFound {
            throw Self.makeError(updateStatus)
        }

        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(query as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw Self.makeError(addStatus)
        }
        #else
        throw KeychainError.unavailable
        #endif
    }

    public func getData(account: String) throws -> Data? {
        #if canImport(Security)
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw Self.makeError(status)
        }
        guard let data = result as? Data else {
            throw KeychainError.unexpectedData
        }
        return data
        #else
        throw KeychainError.unavailable
        #endif
    }

    public func delete(account: String) throws {
        #if canImport(Security)
        let query = baseQuery(account: account)
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw Self.makeError(status)
        }
        #else
        throw KeychainError.unavailable
        #endif
    }

    /// Removes every item stored under this Keychain `service`.
    public func deleteAll() throws {
        #if canImport(Security)
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw Self.makeError(status)
        }
        #else
        throw KeychainError.unavailable
        #endif
    }

    // MARK: - Helpers

    #if canImport(Security)
    private func baseQuery(account: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }

    private static func makeError(_ status: OSStatus) -> KeychainError {
        let message = (SecCopyErrorMessageString(status, nil) as String?) ?? "OSStatus \(status)"
        return .osStatus(Int32(status), message)
    }
    #endif
}
