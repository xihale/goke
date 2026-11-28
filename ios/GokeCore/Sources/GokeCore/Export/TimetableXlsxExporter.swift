import Foundation
import GokeCoreModel

// MARK: - Structured OOXML (.xlsx) Timetable Exporter

public enum TimetableXlsxError: Error, Sendable, Equatable, LocalizedError {
    case noCurrentSemester

    public var errorDescription: String? {
        switch self {
        case .noCurrentSemester:
            return "当前没有可导出的学期课表。"
        }
    }
}

/// Pure-Swift OOXML `.xlsx` timetable grid exporter (one worksheet per week, botanical palette fills,
/// merged multi-period cells, frozen headers, and conflict highlighting — matches Android `XlsxTimetableWriter`).
public enum TimetableXlsxExporter {
    private static let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    // Matches Theme.kt / GokeTheme.swift botanical light course palette (OOXML AARRGGBB)
    private static let courseColors: [(bg: String, fg: String)] = [
        ("FFD9E8EF", "FF3B6173"),
        ("FFBFDCD5", "FF2C5B53"),
        ("FFDAD8EE", "FF4F4D7C"),
        ("FFF3EBCF", "FF75662F"),
        ("FFF7DFD2", "FF85503A"),
        ("FFDFE8DA", "FF4B5F49"),
        ("FFF4D9E0", "FF83465A"),
        ("FFD2D8EA", "FF454F7A"),
    ]
    private static let conflictBg = "FFFCE9E7"
    private static let conflictFg = "FF7A2A22"
    private static let headerBg = "FFF2F3F5"
    private static let headerFg = "FF333333"
    private static let borderColor = "FFD0D7DE"

    private static let styleEmpty = 0
    private static let styleHeader = 1
    private static let styleCourseBase = 2
    private static let styleConflict = styleCourseBase + courseColors.count

    private struct CellSpec {
        let style: Int
        let sharedIdx: Int?
    }

    public static func write(
        snapshot: AppSnapshot,
        includeTeacher: Bool = true
    ) throws -> Data {
        let live = snapshot.live()
        guard let sem = live.currentSemester else {
            throw TimetableXlsxError.noCurrentSemester
        }
        return write(
            semester: sem,
            courses: live.courses,
            exceptions: live.exceptions,
            includeTeacher: includeTeacher
        )
    }

