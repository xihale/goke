import GokeCore
import GokeShared
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Shared Task Toggle Logic (Android Rows.kt / TodayScreen.kt Parity)

enum GokeTaskToggle {
    /// Mirrors Android `isClearingTodayTasks(snap, setOf(task.id), now)`:
    /// returns `true` if completing `completingIds` finishes the last remaining open task for today (including overdue).
    static func isClearingTodayTasks(
        snapshot: AppSnapshot,
        completingIds: Set<String>,
        now: LocalDateTime,
        defaultBlockMinutes: Int = 30
    ) -> Bool {
        let live = snapshot.live()
        let today = now.date
        let items = AgendaEngine.forDay(
            snapshot: live,
            date: today,
            defaultBlockMinutes: defaultBlockMinutes
        )
        var openIds = Set<String>()
        for item in items {
            switch item {
            case .task(let t, _, _) where !t.done:
                openIds.insert(t.id)
            case .block(let t, _, _) where !t.done:
                openIds.insert(t.id)
            default:
                break
            }
        }
        for t in TaskLogic.filter(live.tasks, smartList: .OVERDUE, now: now) where !t.done {
            openIds.insert(t.id)
        }
        return !openIds.isEmpty && openIds.isSubset(of: completingIds)
    }

    @MainActor
    static func toggle(
        task: GokeTask,
        done: Bool,
        snapshot: AppSnapshot,
        now: LocalDateTime,
        defaultBlockMinutes: Int,
        actions: ActionDispatcher
    ) async {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        if done {
            let clearedToday = isClearingTodayTasks(
                snapshot: snapshot,
                completingIds: [task.id],
                now: now,
                defaultBlockMinutes: defaultBlockMinutes
            )
            let result = TaskLogic.complete(task, nowMillis: nowMillis, now: now)
            var changes: [Change] = [.modify(before: .task(task), after: .task(result.finished))]
            if let next = result.next {
                changes.append(.add(after: .task(next)))
            }
            let desc = clearedToday ? "今日待办已全部完成！" : "已完成「\(task.title)」"
            let cs = ChangeSet(
                description: desc,
                source: .MANUAL,
                createdAt: nowMillis,
                changes: changes
            )
            _ = await actions.commit(cs, label: desc)
        } else {
            var updated = task
            updated.done = false
            updated.doneAt = nil
            updated.updatedAt = nowMillis
            let desc = "恢复待办「\(task.title)」"
            let cs = ChangeSet(
                description: desc,
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.modify(before: .task(task), after: .task(updated))]
            )
            _ = await actions.commit(cs, label: desc)
        }
    }
}

// MARK: - Shared Agenda & Task Row Views (Android Rows.kt 1:1 Parity)

/// Unified agenda row matching Android `Rows.kt:AgendaRow`.
/// Renders botanical pastel `GokeAgendaRowShell` blocks (with horizontal `CourseProgressBackground` wipe)
/// for courses/events/time blocks, and frameless paper `GokeTaskRow`s for due tasks.
struct GokeAgendaRow: View {
    let item: AgendaItem
    let now: LocalDateTime
    let showTeacher: Bool
    let listColors: [String: Int]
    let onOpenCourse: (String) -> Void
    let onOpenEvent: (String) -> Void
    let onOpenTask: (String) -> Void
    let onToggleTask: (GokeTask, Bool) -> Void

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        switch item {
        case .course(let occ):
            let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: now)
            let rawRoom = TodayFormatters.smartFoldRoom(occ.room)
            let roomText: String = if occ.course.isExam {
                JxfwSyncMapper.formatExamLocation(
                    location: rawRoom,
                    note: occ.course.note,
                    seatSuffix: "号"
                ) ?? rawRoom
            } else {
                rawRoom
            }
            let examDisplay: JxfwSyncMapper.ExamDisplay? = occ.course.isExam
                ? JxfwSyncMapper.splitExamDisplay(occ.course.name, fallbackBadge: "考试")
                : nil
            let tag: String? = {
                switch (examDisplay, occ.rescheduled) {
                case (let ed?, true): return "\(ed.badgeFull) · 调课"
                case (let ed?, false): return ed.badgeFull
                case (nil, true): return "调课"
                case (nil, false): return nil
                }
            }()
            let subtitle = [roomText, showTeacher ? occ.course.teacher : ""]
                .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .joined(separator: " · ")
            let pair = theme.courseColor(index: occ.course.colorIndex)

            GokeAgendaRowShell(
                time: occ.start.time.description,
                endTime: occ.end.time.description,
                bg: pair.background,
                fg: pair.foreground,
                title: examDisplay?.title ?? occ.course.name,
                subtitle: subtitle,
                phase: phase,
                tag: tag,
                onTap: { onOpenCourse(occ.course.id) }
            )

        case .event(let event, let start, let end, let allDay):
            let phase: TimePhase = allDay
                ? .upcoming
                : CourseProgress.phase(start: start, end: end, now: now)
            let timeLabel = allDay ? "全天" : start.time.description
            let endLabel = (!allDay && end > start) ? end.time.description : nil
            let examDisplay: JxfwSyncMapper.ExamDisplay? = event.kind == .EXAM
                ? JxfwSyncMapper.splitExamDisplay(event.title, fallbackBadge: "考试")
                : nil
            let kindTag: String? = switch event.kind {
            case .EXAM: examDisplay?.badgeFull ?? "考试"
            case .COUNTDOWN: "倒数日"
            case .NORMAL: nil
            }
            let pair = theme.courseColor(index: event.colorIndex)

            GokeAgendaRowShell(
                time: timeLabel,
                endTime: endLabel,
                bg: pair.background,
                fg: pair.foreground,
                title: examDisplay?.title ?? event.title,
                subtitle: TodayFormatters.smartFoldRoom(event.location),
                phase: phase,
                tag: kindTag,
                onTap: { onOpenEvent(event.id) }
            )

        case .task(let task, let due, let hasTime):
            let listColor: Color? = task.listId
                .flatMap { listColors[$0] }
                .map { theme.courseColor(index: $0).foreground }
            GokeTaskRow(
                task: task,
                subtitle: hasTime ? due.time.description : nil,
                listColor: listColor,
                onToggle: { onToggleTask(task, !task.done) },
                onOpen: { onOpenTask(task.id) }
            )

