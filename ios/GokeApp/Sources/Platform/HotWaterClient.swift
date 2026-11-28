import Foundation
import GokeAdapterLoader
import GokeCore
import GokeShared
import Observation
#if canImport(UserNotifications)
import UserNotifications
#endif

// MARK: - Keychain & AppGroup Persistence

/// Persists hot water secrets (`userId`, `uuid`) in Keychain and non-sensitive config/cache in `AppGroupContainer.userDefaults`.
public final class HotWaterStore: Sendable {
    public static let userIdKeychainKey: KeychainKey = .custom("campus.hotwater.userId")
    public static let uuidKeychainKey: KeychainKey = .custom("campus.hotwater.uuid")

    private static let keyConfig = "app.goke.ios.hotwater.config.v1"
    private static let keyIsArrearage = "app.goke.ios.hotwater.isArrearage.v1"
    private static let keyArrearsYuan = "app.goke.ios.hotwater.arrearsYuan.v1"
    private static let keyCanUseAmount = "app.goke.ios.hotwater.canUseAmount.v1"
    private static let keyLastCheckAt = "app.goke.ios.hotwater.lastCheckAt.v1"
    private static let keyValveOpen = "app.goke.ios.hotwater.valveOpen.v1"
    private static let keyValveOpenedAt = "app.goke.ios.hotwater.valveOpenedAt.v1"
    private static let keyShowerRecords = "app.goke.ios.hotwater.showerRecords.v1"
    private static let maxShowerRecords = 100

    private let keychain: KeychainStore
    private let appGroup: AppGroupContainer

    public init(
        keychain: KeychainStore = .shared,
        appGroup: AppGroupContainer = .shared
    ) {
        self.keychain = keychain
        self.appGroup = appGroup
    }

    public func userId() async -> String? {
        guard let raw = (try? await keychain.get(Self.userIdKeychainKey)) ?? nil else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    public func saveCredentials(userId: String, uuid: String) async {
        try? await keychain.set(userId, for: Self.userIdKeychainKey)
        if !uuid.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            try? await keychain.set(uuid, for: Self.uuidKeychainKey)
        }
    }

    public func ensureUuid() async -> String {
        if let existing = (try? await keychain.get(Self.uuidKeychainKey)) ?? nil,
           !existing.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        {
            return existing
        }
        let generated = UUID().uuidString.lowercased()
        try? await keychain.set(generated, for: Self.uuidKeychainKey)
        return generated
    }

    public func readConfig() -> HotWaterConfig {
        guard let data = appGroup.userDefaults.data(forKey: Self.keyConfig),
              let decoded = try? JSONDecoder().decode(HotWaterConfig.self, from: data)
        else {
            return HotWaterConfig()
        }
        return decoded
    }

    public func writeConfig(_ config: HotWaterConfig) {
        if let data = try? JSONEncoder().encode(config) {
            appGroup.userDefaults.set(data, forKey: Self.keyConfig)
        }
    }

    public func readIsArrearage() -> Bool {
        appGroup.userDefaults.bool(forKey: Self.keyIsArrearage)
    }

    public func readArrearsYuan() -> Double {
        appGroup.userDefaults.double(forKey: Self.keyArrearsYuan)
    }

    public func readCanUseAmount() -> Int {
        appGroup.userDefaults.integer(forKey: Self.keyCanUseAmount)
    }

    public func readLastCheckAt() -> Int64 {
        Int64(appGroup.userDefaults.object(forKey: Self.keyLastCheckAt) as? Int64 ?? 0)
    }

    public func writeArrearsSnapshot(
        isArrearage: Bool,
        arrearsYuan: Double,
        canUseAmount: Int,
        checkedAt: Int64
    ) {
        let ud = appGroup.userDefaults
        ud.set(isArrearage, forKey: Self.keyIsArrearage)
        ud.set(arrearsYuan, forKey: Self.keyArrearsYuan)
        ud.set(canUseAmount, forKey: Self.keyCanUseAmount)
        ud.set(checkedAt, forKey: Self.keyLastCheckAt)
    }

    public func readValveOpen() -> Bool {
        appGroup.userDefaults.bool(forKey: Self.keyValveOpen)
    }

    public func readValveOpenedAt() -> Int64 {
        Int64(appGroup.userDefaults.object(forKey: Self.keyValveOpenedAt) as? Int64 ?? 0)
    }

    public func writeValveOpen(_ open: Bool, openedAt: Int64) {
        let ud = appGroup.userDefaults
        ud.set(open, forKey: Self.keyValveOpen)
        ud.set(openedAt, forKey: Self.keyValveOpenedAt)
    }

