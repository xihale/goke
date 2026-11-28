import Foundation
import GokeCoreModel

// MARK: - Raw GDUT jxfw Wire Models (matches `core/jxfw/Jxfw.kt`)

/// Full-semester timetable row (`var kbxx` element from `xsgrkbcx!xsAllKbList.action`).
public struct JxfwLesson: Sendable, Hashable, Codable {
    public var kcmc: String
    public var kcbh: String
    public var jxbmc: String
    public var jcdm2: String
    public var zcs: String
    public var xq: String
    public var jxcdmcs: String
    public var teaxms: String
    public var kcrwdm: String
    public var sknrjj: String
    public var bz: String

    public var remark: String {
        JxfwParse.mergeRemarks(sknrjj, bz)
    }

    public init(
        kcmc: String,
        kcbh: String = "",
        jxbmc: String = "",
        jcdm2: String,
        zcs: String,
        xq: String,
        jxcdmcs: String = "",
        teaxms: String = "",
        kcrwdm: String = "",
        sknrjj: String = "",
        bz: String = ""
    ) {
        self.kcmc = kcmc
        self.kcbh = kcbh
        self.jxbmc = jxbmc
        self.jcdm2 = jcdm2
        self.zcs = zcs
        self.xq = xq
        self.jxcdmcs = jxcdmcs
        self.teaxms = teaxms
        self.kcrwdm = kcrwdm
        self.sknrjj = sknrjj
        self.bz = bz
    }

    private enum CodingKeys: String, CodingKey {
        case kcmc, kcbh, jxbmc, jcdm2, zcs, xq, jxcdmcs, teaxms, kcrwdm, sknrjj, bz
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.kcmc = try c.decode(String.self, forKey: .kcmc)
        self.kcbh = try c.decodeIfPresent(String.self, forKey: .kcbh) ?? ""
        self.jxbmc = try c.decodeIfPresent(String.self, forKey: .jxbmc) ?? ""
        self.jcdm2 = try c.decode(String.self, forKey: .jcdm2)
        self.zcs = try c.decode(String.self, forKey: .zcs)
        self.xq = try c.decode(String.self, forKey: .xq)
        self.jxcdmcs = try c.decodeIfPresent(String.self, forKey: .jxcdmcs) ?? ""
        self.teaxms = try c.decodeIfPresent(String.self, forKey: .teaxms) ?? ""
        self.kcrwdm = try c.decodeIfPresent(String.self, forKey: .kcrwdm) ?? ""
        self.sknrjj = try c.decodeIfPresent(String.self, forKey: .sknrjj) ?? ""
        self.bz = try c.decodeIfPresent(String.self, forKey: .bz) ?? ""
    }
}

/// Weekly timetable row (`data[0]` element from `xsgrkbcx!getKbRq.action`). Note singular `jxcdmc`.
public struct JxfwInstance: Sendable, Hashable, Codable {
    public var kcmc: String
    public var jcdm2: String
    public var xq: String
    public var jxcdmc: String
    public var teaxms: String
    public var zc: String
    public var kcbh: String
    public var sknrjj: String
    public var bz: String

    public var remark: String {
        JxfwParse.mergeRemarks(sknrjj, bz)
    }

    public init(
        kcmc: String,
        jcdm2: String,
        xq: String,
        jxcdmc: String = "",
        teaxms: String = "",
        zc: String = "",
        kcbh: String = "",
        sknrjj: String = "",
        bz: String = ""
    ) {
        self.kcmc = kcmc
        self.jcdm2 = jcdm2
        self.xq = xq
        self.jxcdmc = jxcdmc
        self.teaxms = teaxms
        self.zc = zc
        self.kcbh = kcbh
        self.sknrjj = sknrjj
        self.bz = bz
    }

    private enum CodingKeys: String, CodingKey {
        case kcmc, jcdm2, xq, jxcdmc, teaxms, zc, kcbh, sknrjj, bz
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.kcmc = try c.decode(String.self, forKey: .kcmc)
        self.jcdm2 = try c.decode(String.self, forKey: .jcdm2)
        self.xq = try c.decode(String.self, forKey: .xq)
        self.jxcdmc = try c.decodeIfPresent(String.self, forKey: .jxcdmc) ?? ""
        self.teaxms = try c.decodeIfPresent(String.self, forKey: .teaxms) ?? ""
        self.zc = try c.decodeIfPresent(String.self, forKey: .zc) ?? ""
        self.kcbh = try c.decodeIfPresent(String.self, forKey: .kcbh) ?? ""
        self.sknrjj = try c.decodeIfPresent(String.self, forKey: .sknrjj) ?? ""
        self.bz = try c.decodeIfPresent(String.self, forKey: .bz) ?? ""
    }
}

