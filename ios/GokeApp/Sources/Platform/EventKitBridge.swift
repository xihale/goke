import Foundation
import GokeCore
import GokeShared
#if canImport(EventKit)
import EventKit
#endif

/// EventKit calendar permission status surfaced to UI & Reliability checks.
enum EventKitAuthorizationState: String, Sendable, Equatable {
    case notDetermined
    case fullAccess
    case writeOnly
    case denied
    case restricted
    case unavailable
}

/// Immutable, `Sendable` summary of an Apple Calendar (`EKCalendar`).
struct EventKitCalendarInfo: Sendable, Hashable, Identifiable {
    let id: String
    let title: String
    let sourceTitle: String
    let allowsContentModifications: Bool
    let isDefault: Bool

    init(
        id: String,
        title: String,
        sourceTitle: String,
        allowsContentModifications: Bool,
        isDefault: Bool
    ) {
        self.id = id
        self.title = title
        self.sourceTitle = sourceTitle
        self.allowsContentModifications = allowsContentModifications
        self.isDefault = isDefault
    }
}

/// Domain entity kind linked with an Apple Calendar `EKEvent`.
enum EventKitSyncEntityKind: String, Codable, Sendable, Hashable {
    case event
    case task
    case course
}

/// Local-only bidirectional mapping stored in `UserDefaults` (excluded from Goke cloud sync).
struct EventKitSyncLink: Codable, Sendable, Hashable {
    let gokeId: String
    let ekEventIdentifier: String
    let ekExternalIdentifier: String?
    let calendarIdentifier: String
    let kind: EventKitSyncEntityKind
    let lastSyncedAtMillis: Int64

    init(
        gokeId: String,
        ekEventIdentifier: String,
        ekExternalIdentifier: String?,
        calendarIdentifier: String,
        kind: EventKitSyncEntityKind,
        lastSyncedAtMillis: Int64
    ) {
        self.gokeId = gokeId
        self.ekEventIdentifier = ekEventIdentifier
        self.ekExternalIdentifier = ekExternalIdentifier
        self.calendarIdentifier = calendarIdentifier
        self.kind = kind
        self.lastSyncedAtMillis = lastSyncedAtMillis
    }
}

/// Result of a bidirectional EventKit sync pass.
/// Callers commit `changeSet` through `RepositoryActor` / `ActionDispatcher` to preserve undo & cloud sync.
struct EventKitSyncResult: Sendable, Equatable {
    let changeSet: ChangeSet
    let pushedAdded: Int
    let pushedUpdated: Int
    let pushedDeleted: Int
    let pulledAdded: Int
    let pulledUpdated: Int
    let pulledRemoved: Int

    init(
        changeSet: ChangeSet,
        pushedAdded: Int,
        pushedUpdated: Int,
        pushedDeleted: Int,
        pulledAdded: Int,
        pulledUpdated: Int,
        pulledRemoved: Int
    ) {
        self.changeSet = changeSet
        self.pushedAdded = pushedAdded
        self.pushedUpdated = pushedUpdated
        self.pushedDeleted = pushedDeleted
        self.pulledAdded = pulledAdded
        self.pulledUpdated = pulledUpdated
        self.pulledRemoved = pulledRemoved
    }
}

enum EventKitBridgeError: Error, Sendable, Equatable, LocalizedError {
    case unavailable
    case permissionDenied
    case noWritableCalendar
    case calendarNotFound(String)

    var errorDescription: String? {
        switch self {
        case .unavailable:
            "当前平台不支持系统日历同步。"
        case .permissionDenied:
            "未获得系统日历完全访问权限，请在系统设置中授权。"
        case .noWritableCalendar:
            "未找到可写入的系统日历账户。"
        case .calendarNotFound(let id):
            "找不到指定的系统日历 (\(id))。"
        }
    }
}

/// Encodes and extracts structured Goke sync tags embedded inside `EKEvent.notes`.
/// Format: `[goke:<kind>:<id>]` (e.g. `[goke:event:abc123]`).
enum EventKitSyncTag {
    struct Parsed: Sendable, Equatable {
        let kind: EventKitSyncEntityKind
        let gokeId: String
    }

