import Foundation
import GokeAdapterLoader
import GokeCore
import GokeShared
import Observation
import SwiftUI

// MARK: - Persistent Store for Dual-Source Jxfw Notices

@MainActor
@Observable
final class JxfwNoticesStore {
    static let shared = JxfwNoticesStore()

    private enum Keys {
        static let cachedNotices = "app.goke.ios.jxfw.notices.cache.v1"
        static let seenIds = "app.goke.ios.jxfw.notices.seenIds.v1"
        static let unreadIds = "app.goke.ios.jxfw.notices.unreadIds.v1"
        static let fetchedAtMs = "app.goke.ios.jxfw.notices.fetchedAtMs.v1"
        static let publicEtag = "app.goke.ios.jxfw.notices.publicEtag.v1"
        static let publicLastMod = "app.goke.ios.jxfw.notices.publicLastMod.v1"
        static let publicEnabled = "app.goke.ios.jxfw.notices.publicEnabled.v1"
        static let internalEnabled = "app.goke.ios.jxfw.notices.internalEnabled.v1"
    }

    private(set) var notices: [JxfwNotice] = []
    private(set) var unreadIds: Set<String> = []
    private(set) var fetchedAtMs: Int64?
    private(set) var isBusy: Bool = false
    private(set) var lastError: String?
    var publicEnabled: Bool = true
    var internalEnabled: Bool = true

    @ObservationIgnored private var seenIds: [String] = []
    @ObservationIgnored private var hydrated: Bool = false
    @ObservationIgnored private let client: any JxfwNoticesAdapterClient =
        CampusAdapterLoader.makeFactory().makeJxfwNoticesClient()
    @ObservationIgnored private var hasAuthenticatedInternal: Bool = false

    var isSupported: Bool {
        client.isSupported
    }

    private init() {}

    var activeUnreadIds: Set<String> {
        let enabledIds = Set(notices.filter { isSourceEnabled($0.source) }.map(\.id))
        return unreadIds.intersection(enabledIds)
    }

    var activeUnreadCount: Int {
        activeUnreadIds.count
    }

    func isSourceEnabled(_ source: JxfwNoticeSource) -> Bool {
        switch source {
        case .public: publicEnabled
        case .internal: internalEnabled
        }
    }

    func hydrate() {
        guard !hydrated else { return }
        hydrated = true
        let ud = AppGroupContainer.shared.userDefaults
        if ud.object(forKey: Keys.publicEnabled) != nil {
            publicEnabled = ud.bool(forKey: Keys.publicEnabled)
        }
        if ud.object(forKey: Keys.internalEnabled) != nil {
            internalEnabled = ud.bool(forKey: Keys.internalEnabled)
        }
        if let data = ud.data(forKey: Keys.cachedNotices),
           let decoded = try? JSONDecoder().decode([JxfwNotice].self, from: data)
        {
            notices = decoded
        }
        seenIds = ud.stringArray(forKey: Keys.seenIds) ?? []
        unreadIds = Set(ud.stringArray(forKey: Keys.unreadIds) ?? [])
        let ts = ud.object(forKey: Keys.fetchedAtMs) as? NSNumber
        fetchedAtMs = ts?.int64Value
    }

    func setPublicEnabled(_ enabled: Bool) {
        publicEnabled = enabled
        AppGroupContainer.shared.userDefaults.set(enabled, forKey: Keys.publicEnabled)
    }

    func setInternalEnabled(_ enabled: Bool) {
        internalEnabled = enabled
        AppGroupContainer.shared.userDefaults.set(enabled, forKey: Keys.internalEnabled)
    }

    func markRead(_ id: String) {
        guard unreadIds.remove(id) != nil else { return }
        persistReadState()
    }

    func markAllRead() {
        let active = activeUnreadIds
        guard !active.isEmpty else { return }
        unreadIds.subtract(active)
        persistReadState()
    }