        case .block(let task, let start, let end):
            let colorIndex = task.listId.flatMap { listColors[$0] } ?? 1
            let pair = theme.courseColor(index: colorIndex)
            let phase: TimePhase = task.done
                ? .finished
                : CourseProgress.phase(start: start, end: end, now: now)
            let isExamBlock = task.extra["kind"] == EventKind.EXAM.rawValue
            let examDisplay: JxfwSyncMapper.ExamDisplay? = isExamBlock
                ? JxfwSyncMapper.splitExamDisplay(task.title, fallbackBadge: "考试")
                : nil
            let kindTag: String? = switch task.extra["kind"] {
            case EventKind.EXAM.rawValue: examDisplay?.badgeFull ?? "考试"
            case EventKind.COUNTDOWN.rawValue: "倒数日"
            default: nil
            }
            let rawLocation = task.extra["location"]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let location = TodayFormatters.smartFoldRoom(rawLocation)

            GokeAgendaRowShell(
                time: start.time.description,
                endTime: end > start ? end.time.description : nil,
                bg: pair.background,
                fg: pair.foreground,
                title: examDisplay?.title ?? task.title,
                subtitle: location,
                phase: phase,
                tag: kindTag,
                onTap: { onOpenTask(task.id) }
            )
        }
    }
}

/// Botanical pastel time-block row matching Android `Rows.kt:RowShell`.
/// Uses horizontal `CourseProgressBackground` desaturation wipe, elastic `minWidth: 48` time column,
/// and right-aligned trailing tag pill.
struct GokeAgendaRowShell: View {
    let time: String
    let endTime: String?
    let bg: Color
    let fg: Color
    let title: String
    let subtitle: String
    let phase: TimePhase
    let tag: String?
    let onTap: () -> Void

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        let textFg = CourseProgressStyle.foreground(activeFg: fg, phase: phase, theme: theme)
        Button(action: onTap) {
            HStack(alignment: .center, spacing: GokeSpacing.sm) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(time)
                        .font(.system(size: 14, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(textFg)
                        .lineLimit(1)
                    if let endTime {
                        Text(endTime)
                            .font(GokeTypography.labelSmall)
                            .monospacedDigit()
                            .foregroundStyle(textFg.opacity(0.78))
                            .lineLimit(1)
                    }
                }
                .frame(minWidth: 48, alignment: .leading)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(GokeTypography.titleMedium)
                        .foregroundStyle(textFg)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(textFg.opacity(0.8))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if let tag, !tag.isEmpty {
                    Text(tag)
                        .font(GokeTypography.labelMedium)
                        .monospacedDigit()
                        .foregroundStyle(textFg)
                        .lineLimit(1)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 3)
                        .background(
                            Capsule()
                                .fill(textFg.opacity(0.15))
                        )
                }
            }
            .padding(.horizontal, GokeSpacing.md)
            .padding(.vertical, GokeSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                CourseProgressBackground(activeBg: bg, phase: phase, axis: .horizontal)
            }
            .clipShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous))
        }
        .buttonStyle(.mintRow)
    }
}

/// Frameless paper task row matching Android `Rows.kt:TaskRow`.
struct GokeTaskRow: View {
    let task: GokeTask
    let subtitle: String?
    var subtitleColor: Color? = nil
    var listColor: Color? = nil
    let onToggle: () -> Void
    let onOpen: () -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance

    var body: some View {
        let rowAlpha: Double = task.done ? 0.45 : 1.0
        let resolvedSubtitleColor = (subtitleColor ?? theme.muted).opacity(rowAlpha)
        let simplify = reduceMotion || performance.shouldSimplifyMotion

        HStack(alignment: .center, spacing: GokeSpacing.sm) {
            GokeCheckBox(
                isChecked: task.done,
                priority: task.priority,
                listColor: listColor,
                accessibilityLabel: task.done ? "标为未完成：\(task.title)" : "完成：\(task.title)",
                onToggle: onToggle
            )

            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 2) {
                    let examDisplay: JxfwSyncMapper.ExamDisplay? = task.isExam
                        ? JxfwSyncMapper.splitExamDisplay(task.title, fallbackBadge: "考试")
                        : nil
                    if let examDisplay {
                        HStack(alignment: .center, spacing: 6) {
                            Text(examDisplay.title)
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle((task.done ? theme.muted : theme.ink).opacity(rowAlpha))
                                .strikethrough(task.done, color: theme.muted)
                                .lineLimit(2)
                                .truncationMode(.tail)
                            Text(examDisplay.badgeFull)
                                .font(GokeTypography.labelSmall.weight(.semibold))
                                .foregroundStyle(theme.onTertiaryContainer.opacity(rowAlpha))
                                .lineLimit(1)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(
                                    Capsule()
                                        .fill(theme.tertiaryContainer.opacity(rowAlpha))
                                )
                        }
                    } else {
                        Text(task.title)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle((task.done ? theme.muted : theme.ink).opacity(rowAlpha))
                            .strikethrough(task.done, color: theme.muted)
                            .lineLimit(2)
                            .truncationMode(.tail)
                    }

                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(resolvedSubtitleColor)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)
        }
        .padding(.leading, 2)
        .padding(.trailing, GokeSpacing.sm)
        .padding(.vertical, 2)
        .frame(minHeight: 50)
        .animation(GokeMotion.press(reduceMotion: simplify), value: task.done)
    }
}

/// Legacy Event detail sheet matching Android `Rows.kt:EventDetailSheet` with `"转为待办"` & `"删除"`.
struct GokeEventDetailSheet: View {
    let event: Event
    let snapshot: AppSnapshot
    let onDismiss: () -> Void
    let onOpenConvertedTask: (String) -> Void

    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @State private var isWorking: Bool = false

