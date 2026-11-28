import Foundation
import GokeCore
import GokeShared
import SwiftUI

@MainActor
private final class MeCampusSummaryCacheBox {
    var hasDecodedGrades: Bool = false
    var lastGradesData: Data?
    var weightedGpa: Double?

    var hasDecodedTzcs: Bool = false
    var lastTzcsData: Data?
    var physicalScore: String?
}

/// Root hub for the `Me` tab following Android's "素纸 Goke" frameless paper aesthetic:
/// 64pt left-aligned paper header with collapsible search, flat `CampusHubCard`,
/// 4 frameless sections with calm monochrome SF Symbols, and live trailing statuses.
struct MeView: View {
    private static let gradesCacheKey = "app.goke.ios.campus.gradesCache.v1"
    private static let tzcsCacheKey = "app.goke.ios.campus.tzcsCache.v1"
    private static let calendarSourceIdsKey = "app.goke.ios.calendarSync.sourceIds.v1"
    private static let calendarSyncEnabledKey = "app.goke.ios.calendarSync.enabled.v1"

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(SyncStateCoordinator.self) private var syncCoordinator
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var isSearching: Bool = false
    @State private var searchQuery: String = ""
    @FocusState private var isSearchFieldFocused: Bool

    @State private var jxfwStudentId: String?
    @State private var cachedWeightedGpa: Double?
    @State private var cachedPhysicalScore: String?
    @State private var campusSummaryCache = MeCampusSummaryCacheBox()
    @State private var reminderAuth: ReminderAuthorizationSnapshot = .denied
    @State private var calendarAuthState: EventKitAuthorizationState = .notDetermined
    @State private var calendarSyncEnabled: Bool = false
    @State private var selectedCalendarCount: Int = 0
    @State private var hotWaterClient: HotWaterClient?
    @State private var updateManager = AppUpdateManager.shared

    init() {}

    private var live: AppSnapshot { container.liveSnapshot }
    private var currentSemester: Semester? { live.currentSemester }

    private var currentWeekText: String? {
        guard let sem = currentSemester else { return nil }
        let cal = SemesterCalendar(semester: sem)
        let week = cal.weekOf(.today())
        if week < 1 {
            return "开课前"
        } else if week > sem.totalWeeks {
            return "学期已结束"
        }
        return "第 \(week) 周"
    }

    private var trashCount: Int {
        container.deletedRecordCount
    }

    private var activeThemeName: String {
        switch settingsStore.settings.themeSource {
        case .CUSTOM:
            return "自定义"
        case .PRESET, .DYNAMIC:
            return GokeThemePreset.from(key: settingsStore.settings.preset).displayName
        }
    }

    private var enabledReminderCount: Int {
        let s = settingsStore.settings
        var count = 0
        if s.remindCourse { count += 1 }
        if s.remindExam { count += 1 }
        if s.remindTask { count += 1 }
        if s.remindEvent { count += 1 }
        if s.dailyDigest { count += 1 }
        return count
    }

    private var reminderStatusText: String {
        let count = enabledReminderCount
        if count == 0 {
            return "已关闭"
        }
        if !reminderAuth.isAuthorized {
            return "\(count) 项待授权"
        }
        return "已开启 \(count) 项"
    }

    private var calendarSyncStatusText: String {
        switch calendarAuthState {
        case .fullAccess:
            guard calendarSyncEnabled else { return "已关闭" }
            if selectedCalendarCount > 0 {
                return "已选 \(selectedCalendarCount) 个"
            }
            return "已开启"
        case .writeOnly:
            return "仅写入"
        case .denied, .restricted:
            return "未授权"
        case .notDetermined, .unavailable:
            return "未开启"
        }
    }

    private var hotWaterStatusInfo: (text: String, tint: Color?) {
        guard let state = hotWaterClient?.state, state.isBound else { return ("未绑定", nil) }
        if state.phase == .valveOpen {
            return ("出水中", theme.accent)
        }
        if state.isArrearage {
            return ("欠费 ¥\(String(format: "%.2f", state.balanceYuan))", theme.error)
        }
        return ("¥\(String(format: "%.2f", state.balanceYuan))", state.balanceYuan <= 3.0 ? theme.error : nil)
    }

