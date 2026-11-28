import Foundation

public enum ThemeSource: String, Sendable, Hashable, Codable, CaseIterable {
    case DYNAMIC
    case PRESET
    case CUSTOM
}

public enum DarkMode: String, Sendable, Hashable, Codable, CaseIterable {
    case SYSTEM
    case LIGHT
    case DARK
}

public enum UpdateInterval: String, Sendable, Hashable, Codable, CaseIterable {
    case STARTUP
    case DAILY
    case WEEKLY
    case OFF
}

/// Non-secret user preferences matching Android's `Settings`. Secrets live in Keychain only.
public struct AppSettings: Sendable, Hashable, Codable {
    public var themeSource: ThemeSource
    public var preset: String
    public var seedColorARGB: UInt32
    public var darkMode: DarkMode
    public var showTeacher: Bool
    public var defaultBlockMinutes: Int
    public var remindCourse: Bool
    public var courseRemindMinutes: Int
    public var courseRemindMinutesList: [Int]
    public var remindExam: Bool
    public var examRemindMinutesList: [Int]
    public var examRemindPersistent: Bool
    public var remindTask: Bool
    public var remindEvent: Bool
    public var dailyDigest: Bool
    public var digestHour: Int
    public var digestMinute: Int
    public var swapSwipe: Bool
    public var hapticFeedback: Bool
    public var soundFeedback: Bool
    public var autoBackup: Bool
    public var backupKeep: Int
    public var parcelEnabled: Bool
    public var parcelWindowStart: Int
    public var parcelWindowEnd: Int
    public var weekStartsMonday: Bool
    public var unifiedTasks: Bool
    public var updateInterval: UpdateInterval
    public var lastUpdateCheckAt: Int64
    public var pendingUpdateVersion: String
    public var pendingUpdateUrl: String
    public var pendingUpdateNotes: String
    public var dismissedUpdateVersion: String
    public var quickRemindKeyword: String
    public var quickRemindMinutesList: [Int]
    public var quickRemindPersistentMinutesList: [Int]
    public var quickRemindBangPersistent: Bool

    public static let defaultExamRemindMinutes: [Int] = [60, 1440]

    public var effectiveCourseRemindMinutes: [Int] {
        let normalized = Reminders.normalize(
            primary: courseRemindMinutesList.isEmpty ? courseRemindMinutes : nil,
            list: courseRemindMinutesList
        )
        return normalized.isEmpty ? [max(0, courseRemindMinutes)] : normalized
    }

    public var effectiveExamRemindMinutes: [Int] {
        let normalized = Reminders.normalize(examRemindMinutesList)
        return normalized.isEmpty ? Self.defaultExamRemindMinutes : normalized
    }

    public var quickAddConfig: QuickAddConfig {
        QuickAddConfig(
            remindKeyword: quickRemindKeyword,
            remindMinutesList: quickRemindMinutesList,
            persistentRemindMinutesList: quickRemindPersistentMinutesList,
            bangPersistent: quickRemindBangPersistent
        )
    }

    public mutating func applyQuickAddConfig(_ config: QuickAddConfig) {
        quickRemindKeyword = config.remindKeyword
        quickRemindMinutesList = config.effectiveRemindMinutes
        quickRemindPersistentMinutesList = config.effectivePersistentRemindMinutes
        quickRemindBangPersistent = config.bangPersistent
    }