    private var timeText: String {
        let startDay = event.start.date
        let endDay = event.end.date
        let startLabel = "\(startDay.month)月\(startDay.day)日 \(TodayFormatters.weekdayLabel(startDay.dayOfWeek))"
        let endLabel = "\(endDay.month)月\(endDay.day)日 \(TodayFormatters.weekdayLabel(endDay.dayOfWeek))"
        if event.allDay {
            let range = startDay == endDay ? startLabel : "\(startLabel) – \(endLabel)"
            return "\(range) · 全天"
        } else if startDay == endDay {
            return "\(startLabel) \(event.start.time.description)–\(event.end.time.description)"
        } else {
            return "\(startLabel) \(event.start.time.description) – \(endLabel) \(event.end.time.description)"
        }
    }

    private var kindLabel: String? {
        switch event.kind {
        case .EXAM: "考试"
        case .COUNTDOWN: "倒数日"
        case .NORMAL: nil
        }
    }

    private var recurrenceLabel: String? {
        guard let r = event.recurrence else { return nil }
        let unit: String = switch r.freq {
        case .DAILY: "天"
        case .WEEKLY: "周"
        case .MONTHLY: "月"
        case .YEARLY: "年"
        }
        let base = r.interval <= 1 ? "每\(unit)" : "每 \(r.interval) \(unit)"
        if let until = r.until {
            return "\(base) · 截止 \(until.year)-\(String(format: "%02d", until.month))-\(String(format: "%02d", until.day))"
        }
        return base
    }

    var body: some View {
        let pair = theme.courseColor(index: event.colorIndex)
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                Capsule()
                    .fill(pair.foreground)
                    .frame(width: 4, height: 22)
                Text(event.title)
                    .font(GokeTypography.titleLarge)
                    .foregroundStyle(theme.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let kindLabel {
                    Text(kindLabel)
                        .font(GokeTypography.labelSmall.weight(.semibold))
                        .foregroundStyle(pair.foreground)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(pair.background))
                }
                Button {
                    onDismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(theme.muted.opacity(0.85))
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.mintCompact)
                .padding(.trailing, -10)
                .padding(.vertical, -8)
                .accessibilityLabel("关闭")
            }

            Text(timeText)
                .font(GokeTypography.bodyMedium)
                .monospacedDigit()
                .foregroundStyle(theme.muted)
                .padding(.leading, 14)

            if !event.location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                infoRow(label: "地点", value: event.location.trimmingCharacters(in: .whitespacesAndNewlines))
            }

            if let recurrenceLabel {
                infoRow(label: "重复", value: recurrenceLabel)
            }

            let trimmedNote = event.note.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedNote.isEmpty {
                Text(trimmedNote)
                    .font(.caption)
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                            .fill(theme.surfaceHighest.opacity(0.6))
                    )
            }

            Spacer(minLength: 8)

            HStack(spacing: GokeSpacing.sm) {
                Button {
                    guard !isWorking else { return }
                    isWorking = true
                    Task {
                        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
                        let desc = "删除「\(event.title)」"
                        let cs = ChangeSet(
                            description: desc,
                            source: .MANUAL,
                            createdAt: nowMillis,
                            changes: [.remove(before: .event(event))]
                        )
                        let ok = await actions.commit(cs, label: desc)
                        if ok {
                            onDismiss()
                        } else {
                            isWorking = false
                        }
                    }
                } label: {
                    Text("删除")
                        .font(GokeTypography.labelLarge.weight(.medium))
                        .foregroundStyle(theme.error)
                        .frame(minHeight: 48)
                        .padding(.horizontal, 14)
                }
                .disabled(isWorking)
                .buttonStyle(.mintCompact)

                Button {
                    guard !isWorking else { return }
                    isWorking = true
                    Task {
                        let newId = UUID().uuidString.lowercased()
                        let nextOrder = (snapshot.tasks.map(\.sortOrder).max() ?? 0) + 1
                        let converted = Self.convertEventToTask(event, id: newId, sortOrder: nextOrder)
                        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
                        let cs = ChangeSet(
                            description: "转为待办",
                            source: .MANUAL,
                            createdAt: nowMillis,
                            changes: [
                                .add(after: .task(converted)),
                                .remove(before: .event(event))
                            ]
                        )
                        let ok = await actions.commit(cs, label: "已转为待办", undoable: false)
                        if ok {
                            onOpenConvertedTask(newId)
                            onDismiss()
                        } else {
                            isWorking = false
                        }
                    }
                } label: {
                    Text("转为待办")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                        .foregroundStyle(theme.onAccent)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(
                            Capsule()
                                .fill(theme.accent)
                        )
                }
                .disabled(isWorking)
                .buttonStyle(.mintRow)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 24)
        .background(theme.paper.ignoresSafeArea())
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundStyle(theme.muted)
                .frame(width: 40, alignment: .leading)
            Text(value)
                .font(GokeTypography.bodyMedium)
                .monospacedDigit()
                .foregroundStyle(theme.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.leading, 14)
    }

    /// Mirrors Android `Unify.eventToTask(e, id, sortOrder)`.
    private static func convertEventToTask(_ e: Event, id: String, sortOrder: Int) -> GokeTask {
        let timed = !e.allDay
        let location = e.location.trimmingCharacters(in: .whitespacesAndNewlines)
        let rawMinutes = Int(e.start.secondsUntil(e.end) / 60)
        let minutes = min(24 * 60, max(15, rawMinutes))

        var tags: [String] = []
        switch e.kind {
        case .EXAM: tags.append("考试")
        case .COUNTDOWN: tags.append("倒数日")
        case .NORMAL: break
        }
        let cat = e.category.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cat.isEmpty && !tags.contains(cat) {
            tags.append(cat)
        }

        var extra: [String: String] = [:]
        if e.kind == .EXAM || e.kind == .COUNTDOWN {
            extra["kind"] = e.kind.rawValue
            extra["checkable"] = "false"
        }
        if !location.isEmpty {
            extra["location"] = location
        }

        let reminders = e.effectiveRemindMinutes
        return GokeTask(
            id: id,
            title: e.title,
            note: e.note.trimmingCharacters(in: .whitespacesAndNewlines),
            listId: nil,
            tags: tags,
            priority: 0,
            startDate: nil,
            due: timed ? e.end : e.start.date.atTime(LocalTime(hour: 23, minute: 59)),
            dueHasTime: timed,
            estimateMinutes: nil,
            done: false,
            doneAt: nil,
            dropped: false,
            parentId: nil,
            repeat: e.recurrence,
            remindMinutes: reminders.first,
            remindPersistent: false,
            courseId: nil,
            dueBeforeNextClass: false,
            blockStart: timed ? e.start : nil,
            blockMinutes: timed ? minutes : nil,
            sortOrder: sortOrder,
            source: e.source,
            extra: extra,
            attachments: [],
            updatedAt: Int64(Date().timeIntervalSince1970 * 1000),
            deletedAt: nil,
            userId: e.userId,
            remindMinutesList: reminders
        )
    }
}

