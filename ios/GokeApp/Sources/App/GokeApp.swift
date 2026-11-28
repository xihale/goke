import Foundation
import GokeCore
import SwiftUI
import GokeShared
#if canImport(Combine)
import Combine
#endif
#if canImport(UIKit)
import UIKit
#endif

extension Notification.Name {
    /// Posted when the user activates an iOS Home Screen Quick Action (`UIApplicationShortcutItem`).
    static let gokeDidTriggerShortcutItem = Notification.Name("app.goke.ios.didTriggerShortcutItem")
}

#if canImport(UIKit)
/// Minimal `UIApplicationDelegate` bridging iOS Home Screen Quick Actions (`UIApplicationShortcutItem`)
/// into SwiftUI's `AppRouter`, including cold-launch shortcut buffering.
@MainActor
final class GokeAppDelegate: NSObject, UIApplicationDelegate {
    fileprivate static var pendingShortcutType: String?

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let shortcutItem = options.shortcutItem {
            Self.deliverShortcut(shortcutItem.type)
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = GokeSceneDelegate.self
        return configuration
    }

    static func consumePendingShortcutType() -> String? {
        let type = pendingShortcutType
        pendingShortcutType = nil
        return type
    }

    fileprivate static func deliverShortcut(_ type: String) {
        pendingShortcutType = type
        NotificationCenter.default.post(
            name: .gokeDidTriggerShortcutItem,
            object: nil,
            userInfo: ["type": type]
        )
    }
}

@MainActor
final class GokeSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        GokeAppDelegate.deliverShortcut(shortcutItem.type)
        completionHandler(true)
    }
}
#endif

/// Main SwiftUI entry point for Goke iOS.
@main
public struct GokeApp: App {
    #if canImport(UIKit)
    @UIApplicationDelegateAdaptor(GokeAppDelegate.self) private var appDelegate
    #endif

    @State private var container: AppContainer
    @Environment(\.scenePhase) private var scenePhase

    public init() {
        let appContainer = AppContainer()
        // Install UNUserNotificationCenter delegate synchronously during launch so cold-launch
        // notification taps and interactive actions are never discarded by iOS.
        appContainer.installNotificationDelegate()
        appContainer.registerBackgroundTasks()
        #if canImport(UIKit)
        GokeChromeAppearance.applyProxies(theme: appContainer.settingsStore.resolvedTheme)
        #endif
        _container = State(initialValue: appContainer)
    }

    public var body: some Scene {
        WindowGroup {
            RootTabView()
                .environment(container)
                .environment(container.router)
                .environment(container.actions)
                .environment(container.settingsStore)
                .environment(container.syncCoordinator)
                .gokeTheme(container.settingsStore.resolvedTheme)
                .gokePerformanceObserver()
                .gokeQuickSnoozeHost()
                .preferredColorScheme(container.settingsStore.preferredColorScheme)
                .onAppear {
                    container.markRootViewMounted()
                }
                .task {
                    await container.bootstrap()
                    #if canImport(UIKit)
                    if let shortcutType = GokeAppDelegate.consumePendingShortcutType() {
                        _ = container.router.handleShortcutItemType(shortcutType)
                    }
                    #endif
                }
                .onOpenURL { url in
                    container.handleOpenURL(url)
                }
                .onChange(of: scenePhase) { _, newPhase in
                    switch newPhase {
                    case .active:
                        #if DEBUG && canImport(UIKit)
                        if container.isHydrated {
                            UIApplication.shared.isIdleTimerDisabled = true
                            DebugBridgeServer.shared.start(router: container.router, container: container)
                        }
                        #endif
                        Task {
                            await container.handleForegroundRefresh()
                        }
                    case .background:
                        container.handleEnterBackground()
                    case .inactive:
                        break
                    @unknown default:
                        break
                    }
                }
                .onChange(of: container.settingsStore.settings) { _, _ in
                    container.notifySettingsChanged()
                }
                .onReceive(NotificationCenter.default.publisher(for: .gokeIntentDidMutateData)) { _ in
                    Task {
                        await container.handleExternalDataMutation()
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: .gokeIntentDidRequestTab)) { _ in
                    container.router.consumePendingIntentTab(from: container.intentBridge)
                }
                .onReceive(NotificationCenter.default.publisher(for: .gokeFocusFilterDidChange)) { _ in
                    container.notifySettingsChanged()
                }
                .onReceive(NotificationCenter.default.publisher(for: .gokeDidTriggerShortcutItem)) { notification in
                    #if canImport(UIKit)
                    _ = GokeAppDelegate.consumePendingShortcutType()
                    #endif
                    if let type = notification.userInfo?["type"] as? String {
                        _ = container.router.handleShortcutItemType(type)
                    }
                }
        }
    }
}
