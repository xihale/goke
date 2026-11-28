import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - GokeHorizontalPager (UIKit-Backed Zero-Stall Horizontal Pager)

/// High-responsiveness horizontal pager backed by a custom `UIScrollView` (`isPagingEnabled = true`)
/// with a tiny recycled `UIHostingController` pool (at most 3–4 pages mounted at once).
///
/// Designed specifically to eliminate iOS 17 `TabView(.page)` stickiness on real hardware:
/// 1. **Directional angle gate**: `GokePagingScrollView.gestureRecognizerShouldBegin` claims
///    horizontal-leaning thumb arcs (up to ~49° off horizontal) and immediately yields vertical
///    scrolls (0ms delay) to child or parent vertical `ScrollView`s.
/// 2. **Automatic nested `ScrollView` arbitration**: Wires `verticalScrollView.panGestureRecognizer
///    .require(toFail: pagingScrollView.panGestureRecognizer)` on both ancestor (Calendar) and
///    descendant (Timetable, Today) vertical `UIScrollView`s so slight diagonal swipes never get
///    hijacked by vertical rubber-banding.
/// 3. **Zero `ForEach` structural diffing & zero `.id(...)` teardowns**: Uses a fixed-coordinate
///    virtual scroll canvas (`bounds` for finite ranges, or ±120 silent-recenter virtual span for
///    unbounded day/month/week streams) so rapid consecutive flicks never hit `_PagingUICollectionView`
///    `reloadData()` stalls or mid-deceleration teardown.
/// 4. **Cold-launch Frame 1 fast path**: `preloadNeighbors: false` mounts only the active page on
///    Frame 1, then lazily populates `±1` neighbors once enabled.
struct GokeHorizontalPager<PageContent: View, ReloadToken: Equatable>: View {
    @Binding var selection: Int
    let bounds: ClosedRange<Int>?
    let preloadNeighbors: Bool
    let isScrollEnabled: Bool
    let reloadToken: ReloadToken
    let pageContent: @MainActor (Int) -> PageContent

    init(
        selection: Binding<Int>,
        bounds: ClosedRange<Int>? = nil,
        preloadNeighbors: Bool = true,
        isScrollEnabled: Bool = true,
        reloadToken: ReloadToken,
        @ViewBuilder pageContent: @escaping @MainActor (Int) -> PageContent
    ) {
        self._selection = selection
        self.bounds = bounds
        self.preloadNeighbors = preloadNeighbors
        self.isScrollEnabled = isScrollEnabled
        self.reloadToken = reloadToken
        self.pageContent = pageContent
    }

    var body: some View {
        #if canImport(UIKit)
        GokeHorizontalPagerRepresentable(
            selection: $selection,
            bounds: bounds,
            preloadNeighbors: preloadNeighbors,
            isScrollEnabled: isScrollEnabled,
            reloadToken: reloadToken,
            pageContent: pageContent
        )
        #else
        pageContent(selection)
        #endif
    }
}

#if canImport(UIKit)
private struct GokePagerEnvironment {
    let theme: GokeTheme
    let performance: GokePerformancePolicy
    let zoomNamespace: Namespace.ID?
    let overlayDismissBridge: GokeOverlayDismissBridge?

    var signature: GokePagerEnvironmentSignature {
        GokePagerEnvironmentSignature(
            theme: theme,
            performance: performance,
            zoomNamespace: zoomNamespace,
            hasOverlayBridge: overlayDismissBridge != nil
        )
    }
}

private struct GokePagerEnvironmentSignature: Equatable {
    let theme: GokeTheme
    let performance: GokePerformancePolicy
    let zoomNamespace: Namespace.ID?
    let hasOverlayBridge: Bool
}

private struct GokePagerPageWrapper<PageContent: View>: View {
    let pageIndex: Int
    let content: PageContent
    let environment: GokePagerEnvironment

    var body: some View {
        content
            .id(pageIndex)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .environment(\.gokeTheme, environment.theme)
            .environment(\.gokePerformance, environment.performance)
            .environment(\.gokeZoomNamespace, environment.zoomNamespace)
            .environment(\.gokeOverlayDismissBridge, environment.overlayDismissBridge)
    }
}

private struct GokeHorizontalPagerRepresentable<
    PageContent: View,
    ReloadToken: Equatable
