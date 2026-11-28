import Foundation
import Testing
@testable import GokeCore

@Suite("ICS Codec & Backup Codec")
struct ParcelICSTests {
    @Test("ICSCodec round-trips courses, events, and tasks cleanly")
    func icsRoundTrip() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let course = Course(
            id: "c1",
            semesterId: "s1",
            name: "数据结构",
            teacher: "李教授",
            room: "教2-305",
            dayOfWeek: 2,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [1, 2, 3, 5, 6]
        )
        let event = Event(
            id: "e1",
            title: "期中答辩",
            start: LocalDateTime(year: 2026, month: 10, day: 15, hour: 14, minute: 0),
            end: LocalDateTime(year: 2026, month: 10, day: 15, hour: 16, minute: 0),
            location: "报告厅",
            note: "带PPT\\n提前10分钟",
            remindMinutes: 15
        )
        let task = GokeTask(
            id: "t1",
            title: "提交实验报告",
            note: "PDF格式",
            priority: 3,
            due: LocalDateTime(year: 2026, month: 10, day: 20, hour: 23, minute: 0),
            dueHasTime: true,
            remindMinutes: 30
        )

        let snap = AppSnapshot(semesters: [sem], courses: [course], events: [event], tasks: [task])
        let icsText = ICSCodec.write(snapshot: snap, zone: ICSCodec.shanghaiTimeZone, nowMillis: 1_700_000_000_000)
        #expect(icsText.contains("BEGIN:VCALENDAR"))
        #expect(icsText.contains("BEGIN:VEVENT"))
        #expect(icsText.contains("BEGIN:VTODO"))

        let parsed = ICSCodec.parse(icsText, targetZone: ICSCodec.shanghaiTimeZone)
        #expect(parsed.isSuccess)
        let cal = parsed.value!
        #expect(cal.todos.count == 1)
        // 1 event + 2 course week runs ([1,2,3] and [5,6])
        #expect(cal.events.count == 3)

        let tasks = ICSCodec.toTasks(cal, now: 1000).value!
        #expect(tasks.count == 1)
        #expect(tasks[0].title == "提交实验报告")
        #expect(tasks[0].priority == 3)
        #expect(tasks[0].remindMinutes == 30)

