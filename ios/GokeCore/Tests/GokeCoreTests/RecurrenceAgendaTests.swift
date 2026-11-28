import Foundation
import Testing
@testable import GokeCore

@Suite("Recurrence, Agenda & Timetable Layout")
struct RecurrenceAgendaTests {

    @Test("SemesterCalendar week math & WeekSets formatting/parsing")
    func semesterCalendarAndWeekSets() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let cal = SemesterCalendar(semester: sem)
        #expect(cal.weekOf(LocalDate(year: 2026, month: 8, day: 31)) == 1)
        #expect(cal.weekOf(LocalDate(year: 2026, month: 9, day: 6)) == 1)
        #expect(cal.weekOf(LocalDate(year: 2026, month: 9, day: 7)) == 2)
        #expect(cal.weekOf(LocalDate(year: 2026, month: 8, day: 30)) == 0)
        #expect(cal.dateOf(week: 2, dayOfWeek: 3) == LocalDate(year: 2026, month: 9, day: 9))

        #expect(WeekSets.format([1, 2, 3, 5, 7, 8]) == "1-3,5,7,8")
        #expect(WeekSets.parse("1-5单,8-10周") == [1, 3, 5, 8, 9, 10])
    }

    @Test("RecurrenceEngine WEEKLY/MONTHLY/YEARLY with count and exDates")
    func recurrenceEngineRules() {
        // Weekly Mon & Fri every 2 weeks with exDate
        let weekly = Recurrence(
            freq: .WEEKLY,
            interval: 2,
            byDays: [1, 5],
            exDates: [LocalDate(year: 2026, month: 9, day: 18)]
        )
        let anchor = LocalDate(year: 2026, month: 8, day: 31) // Monday
        let dates = RecurrenceEngine.dates(
            rule: weekly,
            anchor: anchor,
            from: LocalDate(year: 2026, month: 8, day: 31),
            to: LocalDate(year: 2026, month: 9, day: 20)
        )
        #expect(dates == [
            LocalDate(year: 2026, month: 8, day: 31),
            LocalDate(year: 2026, month: 9, day: 4),
            LocalDate(year: 2026, month: 9, day: 14),
            // 2026-09-18 excluded
        ])

        // Monthly on 31st skips February & April
        let monthly31 = Recurrence(freq: .MONTHLY, byDays: [31])
        let mDates = RecurrenceEngine.dates(
            rule: monthly31,
            anchor: LocalDate(year: 2026, month: 1, day: 31),
            from: LocalDate(year: 2026, month: 1, day: 1),
            to: LocalDate(year: 2026, month: 5, day: 31)
        )
        #expect(mDates == [
            LocalDate(year: 2026, month: 1, day: 31),
            LocalDate(year: 2026, month: 3, day: 31),
            LocalDate(year: 2026, month: 5, day: 31),
        ])

        // Yearly cartesian product byMonths x byDays with count
        let yearly = Recurrence(freq: .YEARLY, byDays: [1, 15], count: 3, byMonths: [3, 9])
        let yDates = RecurrenceEngine.dates(
            rule: yearly,
            anchor: LocalDate(year: 2026, month: 3, day: 1),
            from: LocalDate(year: 2026, month: 1, day: 1),
            to: LocalDate(year: 2027, month: 12, day: 31)
        )
        #expect(yDates == [
            LocalDate(year: 2026, month: 3, day: 1),
            LocalDate(year: 2026, month: 3, day: 15),
            LocalDate(year: 2026, month: 9, day: 1),
        ])
    }

    @Test("CourseExpander applies CANCEL and RESCHEDULE exceptions")
    func courseExpanderExceptions() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let course = Course(
            id: "c1",
            semesterId: "s1",
            name: "操作系统",
            room: "教1-101",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2, 3]
        )
        let cancelW2 = CourseException(id: "ex1", courseId: "c1", week: 2, type: .CANCEL)
        let moveW3 = CourseException(
            id: "ex2",
            courseId: "c1",
            week: 3,
            type: .RESCHEDULE,
            newWeek: 3,
            newDayOfWeek: 4,
            newStartPeriod: 5,
            newEndPeriod: 6,
            newRoom: "实验楼-302"
        )

        let occs = CourseExpander.occurrences(
            semester: sem,
            courses: [course],
            exceptions: [cancelW2, moveW3],
            from: LocalDate(year: 2026, month: 8, day: 31),
            to: LocalDate(year: 2026, month: 9, day: 20)
        )
        #expect(occs.count == 2)
        #expect(occs[0].week == 1)
        #expect(occs[0].room == "教1-101")
        #expect(!occs[0].rescheduled)
        #expect(occs[1].week == 3)
        #expect(occs[1].date.dayOfWeek == 4)
        #expect(occs[1].startPeriod == 5)
        #expect(occs[1].room == "实验楼-302")
        #expect(occs[1].rescheduled)
    }

    @Test("AgendaEngine suppresses duplicate same-day TaskItem when BlockItem covers due, and ranks correctly")
    func agendaDedupAndRanking() {
        let day = LocalDate(year: 2026, month: 9, day: 1)
        let allDayEv = Event(
            id: "e-all",
            title: "开学典礼",
            start: day.atStartOfDay(),
            end: day.atTime(hour: 23, minute: 59),
            allDay: true
        )
        let blockTask = GokeTask(
            id: "t-block",
            title: "写实验报告",
            due: day.atTime(hour: 14, minute: 0),
            dueHasTime: true,
            blockStart: day.atTime(hour: 14, minute: 0),
            blockMinutes: 60
        )
        let dateOnlyTask = GokeTask(
            id: "t-date",
            title: "买文具",
            due: day.atStartOfDay(),
            dueHasTime: false
        )
        let snap = AppSnapshot(events: [allDayEv], tasks: [blockTask, dateOnlyTask])
        let items = AgendaEngine.forDay(snapshot: snap, date: day)
        #expect(items.count == 3)
        if case .event(_, _, _, let allDay) = items[0] {
            #expect(allDay)
        } else {
            Issue.record("Expected all-day event first")
        }
        if case .block(let t, _, _) = items[1] {
            #expect(t.id == "t-block")
        } else {
            Issue.record("Expected BlockItem second")
        }
        if case .task(let t, _, let hasTime) = items[2] {
            #expect(t.id == "t-date")
            #expect(!hasTime)
        } else {
            Issue.record("Expected date-only TaskItem last")
        }
    }

    @Test("TaskLogic complete repeating task decrements count and shifts dates")
    func taskLogicRepeatingComplete() {
        let now = LocalDateTime(year: 2026, month: 9, day: 1, hour: 10, minute: 0)
        let task = GokeTask(
            id: "t1",
            title: "每日背单词",
            startDate: LocalDate(year: 2026, month: 9, day: 1),
            due: LocalDateTime(year: 2026, month: 9, day: 1, hour: 21, minute: 0),
            dueHasTime: true,
            repeat: Recurrence(freq: .DAILY, interval: 1, count: 3),
            blockStart: LocalDateTime(year: 2026, month: 9, day: 1, hour: 20, minute: 0),
            blockMinutes: 30
        )
        let (finished, next) = TaskLogic.complete(task, nowMillis: 1000, now: now, nextId: { "t2" })
        #expect(finished.done)
        #expect(finished.doneAt == 1000)
        #expect(next?.id == "t2")
        #expect(next?.done == false)
        #expect(next?.due == LocalDateTime(year: 2026, month: 9, day: 2, hour: 21, minute: 0))
        #expect(next?.blockStart == nil)
        #expect(next?.repeat?.count == 2)
    }

    @Test("TimetableLayout computes Equatable overlap lanes and folds >3h windows to flexiblePills")
    func timetableLayoutOverlapAndCleanGrid() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let c1 = Course(id: "c1", semesterId: "s1", name: "高数", dayOfWeek: 1, startPeriod: 1, endPeriod: 2, weeks: [1])
        let c2 = Course(id: "c2", semesterId: "s1", name: "冲突选修", dayOfWeek: 1, startPeriod: 2, endPeriod: 3, weeks: [1])
        let parcelWindowTask = GokeTask(
            id: "t-parcel",
            title: "取丰巢快递",
            blockStart: LocalDateTime(year: 2026, month: 8, day: 31, hour: 14, minute: 0),
            blockMinutes: 8 * 60 // 8 hours > 3h -> folds to flexible pill
        )
        let shortBlock = GokeTask(
            id: "t-short",
            title: "午间讨论",
            blockStart: LocalDateTime(year: 2026, month: 8, day: 31, hour: 13, minute: 50),
            blockMinutes: 45
        )
        let snap = AppSnapshot(semesters: [sem], courses: [c1, c2], tasks: [parcelWindowTask, shortBlock])
        let layout1 = TimetableLayout.buildWeek(snapshot: snap, week: 1)
        let layout2 = TimetableLayout.buildWeek(snapshot: snap, week: 1)
        #expect(layout1 != nil)
        #expect(layout1 == layout2)

        let monday = layout1!.columns[0]
        #expect(monday.dayOfWeek == 1)
        #expect(monday.flexiblePills.count == 1)
        #expect(monday.flexiblePills[0].task.id == "t-parcel")

        let overlapping = monday.slots.filter { $0.hasOverlap }
        #expect(overlapping.count == 2)
        #expect(Set(overlapping.map(\.laneIndex)) == [0, 1])
        #expect(overlapping.allSatisfy { $0.laneCount == 2 })

        let nonOverlapping = monday.slots.first { $0.id.hasPrefix("block:t-short") }
        #expect(nonOverlapping?.laneCount == 1)
        #expect(nonOverlapping?.startPeriod == 5)
    }

    @Test("CourseProgress classifies TimePhase and fraction accurately")
    func courseProgressPhases() {
        let start = LocalDateTime(year: 2026, month: 9, day: 7, hour: 8, minute: 30)
        let end = LocalDateTime(year: 2026, month: 9, day: 7, hour: 10, minute: 5) // 95 min

        let before = LocalDateTime(year: 2026, month: 9, day: 7, hour: 8, minute: 0)
        #expect(CourseProgress.phase(start: start, end: end, now: before) == .upcoming)
        #expect(CourseProgress.fraction(start: start, end: end, now: before) == 0.0)
        #expect(CourseProgress.percent(start: start, end: end, now: before) == 0)

        #expect(CourseProgress.phase(start: start, end: end, now: start) == .ongoing(progress: 0.0))
        let mid = LocalDateTime(year: 2026, month: 9, day: 7, hour: 9, minute: 17, second: 30)
        let midPhase = CourseProgress.phase(start: start, end: end, now: mid)
        #expect(midPhase.isOngoing)
        #expect(abs(midPhase.progress - 0.5) < 1e-4)
        #expect(CourseProgress.percent(start: start, end: end, now: mid) == 50)

        #expect(CourseProgress.phase(start: start, end: end, now: end) == .finished)
        #expect(CourseProgress.fraction(start: start, end: end, now: end) == 1.0)
        #expect(CourseProgress.percent(start: start, end: end, now: end) == 100)

        #expect(CourseProgress.phase(start: start, end: start, now: before) == .upcoming)
        #expect(CourseProgress.phase(start: start, end: start, now: start) == .finished)
    }

    @Test("TimetableLayout.calcNowBarSpan insets concentrically along block rounded corners")
    func calcNowBarSpanConcentricCorners() {
        let block = TimetableNowBarBlock(left: 1.5, top: 2.0, right: 45.0, bottom: 58.0, cornerRadius: 12.0)

        let above = TimetableLayout.calcNowBarSpan(defaultStartX: 3.0, defaultEndX: 43.5, y: 1.5, edgeInset: 1.5, blocks: [block])
        #expect(abs(above.startX - 3.0) < 0.01)
        #expect(abs(above.endX - 43.5) < 0.01)

        let mid = TimetableLayout.calcNowBarSpan(defaultStartX: 3.0, defaultEndX: 43.5, y: 30.0, edgeInset: 1.5, blocks: [block])
        #expect(abs(mid.startX - 3.0) < 0.01)
        #expect(abs(mid.endX - 43.5) < 0.01)

        let atTopEdge = TimetableLayout.calcNowBarSpan(defaultStartX: 3.0, defaultEndX: 43.5, y: 2.0, edgeInset: 1.5, blocks: [block])
        #expect(abs(atTopEdge.startX - (block.left + 12.0)) < 0.01)
        #expect(abs(atTopEdge.endX - (block.right - 12.0)) < 0.01)

        let inTopCorner = TimetableLayout.calcNowBarSpan(defaultStartX: 3.0, defaultEndX: 43.5, y: 8.0, edgeInset: 1.5, blocks: [block])
        #expect(inTopCorner.startX > 3.0)
        #expect(inTopCorner.endX < 43.5)
        let leftCenter = block.left + 12.0
        let topCenter = block.top + 12.0
        #expect(abs(hypot(leftCenter - inTopCorner.startX, topCenter - 8.0) - 10.5) < 0.02)

        // Stacked front + peek blocks
        let front = TimetableNowBarBlock(left: 1.5, top: 2.0, right: 39.5, bottom: 58.0, cornerRadius: 12.0, isStackedFront: true)
        let peek = TimetableNowBarBlock(left: 11.5, top: 5.0, right: 45.0, bottom: 55.0, cornerRadius: 12.0, isStackedFront: false)
        let inPeekCorner = TimetableLayout.calcNowBarSpan(defaultStartX: 3.0, defaultEndX: 43.5, y: 9.0, edgeInset: 1.5, blocks: [front, peek])
        #expect(inPeekCorner.startX > 3.0)
        #expect(inPeekCorner.endX < 43.5)
        #expect(abs(hypot((peek.right - 12.0) - inPeekCorner.endX, (peek.top + 12.0) - 9.0) - 10.5) < 0.02)
    }

    @Test("TaskLogic.isOverdue excludes time-blocked tasks and clearTimeIfPast clears past time on Today")
    func overdueExcludesTimeBlocksAndClearTimeIfPast() {
        let now = LocalDateTime(year: 2026, month: 9, day: 10, hour: 14, minute: 0)
        let yesterdayMorning = LocalDateTime(year: 2026, month: 9, day: 9, hour: 9, minute: 0)

        let normalOverdue = GokeTask(id: "t1", title: "交作业", due: yesterdayMorning, dueHasTime: true)
        let blockedTask = GokeTask(
            id: "t2",
            title: "自习块",
            due: yesterdayMorning,
            dueHasTime: true,
            blockStart: yesterdayMorning,
            blockMinutes: 60
        )
        #expect(TaskLogic.isOverdue(normalOverdue, now: now) == true)
        #expect(TaskLogic.isOverdue(blockedTask, now: now) == false)

        // Rescheduling yesterday 09:00 to today at 14:00 clears past time -> 23:59 untimed
        let todayRes = TaskLogic.rescheduled(normalOverdue, to: now.date, nowMillis: 1000, clearTimeIfPast: now)
        #expect(todayRes.clearedPastTime == true)
        #expect(todayRes.task.dueHasTime == false)
        #expect(todayRes.task.due == now.date.atTime(hour: 23, minute: 59))
        #expect(TaskLogic.isOverdue(todayRes.task, now: now) == false)

        // Rescheduling yesterday 09:00 to tomorrow preserves 09:00
        let tomorrow = now.date.addingDays(1)
        let tomorrowRes = TaskLogic.rescheduled(normalOverdue, to: tomorrow, nowMillis: 1000, clearTimeIfPast: now)
        #expect(tomorrowRes.clearedPastTime == false)
        #expect(tomorrowRes.task.dueHasTime == true)
        #expect(tomorrowRes.task.due == tomorrow.atTime(hour: 9, minute: 0))
    }

    @Test("NextUps.find scopes course tasks to concrete occurrence via TaskLogic.forOccurrence")
    func nextUpUsesOccurrenceScoping() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let course = Course(
            id: "c1",
            semesterId: "s1",
            name: "操作系统",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2, 3]
        )
        let week1Task = GokeTask(id: "t-w1", title: "第1周预习", courseId: "c1", extra: ["courseWeek": "1"])
        let week3Task = GokeTask(id: "t-w3", title: "第3周实验", courseId: "c1", extra: ["courseWeek": "3"])
        let snap = AppSnapshot(semesters: [sem], courses: [course], tasks: [week1Task, week3Task])

        let now = LocalDateTime(year: 2026, month: 8, day: 31, hour: 8, minute: 0) // Week 1 Monday before class
        let nextUp = NextUps.find(snapshot: snap, now: now)
        #expect(nextUp != nil)
        #expect(nextUp?.occurrence.week == 1)
        #expect(nextUp?.tasks.map(\.id) == ["t-w1"])
    }

    @Test("TodoSearch parses #list @tag !priority and matches parent via subtask title")
    func todoSearchStructuredAndSubtasks() {
        let q = TodoSearch.parse("#学习 @实验 !高 报告")
        #expect(q.lists == ["学习"])
        #expect(q.tags == ["实验"])
        #expect(q.priority == 3)
        #expect(q.words == ["报告"])

        let parent = GokeTask(id: "p1", title: "操作系统作业", tags: ["实验一"], priority: 3)
        let sub = GokeTask(id: "s1", title: "撰写实验报告", parentId: "p1")
        #expect(TodoSearch.matches(query: q, task: parent, subtasks: [sub], listName: "专业学习") == true)
        #expect(TodoSearch.matches(query: q, task: parent, subtasks: [], listName: "专业学习") == false)
        #expect(TodoSearch.matches(query: q, task: parent, subtasks: [sub], listName: "生活") == false)
    }

    @Test("CourseExpander.promoteExamOccurrences merges in-class exam over regular course and preserves custom clock times")
    func courseExpanderPromotesExamAndCustomClocks() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let regular = Course(
            id: "jxfw-course/202601/1/1-2/操作系统",
            semesterId: "s1",
            name: "操作系统",
            room: "教1-101",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 16]
        )
        let exam = Course(
            id: "jxfw-exam-course/202601/EX1",
            semesterId: "s1",
            name: "考试：操作系统",
            room: "",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [16],
            kind: .EXAM,
            remindPersistent: true,
            startTime: "09:00",
            endTime: "11:00"
        )

        // Week 1: regular class unaffected
        let w1 = CourseExpander.occurrencesInWeek(semester: sem, courses: [regular, exam], exceptions: [], week: 1)
        #expect(w1.count == 1)
        #expect(!w1[0].course.isExam)
        #expect(w1[0].start.time == LocalTime(hour: 8, minute: 30))

        // Week 16: exam promotes/replaces overlapping regular class, keeps 09:00-11:00, and inherits room "教1-101"
        let w16 = CourseExpander.occurrencesInWeek(semester: sem, courses: [regular, exam], exceptions: [], week: 16)
        #expect(w16.count == 1)
        #expect(w16[0].course.isExam)
        #expect(w16[0].room == "教1-101")
        #expect(w16[0].start.time == LocalTime(hour: 9, minute: 0))
        #expect(w16[0].end.time == LocalTime(hour: 11, minute: 0))
    }

    @Test("TimetableLayout assigns startPeriod-first lanes, unified parcel keys, and full connected overlap clusters")
    func timetableLayoutClusterStackingAndParcelKeys() throws {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let mondayDate = LocalDate(year: 2026, month: 8, day: 31)

        // Connected chain: Event B (P1-2) <- Task C (P2-5) -> Course A (P5-6)
        let courseA = Course(id: "cA", semesterId: "s1", name: "高数", dayOfWeek: 1, startPeriod: 5, endPeriod: 6, weeks: [1])
        let eventB = Event(
            id: "eB",
            title: "早会",
            start: mondayDate.atTime(hour: 8, minute: 30),
            end: mondayDate.atTime(hour: 10, minute: 5)
        )
        let taskC = GokeTask(
            id: "tC",
            title: "跨节实验",
            blockStart: mondayDate.atTime(hour: 9, minute: 30),
            blockMinutes: 170 // 09:30 -> 12:20 -> covers P2..P4; make it bridge to P5 via 13:50
        )
        // Use Tuesday for 4-item simultaneous overlap at P1-2
        let t1 = Course(id: "t1", semesterId: "s1", name: "课程1", dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1])
        let t2 = Course(id: "t2", semesterId: "s1", name: "课程2", dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1])
        let t3 = Course(id: "t3", semesterId: "s1", name: "课程3", dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1])
        let t4 = Course(id: "t4", semesterId: "s1", name: "课程4", dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1])

        // Parcel key checks
        let realParcel = GokeTask(
            id: "p-real",
            title: "取件码 3-2-1001",
            due: mondayDate.atTime(hour: 18, minute: 0),
            source: .MANUAL,
            extra: ["code": "3-2-1001", "place": "菜鸟驿站", "company": "顺丰"]
        )
        let taggedNonParcel = GokeTask(
            id: "t-tagged",
            title: "去取件顺路买水",
            tags: ["取件"],
            blockStart: mondayDate.atTime(hour: 19, minute: 0),
            blockMinutes: 45
        )
        #expect(TimetableLayout.isParcelTask(realParcel))
        #expect(TimetableLayout.parcelCode(of: realParcel) == "3-2-1001")
        #expect(TimetableLayout.parcelPlace(of: realParcel) == "菜鸟驿站")
        #expect(TimetableLayout.parcelCompany(of: realParcel) == "顺丰")
        #expect(!TimetableLayout.isParcelTask(taggedNonParcel))

        // Bridging task covering P2..P5 on Monday (start 09:25..14:00 = 275min > 180min folds, so use 2 courses + event)
        let bridgeCourse = Course(id: "cBridge", semesterId: "s1", name: "长实验", dayOfWeek: 1, startPeriod: 2, endPeriod: 5, weeks: [1])
        let snap = AppSnapshot(
            semesters: [sem],
            courses: [courseA, bridgeCourse, t1, t2, t3, t4],
            events: [eventB],
            tasks: [realParcel, taggedNonParcel, taskC]
        )
        let layout = try #require(TimetableLayout.buildWeek(snapshot: snap, week: 1))

        // Monday: Event B (P1-2), bridgeCourse (P2-5), courseA (P5-6) form 1 connected cluster of size >= 3
        let monday = layout.columns[0]
        #expect(monday.dayParcels.map(\.id) == ["p-real"])
        let slotB = try #require(monday.slots.first { $0.id.hasPrefix("event:eB") })
        let slotA = try #require(monday.slots.first { $0.id.hasPrefix("course:cA") })
        // Event B starts at P1 (disjoint from Course A at P5-6), so Event B gets laneIndex == 0 (never stranded in a back lane!)
        #expect(slotB.laneIndex == 0)
        #expect(slotA.clusterId == slotB.clusterId)
        #expect(monday.cluster(for: slotA).contains(where: { $0.id == slotB.id }))

        // Tuesday: 4 overlapping courses share 1 cluster, max 2 back peeks, 4th tucked (stackDepth == nil) yet reachable via cluster(for:)
        let tuesday = layout.columns[1]
        #expect(tuesday.slots.count == 4)
        let front = try #require(tuesday.slots.first(where: { $0.isStackFront }))
        #expect(front.stackDepth == 0)
        #expect(front.clusterSize == 4)
        #expect(front.clusterMaxDepth == 2)
        let tucked = tuesday.slots.filter { $0.stackDepth == nil }
        #expect(tucked.count == 1)
        #expect(tuesday.cluster(for: front).count == 4)
    }

    @Test("AgendaEngine expands repeating blockStart, includes startDate-only tasks, and surfaces dueBeforeNextClass overdue")
    func agendaRepeatingBlockStartDateAndDueBeforeNextClassOverdue() {
        let sem = Semester(id: "s1", name: "Fall", week1Monday: LocalDate(year: 2026, month: 8, day: 31))
        let course = Course(
            id: "c-os",
            semesterId: "s1",
            name: "操作系统",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: [1, 2]
        )
        let week1Mon = LocalDate(year: 2026, month: 8, day: 31)
        let week2Mon = LocalDate(year: 2026, month: 9, day: 7)

        let repeatingBlock = GokeTask(
            id: "t-rep-block",
            title: "每周复盘",
            repeat: Recurrence(freq: .WEEKLY, interval: 1),
            blockStart: week1Mon.atTime(hour: 15, minute: 0),
            blockMinutes: 60
        )
        let startOnlyTask = GokeTask(
            id: "t-start-only",
            title: "今天整理的笔记",
            startDate: week2Mon
        )
        let overdueBeforeClass = GokeTask(
            id: "t-before-class",
            title: "预习第一章",
            courseId: "c-os",
            dueBeforeNextClass: true,
            extra: ["courseWeek": "1"]
        )

        let snap = AppSnapshot(
            semesters: [sem],
            courses: [course],
            tasks: [repeatingBlock, startOnlyTask, overdueBeforeClass]
        )

        // Repeating blockStart appears in Week 2 Monday agenda, and startDate-only task appears on Week 2 Monday!
        let week2Items = AgendaEngine.forDay(snapshot: snap, date: week2Mon)
        let hasRepeatingBlock = week2Items.contains {
            if case .block(let t, let start, _) = $0 {
                return t.id == "t-rep-block" && start.date == week2Mon
            }
            return false
        }
        let hasStartOnly = week2Items.contains {
            if case .task(let t, _, let hasTime) = $0 {
                return t.id == "t-start-only" && !hasTime
            }
            return false
        }
        #expect(hasRepeatingBlock)
        #expect(hasStartOnly)

        // After Week 1 Monday 08:30 (e.g. 09:00), dueBeforeNextClass task pinned to week 1 is overdue!
        let afterClassStart = week1Mon.atTime(hour: 9, minute: 0)
        #expect(TaskLogic.isOverdue(overdueBeforeClass, semester: sem, courses: [course], exceptions: [], now: afterClassStart))
        let overdueList = TaskLogic.filter(
            [overdueBeforeClass],
            smartList: .OVERDUE,
            semester: sem,
            courses: [course],
            exceptions: [],
            now: afterClassStart
        )
        #expect(overdueList.map(\.id) == ["t-before-class"])
        let todayList = TaskLogic.filter(
            [overdueBeforeClass],
            smartList: .TODAY,
            semester: sem,
            courses: [course],
            exceptions: [],
            now: week2Mon.atTime(hour: 10, minute: 0)
        )
        #expect(todayList.map(\.id) == ["t-before-class"])
    }

    @Test("CourseExpander single-day pruning includes cross-week reschedules and matches nonEmptyWeeks")
    func courseExpanderPruningAndNonEmptyWeeksParity() {
        let week1Mon = LocalDate(year: 2026, month: 8, day: 31)
        let sem = Semester(id: "s-perf", name: "2026秋", week1Monday: week1Mon, totalWeeks: 20)
        let course = Course(
            id: "c-math",
            semesterId: sem.id,
            name: "高等数学",
            dayOfWeek: 1,
            startPeriod: 1,
            endPeriod: 2,
            weeks: Set([1, 2, 3, 5])
        )
        let cancelW2 = CourseException(id: "ex-c", courseId: course.id, week: 2, type: .CANCEL)
        let moveW5ToW10 = CourseException(
            id: "ex-m",
            courseId: course.id,
            week: 5,
            type: .RESCHEDULE,
            newWeek: 10,
            newDayOfWeek: 3,
            newStartPeriod: 3,
            newEndPeriod: 4
        )
        let snap = AppSnapshot(semesters: [sem], courses: [course], exceptions: [cancelW2, moveW5ToW10])

        let nonEmpty = TimetableLayout.nonEmptyWeeks(snapshot: snap, semester: sem)
        #expect(nonEmpty == Set([1, 3, 10]))

        let cal = SemesterCalendar(semester: sem)
        let w10Wed = cal.dateOf(week: 10, dayOfWeek: 3)
        let singleDay = CourseExpander.occurrences(
            semester: sem,
            courses: snap.courses,
            exceptions: snap.exceptions,
            from: w10Wed,
            to: w10Wed
        )
        #expect(singleDay.count == 1)
        #expect(singleDay[0].week == 10)
        #expect(singleDay[0].originalWeek == 5)
        #expect(singleDay[0].rescheduled == true)

        let w2Mon = cal.dateOf(week: 2, dayOfWeek: 1)
        #expect(CourseExpander.occurrences(semester: sem, courses: snap.courses, exceptions: snap.exceptions, from: w2Mon, to: w2Mon).isEmpty)

        // AgendaEngine.marks(from:) matches marks(snapshot:from:to:)
        let from = week1Mon
        let to = cal.weekEnd(10)
        let agenda = AgendaEngine.range(snapshot: snap, from: from, to: to)
        #expect(AgendaEngine.marks(from: agenda) == AgendaEngine.marks(snapshot: snap, from: from, to: to))
    }

    @Test("LocalDateTime.fromDate matches Calendar across timezones and pre-1970 dates")
    func localDateTimeFromDateAccuracy() {
        let tzShanghai = TimeZone(identifier: "Asia/Shanghai")!
        let tzNewYork = TimeZone(identifier: "America/New_York")!
        let samples: [Date] = [
            Date(timeIntervalSince1970: 0),
            Date(timeIntervalSince1970: -0.5),
            Date(timeIntervalSince1970: -86_401),
            Date(timeIntervalSince1970: 1_788_134_400), // 2026-08-31T00:00:00Z (DST in NY)
            Date(timeIntervalSince1970: 1_767_225_600), // 2026-01-01T00:00:00Z (EST in NY)
        ]
        for tz in [tzShanghai, tzNewYork, TimeZone(secondsFromGMT: 0)!] {
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = tz
            for instant in samples {
                let fast = LocalDateTime.fromDate(instant, in: tz)
                let c = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: instant)
                #expect(fast.year == c.year)
                #expect(fast.month == c.month)
                #expect(fast.day == c.day)
                #expect(fast.hour == c.hour)
                #expect(fast.minute == c.minute)
                #expect(fast.second == c.second)
            }
        }
    }
}
