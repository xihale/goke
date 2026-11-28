import Foundation
import GokeCoreModel

/// Planned result of a jxfw full or weekly sync pass.
public struct JxfwPlan: Sendable, Hashable {
    public var changeSet: ChangeSet
    public var courseCount: Int
    public var examCount: Int
    public var exceptionCount: Int
    public var extraCount: Int
    public var skippedReason: String?

    public init(
        changeSet: ChangeSet,
        courseCount: Int = 0,
        examCount: Int = 0,
        exceptionCount: Int = 0,
        extraCount: Int = 0,
        skippedReason: String? = nil
    ) {
        self.changeSet = changeSet
        self.courseCount = courseCount
        self.examCount = examCount
        self.exceptionCount = exceptionCount
        self.extraCount = extraCount
        self.skippedReason = skippedReason
    }

    public var isNoop: Bool { changeSet.isEmpty }
}

/// Pure, idempotent mapper from GDUT jxfw data to Goke `ChangeSet`s (matches Kotlin `JxfwSync.kt`).
///
/// Guarantees:
/// - Deterministic IDs (`jxfw-course/...`, `jxfw-excep/...`, `jxfw-exam/...`).
/// - Never touches manual courses (`source != .IMPORT` or non-`jxfw-` IDs).
/// - Never resurrects user-deleted tombstones (`deletedAt != nil`).
/// - Empty full/weekly responses produce a safe noop with `skippedReason`.
public enum JxfwSyncMapper {
    public static let coursePrefix = "jxfw-course/"
    public static let exceptionPrefix = "jxfw-excep/"
    public static let examPrefix = "jxfw-exam/"
    /// Legacy exam task prefix (and deprecated iOS official-exam course prefix cleaned up on sync/migration).
    public static let courseExamPrefix = "jxfw-course-exam/"
    /// Official exam course prefix matching Android `JxfwSync.EXAM_COURSE_PREFIX`.
    public static let examCoursePrefix = "jxfw-exam-course/"
    /// Single-week in-class remark exam course prefix matching Android `JxfwSync.COURSE_EXAM_COURSE_PREFIX`.
    public static let remarkExamCoursePrefix = "jxfw-remark-exam/"
    public static let examColorIndex = 2
    public static let paletteSize = 8
    private static let maxWeeks = 30

    private static let weekRemarkLineRegex = try! NSRegularExpression(
        pattern: #"^第([\d,\-]+)周：(.*)$"#
    )
    private static let examPrefixCaptureRegex = try! NSRegularExpression(
        pattern: #"^((?:期中|期末|随堂|结课|实验|上机)?(?:考试|测验|小测|考核)|(?:期中|期末|随堂|结课)测试|补考|重考|缓考|机考|机试)\s*[:：·\-—]\s*(.*)$"#
    )
    private static let verticalParenRegex = try! NSRegularExpression(
        pattern: #"^\([A-Za-z0-9+\-]{1,2}\)"#
    )
    private static let verticalAsciiRegex = try! NSRegularExpression(
        pattern: #"^[A-Za-z0-9+\-]{1,3}"#
    )

    public struct ExamDisplay: Sendable, Hashable {
        public let badgeFull: String
        public let badgeShort: String
        public let title: String

        public init(badgeFull: String, badgeShort: String, title: String) {
            self.badgeFull = badgeFull
            self.badgeShort = badgeShort
            self.title = title
        }
    }

    private struct Slot: Hashable {
        let name: String
        let day: Int
        let periods: [Int]
    }

    private struct AssignmentKey: Hashable {
        let slot: Slot
        let teacher: String
        let room: String
    }

    private struct WeekSlotKey: Hashable {
        let week: Int
        let slot: Slot
    }

    private struct CourseExamSpec {
        var week: Int
        var slot: Slot
        var teacher: String
        var room: String
        var remark: String
    }

    private struct OfficialExamEntry {
        let raw: JxfwExam
        var course: Course
        var hasExplicitTimeOrPeriods: Bool
    }

    public static func isJxfwId(_ id: String) -> Bool {
        id.hasPrefix("jxfw-")
    }

    private static func periodKey(_ periods: [Int]) -> String {
        guard let first = periods.first, let last = periods.last else { return "0" }
        if last - first + 1 == periods.count {
            return "\(first)-\(last)"
        }
        return periods.map(String.init).joined(separator: "_")
    }

    public static func courseId(
        term: String,
        day: Int,
        periods: [Int],
        name: String,
        teacher: String = "",
        room: String = "",
        splitByDetail: Bool = false
    ) -> String {
        let base = "\(coursePrefix)\(term)/\(day)/\(periodKey(periods))/\(name)"
        guard splitByDetail else { return base }
        let t = teacher.replacingOccurrences(of: "/", with: "_")
        let r = room.replacingOccurrences(of: "/", with: "_")
        return "\(base)/\(t)@\(r)"
    }

    public static func exceptionId(courseId: String, week: Int) -> String {
        "\(exceptionPrefix)\(courseId)/\(week)"
    }

    public static func examId(term: String, ksrcdm: String) -> String {
        "\(examPrefix)\(term)/\(ksrcdm)"
    }

    public static func examCourseId(term: String, ksrcdm: String) -> String {
        "\(examCoursePrefix)\(term)/\(ksrcdm)"
    }

    public static func remarkExamCourseId(
        term: String,
        week: Int,
        day: Int,
        periods: [Int],
        name: String
    ) -> String {
        "\(remarkExamCoursePrefix)\(term)/\(week)/\(day)/\(periodKey(periods))/\(name)"
    }

    private static func examLabelFromRemark(_ remark: String) -> String {
        if remark.contains("期中") && remark.contains("测") && !remark.contains("考") { return "期中测验" }
        if remark.contains("期中") { return "期中考试" }
        if remark.contains("期末") && remark.contains("测") && !remark.contains("考") { return "期末测验" }
        if remark.contains("期末") { return "期末考试" }
        if remark.contains("随堂") && remark.contains("考") { return "随堂考试" }
        if remark.contains("随堂") { return "随堂测验" }
        if remark.contains("补考") { return "补考" }
        if remark.contains("机考") || remark.contains("机试") || remark.contains("上机") { return "上机考试" }
        return "考试"
    }

    private static func shortenExamBadge(_ badgeFull: String) -> String {
        if badgeFull.contains("期中") { return "期中" }
        if badgeFull.contains("期末") { return "期末" }
        if badgeFull.contains("随堂") { return "随堂" }
        if badgeFull.contains("结课") { return "结课" }
        if badgeFull.contains("补考") { return "补考" }
        if badgeFull.contains("重考") { return "重考" }
        if badgeFull.contains("缓考") { return "缓考" }
        if badgeFull.contains("机考") || badgeFull.contains("机试") || badgeFull.contains("上机") { return "机考" }
        if badgeFull.contains("实验") { return "实验" }
        if badgeFull.contains("测验") || badgeFull.contains("小测") || badgeFull.contains("测试") { return "测验" }
        return String(badgeFull.prefix(2))
    }

    /// Splits an exam display title into a badge (`badgeFull` / `badgeShort`) and cleaned course name.
    public static func splitExamDisplay(
        _ rawName: String,
        fallbackBadge: String = "考试"
    ) -> ExamDisplay {
        let trimmed = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        let ns = trimmed as NSString
        let range = NSRange(location: 0, length: ns.length)
        var prefix: String?
        var rest: String?
        if let match = examPrefixCaptureRegex.firstMatch(in: trimmed, range: range), match.numberOfRanges >= 3 {
            let pRange = match.range(at: 1)
            if pRange.location != NSNotFound {
                let p = ns.substring(with: pRange).trimmingCharacters(in: .whitespacesAndNewlines)
                if !p.isEmpty { prefix = p }
            }
            let rRange = match.range(at: 2)
            if rRange.location != NSNotFound {
                let r = ns.substring(with: rRange).trimmingCharacters(in: .whitespacesAndNewlines)
                if !r.isEmpty { rest = r }
            }
        }
        let cleanTitle = (rest ?? trimmed)
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
        let trimmedFallback = fallbackBadge.trimmingCharacters(in: .whitespacesAndNewlines)
        let badgeFull = prefix ?? (trimmedFallback.isEmpty ? "考试" : trimmedFallback)
        return ExamDisplay(
            badgeFull: badgeFull,
            badgeShort: shortenExamBadge(badgeFull),
            title: cleanTitle
        )
    }

