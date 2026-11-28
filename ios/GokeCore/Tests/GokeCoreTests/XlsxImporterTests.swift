import Foundation
import Testing
@testable import GokeCore

@Suite("AppSettings Reminders & TimetableXlsxImporter")
struct XlsxImporterTests {
    private static func makeSemester() -> Semester {
        Semester(
            id: "sem-xlsx",
            name: "2026春季学期",
            week1Monday: LocalDate(year: 2026, month: 3, day: 2),
            totalWeeks: 16,
            periods: GdutPeriods.default,
            isCurrent: true,
            updatedAt: 1000
        )
    }

    // MARK: - AppSettings Backward-Compatible Codable & Multi-Lead Normalization

    @Test("Legacy AppSettings JSON decodes with exam & multi-lead defaults")
    func legacyAppSettingsDecoding() throws {
        let legacyJSON = """
        {
          "themeSource": "PRESET",
          "preset": "mint",
          "remindCourse": true,
          "courseRemindMinutes": 20,
          "remindTask": true,
          "remindEvent": false
        }
        """
        let decoded = try JSONDecoder().decode(AppSettings.self, from: Data(legacyJSON.utf8))
        #expect(decoded.remindCourse == true)
        #expect(decoded.courseRemindMinutes == 20)
        #expect(decoded.courseRemindMinutesList == [20])
        #expect(decoded.effectiveCourseRemindMinutes == [20])
        #expect(decoded.remindExam == true)
        #expect(decoded.examRemindMinutesList == [60, 1440])
        #expect(decoded.effectiveExamRemindMinutes == [60, 1440])
        #expect(decoded.examRemindPersistent == false)
        #expect(decoded.remindEvent == false)
    }

    @Test("AppSettings round-trips multi-lead course & exam reminder settings")
    func appSettingsMultiLeadRoundTrip() throws {
        let settings = AppSettings(
            remindCourse: true,
            courseRemindMinutes: 15,
            courseRemindMinutesList: [30, 10, 10, -5],
            remindExam: true,
            examRemindMinutesList: [1440, 60, 30],
            examRemindPersistent: true
        )
        #expect(settings.courseRemindMinutes == 10)
        #expect(settings.courseRemindMinutesList == [10, 30])
        #expect(settings.examRemindMinutesList == [30, 60, 1440])
        #expect(settings.examRemindPersistent == true)

        let data = try JSONEncoder().encode(settings)
        let decoded = try JSONDecoder().decode(AppSettings.self, from: data)
        #expect(decoded == settings)
    }

    // MARK: - Round-trip with TimetableXlsxExporter

    @Test("Round-trips Goke-exported multi-week .xlsx timetable losslessly")
    func roundTripWithExporter() throws {
        let sem = Self.makeSemester()
        let courses: [Course] = [
            Course(
                id: "c1",
                semesterId: sem.id,
                name: "高等数学",
                teacher: "王老师",
                room: "教3-201",
                dayOfWeek: 1,
                startPeriod: 1,
                endPeriod: 2,
                weeks: Set(1...8),
                colorIndex: 0,
                updatedAt: 1000
            ),
            Course(
                id: "c2",
                semesterId: sem.id,
                name: "大学物理",
                teacher: "李老师",
                room: "实验楼B102",
                dayOfWeek: 3,
                startPeriod: 5,
                endPeriod: 7,
                weeks: Set([1, 3, 5, 7, 9, 11]),
                colorIndex: 1,
                updatedAt: 1000
            ),
            Course(
                id: "c3",
                semesterId: sem.id,
                name: "数据结构",
                teacher: "",
                room: "信工楼405",
                dayOfWeek: 5,
                startPeriod: 3,
                endPeriod: 4,
                weeks: Set(2...10),
                colorIndex: 2,
                updatedAt: 1000
            ),
        ]

        let xlsxData = TimetableXlsxExporter.write(
            semester: sem,
            courses: courses,
            includeTeacher: true
        )
        let result = TimetableXlsxImporter.import(data: xlsxData, semester: sem, now: 2000)

        #expect(result.error == nil)
        #expect(result.warnings.isEmpty)
        let imported = try #require(result.value)
        #expect(imported.courses.count == 3)
        #expect(imported.maxWeek == 11)

        let math = try #require(imported.courses.first(where: { $0.name == "高等数学" }))
        #expect(math.room == "教3-201")
        #expect(math.teacher == "王老师")
        #expect(math.dayOfWeek == 1)
        #expect(math.startPeriod == 1)
        #expect(math.endPeriod == 2)
        #expect(math.weeks == Set(1...8))
        #expect(math.source == .IMPORT)

        let physics = try #require(imported.courses.first(where: { $0.name == "大学物理" }))
        #expect(physics.room == "实验楼B102")
        #expect(physics.teacher == "李老师")
        #expect(physics.dayOfWeek == 3)
        #expect(physics.startPeriod == 5)
        #expect(physics.endPeriod == 7)
        #expect(physics.weeks == Set([1, 3, 5, 7, 9, 11]))

        let ds = try #require(imported.courses.first(where: { $0.name == "数据结构" }))
        #expect(ds.room == "信工楼405")
        #expect(ds.teacher == "")
        #expect(ds.dayOfWeek == 5)
        #expect(ds.startPeriod == 3)
        #expect(ds.endPeriod == 4)
        #expect(ds.weeks == Set(2...10))
    }