    public static func write(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException] = [],
        includeTeacher: Bool = true
    ) -> Data {
        let cal = SemesterCalendar(semester: semester)
        var stringOrder: [String] = []
        var stringIndex: [String: Int] = [:]

        func intern(_ s: String) -> Int {
            if let existing = stringIndex[s] {
                return existing
            }
            let idx = stringOrder.count
            stringOrder.append(s)
            stringIndex[s] = idx
            return idx
        }

        let liveCourses = courses.filter { $0.deletedAt == nil && $0.semesterId == semester.id }
        let liveExceptions = exceptions.filter { $0.deletedAt == nil }
        let totalWeeks = max(1, semester.totalWeeks)

        let allOccs = CourseExpander.occurrences(
            semester: semester,
            courses: liveCourses,
            exceptions: liveExceptions,
            from: cal.weekStart(1),
            to: cal.weekEnd(totalWeeks)
        )
        let occsByWeek = Dictionary(grouping: allOccs, by: \.week)

        var sheets: [(name: String, xml: String)] = []
        sheets.reserveCapacity(totalWeeks)
        for week in 1...totalWeeks {
            let name = "第\(week)周"
            let xml = buildSheetXml(
                cal: cal,
                week: week,
                occs: occsByWeek[week] ?? [],
                includeTeacher: includeTeacher,
                intern: intern
            )
            sheets.append((name: name, xml: xml))
        }

        var entries: [(name: String, data: Data)] = [
            ("[Content_Types].xml", Data(buildContentTypes(sheetCount: sheets.count).utf8)),
            ("_rels/.rels", Data(relsXml.utf8)),
            ("xl/workbook.xml", Data(buildWorkbookXml(sheetNames: sheets.map(\.name)).utf8)),
            ("xl/_rels/workbook.xml.rels", Data(buildWorkbookRelsXml(sheetCount: sheets.count).utf8)),
            ("xl/styles.xml", Data(buildStylesXml().utf8)),
            ("xl/sharedStrings.xml", Data(buildSharedStringsXml(strings: stringOrder).utf8)),
        ]
        for (idx, sheet) in sheets.enumerated() {
            entries.append(("xl/worksheets/sheet\(idx + 1).xml", Data(sheet.xml.utf8)))
        }

        return MiniZipWriter.archive(entries: entries)
    }

    private static func buildSheetXml(
        cal: SemesterCalendar,
        week: Int,
        occs: [CourseOccurrence],
        includeTeacher: Bool,
        intern: (String) -> Int
    ) -> String {
        let periods = cal.periods
        let occsByDay = Dictionary(grouping: occs, by: { $0.date.dayOfWeek })

        var merges: [String] = []
        var rowsSb = ""

        // Row 1: Header
        rowsSb += "<row r=\"1\" ht=\"28\" customHeight=\"1\">"
        rowsSb += strCell(row: 1, col: 1, style: styleHeader, sharedIdx: intern("节次\\星期"))
        for dow in 1...7 {
            let d = cal.dateOf(week: week, dayOfWeek: dow)
            let label = "\(dayNames[dow - 1]) \(d.month)/\(d.day)"
            rowsSb += strCell(row: 1, col: dow + 1, style: styleHeader, sharedIdx: intern(label))
        }
        rowsSb += "</row>"

        var dayCovering: [Int: [[CourseOccurrence]]] = [:]
        for dow in 1...7 {
            let dayOccs = occsByDay[dow] ?? []
            dayCovering[dow] = periods.map { p in
                dayOccs.filter { p.index >= $0.startPeriod && p.index <= $0.endPeriod }
            }
        }

        var grid: [[CellSpec?]] = Array(repeating: Array(repeating: nil, count: 8), count: periods.count)
        for dow in 1...7 {
            let col = dow + 1
            let covering = dayCovering[dow] ?? []
            var rIdx = 0
            while rIdx < periods.count {
                let list = covering[rIdx]
                if list.isEmpty {
                    grid[rIdx][dow] = CellSpec(style: styleEmpty, sharedIdx: nil)
                    rIdx += 1
                } else if list.count > 1 {
                    let text = list.map { formatOccurrence($0, includeTeacher: includeTeacher) }.joined(separator: "\n───\n")
                    grid[rIdx][dow] = CellSpec(style: styleConflict, sharedIdx: intern(text))
                    rIdx += 1
                } else {
                    let occ = list[0]
                    var endRIdx = rIdx
                    while endRIdx + 1 < periods.count
                        && covering[endRIdx + 1].count == 1
                        && covering[endRIdx + 1][0] == occ
                    {
                        endRIdx += 1
                    }
                    let colorIdx = ((occ.course.colorIndex % courseColors.count) + courseColors.count) % courseColors.count
                    let style = styleCourseBase + colorIdx
                    let textIdx = intern(formatOccurrence(occ, includeTeacher: includeTeacher))
                    grid[rIdx][dow] = CellSpec(style: style, sharedIdx: textIdx)
                    if endRIdx > rIdx {
                        for k in (rIdx + 1)...endRIdx {
                            grid[k][dow] = CellSpec(style: style, sharedIdx: nil)
                        }
                        let colL = colLetter(col)
                        merges.append("\(colL)\(rIdx + 2):\(colL)\(endRIdx + 2)")
                    }
                    rIdx = endRIdx + 1
                }
            }
        }

        // Rows 2...(periods.count + 1)
        for (pIdx, p) in periods.enumerated() {
            let row = pIdx + 2
            rowsSb += "<row r=\"\(row)\" ht=\"44\" customHeight=\"1\">"
            rowsSb += strCell(row: row, col: 1, style: styleHeader, sharedIdx: intern("\(p.index)\n\(p.start.description)-\(p.end.description)"))
            for dow in 1...7 {
                let spec = grid[pIdx][dow] ?? CellSpec(style: styleEmpty, sharedIdx: nil)
                if let sharedIdx = spec.sharedIdx {
                    rowsSb += strCell(row: row, col: dow + 1, style: spec.style, sharedIdx: sharedIdx)
                } else {
                    rowsSb += emptyCell(row: row, col: dow + 1, style: spec.style)
                }
            }
            rowsSb += "</row>"
        }

        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
        sb += "<sheetViews><sheetView tabSelected=\"\(week == 1 ? 1 : 0)\" workbookViewId=\"0\">"
        sb += "<pane xSplit=\"1\" ySplit=\"1\" topLeftCell=\"B2\" activePane=\"bottomRight\" state=\"frozen\"/>"
        sb += "</sheetView></sheetViews>"
        sb += "<sheetFormatPr defaultRowHeight=\"44\"/>"
        sb += "<cols>"
        sb += "<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>"
        sb += "<col min=\"2\" max=\"8\" width=\"18\" customWidth=\"1\"/>"
        sb += "</cols>"
        sb += "<sheetData>\(rowsSb)</sheetData>"
        if !merges.isEmpty {
            sb += "<mergeCells count=\"\(merges.count)\">"
            for ref in merges {
                sb += "<mergeCell ref=\"\(ref)\"/>"
            }
            sb += "</mergeCells>"
        }
        sb += "</worksheet>"
        return sb
    }

    private static func formatOccurrence(_ occ: CourseOccurrence, includeTeacher: Bool) -> String {
        var parts = [occ.course.name]
        let trimmedRoom = occ.room.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedRoom.isEmpty {
            parts.append(trimmedRoom)
        }
        let trimmedTeacher = occ.course.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
        if includeTeacher && !trimmedTeacher.isEmpty {
            parts.append(trimmedTeacher)
        }
        return parts.joined(separator: "\n")
    }

    private static func strCell(row: Int, col: Int, style: Int, sharedIdx: Int) -> String {
        "<c r=\"\(colLetter(col))\(row)\" t=\"s\" s=\"\(style)\"><v>\(sharedIdx)</v></c>"
    }

    private static func emptyCell(row: Int, col: Int, style: Int) -> String {
        "<c r=\"\(colLetter(col))\(row)\" s=\"\(style)\"/>"
    }

    private static func colLetter(_ col: Int) -> String {
        var n = col
        var chars: [Character] = []
        while n > 0 {
            let rem = (n - 1) % 26
            chars.append(Character(UnicodeScalar(65 + rem)!))
            n = (n - 1) / 26
        }
        return String(chars.reversed())
    }

    private static func buildContentTypes(sheetCount: Int) -> String {
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        sb += "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        sb += "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        sb += "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        sb += "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        sb += "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
        for i in 1...sheetCount {
            sb += "<Override PartName=\"/xl/worksheets/sheet\(i).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        sb += "</Types>"
        return sb
    }

    private static func buildWorkbookXml(sheetNames: [String]) -> String {
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
        sb += "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
        sb += "<sheets>"
        for (idx, name) in sheetNames.enumerated() {
            let id = idx + 1
            sb += "<sheet name=\"\(escapeXml(name))\" sheetId=\"\(id)\" r:id=\"rId\(id)\"/>"
        }
        sb += "</sheets></workbook>"
        return sb
    }

    private static func buildWorkbookRelsXml(sheetCount: Int) -> String {
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        for i in 1...sheetCount {
            sb += "<Relationship Id=\"rId\(i)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\(i).xml\"/>"
        }
        sb += "<Relationship Id=\"rId\(sheetCount + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        sb += "<Relationship Id=\"rId\(sheetCount + 2)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>"
        sb += "</Relationships>"
        return sb
    }

    private static func buildSharedStringsXml(strings: [String]) -> String {
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"\(strings.count)\" uniqueCount=\"\(strings.count)\">"
        for s in strings {
            sb += "<si><t xml:space=\"preserve\">\(escapeXml(s))</t></si>"
        }
        sb += "</sst>"
        return sb
    }

    private static func buildStylesXml() -> String {
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        sb += "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"

        sb += "<fonts count=\"\(3 + courseColors.count)\">"
        sb += "<font><sz val=\"10\"/><color rgb=\"\(headerFg)\"/><name val=\"sans-serif\"/></font>"
        sb += "<font><b/><sz val=\"10\"/><color rgb=\"\(headerFg)\"/><name val=\"sans-serif\"/></font>"
        for pair in courseColors {
            sb += "<font><sz val=\"10\"/><color rgb=\"\(pair.fg)\"/><name val=\"sans-serif\"/></font>"
        }
        sb += "<font><sz val=\"10\"/><color rgb=\"\(conflictFg)\"/><name val=\"sans-serif\"/></font>"
        sb += "</fonts>"

        sb += "<fills count=\"\(4 + courseColors.count)\">"
        sb += "<fill><patternFill patternType=\"none\"/></fill>"
        sb += "<fill><patternFill patternType=\"gray125\"/></fill>"
        sb += "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"\(headerBg)\"/></patternFill></fill>"
        for pair in courseColors {
            sb += "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"\(pair.bg)\"/></patternFill></fill>"
        }
        sb += "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"\(conflictBg)\"/></patternFill></fill>"
        sb += "</fills>"

        sb += "<borders count=\"2\">"
        sb += "<border><left/><right/><top/><bottom/><diagonal/></border>"
        sb += "<border>"
        sb += "<left style=\"thin\"><color rgb=\"\(borderColor)\"/></left>"
        sb += "<right style=\"thin\"><color rgb=\"\(borderColor)\"/></right>"
        sb += "<top style=\"thin\"><color rgb=\"\(borderColor)\"/></top>"
        sb += "<bottom style=\"thin\"><color rgb=\"\(borderColor)\"/></bottom>"
        sb += "<diagonal/></border>"
        sb += "</borders>"

        sb += "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"

        sb += "<cellXfs count=\"\(3 + courseColors.count)\">"
        sb += "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
        sb += "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
        for i in 0..<courseColors.count {
            let fontId = 2 + i
            let fillId = 3 + i
            sb += "<xf numFmtId=\"0\" fontId=\"\(fontId)\" fillId=\"\(fillId)\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
        }
        let conflictFontId = 2 + courseColors.count
        let conflictFillId = 3 + courseColors.count
        sb += "<xf numFmtId=\"0\" fontId=\"\(conflictFontId)\" fillId=\"\(conflictFillId)\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
        sb += "</cellXfs>"
        sb += "</styleSheet>"
        return sb
    }

    private static func escapeXml(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    private static let relsXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>"
}

// MARK: - Pure-Swift Uncompressed ZIP Writer (STORE / Method 0 + CRC-32)

private enum MiniZipWriter {
    private static let crcTable: [UInt32] = {
        (0..<256).map { i -> UInt32 in
            var c = UInt32(i)
            for _ in 0..<8 {
                c = (c & 1) != 0 ? (0xEDB88320 ^ (c >> 1)) : (c >> 1)
            }
            return c
        }
    }()

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            let idx = Int((crc ^ UInt32(byte)) & 0xFF)
            crc = crcTable[idx] ^ (crc >> 8)
        }
        return crc ^ 0xFFFF_FFFF
    }

    private static func appendUInt16(_ value: UInt16, to data: inout Data) {
        var le = value.littleEndian
        withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
    }

    private static func appendUInt32(_ value: UInt32, to data: inout Data) {
        var le = value.littleEndian
        withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
    }

    static func archive(entries: [(name: String, data: Data)]) -> Data {
        var out = Data()
        var centralDir = Data()

        for entry in entries {
            let nameData = Data(entry.name.utf8)
            let content = entry.data
            let crc = crc32(content)
            let localOffset = UInt32(out.count)
            let size = UInt32(content.count)
            let nameLen = UInt16(nameData.count)

            // Local file header (30 bytes + name + payload)
            appendUInt32(0x0403_4B50, to: &out)
            appendUInt16(20, to: &out)          // version needed: 2.0
            appendUInt16(0x0800, to: &out)      // UTF-8 filename flag
            appendUInt16(0, to: &out)           // method 0 (STORE)
            appendUInt16(0, to: &out)           // mod time
            appendUInt16(0x0021, to: &out)      // mod date (1980-01-01)
            appendUInt32(crc, to: &out)
            appendUInt32(size, to: &out)
            appendUInt32(size, to: &out)
            appendUInt16(nameLen, to: &out)
            appendUInt16(0, to: &out)           // extra field length
            out.append(nameData)
            out.append(content)

            // Central directory header (46 bytes + name)
            appendUInt32(0x0201_4B50, to: &centralDir)
            appendUInt16(20, to: &centralDir)   // version made by
            appendUInt16(20, to: &centralDir)   // version needed
            appendUInt16(0x0800, to: &centralDir)
            appendUInt16(0, to: &centralDir)    // STORE
            appendUInt16(0, to: &centralDir)
            appendUInt16(0x0021, to: &centralDir)
            appendUInt32(crc, to: &centralDir)
            appendUInt32(size, to: &centralDir)
            appendUInt32(size, to: &centralDir)
            appendUInt16(nameLen, to: &centralDir)
            appendUInt16(0, to: &centralDir)    // extra length
            appendUInt16(0, to: &centralDir)    // comment length
            appendUInt16(0, to: &centralDir)    // disk number
            appendUInt16(0, to: &centralDir)    // internal attrs
            appendUInt32(0, to: &centralDir)    // external attrs
            appendUInt32(localOffset, to: &centralDir)
            centralDir.append(nameData)
        }

        let cdOffset = UInt32(out.count)
        let cdSize = UInt32(centralDir.count)
        let count = UInt16(entries.count)
        out.append(centralDir)

        // End of central directory record (22 bytes)
        appendUInt32(0x0605_4B50, to: &out)
        appendUInt16(0, to: &out)
        appendUInt16(0, to: &out)
        appendUInt16(count, to: &out)
        appendUInt16(count, to: &out)
        appendUInt32(cdSize, to: &out)
        appendUInt32(cdOffset, to: &out)
        appendUInt16(0, to: &out)

        return out
    }
}

