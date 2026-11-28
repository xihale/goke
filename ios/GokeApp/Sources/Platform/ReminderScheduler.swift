import Foundation
import GokeCore
#if canImport(AudioToolbox)
import AudioToolbox
#endif
#if canImport(UserNotifications)
import UserNotifications
#endif

extension Notification.Name {
    /// Posted when the user taps a local reminder notification (or triggers a preview in Settings)
    /// to present the in-app Quick-Snooze overlay.
    static let gokePresentQuickSnooze = Notification.Name("app.goke.ios.presentQuickSnooze")
}

/// Selectable alert chime for reminders and in-app preview playback.
enum ReminderChime: String, CaseIterable, Identifiable, Sendable, Hashable, Codable {
    case systemDefault
    case mintBell
    case gentleChime
    case focusPulse
    case silent

    var id: String { rawValue }

    static let userDefaultsKey = "goke.reminder.chime"

    var displayName: String {
        switch self {
        case .systemDefault: "系统默认"
        case .mintBell: "清脆铃音"
        case .gentleChime: "柔和风铃"
        case .focusPulse: "专注提示"
        case .silent: "仅静音横幅"
        }
    }

    var subtitle: String {
        switch self {
        case .systemDefault: "使用 iOS 系统默认通知提示音"
        case .mintBell: "清晰短促的铃声提示，适合上课与重要待办"
        case .gentleChime: "轻柔不打扰的风铃音，适合图书馆或自习"
        case .focusPulse: "极简轻敲提示音，适合时间块切换"
        case .silent: "仅显示横幅与角标，不播放提示音"
        }
    }

    static func current(from defaults: UserDefaults = .standard) -> ReminderChime {
        guard let raw = defaults.string(forKey: userDefaultsKey),
              let chime = ReminderChime(rawValue: raw) else {
            return .systemDefault
        }
        return chime
    }

    func save(to defaults: UserDefaults = .standard) {
        defaults.set(rawValue, forKey: Self.userDefaultsKey)
    }

    #if canImport(UserNotifications)
    var notificationSound: UNNotificationSound? {
        switch self {
        case .silent:
            return nil
        case .systemDefault, .mintBell, .gentleChime, .focusPulse:
            // Bundled custom .caf sounds can be added to Resources later;
            // fall back cleanly to `.default` when no custom audio file is bundled.
            if let fileName = bundledSoundFileName,
               Bundle.main.url(forResource: fileName, withExtension: "caf") != nil {
                return UNNotificationSound(named: UNNotificationSoundName("\(fileName).caf"))
            }
            return .default
        }
    }
    #endif

    private var bundledSoundFileName: String? {
        switch self {
        case .systemDefault, .silent: nil
        case .mintBell: "goke_chime_bell"
        case .gentleChime: "goke_chime_gentle"
        case .focusPulse: "goke_chime_pulse"
        }
    }

    /// Plays a short system preview tone on iOS so the user can audition the chime.
    func playPreview() {
        #if canImport(AudioToolbox)
        let soundID: SystemSoundID? = switch self {
        case .systemDefault: 1007 // Tri-tone
        case .mintBell: 1013      // Bell
        case .gentleChime: 1016   // Tweet / Chime
        case .focusPulse: 1057    // Tink
        case .silent: nil
        }
        if let soundID {
            AudioServicesPlaySystemSound(soundID)
        }
        #endif
    }
}

/// Payload delivered to `QuickSnoozeOverlayView` when a reminder notification is tapped.
public struct QuickSnoozePayload: Identifiable, Equatable, Sendable {
    public let id: String
    let entityId: String
    let category: ReminderCategory
    let title: String
    let subtitle: String
    let body: String
    let targetAt: LocalDateTime?
    let timeSensitive: Bool
    let deepLink: String

    init(
        id: String = UUID().uuidString,
        entityId: String,
        category: ReminderCategory,
        title: String,
        subtitle: String,
        body: String,
        targetAt: LocalDateTime? = nil,
        timeSensitive: Bool = false,
        deepLink: String = "goke://today"
    ) {
        self.id = id
        self.entityId = entityId
        self.category = category
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.targetAt = targetAt
        self.timeSensitive = timeSensitive
        self.deepLink = deepLink
    }
}

