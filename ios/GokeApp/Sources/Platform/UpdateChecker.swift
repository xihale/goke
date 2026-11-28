import Foundation
import GokeCore

public extension UpdateInterval {
    var displayName: String {
        switch self {
        case .STARTUP: return "每次启动"
        case .DAILY: return "每天"
        case .WEEKLY: return "每周"
        case .OFF: return "关闭"
        }
    }

    var thresholdMillis: Int64? {
        switch self {
        case .STARTUP: return 0
        case .DAILY: return 24 * 60 * 60 * 1000
        case .WEEKLY: return 7 * 24 * 60 * 60 * 1000
        case .OFF: return nil
        }
    }
}

/// Release metadata returned by `https://goke.xeed.ink/downloads/latest.json`.
public struct GokeReleaseInfo: Sendable, Equatable {
    public let version: String
    public let url: String
    public let notes: String?

    public init(version: String, url: String, notes: String?) {
        self.version = version
        self.url = url
        self.notes = notes
    }
}

public enum GokeUpdateCheckResult: Sendable, Equatable {
    case idle
    case upToDate
    case newer(GokeReleaseInfo)
    case error(String)
}

/// SemVer 2.0 parser & comparator matching Android `UpdateChecker.kt`.
public struct GokeSemVer: Sendable, Equatable, Comparable {
    public let major: Int
    public let minor: Int
    public let patch: Int
    public let preRelease: [String]

    public init(major: Int, minor: Int, patch: Int, preRelease: [String] = []) {
        self.major = major
        self.minor = minor
        self.patch = patch
        self.preRelease = preRelease
    }

    public static func stripBuildSuffixes(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("v") || s.hasPrefix("V") {
            s.removeFirst()
        }
        if let range = s.range(of: #"-(debug|bench)$"#, options: .regularExpression) {
            s.removeSubrange(range)
        }
        if let range = s.range(of: #"-dirty$"#, options: .regularExpression) {
            s.removeSubrange(range)
        }
        if let range = s.range(of: #"-\d+-g[0-9a-fA-F]+$"#, options: .regularExpression) {
            s.removeSubrange(range)
        }
        return s
    }

    public static func parse(_ raw: String) -> GokeSemVer? {
        let clean = stripBuildSuffixes(raw)
        guard !clean.isEmpty else { return nil }
        let dashIdx = clean.firstIndex(of: "-")
        let coreStr = dashIdx.map { String(clean[..<$0]) } ?? clean
        let preStr = dashIdx.map { String(clean[clean.index(after: $0)...]) }

        let parts = coreStr.split(separator: ".", omittingEmptySubsequences: false)
        guard !parts.isEmpty, parts.count <= 3 else { return nil }
        guard parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) }) else { return nil }

        guard let major = Int(parts[0]) else { return nil }
        let minor = parts.count > 1 ? (Int(parts[1]) ?? 0) : 0
        let patch = parts.count > 2 ? (Int(parts[2]) ?? 0) : 0

        let pre: [String]
        if let preStr {
            let tokens = preStr.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
            guard !tokens.contains(where: \.isEmpty) else { return nil }
            pre = tokens
        } else {
            pre = []
        }
        return GokeSemVer(major: major, minor: minor, patch: patch, preRelease: pre)
    }

    public static func < (lhs: GokeSemVer, rhs: GokeSemVer) -> Bool {
        if lhs.major != rhs.major { return lhs.major < rhs.major }
        if lhs.minor != rhs.minor { return lhs.minor < rhs.minor }
        if lhs.patch != rhs.patch { return lhs.patch < rhs.patch }

        if lhs.preRelease.isEmpty && rhs.preRelease.isEmpty { return false }
        if lhs.preRelease.isEmpty { return false }
        if rhs.preRelease.isEmpty { return true }

        let count = min(lhs.preRelease.count, rhs.preRelease.count)
        for idx in 0..<count {
            let a = lhs.preRelease[idx]
            let b = rhs.preRelease[idx]
            if a == b { continue }
            if let an = Int(a), let bn = Int(b) {
                return an < bn
            }
            if Int(a) != nil { return true }
            if Int(b) != nil { return false }
            return a < b
        }
        return lhs.preRelease.count < rhs.preRelease.count
    }

    public static func isNewer(_ candidate: String, than current: String) -> Bool {
        guard let c = parse(candidate), let cur = parse(current) else { return false }
        return cur < c
    }
}

