import SwiftUI
import WidgetKit

/// Entry point registering all Goke Home Screen / Lock Screen widgets,
/// the iOS 18 Control Center quick-capture button, and the class Live Activity.
@main
struct GokeWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayOverviewWidget()
        NextClassWidget()
        WeeklyTimetableWidget()
        TodoChecklistWidget()
        HotWaterWidget()
        if #available(iOS 18.0, *) {
            QuickAddControlWidget()
        }
        GokeClassLiveActivity()
    }
}
