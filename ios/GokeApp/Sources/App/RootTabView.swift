import GokeCore
import SwiftUI
import GokeShared
#if canImport(UIKit)
import UIKit
#endif

// MARK: - RootTabView

/// 5-tab native SwiftUI root shell (`Today`, `Timetable`, `Calendar`, `Todo`, `Me`) with
/// per-tab `NavigationStack`, global `UndoToastBanner` overlay, and unified sheet host.
struct RootTabView: View {
    private let screens: GokeRootScreensWiring

    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Tracks tabs that have been mounted at least once so cold launch only instantiates
    /// the active tab (`.timetable` by default) instead of constructing all 5 root tabs upfront.
    @State private var visitedTabs: Set<GokeTab> = [.timetable]
    @State private var updateManager = AppUpdateManager.shared

    init(screens: GokeRootScreensWiring = GokeRootScreensWiring()) {
        self.screens = screens
    }

    private func isTabMounted(_ tab: GokeTab) -> Bool {
        visitedTabs.contains(tab) || router.selectedTab == tab || !router.path(for: tab).isEmpty
    }

    private var activeTabHasFloatingFab: Bool {
        guard router.activeQuickAddSheet == nil else { return false }
        guard router.path(for: router.selectedTab).isEmpty else { return false }
        switch router.selectedTab {
        case .today, .calendar, .todo:
            return true
        case .timetable, .me:
            return false
        }
    }

    private var toastBottomPadding: CGFloat {
        if router.activeQuickAddSheet != nil {
            return 16
        }
        if activeTabHasFloatingFab {
            // Docks calmly bottom-leading beside the 56pt bottom-right GokeMintFab on the same baseline.
            return 70
        }
        return router.path(for: router.selectedTab).isEmpty ? 64 : 16
    }