// MARK: - Structured Shared Timetable Payload (`goke.timetable.v1`) & Expiry Presets

public struct SharedPeriodTime: Sendable, Hashable, Codable {
    public let index: Int
    public let start: String
    public let end: String

    public init(index: Int, start: String, end: String) {
        self.index = index
        self.start = start
        self.end = end
    }
}

public struct SharedSemester: Sendable, Hashable, Codable {
    public let name: String
    public let week1Monday: String
    public let totalWeeks: Int
    public let periods: [SharedPeriodTime]

    public init(name: String, week1Monday: String, totalWeeks: Int, periods: [SharedPeriodTime]) {
        self.name = name
        self.week1Monday = week1Monday
        self.totalWeeks = totalWeeks
        self.periods = periods
    }
}

public struct SharedCourse: Sendable, Hashable, Codable {
    public let name: String
    public let teacher: String
    public let room: String
    public let dayOfWeek: Int
    public let startPeriod: Int
    public let endPeriod: Int
    public let weeks: [Int]
    public let colorIndex: Int
    public let kind: String
    public let note: String

    public init(
        name: String,
        teacher: String,
        room: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeks: [Int],
        colorIndex: Int,
        kind: String,
        note: String
    ) {
        self.name = name
        self.teacher = teacher
        self.room = room
        self.dayOfWeek = dayOfWeek
        self.startPeriod = startPeriod
        self.endPeriod = endPeriod
        self.weeks = weeks
        self.colorIndex = colorIndex
        self.kind = kind
        self.note = note
    }
}

