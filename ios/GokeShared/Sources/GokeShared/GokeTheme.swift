import GokeCoreModel
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Color Hex & Dynamic Light/Dark Helpers

public extension Color {
    /// Creates a color from a 24-bit RGB hex value (e.g. `0x2E776C`).
    init(hex: UInt32, opacity: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: opacity)
    }

    /// Creates a dynamic color that resolves to `light` in light mode and `dark` in dark mode.
    init(light: UInt32, dark: UInt32, opacity: Double = 1.0) {
        #if canImport(UIKit)
        self.init(
            uiColor: UIColor { traits in
                let hex = traits.userInterfaceStyle == .dark ? dark : light
                let r = CGFloat((hex >> 16) & 0xFF) / 255.0
                let g = CGFloat((hex >> 8) & 0xFF) / 255.0
                let b = CGFloat(hex & 0xFF) / 255.0
                return UIColor(red: r, green: g, blue: b, alpha: CGFloat(opacity))
            }
        )
        #else
        self.init(hex: light, opacity: opacity)
        #endif
    }

    /// Creates a dynamic color from prebuilt light and dark `Color` values.
    init(light: Color, dark: Color) {
        #if canImport(UIKit)
        self.init(
            uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
            }
        )
        #else
        self = light
        #endif
    }
}

// MARK: - Botanical Course Color Pairs

/// A paired background/foreground color token for timetable courses and fixed time-blocks.
public struct CourseColorPair: Sendable, Equatable, Hashable {
    public let index: Int
    public let name: String
    public let background: Color
    public let foreground: Color

    public init(index: Int, name: String, background: Color, foreground: Color) {
        self.index = index
        self.name = name
        self.background = background
        self.foreground = foreground
    }

    // MARK: - Tonal Progress Wipe & Finished Dimming

    /// Subtle elapsed-portion blend opacity for ongoing timetable blocks (14%, no hard frontier line).
    public static let elapsedBlendOpacity: Double = 0.14
    /// Dimmed surface opacity for finished timetable blocks.
    public static let finishedSurfaceOpacity: Double = 0.55
    /// Dimmed ink opacity for finished timetable blocks.
    public static let finishedInkOpacity: Double = 0.72

    /// Soft botanical tint overlaid on the elapsed portion of an ongoing time-block.
    public var elapsedTint: Color {
        foreground.opacity(Self.elapsedBlendOpacity)
    }

    /// Dimmed surface fill for a finished time-block.
    public var finishedBackground: Color {
        background.opacity(Self.finishedSurfaceOpacity)
    }

    /// Dimmed foreground ink for a finished time-block.
    public var finishedForeground: Color {
        foreground.opacity(Self.finishedInkOpacity)
    }

    /// Returns the appropriate surface color for finished vs. active/upcoming blocks.
    public func surfaceColor(isFinished: Bool) -> Color {
        isFinished ? finishedBackground : background
    }

    /// Returns the appropriate ink color for finished vs. active/upcoming blocks.
    public func inkColor(isFinished: Bool) -> Color {
        isFinished ? finishedForeground : foreground
    }

    /// Crisp saturated left-edge accent bar color for Apple Calendar-style timetable blocks & timeline rows.
    public var barColor: Color {
        switch index {
        case 0: Color(light: 0x2D7DA3, dark: 0x7AC7F0) // Sky
        case 1: Color(light: 0x1E7A6D, dark: 0x4FD1B8) // Mint
        case 2: Color(light: 0x5E56B0, dark: 0xA69DF2) // Iris
        case 3: Color(light: 0xB08A1E, dark: 0xF2CE63) // Wheat
        case 4: Color(light: 0xCC623D, dark: 0xF59D7D) // Peach
        case 5: Color(light: 0x488244, dark: 0x8FD487) // Sage
        case 6: Color(light: 0xBF4366, dark: 0xF28CA8) // Rose
        case 7: Color(light: 0x4C62B0, dark: 0x93A8F2) // Mist
        default: foreground
        }
    }

    /// Fixed bright dark-surface foreground color for always-black surfaces (e.g. Dynamic Island in Light Mode).
    public var darkForeground: Color {
        switch index {
        case 0: Color(hex: 0xBFE0F0) // Sky
        case 1: Color(hex: 0xB2E3D8) // Mint
        case 2: Color(hex: 0xD3D0F5) // Iris
        case 3: Color(hex: 0xF0E5B6) // Wheat
        case 4: Color(hex: 0xF5D2BF) // Peach
        case 5: Color(hex: 0xCFE5C8) // Sage
        case 6: Color(hex: 0xF4CBD7) // Rose
        case 7: Color(hex: 0xC9D3F5) // Mist
        default: Color(hex: 0xB2E3D8)
        }
    }

    /// Fixed bright dark-surface accent bar & keyline color for always-black surfaces (e.g. Dynamic Island).
    public var darkBarColor: Color {
        switch index {
        case 0: Color(hex: 0x7AC7F0) // Sky
        case 1: Color(hex: 0x4FD1B8) // Mint
        case 2: Color(hex: 0xA69DF2) // Iris
        case 3: Color(hex: 0xF2CE63) // Wheat
        case 4: Color(hex: 0xF59D7D) // Peach
        case 5: Color(hex: 0x8FD487) // Sage
        case 6: Color(hex: 0xF28CA8) // Rose
        case 7: Color(hex: 0x93A8F2) // Mist
        default: Color(hex: 0x4FD1B8)
        }
    }

    /// Subtle top-to-bottom botanical tonal progress wipe with a soft feathered edge (zero hard frontier line).
    public func progressWipeGradient(progress: Double) -> LinearGradient {
        progressWipeGradient(progress: progress, startPoint: .top, endPoint: .bottom)
    }

