import Foundation

/// Configurable reminder shorthand rules for `QuickAddParser`, matching Android's `QuickAddConfig`.
public struct QuickAddConfig: Sendable, Hashable, Codable {
    public static let defaultRemindKeyword = "提醒"
    /// 15 minutes before + on time (normalized ascending as `[0, 15]`).
    public static let defaultRemindMinutes: [Int] = [0, 15]
    public static let `default` = QuickAddConfig()

    /// Keyword that triggers normal/persistent reminder shorthands (trimmed; empty disables keyword shorthands).
    public var remindKeyword: String
    /// Default lead minutes when matching plain `提醒`.
    public var remindMinutesList: [Int]
    /// Lead minutes when matching `提醒!` / `提醒！` (falls back to `effectiveRemindMinutes` when empty).
    public var persistentRemindMinutesList: [Int]
    /// Whether `提醒!` / `提醒！` enables persistent alarm reminders.
    public var bangPersistent: Bool

    public init(
        remindKeyword: String = QuickAddConfig.defaultRemindKeyword,
        remindMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes,
        persistentRemindMinutesList: [Int] = QuickAddConfig.defaultRemindMinutes,
        bangPersistent: Bool = true
    ) {
        self.remindKeyword = remindKeyword
        self.remindMinutesList = remindMinutesList
        self.persistentRemindMinutesList = persistentRemindMinutesList
        self.bangPersistent = bangPersistent
    }

    public var normalizedKeyword: String {
        remindKeyword.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public var effectiveRemindMinutes: [Int] {
        let norm = Reminders.normalize(remindMinutesList)
        return norm.isEmpty ? Self.defaultRemindMinutes : norm
    }

    public var effectivePersistentRemindMinutes: [Int] {
        let norm = Reminders.normalize(persistentRemindMinutesList)
        return norm.isEmpty ? effectiveRemindMinutes : norm
    }
}
