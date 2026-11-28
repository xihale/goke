import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

/// Minimalist Mint Paper Weekly Timetable Widget (Small / Medium / Large):
/// - Small: clean abstract botanical occupancy minimap (zero microscopic 6.5pt text).
/// - Medium: crisp 5-day (or 7-day when weekend classes exist) barless course blocks.
/// - Large: full 7-day grid with quiet period rail, crisp course names & rooms.
struct WeeklyTimetableWidget: Widget {
    static let kind = "app.goke.widget.weeklyTimetable"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: WidgetSnapshotProvider()) { entry in
            WeeklyTimetableWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.theme.paper
                }
        }
        .contentMarginsDisabled()
        .configurationDisplayName("本周课表")
        .description("植物色块迷你周课表，一眼掌握全周课程分布。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - View

private struct WeeklyTimetableWidgetView: View {
    let entry: GokeWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode

    private var theme: GokeTheme { entry.theme }
    private var isAccented: Bool { WidgetFormat.isAccentedMode(renderingMode) }

    var body: some View {
        Group {
            if let layout = entry.currentWeekLayout {
                gridContent(layout: layout)
            } else {
                emptyState
            }
        }
        .widgetURL(WidgetDeepLink.timetable)
    }

    @ViewBuilder
    private func gridContent(layout: TimetableWeekLayout) -> some View {
        let hasWeekendCourses = layout.columns
            .filter { $0.dayOfWeek >= 6 }
            .contains { !$0.slots.isEmpty }
        let visibleColumns: [TimetableColumn] = (family == .systemLarge || (family == .systemMedium && hasWeekendCourses))
            ? layout.columns
            : layout.columns.filter { $0.dayOfWeek <= 5 }
        let today = entry.localNow.date
        let isSmall = family == .systemSmall
        let isLarge = family == .systemLarge
        let colSpacing: CGFloat = isLarge ? 2 : 3
        let railWidth: CGFloat = 12

        VStack(spacing: 4) {
            // Ultra-slim 1-line header
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("第\(layout.week)周")
                    .font(.system(size: isLarge ? 12.5 : 11.5, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(isAccented ? Color.primary : theme.ink)
                Spacer(minLength: 0)
                if let rangeText = entry.weekDateRangeText {
                    Text(rangeText)
                        .font(.system(size: isSmall ? 9.5 : 10, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                        .lineLimit(1)
                }
            }

            // Weekday header strip
            HStack(spacing: colSpacing) {
                if isLarge {
                    Color.clear.frame(width: railWidth, height: 1)
                }
                ForEach(visibleColumns) { col in
                    let isToday = col.date == today
                    let badgeSize: CGFloat = isLarge ? 16 : 14
                    VStack(spacing: 1) {
                        Text(WidgetFormat.weekdayShort(col.dayOfWeek))
                            .font(.system(size: isSmall ? 9.5 : 10, weight: isToday ? .bold : .medium))
                            .foregroundStyle(
                                isToday
                                    ? (isAccented ? Color.primary : theme.onAccent)
                                    : (isAccented ? Color.secondary : theme.muted)
                            )
                            .frame(width: badgeSize, height: badgeSize)
                            .background {
                                if isToday {
                                    Circle()
                                        .fill(isAccented ? Color.primary.opacity(0.22) : theme.accent)
                                        .widgetAccentable()
                                }
                            }
                        if isLarge {
                            Text("\(col.date.day)")
                                .font(.system(size: 8.5, weight: isToday ? .semibold : .regular, design: .rounded))
                                .monospacedDigit()
                                .foregroundStyle(
                                    isToday
                                        ? (isAccented ? Color.primary : theme.accent)
                                        : (isAccented ? Color.secondary : theme.muted)
                                )
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            // Grid body
            HStack(spacing: colSpacing) {
                if isLarge {
                    periodRail(periods: layout.periods)
                        .frame(width: railWidth)
                }
                ForEach(visibleColumns) { col in
                    dayColumnView(
                        column: col,
                        isToday: col.date == today,
                        periodCount: layout.periods.count,
                        isSmall: isSmall
                    )
                }
            }
            .frame(maxHeight: .infinity)
        }
        .padding(.horizontal, WidgetMetrics.gridPaddingH)
        .padding(.vertical, WidgetMetrics.gridPaddingV)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func periodRail(periods: [PeriodTime]) -> some View {
        VStack(spacing: 0) {
            ForEach(periods) { p in
                Text("\(p.index)")
                    .font(.system(size: 8.5, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(isAccented ? Color.secondary : theme.muted.opacity(0.88))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private func dayColumnView(column: TimetableColumn, isToday: Bool, periodCount: Int, isSmall: Bool) -> some View {
        GeometryReader { geo in
            let corner: CGFloat = isSmall ? 4 : 6
            ZStack(alignment: .topLeading) {
                if isToday {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(isAccented ? Color.primary.opacity(0.06) : theme.accentContainer.opacity(0.24))
                } else {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(isAccented ? Color.clear : theme.surface.opacity(0.5))
                }

                // Subtle morning/afternoon/evening dividers for spatial time-of-day anchor
                let rows = max(1, periodCount)
                ForEach([4, 9].filter { $0 < rows }, id: \.self) { splitRow in
                    let y = (CGFloat(splitRow) / CGFloat(rows)) * geo.size.height
                    Rectangle()
                        .fill(isAccented ? Color.primary.opacity(0.14) : theme.outlineVariant.opacity(0.45))
                        .frame(height: 0.5)
                        .offset(y: y)
                }

                ForEach(column.slots) { slot in
                    if isSmall {
                        minimapBlockView(slot: slot, containerSize: geo.size)
                    } else {
                        slotBlockView(slot: slot, containerSize: geo.size)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Small: Clean Abstract Botanical Minimap (Zero Microscopic Text)

    @ViewBuilder
    private func minimapBlockView(slot: TimetableSlotFrame, containerSize: CGSize) -> some View {
        let lanes = CGFloat(max(1, slot.laneCount))
        let width = max(4, (containerSize.width / lanes) - 1.5)
        let x = (CGFloat(slot.laneIndex) / lanes) * containerSize.width + 0.75
        let y = min(max(0, CGFloat(slot.normalizedY) * containerSize.height + 0.75), max(0, containerSize.height - 4))
        let rawHeight = max(6, CGFloat(slot.normalizedHeight) * containerSize.height - 1.5)
        let height = max(4, min(rawHeight, max(4, containerSize.height - y)))

        let resolved = resolveSlotPresentation(slot)
        let pair = theme.courseColor(index: resolved.colorIndex)
        let pillColor: Color = if isAccented {
            Color.primary.opacity(resolved.isFinished ? 0.14 : 0.34)
        } else {
            pair.barColor.opacity(resolved.isFinished ? 0.32 : 0.78)
        }

        RoundedRectangle(cornerRadius: 4, style: .continuous)
            .fill(pillColor)
            .overlay {
                if case .ongoing(let progress) = resolved.phase {
                    if isAccented {
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(Color.primary.opacity(0.25))
                    } else {
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(pair.progressWipeGradient(progress: progress))
                    }
                }
            }
            .frame(width: width, height: height)
            .offset(x: x, y: y)
            .widgetAccentable(!resolved.isFinished)
    }

    // MARK: - Medium & Large: Clean Barless Botanical Course Blocks

    @ViewBuilder
    private func slotBlockView(slot: TimetableSlotFrame, containerSize: CGSize) -> some View {
        let lanes = CGFloat(max(1, slot.laneCount))
        let width = max(6, (containerSize.width / lanes) - 1.5)
        let x = (CGFloat(slot.laneIndex) / lanes) * containerSize.width + 0.75
        let y = min(max(0, CGFloat(slot.normalizedY) * containerSize.height + 0.75), max(0, containerSize.height - 6))
        let rawHeight = max(10, CGFloat(slot.normalizedHeight) * containerSize.height - 1.5)
        let height = max(6, min(rawHeight, max(6, containerSize.height - y)))
        let corner: CGFloat = family == .systemLarge ? 6 : 5

        let resolved = resolveSlotPresentation(slot)
        let pair = theme.courseColor(index: resolved.colorIndex)
        let inkColor = isAccented
            ? Color.primary.opacity(resolved.isFinished ? 0.55 : 0.95)
            : pair.inkColor(isFinished: resolved.isFinished)
        let displayTitle = resolved.isExam ? "考 \(resolved.title)" : resolved.title

        let isLarge = family == .systemLarge
        let showText = height >= 13
        let showRoom = isLarge && !resolved.room.isEmpty && height >= 34
        let titleLineLimit: Int = if isLarge && !showRoom && height >= 44 {
            3
        } else {
            height >= 26 ? 2 : 1
        }
        let hPad: CGFloat = slot.laneCount > 1 ? 1.5 : 2.5
        let vPad: CGFloat = height >= 16 ? 2 : 1.5
        let blockAlignment: Alignment = height >= 26 ? .topLeading : .leading

        VStack(alignment: .leading, spacing: 1) {
            if showText {
                Text(displayTitle)
                    .font(.system(size: isLarge ? 9.5 : 8.5, weight: .semibold))
                    .foregroundStyle(inkColor)
                    .lineLimit(titleLineLimit)
                    .minimumScaleFactor(0.85)
                if showRoom {
                    Text(resolved.room)
                        .font(.system(size: 8, weight: .regular))
                        .monospacedDigit()
                        .foregroundStyle(inkColor.opacity(0.82))
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, hPad)
        .padding(.vertical, vPad)
        .frame(width: width, height: height, alignment: blockAlignment)
        .clipped()
        .background {
            if isAccented {
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(Color.primary.opacity(resolved.isFinished ? 0.05 : 0.11))
                    .overlay(
                        RoundedRectangle(cornerRadius: corner, style: .continuous)
                            .strokeBorder(Color.primary.opacity(resolved.isFinished ? 0.14 : 0.28), lineWidth: 0.75)
                    )
            } else {
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(pair.surfaceColor(isFinished: resolved.isFinished))
            }
        }
        .overlay {
            if case .ongoing(let progress) = resolved.phase {
                if isAccented {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(Color.primary.opacity(0.14))
                } else {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .fill(pair.progressWipeGradient(progress: progress))
                }
            }
        }
        .offset(x: x, y: y)
        .widgetAccentable(resolved.isExam)
    }

    private struct SlotPresentation {
        let title: String
        let room: String
        let colorIndex: Int
        let phase: TimePhase
        let isFinished: Bool
        let isExam: Bool
    }

    private func resolveSlotPresentation(_ slot: TimetableSlotFrame) -> SlotPresentation {
        switch slot.kind {
        case .course(let occ):
            let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: entry.localNow)
            let isExam = occ.course.isExam
            let title = isExam ? WidgetFormat.splitExamDisplay(occ.course.name).title : occ.course.name
            return SlotPresentation(
                title: title,
                room: occ.room,
                colorIndex: occ.course.colorIndex,
                phase: phase,
                isFinished: phase.isFinished,
                isExam: isExam
            )
        case .taskBlock(let task, let start, let end):
            let phase = CourseProgress.phase(start: start, end: end, now: entry.localNow)
            let isExam = task.isExam
            let title = isExam ? WidgetFormat.splitExamDisplay(task.title).title : task.title
            // Deterministic color resolution matching TimetableLayout (never random hashValue).
            let colorIndex = slot.listColorIndex ?? (isExam ? 2 : WidgetFormat.stableColorIndex(for: task.id))
            let loc = (task.extra["location"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return SlotPresentation(
                title: title,
                room: loc,
                colorIndex: colorIndex,
                phase: task.done ? .finished : phase,
                isFinished: task.done || phase.isFinished,
                isExam: isExam
            )
        case .event(let ev):
            let phase = CourseProgress.phase(start: ev.start, end: ev.end, now: entry.localNow)
            return SlotPresentation(
                title: ev.event.title,
                room: ev.event.location,
                colorIndex: ev.event.colorIndex,
                phase: phase,
                isFinished: phase.isFinished,
                isExam: ev.event.kind == .EXAM
            )
        }
    }

    private var emptyState: some View {
        Text("导入课表后自动显示")
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
            .padding(WidgetMetrics.editorialPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}
