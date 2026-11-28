import Foundation
import Testing
@testable import GokeCore

@Suite("QuickAdd Parser, Config & Course-Session Todo Scoping")
struct QuickAddTests {
    private let now = LocalDateTime(year: 2026, month: 10, day: 26, hour: 8, minute: 0)
    private let sem = Semester(
        id: "sem1",
        name: "2026秋",
        week1Monday: LocalDate(year: 2026, month: 8, day: 31),
        totalWeeks: 20
    )
    private let otherSem = Semester(
        id: "sem2",
        name: "2027春",
        week1Monday: LocalDate(year: 2027, month: 3, day: 1),
        totalWeeks: 20
    )

    // MARK: - Reminder Shorthands & Config

    @Test("Standalone remind keyword and bang (! / ！)")
    func standaloneRemindAndBang() {
        let normal = QuickAddParser.parse("明天下午3点 开会 提醒", now: now)
        #expect(normal.title == "开会")
        #expect(normal.remindMinutesList == [0, 15])
        #expect(!normal.remindPersistent)
        #expect(normal.tokens.contains(QuickToken(text: "提醒", kind: .remind)))

        let bangAscii = QuickAddParser.parse("明天下午3点 开会 提醒!", now: now)
        #expect(bangAscii.title == "开会")
        #expect(bangAscii.remindMinutesList == [0, 15])
        #expect(bangAscii.remindPersistent)
        #expect(bangAscii.tokens.contains(QuickToken(text: "提醒!", kind: .remind)))

        let bangFull = QuickAddParser.parse("明天下午3点 开会 提醒！", now: now)
        #expect(bangFull.title == "开会")
        #expect(bangFull.remindMinutesList == [0, 15])
        #expect(bangFull.remindPersistent)
        #expect(bangFull.tokens.contains(QuickToken(text: "提醒！", kind: .remind)))
    }

    @Test("Custom QuickAddConfig keyword, offsets, and blank disable")
    func customQuickAddConfig() {
        let cfg = QuickAddConfig(
            remindKeyword: " 闹钟 ",
            remindMinutesList: [30, 5, 5],
            persistentRemindMinutesList: [10, 0],
            bangPersistent: false
        )
        let r1 = QuickAddParser.parse("交作业 闹钟", now: now, config: cfg)
        #expect(r1.title == "交作业")
        #expect(r1.remindMinutesList == [5, 30])
        #expect(!r1.remindPersistent)

        let r2 = QuickAddParser.parse("交作业 闹钟!", now: now, config: cfg)
        #expect(r2.title == "交作业")
        #expect(r2.remindMinutesList == [0, 10])
        #expect(!r2.remindPersistent)

        let disabled = QuickAddConfig(remindKeyword: "   ")
        let r3 = QuickAddParser.parse("交作业 提醒 提醒!", now: now, config: disabled)
        #expect(r3.title == "交作业 提醒 提醒!")
        #expect(r3.remindMinutesList.isEmpty)
        #expect(!r3.remindPersistent)
    }

    @Test("Explicit offset shorthands and multiple remind tokens")
    func explicitOffsetShorthandsAndMultipleTokens() {
        let r1 = QuickAddParser.parse("交作业 提醒30分钟", now: now)
        #expect(r1.title == "交作业")
        #expect(r1.remindMinutesList == [30])
        #expect(!r1.remindPersistent)

        let r2 = QuickAddParser.parse("交作业 提醒30分钟!", now: now)
        #expect(r2.title == "交作业")
        #expect(r2.remindMinutesList == [30])
        #expect(r2.remindPersistent)

        let r3 = QuickAddParser.parse("提前15分钟提醒! 交作业", now: now)
        #expect(r3.title == "交作业")
        #expect(r3.remindMinutesList == [15])
        #expect(r3.remindPersistent)

        let r4 = QuickAddParser.parse("提前半小时提醒 交作业", now: now)
        #expect(r4.title == "交作业")
        #expect(r4.remindMinutesList == [30])
        #expect(!r4.remindPersistent)

        let r5 = QuickAddParser.parse("交作业 提醒1小时!", now: now)
        #expect(r5.title == "交作业")
        #expect(r5.remindMinutesList == [60])
        #expect(r5.remindPersistent)

        let r6 = QuickAddParser.parse("交作业 提醒15m!", now: now)
        #expect(r6.title == "交作业")
        #expect(r6.remindMinutesList == [15])
        #expect(r6.remindPersistent)

        let r7 = QuickAddParser.parse("交作业 提醒0分钟", now: now)
        #expect(r7.title == "交作业")
        #expect(r7.remindMinutesList == [0])
        #expect(!r7.remindPersistent)

        let multi = QuickAddParser.parse("交作业 提醒30分钟 提醒5分钟!", now: now)
        #expect(multi.title == "交作业")
        #expect(multi.remindMinutesList == [5, 30])
        #expect(multi.remindPersistent)
    }