    /// Subtle left-to-right botanical tonal progress wipe for horizontal agenda rows (matches Android `CourseProgressAxis.HORIZONTAL`).
    public func horizontalProgressWipeGradient(progress: Double) -> LinearGradient {
        progressWipeGradient(progress: progress, startPoint: .leading, endPoint: .trailing)
    }

    private func progressWipeGradient(progress: Double, startPoint: UnitPoint, endPoint: UnitPoint) -> LinearGradient {
        let clamped = min(max(progress, 0.0), 1.0)
        if clamped <= 0.001 {
            return LinearGradient(colors: [.clear, .clear], startPoint: startPoint, endPoint: endPoint)
        }
        if clamped >= 0.999 {
            return LinearGradient(colors: [elapsedTint, elapsedTint], startPoint: startPoint, endPoint: endPoint)
        }
        let feather = 0.035
        let fadeStart = max(0.0, clamped - feather)
        let fadeEnd = min(1.0, clamped + feather)
        return LinearGradient(
            stops: [
                .init(color: elapsedTint, location: 0.0),
                .init(color: elapsedTint, location: fadeStart),
                .init(color: .clear, location: fadeEnd),
                .init(color: .clear, location: 1.0),
            ],
            startPoint: startPoint,
            endPoint: endPoint
        )
    }

    /// The 8 botanical course color pairs from Goke's `Theme.kt` source of truth.
    public static let botanical: [CourseColorPair] = [
        CourseColorPair(
            index: 0,
            name: "Sky",
            background: Color(light: 0xD9E8EF, dark: 0x2B4350),
            foreground: Color(light: 0x3B6173, dark: 0xBFE0F0)
        ),
        CourseColorPair(
            index: 1,
            name: "Mint",
            background: Color(light: 0xBFDCD5, dark: 0x24463F),
            foreground: Color(light: 0x2C5B53, dark: 0xB2E3D8)
        ),
        CourseColorPair(
            index: 2,
            name: "Iris",
            background: Color(light: 0xDAD8EE, dark: 0x38355A),
            foreground: Color(light: 0x4F4D7C, dark: 0xD3D0F5)
        ),
        CourseColorPair(
            index: 3,
            name: "Wheat",
            background: Color(light: 0xF3EBCF, dark: 0x4A4224),
            foreground: Color(light: 0x75662F, dark: 0xF0E5B6)
        ),
        CourseColorPair(
            index: 4,
            name: "Peach",
            background: Color(light: 0xF7DFD2, dark: 0x503629),
            foreground: Color(light: 0x85503A, dark: 0xF5D2BF)
        ),
        CourseColorPair(
            index: 5,
            name: "Sage",
            background: Color(light: 0xDFE8DA, dark: 0x33442F),
            foreground: Color(light: 0x4B5F49, dark: 0xCFE5C8)
        ),
        CourseColorPair(
            index: 6,
            name: "Rose",
            background: Color(light: 0xF4D9E0, dark: 0x56323E),
            foreground: Color(light: 0x83465A, dark: 0xF4CBD7)
        ),
        CourseColorPair(
            index: 7,
            name: "Mist",
            background: Color(light: 0xD2D8EA, dark: 0x303A5E),
            foreground: Color(light: 0x454F7A, dark: 0xC9D3F5)
        ),
    ]
}

// MARK: - Theme Presets

/// The 6 botanical theme presets matching Android `Preset` in `Theme.kt`.
public enum GokeThemePreset: String, CaseIterable, Identifiable, Sendable, Hashable, Codable {
    case mint
    case peach
    case lavender
    case sky
    case rose
    case lemon

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .mint: "薄荷"
        case .peach: "蜜桃"
        case .lavender: "薰衣草"
        case .sky: "天空"
        case .rose: "玫瑰"
        case .lemon: "柠檬"
        }
    }

    public var seedHex: UInt32 {
        switch self {
        case .mint: 0x2E776C
        case .peach: 0xE8836B
        case .lavender: 0x7A6CC4
        case .sky: 0x4A90D9
        case .rose: 0xD65C7A
        case .lemon: 0xC9A227
        }
    }

    public var seedColor: Color {
        Color(hex: seedHex)
    }

    public static func from(key: String) -> GokeThemePreset {
        GokeThemePreset(rawValue: key.lowercased()) ?? .mint
    }
}

// MARK: - Design Metrics, Typography & Motion

public enum GokeRadius {
    public static let xs: CGFloat = 4
    public static let sm: CGFloat = 12
    public static let md: CGFloat = 16
    public static let lg: CGFloat = 24
    public static let xl: CGFloat = 28
    public static let pill: CGFloat = 999
}

public enum GokeSpacing {
    public static let xs: CGFloat = 4
    public static let sm: CGFloat = 8
    public static let md: CGFloat = 12
    public static let lg: CGFloat = 16
    public static let xl: CGFloat = 24
}

public enum GokeTypography {
    public static let headlineMedium: Font = .system(size: 26, weight: .semibold)
    public static let titleLarge: Font = .system(size: 22, weight: .semibold)
    public static let titleMedium: Font = .system(size: 16, weight: .medium)
    public static let bodyLarge: Font = .system(size: 16, weight: .regular)
    public static let bodyMedium: Font = .system(size: 14, weight: .regular)
    public static let labelLarge: Font = .system(size: 14, weight: .medium)
    public static let labelMedium: Font = .system(size: 12, weight: .medium)
    public static let labelSmall: Font = .system(size: 11, weight: .medium)
}

