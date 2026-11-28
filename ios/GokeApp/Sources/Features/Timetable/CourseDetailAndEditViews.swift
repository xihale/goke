import Foundation
import GokeCore
import SwiftUI
import GokeShared
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Mint Paper Form Primitives (File-Scoped)

private struct CoursePaperSection<Content: View>: View {
    let title: String?
    let footer: String?
    let footerColor: Color?
    let content: Content

    @Environment(\.gokeTheme) private var theme

    init(
        _ title: String? = nil,
        footer: String? = nil,
        footerColor: Color? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.footer = footer
        self.footerColor = footerColor
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            if let title, !title.isEmpty {
                Text(title)
                    .font(GokeTypography.labelLarge)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .padding(.horizontal, 4)
            }

            VStack(alignment: .leading, spacing: 0) {
                content
            }
            .padding(GokeSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                    .fill(theme.surface)
            )

            if let footer, !footer.isEmpty {
                Text(footer)
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(footerColor ?? theme.muted)
                    .padding(.horizontal, 4)
            }
        }
    }
}

private struct CoursePaperDivider: View {
    @Environment(\.gokeTheme) private var theme

    var body: some View {
        Rectangle()
            .fill(theme.outlineVariant.opacity(0.45))
            .frame(height: 0.5)
            .padding(.vertical, GokeSpacing.sm)
    }
}

private struct CoursePaperStepperRow: View {
    let title: String
    let valueLabel: String
    @Binding var value: Int
    let range: ClosedRange<Int>

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    var body: some View {
        HStack(spacing: GokeSpacing.sm) {
            Text(title)
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.ink)

            Spacer(minLength: GokeSpacing.sm)

            HStack(spacing: 0) {
                Button {
                    if value > range.lowerBound {
                        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                            value -= 1
                        }
                    }
                } label: {
                    Image(systemName: "minus")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(value > range.lowerBound ? theme.ink : theme.muted.opacity(0.45))
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(value <= range.lowerBound)
                .accessibilityLabel("减少\(title)")

                Text(valueLabel)
                    .font(GokeTypography.labelLarge.weight(.semibold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .foregroundStyle(theme.ink)
                    .frame(minWidth: 48)

                Button {
                    if value < range.upperBound {
                        withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                            value += 1
                        }
                    }
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(value < range.upperBound ? theme.ink : theme.muted.opacity(0.45))
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(value >= range.upperBound)
                .accessibilityLabel("增加\(title)")
            }
            .background {
                Capsule()
                    .fill(theme.surfaceHighest)
                    .frame(height: 36)
            }
        }
    }
}

private struct CoursePaperLabeledRow: View {
    let label: String
    let value: String

    @Environment(\.gokeTheme) private var theme

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: GokeSpacing.md) {
            Text(label)
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.muted)
            Spacer(minLength: GokeSpacing.sm)
            Text(value)
                .font(GokeTypography.bodyMedium.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(theme.ink)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - 1. CourseDetailView

/// Native iOS HIG detail view for a single `Course` using `List(.insetGrouped)` with
/// a botanical header section, `LabeledContent` attributes, swipeable single-week exceptions,
/// and linked course tasks.
struct CourseDetailView: View {
    let courseId: String

    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var isRoomExpanded: Bool = false
    @State private var isAllNotesExpanded: Bool = false
    @State private var showDeleteConfirm: Bool = false
    @State private var showExceptionSheet: Bool = false

    init(courseId: String) {
        self.courseId = courseId
    }

    private var live: AppSnapshot { container.liveSnapshot }

    private var course: Course? {
        live.courses.first(where: { $0.id == courseId })
    }

    private var semester: Semester? {
        guard let course else { return live.currentSemester }
        return live.semesters.first(where: { $0.id == course.semesterId }) ?? live.currentSemester
    }

    private var exceptions: [CourseException] {
        live.exceptions
            .filter { $0.courseId == courseId }
            .sorted { $0.week < $1.week }
    }

    private var relatedTasks: [GokeTask] {
        guard let course else { return [] }
        return TaskLogic.topLevel(live.tasks)
            .filter { $0.courseId == course.id }
            .sorted { lhs, rhs in
                if lhs.done != rhs.done { return !lhs.done }
                if let ld = lhs.due, let rd = rhs.due, ld != rd { return ld < rd }
                return lhs.sortOrder < rhs.sortOrder
            }
    }

    var body: some View {
        Group {
            if let course {
                detailList(course: course)
            } else {
                GokeEmptyState(
                    systemImage: "book.closed",
                    title: "课程已不存在",
                    subtitle: "该课程可能已被删除或移除",
                    primaryActionTitle: "返回",
                    onPrimaryAction: { dismiss() }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(theme.paper.ignoresSafeArea())
            }
        }
        .navigationTitle("课程详情")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            if let course {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("编辑") {
                        router.push(.courseEdit(courseId: course.id))
                    }
                }
            }
        }
        .confirmationDialog("删除此课程？", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("移入回收站", role: .destructive) {
                if let course {
                    deleteCourse(course)
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("可在回收站恢复")
        }
        .sheet(isPresented: $showExceptionSheet) {
            if let course {
                CourseExceptionSheet(
                    course: course,
                    semester: semester
                )
                .gokePaperSheet(theme, detents: [.medium, .large])
            }
        }
    }

    private func detailList(course: Course) -> some View {
        let pair = theme.courseColor(index: course.colorIndex)
        let roomLocations = splitLocations(course.room)
        let periodSummary = periodRangeText(course: course, semester: semester)
        let weeksSummary = WeekSets.format(course.weeks)
        let activeWeek = semester.map { SemesterCalendar(semester: $0).currentWeek(today: .today()) }
        let parsedNotes = CourseNotes.parse(
            course.note,
            week: activeWeek,
            maxWeeks: semester?.totalWeeks ?? CourseNotes.defaultMaxWeeks
        )

        return ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                // Botanical Top Header Card
                VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                    HStack(alignment: .firstTextBaseline, spacing: GokeSpacing.sm) {
                        Text(course.name)
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(pair.foreground)

                        if course.isExam {
                            GokeBadge("考试", tone: .tertiary)
                        }
                    }

                    Text("\(TodayFormatters.weekdayLabel(course.dayOfWeek)) \(periodSummary)\(weeksSummary.isEmpty ? "" : " · \(weeksSummary)周")")
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(pair.foreground.opacity(0.86))

                    if !roomLocations.isEmpty {
                        if roomLocations.count > 2 {
                            Button {
                                withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                    isRoomExpanded.toggle()
                                }
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "mappin.and.ellipse")
                                        .font(.caption.weight(.medium))
                                    Text(isRoomExpanded ? "收起全部 \(roomLocations.count) 处地点" : "\(roomLocations[0]) 等 \(roomLocations.count) 处")
                                        .font(.footnote.weight(.medium))
                                        .monospacedDigit()
                                    Image(systemName: "chevron.down")
                                        .font(.caption2.weight(.semibold))
                                        .rotationEffect(.degrees(isRoomExpanded ? 180 : 0))
                                }
                                .foregroundStyle(pair.foreground)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background {
                                    Capsule().fill(pair.foreground.opacity(0.12))
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)

                            if isRoomExpanded {
                                VStack(alignment: .leading, spacing: 4) {
                                    ForEach(Array(roomLocations.enumerated()), id: \.offset) { idx, place in
                                        Text("\(idx + 1). \(place)")
                                            .font(.footnote)
                                            .monospacedDigit()
                                            .foregroundStyle(pair.foreground.opacity(0.88))
                                    }
                                }
                                .padding(.top, 2)
                                .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
                            }
                        } else {
                            Label(course.room, systemImage: "mappin.and.ellipse")
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(pair.foreground.opacity(0.9))
                        }
                    }
                }
                .padding(GokeSpacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                        .fill(pair.background)
                )

                // Course Attributes
                CoursePaperSection("课程信息") {
                    if !course.teacher.isEmpty {
                        CoursePaperLabeledRow(label: "教师", value: course.teacher)
                        CoursePaperDivider()
                    }

                    CoursePaperLabeledRow(
                        label: "时间",
                        value: periodTimeClockRange(course: course, semester: semester)
                    )
                    CoursePaperDivider()

                    if weeksSummary.isEmpty {
                        CoursePaperLabeledRow(label: "周次", value: "未设置")
                        CoursePaperDivider()
                    }

                    CoursePaperLabeledRow(label: "提醒", value: reminderDescription(course: course))

                    if !course.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        CoursePaperDivider()
                        courseNotesCard(parsed: parsedNotes, activeWeek: activeWeek)
                    }
                }

