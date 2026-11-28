import Foundation
import GokeCore
import GokeShared
import SwiftUI

// MARK: - Unified WhenPick & WhenPanelResult (Android EventTimeSheet.kt Parity)

/// Unified date / deadline / calendar time-block selection shared by `QuickAddSheet` and `TodoEditView`.
/// - `start == nil`: date-only deadline (`due = date 23:59`, `dueHasTime = false`)
/// - `start != nil && minutes == nil`: timed deadline (`due = date + start`, `dueHasTime = true`, `blockStart = nil`)
/// - `start != nil && minutes != nil`: calendar time block (`blockStart = date + start`, `blockMinutes = minutes`, `due = blockStart + minutes`, `dueHasTime = true`)
struct WhenPick: Equatable, Hashable, Sendable {
    var date: LocalDate
    var start: LocalTime?
    var minutes: Int?

    init(date: LocalDate, start: LocalTime? = nil, minutes: Int? = nil) {
        self.date = date
        self.start = start
        self.minutes = start == nil ? nil : minutes
    }

    /// Resolved due timestamp matching Android `QuickAddBar.kt:1393` & `TodoEditScreen.kt:501`.
    var resolvedDue: LocalDateTime {
        if let start {
            let startDT = date.atTime(start)
            if let minutes, minutes > 0 {
                return startDT.addingMinutes(minutes)
            }
            return startDT
        }
        return date.atTime(.endOfDay)
    }

    var resolvedDueHasTime: Bool {
        start != nil
    }

    var resolvedBlockStart: LocalDateTime? {
        guard let start, let minutes, minutes > 0 else { return nil }
        return date.atTime(start)
    }

    var resolvedBlockMinutes: Int? {
        guard start != nil, let minutes, minutes > 0 else { return nil }
        return minutes
    }
}

/// Complete output of `GokeWhenPanel`.
struct WhenPanelResult: Equatable, Hashable, Sendable {
    var whenPick: WhenPick?
    var remindMinutesList: [Int]
    var remindPersistent: Bool
    var freq: Freq?
    var interval: Int
    var until: LocalDate?
    var byDays: Set<Int>
    var byMonths: Set<Int>

    init(
        whenPick: WhenPick? = nil,
        remindMinutesList: [Int] = [],
        remindPersistent: Bool = false,
        freq: Freq? = nil,
        interval: Int = 1,
        until: LocalDate? = nil,
        byDays: Set<Int> = [],
        byMonths: Set<Int> = []
    ) {
        self.whenPick = whenPick
        self.remindMinutesList = Reminders.normalize(remindMinutesList)
        self.remindPersistent = remindPersistent && !self.remindMinutesList.isEmpty
        self.freq = freq
        self.interval = max(1, interval)
        self.until = until
        self.byDays = byDays
        self.byMonths = byMonths
    }

    func buildRecurrence(
        preservedCount: Int? = nil,
        preservedExDates: Set<LocalDate> = []
    ) -> Recurrence? {
        guard let freq else { return nil }
        return Recurrence(
            freq: freq,
            interval: max(1, interval),
            byDays: (freq == .WEEKLY || freq == .MONTHLY || freq == .YEARLY) ? byDays : [],
            until: until,
            count: preservedCount,
            exDates: preservedExDates,
            byMonths: freq == .YEARLY ? byMonths : []
        )
    }
}

// MARK: - Formatting Helpers

enum GokeWhenFormatter {
    static let weekdayShorts = ["一", "二", "三", "四", "五", "六", "日"]

    static func weekdayChar(_ dow: Int) -> String {
        guard (1...7).contains(dow) else { return "" }
        return weekdayShorts[dow - 1]
    }

    static func nextMonday(after date: LocalDate) -> LocalDate {
        date.startOfWeekMonday.addingWeeks(1)
    }

    static func semesterEndDate(_ semester: Semester?) -> LocalDate? {
        guard let semester else { return nil }
        return semester.week1Monday.addingWeeks(max(1, semester.totalWeeks)).addingDays(-1)
    }

    static func formatDuration(_ minutes: Int) -> String {
        if minutes <= 0 { return "无" }
        let h = minutes / 60
        let m = minutes % 60
        if h > 0 && m > 0 {
            return "\(h) 小时 \(m) 分"
        } else if h > 0 {
            return "\(h) 小时"
        } else {
            return "\(m) 分钟"
        }
    }

    static func formatShortDuration(_ minutes: Int) -> String {
        if minutes <= 0 { return "无" }
        let h = minutes / 60
        let m = minutes % 60
        if h > 0 && m > 0 {
            return "\(h)h\(m)m"
        } else if h > 0 {
            return "\(h)小时"
        } else {
            return "\(m)分"
        }
    }

    static func formatWhenPick(_ pick: WhenPick, today: LocalDate) -> String {
        let dayPart: String
        let diff = today.daysUntil(pick.date)
        switch diff {
        case 0: dayPart = "今天"
        case 1: dayPart = "明天"
        case 2: dayPart = "后天"
        default:
            dayPart = pick.date.year == today.year
                ? "\(pick.date.month)月\(pick.date.day)日"
                : "\(pick.date.year)/\(pick.date.month)/\(pick.date.day)"
        }
        guard let start = pick.start else {
            return dayPart
        }
        let startStr = TodoDateFormatter.hhmm(start)
        if let mins = pick.minutes, mins > 0 {
            let endDT = pick.date.atTime(start).addingMinutes(mins)
            let endStr = TodoDateFormatter.hhmm(endDT.time)
            return "\(dayPart) \(startStr)–\(endStr)"
        }
        return "\(dayPart) \(startStr)"
    }

    static func formatRemindOffset(_ minutes: Int, hasTime: Bool) -> String {
        if !hasTime {
            switch minutes {
            case 0: return "当天 09:00"
            case 780: return "前一天 20:00"
            case 1440: return "提前 1 天"
            case 2880: return "提前 2 天"
            case 4320: return "提前 3 天"
            case 10080: return "提前 1 周"
            default: break
            }
        }
        if minutes == 0 { return "准时" }
        if minutes % 10080 == 0 { return "提前 \(minutes / 10080) 周" }
        if minutes % 1440 == 0 { return "提前 \(minutes / 1440) 天" }
        if minutes % 60 == 0 { return "提前 \(minutes / 60) 小时" }
        return "提前 \(minutes) 分钟"
    }

