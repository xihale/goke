import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Adaptive Snooze Planner (Parity with Android SnoozePlanner)

public enum GokeSnoozeScaleMode: Sendable, Equatable {
    case linear
    case exponential
    case standard
}

public struct GokeSnoozeOption: Identifiable, Equatable, Sendable {
    public let id: String
    public let delayMinutes: Int
    public let triggerAt: LocalDateTime
    public let label: String
    public let isAtTarget: Bool
    public let isTomorrowMorning: Bool

    public init(
        id: String,
        delayMinutes: Int,
        triggerAt: LocalDateTime,
        label: String,
        isAtTarget: Bool = false,
        isTomorrowMorning: Bool = false
    ) {
        self.id = id
        self.delayMinutes = delayMinutes
        self.triggerAt = triggerAt
        self.label = label
        self.isAtTarget = isAtTarget
        self.isTomorrowMorning = isTomorrowMorning
    }
}

public struct GokeSnoozePlan: Equatable, Sendable {
    public let mode: GokeSnoozeScaleMode
    public let targetAt: LocalDateTime?
    public let remainingMinutes: Int?
    public let options: [GokeSnoozeOption]
}

public enum GokeSnoozePlanner {
    public static let maxCustomMinutes: Int = 24 * 60

    public static func plan(
        now: LocalDateTime = .now(),
        targetAt: LocalDateTime?
    ) -> GokeSnoozePlan {
        if let targetAt {
            let remaining = Int(now.secondsUntil(targetAt) / 60)
            if remaining >= 2 {
                if remaining <= 30 {
                    let steps = [5, 10, 15].filter { $0 < remaining }
                    var opts = steps.map { mins in
                        GokeSnoozeOption(
                            id: "lin-\(mins)",
                            delayMinutes: mins,
                            triggerAt: now.addingMinutes(mins),
                            label: "\(mins)分钟后"
                        )
                    }
                    opts.append(
                        GokeSnoozeOption(
                            id: "at-target",
                            delayMinutes: remaining,
                            triggerAt: targetAt,
                            label: "准点提醒",
                            isAtTarget: true
                        )
                    )
                    return GokeSnoozePlan(
                        mode: .linear,
                        targetAt: targetAt,
                        remainingMinutes: remaining,
                        options: opts
                    )
                } else if remaining <= 240 {
                    let steps = [10, 20, 30, 60].filter { $0 < remaining }
                    var opts = steps.map { mins in
                        GokeSnoozeOption(
                            id: "exp-\(mins)",
                            delayMinutes: mins,
                            triggerAt: now.addingMinutes(mins),
                            label: mins >= 60 ? "\(mins / 60)小时后" : "\(mins)分钟后"
                        )
                    }
                    opts.append(
                        GokeSnoozeOption(
                            id: "at-target",
                            delayMinutes: remaining,
                            triggerAt: targetAt,
                            label: "准点提醒",
                            isAtTarget: true
                        )
                    )
                    return GokeSnoozePlan(
                        mode: .exponential,
                        targetAt: targetAt,
                        remainingMinutes: remaining,
                        options: opts
                    )
                }
            }
        }

        // Standard fallback (past target, no target, or >4h remaining)
        let standardDelays = [10, 15, 30, 60]
        var opts = standardDelays.map { mins in
            GokeSnoozeOption(
                id: "std-\(mins)",
                delayMinutes: mins,
                triggerAt: now.addingMinutes(mins),
                label: mins >= 60 ? "\(mins / 60)小时后" : "\(mins)分钟后"
            )
        }
        let tomorrowMorning = now.date.addingDays(1).atTime(hour: 8, minute: 30)
        let tomorrowDelay = max(1, Int(now.secondsUntil(tomorrowMorning) / 60))
        opts.append(
            GokeSnoozeOption(
                id: "tomorrow-morning",
                delayMinutes: tomorrowDelay,
                triggerAt: tomorrowMorning,
                label: "明早 8:30",
                isTomorrowMorning: true
            )
        )
        return GokeSnoozePlan(
            mode: .standard,
            targetAt: targetAt,
            remainingMinutes: targetAt.map { Int(now.secondsUntil($0) / 60) },
            options: opts
        )
    }

    public static func formatSpan(_ minutes: Int) -> String {
        let clamped = max(1, minutes)
        if clamped < 60 {
            return "\(clamped) 分钟"
        }
        let h = clamped / 60
        let m = clamped % 60
        return m == 0 ? "\(h) 小时" : "\(h) 小时 \(m) 分"
    }
}

