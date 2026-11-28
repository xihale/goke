import Foundation

/// Persisted operation history entry matching Android `OpRow` in `MintDb.kt`.
public struct OpRow: Sendable, Hashable, Codable, Identifiable {
    public let id: String
    public let createdAt: Int64
    public let description: String
    public let source: String
    public let json: String
    public let undoneAt: Int64?

    public init(
        id: String,
        createdAt: Int64,
        description: String,
        source: String,
        json: String,
        undoneAt: Int64?
    ) {
        self.id = id
        self.createdAt = createdAt
        self.description = description
        self.source = source
        self.json = json
        self.undoneAt = undoneAt
    }

    public func decodedChangeSet() -> ChangeSet? {
        try? ChangeSet.fromJSON(json)
    }
}

/// Pure undo/redo state checker and `pendingPurged` tombstone planner.
public struct UndoHistory: Sendable, Hashable {
    public var ops: [OpRow]
    public var pendingPurged: [Record]

    public init(ops: [OpRow] = [], pendingPurged: [Record] = []) {
        self.ops = ops
        self.pendingPurged = pendingPurged
    }

    public func canUndo(_ op: OpRow, in snapshot: AppSnapshot) -> Bool {
        guard op.undoneAt == nil, let cs = op.decodedChangeSet() else { return false }
        return snapshot.canUndo(cs)
    }

    public func canRedo(_ op: OpRow, in snapshot: AppSnapshot) -> Bool {
        guard op.undoneAt != nil, let cs = op.decodedChangeSet() else { return false }
        return snapshot.canRedo(cs)
    }

    /// Computes tombstones for physically purged `.add` records when sync purge tracking is enabled.
    public static func purgeTombstones(forUndoing changeSet: ChangeSet, at timestamp: Int64, trackPurges: Bool) -> [Record] {
        guard trackPurges else { return [] }
        return changeSet.changes.compactMap { change in
            if case .add(let after) = change {
                return after.withMeta(updatedAt: timestamp, deletedAt: timestamp)
            }
            return nil
        }
    }

    /// Returns whether a remote record should be suppressed because a newer/equal local purged tombstone exists.
    public func isSuppressedByPendingPurge(_ remote: Record) -> Bool {
        guard let tombstone = pendingPurged.first(where: { $0.id == remote.id }) else { return false }
        return tombstone.updatedAt >= remote.updatedAt
    }
}