    @Test("Strict boundaries prevent false positives on remind keyword")
    func strictBoundariesPreventFalsePositives() {
        let r1 = QuickAddParser.parse("提醒小明交作业", now: now)
        #expect(r1.title == "提醒小明交作业")
        #expect(r1.remindMinutesList.isEmpty)
        #expect(!r1.remindPersistent)

        let r2 = QuickAddParser.parse("设置提醒功能", now: now)
        #expect(r2.title == "设置提醒功能")
        #expect(r2.remindMinutesList.isEmpty)

        let r3 = QuickAddParser.parse("设置提醒", now: now)
        #expect(r3.title == "设置提醒")
        #expect(r3.remindMinutesList.isEmpty)

        let r4 = QuickAddParser.parse("提醒小明交作业 提醒", now: now)
        #expect(r4.title == "提醒小明交作业")
        #expect(r4.remindMinutesList == [0, 15])
        #expect(!r4.remindPersistent)
    }

    @Test("Priority coexistence without collision")
    func priorityCoexistenceWithoutCollision() {
        let r1 = QuickAddParser.parse("买牛奶 提醒! !高", now: now)
        #expect(r1.title == "买牛奶")
        #expect(r1.priority == 3)
        #expect(r1.remindMinutesList == [0, 15])
        #expect(r1.remindPersistent)

        let r2 = QuickAddParser.parse("买牛奶 提醒!高", now: now)
        #expect(r2.title == "买牛奶")
        #expect(r2.priority == 3)
        #expect(r2.remindMinutesList == [0, 15])
        #expect(!r2.remindPersistent)

        let r3 = QuickAddParser.parse("买牛奶 提醒!!高", now: now)
        #expect(r3.title == "买牛奶")
        #expect(r3.priority == 3)
        #expect(r3.remindMinutesList == [0, 15])
        #expect(r3.remindPersistent)

        let r4 = QuickAddParser.parse("买牛奶 提醒30分钟 !1", now: now)
        #expect(r4.title == "买牛奶")
        #expect(r4.priority == 1)
        #expect(r4.remindMinutesList == [30])
        #expect(!r4.remindPersistent)

        let r5 = QuickAddParser.parse("买牛奶 !3 提醒!", now: now)
        #expect(r5.title == "买牛奶")
        #expect(r5.priority == 3)
        #expect(r5.remindMinutesList == [0, 15])
        #expect(r5.remindPersistent)
    }

    @Test("Remind without due date does not fabricate due")
    func remindWithoutDueDateDoesNotFabricateDue() {
        let r = QuickAddParser.parse("买牛奶 提醒!", now: now)
        #expect(r.title == "买牛奶")
        #expect(r.due == nil)
        #expect(!r.dueHasTime)
        #expect(r.remindMinutesList == [0, 15])
        #expect(r.remindPersistent)
        #expect(r.tokens == [QuickToken(text: "提醒!", kind: .remind)])
    }

    // MARK: - Chinese & Mixed Time Variants + Dates + Slash/Course/List/Tag