/// Notification category identifiers registered with `UNUserNotificationCenter`.
enum ReminderCategory: String, Sendable, CaseIterable {
    case task = "GOKE_TASK_REMINDER"
    case course = "GOKE_COURSE_REMINDER"
    case exam = "GOKE_EXAM_REMINDER"
    case event = "GOKE_EVENT_REMINDER"
    case dailyDigest = "GOKE_DAILY_DIGEST"
}

/// Interactive notification action identifiers.
enum ReminderAction: String, Sendable, CaseIterable {
    case completeTask = "COMPLETE_TASK"
    case snooze15m = "SNOOZE_15M"
    case snooze1h = "SNOOZE_1H"
}

/// Authorization summary surfaced to Reliability / Settings screens.
struct ReminderAuthorizationSnapshot: Sendable, Equatable {
    let isAuthorized: Bool
    let isProvisional: Bool
    let timeSensitiveEnabled: Bool
    let alertEnabled: Bool
    let soundEnabled: Bool

    init(
        isAuthorized: Bool,
        isProvisional: Bool,
        timeSensitiveEnabled: Bool,
        alertEnabled: Bool,
        soundEnabled: Bool
    ) {
        self.isAuthorized = isAuthorized
        self.isProvisional = isProvisional
        self.timeSensitiveEnabled = timeSensitiveEnabled
        self.alertEnabled = alertEnabled
        self.soundEnabled = soundEnabled
    }

    static let denied = ReminderAuthorizationSnapshot(
        isAuthorized: false,
        isProvisional: false,
        timeSensitiveEnabled: false,
        alertEnabled: false,
        soundEnabled: false
    )
}

/// Action dispatched when the user taps a notification or one of its interactive buttons.
enum ReminderActionEvent: Sendable, Equatable {
    case completeTask(taskId: String)
    case snoozed(entityId: String, minutes: Int)
    case openDeepLink(URL)
    case presentQuickSnooze(QuickSnoozePayload)
}

/// Concrete planned notification item before submission to `UNUserNotificationCenter`.
struct PlannedReminder: Sendable, Hashable {
    let identifier: String
    let category: ReminderCategory
    let entityId: String
    let fireAt: LocalDateTime
    let targetAt: LocalDateTime?
    let title: String
    let subtitle: String
    let body: String
    let timeSensitive: Bool
    let deepLink: String

    init(
        identifier: String,
        category: ReminderCategory,
        entityId: String,
        fireAt: LocalDateTime,
        targetAt: LocalDateTime? = nil,
        title: String,
        subtitle: String,
        body: String,
        timeSensitive: Bool,
        deepLink: String
    ) {
        self.identifier = identifier
        self.category = category
        self.entityId = entityId
        self.fireAt = fireAt
        self.targetAt = targetAt
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.timeSensitive = timeSensitive
        self.deepLink = deepLink
    }
}