public struct SharedCourseException: Sendable, Hashable, Codable {
    public let courseIndex: Int
    public let week: Int
    public let type: String
    public let newWeek: Int?
    public let newDayOfWeek: Int?
    public let newStartPeriod: Int?
    public let newEndPeriod: Int?
    public let newRoom: String?

    public init(
        courseIndex: Int,
        week: Int,
        type: String,
        newWeek: Int? = nil,
        newDayOfWeek: Int? = nil,
        newStartPeriod: Int? = nil,
        newEndPeriod: Int? = nil,
        newRoom: String? = nil
    ) {
        self.courseIndex = courseIndex
        self.week = week
        self.type = type
        self.newWeek = newWeek
        self.newDayOfWeek = newDayOfWeek
        self.newStartPeriod = newStartPeriod
        self.newEndPeriod = newEndPeriod
        self.newRoom = newRoom
    }
}

/// Anonymized timetable payload (`goke.timetable.v1`) for Quick Share (`goke.xeed.ink`), containing zero credentials.
public struct SharedTimetablePayload: Sendable, Hashable, Codable {
    public static let schemaV1 = "goke.timetable.v1"

    public let schema: String
    public let code: String?
    public let name: String
    public let updatedAt: Int64
    public let expiresAt: Int64?
    public let semester: SharedSemester
    public let courses: [SharedCourse]
    public let exceptions: [SharedCourseException]

