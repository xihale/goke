import Foundation
import GokeCore
import SwiftUI
import GokeShared
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Shared Campus Cache Keys & Helpers

private enum CampusStorageKeys {
    static let gradesCache = "app.goke.ios.campus.gradesCache.v1"
    static let gradesUpdatedAt = "app.goke.ios.campus.gradesUpdatedAt.v1"
    static let tzcsCache = "app.goke.ios.campus.tzcsCache.v1"
    static let tzcsUpdatedAt = "app.goke.ios.campus.tzcsUpdatedAt.v1"
    static let lastJxfwTerm = "app.goke.ios.campus.lastJxfwTerm.v1"
    static let tzcsCookiesKey: KeychainKey = .custom("campus.tzcs.cookies")
}

@MainActor
enum CampusHelpers {
    static func suggestedTerms(reference: LocalDate = .today()) -> [String] {
        let current = JxfwParse.currentTerm(today: reference)
        guard current.count == 6, let year = Int(current.prefix(4)) else {
            return [current]
        }
        let suffix = String(current.suffix(2))
        if suffix == "01" {
            return ["\(year)01", "\(year - 1)02", "\(year - 1)01", "\(year)02"]
        } else {
            return ["\(year)02", "\(year)01", "\(year - 1)02", "\(year + 1)01"]
        }
    }

    private static let timestampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "MM-dd HH:mm"
        return f
    }()

    static func formatTimestamp(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return timestampFormatter.string(from: date)
    }

    static func jxfwErrorMessage(_ error: Error) -> String {
        if let err = error as? JxfwError {
            switch err.kind {
            case .credentials:
                return "学号或密码错误：\(err.message)"
            case .captcha:
                return "统一认证触发了图形验证码，请先在浏览器完成一次登录后重试。"
            case .sms:
                return "统一认证需要短信二次验证，请先在校园网或浏览器完成认证。"
            case .session:
                return "教务会话已过期，请重新登录。"
            case .network:
                return "网络连接失败（请检查校园网或 VPN）：\(err.message)"
            case .unsupported:
                return err.message.isEmpty ? CampusAdapterErrorMessages.unsupported : err.message
            case .protocolError, .other:
                return err.message
            }
        }
        return error.localizedDescription
    }

    static func isUnexpectedJxfwError(_ error: Error) -> Bool {
        guard let err = error as? JxfwError else { return true }
        switch err.kind {
        case .network, .protocolError, .other:
            return true
        case .credentials, .captcha, .sms, .session, .unsupported:
            return false
        }
    }

    static func isUnexpectedBannerMessage(_ message: String) -> Bool {
        let expectedPrefixes = [
            "学号或密码错误",
            "统一认证触发了图形验证码",
            "统一认证需要短信二次验证",
            "教务会话已过期",
            "当前开源构建",
            "需连接校园网",
            "连接校园网方可使用",
            "体测会话已过期",
        ]
        return !expectedPrefixes.contains { message.hasPrefix($0) }
    }
}

struct CampusUnsupportedBanner: View {
    let message: String

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        Label {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(theme.ink)
        } icon: {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(theme.accent)
        }
        .padding(.vertical, 2)
    }
}

private struct CampusErrorRow: View {
    let message: String
    var allowCopy: Bool = false

    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: GokeSpacing.xs) {
            Label {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(theme.error)
                    .textSelection(.enabled)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(theme.error)
            }
            if allowCopy {
                Button {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = message
                    #endif
                    actions.showToast(message: "已复制报错信息，可粘贴发给开发者")
                } label: {
                    Label("复制报错信息", systemImage: "doc.on.doc")
                        .font(.caption.weight(.medium))
                }
                .buttonStyle(.mintCompact)
            }
        }
    }
}

// MARK: - 1. JxfwImportView (GDUT Academic Portal Timetable & Exams Sync)

public struct JxfwImportView: View {
    private enum SyncMode: String, CaseIterable, Identifiable, Sendable {
        case full
        case weekly

        var id: String { rawValue }
        var title: String {
            switch self {
            case .full: "全量同步"
            case .weekly: "单周校准"
            }
        }
    }

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var rememberCredentials: Bool = true
    @State private var hasSavedCredentials: Bool = false

    @State private var termCode: String = JxfwParse.currentTerm(today: .today())
    @State private var syncMode: SyncMode = .full
    @State private var selectedWeek: Int = 1
    @State private var includeExams: Bool = true

    @State private var isBusy: Bool = false
    @State private var progressMessage: String?
    @State private var errorMessage: String?
    @State private var pendingPlan: JxfwPlan?
    @State private var fetchedWeek1Monday: LocalDate?

    public init() {}

    private var currentSemester: Semester? { container.liveSnapshot.currentSemester }
    private var maxWeeks: Int { max(1, currentSemester?.totalWeeks ?? 20) }

    public var body: some View {
        Form {
            if !container.jxfwClient.isSupported {
                Section {
                    CampusUnsupportedBanner(
                        message: "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包"
                    )
                }
                .listRowBackground(theme.surface)
            }
            credentialsSection
            syncConfigSection

            if let progressMessage {
                Section {
                    HStack(spacing: GokeSpacing.md) {
                        ProgressView()
                            .controlSize(.small)
                        Text(progressMessage)
                            .font(.subheadline)
                            .foregroundStyle(theme.muted)
                    }
                }
                .listRowBackground(theme.surface)
            }

            if let errorMessage {
                Section {
                    CampusErrorRow(
                        message: errorMessage,
                        allowCopy: CampusHelpers.isUnexpectedBannerMessage(errorMessage)
                    )
                }
                .listRowBackground(theme.errorContainer)
            }

            if let plan = pendingPlan {
                planPreviewSection(plan)
            }

            fetchActionSection

            Section("更多") {
                NavigationLink(value: GokeRoute.courseSelection) {
                    Label("选课", systemImage: "books.vertical")
                }
                NavigationLink(value: GokeRoute.jxfwNotices) {
                    Label("教务通知", systemImage: "bell.badge")
                }
            }
            .listRowBackground(theme.surface)
        }
        .gokePaperListStyle()
        .navigationTitle("教务系统")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .task {
            await loadInitialState()
        }
    }

    private var credentialsSection: some View {
        Section {
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

            Toggle("保存密码", isOn: $rememberCredentials)

            if hasSavedCredentials {
                Button("清除已保存密码", role: .destructive) {
                    Task { await clearSavedCredentials() }
                }
            }
        } header: {
            Text("教务账号")
        } footer: {
            Text("密码仅加密保存在本机，直连学校教务。")
        }
        .listRowBackground(theme.surface)
    }

    private var syncConfigSection: some View {
        Section {
            GokeSegmentedPill(
                options: SyncMode.allCases.map { ($0, $0.title) },
                selection: $syncMode,
                height: 38
            )
            .padding(.vertical, 2)

            HStack(spacing: GokeSpacing.sm) {
                TextField("学期代码（如 202601）", text: $termCode)
                    .keyboardType(.numberPad)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)

                Menu {
                    ForEach(CampusHelpers.suggestedTerms(), id: \.self) { code in
                        Button {
                            termCode = code
                        } label: {
                            if termCode == code {
                                Label("\(code)（\(JxfwParse.termLabel(code))）", systemImage: "checkmark")
                            } else {
                                Text("\(code)（\(JxfwParse.termLabel(code))）")
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(JxfwParse.termLabel(termCode))
                            .font(.subheadline)
                            .lineLimit(1)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2)
                    }
                }
            }

            if syncMode == .weekly {
                GokeMintStepper(
                    "目标周次",
                    valueText: "第 \(selectedWeek) 周",
                    value: $selectedWeek,
                    in: 1...maxWeeks
                )
            }

            Toggle("同时同步考试", isOn: $includeExams)
        } header: {
            Text("同步配置")
        } footer: {
            if let sem = currentSemester {
                Text("写入「\(sem.name)」，保留手动课程")
            } else {
                Text("请先创建学期后再导入")
            }
        }
        .listRowBackground(theme.surface)
    }

