import Foundation
import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Shared Todo Quick Helpers

enum TodoQuickHelpers {
    /// Maps "本周末" to the upcoming (or current) Saturday, matching Android `nextOrSame(SATURDAY)`.
    static func nextOrSameSaturday(from date: LocalDate) -> LocalDate {
        let delta = (6 - date.dayOfWeek + 7) % 7
        return date.addingDays(delta)
    }

    /// Short token name suitable for `/课程名` insertion (strips spaces so parser consumes the full token).
    static func shortCourseToken(for course: Course) -> String {
        let cleaned = course.name
            .replacingOccurrences(of: "\\s+", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? course.name : cleaned
    }

    /// Matches a `/courseName` token against live courses:
    /// prefers `currentSemester` first, excludes exams (`!isExam`), and prefers exact normalized match over substring.
    static func matchCourse(
        name rawName: String,
        courses: [Course],
        currentSemesterId: String?
    ) -> Course? {
        let needle = rawName.replacingOccurrences(of: " ", with: "").lowercased()
        guard !needle.isEmpty else { return nil }
        let liveNonExam = courses.filter { $0.deletedAt == nil && !$0.isExam }
        let scoped: [[Course]]
        if let currentSemesterId {
            let current = liveNonExam.filter { $0.semesterId == currentSemesterId }
            let others = liveNonExam.filter { $0.semesterId != currentSemesterId }
            scoped = [current, others]
        } else {
            scoped = [liveNonExam]
        }
        for group in scoped {
            if let exact = group.first(where: {
                $0.name.replacingOccurrences(of: " ", with: "").lowercased() == needle
            }) {
                return exact
            }
            if let partial = group.first(where: {
                $0.name.replacingOccurrences(of: " ", with: "").lowercased().contains(needle)
            }) {
                return partial
            }
        }
        return nil
    }

    /// Deduplicates non-exam courses, prioritizing the active semester when present.
    static func pickerCourses(
        from courses: [Course],
        currentSemesterId: String?
    ) -> [Course] {
        let nonExam = courses.filter { $0.deletedAt == nil && !$0.isExam }
        let preferred: [Course]
        if let currentSemesterId {
            let inCurrent = nonExam.filter { $0.semesterId == currentSemesterId }
            preferred = inCurrent.isEmpty ? nonExam : inCurrent
        } else {
            preferred = nonExam
        }
        var seen = Set<String>()
        var out: [Course] = []
        for c in preferred.sorted(by: { $0.name < $1.name }) {
            if seen.insert(c.name).inserted {
                out.append(c)
            }
        }
        return out
    }

    /// Rich recurrence summary delegating to `GokeWhenFormatter`.
    static func recurrenceSummary(
        _ rule: Recurrence,
        anchor: LocalDate = .today(),
        semesterEnd: LocalDate? = nil
    ) -> String {
        GokeWhenFormatter.formatRecurrence(
            freq: rule.freq,
            interval: rule.interval,
            byDays: rule.byDays,
            byMonths: rule.byMonths,
            until: rule.until,
            anchor: anchor,
            semesterEnd: semesterEnd
        )
    }
}

// MARK: - QuickAddSheet (Keyboard-Docked Overlay, 1:1 Android QuickAddBar.kt)

private enum QuickAddModal: Identifiable {
    case whenPanel
    case remindSettings
    case expandedEditor(TodoEditDraft)

    var id: String {
        switch self {
        case .whenPanel: return "whenPanel"
        case .remindSettings: return "remindSettings"
        case .expandedEditor(let draft): return "expandedEditor:\(draft.id)"
        }
    }
}

private enum QuickAddInlinePanel: String, Hashable, Sendable {
    case remind
    case priority
    case list
    case tag
    case course
}

/// Compact keyboard-docked Quick Add composer matching Android `QuickAddBar.kt`:
/// - Hosted directly inside Wave 1's `GokeOverlaySheetContainer` (~128pt collapsed height above keyboard,
///   no redundant `"取消"` nav bar, no half-screen void).
/// - Live inline `GokeTokenTextView` (Chinese IME safe, cursor sync, Android exact token colors).
/// - Single-source-of-truth token rewriting via `QuickAddParser.rewriteQuickField` / `toggleTag`
///   so panels can both set AND clear parsed `!prio`/`#list`/`/course`/`date`/`@tag` tokens.
/// - Keeps keyboard UP when toggling inline panels; only resigns focus for the tall `GokeWhenPanel` sheet.
/// - Fixes all 10 dual-state bugs (`hasBodyText`, date precedence, Today `startDate`, `@tag` toggle,
///   time-block `due = blockStart + blockMinutes`, raw-whitespace multi-line handoff to `BatchAddSheet`).
@MainActor
private final class QuickAddDerivedCacheBox {
    private struct ParseKey: Equatable {
        let text: String
        let nowMinute: LocalDateTime
        let config: QuickAddConfig
    }

    private var cachedParseKey: ParseKey?
    private var cachedParsed: QuickAddResult?
    private var cachedTokenSpans: [QuickTokenSpan] = []

    private var cachedSnapshotRevision: UInt64?
    private var cachedSortedLists: [TaskList] = []
    private var cachedCourses: [Course] = []
    private var cachedExistingTags: [String] = []

    func parse(
        _ text: String,
        nowMinute: LocalDateTime,
        config: QuickAddConfig
    ) -> (parsed: QuickAddResult, tokenSpans: [QuickTokenSpan]) {
        let key = ParseKey(text: text, nowMinute: nowMinute, config: config)
        if key == cachedParseKey, let cachedParsed {
            return (cachedParsed, cachedTokenSpans)
        }
        let parsed = QuickAddParser.parse(text, now: nowMinute, config: config)
        let spans = QuickAddParser.tokenRanges(in: text, tokens: parsed.tokens)
        cachedParseKey = key
        cachedParsed = parsed
        cachedTokenSpans = spans
        return (parsed, spans)
    }

    func snapshotIndices(
        revision: UInt64,
        live: AppSnapshot
    ) -> (sortedLists: [TaskList], courses: [Course], existingTags: [String]) {
        if cachedSnapshotRevision == revision {
            return (cachedSortedLists, cachedCourses, cachedExistingTags)
        }
        let sortedLists = live.lists.sorted { $0.sort < $1.sort }
        let courses = TodoQuickHelpers.pickerCourses(
            from: live.courses,
            currentSemesterId: live.currentSemester?.id
        )
        var tagSet = Set<String>()
        for task in live.tasks where task.deletedAt == nil {
            for rawTag in task.tags {
                let trimmed = rawTag.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    tagSet.insert(trimmed)
                }
            }
        }
        let existingTags = Array(tagSet.sorted().prefix(12))
        cachedSnapshotRevision = revision
        cachedSortedLists = sortedLists
        cachedCourses = courses
        cachedExistingTags = existingTags
        return (sortedLists, courses, existingTags)
    }
}