                // Single-Week Exceptions
                CoursePaperSection("单周调停课") {
                    ForEach(exceptions) { ex in
                        HStack(spacing: GokeSpacing.md) {
                            GokeBadge(
                                ex.type == .CANCEL ? "停课" : "调课",
                                tone: ex.type == .CANCEL ? .error : .secondary
                            )

                            VStack(alignment: .leading, spacing: 2) {
                                Text("原第 \(ex.week) 周")
                                    .font(GokeTypography.bodyMedium.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundStyle(theme.ink)

                                if ex.type == .RESCHEDULE {
                                    Text(rescheduleSummary(ex: ex, course: course))
                                        .font(.footnote)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.muted)
                                }
                            }

                            Spacer(minLength: GokeSpacing.xs)

                            Button {
                                GokeHaptics.destructiveConfirm()
                                removeException(ex, courseName: course.name)
                            } label: {
                                Text("恢复")
                                    .font(GokeTypography.labelMedium.weight(.semibold))
                                    .foregroundStyle(theme.error)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(theme.conflictBg, in: Capsule())
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                            .accessibilityLabel("恢复第 \(ex.week) 周原安排")
                        }
                        .padding(.vertical, 2)
                        CoursePaperDivider()
                    }

                    Button {
                        router.startTimetableReschedulePicking(courseId: course.id)
                        dismiss()
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Image(systemName: "calendar.day.timeline.left")
                                .font(.body)
                                .foregroundStyle(theme.accent)
                                .frame(width: 24)
                            Text("在课表上调课")
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(theme.muted.opacity(0.6))
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)

                    CoursePaperDivider()

                    Button {
                        showExceptionSheet = true
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Image(systemName: "calendar.badge.clock")
                                .font(.body)
                                .foregroundStyle(theme.accent)
                                .frame(width: 24)
                            Text("新增单周调停课")
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(theme.muted.opacity(0.6))
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }

                // Related Course Tasks
                CoursePaperSection("关联待办") {
                    ForEach(relatedTasks) { task in
                        HStack(alignment: .center, spacing: GokeSpacing.sm) {
                            GokeCheckBox(
                                isChecked: task.done,
                                priority: task.priority,
                                accessibilityLabel: task.done ? "取消完成 \(task.title)" : "完成 \(task.title)",
                                onToggle: { toggleTask(task) }
                            )

                            Button {
                                router.present(.todoEdit(taskId: task.id))
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(task.title)
                                        .font(GokeTypography.bodyLarge)
                                        .strikethrough(task.done, color: theme.muted)
                                        .foregroundStyle(task.done ? theme.muted : theme.ink)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)

                                    if task.dueBeforeNextClass {
                                        Text("下次上课前截止")
                                            .font(.footnote)
                                            .foregroundStyle(theme.accent)
                                    } else if let due = task.due {
                                        Text("截止 \(due.date.month)/\(due.date.day)\(task.dueHasTime ? " \(due.time.description)" : "")")
                                            .font(.footnote)
                                            .monospacedDigit()
                                            .foregroundStyle(theme.muted)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)
                        }
                        .opacity(task.done ? 0.5 : 1.0)
                        .animation(GokeMotion.press(reduceMotion: simplifyMotion), value: task.done)
                        .contextMenu {
                            Button(task.done ? "标为未完成" : "标为已完成", systemImage: task.done ? "arrow.uturn.backward" : "checkmark.circle") {
                                toggleTask(task)
                            }
                            Button("改期", systemImage: "calendar.badge.clock") {
                                router.present(.rescheduleTask(taskId: task.id))
                            }
                            Divider()
                            Button("删除", systemImage: "trash", role: .destructive) {
                                deleteTask(task)
                            }
                        }

                        CoursePaperDivider()
                    }

                    Button {
                        router.present(.todoEdit(courseId: course.id))
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Image(systemName: "plus.circle")
                                .font(.body)
                                .foregroundStyle(theme.accent)
                                .frame(width: 24)
                            Text("添加待办")
                                .font(GokeTypography.bodyLarge.weight(.medium))
                                .foregroundStyle(theme.accent)
                            Spacer()
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }

                // Destructive Delete
                Button {
                    showDeleteConfirm = true
                } label: {
                    Text("删除课程")
                        .font(GokeTypography.bodyLarge.weight(.medium))
                        .foregroundStyle(theme.error)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
                }
                .buttonStyle(.mintRow)
            }
            .padding(GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
        }
        .background(theme.paper.ignoresSafeArea())
    }

    private func splitLocations(_ raw: String) -> [String] {
        raw.split { $0 == "," || $0 == "，" || $0 == ";" || $0 == "；" || $0 == "/" || $0 == "、" }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func periodRangeText(course: Course, semester: Semester?) -> String {
        if course.startPeriod == course.endPeriod {
            return "第\(course.startPeriod)节"
        }
        return "第\(course.startPeriod)–\(course.endPeriod)节"
    }

    private func periodTimeClockRange(course: Course, semester: Semester?) -> String {
        let cal = semester.map { SemesterCalendar(semester: $0) }
        let startT = cal?.startTime(period: course.startPeriod)?.description
        let endT = cal?.endTime(period: course.endPeriod)?.description
        if let startT, let endT {
            return "\(startT)–\(endT)"
        }
        return periodRangeText(course: course, semester: semester)
    }

    @ViewBuilder
    private func courseNotesCard(parsed: CourseNotes.Parsed, activeWeek: Int?) -> some View {
        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            HStack(spacing: GokeSpacing.xs) {
                Text("课程备注")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(theme.muted)
                if !parsed.currentWeekNotes.isEmpty, let activeWeek {
                    GokeBadge("第 \(activeWeek) 周", tone: .accent)
                }
            }

            if !parsed.focusedNotes.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(parsed.focusedNotes.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.subheadline)
                            .foregroundStyle(theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(GokeSpacing.md)
                .background {
                    RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                        .fill(parsed.currentWeekNotes.isEmpty ? theme.surfaceHighest : theme.accentContainer.opacity(0.45))
                }
            } else if parsed.hasOtherWeeks {
                Text("本周无备注")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }

            if parsed.hasOtherWeeks {
                Button {
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        isAllNotesExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(isAllNotesExpanded ? "收起全部备注" : "查看全部备注（\(parsed.otherWeekLines.count) 条其他周）")
                            .font(.footnote.weight(.medium))
                            .monospacedDigit()
                        notesDisclosureChevron(expanded: isAllNotesExpanded)
                    }
                    .foregroundStyle(theme.accent)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)

                if isAllNotesExpanded {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(Array(parsed.otherWeekLines.enumerated()), id: \.offset) { _, line in
                            Text(line)
                                .font(.footnote)
                                .foregroundStyle(theme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.top, 2)
                    .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func notesDisclosureChevron(expanded: Bool) -> some View {
        Image(systemName: "chevron.down")
            .font(.caption2.weight(.semibold))
            .rotationEffect(.degrees(expanded ? 180 : 0))
            .animation(GokeMotion.snappy(reduceMotion: simplifyMotion), value: expanded)
    }

    private func reminderDescription(course: Course) -> String {
        let offsets = course.effectiveRemindMinutes
        if offsets.isEmpty {
            return course.remindMinutes == 0 ? "不提醒" : "跟随全局"
        }
        return GokeMultiReminderPicker.formatSummary(offsets, emptyLabel: "跟随全局", prefix: "课前")
    }

    private func rescheduleSummary(ex: CourseException, course: Course) -> String {
        let w = ex.newWeek ?? ex.week
        let d = ex.newDayOfWeek ?? course.dayOfWeek
        let sp = ex.newStartPeriod ?? course.startPeriod
        let ep = ex.newEndPeriod ?? course.endPeriod
        var parts = ["调至第 \(w) 周 \(TodayFormatters.weekdayLabel(d)) 第\(sp)–\(ep)节"]
        if let room = ex.newRoom, !room.isEmpty {
            parts.append(room)
        }
        return parts.joined(separator: " · ")
    }

    private func removeException(_ ex: CourseException, courseName: String) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs = ChangeSet(
            description: "恢复第 \(ex.week) 周「\(courseName)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.remove(before: .courseException(ex))]
        )
        Task { _ = await actions.commit(cs, label: "已恢复第 \(ex.week) 周安排") }
    }

    private func deleteCourse(_ course: Course) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = [.remove(before: .course(course))]
        for ex in container.liveSnapshot.exceptions where ex.courseId == course.id {
            changes.append(.remove(before: .courseException(ex)))
        }
        let cs = ChangeSet(
            description: "删除课程「\(course.name)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已删除「\(course.name)」")
            if ok { dismiss() }
        }
    }

    private func toggleTask(_ task: GokeTask) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        if task.done {
            let reopened = TaskLogic.reopen(task, nowMillis: nowMillis)
            let cs = ChangeSet(
                description: "恢复「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: [.modify(before: .task(task), after: .task(reopened))]
            )
            Task { _ = await actions.commit(cs, label: "已恢复「\(task.title)」") }
        } else {
            let (finished, next) = TaskLogic.complete(task, nowMillis: nowMillis, now: .now())
            var changes: [Change] = [.modify(before: .task(task), after: .task(finished))]
            if let next { changes.append(.add(after: .task(next))) }
            let cs = ChangeSet(
                description: "完成「\(task.title)」",
                source: .MANUAL,
                createdAt: nowMillis,
                changes: changes
            )
            Task { _ = await actions.commit(cs, label: "已完成「\(task.title)」") }
        }
    }

    private func deleteTask(_ task: GokeTask) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs = ChangeSet(
            description: "删除待办「\(task.title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.remove(before: .task(task))]
        )
        Task { _ = await actions.commit(cs, label: "已删除「\(task.title)」") }
    }
}

// MARK: - Single-Week Exception Editor Sheet

private struct CourseExceptionSheet: View {
    let course: Course
    let semester: Semester?

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var sourceWeek: Int = 1
    @State private var exceptionType: ExceptionType = .RESCHEDULE
    @State private var newWeek: Int = 1
    @State private var newDayOfWeek: Int = 1
    @State private var newStartPeriod: Int = 1
    @State private var newEndPeriod: Int = 2
    @State private var newRoom: String = ""
    @State private var didInit: Bool = false