    private func planPreviewSection(_ plan: JxfwPlan) -> some View {
        Section("变更预览") {
            LabeledContent("同步状态") {
                if plan.isNoop {
                    GokeBadge("已是最新", tone: .success)
                } else {
                    GokeBadge("\(plan.changeSet.changes.count) 项变更", tone: .accent)
                }
            }

            if let skipped = plan.skippedReason {
                Text(skipped)
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
            } else {
                LabeledContent("解析课程") {
                    Text("\(plan.courseCount) 门")
                        .monospacedDigit()
                        .contentTransition(.numericText())
                }
                if includeExams {
                    LabeledContent("解析考试") {
                        Text("\(plan.examCount) 场")
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                }
                if plan.exceptionCount > 0 {
                    LabeledContent("调停课记录") {
                        Text("\(plan.exceptionCount) 条")
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                }

                if !plan.isNoop {
                    LabeledContent("新增") {
                        Text("\(plan.changeSet.addCount)")
                            .monospacedDigit()
                            .contentTransition(.numericText())
                            .foregroundStyle(theme.added)
                    }
                    LabeledContent("更新") {
                        Text("\(plan.changeSet.modifyCount)")
                            .monospacedDigit()
                            .contentTransition(.numericText())
                            .foregroundStyle(theme.onModified)
                    }
                    LabeledContent("移除") {
                        Text("\(plan.changeSet.removeCount)")
                            .monospacedDigit()
                            .contentTransition(.numericText())
                            .foregroundStyle(theme.removed)
                    }
                }

                if let monday = fetchedWeek1Monday {
                    LabeledContent("第 1 周周一") {
                        Text(monday.description)
                            .monospacedDigit()
                    }
                }
            }

            if !plan.isNoop {
                Button {
                    Task { await commitPendingPlan(plan) }
                } label: {
                    Label("确认导入", systemImage: "checkmark.circle.fill")
                        .font(.body.weight(.semibold))
                }
                .disabled(isBusy)
            }
        }
        .listRowBackground(theme.surface)
    }

    private var fetchActionSection: some View {
        Section {
            GokePrimaryActionButton(
                isBusy
                    ? "获取中…"
                    : (syncMode == .full ? "获取课表" : "校准第 \(selectedWeek) 周"),
                systemImage: "arrow.down.circle.fill",
                isLoading: isBusy,
                isDisabled: isBusy
                    || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    || password.isEmpty
            ) {
                Task { await runFetchAndPreview() }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
            .listRowSeparator(.hidden)
        }
    }

    // MARK: - Actions

    private func loadInitialState() async {
        let savedAccount = (try? await container.keychain.get(.jxfwAccount)) ?? nil
        let savedPassword = (try? await container.keychain.get(.jxfwPassword)) ?? nil
        if let savedAccount, !savedAccount.isEmpty {
            username = savedAccount
        }
        if let savedPassword, !savedPassword.isEmpty {
            password = savedPassword
            hasSavedCredentials = true
        }
        if let savedTerm = container.appGroup.userDefaults.string(forKey: CampusStorageKeys.lastJxfwTerm),
           !savedTerm.isEmpty {
            termCode = savedTerm
        }
        if let sem = currentSemester {
            selectedWeek = SemesterCalendar(semester: sem).currentWeek(today: .today())
        }
    }

    private func clearSavedCredentials() async {
        try? await container.keychain.delete(.jxfwAccount)
        try? await container.keychain.delete(.jxfwPassword)
        await container.jxfwClient.resetSession()
        hasSavedCredentials = false
        password = ""
        actions.showToast(message: "已清除本地保存的教务凭据")
    }

    private func runFetchAndPreview() async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let term = termCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else { return }
        guard let semester = currentSemester else {
            errorMessage = "当前没有可用学期，请先在课表管理中创建一个学期。"
            return
        }

        isBusy = true
        errorMessage = nil
        pendingPlan = nil
        fetchedWeek1Monday = nil
        defer {
            isBusy = false
            progressMessage = nil
        }

        do {
            if rememberCredentials {
                try? await container.keychain.set(user, for: .jxfwAccount)
                try? await container.keychain.set(password, for: .jxfwPassword)
                hasSavedCredentials = true
            }
            container.appGroup.userDefaults.set(term, forKey: CampusStorageKeys.lastJxfwTerm)

            progressMessage = "正在登录广工统一身份认证..."
            try await container.jxfwClient.login(username: user, password: password)

            let exams: [JxfwExam]?
            if includeExams {
                progressMessage = "正在获取考试安排..."
                exams = try? await container.jxfwClient.fetchExams(term: term)
            } else {
                exams = nil
            }

            switch syncMode {
            case .full:
                progressMessage = "正在拉取全学期课表..."
                let lessons = try await container.jxfwClient.fetchAllLessons(term: term)

                progressMessage = "正在校准开学第一周日期..."
                let week1 = try? await container.jxfwClient.fetchWeek(term: term, week: 1)
                let monday = week1.flatMap { JxfwParse.firstMonday(from: $0.days) }
                fetchedWeek1Monday = monday

                let ambiguous = JxfwSyncMapper.ambiguousWeeks(lessons).sorted()
                var weekInstances: [Int: [JxfwInstance]] = [:]
                if let w1 = week1, ambiguous.contains(1) {
                    weekInstances[1] = w1.instances
                }
                for w in ambiguous where weekInstances[w] == nil {
                    progressMessage = "正在校准第 \(w) 周多教室/多教师安排..."
                    if let fetched = try? await container.jxfwClient.fetchWeek(term: term, week: w) {
                        weekInstances[w] = fetched.instances
                    }
                }

                let plan = JxfwSyncMapper.fullImport(
                    snapshot: container.snapshot,
                    semester: semester,
                    term: term,
                    lessons: lessons,
                    exams: exams,
                    week1Monday: monday,
                    weekInstances: weekInstances
                )
                pendingPlan = plan

            case .weekly:
                progressMessage = "正在拉取第 \(selectedWeek) 周课表..."
                let weekData = try await container.jxfwClient.fetchWeek(term: term, week: selectedWeek)
                let plan = JxfwSyncMapper.weeklyUpdate(
                    snapshot: container.snapshot,
                    semester: semester,
                    term: term,
                    week: selectedWeek,
                    instances: weekData.instances,
                    exams: exams
                )
                pendingPlan = plan
            }
        } catch {
            errorMessage = CampusHelpers.jxfwErrorMessage(error)
            if CampusHelpers.isUnexpectedJxfwError(error) {
                container.recordError(source: "Jxfw.import", error: error)
            }
        }
    }

    private func commitPendingPlan(_ plan: JxfwPlan) async {
        isBusy = true
        defer { isBusy = false }
        let ok = await actions.commit(plan.changeSet, label: plan.changeSet.description)
        if ok {
            pendingPlan = nil
        }
    }
}

// MARK: - 2. GradesView (Grades & Weighted GPA Simulator)

public struct GradesView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var grades: [JxfwGrade] = []
    @State private var lastUpdatedMillis: Int64?
    @State private var selectedTerm: String? = nil
    @State private var isSimulatorMode: Bool = false
    @State private var excludedCategories: Set<String> = []
    @State private var excludedKeys: Set<String> = []
    @State private var searchText: String = ""

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var showLoginSheet: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String?

    public init() {}

    private var availableTerms: [String] {
        Array(Set(grades.map(\.term).filter { !$0.isEmpty })).sorted(by: >)
    }

    private var termScopedGrades: [JxfwGrade] {
        guard let selectedTerm else { return grades }
        return grades.filter { $0.term == selectedTerm }
    }

    private var categories: [String] {
        JxfwParse.gradeCategories(termScopedGrades)
    }

    private var supersededKeys: Set<String> {
        JxfwParse.supersededGradeKeys(grades)
    }

    private var summary: JxfwGradeSummary {
        JxfwParse.summarizeGrades(
            termScopedGrades,
            excludedKeys: excludedKeys,
            excludedCategories: excludedCategories,
            supersededKeys: supersededKeys
        )
    }

    private var averageScore: Double? {
        let active = JxfwParse.filterGrades(
            termScopedGrades,
            excludedKeys: excludedKeys,
            excludedCategories: excludedCategories
        )
        var weightedSum = 0.0
        var creditSum = 0.0
        var plainSum = 0.0
        var plainCount = 0
        for g in active {
            if JxfwParse.isDeferredGrade(g) || JxfwParse.isUngradedGrade(g) || supersededKeys.contains(g.filterKey) {
                continue
            }
            guard let numeric = Double(g.score.trimmingCharacters(in: .whitespacesAndNewlines)), numeric.isFinite else {
                continue
            }
            plainSum += numeric
            plainCount += 1
            if let c = g.credit, c > 0.0 {
                weightedSum += numeric * c
                creditSum += c
            }
        }
        if creditSum > 0.0 {
            return weightedSum / creditSum
        }
        return plainCount > 0 ? (plainSum / Double(plainCount)) : nil
    }

    private var displayedGrades: [JxfwGrade] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let list = query.isEmpty
            ? termScopedGrades
            : termScopedGrades.filter {
                $0.name.localizedCaseInsensitiveContains(query)
                    || $0.code.localizedCaseInsensitiveContains(query)
                    || $0.category.localizedCaseInsensitiveContains(query)
            }
        return list.sorted { lhs, rhs in
            if lhs.term != rhs.term { return lhs.term > rhs.term }
            return lhs.name < rhs.name
        }
    }

