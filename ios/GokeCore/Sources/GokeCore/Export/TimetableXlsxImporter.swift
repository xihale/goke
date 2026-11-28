import Foundation
import GokeCoreModel
#if canImport(FoundationXML)
import FoundationXML
#endif
#if canImport(Compression)
import Compression
#endif

// MARK: - OOXML Worksheet Models

/// Merged cell range in a worksheet (1-based inclusive row/column indices).
public struct XlsxCellRange: Sendable, Hashable {
    public let r1: Int
    public let c1: Int
    public let r2: Int
    public let c2: Int

    public init(r1: Int, c1: Int, r2: Int, c2: Int) {
        self.r1 = r1
        self.c1 = c1
        self.r2 = r2
        self.c2 = c2
    }
}

/// 1-based worksheet cell coordinate.
public struct XlsxCellCoord: Sendable, Hashable {
    public let row: Int
    public let col: Int

    public init(row: Int, col: Int) {
        self.row = row
        self.col = col
    }
}

/// Parsed worksheet with sparse text cells and merged ranges.
public struct XlsxSheet: Sendable, Hashable {
    public let name: String
    public let cells: [XlsxCellCoord: String]
    public let merges: [XlsxCellRange]

    public var maxRow: Int {
        cells.keys.map(\.row).max() ?? 0
    }

    public var maxCol: Int {
        cells.keys.map(\.col).max() ?? 0
    }

    public init(
        name: String,
        cells: [XlsxCellCoord: String],
        merges: [XlsxCellRange] = []
    ) {
        self.name = name
        self.cells = cells
        self.merges = merges
    }

    public func text(row: Int, col: Int) -> String {
        cells[XlsxCellCoord(row: row, col: col)] ?? ""
    }

    public func mergeAt(row: Int, col: Int) -> XlsxCellRange? {
        merges.first { range in
            row >= range.r1 && row <= range.r2 && col >= range.c1 && col <= range.c2
        }
    }
}

// MARK: - Lightweight Pure-Swift XLSX Reader (ZIP STORE/DEFLATE + XMLParser)

public enum XlsxReader {
    public static func read(data: Data) -> ImportResult<[XlsxSheet]> {
        guard let files = MiniZipReader.extractXmlEntries(from: data) else {
            return .failure("not_xlsx")
        }
        guard let wbData = files["xl/workbook.xml"] else {
            return .failure("not_xlsx")
        }

        do {
            let shared: [String]
            if let ssData = files["xl/sharedStrings.xml"] {
                shared = try parseSharedStrings(ssData)
            } else {
                shared = []
            }

            let rels: [String: String]
            if let relsData = files["xl/_rels/workbook.xml.rels"] {
                rels = try parseRels(relsData)
            } else {
                rels = [:]
            }

            let sheetEntries = try parseWorkbookSheets(wbData)
            var sheets: [XlsxSheet] = []
            var warnings: [String] = []

            for (idx, entry) in sheetEntries.enumerated() {
                let target: String
                if let rawTarget = rels[entry.rid] {
                    target = normalizeWorkbookTarget(rawTarget)
                } else {
                    target = "xl/worksheets/sheet\(idx + 1).xml"
                }

                guard let sheetData = files[target] else {
                    warnings.append("missing_sheet:\(entry.name)")
                    continue
                }
                let sheet = try parseSheet(name: entry.name, data: sheetData, shared: shared)
                sheets.append(sheet)
            }

            return ImportResult(value: sheets, warnings: warnings)
        } catch {
            return .failure("bad_xlsx:\(error.localizedDescription)")
        }
    }

    /// Parses an A1-style reference like `"B12"` into `XlsxCellCoord(row: 12, col: 2)`.
    public static func parseRef(_ ref: String) -> XlsxCellCoord? {
        let trimmed = ref.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        var col = 0
        var idx = trimmed.startIndex
        var hasLetters = false
        while idx < trimmed.endIndex {
            let scalar = trimmed[idx].unicodeScalars.first?.value ?? 0
            if scalar >= 65 && scalar <= 90 { // A-Z
                col = col * 26 + Int(scalar - 64)
                hasLetters = true
                idx = trimmed.index(after: idx)
            } else if scalar >= 97 && scalar <= 122 { // a-z
                col = col * 26 + Int(scalar - 96)
                hasLetters = true
                idx = trimmed.index(after: idx)
            } else {
                break
            }
        }
        guard hasLetters, idx < trimmed.endIndex,
              let row = Int(trimmed[idx...]), row > 0, col > 0 else {
            return nil
        }
        return XlsxCellCoord(row: row, col: col)
    }

