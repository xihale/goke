import SwiftUI
import GokeCore
import GokeShared
#if canImport(AVFoundation)
import AVFoundation
#endif
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Tactile Press Button Style (Zero Tap Haptics)

/// Stratified tactile press scale for custom canvas interactive cards only.
///
/// **iOS HIG Rule**: Use `.mintCompact` / `.mintRow` ONLY on custom non-List interactive
/// surfaces (such as Timetable course blocks and Calendar day cells). NEVER attach this
/// style to `List` / `Form` rows, `NavigationLink`, `.toolbar` buttons, or standard
/// `.bordered` / `.borderedProminent` buttons.
/// - Custom full-width canvas cards scale to `0.98x`
/// - Compact custom controls (course blocks, day cells) scale to `0.96x`
/// - Uses fast spatial spring (damping 1.0), respects Reduce Motion, and emits ZERO tap haptics.
struct MintPressableButtonStyle: ButtonStyle {
    enum Scale: Sendable, Equatable {
        case row
        case compact
        case custom(CGFloat)

        var value: CGFloat {
            switch self {
            case .row: 0.98
            case .compact: 0.96
            case .custom(let factor): factor
            }
        }
    }

    let scale: CGFloat

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    init(scale: Scale = .compact) {
        self.scale = scale.value
    }

    init(scale: CGFloat) {
        self.scale = scale
    }

    func makeBody(configuration: Configuration) -> some View {
        let simplifyMotion = reduceMotion || performance.shouldSimplifyMotion
        configuration.label
            .scaleEffect(configuration.isPressed && !simplifyMotion ? scale : 1.0)
            .opacity(configuration.isPressed && simplifyMotion ? 0.72 : 1.0)
            .animation(GokeMotion.press(reduceMotion: simplifyMotion), value: configuration.isPressed)
    }
}

/// Alias matching design-system shorthand (`GokePressStyle`) for `MintPressableButtonStyle`.
typealias GokePressStyle = MintPressableButtonStyle

extension ButtonStyle where Self == MintPressableButtonStyle {
    /// Full-width list row press feedback (`0.98x` spring, zero tap haptics).
    static var mintRow: MintPressableButtonStyle {
        MintPressableButtonStyle(scale: .row)
    }

    /// Compact control press feedback (`0.96x` spring, zero tap haptics).
    static var mintCompact: MintPressableButtonStyle {
        MintPressableButtonStyle(scale: .compact)
    }

    /// Custom scale tactile press feedback (zero tap haptics).
    static func mintPressable(scale: CGFloat) -> MintPressableButtonStyle {
        MintPressableButtonStyle(scale: scale)
    }
}

// MARK: - Gesture-Only Haptics Helper & Declarative Sensory Feedback

/// Centralized haptic feedback helper strictly for continuous/committed gestures.
///
/// Reuses cached `@MainActor` feedback generators and pre-warms the Taptic Engine
/// via `.prepare()` so drag-snap and wheel ticks fire with minimal latency.
///
/// **Design Rule**: Never trigger haptics on regular button taps. Only call these helpers
/// for long-press activation, drag-slot snapping, swipe-to-commit, time-wheel ticks,
/// and destructive confirmations.
@MainActor
enum GokeHaptics {
    /// Optional provider injected by `AppContainer` / tests; defaults to reading persisted `AppSettings.hapticFeedback` (`true` if unset).
    static var isEnabledProvider: (@MainActor () -> Bool)?

    private static var cachedSettingsData: Data?
    private static var cachedHapticEnabled: Bool = true

    /// Whether haptic feedback is currently enabled in `AppSettings` (defaults to `true` when not persisted/injected).
    static var isEnabled: Bool {
        if let provider = isEnabledProvider {
            return provider()
        }
        let data = AppGroupContainer.shared.userDefaults.data(forKey: AppSettingsStore.storageKey)
        if data != cachedSettingsData {
            cachedSettingsData = data
            if let data,
               let decoded = try? MintJSON.makeDecoder().decode(AppSettings.self, from: data)
            {
                cachedHapticEnabled = decoded.hapticFeedback
            } else {
                cachedHapticEnabled = true
            }
        }
        return cachedHapticEnabled
    }

    #if canImport(UIKit)
    private static let impactMedium = UIImpactFeedbackGenerator(style: .medium)
    private static let impactLight = UIImpactFeedbackGenerator(style: .light)
    private static let selection = UISelectionFeedbackGenerator()
    private static let notification = UINotificationFeedbackGenerator()
    #endif

    /// Pre-warms the Taptic Engine before a drag/reorder session begins.
    static func prepareDragSession() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        impactMedium.prepare()
        selection.prepare()
        #endif
    }

    /// Pre-warms the Taptic Engine before a swipe-to-commit or destructive swipe gesture.
    static func prepareSwipeSession() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        impactLight.prepare()
        notification.prepare()
        #endif
    }

    /// Emits a medium impact when a long-press drag or reorder session begins,
    /// and pre-warms the selection generator for subsequent slot snaps.
    static func longPressStarted() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        impactMedium.impactOccurred()
        selection.prepare()
        #endif
    }

    /// Emits a crisp selection tick when a dragged block snaps to a new timetable slot.
    static func dragSnap() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        selection.selectionChanged()
        selection.prepare()
        #endif
    }

    /// Emits a light impact when a swipe action crosses the commit threshold.
    static func swipeCommit() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        impactLight.impactOccurred()
        #endif
    }

    /// Emits a subtle selection tick for custom wheel or ruler snapping.
    static func wheelTick() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        selection.selectionChanged()
        selection.prepare()
        #endif
    }

    /// Emits a warning notification when confirming a destructive swipe/drop.
    static func destructiveConfirm() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        notification.notificationOccurred(.warning)
        #endif
    }
}

/// Unified tactile & light-chime feedback engine matching Android `FeedbackEngine`.
///
/// - Sound playback uses `AVAudioPlayer` on `AVAudioSession.Category.ambient` with `.mixWithOthers`,
///   automatically respecting the iOS Ring/Silent switch and never interrupting background audio.
/// - Haptics respect `AppSettings.hapticFeedback`; chimes respect `AppSettings.soundFeedback`.
/// - All audio/haptic calls degrade gracefully when assets or hardware are unavailable.
@MainActor
enum GokeFeedback {
    /// Semantic feedback events matching Android `FeedbackEvent`.
    enum Event: Sendable, Equatable {
        /// Single task completion: crisp double-beat haptic + rising two-note marimba chime.
        case taskComplete
        /// All today tasks cleared / batch celebration: rising triple haptic + four-note arpeggio.
        case celebrate
        /// Create / save / sync / snooze success: crisp light haptic + soft pop chime.
        case success
        /// Undo / restore: soft selection tick + descending gentle chime.
        case undo
        /// Light confirmation (uncheck, copy): subtle selection tick, silent.
        case softTick
        /// Destructive action (delete): warning haptic, silent.
        case delete
    }

    /// Forwarded haptic enabled provider for compatibility with `GokeHaptics`.
    static var isEnabledProvider: (@MainActor () -> Bool)? {
        get { GokeHaptics.isEnabledProvider }
        set { GokeHaptics.isEnabledProvider = newValue }
    }

    /// Whether haptic feedback is currently enabled.
    static var isEnabled: Bool { GokeHaptics.isEnabled }

    /// Optional sound-enabled provider injected by `AppContainer` / `RootTabView`;
    /// defaults to reading persisted `AppSettings.soundFeedback` (`true` if unset).
    static var isSoundEnabledProvider: (@MainActor () -> Bool)?

    private static var cachedSoundSettingsData: Data?
    private static var cachedSoundEnabled: Bool = true

    /// Whether sound feedback is currently enabled in `AppSettings` (defaults to `true` when unset).
    static var isSoundEnabled: Bool {
        if let provider = isSoundEnabledProvider {
            return provider()
        }
        let data = AppGroupContainer.shared.userDefaults.data(forKey: AppSettingsStore.storageKey)
        if data != cachedSoundSettingsData {
            cachedSoundSettingsData = data
            if let data,
               let decoded = try? MintJSON.makeDecoder().decode(AppSettings.self, from: data)
            {
                cachedSoundEnabled = decoded.soundFeedback
            } else {
                cachedSoundEnabled = true
            }
        }
        return cachedSoundEnabled
    }

    #if canImport(UIKit)
    private static let impactLight = UIImpactFeedbackGenerator(style: .light)
    private static let impactMedium = UIImpactFeedbackGenerator(style: .medium)
    private static let selection = UISelectionFeedbackGenerator()
    private static let notification = UINotificationFeedbackGenerator()
    #endif