    static func formatRemindSummary(list: [Int], persistent: Bool, hasTime: Bool) -> String {
        let normalized = Reminders.normalize(list)
        guard !normalized.isEmpty else { return "无" }
        let suffix = persistent ? "（持续）" : ""
        if normalized.count == 1 {
            return formatRemindOffset(normalized[0], hasTime: hasTime) + suffix
        }
        let first = formatRemindOffset(normalized[0], hasTime: hasTime)
        if normalized.count == 2 {
            let second = formatRemindOffset(normalized[1], hasTime: hasTime)
            return "\(first)、\(second)\(suffix)"
        }
        return "\(first) 等 \(normalized.count) 次\(suffix)"
    }

    static func formatRecurrence(
        freq: Freq?,
        interval: Int,
        byDays: Set<Int>,
        byMonths: Set<Int>,
        until: LocalDate? = nil,
        anchor: LocalDate,
        semesterEnd: LocalDate? = nil
    ) -> String {
        guard let freq else { return "不重复" }
        let step = max(1, interval)
        let base: String
        switch freq {
        case .DAILY:
            base = step == 1 ? "每天" : "每 \(step) 天"
        case .WEEKLY:
            let days = byDays.isEmpty ? [anchor.dayOfWeek] : byDays.sorted()
            if step == 1 && days == [1, 2, 3, 4, 5] {
                base = "工作日"
            } else if step == 1 && days.count == 1 {
                if let semEnd = semesterEnd, until == semEnd {
                    return "本学期每周\(weekdayChar(days[0]))"
                }
                base = "每周\(weekdayChar(days[0]))"
            } else {
                let dayLabels = days.map { "周\(weekdayChar($0))" }.joined(separator: "、")
                base = step == 1 ? "每周 \(dayLabels)" : "每 \(step) 周 \(dayLabels)"
            }
        case .MONTHLY:
            let days = byDays.isEmpty ? [anchor.day] : byDays.sorted()
            let dayText = days.prefix(4).map { "\($0)" }.joined(separator: "、")
                + (days.count > 4 ? "…" : "")
            base = step == 1 ? "每月 \(dayText) 日" : "每 \(step) 月 \(dayText) 日"
        case .YEARLY:
            let months = byMonths.isEmpty ? [anchor.month] : byMonths.sorted()
            let days = byDays.isEmpty ? [anchor.day] : byDays.sorted()
            if step == 1 && months.count == 1 && days.count == 1 {
                base = "每年 \(months[0])月\(days[0])日"
            } else {
                let mText = months.prefix(3).map { "\($0)月" }.joined(separator: "、")
                    + (months.count > 3 ? "…" : "")
                let dText = days.prefix(3).map { "\($0)日" }.joined(separator: "、")
                    + (days.count > 3 ? "…" : "")
                base = step == 1 ? "每年 \(mText) \(dText)" : "每 \(step) 年 \(mText) \(dText)"
            }
        }
        if let until {
            if let semEnd = semesterEnd, until == semEnd {
                return "\(base) · 至学期末"
            }
            return "\(base) · 至\(until.month)/\(until.day)"
        }
        return base
    }
}

// MARK: - Wrapping Flow Layout (Shared across Todo Views)

