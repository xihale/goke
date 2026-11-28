import Foundation

// MARK: - Dual-Source Academic Notice Models (matches Android `core/jxfw/Jxfw.kt`)

/// Notice source: public academic-office bulletin or authenticated portal inbox.
public enum JxfwNoticeSource: String, Sendable, Hashable, Codable, CaseIterable {
    case `public` = "PUBLIC"
    case `internal` = "INTERNAL"
}

/// Academic notice item (device-local cache only; not stored in Repository/Sync).
public struct JxfwNotice: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var title: String
    public var date: String
    public var url: String
    public var source: JxfwNoticeSource

    public init(
        id: String,
        title: String,
        date: String = "",
        url: String = "",
        source: JxfwNoticeSource = .public
    ) {
        self.id = id
        self.title = title
        self.date = date
        self.url = url
        self.source = source
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, date, url, source
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decode(String.self, forKey: .title)
        self.date = try c.decodeIfPresent(String.self, forKey: .date) ?? ""
        self.url = try c.decodeIfPresent(String.self, forKey: .url) ?? ""
        self.source = try c.decodeIfPresent(JxfwNoticeSource.self, forKey: .source) ?? .public
    }
}

/// Conditional fetch outcome for public notices (supports HTTP 304 zero-parse short-circuit).
public enum PublicNoticesFetch: Sendable, Equatable {
    case notModified
    case fetched(notices: [JxfwNotice], etag: String?, lastModified: String?)
}

// MARK: - Incremental Diff & Multi-Source Merge

/// Pure incremental deduplication and multi-source merging for academic notices.
public enum JxfwNoticeDiff {
    public static let maxSeenIds: Int = 100
    public static let maxCachedNotices: Int = 30

    public struct DiffResult: Sendable, Equatable {
        public let newNotices: [JxfwNotice]
        public let updatedSeenIds: [String]

        public init(newNotices: [JxfwNotice], updatedSeenIds: [String]) {
            self.newNotices = newNotices
            self.updatedSeenIds = updatedSeenIds
        }
    }

    /// Computes newly arrived notices and updates the ordered `seenIds` ring buffer.
    ///
    /// - When `isInitialBaseline` is `true`, all incoming IDs are recorded without emitting new notifications.
    /// - Deduplicates strictly by `JxfwNotice.id` without assuming monotonic dates (handles pinned notices).
    public static func diffNew(
        incoming: [JxfwNotice],
        seenIds: [String],
        isInitialBaseline: Bool,
        maxSeenIds: Int = JxfwNoticeDiff.maxSeenIds
    ) -> DiffResult {
        let seenSet = Set(seenIds)
        var dedupedIncoming: [JxfwNotice] = []
        var incomingSeen = Set<String>()
        for item in incoming {
            let trimmedId = item.id.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmedId.isEmpty, incomingSeen.insert(item.id).inserted else { continue }
            dedupedIncoming.append(item)
        }

        let newOnes: [JxfwNotice] = isInitialBaseline
            ? []
            : dedupedIncoming.filter { !seenSet.contains($0.id) }

        var merged: [String] = []
        var mergedSet = Set<String>()
        for item in dedupedIncoming {
            if mergedSet.insert(item.id).inserted {
                merged.append(item.id)
            }
        }
        for id in seenIds {
            if !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, mergedSet.insert(id).inserted {
                merged.append(id)
            }
        }

        let limit = max(maxSeenIds, dedupedIncoming.count)
        return DiffResult(
            newNotices: newOnes,
            updatedSeenIds: Array(merged.prefix(limit))
        )
    }

    /// Merges public and internal notices: internal messages appear first, deduplicated by `id`, capped at `maxItems`.
    public static func mergeSources(
        publicNotices: [JxfwNotice],
        internalNotices: [JxfwNotice],
        maxItems: Int = JxfwNoticeDiff.maxCachedNotices
    ) -> [JxfwNotice] {
        var seen = Set<String>()
        var result: [JxfwNotice] = []
        result.reserveCapacity(min(maxItems, internalNotices.count + publicNotices.count))
        for item in internalNotices {
            guard !item.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  seen.insert(item.id).inserted else { continue }
            result.append(item)
            if result.count >= maxItems { return result }
        }
        for item in publicNotices {
            guard !item.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  seen.insert(item.id).inserted else { continue }
            result.append(item)
            if result.count >= maxItems { return result }
        }
        return result
    }
}