    public init(
        schema: String = SharedTimetablePayload.schemaV1,
        code: String? = nil,
        name: String,
        updatedAt: Int64,
        expiresAt: Int64? = nil,
        semester: SharedSemester,
        courses: [SharedCourse],
        exceptions: [SharedCourseException] = []
    ) {
        self.schema = schema
        self.code = code
        self.name = name
        self.updatedAt = updatedAt
        self.expiresAt = expiresAt
        self.semester = semester
        self.courses = courses
        self.exceptions = exceptions
    }
}

public enum SharedTimetable {
    public static func fromSnapshot(
        _ snapshot: AppSnapshot,
        displayName: String,
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        expiresAt: Int64? = nil,
        code: String? = nil
    ) throws -> SharedTimetablePayload {
        let live = snapshot.live()
        guard let sem = live.currentSemester else {
            throw TimetableXlsxError.noCurrentSemester
        }
        let liveCourses = live.courses.filter { $0.semesterId == sem.id && $0.deletedAt == nil }
        var indexById: [String: Int] = [:]
        for (idx, c) in liveCourses.enumerated() {
            indexById[c.id] = idx
        }
        let liveExceptions: [SharedCourseException] = live.exceptions.compactMap { ex in
            guard ex.deletedAt == nil, let idx = indexById[ex.courseId] else { return nil }
            return SharedCourseException(
                courseIndex: idx,
                week: ex.week,
                type: ex.type.rawValue,
                newWeek: ex.newWeek,
                newDayOfWeek: ex.newDayOfWeek,
                newStartPeriod: ex.newStartPeriod,
                newEndPeriod: ex.newEndPeriod,
                newRoom: ex.newRoom
            )
        }

        let sharedSemester = SharedSemester(
            name: sem.name,
            week1Monday: sem.week1Monday.description,
            totalWeeks: sem.totalWeeks,
            periods: sem.periods
                .sorted { $0.index < $1.index }
                .map { SharedPeriodTime(index: $0.index, start: $0.start.description, end: $0.end.description) }
        )
        let sharedCourses = liveCourses.map { c in
            SharedCourse(
                name: c.name,
                teacher: c.teacher,
                room: c.room,
                dayOfWeek: c.dayOfWeek,
                startPeriod: c.startPeriod,
                endPeriod: c.endPeriod,
                weeks: c.weeks.sorted(),
                colorIndex: c.colorIndex,
                kind: c.kind.rawValue,
                note: c.note
            )
        }

        return SharedTimetablePayload(
            schema: SharedTimetablePayload.schemaV1,
            code: code,
            name: displayName,
            updatedAt: updatedAt,
            expiresAt: expiresAt,
            semester: sharedSemester,
            courses: sharedCourses,
            exceptions: liveExceptions
        )
    }