struct TodoFlowLayout: Layout {
    var horizontalSpacing: CGFloat = 8
    var verticalSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var maxUsedWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth > 0 && rowWidth + horizontalSpacing + size.width > maxWidth {
                totalHeight += rowHeight + verticalSpacing
                maxUsedWidth = max(maxUsedWidth, rowWidth)
                rowWidth = size.width
                rowHeight = size.height
            } else {
                if rowWidth > 0 { rowWidth += horizontalSpacing }
                rowWidth += size.width
                rowHeight = max(rowHeight, size.height)
            }
        }
        totalHeight += rowHeight
        maxUsedWidth = max(maxUsedWidth, rowWidth)
        return CGSize(width: min(maxWidth, maxUsedWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + verticalSpacing
                rowHeight = 0
            }
            subview.place(
                at: CGPoint(x: x, y: y),
                anchor: .topLeading,
                proposal: ProposedViewSize(size)
            )
            x += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - GokeWhenPanel

/// Unified Android `WhenPanel` (`EventTimeSheet.kt`) shared by `QuickAddSheet` and `TodoEditView`:
/// quick chips (`今天`, `明天`, `今晚 20:00`, `本周末`, `下周一`, `无日期`), always-visible compact month
/// calendar grid, time & animated time-block duration rows (`due = blockStart + blockMinutes`, `>3h` hint),
/// context-aware multi-reminders (`当天 09:00` / `前一天 20:00` vs timed + custom `0..10080` min),
/// full repeat picker (`每天`, `工作日`, `每周`, `本学期每周X`, `每月`/`每年` `1..31` `byDays`), and end-repeat sheet.
struct GokeWhenPanel: View {
    let initialPick: WhenPick?
    let initialRemindList: [Int]
    let initialRemindPersistent: Bool
    let initialFreq: Freq?
    let initialInterval: Int
    let initialUntil: LocalDate?
    let initialByDays: Set<Int>
    let initialByMonths: Set<Int>
    let semesterEnd: LocalDate?
    let onConfirm: (WhenPanelResult) -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    @State private var datePicked: Bool
    @State private var selectedDate: LocalDate
    @State private var displayedMonthStart: LocalDate
    @State private var startTime: LocalTime?
    @State private var blockMinutes: Int?

    @State private var remindList: [Int]
    @State private var remindPersistent: Bool

    @State private var freq: Freq?
    @State private var interval: Int
    @State private var until: LocalDate?
    @State private var byDays: Set<Int>
    @State private var byMonths: Set<Int>

    @State private var expandedSection: InlineSection?
    @State private var showCustomDurationWheels: Bool = false
    @State private var showCustomRemindInput: Bool = false
    @State private var customRemindText: String = ""
    @State private var customRemindError: String?
    @State private var showCustomRepeatBuilder: Bool = false
    @State private var showCustomEndCalendar: Bool = false
    @State private var customEndMonthStart: LocalDate

    private enum InlineSection: String, Equatable {
        case time
        case duration
        case remind
        case repeatRule
        case endRepeat
    }

    private let quickTimePresets: [(String, Int, Int)] = [
        ("08:00", 8, 0),
        ("09:00", 9, 0),
        ("14:00", 14, 0),
        ("18:00", 18, 0),
        ("20:00", 20, 0),
        ("22:00", 22, 0),
    ]

    private let durationPresets: [(String, Int)] = [
        ("无", 0),
        ("15 分钟", 15),
        ("30 分钟", 30),
        ("45 分钟", 45),
        ("1 小时", 60),
        ("1.5 小时", 90),
        ("2 小时", 120),
        ("3 小时", 180),
    ]

    init(
        initialPick: WhenPick? = nil,
        initialRemindList: [Int] = [],
        initialRemindPersistent: Bool = false,
        initialFreq: Freq? = nil,
        initialInterval: Int = 1,
        initialUntil: LocalDate? = nil,
        initialByDays: Set<Int> = [],
        initialByMonths: Set<Int> = [],
        semesterEnd: LocalDate? = nil,
        onConfirm: @escaping (WhenPanelResult) -> Void
    ) {
        self.initialPick = initialPick
        self.initialRemindList = initialRemindList
        self.initialRemindPersistent = initialRemindPersistent
        self.initialFreq = initialFreq
        self.initialInterval = max(1, initialInterval)
        self.initialUntil = initialUntil
        self.initialByDays = initialByDays
        self.initialByMonths = initialByMonths
        self.semesterEnd = semesterEnd
        self.onConfirm = onConfirm

        let today = LocalDate.today()
        let anchor = initialPick?.date ?? today
        self._datePicked = State(initialValue: initialPick != nil)
        self._selectedDate = State(initialValue: anchor)
        self._displayedMonthStart = State(initialValue: anchor.withDayOfMonth(1))
        self._startTime = State(initialValue: initialPick?.start)
        self._blockMinutes = State(initialValue: initialPick?.start != nil ? initialPick?.minutes : nil)
        self._remindList = State(initialValue: Reminders.normalize(initialRemindList))
        self._remindPersistent = State(initialValue: initialRemindPersistent && !initialRemindList.isEmpty)
        self._freq = State(initialValue: initialFreq)
        self._interval = State(initialValue: max(1, initialInterval))
        self._until = State(initialValue: initialUntil)
        self._byDays = State(initialValue: initialByDays)
        self._byMonths = State(initialValue: initialByMonths)
        let defaultEnd = initialUntil ?? semesterEnd ?? anchor.addingMonths(1)
        self._customEndMonthStart = State(initialValue: defaultEnd.withDayOfMonth(1))
    }

    private var hasAnySet: Bool {
        datePicked || startTime != nil || blockMinutes != nil || !remindList.isEmpty || freq != nil
    }

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    private var inlineTransition: AnyTransition {
        simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top))
    }

    private func toggleSection(_ section: InlineSection) {
        let isClosing = expandedSection == section
        let anim = isClosing
            ? GokeMotion.dismiss(policy: performance)
            : GokeMotion.snappy(policy: performance)
        withAnimation(anim) {
            expandedSection = isClosing ? nil : section
        }
    }

    var body: some View {
        let today = LocalDate.today()
        let tomorrow = today.addingDays(1)
        let nextMon = GokeWhenFormatter.nextMonday(after: today)
        let isTonight = datePicked && selectedDate == today && startTime == LocalTime(hour: 20, minute: 0)

        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.md) {
                    // Compact 1-row 4-column Quick Chips (matching Android EventTimeSheet.kt)
                    quickChipsStrip(
                        today: today,
                        tomorrow: tomorrow,
                        nextMon: nextMon,
                        isTonight: isTonight
                    )

                    // Fixed 6-row Month Calendar Grid (zero vertical height jumps + interactive swipe)
                    GokeCompactMonthGrid(
                        displayedMonthStart: $displayedMonthStart,
                        selectedDate: datePicked ? selectedDate : nil,
                        today: today,
                        showCardBackground: false
                    ) { picked in
                        withAnimation(GokeMotion.press(policy: performance)) {
                            selectedDate = picked
                            datePicked = true
                            displayedMonthStart = picked.withDayOfMonth(1)
                        }
                    }

                    // Flat Mint Paper Setting Rows with Inline Expandable Pickers (zero nested .sheet stacks!)
                    VStack(spacing: 2) {
                        // 1. 时间
                        settingRow(
                            icon: "clock",
                            title: "时间",
                            value: startTime.map { TodoDateFormatter.hhmm($0) } ?? "无",
                            isActive: startTime != nil,
                            isExpanded: expandedSection == .time,
                            onTap: { toggleSection(.time) },
                            onClear: startTime != nil ? {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    startTime = nil
                                    blockMinutes = nil
                                    if expandedSection == .duration { expandedSection = nil }
                                }
                            } : nil
                        )

                        if expandedSection == .time {
                            inlineTimePickerSection
                                .transition(inlineTransition)
                        }

                        // 2. 持续时间 (shown when startTime != nil)
                        if let start = startTime {
                            settingRow(
                                icon: "timer",
                                title: "持续时间",
                                value: {
                                    guard let mins = blockMinutes, mins > 0 else { return "无" }
                                    let endTime = selectedDate.atTime(start).addingMinutes(mins).time
                                    return "\(GokeWhenFormatter.formatDuration(mins))（至 \(TodoDateFormatter.hhmm(endTime))）"
                                }(),
                                isActive: (blockMinutes ?? 0) > 0,
                                isExpanded: expandedSection == .duration,
                                onTap: { toggleSection(.duration) },
                                onClear: (blockMinutes ?? 0) > 0 ? {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        blockMinutes = nil
                                    }
                                } : nil
                            )
                            .transition(inlineTransition)

                            if expandedSection == .duration {
                                inlineDurationPickerSection(start: start)
                                    .transition(inlineTransition)
                            }
                            if let mins = blockMinutes, mins > 180 {
                                Text("超过 3 小时不显示在课表网格")
                                    .font(GokeTypography.labelMedium)
                                    .foregroundStyle(theme.muted)
                                    .padding(.horizontal, GokeSpacing.md)
                                    .padding(.bottom, GokeSpacing.xs)
                                    .transition(.opacity)
                            }
                        }

                        // 3. 提醒
                        settingRow(
                            icon: remindPersistent ? "alarm.waves.left.and.right" : "alarm",
                            title: "提醒",
                            value: GokeWhenFormatter.formatRemindSummary(
                                list: remindList,
                                persistent: remindPersistent,
                                hasTime: startTime != nil
                            ),
                            isActive: !remindList.isEmpty,
                            isExpanded: expandedSection == .remind,
                            onTap: { toggleSection(.remind) },
                            onClear: !remindList.isEmpty ? {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    remindList = []
                                    remindPersistent = false
                                }
                            } : nil
                        )

                        if expandedSection == .remind {
                            inlineRemindPickerSection
                                .transition(inlineTransition)
                        }

                        // 4. 重复
                        settingRow(
                            icon: "repeat",
                            title: "重复",
                            value: GokeWhenFormatter.formatRecurrence(
                                freq: freq,
                                interval: interval,
                                byDays: byDays,
                                byMonths: byMonths,
                                until: nil,
                                anchor: selectedDate,
                                semesterEnd: semesterEnd
                            ),
                            isActive: freq != nil,
                            isExpanded: expandedSection == .repeatRule,
                            onTap: { toggleSection(.repeatRule) },
                            onClear: freq != nil ? {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    freq = nil
                                    interval = 1
                                    until = nil
                                    byDays = []
                                    byMonths = []
                                    showCustomRepeatBuilder = false
                                    if expandedSection == .endRepeat { expandedSection = nil }
                                }
                            } : nil
                        )

                        if expandedSection == .repeatRule {
                            inlineRepeatPickerSection
                                .transition(inlineTransition)
                        }

                        // 5. 结束重复 (shown when freq != nil)
                        if freq != nil {
                            settingRow(
                                icon: "calendar.badge.exclamationmark",
                                title: "结束重复",
                                value: endRepeatLabel,
                                isActive: until != nil,
                                isExpanded: expandedSection == .endRepeat,
                                onTap: { toggleSection(.endRepeat) },
                                onClear: until != nil ? {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        until = nil
                                        showCustomEndCalendar = false
                                    }
                                } : nil
                            )
                            .transition(inlineTransition)

                            if expandedSection == .endRepeat {
                                inlineEndRepeatPickerSection
                                    .transition(inlineTransition)
                            }
                        }
                    }
                    .clipped()
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.top, GokeSpacing.md)
                .padding(.bottom, GokeSpacing.lg)
            }
            .scrollDismissesKeyboard(.interactively)

            // Lightweight Android-Parity TextButton Footer (zero heavy divider, zero bloated capsule)
            HStack(spacing: GokeSpacing.xs) {
                if hasAnySet {
                    Button {
                        onConfirm(WhenPanelResult(whenPick: nil))
                        dismiss()
                    } label: {
                        Text("清除时间")
                            .font(GokeTypography.labelLarge.weight(.semibold))
                            .foregroundStyle(theme.removed)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                }

                Spacer(minLength: 0)

                Button {
                    dismiss()
                } label: {
                    Text("取消")
                        .font(GokeTypography.labelLarge)
                        .foregroundStyle(theme.muted)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)

                Button {
                    commitAndDismiss()
                } label: {
                    Text("确定")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                        .foregroundStyle(theme.accent)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }
            .padding(.horizontal, GokeSpacing.lg)
            .padding(.vertical, GokeSpacing.xs)
            .background(theme.paper)
        }
        .background(theme.paper.ignoresSafeArea())
    }

    private var endRepeatLabel: String {
        guard let until else { return "永不" }
        if let semEnd = semesterEnd, until == semEnd {
            return "学期末（\(semEnd.month)月\(semEnd.day)日）"
        }
        return "\(until.month)月\(until.day)日 周\(GokeWhenFormatter.weekdayChar(until.dayOfWeek))"
    }

    // MARK: - Compact 4-Column Quick Chips (1 Row, ~56pt)

    private func quickChipsStrip(
        today: LocalDate,
        tomorrow: LocalDate,
        nextMon: LocalDate,
        isTonight: Bool
    ) -> some View {
        HStack(spacing: 6) {
            quickChip(
                icon: "sun.max",
                title: "今天",
                selected: datePicked && selectedDate == today && !isTonight
            ) {
                withAnimation(GokeMotion.press(policy: performance)) {
                    selectedDate = today
                    displayedMonthStart = today.withDayOfMonth(1)
                    datePicked = true
                    if isTonight { startTime = nil; blockMinutes = nil }
                }
            }

            quickChip(
                icon: "sunrise",
                title: "明天",
                selected: datePicked && selectedDate == tomorrow
            ) {
                withAnimation(GokeMotion.press(policy: performance)) {
                    selectedDate = tomorrow
                    displayedMonthStart = tomorrow.withDayOfMonth(1)
                    datePicked = true
                }
            }

            quickChip(
                icon: "arrow.right.circle",
                title: "下周一",
                selected: datePicked && selectedDate == nextMon
            ) {
                withAnimation(GokeMotion.press(policy: performance)) {
                    selectedDate = nextMon
                    displayedMonthStart = nextMon.withDayOfMonth(1)
                    datePicked = true
                }
            }

            quickChip(
                icon: "moon.stars",
                title: "今晚",
                selected: isTonight
            ) {
                withAnimation(GokeMotion.press(policy: performance)) {
                    selectedDate = today
                    displayedMonthStart = today.withDayOfMonth(1)
                    datePicked = true
                    startTime = LocalTime(hour: 20, minute: 0)
                    blockMinutes = nil
                }
            }
        }
    }

    private func quickChip(
        icon: String,
        title: String,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 19, weight: .medium))
                    .foregroundStyle(selected ? theme.accent : theme.ink)
                Text(title)
                    .font(GokeTypography.labelMedium.weight(selected ? .semibold : .medium))
                    .foregroundStyle(selected ? theme.accent : theme.muted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(
                RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                    .fill(selected ? theme.accentContainer.opacity(0.75) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - Inline Expandable Sections (Zero Nested .sheet Stacks)

    private var inlineTimePickerSection: some View {
        let timeBinding = Binding<Date>(
            get: { LocalDate.today().atTime(startTime ?? LocalTime(hour: 18, minute: 0)).toDate() },
            set: { nextDate in
                let t = LocalDateTime.fromDate(nextDate).time
                datePicked = true
                startTime = LocalTime(hour: t.hour, minute: t.minute)
            }
        )
        return VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(quickTimePresets, id: \.0) { label, h, m in
                        let selected = startTime?.hour == h && startTime?.minute == m
                        inlinePillChip(label, isSelected: selected) {
                            withAnimation(GokeMotion.press(policy: performance)) {
                                datePicked = true
                                startTime = LocalTime(hour: h, minute: m)
                            }
                        }
                    }
                }
            }

            DatePicker("时间", selection: timeBinding, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .frame(height: 148)
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.bottom, 6)
    }

    private func inlineDurationPickerSection(start: LocalTime) -> some View {
        let total = max(0, min(1440, blockMinutes ?? 0))
        let hoursBinding = Binding<Int>(
            get: { total / 60 },
            set: { nextH in
                let nextTotal = min(1440, nextH * 60 + (total % 60))
                blockMinutes = nextTotal > 0 ? nextTotal : nil
            }
        )
        let minsBinding = Binding<Int>(
            get: { ((total % 60) / 5) * 5 },
            set: { nextM in
                let nextTotal = min(1440, (total / 60) * 60 + nextM)
                blockMinutes = nextTotal > 0 ? nextTotal : nil
            }
        )
        let isPreset = durationPresets.contains { $0.1 == total }

        return VStack(alignment: .leading, spacing: 10) {
            if total > 0 {
                let end = selectedDate.atTime(start).addingMinutes(total).time
                Text("\(TodoDateFormatter.hhmm(start)) – \(TodoDateFormatter.hhmm(end))")
                    .font(GokeTypography.labelMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.accent)
            }

            TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                ForEach(durationPresets, id: \.1) { title, value in
                    inlinePillChip(title, isSelected: total == value) {
                        withAnimation(GokeMotion.press(policy: performance)) {
                            blockMinutes = value > 0 ? value : nil
                            showCustomDurationWheels = false
                        }
                    }
                }
                inlinePillChip("自定义", isSelected: showCustomDurationWheels || !isPreset) {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        showCustomDurationWheels.toggle()
                    }
                }
            }

            if showCustomDurationWheels || !isPreset {
                HStack(spacing: 0) {
                    Picker("小时", selection: hoursBinding) {
                        ForEach(0...23, id: \.self) { h in
                            Text("\(h) 小时").monospacedDigit().tag(h)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)

                    Picker("分钟", selection: minsBinding) {
                        ForEach(Array(stride(from: 0, through: 55, by: 5)), id: \.self) { m in
                            Text("\(m) 分钟").monospacedDigit().tag(m)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)
                }
                .frame(height: 124)
            }
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.bottom, 6)
    }

    private var inlineRemindPickerSection: some View {
        let hasTime = startTime != nil
        let presets: [(String, Int)] = hasTime
            ? [
                ("准时", 0),
                ("提前 5 分钟", 5),
                ("提前 10 分钟", 10),
                ("提前 15 分钟", 15),
                ("提前 30 分钟", 30),
                ("提前 1 小时", 60),
                ("提前 1 天", 1440),
            ]
            : [
                ("当天 09:00", 0),
                ("前一天 20:00", 780),
                ("提前 1 天", 1440),
                ("提前 2 天", 2880),
                ("提前 3 天", 4320),
                ("提前 1 周", 10080),
            ]
        let presetValues = Set(presets.map(\.1))
        let customExtras = remindList.filter { !presetValues.contains($0) }.sorted()

        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(hasTime ? "可多选" : "当天 09:00 触发")
                    .font(GokeTypography.labelSmall)
                    .foregroundStyle(theme.muted)
                Spacer()
            }

            TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                inlinePillChip("无提醒", isSelected: remindList.isEmpty) {
                    withAnimation(GokeMotion.press(policy: performance)) {
                        remindList = []
                        remindPersistent = false
                    }
                }

                ForEach(presets, id: \.1) { title, mins in
                    inlinePillChip(title, isSelected: remindList.contains(mins)) {
                        toggleInlineRemindMinute(mins)
                    }
                }

                ForEach(customExtras, id: \.self) { extra in
                    inlinePillChip(
                        GokeWhenFormatter.formatRemindOffset(extra, hasTime: hasTime),
                        isSelected: true
                    ) {
                        toggleInlineRemindMinute(extra)
                    }
                }

                inlinePillChip("+ 自定义", isSelected: showCustomRemindInput) {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        showCustomRemindInput.toggle()
                        customRemindError = nil
                    }
                }
            }

            if showCustomRemindInput {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        TextField("提前分钟（0–10080）", text: $customRemindText)
                            .keyboardType(.numberPad)
                            .font(GokeTypography.bodyMedium)
                            .monospacedDigit()
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .background(
                                RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                    .fill(theme.surfaceHighest)
                            )

                        Button {
                            let trimmed = customRemindText.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard let val = Int(trimmed), (0...10080).contains(val) else {
                                customRemindError = "请输入 0–10080"
                                return
                            }
                            withAnimation(GokeMotion.snappy(policy: performance)) {
                                if !remindList.contains(val) {
                                    remindList = Reminders.normalize(remindList + [val])
                                }
                                if !datePicked { datePicked = true }
                                customRemindText = ""
                                customRemindError = nil
                                showCustomRemindInput = false
                            }
                        } label: {
                            Text("添加")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .foregroundStyle(theme.onAccent)
                                .padding(.horizontal, 16)
                                .frame(minHeight: 40)
                                .background(Capsule().fill(theme.accent))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.mintCompact)
                    }
                    if let customRemindError {
                        Text(customRemindError)
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.error)
                    }
                }
            }

            Toggle(isOn: Binding(
                get: { remindPersistent && !remindList.isEmpty },
                set: { remindPersistent = $0 }
            )) {
                Text("持续提醒")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(remindList.isEmpty ? theme.muted : theme.ink)
            }
            .disabled(remindList.isEmpty)
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.bottom, 6)
    }

    private func toggleInlineRemindMinute(_ mins: Int) {
        withAnimation(GokeMotion.press(policy: performance)) {
            if remindList.contains(mins) {
                remindList.removeAll { $0 == mins }
                if remindList.isEmpty { remindPersistent = false }
            } else {
                remindList = Reminders.normalize(remindList + [mins])
                if !datePicked { datePicked = true }
            }
        }
    }

    private var inlineRepeatPickerSection: some View {
        let dowChar = GokeWhenFormatter.weekdayChar(selectedDate.dayOfWeek)
        let canShowSemesterWeekly = semesterEnd.map { selectedDate <= $0 } ?? false
        let activeFreq = freq ?? .WEEKLY

        return VStack(alignment: .leading, spacing: 10) {
            TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                inlinePillChip("不重复", isSelected: freq == nil) {
                    applyInlineRepeat(nil, interval: 1, byDays: [], byMonths: [], until: nil)
                }
                inlinePillChip("每天", isSelected: freq == .DAILY && interval == 1) {
                    applyInlineRepeat(.DAILY, interval: 1, byDays: [], byMonths: [], until: nil)
                }
                inlinePillChip(
                    "工作日",
                    isSelected: freq == .WEEKLY && interval == 1 && byDays == [1, 2, 3, 4, 5]
                ) {
                    applyInlineRepeat(.WEEKLY, interval: 1, byDays: [1, 2, 3, 4, 5], byMonths: [], until: nil)
                }
                inlinePillChip(
                    "每周（周\(dowChar)）",
                    isSelected: freq == .WEEKLY && interval == 1
                        && (byDays.isEmpty || byDays == [selectedDate.dayOfWeek])
                        && (semesterEnd == nil || until != semesterEnd)
                ) {
                    applyInlineRepeat(.WEEKLY, interval: 1, byDays: [selectedDate.dayOfWeek], byMonths: [], until: nil)
                }
                if canShowSemesterWeekly, let semEnd = semesterEnd {
                    inlinePillChip(
                        "本学期每周\(dowChar)",
                        isSelected: freq == .WEEKLY && interval == 1
                            && (byDays.isEmpty || byDays == [selectedDate.dayOfWeek])
                            && until == semEnd
                    ) {
                        applyInlineRepeat(.WEEKLY, interval: 1, byDays: [selectedDate.dayOfWeek], byMonths: [], until: semEnd)
                    }
                }
                inlinePillChip(
                    "每月（\(selectedDate.day)日）",
                    isSelected: freq == .MONTHLY && interval == 1
                        && (byDays.isEmpty || byDays == [selectedDate.day])
                ) {
                    applyInlineRepeat(.MONTHLY, interval: 1, byDays: [selectedDate.day], byMonths: [], until: nil)
                }
                inlinePillChip(
                    "每年（\(selectedDate.month)/\(selectedDate.day)）",
                    isSelected: freq == .YEARLY && interval == 1
                        && (byMonths.isEmpty || byMonths == [selectedDate.month])
                        && (byDays.isEmpty || byDays == [selectedDate.day])
                ) {
                    applyInlineRepeat(.YEARLY, interval: 1, byDays: [selectedDate.day], byMonths: [selectedDate.month], until: nil)
                }
                inlinePillChip("自定义…", isSelected: showCustomRepeatBuilder) {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        showCustomRepeatBuilder.toggle()
                        if showCustomRepeatBuilder && freq == nil {
                            freq = .WEEKLY
                            interval = 1
                            byDays = [selectedDate.dayOfWeek]
                            if !datePicked { datePicked = true }
                        }
                    }
                }
            }

            if showCustomRepeatBuilder {
                VStack(alignment: .leading, spacing: 10) {
                    GokeSegmentedPill(
                        options: [
                            (Freq.DAILY, "天"),
                            (Freq.WEEKLY, "周"),
                            (Freq.MONTHLY, "月"),
                            (Freq.YEARLY, "年"),
                        ],
                        selection: Binding(
                            get: { activeFreq },
                            set: { next in
                                freq = next
                                if !datePicked { datePicked = true }
                                normalizeInlineRepeatDefaults(for: next)
                            }
                        ),
                        height: 40
                    )

                    HStack {
                        Text("重复间隔")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                        Spacer()
                        HStack(spacing: 4) {
                            Button {
                                if interval > 1 { interval -= 1 }
                            } label: {
                                Image(systemName: "minus")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(interval > 1 ? theme.ink : theme.muted.opacity(0.4))
                                    .frame(width: 44, height: 38)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                            .disabled(interval <= 1)

                            Text("每 \(interval) \(inlineFreqUnit(activeFreq))")
                                .font(GokeTypography.labelLarge.weight(.semibold))
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                                .frame(minWidth: 64)

                            Button {
                                if interval < 99 { interval += 1 }
                            } label: {
                                Image(systemName: "plus")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(interval < 99 ? theme.ink : theme.muted.opacity(0.4))
                                    .frame(width: 44, height: 38)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                            .disabled(interval >= 99)
                        }
                        .background(Capsule().fill(theme.surfaceHighest))
                    }

                    if activeFreq == .WEEKLY {
                        HStack(spacing: 6) {
                            ForEach(1...7, id: \.self) { dow in
                                let active = byDays.contains(dow)
                                Button {
                                    if active {
                                        if byDays.count > 1 { byDays.remove(dow) }
                                    } else {
                                        byDays.insert(dow)
                                    }
                                } label: {
                                    Text(GokeWhenFormatter.weekdayChar(dow))
                                        .font(GokeTypography.labelMedium.weight(active ? .semibold : .medium))
                                        .foregroundStyle(active ? theme.onAccent : theme.ink)
                                        .frame(maxWidth: .infinity, minHeight: 36)
                                        .background(Circle().fill(active ? theme.accent : theme.surfaceHighest))
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                    }

                    if activeFreq == .YEARLY {
                        let cols = Array(repeating: GridItem(.flexible(), spacing: 6), count: 6)
                        LazyVGrid(columns: cols, spacing: 6) {
                            ForEach(1...12, id: \.self) { mo in
                                let active = byMonths.contains(mo)
                                Button {
                                    if active {
                                        if byMonths.count > 1 { byMonths.remove(mo) }
                                    } else {
                                        byMonths.insert(mo)
                                    }
                                } label: {
                                    Text("\(mo)月")
                                        .font(GokeTypography.labelSmall.weight(active ? .semibold : .medium))
                                        .monospacedDigit()
                                        .foregroundStyle(active ? theme.onAccent : theme.ink)
                                        .frame(maxWidth: .infinity, minHeight: 32)
                                        .background(
                                            RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                                .fill(active ? theme.accent : theme.surfaceHighest)
                                        )
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                    }

                    if activeFreq == .MONTHLY || activeFreq == .YEARLY {
                        let cols = Array(repeating: GridItem(.flexible(), spacing: 5), count: 7)
                        LazyVGrid(columns: cols, spacing: 5) {
                            ForEach(1...31, id: \.self) { day in
                                let active = byDays.contains(day)
                                Button {
                                    if active {
                                        if byDays.count > 1 { byDays.remove(day) }
                                    } else {
                                        byDays.insert(day)
                                    }
                                } label: {
                                    Text("\(day)")
                                        .font(GokeTypography.labelSmall.weight(active ? .semibold : .medium))
                                        .monospacedDigit()
                                        .foregroundStyle(active ? theme.onAccent : theme.ink)
                                        .frame(maxWidth: .infinity, minHeight: 32)
                                        .background(
                                            RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                                .fill(active ? theme.accent : theme.surfaceHighest)
                                        )
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                    }
                }
                .padding(.top, 4)
            }
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.bottom, 6)
    }

    private func applyInlineRepeat(
        _ nextFreq: Freq?,
        interval nextInterval: Int,
        byDays nextByDays: Set<Int>,
        byMonths nextByMonths: Set<Int>,
        until nextUntil: LocalDate?
    ) {
        withAnimation(GokeMotion.press(policy: performance)) {
            freq = nextFreq
            interval = max(1, nextInterval)
            byDays = nextByDays
            byMonths = nextByMonths
            if let nextUntil {
                until = nextUntil
            } else if nextFreq == nil {
                until = nil
            }
            if nextFreq != nil && !datePicked {
                datePicked = true
            }
            showCustomRepeatBuilder = false
        }
    }

    private func normalizeInlineRepeatDefaults(for target: Freq) {
        switch target {
        case .DAILY:
            byDays = []
            byMonths = []
        case .WEEKLY:
            byMonths = []
            let valid = byDays.filter { (1...7).contains($0) }
            byDays = valid.isEmpty ? [selectedDate.dayOfWeek] : valid
        case .MONTHLY:
            byMonths = []
            let valid = byDays.filter { (1...31).contains($0) }
            byDays = valid.isEmpty ? [selectedDate.day] : valid
        case .YEARLY:
            let validM = byMonths.filter { (1...12).contains($0) }
            let validD = byDays.filter { (1...31).contains($0) }
            byMonths = validM.isEmpty ? [selectedDate.month] : validM
            byDays = validD.isEmpty ? [selectedDate.day] : validD
        }
    }

    private func inlineFreqUnit(_ f: Freq) -> String {
        switch f {
        case .DAILY: return "天"
        case .WEEKLY: return "周"
        case .MONTHLY: return "月"
        case .YEARLY: return "年"
        }
    }

    private var inlineEndRepeatPickerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                inlinePillChip("永不", isSelected: until == nil) {
                    withAnimation(GokeMotion.press(policy: performance)) {
                        until = nil
                        showCustomEndCalendar = false
                    }
                }
                if let semEnd = semesterEnd, semEnd >= selectedDate {
                    inlinePillChip(
                        "学期末（\(semEnd.month)/\(semEnd.day)）",
                        isSelected: until == semEnd
                    ) {
                        withAnimation(GokeMotion.press(policy: performance)) {
                            until = semEnd
                            showCustomEndCalendar = false
                        }
                    }
                }
                inlinePillChip(
                    "指定日期",
                    isSelected: showCustomEndCalendar || (until != nil && until != semesterEnd)
                ) {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        showCustomEndCalendar.toggle()
                        let base = until ?? semesterEnd ?? selectedDate.addingMonths(1)
                        customEndMonthStart = base.withDayOfMonth(1)
                    }
                }
            }

            if showCustomEndCalendar {
                GokeCompactMonthGrid(
                    displayedMonthStart: $customEndMonthStart,
                    selectedDate: until,
                    today: LocalDate.today(),
                    showCardBackground: false
                ) { picked in
                    withAnimation(GokeMotion.press(policy: performance)) {
                        let clamped = max(selectedDate, picked)
                        until = clamped
                        customEndMonthStart = clamped.withDayOfMonth(1)
                    }
                }
            }
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                .fill(theme.surface)
        )
        .padding(.bottom, 6)
    }

    private func inlinePillChip(
        _ title: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(GokeTypography.labelMedium.weight(isSelected ? .semibold : .medium))
                .monospacedDigit()
                .foregroundStyle(isSelected ? theme.onAccentContainer : theme.ink)
                .padding(.horizontal, 12)
                .frame(minHeight: 36)
                .background(Capsule().fill(isSelected ? theme.accentContainer : theme.surfaceHighest))
        }
        .buttonStyle(.mintCompact)
    }

    private func settingRow(
        icon: String,
        title: String,
        value: String,
        isActive: Bool,
        isExpanded: Bool,
        onTap: @escaping () -> Void,
        onClear: (() -> Void)?
    ) -> some View {
        HStack(spacing: 0) {
            Button(action: onTap) {
                HStack(spacing: GokeSpacing.md) {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(isActive || isExpanded ? theme.accent : theme.muted)
                        .frame(width: 24)
                    Text(title)
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)
                    Spacer(minLength: 8)
                    Text(value)
                        .font(GokeTypography.bodyMedium.weight(isActive ? .medium : .regular))
                        .monospacedDigit()
                        .foregroundStyle(isActive ? theme.accent : theme.muted)
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(isExpanded ? theme.accent : theme.muted.opacity(0.7))
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))
                }
                .padding(.leading, GokeSpacing.xs)
                .padding(.trailing, onClear == nil ? GokeSpacing.xs : 4)
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            if let onClear {
                Button(action: onClear) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 44, height: 48)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("清除\(title)")
            }
        }
    }

    private func commitAndDismiss() {
        let shouldSetDate = datePicked || startTime != nil || freq != nil || !remindList.isEmpty
        let pick: WhenPick? = shouldSetDate
            ? WhenPick(date: selectedDate, start: startTime, minutes: blockMinutes)
            : nil
        let result = WhenPanelResult(
            whenPick: pick,
            remindMinutesList: remindList,
            remindPersistent: remindPersistent,
            freq: freq,
            interval: interval,
            until: freq != nil ? until : nil,
            byDays: byDays,
            byMonths: byMonths
        )
        onConfirm(result)
        dismiss()
    }
}

