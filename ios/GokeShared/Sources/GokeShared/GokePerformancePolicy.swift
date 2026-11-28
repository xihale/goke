import Foundation
import SwiftUI
#if canImport(Combine)
import Combine
#endif

// MARK: - User Performance Mode Preference

/// User-selectable performance & visual-effects tier for low-end devices and constrained battery/thermal states.
public enum GokePerformanceMode: String, CaseIterable, Identifiable, Sendable, Hashable, Codable {
    /// Automatically degrades glass/blur, shadows, and spring/3D effects under Low Power Mode,
    /// thermal throttling (`.serious` / `.critical`), <= 4 GB RAM devices, or Reduce Motion / Transparency.
    case auto
    /// Keeps full visual effects unless system Accessibility settings (Reduce Motion / Transparency) are enabled.
    case standard
    /// Forces lightweight flat surfaces, zero shadows/blur, collapsed springs/3D stacking, and 2x PNG export cap.
    case powerSaver

    public var id: String { rawValue }

    /// Shared `UserDefaults` key for lightweight preference persistence prior to `AppSettings` sync.
    public static let userDefaultsKey = "goke.performanceMode"

    public var displayName: String {
        switch self {
        case .auto: "自动"
        case .standard: "标准"
        case .powerSaver: "省电流畅"
        }
    }

    public var subtitle: String {
        switch self {
        case .auto: "低电量、发热或低内存设备自动简化模糊与动效"
        case .standard: "保持完整玻璃质感与弹簧动效（遵循系统无障碍设置）"
        case .powerSaver: "关闭实时模糊、阴影与复杂动效，优先 60fps 流畅与续航"
        }
    }

    /// Loads the persisted mode from `UserDefaults`, defaulting to `.auto`.
    public static func load(from defaults: UserDefaults = .standard) -> GokePerformanceMode {
        guard let raw = defaults.string(forKey: userDefaultsKey),
              let mode = GokePerformanceMode(rawValue: raw) else {
            return .auto
        }
        return mode
    }

    /// Persists the selected mode to `UserDefaults`.
    public func save(to defaults: UserDefaults = .standard) {
        defaults.set(rawValue, forKey: Self.userDefaultsKey)
    }
}

// MARK: - Performance Policy Snapshot

/// Immutable, O(1)-equatable runtime snapshot driving graceful visual & animation degradation.
///
/// Injected via `@Environment(\.gokePerformance)` so `GlassSurface`, `MintComponents`,
/// `TimetableView`, and exporters can adapt without allocating or observing Foundation notifications per-view.
public struct GokePerformancePolicy: Sendable, Equatable, Hashable {
    /// `Sendable` + `Hashable` mirror of `ProcessInfo.ThermalState`.
    public enum ThermalTier: Int, Sendable, Equatable, Hashable, Comparable {
        case nominal = 0
        case fair = 1
        case serious = 2
        case critical = 3

        public static func < (lhs: ThermalTier, rhs: ThermalTier) -> Bool {
            lhs.rawValue < rhs.rawValue
        }

        public init(_ state: ProcessInfo.ThermalState) {
            switch state {
            case .nominal: self = .nominal
            case .fair: self = .fair
            case .serious: self = .serious
            case .critical: self = .critical
            @unknown default: self = .nominal
            }
        }
    }

    /// RAM threshold (`3.5 GB`) classifying <= 3 GB devices (e.g., iPhone XR / SE2) as visually degraded
    /// so 4 GB A13 devices (iPhone 11) keep native `.ultraThinMaterial` and smooth springs under nominal load.
    public static let lowMemoryVisualThresholdBytes: UInt64 = 3_500_000_000

    /// RAM threshold (`4.5 GB`) classifying <= 4 GB devices (including iPhone 11) for capped `2.0x` PNG export
    /// to guarantee zero Jetsam OOM during timetable image rasterization.
    public static let lowMemoryThresholdBytes: UInt64 = 4_500_000_000

    public let mode: GokePerformanceMode
    public let isLowPowerModeEnabled: Bool
    public let thermalState: ThermalTier
    public let isLowMemoryDevice: Bool
    public let isConstrainedExportMemory: Bool
    public let reduceMotion: Bool
    public let reduceTransparency: Bool

    public init(
        mode: GokePerformanceMode = .auto,
        isLowPowerModeEnabled: Bool = false,
        thermalState: ThermalTier = .nominal,
        isLowMemoryDevice: Bool = false,
        isConstrainedExportMemory: Bool? = nil,
        reduceMotion: Bool = false,
        reduceTransparency: Bool = false
    ) {
        self.mode = mode
        self.isLowPowerModeEnabled = isLowPowerModeEnabled
        self.thermalState = thermalState
        self.isLowMemoryDevice = isLowMemoryDevice
        self.isConstrainedExportMemory = isConstrainedExportMemory ?? isLowMemoryDevice
        self.reduceMotion = reduceMotion
        self.reduceTransparency = reduceTransparency
    }