    @Test("Round-trips conflict cells exported with separator line")
    func roundTripConflictCells() throws {
        let sem = Semester(
            id: "sem-conflict",
            name: "测试学期",
            week1Monday: LocalDate(year: 2026, month: 3, day: 2),
            totalWeeks: 2,
            periods: GdutPeriods.default,
            isCurrent: true,
            updatedAt: 1000
        )
        let courses = [
            Course(
                id: "c1",
                semesterId: sem.id,
                name: "选修课A",
                teacher: "张老师",
                room: "A101",
                dayOfWeek: 2,
                startPeriod: 1,
                endPeriod: 2,
                weeks: [1],
                updatedAt: 1000
            ),
            Course(
                id: "c2",
                semesterId: sem.id,
                name: "选修课B",
                teacher: "赵老师",
                room: "B202",
                dayOfWeek: 2,
                startPeriod: 1,
                endPeriod: 2,
                weeks: [1],
                updatedAt: 1000
            ),
        ]

        let xlsxData = TimetableXlsxExporter.write(semester: sem, courses: courses, includeTeacher: true)
        let result = TimetableXlsxImporter.import(data: xlsxData, semester: sem, now: 2000)
        let imported = try #require(result.value)
        #expect(imported.courses.count == 2)
        let names = Set(imported.courses.map { $0.name })
        #expect(names == Set(["选修课A", "选修课B"]))
        for c in imported.courses {
            #expect(c.startPeriod == 1)
            #expect(c.endPeriod == 2)
            #expect(c.weeks == [1])
        }
    }

    // MARK: - University Single-Sheet Grid Format & Unmerged Adjacent Cells