    @discardableResult
    func refresh(force: Bool = false) async -> Bool {
        hydrate()
        guard publicEnabled || internalEnabled else { return false }
        guard !isBusy else { return false }
        isBusy = true
        lastError = nil
        defer { isBusy = false }

        let ud = AppGroupContainer.shared.userDefaults
        var publicList = notices.filter { $0.source == .public }
        var internalList = notices.filter { $0.source == .internal }
        var errors: [String] = []
        var anySuccess = false

        if publicEnabled {
            let hasCachedPublic = !publicList.isEmpty
            let etag = (force || !hasCachedPublic) ? nil : ud.string(forKey: Keys.publicEtag)
            let lastMod = (force || !hasCachedPublic) ? nil : ud.string(forKey: Keys.publicLastMod)
            do {
                let fetched = try await client.fetchPublicNotices(etag: etag, lastModified: lastMod)
                switch fetched {
                case .notModified:
                    anySuccess = true
                case .fetched(let list, let respEtag, let respLastMod):
                    publicList = list
                    ud.set(respEtag, forKey: Keys.publicEtag)
                    ud.set(respLastMod, forKey: Keys.publicLastMod)
                    anySuccess = true
                }
            } catch {
                errors.append("公开公告：\(error.localizedDescription)")
            }
        } else {
            publicList = []
        }

        if internalEnabled {
            let account = (try? await KeychainStore.shared.get(.jxfwAccount)) ?? nil
            let password = (try? await KeychainStore.shared.get(.jxfwPassword)) ?? nil
            if let account, !account.isEmpty, let password, !password.isEmpty {
                do {
                    if !hasAuthenticatedInternal {
                        try await client.login(username: account, password: password)
                        hasAuthenticatedInternal = true
                    }
                    do {
                        internalList = try await client.fetchInternalNotices()
                        anySuccess = true
                    } catch let err as JxfwError where err.kind == .session {
                        hasAuthenticatedInternal = false
                        try await client.login(username: account, password: password)
                        hasAuthenticatedInternal = true
                        internalList = try await client.fetchInternalNotices()
                        anySuccess = true
                    }
                } catch {
                    errors.append("站内消息：\(error.localizedDescription)")
                }
            }
        } else {
            internalList = []
        }

        if anySuccess {
            let merged = JxfwNoticeDiff.mergeSources(
                publicNotices: publicList,
                internalNotices: internalList
            )
            let isBaseline = seenIds.isEmpty && fetchedAtMs == nil
            let diff = JxfwNoticeDiff.diffNew(
                incoming: merged,
                seenIds: seenIds,
                isInitialBaseline: isBaseline
            )
            seenIds = diff.updatedSeenIds
            for item in diff.newNotices {
                unreadIds.insert(item.id)
            }
            notices = merged
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            fetchedAtMs = nowMs

            if let encoded = try? JSONEncoder().encode(merged) {
                ud.set(encoded, forKey: Keys.cachedNotices)
            }
            ud.set(seenIds, forKey: Keys.seenIds)
            ud.set(NSNumber(value: nowMs), forKey: Keys.fetchedAtMs)
            persistReadState()
        }

        if !errors.isEmpty {
            lastError = errors.joined(separator: "\n")
        }
        return anySuccess
    }

    private func persistReadState() {
        AppGroupContainer.shared.userDefaults.set(Array(unreadIds), forKey: Keys.unreadIds)
    }
}

// MARK: - JxfwNoticesView

public struct JxfwNoticesView: View {
    private enum NoticeFilter: String, CaseIterable, Identifiable {
        case all
        case `internal`
        case `public`

        var id: String { rawValue }