    public static func toJSON(_ payload: SharedTimetablePayload) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(payload)
        return String(decoding: data, as: UTF8.self)
    }

    public static func fromJSON(_ raw: String) throws -> SharedTimetablePayload {
        try JSONDecoder().decode(SharedTimetablePayload.self, from: Data(raw.utf8))
    }
}

public struct ShareExpiryPreset: Sendable, Hashable, Identifiable {
    public var id: String { key }
    public let key: String
    public let label: String
    public let date: LocalDate
    public let expiresAt: Int64

    public init(key: String, label: String, date: LocalDate, expiresAt: Int64) {
        self.key = key
        self.label = label
        self.date = date
        self.expiresAt = expiresAt
    }
}

/// Share link expiry cap and preset computation (matches Android `ShareExpiry`).
public enum ShareExpiry {
    public static let defaultMaxDays: Int = 210

    public static func semesterEnd(_ sem: Semester) -> LocalDate {
        sem.week1Monday.addingDays(sem.totalWeeks * 7)
    }

    public static func nextSemesterStart(_ sem: Semester) -> LocalDate {
        let semEnd = semesterEnd(sem)
        let gapWeeks = (8...12).contains(sem.week1Monday.month) ? 6 : 8
        return semEnd.addingDays(gapWeeks * 7)
    }

