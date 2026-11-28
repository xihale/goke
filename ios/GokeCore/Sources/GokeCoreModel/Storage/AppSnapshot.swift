import Foundation

/// Immutable point-in-time snapshot of all domain records (including tombstones until `.live()` is called).
public struct AppSnapshot: Sendable, Hashable, Codable {
    public var semesters: [Semester]
    public var courses: [Course]
    public var exceptions: [CourseException]
    public var events: [Event]
    public var tasks: [GokeTask]
    public var lists: [TaskList]

    public init(
        semesters: [Semester] = [],
        courses: [Course] = [],
        exceptions: [CourseException] = [],
        events: [Event] = [],
        tasks: [GokeTask] = [],
        lists: [TaskList] = []
    ) {
        self.semesters = semesters
        self.courses = courses
        self.exceptions = exceptions
        self.events = events
        self.tasks = tasks
        self.lists = lists
    }

    public var currentSemester: Semester? {
        semesters.first { $0.isCurrent && $0.deletedAt == nil }
            ?? semesters.first { $0.deletedAt == nil }
    }

    public var hasDeletedRecords: Bool {
        semesters.contains { $0.deletedAt != nil }
            || courses.contains { $0.deletedAt != nil }
            || exceptions.contains { $0.deletedAt != nil }
            || events.contains { $0.deletedAt != nil }
            || tasks.contains { $0.deletedAt != nil }
            || lists.contains { $0.deletedAt != nil }
    }

    /// Counts soft-deleted records across all 6 collections without allocating `[Record]`.
    public var deletedRecordCount: Int {
        var count = 0
        for v in semesters where v.deletedAt != nil { count += 1 }
        for v in courses where v.deletedAt != nil { count += 1 }
        for v in exceptions where v.deletedAt != nil { count += 1 }
        for v in events where v.deletedAt != nil { count += 1 }
        for v in tasks where v.deletedAt != nil { count += 1 }
        for v in lists where v.deletedAt != nil { count += 1 }
        return count
    }

    public func live() -> AppSnapshot {
        guard hasDeletedRecords else { return self }
        return AppSnapshot(
            semesters: semesters.filter { $0.deletedAt == nil },
            courses: courses.filter { $0.deletedAt == nil },
            exceptions: exceptions.filter { $0.deletedAt == nil },
            events: events.filter { $0.deletedAt == nil },
            tasks: tasks.filter { $0.deletedAt == nil },
            lists: lists.filter { $0.deletedAt == nil }
        )
    }

    public func allRecords() -> [Record] {
        var out: [Record] = []
        out.reserveCapacity(semesters.count + courses.count + exceptions.count + events.count + tasks.count + lists.count)
        out.append(contentsOf: semesters.map(Record.semester))
        out.append(contentsOf: courses.map(Record.course))
        out.append(contentsOf: exceptions.map(Record.courseException))
        out.append(contentsOf: events.map(Record.event))
        out.append(contentsOf: tasks.map(Record.task))
        out.append(contentsOf: lists.map(Record.taskList))
        return out
    }

    public func find(id: String) -> Record? {
        if let v = semesters.first(where: { $0.id == id }) { return .semester(v) }
        if let v = courses.first(where: { $0.id == id }) { return .course(v) }
        if let v = exceptions.first(where: { $0.id == id }) { return .courseException(v) }
        if let v = events.first(where: { $0.id == id }) { return .event(v) }
        if let v = tasks.first(where: { $0.id == id }) { return .task(v) }
        if let v = lists.first(where: { $0.id == id }) { return .taskList(v) }
        return nil
    }

    /// Upserts a record by `id`.
    public func put(_ record: Record) -> AppSnapshot {
        var copy = self
        switch record {
        case .semester(let v): copy.semesters = upsert(copy.semesters, item: v)
        case .course(let v): copy.courses = upsert(copy.courses, item: v)
        case .courseException(let v): copy.exceptions = upsert(copy.exceptions, item: v)
        case .event(let v): copy.events = upsert(copy.events, item: v)
        case .task(let v): copy.tasks = upsert(copy.tasks, item: v)
        case .taskList(let v): copy.lists = upsert(copy.lists, item: v)
        }
        return copy
    }

    /// Physically removes a record by `id` (used when undoing an `.add` or purging trash).
    public func purge(_ record: Record) -> AppSnapshot {
        var copy = self
        switch record {
        case .semester(let v): copy.semesters.removeAll { $0.id == v.id }
        case .course(let v): copy.courses.removeAll { $0.id == v.id }
        case .courseException(let v): copy.exceptions.removeAll { $0.id == v.id }
        case .event(let v): copy.events.removeAll { $0.id == v.id }
        case .task(let v): copy.tasks.removeAll { $0.id == v.id }
        case .taskList(let v): copy.lists.removeAll { $0.id == v.id }
        }
        return copy
    }