public enum GokeMotion {
    /// Immediate tactile detent (`0.98x` row / `0.96x` compact control, checkbox draw, segmented thumb,
    /// row done fade). Critically damped (zero bounce), matching Android M3 fastSpatial (`1400` stiffness).
    public static let pressSpring: Animation = .spring(duration: 0.11, bounce: 0.0)
    /// Crisp micro-interactions: chip/tray toggles, calendar day ring, swipe-row snapback, numeric counters.
    /// Zero overshoot so swipe rows and keyboard-docked panels never wobble past rest.
    public static let snappySpring: Animation = .spring(duration: 0.18, bounce: 0.0)
    /// Panels, disclosures, month↔week grid height, and toast entrance.
    /// Critically damped (zero bounce), matching Android M3 defaultSpatial (`700` stiffness).
    public static let standardSpring: Animation = .spring(duration: 0.24, bounce: 0.0)
    /// Direct-manipulation drag follow & snapback (timetable task drag, overlay drag-to-dismiss).
    public static let interactiveDragSpring: Animation = .interactiveSpring(
        response: 0.18,
        dampingFraction: 0.92,
        blendDuration: 0.04
    )
    /// Calm fluid settles (`TodayProgressRing` arc sweep, progress bars) — decisive, never sluggish.
    public static let gentleSpring: Animation = .spring(duration: 0.28, bounce: 0.0)
    /// Fast asymmetric exit curve (zero bounce) for dismissing overlays, toasts, and popovers.
    public static let dismissAnimation: Animation = .easeOut(duration: 0.16)
    /// Critically damped overlay sheet enter (zero upward overshoot / zero bottom gap on open).
    public static let sheetEnterSpring: Animation = .spring(duration: 0.22, bounce: 0.0)
    /// Fast asymmetric overlay sheet exit (zero bounce).
    public static let sheetExitAnimation: Animation = .easeOut(duration: 0.16)
    /// Subtle opacity-only crossfade for transitions under Reduce Motion or degraded performance.
    public static let reduceMotionCrossfade: Animation = .easeOut(duration: 0.12)

    public static func press(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : pressSpring
    }

    public static func snappy(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : snappySpring
    }

    public static func standard(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : standardSpring
    }

    public static func interactiveDrag(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : interactiveDragSpring
    }

    public static func gentle(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : gentleSpring
    }

    public static func dismiss(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : dismissAnimation
    }

    public static func sheetEnter(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : sheetEnterSpring
    }

    public static func sheetExit(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : sheetExitAnimation
    }

    public static func crossfade(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : reduceMotionCrossfade
    }
}

// MARK: - GokeTheme

/// Semantic "素纸 Goke (Clean Paper Grid)" color & token palette.
///
/// Equality is keyed on `(preset, customSeedHex)` so SwiftUI `@Environment(\.gokeTheme)`
/// checks remain O(1) for 120Hz ProMotion rendering.
public struct GokeTheme: Sendable, Equatable, Hashable {
    public let preset: GokeThemePreset
    public let customSeedHex: UInt32?

    // Paper & Surface hierarchy
    public let paper: Color
    public let surface: Color
    public let surfaceHigh: Color
    public let surfaceHighest: Color

    // Ink & Outlines
    public let ink: Color
    public let muted: Color
    public let outline: Color
    public let outlineVariant: Color

    // Primary Accent
    public let accent: Color
    public let onAccent: Color
    public let accentContainer: Color
    public let onAccentContainer: Color

    // Secondary & Tertiary
    public let secondary: Color
    public let onSecondary: Color
    public let secondaryContainer: Color
    public let onSecondaryContainer: Color

    public let tertiary: Color
    public let onTertiary: Color
    public let tertiaryContainer: Color
    public let onTertiaryContainer: Color

    // Semantic Signals
    public let error: Color
    public let onError: Color
    public let errorContainer: Color
    public let onErrorContainer: Color

    public let success: Color
    public let added: Color
    public let modified: Color
    public let onModified: Color
    public let removed: Color
    public let conflictBg: Color

    // Task Priorities
    public let priorityLow: Color
    public let priorityMedium: Color
    public let priorityHigh: Color

    // Sticky-Note Reminder Tokens (matching Android Theme.kt)
    public let stickyBg: Color
    public let stickyBorder: Color
    public let stickyFg: Color

    // 8 Botanical Course Pairs
    public let courseColors: [CourseColorPair]

    public static func == (lhs: GokeTheme, rhs: GokeTheme) -> Bool {
        lhs.preset == rhs.preset && lhs.customSeedHex == rhs.customSeedHex
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(preset)
        hasher.combine(customSeedHex)
    }

    /// Returns the botanical course color pair for `index`, safely wrapping negative and large indices.
    public func courseColor(index: Int) -> CourseColorPair {
        let count = courseColors.count
        let normalized = ((index % count) + count) % count
        return courseColors[normalized]
    }

    /// Resolves the active `GokeTheme` from `AppSettings` (shared across App, Widgets & Live Activity).
    public static func resolve(settings: AppSettings) -> GokeTheme {
        switch settings.themeSource {
        case .CUSTOM:
            return GokeTheme.custom(seedHex: settings.seedColorARGB & 0x00FF_FFFF)
        case .PRESET, .DYNAMIC:
            return GokeTheme.forPreset(GokeThemePreset.from(key: settings.preset))
        }
    }

    /// Returns the semantic color for a task priority (`0` = none, `1` = low, `2` = medium, `3+` = high).
    public func priorityColor(_ priority: Int) -> Color? {
        switch priority {
        case ...0: nil
        case 1: priorityLow
        case 2: priorityMedium
        default: priorityHigh
        }
    }

    // MARK: - iOS Semantic Surface Colors

