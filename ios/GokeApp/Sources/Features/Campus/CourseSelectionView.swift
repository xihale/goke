import Foundation
import GokeCore
import GokeShared
import SwiftUI

/// GDUT JXFW course selection (`xsxklist`) view supporting window-status detection,
/// open elective plans, credit wallet progress, dual-source schedule conflict detection,
/// search/filter chips, bilingual syllabus detail sheet, and confirmed enroll/drop actions.
public struct CourseSelectionView: View {
    private enum XkTab: String, CaseIterable, Identifiable {
        case available
        case enrolled

        var id: String { rawValue }
    }

    private enum XkSearchKey: String, CaseIterable, Identifiable {
        case courseName = "kcmc"
        case teacher = "teaxm"
        case category = "kcflmc"

        var id: String { rawValue }

        var title: String {
            switch self {
            case .courseName: "课程名"
            case .teacher: "教师"
            case .category: "类别"
            }
        }
    }

    private enum ConfirmMode {
        case enroll
        case drop
    }

    private struct ConfirmRequest: Identifiable {
        let item: XkCourseItem
        let mode: ConfirmMode
        var id: String { "\(item.kcrwdm)-\(mode == .enroll ? "enroll" : "drop")" }
    }

    private enum ActiveSheet: Identifiable {
        case login
        case detail(XkCourseItem)

        var id: String {
            switch self {
            case .login: "login"
            case .detail(let item): "detail:\(item.kcrwdm)"
            }
        }
    }

    private static let pageSize = 50

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @Namespace private var xkNamespace

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var hasCredentials: Bool = false

    @State private var window: XkWindowStatus?
    @State private var wallet: XkCreditWallet?
    @State private var available: [XkCourseItem] = []
    @State private var availableTotal: Int = 0
    @State private var enrolled: [XkCourseItem] = []
    @State private var currentPage: Int = 1
    @State private var reachedEnd: Bool = false
    @State private var cachedJxfwOccupied: [XkOccupiedSlot] = []
    @State private var detailCache: [String: XkCourseDetail] = [:]
    @State private var syllabusCache: [String: (zh: String, en: String)] = [:]

    @State private var activeTab: XkTab = .available
    @State private var searchKey: XkSearchKey = .courseName
    @State private var searchQuery: String = ""
    @State private var onlyHasSeats: Bool = false
    @State private var onlyNoConflict: Bool = false

    @State private var isLoading: Bool = false
    @State private var isLoadingMore: Bool = false
    @State private var loadGeneration: Int = 0
    @State private var actionBusyId: String?
    @State private var errorMessage: String?

    @State private var activeSheet: ActiveSheet?
    @State private var pendingConfirmRequest: ConfirmRequest?
    @State private var confirmRequest: ConfirmRequest?

    public init() {}

    private var visibleAvailable: [XkCourseItem] {
        available.filter { item in
            (!onlyHasSeats || !item.isFull) && (!onlyNoConflict || !item.hasConflict)
        }
    }

    private var hasMore: Bool {
        !reachedEnd && available.count < availableTotal
    }

