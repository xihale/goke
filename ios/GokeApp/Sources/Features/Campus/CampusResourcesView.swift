import Foundation
import GokeAdapterLoader
import GokeCore
import GokeShared
import Observation
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Persistent Store for DropRelay Campus Resources

@MainActor
@Observable
final class DropRelayStore {
    static let shared = DropRelayStore()

    private enum Keys {
        static let cachedIndex = "app.goke.ios.droprelay.indexCache.v1"
        static let etag = "app.goke.ios.droprelay.etag.v1"
        static let lastModified = "app.goke.ios.droprelay.lastModified.v1"
        static let syncedAtMs = "app.goke.ios.droprelay.syncedAtMs.v1"
        static let customShareUrl = "app.goke.ios.droprelay.customShareUrl.v1"
        static let customDropUrl = "app.goke.ios.droprelay.customDropUrl.v1"
    }

    let campusDefaults: CampusDropRelayDefaults = CampusAdapterLoader.makeFactory().dropRelayDefaults()

    private(set) var index: DropRelayIndex?
    private(set) var loadingIndex: Bool = false
    private(set) var indexError: DropRelayErrorKind?
    private(set) var indexErrorDetail: String?
    private(set) var lastSyncedAtMs: Int64 = 0
    private(set) var customShareUrl: String = ""
    private(set) var customDropUrl: String = ""

    @ObservationIgnored private var hydrated: Bool = false
    @ObservationIgnored private let client = DropRelayClient()

    private init() {}

    var effectiveShareUrl: String {
        DropRelayLogic.normalizeBaseUrl(customShareUrl)
            ?? DropRelayLogic.normalizeBaseUrl(campusDefaults.shareUrl)
            ?? ""
    }

    var effectiveDropUrl: String {
        DropRelayLogic.normalizeBaseUrl(customDropUrl)
            ?? DropRelayLogic.normalizeBaseUrl(campusDefaults.dropUrl)
            ?? ""
    }

    var hasCampusDefaults: Bool {
        !campusDefaults.shareUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !campusDefaults.dropUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func hydrate() {
        guard !hydrated else { return }
        hydrated = true
        let ud = AppGroupContainer.shared.userDefaults
        customShareUrl = ud.string(forKey: Keys.customShareUrl) ?? ""
        customDropUrl = ud.string(forKey: Keys.customDropUrl) ?? ""
        if let raw = ud.string(forKey: Keys.cachedIndex),
           let parsed = try? DropRelayLogic.parseIndexJson(raw)
        {
            index = parsed
        }
        lastSyncedAtMs = (ud.object(forKey: Keys.syncedAtMs) as? NSNumber)?.int64Value ?? 0
    }

    func saveCustomUrls(share: String, drop: String) {
        let trimmedShare = share.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedDrop = drop.trimmingCharacters(in: .whitespacesAndNewlines)
        customShareUrl = trimmedShare
        customDropUrl = trimmedDrop
        let ud = AppGroupContainer.shared.userDefaults
        ud.set(trimmedShare, forKey: Keys.customShareUrl)
        ud.set(trimmedDrop, forKey: Keys.customDropUrl)
    }

    func matchingFiles(forCourse courseName: String) -> [DropRelayFile] {
        DropRelayLogic.matchCourseResources(files: index?.files ?? [], courseName: courseName)
    }

    func refreshIndex(force: Bool = false) async {
        hydrate()
        let shareUrl = effectiveShareUrl
        guard !shareUrl.isEmpty else {
            loadingIndex = false
            indexError = .invalidUrl
            indexErrorDetail = nil
            return
        }
        guard !loadingIndex else { return }
        loadingIndex = true
        indexError = nil
        indexErrorDetail = nil
        defer { loadingIndex = false }

        let ud = AppGroupContainer.shared.userDefaults
        let hasLocalIndex = index != nil
        let cachedEtag = (force || !hasLocalIndex) ? nil : ud.string(forKey: Keys.etag)
        let cachedLastMod = (force || !hasLocalIndex) ? nil : ud.string(forKey: Keys.lastModified)

        do {
            let result = try await client.fetchIndex(
                shareBaseUrl: shareUrl,
                etag: cachedEtag,
                lastModified: cachedLastMod
            )
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            switch result {
            case .notModified:
                lastSyncedAtMs = nowMs
                ud.set(NSNumber(value: nowMs), forKey: Keys.syncedAtMs)
            case .updated(let parsed, let rawJson, let etag, let lastMod):
                index = parsed
                lastSyncedAtMs = nowMs
                ud.set(rawJson, forKey: Keys.cachedIndex)
                ud.set(etag, forKey: Keys.etag)
                ud.set(lastMod, forKey: Keys.lastModified)
                ud.set(NSNumber(value: nowMs), forKey: Keys.syncedAtMs)
            }
        } catch let err as DropRelayError {
            indexError = err.kind
            indexErrorDetail = err.message
        } catch {
            indexError = .unreachable
            indexErrorDetail = error.localizedDescription
        }
    }
}

// MARK: - CampusResourcesView (资料库 + 文件快传)

public struct CampusResourcesView: View {
    private enum Tab: Int, CaseIterable, Identifiable {
        case share = 0
        case drop = 1