    /// Splits a title into vertical-text tokens (`(3)` and `<=3` ASCII runs stay upright as a single token).
    public static func tokenizeVerticalTitle(_ raw: String) -> [String] {
        let norm = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
        guard !norm.isEmpty else { return [] }
        var tokens: [String] = []
        var idx = norm.startIndex
        while idx < norm.endIndex {
            let rest = String(norm[idx...])
            let nsRest = rest as NSString
            let fullRange = NSRange(location: 0, length: nsRest.length)
            if let paren = verticalParenRegex.firstMatch(in: rest, range: fullRange) {
                let token = nsRest.substring(with: paren.range)
                tokens.append(token)
                idx = norm.index(idx, offsetBy: token.count)
                continue
            }
            if let ascii = verticalAsciiRegex.firstMatch(in: rest, range: fullRange) {
                let token = nsRest.substring(with: ascii.range)
                tokens.append(token)
                idx = norm.index(idx, offsetBy: token.count)
                continue
            }
            let ch = norm[idx]
            if !ch.isWhitespace {
                tokens.append(String(ch))
            }
            idx = norm.index(after: idx)
        }
        return tokens
    }

    /// Formats vertical multi-line text, truncating with `…` when exceeding `maxTokens`.
    public static func formatVerticalText(_ raw: String, maxTokens: Int) -> String {
        let tokens = tokenizeVerticalTitle(raw)
        guard !tokens.isEmpty, maxTokens > 0 else { return "" }
        let limited: [String]
        if tokens.count > maxTokens {
            limited = maxTokens == 1 ? ["…"] : Array(tokens.prefix(maxTokens - 1)) + ["…"]
        } else {
            limited = tokens
        }
        return limited.joined(separator: "\n")
    }

    /// Normalizes a course/exam name by stripping exam prefixes/suffixes, brackets, and whitespace.
    public static func normalizeCourseNameForExam(_ raw: String) -> String {
        ExamTextHelper.normalizeCourseNameForExam(raw)
    }

    public static func isSameCourseForExam(_ examName: String, _ courseName: String) -> Bool {
        ExamTextHelper.isSameCourseForExam(examName, courseName)
    }

    /// Returns true if a course remark represents an exam/quiz arrangement while filtering out
    /// review/Q&A/cancellation notices and software testing topics.
    public static func isExamRemark(_ remark: String) -> Bool {
        ExamTextHelper.isExamRemark(remark)
    }

    /// Classifies a course as `EventKind.EXAM` if its name or any note line matches exam keywords.
    public static func shouldClassifyCourseAsExam(name: String, note: String) -> Bool {
        if isExamRemark(name) { return true }
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedNote.isEmpty else { return false }
        return trimmedNote
            .components(separatedBy: .newlines)
            .contains { line in
                let content = CourseNotes.stripWeekPrefix(line)
                return !content.isEmpty && isExamRemark(content)
            }
    }

    public static func extractExamSeat(from note: String) -> String? {
        ExamTextHelper.extractExamSeat(from: note)
    }

    public static func formatExamLocation(
        location: String,
        note: String,
        seatSuffix: String
    ) -> String? {
        let loc = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let seat: String? = extractExamSeat(from: note).map { s in
            let suffix = seatSuffix.trimmingCharacters(in: .whitespacesAndNewlines)
            return suffix.isEmpty ? s : "\(s)\(suffix)"
        }
        switch (loc.isEmpty, seat) {
        case (false, let s?): return "\(loc) · \(s)"
        case (false, nil): return loc
        case (true, let s?): return s
        case (true, nil): return nil
        }
    }

    /// Subset-based room equality: `a ⊆ b || b ⊆ a` (multi-room labs often appear as subsets in weekly responses).
    public static func sameRoom(_ a: Set<String>, _ b: Set<String>) -> Bool {
        a.isSubset(of: b) || b.isSubset(of: a)
    }

    /// Assigns botanical palette indices (`0..<8`) by first appearance of each course name.
    public static func assignPalette(_ names: [String], startAt: Int = 0) -> [String: Int] {
        var map: [String: Int] = [:]
        var next = startAt
        for name in names where map[name] == nil {
            map[name] = next % paletteSize
            next += 1
        }
        return map
    }

    /// Identifies weeks that need `getKbRq` verification due to multiple teachers/rooms or overlapping slot rows.
    public static func ambiguousWeeks(_ lessons: [JxfwLesson]) -> Set<Int> {
        var out: Set<Int> = []
        var seenBySlot: [Slot: [Int: (String, String)]] = [:]

        for lesson in lessons {
            guard let day = JxfwParse.dayOfWeek(lesson.xq) else { continue }
            let periods = JxfwParse.periods(lesson.jcdm2)
            let name = lesson.kcmc
            guard !periods.isEmpty, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let weeks = JxfwParse.weeks(lesson.zcs).filter { (1...maxWeeks).contains($0) }
            guard !weeks.isEmpty else { continue }

            let teachers = cleanTeachers(lesson.teaxms)
            let roomSet = parseRoomsOrdered(lesson.jxcdmcs)
            if teachers.count > 1 || roomSet.count > 1 {
                out.formUnion(weeks)
                continue
            }
            let slot = Slot(name: name, day: day, periods: periods)
            let detail = (teachers.joined(separator: "、"), roomSet.joined(separator: ","))
            var byWeek = seenBySlot[slot] ?? [:]
            for w in weeks {
                if let prev = byWeek[w], prev != detail {
                    out.insert(w)
                }
                byWeek[w] = detail
            }
            seenBySlot[slot] = byWeek
        }
        return out
    }

    // MARK: - Full Semester Import

