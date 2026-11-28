import Foundation

// MARK: - Physical Fitness Test Domain Models & Pure Helpers (matches `core/jxfw/Tzcs.kt`)

public struct TzcsItem: Sendable, Hashable, Codable {
    public var name: String
    public var rawValue: String
    public var itemScore: String
    public var grade: String
    public var weightedScore: Double?
    public var bonusScore: Double?

    public init(
        name: String,
        rawValue: String = "",
        itemScore: String = "",
        grade: String = "",
        weightedScore: Double? = nil,
        bonusScore: Double? = nil
    ) {
        self.name = name
        self.rawValue = rawValue
        self.itemScore = itemScore
        self.grade = grade
        self.weightedScore = weightedScore
        self.bonusScore = bonusScore
    }
}

public struct TzcsYearReport: Sendable, Hashable, Codable {
    public var year: Int
    public var studentName: String
    public var studentNo: String
    public var totalScore: String
    public var totalGrade: String
    public var items: [TzcsItem]
    public var historyYears: [Int]

    public init(
        year: Int,
        studentName: String = "",
        studentNo: String = "",
        totalScore: String = "",
        totalGrade: String = "",
        items: [TzcsItem] = [],
        historyYears: [Int] = []
    ) {
        self.year = year
        self.studentName = studentName
        self.studentNo = studentNo
        self.totalScore = totalScore
        self.totalGrade = totalGrade
        self.items = items
        self.historyYears = historyYears
    }
}

public struct TzcsSummary: Sendable, Hashable {
    public var yearCount: Int
    public var recordedYearCount: Int
    public var bestScore: Double?
    public var bestYear: Int?
    public var latestReport: TzcsYearReport?

    public init(
        yearCount: Int,
        recordedYearCount: Int,
        bestScore: Double?,
        bestYear: Int?,
        latestReport: TzcsYearReport?
    ) {
        self.yearCount = yearCount
        self.recordedYearCount = recordedYearCount
        self.bestScore = bestScore
        self.bestYear = bestYear
        self.latestReport = latestReport
    }
}

public enum TzcsErrorKind: String, Sendable, Hashable, Codable {
    case network
    case credentials
    case captcha
    case session
    case protocolError
    case unsupported
    case other
}

public struct TzcsError: Error, Sendable, Hashable, LocalizedError {
    public let message: String
    public let kind: TzcsErrorKind

    public init(_ message: String, kind: TzcsErrorKind = .other) {
        self.message = message
        self.kind = kind
    }

    public var errorDescription: String? { message }
}

// MARK: - Pure Report Logic (`TzcsParse`)

public enum TzcsParse {
    private static let maleOnlyItemKeys = ["1000米", "引体向上"]
    private static let femaleOnlyItemKeys = ["800米", "仰卧起坐"]

    public static func hasRecordedScores(_ report: TzcsYearReport) -> Bool {
        if !report.totalGrade.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        if let totalNum = Double(report.totalScore), totalNum > 0.0 { return true }
        return report.items.contains(where: isRecorded)
    }

    public static func filterApplicableItems(_ reports: [TzcsYearReport]) -> [TzcsYearReport] {
        let hasMale = reports.contains { r in
            r.items.contains { item in
                isRecorded(item) && maleOnlyItemKeys.contains { item.name.contains($0) }
            }
        }
        let hasFemale = reports.contains { r in
            r.items.contains { item in
                isRecorded(item) && femaleOnlyItemKeys.contains { item.name.contains($0) }
            }
        }
        let opposite: [String]
        if hasMale {
            opposite = femaleOnlyItemKeys
        } else if hasFemale {
            opposite = maleOnlyItemKeys
        } else {
            opposite = []
        }

        return reports.map { report in
            var copy = report
            copy.items = report.items.filter { item in
                !isEmptyRow(item) && !opposite.contains { item.name.contains($0) }
            }
            return copy
        }
    }

    public static func mergeReports(existing: [TzcsYearReport], incoming: [TzcsYearReport]) -> [TzcsYearReport] {
        if incoming.isEmpty {
            return existing.sorted { $0.year > $1.year }
        }
        var byYear: [Int: TzcsYearReport] = [:]
        for r in existing { byYear[r.year] = r }
        for r in incoming { byYear[r.year] = r }
        return byYear.values.sorted { $0.year > $1.year }
    }

    public static func summarizeReports(_ reports: [TzcsYearReport]) -> TzcsSummary {
        let sorted = reports.sorted { $0.year > $1.year }
        let recorded = sorted.filter(hasRecordedScores)
        var bestScore: Double?
        var bestYear: Int?
        for r in recorded {
            guard let score = Double(r.totalScore), score > 0.0 else { continue }
            if bestScore == nil || score > bestScore! {
                bestScore = score
                bestYear = r.year
            }
        }
        return TzcsSummary(
            yearCount: sorted.count,
            recordedYearCount: recorded.count,
            bestScore: bestScore,
            bestYear: bestYear,
            latestReport: recorded.first ?? sorted.first
        )
    }

    private static func isRecorded(_ item: TzcsItem) -> Bool {
        !item.rawValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !item.itemScore.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !item.grade.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private static func isEmptyRow(_ item: TzcsItem) -> Bool {
        !isRecorded(item) && item.weightedScore == nil && item.bonusScore == nil
    }
}