    public init(
        themeSource: ThemeSource = .PRESET,
        preset: String = "mint",
        seedColorARGB: UInt32 = 0xFF2E776C,
        darkMode: DarkMode = .SYSTEM,
        showTeacher: Bool = true,
        defaultBlockMinutes: Int = 30,
        remindCourse: Bool = false,
        courseRemindMinutes: Int = 15,
        courseRemindMinutesList: [Int]? = nil,
        remindExam: Bool = true,
        examRemindMinutesList: [Int] = AppSettings.defaultExamRemindMinutes,
        examRemindPersistent: Bool = false,
        remindTask: Bool = true,
        remindEvent: Bool = true,
        dailyDigest: Bool = false,
        digestHour: Int = 7,
        digestMinute: Int = 30,
        swapSwipe: Bool = false,
        hapticFeedback: Bool = true,
        soundFeedback: Bool = true,
        autoBackup: Bool = false,
        backupKeep: Int = 7,
        parcelEnabled: Bool = false,
        parcelWindowStart: Int = 14 * 60,
        parcelWindowEnd: Int = 22 * 60,
        weekStartsMonday: Bool = true,
        unifiedTasks: Bool = false,
        updateInterval: UpdateInterval = .DAILY,
        lastUpdateCheckAt: Int64 = 0,
        pendingUpdateVersion: String = "",
        pendingUpdateUrl: String = "",
        pendingUpdateNotes: String = "",
        dismissedUpdateVersion: String = "",
        quickRemindKeyword: String = QuickAddConfig.defaultRemindKeyword,
        quickRemindMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes,
        quickRemindPersistentMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes,
        quickRemindBangPersistent: Bool = true
    ) {
        self.themeSource = themeSource
        self.preset = preset
        self.seedColorARGB = seedColorARGB
        self.darkMode = darkMode
        self.showTeacher = showTeacher
        self.defaultBlockMinutes = defaultBlockMinutes
        self.remindCourse = remindCourse
        let safeCourseMin = max(0, courseRemindMinutes)
        let normCourse = Reminders.normalize(courseRemindMinutesList ?? [safeCourseMin])
        self.courseRemindMinutesList = normCourse.isEmpty ? [safeCourseMin] : normCourse
        self.courseRemindMinutes = self.courseRemindMinutesList.first ?? safeCourseMin
        self.remindExam = remindExam
        let normExam = Reminders.normalize(examRemindMinutesList)
        self.examRemindMinutesList = normExam.isEmpty ? Self.defaultExamRemindMinutes : normExam
        self.examRemindPersistent = examRemindPersistent
        self.remindTask = remindTask
        self.remindEvent = remindEvent
        self.dailyDigest = dailyDigest
        self.digestHour = digestHour
        self.digestMinute = digestMinute
        self.swapSwipe = swapSwipe
        self.hapticFeedback = hapticFeedback
        self.soundFeedback = soundFeedback
        self.autoBackup = autoBackup
        self.backupKeep = backupKeep
        self.parcelEnabled = parcelEnabled
        self.parcelWindowStart = parcelWindowStart
        self.parcelWindowEnd = parcelWindowEnd
        self.weekStartsMonday = weekStartsMonday
        self.unifiedTasks = unifiedTasks
        self.updateInterval = updateInterval
        self.lastUpdateCheckAt = lastUpdateCheckAt
        self.pendingUpdateVersion = pendingUpdateVersion
        self.pendingUpdateUrl = pendingUpdateUrl
        self.pendingUpdateNotes = pendingUpdateNotes
        self.dismissedUpdateVersion = dismissedUpdateVersion
        self.quickRemindKeyword = quickRemindKeyword
        self.quickRemindMinutesList = Reminders.normalize(quickRemindMinutesList).isEmpty
            ? QuickAddConfig.defaultRemindMinutes
            : Reminders.normalize(quickRemindMinutesList)
        let normPersistent = Reminders.normalize(quickRemindPersistentMinutesList)
        self.quickRemindPersistentMinutesList = normPersistent.isEmpty
            ? QuickAddConfig.defaultRemindMinutes
            : normPersistent
        self.quickRemindBangPersistent = quickRemindBangPersistent
    }