    private static let tagRegex = try! NSRegularExpression(
        pattern: "\\[goke:(event|task|course):([^\\]\\s]+)\\]"
    )

    static func encode(kind: EventKitSyncEntityKind, gokeId: String) -> String {
        "[goke:\(kind.rawValue):\(gokeId)]"
    }

    static func parse(from notes: String?) -> Parsed? {
        guard let notes, !notes.isEmpty else { return nil }
        let range = NSRange(notes.startIndex..., in: notes)
        guard let match = tagRegex.firstMatch(in: notes, range: range),
              let kindRange = Range(match.range(at: 1), in: notes),
              let idRange = Range(match.range(at: 2), in: notes),
              let kind = EventKitSyncEntityKind(rawValue: String(notes[kindRange]))
        else {
            return nil
        }
        return Parsed(kind: kind, gokeId: String(notes[idRange]))
    }

    /// Removes any existing `[goke:...]` tag lines from user notes.
    static func strip(from notes: String?) -> String {
        guard let notes, !notes.isEmpty else { return "" }
        let range = NSRange(notes.startIndex..., in: notes)
        let cleaned = tagRegex.stringByReplacingMatches(in: notes, range: range, withTemplate: "")
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Appends a single canonical `[goke:<kind>:<id>]` tag to `userNote`.
    static func attach(to userNote: String, kind: EventKitSyncEntityKind, gokeId: String) -> String {
        let clean = strip(from: userNote)
        let tag = encode(kind: kind, gokeId: gokeId)
        return clean.isEmpty ? tag : "\(clean)\n\n\(tag)"
    }
}

/// Actor-isolated bidirectional bridge between Goke (`Event`, `GokeTask` blocks, `Course` export) and Apple Calendar (`EventKit`).
actor EventKitBridge {
    static let shared = EventKitBridge()
    static let linksStorageKey = "app.goke.ios.eventkit.links.v1"
    static let gokeCalendarIdKey = "app.goke.ios.eventkit.gokeCalendarId.v1"

    #if canImport(EventKit)
    private var _eventStore: EKEventStore?
    private var eventStore: EKEventStore {
        if let existing = _eventStore {
            return existing
        }
        let created = EKEventStore()
        _eventStore = created
        return created
    }
    #endif
    private let container: AppGroupContainer

    init(container: AppGroupContainer = .shared) {
        self.container = container
    }

    // MARK: - Permissions & Calendars

    func authorizationStatus() -> EventKitAuthorizationState {
        #if canImport(EventKit)
        switch EKEventStore.authorizationStatus(for: .event) {
        case .notDetermined: return .notDetermined
        case .fullAccess: return .fullAccess
        case .writeOnly: return .writeOnly
        case .denied: return .denied
        case .restricted: return .restricted
        @unknown default: return .denied
        }
        #else
        return .unavailable
        #endif
    }

    /// Requests iOS 17+ full calendar access (`requestFullAccessToEvents()`).
    @discardableResult
    func requestFullAccess() async throws -> Bool {
        #if canImport(EventKit)
        return try await eventStore.requestFullAccessToEvents()
        #else
        throw EventKitBridgeError.unavailable
        #endif
    }

    /// Lists all Apple Calendars visible to EventKit.
    func listCalendars() -> [EventKitCalendarInfo] {
        #if canImport(EventKit)
        let defaultId = eventStore.defaultCalendarForNewEvents?.calendarIdentifier
        return eventStore.calendars(for: .event)
            .map { cal in
                EventKitCalendarInfo(
                    id: cal.calendarIdentifier,
                    title: cal.title,
                    sourceTitle: cal.source?.title ?? "",
                    allowsContentModifications: cal.allowsContentModifications,
                    isDefault: cal.calendarIdentifier == defaultId
                )
            }
            .sorted { lhs, rhs in
                if lhs.isDefault != rhs.isDefault { return lhs.isDefault }
                return lhs.title.localizedStandardCompare(rhs.title) == .orderedAscending
            }
        #else
        return []
        #endif
    }

    /// Finds or creates a dedicated writable "Goke" calendar in the user's primary calendar source.
    func ensureGokeCalendar(title: String = "Goke") throws -> EventKitCalendarInfo {
        #if canImport(EventKit)
        guard authorizationStatus() == .fullAccess else {
            throw EventKitBridgeError.permissionDenied
        }
        let defaults = container.userDefaults
        if let savedId = defaults.string(forKey: Self.gokeCalendarIdKey),
           let existing = eventStore.calendar(withIdentifier: savedId),
           existing.allowsContentModifications {
            return makeInfo(existing)
        }
        if let byTitle = eventStore.calendars(for: .event).first(where: {
            $0.title == title && $0.allowsContentModifications
        }) {
            defaults.set(byTitle.calendarIdentifier, forKey: Self.gokeCalendarIdKey)
            return makeInfo(byTitle)
        }

        guard let source = preferredWritableSource() else {
            throw EventKitBridgeError.noWritableCalendar
        }
        let calendar = EKCalendar(for: .event, eventStore: eventStore)
        calendar.title = title
        calendar.source = source
        try eventStore.saveCalendar(calendar, commit: true)
        defaults.set(calendar.calendarIdentifier, forKey: Self.gokeCalendarIdKey)
        return makeInfo(calendar)
        #else
        throw EventKitBridgeError.unavailable
        #endif
    }

    /// Streams external Apple Calendar change notifications (`.EKEventStoreChanged`).
    nonisolated func storeChangedStream() -> AsyncStream<Void> {
        AsyncStream { continuation in
            #if canImport(EventKit)
            let task = Task {
                for await _ in NotificationCenter.default.notifications(named: .EKEventStoreChanged) {
                    continuation.yield(())
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
            #else
            continuation.finish()
            #endif
        }
    }

    // MARK: - Bidirectional Sync

    /// Synchronizes Goke `Event`s and scheduled `GokeTask` time blocks with Apple Calendar.
    /// Returns a `ChangeSet` containing Apple Calendar changes to commit into `RepositoryActor`.
    func syncBidirectional(
        snapshot: AppSnapshot,
        sourceCalendarIds: [String],
        targetCalendarId: String? = nil,
        defaultBlockMinutes: Int = 30,
        windowPastDays: Int = 30,
        windowFutureDays: Int = 180,
        now: LocalDateTime = .now(),
        timeZone: TimeZone = .current
    ) async throws -> EventKitSyncResult {
        #if canImport(EventKit)
        guard authorizationStatus() == .fullAccess else {
            throw EventKitBridgeError.permissionDenied
        }

        let targetCalendar: EKCalendar
        if let targetCalendarId {
            guard let found = eventStore.calendar(withIdentifier: targetCalendarId),
                  found.allowsContentModifications
            else {
                throw EventKitBridgeError.calendarNotFound(targetCalendarId)
            }
            targetCalendar = found
        } else if let def = eventStore.defaultCalendarForNewEvents, def.allowsContentModifications {
            targetCalendar = def
        } else {
            let created = try ensureGokeCalendar()
            guard let found = eventStore.calendar(withIdentifier: created.id) else {
                throw EventKitBridgeError.noWritableCalendar
            }
            targetCalendar = found
        }

        let windowStart = now.date.addingDays(-windowPastDays).atStartOfDay().toDate(in: timeZone)
        let windowEnd = now.date.addingDays(windowFutureDays).atTime(.endOfDay).toDate(in: timeZone)

        var selectedCalendars: [EKCalendar] = sourceCalendarIds.compactMap {
            eventStore.calendar(withIdentifier: $0)
        }
        if !selectedCalendars.contains(where: { $0.calendarIdentifier == targetCalendar.calendarIdentifier }) {
            selectedCalendars.append(targetCalendar)
        }

        let predicate = eventStore.predicateForEvents(
            withStart: windowStart,
            end: windowEnd,
            calendars: selectedCalendars
        )
        let ekEvents = eventStore.events(matching: predicate)

        var ekById: [String: EKEvent] = [:]
        var ekByTagGokeId: [String: EKEvent] = [:]
        for ek in ekEvents {
            if let eid = ek.eventIdentifier {
                ekById[eid] = ek
            }
            if let parsed = EventKitSyncTag.parse(from: ek.notes) {
                ekByTagGokeId[parsed.gokeId] = ek
            }
        }

        var links = loadLinks()
        let live = snapshot.live()
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)

        var pushedAdded = 0
        var pushedUpdated = 0
        var pushedDeleted = 0
        var pulledAdded = 0
        var pulledUpdated = 0
        var pulledRemoved = 0
        var changes: [Change] = []
        var activeGokeIds = Set<String>()
        var dirtyCalendar = false

        // 1. Push / Reconcile Goke Events <-> Apple Calendar
        for gokeEvent in live.events {
            activeGokeIds.insert(gokeEvent.id)
            var existingEK: EKEvent?
            if let link = links[gokeEvent.id] {
                existingEK = ekById[link.ekEventIdentifier] ?? eventStore.event(withIdentifier: link.ekEventIdentifier)
            }
            if existingEK == nil {
                existingEK = ekByTagGokeId[gokeEvent.id]
            }

            if let ek = existingEK {
                let ekModifiedMillis = Int64((ek.lastModifiedDate?.timeIntervalSince1970 ?? 0) * 1000)
                let lastSynced = links[gokeEvent.id]?.lastSyncedAtMillis ?? 0
                if ekModifiedMillis > max(gokeEvent.updatedAt, lastSynced + 1000) {
                    // External edit in Apple Calendar wins (LWW) -> pull into Goke ChangeSet.
                    let pulled = mapEKEventToGokeEvent(ek, existing: gokeEvent, timeZone: timeZone)
                    if !Record.event(gokeEvent).hasSameContent(as: .event(pulled)) {
                        changes.append(.modify(before: .event(gokeEvent), after: .event(pulled)))
                        pulledUpdated += 1
                    }
                    links[gokeEvent.id] = makeLink(gokeId: gokeEvent.id, ek: ek, kind: .event, syncedAt: nowMillis)
                } else if needsCalendarUpdate(ek: ek, event: gokeEvent, timeZone: timeZone) {
                    applyGokeEvent(gokeEvent, to: ek, timeZone: timeZone)
                    try eventStore.save(ek, span: .thisEvent, commit: false)
                    dirtyCalendar = true
                    pushedUpdated += 1
                    links[gokeEvent.id] = makeLink(gokeId: gokeEvent.id, ek: ek, kind: .event, syncedAt: nowMillis)
                } else {
                    links[gokeEvent.id] = makeLink(gokeId: gokeEvent.id, ek: ek, kind: .event, syncedAt: nowMillis)
                }
            } else if links[gokeEvent.id] != nil && gokeEvent.source == .IMPORT {
                // Previously imported from Apple Calendar and now deleted in Apple Calendar.
                changes.append(.remove(before: .event(gokeEvent)))
                links.removeValue(forKey: gokeEvent.id)
                pulledRemoved += 1
            } else {
                let ek = EKEvent(eventStore: eventStore)
                ek.calendar = targetCalendar
                applyGokeEvent(gokeEvent, to: ek, timeZone: timeZone)
                try eventStore.save(ek, span: .thisEvent, commit: false)
                dirtyCalendar = true
                pushedAdded += 1
                links[gokeEvent.id] = makeLink(gokeId: gokeEvent.id, ek: ek, kind: .event, syncedAt: nowMillis)
            }
        }

        // 2. Push scheduled GokeTask time blocks -> Apple Calendar
        for task in TaskLogic.topLevel(live.tasks) where !task.done {
            guard let blockStart = task.blockStart,
                  let blockEnd = TaskLogic.blockEnd(task: task, defaultMinutes: defaultBlockMinutes)
            else { continue }
            activeGokeIds.insert(task.id)
            var existingEK: EKEvent?
            if let link = links[task.id] {
                existingEK = ekById[link.ekEventIdentifier] ?? eventStore.event(withIdentifier: link.ekEventIdentifier)
            }
            if existingEK == nil {
                existingEK = ekByTagGokeId[task.id]
            }

            if let ek = existingEK {
                if needsCalendarUpdate(ek: ek, task: task, start: blockStart, end: blockEnd, timeZone: timeZone) {
                    applyGokeTaskBlock(task, start: blockStart, end: blockEnd, to: ek, timeZone: timeZone)
                    try eventStore.save(ek, span: .thisEvent, commit: false)
                    dirtyCalendar = true
                    pushedUpdated += 1
                }
                links[task.id] = makeLink(gokeId: task.id, ek: ek, kind: .task, syncedAt: nowMillis)
            } else {
                let ek = EKEvent(eventStore: eventStore)
                ek.calendar = targetCalendar
                applyGokeTaskBlock(task, start: blockStart, end: blockEnd, to: ek, timeZone: timeZone)
                try eventStore.save(ek, span: .thisEvent, commit: false)
                dirtyCalendar = true
                pushedAdded += 1
                links[task.id] = makeLink(gokeId: task.id, ek: ek, kind: .task, syncedAt: nowMillis)
            }
        }

        // 3. Remove Apple Calendar entries for deleted/completed Goke items
        let staleLinks = links.values.filter { link in
            (link.kind == .event || link.kind == .task) && !activeGokeIds.contains(link.gokeId)
        }
        for link in staleLinks {
            if let ek = ekById[link.ekEventIdentifier] ?? eventStore.event(withIdentifier: link.ekEventIdentifier),
               ek.calendar?.allowsContentModifications == true {
                try? eventStore.remove(ek, span: .thisEvent, commit: false)
                dirtyCalendar = true
                pushedDeleted += 1
            }
            links.removeValue(forKey: link.gokeId)
        }

        // 4. Pull newly created external Apple Calendar events -> Goke Events
        let linkedEkIds = Set(links.values.map(\.ekEventIdentifier))
        let sourceIdSet = Set(sourceCalendarIds)
        for ek in ekEvents {
            guard let calId = ek.calendar?.calendarIdentifier,
                  sourceIdSet.contains(calId),
                  let ekId = ek.eventIdentifier,
                  !linkedEkIds.contains(ekId),
                  EventKitSyncTag.parse(from: ek.notes) == nil
            else { continue }

            let imported = mapEKEventToGokeEvent(ek, existing: nil, timeZone: timeZone)
            changes.append(.add(after: .event(imported)))
            links[imported.id] = makeLink(gokeId: imported.id, ek: ek, kind: .event, syncedAt: nowMillis)
            pulledAdded += 1
        }

        if dirtyCalendar {
            try eventStore.commit()
        }
        saveLinks(links)

        let cs = ChangeSet(
            description: "系统日历同步",
            source: .IMPORT,
            createdAt: nowMillis,
            changes: changes
        )
        return EventKitSyncResult(
            changeSet: cs,
            pushedAdded: pushedAdded,
            pushedUpdated: pushedUpdated,
            pushedDeleted: pushedDeleted,
            pulledAdded: pulledAdded,
            pulledUpdated: pulledUpdated,
            pulledRemoved: pulledRemoved
        )
        #else
        throw EventKitBridgeError.unavailable
        #endif
    }

    /// Exports all course occurrences of `semester` (or current semester) into the target Apple Calendar.
    @discardableResult
    func exportCourses(
        snapshot: AppSnapshot,
        semester: Semester? = nil,
        toCalendarId: String? = nil,
        timeZone: TimeZone = .current
    ) async throws -> Int {
        #if canImport(EventKit)
        guard authorizationStatus() == .fullAccess else {
            throw EventKitBridgeError.permissionDenied
        }
        let live = snapshot.live()
        guard let sem = semester ?? live.currentSemester else { return 0 }

        let targetCalendar: EKCalendar
        if let toCalendarId, let found = eventStore.calendar(withIdentifier: toCalendarId), found.allowsContentModifications {
            targetCalendar = found
        } else {
            let gokeCal = try ensureGokeCalendar()
            guard let found = eventStore.calendar(withIdentifier: gokeCal.id) else {
                throw EventKitBridgeError.noWritableCalendar
            }
            targetCalendar = found
        }

        let cal = SemesterCalendar(semester: sem)
        let occurrences = CourseExpander.occurrences(
            semester: sem,
            courses: live.courses,
            exceptions: live.exceptions,
            from: cal.weekStart(1),
            to: cal.weekEnd(max(1, sem.totalWeeks))
        )

        var links = loadLinks()
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        var exportedCount = 0

        for occ in occurrences {
            let linkKey = "course:\(occ.id)"
            let ek = links[linkKey].flatMap { eventStore.event(withIdentifier: $0.ekEventIdentifier) }
                ?? EKEvent(eventStore: eventStore)
            ek.calendar = targetCalendar
            ek.title = occ.course.name
            ek.location = occ.room.isEmpty ? nil : occ.room
            ek.startDate = occ.start.toDate(in: timeZone)
            ek.endDate = occ.end.toDate(in: timeZone)
            ek.isAllDay = false
            let noteBody = occ.course.teacher.isEmpty ? occ.course.note : "教师：\(occ.course.teacher)\n\(occ.course.note)"
            ek.notes = EventKitSyncTag.attach(to: noteBody, kind: .course, gokeId: occ.id)
            if let remind = occ.course.remindMinutes, remind >= 0 {
                ek.alarms = [EKAlarm(relativeOffset: -TimeInterval(remind * 60))]
            }
            try eventStore.save(ek, span: .thisEvent, commit: false)
            links[linkKey] = makeLink(gokeId: linkKey, ek: ek, kind: .course, syncedAt: nowMillis)
            exportedCount += 1
        }

        if exportedCount > 0 {
            try eventStore.commit()
            saveLinks(links)
        }
        return exportedCount
        #else
        throw EventKitBridgeError.unavailable
        #endif
    }

    // MARK: - Link Persistence & Mapping Helpers

    func loadLinks() -> [String: EventKitSyncLink] {
        guard let data = container.userDefaults.data(forKey: Self.linksStorageKey),
              let decoded = try? MintJSON.makeDecoder().decode([String: EventKitSyncLink].self, from: data)
        else {
            return [:]
        }
        return decoded
    }

    func clearLinks() {
        container.userDefaults.removeObject(forKey: Self.linksStorageKey)
    }

    private func saveLinks(_ links: [String: EventKitSyncLink]) {
        guard let data = try? MintJSON.makeEncoder().encode(links) else { return }
        container.userDefaults.set(data, forKey: Self.linksStorageKey)
    }

    #if canImport(EventKit)
    private func makeInfo(_ cal: EKCalendar) -> EventKitCalendarInfo {
        let defaultId = eventStore.defaultCalendarForNewEvents?.calendarIdentifier
        return EventKitCalendarInfo(
            id: cal.calendarIdentifier,
            title: cal.title,
            sourceTitle: cal.source?.title ?? "",
            allowsContentModifications: cal.allowsContentModifications,
            isDefault: cal.calendarIdentifier == defaultId
        )
    }

    private func preferredWritableSource() -> EKSource? {
        if let defaultSource = eventStore.defaultCalendarForNewEvents?.source {
            return defaultSource
        }
        return eventStore.sources.first(where: { $0.sourceType == .calDAV })
            ?? eventStore.sources.first(where: { $0.sourceType == .local })
            ?? eventStore.sources.first
    }

    private func makeLink(
        gokeId: String,
        ek: EKEvent,
        kind: EventKitSyncEntityKind,
        syncedAt: Int64
    ) -> EventKitSyncLink {
        EventKitSyncLink(
            gokeId: gokeId,
            ekEventIdentifier: ek.eventIdentifier ?? "",
            ekExternalIdentifier: ek.calendarItemExternalIdentifier,
            calendarIdentifier: ek.calendar?.calendarIdentifier ?? "",
            kind: kind,
            lastSyncedAtMillis: syncedAt
        )
    }

    private func applyGokeEvent(_ event: Event, to ek: EKEvent, timeZone: TimeZone) {
        ek.title = event.title
        ek.startDate = event.start.toDate(in: timeZone)
        ek.endDate = event.end.toDate(in: timeZone)
        ek.isAllDay = event.allDay
        ek.location = event.location.isEmpty ? nil : event.location
        ek.notes = EventKitSyncTag.attach(to: event.note, kind: .event, gokeId: event.id)
        if let remind = event.remindMinutes, remind >= 0 {
            ek.alarms = [EKAlarm(relativeOffset: -TimeInterval(remind * 60))]
        } else {
            ek.alarms = nil
        }
    }

    private func needsCalendarUpdate(ek: EKEvent, event: Event, timeZone: TimeZone) -> Bool {
        let start = event.start.toDate(in: timeZone)
        let end = event.end.toDate(in: timeZone)
        let expectedNotes = EventKitSyncTag.attach(to: event.note, kind: .event, gokeId: event.id)
        return ek.title != event.title
            || abs(ek.startDate.timeIntervalSince(start)) > 1
            || abs(ek.endDate.timeIntervalSince(end)) > 1
            || ek.isAllDay != event.allDay
            || (ek.location ?? "") != event.location
            || (ek.notes ?? "") != expectedNotes
    }

    private func applyGokeTaskBlock(
        _ task: GokeTask,
        start: LocalDateTime,
        end: LocalDateTime,
        to ek: EKEvent,
        timeZone: TimeZone
    ) {
        ek.title = task.title
        ek.startDate = start.toDate(in: timeZone)
        ek.endDate = end.toDate(in: timeZone)
        ek.isAllDay = false
        ek.notes = EventKitSyncTag.attach(to: task.note, kind: .task, gokeId: task.id)
        if let remind = task.remindMinutes, remind >= 0 {
            ek.alarms = [EKAlarm(relativeOffset: -TimeInterval(remind * 60))]
        } else {
            ek.alarms = nil
        }
    }

    private func needsCalendarUpdate(
        ek: EKEvent,
        task: GokeTask,
        start: LocalDateTime,
        end: LocalDateTime,
        timeZone: TimeZone
    ) -> Bool {
        let s = start.toDate(in: timeZone)
        let e = end.toDate(in: timeZone)
        let expectedNotes = EventKitSyncTag.attach(to: task.note, kind: .task, gokeId: task.id)
        return ek.title != task.title
            || abs(ek.startDate.timeIntervalSince(s)) > 1
            || abs(ek.endDate.timeIntervalSince(e)) > 1
            || ek.isAllDay
            || (ek.notes ?? "") != expectedNotes
    }

    private func mapEKEventToGokeEvent(
        _ ek: EKEvent,
        existing: Event?,
        timeZone: TimeZone
    ) -> Event {
        let start = LocalDateTime.fromDate(ek.startDate, in: timeZone)
        let end = LocalDateTime.fromDate(ek.endDate, in: timeZone)
        let cleanNote = EventKitSyncTag.strip(from: ek.notes)
        let remindMinutes: Int? = ek.alarms?.first.map { max(0, Int(-$0.relativeOffset / 60)) }
        let deterministicId = existing?.id ?? "ek-\(ek.calendarItemExternalIdentifier ?? ek.eventIdentifier ?? UUID().uuidString.lowercased())"
        return Event(
            id: deterministicId,
            title: ek.title?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? (ek.title ?? "日历日程") : "日历日程",
            start: start,
            end: end < start ? start : end,
            allDay: ek.isAllDay,
            location: ek.location ?? "",
            note: cleanNote,
            colorIndex: existing?.colorIndex ?? 4,
            category: ek.calendar?.title ?? "",
            remindMinutes: remindMinutes ?? existing?.remindMinutes,
            recurrence: existing?.recurrence,
            kind: existing?.kind ?? .NORMAL,
            source: existing?.source ?? .IMPORT,
            updatedAt: existing?.updatedAt ?? 0,
            deletedAt: nil,
            userId: existing?.userId
        )
    }
    #endif
}
