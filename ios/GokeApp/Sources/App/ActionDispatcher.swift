import Foundation
import GokeCore
import Observation
import GokeShared

/// Transient floating toast / undo pill state displayed for 4.5 seconds above the root tab bar.
struct UndoToastState: Identifiable, Equatable, Sendable {
    let id: UUID
    let message: String
    let opId: String?
    let canUndo: Bool
    let actionTitle: String?
    let createdAt: Date

    init(
        id: UUID = UUID(),
        message: String,
        opId: String? = nil,
        canUndo: Bool = false,
        actionTitle: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.message = message
        self.opId = opId
        self.canUndo = canUndo
        self.actionTitle = actionTitle
        self.createdAt = createdAt
    }
}

/// Single write entry point mirroring Android's `LocalActions` (`Actions.commit(ChangeSet)`).
///
/// Every user mutation flows through `ActionDispatcher` so that SQLite WAL persistence, undo/redo oplog,
/// WidgetKit snapshot publishing, rolling local notifications, Live Activity reconciliation, and
/// debounced cloud sync remain strictly consistent.
@MainActor
@Observable
final class ActionDispatcher {
    static let toastDuration: Duration = .seconds(4.5)
    static let sideEffectDebounceDelay: Duration = .milliseconds(300)

    private(set) var toast: UndoToastState?
    private(set) var lastCommittedOpId: String?
    private(set) var lastUndoneOpId: String?
    private(set) var isBusy: Bool = false
    private(set) var lastErrorMessage: String?

    @ObservationIgnored private let repository: RepositoryActor
    @ObservationIgnored private let settingsStore: AppSettingsStore
    @ObservationIgnored private let appGroup: AppGroupContainer
    @ObservationIgnored private let reminderScheduler: ReminderScheduler
    @ObservationIgnored private let liveActivityManager: LiveActivityManager

    @ObservationIgnored var onSnapshotChanged: (@MainActor (AppSnapshot) -> Void)?
    @ObservationIgnored var onRequestDebouncedSync: (@MainActor () -> Void)?

    @ObservationIgnored private nonisolated(unsafe) var toastDismissTask: Task<Void, Never>?
    @ObservationIgnored private nonisolated(unsafe) var sideEffectTask: Task<Void, Never>?

    init(
        repository: RepositoryActor,
        settingsStore: AppSettingsStore,
        appGroup: AppGroupContainer = .shared,
        reminderScheduler: ReminderScheduler = .shared,
        liveActivityManager: LiveActivityManager = .shared,
        onSnapshotChanged: (@MainActor (AppSnapshot) -> Void)? = nil,
        onRequestDebouncedSync: (@MainActor () -> Void)? = nil
    ) {
        self.repository = repository
        self.settingsStore = settingsStore
        self.appGroup = appGroup
        self.reminderScheduler = reminderScheduler
        self.liveActivityManager = liveActivityManager
        self.onSnapshotChanged = onSnapshotChanged
        self.onRequestDebouncedSync = onRequestDebouncedSync
    }

    deinit {
        toastDismissTask?.cancel()
        sideEffectTask?.cancel()
    }

    // MARK: - Core Write Pipeline

    /// Commits a `ChangeSet` atomically via `RepositoryActor`, publishes derived side effects,
    /// triggers debounced cloud sync, and presents a 4.5-second `UndoToastState`.
    @discardableResult
    func commit(
        _ changeSet: ChangeSet,
        label: String? = nil,
        record: Bool = true,
        undoable: Bool = true,
        showToast: Bool = true
    ) async -> Bool {
        guard !changeSet.isEmpty else { return false }

        isBusy = true
        lastErrorMessage = nil
        defer { isBusy = false }

        do {
            let committed = try await repository.commit(changeSet, record: record)
            let updatedSnapshot = await repository.snapshot
            onSnapshotChanged?(updatedSnapshot)

            let canUndoOp = record && undoable
            if record {
                lastCommittedOpId = committed.id
                lastUndoneOpId = nil
            }

            scheduleSideEffects(snapshot: updatedSnapshot, triggerSync: true)

            if showToast {
                let message = Self.resolvedLabel(label, fallback: committed.description)
                presentToast(
                    UndoToastState(
                        message: message,
                        opId: canUndoOp ? committed.id : nil,
                        canUndo: canUndoOp,
                        actionTitle: canUndoOp ? "撤销" : nil
                    )
                )
            }
            return true
        } catch {
            lastErrorMessage = error.localizedDescription
            presentToast(
                UndoToastState(
                    message: "保存失败：\(error.localizedDescription)",
                    opId: nil,
                    canUndo: false,
                    actionTitle: nil
                )
            )
            return false
        }
    }