    #if canImport(AVFoundation)
    private static var audioSessionConfigured: Bool = false
    private static var players: [String: AVAudioPlayer] = [:]
    #endif

    /// Plays both haptic and sound feedback for `event`, respecting user preferences.
    static func play(_ event: Event) {
        if GokeHaptics.isEnabled {
            emitHaptic(for: event)
        }
        if isSoundEnabled {
            emitSound(for: event)
        }
    }

    static func taskComplete() { play(.taskComplete) }
    static func celebrate() { play(.celebrate) }
    static func success() { play(.success) }
    static func undo() { play(.undo) }
    static func softTick() { play(.softTick) }
    static func delete() { play(.delete) }

    /// Instant haptic preview when enabling haptic feedback in Settings.
    static func previewHaptic() {
        emitHaptic(for: .taskComplete)
    }

    /// Instant chime preview when enabling sound feedback in Settings.
    static func previewSound() {
        emitSound(for: .taskComplete)
    }

    /// Returns `true` when completing `completingIds` clears every pending task due/scheduled
    /// for today (including today's agenda tasks/blocks and overdue tasks).
    static func isClearingTodayTasks(
        snapshot: AppSnapshot,
        completingIds: Set<String>,
        now: LocalDateTime = .now()
    ) -> Bool {
        guard !completingIds.isEmpty else { return false }
        let today = now.date
        var pendingTodayIds = Set<String>()
        for item in AgendaEngine.forDay(snapshot: snapshot, date: today, includeDoneTasks: false) {
            switch item {
            case .task(let task, _, _) where !task.done:
                pendingTodayIds.insert(task.id)
            case .block(let task, _, _) where !task.done:
                pendingTodayIds.insert(task.id)
            default:
                break
            }
        }
        for task in TaskLogic.filter(snapshot.tasks, smartList: .OVERDUE, now: now) where !task.done {
            pendingTodayIds.insert(task.id)
        }
        return !pendingTodayIds.isEmpty && pendingTodayIds.isSubset(of: completingIds)
    }

    private static func emitHaptic(for event: Event) {
        #if canImport(UIKit)
        switch event {
        case .taskComplete:
            impactMedium.impactOccurred(intensity: 0.85)
            impactLight.prepare()
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(55))
                impactLight.impactOccurred(intensity: 0.6)
            }
        case .celebrate:
            impactLight.impactOccurred(intensity: 0.6)
            impactMedium.prepare()
            notification.prepare()
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(60))
                impactMedium.impactOccurred(intensity: 0.8)
                try? await Task.sleep(for: .milliseconds(75))
                notification.notificationOccurred(.success)
            }
        case .success:
            impactLight.impactOccurred(intensity: 0.7)
        case .undo:
            selection.selectionChanged()
        case .softTick:
            selection.selectionChanged()
        case .delete:
            notification.notificationOccurred(.warning)
        }
        #endif
    }

    private static func soundAsset(for event: Event) -> (name: String, volume: Float)? {
        switch event {
        case .taskComplete: ("goke_sfx_complete", 0.68)
        case .celebrate: ("goke_sfx_celebrate", 0.76)
        case .success: ("goke_sfx_pop", 0.55)
        case .undo: ("goke_sfx_undo", 0.52)
        case .softTick, .delete: nil
        }
    }

    private static func emitSound(for event: Event) {
        guard let asset = soundAsset(for: event) else { return }
        #if canImport(AVFoundation)
        configureAudioSessionIfNeeded()
        guard let player = playerForSound(named: asset.name) else { return }
        player.volume = asset.volume
        player.currentTime = 0
        player.play()
        #endif
    }

    #if canImport(AVFoundation)
    private static func configureAudioSessionIfNeeded() {
        guard !audioSessionConfigured else { return }
        audioSessionConfigured = true
        #if os(iOS)
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        } catch {
            // Non-fatal: fall back to default session behavior.
        }
        #endif
    }

    private static func playerForSound(named name: String) -> AVAudioPlayer? {
        if let cached = players[name] {
            return cached
        }
        let url =
            Bundle.main.url(forResource: name, withExtension: "caf", subdirectory: "Sounds")
            ?? Bundle.main.url(forResource: name, withExtension: "caf")
        guard let url else { return nil }
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            players[name] = player
            return player
        } catch {
            return nil
        }
    }
    #endif
}

extension View {
    /// Declarative selection-tick sensory feedback for state-driven drag/reschedule slot snapping.
    ///
    /// **Design Rule**: Bind strictly to drag-hover slot or wheel tick state, never to regular button taps.
    func gokeDragSnapFeedback<T: Equatable>(trigger: T) -> some View {
        sensoryFeedback(.selection, trigger: trigger) { _, _ in
            GokeHaptics.isEnabled
        }
    }

    /// Declarative light-impact sensory feedback for committed swipe gestures.
    ///
    /// **Design Rule**: Bind strictly to swipe-commit triggers, never to regular button taps.
    func gokeSwipeCommitFeedback<T: Equatable>(trigger: T) -> some View {
        sensoryFeedback(.impact(weight: .light), trigger: trigger) { _, _ in
            GokeHaptics.isEnabled
        }
    }

    /// Declarative warning sensory feedback for destructive swipe/drop confirmations.
    func gokeDestructiveFeedback<T: Equatable>(trigger: T) -> some View {
        sensoryFeedback(.warning, trigger: trigger) { _, _ in
            GokeHaptics.isEnabled
        }
    }
}

// MARK: - GokeMintFab

/// Floating quick-add button matching Android `MintFab` (`Kit.kt:1211-1226`):
/// soft `theme.accentContainer` surface + `theme.onAccentContainer` icon + `16pt` continuous corners + subtle `3pt` shadow.
/// Zero tap haptics per `DESIGN.md`.
struct GokeMintFab: View {
    private let accessibilityLabel: String
    private let action: () -> Void

    @Environment(\.gokeTheme) private var theme

    init(
        accessibilityLabel: String = "快速添加",
        action: @escaping () -> Void
    ) {
        self.accessibilityLabel = accessibilityLabel
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(theme.onAccentContainer)
                .frame(width: 56, height: 56)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                        .fill(theme.accentContainer)
                )
                .shadow(color: Color.black.opacity(0.08), radius: 3, x: 0, y: 2)
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel(accessibilityLabel)
    }
}

// MARK: - GokePaperCard

/// Single-layer Goke semantic surface card (`16pt` continuous corners, zero shadow) on `theme.surface`.
struct GokePaperCard<Content: View>: View {
    private let padding: CGFloat
    private let cornerRadius: CGFloat
    private let content: Content

    @Environment(\.gokeTheme) private var theme

    init(
        padding: CGFloat = GokeSpacing.lg,
        cornerRadius: CGFloat = GokeRadius.lg,
        @ViewBuilder content: () -> Content
    ) {
        self.padding = padding
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(theme.surface)
            }
    }
}

// MARK: - GokeSectionHeader

/// Section label matching Android `Kit.SectionLabel`: left-aligned `labelLarge` title + right-aligned `labelMedium` count.
struct GokeSectionHeader<Trailing: View>: View {
    private let title: String
    private let count: Int?
    private let trailing: Trailing

    @Environment(\.gokeTheme) private var theme

    init(
        _ title: String,
        count: Int? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.count = count
        self.trailing = trailing()
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: GokeSpacing.sm) {
            Text(title)
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)

            Spacer(minLength: GokeSpacing.sm)

            if let count {
                Text("\(count)")
                    .font(GokeTypography.labelMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
            }

            trailing
        }
        .padding(.vertical, GokeSpacing.xs)
        .accessibilityAddTraits(.isHeader)
    }
}

extension GokeSectionHeader where Trailing == EmptyView {
    init(_ title: String, count: Int? = nil) {
        self.init(title, count: count) {
            EmptyView()
        }
    }
}

// MARK: - UndoToastBanner

/// Compact 44pt single-line Deep Forest Ink capsule banner for non-intrusive feedback and single-tap undo.
///
/// Uses a high-contrast dark forest ink surface (`#202D29` light / `#2A3733` dark) over Mint Paper,
/// concentric 30pt tonal mint undo pill (`7pt` inset inside `44pt` capsule), auto-dismiss timer,
/// and interactive swipe-down / horizontal flick dismiss with Reduce Motion–aware transitions.
struct UndoToastBanner: View {
    private struct TimerKey: Equatable {
        let message: String
        let actionTitle: String?
        let duration: TimeInterval
    }

    private let message: String
    private let actionTitle: String?
    private let duration: TimeInterval
    private let onAction: (() -> Void)?
    private let onDismiss: (() -> Void)?

