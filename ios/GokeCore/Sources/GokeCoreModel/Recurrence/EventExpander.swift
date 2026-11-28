import Foundation

/// A concrete occurrence of a course in a specific week/day.
public struct CourseOccurrence: Sendable, Hashable, Identifiable {
    public var id: String { "\(course.id)#\(originalWeek)#\(week)#\(startPeriod)" }
    public let course: Course
    public let week: Int
    public let date: LocalDate
    public let startPeriod: Int
    public let endPeriod: Int
    public let start: LocalDateTime
    public let end: LocalDateTime
    public let room: String
    public let rescheduled: Bool
    public let originalWeek: Int

    public init(
        course: Course,
        week: Int,
        date: LocalDate,
        startPeriod: Int,
        endPeriod: Int,
        start: LocalDateTime,
        end: LocalDateTime,
        room: String,
        rescheduled: Bool,
        originalWeek: Int? = nil
    ) {
        self.course = course
        self.week = week
        self.date = date
        self.startPeriod = startPeriod
        self.endPeriod = endPeriod
        self.start = start
        self.end = end
        self.room = room
        self.rescheduled = rescheduled
        self.originalWeek = originalWeek ?? week
    }
}

public enum CourseExpander {
    internal struct ExceptionKey: Hashable {
        let courseId: String
        let week: Int
    }

    internal static func expandOne(
        cal: SemesterCalendar,
        course c: Course,
        week w: Int,
        exception ex: CourseException?
    ) -> CourseOccurrence? {
        if ex?.type == .CANCEL { return nil }
        let isRescheduled = ex?.type == .RESCHEDULE
        let week = isRescheduled ? (ex?.newWeek ?? w) : w
        let dow = isRescheduled ? (ex?.newDayOfWeek ?? c.dayOfWeek) : c.dayOfWeek
        let sp = isRescheduled ? (ex?.newStartPeriod ?? c.startPeriod) : c.startPeriod
        let ep = isRescheduled ? (ex?.newEndPeriod ?? c.endPeriod) : c.endPeriod
        let date = cal.dateOf(week: week, dayOfWeek: dow)
        let useCustomClock = !isRescheduled || (ex?.newStartPeriod == nil && ex?.newEndPeriod == nil)
        let startClock = (useCustomClock ? c.startLocalTime : nil) ?? cal.startTime(period: sp)
        let endClock = (useCustomClock ? c.endLocalTime : nil) ?? cal.endTime(period: ep)
        guard let st = startClock, let et = endClock else { return nil }
        return CourseOccurrence(
            course: c,
            week: week,
            date: date,
            startPeriod: sp,
            endPeriod: ep,
            start: date.atTime(st),
            end: date.atTime(et),
            room: (isRescheduled ? ex?.newRoom : nil) ?? c.room,
            rescheduled: isRescheduled,
            originalWeek: w
        )
    }

    /// Promotes overlapping same-course regular occurrences into the exam occurrence on the same date,
    /// eliminating fake conflict stacks for in-class exams and inheriting room when the exam room is blank.
    /// Matches Android `CourseExpander.promoteExamOccurrences`.
    internal static func promoteExamOccurrences(_ out: [CourseOccurrence]) -> [CourseOccurrence] {
        let exams = out.filter { $0.course.isExam }
        guard !exams.isEmpty else { return out }
        var examRooms = exams.map(\.room)
        var suppressedIndices = Set<Int>()

        for (idx, occ) in out.enumerated() where !occ.course.isExam {
            let examIdx = exams.firstIndex { e in
                e.date == occ.date
                    && ExamTextHelper.isSameCourseForExam(e.course.name, occ.course.name)
                    && ((occ.start < e.end && e.start < occ.end)
                        || (occ.startPeriod <= e.endPeriod && e.startPeriod <= occ.endPeriod))
            }
            if let examIdx {
                suppressedIndices.insert(idx)
                if examRooms[examIdx].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                   !occ.room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                {
                    examRooms[examIdx] = occ.room
                }
            }
        }

        guard !suppressedIndices.isEmpty else { return out }
        var nextExamIdx = 0
        var result: [CourseOccurrence] = []
        result.reserveCapacity(out.count - suppressedIndices.count)
        for (idx, occ) in out.enumerated() {
            if suppressedIndices.contains(idx) { continue }
            if occ.course.isExam {
                let inheritedRoom = examRooms[nextExamIdx]
                nextExamIdx += 1
                if inheritedRoom != occ.room {
                    result.append(
                        CourseOccurrence(
                            course: occ.course,
                            week: occ.week,
                            date: occ.date,
                            startPeriod: occ.startPeriod,
                            endPeriod: occ.endPeriod,
                            start: occ.start,
                            end: occ.end,
                            room: inheritedRoom,
                            rescheduled: occ.rescheduled,
                            originalWeek: occ.originalWeek
                        )
                    )
                } else {
                    result.append(occ)
                }
            } else {
                result.append(occ)
            }
        }
        return result
    }