    private var totalWeeks: Int { max(1, semester?.totalWeeks ?? 20) }
    private var periods: [PeriodTime] { semester?.periods.sorted { $0.index < $1.index } ?? GdutPeriods.default }
    private var selectableSourceWeeks: [Int] {
        course.weeks.isEmpty ? Array(1...totalWeeks) : course.weeks.sorted()
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    CoursePaperSection("原上课周次") {
                        HStack {
                            Text("原周次")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Picker("原周次", selection: $sourceWeek) {
                                ForEach(selectableSourceWeeks, id: \.self) { w in
                                    Text("第 \(w) 周").tag(w)
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(theme.accent)
                        }

                        CoursePaperDivider()

                        GokeSegmentedPill(
                            options: [(.RESCHEDULE, "单周调课"), (.CANCEL, "单周停课")],
                            selection: $exceptionType
                        )
                    }

                    if exceptionType == .RESCHEDULE {
                        CoursePaperSection("调课目标") {
                            HStack {
                                Text("新周次")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Picker("新周次", selection: $newWeek) {
                                    ForEach(1...totalWeeks, id: \.self) { w in
                                        Text("第 \(w) 周").tag(w)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(theme.accent)
                            }

                            CoursePaperDivider()

                            HStack {
                                Text("星期")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Picker("星期", selection: $newDayOfWeek) {
                                    ForEach(1...7, id: \.self) { d in
                                        Text(TodayFormatters.weekdayLabel(d)).tag(d)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(theme.accent)
                            }

                            CoursePaperDivider()

                            HStack {
                                Text("开始节次")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Picker("开始节次", selection: $newStartPeriod) {
                                    ForEach(periods) { p in
                                        Text("第 \(p.index) 节 (\(p.start.description))").tag(p.index)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(theme.accent)
                                .onChange(of: newStartPeriod) { _, nextStart in
                                    if newEndPeriod < nextStart {
                                        newEndPeriod = nextStart
                                    }
                                }
                            }

                            CoursePaperDivider()

                            HStack {
                                Text("结束节次")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.muted)
                                Spacer()
                                Picker("结束节次", selection: $newEndPeriod) {
                                    ForEach(periods.filter { $0.index >= newStartPeriod }) { p in
                                        Text("第 \(p.index) 节 (\(p.end.description))").tag(p.index)
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(theme.accent)
                            }

                            CoursePaperDivider()

                            TextField("新教室（留空则沿用原教室）", text: $newRoom)
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.ink)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                        }
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background(theme.paper.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("单周调停课")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        saveException()
                    }
                    .fontWeight(.semibold)
                }
            }
            .onAppear {
                guard !didInit else { return }
                didInit = true
                let defaultWeek = semester.map { SemesterCalendar(semester: $0).currentWeek(today: .today()) } ?? 1
                let matchedWeek = course.weeks.contains(defaultWeek) ? defaultWeek : (course.weeks.sorted().first ?? 1)
                sourceWeek = matchedWeek
                newWeek = matchedWeek
                newDayOfWeek = course.dayOfWeek
                newStartPeriod = course.startPeriod
                newEndPeriod = course.endPeriod
                newRoom = ""
            }
        }
    }

    private func saveException() {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let existing = container.liveSnapshot.exceptions.first {
            $0.courseId == course.id && $0.week == sourceWeek
        }
        let trimmedRoom = newRoom.trimmingCharacters(in: .whitespacesAndNewlines)
        let record = CourseException(
            id: existing?.id ?? UUID().uuidString.lowercased(),
            courseId: course.id,
            week: sourceWeek,
            type: exceptionType,
            newWeek: exceptionType == .RESCHEDULE ? newWeek : nil,
            newDayOfWeek: exceptionType == .RESCHEDULE ? newDayOfWeek : nil,
            newStartPeriod: exceptionType == .RESCHEDULE ? newStartPeriod : nil,
            newEndPeriod: exceptionType == .RESCHEDULE ? max(newStartPeriod, newEndPeriod) : nil,
            newRoom: exceptionType == .RESCHEDULE && !trimmedRoom.isEmpty ? trimmedRoom : nil,
            updatedAt: nowMillis
        )
        let change: Change = if let existing {
            .modify(before: .courseException(existing), after: .courseException(record))
        } else {
            .add(after: .courseException(record))
        }
        let cs = ChangeSet(
            description: exceptionType == .CANCEL
                ? "第 \(sourceWeek) 周停课「\(course.name)」"
                : "第 \(sourceWeek) 周调课「\(course.name)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [change]
        )
        Task {
            let ok = await actions.commit(cs, label: exceptionType == .CANCEL ? "已设置单周停课" : "已保存单周调课")
            if ok { dismiss() }
        }
    }
}

// MARK: - 2. CourseEditView

/// Native SwiftUI `Form` editor for creating and editing multi-slot `Course` records atomically
/// with live `GokeConflictCard` clash detection, interactive week-grid picker, and 8 botanical colors.
struct CourseEditView: View {
    let courseId: String?
    let initialDayOfWeek: Int?
    let initialStartPeriod: Int?
    let initialEndPeriod: Int?
    let initialWeek: Int?
    let isSheet: Bool

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private struct SlotDraft: Identifiable, Hashable {
        let id: String
        let base: Course?
        var dayOfWeek: Int
        var startPeriod: Int
        var endPeriod: Int
        var weeks: Set<Int>
        var room: String
        var remindMinutesList: [Int]
        var startTime: String?
        var endTime: String?
    }

    private struct CourseClashItem: Identifiable, Hashable {
        let id: String
        let title: String
        let detail: String
    }

    @State private var didLoad: Bool = false
    @State private var name: String = ""
    @State private var teacher: String = ""
    @State private var colorIndex: Int = 1
    @State private var kind: EventKind = .NORMAL
    @State private var remindPersistent: Bool = false
    @State private var note: String = ""
    @State private var slots: [SlotDraft] = []
    @State private var activeSlotIndex: Int = 0
    @State private var originalSiblings: [Course] = []
    @State private var showDeleteConfirm: Bool = false
    @FocusState private var isNameFocused: Bool

    init(
        courseId: String? = nil,
        initialDayOfWeek: Int? = nil,
        initialStartPeriod: Int? = nil,
        initialEndPeriod: Int? = nil,
        initialWeek: Int? = nil,
        isSheet: Bool = false
    ) {
        self.courseId = courseId
        self.initialDayOfWeek = initialDayOfWeek
        self.initialStartPeriod = initialStartPeriod
        self.initialEndPeriod = initialEndPeriod
        self.initialWeek = initialWeek
        self.isSheet = isSheet
    }

    private var existingCourse: Course? {
        guard let courseId else { return nil }
        return container.liveSnapshot.courses.first(where: { $0.id == courseId })
    }

    private var targetSemester: Semester? {
        if let existing = existingCourse {
            return container.liveSnapshot.semesters.first(where: { $0.id == existing.semesterId })
                ?? container.liveSnapshot.currentSemester
        }
        return container.liveSnapshot.currentSemester
    }

    private var periods: [PeriodTime] {
        (targetSemester?.periods ?? GdutPeriods.default).sorted { $0.index < $1.index }
    }

    private var totalWeeks: Int {
        max(1, targetSemester?.totalWeeks ?? 20)
    }

    private var safeActiveSlotIndex: Int {
        guard !slots.isEmpty else { return 0 }
        return min(max(0, activeSlotIndex), slots.count - 1)
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !slots.isEmpty
            && slots.allSatisfy { !$0.weeks.isEmpty }
    }

    private var conflicts: [CourseClashItem] {
        guard let sem = targetSemester, !slots.isEmpty else { return [] }
        let excludedIds = Set(originalSiblings.map(\.id))
        let otherCourses = container.liveSnapshot.courses.filter {
            $0.semesterId == sem.id && !excludedIds.contains($0.id)
        }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        var results: [CourseClashItem] = []

        for (idx, slot) in slots.enumerated() {
            let slotEnd = max(slot.startPeriod, slot.endPeriod)
            for other in otherCourses {
                guard other.dayOfWeek == slot.dayOfWeek,
                      other.startPeriod <= slotEnd,
                      other.endPeriod >= slot.startPeriod
                else { continue }
                let overlapWeeks = other.weeks.intersection(slot.weeks)
                guard !overlapWeeks.isEmpty else { continue }
                if kind == .EXAM, !other.isExam,
                   !trimmedName.isEmpty,
                   JxfwSyncMapper.isSameCourseForExam(trimmedName, other.name)
                {
                    continue
                }
                let spanText = other.startPeriod == other.endPeriod
                    ? "第\(other.startPeriod)节"
                    : "第\(other.startPeriod)–\(other.endPeriod)节"
                let weekText = WeekSets.format(overlapWeeks)
                results.append(
                    CourseClashItem(
                        id: "ext-\(idx)-\(other.id)",
                        title: other.name,
                        detail: "\(TodayFormatters.weekdayLabel(other.dayOfWeek)) \(spanText) · 第 \(weekText) 周"
                    )
                )
            }

            if idx + 1 < slots.count {
                for otherIdx in (idx + 1)..<slots.count {
                    let otherSlot = slots[otherIdx]
                    let otherEnd = max(otherSlot.startPeriod, otherSlot.endPeriod)
                    guard otherSlot.dayOfWeek == slot.dayOfWeek,
                          otherSlot.startPeriod <= slotEnd,
                          otherEnd >= slot.startPeriod
                    else { continue }
                    let overlapWeeks = otherSlot.weeks.intersection(slot.weeks)
                    guard !overlapWeeks.isEmpty else { continue }
                    let weekText = WeekSets.format(overlapWeeks)
                    results.append(
                        CourseClashItem(
                            id: "self-\(idx)-\(otherIdx)",
                            title: "本课程时段 \(idx + 1) 与时段 \(otherIdx + 1) 重叠",
                            detail: "\(TodayFormatters.weekdayLabel(slot.dayOfWeek)) · 第 \(weekText) 周"
                        )
                    )
                }
            }
        }
        return results
    }

    var body: some View {
        if isSheet {
            NavigationStack {
                editorForm
            }
            .gokePaperSheet(theme, detents: [.large])
        } else {
            editorForm
        }
    }

    private var editorForm: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                if !conflicts.isEmpty {
                    GokeConflictCard(title: "与已有课程时间冲突（\(conflicts.count) 处）") {
                        ForEach(conflicts.prefix(4)) { item in
                            Text("\(item.title)（\(item.detail)）")
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                        }
                        if conflicts.count > 4 {
                            Text("另有 \(conflicts.count - 4) 处冲突")
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }
                    }
                }

                CoursePaperSection(
                    "基本信息",
                    footer: slots.count > 1 ? "名称、教师与备注对全部时段生效" : nil
                ) {
                    VStack(spacing: GokeSpacing.sm) {
                        TextField("课程名称", text: $name)
                            .font(GokeTypography.bodyLarge.weight(.medium))
                            .foregroundStyle(theme.ink)
                            .focused($isNameFocused)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 11)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))

                        TextField("教师（选填）", text: $teacher)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                    }

                    CoursePaperDivider()

                    Toggle(isOn: Binding(
                        get: { kind == .EXAM },
                        set: { isExam in
                            kind = isExam ? .EXAM : .NORMAL
                            if isExam {
                                remindPersistent = true
                            }
                        }
                    )) {
                        Text("标记为考试")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    }
                    .tint(theme.accent)

                    if kind == .EXAM {
                        CoursePaperDivider()
                        Toggle(isOn: $remindPersistent) {
                            Text("考试持续提醒")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.ink)
                        }
                        .tint(theme.accent)
                    }
                }

                if !slots.isEmpty {
                    let idx = safeActiveSlotIndex
                    let currentSlot = slots[idx]

                    if slots.count > 1 {
                        CoursePaperSection("上课时段（共 \(slots.count) 个）") {
                            ForEach(Array(slots.enumerated()), id: \.element.id) { sIdx, slot in
                                let isSelected = sIdx == idx
                                let span = slot.startPeriod == slot.endPeriod
                                    ? "第\(slot.startPeriod)节"
                                    : "第\(slot.startPeriod)–\(slot.endPeriod)节"
                                let weekStr = slot.weeks.isEmpty ? "未选周次" : "第 \(WeekSets.format(slot.weeks)) 周"

                                if sIdx > 0 {
                                    CoursePaperDivider()
                                }

                                HStack(spacing: 10) {
                                    Button {
                                        activeSlotIndex = sIdx
                                    } label: {
                                        HStack(spacing: 10) {
                                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                                .foregroundStyle(isSelected ? theme.accent : theme.muted)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("\(TodayFormatters.weekdayLabel(slot.dayOfWeek)) \(span)\(slot.room.isEmpty ? "" : " · \(slot.room)")")
                                                    .font(.subheadline.weight(isSelected ? .semibold : .regular))
                                                    .foregroundStyle(theme.ink)
                                                    .monospacedDigit()
                                                Text(weekStr)
                                                    .font(.caption)
                                                    .foregroundStyle(slot.weeks.isEmpty ? theme.error : theme.muted)
                                                    .monospacedDigit()
                                            }
                                            Spacer()
                                        }
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.mintRow)

                                    Button {
                                        removeSlot(at: sIdx)
                                    } label: {
                                        Image(systemName: "minus.circle")
                                            .foregroundStyle(theme.error)
                                            .frame(width: 44, height: 44)
                                            .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.mintCompact)
                                    .accessibilityLabel("删除第 \(sIdx + 1) 个时段")
                                }
                            }
                        }
                    }

                    CoursePaperSection(slots.count > 1 ? "编辑时段 \(idx + 1)" : "上课时间") {
                        TextField("教室（选填）", text: slotRoomBinding(at: idx))
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))

                        CoursePaperDivider()

                        HStack {
                            Text("星期")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Picker("星期", selection: slotDayBinding(at: idx)) {
                                ForEach(1...7, id: \.self) { dow in
                                    Text(TodayFormatters.weekdayLabel(dow)).tag(dow)
                                }
                            }
                            .pickerStyle(.menu)
                            .font(GokeTypography.bodyMedium.weight(.medium))
                            .tint(theme.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                        }
                        .frame(minHeight: 40)

                        CoursePaperDivider()

                        HStack {
                            Text("开始节次")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Picker("开始节次", selection: slotStartPeriodBinding(at: idx)) {
                                ForEach(periods) { p in
                                    Text("第 \(p.index) 节 · \(p.start.description)").tag(p.index)
                                }
                            }
                            .pickerStyle(.menu)
                            .font(GokeTypography.bodyMedium.weight(.medium))
                            .monospacedDigit()
                            .tint(theme.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                        }
                        .frame(minHeight: 40)

                        CoursePaperDivider()

                        HStack {
                            Text("结束节次")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.muted)
                            Spacer()
                            Picker("结束节次", selection: slotEndPeriodBinding(at: idx)) {
                                ForEach(periods.filter { $0.index >= currentSlot.startPeriod }) { p in
                                    Text("第 \(p.index) 节 · \(p.end.description)").tag(p.index)
                                }
                            }
                            .pickerStyle(.menu)
                            .font(GokeTypography.bodyMedium.weight(.medium))
                            .monospacedDigit()
                            .tint(theme.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                        }
                        .frame(minHeight: 40)

                        CoursePaperDivider()

                        GokeMultiReminderPicker(
                            "课前提醒",
                            selection: slotReminderBinding(at: idx),
                            presets: GokeMultiReminderPicker.coursePresets,
                            emptyLabel: "跟随全局",
                            prefix: "课前"
                        )

                        CoursePaperDivider()

                        Button {
                            appendSlot()
                        } label: {
                            HStack(spacing: GokeSpacing.sm) {
                                Image(systemName: "plus.circle")
                                    .foregroundStyle(theme.accent)
                                Text("添加时段")
                                    .font(GokeTypography.bodyMedium.weight(.medium))
                                    .foregroundStyle(theme.accent)
                                Spacer()
                            }
                            .frame(minHeight: 38)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintRow)
                    }

                    CoursePaperSection(
                        slots.count > 1 ? "时段 \(idx + 1) 上课周次" : "上课周次",
                        footer: currentSlot.weeks.isEmpty
                            ? "请至少选 1 周"
                            : "第 \(WeekSets.format(currentSlot.weeks)) 周 · 共 \(currentSlot.weeks.count) 周",
                        footerColor: currentSlot.weeks.isEmpty ? theme.error : theme.muted
                    ) {
                        HStack(spacing: 6) {
                            weekPresetPill(title: "全选", target: WeekSets.all(totalWeeks), slotIndex: idx)
                            weekPresetPill(title: "1–16周", target: WeekSets.range(1, min(16, totalWeeks)), slotIndex: idx)
                            weekPresetPill(title: "单周", target: WeekSets.odd(totalWeeks), slotIndex: idx)
                            weekPresetPill(title: "双周", target: WeekSets.even(totalWeeks), slotIndex: idx)
                        }
                        .padding(.bottom, GokeSpacing.sm)

                        let gridCols = Array(repeating: GridItem(.flexible(), spacing: 6), count: 5)
                        LazyVGrid(columns: gridCols, spacing: 6) {
                            ForEach(1...totalWeeks, id: \.self) { w in
                                let selected = currentSlot.weeks.contains(w)
                                Button {
                                    if selected {
                                        slots[idx].weeks.remove(w)
                                    } else {
                                        slots[idx].weeks.insert(w)
                                    }
                                } label: {
                                    Text("\(w)")
                                        .font(.subheadline.weight(selected ? .semibold : .regular))
                                        .monospacedDigit()
                                        .foregroundStyle(selected ? theme.onAccentContainer : theme.muted)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 40)
                                        .background {
                                            RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                                .fill(selected ? theme.accentContainer : theme.surfaceHighest)
                                        }
                                        .contentShape(RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }

                        if !currentSlot.weeks.isEmpty {
                            CoursePaperDivider()
                            Button {
                                slots[idx].weeks = []
                            } label: {
                                Text("清空")
                                    .font(.footnote.weight(.medium))
                                    .foregroundStyle(theme.error)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                        }
                    }
                }

                CoursePaperSection("课程配色") {
                    HStack(spacing: 0) {
                        ForEach(theme.courseColors, id: \.index) { pair in
                            let selected = colorIndex == pair.index
                            Button {
                                colorIndex = pair.index
                            } label: {
                                ZStack {
                                    Circle()
                                        .fill(pair.background)
                                        .frame(width: 32, height: 32)
                                        .overlay {
                                            Circle()
                                                .strokeBorder(
                                                    selected ? pair.foreground : theme.outlineVariant.opacity(0.45),
                                                    lineWidth: selected ? 2 : 0.5
                                                )
                                        }
                                    if selected {
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
                }

                CoursePaperSection("备注") {
                    TextField("备注（选填）", text: $note, axis: .vertical)
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.ink)
                        .lineLimit(3...6)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                }

                if existingCourse != nil {
                    Button {
                        showDeleteConfirm = true
                    } label: {
                        Text("删除课程")
                            .font(GokeTypography.bodyLarge.weight(.medium))
                            .monospacedDigit()
                            .foregroundStyle(theme.error)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
                            .contentShape(RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
                    }
                    .buttonStyle(.mintRow)
                }
            }
            .padding(GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
        }
        .background(theme.paper.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(existingCourse == nil ? "新建课程" : "编辑课程")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            if isSheet {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("保存") {
                    saveCourse()
                }
                .fontWeight(.semibold)
                .disabled(!canSave)
            }
        }
        .confirmationDialog("删除此课程？", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("移入回收站", role: .destructive) {
                deleteCourse()
            }
            Button("取消", role: .cancel) {}
        }
        .onAppear {
            loadInitialState()
        }
        .task {
            guard existingCourse == nil else { return }
            try? await Task.sleep(for: .milliseconds(150))
            if !Task.isCancelled {
                isNameFocused = true
            }
        }
    }

    private func weekPresetPill(title: String, target: Set<Int>, slotIndex: Int) -> some View {
        let isActive = slotIndex < slots.count && slots[slotIndex].weeks == target
        return Button {
            guard slotIndex < slots.count else { return }
            slots[slotIndex].weeks = target
        } label: {
            Text(title)
                .font(.caption.weight(isActive ? .semibold : .medium))
                .monospacedDigit()
                .foregroundStyle(isActive ? theme.onAccentContainer : theme.muted)
                .frame(maxWidth: .infinity)
                .frame(height: 34)
                .background {
                    RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                        .fill(isActive ? theme.accentContainer : theme.surfaceHighest)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.mintCompact)
    }

    private func slotRoomBinding(at index: Int) -> Binding<String> {
        Binding(
            get: { index < slots.count ? slots[index].room : "" },
            set: { if index < slots.count { slots[index].room = $0 } }
        )
    }

    private func slotDayBinding(at index: Int) -> Binding<Int> {
        Binding(
            get: { index < slots.count ? slots[index].dayOfWeek : 1 },
            set: { if index < slots.count { slots[index].dayOfWeek = $0 } }
        )
    }

    private func slotStartPeriodBinding(at index: Int) -> Binding<Int> {
        Binding(
            get: { index < slots.count ? slots[index].startPeriod : 1 },
            set: { nextStart in
                guard index < slots.count else { return }
                slots[index].startPeriod = nextStart
                if slots[index].endPeriod < nextStart {
                    slots[index].endPeriod = nextStart
                }
            }
        )
    }

    private func slotEndPeriodBinding(at index: Int) -> Binding<Int> {
        Binding(
            get: { index < slots.count ? slots[index].endPeriod : 2 },
            set: { if index < slots.count { slots[index].endPeriod = max(slots[index].startPeriod, $0) } }
        )
    }

    private func slotReminderBinding(at index: Int) -> Binding<[Int]> {
        Binding(
            get: { index < slots.count ? slots[index].remindMinutesList : [] },
            set: { if index < slots.count { slots[index].remindMinutesList = $0 } }
        )
    }

    private func appendSlot() {
        let maxP = periods.last?.index ?? 12
        let baseSlot = slots.indices.contains(safeActiveSlotIndex) ? slots[safeActiveSlotIndex] : nil
        let usedDays = Set(slots.map(\.dayOfWeek))
        let nextDay = (1...7).first(where: { !usedDays.contains($0) }) ?? (((baseSlot?.dayOfWeek ?? 1) % 7) + 1)
        let sp = min(maxP, max(1, baseSlot?.startPeriod ?? 1))
        let ep = min(maxP, max(sp, baseSlot?.endPeriod ?? min(sp + 1, maxP)))
        let newDraft = SlotDraft(
            id: UUID().uuidString.lowercased(),
            base: nil,
            dayOfWeek: nextDay,
            startPeriod: sp,
            endPeriod: ep,
            weeks: baseSlot?.weeks ?? WeekSets.range(1, min(16, totalWeeks)),
            room: baseSlot?.room ?? "",
            remindMinutesList: baseSlot?.remindMinutesList ?? [],
            startTime: nil,
            endTime: nil
        )
        slots.append(newDraft)
        activeSlotIndex = slots.count - 1
    }

    private func removeSlot(at index: Int) {
        guard slots.count > 1, slots.indices.contains(index) else { return }
        slots.remove(at: index)
        activeSlotIndex = min(activeSlotIndex, max(0, slots.count - 1))
    }

    private func loadInitialState() {
        guard !didLoad else { return }
        didLoad = true

        if let existing = existingCourse {
            let siblings = container.liveSnapshot.courses
                .filter { $0.semesterId == existing.semesterId && $0.name == existing.name }
                .sorted { lhs, rhs in
                    let lw = lhs.weeks.min() ?? 99
                    let rw = rhs.weeks.min() ?? 99
                    if lw != rw { return lw < rw }
                    if lhs.dayOfWeek != rhs.dayOfWeek { return lhs.dayOfWeek < rhs.dayOfWeek }
                    return lhs.startPeriod < rhs.startPeriod
                }
            let effectiveSiblings = siblings.isEmpty ? [existing] : siblings
            originalSiblings = effectiveSiblings
            name = existing.name
            teacher = existing.teacher
            colorIndex = existing.colorIndex
            kind = existing.kind
            remindPersistent = existing.remindPersistent
            note = existing.note
            slots = effectiveSiblings.map { c in
                SlotDraft(
                    id: c.id,
                    base: c,
                    dayOfWeek: c.dayOfWeek,
                    startPeriod: c.startPeriod,
                    endPeriod: c.endPeriod,
                    weeks: c.weeks,
                    room: c.room,
                    remindMinutesList: c.effectiveRemindMinutes,
                    startTime: c.startTime,
                    endTime: c.endTime
                )
            }
            activeSlotIndex = effectiveSiblings.firstIndex(where: { $0.id == existing.id }) ?? 0
        } else {
            let maxP = periods.last?.index ?? 12
            let dow = min(7, max(1, initialDayOfWeek ?? LocalDate.today().dayOfWeek))
            let sp = min(maxP, max(1, initialStartPeriod ?? 1))
            let ep = min(maxP, max(sp, initialEndPeriod ?? min(sp + 1, maxP)))
            var defaultWeeks = WeekSets.range(1, min(16, totalWeeks))
            if let initialWeek, initialWeek >= 1, initialWeek <= totalWeeks {
                defaultWeeks.insert(initialWeek)
            }
            colorIndex = container.liveSnapshot.courses.count % theme.courseColors.count
            slots = [
                SlotDraft(
                    id: UUID().uuidString.lowercased(),
                    base: nil,
                    dayOfWeek: dow,
                    startPeriod: sp,
                    endPeriod: ep,
                    weeks: defaultWeeks,
                    room: "",
                    remindMinutesList: [],
                    startTime: nil,
                    endTime: nil
                )
            ]
            activeSlotIndex = 0
        }
    }

    private func saveCourse() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty, !slots.isEmpty, slots.allSatisfy({ !$0.weeks.isEmpty }) else { return }

        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []

        let resolvedSemesterId: String
        if let sem = targetSemester {
            resolvedSemesterId = sem.id
        } else {
            let createdSem = Semester(
                name: "默认学期",
                week1Monday: LocalDate.today().startOfWeekMonday,
                totalWeeks: 20,
                periods: GdutPeriods.default,
                isCurrent: true,
                updatedAt: nowMillis
            )
            changes.append(.add(after: .semester(createdSem)))
            resolvedSemesterId = createdSem.id
        }

        let trimmedTeacher = teacher.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let effectivePersistent = kind == .EXAM ? remindPersistent : false

        let retainedIds = Set(slots.compactMap { $0.base?.id })
        let removedIds = Set(originalSiblings.filter { !retainedIds.contains($0.id) }.map(\.id))
        for removed in originalSiblings where removedIds.contains(removed.id) {
            changes.append(.remove(before: .course(removed)))
        }
        for ex in container.liveSnapshot.exceptions where removedIds.contains(ex.courseId) {
            changes.append(.remove(before: .courseException(ex)))
        }

        for draft in slots {
            let trimmedRoom = draft.room.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedEnd = max(draft.startPeriod, draft.endPeriod)
            let normalizedReminds = Reminders.normalize(draft.remindMinutesList)

            if let base = draft.base {
                var updated = base
                updated.name = trimmedName
                updated.teacher = trimmedTeacher
                updated.room = trimmedRoom
                updated.dayOfWeek = draft.dayOfWeek
                updated.startPeriod = draft.startPeriod
                updated.endPeriod = resolvedEnd
                updated.weeks = draft.weeks
                updated.colorIndex = colorIndex
                updated.remindMinutes = normalizedReminds.first
                updated.remindMinutesList = normalizedReminds
                updated.kind = kind
                updated.remindPersistent = effectivePersistent
                updated.startTime = draft.startTime
                updated.endTime = draft.endTime
                updated.note = trimmedNote
                updated.updatedAt = nowMillis
                changes.append(.modify(before: .course(base), after: .course(updated)))
            } else {
                let created = Course(
                    id: draft.id,
                    semesterId: resolvedSemesterId,
                    name: trimmedName,
                    teacher: trimmedTeacher,
                    room: trimmedRoom,
                    dayOfWeek: draft.dayOfWeek,
                    startPeriod: draft.startPeriod,
                    endPeriod: resolvedEnd,
                    weeks: draft.weeks,
                    colorIndex: colorIndex,
                    remindMinutes: normalizedReminds.first,
                    note: trimmedNote,
                    source: .MANUAL,
                    updatedAt: nowMillis,
                    remindMinutesList: normalizedReminds,
                    kind: kind,
                    remindPersistent: effectivePersistent,
                    startTime: draft.startTime,
                    endTime: draft.endTime
                )
                changes.append(.add(after: .course(created)))
            }
        }

        let toastLabel = existingCourse != nil ? "已更新「\(trimmedName)」" : "已添加「\(trimmedName)」"
        let cs = ChangeSet(
            description: toastLabel,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: toastLabel)
            if ok { dismiss() }
        }
    }

    private func deleteCourse() {
        let targets = originalSiblings.isEmpty ? (existingCourse.map { [$0] } ?? []) : originalSiblings
        guard !targets.isEmpty else { return }
        let title = targets[0].name
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let targetIds = Set(targets.map(\.id))
        var changes: [Change] = targets.map { .remove(before: .course($0)) }
        for ex in container.liveSnapshot.exceptions where targetIds.contains(ex.courseId) {
            changes.append(.remove(before: .courseException(ex)))
        }
        let cs = ChangeSet(
            description: "删除课程「\(title)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: "已删除「\(title)」")
            if ok { dismiss() }
        }
    }
}

// MARK: - 3. EditTimetableView (Semester & Timetable Manager)

/// Native iOS HIG `List(.insetGrouped)` manager for semesters and courses with `.searchable`,
/// `NavigationLink` course navigation, swipe actions, and timetable tools.
struct EditTimetableView: View {
    @Environment(AppContainer.self) private var container
    @Environment(AppRouter.self) private var router
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    private enum EditTimetableActiveSheet: Identifiable {
        case createSemester
        case editSemester(Semester)
        case copySemester(Semester)

        var id: String {
            switch self {
            case .createSemester: "create"
            case .editSemester(let sem): "edit:\(sem.id)"
            case .copySemester(let sem): "copy:\(sem.id)"
            }
        }
    }

    @State private var searchText: String = ""
    @State private var activeSheet: EditTimetableActiveSheet?
    @State private var semesterToDelete: Semester?
    @State private var jxfwAccount: String = ""
    @State private var expandedGroupIds: Set<String> = []

    private struct CourseGroup: Identifiable {
        var id: String { name }
        let name: String
        let courses: [Course]
        var primary: Course { courses[0] }
    }

    init() {}

    private var live: AppSnapshot { container.liveSnapshot }

    private var semesters: [Semester] {
        live.semesters.sorted { lhs, rhs in
            if lhs.isCurrent != rhs.isCurrent { return lhs.isCurrent }
            return lhs.week1Monday > rhs.week1Monday
        }
    }

    private var trimmedQuery: String {
        searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var filteredSemesters: [Semester] {
        guard !trimmedQuery.isEmpty else { return semesters }
        return semesters.filter { $0.name.localizedCaseInsensitiveContains(trimmedQuery) }
    }

    private var currentSemesterCourses: [Course] {
        guard let current = live.currentSemester else { return [] }
        let base = live.courses
            .filter { $0.semesterId == current.id }
            .sorted { lhs, rhs in
                if lhs.dayOfWeek != rhs.dayOfWeek { return lhs.dayOfWeek < rhs.dayOfWeek }
                if lhs.startPeriod != rhs.startPeriod { return lhs.startPeriod < rhs.startPeriod }
                return lhs.name < rhs.name
            }
        guard !trimmedQuery.isEmpty else { return base }
        return base.filter {
            $0.name.localizedCaseInsensitiveContains(trimmedQuery)
                || $0.room.localizedCaseInsensitiveContains(trimmedQuery)
                || $0.teacher.localizedCaseInsensitiveContains(trimmedQuery)
        }
    }

    private var groupedCurrentSemesterCourses: [CourseGroup] {
        var order: [String] = []
        var buckets: [String: [Course]] = [:]
        for course in currentSemesterCourses {
            if buckets[course.name] == nil {
                order.append(course.name)
            }
            buckets[course.name, default: []].append(course)
        }
        return order.compactMap { name in
            guard let list = buckets[name], !list.isEmpty else { return nil }
            return CourseGroup(name: name, courses: list)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                if trimmedQuery.isEmpty {
                    jxfwSyncCard
                }

                if !semesters.isEmpty {
                    if !filteredSemesters.isEmpty {
                        CoursePaperSection("全部学期") {
                            ForEach(Array(filteredSemesters.enumerated()), id: \.element.id) { idx, sem in
                                if idx > 0 {
                                    CoursePaperDivider()
                                }
                                semesterRow(sem)
                            }
                        }
                    }

                    if !groupedCurrentSemesterCourses.isEmpty {
                        CoursePaperSection("当前学期课程（\(groupedCurrentSemesterCourses.count) 门）") {
                            ForEach(Array(groupedCurrentSemesterCourses.enumerated()), id: \.element.id) { idx, group in
                                if idx > 0 {
                                    CoursePaperDivider()
                                }
                                courseGroupRow(group)
                            }
                        }
                    }

                    if trimmedQuery.isEmpty, let current = live.currentSemester {
                        CoursePaperSection("工具") {
                            NavigationLink(value: GokeRoute.periods(semesterId: current.id)) {
                                HStack(spacing: GokeSpacing.sm) {
                                    Image(systemName: "clock")
                                        .font(.body)
                                        .foregroundStyle(theme.accent)
                                        .frame(width: 24)
                                    Text("节次时间")
                                        .font(GokeTypography.bodyLarge)
                                        .foregroundStyle(theme.ink)
                                    Spacer(minLength: GokeSpacing.sm)
                                    Text("\(current.periods.count) 节")
                                        .font(.footnote)
                                        .monospacedDigit()
                                        .foregroundStyle(theme.muted)
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .frame(minHeight: 40)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)

                            CoursePaperDivider()

                            Button {
                                router.present(.timetableExport)
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.body)
                                        .foregroundStyle(theme.accent)
                                        .frame(width: 24)
                                    Text("导出与分享")
                                        .font(GokeTypography.bodyLarge)
                                        .foregroundStyle(theme.ink)
                                    Spacer(minLength: GokeSpacing.sm)
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .frame(minHeight: 40)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)

                            CoursePaperDivider()

                            Button {
                                router.push(.importFile())
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    Image(systemName: "doc.badge.plus")
                                        .font(.body)
                                        .foregroundStyle(theme.accent)
                                        .frame(width: 24)
                                    Text("导入文件")
                                        .font(GokeTypography.bodyLarge)
                                        .foregroundStyle(theme.ink)
                                    Spacer(minLength: GokeSpacing.sm)
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .frame(minHeight: 40)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)

                            CoursePaperDivider()

                            Button {
                                activeSheet = .copySemester(current)
                            } label: {
                                HStack(spacing: GokeSpacing.sm) {
                                    Image(systemName: "doc.on.doc")
                                        .font(.body)
                                        .foregroundStyle(theme.accent)
                                        .frame(width: 24)
                                    Text("复制到下学期")
                                        .font(GokeTypography.bodyLarge)
                                        .foregroundStyle(theme.ink)
                                    Spacer(minLength: GokeSpacing.sm)
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(theme.muted.opacity(0.6))
                                }
                                .frame(minHeight: 40)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintRow)
                        }
                    }
                }
            }
            .padding(GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
        }
        .background(theme.paper.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .searchable(text: $searchText, prompt: "搜索学期、课程、教室或教师")
        .overlay {
            if semesters.isEmpty {
                GokeEmptyState(
                    systemImage: "books.vertical",
                    title: "暂无学期课表",
                    subtitle: "新建一个学期，或直接从广工教务导入",
                    primaryActionTitle: "新建学期",
                    onPrimaryAction: { activeSheet = .createSemester },
                    secondaryActionTitle: "广工教务导入",
                    onSecondaryAction: { router.push(.jxfw) }
                )
                .padding(.horizontal, GokeSpacing.lg)
            } else if !trimmedQuery.isEmpty && filteredSemesters.isEmpty && groupedCurrentSemesterCourses.isEmpty {
                GokeEmptyState(
                    systemImage: "magnifyingglass",
                    title: "未找到「\(searchText)」",
                    subtitle: "试试搜索其他学期、课程、教室或教师"
                )
                .padding(.horizontal, GokeSpacing.lg)
            }
        }
        .navigationTitle("课表与学期")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("新建学期", systemImage: "calendar.badge.plus") {
                        activeSheet = .createSemester
                    }
                    if live.currentSemester != nil {
                        Button("添加课程", systemImage: "plus.square") {
                            router.present(.courseEdit())
                        }
                    }
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("新建学期或添加课程")
            }
        }
        .task {
            if let acc = try? await KeychainStore.shared.get(.jxfwAccount) {
                jxfwAccount = acc
            }
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .createSemester:
                SemesterFormSheet(semester: nil)
                    .gokePaperSheet(theme, detents: [.medium, .large])
            case .editSemester(let sem):
                SemesterFormSheet(semester: sem)
                    .gokePaperSheet(theme, detents: [.medium, .large])
            case .copySemester(let sem):
                CopySemesterSheet(
                    source: sem,
                    courses: live.courses.filter { $0.semesterId == sem.id }
                )
                .gokePaperSheet(theme, detents: [.medium])
            }
        }
        .confirmationDialog(
            "删除学期「\(semesterToDelete?.name ?? "")」？",
            isPresented: Binding(
                get: { semesterToDelete != nil },
                set: { if !$0 { semesterToDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("移入回收站", role: .destructive) {
                if let target = semesterToDelete {
                    deleteSemester(target)
                }
                semesterToDelete = nil
            }
            Button("取消", role: .cancel) {
                semesterToDelete = nil
            }
        } message: {
            Text("该学期及其下所有课程与调课记录会一并移入回收站，可随时撤销或恢复。")
        }
    }

    private var jxfwSyncCard: some View {
        Button {
            router.push(.jxfw)
        } label: {
            HStack(spacing: GokeSpacing.md) {
                ZStack {
                    Circle()
                        .fill(theme.accentContainer)
                        .frame(width: 44, height: 44)
                    Image(systemName: "graduationcap.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(theme.onAccentContainer)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("广工教务")
                        .font(GokeTypography.titleMedium)
                        .foregroundStyle(theme.ink)
                    if let current = live.currentSemester {
                        let curWeek = SemesterCalendar(semester: current).currentWeek(today: .today())
                        Text(jxfwAccount.isEmpty
                             ? "\(current.name) · 第 \(curWeek) 周"
                             : "学号 \(jxfwAccount) · \(current.name)")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    } else {
                        Text(jxfwAccount.isEmpty ? "导入课表与考试" : "学号 \(jxfwAccount)")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 4)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.muted.opacity(0.6))
            }
            .padding(GokeSpacing.md)
            .background(theme.surface, in: RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous))
        }
        .buttonStyle(.mintRow)
    }

    private func semesterRow(_ sem: Semester) -> some View {
        let courseCount = Set(live.courses.filter { $0.semesterId == sem.id }.map(\.name)).count
        return HStack(spacing: GokeSpacing.sm) {
            Button {
                activeSheet = .editSemester(sem)
            } label: {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: GokeSpacing.xs) {
                        Text(sem.name)
                            .font(GokeTypography.titleMedium)
                            .foregroundStyle(theme.ink)
                            .lineLimit(1)

                        if sem.isCurrent {
                            GokeBadge("当前", tone: .accent)
                        }
                    }

                    Text("第 1 周起 \(sem.week1Monday.description) · 共 \(sem.totalWeeks) 周 · \(courseCount) 门课")
                        .font(.footnote)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.mintRow)

            if !sem.isCurrent {
                Button {
                    setCurrentSemester(sem)
                } label: {
                    Text("设为当前")
                        .font(GokeTypography.labelSmall.weight(.semibold))
                        .foregroundStyle(theme.onAccentContainer)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(theme.accentContainer, in: Capsule())
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
            }

            Menu {
                if !sem.isCurrent {
                    Button("设为当前课表", systemImage: "checkmark.circle") {
                        setCurrentSemester(sem)
                    }
                }
                Button("编辑学期", systemImage: "pencil") {
                    activeSheet = .editSemester(sem)
                }
                Button("节次时间设置", systemImage: "clock") {
                    router.push(.periods(semesterId: sem.id))
                }
                if semesters.count > 1 {
                    Divider()
                    Button("删除学期", systemImage: "trash", role: .destructive) {
                        semesterToDelete = sem
                    }
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("更多学期操作")
        }
        .contextMenu {
            if !sem.isCurrent {
                Button("设为当前课表", systemImage: "checkmark.circle") {
                    setCurrentSemester(sem)
                }
            }
            Button("编辑学期", systemImage: "pencil") {
                activeSheet = .editSemester(sem)
            }
            Button("节次时间设置", systemImage: "clock") {
                router.push(.periods(semesterId: sem.id))
            }
            if semesters.count > 1 {
                Divider()
                Button("删除学期", systemImage: "trash", role: .destructive) {
                    semesterToDelete = sem
                }
            }
        }
    }

    @ViewBuilder
    private func courseGroupRow(_ group: CourseGroup) -> some View {
        if group.courses.count == 1, let single = group.courses.first {
            courseRow(single)
        } else {
            let primary = group.primary
            let pair = theme.courseColor(index: primary.colorIndex)
            let isExpanded = expandedGroupIds.contains(group.id)
            let slotSummaries = group.courses.map { c in
                let pText = c.startPeriod == c.endPeriod ? "第\(c.startPeriod)节" : "第\(c.startPeriod)–\(c.endPeriod)节"
                return "\(TodayFormatters.weekdayLabel(c.dayOfWeek)) \(pText)"
            }
            let teachers = Array(Set(group.courses.map(\.teacher).filter { !$0.isEmpty })).sorted()
            let subtitle = ([slotSummaries.joined(separator: "、")] + teachers).joined(separator: " · ")

            VStack(alignment: .leading, spacing: 0) {
                Button {
                    withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                        if isExpanded {
                            expandedGroupIds.remove(group.id)
                        } else {
                            expandedGroupIds.insert(group.id)
                        }
                    }
                } label: {
                    HStack(spacing: GokeSpacing.md) {
                        RoundedRectangle(cornerRadius: 2, style: .continuous)
                            .fill(pair.foreground)
                            .frame(width: 3.5, height: 34)

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(group.name)
                                    .font(GokeTypography.bodyLarge)
                                    .foregroundStyle(theme.ink)
                                    .lineLimit(1)
                                Text("\(group.courses.count) 时段")
                                    .font(.caption2.weight(.semibold))
                                    .monospacedDigit()
                                    .foregroundStyle(pair.foreground)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 1.5)
                                    .background(pair.background, in: Capsule())
                            }
                            Text(subtitle)
                                .font(.footnote)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                                .lineLimit(1)
                        }

                        Spacer(minLength: GokeSpacing.xs)

                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(theme.muted)
                            .rotationEffect(.degrees(isExpanded ? 0 : -90))
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.mintRow)
                .contextMenu {
                    Button("删除整门课程", systemImage: "trash", role: .destructive) {
                        deleteCourseGroup(group)
                    }
                }

                if isExpanded {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(group.courses) { course in
                            CoursePaperDivider()
                            courseRow(course)
                                .padding(.leading, 14)
                        }
                    }
                    .transition(simplifyMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
                }
            }
            .clipped()
        }
    }

    private func courseRow(_ course: Course) -> some View {
        let pair = theme.courseColor(index: course.colorIndex)
        let periodText = course.startPeriod == course.endPeriod
            ? "第\(course.startPeriod)节"
            : "第\(course.startPeriod)–\(course.endPeriod)节"
        var metaParts = ["\(TodayFormatters.weekdayLabel(course.dayOfWeek)) \(periodText)"]
        let foldedRoom = TodayFormatters.smartFoldRoom(course.room)
        if !foldedRoom.isEmpty {
            metaParts.append(foldedRoom)
        }
        if !course.teacher.isEmpty {
            metaParts.append(course.teacher)
        }

        return NavigationLink(value: GokeRoute.courseDetail(courseId: course.id)) {
            HStack(spacing: GokeSpacing.md) {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(pair.foreground)
                    .frame(width: 3.5, height: 34)

                VStack(alignment: .leading, spacing: 2) {
                    Text(course.name)
                        .font(GokeTypography.bodyLarge)
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)

                    Text(metaParts.joined(separator: " · "))
                        .font(.footnote)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                }

                Spacer(minLength: GokeSpacing.xs)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.muted.opacity(0.6))
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.mintRow)
        .contextMenu {
            Button("编辑课程", systemImage: "pencil") {
                router.push(.courseEdit(courseId: course.id))
            }
            Divider()
            Button("删除", systemImage: "trash", role: .destructive) {
                deleteCourse(course)
            }
        }
    }

    private func setCurrentSemester(_ target: Semester) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var changes: [Change] = []
        for sem in semesters {
            let shouldBeCurrent = sem.id == target.id
            if sem.isCurrent != shouldBeCurrent {
                var updated = sem
                updated.isCurrent = shouldBeCurrent
                updated.updatedAt = nowMillis
                changes.append(.modify(before: .semester(sem), after: .semester(updated)))
            }
        }
        guard !changes.isEmpty else { return }
        let cs = ChangeSet(
            description: "切换当前学期为「\(target.name)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task { _ = await actions.commit(cs, label: "已切换到「\(target.name)」") }
    }

    private func deleteSemester(_ target: Semester) {
        Task { _ = await actions.deleteSemester(target) }
    }

    private func deleteCourse(_ course: Course) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let cs = ChangeSet(
            description: "删除课程「\(course.name)」",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.remove(before: .course(course))]
        )
        Task { _ = await actions.commit(cs, label: "已删除「\(course.name)」") }
    }

    private func deleteCourseGroup(_ group: CourseGroup) {
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let changes = group.courses.map { Change.remove(before: .course($0)) }
        let cs = ChangeSet(
            description: "删除课程「\(group.name)」共 \(group.courses.count) 个时段",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task { _ = await actions.commit(cs, label: "已删除「\(group.name)」") }
    }
}

private struct CopySemesterSheet: View {
    let source: Semester
    let courses: [Course]

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var week1Date: Date = Date()
    @State private var totalWeeks: Int = 20
    @State private var copyCourses: Bool = true
    @State private var didInit: Bool = false

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    CoursePaperSection("新学期信息") {
                        TextField("新学期名称", text: $name)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))

                        CoursePaperDivider()

                        DatePicker(selection: $week1Date, displayedComponents: [.date]) {
                            Text("第 1 周周一")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.ink)
                        }
                        .tint(theme.accent)

                        CoursePaperDivider()

                        CoursePaperStepperRow(
                            title: "总周数",
                            valueLabel: "\(totalWeeks) 周",
                            value: $totalWeeks,
                            range: 1...30
                        )
                    }

                    CoursePaperSection(
                        footer: "保留原有作息时间并设为当前"
                    ) {
                        Toggle(isOn: $copyCourses) {
                            Text("同时复制 \(courses.count) 条排课记录")
                                .font(GokeTypography.bodyMedium)
                                .monospacedDigit()
                                .foregroundStyle(theme.ink)
                        }
                        .tint(theme.accent)
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background(theme.paper.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("复制到下学期")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") { performCopy() }
                        .fontWeight(.semibold)
                        .disabled(!canSave)
                }
            }
            .onAppear {
                guard !didInit else { return }
                didInit = true
                name = "\(source.name)（下学期）"
                let suggestedMonday = source.week1Monday.addingWeeks(source.totalWeeks + 8)
                week1Date = suggestedMonday.atStartOfDay().toDate()
                totalWeeks = source.totalWeeks
            }
        }
    }