    /// Undoes the specified operation (or the currently active undo toast / most recent committed operation).
    @discardableResult
    func undo(opId: String? = nil) async -> Bool {
        guard let targetId = opId ?? toast?.opId ?? lastCommittedOpId else {
            return false
        }

        isBusy = true
        lastErrorMessage = nil
        defer { isBusy = false }

        do {
            let succeeded = try await repository.undo(opId: targetId)
            guard succeeded else {
                presentToast(
                    UndoToastState(message: "无法撤销：相关记录已被后续修改")
                )
                return false
            }

            let updatedSnapshot = await repository.snapshot
            onSnapshotChanged?(updatedSnapshot)
            lastUndoneOpId = targetId
            if lastCommittedOpId == targetId {
                lastCommittedOpId = nil
            }

            scheduleSideEffects(snapshot: updatedSnapshot, triggerSync: true)
            presentToast(UndoToastState(message: "已撤销"))
            return true
        } catch {
            lastErrorMessage = error.localizedDescription
            presentToast(UndoToastState(message: "撤销失败：\(error.localizedDescription)"))
            return false
        }
    }

    /// Redoes the specified previously undone operation (or the most recently undone operation).
    @discardableResult
    func redo(opId: String? = nil) async -> Bool {
        guard let targetId = opId ?? lastUndoneOpId else {
            return false
        }

        isBusy = true
        lastErrorMessage = nil
        defer { isBusy = false }

        do {
            let succeeded = try await repository.redo(opId: targetId)
            guard succeeded else {
                presentToast(
                    UndoToastState(message: "无法重做：相关记录状态已变化")
                )
                return false
            }

            let updatedSnapshot = await repository.snapshot
            onSnapshotChanged?(updatedSnapshot)
            lastCommittedOpId = targetId
            if lastUndoneOpId == targetId {
                lastUndoneOpId = nil
            }

            scheduleSideEffects(snapshot: updatedSnapshot, triggerSync: true)
            presentToast(
                UndoToastState(
                    message: "已重做",
                    opId: targetId,
                    canUndo: true,
                    actionTitle: "撤销"
                )
            )
            return true
        } catch {
            lastErrorMessage = error.localizedDescription
            presentToast(UndoToastState(message: "重做失败：\(error.localizedDescription)"))
            return false
        }
    }

    // MARK: - Convenience Domain Actions