>: UIViewControllerRepresentable {
    @Binding var selection: Int
    let bounds: ClosedRange<Int>?
    let preloadNeighbors: Bool
    let isScrollEnabled: Bool
    let reloadToken: ReloadToken
    let pageContent: @MainActor (Int) -> PageContent

    func makeUIViewController(context: Context) -> GokeHorizontalPagerController<PageContent, ReloadToken> {
        let selectionBinding = $selection
        let controller = GokeHorizontalPagerController<PageContent, ReloadToken>(
            initialSelection: selection,
            bounds: bounds,
            preloadNeighbors: preloadNeighbors,
            isScrollEnabled: isScrollEnabled,
            reloadToken: reloadToken,
            environment: makeEnvironment(from: context.environment),
            pageContent: pageContent,
            onSelectionCommit: { newPage in
                if selectionBinding.wrappedValue != newPage {
                    selectionBinding.wrappedValue = newPage
                }
            }
        )
        return controller
    }

    func updateUIViewController(
        _ uiViewController: GokeHorizontalPagerController<PageContent, ReloadToken>,
        context: Context
    ) {
        let selectionBinding = $selection
        let shouldAnimate = !context.transaction.disablesAnimations && context.transaction.animation != nil
        uiViewController.applyUpdate(
            selection: selection,
            bounds: bounds,
            preloadNeighbors: preloadNeighbors,
            isScrollEnabled: isScrollEnabled,
            reloadToken: reloadToken,
            environment: makeEnvironment(from: context.environment),
            shouldAnimateSelectionChange: shouldAnimate,
            pageContent: pageContent,
            onSelectionCommit: { newPage in
                if selectionBinding.wrappedValue != newPage {
                    selectionBinding.wrappedValue = newPage
                }
            }
        )
    }

    private func makeEnvironment(from env: EnvironmentValues) -> GokePagerEnvironment {
        GokePagerEnvironment(
            theme: env.gokeTheme,
            performance: env.gokePerformance,
            zoomNamespace: env.gokeZoomNamespace,
            overlayDismissBridge: env.gokeOverlayDismissBridge
        )
    }
}

// MARK: - Custom Directional-Priority Paging UIScrollView

@MainActor
private final class GokePagingScrollView: UIScrollView {
    private let wiredScrollViews = NSHashTable<UIScrollView>.weakObjects()
    private var didWireAncestorsForCurrentWindow: Bool = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isPagingEnabled = true
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        isDirectionalLockEnabled = true
        alwaysBounceHorizontal = true
        alwaysBounceVertical = false
        bounces = true
        delaysContentTouches = false
        canCancelContentTouches = true
        contentInsetAdjustmentBehavior = .never
        backgroundColor = .clear
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func touchesShouldCancel(in view: UIView) -> Bool {
        true
    }