    public static func fullImport(
        snapshot: AppSnapshot,
        semester: Semester,
        term: String,
        lessons: [JxfwLesson],
        exams: [JxfwExam]? = nil,
        week1Monday: LocalDate? = nil,
        weekInstances: [Int: [JxfwInstance]] = [:],
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        idGenerator: () -> String = { UUID().uuidString.lowercased() }
    ) -> JxfwPlan {
        if lessons.isEmpty {
            return JxfwPlan(
                changeSet: ChangeSet(
                    id: idGenerator(),
                    description: "教务系统：全量同步",
                    source: .IMPORT,
                    createdAt: now
                ),
                skippedReason: "教务系统没有返回课程（可能本学期课表尚未发布），已保持原样"
            )
        }

        var changes: [Change] = []

        // Update semester week1Monday if provided and changed.
        let currentSem = snapshot.semesters.first(where: { $0.id == semester.id }) ?? semester
        let effectiveSem: Semester
        if let monday = week1Monday, monday != currentSem.week1Monday {
            var updatedSem = currentSem
            updatedSem.week1Monday = monday
            changes.append(.modify(before: .semester(currentSem), after: .semester(updatedSem)))
            effectiveSem = updatedSem
        } else {
            effectiveSem = currentSem
        }

        // Index weekly instances: (week, slot) -> (teacher, room, remark)
        var resolvedByWeek: [Int: [Slot: (String, String, String)]] = [:]
        for (week, instList) in weekInstances {
            var teachersBySlot: [Slot: [String]] = [:]
            var roomsBySlot: [Slot: [String]] = [:]
            var remarksBySlot: [Slot: [String]] = [:]
            var slots: [Slot] = []
            for inst in instList {
                guard let day = JxfwParse.dayOfWeek(inst.xq) else { continue }
                let periods = JxfwParse.periods(inst.jcdm2)
                guard !periods.isEmpty, !inst.kcmc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
                let slot = Slot(name: inst.kcmc, day: day, periods: periods)
                if teachersBySlot[slot] == nil {
                    slots.append(slot)
                    teachersBySlot[slot] = []
                    roomsBySlot[slot] = []
                    remarksBySlot[slot] = []
                }
                appendUnique(cleanTeachers(inst.teaxms), to: &teachersBySlot[slot]!)
                appendUnique(parseRoomsOrdered(inst.jxcdmc), to: &roomsBySlot[slot]!)
                let rem = inst.remark
                if !rem.isEmpty {
                    appendUnique([rem], to: &remarksBySlot[slot]!)
                }
            }
            var resolved: [Slot: (String, String, String)] = [:]
            for slot in slots {
                let t = (teachersBySlot[slot] ?? []).joined(separator: "、")
                let r = (roomsBySlot[slot] ?? []).joined(separator: ",")
                let rem = (remarksBySlot[slot] ?? []).joined(separator: " · ")
                resolved[slot] = (t, r, rem)
            }
            resolvedByWeek[week] = resolved
        }

        // Aggregate regular weeks by (slot, teacher, room); split out official in-class exams & single-week exam remarks.
        let cal = SemesterCalendar(semester: effectiveSem)
        var officialExamEntries = exams.map { buildOfficialExamEntries(term: term, semester: effectiveSem, exams: $0) } ?? []
        var orderedKeys: [AssignmentKey] = []
        var weeksByKey: [AssignmentKey: Set<Int>] = [:]
        var remarksByKey: [AssignmentKey: [Int: String]] = [:]
        var orderedExamKeys: [WeekSlotKey] = []
        var courseExams: [WeekSlotKey: CourseExamSpec] = [:]
        var coveredWeekSlots: Set<WeekSlotKey> = []

        for lesson in lessons {
            guard let day = JxfwParse.dayOfWeek(lesson.xq) else { continue }
            let periods = JxfwParse.periods(lesson.jcdm2)
            guard !periods.isEmpty, !lesson.kcmc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let slot = Slot(name: lesson.kcmc, day: day, periods: periods)
            let baseTeacher = cleanTeacher(lesson.teaxms)
            let baseRoom = parseRoomsOrdered(lesson.jxcdmcs).joined(separator: ",")
            let baseRemark = lesson.remark

            for w in JxfwParse.weeks(lesson.zcs).sorted() {
                let wsKey = WeekSlotKey(week: w, slot: slot)
                coveredWeekSlots.insert(wsKey)
                let resolved = resolvedByWeek[w]?[slot]
                let teacher = (resolved?.0.isEmpty == false) ? resolved!.0 : baseTeacher
                let room = (resolved?.1.isEmpty == false) ? resolved!.1 : baseRoom
                let remark = (resolved?.2.isEmpty == false) ? resolved!.2 : baseRemark

                if let officialIdx = matchOfficialExamIndex(
                    entries: officialExamEntries,
                    cal: cal,
                    week: w,
                    slot: slot,
                    kcbh: lesson.kcbh
                ) {
                    backfillExamFromSlot(&officialExamEntries[officialIdx], slot: slot, teacher: teacher, room: room)
                    continue
                }

                if isExamRemark(remark) {
                    if var prev = courseExams[wsKey] {
                        prev.teacher = mergeParts(prev.teacher, teacher, separator: "、")
                        prev.room = mergeParts(prev.room, room, separator: ",")
                        prev.remark = mergeParts(prev.remark, remark, separator: " · ")
                        courseExams[wsKey] = prev
                    } else {
                        orderedExamKeys.append(wsKey)
                        courseExams[wsKey] = CourseExamSpec(week: w, slot: slot, teacher: teacher, room: room, remark: remark)
                    }
                    continue
                }

                let key = AssignmentKey(slot: slot, teacher: teacher, room: room)
                if weeksByKey[key] == nil {
                    orderedKeys.append(key)
                    weeksByKey[key] = []
                }
                weeksByKey[key]?.insert(w)
                if !remark.isEmpty {
                    var perWeek = remarksByKey[key] ?? [:]
                    if let prev = perWeek[w] {
                        perWeek[w] = mergeParts(prev, remark, separator: " · ")
                    } else {
                        perWeek[w] = remark
                    }
                    remarksByKey[key] = perWeek
                }
            }
        }

        // Also promote weekly-only exam remark rows not present in full-semester `lessons`.
        for w in resolvedByWeek.keys.sorted() {
            guard let bySlot = resolvedByWeek[w] else { continue }
            for (slot, detail) in bySlot {
                let wsKey = WeekSlotKey(week: w, slot: slot)
                guard !coveredWeekSlots.contains(wsKey) else { continue }
                if matchOfficialExamIndex(entries: officialExamEntries, cal: cal, week: w, slot: slot) != nil {
                    continue
                }
                if isExamRemark(detail.2) {
                    if courseExams[wsKey] == nil {
                        orderedExamKeys.append(wsKey)
                    }
                    courseExams[wsKey] = CourseExamSpec(week: w, slot: slot, teacher: detail.0, room: detail.1, remark: detail.2)
                }
            }
        }

        var groupCountBySlot: [Slot: Int] = [:]
        for key in orderedKeys {
            groupCountBySlot[key.slot, default: 0] += 1
        }
        let colors = assignPalette(orderedKeys.map(\.slot.name))

        var liveImportedByName: [String: Course] = [:]
        let termCoursePrefix = "\(coursePrefix)\(term)/"
        for c in snapshot.courses
        where c.deletedAt == nil && c.semesterId == semester.id && c.source == .IMPORT && c.id.hasPrefix(termCoursePrefix) {
            liveImportedByName[c.name] = c
        }

        var expectedIds: Set<String> = []
        for key in orderedKeys {
            let weeks = weeksByKey[key] ?? []
            let split = (groupCountBySlot[key.slot] ?? 1) > 1
            let id = courseId(
                term: term,
                day: key.slot.day,
                periods: key.slot.periods,
                name: key.slot.name,
                teacher: key.teacher,
                room: key.room,
                splitByDetail: split
            )
            expectedIds.insert(id)

            let existing = snapshot.courses.first(where: { $0.id == id })
            if let existing, existing.deletedAt != nil {
                continue // Never resurrect user-deleted tombstones.
            }
            let template = existing ?? liveImportedByName[key.slot.name]
            let importedNote = formatCourseRemarks(weeks: weeks, remarksByWeek: remarksByKey[key] ?? [:])
            let cleanTemplateNote = stripStaleExamRemarkLines(template?.note ?? "")
            let effectiveNote = importedNote.isEmpty ? cleanTemplateNote : importedNote
            // Regular jxfw-course/ records are NEVER marked as .EXAM across the semester;
            // exams live exclusively in single-week jxfw-exam-course/ and jxfw-remark-exam/ courses.
            let want = Course(
                id: id,
                semesterId: semester.id,
                name: key.slot.name,
                teacher: key.teacher,
                room: key.room,
                dayOfWeek: key.slot.day,
                startPeriod: key.slot.periods.first ?? 1,
                endPeriod: key.slot.periods.last ?? 1,
                weeks: weeks,
                colorIndex: template?.colorIndex ?? (colors[key.slot.name] ?? 0),
                remindMinutes: template?.remindMinutes,
                note: effectiveNote,
                source: .IMPORT,
                remindMinutesList: template?.remindMinutesList ?? [],
                kind: .NORMAL,
                remindPersistent: false
            )

            if let existing {
                var merged = existing
                merged.semesterId = want.semesterId
                merged.teacher = want.teacher
                merged.room = want.room
                merged.dayOfWeek = want.dayOfWeek
                merged.startPeriod = want.startPeriod
                merged.endPeriod = want.endPeriod
                merged.weeks = want.weeks
                let cleanedExistingNote = stripStaleExamRemarkLines(existing.note)
                merged.note = mergeCourseNote(existingNote: cleanedExistingNote, importedNote: importedNote)
                // Self-heal regular imported courses previously misclassified as .EXAM by older builds.
                if existing.kind == .EXAM {
                    merged.kind = .NORMAL
                    merged.remindPersistent = false
                }
                if merged != existing {
                    changes.append(.modify(before: .course(existing), after: .course(merged)))
                }
            } else {
                changes.append(.add(after: .course(want)))
            }
        }

        // Remove stale jxfw-imported courses in this semester (plus their exceptions).
        var removedCourseIds: Set<String> = []
        for c in snapshot.courses
        where c.deletedAt == nil && c.source == .IMPORT && c.semesterId == semester.id
            && c.id.hasPrefix(coursePrefix) && !expectedIds.contains(c.id)
        {
            changes.append(.remove(before: .course(c)))
            removedCourseIds.insert(c.id)
        }
        for e in snapshot.exceptions where e.deletedAt == nil && removedCourseIds.contains(e.courseId) {
            changes.append(.remove(before: .courseException(e)))
        }

        if exams != nil {
            changes.append(
                contentsOf: syncExamCourses(
                    snapshot: snapshot,
                    semester: effectiveSem,
                    scopePrefix: examCoursePrefix,
                    legacyTaskPrefix: examPrefix,
                    legacyCoursePrefix: courseExamPrefix,
                    wantCourses: officialExamEntries.map(\.course)
                )
            )
        }

        let remarkExamCourses: [Course] = orderedExamKeys.compactMap { wsKey in
            guard let spec = courseExams[wsKey] else { return nil }
            let date = cal.dateOf(week: spec.week, dayOfWeek: spec.slot.day)
            if hasOfficialExamMatch(exams: exams, date: date, courseName: spec.slot.name) {
                return nil
            }
            return remarkExamCourse(term: term, semester: effectiveSem, spec: spec)
        }
        changes.append(
            contentsOf: syncExamCourses(
                snapshot: snapshot,
                semester: effectiveSem,
                scopePrefix: remarkExamCoursePrefix,
                legacyTaskPrefix: courseExamPrefix,
                legacyCoursePrefix: nil,
                wantCourses: remarkExamCourses
            )
        )

        return JxfwPlan(
            changeSet: ChangeSet(
                id: idGenerator(),
                description: "教务系统：全量同步",
                source: .IMPORT,
                createdAt: now,
                changes: changes
            ),
            courseCount: orderedKeys.count,
            examCount: (exams?.count ?? 0) + remarkExamCourses.count
        )
    }

