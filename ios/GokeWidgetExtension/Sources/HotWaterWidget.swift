import Foundation
import GokeCoreModel
import GokeShared
import SwiftUI
import WidgetKit

// MARK: - Timeline Entry & Provider

struct HotWaterWidgetEntry: TimelineEntry, Sendable {
    let date: Date
    let snapshot: HotWaterWidgetSnapshot
    let theme: GokeTheme
    let isPlaceholder: Bool

    static func placeholder(date: Date = Date()) -> HotWaterWidgetEntry {
        HotWaterWidgetEntry(
            date: date,
            snapshot: HotWaterWidgetSnapshot(
                isBound: true,
                dormLabel: "西四 408",
                canUseAmount: 2450,
                isArrearage: false,
                phase: .idle,
                lastCheckAt: Int64(date.timeIntervalSince1970 * 1000),
                lastActionAt: 0
            ),
            theme: GokeTheme.forPreset(.mint),
            isPlaceholder: true
        )
    }
}

struct HotWaterWidgetProvider: TimelineProvider, Sendable {
    typealias Entry = HotWaterWidgetEntry

    func placeholder(in context: Context) -> HotWaterWidgetEntry {
        HotWaterWidgetEntry.placeholder()
    }

    func getSnapshot(in context: Context, completion: @escaping (HotWaterWidgetEntry) -> Void) {
        if context.isPreview {
            completion(HotWaterWidgetEntry.placeholder())
            return
        }
        completion(loadLiveEntry(now: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HotWaterWidgetEntry>) -> Void) {
        let now = Date()
        let entry = loadLiveEntry(now: now)
        // Main app explicitly reloads timelines whenever balance/valve state updates;
        // use a conservative 2-hour fallback to preserve WidgetKit's daily budget.
        let nextRefresh = now.addingTimeInterval(2 * 3600)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadLiveEntry(now: Date) -> HotWaterWidgetEntry {
        let appGroup = AppGroupContainer.shared
        let hw = appGroup.readHotWaterWidgetSnapshot()
        let settings = appGroup.readWidgetSnapshot()?.settings ?? .default
        let theme = GokeTheme.resolve(settings: settings)
        return HotWaterWidgetEntry(date: now, snapshot: hw, theme: theme, isPlaceholder: false)
    }
}

// MARK: - Widget Definition

/// Home Screen & Lock Screen Smart Hot Water widget (iOS 17.0+ safe).
/// Tapping the card safely opens `goke://hotwater` (view only); valve toggling is isolated
/// to the explicit tactile action button (`goke://hotwater?action=toggle`) in Medium.
struct HotWaterWidget: Widget {
    static let kind = HotWaterWidgetSnapshot.widgetKind

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: HotWaterWidgetProvider()) { entry in
            HotWaterWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.theme.paper
                }
        }
        .contentMarginsDisabled()
        .configurationDisplayName("智慧热水")
        .description("快速查看宿舍热水余额与阀门状态，安全防误触。")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

// MARK: - Views

private struct HotWaterWidgetView: View {
    let entry: HotWaterWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode

    private var theme: GokeTheme { entry.theme }
    private var hw: HotWaterWidgetSnapshot { entry.snapshot }
    private var isAccented: Bool { WidgetFormat.isAccentedMode(renderingMode) }

    private static let openURL = URL(string: "goke://hotwater")!
    private static let toggleURL = URL(string: "goke://hotwater?action=toggle")!

    private var isOpen: Bool {
        hw.isBound && hw.phase == .valveOpen
    }

    private var isLowBalance: Bool {
        hw.isBound && (hw.isArrearage || hw.balanceYuan <= 3.0)
    }

    private var shortStatusWord: String {
        guard hw.isBound else { return "未绑定" }
        if hw.isArrearage { return "已欠费" }
        switch hw.phase {
        case .valveOpen: return "出水中"
        case .working: return "通信中"
        case .error: return "异常"
        case .idle, .unbound: return isLowBalance ? "余额偏低" : "空闲"
        }
    }

    private var statusTint: Color {
        guard hw.isBound else { return theme.muted }
        if hw.isArrearage || hw.phase == .error { return theme.error }
        switch hw.phase {
        case .valveOpen: return theme.accent
        case .working: return theme.modified
        case .error: return theme.error
        case .idle, .unbound: return isLowBalance ? theme.error : theme.accent
        }
    }

    private var statusLabelColor: Color {
        if isAccented { return Color.secondary }
        if hw.isArrearage || hw.phase == .error || isLowBalance { return theme.error }
        if isOpen { return theme.accent }
        return theme.muted
    }