    private static func normalizeWorkbookTarget(_ target: String) -> String {
        var path = target.replacingOccurrences(of: "\\", with: "/")
        while path.hasPrefix("/") {
            path.removeFirst()
        }
        if path.hasPrefix("xl/") {
            return path
        }
        if path.hasPrefix("../") {
            return String(path.dropFirst(3))
        }
        return "xl/\(path)"
    }

    private struct WorkbookSheetEntry {
        let name: String
        let rid: String
    }

    private static func parseWorkbookSheets(_ data: Data) throws -> [WorkbookSheetEntry] {
        let delegate = WorkbookXMLDelegate()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? XlsxParseError.invalidXML("workbook.xml")
        }
        return delegate.sheets
    }

    private static func parseRels(_ data: Data) throws -> [String: String] {
        let delegate = RelsXMLDelegate()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? XlsxParseError.invalidXML("workbook.xml.rels")
        }
        return delegate.rels
    }

    private static func parseSharedStrings(_ data: Data) throws -> [String] {
        let delegate = SharedStringsXMLDelegate()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? XlsxParseError.invalidXML("sharedStrings.xml")
        }
        return delegate.strings
    }

    private static func parseSheet(name: String, data: Data, shared: [String]) throws -> XlsxSheet {
        let delegate = WorksheetXMLDelegate(shared: shared)
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? XlsxParseError.invalidXML(name)
        }
        return XlsxSheet(name: name, cells: delegate.cells, merges: delegate.merges)
    }

    private enum XlsxParseError: Error, LocalizedError {
        case invalidXML(String)

        var errorDescription: String? {
            switch self {
            case .invalidXML(let part):
                return "XML 解析失败：\(part)"
            }
        }
    }

    private final class WorkbookXMLDelegate: NSObject, XMLParserDelegate {
        var sheets: [WorkbookSheetEntry] = []

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String]
        ) {
            if localName(elementName) == "sheet" {
                let name = attributeDict["name"] ?? ""
                let rid = attributeDict["r:id"] ?? attributeDict["id"] ?? ""
                sheets.append(WorkbookSheetEntry(name: name, rid: rid))
            }
        }
    }

    private final class RelsXMLDelegate: NSObject, XMLParserDelegate {
        var rels: [String: String] = [:]

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String]
        ) {
            if localName(elementName) == "Relationship",
               let id = attributeDict["Id"],
               let target = attributeDict["Target"] {
                rels[id] = target
            }
        }
    }

    private final class SharedStringsXMLDelegate: NSObject, XMLParserDelegate {
        var strings: [String] = []
        private var inSi = false
        private var inT = false
        private var skipDepth = 0
        private var current = ""

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String]
        ) {
            let name = localName(elementName)
            if name == "si" {
                inSi = true
                current = ""
                skipDepth = 0
            } else if inSi {
                if name == "rPh" || name == "phoneticPr" {
                    skipDepth += 1
                } else if skipDepth == 0 && name == "t" {
                    inT = true
                }
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            if inSi && inT && skipDepth == 0 {
                current.append(string)
            }
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            let name = localName(elementName)
            if name == "si" {
                strings.append(current)
                inSi = false
                inT = false
                skipDepth = 0
            } else if inSi {
                if name == "rPh" || name == "phoneticPr" {
                    skipDepth = max(0, skipDepth - 1)
                } else if name == "t" {
                    inT = false
                }
            }
        }
    }

    private final class WorksheetXMLDelegate: NSObject, XMLParserDelegate {
        private let shared: [String]
        var cells: [XlsxCellCoord: String] = [:]
        var merges: [XlsxCellRange] = []

        private var currentCoord: XlsxCellCoord?
        private var currentType: String = ""
        private var inV = false
        private var inT = false
        private var skipDepth = 0
        private var valueBuffer = ""
        private var inlineBuffer = ""

        init(shared: [String]) {
            self.shared = shared
        }

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String]
        ) {
            let name = localName(elementName)
            switch name {
            case "c":
                currentCoord = attributeDict["r"].flatMap { XlsxReader.parseRef($0) }
                currentType = attributeDict["t"] ?? ""
                valueBuffer = ""
                inlineBuffer = ""
                inV = false
                inT = false
                skipDepth = 0
            case "v":
                if currentCoord != nil {
                    inV = true
                }
            case "rPh", "phoneticPr":
                if currentCoord != nil {
                    skipDepth += 1
                }
            case "t":
                if currentCoord != nil && skipDepth == 0 {
                    inT = true
                }
            case "mergeCell":
                if let ref = attributeDict["ref"] {
                    let parts = ref.split(separator: ":", maxSplits: 1)
                    let a = String(parts[0])
                    let b = parts.count > 1 ? String(parts[1]) : a
                    if let p1 = XlsxReader.parseRef(a), let p2 = XlsxReader.parseRef(b) {
                        merges.append(XlsxCellRange(r1: p1.row, c1: p1.col, r2: p2.row, c2: p2.col))
                    }
                }
            default:
                break
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            if inV {
                valueBuffer.append(string)
            } else if inT && skipDepth == 0 {
                inlineBuffer.append(string)
            }
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            let name = localName(elementName)
            switch name {
            case "v":
                inV = false
            case "rPh", "phoneticPr":
                skipDepth = max(0, skipDepth - 1)
            case "t":
                inT = false
            case "c":
                if let coord = currentCoord {
                    let rawText: String
                    switch currentType {
                    case "s":
                        if let idx = Int(valueBuffer.trimmingCharacters(in: .whitespacesAndNewlines)),
                           idx >= 0 && idx < shared.count {
                            rawText = shared[idx]
                        } else {
                            rawText = ""
                        }
                    case "inlineStr":
                        rawText = inlineBuffer
                    default:
                        rawText = valueBuffer
                    }
                    let normalized = rawText
                        .replacingOccurrences(of: "\r\n", with: "\n")
                        .replacingOccurrences(of: "\r", with: "\n")
                    if !normalized.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        cells[coord] = normalized
                    }
                }
                currentCoord = nil
                currentType = ""
                inV = false
                inT = false
                skipDepth = 0
            default:
                break
            }
        }
    }

    private static func localName(_ elementName: String) -> String {
        if let idx = elementName.lastIndex(of: ":") {
            return String(elementName[elementName.index(after: idx)...])
        }
        return elementName
    }
}