        // Course-only ICS detection & import
        let courseSnap = AppSnapshot(semesters: [sem], courses: [course])
        let courseIcs = ICSCodec.write(snapshot: courseSnap, zone: ICSCodec.shanghaiTimeZone, nowMillis: 1_700_000_000_000)
        let courseCal = ICSCodec.parse(courseIcs, targetZone: ICSCodec.shanghaiTimeZone).value!
        #expect(ICSCodec.looksLikeTimetable(courseCal))
        let importedCourses = ICSCodec.toCourses(courseCal, semester: sem, now: 1000).value!
        #expect(importedCourses.courses.count == 1)
        #expect(importedCourses.courses[0].name == "数据结构")
        #expect(importedCourses.courses[0].weeks == [1, 2, 3, 5, 6])
    }

    @Test("BackupCodec round-trips ExportBundle and decodes legacy mintcal-web format")
    func backupCodecRoundTripAndLegacy() throws {
        let sem = Semester(id: "s1", name: "2026秋", week1Monday: GdutPeriods.week1Monday, updatedAt: 100)
        let task = GokeTask(id: "t1", title: "复习高数", updatedAt: 101)
        let bundle = ExportBundle(exportedAt: 1_730_000_000_000, snapshot: AppSnapshot(semesters: [sem], tasks: [task]))

        let encoded = try BackupCodec.encode(bundle, pretty: true)
        let decoded = try BackupCodec.decode(encoded)
        #expect(decoded == bundle)

        let legacyJson = """
        {
          "app": "mintcal-web",
          "exportedAt": "2026-10-01T08:00:00Z",
          "records": [
            {
              "_t": "taskList",
              "id": "list-1",
              "name": "收件箱",
              "colorIndex": 1,
              "sort": 0,
              "updatedAt": 50,
              "deletedAt": null,
              "userId": null
            },
            {
              "_t": "task",
              "id": "task-1",
              "title": "旧版导入任务",
              "updatedAt": 51,
              "deletedAt": null,
              "userId": null
            }
          ]
        }
        """
        let legacy = try BackupCodec.decode(legacyJson)
        #expect(legacy.app == "mintcal-web")
        #expect(legacy.exportedAt > 0)
        #expect(legacy.snapshot.lists.count == 1)
        #expect(legacy.snapshot.tasks.count == 1)
        #expect(legacy.snapshot.tasks[0].title == "旧版导入任务")
    }

    @Test("ICSCodec.writeTimetable exports courses and semester metadata only (no events/todos)")
    func timetableOnlyIcsExport() throws {
        let sem = Semester(id: "s1", name: "2026秋", week1Monday: LocalDate(year: 2026, month: 8, day: 31), totalWeeks: 18)
        let course = Course(
            id: "c1",
            semesterId: "s1",
            name: "操作系统",
            teacher: "王老师",
            room: "教1-202",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2, 3]
        )
        let event = Event(
            id: "e1",
            title: "私人日程",
            start: LocalDateTime(year: 2026, month: 9, day: 1, hour: 12, minute: 0),
            end: LocalDateTime(year: 2026, month: 9, day: 1, hour: 13, minute: 0)
        )
        let task = GokeTask(id: "t1", title: "私人待办")
        let snap = AppSnapshot(semesters: [sem], courses: [course], events: [event], tasks: [task])

        let ics = try #require(ICSCodec.writeTimetable(snapshot: snap, zone: ICSCodec.shanghaiTimeZone, nowMillis: 1_700_000_000_000))
        #expect(ics.contains("X-WR-CALNAME:2026秋"))
        #expect(ics.contains("X-GOKE-SEMESTER:2026秋"))
        #expect(ics.contains("X-GOKE-WEEK1-MONDAY:2026-08-31"))
        #expect(ics.contains("X-GOKE-TOTAL-WEEKS:18"))
        #expect(ics.contains("操作系统"))
        #expect(!ics.contains("私人日程"))
        #expect(!ics.contains("BEGIN:VTODO"))

        let parsed = try #require(ICSCodec.parse(ics, targetZone: ICSCodec.shanghaiTimeZone).value)
        #expect(parsed.todos.isEmpty)
        #expect(parsed.events.count == 1)
        #expect(ICSCodec.looksLikeTimetable(parsed))
    }

    @Test("TimetableXlsxExporter generates valid OOXML ZIP with merged cells and conflict styling")
    func timetableXlsxExport() throws {
        let sem = Semester(id: "s1", name: "2026秋", week1Monday: LocalDate(year: 2026, month: 8, day: 31), totalWeeks: 2)
        let c1 = Course(
            id: "c1",
            semesterId: "s1",
            name: "数据结构",
            teacher: "李教授",
            room: "教2-305",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1],
            colorIndex: 1
        )
        let c2 = Course(
            id: "c2",
            semesterId: "s1",
            name: "大学物理",
            teacher: "张教授",
            room: "教3-101",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 1,
            weeks: [1],
            colorIndex: 3
        )
        let c3 = Course(
            id: "c3",
            semesterId: "s1",
            name: "线性代数",
            teacher: "赵老师",
            room: "教1-105",
            dayOfWeek: 3,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [1, 2],
            colorIndex: 0
        )
        let snap = AppSnapshot(semesters: [sem], courses: [c1, c2, c3])

        let xlsx = try TimetableXlsxExporter.write(snapshot: snap, includeTeacher: true)
        // ZIP local file header signature ("PK\x03\x04") & EOCD signature ("PK\x05\x06")
        #expect(xlsx.prefix(4) == Data([0x50, 0x4B, 0x03, 0x04]))
        #expect(xlsx.range(of: Data([0x50, 0x4B, 0x05, 0x06])) != nil)

        // Since STORE method (0) is used, XML entries are directly inspectable inside the ZIP payload.
        let text = String(decoding: xlsx, as: UTF8.self)
        #expect(text.contains("[Content_Types].xml"))
        #expect(text.contains("xl/workbook.xml"))
        #expect(text.contains("xl/styles.xml"))
        #expect(text.contains("xl/sharedStrings.xml"))
        #expect(text.contains("xl/worksheets/sheet1.xml"))
        #expect(text.contains("xl/worksheets/sheet2.xml"))
        #expect(text.contains("第1周"))
        #expect(text.contains("第2周"))
        #expect(text.contains("数据结构"))
        #expect(text.contains("李教授"))
        // Conflict separator for overlapping c1 & c2 on Monday period 1
        #expect(text.contains("───"))
        // Merged cell for c3 (Wednesday = col D, periods 3-4 -> rows 4:5)
        #expect(text.contains("<mergeCell ref=\"D4:D5\"/>"))

        // Excluding teacher omits teacher name from shared strings
        let noTeacherData = try TimetableXlsxExporter.write(snapshot: snap, includeTeacher: false)
        let noTeacherText = String(decoding: noTeacherData, as: UTF8.self)
        #expect(!noTeacherText.contains("李教授"))
    }

    @Test("SharedTimetable payload round-trips JSON and ShareExpiry computes clamped presets")
    func sharedTimetableAndExpiry() throws {
        let sem = Semester(id: "s1", name: "2026秋", week1Monday: LocalDate(year: 2026, month: 8, day: 31), totalWeeks: 18)
        let c1 = Course(
            id: "c1",
            semesterId: "s1",
            name: "编译原理",
            teacher: "陈老师",
            room: "教4-201",
            dayOfWeek: 2,
            startPeriod: 5,
            endPeriod: 6,
            weeks: [1, 2, 3],
            colorIndex: 2
        )
        let ex1 = CourseException(id: "ex1", courseId: "c1", week: 2, type: .RESCHEDULE, newRoom: "教4-205")
        let snap = AppSnapshot(semesters: [sem], courses: [c1], exceptions: [ex1])

        let payload = try SharedTimetable.fromSnapshot(snap, displayName: "小明", updatedAt: 1_700_000_000_000, expiresAt: 1_710_000_000_000)
        #expect(payload.schema == SharedTimetablePayload.schemaV1)
        #expect(payload.courses.count == 1)
        #expect(payload.exceptions.count == 1)
        #expect(payload.exceptions[0].courseIndex == 0)
        #expect(payload.exceptions[0].newRoom == "教4-205")

        let json = try SharedTimetable.toJSON(payload)
        let decoded = try SharedTimetable.fromJSON(json)
        #expect(decoded == payload)

        let today = LocalDate(year: 2026, month: 9, day: 1)
        let presets = ShareExpiry.presets(sem, today: today, zone: ICSCodec.shanghaiTimeZone)
        #expect(presets.count >= 4)
        #expect(presets.contains(where: { $0.key == "semesterEnd" }))
        let maxD = ShareExpiry.maxDate(sem, today: today)
        for p in presets {
            #expect(p.date <= maxD)
            #expect(p.date >= today.addingDays(1))
        }
    }
}
