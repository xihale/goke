import Foundation
import GokeCore
import SwiftUI

/// Concrete screen factory wiring all 5 root tabs, 25 `GokeRoute` destinations,
/// and 9 `GokeSheet` modals directly into `RootTabView` with zero `AnyView` type erasure
/// and named destination/sheet wrapper views to avoid leaking huge `_ConditionalContent` typerefs.
@MainActor
struct GokeRootScreensWiring {
    init() {}

    // MARK: - 5 Root Tabs

    @ViewBuilder
    func makeTodayView() -> TodayView {
        TodayView()
    }

    @ViewBuilder
    func makeTimetableView() -> TimetableView {
        TimetableView()
    }

    @ViewBuilder
    func makeCalendarView() -> CalendarView {
        CalendarView()
    }

    @ViewBuilder
    func makeTodoView() -> TodoView {
        TodoView()
    }

    @ViewBuilder
    func makeMeView() -> MeView {
        MeView()
    }

    // MARK: - Typed Navigation Destinations & Global Modal Sheets

    func makeRouteDestination(for route: GokeRoute) -> GokeRouteDestinationView {
        GokeRouteDestinationView(route: route)
    }

    func makeSheetContent(for sheet: GokeSheet) -> GokeSheetContentView {
        GokeSheetContentView(sheet: sheet)
    }
}

struct GokeRouteDestinationView: View {
    let route: GokeRoute

    var body: some View {
        switch route {
        case .editTimetable:
            EditTimetableView()
        case .periods(let semesterId):
            PeriodsEditView(semesterId: semesterId)
        case .courseDetail(let courseId, _):
            CourseDetailView(courseId: courseId)
        case .courseEdit(let courseId):
            CourseEditView(courseId: courseId, isSheet: false)
        case .todoEdit(let taskId, let initialTitle, let initialDate, let courseId):
            TodoEditView(
                taskId: taskId,
                initialTitle: initialTitle,
                initialDate: initialDate,
                courseId: courseId,
                isSheet: false
            )
        case .eventEdit(let eventId, let initialDate):
            EventEditView(eventId: eventId, initialDate: initialDate, isSheet: false)
        case .jxfw:
            JxfwImportView()
        case .jxfwNotices:
            JxfwNoticesView()
        case .courseSelection:
            CourseSelectionView()
        case .grades:
            GradesView()
        case .physicalTest:
            PhysicalTestView()
        case .classrooms:
            EmptyClassroomsView()
        case .campusResources(let initialTab, let initialQuery, let initialCode):
            CampusResourcesView(
                initialTab: initialTab,
                initialQuery: initialQuery,
                initialCode: initialCode
            )
        case .hotWater(let autoToggle):
            HotWaterView(autoToggle: autoToggle)
        case .hotWaterBills:
            HotWaterBillsView()
        case .sync:
            SyncSettingsView()
        case .calendarSync:
            CalendarSyncView()
        case .dataManagement:
            DataManagementView()
        case .importFile(let url):
            ImportFileView(initialURL: url)
        case .trash:
            TrashView()
        case .history:
            ChangeHistoryView()
        case .reliability:
            ReliabilityView()
        case .appearance:
            AppearanceSettingsView()
        case .preferences:
            PreferencesSettingsView()
        case .reminders:
            ReminderSettingsView()
        case .widgetsGallery:
            WidgetsGalleryView()
        case .about:
            AboutView()
        }
    }
}

struct GokeSheetContentView: View {
    let sheet: GokeSheet

    var body: some View {
        switch sheet {
        case .quickAdd(let initialText, let initialDate):
            QuickAddSheet(initialText: initialText, initialDate: initialDate)
        case .batchAdd(let lines):
            BatchAddSheet(lines: lines)
        case .courseEdit(let courseId):
            CourseEditView(courseId: courseId, isSheet: true)
        case .todoEdit(let taskId, let initialTitle, let initialDate, let courseId):
            TodoEditView(
                taskId: taskId,
                initialTitle: initialTitle,
                initialDate: initialDate,
                courseId: courseId,
                isSheet: true
            )
        case .eventEdit(let eventId, let initialDate):
            EventEditView(eventId: eventId, initialDate: initialDate, isSheet: true)
        case .morningTidy:
            MorningTidySheet()
        case .rescheduleTask(let taskId):
            RescheduleTaskSheet(taskId: taskId)
        case .importFile(let url):
            ImportFileSheetHost(url: url)
                .gokePaperSheet(detents: [.medium, .large])
        case .timetableExport:
            TimetableExportSheet()
                .gokePaperSheet(detents: [.medium, .large])
        }
    }
}

private struct ImportFileSheetHost: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ImportFileView(initialURL: url)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("关闭") {
                            dismiss()
                        }
                    }
                }
        }
    }
}
