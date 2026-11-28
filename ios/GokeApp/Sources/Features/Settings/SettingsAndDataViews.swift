import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(CoreImage)
import CoreImage
import CoreImage.CIFilterBuiltins
#endif
#if canImport(UIKit)
import UIKit
#endif
#if canImport(UniformTypeIdentifiers)
import UniformTypeIdentifiers
#endif

// MARK: - Shared Mint Paper Sub-Screen Primitives (Wave 2)

/// Wrapping flow layout so chip groups never clip mid-pill on 414pt screens.
internal struct GokeMintFlowLayout: Layout, Sendable {
    var horizontalSpacing: CGFloat = 8
    var verticalSpacing: CGFloat = 8

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxRowWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX > 0 && currentX + size.width > maxWidth {
                maxRowWidth = max(maxRowWidth, currentX - horizontalSpacing)
                currentX = 0
                currentY += rowHeight + verticalSpacing
                rowHeight = 0
            }
            currentX += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
        }
        maxRowWidth = max(maxRowWidth, max(0, currentX - horizontalSpacing))
        return CGSize(
            width: maxWidth.isFinite ? maxWidth : maxRowWidth,
            height: currentY + rowHeight
        )
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        var currentX = bounds.minX
        var currentY = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX > bounds.minX && currentX + size.width > bounds.maxX + 0.5 {
                currentX = bounds.minX
                currentY += rowHeight + verticalSpacing
                rowHeight = 0
            }
            subview.place(
                at: CGPoint(x: currentX, y: currentY),
                proposal: ProposedViewSize(size)
            )
            currentX += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// Custom Mint `- / +` capsule stepper replacing clashing grey UIKit `Stepper`.
internal struct GokeMintStepper: View {
    let title: String
    let valueText: String?
    @Binding var value: Int
    let range: ClosedRange<Int>
    let step: Int

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        _ title: String,
        valueText: String? = nil,
        value: Binding<Int>,
        in range: ClosedRange<Int>,
        step: Int = 1
    ) {
        self.title = title
        self.valueText = valueText
        self._value = value
        self.range = range
        self.step = step
    }

    private var canDecrement: Bool { value - step >= range.lowerBound }
    private var canIncrement: Bool { value + step <= range.upperBound }

    var body: some View {
        let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
        HStack(spacing: GokeSpacing.sm) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                if let valueText {
                    Text(valueText)
                        .font(GokeTypography.labelMedium)
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: valueText)
                        .foregroundStyle(theme.accent)
                }
            }

            Spacer(minLength: GokeSpacing.sm)

            HStack(spacing: 0) {
                Button {
                    let next = max(range.lowerBound, value - step)
                    value = next
                } label: {
                    Image(systemName: "minus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(canDecrement ? theme.ink : theme.muted.opacity(0.35))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(!canDecrement)
                .accessibilityLabel("减少\(title)")

                Rectangle()
                    .fill(theme.outlineVariant.opacity(0.55))
                    .frame(width: 0.75, height: 18)

                Button {
                    let next = min(range.upperBound, value + step)
                    value = next
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(canIncrement ? theme.ink : theme.muted.opacity(0.35))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(!canIncrement)
                .accessibilityLabel("增加\(title)")
            }
            .background {
                Capsule()
                    .fill(theme.surfaceHighest)
                    .frame(height: 34)
            }
        }
    }
}

/// Standardized 22pt-wide optical monochrome icon matching `MeView`.
internal struct GokePaperIcon: View {
    let systemImage: String
    var color: Color? = nil

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 18, weight: .regular))
            .imageScale(.medium)
            .symbolRenderingMode(.monochrome)
            .foregroundStyle(color ?? theme.muted)
            .frame(width: 22, height: 24, alignment: .center)
    }
}

private struct GokePaperListModifier: ViewModifier {
    @Environment(\.gokeTheme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(theme.paper.ignoresSafeArea())
            .listRowSeparatorTint(theme.outlineVariant.opacity(0.45))
            .tint(theme.accent)
    }
}

private struct GokePaperSheetChromeModifier: ViewModifier {
    let detents: Set<PresentationDetent>
    let dragIndicator: Visibility
    @Environment(\.gokeTheme) private var theme

    func body(content: Content) -> some View {
        content.gokePaperSheet(theme, detents: detents, dragIndicator: dragIndicator)
    }
}

/// Crisp Mint Paper inline capsule button replacing stock UIKit `.bordered` / `.borderedProminent` controls.
/// Maintains a `>=44pt` vertical hit zone while rendering a compact visual capsule pill.
internal struct GokeMintActionPill: View {
    enum Style {
        case prominent
        case tonal
        case surface
        case destructive
    }

    let title: String
    var systemImage: String? = nil
    var style: Style = .tonal
    var isLoading: Bool = false
    var isDisabled: Bool = false
    var role: ButtonRole? = nil
    let action: () -> Void

    @Environment(\.gokeTheme) private var theme

    init(
        _ title: String,
        systemImage: String? = nil,
        style: Style = .tonal,
        isLoading: Bool = false,
        isDisabled: Bool = false,
        role: ButtonRole? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.style = style
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.role = role
        self.action = action
    }

    private var backgroundColor: Color {
        switch style {
        case .prominent: theme.accent
        case .tonal: theme.accentContainer
        case .surface: theme.surface
        case .destructive: theme.errorContainer
        }
    }

    private var foregroundColor: Color {
        switch style {
        case .prominent: theme.onAccent
        case .tonal: theme.onAccentContainer
        case .surface: theme.ink
        case .destructive: theme.error
        }
    }

    var body: some View {
        let effectiveDisabled = isDisabled || isLoading
        Button(role: role, action: action) {
            HStack(spacing: 5) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(foregroundColor)
                } else {
                    if let systemImage {
                        Image(systemName: systemImage)
                            .font(.caption.weight(.semibold))
                    }
                    Text(title)
                        .font(GokeTypography.labelMedium.weight(.semibold))
                        .monospacedDigit()
                        .lineLimit(1)
                }
            }
            .foregroundStyle(effectiveDisabled ? foregroundColor.opacity(0.55) : foregroundColor)
            .padding(.horizontal, 13)
            .padding(.vertical, 7)
            .background(
                Capsule()
                    .fill(effectiveDisabled ? backgroundColor.opacity(0.55) : backgroundColor)
            )
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .disabled(effectiveDisabled)
    }
}

internal extension View {
    /// Applies cohesive Mint Paper canvas background (`theme.paper`), soft separator tint, and accent tint to `List`/`Form`.
    func gokePaperListStyle() -> some View {
        modifier(GokePaperListModifier())
    }

    /// Applies `gokeSheetChrome` with warm Mint Paper `presentationBackground(theme.paper)`.
    func gokePaperSheetChrome(
        detents: Set<PresentationDetent> = [],
        dragIndicator: Visibility = .visible
    ) -> some View {
        modifier(GokePaperSheetChromeModifier(detents: detents, dragIndicator: dragIndicator))
    }
}

// MARK: - Shared Settings UI Helpers

@MainActor
private enum SettingsUIHelpers {
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
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f
    }()

    private static let backupStampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd-HHmmss"
        return f
    }()

    private static let fileSizeFormatter: ByteCountFormatter = {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter
    }()

    static func formatDateTime(millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return dateTimeFormatter.string(from: date)
    }

    static func formatBackupStamp(date: Date = Date()) -> String {
        backupStampFormatter.string(from: date)
    }

    static func formatFileSize(_ bytes: Int64) -> String {
        fileSizeFormatter.string(fromByteCount: bytes)
    }

    static func entityTypeLabel(_ type: EntityType) -> String {
        switch type {
        case .SEMESTER: "学期"
        case .COURSE: "课程"
        case .EXCEPTION: "调停课"
        case .EVENT: "日程"
        case .TASK: "待办"
        case .LIST: "清单"
        }
    }

    static func sourceLabel(_ raw: String) -> String {
        switch Source(rawValue: raw) {
        case .MANUAL: "手动"
        case .IMPORT: "导入"
        case .PARCEL: "快递"
        case .AI: "AI"
        case .CODE: "脚本"
        case nil: raw
        }
    }

    static func argbFromColor(_ color: Color, fallback: UInt32) -> UInt32 {
        #if canImport(UIKit)
        let uiColor = UIColor(color)
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 1
        if uiColor.getRed(&r, green: &g, blue: &b, alpha: &a) {
            let ri = UInt32(max(0, min(255, Int((r * 255.0).rounded()))))
            let gi = UInt32(max(0, min(255, Int((g * 255.0).rounded()))))
            let bi = UInt32(max(0, min(255, Int((b * 255.0).rounded()))))
            return 0xFF00_0000 | (ri << 16) | (gi << 8) | bi
        }
        #endif
        return fallback
    }

    static func hexString(fromARGB argb: UInt32) -> String {
        String(format: "#%06X", argb & 0x00FF_FFFF)
    }

    static func parseHexColor(_ raw: String) -> UInt32? {
        let cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
            .uppercased()
        guard cleaned.count == 6, let rgb = UInt32(cleaned, radix: 16) else { return nil }
        return 0xFF00_0000 | rgb
    }

    static func isLightColor(hex: UInt32) -> Bool {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        let luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance > 0.62
    }
}

// MARK: - 1. AppearanceSettingsView

public struct AppearanceSettingsView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var showHexAlert: Bool = false
    @State private var hexDraft: String = ""

    public init() {}

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    private var effectiveThemeSource: ThemeSource {
        settingsStore.settings.themeSource == .CUSTOM ? .CUSTOM : .PRESET
    }

    private var themeSourceBinding: Binding<ThemeSource> {
        Binding(
            get: { effectiveThemeSource },
            set: { newSource in
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    settingsStore.update { $0.themeSource = newSource }
                }
            }
        )
    }

    private var darkModeBinding: Binding<DarkMode> {
        Binding(
            get: { settingsStore.settings.darkMode },
            set: { newMode in
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    settingsStore.update { $0.darkMode = newMode }
                }
            }
        )
    }

    private var customColorBinding: Binding<Color> {
        Binding(
            get: { Color(hex: settingsStore.settings.seedColorARGB & 0x00FF_FFFF) },
            set: { newColor in
                let fallback = settingsStore.settings.seedColorARGB
                let encoded = SettingsUIHelpers.argbFromColor(newColor, fallback: fallback)
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    settingsStore.update {
                        $0.themeSource = .CUSTOM
                        $0.seedColorARGB = encoded
                    }
                }
            }
        )
    }

    private var currentSeedHex: UInt32 {
        settingsStore.settings.seedColorARGB & 0x00FF_FFFF
    }

    private var customPreviewTheme: GokeTheme {
        GokeTheme.custom(seedHex: currentSeedHex)
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.xl) {
                themeColorSection
                darkModeSection
            }
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.top, GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("外观")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .alert("HEX 颜色", isPresented: $showHexAlert) {
            TextField("#2E776C", text: $hexDraft)
                #if os(iOS)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                #endif
            Button("应用") {
                if let parsed = SettingsUIHelpers.parseHexColor(hexDraft) {
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        settingsStore.update {
                            $0.themeSource = .CUSTOM
                            $0.seedColorARGB = parsed
                        }
                    }
                } else {
                    actions.showToast(message: "格式：#RRGGBB")
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("格式：#RRGGBB")
        }
        .onChange(of: settingsStore.settings) { _, _ in
            actions.refreshSideEffects(snapshot: container.liveSnapshot, triggerSync: false)
        }
    }

    private var themeColorSection: some View {
        VStack(alignment: .leading, spacing: GokeSpacing.md) {
            Text("主题色")
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 4)

            GokeSegmentedPill(
                options: [
                    (ThemeSource.PRESET, "预设"),
                    (ThemeSource.CUSTOM, "自定义"),
                ],
                selection: themeSourceBinding
            )

            if effectiveThemeSource == .PRESET {
                presetGrid
                    .padding(.top, 4)
            } else {
                customSeedControls
                    .padding(.top, 4)
            }
        }
    }

    private var presetGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
        return LazyVGrid(columns: columns, spacing: 14) {
            ForEach(GokeThemePreset.allCases) { preset in
                let selected = settingsStore.settings.preset.lowercased() == preset.rawValue
                Button {
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        settingsStore.update {
                            $0.themeSource = .PRESET
                            $0.preset = preset.rawValue
                        }
                    }
                } label: {
                    VStack(spacing: 4) {
                        ZStack {
                            if selected {
                                Circle()
                                    .strokeBorder(preset.seedColor, lineWidth: 2)
                                    .frame(width: 52, height: 52)
                                    .transition(simplifyMotion ? .opacity : .scale(scale: 0.85).combined(with: .opacity))
                            }
                            Circle()
                                .fill(preset.seedColor)
                                .frame(width: 44, height: 44)
                            if selected {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundStyle(
                                        SettingsUIHelpers.isLightColor(hex: preset.seedHex)
                                            ? Color(hex: 0x1E2A26)
                                            : Color(hex: 0xFFFFFF)
                                    )
                                    .transition(simplifyMotion ? .opacity : .scale(scale: 0.7).combined(with: .opacity))
                            }
                        }
                        .frame(width: 52, height: 52)

                        Text(preset.displayName)
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("\(preset.displayName)\(selected ? "，已选中" : "")")
            }
        }
        .padding(.vertical, 4)
    }

    private var customSeedControls: some View {
        let preview = customPreviewTheme
        let swatches: [(String, Color)] = [
            ("种子色", Color(hex: currentSeedHex)),
            ("主色", preview.accent),
            ("容器", preview.accentContainer),
            ("辅助", preview.secondaryContainer),
        ]

        return VStack(alignment: .leading, spacing: GokeSpacing.md) {
            VStack(spacing: 0) {
                ColorPicker("选取颜色", selection: customColorBinding, supportsOpacity: false)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 10)

                Button {
                    hexDraft = SettingsUIHelpers.hexString(fromARGB: settingsStore.settings.seedColorARGB)
                    showHexAlert = true
                } label: {
                    HStack {
                        Text("HEX 颜色")
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                        Spacer()
                        Text(SettingsUIHelpers.hexString(fromARGB: settingsStore.settings.seedColorARGB))
                            .font(.subheadline.monospaced())
                            .foregroundStyle(theme.accent)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(theme.muted.opacity(0.65))
                    }
                    .padding(.horizontal, 4)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintRow)
            }

            // Compact 4-capsule tonal strip matching Android AppearanceScreen
            HStack(spacing: GokeSpacing.sm) {
                ForEach(swatches, id: \.0) { item in
                    Capsule()
                        .fill(item.1)
                        .frame(height: 28)
                        .overlay {
                            Capsule()
                                .strokeBorder(theme.outlineVariant.opacity(0.55), lineWidth: 0.75)
                        }
                        .accessibilityLabel(item.0)
                }
            }
            .padding(.top, 2)
        }
    }

    private var darkModeSection: some View {
        VStack(alignment: .leading, spacing: GokeSpacing.md) {
            Text("深色模式")
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 4)

            GokeSegmentedPill(
                options: [
                    (DarkMode.SYSTEM, "跟随系统"),
                    (DarkMode.LIGHT, "浅色"),
                    (DarkMode.DARK, "深色"),
                ],
                selection: darkModeBinding
            )
        }
    }
}