    @Environment(\.gokeTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var dragOffset: CGSize = .zero
    @State private var isDismissing: Bool = false
    @State private var hasRequestedDismiss: Bool = false

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    init(
        message: String,
        actionTitle: String? = nil,
        duration: TimeInterval = 4.0,
        onAction: (() -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        self.message = message
        self.actionTitle = actionTitle
        self.duration = duration
        self.onAction = onAction
        self.onDismiss = onDismiss
    }

    /// Recommended zero-bounce entrance/exit transition for hosting `UndoToastBanner`.
    static func transition(reduceMotion: Bool) -> AnyTransition {
        if reduceMotion {
            return .opacity
        }
        return .asymmetric(
            insertion: .move(edge: .bottom)
                .combined(with: .opacity)
                .combined(with: .scale(scale: 0.94)),
            removal: .move(edge: .bottom).combined(with: .opacity)
        )
    }

    private var surfaceFill: Color {
        colorScheme == .dark
            ? Color(hex: 0x2A3733, opacity: 0.98)
            : Color(hex: 0x202D29, opacity: 0.96)
    }

    private var rimStrokeColor: Color {
        colorScheme == .dark
            ? Color.white.opacity(0.10)
            : Color.white.opacity(0.08)
    }

    private var messageColor: Color {
        colorScheme == .dark
            ? Color(hex: 0xE6EEEB)
            : Color.white.opacity(0.95)
    }

    private var actionForeground: Color {
        colorScheme == .dark
            ? theme.onAccentContainer
            : Color(hex: 0x8EE0CE)
    }

    private var actionBackground: Color {
        colorScheme == .dark
            ? theme.accentContainer
            : Color(hex: 0x7CCDBA, opacity: 0.20)
    }

    private var dragDismissOpacity: Double {
        let verticalProgress = max(0.0, Double(dragOffset.height)) / 110.0
        let horizontalProgress = abs(Double(dragOffset.width)) / 160.0
        return max(0.25, 1.0 - max(verticalProgress, horizontalProgress))
    }

    var body: some View {
        let hasAction = actionTitle != nil && onAction != nil
        let timerKey = TimerKey(message: message, actionTitle: actionTitle, duration: duration)

        HStack(spacing: 10) {
            if !hasAction {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x7CCDBA))
                    .accessibilityHidden(true)
            }

            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(messageColor)
                .lineLimit(1)
                .truncationMode(.tail)
                .layoutPriority(0)

            if let actionTitle, let onAction {
                Button {
                    guard !hasRequestedDismiss else { return }
                    hasRequestedDismiss = true
                    onAction()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 11, weight: .semibold))
                        Text(actionTitle)
                            .font(.system(size: 13, weight: .semibold))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(actionForeground)
                    .padding(.horizontal, 12)
                    .frame(height: 30)
                    .background(Capsule().fill(actionBackground))
                    .frame(height: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .layoutPriority(1)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, hasAction ? 7 : 16)
        .frame(height: 44)
        .background(
            Capsule()
                .fill(surfaceFill)
        )
        .overlay(
            Capsule()
                .strokeBorder(rimStrokeColor, lineWidth: 0.5)
                .allowsHitTesting(false)
        )
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.30 : 0.16),
            radius: 12,
            x: 0,
            y: 4
        )
        .offset(x: dragOffset.width, y: dragOffset.height)
        .opacity(isDismissing ? 0 : dragDismissOpacity)
        .task(id: timerKey) {
            hasRequestedDismiss = false
            isDismissing = false
            dragOffset = .zero
            guard duration > 0, let onDismiss else { return }
            try? await Task.sleep(for: .seconds(duration))
            guard !Task.isCancelled, !isDismissing, !hasRequestedDismiss else { return }
            hasRequestedDismiss = true
            onDismiss()
        }
        .gesture(
            DragGesture(minimumDistance: 10)
                .onChanged { value in
                    guard onDismiss != nil, !isDismissing else { return }
                    let dx = value.translation.width
                    let dy = value.translation.height
                    if abs(dx) > abs(dy) * 1.3 {
                        dragOffset = CGSize(width: dx, height: 0)
                    } else {
                        dragOffset = CGSize(width: 0, height: dy < 0 ? dy * 0.18 : dy)
                    }
                }
                .onEnded { value in
                    guard let onDismiss, !isDismissing else { return }
                    let dx = value.translation.width
                    let dy = value.translation.height
                    let predictedDx = value.predictedEndTranslation.width
                    let predictedDy = value.predictedEndTranslation.height

                    let shouldDismissDown = dy > 28 || predictedDy > 64
                    let shouldDismissHorizontal = abs(dx) > 64 || abs(predictedDx) > 120

                    if shouldDismissDown || shouldDismissHorizontal {
                        GokeHaptics.swipeCommit()
                        isDismissing = true
                        if simplifyMotion {
                            guard !hasRequestedDismiss else { return }
                            hasRequestedDismiss = true
                            onDismiss()
                        } else {
                            let exitOffset: CGSize = shouldDismissHorizontal && !shouldDismissDown
                                ? CGSize(width: (dx >= 0 ? 1 : -1) * 160, height: 0)
                                : CGSize(width: 0, height: 72)
                            withAnimation(GokeMotion.dismiss(reduceMotion: false)) {
                                dragOffset = exitOffset
                            } completion: {
                                guard !hasRequestedDismiss else { return }
                                hasRequestedDismiss = true
                                onDismiss()
                            }
                        }
                    } else {
                        withAnimation(GokeMotion.interactiveDrag(reduceMotion: simplifyMotion)) {
                            dragOffset = .zero
                        }
                    }
                },
            isEnabled: onDismiss != nil
        )
        .accessibilityElement(children: .contain)
    }
}

// MARK: - GokeEmptyState

/// Calm Mint Paper empty state (`56pt` `accentContainer` circle icon + short title + up to 2 tactile actions).
struct GokeEmptyState: View {
    private let systemImage: String
    private let title: String
    private let subtitle: String?
    private let primaryActionTitle: String?
    private let onPrimaryAction: (() -> Void)?
    private let secondaryActionTitle: String?
    private let onSecondaryAction: (() -> Void)?

    @Environment(\.gokeTheme) private var theme

    init(
        systemImage: String,
        title: String,
        subtitle: String? = nil,
        primaryActionTitle: String? = nil,
        onPrimaryAction: (() -> Void)? = nil,
        secondaryActionTitle: String? = nil,
        onSecondaryAction: (() -> Void)? = nil
    ) {
        self.systemImage = systemImage
        self.title = title
        self.subtitle = subtitle
        self.primaryActionTitle = primaryActionTitle
        self.onPrimaryAction = onPrimaryAction
        self.secondaryActionTitle = secondaryActionTitle
        self.onSecondaryAction = onSecondaryAction
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle()
                    .fill(theme.accentContainer)
                    .frame(width: 56, height: 56)
                Image(systemName: systemImage)
                    .font(.system(size: 26, weight: .medium))
                    .foregroundStyle(theme.onAccentContainer)
            }
            .accessibilityHidden(true)

            Text(title)
                .font(GokeTypography.titleMedium)
                .foregroundStyle(theme.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 12)

            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }

            if let primaryActionTitle, let onPrimaryAction {
                if let secondaryActionTitle, let onSecondaryAction {
                    Button(action: onPrimaryAction) {
                        Text(primaryActionTitle)
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.onAccent)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(theme.accent))
                            .frame(minHeight: 44)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.mintCompact)
                    .padding(.top, 12)

                    Button(action: onSecondaryAction) {
                        Text(secondaryActionTitle)
                            .font(GokeTypography.labelLarge.weight(.medium))
                            .foregroundStyle(theme.accent)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 44)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.mintCompact)
                    .padding(.top, 2)
                } else {
                    Button(action: onPrimaryAction) {
                        Text(primaryActionTitle)
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.onAccent)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(theme.accent))
                            .frame(minHeight: 44)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.mintCompact)
                    .padding(.top, 12)
                }
            } else if let secondaryActionTitle, let onSecondaryAction {
                Button(action: onSecondaryAction) {
                    Text(secondaryActionTitle)
                        .font(GokeTypography.labelLarge.weight(.medium))
                        .foregroundStyle(theme.accent)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .contentShape(Capsule())
                }
                .buttonStyle(.mintCompact)
                .padding(.top, 12)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28)
        .padding(.horizontal, GokeSpacing.lg)
    }
}

// MARK: - GokeBadge

/// Dynamic Type full-pill semantic tag/badge.
struct GokeBadge: View {
    enum Tone: Sendable, Equatable {
        case accent
        case secondary
        case tertiary
        case muted
        case error
        case success
        case course(Int)
        case custom(background: Color, foreground: Color)
    }