        var id: Int { rawValue }

        var title: String {
            switch self {
            case .share: "资料库"
            case .drop: "文件快传"
            }
        }
    }

    public let initialTab: Int
    public let initialQuery: String
    public let initialCode: String

    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.openURL) private var openURL

    @State private var store = DropRelayStore.shared
    @State private var selectedTab: Tab = .share
    @State private var searchQuery: String = ""
    @State private var currentDir: String = ""
    @State private var codeInput: String = ""
    @State private var clipboardCode: String?
    @State private var showConfigSheet: Bool = false
    @State private var p2pConfirmFile: DropRelayFile?

    public init(initialTab: Int = 0, initialQuery: String = "", initialCode: String = "") {
        self.initialTab = initialTab
        self.initialQuery = initialQuery
        self.initialCode = initialCode
    }

    private var shareUrl: String { store.effectiveShareUrl }
    private var dropUrl: String { store.effectiveDropUrl }
    private var hasShare: Bool { !shareUrl.isEmpty }
    private var hasDrop: Bool { !dropUrl.isEmpty }

    public var body: some View {
        VStack(spacing: 0) {
            if !hasShare && !hasDrop {
                GokeEmptyState(
                    systemImage: "folder",
                    title: "尚未配置校园资料库",
                    subtitle: "填写 DropRelay 资料库或快传地址即可浏览校园资料与快传文件",
                    primaryActionTitle: "配置地址",
                    onPrimaryAction: { showConfigSheet = true }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                if hasShare && hasDrop {
                    Picker("模式", selection: $selectedTab) {
                        ForEach(Tab.allCases) { tab in
                            Text(tab.title).tag(tab)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.vertical, GokeSpacing.sm)
                }

                if selectedTab == .share && hasShare {
                    shareLibraryContent
                } else if hasDrop {
                    dropTransferContent
                }
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("校园资料与快传")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if hasShare && selectedTab == .share {
                    Button {
                        Task { await store.refreshIndex(force: true) }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .gokeSpinningSymbol(isActive: store.loadingIndex)
                    }
                    .disabled(store.loadingIndex)
                    .accessibilityLabel("刷新资料索引")
                }

                Button {
                    showConfigSheet = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel("配置服务器地址")
            }
        }
        .sheet(isPresented: $showConfigSheet) {
            DropRelayConfigSheet(
                initialShareUrl: store.customShareUrl,
                initialDropUrl: store.customDropUrl,
                defaultShareUrl: store.campusDefaults.shareUrl,
                defaultDropUrl: store.campusDefaults.dropUrl,
                hasDefaults: store.hasCampusDefaults
            ) { newShare, newDrop in
                store.saveCustomUrls(share: newShare, drop: newDrop)
                showConfigSheet = false
                actions.showToast(message: "已保存服务器地址")
                if !store.effectiveShareUrl.isEmpty {
                    Task { await store.refreshIndex(force: true) }
                }
            }
            .gokePaperSheet(detents: [.medium])
        }
        .sheet(item: $p2pConfirmFile) { file in
            p2pFileConfirmSheet(file)
                .gokePaperSheet(detents: [.medium])
        }
        .task {
            store.hydrate()
            searchQuery = initialQuery
            codeInput = initialCode
            if !initialCode.isEmpty && hasDrop {
                selectedTab = .drop
            } else if hasShare && hasDrop {
                selectedTab = initialTab == 1 ? .drop : .share
            } else if hasDrop && !hasShare {
                selectedTab = .drop
            } else {
                selectedTab = .share
            }
            refreshClipboardCode()
            if hasShare && store.index == nil && !store.loadingIndex {
                await store.refreshIndex(force: false)
            }
        }
    }

    // MARK: - Share Library Tab

    @ViewBuilder
    private var shareLibraryContent: some View {
        let files = store.index?.files ?? []
        let trimmedQuery = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let isSearching = !trimmedQuery.isEmpty

        VStack(spacing: 0) {
            HStack(spacing: GokeSpacing.sm) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(theme.muted)

                TextField("搜索资料名称或路径", text: $searchQuery)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                    .submitLabel(.search)

                if isSearching {
                    Button {
                        searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(theme.muted)
                    }
                    .buttonStyle(.mintCompact)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 40)
            .background(theme.surfaceHighest, in: Capsule())
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.sm)

            if store.indexError != nil && store.index != nil {
                HStack {
                    GokeBadge("离线缓存模式（当前无法连接服务器）", tone: .secondary)
                    Spacer()
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.bottom, GokeSpacing.xs)
            }

            if !isSearching && !currentDir.isEmpty {
                breadcrumbBar
            }

            if store.loadingIndex && store.index == nil {
                VStack(spacing: GokeSpacing.md) {
                    ProgressView()
                    Text("正在加载资料库索引…")
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if store.index == nil && store.indexError != nil {
                let detail = store.indexErrorDetail?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let subtitle = detail.isEmpty
                    ? "请检查校园网连接或服务器地址配置"
                    : "请检查校园网连接或服务器地址配置\n\(detail)"
                GokeEmptyState(
                    systemImage: "wifi.slash",
                    title: "无法连接资料库",
                    subtitle: subtitle,
                    primaryActionTitle: "重试",
                    onPrimaryAction: {
                        Task { await store.refreshIndex(force: true) }
                    },
                    secondaryActionTitle: "在浏览器中打开",
                    onSecondaryAction: {
                        openExternal(shareUrl)
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if isSearching {
                let results = DropRelayLogic.searchFiles(files: files, query: trimmedQuery)
                if results.isEmpty {
                    GokeEmptyState(
                        systemImage: "magnifyingglass",
                        title: "没有找到相关资料",
                        subtitle: "尝试换一个关键词或清空搜索"
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        Section("搜索结果（\(results.count)）") {
                            ForEach(results) { file in
                                fileRow(file, showPath: true)
                            }
                        }
                        .listRowBackground(theme.surface)
                    }
                    .listStyle(.insetGrouped)
                    .gokePaperListStyle()
                }
            } else {
                let entries = DropRelayLogic.listDirectory(files: files, currentDir: currentDir)
                if entries.isEmpty {
                    GokeEmptyState(
                        systemImage: "folder",
                        title: "当前目录为空",
                        subtitle: "点击右上角刷新或返回上级目录"
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        Section {
                            ForEach(entries) { entry in
                                switch entry {
                                case .dir(let path, let name, let fileCount, let totalBytes):
                                    dirRow(path: path, name: name, fileCount: fileCount, totalBytes: totalBytes)
                                case .file(let file):
                                    fileRow(file, showPath: false)
                                }
                            }
                        }
                        .listRowBackground(theme.surface)
                    }
                    .listStyle(.insetGrouped)
                    .gokePaperListStyle()
                    .refreshable {
                        await store.refreshIndex(force: true)
                    }
                }
            }
        }
    }

    private var breadcrumbBar: some View {
        let segments = currentDir.split(separator: "/").map(String.init)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                Button("根目录") {
                    currentDir = ""
                }
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.accent)

                ForEach(Array(segments.enumerated()), id: \.offset) { idx, seg in
                    let targetPath = segments.prefix(idx + 1).joined(separator: "/")
                    let isLast = idx == segments.count - 1
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(theme.muted)
                    Button(seg) {
                        currentDir = targetPath
                    }
                    .disabled(isLast)
                    .font(GokeTypography.labelLarge)
                    .fontWeight(isLast ? .semibold : .regular)
                    .foregroundStyle(isLast ? theme.ink : theme.accent)
                }
            }
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.vertical, 6)
        }
    }

    private func dirRow(path: String, name: String, fileCount: Int, totalBytes: Int64) -> some View {
        HStack(spacing: 12) {
            Button {
                currentDir = path
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "folder.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(theme.accent)
                        .frame(width: 24)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(name)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .lineLimit(1)
                        Text("\(fileCount) 个文件 · \(DropRelayLogic.formatFileSize(totalBytes))")
                            .font(GokeTypography.labelMedium)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }
                    Spacer(minLength: 4)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            Button {
                openExternal(DropRelayLogic.dirZipUrl(baseUrl: shareUrl, dirPath: path))
            } label: {
                Image(systemName: "arrow.down.circle")
                    .font(.system(size: 18))
                    .foregroundStyle(theme.muted)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.mintCompact)
            .accessibilityLabel("打包下载 \(name)")
        }
    }

    private func fileRow(_ file: DropRelayFile, showPath: Bool) -> some View {
        Button {
            if file.size > DropRelayLogic.directDownloadLimitBytes {
                p2pConfirmFile = file
            } else {
                openExternal(DropRelayLogic.fileWebseedUrl(baseUrl: shareUrl, path: file.normalizedPath))
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "doc")
                    .font(.system(size: 18))
                    .foregroundStyle(theme.muted)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(file.displayName)
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)

                    HStack(spacing: 6) {
                        Text(DropRelayLogic.formatFileSize(file.size))
                            .font(GokeTypography.labelMedium)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)

                        if showPath {
                            Text(file.normalizedPath)
                                .font(GokeTypography.labelMedium)
                                .foregroundStyle(theme.muted.opacity(0.8))
                                .lineLimit(1)
                        }
                    }
                }

                Spacer(minLength: 4)

                Image(systemName: file.size > DropRelayLogic.directDownloadLimitBytes ? "arrow.up.right.square" : "arrow.down.circle")
                    .font(.system(size: 16))
                    .foregroundStyle(theme.muted.opacity(0.75))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintRow)
    }