// MARK: - 1b. PreferencesSettingsView (显示与交互)

public struct PreferencesSettingsView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage(GokePerformanceMode.userDefaultsKey) private var performanceModeRaw: String = GokePerformanceMode.auto.rawValue

    private let blockDurationPresets = [15, 20, 25, 30, 45, 60, 90, 120]

    public init() {}

    private var selectedPerformanceMode: GokePerformanceMode {
        GokePerformanceMode(rawValue: performanceModeRaw) ?? .auto
    }

    private var fluencyStatusSummary: String? {
        guard selectedPerformanceMode != .powerSaver else { return nil }
        guard performance.isDegraded || performance.shouldDisableGlassAndBlur || performance.shouldSimplifyMotion else {
            return nil
        }
        var reasons: [String] = []
        if performance.isLowPowerModeEnabled { reasons.append("低电量") }
        if performance.isThermallyThrottled { reasons.append("设备发热") }
        if performance.isLowMemoryDevice { reasons.append("内存优化") }
        if performance.reduceTransparency { reasons.append("降低透明度") }
        if performance.reduceMotion { reasons.append("减弱动态效果") }
        guard !reasons.isEmpty else { return nil }
        return "已自动降级：\(reasons.joined(separator: "、"))"
    }

    private func friendlyModeDescription(_ mode: GokePerformanceMode) -> String {
        switch mode {
        case .auto: "低电量或发热时自动精简"
        case .standard: "完整视觉与动效"
        case .powerSaver: "精简模糊与动效"
        }
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.xl) {
                scheduleSection
                todoSection
                feedbackSection
                fluencySection
            }
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.top, GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.paper.ignoresSafeArea())
        .navigationTitle("显示与交互")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .onChange(of: settingsStore.settings) { _, _ in
            actions.refreshSideEffects(snapshot: container.liveSnapshot, triggerSync: false)
        }
    }

    private var scheduleSection: some View {
        paperSection("课表与日历") {
            paperToggleRow(
                "课表显示教师",
                isOn: Binding(
                    get: { settingsStore.settings.showTeacher },
                    set: { val in settingsStore.update { $0.showTeacher = val } }
                )
            )

            paperToggleRow(
                "周一作为一周开始",
                isOn: Binding(
                    get: { settingsStore.settings.weekStartsMonday },
                    set: { val in settingsStore.update { $0.weekStartsMonday = val } }
                )
            )
        }
    }

    private var todoSection: some View {
        paperSection("待办与手势") {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center) {
                    Text("默认时间块时长")
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)
                    Spacer(minLength: GokeSpacing.sm)
                    Text("\(settingsStore.settings.defaultBlockMinutes) 分钟")
                        .font(GokeTypography.bodyMedium.weight(.medium))
                        .monospacedDigit()
                        .foregroundStyle(theme.accent)
                }

                GokeMintFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                    ForEach(blockDurationPresets, id: \.self) { mins in
                        let selected = settingsStore.settings.defaultBlockMinutes == mins
                        Button {
                            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                                settingsStore.update { $0.defaultBlockMinutes = mins }
                            }
                        } label: {
                            Text("\(mins)分")
                                .font(GokeTypography.labelMedium.weight(selected ? .semibold : .regular))
                                .monospacedDigit()
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(
                                    selected ? theme.accentContainer : theme.surfaceHighest,
                                    in: Capsule()
                                )
                                .foregroundStyle(selected ? theme.onAccentContainer : theme.ink)
                        }
                        .buttonStyle(.mintCompact)
                    }
                }

                GokeMintStepper(
                    "微调时长",
                    value: Binding(
                        get: { settingsStore.settings.defaultBlockMinutes },
                        set: { val in settingsStore.update { $0.defaultBlockMinutes = val } }
                    ),
                    in: 5...240,
                    step: 5
                )
                .padding(.top, 4)
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 10)

            paperToggleRow(
                "交换滑动方向",
                subtitle: "左滑完成，右滑改期",
                isOn: Binding(
                    get: { settingsStore.settings.swapSwipe },
                    set: { val in settingsStore.update { $0.swapSwipe = val } }
                )
            )
        }
    }

    private var feedbackSection: some View {
        paperSection("交互反馈") {
            paperToggleRow(
                "震动反馈",
                subtitle: "长按、拖拽与滑动提交时轻震",
                isOn: Binding(
                    get: { settingsStore.settings.hapticFeedback },
                    set: { val in
                        settingsStore.update { $0.hapticFeedback = val }
                        if val {
                            GokeHaptics.swipeCommit()
                        }
                    }
                )
            )

            paperToggleRow(
                "音效反馈",
                subtitle: "完成待办时播放轻提示音",
                isOn: Binding(
                    get: { settingsStore.settings.soundFeedback },
                    set: { val in
                        settingsStore.update { $0.soundFeedback = val }
                        if val {
                            ReminderChime.gentleChime.playPreview()
                        }
                    }
                )
            )
        }
    }

    private var fluencySection: some View {
        paperSection("显示与流畅度", footer: fluencyStatusSummary) {
            let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
            ForEach(GokePerformanceMode.allCases) { mode in
                let isSelected = selectedPerformanceMode == mode
                Button {
                    withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                        performanceModeRaw = mode.rawValue
                    }
                } label: {
                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(mode.displayName)
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)
                            Text(friendlyModeDescription(mode))
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                        }
                        Spacer(minLength: 8)
                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(theme.accent)
                                .transition(simplifyMotion ? .opacity : .scale(scale: 0.7).combined(with: .opacity))
                        }
                    }
                    .padding(.horizontal, 4)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintRow)
            }
        }
    }

    private func paperSection<Content: View>(
        _ title: String,
        footer: String? = nil,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 4)
                .padding(.bottom, 2)
                .accessibilityAddTraits(.isHeader)

            VStack(alignment: .leading, spacing: 2) {
                content()
            }

            if let footer, !footer.isEmpty {
                Text(footer)
                    .font(.caption)
                    .foregroundStyle(theme.muted)
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
            }
        }
    }

    private func paperToggleRow(
        _ title: String,
        subtitle: String? = nil,
        isOn: Binding<Bool>
    ) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                }
            }
        }
        .tint(theme.accent)
        .padding(.horizontal, 4)
        .padding(.vertical, 10)
    }
}

// MARK: - 2. ReminderSettingsView

