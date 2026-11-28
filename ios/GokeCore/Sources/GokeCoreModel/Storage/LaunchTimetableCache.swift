import Foundation

/// Compact binary-plist cache of the active semester and nearby timetable items (~5–15 KB).
///
/// Read synchronously during cold launch via `.mappedIfSafe` (~1–3 ms) so Frame 1 paints the real
/// current-week Timetable before SQLite opens or full JSON deserialization completes.
public struct LaunchTimetableCache: Codable, Sendable, Equatable {
    public static let currentSchemaVersion = 1
    public static let defaultFileName = "launch_timetable_cache.plist"
    public static let defaultWindowDays = 14
    public static let defaultMaxAgeMillis: Int64 = 7 * 86_400_000
    public static let appGroupIdentifier = "group.app.goke.shared"

    public let schemaVersion: Int
    public let savedAt: Int64
    public let activeSemester: Semester?
    /// Live courses belonging to `activeSemester`.
    public let courses: [Course]
    /// Live exceptions belonging to `courses`.
    public let exceptions: [CourseException]
    /// Live timed events overlapping `[today - windowDays, today + windowDays]` or repeating.
    public let events: [Event]
    /// Live top-level tasks with `blockStart != nil` in `±windowDays` or repeating.
    public let blockTasks: [GokeTask]
    /// Live task lists (needed by `TimetableLayout` for task-block colors).
    public let lists: [TaskList]

    public init(
        schemaVersion: Int = LaunchTimetableCache.currentSchemaVersion,
        savedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        activeSemester: Semester?,
        courses: [Course] = [],
        exceptions: [CourseException] = [],
        events: [Event] = [],
        blockTasks: [GokeTask] = [],
        lists: [TaskList] = []
    ) {
        self.schemaVersion = schemaVersion
        self.savedAt = savedAt
        self.activeSemester = activeSemester
        self.courses = courses
        self.exceptions = exceptions
        self.events = events
        self.blockTasks = blockTasks
        self.lists = lists
    }

    /// Converts this compact cache payload into a minimal live `AppSnapshot` ready for Frame-1 Timetable rendering.
    public func toPartialSnapshot() -> AppSnapshot {
        AppSnapshot(
            semesters: activeSemester.map { [$0] } ?? [],
            courses: courses,
            exceptions: exceptions,
            events: events,
            tasks: blockTasks,
            lists: lists
        )
    }

    /// Returns `true` when `savedAt` is older than (or skewed ahead by more than) `maxAgeMillis` (default: 7 days),
    /// preventing stale ±14-day windows from flashing outdated events/blocks after long idle gaps.
    public func isExpired(
        now: Date = Date(),
        maxAgeMillis: Int64 = LaunchTimetableCache.defaultMaxAgeMillis
    ) -> Bool {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        return abs(nowMillis - savedAt) > maxAgeMillis
    }

    /// Compares all rendered payload content while ignoring `savedAt` timestamp churn.
    public func contentEquals(_ other: LaunchTimetableCache) -> Bool {
        schemaVersion == other.schemaVersion
            && activeSemester == other.activeSemester
            && courses == other.courses
            && exceptions == other.exceptions
            && events == other.events
            && blockTasks == other.blockTasks
            && lists == other.lists
    }

    /// Builds a minimal `LaunchTimetableCache` payload from a full `AppSnapshot`.
    public static func buildPayload(
        from snapshot: AppSnapshot,
        now: Date = Date(),
        timeZone: TimeZone = .current,
        windowDays: Int = LaunchTimetableCache.defaultWindowDays
    ) -> LaunchTimetableCache {
        let live = snapshot.live()
        let activeSem = live.currentSemester
        let savedAt = Int64(now.timeIntervalSince1970 * 1000)
        let today = LocalDateTime.fromDate(now, in: timeZone).date
        let windowStart = today.addingDays(-windowDays)
        let windowEnd = today.addingDays(windowDays)

        let activeCourses: [Course]
        let activeExceptions: [CourseException]
        if let activeSem {
            activeCourses = live.courses.filter { $0.semesterId == activeSem.id }
            let courseIds = Set(activeCourses.map(\.id))
            activeExceptions = live.exceptions.filter { courseIds.contains($0.courseId) }
        } else {
            activeCourses = []
            activeExceptions = []
        }

        let activeEvents = live.events.filter { ev in
            guard !ev.allDay else { return false }
            if let rec = ev.recurrence {
                if let until = rec.until, until < windowStart { return false }
                return ev.start.date <= windowEnd
            }
            return ev.end.date >= windowStart && ev.start.date <= windowEnd
        }

        var blockTasks: [GokeTask] = []
        for task in live.tasks where task.parentId == nil {
            if TimetableLayout.isParcelTask(task) {
                continue
            }

            guard let blockStart = task.blockStart else { continue }
            if let rec = task.repeat {
                if let until = rec.until, until < windowStart { continue }
                if blockStart.date <= windowEnd {
                    blockTasks.append(task)
                }
            } else if blockStart.date >= windowStart && blockStart.date <= windowEnd {
                blockTasks.append(task)
            }
        }

        return LaunchTimetableCache(
            schemaVersion: currentSchemaVersion,
            savedAt: savedAt,
            activeSemester: activeSem,
            courses: activeCourses,
            exceptions: activeExceptions,
            events: activeEvents,
            blockTasks: blockTasks,
            lists: live.lists
        )
    }