    @Test("Parses single-sheet university grid with inline week specs (odd/even/ranges)")
    func singleSheetUniversityGrid() throws {
        let sem = Self.makeSemester()
        let sheet = XlsxSheet(
            name: "课程表",
            cells: [
                XlsxCellCoord(row: 1, col: 1): "节次",
                XlsxCellCoord(row: 1, col: 2): "星期一",
                XlsxCellCoord(row: 1, col: 3): "星期二",
                XlsxCellCoord(row: 1, col: 4): "星期三",
                XlsxCellCoord(row: 1, col: 5): "星期四",
                XlsxCellCoord(row: 1, col: 6): "星期五",
                XlsxCellCoord(row: 2, col: 1): "第1节",
                XlsxCellCoord(row: 3, col: 1): "第2节",
                XlsxCellCoord(row: 4, col: 1): "第3节",
                XlsxCellCoord(row: 5, col: 1): "第4节",
                // Unmerged adjacent cells with inline odd-week range
                XlsxCellCoord(row: 2, col: 2): "操作系统\n1-7周(单)\n教1-301\n陈教授",
                XlsxCellCoord(row: 3, col: 2): "操作系统\n1-7周(单)\n教1-301\n陈教授",
                // Merged cell with comma-separated ranges
                XlsxCellCoord(row: 4, col: 4): "计算机网络\n第1-3,6-8周\n计科楼204",
            ],
            merges: [
                XlsxCellRange(r1: 4, c1: 4, r2: 5, c2: 4),
            ]
        )

        let res = TimetableXlsxImporter.fromSheets([sheet], semester: sem, now: 3000)
        #expect(res.error == nil)
        let imported = try #require(res.value)
        #expect(imported.courses.count == 2)

        let os = try #require(imported.courses.first(where: { $0.name == "操作系统" }))
        #expect(os.dayOfWeek == 1)
        #expect(os.startPeriod == 1)
        #expect(os.endPeriod == 2)
        #expect(os.weeks == Set([1, 3, 5, 7]))
        #expect(os.room == "教1-301")
        #expect(os.teacher == "陈教授")

        let net = try #require(imported.courses.first(where: { $0.name == "计算机网络" }))
        #expect(net.dayOfWeek == 3)
        #expect(net.startPeriod == 3)
        #expect(net.endPeriod == 4)
        #expect(net.weeks == Set([1, 2, 3, 6, 7, 8]))
        #expect(net.room == "计科楼204")
    }

    // MARK: - DEFLATE Decompression & Edge Cases

    @Test("Inflates raw DEFLATE streams (stored & fixed Huffman)")
    func deflateInflaterTests() throws {
        // Fixed Huffman compressed "Hello, Goke XLSX!" (RFC 1951 raw deflate, no zlib header)
        // Verified via python3: zlib.compressobj(wbits=-15).compress(b"Hello, Goke XLSX!") + flush()
        let fixedDeflateBytes: [UInt8] = [
            0xF3, 0x48, 0xCD, 0xC9, 0xC9, 0xD7, 0x51, 0x70, 0xCF, 0xCF, 0x4E, 0x55,
            0x88, 0xF0, 0x09, 0x8E, 0x50, 0x04, 0x00,
        ]
        let inflated = try #require(MiniZipReader.decompressDeflate(Data(fixedDeflateBytes), expectedSize: 17))
        #expect(String(decoding: inflated, as: UTF8.self) == "Hello, Goke XLSX!")

        // Stored (uncompressed) raw DEFLATE block for "ABC"
        let storedBlock: [UInt8] = [0x01, 0x03, 0x00, 0xFC, 0xFF, 0x41, 0x42, 0x43]
        let storedOut = try #require(RawDeflateInflater.inflate(storedBlock))
        #expect(String(decoding: storedOut, as: UTF8.self) == "ABC")
    }

    @Test("Rejects non-XLSX bytes and warns when weeks exceed semester total")
    func errorAndWarningCases() throws {
        let sem = Self.makeSemester()
        let badRes = TimetableXlsxImporter.import(data: Data("not a zip".utf8), semester: sem)
        #expect(badRes.value == nil)
        #expect(badRes.error == "not_xlsx")

        #expect(XlsxReader.parseRef("B12") == XlsxCellCoord(row: 12, col: 2))
        #expect(XlsxReader.parseRef("AA3") == XlsxCellCoord(row: 3, col: 27))
        #expect(XlsxReader.parseRef("invalid") == nil)

        let overflowSheet = XlsxSheet(
            name: "第20周",
            cells: [
                XlsxCellCoord(row: 1, col: 2): "周一",
                XlsxCellCoord(row: 1, col: 3): "周二",
                XlsxCellCoord(row: 1, col: 4): "周三",
                XlsxCellCoord(row: 1, col: 5): "周四",
                XlsxCellCoord(row: 1, col: 6): "周五",
                XlsxCellCoord(row: 2, col: 1): "1",
                XlsxCellCoord(row: 2, col: 2): "期末复习\n教1-101",
            ]
        )
        let res = TimetableXlsxImporter.fromSheets([overflowSheet], semester: sem, now: 4000)
        #expect(res.error == nil)
        #expect(res.warnings.contains("weeks_exceed:20"))
        #expect(res.value?.maxWeek == 20)
    }
}