    /// Native iOS primary canvas background (`UIColor.systemBackground`), falling back to `surface`.
    public var systemBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemBackground)
        #else
        surface
        #endif
    }

    /// Native iOS secondary canvas background (`UIColor.secondarySystemBackground`), falling back to `surfaceHigh`.
    public var secondarySystemBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemBackground)
        #else
        surfaceHigh
        #endif
    }

    /// Native iOS tertiary canvas background (`UIColor.tertiarySystemBackground`), falling back to `surfaceHighest`.
    public var tertiarySystemBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .tertiarySystemBackground)
        #else
        surfaceHighest
        #endif
    }

    /// Native iOS inset-grouped list canvas (`UIColor.systemGroupedBackground`), falling back to `paper`.
    public var groupedBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemGroupedBackground)
        #else
        paper
        #endif
    }

    /// Native iOS inset-grouped row/card surface (`UIColor.secondarySystemGroupedBackground`), falling back to `surface`.
    public var groupedSurface: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
        #else
        surface
        #endif
    }

    /// Native iOS tertiary inset-grouped surface (`UIColor.tertiarySystemGroupedBackground`), falling back to `surfaceHigh`.
    public var tertiaryGroupedSurface: Color {
        #if canImport(UIKit)
        Color(uiColor: .tertiarySystemGroupedBackground)
        #else
        surfaceHigh
        #endif
    }

    /// Native iOS primary fill (`UIColor.systemFill`), falling back to `surfaceHighest`.
    public var fill: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemFill)
        #else
        surfaceHighest
        #endif
    }

    /// Native iOS secondary fill (`UIColor.secondarySystemFill`), falling back to `surfaceHighest`.
    public var secondaryFill: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemFill)
        #else
        surfaceHighest.opacity(0.8)
        #endif
    }

    /// Native iOS tertiary fill (`UIColor.tertiarySystemFill`), falling back to `surfaceHigh`.
    public var tertiaryFill: Color {
        #if canImport(UIKit)
        Color(uiColor: .tertiarySystemFill)
        #else
        surfaceHigh
        #endif
    }

    /// Native iOS quaternary fill (`UIColor.quaternarySystemFill`), falling back to `surfaceHigh`.
    public var quaternaryFill: Color {
        #if canImport(UIKit)
        Color(uiColor: .quaternarySystemFill)
        #else
        surfaceHigh.opacity(0.6)
        #endif
    }

    /// Native iOS hairline divider (`UIColor.separator`), falling back to `outlineVariant`.
    public var separator: Color {
        #if canImport(UIKit)
        Color(uiColor: .separator)
        #else
        outlineVariant
        #endif
    }

    /// Native iOS opaque hairline divider (`UIColor.opaqueSeparator`), falling back to `outlineVariant`.
    public var opaqueSeparator: Color {
        #if canImport(UIKit)
        Color(uiColor: .opaqueSeparator)
        #else
        outlineVariant
        #endif
    }

    // MARK: - UIKit Semantic Signal Helpers

    private static var systemRedColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemRed)
        #else
        Color(light: 0xFF3B30, dark: 0xFF453A)
        #endif
    }

    private static var systemGreenColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemGreen)
        #else
        Color(light: 0x34C759, dark: 0x30D158)
        #endif
    }

    private static var systemOrangeColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemOrange)
        #else
        Color(light: 0xFF9500, dark: 0xFF9F0A)
        #endif
    }

    private static var systemBlueColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemBlue)
        #else
        Color(light: 0x007AFF, dark: 0x0A84FF)
        #endif
    }

    // MARK: - Preset Themes

    /// Hand-tuned Mint brand theme matching Android `Theme.kt` (`brandLight` / `brandDark` + `mintColors`).
    public static let mint = GokeTheme(
        preset: .mint,
        customSeedHex: nil,
        paper: Color(light: 0xF2F7F5, dark: 0x141A19),
        surface: Color(light: 0xFFFFFF, dark: 0x1E2624),
        surfaceHigh: Color(light: 0xF7FAF9, dark: 0x26302D),
        surfaceHighest: Color(light: 0xE9F1EE, dark: 0x2A3431),
        ink: Color(light: 0x24312D, dark: 0xE6EEEB),
        muted: Color(light: 0x5E6F69, dark: 0x93A39E),
        outline: Color(light: 0x6E7F7A, dark: 0x6C7C77),
        outlineVariant: Color(light: 0xE4ECE9, dark: 0x2A3431),
        accent: Color(light: 0x2E776C, dark: 0x7CCDBA),
        onAccent: Color(light: 0xFFFFFF, dark: 0x0B2A24),
        accentContainer: Color(light: 0xCDEBE3, dark: 0x24413B),
        onAccentContainer: Color(light: 0x1B473F, dark: 0xBFEDE2),
        secondary: Color(light: 0xB4694B, dark: 0xF6D5C3),
        onSecondary: Color(light: 0xFFFFFF, dark: 0x4A3329),
        secondaryContainer: Color(light: 0xFBE3D6, dark: 0x4A3329),
        onSecondaryContainer: Color(light: 0x7A4630, dark: 0xF6D5C3),
        tertiary: Color(light: 0x6C63B5, dark: 0xC9C3F5),
        onTertiary: Color(light: 0xFFFFFF, dark: 0x2F2C45),
        tertiaryContainer: Color(light: 0xE4E0F6, dark: 0x2F2C45),
        onTertiaryContainer: Color(light: 0x3D3873, dark: 0xE4E0F6),
        error: Color(light: 0xB23E50, dark: 0xFF8F84),
        onError: Color(light: 0xFFFFFF, dark: 0x3B0A06),
        errorContainer: Color(light: 0xFCE9E7, dark: 0x4A2A27),
        onErrorContainer: Color(light: 0x7A2A22, dark: 0xFCE9E7),
        success: Color(light: 0x46A171, dark: 0x68C794),
        added: Color(light: 0x46A171, dark: 0x68C794),
        modified: Color(light: 0xD5A03B, dark: 0xE8B95B),
        onModified: Color(light: 0x9A6B12, dark: 0xF1CF8A),
        removed: Color(light: 0xB23E50, dark: 0xE56458),
        conflictBg: Color(light: 0xFCE9E7, dark: 0x4A2A27),
        priorityLow: Color(light: 0x5E6F69, dark: 0x93A39E),
        priorityMedium: Color(light: 0xB07F1E, dark: 0xE0B45C),
        priorityHigh: Color(light: 0xB23E50, dark: 0xFF8F84),
        stickyBg: Color(light: 0xFFF0A8, dark: 0xF6CF65),
        stickyBorder: Color(light: 0xE6C64C, dark: 0xD9A734),
        stickyFg: Color(light: 0x4A3404, dark: 0x3B2804),
        courseColors: CourseColorPair.botanical
    )

    public static let peach = GokeTheme.presetPalette(
        preset: .peach,
        paper: (0xFAF4F1, 0x1A1514),
        surface: (0xFFFFFF, 0x261F1D),
        surfaceHigh: (0xFCF8F6, 0x302725),
        surfaceHighest: (0xF2E9E5, 0x362C29),
        ink: (0x332724, 0xEFE7E4),
        muted: (0x75615B, 0xA89690),
        outline: (0x85716B, 0x7F6E68),
        outlineVariant: (0xEDE2DD, 0x362C29),
        accent: (0xB8563E, 0xE0745A),
        onAccent: (0xFFFFFF, 0x2E0E06),
        accentContainer: (0xFADFD7, 0x542B21),
        onAccentContainer: (0x6B2C1D, 0xFDE5DE)
    )

    public static let lavender = GokeTheme.presetPalette(
        preset: .lavender,
        paper: (0xF5F4FA, 0x16151C),
        surface: (0xFFFFFF, 0x201E29),
        surfaceHigh: (0xF9F8FC, 0x292734),
        surfaceHighest: (0xECEAF5, 0x2F2D3C),
        ink: (0x282636, 0xEAE8F2),
        muted: (0x646078, 0x9C98B0),
        outline: (0x75718A, 0x75718A),
        outlineVariant: (0xE6E4F0, 0x2F2D3C),
        accent: (0x6356AD, 0x8A7CE0),
        onAccent: (0xFFFFFF, 0x150F33),
        accentContainer: (0xE5E1F8, 0x362F5E),
        onAccentContainer: (0x362D6B, 0xECE9FC)
    )

    public static let sky = GokeTheme.presetPalette(
        preset: .sky,
        paper: (0xF2F6FA, 0x13181D),
        surface: (0xFFFFFF, 0x1C232A),
        surfaceHigh: (0xF6F9FC, 0x242D36),
        surfaceHighest: (0xE8EFF6, 0x2A343E),
        ink: (0x222D38, 0xE5ECF3),
        muted: (0x5B6B7A, 0x90A0B0),
        outline: (0x6C7C8C, 0x6B7B8A),
        outlineVariant: (0xE1EAF2, 0x2A343E),
        accent: (0x2F6FB2, 0x3B8CE2),
        onAccent: (0xFFFFFF, 0x06192E),
        accentContainer: (0xD6E8FA, 0x1F3B59),
        onAccentContainer: (0x1B416B, 0xE0EFFC)
    )

    public static let rose = GokeTheme.presetPalette(
        preset: .rose,
        paper: (0xFAF3F5, 0x1B1416),
        surface: (0xFFFFFF, 0x261E20),
        surfaceHigh: (0xFCF7F8, 0x302629),
        surfaceHighest: (0xF3E7EA, 0x372B2E),
        ink: (0x342429, 0xF0E6E9),
        muted: (0x765F66, 0xA8939A),
        outline: (0x866E75, 0x7E6970),
        outlineVariant: (0xEEE0E4, 0x372B2E),
        accent: (0xB34260, 0xE05A7C),
        onAccent: (0xFFFFFF, 0x2E0714),
        accentContainer: (0xFADCE4, 0x572433),
        onAccentContainer: (0x6E1F36, 0xFDE4EB)
    )

    public static let lemon = GokeTheme.presetPalette(
        preset: .lemon,
        paper: (0xF9F7F0, 0x191813),
        surface: (0xFFFFFF, 0x24221B),
        surfaceHigh: (0xFBFAF5, 0x2D2B22),
        surfaceHighest: (0xF0ECE0, 0x343127),
        ink: (0x302D22, 0xEFECE1),
        muted: (0x706A56, 0xA39D88),
        outline: (0x807A65, 0x7A7461),
        outlineVariant: (0xEAE5D7, 0x343127),
        accent: (0x9B7B16, 0xC79B1E),
        onAccent: (0xFFFFFF, 0x261D03),
        accentContainer: (0xF5EBC6, 0x4A3D15),
        onAccentContainer: (0x5C4808, 0xFAF2D4)
    )

    /// Resolves a preset theme (Mint is the default).
    public static func forPreset(_ preset: GokeThemePreset) -> GokeTheme {
        switch preset {
        case .mint: .mint
        case .peach: .peach
        case .lavender: .lavender
        case .sky: .sky
        case .rose: .rose
        case .lemon: .lemon
        }
    }

    private static let customThemeCacheLock = NSLock()
    private static nonisolated(unsafe) var customThemeCache: [UInt32: GokeTheme] = [:]
    private static let customThemeCacheMaxEntries = 8

    /// Creates a theme derived from a custom user-selected 24-bit RGB seed color,
    /// tinting neutral surfaces (`paper`, `surface`, `surfaceHigh`, `surfaceHighest`, `ink`, `muted`, `outline`)
    /// to match Android `Theme.kt`'s `dynamicColorScheme(seedColor, isDark)`.
    public static func custom(seedHex: UInt32) -> GokeTheme {
        customThemeCacheLock.lock()
        if let cached = customThemeCache[seedHex] {
            customThemeCacheLock.unlock()
            return cached
        }
        customThemeCacheLock.unlock()

        let base = GokeTheme.mint
        let lightAccent = Self.tonedHex(seedHex, targetBrightness: 0.48, saturationScale: 0.92)
        let darkAccent = Self.tonedHex(seedHex, targetBrightness: 0.80, saturationScale: 0.45)
        let onDarkAccent = Self.tonedHex(seedHex, targetBrightness: 0.16, saturationScale: 0.72)
        let lightContainer = Self.tonedHex(seedHex, targetBrightness: 0.93, saturationScale: 0.22)
        let darkContainer = Self.tonedHex(seedHex, targetBrightness: 0.28, saturationScale: 0.48)
        let onLightContainer = Self.tonedHex(seedHex, targetBrightness: 0.28, saturationScale: 0.85)
        let onDarkContainer = Self.tonedHex(seedHex, targetBrightness: 0.92, saturationScale: 0.24)

        let lightPaper = Self.tonedHex(seedHex, targetBrightness: 0.97, saturationScale: 0.05)
        let darkPaper = Self.tonedHex(seedHex, targetBrightness: 0.10, saturationScale: 0.18)
        let darkSurface = Self.tonedHex(seedHex, targetBrightness: 0.14, saturationScale: 0.18)
        let lightSurfaceHigh = Self.tonedHex(seedHex, targetBrightness: 0.985, saturationScale: 0.03)
        let darkSurfaceHigh = Self.tonedHex(seedHex, targetBrightness: 0.18, saturationScale: 0.18)
        let lightSurfaceHighest = Self.tonedHex(seedHex, targetBrightness: 0.94, saturationScale: 0.07)
        let darkSurfaceHighest = Self.tonedHex(seedHex, targetBrightness: 0.21, saturationScale: 0.18)
        let lightInk = Self.tonedHex(seedHex, targetBrightness: 0.19, saturationScale: 0.25)
        let darkInk = Self.tonedHex(seedHex, targetBrightness: 0.93, saturationScale: 0.05)
        let lightMuted = Self.tonedHex(seedHex, targetBrightness: 0.44, saturationScale: 0.16)
        let darkMuted = Self.tonedHex(seedHex, targetBrightness: 0.64, saturationScale: 0.11)
        let lightOutline = Self.tonedHex(seedHex, targetBrightness: 0.50, saturationScale: 0.14)
        let darkOutline = Self.tonedHex(seedHex, targetBrightness: 0.48, saturationScale: 0.14)
        let lightOutlineVariant = Self.tonedHex(seedHex, targetBrightness: 0.92, saturationScale: 0.07)
        let darkOutlineVariant = Self.tonedHex(seedHex, targetBrightness: 0.21, saturationScale: 0.18)

        let built = GokeTheme(
            preset: .mint,
            customSeedHex: seedHex,
            paper: Color(light: lightPaper, dark: darkPaper),
            surface: Color(light: 0xFFFFFF, dark: darkSurface),
            surfaceHigh: Color(light: lightSurfaceHigh, dark: darkSurfaceHigh),
            surfaceHighest: Color(light: lightSurfaceHighest, dark: darkSurfaceHighest),
            ink: Color(light: lightInk, dark: darkInk),
            muted: Color(light: lightMuted, dark: darkMuted),
            outline: Color(light: lightOutline, dark: darkOutline),
            outlineVariant: Color(light: lightOutlineVariant, dark: darkOutlineVariant),
            accent: Color(light: lightAccent, dark: darkAccent),
            onAccent: Color(light: 0xFFFFFF, dark: onDarkAccent),
            accentContainer: Color(light: lightContainer, dark: darkContainer),
            onAccentContainer: Color(light: onLightContainer, dark: onDarkContainer),
            secondary: base.secondary,
            onSecondary: base.onSecondary,
            secondaryContainer: base.secondaryContainer,
            onSecondaryContainer: base.onSecondaryContainer,
            tertiary: base.tertiary,
            onTertiary: base.onTertiary,
            tertiaryContainer: base.tertiaryContainer,
            onTertiaryContainer: base.onTertiaryContainer,
            error: base.error,
            onError: base.onError,
            errorContainer: base.errorContainer,
            onErrorContainer: base.onErrorContainer,
            success: base.success,
            added: base.added,
            modified: base.modified,
            onModified: base.onModified,
            removed: base.removed,
            conflictBg: base.conflictBg,
            priorityLow: base.priorityLow,
            priorityMedium: base.priorityMedium,
            priorityHigh: base.priorityHigh,
            stickyBg: base.stickyBg,
            stickyBorder: base.stickyBorder,
            stickyFg: base.stickyFg,
            courseColors: CourseColorPair.botanical
        )
        customThemeCacheLock.lock()
        if customThemeCache.count >= customThemeCacheMaxEntries {
            customThemeCache.removeAll(keepingCapacity: true)
        }
        customThemeCache[seedHex] = built
        customThemeCacheLock.unlock()
        return built
    }

    // MARK: - Internal Palette Builders

    private static func presetPalette(
        preset: GokeThemePreset,
        paper: (UInt32, UInt32),
        surface: (UInt32, UInt32),
        surfaceHigh: (UInt32, UInt32),
        surfaceHighest: (UInt32, UInt32),
        ink: (UInt32, UInt32),
        muted: (UInt32, UInt32),
        outline: (UInt32, UInt32),
        outlineVariant: (UInt32, UInt32),
        accent: (UInt32, UInt32),
        onAccent: (UInt32, UInt32),
        accentContainer: (UInt32, UInt32),
        onAccentContainer: (UInt32, UInt32)
    ) -> GokeTheme {
        let base = GokeTheme.mint
        return GokeTheme(
            preset: preset,
            customSeedHex: nil,
            paper: Color(light: paper.0, dark: paper.1),
            surface: Color(light: surface.0, dark: surface.1),
            surfaceHigh: Color(light: surfaceHigh.0, dark: surfaceHigh.1),
            surfaceHighest: Color(light: surfaceHighest.0, dark: surfaceHighest.1),
            ink: Color(light: ink.0, dark: ink.1),
            muted: Color(light: muted.0, dark: muted.1),
            outline: Color(light: outline.0, dark: outline.1),
            outlineVariant: Color(light: outlineVariant.0, dark: outlineVariant.1),
            accent: Color(light: accent.0, dark: accent.1),
            onAccent: Color(light: onAccent.0, dark: onAccent.1),
            accentContainer: Color(light: accentContainer.0, dark: accentContainer.1),
            onAccentContainer: Color(light: onAccentContainer.0, dark: onAccentContainer.1),
            secondary: base.secondary,
            onSecondary: base.onSecondary,
            secondaryContainer: base.secondaryContainer,
            onSecondaryContainer: base.onSecondaryContainer,
            tertiary: base.tertiary,
            onTertiary: base.onTertiary,
            tertiaryContainer: base.tertiaryContainer,
            onTertiaryContainer: base.onTertiaryContainer,
            error: base.error,
            onError: base.onError,
            errorContainer: base.errorContainer,
            onErrorContainer: base.onErrorContainer,
            success: base.success,
            added: base.added,
            modified: base.modified,
            onModified: base.onModified,
            removed: base.removed,
            conflictBg: base.conflictBg,
            priorityLow: base.priorityLow,
            priorityMedium: base.priorityMedium,
            priorityHigh: base.priorityHigh,
            stickyBg: base.stickyBg,
            stickyBorder: base.stickyBorder,
            stickyFg: base.stickyFg,
            courseColors: CourseColorPair.botanical
        )
    }

    private static func tonedHex(
        _ hex: UInt32,
        targetBrightness: Double,
        saturationScale: Double
    ) -> UInt32 {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0

        let maxC = max(r, max(g, b))
        let minC = min(r, min(g, b))
        let delta = maxC - minC

        var h = 0.0
        if delta > 0.0001 {
            if maxC == r {
                h = ((g - b) / delta).truncatingRemainder(dividingBy: 6.0)
            } else if maxC == g {
                h = ((b - r) / delta) + 2.0
            } else {
                h = ((r - g) / delta) + 4.0
            }
            h /= 6.0
            if h < 0 { h += 1.0 }
        }
        let s = maxC > 0.0001 ? min(1.0, (delta / maxC) * saturationScale) : 0.0
        let v = min(1.0, max(0.0, targetBrightness))

        let sector = Int(floor(h * 6.0))
        let f = h * 6.0 - Double(sector)
        let p = v * (1.0 - s)
        let q = v * (1.0 - f * s)
        let t = v * (1.0 - (1.0 - f) * s)

        let (nr, ng, nb): (Double, Double, Double) = switch sector % 6 {
        case 0: (v, t, p)
        case 1: (q, v, p)
        case 2: (p, v, t)
        case 3: (p, q, v)
        case 4: (t, p, v)
        default: (v, p, q)
        }

        let ri = UInt32(min(255, max(0, Int((nr * 255.0).rounded()))))
        let gi = UInt32(min(255, max(0, Int((ng * 255.0).rounded()))))
        let bi = UInt32(min(255, max(0, Int((nb * 255.0).rounded()))))
        return (ri << 16) | (gi << 8) | bi
    }
}