public struct ReminderSettingsView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(ReminderChime.userDefaultsKey) private var selectedChimeRaw: String = ReminderChime.systemDefault.rawValue

    @State private var authSnapshot: ReminderAuthorizationSnapshot = .denied
    @State private var plannedReminders: [PlannedReminder] = []
    @State private var liveActivitiesEnabled: Bool = false
    @State private var activeLiveCourseId: String?
    @State private var isRescheduling: Bool = false
    @State private var settingsRescheduleTask: Task<Void, Never>?

    private let courseLeadOptions = [0, 5, 10, 15, 20, 30, 45, 60]
    private let examLeadOptions = [0, 15, 30, 60, 120, 180, 1440, 2880]
    private let quickLeadOptions = [0, 5, 10, 15, 30, 60, 120, 1440]

    public init() {}

    private var selectedChime: ReminderChime {
        ReminderChime(rawValue: selectedChimeRaw) ?? .systemDefault
    }

    private var digestTimeBinding: Binding<Date> {
        Binding(
            get: {
                LocalDate.today()
                    .atTime(hour: settingsStore.settings.digestHour, minute: settingsStore.settings.digestMinute)
                    .toDate()
            },
            set: { newDate in
                let ldt = LocalDateTime.fromDate(newDate)
                settingsStore.update {
                    $0.digestHour = ldt.hour
                    $0.digestMinute = ldt.minute
                }
            }
        )
    }

    public var body: some View {
        Form {
            Group {
                permissionSection
                reminderSwitchesSection
                quickAddRemindSection
                alertChimeAndSnoozeSection
                dailyDigestSection
                liveActivitySection
                upcomingQueueSection
            }
            .listRowBackground(theme.surface)
        }
        .gokePaperListStyle()
        .navigationTitle("提醒与通知")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .task {
            await refreshStatusAndPlan()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await refreshStatusAndPlan() }
            }
        }
        .onChange(of: settingsStore.settings) { _, newSettings in
            settingsRescheduleTask?.cancel()
            settingsRescheduleTask = Task {
                _ = await container.reminderScheduler.reschedule(
                    snapshot: container.snapshot,
                    settings: newSettings,
                    force: true
                )
                guard !Task.isCancelled else { return }
                await refreshStatusAndPlan()
            }
        }
        .onDisappear {
            settingsRescheduleTask?.cancel()
            settingsRescheduleTask = nil
        }
    }

    private var permissionSection: some View {
        Section {
            LabeledContent("通知权限") {
                Label(
                    authSnapshot.isAuthorized ? "已开启" : "未授权",
                    systemImage: authSnapshot.isAuthorized ? "checkmark.circle.fill" : "exclamationmark.circle.fill"
                )
                .font(.subheadline)
                .foregroundStyle(authSnapshot.isAuthorized ? theme.success : theme.error)
            }

            if !authSnapshot.isAuthorized {
                Button("开启通知权限") {
                    Task {
                        _ = try? await container.reminderScheduler.requestAuthorization()
                        await refreshStatusAndPlan()
                    }
                }
                Button("打开系统设置") {
                    SettingsUIHelpers.openAppSettings()
                }
            }

            NavigationLink(value: GokeRoute.reliability) {
                Label("权限与自检", systemImage: "checkmark.shield")
            }
        }
    }

    private func ensureNotificationPermissionIfNeeded(enabled: Bool) {
        guard enabled, !authSnapshot.isAuthorized else { return }
        Task {
            _ = try? await container.reminderScheduler.requestAuthorization()
            await refreshStatusAndPlan()
        }
    }

    private var reminderSwitchesSection: some View {
        Section {
            Toggle(
                "上课提醒",
                isOn: Binding(
                    get: { settingsStore.settings.remindCourse },
                    set: { val in
                        settingsStore.update { $0.remindCourse = val }
                        ensureNotificationPermissionIfNeeded(enabled: val)
                    }
                )
            )

            if settingsStore.settings.remindCourse {
                NavigationLink {
                    ReminderLeadMultiPickerView(
                        title: "提前时间",
                        options: courseLeadOptions,
                        selected: Binding(
                            get: { settingsStore.settings.effectiveCourseRemindMinutes },
                            set: { list in
                                let normalized = Reminders.normalize(list)
                                guard let first = normalized.first else { return }
                                settingsStore.update {
                                    $0.courseRemindMinutes = first
                                    $0.courseRemindMinutesList = normalized
                                }
                            }
                        )
                    )
                } label: {
                    LabeledContent("提前时间") {
                        Text(formatLeadSummary(settingsStore.settings.effectiveCourseRemindMinutes))
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }
            }

            Toggle(
                "考试提醒",
                isOn: Binding(
                    get: { settingsStore.settings.remindExam },
                    set: { val in
                        settingsStore.update { $0.remindExam = val }
                        ensureNotificationPermissionIfNeeded(enabled: val)
                    }
                )
            )

            if settingsStore.settings.remindExam {
                NavigationLink {
                    ReminderLeadMultiPickerView(
                        title: "提前时间",
                        options: examLeadOptions,
                        selected: Binding(
                            get: { settingsStore.settings.effectiveExamRemindMinutes },
                            set: { list in
                                let normalized = Reminders.normalize(list)
                                guard !normalized.isEmpty else { return }
                                settingsStore.update {
                                    $0.examRemindMinutesList = normalized
                                }
                            }
                        )
                    )
                } label: {
                    LabeledContent("提前时间") {
                        Text(formatLeadSummary(settingsStore.settings.effectiveExamRemindMinutes))
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }

                Toggle(
                    "临考强提醒",
                    isOn: Binding(
                        get: { settingsStore.settings.examRemindPersistent },
                        set: { val in settingsStore.update { $0.examRemindPersistent = val } }
                    )
                )
            }

            Toggle(
                "待办提醒",
                isOn: Binding(
                    get: { settingsStore.settings.remindTask },
                    set: { val in
                        settingsStore.update { $0.remindTask = val }
                        ensureNotificationPermissionIfNeeded(enabled: val)
                    }
                )
            )

            Toggle(
                "日程提醒",
                isOn: Binding(
                    get: { settingsStore.settings.remindEvent },
                    set: { val in
                        settingsStore.update { $0.remindEvent = val }
                        ensureNotificationPermissionIfNeeded(enabled: val)
                    }
                )
            )
        } header: {
            Text("提醒类型")
        } footer: {
            if settingsStore.settings.remindExam && settingsStore.settings.examRemindPersistent {
                Text("考前 2 小时内使用强提醒")
            }
        }
    }

    private var quickAddRemindSection: some View {
        let qCfg = settingsStore.settings.quickAddConfig
        let kw = qCfg.normalizedKeyword.isEmpty ? QuickAddConfig.defaultRemindKeyword : qCfg.normalizedKeyword
        let isModified = qCfg != .default

        return Section {
            HStack {
                Text("关键词")
                Spacer()
                TextField(
                    QuickAddConfig.defaultRemindKeyword,
                    text: Binding(
                        get: { settingsStore.settings.quickRemindKeyword },
                        set: { raw in
                            let cleaned = raw
                                .replacingOccurrences(of: #"[\s!！#@/]"#, with: "", options: .regularExpression)
                            let limited = String(cleaned.prefix(8))
                            settingsStore.update { $0.quickRemindKeyword = limited }
                        }
                    )
                )
                .multilineTextAlignment(.trailing)
                .foregroundStyle(theme.accent)
            }

            NavigationLink {
                ReminderLeadMultiPickerView(
                    title: "提前时间",
                    options: quickLeadOptions,
                    selected: Binding(
                        get: { qCfg.effectiveRemindMinutes },
                        set: { list in
                            let normalized = Reminders.normalize(list)
                            guard !normalized.isEmpty else { return }
                            settingsStore.update { $0.quickRemindMinutesList = normalized }
                        }
                    )
                )
            } label: {
                LabeledContent("提前时间") {
                    Text(formatLeadSummary(qCfg.effectiveRemindMinutes))
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                }
            }

            Toggle(
                "「\(kw)!」强提醒",
                isOn: Binding(
                    get: { settingsStore.settings.quickRemindBangPersistent },
                    set: { val in settingsStore.update { $0.quickRemindBangPersistent = val } }
                )
            )

            if settingsStore.settings.quickRemindBangPersistent {
                NavigationLink {
                    ReminderLeadMultiPickerView(
                        title: "强提醒提前",
                        options: quickLeadOptions,
                        selected: Binding(
                            get: { qCfg.effectivePersistentRemindMinutes },
                            set: { list in
                                let normalized = Reminders.normalize(list)
                                guard !normalized.isEmpty else { return }
                                settingsStore.update { $0.quickRemindPersistentMinutesList = normalized }
                            }
                        )
                    )
                } label: {
                    LabeledContent("强提醒提前") {
                        Text(formatLeadSummary(qCfg.effectivePersistentRemindMinutes))
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }
            }

            if isModified {
                Button("恢复默认") {
                    settingsStore.update {
                        $0.quickRemindKeyword = QuickAddConfig.defaultRemindKeyword
                        $0.quickRemindMinutesList = QuickAddConfig.defaultRemindMinutes
                        $0.quickRemindPersistentMinutesList = QuickAddConfig.defaultRemindMinutes
                        $0.quickRemindBangPersistent = true
                    }
                    actions.showToast(message: "已恢复默认配置")
                }
                .foregroundStyle(theme.accent)
            }
        } header: {
            Text("提醒简写")
        } footer: {
            Text("输入「\(kw)」触发提醒，「\(kw)!」触发强提醒")
        }
    }

    private func formatLeadSummary(_ list: [Int]) -> String {
        let normalized = Reminders.normalize(list)
        guard !normalized.isEmpty else { return "未设置" }
        return normalized.map { m in
            m == 0 ? "准时" : "提前\(ReminderScheduler.formatLeadTime(m))"
        }.joined(separator: "、")
    }

    private var alertChimeAndSnoozeSection: some View {
        Section {
            ForEach(ReminderChime.allCases) { chime in
                let isSelected = selectedChime == chime
                HStack(spacing: 12) {
                    Button {
                        selectedChimeRaw = chime.rawValue
                        chime.playPreview()
                        Task {
                            _ = await container.reminderScheduler.reschedule(
                                snapshot: container.snapshot,
                                settings: settingsStore.settings,
                                force: true
                            )
                        }
                    } label: {
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(chime.displayName)
                                    .font(.body)
                                    .foregroundStyle(theme.ink)
                                Text(chime.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(theme.muted)
                            }
                            Spacer(minLength: 8)
                            if isSelected {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(theme.accent)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)

                    if chime != .silent {
                        Button {
                            chime.playPreview()
                        } label: {
                            Image(systemName: "speaker.wave.2")
                                .font(.subheadline)
                                .foregroundStyle(theme.accent)
                                .frame(width: 36, height: 36)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("试听\(chime.displayName)")
                    }
                }
            }

            Button {
                let sampleTarget = LocalDateTime.now().addingMinutes(25)
                let samplePayload = QuickSnoozePayload(
                    entityId: plannedReminders.first?.entityId ?? "",
                    category: plannedReminders.first?.category ?? .course,
                    title: plannedReminders.first?.title ?? "高等数学（上）",
                    subtitle: plannedReminders.first?.subtitle ?? "08:30–10:05 · 教3-201",
                    body: plannedReminders.first?.body ?? "25分钟后上课 · 教3-201",
                    targetAt: plannedReminders.first?.targetAt ?? sampleTarget,
                    timeSensitive: plannedReminders.first?.timeSensitive ?? false,
                    deepLink: plannedReminders.first?.deepLink ?? "goke://today"
                )
                NotificationCenter.default.post(
                    name: .gokePresentQuickSnooze,
                    object: nil,
                    userInfo: ["payload": samplePayload]
                )
            } label: {
                Label("预览稍后提醒", systemImage: "alarm")
            }
        } header: {
            Text("提示音")
        }
    }

    private var dailyDigestSection: some View {
        Section("每日摘要") {
            Toggle(
                "开启每日摘要",
                isOn: Binding(
                    get: { settingsStore.settings.dailyDigest },
                    set: { val in
                        settingsStore.update { $0.dailyDigest = val }
                        ensureNotificationPermissionIfNeeded(enabled: val)
                    }
                )
            )

            if settingsStore.settings.dailyDigest {
                DatePicker("摘要时间", selection: digestTimeBinding, displayedComponents: .hourAndMinute)
            }
        }
    }

    private var liveActivitySection: some View {
        Section {
            NavigationLink(value: GokeRoute.widgetsGallery) {
                LabeledContent("实时活动与灵动岛") {
                    Text(liveActivitiesEnabled ? (activeLiveCourseId != nil ? "运行中" : "已就绪") : "未开启")
                        .font(.subheadline)
                        .foregroundStyle(liveActivitiesEnabled ? theme.success : theme.muted)
                }
            }
        } footer: {
            Text("课前 45 分钟自动上岛")
        }
    }

    private var upcomingQueueSection: some View {
        Section("即将提醒（\(plannedReminders.count)）") {
            if plannedReminders.isEmpty {
                GokeEmptyState(
                    systemImage: "bell.slash",
                    title: "未来 7 天暂无提醒",
                    subtitle: nil
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(Array(plannedReminders.prefix(6)), id: \.identifier) { item in
                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.body)
                                .foregroundStyle(theme.ink)
                                .lineLimit(1)
                            Text(item.body)
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 8)
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("\(item.fireAt.month)/\(item.fireAt.day) \(item.fireAt.time.description)")
                                .font(.footnote)
                                .monospacedDigit()
                                .foregroundStyle(theme.accent)
                            HStack(spacing: 4) {
                                if item.category == .exam {
                                    GokeBadge("考试", tone: .tertiary)
                                }
                                if item.timeSensitive {
                                    GokeBadge("强提醒", tone: .secondary)
                                }
                            }
                        }
                    }
                }
            }

            Button {
                Task { await forceReschedule() }
            } label: {
                HStack {
                    Text(isRescheduling ? "校准中..." : "重新校准")
                    if isRescheduling {
                        Spacer()
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isRescheduling)
        }
    }

    private func refreshStatusAndPlan() async {
        authSnapshot = await container.reminderScheduler.authorizationStatus()
        liveActivitiesEnabled = await container.liveActivityManager.areActivitiesEnabled()
        activeLiveCourseId = await container.liveActivityManager.activeCourseId()
        plannedReminders = container.reminderScheduler.planReminders(
            snapshot: container.snapshot,
            settings: settingsStore.settings
        )
    }

    private func forceReschedule() async {
        isRescheduling = true
        defer { isRescheduling = false }
        let scheduled = await container.reminderScheduler.reschedule(
            snapshot: container.snapshot,
            settings: settingsStore.settings,
            force: true
        )
        await refreshStatusAndPlan()
        actions.showToast(message: "已重新校准 \(scheduled) 条本地提醒")
    }
}

// MARK: - 3. DataManagementView

public struct DataManagementView: View {
    private struct BackupFileItem: Identifiable, Hashable {
        let url: URL
        let name: String
        let sizeBytes: Int64
        let modifiedAt: Date

        var id: String { url.path }
    }

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme

    @State private var backupFiles: [BackupFileItem] = []
    @State private var exportJSONURL: URL?
    @State private var exportICSURL: URL?
    @State private var exportXLSXURL: URL?
    @State private var showResetPrefsConfirm: Bool = false

    private let backupKeepPresets = [7, 14, 30]

    public init() {}

    private var live: AppSnapshot { container.liveSnapshot }

    private var trashCount: Int {
        container.snapshot.allRecords().filter { $0.deletedAt != nil }.count
    }

    public var body: some View {
        List {
            Group {
                storageStatsSection
                exportSection
                importAndHistorySection
                autoBackupSection
                localBackupsListSection
                resetSection
            }
            .listRowBackground(theme.surface)
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("数据与备份")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .confirmationDialog(
            "恢复默认偏好设置？",
            isPresented: $showResetPrefsConfirm,
            titleVisibility: .visible
        ) {
            Button("恢复默认", role: .destructive) {
                settingsStore.reset()
                actions.refreshSideEffects(snapshot: container.liveSnapshot, triggerSync: false)
                actions.showToast(message: "已恢复默认应用偏好设置")
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("仅重置主题、提醒与显示偏好，不会删除课表、日程或待办数据。")
        }
        .task(id: container.snapshotRevision) {
            reloadBackupFiles()
            prepareExportFiles()
        }
    }

    private var storageStatsSection: some View {
        Section("数据概览") {
            LabeledContent("学期") {
                Text("\(live.semesters.count)")
                    .monospacedDigit()
            }
            LabeledContent("课程") {
                Text("\(live.courses.count)")
                    .monospacedDigit()
            }
            LabeledContent("日程") {
                Text("\(live.events.count)")
                    .monospacedDigit()
            }
            LabeledContent("待办") {
                Text("\(live.tasks.count)")
                    .monospacedDigit()
            }
        }
    }

    private var exportSection: some View {
        Section("导出与分享") {
            Button {
                router.present(.timetableExport)
            } label: {
                Label("课表快传", systemImage: "qrcode")
            }

            if let xlsxURL = exportXLSXURL {
                ShareLink(item: xlsxURL) {
                    Label("导出 Excel (.xlsx)", systemImage: "tablecells")
                }
            }

            if let icsURL = exportICSURL {
                ShareLink(item: icsURL) {
                    Label("导出日历 (.ics)", systemImage: "calendar.badge.plus")
                }
            }

            Button {
                router.present(.timetableExport)
            } label: {
                Label("导出课表图片", systemImage: "photo")
            }

            if let jsonURL = exportJSONURL {
                ShareLink(item: jsonURL) {
                    Label("导出备份 (JSON)", systemImage: "doc.badge.arrow.up")
                }
            }
        }
    }

    private var importAndHistorySection: some View {
        Section("导入与记录") {
            NavigationLink(value: GokeRoute.importFile()) {
                Label("导入文件", systemImage: "square.and.arrow.down")
            }

            NavigationLink(value: GokeRoute.trash) {
                LabeledContent {
                    if trashCount > 0 {
                        Text("\(trashCount)")
                            .monospacedDigit()
                    }
                } label: {
                    Label("回收站", systemImage: "trash")
                }
            }

            NavigationLink(value: GokeRoute.history) {
                Label("操作历史", systemImage: "clock.arrow.circlepath")
            }
        }
    }

    private var autoBackupSection: some View {
        Section("自动备份") {
            Toggle(
                "每天自动备份",
                isOn: Binding(
                    get: { settingsStore.settings.autoBackup },
                    set: { val in settingsStore.update { $0.autoBackup = val } }
                )
            )

            VStack(alignment: .leading, spacing: 10) {
                GokeMintStepper(
                    "保留份数",
                    valueText: "\(settingsStore.settings.backupKeep) 份",
                    value: Binding(
                        get: { settingsStore.settings.backupKeep },
                        set: { val in settingsStore.update { $0.backupKeep = val } }
                    ),
                    in: 1...365
                )

                GokeMintFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(backupKeepPresets, id: \.self) { count in
                        let selected = settingsStore.settings.backupKeep == count
                        Button {
                            settingsStore.update { $0.backupKeep = count }
                        } label: {
                            Text("\(count) 份")
                                .font(GokeTypography.labelMedium.weight(selected ? .semibold : .regular))
                                .monospacedDigit()
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    selected ? theme.accentContainer : theme.surfaceHighest,
                                    in: Capsule()
                                )
                                .foregroundStyle(selected ? theme.onAccentContainer : theme.ink)
                        }
                        .buttonStyle(.mintCompact)
                    }
                }
            }
            .padding(.vertical, 4)

            Button {
                createLocalBackupNow()
            } label: {
                Label("立即备份", systemImage: "plus.circle")
            }
        }
    }

    private var localBackupsListSection: some View {
        Section {
            if backupFiles.isEmpty {
                GokeEmptyState(
                    systemImage: "externaldrive",
                    title: "暂无本地备份",
                    subtitle: nil
                )
                .listRowBackground(Color.clear)
            } else {
                ForEach(backupFiles) { file in
                    NavigationLink(value: GokeRoute.importFile(url: file.url)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(file.name)
                                .font(.body)
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                                .lineLimit(1)
                            Text(SettingsUIHelpers.formatFileSize(file.sizeBytes))
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            GokeHaptics.destructiveConfirm()
                            try? FileManager.default.removeItem(at: file.url)
                            reloadBackupFiles()
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                    }
                }
            }
        } header: {
            Text("本地备份（\(backupFiles.count)）")
        } footer: {
            if !backupFiles.isEmpty {
                Text("轻点恢复，左滑删除")
            }
        }
    }

    private var resetSection: some View {
        Section {
            Button("恢复默认偏好设置", role: .destructive) {
                showResetPrefsConfirm = true
            }
        }
    }

    private func prepareExportFiles() {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let today = LocalDate.today().description
        let tempDir = FileManager.default.temporaryDirectory

        let bundle = ExportBundle(exportedAt: nowMillis, snapshot: container.snapshot)
        if let encoded = try? BackupCodec.encode(bundle, pretty: true),
           let data = encoded.data(using: .utf8) {
            let url = tempDir.appendingPathComponent("goke-backup-\(today).json")
            try? data.write(to: url, options: .atomic)
            exportJSONURL = url
        }

        let icsText = ICSCodec.write(snapshot: container.snapshot, nowMillis: nowMillis)
        if let data = icsText.data(using: .utf8) {
            let url = tempDir.appendingPathComponent("goke-calendar-\(today).ics")
            try? data.write(to: url, options: .atomic)
            exportICSURL = url
        }

        if let xlsxData = try? TimetableXlsxExporter.write(
            snapshot: container.snapshot,
            includeTeacher: settingsStore.settings.showTeacher
        ) {
            let url = tempDir.appendingPathComponent("goke-timetable-\(today).xlsx")
            try? xlsxData.write(to: url, options: .atomic)
            exportXLSXURL = url
        } else {
            exportXLSXURL = nil
        }
    }

    private func createLocalBackupNow() {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let stamp = SettingsUIHelpers.formatBackupStamp()
        let url = container.appGroup.backupsDirectoryURL
            .appendingPathComponent("goke-backup-\(stamp).json", isDirectory: false)
        let bundle = ExportBundle(exportedAt: nowMillis, snapshot: container.snapshot)
        do {
            let text = try BackupCodec.encode(bundle, pretty: true)
            try Data(text.utf8).write(to: url, options: .atomic)
            reloadBackupFiles()
            actions.showToast(message: "已保存本地备份：\(url.lastPathComponent)")
        } catch {
            actions.showToast(message: "创建备份失败：\(error.localizedDescription)")
        }
    }

    private func reloadBackupFiles() {
        let dir = container.appGroup.backupsDirectoryURL
        guard let urls = try? FileManager.default.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else {
            backupFiles = []
            return
        }
        backupFiles = urls
            .filter { $0.pathExtension.lowercased() == "json" }
            .map { url in
                let vals = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
                return BackupFileItem(
                    url: url,
                    name: url.lastPathComponent,
                    sizeBytes: Int64(vals?.fileSize ?? 0),
                    modifiedAt: vals?.contentModificationDate ?? .distantPast
                )
            }
            .sorted { $0.modifiedAt > $1.modifiedAt }
    }
}

private struct ReminderLeadMultiPickerView: View {
    let title: String
    let options: [Int]
    @Binding var selected: [Int]
    @Environment(\.gokeTheme) private var theme

    var body: some View {
        Form {
            Section {
                ForEach(options, id: \.self) { minutes in
                    let isSelected = selected.contains(minutes)
                    Button {
                        toggle(minutes)
                    } label: {
                        HStack {
                            Text(minutes == 0 ? "准时提醒" : "提前 \(ReminderScheduler.formatLeadTime(minutes))")
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                            Spacer()
                            if isSelected {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(theme.accent)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }
            } footer: {
                Text("可多选，至少保留 1 项")
            }
            .listRowBackground(theme.surface)
        }
        .gokePaperListStyle()
        .navigationTitle(title)
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
    }

    private func toggle(_ minutes: Int) {
        var set = Set(selected)
        if set.contains(minutes) {
            guard set.count > 1 else { return }
            set.remove(minutes)
        } else {
            set.insert(minutes)
        }
        selected = Reminders.normalize(Array(set))
    }
}

// MARK: - 4. ImportFileView

public struct ImportFileView: View {
    private enum ParsedPayload {
        case backup(ExportBundle, filename: String)
        case ics(IcsCalendar, filename: String, warnings: [String])
        case xlsx(TimetableImport, filename: String, warnings: [String])
    }

    private enum IcsImportMode: String, CaseIterable, Identifiable {
        case timetable
        case eventsAndTasks

        var id: String { rawValue }
        var title: String {
            switch self {
            case .timetable: "当作课表"
            case .eventsAndTasks: "导入为日程与待办"
            }
        }
    }

    public let initialURL: URL?

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var showFileImporter: Bool = false
    @State private var parsedPayload: ParsedPayload?
    @State private var icsMode: IcsImportMode = .eventsAndTasks
    @State private var updateWeek1Monday: Bool = false
    @State private var isImporting: Bool = false
    @State private var errorMessage: String?
    @State private var showReplaceConfirm: Bool = false

    public init(initialURL: URL? = nil) {
        self.initialURL = initialURL
    }

    public var body: some View {
        Form {
            Group {
                if let errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(theme.error)
                        if parsedPayload == nil {
                            Button("重新选取文件") {
                                showFileImporter = true
                            }
                        }
                    }
                }

                if let payload = parsedPayload {
                    switch payload {
                    case .backup(let bundle, let filename):
                        backupSections(bundle: bundle, filename: filename)
                    case .ics(let calendar, let filename, let warnings):
                        icsSections(calendar: calendar, filename: filename, warnings: warnings)
                    case .xlsx(let imported, let filename, let warnings):
                        xlsxSections(imported: imported, filename: filename, warnings: warnings)
                    }
                }
            }
            .listRowBackground(theme.surface)
        }
        .gokePaperListStyle()
        .overlay {
            if parsedPayload == nil && errorMessage == nil {
                GokeEmptyState(
                    systemImage: "square.and.arrow.down",
                    title: "导入文件",
                    subtitle: "支持 JSON 备份、ICS 日历与 Excel 课表",
                    primaryActionTitle: "选择文件",
                    onPrimaryAction: {
                        showFileImporter = true
                    }
                )
            }
        }
        .navigationTitle("导入文件")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("选取文件") {
                    showFileImporter = true
                }
            }
        }
        #if canImport(UniformTypeIdentifiers)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [
                .json,
                UTType(filenameExtension: "ics") ?? .calendarEvent,
                UTType(filenameExtension: "xlsx") ?? .data,
                .plainText,
            ],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    loadFile(at: url)
                }
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
        #endif
        .confirmationDialog(
            "确认完全覆盖本地数据？",
            isPresented: $showReplaceConfirm,
            titleVisibility: .visible
        ) {
            Button("覆盖恢复", role: .destructive) {
                if case .backup(let bundle, _) = parsedPayload {
                    Task { await replaceWithBackup(bundle) }
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将覆盖当前全部数据，且无法撤销。")
        }
        .task {
            if let initialURL {
                loadFile(at: initialURL)
            }
        }
    }

    @ViewBuilder
    private func backupSections(bundle: ExportBundle, filename: String) -> some View {
        let live = bundle.snapshot.live()

        Section("文件信息") {
            LabeledContent("文件名", value: filename)
            LabeledContent("类型", value: "JSON 备份")
            LabeledContent("来源", value: "\(bundle.app) · v\(bundle.version)")
            if bundle.exportedAt > 0 {
                LabeledContent("导出时间") {
                    Text(SettingsUIHelpers.formatDateTime(millis: bundle.exportedAt))
                        .monospacedDigit()
                }
            }
        }

        Section("解析统计") {
            LabeledContent("学期") {
                Text("\(live.semesters.count)")
                    .monospacedDigit()
            }
            LabeledContent("课程") {
                Text("\(live.courses.count)")
                    .monospacedDigit()
            }
            LabeledContent("日程") {
                Text("\(live.events.count)")
                    .monospacedDigit()
            }
            LabeledContent("待办") {
                Text("\(live.tasks.count)")
                    .monospacedDigit()
            }
        }

        Section {
            Button {
                Task { await mergeBackup(bundle) }
            } label: {
                HStack {
                    Text(isImporting ? "正在导入..." : "合并导入")
                        .font(.body.weight(.semibold))
                    if isImporting {
                        Spacer()
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isImporting)

            Button("覆盖并恢复", role: .destructive) {
                showReplaceConfirm = true
            }
            .disabled(isImporting)
        }
    }

    @ViewBuilder
    private func icsSections(calendar: IcsCalendar, filename: String, warnings: [String]) -> some View {
        let suggestedMonday = ICSCodec.suggestWeek1Monday(calendar)

        Section("文件信息") {
            LabeledContent("文件名", value: filename)
            LabeledContent("类型", value: ICSCodec.looksLikeTimetable(calendar) ? "ICS 课表" : "ICS 日历")
            if !calendar.prodId.isEmpty {
                LabeledContent("来源", value: calendar.prodId)
            }
        }

        Section {
            LabeledContent("事件") {
                Text("\(calendar.events.count)")
                    .monospacedDigit()
            }
            LabeledContent("待办") {
                Text("\(calendar.todos.count)")
                    .monospacedDigit()
            }
        } header: {
            Text("解析统计")
        } footer: {
            if !warnings.isEmpty {
                let preview = warnings.prefix(3).joined(separator: "；")
                Text("跳过 \(warnings.count) 条：\(preview)\(warnings.count > 3 ? "…" : "")")
            }
        }

        Section("导入方式") {
            Picker("导入方式", selection: $icsMode) {
                ForEach(IcsImportMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }

            if icsMode == .timetable, let suggestedMonday {
                Toggle(
                    "将开学第 1 周周一调整为 \(suggestedMonday.description)",
                    isOn: $updateWeek1Monday
                )
            }
        }

        Section {
            Button {
                Task { await commitICSImport(calendar: calendar, suggestedMonday: suggestedMonday) }
            } label: {
                HStack {
                    Text(isImporting ? "正在导入..." : "确认导入")
                        .font(.body.weight(.semibold))
                    if isImporting {
                        Spacer()
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isImporting)
        }
    }

    @ViewBuilder
    private func xlsxSections(imported: TimetableImport, filename: String, warnings: [String]) -> some View {
        Section("文件信息") {
            LabeledContent("文件名", value: filename)
            LabeledContent("类型", value: "Excel 课表 (.xlsx)")
            if let sem = container.liveSnapshot.currentSemester {
                LabeledContent("目标学期", value: sem.name)
            }
        }

        Section {
            LabeledContent("解析课程") {
                Text("\(imported.courses.count) 门")
                    .monospacedDigit()
            }
            LabeledContent("最大周次") {
                Text("第 \(imported.maxWeek) 周")
                    .monospacedDigit()
            }
        } header: {
            Text("解析统计")
        } footer: {
            if !warnings.isEmpty {
                let preview = warnings.prefix(3).joined(separator: "；")
                Text("跳过 \(warnings.count) 条：\(preview)\(warnings.count > 3 ? "…" : "")")
            }
        }

        if !imported.courses.isEmpty {
            Section("课程预览（前 \(min(imported.courses.count, 5)) 门）") {
                ForEach(Array(imported.courses.prefix(5)), id: \.id) { course in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(course.name)
                            .font(.body)
                            .foregroundStyle(theme.ink)
                        let detail = [
                            "周\(dayLabel(course.dayOfWeek)) 第\(course.startPeriod)-\(course.endPeriod)节",
                            course.room.isEmpty ? nil : course.room,
                            course.teacher.isEmpty ? nil : course.teacher,
                        ].compactMap { $0 }.joined(separator: " · ")
                        Text(detail)
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }
                }
            }
        }

        Section {
            Button {
                Task { await commitXlsxImport(imported) }
            } label: {
                HStack {
                    Text(isImporting ? "正在导入..." : "确认导入")
                        .font(.body.weight(.semibold))
                    if isImporting {
                        Spacer()
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
            .disabled(isImporting)
        }
    }

    private func dayLabel(_ dow: Int) -> String {
        switch dow {
        case 1: "一"
        case 2: "二"
        case 3: "三"
        case 4: "四"
        case 5: "五"
        case 6: "六"
        default: "日"
        }
    }

    private func loadFile(at url: URL) {
        errorMessage = nil
        parsedPayload = nil
        let didAccess = url.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                url.stopAccessingSecurityScopedResource()
            }
        }
        do {
            let data = try Data(contentsOf: url)
            let name = url.lastPathComponent
            let ext = url.pathExtension.lowercased()
            let isZipMagic = data.count >= 4
                && data[data.startIndex] == 0x50
                && data[data.startIndex + 1] == 0x4B
                && data[data.startIndex + 2] == 0x03
                && data[data.startIndex + 3] == 0x04

            if ext == "xlsx" || isZipMagic {
                guard let semester = container.liveSnapshot.currentSemester else {
                    errorMessage = "当前没有可用学期，请先创建学期后再导入课表。"
                    return
                }
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                let res = TimetableXlsxImporter.import(data: data, semester: semester, now: now)
                if let imported = res.value {
                    parsedPayload = .xlsx(imported, filename: name, warnings: res.warnings)
                } else {
                    errorMessage = "XLSX 课表解析失败：\(res.error ?? "格式不受支持")"
                }
                return
            }

            let text = String(decoding: data, as: UTF8.self)
            if text.contains("BEGIN:VCALENDAR") || ext == "ics" {
                let res = ICSCodec.parse(text)
                if let cal = res.value {
                    parsedPayload = .ics(cal, filename: name, warnings: res.warnings)
                    icsMode = ICSCodec.looksLikeTimetable(cal) ? .timetable : .eventsAndTasks
                } else {
                    errorMessage = "ICS 文件解析失败：\(res.error ?? "格式不受支持")"
                }
            } else {
                let bundle = try BackupCodec.decode(text)
                parsedPayload = .backup(bundle, filename: name)
            }
        } catch {
            errorMessage = "读取文件失败：\(error.localizedDescription)"
        }
    }

    private func commitXlsxImport(_ imported: TimetableImport) async {
        isImporting = true
        defer { isImporting = false }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let changes = imported.courses.map { Change.add(after: .course($0)) }
        guard !changes.isEmpty else {
            errorMessage = "未在文件中找到可导入的课程。"
            return
        }
        let cs = ChangeSet(
            description: "导入 XLSX 课表（\(imported.courses.count) 门）",
            source: .IMPORT,
            createdAt: now,
            changes: changes
        )
        if await actions.commit(cs, label: "已导入 \(imported.courses.count) 门课程") {
            dismiss()
        }
    }

    private func mergeBackup(_ bundle: ExportBundle) async {
        isImporting = true
        defer { isImporting = false }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var merged = container.snapshot
        for rec in bundle.snapshot.live().allRecords() {
            merged = merged.put(rec.withMeta(updatedAt: now, deletedAt: nil))
        }
        let changes = SnapshotDiff.diff(before: container.snapshot, after: merged)
        guard !changes.isEmpty else {
            actions.showToast(message: "备份内容与本地完全一致，无需变更")
            return
        }
        let cs = ChangeSet(
            description: "合并导入 JSON 备份",
            source: .IMPORT,
            createdAt: now,
            changes: changes
        )
        let ok = await actions.commit(cs, label: "已合并导入 \(changes.count) 项记录")
        if ok {
            dismiss()
        }
    }

    private func replaceWithBackup(_ bundle: ExportBundle) async {
        isImporting = true
        defer { isImporting = false }
        do {
            try await container.repository.replaceAll(with: bundle.snapshot)
            let updated = await container.repository.snapshot
            actions.refreshSideEffects(snapshot: updated, triggerSync: true)
            actions.showToast(message: "已从备份完全恢复本地数据")
            dismiss()
        } catch {
            errorMessage = "恢复失败：\(error.localizedDescription)"
        }
    }

    private func commitICSImport(calendar: IcsCalendar, suggestedMonday: LocalDate?) async {
        isImporting = true
        defer { isImporting = false }
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var changes: [Change] = []
        switch icsMode {
        case .timetable:
            guard var semester = container.liveSnapshot.currentSemester else {
                errorMessage = "当前没有可用学期，请先创建学期。"
                return
            }
            if updateWeek1Monday, let monday = suggestedMonday, monday != semester.week1Monday {
                let before = semester
                semester.week1Monday = monday
                semester.updatedAt = now
                changes.append(.modify(before: .semester(before), after: .semester(semester)))
            }
            let result = ICSCodec.toCourses(calendar, semester: semester, now: now)
            guard let imported = result.value else {
                errorMessage = result.error ?? "课表转换失败"
                return
            }
            for course in imported.courses {
                changes.append(.add(after: .course(course)))
            }
            let cs = ChangeSet(
                description: "导入 ICS 课表（\(imported.courses.count) 门）",
                source: .IMPORT,
                createdAt: now,
                changes: changes
            )
            if await actions.commit(cs) {
                dismiss()
            }

        case .eventsAndTasks:
            let evs = ICSCodec.toEvents(calendar, now: now).value ?? []
            let tasks = ICSCodec.toTasks(calendar, now: now).value ?? []
            for e in evs { changes.append(.add(after: .event(e))) }
            for t in tasks { changes.append(.add(after: .task(t))) }
            guard !changes.isEmpty else {
                errorMessage = "未在文件中找到可导入的日程或待办。"
                return
            }
            let cs = ChangeSet(
                description: "导入 ICS（\(evs.count) 日程 · \(tasks.count) 待办）",
                source: .IMPORT,
                createdAt: now,
                changes: changes
            )
            if await actions.commit(cs) {
                dismiss()
            }
        }
    }
}

// MARK: - 5. TrashView

public struct TrashView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme

    @State private var selectedFilter: EntityType? = nil
    @State private var searchText: String = ""
    @State private var showEmptyConfirm: Bool = false

    public init() {}

    private var deletedRecords: [Record] {
        container.snapshot.allRecords()
            .filter { $0.deletedAt != nil }
            .sorted { ($0.deletedAt ?? 0) > ($1.deletedAt ?? 0) }
    }

    private var visibleRecords: [Record] {
        let byType: [Record]
        if let selectedFilter {
            byType = deletedRecords.filter { $0.entityType == selectedFilter }
        } else {
            byType = deletedRecords
        }
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return byType }
        return byType.filter { record in
            record.displayTitle.localizedCaseInsensitiveContains(query)
                || SettingsUIHelpers.entityTypeLabel(record.entityType).localizedCaseInsensitiveContains(query)
        }
    }

    public var body: some View {
        List {
            if !visibleRecords.isEmpty {
                Section {
                    ForEach(visibleRecords, id: \.id) { record in
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 8) {
                                    GokeBadge(SettingsUIHelpers.entityTypeLabel(record.entityType), tone: .muted)
                                    Text(record.displayTitle.isEmpty ? "(无标题)" : record.displayTitle)
                                        .font(.body)
                                        .foregroundStyle(theme.ink)
                                        .lineLimit(1)
                                }
                                if let deletedAt = record.deletedAt {
                                    Text("删除于 \(SettingsUIHelpers.formatDateTime(millis: deletedAt))")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.muted)
                                }
                            }
                            Spacer(minLength: 8)
                            GokeMintActionPill("恢复", style: .tonal) {
                                Task { await restore(record) }
                            }
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                GokeHaptics.swipeCommit()
                                Task { await restore(record) }
                            } label: {
                                Label("恢复", systemImage: "arrow.uturn.backward")
                            }
                            .tint(theme.success)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                GokeHaptics.destructiveConfirm()
                                Task { await purge([record]) }
                            } label: {
                                Label("彻底删除", systemImage: "trash.slash")
                            }
                        }
                    }
                } footer: {
                    Text("保留 30 天后自动清理")
                }
                .listRowBackground(theme.surface)
            }
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .searchable(text: $searchText, prompt: "搜索已删除项目")
        .overlay {
            if deletedRecords.isEmpty {
                GokeEmptyState(
                    systemImage: "trash",
                    title: "回收站是空的",
                    subtitle: "已删除内容保留 30 天"
                )
            } else if visibleRecords.isEmpty {
                if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    GokeEmptyState(
                        systemImage: "magnifyingglass",
                        title: "未找到「\(searchText)」",
                        subtitle: nil
                    )
                } else {
                    GokeEmptyState(
                        systemImage: "line.3.horizontal.decrease.circle",
                        title: "当前分类暂无项目",
                        subtitle: nil
                    )
                }
            }
        }
        .navigationTitle("回收站")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            if !deletedRecords.isEmpty {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            selectedFilter = nil
                        } label: {
                            if selectedFilter == nil {
                                Label("全部 (\(deletedRecords.count))", systemImage: "checkmark")
                            } else {
                                Text("全部 (\(deletedRecords.count))")
                            }
                        }
                        ForEach(EntityType.allCases, id: \.self) { type in
                            let count = deletedRecords.filter { $0.entityType == type }.count
                            if count > 0 {
                                Button {
                                    selectedFilter = type
                                } label: {
                                    let title = "\(SettingsUIHelpers.entityTypeLabel(type)) (\(count))"
                                    if selectedFilter == type {
                                        Label(title, systemImage: "checkmark")
                                    } else {
                                        Text(title)
                                    }
                                }
                            }
                        }
                    } label: {
                        Image(
                            systemName: selectedFilter == nil
                                ? "line.3.horizontal.decrease.circle"
                                : "line.3.horizontal.decrease.circle.fill"
                        )
                    }
                    .accessibilityLabel("按类型筛选")

                    Button("清空", role: .destructive) {
                        showEmptyConfirm = true
                    }
                }
            }
        }
        .confirmationDialog(
            "彻底清空回收站？",
            isPresented: $showEmptyConfirm,
            titleVisibility: .visible
        ) {
            Button("全部彻底删除（\(deletedRecords.count) 项）", role: .destructive) {
                GokeHaptics.destructiveConfirm()
                Task { await purge(deletedRecords) }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将永久删除 \(deletedRecords.count) 条已删除记录且无法恢复。")
        }
    }

    private func restore(_ record: Record) async {
        do {
            try await container.repository.restoreFromTrash(record)
            let updated = await container.repository.snapshot
            actions.refreshSideEffects(snapshot: updated, triggerSync: true)
            actions.showToast(message: "已恢复「\(record.displayTitle)」")
        } catch {
            actions.showToast(message: "恢复失败：\(error.localizedDescription)")
        }
    }

    private func purge(_ records: [Record]) async {
        do {
            try await container.repository.purge(records)
            let updated = await container.repository.snapshot
            actions.refreshSideEffects(snapshot: updated, triggerSync: true)
            actions.showToast(message: "已彻底删除 \(records.count) 项记录")
        } catch {
            actions.showToast(message: "删除失败：\(error.localizedDescription)")
        }
    }
}

