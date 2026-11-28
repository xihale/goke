import SwiftUI
import GokeShared
#if canImport(UIKit)
import UIKit
#endif

/// Visual style for Goke's adaptive Liquid Glass surface adapter.
public enum GokeGlassStyle: Sendable, Equatable {
    case regular
    case clear
    case interactive
}

/// Groups adjacent or stacked floating glass surfaces so they morph fluidly on iOS 26
/// via `GlassEffectContainer`, with a zero-overhead pass-through on iOS 17–25 or degraded mode.
public struct GokeGlassContainer<Content: View>: View {
    private let spacing: CGFloat?
    private let content: Content

    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    public init(
        spacing: CGFloat? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.spacing = spacing
        self.content = content()
    }

    @ViewBuilder
    public var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26, *), !performance.shouldDisableGlassAndBlur, !reduceTransparency {
            GlassEffectContainer(spacing: spacing) {
                content
            }
        } else {
            content
        }
        #else
        content
        #endif
    }
}

/// Single-file iOS 26 Liquid Glass adapter with `.ultraThinMaterial` fallback on iOS 17–25
/// and flat opaque `theme.surfaceHigh` degradation under `GokePerformancePolicy` / Reduce Transparency.
///
/// Uses generic `S: Shape` to avoid `AnyView` / `AnyShape` type erasure overhead
/// during 120Hz ProMotion scrolling and transitions.
public struct GlassSurfaceModifier<S: Shape>: ViewModifier {
    private let shape: S
    private let style: GokeGlassStyle
    private let tint: Color?

    @Environment(\.gokeTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    public init(shape: S, style: GokeGlassStyle = .regular, tint: Color? = nil) {
        self.shape = shape
        self.style = style
        self.tint = tint
    }

    private var useDegradedFlatSurface: Bool {
        performance.shouldDisableGlassAndBlur || reduceTransparency
    }

    @ViewBuilder
    public func body(content: Content) -> some View {
        if useDegradedFlatSurface {
            degradedFlatSurface(content)
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26, *) {
                content.glassEffect(resolvedGlass, in: shape)
            } else {
                fallbackSurface(content)
            }
            #else
            fallbackSurface(content)
            #endif
        }
    }

    #if compiler(>=6.2)
    @available(iOS 26, *)
    private var resolvedGlass: Glass {
        let base: Glass = switch style {
        case .regular: .regular
        case .clear: .clear
        case .interactive: .regular.interactive()
        }
        if let tint {
            return base.tint(tint)
        }
        return base
    }
    #endif

