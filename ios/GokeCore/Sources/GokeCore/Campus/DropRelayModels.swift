import Foundation

// MARK: - DropRelay Campus Resources Models (matches Android `core/campus/DropRelayModels.kt`)

/// Optional campus default DropRelay endpoints (empty in open-source builds; injected by private adapter factory when present).
public struct CampusDropRelayDefaults: Sendable, Hashable, Codable {
    public var shareUrl: String
    public var dropUrl: String

    public init(shareUrl: String = "", dropUrl: String = "") {
        self.shareUrl = shareUrl
        self.dropUrl = dropUrl
    }
}

public struct DropRelayFile: Sendable, Hashable, Codable, Identifiable {
    public var path: String
    public var name: String
    public var size: Int64
    public var mtime: Int64
    public var ih: String

    public var id: String { normalizedPath }

    public var normalizedPath: String {
        path.trimmingCharacters(in: .whitespacesAndNewlines)
            .drop(while: { $0 == "/" })
            .description
    }

    public var displayName: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        let norm = normalizedPath
        if let slash = norm.lastIndex(of: "/") {
            return String(norm[norm.index(after: slash)...])
        }
        return norm
    }

    public init(
        path: String,
        name: String = "",
        size: Int64 = 0,
        mtime: Int64 = 0,
        ih: String = ""
    ) {
        self.path = path
        self.name = name
        self.size = size
        self.mtime = mtime
        self.ih = ih
    }

    private enum CodingKeys: String, CodingKey {
        case path, name, size, mtime, ih
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.path = try c.decode(String.self, forKey: .path)
        self.name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.size = try c.decodeIfPresent(Int64.self, forKey: .size) ?? 0
        self.mtime = try c.decodeIfPresent(Int64.self, forKey: .mtime) ?? 0
        self.ih = try c.decodeIfPresent(String.self, forKey: .ih) ?? ""
    }
}

public struct DropRelayIndex: Sendable, Hashable, Codable {
    public var version: Int
    public var generatedAt: Int64
    public var totalBytes: Int64
    public var files: [DropRelayFile]

    public init(
        version: Int = 1,
        generatedAt: Int64 = 0,
        totalBytes: Int64 = 0,
        files: [DropRelayFile] = []
    ) {
        self.version = version
        self.generatedAt = generatedAt
        self.totalBytes = totalBytes
        self.files = files
    }

    private enum CodingKeys: String, CodingKey {
        case version, generatedAt, totalBytes, files
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        self.generatedAt = try c.decodeIfPresent(Int64.self, forKey: .generatedAt) ?? 0
        self.totalBytes = try c.decodeIfPresent(Int64.self, forKey: .totalBytes) ?? 0
        self.files = try c.decodeIfPresent([DropRelayFile].self, forKey: .files) ?? []
    }
}

public struct DropRelayLimits: Sendable, Hashable, Codable {
    public var maxFiles: Int
    public var maxRooms: Int
    public var maxRoomsPerIP: Int
    public var roomIdleSec: Int
    public var roomHardSec: Int

    public init(
        maxFiles: Int = 64,
        maxRooms: Int = 8,
        maxRoomsPerIP: Int = 2,
        roomIdleSec: Int = 7200,
        roomHardSec: Int = 21600
    ) {
        self.maxFiles = maxFiles
        self.maxRooms = maxRooms
        self.maxRoomsPerIP = maxRoomsPerIP
        self.roomIdleSec = roomIdleSec
        self.roomHardSec = roomHardSec
    }
}

public struct DropRelayHealth: Sendable, Hashable, Codable {
    public var ok: Bool
    public var version: Int
    public var mode: String
    public var capabilities: [String]
    public var peerOrigin: String
    public var shareOrigin: String
    public var dropOrigin: String
    public var rooms: Int
    public var swarms: Int
    public var seedActive: Int
    public var seedBytes: Int64
    public var loadRatio: Double
    public var p2pWaitMs: Int64
    public var limits: DropRelayLimits?

