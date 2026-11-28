import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Token Highlight Palette (1:1 Android QuickAddBar.kt Parity)

/// Shared Android-exact token color mapping (`QuickAddBar.kt:1268-1276`).
enum GokeTokenHighlightPalette {
    static let pillAlpha: CGFloat = 0.12

    static func color(
        for kind: QuickTokenKind,
        priority: Int,
        remindPersistent: Bool,
        theme: GokeTheme
    ) -> Color {
        switch kind {
        case .date, .time, .course:
            return theme.accent
        case .priority:
            return theme.priorityColor(priority) ?? theme.priorityMedium
        case .list:
            return theme.tertiary
        case .tag:
            return theme.secondary
        case .remind:
            return remindPersistent ? theme.error : theme.accent
        }
    }
}

/// One-shot cursor positioning request produced by `QuickAddParser.rewriteQuickField` / `toggleTag`.
struct GokeCursorRequest: Equatable, Sendable {
    let id: UUID
    let utf16Offset: Int

    init(id: UUID = UUID(), utf16Offset: Int) {
        self.id = id
        self.utf16Offset = utf16Offset
    }
}

#if canImport(UIKit)
private extension NSAttributedString.Key {
    static let gokeTokenPillColor = NSAttributedString.Key("app.goke.tokenPillColor")
}

/// TextKit 1 `NSLayoutManager` drawing subtle 12%-alpha rounded pills behind recognized quick-add tokens.
private final class TokenPillLayoutManager: NSLayoutManager {
    override func drawBackground(forGlyphRange glyphsToShow: NSRange, at origin: CGPoint) {
        super.drawBackground(forGlyphRange: glyphsToShow, at: origin)

        guard let storage = textStorage, let ctx = UIGraphicsGetCurrentContext() else {
            return
        }
        let charRange = characterRange(forGlyphRange: glyphsToShow, actualGlyphRange: nil)
        guard charRange.length > 0 else { return }

        ctx.saveGState()
        storage.enumerateAttribute(.gokeTokenPillColor, in: charRange, options: []) { value, range, _ in
            guard let pillColor = value as? UIColor else { return }
            let glyphRange = self.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
            guard glyphRange.length > 0 else { return }

            self.enumerateEnclosingRects(
                forGlyphRange: glyphRange,
                withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0),
                in: self.textContainers[0]
            ) { rect, _ in
                var pillRect = rect.offsetBy(dx: origin.x, dy: origin.y)
                pillRect = pillRect.insetBy(dx: -2.0, dy: 1.0)
                if pillRect.origin.x < 0 {
                    let shift = -pillRect.origin.x
                    pillRect.origin.x = 0
                    pillRect.size.width = max(0, pillRect.size.width - shift)
                }
                let path = UIBezierPath(roundedRect: pillRect, cornerRadius: 5.0)
                pillColor.setFill()
                path.fill()
            }
        }
        ctx.restoreGState()
    }
}

/// Auto-growing `UITextView` subclass that hosts a crisp aligned placeholder label.
private final class TokenComposerTextView: UITextView {
    let placeholderLabel: UILabel = {
        let label = UILabel()
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        label.isUserInteractionEnabled = false
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private var placeholderTopConstraint: NSLayoutConstraint?
    private var placeholderLeadingConstraint: NSLayoutConstraint?
    private var placeholderTrailingConstraint: NSLayoutConstraint?
    var pendingFocus: Bool = false

    init() {
        let storage = NSTextStorage()
        let layoutManager = TokenPillLayoutManager()
        let container = NSTextContainer(size: CGSize(width: 0, height: CGFloat.greatestFiniteMagnitude))
        container.widthTracksTextView = true
        container.lineFragmentPadding = 0
        layoutManager.addTextContainer(container)
        storage.addLayoutManager(layoutManager)
        super.init(frame: .zero, textContainer: container)

        backgroundColor = .clear
        // 2pt horizontal inset preserves col-0 `dx: -2` token pill padding inside text view bounds.
        textContainerInset = UIEdgeInsets(top: 6, left: 2, bottom: 10, right: 2)
        isScrollEnabled = false
        adjustsFontForContentSizeCategory = true
        autocorrectionType = .default
        smartDashesType = .no
        smartQuotesType = .no

        addSubview(placeholderLabel)
        let top = placeholderLabel.topAnchor.constraint(equalTo: topAnchor, constant: textContainerInset.top)
        let leading = placeholderLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: textContainerInset.left)
        let trailing = placeholderLabel.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -textContainerInset.right)
        placeholderTopConstraint = top
        placeholderLeadingConstraint = leading
        placeholderTrailingConstraint = trailing
        NSLayoutConstraint.activate([top, leading, trailing])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil, pendingFocus, !isFirstResponder {
            pendingFocus = false
            becomeFirstResponder()
        }
    }

    func updatePlaceholderVisibility() {
        let shouldHide = !text.isEmpty
        if placeholderLabel.isHidden != shouldHide {
            placeholderLabel.isHidden = shouldHide
        }
    }

    func updateInsets(_ insets: UIEdgeInsets) {
        if textContainerInset != insets {
            textContainerInset = insets
            placeholderTopConstraint?.constant = insets.top
            placeholderLeadingConstraint?.constant = insets.left
            placeholderTrailingConstraint?.constant = -insets.right
        }
    }
}

