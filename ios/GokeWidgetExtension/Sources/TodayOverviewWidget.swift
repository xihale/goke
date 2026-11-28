import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

/// Minimalist Mint Paper Today Overview Widget (Small / Medium / Large):
/// Unified vertical timeline on clean paper combining remaining today courses/blocks and
/// interactive overdue/today tasks (parity with Android `widget_today.xml`).
struct TodayOverviewWidget: Widget {
    static let kind = "app.goke.widget.today"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: WidgetSnapshotProvider()) { entry in
            TodayOverviewWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.theme.paper
                }
        }
        .contentMarginsDisabled()
        .configurationDisplayName("今日概览")
        .description("一屏查看今日课程、日程与待办进度。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Root Adaptive View

private struct TodayOverviewWidgetView: View {
    let entry: GokeWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode

    private var theme: GokeTheme { entry.theme }
    private var isAccented: Bool { WidgetFormat.isAccentedMode(renderingMode) }

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                smallLayout
            case .systemMedium:
                mediumLayout
            case .systemLarge:
                largeLayout
            default:
                mediumLayout
            }
        }
        .widgetURL(WidgetDeepLink.today)
    }

    // MARK: - Small (2×2 Quiet Agenda Preview — 141×141 inner canvas)

    private var smallLayout: some View {
        let hasCourse = entry.nextUp != nil
        let hasTask = !entry.todayTasks.isEmpty

        return VStack(alignment: .leading, spacing: 0) {
            // Plain typographic header (week hidden automatically during vacation)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(WidgetFormat.shortDate(entry.localNow.date))
                    .font(.system(size: 13, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(isAccented ? Color.primary : theme.ink)
                Text(WidgetFormat.weekdayLabel(entry.localNow.date.dayOfWeek))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                Spacer(minLength: 0)
                if let week = entry.currentWeekNumber {
                    Text("第\(week)周")
                        .font(.system(size: 11, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.primary : theme.accent)
                        .widgetAccentable()
                }
            }

            Spacer().frame(height: 8)

            if !hasCourse && !hasTask {
                Spacer(minLength: 0)
                Text(entry.hasFinishedAllTodayCourses ? "今日课程已结束" : "今日无安排")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.bottom, 10)
                Spacer(minLength: 0)
            } else {
                // Primary slot: next/ongoing course strip (concentric r=10, zero overflow at 2-line title)
                if let nextUp = entry.nextUp {
                    let occ = nextUp.occurrence
                    let pair = theme.courseColor(index: occ.course.colorIndex)
                    let inProgress = entry.isInProgress(occ)
                    let isExam = occ.course.isExam
                    let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                    let venueLine = isExam
                        ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                        : occ.room

                    VStack(alignment: .leading, spacing: 2.5) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor))
                                .frame(width: 6, height: 6)
                                .widgetAccentable()
                            Text(inProgress
                                ? (isExam ? "考试中" : "上课中")
                                : (occ.date == entry.localNow.date
                                    ? WidgetFormat.hhmm(occ.start.time)
                                    : WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: occ.date)))
                                .font(.system(size: 10.5, weight: .semibold))
                                .monospacedDigit()
                                .foregroundStyle(isAccented ? Color.primary : (isExam ? theme.tertiary : pair.foreground))
                            Spacer(minLength: 0)
                        }

                        Text(examDisplay?.title ?? occ.course.name)
                            .font(.system(size: 13.5, weight: .semibold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(2)

                        if !venueLine.isEmpty {
                            Text(venueLine)
                                .font(.system(size: 10.5))
                                .monospacedDigit()
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                .lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 7)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: WidgetMetrics.cardRadius, style: .continuous)
                            .fill(isAccented ? Color.primary.opacity(0.08) : pair.background.opacity(0.55))
                    )
                } else {
                    Text(entry.hasFinishedAllTodayCourses ? "今日 \(entry.todayCourses.count) 堂课已结束" : "今日无课")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .padding(.top, 2)
                }

                Spacer(minLength: 6)

                // Bottom quiet agenda line: top today task or calm count text
                if let topTask = entry.todayTasks.first {
                    let overdue = TaskLogic.isOverdue(topTask, now: entry.localNow)
                    let boxColor = isAccented
                        ? (overdue ? Color.primary : Color.secondary)
                        : (overdue ? theme.error : (theme.priorityColor(topTask.priority) ?? theme.outline))
                    HStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .strokeBorder(boxColor, lineWidth: 1.4)
                            .frame(width: 12, height: 12)
                            .widgetAccentable(overdue)
                        Text(topTask.title)
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(1)
                        Spacer(minLength: 4)
                        if entry.todayTasks.count > 1 {
                            Text("+\(entry.todayTasks.count - 1)")
                                .font(.system(size: 10.5, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        }
                    }
                } else {
                    Text("今日无待办")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                }
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: - Medium (4×2 Full-Width Unified Vertical Timeline — 332×141 inner canvas)

    private var mediumLayout: some View {
        let maxRows = 3
        let scheduleSlice = Array(entry.remainingTodaySchedule.prefix(maxRows))
        let taskAllowance = max(0, maxRows - scheduleSlice.count)
        let taskSlice = Array(entry.todayTasks.prefix(taskAllowance))
        let isEmpty = scheduleSlice.isEmpty && taskSlice.isEmpty

        return VStack(alignment: .leading, spacing: 0) {
            unifiedHeader
            Spacer().frame(height: 8)

            if isEmpty {
                Spacer(minLength: 0)
                Text(entry.hasFinishedAllTodayCourses ? "今日安排已全部完成" : "今日无安排")
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.bottom, 12)
                Spacer(minLength: 0)
            } else {
                if !scheduleSlice.isEmpty {
                    VStack(alignment: .leading, spacing: 5) {
                        ForEach(scheduleSlice) { slot in
                            unifiedScheduleRow(slot)
                        }
                    }
                }

                if !scheduleSlice.isEmpty && !taskSlice.isEmpty {
                    Rectangle()
                        .fill(isAccented ? Color.primary.opacity(0.18) : theme.outlineVariant.opacity(0.6))
                        .frame(height: 0.5)
                        .padding(.vertical, 4)
                }

                if !taskSlice.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(taskSlice) { task in
                            interactiveTodayTaskRow(task)
                        }
                    }
                }

                Spacer(minLength: 0)
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: - Large (4×4 Unified Timeline with Quiet Sections — 332×348 inner canvas)

    private var largeLayout: some View {
        let maxTotal = 9
        var scheduleSlice: [WidgetScheduleSlot]
        var taskSlice: [GokeTask]

        if entry.remainingTodaySchedule.isEmpty {
            scheduleSlice = []
            taskSlice = Array(entry.todayTasks.prefix(maxTotal))
        } else if entry.todayTasks.isEmpty {
            scheduleSlice = Array(entry.remainingTodaySchedule.prefix(maxTotal))
            taskSlice = []
        } else {
            let maxSchedule = min(5, entry.remainingTodaySchedule.count)
            scheduleSlice = Array(entry.remainingTodaySchedule.prefix(maxSchedule))
            let remainingSlots = max(3, maxTotal - scheduleSlice.count)
            taskSlice = Array(entry.todayTasks.prefix(remainingSlots))
        }

        let showSectionHeaders = !scheduleSlice.isEmpty && !taskSlice.isEmpty
        let totalAvailable = entry.remainingTodaySchedule.count + entry.todayTasks.count
        if showSectionHeaders && (scheduleSlice.count + taskSlice.count) >= maxTotal && totalAvailable > maxTotal {
            if taskSlice.count > 3 {
                taskSlice.removeLast()
            } else if scheduleSlice.count > 4 {
                scheduleSlice.removeLast()
            }
        }
        let hiddenCount = max(0, totalAvailable - (scheduleSlice.count + taskSlice.count))
        let isEmpty = scheduleSlice.isEmpty && taskSlice.isEmpty

        return VStack(alignment: .leading, spacing: 0) {
            unifiedHeader
            Spacer().frame(height: 8)

            if isEmpty {
                Spacer(minLength: 0)
                Text(entry.hasFinishedAllTodayCourses ? "今日安排已全部完成" : "今日无安排")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.bottom, 12)
                Spacer(minLength: 0)
            } else {
                if !scheduleSlice.isEmpty {
                    VStack(alignment: .leading, spacing: 5) {
                        if showSectionHeaders {
                            Text("日程")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        }
                        ForEach(scheduleSlice) { slot in
                            unifiedScheduleRow(slot)
                        }
                    }
                }

                if showSectionHeaders {
                    Spacer().frame(height: 10)
                }

                if !taskSlice.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        if showSectionHeaders {
                            Text("待办")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        }
                        ForEach(taskSlice) { task in
                            interactiveTodayTaskRow(task)
                        }
                    }
                }

                if hiddenCount > 0 {
                    Text("+\(hiddenCount) 项未显示")
                        .font(.system(size: 11, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .padding(.top, 6)
                }

                Spacer(minLength: 0)
            }
        }
        .padding(WidgetMetrics.editorialPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    // MARK: - Subcomponents

    private var unifiedHeader: some View {
        HStack(alignment: .center, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text(WidgetFormat.shortDate(entry.localNow.date))
                    .font(.system(size: 15, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(isAccented ? Color.primary : theme.ink)
                Text(WidgetFormat.weekdayLabel(entry.localNow.date.dayOfWeek))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                if let week = entry.currentWeekNumber {
                    Text("· 第\(week)周")
                        .font(.system(size: 12, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.primary : theme.accent)
                        .widgetAccentable()
                }
            }
            Spacer(minLength: 0)
            WidgetQuickAddButton(theme: theme, isAccented: isAccented)
        }
    }

    @ViewBuilder
    private func unifiedScheduleRow(_ slot: WidgetScheduleSlot) -> some View {
        switch slot {
        case .course(let occ):
            let pair = theme.courseColor(index: occ.course.colorIndex)
            let isOngoing = entry.isInProgress(occ)
            let isExam = occ.course.isExam
            let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
            let venueLine = isExam
                ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                : occ.room
            let barColor = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor)

            Link(destination: WidgetDeepLink.course(occ.course.id)) {
                HStack(alignment: .center, spacing: 8) {
                    Text(WidgetFormat.hhmm(occ.start.time))
                        .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : (isOngoing ? pair.foreground : theme.muted))
                        .frame(width: WidgetMetrics.timeColumnWidth, alignment: .leading)

                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(barColor)
                        .frame(width: 3, height: 18)
                        .widgetAccentable()

                    HStack(spacing: 4) {
                        if let examDisplay {
                            Text(examDisplay.badgeShort)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(isAccented ? Color.primary : theme.tertiary)
                                .widgetAccentable()
                        }
                        Text(examDisplay?.title ?? occ.course.name)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(1)
                    }
                    .layoutPriority(1)

                    Spacer(minLength: 4)

                    if !venueLine.isEmpty {
                        Text(venueLine)
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                            .lineLimit(1)
                    }
                }
                .padding(.vertical, isOngoing ? 4 : 2)
                .background {
                    if isOngoing {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(isAccented ? Color.primary.opacity(0.08) : pair.background.opacity(0.45))
                            .padding(.horizontal, -6)
                    }
                }
            }

        case .block(let block):
            let pair = theme.courseColor(index: block.colorIndex)
            let isOngoing = block.start <= entry.localNow && block.end > entry.localNow
            let isExam = block.task.isExam
            let examDisplay = isExam ? WidgetFormat.splitExamDisplay(block.task.title) : nil
            let meta = WidgetFormat.blockMetadata(for: block.task)
            let barColor = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor)

            Link(destination: WidgetDeepLink.todoItem(block.task.id)) {
                HStack(alignment: .center, spacing: 8) {
                    Text(WidgetFormat.hhmm(block.start.time))
                        .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : (isOngoing ? pair.foreground : theme.muted))
                        .frame(width: WidgetMetrics.timeColumnWidth, alignment: .leading)

                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(barColor)
                        .frame(width: 3, height: 18)
                        .widgetAccentable()

                    Text(examDisplay?.title ?? block.task.title)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(isAccented ? Color.primary : theme.ink)
                        .lineLimit(1)
                        .layoutPriority(1)

                    Spacer(minLength: 4)

                    Text(meta)
                        .font(.system(size: 11))
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .lineLimit(1)
                }
                .padding(.vertical, isOngoing ? 4 : 2)
                .background {
                    if isOngoing {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(isAccented ? Color.primary.opacity(0.08) : pair.background.opacity(0.45))
                            .padding(.horizontal, -6)
                    }
                }
            }
        }
    }

    private func interactiveTodayTaskRow(_ task: GokeTask) -> some View {
        let overdue = TaskLogic.isOverdue(task, now: entry.localNow)

        return HStack(spacing: 4) {
            WidgetMintCheckButton(
                task: task,
                overdue: overdue,
                theme: theme,
                isAccented: isAccented
            )
            .padding(.leading, WidgetMetrics.checkOpticalLeading)

            Link(destination: WidgetDeepLink.todoItem(task.id)) {
                HStack(spacing: 6) {
                    Text(task.title)
                        .font(.system(size: WidgetMetrics.taskTitleSize, weight: .medium))
                        .foregroundStyle(isAccented ? Color.primary : theme.ink)
                        .lineLimit(1)
                        .layoutPriority(1)
                    Spacer(minLength: 4)
                    if let due = task.due {
                        let label = task.dueHasTime && due.date == entry.localNow.date
                            ? WidgetFormat.hhmm(due.time)
                            : WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: due.date)
                        Text(label)
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(
                                isAccented
                                    ? (overdue ? Color.primary : Color.secondary)
                                    : (overdue ? theme.error : theme.muted)
                            )
                            .lineLimit(1)
                            .widgetAccentable(overdue)
                    }
                }
                .frame(minHeight: WidgetMetrics.checkButtonHitSize)
                .contentShape(Rectangle())
            }
        }
    }
}
