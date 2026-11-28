import Foundation
import GokeCore
import SwiftUI
import GokeShared
#if canImport(EventKit)
import EventKit
#endif
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Local Helpers

private enum SyncCalendarStorageKeys {
    static let lastServerURL = "app.goke.ios.sync.lastServerURL.v1"
    static let sourceCalendarIds = "app.goke.ios.calendarSync.sourceIds.v1"
    static let targetCalendarId = "app.goke.ios.calendarSync.targetId.v1"
    static let syncEnabled = "app.goke.ios.calendarSync.enabled.v1"
    static let exportCourses = "app.goke.ios.calendarSync.exportCourses.v1"
}

@MainActor
private enum SyncUIHelpers {
    static func defaultDeviceName() -> String {
        #if canImport(UIKit)
        return UIDevice.current.name
        #else
        return "iPhone"
        #endif
    }

    static func openAppSettings() {
        #if canImport(UIKit)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }

    private static let dateTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()

    static func formatDateTime(millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return dateTimeFormatter.string(from: date)
    }
}

// MARK: - 1. SyncSettingsView (Self-Hosted LWW Cloud Sync + Cloud MCP)

public struct SyncSettingsView: View {
    @Environment(AppContainer.self) private var container
    @Environment(SyncStateCoordinator.self) private var syncCoordinator
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var serverURL: String = ""
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var deviceName: String = ""

    @State private var mcpEndpoint: String?
    @State private var mcpConfigJSON: String?
    @State private var showMCPConfig: Bool = false

    @State private var showChangePasswordSheet: Bool = false
    @State private var oldPassword: String = ""
    @State private var newPassword: String = ""
    @State private var confirmPassword: String = ""
    @State private var isChangingPassword: Bool = false
    @State private var passwordError: String?

    @State private var showLogoutConfirm: Bool = false
    @State private var localError: String?

    public init() {}

    private var pendingLocalCount: Int {
        SyncPlanner.pending(
            snapshot: container.snapshot,
            watermark: syncCoordinator.cursorState.watermark
        ).count
    }

    private var isSignedInActive: Bool {
        syncCoordinator.isSignedIn && !syncCoordinator.needsRelogin
    }

    private var isLoginDisabled: Bool {
        syncCoordinator.isSyncing
            || serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || password.isEmpty
    }

    public var body: some View {
        Form {
            if let err = localError ?? syncCoordinator.lastError, !err.isEmpty {
                Section {
                    Label {
                        Text(err)
                            .font(.subheadline)
                            .foregroundStyle(theme.error)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(theme.error)
                    }
                }
                .listRowBackground(theme.errorContainer)
            }

            if isSignedInActive {
                accountAndSyncSection
                cloudMCPSection
                securitySection
            } else {
                if syncCoordinator.needsRelogin {
                    reloginWarningSection
                }
                signedOutLoginSections
            }
        }
        .gokePaperListStyle()
        .refreshable {
            if isSignedInActive {
                await performManualSync()
            } else {
                await loadSyncMetadata()
            }
        }
        .navigationTitle("账号与同步")
        #if os(iOS)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .sheet(isPresented: $showChangePasswordSheet) {
            changePasswordSheet
                .gokePaperSheet(detents: [.medium, .large])
        }
        .alert("退出登录？", isPresented: $showLogoutConfirm) {
            Button("取消", role: .cancel) {}
            Button("退出登录", role: .destructive) {
                Task { await performLogout() }
            }
        } message: {
            Text("退出后回到本地模式，本机与服务器数据均会保留。")
        }
        .task {
            await loadSyncMetadata()
        }
    }

    // MARK: - Signed-Out Sections

