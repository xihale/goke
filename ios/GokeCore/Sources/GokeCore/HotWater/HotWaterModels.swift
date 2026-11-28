import Foundation
import GokeCoreModel

// MARK: - Error Enums

public enum HotWaterErrorCode: String, Sendable, Hashable, Codable {
    case none = "NONE"
    case notBound = "NOT_BOUND"
    case noMeterInfo = "NO_METER_INFO"
    case noBlePermission = "NO_BLE_PERMISSION"
    case bluetoothDisabled = "BLUETOOTH_DISABLED"
    case deviceNotFound = "DEVICE_NOT_FOUND"
    case deviceDisabled = "DEVICE_DISABLED"
    case authFailed = "AUTH_FAILED"
    case networkError = "NETWORK_ERROR"
    case bleError = "BLE_ERROR"
    case busy = "BUSY"
    case unsupported = "UNSUPPORTED"
}

/// Platform-neutral Bluetooth authorization status for SwiftUI permission banners.
public enum HotWaterBleAuthStatus: String, Sendable, Equatable {
    case notDetermined
    case restricted
    case denied
    case allowedAlways
}

// MARK: - API Result Wrapper

public enum HotWaterApiResult<T: Sendable>: Sendable {
    case ok(T)
    case err(code: HotWaterErrorCode, detail: String = "")

    public var isOk: Bool {
        if case .ok = self { return true }
        return false
    }

    public var value: T? {
        if case .ok(let v) = self { return v }
        return nil
    }
}

// MARK: - Config, State & Operations

/// Non-sensitive hot water configuration and meter metadata.
public struct HotWaterConfig: Sendable, Hashable, Codable {
    public static let defaultArrearsThresholdYuan = 5

    public var phone: String
    public var machineSn: String
    public var bluetoothName: String
    public var schoolName: String
    public var houseName: String
    public var dormitoryNo: String
    /// Cached BLE peripheral UUID string (iOS replacement for Android's BLE MAC address).
    public var blePeripheralUUID: String
    public var arrearsTodoEnabled: Bool
    public var arrearsThresholdYuan: Int
    public var showerSummaryNotifyEnabled: Bool

    public init(
        phone: String = "",
        machineSn: String = "",
        bluetoothName: String = "",
        schoolName: String = "",
        houseName: String = "",
        dormitoryNo: String = "",
        blePeripheralUUID: String = "",
        arrearsTodoEnabled: Bool = false,
        arrearsThresholdYuan: Int = HotWaterConfig.defaultArrearsThresholdYuan,
        showerSummaryNotifyEnabled: Bool = true
    ) {
        self.phone = phone
        self.machineSn = machineSn
        self.bluetoothName = bluetoothName
        self.schoolName = schoolName
        self.houseName = houseName
        self.dormitoryNo = dormitoryNo
        self.blePeripheralUUID = blePeripheralUUID
        self.arrearsTodoEnabled = arrearsTodoEnabled
        self.arrearsThresholdYuan = min(max(arrearsThresholdYuan, 1), 200)
        self.showerSummaryNotifyEnabled = showerSummaryNotifyEnabled
    }