    private var syncStatusText: String {
        if syncCoordinator.isSyncing {
            return "同步中"
        }
        if !syncCoordinator.isSignedIn {
            return "未登录"
        }
        if syncCoordinator.needsRelogin {
            return "需重登"
        }
        if syncCoordinator.lastError != nil {
            return "有异常"
        }
        return "已连接"
    }

    private var trimmedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var searchMatches: [MeSettingsSearchItem] {
        MeSettingsSearchCatalog.search(query: trimmedSearchQuery)
    }

    var body: some View {
        VStack(spacing: 0) {
            paperHeader

            if isSearching {
                inlineSearchBar
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.bottom, GokeSpacing.sm)
                    .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            }

            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 20) {
                        if isSearching && !trimmedSearchQuery.isEmpty {
                            searchResultsContent
                        } else {
                            campusHubCard
                                .id("me.top")
                            campusSection
                            preferencesSection
                            syncAndDataSection
                            moreSection
                        }
                    }
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.top, 2)
                    .padding(.bottom, 108)
                }
                .scrollDismissesKeyboard(.interactively)
                .refreshable {
                    await refreshLiveStatuses()
                    await triggerManualSyncIfSignedIn()
                }
                .onChange(of: router.reselectCount(for: .me)) { _, _ in
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        proxy.scrollTo("me.top", anchor: .top)
                    }
                }
            }
        }
        .background(theme.paper.ignoresSafeArea())
        #if os(iOS)
        .toolbar(.hidden, for: .navigationBar)
        #endif
        .task {
            await refreshLiveStatuses()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await refreshLiveStatuses() }
            }
        }
        .onChange(of: router.mePath.isEmpty) { _, isAtRoot in
            if isAtRoot {
                Task { await refreshLiveStatuses() }
            }
        }
        .onChange(of: router.selectedTab) { _, newTab in
            if newTab == .me && router.mePath.isEmpty {
                Task { await refreshLiveStatuses() }
            }
        }
    }

    // MARK: - 64pt Left-Aligned Paper Header & Inline Search Bar

    private var paperHeader: some View {
        HStack(spacing: GokeSpacing.sm) {
            Text("我的")
                .font(GokeTypography.titleLarge)
                .foregroundStyle(theme.ink)
                .lineLimit(1)

            Spacer(minLength: GokeSpacing.sm)

            Button {
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    isSearching.toggle()
                    if isSearching {
                        isSearchFieldFocused = true
                    } else {
                        searchQuery = ""
                        isSearchFieldFocused = false
                    }
                }
            } label: {
                Image(systemName: isSearching ? "xmark" : "magnifyingglass")
                    .font(.system(size: 19, weight: .medium))
                    .foregroundStyle(theme.ink)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.mintCompact)
            .accessibilityLabel(isSearching ? "关闭搜索" : "搜索设置")
        }
        .frame(height: 64)
        .padding(.leading, GokeSpacing.lg)
        .padding(.trailing, GokeSpacing.sm)
    }

    private var inlineSearchBar: some View {
        HStack(spacing: GokeSpacing.sm) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(theme.muted)

            TextField("搜索设置、功能或关键词", text: $searchQuery)
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.ink)
                .focused($isSearchFieldFocused)
                .submitLabel(.search)
                .onSubmit {
                    isSearchFieldFocused = false
                }

            if !searchQuery.isEmpty {
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("清空搜索")
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, searchQuery.isEmpty ? 14 : 0)
        .frame(height: 44)
        .background(theme.surfaceHighest, in: Capsule())
    }

    // MARK: - Search Results

    @ViewBuilder
    private var searchResultsContent: some View {
        if searchMatches.isEmpty {
            GokeEmptyState(
                systemImage: "magnifyingglass",
                title: "没有找到相关设置",
                subtitle: "换个关键词试试",
                primaryActionTitle: "清空搜索",
                onPrimaryAction: {
                    searchQuery = ""
                }
            )
            .frame(maxWidth: .infinity)
            .padding(.top, 36)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                GokeSectionHeader("搜索结果", count: searchMatches.count)
                    .padding(.horizontal, 4)

                ForEach(searchMatches) { item in
                    Button {
                        isSearchFieldFocused = false
                        router.push(item.route, in: .me)
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: item.systemImage)
                                .font(.system(size: 18, weight: .regular))
                                .imageScale(.medium)
                                .symbolRenderingMode(.monochrome)
                                .foregroundStyle(theme.muted)
                                .frame(width: 22, height: 24, alignment: .center)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(highlightedText(item.title, query: trimmedSearchQuery))
                                    .font(GokeTypography.bodyLarge)
                                    .foregroundStyle(theme.ink)
                                let metaText = item.subtitle.isEmpty ? item.section : "\(item.section) · \(item.subtitle)"
                                Text(highlightedText(metaText, query: trimmedSearchQuery))
                                    .font(GokeTypography.labelMedium)
                                    .foregroundStyle(theme.muted)
                                    .lineLimit(1)
                            }

                            Spacer(minLength: GokeSpacing.sm)

                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(theme.muted.opacity(0.55))
                        }
                        .padding(.horizontal, 4)
                        .padding(.vertical, 11)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }
            }
        }
    }

    private func highlightedText(_ text: String, query: String) -> AttributedString {
        var attributed = AttributedString(text)
        let tokens = query
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
            .filter { !$0.isEmpty }
        guard !tokens.isEmpty else { return attributed }

        for token in tokens {
            var searchRange = attributed.startIndex..<attributed.endIndex
            while let range = attributed[searchRange].range(of: token, options: [.caseInsensitive, .diacriticInsensitive]) {
                attributed[range].foregroundColor = theme.accent
                attributed[range].backgroundColor = theme.accentContainer
                if range.upperBound < attributed.endIndex {
                    searchRange = range.upperBound..<attributed.endIndex
                } else {
                    break
                }
            }
        }
        return attributed
    }

    // MARK: - Top CampusHubCard (Flat Surface on Paper)

    private var campusHubCard: some View {
        Button {
            router.push(.jxfw, in: .me)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(theme.accentContainer)
                        .frame(width: 44, height: 44)
                    Image(systemName: "graduationcap.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(theme.onAccentContainer)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(campusHubTitle)
                        .font(GokeTypography.titleMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)

                    if let subtitle = campusHubSubtitle {
                        Text(subtitle)
                            .font(GokeTypography.bodyMedium)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Spacer(minLength: 4)

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(theme.muted.opacity(0.65))
            }
            .padding(GokeSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
        }
        .buttonStyle(.mintRow)
    }

    private var campusHubTitle: String {
        if let sid = jxfwStudentId, !sid.isEmpty {
            return "学号 \(sid)"
        }
        return "绑定广工教务"
    }

    private var campusHubSubtitle: String? {
        if let sid = jxfwStudentId, !sid.isEmpty {
            if let semName = currentSemester?.name, !semName.isEmpty {
                if let week = currentWeekText {
                    return "\(semName) · \(week)"
                }
                return semName
            }
            return currentWeekText ?? "管理课表与考试"
        }
        return "同步课表、考试与成绩"
    }

    // MARK: - 4 Clean Android Frameless Paper Sections

    private var campusSection: some View {
        paperSection("校园服务") {
            paperNavRow(
                "成绩与绩点",
                systemImage: "chart.bar.doc.horizontal",
                detail: cachedWeightedGpa.map { String(format: "均绩 %.2f", $0) },
                route: .grades
            )
            paperNavRow(
                "体测成绩",
                systemImage: "figure.run",
                detail: cachedPhysicalScore.map { "\($0) 分" },
                route: .physicalTest
            )
            paperNavRow(
                "空教室",
                systemImage: "building.2",
                route: .classrooms
            )
            paperNavRow(
                "选课",
                systemImage: "books.vertical",
                route: .courseSelection
            )
            let unreadNotices = JxfwNoticesStore.shared.activeUnreadCount
            paperNavRow(
                "教务通知",
                systemImage: "bell.badge",
                detail: unreadNotices > 0 ? "\(unreadNotices) 条未读" : nil,
                detailColor: unreadNotices > 0 ? theme.accent : nil,
                route: .jxfwNotices
            )
            paperNavRow(
                "校园资料与快传",
                systemImage: "folder",
                route: .campusResources()
            )
            let hw = hotWaterStatusInfo
            paperNavRow(
                "智慧热水",
                systemImage: "drop",
                detail: hw.text,
                detailColor: hw.tint,
                route: .hotWater()
            )
        }
    }

    private var preferencesSection: some View {
        paperSection("偏好设置") {
            paperNavRow(
                "外观",
                systemImage: "paintpalette",
                detail: activeThemeName,
                route: .appearance
            )
            paperNavRow(
                "桌面小组件",
                systemImage: "square.grid.2x2",
                route: .widgetsGallery
            )
            paperNavRow(
                "显示与交互",
                systemImage: "slider.horizontal.3",
                route: .preferences
            )
            paperNavRow(
                "提醒与通知",
                systemImage: "bell",
                detail: reminderStatusText,
                route: .reminders
            )
            paperNavRow(
                "课表与学期",
                systemImage: "tablecells",
                route: .editTimetable
            )
        }
    }

    private var syncAndDataSection: some View {
        paperSection("同步与数据") {
            paperNavRow(
                "账号与同步",
                systemImage: "icloud",
                detail: syncStatusText,
                route: .sync
            )
            paperNavRow(
                "系统日历同步",
                systemImage: "calendar.badge.clock",
                detail: calendarSyncStatusText,
                route: .calendarSync
            )
            paperNavRow(
                "数据与备份",
                systemImage: "arrow.up.arrow.down",
                route: .dataManagement
            )
            paperNavRow(
                "回收站",
                systemImage: "trash",
                detail: trashCount > 0 ? "\(trashCount)" : nil,
                route: .trash
            )
            paperNavRow(
                "操作历史",
                systemImage: "clock.arrow.circlepath",
                route: .history
            )
        }
    }

    private var moreSection: some View {
        paperSection("更多") {
            Button {
                if updateManager.availableUpdate != nil {
                    updateManager.presentUpdateSheet()
                } else {
                    Task {
                        let result = await updateManager.checkManually()
                        switch result {
                        case .upToDate:
                            actions.showToast(message: "当前已是最新版本")
                        case .failed(let message):
                            actions.showToast(message: message)
                        case .idle, .checking, .available:
                            break
                        }
                    }
                }
            } label: {
                HStack(spacing: 14) {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 18, weight: .regular))
                        .imageScale(.medium)
                        .symbolRenderingMode(.monochrome)
                        .foregroundStyle(updateManager.hasUnreadUpdate ? theme.accent : theme.muted)
                        .frame(width: 22, height: 24, alignment: .center)

                    Text("检查更新")
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)

                    Spacer(minLength: GokeSpacing.sm)

                    if updateManager.isChecking {
                        ProgressView()
                            .controlSize(.small)
                    } else if let update = updateManager.availableUpdate {
                        GokeBadge("v\(update.version)", tone: .accent)
                    } else {
                        Text(GokeAppVersion.shortLabel)
                            .font(GokeTypography.bodyMedium)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }

                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(theme.muted.opacity(0.55))
                }
                .padding(.horizontal, 4)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)
            .disabled(updateManager.isChecking)

            Button {
                router.push(.about, in: .me)
            } label: {
                HStack(spacing: 14) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 18, weight: .regular))
                        .imageScale(.medium)
                        .symbolRenderingMode(.monochrome)
                        .foregroundStyle(theme.muted)
                        .frame(width: 22, height: 24, alignment: .center)

                    Text("关于")
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)

                    Spacer(minLength: GokeSpacing.sm)

                    Text(GokeAppVersion.shortLabel)
                        .font(GokeTypography.bodyMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)

                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(theme.muted.opacity(0.55))
                }
                .padding(.horizontal, 4)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)
        }
    }

    private func paperSection<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 4)
                .padding(.bottom, 2)
                .accessibilityAddTraits(.isHeader)

            VStack(spacing: 0) {
                content()
            }
        }
    }

    private func paperNavRow(
        _ title: String,
        systemImage: String,
        detail: String? = nil,
        detailColor: Color? = nil,
        route: GokeRoute
    ) -> some View {
        Button {
            router.push(route, in: .me)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .regular))
                    .imageScale(.medium)
                    .symbolRenderingMode(.monochrome)
                    .foregroundStyle(theme.muted)
                    .frame(width: 22, height: 24, alignment: .center)

                Text(title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)

                Spacer(minLength: GokeSpacing.sm)

                if let detail {
                    Text(detail)
                        .font(GokeTypography.bodyMedium)
                        .monospacedDigit()
                        .foregroundStyle(detailColor ?? theme.muted)
                        .lineLimit(1)
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(theme.muted.opacity(0.55))
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintRow)
    }

    // MARK: - Live Status Refresh

    private func refreshLiveStatuses() async {
        let defaults = container.appGroup.userDefaults
        let gradesData = defaults.data(forKey: Self.gradesCacheKey)
        let tzcsData = defaults.data(forKey: Self.tzcsCacheKey)
        let savedIds = defaults.stringArray(forKey: Self.calendarSourceIdsKey) ?? []
        let calendarSyncFlag: Bool = if defaults.object(forKey: Self.calendarSyncEnabledKey) != nil {
            defaults.bool(forKey: Self.calendarSyncEnabledKey)
        } else {
            !savedIds.isEmpty
        }

        let needsGradesDecode = !campusSummaryCache.hasDecodedGrades || campusSummaryCache.lastGradesData != gradesData
        let needsTzcsDecode = !campusSummaryCache.hasDecodedTzcs || campusSummaryCache.lastTzcsData != tzcsData
        let existingGpa = campusSummaryCache.weightedGpa
        let existingPhysicalScore = campusSummaryCache.physicalScore

        let summaryTask: Task<(gpa: Double?, physicalScore: String?), Never>? = (needsGradesDecode || needsTzcsDecode)
            ? Task.detached(priority: .utility) {
                let gpa: Double? = if needsGradesDecode {
                    if let data = gradesData,
                       let decoded = try? MintJSON.makeDecoder().decode([JxfwGrade].self, from: data),
                       !decoded.isEmpty {
                        JxfwParse.summarizeGrades(decoded).weightedGpa
                    } else {
                        nil
                    }
                } else {
                    existingGpa
                }

                let score: String? = if needsTzcsDecode {
                    if let data = tzcsData,
                       let decoded = try? MintJSON.makeDecoder().decode([TzcsYearReport].self, from: data),
                       let latest = TzcsParse.summarizeReports(decoded).latestReport,
                       !latest.totalScore.isEmpty {
                        latest.totalScore
                    } else {
                        nil
                    }
                } else {
                    existingPhysicalScore
                }

                return (gpa, score)
            }
            : nil

        let keychain = container.keychain
        let scheduler = container.reminderScheduler
        let ekBridge = container.eventKitBridge
        let hwClient = hotWaterClient ?? HotWaterClient.shared
        if hotWaterClient == nil {
            hotWaterClient = hwClient
        }
        JxfwNoticesStore.shared.hydrate()

        async let savedAccountTask: String? = (try? keychain.get(.jxfwAccount)) ?? nil
        async let hotWaterHydrate: Void = hwClient.ensureHydrated()
        async let reminderAuthTask = scheduler.authorizationStatus()
        async let calendarAuthTask = ekBridge.authorizationStatus()

        let savedAccount = await savedAccountTask
        let trimmedAccount = savedAccount?.trimmingCharacters(in: .whitespacesAndNewlines)
        jxfwStudentId = (trimmedAccount?.isEmpty == false) ? trimmedAccount : nil

        if let summaryTask {
            let summary = await summaryTask.value
            if needsGradesDecode {
                campusSummaryCache.hasDecodedGrades = true
                campusSummaryCache.lastGradesData = gradesData
                campusSummaryCache.weightedGpa = summary.gpa
            }
            if needsTzcsDecode {
                campusSummaryCache.hasDecodedTzcs = true
                campusSummaryCache.lastTzcsData = tzcsData
                campusSummaryCache.physicalScore = summary.physicalScore
            }
            cachedWeightedGpa = campusSummaryCache.weightedGpa
            cachedPhysicalScore = campusSummaryCache.physicalScore
        } else {
            cachedWeightedGpa = existingGpa
            cachedPhysicalScore = existingPhysicalScore
        }

        _ = await hotWaterHydrate
        reminderAuth = await reminderAuthTask
        calendarAuthState = await calendarAuthTask
        selectedCalendarCount = savedIds.count
        calendarSyncEnabled = calendarSyncFlag
    }

    private func triggerManualSyncIfSignedIn() async {
        guard syncCoordinator.isSignedIn, !syncCoordinator.isSyncing else { return }
        let outcome = await syncCoordinator.syncNow(silent: false)
        if let outcome {
            actions.showToast(
                message: "同步完成：拉取 \(outcome.pulledCount) · 推送 \(outcome.pushedCount)"
            )
        } else if let err = syncCoordinator.lastError {
            actions.showToast(message: "同步失败：\(err)")
        }
    }
}

