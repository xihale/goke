import Foundation

public enum SmartList: String, Sendable, Hashable, CaseIterable {
    case TODAY
    case PLANNED
    case ALL
    case DONE
    case OVERDUE
}

/// Ordered sections of the unified todo timeline.
public enum TaskBucket: String, Sendable, Hashable, CaseIterable {
    case OVERDUE
    case TODAY
    case TOMORROW
    case THIS_WEEK
    case LATER
    case INBOX
    case DONE
}

public struct TimelineSection: Sendable, Hashable, Identifiable {
    public var id: TaskBucket { bucket }
    public let bucket: TaskBucket
    public let tasks: [GokeTask]

    public init(bucket: TaskBucket, tasks: [GokeTask]) {
        self.bucket = bucket
        self.tasks = tasks
    }
}

/// Pure task filtering, bucketing, completion/recurrence, and effective-due logic matching Android `TaskLogic`.
public enum TaskLogic {
    /// Sentinel returned by `dropDate` when a bucket cannot accept drops.
    public static let noDrop: LocalDate = .min

    public static func topLevel(_ tasks: [GokeTask]) -> [GokeTask] {
        tasks.filter { $0.deletedAt == nil && $0.parentId == nil }
    }

    public static func subtasks(_ tasks: [GokeTask], parentId: String) -> [GokeTask] {
        tasks
            .filter { $0.deletedAt == nil && $0.parentId == parentId }
            .sorted { $0.sortOrder < $1.sortOrder }
    }

    public static func progress(_ tasks: [GokeTask], parentId: String) -> (done: Int, total: Int) {
        let subs = subtasks(tasks, parentId: parentId)
        return (subs.filter(\.done).count, subs.count)
    }

    public static func dueDate(_ task: GokeTask) -> LocalDate? {
        task.due?.date
    }

    public static func isOverdue(_ task: GokeTask, due: LocalDateTime?, now: LocalDateTime) -> Bool {
        guard !task.done, task.blockStart == nil, let due else { return false }
        let hasTime = task.dueHasTime || task.dueBeforeNextClass
        return hasTime ? due < now : due.date < now.date
    }

    public static func isOverdue(_ task: GokeTask, now: LocalDateTime) -> Bool {
        isOverdue(task, due: task.due, now: now)
    }

