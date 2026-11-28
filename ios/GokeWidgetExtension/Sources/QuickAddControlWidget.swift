import AppIntents
import Foundation
import SwiftUI
import WidgetKit
import GokeShared

// MARK: - Quick Add Control Intent

/// Opens Goke directly to the Quick Capture composer (`goke://quickadd`)
/// from iOS 18 Control Center, Lock Screen controls, or the Action Button.
@available(iOS 18.0, *)
struct OpenQuickAddControlIntent: AppIntent {
    static let title: LocalizedStringResource = "快速记录"
    static let description: IntentDescription? = IntentDescription("打开 Goke 快速添加待办或日程。")
    static let openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult & OpensIntent {
        let deepLink = WidgetDeepLink.quickAdd
        // Rely strictly on OpenURLIntent so AppContainer.handleOpenURL delivers the deep link once
        // without leaving a stale pendingDeepLink that would re-trigger QuickAdd on next foreground.
        AppGroupContainer.shared.userDefaults.removeObject(forKey: "app.goke.ios.pendingDeepLink")
        return .result(opensIntent: OpenURLIntent(deepLink))
    }
}

// MARK: - iOS 18 ControlWidget

/// iOS 18 Control Center & Action Button quick-capture widget (`goke://quickadd`).
@available(iOS 18.0, *)
struct QuickAddControlWidget: ControlWidget {
    static let kind = "app.goke.control.quickAdd"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: OpenQuickAddControlIntent()) {
                Label("Goke 速记", systemImage: "plus.square.on.square")
            }
        }
        .displayName("快速记录")
        .description("一键打开 Goke 速记面板，快速添加待办或日程。")
    }
}