    public static func maxDate(
        _ sem: Semester,
        today: LocalDate = .today(),
        maxDays: Int = defaultMaxDays
    ) -> LocalDate {
        let cap = today.addingDays(maxDays)
        let minAllowed = today.addingDays(1)
        let candidate = min(nextSemesterStart(sem), cap)
        return min(max(candidate, minAllowed), cap)
    }

    public static func endOfDayMillis(
        _ date: LocalDate,
        zone: TimeZone = .current
    ) -> Int64 {
        let dt = date.atTime(LocalTime(hour: 23, minute: 59, second: 59))
        return Int64(dt.toDate(in: zone).timeIntervalSince1970 * 1000) + 999
    }

    public static func maxMillis(
        _ sem: Semester,
        today: LocalDate = .today(),
        zone: TimeZone = .current,
        maxDays: Int = defaultMaxDays
    ) -> Int64 {
        endOfDayMillis(maxDate(sem, today: today, maxDays: maxDays), zone: zone)
    }

    public static func presets(
        _ sem: Semester,
        today: LocalDate = .today(),
        maxDays: Int = defaultMaxDays,
        zone: TimeZone = .current
    ) -> [ShareExpiryPreset] {
        let minDate = today.addingDays(1)
        let upper = maxDate(sem, today: today, maxDays: maxDays)
        let candidates: [(String, String, LocalDate)] = [
            ("1d", "1 天", today.addingDays(1)),
            ("7d", "7 天", today.addingDays(7)),
            ("30d", "30 天", today.addingDays(30)),
            ("semesterEnd", "本学期结束", semesterEnd(sem)),
            ("nextSemStart", "下学期开学前", upper),
        ]
        var seenDates = Set<LocalDate>()
        var out: [ShareExpiryPreset] = []
        for (key, label, rawDate) in candidates {
            let clamped = min(max(rawDate, minDate), upper)
            if seenDates.insert(clamped).inserted {
                out.append(
                    ShareExpiryPreset(
                        key: key,
                        label: label,
                        date: clamped,
                        expiresAt: endOfDayMillis(clamped, zone: zone)
                    )
                )
            }
        }
        return out
    }
}