    // MARK: - Exam Mapping (`JxfwExam` -> `Course` `jxfw-exam-course/` & `jxfw-remark-exam/`)

    /// Maps an official `JxfwExam` to a timetable `Course` (`jxfw-exam-course/<term>/<ksrcdm>`).
    public static func examCourse(semester: Semester, term: String, exam: JxfwExam) -> Course? {
        let cal = SemesterCalendar(semester: semester)
        let week: Int
        let dayOfWeek: Int
        if let date = LocalDate.parse(exam.ksrq.trimmingCharacters(in: .whitespacesAndNewlines)) {
            week = cal.weekOf(date)
            dayOfWeek = date.dayOfWeek
        } else if let zc = Int(exam.zc.trimmingCharacters(in: .whitespacesAndNewlines)),
                  let dow = JxfwParse.dayOfWeek(exam.xq)
        {
            week = zc
            dayOfWeek = dow
        } else {
            return nil
        }
        guard week >= 1 else { return nil }

        let maxP = max(1, cal.periods.last?.index ?? 12)
        let range = JxfwParse.parseKssj(exam.kssj)
        let parsedPeriods = JxfwParse.periods(exam.jcdm2)
        let startPeriod: Int
        let endPeriod: Int
        let startTime: String?
        let endTime: String?
        if let range {
            let rows = AgendaEngine.blockRows(periods: cal.periods, start: range.0, end: range.1)
            startPeriod = min(max(1, rows?.lowerBound ?? parsedPeriods.first ?? 1), maxP)
            endPeriod = min(max(startPeriod, rows?.upperBound ?? parsedPeriods.last ?? startPeriod), maxP)
            startTime = range.0.description
            endTime = range.1.description
        } else if !parsedPeriods.isEmpty {
            startPeriod = min(max(1, parsedPeriods.first ?? 1), maxP)
            endPeriod = min(max(startPeriod, parsedPeriods.last ?? startPeriod), maxP)
            startTime = nil
            endTime = nil
        } else {
            startPeriod = min(1, maxP)
            endPeriod = startPeriod
            startTime = nil
            endTime = nil
        }

        let kindLabel = exam.ksaplxmc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? exam.kslbmc
            : exam.ksaplxmc
        let seat = exam.zwh.trimmingCharacters(in: .whitespacesAndNewlines)
        var noteParts: [String] = []
        let campus = exam.xqmc.trimmingCharacters(in: .whitespacesAndNewlines)
        if !campus.isEmpty { noteParts.append(campus) }
        let trimmedKind = kindLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedKind.isEmpty { noteParts.append(trimmedKind) }
        if !seat.isEmpty { noteParts.append("座位号 \(seat)") }
        let note = noteParts.joined(separator: " · ")

        let trimmedRoom = exam.kscdmc.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = exam.kcmc.hasPrefix("考试：") ? exam.kcmc : "考试：\(exam.kcmc)"
        return Course(
            id: examCourseId(term: term, ksrcdm: exam.ksrcdm),
            semesterId: semester.id,
            name: title,
            teacher: cleanTeacher(exam.jkteaxms),
            room: trimmedRoom,
            dayOfWeek: dayOfWeek,
            startPeriod: startPeriod,
            endPeriod: endPeriod,
            weeks: [week],
            colorIndex: examColorIndex,
            note: note,
            source: .IMPORT,
            kind: .EXAM,
            remindPersistent: true,
            startTime: startTime,
            endTime: endTime
        )
    }

    private static func buildOfficialExamEntries(
        term: String,
        semester: Semester,
        exams: [JxfwExam]
    ) -> [OfficialExamEntry] {
        exams.compactMap { exam in
            guard let course = examCourse(semester: semester, term: term, exam: exam) else { return nil }
            let explicit = JxfwParse.parseKssj(exam.kssj) != nil || !JxfwParse.periods(exam.jcdm2).isEmpty
            return OfficialExamEntry(raw: exam, course: course, hasExplicitTimeOrPeriods: explicit)
        }
    }

    private static func matchOfficialExamIndex(
        entries: [OfficialExamEntry],
        cal: SemesterCalendar,
        week: Int,
        slot: Slot,
        kcbh: String = ""
    ) -> Int? {
        guard !entries.isEmpty else { return nil }
        let slotStart = slot.periods.first.flatMap { cal.startTime(period: $0) }
        let slotEnd = slot.periods.last.flatMap { cal.endTime(period: $0) }
        let trimmedKcbh = kcbh.trimmingCharacters(in: .whitespacesAndNewlines)
        return entries.firstIndex { entry in
            let c = entry.course
            guard c.weeks.contains(week), slot.day == c.dayOfWeek else { return false }
            let entryKcbh = entry.raw.kcbh.trimmingCharacters(in: .whitespacesAndNewlines)
            let sameCourse = (!entryKcbh.isEmpty && !trimmedKcbh.isEmpty && entryKcbh == trimmedKcbh)
                || isSameCourseForExam(entry.raw.kcmc, slot.name)
            guard sameCourse else { return false }
            if !entry.hasExplicitTimeOrPeriods { return true }
            let periodOverlap = (slot.periods.first ?? 1) <= c.endPeriod && c.startPeriod <= (slot.periods.last ?? 1)
            let clockOverlap: Bool
            if let ss = slotStart, let se = slotEnd, let cs = c.startLocalTime, let ce = c.endLocalTime {
                clockOverlap = ss < ce && cs < se
            } else {
                clockOverlap = false
            }
            return periodOverlap || clockOverlap
        }
    }