    public var body: some View {
        Group {
            if !container.jxfwClient.isSupported && window == nil && !isLoading {
                GokeEmptyState(
                    systemImage: "graduationcap",
                    title: "开源构建未包含教务适配器",
                    subtitle: "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包",
                    primaryActionTitle: nil,
                    onPrimaryAction: nil
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if !hasCredentials && window == nil && !isLoading {
                GokeEmptyState(
                    systemImage: "graduationcap",
                    title: "尚未登录教务",
                    subtitle: "请先绑定广工教务账号",
                    primaryActionTitle: "登录教务",
                    onPrimaryAction: {
                        activeSheet = .login
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if window == nil && isLoading && errorMessage == nil {
                VStack(spacing: GokeSpacing.md) {
                    ProgressView()
                    Text("加载中…")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    if let errorMessage {
                        Section {
                            VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                                Label {
                                    Text(errorMessage)
                                        .font(.subheadline)
                                        .foregroundStyle(theme.error)
                                } icon: {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(theme.error)
                                }

                                HStack(spacing: GokeSpacing.md) {
                                    GokeMintActionPill("重试", style: .surface) {
                                        Task { await loadCourseSelection(page: 1) }
                                    }

                                    GokeMintActionPill("重新登录", style: .surface) {
                                        activeSheet = .login
                                    }

                                    if CampusHelpers.isUnexpectedBannerMessage(errorMessage) {
                                        GokeMintActionPill("复制报错信息", style: .surface) {
                                            #if canImport(UIKit)
                                            UIPasteboard.general.string = errorMessage
                                            #endif
                                            actions.showToast(message: "已复制报错信息，可粘贴发给开发者")
                                        }
                                    }
                                }
                            }
                            .padding(.vertical, 2)
                        }
                        .listRowBackground(theme.errorContainer)
                    }

                    if let window {
                        if !window.isOpen {
                            closedWindowSection(window)
                            if !window.openPlans.isEmpty {
                                openPlansSection(window.openPlans)
                            }
                            if !enrolled.isEmpty {
                                enrolledCoursesSection(canDrop: window.canDrop, showHeader: true)
                            }
                        } else {
                            openSummarySection(window)
                            tabSwitcherSection
                            if activeTab == .available {
                                searchAndFilterSection
                                availableCoursesSection(canDrop: window.canDrop)
                            } else {
                                enrolledCoursesSection(canDrop: window.canDrop, showHeader: false)
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .gokePaperListStyle()
                .searchable(text: $searchQuery, prompt: "按\(searchKey.title)搜索可选课程")
                .onSubmit(of: .search) {
                    Task { await loadCourseSelection(page: 1) }
                }
                .refreshable {
                    await loadCourseSelection(page: 1)
                }
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("选课")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    activeSheet = .login
                } label: {
                    Image(systemName: "person.crop.circle")
                }
                .accessibilityLabel("教务账号")

                Button {
                    Task { await loadCourseSelection(page: 1) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .gokeSpinningSymbol(isActive: isLoading)
                }
                .disabled(isLoading)
                .accessibilityLabel(isLoading ? "正在刷新选课" : "刷新选课")
            }
        }
        .sheet(
            item: $activeSheet,
            onDismiss: {
                if let pending = pendingConfirmRequest {
                    pendingConfirmRequest = nil
                    confirmRequest = pending
                }
            }
        ) { sheet in
            switch sheet {
            case .login:
                casLoginSheet
                    .gokePaperSheet(detents: [.medium, .large])
            case .detail(let item):
                XkCourseDetailSheet(
                    item: item,
                    canDrop: window?.canDrop ?? false,
                    windowOpen: window?.isOpen ?? false,
                    busy: actionBusyId == item.kcrwdm,
                    loadDetail: { target in
                        try await loadCourseDetail(for: target)
                    },
                    onEnroll: {
                        pendingConfirmRequest = ConfirmRequest(item: item, mode: .enroll)
                        activeSheet = nil
                    },
                    onDrop: {
                        pendingConfirmRequest = ConfirmRequest(item: item, mode: .drop)
                        activeSheet = nil
                    }
                )
                .gokePaperSheet(detents: [.medium, .large])
            }
        }
        .confirmationDialog(
            confirmRequest?.mode == .drop ? "确认退选课程" : "确认选课",
            isPresented: Binding(
                get: { confirmRequest != nil },
                set: { if !$0 { confirmRequest = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let req = confirmRequest {
                switch req.mode {
                case .enroll:
                    Button(req.item.hasConflict ? "仍要选课" : "确认选课", role: req.item.hasConflict ? .destructive : nil) {
                        confirmRequest = nil
                        Task { await performEnroll(req.item) }
                    }
                case .drop:
                    Button("确认退选", role: .destructive) {
                        confirmRequest = nil
                        Task { await performDrop(req.item) }
                    }
                }
                Button("取消", role: .cancel) {
                    confirmRequest = nil
                }
            }
        } message: {
            if let req = confirmRequest {
                Text(confirmationMessage(for: req))
            }
        }
        .task {
            await bootstrap()
        }
    }

    // MARK: - Sections

    private func closedWindowSection(_ window: XkWindowStatus) -> some View {
        Section {
            VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                HStack(spacing: GokeSpacing.sm) {
                    Image(systemName: "clock.badge.exclamationmark")
                        .font(.title3)
                        .foregroundStyle(theme.muted)
                    Text("当前未在选课时间窗")
                        .font(.headline)
                        .foregroundStyle(theme.ink)
                }

                if !window.headerNotice.isEmpty {
                    Text(window.headerNotice)
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }

                windowStatusPills(window)
            }
            .padding(.vertical, 4)

            if let wallet {
                creditWalletRow(wallet)
            }
        } header: {
            if !window.xnxqdm.isEmpty {
                Text(JxfwParse.termLabel(window.xnxqdm))
            }
        }
        .listRowBackground(theme.surface)
    }

    private func openPlansSection(_ plans: [XkOpenPlan]) -> some View {
        Section("已开放限选计划（\(plans.count)）") {
            ForEach(plans, id: \.self) { plan in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .center, spacing: GokeSpacing.sm) {
                        Text(plan.planName)
                            .font(.body.weight(.medium))
                            .foregroundStyle(theme.ink)
                        Spacer(minLength: GokeSpacing.xs)
                        if !plan.planType.isEmpty {
                            GokeBadge(plan.planType, tone: .accent)
                        }
                    }

                    if !plan.startTime.isEmpty || !plan.endTime.isEmpty {
                        Text("开放时间：\(plan.startTime) ～ \(plan.endTime)")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .listRowBackground(theme.surface)
    }

    private func openSummarySection(_ window: XkWindowStatus) -> some View {
        Section {
            HStack(spacing: GokeSpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(window.xnxqdm.isEmpty ? "选课开放中" : JxfwParse.termLabel(window.xnxqdm))
                        .font(.headline)
                        .foregroundStyle(theme.ink)
                    if !window.headerNotice.isEmpty {
                        Text(window.headerNotice)
                            .font(.caption)
                            .foregroundStyle(theme.muted)
                            .lineLimit(3)
                    }
                }
                Spacer(minLength: GokeSpacing.xs)
                GokeBadge("开放选课", tone: .success)
            }
            .padding(.vertical, 2)

            windowStatusPills(window)

            if let wallet {
                creditWalletRow(wallet)
            }
        }
        .listRowBackground(theme.surface)
    }

    private func windowStatusPills(_ window: XkWindowStatus) -> some View {
        HStack(spacing: GokeSpacing.sm) {
            GokeBadge(
                window.canDrop ? "允许退选" : "禁止退选",
                tone: window.canDrop ? .secondary : .muted
            )
            if let maxCourses = window.maxCourses {
                GokeBadge("限选 \(maxCourses) 门", tone: .muted)
            }
            Spacer(minLength: 0)
        }
    }

    private func creditWalletRow(_ wallet: XkCreditWallet) -> some View {
        let ratio = wallet.totalCredits > 0 ? min(1.0, max(0.0, wallet.usedCredits / wallet.totalCredits)) : 0.0
        return VStack(alignment: .leading, spacing: GokeSpacing.xs) {
            HStack {
                Text("学分钱包")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(theme.ink)
                Spacer(minLength: GokeSpacing.sm)
                Text("可用 \(Self.formatCompactNumber(wallet.availableCredits)) · 已用 \(Self.formatCompactNumber(wallet.usedCredits)) / \(Self.formatCompactNumber(wallet.totalCredits))")
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: wallet)
                    .foregroundStyle(theme.accent)
            }

            ProgressView(value: ratio, total: 1.0)
                .tint(theme.accent)
                .animation(GokeMotion.standard(reduceMotion: simplifyMotion), value: ratio)
        }
        .padding(.vertical, 2)
    }

    private var tabSwitcherSection: some View {
        Section {
            HStack(spacing: GokeSpacing.xs) {
                tabButton(
                    tab: .available,
                    title: availableTotal > 0 ? "可选课程 (\(availableTotal))" : "可选课程"
                )
                tabButton(
                    tab: .enrolled,
                    title: "已选课程 (\(enrolled.count))"
                )
            }
            .padding(3)
            .background(theme.surfaceHighest, in: Capsule())
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            .listRowBackground(Color.clear)
        }
    }

    private func tabButton(tab: XkTab, title: String) -> some View {
        let selected = activeTab == tab
        return Button {
            guard activeTab != tab else { return }
            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                activeTab = tab
            }
        } label: {
            Text(title)
                .font(.subheadline.weight(selected ? .semibold : .medium))
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundStyle(selected ? theme.onAccent : theme.ink)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .frame(minHeight: 36)
                .contentShape(Capsule())
        }
        .buttonStyle(GokePressStyle(scale: selected ? 1.0 : 0.96))
        .background {
            if selected {
                Capsule()
                    .fill(theme.accent)
                    .matchedGeometryEffect(id: "xkActiveTab", in: xkNamespace)
            }
        }
    }

    private var searchAndFilterSection: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: GokeSpacing.sm) {
                    ForEach(XkSearchKey.allCases) { key in
                        let selected = searchKey == key
                        Button {
                            guard searchKey != key else { return }
                            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                                searchKey = key
                            }
                        } label: {
                            Text(key.title)
                                .font(.caption.weight(selected ? .semibold : .medium))
                                .foregroundStyle(selected ? theme.onAccent : theme.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background {
                                    if selected {
                                        Capsule()
                                            .fill(theme.accent)
                                            .matchedGeometryEffect(id: "xkSearchKey", in: xkNamespace)
                                    } else {
                                        Capsule()
                                            .fill(theme.surfaceHighest)
                                    }
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(GokePressStyle(scale: selected ? 1.0 : 0.96))
                    }

                    Divider()
                        .frame(height: 18)

                    filterToggleChip(title: "仅看有余量", active: onlyHasSeats) {
                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                            onlyHasSeats.toggle()
                        }
                    }

                    filterToggleChip(title: "仅看无冲突", active: onlyNoConflict) {
                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                            onlyNoConflict.toggle()
                        }
                    }
                }
            }
            .listRowInsets(EdgeInsets(top: 2, leading: 16, bottom: 2, trailing: 16))
        }
        .listRowBackground(theme.surface)
    }

    private func filterToggleChip(title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if active {
                    Image(systemName: "checkmark")
                        .font(.caption2.weight(.bold))
                }
                Text(title)
                    .font(.caption.weight(active ? .semibold : .medium))
            }
            .foregroundStyle(active ? theme.onAccent : theme.ink)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(active ? theme.accent : theme.surfaceHighest)
            )
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
    }

    private func availableCoursesSection(canDrop: Bool) -> some View {
        Section {
            if visibleAvailable.isEmpty && !isLoading {
                GokeEmptyState(
                    systemImage: "books.vertical",
                    title: "暂无匹配课程",
                    subtitle: "调整搜索或筛选试试"
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(visibleAvailable) { item in
                    courseRow(item, canDrop: canDrop)
                }

                if hasMore {
                    Button {
                        Task { await loadCourseSelection(page: currentPage + 1) }
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Spacer()
                            if isLoadingMore {
                                ProgressView()
                                    .controlSize(.small)
                                Text("加载中…")
                            } else {
                                Text("加载更多")
                            }
                            Spacer()
                        }
                        .font(.subheadline.weight(.medium))
                    }
                    .disabled(isLoadingMore || isLoading)
                }
            }
        }
        .listRowBackground(theme.surface)
    }

    private func enrolledCoursesSection(canDrop: Bool, showHeader: Bool) -> some View {
        Section {
            if enrolled.isEmpty && !isLoading {
                GokeEmptyState(
                    systemImage: "tray",
                    title: "暂无已选课程"
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(enrolled) { item in
                    courseRow(item, canDrop: canDrop)
                }
            }
        } header: {
            if showHeader {
                Text("已选课程（\(enrolled.count)）")
                    .monospacedDigit()
                    .contentTransition(.numericText())
            }
        }
        .listRowBackground(theme.surface)
    }

    private func courseRow(_ item: XkCourseItem, canDrop: Bool) -> some View {
        let isBusy = actionBusyId == item.kcrwdm
        let anyBusy = actionBusyId != nil
        return VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            Button {
                activeSheet = .detail(item)
            } label: {
                VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                    HStack(alignment: .top, spacing: GokeSpacing.sm) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.displayName)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(theme.ink)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)

                            let subtitle = [item.teacher, item.classGroup]
                                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                                .filter { !$0.isEmpty }
                                .joined(separator: " · ")
                            if !subtitle.isEmpty {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundStyle(theme.muted)
                                    .lineLimit(1)
                            }
                        }

                        Spacer(minLength: GokeSpacing.xs)

                        if let credit = item.credit {
                            GokeBadge("\(Self.formatCompactNumber(credit)) 学分", tone: .accent)
                        }
                    }

                    HStack(spacing: GokeSpacing.xs) {
                        if !item.category.isEmpty {
                            GokeBadge(item.category, tone: .muted)
                        }
                        if !item.campusHint.isEmpty {
                            GokeBadge(item.campusHint, tone: .secondary)
                        }
                        if item.hasConflict && !item.enrolled {
                            GokeBadge("时间冲突 \(item.conflicts.count)", tone: .error)
                        }
                    }

                    if item.scheduleSummary.isEmpty {
                        Text("上课时间待定")
                            .font(.caption)
                            .foregroundStyle(theme.muted)
                    } else {
                        ForEach(Array(item.scheduleSummary.enumerated()), id: \.offset) { _, summary in
                            Text(Self.formatScheduleSummary(summary))
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                                .lineLimit(2)
                        }
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            HStack(alignment: .center, spacing: GokeSpacing.sm) {
                if let cap = item.capacity, let count = item.enrolledCount {
                    let rem = item.remainingSeats ?? max(0, cap - count)
                    Text(item.isFull ? "已满 \(count)/\(cap)" : "余 \(rem) / \(cap)")
                        .font(.caption.weight(.medium))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: rem)
                        .foregroundStyle(item.isFull ? theme.error : theme.success)
                }

                Spacer(minLength: GokeSpacing.xs)

                if item.enrolled {
                    GokeBadge("已选", tone: .accent)
                    GokeMintActionPill(
                        canDrop ? "退选" : "不可退选",
                        style: .destructive,
                        isLoading: isBusy,
                        isDisabled: !canDrop || anyBusy,
                        role: .destructive
                    ) {
                        confirmRequest = ConfirmRequest(item: item, mode: .drop)
                    }
                } else {
                    GokeMintActionPill(
                        "选课",
                        style: .prominent,
                        isLoading: isBusy,
                        isDisabled: item.isFull || anyBusy
                    ) {
                        confirmRequest = ConfirmRequest(item: item, mode: .enroll)
                    }
                }
            }
        }
        .padding(.vertical, 3)
    }