    public init(
        ok: Bool = false,
        version: Int = 1,
        mode: String = "",
        capabilities: [String] = [],
        peerOrigin: String = "",
        shareOrigin: String = "",
        dropOrigin: String = "",
        rooms: Int = 0,
        swarms: Int = 0,
        seedActive: Int = 0,
        seedBytes: Int64 = 0,
        loadRatio: Double = 0.0,
        p2pWaitMs: Int64 = 0,
        limits: DropRelayLimits? = nil
    ) {
        self.ok = ok
        self.version = version
        self.mode = mode
        self.capabilities = capabilities
        self.peerOrigin = peerOrigin
        self.shareOrigin = shareOrigin
        self.dropOrigin = dropOrigin
        self.rooms = rooms
        self.swarms = swarms
        self.seedActive = seedActive
        self.seedBytes = seedBytes
        self.loadRatio = loadRatio
        self.p2pWaitMs = p2pWaitMs
        self.limits = limits
    }

    public func discoveredPeerOrigin() -> String {
        if !peerOrigin.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return peerOrigin
        }
        if mode.caseInsensitiveCompare("share") == .orderedSame {
            return dropOrigin
        }
        if mode.caseInsensitiveCompare("drop") == .orderedSame {
            return shareOrigin
        }
        return ""
    }
}

public enum ShareBrowseEntry: Sendable, Hashable, Identifiable {
    case dir(path: String, name: String, fileCount: Int, totalBytes: Int64)
    case file(DropRelayFile)

    public var id: String {
        switch self {
        case .dir(let path, _, _, _): "dir:\(path)"
        case .file(let f): "file:\(f.normalizedPath)"
        }
    }

    public var sortName: String {
        switch self {
        case .dir(_, let name, _, _): name
        case .file(let f): f.displayName
        }
    }
}

// MARK: - Pure DropRelay Protocol Logic

/// Pure DropRelay URL normalization, index parsing, directory projection, course matching, and code extraction.
public enum DropRelayLogic {
    public static let directDownloadLimitBytes: Int64 = 200 * 1024 * 1024
    public static let codeLength: Int = 6
    private static let codeAlphabet = Set("23456789ABCDEFGHJKMNPQRSTUVWXYZ")

    private static let urlCodePattern =
        #"https?://[^\s/]+/([23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6})(?:[/?#\s]|$)"#
    private static let standaloneCodePattern =
        #"(?<![A-Za-z0-9])([23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6})(?![A-Za-z0-9])"#
    private static let courseParenPattern = #"[（(\[【][^）)\]】]*[）)\]】]"#
    private static let courseSuffixPattern = #"(?:[（(]?[上下中前后][）)]?|[IVXⅠⅡⅢⅣ]+|\d+|[A-Za-z])$"#
    private static let tokenSplitPattern = #"[\s\-_/·、,，。:：;；]+"#