// MARK: - Structured OOXML (.xlsx) Timetable Importer

/// Pure-Swift `.xlsx` timetable importer matching Android `XlsxTimetableImporter`:
/// supports both multi-sheet Goke-exported `.xlsx` (one sheet per week) and common
/// single-sheet university grid `.xlsx` formats with inline week ranges (e.g. `1-16周`, `1-15周(单)`).
public enum TimetableXlsxImporter {
    private static let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    private static let englishDayPrefixes = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

    private struct RawCell {
        var week: Int
        var dow: Int
        var sp: Int
        var ep: Int
        var name: String
        var room: String
        var teacher: String
    }

    private struct CourseKey: Hashable {
        let name: String
        let room: String
        let teacher: String
        let dow: Int
        let sp: Int
        let ep: Int
    }

    public static func `import`(
        data: Data,
        semester: Semester,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        idGenerator: () -> String = { UUID().uuidString }
    ) -> ImportResult<TimetableImport> {
        let sheetsResult = XlsxReader.read(data: data)
        guard let sheets = sheetsResult.value else {
            return ImportResult(value: nil, warnings: sheetsResult.warnings, error: sheetsResult.error)
        }
        let imported = fromSheets(sheets, semester: semester, now: now, idGenerator: idGenerator)
        return ImportResult(
            value: imported.value,
            warnings: sheetsResult.warnings + imported.warnings,
            error: imported.error
        )
    }