// MARK: - GokeTokenTextView

/// Live inline token-highlighted composer backed by `UITextView`, with strict Chinese IME
/// (`markedTextRange == nil`) guard, programmatic UTF-16 cursor sync, auto-growing height,
/// and Android 1:1 token pill colors.
struct GokeTokenTextView: UIViewRepresentable {
    fileprivate struct AppearanceSignature: Equatable {
        let theme: GokeTheme
        let fontSize: CGFloat
        let fontWeight: UIFont.Weight
        let contentSizeCategory: UIContentSizeCategory
        let returnKeyType: UIReturnKeyType
        let placeholder: String
    }

    fileprivate struct HighlightSignature: Equatable {
        let text: String
        let tokenSpans: [QuickTokenSpan]
        let priority: Int
        let remindPersistent: Bool
        let allowedKinds: Set<QuickTokenKind>?
        let theme: GokeTheme
        let fontSize: CGFloat
        let fontWeight: UIFont.Weight
        let contentSizeCategory: UIContentSizeCategory
        let isStrikethrough: Bool
        let isDimmed: Bool
    }

    @Binding var text: String
    @Binding var isFocused: Bool
    @Binding var cursorRequest: GokeCursorRequest?

    let placeholder: String
    let tokenSpans: [QuickTokenSpan]
    let priority: Int
    let remindPersistent: Bool
    let allowedKinds: Set<QuickTokenKind>?
    let theme: GokeTheme
    let fontSize: CGFloat
    let fontWeight: UIFont.Weight
    let isStrikethrough: Bool
    let isDimmed: Bool
    let minHeight: CGFloat
    let maxLines: Int
    let returnKeyType: UIReturnKeyType
    let onRecomputeSpans: (@MainActor (String) -> [QuickTokenSpan])?
    let onSubmit: (() -> Void)?