    public static func normalizeBaseUrl(_ raw: String) -> String? {
        var trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") {
            trimmed.removeLast()
        }
        guard !trimmed.isEmpty, let comps = URLComponents(string: trimmed) else { return nil }
        guard let scheme = comps.scheme?.lowercased(), scheme == "http" || scheme == "https" else {
            return nil
        }
        guard let host = comps.host?.trimmingCharacters(in: .whitespacesAndNewlines), !host.isEmpty else {
            return nil
        }
        if let port = comps.port, port > 0 {
            return "\(scheme)://\(host):\(port)"
        }
        return "\(scheme)://\(host)"
    }

    public static func parseIndexJson(_ raw: String) throws -> DropRelayIndex {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let data = Data(trimmed.utf8)
        let decoder = JSONDecoder()
        if trimmed.hasPrefix("[") {
            let files = try decoder.decode([DropRelayFile].self, from: data)
            let generatedAt = files.map(\.mtime).max() ?? 0
            let totalBytes = files.reduce(Int64(0)) { $0 + max(0, $1.size) }
            return DropRelayIndex(
                version: 1,
                generatedAt: generatedAt,
                totalBytes: totalBytes,
                files: files
            )
        }
        return try decoder.decode(DropRelayIndex.self, from: data)
    }

    public static func encodePathSegments(_ path: String) -> String {
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))
        return path
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .drop(while: { $0 == "/" })
            .split(separator: "/")
            .filter { !$0.isEmpty }
            .map { seg in
                String(seg).addingPercentEncoding(withAllowedCharacters: allowed) ?? String(seg)
            }
            .joined(separator: "/")
    }

    public static func fileWebseedUrl(baseUrl: String, path: String) -> String {
        let base = normalizeBaseUrl(baseUrl) ?? baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return "\(base)/\(encodePathSegments(path))"
    }

    public static func fileBrowserUrl(baseUrl: String, path: String) -> String {
        let base = normalizeBaseUrl(baseUrl) ?? baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return "\(base)/#/\(encodePathSegments(path))"
    }

    public static func dirZipUrl(baseUrl: String, dirPath: String) -> String {
        let base = normalizeBaseUrl(baseUrl) ?? baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let encoded = encodePathSegments(dirPath)
        return encoded.isEmpty ? "\(base)/?zip=1" : "\(base)/\(encoded)?zip=1"
    }

    public static func dropRoomUrl(baseUrl: String, code: String) -> String {
        let base = normalizeBaseUrl(baseUrl) ?? baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return "\(base)/\(code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())"
    }

    public static func shouldPreferBrowserP2p(fileSize: Int64, health: DropRelayHealth? = nil) -> Bool {
        fileSize > directDownloadLimitBytes || (health != nil && (health?.p2pWaitMs ?? 0) > 0)
    }

    public static func isValidDropCode(_ raw: String) -> Bool {
        let upper = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return upper.count == codeLength && upper.allSatisfy { codeAlphabet.contains($0) }
    }

    /// Extracts a 6-character DropRelay pickup code from free text, clipboard, or URL.
    public static func extractDropCode(from text: String, dropBaseUrl: String = "") -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        if isValidDropCode(trimmed) { return trimmed.uppercased() }
        if let fromUrl = extractDropCodeFromUrl(from: trimmed, dropBaseUrl: dropBaseUrl) {
            return fromUrl
        }
        guard let regex = try? NSRegularExpression(pattern: standaloneCodePattern, options: [.caseInsensitive]) else {
            return nil
        }
        let ns = trimmed as NSString
        guard let match = regex.firstMatch(in: trimmed, range: NSRange(location: 0, length: ns.length)),
              match.numberOfRanges >= 2
        else {
            return nil
        }
        let r = match.range(at: 1)
        guard r.location != NSNotFound else { return nil }
        return ns.substring(with: r).uppercased()
    }

    /// Extracts a pickup code only from an explicit DropRelay URL (configured host or `drop.*`).
    public static func extractDropCodeFromUrl(from text: String, dropBaseUrl: String = "") -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        let configuredHost = normalizeBaseUrl(dropBaseUrl)
            .flatMap { URL(string: $0)?.host?.lowercased() }
        guard let regex = try? NSRegularExpression(pattern: urlCodePattern, options: [.caseInsensitive]) else {
            return nil
        }
        let ns = trimmed as NSString
        for match in regex.matches(in: trimmed, range: NSRange(location: 0, length: ns.length)) {
            guard match.numberOfRanges >= 2 else { continue }
            let fullUrl = ns.substring(with: match.range).trimmingCharacters(in: .whitespacesAndNewlines)
            let code = ns.substring(with: match.range(at: 1)).uppercased()
            let candidateUrlString = fullUrl.split(separator: " ").first.map(String.init) ?? fullUrl
            guard let host = URL(string: candidateUrlString)?.host?.lowercased() else { continue }
            if let configuredHost, host == configuredHost {
                return code
            }
            if host.hasPrefix("drop.") {
                return code
            }
        }
        return nil
    }

    public static func listDirectory(files: [DropRelayFile], currentDir: String) -> [ShareBrowseEntry] {
        let trimmedDir = currentDir.trimmingCharacters(in: CharacterSet(charactersIn: "/ ").union(.whitespacesAndNewlines))
        let prefix = trimmedDir.isEmpty ? "" : "\(trimmedDir)/"

        var dirAgg: [String: (count: Int, bytes: Int64)] = [:]
        var directFiles: [ShareBrowseEntry] = []

        for file in files {
            let norm = file.normalizedPath
            if !prefix.isEmpty && !norm.hasPrefix(prefix) { continue }
            let rest = prefix.isEmpty ? norm : String(norm.dropFirst(prefix.count))
            if rest.isEmpty { continue }
            if let slash = rest.firstIndex(of: "/") {
                let dirName = String(rest[..<slash])
                let prev = dirAgg[dirName] ?? (0, 0)
                dirAgg[dirName] = (prev.count + 1, prev.bytes + max(0, file.size))
            } else {
                directFiles.append(.file(file))
            }
        }

        let dirs: [ShareBrowseEntry] = dirAgg.map { name, agg in
            ShareBrowseEntry.dir(
                path: prefix.isEmpty ? name : "\(prefix)\(name)",
                name: name,
                fileCount: agg.count,
                totalBytes: agg.bytes
            )
        }.sorted { $0.sortName.lowercased() < $1.sortName.lowercased() }

        let sortedFiles = directFiles.sorted { $0.sortName.lowercased() < $1.sortName.lowercased() }
        return dirs + sortedFiles
    }

    public static func searchFiles(files: [DropRelayFile], query: String) -> [DropRelayFile] {
        let tokens = splitTokens(query.lowercased())
        if tokens.isEmpty { return files }
        return files.filter { file in
            let hay = "\(file.normalizedPath) \(file.displayName)".lowercased()
            return tokens.allSatisfy { hay.contains($0) }
        }
    }

    public static func matchCourseResources(files: [DropRelayFile], courseName: String) -> [DropRelayFile] {
        let raw = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard raw.count >= 2, !files.isEmpty else { return [] }

        let withoutParen = replacingRegex(courseParenPattern, in: raw, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let withoutSuffix = replacingRegex(courseSuffixPattern, in: withoutParen, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let cleaned = withoutSuffix.isEmpty ? raw : withoutSuffix

        var candidates: [String] = []
        var seen = Set<String>()
        func addCandidate(_ s: String) {
            let lower = s.lowercased()
            if lower.count >= 2, seen.insert(lower).inserted {
                candidates.append(lower)
            }
        }
        addCandidate(raw)
        addCandidate(cleaned)
        for token in splitTokens(cleaned) {
            addCandidate(token)
        }

        return files.filter { file in
            let hay = "\(file.normalizedPath) \(file.displayName)".lowercased()
            return candidates.contains { hay.contains($0) }
        }
    }

    public static func formatFileSize(_ bytes: Int64) -> String {
        let b = max(0, bytes)
        if b < 1024 {
            return "\(b) B"
        }
        if b < 1024 * 1024 {
            return String(format: "%.1f KB", Double(b) / 1024.0)
        }
        if b < 1024 * 1024 * 1024 {
            return String(format: "%.1f MB", Double(b) / (1024.0 * 1024.0))
        }
        return String(format: "%.2f GB", Double(b) / (1024.0 * 1024.0 * 1024.0))
    }

    private static func splitTokens(_ input: String) -> [String] {
        let replaced = replacingRegex(tokenSplitPattern, in: input, with: "\n")
        return replaced
            .split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private static func replacingRegex(_ pattern: String, in text: String, with template: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return text }
        let ns = text as NSString
        return regex.stringByReplacingMatches(in: text, range: NSRange(location: 0, length: ns.length), withTemplate: template)
    }
}