    private let text: String
    private let systemImage: String?
    private let tone: Tone

    @Environment(\.gokeTheme) private var theme

    init(
        _ text: String,
        systemImage: String? = nil,
        tone: Tone = .accent
    ) {
        self.text = text
        self.systemImage = systemImage
        self.tone = tone
    }

    var body: some View {
        let colors = resolvedColors
        HStack(spacing: GokeSpacing.xs) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.caption2.weight(.semibold))
            }
            Text(text)
                .font(.caption2.weight(.semibold))
                .monospacedDigit()
                .lineLimit(1)
        }
        .foregroundStyle(colors.foreground)
        .padding(.horizontal, 8)
        .padding(.vertical, 2.5)
        .background {
            Capsule().fill(colors.background)
        }
    }

    private var resolvedColors: (background: Color, foreground: Color) {
        switch tone {
        case .accent:
            return (theme.accentContainer, theme.onAccentContainer)
        case .secondary:
            return (theme.secondaryContainer, theme.onSecondaryContainer)
        case .tertiary:
            return (theme.tertiaryContainer, theme.onTertiaryContainer)
        case .muted:
            return (theme.surfaceHigh, theme.muted)
        case .error:
            return (theme.errorContainer, theme.onErrorContainer)
        case .success:
            return (theme.success.opacity(0.16), theme.success)
        case .course(let index):
            let pair = theme.courseColor(index: index)
            return (pair.background, pair.foreground)
        case .custom(let background, let foreground):
            return (background, foreground)
        }
    }
}

// MARK: - Completion & Checkbox Helpers

/// Calm paper-grid checkbox matching Android `MintCheck` (`Kit.kt:293-539`):
/// crisp `22pt` (customizable via `boxSize`) continuous-corner outline inside a full `44×44pt` hit target,
/// smooth interpolated stroke width + checkmark draw (`0.11s` critically damped), and crisp `180ms`
/// micro-pop + 6-dot emerald burst on check, `.mintCompact` tactile press, zero tap haptics.
struct GokeCheckBox: View {
    private let isChecked: Bool
    private let priority: Int
    private let listColor: Color?
    private let boxSize: CGFloat
    private let accessibilityLabel: String
    private let onToggle: () -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    @State private var checkProgress: Double
    @State private var burstProgress: Double = 0.0

    init(
        isChecked: Bool,
        priority: Int = 0,
        listColor: Color? = nil,
        boxSize: CGFloat = 22,
        accessibilityLabel: String = "切换完成状态",
        onToggle: @escaping () -> Void
    ) {
        self.isChecked = isChecked
        self.priority = priority
        self.listColor = listColor
        self.boxSize = boxSize
        self.accessibilityLabel = accessibilityLabel
        self.onToggle = onToggle
        self._checkProgress = State(initialValue: isChecked ? 1.0 : 0.0)
    }

    private var uncheckedStrokeWidth: CGFloat {
        switch priority {
        case 3...: return 3.5
        case 2: return 2.75
        default: return 2.0
        }
    }

    var body: some View {
        let ringColor = theme.priorityColor(priority) ?? listColor ?? theme.outline
        let simplifyMotion = reduceMotion || performance.shouldSimplifyMotion
        Button(action: onToggle) {
            MintCheckVisual(
                checkProgress: checkProgress,
                burstProgress: burstProgress,
                boxSize: boxSize,
                uncheckedStrokeWidth: uncheckedStrokeWidth,
                checkedStrokeWidth: 2.0,
                strokeColor: ringColor,
                checkColor: theme.muted,
                burstColor: theme.added
            )
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .onChange(of: isChecked) { oldValue, newValue in
            if simplifyMotion {
                checkProgress = newValue ? 1.0 : 0.0
                burstProgress = 0.0
            } else {
                withAnimation(GokeMotion.pressSpring) {
                    checkProgress = newValue ? 1.0 : 0.0
                }
                if !oldValue && newValue {
                    var resetTx = Transaction()
                    resetTx.disablesAnimations = true
                    withTransaction(resetTx) {
                        burstProgress = 0.0
                    }
                    withAnimation(.easeOut(duration: 0.18)) {
                        burstProgress = 1.0
                    } completion: {
                        var clearTx = Transaction()
                        clearTx.disablesAnimations = true
                        withTransaction(clearTx) {
                            if burstProgress >= 0.999 {
                                burstProgress = 0.0
                            }
                        }
                    }
                } else if !newValue {
                    var resetTx = Transaction()
                    resetTx.disablesAnimations = true
                    withTransaction(resetTx) {
                        burstProgress = 0.0
                    }
                }
            }
        }
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(isChecked ? [.isButton, .isSelected] : .isButton)
    }
}

private struct MintCheckVisual: View, @MainActor Animatable {
    var checkProgress: Double
    var burstProgress: Double
    let boxSize: CGFloat
    let uncheckedStrokeWidth: CGFloat
    let checkedStrokeWidth: CGFloat
    let strokeColor: Color
    let checkColor: Color
    let burstColor: Color

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(checkProgress, burstProgress) }
        set {
            checkProgress = newValue.first
            burstProgress = newValue.second
        }
    }

    var body: some View {
        Canvas { context, size in
            let t = min(max(checkProgress, 0.0), 1.0)
            let b = min(max(burstProgress, 0.0), 1.0)
            let cx = size.width / 2
            let cy = size.height / 2
            let currentStrokeWidth = uncheckedStrokeWidth + (checkedStrokeWidth - uncheckedStrokeWidth) * CGFloat(t)
            let halfStroke = currentStrokeWidth / 2
            let rect = CGRect(
                x: cx - boxSize / 2 + halfStroke,
                y: cy - boxSize / 2 + halfStroke,
                width: boxSize - currentStrokeWidth,
                height: boxSize - currentStrokeWidth
            )
            let currentStrokeColor: Color = {
                if t <= 0.001 { return strokeColor }
                if t >= 0.999 { return checkColor }
                return CourseProgressStyle.lerp(strokeColor, checkColor, fraction: t)
            }()
            // Crisp early micro-pop (~115ms) while burst dots dissipate cleanly by 180ms (zero sluggish bloat).
            let popScale: CGFloat = (b > 0.001 && b < 0.65)
                ? CGFloat(1.0 + 0.08 * sin((b / 0.65) * .pi))
                : 1.0

            context.drawLayer { layer in
                if popScale != 1.0 {
                    layer.translateBy(x: cx, y: cy)
                    layer.scaleBy(x: popScale, y: popScale)
                    layer.translateBy(x: -cx, y: -cy)
                }

                let roundedRect = Path(roundedRect: rect, cornerRadius: GokeRadius.xs, style: .continuous)
                layer.stroke(roundedRect, with: .color(currentStrokeColor), lineWidth: currentStrokeWidth)

                if t > 0.001 {
                    let boxLeft = cx - boxSize / 2
                    let boxTop = cy - boxSize / 2
                    var checkPath = Path()
                    checkPath.move(to: CGPoint(x: boxLeft + boxSize * 0.28, y: boxTop + boxSize * 0.52))
                    checkPath.addLine(to: CGPoint(x: boxLeft + boxSize * 0.44, y: boxTop + boxSize * 0.70))
                    checkPath.addLine(to: CGPoint(x: boxLeft + boxSize * 0.74, y: boxTop + boxSize * 0.34))
                    let trimmed = t >= 0.999 ? checkPath : checkPath.trimmedPath(from: 0, to: t)

                    layer.stroke(
                        trimmed,
                        with: .color(t >= 0.999 ? checkColor : checkColor.opacity(t)),
                        style: StrokeStyle(lineWidth: 2.0, lineCap: .round, lineJoin: .round)
                    )
                }
            }

            if b > 0.001 && b < 0.999 {
                let dotAlpha = (1.0 - b) * 0.85
                let radius = boxSize * CGFloat(0.56 + 0.42 * b)
                let dotRadius = CGFloat(2.0 * (1.0 - b * 0.72))
                let dotColor = burstColor.opacity(dotAlpha)
                for i in 0..<6 {
                    let angle = (Double(i) * 60.0 - 30.0) * .pi / 180.0
                    let dx = CGFloat(cos(angle)) * radius
                    let dy = CGFloat(sin(angle)) * radius
                    let dotRect = CGRect(
                        x: cx + dx - dotRadius,
                        y: cy + dy - dotRadius,
                        width: dotRadius * 2,
                        height: dotRadius * 2
                    )
                    context.fill(Path(ellipseIn: dotRect), with: .color(dotColor))
                }
            }
        }
    }
}