// MARK: - Settings Keyword Search Index (Parity with Android SettingsSearch.kt)

private struct MeSettingsSearchItem: Identifiable {
    let id: String
    let title: String
    let section: String
    let subtitle: String
    let keywords: [String]
    let systemImage: String
    let route: GokeRoute
    let isTopLevel: Bool
}

private enum MeSettingsSearchCatalog {
    static func search(query: String) -> [MeSettingsSearchItem] {
        let tokens = query
            .lowercased()
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
            .filter { !$0.isEmpty }
        guard !tokens.isEmpty else { return [] }

        let scored: [(item: MeSettingsSearchItem, score: Int)] = allItems.compactMap { item in
            let titleLower = item.title.lowercased()
            let sectionLower = item.section.lowercased()
            let subtitleLower = item.subtitle.lowercased()
            let kwJoined = item.keywords.joined(separator: " ").lowercased()

            var totalScore = 0
            for token in tokens {
                if titleLower == token {
                    totalScore += 120
                } else if titleLower.hasPrefix(token) {
                    totalScore += 90
                } else if titleLower.contains(token) {
                    totalScore += 65
                } else if kwJoined.contains(token) {
                    totalScore += 45
                } else if subtitleLower.contains(token) || sectionLower.contains(token) {
                    totalScore += 25
                } else {
                    return nil
                }
            }
            if item.isTopLevel {
                totalScore += 5
            }
            return (item, totalScore)
        }

        return scored
            .sorted { lhs, rhs in
                if lhs.score != rhs.score { return lhs.score > rhs.score }
                return lhs.item.title < rhs.item.title
            }
            .map(\.item)
    }

