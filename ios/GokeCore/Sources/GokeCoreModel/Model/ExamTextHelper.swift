import Foundation

/// Lightweight exam remark/seat/name matching helpers shared by `CourseExpander`,
/// `JxfwSyncMapper`, and `GokeWidgetExtension` without pulling in JXFW networking/parsing.
public enum ExamTextHelper {
    private static let examRemarkIncludeRegex = try! NSRegularExpression(
        pattern: #"考试|测验|小测|机考|机试|笔试|口试|补考|重考|缓考|随堂考|结课考|实验考|(?:期中|期末|随堂|结课)(?:测试|考核)|实验考核|\b(?:midterm|final\s+exam|quiz)\b"#,
        options: [.caseInsensitive]
    )
    private static let examRemarkExcludeRegex = try! NSRegularExpression(
        pattern: #"复习|答疑|辅导|讲评|讲解|动员|不考|免考|无考|取消"#
    )
    private static let examNamePrefixRegex = try! NSRegularExpression(
        pattern: #"^(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*"#
    )
    private static let examNameSuffixRegex = try! NSRegularExpression(
        pattern: #"\s*[:：·\-—]?\s*(?:(?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)$"#
    )
    private static let examSeatRegex = try! NSRegularExpression(
        pattern: #"座位号\s*([^\s·,，;；]+)"#
    )

    /// Normalizes a course/exam name by stripping exam prefixes/suffixes, brackets, and whitespace.
    public static func normalizeCourseNameForExam(_ raw: String) -> String {
        var s = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
            .components(separatedBy: .whitespacesAndNewlines)
            .joined()
        var prev = ""
        while s != prev && !s.isEmpty {
            prev = s
            let r1 = NSRange(location: 0, length: (s as NSString).length)
            s = examNamePrefixRegex.stringByReplacingMatches(in: s, range: r1, withTemplate: "")
            let r2 = NSRange(location: 0, length: (s as NSString).length)
            s = examNameSuffixRegex.stringByReplacingMatches(in: s, range: r2, withTemplate: "")
            s = s.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return s.lowercased()
    }

    public static func isSameCourseForExam(_ examName: String, _ courseName: String) -> Bool {
        let a = normalizeCourseNameForExam(examName)
        let b = normalizeCourseNameForExam(courseName)
        return !a.isEmpty && a == b
    }

    /// Returns true if a course remark represents an exam/quiz arrangement while filtering out
    /// review/Q&A/cancellation notices and software testing topics.
    public static func isExamRemark(_ remark: String) -> Bool {
        let text = remark.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return false }
        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        guard examRemarkIncludeRegex.firstMatch(in: text, range: range) != nil else { return false }
        return examRemarkExcludeRegex.firstMatch(in: text, range: range) == nil
    }

    public static func extractExamSeat(from note: String) -> String? {
        let ns = note as NSString
        let range = NSRange(location: 0, length: ns.length)
        guard let match = examSeatRegex.firstMatch(in: note, range: range),
              match.numberOfRanges > 1
        else {
            return nil
        }
        let captureRange = match.range(at: 1)
        guard captureRange.location != NSNotFound else { return nil }
        let trimmed = ns.substring(with: captureRange).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