// MARK: - In-App Quick Snooze Overlay

/// In-app notification tap preview & adaptive quick-snooze modal.
///
/// Explicitly omits course notes from the preview body (matching Android parity)
/// and degrades glass/blur & spring animations automatically under `GokePerformancePolicy`.
public struct QuickSnoozeOverlayView: View {
    public let payload: QuickSnoozePayload
    public let onDismiss: () -> Void

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var openedAt: LocalDateTime = .now()
    @State private var snoozePanelOpen: Bool = false
    @State private var showCustomInput: Bool = false
    @State private var customMinutesText: String = ""
    @State private var isWorking: Bool = false
    @FocusState private var customFieldFocused: Bool

    public init(payload: QuickSnoozePayload, onDismiss: @escaping () -> Void) {
        self.payload = payload
        self.onDismiss = onDismiss
    }

    private var live: AppSnapshot { container.liveSnapshot }

    private var linkedTask: GokeTask? {
        guard (payload.category == .task || payload.category == .exam), !payload.entityId.isEmpty else { return nil }
        return live.tasks.first(where: { $0.id == payload.entityId })
    }

    private var linkedCourse: Course? {
        guard (payload.category == .course || payload.category == .exam), !payload.entityId.isEmpty else { return nil }
        return live.courses.first(where: { $0.id == payload.entityId })
    }

    private var plan: GokeSnoozePlan {
        GokeSnoozePlanner.plan(now: openedAt, targetAt: payload.targetAt)
    }

    private var customMinutes: Int? {
        guard let value = Int(customMinutesText.trimmingCharacters(in: .whitespacesAndNewlines)),
              (1...GokeSnoozePlanner.maxCustomMinutes).contains(value) else {
            return nil
        }
        return value
    }

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    private var effectiveAnimation: Animation? {
        GokeMotion.snappy(reduceMotion: simplifyMotion)
    }

    private var categoryBadgeTitle: String {
        switch payload.category {
        case .course: linkedCourse?.isExam == true ? "考试提醒" : "课程提醒"
        case .exam: "考试提醒"
        case .task: linkedTask?.isExam == true ? "考试待办" : "待办提醒"
        case .event: "日程提醒"
        case .dailyDigest: "每日简报"
        }
    }

    private var categoryIcon: String {
        switch payload.category {
        case .course: linkedCourse?.isExam == true ? "graduationcap.fill" : "book.closed.fill"
        case .exam: "graduationcap.fill"
        case .task: "checklist"
        case .event: "calendar.badge.clock"
        case .dailyDigest: "sun.max.fill"
        }
    }