// MARK: - Today Precomputed Snapshot (Zero ViewModels)

private struct TodayDisplaySnapshot: Equatable, Sendable {
    let date: LocalDate
    let actualToday: LocalDate
    let dayOffset: Int
    let now: LocalDateTime
    let currentWeekLabel: String
    let relativeLabel: String?
    let courseCount: Int
    let eventCount: Int
    let taskTotal: Int
    let taskDone: Int
    let taskProgress: Double
    let allDone: Bool
    let dayQuiet: Bool
    let dayEmpty: Bool
    let nextToday: AgendaItem?
    let nextUp: NextUp?
    let overdue: [GokeTask]
    let overdueEffectiveDues: [String: LocalDateTime]
    let pendingItems: [AgendaItem]
    let doneItems: [AgendaItem]
    let listColors: [String: Int]

    var isToday: Bool { date == actualToday }

    var headerTitle: String {
        let wd = TodayFormatters.weekdayLabel(date.dayOfWeek)
        if date.year != actualToday.year {
            return "\(date.year)年\(date.month)月\(date.day)日 \(wd)"
        }
        return "\(date.month)月\(date.day)日 \(wd)"
    }

    var headerSubtitle: String {
        [relativeLabel, currentWeekLabel.isEmpty ? nil : currentWeekLabel]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    var summaryText: String {
        if allDone {
            return "都做完了"
        }
        let classPart: String = if courseCount == 0 {
            isToday ? "今日无课" : "当日无课"
        } else {
            "\(courseCount) 节课"
        }
        let remaining = max(0, taskTotal - taskDone)
        if remaining > 0 {
            return "\(classPart) · 还剩 \(remaining) 项"
        }
        return classPart
    }

    var nextLine: String? {
        guard isToday else { return nil }
        if let nextToday {
            switch nextToday {
            case .course(let occ):
                let kindPrefix = (occ.course.isExam && !occ.course.name.contains("考试")) ? "考试 · " : ""
                let room = TodayFormatters.smartFoldRoom(occ.room)
                let roomSuffix = room.isEmpty ? "" : " · \(room)"
                return "\(kindPrefix)下一节 · \(occ.start.time.description)–\(occ.end.time.description) \(occ.course.name)\(roomSuffix)"

            case .event(let event, let start, let end, let allDay):
                let kindPrefix = event.kind == .EXAM ? "考试 · " : ""
                let loc = TodayFormatters.smartFoldRoom(event.location)
                let locSuffix = loc.isEmpty ? "" : " · \(loc)"
                let timeText: String = if !allDay && end > start {
                    "\(start.time.description)–\(end.time.description)"
                } else if allDay {
                    "全天"
                } else {
                    start.time.description
                }
                return "下一项 · \(kindPrefix)\(timeText) \(event.title)\(locSuffix)"

            case .block(let task, let start, let end):
                let isExam = task.extra["kind"] == EventKind.EXAM.rawValue
                let kindPrefix = (isExam && !task.title.contains("考试")) ? "考试 · " : ""
                let rawLoc = task.extra["location"]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let loc = TodayFormatters.smartFoldRoom(rawLoc)
                let locSuffix = loc.isEmpty ? "" : " · \(loc)"
                let timeText = end > start
                    ? "\(start.time.description)–\(end.time.description)"
                    : start.time.description
                return "下一项 · \(kindPrefix)\(timeText) \(task.title)\(locSuffix)"

            case .task:
                break
            }
        }
        if let occ = nextUp?.occurrence {
            let kindPrefix = (occ.course.isExam && !occ.course.name.contains("考试")) ? "考试 · " : ""
            let dayText = TodayFormatters.shortRelativeDay(from: actualToday, to: occ.date)
            let room = TodayFormatters.smartFoldRoom(occ.room)
            let roomSuffix = room.isEmpty ? "" : " · \(room)"
            return "\(kindPrefix)下一节 · \(dayText) \(occ.start.time.description)–\(occ.end.time.description) \(occ.course.name)\(roomSuffix)"
        }
        return nil
    }

    var dueBeforeClassLine: String? {
        guard isToday, let up = nextUp, !up.tasks.isEmpty else { return nil }
        let matchesNext: Bool = {
            guard let nextToday else { return true }
            if case .course(let occ) = nextToday,
               occ.course.id == up.occurrence.course.id,
               occ.date == up.occurrence.date
            {
                return true
            }
            return up.occurrence.date == actualToday
        }()
        guard matchesNext else { return nil }
        let joined = up.tasks.map(\.title).joined(separator: "、")
        return "课前要交：\(joined)"
    }

    static func build(
        liveSnapshot live: AppSnapshot,
        defaultBlockMinutes: Int,
        now: LocalDateTime,
        dayOffset: Int
    ) -> TodayDisplaySnapshot {
        let actualToday = now.date
        let targetDate = actualToday.addingDays(dayOffset)
        let isToday = targetDate == actualToday

        let semester = live.currentSemester
        let weekLabel: String = {
            guard let semester else { return "" }
            let w = SemesterCalendar(semester: semester).weekOf(targetDate)
            if w < 1 { return "未开学" }
            if w > semester.totalWeeks { return "已结课" }
            return "第\(w)周"
        }()

        let relLabel: String? = switch dayOffset {
        case 0: nil
        case -1: "昨天"
        case 1: "明天"
        case ..<(-1): "\(-dayOffset) 天前"
        default: "\(dayOffset) 天后"
        }

        let listColors = Dictionary(uniqueKeysWithValues: live.lists.map { ($0.id, $0.colorIndex) })
        let items = AgendaEngine.forDay(
            snapshot: live,
            date: targetDate,
            defaultBlockMinutes: defaultBlockMinutes
        )

        var overdueEffectiveDues: [String: LocalDateTime] = [:]
        let overdue: [GokeTask]
        if isToday {
            let overdueCandidates = TaskLogic.topLevel(live.tasks).filter { task in
                TaskLogic.isOverdue(
                    task,
                    semester: semester,
                    courses: live.courses,
                    exceptions: live.exceptions,
                    now: now
                )
            }
            for task in overdueCandidates {
                if let resolvedDue = TaskLogic.effectiveDue(
                    task: task,
                    semester: semester,
                    courses: live.courses,
                    exceptions: live.exceptions,
                    now: now
                ) {
                    overdueEffectiveDues[task.id] = resolvedDue
                }
            }
            overdue = overdueCandidates.sorted { lhs, rhs in
                let ld = overdueEffectiveDues[lhs.id]
                let rd = overdueEffectiveDues[rhs.id]
                if (ld == nil) != (rd == nil) { return ld != nil }
                if let ld, let rd, ld != rd { return ld < rd }
                if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
                return lhs.sortOrder < rhs.sortOrder
            }
        } else {
            overdue = []
        }
        let overdueIds = Set(overdue.map(\.id))

        let timeline = items.filter { item in
            switch item {
            case .task(let t, _, _):
                return !overdueIds.contains(t.id)
            default:
                return true
            }
        }

        func isDoneItem(_ item: AgendaItem) -> Bool {
            switch item {
            case .task(let t, _, _): return t.done
            case .block(let t, _, _): return t.done
            default: return false
            }
        }

        let pendingItems = timeline.filter { !isDoneItem($0) }
        let doneItems = timeline.filter { isDoneItem($0) }

        var dayTaskIds = Set<String>()
        var doneTaskIds = Set<String>()
        var courseCount = 0
        var eventCount = 0
        var doneCount = 0

        for item in items {
            switch item {
            case .course(let occ):
                courseCount += 1
                if occ.end < now { doneCount += 1 }
            case .event(_, _, let end, let allDay):
                eventCount += 1
                if !allDay && end < now { doneCount += 1 }
            case .task(let t, _, _):
                dayTaskIds.insert(t.id)
                if t.done {
                    doneTaskIds.insert(t.id)
                    doneCount += 1
                }
            case .block(let t, _, let end):
                dayTaskIds.insert(t.id)
                if t.done { doneTaskIds.insert(t.id) }
                if t.done || end < now { doneCount += 1 }
            }
        }

        let taskTotal = dayTaskIds.union(overdueIds).count
        let taskDone = doneTaskIds.count
        let taskProgress = taskTotal == 0 ? 0.0 : Double(taskDone) / Double(taskTotal)

        let nextToday: AgendaItem? = if !isToday {
            nil
        } else {
            items.first { item in
                switch item {
                case .course(let occ): return occ.end > now
                case .event(_, _, let end, let allDay): return !allDay && end > now
                case .block(let t, _, let end): return !t.done && end > now
                case .task: return false
                }
            }
        }

        let nextUp = isToday ? NextUps.find(snapshot: live, now: now) : nil
        let allDone = !items.isEmpty && doneCount == items.count && overdue.isEmpty
        let dayEmpty = timeline.isEmpty && overdue.isEmpty
        let dayQuiet = courseCount == 0 && eventCount == 0 && taskTotal == 0

        return TodayDisplaySnapshot(
            date: targetDate,
            actualToday: actualToday,
            dayOffset: dayOffset,
            now: now,
            currentWeekLabel: weekLabel,
            relativeLabel: relLabel,
            courseCount: courseCount,
            eventCount: eventCount,
            taskTotal: taskTotal,
            taskDone: taskDone,
            taskProgress: taskProgress,
            allDone: allDone,
            dayQuiet: dayQuiet,
            dayEmpty: dayEmpty,
            nextToday: nextToday,
            nextUp: nextUp,
            overdue: overdue,
            overdueEffectiveDues: overdueEffectiveDues,
            pendingItems: pendingItems,
            doneItems: doneItems,
            listColors: listColors
        )
    }
}

private struct TodayRecomputeBaseKey: Equatable {
    let snapshotRevision: UInt64
    let defaultBlockMinutes: Int
    let minuteBucket: Int64
    let pageAnchorDate: LocalDate
}

private struct TodayRecomputeKey: Equatable {
    let base: TodayRecomputeBaseKey
    let pageOffset: Int
}

private struct TodayPagerReloadToken: Equatable {
    let base: TodayRecomputeBaseKey
    let expandedDoneDates: Set<LocalDate>
    let scrollToTopToken: Int
    let scrollToTopTargetOffset: Int
    let showTeacher: Bool
}

@MainActor
private final class TodayPageCacheBox {
    private var baseKey: TodayRecomputeBaseKey?
    private var pages: [Int: TodayDisplaySnapshot] = [:]