    /// Non-degraded baseline policy suitable for previews and tests.
    public static let standard = GokePerformancePolicy(mode: .standard)

    /// Captures a live policy snapshot from `ProcessInfo` and supplied accessibility/user flags.
    public static func current(
        mode: GokePerformanceMode = .auto,
        reduceMotion: Bool = false,
        reduceTransparency: Bool = false,
        processInfo: ProcessInfo = .processInfo
    ) -> GokePerformancePolicy {
        GokePerformancePolicy(
            mode: mode,
            isLowPowerModeEnabled: processInfo.isLowPowerModeEnabled,
            thermalState: ThermalTier(processInfo.thermalState),
            isLowMemoryDevice: processInfo.physicalMemory < lowMemoryVisualThresholdBytes,
            isConstrainedExportMemory: processInfo.physicalMemory < lowMemoryThresholdBytes,
            reduceMotion: reduceMotion,
            reduceTransparency: reduceTransparency
        )
    }

    // MARK: - Derived Degradation Flags

    /// Whether the device is currently experiencing elevated thermal pressure (`.serious` or `.critical`).
    public var isThermallyThrottled: Bool {
        thermalState >= .serious
    }

    /// Master degradation signal for consumers that want a single boolean switch.
    public var isDegraded: Bool {
        switch mode {
        case .powerSaver:
            return true
        case .standard:
            return false
        case .auto:
            return isLowPowerModeEnabled
                || isThermallyThrottled
                || isLowMemoryDevice
                || reduceTransparency
                || reduceMotion
        }
    }

    /// When `true`, `GlassSurface` strips `.glassEffect`, `.ultraThinMaterial`, specular rim gradients,
    /// and drop shadows in favor of an opaque `theme.surfaceHigh` fill + `0.5pt` `theme.outlineVariant` stroke.
    public var shouldDisableGlassAndBlur: Bool {
        if reduceTransparency {
            return true
        }
        switch mode {
        case .powerSaver:
            return true
        case .standard:
            return false
        case .auto:
            return isLowPowerModeEnabled || isThermallyThrottled || isLowMemoryDevice
        }
    }

    /// When `true`, spring animations collapse to `nil` or a fast opacity crossfade,
    /// and continuous symbol rotations pause.
    public var shouldSimplifyMotion: Bool {
        if reduceMotion {
            return true
        }
        switch mode {
        case .powerSaver:
            return true
        case .standard:
            return false
        case .auto:
            return isLowPowerModeEnabled || isThermallyThrottled
        }
    }

    /// When `true`, iOS 18+ `.matchedTransitionSource` / `.navigationTransition(.zoom)` falls back to standard push.
    public var shouldDisableZoomTransitions: Bool {
        shouldSimplifyMotion || isDegraded
    }

    /// When `true`, timetable 3D card stacking flattens to 2D cards + compact `+N` badge with zero shadow pass.
    public var shouldFlatten3DStacking: Bool {
        isDegraded || shouldDisableGlassAndBlur || shouldSimplifyMotion
    }

    /// Maximum rasterization scale for `TimetableImageExporter` (`2.0` on constrained devices to avoid Jetsam OOM).
    public var maxExportScale: CGFloat {
        if mode == .powerSaver || isLowMemoryDevice || isConstrainedExportMemory || isThermallyThrottled || isLowPowerModeEnabled {
            return 2.0
        }
        return 3.0
    }

    /// When `true`, cosmetic foreground timers (e.g., 60s countdown ticks) should pause or coalesce.
    public var shouldPauseCosmeticTimers: Bool {
        switch mode {
        case .powerSaver:
            return true
        case .standard:
            return false
        case .auto:
            return isLowPowerModeEnabled || isThermallyThrottled
        }
    }

    /// Recommended debounce interval (seconds) for background/edit-triggered sync pushes.
    public var syncDebounceSeconds: TimeInterval {
        shouldPauseCosmeticTimers ? 12.0 : 4.0
    }
}

// MARK: - GokeMotion Policy Extensions

public extension GokeMotion {
    /// Fast opacity crossfade used when springs are degraded (and Reduce Motion is off).
    static let degradedCrossfade: Animation = .easeOut(duration: 0.12)

    /// Unified motion-simplification check combining system Reduce Motion and runtime `GokePerformancePolicy`.
    static func simplify(reduceMotion: Bool, policy: GokePerformancePolicy) -> Bool {
        reduceMotion || policy.shouldSimplifyMotion
    }