    public static func fromSheets(
        _ sheets: [XlsxSheet],
        semester: Semester,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        idGenerator: () -> String = { UUID().uuidString }
    ) -> ImportResult<TimetableImport> {
        var warnings: [String] = []
        var raw: [RawCell] = []

        let isSingleUnnumberedSheet = sheets.count == 1 && firstPositiveInt(in: sheets[0].name) == nil

        for (idx, sheet) in sheets.enumerated() {
            let sheetWeek = firstPositiveInt(in: sheet.name) ?? (idx + 1)
            guard let header = findHeader(sheet) else {
                warnings.append("no_header:\(sheet.name)")
                continue
            }
            let dayCols = header.dayCols
            let headerRow = header.headerRow
            let labelCol = max(1, (dayCols.values.min() ?? 2) - 1)

            var rowPeriod: [Int: Int] = [:]
            if headerRow + 1 <= sheet.maxRow {
                for r in (headerRow + 1)...sheet.maxRow {
                    let label = sheet.text(row: r, col: labelCol)
                    rowPeriod[r] = firstPositiveInt(in: label) ?? (r - headerRow)
                }
            }

            for (dow, col) in dayCols {
                guard headerRow + 1 <= sheet.maxRow else { continue }
                for r in (headerRow + 1)...sheet.maxRow {
                    let text = sheet.text(row: r, col: col).trimmingCharacters(in: .whitespacesAndNewlines)
                    if text.isEmpty { continue }
                    if let merge = sheet.mergeAt(row: r, col: col),
                       merge.r1 != r || merge.c1 != col {
                        continue
                    }
                    let r2 = sheet.mergeAt(row: r, col: col)?.r2 ?? r
                    guard let sp = rowPeriod[r] else { continue }
                    let ep = max(sp, rowPeriod[r2] ?? sp)

                    // Split Goke conflict cells joined with "\n───\n"
                    let blocks = splitConflictBlocks(text)
                    for block in blocks {
                        let lines = block
                            .split(separator: "\n", omittingEmptySubsequences: true)
                            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                        guard let name = lines.first else { continue }

                        if isSingleUnnumberedSheet,
                           let parsed = extractInlineWeeks(from: Array(lines.dropFirst())) {
                            let room = parsed.remainingLines.first ?? ""
                            let teacher = parsed.remainingLines.count > 1 ? parsed.remainingLines[1] : ""
                            for w in parsed.weeks {
                                raw.append(RawCell(
                                    week: w,
                                    dow: dow,
                                    sp: sp,
                                    ep: ep,
                                    name: name,
                                    room: room,
                                    teacher: teacher
                                ))
                            }
                        } else {
                            let room = lines.count > 1 ? lines[1] : ""
                            let teacher = lines.count > 2 ? lines[2] : ""
                            raw.append(RawCell(
                                week: sheetWeek,
                                dow: dow,
                                sp: sp,
                                ep: ep,
                                name: name,
                                room: room,
                                teacher: teacher
                            ))
                        }
                    }
                }
            }
        }

        if raw.isEmpty {
            return .failure("no_courses", warnings: warnings)
        }

        // Merge unmerged adjacent cells with identical content into continuous period spans
        struct MergeGroupKey: Hashable {
            let week: Int
            let dow: Int
            let name: String
            let room: String
            let teacher: String
        }
        var grouped: [MergeGroupKey: [RawCell]] = [:]
        var groupOrder: [MergeGroupKey] = []
        for cell in raw {
            let key = MergeGroupKey(week: cell.week, dow: cell.dow, name: cell.name, room: cell.room, teacher: cell.teacher)
            if grouped[key] == nil {
                groupOrder.append(key)
            }
            grouped[key, default: []].append(cell)
        }

        var merged: [RawCell] = []
        for key in groupOrder {
            guard let items = grouped[key] else { continue }
            let sorted = items.sorted { $0.sp < $1.sp }
            var cur = sorted[0]
            for c in sorted.dropFirst() {
                if c.sp <= cur.ep + 1 {
                    cur.ep = max(cur.ep, c.ep)
                } else {
                    merged.append(cur)
                    cur = c
                }
            }
            merged.append(cur)
        }

        // Aggregate across weeks preserving deterministic order (week, dow, sp)
        let sortedMerged = merged.sorted { a, b in
            if a.week != b.week { return a.week < b.week }
            if a.dow != b.dow { return a.dow < b.dow }
            if a.sp != b.sp { return a.sp < b.sp }
            return a.name < b.name
        }

        var byKey: [CourseKey: Set<Int>] = [:]
        var keyOrder: [CourseKey] = []
        for c in sortedMerged {
            let k = CourseKey(name: c.name, room: c.room, teacher: c.teacher, dow: c.dow, sp: c.sp, ep: c.ep)
            if byKey[k] == nil {
                keyOrder.append(k)
            }
            byKey[k, default: []].insert(c.week)
        }

        let colors = Palette.assign(names: keyOrder.map(\.name))
        let courses: [Course] = keyOrder.map { k in
            Course(
                id: idGenerator(),
                semesterId: semester.id,
                name: k.name,
                teacher: k.teacher,
                room: k.room,
                dayOfWeek: k.dow,
                startPeriod: k.sp,
                endPeriod: k.ep,
                weeks: byKey[k] ?? [],
                colorIndex: colors[k.name] ?? 0,
                source: .IMPORT,
                updatedAt: now
            )
        }

        let maxWeek = courses.flatMap(\.weeks).max() ?? 1
        if maxWeek > semester.totalWeeks {
            warnings.append("weeks_exceed:\(maxWeek)")
        }
        return ImportResult(
            value: TimetableImport(courses: courses, maxWeek: maxWeek, skipped: []),
            warnings: warnings
        )
    }

