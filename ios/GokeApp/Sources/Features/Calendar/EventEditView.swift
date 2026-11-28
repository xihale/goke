import Foundation
import GokeCore
import SwiftUI
import GokeShared

/// Native SwiftUI `Form` editor for creating and updating `Event` records (normal events, exams, countdowns).
///
/// Zero ViewModels: reads existing records from `AppContainer` and persists all changes atomically
/// through `ActionDispatcher.commit(_:)`.
public struct EventEditView: View {
    public let eventId: String?
    public let initialDate: LocalDate?
    public let isSheet: Bool

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    @State private var didLoadInitialState: Bool = false
    @State private var title: String = ""
    @State private var kind: EventKind = .NORMAL
    @State private var allDay: Bool = false
    @State private var startDate: Date = Date()
    @State private var endDate: Date = Date().addingTimeInterval(3600)
    @State private var location: String = ""
    @State private var category: String = ""
    @State private var colorIndex: Int = 4
    @State private var remindMinutesList: [Int] = [15]
    @State private var note: String = ""

    // Recurrence state
    @State private var repeatEnabled: Bool = false
    @State private var repeatFreq: Freq = .WEEKLY
    @State private var repeatInterval: Int = 1
    @State private var repeatWeekdays: Set<Int> = []
    @State private var repeatEndMode: RepeatEndMode = .never
    @State private var repeatUntilDate: Date = Date().addingTimeInterval(86_400 * 30)
    @State private var repeatCount: Int = 10

    @State private var showDeleteConfirm: Bool = false
    @FocusState private var isTitleFocused: Bool

    private enum RepeatEndMode: String, CaseIterable, Identifiable {
        case never = "永不"
        case until = "指定日期"
        case count = "按次数"
        var id: String { rawValue }
    }

    private static let categoryPresets = ["学习", "考试", "会议", "社团", "生活"]
    private static let weekdayLabels = ["一", "二", "三", "四", "五", "六", "日"]

    public init(
        eventId: String? = nil,
        initialDate: LocalDate? = nil,
        isSheet: Bool = false
    ) {
        self.eventId = eventId
        self.initialDate = initialDate
        self.isSheet = isSheet
    }

    private var existingEvent: Event? {
        guard let eventId else { return nil }
        return container.liveSnapshot.events.first(where: { $0.id == eventId })
    }

    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    public var body: some View {
        if isSheet {
            NavigationStack {
                editorForm
            }
            .gokePaperSheet(theme, detents: [.large])
        } else {
            editorForm
                #if os(iOS)
                .toolbar(.hidden, for: .tabBar)
                #endif
        }
    }

    private var editorForm: some View {
        Form {
            Section("基本信息") {
                TextField("日程标题", text: $title)
                    .font(GokeTypography.bodyLarge)
                    .foregroundStyle(theme.ink)
                    .focused($isTitleFocused)

                GokeSegmentedPill(
                    options: [
                        (EventKind.NORMAL, "日程"),
                        (EventKind.EXAM, "考试"),
                        (EventKind.COUNTDOWN, "倒数日"),
                    ],
                    selection: $kind,
                    height: 40
                )
                .padding(.vertical, 2)
                .onChange(of: kind) { _, newKind in
                    if newKind == .COUNTDOWN {
                        allDay = true
                    }
                }

                TextField(kind == .EXAM ? "考场 / 座位号" : "地点", text: $location)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)

                HStack(spacing: GokeSpacing.sm) {
                    TextField("分类", text: $category)
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.ink)

                    Menu {
                        ForEach(Self.categoryPresets, id: \.self) { preset in
                            Button {
                                category = preset
                            } label: {
                                if category == preset {
                                    Label(preset, systemImage: "checkmark")
                                } else {
                                    Text(preset)
                                }
                            }
                        }
                        if !category.isEmpty {
                            Divider()
                            Button("清除分类", role: .destructive) {
                                category = ""
                            }
                        }
                    } label: {
                        Image(systemName: "tag")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(theme.muted)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintCompact)
                    .accessibilityLabel("选择常用分类")
                }
            }
            .listRowBackground(theme.surface)

            Section("时间与重复") {
                if kind != .COUNTDOWN {
                    Toggle("全天", isOn: $allDay)
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.ink)
                }