    private static func backfillExamFromSlot(
        _ entry: inout OfficialExamEntry,
        slot: Slot,
        teacher: String,
        room: String
    ) {
        if !entry.hasExplicitTimeOrPeriods {
            entry.course.startPeriod = slot.periods.first ?? entry.course.startPeriod
            entry.course.endPeriod = slot.periods.last ?? entry.course.endPeriod
            entry.hasExplicitTimeOrPeriods = true
        }
        if entry.course.room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           !room.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        {
            entry.course.room = room
        }
        if entry.course.teacher.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           !teacher.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        {
            entry.course.teacher = teacher
        }
    }

    private static func hasOfficialExamMatch(
        exams: [JxfwExam]?,
        date: LocalDate,
        courseName: String
    ) -> Bool {
        guard let exams, !exams.isEmpty else { return false }
        let dateStr = date.description
        return exams.contains { ex in
            ex.ksrq.trimmingCharacters(in: .whitespacesAndNewlines) == dateStr
                && isSameCourseForExam(ex.kcmc, courseName)
        }
    }

    private static func remarkExamCourse(
        term: String,
        semester: Semester,
        spec: CourseExamSpec
    ) -> Course {
        Course(
            id: remarkExamCourseId(
                term: term,
                week: spec.week,
                day: spec.slot.day,
                periods: spec.slot.periods,
                name: spec.slot.name
            ),
            semesterId: semester.id,
            name: "\(examLabelFromRemark(spec.remark))：\(spec.slot.name)",
            teacher: spec.teacher,
            room: spec.room,
            dayOfWeek: spec.slot.day,
            startPeriod: spec.slot.periods.first ?? 1,
            endPeriod: spec.slot.periods.last ?? 1,
            weeks: [spec.week],
            colorIndex: examColorIndex,
            note: spec.remark,
            source: .IMPORT,
            kind: .EXAM,
            remindPersistent: true
        )
    }

    private static func syncExamCourses(
        snapshot: AppSnapshot,
        semester: Semester,
        scopePrefix: String,
        legacyTaskPrefix: String,
        legacyCoursePrefix: String?,
        wantCourses: [Course]
    ) -> [Change] {
        var out: [Change] = []
        var expected: Set<String> = []

        for want in wantCourses {
            expected.insert(want.id)
            let existing = snapshot.courses.first(where: { $0.id == want.id })
            if let existing, existing.deletedAt != nil {
                continue // Never resurrect tombstones.
            }
            if let existing {
                var merged = existing
                merged.semesterId = want.semesterId
                merged.name = want.name
                merged.teacher = want.teacher
                merged.room = want.room
                merged.dayOfWeek = want.dayOfWeek
                merged.startPeriod = want.startPeriod
                merged.endPeriod = want.endPeriod
                merged.weeks = want.weeks
                merged.note = want.note
                merged.kind = .EXAM
                merged.startTime = want.startTime
                merged.endTime = want.endTime
                if merged != existing {
                    out.append(.modify(before: .course(existing), after: .course(merged)))
                }
            } else {
                out.append(.add(after: .course(want)))
            }
        }

        var removedIds: Set<String> = []
        for course in snapshot.courses
        where course.deletedAt == nil && course.source == .IMPORT && course.semesterId == semester.id {
            let isStaleScoped = course.id.hasPrefix(scopePrefix) && !expected.contains(course.id)
            let isLegacyCourseExam = legacyCoursePrefix.map { course.id.hasPrefix($0) } ?? false
            if isStaleScoped || isLegacyCourseExam {
                out.append(.remove(before: .course(course)))
                removedIds.insert(course.id)
            }
        }
        if !removedIds.isEmpty {
            for ex in snapshot.exceptions where ex.deletedAt == nil && removedIds.contains(ex.courseId) {
                out.append(.remove(before: .courseException(ex)))
            }
        }

        // Remove legacy exam tasks superseded by exam courses.
        for task in snapshot.tasks
        where task.deletedAt == nil && task.source == .IMPORT && task.id.hasPrefix(legacyTaskPrefix) {
            out.append(.remove(before: .task(task)))
        }

        return out
    }

    public static func examChanges(
        snapshot: AppSnapshot,
        semester: Semester,
        term: String,
        exams: [JxfwExam]
    ) -> [Change] {
        syncExamCourses(
            snapshot: snapshot,
            semester: semester,
            scopePrefix: "\(examCoursePrefix)\(term)/",
            legacyTaskPrefix: "\(examPrefix)\(term)/",
            legacyCoursePrefix: "\(courseExamPrefix)\(term)/",
            wantCourses: exams.compactMap { examCourse(semester: semester, term: term, exam: $0) }
        )
    }

    /// Migrates legacy imported exam tasks (`jxfw-exam/...`, `jxfw-course-exam/...`) into timetable exam courses
    /// (`jxfw-exam-course/...`, `jxfw-remark-exam/...`), and self-heals legacy iOS `jxfw-course-exam/` courses and
    /// regular multi-week `jxfw-course/` records mistakenly flagged `isExam = true`.
    public static func migrateExamTasksToCourses(
        snapshot: AppSnapshot,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        idGenerator: () -> String = { UUID().uuidString.lowercased() }
    ) -> ChangeSet? {
        let legacyTasks = snapshot.tasks.filter {
            $0.deletedAt == nil && $0.source == .IMPORT
                && ($0.id.hasPrefix(examPrefix) || $0.id.hasPrefix(courseExamPrefix))
        }
        let legacyExamCourses = snapshot.courses.filter {
            $0.deletedAt == nil && $0.source == .IMPORT && $0.id.hasPrefix(courseExamPrefix)
        }
        let misclassifiedRegularCourses = snapshot.courses.filter {
            $0.deletedAt == nil && $0.source == .IMPORT
                && $0.id.hasPrefix(coursePrefix) && $0.kind == .EXAM
        }
        guard !legacyTasks.isEmpty || !legacyExamCourses.isEmpty || !misclassifiedRegularCourses.isEmpty else {
            return nil
        }

        let sem = snapshot.live().currentSemester
        let cal = sem.map { SemesterCalendar(semester: $0) }
        let maxP = max(1, cal?.periods.last?.index ?? 12)
        var changes: [Change] = []
        var existingOrAddedCourseIds = Set(snapshot.courses.map(\.id))

        for task in legacyTasks {
            changes.append(.remove(before: .task(task)))
            guard let sem, let cal else { continue }
            let isOfficial = task.id.hasPrefix(examPrefix)
            let targetId: String
            if isOfficial {
                targetId = examCoursePrefix + String(task.id.dropFirst(examPrefix.count))
            } else {
                targetId = remarkExamCoursePrefix + String(task.id.dropFirst(courseExamPrefix.count))
            }
            guard !existingOrAddedCourseIds.contains(targetId) else { continue }

            var week: Int?
            var dow: Int?
            var sp = min(1, maxP)
            var ep = sp
            var startTime: String?
            var endTime: String?
            var resolved = false

            if !isOfficial {
                let parts = String(task.id.dropFirst(courseExamPrefix.count)).split(separator: "/").map(String.init)
                if parts.count >= 4,
                   let w = Int(parts[1]),
                   let d = Int(parts[2]), (1...7).contains(d)
                {
                    let ps = parts[3]
                        .split { $0 == "-" || $0 == "_" }
                        .compactMap { Int($0) }
                    if let minP = ps.min(), let maxParsed = ps.max() {
                        week = w
                        dow = d
                        sp = min(max(1, minP), maxP)
                        ep = min(max(sp, maxParsed), maxP)
                        resolved = true
                    }
                }
            }
            if !resolved, let date = task.blockStart?.date ?? task.startDate ?? task.due?.date {
                let w = cal.weekOf(date)
                if w >= 1 {
                    week = w
                    dow = date.dayOfWeek
                    if let st = task.blockStart?.time, let mins = task.blockMinutes {
                        let et = task.blockStart!.addingMinutes(mins).time
                        if let rows = AgendaEngine.blockRows(periods: cal.periods, start: st, end: et) {
                            sp = min(max(1, rows.lowerBound), maxP)
                            ep = min(max(sp, rows.upperBound), maxP)
                        }
                        startTime = st.description
                        endTime = et.description
                    }
                    resolved = true
                }
            }
            guard resolved, let finalWeek = week, let finalDow = dow else { continue }

            let course = Course(
                id: targetId,
                semesterId: sem.id,
                name: task.title,
                room: task.extra["location"] ?? "",
                dayOfWeek: finalDow,
                startPeriod: sp,
                endPeriod: ep,
                weeks: [finalWeek],
                colorIndex: examColorIndex,
                remindMinutes: task.remindMinutes,
                note: task.note,
                source: .IMPORT,
                remindMinutesList: task.remindMinutesList,
                kind: .EXAM,
                remindPersistent: task.remindPersistent,
                startTime: startTime,
                endTime: endTime
            )
            changes.append(.add(after: .course(course)))
            existingOrAddedCourseIds.insert(targetId)
        }

        // Self-heal legacy iOS `jxfw-course-exam/` courses -> Android-compatible `jxfw-exam-course/` IDs.
        for legacyCourse in legacyExamCourses {
            changes.append(.remove(before: .course(legacyCourse)))
            let targetId = examCoursePrefix + String(legacyCourse.id.dropFirst(courseExamPrefix.count))
            guard !existingOrAddedCourseIds.contains(targetId) else { continue }
            var migrated = legacyCourse
            migrated.id = targetId
            migrated.kind = .EXAM
            migrated.remindPersistent = true
            changes.append(.add(after: .course(migrated)))
            existingOrAddedCourseIds.insert(targetId)
        }

        // Self-heal mistakenly flagged `jxfw-course/` regular courses back to `.NORMAL`.
        for badCourse in misclassifiedRegularCourses {
            var fixed = badCourse
            fixed.kind = .NORMAL
            fixed.remindPersistent = false
            fixed.note = stripStaleExamRemarkLines(badCourse.note)
            if fixed != badCourse {
                changes.append(.modify(before: .course(badCourse), after: .course(fixed)))
            }
        }

        guard !changes.isEmpty else { return nil }
        return ChangeSet(
            id: idGenerator(),
            description: "教务考试转为课程",
            source: .IMPORT,
            createdAt: now,
            changes: changes
        )
    }