// MARK: - SwiftUI Environment Integration

private struct GokeThemeKey: EnvironmentKey {
    static let defaultValue: GokeTheme = .mint
}

public extension EnvironmentValues {
    var gokeTheme: GokeTheme {
        get { self[GokeThemeKey.self] }
        set { self[GokeThemeKey.self] = newValue }
    }
}

public extension View {
    /// Injects `GokeTheme` into the SwiftUI environment and aligns the system accent tint.
    func gokeTheme(_ theme: GokeTheme) -> some View {
        self
            .environment(\.gokeTheme, theme)
            .tint(theme.accent)
    }
}

// MARK: - CourseProgressStyle (Android CourseProgressStyle.kt Parity)

/// Direction of progressive course/block desaturation wipe.
/// Horizontal for timeline rows (Today / Calendar), vertical for timetable grid blocks.
public enum CourseProgressAxis: Sendable, Hashable {
    case horizontal
    case vertical
}

/// Desaturated paper-tonal progress styling matching Android `CourseProgressStyle.kt`.
public enum CourseProgressStyle {
    /// Linearly interpolates two dynamic SwiftUI colors in sRGB space per trait collection.
    public static func lerp(_ from: Color, _ to: Color, fraction: Double) -> Color {
        let t = CGFloat(min(max(fraction, 0.0), 1.0))
        #if canImport(UIKit)
        return Color(
            uiColor: UIColor { traits in
                let c1 = UIColor(from).resolvedColor(with: traits)
                let c2 = UIColor(to).resolvedColor(with: traits)
                var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
                var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
                c1.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
                c2.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
                return UIColor(
                    red: r1 + (r2 - r1) * t,
                    green: g1 + (g2 - g1) * t,
                    blue: b1 + (b2 - b1) * t,
                    alpha: a1 + (a2 - a1) * t
                )
            }
        )
        #else
        return t < 0.5 ? from : to
        #endif
    }

