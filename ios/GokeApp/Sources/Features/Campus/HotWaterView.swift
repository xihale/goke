import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
#if canImport(UserNotifications)
import UserNotifications
#endif

// MARK: - HotWaterView (智慧热水)

public struct HotWaterView: View {
    private enum BindMode: String, CaseIterable, Identifiable, Sendable {
        case password
        case sms
        case userId

        var id: String { rawValue }

        var title: String {
            switch self {
            case .password: "密码"
            case .sms: "短信"
            case .userId: "UserID"
            }
        }
    }

    public let autoToggle: Bool

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    private enum BindField: Hashable {
        case phone
        case password
        case smsCode
        case userId
        case uuid
    }

    @State private var client = HotWaterClient.shared
    @State private var bindMode: BindMode = .password
    @State private var phone: String = ""
    @State private var password: String = ""
    @State private var smsCode: String = ""
    @State private var smsCountdown: Int = 0
    @State private var smsTimerTask: Task<Void, Never>?
    @State private var userIdInput: String = ""
    @State private var uuidInput: String = ""
    @State private var showUnbindConfirm: Bool = false
    @State private var didAutoToggle: Bool = false
    @State private var dismissedErrorKey: String?
    @State private var bleAuthStatus: HotWaterBleAuthStatus = .notDetermined
    @FocusState private var focusedField: BindField?

    public init(autoToggle: Bool = false) {
        self.autoToggle = autoToggle
    }