    private var tabSelectionBinding: Binding<GokeTab> {
        Binding(
            get: { router.selectedTab },
            set: { newValue in
                visitedTabs.insert(newValue)
                if newValue == router.selectedTab {
                    router.handleTabReselect(newValue)
                } else {
                    router.selectedTab = newValue
                }
            }
        )
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: tabSelectionBinding) {
                GokeTabNavigationHost(
                    path: router.pathBinding(for: .today),
                    isSelectedTab: router.selectedTab == .today,
                    isMounted: isTabMounted(.today),
                    tabBarPaper: theme.paper
                ) {
                    screens.makeTodayView()
                }
                .tabItem {
                    Label(GokeTab.today.title, systemImage: GokeTab.today.systemImage)
                }
                .tag(GokeTab.today)

                GokeTabNavigationHost(
                    path: router.pathBinding(for: .timetable),
                    isSelectedTab: router.selectedTab == .timetable,
                    isMounted: isTabMounted(.timetable),
                    tabBarPaper: theme.paper
                ) {
                    screens.makeTimetableView()
                }
                .tabItem {
                    Label(GokeTab.timetable.title, systemImage: GokeTab.timetable.systemImage)
                }
                .tag(GokeTab.timetable)

                GokeTabNavigationHost(
                    path: router.pathBinding(for: .calendar),
                    isSelectedTab: router.selectedTab == .calendar,
                    isMounted: isTabMounted(.calendar),
                    tabBarPaper: theme.paper
                ) {
                    screens.makeCalendarView()
                }
                .tabItem {
                    Label(GokeTab.calendar.title, systemImage: GokeTab.calendar.systemImage)
                }
                .tag(GokeTab.calendar)

                GokeTabNavigationHost(
                    path: router.pathBinding(for: .todo),
                    isSelectedTab: router.selectedTab == .todo,
                    isMounted: isTabMounted(.todo),
                    tabBarPaper: theme.paper
                ) {
                    screens.makeTodoView()
                }
                .tabItem {
                    Label(GokeTab.todo.title, systemImage: GokeTab.todo.systemImage)
                }
                .tag(GokeTab.todo)

                GokeTabNavigationHost(
                    path: router.pathBinding(for: .me),
                    isSelectedTab: router.selectedTab == .me,
                    isMounted: isTabMounted(.me),
                    tabBarPaper: theme.paper
                ) {
                    screens.makeMeView()
                }
                .tabItem {
                    Label(GokeTab.me.title, systemImage: GokeTab.me.systemImage)
                }
                .tag(GokeTab.me)
            }
            .tint(theme.accent)
            .gokeAdaptiveTabBarMinimize()
            // Attached to the TabView sibling (NOT the enclosing ZStack) so modal sheets never shadow
            // inner sheets presented from the sibling GokeOverlaySheetContainer (QuickAdd).
            .sheet(item: router.modalSheetBinding) { sheet in
                let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
                ZStack(alignment: .bottom) {
                    screens.makeSheetContent(for: sheet)
                        .tint(theme.accent)

                    GokeGlassContainer(spacing: GokeSpacing.sm) {
                        if let toast = actions.toast {
                            UndoToastBanner(
                                message: toast.message,
                                actionTitle: toast.canUndo ? toast.actionTitle : nil,
                                duration: 4.5,
                                onAction: toast.canUndo ? {
                                    let opId = toast.opId
                                    Task {
                                        _ = await actions.undo(opId: opId)
                                    }
                                } : nil,
                                onDismiss: {
                                    actions.dismissToast()
                                }
                            )
                            .id(toast.id)
                            .padding(.horizontal, GokeSpacing.lg)
                            .padding(.bottom, 16)
                            .transition(UndoToastBanner.transition(reduceMotion: simplifyMotion))
                        }
                    }
                    .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: actions.toast?.id)
                    .zIndex(20)
                }
                .gokePaperSheet(theme)
            }

            // Keyboard-docked QuickAdd overlay (item-retaining: stays rendered through crisp 0.16s exit)
            GokeOverlaySheetContainer(
                item: router.activeQuickAddSheet,
                scrimAlpha: 0.22,
                cornerRadius: GokeRadius.lg,
                onDismiss: {
                    router.dismissSheet()
                }
            ) { quickAddSheet in
                screens.makeSheetContent(for: quickAddSheet)
                    .tint(theme.accent)
            }
            .zIndex(30)

            let simplifyMotion = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
            let dockLeadingBesideFab = activeTabHasFloatingFab
            GokeGlassContainer(spacing: GokeSpacing.sm) {
                if let toast = actions.toast, router.modalSheetBinding.wrappedValue == nil {
                    UndoToastBanner(
                        message: toast.message,
                        actionTitle: toast.canUndo ? toast.actionTitle : nil,
                        duration: 4.5,
                        onAction: toast.canUndo ? {
                            let opId = toast.opId
                            Task {
                                _ = await actions.undo(opId: opId)
                            }
                        } : nil,
                        onDismiss: {
                            actions.dismissToast()
                        }
                    )
                    .id(toast.id)
                    .frame(maxWidth: .infinity, alignment: dockLeadingBesideFab ? .leading : .center)
                    .padding(.leading, dockLeadingBesideFab ? 16 : GokeSpacing.lg)
                    .padding(.trailing, dockLeadingBesideFab ? 88 : GokeSpacing.lg)
                    .padding(.bottom, toastBottomPadding)
                    .transition(UndoToastBanner.transition(reduceMotion: simplifyMotion))
                }
            }
            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: actions.toast?.id)
            .zIndex(40)
        }
        .onAppear {
            visitedTabs.insert(router.selectedTab)
            wireFeedbackPreferences()
        }
        .task {
            await updateManager.checkOnLaunchIfNeeded()
        }
        .sheet(isPresented: $updateManager.isSheetPresented) {
            if let update = updateManager.availableUpdate {
                AppUpdateSheet(
                    update: update,
                    currentVersion: updateManager.currentVersion,
                    isAltStoreInstalled: updateManager.isAltStoreInstalled,
                    onIgnore: {
                        updateManager.ignoreCurrentUpdate()
                    },
                    onDismiss: {
                        updateManager.dismissSheet()
                    }
                )
                .gokePaperSheet(theme, detents: [.medium, .large])
            }
        }
        .onChange(of: router.selectedTab) { _, newTab in
            visitedTabs.insert(newTab)
        }
        .onChange(of: settingsStore.settings.hapticFeedback) { _, _ in
            wireFeedbackPreferences()
        }
        .onChange(of: settingsStore.settings.soundFeedback) { oldValue, newValue in
            wireFeedbackPreferences()
            if !oldValue && newValue {
                GokeFeedback.previewSound()
            }
        }
        .onChange(of: theme) { _, newTheme in
            #if canImport(UIKit)
            GokeChromeAppearance.apply(theme: newTheme, colorScheme: colorScheme)
            #endif
        }
        .onChange(of: colorScheme) { _, newScheme in
            #if canImport(UIKit)
            GokeChromeAppearance.apply(theme: theme, colorScheme: newScheme)
            #endif
        }
    }

    private func wireFeedbackPreferences() {
        let store = settingsStore
        GokeFeedback.isEnabledProvider = { [weak store] in
            store?.settings.hapticFeedback ?? true
        }
        GokeFeedback.isSoundEnabledProvider = { [weak store] in
            store?.settings.soundFeedback ?? true
        }
    }
}

