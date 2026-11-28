import Foundation
import GokeCore
import GokeShared
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
#if canImport(UIKit)
import UIKit
#endif

// MARK: - TodoEditDraft

/// Complete prefill draft transferred from `QuickAddSheet` ("打开详细编辑器") to `TodoEditView`.
struct TodoEditDraft: Identifiable, Sendable, Hashable {
    let id: UUID
    var title: String
    var due: LocalDateTime?
    var dueHasTime: Bool
    var startDate: LocalDate?
    var priority: Int
    var listId: String?
    var pendingListName: String?
    var courseId: String?
    var courseWeek: Int?
    var dueBeforeNextClass: Bool
    var tags: [String]
    var remindMinutesList: [Int]
    var remindPersistent: Bool
    var repeatRule: Recurrence?
    var blockStart: LocalDateTime?
    var blockMinutes: Int?

    init(
        id: UUID = UUID(),
        title: String = "",
        due: LocalDateTime? = nil,
        dueHasTime: Bool = false,
        startDate: LocalDate? = nil,
        priority: Int = 0,
        listId: String? = nil,
        pendingListName: String? = nil,
        courseId: String? = nil,
        courseWeek: Int? = nil,
        dueBeforeNextClass: Bool = false,
        tags: [String] = [],
        remindMinutesList: [Int] = [],
        remindPersistent: Bool = false,
        repeatRule: Recurrence? = nil,
        blockStart: LocalDateTime? = nil,
        blockMinutes: Int? = nil
    ) {
        self.id = id
        self.title = title
        self.due = due
        self.dueHasTime = dueHasTime
        self.startDate = startDate
        self.priority = priority
        self.listId = listId
        self.pendingListName = pendingListName
        self.courseId = courseId
        self.courseWeek = courseWeek
        self.dueBeforeNextClass = dueBeforeNextClass
        self.tags = tags
        self.remindMinutesList = remindMinutesList
        self.remindPersistent = remindPersistent
        self.repeatRule = repeatRule
        self.blockStart = blockStart
        self.blockMinutes = blockMinutes
    }
}

// MARK: - Docked Editor Panel & Modal Enums

private enum TodoEditModal: Identifiable {
    case whenPanel

    var id: String {
        switch self {
        case .whenPanel: return "whenPanel"
        }
    }
}

private enum TodoEditorPanel: String, Hashable, Sendable {
    case list
    case course
    case priority
    case tags
}

private enum TodoEditFocusField: Hashable {
    case location
    case note
    case newSubtask
    case newTag
}

private struct ScheduleConflictItem: Identifiable, Hashable, Sendable {
    let id: String
    let date: LocalDate
    let title: String
    let timeRange: String
    let isCourse: Bool
}

// MARK: - TodoEditView (1:1 Android TodoEditScreen.kt Paper Canvas + Docked Panels)

/// Paper-first Task Editor matching Android `TodoEditScreen.kt`:
/// - Borderless Mint Paper canvas with zero interior dividers & full-canvas tap-to-focus on notes.
/// - Live inline `GokeTokenTextView` SmartTitle (`DATE | TIME | REMIND`) with automatic live apply
///   and Android's untouched-existing guard (zero manual `"识别应用"` friction).
/// - Borderless `WhenRow` with flush-right `×` clear button, opening unified `GokeWhenPanel`.
/// - Inline panels (`List`, `Priority`, `Tag`, `Course`, `Subtasks`) docked **directly above the
///   persistent bottom toolbar** (never off-screen inside ScrollView!).
/// - 1-tap `PhotosPicker` & `.fileImporter` triggers right on the bottom toolbar.
/// - 6-month conflict detection expanding both courses AND other recurring block tasks.
/// - Automatic flush of uncommitted `newSubtaskTitle` on Save, `@tag` rendering, top-right
///   checkbox + immediate undoable delete for existing tasks, and dirty-draft guard on expanded QuickAdd drafts.
@MainActor
private final class TodoEditDerivedCacheBox {
    private struct ParseKey: Equatable {
        let title: String
        let nowMinute: LocalDateTime
        let config: QuickAddConfig
        let isUntouchedDatelessExisting: Bool
    }

    private struct ConflictKey: Equatable {
        let snapshotRevision: UInt64
        let taskId: String?
        let blockStart: LocalDateTime?
        let blockMinutes: Int?
        let repeatFreq: Freq?
        let repeatInterval: Int
        let repeatByDays: Set<Int>
        let repeatByMonths: Set<Int>
        let repeatUntil: LocalDate?
        let repeatCount: Int?
        let repeatExDates: Set<LocalDate>
    }

    private var cachedParseKey: ParseKey?
    private var cachedParsedTitle: QuickAddResult?
    private var cachedTokenSpans: [QuickTokenSpan] = []

    private var cachedSnapshotRevision: UInt64?
    private var cachedLists: [TaskList] = []
    private var cachedCourses: [Course] = []
    private var cachedExistingTags: [String] = []

    private var cachedConflictKey: ConflictKey?
    private var cachedConflicts: [ScheduleConflictItem] = []

    func parseTitle(
        _ title: String,
        nowMinute: LocalDateTime,
        config: QuickAddConfig,
        isUntouchedDatelessExisting: Bool
    ) -> (parsed: QuickAddResult, tokenSpans: [QuickTokenSpan]) {
        let key = ParseKey(
            title: title,
            nowMinute: nowMinute,
            config: config,
            isUntouchedDatelessExisting: isUntouchedDatelessExisting
        )
        if key == cachedParseKey, let cachedParsedTitle {
            return (cachedParsedTitle, cachedTokenSpans)
        }
        let parsed = QuickAddParser.parse(title, now: nowMinute, config: config)
        let spans = isUntouchedDatelessExisting
            ? []
            : QuickAddParser.tokenRanges(in: title, tokens: parsed.tokens)
        cachedParseKey = key
        cachedParsedTitle = parsed
        cachedTokenSpans = spans
        return (parsed, spans)
    }

    func snapshotIndices(
        revision: UInt64,
        live: AppSnapshot
    ) -> (lists: [TaskList], courses: [Course], existingTags: [String]) {
        if cachedSnapshotRevision == revision {
            return (cachedLists, cachedCourses, cachedExistingTags)
        }
        let lists = live.lists.sorted { $0.sort < $1.sort }
        let courses = TodoQuickHelpers.pickerCourses(
            from: live.courses,
            currentSemesterId: live.currentSemester?.id
        )
        let existingTags = Array(
            Set(
                live.tasks
                    .filter { $0.deletedAt == nil }
                    .flatMap(\.tags)
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            )
        ).sorted().prefix(16).map { $0 }
        cachedSnapshotRevision = revision
        cachedLists = lists
        cachedCourses = courses
        cachedExistingTags = existingTags
        return (lists, courses, existingTags)
    }

    func conflicts(
        snapshotRevision: UInt64,
        taskId: String?,
        live: AppSnapshot,
        effectiveWhen: WhenPick?,
        repeatFreq: Freq?,
        repeatInterval: Int,
        repeatByDays: Set<Int>,
        repeatByMonths: Set<Int>,
        repeatUntil: LocalDate?,
        preservedRepeatCount: Int?,
        preservedRepeatExDates: Set<LocalDate>
    ) -> [ScheduleConflictItem] {
        guard let wp = effectiveWhen,
              let blockStartDT = wp.resolvedBlockStart,
              let duration = wp.resolvedBlockMinutes,
              duration > 0
        else {
            cachedConflictKey = nil
            cachedConflicts = []
            return []
        }

        let key = ConflictKey(
            snapshotRevision: snapshotRevision,
            taskId: taskId,
            blockStart: blockStartDT,
            blockMinutes: duration,
            repeatFreq: repeatFreq,
            repeatInterval: repeatInterval,
            repeatByDays: repeatByDays,
            repeatByMonths: repeatByMonths,
            repeatUntil: repeatUntil,
            repeatCount: preservedRepeatCount,
            repeatExDates: preservedRepeatExDates
        )
        if key == cachedConflictKey {
            return cachedConflicts
        }

        let anchor = blockStartDT.date
        let horizonEnd = repeatUntil ?? anchor.addingMonths(6)
        let myDates: [LocalDate]
        if let freq = repeatFreq {
            let rule = Recurrence(
                freq: freq,
                interval: max(1, repeatInterval),
                byDays: (freq == .WEEKLY || freq == .MONTHLY || freq == .YEARLY) ? repeatByDays : [],
                until: repeatUntil,
                count: preservedRepeatCount,
                exDates: preservedRepeatExDates,
                byMonths: freq == .YEARLY ? repeatByMonths : []
            )
            myDates = RecurrenceEngine.dates(rule: rule, anchor: anchor, from: anchor, to: horizonEnd)
        } else {
            myDates = [anchor]
        }

        guard let minDate = myDates.min(), let maxDate = myDates.max() else {
            cachedConflictKey = key
            cachedConflicts = []
            return []
        }

        let startMins = blockStartDT.time.hour * 60 + blockStartDT.time.minute
        let endMins = startMins + max(5, duration)
        let dateSet = Set(myDates)
        var results: [ScheduleConflictItem] = []

        if let semester = live.currentSemester {
            let occs = CourseExpander.occurrences(
                semester: semester,
                courses: live.courses,
                exceptions: live.exceptions,
                from: minDate,
                to: maxDate
            )
            for occ in occs where dateSet.contains(occ.date) {
                let cStart = occ.start.time.hour * 60 + occ.start.time.minute
                let cEnd = occ.end.time.hour * 60 + occ.end.time.minute
                if startMins < cEnd && endMins > cStart {
                    results.append(
                        ScheduleConflictItem(
                            id: "c:\(occ.id)",
                            date: occ.date,
                            title: occ.course.name,
                            timeRange: "\(TodoDateFormatter.hhmm(occ.start.time))–\(TodoDateFormatter.hhmm(occ.end.time))",
                            isCourse: true
                        )
                    )
                }
            }
        }

        for other in live.tasks where other.deletedAt == nil && !other.done && other.id != taskId {
            guard let bs = other.blockStart else { continue }
            let oStart = bs.time.hour * 60 + bs.time.minute
            let oEnd = oStart + max(5, other.blockMinutes ?? 30)
            guard startMins < oEnd && endMins > oStart else { continue }

            let otherDates: [LocalDate]
            if let otherRule = other.`repeat` {
                otherDates = RecurrenceEngine.dates(
                    rule: otherRule,
                    anchor: bs.date,
                    from: minDate,
                    to: maxDate
                )
            } else {
                otherDates = [bs.date]
            }

            let endTime = bs.addingMinutes(max(5, other.blockMinutes ?? 30)).time
            let rangeText = "\(TodoDateFormatter.hhmm(bs.time))–\(TodoDateFormatter.hhmm(endTime))"
            for od in otherDates where dateSet.contains(od) {
                results.append(
                    ScheduleConflictItem(
                        id: "t:\(other.id):\(od.description)",
                        date: od,
                        title: other.title,
                        timeRange: rangeText,
                        isCourse: false
                    )
                )
            }
        }

        let sorted = results.sorted { $0.date < $1.date }
        cachedConflictKey = key
        cachedConflicts = sorted
        return sorted
    }
}

struct TodoEditView: View {
    let taskId: String?
    let initialTitle: String?
    let initialDate: LocalDate?
    let initialCourseId: String?
    let initialCourseWeek: Int?
    let draft: TodoEditDraft?
    let isSheet: Bool
    let onCommitted: (() -> Void)?

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var derivedCache = TodoEditDerivedCacheBox()
    @State private var didLoad: Bool = false
    @State private var initialSignature: String = ""
    @State private var title: String = ""
    @State private var isTitleFocused: Bool = false
    @State private var titleCursorRequest: GokeCursorRequest?
    @State private var locationText: String = ""
    @State private var note: String = ""
    @State private var priority: Int = 0
    @State private var listId: String?
    @State private var pendingListName: String?
    @State private var courseId: String?
    @State private var courseWeek: Int?
    @State private var dueBeforeNextClass: Bool = false
    @State private var isDone: Bool = false

