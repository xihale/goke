import Foundation
import Testing
@testable import GokeCore

@Suite("Core Model & Wire-JSON Parity")
struct CoreModelTests {

    @Test("LocalDate civil calendar math, leap years, and ISO day of week")
    func localDateMath() {
        #expect(GdutPeriods.week1Monday == LocalDate(year: 2026, month: 8, day: 31))
        #expect(GdutPeriods.week1Monday.dayOfWeek == 1) // Monday
        #expect(LocalDate(year: 1970, month: 1, day: 1).dayOfWeek == 4) // Thursday
        #expect(LocalDate.isLeapYear(2024))
        #expect(!LocalDate.isLeapYear(2026))
        #expect(LocalDate.isLeapYear(2000))
        #expect(!LocalDate.isLeapYear(1900))

        let jan31 = LocalDate(year: 2026, month: 1, day: 31)
        #expect(jan31.addingMonths(1) == LocalDate(year: 2026, month: 2, day: 28))
        #expect(LocalDate(year: 2024, month: 2, day: 29).addingYears(1) == LocalDate(year: 2025, month: 2, day: 28))
        #expect(jan31.daysUntil(LocalDate(year: 2026, month: 3, day: 1)) == 29)
    }

    @Test("LocalDateTime lenient parsing & ISO-8601 formatting")
    func localDateTimeLenientParsing() throws {
        #expect(LocalDateTime.parseLenient("2026-09-01T08:30") == LocalDateTime(year: 2026, month: 9, day: 1, hour: 8, minute: 30))
        #expect(LocalDateTime.parseLenient("2026-09-01 08:30") == LocalDateTime(year: 2026, month: 9, day: 1, hour: 8, minute: 30))
        #expect(LocalDateTime.parseLenient("2026-09-01") == LocalDateTime(year: 2026, month: 9, day: 1, hour: 0, minute: 0))
        #expect(LocalDateTime(year: 2026, month: 9, day: 1, hour: 8, minute: 30).description == "2026-09-01T08:30")
        #expect(LocalDateTime(year: 2026, month: 9, day: 1, hour: 8, minute: 30, second: 45).description == "2026-09-01T08:30:45")
    }

    @Test("All 6 Record types encode with exact _t discriminator and encodeDefaults parity")
    func allSixRecordsWireParity() throws {
        let sem = Semester(id: "sem-1", name: "大一下", week1Monday: GdutPeriods.week1Monday, updatedAt: 100)
        let course = Course(
            id: "c-1",
            semesterId: "sem-1",
            name: "高等数学",
            teacher: "王老师",
            room: "教3-204",
            dayOfWeek: 2,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2, 3, 5],
            colorIndex: 3,
            remindMinutes: 15,
            note: "带计算器",
            updatedAt: 101
        )
        let ex = CourseException(id: "ex-1", courseId: "c-1", week: 2, type: .CANCEL, updatedAt: 102)
        let ev = Event(
            id: "ev-1",
            title: "社团例会",
            start: LocalDateTime(year: 2026, month: 10, day: 23, hour: 20, minute: 0),
            end: LocalDateTime(year: 2026, month: 10, day: 23, hour: 21, minute: 30),
            recurrence: Recurrence(
                freq: .WEEKLY,
                interval: 2,
                byDays: [1, 5],
                until: LocalDate(year: 2026, month: 12, day: 31),
                exDates: [LocalDate(year: 2026, month: 11, day: 6)]
            ),
            kind: .EXAM,
            updatedAt: 103
        )
        let list = TaskList(id: "l-1", name: "学习", colorIndex: 2, sort: 1, updatedAt: 104)
        let task = GokeTask(
            id: "t-1",
            title: "交作业",
            listId: "l-1",
            tags: ["数学", "紧急"],
            priority: 3,
            startDate: LocalDate(year: 2026, month: 10, day: 20),
            due: LocalDateTime(year: 2026, month: 10, day: 23, hour: 18, minute: 0),
            dueHasTime: true,
            estimateMinutes: 90,
            repeat: Recurrence(freq: .DAILY, interval: 2, count: 5),
            remindMinutes: 30,
            courseId: "c-1",
            extra: ["code": "12-3-4567"],
            attachments: [Attachment(name: "hw.pdf", mime: "application/pdf", file: "abc.pdf")],
            updatedAt: 105
        )