    private var specularRimGradient: LinearGradient {
        if colorScheme == .dark {
            LinearGradient(
                stops: [
                    .init(color: Color.white.opacity(0.26), location: 0.0),
                    .init(color: theme.outlineVariant.opacity(0.32), location: 0.48),
                    .init(color: Color.white.opacity(0.06), location: 1.0),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        } else {
            LinearGradient(
                stops: [
                    .init(color: Color.white.opacity(0.62), location: 0.0),
                    .init(color: theme.outlineVariant.opacity(0.35), location: 0.52),
                    .init(color: theme.outlineVariant.opacity(0.48), location: 1.0),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    private func fallbackSurface(_ content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: shape)
            .background {
                if let tint {
                    shape.fill(tint.opacity(colorScheme == .dark ? 0.16 : 0.12))
                }
            }
            .overlay {
                shape.stroke(specularRimGradient, lineWidth: 0.5)
            }
            .shadow(
                color: Color.black.opacity(colorScheme == .dark ? 0.24 : 0.07),
                radius: 10,
                y: 4
            )
    }

    /// Flat opaque fallback for low-power, thermal throttling, <=3 GB RAM, or Reduce Transparency:
    /// neutral iOS semantic surface with hairline separator and subtle Light Mode lift.
    private func degradedFlatSurface(_ content: Content) -> some View {
        let baseFill = colorScheme == .dark ? theme.tertiarySystemBackground : theme.groupedSurface
        return content
            .background {
                ZStack {
                    shape.fill(baseFill)
                    if let tint {
                        shape.fill(tint.opacity(colorScheme == .dark ? 0.16 : 0.11))
                    }
                }
            }
            .overlay {
                shape.stroke(
                    theme.separator.opacity(colorScheme == .dark ? 0.42 : 0.28),
                    lineWidth: 0.5
                )
            }
            .shadow(
                color: colorScheme == .light && !reduceTransparency ? Color.black.opacity(0.05) : .clear,
                radius: 6,
                y: 2
            )
    }
}

public extension View {
    /// Applies native iOS 26 Liquid Glass (`.glassEffect`) with an `.ultraThinMaterial` fallback on iOS 17–25
    /// and a flat opaque `surfaceHigh` surface when degraded by `GokePerformancePolicy`.
    func gokeGlassSurface<S: Shape>(
        in shape: S,
        style: GokeGlassStyle = .regular,
        tint: Color? = nil
    ) -> some View {
        modifier(GlassSurfaceModifier(shape: shape, style: style, tint: tint))
    }

    /// Applies adaptive Liquid Glass clipped to a full `Capsule` (for floating bars, undo pills, and chips).
    func gokeGlassCapsule(
        style: GokeGlassStyle = .regular,
        tint: Color? = nil
    ) -> some View {
        gokeGlassSurface(in: Capsule(), style: style, tint: tint)
    }

    /// Applies adaptive Liquid Glass clipped to a continuous `RoundedRectangle`.
    func gokeGlassCard(
        cornerRadius: CGFloat = GokeRadius.lg,
        style: GokeGlassStyle = .regular,
        tint: Color? = nil
    ) -> some View {
        gokeGlassSurface(
            in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous),
            style: style,
            tint: tint
        )
    }

    /// Enables native iOS 26 tab bar minimization on downward scroll; no-op on iOS 18–25.
    @ViewBuilder
    func gokeAdaptiveTabBarMinimize() -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
        #else
        self
        #endif
    }
}

/// Centralized zoom transition source modifier backed by `@Environment(\.gokeZoomNamespace)`.
///
/// Automatically clips the matched source surface to a continuous `RoundedRectangle` and
/// disables zoom transitions when `accessibilityReduceMotion` is enabled, `gokePerformance`
/// is degraded, or no namespace is present.
public struct GokeZoomSourceModifier: ViewModifier {
    private let id: String
    private let cornerRadius: CGFloat

    @Environment(\.gokeZoomNamespace) private var zoomNamespace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    public init(id: String, cornerRadius: CGFloat = GokeRadius.md) {
        self.id = id
        self.cornerRadius = cornerRadius
    }

    @ViewBuilder
    public func body(content: Content) -> some View {
        if #available(iOS 18.0, *),
           let zoomNamespace,
           !reduceMotion,
           !performance.shouldDisableZoomTransitions {
            content.matchedTransitionSource(id: id, in: zoomNamespace) { configuration in
                configuration.clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
        } else {
            content
        }
    }
}

public extension View {
    /// Marks this view as a navigation zoom transition source using the active `gokeZoomNamespace`.
    /// Skipped automatically when `accessibilityReduceMotion` is enabled or no namespace is injected.
    func gokeZoomSource(
        id: String,
        cornerRadius: CGFloat = GokeRadius.md
    ) -> some View {
        modifier(GokeZoomSourceModifier(id: id, cornerRadius: cornerRadius))
    }

    /// Applies native modal sheet chrome: system drag indicator, optional presentation detents,
    /// and `GokeRadius.xl` corner radius for custom-detent sheets (leaving full sheets at system geometry).
    @ViewBuilder
    func gokeSheetChrome(
        detents: Set<PresentationDetent> = [],
        dragIndicator: Visibility = .visible
    ) -> some View {
        if detents.isEmpty {
            self
                .presentationDragIndicator(dragIndicator)
        } else {
            self
                .presentationDetents(detents)
                .presentationDragIndicator(dragIndicator)
                .presentationCornerRadius(GokeRadius.xl)
        }
    }

    /// Applies cohesive Mint Paper sheet presentation chrome (`presentationBackground(theme.paper)`,
    /// accent tint, clean drag indicator, and optional detents) so modal sheets never flash stock white/gray.
    func gokePaperSheet(
        _ theme: GokeTheme,
        detents: Set<PresentationDetent> = [],
        dragIndicator: Visibility = .visible
    ) -> some View {
        self
            .gokeSheetChrome(detents: detents, dragIndicator: dragIndicator)
            .presentationBackground(theme.paper)
            .tint(theme.accent)
    }

    /// Environment-backed variant of `gokePaperSheet` reading `@Environment(\.gokeTheme)`.
    func gokePaperSheet(
        detents: Set<PresentationDetent> = [],
        dragIndicator: Visibility = .visible
    ) -> some View {
        modifier(GokePaperSheetPresentationModifier(detents: detents, dragIndicator: dragIndicator))
    }
}

private struct GokePaperSheetPresentationModifier: ViewModifier {
    let detents: Set<PresentationDetent>
    let dragIndicator: Visibility

    @Environment(\.gokeTheme) private var theme

    func body(content: Content) -> some View {
        content.gokePaperSheet(theme, detents: detents, dragIndicator: dragIndicator)
    }
}