    /// Upserts a batch of records in O(N + M) using per-collection index dictionaries.
    public func putBatch(_ records: [Record]) -> AppSnapshot {
        guard !records.isEmpty else { return self }
        if records.count == 1 {
            return put(records[0])
        }
        var copy = self
        var semUpdates: [Semester] = []
        var courseUpdates: [Course] = []
        var excUpdates: [CourseException] = []
        var eventUpdates: [Event] = []
        var taskUpdates: [GokeTask] = []
        var listUpdates: [TaskList] = []

        for r in records {
            switch r {
            case .semester(let v): semUpdates.append(v)
            case .course(let v): courseUpdates.append(v)
            case .courseException(let v): excUpdates.append(v)
            case .event(let v): eventUpdates.append(v)
            case .task(let v): taskUpdates.append(v)
            case .taskList(let v): listUpdates.append(v)
            }
        }

        if !semUpdates.isEmpty { copy.semesters = upsertBatch(copy.semesters, items: semUpdates) }
        if !courseUpdates.isEmpty { copy.courses = upsertBatch(copy.courses, items: courseUpdates) }
        if !excUpdates.isEmpty { copy.exceptions = upsertBatch(copy.exceptions, items: excUpdates) }
        if !eventUpdates.isEmpty { copy.events = upsertBatch(copy.events, items: eventUpdates) }
        if !taskUpdates.isEmpty { copy.tasks = upsertBatch(copy.tasks, items: taskUpdates) }
        if !listUpdates.isEmpty { copy.lists = upsertBatch(copy.lists, items: listUpdates) }
        return copy
    }

    /// Physically removes records matching any id in `ids` in O(N).
    public func purgeIds(_ ids: Set<String>) -> AppSnapshot {
        guard !ids.isEmpty else { return self }
        var copy = self
        copy.semesters.removeAll { ids.contains($0.id) }
        copy.courses.removeAll { ids.contains($0.id) }
        copy.exceptions.removeAll { ids.contains($0.id) }
        copy.events.removeAll { ids.contains($0.id) }
        copy.tasks.removeAll { ids.contains($0.id) }
        copy.lists.removeAll { ids.contains($0.id) }
        return copy
    }

    /// Applies `changeSet` with timestamp `at` and returns both the new snapshot and the deduplicated stamped records to upsert.
    internal func applyingWithUpserts(
        _ changeSet: ChangeSet,
        at timestamp: Int64? = nil
    ) -> (snapshot: AppSnapshot, upserts: [Record]) {
        guard !changeSet.changes.isEmpty else { return (self, []) }
        let at = timestamp ?? changeSet.createdAt
        var orderedIds: [String] = []
        orderedIds.reserveCapacity(changeSet.changes.count)
        var latestById: [String: Record] = [:]
        latestById.reserveCapacity(changeSet.changes.count)

        for change in changeSet.changes {
            let stamped: Record
            switch change {
            case .add(let after):
                stamped = after.withMeta(updatedAt: at, deletedAt: nil)
            case .modify(_, let after):
                stamped = after.withMeta(updatedAt: at, deletedAt: after.deletedAt)
            case .remove(let before):
                stamped = before.withMeta(updatedAt: at, deletedAt: at)
            }
            if latestById[stamped.id] == nil {
                orderedIds.append(stamped.id)
            }
            latestById[stamped.id] = stamped
        }

        let upserts = orderedIds.compactMap { latestById[$0] }
        return (putBatch(upserts), upserts)
    }

    /// Applies `changeSet` with timestamp `at` (defaults to `changeSet.createdAt`).
    public func applying(_ changeSet: ChangeSet, at timestamp: Int64? = nil) -> AppSnapshot {
        applyingWithUpserts(changeSet, at: timestamp).snapshot
    }