    private var casLoginSheet: some View {
        NavigationStack {
            Form {
                Section("教务账号") {
                    TextField("学号", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.asciiCapable)
                        .textContentType(.username)
                        .foregroundStyle(theme.ink)

                    SecureField("密码", text: $password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .foregroundStyle(theme.ink)
                }
                .listRowBackground(theme.surface)

                Section {
                    Button {
                        activeSheet = nil
                        Task {
                            let trimmedUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !trimmedUser.isEmpty && !password.isEmpty {
                                try? await container.keychain.set(trimmedUser, for: .jxfwAccount)
                                try? await container.keychain.set(password, for: .jxfwPassword)
                                hasCredentials = true
                            }
                            await container.jxfwClient.resetSession()
                            await loadCourseSelection(page: 1)
                        }
                    } label: {
                        Text("登录")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .disabled(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
                }
                .listRowBackground(theme.surface)
            }
            .gokePaperListStyle()
            .navigationTitle("登录教务")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { activeSheet = nil }
                }
            }
        }
        .tint(theme.accent)
    }

    // MARK: - Networking & Data

    private func bootstrap() async {
        if let acc = (try? await container.keychain.get(.jxfwAccount)) ?? nil, !acc.isEmpty {
            username = acc
        }
        if let pwd = (try? await container.keychain.get(.jxfwPassword)) ?? nil, !pwd.isEmpty {
            password = pwd
        }
        hasCredentials = !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty
        if hasCredentials && window == nil {
            await loadCourseSelection(page: 1)
        }
    }

    private func ensureAuthenticated() async throws {
        let authed = await container.jxfwClient.isAuthenticated
        if authed { return }
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else {
            throw JxfwError("请先登录广工统一身份认证。", kind: .session)
        }
        try await container.jxfwClient.login(username: user, password: password)
    }

    private func withSessionRetry<T>(_ op: () async throws -> T) async throws -> T {
        try await ensureAuthenticated()
        do {
            return try await op()
        } catch let err as JxfwError where err.kind == .session {
            let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !user.isEmpty, !password.isEmpty else { throw err }
            try await container.jxfwClient.login(username: user, password: password)
            return try await op()
        }
    }

    private func loadCourseSelection(page: Int) async {
        let targetPage = max(1, page)
        let isRefresh = targetPage == 1
        loadGeneration += 1
        let gen = loadGeneration
        if isRefresh {
            isLoading = true
            isLoadingMore = false
        } else {
            isLoadingMore = true
        }
        errorMessage = nil
        defer {
            if gen == loadGeneration {
                isLoading = false
                isLoadingMore = false
            }
        }

        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let activeSearchKey = searchKey.rawValue
        let client = container.jxfwClient

        do {
            let baseWindow = try await withSessionRetry {
                try await client.fetchXkWindowStatus()
            }
            guard gen == loadGeneration else { return }

            if !baseWindow.isOpen {
                let term = baseWindow.xnxqdm.isEmpty ? JxfwParse.currentTerm(today: .today()) : baseWindow.xnxqdm
                async let plansTask = try? client.fetchXkOpenPlans(term: term)
                async let walletTask: XkCreditWallet? = baseWindow.isCreditWallet ? (try? client.fetchXkCreditWallet()) : nil
                async let enrolledTask = try? client.fetchXkEnrolledCourses(page: 1, rows: 100)

                let (plans, fetchedWallet, enrolledPage) = await (plansTask, walletTask, enrolledTask)
                guard gen == loadGeneration else { return }
                var resolvedWindow = baseWindow
                resolvedWindow.openPlans = plans ?? []

                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    self.window = resolvedWindow
                    self.wallet = fetchedWallet
                    self.enrolled = enrolledPage?.items ?? []
                    self.available = []
                    self.availableTotal = 0
                    self.currentPage = 1
                    self.reachedEnd = true
                    if isRefresh {
                        self.detailCache.removeAll()
                    }
                }
                return
            }

            async let availTask = client.fetchXkAvailableCourses(
                page: targetPage,
                rows: Self.pageSize,
                searchKey: activeSearchKey,
                searchValue: query
            )
            async let enrolledTask: (total: Int, items: [XkCourseItem])? = isRefresh
                ? (try await client.fetchXkEnrolledCourses(page: 1, rows: 100))
                : nil
            async let walletTask: XkCreditWallet? = (isRefresh && baseWindow.isCreditWallet)
                ? (try? client.fetchXkCreditWallet())
                : nil
            async let schedTask: [XkOccupiedSlot]? = (isRefresh && !baseWindow.xnxqdm.isEmpty)
                ? (try? client.fetchXkStudentSchedule(term: baseWindow.xnxqdm))
                : nil

            let ((total, rawAvailable), enrolledPage, fetchedWallet, fetchedOccupied) = try await (
                availTask,
                enrolledTask,
                walletTask,
                schedTask
            )
            guard gen == loadGeneration else { return }

            let rawEnrolled = enrolledPage?.items ?? enrolled
            let enrolledIds = Set(rawEnrolled.map(\.kcrwdm))

            var idsToEnrich = rawAvailable.map(\.kcrwdm)
            if isRefresh {
                idsToEnrich.append(contentsOf: rawEnrolled.map(\.kcrwdm))
            }
            let uniqueIds = Array(Set(idsToEnrich))
            let batchSlots = uniqueIds.isEmpty ? [] : ((try? await client.fetchXkBatchSchedule(kcrwdms: uniqueIds)) ?? [])
            guard gen == loadGeneration else { return }
            let slotsByTask = Dictionary(grouping: batchSlots, by: \.kcrwdm)

            let jxfwOccupied: [XkOccupiedSlot]
            if isRefresh {
                let resolved = fetchedOccupied ?? []
                cachedJxfwOccupied = resolved
                jxfwOccupied = resolved
                detailCache.removeAll()
                syllabusCache.removeAll()
            } else {
                jxfwOccupied = cachedJxfwOccupied
            }

            let localOccupied = localOccupiedSlots()
            let mergedAvailable = JxfwXkParse.mergeCourseData(
                items: rawAvailable,
                enrolledIds: enrolledIds,
                slotsByTask: slotsByTask,
                jxfwOccupied: jxfwOccupied,
                localOccupied: localOccupied
            )
            let mergedEnrolled = isRefresh
                ? JxfwXkParse.mergeCourseData(
                    items: rawEnrolled,
                    enrolledIds: enrolledIds,
                    slotsByTask: slotsByTask,
                    jxfwOccupied: jxfwOccupied,
                    localOccupied: localOccupied
                )
                : enrolled

            let combinedAvailable: [XkCourseItem]
            if isRefresh {
                combinedAvailable = mergedAvailable
            } else {
                var seen = Set(available.map(\.kcrwdm))
                var next = available
                for item in mergedAvailable where seen.insert(item.kcrwdm).inserted {
                    next.append(item)
                }
                combinedAvailable = next
            }

            let didReachEnd = isRefresh
                ? (mergedAvailable.isEmpty || combinedAvailable.count >= total)
                : (mergedAvailable.isEmpty || combinedAvailable.count <= available.count || combinedAvailable.count >= total)

            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                self.window = baseWindow
                if isRefresh {
                    self.wallet = fetchedWallet
                }
                self.available = combinedAvailable
                self.availableTotal = total
                self.currentPage = targetPage
                self.enrolled = mergedEnrolled
                self.reachedEnd = didReachEnd
            }
        } catch {
            guard gen == loadGeneration else { return }
            errorMessage = Self.friendlyErrorMessage(error)
        }
    }

    private func loadCourseDetail(for item: XkCourseItem) async throws -> XkCourseDetail {
        if let cached = detailCache[item.kcrwdm] {
            return cached
        }
        let client = container.jxfwClient
        let term = (window?.xnxqdm.isEmpty == false ? window?.xnxqdm : nil) ?? JxfwParse.currentTerm(today: .today())
        let kcptdm = item.kcptdm.trimmingCharacters(in: .whitespacesAndNewlines)
        let cachedSyllabus = kcptdm.isEmpty ? nil : syllabusCache[kcptdm]

        let detail = try await withSessionRetry {
            async let syllabusPair: (zh: String, en: String) = {
                if kcptdm.isEmpty { return ("", "") }
                if let cachedSyllabus { return cachedSyllabus }
                return (try? await client.fetchXkSyllabus(kcptdm: kcptdm)) ?? ("", "")
            }()

            async let calendarSlots: [XkSessionSlot] = {
                if !item.sessions.isEmpty {
                    return item.sessions
                }
                return (try? await client.fetchXkTeachingCalendar(kcrwdm: item.kcrwdm)) ?? []
            }()

            async let extraMap: [String: XkExtraMeta] = (try? await client.fetchXkTaskMetadata(term: term, courseName: item.name)) ?? [:]

            let ((zh, en), calendar, extras) = await (syllabusPair, calendarSlots, extraMap)
            return XkCourseDetail(
                kcrwdm: item.kcrwdm,
                kcptdm: item.kcptdm,
                zhSyllabus: zh,
                enSyllabus: en,
                calendar: calendar,
                extra: extras[item.kcrwdm]
            )
        }

        if !kcptdm.isEmpty && (!detail.zhSyllabus.isEmpty || !detail.enSyllabus.isEmpty) {
            syllabusCache[kcptdm] = (detail.zhSyllabus, detail.enSyllabus)
        }
        detailCache[item.kcrwdm] = detail
        return detail
    }

    private func performEnroll(_ item: XkCourseItem) async {
        guard actionBusyId == nil else { return }
        actionBusyId = item.kcrwdm
        defer { actionBusyId = nil }

        do {
            let result = try await withSessionRetry {
                try await container.jxfwClient.xkEnroll(kcrwdm: item.kcrwdm, kcmc: item.name)
            }
            switch result {
            case .success:
                actions.showToast(message: "已成功选课：\(item.displayName)")
                await loadCourseSelection(page: 1)
            case .failure(let reason, let kind):
                actions.showToast(message: Self.failureMessage(kind: kind, reason: reason))
            }
        } catch {
            actions.showToast(message: Self.friendlyErrorMessage(error))
        }
    }

    private func performDrop(_ item: XkCourseItem) async {
        guard actionBusyId == nil else { return }
        guard let window, window.isOpen else {
            actions.showToast(message: Self.failureMessage(kind: .OUT_OF_WINDOW, reason: ""))
            return
        }
        guard window.canDrop else {
            actions.showToast(message: Self.failureMessage(kind: .NOT_DROPPABLE, reason: ""))
            return
        }

        actionBusyId = item.kcrwdm
        defer { actionBusyId = nil }

        do {
            let result = try await withSessionRetry {
                try await container.jxfwClient.xkDrop(kcrwdm: item.kcrwdm, kcmc: item.name)
            }
            switch result {
            case .success:
                actions.showToast(message: "已退选：\(item.displayName)")
                await loadCourseSelection(page: 1)
            case .failure(let reason, let kind):
                actions.showToast(message: Self.failureMessage(kind: kind, reason: reason))
            }
        } catch {
            actions.showToast(message: Self.friendlyErrorMessage(error))
        }
    }

    private func localOccupiedSlots() -> [XkOccupiedSlot] {
        let live = container.liveSnapshot
        guard let semId = live.currentSemester?.id else {
            return JxfwXkParse.localCoursesToOccupiedSlots(live.courses)
        }
        return JxfwXkParse.localCoursesToOccupiedSlots(live.courses.filter { $0.semesterId == semId })
    }

    private func confirmationMessage(for req: ConfirmRequest) -> String {
        switch req.mode {
        case .enroll:
            if req.item.hasConflict {
                let conflictText = req.item.conflicts.map(Self.formatConflictLine).joined(separator: "\n")
                return "「\(req.item.displayName)」与已排课程时间冲突：\n\(conflictText)\n\n仍要选修吗？"
            }
            return "选修「\(req.item.displayName)」？"
        case .drop:
            return "退选「\(req.item.displayName)」？名额将立即释放。"
        }
    }

    // MARK: - Formatting

    fileprivate static func formatCompactNumber(_ value: Double) -> String {
        if value.rounded() == value {
            return String(Int(value))
        }
        return String(format: "%.1f", value)
    }

    fileprivate static func weekdayLabel(_ dayOfWeek: Int) -> String {
        switch dayOfWeek {
        case 1: "周一"
        case 2: "周二"
        case 3: "周三"
        case 4: "周四"
        case 5: "周五"
        case 6: "周六"
        case 7: "周日"
        default: ""
        }
    }

    fileprivate static func formatPeriods(_ periods: [Int]) -> String {
        let sorted = Array(Set(periods.filter { $0 > 0 })).sorted()
        guard !sorted.isEmpty else { return "" }
        let isContiguous = zip(sorted, sorted.dropFirst()).allSatisfy { $0.1 == $0.0 + 1 }
        if isContiguous && sorted.count > 1 {
            return "\(sorted.first!)-\(sorted.last!)"
        }
        return sorted.map(String.init).joined(separator: ",")
    }

    fileprivate static func formatScheduleSummary(_ summary: XkScheduleSummary) -> String {
        var parts: [String] = []
        let weekday = weekdayLabel(summary.dayOfWeek)
        let periods = formatPeriods(summary.periods)
        let timeStr = [weekday, periods.isEmpty ? "" : "第\(periods)节"]
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        if !timeStr.isEmpty { parts.append(timeStr) }
        let weeks = JxfwXkParse.formatWeeksRange(summary.weeks)
        if !weeks.isEmpty { parts.append(weeks) }
        if !summary.room.isEmpty { parts.append(summary.room) }
        if !summary.campusHint.isEmpty && !summary.room.contains(summary.campusHint) {
            parts.append(summary.campusHint)
        }
        return parts.joined(separator: " · ")
    }

    fileprivate static func formatConflictLine(_ conflict: XkConflict) -> String {
        let weekday = weekdayLabel(conflict.dayOfWeek)
        let periods = formatPeriods(conflict.periods)
        let weeks = JxfwXkParse.formatWeeksRange(conflict.weeks)
        let sourceLabel = conflict.source == .JXFW ? "教务已排" : "本地课表"
        return "\(conflict.courseName)（\(weekday) 第\(periods)节 · \(weeks) · \(sourceLabel)）"
    }

    private static func failureMessage(kind: XkFailureKind, reason: String) -> String {
        switch kind {
        case .FULL: "名额已满，选课未成功。"
        case .CONFLICT: "上课时间存在冲突，教务系统拒绝了本次选课。"
        case .CREDIT_LIMIT: "超出学分上限，无法继续选课。"
        case .OUT_OF_WINDOW: "当前不在选课开放时段内。"
        case .ALREADY_ENROLLED: "你已选修过该课程。"
        case .NOT_DROPPABLE: "当前选课轮次不允许退选。"
        case .OTHER: reason.isEmpty ? "教务系统返回失败，请稍后重试。" : reason
        }
    }

    private static func friendlyErrorMessage(_ error: Error) -> String {
        if let err = error as? JxfwError {
            switch err.kind {
            case .credentials: return "学号或密码错误：\(err.message)"
            case .captcha: return "统一认证触发了图形验证码，请先在浏览器完成一次登录后重试。"
            case .sms: return "统一认证需要短信二次验证，请先在校园网或浏览器完成认证。"
            case .session: return "教务会话已过期，请重新登录。"
            case .network: return "网络连接失败（请检查校园网或 VPN）：\(err.message)"
            case .unsupported: return err.message.isEmpty ? CampusAdapterErrorMessages.unsupported : err.message
            case .protocolError, .other: return err.message
            }
        }
        return error.localizedDescription
    }
}