    // Unified WhenPick state (nil = unset unless SmartTitle derives a live date/time)
    @State private var whenPick: WhenPick?
    @State private var preservedStartDate: LocalDate?
    @State private var estimateMinutes: Int?

    @State private var repeatFreq: Freq?
    @State private var repeatInterval: Int = 1
    @State private var repeatByDays: Set<Int> = []
    @State private var repeatByMonths: Set<Int> = []
    @State private var repeatUntil: LocalDate?
    @State private var preservedRepeatCount: Int?
    @State private var preservedRepeatExDates: Set<LocalDate> = []

    @State private var remindMinutesList: [Int] = []
    @State private var remindPersistent: Bool = false

    @State private var tags: [String] = []
    @State private var newTagText: String = ""
    @State private var attachments: [Attachment] = []
    @State private var imageCols: Int?

    @State private var subtasks: [SubtaskDraft] = []
    @State private var newSubtaskTitle: String = ""

    @State private var activePanel: TodoEditorPanel?
    @State private var activeModal: TodoEditModal?
    @State private var showInlineSubtasks: Bool = false
    @State private var showDiscardConfirm: Bool = false
    @State private var pendingConflictJumpDate: LocalDate?
    @State private var didCommitOrDiscard: Bool = false
    @State private var cachedCourseSessions: [CourseOccurrence] = []

    // 1-tap bottom bar attachment pickers
    @State private var photoPickerItems: [PhotosPickerItem] = []
    @State private var showFileImporter: Bool = false
    @State private var isImportingAttachments: Bool = false

    @FocusState private var focusedField: TodoEditFocusField?

    private struct SubtaskDraft: Identifiable, Hashable {
        let id: String
        var title: String
        var done: Bool
        var existing: GokeTask?
    }

    init(
        taskId: String? = nil,
        initialTitle: String? = nil,
        initialDate: LocalDate? = nil,
        courseId: String? = nil,
        courseWeek: Int? = nil,
        draft: TodoEditDraft? = nil,
        isSheet: Bool = true,
        onCommitted: (() -> Void)? = nil
    ) {
        self.taskId = taskId
        self.initialTitle = initialTitle
        self.initialDate = initialDate
        self.initialCourseId = courseId
        self.initialCourseWeek = courseWeek
        self.draft = draft
        self.isSheet = isSheet
        self.onCommitted = onCommitted
    }

    private var existingTask: GokeTask? {
        guard let taskId else { return nil }
        return container.liveSnapshot.tasks.first { $0.id == taskId }
    }

    private var currentSignature: String {
        let subSig = subtasks.map { "\($0.id):\($0.title):\($0.done)" }.joined(separator: "|")
        let attSig = attachments.map(\.file).joined(separator: ",")
        let whenSig: String
        if let wp = whenPick {
            whenSig = "\(wp.date.description):\(wp.start?.description ?? ""):\(wp.minutes ?? 0)"
        } else {
            whenSig = ""
        }
        let repeatDaysSig = repeatByDays.sorted().map(String.init).joined(separator: ",")
        let repeatMonthsSig = repeatByMonths.sorted().map(String.init).joined(separator: ",")
        let remindSig = remindMinutesList.map(String.init).joined(separator: ",")
        let tagsSig = tags.joined(separator: ",")
        let parts: [String] = [
            title,
            locationText,
            note,
            "\(priority)",
            listId ?? "",
            pendingListName ?? "",
            courseId ?? "",
            courseWeek.map(String.init) ?? "",
            "\(dueBeforeNextClass)",
            "\(isDone)",
            whenSig,
            preservedStartDate?.description ?? "",
            repeatFreq?.rawValue ?? "",
            "\(repeatInterval)",
            repeatDaysSig,
            repeatMonthsSig,
            repeatUntil?.description ?? "",
            remindSig,
            "\(remindPersistent)",
            tagsSig,
            attSig,
            imageCols.map(String.init) ?? "",
            subSig,
            newSubtaskTitle,
        ]
        return parts.joined(separator: "§")
    }

    /// Expanded QuickAdd `draft` is ALWAYS treated as dirty so backing out never silently drops it!
    private var isDirty: Bool {
        guard didLoad else { return false }
        if existingTask == nil && draft != nil {
            return true
        }
        return currentSignature != initialSignature
    }

    private var needsDiscardConfirm: Bool {
        guard existingTask == nil else { return false }
        return !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isDirty
    }

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    /// Android untouched-existing guard: an existing dateless task whose title was not edited keeps no auto-derived date.
    private var isUntouchedDatelessExisting: Bool {
        guard let existing = existingTask else { return false }
        return existing.due == nil
            && existing.blockStart == nil
            && title == existing.title
    }

    private func smartTitleParse(now: LocalDateTime) -> QuickAddResult {
        let cfg = container.settingsStore.settings.quickAddConfig
        return QuickAddParser.parse(title, now: now, config: cfg)
    }

    private func resolvedWhenPick(from parsed: QuickAddResult) -> WhenPick? {
        if dueBeforeNextClass { return nil }
        if let whenPick { return whenPick }
        guard !isUntouchedDatelessExisting, let due = parsed.due else { return nil }
        return WhenPick(date: due.date, start: parsed.dueHasTime ? due.time : nil, minutes: nil)
    }

    private func resolvedRemindList(from parsed: QuickAddResult) -> [Int] {
        if !remindMinutesList.isEmpty {
            return Reminders.normalize(remindMinutesList)
        }
        guard !isUntouchedDatelessExisting else { return [] }
        return Reminders.normalize(parsed.remindMinutesList)
    }

    private func resolvedRemindPersistent(from parsed: QuickAddResult, effectiveList: [Int]) -> Bool {
        guard !effectiveList.isEmpty else { return false }
        if !remindMinutesList.isEmpty {
            return remindPersistent
        }
        return !isUntouchedDatelessExisting && parsed.remindPersistent
    }

    var body: some View {
        if isSheet {
            NavigationStack {
                editorContent
            }
            .gokePaperSheet(theme, detents: [.large])
        } else {
            editorContent
                #if os(iOS)
                .toolbar(.hidden, for: .tabBar)
                #endif
        }
    }

    @ViewBuilder
    private var editorContent: some View {
        let now = LocalDateTime.now()
        let nowMinute = LocalDateTime(
            date: now.date,
            time: LocalTime(hour: now.time.hour, minute: now.time.minute, second: 0)
        )
        let today = nowMinute.date
        let live = container.liveSnapshot
        let semesterEnd = GokeWhenFormatter.semesterEndDate(live.currentSemester)
        let (lists, courses, existingTagPool) = derivedCache.snapshotIndices(
            revision: container.snapshotRevision,
            live: live
        )
        let selectedList = listId.flatMap { id in lists.first(where: { $0.id == id }) }
        let selectedCourse = courseId.flatMap { id in courses.first(where: { $0.id == id }) }

        let quickAddConfig = container.settingsStore.settings.quickAddConfig
        let (parsedTitle, smartTokenSpans) = derivedCache.parseTitle(
            title,
            nowMinute: nowMinute,
            config: quickAddConfig,
            isUntouchedDatelessExisting: isUntouchedDatelessExisting
        )
        let effectiveWhen = resolvedWhenPick(from: parsedTitle)
        let effectiveRemind = resolvedRemindList(from: parsedTitle)
        let effectivePersistent = resolvedRemindPersistent(from: parsedTitle, effectiveList: effectiveRemind)
        let conflicts = derivedCache.conflicts(
            snapshotRevision: container.snapshotRevision,
            taskId: taskId,
            live: live,
            effectiveWhen: effectiveWhen,
            repeatFreq: repeatFreq,
            repeatInterval: repeatInterval,
            repeatByDays: repeatByDays,
            repeatByMonths: repeatByMonths,
            repeatUntil: repeatUntil,
            preservedRepeatCount: preservedRepeatCount,
            preservedRepeatExDates: preservedRepeatExDates
        )

        let targetCourseOcc: CourseOccurrence? = {
            guard dueBeforeNextClass, courseId != nil else { return nil }
            if let cw = courseWeek {
                return cachedCourseSessions.first(where: { $0.originalWeek == cw })
            }
            return cachedCourseSessions.first(where: { $0.start >= nowMinute }) ?? cachedCourseSessions.last
        }()

        ScrollViewReader { scrollProxy in
            VStack(spacing: 0) {
                GeometryReader { geo in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            // MARK: Zone 1 — Identity & Context
                            VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                                // Borderless SmartTitle (live inline DATE/TIME/REMIND highlights, IME-safe)
                                #if canImport(UIKit)
                                GokeTokenTextView(
                                    text: $title,
                                    isFocused: $isTitleFocused,
                                    cursorRequest: $titleCursorRequest,
                                    placeholder: "准备做什么？",
                                    tokenSpans: smartTokenSpans,
                                    priority: priority,
                                    remindPersistent: effectivePersistent,
                                    allowedKinds: [.date, .time, .remind],
                                    theme: theme,
                                    fontSize: 22,
                                    fontWeight: .semibold,
                                    isStrikethrough: isDone,
                                    isDimmed: isDone,
                                    minHeight: 44,
                                    maxLines: 5,
                                    returnKeyType: .next,
                                    onRecomputeSpans: { updated in
                                        derivedCache.parseTitle(
                                            updated,
                                            nowMinute: nowMinute,
                                            config: quickAddConfig,
                                            isUntouchedDatelessExisting: isUntouchedDatelessExisting
                                        ).1
                                    },
                                    onSubmit: {
                                        isTitleFocused = false
                                        focusedField = .location
                                    }
                                )
                                .padding(.horizontal, GokeSpacing.lg)
                                .padding(.top, GokeSpacing.xs)
                                #else
                                TextField("准备做什么？", text: $title, axis: .vertical)
                                    .font(GokeTypography.titleLarge)
                                    .foregroundStyle(isDone ? theme.muted : theme.ink)
                                    .strikethrough(isDone)
                                    .lineLimit(1...5)
                                    .padding(.horizontal, GokeSpacing.lg)
                                    .padding(.top, GokeSpacing.xs)
                                #endif

                                // Interactive Attribute Pills Strip (Priority · Course · @tags; zero noise when empty)
                                if priority > 0 || selectedCourse != nil || !tags.isEmpty {
                                    attributePillsStrip(selectedCourse: selectedCourse)
                                        .padding(.horizontal, GokeSpacing.lg)
                                        .padding(.top, 2)
                                }

                                // Upgraded WhenRow (berry-red overdue, duration badge, resolved next-class time)
                                whenRow(
                                    effectiveWhen: effectiveWhen,
                                    effectiveRemind: effectiveRemind,
                                    effectivePersistent: effectivePersistent,
                                    selectedCourse: selectedCourse,
                                    targetCourseOcc: targetCourseOcc,
                                    today: today,
                                    nowMinute: nowMinute,
                                    semesterEnd: semesterEnd,
                                    parsedTokens: parsedTitle.tokens
                                )
                                .padding(.horizontal, GokeSpacing.lg)

                                // Time-block conflict alert card (cached 6-month + recurring expansion)
                                if !conflicts.isEmpty {
                                    GokeConflictCard(title: "时间冲突（\(conflicts.count) 处）") {
                                        VStack(alignment: .leading, spacing: 6) {
                                            ForEach(conflicts.prefix(4)) { item in
                                                Button {
                                                    handleConflictTap(date: item.date, live: live)
                                                } label: {
                                                    HStack(spacing: 6) {
                                                        Image(systemName: item.isCourse ? "graduationcap.fill" : "clock.fill")
                                                            .font(.caption2)
                                                            .foregroundStyle(theme.removed)
                                                        Text("\(TodoDateFormatter.shortDate(item.date)) \(item.timeRange) · \(item.title)")
                                                            .font(GokeTypography.labelMedium)
                                                            .foregroundStyle(theme.ink)
                                                            .monospacedDigit()
                                                            .lineLimit(1)
                                                        Spacer(minLength: 0)
                                                        Image(systemName: "chevron.right")
                                                            .font(.caption2)
                                                            .foregroundStyle(theme.muted)
                                                    }
                                                    .padding(.vertical, 4)
                                                    .contentShape(Rectangle())
                                                }
                                                .buttonStyle(.mintRow)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, GokeSpacing.lg)
                                }

                                // Icon-aligned Location Row (aligned on the exact 22pt left icon axis as WhenRow)
                                locationRow
                                    .padding(.horizontal, GokeSpacing.lg)
                            }
                            .padding(.bottom, 12)

                            // Clean 0.5pt hairline separating Zone 1 (Context) and Zone 2 (Details)
                            Rectangle()
                                .fill(theme.outlineVariant.opacity(0.35))
                                .frame(height: 0.5)
                                .padding(.horizontal, GokeSpacing.lg)

                            // MARK: Zone 2 — Calm Writing, Inline Subtasks & Attachments
                            VStack(alignment: .leading, spacing: GokeSpacing.md) {
                                TextField("写点备注…", text: $note, axis: .vertical)
                                    .font(GokeTypography.bodyLarge)
                                    .lineSpacing(4)
                                    .foregroundStyle(theme.ink)
                                    .focused($focusedField, equals: .note)
                                    .lineLimit(3...18)
                                    .padding(.horizontal, GokeSpacing.lg)

                                if !subtasks.isEmpty || showInlineSubtasks {
                                    inlineSubtasksSection
                                        .id("inline-subtasks-section")
                                        .padding(.horizontal, GokeSpacing.lg)
                                        .padding(.top, 2)
                                }

                                if !attachments.isEmpty {
                                    TodoAttachmentEditorView(
                                        attachments: $attachments,
                                        imageColumns: $imageCols,
                                        allowEditing: true,
                                        showPrimaryPickers: false
                                    )
                                    .padding(.horizontal, GokeSpacing.lg)
                                    .padding(.top, 2)
                                }
                            }
                            .padding(.top, 14)

                            // MARK: Zone 3 — Quiet Metadata Footer (Existing Tasks)
                            if let existing = existingTask {
                                quietMetadataFooter(task: existing)
                                    .padding(.horizontal, GokeSpacing.lg)
                                    .padding(.top, 20)
                            }

                            // Full-canvas tap-to-focus empty paper region -> tapping focuses Note!
                            Color.clear
                                .frame(maxWidth: .infinity, minHeight: 110)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    closePanel()
                                    isTitleFocused = false
                                    focusedField = .note
                                }
                        }
                        .frame(minHeight: geo.size.height, alignment: .top)
                    }
                    .scrollDismissesKeyboard(.interactively)
                }

                // Crisp 0.5pt hairline separating scrolling canvas from docked bottom chrome
                Rectangle()
                    .fill(theme.outlineVariant.opacity(0.45))
                    .frame(height: 0.5)

                // Docked Inline Panel Tray directly above persistent bottom toolbar
                VStack(spacing: 0) {
                    if let panel = activePanel {
                        dockedPanelContent(
                            panel: panel,
                            lists: lists,
                            courses: courses,
                            existingTagPool: existingTagPool
                        )
                        .id(panel)
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.top, 10)
                        .padding(.bottom, 8)
                        .background(theme.surface)
                        .transition(
                            simplifyMotion
                                ? .opacity
                                : .asymmetric(
                                    insertion: .opacity.combined(with: .move(edge: .bottom)),
                                    removal: .opacity
                                )
                        )
                    }
                }
                .clipped()