    // MARK: - Header & Text Helpers

    private static func findHeader(_ sheet: XlsxSheet) -> (dayCols: [Int: Int], headerRow: Int)? {
        let maxScanRow = min(sheet.maxRow, 5)
        guard maxScanRow >= 1, sheet.maxCol >= 1 else { return nil }

        for r in 1...maxScanRow {
            var cols: [Int: Int] = [:]
            for c in 1...sheet.maxCol {
                let t = sheet.text(row: r, col: c).trimmingCharacters(in: .whitespacesAndNewlines)
                guard !t.isEmpty else { continue }
                if let dow = matchDayOfWeek(t) {
                    cols[dow] = c
                }
            }
            if cols.count >= 5 {
                return (dayCols: cols, headerRow: r)
            }
        }
        return nil
    }

    private static func matchDayOfWeek(_ text: String) -> Int? {
        for (idx, zh) in dayNames.enumerated() {
            let alt = zh.replacingOccurrences(of: "周", with: "星期")
            if text == zh || text == alt || text.hasPrefix(zh) || text.hasPrefix(alt) || text.hasSuffix(zh) || text.hasSuffix(alt) {
                return idx + 1
            }
        }
        let lower = text.lowercased()
        for (idx, en) in englishDayPrefixes.enumerated() {
            if lower == en || lower.hasPrefix(en + " ") || lower.hasPrefix(en + ".") || lower.hasPrefix(en + "day") {
                return idx + 1
            }
        }
        return nil
    }

    private static func firstPositiveInt(in text: String) -> Int? {
        var digits = ""
        for ch in text {
            if ch.isASCII && ch.isNumber {
                digits.append(ch)
            } else if !digits.isEmpty {
                break
            }
        }
        guard let v = Int(digits), v > 0 else { return nil }
        return v
    }

    private static func splitConflictBlocks(_ text: String) -> [String] {
        if text.contains("\n───\n") {
            return text.components(separatedBy: "\n───\n")
        }
        return [text]
    }

    /// Parses common university timetable inline week specifications such as:
    /// `"1-16周"`, `"第1-8,10-16周"`, `"1-15周(单)"`, `"2-16周(双)"`, `"[1-12周]"`.
    private static func extractInlineWeeks(from lines: [String]) -> (weeks: Set<Int>, remainingLines: [String])? {
        for (idx, line) in lines.enumerated() {
            if let weeks = parseWeekSpec(line), !weeks.isEmpty {
                var remaining = lines
                remaining.remove(at: idx)
                return (weeks, remaining)
            }
        }
        return nil
    }

    private static func parseWeekSpec(_ raw: String) -> Set<Int>? {
        guard raw.contains("周") else { return nil }
        var cleaned = raw
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
            .replacingOccurrences(of: "，", with: ",")
            .replacingOccurrences(of: "、", with: ",")
            .replacingOccurrences(of: "～", with: "-")
            .replacingOccurrences(of: "~", with: "-")
            .replacingOccurrences(of: "—", with: "-")
            .replacingOccurrences(of: "–", with: "-")

        let oddOnly = cleaned.contains("单")
        let evenOnly = cleaned.contains("双")
        cleaned = cleaned
            .replacingOccurrences(of: "单周", with: "")
            .replacingOccurrences(of: "双周", with: "")
            .replacingOccurrences(of: "(单)", with: "")
            .replacingOccurrences(of: "(双)", with: "")
            .replacingOccurrences(of: "单", with: "")
            .replacingOccurrences(of: "双", with: "")
            .replacingOccurrences(of: "第", with: "")
            .replacingOccurrences(of: "周", with: "")
            .trimmingCharacters(in: CharacterSet(charactersIn: " []()"))

        guard !cleaned.isEmpty else { return nil }

        var result = Set<Int>()
        for part in cleaned.split(separator: ",") {
            let token = part.trimmingCharacters(in: .whitespaces)
            if token.contains("-") {
                let bounds = token.split(separator: "-", maxSplits: 1)
                guard bounds.count == 2,
                      let a = Int(bounds[0].trimmingCharacters(in: .whitespaces)),
                      let b = Int(bounds[1].trimmingCharacters(in: .whitespaces)),
                      a >= 1, b >= a, b <= 60 else {
                    return nil
                }
                for w in a...b {
                    if oddOnly && w % 2 == 0 { continue }
                    if evenOnly && w % 2 != 0 { continue }
                    result.insert(w)
                }
            } else if let w = Int(token), w >= 1, w <= 60 {
                if oddOnly && w % 2 == 0 { continue }
                if evenOnly && w % 2 != 0 { continue }
                result.insert(w)
            } else {
                return nil
            }
        }
        return result.isEmpty ? nil : result
    }
}