// MARK: - Compact Month Calendar Grid (Fixed 6×7 Height + Interactive Swipe)

struct GokeCompactMonthGrid: View {
    private struct GridDayCell: Identifiable {
        let date: LocalDate
        let isCurrentMonth: Bool
        var id: Int64 { date.epochDays }
    }

    @Binding var displayedMonthStart: LocalDate
    let selectedDate: LocalDate?
    let today: LocalDate
    var showCardBackground: Bool = true
    let onSelectDate: (LocalDate) -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @State private var swipeOffsetX: CGFloat = 0

    /// Always generates exactly 42 cells (6 rows × 7 columns) so month switches never cause vertical layout jumps.
    private var gridCells: [GridDayCell] {
        let firstOfMonth = displayedMonthStart.withDayOfMonth(1)
        let leadingCount = firstOfMonth.dayOfWeek - 1 // Monday = 1 -> 0 leading days
        let startDate = firstOfMonth.addingDays(-leadingCount)
        return (0..<42).map { offset in
            let d = startDate.addingDays(offset)
            return GridDayCell(
                date: d,
                isCurrentMonth: d.year == firstOfMonth.year && d.month == firstOfMonth.month
            )
        }
    }

    var body: some View {
        let cells = gridCells
        let isCurrentMonth = displayedMonthStart.year == today.year && displayedMonthStart.month == today.month

        VStack(spacing: GokeSpacing.sm) {
            // Month header with prev / title / today / next
            HStack(spacing: GokeSpacing.xs) {
                Button {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        displayedMonthStart = displayedMonthStart.addingMonths(-1).withDayOfMonth(1)
                    }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.ink)
                        .frame(width: 44, height: 40)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("上个月")

                Text("\(displayedMonthStart.year)年\(displayedMonthStart.month)月")
                    .font(GokeTypography.titleMedium.weight(.semibold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .foregroundStyle(theme.ink)

                if !isCurrentMonth {
                    Button {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            displayedMonthStart = today.withDayOfMonth(1)
                        }
                    } label: {
                        Text("今")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(theme.onAccentContainer)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(theme.accentContainer))
                            .frame(minHeight: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                }

                Spacer(minLength: 0)

                Button {
                    withAnimation(GokeMotion.snappy(policy: performance)) {
                        displayedMonthStart = displayedMonthStart.addingMonths(1).withDayOfMonth(1)
                    }
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.ink)
                        .frame(width: 44, height: 40)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .accessibilityLabel("下个月")
            }

            // Weekday header
            HStack(spacing: 0) {
                ForEach(GokeWhenFormatter.weekdayShorts, id: \.self) { label in
                    Text(label)
                        .font(GokeTypography.labelSmall.weight(.semibold))
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity)
                }
            }

            // Fixed 6×7 day cells with live horizontal swipe feedback
            let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(cells) { cell in
                    let dayDate = cell.date
                    let isSelected = selectedDate == dayDate
                    let isToday = dayDate == today
                    Button {
                        onSelectDate(dayDate)
                    } label: {
                        Text("\(dayDate.day)")
                            .font(GokeTypography.bodyMedium.weight(isSelected || isToday ? .semibold : .regular))
                            .monospacedDigit()
                            .foregroundStyle(
                                isSelected
                                    ? theme.onAccent
                                    : (isToday ? theme.accent : (cell.isCurrentMonth ? theme.ink : theme.muted.opacity(0.38)))
                            )
                            .frame(width: 34, height: 34)
                            .background {
                                if isSelected {
                                    Circle().fill(theme.accent)
                                } else if isToday {
                                    Circle().stroke(theme.accent, lineWidth: 1.5)
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                }
            }
            .offset(x: swipeOffsetX)
            .clipped()
        }
        .padding(.horizontal, showCardBackground ? GokeSpacing.md : 0)
        .padding(.vertical, showCardBackground ? GokeSpacing.md : GokeSpacing.xs)
        .background {
            if showCardBackground {
                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                    .fill(theme.surface)
            }
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 24)
                .onChanged { value in
                    let dx = value.translation.width
                    let dy = value.translation.height
                    guard abs(dx) > abs(dy) * 1.3 else { return }
                    swipeOffsetX = max(-72, min(72, dx * 0.45))
                }
                .onEnded { value in
                    let dx = value.translation.width
                    let dy = value.translation.height
                    let isHorizontal = abs(dx) > abs(dy) * 1.3
                    let shouldSwitch = isHorizontal && (abs(dx) > 48 || abs(value.predictedEndTranslation.width) > 110)
                    if shouldSwitch {
                        let delta = dx < 0 ? 1 : -1
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            displayedMonthStart = displayedMonthStart.addingMonths(delta).withDayOfMonth(1)
                            swipeOffsetX = 0
                        }
                    } else {
                        withAnimation(GokeMotion.interactiveDrag(policy: performance)) {
                            swipeOffsetX = 0
                        }
                    }
                }
        )
    }
}