// MARK: - GokeCopyButton

/// Reusable copy-to-clipboard button with a 1.4s `doc.on.doc` -> `checkmark` symbol morph.
///
/// **Design Rule**: Zero tap haptics. Respects `accessibilityReduceMotion` and `GokePerformancePolicy`.
struct GokeCopyButton: View {
    private let textToCopy: String
    private let title: String?
    private let copiedTitle: String?
    private let accessibilityLabel: String
    private let onCopied: (() -> Void)?

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    @State private var isCopied: Bool = false
    @State private var resetTask: Task<Void, Never>?

    init(
        text: String,
        title: String? = nil,
        copiedTitle: String? = "已复制",
        accessibilityLabel: String = "复制",
        onCopied: (() -> Void)? = nil
    ) {
        self.textToCopy = text
        self.title = title
        self.copiedTitle = copiedTitle
        self.accessibilityLabel = accessibilityLabel
        self.onCopied = onCopied
    }

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    var body: some View {
        Button(action: performCopy) {
            HStack(spacing: GokeSpacing.xs) {
                Image(systemName: isCopied ? "checkmark" : "doc.on.doc")
                    .font(.subheadline.weight(.medium))
                    .contentTransition(simplifyMotion ? .identity : .symbolEffect(.replace))

                if let title {
                    Text(isCopied ? (copiedTitle ?? title) : title)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
            }
            .foregroundStyle(isCopied ? theme.success : theme.accent)
            .frame(minWidth: title == nil ? 44 : nil, minHeight: 44)
            .contentShape(Rectangle())
            .animation(GokeMotion.press(reduceMotion: simplifyMotion), value: isCopied)
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel(isCopied ? (copiedTitle ?? "已复制") : accessibilityLabel)
        .onDisappear {
            resetTask?.cancel()
            resetTask = nil
        }
    }

    private func performCopy() {
        #if canImport(UIKit)
        UIPasteboard.general.string = textToCopy
        #endif
        onCopied?()

        resetTask?.cancel()
        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
            isCopied = true
        }
        resetTask = Task {
            try? await Task.sleep(for: .seconds(1.4))
            guard !Task.isCancelled else { return }
            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                isCopied = false
            }
        }
    }
}

// MARK: - GokeMultiReminderPicker

/// Reusable multi-offset reminder selector supporting `[Int]` minute offsets.
///
/// Designed for native iOS `.insetGrouped` forms (`CourseEditView`, `EventEditView`,
/// `TodoEditView`, `QuickAddSheet`) with calm removable chips and a native toggle `Menu`.
/// Zero tap haptics; respects `accessibilityReduceMotion`.
struct GokeMultiReminderPicker: View {
    /// Standard presets for events and tasks (in minutes before start).
    static let defaultPresets: [Int] = [0, 5, 10, 15, 30, 60, 1440]
    /// Presets tailored for timetable courses (in minutes before class).
    static let coursePresets: [Int] = [5, 10, 15, 30, 60]

    private let title: String
    private let emptyLabel: String
    private let prefix: String
    private let presets: [Int]
    @Binding private var selection: [Int]

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    init(
        _ title: String = "提醒",
        selection: Binding<[Int]>,
        presets: [Int] = GokeMultiReminderPicker.defaultPresets,
        emptyLabel: String = "不提醒",
        prefix: String = "提前"
    ) {
        self.title = title
        self._selection = selection
        self.presets = presets
        self.emptyLabel = emptyLabel
        self.prefix = prefix
    }

    /// Formats a single minute offset (e.g. `0` -> `"准时"`, `15` -> `"提前 15 分钟"`, `1440` -> `"提前 1 天"`).
    static func formatOffset(_ minutes: Int, prefix: String = "提前") -> String {
        if minutes <= 0 {
            return "准时"
        }
        if minutes < 60 {
            return "\(prefix) \(minutes) 分钟"
        }
        if minutes % 1440 == 0 {
            return "\(prefix) \(minutes / 1440) 天"
        }
        if minutes % 60 == 0 {
            return "\(prefix) \(minutes / 60) 小时"
        }
        let hours = minutes / 60
        let mins = minutes % 60
        return "\(prefix) \(hours) 小时 \(mins) 分钟"
    }

    /// Formats a normalized list of minute offsets into a concise human-readable summary.
    static func formatSummary(
        _ offsets: [Int],
        emptyLabel: String = "不提醒",
        prefix: String = "提前"
    ) -> String {
        let normalized = Array(Set(offsets.filter { $0 >= 0 })).sorted()
        guard !normalized.isEmpty else { return emptyLabel }
        return normalized.map { formatOffset($0, prefix: prefix) }.joined(separator: "、")
    }

    private var normalizedSelection: [Int] {
        Array(Set(selection.filter { $0 >= 0 })).sorted()
    }

    private var allMenuOptions: [Int] {
        Array(Set(presets + normalizedSelection)).sorted()
    }

    var body: some View {
        let active = normalizedSelection
        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            HStack(spacing: GokeSpacing.sm) {
                Text(title)
                    .foregroundStyle(theme.ink)

                Spacer(minLength: GokeSpacing.sm)

                Menu {
                    ForEach(allMenuOptions, id: \.self) { offset in
                        let isSelected = active.contains(offset)
                        Button {
                            toggle(offset)
                        } label: {
                            if isSelected {
                                Label(Self.formatOffset(offset, prefix: prefix), systemImage: "checkmark")
                            } else {
                                Text(Self.formatOffset(offset, prefix: prefix))
                            }
                        }
                    }

                    if !active.isEmpty {
                        Divider()
                        Button(role: .destructive) {
                            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                selection = []
                            }
                        } label: {
                            Label("清除全部", systemImage: "xmark.circle")
                        }
                    }
                } label: {
                    HStack(spacing: GokeSpacing.xs) {
                        Text(active.isEmpty ? emptyLabel : "已选 \(active.count) 项")
                            .font(.subheadline)
                            .foregroundStyle(active.isEmpty ? theme.muted : theme.accent)
                            .monospacedDigit()
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(theme.muted)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }

            if !active.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: GokeSpacing.xs) {
                        ForEach(active, id: \.self) { offset in
                            Button {
                                toggle(offset)
                            } label: {
                                HStack(spacing: 4) {
                                    Text(Self.formatOffset(offset, prefix: prefix))
                                        .font(.caption.weight(.medium))
                                        .monospacedDigit()
                                    Image(systemName: "xmark")
                                        .font(.system(size: 9, weight: .bold))
                                        .opacity(0.75)
                                }
                                .foregroundStyle(theme.onAccentContainer)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(theme.accentContainer, in: Capsule())
                                .frame(minHeight: 36)
                                .contentShape(Capsule())
                            }
                            .buttonStyle(.mintCompact)
                            .accessibilityLabel("移除\(Self.formatOffset(offset, prefix: prefix))提醒")
                        }
                    }
                    .padding(.vertical, 2)
                }
                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func toggle(_ offset: Int) {
        var next = Set(selection.filter { $0 >= 0 })
        if next.contains(offset) {
            next.remove(offset)
        } else {
            next.insert(offset)
        }
        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
            selection = next.sorted()
        }
    }
}

extension View {
    /// Applies Goke's "Gray-Out & Strikethrough Completion Rule" (`0.45` opacity when done).
    func gokeCompletedOpacity(_ isCompleted: Bool) -> some View {
        opacity(isCompleted ? 0.45 : 1.0)
    }
}

// MARK: - Brand Mark, Version & Settings Icon Badge

/// Crisp vector reproduction of Goke's dual-slash brand mark (`server/internal/server/web/app/icon.svg`).
struct GokeBrandMark: View {
    private let size: CGFloat

    init(size: CGFloat = 64) {
        self.size = size
    }

    var body: some View {
        let cornerRadius = size * (112.0 / 512.0)
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color(hex: 0x317D71), Color(hex: 0x1E554D)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            GokeBrandLeftBlade()
                .fill(Color(hex: 0xF2F7F5))

            GokeBrandRightBlade()
                .fill(Color(hex: 0x8AE0CB))
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

private struct GokeBrandLeftBlade: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 512.0
        let ox = rect.minX + (rect.width - 512.0 * s) * 0.5
        let oy = rect.minY + (rect.height - 512.0 * s) * 0.5
        let p: (CGFloat, CGFloat) -> CGPoint = { x, y in
            CGPoint(x: ox + x * s, y: oy + y * s)
        }

        var path = Path()
        path.move(to: p(130, 260))
        path.addArc(tangent1End: p(130, 130), tangent2End: p(281.17, 130), radius: 54 * s)
        path.addArc(tangent1End: p(281.17, 130), tangent2End: p(199.29, 382), radius: 16 * s)
        path.addArc(tangent1End: p(199.29, 382), tangent2End: p(130, 382), radius: 16 * s)
        path.addArc(tangent1End: p(130, 382), tangent2End: p(130, 260), radius: 31.18 * s)
        path.closeSubpath()
        return path
    }
}