    /// Expands all live courses in `semester` within `[from, to]` (inclusive), applying exceptions.
    public static func occurrences(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException],
        from: LocalDate,
        to: LocalDate
    ) -> [CourseOccurrence] {
        guard from <= to else { return [] }
        let cal = SemesterCalendar(semester: semester)
        let minWeek = cal.weekOf(from)
        let maxWeek = cal.weekOf(to)
        var exByKey: [ExceptionKey: CourseException] = [:]
        var rescheduledOutsideByCourse: [String: [Int]] = [:]
        if !exceptions.isEmpty {
            exByKey.reserveCapacity(exceptions.count)
            for ex in exceptions where ex.deletedAt == nil {
                exByKey[ExceptionKey(courseId: ex.courseId, week: ex.week)] = ex
            }
            for ex in exByKey.values where ex.type == .RESCHEDULE && (ex.week < minWeek || ex.week > maxWeek) {
                rescheduledOutsideByCourse[ex.courseId, default: []].append(ex.week)
            }
        }
        let weekSpan = maxWeek - minWeek + 1
        var out: [CourseOccurrence] = []
        for c in courses where c.deletedAt == nil && c.semesterId == semester.id {
            if weekSpan <= c.weeks.count {
                for w in minWeek...maxWeek where c.weeks.contains(w) {
                    let ex = exByKey.isEmpty ? nil : exByKey[ExceptionKey(courseId: c.id, week: w)]
                    guard let occ = expandOne(cal: cal, course: c, week: w, exception: ex) else { continue }
                    if occ.date < from || occ.date > to { continue }
                    out.append(occ)
                }
                if let extraWeeks = rescheduledOutsideByCourse[c.id] {
                    for w in extraWeeks where c.weeks.contains(w) {
                        let ex = exByKey[ExceptionKey(courseId: c.id, week: w)]
                        guard let occ = expandOne(cal: cal, course: c, week: w, exception: ex) else { continue }
                        if occ.date < from || occ.date > to { continue }
                        out.append(occ)
                    }
                }
            } else {
                for w in c.weeks {
                    let inRange = w >= minWeek && w <= maxWeek
                    let ex = exByKey.isEmpty ? nil : exByKey[ExceptionKey(courseId: c.id, week: w)]
                    if !inRange && ex?.type != .RESCHEDULE { continue }
                    guard let occ = expandOne(cal: cal, course: c, week: w, exception: ex) else { continue }
                    if occ.date < from || occ.date > to { continue }
                    out.append(occ)
                }
            }
        }
        return promoteExamOccurrences(out).sorted { lhs, rhs in
            if lhs.start != rhs.start { return lhs.start < rhs.start }
            return lhs.course.name < rhs.course.name
        }
    }

    /// Expands a single concrete session for `courseId` at `originalWeek` (returns `nil` if cancelled or not scheduled).
    public static func occurrence(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException],
        courseId: String,
        originalWeek: Int
    ) -> CourseOccurrence? {
        guard let c = courses.first(where: {
            $0.id == courseId && $0.deletedAt == nil && $0.semesterId == semester.id && $0.weeks.contains(originalWeek)
        }) else {
            return nil
        }
        let ex = exceptions.last { $0.deletedAt == nil && $0.courseId == c.id && $0.week == originalWeek }
        return expandOne(cal: SemesterCalendar(semester: semester), course: c, week: originalWeek, exception: ex)
    }

    public static func occurrencesInWeek(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException],
        week: Int
    ) -> [CourseOccurrence] {
        let cal = SemesterCalendar(semester: semester)
        return occurrences(
            semester: semester,
            courses: courses,
            exceptions: exceptions,
            from: cal.weekStart(week),
            to: cal.weekEnd(week)
        )
    }

    /// Finds the next occurrence strictly after `after`, merging multi-slot courses with the same name in the same semester.
    public static func nextOccurrence(
        semester: Semester,
        courses: [Course],
        exceptions: [CourseException],
        courseId: String,
        after: LocalDateTime
    ) -> CourseOccurrence? {
        let cal = SemesterCalendar(semester: semester)
        let anchor = courses.first { $0.id == courseId && $0.deletedAt == nil }
        let matching = courses.filter { c in
            if c.id == courseId { return true }
            guard let anchor else { return false }
            return c.deletedAt == nil
                && c.semesterId == anchor.semesterId
                && c.name.trimmingCharacters(in: .whitespacesAndNewlines)
                    == anchor.name.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let list = occurrences(
            semester: semester,
            courses: matching,
            exceptions: exceptions,
            from: after.date,
            to: cal.weekEnd(cal.totalWeeks)
        )
        return list.first { $0.start > after }
    }
}

/// A concrete occurrence of an `Event`.
public struct EventOccurrence: Sendable, Hashable, Identifiable {
    public var id: String { "\(event.id)#\(start.description)" }
    public let event: Event
    public let start: LocalDateTime
    public let end: LocalDateTime

    public var date: LocalDate { start.date }

    public init(event: Event, start: LocalDateTime, end: LocalDateTime) {
        self.event = event
        self.start = start
        self.end = end
    }
}

public enum EventExpander {
    public static func occurrences(
        event: Event,
        from: LocalDate,
        to: LocalDate
    ) -> [EventOccurrence] {
        guard event.deletedAt == nil else { return [] }
        let rawSeconds = event.start.secondsUntil(event.end)
        let durationSeconds = max(Int64(0), rawSeconds)
        let starts: [LocalDateTime]
        if let rule = event.recurrence {
            let backDays = Int(durationSeconds / 86_400) + 1
            starts = RecurrenceEngine
                .dates(rule: rule, anchor: event.start.date, from: from.addingDays(-backDays), to: to)
                .map { LocalDateTime(date: $0, time: event.start.time) }
        } else {
            starts = [event.start]
        }
        return starts
            .map { EventOccurrence(event: event, start: $0, end: $0.addingSeconds(durationSeconds)) }
            .filter { $0.end.date >= from && $0.start.date <= to }
            .filter { occ in
                occ.end.date != from || occ.end.time != .midnight || occ.start.date == from || event.allDay
            }
            .sorted { $0.start < $1.start }
    }

    public static func occurrences(
        events: [Event],
        from: LocalDate,
        to: LocalDate
    ) -> [EventOccurrence] {
        events
            .flatMap { occurrences(event: $0, from: from, to: to) }
            .sorted { lhs, rhs in
                if lhs.start != rhs.start { return lhs.start < rhs.start }
                return lhs.event.title < rhs.event.title
            }
    }
}
