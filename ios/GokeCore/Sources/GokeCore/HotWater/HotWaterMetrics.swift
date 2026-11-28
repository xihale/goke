import Foundation
import GokeCoreModel

/// Hot water usage, cost, and real shower duration helpers (estimated at 0.02454 CNY/L).
public enum HotWaterMetrics {
    public static let defaultYuanPerLiter: Double = 0.02454

    public static func durationSecondsBetween(startTime: String, endTime: String) -> Int64? {
        guard let s = parseDateTimeSeconds(startTime),
              let e = parseDateTimeSeconds(endTime)
        else {
            return nil
        }
        return max(0, e - s)
    }

    public static func estimateLiters(useAmount: Int, costYuan: Double) -> Double {
        if useAmount > 0 {
            return Double(useAmount)
        }
        if costYuan > 0.0 {
            return costYuan / defaultYuanPerLiter
        }
        return 0.0
    }

    public static func estimateCostYuan(useAmount: Int) -> Double {
        guard useAmount > 0 else { return 0.0 }
        return (Double(useAmount) * defaultYuanPerLiter * 100.0).rounded() / 100.0
    }

    public static func estimateForOrder(
        _ order: HotWaterOrder,
        localRecords: [HotWaterShowerRecord]
    ) -> HotWaterOrderMetrics {
        let price = Double(order.usePrice.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0.0
        let orderSec = parseDateTimeSeconds(order.createTime)

        let matched = localRecords.first { rec in
            if rec.startTime == order.createTime {
                return true
            }
            if let orderSec, let recSec = parseDateTimeSeconds(rec.startTime) {
                return abs(recSec - orderSec) <= 5
            }
            return false
        }

        if let matched {
            let liters = matched.liters > 0.0 ? matched.liters : estimateLiters(useAmount: 0, costYuan: price)
            return HotWaterOrderMetrics(durationSec: matched.durationSec, liters: liters)
        }

        let liters = price > 0.0 ? price / defaultYuanPerLiter : 0.0
        return HotWaterOrderMetrics(durationSec: nil, liters: liters)
    }

    /// Parses `"yyyy-MM-dd HH:mm:ss"` into UTC epoch seconds (calendar-consistent across Linux & iOS).
    private static func parseDateTimeSeconds(_ raw: String) -> Int64? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count == 19 else { return nil }
        let chars = Array(trimmed)
        guard chars[4] == "-", chars[7] == "-", chars[10] == " ", chars[13] == ":", chars[16] == ":" else {
            return nil
        }
        func parseInt(_ start: Int, _ len: Int) -> Int? {
            Int(String(chars[start..<(start + len)]))
        }
        guard let year = parseInt(0, 4),
              let month = parseInt(5, 2), (1...12).contains(month),
              let day = parseInt(8, 2), (1...31).contains(day),
              let hour = parseInt(11, 2), (0...23).contains(hour),
              let minute = parseInt(14, 2), (0...59).contains(minute),
              let second = parseInt(17, 2), (0...59).contains(second)
        else {
            return nil
        }
        var comps = DateComponents()
        comps.calendar = Calendar(identifier: .gregorian)
        comps.timeZone = TimeZone(secondsFromGMT: 0)
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = hour
        comps.minute = minute
        comps.second = second
        guard let date = comps.date else { return nil }
        return Int64(date.timeIntervalSince1970)
    }
}