    public var body: some View {
        Group {
            if grades.isEmpty && !isLoading && errorMessage == nil {
                GokeEmptyState(
                    systemImage: "chart.bar.doc.horizontal",
                    title: container.jxfwClient.isSupported ? "暂无成绩" : "开源构建未包含教务适配器",
                    subtitle: container.jxfwClient.isSupported
                        ? "登录教务后获取"
                        : "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包",
                    primaryActionTitle: container.jxfwClient.isSupported ? "登录教务" : nil,
                    onPrimaryAction: container.jxfwClient.isSupported ? {
                        Task { await refreshGrades(allowPrompt: true) }
                    } : nil
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    if !container.jxfwClient.isSupported {
                        Section {
                            CampusUnsupportedBanner(
                                message: "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包"
                            )
                        }
                        .listRowBackground(theme.surface)
                    }

                    if let errorMessage {
                        Section {
                            CampusErrorRow(
                                message: errorMessage,
                                allowCopy: CampusHelpers.isUnexpectedBannerMessage(errorMessage)
                            )
                        }
                        .listRowBackground(theme.errorContainer)
                    }

                    if !grades.isEmpty {
                        gpaSummarySection
                        gradesListSection
                    } else if isLoading {
                        Section {
                            HStack(spacing: GokeSpacing.md) {
                                ProgressView()
                                    .controlSize(.small)
                                Text("加载中…")
                                    .font(.subheadline)
                                    .foregroundStyle(theme.muted)
                            }
                        }
                        .listRowBackground(theme.surface)
                    }
                }
                .listStyle(.insetGrouped)
                .gokePaperListStyle()
                .searchable(text: $searchText, prompt: "搜索课程名、代码或类别")
                .refreshable {
                    await refreshGrades(allowPrompt: true)
                }
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("成绩与绩点")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    showLoginSheet = true
                } label: {
                    Image(systemName: "person.crop.circle")
                }
                .accessibilityLabel("教务登录")

                Button {
                    Task { await refreshGrades(allowPrompt: true) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .gokeSpinningSymbol(isActive: isLoading)
                }
                .disabled(isLoading)
                .accessibilityLabel(isLoading ? "正在刷新成绩" : "刷新成绩")
            }
        }
        .sheet(isPresented: $showLoginSheet) {
            casLoginSheet
                .gokePaperSheet(detents: [.medium, .large])
        }
        .task {
            loadCachedGrades()
            await loadSavedCredentials()
        }
    }

    private var gpaSummarySection: some View {
        Section {
            // Hero GPA Summary
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .lastTextBaseline, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("加权绩点")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(theme.muted)
                        Text(summary.weightedGpa.map { String(format: "%.2f", $0) } ?? "—")
                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                            .monospacedDigit()
                            .contentTransition(.numericText())
                            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: summary.weightedGpa)
                            .foregroundStyle(theme.accent)
                    }

                    Spacer(minLength: 8)

