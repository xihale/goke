import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

/// Minimalist Mint Paper Next Class Countdown Widget:
/// Supports Home Screen (Small / Medium / Large) and Lock Screen
/// (`.accessoryRectangular`, `.accessoryCircular`, `.accessoryInline`).
struct NextClassWidget: Widget {
    static let kind = "app.goke.widget.nextClass"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: WidgetSnapshotProvider()) { entry in
            NextClassWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.theme.paper
                }
        }
        .contentMarginsDisabled()
        .configurationDisplayName("下节课程")
        .description("实时倒计时提醒下一节或进行中的课程与教室地点。")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .systemLarge,
            .accessoryRectangular,
            .accessoryCircular,
            .accessoryInline,
        ])
    }
}

// MARK: - Adaptive View

private struct NextClassWidgetView: View {
    let entry: GokeWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode

    private var theme: GokeTheme { entry.theme }
    private var isAccented: Bool { WidgetFormat.isAccentedMode(renderingMode) }

    private var deepLinkURL: URL {
        if let occ = entry.nextUp?.occurrence {
            return WidgetDeepLink.course(occ.course.id)
        }
        return WidgetDeepLink.timetable
    }

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                smallView
            case .systemMedium:
                mediumView
            case .systemLarge:
                largeView
            case .accessoryRectangular:
                accessoryRectangularView
            case .accessoryCircular:
                accessoryCircularView
            case .accessoryInline:
                accessoryInlineView
            default:
                smallView
            }
        }
        .widgetURL(deepLinkURL)
    }

    // MARK: - Home Screen Small (2×2 Editorial Focus — 141×141 inner canvas)

    private var smallView: some View {
        Group {
            if let nextUp = entry.nextUp {
                let occ = nextUp.occurrence
                let pair = theme.courseColor(index: occ.course.colorIndex)
                let inProgress = entry.isInProgress(occ)
                let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: entry.localNow)
                let isExam = occ.course.isExam
                let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                let displayTitle = examDisplay?.title ?? occ.course.name
                let venueLine = isExam
                    ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                    : occ.room
                let statusPrefix = quietStatusPrefix(occ: occ, inProgress: inProgress, isExam: isExam)
                let headerMeta: String = if !inProgress, let badge = examDisplay?.badgeShort {
                    "\(badge) · \(statusPrefix) · \(WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod))"
                } else {
                    "\(statusPrefix) · \(WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod))"
                }
                let dotColor = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor)
                let accentInk = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.foreground)

                VStack(alignment: .leading, spacing: 0) {
                    // Top row: botanical dot + quiet status & periods
                    HStack(spacing: 5) {
                        Circle()
                            .fill(dotColor)
                            .frame(width: 7, height: 7)
                            .widgetAccentable()
                        Text(headerMeta)
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }

                    Spacer().frame(height: 8)

                    // Flush left-aligned course title + venue
                    VStack(alignment: .leading, spacing: 3) {
                        Text(displayTitle)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(2)

                        if !venueLine.isEmpty {
                            Text(venueLine)
                                .font(.system(size: 12, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                .lineLimit(1)
                        }
                    }

                    Spacer(minLength: 6)

                    // Bottom countdown: caption stacked ABOVE greedy timerInterval so caption never floats right
                    VStack(alignment: .leading, spacing: 4) {
                        VStack(alignment: .leading, spacing: 2) {
                            if occ.date == entry.localNow.date {
                                Text(countdownCaption(inProgress: inProgress, isExam: isExam))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                    .lineLimit(1)
                            }
                            countdownView(for: occ, inProgress: inProgress)
                                .font(.system(size: 19, weight: .bold, design: .monospaced))
                                .monospacedDigit()
                                .foregroundStyle(accentInk)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .widgetAccentable()
                        }

                        if case .ongoing(let progress) = phase {
                            thinProgressBar(progress: progress, tint: dotColor, track: pair.background)
                        }
                    }
                }
                .padding(WidgetMetrics.editorialPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                emptyHomeView
                    .padding(WidgetMetrics.editorialPadding)
            }
        }
    }

    // MARK: - Home Screen Medium (4×2 Full-Width Unified Flow — 332×141 inner canvas)

    private var mediumView: some View {
        Group {
            if let nextUp = entry.nextUp {
                let occ = nextUp.occurrence
                let pair = theme.courseColor(index: occ.course.colorIndex)
                let inProgress = entry.isInProgress(occ)
                let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: entry.localNow)
                let isExam = occ.course.isExam
                let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                let displayTitle = examDisplay?.title ?? occ.course.name
                let venueLine = isExam
                    ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                    : occ.room
                let following = Array(entry.upcomingCourses(limit: 3).filter { $0.id != occ.id }.prefix(2))
                let dotColor = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor)
                let accentInk = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.foreground)

                VStack(alignment: .leading, spacing: 0) {
                    // Two-column hero: left metadata+title, right fixed-width countdown with layoutPriority(1)
                    HStack(alignment: .center, spacing: 10) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(alignment: .center, spacing: 5) {
                                Circle()
                                    .fill(dotColor)
                                    .frame(width: 7, height: 7)
                                    .widgetAccentable()

                                if let examDisplay {
                                    Text(examDisplay.badgeShort)
                                        .font(.system(size: 10.5, weight: .semibold))
                                        .foregroundStyle(isAccented ? Color.primary : theme.tertiary)
                                        .widgetAccentable()
                                }

                                Text(mediumMetaLine(occ: occ, inProgress: inProgress, venue: venueLine))
                                    .font(.system(size: 11, weight: .medium))
                                    .monospacedDigit()
                                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                    .lineLimit(1)
                            }

                            Text(displayTitle)
                                .font(.system(size: 17, weight: .bold))
                                .foregroundStyle(isAccented ? Color.primary : theme.ink)
                                .lineLimit(following.isEmpty ? 2 : 1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        VStack(alignment: .trailing, spacing: 1) {
                            countdownView(for: occ, inProgress: inProgress)
                                .font(.system(size: 17, weight: .bold, design: .monospaced))
                                .monospacedDigit()
                                .foregroundStyle(accentInk)
                                .multilineTextAlignment(.trailing)
                                .lineLimit(1)
                                .minimumScaleFactor(0.75)
                                .widgetAccentable()

                            if occ.date == entry.localNow.date {
                                Text(countdownCaption(inProgress: inProgress, isExam: isExam))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                    .lineLimit(1)
                            }
                        }
                        .frame(width: 84, alignment: .trailing)
                        .layoutPriority(1)
                    }

                    if case .ongoing(let progress) = phase {
                        thinProgressBar(progress: progress, tint: dotColor, track: pair.background)
                            .padding(.top, 5)
                    }

                    if !following.isEmpty {
                        Spacer().frame(height: 8)
                        Rectangle()
                            .fill(isAccented ? Color.primary.opacity(0.18) : theme.outlineVariant.opacity(0.6))
                            .frame(height: 0.5)
                        Spacer().frame(height: 7)

                        VStack(alignment: .leading, spacing: 5) {
                            ForEach(following) { item in
                                let itemPair = theme.courseColor(index: item.course.colorIndex)
                                let itemExam = item.course.isExam ? WidgetFormat.splitExamDisplay(item.course.name) : nil
                                let timeLabel = item.date == entry.localNow.date
                                    ? WidgetFormat.hhmm(item.start.time)
                                    : "\(WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: item.date)) \(WidgetFormat.hhmm(item.start.time))"
                                Link(destination: WidgetDeepLink.course(item.course.id)) {
                                    HStack(spacing: 6) {
                                        Circle()
                                            .fill(isAccented ? Color.primary.opacity(0.75) : (item.course.isExam ? theme.tertiary : itemPair.barColor))
                                            .frame(width: 6, height: 6)
                                            .widgetAccentable()
                                        Text(itemExam?.title ?? item.course.name)
                                            .font(.system(size: 12.5, weight: .medium))
                                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                                            .lineLimit(1)
                                            .layoutPriority(1)
                                        if !item.room.isEmpty {
                                            Text(item.room)
                                                .font(.system(size: 11))
                                                .monospacedDigit()
                                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                                .lineLimit(1)
                                        }
                                        Spacer(minLength: 4)
                                        Text(timeLabel)
                                            .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                                            .monospacedDigit()
                                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(minLength: 0)
                }
                .padding(WidgetMetrics.editorialPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                emptyHomeView
                    .padding(WidgetMetrics.editorialPadding)
            }
        }
    }

    // MARK: - Home Screen Large (4×4 Calm Hero + Flat Agenda List — 332×348 inner canvas)

    private var largeView: some View {
        Group {
            if let nextUp = entry.nextUp {
                let occ = nextUp.occurrence
                let pair = theme.courseColor(index: occ.course.colorIndex)
                let inProgress = entry.isInProgress(occ)
                let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: entry.localNow)
                let isExam = occ.course.isExam
                let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                let displayTitle = examDisplay?.title ?? occ.course.name
                let venueLine = isExam
                    ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                    : occ.room
                let upcomingStack = Array(
                    entry.upcomingCourses(limit: 5).filter { $0.id != occ.id }.prefix(4)
                )
                let dotColor = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.barColor)
                let accentInk = isAccented ? Color.primary : (isExam ? theme.tertiary : pair.foreground)

                VStack(alignment: .leading, spacing: 0) {
                    // Concentric tonal hero (r=10 inside 14pt outer padding, edges flush with list below)
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(dotColor)
                                .frame(width: 7, height: 7)
                                .widgetAccentable()
                            Text("\(quietStatusPrefix(occ: occ, inProgress: inProgress, isExam: isExam)) · \(WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod))")
                                .font(.system(size: 11, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                .lineLimit(1)
                            Spacer(minLength: 6)
                            countdownView(for: occ, inProgress: inProgress)
                                .font(.system(size: 14.5, weight: .bold, design: .monospaced))
                                .monospacedDigit()
                                .foregroundStyle(accentInk)
                                .multilineTextAlignment(.trailing)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                                .layoutPriority(1)
                                .widgetAccentable()
                        }

                        Text(displayTitle)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(2)

                        let metaParts = [
                            WidgetFormat.timeRange(start: occ.start.time, end: occ.end.time),
                            venueLine,
                            entry.settings.showTeacher ? occ.course.teacher : "",
                        ].filter { !$0.isEmpty }
                        Text(metaParts.joined(separator: " · "))
                            .font(.system(size: 11.5, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                            .lineLimit(1)

                        if case .ongoing(let progress) = phase {
                            thinProgressBar(progress: progress, tint: dotColor, track: pair.background)
                                .padding(.top, 2)
                        }

                        // Homework cue lives INSIDE the hero card so it visually belongs to the hero course
                        if let firstTask = nextUp.tasks.first {
                            Rectangle()
                                .fill(isAccented ? Color.primary.opacity(0.14) : theme.outlineVariant.opacity(0.45))
                                .frame(height: 0.5)
                                .padding(.top, 3)

                            Link(destination: WidgetDeepLink.todoItem(firstTask.id)) {
                                HStack(spacing: 5) {
                                    Text("本课 \(nextUp.tasks.count) 项作业")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(isAccented ? Color.primary : theme.accent)
                                        .layoutPriority(1)
                                        .widgetAccentable()
                                    Text("· \(firstTask.title)")
                                        .font(.system(size: 11))
                                        .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                                        .lineLimit(1)
                                    Spacer(minLength: 0)
                                }
                                .padding(.top, 1)
                            }
                        }
                    }
                    .padding(.horizontal, 11)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: WidgetMetrics.cardRadius, style: .continuous)
                            .fill(isAccented ? Color.primary.opacity(0.08) : pair.background.opacity(0.45))
                    )

                    // Flat upcoming rows directly on paper
                    if !upcomingStack.isEmpty {
                        Spacer().frame(height: 12)
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(upcomingStack) { item in
                                flatUpcomingRow(item)
                            }
                        }
                    }

                    Spacer(minLength: 0)
                }
                .padding(WidgetMetrics.editorialPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                emptyHomeView
                    .padding(WidgetMetrics.editorialPadding)
            }
        }
    }

    private func flatUpcomingRow(_ occ: CourseOccurrence) -> some View {
        let pair = theme.courseColor(index: occ.course.colorIndex)
        let isExam = occ.course.isExam
        let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
        let title = examDisplay?.title ?? occ.course.name
        let venue = isExam
            ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
            : occ.room
        let isToday = occ.date == entry.localNow.date

        return Link(destination: WidgetDeepLink.course(occ.course.id)) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(WidgetFormat.hhmm(occ.start.time))
                        .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(isAccented ? Color.primary : theme.ink)
                    if !isToday {
                        Text(WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: occ.date))
                            .font(.system(size: 9.5, weight: .medium))
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                    }
                }
                .frame(width: WidgetMetrics.timeColumnWidth, alignment: .leading)

                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .fill(isAccented ? Color.primary.opacity(0.7) : (isExam ? theme.tertiary : pair.barColor))
                    .frame(width: 3, height: venue.isEmpty ? 16 : 26)
                    .widgetAccentable()

                VStack(alignment: .leading, spacing: 1.5) {
                    HStack(spacing: 4) {
                        if let examDisplay {
                            Text(examDisplay.badgeShort)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(isAccented ? Color.primary : theme.tertiary)
                                .widgetAccentable()
                        }
                        Text(title)
                            .font(.system(size: 13.5, weight: .semibold))
                            .foregroundStyle(isAccented ? Color.primary : theme.ink)
                            .lineLimit(1)
                    }
                    if !venue.isEmpty {
                        Text(venue)
                            .font(.system(size: 11))
                            .monospacedDigit()
                            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: - Helpers

    private func countdownView(for occ: CourseOccurrence, inProgress: Bool) -> Text {
        if inProgress {
            return WidgetFormat.countdownText(
                start: occ.start.toDate(),
                end: occ.end.toDate(),
                fallback: "0:00"
            )
        }
        if occ.date == entry.localNow.date {
            return WidgetFormat.countdownText(
                start: entry.date,
                end: occ.start.toDate(),
                fallback: "0:00"
            )
        }
        return Text(WidgetFormat.hhmm(occ.start.time))
    }

    private func thinProgressBar(progress: Double, tint: Color, track: Color) -> some View {
        let clamped = min(max(progress, 0.04), 1.0)
        return GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(isAccented ? Color.primary.opacity(0.18) : track.opacity(0.65))
                Capsule()
                    .fill(tint)
                    .frame(width: max(4, geo.size.width * CGFloat(clamped)))
                    .widgetAccentable()
            }
        }
        .frame(height: 3)
    }

    private func quietStatusPrefix(occ: CourseOccurrence, inProgress: Bool, isExam: Bool) -> String {
        if inProgress { return isExam ? "考试中" : "上课中" }
        if occ.date == entry.localNow.date {
            return WidgetFormat.hhmm(occ.start.time)
        }
        return WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: occ.date)
    }

    private func mediumMetaLine(occ: CourseOccurrence, inProgress: Bool, venue: String) -> String {
        let timeStr = inProgress
            ? (occ.course.isExam ? "考试中 · \(WidgetFormat.hhmm(occ.end.time))结束" : "上课中 · \(WidgetFormat.hhmm(occ.end.time))下课")
            : (occ.date == entry.localNow.date
                ? WidgetFormat.timeRange(start: occ.start.time, end: occ.end.time)
                : "\(WidgetFormat.relativeDayLabel(from: entry.localNow.date, to: occ.date)) \(WidgetFormat.hhmm(occ.start.time))")
        return venue.isEmpty ? timeStr : "\(timeStr) · \(venue)"
    }

    private func countdownCaption(inProgress: Bool, isExam: Bool) -> String {
        if isExam {
            return inProgress ? "距交卷" : "距开考"
        }
        return inProgress ? "距下课" : "距上课"
    }

    private var emptyHomeView: some View {
        Text(entry.hasFinishedAllTodayCourses ? "今日课程已结束" : "今日无课")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(isAccented ? Color.secondary : theme.muted)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }

    // MARK: - Lock Screen Accessories

    private var accessoryRectangularView: some View {
        Group {
            if let occ = entry.nextUp?.occurrence {
                let inProgress = entry.isInProgress(occ)
                let isExam = occ.course.isExam
                let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                let displayTitle = examDisplay?.title ?? occ.course.name
                let venueLine = isExam
                    ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                    : occ.room

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(inProgress ? (isExam ? "考试中" : "上课中") : (examDisplay?.badgeShort ?? WidgetFormat.hhmm(occ.start.time)))
                            .font(.system(size: 11, weight: .semibold))
                            .monospacedDigit()
                            .lineLimit(1)
                            .layoutPriority(1)
                        Text("·")
                            .font(.system(size: 11))
                            .layoutPriority(1)
                        if occ.date == entry.localNow.date {
                            countdownView(for: occ, inProgress: inProgress)
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .monospacedDigit()
                                .lineLimit(1)
                                .frame(minWidth: 0)
                        } else {
                            Text(WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod))
                                .font(.system(size: 11, weight: .medium))
                                .monospacedDigit()
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .widgetAccentable()

                    Text(displayTitle)
                        .font(.system(size: 13.5, weight: .bold))
                        .lineLimit(1)

                    Text(venueLine.isEmpty ? WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod) : venueLine)
                        .font(.system(size: 11))
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    Text("课表")
                        .font(.system(size: 11, weight: .semibold))
                        .widgetAccentable()
                    Text(entry.hasFinishedAllTodayCourses ? "今日课程已结束" : "近期无课")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var accessoryCircularView: some View {
        GeometryReader { geo in
            let metrics = WidgetCircleMetrics(size: geo.size)
            let d = metrics.diameter
            ZStack {
                AccessoryWidgetBackground()
                if let occ = entry.nextUp?.occurrence {
                    let inProgress = entry.isInProgress(occ)
                    let phase = CourseProgress.phase(start: occ.start, end: occ.end, now: entry.localNow)
                    let isExam = occ.course.isExam

                    if case .ongoing(let progress) = phase {
                        Circle()
                            .stroke(.primary.opacity(0.22), lineWidth: 2.5)
                            .padding(2)
                        Circle()
                            .trim(from: 0, to: min(max(progress, 0.04), 1.0))
                            .stroke(.primary, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .padding(2)
                            .widgetAccentable()
                    } else if isExam {
                        Circle()
                            .stroke(.primary.opacity(0.45), lineWidth: 1.5)
                            .padding(2)
                            .widgetAccentable()
                    }

                    VStack(spacing: 1) {
                        Text(WidgetFormat.hhmm(inProgress ? occ.end.time : occ.start.time))
                            .font(.system(size: d < 48 ? 11.5 : 13, weight: .bold, design: .monospaced))
                            .monospacedDigit()
                            .minimumScaleFactor(0.75)
                            .lineLimit(1)
                            .widgetAccentable()
                        Text(isExam ? WidgetFormat.splitExamDisplay(occ.course.name).badgeShort : WidgetFormat.periodRange(start: occ.startPeriod, end: occ.endPeriod))
                            .font(.system(size: 9, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .padding(4)
                } else {
                    Text("无课")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: d, height: d)
            .aspectRatio(1, contentMode: .fit)
            .clipShape(Circle())
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
    }

    private var accessoryInlineView: some View {
        Group {
            if let occ = entry.nextUp?.occurrence {
                let isExam = occ.course.isExam
                let examDisplay = isExam ? WidgetFormat.splitExamDisplay(occ.course.name) : nil
                let displayTitle = examDisplay?.title ?? occ.course.name
                let prefix = entry.isInProgress(occ)
                    ? (isExam ? "考试中" : "上课中")
                    : (examDisplay != nil ? "\(examDisplay!.badgeShort) \(WidgetFormat.hhmm(occ.start.time))" : WidgetFormat.hhmm(occ.start.time))
                let venue = isExam
                    ? WidgetFormat.examLocationLine(room: occ.room, note: occ.course.note)
                    : occ.room
                let roomPart = venue.isEmpty ? "" : " · \(venue)"
                Label("\(prefix) \(displayTitle)\(roomPart)", systemImage: isExam ? "pencil.and.ruler.fill" : "book.closed.fill")
            } else {
                Label(
                    entry.hasFinishedAllTodayCourses ? "今日课程已结束" : "近期无课",
                    systemImage: entry.hasFinishedAllTodayCourses ? "checkmark.circle" : "calendar"
                )
            }
        }
    }
}