    static func press(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : pressSpring
    }

    static func snappy(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : snappySpring
    }

    static func standard(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : standardSpring
    }

    static func interactiveDrag(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : interactiveDragSpring
    }

    static func gentle(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : gentleSpring
    }

    static func dismiss(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : dismissAnimation
    }

    static func sheetEnter(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : sheetEnterSpring
    }

    static func sheetExit(policy: GokePerformancePolicy) -> Animation? {
        policy.shouldSimplifyMotion ? nil : sheetExitAnimation
    }

    /// Returns `standardSpring` normally, `0.12s` crossfade when degraded, or `nil` under Reduce Motion.
    static func crossfade(policy: GokePerformancePolicy) -> Animation? {
        if policy.reduceMotion {
            return nil
        }
        return policy.shouldSimplifyMotion ? degradedCrossfade : standardSpring
    }
}

// MARK: - SwiftUI Environment & Live Observer Integration

private struct GokePerformancePolicyKey: EnvironmentKey {
    static let defaultValue: GokePerformancePolicy = .current()
}

public extension EnvironmentValues {
    /// Active runtime performance policy governing glass blur, shadows, springs, 3D stacking, and export caps.
    var gokePerformance: GokePerformancePolicy {
        get { self[GokePerformancePolicyKey.self] }
        set { self[GokePerformancePolicyKey.self] = newValue }
    }
}

/// Root-level `ViewModifier` that observes Low Power Mode, thermal state, physical memory class,
/// and SwiftUI Accessibility flags, publishing an O(1) `GokePerformancePolicy` into `EnvironmentValues`.
public struct GokePerformanceObserverModifier: ViewModifier {
    private let explicitMode: GokePerformanceMode?

    @AppStorage(GokePerformanceMode.userDefaultsKey)
    private var storedModeRawValue: String = GokePerformanceMode.auto.rawValue

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @State private var isLowPowerModeEnabled: Bool = ProcessInfo.processInfo.isLowPowerModeEnabled
    @State private var thermalState: GokePerformancePolicy.ThermalTier = .init(ProcessInfo.processInfo.thermalState)
    private let isLowMemoryDevice: Bool = ProcessInfo.processInfo.physicalMemory < GokePerformancePolicy.lowMemoryVisualThresholdBytes
    private let isConstrainedExportMemory: Bool = ProcessInfo.processInfo.physicalMemory < GokePerformancePolicy.lowMemoryThresholdBytes

    public init(mode: GokePerformanceMode? = nil) {
        self.explicitMode = mode
    }

    private var resolvedMode: GokePerformanceMode {
        explicitMode ?? GokePerformanceMode(rawValue: storedModeRawValue) ?? .auto
    }

    private var resolvedPolicy: GokePerformancePolicy {
        GokePerformancePolicy(
            mode: resolvedMode,
            isLowPowerModeEnabled: isLowPowerModeEnabled,
            thermalState: thermalState,
            isLowMemoryDevice: isLowMemoryDevice,
            isConstrainedExportMemory: isConstrainedExportMemory,
            reduceMotion: reduceMotion,
            reduceTransparency: reduceTransparency
        )
    }

    public func body(content: Content) -> some View {
        content
            .environment(\.gokePerformance, resolvedPolicy)
            .onReceive(
                NotificationCenter.default
                    .publisher(for: .NSProcessInfoPowerStateDidChange)
                    .receive(on: RunLoop.main)
            ) { _ in
                let next = ProcessInfo.processInfo.isLowPowerModeEnabled
                if next != isLowPowerModeEnabled {
                    isLowPowerModeEnabled = next
                }
            }
            .onReceive(
                NotificationCenter.default
                    .publisher(for: ProcessInfo.thermalStateDidChangeNotification)
                    .receive(on: RunLoop.main)
            ) { _ in
                let next = GokePerformancePolicy.ThermalTier(ProcessInfo.processInfo.thermalState)
                if next != thermalState {
                    thermalState = next
                }
            }
    }
}

public extension View {
    /// Injects an explicit `GokePerformancePolicy` snapshot into the SwiftUI environment.
    func gokePerformance(_ policy: GokePerformancePolicy) -> some View {
        environment(\.gokePerformance, policy)
    }

    /// Attaches a live observer for Low Power Mode, thermal state, device RAM class, and accessibility flags,
    /// injecting the resolved `GokePerformancePolicy` into `EnvironmentValues.gokePerformance`.
    ///
    /// Pass `mode: nil` (default) to automatically read the user's persisted `GokePerformanceMode` preference.
    func gokePerformanceObserver(mode: GokePerformanceMode? = nil) -> some View {
        modifier(GokePerformanceObserverModifier(mode: mode))
    }
}