    private var reloginWarningSection: some View {
        Section {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("凭据已失效")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(theme.error)
                    Text("登录已失效，请重新验证密码。")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                }
            } icon: {
                Image(systemName: "exclamationmark.shield.fill")
                    .foregroundStyle(theme.error)
            }
        }
        .listRowBackground(theme.surface)
    }

    @ViewBuilder
    private var signedOutLoginSections: some View {
        let isInsecureHTTP = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .hasPrefix("http://")

        Section {
            TextField("服务器地址", text: $serverURL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .textContentType(.URL)
                .foregroundStyle(theme.ink)

            TextField("用户名", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.username)
                .foregroundStyle(theme.ink)

            SecureField("密码", text: $password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.password)
                .foregroundStyle(theme.ink)

            TextField("设备名称（可选）", text: $deviceName)
                .foregroundStyle(theme.ink)
        } header: {
            Text("登录自建服务器")
        } footer: {
            if isInsecureHTTP {
                Text("当前使用未加密 HTTP 连接，建议改用 HTTPS")
            }
        }
        .listRowBackground(theme.surface)

        Section {
            GokePrimaryActionButton(
                syncCoordinator.isSyncing ? "登录中..." : "登录并同步",
                systemImage: "icloud.and.arrow.up",
                isLoading: syncCoordinator.isSyncing,
                isDisabled: isLoginDisabled
            ) {
                Task { await performLogin() }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        }

        if syncCoordinator.needsRelogin {
            Section {
                Button("退出登录", role: .destructive) {
                    showLogoutConfirm = true
                }
            }
            .listRowBackground(theme.surface)
        }
    }

    // MARK: - Signed-In Sections

    private var accountAndSyncSection: some View {
        Section {
            LabeledContent("服务器") {
                Text(syncCoordinator.account?.serverURL ?? "—")
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
                    .multilineTextAlignment(.trailing)
            }

            LabeledContent("账号") {
                HStack(spacing: GokeSpacing.xs) {
                    Text(syncCoordinator.account?.username ?? "已登录")
                        .foregroundStyle(theme.ink)
                    if let role = syncCoordinator.account?.role, !role.isEmpty {
                        GokeBadge(role, tone: .accent)
                    }
                }
            }

            LabeledContent("连接状态") {
                GokeBadge(
                    syncCoordinator.isSyncing ? "同步中" : "已连接",
                    systemImage: syncCoordinator.isSyncing ? "arrow.triangle.2.circlepath" : "checkmark.icloud",
                    tone: syncCoordinator.isSyncing ? .accent : .success
                )
            }

            if pendingLocalCount > 0 {
                LabeledContent("待同步") {
                    Text("\(pendingLocalCount) 条")
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .contentTransition(.numericText())
                        .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: pendingLocalCount)
                }
            }

            if let lastSync = syncCoordinator.cursorState.lastSyncAt {
                LabeledContent("上次同步") {
                    Text(SyncUIHelpers.formatDateTime(millis: lastSync))
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                }
            }

            Button {
                Task { await performManualSync() }
            } label: {
                HStack {
                    Label {
                        Text(syncCoordinator.isSyncing ? "同步中..." : "立即同步")
                    } icon: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .gokeSpinningSymbol(isActive: syncCoordinator.isSyncing)
                    }
                    Spacer()
                    if syncCoordinator.isSyncing {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(syncCoordinator.isSyncing)
        } header: {
            Text("同步账号")
        }
        .listRowBackground(theme.surface)
    }

    private var cloudMCPSection: some View {
        Section {
            if let endpoint = mcpEndpoint {
                HStack(spacing: GokeSpacing.md) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("MCP 端点")
                            .font(.caption)
                            .foregroundStyle(theme.muted)
                        Text(endpoint)
                            .font(.footnote.monospaced())
                            .foregroundStyle(theme.ink)
                            .lineLimit(2)
                    }
                    Spacer(minLength: GokeSpacing.sm)
                    GokeCopyButton(
                        text: endpoint,
                        copiedTitle: nil,
                        accessibilityLabel: "复制 MCP 地址"
                    ) {
                        actions.showToast(message: "已复制 MCP 地址")
                    }
                }
            }

            if let config = mcpConfigJSON {
                GokeCopyButton(
                    text: config,
                    title: "复制配置 JSON",
                    copiedTitle: "已复制",
                    accessibilityLabel: "复制配置 JSON"
                ) {
                    actions.showToast(message: "已复制配置 JSON")
                }

                DisclosureGroup(
                    "预览配置 JSON",
                    isExpanded: $showMCPConfig.animation(GokeMotion.snappy(reduceMotion: simplifyMotion))
                ) {
                    Text(config)
                        .font(.caption.monospaced())
                        .foregroundStyle(theme.muted)
                        .textSelection(.enabled)
                        .padding(.vertical, GokeSpacing.xs)
                }
            }
        } header: {
            Text("云端 MCP")
        } footer: {
            Text("供 AI 客户端读写云端日程与待办")
        }
        .listRowBackground(theme.surface)
    }

    private var securitySection: some View {
        Section("账号与安全") {
            Button {
                oldPassword = ""
                newPassword = ""
                confirmPassword = ""
                passwordError = nil
                showChangePasswordSheet = true
            } label: {
                Label("修改密码", systemImage: "key.horizontal")
            }

            Button(role: .destructive) {
                showLogoutConfirm = true
            } label: {
                Label("退出登录", systemImage: "rectangle.portrait.and.arrow.right")
            }
        }
        .listRowBackground(theme.surface)
    }

    // MARK: - Change Password Sheet

    private var changePasswordSheet: some View {
        NavigationStack {
            Form {
                if let passwordError {
                    Section {
                        Label {
                            Text(passwordError)
                                .font(.subheadline)
                                .foregroundStyle(theme.error)
                        } icon: {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(theme.error)
                        }
                    }
                    .listRowBackground(theme.errorContainer)
                }

                Section {
                    SecureField("当前密码", text: $oldPassword)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                    SecureField("新密码（至少 8 位）", text: $newPassword)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.newPassword)
                    SecureField("确认新密码", text: $confirmPassword)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.newPassword)
                }
                .listRowBackground(theme.surface)

                Section {
                    Button {
                        Task { await performChangePassword() }
                    } label: {
                        HStack {
                            Text(isChangingPassword ? "正在提交..." : "确认修改密码")
                                .font(.body.weight(.semibold))
                            Spacer()
                            if isChangingPassword {
                                ProgressView()
                                    .controlSize(.small)
                            }
                        }
                    }
                    .disabled(
                        isChangingPassword
                            || oldPassword.isEmpty
                            || newPassword.isEmpty
                            || confirmPassword.isEmpty
                    )
                }
                .listRowBackground(theme.surface)
            }
            .gokePaperListStyle()
            .navigationTitle("修改账号密码")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showChangePasswordSheet = false }
                }
            }
        }
        .tint(theme.accent)
    }

    // MARK: - Actions

    private func loadSyncMetadata() async {
        if deviceName.isEmpty {
            deviceName = SyncUIHelpers.defaultDeviceName()
        }
        await syncCoordinator.refreshState()
        if let account = syncCoordinator.account {
            serverURL = account.serverURL
            username = account.username
        } else if let savedURL = container.appGroup.userDefaults.string(forKey: SyncCalendarStorageKeys.lastServerURL) {
            serverURL = savedURL
        }
        mcpEndpoint = await syncCoordinator.cloudMCPEndpoint()
        mcpConfigJSON = await syncCoordinator.cloudMCPConfigJSON()
    }

    private func performLogin() async {
        let trimmedURL = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        localError = nil

        do {
            let outcome = try await syncCoordinator.login(
                serverURL: trimmedURL,
                username: trimmedUser,
                password: password,
                deviceName: deviceName.isEmpty ? SyncUIHelpers.defaultDeviceName() : deviceName
            )
            container.appGroup.userDefaults.set(trimmedURL, forKey: SyncCalendarStorageKeys.lastServerURL)
            password = ""
            mcpEndpoint = await syncCoordinator.cloudMCPEndpoint()
            mcpConfigJSON = await syncCoordinator.cloudMCPConfigJSON()
            actions.showToast(
                message: "已登录并同步：拉取 \(outcome.pulledCount) · 推送 \(outcome.pushedCount)"
            )
        } catch {
            localError = error.localizedDescription
        }
    }

    private func performManualSync() async {
        localError = nil
        do {
            let outcome = try await syncCoordinator.syncNowThrowing()
            actions.showToast(
                message: "同步完成：应用 \(outcome.appliedCount) · 推送 \(outcome.pushedCount)"
            )
        } catch {
            localError = error.localizedDescription
        }
    }

    private func performLogout() async {
        await syncCoordinator.logout()
        mcpEndpoint = nil
        mcpConfigJSON = nil
        localError = nil
        actions.showToast(message: "已退出云同步，本地数据已保留")
    }

    private func performChangePassword() async {
        guard newPassword == confirmPassword else {
            passwordError = "两次输入的新密码不一致。"
            return
        }
        isChangingPassword = true
        passwordError = nil
        defer { isChangingPassword = false }

        do {
            try await syncCoordinator.changePassword(oldPassword: oldPassword, newPassword: newPassword)
            showChangePasswordSheet = false
            actions.showToast(message: "同步账号密码已更新")
        } catch {
            passwordError = error.localizedDescription
        }
    }
}