private struct GokeBrandRightBlade: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 512.0
        let ox = rect.minX + (rect.width - 512.0 * s) * 0.5
        let oy = rect.minY + (rect.height - 512.0 * s) * 0.5
        let p: (CGFloat, CGFloat) -> CGPoint = { x, y in
            CGPoint(x: ox + x * s, y: oy + y * s)
        }

        var path = Path()
        path.move(to: p(382, 256))
        path.addArc(tangent1End: p(382, 382), tangent2End: p(230.83, 382), radius: 54 * s)
        path.addArc(tangent1End: p(230.83, 382), tangent2End: p(312.71, 130), radius: 16 * s)
        path.addArc(tangent1End: p(312.71, 130), tangent2End: p(382, 130), radius: 16 * s)
        path.addArc(tangent1End: p(382, 130), tangent2End: p(382, 256), radius: 31.18 * s)
        path.closeSubpath()
        return path
    }
}

/// Centralized bundle version reader aligned with `scripts/sync_ios_version.py` Info.plist keys.
enum GokeAppVersion: Sendable {
    /// Clean full SemVer display string (`GokeDisplayVersion` if non-empty, else `CFBundleShortVersionString`, else `"0.0.1"`).
    static var displayVersion: String {
        if let custom = nonEmptyInfoValue(forKey: "GokeDisplayVersion") {
            return custom
        }
        if let short = nonEmptyInfoValue(forKey: "CFBundleShortVersionString") {
            return short
        }
        return "0.0.1"
    }

    /// Monotonic bundle build number (`CFBundleVersion`, else `"1"`).
    static var buildNumber: String {
        nonEmptyInfoValue(forKey: "CFBundleVersion") ?? "1"
    }

    /// Short version label suitable for compact badges (e.g. `"v0.0.1-beta.9"`).
    static var shortLabel: String {
        "v\(displayVersion)"
    }

    /// Full version label suitable for About / diagnostics (e.g. `"v0.0.1-beta.9 (12099)"`).
    static var fullLabel: String {
        "v\(displayVersion) (\(buildNumber))"
    }

    private static func nonEmptyInfoValue(forKey key: String) -> String? {
        guard let raw = Bundle.main.infoDictionary?[key] as? String else {
            return nil
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// Native Apple Settings-style `29×29pt` continuous rounded-square icon tile (`cornerRadius: 7`).
struct GokeSettingsIconBadge: View {
    private let systemImage: String
    private let color: Color

    init(systemImage: String, color: Color) {
        self.systemImage = systemImage
        self.color = color
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 7, style: .continuous)
        Image(systemName: systemImage)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 29, height: 29)
            .background {
                shape.fill(
                    LinearGradient(
                        colors: [color.opacity(0.88), color],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay {
                    shape.fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.16), Color.clear],
                            startPoint: .top,
                            endPoint: .center
                        )
                    )
                }
            }
            .accessibilityHidden(true)
    }
}

/// Centered Apple-style feature header for campus & setup screens inside `.insetGrouped` lists.
struct GokeFeatureHeader: View {
    private let systemImage: String
    private let color: Color
    private let title: String
    private let subtitle: String

    @Environment(\.gokeTheme) private var theme

    init(
        systemImage: String,
        color: Color,
        title: String,
        subtitle: String
    ) {
        self.systemImage = systemImage
        self.color = color
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(spacing: GokeSpacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background {
                    shape.fill(
                        LinearGradient(
                            colors: [color.opacity(0.88), color],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .overlay {
                        shape.fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.16), Color.clear],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )
                    }
                }
                .accessibilityHidden(true)

            VStack(spacing: GokeSpacing.xs) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(theme.ink)

                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, GokeSpacing.sm)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }
}

/// Native Apple prominent full-width form submit button (`.borderedProminent`, `.large`, `12pt` radius).
struct GokePrimaryActionButton: View {
    private let title: String
    private let systemImage: String?
    private let isLoading: Bool
    private let isDisabled: Bool
    private let tint: Color?
    private let action: () -> Void

    @Environment(\.gokeTheme) private var theme

    init(
        _ title: String,
        systemImage: String? = nil,
        isLoading: Bool = false,
        isDisabled: Bool = false,
        tint: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.tint = tint
        self.action = action
    }

    var body: some View {
        let fillColor = tint ?? theme.accent
        let labelColor: Color = tint == nil ? theme.onAccent : .white
        let effectiveDisabled = isDisabled || isLoading
        Button(action: action) {
            HStack(spacing: GokeSpacing.sm) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(labelColor)
                } else if let systemImage {
                    Image(systemName: systemImage)
                        .font(.headline)
                }
                Text(title)
                    .font(.headline)
            }
            .foregroundStyle(labelColor)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                    .fill(effectiveDisabled ? fillColor.opacity(0.45) : fillColor)
            )
        }
        .buttonStyle(.mintCompact)
        .disabled(effectiveDisabled)
    }
}

// MARK: - iOS 17 Compatible Spinning Symbol Modifier

/// Applies continuous SF Symbol rotation on iOS 18+, graceful `.pulse` feedback on iOS 17,
/// and remains static when `accessibilityReduceMotion` or degraded `GokePerformancePolicy` is active.
struct GokeSpinningSymbolModifier: ViewModifier {
    private let isActive: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    init(isActive: Bool) {
        self.isActive = isActive
    }

    private var shouldAnimate: Bool {
        isActive && !reduceMotion && !performance.shouldSimplifyMotion
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.symbolEffect(.rotate, isActive: shouldAnimate)
        } else {
            content.symbolEffect(.pulse, isActive: shouldAnimate)
        }
    }
}

extension View {
    /// iOS 17-safe spinning SF Symbol effect (`.rotate` on iOS 18+, `.pulse` on iOS 17, static when degraded).
    func gokeSpinningSymbol(isActive: Bool) -> some View {
        modifier(GokeSpinningSymbolModifier(isActive: isActive))
    }
}

// MARK: - GokeSegmentedPill

/// Sliding capsule segmented picker (2–4 equal-width options) matching Android `FormKit.SegmentedPill`.
/// Uses `matchedGeometryEffect` with zero-overshoot `GokeMotion.press` spring (`0.11s`, `4pt` track inset)
/// and zero tap haptics. Supports optional per-option `activeTint` (e.g. semantic Priority colors).
struct GokeSegmentedPill<Value: Hashable & Sendable>: View {
    struct Option: Identifiable, Sendable {
        let value: Value
        let label: String
        let activeTint: Color?
        var id: Value { value }

        init(_ value: Value, _ label: String, activeTint: Color? = nil) {
            self.value = value
            self.label = label
            self.activeTint = activeTint
        }
    }

    private let options: [Option]
    @Binding private var selection: Value
    private let height: CGFloat
    private let onSelect: ((Value) -> Void)?