    @Test("Chinese and mixed natural-language time variants")
    func chineseAndMixedTimeVariants() {
        func timeOf(_ expr: String) -> (Int, Int) {
            let r = QuickAddParser.parse("明天 \(expr) 开会", now: now)
            #expect(r.title == "开会", "title mismatch for: \(expr)")
            #expect(r.dueHasTime, "dueHasTime should be true for: \(expr)")
            guard let due = r.due else {
                Issue.record("Missing due for \(expr)")
                return (-1, -1)
            }
            return (due.hour, due.minute)
        }

        #expect(timeOf("十二点三十") == (12, 30))
        #expect(timeOf("十二点三十分") == (12, 30))
        #expect(timeOf("十点零五") == (10, 5))
        #expect(timeOf("十点零五分") == (10, 5))
        #expect(timeOf("十点〇五") == (10, 5))
        #expect(timeOf("两点半") == (2, 30))
        #expect(timeOf("八点一刻") == (8, 15))
        #expect(timeOf("八点两刻") == (8, 30))
        #expect(timeOf("九点三刻") == (9, 45))
        #expect(timeOf("下午三点四十五") == (15, 45))
        #expect(timeOf("晚上十一点二十") == (23, 20))
        #expect(timeOf("12点三十") == (12, 30))
        #expect(timeOf("十二点30") == (12, 30))
        #expect(timeOf("12点30分") == (12, 30))
        #expect(timeOf("差一刻九点") == (8, 45))
        #expect(timeOf("差十分八点") == (7, 50))
        #expect(timeOf("下午差一刻三点") == (14, 45))
        #expect(timeOf("差5分到9点") == (8, 55))
        #expect(timeOf("八点整") == (8, 0))
        #expect(timeOf("21:30") == (21, 30))
    }

    @Test("Slash dates, /course linkage, #list, @tags, and parseLines subtasks")
    func slashDatesCourseAndBatchParseLines() {
        let r = QuickAddParser.parse("11/02 提交报告 /概率论 #学习 @数学 @作业 !2", now: now)
        #expect(r.title == "提交报告")
        #expect(r.due?.date == LocalDate(year: 2026, month: 11, day: 2))
        #expect(!r.dueHasTime)
        #expect(r.courseName == "概率论")
        #expect(r.listName == "学习")
        #expect(r.tags == ["数学", "作业"])
        #expect(r.priority == 2)

        let batch = QuickAddParser.parseLines("""
        1. 完成实验报告 明天 提醒
          - 整理数据
          - 画图
        2. 预习高数 /高等数学 !高
        """, now: now)
        #expect(batch.count == 4)
        #expect(batch[0].parentIndex == nil)
        #expect(batch[0].result.title == "完成实验报告")
        #expect(batch[0].result.remindMinutesList == [0, 15])
        #expect(batch[1].parentIndex == 0)
        #expect(batch[1].result.title == "整理数据")
        #expect(batch[2].parentIndex == 0)
        #expect(batch[2].result.title == "画图")
        #expect(batch[3].parentIndex == nil)
        #expect(batch[3].result.courseName == "高等数学")
        #expect(batch[3].result.priority == 3)
    }

    // MARK: - Single-Session Course Todo Scoping

    @Test("CourseExpander nextOccurrence merges multi-slot courses and occurrence handles reschedule/cancel")
    func courseExpanderSessionLogic() {
        let mon = Course(id: "c-mon", semesterId: sem.id, name: "高等数学", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: Set(1...16))
        let wed = Course(id: "c-wed", semesterId: sem.id, name: "高等数学", dayOfWeek: 3, startPeriod: 3, endPeriod: 4, weeks: Set(1...16))

        let next = CourseExpander.nextOccurrence(
            semester: sem,
            courses: [mon, wed],
            exceptions: [],
            courseId: mon.id,
            after: LocalDateTime(year: 2026, month: 9, day: 1, hour: 10, minute: 0)
        )
        #expect(next?.date == LocalDate(year: 2026, month: 9, day: 2))
        #expect(next?.course.id == wed.id)

        let course = Course(id: "c-prob", semesterId: sem.id, name: "概率论", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1, 2, 3, 5])
        let exMove = CourseException(
            id: "ex-move",
            courseId: course.id,
            week: 3,
            type: .RESCHEDULE,
            newWeek: 4,
            newDayOfWeek: 5,
            newStartPeriod: 3,
            newEndPeriod: 4,
            newRoom: "教3-301"
        )
        let exCancel = CourseException(id: "ex-cancel", courseId: course.id, week: 5, type: .CANCEL)

        let occ2 = CourseExpander.occurrence(semester: sem, courses: [course], exceptions: [exMove, exCancel], courseId: course.id, originalWeek: 2)
        #expect(occ2?.week == 2)
        #expect(occ2?.originalWeek == 2)
        #expect(occ2?.date == LocalDate(year: 2026, month: 9, day: 7))

        let occ3 = CourseExpander.occurrence(semester: sem, courses: [course], exceptions: [exMove, exCancel], courseId: course.id, originalWeek: 3)
        #expect(occ3?.week == 4)
        #expect(occ3?.originalWeek == 3)
        #expect(occ3?.date == LocalDate(year: 2026, month: 9, day: 25))
        #expect(occ3?.room == "教3-301")
        #expect(occ3?.rescheduled == true)