/// Weekly calendar date row (`data[1]` element from `xsgrkbcx!getKbRq.action`).
public struct JxfwDay: Sendable, Hashable, Codable {
    public var xqmc: String
    public var rq: String

    public init(xqmc: String = "", rq: String = "") {
        self.xqmc = xqmc
        self.rq = rq
    }

    private enum CodingKeys: String, CodingKey {
        case xqmc, rq
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.xqmc = try c.decodeIfPresent(String.self, forKey: .xqmc) ?? ""
        self.rq = try c.decodeIfPresent(String.self, forKey: .rq) ?? ""
    }
}

/// Exam schedule row (`rows` element from `xsksap!getDataList.action`).
public struct JxfwExam: Sendable, Hashable, Codable {
    public var ksrcdm: String
    public var kcmc: String
    public var kcbh: String
    public var ksrq: String
    public var kssj: String
    public var zc: String
    public var xq: String
    public var jcdm2: String
    public var kscdmc: String
    public var xqmc: String
    public var kslbmc: String
    public var ksaplxmc: String
    public var zwh: String
    public var jkteaxms: String

    public init(
        ksrcdm: String,
        kcmc: String,
        kcbh: String = "",
        ksrq: String = "",
        kssj: String = "",
        zc: String = "",
        xq: String = "",
        jcdm2: String = "",
        kscdmc: String = "",
        xqmc: String = "",
        kslbmc: String = "",
        ksaplxmc: String = "",
        zwh: String = "",
        jkteaxms: String = ""
    ) {
        self.ksrcdm = ksrcdm
        self.kcmc = kcmc
        self.kcbh = kcbh
        self.ksrq = ksrq
        self.kssj = kssj
        self.zc = zc
        self.xq = xq
        self.jcdm2 = jcdm2
        self.kscdmc = kscdmc
        self.xqmc = xqmc
        self.kslbmc = kslbmc
        self.ksaplxmc = ksaplxmc
        self.zwh = zwh
        self.jkteaxms = jkteaxms
    }

    private enum CodingKeys: String, CodingKey {
        case ksrcdm, kcmc, kcbh, ksrq, kssj, zc, xq, jcdm2, kscdmc, xqmc, kslbmc, ksaplxmc, zwh, jkteaxms
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.ksrcdm = try c.decode(String.self, forKey: .ksrcdm)
        self.kcmc = try c.decode(String.self, forKey: .kcmc)
        self.kcbh = try c.decodeIfPresent(String.self, forKey: .kcbh) ?? ""
        self.ksrq = try c.decodeIfPresent(String.self, forKey: .ksrq) ?? ""
        self.kssj = try c.decodeIfPresent(String.self, forKey: .kssj) ?? ""
        self.zc = try c.decodeIfPresent(String.self, forKey: .zc) ?? ""
        self.xq = try c.decodeIfPresent(String.self, forKey: .xq) ?? ""
        self.jcdm2 = try c.decodeIfPresent(String.self, forKey: .jcdm2) ?? ""
        self.kscdmc = try c.decodeIfPresent(String.self, forKey: .kscdmc) ?? ""
        self.xqmc = try c.decodeIfPresent(String.self, forKey: .xqmc) ?? ""
        self.kslbmc = try c.decodeIfPresent(String.self, forKey: .kslbmc) ?? ""
        self.ksaplxmc = try c.decodeIfPresent(String.self, forKey: .ksaplxmc) ?? ""
        self.zwh = try c.decodeIfPresent(String.self, forKey: .zwh) ?? ""
        self.jkteaxms = try c.decodeIfPresent(String.self, forKey: .jkteaxms) ?? ""
    }
}

/// Course grade row (`rows` element from `xskccjxx!getDataList.action`).
public struct JxfwGrade: Sendable, Hashable, Codable {
    public var term: String
    public var code: String
    public var name: String
    public var credit: Double?
    public var score: String
    public var gpa: Double?
    public var category: String
    public var studyMode: String
    public var examType: String
    public var status: String

    public init(
        term: String,
        code: String = "",
        name: String,
        credit: Double? = nil,
        score: String = "",
        gpa: Double? = nil,
        category: String = "",
        studyMode: String = "",
        examType: String = "",
        status: String = ""
    ) {
        self.term = term
        self.code = code
        self.name = name
        self.credit = credit
        self.score = score
        self.gpa = gpa
        self.category = category
        self.studyMode = studyMode
        self.examType = examType
        self.status = status
    }