    public var body: some View {
        ZStack {
            // Backdrop scrim (zero blur when degraded)
            Color.black
                .opacity(performance.shouldDisableGlassAndBlur ? 0.52 : 0.36)
                .ignoresSafeArea()
                .onTapGesture {
                    customFieldFocused = false
                    onDismiss()
                }

            ScrollView(.vertical, showsIndicators: false) {
                cardContent
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.vertical, GokeSpacing.lg)
                    .frame(maxWidth: 420)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .transition(simplifyMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
        .accessibilityAddTraits(.isModal)
    }

    private var subtleHairline: some View {
        Rectangle()
            .fill(theme.outlineVariant.opacity(0.45))
            .frame(height: 0.5)
    }

    @ViewBuilder
    private var cardContent: some View {
        let shape = RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
        let inner = Group {
            if snoozePanelOpen {
                quickSnoozePanel
                    .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .trailing)))
            } else {
                VStack(alignment: .leading, spacing: GokeSpacing.md) {
                    headerSection
                    previewDetailsSection
                    subtleHairline
                    mainActionBar
                }
                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .leading)))
            }
        }
        .padding(GokeSpacing.lg)
        .animation(effectiveAnimation, value: snoozePanelOpen)

        inner
            .background(theme.surface, in: shape)
            .overlay(
                shape.strokeBorder(theme.outlineVariant.opacity(0.45), lineWidth: 0.5)
            )
            .shadow(
                color: performance.shouldDisableGlassAndBlur ? .clear : Color.black.opacity(0.08),
                radius: performance.shouldDisableGlassAndBlur ? 0 : 12,
                y: performance.shouldDisableGlassAndBlur ? 0 : 4
            )
    }

    private var headerSection: some View {
        HStack(alignment: .center, spacing: GokeSpacing.sm) {
            GokeBadge(categoryBadgeTitle, systemImage: categoryIcon, tone: payload.timeSensitive ? .tertiary : .accent)
            Spacer()
            Button {
                customFieldFocused = false
                onDismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(theme.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.mintCompact)
            .padding(.trailing, -10)
            .padding(.vertical, -8)
            .accessibilityLabel("关闭提醒预览")
        }
    }

    private var previewDetailsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(payload.title)
                .font(GokeTypography.titleMedium)
                .foregroundStyle(theme.ink)
                .lineLimit(2)

            if !payload.subtitle.isEmpty {
                Text(payload.subtitle)
                    .font(GokeTypography.bodyMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
            }

            if !payload.body.isEmpty {
                Text(payload.body)
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
            }

            // Course metadata preview explicitly omits `course.notes` for clean notification preview parity.
            if let course = linkedCourse, !course.teacher.isEmpty {
                Label(course.teacher, systemImage: "person")
                    .font(.caption)
                    .foregroundStyle(theme.muted)
            }
        }
    }

    private var mainActionBar: some View {
        let hasCompleteAction = linkedTask.map { !$0.done } ?? false
        return VStack(spacing: GokeSpacing.sm) {
            HStack(spacing: GokeSpacing.sm) {
                Button {
                    withAnimation(effectiveAnimation) {
                        snoozePanelOpen = true
                    }
                } label: {
                    Label("稍后提醒", systemImage: "alarm")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                        .foregroundStyle(theme.onAccentContainer)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(theme.accentContainer, in: Capsule())
                        .contentShape(Capsule())
                }
                .buttonStyle(.mintCompact)
                .disabled(isWorking)

                if let task = linkedTask, !task.done {
                    Button {
                        completeLinkedTask(id: task.id)
                    } label: {
                        Label("标记完成", systemImage: "checkmark.circle.fill")
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.onAccent)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(theme.accent, in: Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.mintCompact)
                    .disabled(isWorking)
                } else {
                    Button {
                        openDetail()
                    } label: {
                        Label("查看详情", systemImage: "arrow.up.forward.app")
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.onAccent)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(theme.accent, in: Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.mintCompact)
                    .disabled(isWorking)
                }
            }

            if hasCompleteAction {
                Button {
                    openDetail()
                } label: {
                    Text("查看详情")
                        .font(GokeTypography.labelLarge)
                        .foregroundStyle(theme.accent)
                        .frame(minHeight: 36)
                        .padding(.horizontal, GokeSpacing.md)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(isWorking)
            }
        }
    }

    private var quickSnoozePanel: some View {
        let totalItems = plan.options.count + 1
        let columnCount: Int = totalItems <= 4 ? totalItems : (totalItems <= 6 ? 3 : 4)
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: max(1, columnCount))

        return VStack(alignment: .leading, spacing: GokeSpacing.md) {
            ZStack {
                HStack {
                    Button {
                        customFieldFocused = false
                        withAnimation(effectiveAnimation) {
                            showCustomInput = false
                            snoozePanelOpen = false
                        }
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(theme.muted)
                            .frame(width: 44, height: 44)
                            .contentShape(Circle())
                    }
                    .buttonStyle(.mintCompact)
                    .padding(.leading, -10)
                    .accessibilityLabel("返回")
                    Spacer()
                }

                Text("快捷延后")
                    .font(GokeTypography.titleMedium.weight(.semibold))
                    .foregroundStyle(theme.ink)
            }

            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(plan.options) { option in
                    snoozeGridTile(
                        label: snoozeSubLabel(for: option),
                        accessibilityLabel: "\(option.label)（\(snoozeSubLabel(for: option))）",
                        selected: false
                    ) {
                        commitSnooze(option: option)
                    } badge: {
                        if option.isAtTarget {
                            Image(systemName: "alarm")
                                .font(.system(size: 24, weight: .medium))
                                .foregroundStyle(theme.accent)
                        } else if option.isTomorrowMorning {
                            Image(systemName: "moon.stars")
                                .font(.system(size: 24, weight: .medium))
                                .foregroundStyle(theme.accent)
                        } else {
                            SnoozeClockBadge(
                                text: snoozeShortBadge(for: option),
                                tint: theme.accent
                            )
                        }
                    }
                }

                snoozeGridTile(
                    label: "自定义",
                    accessibilityLabel: "自定义延后时长",
                    selected: showCustomInput
                ) {
                    withAnimation(effectiveAnimation) {
                        showCustomInput.toggle()
                    }
                    customFieldFocused = showCustomInput
                } badge: {
                    Image(systemName: "pencil")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(theme.accent)
                }
            }

            if showCustomInput {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("输入延后分钟数 (1–1440)", text: $customMinutesText)
                        #if os(iOS)
                        .keyboardType(.numberPad)
                        #endif
                        .font(GokeTypography.bodyMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 44)
                        .background(
                            theme.surfaceHighest,
                            in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                        )
                        .focused($customFieldFocused)
                        .submitLabel(.done)
                        .onSubmit {
                            if let mins = customMinutes {
                                customFieldFocused = false
                                commitCustomSnooze(minutes: mins)
                            }
                        }

                    if let mins = customMinutes {
                        let previewAt = LocalDateTime.now().addingMinutes(mins)
                        Text("将于 \(previewAt.date.month)月\(previewAt.date.day)日 \(previewAt.time.description) 再次提醒")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    } else if !customMinutesText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text("请输入 1 到 \(GokeSnoozePlanner.maxCustomMinutes) 之间的整数分钟")
                            .font(.caption)
                            .foregroundStyle(theme.error)
                    }

                    HStack(spacing: 8) {
                        Spacer()
                        Button {
                            customFieldFocused = false
                            withAnimation(effectiveAnimation) {
                                showCustomInput = false
                            }
                        } label: {
                            Text("取消")
                                .font(GokeTypography.labelLarge)
                                .foregroundStyle(theme.muted)
                                .padding(.horizontal, 12)
                                .frame(minHeight: 40)
                        }
                        .buttonStyle(.mintCompact)

                        let canConfirmCustom = customMinutes != nil && !isWorking
                        Button {
                            if let mins = customMinutes {
                                customFieldFocused = false
                                commitCustomSnooze(minutes: mins)
                            }
                        } label: {
                            Text("确定")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(canConfirmCustom ? theme.onAccent : theme.muted)
                                .padding(.horizontal, 18)
                                .frame(minHeight: 40)
                                .background(
                                    canConfirmCustom ? theme.accent : theme.surfaceHighest,
                                    in: Capsule()
                                )
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.mintCompact)
                        .disabled(!canConfirmCustom)
                    }
                }
                .padding(.top, 2)
                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.98, anchor: .top)))
            }
        }
    }

    private func snoozeGridTile<Badge: View>(
        label: String,
        accessibilityLabel: String,
        selected: Bool,
        action: @escaping () -> Void,
        @ViewBuilder badge: () -> Badge
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    badge()
                }
                .frame(height: 38)

                Text(label)
                    .font(GokeTypography.labelMedium.weight(selected ? .medium : .regular))
                    .monospacedDigit()
                    .foregroundStyle(selected ? theme.accent : theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal, 4)
            .background(
                selected ? theme.accentContainer.opacity(0.45) : Color.clear,
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.mintCompact)
        .disabled(isWorking)
        .accessibilityLabel(accessibilityLabel)
    }

    private func snoozeShortBadge(for option: GokeSnoozeOption) -> String {
        if option.delayMinutes < 60 {
            return "\(option.delayMinutes) m"
        }
        if option.delayMinutes % 1440 == 0 {
            return "\(option.delayMinutes / 1440) d"
        }
        if option.delayMinutes % 60 == 0 {
            return "\(option.delayMinutes / 60) h"
        }
        return "\(option.delayMinutes) m"
    }

    private func snoozeSubLabel(for option: GokeSnoozeOption) -> String {
        if option.isTomorrowMorning {
            return option.label
        }
        if option.triggerAt.date == openedAt.date {
            return option.triggerAt.time.description
        }
        return "\(option.triggerAt.date.month)月\(option.triggerAt.date.day)日 \(option.triggerAt.time.description)"
    }

    private func commitSnooze(option: GokeSnoozeOption) {
        isWorking = true
        let liveNow = LocalDateTime.now()
        let fireAt: LocalDateTime
        if option.isAtTarget || option.isTomorrowMorning {
            fireAt = option.triggerAt > liveNow ? option.triggerAt : liveNow.addingMinutes(1)
        } else {
            fireAt = liveNow.addingMinutes(option.delayMinutes)
        }
        let delayMinutes = max(1, Int(ceil(Double(liveNow.secondsUntil(fireAt)) / 60.0)))
        Task {
            await container.reminderScheduler.scheduleSnooze(
                entityId: payload.entityId,
                category: payload.category,
                title: payload.title,
                subtitle: payload.subtitle,
                body: payload.body,
                minutes: delayMinutes,
                timeSensitive: payload.timeSensitive,
                deepLink: payload.deepLink,
                targetAt: payload.targetAt
            )
            GokeFeedback.play(.success)
            actions.showToast(message: "已延后至 \(fireAt.time.description) 提醒")
            isWorking = false
            onDismiss()
        }
    }

    private func commitCustomSnooze(minutes: Int) {
        isWorking = true
        let fireAt = LocalDateTime.now().addingMinutes(minutes)
        Task {
            await container.reminderScheduler.scheduleSnooze(
                entityId: payload.entityId,
                category: payload.category,
                title: payload.title,
                subtitle: payload.subtitle,
                body: payload.body,
                minutes: minutes,
                timeSensitive: payload.timeSensitive,
                deepLink: payload.deepLink,
                targetAt: payload.targetAt
            )
            GokeFeedback.play(.success)
            actions.showToast(message: "已延后 \(GokeSnoozePlanner.formatSpan(minutes))（\(fireAt.time.description)）")
            isWorking = false
            onDismiss()
        }
    }

    private func completeLinkedTask(id: String) {
        isWorking = true
        Task {
            _ = await actions.completeTask(id: id)
            isWorking = false
            onDismiss()
        }
    }

    private func openDetail() {
        if let url = URL(string: payload.deepLink) {
            _ = router.handleDeepLink(url)
        }
        onDismiss()
    }
}