    @Namespace private var pillNamespace
    @Environment(\.gokeTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    init(
        options: [Option],
        selection: Binding<Value>,
        height: CGFloat = 40,
        onSelect: ((Value) -> Void)? = nil
    ) {
        self.options = options
        self._selection = selection
        self.height = height
        self.onSelect = onSelect
    }

    init(
        options: [(Value, String)],
        selection: Binding<Value>,
        height: CGFloat = 40,
        onSelect: ((Value) -> Void)? = nil
    ) {
        self.options = options.map { Option($0.0, $0.1) }
        self._selection = selection
        self.height = height
        self.onSelect = onSelect
    }

    private var trackColor: Color {
        colorScheme == .dark ? theme.surfaceHigh : theme.surfaceHighest
    }

    private var thumbColor: Color {
        colorScheme == .dark
            ? CourseProgressStyle.lerp(theme.surfaceHigh, theme.ink, fraction: 0.16)
            : theme.surface
    }

    var body: some View {
        let simplifyMotion = reduceMotion || performance.shouldSimplifyMotion
        HStack(spacing: 0) {
            ForEach(options) { option in
                let isSelected = option.value == selection
                let resolvedActiveTint = option.activeTint ?? theme.accent
                Button {
                    if selection != option.value {
                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                            selection = option.value
                        }
                    } else {
                        // Allow re-tapping the active option (e.g. confirming "无" in QuickAdd priority)
                        // without wrapping in a competing thumb-slide animation.
                        selection = option.value
                    }
                    onSelect?(option.value)
                } label: {
                    Text(option.label)
                        .font(GokeTypography.labelLarge.weight(isSelected ? .semibold : .medium))
                        .foregroundStyle(isSelected ? resolvedActiveTint : theme.muted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(Capsule())
                }
                .buttonStyle(MintPressableButtonStyle(scale: isSelected ? 0.98 : 0.96))
                .background {
                    if isSelected {
                        Capsule()
                            .fill(thumbColor)
                            .matchedGeometryEffect(id: "goke-segmented-thumb", in: pillNamespace)
                    }
                }
                .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(4)
        .frame(height: height)
        .background(Capsule().fill(trackColor))
    }
}

// MARK: - GokeReturnPill

/// Unified crisp return-to-anchor pill (`"回今天"` / `"回本周"`) for Today, Calendar, and Timetable headers.
///
/// Renders a >=32pt visual capsule (`theme.accentContainer` + `theme.onAccentContainer`) inside a
/// HIG-compliant >=44pt vertical hit target with `.mintCompact` tactile press and zero tap haptics.
struct GokeReturnPill: View {
    private let title: String
    private let systemImage: String?
    private let accessibilityLabel: String?
    private let action: () -> Void

    @Environment(\.gokeTheme) private var theme

    init(
        _ title: String = "回今天",
        systemImage: String? = nil,
        accessibilityLabel: String? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.accessibilityLabel = accessibilityLabel
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 12, weight: .semibold))
                }
                Text(title)
                    .font(GokeTypography.labelMedium.weight(.semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(theme.onAccentContainer)
            .padding(.horizontal, GokeSpacing.md)
            .frame(minHeight: 32)
            .background(Capsule().fill(theme.accentContainer))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel(accessibilityLabel ?? title)
    }
}

// MARK: - GokeShrinkWrappingScrollView

/// Clamps vertical size proposals to `maxHeight` synchronously so `ViewThatFits(in: .vertical)`
/// resolves fit-vs-scroll on Frame 0 even inside shrink-wrapping `VStack` containers (`height == nil`).
private struct MaxHeightProposalLayout: Layout {
    let maxHeight: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let cappedHeight = min(proposal.height ?? maxHeight, maxHeight)
        let cappedProposal = ProposedViewSize(width: proposal.width, height: cappedHeight)
        guard let subview = subviews.first else {
            return CGSize(width: proposal.width ?? 0, height: 0)
        }
        let size = subview.sizeThatFits(cappedProposal)
        return CGSize(width: size.width, height: min(size.height, cappedHeight))
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        guard let subview = subviews.first else { return }
        let cappedProposal = ProposedViewSize(width: bounds.width, height: bounds.height)
        subview.place(at: bounds.origin, anchor: .topLeading, proposal: cappedProposal)
    }
}

/// Vertical container that shrink-wraps compact content directly (no `ScrollView` touch delay
/// and zero Frame-0 height jump) and switches to a `ScrollView` only when content exceeds `maxHeight`.
struct GokeShrinkWrappingScrollView<Content: View>: View {
    private let maxHeight: CGFloat
    private let showsIndicators: Bool
    private let content: Content

    init(
        maxHeight: CGFloat,
        showsIndicators: Bool = false,
        @ViewBuilder content: () -> Content
    ) {
        self.maxHeight = maxHeight
        self.showsIndicators = showsIndicators
        self.content = content()
    }

    var body: some View {
        MaxHeightProposalLayout(maxHeight: maxHeight) {
            ViewThatFits(in: .vertical) {
                content
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                ScrollView(.vertical, showsIndicators: showsIndicators) {
                    content
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

// MARK: - GokeConflictCard

/// Unified time-conflict alert card matching Android `Kit.ConflictCard` (`16pt` radius, `conflictBg` fill,
/// warning icon + bold `removed` title + caller-supplied conflict rows).
struct GokeConflictCard<Content: View>: View {
    private let title: String
    private let content: Content

    @Environment(\.gokeTheme) private var theme

    init(
        title: String,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.removed)
                Text(title)
                    .font(GokeTypography.labelLarge.weight(.bold))
                    .foregroundStyle(theme.removed)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.conflictBg)
        )
    }
}

// MARK: - GokeOverlaySheetContainer (Keyboard-Docked Overlay, Android MintSheetOverlay Parity)

/// Callable dismiss action injected into views hosted by `GokeOverlaySheetContainer`.
///
/// Invoking `gokeDismissOverlay?()` commits an immediate clean dismiss (bypassing interactive
/// draft-discard guards) while keeping content rendered through the fast exit animation.
@MainActor
struct GokeOverlayDismissAction: Sendable {
    private let handler: @MainActor () -> Void

    init(_ handler: @escaping @MainActor () -> Void) {
        self.handler = handler
    }

    func callAsFunction() {
        handler()
    }
}

/// Bridge allowing hosted overlay content (e.g., `QuickAddSheet` with unsaved draft) to intercept
/// scrim-tap or swipe-down dismiss attempts and show a confirmation dialog instead of losing data.
@MainActor
final class GokeOverlayDismissBridge: Sendable {
    var onAttemptDismiss: (@MainActor () -> Bool)?
}

private struct GokeDismissOverlayKey: EnvironmentKey {
    static let defaultValue: GokeOverlayDismissAction? = nil
}

private struct GokeOverlayDismissBridgeKey: EnvironmentKey {
    static let defaultValue: GokeOverlayDismissBridge? = nil
}

extension EnvironmentValues {
    /// Dismisses the enclosing `GokeOverlaySheetContainer` when presented as an in-window overlay.
    var gokeDismissOverlay: GokeOverlayDismissAction? {
        get { self[GokeDismissOverlayKey.self] }
        set { self[GokeDismissOverlayKey.self] = newValue }
    }

    var gokeOverlayDismissBridge: GokeOverlayDismissBridge? {
        get { self[GokeOverlayDismissBridgeKey.self] }
        set { self[GokeOverlayDismissBridgeKey.self] = newValue }
    }
}

private struct GokeOverlayInterceptDismissModifier: ViewModifier {
    let shouldAllowDismiss: @MainActor () -> Bool
    @Environment(\.gokeOverlayDismissBridge) private var bridge

    func body(content: Content) -> some View {
        content
            .onAppear {
                bridge?.onAttemptDismiss = shouldAllowDismiss
            }
            .onDisappear {
                bridge?.onAttemptDismiss = nil
            }
    }
}

extension View {
    /// Registers an interactive dismiss guard on the enclosing `GokeOverlaySheetContainer`.
    /// Return `false` to block scrim-tap / swipe-down dismissal (e.g. to prompt discard confirmation).
    func gokeOverlayInterceptDismiss(
        _ shouldAllowDismiss: @escaping @MainActor () -> Bool
    ) -> some View {
        modifier(GokeOverlayInterceptDismissModifier(shouldAllowDismiss: shouldAllowDismiss))
    }
}

/// Top-only rounded hairline path (top-left arc -> top edge -> top-right arc) with zero vertical side strokes.
private struct TopRoundedHairlineShape: Shape {
    let cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        let r = min(cornerRadius, rect.width / 2, rect.height)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + r))
        path.addArc(
            tangent1End: CGPoint(x: rect.minX, y: rect.minY),
            tangent2End: CGPoint(x: rect.minX + r, y: rect.minY),
            radius: r
        )
        path.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        path.addArc(
            tangent1End: CGPoint(x: rect.maxX, y: rect.minY),
            tangent2End: CGPoint(x: rect.maxX, y: rect.minY + r),
            radius: r
        )
        return path
    }
}

private struct OverlaySheetHeightPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// Reusable keyboard-docked bottom overlay matching Android `MintSheetOverlay` (`FormKit.kt:934-1060`):
/// - Surface defaults to `theme.surface` (`surfaceContainerLow`: `#FFFFFF` light / `#1E2624` dark), zero shadow,
///   clean top-only `0.5pt` hairline (`outlineVariant @ 0.45`, zero side strokes), and unclipped bottom safe-area fill.
/// - Retains the last presented `Item` throughout the fast asymmetric `0.16s` zero-bounce exit so content never
///   vanishes on Frame 0, and slides smoothly off-screen from the user's current `dragOffsetY` without upward snap.
/// - Critically damped `0.22s` zero-bounce entrance (zero bottom lift) that tracks UIKit keyboard safe-area smoothly.
/// - Drag-to-dismiss is scoped strictly to a dedicated top grabber zone so taps on the sheet's
///   input field, horizontal toolbar, and inline panels never compete with a container-wide drag gesture.
struct GokeOverlaySheetContainer<Item: Equatable, Content: View>: View {
    private let item: Item?
    private let scrimAlpha: Double
    private let cornerRadius: CGFloat
    private let containerColor: Color?
    private let canDragDismiss: Bool
    private let onRequestDismiss: (() -> Bool)?
    private let onDismiss: () -> Void
    private let content: (Item) -> Content

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    @State private var renderedItem: Item?
    @State private var isShowingSheet: Bool = false
    @State private var dragOffsetY: CGFloat = 0
    @State private var measuredSheetHeight: CGFloat = 220
    @State private var isDismissingInternally: Bool = false
    @State private var presentTask: Task<Void, Never>?
    @State private var dismissBridge = GokeOverlayDismissBridge()

