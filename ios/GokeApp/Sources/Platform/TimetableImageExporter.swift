import Foundation
import GokeCore
import SwiftUI
import GokeShared
#if canImport(UIKit)
import UIKit
#endif

/// Configuration for high-DPI timetable PNG export.
public struct TimetableExportOptions: Sendable, Equatable {
    public var canvasWidth: CGFloat
    public var scale: CGFloat
    public var showTeacher: Bool
    public var showPeriodTimes: Bool
    public var showWeekend: Bool
    public var colorScheme: ColorScheme?
    public var watermark: String

    public init(
        canvasWidth: CGFloat = 420,
        scale: CGFloat = 3.0,
        showTeacher: Bool = true,
        showPeriodTimes: Bool = true,
        showWeekend: Bool = true,
        colorScheme: ColorScheme? = .light,
        watermark: String = "Goke · 工课"
    ) {
        self.canvasWidth = canvasWidth
        self.scale = scale
        self.showTeacher = showTeacher
        self.showPeriodTimes = showPeriodTimes
        self.showWeekend = showWeekend
        self.colorScheme = colorScheme
        self.watermark = watermark
    }
}

public enum TimetableExportError: Error, Sendable, Equatable, LocalizedError {
    case renderingFailed
    case pngEncodingFailed

    public var errorDescription: String? {
        switch self {
        case .renderingFailed:
            "课表图片渲染失败。"
        case .pngEncodingFailed:
            "无法将课表图像编码为 PNG 格式。"
        }
    }
}

/// `@MainActor` SwiftUI `ImageRenderer` high-DPI timetable PNG exporter following the "素纸 Goke" aesthetic.
@MainActor
public enum TimetableImageExporter {
    #if canImport(UIKit)
    /// Renders a `TimetableWeekLayout` to a high-DPI `UIImage`, capping scale via `performance.maxExportScale` when provided.
    public static func renderUIImage(
        layout: TimetableWeekLayout,
        semesterName: String,
        theme: GokeTheme = .mint,
        options: TimetableExportOptions = TimetableExportOptions(),
        performance: GokePerformancePolicy? = nil
    ) -> UIImage? {
        var resolvedOptions = options
        let effectivePolicy = performance ?? .current()
        resolvedOptions.scale = min(options.scale, effectivePolicy.maxExportScale)
        let view = TimetableExportCardView(
            layout: layout,
            semesterName: semesterName,
            theme: theme,
            options: resolvedOptions
        )
        let renderer = ImageRenderer(content: view)
        renderer.scale = resolvedOptions.scale
        renderer.isOpaque = true
        return renderer.uiImage
    }
    #endif

    /// Renders a `TimetableWeekLayout` into crisp PNG `Data` at `options.scale` (capped by `performance.maxExportScale`).
    public static func renderPNGData(
        layout: TimetableWeekLayout,
        semesterName: String,
        theme: GokeTheme = .mint,
        options: TimetableExportOptions = TimetableExportOptions(),
        performance: GokePerformancePolicy? = nil
    ) -> Data? {
        #if canImport(UIKit)
        return autoreleasepool {
            renderUIImage(
                layout: layout,
                semesterName: semesterName,
                theme: theme,
                options: options,
                performance: performance
            )?.pngData()
        }
        #else
        return nil
        #endif
    }

    /// Exports the rendered timetable PNG to a temporary file URL suitable for `ShareLink`.
    public static func exportToTemporaryPNGFile(
        layout: TimetableWeekLayout,
        semesterName: String,
        theme: GokeTheme = .mint,
        options: TimetableExportOptions = TimetableExportOptions(),
        performance: GokePerformancePolicy? = nil
    ) throws -> URL {
        guard let data = renderPNGData(
            layout: layout,
            semesterName: semesterName,
            theme: theme,
            options: options,
            performance: performance
        ) else {
            throw TimetableExportError.renderingFailed
        }
        let safeSemester = semesterName
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: " ", with: "_")
        let fileName = "Goke_\(safeSemester)_W\(layout.week).png"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try data.write(to: url, options: .atomic)
        return url
    }
}

// MARK: - Printable Clean-Paper Timetable View

private struct TimetableExportCardView: View {
    let layout: TimetableWeekLayout
    let semesterName: String
    let theme: GokeTheme
    let options: TimetableExportOptions