    /// Legacy mapper retained for backward compatibility with older callers.
    public static func examTask(term: String, exam: JxfwExam) -> GokeTask? {
        guard let date = LocalDate.parse(exam.ksrq) else { return nil }
        let range = JxfwParse.parseKssj(exam.kssj)
        let start = range.map { LocalDateTime(date: date, time: $0.0) }
        let end = range.map { LocalDateTime(date: date, time: $0.1) }

        let kindLabel = exam.ksaplxmc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? exam.kslbmc
            : exam.ksaplxmc
        let baseNote = [exam.xqmc, kindLabel, exam.kscdmc]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        let seat = exam.zwh.trimmingCharacters(in: .whitespacesAndNewlines)
        let note: String
        if !seat.isEmpty {
            note = baseNote.isEmpty ? "座位号 \(seat)" : "\(baseNote) · 座位号 \(seat)"
        } else {
            note = baseNote
        }

        let minutes: Int?
        if let start, let end {
            let diffMin = Int(start.secondsUntil(end) / 60)
            minutes = max(15, diffMin)
        } else {
            minutes = nil
        }

        var extra: [String: String] = [
            "kind": EventKind.EXAM.rawValue,
            "checkable": "false",
        ]
        let trimmedRoom = exam.kscdmc.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedRoom.isEmpty {
            extra["location"] = trimmedRoom
        }

        return GokeTask(
            id: examId(term: term, ksrcdm: exam.ksrcdm),
            title: "考试：\(exam.kcmc)",
            note: note,
            tags: ["考试"],
            priority: 3,
            startDate: date,
            due: end ?? date.atTime(hour: 23, minute: 59),
            dueHasTime: start != nil,
            remindPersistent: true,
            blockStart: start,
            blockMinutes: minutes,
            source: .IMPORT,
            extra: extra
        )
    }

    public static func examChanges(snapshot: AppSnapshot, term: String, exams: [JxfwExam]) -> [Change] {
        var out: [Change] = []
        var expected: Set<String> = []

        for exam in exams {
            guard let want = examTask(term: term, exam: exam) else { continue }
            expected.insert(want.id)
            let existing = snapshot.tasks.first(where: { $0.id == want.id })
            if let existing, existing.deletedAt != nil {
                continue // Never resurrect tombstones.
            }
            if let existing {
                var merged = existing
                merged.title = want.title
                merged.note = want.note
                merged.due = want.due
                merged.dueHasTime = want.dueHasTime
                merged.blockStart = want.blockStart
                merged.blockMinutes = want.blockMinutes
                merged.startDate = want.startDate
                if merged != existing {
                    out.append(.modify(before: .task(existing), after: .task(merged)))
                }
            } else {
                out.append(.add(after: .task(want)))
            }
        }

        let termExamPrefix = "\(examPrefix)\(term)/"
        for task in snapshot.tasks
        where task.deletedAt == nil && task.source == .IMPORT
            && task.id.hasPrefix(termExamPrefix) && !expected.contains(task.id)
        {
            out.append(.remove(before: .task(task)))
        }
        return out
    }

    // MARK: - Weekly Incremental Diff

