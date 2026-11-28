import Foundation

/// Parsed Semantic Version 2.0 identifier supporting optional `ios-v` / `v` prefixes,
/// pre-release segments (e.g. `0.0.1-beta.10`), and local build/git-describe suffixes.
public struct SemVer: Sendable, Equatable, Hashable, Comparable {
    public let major: Int
    public let minor: Int
    public let patch: Int
    public let preRelease: [String]

    public var isPrerelease: Bool {
        !preRelease.isEmpty
    }

    public init(major: Int, minor: Int, patch: Int, preRelease: [String] = []) {
        self.major = major
        self.minor = minor
        self.patch = patch
        self.preRelease = preRelease
    }

    /// Strips optional `ios-`, `v`/`V` prefixes, `+build` metadata, and local git-describe
    /// (`-<n>-g<sha>`, `-dirty`) or build-variant (`-debug`, `-bench`) suffixes.
    public static func stripBuildSuffixes(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.lowercased().hasPrefix("ios-") {
            value = String(value.dropFirst(4))
        }
        if value.hasPrefix("v") || value.hasPrefix("V") {
            value = String(value.dropFirst())
        }
        if let plusIndex = value.firstIndex(of: "+") {
            value = String(value[..<plusIndex])
        }
        value = stripTrailingSuffix(value, pattern: #"-(debug|bench)$"#)
        value = stripTrailingSuffix(value, pattern: #"-dirty$"#)
        value = stripTrailingSuffix(value, pattern: #"-\d+-g[0-9a-fA-F]+$"#)
        return value
    }

    public static func parse(_ raw: String) -> SemVer? {
        let clean = stripBuildSuffixes(raw)
        guard !clean.isEmpty else { return nil }

        let corePart: Substring
        let prePart: Substring?
        if let dashIndex = clean.firstIndex(of: "-") {
            corePart = clean[..<dashIndex]
            prePart = clean[clean.index(after: dashIndex)...]
        } else {
            corePart = clean[...]
            prePart = nil
        }

        let coreSegments = corePart.split(separator: ".", omittingEmptySubsequences: false)
        guard !coreSegments.isEmpty, coreSegments.count <= 3 else { return nil }
        for seg in coreSegments {
            guard !seg.isEmpty, seg.allSatisfy(\.isASCII), seg.allSatisfy(\.isNumber) else {
                return nil
            }
        }

        guard let major = Int(coreSegments[0]) else { return nil }
        let minor = coreSegments.count > 1 ? (Int(coreSegments[1]) ?? -1) : 0
        let patch = coreSegments.count > 2 ? (Int(coreSegments[2]) ?? -1) : 0
        guard minor >= 0, patch >= 0 else { return nil }

        let preTokens: [String]
        if let prePart {
            let parts = prePart.split(separator: ".", omittingEmptySubsequences: false)
            guard !parts.isEmpty, parts.allSatisfy({ !$0.isEmpty }) else { return nil }
            preTokens = parts.map(String.init)
        } else {
            preTokens = []
        }

        return SemVer(major: major, minor: minor, patch: patch, preRelease: preTokens)
    }

    public static func < (lhs: SemVer, rhs: SemVer) -> Bool {
        if lhs.major != rhs.major { return lhs.major < rhs.major }
        if lhs.minor != rhs.minor { return lhs.minor < rhs.minor }
        if lhs.patch != rhs.patch { return lhs.patch < rhs.patch }

        let lPre = lhs.preRelease
        let rPre = rhs.preRelease
        if lPre.isEmpty && rPre.isEmpty { return false }
        if lPre.isEmpty { return false }
        if rPre.isEmpty { return true }

        let count = min(lPre.count, rPre.count)
        for index in 0..<count {
            let li = lPre[index]
            let ri = rPre[index]
            if li == ri { continue }
            let ln = Int(li)
            let rn = Int(ri)
            switch (ln, rn) {
            case let (lNum?, rNum?):
                return lNum < rNum
            case (_?, nil):
                return true
            case (nil, _?):
                return false
            case (nil, nil):
                return li < ri
            }
        }
        return lPre.count < rPre.count
    }

    /// Returns `true` if `candidate` is strictly newer than `current` under SemVer 2.0.
    /// Returns `false` if either version string cannot be parsed.
    public static func isNewer(_ candidate: String, than current: String) -> Bool {
        guard let c = parse(candidate), let cur = parse(current) else {
            return false
        }
        return c > cur
    }

    private static func stripTrailingSuffix(_ input: String, pattern: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return input }
        let range = NSRange(input.startIndex..<input.endIndex, in: input)
        return regex.stringByReplacingMatches(in: input, options: [], range: range, withTemplate: "")
    }
}

/// Metadata describing an available iOS app update from GitHub Releases.
public struct AppUpdateInfo: Sendable, Equatable, Hashable, Codable {
    public let version: String
    public let tagName: String
    public let releaseURL: String
    public let notes: String
    public let ipaDownloadURL: String
    public let ipaFileName: String
    public let ipaSizeBytes: Int64
    public let isPrerelease: Bool
    public let publishedAt: String?

    public init(
        version: String,
        tagName: String,
        releaseURL: String,
        notes: String,
        ipaDownloadURL: String,
        ipaFileName: String,
        ipaSizeBytes: Int64,
        isPrerelease: Bool,
        publishedAt: String? = nil
    ) {
        self.version = version
        self.tagName = tagName
        self.releaseURL = releaseURL
        self.notes = notes
        self.ipaDownloadURL = ipaDownloadURL
        self.ipaFileName = ipaFileName
        self.ipaSizeBytes = max(0, ipaSizeBytes)
        self.isPrerelease = isPrerelease
        self.publishedAt = publishedAt
    }

    public var sideStoreInstallURL: URL? {
        UpdateChecker.sideStoreInstallURL(ipaURL: ipaDownloadURL)
    }

    public var altStoreInstallURL: URL? {
        UpdateChecker.altStoreInstallURL(ipaURL: ipaDownloadURL)
    }

    public var ipaURL: URL? {
        URL(string: ipaDownloadURL)
    }

    public var releasePageURL: URL? {
        URL(string: releaseURL)
    }
}

/// Pure-Swift iOS release selector and sideload URL builder backed by GitHub Releases.
public enum UpdateChecker: Sendable {
    public static let githubReleasesAPIURLString = "https://api.github.com/repos/xihale/goke/releases?per_page=20"
    public static let githubReleasesPageURLString = "https://github.com/xihale/goke/releases"
    public static let defaultSourceJSONURL = "https://goke.xeed.ink/altstore.json"
    public static var defaultSideStoreSourceJSONURL: String { defaultSourceJSONURL }
    public static let iosTagPrefix = "ios-v"

    /// RFC 3986 unreserved characters safe in a single query parameter value without encoding.
    private static let queryValueAllowed: CharacterSet = {
        var set = CharacterSet.alphanumerics
        set.insert(charactersIn: "-._~")
        return set
    }()

    /// Parses GitHub Releases JSON (`/repos/xihale/goke/releases`) and returns the newest
    /// eligible `ios-v*` release with a `.ipa` asset that is strictly newer than `currentVersion`.
    ///
    /// - Parameters:
    ///   - data: Raw JSON payload returned by the GitHub Releases API.
    ///   - currentVersion: Installed app version (e.g. `0.0.1-beta.9` or `0.1.0`).
    ///   - includePrereleases: Override prerelease inclusion; defaults to `true` when
    ///     `currentVersion` itself is a prerelease and `false` for stable builds.
    public static func selectLatestIOSRelease(
        from data: Data,
        currentVersion: String,
        includePrereleases: Bool? = nil
    ) throws -> AppUpdateInfo? {
        guard let currentSemVer = SemVer.parse(currentVersion) else {
            return nil
        }
        let allowPrerelease = includePrereleases ?? currentSemVer.isPrerelease
        let releases = try JSONDecoder().decode([GitHubReleaseDTO].self, from: data)

        var best: (semVer: SemVer, info: AppUpdateInfo)?
        for release in releases {
            guard !release.draft else { continue }
            let trimmedTag = release.tagName.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmedTag.lowercased().hasPrefix(iosTagPrefix) else { continue }

            let versionStr = String(trimmedTag.dropFirst(iosTagPrefix.count))
            guard let candidateSemVer = SemVer.parse(versionStr) else { continue }

            let isPre = release.prerelease || candidateSemVer.isPrerelease
            if !allowPrerelease && isPre { continue }
            guard candidateSemVer > currentSemVer else { continue }

            guard let ipaAsset = selectIPAAsset(from: release.assets) else { continue }

            let htmlURL = release.htmlURL.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedReleaseURL = htmlURL.isEmpty ? githubReleasesPageURLString : htmlURL
            let notes = (release.body ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

            let info = AppUpdateInfo(
                version: versionStr,
                tagName: trimmedTag,
                releaseURL: resolvedReleaseURL,
                notes: notes,
                ipaDownloadURL: ipaAsset.browserDownloadURL.trimmingCharacters(in: .whitespacesAndNewlines),
                ipaFileName: ipaAsset.name.trimmingCharacters(in: .whitespacesAndNewlines),
                ipaSizeBytes: max(0, ipaAsset.size),
                isPrerelease: isPre,
                publishedAt: release.publishedAt
            )

            if let existing = best {
                if candidateSemVer > existing.semVer {
                    best = (candidateSemVer, info)
                }
            } else {
                best = (candidateSemVer, info)
            }
        }

        return best?.info
    }

    /// Builds `sidestore://install?url=<encoded>` for direct SideStore installation.
    public static func sideStoreInstallURL(ipaURL: String) -> URL? {
        sideloadURL(scheme: "sidestore", action: "install", targetURL: ipaURL)
    }

    /// Builds `altstore://install?url=<encoded>` for direct AltStore installation.
    public static func altStoreInstallURL(ipaURL: String) -> URL? {
        sideloadURL(scheme: "altstore", action: "install", targetURL: ipaURL)
    }

    /// Builds `sidestore://source?url=<encoded>` for 1-tap SideStore source subscription.
    public static func sideStoreAddSourceURL(sourceURL: String = defaultSourceJSONURL) -> URL? {
        sideloadURL(scheme: "sidestore", action: "source", targetURL: sourceURL)
    }

    /// Builds `altstore://source?url=<encoded>` for 1-tap AltStore source subscription.
    public static func altStoreAddSourceURL(sourceURL: String = defaultSourceJSONURL) -> URL? {
        sideloadURL(scheme: "altstore", action: "source", targetURL: sourceURL)
    }

    private static func sideloadURL(scheme: String, action: String, targetURL: String) -> URL? {
        let trimmed = targetURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let parsed = URL(string: trimmed),
              let urlScheme = parsed.scheme?.lowercased(),
              urlScheme == "https" || urlScheme == "http",
              parsed.host != nil
        else {
            return nil
        }
        guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: queryValueAllowed) else {
            return nil
        }
        return URL(string: "\(scheme)://\(action)?url=\(encoded)")
    }

    private static func selectIPAAsset(from assets: [GitHubAssetDTO]) -> GitHubAssetDTO? {
        let candidates = assets.filter { asset in
            let name = asset.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let url = asset.browserDownloadURL.trimmingCharacters(in: .whitespacesAndNewlines)
            return name.hasSuffix(".ipa") && !url.isEmpty
        }
        guard !candidates.isEmpty else { return nil }
        if let preferred = candidates.first(where: { !$0.name.lowercased().contains("sim") }) {
            return preferred
        }
        return candidates.first
    }
}

private struct GitHubReleaseDTO: Decodable {
    let tagName: String
    let htmlURL: String
    let prerelease: Bool
    let draft: Bool
    let body: String?
    let publishedAt: String?
    let assets: [GitHubAssetDTO]

    private enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case htmlURL = "html_url"
        case prerelease
        case draft
        case body
        case publishedAt = "published_at"
        case assets
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.tagName = try container.decodeIfPresent(String.self, forKey: .tagName) ?? ""
        self.htmlURL = try container.decodeIfPresent(String.self, forKey: .htmlURL) ?? ""
        self.prerelease = try container.decodeIfPresent(Bool.self, forKey: .prerelease) ?? false
        self.draft = try container.decodeIfPresent(Bool.self, forKey: .draft) ?? false
        self.body = try container.decodeIfPresent(String.self, forKey: .body)
        self.publishedAt = try container.decodeIfPresent(String.self, forKey: .publishedAt)
        self.assets = try container.decodeIfPresent([GitHubAssetDTO].self, forKey: .assets) ?? []
    }
}

private struct GitHubAssetDTO: Decodable {
    let name: String
    let browserDownloadURL: String
    let size: Int64

    private enum CodingKeys: String, CodingKey {
        case name
        case browserDownloadURL = "browser_download_url"
        case size
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.browserDownloadURL = try container.decodeIfPresent(String.self, forKey: .browserDownloadURL) ?? ""
        self.size = try container.decodeIfPresent(Int64.self, forKey: .size) ?? 0
    }
}
