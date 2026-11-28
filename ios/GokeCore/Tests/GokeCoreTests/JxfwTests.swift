import Foundation
import Testing
@testable import GokeCore

@Suite("Campus Domain & SyncMapper")
struct JxfwTests {
    @Test("JxfwSyncMapper deterministic IDs, exam->Course mapping, tombstone & manual protection, weekly diff")
    func jxfwSyncMapperRules() throws {
        let sem = Semester(
            id: "sem-1",
            name: "2026-2027 第一学期",
            week1Monday: LocalDate(year: 2026, month: 9, day: 7)
        )
        let manualCourse = Course(
            id: "manual-1",
            semesterId: sem.id,
            name: "自习课",
            dayOfWeek: 5,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2],
            source: .MANUAL
        )
        let deletedCourseId = JxfwSyncMapper.courseId(term: "202601", day: 2, periods: [3, 4], name: "已删课程")
        let deletedCourse = Course(
            id: deletedCourseId,
            semesterId: sem.id,
            name: "已删课程",
            dayOfWeek: 2,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [1],
            source: .IMPORT,
            updatedAt: 100,
            deletedAt: 100
        )

        let snap0 = AppSnapshot(semesters: [sem], courses: [manualCourse, deletedCourse])

        let emptyPlan = JxfwSyncMapper.fullImport(snapshot: snap0, semester: sem, term: "202601", lessons: [])
        #expect(emptyPlan.isNoop)
        #expect(emptyPlan.skippedReason != nil)

        let lessons = [
            JxfwLesson(kcmc: "高等数学", jcdm2: "02,01", zcs: "1,2,3", xq: "1", jxcdmcs: "教3-101,教3-102", teaxms: "张三[主讲]"),
            JxfwLesson(kcmc: "已删课程", jcdm2: "03,04", zcs: "1", xq: "2", jxcdmcs: "教1-202", teaxms: "李四"),
        ]
        let exams = [
            JxfwExam(
                ksrcdm: "EX001",
                kcmc: "高等数学",
                ksrq: "2026-12-20",
                kssj: "14:00--16:00",
                kscdmc: "教3-201",
                xqmc: "大学城校区",
                ksaplxmc: "学院停课考",
                zwh: "18"
            ),
        ]

        let plan1 = JxfwSyncMapper.fullImport(
            snapshot: snap0,
            semester: sem,
            term: "202601",
            lessons: lessons,
            exams: exams,
            week1Monday: LocalDate(year: 2026, month: 8, day: 31),
            now: 1000
        )
        let snap1 = snap0.applying(plan1.changeSet, at: 1000)

        #expect(snap1.semesters[0].week1Monday == LocalDate(year: 2026, month: 8, day: 31))
        #expect(snap1.courses.contains(where: { $0.id == "manual-1" && $0.deletedAt == nil }))
        #expect(snap1.courses.first(where: { $0.id == deletedCourseId })?.deletedAt != nil)

        let mathId = JxfwSyncMapper.courseId(term: "202601", day: 1, periods: [1, 2], name: "高等数学")
        let mathCourse = try #require(snap1.courses.first(where: { $0.id == mathId }))
        #expect(mathCourse.teacher == "张三")
        #expect(mathCourse.weeks == [1, 2, 3])

        let examCourse = try #require(snap1.courses.first(where: { $0.id == "jxfw-exam-course/202601/EX001" }))
        #expect(examCourse.isExam)
        #expect(examCourse.kind == .EXAM)
        #expect(examCourse.remindPersistent == true)
        #expect(examCourse.startTime == "14:00")
        #expect(examCourse.endTime == "16:00")
        #expect(examCourse.name == "考试：高等数学")
        #expect(examCourse.room == "教3-201")
        #expect(examCourse.colorIndex == JxfwSyncMapper.examColorIndex)
        #expect(examCourse.dayOfWeek == 7)
        #expect(examCourse.weeks == [16])
        #expect(JxfwSyncMapper.extractExamSeat(from: examCourse.note) == "18")

        let split = JxfwSyncMapper.splitExamDisplay("期中考试：高等数学（A）")
        #expect(split.badgeFull == "期中考试")
        #expect(split.badgeShort == "期中")
        #expect(split.title == "高等数学(A)")
        #expect(JxfwSyncMapper.tokenizeVerticalTitle("高数(3)A+") == ["高", "数", "(3)", "A+"])
        #expect(JxfwSyncMapper.formatVerticalText("高等数学", maxTokens: 3) == "高\n等\n…")
        #expect(JxfwSyncMapper.isSameCourseForExam("考试：高等数学（A）", "高等数学(A)"))