/// Wraps each tab's `NavigationStack`, tinting the root tab bar on Mint Paper and hiding it on pushed routes.
/// Unvisited tabs render a lightweight Mint Paper placeholder until first selection or deep-link routing.
private struct GokeTabNavigationHost<Root: View>: View {
    @Binding var path: NavigationPath
    let isSelectedTab: Bool
    let isMounted: Bool
    let tabBarPaper: Color
    @ViewBuilder let root: () -> Root

    @Namespace private var zoomNamespace

    private var shouldHideTabBar: Bool { !path.isEmpty }

    var body: some View {
        // Keep the lightweight NavigationStack shell mounted per tab so cold deep-links onto
        // unvisited tabs never race stack creation with NavigationPath population or tab-bar hiding,
        // while still deferring heavy root tab content construction until first visit.
        NavigationStack(path: $path) {
            Group {
                if isMounted {
                    root()
                } else {
                    tabBarPaper
                        .ignoresSafeArea()
                }
            }
            .toolbar(shouldHideTabBar ? .hidden : .visible, for: .tabBar)
            .toolbarBackground(tabBarPaper, for: .tabBar)
            .toolbarBackground(shouldHideTabBar ? .hidden : .visible, for: .tabBar)
            .navigationDestination(for: GokeRoute.self) { route in
                GokeRouteDestinationView(route: route)
                    .toolbar(.hidden, for: .tabBar)
                    .toolbarBackground(.hidden, for: .tabBar)
                    .toolbarBackground(tabBarPaper, for: .navigationBar)
            }
        }
        .toolbar(shouldHideTabBar ? .hidden : .visible, for: .tabBar)
        #if canImport(UIKit)
        .background(
            GokeTabBarVisibilityBridge(
                isActiveTab: isSelectedTab,
                isTabBarHidden: shouldHideTabBar
            )
            .allowsHitTesting(false)
        )
        #endif
        .environment(\.gokeZoomNamespace, zoomNamespace)
    }
}

#if canImport(UIKit)
/// Guarantees `UITabBar.isHidden` tracks pushed `NavigationPath` state on iOS 17.x where
/// SwiftUI's `.toolbar(.hidden, for: .tabBar)` can be ignored during programmatic pushes.
private struct GokeTabBarVisibilityBridge: UIViewControllerRepresentable {
    let isActiveTab: Bool
    let isTabBarHidden: Bool

    func makeUIViewController(context: Context) -> TabBarVisibilityController {
        let controller = TabBarVisibilityController()
        controller.update(isActiveTab: isActiveTab, isTabBarHidden: isTabBarHidden)
        return controller
    }

    func updateUIViewController(_ uiViewController: TabBarVisibilityController, context: Context) {
        uiViewController.update(isActiveTab: isActiveTab, isTabBarHidden: isTabBarHidden)
    }

    final class TabBarVisibilityController: UIViewController {
        private var isActiveTab: Bool = false
        private var isTabBarHidden: Bool = false
        private var hasPendingAsyncApply: Bool = false
        private weak var cachedTabBarController: UITabBarController?

        override func loadView() {
            let v = UIView(frame: .zero)
            v.isUserInteractionEnabled = false
            v.backgroundColor = .clear
            self.view = v
        }

