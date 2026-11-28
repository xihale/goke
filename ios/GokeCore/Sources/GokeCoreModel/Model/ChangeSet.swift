import Foundation

/// Single atomic entity mutation with polymorphic `_t` discriminator (`"add"`, `"modify"`, `"remove"`).
public enum Change: Sendable, Hashable, Codable {
    case add(after: Record)
    case modify(before: Record, after: Record)
    case remove(before: Record)

    public var record: Record {
        switch self {
        case .add(let after): return after
        case .modify(_, let after): return after
        case .remove(let before): return before
        }
    }

    public var discriminator: String {
        switch self {
        case .add: return "add"
        case .modify: return "modify"
        case .remove: return "remove"
        }
    }

    private enum CodingKeys: String, CodingKey {
        case type = "_t"
        case before
        case after
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)
        switch type {
        case "add":
            self = .add(after: try c.decode(Record.self, forKey: .after))
        case "modify":
            self = .modify(
                before: try c.decode(Record.self, forKey: .before),
                after: try c.decode(Record.self, forKey: .after)
            )
        case "remove":
            self = .remove(before: try c.decode(Record.self, forKey: .before))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: c,
                debugDescription: "Unknown Change _t discriminator: \(type)"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(discriminator, forKey: .type)
        switch self {
        case .add(let after):
            try c.encode(after, forKey: .after)
        case .modify(let before, let after):
            try c.encode(before, forKey: .before)
            try c.encode(after, forKey: .after)
        case .remove(let before):
            try c.encode(before, forKey: .before)
        }
    }
}

/// Unified write unit for all mutations (manual, import, parcel, sync).
public struct ChangeSet: Sendable, Hashable, Codable, Identifiable {
    public var id: String
    public var description: String
    public var source: Source
    public var createdAt: Int64
    public var changes: [Change]
    public var warnings: [String]
    public var logs: [String]
    public var returnValue: String?

    public init(
        id: String = UUID().uuidString.lowercased(),
        description: String = "",
        source: Source = .MANUAL,
        createdAt: Int64 = 0,
        changes: [Change] = [],
        warnings: [String] = [],
        logs: [String] = [],
        returnValue: String? = nil
    ) {
        self.id = id
        self.description = description
        self.source = source
        self.createdAt = createdAt
        self.changes = changes
        self.warnings = warnings
        self.logs = logs
        self.returnValue = returnValue
    }

    public var isEmpty: Bool { changes.isEmpty }
    public var addCount: Int { changes.filter { if case .add = $0 { return true }; return false }.count }
    public var modifyCount: Int { changes.filter { if case .modify = $0 { return true }; return false }.count }
    public var removeCount: Int { changes.filter { if case .remove = $0 { return true }; return false }.count }

    public func toJSON() throws -> String {
        try MintJSON.encodeToString(self)
    }

    public static func fromJSON(_ text: String) throws -> ChangeSet {
        try MintJSON.decodeFromString(ChangeSet.self, from: text)
    }

    private enum CodingKeys: String, CodingKey {
        case id, description, source, createdAt, changes, warnings, logs, returnValue
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        self.source = try c.decodeIfPresent(Source.self, forKey: .source) ?? .MANUAL
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.changes = try c.decodeIfPresent([Change].self, forKey: .changes) ?? []
        self.warnings = try c.decodeIfPresent([String].self, forKey: .warnings) ?? []
        self.logs = try c.decodeIfPresent([String].self, forKey: .logs) ?? []
        self.returnValue = try c.decodeIfPresent(String.self, forKey: .returnValue)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(description, forKey: .description)
        try c.encode(source, forKey: .source)
        try c.encode(createdAt, forKey: .createdAt)
        try c.encode(changes, forKey: .changes)
        try c.encode(warnings, forKey: .warnings)
        try c.encode(logs, forKey: .logs)
        try c.encode(returnValue, forKey: .returnValue)
    }
}

/// Computes live record diffs between two snapshots.
public enum SnapshotDiff {
    public static func diff(before: AppSnapshot, after: AppSnapshot) -> [Change] {
        var out: [Change] = []

        func compare(_ oldList: [Record], _ newList: [Record]) {
            let oldMap = Dictionary(
                oldList.filter { $0.deletedAt == nil }.map { ($0.id, $0) },
                uniquingKeysWith: { _, latest in latest }
            )
            let newLive = newList.filter { $0.deletedAt == nil }
            let newMap = Dictionary(
                newLive.map { ($0.id, $0) },
                uniquingKeysWith: { _, latest in latest }
            )
            for rec in newLive {
                if let old = oldMap[rec.id] {
                    if !old.hasSameContent(as: rec) {
                        out.append(.modify(before: old, after: rec))
                    }
                } else {
                    out.append(.add(after: rec))
                }
            }
            for old in oldList where old.deletedAt == nil {
                if newMap[old.id] == nil {
                    out.append(.remove(before: old))
                }
            }
        }

        compare(before.semesters.map(Record.semester), after.semesters.map(Record.semester))
        compare(before.lists.map(Record.taskList), after.lists.map(Record.taskList))
        compare(before.courses.map(Record.course), after.courses.map(Record.course))
        compare(before.exceptions.map(Record.courseException), after.exceptions.map(Record.courseException))
        compare(before.events.map(Record.event), after.events.map(Record.event))
        compare(before.tasks.map(Record.task), after.tasks.map(Record.task))
        return out
    }
}