    /// Marks a `GokeTask` completed via the unified `ChangeSet` pipeline (used by notification actions & UI).
    /// Delegates to `TaskLogic.complete` so repeating tasks spawn their next occurrence.
    @discardableResult
    func completeTask(id: String) async -> Bool {
        let current = await repository.snapshot
        guard let task = current.tasks.first(where: { $0.id == id && $0.deletedAt == nil }), !task.done else {
            return false
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let result = TaskLogic.complete(task, nowMillis: nowMillis, now: .now())
        var changes: [Change] = [.modify(before: .task(task), after: .task(result.finished))]
        if let next = result.next {
            changes.append(.add(after: .task(next)))
        }

        let cs = ChangeSet(
            description: "完成「\(task.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        return await commit(cs, label: "已完成「\(task.title)」")
    }

    /// Deletes a semester and cascades removal to all courses and course exceptions belonging to it.
    /// If the deleted semester was active, promotes the latest remaining semester to current.
    @discardableResult
    func deleteSemester(id: String) async -> Bool {
        let live = (await repository.snapshot).live()
        guard let target = live.semesters.first(where: { $0.id == id }) else {
            return false
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = [.remove(before: .semester(target))]
        let targetCourses = live.courses.filter { $0.semesterId == target.id }
        let targetCourseIds = Set(targetCourses.map(\.id))
        for course in targetCourses {
            changes.append(.remove(before: .course(course)))
        }
        for exception in live.exceptions where targetCourseIds.contains(exception.courseId) {
            changes.append(.remove(before: .courseException(exception)))
        }
        if target.isCurrent,
           let nextCurrent = live.semesters
               .filter({ $0.id != target.id })
               .max(by: { $0.week1Monday < $1.week1Monday }),
           !nextCurrent.isCurrent {
            var promoted = nextCurrent
            promoted.isCurrent = true
            promoted.updatedAt = nowMillis
            changes.append(.modify(before: .semester(nextCurrent), after: .semester(promoted)))
        }

        let desc = "已删除学期「\(target.name)」"
        let cs = ChangeSet(
            description: desc,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        return await commit(cs, label: desc)
    }

    /// Convenience overload accepting a `Semester` value directly.
    @discardableResult
    func deleteSemester(_ semester: Semester) async -> Bool {
        await deleteSemester(id: semester.id)
    }

    // MARK: - Toast & Side Effects

    /// Displays an informational (non-undoable) toast message for `duration` (default 4.5s).
    func showToast(
        message: String,
        duration: Duration = ActionDispatcher.toastDuration
    ) {
        presentToast(UndoToastState(message: message), duration: duration)
    }

    /// Dismisses the currently visible toast immediately.
    func dismissToast() {
        toastDismissTask?.cancel()
        toastDismissTask = nil
        toast = nil
    }

    /// Refreshes WidgetKit snapshot, local reminders, and Live Activity after non-commit updates (e.g. remote sync or settings).
    func refreshSideEffects(
        snapshot: AppSnapshot,
        triggerSync: Bool = false,
        debounce: Duration = ActionDispatcher.sideEffectDebounceDelay
    ) {
        scheduleSideEffects(snapshot: snapshot, triggerSync: triggerSync, debounce: debounce)
    }

    private func presentToast(
        _ next: UndoToastState,
        duration: Duration = ActionDispatcher.toastDuration
    ) {
        toastDismissTask?.cancel()
        toast = next
        let toastId = next.id
        toastDismissTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: duration)
            } catch {
                return
            }
            guard let self, self.toast?.id == toastId else { return }
            self.toast = nil
        }
    }

    private func scheduleSideEffects(
        snapshot: AppSnapshot,
        triggerSync: Bool,
        debounce: Duration = ActionDispatcher.sideEffectDebounceDelay
    ) {
        let settings = settingsStore.settings
        let appGroup = self.appGroup
        let reminderScheduler = self.reminderScheduler
        let liveActivityManager = self.liveActivityManager

        if triggerSync {
            onRequestDebouncedSync?()
        }

        sideEffectTask?.cancel()
        sideEffectTask = Task.detached(priority: .utility) {
            if debounce > .zero {
                do {
                    try await Task.sleep(for: debounce)
                } catch {
                    return
                }
            }
            guard !Task.isCancelled else { return }
            try? appGroup.writeWidgetSnapshot(snapshot, settings: settings, reloadTimelines: true)
            guard !Task.isCancelled else { return }
            _ = await reminderScheduler.reschedule(snapshot: snapshot, settings: settings)
            guard !Task.isCancelled else { return }
            await liveActivityManager.sync(snapshot: snapshot)
        }
    }

    private static func resolvedLabel(_ label: String?, fallback: String) -> String {
        if let label {
            let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { return trimmed }
        }
        let fallbackTrimmed = fallback.trimmingCharacters(in: .whitespacesAndNewlines)
        return fallbackTrimmed.isEmpty ? "已保存更改" : fallbackTrimmed
    }
}