// MARK: - Course Detail Sheet

private struct XkCourseDetailSheet: View {
    let item: XkCourseItem
    let canDrop: Bool
    let windowOpen: Bool
    let busy: Bool
    let loadDetail: (XkCourseItem) async throws -> XkCourseDetail
    let onEnroll: () -> Void
    let onDrop: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.gokeTheme) private var theme

    @State private var isLoading: Bool = true
    @State private var detail: XkCourseDetail?

    private var summaries: [XkScheduleSummary] {
        let calendar = detail?.calendar ?? []
        if !calendar.isEmpty {
            return JxfwXkParse.summarizeSchedule(calendar)
        }
        return item.scheduleSummary
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                        Text(item.displayName)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(theme.ink)

                        if let enName = detail?.extra?.englishName, !enName.isEmpty {
                            Text(enName)
                                .font(.subheadline)
                                .foregroundStyle(theme.muted)
                        }

                        HStack(spacing: GokeSpacing.xs) {
                            if let credit = item.credit {
                                GokeBadge("\(CourseSelectionView.formatCompactNumber(credit)) 学分", tone: .accent)
                            }
                            let code = (detail?.extra?.courseCode.isEmpty == false ? detail?.extra?.courseCode : nil) ?? item.kcdm
                            if !code.isEmpty {
                                GokeBadge(code, tone: .muted)
                            }
                            if let hours = detail?.extra?.plannedHours, !hours.isEmpty {
                                GokeBadge("\(hours) 学时", tone: .muted)
                            }
                        }
                        .padding(.top, 2)
                    }
                    .padding(.vertical, 4)

                    if !item.teacher.isEmpty {
                        LabeledContent("任课教师", value: item.teacher)
                    }
                    let classGroup = (detail?.extra?.classGroup.isEmpty == false ? detail?.extra?.classGroup : nil) ?? item.classGroup
                    if !classGroup.isEmpty {
                        LabeledContent("教学班", value: classGroup)
                    }
                    let platform = (detail?.extra?.platformName.isEmpty == false ? detail?.extra?.platformName : nil) ?? item.category
                    if !platform.isEmpty {
                        LabeledContent("课程归属", value: platform)
                    }
                    if let studyMode = detail?.extra?.studyMode, !studyMode.isEmpty {
                        LabeledContent("修读方式", value: studyMode)
                    }
                    if let gradingMode = detail?.extra?.gradingMode, !gradingMode.isEmpty {
                        LabeledContent("考核方式", value: gradingMode)
                    }
                }
                .listRowBackground(theme.surface)

                if item.hasConflict && !item.enrolled {
                    Section("时间冲突预警") {
                        ForEach(Array(item.conflicts.enumerated()), id: \.offset) { _, conflict in
                            Label {
                                Text(CourseSelectionView.formatConflictLine(conflict))
                                    .font(.subheadline)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.error)
                            } icon: {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(theme.error)
                            }
                        }
                    }
                    .listRowBackground(theme.errorContainer)
                }

                Section("上课安排") {
                    if summaries.isEmpty {
                        Text("上课时间待定")
                            .font(.subheadline)
                            .foregroundStyle(theme.muted)
                    } else {
                        ForEach(Array(summaries.enumerated()), id: \.offset) { _, summary in
                            Text(CourseSelectionView.formatScheduleSummary(summary))
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                        }
                    }
                }
                .listRowBackground(theme.surface)

                Section("课程简介") {
                    if isLoading {
                        HStack(spacing: GokeSpacing.sm) {
                            ProgressView()
                                .controlSize(.small)
                            Text("加载中…")
                                .font(.subheadline)
                                .foregroundStyle(theme.muted)
                        }
                    } else {
                        let zh = detail?.zhSyllabus ?? ""
                        let en = detail?.enSyllabus ?? ""
                        if !zh.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("中文简介")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(theme.muted)
                                Text(zh)
                                    .font(.subheadline)
                                    .foregroundStyle(theme.ink)
                            }
                            .padding(.vertical, 2)
                        }
                        if !en.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("English Syllabus")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(theme.muted)
                                Text(en)
                                    .font(.subheadline)
                                    .foregroundStyle(theme.muted)
                            }
                            .padding(.vertical, 2)
                        }
                        if zh.isEmpty && en.isEmpty {
                            Text("暂无课程简介")
                                .font(.subheadline)
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
                .listRowBackground(theme.surface)

                if windowOpen {
                    Section {
                        if item.enrolled {
                            Button(role: .destructive, action: onDrop) {
                                Text(canDrop ? "退选该课程" : "当前轮次禁止退选")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                            }
                            .disabled(!canDrop || busy)
                        } else {
                            Button(action: onEnroll) {
                                Text(item.isFull ? "名额已满" : "立即选课")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                            }
                            .disabled(item.isFull || busy)
                        }
                    }
                    .listRowBackground(theme.surface)
                }
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle("课程详情")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .tint(theme.accent)
        .task(id: item.kcrwdm) {
            isLoading = true
            detail = try? await loadDetail(item)
            isLoading = false
        }
    }
}