    private var topDormHeader: some View {
        HStack(spacing: 5) {
            Image(systemName: isOpen ? "drop.fill" : "drop")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(isAccented ? Color.primary : statusTint)
                .widgetAccentable()
            Text(hw.dormLabel.isEmpty ? "智慧热水" : hw.dormLabel)
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(isAccented ? Color.primary : (hw.isBound ? theme.ink : theme.muted))
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                smallLayout
            case .systemMedium:
                mediumLayout
            case .accessoryCircular:
                accessoryCircularLayout
            case .accessoryRectangular:
                accessoryRectangularLayout
            default:
                smallLayout
            }
        }
        // Whole-card tap always opens the Hot Water view safely (prevents accidental BLE valve toggles).
        .widgetURL(Self.openURL)
    }

    // MARK: - Small (141×141 inner canvas — unified hierarchy with Medium)

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 0) {
            topDormHeader

            Spacer(minLength: 0)

            if hw.isBound {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String(format: "¥%.2f", hw.balanceYuan))
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.primary : (isLowBalance ? theme.error : theme.ink))
                        .minimumScaleFactor(0.75)
                        .lineLimit(1)

                    HStack(spacing: 5) {
                        Circle()
                            .fill(isAccented ? Color.primary : statusTint)
                            .frame(width: 6, height: 6)
                            .widgetAccentable()
                        Text(shortStatusWord)
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(statusLabelColor)
                            .lineLimit(1)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 3) {
                    Text("未绑定水表")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(isAccented ? Color.primary : theme.ink)
                        .lineLimit(1)
                    Text("轻点绑定")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .lineLimit(1)
                }
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: - Medium (332×141 inner canvas — full-height concentric right action tile)

    private var mediumLayout: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 0) {
                topDormHeader

                Spacer(minLength: 0)

                if hw.isBound {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(String(format: "¥%.2f", hw.balanceYuan))
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(isAccented ? Color.primary : (isLowBalance ? theme.error : theme.ink))
                            .minimumScaleFactor(0.75)
                            .lineLimit(1)

                        HStack(spacing: 5) {
                            Circle()
                                .fill(isAccented ? Color.primary : statusTint)
                                .frame(width: 6, height: 6)
                                .widgetAccentable()
                            Text(shortStatusWord)
                                .font(.system(size: 11.5, weight: .medium))
                                .foregroundStyle(statusLabelColor)
                                .lineLimit(1)
                        }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("未绑定水表")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(1)
                        Text("轻点绑定")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            // Full-height concentric right action tile (r=12) — the ONLY surface triggering `action=toggle`.
            Link(destination: hw.isBound ? Self.toggleURL : Self.openURL) {
                VStack(spacing: 6) {
                    Image(systemName: isOpen ? "stop.fill" : "drop.fill")
                        .font(.system(size: 24, weight: .semibold))
                    Text(hw.isBound ? (isOpen ? "关阀" : "放水") : "去绑定")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(
                    isAccented
                        ? Color.primary
                        : (isOpen ? theme.onError : theme.onAccentContainer)
                )
                .frame(width: 96)
                .frame(maxHeight: .infinity)
                .background {
                    if isAccented {
                        RoundedRectangle(cornerRadius: WidgetMetrics.tileRadius, style: .continuous)
                            .fill(Color.primary.opacity(0.1))
                            .overlay(
                                RoundedRectangle(cornerRadius: WidgetMetrics.tileRadius, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.28), lineWidth: 1)
                            )
                    } else {
                        RoundedRectangle(cornerRadius: WidgetMetrics.tileRadius, style: .continuous)
                            .fill(isOpen ? theme.error : theme.accentContainer)
                    }
                }
                .widgetAccentable()
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: - Lock Screen Accessories

    private var accessoryCircularLayout: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 1) {
                Image(systemName: isOpen ? "drop.fill" : "drop")
                    .font(.system(size: 15, weight: .semibold))
                    .widgetAccentable()
                if hw.isBound {
                    Text(String(format: "¥%.0f", hw.balanceYuan))
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                } else {
                    Text("热水")
                        .font(.system(size: 10, weight: .semibold))
                }
            }
        }
    }

    private var accessoryRectangularLayout: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: isOpen ? "drop.fill" : "drop")
                    .font(.system(size: 11, weight: .semibold))
                Text(hw.dormLabel.isEmpty ? "智慧热水" : hw.dormLabel)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
            }
            .widgetAccentable()

            if hw.isBound {
                Text(String(format: "¥%.2f · %@", hw.balanceYuan, shortStatusWord))
                    .font(.system(size: 12, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            } else {
                Text("未绑定水表")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