        let cases: [(Record, String, String)] = [
            (.semester(sem), "semester", "Semester"),
            (.course(course), "course", "Course"),
            (.courseException(ex), "courseException", "CourseException"),
            (.event(ev), "event", "Event"),
            (.taskList(list), "taskList", "TaskList"),
            (.task(task), "task", "Task"),
        ]

        for (rec, expectedDisc, expectedKind) in cases {
            let json = try MintJSON.encodeToString(rec)
            #expect(json.contains("\"_t\":\"\(expectedDisc)\""))
            #expect(json.contains("\"deletedAt\":null"))
            #expect(json.contains("\"userId\":null"))

            let decoded = try MintJSON.decodeFromString(Record.self, from: json)
            #expect(decoded == rec)

            let wire = try WireRecord.encode(rec)
            #expect(wire.kind == expectedKind)
            #expect(wire.id == rec.id)
            #expect(wire.decodeRecord() == rec)
        }

        // Verify explicit defaults on GokeTask JSON
        let taskJson = try MintJSON.encodeToString(Record.task(task))
        #expect(taskJson.contains("\"dropped\":false"))
        #expect(taskJson.contains("\"remindPersistent\":false"))
        #expect(taskJson.contains("\"dueBeforeNextClass\":false"))
        #expect(taskJson.contains("\"done\":false"))
    }

    @Test("Decode web/Android WireRecord fixtures including omitted newer fields & unknown _t")
    func decodeWebFixtureParity() throws {
        let webTaskWire = """
        {
          "id": "b4d963e9-6cca-451f-8e41-ad8094915ffe",
          "kind": "Task",
          "data": {
            "_t": "task",
            "id": "b4d963e9-6cca-451f-8e41-ad8094915ffe",
            "updatedAt": 1700000000007,
            "deletedAt": null,
            "userId": null,
            "title": "交作业",
            "note": "",
            "listId": "16294041-b1e6-41fc-9206-0be5b516b0b0",
            "tags": ["数学", "紧急"],
            "priority": 3,
            "startDate": "2026-10-20",
            "due": "2026-10-23T18:00",
            "dueHasTime": true,
            "estimateMinutes": 90,
            "done": false,
            "doneAt": null,
            "parentId": null,
            "repeat": {
              "freq": "DAILY",
              "interval": 2,
              "byDays": [],
              "until": null,
              "count": 5,
              "exDates": []
            },
            "remindMinutes": 30,
            "courseId": "2543be38-67ec-4162-ba94-b0a7461f9369",
            "dueBeforeNextClass": false,
            "blockStart": null,
            "blockMinutes": null,
            "sortOrder": 0,
            "source": "MANUAL",
            "extra": {}
          },
          "updatedAt": 1700000000007,
          "deletedAt": null
        }
        """

        let wire = try MintJSON.decodeFromString(WireRecord.self, from: webTaskWire)
        #expect(wire.seq == 0)
        guard case .task(let task) = wire.decodeRecord() else {
            Issue.record("Expected .task record")
            return
        }
        #expect(task.title == "交作业")
        #expect(task.tags == ["数学", "紧急"])
        #expect(task.priority == 3)
        #expect(task.repeat?.freq == .DAILY)
        #expect(task.repeat?.count == 5)
        #expect(task.dropped == false)
        #expect(task.attachments.isEmpty)

        // Unknown future _t returns nil cleanly instead of crashing sync
        let unknownWire = try MintJSON.decodeFromString(
            WireRecord.self,
            from: #"{"id":"x","kind":"Future","data":{"_t":"futureEntity","id":"x"},"updatedAt":1,"deletedAt":null}"#
        )
        #expect(unknownWire.decodeRecord() == nil)
    }

    @Test("ChangeSet _t add/modify/remove JSON round-trip and SnapshotDiff")
    func changeSetAndDiff() throws {
        let t1 = GokeTask(id: "t1", title: "Old Title")
        let t1Mod = GokeTask(id: "t1", title: "New Title")
        let t2 = GokeTask(id: "t2", title: "Added")
        let t3 = GokeTask(id: "t3", title: "Removed")

        let before = AppSnapshot(tasks: [t1, t3])
        let after = AppSnapshot(tasks: [t1Mod, t2])
        let diff = SnapshotDiff.diff(before: before, after: after)
        #expect(diff.count == 3)

        let cs = ChangeSet(id: "cs-1", description: "Batch edit", source: .MANUAL, createdAt: 500, changes: diff)
        #expect(cs.addCount == 1)
        #expect(cs.modifyCount == 1)
        #expect(cs.removeCount == 1)

        let json = try cs.toJSON()
        #expect(json.contains("\"_t\":\"add\""))
        #expect(json.contains("\"_t\":\"modify\""))
        #expect(json.contains("\"_t\":\"remove\""))
        let decoded = try ChangeSet.fromJSON(json)
        #expect(decoded == cs)
    }

    @Test("GdutPeriods and AppSettings defaults")
    func periodsAndSettings() throws {
        #expect(GdutPeriods.default.count == 12)
        #expect(GdutPeriods.default.first?.start == LocalTime(hour: 8, minute: 30))
        #expect(GdutPeriods.default.last?.end == LocalTime(hour: 20, minute: 55))

        let settings = AppSettings.default
        #expect(settings.preset == "mint")
        #expect(settings.seedColorARGB == 0xFF2E776C)
        #expect(settings.showTeacher == true)
        #expect(settings.hapticFeedback == true)
        #expect(settings.soundFeedback == true)
        #expect(settings.pendingUpdateVersion == "")
        #expect(settings.pendingUpdateUrl == "")
        #expect(settings.pendingUpdateNotes == "")
        #expect(settings.parcelWindowStart == 14 * 60)
        #expect(settings.parcelWindowEnd == 22 * 60)
        let roundTrip = try MintJSON.decodeFromString(AppSettings.self, from: MintJSON.encodeToString(settings))
        #expect(roundTrip == settings)

        // Backward-compatible decoding when newer feedback & pendingUpdate fields are absent
        let legacyJSON = #"{"preset":"peach","showTeacher":false}"#
        let decodedLegacy = try MintJSON.decodeFromString(AppSettings.self, from: legacyJSON)
        #expect(decodedLegacy.preset == "peach")
        #expect(decodedLegacy.showTeacher == false)
        #expect(decodedLegacy.hapticFeedback == true)
        #expect(decodedLegacy.soundFeedback == true)
        #expect(decodedLegacy.pendingUpdateVersion == "")
        #expect(decodedLegacy.pendingUpdateUrl == "")
        #expect(decodedLegacy.pendingUpdateNotes == "")
    }

    @Test("Reminders normalization and backward-compatible remindMinutesList + Course.kind Codable")
    func multiRemindersAndCourseKindParity() throws {
        #expect(Reminders.normalize(primary: 30, list: [15, -5, 30, 60, 15]) == [15, 30, 60])
        #expect(Reminders.normalize(primary: nil, list: [10, 5, 10]) == [5, 10])

        // Legacy Course JSON without remindMinutesList or kind decodes cleanly
        let legacyCourseJSON = """
        {
          "_t": "course",
          "id": "c-legacy",
          "semesterId": "s1",
          "name": "物理实验",
          "dayOfWeek": 1,
          "startPeriod": 1,
          "endPeriod": 2,
          "weeks": [1, 2],
          "remindMinutes": 15
        }
        """
        let decodedCourse = try MintJSON.decodeFromString(Course.self, from: legacyCourseJSON)
        #expect(decodedCourse.remindMinutes == 15)
        #expect(decodedCourse.remindMinutesList == [15])
        #expect(decodedCourse.effectiveRemindMinutes == [15])
        #expect(decodedCourse.kind == .NORMAL)
        #expect(!decodedCourse.isExam)

        // Multi-offset Course with EXAM kind round-trips normalized & sorted
        let examCourse = Course(
            id: "c-exam",
            semesterId: "s1",
            name: "期中考试",
            dayOfWeek: 3,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [9],
            remindMinutes: 30,
            remindMinutesList: [60, 15, 30, -1],
            kind: .EXAM
        )
        #expect(examCourse.remindMinutesList == [15, 30, 60])
        #expect(examCourse.effectiveRemindMinutes == [15, 30, 60])
        #expect(examCourse.isExam)
        let encodedExam = try MintJSON.encodeToString(examCourse)
        let roundTripExam = try MintJSON.decodeFromString(Course.self, from: encodedExam)
        #expect(roundTripExam == examCourse)

        // Event & GokeTask legacy remindMinutes -> remindMinutesList + effectiveRemindMinutes
        let legacyEventJSON = """
        {
          "_t": "event",
          "id": "e-legacy",
          "title": "期末考",
          "start": "2026-12-20T14:00",
          "end": "2026-12-20T16:00",
          "remindMinutes": 45,
          "kind": "EXAM"
        }
        """
        let decodedEvent = try MintJSON.decodeFromString(Event.self, from: legacyEventJSON)
        #expect(decodedEvent.remindMinutesList == [45])
        #expect(decodedEvent.effectiveRemindMinutes == [45])
        #expect(decodedEvent.isExam)

        let multiTask = GokeTask(
            id: "jxfw-exam/202601/EX1",
            title: "高等数学考试",
            remindMinutesList: [120, 30, 120]
        )
        #expect(multiTask.effectiveRemindMinutes == [30, 120])
        #expect(multiTask.isExam)
    }

    @Test("CourseNotes parses week-scoped lines, general notes, and user note appending")
    func courseNotesParsing() {
        let raw = """
        请带实验手册
        第1,3-4周：线上授课
        第2周：线下实验课
        第5周：
        请带实验手册
        """
        let week3 = CourseNotes.parse(raw, week: 3)
        #expect(week3.currentWeekNotes == ["线上授课"])
        #expect(week3.generalNotes == ["请带实验手册"])
        #expect(week3.otherWeekLines == ["第2周：线下实验课", "第5周："])
        #expect(week3.focusedNotes == ["线上授课", "请带实验手册"])
        #expect(week3.hasOtherWeeks)

        #expect(CourseNotes.summaryForWeek(raw, week: 2) == "线下实验课\n请带实验手册")
        #expect(CourseNotes.stripWeekPrefix("第1-4周：期中考试") == "期中考试")
        #expect(CourseNotes.stripWeekPrefix("普通备注") == "普通备注")
        #expect(CourseNotes.appendUserNote(existingNote: "A", addition: "B") == "A\nB")
        #expect(CourseNotes.appendUserNote(existingNote: "A\nB", addition: "B") == "A\nB")
    }

    @Test("GokeTask courseWeek extra pin & AppSettings QuickAddConfig legacy decoding")
    func courseWeekAndQuickAddSettingsRoundTrip() throws {
        let base = GokeTask(id: "t-cw", title: "交实验报告")
        #expect(base.courseWeek == nil)
        let pinned = base.withCourseLink(courseId: "c-1", courseWeek: 6, dueBeforeNextClass: true)
        #expect(pinned.courseId == "c-1")
        #expect(pinned.courseWeek == 6)
        #expect(pinned.dueBeforeNextClass)
        #expect(pinned.extra[GokeTask.extraCourseWeekKey] == "6")

        let encodedTask = try MintJSON.encodeToString(Record.task(pinned))
        let decodedRecord = try MintJSON.decodeFromString(Record.self, from: encodedTask)
        guard case .task(let decodedTask) = decodedRecord else {
            Issue.record("Expected .task")
            return
        }
        #expect(decodedTask.courseWeek == 6)

        let unpinned = decodedTask.withCourseLink(courseId: nil, courseWeek: 6, dueBeforeNextClass: true)
        #expect(unpinned.courseId == nil)
        #expect(unpinned.courseWeek == nil)
        #expect(!unpinned.dueBeforeNextClass)

        // Legacy AppSettings JSON without QuickAdd keys decodes cleanly with defaults
        let legacyJSON = #"{"themeSource":"PRESET","preset":"mint","darkMode":"DARK"}"#
        let decodedSettings = try MintJSON.decodeFromString(AppSettings.self, from: legacyJSON)
        #expect(decodedSettings.darkMode == .DARK)
        #expect(decodedSettings.quickAddConfig == .default)

        var updated = decodedSettings
        updated.applyQuickAddConfig(
            QuickAddConfig(
                remindKeyword: "闹钟",
                remindMinutesList: [30, 10],
                persistentRemindMinutesList: [5, 0],
                bangPersistent: false
            )
        )
        let reEncoded = try MintJSON.encodeToString(updated)
        let reDecoded = try MintJSON.decodeFromString(AppSettings.self, from: reEncoded)
        #expect(reDecoded.quickAddConfig.remindKeyword == "闹钟")
        #expect(reDecoded.quickAddConfig.effectiveRemindMinutes == [10, 30])
        #expect(reDecoded.quickAddConfig.effectivePersistentRemindMinutes == [0, 5])
        #expect(!reDecoded.quickAddConfig.bangPersistent)
    }
}
