import ActivityKit
import GokeCoreModel
import SwiftUI
import WidgetKit
import GokeShared

/// ActivityKit Live Activity & Dynamic Island UI for ongoing and upcoming Goke classes.
/// Forces `.colorScheme(.dark)` and bright dark-surface botanical tokens on all Dynamic Island
/// regions so text/countdowns stay crisp on the pure-black island even when iOS is in Light Mode.
@available(iOS 16.2, *)
struct GokeClassLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: GokeClassActivityAttributes.self) { context in
            ClassLiveActivityLockScreenView(
                attributes: context.attributes,
                state: context.state
            )
            .widgetURL(WidgetDeepLink.course(context.attributes.courseId))
        } dynamicIsland: { context in
            let pair = Self.coursePair(for: context.attributes.colorIndex)
            let state = context.state
            let attributes = context.attributes
            let phase = Self.effectivePhase(state: state)
            let isExam = Self.isExam(attributes)
            let venueText = Self.formattedRoom(state.room, isExam: isExam)
            let brightTint = pair.darkBarColor
            let brightText = pair.darkForeground

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(brightTint)
                            .frame(width: 7, height: 7)
                        Text("\(Self.phaseLabel(phase, isExam: isExam)) · \(attributes.periodRangeText)")
                            .font(.system(size: 11, weight: .semibold))
                            .monospacedDigit()
                            .foregroundStyle(brightText)
                            .lineLimit(1)
                    }
                    .environment(\.colorScheme, .dark)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 1) {
                        Self.countdownText(state: state, phase: phase)
                            .font(.system(size: 15, weight: .bold, design: .monospaced))
                            .foregroundStyle(brightText)
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Text(Self.countdownCaption(phase: phase, isExam: isExam))
                            .font(.system(size: 9.5, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                    .environment(\.colorScheme, .dark)
                }

                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(attributes.courseName)
                            .font(.system(size: 15.5, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        if !venueText.isEmpty || !attributes.teacher.isEmpty {
                            let meta = [venueText, attributes.teacher]
                                .filter { !$0.isEmpty }
                                .joined(separator: " · ")
                            Text(meta)
                                .font(.system(size: 11.5, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .environment(\.colorScheme, .dark)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        if phase == .inProgress, state.endDate > state.startDate {
                            ProgressView(
                                timerInterval: state.startDate...state.endDate,
                                countsDown: false
                            ) {
                                EmptyView()
                            } currentValueLabel: {
                                EmptyView()
                            }
                            .tint(brightTint)
                        }

                        if let nextName = state.nextCourseName, !nextName.isEmpty {
                            let nextRoom = state.nextCourseRoom?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                            Text("下节：\(nextName)\(nextRoom.isEmpty ? "" : " · \(nextRoom)")")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .environment(\.colorScheme, .dark)
                }
            } compactLeading: {
                HStack(spacing: 5) {
                    Circle()
                        .fill(brightTint)
                        .frame(width: 6, height: 6)
                    Text(String(attributes.courseName.prefix(4)))
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
                .environment(\.colorScheme, .dark)
            } compactTrailing: {
                Self.countdownText(state: state, phase: phase)
                    .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                    .foregroundStyle(brightText)
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .frame(maxWidth: 64, alignment: .trailing)
                    .environment(\.colorScheme, .dark)
            } minimal: {
                Image(systemName: Self.phaseIcon(phase))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(brightTint)
                    .environment(\.colorScheme, .dark)
            }
            .widgetURL(WidgetDeepLink.course(attributes.courseId))
            .keylineTint(brightTint)
        }
    }

    // MARK: - Helpers

    static func coursePair(for colorIndex: Int) -> CourseColorPair {
        let colors = CourseColorPair.botanical
        let normalized = ((colorIndex % colors.count) + colors.count) % colors.count
        return colors[normalized]
    }

    static func isExam(_ attributes: GokeClassActivityAttributes) -> Bool {
        attributes.isExam || ExamTextHelper.isExamRemark(attributes.courseName)
    }

    /// Derives the effective display phase from timestamps so an `.upcoming` activity never stays
    /// frozen at `0:00` once `startDate` has passed and iOS re-evaluates the view.
    static func effectivePhase(state: GokeClassActivityState, now: Date = Date.now) -> ClassActivityPhase {
        if state.phase == .ended || now >= state.endDate {
            return .ended
        }
        if now >= state.startDate && now < state.endDate {
            return .inProgress
        }
        return state.phase
    }

    static func formattedRoom(_ rawRoom: String, isExam: Bool) -> String {
        let trimmed = rawRoom.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        if isExam, let seat = ExamTextHelper.extractExamSeat(from: trimmed) {
            return trimmed.replacingOccurrences(of: "座位号 \(seat)", with: "座位 \(seat)")
        }
        return trimmed
    }

    static func phaseIcon(_ phase: ClassActivityPhase) -> String {
        switch phase {
        case .upcoming: "clock.fill"
        case .inProgress: "graduationcap.fill"
        case .ended: "checkmark.circle.fill"
        }
    }

    static func phaseLabel(_ phase: ClassActivityPhase, isExam: Bool = false) -> String {
        switch phase {
        case .upcoming: isExam ? "即将开考" : "即将上课"
        case .inProgress: isExam ? "考试中" : "上课中"
        case .ended: isExam ? "已交卷" : "已下课"
        }
    }

    static func countdownCaption(phase: ClassActivityPhase, isExam: Bool) -> String {
        switch phase {
        case .upcoming: isExam ? "距开考" : "距上课"
        case .inProgress: isExam ? "距交卷" : "距下课"
        case .ended: isExam ? "已结束" : "已下课"
        }
    }

    @ViewBuilder
    static func countdownText(state: GokeClassActivityState, phase: ClassActivityPhase) -> some View {
        switch phase {
        case .upcoming:
            if state.startDate > Date.now {
                Text(timerInterval: Date.now...state.startDate, countsDown: true)
            } else if state.endDate > state.startDate {
                Text(timerInterval: state.startDate...state.endDate, countsDown: true)
            } else {
                Text("即将开始")
            }
        case .inProgress:
            if state.endDate > state.startDate {
                Text(timerInterval: state.startDate...state.endDate, countsDown: true)
            } else {
                Text("进行中")
            }
        case .ended:
            Text("已下课")
        }
    }
}

// MARK: - Lock Screen / StandBy Banner View

@available(iOS 16.2, *)
private struct ClassLiveActivityLockScreenView: View {
    let attributes: GokeClassActivityAttributes
    let state: GokeClassActivityState

    private var theme: GokeTheme {
        let settings = AppGroupContainer.shared.readWidgetSnapshot()?.settings ?? .default
        return GokeTheme.resolve(settings: settings)
    }

    private var pair: CourseColorPair {
        GokeClassLiveActivity.coursePair(for: attributes.colorIndex)
    }

    var body: some View {
        let phase = GokeClassLiveActivity.effectivePhase(state: state)
        let isExam = GokeClassLiveActivity.isExam(attributes)
        let venueText = GokeClassLiveActivity.formattedRoom(state.room, isExam: isExam)

        VStack(alignment: .leading, spacing: 8) {
            // Row 1: quiet botanical dot + status/period + right-aligned bold countdown
            HStack(alignment: .center, spacing: 6) {
                Circle()
                    .fill(isExam ? theme.tertiary : pair.barColor)
                    .frame(width: 7, height: 7)

                Text("\(GokeClassLiveActivity.phaseLabel(phase, isExam: isExam)) · \(attributes.periodRangeText)")
                    .font(.system(size: 11.5, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
                    .layoutPriority(1)

                Spacer(minLength: 6)

                GokeClassLiveActivity.countdownText(state: state, phase: phase)
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundStyle(isExam ? theme.tertiary : pair.foreground)
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }

            // Row 2: bold course title + clean trailing venue
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(attributes.courseName)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                    .layoutPriority(1)

                Spacer(minLength: 4)

                if !venueText.isEmpty {
                    Text(venueText)
                        .font(.system(size: 12, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                }
            }

            // Row 3: thin progress bar only while ongoing
            if phase == .inProgress, state.endDate > state.startDate {
                ProgressView(
                    timerInterval: state.startDate...state.endDate,
                    countsDown: false
                ) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(isExam ? theme.tertiary : pair.barColor)
            }

            // Optional quiet footer for next class only
            if let nextName = state.nextCourseName, !nextName.isEmpty {
                let nextRoom = state.nextCourseRoom?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                Text("下节：\(nextName)\(nextRoom.isEmpty ? "" : " · \(nextRoom)")")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .activityBackgroundTint(theme.paper)
        .activitySystemActionForegroundColor(theme.accent)
    }
}