                DatePicker(
                    kind == .COUNTDOWN ? "目标日期" : "开始",
                    selection: $startDate,
                    displayedComponents: (allDay || kind == .COUNTDOWN) ? [.date] : [.date, .hourAndMinute]
                )
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.ink)
                .onChange(of: startDate) { _, newStart in
                    if endDate < newStart {
                        endDate = (allDay || kind == .COUNTDOWN) ? newStart : newStart.addingTimeInterval(3600)
                    }
                }

                if kind != .COUNTDOWN {
                    DatePicker(
                        "结束",
                        selection: $endDate,
                        in: startDate...,
                        displayedComponents: allDay ? [.date] : [.date, .hourAndMinute]
                    )
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                }

                if !allDay && kind != .COUNTDOWN {
                    LabeledContent("时长") {
                        HStack(spacing: 6) {
                            durationButton(minutes: 30, label: "30分")
                            durationButton(minutes: 60, label: "1小时")
                            durationButton(minutes: 120, label: "2小时")
                        }
                    }
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                }

                Toggle("重复", isOn: $repeatEnabled)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)

                if repeatEnabled {
                    Picker("频率", selection: $repeatFreq) {
                        Text("每天").tag(Freq.DAILY)
                        Text("每周").tag(Freq.WEEKLY)
                        Text("每月").tag(Freq.MONTHLY)
                        Text("每年").tag(Freq.YEARLY)
                    }
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)

                    GokeMintStepper(
                        "间隔",
                        valueText: "每 \(repeatInterval) \(freqUnit(repeatFreq))",
                        value: $repeatInterval,
                        in: 1...30
                    )

                    if repeatFreq == .WEEKLY {
                        VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                            Text("星期")
                                .font(GokeTypography.labelMedium)
                                .foregroundStyle(theme.muted)
                            HStack(spacing: 6) {
                                ForEach(1...7, id: \.self) { dow in
                                    let active = repeatWeekdays.contains(dow)
                                    Button {
                                        withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                                            if active {
                                                repeatWeekdays.remove(dow)
                                            } else {
                                                repeatWeekdays.insert(dow)
                                            }
                                        }
                                    } label: {
                                        Text(Self.weekdayLabels[dow - 1])
                                            .font(GokeTypography.labelMedium.weight(active ? .semibold : .regular))
                                            .foregroundStyle(active ? theme.onAccent : theme.muted)
                                            .frame(width: 32, height: 32)
                                            .background {
                                                Circle()
                                                    .fill(active ? theme.accent : theme.surfaceHighest)
                                            }
                                            .frame(maxWidth: .infinity, minHeight: 44)
                                            .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.mintCompact)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }

                    Picker("结束", selection: $repeatEndMode) {
                        ForEach(RepeatEndMode.allCases) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)

                    switch repeatEndMode {
                    case .never:
                        EmptyView()
                    case .until:
                        DatePicker("截止日期", selection: $repeatUntilDate, displayedComponents: [.date])
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    case .count:
                        GokeMintStepper(
                            "次数",
                            valueText: "共 \(repeatCount) 次",
                            value: $repeatCount,
                            in: 1...200
                        )
                    }
                }

                GokeMultiReminderPicker("提醒", selection: $remindMinutesList)
            }
            .listRowBackground(theme.surface)

            Section("配色与备注") {
                HStack(spacing: 0) {
                    ForEach(theme.courseColors, id: \.index) { pair in
                        let isSelected = colorIndex == pair.index
                        Button {
                            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                                colorIndex = pair.index
                            }
                        } label: {
                            ZStack {
                                Circle()
                                    .fill(pair.background)
                                    .frame(width: 32, height: 32)
                                    .overlay {
                                        Circle()
                                            .strokeBorder(
                                                isSelected ? pair.foreground : theme.outlineVariant.opacity(0.6),
                                                lineWidth: isSelected ? 2 : 0.75
                                            )
                                    }
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(pair.foreground)
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("配色 \(pair.name)")
                    }
                }

                TextField("备注", text: $note, axis: .vertical)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                    .lineLimit(3...8)
            }
            .listRowBackground(theme.surface)

            if existingEvent != nil {
                Section {
                    Button("删除", role: .destructive) {
                        showDeleteConfirm = true
                    }
                    .font(GokeTypography.bodyMedium)
                }
                .listRowBackground(theme.surface)
            }
        }
        .tint(theme.accent)
        .gokePaperListStyle()
        .navigationTitle(existingEvent == nil ? "新建日程" : "编辑日程")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isSheet {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
            }

            ToolbarItem(placement: .confirmationAction) {
                Button("保存") {
                    saveEvent()
                }
                .fontWeight(.semibold)
                .disabled(!canSave)
            }
        }
        .confirmationDialog("删除此日程？", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("移入回收站", role: .destructive) {
                deleteEvent()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("可在回收站恢复")
        }
        .onAppear {
            loadIfNeeded()
        }
    }

    private func durationButton(minutes: Int, label: String) -> some View {
        let currentMins = Int(round(endDate.timeIntervalSince(startDate) / 60))
        let active = currentMins == minutes
        return Button {
            withAnimation(GokeMotion.press(reduceMotion: simplifyMotion)) {
                endDate = startDate.addingTimeInterval(TimeInterval(minutes * 60))
            }
        } label: {
            Text(label)
                .font(GokeTypography.labelMedium.weight(active ? .semibold : .regular))
                .monospacedDigit()
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(active ? theme.accentContainer : theme.surfaceHighest, in: Capsule())
                .foregroundStyle(active ? theme.onAccentContainer : theme.muted)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
    }

    // MARK: - Persistence

    private func loadIfNeeded() {
        guard !didLoadInitialState else { return }
        didLoadInitialState = true

        if let existing = existingEvent {
            title = existing.title
            kind = existing.kind
            allDay = existing.allDay
            startDate = existing.start.toDate()
            endDate = existing.end.toDate()
            location = existing.location
            category = existing.category
            colorIndex = existing.colorIndex
            remindMinutesList = existing.effectiveRemindMinutes
            note = existing.note
            if let rule = existing.recurrence {
                repeatEnabled = true
                repeatFreq = rule.freq
                repeatInterval = max(1, rule.interval)
                repeatWeekdays = rule.byDays
                if let until = rule.until {
                    repeatEndMode = .until
                    repeatUntilDate = until.atStartOfDay().toDate()
                } else if let count = rule.count {
                    repeatEndMode = .count
                    repeatCount = max(1, count)
                } else {
                    repeatEndMode = .never
                }
            }
        } else {
            let baseDate = initialDate ?? LocalDate.today()
            let nowTime = LocalDateTime.now().time
            let nextHour = min(22, max(8, nowTime.hour + 1))
            let startLDT = baseDate.atTime(hour: nextHour, minute: 0)
            let endLDT = startLDT.addingHours(1)
            startDate = startLDT.toDate()
            endDate = endLDT.toDate()
            repeatWeekdays = [baseDate.dayOfWeek]
            isTitleFocused = true
        }
    }

    private func saveEvent() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return }

        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let rawStart = LocalDateTime.fromDate(startDate)
        let rawEnd = LocalDateTime.fromDate(endDate)

        let finalStart: LocalDateTime
        let finalEnd: LocalDateTime
        if allDay || kind == .COUNTDOWN {
            finalStart = rawStart.date.atStartOfDay()
            let endDay = kind == .COUNTDOWN ? rawStart.date : max(rawStart.date, rawEnd.date)
            finalEnd = endDay.atTime(.endOfDay)
        } else {
            finalStart = LocalDateTime(
                date: rawStart.date,
                time: LocalTime(hour: rawStart.hour, minute: rawStart.minute)
            )
            let candidateEnd = LocalDateTime(
                date: rawEnd.date,
                time: LocalTime(hour: rawEnd.hour, minute: rawEnd.minute)
            )
            finalEnd = candidateEnd > finalStart ? candidateEnd : finalStart.addingMinutes(30)
        }

        let recurrence: Recurrence?
        if repeatEnabled {
            let until: LocalDate? = repeatEndMode == .until ? LocalDateTime.fromDate(repeatUntilDate).date : nil
            let count: Int? = repeatEndMode == .count ? max(1, repeatCount) : nil
            let byDays: Set<Int> = repeatFreq == .WEEKLY
                ? (repeatWeekdays.isEmpty ? [finalStart.dayOfWeek] : repeatWeekdays)
                : []
            recurrence = Recurrence(
                freq: repeatFreq,
                interval: max(1, repeatInterval),
                byDays: byDays,
                until: until,
                count: count,
                exDates: existingEvent?.recurrence?.exDates ?? [],
                byMonths: existingEvent?.recurrence?.byMonths ?? []
            )
        } else {
            recurrence = nil
        }

        let trimmedLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedCategory = category.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedReminds = Reminders.normalize(remindMinutesList)

        let changeSet: ChangeSet
        let toastLabel: String
        if let existing = existingEvent {
            var updated = existing
            updated.title = trimmedTitle
            updated.start = finalStart
            updated.end = finalEnd
            updated.allDay = allDay || kind == .COUNTDOWN
            updated.location = trimmedLocation
            updated.note = trimmedNote
            updated.colorIndex = colorIndex
            updated.category = trimmedCategory
            updated.remindMinutes = normalizedReminds.first
            updated.remindMinutesList = normalizedReminds
            updated.recurrence = recurrence
            updated.kind = kind
            updated.updatedAt = nowMillis
            changeSet = ChangeSet(
                description: "更新日程「\(trimmedTitle)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.modify(before: .event(existing), after: .event(updated))]
            )
            toastLabel = "已更新「\(trimmedTitle)」"
        } else {
            let created = Event(
                title: trimmedTitle,
                start: finalStart,
                end: finalEnd,
                allDay: allDay || kind == .COUNTDOWN,
                location: trimmedLocation,
                note: trimmedNote,
                colorIndex: colorIndex,
                category: trimmedCategory,
                remindMinutes: normalizedReminds.first,
                recurrence: recurrence,
                kind: kind,
                source: .MANUAL,
                updatedAt: nowMillis,
                remindMinutesList: normalizedReminds
            )
            changeSet = ChangeSet(
                description: "添加日程「\(trimmedTitle)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.add(after: .event(created))]
            )
            toastLabel = "已添加「\(trimmedTitle)」"
        }

        Task {
            let ok = await actions.commit(changeSet, label: toastLabel)
            if ok {
                dismiss()
            }
        }
    }

    private func deleteEvent() {
        guard let existing = existingEvent else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs = ChangeSet(
            description: "删除日程「\(existing.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.remove(before: .event(existing))]
        )
        Task {
            let ok = await actions.commit(cs, label: "已删除「\(existing.title)」")
            if ok {
                dismiss()
            }
        }
    }

    private func freqUnit(_ freq: Freq) -> String {
        switch freq {
        case .DAILY: "天"
        case .WEEKLY: "周"
        case .MONTHLY: "月"
        case .YEARLY: "年"
        }
    }
}