    override func didMoveToSuperview() {
        super.didMoveToSuperview()
        didWireAncestorsForCurrentWindow = false
        wireAncestorScrollViews()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        didWireAncestorsForCurrentWindow = false
        if window != nil {
            wireAncestorScrollViews()
        }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if !didWireAncestorsForCurrentWindow, window != nil {
            wireAncestorScrollViews()
        }
        let hit = super.hitTest(point, with: event)
        if let hit {
            var current: UIView? = hit
            while let view = current, view !== self {
                if let sv = view as? UIScrollView {
                    wireFailureRequirement(on: sv)
                }
                current = view.superview
            }
        }
        return hit
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === panGestureRecognizer else {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }
        guard isScrollEnabled else { return false }
        // Always let the user catch an in-flight decelerating page smoothly.
        if isDecelerating {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }

        let translation = panGestureRecognizer.translation(in: self)
        let velocity = panGestureRecognizer.velocity(in: self)
        let tx = abs(translation.x)
        let ty = abs(translation.y)
        let vx = abs(velocity.x)
        let vy = abs(velocity.y)

        let primaryX: CGFloat
        let primaryY: CGFloat
        if max(tx, ty) >= 2.0 {
            primaryX = tx
            primaryY = ty
        } else if max(vx, vy) > 0.01 {
            primaryX = vx
            primaryY = vy
        } else {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }

        // Claim horizontal-leaning thumb arcs (up to ~49° off horizontal);
        // fail immediately for vertical scrolls so vertical ScrollViews respond with 0ms delay.
        guard primaryX > primaryY * 0.85 else {
            return false
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }

    func wireAncestorScrollViews() {
        var ancestor = superview
        while let view = ancestor {
            if let sv = view as? UIScrollView, sv !== self {
                wireFailureRequirement(on: sv)
            }
            ancestor = view.superview
        }
        if window != nil {
            didWireAncestorsForCurrentWindow = true
        }
    }

    func wireDescendantScrollViews(in root: UIView) {
        var stack: [UIView] = [root]
        while let view = stack.popLast() {
            if let sv = view as? UIScrollView, sv !== self {
                wireFailureRequirement(on: sv)
            }
            if !view.subviews.isEmpty {
                stack.append(contentsOf: view.subviews)
            }
        }
    }

    private func wireFailureRequirement(on scrollView: UIScrollView) {
        guard scrollView !== self, !wiredScrollViews.contains(scrollView) else { return }
        let isHorizontalCarousel = scrollView.alwaysBounceHorizontal
            && !scrollView.alwaysBounceVertical
            && scrollView.contentSize.width > scrollView.bounds.width + 1
        guard !isHorizontalCarousel else { return }

        scrollView.panGestureRecognizer.require(toFail: panGestureRecognizer)
        wiredScrollViews.add(scrollView)
    }
}

// MARK: - Virtualized Pager ViewController

@MainActor
private final class GokeHorizontalPagerController<
    PageContent: View,
    ReloadToken: Equatable
>: UIViewController, UIScrollViewDelegate {
    private static var virtualHalfSpan: Int { 120 }
    private static var recenterThreshold: Int { 80 }
    private static var maxRecycledPoolSize: Int { 4 }

    private let scrollView = GokePagingScrollView()

    private var boundsRange: ClosedRange<Int>?
    private var originPage: Int = 0
    private var pageCount: Int = 1
    private var currentPage: Int = 0
    private var preloadNeighbors: Bool
    private var lastReloadToken: ReloadToken
    private var currentEnvironment: GokePagerEnvironment
    private var lastEnvSignature: GokePagerEnvironmentSignature
    private var pageBuilder: @MainActor (Int) -> PageContent
    private var onSelectionCommit: @MainActor (Int) -> Void

    private var lastLayoutSize: CGSize = .zero
    private var isUpdatingFromSwiftUI: Bool = false
    private var isAdjustingOffset: Bool = false

    private var mountedPages: [Int: UIHostingController<GokePagerPageWrapper<PageContent>>] = [:]
    private var recycledPool: [UIHostingController<GokePagerPageWrapper<PageContent>>] = []

    init(
        initialSelection: Int,
        bounds: ClosedRange<Int>?,
        preloadNeighbors: Bool,
        isScrollEnabled: Bool,
        reloadToken: ReloadToken,
        environment: GokePagerEnvironment,
        pageContent: @escaping @MainActor (Int) -> PageContent,
        onSelectionCommit: @escaping @MainActor (Int) -> Void
    ) {
        self.boundsRange = bounds
        self.preloadNeighbors = preloadNeighbors
        self.lastReloadToken = reloadToken
        self.currentEnvironment = environment
        self.lastEnvSignature = environment.signature
        self.pageBuilder = pageContent
        self.onSelectionCommit = onSelectionCommit
        super.init(nibName: nil, bundle: nil)

        let clampedInitial = clampPage(initialSelection, bounds: bounds)
        self.currentPage = clampedInitial
        configureWindow(centeredAt: clampedInitial, bounds: bounds)
        scrollView.isScrollEnabled = isScrollEnabled
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        let container = UIView(frame: .zero)
        container.backgroundColor = .clear
        container.clipsToBounds = true
        self.view = container
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        scrollView.delegate = self
        scrollView.translatesAutoresizingMaskIntoConstraints = true
        scrollView.autoresizingMask = []
        view.addSubview(scrollView)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = view.bounds.size
        guard size.width > 0, size.height > 0 else { return }

        let isFirstLayout = lastLayoutSize.width <= 0
        let widthChanged = abs(size.width - lastLayoutSize.width) > 0.5
        let heightChanged = abs(size.height - lastLayoutSize.height) > 0.5
        guard isFirstLayout || widthChanged || heightChanged || scrollView.frame.size != size else {
            return
        }

        lastLayoutSize = size
        isAdjustingOffset = true
        defer { isAdjustingOffset = false }

        scrollView.frame = CGRect(origin: .zero, size: size)
        scrollView.contentSize = CGSize(
            width: CGFloat(pageCount) * size.width,
            height: size.height
        )

        for (page, hc) in mountedPages {
            hc.view.frame = CGRect(
                x: xForPage(page, width: size.width),
                y: 0,
                width: size.width,
                height: size.height
            )
        }

        // Only snap contentOffset.x on initial layout or width change (e.g. rotation),
        // never on height-only animations (e.g. Calendar 5-row <-> 6-row transition).
        if isFirstLayout || widthChanged {
            scrollView.contentOffset = CGPoint(
                x: xForPage(currentPage, width: size.width),
                y: 0
            )
        }

        syncMountedPages(
            desired: restingDesiredPages(around: currentPage),
            forceReloadExisting: false
        )
        scrollView.wireAncestorScrollViews()
    }

    func applyUpdate(
        selection: Int,
        bounds: ClosedRange<Int>?,
        preloadNeighbors newPreloadNeighbors: Bool,
        isScrollEnabled: Bool,
        reloadToken: ReloadToken,
        environment: GokePagerEnvironment,
        shouldAnimateSelectionChange: Bool,
        pageContent: @escaping @MainActor (Int) -> PageContent,
        onSelectionCommit: @escaping @MainActor (Int) -> Void
    ) {
        isUpdatingFromSwiftUI = true
        defer { isUpdatingFromSwiftUI = false }

        self.pageBuilder = pageContent
        self.onSelectionCommit = onSelectionCommit
        self.currentEnvironment = environment

        if scrollView.isScrollEnabled != isScrollEnabled {
            scrollView.isScrollEnabled = isScrollEnabled
        }

        let boundsChanged = boundsRange != bounds
        let targetPage = clampPage(selection, bounds: bounds)
        let preloadChanged = preloadNeighbors != newPreloadNeighbors
        preloadNeighbors = newPreloadNeighbors

        let envSignature = environment.signature
        let contentNeedsReload = (lastReloadToken != reloadToken)
            || (lastEnvSignature != envSignature)
            || boundsChanged
        lastReloadToken = reloadToken
        lastEnvSignature = envSignature

        if boundsChanged {
            boundsRange = bounds
            configureWindow(centeredAt: targetPage, bounds: bounds)
            currentPage = targetPage
            applyContentSizeAndOffset(to: targetPage, animated: false)
            syncMountedPages(
                desired: restingDesiredPages(around: targetPage),
                forceReloadExisting: true
            )
            return
        }

        if targetPage != currentPage {
            let previousPage = currentPage
            let canSmoothSlide = shouldAnimateSelectionChange
                && lastLayoutSize.width > 0
                && validWindowRange.contains(targetPage)
                && abs(targetPage - previousPage) <= 3

            if canSmoothSlide {
                currentPage = targetPage
                var desired = restingDesiredPages(around: targetPage)
                desired.insert(previousPage)
                syncMountedPages(desired: desired, forceReloadExisting: contentNeedsReload)
                scrollView.setContentOffset(
                    CGPoint(x: xForPage(targetPage, width: lastLayoutSize.width), y: 0),
                    animated: true
                )
            } else {
                if boundsRange == nil,
                   !validWindowRange.contains(targetPage)
                    || abs(targetPage - (originPage + Self.virtualHalfSpan)) >= Self.recenterThreshold
                {
                    configureWindow(centeredAt: targetPage, bounds: nil)
                }
                currentPage = targetPage
                applyContentSizeAndOffset(to: targetPage, animated: false)
                syncMountedPages(
                    desired: restingDesiredPages(around: targetPage),
                    forceReloadExisting: contentNeedsReload
                )
            }
            return
        }

        if preloadChanged || contentNeedsReload {
            if scrollView.isDragging || scrollView.isDecelerating {
                if contentNeedsReload {
                    reloadAllMountedPages()
                }
            } else {
                syncMountedPages(
                    desired: restingDesiredPages(around: currentPage),
                    forceReloadExisting: contentNeedsReload
                )
            }
        }
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard !isAdjustingOffset, lastLayoutSize.width > 0 else { return }
        let width = lastLayoutSize.width
        let offsetX = scrollView.contentOffset.x

        let minSlot = Int(floor(offsetX / width))
        let maxSlot = Int(ceil(offsetX / width))
        let radius = preloadNeighbors ? 1 : 0
        let low = max(validWindowRange.lowerBound, originPage + minSlot - radius)
        let high = min(validWindowRange.upperBound, originPage + maxSlot + radius)
        guard low <= high else { return }

        // Keep pages in `low...high` mounted while preserving a 1-page hysteresis margin during rapid flicks.
        let keepLow = max(validWindowRange.lowerBound, low - 1)
        let keepHigh = min(validWindowRange.upperBound, high + 1)
        let pagesToRecycle = mountedPages.keys.filter { $0 < keepLow || $0 > keepHigh }
        for page in pagesToRecycle {
            recyclePage(page)
        }
        for page in low...high where mountedPages[page] == nil {
            mountPage(page)
        }
    }

    func scrollViewWillEndDragging(
        _ scrollView: UIScrollView,
        withVelocity velocity: CGPoint,
        targetContentOffset: UnsafeMutablePointer<CGPoint>
    ) {
        guard lastLayoutSize.width > 0 else { return }
        let targetPage = pageForOffsetX(targetContentOffset.pointee.x, width: lastLayoutSize.width)
        if targetPage != currentPage {
            currentPage = targetPage
            notifySelectionCommit(targetPage)
        }
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            finishSettle()
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        finishSettle()
    }

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        finishSettle()
    }