/// Rolling 60-slot local notification scheduler respecting iOS's 64-notification system cap.
actor ReminderScheduler {
    static let shared = ReminderScheduler()

    /// Maximum managed notification requests (leaves 4 headroom slots for ad-hoc snoozes).
    static let maxScheduledSlots = 60
    static let managedPrefix = "goke.rem."
    static let snoozePrefix = "goke.snooze."
    static let dailyDigestIdentifier = "goke.rem.daily_digest"
    /// Matches Android `EXAM_PERSISTENT_MAX_MINUTES`: only exam reminders within 2 hours use time-sensitive alerts.
    static let examPersistentMaxMinutes = 120

    private let horizonDays: Int
    private var lastScheduleSignature: Int?
    private var lastScheduledItems: [String: PlannedReminder] = [:]
    private var lastScheduledChime: ReminderChime?
    private var lastScheduledTimeZoneId: String?
    private var lastDailyDigestSignature: Int?

    init(horizonDays: Int = 7) {
        self.horizonDays = horizonDays
    }

    // MARK: - Categories & Authorization

    /// Registers interactive categories (`COMPLETE_TASK`, `SNOOZE_15M`, `SNOOZE_1H`).
    func registerCategories() {
        #if canImport(UserNotifications)
        let completeAction = UNNotificationAction(
            identifier: ReminderAction.completeTask.rawValue,
            title: "完成任务",
            options: []
        )
        let snooze15Action = UNNotificationAction(
            identifier: ReminderAction.snooze15m.rawValue,
            title: "15分钟后再提醒",
            options: []
        )
        let snooze1hAction = UNNotificationAction(
            identifier: ReminderAction.snooze1h.rawValue,
            title: "1小时后再提醒",
            options: []
        )

        let taskCategory = UNNotificationCategory(
            identifier: ReminderCategory.task.rawValue,
            actions: [completeAction, snooze15Action, snooze1hAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        let courseCategory = UNNotificationCategory(
            identifier: ReminderCategory.course.rawValue,
            actions: [snooze15Action],
            intentIdentifiers: [],
            options: []
        )
        let examCategory = UNNotificationCategory(
            identifier: ReminderCategory.exam.rawValue,
            actions: [snooze15Action, snooze1hAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        let eventCategory = UNNotificationCategory(
            identifier: ReminderCategory.event.rawValue,
            actions: [snooze15Action, snooze1hAction],
            intentIdentifiers: [],
            options: []
        )
        let digestCategory = UNNotificationCategory(
            identifier: ReminderCategory.dailyDigest.rawValue,
            actions: [],
            intentIdentifiers: [],
            options: []
        )

        UNUserNotificationCenter.current().setNotificationCategories([
            taskCategory,
            courseCategory,
            examCategory,
            eventCategory,
            digestCategory,
        ])
        #endif
    }

    /// Requests alert + sound + badge notification permissions from iOS.
    @discardableResult
    func requestAuthorization() async throws -> Bool {
        #if canImport(UserNotifications)
        registerCategories()
        return try await UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        )
        #else
        return false
        #endif
    }

    /// Queries the current OS notification settings snapshot.
    func authorizationStatus() async -> ReminderAuthorizationSnapshot {
        #if canImport(UserNotifications)
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        let authorized = settings.authorizationStatus == .authorized
        let provisional = settings.authorizationStatus == .provisional
        let timeSensitive = settings.timeSensitiveSetting == .enabled
        return ReminderAuthorizationSnapshot(
            isAuthorized: authorized || provisional,
            isProvisional: provisional,
            timeSensitiveEnabled: timeSensitive,
            alertEnabled: settings.alertSetting == .enabled,
            soundEnabled: settings.soundSetting == .enabled
        )
        #else
        return .denied
        #endif
    }

    // MARK: - Rolling 60-Slot Planning & Scheduling

    /// Pure deterministic planner computing up to `maxSlots` earliest reminders in `[now, now + horizonDays]`.
    nonisolated func planReminders(
        snapshot: AppSnapshot,
        settings: AppSettings,
        now: LocalDateTime = .now(),
        maxSlots: Int = ReminderScheduler.maxScheduledSlots
    ) -> [PlannedReminder] {
        let live = snapshot.live()
        let startDate = now.date
        let endDate = startDate.addingDays(horizonDays)
        var candidates: [PlannedReminder] = []

        // 1. Course & Exam-Course reminders (multi-offset)
        if (settings.remindCourse || settings.remindExam), let sem = live.currentSemester {
            let occurrences = CourseExpander.occurrences(
                semester: sem,
                courses: live.courses,
                exceptions: live.exceptions,
                from: startDate,
                to: endDate
            )
            for occ in occurrences {
                let isExam = occ.course.isExam
                let enabled = isExam ? settings.remindExam : settings.remindCourse
                guard enabled else { continue }

                let rawLeads = occ.course.effectiveRemindMinutes
                let leads = rawLeads.isEmpty
                    ? (isExam ? settings.effectiveExamRemindMinutes : settings.effectiveCourseRemindMinutes)
                    : rawLeads
                let roomPart = occ.room.isEmpty ? "" : " · \(occ.room)"
                let baseSubtitle = "\(occ.start.time.description)–\(occ.end.time.description)\(roomPart)"
                let subtitle = isExam && !occ.course.name.contains("考") ? "考试 · \(baseSubtitle)" : baseSubtitle
                let title = isExam && !occ.course.name.contains("考") ? "考试：\(occ.course.name)" : occ.course.name

                for lead in leads where lead >= 0 {
                    let fireAt = occ.start.addingMinutes(-lead)
                    guard fireAt > now else { continue }
                    let leadText: String
                    if isExam {
                        leadText = lead == 0 ? "考试即将开始" : "\(Self.formatLeadTime(lead))后开始考试"
                    } else {
                        leadText = lead == 0 ? "即将开始" : "\(Self.formatLeadTime(lead))后上课"
                    }
                    let urgent = isExam && settings.examRemindPersistent && lead <= Self.examPersistentMaxMinutes
                    // Explicitly omit `occ.course.notes` from notification title/subtitle/body for clean alert parity.
                    candidates.append(
                        PlannedReminder(
                            identifier: "\(Self.managedPrefix)\(isExam ? "exam.course" : "course").\(occ.id).\(lead)",
                            category: isExam ? .exam : .course,
                            entityId: occ.course.id,
                            fireAt: fireAt,
                            targetAt: occ.start,
                            title: title,
                            subtitle: subtitle,
                            body: "\(leadText)\(roomPart)",
                            timeSensitive: urgent,
                            deepLink: "goke://course?id=\(occ.course.id)"
                        )
                    )
                }
            }
        }

        // 2. Event & Exam-Event reminders (multi-offset)
        if settings.remindEvent || settings.remindExam {
            let occurrences = EventExpander.occurrences(
                events: live.events,
                from: startDate,
                to: endDate
            )
            for occ in occurrences {
                let isExam = occ.event.isExam
                let enabled = (isExam && settings.remindExam) || settings.remindEvent
                guard enabled else { continue }

                let rawLeads = occ.event.effectiveRemindMinutes
                let leads = rawLeads.isEmpty ? (isExam ? settings.effectiveExamRemindMinutes : []) : rawLeads
                let locPart = occ.event.location.isEmpty ? "" : " · \(occ.event.location)"
                let title = isExam && !occ.event.title.contains("考") ? "考试：\(occ.event.title)" : occ.event.title
                for lead in leads where lead >= 0 {
                    let fireAt = occ.start.addingMinutes(-lead)
                    guard fireAt > now else { continue }
                    let leadText: String
                    if isExam {
                        leadText = lead == 0 ? "考试即将开始" : "\(Self.formatLeadTime(lead))后开始考试"
                    } else {
                        leadText = lead == 0 ? "日程即将开始" : "\(Self.formatLeadTime(lead))后开始"
                    }
                    let urgent = isExam && settings.examRemindPersistent && lead <= Self.examPersistentMaxMinutes
                    candidates.append(
                        PlannedReminder(
                            identifier: "\(Self.managedPrefix)\(isExam ? "exam.event" : "event").\(occ.id).\(lead)",
                            category: isExam ? .exam : .event,
                            entityId: occ.event.id,
                            fireAt: fireAt,
                            targetAt: occ.start,
                            title: title,
                            subtitle: isExam ? "考试 · \(occ.start.time.description)\(locPart)" : "\(occ.start.time.description)\(locPart)",
                            body: "\(leadText)\(locPart)",
                            timeSensitive: urgent,
                            deepLink: "goke://today"
                        )
                    )
                }
            }
        }

        // 3. Task due & scheduled block reminders (multi-offset + exam tasks)
        if settings.remindTask || settings.remindExam {
            let sem = live.currentSemester
            let horizonCutoff = endDate.atTime(.endOfDay)
            for task in TaskLogic.topLevel(live.tasks) where !task.done {
                let isExam = task.isExam
                let enabled = (isExam && settings.remindExam) || settings.remindTask
                guard enabled else { continue }

                let rawLeads = task.effectiveRemindMinutes
                let defaultLeads: [Int] = isExam
                    ? settings.effectiveExamRemindMinutes
                    : (task.remindPersistent ? [0] : [])
                let leads = rawLeads.isEmpty ? defaultLeads : rawLeads
                guard !leads.isEmpty else { continue }

                let effDue = TaskLogic.effectiveDue(
                    task: task,
                    semester: sem,
                    courses: live.courses,
                    exceptions: live.exceptions,
                    now: now
                )

                for lead in leads where lead >= 0 {
                    let urgent = task.remindPersistent
                        || task.priority >= 3
                        || (isExam && settings.examRemindPersistent && lead <= Self.examPersistentMaxMinutes)
                    let cat: ReminderCategory = isExam ? .exam : .task

                    if let due = effDue, (task.dueHasTime || task.dueBeforeNextClass) {
                        let fireAt = due.addingMinutes(-lead)
                        if fireAt > now && fireAt <= horizonCutoff {
                            let body = lead == 0
                                ? (isExam ? "考试即将开始" : "任务已到截止时间")
                                : (isExam ? "\(Self.formatLeadTime(lead))后开始考试" : "距离截止还有 \(Self.formatLeadTime(lead))")
                            candidates.append(
                                PlannedReminder(
                                    identifier: "\(Self.managedPrefix)\(isExam ? "exam.task" : "task").due.\(task.id).\(lead)",
                                    category: cat,
                                    entityId: task.id,
                                    fireAt: fireAt,
                                    targetAt: due,
                                    title: task.title,
                                    subtitle: isExam ? "考试提醒" : (task.dueBeforeNextClass ? "课前截止" : "待办提醒"),
                                    body: body,
                                    timeSensitive: urgent,
                                    deepLink: "goke://task?id=\(task.id)"
                                )
                            )
                        }
                    }

                    if let blockStart = task.blockStart {
                        let fireAt = blockStart.addingMinutes(-lead)
                        if fireAt > now && fireAt <= horizonCutoff {
                            let body = lead == 0
                                ? (isExam ? "考试即将开始" : "计划的时间块开始了")
                                : (isExam ? "\(Self.formatLeadTime(lead))后开始考试" : "\(Self.formatLeadTime(lead))后进入计划时间块")
                            candidates.append(
                                PlannedReminder(
                                    identifier: "\(Self.managedPrefix)\(isExam ? "exam.task" : "task").block.\(task.id).\(lead)",
                                    category: cat,
                                    entityId: task.id,
                                    fireAt: fireAt,
                                    targetAt: blockStart,
                                    title: task.title,
                                    subtitle: isExam ? "考试 · \(blockStart.time.description)" : "时间块 \(blockStart.time.description)",
                                    body: body,
                                    timeSensitive: urgent,
                                    deepLink: "goke://task?id=\(task.id)"
                                )
                            )
                        }
                    }
                }
            }
        }

        let sorted = candidates.sorted { lhs, rhs in
            if lhs.fireAt != rhs.fireAt { return lhs.fireAt < rhs.fireAt }
            return lhs.identifier < rhs.identifier
        }
        let budget = max(0, maxSlots - (settings.dailyDigest ? 1 : 0))
        if sorted.count <= budget {
            return sorted
        }
        // Prioritize exam reminders over ordinary reminders when hitting iOS's 60-slot cap.
        let examItems = sorted.filter { $0.category == .exam }
        let remaining = max(0, budget - examItems.count)
        let otherItems = sorted.filter { $0.category != .exam }.prefix(remaining)
        return (Array(examItems.prefix(budget)) + otherItems).sorted { lhs, rhs in
            if lhs.fireAt != rhs.fireAt { return lhs.fireAt < rhs.fireAt }
            return lhs.identifier < rhs.identifier
        }
    }

    /// Reconciles pending `UNUserNotificationCenter` requests against `snapshot` and `settings`.
    /// Skips redundant churn when the computed schedule signature is unchanged (unless `force == true`),
    /// and diffs against previously scheduled items to minimize XPC calls when only a subset changed.
    @discardableResult
    func reschedule(
        snapshot: AppSnapshot,
        settings: AppSettings,
        now: LocalDateTime = .now(),
        timeZone: TimeZone = .current,
        force: Bool = false
    ) async -> Int {
        let planned = planReminders(snapshot: snapshot, settings: settings, now: now)
        let chime = ReminderChime.current()
        let signature = Self.computeSignature(
            planned: planned,
            settings: settings,
            chime: chime,
            today: now.date,
            timeZone: timeZone
        )
        if !force && signature == lastScheduleSignature {
            return planned.count + (settings.dailyDigest ? 1 : 0)
        }

        #if canImport(UserNotifications)
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let managedIds = pending
            .map(\.identifier)
            .filter { $0.hasPrefix(Self.managedPrefix) }
        let pendingIdSet = Set(managedIds)

        let canDiff = !force
            && lastScheduledChime == chime
            && lastScheduledTimeZoneId == timeZone.identifier
            && !lastScheduledItems.isEmpty

        let sound = chime.notificationSound
        let gregorian = Calendar(identifier: .gregorian)
        var scheduledItems: [String: PlannedReminder] = [:]
        scheduledItems.reserveCapacity(planned.count)
        var scheduledCount = 0

        if canDiff {
            let plannedById = Dictionary(planned.map { ($0.identifier, $0) }, uniquingKeysWith: { first, _ in first })
            var idsToRemove: [String] = []
            for id in managedIds {
                if id == Self.dailyDigestIdentifier {
                    if !settings.dailyDigest {
                        idsToRemove.append(id)
                    }
                } else if plannedById[id] == nil {
                    idsToRemove.append(id)
                }
            }
            if !idsToRemove.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: idsToRemove)
            }

            for item in planned {
                if pendingIdSet.contains(item.identifier), lastScheduledItems[item.identifier] == item {
                    scheduledItems[item.identifier] = item
                    scheduledCount += 1
                    continue
                }
                let request = Self.buildRequest(
                    for: item,
                    sound: sound,
                    calendar: gregorian,
                    timeZone: timeZone
                )
                do {
                    try await center.add(request)
                    scheduledItems[item.identifier] = item
                    scheduledCount += 1
                } catch {
                    continue
                }
            }
        } else {
            if !managedIds.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: managedIds)
            }
            for item in planned {
                let request = Self.buildRequest(
                    for: item,
                    sound: sound,
                    calendar: gregorian,
                    timeZone: timeZone
                )
                do {
                    try await center.add(request)
                    scheduledItems[item.identifier] = item
                    scheduledCount += 1
                } catch {
                    continue
                }
            }
        }

        if settings.dailyDigest {
            let digestSpec = Self.buildDailyDigestSpec(
                snapshot: snapshot,
                settings: settings,
                today: now.date,
                chime: chime,
                timeZone: timeZone
            )
            if canDiff,
               pendingIdSet.contains(Self.dailyDigestIdentifier),
               lastDailyDigestSignature == digestSpec.signature
            {
                scheduledCount += 1
            } else {
                let digestRequest = Self.buildDailyDigestRequest(
                    body: digestSpec.body,
                    settings: settings,
                    sound: sound,
                    calendar: gregorian,
                    timeZone: timeZone
                )
                do {
                    try await center.add(digestRequest)
                    lastDailyDigestSignature = digestSpec.signature
                    scheduledCount += 1
                } catch {
                    // Keep remaining scheduled reminders intact.
                }
            }
        } else {
            lastDailyDigestSignature = nil
        }

        lastScheduleSignature = signature
        lastScheduledItems = scheduledItems
        lastScheduledChime = chime
        lastScheduledTimeZoneId = timeZone.identifier
        return scheduledCount
        #else
        lastScheduleSignature = signature
        lastScheduledItems = Dictionary(planned.map { ($0.identifier, $0) }, uniquingKeysWith: { first, _ in first })
        lastScheduledChime = chime
        lastScheduledTimeZoneId = timeZone.identifier
        return planned.count + (settings.dailyDigest ? 1 : 0)
        #endif
    }

    /// Schedules a one-shot snooze notification outside the rolling managed prefix.
    func scheduleSnooze(
        entityId: String,
        category: ReminderCategory,
        title: String,
        subtitle: String,
        body: String,
        minutes: Int,
        timeSensitive: Bool,
        deepLink: String,
        targetAt: LocalDateTime? = nil
    ) async {
        #if canImport(UserNotifications)
        let chime = ReminderChime.current()
        let content = UNMutableNotificationContent()
        content.title = title
        content.subtitle = subtitle
        content.body = body
        content.sound = chime.notificationSound
        content.categoryIdentifier = category.rawValue
        if #available(iOS 15.0, *) {
            content.interruptionLevel = timeSensitive ? .timeSensitive : .active
        }
        var info: [String: Any] = [
            "entityId": entityId,
            "category": category.rawValue,
            "deepLink": deepLink,
            "timeSensitive": timeSensitive,
        ]
        if let targetAt {
            info["targetAt"] = targetAt.description
        }
        content.userInfo = info
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: TimeInterval(max(60, minutes * 60)),
            repeats: false
        )
        let identifier = "\(Self.snoozePrefix)\(entityId).\(Int(Date().timeIntervalSince1970))"
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        try? await UNUserNotificationCenter.current().add(request)
        #endif
    }

    /// Cancels all Goke-managed pending reminders (does not clear external app notifications).
    func cancelAllManaged() async {
        lastScheduleSignature = nil
        lastScheduledItems = [:]
        lastScheduledChime = nil
        lastScheduledTimeZoneId = nil
        lastDailyDigestSignature = nil
        #if canImport(UserNotifications)
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let ids = pending
            .map(\.identifier)
            .filter { $0.hasPrefix(Self.managedPrefix) || $0.hasPrefix(Self.snoozePrefix) }
        if !ids.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: ids)
        }
        #endif
    }

    // MARK: - Helpers

    static func formatLeadTime(_ minutes: Int) -> String {
        if minutes <= 0 { return "准时" }
        if minutes % 1440 == 0 {
            return "\(minutes / 1440)天"
        }
        if minutes >= 60 {
            let hours = minutes / 60
            let rem = minutes % 60
            return rem == 0 ? "\(hours)小时" : "\(hours)小时\(rem)分钟"
        }
        return "\(minutes)分钟"
    }

    private static func computeSignature(
        planned: [PlannedReminder],
        settings: AppSettings,
        chime: ReminderChime,
        today: LocalDate,
        timeZone: TimeZone
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(settings.dailyDigest)
        if settings.dailyDigest {
            hasher.combine(settings.digestHour)
            hasher.combine(settings.digestMinute)
            hasher.combine(today)
        }
        hasher.combine(chime)
        hasher.combine(timeZone.identifier)
        hasher.combine(planned)
        return hasher.finalize()
    }

    #if canImport(UserNotifications)
    private static func buildRequest(
        for item: PlannedReminder,
        sound: UNNotificationSound?,
        calendar: Calendar,
        timeZone: TimeZone
    ) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = item.title
        content.subtitle = item.subtitle
        content.body = item.body
        content.sound = sound
        content.categoryIdentifier = item.category.rawValue
        if #available(iOS 15.0, *) {
            content.interruptionLevel = item.timeSensitive ? .timeSensitive : .active
            content.relevanceScore = item.timeSensitive ? 0.9 : 0.6
        }
        var info: [String: Any] = [
            "entityId": item.entityId,
            "category": item.category.rawValue,
            "deepLink": item.deepLink,
            "timeSensitive": item.timeSensitive,
        ]
        if let targetAt = item.targetAt {
            info["targetAt"] = targetAt.description
        }
        content.userInfo = info

        var comps = DateComponents()
        comps.calendar = calendar
        comps.timeZone = timeZone
        comps.year = item.fireAt.year
        comps.month = item.fireAt.month
        comps.day = item.fireAt.day
        comps.hour = item.fireAt.hour
        comps.minute = item.fireAt.minute
        comps.second = item.fireAt.second

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        return UNNotificationRequest(identifier: item.identifier, content: content, trigger: trigger)
    }

    private struct DailyDigestSpec {
        let body: String
        let signature: Int
    }

    private static func buildDailyDigestSpec(
        snapshot: AppSnapshot,
        settings: AppSettings,
        today: LocalDate,
        chime: ReminderChime,
        timeZone: TimeZone
    ) -> DailyDigestSpec {
        let items = AgendaEngine.forDay(
            snapshot: snapshot,
            date: today,
            defaultBlockMinutes: settings.defaultBlockMinutes,
            includeDoneTasks: false
        )
        let courseCount = items.filter { if case .course = $0 { return true }; return false }.count
        let taskCount = items.filter {
            switch $0 {
            case .task, .block: return true
            default: return false
            }
        }.count
        let body = (courseCount == 0 && taskCount == 0)
            ? "打开 Goke 查看今日课程与待办安排。"
            : "今日安排：\(courseCount) 节课程 · \(taskCount) 项待办。"
        var hasher = Hasher()
        hasher.combine(body)
        hasher.combine(settings.digestHour)
        hasher.combine(settings.digestMinute)
        hasher.combine(chime)
        hasher.combine(timeZone.identifier)
        return DailyDigestSpec(body: body, signature: hasher.finalize())
    }

    private static func buildDailyDigestRequest(
        body: String,
        settings: AppSettings,
        sound: UNNotificationSound?,
        calendar: Calendar,
        timeZone: TimeZone
    ) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = "今日工课简报"
        content.body = body
        content.sound = sound
        content.categoryIdentifier = ReminderCategory.dailyDigest.rawValue
        content.interruptionLevel = .active
        content.userInfo = [
            "entityId": "daily_digest",
            "category": ReminderCategory.dailyDigest.rawValue,
            "deepLink": "goke://today",
        ]

        var comps = DateComponents()
        comps.calendar = calendar
        comps.timeZone = timeZone
        comps.hour = settings.digestHour
        comps.minute = settings.digestMinute

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        return UNNotificationRequest(identifier: Self.dailyDigestIdentifier, content: content, trigger: trigger)
    }
    #endif
}