    public static func weeklyUpdate(
        snapshot: AppSnapshot,
        semester: Semester,
        term: String,
        week: Int,
        instances: [JxfwInstance],
        exams: [JxfwExam]? = nil,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        idGenerator: () -> String = { UUID().uuidString.lowercased() }
    ) -> JxfwPlan {
        var changes: [Change] = []
        let termCoursePrefix = "\(coursePrefix)\(term)/"
        let imported = snapshot.courses.filter {
            $0.deletedAt == nil && $0.source == .IMPORT && $0.semesterId == semester.id && $0.id.hasPrefix(termCoursePrefix)
        }

        func slotOf(_ course: Course) -> Slot {
            Slot(name: course.name, day: course.dayOfWeek, periods: Array(course.startPeriod...course.endPeriod))
        }

        var predictedOrder: [Slot] = []
        var predicted: [Slot: Course] = [:]
        for c in imported where c.weeks.contains(week) {
            let slot = slotOf(c)
            if predicted[slot] == nil {
                predictedOrder.append(slot)
            }
            predicted[slot] = c
        }

        let cal = SemesterCalendar(semester: semester)
        var officialExamEntries = exams.map { buildOfficialExamEntries(term: term, semester: semester, exams: $0) } ?? []

        var actualOrder: [Slot] = []
        var actualRooms: [Slot: [String]] = [:]
        var actualTeachers: [Slot: [String]] = [:]
        var actualRemarks: [Slot: [String]] = [:]
        var examSpecOrder: [Slot] = []
        var examSpecs: [Slot: CourseExamSpec] = [:]
        var officialExamSlots: Set<Slot> = []

        for inst in instances {
            guard let day = JxfwParse.dayOfWeek(inst.xq) else { continue }
            let periods = JxfwParse.periods(inst.jcdm2)
            guard !periods.isEmpty, !inst.kcmc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let slot = Slot(name: inst.kcmc, day: day, periods: periods)
            if let officialIdx = matchOfficialExamIndex(
                entries: officialExamEntries,
                cal: cal,
                week: week,
                slot: slot,
                kcbh: inst.kcbh
            ) {
                backfillExamFromSlot(
                    &officialExamEntries[officialIdx],
                    slot: slot,
                    teacher: cleanTeacher(inst.teaxms),
                    room: parseRoomsOrdered(inst.jxcdmc).joined(separator: ",")
                )
                officialExamSlots.insert(slot)
                continue
            }
            let remark = inst.remark
            if isExamRemark(remark) {
                let teacher = cleanTeacher(inst.teaxms)
                let room = parseRoomsOrdered(inst.jxcdmc).joined(separator: ",")
                if var prev = examSpecs[slot] {
                    prev.teacher = mergeParts(prev.teacher, teacher, separator: "、")
                    prev.room = mergeParts(prev.room, room, separator: ",")
                    prev.remark = mergeParts(prev.remark, remark, separator: " · ")
                    examSpecs[slot] = prev
                } else {
                    examSpecOrder.append(slot)
                    examSpecs[slot] = CourseExamSpec(week: week, slot: slot, teacher: teacher, room: room, remark: remark)
                }
            } else {
                if actualRooms[slot] == nil {
                    actualOrder.append(slot)
                    actualRooms[slot] = []
                    actualTeachers[slot] = []
                    actualRemarks[slot] = []
                }
                appendUnique(parseRoomsOrdered(inst.jxcdmc), to: &actualRooms[slot]!)
                appendUnique(cleanTeachers(inst.teaxms), to: &actualTeachers[slot]!)
                if !remark.isEmpty {
                    appendUnique([remark], to: &actualRemarks[slot]!)
                }
            }
        }

        // Safety valve: never cancel the entire week if jxfw returns an empty weekly response.
        if actualRooms.isEmpty && examSpecs.isEmpty && officialExamSlots.isEmpty && !predicted.isEmpty {
            return JxfwPlan(
                changeSet: ChangeSet(
                    id: idGenerator(),
                    description: "教务系统：第 \(week) 周更新",
                    source: .IMPORT,
                    createdAt: now
                ),
                skippedReason: "教务系统返回的第 \(week) 周课表为空，已跳过以免误标停课"
            )
        }

        var wantExceptionOrder: [String] = []
        var wantExceptions: [String: CourseException] = [:]
        var extra = 0

        func putException(_ ex: CourseException) {
            if wantExceptions[ex.id] == nil {
                wantExceptionOrder.append(ex.id)
            }
            wantExceptions[ex.id] = ex
        }

        // Cancel regular sessions replaced by official in-class exams or weekly exam remarks.
        var examCancelledSlots: Set<Slot> = []
        for slot in examSpecOrder + Array(officialExamSlots) {
            guard let course = predicted[slot] else { continue }
            let id = exceptionId(courseId: course.id, week: week)
            putException(CourseException(id: id, courseId: course.id, week: week, type: .CANCEL))
            examCancelledSlots.insert(slot)
        }
        for slot in predictedOrder where !examCancelledSlots.contains(slot) {
            guard let course = predicted[slot] else { continue }
            if matchOfficialExamIndex(entries: officialExamEntries, cal: cal, week: week, slot: slot) != nil {
                let id = exceptionId(courseId: course.id, week: week)
                putException(CourseException(id: id, courseId: course.id, week: week, type: .CANCEL))
                examCancelledSlots.insert(slot)
            }
        }

        // Same slot: compare room (subset equality) and merge weekly remark.
        for slot in predictedOrder where !examCancelledSlots.contains(slot) {
            guard let course = predicted[slot], let actualRoomList = actualRooms[slot] else { continue }
            let actualSet = Set(actualRoomList)
            if !sameRoom(Set(parseRoomsOrdered(course.room)), actualSet) {
                let id = exceptionId(courseId: course.id, week: week)
                putException(
                    CourseException(
                        id: id,
                        courseId: course.id,
                        week: week,
                        type: .RESCHEDULE,
                        newRoom: actualRoomList.joined(separator: ",")
                    )
                )
            }
            let instRemark = (actualRemarks[slot] ?? []).joined(separator: " · ")
            var updated = course
            if !instRemark.isEmpty {
                updated.note = mergeWeeklyCourseNote(
                    existingNote: course.note,
                    week: week,
                    weekRemark: instRemark,
                    singleWeek: course.weeks == [week]
                )
            }
            if course.kind == .EXAM {
                updated.kind = .NORMAL
                updated.remindPersistent = false
            }
            if updated != course {
                changes.append(.modify(before: .course(course), after: .course(updated)))
            }
        }

        let unmatchedActual = actualOrder.filter { predicted[$0] == nil }
        var unmatchedPredicted = predictedOrder.filter { actualRooms[$0] == nil && !examCancelledSlots.contains($0) }

        for slot in unmatchedActual {
            let roomList = actualRooms[slot] ?? []
            let roomSet = Set(roomList)
            let room = roomList.joined(separator: ",")
            let teacher = (actualTeachers[slot] ?? []).joined(separator: "、")
            let instRemark = (actualRemarks[slot] ?? []).joined(separator: " · ")

            // Pair with an unmatched predicted slot of the same course name -> RESCHEDULE.
            if let pairIdx = unmatchedPredicted.firstIndex(where: { $0.name == slot.name }) {
                let pairedSlot = unmatchedPredicted.remove(at: pairIdx)
                if let course = predicted[pairedSlot] {
                    let id = exceptionId(courseId: course.id, week: week)
                    putException(
                        CourseException(
                            id: id,
                            courseId: course.id,
                            week: week,
                            type: .RESCHEDULE,
                            newDayOfWeek: slot.day,
                            newStartPeriod: slot.periods.first,
                            newEndPeriod: slot.periods.last,
                            newRoom: room
                        )
                    )
                    if !instRemark.isEmpty {
                        let nextNote = mergeWeeklyCourseNote(
                            existingNote: course.note,
                            week: week,
                            weekRemark: instRemark,
                            singleWeek: course.weeks == [week]
                        )
                        if nextNote != course.note {
                            var updated = course
                            updated.note = nextNote
                            changes.append(.modify(before: .course(course), after: .course(updated)))
                        }
                    }
                }
                continue
            }

            // Extra class on an existing slot with matching room & teacher -> add week to course.
            let slotCandidates = imported.filter { slotOf($0) == slot }
            if let matchedCourse = slotCandidates.first(where: { c in
                sameRoom(Set(parseRoomsOrdered(c.room)), roomSet)
                    && (teacher.isEmpty || c.teacher.isEmpty || c.teacher == teacher)
            }) {
                var updated = matchedCourse
                let addedWeek = !matchedCourse.weeks.contains(week)
                updated.weeks.insert(week)
                if !instRemark.isEmpty {
                    updated.note = mergeWeeklyCourseNote(
                        existingNote: matchedCourse.note,
                        week: week,
                        weekRemark: instRemark,
                        singleWeek: updated.weeks == [week]
                    )
                }
                if updated != matchedCourse {
                    changes.append(.modify(before: .course(matchedCourse), after: .course(updated)))
                    if addedWeek { extra += 1 }
                }
                continue
            }

            // Brand new extra session -> create single-week imported regular Course.
            let id = courseId(
                term: term,
                day: slot.day,
                periods: slot.periods,
                name: slot.name,
                teacher: teacher,
                room: room,
                splitByDetail: !slotCandidates.isEmpty
            )
            let existing = snapshot.courses.first(where: { $0.id == id })
            if let existing, existing.deletedAt != nil {
                continue // Never resurrect tombstones.
            }
            if existing == nil {
                let color = snapshot.courses.first(where: { $0.name == slot.name && $0.deletedAt == nil })?.colorIndex
                    ?? ((imported.map(\.colorIndex).max().map { $0 + 1 }) ?? 0) % paletteSize
                let added = Course(
                    id: id,
                    semesterId: semester.id,
                    name: slot.name,
                    teacher: teacher,
                    room: room,
                    dayOfWeek: slot.day,
                    startPeriod: slot.periods.first ?? 1,
                    endPeriod: slot.periods.last ?? 1,
                    weeks: [week],
                    colorIndex: color,
                    note: instRemark,
                    source: .IMPORT,
                    kind: .NORMAL
                )
                changes.append(.add(after: .course(added)))
                extra += 1
            }
        }

        // Remaining predicted slots missing from actual -> CANCEL.
        for slot in unmatchedPredicted {
            guard let course = predicted[slot] else { continue }
            let id = exceptionId(courseId: course.id, week: week)
            putException(CourseException(id: id, courseId: course.id, week: week, type: .CANCEL))
        }

        // Reconcile weekly jxfw exceptions (skip tombstones).
        let existingExceptions = snapshot.exceptions.filter { $0.id.hasPrefix(exceptionPrefix) && $0.week == week }
        for old in existingExceptions where old.deletedAt == nil && wantExceptions[old.id] == nil {
            changes.append(.remove(before: .courseException(old)))
        }
        for id in wantExceptionOrder {
            guard let want = wantExceptions[id] else { continue }
            if let old = existingExceptions.first(where: { $0.id == id }) {
                if old.deletedAt != nil { continue }
                var comparable = want
                comparable.updatedAt = old.updatedAt
                comparable.userId = old.userId
                if old != comparable {
                    changes.append(.modify(before: .courseException(old), after: .courseException(comparable)))
                }
            } else {
                changes.append(.add(after: .courseException(want)))
            }
        }

        if exams != nil {
            changes.append(
                contentsOf: syncExamCourses(
                    snapshot: snapshot,
                    semester: semester,
                    scopePrefix: "\(examCoursePrefix)\(term)/",
                    legacyTaskPrefix: examPrefix,
                    legacyCoursePrefix: "\(courseExamPrefix)\(term)/",
                    wantCourses: officialExamEntries.map(\.course)
                )
            )
        }
        let weekRemarkExams: [Course] = examSpecOrder.compactMap { slot in
            guard let spec = examSpecs[slot] else { return nil }
            let date = cal.dateOf(week: spec.week, dayOfWeek: spec.slot.day)
            if hasOfficialExamMatch(exams: exams, date: date, courseName: spec.slot.name) {
                return nil
            }
            return remarkExamCourse(term: term, semester: semester, spec: spec)
        }
        changes.append(
            contentsOf: syncExamCourses(
                snapshot: snapshot,
                semester: semester,
                scopePrefix: "\(remarkExamCoursePrefix)\(term)/\(week)/",
                legacyTaskPrefix: "\(courseExamPrefix)\(term)/\(week)/",
                legacyCoursePrefix: nil,
                wantCourses: weekRemarkExams
            )
        )

        return JxfwPlan(
            changeSet: ChangeSet(
                id: idGenerator(),
                description: "教务系统：第 \(week) 周更新",
                source: .IMPORT,
                createdAt: now,
                changes: changes
            ),
            courseCount: actualRooms.count,
            examCount: (exams?.count ?? 0) + weekRemarkExams.count,
            exceptionCount: wantExceptions.count,
            extraCount: extra
        )
    }