    private enum CodingKeys: String, CodingKey {
        case term, code, name, credit, score, gpa, category, studyMode, examType, status
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.term = try c.decode(String.self, forKey: .term)
        self.code = try c.decodeIfPresent(String.self, forKey: .code) ?? ""
        self.name = try c.decode(String.self, forKey: .name)
        self.credit = try c.decodeIfPresent(Double.self, forKey: .credit)
        self.score = try c.decodeIfPresent(String.self, forKey: .score) ?? ""
        self.gpa = try c.decodeIfPresent(Double.self, forKey: .gpa)
        self.category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        self.studyMode = try c.decodeIfPresent(String.self, forKey: .studyMode) ?? ""
        self.examType = try c.decodeIfPresent(String.self, forKey: .examType) ?? ""
        self.status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
    }

    public var filterKey: String { "\(term)|\(code)|\(name)" }
}

/// Aggregated grade summary (credits + weighted GPA).
public struct JxfwGradeSummary: Sendable, Hashable {
    public var totalCredits: Double
    public var gpaCredits: Double
    public var weightedGpa: Double?
    public var count: Int
    public var earnedCredits: Double

    public init(
        totalCredits: Double,
        gpaCredits: Double,
        weightedGpa: Double?,
        count: Int,
        earnedCredits: Double = 0.0
    ) {
        self.totalCredits = totalCredits
        self.gpaCredits = gpaCredits
        self.weightedGpa = weightedGpa
        self.count = count
        self.earnedCredits = earnedCredits
    }
}

// MARK: - Pure jxfw Parsers (matches `JxfwParse` in Kotlin)

public enum JxfwParse {
    private static let failScoreTokens: Set<String> = [
        "不及格", "不合格", "不通过", "未通过", "缺考", "旷考", "作弊", "违纪", "取消资格", "缓考",
    ]

    private static let placeholderZeroScores: Set<String> = [
        "", "0", "0.0", "缓考", "缺考", "旷考",
    ]

    /// Merges raw remark parts, trimming whitespace and deduplicating while preserving order.
    public static func mergeRemarks(_ parts: String...) -> String {
        var seen = Set<String>()
        var out: [String] = []
        for raw in parts {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, seen.insert(trimmed).inserted {
                out.append(trimmed)
            }
        }
        return out.joined(separator: " · ")
    }

    /// Finds week 1 Monday (`xqmc == "1"`) from weekly day rows.
    public static func firstMonday(from days: [JxfwDay]) -> LocalDate? {
        guard let rawDate = days.first(where: { $0.xqmc == "1" })?.rq else { return nil }
        return LocalDate.parse(rawDate)
    }

    /// Parses `"14:00--16:00"` into `(14:00, 16:00)`.
    public static func parseKssj(_ text: String) -> (LocalTime, LocalTime)? {
        let parts = text.components(separatedBy: "--").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard parts.count == 2,
              let start = LocalTime.parse(parts[0]),
              let end = LocalTime.parse(parts[1])
        else {
            return nil
        }
        return (start, end)
    }

    /// Parses period code `"02,01"` into sorted, deduplicated `[1, 2]`.
    public static func periods(_ text: String) -> [Int] {
        let nums = text.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return Array(Set(nums)).sorted()
    }