        var title: String {
            switch self {
            case .all: "全部"
            case .internal: "站内"
            case .public: "公告"
            }
        }
    }

    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.openURL) private var openURL

    @State private var store = JxfwNoticesStore.shared
    @State private var filter: NoticeFilter = .all
    @State private var showSourcesSheet: Bool = false

    public init() {}

    private var selectedSourceDisabled: Bool {
        switch filter {
        case .all: !store.publicEnabled && !store.internalEnabled
        case .internal: !store.internalEnabled
        case .public: !store.publicEnabled
        }
    }

    private var visibleNotices: [JxfwNotice] {
        guard !selectedSourceDisabled else { return [] }
        switch filter {
        case .all:
            return store.notices.filter { store.isSourceEnabled($0.source) }
        case .internal:
            return store.notices.filter { $0.source == .internal }
        case .public:
            return store.notices.filter { $0.source == .public }
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            if !store.isSupported {
                CampusUnsupportedBanner(
                    message: "当前开源构建未包含教务协议适配器，仅支持界面预览；请使用官方安装包"
                )
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.top, GokeSpacing.sm)
            }

            Picker("来源筛选", selection: $filter) {
                ForEach(NoticeFilter.allCases) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.vertical, GokeSpacing.sm)

            if visibleNotices.isEmpty && !store.isBusy {
                emptyContent
            } else {
                List {
                    if store.fetchedAtMs != nil || store.lastError != nil {
                        Section {
                            if let ts = store.fetchedAtMs {
                                Text("上次更新：\(Self.formatTimestamp(ts))")
                                    .font(GokeTypography.labelMedium)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.muted)
                            }
                            if let err = store.lastError {
                                Label {
                                    Text(err)
                                        .font(.subheadline)
                                        .foregroundStyle(theme.error)
                                } icon: {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .foregroundStyle(theme.error)
                                }
                            }
                        }
                        .listRowBackground(theme.surface)
                    }

                    Section {
                        ForEach(visibleNotices) { notice in
                            noticeRow(notice)
                        }
                    }
                    .listRowBackground(theme.surface)
                }
                .listStyle(.insetGrouped)
                .gokePaperListStyle()
                .refreshable {
                    await store.refresh(force: true)
                }
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("教务通知")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !store.activeUnreadIds.isEmpty {
                    Button("全部已读") {
                        store.markAllRead()
                    }
                }
                Button {
                    showSourcesSheet = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel("通知来源设置")

                Button {
                    Task {
                        let ok = await store.refresh(force: true)
                        if ok {
                            actions.showToast(message: "已更新教务通知")
                        }
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .gokeSpinningSymbol(isActive: store.isBusy)
                }
                .disabled(store.isBusy || (!store.publicEnabled && !store.internalEnabled))
                .accessibilityLabel("刷新教务通知")
            }
        }
        .sheet(isPresented: $showSourcesSheet) {
            sourcesConfigSheet
                .gokePaperSheet(detents: [.medium])
        }
        .task {
            store.hydrate()
            if !store.publicEnabled && store.internalEnabled {
                filter = .internal
            } else if store.publicEnabled && !store.internalEnabled {
                filter = .public
            }
            if store.notices.isEmpty && (store.publicEnabled || store.internalEnabled) {
                await store.refresh(force: false)
            }
        }
    }

    @ViewBuilder
    private var emptyContent: some View {
        ScrollView {
            VStack(spacing: GokeSpacing.lg) {
                if let err = store.lastError {
                    Label {
                        Text(err)
                            .font(.subheadline)
                            .foregroundStyle(theme.error)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(theme.error)
                    }
                    .padding(GokeSpacing.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.errorContainer, in: RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
                    .padding(.horizontal, GokeSpacing.lg)
                }

                GokeEmptyState(
                    systemImage: "bell",
                    title: emptyTitle,
                    subtitle: emptyHint,
                    primaryActionTitle: selectedSourceDisabled ? "开启通知来源" : "立即刷新",
                    onPrimaryAction: {
                        if selectedSourceDisabled {
                            showSourcesSheet = true
                        } else {
                            Task { await store.refresh(force: true) }
                        }
                    }
                )
                .padding(.top, 36)
            }
        }
    }

    private var emptyTitle: String {
        if selectedSourceDisabled {
            switch filter {
            case .public: return "已关闭公开公告"
            case .internal: return "已关闭站内消息"
            case .all: return "通知来源已全部关闭"
            }
        }
        switch filter {
        case .internal: return "暂无站内消息"
        case .public: return "暂无公开公告"
        case .all: return "暂无教务通知"
        }
    }

    private var emptyHint: String {
        if selectedSourceDisabled {
            return "可在右上角来源设置中重新开启"
        }
        if filter == .internal {
            return "需在「教务系统」保存账号密码后拉取站内个人消息"
        }
        return "下拉或点击右上角刷新获取最新教务处公告"
    }

    private func noticeRow(_ notice: JxfwNotice) -> some View {
        let unread = store.activeUnreadIds.contains(notice.id)
        let isInternal = notice.source == .internal
        return Button {
            store.markRead(notice.id)
            if !notice.url.isEmpty, let url = URL(string: notice.url) {
                openURL(url)
            }
        } label: {
            HStack(alignment: .top, spacing: 10) {
                if unread {
                    Circle()
                        .fill(theme.accent)
                        .frame(width: 7, height: 7)
                        .padding(.top, 7)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(notice.title)
                        .font(GokeTypography.bodyLarge)
                        .fontWeight(unread ? .medium : .regular)
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        GokeBadge(
                            isInternal ? "站内" : "公告",
                            tone: isInternal ? .secondary : .muted
                        )

                        if !notice.date.isEmpty {
                            Text(notice.date)
                                .font(GokeTypography.labelMedium)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }

                        Spacer(minLength: 4)

                        if !notice.url.isEmpty {
                            Image(systemName: "arrow.up.right.square")
                                .font(.caption)
                                .foregroundStyle(theme.muted.opacity(0.75))
                        }
                    }
                }
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintRow)
    }

    private var sourcesConfigSheet: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("教务处公开公告", isOn: Binding(
                        get: { store.publicEnabled },
                        set: { store.setPublicEnabled($0) }
                    ))
                    Toggle("教务系统站内消息", isOn: Binding(
                        get: { store.internalEnabled },
                        set: { store.setInternalEnabled($0) }
                    ))
                } header: {
                    Text("通知来源")
                } footer: {
                    Text("公开公告免登录拉取教务处官网；站内消息需在「教务系统」保存账号密码，不会自动标为已读。")
                }
                .listRowBackground(theme.surface)
            }
            .gokePaperListStyle()
            .navigationTitle("通知来源")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        showSourcesSheet = false
                    }
                }
            }
        }
    }

    private static let timestampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "zh_CN")
        f.dateFormat = "MM-dd HH:mm"
        return f
    }()

    private static func formatTimestamp(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return timestampFormatter.string(from: date)
    }
}
