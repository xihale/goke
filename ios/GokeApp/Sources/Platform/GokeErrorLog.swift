import Foundation
import GokeShared

/// Persisted diagnostic error entry captured at runtime in `GokeErrorLog`.
public struct GokeErrorEntry: Sendable, Codable, Hashable, Identifiable {
    public let id: String
    public let timestampMillis: Int64
    public let source: String
    public let summary: String
    public let detail: String
    public let isFatal: Bool
    public let acknowledged: Bool

    public init(
        id: String = UUID().uuidString.lowercased(),
        timestampMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        source: String,
        summary: String,
        detail: String = "",
        isFatal: Bool = false,
        acknowledged: Bool = false
    ) {
        self.id = id
        self.timestampMillis = timestampMillis
        self.source = source
        self.summary = summary
        self.detail = detail
        self.isFatal = isFatal
        self.acknowledged = acknowledged
    }

    public func withAcknowledged(_ acknowledged: Bool) -> GokeErrorEntry {
        GokeErrorEntry(
            id: id,
            timestampMillis: timestampMillis,
            source: source,
            summary: summary,
            detail: detail,
            isFatal: isFatal,
            acknowledged: acknowledged
        )
    }

    public var formattedDateTime: String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestampMillis) / 1000.0)
        return Self.dateFormatter.string(from: date)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()
}

/// Persistent ring-buffer error & diagnostic log stored in Application Support (`error-log.json`).
public actor GokeErrorLog {
    public static let shared = GokeErrorLog()
    public static let maxEntries = 50
    public static let dedupWindowMillis: Int64 = 30_000

    private let fileURL: URL
    private let maxEntries: Int
    private var entriesCache: [GokeErrorEntry]?
    private let decoder = JSONDecoder()
    private let encoder: JSONEncoder = {
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return enc
    }()
    private let detailDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()

    public init(
        fileURL: URL? = nil,
        maxEntries: Int = GokeErrorLog.maxEntries,
        fileManager: FileManager = .default
    ) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? AppGroupContainer.shared.rootURL
            let dir = base.appendingPathComponent("GokeDiagnostics", isDirectory: true)
            self.fileURL = dir.appendingPathComponent("error-log.json", isDirectory: false)
        }
        self.maxEntries = max(1, maxEntries)
    }

    /// Loads persisted entries (newest-first).
    public func entries() -> [GokeErrorEntry] {
        ensureLoaded()
        return entriesCache ?? []
    }

    /// Returns entries not yet acknowledged by the user.
    public func unacknowledgedEntries() -> [GokeErrorEntry] {
        entries().filter { !$0.acknowledged }
    }

    /// Records a Swift `Error` with deduplication across rapid identical failures.
    @discardableResult
    public func record(
        source: String,
        error: any Error,
        isFatal: Bool = false,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> GokeErrorEntry? {
        if error is CancellationError { return nil }
        let typeName = String(describing: type(of: error))
        let message = error.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        let summary = message.isEmpty ? typeName : "\(typeName): \(message)"
        let detail = formatDetail(
            source: source,
            summary: summary,
            rawDetail: String(reflecting: error),
            isFatal: isFatal,
            timestampMillis: nowMillis
        )
        return recordEntry(
            source: source,
            summary: summary,
            detail: detail,
            isFatal: isFatal,
            nowMillis: nowMillis
        )
    }

    /// Records a textual diagnostic failure into the ring buffer.
    @discardableResult
    public func record(
        source: String,
        summary: String,
        detail: String = "",
        isFatal: Bool = false,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> GokeErrorEntry? {
        let cleanSummary = summary.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanSummary.isEmpty else { return nil }
        let formatted = formatDetail(
            source: source,
            summary: cleanSummary,
            rawDetail: detail.trimmingCharacters(in: .whitespacesAndNewlines),
            isFatal: isFatal,
            timestampMillis: nowMillis
        )
        return recordEntry(
            source: source,
            summary: cleanSummary,
            detail: formatted,
            isFatal: isFatal,
            nowMillis: nowMillis
        )
    }

    /// Marks all stored error entries as acknowledged.
    public func acknowledgeAll() {
        ensureLoaded()
        guard let current = entriesCache, current.contains(where: { !$0.acknowledged }) else { return }
        let updated = current.map { $0.acknowledged ? $0 : $0.withAcknowledged(true) }
        entriesCache = updated
        persist(updated)
    }

    /// Clears all persisted diagnostic entries.
    public func clear() {
        entriesCache = []
        try? FileManager.default.removeItem(at: fileURL)
    }

    /// Builds a copy-ready multi-entry diagnostic report string.
    public func formattedDiagnosticReport(limit: Int = 10) -> String {
        let items = Array(entries().prefix(max(1, limit)))
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        let header = [
            "=== Goke iOS Diagnostic Report ===",
            "Version: v\(version) (\(build))",
            "OS: \(ProcessInfo.processInfo.operatingSystemVersionString)",
            "Entries: \(items.count)",
        ].joined(separator: "\n")

        guard !items.isEmpty else {
            return "\(header)\n\n(No errors recorded)"
        }

        let body = items.map { entry in
            entry.detail.isEmpty
                ? "[\(entry.formattedDateTime)] [\(entry.source)] \(entry.summary)"
                : entry.detail
        }.joined(separator: "\n\n---\n\n")
        return "\(header)\n\n\(body)"
    }

    // MARK: - Internal Ring Buffer & Persistence

    private func recordEntry(
        source: String,
        summary: String,
        detail: String,
        isFatal: Bool,
        nowMillis: Int64
    ) -> GokeErrorEntry? {
        ensureLoaded()
        let current = entriesCache ?? []
        if let latest = current.first,
           !latest.acknowledged,
           latest.source == source,
           latest.summary == summary,
           (0...Self.dedupWindowMillis).contains(nowMillis - latest.timestampMillis)
        {
            return nil
        }

        let entry = GokeErrorEntry(
            timestampMillis: nowMillis,
            source: source,
            summary: summary,
            detail: detail,
            isFatal: isFatal,
            acknowledged: false
        )
        let updated = Array(([entry] + current).prefix(maxEntries))
        entriesCache = updated
        persist(updated)
        return entry
    }

    private func ensureLoaded() {
        guard entriesCache == nil else { return }
        guard let data = try? Data(contentsOf: fileURL),
              !data.isEmpty,
              let decoded = try? decoder.decode([GokeErrorEntry].self, from: data)
        else {
            entriesCache = []
            return
        }
        entriesCache = Array(decoded.prefix(maxEntries))
    }

    private func persist(_ items: [GokeErrorEntry]) {
        let dir = fileURL.deletingLastPathComponent()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? encoder.encode(items) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    private func formatDetail(
        source: String,
        summary: String,
        rawDetail: String,
        isFatal: Bool,
        timestampMillis: Int64
    ) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestampMillis) / 1000.0)
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"

        var lines: [String] = [
            "=== Goke Error Entry ===",
            "Version: v\(version) (\(build))",
            "OS: \(ProcessInfo.processInfo.operatingSystemVersionString)",
            "Time: \(detailDateFormatter.string(from: date))",
            "Source: \(source) (\(isFatal ? "fatal" : "non-fatal"))",
            "Summary: \(summary)",
        ]
        if !rawDetail.isEmpty {
            let maxChars = 4000
            let clipped = rawDetail.count > maxChars
                ? String(rawDetail.prefix(maxChars)) + "\n... (truncated)"
                : rawDetail
            lines.append("")
            lines.append(clipped)
        }
        return lines.joined(separator: "\n")
    }
}