    private let railWidth: CGFloat = 38
    private let rowHeight: CGFloat = 52
    private let weekdayLabels = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    private var visibleColumns: [TimetableColumn] {
        options.showWeekend ? layout.columns : layout.columns.filter { $0.dayOfWeek <= 5 }
    }

    private var totalGridHeight: CGFloat {
        CGFloat(max(1, layout.periods.count)) * rowHeight
    }

    var body: some View {
        let content = VStack(alignment: .leading, spacing: GokeSpacing.md) {
            headerSection
            gridSection
            footerSection
        }
        .padding(GokeSpacing.lg)
        .frame(width: options.canvasWidth, alignment: .topLeading)
        .background { theme.paper }

        if let scheme = options.colorScheme {
            content.environment(\.colorScheme, scheme)
        } else {
            content
        }
    }

    private var headerSection: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text("第 \(layout.week) 周")
                    .font(GokeTypography.titleLarge)
                    .foregroundStyle(theme.ink)
                    .monospacedDigit()
                Text(semesterName)
                    .font(GokeTypography.labelMedium)
                    .foregroundStyle(theme.muted)
            }
            Spacer(minLength: GokeSpacing.sm)
            Text("\(shortDate(layout.startDate)) – \(shortDate(layout.endDate))")
                .font(GokeTypography.labelMedium)
                .foregroundStyle(theme.accent)
                .monospacedDigit()
                .padding(.horizontal, GokeSpacing.sm)
                .padding(.vertical, GokeSpacing.xs)
                .background {
                    Capsule().fill(theme.accentContainer)
                }
        }
    }

    private var gridSection: some View {
        let cols = visibleColumns
        let dayAreaWidth = max(120, options.canvasWidth - GokeSpacing.lg * 2 - railWidth)
        let colWidth = dayAreaWidth / CGFloat(max(1, cols.count))

        return VStack(spacing: 0) {
            // Day-of-week header row
            HStack(spacing: 0) {
                Color.clear
                    .frame(width: railWidth, height: 34)
                ForEach(cols) { col in
                    VStack(spacing: 1) {
                        Text(weekdayLabels[max(0, min(6, col.dayOfWeek - 1))])
                            .font(GokeTypography.labelMedium)
                            .foregroundStyle(theme.ink)
                        Text("\(col.date.month)/\(col.date.day)")
                            .font(GokeTypography.labelSmall)
                            .foregroundStyle(theme.muted)
                            .monospacedDigit()
                    }
                    .frame(width: colWidth, height: 34)
                }
            }
            .background { theme.surfaceHigh }

            // Period rail + timetable body
            HStack(alignment: .top, spacing: 0) {
                // Left period index & time rail
                VStack(spacing: 0) {
                    ForEach(layout.periods) { period in
                        VStack(spacing: 1) {
                            Text("\(period.index)")
                                .font(GokeTypography.labelMedium)
                                .foregroundStyle(theme.ink)
                                .monospacedDigit()
                            if options.showPeriodTimes {
                                Text(period.start.description)
                                    .font(.system(size: 8.5, weight: .regular))
                                    .foregroundStyle(theme.muted)
                                    .monospacedDigit()
                            }
                        }
                        .frame(width: railWidth, height: rowHeight)
                    }
                }
                .background { theme.surfaceHigh }

                // Paper grid canvas + positioned slot cards
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in
                        let rowCount = max(1, layout.periods.count)
                        for r in 0...rowCount {
                            let y = CGFloat(r) * rowHeight
                            var path = Path()
                            path.move(to: CGPoint(x: 0, y: y))
                            path.addLine(to: CGPoint(x: size.width, y: y))
                            context.stroke(path, with: .color(theme.outlineVariant), lineWidth: 0.75)
                        }
                        for c in 0...cols.count {
                            let x = CGFloat(c) * colWidth
                            var path = Path()
                            path.move(to: CGPoint(x: x, y: 0))
                            path.addLine(to: CGPoint(x: x, y: size.height))
                            context.stroke(path, with: .color(theme.outlineVariant), lineWidth: 0.75)
                        }
                    }

                    ForEach(Array(cols.enumerated()), id: \.element.id) { colIndex, column in
                        ForEach(column.slots) { slot in
                            let laneWidth = colWidth / CGFloat(max(1, slot.laneCount))
                            let x = CGFloat(colIndex) * colWidth + CGFloat(slot.laneIndex) * laneWidth + 2
                            let y = CGFloat(slot.normalizedY) * totalGridHeight + 2
                            let width = max(16, laneWidth - 4)
                            let height = max(20, CGFloat(slot.normalizedHeight) * totalGridHeight - 4)

                            slotCard(for: slot)
                                .frame(width: width, height: height, alignment: .topLeading)
                                .offset(x: x, y: y)
                        }
                    }
                }
                .frame(width: dayAreaWidth, height: totalGridHeight, alignment: .topLeading)
                .background { theme.surface }
            }
        }
        .clipShape(.rect(cornerRadius: GokeRadius.md))
        .overlay {
            RoundedRectangle(cornerRadius: GokeRadius.md)
                .stroke(theme.outlineVariant, lineWidth: 1)
        }
    }

    @ViewBuilder
    private func slotCard(for slot: TimetableSlotFrame) -> some View {
        switch slot.kind {
        case .course(let occ):
            let pair = theme.courseColor(index: occ.course.colorIndex)
            VStack(alignment: .leading, spacing: 2) {
                if occ.course.isExam {
                    let display = JxfwSyncMapper.splitExamDisplay(occ.course.name)
                    Text(display.badgeShort)
                        .font(.system(size: 7.5, weight: .bold))
                        .foregroundStyle(pair.foreground)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background {
                            Capsule().fill(pair.foreground.opacity(0.18))
                        }

                    Text(display.title)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(pair.foreground)
                        .lineLimit(3)

                    if let examLoc = JxfwSyncMapper.formatExamLocation(
                        location: occ.room,
                        note: occ.course.note,
                        seatSuffix: "号"
                    ) {
                        Text(examLoc)
                            .font(.system(size: 8.5, weight: .medium))
                            .foregroundStyle(pair.foreground.opacity(0.85))
                            .lineLimit(2)
                    }
                } else {
                    Text(occ.course.name)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(pair.foreground)
                        .lineLimit(3)
                    if !occ.room.isEmpty {
                        Text("@\(occ.room)")
                            .font(.system(size: 8.5, weight: .medium))
                            .foregroundStyle(pair.foreground.opacity(0.85))
                            .lineLimit(2)
                    }
                }
                if options.showTeacher && !occ.course.teacher.isEmpty {
                    Text(occ.course.teacher)
                        .font(.system(size: 8, weight: .regular))
                        .foregroundStyle(pair.foreground.opacity(0.75))
                        .lineLimit(1)
                }
            }
            .padding(4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                RoundedRectangle(cornerRadius: GokeRadius.xs + 2)
                    .fill(pair.background)
            }

        case .taskBlock(let task, _, _):
            VStack(alignment: .leading, spacing: 2) {
                Text(task.title)
                    .font(.system(size: 9.5, weight: .medium))
                    .foregroundStyle(theme.onAccentContainer)
                    .lineLimit(3)
            }
            .padding(4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                RoundedRectangle(cornerRadius: GokeRadius.xs + 2)
                    .fill(theme.accentContainer)
            }

        case .event(let ev):
            let pair = theme.courseColor(index: ev.event.colorIndex)
            VStack(alignment: .leading, spacing: 2) {
                Text(ev.event.title)
                    .font(.system(size: 9.5, weight: .semibold))
                    .foregroundStyle(pair.foreground)
                    .lineLimit(3)
                if !ev.event.location.isEmpty {
                    Text(ev.event.location)
                        .font(.system(size: 8, weight: .regular))
                        .foregroundStyle(pair.foreground.opacity(0.8))
                        .lineLimit(1)
                }
            }
            .padding(4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                RoundedRectangle(cornerRadius: GokeRadius.xs + 2)
                    .fill(pair.background)
            }
        }
    }

    private var footerSection: some View {
        HStack {
            Text(options.watermark)
                .font(GokeTypography.labelSmall)
                .foregroundStyle(theme.muted)
            Spacer()
            Text("共 \(layout.periods.count) 节")
                .font(GokeTypography.labelSmall)
                .foregroundStyle(theme.muted)
                .monospacedDigit()
        }
    }

    private func shortDate(_ date: LocalDate) -> String {
        String(format: "%02d/%02d", date.month, date.day)
    }
}