// MARK: - Pure-Swift ZIP Reader (STORE Method 0 + DEFLATE Method 8)

enum MiniZipReader {
    private static let localHeaderSig: UInt32 = 0x0403_4B50
    private static let centralDirSig: UInt32 = 0x0201_4B50
    private static let eocdSig: UInt32 = 0x0605_4B50

    /// Extracts all `.xml` and `.rels` entries from a ZIP archive.
    static func extractXmlEntries(from data: Data) -> [String: Data]? {
        let bytes = [UInt8](data)
        guard bytes.count >= 22 else { return nil }

        // Locate End of Central Directory (EOCD) scanning backwards
        let minEocdOffset = max(0, bytes.count - 65557)
        var eocdOffset: Int?
        var pos = bytes.count - 22
        while pos >= minEocdOffset {
            if readUInt32(bytes, at: pos) == eocdSig {
                let commentLen = Int(readUInt16(bytes, at: pos + 20))
                if pos + 22 + commentLen == bytes.count {
                    eocdOffset = pos
                    break
                }
            }
            pos -= 1
        }
        guard let eocd = eocdOffset else { return nil }

        let totalEntries = Int(readUInt16(bytes, at: eocd + 10))
        let cdSize = Int(readUInt32(bytes, at: eocd + 12))
        let cdOffset = Int(readUInt32(bytes, at: eocd + 16))
        guard cdOffset >= 0, cdOffset + cdSize <= bytes.count else { return nil }

        var result: [String: Data] = [:]
        var cursor = cdOffset
        for _ in 0..<totalEntries {
            guard cursor + 46 <= bytes.count,
                  readUInt32(bytes, at: cursor) == centralDirSig else {
                return nil
            }
            let method = readUInt16(bytes, at: cursor + 10)
            let compressedSize = Int(readUInt32(bytes, at: cursor + 20))
            let uncompressedSize = Int(readUInt32(bytes, at: cursor + 24))
            let nameLen = Int(readUInt16(bytes, at: cursor + 28))
            let extraLen = Int(readUInt16(bytes, at: cursor + 30))
            let commentLen = Int(readUInt16(bytes, at: cursor + 32))
            let localOffset = Int(readUInt32(bytes, at: cursor + 42))

            let nameStart = cursor + 46
            let nameEnd = nameStart + nameLen
            guard nameEnd <= bytes.count else { return nil }
            let name = String(decoding: bytes[nameStart..<nameEnd], as: UTF8.self)
            cursor = nameEnd + extraLen + commentLen

            let lower = name.lowercased()
            if name.hasSuffix("/") || !(lower.hasSuffix(".xml") || lower.hasSuffix(".rels")) {
                continue
            }

            guard localOffset + 30 <= bytes.count,
                  readUInt32(bytes, at: localOffset) == localHeaderSig else {
                return nil
            }
            let localNameLen = Int(readUInt16(bytes, at: localOffset + 26))
            let localExtraLen = Int(readUInt16(bytes, at: localOffset + 28))
            let payloadStart = localOffset + 30 + localNameLen + localExtraLen
            let payloadEnd = payloadStart + compressedSize
            guard payloadStart >= 0, payloadEnd <= bytes.count else { return nil }

            let payload = Data(bytes[payloadStart..<payloadEnd])
            switch method {
            case 0: // STORE
                result[name] = payload
            case 8: // DEFLATE (RFC 1951 raw stream)
                guard let inflated = decompressDeflate(payload, expectedSize: uncompressedSize) else {
                    return nil
                }
                result[name] = inflated
            default:
                continue
            }
        }
        return result
    }