    /// Parses weeks `"10,12,11"` into `{10, 11, 12}`.
    public static func weeks(_ text: String) -> Set<Int> {
        Set(text.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) })
    }

    /// Parses day-of-week `"1"..."7"`.
    public static func dayOfWeek(_ xq: String) -> Int? {
        guard let n = Int(xq.trimmingCharacters(in: .whitespacesAndNewlines)), (1...7).contains(n) else {
            return nil
        }
        return n
    }

    /// Derives GDUT term code from calendar date:
    /// month >= 8 -> `YYYY01`, month == 1 -> `(YYYY-1)01`, months 2..7 -> `(YYYY-1)02`.
    public static func currentTerm(today: LocalDate) -> String {
        if today.month >= 8 {
            return "\(today.year)01"
        } else if today.month == 1 {
            return "\(today.year - 1)01"
        } else {
            return "\(today.year - 1)02"
        }
    }

    /// Converts `"202601"` -> `"2026-2027 第一学期"`; unknown formats returned as-is.
    public static func termLabel(_ termCode: String) -> String {
        let trimmed = termCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count == 6,
              let year = Int(trimmed.prefix(4))
        else {
            return termCode
        }
        switch trimmed.suffix(2) {
        case "01": return "\(year)-\(year + 1) 第一学期"
        case "02": return "\(year)-\(year + 1) 第二学期"
        default: return termCode
        }
    }

    /// Returns true if the grade entry is explicitly marked as deferred (`缓考`).
    public static func isDeferredGrade(_ grade: JxfwGrade) -> Bool {
        grade.status.contains("缓考")
            || grade.examType.contains("缓考")
            || grade.score.trimmingCharacters(in: .whitespacesAndNewlines) == "缓考"
    }

    /// Returns true if the grade entry has no score text and no GPA yet.
    public static func isUngradedGrade(_ grade: JxfwGrade) -> Bool {
        grade.score.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && grade.gpa == nil
    }

    /// Identifies `filterKey`s of deferred/ungraded/0-GPA placeholder rows superseded by a later passing row for the same course.
    public static func supersededGradeKeys(_ grades: [JxfwGrade]) -> Set<String> {
        guard grades.count >= 2 else { return [] }
        var result: Set<String> = []
        let byCourse = Dictionary(grouping: grades) { g -> String in
            let code = g.code.trimmingCharacters(in: .whitespacesAndNewlines)
            return code.isEmpty ? g.name.trimmingCharacters(in: .whitespacesAndNewlines) : code
        }
        for (courseKey, group) in byCourse {
            guard !courseKey.isEmpty, group.count >= 2 else { continue }
            guard group.contains(where: { isPassedGrade($0) }) else { continue }
            for g in group {
                let trimmedScore = g.score.trimmingCharacters(in: .whitespacesAndNewlines)
                let isZeroPlaceholder = g.gpa == 0.0 && placeholderZeroScores.contains(trimmedScore)
                if isDeferredGrade(g) || isUngradedGrade(g) || isZeroPlaceholder {
                    result.insert(g.filterKey)
                }
            }
        }
        return result
    }

    public static func isPassedGrade(_ grade: JxfwGrade) -> Bool {
        if isDeferredGrade(grade) { return false }
        guard let c = grade.credit, c > 0.0 else { return false }
        if let gpa = grade.gpa { return gpa > 0.0 }
        let s = grade.score.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return false }
        if let numeric = Double(s) { return numeric >= 60.0 }
        return !failScoreTokens.contains(s)
    }

    public static func gradeCategories(_ grades: [JxfwGrade]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for g in grades {
            for raw in [g.category, g.studyMode] {
                let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                if !s.isEmpty, seen.insert(s).inserted {
                    out.append(s)
                }
            }
        }
        return out
    }

    public static func filterGrades(
        _ grades: [JxfwGrade],
        excludedKeys: Set<String> = [],
        excludedCategories: Set<String> = []
    ) -> [JxfwGrade] {
        if excludedKeys.isEmpty && excludedCategories.isEmpty { return grades }
        return grades.filter { g in
            !excludedKeys.contains(g.filterKey)
                && (g.category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !excludedCategories.contains(g.category))
                && (g.studyMode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !excludedCategories.contains(g.studyMode))
        }
    }

    public static func summarizeGrades(
        _ grades: [JxfwGrade],
        excludedKeys: Set<String> = [],
        excludedCategories: Set<String> = [],
        supersededKeys: Set<String>? = nil
    ) -> JxfwGradeSummary {
        let effectiveSuperseded = supersededKeys ?? supersededGradeKeys(grades)
        let active = filterGrades(grades, excludedKeys: excludedKeys, excludedCategories: excludedCategories)
        var totalCredits = 0.0
        var earnedCredits = 0.0
        var gpaCredits = 0.0
        var weightedPoints = 0.0
        for g in active {
            if isDeferredGrade(g) || isUngradedGrade(g) || effectiveSuperseded.contains(g.filterKey) {
                continue
            }
            guard let c = g.credit, c > 0.0 else { continue }
            totalCredits += c
            if isPassedGrade(g) { earnedCredits += c }
            guard let p = g.gpa else { continue }
            gpaCredits += c
            weightedPoints += c * p
        }
        let weightedGpa = gpaCredits > 0.0 ? (weightedPoints / gpaCredits) : nil
        return JxfwGradeSummary(
            totalCredits: totalCredits,
            gpaCredits: gpaCredits,
            weightedGpa: weightedGpa,
            count: active.count,
            earnedCredits: earnedCredits
        )
    }
}