                    if !availableTerms.isEmpty {
                        Menu {
                            Button {
                                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                    selectedTerm = nil
                                }
                            } label: {
                                if selectedTerm == nil {
                                    Label("全部学期", systemImage: "checkmark")
                                } else {
                                    Text("全部学期")
                                }
                            }
                            ForEach(availableTerms, id: \.self) { term in
                                Button {
                                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                        selectedTerm = term
                                    }
                                } label: {
                                    if selectedTerm == term {
                                        Label(JxfwParse.termLabel(term), systemImage: "checkmark")
                                    } else {
                                        Text(JxfwParse.termLabel(term))
                                    }
                                }
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Text(selectedTerm.map(JxfwParse.termLabel) ?? "全部学期")
                                    .font(.caption.weight(.semibold))
                                Image(systemName: "chevron.up.chevron.down")
                                    .font(.caption2.weight(.semibold))
                            }
                            .foregroundStyle(theme.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(theme.surfaceHighest, in: Capsule())
                        }
                    }
                }

                Divider()

                HStack(spacing: 0) {
                    gpaMetricItem(
                        title: "加权均分",
                        value: averageScore.map { String(format: "%.1f", $0) } ?? "—"
                    )
                    Divider()
                        .frame(height: 26)
                    gpaMetricItem(
                        title: "已获/总学分",
                        value: "\(formatDecimal(summary.earnedCredits))/\(formatDecimal(summary.totalCredits))"
                    )
                    Divider()
                        .frame(height: 26)
                    gpaMetricItem(
                        title: "计入课程",
                        value: "\(summary.count) 门"
                    )
                }
            }
            .padding(.vertical, 4)

            Toggle("绩点试算", isOn: $isSimulatorMode.animation(GokeMotion.snappy(reduceMotion: simplifyMotion)))

            if isSimulatorMode && !categories.isEmpty {
                DisclosureGroup("按类别排除（已排除 \(excludedCategories.count)）") {
                    ForEach(categories, id: \.self) { cat in
                        Toggle(cat, isOn: Binding(
                            get: { !excludedCategories.contains(cat) },
                            set: { included in
                                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                    if included {
                                        excludedCategories.remove(cat)
                                    } else {
                                        excludedCategories.insert(cat)
                                    }
                                }
                            }
                        ))
                    }
                }
            }

            if !excludedKeys.isEmpty || !excludedCategories.isEmpty {
                Button("重置筛选") {
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        excludedKeys.removeAll()
                        excludedCategories.removeAll()
                    }
                }
            }
        } footer: {
            if let ts = lastUpdatedMillis, isSimulatorMode {
                Text("更新于 \(CampusHelpers.formatTimestamp(ts)) · 点击课程可临时排除")
            } else if isSimulatorMode {
                Text("点击课程可临时排除")
            } else if let ts = lastUpdatedMillis {
                Text("更新于 \(CampusHelpers.formatTimestamp(ts))")
            }
        }
        .listRowBackground(theme.surface)
    }

    private func gpaMetricItem(title: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundStyle(theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Text(title)
                .font(.caption2)
                .foregroundStyle(theme.muted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private struct IdentifiableGrade: Identifiable {
        let id: String
        let grade: JxfwGrade
    }

    private var identifiableDisplayedGrades: [IdentifiableGrade] {
        var counts: [String: Int] = [:]
        return displayedGrades.map { grade in
            let base = "\(grade.filterKey)|\(grade.examType)|\(grade.status)"
            let index = counts[base, default: 0]
            counts[base] = index + 1
            return IdentifiableGrade(id: index == 0 ? base : "\(base)#\(index)", grade: grade)
        }
    }

    private var gradesListSection: some View {
        Section {
            if displayedGrades.isEmpty {
                GokeEmptyState(
                    systemImage: "magnifyingglass",
                    title: searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? "暂无成绩"
                        : "未找到「\(searchText)」"
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(identifiableDisplayedGrades) { item in
                    let grade = item.grade
                    let isExcluded = excludedKeys.contains(grade.filterKey)
                        || excludedCategories.contains(grade.category)
                        || excludedCategories.contains(grade.studyMode)
                    let isDeferred = JxfwParse.isDeferredGrade(grade)
                    let isUngraded = JxfwParse.isUngradedGrade(grade)
                    let isSuperseded = supersededKeys.contains(grade.filterKey)
                    let passed = JxfwParse.isPassedGrade(grade)
                    let trimmedScore = grade.score.trimmingCharacters(in: .whitespacesAndNewlines)
                    let numericScore = Double(trimmedScore)
                    let isNumericFail = numericScore.map { $0 < 60.0 } ?? false
                    let isFailed = !isDeferred && !isUngraded && !isSuperseded
                        && (grade.gpa == 0.0 || isNumericFail || (!passed && (grade.credit ?? 1.0) > 0.0))
                    let scoreText = isDeferred
                        ? "缓考"
                        : (isUngraded ? "—" : (trimmedScore.isEmpty ? "—" : trimmedScore))
                    let scoreColor: Color = {
                        if isExcluded || isDeferred || isUngraded || isSuperseded {
                            return theme.muted
                        }
                        if isFailed {
                            return theme.error
                        }
                        if let score = numericScore {
                            if score >= 90.0 { return theme.success }
                            if score >= 80.0 { return theme.accent }
                            return theme.ink
                        }
                        if trimmedScore.contains("优") { return theme.success }
                        if trimmedScore.contains("良") { return theme.accent }
                        return theme.ink
                    }()

                    Button {
                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                            if !isSimulatorMode {
                                isSimulatorMode = true
                            }
                            if excludedKeys.contains(grade.filterKey) {
                                excludedKeys.remove(grade.filterKey)
                            } else {
                                excludedKeys.insert(grade.filterKey)
                            }
                        }
                    } label: {
                        HStack(alignment: .center, spacing: GokeSpacing.md) {
                            if isSimulatorMode {
                                Image(systemName: isExcluded ? "circle" : "checkmark.circle.fill")
                                    .foregroundStyle(isExcluded ? theme.muted : theme.accent)
                                    .contentTransition(simplifyMotion ? .identity : .symbolEffect(.replace))
                            }

                            VStack(alignment: .leading, spacing: 3) {
                                Text(grade.name)
                                    .font(.body)
                                    .foregroundStyle(theme.ink)
                                    .strikethrough(isExcluded || isSuperseded)
                                    .lineLimit(2)

                                HStack(spacing: GokeSpacing.xs) {
                                    if !grade.term.isEmpty {
                                        Text(grade.term)
                                            .monospacedDigit()
                                    }
                                    if let credit = grade.credit {
                                        Text("· \(formatDecimal(credit)) 学分")
                                            .monospacedDigit()
                                    }
                                    if !grade.category.isEmpty {
                                        Text("· \(grade.category)")
                                    }
                                    if !grade.examType.isEmpty, grade.examType != "正常考试" {
                                        Text("· \(grade.examType)")
                                    }
                                    if !grade.status.isEmpty, grade.status != grade.examType {
                                        Text("· \(grade.status)")
                                    }
                                }
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                                .lineLimit(1)
                            }

                            Spacer(minLength: GokeSpacing.sm)

                            VStack(alignment: .trailing, spacing: 3) {
                                Text(scoreText)
                                    .font(.headline)
                                    .monospacedDigit()
                                    .strikethrough(isSuperseded && !isDeferred && !isUngraded)
                                    .foregroundStyle(scoreColor)
                                if isSuperseded {
                                    GokeBadge("已重修", tone: .muted)
                                } else if !isDeferred && !isUngraded, let gpa = grade.gpa {
                                    Text("绩点 \(String(format: "%.1f", gpa))")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.muted)
                                }
                            }
                        }
                        .opacity(isExcluded ? 0.45 : ((isSuperseded || isDeferred) ? 0.62 : 1.0))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                    .foregroundStyle(theme.ink)
                }
            }
        } header: {
            Text("成绩明细（\(displayedGrades.count)）")
                .monospacedDigit()
                .contentTransition(.numericText())
        }
        .listRowBackground(theme.surface)
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
                        showLoginSheet = false
                        Task { await refreshGrades(allowPrompt: false, forceRelogin: true) }
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
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showLoginSheet = false }
                }
            }
        }
        .tint(theme.accent)
    }

    // MARK: - Data Loading

    private func loadCachedGrades() {
        let defaults = container.appGroup.userDefaults
        if let data = defaults.data(forKey: CampusStorageKeys.gradesCache),
           let decoded = try? MintJSON.makeDecoder().decode([JxfwGrade].self, from: data) {
            grades = decoded
        }
        let ts = defaults.object(forKey: CampusStorageKeys.gradesUpdatedAt) as? Int64
        lastUpdatedMillis = ts
    }

    private func saveGradesCache(_ list: [JxfwGrade]) {
        let defaults = container.appGroup.userDefaults
        if let data = try? MintJSON.makeEncoder().encode(list) {
            defaults.set(data, forKey: CampusStorageKeys.gradesCache)
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        defaults.set(now, forKey: CampusStorageKeys.gradesUpdatedAt)
        lastUpdatedMillis = now
    }

    private func loadSavedCredentials() async {
        if let acc = (try? await container.keychain.get(.jxfwAccount)) ?? nil {
            username = acc
        }
        if let pwd = (try? await container.keychain.get(.jxfwPassword)) ?? nil {
            password = pwd
        }
    }

    private func refreshGrades(allowPrompt: Bool, forceRelogin: Bool = false) async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        if user.isEmpty || password.isEmpty {
            if allowPrompt {
                showLoginSheet = true
            }
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            if forceRelogin {
                await container.jxfwClient.resetSession()
            }
            let authed = await container.jxfwClient.isAuthenticated
            if !authed {
                try await container.jxfwClient.login(username: user, password: password)
                try? await container.keychain.set(user, for: .jxfwAccount)
                try? await container.keychain.set(password, for: .jxfwPassword)
            }
            let fetched: [JxfwGrade]
            do {
                fetched = try await container.jxfwClient.fetchGrades(termCode: "")
            } catch let err as JxfwError where err.kind == .session {
                try await container.jxfwClient.login(username: user, password: password)
                try? await container.keychain.set(user, for: .jxfwAccount)
                try? await container.keychain.set(password, for: .jxfwPassword)
                fetched = try await container.jxfwClient.fetchGrades(termCode: "")
            }
            grades = fetched
            saveGradesCache(fetched)
            actions.showToast(message: "已更新 \(fetched.count) 门课程成绩")
        } catch {
            errorMessage = CampusHelpers.jxfwErrorMessage(error)
            if CampusHelpers.isUnexpectedJxfwError(error) {
                container.recordError(source: "Jxfw.grades", error: error)
            }
        }
    }

    private func formatDecimal(_ value: Double) -> String {
        if value.rounded() == value {
            return String(format: "%.0f", value)
        }
        return String(format: "%.1f", value)
    }
}