    func clear() {
        baseKey = nil
        pages.removeAll(keepingCapacity: true)
    }

    func display(
        for pageOffset: Int,
        actualDayOffset: Int,
        key: TodayRecomputeBaseKey,
        liveSnapshot: AppSnapshot,
        now: LocalDateTime
    ) -> TodayDisplaySnapshot {
        if baseKey != key {
            baseKey = key
            pages.removeAll(keepingCapacity: true)
        }
        if let cached = pages[pageOffset] {
            return cached
        }
        let built = TodayDisplaySnapshot.build(
            liveSnapshot: liveSnapshot,
            defaultBlockMinutes: key.defaultBlockMinutes,
            now: now,
            dayOffset: actualDayOffset
        )
        pages[pageOffset] = built
        return built
    }

    func prewarm(
        offsets: [Int],
        anchorDeltaFromToday: Int,
        key: TodayRecomputeBaseKey,
        liveSnapshot: AppSnapshot,
        now: LocalDateTime
    ) {
        if baseKey != key {
            baseKey = key
            pages.removeAll(keepingCapacity: true)
        }
        for offset in offsets where pages[offset] == nil {
            pages[offset] = TodayDisplaySnapshot.build(
                liveSnapshot: liveSnapshot,
                defaultBlockMinutes: key.defaultBlockMinutes,
                now: now,
                dayOffset: anchorDeltaFromToday + offset
            )
        }
        let keep = Set(offsets)
        if pages.count > keep.count + 4 {
            pages = pages.filter { keep.contains($0.key) }
        }
    }
}

// MARK: - TodayView (Android TodayScreen.kt 1:1 Parity)

/// Title-free "素纸 Goke" Today screen matching Android `TodayScreen.kt`.
/// Supports native paged horizontal day swipe (`TabView(.page)`), midnight rollover, flat summary card +
/// pulsing progress ring, overdue 1-row vs >=2 tidy banner, unified timeline,
/// and collapsible done section.
struct TodayView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    @State private var minuteBucket: Int64 = Int64(Date().timeIntervalSince1970 / 60)
    @State private var lastKnownToday: LocalDate = .today()
    @State private var pageAnchorDate: LocalDate = .today()
    @State private var pageOffset: Int = 0
    @State private var pageCache = TodayPageCacheBox()
    @State private var expandedDoneDates: Set<LocalDate> = []
    @State private var selectedEvent: Event?
    @State private var pendingSheetAfterEventDismiss: GokeSheet?
    @State private var scrollToTopToken: Int = 0
    @State private var scrollToTopTargetOffset: Int = 0

