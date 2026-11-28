import Foundation
import GokeCoreModel

/// Parses and organizes course remarks by separating jxfw week-scoped entries
/// (`第X周：授课内容`) from user-authored general remarks.
public enum CourseNotes {
    public static let defaultMaxWeeks = 30

    public struct Parsed: Sendable, Hashable {
        public let currentWeekNotes: [String]
        public let generalNotes: [String]
        public let otherWeekLines: [String]

        public var focusedNotes: [String] {
            CourseNotes.deduplicate(currentWeekNotes + generalNotes)
        }

        public var hasOtherWeeks: Bool {
            !otherWeekLines.isEmpty
        }

        public init(
            currentWeekNotes: [String],
            generalNotes: [String],
            otherWeekLines: [String]
        ) {
            self.currentWeekNotes = currentWeekNotes
            self.generalNotes = generalNotes
            self.otherWeekLines = otherWeekLines
        }
    }

    private static let weekLineRegex = try! NSRegularExpression(pattern: #"^第([\d,\-]+)周：(.*)$"#)

    public static func parse(
        _ note: String,
        week: Int?,
        maxWeeks: Int = defaultMaxWeeks
    ) -> Parsed {
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedNote.isEmpty else {
            return Parsed(currentWeekNotes: [], generalNotes: [], otherWeekLines: [])
        }

        var currentWeek: [String] = []
        var general: [String] = []
        var otherWeeks: [String] = []

        for rawLine in note.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty else { continue }
            guard let (weekSpec, text) = matchWeekLine(line) else {
                general.append(line)
                continue
            }
            let weeks = WeekSets.parse(weekSpec, total: maxWeeks).filter { (1...maxWeeks).contains($0) }
            if let week, weeks.contains(week) {
                if !text.isEmpty {
                    currentWeek.append(text)
                }
            } else {
                otherWeeks.append(line)
            }
        }

        return Parsed(
            currentWeekNotes: deduplicate(currentWeek),
            generalNotes: deduplicate(general),
            otherWeekLines: deduplicate(otherWeeks)
        )
    }

    public static func summaryForWeek(
        _ note: String,
        week: Int?,
        maxWeeks: Int = defaultMaxWeeks
    ) -> String {
        parse(note, week: week, maxWeeks: maxWeeks)
            .focusedNotes
            .joined(separator: "\n")
    }

    public static func stripWeekPrefix(_ line: String) -> String {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let (_, text) = matchWeekLine(trimmed) else { return trimmed }
        return text
    }

    public static func appendUserNote(
        existingNote: String,
        addition: String
    ) -> String {
        let clean = addition.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return existingNote }
        let cur = existingNote.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cur.isEmpty else { return clean }
        let existingLines = cur
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        if existingLines.contains(clean) {
            return existingNote
        }
        return "\(cur)\n\(clean)"
    }

    private static func matchWeekLine(_ line: String) -> (weekSpec: String, text: String)? {
        let ns = line as NSString
        let range = NSRange(location: 0, length: ns.length)
        guard let match = weekLineRegex.firstMatch(in: line, range: range),
              match.numberOfRanges >= 3
        else {
            return nil
        }
        let weekSpec = ns.substring(with: match.range(at: 1))
        let text = ns.substring(with: match.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
        return (weekSpec, text)
    }

    private static func deduplicate(_ items: [String]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        out.reserveCapacity(items.count)
        for item in items where seen.insert(item).inserted {
            out.append(item)
        }
        return out
    }
}