struct QuickAddSheet: View {
    let initialText: String?
    let initialDate: LocalDate?
    let defaultBucket: TaskBucket?
    let defaultListId: String?

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.dismiss) private var dismiss
    @Environment(\.gokeDismissOverlay) private var dismissOverlay
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var derivedCache = QuickAddDerivedCacheBox()
    @State private var inputText: String = ""
    @State private var isInputFocused: Bool = true
    @State private var cursorRequest: GokeCursorRequest?

    // Explicit panel overrides (nil = defer cleanly to parsed text / passive context)
    @State private var panelWhen: WhenPick?
    @State private var panelFreq: Freq?
    @State private var panelInterval: Int = 1
    @State private var panelUntil: LocalDate?
    @State private var panelByDays: Set<Int> = []
    @State private var panelByMonths: Set<Int> = []
    @State private var panelRemindList: [Int] = []
    @State private var panelRemindPersistent: Bool = false
    @State private var explicitClearedDate: Bool = false
    @State private var explicitInbox: Bool = false
    @State private var selectedCourseWeek: Int?

    @State private var activePanel: QuickAddInlinePanel?
    @State private var activeModal: QuickAddModal?
    @State private var showDiscardConfirm: Bool = false
    @State private var shouldDismissAfterExpandedCommit: Bool = false
    @State private var isSubmitting: Bool = false
    @State private var didInit: Bool = false
    @State private var cachedCourseSessions: [CourseOccurrence] = []
    @State private var showEmptyTitleHint: Bool = false

    init(
        initialText: String? = nil,
        initialDate: LocalDate? = nil,
        defaultBucket: TaskBucket? = nil,
        defaultListId: String? = nil
    ) {
        self.initialText = initialText
        self.initialDate = initialDate
        self.defaultBucket = defaultBucket
        self.defaultListId = defaultListId
    }

    private func closeComposer() {
        isInputFocused = false
        if let dismissOverlay {
            dismissOverlay()
        } else {
            dismiss()
        }
    }

    private var hasDraft: Bool {
        !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || panelWhen != nil
            || panelFreq != nil
            || !panelRemindList.isEmpty
    }

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    var body: some View {
        let now = LocalDateTime.now()
        let nowMinute = LocalDateTime(
            date: now.date,
            time: LocalTime(hour: now.time.hour, minute: now.time.minute, second: 0)
        )
        let today = nowMinute.date
        let ctx = router.quickAddContext
        let resolvedDefaultBucket = defaultBucket ?? ctx?.defaultBucket
        let resolvedDefaultListId = defaultListId ?? ctx?.defaultListId
        let resolvedDefaultStartDate = ctx?.defaultStartDate ?? (resolvedDefaultBucket == .TODAY ? today : nil)
        let resolvedDefaultDueDate: LocalDate? = {
            if let d = ctx?.defaultDueDate { return d }
            // If TODAY bucket or explicit defaultStartDate was requested, do NOT treat initialDate as a due deadline!
            if resolvedDefaultStartDate != nil || resolvedDefaultBucket == .TODAY {
                return nil
            }
            if let d = initialDate { return d }
            switch resolvedDefaultBucket {
            case .TOMORROW: return today.addingDays(1)
            case .THIS_WEEK: return today.addingDays(2)
            case .LATER: return today.addingDays(7)
            default: return nil
            }
        }()

        let quickAddConfig = container.settingsStore.settings.quickAddConfig
        // Preserve raw leading whitespace so multi-line handoff keeps indented subtasks nested!
        let rawNonEmptyLines = inputText
            .components(separatedBy: .newlines)
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

        let (parsed, tokenSpans) = derivedCache.parse(inputText, nowMinute: nowMinute, config: quickAddConfig)

        let live = container.liveSnapshot
        let semesterEnd = GokeWhenFormatter.semesterEndDate(live.currentSemester)
        let (sortedLists, courses, existingTags) = derivedCache.snapshotIndices(
            revision: container.snapshotRevision,
            live: live
        )
        let matchedList = parsed.listName.flatMap { name in
            sortedLists.first { $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame }
        }
        let effectiveListId: String? = {
            if let matchedList { return matchedList.id }
            if parsed.listName != nil { return nil } // Pending new list
            if explicitInbox { return nil }
            return resolvedDefaultListId
        }()
        let pendingNewListName: String? = (matchedList == nil) ? parsed.listName : nil
        let activeListDisplayName: String? = {
            if let name = pendingNewListName { return name }
            if let id = effectiveListId {
                return sortedLists.first(where: { $0.id == id })?.name
            }
            return nil
        }()

        let matchedCourse = parsed.courseName.flatMap { name in
            TodoQuickHelpers.matchCourse(
                name: name,
                courses: live.courses,
                currentSemesterId: live.currentSemester?.id
            )
        }
        let effectiveCourseId = matchedCourse?.id

        // Date precedence: panelWhen (explicit WhenPanel) > parsed.due (typed "明天") > passive context (unless cleared or course-linked)
        let parsedWhenPick: WhenPick? = parsed.due.map { d in
            WhenPick(date: d.date, start: parsed.dueHasTime ? d.time : nil, minutes: nil)
        }
        let explicitWhen: WhenPick? = panelWhen ?? parsedWhenPick
        let passiveDueDate: LocalDate? = (explicitClearedDate || explicitWhen != nil || effectiveCourseId != nil)
            ? nil
            : resolvedDefaultDueDate
        let passiveStartDate: LocalDate? = (explicitClearedDate || explicitWhen != nil || passiveDueDate != nil || effectiveCourseId != nil)
            ? nil
            : resolvedDefaultStartDate

        let effectiveRemindList: [Int] = !panelRemindList.isEmpty
            ? Reminders.normalize(panelRemindList)
            : Reminders.normalize(parsed.remindMinutesList)
        let effectivePersistent: Bool = !panelRemindList.isEmpty
            ? (panelRemindPersistent && !effectiveRemindList.isEmpty)
            : (parsed.remindPersistent && !effectiveRemindList.isEmpty)

        let effectiveRepeat: Recurrence? = panelFreq.map { f in
            Recurrence(
                freq: f,
                interval: max(1, panelInterval),
                byDays: (f == .WEEKLY || f == .MONTHLY || f == .YEARLY) ? panelByDays : [],
                until: panelUntil,
                byMonths: f == .YEARLY ? panelByMonths : []
            )
        }

        let canSubmit = parsed.hasBodyText(in: inputText) && !isSubmitting

        let whenToolbarLabel: String? = {
            if let wp = explicitWhen {
                return GokeWhenFormatter.formatWhenPick(wp, today: today)
            }
            if let f = panelFreq {
                return GokeWhenFormatter.formatRecurrence(
                    freq: f,
                    interval: panelInterval,
                    byDays: panelByDays,
                    byMonths: panelByMonths,
                    until: panelUntil,
                    anchor: today,
                    semesterEnd: semesterEnd
                )
            }
            if let d = passiveDueDate {
                return GokeWhenFormatter.formatWhenPick(WhenPick(date: d), today: today)
            }
            if let s = passiveStartDate {
                return s == today ? "今天" : TodoDateFormatter.shortDate(s)
            }
            return nil
        }()

        VStack(alignment: .leading, spacing: 0) {
            // Top Compact Composer Row (~60pt height, zero nav bar, zero dead void)
            HStack(alignment: .top, spacing: GokeSpacing.xs) {
                #if canImport(UIKit)
                GokeTokenTextView(
                    text: $inputText,
                    isFocused: $isInputFocused,
                    cursorRequest: $cursorRequest,
                    placeholder: "准备做什么？",
                    tokenSpans: tokenSpans,
                    priority: parsed.priority,
                    remindPersistent: effectivePersistent,
                    theme: theme,
                    fontSize: 22,
                    fontWeight: .regular,
                    minHeight: 52,
                    maxLines: 4,
                    returnKeyType: rawNonEmptyLines.count > 1 ? .default : .done,
                    onRecomputeSpans: { updated in
                        derivedCache.parse(updated, nowMinute: nowMinute, config: quickAddConfig).1
                    },
                    onSubmit: rawNonEmptyLines.count <= 1 ? {
                        if canSubmit {
                            submitQuickAdd(
                                parsed: parsed,
                                explicitWhen: explicitWhen,
                                passiveDueDate: passiveDueDate,
                                passiveStartDate: passiveStartDate,
                                effectiveListId: effectiveListId,
                                pendingListName: pendingNewListName,
                                effectiveCourseId: effectiveCourseId,
                                effectiveRemindList: effectiveRemindList,
                                effectivePersistent: effectivePersistent,
                                effectiveRepeat: effectiveRepeat,
                                today: today
                            )
                        }
                    } : nil
                )
                .frame(maxWidth: .infinity)
                #else
                TextField("准备做什么？", text: $inputText, axis: .vertical)
                    .font(GokeTypography.titleLarge)
                    .foregroundStyle(theme.ink)
                    .lineLimit(1...4)
                #endif

                // Right-aligned Expand to full TodoEditView (44x44pt hit area, matching Android)
                Button {
                    activePanel = nil
                    isInputFocused = false
                    activeModal = .expandedEditor(
                        makeDraft(
                            parsed: parsed,
                            explicitWhen: explicitWhen,
                            passiveDueDate: passiveDueDate,
                            passiveStartDate: passiveStartDate,
                            effectiveListId: effectiveListId,
                            pendingListName: pendingNewListName,
                            effectiveCourseId: effectiveCourseId,
                            effectiveRemindList: effectiveRemindList,
                            effectivePersistent: effectivePersistent,
                            effectiveRepeat: effectiveRepeat
                        )
                    )
                } label: {
                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("打开详细编辑器")
            }
            .padding(.leading, GokeSpacing.lg)
            .padding(.trailing, GokeSpacing.sm)
            .padding(.top, 12)

            if showEmptyTitleHint && !canSubmit {
                Text("输入待办名称即可添加")
                    .font(GokeTypography.labelSmall)
                    .foregroundStyle(theme.accent)
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.bottom, 4)
                    .transition(.opacity)
            }

            // Multi-line batch paste banner (preserves raw indentation!)
            if rawNonEmptyLines.count > 1 {
                Button {
                    let capturedRawLines = rawNonEmptyLines
                    let delayNs: UInt64 = simplifyMotion ? 20_000_000 : 180_000_000
                    activePanel = nil
                    closeComposer()
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: delayNs)
                        router.present(.batchAdd(lines: capturedRawLines))
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "text.badge.plus")
                            .font(.system(size: 13, weight: .semibold))
                        Text("转为批量添加（\(rawNonEmptyLines.count) 条）")
                            .font(GokeTypography.labelLarge)
                            .monospacedDigit()
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right")
                            .font(.caption2.weight(.semibold))
                    }
                    .foregroundStyle(theme.accent)
                    .padding(.horizontal, GokeSpacing.md)
                    .padding(.vertical, 9)
                    .background(
                        RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                            .fill(theme.accentContainer.opacity(0.6))
                    )
                }
                .buttonStyle(.mintCompact)
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.bottom, 6)
                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .bottom)))
            }

            // Seamless Inline Expanding Panel directly on theme.surface (zero divider, zero band, clipped cleanly!)
            VStack(spacing: 0) {
                if let panel = activePanel {
                    inlinePanelContent(
                        panel: panel,
                        parsed: parsed,
                        quickAddConfig: quickAddConfig,
                        sortedLists: sortedLists,
                        effectiveListId: effectiveListId,
                        existingTags: existingTags,
                        courses: courses,
                        effectiveCourseId: effectiveCourseId,
                        effectiveRemindList: effectiveRemindList,
                        hasScheduleForRemind: explicitWhen != nil || passiveDueDate != nil || effectiveCourseId != nil
                    )
                    .id(panel)
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.top, 4)
                    .padding(.bottom, 8)
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

            // Persistent Bottom Toolbar (1:1 Android icon-first order: Calendar · Remind · Flag · Tag · Inbox · Course · Send)
            let listToolbarTint: Color = {
                if let matchedList {
                    return theme.courseColor(index: matchedList.colorIndex).foreground
                }
                if activeListDisplayName != nil || activePanel == .list {
                    return theme.accent
                }
                return theme.muted
            }()

            let toolbarRow = HStack(spacing: 2) {
                // 1. Calendar / WhenPanel (no duplicate bell icon; crisp full accent tint)
                toolbarButton(
                    icon: "calendar",
                    label: whenToolbarLabel,
                    maxLabelWidth: 96,
                    tint: (explicitWhen != nil || effectiveRepeat != nil || whenToolbarLabel != nil)
                        ? theme.accent
                        : theme.muted,
                    isActive: explicitWhen != nil || effectiveRepeat != nil,
                    extraIcon: (effectiveRepeat != nil && explicitWhen != nil) ? "repeat" : nil,
                    accessibilityLabel: whenToolbarLabel.map { "日期时间：\($0)" } ?? "设置日期与时间"
                ) {
                    activePanel = nil
                    isInputFocused = false
                    activeModal = .whenPanel
                }

                // 2. Remind
                toolbarButton(
                    icon: effectivePersistent ? "bell.badge.fill" : (effectiveRemindList.isEmpty ? "bell" : "bell.fill"),
                    label: effectiveRemindList.isEmpty ? nil : remindShortLabel(effectiveRemindList, persistent: effectivePersistent),
                    maxLabelWidth: 84,
                    tint: effectivePersistent
                        ? theme.error
                        : ((!effectiveRemindList.isEmpty || activePanel == .remind) ? theme.accent : theme.muted),
                    isActive: activePanel == .remind || !effectiveRemindList.isEmpty,
                    accessibilityLabel: "提醒"
                ) {
                    toggleInlinePanel(.remind)
                }

                // 3. Priority (icon-only; outline flag when unset, semantic filled flag when set)
                toolbarButton(
                    icon: parsed.priority > 0 ? "flag.fill" : "flag",
                    label: nil,
                    maxLabelWidth: 0,
                    tint: theme.priorityColor(parsed.priority) ?? (activePanel == .priority ? theme.accent : theme.muted),
                    isActive: activePanel == .priority || parsed.priority > 0,
                    accessibilityLabel: "优先级"
                ) {
                    toggleInlinePanel(.priority)
                }

                // 4. Tag (always opens inline panel, even when existingTags is empty)
                toolbarButton(
                    icon: "tag",
                    label: nil,
                    maxLabelWidth: 0,
                    tint: !parsed.tags.isEmpty
                        ? theme.secondary
                        : (activePanel == .tag ? theme.accent : theme.muted),
                    isActive: activePanel == .tag || !parsed.tags.isEmpty,
                    accessibilityLabel: "标签"
                ) {
                    toggleInlinePanel(.tag)
                }

                // 5. List / Inbox (always opens inline panel, even when sortedLists is empty)
                toolbarButton(
                    icon: "tray",
                    label: activeListDisplayName ?? "收集箱",
                    maxLabelWidth: 76,
                    tint: listToolbarTint,
                    isActive: activePanel == .list || activeListDisplayName != nil,
                    accessibilityLabel: "清单：\(activeListDisplayName ?? "收集箱")"
                ) {
                    toggleInlinePanel(.list)
                }

                // 6. Course (icon-only, hidden if no courses exist)
                if !courses.isEmpty {
                    toolbarButton(
                        icon: "graduationcap",
                        label: nil,
                        maxLabelWidth: 0,
                        tint: (effectiveCourseId != nil || activePanel == .course) ? theme.accent : theme.muted,
                        isActive: activePanel == .course || effectiveCourseId != nil,
                        accessibilityLabel: "关联课程"
                    ) {
                        toggleInlinePanel(.course)
                    }
                }
            }
            .padding(.leading, GokeSpacing.md)
            .padding(.trailing, GokeSpacing.xs)

            HStack(spacing: 0) {
                ViewThatFits(in: .horizontal) {
                    toolbarRow
                        .frame(maxWidth: .infinity, alignment: .leading)
                    ScrollView(.horizontal, showsIndicators: false) {
                        toolbarRow
                    }
                }

                // Fixed Right Send Button (36x36 soft mint/accent orb inside 44x44 hit area)
                Button {
                    if canSubmit {
                        showEmptyTitleHint = false
                        submitQuickAdd(
                            parsed: parsed,
                            explicitWhen: explicitWhen,
                            passiveDueDate: passiveDueDate,
                            passiveStartDate: passiveStartDate,
                            effectiveListId: effectiveListId,
                            pendingListName: pendingNewListName,
                            effectiveCourseId: effectiveCourseId,
                            effectiveRemindList: effectiveRemindList,
                            effectivePersistent: effectivePersistent,
                            effectiveRepeat: effectiveRepeat,
                            today: today
                        )
                    } else if hasDraft {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            activePanel = nil
                            showEmptyTitleHint = true
                        }
                        isInputFocused = true
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(canSubmit ? theme.accent : theme.surfaceHighest)
                            .frame(width: 36, height: 36)
                        Image(systemName: "arrow.up")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(canSubmit ? theme.onAccent : theme.muted.opacity(hasDraft ? 0.72 : 0.42))
                    }
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(!canSubmit && !hasDraft)
                .padding(.trailing, GokeSpacing.sm)
                .accessibilityLabel(canSubmit ? "添加待办" : "请先输入待办名称")
            }
            .frame(minHeight: 46)
            .padding(.bottom, 8)
        }
        .background(theme.surface)
        .gokeOverlayInterceptDismiss {
            if hasDraft {
                showDiscardConfirm = true
                return false
            }
            return true
        }
        .onAppear {
            guard !didInit else { return }
            didInit = true
            if let initialText, !initialText.isEmpty {
                inputText = initialText
                cursorRequest = GokeCursorRequest(utf16Offset: initialText.utf16.count)
            }
            isInputFocused = true
        }
        .onChange(of: canSubmit) { _, ready in
            if ready && showEmptyTitleHint {
                withAnimation(GokeMotion.dismiss(policy: performance)) {
                    showEmptyTitleHint = false
                }
            }
        }
        .onChange(of: effectiveCourseId, initial: true) { _, newCourseId in
            if let cid = newCourseId {
                cachedCourseSessions = courseSessionOptions(courseId: cid, live: container.liveSnapshot)
                if let w = selectedCourseWeek, !cachedCourseSessions.contains(where: { $0.originalWeek == w }) {
                    selectedCourseWeek = nil
                }
            } else {
                cachedCourseSessions = []
                selectedCourseWeek = nil
            }
        }
        .sheet(item: $activeModal, onDismiss: {
            if shouldDismissAfterExpandedCommit {
                shouldDismissAfterExpandedCommit = false
                closeComposer()
            } else {
                isInputFocused = true
            }
        }) { modal in
            switch modal {
            case .whenPanel:
                let initialPick: WhenPick? = explicitWhen
                    ?? passiveDueDate.map { WhenPick(date: $0) }
                    ?? passiveStartDate.map { WhenPick(date: $0) }
                GokeWhenPanel(
                    initialPick: initialPick,
                    initialRemindList: effectiveRemindList,
                    initialRemindPersistent: effectivePersistent,
                    initialFreq: panelFreq,
                    initialInterval: panelInterval,
                    initialUntil: panelUntil,
                    initialByDays: panelByDays,
                    initialByMonths: panelByMonths,
                    semesterEnd: semesterEnd
                ) { result in
                    applyWhenPanelResult(result, parsedTokens: parsed.tokens)
                }
                .gokePaperSheet(theme, detents: [.large])

            case .remindSettings:
                QuickAddRemindSettingsSheet()
                    .gokePaperSheet(theme, detents: [.medium, .large])

            case .expandedEditor(let draft):
                TodoEditView(
                    draft: draft,
                    isSheet: true,
                    onCommitted: {
                        shouldDismissAfterExpandedCommit = true
                    }
                )
            }
        }
        .confirmationDialog("放弃这条未保存的草稿？", isPresented: $showDiscardConfirm, titleVisibility: .visible) {
            Button("放弃草稿", role: .destructive) {
                closeComposer()
            }
            Button("继续编辑", role: .cancel) {
                isInputFocused = true
            }
        }
    }

    // MARK: - Toolbar Button (Zero Muddy Capsules — Crisp Icon-First Android Parity)

    private func toolbarButton(
        icon: String,
        label: String?,
        maxLabelWidth: CGFloat,
        tint: Color,
        isActive: Bool,
        extraIcon: String? = nil,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 19, weight: isActive ? .semibold : .medium))
                if let label, !label.isEmpty {
                    Text(label)
                        .font(GokeTypography.labelLarge.weight(isActive ? .semibold : .medium))
                        .monospacedDigit()
                        .lineLimit(1)
                        .frame(maxWidth: maxLabelWidth, alignment: .leading)
                }
                if let extraIcon {
                    Image(systemName: extraIcon)
                        .font(.system(size: 13, weight: .semibold))
                }
            }
            .foregroundStyle(tint)
            .padding(.horizontal, label != nil ? 8 : 0)
            .frame(minWidth: 40, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
        .accessibilityLabel(accessibilityLabel)
    }

    // MARK: - Inline Panels (Keyboard Stays UP!)

    private func toggleInlinePanel(_ panel: QuickAddInlinePanel) {
        let isClosing = activePanel == panel
        let anim = isClosing
            ? GokeMotion.dismiss(policy: performance)
            : GokeMotion.snappy(policy: performance)
        withAnimation(anim) {
            activePanel = isClosing ? nil : panel
        }
    }

    @ViewBuilder
    private func inlinePanelContent(
        panel: QuickAddInlinePanel,
        parsed: QuickAddResult,
        quickAddConfig: QuickAddConfig,
        sortedLists: [TaskList],
        effectiveListId: String?,
        existingTags: [String],
        courses: [Course],
        effectiveCourseId: String?,
        effectiveRemindList: [Int],
        hasScheduleForRemind: Bool
    ) -> some View {
        switch panel {
        case .remind:
            let kw = quickAddConfig.normalizedKeyword
            let hasTextRemind = !parsed.remindMinutesList.isEmpty
            let normalOn = panelRemindList.isEmpty && hasTextRemind && !parsed.remindPersistent
            let bangOn = panelRemindList.isEmpty && hasTextRemind && parsed.remindPersistent
            VStack(alignment: .leading, spacing: 8) {
                TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    if !kw.isEmpty {
                        panelChip(
                            title: kw,
                            selected: normalOn
                        ) {
                            panelRemindList = []
                            panelRemindPersistent = false
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [.remind],
                                    add: normalOn ? nil : kw
                                )
                            )
                            closeInlinePanel()
                        }

                        panelChip(
                            title: "\(kw)!",
                            tint: theme.error,
                            isDanger: true,
                            selected: bangOn
                        ) {
                            panelRemindList = []
                            panelRemindPersistent = false
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [.remind],
                                    add: bangOn ? nil : "\(kw)!"
                                )
                            )
                            closeInlinePanel()
                        }
                    }

                    panelChip(title: "自定义", selected: !panelRemindList.isEmpty) {
                        activePanel = nil
                        isInputFocused = false
                        activeModal = .whenPanel
                    }

                    panelChip(title: "设置", selected: false) {
                        activePanel = nil
                        isInputFocused = false
                        activeModal = .remindSettings
                    }
                }

                if !effectiveRemindList.isEmpty && !hasScheduleForRemind {
                    Text("设时间后生效")
                        .font(GokeTypography.labelSmall)
                        .foregroundStyle(theme.muted)
                }
            }

        case .priority:
            // Android 1:1 SegmentedPill: 高 · 中 · 低 · 无
            GokeSegmentedPill(
                options: [
                    .init(3, "高", activeTint: theme.priorityHigh),
                    .init(2, "中", activeTint: theme.priorityMedium),
                    .init(1, "低", activeTint: theme.priorityLow),
                    .init(0, "无", activeTint: theme.ink),
                ],
                selection: Binding(
                    get: { parsed.priority },
                    set: { level in
                        let token: String? = switch level {
                        case 3: "!高"
                        case 2: "!中"
                        case 1: "!低"
                        default: nil
                        }
                        applyRewrite(
                            QuickAddParser.rewriteQuickField(
                                current: inputText,
                                tokens: parsed.tokens,
                                kinds: [.priority],
                                add: token
                            )
                        )
                        Task { @MainActor in
                            closeInlinePanel()
                        }
                    }
                ),
                height: 44
            )

        case .list:
            VStack(alignment: .leading, spacing: 6) {
                GokeShrinkWrappingScrollView(maxHeight: 132) {
                    TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                        panelChip(
                            title: "收集箱",
                            selected: effectiveListId == nil && parsed.listName == nil
                        ) {
                            explicitInbox = true
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [.list],
                                    add: nil
                                )
                            )
                            closeInlinePanel()
                        }

                        ForEach(sortedLists) { list in
                            let selected = effectiveListId == list.id
                            let dot = theme.courseColor(index: list.colorIndex).foreground
                            panelChip(
                                title: "#\(list.name)",
                                dotColor: dot,
                                selected: selected
                            ) {
                                explicitInbox = false
                                applyRewrite(
                                    QuickAddParser.rewriteQuickField(
                                        current: inputText,
                                        tokens: parsed.tokens,
                                        kinds: [.list],
                                        add: "#\(list.name)"
                                    )
                                )
                                closeInlinePanel()
                            }
                        }

                        panelChip(title: "+ 新清单", tint: theme.accent, selected: false) {
                            explicitInbox = false
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [.list],
                                    add: "#"
                                )
                            )
                            closeInlinePanel()
                            isInputFocused = true
                        }
                    }
                }
                if sortedLists.isEmpty {
                    Text("输入 #清单名 即可新建")
                        .font(GokeTypography.labelSmall)
                        .foregroundStyle(theme.muted)
                }
            }

        case .tag:
            VStack(alignment: .leading, spacing: 6) {
                GokeShrinkWrappingScrollView(maxHeight: 132) {
                    TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                        ForEach(existingTags, id: \.self) { tag in
                            let isSelected = parsed.tags.contains {
                                $0.localizedCaseInsensitiveCompare(tag) == .orderedSame
                            }
                            panelChip(
                                title: "@\(tag)",
                                selected: isSelected
                            ) {
                                applyRewrite(
                                    QuickAddParser.toggleTag(
                                        current: inputText,
                                        tokens: parsed.tokens,
                                        tag: tag
                                    )
                                )
                                closeInlinePanel()
                            }
                        }

                        panelChip(title: "+ 新标签", tint: theme.accent, selected: false) {
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [],
                                    add: "@"
                                )
                            )
                            closeInlinePanel()
                            isInputFocused = true
                        }
                    }
                }
                if existingTags.isEmpty {
                    Text("输入 @标签名 即可新建")
                        .font(GokeTypography.labelSmall)
                        .foregroundStyle(theme.muted)
                }
            }

        case .course:
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text("关联课程 · 下节课前截止")
                        .font(GokeTypography.labelMedium)
                        .foregroundStyle(theme.muted)
                    Spacer()
                    if effectiveCourseId != nil {
                        Button {
                            selectedCourseWeek = nil
                            applyRewrite(
                                QuickAddParser.rewriteQuickField(
                                    current: inputText,
                                    tokens: parsed.tokens,
                                    kinds: [.course],
                                    add: nil
                                )
                            )
                            closeInlinePanel()
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
                            closeInlinePanel()
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

                GokeShrinkWrappingScrollView(maxHeight: 164) {
                    VStack(spacing: 2) {
                        ForEach(courses) { course in
                            let selected = effectiveCourseId == course.id
                            let color = theme.courseColor(index: course.colorIndex)
                            Button {
                                if selected {
                                    selectedCourseWeek = nil
                                    applyRewrite(
                                        QuickAddParser.rewriteQuickField(
                                            current: inputText,
                                            tokens: parsed.tokens,
                                            kinds: [.course],
                                            add: nil
                                        )
                                    )
                                    closeInlinePanel()
                                } else {
                                    selectedCourseWeek = nil
                                    let sessions = courseSessionOptions(courseId: course.id, live: container.liveSnapshot)
                                    cachedCourseSessions = sessions
                                    applyRewrite(
                                        QuickAddParser.rewriteQuickField(
                                            current: inputText,
                                            tokens: parsed.tokens,
                                            kinds: [.course],
                                            add: "/\(TodoQuickHelpers.shortCourseToken(for: course))"
                                        )
                                    )
                                    // Keep panel open when sessions exist so user can immediately pick a specific week!
                                    if sessions.isEmpty {
                                        closeInlinePanel()
                                    }
                                }
                            } label: {
                                HStack(spacing: 10) {
                                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                                        .fill(color.barColor)
                                        .frame(width: 4, height: 18)
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
                                .frame(minHeight: 40)
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

                // Optional specific session picker when linked to a course
                if effectiveCourseId != nil, !cachedCourseSessions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            panelChip(title: "自动下节课", selected: selectedCourseWeek == nil) {
                                selectedCourseWeek = nil
                                closeInlinePanel()
                            }
                            ForEach(cachedCourseSessions.prefix(12), id: \.id) { occ in
                                let label = "第\(occ.week)周 \(occ.date.month)/\(occ.date.day)"
                                panelChip(title: label, selected: selectedCourseWeek == occ.originalWeek) {
                                    selectedCourseWeek = occ.originalWeek
                                    closeInlinePanel()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func panelChip(
        title: String,
        dotColor: Color? = nil,
        tint: Color? = nil,
        isDanger: Bool = false,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        let fg: Color = {
            if selected {
                return isDanger ? theme.error : theme.onAccentContainer
            }
            return tint ?? theme.ink
        }()
        let bg: Color = {
            if selected {
                return isDanger ? theme.errorContainer : theme.accentContainer
            }
            return theme.surfaceHighest
        }()
        return Button(action: action) {
            HStack(spacing: 6) {
                if let dotColor {
                    Circle()
                        .fill(selected ? theme.onAccentContainer : dotColor)
                        .frame(width: 7, height: 7)
                }
                Text(title)
                    .font(GokeTypography.labelLarge.weight(selected ? .semibold : .regular))
                    .monospacedDigit()
                    .lineLimit(1)
            }
            .foregroundStyle(fg)
            .padding(.horizontal, 14)
            .frame(minHeight: 40)
            .background(Capsule().fill(bg))
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - Rewriting & WhenPanel Sync

    private func applyRewrite(_ rewrite: QuickFieldRewrite) {
        inputText = rewrite.text
        cursorRequest = GokeCursorRequest(utf16Offset: rewrite.cursorUTF16)
    }

    private func closeInlinePanel() {
        withAnimation(GokeMotion.dismiss(policy: performance)) {
            activePanel = nil
        }
    }

    private func applyWhenPanelResult(_ result: WhenPanelResult, parsedTokens: [QuickToken]) {
        if result.whenPick == nil && result.remindMinutesList.isEmpty && result.freq == nil {
            // Clearing WhenPanel strips parsed DATE / TIME / REMIND tokens from text too!
            panelWhen = nil
            panelFreq = nil
            panelInterval = 1
            panelUntil = nil
            panelByDays = []
            panelByMonths = []
            panelRemindList = []
            panelRemindPersistent = false
            explicitClearedDate = true
            let rewrite = QuickAddParser.rewriteQuickField(
                current: inputText,
                tokens: parsedTokens,
                kinds: [.date, .time, .remind],
                add: nil
            )
            applyRewrite(rewrite)
            return
        }

        explicitClearedDate = false
        panelWhen = result.whenPick
        panelFreq = result.freq
        panelInterval = result.interval
        panelUntil = result.until
        panelByDays = result.byDays
        panelByMonths = result.byMonths
        panelRemindList = result.remindMinutesList
        panelRemindPersistent = result.remindPersistent

        // Strip conflicting DATE/TIME (and REMIND if overridden) from text so single source of truth holds
        var kindsToStrip: Set<QuickTokenKind> = [.date, .time]
        if !result.remindMinutesList.isEmpty {
            kindsToStrip.insert(.remind)
        }
        let hasConflicting = parsedTokens.contains { kindsToStrip.contains($0.kind) }
        if hasConflicting {
            let rewrite = QuickAddParser.rewriteQuickField(
                current: inputText,
                tokens: parsedTokens,
                kinds: kindsToStrip,
                add: nil
            )
            applyRewrite(rewrite)
        }
    }

    // MARK: - Draft Transfer & Commit

    private func makeDraft(
        parsed: QuickAddResult,
        explicitWhen: WhenPick?,
        passiveDueDate: LocalDate?,
        passiveStartDate: LocalDate?,
        effectiveListId: String?,
        pendingListName: String?,
        effectiveCourseId: String?,
        effectiveRemindList: [Int],
        effectivePersistent: Bool,
        effectiveRepeat: Recurrence?
    ) -> TodoEditDraft {
        let resolvedDue: LocalDateTime?
        let resolvedDueHasTime: Bool
        let resolvedStartDate: LocalDate?
        let resolvedBlockStart: LocalDateTime?
        let resolvedBlockMinutes: Int?

        if let wp = explicitWhen {
            resolvedDue = wp.resolvedDue
            resolvedDueHasTime = wp.resolvedDueHasTime
            resolvedStartDate = nil
            resolvedBlockStart = wp.resolvedBlockStart
            resolvedBlockMinutes = wp.resolvedBlockMinutes
        } else if let d = passiveDueDate {
            resolvedDue = d.atTime(.endOfDay)
            resolvedDueHasTime = false
            resolvedStartDate = nil
            resolvedBlockStart = nil
            resolvedBlockMinutes = nil
        } else if let s = passiveStartDate {
            resolvedDue = nil
            resolvedDueHasTime = false
            resolvedStartDate = s
            resolvedBlockStart = nil
            resolvedBlockMinutes = nil
        } else {
            resolvedDue = nil
            resolvedDueHasTime = false
            resolvedStartDate = nil
            resolvedBlockStart = nil
            resolvedBlockMinutes = nil
        }

        let dueBeforeClass = effectiveCourseId != nil && resolvedDue == nil && resolvedStartDate == nil
        return TodoEditDraft(
            title: parsed.hasBodyText(in: inputText) ? parsed.title : inputText.trimmingCharacters(in: .whitespacesAndNewlines),
            due: resolvedDue,
            dueHasTime: resolvedDueHasTime,
            startDate: resolvedStartDate,
            priority: parsed.priority,
            listId: effectiveListId,
            pendingListName: pendingListName,
            courseId: effectiveCourseId,
            courseWeek: selectedCourseWeek,
            dueBeforeNextClass: dueBeforeClass,
            tags: parsed.tags,
            remindMinutesList: effectiveRemindList,
            remindPersistent: effectivePersistent,
            repeatRule: effectiveRepeat,
            blockStart: resolvedBlockStart,
            blockMinutes: resolvedBlockMinutes
        )
    }

    private func submitQuickAdd(
        parsed: QuickAddResult,
        explicitWhen: WhenPick?,
        passiveDueDate: LocalDate?,
        passiveStartDate: LocalDate?,
        effectiveListId: String?,
        pendingListName: String?,
        effectiveCourseId: String?,
        effectiveRemindList: [Int],
        effectivePersistent: Bool,
        effectiveRepeat: Recurrence?,
        today: LocalDate
    ) {
        guard !isSubmitting, parsed.hasBodyText(in: inputText) else { return }
        let trimmed = parsed.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSubmitting = true

        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        var resolvedListId = effectiveListId

        // Auto-create unknown #listName atomically
        if resolvedListId == nil,
           let rawName = pendingListName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !rawName.isEmpty {
            let newList = TaskList(
                name: rawName,
                colorIndex: (container.liveSnapshot.lists.count + 1) % max(theme.courseColors.count, 1),
                sort: container.liveSnapshot.lists.count,
                updatedAt: nowMillis
            )
            changes.append(.add(after: .taskList(newList)))
            resolvedListId = newList.id
        }

        let due: LocalDateTime?
        let dueHasTime: Bool
        let startDate: LocalDate?
        let blockStart: LocalDateTime?
        let blockMinutes: Int?

        if let wp = explicitWhen {
            // Time-block sets due = blockStart + blockMinutes!
            due = wp.resolvedDue
            dueHasTime = wp.resolvedDueHasTime
            startDate = nil
            blockStart = wp.resolvedBlockStart
            blockMinutes = wp.resolvedBlockMinutes
        } else if let d = passiveDueDate {
            due = d.atTime(.endOfDay)
            dueHasTime = false
            startDate = nil
            blockStart = nil
            blockMinutes = nil
        } else if let s = passiveStartDate {
            // Today context sets startDate = today (NOT due = today 23:59!)
            due = nil
            dueHasTime = false
            startDate = s
            blockStart = nil
            blockMinutes = nil
        } else {
            due = nil
            dueHasTime = false
            startDate = nil
            blockStart = nil
            blockMinutes = nil
        }

        let normalizedReminders = Reminders.normalize(effectiveRemindList)
        let dueBeforeClass = effectiveCourseId != nil && due == nil && startDate == nil
        let maxSort = container.liveSnapshot.tasks.filter { $0.deletedAt == nil }.map(\.sortOrder).max() ?? 0
        let baseTask = GokeTask(
            title: trimmed,
            listId: resolvedListId,
            tags: parsed.tags,
            priority: parsed.priority,
            startDate: startDate,
            due: due,
            dueHasTime: dueHasTime,
            repeat: effectiveRepeat,
            remindMinutes: normalizedReminders.first,
            remindPersistent: effectivePersistent && !normalizedReminders.isEmpty,
            courseId: effectiveCourseId,
            dueBeforeNextClass: dueBeforeClass,
            blockStart: blockStart,
            blockMinutes: blockMinutes,
            sortOrder: maxSort + 1,
            source: .MANUAL,
            updatedAt: nowMillis,
            remindMinutesList: normalizedReminders
        )
        let task = baseTask.withCourseLink(
            courseId: effectiveCourseId,
            courseWeek: selectedCourseWeek,
            dueBeforeNextClass: dueBeforeClass
        )
        changes.append(.add(after: .task(task)))

        let cs = ChangeSet(
            description: "快速添加「\(trimmed)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )

        let toastSuffix: String = {
            if let wp = explicitWhen {
                return " · \(GokeWhenFormatter.formatWhenPick(wp, today: today))"
            }
            if let d = passiveDueDate {
                return " · \(GokeWhenFormatter.formatWhenPick(WhenPick(date: d), today: today))"
            }
            if let s = passiveStartDate {
                return s == today ? " · 今天" : " · \(TodoDateFormatter.shortDate(s))"
            }
            return ""
        }()

        Task {
            let ok = await actions.commit(cs, label: "已添加「\(trimmed)」\(toastSuffix)")
            isSubmitting = false
            if ok {
                closeComposer()
            }
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

    private func remindShortLabel(_ list: [Int], persistent: Bool) -> String {
        if list.count == 1, let only = list.first {
            let label = only == 0 ? "准时" : "\(only)分前"
            return persistent ? "\(label)!" : label
        }
        return persistent ? "\(list.count)项!" : "\(list.count)项"
    }
}

// MARK: - QuickAddRemindSettingsSheet (Frameless Mint Paper)

struct QuickAddRemindSettingsSheet: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var keyword: String = QuickAddConfig.defaultRemindKeyword
    @State private var remindMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes
    @State private var persistentMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes
    @State private var bangPersistent: Bool = true
    @State private var didLoad: Bool = false

    init() {}

    var body: some View {
        let kwDisplay = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        let kwLabel = kwDisplay.isEmpty ? "提醒" : kwDisplay

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("关键词")
                            .font(GokeTypography.labelMedium.weight(.semibold))
                            .foregroundStyle(theme.muted)
                            .padding(.horizontal, 4)

                        TextField("留空关闭", text: $keyword)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                    .fill(theme.surfaceHighest)
                            )

                        if !kwDisplay.isEmpty {
                            Text("例：\(kwDisplay) / \(kwDisplay)! / 提前15分\(kwDisplay)")
                                .font(GokeTypography.labelMedium)
                                .foregroundStyle(theme.muted)
                                .padding(.horizontal, 4)
                        }
                    }

                    GokePaperCard {
                        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                            Text("「\(kwLabel)」")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.ink)
                            GokeMultiReminderPicker("提前提醒", selection: $remindMinutesList)
                        }
                    }

                    GokePaperCard {
                        VStack(alignment: .leading, spacing: GokeSpacing.md) {
                            Text("「\(kwLabel)!」强提醒")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.ink)
                            Toggle("持续提醒", isOn: $bangPersistent)
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)
                            GokeMultiReminderPicker("提前提醒", selection: $persistentMinutesList)
                        }
                    }

                    Button {
                        let def = QuickAddConfig.default
                        keyword = def.remindKeyword
                        remindMinutesList = def.remindMinutesList
                        persistentMinutesList = def.persistentRemindMinutesList
                        bangPersistent = def.bangPersistent
                    } label: {
                        Text("恢复默认")
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.removed)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                    .fill(theme.surface)
                            )
                    }
                    .buttonStyle(.mintCompact)
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.vertical, GokeSpacing.md)
            }
            .background(theme.paper.ignoresSafeArea())
            .toolbarBackground(theme.paper, for: .navigationBar)
            .navigationTitle("提醒简写")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let cfg = QuickAddConfig(
                            remindKeyword: keyword,
                            remindMinutesList: remindMinutesList.isEmpty ? QuickAddConfig.defaultRemindMinutes : remindMinutesList,
                            persistentRemindMinutesList: persistentMinutesList.isEmpty ? QuickAddConfig.defaultRemindMinutes : persistentMinutesList,
                            bangPersistent: bangPersistent
                        )
                        container.settingsStore.update { $0.applyQuickAddConfig(cfg) }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .onAppear {
                guard !didLoad else { return }
                didLoad = true
                let cfg = container.settingsStore.settings.quickAddConfig
                keyword = cfg.remindKeyword
                remindMinutesList = cfg.effectiveRemindMinutes
                persistentMinutesList = cfg.effectivePersistentRemindMinutes
                bangPersistent = cfg.bangPersistent
            }
        }
    }
}

// MARK: - BatchAddSheet (Frameless Mint Paper + Rich Preview + Preserved Indentation)

/// Multi-line paste batch importer using `QuickAddParser.parseLines` (supports indented / `- ` subtasks),
/// preserving leading whitespace, showing full metadata preview, and continuing `sortOrder`.
struct BatchAddSheet: View {
    let initialLines: [String]

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.dismiss) private var dismiss

    @State private var rawText: String = ""
    @State private var selectedListId: String?
    @State private var useSharedDueDate: Bool = false
    @State private var sharedDueDateValue: Date = Date()
    @State private var didInit: Bool = false

    init(lines: [String] = []) {
        self.initialLines = lines
    }

    var body: some View {
        let now = LocalDateTime.now()
        let live = container.liveSnapshot
        let quickAddConfig = container.settingsStore.settings.quickAddConfig
        let parsedItems = QuickAddParser.parseLines(rawText, now: now, config: quickAddConfig)
        let sharedDueDate: LocalDate? = useSharedDueDate ? LocalDateTime.fromDate(sharedDueDateValue).date : nil
        let sortedLists = live.lists.sorted { $0.sort < $1.sort }

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    // Multiline Input Card
                    VStack(alignment: .leading, spacing: 6) {
                        Text("每行一条，缩进为子任务")
                            .font(GokeTypography.labelMedium)
                            .foregroundStyle(theme.muted)
                            .padding(.horizontal, 4)

                        TextEditor(text: $rawText)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .scrollContentBackground(.hidden)
                            .frame(minHeight: 132, maxHeight: 200)
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                                    .fill(theme.surface)
                            )
                    }

                    // Batch Defaults Card
                    GokePaperCard {
                        VStack(alignment: .leading, spacing: GokeSpacing.md) {
                            Text("默认归属")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.ink)

                            if !sortedLists.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 6) {
                                        batchChip("收集箱", selected: selectedListId == nil) {
                                            selectedListId = nil
                                        }
                                        ForEach(sortedLists) { list in
                                            batchChip(list.name, selected: selectedListId == list.id) {
                                                selectedListId = list.id
                                            }
                                        }
                                    }
                                }
                            }

                            Toggle("统一截止日期", isOn: $useSharedDueDate)
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)

                            if useSharedDueDate {
                                DatePicker("日期", selection: $sharedDueDateValue, displayedComponents: .date)
                                    .font(GokeTypography.bodyMedium)
                            }
                        }
                    }

                    // Live Parsed Preview
                    VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                        HStack {
                            Text("预览")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Text("\(parsedItems.count) 项")
                                .font(GokeTypography.labelMedium.weight(.semibold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .animation(GokeMotion.snappy(policy: performance), value: parsedItems.count)
                                .foregroundStyle(theme.accent)
                        }
                        .padding(.horizontal, 4)

                        if parsedItems.isEmpty {
                            GokeEmptyState(
                                systemImage: "text.badge.plus",
                                title: "输入或粘贴多行待办",
                                subtitle: "支持日期、!高、#清单、@标签"
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, GokeSpacing.md)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(Array(parsedItems.enumerated()), id: \.offset) { idx, batchItem in
                                    if idx > 0 {
                                        Rectangle()
                                            .fill(theme.outlineVariant.opacity(0.4))
                                            .frame(height: 0.5)
                                            .padding(.leading, batchItem.parentIndex != nil ? 38 : 14)
                                    }
                                    batchPreviewRow(batchItem: batchItem, live: live, sharedDueDate: sharedDueDate, now: now)
                                }
                            }
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                                    .fill(theme.surface)
                            )
                        }
                    }
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.vertical, GokeSpacing.md)
            }
            .background(theme.paper.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .toolbarBackground(theme.paper, for: .navigationBar)
            .navigationTitle("批量添加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("添加 \(parsedItems.count) 条") {
                        commitBatch(parsedItems, sharedDueDate: sharedDueDate)
                    }
                    .fontWeight(.semibold)
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .disabled(parsedItems.isEmpty)
                }
            }
            .onAppear {
                if !didInit {
                    didInit = true
                    if !initialLines.isEmpty {
                        rawText = initialLines.joined(separator: "\n")
                    }
                }
            }
        }
        .gokePaperSheet(theme, detents: [.medium, .large])
    }

    private func batchPreviewRow(
        batchItem: QuickBatchItem,
        live: AppSnapshot,
        sharedDueDate: LocalDate?,
        now: LocalDateTime
    ) -> some View {
        let item = batchItem.result
        let matchedCourse = item.courseName.flatMap {
            TodoQuickHelpers.matchCourse(name: $0, courses: live.courses, currentSemesterId: live.currentSemester?.id)
        }
        return HStack(alignment: .top, spacing: GokeSpacing.sm) {
            if batchItem.parentIndex != nil {
                Image(systemName: "arrow.turn.down.right")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(theme.muted)
                    .padding(.leading, 12)
                    .padding(.top, 4)
            }

            Circle()
                .fill(theme.priorityColor(item.priority) ?? theme.accent)
                .frame(width: 8, height: 8)
                .padding(.top, 6)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                    .lineLimit(2)

                HStack(spacing: 6) {
                    if let d = item.due {
                        Text(TodoDateFormatter.dueLabel(due: d, hasTime: item.dueHasTime, now: now))
                            .font(GokeTypography.labelSmall)
                            .monospacedDigit()
                            .foregroundStyle(theme.accent)
                    } else if let d = sharedDueDate {
                        Text(TodoDateFormatter.shortDate(d))
                            .font(GokeTypography.labelSmall)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }
                    if let course = matchedCourse {
                        Text("/\(course.name)")
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.accent)
                            .lineLimit(1)
                    } else if let cname = item.courseName {
                        Text("/\(cname)")
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.muted)
                    }
                    if let listName = item.listName {
                        Text("#\(listName)")
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.tertiary)
                    }
                    ForEach(item.tags.prefix(2), id: \.self) { tag in
                        Text("@\(tag)")
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.secondary)
                    }
                    if item.priority > 0 {
                        Text(item.priority >= 3 ? "!高" : (item.priority == 2 ? "!中" : "!低"))
                            .font(GokeTypography.labelSmall.weight(.semibold))
                            .foregroundStyle(theme.priorityColor(item.priority) ?? theme.muted)
                    }
                    if !item.remindMinutesList.isEmpty {
                        Image(systemName: item.remindPersistent ? "bell.badge.fill" : "bell.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(item.remindPersistent ? theme.error : theme.accent)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, GokeSpacing.md)
        .padding(.vertical, 10)
    }

    private func batchChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(GokeTypography.labelMedium.weight(selected ? .semibold : .medium))
                .foregroundStyle(selected ? theme.onAccentContainer : theme.ink)
                .padding(.horizontal, 12)
                .frame(minHeight: 34)
                .background(Capsule().fill(selected ? theme.accentContainer : theme.surfaceHighest))
        }
        .buttonStyle(.mintCompact)
    }

    private func commitBatch(_ items: [QuickBatchItem], sharedDueDate: LocalDate?) {
        guard !items.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let live = container.liveSnapshot
        let existingLists = live.lists
        let maxSortOrder = live.tasks.filter { $0.deletedAt == nil }.map(\.sortOrder).max() ?? 0
        var newListsByKey: [String: TaskList] = [:]
        var nextListSort = existingLists.count
        var changes: [Change] = []

        func resolveListId(for listName: String?) -> String? {
            guard let raw = listName?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
                return nil
            }
            if let existing = existingLists.first(where: { $0.name.localizedCaseInsensitiveCompare(raw) == .orderedSame }) {
                return existing.id
            }
            let key = raw.lowercased()
            if let created = newListsByKey[key] {
                return created.id
            }
            let created = TaskList(
                name: raw,
                colorIndex: (nextListSort + 1) % max(theme.courseColors.count, 1),
                sort: nextListSort,
                updatedAt: nowMillis
            )
            nextListSort += 1
            newListsByKey[key] = created
            changes.append(.add(after: .taskList(created)))
            return created.id
        }

        let generatedIds = items.map { _ in UUID().uuidString.lowercased() }
        var resolvedListIdsByIndex: [Int: String] = [:]

        for (idx, batchItem) in items.enumerated() {
            let item = batchItem.result
            let due: LocalDateTime?
            let dueHasTime: Bool
            if let parsedDue = item.due {
                due = item.dueHasTime ? parsedDue : parsedDue.date.atTime(.endOfDay)
                dueHasTime = item.dueHasTime
            } else if let sharedDueDate {
                due = sharedDueDate.atTime(.endOfDay)
                dueHasTime = false
            } else {
                due = nil
                dueHasTime = false
            }

            let parentId = batchItem.parentIndex.flatMap { pIdx in
                generatedIds.indices.contains(pIdx) ? generatedIds[pIdx] : nil
            }
            let parentListId = batchItem.parentIndex.flatMap { resolvedListIdsByIndex[$0] }
            let matchedListId = resolveListId(for: item.listName) ?? (parentId == nil ? selectedListId : parentListId)
            if let matchedListId {
                resolvedListIdsByIndex[idx] = matchedListId
            }

            let matchedCourseId = item.courseName.flatMap { name in
                TodoQuickHelpers.matchCourse(
                    name: name,
                    courses: live.courses,
                    currentSemesterId: live.currentSemester?.id
                )?.id
            }
            let reminders = Reminders.normalize(item.remindMinutesList)
            let task = GokeTask(
                id: generatedIds[idx],
                title: item.title,
                listId: matchedListId,
                tags: item.tags,
                priority: item.priority,
                due: due,
                dueHasTime: dueHasTime,
                parentId: parentId,
                remindMinutes: reminders.first,
                remindPersistent: item.remindPersistent && !reminders.isEmpty,
                courseId: matchedCourseId,
                dueBeforeNextClass: matchedCourseId != nil && due == nil,
                sortOrder: maxSortOrder + idx + 1,
                source: .IMPORT,
                updatedAt: nowMillis,
                remindMinutesList: reminders
            )
            changes.append(.add(after: .task(task)))
        }

        let cs = ChangeSet(
            description: "批量导入 \(items.count) 条待办",
            source: .IMPORT,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已导入 \(items.count) 条待办")
            if ok { dismiss() }
        }
    }
}