    init() {}

    private var baseKey: TodayRecomputeBaseKey {
        TodayRecomputeBaseKey(
            snapshotRevision: container.snapshotRevision,
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes,
            minuteBucket: minuteBucket,
            pageAnchorDate: pageAnchorDate
        )
    }

    private var recomputeKey: TodayRecomputeKey {
        TodayRecomputeKey(base: baseKey, pageOffset: pageOffset)
    }

    private var isMinuteTickerActive: Bool {
        scenePhase == .active && router.selectedTab == .today
    }

    private func resolvedDisplay(for offset: Int, now: LocalDateTime) -> TodayDisplaySnapshot {
        let actualDayOffset = now.date.daysUntil(pageAnchorDate) + offset
        return pageCache.display(
            for: offset,
            actualDayOffset: actualDayOffset,
            key: baseKey,
            liveSnapshot: container.liveSnapshot,
            now: now
        )
    }

    private var simplifyMotion: Bool {
        reduceMotion || performance.shouldSimplifyMotion
    }

    var body: some View {
        let now = LocalDateTime.now()
        let data = resolvedDisplay(for: pageOffset, now: now)

        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                todayHeader(data: data)

                GokeHorizontalPager(
                    selection: $pageOffset,
                    bounds: nil,
                    preloadNeighbors: true,
                    isScrollEnabled: true,
                    reloadToken: TodayPagerReloadToken(
                        base: baseKey,
                        expandedDoneDates: expandedDoneDates,
                        scrollToTopToken: scrollToTopToken,
                        scrollToTopTargetOffset: scrollToTopTargetOffset,
                        showTeacher: settingsStore.settings.showTeacher
                    )
                ) { offset in
                    todayPageView(
                        offset: offset,
                        data: resolvedDisplay(for: offset, now: now)
                    )
                }
            }
            .background(theme.paper.ignoresSafeArea())