    /// Resolves the default cache URL inside the App Group container (supporting rewritten sideload App Group IDs
    /// from `embedded.mobileprovision`, and falling back to `Application Support/GokeData`).
    public static func defaultURL(fileManager: FileManager = .default) -> URL {
        #if os(iOS) || os(macOS) || os(tvOS) || os(watchOS) || os(visionOS)
        if let groupURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) {
            return groupURL.appendingPathComponent(defaultFileName, isDirectory: false)
        }
        if let profileURL = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
           let data = try? Data(contentsOf: profileURL),
           let start = data.range(of: Data("<plist".utf8)),
           let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound..<data.endIndex),
           let plist = try? PropertyListSerialization.propertyList(
               from: data.subdata(in: start.lowerBound..<end.upperBound),
               options: [],
               format: nil
           ) as? [String: Any],
           let entitlements = plist["Entitlements"] as? [String: Any],
           let groups = entitlements["com.apple.security.application-groups"] as? [String]
        {
            for groupId in groups where !groupId.isEmpty && groupId != appGroupIdentifier {
                if let groupURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: groupId) {
                    return groupURL.appendingPathComponent(defaultFileName, isDirectory: false)
                }
            }
        }
        #endif
        let fallbackBase =
            fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return fallbackBase
            .appendingPathComponent("GokeData", isDirectory: true)
            .appendingPathComponent(defaultFileName, isDirectory: false)
    }

    /// Encodes a payload as a compact binary property list.
    public static func encodeBinary(_ payload: LaunchTimetableCache) throws -> Data {
        let encoder = PropertyListEncoder()
        encoder.outputFormat = .binary
        return try encoder.encode(payload)
    }

    /// Decodes a binary property list payload.
    public static func decodeBinary(_ data: Data) throws -> LaunchTimetableCache {
        try PropertyListDecoder().decode(LaunchTimetableCache.self, from: data)
    }

    /// Ultra-fast synchronous read using `.mappedIfSafe`, returning the raw `LaunchTimetableCache` if schema matches
    /// and the payload is within `maxAgeMillis` (default: 7 days) of `now`.
    public static func loadPayloadSync(
        from url: URL = LaunchTimetableCache.defaultURL(),
        now: Date = Date(),
        maxAgeMillis: Int64 = LaunchTimetableCache.defaultMaxAgeMillis
    ) -> LaunchTimetableCache? {
        guard let data = try? Data(contentsOf: url, options: .mappedIfSafe), !data.isEmpty else {
            return nil
        }
        guard let payload = try? decodeBinary(data),
              payload.schemaVersion == currentSchemaVersion,
              !payload.isExpired(now: now, maxAgeMillis: maxAgeMillis)
        else {
            return nil
        }
        return payload
    }

    /// Ultra-fast (~1–3 ms) synchronous read returning a minimal `AppSnapshot` seeded with active timetable records.
    /// Returns `nil` when the cache file is missing, invalid, schema-mismatched, expired, or has no active semester.
    public static func loadSync(
        from url: URL = LaunchTimetableCache.defaultURL(),
        now: Date = Date(),
        maxAgeMillis: Int64 = LaunchTimetableCache.defaultMaxAgeMillis
    ) -> AppSnapshot? {
        guard let payload = loadPayloadSync(from: url, now: now, maxAgeMillis: maxAgeMillis),
              payload.activeSemester != nil
        else {
            return nil
        }
        return payload.toPartialSnapshot()
    }

    /// Synchronously encodes and atomically writes a pre-built cache payload.
    public static func savePayloadSync(
        _ payload: LaunchTimetableCache,
        to url: URL = LaunchTimetableCache.defaultURL()
    ) throws {
        let parentURL = url.deletingLastPathComponent()
        let fm = FileManager.default
        if !fm.fileExists(atPath: parentURL.path) {
            try fm.createDirectory(at: parentURL, withIntermediateDirectories: true)
        }
        let data = try encodeBinary(payload)
        try data.write(to: url, options: .atomic)
    }

    /// Synchronously builds and atomically writes the launch cache from `snapshot`.
    public static func saveSync(
        from snapshot: AppSnapshot,
        to url: URL = LaunchTimetableCache.defaultURL(),
        now: Date = Date(),
        timeZone: TimeZone = .current
    ) throws {
        let payload = buildPayload(from: snapshot, now: now, timeZone: timeZone)
        if payload.activeSemester == nil {
            invalidate(at: url)
            return
        }
        try savePayloadSync(payload, to: url)
    }

    /// Builds and atomically writes the launch cache off the main thread at `.utility` priority.
    @discardableResult
    public static func saveAsync(
        from snapshot: AppSnapshot,
        to url: URL = LaunchTimetableCache.defaultURL(),
        now: Date = Date(),
        timeZone: TimeZone = .current
    ) -> Task<Void, Never> {
        Task.detached(priority: .utility) {
            try? saveSync(from: snapshot, to: url, now: now, timeZone: timeZone)
        }
    }

    /// Removes the on-disk launch cache file if present.
    public static func invalidate(at url: URL = LaunchTimetableCache.defaultURL()) {
        try? FileManager.default.removeItem(at: url)
    }
}