    static func decompressDeflate(_ data: Data, expectedSize: Int) -> Data? {
        #if canImport(Compression)
        if data.isEmpty { return Data() }
        var dstCapacity = max(expectedSize, data.count * 4, 4096)
        // Cap at 64 MB to protect against zip bombs
        let maxCapacity = 64 * 1024 * 1024
        while dstCapacity <= maxCapacity {
            var dst = [UInt8](repeating: 0, count: dstCapacity)
            let decodedSize = data.withUnsafeBytes { srcBuf -> Int in
                guard let srcPtr = srcBuf.baseAddress?.assumingMemoryBound(to: UInt8.self) else {
                    return 0
                }
                return compression_decode_buffer(
                    &dst,
                    dstCapacity,
                    srcPtr,
                    data.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
            if decodedSize > 0 && decodedSize < dstCapacity {
                return Data(dst.prefix(decodedSize))
            }
            if decodedSize == dstCapacity {
                dstCapacity *= 2
                continue
            }
            break
        }
        return RawDeflateInflater.inflate([UInt8](data))
        #else
        return RawDeflateInflater.inflate([UInt8](data))
        #endif
    }

    private static func readUInt16(_ bytes: [UInt8], at offset: Int) -> UInt16 {
        UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8)
    }

    private static func readUInt32(_ bytes: [UInt8], at offset: Int) -> UInt32 {
        UInt32(bytes[offset])
            | (UInt32(bytes[offset + 1]) << 8)
            | (UInt32(bytes[offset + 2]) << 16)
            | (UInt32(bytes[offset + 3]) << 24)
    }
}

// MARK: - Pure-Swift RFC 1951 Raw DEFLATE Inflater (Cross-Platform Fallback & Linux Support)

enum RawDeflateInflater {
    private static let maxOutputBytes = 64 * 1024 * 1024

    private static let lengthBase: [Int] = [
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    ]
    private static let lengthExtra: [Int] = [
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    ]
    private static let distBase: [Int] = [
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    ]
    private static let distExtra: [Int] = [
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    ]
    private static let codeLenOrder: [Int] = [
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    ]

    private struct BitReader {
        let data: [UInt8]
        var bytePos: Int = 0
        var bitBuf: UInt32 = 0
        var bitCount: Int = 0

        mutating func readBits(_ n: Int) -> Int? {
            if n == 0 { return 0 }
            while bitCount < n {
                guard bytePos < data.count else { return nil }
                bitBuf |= UInt32(data[bytePos]) << bitCount
                bytePos += 1
                bitCount += 8
            }
            let val = Int(bitBuf & ((UInt32(1) << n) - 1))
            bitBuf >>= n
            bitCount -= n
            return val
        }

        mutating func alignToByte() {
            let discard = bitCount & 7
            bitBuf >>= discard
            bitCount -= discard
        }

        mutating func readAlignedBytes(_ count: Int) -> [UInt8]? {
            alignToByte()
            var out: [UInt8] = []
            out.reserveCapacity(count)
            while bitCount >= 8 && out.count < count {
                out.append(UInt8(bitBuf & 0xFF))
                bitBuf >>= 8
                bitCount -= 8
            }
            let remaining = count - out.count
            guard bytePos + remaining <= data.count else { return nil }
            out.append(contentsOf: data[bytePos..<(bytePos + remaining)])
            bytePos += remaining
            return out
        }
    }

    private struct HuffmanTree {
        let counts: [Int]
        let symbols: [Int]
        let maxBits: Int

        init?(lengths: [Int]) {
            let maxLen = lengths.max() ?? 0
            if maxLen == 0 {
                self.counts = [0]
                self.symbols = []
                self.maxBits = 0
                return
            }
            guard maxLen <= 15 else { return nil }
            var counts = [Int](repeating: 0, count: maxLen + 1)
            for l in lengths where l > 0 {
                counts[l] += 1
            }
            var offsets = [Int](repeating: 0, count: maxLen + 1)
            var total = 0
            for bits in 1...maxLen {
                offsets[bits] = total
                total += counts[bits]
            }
            var symbols = [Int](repeating: 0, count: total)
            for (sym, len) in lengths.enumerated() where len > 0 {
                symbols[offsets[len]] = sym
                offsets[len] += 1
            }
            self.counts = counts
            self.symbols = symbols
            self.maxBits = maxLen
        }

        func decode(_ reader: inout BitReader) -> Int? {
            var code = 0
            var first = 0
            var index = 0
            for len in 1...maxBits {
                guard let bit = reader.readBits(1) else { return nil }
                code = (code << 1) | bit
                let count = counts[len]
                if code - count < first {
                    return symbols[index + (code - first)]
                }
                index += count
                first = (first + count) << 1
            }
            return nil
        }
    }

    private static let fixedLitTree: HuffmanTree = {
        var lengths = [Int](repeating: 0, count: 288)
        for i in 0...143 { lengths[i] = 8 }
        for i in 144...255 { lengths[i] = 9 }
        for i in 256...279 { lengths[i] = 7 }
        for i in 280...287 { lengths[i] = 8 }
        return HuffmanTree(lengths: lengths)!
    }()