            ZStack {
                if !data.dayEmpty {
                    GokeMintFab {
                        router.presentQuickAdd(
                            anchorDate: data.date,
                            defaultStartDate: data.date,
                            defaultBucket: data.isToday ? .TODAY : nil
                        )
                    }
                    .padding(.trailing, GokeSpacing.lg)
                    .padding(.bottom, GokeSpacing.lg)
                    .transition(simplifyMotion ? .opacity : .scale(scale: 0.85).combined(with: .opacity))
                }
            }
            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: data.dayEmpty)
        }
        .toolbar(.hidden, for: .navigationBar)
        .task(id: recomputeKey) {
            recomputeDisplayWindow()
        }
        .task(id: isMinuteTickerActive) {
            guard isMinuteTickerActive else { return }
            let currentBucket = Int64(Date().timeIntervalSince1970 / 60)
            if minuteBucket != currentBucket {
                minuteBucket = currentBucket
            }
            await runMinuteTicker()
        }
        .onChange(of: router.reselectCount(for: .today)) { _, _ in
            jumpToToday()
            scrollToTopTargetOffset = pageOffset
            scrollToTopToken += 1
        }
        .sheet(
            item: $selectedEvent,
            onDismiss: {
                if let pending = pendingSheetAfterEventDismiss {
                    pendingSheetAfterEventDismiss = nil
                    router.present(pending)
                }
            }
        ) { event in
            GokeEventDetailSheet(
                event: event,
                snapshot: container.snapshot,
                onDismiss: { selectedEvent = nil },
                onOpenConvertedTask: { taskId in
                    pendingSheetAfterEventDismiss = .todoEdit(taskId: taskId)
                }
            )
            .gokePaperSheet(theme, detents: [.medium, .large])
        }
    }

    @ViewBuilder
    private func todayPageView(offset: Int, data: TodayDisplaySnapshot) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                    Color.clear
                        .frame(height: 0)
                        .id("today-top")

                    if !data.dayQuiet {
                        summaryCard(data: data)
                    }

                    if data.overdue.count == 1, let single = data.overdue.first {
                        singleOverdueSection(task: single, data: data)
                    } else if data.overdue.count >= 2 {
                        overdueTidyBanner(count: data.overdue.count)
                    }

                    if !data.pendingItems.isEmpty {
                        timelineSection(data: data)
                    }

                    if !data.doneItems.isEmpty {
                        doneSection(data: data)
                    }

                    if data.dayEmpty {
                        emptyDayView(data: data)
                    }
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.top, GokeSpacing.xs)
                .padding(.bottom, 104)
            }
            .onChange(of: scrollToTopToken) { _, _ in
                guard offset == scrollToTopTargetOffset else { return }
                withAnimation(GokeMotion.standard(reduceMotion: simplifyMotion)) {
                    proxy.scrollTo("today-top", anchor: .top)
                }
            }
        }
    }

    // MARK: - Header (Title-Free 64pt Paper Bar)

    private func todayHeader(data: TodayDisplaySnapshot) -> some View {
        HStack(alignment: .center, spacing: GokeSpacing.sm) {
            VStack(alignment: .leading, spacing: 2) {
                Text(data.headerTitle)
                    .font(GokeTypography.titleLarge)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .contentTransition(simplifyMotion ? .identity : .numericText())
                    .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: data.headerTitle)
                if !data.headerSubtitle.isEmpty {
                    Text(data.headerSubtitle)
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .contentTransition(simplifyMotion ? .identity : .numericText())
                        .animation(GokeMotion.crossfade(reduceMotion: simplifyMotion), value: data.headerSubtitle)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            ZStack {
                if !data.isToday {
                    GokeReturnPill("回今天") {
                        jumpToToday()
                    }
                    .transition(simplifyMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.92)))
                }
            }
            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: data.isToday)
        }
        .padding(.horizontal, GokeSpacing.lg)
        .frame(height: 64)
    }

    // MARK: - Flat Summary Card (No Card-in-Card, One-Number Rule)

    private func summaryCard(data: TodayDisplaySnapshot) -> some View {
        HStack(alignment: .center, spacing: GokeSpacing.md) {
            VStack(alignment: .leading, spacing: 2) {
                Text(data.summaryText)
                    .font(GokeTypography.titleMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                    .contentTransition(simplifyMotion ? .identity : .numericText())

                if let nextLine = data.nextLine {
                    Text(nextLine)
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }

                if let dueBeforeClass = data.dueBeforeClassLine {
                    Text(dueBeforeClass)
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.accent)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if data.taskTotal > 0 {
                TodayProgressRing(
                    progress: data.taskProgress,
                    done: data.taskDone,
                    total: data.taskTotal
                )
                .frame(width: 44, height: 44)
            }
        }
        .padding(GokeSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.surface)
        )
    }

    // MARK: - Sections

    private func singleOverdueSection(task: GokeTask, data: TodayDisplaySnapshot) -> some View {
        VStack(alignment: .leading, spacing: GokeSpacing.xs) {
            HStack(alignment: .firstTextBaseline, spacing: GokeSpacing.sm) {
                Text("已逾期")
                    .font(GokeTypography.labelLarge)
                    .foregroundStyle(theme.muted)
                Spacer(minLength: GokeSpacing.sm)
                Text("1")
                    .font(GokeTypography.labelMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.removed)
            }
            .padding(.vertical, GokeSpacing.xs)
            .accessibilityAddTraits(.isHeader)

            let resolvedDue = data.overdueEffectiveDues[task.id] ?? task.due
            let overdueSub: String? = resolvedDue.map {
                "\(String(format: "%02d-%02d", $0.date.month, $0.date.day)) 起逾期"
            }
            GokeTaskRow(
                task: task,
                subtitle: overdueSub,
                subtitleColor: theme.removed,
                listColor: task.listId.flatMap { data.listColors[$0] }.map { theme.courseColor(index: $0).foreground },
                onToggle: { toggleTask(task, done: !task.done, now: data.now) },
                onOpen: { router.present(.todoEdit(taskId: task.id)) }
            )
        }
    }

    private func overdueTidyBanner(count: Int) -> some View {
        HStack(alignment: .center, spacing: GokeSpacing.md) {
            Text("已逾期 · \(count)")
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(theme.removed)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                router.present(.morningTidy)
            } label: {
                Text("去整理")
                    .font(GokeTypography.labelLarge.weight(.semibold))
                    .foregroundStyle(theme.removed)
                    .lineLimit(1)
                    .frame(minHeight: 44)
                    .padding(.horizontal, GokeSpacing.sm)
            }
            .buttonStyle(.mintCompact)
        }
        .padding(.horizontal, GokeSpacing.lg)
        .padding(.vertical, GokeSpacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.conflictBg)
        )
    }

    private func timelineSection(data: TodayDisplaySnapshot) -> some View {
        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            GokeSectionHeader(data.isToday ? "今日安排" : "当日安排", count: data.pendingItems.count)
            ForEach(data.pendingItems) { item in
                GokeAgendaRow(
                    item: item,
                    now: data.now,
                    showTeacher: settingsStore.settings.showTeacher,
                    listColors: data.listColors,
                    onOpenCourse: { courseId in
                        router.pushCourseDetail(id: courseId)
                    },
                    onOpenEvent: { eventId in
                        selectedEvent = container.snapshot.events.first { $0.id == eventId && $0.deletedAt == nil }
                    },
                    onOpenTask: { taskId in
                        router.present(.todoEdit(taskId: taskId))
                    },
                    onToggleTask: { task, done in
                        toggleTask(task, done: done, now: data.now)
                    }
                )
            }
        }
    }

    private func doneSection(data: TodayDisplaySnapshot) -> some View {
        let isExpanded = expandedDoneDates.contains(data.date)
        return VStack(alignment: .leading, spacing: GokeSpacing.xs) {
            Button {
                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                    if isExpanded {
                        expandedDoneDates.remove(data.date)
                    } else {
                        expandedDoneDates.insert(data.date)
                    }
                }
            } label: {
                HStack(alignment: .center, spacing: GokeSpacing.xs) {
                    Text("已完成")
                        .font(GokeTypography.labelLarge)
                        .foregroundStyle(theme.muted)
                    Spacer(minLength: GokeSpacing.sm)
                    Text("\(data.doneItems.count)")
                        .font(GokeTypography.labelMedium)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.muted)
                        .rotationEffect(.degrees(isExpanded ? 0 : -90))
                }
                .padding(.horizontal, 4)
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            if isExpanded {
                VStack(alignment: .leading, spacing: GokeSpacing.xs) {
                    ForEach(data.doneItems) { item in
                        if let task = extractTask(from: item) {
                            GokeTaskRow(
                                task: task,
                                subtitle: nil,
                                listColor: task.listId.flatMap { data.listColors[$0] }.map { theme.courseColor(index: $0).foreground },
                                onToggle: { toggleTask(task, done: !task.done, now: data.now) },
                                onOpen: { router.present(.todoEdit(taskId: task.id)) }
                            )
                        }
                    }
                }
                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            }
        }
        .clipped()
    }

    private func emptyDayView(data: TodayDisplaySnapshot) -> some View {
        GokeEmptyState(
            systemImage: "sun.max.fill",
            title: data.isToday ? "今天没有安排" : "这一天没有安排",
            primaryActionTitle: "添加待办",
            onPrimaryAction: {
                router.presentQuickAdd(
                    anchorDate: data.date,
                    defaultStartDate: data.date,
                    defaultBucket: data.isToday ? .TODAY : nil
                )
            }
        )
        .frame(maxWidth: .infinity)
        .padding(.vertical, 88)
    }

    // MARK: - Lifecycle Helpers

    private func extractTask(from item: AgendaItem) -> GokeTask? {
        switch item {
        case .task(let t, _, _): return t
        case .block(let t, _, _): return t
        default: return nil
        }
    }

    private func toggleTask(_ task: GokeTask, done: Bool, now: LocalDateTime) {
        let snap = container.snapshot
        let blockMins = settingsStore.settings.defaultBlockMinutes
        Task {
            await GokeTaskToggle.toggle(
                task: task,
                done: done,
                snapshot: snap,
                now: now,
                defaultBlockMinutes: blockMins,
                actions: actions
            )
        }
    }

    private func jumpToToday() {
        let currentToday = LocalDate.today()
        let targetOffset = pageAnchorDate.daysUntil(currentToday)
        if pageOffset != targetOffset {
            withAnimation(GokeMotion.standard(reduceMotion: simplifyMotion)) {
                pageOffset = targetOffset
            }
        }
    }

    private func recomputeDisplayWindow() {
        let now = LocalDateTime.now()
        let currentToday = now.date
        if currentToday != lastKnownToday {
            let wasOnOldToday = pageAnchorDate.addingDays(pageOffset) == lastKnownToday
            lastKnownToday = currentToday
            if wasOnOldToday {
                var tx = Transaction()
                tx.disablesAnimations = true
                withTransaction(tx) {
                    pageAnchorDate = currentToday
                    pageOffset = 0
                    pageCache.clear()
                }
            }
        }

        let key = baseKey
        let targetOffsets = [pageOffset - 2, pageOffset - 1, pageOffset, pageOffset + 1, pageOffset + 2]
        pageCache.prewarm(
            offsets: targetOffsets,
            anchorDeltaFromToday: currentToday.daysUntil(pageAnchorDate),
            key: key,
            liveSnapshot: container.liveSnapshot,
            now: now
        )
    }

    private func runMinuteTicker() async {
        while !Task.isCancelled {
            let nowEpoch = Date().timeIntervalSince1970
            let nextMinuteDelay = max(1.0, 60.0 - nowEpoch.truncatingRemainder(dividingBy: 60.0) + 0.1)
            try? await Task.sleep(for: .seconds(nextMinuteDelay))
            guard !Task.isCancelled else { break }
            minuteBucket = Int64(Date().timeIntervalSince1970 / 60)
        }
    }
}