    /// Linearly interpolates two dynamic SwiftUI colors in sRGB space per trait collection,
    /// supporting distinct blend fractions in light vs dark mode.
    public static func lerp(
        _ from: Color,
        _ to: Color,
        lightFraction: Double,
        darkFraction: Double,
        lightAlpha: Double = 1.0,
        darkAlpha: Double = 1.0
    ) -> Color {
        let lt = CGFloat(min(max(lightFraction, 0.0), 1.0))
        let dt = CGFloat(min(max(darkFraction, 0.0), 1.0))
        let la = CGFloat(min(max(lightAlpha, 0.0), 1.0))
        let da = CGFloat(min(max(darkAlpha, 0.0), 1.0))
        #if canImport(UIKit)
        return Color(
            uiColor: UIColor { traits in
                let isDark = traits.userInterfaceStyle == .dark
                let t = isDark ? dt : lt
                let alphaScale = isDark ? da : la
                let c1 = UIColor(from).resolvedColor(with: traits)
                let c2 = UIColor(to).resolvedColor(with: traits)
                var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
                var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
                c1.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
                c2.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
                return UIColor(
                    red: r1 + (r2 - r1) * t,
                    green: g1 + (g2 - g1) * t,
                    blue: b1 + (b2 - b1) * t,
                    alpha: (a1 + (a2 - a1) * t) * alphaScale
                )
            }
        )
        #else
        return lt < 0.5 ? from.opacity(lightAlpha) : to.opacity(lightAlpha)
        #endif
    }