    private static let fixedDistTree: HuffmanTree = {
        HuffmanTree(lengths: [Int](repeating: 5, count: 32))!
    }()

    static func inflate(_ bytes: [UInt8]) -> Data? {
        var reader = BitReader(data: bytes)
        var out: [UInt8] = []
        out.reserveCapacity(min(bytes.count * 4, 65536))

        var isFinal = false
        while !isFinal {
            guard let bfinal = reader.readBits(1),
                  let btype = reader.readBits(2) else {
                return nil
            }
            isFinal = (bfinal == 1)

            switch btype {
            case 0: // Stored block
                guard let header = reader.readAlignedBytes(4) else { return nil }
                let len = Int(header[0]) | (Int(header[1]) << 8)
                let nlen = Int(header[2]) | (Int(header[3]) << 8)
                guard (len ^ 0xFFFF) == nlen,
                      out.count + len <= maxOutputBytes,
                      let literal = reader.readAlignedBytes(len) else {
                    return nil
                }
                out.append(contentsOf: literal)

            case 1: // Fixed Huffman
                guard decodeBlock(reader: &reader, litTree: fixedLitTree, distTree: fixedDistTree, out: &out) else {
                    return nil
                }

            case 2: // Dynamic Huffman
                guard let hlitRaw = reader.readBits(5),
                      let hdistRaw = reader.readBits(5),
                      let hclenRaw = reader.readBits(4) else {
                    return nil
                }
                let hlit = hlitRaw + 257
                let hdist = hdistRaw + 1
                let hclen = hclenRaw + 4
                var clLengths = [Int](repeating: 0, count: 19)
                for i in 0..<hclen {
                    guard let v = reader.readBits(3) else { return nil }
                    clLengths[codeLenOrder[i]] = v
                }
                guard let clTree = HuffmanTree(lengths: clLengths) else { return nil }

                var allLengths: [Int] = []
                allLengths.reserveCapacity(hlit + hdist)
                while allLengths.count < hlit + hdist {
                    guard let sym = clTree.decode(&reader) else { return nil }
                    if sym < 16 {
                        allLengths.append(sym)
                    } else if sym == 16 {
                        guard let prev = allLengths.last,
                              let extra = reader.readBits(2) else { return nil }
                        allLengths.append(contentsOf: repeatElement(prev, count: 3 + extra))
                    } else if sym == 17 {
                        guard let extra = reader.readBits(3) else { return nil }
                        allLengths.append(contentsOf: repeatElement(0, count: 3 + extra))
                    } else if sym == 18 {
                        guard let extra = reader.readBits(7) else { return nil }
                        allLengths.append(contentsOf: repeatElement(0, count: 11 + extra))
                    } else {
                        return nil
                    }
                }
                guard allLengths.count == hlit + hdist,
                      let litTree = HuffmanTree(lengths: Array(allLengths[0..<hlit])),
                      let distTree = HuffmanTree(lengths: Array(allLengths[hlit..<(hlit + hdist)])) else {
                    return nil
                }
                guard decodeBlock(reader: &reader, litTree: litTree, distTree: distTree, out: &out) else {
                    return nil
                }

            default:
                return nil
            }
        }
        return Data(out)
    }

    private static func decodeBlock(
        reader: inout BitReader,
        litTree: HuffmanTree,
        distTree: HuffmanTree,
        out: inout [UInt8]
    ) -> Bool {
        while true {
            guard let sym = litTree.decode(&reader) else { return false }
            if sym < 256 {
                if out.count >= maxOutputBytes { return false }
                out.append(UInt8(sym))
            } else if sym == 256 {
                return true
            } else {
                let lenIdx = sym - 257
                guard lenIdx >= 0 && lenIdx < lengthBase.count,
                      let lenEx = reader.readBits(lengthExtra[lenIdx]) else {
                    return false
                }
                let length = lengthBase[lenIdx] + lenEx
                guard let distSym = distTree.decode(&reader),
                      distSym >= 0 && distSym < distBase.count,
                      let distEx = reader.readBits(distExtra[distSym]) else {
                    return false
                }
                let distance = distBase[distSym] + distEx
                guard distance > 0 && distance <= out.count && out.count + length <= maxOutputBytes else {
                    return false
                }
                for _ in 0..<length {
                    out.append(out[out.count - distance])
                }
            }
        }
    }
}