// MARK: - Progress Ring (Android TodayScreen.kt:ProgressRing Parity)

private struct TodayProgressRing: View, Equatable {
    let progress: Double
    let done: Int
    let total: Int

    @Environment(\.gokeTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.gokePerformance) private var performance
    @State private var pulseScale: CGFloat = 1.0
    @State private var wasComplete: Bool = false
    @State private var pulseTask: Task<Void, Never>?

    nonisolated static func == (lhs: TodayProgressRing, rhs: TodayProgressRing) -> Bool {
        lhs.done == rhs.done && lhs.total == rhs.total && abs(lhs.progress - rhs.progress) < 0.001
    }

    var body: some View {
        let clamped = min(max(progress, 0.0), 1.0)
        let complete = clamped >= 1.0 && total > 0
        let simplify = GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
        let barColor = complete ? theme.added : theme.accent

        ZStack {
            Circle()
                .stroke(theme.surfaceHighest, lineWidth: 4)
            Circle()
                .trim(from: 0, to: clamped)
                .stroke(barColor, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(GokeMotion.standard(reduceMotion: simplify), value: clamped)
            Text("\(done)/\(total)")
                .font(GokeTypography.labelSmall)
                .monospacedDigit()
                .foregroundStyle(complete ? theme.added : theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .contentTransition(simplify ? .identity : .numericText())
        }
        .scaleEffect(pulseScale)
        .onAppear {
            wasComplete = complete
        }
        .onDisappear {
            pulseTask?.cancel()
            pulseTask = nil
        }
        .onChange(of: complete) { _, isNowComplete in
            if isNowComplete && !wasComplete && !simplify {
                pulseTask?.cancel()
                withAnimation(GokeMotion.pressSpring) {
                    pulseScale = 1.10
                }
                pulseTask = Task {
                    try? await Task.sleep(for: .milliseconds(110))
                    guard !Task.isCancelled else { return }
                    withAnimation(GokeMotion.snappySpring) {
                        pulseScale = 1.0
                    }
                }
            }
            wasComplete = isNowComplete
        }
        .accessibilityLabel("今日待办进度 \(done) / \(total)")
    }
}

// MARK: - Formatting Helpers (Preserves TimetableView / CourseDetailAndEditViews API)

enum TodayFormatters {
    static let weekdays = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    static func weekdayLabel(_ dayOfWeek: Int) -> String {
        weekdays[max(0, min(6, dayOfWeek - 1))]
    }

    static func smartFoldRoom(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let parts = trimmed
            .split { $0 == "," || $0 == "，" || $0 == ";" || $0 == "；" || $0 == "/" || $0 == "、" }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        if parts.count > 2, let first = parts.first {
            return "\(first) 等 \(parts.count) 处"
        }
        return trimmed
    }

    static func shortRelativeDay(from today: LocalDate, to target: LocalDate) -> String {
        let diff = today.daysUntil(target)
        switch diff {
        case 0: return "今天"
        case 1: return "明天"
        case 2: return "后天"
        case -1: return "昨天"
        default: return "\(target.month)/\(target.day) \(weekdayLabel(target.dayOfWeek))"
        }
    }

    static func dueLabel(due: LocalDateTime, hasTime: Bool, today: LocalDate) -> String {
        let diff = today.daysUntil(due.date)
        let dayPart: String = switch diff {
        case 0: "今天"
        case 1: "明天"
        case -1: "昨天"
        default: "\(due.date.month)月\(due.date.day)日"
        }
        return hasTime ? "\(dayPart) \(due.time.description)" : dayPart
    }
}