    /// Muted background for elapsed / finished portions.
    /// Light mode uses `0.78` toward `surfaceHighest` (matching Android); dark mode softens desaturation
    /// to `0.58` so elapsed course blocks retain a whisper of their botanical hue instead of washing to charcoal.
    public static func mutedBackground(activeBg: Color, theme: GokeTheme) -> Color {
        lerp(activeBg, theme.surfaceHighest, lightFraction: 0.78, darkFraction: 0.58)
    }

    /// Finished block background: light mode uses `0.72` opacity over `mutedBackground`; dark mode preserves
    /// botanical tint (`0.58` desaturation, `0.84` opacity) so finished classes remain calmly distinguishable.
    public static func finishedBackground(activeBg: Color, theme: GokeTheme) -> Color {
        lerp(
            activeBg,
            theme.surfaceHighest,
            lightFraction: 0.78,
            darkFraction: 0.58,
            lightAlpha: 0.72,
            darkAlpha: 0.84
        )
    }

    /// Foreground ink for a course/block (`lerp(activeFg, muted, 0.76)` in light, `0.64` in dark when `.finished`, else `activeFg`).
    public static func foreground(activeFg: Color, phase: TimePhase, theme: GokeTheme) -> Color {
        if phase.isFinished {
            return lerp(activeFg, theme.muted, lightFraction: 0.76, darkFraction: 0.64)
        }
        return activeFg
    }
}