// MARK: - 6. ChangeHistoryView

public struct ChangeHistoryView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme

    @State private var ops: [OpRow] = []
    @State private var expandedOpIds: Set<String> = []
    @State private var isLoading: Bool = false

    public init() {}

    public var body: some View {
        let undoHistory = UndoHistory(ops: ops)

        List {
            if !ops.isEmpty {
                Section("近期变更记录") {
                    ForEach(ops) { op in
                        let cs = op.decodedChangeSet()
                        let canUndo = undoHistory.canUndo(op, in: container.snapshot)
                        let canRedo = undoHistory.canRedo(op, in: container.snapshot)
                        let isExpanded = expandedOpIds.contains(op.id)

                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top, spacing: 12) {
                                Button {
                                    if isExpanded {
                                        expandedOpIds.remove(op.id)
                                    } else {
                                        expandedOpIds.insert(op.id)
                                    }
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack(spacing: 6) {
                                            GokeBadge(SettingsUIHelpers.sourceLabel(op.source), tone: .muted)
                                            if op.undoneAt != nil {
                                                GokeBadge("已撤销", tone: .secondary)
                                            }
                                            Text(SettingsUIHelpers.formatDateTime(millis: op.createdAt))
                                                .font(.caption)
                                                .monospacedDigit()
                                                .foregroundStyle(theme.muted)
                                        }
                                        Text(op.description.isEmpty ? "数据变更" : op.description)
                                            .font(.body)
                                            .foregroundStyle(theme.ink)
                                            .multilineTextAlignment(.leading)

                                        if let cs {
                                            Text("+\(cs.addCount) 新增 · ~\(cs.modifyCount) 修改 · -\(cs.removeCount) 删除")
                                                .font(.caption)
                                                .monospacedDigit()
                                                .foregroundStyle(theme.muted)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)

                                if canUndo {
                                    GokeMintActionPill("撤销", style: .tonal) {
                                        Task {
                                            _ = await actions.undo(opId: op.id)
                                            await reloadHistory()
                                        }
                                    }
                                } else if canRedo {
                                    GokeMintActionPill("重做", style: .tonal) {
                                        Task {
                                            _ = await actions.redo(opId: op.id)
                                            await reloadHistory()
                                        }
                                    }
                                }
                            }

                            if isExpanded, let cs {
                                Divider()
                                VStack(alignment: .leading, spacing: 6) {
                                    ForEach(Array(cs.changes.prefix(20).enumerated()), id: \.offset) { _, change in
                                        HStack(spacing: 8) {
                                            Text(changeBadgeSymbol(change))
                                                .font(.caption.weight(.bold).monospaced())
                                                .foregroundStyle(changeBadgeColor(change))
                                            Text(SettingsUIHelpers.entityTypeLabel(change.record.entityType))
                                                .font(.caption)
                                                .foregroundStyle(theme.muted)
                                            Text(change.record.displayTitle)
                                                .font(.subheadline)
                                                .foregroundStyle(theme.ink)
                                                .lineLimit(1)
                                        }
                                    }
                                    if cs.changes.count > 20 {
                                        Text("以及其余 \(cs.changes.count - 20) 条变更...")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundStyle(theme.muted)
                                    }
                                }
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
        .overlay {
            if isLoading && ops.isEmpty {
                ProgressView()
            } else if ops.isEmpty {
                GokeEmptyState(
                    systemImage: "clock.arrow.circlepath",
                    title: "还没有操作记录",
                    subtitle: nil
                )
            }
        }
        .navigationTitle("操作历史")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .refreshable {
            await reloadHistory()
        }
        .task {
            await reloadHistory()
        }
    }

    private func changeBadgeSymbol(_ change: Change) -> String {
        switch change {
        case .add: "+"
        case .modify: "~"
        case .remove: "-"
        }
    }

    private func changeBadgeColor(_ change: Change) -> Color {
        switch change {
        case .add: theme.added
        case .modify: theme.onModified
        case .remove: theme.removed
        }
    }

    private func reloadHistory() async {
        isLoading = true
        defer { isLoading = false }
        if let loaded = try? await container.repository.ensureHistoryLoaded(limit: 100) {
            ops = loaded
        }
    }
}

// MARK: - 7. ReliabilityView

public struct ReliabilityView: View {
    private enum DiagnosticStatus {
        case ok
        case warning
        case error
        case neutral

        var systemImage: String {
            switch self {
            case .ok: "checkmark.circle.fill"
            case .warning: "exclamationmark.triangle.fill"
            case .error: "xmark.circle.fill"
            case .neutral: "info.circle.fill"
            }
        }

        func color(in theme: GokeTheme) -> Color {
            switch self {
            case .ok: theme.success
            case .warning: theme.modified
            case .error: theme.error
            case .neutral: theme.muted
            }
        }
    }

    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(SyncStateCoordinator.self) private var syncCoordinator
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    @State private var reminderAuth: ReminderAuthorizationSnapshot = .denied
    @State private var eventKitAuth: EventKitAuthorizationState = .notDetermined
    @State private var liveActivitiesEnabled: Bool = false
    @State private var activeLiveCourseId: String?
    @State private var plannedReminderCount: Int = 0
    @State private var widgetSnapshotMillis: Int64?
    @State private var showDiagnosticSheet: Bool = false
    @State private var diagnosticReportText: String = ""
    @State private var showClearErrorsConfirm: Bool = false
    @State private var showSheetClearErrorsConfirm: Bool = false
    @State private var showAdvancedDiagnostics: Bool = false

    public init() {}

    public var body: some View {
        List {
            Group {
                summarySection
                permissionsSection
                platformHealthSection
                errorHistorySection
            }
            .listRowBackground(theme.surface)
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("权限与自检")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .refreshable {
            await runDiagnostics()
        }
        .task {
            await runDiagnostics()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await runDiagnostics() }
            }
        }
        .sheet(isPresented: $showDiagnosticSheet) {
            diagnosticReportSheet
                .gokePaperSheet(detents: [.medium, .large])
        }
        .confirmationDialog("清空异常日志", isPresented: $showClearErrorsConfirm, titleVisibility: .visible) {
            Button("清空全部日志", role: .destructive) {
                Task {
                    await container.clearErrorLog()
                    diagnosticReportText = await container.errorLog.formattedDiagnosticReport(limit: 20)
                    actions.showToast(message: "已清空诊断日志")
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将删除本地保留的所有运行时错误记录，此操作不可撤销。")
        }
    }

    private var summarySection: some View {
        let healthy = reminderAuth.isAuthorized && container.isBootstrapped && container.bootstrapError == nil
        return Section {
            HStack(spacing: 14) {
                Image(systemName: healthy ? "checkmark.shield.fill" : "exclamationmark.shield.fill")
                    .font(.title2)
                    .foregroundStyle(healthy ? theme.success : theme.modified)

                VStack(alignment: .leading, spacing: 3) {
                    Text(healthy ? "核心服务运行正常" : "部分权限待开启")
                        .font(.headline)
                        .foregroundStyle(theme.ink)
                    Text(healthy ? "已就绪" : "请检查下方未授权项")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }
            }
            .padding(.vertical, 4)
        }
    }

    private var permissionsSection: some View {
        Section("系统权限") {
            diagnosticRow(
                title: "本地通知",
                detail: reminderAuth.isAuthorized
                    ? "已授权 · \(plannedReminderCount) 条待触发"
                    : "未授权，提醒无法送达",
                statusText: reminderAuth.isAuthorized ? "正常" : "待开启",
                status: reminderAuth.isAuthorized ? .ok : .error,
                actionTitle: reminderAuth.isAuthorized ? "校准" : "开启"
            ) {
                Task {
                    if !reminderAuth.isAuthorized {
                        _ = try? await container.reminderScheduler.requestAuthorization()
                    } else {
                        _ = await container.reminderScheduler.reschedule(
                            snapshot: container.snapshot,
                            settings: settingsStore.settings,
                            force: true
                        )
                    }
                    await runDiagnostics()
                }
            }

            diagnosticRow(
                title: "系统日历",
                detail: eventKitAuth == .fullAccess ? "已开启" : "未开启",
                statusText: eventKitAuth == .fullAccess ? "正常" : "可选",
                status: eventKitAuth == .fullAccess ? .ok : .neutral,
                actionTitle: eventKitAuth == .fullAccess ? nil : "授权"
            ) {
                Task {
                    _ = try? await container.eventKitBridge.requestFullAccess()
                    await runDiagnostics()
                }
            }

            diagnosticRow(
                title: "实时活动与灵动岛",
                detail: liveActivitiesEnabled
                    ? (activeLiveCourseId != nil ? "进行中" : "已开启")
                    : "未开启",
                statusText: liveActivitiesEnabled ? "可用" : "未开启",
                status: liveActivitiesEnabled ? .ok : .neutral,
                actionTitle: liveActivitiesEnabled ? "同步" : "设置"
            ) {
                if liveActivitiesEnabled {
                    Task {
                        await container.liveActivityManager.sync(snapshot: container.snapshot)
                        await runDiagnostics()
                        actions.showToast(message: "已同步实时活动状态")
                    }
                } else {
                    SettingsUIHelpers.openAppSettings()
                }
            }
        }
    }

    private var platformHealthSection: some View {
        Section {
            DisclosureGroup("高级诊断", isExpanded: $showAdvancedDiagnostics) {
                diagnosticRow(
                    title: "小组件数据共享",
                    detail: container.appGroup.isAppGroupAvailable
                        ? "已开启 · \(container.appGroup.groupIdentifier)"
                        : "本地回退（签名未含 App Group）",
                    statusText: container.appGroup.isAppGroupAvailable ? "共享" : "本地回退",
                    status: container.appGroup.isAppGroupAvailable ? .ok : .warning,
                    actionTitle: "刷新快照"
                ) {
                    try? container.appGroup.writeWidgetSnapshot(
                        container.snapshot,
                        settings: settingsStore.settings,
                        reloadTimelines: true
                    )
                    Task {
                        await HotWaterStore(keychain: container.keychain, appGroup: container.appGroup)
                            .syncWidgetSnapshotFromPersistence(reloadTimelines: true)
                    }
                    widgetSnapshotMillis = container.appGroup.readWidgetSnapshot()?.generatedAtMillis
                    actions.showToast(message: "已刷新小组件快照")
                }

                diagnosticRow(
                    title: "小组件快照",
                    detail: widgetSnapshotMillis.map { "更新于 \(SettingsUIHelpers.formatDateTime(millis: $0))" }
                        ?? "尚未生成",
                    statusText: widgetSnapshotMillis != nil ? "就绪" : "待写入",
                    status: widgetSnapshotMillis != nil ? .ok : .neutral,
                    actionTitle: nil
                ) {}

                diagnosticRow(
                    title: "本地数据库",
                    detail: container.bootstrapError ?? "正常 · \(container.liveSnapshot.allRecords().count) 条记录",
                    statusText: container.bootstrapError == nil ? "健康" : "异常",
                    status: container.bootstrapError == nil ? .ok : .error,
                    actionTitle: nil
                ) {}

                diagnosticRow(
                    title: "后台刷新",
                    detail: syncCoordinator.isSignedIn ? "已启用云同步与提醒续期" : "已启用提醒续期",
                    statusText: "已注册",
                    status: .ok,
                    actionTitle: "重新提交"
                ) {
                    container.scheduleBackgroundRefresh()
                    actions.showToast(message: "已重新提交后台刷新")
                }
            }
        }
    }

    private var errorHistorySection: some View {
        Section {
            if container.recentErrors.isEmpty {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(theme.success)
                    Text("暂无异常")
                        .font(.body)
                        .foregroundStyle(theme.ink)
                }
                .padding(.vertical, 2)
            } else {
                ForEach(container.recentErrors.prefix(20)) { entry in
                    Button {
                        Task { await openDiagnosticSheet() }
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                GokeBadge(
                                    entry.source,
                                    tone: entry.isFatal ? .error : .muted
                                )
                                if entry.isFatal {
                                    GokeBadge("严重", tone: .error)
                                }
                                Spacer(minLength: 4)
                                Text(entry.formattedDateTime)
                                    .font(.caption2)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.muted)
                            }
                            Text(entry.summary)
                                .font(.subheadline)
                                .foregroundStyle(theme.ink)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                        }
                        .padding(.vertical, 2)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }
            }

            Button {
                Task { await openDiagnosticSheet() }
            } label: {
                LabeledContent("查看并复制诊断报告") {
                    Text("\(container.recentErrors.count) 条")
                        .font(.subheadline)
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .foregroundStyle(theme.muted)
                }
            }

            if !container.recentErrors.isEmpty {
                Button("清空异常日志", role: .destructive) {
                    showClearErrorsConfirm = true
                }
            }
        } header: {
            Text("异常与诊断日志")
        }
    }

    private var diagnosticReportSheet: some View {
        NavigationStack {
            List {
                Group {
                    Section("报告操作") {
                        HStack {
                            GokeCopyButton(
                                text: diagnosticReportText,
                                title: "复制诊断报告",
                                copiedTitle: "已复制"
                            )
                            Spacer()
                            Text("\(container.recentErrors.count) 条记录")
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }

                        if !container.recentErrors.isEmpty {
                            Button("清空异常日志", role: .destructive) {
                                showSheetClearErrorsConfirm = true
                            }
                        }
                    }

                    Section("诊断详情") {
                        Text(diagnosticReportText)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(theme.ink)
                            .textSelection(.enabled)
                            .padding(.vertical, 4)
                    }
                }
                .listRowBackground(theme.surface)
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle("诊断报告")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { showDiagnosticSheet = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    GokeCopyButton(
                        text: diagnosticReportText,
                        title: "复制",
                        copiedTitle: "已复制"
                    )
                }
            }
            .confirmationDialog("清空异常日志", isPresented: $showSheetClearErrorsConfirm, titleVisibility: .visible) {
                Button("清空全部日志", role: .destructive) {
                    Task {
                        await container.clearErrorLog()
                        diagnosticReportText = await container.errorLog.formattedDiagnosticReport(limit: 20)
                        actions.showToast(message: "已清空诊断日志")
                    }
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text("将删除本地保留的所有运行时错误记录，此操作不可撤销。")
            }
        }
        .tint(theme.accent)
    }

    private func diagnosticRow(
        title: String,
        detail: String,
        statusText: String,
        status: DiagnosticStatus,
        actionTitle: String?,
        action: @escaping () -> Void
    ) -> some View {
        let statusColor = status.color(in: theme)
        return HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Image(systemName: status.systemImage)
                        .font(.subheadline)
                        .foregroundStyle(statusColor)
                    Text(title)
                        .font(.body)
                        .foregroundStyle(theme.ink)
                    Text(statusText)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(statusColor)
                }
                Text(detail)
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
            }

            Spacer(minLength: 8)

            if let actionTitle {
                GokeMintActionPill(actionTitle, style: .tonal, action: action)
            }
        }
        .padding(.vertical, 2)
    }

    private func runDiagnostics() async {
        reminderAuth = await container.reminderScheduler.authorizationStatus()
        eventKitAuth = await container.eventKitBridge.authorizationStatus()
        liveActivitiesEnabled = await container.liveActivityManager.areActivitiesEnabled()
        activeLiveCourseId = await container.liveActivityManager.activeCourseId()
        plannedReminderCount = container.reminderScheduler.planReminders(
            snapshot: container.snapshot,
            settings: settingsStore.settings
        ).count
        widgetSnapshotMillis = container.appGroup.readWidgetSnapshot()?.generatedAtMillis
        await container.refreshErrorLog()
        diagnosticReportText = await container.errorLog.formattedDiagnosticReport(limit: 20)
    }

    private func openDiagnosticSheet() async {
        await container.refreshErrorLog()
        diagnosticReportText = await container.errorLog.formattedDiagnosticReport(limit: 20)
        showDiagnosticSheet = true
    }
}

