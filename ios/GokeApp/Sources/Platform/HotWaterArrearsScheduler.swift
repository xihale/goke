import Foundation
import GokeCore
#if canImport(BackgroundTasks)
import BackgroundTasks
#endif

/// Schedules periodic 6-hour hot water arrears checks (`BGAppRefreshTask` + foreground resume fallback)
/// and creates deduplicated arrears recharge `GokeTask` reminders in `RepositoryActor`.
public enum HotWaterArrearsScheduler {
    public static let backgroundTaskIdentifier = "app.goke.ios.hotwater.arrears"
    public static let checkIntervalSeconds: TimeInterval = 6 * 60 * 60
    public static let extraKindKey = "hotwater_kind"
    public static let extraKindArrears = "arrears"

    private static let dedupActor = ArrearsTodoActor()

    /// Registers `app.goke.ios.hotwater.arrears` with `BGTaskScheduler`.
    /// Call once during app launch alongside other background task registrations.
    @MainActor
    public static func registerBackgroundTask(
        client: HotWaterClient = .shared,
        repository: RepositoryActor
    ) {
        #if canImport(BackgroundTasks) && os(iOS)
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: backgroundTaskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            let box = BGTaskBox(task: refreshTask)
            let work = Task { @MainActor in
                await client.ensureHydrated()
                let snap = client.state
                guard snap.isBound, snap.config.arrearsTodoEnabled else {
                    scheduleNextRefresh(enabled: false)
                    return true
                }
                scheduleNextRefresh(enabled: true)
                let res = await client.refreshStatusAndCheckArrears(probeBle: false, repository: repository)
                return res.ok || res.errorCode != .networkError
            }
            refreshTask.expirationHandler = {
                work.cancel()
            }
            Task {
                let success = await work.value
                box.task.setTaskCompleted(success: success)
            }
        }
        #endif
    }

    /// Submits or cancels the next `BGAppRefreshTaskRequest` (earliest begin date +6h).
    public static func scheduleNextRefresh(enabled: Bool) {
        #if canImport(BackgroundTasks) && os(iOS)
        if enabled {
            let request = BGAppRefreshTaskRequest(identifier: backgroundTaskIdentifier)
            request.earliestBeginDate = Date(timeIntervalSinceNow: checkIntervalSeconds)
            try? BGTaskScheduler.shared.submit(request)
        } else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: backgroundTaskIdentifier)
        }
        #endif
    }

    /// Foreground fallback check: runs when bound, `arrearsTodoEnabled == true`, and `lastCheckAt` is older than 6h.
    @MainActor
    public static func checkForegroundIfDue(
        client: HotWaterClient = .shared,
        repository: RepositoryActor
    ) async {
        await client.ensureHydrated()
        let snap = client.state
        guard snap.isBound, snap.config.arrearsTodoEnabled else { return }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let intervalMs = Int64(checkIntervalSeconds * 1000)
        if snap.lastCheckAt > 0, (nowMs - snap.lastCheckAt) < intervalMs {
            return
        }
        _ = await client.refreshStatusAndCheckArrears(probeBle: false, repository: repository)
    }

    /// Creates a deduplicated arrears recharge `GokeTask` (`priority = 2`, `extra["hotwater_kind"] == "arrears"`)
    /// via `RepositoryActor.commit(ChangeSet)` if no pending arrears task already exists.
    public static func ensureArrearsTodo(
        balanceYuan: Double,
        repository: RepositoryActor,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) async {
        await dedupActor.ensureArrearsTodo(
            balanceYuan: balanceYuan,
            repository: repository,
            nowMillis: nowMillis
        )
    }
}

#if canImport(BackgroundTasks) && os(iOS)
private struct BGTaskBox: @unchecked Sendable {
    let task: BGAppRefreshTask
}
#endif

private actor ArrearsTodoActor {
    func ensureArrearsTodo(
        balanceYuan: Double,
        repository: RepositoryActor,
        nowMillis: Int64
    ) async {
        let snap = await repository.snapshot
        let hasPending = snap.tasks.contains { task in
            task.deletedAt == nil
                && !task.done
                && !task.dropped
                && task.extra[HotWaterArrearsScheduler.extraKindKey] == HotWaterArrearsScheduler.extraKindArrears
        }
        guard !hasPending else { return }

        let formatted = String(format: "%.2f", balanceYuan)
        let task = GokeTask(
            id: UUID().uuidString.lowercased(),
            title: "热水余额不足，请及时充值",
            note: "当前智慧热水可用余额 ¥\(formatted)，已低于提醒阈值。",
            priority: 2,
            source: .MANUAL,
            extra: [
                HotWaterArrearsScheduler.extraKindKey: HotWaterArrearsScheduler.extraKindArrears,
                "balance_yuan": formatted,
            ],
            updatedAt: nowMillis
        )
        let cs = ChangeSet(
            description: "自动创建热水充值待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.add(after: .task(task))]
        )
        _ = try? await repository.commit(cs, record: true)
    }
}