/// Progressive desaturation background view matching Android `Modifier.courseProgressBackground`.
/// - `.upcoming`: full botanical `activeBg`.
/// - `.finished`: `CourseProgressStyle.finishedBackground(activeBg:theme:)`.
/// - `.ongoing`:
///   - `.vertical`: crisp split at `splitY` (`mutedBg` above, `activeBg` below) aligning pixel-exact with now-bar.
///   - `.horizontal`: `mutedBg` on `[0, splitX]` with a subtle `8pt` horizontal feather into `activeBg`.
public struct CourseProgressBackground: View {
    private let activeBg: Color
    private let phase: TimePhase
    private let axis: CourseProgressAxis

    @Environment(\.gokeTheme) private var theme

    public init(
        activeBg: Color,
        phase: TimePhase,
        axis: CourseProgressAxis
    ) {
        self.activeBg = activeBg
        self.phase = phase
        self.axis = axis
    }

    public var body: some View {
        let mutedBg = CourseProgressStyle.mutedBackground(activeBg: activeBg, theme: theme)
        switch phase {
        case .upcoming:
            activeBg
        case .finished:
            CourseProgressStyle.finishedBackground(activeBg: activeBg, theme: theme)
        case .ongoing(let rawProgress):
            let p = min(max(rawProgress, 0.0), 1.0)
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(activeBg))
                switch axis {
                case .vertical:
                    let splitY = size.height * p
                    if splitY > 0 {
                        context.fill(
                            Path(CGRect(x: 0, y: 0, width: size.width, height: splitY)),
                            with: .color(mutedBg)
                        )
                    }
                case .horizontal:
                    let splitX = size.width * p
                    if splitX > 0 {
                        context.fill(
                            Path(CGRect(x: 0, y: 0, width: splitX, height: size.height)),
                            with: .color(mutedBg)
                        )
                        let blendW = min(8.0, max(0.0, size.width - splitX))
                        if blendW > 0.5 {
                            let gradRect = CGRect(x: splitX, y: 0, width: blendW, height: size.height)
                            context.fill(
                                Path(gradRect),
                                with: .linearGradient(
                                    Gradient(colors: [mutedBg, mutedBg.opacity(0.0)]),
                                    startPoint: CGPoint(x: splitX, y: 0),
                                    endPoint: CGPoint(x: splitX + blendW, y: 0)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