    // MARK: - Settle & Silent Idle Recenter

    private func finishSettle() {
        guard lastLayoutSize.width > 0 else { return }
        let settled = pageForOffsetX(scrollView.contentOffset.x, width: lastLayoutSize.width)
        if settled != currentPage {
            currentPage = settled
            notifySelectionCommit(settled)
        }
        syncMountedPages(
            desired: restingDesiredPages(around: currentPage),
            forceReloadExisting: false
        )

        if boundsRange == nil,
           !scrollView.isDragging,
           !scrollView.isTracking,
           !scrollView.isDecelerating
        {
            let virtualCenter = originPage + Self.virtualHalfSpan
            if abs(currentPage - virtualCenter) >= Self.recenterThreshold {
                recenterVirtualWindowSilently(around: currentPage)
            }
        }
    }

    private func recenterVirtualWindowSilently(around page: Int) {
        guard lastLayoutSize.width > 0 else { return }
        let width = lastLayoutSize.width
        let height = lastLayoutSize.height

        UIView.performWithoutAnimation {
            isAdjustingOffset = true
            defer { isAdjustingOffset = false }

            originPage = page - Self.virtualHalfSpan
            pageCount = Self.virtualHalfSpan * 2 + 1
            scrollView.contentSize = CGSize(width: CGFloat(pageCount) * width, height: height)
            for (mountedPage, hc) in mountedPages {
                hc.view.frame = CGRect(
                    x: xForPage(mountedPage, width: width),
                    y: 0,
                    width: width,
                    height: height
                )
            }
            scrollView.contentOffset = CGPoint(x: xForPage(page, width: width), y: 0)
        }
    }