    /// Undoes `changeSet` in reverse order and returns the updated snapshot plus exact upserts and purged ids.
    internal func undoingWithMutations(
        _ changeSet: ChangeSet,
        at timestamp: Int64
    ) -> (snapshot: AppSnapshot, upserts: [Record], deleteIds: [String]) {
        guard !changeSet.changes.isEmpty else { return (self, [], []) }
        var purgedIds = Set<String>()
        var orderedUpsertIds: [String] = []
        var latestUpserts: [String: Record] = [:]

        for change in changeSet.changes.reversed() {
            switch change {
            case .add(let after):
                purgedIds.insert(after.id)
                latestUpserts.removeValue(forKey: after.id)
            case .modify(let before, _):
                purgedIds.remove(before.id)
                if latestUpserts[before.id] == nil {
                    orderedUpsertIds.append(before.id)
                }
                latestUpserts[before.id] = before.withMeta(updatedAt: timestamp, deletedAt: before.deletedAt)
            case .remove(let before):
                purgedIds.remove(before.id)
                if latestUpserts[before.id] == nil {
                    orderedUpsertIds.append(before.id)
                }
                latestUpserts[before.id] = before.withMeta(updatedAt: timestamp, deletedAt: nil)
            }
        }

        let deleteIds = changeSet.changes.compactMap { change -> String? in
            if case .add(let after) = change, purgedIds.contains(after.id) { return after.id }
            return nil
        }
        let upserts = orderedUpsertIds.compactMap { id -> Record? in
            guard !purgedIds.contains(id) else { return nil }
            return latestUpserts[id]
        }
        let next = purgeIds(purgedIds).putBatch(upserts)
        return (next, upserts, deleteIds)
    }

    /// Undoes `changeSet` in reverse order: `.add` is purged, `.modify` restores `before`, `.remove` clears `deletedAt`.
    public func undoing(_ changeSet: ChangeSet, at timestamp: Int64) -> AppSnapshot {
        undoingWithMutations(changeSet, at: timestamp).snapshot
    }

    /// Returns true iff every record touched by `changeSet` still matches its post-apply state (ignoring `updatedAt`/`deletedAt` content).
    public func canUndo(_ changeSet: ChangeSet) -> Bool {
        guard !changeSet.isEmpty else { return false }
        var grouped: [String: [Change]] = [:]
        for change in changeSet.changes {
            grouped[change.record.id, default: []].append(change)
        }
        return grouped.values.allSatisfy { list in
            guard let last = list.last else { return false }
            let cur = find(id: last.record.id)
            switch last {
            case .add(let after):
                return cur != nil && cur?.deletedAt == nil && cur!.hasSameContent(as: after)
            case .modify(_, let after):
                return cur != nil && cur?.deletedAt == after.deletedAt && cur!.hasSameContent(as: after)
            case .remove:
                return cur != nil && cur?.deletedAt != nil
            }
        }
    }

    /// Returns true iff every record touched by `changeSet` still matches its pre-apply state.
    public func canRedo(_ changeSet: ChangeSet) -> Bool {
        guard !changeSet.isEmpty else { return false }
        var grouped: [String: [Change]] = [:]
        for change in changeSet.changes {
            grouped[change.record.id, default: []].append(change)
        }
        return grouped.values.allSatisfy { list in
            guard let first = list.first else { return false }
            let cur = find(id: first.record.id)
            switch first {
            case .add:
                return cur == nil
            case .modify(let before, _):
                return cur != nil && cur?.deletedAt == before.deletedAt && cur!.hasSameContent(as: before)
            case .remove(let before):
                return cur != nil && cur?.deletedAt == nil && cur!.hasSameContent(as: before)
            }
        }
    }

    private func upsert<T: Identifiable>(_ list: [T], item: T) -> [T] where T.ID == String {
        var copy = list
        if let idx = copy.firstIndex(where: { $0.id == item.id }) {
            copy[idx] = item
        } else {
            copy.append(item)
        }
        return copy
    }

    private func upsertBatch<T: Identifiable>(_ list: [T], items: [T]) -> [T] where T.ID == String {
        if items.count == 1 {
            return upsert(list, item: items[0])
        }
        var copy = list
        copy.reserveCapacity(list.count + items.count)
        var indexById: [String: Int] = [:]
        indexById.reserveCapacity(list.count + items.count)
        for (i, existing) in copy.enumerated() {
            indexById[existing.id] = i
        }
        for item in items {
            if let idx = indexById[item.id] {
                copy[idx] = item
            } else {
                indexById[item.id] = copy.count
                copy.append(item)
            }
        }
        return copy
    }

    private enum CodingKeys: String, CodingKey {
        case semesters, courses, exceptions, events, tasks, lists
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.semesters = try c.decodeIfPresent([Semester].self, forKey: .semesters) ?? []
        self.courses = try c.decodeIfPresent([Course].self, forKey: .courses) ?? []
        self.exceptions = try c.decodeIfPresent([CourseException].self, forKey: .exceptions) ?? []
        self.events = try c.decodeIfPresent([Event].self, forKey: .events) ?? []
        self.tasks = try c.decodeIfPresent([GokeTask].self, forKey: .tasks) ?? []
        self.lists = try c.decodeIfPresent([TaskList].self, forKey: .lists) ?? []
    }
}

public typealias Snapshot = AppSnapshot