// MARK: - 8. WidgetsGalleryView

public struct WidgetsGalleryView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase

    @State private var liveActivitiesEnabled: Bool = false
    @State private var activeCourseId: String?

    public init() {}

    private var nextUp: NextUp? {
        NextUps.find(snapshot: container.liveSnapshot, now: .now(), horizonDays: 7)
    }

    private var todayAgenda: [AgendaItem] {
        AgendaEngine.forDay(
            snapshot: container.liveSnapshot,
            date: .today(),
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes,
            includeDoneTasks: false
        )
    }

    public var body: some View {
        List {
            Group {
                nextClassWidgetSection
                todayAgendaWidgetSection
                moreWidgetsSection
                liveActivitySection
                setupGuideSection
            }
            .listRowBackground(theme.surface)
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("桌面小组件")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("刷新小组件") {
                    do {
                        try container.appGroup.writeWidgetSnapshot(
                            container.snapshot,
                            settings: settingsStore.settings,
                            reloadTimelines: true
                        )
                        actions.showToast(message: "已请求刷新全部桌面小组件")
                    } catch {
                        actions.showToast(message: "刷新小组件失败：\(error.localizedDescription)")
                    }
                }
            }
        }
        .task {
            await refreshLiveActivityStatus()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await refreshLiveActivityStatus() }
            }
        }
    }

    private func refreshLiveActivityStatus() async {
        liveActivitiesEnabled = await container.liveActivityManager.areActivitiesEnabled()
        activeCourseId = await container.liveActivityManager.activeCourseId()
    }

    private var nextClassWidgetSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    GokeBadge("下节课程", systemImage: "graduationcap", tone: .accent)
                    Spacer()
                    if let next = nextUp {
                        Text("\(next.occurrence.start.time.description)–\(next.occurrence.end.time.description)")
                            .font(.subheadline)
                            .monospacedDigit()
                            .foregroundStyle(theme.accent)
                    }
                }

                if let next = nextUp {
                    Text(next.occurrence.course.name)
                        .font(.headline)
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)
                    if !next.occurrence.room.isEmpty {
                        Text(next.occurrence.room)
                            .font(.subheadline)
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                } else {
                    Text("暂无课程")
                        .font(.headline)
                        .foregroundStyle(theme.ink)
                }
            }
            .padding(.vertical, 4)

            LabeledContent("支持尺寸", value: "小号 · 中号 · 大号 · 锁屏矩形 · 锁屏圆形 · 锁屏顶栏")
        } header: {
            Text("下节课程")
        }
    }

    private var todayAgendaWidgetSection: some View {
        Section {
            LabeledContent("今日工课 · \(LocalDate.today().description)") {
                Text("\(todayAgenda.count) 项")
                    .monospacedDigit()
                    .foregroundStyle(theme.accent)
            }

            if todayAgenda.isEmpty {
                Text("今天没有安排")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
            } else {
                ForEach(Array(todayAgenda.prefix(3).enumerated()), id: \.offset) { _, item in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(theme.accent)
                            .frame(width: 7, height: 7)
                        Text(agendaItemTitle(item))
                            .font(.body)
                            .foregroundStyle(theme.ink)
                            .lineLimit(1)
                        Spacer(minLength: 8)
                        Text(agendaItemTime(item))
                            .font(.footnote)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }
                }
            }

            LabeledContent("支持尺寸", value: "小号 · 中号 · 大号")
        } header: {
            Text("今日概览")
        }
    }

    private var moreWidgetsSection: some View {
        Section("更多小组件") {
            widgetCatalogRow(
                title: "周课表",
                systemImage: "calendar.day.timeline.left",
                subtitle: "素纸色块迷你周课表，一眼掌握全周课程分布",
                families: "小号 · 中号 · 大号"
            )
            widgetCatalogRow(
                title: "待办清单",
                systemImage: "checklist",
                subtitle: "在桌面直接勾选完成待办，或轻点进入详情",
                families: "中号 · 大号"
            )
            widgetCatalogRow(
                title: "智慧热水",
                systemImage: "drop",
                subtitle: "查看宿舍热水余额与阀门状态，独立按钮一键控阀",
                families: "小号 · 中号 · 锁屏圆形 · 锁屏矩形"
            )
            widgetCatalogRow(
                title: "快速记录",
                systemImage: "plus.circle",
                subtitle: "控制中心一键打开工课速记面板，快速添加待办或日程",
                families: "iOS 18+ 控制中心"
            )
        }
    }

    private func widgetCatalogRow(
        title: String,
        systemImage: String,
        subtitle: String,
        families: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(theme.accent)
                    .frame(width: 20)
                Text(title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(theme.ink)
            }
            Text(subtitle)
                .font(.footnote)
                .foregroundStyle(theme.muted)
            Text(families)
                .font(.caption)
                .foregroundStyle(theme.accent)
        }
        .padding(.vertical, 2)
    }

    private var liveActivitySection: some View {
        Section {
            LabeledContent("课堂实时活动") {
                Text(liveActivitiesEnabled ? (activeCourseId != nil ? "运行中" : "已就绪") : "未开启")
                    .font(.subheadline)
                    .foregroundStyle(liveActivitiesEnabled ? theme.success : theme.muted)
            }

            if !liveActivitiesEnabled {
                Button("打开系统设置") {
                    SettingsUIHelpers.openAppSettings()
                }
            }

            Button("同步实时活动") {
                Task {
                    await refreshLiveActivityStatus()
                    guard liveActivitiesEnabled else {
                        actions.showToast(message: "请在系统设置中开启实时活动")
                        return
                    }
                    await container.liveActivityManager.sync(snapshot: container.snapshot)
                    activeCourseId = await container.liveActivityManager.activeCourseId()
                    actions.showToast(
                        message: activeCourseId != nil ? "已启动/更新课堂实时活动" : "未来 45 分钟内暂无课程"
                    )
                }
            }

            if activeCourseId != nil {
                Button("结束当前实时活动", role: .destructive) {
                    Task {
                        await container.liveActivityManager.endAll()
                        activeCourseId = nil
                        actions.showToast(message: "已结束当前实时活动")
                    }
                }
            }
        } header: {
            Text("实时活动与灵动岛")
        } footer: {
            Text("课前 45 分钟自动显示")
        }
    }

    private var setupGuideSection: some View {
        Section {
        } footer: {
            Text("主屏或锁屏长按空白处，添加「工课」小组件。")
        }
    }

    private func agendaItemTitle(_ item: AgendaItem) -> String {
        switch item {
        case .course(let occ): occ.course.name
        case .event(let ev, _, _, _): ev.title
        case .task(let task, _, _): task.title
        case .block(let task, _, _): task.title
        }
    }

    private func agendaItemTime(_ item: AgendaItem) -> String {
        switch item {
        case .course(let occ): occ.start.time.description
        case .event(_, let start, _, let allDay): allDay ? "全天" : start.time.description
        case .task(_, let due, let hasTime): hasTime ? due.time.description : "今日"
        case .block(_, let start, _): start.time.description
        }
    }
}