    // MARK: - Window & Coordinate Helpers

    private var validWindowRange: ClosedRange<Int> {
        originPage...(originPage + max(1, pageCount) - 1)
    }

    private func clampPage(_ page: Int, bounds: ClosedRange<Int>?) -> Int {
        guard let bounds else { return page }
        return min(max(bounds.lowerBound, page), bounds.upperBound)
    }

    private func configureWindow(centeredAt page: Int, bounds: ClosedRange<Int>?) {
        if let bounds {
            originPage = bounds.lowerBound
            pageCount = max(1, bounds.upperBound - bounds.lowerBound + 1)
        } else {
            originPage = page - Self.virtualHalfSpan
            pageCount = Self.virtualHalfSpan * 2 + 1
        }
    }

    private func xForPage(_ page: Int, width: CGFloat) -> CGFloat {
        CGFloat(page - originPage) * width
    }

    private func pageForOffsetX(_ offsetX: CGFloat, width: CGFloat) -> Int {
        guard width > 0 else { return currentPage }
        let rawSlot = Int((offsetX / width).rounded())
        let rawPage = originPage + rawSlot
        return min(max(validWindowRange.lowerBound, rawPage), validWindowRange.upperBound)
    }

    private func restingDesiredPages(around center: Int) -> Set<Int> {
        let radius = preloadNeighbors ? 1 : 0
        let low = max(validWindowRange.lowerBound, center - radius)
        let high = min(validWindowRange.upperBound, center + radius)
        guard low <= high else { return [center] }
        return Set(low...high)
    }