    private func p2pFileConfirmSheet(_ file: DropRelayFile) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: GokeSpacing.md) {
                Text(file.displayName)
                    .font(GokeTypography.titleMedium)
                    .foregroundStyle(theme.ink)
                    .lineLimit(2)

                Text("文件大小 \(DropRelayLogic.formatFileSize(file.size))，超过 200 MB 建议在浏览器使用校园网 P2P 加速下载。")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)

                GokePrimaryActionButton(
                    "在浏览器中 P2P 下载",
                    systemImage: "arrow.up.right.square"
                ) {
                    p2pConfirmFile = nil
                    openExternal(DropRelayLogic.fileBrowserUrl(baseUrl: shareUrl, path: file.normalizedPath))
                }

                Button {
                    p2pConfirmFile = nil
                    openExternal(DropRelayLogic.fileWebseedUrl(baseUrl: shareUrl, path: file.normalizedPath))
                } label: {
                    Label("仍要直接下载", systemImage: "arrow.down.circle")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)

                Spacer()
            }
            .padding(GokeSpacing.lg)
            .background(theme.paper.ignoresSafeArea())
            .navigationTitle("大文件下载提示")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { p2pConfirmFile = nil }
                }
            }
        }
    }

    // MARK: - Drop Transfer Tab

    private var dropTransferContent: some View {
        let parsedCode = DropRelayLogic.extractDropCode(from: codeInput, dropBaseUrl: dropUrl)
        return List {
            Section {
                TextField("输入 6 位取件码或粘贴链接", text: $codeInput)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .font(GokeTypography.bodyLarge)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)

                if let clip = clipboardCode,
                   clip.caseInsensitiveCompare(codeInput.trimmingCharacters(in: .whitespacesAndNewlines)) != .orderedSame
                {
                    Button {
                        codeInput = clip
                        openExternal(DropRelayLogic.dropRoomUrl(baseUrl: dropUrl, code: clip))
                    } label: {
                        Label("从剪贴板识别到取件码：\(clip)", systemImage: "doc.on.clipboard")
                            .font(GokeTypography.labelLarge)
                            .foregroundStyle(theme.accent)
                    }
                }

                GokePrimaryActionButton(
                    "在浏览器中取件",
                    systemImage: "arrow.up.right.square",
                    isDisabled: parsedCode == nil
                ) {
                    if let code = parsedCode {
                        openExternal(DropRelayLogic.dropRoomUrl(baseUrl: dropUrl, code: code))
                    }
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            } header: {
                Text("接收文件")
            } footer: {
                Text("输入 6 位取件码后将直接跳转系统浏览器打开快传取件页。")
            }
            .listRowBackground(theme.surface)

            Section {
                Text("通过校园网 DropRelay 快速向电脑或同学发送文件与文本。")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)

                GokePrimaryActionButton(
                    "在浏览器中发件",
                    systemImage: "paperplane"
                ) {
                    openExternal(dropUrl)
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            } header: {
                Text("发送文件")
            }
            .listRowBackground(theme.surface)
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
    }

    private func refreshClipboardCode() {
        #if canImport(UIKit)
        if let str = UIPasteboard.general.string {
            clipboardCode = DropRelayLogic.extractDropCode(from: str, dropBaseUrl: dropUrl)
        }
        #endif
    }

    private func openExternal(_ urlString: String) {
        guard let url = URL(string: urlString) else {
            actions.showToast(message: "无法打开链接")
            return
        }
        openURL(url)
    }
}