                bottomActionToolbar(
                    selectedList: selectedList,
                    onTapSubtasks: {
                        closePanel()
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            showInlineSubtasks = true
                        }
                        isTitleFocused = false
                        focusedField = .newSubtask
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 60_000_000)
                            withAnimation(GokeMotion.snappy(policy: performance)) {
                                scrollProxy.scrollTo("inline-subtasks-section", anchor: .bottom)
                            }
                        }
                    }
                )
            }
        }
        .background(theme.paper.ignoresSafeArea())
        .toolbarBackground(theme.paper, for: .navigationBar)
        .navigationTitle(existingTask == nil ? "新建待办" : "")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(!isSheet && needsDiscardConfirm)
        .interactiveDismissDisabled(needsDiscardConfirm)
        .toolbar {
            if isSheet {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        handleBackOrClose()
                    } label: {
                        if existingTask == nil {
                            Text("取消")
                                .foregroundStyle(theme.muted)
                        } else if isDirty {
                            Text("完成")
                                .fontWeight(.semibold)
                                .foregroundStyle(theme.accent)
                        } else {
                            Text("关闭")
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
            } else if needsDiscardConfirm {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        handleBackOrClose()
                    } label: {
                        Label("返回", systemImage: "chevron.left")
                            .foregroundStyle(theme.ink)
                    }
                }
            }

            if let existing = existingTask {
                // Top-right immediate completion checkbox + crafted More menu
                ToolbarItemGroup(placement: .topBarTrailing) {
                    GokeCheckBox(
                        isChecked: isDone,
                        priority: priority,
                        listColor: selectedList.map { theme.courseColor(index: $0.colorIndex).foreground },
                        accessibilityLabel: isDone ? "标记为未完成" : "标记为已完成"
                    ) {
                        toggleExistingCompletionImmediately(task: existing, now: now)
                    }

                    Menu {
                        Button {
                            deferToTomorrow(today: today)
                        } label: {
                            Label("延期到明天", systemImage: "calendar.badge.clock")
                        }

                        Button {
                            copyTitleToPasteboard()
                        } label: {
                            Label("复制标题", systemImage: "doc.on.doc")
                        }

                        Divider()

                        Button(role: .destructive) {
                            deleteExistingTask()
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundStyle(theme.muted)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("更多操作")
                }
            } else {
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        saveTask(shouldDismiss: true)
                    }
                    .fontWeight(.semibold)
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .sheet(item: $activeModal) { modal in
            switch modal {
            case .whenPanel:
                GokeWhenPanel(
                    initialPick: effectiveWhen ?? preservedStartDate.map { WhenPick(date: $0) },
                    initialRemindList: effectiveRemind,
                    initialRemindPersistent: effectivePersistent,
                    initialFreq: repeatFreq,
                    initialInterval: repeatInterval,
                    initialUntil: repeatUntil,
                    initialByDays: repeatByDays,
                    initialByMonths: repeatByMonths,
                    semesterEnd: semesterEnd
                ) { result in
                    applyWhenPanelResult(result, parsedTokens: parsedTitle.tokens)
                }
                .gokePaperSheet(theme, detents: [.large])
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.item],
            allowsMultipleSelection: true
        ) { result in
            guard case .success(let urls) = result, !urls.isEmpty else { return }
            isImportingAttachments = true
            Task {
                var imported: [Attachment] = []
                for url in urls {
                    if let att = TodoAttachmentStore.importFileURL(url) {
                        imported.append(att)
                    }
                }
                await MainActor.run {
                    if !imported.isEmpty {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            attachments.append(contentsOf: imported)
                        }
                    }
                    isImportingAttachments = false
                }
            }
        }
        .onChange(of: photoPickerItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            let itemsToLoad = newItems
            photoPickerItems = []
            isImportingAttachments = true
            Task {
                var imported: [Attachment] = []
                for item in itemsToLoad {
                    if let data = try? await item.loadTransferable(type: Data.self) {
                        let contentType = item.supportedContentTypes.first(where: { $0.conforms(to: .image) })
                        if let attachment = TodoAttachmentStore.importImageData(data, contentType: contentType) {
                            imported.append(attachment)
                        }
                    }
                }
                await MainActor.run {
                    if !imported.isEmpty {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            attachments.append(contentsOf: imported)
                        }
                    }
                    isImportingAttachments = false
                }
            }
        }
        .confirmationDialog("放弃这条新待办？", isPresented: $showDiscardConfirm, titleVisibility: .visible) {
            Button("放弃", role: .destructive) {
                didCommitOrDiscard = true
                if let jumpDate = pendingConflictJumpDate {
                    pendingConflictJumpDate = nil
                    jumpToTimetable(date: jumpDate, live: live)
                } else {
                    dismiss()
                }
            }
            Button("继续编辑", role: .cancel) {
                pendingConflictJumpDate = nil
            }
        }
        .onAppear {
            loadInitialStateIfNeeded()
            if existingTask == nil && draft == nil && (initialTitle?.isEmpty ?? true) {
                isTitleFocused = true
            }
        }
        .onChange(of: isTitleFocused) { _, focused in
            if focused && activePanel != nil {
                closePanel()
            }
        }
        .onChange(of: focusedField) { _, field in
            if (field == .location || field == .note || field == .newSubtask) && activePanel != nil {
                closePanel()
            }
        }
        .onChange(of: courseId, initial: true) { _, newCourseId in
            if let cid = newCourseId {
                cachedCourseSessions = courseSessionOptions(courseId: cid, live: container.liveSnapshot)
                if let w = courseWeek, !cachedCourseSessions.contains(where: { $0.originalWeek == w }) {
                    courseWeek = nil
                }
            } else {
                cachedCourseSessions = []
                courseWeek = nil
            }
        }
        .onDisappear {
            // Auto-save edits silently on back/swipe-dismiss (matching Android TodoEditScreen)
            if existingTask != nil,
               !didCommitOrDiscard,
               isDirty,
               !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                saveTask(shouldDismiss: false)
            }
        }
    }

    // MARK: - Zone 1: Attribute Pills Strip, Upgraded WhenRow & Location Row

    private func attributePillsStrip(selectedCourse: Course?) -> some View {
        TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
            if priority > 0, let pColor = theme.priorityColor(priority) {
                let pTitle: String = switch priority {
                case 3: "高优先级"
                case 2: "中优先级"
                default: "低优先级"
                }
                Button {
                    togglePanel(.priority)
                } label: {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(pColor)
                            .frame(width: 7, height: 7)
                        Text(pTitle)
                            .font(GokeTypography.labelMedium.weight(.semibold))
                            .foregroundStyle(pColor)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 36)
                    .background(Capsule().fill(pColor.opacity(0.14)))
                    .contentShape(Capsule())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("优先级：\(pTitle)，轻点修改")
            }

            if let selectedCourse {
                let cPair = theme.courseColor(index: selectedCourse.colorIndex)
                let label = courseWeek.map { "\(selectedCourse.name) · 第\($0)周" } ?? selectedCourse.name
                Button {
                    togglePanel(.course)
                } label: {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(cPair.barColor)
                            .frame(width: 7, height: 7)
                        Text(label)
                            .font(GokeTypography.labelMedium.weight(.semibold))
                            .foregroundStyle(cPair.foreground)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 36)
                    .background(Capsule().fill(cPair.background))
                    .contentShape(Capsule())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("关联课程：\(label)，轻点修改")
            }

            ForEach(tags, id: \.self) { tag in
                HStack(spacing: 0) {
                    Button {
                        togglePanel(.tags)
                    } label: {
                        Text("@\(tag)")
                            .font(GokeTypography.labelMedium.weight(.medium))
                            .foregroundStyle(theme.secondary)
                            .padding(.leading, 12)
                            .padding(.trailing, 4)
                            .frame(minHeight: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("标签 @\(tag)，轻点编辑")

                    Button {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            tags.removeAll { $0 == tag }
                        }
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(theme.secondary.opacity(0.75))
                            .frame(width: 36, height: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("移除标签 @\(tag)")
                }
                .background(Capsule().fill(theme.secondaryContainer.opacity(0.55)))
            }
        }
    }

    private func whenRow(
        effectiveWhen: WhenPick?,
        effectiveRemind: [Int],
        effectivePersistent: Bool,
        selectedCourse: Course?,
        targetCourseOcc: CourseOccurrence?,
        today: LocalDate,
        nowMinute: LocalDateTime,
        semesterEnd: LocalDate?,
        parsedTokens: [QuickToken]
    ) -> some View {
        let hasAnyWhen = dueBeforeNextClass
            || effectiveWhen != nil
            || preservedStartDate != nil
            || repeatFreq != nil
            || !effectiveRemind.isEmpty

        let isOverdue: Bool = {
            guard !isDone else { return false }
            if dueBeforeNextClass {
                if let occ = targetCourseOcc {
                    return occ.start < nowMinute
                }
                return false
            }
            if let wp = effectiveWhen {
                if let start = wp.start {
                    let dueDT = wp.resolvedDue ?? wp.date.atTime(start)
                    return dueDT < nowMinute
                }
                return wp.date < today
            }
            return false
        }()

        let blockDurationText: String? = {
            guard !dueBeforeNextClass, let wp = effectiveWhen, wp.start != nil, let mins = wp.minutes, mins > 0 else {
                return nil
            }
            return GokeWhenFormatter.formatShortDuration(mins)
        }()

        let primaryText: String = {
            if dueBeforeNextClass {
                if let w = courseWeek {
                    return "第 \(w) 周课前截止"
                }
                return selectedCourse.map { "「\($0.name)」下节课前截止" } ?? "下节课前截止"
            }
            if let wp = effectiveWhen {
                if let start = wp.start, let mins = wp.minutes, mins > 0 {
                    let datePart = GokeWhenFormatter.formatWhenPick(WhenPick(date: wp.date), today: today)
                    let endTime = wp.date.atTime(start).addingMinutes(mins).time
                    return "\(datePart) \(TodoDateFormatter.hhmm(start))–\(TodoDateFormatter.hhmm(endTime))"
                }
                return GokeWhenFormatter.formatWhenPick(wp, today: today)
            }
            if let start = preservedStartDate {
                return start == today ? "计划今天" : "开始于 \(TodoDateFormatter.shortDate(start))"
            }
            return "添加日期与时间"
        }()

        let secondaryParts: [String] = {
            var parts: [String] = []
            if dueBeforeNextClass, let occ = targetCourseOcc {
                let wd = GokeWhenFormatter.weekdayChar(occ.date.dayOfWeek)
                parts.append("周\(wd) \(occ.date.month)月\(occ.date.day)日 \(TodoDateFormatter.hhmm(occ.start.time))")
            }
            if let freq = repeatFreq {
                parts.append(
                    GokeWhenFormatter.formatRecurrence(
                        freq: freq,
                        interval: repeatInterval,
                        byDays: repeatByDays,
                        byMonths: repeatByMonths,
                        until: repeatUntil,
                        anchor: effectiveWhen?.date ?? today,
                        semesterEnd: semesterEnd
                    )
                )
            }
            if !effectiveRemind.isEmpty {
                parts.append(
                    GokeWhenFormatter.formatRemindSummary(
                        list: effectiveRemind,
                        persistent: effectivePersistent,
                        hasTime: effectiveWhen?.start != nil || dueBeforeNextClass
                    )
                )
            }
            return parts
        }()

        let leadingTint: Color = isOverdue ? theme.error : (hasAnyWhen ? theme.accent : theme.muted.opacity(0.8))

        return HStack(spacing: 0) {
            Button {
                closePanel()
                isTitleFocused = false
                focusedField = nil
                activeModal = .whenPanel
            } label: {
                HStack(spacing: GokeSpacing.md) {
                    Image(systemName: dueBeforeNextClass ? "bell.badge" : (effectiveWhen?.minutes != nil ? "timer" : "calendar"))
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(leadingTint)
                        .frame(width: 22)

                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(primaryText)
                                .font(GokeTypography.bodyMedium.weight(hasAnyWhen ? .semibold : .regular))
                                .monospacedDigit()
                                .foregroundStyle(isOverdue ? theme.error : (hasAnyWhen ? theme.ink : theme.muted))
                                .lineLimit(1)

                            if isOverdue {
                                Text("已逾期")
                                    .font(GokeTypography.labelSmall.weight(.semibold))
                                    .foregroundStyle(theme.error)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(theme.errorContainer))
                            } else if let blockDurationText {
                                Text(blockDurationText)
                                    .font(GokeTypography.labelSmall.weight(.semibold))
                                    .monospacedDigit()
                                    .foregroundStyle(theme.onAccentContainer)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(theme.accentContainer.opacity(0.75)))
                            }
                        }

                        if !secondaryParts.isEmpty {
                            Text(secondaryParts.joined(separator: " · "))
                                .font(GokeTypography.labelSmall)
                                .monospacedDigit()
                                .foregroundStyle(isOverdue ? theme.error.opacity(0.85) : theme.muted)
                                .lineLimit(1)
                        }
                    }

                    Spacer(minLength: 4)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            if hasAnyWhen {
                Button {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        clearWhenAndTokens(parsedTokens: parsedTokens)
                    }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("清除时间设置")
            }
        }
    }

    private var locationRow: some View {
        HStack(spacing: GokeSpacing.md) {
            Image(systemName: "mappin.and.ellipse")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(locationText.isEmpty ? theme.muted.opacity(0.75) : theme.accent)
                .frame(width: 22)

            TextField("添加地点", text: $locationText)
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.ink)
                .focused($focusedField, equals: .location)
                .submitLabel(.next)
                .onSubmit { focusedField = .note }

            if !locationText.isEmpty && focusedField == .location {
                Button {
                    locationText = ""
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("清除地点")
            }
        }
        .frame(minHeight: 40)
    }

    // MARK: - Zone 2: Integrated Inline-Add Subtasks Section

    private var inlineSubtasksSection: some View {
        let doneCount = subtasks.filter(\.done).count
        let totalCount = subtasks.count
        let allDone = totalCount > 0 && doneCount == totalCount

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text("子任务")
                    .font(GokeTypography.labelMedium.weight(.semibold))
                    .foregroundStyle(theme.muted)

                Spacer()

                if totalCount > 0 {
                    Text("\(doneCount)/\(totalCount)")
                        .font(GokeTypography.labelSmall.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(allDone ? theme.onAccentContainer : theme.muted)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            Capsule().fill(allDone ? theme.accentContainer : theme.surfaceHighest)
                        )
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                ForEach($subtasks) { $sub in
                    HStack(spacing: 4) {
                        GokeCheckBox(
                            isChecked: sub.done,
                            priority: 0,
                            boxSize: 19,
                            accessibilityLabel: sub.done ? "标记子任务未完成" : "标记子任务已完成"
                        ) {
                            withAnimation(GokeMotion.press(policy: performance)) {
                                sub.done.toggle()
                            }
                        }

                        TextField("子任务", text: $sub.title)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(sub.done ? theme.muted : theme.ink)
                            .strikethrough(sub.done)
                            .animation(GokeMotion.press(policy: performance), value: sub.done)

                        Button {
                            withAnimation(GokeMotion.snappy(policy: performance)) {
                                subtasks.removeAll { $0.id == sub.id }
                            }
                        } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(theme.muted.opacity(0.65))
                                .frame(width: 40, height: 40)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("删除子任务")
                    }
                }

                // In-place inline-add subtask row directly inside the checklist
                HStack(spacing: 8) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(theme.accent)
                        .frame(width: 24)

                    TextField("添加子任务", text: $newSubtaskTitle)
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.ink)
                        .focused($focusedField, equals: .newSubtask)
                        .submitLabel(.done)
                        .onSubmit { appendSubtask() }

                    if !newSubtaskTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Button {
                            appendSubtask()
                        } label: {
                            Text("添加")
                                .font(GokeTypography.labelMedium.weight(.semibold))
                                .foregroundStyle(theme.onAccentContainer)
                                .padding(.horizontal, 12)
                                .frame(minHeight: 36)
                                .background(Capsule().fill(theme.accentContainer))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.mintCompact)
                    }
                }
                .padding(.leading, 8)
                .padding(.vertical, 6)
            }
        }
    }

    // MARK: - Zone 3: Quiet Provenance Metadata Footer

    private func quietMetadataFooter(task: GokeTask) -> some View {
        let updatedText = formatMetaTimestamp(task.updatedAt)
        let doneText: String? = (isDone ? task.doneAt : nil).map { formatMetaTimestamp($0) }
        let line = doneText.map { "更新于 \(updatedText) · 完成于 \($0)" } ?? "更新于 \(updatedText)"

        return Text(line)
            .font(.system(size: 11, weight: .regular))
            .monospacedDigit()
            .foregroundStyle(theme.muted.opacity(0.68))
    }

    private func formatMetaTimestamp(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        let comps = Calendar.current.dateComponents([.month, .day, .hour, .minute], from: date)
        let month = comps.month ?? 1
        let day = comps.day ?? 1
        let hour = comps.hour ?? 0
        let minute = comps.minute ?? 0
        return String(format: "%d月%d日 %02d:%02d", month, day, hour, minute)
    }

    // MARK: - Docked Bottom Panels (Directly Above Persistent Bottom Bar)

    @ViewBuilder
    private func dockedPanelContent(
        panel: TodoEditorPanel,
        lists: [TaskList],
        courses: [Course],
        existingTagPool: [String]
    ) -> some View {
        switch panel {
        case .list:
            GokeShrinkWrappingScrollView(maxHeight: 140) {
                TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    dockedChip("收集箱", isSelected: listId == nil && pendingListName == nil) {
                        listId = nil
                        pendingListName = nil
                        closePanel()
                    }
                    if let pending = pendingListName, listId == nil {
                        dockedChip("#\(pending)（新建）", isSelected: true) {
                            closePanel()
                        }
                    }
                    ForEach(lists) { list in
                        dockedChip(
                            list.name,
                            dotColor: theme.courseColor(index: list.colorIndex).foreground,
                            isSelected: listId == list.id
                        ) {
                            listId = list.id
                            pendingListName = nil
                            closePanel()
                        }
                    }
                }
            }

        case .priority:
            GokeSegmentedPill(
                options: [
                    .init(3, "高", activeTint: theme.priorityHigh),
                    .init(2, "中", activeTint: theme.priorityMedium),
                    .init(1, "低", activeTint: theme.priorityLow),
                    .init(0, "无", activeTint: theme.ink),
                ],
                selection: Binding(
                    get: { priority },
                    set: { level in
                        priority = level
                        Task { @MainActor in
                            closePanel()
                        }
                    }
                ),
                height: 44
            )

        case .tags:
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text("@")
                        .font(GokeTypography.titleMedium.weight(.semibold))
                        .foregroundStyle(theme.secondary)
                    TextField("新标签", text: $newTagText)
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.ink)
                        .focused($focusedField, equals: .newTag)
                        .submitLabel(.done)
                        .onSubmit { addTag() }
                    if !newTagText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Button {
                            addTag()
                        } label: {
                            Text("添加")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.onAccent)
                                .padding(.horizontal, 14)
                                .frame(minHeight: 36)
                                .background(Capsule().fill(theme.accent))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.mintCompact)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                        .fill(theme.surfaceHighest)
                )

                if !existingTagPool.isEmpty {
                    GokeShrinkWrappingScrollView(maxHeight: 112) {
                        TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                            ForEach(existingTagPool, id: \.self) { tag in
                                let selected = tags.contains { $0.localizedCaseInsensitiveCompare(tag) == .orderedSame }
                                dockedChip("@\(tag)", isSelected: selected) {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        if selected {
                                            tags.removeAll { $0.localizedCaseInsensitiveCompare(tag) == .orderedSame }
                                        } else {
                                            tags.append(tag)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        case .course:
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text("关联课程")
                        .font(GokeTypography.labelMedium.weight(.semibold))
                        .foregroundStyle(theme.muted)
                    Spacer()
                    if courseId != nil {
                        Button {
                            courseId = nil
                            courseWeek = nil
                            dueBeforeNextClass = false
                            closePanel()
                        } label: {
                            Text("取消关联")
                                .font(GokeTypography.labelMedium.weight(.semibold))
                                .foregroundStyle(theme.muted)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)

                        Button {
                            closePanel()
                        } label: {
                            Text("完成")
                                .font(GokeTypography.labelMedium.weight(.semibold))
                                .foregroundStyle(theme.accent)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                    }
                }

                if courseId != nil {
                    Toggle("下节课前截止", isOn: Binding(
                        get: { dueBeforeNextClass },
                        set: { enabled in
                            dueBeforeNextClass = enabled
                            if enabled {
                                // Android parity: enabling clears manual due/repeat
                                whenPick = nil
                                preservedStartDate = nil
                                repeatFreq = nil
                                repeatInterval = 1
                                repeatUntil = nil
                                repeatByDays = []
                                repeatByMonths = []
                            }
                        }
                    ))
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                    .padding(.horizontal, 4)
                }

                if courses.isEmpty {
                    Text("暂无课程")
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.muted)
                } else {
                    GokeShrinkWrappingScrollView(maxHeight: 156) {
                        VStack(spacing: 2) {
                            ForEach(courses) { course in
                                let selected = courseId == course.id
                                let color = theme.courseColor(index: course.colorIndex)
                                Button {
                                    if selected {
                                        courseId = nil
                                        courseWeek = nil
                                        dueBeforeNextClass = false
                                        closePanel()
                                    } else {
                                        courseId = course.id
                                        courseWeek = nil
                                        let sessions = courseSessionOptions(courseId: course.id, live: container.liveSnapshot)
                                        cachedCourseSessions = sessions
                                        // Android parity: auto-enable dueBeforeNextClass when no schedule set
                                        if whenPick == nil && preservedStartDate == nil {
                                            dueBeforeNextClass = true
                                        }
                                        if sessions.isEmpty {
                                            closePanel()
                                        }
                                    }
                                } label: {
                                    HStack(spacing: 10) {
                                        RoundedRectangle(cornerRadius: 2, style: .continuous)
                                            .fill(color.barColor)
                                            .frame(width: 4, height: 20)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(course.name)
                                                .font(GokeTypography.bodyMedium.weight(selected ? .semibold : .regular))
                                                .foregroundStyle(selected ? theme.accent : theme.ink)
                                                .lineLimit(1)
                                            if !course.teacher.isEmpty {
                                                Text(course.teacher)
                                                    .font(GokeTypography.labelSmall)
                                                    .foregroundStyle(theme.muted)
                                                    .lineLimit(1)
                                            }
                                        }
                                        Spacer(minLength: 4)
                                        if selected {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundStyle(theme.accent)
                                        }
                                    }
                                    .padding(.horizontal, 10)
                                    .frame(minHeight: 42)
                                    .background(
                                        RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                            .fill(selected ? theme.accentContainer.opacity(0.5) : Color.clear)
                                    )
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)
                            }
                        }
                    }
                }

                if courseId != nil, !cachedCourseSessions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            dockedChip("自动下节课", isSelected: courseWeek == nil) {
                                courseWeek = nil
                                closePanel()
                            }
                            ForEach(cachedCourseSessions.prefix(12), id: \.id) { occ in
                                let label = "第\(occ.week)周 \(occ.date.month)/\(occ.date.day)"
                                dockedChip(label, isSelected: courseWeek == occ.originalWeek) {
                                    courseWeek = occ.originalWeek
                                    closePanel()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func dockedChip(
        _ title: String,
        dotColor: Color? = nil,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let dotColor {
                    Circle()
                        .fill(isSelected ? theme.onAccentContainer : dotColor)
                        .frame(width: 7, height: 7)
                }
                Text(title)
                    .font(GokeTypography.labelLarge.weight(isSelected ? .semibold : .regular))
                    .monospacedDigit()
                    .lineLimit(1)
            }
            .foregroundStyle(isSelected ? theme.onAccentContainer : theme.ink)
            .padding(.horizontal, 13)
            .frame(minHeight: 38)
            .background(Capsule().fill(isSelected ? theme.accentContainer : theme.surfaceHighest))
            .contentShape(Capsule())
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - Persistent Bottom Action Toolbar (Balanced >=42pt Targets + 1-Tap Pickers)

    private func bottomActionToolbar(
        selectedList: TaskList?,
        onTapSubtasks: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 0) {
            // Left: List Pill (protected with layoutPriority so 375pt screens never crush it)
            Button {
                togglePanel(.list)
            } label: {
                HStack(spacing: 6) {
                    if let selectedList {
                        Circle()
                            .fill(theme.courseColor(index: selectedList.colorIndex).foreground)
                            .frame(width: 8, height: 8)
                    } else {
                        Image(systemName: "tray")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(activePanel == .list ? theme.accent : theme.muted)
                    }
                    Text(selectedList?.name ?? pendingListName ?? "收集箱")
                        .font(GokeTypography.labelLarge.weight(selectedList != nil || pendingListName != nil ? .semibold : .medium))
                        .foregroundStyle(activePanel == .list ? theme.accent : theme.ink)
                        .lineLimit(1)
                }
                .padding(.horizontal, 8)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintCompact)
            .layoutPriority(1)

            Spacer(minLength: 4)

            // Right: Subtasks (inline scroll+focus) · Course · Priority · Tag · 1-Tap Photo · 1-Tap File
            HStack(spacing: 0) {
                bottomBarIcon(
                    "checklist",
                    semanticTint: !subtasks.isEmpty ? theme.accent : nil,
                    active: showInlineSubtasks && focusedField == .newSubtask,
                    accessibilityLabel: "添加子任务",
                    action: onTapSubtasks
                )

                bottomBarIcon(
                    "graduationcap",
                    semanticTint: courseId != nil ? theme.accent : nil,
                    active: activePanel == .course,
                    showDotBadge: courseId != nil,
                    accessibilityLabel: "关联课程"
                ) {
                    togglePanel(.course)
                }

                bottomBarIcon(
                    priority > 0 ? "flag.fill" : "flag",
                    semanticTint: theme.priorityColor(priority),
                    active: activePanel == .priority,
                    accessibilityLabel: "优先级"
                ) {
                    togglePanel(.priority)
                }

                bottomBarIcon(
                    "tag",
                    semanticTint: !tags.isEmpty ? theme.secondary : nil,
                    active: activePanel == .tags,
                    accessibilityLabel: "标签"
                ) {
                    togglePanel(.tags)
                }

                // 1-Tap Direct Image Picker (>=42x44pt target)
                PhotosPicker(
                    selection: $photoPickerItems,
                    maxSelectionCount: 9,
                    matching: .images,
                    photoLibrary: .shared()
                ) {
                    Image(systemName: isImportingAttachments ? "hourglass" : "photo")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(attachments.contains(where: \.isImage) ? theme.accent : theme.muted)
                        .frame(width: 42, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(isImportingAttachments)
                .accessibilityLabel("添加图片附件")

                // 1-Tap Direct File Picker (>=42x44pt target)
                Button {
                    closePanel()
                    showFileImporter = true
                } label: {
                    Image(systemName: "doc")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(attachments.contains(where: { !$0.isImage }) ? theme.accent : theme.muted)
                        .frame(width: 42, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(isImportingAttachments)
                .accessibilityLabel("添加文件附件")
            }
        }
        .padding(.horizontal, GokeSpacing.sm)
        .padding(.vertical, 2)
        .background(activePanel != nil ? theme.surface : theme.paper)
    }

    private func bottomBarIcon(
        _ systemName: String,
        semanticTint: Color?,
        active: Bool,
        showDotBadge: Bool = false,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        let iconTint = semanticTint ?? (active ? theme.accent : theme.muted)
        return Button(action: action) {
            ZStack(alignment: .bottom) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemName)
                        .font(.system(size: 18, weight: (active || semanticTint != nil) ? .semibold : .medium))
                        .foregroundStyle(iconTint)
                        .frame(width: 42, height: 44)
                    if showDotBadge && !active {
                        Circle()
                            .fill(theme.accent)
                            .frame(width: 6, height: 6)
                            .offset(x: -7, y: 9)
                    }
                }
                if active {
                    Capsule()
                        .fill(iconTint)
                        .frame(width: 14, height: 2.5)
                        .padding(.bottom, 4)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel(accessibilityLabel)
    }

    private func togglePanel(_ panel: TodoEditorPanel) {
        let isClosing = activePanel == panel
        let anim = isClosing
            ? GokeMotion.dismiss(policy: performance)
            : GokeMotion.snappy(policy: performance)
        if !isClosing && panel == .course {
            // Tall course panel needs breathing room on compact screens
            isTitleFocused = false
            focusedField = nil
        }
        withAnimation(anim) {
            activePanel = isClosing ? nil : panel
        }
        if activePanel == .tags {
            focusedField = .newTag
        }
    }

    private func closePanel() {
        guard activePanel != nil else { return }
        withAnimation(GokeMotion.dismiss(policy: performance)) {
            activePanel = nil
        }
    }

    // MARK: - Immediate Completion & Quick Menu Actions

    private func toggleExistingCompletionImmediately(task: GokeTask, now: LocalDateTime) {
        let nextDone = !isDone
        withAnimation(GokeMotion.press(policy: performance)) {
            isDone = nextDone
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs: ChangeSet
        let label: String
        if nextDone {
            let (finished, next) = TaskLogic.complete(task, nowMillis: nowMillis, now: now)
            var changes: [Change] = [.modify(before: .task(task), after: .task(finished))]
            if let next {
                changes.append(.add(after: .task(next)))
            }
            cs = ChangeSet(
                description: "完成「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: changes
            )
            label = next != nil ? "已完成并生成下一次重复任务" : "已完成「\(task.title)」"
        } else {
            let reopened = TaskLogic.reopen(task, nowMillis: nowMillis)
            cs = ChangeSet(
                description: "重新打开「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.modify(before: .task(task), after: .task(reopened))]
            )
            label = "已重新打开「\(task.title)」"
        }
        if !isDirty {
            initialSignature = currentSignature
        }
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok && !isDirty {
                initialSignature = currentSignature
            }
        }
    }

    private func deferToTomorrow(today: LocalDate) {
        let tomorrow = today.addingDays(1)
        withAnimation(GokeMotion.snappy(policy: performance)) {
            dueBeforeNextClass = false
            preservedStartDate = nil
            if let wp = whenPick {
                whenPick = WhenPick(date: tomorrow, start: wp.start, minutes: wp.minutes)
            } else {
                whenPick = WhenPick(date: tomorrow)
            }
        }
        actions.showToast(message: "已延期到明天")
    }

    private func copyTitleToPasteboard() {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        #if canImport(UIKit)
        UIPasteboard.general.string = trimmed
        #endif
        actions.showToast(message: "已复制标题")
    }

    private func handleConflictTap(date: LocalDate, live: AppSnapshot) {
        if needsDiscardConfirm {
            pendingConflictJumpDate = date
            showDiscardConfirm = true
        } else {
            if existingTask != nil && isDirty && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                saveTask(shouldDismiss: false)
            }
            jumpToTimetable(date: date, live: live)
        }
    }

    private func jumpToTimetable(date: LocalDate, live: AppSnapshot) {
        if let sem = live.currentSemester {
            let week = SemesterCalendar(semester: sem).weekOf(date)
            if week >= 1 && week <= sem.totalWeeks {
                router.requestedTimetableWeek = week
            }
        }
        router.selectedTab = .timetable
        dismiss()
    }

    // MARK: - Lifecycle, When Sync & Persistence

    private func applyWhenPanelResult(_ result: WhenPanelResult, parsedTokens: [QuickToken]) {
        if result.whenPick == nil && result.remindMinutesList.isEmpty && result.freq == nil {
            clearWhenAndTokens(parsedTokens: parsedTokens)
            return
        }
        dueBeforeNextClass = false
        preservedStartDate = nil
        whenPick = result.whenPick
        repeatFreq = result.freq
        repeatInterval = result.interval
        repeatUntil = result.until
        repeatByDays = result.byDays
        repeatByMonths = result.byMonths
        remindMinutesList = result.remindMinutesList
        remindPersistent = result.remindPersistent

        // Strip conflicting DATE/TIME/REMIND tokens from title so SmartTitle stays cleanly in sync
        var stripKinds: Set<QuickTokenKind> = [.date, .time]
        if !result.remindMinutesList.isEmpty {
            stripKinds.insert(.remind)
        }
        if parsedTokens.contains(where: { stripKinds.contains($0.kind) }) {
            let rewrite = QuickAddParser.rewriteQuickField(
                current: title,
                tokens: parsedTokens,
                kinds: stripKinds,
                add: nil
            )
            if !rewrite.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                title = rewrite.text
                titleCursorRequest = GokeCursorRequest(utf16Offset: rewrite.cursorUTF16)
            }
        }
    }

    private func clearWhenAndTokens(parsedTokens: [QuickToken]) {
        dueBeforeNextClass = false
        whenPick = nil
        preservedStartDate = nil
        repeatFreq = nil
        repeatInterval = 1
        repeatUntil = nil
        repeatByDays = []
        repeatByMonths = []
        remindMinutesList = []
        remindPersistent = false

        let stripKinds: Set<QuickTokenKind> = [.date, .time, .remind]
        if parsedTokens.contains(where: { stripKinds.contains($0.kind) }) {
            let rewrite = QuickAddParser.rewriteQuickField(
                current: title,
                tokens: parsedTokens,
                kinds: stripKinds,
                add: nil
            )
            if !rewrite.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                title = rewrite.text
                titleCursorRequest = GokeCursorRequest(utf16Offset: rewrite.cursorUTF16)
            }
        }
    }

    private func handleBackOrClose() {
        if existingTask != nil {
            if isDirty && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                saveTask(shouldDismiss: true)
            } else {
                didCommitOrDiscard = true
                dismiss()
            }
        } else {
            if needsDiscardConfirm {
                showDiscardConfirm = true
            } else {
                didCommitOrDiscard = true
                dismiss()
            }
        }
    }

    private func loadInitialStateIfNeeded() {
        guard !didLoad else { return }
        didLoad = true

        if let task = existingTask {
            title = task.title
            locationText = task.extra["location"] ?? ""
            note = task.note
            priority = task.priority
            listId = task.listId
            courseId = task.courseId
            courseWeek = task.courseWeek
            dueBeforeNextClass = task.dueBeforeNextClass
            isDone = task.done

            if let block = task.blockStart {
                whenPick = WhenPick(date: block.date, start: block.time, minutes: task.blockMinutes ?? 30)
            } else if let due = task.due {
                whenPick = WhenPick(date: due.date, start: task.dueHasTime ? due.time : nil, minutes: nil)
            }
            preservedStartDate = task.startDate
            estimateMinutes = task.estimateMinutes

            if let rep = task.`repeat` {
                repeatFreq = rep.freq
                repeatInterval = max(1, rep.interval)
                repeatByDays = rep.byDays
                repeatByMonths = rep.byMonths
                repeatUntil = rep.until
                preservedRepeatCount = rep.count
                preservedRepeatExDates = rep.exDates
            }
            remindMinutesList = task.effectiveRemindMinutes
            remindPersistent = task.remindPersistent
            tags = task.tags
            attachments = task.attachments
            if let colsStr = task.extra["imageCols"], let cols = Int(colsStr) {
                imageCols = cols
            }
            subtasks = TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: task.id).map {
                SubtaskDraft(id: $0.id, title: $0.title, done: $0.done, existing: $0)
            }
        } else if let draft {
            title = draft.title
            priority = draft.priority
            listId = draft.listId
            pendingListName = draft.pendingListName
            courseId = draft.courseId
            courseWeek = draft.courseWeek
            dueBeforeNextClass = draft.dueBeforeNextClass
            if let block = draft.blockStart {
                whenPick = WhenPick(date: block.date, start: block.time, minutes: draft.blockMinutes ?? 30)
            } else if let due = draft.due {
                whenPick = WhenPick(date: due.date, start: draft.dueHasTime ? due.time : nil, minutes: nil)
            }
            preservedStartDate = draft.startDate
            if let rep = draft.repeatRule {
                repeatFreq = rep.freq
                repeatInterval = max(1, rep.interval)
                repeatByDays = rep.byDays
                repeatByMonths = rep.byMonths
                repeatUntil = rep.until
            }
            remindMinutesList = draft.remindMinutesList
            remindPersistent = draft.remindPersistent
            tags = draft.tags
        } else {
            if let initialTitle { title = initialTitle }
            if let initialDate {
                whenPick = WhenPick(date: initialDate)
            }
            if let initialCourseId {
                courseId = initialCourseId
                courseWeek = initialCourseWeek
                dueBeforeNextClass = initialDate == nil
            }
        }

        if let cid = courseId {
            cachedCourseSessions = courseSessionOptions(courseId: cid, live: container.liveSnapshot)
        }
        initialSignature = currentSignature
    }

    private func addTag() {
        let trimmed = newTagText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "^@+", with: "", options: .regularExpression)
        guard !trimmed.isEmpty else { return }
        if !tags.contains(where: { $0.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }) {
            withAnimation(GokeMotion.snappy(policy: performance)) {
                tags.append(trimmed)
            }
        }
        newTagText = ""
    }

    private func appendSubtask() {
        let trimmed = newSubtaskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        withAnimation(GokeMotion.snappy(policy: performance)) {
            subtasks.append(
                SubtaskDraft(
                    id: UUID().uuidString.lowercased(),
                    title: trimmed,
                    done: false,
                    existing: nil
                )
            )
        }
        newSubtaskTitle = ""
    }

    private func saveTask(shouldDismiss: Bool) {
        // Flush any uncommitted newSubtaskTitle automatically before saving!
        let pendingSub = newSubtaskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        var finalSubtasks = subtasks
        if !pendingSub.isEmpty {
            finalSubtasks.append(
                SubtaskDraft(
                    id: UUID().uuidString.lowercased(),
                    title: pendingSub,
                    done: false,
                    existing: nil
                )
            )
            subtasks = finalSubtasks
            newSubtaskTitle = ""
        }

        let now = LocalDateTime.now()
        let parsedTitle = smartTitleParse(now: now)
        let effectiveWhen = resolvedWhenPick(from: parsedTitle)
        let effectiveRemind = resolvedRemindList(from: parsedTitle)
        let effectivePersistent = resolvedRemindPersistent(from: parsedTitle, effectiveList: effectiveRemind)

        // Strip auto-applied DATE/TIME/REMIND tokens cleanly from title if non-empty body remains
        let usedSmartWhen = (whenPick == nil && !dueBeforeNextClass && effectiveWhen != nil)
        let usedSmartRemind = (remindMinutesList.isEmpty && !effectiveRemind.isEmpty)
        let finalTitle: String = {
            if usedSmartWhen || usedSmartRemind {
                var stripKinds: Set<QuickTokenKind> = []
                if usedSmartWhen { stripKinds.formUnion([.date, .time]) }
                if usedSmartRemind { stripKinds.insert(.remind) }
                let rewritten = QuickAddParser.rewriteQuickField(
                    current: title,
                    tokens: parsedTitle.tokens,
                    kinds: stripKinds,
                    add: nil
                ).text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !rewritten.isEmpty {
                    return rewritten
                }
            }
            return title.trimmingCharacters(in: .whitespacesAndNewlines)
        }()

        guard !finalTitle.isEmpty else { return }
        didCommitOrDiscard = true

        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []

        // Auto-create pending #listName if transferred from QuickAdd
        var resolvedListId = listId
        if resolvedListId == nil,
           let rawListName = pendingListName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !rawListName.isEmpty {
            let newList = TaskList(
                name: rawListName,
                colorIndex: (container.liveSnapshot.lists.count + 1) % max(theme.courseColors.count, 1),
                sort: container.liveSnapshot.lists.count,
                updatedAt: nowMillis
            )
            changes.append(.add(after: .taskList(newList)))
            resolvedListId = newList.id
        }

        let resolvedDue: LocalDateTime?
        let resolvedDueHasTime: Bool
        let resolvedBlockStart: LocalDateTime?
        let resolvedBlockMinutes: Int?
        let resolvedStartDate: LocalDate?

        if dueBeforeNextClass {
            resolvedDue = nil
            resolvedDueHasTime = false
            resolvedBlockStart = nil
            resolvedBlockMinutes = nil
            resolvedStartDate = nil
        } else if let wp = effectiveWhen {
            // Time-block sets due = blockStart + blockMinutes!
            resolvedDue = wp.resolvedDue
            resolvedDueHasTime = wp.resolvedDueHasTime
            resolvedBlockStart = wp.resolvedBlockStart
            resolvedBlockMinutes = wp.resolvedBlockMinutes
            resolvedStartDate = preservedStartDate
        } else {
            resolvedDue = nil
            resolvedDueHasTime = false
            resolvedBlockStart = nil
            resolvedBlockMinutes = nil
            resolvedStartDate = preservedStartDate
        }

        let resolvedRepeat: Recurrence? = repeatFreq.map { freq in
            Recurrence(
                freq: freq,
                interval: max(1, repeatInterval),
                byDays: (freq == .WEEKLY || freq == .MONTHLY || freq == .YEARLY) ? repeatByDays : [],
                until: repeatUntil,
                count: preservedRepeatCount,
                exDates: preservedRepeatExDates,
                byMonths: freq == .YEARLY ? repeatByMonths : []
            )
        }

        var nextExtra = existingTask?.extra ?? [:]
        let trimmedLocation = locationText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedLocation.isEmpty {
            nextExtra.removeValue(forKey: "location")
        } else {
            nextExtra["location"] = trimmedLocation
        }
        if let imageCols {
            nextExtra["imageCols"] = String(min(max(imageCols, 1), 3))
        } else {
            nextExtra.removeValue(forKey: "imageCols")
        }

        let parentIdValue = existingTask?.id ?? UUID().uuidString.lowercased()
        let baseTask = GokeTask(
            id: parentIdValue,
            title: finalTitle,
            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            listId: resolvedListId,
            tags: tags,
            priority: priority,
            startDate: resolvedStartDate,
            due: resolvedDue,
            dueHasTime: resolvedDueHasTime,
            estimateMinutes: estimateMinutes,
            done: isDone,
            doneAt: isDone ? (existingTask?.doneAt ?? nowMillis) : nil,
            dropped: isDone ? (existingTask?.dropped ?? false) : false,
            parentId: existingTask?.parentId,
            repeat: resolvedRepeat,
            remindMinutes: effectiveRemind.first,
            remindPersistent: effectivePersistent,
            courseId: courseId,
            dueBeforeNextClass: courseId != nil && dueBeforeNextClass,
            blockStart: resolvedBlockStart,
            blockMinutes: resolvedBlockMinutes,
            sortOrder: existingTask?.sortOrder ?? 0,
            source: existingTask?.source ?? .MANUAL,
            extra: nextExtra,
            attachments: attachments,
            updatedAt: nowMillis,
            deletedAt: nil,
            userId: existingTask?.userId,
            remindMinutesList: effectiveRemind
        )
        let updatedTask = baseTask.withCourseLink(
            courseId: courseId,
            courseWeek: courseWeek,
            dueBeforeNextClass: dueBeforeNextClass
        )

        if let existingTask {
            changes.append(.modify(before: .task(existingTask), after: .task(updatedTask)))
        } else {
            changes.append(.add(after: .task(updatedTask)))
        }

        // Reconcile subtasks atomically
        let existingSubtasks = existingTask.map { TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: $0.id) } ?? []
        let keptIds = Set(finalSubtasks.map(\.id))
        for removed in existingSubtasks where !keptIds.contains(removed.id) {
            changes.append(.remove(before: .task(removed)))
        }
        for (idx, subDraft) in finalSubtasks.enumerated() {
            let subTitle = subDraft.title.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !subTitle.isEmpty else { continue }
            if let oldSub = subDraft.existing {
                var nextSub = oldSub
                nextSub.title = subTitle
                nextSub.done = subDraft.done
                nextSub.doneAt = subDraft.done ? (oldSub.doneAt ?? nowMillis) : nil
                nextSub.listId = resolvedListId
                nextSub.sortOrder = idx
                nextSub.updatedAt = nowMillis
                if !Record.task(oldSub).hasSameContent(as: .task(nextSub)) {
                    changes.append(.modify(before: .task(oldSub), after: .task(nextSub)))
                }
            } else {
                let newSub = GokeTask(
                    id: subDraft.id,
                    title: subTitle,
                    listId: resolvedListId,
                    done: subDraft.done,
                    doneAt: subDraft.done ? nowMillis : nil,
                    parentId: parentIdValue,
                    sortOrder: idx,
                    source: .MANUAL,
                    updatedAt: nowMillis
                )
                changes.append(.add(after: .task(newSub)))
            }
        }

        let cs = ChangeSet(
            description: existingTask == nil ? "新建待办「\(finalTitle)」" : "更新待办「\(finalTitle)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        let label = contextualSaveLabel(
            isNew: existingTask == nil,
            effectiveWhen: effectiveWhen,
            resolvedStartDate: resolvedStartDate,
            today: now.date
        )
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok {
                onCommitted?()
                if shouldDismiss { dismiss() }
            } else {
                didCommitOrDiscard = false
            }
        }
    }

    private func contextualSaveLabel(
        isNew: Bool,
        effectiveWhen: WhenPick?,
        resolvedStartDate: LocalDate?,
        today: LocalDate
    ) -> String {
        guard isNew else { return "已保存更改" }
        let courseName = courseId.flatMap { cid in
            container.liveSnapshot.courses.first(where: { $0.id == cid })?.name
        }
        if dueBeforeNextClass, let courseName {
            return "已加入「\(courseName)」课前待办"
        }
        if let wp = effectiveWhen {
            return "已加入 \(GokeWhenFormatter.formatWhenPick(wp, today: today))"
        }
        if let s = resolvedStartDate {
            return s == today ? "已加入今天" : "已加入 \(TodoDateFormatter.shortDate(s))"
        }
        if let courseName {
            return "已关联至「\(courseName)」"
        }
        return "已加入收集箱"
    }

    private func deleteExistingTask() {
        guard let existingTask else { return }
        didCommitOrDiscard = true
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = [.remove(before: .task(existingTask))]
        for sub in TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: existingTask.id) {
            changes.append(.remove(before: .task(sub)))
        }
        let cs = ChangeSet(
            description: "删除待办「\(existingTask.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已移入回收站")
            if ok { dismiss() }
        }
    }

    private func courseSessionOptions(courseId: String, live: AppSnapshot) -> [CourseOccurrence] {
        guard let semester = live.currentSemester,
              let course = live.courses.first(where: { $0.id == courseId && $0.deletedAt == nil })
        else { return [] }
        return course.weeks.sorted().compactMap { w in
            CourseExpander.occurrence(
                semester: semester,
                courses: live.courses,
                exceptions: live.exceptions,
                courseId: course.id,
                originalWeek: w
            )
        }.sorted { $0.start < $1.start }
    }
}