        #expect(CourseExpander.occurrence(semester: sem, courses: [course], exceptions: [exMove, exCancel], courseId: course.id, originalWeek: 5) == nil)
        #expect(CourseExpander.occurrence(semester: sem, courses: [course], exceptions: [exMove, exCancel], courseId: course.id, originalWeek: 99) == nil)
    }

    @Test("Pinned courseWeek task only matches its pinned slot and week, follows reschedule, and clears on cancel")
    func pinnedCourseWeekScoping() {
        let mon = Course(id: "c-mon", semesterId: sem.id, name: "概率论", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: Set(1...16))
        let fri = Course(id: "c-fri", semesterId: sem.id, name: "概率论", dayOfWeek: 5, startPeriod: 1, endPeriod: 2, weeks: Set(1...16))
        let courses = [mon, fri]
        let pinned = GokeTask(id: "t-pin", title: "交第一批作业").withCourseLink(courseId: mon.id, courseWeek: 14, dueBeforeNextClass: true)
        #expect(pinned.courseWeek == 14)

        let nowWeek14 = LocalDateTime(year: 2026, month: 11, day: 30, hour: 8, minute: 0)
        let counts = TaskLogic.occurrenceCounts([pinned], semester: sem, courses: courses, exceptions: [], now: nowWeek14)
        #expect(counts == [TaskLogic.CourseOccurrenceKey(courseId: mon.id, originalWeek: 14): 1])

        let occMon14 = CourseExpander.occurrence(semester: sem, courses: courses, exceptions: [], courseId: mon.id, originalWeek: 14)!
        let occFri14 = CourseExpander.occurrence(semester: sem, courses: courses, exceptions: [], courseId: fri.id, originalWeek: 14)!
        let occMon15 = CourseExpander.occurrence(semester: sem, courses: courses, exceptions: [], courseId: mon.id, originalWeek: 15)!
        #expect(TaskLogic.matchesOccurrence(task: pinned, occurrence: occMon14, semester: sem, courses: courses, exceptions: [], now: nowWeek14))
        #expect(!TaskLogic.matchesOccurrence(task: pinned, occurrence: occFri14, semester: sem, courses: courses, exceptions: [], now: nowWeek14))
        #expect(!TaskLogic.matchesOccurrence(task: pinned, occurrence: occMon15, semester: sem, courses: courses, exceptions: [], now: nowWeek14))

        let resched = CourseException(
            id: "ex-r",
            courseId: mon.id,
            week: 14,
            type: .RESCHEDULE,
            newDayOfWeek: 2,
            newStartPeriod: 3,
            newEndPeriod: 4
        )
        let nowWeek15 = LocalDateTime(year: 2026, month: 12, day: 7, hour: 10, minute: 0)
        let movedOcc = CourseExpander.occurrence(semester: sem, courses: courses, exceptions: [resched], courseId: mon.id, originalWeek: 14)!
        #expect(movedOcc.date == LocalDate(year: 2026, month: 12, day: 1))
        #expect(TaskLogic.matchesOccurrence(task: pinned, occurrence: movedOcc, semester: sem, courses: courses, exceptions: [resched], now: nowWeek15))
        #expect(TaskLogic.effectiveDue(task: pinned, semester: sem, courses: courses, exceptions: [resched], now: nowWeek15) == movedOcc.start)

        let cancelled = CourseException(id: "ex-c", courseId: mon.id, week: 14, type: .CANCEL)
        #expect(TaskLogic.occurrenceCounts([pinned], semester: sem, courses: courses, exceptions: [cancelled], now: nowWeek14).isEmpty)
    }

    @Test("Unpinned dueBeforeNextClass task attaches only to single next occurrence")
    func unpinnedNextOccurrenceScoping() {
        let mon = Course(id: "c-mon", semesterId: sem.id, name: "高等数学", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: Set(1...16))
        let fri = Course(id: "c-fri", semesterId: sem.id, name: "高等数学", dayOfWeek: 5, startPeriod: 1, endPeriod: 2, weeks: Set(1...16))
        let courses = [mon, fri]
        let unpinned = GokeTask(id: "t-unpinned", title: "预习", courseId: mon.id, dueBeforeNextClass: true)

        let tuesday = LocalDateTime(year: 2026, month: 9, day: 1, hour: 10, minute: 0)
        let counts = TaskLogic.occurrenceCounts([unpinned], semester: sem, courses: courses, exceptions: [], now: tuesday)
        #expect(counts == [TaskLogic.CourseOccurrenceKey(courseId: fri.id, originalWeek: 1): 1])
    }

    @Test("forCourse groups same semester slots and isolates other semesters")
    func forCourseGrouping() {
        let mon = Course(id: "c1", semesterId: sem.id, name: "数字逻辑", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1, 2])
        let thu = Course(id: "c2", semesterId: sem.id, name: " 数字逻辑 ", dayOfWeek: 4, startPeriod: 3, endPeriod: 4, weeks: [1, 2])
        let nextSemCourse = Course(id: "c3", semesterId: otherSem.id, name: "数字逻辑", dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1])

        let tasks = [
            GokeTask(id: "t1", title: "交预习报告", courseId: mon.id),
            GokeTask(id: "t2", title: "带实验板", courseId: thu.id),
            GokeTask(id: "t3", title: "已完成", done: true, courseId: mon.id),
            GokeTask(id: "t4", title: "子任务", parentId: "t1", courseId: mon.id),
            GokeTask(id: "t5", title: "已删除", courseId: mon.id, deletedAt: 123),
            GokeTask(id: "t6", title: "下学期", courseId: nextSemCourse.id),
        ]
        let courses = [mon, thu, nextSemCourse]

        #expect(TaskLogic.forCourse(tasks, courses: courses, target: mon).map(\.id) == ["t1", "t2"])
        #expect(TaskLogic.forCourse(tasks, courses: courses, target: thu, includeDone: true).map(\.id) == ["t1", "t2", "t3"])
        #expect(TaskLogic.forCourse(tasks, courses: courses, target: nextSemCourse).map(\.id) == ["t6"])
    }
}