    init(
        item: Item?,
        scrimAlpha: Double = 0.22,
        cornerRadius: CGFloat = GokeRadius.lg,
        containerColor: Color? = nil,
        canDragDismiss: Bool = true,
        onRequestDismiss: (() -> Bool)? = nil,
        onDismiss: @escaping () -> Void,
        @ViewBuilder content: @escaping (Item) -> Content
    ) {
        self.item = item
        self.scrimAlpha = scrimAlpha
        self.cornerRadius = cornerRadius
        self.containerColor = containerColor
        self.canDragDismiss = canDragDismiss
        self.onRequestDismiss = onRequestDismiss
        self.onDismiss = onDismiss
        self.content = content
    }

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    private var dragFadeFactor: Double {
        let down = max(0.0, Double(dragOffsetY))
        return max(0.2, 1.0 - down / 240.0)
    }

    private var sheetVerticalOffset: CGFloat {
        if isShowingSheet {
            return dragOffsetY
        }
        if simplifyMotion {
            return 0
        }
        return max(measuredSheetHeight, 220) + 64 + max(0, dragOffsetY)
    }

    private var dragDismissGesture: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                guard canDragDismiss, isShowingSheet else { return }
                let dy = value.translation.height
                if dy >= 0 {
                    dragOffsetY = dy
                } else {
                    dragOffsetY = max(-16, dy * 0.16)
                }
            }
            .onEnded { value in
                guard canDragDismiss, isShowingSheet else { return }
                let shouldDismiss = value.translation.height > 72
                    || value.predictedEndTranslation.height > 140
                if shouldDismiss {
                    attemptInteractiveDismiss()
                } else {
                    withAnimation(GokeMotion.interactiveDrag(reduceMotion: simplifyMotion)) {
                        dragOffsetY = 0
                    }
                }
            }
    }

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: cornerRadius,
            bottomLeadingRadius: 0,
            bottomTrailingRadius: 0,
            topTrailingRadius: cornerRadius,
            style: .continuous
        )
        let resolvedSurface = containerColor ?? theme.surface

        ZStack(alignment: .bottom) {
            if let activeItem = renderedItem {
                Color.black
                    .opacity(isShowingSheet ? scrimAlpha * dragFadeFactor : 0)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        attemptInteractiveDismiss()
                    }
                    .allowsHitTesting(isShowingSheet)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("关闭浮层")

                VStack(spacing: 0) {
                    if canDragDismiss {
                        Color.clear
                            .frame(height: 20)
                            .allowsHitTesting(false)
                    }
                    content(activeItem)
                        .environment(\.gokeDismissOverlay, GokeOverlayDismissAction {
                            dismissCleanly()
                        })
                        .environment(\.gokeOverlayDismissBridge, dismissBridge)
                }
                .frame(maxWidth: .infinity)
                .overlay(alignment: .top) {
                    if canDragDismiss {
                        VStack(spacing: 0) {
                            Capsule()
                                .fill(theme.outlineVariant.opacity(0.65))
                                .frame(width: 36, height: 4)
                                .padding(.top, 8)
                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                        .contentShape(Rectangle())
                        .gesture(dragDismissGesture, isEnabled: isShowingSheet)
                        .accessibilityLabel("向下拖动关闭")
                    }
                }
                .clipShape(shape)
                .background {
                    shape
                        .fill(resolvedSurface)
                        .padding(.bottom, dragOffsetY < 0 ? dragOffsetY : 0)
                        .ignoresSafeArea(.container, edges: .bottom)
                }
                .overlay {
                    TopRoundedHairlineShape(cornerRadius: cornerRadius)
                        .stroke(theme.outlineVariant.opacity(0.45), lineWidth: 0.5)
                        .allowsHitTesting(false)
                }
                .background {
                    GeometryReader { proxy in
                        Color.clear
                            .preference(key: OverlaySheetHeightPreferenceKey.self, value: proxy.size.height)
                    }
                }
                .onPreferenceChange(OverlaySheetHeightPreferenceKey.self) { height in
                    if height > 0, abs(height - measuredSheetHeight) > 0.5 {
                        measuredSheetHeight = height
                    }
                }
                .offset(y: sheetVerticalOffset)
                .opacity(simplifyMotion ? (isShowingSheet ? 1 : 0) : 1)
                .allowsHitTesting(isShowingSheet)
            }
        }
        .onAppear {
            if let initialItem = item {
                present(initialItem)
            }
        }
        .onChange(of: item) { _, newValue in
            if let newValue {
                present(newValue)
            } else if !isDismissingInternally {
                runExternalDismiss()
            }
        }
        .onDisappear {
            presentTask?.cancel()
            presentTask = nil
        }
    }

    private func present(_ newItem: Item) {
        presentTask?.cancel()
        presentTask = nil
        isDismissingInternally = false

        let wasHidden = renderedItem == nil || !isShowingSheet
        renderedItem = newItem
        if wasHidden {
            dragOffsetY = 0
        }

        if simplifyMotion {
            isShowingSheet = true
            return
        }

        guard wasHidden else { return }
        presentTask = Task { @MainActor in
            guard !Task.isCancelled, item != nil else { return }
            withAnimation(GokeMotion.sheetEnter(reduceMotion: false)) {
                isShowingSheet = true
            }
        }
    }

    private func attemptInteractiveDismiss() {
        if let onRequestDismiss, !onRequestDismiss() {
            snapBackAfterBlockedDismiss()
            return
        }
        if let intercept = dismissBridge.onAttemptDismiss, !intercept() {
            snapBackAfterBlockedDismiss()
            return
        }
        dismissCleanly()
    }

    private func snapBackAfterBlockedDismiss() {
        guard dragOffsetY != 0 else { return }
        withAnimation(GokeMotion.interactiveDrag(reduceMotion: simplifyMotion)) {
            dragOffsetY = 0
        }
    }

    private func resignActiveKeyboard() {
        #if canImport(UIKit)
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
        #endif
    }

    private func dismissCleanly() {
        presentTask?.cancel()
        presentTask = nil
        isDismissingInternally = true
        resignActiveKeyboard()

        if simplifyMotion {
            isShowingSheet = false
            renderedItem = nil
            dragOffsetY = 0
            isDismissingInternally = false
            onDismiss()
            return
        }

        withAnimation(GokeMotion.sheetExit(reduceMotion: false)) {
            isShowingSheet = false
        } completion: {
            if !isShowingSheet && item == nil {
                renderedItem = nil
                dragOffsetY = 0
                isDismissingInternally = false
            }
        }
        onDismiss()
    }

    private func runExternalDismiss() {
        presentTask?.cancel()
        presentTask = nil
        guard renderedItem != nil else { return }
        resignActiveKeyboard()

        if simplifyMotion {
            isShowingSheet = false
            renderedItem = nil
            dragOffsetY = 0
            return
        }

        withAnimation(GokeMotion.sheetExit(reduceMotion: false)) {
            isShowingSheet = false
        } completion: {
            if !isShowingSheet && item == nil {
                renderedItem = nil
                dragOffsetY = 0
            }
        }
    }
}

extension GokeOverlaySheetContainer where Item == Bool {
    /// Source-compatible boolean presentation initializer. Prefer `init(item:...)` so hosted content
    /// stays alive throughout the exit animation without collapsing on Frame 0.
    init(
        isPresented: Bool,
        scrimAlpha: Double = 0.22,
        cornerRadius: CGFloat = GokeRadius.lg,
        containerColor: Color? = nil,
        canDragDismiss: Bool = true,
        onRequestDismiss: (() -> Bool)? = nil,
        onDismiss: @escaping () -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(
            item: isPresented ? true : nil,
            scrimAlpha: scrimAlpha,
            cornerRadius: cornerRadius,
            containerColor: containerColor,
            canDragDismiss: canDragDismiss,
            onRequestDismiss: onRequestDismiss,
            onDismiss: onDismiss,
            content: { _ in content() }
        )
    }
}