// MARK: - 3. PhysicalTestView (GDUT TZCS Physical Fitness Reports)

public struct PhysicalTestView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var reports: [TzcsYearReport] = []
    @State private var selectedYear: Int?
    @State private var lastUpdatedMillis: Int64?

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var vercode: String = ""
    @State private var captchaData: Data?
    @State private var showLoginCard: Bool = false

    @State private var isLoading: Bool = false
    @State private var isFetchingCaptcha: Bool = false
    @State private var errorMessage: String?

    public init() {}

    private var summary: TzcsSummary {
        TzcsParse.summarizeReports(reports)
    }

    private var activeReport: TzcsYearReport? {
        if let selectedYear, let found = reports.first(where: { $0.year == selectedYear }) {
            return found
        }
        return summary.latestReport ?? reports.first
    }

    public var body: some View {
        List {
            if !container.tzcsClient.isSupported {
                Section {
                    CampusUnsupportedBanner(
                        message: "当前开源构建未包含体测协议适配器，仅支持界面预览；请使用官方安装包"
                    )
                }
                .listRowBackground(theme.surface)
            }

            if let errorMessage {
                Section {
                    CampusErrorRow(
                        message: errorMessage,
                        allowCopy: CampusHelpers.isUnexpectedBannerMessage(errorMessage)
                    )
                }
                .listRowBackground(theme.errorContainer)
            }

            if showLoginCard || reports.isEmpty {
                tzcsLoginSection
            }

            if !reports.isEmpty {
                summarySection
                if let report = activeReport {
                    reportDetailsSection(report)
                }
            }
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("体测成绩")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await refreshUsingSessionOrPrompt() }
                } label: {
                    if isLoading {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .disabled(isLoading)
                .accessibilityLabel("刷新体测成绩")
            }
        }
        .task {
            loadCachedReports()
            await loadSavedCredentials()
            if reports.isEmpty {
                showLoginCard = true
                await loadCaptcha()
            }
        }
    }

    private var tzcsLoginSection: some View {
        Group {
            Section {
                TextField("学号", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.asciiCapable)
                    .textContentType(.username)
                    .foregroundStyle(theme.ink)

                SecureField("密码（初始为身份证后六位）", text: $password)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textContentType(.password)
                    .foregroundStyle(theme.ink)

                HStack(spacing: GokeSpacing.md) {
                    TextField("4 位验证码", text: $vercode)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.asciiCapable)
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)

                    Button {
                        Task { await loadCaptcha() }
                    } label: {
                        ZStack {
                            RoundedRectangle(cornerRadius: GokeRadius.xs, style: .continuous)
                                .fill(theme.surfaceHighest)
                                .frame(width: 108, height: 38)

                            if isFetchingCaptcha {
                                ProgressView()
                                    .controlSize(.small)
                            } else if let captchaData {
                                captchaImageView(data: captchaData)
                            } else {
                                Text("点击获取")
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(theme.accent)
                            }
                        }
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("刷新图形验证码")
                }

                if !reports.isEmpty {
                    Button("收起登录") {
                        showLoginCard = false
                    }
                    .foregroundStyle(theme.muted)
                }
            } header: {
                Text("登录体测平台")
            } footer: {
                Text("需连接校园网")
            }
            .listRowBackground(theme.surface)

            Section {
                GokePrimaryActionButton(
                    isLoading ? "查询中…" : "登录并查询",
                    systemImage: "figure.run",
                    isLoading: isLoading,
                    isDisabled: isLoading
                        || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || password.isEmpty
                        || vercode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ) {
                    Task { await loginAndFetchReports() }
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
        }
    }

    @ViewBuilder
    private func captchaImageView(data: Data) -> some View {
        #if canImport(UIKit)
        if let uiImage = UIImage(data: data) {
            Image(uiImage: uiImage)
                .resizable()
                .interpolation(.none)
                .scaledToFit()
                .frame(width: 104, height: 34)
                .clipShape(RoundedRectangle(cornerRadius: GokeRadius.xs, style: .continuous))
        } else {
            Text("加载失败")
                .font(.caption2)
                .foregroundStyle(theme.error)
        }
        #else
        Text("已获取")
            .font(.caption2)
            .foregroundStyle(theme.muted)
        #endif
    }

    private var summarySection: some View {
        Section {
            if reports.count > 1 {
                Picker(
                    "选择学年",
                    selection: Binding(
                        get: { activeReport?.year ?? reports[0].year },
                        set: { selectedYear = $0 }
                    )
                ) {
                    ForEach(reports, id: \.year) { report in
                        Text(
                            report.totalGrade.isEmpty
                                ? "\(report.year) 年"
                                : "\(report.year) 年 · \(report.totalGrade)"
                        )
                        .tag(report.year)
                    }
                }
            }

            // Physical Test Hero Summary
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .lastTextBaseline, spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(activeReport.map { "\($0.year) 年度总分" } ?? "体测总分")
                            .font(.caption.weight(.medium))
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(activeReport?.totalScore.isEmpty == false ? activeReport!.totalScore : "—")
                                .font(.system(.largeTitle, design: .rounded, weight: .bold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: activeReport?.year)
                                .foregroundStyle(theme.accent)
                            if let grade = activeReport?.totalGrade, !grade.isEmpty {
                                GokeBadge(grade, tone: gradeTone(grade))
                            }
                        }
                    }

                    Spacer(minLength: 8)

                    HStack(spacing: 16) {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(summary.bestScore.map { String(format: "%.1f", $0) } ?? "—")
                                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .foregroundStyle(theme.ink)
                            Text("历史最高")
                                .font(.caption2)
                                .foregroundStyle(theme.muted)
                        }

                        VStack(alignment: .trailing, spacing: 2) {
                            Text("\(summary.recordedYearCount)/\(summary.yearCount) 年")
                                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .foregroundStyle(theme.ink)
                            Text("已记录")
                                .font(.caption2)
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
            }
            .padding(.vertical, 4)

            Button("重新登录") {
                showLoginCard = true
                Task { await loadCaptcha() }
            }
        } footer: {
            if let ts = lastUpdatedMillis {
                Text("上次同步：\(CampusHelpers.formatTimestamp(ts))")
            }
        }
        .listRowBackground(theme.surface)
    }

    private func reportDetailsSection(_ report: TzcsYearReport) -> some View {
        Section("单项明细（\(report.items.count)）") {
            if report.items.isEmpty {
                GokeEmptyState(
                    systemImage: "figure.run",
                    title: "本学年暂无单项记录"
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(Array(report.items.enumerated()), id: \.offset) { _, item in
                    VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                        LabeledContent {
                            HStack(spacing: GokeSpacing.xs) {
                                if !item.itemScore.isEmpty {
                                    Text(item.itemScore)
                                        .font(.headline)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.ink)
                                }
                                if !item.grade.isEmpty {
                                    GokeBadge(item.grade, tone: gradeTone(item.grade))
                                }
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name)
                                    .font(.body)
                                    .foregroundStyle(theme.ink)
                                HStack(spacing: GokeSpacing.sm) {
                                    if !item.rawValue.isEmpty {
                                        Text("成绩 \(item.rawValue)")
                                            .monospacedDigit()
                                    }
                                    if let weighted = item.weightedScore {
                                        Text("加权 \(String(format: "%.1f", weighted))")
                                            .monospacedDigit()
                                    }
                                    if let bonus = item.bonusScore, bonus > 0 {
                                        Text("附加 +\(String(format: "%.1f", bonus))")
                                            .monospacedDigit()
                                            .foregroundStyle(theme.success)
                                    }
                                }
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                            }
                        }

                        if let score = Double(item.itemScore.trimmingCharacters(in: .whitespacesAndNewlines)) {
                            ProgressView(value: min(max(score, 0), 100), total: 100)
                                .tint(progressTint(for: item.grade))
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .listRowBackground(theme.surface)
    }

    private func gradeTone(_ grade: String) -> GokeBadge.Tone {
        if grade.contains("优") { return .success }
        if grade.contains("良") { return .accent }
        if grade.contains("不及格") { return .error }
        return .secondary
    }

    private func progressTint(for grade: String) -> Color {
        if grade.contains("优") { return theme.success }
        if grade.contains("不及格") { return theme.error }
        return theme.accent
    }

    // MARK: - Networking & Persistence

    private func loadCachedReports() {
        let defaults = container.appGroup.userDefaults
        if let data = defaults.data(forKey: CampusStorageKeys.tzcsCache),
           let decoded = try? MintJSON.makeDecoder().decode([TzcsYearReport].self, from: data) {
            reports = decoded
            selectedYear = TzcsParse.summarizeReports(decoded).latestReport?.year
        }
        lastUpdatedMillis = defaults.object(forKey: CampusStorageKeys.tzcsUpdatedAt) as? Int64
    }

    private func saveReportsCache(_ list: [TzcsYearReport]) {
        let defaults = container.appGroup.userDefaults
        if let data = try? MintJSON.makeEncoder().encode(list) {
            defaults.set(data, forKey: CampusStorageKeys.tzcsCache)
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        defaults.set(now, forKey: CampusStorageKeys.tzcsUpdatedAt)
        lastUpdatedMillis = now
    }

    private func loadSavedCredentials() async {
        if let acc = (try? await container.keychain.get(.jxfwAccount)) ?? nil {
            username = acc
        }
        if let pwd = (try? await container.keychain.get(.tzcsPassword)) ?? nil {
            password = pwd
        }
        if let rawCookies = (try? await container.keychain.get(CampusStorageKeys.tzcsCookiesKey)) ?? nil,
           !rawCookies.isEmpty {
            _ = await container.tzcsClient.importCookies(rawCookies)
        }
    }

    private func loadCaptcha() async {
        isFetchingCaptcha = true
        errorMessage = nil
        defer { isFetchingCaptcha = false }
        do {
            captchaData = try await container.tzcsClient.fetchCaptchaChallenge()
            vercode = ""
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func refreshUsingSessionOrPrompt() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let fetched = try await container.tzcsClient.fetchAllReports()
            applyFetchedReports(fetched)
            showLoginCard = false
            actions.showToast(message: "体测成绩已刷新")
        } catch {
            showLoginCard = true
            errorMessage = error.localizedDescription
            await loadCaptcha()
        }
    }

    private func loginAndFetchReports() async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let code = vercode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty, !code.isEmpty else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await container.tzcsClient.login(username: user, password: password, vercode: code)
            try? await container.keychain.set(user, for: .jxfwAccount)
            try? await container.keychain.set(password, for: .tzcsPassword)
            let exportedCookies = await container.tzcsClient.exportCookies()
            try? await container.keychain.set(exportedCookies, for: CampusStorageKeys.tzcsCookiesKey)

            let fetched = try await container.tzcsClient.fetchAllReports()
            applyFetchedReports(fetched)
            showLoginCard = false
            actions.showToast(message: "已获取 \(fetched.count) 个学年体测报告")
        } catch {
            errorMessage = error.localizedDescription
            if let tzErr = error as? TzcsError, tzErr.kind == .captcha {
                await loadCaptcha()
            }
        }
    }

    private func applyFetchedReports(_ incoming: [TzcsYearReport]) {
        let merged = TzcsParse.mergeReports(existing: reports, incoming: incoming)
        let filtered = TzcsParse.filterApplicableItems(merged)
        reports = filtered
        if selectedYear == nil || !filtered.contains(where: { $0.year == selectedYear }) {
            selectedYear = TzcsParse.summarizeReports(filtered).latestReport?.year
        }
        saveReportsCache(filtered)
    }
}

// MARK: - 4. EmptyClassroomsView (GDUT jwcwx Empty Classroom Finder)

public struct EmptyClassroomsView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var isAuthenticated: Bool = false

    @State private var buildings: [ClassroomBuilding] = []
    @State private var selectedCampus: String = ""
    @State private var selectedBuildingCode: String = ""
    @State private var selectedDate: LocalDate = .today()
    @State private var startPeriod: Int = 1
    @State private var endPeriod: Int = 4
    @State private var onlyFreeRooms: Bool = true
    @State private var searchQuery: String = ""

    @State private var rooms: [ClassroomRoom] = []
    @State private var occupancy: [ClassroomOccupancy] = []
    @State private var loadedBuildingName: String = ""
    @State private var loadedDate: LocalDate = .today()
    @State private var loadGeneration: Int = 0
    @State private var inspectedRoom: ClassroomRoom?

    @State private var isLoadingAuth: Bool = false
    @State private var isLoadingRooms: Bool = false
    @State private var errorMessage: String?

    private struct QueryKey: Equatable {
        let isAuthenticated: Bool
        let buildingCode: String
        let date: LocalDate
    }

    public init() {}

    private var maxPeriod: Int {
        let semesterMax = (container.liveSnapshot.currentSemester?.periods ?? GdutPeriods.default)
            .map(\.index)
            .max() ?? 12
        return max(12, semesterMax)
    }

    private var selectedPeriods: Set<Int> {
        let low = min(startPeriod, endPeriod)
        let high = max(startPeriod, endPeriod)
        return Set(low...high)
    }

    private var campuses: [String] {
        var seen = Set<String>()
        var out: [String] = []
        for b in buildings {
            let name = b.campusName.isEmpty ? "默认校区" : b.campusName
            if seen.insert(name).inserted {
                out.append(name)
            }
        }
        return out
    }

    private var campusBuildings: [ClassroomBuilding] {
        guard !selectedCampus.isEmpty else { return buildings }
        return buildings.filter { ($0.campusName.isEmpty ? "默认校区" : $0.campusName) == selectedCampus }
    }

    private var selectedBuildingName: String {
        buildings.first(where: { $0.code == selectedBuildingCode })?.name ?? "教学楼"
    }

    private var occupiedByRoom: [String: [ClassroomOccupancy]] {
        var map: [String: [ClassroomOccupancy]] = [:]
        for item in occupancy {
            if !item.roomCode.isEmpty {
                map[item.roomCode, default: []].append(item)
            }
            if !item.roomName.isEmpty && item.roomName != item.roomCode {
                map[item.roomName, default: []].append(item)
            }
        }
        return map
    }

    private func occupiedPeriods(for room: ClassroomRoom) -> Set<Int> {
        let items = occupiedByRoom[room.code] ?? occupiedByRoom[room.name] ?? []
        var out = Set<Int>()
        for item in items {
            out.formUnion(item.periods)
        }
        return out
    }

    private func isRoomFree(_ room: ClassroomRoom) -> Bool {
        occupiedPeriods(for: room).isDisjoint(with: selectedPeriods)
    }

    private var filteredRooms: [ClassroomRoom] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        return rooms.filter { room in
            if onlyFreeRooms && !isRoomFree(room) { return false }
            if !q.isEmpty && !room.name.localizedCaseInsensitiveContains(q) { return false }
            return true
        }
    }

    private struct RoomGroup: Identifiable {
        let title: String
        let rooms: [ClassroomRoom]
        var id: String { title }
    }

    private var displayedBuildingName: String {
        loadedBuildingName.isEmpty ? selectedBuildingName : loadedBuildingName
    }

    private var groupedRooms: [RoomGroup] {
        guard !filteredRooms.isEmpty else { return [] }
        let prefixes = filteredRooms.compactMap { room -> String? in
            let parts = room.name.split(separator: "-", maxSplits: 1)
            return parts.count == 2 ? String(parts[0]) : nil
        }
        let distinctPrefixes = Set(prefixes)
        if distinctPrefixes.count > 1 {
            var order: [String] = []
            var buckets: [String: [ClassroomRoom]] = [:]
            for room in filteredRooms {
                let parts = room.name.split(separator: "-", maxSplits: 1)
                let key = parts.count == 2 ? String(parts[0]) : displayedBuildingName
                if buckets[key] == nil {
                    order.append(key)
                }
                buckets[key, default: []].append(room)
            }
            return order.map { RoomGroup(title: $0, rooms: buckets[$0] ?? []) }
        }
        return [RoomGroup(title: displayedBuildingName, rooms: filteredRooms)]
    }

    public var body: some View {
        List {
            if !container.jwcwxClient.isSupported {
                Section {
                    CampusUnsupportedBanner(
                        message: "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包"
                    )
                }
                .listRowBackground(theme.surface)
            }

            if let errorMessage {
                Section {
                    CampusErrorRow(
                        message: errorMessage,
                        allowCopy: CampusHelpers.isUnexpectedBannerMessage(errorMessage)
                    )
                }
                .listRowBackground(theme.errorContainer)
            }

            if !isAuthenticated && buildings.isEmpty {
                casLoginSection
            } else {
                queryFilterSection
                roomsResultSections
            }
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .searchable(text: $searchQuery, prompt: "搜索教室号")
        .refreshable {
            if isAuthenticated && !selectedBuildingCode.isEmpty {
                await loadRoomsAndOccupancy()
            }
        }
        .navigationTitle("空教室")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            if isAuthenticated {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await loadRoomsAndOccupancy() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .gokeSpinningSymbol(isActive: isLoadingRooms)
                    }
                    .disabled(isLoadingRooms || selectedBuildingCode.isEmpty)
                    .accessibilityLabel(isLoadingRooms ? "正在刷新教室状态" : "刷新教室状态")
                }
            }
        }
        .sheet(item: Binding(
            get: { inspectedRoom.map(InspectedRoomItem.init) },
            set: { inspectedRoom = $0?.room }
        )) { item in
            roomDetailSheet(room: item.room)
                .gokePaperSheet(detents: [.medium, .large])
        }
        .task {
            let defaults = currentDefaultPeriodRange()
            startPeriod = defaults.lowerBound
            endPeriod = defaults.upperBound
            await bootstrapClassrooms()
        }
        .task(id: QueryKey(isAuthenticated: isAuthenticated, buildingCode: selectedBuildingCode, date: selectedDate)) {
            guard isAuthenticated, !selectedBuildingCode.isEmpty else { return }
            await loadRoomsAndOccupancy()
        }
    }

    private struct InspectedRoomItem: Identifiable {
        let room: ClassroomRoom
        var id: String { room.code }
    }

    private var casLoginSection: some View {
        Section("教务账号") {
            TextField("学号", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .textContentType(.username)

            SecureField("密码", text: $password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.password)

            Button {
                Task { await signInAndLoadBuildings() }
            } label: {
                HStack {
                    Text(isLoadingAuth ? "登录中…" : "登录")
                        .font(.body.weight(.semibold))
                    Spacer()
                    if isLoadingAuth {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(
                isLoadingAuth
                    || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    || password.isEmpty
            )
        }
        .listRowBackground(theme.surface)
    }

    private var queryFilterSection: some View {
        Section("查询条件") {
            if campuses.count > 1 {
                Picker(
                    "校区",
                    selection: Binding(
                        get: { selectedCampus },
                        set: { newCampus in
                            selectedCampus = newCampus
                            if let first = campusBuildings.first {
                                selectedBuildingCode = first.code
                            }
                        }
                    )
                ) {
                    ForEach(campuses, id: \.self) { campus in
                        Text(campus).tag(campus)
                    }
                }
            }

            if !campusBuildings.isEmpty {
                Picker("教学楼", selection: $selectedBuildingCode) {
                    ForEach(campusBuildings, id: \.code) { building in
                        Text(building.name).tag(building.code)
                    }
                }
            }

            DatePicker(
                "日期",
                selection: Binding(
                    get: { selectedDate.atStartOfDay().toDate() },
                    set: { newDate in
                        selectedDate = LocalDateTime.fromDate(newDate).date
                    }
                ),
                displayedComponents: .date
            )

            Picker(
                "开始节次",
                selection: Binding(
                    get: { startPeriod },
                    set: { newVal in
                        startPeriod = newVal
                        if endPeriod < newVal { endPeriod = newVal }
                    }
                )
            ) {
                ForEach(1...maxPeriod, id: \.self) { p in
                    Text("第 \(p) 节").tag(p)
                }
            }

            Picker(
                "结束节次",
                selection: Binding(
                    get: { endPeriod },
                    set: { newVal in
                        endPeriod = newVal
                        if startPeriod > newVal { startPeriod = newVal }
                    }
                )
            ) {
                ForEach(1...maxPeriod, id: \.self) { p in
                    Text("第 \(p) 节").tag(p)
                }
            }

            Toggle("仅显示空闲教室", isOn: $onlyFreeRooms)

            Button {
                Task { await loadRoomsAndOccupancy() }
            } label: {
                HStack {
                    Label(
                        isLoadingRooms ? "查询中…" : "查询",
                        systemImage: "magnifyingglass"
                    )
                    Spacer()
                    if isLoadingRooms {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isLoadingRooms || selectedBuildingCode.isEmpty)
        }
        .listRowBackground(theme.surface)
    }

    @ViewBuilder
    private var roomsResultSections: some View {
        if isLoadingRooms && rooms.isEmpty {
            Section {
                HStack(spacing: GokeSpacing.md) {
                    ProgressView()
                        .controlSize(.small)
                    Text("查询中…")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }
            }
            .listRowBackground(theme.surface)
        } else if filteredRooms.isEmpty {
            Section {
                if !searchQuery.isEmpty {
                    GokeEmptyState(
                        systemImage: "magnifyingglass",
                        title: "未找到「\(searchQuery)」"
                    )
                } else {
                    GokeEmptyState(
                        systemImage: "building.2",
                        title: "暂无符合条件的教室",
                        subtitle: "调整节次或切换教学楼试试"
                    )
                }
            }
            .listRowBackground(Color.clear)
        } else {
            ForEach(groupedRooms) { group in
                Section("\(group.title) · \(loadedDate.description)（\(group.rooms.count)）") {
                    ForEach(group.rooms, id: \.code) { room in
                        let occupied = occupiedPeriods(for: room)
                        let free = isRoomFree(room)

                        Button {
                            inspectedRoom = room
                        } label: {
                            VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                                HStack {
                                    Text(room.name)
                                        .font(.headline)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.ink)
                                    if let cap = room.capacity {
                                        Text("\(cap) 座")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundStyle(theme.muted)
                                    }
                                    Spacer()
                                    GokeBadge(free ? "空闲" : "有占用", tone: free ? .success : .muted)
                                }

                                HStack(spacing: 3) {
                                    ForEach(1...maxPeriod, id: \.self) { p in
                                        let isOcc = occupied.contains(p)
                                        let isTargeted = selectedPeriods.contains(p)
                                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                                            .fill(
                                                isOcc
                                                    ? theme.outline.opacity(0.35)
                                                    : theme.accent.opacity(isTargeted ? 0.85 : 0.3)
                                            )
                                            .frame(height: 8)
                                    }
                                }
                            }
                            .padding(.vertical, 2)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintRow)
                        .foregroundStyle(theme.ink)
                    }
                }
                .listRowBackground(theme.surface)
            }
        }
    }

    private func roomDetailSheet(room: ClassroomRoom) -> some View {
        let items = (occupiedByRoom[room.code] ?? occupiedByRoom[room.name] ?? [])
            .sorted { ($0.periods.first ?? 0) < ($1.periods.first ?? 0) }
        return NavigationStack {
            List {
                if items.isEmpty {
                    Section {
                        GokeEmptyState(
                            systemImage: "checkmark.circle",
                            title: "全天空闲"
                        )
                    }
                    .listRowBackground(Color.clear)
                } else {
                    Section("\(loadedDate.description) 占用记录") {
                        ForEach(items, id: \.id) { occ in
                            VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                                HStack {
                                    Text(occ.courseName.isEmpty ? occupancyTypeLabel(occ.type) : occ.courseName)
                                        .font(.headline)
                                        .foregroundStyle(theme.ink)
                                    Spacer()
                                    GokeBadge(
                                        occupancyTypeLabel(occ.type),
                                        tone: occ.type == .EXAM ? .error : .secondary
                                    )
                                }
                                Text("节次：第 \(occ.periods.map(String.init).joined(separator: ", ")) 节")
                                    .font(.subheadline)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.accent)
                                if !occ.teacher.isEmpty {
                                    Text("教师：\(occ.teacher)")
                                        .font(.footnote)
                                        .foregroundStyle(theme.muted)
                                }
                                if !occ.className.isEmpty {
                                    Text("班级：\(occ.className)")
                                        .font(.footnote)
                                        .foregroundStyle(theme.muted)
                                }
                                if !occ.note.isEmpty {
                                    Text(occ.note)
                                        .font(.caption)
                                        .foregroundStyle(theme.muted)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                    .listRowBackground(theme.surface)
                }
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle(room.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { inspectedRoom = nil }
                }
            }
        }
        .tint(theme.accent)
    }

    private func occupancyTypeLabel(_ type: OccupancyType) -> String {
        switch type {
        case .COURSE: "排课"
        case .BORROWED: "借用"
        case .LOCKED: "锁定"
        case .EXAM: "考试"
        case .OTHER: "占用"
        }
    }

    // MARK: - Networking

    private func currentDefaultPeriodRange() -> ClosedRange<Int> {
        let upper = maxPeriod
        let now = LocalDateTime.now().time
        let periods = container.liveSnapshot.currentSemester?.periods ?? GdutPeriods.default
        if let current = periods.first(where: { now <= $0.end }) {
            let start = min(max(1, current.index), upper)
            let next = min(upper, start + 1)
            return start...next
        }
        return 1...min(4, upper)
    }

    private func bootstrapClassrooms() async {
        if let acc = (try? await container.keychain.get(.jxfwAccount)) ?? nil {
            username = acc
        }
        if let pwd = (try? await container.keychain.get(.jxfwPassword)) ?? nil {
            password = pwd
        }
        let alreadyAuthed = await container.jwcwxClient.isAuthenticated
        isAuthenticated = alreadyAuthed
        if alreadyAuthed {
            await loadBuildings()
        } else if !username.isEmpty && !password.isEmpty {
            await signInAndLoadBuildings()
        }
    }

    private func signInAndLoadBuildings() async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else { return }
        isLoadingAuth = true
        errorMessage = nil
        defer { isLoadingAuth = false }

        do {
            try await container.jwcwxClient.login(username: user, password: password)
            isAuthenticated = true
            await loadBuildings()
        } catch {
            errorMessage = CampusHelpers.jxfwErrorMessage(error)
            if CampusHelpers.isUnexpectedJxfwError(error) {
                container.recordError(source: "Jwcwx.login", error: error)
            }
        }
    }

    private func loadBuildings() async {
        isLoadingAuth = true
        errorMessage = nil
        defer { isLoadingAuth = false }
        do {
            let list = try await container.jwcwxClient.fetchBuildings()
            buildings = list
            if selectedCampus.isEmpty {
                selectedCampus = campuses.first ?? ""
            }
            if selectedBuildingCode.isEmpty, let first = campusBuildings.first ?? list.first {
                selectedBuildingCode = first.code
            }
        } catch {
            errorMessage = CampusHelpers.jxfwErrorMessage(error)
            if CampusHelpers.isUnexpectedJxfwError(error) {
                container.recordError(source: "Jwcwx.buildings", error: error)
            }
        }
    }

    private func loadRoomsAndOccupancy() async {
        guard !selectedBuildingCode.isEmpty else { return }
        loadGeneration += 1
        let gen = loadGeneration
        isLoadingRooms = true
        errorMessage = nil
        defer {
            if gen == loadGeneration {
                isLoadingRooms = false
            }
        }

        let code = selectedBuildingCode
        let buildingName = buildings.first(where: { $0.code == code })?.name ?? "教学楼"
        let date = selectedDate
        let client = container.jwcwxClient
        do {
            async let fetchedRooms = client.fetchRooms(buildingCode: code)
            async let fetchedOcc = client.fetchOccupancy(buildingCode: code, date: date)
            let (r, o) = try await (fetchedRooms, fetchedOcc)
            guard gen == loadGeneration else { return }
            rooms = r
            occupancy = o
            loadedBuildingName = buildingName
            loadedDate = date
        } catch {
            guard gen == loadGeneration else { return }
            errorMessage = CampusHelpers.jxfwErrorMessage(error)
            if CampusHelpers.isUnexpectedJxfwError(error) {
                container.recordError(source: "Jwcwx.rooms", error: error)
            }
        }
    }
}