@Suite("QuickAddParser Spans, Body Guard & Rewrite Helpers")
struct QuickAddTokenSpanAndRewriteTests {
    private let now = LocalDateTime(year: 2026, month: 9, day: 1, hour: 10, minute: 0)

    @Test("hasBodyText rejects empty and token-only inputs while allowing real titles")
    func hasBodyTextPredicate() {
        let empty = QuickAddParser.parse("   ", now: now)
        #expect(!empty.hasBodyText)
        #expect(!empty.hasBodyText(in: "   "))

        let tokenOnly = QuickAddParser.parse("明天 !高", now: now)
        #expect(!tokenOnly.hasBodyText)
        #expect(!tokenOnly.hasBodyText(in: "明天 !高"))
        #expect(tokenOnly.priority == 3)
        #expect(tokenOnly.due?.date == LocalDate(year: 2026, month: 9, day: 2))

        let multiTokenOnly = QuickAddParser.parse("明天 15:00 #学习 /高数 提醒!", now: now)
        #expect(!multiTokenOnly.hasBodyText)

        let normal = QuickAddParser.parse("买牛奶 明天 !高", now: now)
        #expect(normal.hasBodyText)
        #expect(normal.hasBodyText(in: "买牛奶 明天 !高"))
        #expect(normal.title == "买牛奶")

        let plain = QuickAddParser.parse("写高数作业", now: now)
        #expect(plain.hasBodyText)
        #expect(plain.title == "写高数作业")

        let duplicateDate = QuickAddParser.parse("明天 明天", now: now)
        #expect(!duplicateDate.hasBodyText)

        let partialRemind = QuickAddParser.parse("提醒 提醒事项", now: now)
        #expect(partialRemind.hasBodyText)
        #expect(partialRemind.title == "提醒事项")
    }