// MARK: - MorningTidySheet (Frameless Mint Paper)

/// Overdue-task triage sheet matching Android `MorningTidySheet`:
/// uses `effectiveDue` timeline `.OVERDUE` bucket, `TaskLogic.rescheduled(..., clearTimeIfPast:)`
/// when moving to Today, batch Drop ("全部不做了"), double-tap `handledIds` guard, and auto-dismiss.
struct MorningTidySheet: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var handledIds: Set<String> = []
    @State private var sawNonEmpty: Bool = false

    init() {}

    private func overdueTasks(live: AppSnapshot, now: LocalDateTime) -> [GokeTask] {
        let sections = TaskLogic.timeline(live.tasks, now: now) { task in
            TaskLogic.effectiveDue(
                task: task,
                semester: live.currentSemester,
                courses: live.courses,
                exceptions: live.exceptions,
                now: now
            )
        }
        return sections.first(where: { $0.bucket == .OVERDUE })?.tasks ?? []
    }

    var body: some View {
        let now = LocalDateTime.now()
        let live = container.liveSnapshot
        let overdue = overdueTasks(live: live, now: now)

        NavigationStack {
            Group {
                if overdue.isEmpty {
                    GokeEmptyState(
                        systemImage: "sparkles",
                        title: "逾期已清空",
                        primaryActionTitle: "完成",
                        onPrimaryAction: { dismiss() }
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(theme.paper)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                            // Batch Quick Actions Card
                            GokePaperCard {
                                VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                                    HStack(spacing: 8) {
                                        tidyBatchPill("全部改到今天", icon: "sun.max", tint: theme.accent) {
                                            deferAll(overdue, to: now.date, now: now, baseLabel: "已全部移至今日")
                                        }
                                        tidyBatchPill("全部改到明天", icon: "sunrise", tint: theme.accent) {
                                            deferAll(overdue, to: now.date.addingDays(1), now: now, baseLabel: "已全部顺延至明日")
                                        }
                                    }

                                    Button {
                                        dropAll(overdue)
                                    } label: {
                                        Label("全部不做了", systemImage: "xmark.circle")
                                            .font(GokeTypography.labelLarge)
                                            .foregroundStyle(theme.removed)
                                            .frame(maxWidth: .infinity, minHeight: 38)
                                            .background(
                                                RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                                    .fill(theme.removed.opacity(0.1))
                                            )
                                    }
                                    .buttonStyle(.mintCompact)
                                }
                            }

                            // Per-task rows
                            VStack(spacing: 0) {
                                ForEach(Array(overdue.enumerated()), id: \.element.id) { idx, task in
                                    if idx > 0 {
                                        Rectangle()
                                            .fill(theme.outlineVariant.opacity(0.45))
                                            .frame(height: 0.5)
                                            .padding(.leading, GokeSpacing.md)
                                    }
                                    tidyTaskRow(task, live: live, now: now)
                                }
                            }
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                    .fill(theme.surface)
                            )
                            .animation(GokeMotion.snappy(reduceMotion: reduceMotion), value: overdue.map(\.id))
                        }
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.vertical, GokeSpacing.md)
                    }
                    .background(theme.paper)
                }
            }
            .toolbarBackground(theme.paper, for: .navigationBar)
            .navigationTitle(overdue.isEmpty ? "整理逾期" : "整理 \(overdue.count) 条逾期")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .onAppear {
                if !overdue.isEmpty { sawNonEmpty = true }
            }
            .onChange(of: overdue.isEmpty) { _, isEmpty in
                if isEmpty && sawNonEmpty { dismiss() }
            }
        }
        .gokePaperSheet(theme, detents: [.medium, .large])
    }

    private func tidyBatchPill(_ title: String, icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(GokeTypography.labelMedium.weight(.semibold))
                .foregroundStyle(theme.onAccentContainer)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                        .fill(theme.accentContainer)
                )
        }
        .buttonStyle(.mintCompact)
    }

    private func tidyTaskRow(_ task: GokeTask, live: AppSnapshot, now: LocalDateTime) -> some View {
        let effDue = TaskLogic.effectiveDue(
            task: task,
            semester: live.currentSemester,
            courses: live.courses,
            exceptions: live.exceptions,
            now: now
        )
        let disabled = handledIds.contains(task.id)
        return VStack(alignment: .leading, spacing: 8) {
            Button {
                let targetId = task.id
                let routerRef = router
                dismiss()
                Task { @MainActor in
                    routerRef.present(.todoEdit(taskId: targetId))
                }
            } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text(task.title)
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    if let due = effDue {
                        Label(
                            "原定 \(TodoDateFormatter.dueLabel(due: due, hasTime: task.dueHasTime || task.dueBeforeNextClass, now: now, dueBeforeNextClass: task.dueBeforeNextClass))",
                            systemImage: "clock"
                        )
                        .font(GokeTypography.labelMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.removed)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            HStack(spacing: 6) {
                tidyInlineAction("今日", tint: theme.accent, disabled: disabled) {
                    reschedule(task, to: now.date, now: now, baseLabel: "已移至今天")
                }
                tidyInlineAction("明日", tint: theme.accent, disabled: disabled) {
                    reschedule(task, to: now.date.addingDays(1), now: now, baseLabel: "已移至明天")
                }
                tidyInlineAction("完成", tint: theme.added, disabled: disabled) {
                    complete(task, now: now)
                }
                Spacer(minLength: 0)
                tidyInlineAction("不做了", tint: theme.muted, disabled: disabled) {
                    dropTask(task)
                }
            }
        }
        .padding(.horizontal, GokeSpacing.md)
        .padding(.vertical, 12)
    }

    private func tidyInlineAction(_ title: String, tint: Color, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(GokeTypography.labelMedium.weight(.semibold))
                .foregroundStyle(tint)
                .padding(.horizontal, 11)
                .frame(minHeight: 30)
                .background(Capsule().fill(tint.opacity(0.12)))
        }
        .buttonStyle(.mintCompact)
        .disabled(disabled)
    }

    private func reschedule(_ task: GokeTask, to targetDate: LocalDate, now: LocalDateTime, baseLabel: String) {
        guard handledIds.insert(task.id).inserted else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let result = TaskLogic.rescheduled(
            task,
            to: targetDate,
            nowMillis: nowMillis,
            clearTimeIfPast: targetDate == now.date ? now : nil
        )
        let cs = ChangeSet(
            description: "调整「\(task.title)」日期",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.modify(before: .task(task), after: .task(result.task))]
        )
        let label = result.clearedPastTime ? "\(baseLabel)（已清除过期时间）" : baseLabel
        Task {
            let ok = await actions.commit(cs, label: label)
            if !ok { handledIds.remove(task.id) }
        }
    }

    private func deferAll(_ tasks: [GokeTask], to targetDate: LocalDate, now: LocalDateTime, baseLabel: String) {
        let pending = tasks.filter { !handledIds.contains($0.id) }
        guard !pending.isEmpty else { return }
        for t in pending { handledIds.insert(t.id) }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var anyClearedPastTime = false
        let changes: [Change] = pending.map { task in
            let result = TaskLogic.rescheduled(
                task,
                to: targetDate,
                nowMillis: nowMillis,
                clearTimeIfPast: targetDate == now.date ? now : nil
            )
            if result.clearedPastTime { anyClearedPastTime = true }
            return .modify(before: .task(task), after: .task(result.task))
        }
        let cs = ChangeSet(
            description: "晨间整理：顺延 \(pending.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        let label = anyClearedPastTime ? "\(baseLabel)（已清除过期时间）" : baseLabel
        Task {
            let ok = await actions.commit(cs, label: label)
            if !ok {
                for t in pending { handledIds.remove(t.id) }
            }
        }
    }

    private func dropAll(_ tasks: [GokeTask]) {
        let pending = tasks.filter { !handledIds.contains($0.id) }
        guard !pending.isEmpty else { return }
        for t in pending { handledIds.insert(t.id) }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let changes: [Change] = pending.map { task in
            var updated = task
            updated.done = true
            updated.dropped = true
            updated.doneAt = nowMillis
            updated.updatedAt = nowMillis
            return .modify(before: .task(task), after: .task(updated))
        }
        let cs = ChangeSet(
            description: "晨间整理：放弃 \(pending.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已放弃 \(pending.count) 项待办")
            if !ok {
                for t in pending { handledIds.remove(t.id) }
            }
        }
    }

    private func complete(_ task: GokeTask, now: LocalDateTime) {
        guard handledIds.insert(task.id).inserted else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let (finished, next) = TaskLogic.complete(task, nowMillis: nowMillis, now: now)
        var changes: [Change] = [.modify(before: .task(task), after: .task(finished))]
        if let next { changes.append(.add(after: .task(next))) }
        let cs = ChangeSet(
            description: "完成「\(task.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已完成「\(task.title)」")
            if !ok { handledIds.remove(task.id) }
        }
    }

    private func dropTask(_ task: GokeTask) {
        guard handledIds.insert(task.id).inserted else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var updated = task
        updated.done = true
        updated.dropped = true
        updated.doneAt = nowMillis
        updated.updatedAt = nowMillis
        let cs = ChangeSet(
            description: "放弃「\(task.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.modify(before: .task(task), after: .task(updated))]
        )
        Task {
            let ok = await actions.commit(cs, label: "已放弃「\(task.title)」")
            if !ok { handledIds.remove(task.id) }
        }
    }
}