// MARK: - DropRelay Config Sheet

private struct DropRelayConfigSheet: View {
    let defaultShareUrl: String
    let defaultDropUrl: String
    let hasDefaults: Bool
    let onSave: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.gokeTheme) private var theme

    @State private var shareInput: String
    @State private var dropInput: String

    init(
        initialShareUrl: String,
        initialDropUrl: String,
        defaultShareUrl: String,
        defaultDropUrl: String,
        hasDefaults: Bool,
        onSave: @escaping (String, String) -> Void
    ) {
        self.defaultShareUrl = defaultShareUrl
        self.defaultDropUrl = defaultDropUrl
        self.hasDefaults = hasDefaults
        self.onSave = onSave
        self._shareInput = State(initialValue: initialShareUrl)
        self._dropInput = State(initialValue: initialDropUrl)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(
                        defaultShareUrl.isEmpty ? "https://share.example.com" : defaultShareUrl,
                        text: $shareInput
                    )
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                } header: {
                    Text("资料库地址 (Share)")
                }
                .listRowBackground(theme.surface)

                Section {
                    TextField(
                        defaultDropUrl.isEmpty ? "https://drop.example.com" : defaultDropUrl,
                        text: $dropInput
                    )
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                } header: {
                    Text("文件快传地址 (Drop)")
                } footer: {
                    if hasDefaults {
                        Text("留空则自动使用校园默认服务器地址。")
                    }
                }
                .listRowBackground(theme.surface)

                if hasDefaults && (!shareInput.isEmpty || !dropInput.isEmpty) {
                    Section {
                        Button("恢复默认地址", role: .destructive) {
                            shareInput = ""
                            dropInput = ""
                        }
                    }
                    .listRowBackground(theme.surface)
                }
            }
            .gokePaperListStyle()
            .navigationTitle("服务器配置")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        onSave(shareInput, dropInput)
                    }
                }
            }
        }
    }
}