#if canImport(UserNotifications)
/// `UNUserNotificationCenterDelegate` bridge forwarding interactive actions (`COMPLETE_TASK`, `SNOOZE_15M`, `SNOOZE_1H`)
/// and foreground banner presentation under Swift 6 strict concurrency.
///
/// Safety invariant: all stored properties are immutable `Sendable` references (`ReminderScheduler` actor and `@Sendable` closure).
final class ReminderNotificationDelegate: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    private let scheduler: ReminderScheduler
    private let onAction: @Sendable (ReminderActionEvent) async -> Void

    init(
        scheduler: ReminderScheduler = .shared,
        onAction: @escaping @Sendable (ReminderActionEvent) async -> Void
    ) {
        self.scheduler = scheduler
        self.onAction = onAction
    }

    func install() {
        UNUserNotificationCenter.current().delegate = self
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let actionId = response.actionIdentifier
        let content = response.notification.request.content
        let info = content.userInfo
        let entityId = (info["entityId"] as? String) ?? ""
        let categoryRaw = (info["category"] as? String) ?? content.categoryIdentifier
        let category = ReminderCategory(rawValue: categoryRaw) ?? .task
        let deepLinkStr = (info["deepLink"] as? String) ?? "goke://today"
        let timeSensitive = (info["timeSensitive"] as? Bool) ?? false

        switch actionId {
        case ReminderAction.completeTask.rawValue:
            if !entityId.isEmpty {
                await onAction(.completeTask(taskId: entityId))
            }

        case ReminderAction.snooze15m.rawValue:
            await scheduler.scheduleSnooze(
                entityId: entityId,
                category: category,
                title: content.title,
                subtitle: content.subtitle,
                body: content.body,
                minutes: 15,
                timeSensitive: timeSensitive,
                deepLink: deepLinkStr
            )
            await onAction(.snoozed(entityId: entityId, minutes: 15))

        case ReminderAction.snooze1h.rawValue:
            await scheduler.scheduleSnooze(
                entityId: entityId,
                category: category,
                title: content.title,
                subtitle: content.subtitle,
                body: content.body,
                minutes: 60,
                timeSensitive: timeSensitive,
                deepLink: deepLinkStr
            )
            await onAction(.snoozed(entityId: entityId, minutes: 60))

        case UNNotificationDefaultActionIdentifier:
            if category == .dailyDigest {
                if let url = URL(string: deepLinkStr) {
                    await onAction(.openDeepLink(url))
                }
            } else {
                let targetAt: LocalDateTime? = (info["targetAt"] as? String).flatMap { LocalDateTime.parseLenient($0) }
                let payload = QuickSnoozePayload(
                    entityId: entityId,
                    category: category,
                    title: content.title,
                    subtitle: content.subtitle,
                    body: content.body,
                    targetAt: targetAt,
                    timeSensitive: timeSensitive,
                    deepLink: deepLinkStr
                )
                await onAction(.presentQuickSnooze(payload))
            }

        default:
            break
        }
    }
}
#endif