    private func performCopy() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let monday = LocalDateTime.fromDate(week1Date).date.startOfWeekMonday

        var changes: [Change] = []
        for existing in container.liveSnapshot.semesters where existing.isCurrent {
            var demoted = existing
            demoted.isCurrent = false
            demoted.updatedAt = nowMillis
            changes.append(.modify(before: .semester(existing), after: .semester(demoted)))
        }

        let newSem = Semester(
            name: trimmed,
            week1Monday: monday,
            totalWeeks: totalWeeks,
            periods: source.periods,
            isCurrent: true,
            updatedAt: nowMillis
        )
        changes.append(.add(after: .semester(newSem)))

        if copyCourses {
            for c in courses where !c.isExam {
                var cloned = c
                cloned.id = UUID().uuidString.lowercased()
                cloned.semesterId = newSem.id
                cloned.source = .MANUAL
                cloned.updatedAt = nowMillis
                cloned.deletedAt = nil
                changes.append(.add(after: .course(cloned)))
            }
        }

        let label = "已复制到新学期「\(trimmed)」"
        let cs = ChangeSet(
            description: label,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok { dismiss() }
        }
    }
}

private struct SemesterFormSheet: View {
    let semester: Semester?

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var week1Date: Date = GdutPeriods.week1Monday.atStartOfDay().toDate()
    @State private var totalWeeks: Int = 20
    @State private var makeCurrent: Bool = true
    @State private var didInit: Bool = false

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    CoursePaperSection("学期信息") {
                        TextField("学期名称", text: $name)
                            .font(GokeTypography.bodyLarge)
                            .foregroundStyle(theme.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(theme.surfaceHighest, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))

                        CoursePaperDivider()

                        DatePicker(selection: $week1Date, displayedComponents: [.date]) {
                            Text("第 1 周周一")
                                .font(GokeTypography.bodyMedium)
                                .foregroundStyle(theme.ink)
                        }
                        .tint(theme.accent)

                        CoursePaperDivider()

                        CoursePaperStepperRow(
                            title: "总周数",
                            valueLabel: "\(totalWeeks) 周",
                            value: $totalWeeks,
                            range: 1...30
                        )
                    }