    // MARK: - Helpers

    private static func cleanTeachers(_ raw: String) -> [String] {
        let separators = CharacterSet(charactersIn: ",，;；、")
        var out: [String] = []
        for part in raw.components(separatedBy: separators) {
            let cleaned = part.replacingOccurrences(of: "[主讲]", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleaned.isEmpty, !out.contains(cleaned) {
                out.append(cleaned)
            }
        }
        return out
    }

    private static func cleanTeacher(_ raw: String) -> String {
        cleanTeachers(raw).joined(separator: "、")
    }

    private static func parseRoomsOrdered(_ raw: String) -> [String] {
        let separators = CharacterSet(charactersIn: ",，;；")
        var out: [String] = []
        for part in raw.components(separatedBy: separators) {
            let cleaned = part.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleaned.isEmpty, !out.contains(cleaned) {
                out.append(cleaned)
            }
        }
        return out
    }

    private static func appendUnique(_ items: [String], to target: inout [String]) {
        for item in items where !target.contains(item) {
            target.append(item)
        }
    }

    private static func mergeParts(_ a: String, _ b: String, separator: String) -> String {
        var seen = Set<String>()
        var out: [String] = []
        for raw in a.components(separatedBy: separator) + b.components(separatedBy: separator) {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, seen.insert(trimmed).inserted {
                out.append(trimmed)
            }
        }
        return out.joined(separator: separator)
    }

    private static func formatCourseRemarks(weeks: Set<Int>, remarksByWeek: [Int: String]) -> String {
        guard !remarksByWeek.isEmpty else { return "" }
        var orderedRemarks: [String] = []
        var weeksByRemark: [String: Set<Int>] = [:]
        for w in remarksByWeek.keys.sorted() {
            let r = (remarksByWeek[w] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !r.isEmpty else { continue }
            if weeksByRemark[r] == nil {
                orderedRemarks.append(r)
                weeksByRemark[r] = []
            }
            weeksByRemark[r]?.insert(w)
        }
        guard !orderedRemarks.isEmpty else { return "" }
        if orderedRemarks.count == 1, let only = orderedRemarks.first, weeksByRemark[only] == weeks {
            return only
        }
        return orderedRemarks.map { remark in
            let ws = weeksByRemark[remark] ?? []
            return "第\(WeekSets.format(ws))周：\(remark)"
        }.joined(separator: "\n")
    }

    private static func stripStaleExamRemarkLines(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let kept = trimmed
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { line in
                guard !line.isEmpty else { return false }
                let ns = line as NSString
                let range = NSRange(location: 0, length: ns.length)
                let isWeekRemark = weekRemarkLineRegex.firstMatch(in: line, range: range) != nil
                if isWeekRemark {
                    let content = CourseNotes.stripWeekPrefix(line)
                    return !isExamRemark(content)
                }
                return !isExamRemark(line)
            }
        return kept.joined(separator: "\n")
    }

    private static func mergeWeeklyCourseNote(
        existingNote: String,
        week: Int,
        weekRemark: String,
        singleWeek: Bool
    ) -> String {
        let imp = weekRemark.trimmingCharacters(in: .whitespacesAndNewlines)
        if imp.isEmpty { return existingNote }
        let cur = existingNote.trimmingCharacters(in: .whitespacesAndNewlines)
        let formattedLine = singleWeek ? imp : "第\(week)周：\(imp)"
        if cur.isEmpty { return formattedLine }
        if cur == imp { return existingNote }
        var lines = cur
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        var replaced = false
        var idx = 0
        while idx < lines.count {
            let line = lines[idx]
            if line == imp { return existingNote }
            let ns = line as NSString
            let range = NSRange(location: 0, length: ns.length)
            guard let m = weekRemarkLineRegex.firstMatch(in: line, range: range), m.numberOfRanges >= 3 else {
                idx += 1
                continue
            }
            let weekPart = ns.substring(with: m.range(at: 1))
            let textPart = ns.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
            let ws = WeekSets.parse(weekPart, total: maxWeeks)
            if ws.contains(week) && textPart == imp { return existingNote }
            if ws == [week] {
                lines[idx] = "第\(week)周：\(imp)"
                replaced = true
            } else if ws.contains(week) {
                var rest = ws
                rest.remove(week)
                lines[idx] = "第\(WeekSets.format(rest))周：\(textPart)"
                lines.insert("第\(week)周：\(imp)", at: idx + 1)
                replaced = true
                idx += 1
            }
            idx += 1
        }
        if replaced { return lines.joined(separator: "\n") }
        return "\(cur)\n\(formattedLine)"
    }

    private static func mergeCourseNote(existingNote: String, importedNote: String) -> String {
        let cur = existingNote.trimmingCharacters(in: .whitespacesAndNewlines)
        let imp = importedNote.trimmingCharacters(in: .whitespacesAndNewlines)
        if imp.isEmpty { return existingNote }
        if cur.isEmpty { return imp }
        if cur == imp || cur.contains(imp) { return existingNote }
        let curLines = cur
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let userLines = curLines.filter { line in
            let ns = line as NSString
            let range = NSRange(location: 0, length: ns.length)
            let isWeekRemark = weekRemarkLineRegex.firstMatch(in: line, range: range) != nil
            return !imp.contains(line) && !isWeekRemark
        }
        return (userLines + [imp]).joined(separator: "\n")
    }
}