// MARK: - 2. CalendarSyncView (Apple Calendar EventKit Bidirectional Bridge)

public struct CalendarSyncView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var authState: EventKitAuthorizationState = .notDetermined
    @State private var calendars: [EventKitCalendarInfo] = []
    @State private var calendarColors: [String: Color] = [:]
    @State private var syncEnabled: Bool = false
    @State private var exportCoursesEnabled: Bool = false
    @State private var selectedSourceIds: Set<String> = []
    @State private var targetCalendarId: String?
    @State private var linkedCount: Int = 0

    @State private var isSyncing: Bool = false
    @State private var isExportingCourses: Bool = false
    @State private var lastSyncResult: EventKitSyncResult?
    @State private var errorMessage: String?
    @State private var showResetLinksConfirm: Bool = false

    public init() {}

    private var writableCalendars: [EventKitCalendarInfo] {
        calendars.filter(\.allowsContentModifications)
    }

    private var authBadge: (text: String, tone: GokeBadge.Tone) {
        switch authState {
        case .fullAccess: return ("完全访问", .success)
        case .notDetermined: return ("未授权", .muted)
        case .writeOnly: return ("仅写入", .secondary)
        case .denied: return ("已拒绝", .error)
        case .restricted: return ("受限", .error)
        case .unavailable: return ("不可用", .muted)
        }
    }

    public var body: some View {
        Form {
            if let errorMessage {
                Section {
                    Label {
                        Text(errorMessage)
                            .font(.subheadline)
                            .foregroundStyle(theme.error)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(theme.error)
                    }
                }
                .listRowBackground(theme.errorContainer)
            }

            permissionSection

            if authState == .fullAccess {
                syncPreferencesSection

                if syncEnabled {
                    targetCalendarSection
                    sourceCalendarsSection
                    syncActionsSection
                }
            }
        }
        .gokePaperListStyle()
        .refreshable {
            await reloadEventKitState()
        }
        .navigationTitle("系统日历同步")
        #if os(iOS)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .alert("重置日历同步映射？", isPresented: $showResetLinksConfirm) {
            Button("取消", role: .cancel) {}
            Button("重置映射", role: .destructive) {
                Task {
                    await container.eventKitBridge.clearLinks()
                    linkedCount = 0
                    actions.showToast(message: "已清除本地日历同步映射")
                }
            }
        } message: {
            Text("仅清除本地映射，不会删除工课或系统日历中的已有事件。")
        }
        .task {
            loadPersistedSelection()
            await reloadEventKitState()
        }
    }

    private var permissionSection: some View {
        Section {
            LabeledContent("日历权限") {
                GokeBadge(authBadge.text, tone: authBadge.tone)
            }

            if authState == .notDetermined || authState == .writeOnly {
                Button {
                    Task { await requestAccess() }
                } label: {
                    Label("授权访问", systemImage: "calendar.badge.checkmark")
                }
            } else if authState == .denied {
                Button {
                    SyncUIHelpers.openAppSettings()
                } label: {
                    Label("前往系统设置", systemImage: "gear")
                }
            }

            if let result = lastSyncResult {
                LabeledContent("上次同步") {
                    Text("推送 \(result.pushedAdded + result.pushedUpdated) · 拉取 \(result.pulledAdded + result.pulledUpdated)")
                        .font(.footnote)
                        .monospacedDigit()
                        .foregroundStyle(theme.accent)
                }
            }
        }
        .listRowBackground(theme.surface)
    }

    private var syncPreferencesSection: some View {
        Section {
            Toggle(
                "系统日历双向同步",
                isOn: Binding(
                    get: { syncEnabled },
                    set: { newValue in
                        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                            syncEnabled = newValue
                        }
                        persistSelection()
                        if newValue {
                            Task { await runBidirectionalSync() }
                        }
                    }
                )
            )

            if syncEnabled {
                Toggle(
                    "同步课程表到系统日历",
                    isOn: Binding(
                        get: { exportCoursesEnabled },
                        set: { newValue in
                            exportCoursesEnabled = newValue
                            persistSelection()
                            if newValue, container.liveSnapshot.currentSemester != nil {
                                Task { await runExportCourses() }
                            }
                        }
                    )
                )
            }
        } footer: {
            if syncEnabled {
                Text("开启后系统日历与工课可能重复提醒，建议仅保留一侧")
            }
        }
        .listRowBackground(theme.surface)
    }

    private func calendarDotColor(for cal: EventKitCalendarInfo) -> Color {
        if let color = calendarColors[cal.id] {
            return color
        }
        let hash = abs(cal.id.hashValue)
        return theme.courseColor(index: hash).barColor
    }

    private var targetCalendarSection: some View {
        Section {
            if writableCalendars.isEmpty {
                GokeEmptyState(
                    systemImage: "calendar.badge.exclamationmark",
                    title: "未找到可写入日历",
                    subtitle: "请先在系统日历中启用账户"
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(writableCalendars, id: \.id) { cal in
                    let selected = targetCalendarId == cal.id
                    Button {
                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                            targetCalendarId = cal.id
                        }
                        persistSelection()
                    } label: {
                        HStack(spacing: GokeSpacing.md) {
                            Circle()
                                .fill(calendarDotColor(for: cal))
                                .frame(width: 10, height: 10)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(cal.title)
                                    .font(.body)
                                    .foregroundStyle(theme.ink)
                                if !cal.sourceTitle.isEmpty {
                                    Text(cal.sourceTitle)
                                        .font(.caption)
                                        .foregroundStyle(theme.muted)
                                }
                            }

                            Spacer()

                            if cal.isDefault {
                                GokeBadge("默认", tone: .muted)
                            }

                            if selected {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(theme.accent)
                                    .transition(simplifyMotion ? .opacity : .scale(scale: 0.7).combined(with: .opacity))
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                    .foregroundStyle(theme.ink)
                }
            }

            Button {
                Task { await ensureDedicatedGokeCalendar() }
            } label: {
                Label("创建专属 Goke 日历", systemImage: "calendar.badge.plus")
            }
        } header: {
            Text("写入日历")
        }
        .listRowBackground(theme.surface)
    }

    private var sourceCalendarsSection: some View {
        Section {
            ForEach(calendars, id: \.id) { cal in
                Toggle(isOn: Binding(
                    get: { selectedSourceIds.contains(cal.id) },
                    set: { isIncluded in
                        if isIncluded {
                            selectedSourceIds.insert(cal.id)
                        } else {
                            selectedSourceIds.remove(cal.id)
                        }
                        persistSelection()
                    }
                )) {
                    HStack(spacing: GokeSpacing.sm) {
                        Circle()
                            .fill(calendarDotColor(for: cal))
                            .frame(width: 10, height: 10)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(cal.title)
                                .font(.body)
                                .foregroundStyle(theme.ink)
                            if !cal.sourceTitle.isEmpty {
                                Text(cal.sourceTitle)
                                    .font(.caption)
                                    .foregroundStyle(theme.muted)
                            }
                        }
                        if !cal.allowsContentModifications {
                            GokeBadge("只读", tone: .muted)
                        }
                    }
                }
            }
        } header: {
            HStack {
                Text("拉取日历")
                Spacer()
                Text("已选 \(selectedSourceIds.count)")
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: selectedSourceIds.count)
                    .foregroundStyle(theme.muted)
            }
            .textCase(nil)
        }
        .listRowBackground(theme.surface)
    }

    private var syncActionsSection: some View {
        Section {
            Button {
                Task { await runBidirectionalSync() }
            } label: {
                HStack {
                    Label {
                        Text(isSyncing ? "同步中..." : "立即同步")
                    } icon: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .gokeSpinningSymbol(isActive: isSyncing)
                    }
                    Spacer()
                    if isSyncing {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isSyncing || isExportingCourses)

            Button {
                Task { await runExportCourses() }
            } label: {
                HStack {
                    Label(
                        isExportingCourses ? "导出中..." : "导出课表",
                        systemImage: "calendar.badge.plus"
                    )
                    Spacer()
                    if isExportingCourses {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isSyncing || isExportingCourses || container.liveSnapshot.currentSemester == nil)

            if linkedCount > 0 {
                Button("重置同步映射", role: .destructive) {
                    showResetLinksConfirm = true
                }
            }
        }
        .listRowBackground(theme.surface)
    }

    // MARK: - Actions & Persistence

    private func loadPersistedSelection() {
        let defaults = container.appGroup.userDefaults
        if let savedSources = defaults.stringArray(forKey: SyncCalendarStorageKeys.sourceCalendarIds) {
            selectedSourceIds = Set(savedSources)
        }
        targetCalendarId = defaults.string(forKey: SyncCalendarStorageKeys.targetCalendarId)
        if defaults.object(forKey: SyncCalendarStorageKeys.syncEnabled) != nil {
            syncEnabled = defaults.bool(forKey: SyncCalendarStorageKeys.syncEnabled)
        } else {
            // Backward compatibility: auto-enable if user already selected calendars before this toggle existed.
            syncEnabled = !selectedSourceIds.isEmpty || targetCalendarId != nil
        }
        exportCoursesEnabled = defaults.bool(forKey: SyncCalendarStorageKeys.exportCourses)
    }

    private func persistSelection() {
        let defaults = container.appGroup.userDefaults
        defaults.set(Array(selectedSourceIds).sorted(), forKey: SyncCalendarStorageKeys.sourceCalendarIds)
        defaults.set(targetCalendarId, forKey: SyncCalendarStorageKeys.targetCalendarId)
        defaults.set(syncEnabled, forKey: SyncCalendarStorageKeys.syncEnabled)
        defaults.set(exportCoursesEnabled, forKey: SyncCalendarStorageKeys.exportCourses)
    }

    private func reloadEventKitState() async {
        authState = await container.eventKitBridge.authorizationStatus()
        linkedCount = await container.eventKitBridge.loadLinks().count
        if authState == .fullAccess {
            calendars = await container.eventKitBridge.listCalendars()
            calendarColors = Self.loadSystemCalendarColors()
            if targetCalendarId == nil || !writableCalendars.contains(where: { $0.id == targetCalendarId }) {
                targetCalendarId = writableCalendars.first(where: \.isDefault)?.id ?? writableCalendars.first?.id
            }
        } else {
            calendars = []
            calendarColors = [:]
        }
    }

    private static func loadSystemCalendarColors() -> [String: Color] {
        #if canImport(EventKit)
        let store = EKEventStore()
        var map: [String: Color] = [:]
        for ekCal in store.calendars(for: .event) {
            if let cg = ekCal.cgColor {
                map[ekCal.calendarIdentifier] = Color(cgColor: cg)
            }
        }
        return map
        #else
        return [:]
        #endif
    }

    private func requestAccess() async {
        errorMessage = nil
        do {
            _ = try await container.eventKitBridge.requestFullAccess()
            await reloadEventKitState()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func ensureDedicatedGokeCalendar() async {
        errorMessage = nil
        do {
            let created = try await container.eventKitBridge.ensureGokeCalendar(title: "Goke")
            await reloadEventKitState()
            targetCalendarId = created.id
            persistSelection()
            actions.showToast(message: "已就绪专属「\(created.title)」系统日历")
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func runBidirectionalSync() async {
        isSyncing = true
        errorMessage = nil
        defer { isSyncing = false }

        do {
            let result = try await container.eventKitBridge.syncBidirectional(
                snapshot: container.snapshot,
                sourceCalendarIds: Array(selectedSourceIds),
                targetCalendarId: targetCalendarId,
                defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes
            )
            lastSyncResult = result
            linkedCount = await container.eventKitBridge.loadLinks().count
            if !result.changeSet.isEmpty {
                _ = await actions.commit(result.changeSet, label: "系统日历同步")
            } else {
                actions.showToast(
                    message: "系统日历同步完成：推送 \(result.pushedAdded + result.pushedUpdated) 项"
                )
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func runExportCourses() async {
        isExportingCourses = true
        errorMessage = nil
        defer { isExportingCourses = false }

        do {
            let count = try await container.eventKitBridge.exportCourses(
                snapshot: container.snapshot,
                semester: container.liveSnapshot.currentSemester,
                toCalendarId: targetCalendarId
            )
            linkedCount = await container.eventKitBridge.loadLinks().count
            actions.showToast(message: "已导出 \(count) 节课程到系统日历")
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