                    if semester == nil {
                        CoursePaperSection {
                            Toggle(isOn: $makeCurrent) {
                                Text("创建后设为当前学期")
                                    .font(GokeTypography.bodyMedium)
                                    .foregroundStyle(theme.ink)
                            }
                            .tint(theme.accent)
                        }
                    }
                }
                .padding(GokeSpacing.lg)
            }
            .background(theme.paper.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(semester == nil ? "新建学期" : "编辑学期")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { save() }
                        .fontWeight(.semibold)
                        .disabled(!canSave)
                }
            }
            .onAppear {
                guard !didInit else { return }
                didInit = true
                if let semester {
                    name = semester.name
                    week1Date = semester.week1Monday.atStartOfDay().toDate()
                    totalWeeks = semester.totalWeeks
                    makeCurrent = semester.isCurrent
                } else {
                    let today = LocalDate.today()
                    name = "\(today.year) 学期"
                    week1Date = today.startOfWeekMonday.atStartOfDay().toDate()
                    totalWeeks = 20
                    makeCurrent = true
                }
            }
        }
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let monday = LocalDateTime.fromDate(week1Date).date.startOfWeekMonday

        var changes: [Change] = []
        let label: String
        if let semester {
            var updated = semester
            updated.name = trimmed
            updated.week1Monday = monday
            updated.totalWeeks = totalWeeks
            updated.updatedAt = nowMillis
            changes.append(.modify(before: .semester(semester), after: .semester(updated)))
            label = "已更新学期「\(trimmed)」"
        } else {
            if makeCurrent {
                for existing in container.liveSnapshot.semesters where existing.isCurrent {
                    var demoted = existing
                    demoted.isCurrent = false
                    demoted.updatedAt = nowMillis
                    changes.append(.modify(before: .semester(existing), after: .semester(demoted)))
                }
            }
            let created = Semester(
                name: trimmed,
                week1Monday: monday,
                totalWeeks: totalWeeks,
                periods: GdutPeriods.default,
                isCurrent: makeCurrent || container.liveSnapshot.semesters.isEmpty,
                updatedAt: nowMillis
            )
            changes.append(.add(after: .semester(created)))
            label = "已创建学期「\(trimmed)」"
        }

        let cs = ChangeSet(
            description: label,
            source: .MANUAL,
            createdAt: nowMillis,
            changes: changes
        )
        Task {
            let ok = await actions.commit(cs, label: label)
            if ok { dismiss() }
        }
    }
}