    public static func isOverdue(
        _ task: GokeTask,
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> Bool {
        let resolvedDue = effectiveDue(
            task: task,
            semester: semester,
            courses: courses,
            exceptions: exceptions,
            now: now
        )
        return isOverdue(task, due: resolvedDue, now: now)
    }

    /// Resolves the target due timestamp when rescheduling a task to `targetDate`.
    /// When `clearTimeIfPast` is provided (e.g. moving to Today) and the preserved time-of-day
    /// would already be in the past, clears `dueHasTime` to an untimed `23:59` due date so the
    /// task does not stay stuck in Overdue. Matches Android `Edits.reschedule`.
    public static func clearTimeIfPast(
        task: GokeTask,
        targetDate: LocalDate,
        now: LocalDateTime?
    ) -> (due: LocalDateTime, dueHasTime: Bool, clearedPastTime: Bool) {
        let origTime: LocalTime? = task.dueHasTime ? task.due?.time : nil
        let dropPastTime: Bool = {
            guard let origTime, let now else { return false }
            return targetDate.atTime(origTime) < now
        }()
        let resolvedTime = (origTime != nil && !dropPastTime) ? origTime! : LocalTime(hour: 23, minute: 59)
        return (
            due: targetDate.atTime(resolvedTime),
            dueHasTime: task.dueHasTime && !dropPastTime,
            clearedPastTime: dropPastTime
        )
    }

    /// Returns a rescheduled copy of `task` matching Android `Edits.reschedule`.
    /// Passing `date == nil` clears scheduling back to Inbox.
    public static func rescheduled(
        _ task: GokeTask,
        to date: LocalDate?,
        nowMillis: Int64,
        clearTimeIfPast: LocalDateTime? = nil
    ) -> (task: GokeTask, clearedPastTime: Bool) {
        var updated = task
        updated.startDate = nil
        updated.blockStart = nil
        updated.dueBeforeNextClass = false
        updated.updatedAt = nowMillis
        guard let date else {
            updated.due = nil
            updated.dueHasTime = false
            return (updated, false)
        }
        let resolved = Self.clearTimeIfPast(task: task, targetDate: date, now: clearTimeIfPast)
        updated.due = resolved.due
        updated.dueHasTime = resolved.dueHasTime
        return (updated, resolved.clearedPastTime)
    }

    public static func filter(
        _ tasks: [GokeTask],
        smartList: SmartList,
        now: LocalDateTime,
        dueOf: (GokeTask) -> LocalDateTime? = { $0.due }
    ) -> [GokeTask] {
        let today = now.date
        let top = topLevel(tasks).map { (task: $0, due: dueOf($0)) }
        let list: [(task: GokeTask, due: LocalDateTime?)]
        switch smartList {
        case .TODAY:
            list = top.filter {
                (!$0.task.done && isOverdue($0.task, due: $0.due, now: now))
                    || $0.due?.date == today
                    || $0.task.startDate == today
                    || $0.task.blockStart?.date == today
            }
        case .PLANNED:
            let nextWeek = today.addingDays(7)
            list = top.filter {
                guard !$0.task.done, let d = $0.due?.date else { return false }
                return d > today && d <= nextWeek
            }
        case .ALL:
            list = top.filter { !$0.task.done }
        case .DONE:
            list = top.filter(\.task.done)
        case .OVERDUE:
            list = top.filter { isOverdue($0.task, due: $0.due, now: now) }
        }
        return list
            .sorted { lhs, rhs in
                if lhs.task.done != rhs.task.done { return !lhs.task.done }
                if (lhs.due == nil) != (rhs.due == nil) { return lhs.due != nil }
                if let ld = lhs.due, let rd = rhs.due, ld != rd { return ld < rd }
                if lhs.task.priority != rhs.task.priority { return lhs.task.priority > rhs.task.priority }
                return lhs.task.sortOrder < rhs.task.sortOrder
            }
            .map(\.task)
    }

    /// Smart-list filter that resolves `dueBeforeNextClass` deadlines against the active semester timetable.
    public static func filter(
        _ tasks: [GokeTask],
        smartList: SmartList,
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> [GokeTask] {
        filter(tasks, smartList: smartList, now: now) { task in
            effectiveDue(
                task: task,
                semester: semester,
                courses: courses,
                exceptions: exceptions,
                now: now
            )
        }
    }

    public static func bucketOf(task: GokeTask, due: LocalDateTime?, now: LocalDateTime) -> TaskBucket {
        if task.done { return .DONE }
        let today = now.date
        if let due {
            let hasTime = task.dueHasTime || task.dueBeforeNextClass
            let date = due.date
            if hasTime ? (due < now) : (date < today) { return .OVERDUE }
            if date == today { return .TODAY }
            if date == today.addingDays(1) { return .TOMORROW }
            if date <= today.endOfWeekSunday { return .THIS_WEEK }
            return .LATER
        }
        if let start = task.startDate ?? task.blockStart?.date {
            if start <= today { return .TODAY }
            if start == today.addingDays(1) { return .TOMORROW }
            if start <= today.endOfWeekSunday { return .THIS_WEEK }
            return .LATER
        }
        return .INBOX
    }

    public static func timeline(
        _ tasks: [GokeTask],
        now: LocalDateTime,
        dueOf: (GokeTask) -> LocalDateTime? = { $0.due }
    ) -> [TimelineSection] {
        var groups: [TaskBucket: [(task: GokeTask, due: LocalDateTime?)]] = [:]
        for task in topLevel(tasks) {
            let resolvedDue = dueOf(task)
            let bucket = bucketOf(task: task, due: resolvedDue, now: now)
            groups[bucket, default: []].append((task: task, due: resolvedDue))
        }
        return TaskBucket.allCases.compactMap { bucket in
            guard let list = groups[bucket], !list.isEmpty else { return nil }
            let sorted: [GokeTask]
            if bucket == .DONE {
                sorted = list
                    .sorted { ($0.task.doneAt ?? 0) > ($1.task.doneAt ?? 0) }
                    .map(\.task)
            } else {
                sorted = list
                    .sorted { lhs, rhs in
                        let ld = lhs.due
                        let rd = rhs.due
                        if (ld == nil) != (rd == nil) { return ld != nil }
                        if let ld, let rd, ld != rd { return ld < rd }
                        if lhs.task.priority != rhs.task.priority { return lhs.task.priority > rhs.task.priority }
                        return lhs.task.sortOrder < rhs.task.sortOrder
                    }
                    .map(\.task)
            }
            return TimelineSection(bucket: bucket, tasks: sorted)
        }
    }

    public static func dropDate(bucket: TaskBucket, today: LocalDate) -> LocalDate? {
        switch bucket {
        case .TODAY: return today
        case .TOMORROW: return today.addingDays(1)
        case .THIS_WEEK:
            let end = today.endOfWeekSunday
            return end >= today.addingDays(2) ? end : noDrop
        case .LATER: return today.addingDays(7)
        case .INBOX: return nil
        case .OVERDUE, .DONE: return noDrop
        }
    }

    /// Marks `task` done and, if repeating, spawns the next occurrence instance.
    public static func complete(
        _ task: GokeTask,
        nowMillis: Int64,
        now: LocalDateTime,
        nextId: () -> String = { UUID().uuidString.lowercased() }
    ) -> (finished: GokeTask, next: GokeTask?) {
        var finished = task
        finished.done = true
        finished.doneAt = nowMillis
        finished.updatedAt = nowMillis
        guard let rule = task.`repeat` else {
            return (finished, nil)
        }
        let anchorDate = task.due?.date ?? task.startDate ?? now.date
        var unboundedRule = rule
        unboundedRule.count = nil
        let afterDate = max(anchorDate, now.date)
        guard let nextDate = RecurrenceEngine.next(rule: unboundedRule, anchor: anchorDate, after: afterDate) else {
            return (finished, nil)
        }
        let shift = anchorDate.daysUntil(nextDate)
        var nextTask = task
        nextTask.id = nextId()
        nextTask.done = false
        nextTask.doneAt = nil
        nextTask.due = task.due?.addingDays(shift)
        nextTask.startDate = task.startDate?.addingDays(shift)
        nextTask.blockStart = nil
        nextTask.updatedAt = nowMillis
        if let count = rule.count {
            if count > 1 {
                var updatedRule = rule
                updatedRule.count = count - 1
                nextTask.`repeat` = updatedRule
            } else {
                nextTask.`repeat` = nil
            }
        }
        return (finished, nextTask)
    }

    public static func reopen(_ task: GokeTask, nowMillis: Int64) -> GokeTask {
        var copy = task
        copy.done = false
        copy.doneAt = nil
        copy.dropped = false
        copy.updatedAt = nowMillis
        return copy
    }

    /// Stable hashable key identifying a single concrete class session `(courseId, originalWeek)`.
    public struct CourseOccurrenceKey: Sendable, Hashable {
        public let courseId: String
        public let originalWeek: Int

        public init(courseId: String, originalWeek: Int) {
            self.courseId = courseId
            self.originalWeek = originalWeek
        }
    }

    /// Checks whether `task` is linked to `target` (matching same ID or same-semester same-name slots).
    public static func matchesCourse(
        _ task: GokeTask,
        target: Course,
        courseById: [String: Course]
    ) -> Bool {
        guard let cid = task.courseId else { return false }
        if cid == target.id { return true }
        guard let linked = courseById[cid] else { return false }
        return linked.semesterId == target.semesterId
            && linked.name.trimmingCharacters(in: .whitespacesAndNewlines)
                == target.name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Lists top-level tasks linked to `target` course (excluding completed tasks by default).
    public static func forCourse(
        _ tasks: [GokeTask],
        courses: [Course],
        target: Course,
        includeDone: Bool = false
    ) -> [GokeTask] {
        var byId: [String: Course] = [:]
        for c in courses where c.deletedAt == nil {
            byId[c.id] = c
        }
        return tasks.filter { t in
            t.deletedAt == nil
                && t.parentId == nil
                && (includeDone || !t.done)
                && matchesCourse(t, target: target, courseById: byId)
        }
    }

    /// Resolves the single concrete `CourseOccurrence` that `task` belongs to, or `nil` if unlinked/cancelled.
    public static func targetOccurrence(
        task: GokeTask,
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> CourseOccurrence? {
        guard let cid = task.courseId, let sem = semester else { return nil }
        if let cw = task.courseWeek {
            return CourseExpander.occurrence(
                semester: sem,
                courses: courses,
                exceptions: exceptions,
                courseId: cid,
                originalWeek: cw
            )
        }
        if task.dueBeforeNextClass || (task.due == nil && task.blockStart == nil) {
            return CourseExpander.nextOccurrence(
                semester: sem,
                courses: courses,
                exceptions: exceptions,
                courseId: cid,
                after: now
            )
        }
        guard let anchor = task.blockStart ?? task.due else { return nil }
        let anchorCourse = courses.first { $0.id == cid && $0.deletedAt == nil }
        let matching = courses.filter { c in
            guard c.deletedAt == nil else { return false }
            if c.id == cid { return true }
            guard let anchorCourse else { return false }
            return c.semesterId == anchorCourse.semesterId
                && c.name.trimmingCharacters(in: .whitespacesAndNewlines)
                    == anchorCourse.name.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let anchorDate = anchor.date
        let onDate = CourseExpander.occurrences(
            semester: sem,
            courses: matching,
            exceptions: exceptions,
            from: anchorDate,
            to: anchorDate
        )
        if !onDate.isEmpty {
            let sameSlot = onDate.filter { $0.course.id == cid }
            let pool = sameSlot.isEmpty ? onDate : sameSlot
            return pool.first { $0.end >= anchor } ?? pool.first
        }
        return CourseExpander.nextOccurrence(
            semester: sem,
            courses: courses,
            exceptions: exceptions,
            courseId: cid,
            after: anchor.addingSeconds(-1)
        )
    }

    /// Checks whether `task` belongs to the specific concrete class session `occ`.
    public static func matchesOccurrence(
        task: GokeTask,
        occurrence occ: CourseOccurrence,
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> Bool {
        guard let cid = task.courseId else { return false }
        if let cw = task.courseWeek {
            return cid == occ.course.id && cw == occ.originalWeek
        }
        guard let target = targetOccurrence(
            task: task,
            semester: semester,
            courses: courses,
            exceptions: exceptions,
            now: now
        ) else {
            return false
        }
        return target.course.id == occ.course.id && target.originalWeek == occ.originalWeek
    }

    /// Lists top-level tasks scoped to the concrete class session `occ`.
    public static func forOccurrence(
        _ tasks: [GokeTask],
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        occurrence occ: CourseOccurrence,
        now: LocalDateTime,
        includeDone: Bool = false
    ) -> [GokeTask] {
        tasks.filter { t in
            t.deletedAt == nil
                && t.parentId == nil
                && (includeDone || !t.done)
                && matchesOccurrence(
                    task: t,
                    occurrence: occ,
                    semester: semester,
                    courses: courses,
                    exceptions: exceptions,
                    now: now
                )
        }
    }

    /// Counts unfinished top-level course tasks per concrete class session `(courseId, originalWeek)`.
    public static func occurrenceCounts(
        _ tasks: [GokeTask],
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> [CourseOccurrenceKey: Int] {
        guard let sem = semester else { return [:] }
        let cal = SemesterCalendar(semester: sem)
        var byId: [String: Course] = [:]
        byId.reserveCapacity(courses.count)
        for c in courses where c.deletedAt == nil {
            byId[c.id] = c
        }
        var exByKey: [CourseExpander.ExceptionKey: CourseException] = [:]
        if !exceptions.isEmpty {
            exByKey.reserveCapacity(exceptions.count)
            for ex in exceptions where ex.deletedAt == nil {
                exByKey[CourseExpander.ExceptionKey(courseId: ex.courseId, week: ex.week)] = ex
            }
        }
        var nextCache: [String: CourseOccurrence?] = [:]
        var counts: [CourseOccurrenceKey: Int] = [:]
        for t in tasks {
            guard t.deletedAt == nil, t.parentId == nil, !t.done, let cid = t.courseId else { continue }
            let occ: CourseOccurrence?
            if let cw = t.courseWeek {
                if let c = byId[cid], c.semesterId == sem.id, c.weeks.contains(cw) {
                    let ex = exByKey.isEmpty ? nil : exByKey[CourseExpander.ExceptionKey(courseId: c.id, week: cw)]
                    occ = CourseExpander.expandOne(cal: cal, course: c, week: cw, exception: ex)
                } else {
                    occ = nil
                }
            } else if t.dueBeforeNextClass || (t.due == nil && t.blockStart == nil) {
                let cacheKey: String
                if let c = byId[cid] {
                    cacheKey = "\(c.semesterId)|\(c.name.trimmingCharacters(in: .whitespacesAndNewlines))"
                } else {
                    cacheKey = "id:\(cid)"
                }
                if let cached = nextCache[cacheKey] {
                    occ = cached
                } else {
                    let resolved = CourseExpander.nextOccurrence(
                        semester: sem,
                        courses: courses,
                        exceptions: exceptions,
                        courseId: cid,
                        after: now
                    )
                    nextCache[cacheKey] = resolved
                    occ = resolved
                }
            } else {
                occ = targetOccurrence(
                    task: t,
                    semester: sem,
                    courses: courses,
                    exceptions: exceptions,
                    now: now
                )
            }
            if let occ {
                let key = CourseOccurrenceKey(courseId: occ.course.id, originalWeek: occ.originalWeek)
                counts[key, default: 0] += 1
            }
        }
        return counts
    }

    public static func effectiveDue(
        task: GokeTask,
        semester: Semester?,
        courses: [Course],
        exceptions: [CourseException],
        now: LocalDateTime
    ) -> LocalDateTime? {
        if task.dueBeforeNextClass, let courseId = task.courseId, let semester {
            if let cw = task.courseWeek {
                return CourseExpander.occurrence(
                    semester: semester,
                    courses: courses,
                    exceptions: exceptions,
                    courseId: courseId,
                    originalWeek: cw
                )?.start ?? task.due
            }
            if let next = CourseExpander.nextOccurrence(
                semester: semester,
                courses: courses,
                exceptions: exceptions,
                courseId: courseId,
                after: now
            ) {
                return next.start
            }
        }
        return task.due
    }

    public static func blockEnd(task: GokeTask, defaultMinutes: Int = 30) -> LocalDateTime? {
        guard let start = task.blockStart else { return nil }
        let minutes = task.blockMinutes ?? task.estimateMinutes ?? defaultMinutes
        return start.addingMinutes(minutes)
    }
}

/// Top-level convenience alias for `TaskLogic.CourseOccurrenceKey`.
public typealias CourseOccurrenceKey = TaskLogic.CourseOccurrenceKey

/// Unified agenda item for a calendar day.
public enum AgendaItem: Sendable, Hashable, Identifiable {
    case course(CourseOccurrence)
    case event(event: Event, start: LocalDateTime, end: LocalDateTime, allDay: Bool)
    case task(task: GokeTask, due: LocalDateTime, hasTime: Bool)
    case block(task: GokeTask, start: LocalDateTime, end: LocalDateTime)

    public var id: String {
        switch self {
        case .course(let occ): return "course:\(occ.id)"
        case .event(let ev, let start, _, _): return "event:\(ev.id)#\(start.description)"
        case .task(let t, _, _): return "task:\(t.id)"
        case .block(let t, let start, _): return "block:\(t.id)#\(start.description)"
        }
    }

    public var sortKey: LocalDateTime {
        switch self {
        case .course(let occ): return occ.start
        case .event(_, let start, _, let allDay): return allDay ? start.date.atStartOfDay() : start
        case .task(_, let due, let hasTime): return hasTime ? due : due.date.atTime(.endOfDay)
        case .block(_, let start, _): return start
        }
    }

    public var rank: Int {
        switch self {
        case .course: return 1
        case .event(_, _, _, let allDay): return allDay ? 0 : 1
        case .task(_, _, let hasTime): return hasTime ? 1 : 2
        case .block: return 1
        }
    }
}

public struct DayMarks: Sendable, Hashable {
    public let courses: Int
    public let events: Int
    public let tasks: Int
    public let doneTasks: Int
    public var scheduled: Int { courses + events }
    public var total: Int { courses + events + tasks }

    public init(courses: Int, events: Int, tasks: Int, doneTasks: Int = 0) {
        self.courses = courses
        self.events = events
        self.tasks = tasks
        self.doneTasks = doneTasks
    }
}

/// Unified agenda expander matching Android `Agenda`.
public enum AgendaEngine {
    public static func range(
        snapshot: AppSnapshot,
        from: LocalDate,
        to: LocalDate,
        defaultBlockMinutes: Int = 30,
        includeDoneTasks: Bool = true
    ) -> [LocalDate: [AgendaItem]] {
        let s = snapshot.live()
        var out: [LocalDate: [AgendaItem]] = [:]

        func add(_ date: LocalDate, _ item: AgendaItem) {
            guard date >= from && date <= to else { return }
            out[date, default: []].append(item)
        }

        let sem = s.currentSemester
        if let sem {
            for occ in CourseExpander.occurrences(
                semester: sem,
                courses: s.courses,
                exceptions: s.exceptions,
                from: from,
                to: to
            ) {
                add(occ.date, .course(occ))
            }
        }

        for occ in EventExpander.occurrences(events: s.events, from: from, to: to) {
            let allDay = occ.event.allDay
            var d = occ.start.date
            let last: LocalDate
            if allDay {
                last = occ.end.date
            } else if occ.end.time == .midnight && occ.end > occ.start {
                last = occ.end.date.addingDays(-1)
            } else {
                last = occ.end.date
            }
            while d <= last && d <= to {
                add(d, .event(event: occ.event, start: occ.start, end: occ.end, allDay: allDay))
                d = d.addingDays(1)
            }
        }

        for t in s.tasks where t.parentId == nil {
            if t.done && !includeDoneTasks { continue }
            let due = TaskLogic.effectiveDue(
                task: t,
                semester: sem,
                courses: s.courses,
                exceptions: s.exceptions,
                now: from.atStartOfDay()
            )
            let blockEnd = t.blockStart != nil ? TaskLogic.blockEnd(task: t, defaultMinutes: defaultBlockMinutes) : nil
            var emittedBlocks: [LocalDate: (start: LocalDateTime, end: LocalDateTime)]?
            if let blockStart = t.blockStart, let blockEnd {
                let durationSecs = max(Int64(60), blockStart.secondsUntil(blockEnd))
                var blocks: [LocalDate: (start: LocalDateTime, end: LocalDateTime)] = [:]
                if let rule = t.`repeat` {
                    for occDate in RecurrenceEngine.dates(rule: rule, anchor: blockStart.date, from: from, to: to) {
                        let occStart = LocalDateTime(date: occDate, time: blockStart.time)
                        let occEnd = occStart.addingSeconds(durationSecs)
                        add(occDate, .block(task: t, start: occStart, end: occEnd))
                        blocks[occDate] = (occStart, occEnd)
                    }
                } else {
                    let occStart = blockStart
                    let occEnd = occStart.addingSeconds(durationSecs)
                    add(blockStart.date, .block(task: t, start: occStart, end: occEnd))
                    blocks[blockStart.date] = (occStart, occEnd)
                }
                emittedBlocks = blocks
            }
            if let due {
                let sameDayBlockCoversDue: Bool
                if let block = emittedBlocks?[due.date] {
                    sameDayBlockCoversDue = !(t.dueHasTime || t.dueBeforeNextClass) || due == block.start || due == block.end
                } else if let blockStart = t.blockStart, due.date == blockStart.date {
                    sameDayBlockCoversDue = !(t.dueHasTime || t.dueBeforeNextClass) || due == blockStart || due == blockEnd
                } else {
                    sameDayBlockCoversDue = false
                }
                if !sameDayBlockCoversDue {
                    add(due.date, .task(task: t, due: due, hasTime: t.dueHasTime || t.dueBeforeNextClass))
                }
            } else if let startDate = t.startDate, emittedBlocks?[startDate] == nil {
                add(startDate, .task(task: t, due: startDate.atTime(.endOfDay), hasTime: false))
            }
        }

        return out.mapValues { items in
            items.sorted { lhs, rhs in
                let lg = lhs.rank == 0 ? 0 : 1
                let rg = rhs.rank == 0 ? 0 : 1
                if lg != rg { return lg < rg }
                if lhs.sortKey != rhs.sortKey { return lhs.sortKey < rhs.sortKey }
                return lhs.rank < rhs.rank
            }
        }
    }

    public static func forDay(
        snapshot: AppSnapshot,
        date: LocalDate,
        defaultBlockMinutes: Int = 30,
        includeDoneTasks: Bool = true
    ) -> [AgendaItem] {
        range(
            snapshot: snapshot,
            from: date,
            to: date,
            defaultBlockMinutes: defaultBlockMinutes,
            includeDoneTasks: includeDoneTasks
        )[date] ?? []
    }

    /// Maps a time block onto semester period indices, snapping to nearest period if outside schedule hours.
    public static func blockRows(
        periods: [PeriodTime],
        start: LocalTime,
        end: LocalTime
    ) -> ClosedRange<Int>? {
        guard !periods.isEmpty else { return nil }
        var isSorted = true
        for i in 1..<periods.count where periods[i - 1].index > periods[i].index {
            isSorted = false
            break
        }
        let sorted = isSorted ? periods : periods.sorted { $0.index < $1.index }
        let firstIdx = sorted.firstIndex { start < $0.end } ?? (sorted.count - 1)
        let rawLast = sorted.lastIndex { end > $0.start } ?? firstIdx
        let lastIdx = min(max(rawLast, firstIdx), sorted.count - 1)
        return sorted[firstIdx].index...sorted[lastIdx].index
    }

    /// Computes per-day `DayMarks` directly from an already-expanded `AgendaEngine.range(...)` map
    /// without running a second expansion pass.
    public static func marks(
        from agendaByDate: [LocalDate: [AgendaItem]]
    ) -> [LocalDate: DayMarks] {
        agendaByDate.mapValues { items in
            var openTaskIds = Set<String>()
            var doneTaskIds = Set<String>()
            var courses = 0
            var events = 0
            for item in items {
                switch item {
                case .course:
                    courses += 1
                case .event:
                    events += 1
                case .task(let t, _, _):
                    if t.done { doneTaskIds.insert(t.id) } else { openTaskIds.insert(t.id) }
                case .block(let t, _, _):
                    if t.done { doneTaskIds.insert(t.id) } else { openTaskIds.insert(t.id) }
                }
            }
            return DayMarks(courses: courses, events: events, tasks: openTaskIds.count, doneTasks: doneTaskIds.count)
        }
    }

    public static func marks(
        snapshot: AppSnapshot,
        from: LocalDate,
        to: LocalDate
    ) -> [LocalDate: DayMarks] {
        marks(from: range(snapshot: snapshot, from: from, to: to))
    }
}

public struct NextUp: Sendable, Hashable {
    public let occurrence: CourseOccurrence
    public let tasks: [GokeTask]

    public init(occurrence: CourseOccurrence, tasks: [GokeTask]) {
        self.occurrence = occurrence
        self.tasks = tasks
    }
}

public enum NextUps {
    public static func find(
        snapshot: AppSnapshot,
        now: LocalDateTime,
        horizonDays: Int = 7
    ) -> NextUp? {
        let s = snapshot.live()
        guard let sem = s.currentSemester else { return nil }
        let from = now.date
        guard let occ = CourseExpander
            .occurrences(
                semester: sem,
                courses: s.courses,
                exceptions: s.exceptions,
                from: from,
                to: from.addingDays(horizonDays)
            )
            .first(where: { $0.end > now })
        else { return nil }

        let related = TaskLogic.forOccurrence(
            s.tasks,
            semester: sem,
            courses: s.courses,
            exceptions: s.exceptions,
            occurrence: occ,
            now: now
        )
        return NextUp(occurrence: occ, tasks: related)
    }
}

/// Structured todo search query (`#清单 @标签 !高` + plain keywords, AND-combined).
/// Matches Android `TodoSearch.kt`.
public struct TodoQuery: Sendable, Hashable {
    public let words: [String]
    public let lists: [String]
    public let tags: [String]
    public let priority: Int?

    public var isEmpty: Bool {
        words.isEmpty && lists.isEmpty && tags.isEmpty && priority == nil
    }

    public init(words: [String], lists: [String], tags: [String], priority: Int?) {
        self.words = words
        self.lists = lists
        self.tags = tags
        self.priority = priority
    }
}

public enum TodoSearch {
    public static func parse(_ input: String) -> TodoQuery {
        var words: [String] = []
        var lists: [String] = []
        var tags: [String] = []
        var priority: Int? = nil

        let tokens = input.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0.isWhitespace })
        for rawSub in tokens {
            let raw = String(rawSub)
            if raw.isEmpty { continue }
            if raw.hasPrefix("#"), raw.count > 1 {
                lists.append(String(raw.dropFirst()).lowercased())
            } else if raw.hasPrefix("@"), raw.count > 1 {
                tags.append(String(raw.dropFirst()).lowercased())
            } else if raw.hasPrefix("!") || raw.hasPrefix("！") {
                switch String(raw.dropFirst()).lowercased() {
                case "高", "high", "3": priority = 3
                case "中", "mid", "2": priority = 2
                case "低", "low", "1": priority = 1
                default: break
                }
            } else {
                words.append(raw.lowercased())
            }
        }
        return TodoQuery(words: words, lists: lists, tags: tags, priority: priority)
    }

    /// Returns `true` when `task` (or any of its `subtasks`) satisfies all clauses in `query`.
    public static func matches(
        query: TodoQuery,
        task: GokeTask,
        subtasks: [GokeTask],
        listName: String?
    ) -> Bool {
        if query.isEmpty { return true }
        let lowerList = listName?.lowercased()
        if !query.lists.isEmpty {
            if query.lists.contains(where: { lowerList?.contains($0) != true }) {
                return false
            }
        }
        if !query.tags.isEmpty {
            for tag in query.tags {
                if !task.tags.contains(where: { $0.lowercased().contains(tag) }) {
                    return false
                }
            }
        }
        if let p = query.priority, task.priority != p {
            return false
        }
        let lowerTitle = task.title.lowercased()
        let lowerNote = task.note.lowercased()
        let lowerCode = (task.extra["code"] ?? task.extra["pickupCode"])?.lowercased() ?? ""
        return query.words.allSatisfy { w in
            lowerTitle.contains(w)
                || lowerNote.contains(w)
                || lowerCode.contains(w)
                || subtasks.contains(where: { $0.title.lowercased().contains(w) })
        }
    }
}