/// Lightweight sideload update checker fetching `https://goke.xeed.ink/downloads/latest.json`
/// and aggregating intermediate release notes for iOS sideload users.
public actor GokeUpdateChecker {
    public static let shared = GokeUpdateChecker()

    public static let landingURL = "https://goke.xeed.ink"
    public static let manifestURL = URL(string: "https://goke.xeed.ink/downloads/latest.json")!

    private static let lastCheckKey = "goke.update.lastCheckAt"
    private var hasCompletedStartupCheck: Bool = false

    private struct RemoteRelease: Decodable, Sendable {
        let tagName: String
        let htmlUrl: String?
        let prerelease: Bool?
        let draft: Bool?
        let body: String?

        enum CodingKeys: String, CodingKey {
            case tagName = "tag_name"
            case htmlUrl = "html_url"
            case prerelease
            case draft
            case body
        }
    }

    private struct LatestManifest: Decodable, Sendable {
        let tagName: String?
        let htmlUrl: String?
        let prerelease: Bool?
        let draft: Bool?
        let body: String?
        let stable: RemoteRelease?
        let latestAny: RemoteRelease?
        let changelog: [RemoteRelease]?

        enum CodingKeys: String, CodingKey {
            case tagName = "tag_name"
            case htmlUrl = "html_url"
            case prerelease
            case draft
            case body
            case stable
            case latestAny = "latest_any"
            case changelog
        }
    }

    public init() {}

    /// Fetches `downloads/latest.json` and compares against `currentVersion`.
    public func checkNow(currentVersion: String) async -> GokeUpdateCheckResult {
        var request = URLRequest(url: Self.manifestURL)
        request.timeoutInterval = 12
        request.cachePolicy = .reloadIgnoringLocalCacheData

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                return .error("服务器返回状态码 \(http.statusCode)")
            }
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: Self.lastCheckKey)
            return Self.evaluateManifestData(data, currentVersion: currentVersion)
        } catch {
            return .error(error.localizedDescription)
        }
    }

    public static func lastCheckDate() -> Date? {
        let ts = UserDefaults.standard.double(forKey: lastCheckKey)
        return ts > 0 ? Date(timeIntervalSince1970: ts) : nil
    }

    /// Returns whether an automatic check should run according to `settings.updateInterval` and `lastUpdateCheckAt`.
    public static func isCheckDue(
        settings: AppSettings,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Bool {
        guard let threshold = settings.updateInterval.thresholdMillis else { return false }
        if settings.updateInterval == .STARTUP { return true }
        if settings.lastUpdateCheckAt <= 0 { return true }
        return nowMillis - settings.lastUpdateCheckAt >= threshold
    }

    /// Returns the persisted pending release if it is newer than `currentVersion` and not dismissed.
    public static func pendingRelease(
        from settings: AppSettings,
        currentVersion: String
    ) -> GokeReleaseInfo? {
        let ver = settings.pendingUpdateVersion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !ver.isEmpty else { return nil }
        guard GokeSemVer.isNewer(ver, than: currentVersion) else { return nil }
        if settings.dismissedUpdateVersion == ver { return nil }
        let url = settings.pendingUpdateUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let notes = settings.pendingUpdateNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        return GokeReleaseInfo(
            version: ver,
            url: url.isEmpty ? Self.landingURL : url,
            notes: notes.isEmpty ? nil : notes
        )
    }

    /// Marks a version as dismissed so passive badges/banners stay hidden until a newer release arrives.
    @MainActor
    public static func dismissPendingUpdate(version: String, settingsStore: AppSettingsStore) {
        let clean = version.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        settingsStore.update {
            $0.dismissedUpdateVersion = clean
        }
    }

    /// Runs a network update check when due (or when `force == true`) and persists `lastUpdateCheckAt`
    /// plus `pendingUpdate*` fields into `AppSettingsStore`.
    public func checkIfDue(
        settingsStore: AppSettingsStore,
        currentVersion: String,
        force: Bool = false
    ) async -> GokeUpdateCheckResult {
        let snapshot = await MainActor.run { settingsStore.settings }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)

        if !force {
            if snapshot.updateInterval == .OFF {
                return Self.pendingRelease(from: snapshot, currentVersion: currentVersion).map { .newer($0) } ?? .idle
            }
            if snapshot.updateInterval == .STARTUP && hasCompletedStartupCheck {
                return Self.pendingRelease(from: snapshot, currentVersion: currentVersion).map { .newer($0) } ?? .idle
            }
            if !Self.isCheckDue(settings: snapshot, nowMillis: nowMillis) {
                return Self.pendingRelease(from: snapshot, currentVersion: currentVersion).map { .newer($0) } ?? .idle
            }
        }

        let result = await checkNow(currentVersion: currentVersion)
        switch result {
        case .newer(let info):
            hasCompletedStartupCheck = true
            await MainActor.run {
                settingsStore.update {
                    $0.lastUpdateCheckAt = nowMillis
                    $0.pendingUpdateVersion = info.version
                    $0.pendingUpdateUrl = info.url
                    $0.pendingUpdateNotes = info.notes ?? ""
                    if force && $0.dismissedUpdateVersion == info.version {
                        $0.dismissedUpdateVersion = ""
                    }
                }
            }
        case .upToDate:
            hasCompletedStartupCheck = true
            await MainActor.run {
                settingsStore.update {
                    $0.lastUpdateCheckAt = nowMillis
                    $0.pendingUpdateVersion = ""
                    $0.pendingUpdateUrl = ""
                    $0.pendingUpdateNotes = ""
                }
            }
        case .idle, .error:
            break
        }
        return result
    }

    /// Pure parser + comparator for `latest.json` (supports both object manifest and release array).
    static func evaluateManifestData(_ data: Data, currentVersion: String) -> GokeUpdateCheckResult {
        let decoder = JSONDecoder()
        let candidates: [RemoteRelease]
        let changelog: [RemoteRelease]

        if let list = try? decoder.decode([RemoteRelease].self, from: data) {
            candidates = list
            changelog = list
        } else if let manifest = try? decoder.decode(LatestManifest.self, from: data) {
            let entries = [manifest.stable, manifest.latestAny].compactMap { $0 }
            if !entries.isEmpty {
                candidates = entries
            } else if let tag = manifest.tagName, !tag.isEmpty {
                candidates = [
                    RemoteRelease(
                        tagName: tag,
                        htmlUrl: manifest.htmlUrl,
                        prerelease: manifest.prerelease,
                        draft: manifest.draft,
                        body: manifest.body
                    )
                ]
            } else {
                candidates = []
            }
            changelog = manifest.changelog ?? candidates
        } else {
            return .error("更新清单格式无法解析")
        }

        let currentSemVer = GokeSemVer.parse(currentVersion)
        let allowPrerelease = !(currentSemVer?.preRelease.isEmpty ?? true)

        let eligible = candidates.filter { rel in
            guard rel.draft != true else { return false }
            if rel.prerelease == true && !allowPrerelease { return false }
            return GokeSemVer.isNewer(rel.tagName, than: currentVersion)
        }

        guard let best = eligible.max(by: { lhs, rhs in
            guard let lv = GokeSemVer.parse(lhs.tagName), let rv = GokeSemVer.parse(rhs.tagName) else {
                return false
            }
            return lv < rv
        }) else {
            return .upToDate
        }

        let aggregatedNotes = aggregateNotes(
            changelog: changelog,
            currentVersion: currentVersion,
            latestVersion: best.tagName,
            allowPrerelease: allowPrerelease,
            fallback: best.body
        )

        let cleanVersion = GokeSemVer.stripBuildSuffixes(best.tagName)
        let resolvedURL = (best.htmlUrl?.isEmpty == false) ? best.htmlUrl! : Self.landingURL
        return .newer(
            GokeReleaseInfo(
                version: cleanVersion.isEmpty ? best.tagName : cleanVersion,
                url: resolvedURL,
                notes: aggregatedNotes
            )
        )
    }

    private static func aggregateNotes(
        changelog: [RemoteRelease],
        currentVersion: String,
        latestVersion: String,
        allowPrerelease: Bool,
        fallback: String?
    ) -> String? {
        guard let latestSem = GokeSemVer.parse(latestVersion) else {
            return fallback?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let between = changelog
            .filter { rel in
                guard rel.draft != true else { return false }
                if rel.prerelease == true && !allowPrerelease { return false }
                guard let v = GokeSemVer.parse(rel.tagName) else { return false }
                let newerThanCurrent = GokeSemVer.isNewer(rel.tagName, than: currentVersion)
                return newerThanCurrent && !(latestSem < v)
            }
            .sorted { lhs, rhs in
                guard let lv = GokeSemVer.parse(lhs.tagName), let rv = GokeSemVer.parse(rhs.tagName) else {
                    return false
                }
                return rv < lv
            }

        let blocks: [String] = between.compactMap { rel in
            let text = rel.body?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !text.isEmpty else { return nil }
            let tag = rel.tagName.hasPrefix("v") ? rel.tagName : "v\(rel.tagName)"
            return between.count > 1 ? "【\(tag)】\n\(text)" : text
        }

        if !blocks.isEmpty {
            return blocks.joined(separator: "\n\n")
        }
        let trimmedFallback = fallback?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (trimmedFallback?.isEmpty == false) ? trimmedFallback : nil
    }
}