// MARK: - 9. AboutView

public struct AboutView: View {
    private static let qqGroupNumber = "1137453860"
    private static let qqGroupURLString = "https://qm.qq.com/q/VWUh0uEhKY"
    private static let projectURLString = "https://github.com/xihale/goke"

    private struct OssLibraryItem: Identifiable {
        let id: String
        let name: String
        let license: String
        let url: String
    }

    private static let ossLibraries: [OssLibraryItem] = [
        OssLibraryItem(id: "goke", name: "工课 · Goke", license: "Goke-NC-SA-2.0", url: "https://github.com/xihale/goke"),
        OssLibraryItem(id: "swift", name: "Swift & SwiftUI", license: "Apache-2.0 / Apple SDK", url: "https://swift.org"),
        OssLibraryItem(id: "sqlite", name: "SQLite", license: "Public Domain", url: "https://sqlite.org"),
        OssLibraryItem(id: "material-kolor", name: "Material Kolor (色板灵感)", license: "MIT", url: "https://github.com/jordond/material-kolor"),
    ]

    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private enum AboutActiveSheet: Identifiable {
        case qqGroup
        case license
        case oss
        case errorLogs

        var id: String {
            switch self {
            case .qqGroup: "qqGroup"
            case .license: "license"
            case .oss: "oss"
            case .errorLogs: "errorLogs"
            }
        }
    }