        let legacyTask = try #require(JxfwSyncMapper.examTask(term: "202601", exam: exams[0]))
        let legacySnap = AppSnapshot(semesters: [snap1.semesters[0]], tasks: [legacyTask])
        let migrateCS = try #require(JxfwSyncMapper.migrateExamTasksToCourses(snapshot: legacySnap, now: 1500))
        let migratedSnap = legacySnap.applying(migrateCS, at: 1500)
        #expect(migratedSnap.live().tasks.isEmpty)
        let migratedCourse = try #require(migratedSnap.live().courses.first(where: { $0.id == "jxfw-exam-course/202601/EX001" }))
        #expect(migratedCourse.isExam)
        #expect(migratedCourse.startTime == "14:00")
        #expect(migratedCourse.endTime == "16:00")

        let plan2 = JxfwSyncMapper.fullImport(
            snapshot: snap1,
            semester: snap1.semesters[0],
            term: "202601",
            lessons: lessons,
            exams: exams,
            week1Monday: LocalDate(year: 2026, month: 8, day: 31),
            now: 2000
        )
        #expect(plan2.isNoop)

        let subsetPlan = JxfwSyncMapper.weeklyUpdate(
            snapshot: snap1,
            semester: snap1.semesters[0],
            term: "202601",
            week: 1,
            instances: [JxfwInstance(kcmc: "高等数学", jcdm2: "01,02", xq: "1", jxcdmc: "教3-101", teaxms: "张三")],
            now: 3000
        )
        #expect(subsetPlan.isNoop)

        let movedPlan = JxfwSyncMapper.weeklyUpdate(
            snapshot: snap1,
            semester: snap1.semesters[0],
            term: "202601",
            week: 2,
            instances: [JxfwInstance(kcmc: "高等数学", jcdm2: "05,06", xq: "3", jxcdmc: "实验楼B301", teaxms: "张三")],
            now: 4000
        )
        let snap2 = snap1.applying(movedPlan.changeSet, at: 4000)
        let exId = JxfwSyncMapper.exceptionId(courseId: mathId, week: 2)
        let ex = try #require(snap2.exceptions.first(where: { $0.id == exId }))
        #expect(ex.type == .RESCHEDULE)
        #expect(ex.newDayOfWeek == 3)
        #expect(ex.newStartPeriod == 5)
        #expect(ex.newEndPeriod == 6)
        #expect(ex.newRoom == "实验楼B301")