    private static let allItems: [MeSettingsSearchItem] = [
        // Top-level destinations
        MeSettingsSearchItem(
            id: "jxfw",
            title: "教务系统",
            section: "校园服务",
            subtitle: "同步课表、考试与成绩",
            keywords: ["教务", "课表", "导入", "考试", "gdut", "jxfw", "学号", "统一身份认证"],
            systemImage: "graduationcap",
            route: .jxfw,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "courseSelection",
            title: "选课",
            section: "校园服务",
            subtitle: "选课清单与余量",
            keywords: ["选课", "教务选课", "抢课", "公选", "专业课", "xk"],
            systemImage: "books.vertical",
            route: .courseSelection,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "jxfwNotices",
            title: "教务通知",
            section: "校园服务",
            subtitle: "教务处公告与站内消息",
            keywords: ["教务通知", "公告", "通知", "教务处", "站内消息", "jwc", "notice"],
            systemImage: "bell.badge",
            route: .jxfwNotices,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "campusResources",
            title: "校园资料与快传",
            section: "校园服务",
            subtitle: "DropRelay 资料库与文件快传",
            keywords: ["资料", "资料库", "快传", "取件码", "文件", "droprelay", "share", "drop"],
            systemImage: "folder",
            route: .campusResources(),
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "grades",
            title: "成绩与绩点",
            section: "校园服务",
            subtitle: "成绩单与均绩",
            keywords: ["成绩", "绩点", "均绩", "gpa", "学分", "挂科", "排名"],
            systemImage: "chart.bar.doc.horizontal",
            route: .grades,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "physicalTest",
            title: "体测成绩",
            section: "校园服务",
            subtitle: "体测报告与评分",
            keywords: ["体测", "体育", "跑步", "肺活量", "bmi", "tzcs"],
            systemImage: "figure.run",
            route: .physicalTest,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "classrooms",
            title: "空教室",
            section: "校园服务",
            subtitle: "自习教室检索",
            keywords: ["空教室", "自习", "教学楼", "教室", "大学城", "东风路", "龙洞", "揭阳"],
            systemImage: "building.2",
            route: .classrooms,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "hotWater",
            title: "智慧热水",
            section: "校园服务",
            subtitle: "控阀、余额与账单",
            keywords: ["热水", "智慧热水", "水表", "洗澡", "蓝牙", "余额", "欠费", "账单", "hotwater", "ble"],
            systemImage: "drop",
            route: .hotWater(),
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "appearance",
            title: "外观",
            section: "偏好设置",
            subtitle: "深浅色与主题色",
            keywords: ["外观", "主题", "深色", "暗黑", "配色", "颜色", "色板", "hex", "theme", "dark"],
            systemImage: "paintpalette",
            route: .appearance,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "widgets",
            title: "桌面小组件",
            section: "偏好设置",
            subtitle: "小组件与灵动岛",
            keywords: ["小组件", "widget", "桌面", "锁屏", "灵动岛"],
            systemImage: "square.grid.2x2",
            route: .widgetsGallery,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "preferences",
            title: "显示与交互",
            section: "偏好设置",
            subtitle: "课表、手势与流畅度",
            keywords: ["显示与交互", "通用偏好", "偏好", "设置", "教师", "周一", "时间块", "手势", "震动", "音效", "流畅", "省电", "preferences"],
            systemImage: "slider.horizontal.3",
            route: .preferences,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "reminders",
            title: "提醒与通知",
            section: "偏好设置",
            subtitle: "课前、考试与摘要",
            keywords: ["提醒", "通知", "铃声", "提示音", "延后", "snooze", "简报", "摘要", "chime", "notification"],
            systemImage: "bell",
            route: .reminders,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "editTimetable",
            title: "课表与学期",
            section: "偏好设置",
            subtitle: "切换学期与节次",
            keywords: ["学期", "开学日期", "周数", "课表管理", "复制学期"],
            systemImage: "tablecells",
            route: .editTimetable,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "sync",
            title: "账号与同步",
            section: "同步与数据",
            subtitle: "多端同步与 MCP",
            keywords: ["同步", "云同步", "mcp", "服务器", "账号", "登录", "sync"],
            systemImage: "icloud",
            route: .sync,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "calendarSync",
            title: "系统日历同步",
            section: "同步与数据",
            subtitle: "同步到 Apple 日历",
            keywords: ["日历", "apple", "eventkit", "系统日历", "写入日历"],
            systemImage: "calendar.badge.clock",
            route: .calendarSync,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "dataManagement",
            title: "数据与备份",
            section: "同步与数据",
            subtitle: "导入、导出与备份",
            keywords: ["备份", "恢复", "导出", "导入", "json", "ics", "xlsx", "excel", "数据"],
            systemImage: "arrow.up.arrow.down",
            route: .dataManagement,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "trash",
            title: "回收站",
            section: "同步与数据",
            subtitle: "恢复已删除项目",
            keywords: ["回收站", "恢复", "误删", "清空", "trash"],
            systemImage: "trash",
            route: .trash,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "history",
            title: "操作历史",
            section: "同步与数据",
            subtitle: "查看与撤销变更",
            keywords: ["历史", "撤销", "重做", "undo", "redo", "changelog"],
            systemImage: "clock.arrow.circlepath",
            route: .history,
            isTopLevel: true
        ),
        MeSettingsSearchItem(
            id: "about",
            title: "关于",
            section: "更多",
            subtitle: "版本与日志",
            keywords: ["关于", "关于工课", "版本", "内测", "更新", "github", "about"],
            systemImage: "info.circle",
            route: .about,
            isTopLevel: true
        ),

        // Granular sub-setting items
        MeSettingsSearchItem(
            id: "sub.showTeacher",
            title: "课表显示教师",
            section: "显示与交互",
            subtitle: "",
            keywords: ["教师", "老师", "教师姓名", "showteacher"],
            systemImage: "person.text.rectangle",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.weekStart",
            title: "周一作为一周开始",
            section: "显示与交互",
            subtitle: "",
            keywords: ["周一", "周日", "起始日", "每周开始", "weekstart"],
            systemImage: "calendar",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.defaultBlockMinutes",
            title: "默认时间块时长",
            section: "显示与交互",
            subtitle: "",
            keywords: ["时间块", "默认时长", "分钟", "duration"],
            systemImage: "clock",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.swapSwipe",
            title: "交换滑动方向",
            section: "显示与交互",
            subtitle: "左滑完成，右滑改期",
            keywords: ["滑动", "反转", "左滑", "右滑", "手势", "swipe"],
            systemImage: "hand.draw",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.hapticFeedback",
            title: "震动反馈",
            section: "显示与交互",
            subtitle: "长按与滑动轻震",
            keywords: ["震动", "触觉", "振动", "haptic", "vibrate"],
            systemImage: "iphone.radiowaves.left.and.right",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.soundFeedback",
            title: "音效反馈",
            section: "显示与交互",
            subtitle: "完成待办提示音",
            keywords: ["音效", "声音", "提示音", "sound"],
            systemImage: "speaker.wave.2",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.performanceMode",
            title: "显示与流畅度",
            section: "显示与交互",
            subtitle: "自动 / 标准 / 省电",
            keywords: ["性能", "省电", "流畅", "模糊", "动效", "卡顿"],
            systemImage: "gauge.with.dots.needle.33percent",
            route: .preferences,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.customHex",
            title: "HEX 颜色",
            section: "外观",
            subtitle: "自定义主题色",
            keywords: ["自定义颜色", "十六进制", "hex", "色值", "种子色"],
            systemImage: "eyedropper",
            route: .appearance,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.periods",
            title: "节次时间",
            section: "课表与学期",
            subtitle: "上下课时间",
            keywords: ["节次", "作息时间", "上课时间", "下课时间", "periods"],
            systemImage: "clock.badge.checkmark",
            route: .periods(),
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.remindCourse",
            title: "上课提醒",
            section: "提醒与通知",
            subtitle: "课前提前时间",
            keywords: ["课前", "上课提醒", "提前提醒", "多档提醒"],
            systemImage: "bell",
            route: .reminders,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.remindExam",
            title: "考试提醒",
            section: "提醒与通知",
            subtitle: "考前与强提醒",
            keywords: ["考试", "考试提醒", "强提醒", "临考"],
            systemImage: "exclamationmark.bubble",
            route: .reminders,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.dailyDigest",
            title: "每日摘要",
            section: "提醒与通知",
            subtitle: "定时推送当日安排",
            keywords: ["每日摘要", "早间简报", "每日简报", "digest"],
            systemImage: "sun.horizon",
            route: .reminders,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.quickRemind",
            title: "提醒简写",
            section: "提醒与通知",
            subtitle: "快速添加关键词",
            keywords: ["快捷提醒", "关键词", "快速输入", "quickadd"],
            systemImage: "bolt.badge.clock",
            route: .reminders,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.reliability",
            title: "权限与自检",
            section: "提醒与通知",
            subtitle: "通知与系统检查",
            keywords: ["权限", "自检", "诊断", "后台", "可靠性"],
            systemImage: "checkmark.shield",
            route: .reliability,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.autoBackup",
            title: "自动备份",
            section: "数据与备份",
            subtitle: "每日备份与保留份数",
            keywords: ["自动备份", "保留份数", "backup"],
            systemImage: "clock.arrow.2.circlepath",
            route: .dataManagement,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.exportXlsx",
            title: "导出与课表快传",
            section: "数据与备份",
            subtitle: "Excel 与快传链接",
            keywords: ["excel", "xlsx", "课表快传", "快传", "分享课表", "表格"],
            systemImage: "tablecells.badge.ellipsis",
            route: .dataManagement,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.errorLogs",
            title: "异常日志",
            section: "关于",
            subtitle: "诊断与异常记录",
            keywords: ["异常", "日志", "错误日志", "报错", "崩溃", "log"],
            systemImage: "doc.text.magnifyingglass",
            route: .about,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.license",
            title: "开源协议",
            section: "关于",
            subtitle: "协议与开源致谢",
            keywords: ["开源", "协议", "许可证", "致谢", "license", "goke-nc-sa"],
            systemImage: "doc.text",
            route: .about,
            isTopLevel: false
        ),
        MeSettingsSearchItem(
            id: "sub.qqGroup",
            title: "QQ 交流群",
            section: "关于",
            subtitle: "反馈与交流",
            keywords: ["qq", "qq群", "交流群", "反馈", "社区"],
            systemImage: "person.2",
            route: .about,
            isTopLevel: false
        ),
    ]
}