        func update(isActiveTab: Bool, isTabBarHidden: Bool) {
            let stateChanged = self.isActiveTab != isActiveTab || self.isTabBarHidden != isTabBarHidden
            self.isActiveTab = isActiveTab
            self.isTabBarHidden = isTabBarHidden

            guard isActiveTab else { return }

            if let tbc = resolvedTabBarController() {
                let needsChange = tbc.tabBar.isHidden != isTabBarHidden
                if needsChange {
                    tbc.tabBar.isHidden = isTabBarHidden
                }
                guard stateChanged || needsChange else { return }
            } else if !stateChanged {
                return
            }

            if !hasPendingAsyncApply {
                hasPendingAsyncApply = true
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    self.hasPendingAsyncApply = false
                    self.applyIfActive()
                }
            }
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            cachedTabBarController = nil
            applyIfActive()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            applyIfActive()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            applyIfActive()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            applyIfActive()
        }

        private func applyIfActive() {
            guard isActiveTab else { return }
            guard let tbc = resolvedTabBarController() else { return }
            if tbc.tabBar.isHidden != isTabBarHidden {
                tbc.tabBar.isHidden = isTabBarHidden
            }
        }

        private func resolvedTabBarController() -> UITabBarController? {
            if let cached = cachedTabBarController {
                return cached
            }
            if let direct = tabBarController {
                cachedTabBarController = direct
                return direct
            }
            var current: UIViewController? = parent
            while let vc = current {
                if let tbc = vc as? UITabBarController {
                    cachedTabBarController = tbc
                    return tbc
                }
                if let tbc = vc.tabBarController {
                    cachedTabBarController = tbc
                    return tbc
                }
                current = vc.parent
            }
            if let root = view.window?.rootViewController,
               let found = Self.findTabBarController(in: root) {
                cachedTabBarController = found
                return found
            }
            return nil
        }

        private static func findTabBarController(in vc: UIViewController) -> UITabBarController? {
            if let tbc = vc as? UITabBarController {
                return tbc
            }
            for child in vc.children {
                if let found = findTabBarController(in: child) {
                    return found
                }
            }
            return nil
        }
    }
}

// MARK: - Reactive Global Navigation & Tab Bar Chrome

/// Configures global `UINavigationBarAppearance` and `UITabBarAppearance` from `GokeTheme`,
/// updating both appearance proxies and already-mounted controllers so pushed nav bars stay warm
/// Mint Paper (`theme.paper`, `theme.ink`, `theme.accent`) instead of harsh `#000000`/`#FFFFFF` or grey blur.
@MainActor
enum GokeChromeAppearance {
    private struct AppliedSignature: Equatable {
        let theme: GokeTheme
        let colorScheme: ColorScheme?
        let appliedLiveChrome: Bool
    }

    private static var lastApplied: AppliedSignature?

    /// Launch-time fast path: configures global `UIAppearance` proxies once before any bar mounts,
    /// skipping live `UIWindowScene` / `UIViewController` traversal.
    static func applyProxies(theme: GokeTheme) {
        apply(theme: theme, colorScheme: nil, updateLiveRoots: false)
    }