        let emptyWeek = JxfwSyncMapper.weeklyUpdate(
            snapshot: snap1,
            semester: snap1.semesters[0],
            term: "202601",
            week: 1,
            instances: [],
            now: 5000
        )
        #expect(emptyWeek.isNoop)
        #expect(emptyWeek.skippedReason != nil)
    }

    @Test("JxfwParse pure helpers handle remarks, periods, weeks, terms, and grade summaries")
    func gradesDeferredUngradedAndSuperseded() {
        #expect(JxfwParse.mergeRemarks("  实验  ", "", "实验", "带电脑") == "实验 · 带电脑")
        #expect(JxfwParse.periods("02,01,02") == [1, 2])
        #expect(JxfwParse.weeks("1,3,2") == [1, 2, 3])
        #expect(JxfwParse.dayOfWeek("7") == 7)
        #expect(JxfwParse.dayOfWeek("9") == nil)
        #expect(JxfwParse.currentTerm(today: LocalDate(year: 2026, month: 9, day: 1)) == "202601")
        #expect(JxfwParse.currentTerm(today: LocalDate(year: 2026, month: 3, day: 1)) == "202502")
        #expect(JxfwParse.termLabel("202601") == "2026-2027 第一学期")
        #expect(JxfwParse.termLabel("202602") == "2026-2027 第二学期")

        let deferredZero = JxfwGrade(
            term: "202502",
            code: "EE201",
            name: "电路分析基础",
            credit: 2.5,
            score: "0",
            gpa: 0.0,
            examType: "正常考试"
        )
        let deferredPassed = JxfwGrade(
            term: "202601",
            code: "EE201",
            name: "电路分析基础",
            credit: 2.5,
            score: "64",
            gpa: 1.4,
            examType: "正常考试"
        )
        let explicitDeferred = JxfwGrade(
            term: "202601",
            code: "MA202",
            name: "复变函数",
            credit: 2.0,
            score: "0",
            gpa: 0.0,
            status: "缓考"
        )
        let ungraded = JxfwGrade(
            term: "202601",
            code: "MA203",
            name: "复变函数与积分变换",
            credit: 2.0,
            score: "",
            gpa: nil
        )

        #expect(JxfwParse.isDeferredGrade(explicitDeferred))
        #expect(!JxfwParse.isPassedGrade(explicitDeferred))
        #expect(JxfwParse.isUngradedGrade(ungraded))
        #expect(!JxfwParse.isPassedGrade(ungraded))

        let all = [deferredZero, deferredPassed, explicitDeferred, ungraded]
        let superseded = JxfwParse.supersededGradeKeys(all)
        #expect(superseded == [deferredZero.filterKey])

        let summary = JxfwParse.summarizeGrades(all)
        #expect(abs(summary.totalCredits - 2.5) < 1e-9)
        #expect(abs(summary.earnedCredits - 2.5) < 1e-9)
        #expect(abs(summary.gpaCredits - 2.5) < 1e-9)
        #expect(abs((summary.weightedGpa ?? 0) - 1.4) < 1e-9)
        #expect(summary.count == 4)

        let termOnly = JxfwParse.summarizeGrades([deferredZero], supersededKeys: superseded)
        #expect(termOnly.totalCredits == 0.0)
        #expect(termOnly.gpaCredits == 0.0)
        #expect(termOnly.weightedGpa == nil)
    }

    @Test("JxfwSyncMapper isolates single-week exam remarks without flagging multi-week courses and self-heals legacy DB")
    func examCourseAutoClassification() throws {
        #expect(JxfwSyncMapper.isExamRemark("期中考试"))
        #expect(JxfwSyncMapper.isExamRemark("随堂小测"))
        #expect(JxfwSyncMapper.isExamRemark("Midterm"))
        #expect(!JxfwSyncMapper.isExamRemark("第七章 软件测试"))
        #expect(!JxfwSyncMapper.isExamRemark("期中考试复习"))
        #expect(!JxfwSyncMapper.isExamRemark("期末考试答疑"))

        let sem = Semester(id: "sem-ex", name: "Term", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let lessons = [
            JxfwLesson(
                kcmc: "数字逻辑",
                jcdm2: "03,04",
                zcs: (1...16).map(String.init).joined(separator: ","),
                xq: "4",
                jxcdmcs: "教1-201"
            ),
            JxfwLesson(kcmc: "软件工程", jcdm2: "03,04", zcs: "1,2", xq: "2", sknrjj: "第七章 软件测试"),
        ]
        let weekInstances: [Int: [JxfwInstance]] = [
            16: [JxfwInstance(kcmc: "数字逻辑", jcdm2: "03,04", xq: "4", jxcdmc: "教1-201", sknrjj: "期末考试")],
        ]
        let plan = JxfwSyncMapper.fullImport(
            snapshot: AppSnapshot(semesters: [sem]),
            semester: sem,
            term: "202601",
            lessons: lessons,
            weekInstances: weekInstances,
            now: 100
        )
        let snap = AppSnapshot(semesters: [sem]).applying(plan.changeSet, at: 100)

        let logicRegular = try #require(snap.live().courses.first(where: { $0.id.hasPrefix(JxfwSyncMapper.coursePrefix) && $0.name == "数字逻辑" }))
        #expect(logicRegular.kind == .NORMAL)
        #expect(!logicRegular.isExam)
        #expect(logicRegular.weeks == Set(1...15))

        let logicExam = try #require(snap.live().courses.first(where: { $0.id.hasPrefix(JxfwSyncMapper.remarkExamCoursePrefix) }))
        #expect(logicExam.kind == .EXAM)
        #expect(logicExam.isExam)
        #expect(logicExam.weeks == [16])
        #expect(logicExam.name == "期末考试：数字逻辑")

        let se = try #require(snap.live().courses.first(where: { $0.name == "软件工程" }))
        #expect(se.kind == .NORMAL)
        #expect(se.note == "第七章 软件测试")
    }

    @Test("Course Codable decodes legacy JSON without startTime/endTime/remindPersistent and round-trips new fields")
    func courseCodableBackwardCompatibility() throws {
        let legacyJSON = """
        {
          "id": "c-legacy",
          "semesterId": "sem-1",
          "name": "概率论",
          "dayOfWeek": 1,
          "startPeriod": 1,
          "endPeriod": 2,
          "weeks": [1, 2, 3]
        }
        """
        let decodedLegacy = try MintJSON.makeDecoder().decode(Course.self, from: Data(legacyJSON.utf8))
        #expect(decodedLegacy.startTime == nil)
        #expect(decodedLegacy.endTime == nil)
        #expect(decodedLegacy.remindPersistent == false)
        #expect(decodedLegacy.kind == .NORMAL)

        let exam = Course(
            id: "jxfw-exam-course/202601/EX1",
            semesterId: "sem-1",
            name: "考试：概率论",
            dayOfWeek: 5,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [16],
            kind: .EXAM,
            remindPersistent: true,
            startTime: "09:00",
            endTime: "11:00"
        )
        let encoded = try MintJSON.makeEncoder().encode(exam)
        let roundTripped = try MintJSON.makeDecoder().decode(Course.self, from: encoded)
        #expect(roundTripped == exam)
        #expect(roundTripped.startLocalTime == LocalTime(hour: 9, minute: 0))
        #expect(roundTripped.endLocalTime == LocalTime(hour: 11, minute: 0))
    }

    @Test("JxfwXkParse summarizes schedules, formats week ranges, and detects dual-source conflicts")
    func courseSelectionPureLogic() {
        #expect(JxfwXkParse.formatWeeksRange([1, 2, 3, 5, 7, 8]) == "1-3,5,7-8周")

        let localCourse = Course(
            id: "c1",
            semesterId: "sem-1",
            name: "线性代数",
            dayOfWeek: 2,
            startPeriod: 3,
            endPeriod: 4,
            weeks: [1, 2]
        )
        let localOccupied = JxfwXkParse.localCoursesToOccupiedSlots([localCourse])
        #expect(localOccupied.count == 4)

        let item = XkCourseItem(kcrwdm: "TASK1", name: "音乐鉴赏", capacity: 60, enrolledCount: 55)
        let slots = [
            XkSessionSlot(kcrwdm: "TASK1", week: 1, dayOfWeek: 2, periods: [3, 4], room: "教2-101(城)", roomCode: "0102", teacher: "王老师"),
            XkSessionSlot(kcrwdm: "TASK1", week: 2, dayOfWeek: 2, periods: [3, 4], room: "教2-101(城)", roomCode: "0102", teacher: "王老师"),
        ]
        let merged = JxfwXkParse.mergeCourseData(
            items: [item],
            enrolledIds: [],
            slotsByTask: ["TASK1": slots],
            jxfwOccupied: [],
            localOccupied: localOccupied
        )
        #expect(merged.count == 1)
        #expect(merged[0].teacher == "王老师")
        #expect(merged[0].campusHint == "大学城")
        #expect(merged[0].hasConflict)
        #expect(merged[0].conflicts.first?.courseName == "线性代数")
        #expect(merged[0].conflicts.first?.weeks == [1, 2])
    }

    @Test("TzcsParse filters gender-specific items, merges reports, and summarizes best score")
    func tzcsPureReportLogic() {
        let r2025 = TzcsYearReport(
            year: 2025,
            totalScore: "82.5",
            totalGrade: "良好",
            items: [
                TzcsItem(name: "1000米跑", rawValue: "3'50", itemScore: "80", grade: "良好"),
                TzcsItem(name: "800米跑", rawValue: "", itemScore: "", grade: ""),
                TzcsItem(name: "空项目"),
            ]
        )
        let r2026 = TzcsYearReport(year: 2026, totalScore: "88.0", totalGrade: "良好", items: [])

        let merged = TzcsParse.mergeReports(existing: [r2025], incoming: [r2026])
        #expect(merged.map(\.year) == [2026, 2025])

        let filtered = TzcsParse.filterApplicableItems(merged)
        let items2025 = filtered.first(where: { $0.year == 2025 })?.items ?? []
        #expect(items2025.map(\.name) == ["1000米跑"])

        let summary = TzcsParse.summarizeReports(filtered)
        #expect(summary.yearCount == 2)
        #expect(summary.recordedYearCount == 2)
        #expect(summary.bestYear == 2026)
        #expect(summary.bestScore == 88.0)
    }
}