    public func readShowerRecords() -> [HotWaterShowerRecord] {
        guard let data = appGroup.userDefaults.data(forKey: Self.keyShowerRecords),
              let decoded = try? JSONDecoder().decode([HotWaterShowerRecord].self, from: data)
        else {
            return []
        }
        return decoded
    }

    public func appendShowerRecord(_ record: HotWaterShowerRecord) {
        let existing = readShowerRecords().filter { $0.startTime != record.startTime }
        let updated = Array(([record] + existing).prefix(Self.maxShowerRecords))
        if let data = try? JSONEncoder().encode(updated) {
            appGroup.userDefaults.set(data, forKey: Self.keyShowerRecords)
        }
    }

    public func syncWidgetSnapshot(from state: HotWaterState) {
        appGroup.writeHotWaterWidgetSnapshot(HotWaterWidgetSnapshot(from: state), reloadTimelines: true)
    }

    /// Lightweight widget-snapshot synchronization directly from Keychain + persisted config/cache,
    /// without instantiating `HotWaterClient` or `CoreBluetooth`. Ensures `HotWaterWidget` always sees
    /// `isBound = true` on app launch, foreground, background, and manual snapshot refresh even if the
    /// user bound Hot Water before `hotwater-widget.json` existed or never reopened `HotWaterView`.
    public func syncWidgetSnapshotFromPersistence(reloadTimelines: Bool = true) async {
        let bound = await userId() != nil
        guard bound else {
            let existing = appGroup.readHotWaterWidgetSnapshot()
            if existing.isBound {
                appGroup.clearHotWaterWidgetSnapshot(reloadTimelines: reloadTimelines)
            }
            return
        }
        let cfg = readConfig()
        let phase: HotWaterPhase = readValveOpen() ? .valveOpen : .idle
        let snapshot = HotWaterWidgetSnapshot(
            isBound: true,
            dormLabel: cfg.dormLabel,
            canUseAmount: readCanUseAmount(),
            isArrearage: readIsArrearage(),
            phase: phase,
            lastCheckAt: readLastCheckAt(),
            lastActionAt: readValveOpenedAt()
        )
        appGroup.writeHotWaterWidgetSnapshot(snapshot, reloadTimelines: reloadTimelines)
    }

    public func clearAll() async {
        try? await keychain.delete(Self.userIdKeychainKey)
        try? await keychain.delete(Self.uuidKeychainKey)
        let ud = appGroup.userDefaults
        for key in [
            Self.keyConfig,
            Self.keyIsArrearage,
            Self.keyArrearsYuan,
            Self.keyCanUseAmount,
            Self.keyLastCheckAt,
            Self.keyValveOpen,
            Self.keyValveOpenedAt,
            Self.keyShowerRecords,
        ] {
            ud.removeObject(forKey: key)
        }
        appGroup.clearHotWaterWidgetSnapshot(reloadTimelines: true)
    }
}

// MARK: - HotWaterClient (@MainActor @Observable Coordinator)

/// Main-actor observable state machine coordinating HTTPS account binding, balance/bill fetching,
/// BLE valve control, shower summary notifications, arrears auto-todo, and WidgetKit snapshot sync.
@MainActor
@Observable
public final class HotWaterClient {
    public static let shared = HotWaterClient()

    public private(set) var state: HotWaterState = HotWaterState()

    public let backend: any HotWaterAdapterBackend
    public let store: HotWaterStore

    private var hydrated = false
    private var bindInFlight = false
    private var bleInFlight = false

    public var isSupported: Bool {
        backend.isSupported
    }

    public var bluetoothAuthorizationStatus: HotWaterBleAuthStatus {
        backend.currentBleAuthorizationStatus()
    }

    public init(
        backend: (any HotWaterAdapterBackend)? = nil,
        store: HotWaterStore = HotWaterStore()
    ) {
        self.backend = backend ?? CampusAdapterLoader.makeFactory().makeHotWaterBackend()
        self.store = store
    }

    /// Idempotently loads persisted credentials, meter metadata, balance, and valve state.
    public func ensureHydrated() async {
        if hydrated {
            if state.isBound {
                store.syncWidgetSnapshot(from: state)
            }
            return
        }
        let userId = await store.userId()
        let config = store.readConfig()
        let bound = userId != nil
        let phase: HotWaterPhase
        if !bound {
            phase = .unbound
        } else if store.readValveOpen() {
            phase = .valveOpen
        } else {
            phase = .idle
        }
        state = HotWaterState(
            isBound: bound,
            userIdMasked: userId.map(Self.maskUserId) ?? "",
            config: config,
            phase: phase,
            isArrearage: store.readIsArrearage(),
            arrearsYuan: store.readArrearsYuan(),
            canUseAmount: store.readCanUseAmount(),
            lastCheckAt: store.readLastCheckAt()
        )
        hydrated = true
        store.syncWidgetSnapshot(from: state)
    }