    public var dormLabel: String {
        [houseName, dormitoryNo]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    public var hasMeter: Bool {
        !machineSn.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !bluetoothName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// Single bill / consumption order returned by the hot water server.
public struct HotWaterOrder: Sendable, Hashable, Codable, Identifiable {
    public var orderId: String
    public var usePrice: String
    public var status: Int
    public var createTime: String
    public var payTime: String

    public var id: String { orderId }
    public var isPaid: Bool { status == 1 }

    public init(
        orderId: String,
        usePrice: String,
        status: Int,
        createTime: String,
        payTime: String
    ) {
        self.orderId = orderId
        self.usePrice = usePrice
        self.status = status
        self.createTime = createTime
        self.payTime = payTime
    }
}

/// Paginated bill list response.
public struct HotWaterOrderPage: Sendable, Hashable, Codable {
    public var orders: [HotWaterOrder]
    public var totalAmount: Double?
    public var hasMore: Bool

    public init(
        orders: [HotWaterOrder] = [],
        totalAmount: Double? = nil,
        hasMore: Bool = false
    ) {
        self.orders = orders
        self.totalAmount = totalAmount
        self.hasMore = hasMore
    }
}

/// Immutable state snapshot observed by SwiftUI screens and mirrored to WidgetKit.
public struct HotWaterState: Sendable, Hashable, Codable {
    public var isBound: Bool
    public var userIdMasked: String
    public var config: HotWaterConfig
    public var phase: HotWaterPhase
    public var errorCode: HotWaterErrorCode
    public var errorDetail: String
    public var isArrearage: Bool
    public var arrearsYuan: Double
    /// Available balance in cents (fen), matching Android `CanUseAmount`.
    public var canUseAmount: Int
    public var lastCheckAt: Int64
    public var lastActionAt: Int64
    public var busy: Bool

    public var balanceYuan: Double {
        Double(canUseAmount) / 100.0
    }

    public init(
        isBound: Bool = false,
        userIdMasked: String = "",
        config: HotWaterConfig = HotWaterConfig(),
        phase: HotWaterPhase = .unbound,
        errorCode: HotWaterErrorCode = .none,
        errorDetail: String = "",
        isArrearage: Bool = false,
        arrearsYuan: Double = 0.0,
        canUseAmount: Int = 0,
        lastCheckAt: Int64 = 0,
        lastActionAt: Int64 = 0,
        busy: Bool = false
    ) {
        self.isBound = isBound
        self.userIdMasked = userIdMasked
        self.config = config
        self.phase = phase
        self.errorCode = errorCode
        self.errorDetail = errorDetail
        self.isArrearage = isArrearage
        self.arrearsYuan = arrearsYuan
        self.canUseAmount = canUseAmount
        self.lastCheckAt = lastCheckAt
        self.lastActionAt = lastActionAt
        self.busy = busy
    }
}

public struct HotWaterOpResult: Sendable, Hashable, Codable {
    public var ok: Bool
    public var phase: HotWaterPhase
    public var errorCode: HotWaterErrorCode
    public var detail: String

    public init(
        ok: Bool,
        phase: HotWaterPhase,
        errorCode: HotWaterErrorCode = .none,
        detail: String = ""
    ) {
        self.ok = ok
        self.phase = phase
        self.errorCode = errorCode
        self.detail = detail
    }
}

/// Locally cached shower completion summary (up to 100 entries, newest first).
public struct HotWaterShowerRecord: Sendable, Hashable, Codable, Identifiable {
    public var consumeId: Int64
    public var startTime: String
    public var endTime: String
    public var durationSec: Int64
    public var useAmount: Int
    public var costYuan: Double
    public var liters: Double

    public var id: String { "\(consumeId)-\(startTime)" }

    public init(
        consumeId: Int64,
        startTime: String,
        endTime: String,
        durationSec: Int64,
        useAmount: Int,
        costYuan: Double,
        liters: Double
    ) {
        self.consumeId = consumeId
        self.startTime = startTime
        self.endTime = endTime
        self.durationSec = durationSec
        self.useAmount = useAmount
        self.costYuan = costYuan
        self.liters = liters
    }
}

/// Computed real shower duration (when matched with local record) and estimated liters for a bill.
public struct HotWaterOrderMetrics: Sendable, Hashable, Codable {
    public var durationSec: Int64?
    public var liters: Double

    public init(durationSec: Int64?, liters: Double) {
        self.durationSec = durationSec
        self.liters = liters
    }
}

// MARK: - WidgetKit Shared Snapshot

extension HotWaterWidgetSnapshot {
    public init(from state: HotWaterState) {
        self.init(
            isBound: state.isBound,
            dormLabel: state.config.dormLabel,
            canUseAmount: state.canUseAmount,
            isArrearage: state.isArrearage,
            phase: state.phase,
            lastCheckAt: state.lastCheckAt,
            lastActionAt: state.lastActionAt
        )
    }
}