// MARK: - RescheduleTaskSheet (Frameless Mint Paper)

/// Reschedule sheet supporting single OR multi-task selection, `clearTimeIfPast` when moving to Today,
/// Saturday `"本周末"` preset, compact month calendar, plus Drop ("不做了") and Delete ("删除") actions.
struct RescheduleTaskSheet: View {
    let taskIds: [String]
    let onCompleted: (() -> Void)?

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var selectedDate: LocalDate = LocalDate.today()
    @State private var displayedMonthStart: LocalDate = LocalDate.today().withDayOfMonth(1)
    @State private var preserveTime: Bool = true
    @State private var didInit: Bool = false

    init(taskId: String) {
        self.taskIds = [taskId]
        self.onCompleted = nil
    }

    init(taskIds: [String], onCompleted: (() -> Void)? = nil) {
        self.taskIds = taskIds
        self.onCompleted = onCompleted
    }

    private var targetTasks: [GokeTask] {
        let idSet = Set(taskIds)
        return container.liveSnapshot.tasks.filter { $0.deletedAt == nil && idSet.contains($0.id) }
    }

    var body: some View {
        let now = LocalDateTime.now()
        let today = now.date
        let saturday = TodoQuickHelpers.nextOrSameSaturday(from: today)
        let nextMonday = today.startOfWeekMonday.addingWeeks(1)
        let tasks = targetTasks

        NavigationStack {
            Group {
                if tasks.isEmpty {
                    GokeEmptyState(
                        systemImage: "questionmark.circle",
                        title: "未找到该待办"
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(theme.paper)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                            // Task Header Card
                            GokePaperCard {
                                if tasks.count == 1, let single = tasks.first {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(single.title)
                                            .font(GokeTypography.titleMedium.weight(.semibold))
                                            .foregroundStyle(theme.ink)
                                            .lineLimit(2)
                                        if let due = single.due {
                                            Text("当前：\(TodoDateFormatter.dueLabel(due: due, hasTime: single.dueHasTime, now: now))")
                                                .font(GokeTypography.labelMedium)
                                                .monospacedDigit()
                                                .foregroundStyle(theme.muted)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                } else {
                                    Text("已选 \(tasks.count) 项")
                                        .font(GokeTypography.titleMedium.weight(.semibold))
                                        .foregroundStyle(theme.ink)
                                        .monospacedDigit()
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }

                            // Quick Reschedule Presets
                            VStack(spacing: 0) {
                                presetRow(tasks: tasks, title: "今天", subtitle: TodoDateFormatter.shortDate(today), icon: "sun.max", date: today, now: now)
                                rowDivider
                                presetRow(tasks: tasks, title: "明天", subtitle: TodoDateFormatter.shortDate(today.addingDays(1)), icon: "sunrise", date: today.addingDays(1), now: now)
                                rowDivider
                                presetRow(tasks: tasks, title: "本周末", subtitle: TodoDateFormatter.shortDate(saturday), icon: "calendar", date: saturday, now: now)
                                rowDivider
                                presetRow(tasks: tasks, title: "下周一", subtitle: TodoDateFormatter.shortDate(nextMonday), icon: "arrow.right.circle", date: nextMonday, now: now)
                                rowDivider
                                Button {
                                    applyDate(tasks: tasks, targetDate: nil, now: now)
                                } label: {
                                    HStack(spacing: GokeSpacing.md) {
                                        Image(systemName: "calendar.badge.minus")
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundStyle(theme.muted)
                                            .frame(width: 22)
                                        Text("无日期（收集箱）")
                                            .font(GokeTypography.bodyLarge)
                                            .foregroundStyle(theme.muted)
                                        Spacer()
                                    }
                                    .padding(.horizontal, GokeSpacing.md)
                                    .frame(minHeight: 48)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)
                            }
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                    .fill(theme.surface)
                            )

                            // Compact Month Calendar + Apply
                            VStack(spacing: GokeSpacing.sm) {
                                GokeCompactMonthGrid(
                                    displayedMonthStart: $displayedMonthStart,
                                    selectedDate: selectedDate,
                                    today: today
                                ) { picked in
                                    selectedDate = picked
                                    displayedMonthStart = picked.withDayOfMonth(1)
                                }

                                if tasks.contains(where: \.dueHasTime) {
                                    Toggle("保留时间", isOn: $preserveTime)
                                        .font(GokeTypography.bodyMedium)
                                        .foregroundStyle(theme.ink)
                                        .padding(.horizontal, GokeSpacing.md)
                                        .padding(.vertical, 10)
                                        .background(
                                            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                                .fill(theme.surface)
                                        )
                                }

                                Button {
                                    applyDate(tasks: tasks, targetDate: selectedDate, now: now)
                                } label: {
                                    Text("应用 \(TodoDateFormatter.shortDate(selectedDate))")
                                        .font(GokeTypography.labelLarge.weight(.semibold))
                                        .monospacedDigit()
                                        .foregroundStyle(theme.onAccent)
                                        .frame(maxWidth: .infinity, minHeight: 46)
                                        .background(
                                            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                                .fill(theme.accent)
                                        )
                                }
                                .buttonStyle(.mintCompact)
                            }

                            // More Actions
                            VStack(spacing: 0) {
                                if tasks.count == 1, let single = tasks.first {
                                    Button {
                                        let targetId = single.id
                                        let routerRef = router
                                        dismiss()
                                        Task { @MainActor in
                                            routerRef.present(.todoEdit(taskId: targetId))
                                        }
                                    } label: {
                                        HStack(spacing: GokeSpacing.md) {
                                            Image(systemName: "pencil")
                                                .font(.system(size: 16, weight: .medium))
                                                .foregroundStyle(theme.ink)
                                                .frame(width: 22)
                                            Text("编辑详情")
                                                .font(GokeTypography.bodyLarge)
                                                .foregroundStyle(theme.ink)
                                            Spacer()
                                        }
                                        .padding(.horizontal, GokeSpacing.md)
                                        .frame(minHeight: 48)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.mintRow)
                                    rowDivider
                                }

                                Button {
                                    dropTasks(tasks)
                                } label: {
                                    HStack(spacing: GokeSpacing.md) {
                                        Image(systemName: "xmark.circle")
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundStyle(theme.muted)
                                            .frame(width: 22)
                                        Text("不做了")
                                            .font(GokeTypography.bodyLarge)
                                            .foregroundStyle(theme.muted)
                                        Spacer()
                                    }
                                    .padding(.horizontal, GokeSpacing.md)
                                    .frame(minHeight: 48)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)

                                rowDivider

                                Button(role: .destructive) {
                                    deleteTasks(tasks)
                                } label: {
                                    HStack(spacing: GokeSpacing.md) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundStyle(theme.removed)
                                            .frame(width: 22)
                                        Text("删除")
                                            .font(GokeTypography.bodyLarge)
                                            .foregroundStyle(theme.removed)
                                        Spacer()
                                    }
                                    .padding(.horizontal, GokeSpacing.md)
                                    .frame(minHeight: 48)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintRow)
                            }
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                    .fill(theme.surface)
                            )
                        }
                        .padding(.horizontal, GokeSpacing.lg)
                        .padding(.vertical, GokeSpacing.md)
                    }
                    .background(theme.paper)
                }
            }
            .toolbarBackground(theme.paper, for: .navigationBar)
            .navigationTitle(tasks.count > 1 ? "批量改期" : "改期")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .onAppear {
                if !didInit {
                    didInit = true
                    if let due = tasks.first?.due {
                        selectedDate = due.date
                        displayedMonthStart = due.date.withDayOfMonth(1)
                    }
                }
            }
        }
        .gokePaperSheet(theme, detents: [.medium, .large])
    }

    private var rowDivider: some View {
        Rectangle()
            .fill(theme.outlineVariant.opacity(0.45))
            .frame(height: 0.5)
            .padding(.leading, 48)
    }

    private func presetRow(
        tasks: [GokeTask],
        title: String,
        subtitle: String,
        icon: String,
        date: LocalDate,
        now: LocalDateTime
    ) -> some View {
        Button {
            applyDate(tasks: tasks, targetDate: date, now: now)
        } label: {
            HStack(spacing: GokeSpacing.md) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(theme.accent)
                    .frame(width: 22)
                Text(title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                Spacer()
                Text(subtitle)
                    .font(GokeTypography.bodyMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
            }
            .padding(.horizontal, GokeSpacing.md)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintRow)
    }

    private func applyDate(tasks: [GokeTask], targetDate: LocalDate?, now: LocalDateTime) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var anyClearedPastTime = false
        let changes: [Change] = tasks.map { task in
            if let targetDate {
                if preserveTime {
                    let result = TaskLogic.rescheduled(
                        task,
                        to: targetDate,
                        nowMillis: nowMillis,
                        clearTimeIfPast: targetDate == now.date ? now : nil
                    )
                    if result.clearedPastTime { anyClearedPastTime = true }
                    return .modify(before: .task(task), after: .task(result.task))
                } else {
                    var updated = task
                    updated.startDate = nil
                    updated.blockStart = nil
                    updated.dueBeforeNextClass = false
                    updated.due = targetDate.atTime(.endOfDay)
                    updated.dueHasTime = false
                    updated.updatedAt = nowMillis
                    return .modify(before: .task(task), after: .task(updated))
                }
            } else {
                let result = TaskLogic.rescheduled(task, to: nil, nowMillis: nowMillis)
                return .modify(before: .task(task), after: .task(result.task))
            }
        }

        let desc = tasks.count == 1
            ? "调整「\(tasks[0].title)」截止日期"
            : "批量调整 \(tasks.count) 项待办日期"
        let cs = ChangeSet(
            description: desc,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        let baseLabel = targetDate.map { "已改期至 \(TodoDateFormatter.shortDate($0))" } ?? "已清除截止日期"
        let label = anyClearedPastTime ? "\(baseLabel)（已清除过期时间）" : baseLabel
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok {
                onCompleted?()
                dismiss()
            }
        }
    }

    private func dropTasks(_ tasks: [GokeTask]) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let changes: [Change] = tasks.map { task in
            var updated = task
            updated.done = true
            updated.dropped = true
            updated.doneAt = nowMillis
            updated.updatedAt = nowMillis
            return .modify(before: .task(task), after: .task(updated))
        }
        let cs = ChangeSet(
            description: tasks.count == 1 ? "放弃「\(tasks[0].title)」" : "放弃 \(tasks.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: tasks.count == 1 ? "已放弃「\(tasks[0].title)」" : "已放弃 \(tasks.count) 项待办")
            if ok {
                onCompleted?()
                dismiss()
            }
        }
    }

    private func deleteTasks(_ tasks: [GokeTask]) {
        guard !tasks.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        for task in tasks {
            changes.append(.remove(before: .task(task)))
            for sub in TaskLogic.subtasks(container.liveSnapshot.tasks, parentId: task.id) {
                changes.append(.remove(before: .task(sub)))
            }
        }
        let cs = ChangeSet(
            description: tasks.count == 1 ? "删除「\(tasks[0].title)」" : "删除 \(tasks.count) 项待办",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已移入回收站")
            if ok {
                onCompleted?()
                dismiss()
            }
        }
    }
}