    // MARK: - Account Binding

    @discardableResult
    public func sendSmsCode(phone: String) async -> HotWaterOpResult {
        let trimmed = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            state.errorCode = .authFailed
            state.errorDetail = "empty_phone"
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .authFailed, detail: "empty_phone")
        }
        guard !bindInFlight else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .busy)
        }
        bindInFlight = true
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""
        defer {
            bindInFlight = false
            state.busy = false
        }

        switch await backend.sendSmsCode(phone: trimmed) {
        case .ok:
            return HotWaterOpResult(ok: true, phase: state.phase)
        case .err(let code, let detail):
            state.errorCode = code
            state.errorDetail = detail
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: code, detail: detail)
        }
    }

    @discardableResult
    public func loginWithPassword(
        phone: String,
        password: String,
        repository: RepositoryActor? = nil
    ) async -> HotWaterOpResult {
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPhone.isEmpty, !password.isEmpty else {
            state.errorCode = .authFailed
            state.errorDetail = "empty_credentials"
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .authFailed, detail: "empty_credentials")
        }
        guard !bindInFlight else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .busy)
        }
        bindInFlight = true
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""
        defer { bindInFlight = false }

        let uuid = await store.ensureUuid()
        switch await backend.loginWithPassword(phone: trimmedPhone, password: password, uuid: uuid) {
        case .err(let code, let detail):
            state.busy = false
            state.errorCode = code
            state.errorDetail = detail
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: code, detail: detail)
        case .ok(let data):
            return await completeBind(userId: data.userId, uuid: uuid, phone: data.phone, repository: repository)
        }
    }

    @discardableResult
    public func loginWithSms(
        phone: String,
        code: String,
        repository: RepositoryActor? = nil
    ) async -> HotWaterOpResult {
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPhone.isEmpty, !trimmedCode.isEmpty else {
            state.errorCode = .authFailed
            state.errorDetail = "empty_sms_code"
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .authFailed, detail: "empty_sms_code")
        }
        guard !bindInFlight else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .busy)
        }
        bindInFlight = true
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""
        defer { bindInFlight = false }

        let uuid = await store.ensureUuid()
        switch await backend.loginWithSms(phone: trimmedPhone, code: trimmedCode, uuid: uuid) {
        case .err(let code, let detail):
            state.busy = false
            state.errorCode = code
            state.errorDetail = detail
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: code, detail: detail)
        case .ok(let data):
            return await completeBind(userId: data.userId, uuid: uuid, phone: data.phone, repository: repository)
        }
    }

    @discardableResult
    public func bindWithUserId(
        _ userId: String,
        uuid: String = "",
        repository: RepositoryActor? = nil
    ) async -> HotWaterOpResult {
        let trimmedId = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedId.isEmpty else {
            state.errorCode = .authFailed
            state.errorDetail = "empty_user_id"
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .authFailed, detail: "empty_user_id")
        }
        guard !bindInFlight else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .busy)
        }
        bindInFlight = true
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""
        defer { bindInFlight = false }

        let trimmedUuid = uuid.trimmingCharacters(in: .whitespacesAndNewlines)
        let effectiveUuid: String
        if trimmedUuid.isEmpty {
            effectiveUuid = await store.ensureUuid()
        } else {
            effectiveUuid = trimmedUuid
        }
        return await completeBind(userId: trimmedId, uuid: effectiveUuid, phone: state.config.phone, repository: repository)
    }

    private func completeBind(
        userId: String,
        uuid: String,
        phone: String,
        repository: RepositoryActor?
    ) async -> HotWaterOpResult {
        await store.saveCredentials(userId: userId, uuid: uuid)
        var nextCfg = state.config
        nextCfg.phone = phone
        store.writeConfig(nextCfg)

        state.isBound = true
        state.userIdMasked = Self.maskUserId(userId)
        state.config = nextCfg
        state.phase = .idle
        state.errorCode = .none
        state.errorDetail = ""
        hydrated = true
        store.syncWidgetSnapshot(from: state)

        return await refreshStatusAndCheckArrears(probeBle: false, repository: repository)
    }

    public func unbind() async {
        await store.clearAll()
        state = HotWaterState()
        hydrated = true
        HotWaterArrearsScheduler.scheduleNextRefresh(enabled: false)
    }

    // MARK: - Settings

    public func updateArrearsConfig(enabled: Bool, thresholdYuan: Int) {
        let clamped = min(max(thresholdYuan, 1), 200)
        var nextCfg = state.config
        nextCfg.arrearsTodoEnabled = enabled
        nextCfg.arrearsThresholdYuan = clamped
        store.writeConfig(nextCfg)
        state.config = nextCfg
        HotWaterArrearsScheduler.scheduleNextRefresh(enabled: state.isBound && enabled)
    }

    public func updateShowerNotifyEnabled(_ enabled: Bool) {
        var nextCfg = state.config
        nextCfg.showerSummaryNotifyEnabled = enabled
        store.writeConfig(nextCfg)
        state.config = nextCfg
    }

    public func cachedShowerRecords() -> [HotWaterShowerRecord] {
        store.readShowerRecords()
    }

    // MARK: - Status, Bills & Arrears

    @discardableResult
    public func refreshStatusAndCheckArrears(
        probeBle: Bool = false,
        repository: RepositoryActor? = nil
    ) async -> HotWaterOpResult {
        await ensureHydrated()
        guard let userId = await store.userId() else {
            state.busy = false
            state.errorCode = .notBound
            state.errorDetail = ""
            return HotWaterOpResult(ok: false, phase: .unbound, errorCode: .notBound)
        }
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""

        let uuid = await store.ensureUuid()

        let statusData: HotWaterStatusSnapshot
        switch await backend.fetchAccountStatus(userId: userId, uuid: uuid, currentConfig: state.config) {
        case .ok(let data):
            statusData = data
        case .err(let code, let detail):
            state.busy = false
            state.errorCode = code
            state.errorDetail = detail
            store.syncWidgetSnapshot(from: state)
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: code, detail: detail)
        }

        let snChanged = !statusData.machineSn.isEmpty && statusData.machineSn != state.config.machineSn
        var cfg = state.config
        if !statusData.machineSn.isEmpty { cfg.machineSn = statusData.machineSn }
        if !statusData.bluetoothName.isEmpty { cfg.bluetoothName = statusData.bluetoothName }
        if !statusData.schoolName.isEmpty { cfg.schoolName = statusData.schoolName }
        if !statusData.houseName.isEmpty { cfg.houseName = statusData.houseName }
        if !statusData.dormitoryNo.isEmpty { cfg.dormitoryNo = statusData.dormitoryNo }
        if snChanged { cfg.blePeripheralUUID = "" }
        store.writeConfig(cfg)

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let balanceYuan = Double(statusData.canUseAmount) / 100.0
        store.writeArrearsSnapshot(
            isArrearage: statusData.isArrearage,
            arrearsYuan: 0.0,
            canUseAmount: statusData.canUseAmount,
            checkedAt: nowMs
        )

        var probedOpen: Bool? = nil
        if probeBle, cfg.hasMeter, !bleInFlight {
            bleInFlight = true
            defer { bleInFlight = false }
            let cfgSnapshot = cfg
            probedOpen = await backend.queryValveOpen(config: cfgSnapshot) { [weak self] resolvedUUID in
                Task { @MainActor [weak self] in
                    guard let self, !resolvedUUID.isEmpty, resolvedUUID != self.state.config.blePeripheralUUID else { return }
                    var updated = self.state.config
                    updated.blePeripheralUUID = resolvedUUID
                    self.store.writeConfig(updated)
                    self.state.config = updated
                }
            }
            if let probedOpen {
                let openedAt = probedOpen ? (store.readValveOpenedAt() > 0 ? store.readValveOpenedAt() : nowMs) : 0
                store.writeValveOpen(probedOpen, openedAt: openedAt)
            }
        }
        let valveOpen = probedOpen ?? store.readValveOpen()

        state.config = cfg
        state.phase = valveOpen ? .valveOpen : .idle
        state.isArrearage = statusData.isArrearage
        state.arrearsYuan = 0.0
        state.canUseAmount = statusData.canUseAmount
        state.lastCheckAt = nowMs
        state.busy = false
        state.errorCode = .none
        state.errorDetail = ""

        if let repository,
           cfg.arrearsTodoEnabled,
           statusData.isArrearage || balanceYuan <= Double(cfg.arrearsThresholdYuan)
        {
            await HotWaterArrearsScheduler.ensureArrearsTodo(
                balanceYuan: balanceYuan,
                repository: repository,
                nowMillis: nowMs
            )
        }

        store.syncWidgetSnapshot(from: state)
        return HotWaterOpResult(ok: true, phase: state.phase)
    }

    public func fetchOrders(all: Bool, pageIndex: Int = 1) async -> HotWaterApiResult<HotWaterOrderPage> {
        await ensureHydrated()
        guard let userId = await store.userId() else {
            return .err(code: .notBound)
        }
        return await backend.fetchOrders(
            userId: userId,
            all: all,
            pageIndex: pageIndex
        )
    }

    // MARK: - BLE Valve Toggle

    @discardableResult
    public func toggleValve(repository: RepositoryActor? = nil) async -> HotWaterOpResult {
        await ensureHydrated()
        guard let userId = await store.userId() else {
            return HotWaterOpResult(ok: false, phase: .unbound, errorCode: .notBound)
        }
        let cfg = state.config
        guard cfg.hasMeter else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .noMeterInfo)
        }
        guard !bleInFlight else {
            return HotWaterOpResult(ok: false, phase: state.phase, errorCode: .busy)
        }
        bleInFlight = true
        defer { bleInFlight = false }

        let balanceBeforeCents = state.canUseAmount
        let openedAtMs = store.readValveOpenedAt()

        state.phase = .working
        state.busy = true
        state.errorCode = .none
        state.errorDetail = ""
        store.syncWidgetSnapshot(from: state)

        let toggleResult = await backend.toggleValve(
            userId: userId,
            config: cfg,
            onPeripheralUUIDResolved: { [weak self] resolvedUUID in
                Task { @MainActor [weak self] in
                    guard let self, !resolvedUUID.isEmpty, resolvedUUID != self.state.config.blePeripheralUUID else { return }
                    var updated = self.state.config
                    updated.blePeripheralUUID = resolvedUUID
                    self.store.writeConfig(updated)
                    self.state.config = updated
                }
            }
        )
        let result = toggleResult.opResult

        let actionAtMs = Int64(Date().timeIntervalSince1970 * 1000)
        if result.ok, result.phase == .valveOpen {
            store.writeValveOpen(true, openedAt: actionAtMs)
        } else if result.ok, result.phase == .idle {
            store.writeValveOpen(false, openedAt: 0)
        }

        state.phase = result.phase
        state.errorCode = result.errorCode
        state.errorDetail = result.detail
        state.lastActionAt = actionAtMs
        state.busy = false
        store.syncWidgetSnapshot(from: state)

        if result.ok, result.phase == .idle {
            if result.detail.isEmpty {
                _ = await refreshStatusAndCheckArrears(probeBle: false, repository: repository)
            }
            if let rec = toggleResult.consumeSummary {
                let diffCents = balanceBeforeCents - state.canUseAmount
                let costYuan = diffCents > 0
                    ? Double(diffCents) / 100.0
                    : HotWaterMetrics.estimateCostYuan(useAmount: rec.useAmount)
                let durationSec = HotWaterMetrics.durationSecondsBetween(startTime: rec.startTime, endTime: rec.endTime)
                    ?? (openedAtMs > 0 ? max(0, (actionAtMs - openedAtMs) / 1000) : 0)
                let liters = HotWaterMetrics.estimateLiters(useAmount: rec.useAmount, costYuan: costYuan)

                let summary = HotWaterShowerRecord(
                    consumeId: rec.consumeId,
                    startTime: rec.startTime,
                    endTime: rec.endTime,
                    durationSec: durationSec,
                    useAmount: rec.useAmount,
                    costYuan: costYuan,
                    liters: liters
                )
                store.appendShowerRecord(summary)
                if state.config.showerSummaryNotifyEnabled {
                    await postShowerSummaryNotification(summary)
                }
            }
        }
        return result
    }

    // MARK: - Helpers

    private func postShowerSummaryNotification(_ summary: HotWaterShowerRecord) async {
        #if canImport(UserNotifications)
        let center = UNUserNotificationCenter.current()
        let content = UNMutableNotificationContent()
        content.title = "热水已关闭"
        let mins = summary.durationSec / 60
        let secs = summary.durationSec % 60
        let durationText = mins > 0 ? "\(mins)分\(secs)秒" : "\(secs)秒"
        content.body = String(
            format: "本次用水 %@ · 约 %.1f L · 预估 ¥%.2f",
            durationText,
            summary.liters,
            summary.costYuan
        )
        content.sound = .default
        let req = UNNotificationRequest(
            identifier: "goke.hotwater.shower.\(summary.consumeId)",
            content: content,
            trigger: nil
        )
        try? await center.add(req)
        #endif
    }

    private static func maskUserId(_ userId: String) -> String {
        let clean = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.count > 6 else { return "***" }
        return "\(clean.prefix(3))***\(clean.suffix(3))"
    }
}