    private var state: HotWaterState { client.state }
    private var simplifyMotion: Bool { GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance) }
    private var isWorking: Bool { state.phase == .working || state.busy }

    private var currentErrorKey: String {
        "\(state.errorCode.rawValue):\(state.errorDetail)"
    }

    private var isBluetoothDenied: Bool {
        bleAuthStatus == .denied || bleAuthStatus == .restricted || state.errorCode == .noBlePermission
    }

    private var shouldShowGenericErrorBanner: Bool {
        guard state.errorCode != .none else { return false }
        guard dismissedErrorKey != currentErrorKey else { return false }
        if state.errorCode == .noBlePermission && isBluetoothDenied { return false }
        if state.errorCode == .bluetoothDisabled && state.isBound { return false }
        return true
    }

    private var canSubmitBind: Bool {
        switch bindMode {
        case .password:
            !phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty
        case .sms:
            !phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && !smsCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .userId:
            !userIdInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    public var body: some View {
        List {
            if !client.isSupported {
                Section {
                    CampusUnsupportedBanner(
                        message: "当前开源构建未包含智慧热水协议适配器，仅支持界面预览；请使用官方安装包"
                    )
                }
                .listRowBackground(theme.surface)
            }
            if !state.isBound {
                bindSection
            } else {
                controlSection
                billsEntrySection
                reminderSettingsSection
                accountSection
            }
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("智慧热水")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            if state.isBound {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task {
                            dismissedErrorKey = nil
                            bleAuthStatus = client.bluetoothAuthorizationStatus
                            let res = await client.refreshStatusAndCheckArrears(
                                probeBle: true,
                                repository: container.repository
                            )
                            if res.ok {
                                actions.showToast(message: "已更新余额与水表状态")
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .gokeSpinningSymbol(isActive: isWorking)
                    }
                    .disabled(isWorking)
                    .accessibilityLabel(isWorking ? "正在刷新水表状态" : "刷新余额与水表状态")
                }
            }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("完成") {
                    focusedField = nil
                }
            }
        }
        .refreshable {
            guard state.isBound, !isWorking else { return }
            dismissedErrorKey = nil
            bleAuthStatus = client.bluetoothAuthorizationStatus
            _ = await client.refreshStatusAndCheckArrears(
                probeBle: true,
                repository: container.repository
            )
        }
        .task {
            bleAuthStatus = client.bluetoothAuthorizationStatus
            await client.ensureHydrated()
            if phone.isEmpty && !client.state.config.phone.isEmpty {
                phone = client.state.config.phone
            }
            if autoToggle && !didAutoToggle && client.state.isBound && !isWorking {
                didAutoToggle = true
                dismissedErrorKey = nil
                let res = await client.toggleValve(repository: container.repository)
                if res.ok {
                    actions.showToast(
                        message: res.phase == .valveOpen ? "已开启热水阀门" : "已关闭热水阀门"
                    )
                }
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                bleAuthStatus = client.bluetoothAuthorizationStatus
            }
        }
        .onChange(of: currentErrorKey) { _, _ in
            dismissedErrorKey = nil
        }
        .onDisappear {
            smsTimerTask?.cancel()
            smsTimerTask = nil
        }
        .alert("解绑账号？", isPresented: $showUnbindConfirm) {
            Button("取消", role: .cancel) {}
            Button("解绑", role: .destructive) {
                Task {
                    smsTimerTask?.cancel()
                    smsTimerTask = nil
                    smsCountdown = 0
                    dismissedErrorKey = nil
                    await client.unbind()
                    password = ""
                    smsCode = ""
                    actions.showToast(message: "已解绑")
                }
            }
        } message: {
            Text("将清除本机凭证与水表信息。")
        }
    }

    // MARK: - Unbound Form

    private var bindSection: some View {
        Group {
            Section {
                GokeSegmentedPill(
                    options: BindMode.allCases.map { ($0, $0.title) },
                    selection: $bindMode,
                    height: 40
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }

            Section {
                switch bindMode {
                case .password:
                    TextField("手机号", text: $phone)
                        .focused($focusedField, equals: .phone)
                        #if os(iOS)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        #endif
                    SecureField("密码", text: $password)
                        .focused($focusedField, equals: .password)
                        #if os(iOS)
                        .textContentType(.password)
                        #endif
                        .submitLabel(.go)
                        .onSubmit {
                            if canSubmitBind && !state.busy {
                                Task { await submitBind() }
                            }
                        }

                case .sms:
                    TextField("手机号", text: $phone)
                        .focused($focusedField, equals: .phone)
                        #if os(iOS)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        #endif
                    HStack(spacing: 10) {
                        TextField("短信验证码", text: $smsCode)
                            .focused($focusedField, equals: .smsCode)
                            #if os(iOS)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            #endif
                        GokeMintActionPill(
                            smsCountdown > 0 ? "\(smsCountdown)s" : "获取验证码",
                            style: .tonal,
                            isDisabled: state.busy
                                || smsCountdown > 0
                                || phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ) {
                            Task { await sendSmsCode() }
                        }
                    }

                case .userId:
                    TextField("UserID", text: $userIdInput)
                        .focused($focusedField, equals: .userId)
                        .autocorrectionDisabled()
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        #endif
                    TextField("UUID（留空自动生成）", text: $uuidInput)
                        .focused($focusedField, equals: .uuid)
                        .autocorrectionDisabled()
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        #endif
                        .submitLabel(.go)
                        .onSubmit {
                            if canSubmitBind && !state.busy {
                                Task { await submitBind() }
                            }
                        }
                }

                if shouldShowGenericErrorBanner {
                    errorBanner(code: state.errorCode, detail: state.errorDetail)
                }
            } header: {
                Text("账号绑定")
            }
            .listRowBackground(theme.surface)

            Section {
                let isSubmitDisabled = state.busy || !canSubmitBind
                GokePrimaryActionButton(
                    state.busy ? "绑定中…" : "绑定账号",
                    isLoading: state.busy,
                    isDisabled: isSubmitDisabled
                ) {
                    Task { await submitBind() }
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            } footer: {
                Text("账号凭证仅保存在本机钥匙串。")
            }
        }
    }

    // MARK: - Bound Valve & Balance Control

    private var controlSection: some View {
        Group {
            Section {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .center, spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(phaseTint.opacity(0.14))
                                .frame(width: 44, height: 44)
                            Image(systemName: state.phase == .valveOpen ? "drop.fill" : "drop")
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(phaseTint)
                                .symbolEffect(
                                    .pulse,
                                    isActive: !simplifyMotion && (state.phase == .valveOpen || isWorking)
                                )
                        }

                        VStack(alignment: .leading, spacing: 3) {
                            Text(state.config.dormLabel.isEmpty ? "智慧热水" : state.config.dormLabel)
                                .font(GokeTypography.titleMedium)
                                .foregroundStyle(theme.ink)
                                .lineLimit(1)

                            Text(
                                state.config.machineSn.isEmpty
                                    ? "暂未获取到水表编号"
                                    : "水表号：\(state.config.machineSn)"
                            )
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                        }

                        Spacer(minLength: 8)

                        Text(phaseLabel)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(phaseTint)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(phaseTint.opacity(0.14), in: Capsule())
                    }

                    // Hero Balance Row
                    VStack(alignment: .leading, spacing: 4) {
                        Text("可用余额")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(theme.muted)

                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(String(format: "¥%.2f", state.balanceYuan))
                                .font(.system(.largeTitle, design: .rounded, weight: .bold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .animation(
                                    GokeMotion.snappy(reduceMotion: simplifyMotion),
                                    value: state.balanceYuan
                                )
                                .foregroundStyle(balanceTint)

                            if state.isArrearage {
                                GokeBadge("已欠费", tone: .error)
                            } else if state.balanceYuan <= 3.0 {
                                GokeBadge("余额不足", tone: .error)
                            }

                            Spacer(minLength: 0)
                        }
                    }

                    // Live Elapsed Timer when Valve is Open
                    if state.phase == .valveOpen {
                        liveValveTimerBar
                    }

                    // Bluetooth Permission / Powered-Off Recovery Banners
                    if isBluetoothDenied {
                        bluetoothPermissionBanner
                    } else if state.errorCode == .bluetoothDisabled && dismissedErrorKey != currentErrorKey {
                        bluetoothPoweredOffBanner
                    }

                    if shouldShowGenericErrorBanner {
                        errorBanner(code: state.errorCode, detail: state.errorDetail)
                    }
                }
                .padding(.vertical, 6)
                .animation(
                    GokeMotion.snappy(reduceMotion: simplifyMotion),
                    value: state.phase
                )
            }
            .listRowBackground(theme.surface)

            Section {
                GokePrimaryActionButton(
                    isWorking
                        ? "通信中…"
                        : (state.phase == .valveOpen ? "关水结算" : "一键开水"),
                    systemImage: isWorking
                        ? nil
                        : (state.phase == .valveOpen ? "stop.circle.fill" : "play.circle.fill"),
                    isLoading: isWorking,
                    isDisabled: isWorking,
                    tint: state.phase == .valveOpen ? theme.error : theme.accent
                ) {
                    Task {
                        dismissedErrorKey = nil
                        let res = await client.toggleValve(repository: container.repository)
                        bleAuthStatus = client.bluetoothAuthorizationStatus
                        if res.ok {
                            actions.showToast(
                                message: res.phase == .valveOpen ? "已开启热水阀门" : "已关闭热水阀门"
                            )
                        }
                    }
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
        }
    }

    private var liveValveTimerBar: some View {
        let openedAtMs = client.store.readValveOpenedAt()
        let startDate =
            openedAtMs > 0
            ? Date(timeIntervalSince1970: TimeInterval(openedAtMs) / 1000.0)
            : (state.lastActionAt > 0
                ? Date(timeIntervalSince1970: TimeInterval(state.lastActionAt) / 1000.0)
                : Date())

        return HStack(spacing: 8) {
            Image(systemName: "timer")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(theme.accent)
            Text("本次放水时长")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            Spacer()
            // Narrow TimelineView scope: only the 5-character elapsed timer text redraws each second.
            TimelineView(.periodic(from: .now, by: 1.0)) { context in
                let elapsed = max(0, Int(context.date.timeIntervalSince(startDate)))
                let mins = elapsed / 60
                let secs = elapsed % 60
                Text(String(format: "%02d:%02d", mins, secs))
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(theme.accent)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            theme.accentContainer,
            in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
        )
    }

    private var bluetoothPoweredOffBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "bolt.horizontal.circle.fill")
                .foregroundStyle(theme.modified)
            VStack(alignment: .leading, spacing: 2) {
                Text("蓝牙未开启")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.ink)
                Text("开启蓝牙后重试")
                    .font(.caption)
                    .foregroundStyle(theme.muted)
            }
            Spacer(minLength: 4)
            GokeMintActionPill("重试", style: .tonal, isDisabled: isWorking) {
                Task {
                    dismissedErrorKey = nil
                    let res = await client.refreshStatusAndCheckArrears(
                        probeBle: true,
                        repository: container.repository
                    )
                    if res.ok {
                        actions.showToast(message: "蓝牙水表状态已更新")
                    }
                }
            }
        }
        .padding(10)
        .background(theme.modified.opacity(0.14), in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
    }

    private var bluetoothPermissionBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "antenna.radiowaves.left.and.right.slash")
                .foregroundStyle(theme.error)
            VStack(alignment: .leading, spacing: 2) {
                Text("未授予蓝牙权限")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.error)
                Text("需开启蓝牙权限")
                    .font(.caption)
                    .foregroundStyle(theme.muted)
            }
            Spacer(minLength: 4)
            #if canImport(UIKit)
            GokeMintActionPill("去设置", style: .surface) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            #endif
        }
        .padding(10)
        .background(theme.errorContainer, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
    }

    // MARK: - Bills Entry

    private var billsEntrySection: some View {
        Section {
            NavigationLink(value: GokeRoute.hotWaterBills) {
                HStack(spacing: 12) {
                    GokePaperIcon(
                        systemImage: "list.bullet.rectangle.portrait",
                        color: theme.accent
                    )
                    Text("账单")
                        .font(.body)
                        .foregroundStyle(theme.ink)
                }
            }
        }
        .listRowBackground(theme.surface)
    }

    // MARK: - Reminders & Arrears Settings

    private var reminderSettingsSection: some View {
        Section("提醒") {
            Toggle(
                isOn: Binding(
                    get: { state.config.arrearsTodoEnabled },
                    set: { enabled in
                        client.updateArrearsConfig(
                            enabled: enabled,
                            thresholdYuan: state.config.arrearsThresholdYuan
                        )
                    }
                )
            ) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("低余额提醒")
                        .foregroundStyle(theme.ink)
                    Text("余额低于阈值时生成待办")
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                }
            }
            .tint(theme.accent)

            if state.config.arrearsTodoEnabled {
                VStack(alignment: .leading, spacing: 8) {
                    Text("提醒阈值")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                    GokeSegmentedPill(
                        options: [3, 5, 8, 10].map { ($0, "\($0) 元") },
                        selection: Binding(
                            get: { state.config.arrearsThresholdYuan },
                            set: { yuan in
                                client.updateArrearsConfig(
                                    enabled: state.config.arrearsTodoEnabled,
                                    thresholdYuan: yuan
                                )
                            }
                        ),
                        height: 36
                    )
                }
                .padding(.vertical, 2)
            }

            Toggle(
                "洗浴结算通知",
                isOn: Binding(
                    get: { state.config.showerSummaryNotifyEnabled },
                    set: { enabled in
                        client.updateShowerNotifyEnabled(enabled)
                        if enabled {
                            requestNotificationPermissionIfNeeded()
                        }
                    }
                )
            )
            .foregroundStyle(theme.ink)
            .tint(theme.accent)
        }
        .listRowBackground(theme.surface)
    }

    // MARK: - Account Info & Unbind

    private var accountSection: some View {
        Section("账号") {
            LabeledContent("账号 ID") {
                Text(state.userIdMasked.isEmpty ? "已绑定" : state.userIdMasked)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
            }

            if !state.config.bluetoothName.isEmpty {
                LabeledContent("水表蓝牙名") {
                    Text(state.config.bluetoothName)
                        .font(.subheadline.monospaced())
                        .foregroundStyle(theme.muted)
                }
            }

            if !state.config.schoolName.isEmpty {
                LabeledContent("所属学校") {
                    Text(state.config.schoolName)
                        .foregroundStyle(theme.muted)
                }
            }

            Button(role: .destructive) {
                showUnbindConfirm = true
            } label: {
                Text("解绑账号")
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .listRowBackground(theme.surface)
    }

    // MARK: - Helpers

    private var phaseLabel: String {
        switch state.phase {
        case .unbound: "未绑定"
        case .idle: "空闲"
        case .working: "通信中"
        case .valveOpen: "出水中"
        case .error: "异常"
        }
    }

    private var phaseTint: Color {
        switch state.phase {
        case .valveOpen: theme.accent
        case .error: theme.error
        case .working: theme.modified
        case .idle, .unbound: theme.muted
        }
    }

    private var balanceTint: Color {
        if state.isArrearage || state.balanceYuan <= 3.0 {
            return theme.error
        }
        if state.balanceYuan < 10.0 {
            return theme.modified
        }
        return theme.accent
    }

    private func errorBanner(code: HotWaterErrorCode, detail: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(theme.error)
                .font(.subheadline)
            Text(HotWaterUIFormat.errorMessage(code: code, detail: detail))
                .font(.subheadline)
                .foregroundStyle(theme.error)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 4)
            Button {
                dismissedErrorKey = currentErrorKey
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.mintCompact)
            .accessibilityLabel("关闭错误提示")
        }
        .padding(10)
        .background(theme.errorContainer, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
    }

    private func sendSmsCode() async {
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPhone.isEmpty else { return }
        dismissedErrorKey = nil
        let res = await client.sendSmsCode(phone: trimmedPhone)
        if res.ok {
            actions.showToast(message: "验证码已发送")
            smsTimerTask?.cancel()
            smsCountdown = 60
            smsTimerTask = Task { @MainActor in
                while !Task.isCancelled && smsCountdown > 0 {
                    do {
                        try await Task.sleep(for: .seconds(1))
                    } catch {
                        break
                    }
                    guard !Task.isCancelled else { break }
                    if smsCountdown > 0 {
                        smsCountdown -= 1
                    }
                }
            }
        }
    }

    private func submitBind() async {
        focusedField = nil
        dismissedErrorKey = nil
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedCode = smsCode.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUserId = userIdInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUuid = uuidInput.trimmingCharacters(in: .whitespacesAndNewlines)

        let res: HotWaterOpResult
        switch bindMode {
        case .password:
            res = await client.loginWithPassword(
                phone: trimmedPhone,
                password: password,
                repository: container.repository
            )
        case .sms:
            res = await client.loginWithSms(
                phone: trimmedPhone,
                code: trimmedCode,
                repository: container.repository
            )
        case .userId:
            res = await client.bindWithUserId(
                trimmedUserId,
                uuid: trimmedUuid,
                repository: container.repository
            )
        }
        if res.ok {
            smsTimerTask?.cancel()
            smsTimerTask = nil
            smsCountdown = 0
            actions.showToast(message: "智慧热水账号绑定成功")
        }
    }

    private func requestNotificationPermissionIfNeeded() {
        #if canImport(UserNotifications)
        Task {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(
                options: [.alert, .sound, .badge]
            )
        }
        #endif
    }
}