    @State private var activeSheet: AboutActiveSheet?
    @State private var errorLogs: [GokeErrorEntry] = []

    public init() {}

    public var body: some View {
        List {
            Group {
                brandHeaderSection
                updateCheckerSection
                communityAndDiagnosticsSection
                licenseAndAckSection
            }
            .listRowBackground(theme.surface)
        }
        .listStyle(.insetGrouped)
        .gokePaperListStyle()
        .navigationTitle("关于")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        #endif
        .task {
            errorLogs = await container.errorLog.entries()
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .qqGroup:
                qqGroupSheet
                    .gokePaperSheet(detents: [.medium, .large])
            case .license:
                licenseSheet
                    .gokePaperSheet(detents: [.medium, .large])
            case .oss:
                ossAcknowledgmentsSheet
                    .gokePaperSheet(detents: [.medium, .large])
            case .errorLogs:
                errorLogsSheet
                    .gokePaperSheet(detents: [.medium, .large])
            }
        }
    }

    private var brandHeaderSection: some View {
        Section {
            HStack(spacing: 14) {
                GokeBrandMark(size: 56)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("工课 · Goke")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(theme.ink)
                        GokeBadge(GokeAppVersion.shortLabel, tone: .accent)
                    }
                    Text("广工课表与日程")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 4)
        }
    }

    private var updateCheckerSection: some View {
        Section {
            LabeledContent {
                GokeBadge("内测中", tone: .accent)
            } label: {
                HStack(spacing: 14) {
                    GokePaperIcon(systemImage: "arrow.down.circle", color: theme.muted)
                    Text("更新渠道")
                        .font(.body)
                        .foregroundStyle(theme.ink)
                }
            }

            if let projectURL = URL(string: Self.projectURLString) {
                Link(destination: projectURL) {
                    LabeledContent {
                        Text("GitHub")
                            .font(.subheadline)
                            .foregroundStyle(theme.muted)
                    } label: {
                        HStack(spacing: 14) {
                            GokePaperIcon(systemImage: "chevron.left.forwardslash.chevron.right", color: theme.muted)
                            Text("项目主页")
                                .font(.body)
                                .foregroundStyle(theme.ink)
                        }
                    }
                }
            }
        } header: {
            Text("版本更新")
        } footer: {
            Text("当前为 iOS 内测版本，暂未接入自动更新渠道。")
        }
    }

    private var communityAndDiagnosticsSection: some View {
        Section("交流与诊断") {
            Button {
                activeSheet = .qqGroup
            } label: {
                LabeledContent {
                    Text(Self.qqGroupNumber)
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                } label: {
                    HStack(spacing: 14) {
                        GokePaperIcon(systemImage: "person.2", color: theme.muted)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Goke 会员制餐厅")
                                .font(.body)
                                .foregroundStyle(theme.ink)
                            Text("QQ 交流群")
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
            }

            Button {
                Task {
                    errorLogs = await container.errorLog.entries()
                    activeSheet = .errorLogs
                }
            } label: {
                LabeledContent {
                    Text(errorLogs.isEmpty ? "无异常" : "\(errorLogs.count) 条")
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(errorLogs.isEmpty ? theme.muted : theme.error)
                } label: {
                    HStack(spacing: 14) {
                        GokePaperIcon(systemImage: "doc.text.magnifyingglass", color: theme.muted)
                        Text("异常日志")
                            .font(.body)
                            .foregroundStyle(theme.ink)
                    }
                }
            }
        }
    }

    private var licenseAndAckSection: some View {
        Section("开源与致谢") {
            Button {
                activeSheet = .license
            } label: {
                LabeledContent {
                    Text("Goke-NC-SA-2.0")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                } label: {
                    HStack(spacing: 14) {
                        GokePaperIcon(systemImage: "doc.text", color: theme.muted)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("开源协议")
                                .font(.body)
                                .foregroundStyle(theme.ink)
                            Text("非商业开源")
                                .font(.caption)
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
            }

            Button {
                activeSheet = .oss
            } label: {
                LabeledContent {
                    Text("\(Self.ossLibraries.count) 项")
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                } label: {
                    HStack(spacing: 14) {
                        GokePaperIcon(systemImage: "heart.text.square", color: theme.muted)
                        Text("开源致谢")
                            .font(.body)
                            .foregroundStyle(theme.ink)
                    }
                }
            }
        }
    }

    private var errorLogsSheet: some View {
        NavigationStack {
            List {
                if errorLogs.isEmpty {
                    GokeEmptyState(
                        systemImage: "checkmark.seal",
                        title: "暂无异常记录",
                        subtitle: nil
                    )
                    .listRowBackground(Color.clear)
                } else {
                    ForEach(errorLogs) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(entry.source)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(entry.isFatal ? theme.error : theme.accent)
                                Spacer()
                                Text(entry.formattedDateTime)
                                    .font(.caption2)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.muted)
                            }
                            Text(entry.summary)
                                .font(.subheadline)
                                .foregroundStyle(theme.ink)
                            if !entry.detail.isEmpty {
                                Text(entry.detail)
                                    .font(.caption2.monospaced())
                                    .foregroundStyle(theme.muted)
                                    .lineLimit(4)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                    .listRowBackground(theme.surface)
                }
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle("异常日志（\(errorLogs.count)）")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { activeSheet = nil }
                }
                if !errorLogs.isEmpty {
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        Button("复制报告") {
                            Task {
                                let report = await container.errorLog.formattedDiagnosticReport()
                                #if canImport(UIKit)
                                UIPasteboard.general.string = report
                                #endif
                                actions.showToast(message: "已复制诊断日志报告")
                            }
                        }
                        Button("清空", role: .destructive) {
                            Task {
                                await container.errorLog.clear()
                                errorLogs = []
                            }
                        }
                    }
                }
            }
        }
        .tint(theme.accent)
    }

    private var ossAcknowledgmentsSheet: some View {
        NavigationStack {
            List {
                Section("核心依赖与灵感来源") {
                    ForEach(Self.ossLibraries) { item in
                        if let url = URL(string: item.url) {
                            Link(destination: url) {
                                LabeledContent {
                                    Text(item.license)
                                        .font(.caption)
                                        .foregroundStyle(theme.muted)
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.name)
                                            .font(.body)
                                            .foregroundStyle(theme.ink)
                                        Text(item.url)
                                            .font(.caption)
                                            .foregroundStyle(theme.muted)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }
                    }
                }
                .listRowBackground(theme.surface)

                Section("社区致谢") {
                    Text("感谢所有参与工课测试、提交反馈、分享教务适配与配色建议的广工同学和开源社区贡献者。")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }
                .listRowBackground(theme.surface)
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle("开源致谢")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { activeSheet = nil }
                }
            }
        }
        .tint(theme.accent)
    }

    private var qqGroupSheet: some View {
        NavigationStack {
            List {
                Section {
                    VStack(spacing: GokeSpacing.md) {
                        qrCodeView(payload: Self.qqGroupURLString)
                            .frame(width: 200, height: 200)
                            .padding(10)
                            .background(Color(hex: 0xFFFFFF), in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                            .accessibilityLabel("Goke 会员制餐厅二维码，QQ \(Self.qqGroupNumber)")

                        VStack(spacing: 4) {
                            Text("Goke 会员制餐厅")
                                .font(.headline)
                                .foregroundStyle(theme.ink)
                            Text("QQ \(Self.qqGroupNumber)")
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }

                        HStack(spacing: GokeSpacing.md) {
                            GokeCopyButton(
                                text: Self.qqGroupNumber,
                                title: "复制群号",
                                copiedTitle: "已复制"
                            )
                            .padding(.horizontal, GokeSpacing.md)
                            .padding(.vertical, GokeSpacing.sm)
                            .background(theme.accentContainer, in: Capsule())

                            if let joinURL = URL(string: Self.qqGroupURLString) {
                                Link(destination: joinURL) {
                                    Label("打开 QQ", systemImage: "arrow.up.right.square")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(theme.onAccent)
                                        .padding(.horizontal, GokeSpacing.lg)
                                        .padding(.vertical, GokeSpacing.sm)
                                        .background(theme.accent, in: Capsule())
                                        .frame(minHeight: 44)
                                        .contentShape(Capsule())
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, GokeSpacing.md)
                }
                .listRowBackground(theme.surface)
            }
            .listStyle(.insetGrouped)
            .gokePaperListStyle()
            .navigationTitle("交流群二维码")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { activeSheet = nil }
                }
            }
        }
        .tint(theme.accent)
    }

    @ViewBuilder
    private func qrCodeView(payload: String) -> some View {
        #if canImport(CoreImage) && canImport(UIKit)
        if let image = Self.generateQRCodeImage(from: payload) {
            Image(uiImage: image)
                .resizable()
                .interpolation(.none)
                .scaledToFit()
        } else {
            Image(systemName: "qrcode")
                .font(.system(size: 72))
                .foregroundStyle(theme.muted)
        }
        #else
        Image(systemName: "qrcode")
            .font(.system(size: 72))
            .foregroundStyle(theme.muted)
        #endif
    }

    #if canImport(CoreImage) && canImport(UIKit)
    private static let qrCIContext = CIContext()
    private static let cachedQqGroupQRImage: UIImage? = renderQRCodeImage(from: qqGroupURLString)

    private static func generateQRCodeImage(from string: String) -> UIImage? {
        if string == qqGroupURLString {
            return cachedQqGroupQRImage
        }
        return renderQRCodeImage(from: string)
    }

    private static func renderQRCodeImage(from string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = qrCIContext.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
    #endif

    private var licenseSheet: some View {
        NavigationStack {
            ScrollView {
                Text(Self.gokeLicenseText)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(theme.ink)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(GokeSpacing.lg)
            }
            .background(theme.paper.ignoresSafeArea())
            .navigationTitle("Goke-NC-SA-2.0")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { activeSheet = nil }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    GokeCopyButton(
                        text: Self.gokeLicenseText,
                        title: "复制全文",
                        copiedTitle: "已复制"
                    )
                }
            }
        }
        .tint(theme.accent)
    }

    private static let gokeLicenseText = """
    Goke Strict Noncommercial Share-Alike License 2.0 (Goke-NC-SA-2.0)
    Adapted from PolyForm Noncommercial License 1.0.0

    Copyright (c) 2025-2026 xihale (https://github.com/xihale/goke)
    Required Notice: Copyright (c) xihale — Goke (https://github.com/xihale/goke)

    Notice on Adaptation:
    This license is adapted from the PolyForm Noncommercial License 1.0.0
    (https://polyformproject.org/licenses/noncommercial/1.0.0), substantially
    strengthened with an absolute prohibition on all commercial activities, an
    irrevocable refusal of any commercial licensing, a licensor noncommercial and
    no-monetization pledge, a strict ban on donations, tips, and sponsorships in the
    project's name, and Share-Alike and Source-Availability obligations. Because this
    document modifies and adds terms beyond the standard PolyForm text, it is not an
    unmodified PolyForm license and is not an OSI-approved Open Source license.

    ================================================================================

    1. Acceptance

    In order to get any license under these terms, you must agree to them as both
    strict obligations and conditions to all your licenses.

    2. Copyright License

    The licensor grants you a copyright license to use the software for any
    permitted purpose.

    3. Distribution License and Network Use

    The licensor grants you an additional copyright license to distribute copies
    of the software and to make the software accessible to others over a computer
    network, strictly for a permitted purpose, provided that you comply with all
    conditions of Sections 4, 5, and 6.

    4. Changes, Derivative Suites, and Share-Alike Source Availability

    The licensor grants you an additional copyright license to make changes and
    new works based on the software—including derivative suites, forks, modified
    builds, plugins, wrappers, companion tools, and bundled distributions—strictly
    for a permitted purpose, and conditioned upon all of the following:

      a) Share-Alike: Any modified version, new work, or derivative suite that you
         distribute or make publicly accessible (including over a network) must be
         licensed as a whole under this exact same License (Goke-NC-SA-2.0). You
         may not add commercial permissions or remove, weaken, or bypass any
         noncommercial or source-availability restriction.

      b) Corresponding Source Availability: If you distribute any binary, build,
         package, container image, or derivative suite, or make any modified or
         unmodified version accessible to external users over a network, you must
         simultaneously make the complete corresponding source code (including all
         modifications, build instructions, and configuration scripts) publicly and
         freely available to every recipient and network user without charge.

      c) Modification Notices: You must retain all original attribution and
         clearly state in modified files and documentation that you changed the
         software and the date of your changes.

    5. Strict Prohibition of Commercial Activities (Software and Derivative Suites)

    No license is granted under these terms for any commercial activity using
    either the software itself or any modified version, fork, plugin, bundle,
    service, or derivative suite based on the software. Without limitation, the
    following activities are strictly prohibited unless you obtain a separate
    written commercial license signed by the licensor:

      a) Commercial Distribution and Bundling: Selling, charging fees for,
         monetizing downloads of, offering paid app-store or package listings for,
         selling pre-installed hardware/images containing, or bundling the
         software or any derivative suite with any commercial product or service.

      b) Commercial Consulting and Paid Services: Providing paid consulting, paid
         deployment, paid installation, paid customization, paid integration, paid
         technical support, or paid training services based on or centered around
         the software or any derivative suite.

      c) Advertising and Monetization: Injecting, displaying, or operating any
         advertisements, ad networks, sponsored placements, affiliate or referral
         links, paywalls, paid memberships, paid feature unlocks, or user-data /
         traffic monetization in or alongside the software, its clients, servers,
         web interfaces, landing pages, distribution channels, or any derivative
         suite.

      d) Commercial Hosting and Business Operations: Operating the software or any
         derivative suite as a paid or commercial SaaS/hosted service, or using it
         for commercial enterprise operations or revenue-generating activities.

    6. Notices

    You must ensure that anyone who gets a copy of any part of the software or any
    derivative suite from you also gets a copy of these terms, together with the
    full text of all lines above starting with "Required Notice:" and all original
    copyright notices. You may not imply endorsement by or official affiliation
    with the licensor without prior written consent.

    7. Patent License and Patent Defense

    The licensor grants you a patent license to the software, covering patent
    claims the licensor can license, or becomes able to license, that you would
    infringe by using the software, strictly for a permitted purpose.

    If you make any written claim that the software infringes or contributes to
    infringement of any patent, your patent license for the software granted under
    these terms ends immediately. If your company makes such a claim, your patent
    license ends immediately for work on behalf of your company.

    8. Fair Use and No Other Rights

    You may have "fair use" or equivalent statutory rights for the software under
    applicable law. These terms do not limit them. These terms do not grant any
    trademark rights or any other rights not expressly stated.

    9. Violations

    Any intentional commercial activity prohibited by Section 5, or any intentional
    breach of Section 4 (Share-Alike and Source Availability), terminates all your
    licenses immediately and permanently without notice.

    For any other first-time inadvertent violation, the first time you are notified
    in writing that you have violated any of these terms, or done anything with
    the software not covered by your licenses, your licenses can nonetheless
    continue if you come into full compliance with these terms, and take practical
    steps to correct past violations, within 32 days of receiving notice. Otherwise,
    all your licenses end immediately.

    10. No Liability

    ***As far as the law allows, the software comes as is, without any warranty or
    condition, and the licensor will not be liable to you for any damages arising
    out of these terms or the use or nature of the software, under any kind of
    legal claim.***

    11. Definitions

    "Licensor" means xihale and the respective copyright holders offering these
    terms.

    "Software" means the Goke (工课) software—including its Android client, core
    modules, scripting engine, sync server, embedded web client, landing page, and
    documentation—made available by the licensor under these terms.

    "Derivative Suites" means any modified version, fork, repackaged build,
    plugin, extension, companion app, wrapper service, container bundle, or
    combined software suite based on, incorporating, or derived from any
    copyrightable portion of the software.

    "Permitted Purpose" means strictly noncommercial personal use, personal study,
    private entertainment, hobbyist use, academic or public-interest research,
    experimentation, and testing for public knowledge, or strictly noncommercial
    use by noncommercial educational or charitable organizations without any direct
    or indirect financial gain, monetary compensation, or commercial advantage.

    "Commercial Activity" means any activity intended for or directed toward
    commercial advantage, monetary compensation, advertising revenue, paid service
    delivery, or business monetization, whether direct or indirect.

    "You" means the individual or entity agreeing to these terms.

    "Your company" means any legal entity, sole proprietorship, or other kind of
    organization that you work for, plus all organizations that have control over,
    are under the control of, or are under common control with that organization.

    "Control" means ownership of substantially all the assets of an entity, or the
    power to direct its management and policies by vote, contract, or otherwise.
    Control can be direct or indirect.

    "Your licenses" means all the licenses granted to you for the software under
    these terms.

    "Use" means anything you do with the software requiring one of your licenses.

    ================================================================================

    12. 中文说明（非正式参考译文，如有歧义以英文正文为准）

    本协议为《Goke 严格非商业共享协议 2.0》（Goke-NC-SA-2.0），基于 PolyForm
    Noncommercial License 1.0.0 修订并强化绝对非商业禁止、不接受商业授权/打赏赞助与传染性开源（Share-Alike）条款：

    1. 仅限非商业用途：仅允许用于个人学习、日常自用、兴趣研究、非营利学术实验等
       无任何直接或间接商业利益的用途。
    2. 严禁本体及衍生套件的一切商业活动：无论是本软件本体，还是基于本软件制作的
       衍生版本、二次打包应用、插件、封装服务或配套套件，均禁止用于任何商业活动，
       包括但不限于：
       - 付费分发、应用商店收费上架、商业捆绑或预装；
       - 商业咨询、付费部署、付费定制开发、付费运维支持或付费培训；
       - 广告投放、广告植入、商业推广、导流返佣、付费墙、打赏赞助或用户数据变现；
       - 商业 SaaS 托管或企业商业运营。
    3. 衍生作品须同协议开源（Share-Alike）：基于本软件修改、二次开发、制作衍生套件
       或向外部用户提供网络服务时，即使是非商业用途，也必须继续完整采用本协议
       （Goke-NC-SA-2.0）发布，并向所有接收方与网络用户免费公开全部对应的源代码、
       保留原作者版权与声明。
    4. 违约与免责：故意违反商业禁止或同协议开源义务的，授权立即终止；其余首次非
       故意违约在收到书面通知后 32 日内完全纠正并消除影响的，许可方可恢复。本软件
       按“现状”提供，不承担任何明示或默示担保及赔偿责任。
    """
}