// MARK: - 4. PeriodsEditView

/// Native iOS HIG `List(.insetGrouped)` editor for a semester's class period times (`[PeriodTime]`)
/// with compact `DatePicker` rows, swipe-to-delete, and one-tap GDUT 12-period reset.
struct PeriodsEditView: View {
    let semesterId: String?

    @Environment(AppContainer.self) private var container
    @Environment(ActionDispatcher.self) private var actions
    @Environment(\.gokeTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var periods: [EditablePeriod] = []
    @State private var didLoad: Bool = false

    private struct EditablePeriod: Identifiable, Equatable {
        let id: UUID
        var start: LocalTime
        var end: LocalTime

        init(id: UUID = UUID(), start: LocalTime, end: LocalTime) {
            self.id = id
            self.start = start
            self.end = end
        }
    }

    init(semesterId: String? = nil) {
        self.semesterId = semesterId
    }

    private var targetSemester: Semester? {
        let live = container.liveSnapshot
        if let semesterId {
            return live.semesters.first(where: { $0.id == semesterId }) ?? live.currentSemester
        }
        return live.currentSemester
    }

    var body: some View {
        Group {
            if let sem = targetSemester {
                periodsList(semester: sem)
            } else {
                GokeEmptyState(
                    systemImage: "clock",
                    title: "暂无可编辑的学期",
                    subtitle: nil,
                    primaryActionTitle: "返回",
                    onPrimaryAction: { dismiss() }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(theme.paper.ignoresSafeArea())
            }
        }
        .navigationTitle("节次时间设置")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("保存") {
                    savePeriods()
                }
                .fontWeight(.semibold)
                .disabled(periods.isEmpty || targetSemester == nil)
            }
        }
        .onAppear {
            loadInitialPeriods()
        }
    }

    private static let maxPeriodCount = 16