// MARK: - HotWaterBillsView (用水账单)

public struct HotWaterBillsView: View {
    private enum BillTab: String, CaseIterable, Identifiable, Sendable {
        case current
        case all

        var id: String { rawValue }

        var title: String {
            switch self {
            case .current: "未结算"
            case .all: "全部账单"
            }
        }
    }

    private struct TabPageState {
        var loaded: Bool = false
        var loading: Bool = false
        var orders: [HotWaterOrder] = []
        var totalAmount: Double? = nil
        var hasMore: Bool = false
        var pageIndex: Int = 1
        var error: HotWaterErrorCode? = nil
    }

    @Environment(\.gokeTheme) private var theme

    @State private var client = HotWaterClient.shared
    @State private var selectedTab: BillTab = .current
    @State private var currentTabState = TabPageState()
    @State private var allTabState = TabPageState()
    @State private var localRecords: [HotWaterShowerRecord] = []

    public init() {}

    private var activeState: TabPageState {
        selectedTab == .current ? currentTabState : allTabState
    }

    public var body: some View {
        List {
            Section {
                GokeSegmentedPill(
                    options: BillTab.allCases.map { ($0, $0.title) },
                    selection: $selectedTab,
                    height: 38
                )
                .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))

                if selectedTab == .current && activeState.loaded && !activeState.orders.isEmpty {
                    let total =
                        activeState.totalAmount
                        ?? activeState.orders.reduce(0.0) { $0 + (Double($1.usePrice) ?? 0.0) }
                    HStack {
                        Text("未结算共 \(activeState.orders.count) 笔")
                            .font(.subheadline)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                        Spacer()
                        Text(String(format: "合计 ¥%.2f", total))
                            .font(.subheadline.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(theme.accent)
                    }
                }
            }
            .listRowBackground(theme.surface)

            if activeState.loading && activeState.orders.isEmpty {
                Section {
                    HStack {
                        Spacer()
                        ProgressView("加载中…")
                            .padding(.vertical, 24)
                        Spacer()
                    }
                }
                .listRowBackground(theme.surface)
            } else if let err = activeState.error, activeState.orders.isEmpty {
                Section {
                    GokeEmptyState(
                        systemImage: "exclamationmark.triangle",
                        title: "账单加载失败",
                        subtitle: HotWaterUIFormat.errorMessage(code: err),
                        primaryActionTitle: "重试",
                        onPrimaryAction: {
                            Task { await loadPage(for: selectedTab, pageIndex: 1, append: false) }
                        }
                    )
                    .listRowBackground(Color.clear)
                }
            } else if activeState.loaded && activeState.orders.isEmpty {
                Section {
                    GokeEmptyState(
                        systemImage: "drop",
                        title: selectedTab == .current ? "暂无未结算账单" : "暂无历史账单"
                    )
                    .listRowBackground(Color.clear)
                }
            } else {
                Section {
                    ForEach(activeState.orders) { order in
                        billRow(order)
                    }

                    if let err = activeState.error {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(theme.error)
                                .font(.caption)
                            Text(HotWaterUIFormat.errorMessage(code: err))
                                .font(.caption)
                                .foregroundStyle(theme.error)
                                .lineLimit(2)
                            Spacer(minLength: 4)
                            GokeMintActionPill("重试", style: .tonal) {
                                Task {
                                    await loadPage(
                                        for: selectedTab,
                                        pageIndex: activeState.pageIndex + (activeState.hasMore ? 1 : 0),
                                        append: activeState.hasMore
                                    )
                                }
                            }
                        }
                    }

                    if activeState.hasMore {
                        Button {
                            guard !activeState.loading else { return }
                            Task {
                                await loadPage(
                                    for: selectedTab,
                                    pageIndex: activeState.pageIndex + 1,
                                    append: true
                                )
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if activeState.loading {
                                    ProgressView().controlSize(.small)
                                }
                                Text(activeState.loading ? "加载中…" : "加载更多")
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .disabled(activeState.loading)
                    }
                }
                .listRowBackground(theme.surface)
            }
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("账单")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .refreshable {
            localRecords = client.cachedShowerRecords()
            await loadPage(for: selectedTab, pageIndex: 1, append: false)
        }
        .task(id: selectedTab) {
            localRecords = client.cachedShowerRecords()
            let st = selectedTab == .current ? currentTabState : allTabState
            if !st.loaded && !st.loading {
                await loadPage(for: selectedTab, pageIndex: 1, append: false)
            }
        }
    }

    private func billRow(_ order: HotWaterOrder) -> some View {
        let metrics = HotWaterMetrics.estimateForOrder(order, localRecords: localRecords)
        return HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(order.createTime.isEmpty ? order.orderId : order.createTime)
                    .font(.subheadline.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)

                if metrics.liters > 0.0 {
                    if let durationSec = metrics.durationSec {
                        Text(
                            "\(HotWaterUIFormat.formatDuration(durationSec)) · 约 \(String(format: "%.1f", metrics.liters)) L"
                        )
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                    } else {
                        Text(String(format: "预估用水 %.1f L", metrics.liters))
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }

                Text(
                    order.isPaid && !order.payTime.isEmpty
                        ? "结算：\(order.payTime)"
                        : "单号：\(order.orderId)"
                )
                .font(.caption2)
                .monospacedDigit()
                .foregroundStyle(theme.muted.opacity(0.75))
                .lineLimit(1)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 4) {
                Text("¥\(order.usePrice)")
                    .font(.headline.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(order.isPaid ? theme.ink : theme.accent)

                if selectedTab == .all {
                    Text(order.isPaid ? "已结算" : "未结算")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(order.isPaid ? theme.muted : theme.modified)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(
                            (order.isPaid ? theme.muted : theme.modified).opacity(0.14),
                            in: Capsule()
                        )
                }
            }
        }
        .padding(.vertical, 2)
    }

    private func setTabState(_ tab: BillTab, _ value: TabPageState) {
        if tab == .current {
            currentTabState = value
        } else {
            allTabState = value
        }
    }

    private func loadPage(for tab: BillTab, pageIndex: Int, append: Bool) async {
        var prev = tab == .current ? currentTabState : allTabState
        prev.loading = true
        prev.error = nil
        setTabState(tab, prev)

        switch await client.fetchOrders(all: tab == .all, pageIndex: pageIndex) {
        case .ok(let page):
            let merged: [HotWaterOrder]
            if append {
                var seen = Set(prev.orders.map(\.orderId))
                var combined = prev.orders
                for item in page.orders where seen.insert(item.orderId).inserted {
                    combined.append(item)
                }
                merged = combined
            } else {
                merged = page.orders
            }
            setTabState(
                tab,
                TabPageState(
                    loaded: true,
                    loading: false,
                    orders: merged,
                    totalAmount: page.totalAmount ?? prev.totalAmount,
                    hasMore: page.hasMore,
                    pageIndex: pageIndex,
                    error: nil
                )
            )

        case .err(let code, _):
            prev.loading = false
            prev.error = code
            setTabState(tab, prev)
        }
    }
}

// MARK: - Formatting & Error Descriptions

private enum HotWaterUIFormat {
    static func errorMessage(code: HotWaterErrorCode, detail: String = "") -> String {
        if code == .authFailed,
           !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           detail.unicodeScalars.contains(where: { $0.value > 127 })
        {
            return detail
        }
        switch code {
        case .none:
            return ""
        case .notBound:
            return "尚未绑定智慧热水账号"
        case .noMeterInfo:
            return "未获取到宿舍水表信息，请先刷新状态"
        case .noBlePermission:
            return "缺少蓝牙权限，请在系统设置中开启蓝牙访问"
        case .bluetoothDisabled:
            return "手机蓝牙未开启，请先打开蓝牙"
        case .deviceNotFound:
            return "附近未搜索到宿舍水表，请靠近水表后重试"
        case .deviceDisabled:
            return "当前水表不可用或处于欠费停用状态"
        case .authFailed:
            return "账号认证失败，请检查手机号、密码或验证码"
        case .networkError:
            return "网络连接异常，请检查网络后重试"
        case .bleError:
            return "蓝牙通信异常，请靠近水表后重试"
        case .busy:
            return "正在执行上一次操作，请稍候"
        case .unsupported:
            let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, trimmed.unicodeScalars.contains(where: { $0.value > 127 }) {
                return trimmed
            }
            return trimmed.isEmpty ? CampusAdapterErrorMessages.unsupported : "当前设备不支持低功耗蓝牙"
        }
    }

    static func formatDuration(_ seconds: Int64) -> String {
        let s = max(0, seconds)
        let mins = s / 60
        let rem = s % 60
        if mins > 0 {
            return "\(mins)分\(rem)秒"
        }
        return "\(rem)秒"
    }
}