    private enum CodingKeys: String, CodingKey {
        case themeSource, preset, seedColorARGB, darkMode, showTeacher, defaultBlockMinutes
        case remindCourse, courseRemindMinutes, courseRemindMinutesList
        case remindExam, examRemindMinutesList, examRemindPersistent
        case remindTask, remindEvent
        case dailyDigest, digestHour, digestMinute, swapSwipe, hapticFeedback, soundFeedback, autoBackup, backupKeep
        case parcelEnabled, parcelWindowStart, parcelWindowEnd, weekStartsMonday, unifiedTasks
        case updateInterval, lastUpdateCheckAt, pendingUpdateVersion, pendingUpdateUrl, pendingUpdateNotes, dismissedUpdateVersion
        case quickRemindKeyword, quickRemindMinutesList, quickRemindPersistentMinutesList, quickRemindBangPersistent
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = AppSettings.default
        self.themeSource = try c.decodeIfPresent(ThemeSource.self, forKey: .themeSource) ?? defaults.themeSource
        self.preset = try c.decodeIfPresent(String.self, forKey: .preset) ?? defaults.preset
        self.seedColorARGB = try c.decodeIfPresent(UInt32.self, forKey: .seedColorARGB) ?? defaults.seedColorARGB
        self.darkMode = try c.decodeIfPresent(DarkMode.self, forKey: .darkMode) ?? defaults.darkMode
        self.showTeacher = try c.decodeIfPresent(Bool.self, forKey: .showTeacher) ?? defaults.showTeacher
        self.defaultBlockMinutes = try c.decodeIfPresent(Int.self, forKey: .defaultBlockMinutes) ?? defaults.defaultBlockMinutes
        self.remindCourse = try c.decodeIfPresent(Bool.self, forKey: .remindCourse) ?? defaults.remindCourse
        let decodedCourseMin = max(0, try c.decodeIfPresent(Int.self, forKey: .courseRemindMinutes) ?? defaults.courseRemindMinutes)
        let decodedCourseList = try c.decodeIfPresent([Int].self, forKey: .courseRemindMinutesList)
        let normCourse = Reminders.normalize(decodedCourseList ?? [decodedCourseMin])
        self.courseRemindMinutesList = normCourse.isEmpty ? [decodedCourseMin] : normCourse
        self.courseRemindMinutes = self.courseRemindMinutesList.first ?? decodedCourseMin
        self.remindExam = try c.decodeIfPresent(Bool.self, forKey: .remindExam) ?? defaults.remindExam
        let rawExamList = try c.decodeIfPresent([Int].self, forKey: .examRemindMinutesList) ?? defaults.examRemindMinutesList
        let normExam = Reminders.normalize(rawExamList)
        self.examRemindMinutesList = normExam.isEmpty ? Self.defaultExamRemindMinutes : normExam
        self.examRemindPersistent = try c.decodeIfPresent(Bool.self, forKey: .examRemindPersistent) ?? defaults.examRemindPersistent
        self.remindTask = try c.decodeIfPresent(Bool.self, forKey: .remindTask) ?? defaults.remindTask
        self.remindEvent = try c.decodeIfPresent(Bool.self, forKey: .remindEvent) ?? defaults.remindEvent
        self.dailyDigest = try c.decodeIfPresent(Bool.self, forKey: .dailyDigest) ?? defaults.dailyDigest
        self.digestHour = try c.decodeIfPresent(Int.self, forKey: .digestHour) ?? defaults.digestHour
        self.digestMinute = try c.decodeIfPresent(Int.self, forKey: .digestMinute) ?? defaults.digestMinute
        self.swapSwipe = try c.decodeIfPresent(Bool.self, forKey: .swapSwipe) ?? defaults.swapSwipe
        self.hapticFeedback = try c.decodeIfPresent(Bool.self, forKey: .hapticFeedback) ?? defaults.hapticFeedback
        self.soundFeedback = try c.decodeIfPresent(Bool.self, forKey: .soundFeedback) ?? defaults.soundFeedback
        self.autoBackup = try c.decodeIfPresent(Bool.self, forKey: .autoBackup) ?? defaults.autoBackup
        self.backupKeep = try c.decodeIfPresent(Int.self, forKey: .backupKeep) ?? defaults.backupKeep
        self.parcelEnabled = try c.decodeIfPresent(Bool.self, forKey: .parcelEnabled) ?? defaults.parcelEnabled
        self.parcelWindowStart = try c.decodeIfPresent(Int.self, forKey: .parcelWindowStart) ?? defaults.parcelWindowStart
        self.parcelWindowEnd = try c.decodeIfPresent(Int.self, forKey: .parcelWindowEnd) ?? defaults.parcelWindowEnd
        self.weekStartsMonday = try c.decodeIfPresent(Bool.self, forKey: .weekStartsMonday) ?? defaults.weekStartsMonday
        self.unifiedTasks = try c.decodeIfPresent(Bool.self, forKey: .unifiedTasks) ?? defaults.unifiedTasks
        self.updateInterval = try c.decodeIfPresent(UpdateInterval.self, forKey: .updateInterval) ?? defaults.updateInterval
        self.lastUpdateCheckAt = try c.decodeIfPresent(Int64.self, forKey: .lastUpdateCheckAt) ?? defaults.lastUpdateCheckAt
        self.pendingUpdateVersion = try c.decodeIfPresent(String.self, forKey: .pendingUpdateVersion) ?? defaults.pendingUpdateVersion
        self.pendingUpdateUrl = try c.decodeIfPresent(String.self, forKey: .pendingUpdateUrl) ?? defaults.pendingUpdateUrl
        self.pendingUpdateNotes = try c.decodeIfPresent(String.self, forKey: .pendingUpdateNotes) ?? defaults.pendingUpdateNotes
        self.dismissedUpdateVersion = try c.decodeIfPresent(String.self, forKey: .dismissedUpdateVersion) ?? defaults.dismissedUpdateVersion
        self.quickRemindKeyword = try c.decodeIfPresent(String.self, forKey: .quickRemindKeyword) ?? defaults.quickRemindKeyword
        let rawRemind = try c.decodeIfPresent([Int].self, forKey: .quickRemindMinutesList) ?? defaults.quickRemindMinutesList
        let normRemind = Reminders.normalize(rawRemind)
        self.quickRemindMinutesList = normRemind.isEmpty ? QuickAddConfig.defaultRemindMinutes : normRemind
        let rawPersistent = try c.decodeIfPresent([Int].self, forKey: .quickRemindPersistentMinutesList) ?? defaults.quickRemindPersistentMinutesList
        let normPersistent = Reminders.normalize(rawPersistent)
        self.quickRemindPersistentMinutesList = normPersistent.isEmpty ? QuickAddConfig.defaultRemindMinutes : normPersistent
        self.quickRemindBangPersistent = try c.decodeIfPresent(Bool.self, forKey: .quickRemindBangPersistent) ?? defaults.quickRemindBangPersistent
    }

    public static let `default` = AppSettings()
}