    static func apply(theme: GokeTheme, colorScheme: ColorScheme? = nil, updateLiveRoots: Bool = true) {
        var liveRoots: [UIViewController] = []
        if updateLiveRoots {
            for scene in UIApplication.shared.connectedScenes {
                guard let windowScene = scene as? UIWindowScene else { continue }
                for window in windowScene.windows {
                    if let root = window.rootViewController {
                        liveRoots.append(root)
                    }
                }
            }
        }

        let effectiveColorScheme = colorScheme ?? lastApplied?.colorScheme
        let hasLiveRoots = !liveRoots.isEmpty
        let signature = AppliedSignature(
            theme: theme,
            colorScheme: effectiveColorScheme,
            appliedLiveChrome: hasLiveRoots
        )
        if let previous = lastApplied,
           previous.theme == signature.theme,
           previous.colorScheme == signature.colorScheme,
           (previous.appliedLiveChrome || !hasLiveRoots)
        {
            return
        }
        lastApplied = signature

        let paperColor = UIColor(theme.paper)
        let inkColor = UIColor(theme.ink)
        let accentColor = UIColor(theme.accent)
        let dividerColor = UIColor(theme.outlineVariant).withAlphaComponent(0.35)

        let standardNav = UINavigationBarAppearance()
        standardNav.configureWithOpaqueBackground()
        standardNav.backgroundColor = paperColor
        standardNav.shadowColor = dividerColor
        standardNav.titleTextAttributes = [.foregroundColor: inkColor]
        standardNav.largeTitleTextAttributes = [.foregroundColor: inkColor]
        standardNav.buttonAppearance.normal.titleTextAttributes = [.foregroundColor: accentColor]
        standardNav.doneButtonAppearance.normal.titleTextAttributes = [.foregroundColor: accentColor]

        let scrollEdgeNav = UINavigationBarAppearance()
        scrollEdgeNav.configureWithOpaqueBackground()
        scrollEdgeNav.backgroundColor = paperColor
        scrollEdgeNav.shadowColor = .clear
        scrollEdgeNav.titleTextAttributes = [.foregroundColor: inkColor]
        scrollEdgeNav.largeTitleTextAttributes = [.foregroundColor: inkColor]
        scrollEdgeNav.buttonAppearance.normal.titleTextAttributes = [.foregroundColor: accentColor]
        scrollEdgeNav.doneButtonAppearance.normal.titleTextAttributes = [.foregroundColor: accentColor]

        let navProxy = UINavigationBar.appearance()
        navProxy.standardAppearance = standardNav
        navProxy.compactAppearance = standardNav
        navProxy.scrollEdgeAppearance = scrollEdgeNav
        navProxy.compactScrollEdgeAppearance = scrollEdgeNav
        navProxy.tintColor = accentColor

        let tabAppearance: UITabBarAppearance?
        if #available(iOS 26, *) {
            // Preserve iOS 26 floating Liquid Glass tab bar appearance; only tint the active item.
            tabAppearance = nil
        } else {
            let legacyTabAppearance = UITabBarAppearance()
            legacyTabAppearance.configureWithOpaqueBackground()
            legacyTabAppearance.backgroundColor = paperColor
            legacyTabAppearance.shadowColor = dividerColor
            tabAppearance = legacyTabAppearance
        }

        let tabProxy = UITabBar.appearance()
        if let tabAppearance {
            tabProxy.standardAppearance = tabAppearance
            tabProxy.scrollEdgeAppearance = tabAppearance
        }
        tabProxy.tintColor = accentColor

        // Also update already-mounted navigation bars & tab bars immediately on runtime theme switches.
        for root in liveRoots {
            applyLiveChrome(
                in: root,
                standardNav: standardNav,
                scrollEdgeNav: scrollEdgeNav,
                tabAppearance: tabAppearance,
                accentColor: accentColor
            )
        }
    }

    private static func applyLiveChrome(
        in vc: UIViewController,
        standardNav: UINavigationBarAppearance,
        scrollEdgeNav: UINavigationBarAppearance,
        tabAppearance: UITabBarAppearance?,
        accentColor: UIColor
    ) {
        if let nav = vc as? UINavigationController {
            nav.navigationBar.standardAppearance = standardNav
            nav.navigationBar.compactAppearance = standardNav
            nav.navigationBar.scrollEdgeAppearance = scrollEdgeNav
            nav.navigationBar.compactScrollEdgeAppearance = scrollEdgeNav
            nav.navigationBar.tintColor = accentColor
        }
        if let tab = vc as? UITabBarController {
            if let tabAppearance {
                tab.tabBar.standardAppearance = tabAppearance
                tab.tabBar.scrollEdgeAppearance = tabAppearance
            }
            tab.tabBar.tintColor = accentColor
        }
        for child in vc.children {
            applyLiveChrome(
                in: child,
                standardNav: standardNav,
                scrollEdgeNav: scrollEdgeNav,
                tabAppearance: tabAppearance,
                accentColor: accentColor
            )
        }
        if let presented = vc.presentedViewController {
            applyLiveChrome(
                in: presented,
                standardNav: standardNav,
                scrollEdgeNav: scrollEdgeNav,
                tabAppearance: tabAppearance,
                accentColor: accentColor
            )
        }
    }
}
#endif