    private func applyContentSizeAndOffset(to page: Int, animated: Bool) {
        guard lastLayoutSize.width > 0 else { return }
        isAdjustingOffset = true
        defer { isAdjustingOffset = false }
        scrollView.contentSize = CGSize(
            width: CGFloat(pageCount) * lastLayoutSize.width,
            height: lastLayoutSize.height
        )
        scrollView.setContentOffset(
            CGPoint(x: xForPage(page, width: lastLayoutSize.width), y: 0),
            animated: animated
        )
    }

    private func notifySelectionCommit(_ page: Int) {
        guard !isUpdatingFromSwiftUI else { return }
        onSelectionCommit(page)
    }

    // MARK: - Hosting Controller Pool Management

    private func syncMountedPages(desired: Set<Int>, forceReloadExisting: Bool) {
        guard lastLayoutSize.width > 0 else { return }

        let toRecycle = mountedPages.keys.filter { !desired.contains($0) }
        for page in toRecycle {
            recyclePage(page)
        }

        if forceReloadExisting {
            for (page, hc) in mountedPages {
                hc.rootView = makeWrappedView(for: page)
                hc.view.frame = CGRect(
                    x: xForPage(page, width: lastLayoutSize.width),
                    y: 0,
                    width: lastLayoutSize.width,
                    height: lastLayoutSize.height
                )
                scrollView.wireDescendantScrollViews(in: hc.view)
            }
        }

        for page in desired.sorted() where mountedPages[page] == nil {
            mountPage(page)
        }
    }

    private func reloadAllMountedPages() {
        for (page, hc) in mountedPages {
            hc.rootView = makeWrappedView(for: page)
            scrollView.wireDescendantScrollViews(in: hc.view)
        }
    }

    private func mountPage(_ page: Int) {
        guard lastLayoutSize.width > 0, validWindowRange.contains(page) else { return }
        let wrapped = makeWrappedView(for: page)
        let hc: UIHostingController<GokePagerPageWrapper<PageContent>>
        if let reused = recycledPool.popLast() {
            hc = reused
            hc.rootView = wrapped
            hc.view.isHidden = false
        } else {
            let fresh = UIHostingController(rootView: wrapped)
            fresh.sizingOptions = []
            fresh.safeAreaRegions = []
            fresh.view.backgroundColor = .clear
            fresh.view.clipsToBounds = true
            fresh.view.translatesAutoresizingMaskIntoConstraints = true
            fresh.view.autoresizingMask = []
            addChild(fresh)
            scrollView.addSubview(fresh.view)
            fresh.didMove(toParent: self)
            hc = fresh
        }

        hc.view.frame = CGRect(
            x: xForPage(page, width: lastLayoutSize.width),
            y: 0,
            width: lastLayoutSize.width,
            height: lastLayoutSize.height
        )
        hc.view.layoutIfNeeded()
        scrollView.wireDescendantScrollViews(in: hc.view)
        mountedPages[page] = hc
    }

    private func recyclePage(_ page: Int) {
        guard let hc = mountedPages.removeValue(forKey: page) else { return }
        if recycledPool.count < Self.maxRecycledPoolSize {
            hc.view.isHidden = true
            recycledPool.append(hc)
        } else {
            hc.willMove(toParent: nil)
            hc.view.removeFromSuperview()
            hc.removeFromParent()
        }
    }

    private func makeWrappedView(for page: Int) -> GokePagerPageWrapper<PageContent> {
        GokePagerPageWrapper(
            pageIndex: page,
            content: pageBuilder(page),
            environment: currentEnvironment
        )
    }
}
#endif
