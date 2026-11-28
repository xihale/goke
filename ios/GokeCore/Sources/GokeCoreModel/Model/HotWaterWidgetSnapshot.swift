import Foundation

public enum HotWaterPhase: String, Sendable, Hashable, Codable {
    case unbound = "UNBOUND"
    case idle = "IDLE"
    case working = "WORKING"
    case valveOpen = "VALVE_OPEN"
    case error = "ERROR"
}

/// Compact snapshot persisted to `AppGroupContainer` (`hotwater-widget.json` + `userDefaults`) for `HotWaterWidget`.
public struct HotWaterWidgetSnapshot: Sendable, Hashable, Codable {
    public static let storageKey = "app.goke.ios.hotwater.widgetSnapshot.v1"
    public static let defaultFileName = "hotwater-widget.json"
    public static let widgetKind = "app.goke.widget.hotWater"

    public var isBound: Bool
    public var dormLabel: String
    public var canUseAmount: Int
    public var isArrearage: Bool
    public var phase: HotWaterPhase
    public var lastCheckAt: Int64
    public var lastActionAt: Int64

    public var balanceYuan: Double {
        Double(canUseAmount) / 100.0
    }

    public init(
        isBound: Bool = false,
        dormLabel: String = "",
        canUseAmount: Int = 0,
        isArrearage: Bool = false,
        phase: HotWaterPhase = .unbound,
        lastCheckAt: Int64 = 0,
        lastActionAt: Int64 = 0
    ) {
        self.isBound = isBound
        self.dormLabel = dormLabel
        self.canUseAmount = canUseAmount
        self.isArrearage = isArrearage
        self.phase = phase
        self.lastCheckAt = lastCheckAt
        self.lastActionAt = lastActionAt
    }

    private enum CodingKeys: String, CodingKey {
        case isBound
        case dormLabel
        case canUseAmount
        case isArrearage
        case phase
        case lastCheckAt
        case lastActionAt
    }

    /// Backward-compatible decoder: tolerates missing/legacy fields or unknown phase strings
    /// instead of failing the entire decode and falsely reverting to `isBound = false`.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let bound = try container.decodeIfPresent(Bool.self, forKey: .isBound) ?? false
        self.isBound = bound
        self.dormLabel = try container.decodeIfPresent(String.self, forKey: .dormLabel) ?? ""
        self.canUseAmount = try container.decodeIfPresent(Int.self, forKey: .canUseAmount) ?? 0
        self.isArrearage = try container.decodeIfPresent(Bool.self, forKey: .isArrearage) ?? false
        if let rawPhase = try container.decodeIfPresent(String.self, forKey: .phase),
           let parsed = HotWaterPhase(rawValue: rawPhase)
        {
            self.phase = parsed
        } else {
            self.phase = bound ? .idle : .unbound
        }
        self.lastCheckAt = try container.decodeIfPresent(Int64.self, forKey: .lastCheckAt) ?? 0
        self.lastActionAt = try container.decodeIfPresent(Int64.self, forKey: .lastActionAt) ?? 0
    }

    public static func load(from defaults: UserDefaults) -> HotWaterWidgetSnapshot {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode(HotWaterWidgetSnapshot.self, from: data)
        else {
            return HotWaterWidgetSnapshot()
        }
        return decoded
    }

    public static func loadIfPresent(from defaults: UserDefaults) -> HotWaterWidgetSnapshot? {
        guard let data = defaults.data(forKey: storageKey) else {
            return nil
        }
        return try? JSONDecoder().decode(HotWaterWidgetSnapshot.self, from: data)
    }

    public func save(to defaults: UserDefaults) {
        if let data = try? JSONEncoder().encode(self) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }

    public static func load(from url: URL) -> HotWaterWidgetSnapshot? {
        guard let data = try? Data(contentsOf: url), !data.isEmpty else {
            return nil
        }
        return try? JSONDecoder().decode(HotWaterWidgetSnapshot.self, from: data)
    }

    public func save(to url: URL) throws {
        let data = try JSONEncoder().encode(self)
        try data.write(to: url, options: .atomic)
    }
}