    private func periodsList(semester: Semester) -> some View {
        let morningIndices = Array(0..<min(4, periods.count))
        let afternoonIndices = periods.count > 4 ? Array(4..<min(9, periods.count)) : []
        let eveningIndices = periods.count > 9 ? Array(9..<periods.count) : []

        return ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                if !morningIndices.isEmpty {
                    CoursePaperSection("\(semester.name) · 上午（第 1–\(morningIndices.count) 节）") {
                        ForEach(Array(morningIndices.enumerated()), id: \.element) { offset, idx in
                            if offset > 0 {
                                CoursePaperDivider()
                            }
                            periodRow(index: idx, item: periods[idx])
                        }
                    }
                }

                if !afternoonIndices.isEmpty {
                    CoursePaperSection("下午（第 5–\(4 + afternoonIndices.count) 节）") {
                        ForEach(Array(afternoonIndices.enumerated()), id: \.element) { offset, idx in
                            if offset > 0 {
                                CoursePaperDivider()
                            }
                            periodRow(index: idx, item: periods[idx])
                        }
                    }
                }

                if !eveningIndices.isEmpty {
                    CoursePaperSection("晚上（第 10–\(periods.count) 节）") {
                        ForEach(Array(eveningIndices.enumerated()), id: \.element) { offset, idx in
                            if offset > 0 {
                                CoursePaperDivider()
                            }
                            periodRow(index: idx, item: periods[idx])
                        }
                    }
                }

                CoursePaperSection {
                    Button {
                        appendPeriod()
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Image(systemName: "plus.circle")
                                .foregroundStyle(periods.count >= Self.maxPeriodCount ? theme.muted : theme.accent)
                            Text(
                                periods.count >= Self.maxPeriodCount
                                    ? "已达上限（最多 \(Self.maxPeriodCount) 节）"
                                    : "添加第 \(periods.count + 1) 节"
                            )
                            .font(GokeTypography.bodyLarge.weight(.medium))
                            .monospacedDigit()
                            .foregroundStyle(periods.count >= Self.maxPeriodCount ? theme.muted : theme.accent)
                            Spacer()
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                    .disabled(periods.count >= Self.maxPeriodCount)

                    if periods.count > 1 {
                        CoursePaperDivider()
                        Button {
                            GokeHaptics.destructiveConfirm()
                            periods.removeLast()
                        } label: {
                            HStack(spacing: GokeSpacing.sm) {
                                Image(systemName: "minus.circle")
                                    .foregroundStyle(theme.error)
                                Text("删除最后一节（第 \(periods.count) 节）")
                                    .font(GokeTypography.bodyLarge)
                                    .monospacedDigit()
                                    .foregroundStyle(theme.error)
                                Spacer()
                            }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintRow)
                    }

                    CoursePaperDivider()

                    Button {
                        periods = GdutPeriods.default.map { EditablePeriod(start: $0.start, end: $0.end) }
                    } label: {
                        HStack(spacing: GokeSpacing.sm) {
                            Image(systemName: "arrow.counterclockwise")
                                .foregroundStyle(theme.muted)
                            Text("恢复默认")
                                .font(GokeTypography.bodyLarge)
                                .foregroundStyle(theme.ink)
                            Spacer()
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.mintRow)
                }
            }
            .padding(GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
        }
        .background(theme.paper.ignoresSafeArea())
    }

    private func periodRow(index: Int, item: EditablePeriod) -> some View {
        let durationMins = max(0, item.end.totalMinutes - item.start.totalMinutes)
        return HStack(spacing: GokeSpacing.sm) {
            Text("\(index + 1)")
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(theme.onAccentContainer)
                .frame(width: 28, height: 28)
                .background {
                    Circle().fill(theme.accentContainer)
                }

            DatePicker(
                "",
                selection: timeBinding(for: index, isStart: true),
                displayedComponents: [.hourAndMinute]
            )
            .labelsHidden()
            .tint(theme.accent)

            Text("–")
                .foregroundStyle(theme.muted)

            DatePicker(
                "",
                selection: timeBinding(for: index, isStart: false),
                displayedComponents: [.hourAndMinute]
            )
            .labelsHidden()
            .tint(theme.accent)

            Spacer(minLength: 4)

            Text("\(durationMins)分")
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(theme.muted)
        }
    }

    private func timeBinding(for index: Int, isStart: Bool) -> Binding<Date> {
        let anchorDay = LocalDate.today()
        return Binding(
            get: {
                guard index < periods.count else { return Date() }
                let t = isStart ? periods[index].start : periods[index].end
                return anchorDay.atTime(t).toDate()
            },
            set: { newDate in
                guard index < periods.count else { return }
                let raw = LocalDateTime.fromDate(newDate).time
                let clean = LocalTime(hour: raw.hour, minute: raw.minute)
                if isStart {
                    let startMins = min(clean.totalMinutes, 23 * 60 + 58)
                    let clampedStart = LocalTime(hour: startMins / 60, minute: startMins % 60)
                    periods[index].start = clampedStart
                    if periods[index].end <= clampedStart {
                        let endMins = min(23 * 60 + 59, startMins + 45)
                        periods[index].end = LocalTime(hour: endMins / 60, minute: endMins % 60)
                    }
                } else {
                    if clean <= periods[index].start {
                        actions.showToast(message: "结束时间必须晚于开始时间")
                        return
                    }
                    periods[index].end = clean
                }
            }
        )
    }

    private func loadInitialPeriods() {
        guard !didLoad else { return }
        didLoad = true
        let source = (targetSemester?.periods ?? GdutPeriods.default).sorted { $0.index < $1.index }
        periods = source.map { EditablePeriod(start: $0.start, end: $0.end) }
    }

    private func appendPeriod() {
        guard periods.count < Self.maxPeriodCount else {
            actions.showToast(message: "最多支持 \(Self.maxPeriodCount) 节作息")
            return
        }
        let lastEnd = periods.last?.end.totalMinutes ?? (8 * 60 + 25)
        let startMins = min(23 * 60, lastEnd + 5)
        let endMins = min(23 * 60 + 55, startMins + 45)
        periods.append(
            EditablePeriod(
                start: LocalTime(hour: startMins / 60, minute: startMins % 60),
                end: LocalTime(hour: endMins / 60, minute: endMins % 60)
            )
        )
    }

    private func savePeriods() {
        guard let sem = targetSemester, !periods.isEmpty else { return }
        if let invalidIdx = periods.firstIndex(where: { $0.end <= $0.start }) {
            actions.showToast(message: "第 \(invalidIdx + 1) 节结束时间必须晚于开始时间")
            return
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var updated = sem
        updated.periods = periods.enumerated().map { idx, item in
            PeriodTime(index: idx + 1, start: item.start, end: item.end)
        }
        updated.updatedAt = nowMillis

        let cs = ChangeSet(
            description: "更新「\(sem.name)」节次作息",
            source: .MANUAL,
            createdAt: nowMillis,
            changes: [.modify(before: .semester(sem), after: .semester(updated))]
        )
        Task {
            let ok = await actions.commit(cs, label: "已保存 \(updated.periods.count) 节作息时间")
            if ok { dismiss() }
        }
    }
}

// MARK: - 5. TimetableExportSheet

/// Native SwiftUI `Form` sheet for timetable PNG, structured `.xlsx`, timetable-only `.ics`,
/// and Quick Share (`goke.xeed.ink` + QR + browser preview).
struct TimetableExportSheet: View {
    let initialWeek: Int?

    @Environment(AppContainer.self) private var container
    @Environment(AppSettingsStore.self) private var settingsStore
    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    private var simplifyMotion: Bool {
        GokeMotion.simplify(reduceMotion: reduceMotion, policy: performance)
    }

    @State private var selectedWeek: Int = 1
    @State private var didInit: Bool = false
    @State private var showTeacher: Bool = true
    @State private var showPeriodTimes: Bool = true
    @State private var showWeekend: Bool = true
    @State private var useDarkScheme: Bool = false
    @State private var watermark: String = "Goke · 工课"
    @State private var exportedPNGURL: URL?
    @State private var exportedXLSXURL: URL?
    @State private var exportedICSURL: URL?
    @State private var exportedJSONURL: URL?
    @State private var exportError: String?

    // Quick Share (`goke.xeed.ink`) state
    @State private var shareDisplayName: String = ""
    @State private var shareStudentId: String = ""
    @State private var shareServerURL: String = ""
    @State private var selectedExpiryKey: String = "semesterEnd"
    @State private var activeShare: TimetableShareRecord?
    @State private var shareBusy: Bool = false
    @State private var shareError: String?
    @State private var shareToast: String?
    @State private var showShareQR: Bool = false
    @State private var confirmDeleteShare: Bool = false

    #if canImport(UIKit)
    @State private var previewImage: UIImage?
    #endif

    private struct RenderKey: Equatable {
        let semesterId: String?
        let week: Int
        let showTeacher: Bool
        let showPeriodTimes: Bool
        let showWeekend: Bool
        let useDarkScheme: Bool
        let watermark: String
        let exportScale: CGFloat
        let theme: GokeTheme
        let snapshotRevision: UInt64
    }

    init(initialWeek: Int? = nil) {
        self.initialWeek = initialWeek
    }

    private var semester: Semester? {
        container.liveSnapshot.currentSemester
    }

    private var totalWeeks: Int {
        max(1, semester?.totalWeeks ?? 20)
    }

    private var effectiveExportScale: CGFloat {
        min(3.0, performance.maxExportScale)
    }

    private var exportOptions: TimetableExportOptions {
        TimetableExportOptions(
            canvasWidth: 420,
            scale: effectiveExportScale,
            showTeacher: showTeacher,
            showPeriodTimes: showPeriodTimes,
            showWeekend: showWeekend,
            colorScheme: useDarkScheme ? .dark : .light,
            watermark: watermark.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Goke · 工课" : watermark
        )
    }

    private var renderKey: RenderKey {
        RenderKey(
            semesterId: semester?.id,
            week: selectedWeek,
            showTeacher: showTeacher,
            showPeriodTimes: showPeriodTimes,
            showWeekend: showWeekend,
            useDarkScheme: useDarkScheme,
            watermark: watermark,
            exportScale: effectiveExportScale,
            theme: theme,
            snapshotRevision: container.snapshotRevision
        )
    }

    private var expiryPresets: [ShareExpiryPreset] {
        guard let sem = semester else { return [] }
        return ShareExpiry.presets(sem, today: .today())
    }

    var body: some View {
        NavigationStack {
            Group {
                if let sem = semester {
                    exportForm(semester: sem)
                } else {
                    GokeEmptyState(
                        systemImage: "photo",
                        title: "暂无可导出的课表",
                        subtitle: nil
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(theme.paper.ignoresSafeArea())
                }
            }
            .navigationTitle("导出课表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task {
                if !didInit {
                    didInit = true
                    if let initialWeek {
                        selectedWeek = min(max(1, initialWeek), totalWeeks)
                    } else if let sem = semester {
                        selectedWeek = SemesterCalendar(semester: sem).currentWeek(today: .today())
                    }
                    showTeacher = settingsStore.settings.showTeacher
                    activeShare = TimetableShareStorage.loadRecord()
                    shareServerURL = TimetableShareStorage.loadCustomServerURL()
                    if let rec = activeShare {
                        shareDisplayName = rec.displayName
                        shareStudentId = rec.boundStudentId
                    }
                }
                if shareStudentId.isEmpty, let jxfwAcc = try? await KeychainStore.shared.get(.jxfwAccount), !jxfwAcc.isEmpty {
                    shareStudentId = jxfwAcc
                }
                if activeShare != nil {
                    await refreshShareStatus()
                }
            }
            .task(id: renderKey) {
                regenerateOutputs()
            }
            .confirmationDialog(
                "撤销在线分享链接？",
                isPresented: $confirmDeleteShare,
                titleVisibility: .visible
            ) {
                Button("撤销并删除云端课表", role: .destructive) {
                    Task { await deleteActiveShare() }
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text("撤销后当前短链与二维码将立即失效。")
            }
        }
    }

    private func exportForm(semester: Semester) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                    Text("预览")
                        .font(GokeTypography.labelLarge)
                        .foregroundStyle(theme.muted)
                        .padding(.horizontal, 4)

                    previewContainer
                }

                CoursePaperSection(
                    "本地导出",
                    footer: exportError,
                    footerColor: theme.error
                ) {
                    if let pngURL = exportedPNGURL {
                        ShareLink(
                            item: pngURL,
                            subject: Text("\(semester.name) 第 \(selectedWeek) 周课表")
                        ) {
                            exportShareRow(
                                icon: "photo",
                                title: "导出图片 (PNG)"
                            )
                        }
                        .buttonStyle(.mintRow)
                        CoursePaperDivider()
                    }

                    if let xlsxURL = exportedXLSXURL {
                        ShareLink(
                            item: xlsxURL,
                            subject: Text("\(semester.name) 课表表格")
                        ) {
                            exportShareRow(icon: "tablecells", title: "导出 Excel (.xlsx)")
                        }
                        .buttonStyle(.mintRow)
                        CoursePaperDivider()
                    }

                    if let icsURL = exportedICSURL {
                        ShareLink(
                            item: icsURL,
                            subject: Text("\(semester.name) 纯课表日历")
                        ) {
                            exportShareRow(icon: "calendar.badge.plus", title: "导出日历 (.ics)")
                        }
                        .buttonStyle(.mintRow)
                        CoursePaperDivider()
                    }

                    if let jsonURL = exportedJSONURL {
                        ShareLink(
                            item: jsonURL,
                            subject: Text("\(semester.name) 课表数据 (JSON)")
                        ) {
                            exportShareRow(icon: "curlybraces", title: "导出数据 (.json)")
                        }
                        .buttonStyle(.mintRow)
                    }
                }

                quickShareSection(semester: semester)

                CoursePaperSection("导出选项") {
                    CoursePaperStepperRow(
                        title: "导出周次",
                        valueLabel: "第 \(selectedWeek) 周",
                        value: $selectedWeek,
                        range: 1...totalWeeks
                    )

                    CoursePaperDivider()

                    Toggle(isOn: $showTeacher) {
                        Text("显示教师")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    }
                    .tint(theme.accent)

                    CoursePaperDivider()

                    Toggle(isOn: $showPeriodTimes) {
                        Text("节次时间")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    }
                    .tint(theme.accent)

                    CoursePaperDivider()

                    Toggle(isOn: $showWeekend) {
                        Text("显示周末")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    }
                    .tint(theme.accent)

                    CoursePaperDivider()

                    Toggle(isOn: $useDarkScheme) {
                        Text("深色样式")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                    }
                    .tint(theme.accent)

                    CoursePaperDivider()

                    HStack(spacing: GokeSpacing.sm) {
                        Text("页脚水印")
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.muted)
                        TextField("Goke · 工课", text: $watermark)
                            .font(GokeTypography.bodyMedium)
                            .foregroundStyle(theme.ink)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            .padding(GokeSpacing.lg)
            .padding(.bottom, GokeSpacing.xl)
        }
        .background(theme.paper.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
    }

    private func exportShareRow(icon: String, title: String) -> some View {
        HStack(spacing: GokeSpacing.sm) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(theme.accent)
                .frame(width: 24)
            Text(title)
                .font(GokeTypography.bodyLarge)
                .monospacedDigit()
                .foregroundStyle(theme.ink)
            Spacer()
            Image(systemName: "square.and.arrow.up")
                .font(.caption.weight(.semibold))
                .foregroundStyle(theme.muted.opacity(0.6))
        }
        .frame(minHeight: 40)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func quickShareSection(semester: Semester) -> some View {
        CoursePaperSection(
            "快速分享（实验性）",
            footer: shareError ?? shareToast ?? "仅上传课表数据，不公开学号",
            footerColor: shareError != nil ? theme.error : (shareToast != nil ? theme.accent : theme.muted)
        ) {
            if let rec = activeShare {
                VStack(alignment: .leading, spacing: GokeSpacing.sm) {
                    HStack {
                        Label("在线课表已开启", systemImage: "link.circle.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(theme.accent)
                        Spacer()
                        Text(rec.code)
                            .font(.caption.monospaced().weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(theme.muted)
                    }

                    Text(rec.shareUrl)
                        .font(.caption.monospaced())
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)

                    Text("有效期至 \(formatShareTimestamp(rec.expiresAt))")
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(theme.muted)

                    HStack(spacing: GokeSpacing.sm) {
                        Button {
                            #if canImport(UIKit)
                            UIPasteboard.general.string = rec.shareUrl
                            #endif
                            shareToast = "已复制分享链接"
                        } label: {
                            Label("复制链接", systemImage: "doc.on.doc")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(theme.onAccentContainer)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(theme.accentContainer, in: Capsule())
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)

                        if let url = URL(string: rec.shareUrl) {
                            Button {
                                openURL(url)
                            } label: {
                                Label("浏览器预览", systemImage: "safari")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(theme.ink)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 7)
                                    .background(theme.surfaceHighest, in: Capsule())
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                        }

                        Button {
                            withAnimation(GokeMotion.snappy(reduceMotion: simplifyMotion)) {
                                showShareQR.toggle()
                            }
                        } label: {
                            Label(showShareQR ? "隐藏二维码" : "二维码", systemImage: "qrcode")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(theme.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(theme.surfaceHighest, in: Capsule())
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                    }

                    #if canImport(UIKit) && canImport(CoreImage)
                    if showShareQR, let qrImage = TimetableQRCodeGenerator.makeUIImage(from: rec.shareUrl) {
                        HStack {
                            Spacer()
                            // White quiet zone required for reliable QR scanning in dark mode.
                            Image(uiImage: qrImage)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 176, height: 176)
                                .padding(10)
                                .background(Color.white, in: RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous))
                            Spacer()
                        }
                        .padding(.top, GokeSpacing.xs)
                        .transition(simplifyMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
                    }
                    #endif
                }
                .padding(.vertical, 4)

                CoursePaperDivider()
            }

            HStack(spacing: GokeSpacing.sm) {
                Text("展示名称")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)
                TextField("选填", text: $shareDisplayName)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                    .multilineTextAlignment(.trailing)
            }

            CoursePaperDivider()

            HStack(spacing: GokeSpacing.sm) {
                Text("学号")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)
                TextField("请输入学号", text: $shareStudentId)
                    .font(GokeTypography.bodyMedium)
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .multilineTextAlignment(.trailing)
                    .autocorrectionDisabled()
            }

            if !expiryPresets.isEmpty {
                CoursePaperDivider()
                HStack {
                    Text("有效期")
                        .font(GokeTypography.bodyMedium)
                        .foregroundStyle(theme.muted)
                    Spacer()
                    Picker("有效期", selection: $selectedExpiryKey) {
                        ForEach(expiryPresets) { preset in
                            Text("\(preset.label)（\(preset.date.description)）").tag(preset.key)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(theme.accent)
                }
            }

            CoursePaperDivider()

            HStack(spacing: GokeSpacing.sm) {
                Text("分享服务")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)
                TextField(TimetableShareClient.defaultServerURL, text: $shareServerURL)
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.ink)
                    .multilineTextAlignment(.trailing)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }

            CoursePaperDivider()

            Button {
                Task { await publishQuickShare(semester: semester) }
            } label: {
                HStack(spacing: GokeSpacing.sm) {
                    if shareBusy {
                        ProgressView()
                            .controlSize(.small)
                            .tint(theme.onAccent)
                    }
                    Text(activeShare == nil ? "生成分享链接" : "更新在线课表")
                        .font(GokeTypography.labelLarge.weight(.semibold))
                }
                .foregroundStyle(theme.onAccent)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(theme.accent, in: Capsule())
            }
            .buttonStyle(.mintCompact)
            .disabled(shareBusy)

            if activeShare != nil {
                CoursePaperDivider()
                Button {
                    confirmDeleteShare = true
                } label: {
                    Text("撤销在线分享")
                        .font(GokeTypography.bodyMedium.weight(.medium))
                        .foregroundStyle(theme.error)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .disabled(shareBusy)
            }
        }
    }

    private func publishQuickShare(semester: Semester) async {
        shareError = nil
        shareToast = nil
        let effectiveId = shareStudentId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !effectiveId.isEmpty else {
            shareError = "请先填写学号"
            return
        }
        let targetURL = shareServerURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? TimetableShareClient.defaultServerURL
            : shareServerURL.trimmingCharacters(in: .whitespacesAndNewlines)

        let chosenExpiry = expiryPresets.first(where: { $0.key == selectedExpiryKey })?.expiresAt
            ?? ShareExpiry.maxMillis(semester)

        shareBusy = true
        defer { shareBusy = false }

        do {
            let client = try TimetableShareClient(baseURL: targetURL)
            let payload = try SharedTimetable.fromSnapshot(
                container.snapshot,
                displayName: shareDisplayName.trimmingCharacters(in: .whitespacesAndNewlines),
                expiresAt: chosenExpiry
            )
            let token = await TimetableShareStorage.loadManageToken()
            let res = try await client.putShare(
                PutShareRequest(
                    studentId: effectiveId,
                    manageToken: token,
                    name: payload.name,
                    expiresAt: chosenExpiry,
                    timetable: SharedTimetableBody(
                        semester: payload.semester,
                        courses: payload.courses,
                        exceptions: payload.exceptions
                    )
                )
            )
            TimetableShareStorage.saveCustomServerURL(targetURL)
            let record = TimetableShareRecord(
                serverUrl: targetURL,
                displayName: res.name,
                boundStudentId: effectiveId,
                code: res.code,
                shareUrl: res.shareUrl,
                semesterName: payload.semester.name,
                updatedAt: res.updatedAt,
                expiresAt: res.expiresAt
            )
            await TimetableShareStorage.saveRecord(record, manageToken: res.manageToken ?? token)
            activeShare = record
            shareToast = "在线课表已同步（短码 \(res.code)）"
        } catch {
            shareError = error.localizedDescription
        }
    }

    private func refreshShareStatus() async {
        guard let rec = activeShare,
              let token = await TimetableShareStorage.loadManageToken(),
              !token.isEmpty
        else { return }
        let targetURL = rec.serverUrl.isEmpty ? TimetableShareClient.defaultServerURL : rec.serverUrl
        guard let client = try? TimetableShareClient(baseURL: targetURL) else { return }
        if let res = try? await client.status(ShareStatusRequest(studentId: rec.boundStudentId, manageToken: token)) {
            if !res.exists {
                await TimetableShareStorage.clearRecord()
                activeShare = nil
            } else if let code = res.code, let shareUrl = res.shareUrl {
                let updated = TimetableShareRecord(
                    serverUrl: targetURL,
                    displayName: res.name ?? rec.displayName,
                    boundStudentId: rec.boundStudentId,
                    code: code,
                    shareUrl: shareUrl,
                    semesterName: res.semesterName ?? rec.semesterName,
                    updatedAt: res.updatedAt ?? rec.updatedAt,
                    expiresAt: res.expiresAt ?? rec.expiresAt
                )
                await TimetableShareStorage.saveRecord(updated, manageToken: token)
                activeShare = updated
            }
        }
    }

    private func deleteActiveShare() async {
        guard let rec = activeShare else { return }
        shareBusy = true
        shareError = nil
        shareToast = nil
        defer { shareBusy = false }

        let targetURL = rec.serverUrl.isEmpty ? TimetableShareClient.defaultServerURL : rec.serverUrl
        if let token = await TimetableShareStorage.loadManageToken(),
           !token.isEmpty,
           let client = try? TimetableShareClient(baseURL: targetURL)
        {
            do {
                _ = try await client.delete(DeleteShareRequest(studentId: rec.boundStudentId, manageToken: token))
            } catch {
                shareError = error.localizedDescription
                return
            }
        }
        await TimetableShareStorage.clearRecord()
        activeShare = nil
        showShareQR = false
        shareToast = "已撤销在线分享"
    }

    private func formatShareTimestamp(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        let dt = LocalDateTime.fromDate(date)
        return dt.date.description
    }

    private var previewContainer: some View {
        VStack(spacing: GokeSpacing.xs) {
            #if canImport(UIKit)
            if let image = previewImage {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .clipShape(.rect(cornerRadius: GokeRadius.md))
                    .overlay {
                        RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                            .stroke(theme.separator.opacity(0.45), lineWidth: 0.75)
                    }
            } else {
                ProgressView("正在渲染高清课表…")
                    .frame(maxWidth: .infinity, minHeight: 220)
            }
            #else
            Text("课表图片预览仅在 iOS 设备可用")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
                .frame(maxWidth: .infinity, minHeight: 160)
            #endif
        }
        .frame(maxWidth: .infinity)
    }

    private func regenerateOutputs() {
        guard let sem = semester else { return }
        exportError = nil

        guard let layout = TimetableLayout.buildWeek(
            snapshot: container.snapshot,
            semester: sem,
            week: selectedWeek,
            defaultBlockMinutes: settingsStore.settings.defaultBlockMinutes,
            includeTimedEvents: true
        ) else {
            exportError = "当前周次无法生成课表布局。"
            return
        }

        let opts = exportOptions
        #if canImport(UIKit)
        previewImage = TimetableImageExporter.renderUIImage(
            layout: layout,
            semesterName: sem.name,
            theme: theme,
            options: opts
        )
        #endif

        let safeName = sem.name
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: " ", with: "_")

        do {
            exportedPNGURL = try TimetableImageExporter.exportToTemporaryPNGFile(
                layout: layout,
                semesterName: sem.name,
                theme: theme,
                options: opts
            )
        } catch {
            exportedPNGURL = nil
            exportError = error.localizedDescription
        }

        do {
            let xlsxData = try TimetableXlsxExporter.write(
                snapshot: container.snapshot,
                includeTeacher: showTeacher
            )
            let xlsxURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("Goke_\(safeName).xlsx")
            try xlsxData.write(to: xlsxURL, options: .atomic)
            exportedXLSXURL = xlsxURL
        } catch {
            exportedXLSXURL = nil
        }

        if let icsString = ICSCodec.writeTimetable(snapshot: container.snapshot),
           let data = icsString.data(using: .utf8)
        {
            let icsURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("Goke_\(safeName)_timetable.ics")
            if (try? data.write(to: icsURL, options: .atomic)) != nil {
                exportedICSURL = icsURL
            }
        }

        do {
            let payload = try SharedTimetable.fromSnapshot(
                container.snapshot,
                displayName: shareDisplayName.trimmingCharacters(in: .whitespacesAndNewlines),
                expiresAt: ShareExpiry.maxMillis(sem)
            )
            let jsonString = try SharedTimetable.toJSON(payload)
            if let data = jsonString.data(using: .utf8) {
                let jsonURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent("Goke_\(safeName)_timetable.json")
                try data.write(to: jsonURL, options: .atomic)
                exportedJSONURL = jsonURL
            }
        } catch {
            exportedJSONURL = nil
        }
    }
}