    init(
        text: Binding<String>,
        isFocused: Binding<Bool>,
        cursorRequest: Binding<GokeCursorRequest?> = .constant(nil),
        placeholder: String,
        tokenSpans: [QuickTokenSpan],
        priority: Int = 0,
        remindPersistent: Bool = false,
        allowedKinds: Set<QuickTokenKind>? = nil,
        theme: GokeTheme,
        fontSize: CGFloat = 20,
        fontWeight: UIFont.Weight = .regular,
        isStrikethrough: Bool = false,
        isDimmed: Bool = false,
        minHeight: CGFloat = 52,
        maxLines: Int = 4,
        returnKeyType: UIReturnKeyType = .done,
        onRecomputeSpans: (@MainActor (String) -> [QuickTokenSpan])? = nil,
        onSubmit: (() -> Void)? = nil
    ) {
        self._text = text
        self._isFocused = isFocused
        self._cursorRequest = cursorRequest
        self.placeholder = placeholder
        self.tokenSpans = tokenSpans
        self.priority = priority
        self.remindPersistent = remindPersistent
        self.allowedKinds = allowedKinds
        self.theme = theme
        self.fontSize = fontSize
        self.fontWeight = fontWeight
        self.isStrikethrough = isStrikethrough
        self.isDimmed = isDimmed
        self.minHeight = minHeight
        self.maxLines = maxLines
        self.returnKeyType = returnKeyType
        self.onRecomputeSpans = onRecomputeSpans
        self.onSubmit = onSubmit
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UITextView {
        let tv = TokenComposerTextView()
        tv.delegate = context.coordinator
        tv.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        tv.setContentHuggingPriority(.defaultLow, for: .horizontal)
        configureAppearance(tv, coordinator: context.coordinator)
        tv.text = text
        applyHighlights(to: tv, coordinator: context.coordinator, force: true)
        tv.updatePlaceholderVisibility()
        if isFocused {
            tv.pendingFocus = true
            if tv.window != nil, !tv.isFirstResponder {
                tv.pendingFocus = false
                tv.becomeFirstResponder()
            }
        }
        return tv
    }

    func updateUIView(_ uiView: UITextView, context: Context) {
        guard let tv = uiView as? TokenComposerTextView else { return }
        let coordinator = context.coordinator
        coordinator.parent = self
        configureAppearance(tv, coordinator: coordinator)

        let hasPendingCursorRequest = cursorRequest.map { $0.id != coordinator.lastAppliedCursorId } ?? false

        // Commit pending Pinyin/Zhuyin IME composition BEFORE applying an explicit programmatic
        // rewrite (e.g. Priority/Remind/List/Tag/Course toolbar tap), while still guarding normal
        // typing updates against mutating active IME marked text.
        if hasPendingCursorRequest && tv.markedTextRange != nil {
            coordinator.isProgrammaticUpdate = true
            tv.unmarkText()
            coordinator.isProgrammaticUpdate = false
        }

        if hasPendingCursorRequest || tv.markedTextRange == nil {
            let textChangedExternally = tv.text != text
            if textChangedExternally {
                coordinator.isProgrammaticUpdate = true
                tv.text = text
                coordinator.isProgrammaticUpdate = false
                coordinator.lastHighlightSignature = nil
            }

            if let req = cursorRequest, req.id != coordinator.lastAppliedCursorId {
                coordinator.lastAppliedCursorId = req.id
                let utf16Len = (tv.text as NSString).length
                let clamped = min(max(0, req.utf16Offset), utf16Len)
                tv.selectedRange = NSRange(location: clamped, length: 0)
                let reqId = req.id
                let cursorBinding = $cursorRequest
                Task { @MainActor in
                    if cursorBinding.wrappedValue?.id == reqId {
                        cursorBinding.wrappedValue = nil
                    }
                }
            }

            applyHighlights(
                to: tv,
                coordinator: coordinator,
                force: textChangedExternally || hasPendingCursorRequest
            )
        }

        tv.updatePlaceholderVisibility()

        // Sync first responder synchronously so keyboard & sheet rise in one smooth transaction.
        if isFocused {
            if tv.window != nil {
                tv.pendingFocus = false
                if !tv.isFirstResponder {
                    tv.becomeFirstResponder()
                }
            } else {
                tv.pendingFocus = true
            }
        } else {
            tv.pendingFocus = false
            if tv.isFirstResponder {
                tv.resignFirstResponder()
            }
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let fallbackWidth = (uiView.window?.windowScene?.screen.bounds.width ?? 360) - 32
        let width = proposal.width ?? fallbackWidth
        guard width > 0 else { return CGSize(width: 200, height: minHeight) }
        let category = uiView.traitCollection.preferredContentSizeCategory
        let baseFont = context.coordinator.baseFont(size: fontSize, weight: fontWeight, category: category)
        let lineHeight = baseFont.lineHeight
        let maxContentHeight = ceil(lineHeight * CGFloat(max(1, maxLines)))
            + uiView.textContainerInset.top
            + uiView.textContainerInset.bottom
            + 4
        let fitting = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        let shouldScroll = fitting.height > maxContentHeight
        // Never mutate `isScrollEnabled` synchronously inside `sizeThatFits` (avoids mid-layout invalidation jitter).
        if uiView.isScrollEnabled != shouldScroll, context.coordinator.pendingScrollEnabled != shouldScroll {
            context.coordinator.pendingScrollEnabled = shouldScroll
            let coordinator = context.coordinator
            Task { @MainActor in
                guard coordinator.pendingScrollEnabled == shouldScroll else { return }
                coordinator.pendingScrollEnabled = nil
                if uiView.isScrollEnabled != shouldScroll {
                    uiView.isScrollEnabled = shouldScroll
                }
            }
        }
        let clampedHeight = min(max(fitting.height, minHeight), maxContentHeight)
        return CGSize(width: width, height: clampedHeight)
    }

    private func configureAppearance(_ tv: TokenComposerTextView, coordinator: Coordinator) {
        let category = tv.traitCollection.preferredContentSizeCategory
        let signature = AppearanceSignature(
            theme: theme,
            fontSize: fontSize,
            fontWeight: fontWeight,
            contentSizeCategory: category,
            returnKeyType: returnKeyType,
            placeholder: placeholder
        )
        guard signature != coordinator.lastAppearanceSignature else { return }
        coordinator.lastAppearanceSignature = signature

        let baseFont = coordinator.baseFont(size: fontSize, weight: fontWeight, category: category)
        tv.font = baseFont
        tv.tintColor = UIColor(theme.accent)
        tv.returnKeyType = returnKeyType
        tv.accessibilityLabel = placeholder
        tv.placeholderLabel.text = placeholder
        tv.placeholderLabel.font = baseFont
        tv.placeholderLabel.textColor = UIColor(theme.muted.opacity(0.68))
    }

    private func applyHighlights(
        to tv: UITextView,
        coordinator: Coordinator,
        overrideSpans: [QuickTokenSpan]? = nil,
        force: Bool = false
    ) {
        guard tv.markedTextRange == nil else { return }
        let currentText = tv.text ?? ""
        let effectiveSpans = overrideSpans ?? tokenSpans
        let category = tv.traitCollection.preferredContentSizeCategory
        let signature = HighlightSignature(
            text: currentText,
            tokenSpans: effectiveSpans,
            priority: priority,
            remindPersistent: remindPersistent,
            allowedKinds: allowedKinds,
            theme: theme,
            fontSize: fontSize,
            fontWeight: fontWeight,
            contentSizeCategory: category,
            isStrikethrough: isStrikethrough,
            isDimmed: isDimmed
        )
        if !force, signature == coordinator.lastHighlightSignature {
            return
        }
        coordinator.lastHighlightSignature = signature

        let fullLength = (currentText as NSString).length
        let baseFont = coordinator.baseFont(size: fontSize, weight: fontWeight, category: category)
        let tokenFont = coordinator.tokenFont(size: fontSize, category: category)
        let baseColor = UIColor(isDimmed ? theme.muted : theme.ink)

        var baseAttrs: [NSAttributedString.Key: Any] = [
            .font: baseFont,
            .foregroundColor: baseColor,
        ]
        if isStrikethrough {
            baseAttrs[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            baseAttrs[.strikethroughColor] = baseColor
        }

        let savedSelection = tv.selectedRange
        let storage = tv.textStorage

        storage.beginEditing()
        if fullLength > 0 {
            let fullRange = NSRange(location: 0, length: fullLength)
            storage.setAttributes(baseAttrs, range: fullRange)

            for span in effectiveSpans {
                if let allowedKinds, !allowedKinds.contains(span.token.kind) {
                    continue
                }
                let loc = span.utf16Range.lowerBound
                let len = span.utf16Range.count
                guard loc >= 0, len > 0, loc + len <= fullLength else { continue }

                let color = GokeTokenHighlightPalette.color(
                    for: span.token.kind,
                    priority: priority,
                    remindPersistent: remindPersistent,
                    theme: theme
                )
                let uiColor = UIColor(color)
                let nsRange = NSRange(location: loc, length: len)
                var tokenAttrs: [NSAttributedString.Key: Any] = [
                    .font: tokenFont,
                    .foregroundColor: uiColor,
                    .gokeTokenPillColor: uiColor.withAlphaComponent(GokeTokenHighlightPalette.pillAlpha),
                ]
                if isStrikethrough {
                    tokenAttrs[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
                    tokenAttrs[.strikethroughColor] = uiColor
                }
                storage.addAttributes(tokenAttrs, range: nsRange)
            }
        }
        storage.endEditing()

        let clampedLoc = min(max(0, savedSelection.location), fullLength)
        let clampedLen = min(max(0, savedSelection.length), fullLength - clampedLoc)
        let restoredRange = NSRange(location: clampedLoc, length: clampedLen)
        if tv.selectedRange != restoredRange {
            tv.selectedRange = restoredRange
        }
        // Reset typing attributes so characters typed right after a token do not inherit pill highlight.
        tv.typingAttributes = baseAttrs
    }

    // MARK: - Coordinator

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: GokeTokenTextView
        var isProgrammaticUpdate: Bool = false
        var lastAppliedCursorId: UUID?
        var pendingScrollEnabled: Bool?
        fileprivate var lastAppearanceSignature: AppearanceSignature?
        fileprivate var lastHighlightSignature: HighlightSignature?

        private var cachedBaseFontSpec: (CGFloat, UIFont.Weight, UIContentSizeCategory)?
        private var cachedBaseFont: UIFont?
        private var cachedTokenFontSpec: (CGFloat, UIContentSizeCategory)?
        private var cachedTokenFont: UIFont?

        init(parent: GokeTokenTextView) {
            self.parent = parent
        }

        func baseFont(size: CGFloat, weight: UIFont.Weight, category: UIContentSizeCategory) -> UIFont {
            if let spec = cachedBaseFontSpec,
               spec.0 == size,
               spec.1 == weight,
               spec.2 == category,
               let cachedBaseFont {
                return cachedBaseFont
            }
            let textStyle: UIFont.TextStyle = size >= 20 ? .title2 : .body
            let metrics = UIFontMetrics(forTextStyle: textStyle)
            let base = UIFont.systemFont(ofSize: size, weight: weight)
            let scaled = metrics.scaledFont(
                for: base,
                compatibleWith: UITraitCollection(preferredContentSizeCategory: category)
            )
            cachedBaseFontSpec = (size, weight, category)
            cachedBaseFont = scaled
            return scaled
        }

        func tokenFont(size: CGFloat, category: UIContentSizeCategory) -> UIFont {
            if let spec = cachedTokenFontSpec,
               spec.0 == size,
               spec.1 == category,
               let cachedTokenFont {
                return cachedTokenFont
            }
            let textStyle: UIFont.TextStyle = size >= 20 ? .title2 : .body
            let metrics = UIFontMetrics(forTextStyle: textStyle)
            let base = UIFont.systemFont(ofSize: size, weight: .medium)
            let scaled = metrics.scaledFont(
                for: base,
                compatibleWith: UITraitCollection(preferredContentSizeCategory: category)
            )
            cachedTokenFontSpec = (size, category)
            cachedTokenFont = scaled
            return scaled
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            if !parent.isFocused {
                parent.isFocused = true
            }
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            if parent.isFocused {
                parent.isFocused = false
            }
        }

        func textViewDidChange(_ textView: UITextView) {
            guard !isProgrammaticUpdate else { return }
            if let tv = textView as? TokenComposerTextView {
                tv.updatePlaceholderVisibility()
            }
            let updated = textView.text ?? ""
            if parent.text != updated {
                parent.text = updated
            }
            // Recompute spans synchronously before painting so tokens never flicker 1 frame behind.
            if textView.markedTextRange == nil, let recompute = parent.onRecomputeSpans {
                let freshSpans = recompute(updated)
                parent.applyHighlights(to: textView, coordinator: self, overrideSpans: freshSpans)
            }
        }

        func textView(
            _ textView: UITextView,
            shouldChangeTextIn range: NSRange,
            replacementText replacement: String
        ) -> Bool {
            if replacement == "\n",
               textView.markedTextRange == nil,
               let onSubmit = parent.onSubmit {
                onSubmit()
                return false
            }
            return true
        }
    }
}
#endif
