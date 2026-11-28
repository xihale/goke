import Foundation
import GokeCore
import Observation
import SwiftUI
import GokeShared

/// `@MainActor @Observable` store for non-secret user preferences (`AppSettings`),
/// persisted to `AppGroupContainer.userDefaults` as JSON so widgets and intents read the same settings.
@MainActor
@Observable
public final class AppSettingsStore {
    nonisolated static let storageKey = "app.goke.ios.settings.v1"

    var settings: AppSettings {
        didSet {
            guard settings != oldValue else { return }
            if Self.themeInputsChanged(from: oldValue, to: settings) {
                resolvedTheme = Self.resolveTheme(from: settings)
            }
            if oldValue.darkMode != settings.darkMode {
                preferredColorScheme = Self.resolveColorScheme(from: settings.darkMode)
            }
            persist(settings)
        }
    }

    /// Cached active `GokeTheme` resolved from `settings.themeSource`, `preset`, and `seedColorARGB`.
    private(set) var resolvedTheme: GokeTheme

    /// Cached SwiftUI `preferredColorScheme` mapped from `settings.darkMode`.
    private(set) var preferredColorScheme: ColorScheme?

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let key: String
    @ObservationIgnored private let encoder = MintJSON.makeEncoder()

    init(
        container: AppGroupContainer = .shared,
        storageKey: String = AppSettingsStore.storageKey
    ) {
        let suite = container.userDefaults
        self.defaults = suite
        self.key = storageKey
        let loaded = Self.load(from: suite, key: storageKey)
        self.settings = loaded
        self.resolvedTheme = Self.resolveTheme(from: loaded)
        self.preferredColorScheme = Self.resolveColorScheme(from: loaded.darkMode)
    }

    init(
        userDefaults: UserDefaults,
        storageKey: String = AppSettingsStore.storageKey
    ) {
        self.defaults = userDefaults
        self.key = storageKey
        let loaded = Self.load(from: userDefaults, key: storageKey)
        self.settings = loaded
        self.resolvedTheme = Self.resolveTheme(from: loaded)
        self.preferredColorScheme = Self.resolveColorScheme(from: loaded.darkMode)
    }

    /// Mutates `settings` atomically and persists only if the value actually changed.
    func update(_ mutate: (inout AppSettings) -> Void) {
        var next = settings
        mutate(&next)
        guard next != settings else { return }
        settings = next
    }

    /// Reloads persisted settings from storage (e.g., after an AppIntent or extension update).
    func reloadFromDisk() {
        let loaded = Self.load(from: defaults, key: key)
        if loaded != settings {
            settings = loaded
        }
    }

    /// Resets all preferences back to `AppSettings.default`.
    func reset() {
        settings = .default
    }

    private func persist(_ value: AppSettings) {
        guard let data = try? encoder.encode(value) else { return }
        defaults.set(data, forKey: key)
    }

    private static func themeInputsChanged(from old: AppSettings, to new: AppSettings) -> Bool {
        old.themeSource != new.themeSource
            || old.preset != new.preset
            || old.seedColorARGB != new.seedColorARGB
    }

    private static func resolveTheme(from settings: AppSettings) -> GokeTheme {
        switch settings.themeSource {
        case .CUSTOM:
            return GokeTheme.custom(seedHex: settings.seedColorARGB & 0x00FF_FFFF)
        case .PRESET, .DYNAMIC:
            return GokeTheme.forPreset(GokeThemePreset.from(key: settings.preset))
        }
    }

    private static func resolveColorScheme(from darkMode: DarkMode) -> ColorScheme? {
        switch darkMode {
        case .SYSTEM: nil
        case .LIGHT: .light
        case .DARK: .dark
        }
    }

    private static func load(from defaults: UserDefaults, key: String) -> AppSettings {
        guard let data = defaults.data(forKey: key),
              let decoded = try? MintJSON.makeDecoder().decode(AppSettings.self, from: data)
        else {
            return .default
        }
        return decoded
    }
}