// MARK: - Frameless Clock-Arc Snooze Badge (Parity with Android SnoozeClockBadge)

private struct SnoozeClockBadge: View {
    let text: String
    let tint: Color

    var body: some View {
        VStack(spacing: 1) {
            Canvas { context, size in
                let stroke: CGFloat = 2
                let radius = (size.width - stroke * 3) / 2
                let cx = size.width / 2
                let cy = radius + stroke
                let center = CGPoint(x: cx, y: cy)

                var arc = Path()
                arc.addArc(
                    center: center,
                    radius: radius,
                    startAngle: .degrees(188),
                    endAngle: .degrees(352),
                    clockwise: false
                )
                context.stroke(
                    arc,
                    with: .color(tint),
                    style: StrokeStyle(lineWidth: stroke, lineCap: .round)
                )

                // Clockwise arrowhead at the right tip (~352°)
                let endRad = 352.0 * Double.pi / 180.0
                let tip = CGPoint(
                    x: cx + radius * CGFloat(cos(endRad)),
                    y: cy + radius * CGFloat(sin(endRad))
                )
                let headLen: CGFloat = 3.6
                var arrow = Path()
                arrow.move(to: tip)
                arrow.addLine(to: CGPoint(x: tip.x - headLen * 0.95, y: tip.y - headLen * 0.25))
                arrow.move(to: tip)
                arrow.addLine(to: CGPoint(x: tip.x + headLen * 0.25, y: tip.y - headLen * 0.95))
                context.stroke(
                    arrow,
                    with: .color(tint),
                    style: StrokeStyle(lineWidth: stroke, lineCap: .round)
                )

                // Tiny clock hands inside the arc
                let handBaseY = cy - 1.5
                var hands = Path()
                hands.move(to: CGPoint(x: cx, y: handBaseY))
                hands.addLine(to: CGPoint(x: cx, y: handBaseY - 4.8))
                hands.move(to: CGPoint(x: cx, y: handBaseY))
                hands.addLine(to: CGPoint(x: cx + 3.2, y: handBaseY))
                context.stroke(
                    hands,
                    with: .color(tint),
                    style: StrokeStyle(lineWidth: stroke * 0.9, lineCap: .round)
                )
            }
            .frame(width: 30, height: 16)

            Text(text)
                .font(.system(size: 13, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(tint)
                .lineLimit(1)
        }
    }
}

// MARK: - Root Host Modifier

private struct QuickSnoozeHostModifier: ViewModifier {
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var activePayload: QuickSnoozePayload?

    func body(content: Content) -> some View {
        content
            .overlay {
                if let payload = activePayload {
                    QuickSnoozeOverlayView(payload: payload) {
                        dismissOverlay()
                    }
                    .zIndex(900)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .gokePresentQuickSnooze)) { notification in
                guard let payload = notification.userInfo?["payload"] as? QuickSnoozePayload else { return }
                let simplify = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                withAnimation(GokeMotion.snappy(reduceMotion: simplify)) {
                    activePayload = payload
                }
            }
    }

    private func dismissOverlay() {
        let simplify = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
        withAnimation(GokeMotion.dismiss(reduceMotion: simplify)) {
            activePayload = nil
        }
    }
}

public extension View {
    /// Installs the global in-app notification Quick-Snooze overlay host.
    func gokeQuickSnoozeHost() -> some View {
        modifier(QuickSnoozeHostModifier())
    }
}