    @Test("tokenRanges enforces non-overlap and remind-keyword boundary checks")
    func tokenRangesBoundaryAndOverlap() {
        // "提醒" inside "不提醒" must NOT match; standalone "提醒" after space MUST match.
        let text = "不提醒 明天 提醒 !高"
        let parsed = QuickAddParser.parse(text, now: now)
        let spans = QuickAddParser.tokenRanges(in: text, tokens: parsed.tokens)

        let remindSpan = spans.first(where: { $0.token.kind == .remind })
        #expect(remindSpan != nil)
        if let remindSpan {
            #expect(String(text[remindSpan.range]) == "提醒")
            // Must point to the standalone "提醒" at index 7 (UTF-16), NOT inside "不提醒" (index 1).
            #expect(remindSpan.utf16Range.lowerBound == 7)
            #expect(remindSpan.utf16Range.count == 2)
        }

        let dateSpan = spans.first(where: { $0.token.kind == .date })
        #expect(dateSpan.map { String(text[$0.range]) } == "明天")

        let prioSpan = spans.first(where: { $0.token.kind == .priority })
        #expect(prioSpan.map { String(text[$0.range]) } == "!高")

        // Ensure no two spans overlap in UTF-16 space.
        let sorted = spans.sorted { $0.utf16Range.lowerBound < $1.utf16Range.lowerBound }
        for i in 1..<sorted.count {
            #expect(sorted[i - 1].utf16Range.upperBound <= sorted[i].utf16Range.lowerBound)
        }

        // Delimiter-bounded remind token: comma on left, priority on right.
        let delimText = "交报告，提醒! !中"
        let delimSpans = QuickAddParser.tokenRanges(for: delimText, now: now)
        #expect(delimSpans.first(where: { $0.token.kind == .remind }).map { String(delimText[$0.range]) } == "提醒!")
        #expect(delimSpans.first(where: { $0.token.kind == .priority }).map { String(delimText[$0.range]) } == "!中")

        // Non-overlapping identical tokens in manually supplied token list.
        let dupText = "明天 明天"
        let dupSpans = QuickAddParser.tokenRanges(
            in: dupText,
            tokens: [QuickToken(text: "明天", kind: .date), QuickToken(text: "明天", kind: .date)]
        )
        #expect(dupSpans.count == 2)
        #expect(dupSpans[0].utf16Range == 0..<2)
        #expect(dupSpans[1].utf16Range == 3..<5)
    }

    @Test("rewriteQuickField replaces/clears tokens and handles prefix prompts")
    func rewriteQuickFieldBehavior() {
        let input = "交高数作业 明天 15:00 !高 #学习 /高数"
        let parsed = QuickAddParser.parse(input, now: now)

        // Replace priority
        let rPrio = QuickAddParser.rewriteQuickField(
            current: input,
            tokens: parsed.tokens,
            kinds: [.priority],
            add: "!低"
        )
        #expect(rPrio.text == "交高数作业 明天 15:00 #学习 /高数 !低 ")
        #expect(rPrio.cursorUTF16 == rPrio.text.utf16.count)

        // Clear priority
        let rClearPrio = QuickAddParser.rewriteQuickField(
            current: input,
            tokens: parsed.tokens,
            kinds: [.priority],
            add: nil
        )
        #expect(rClearPrio.text == "交高数作业 明天 15:00 #学习 /高数")

        // Clear date + time together
        let rClearWhen = QuickAddParser.rewriteQuickField(
            current: input,
            tokens: parsed.tokens,
            kinds: [.date, .time],
            add: nil
        )
        #expect(rClearWhen.text == "交高数作业 !高 #学习 /高数")

        // Replace course
        let rCourse = QuickAddParser.rewriteQuickField(
            current: input,
            tokens: parsed.tokens,
            kinds: [.course],
            add: "/线代"
        )
        #expect(rCourse.text == "交高数作业 明天 15:00 !高 #学习 /线代 ")

        // Prefix prompt '#' omits trailing space and is idempotent
        let rPrefixEmpty = QuickAddParser.rewriteQuickField(current: "", tokens: [], kinds: [.list], add: "#")
        #expect(rPrefixEmpty.text == "#")
        let rPrefixRepeat = QuickAddParser.rewriteQuickField(current: "买菜 #", tokens: [], kinds: [.list], add: "#")
        #expect(rPrefixRepeat.text == "买菜 #")
        let rPrefixAppend = QuickAddParser.rewriteQuickField(current: "买菜", tokens: [], kinds: [.list], add: "#")
        #expect(rPrefixAppend.text == "买菜 #")
    }

    @Test("toggleTag adds missing tags and removes existing tags without disturbing siblings")
    func toggleTagBehavior() {
        let base = "交作业 @重要 @考试"
        let parsed = QuickAddParser.parse(base, now: now)

        let removed = QuickAddParser.toggleTag(current: base, tokens: parsed.tokens, tag: "重要")
        #expect(removed.text == "交作业 @考试")

        let added = QuickAddParser.toggleTag(current: "交作业 @考试", tokens: QuickAddParser.parse("交作业 @考试", now: now).tokens, tag: "复习")
        #expect(added.text == "交作业 @考试 @复习 ")
    }
}
