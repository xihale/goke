import Foundation
import Testing
@testable import GokeCore

@Suite("Hot Water Domain & Metrics")
struct HotWaterTests {
    @Test("HotWaterMetrics duration, cost, liters, and +/-5s local record matching")
    func metricsCalculationAndOrderMatching() {
        #expect(HotWaterMetrics.durationSecondsBetween(startTime: "2026-11-25 18:10:05", endTime: "2026-11-25 18:15:30") == 325)
        #expect(HotWaterMetrics.estimateCostYuan(useAmount: 100) == 2.45)
        #expect(HotWaterMetrics.estimateLiters(useAmount: 50, costYuan: 1.23) == 50.0)

        let local = HotWaterShowerRecord(
            consumeId: 1,
            startTime: "2026-11-25 18:10:05",
            endTime: "2026-11-25 18:15:30",
            durationSec: 325,
            useAmount: 42,
            costYuan: 1.03,
            liters: 42.0
        )
        let orderWithin5s = HotWaterOrder(
            orderId: "ord-1",
            usePrice: "1.03",
            status: 1,
            createTime: "2026-11-25 18:10:08",
            payTime: "2026-11-25 18:16:00"
        )
        let matched = HotWaterMetrics.estimateForOrder(orderWithin5s, localRecords: [local])
        #expect(matched.durationSec == 325)
        #expect(matched.liters == 42.0)

        let unmatchedOrder = HotWaterOrder(
            orderId: "ord-2",
            usePrice: "2.454",
            status: 0,
            createTime: "2026-11-25 20:00:00",
            payTime: ""
        )
        let unmatched = HotWaterMetrics.estimateForOrder(unmatchedOrder, localRecords: [local])
        #expect(unmatched.durationSec == nil)
        #expect(abs(unmatched.liters - 100.0) < 0.01)
    }

    @Test("HotWaterConfig and HotWaterState helpers clamp thresholds and format labels")
    func configAndStateHelpers() {
        let cfg = HotWaterConfig(
            machineSn: "11223344556677",
            houseName: "西三",
            dormitoryNo: "408",
            arrearsThresholdYuan: 500
        )
        #expect(cfg.dormLabel == "西三 408")
        #expect(cfg.hasMeter)
        #expect(cfg.arrearsThresholdYuan == 200)

        let state = HotWaterState(isBound: true, config: cfg, phase: .idle, canUseAmount: 1580)
        #expect(abs(state.balanceYuan - 15.80) < 0.001)
    }

    @Test("HotWaterWidgetSnapshot decodes legacy/partial JSON and round-trips via file")
    func widgetSnapshotBackwardCompatibleDecodeAndFileRoundTrip() throws {
        let partialJSON = Data(#"{"isBound":true,"dormLabel":"西三 408","canUseAmount":1850,"phase":"FUTURE_PHASE"}"#.utf8)
        let decoded = try JSONDecoder().decode(HotWaterWidgetSnapshot.self, from: partialJSON)
        #expect(decoded.isBound == true)
        #expect(decoded.dormLabel == "西三 408")
        #expect(decoded.canUseAmount == 1850)
        #expect(decoded.isArrearage == false)
        #expect(decoded.phase == .idle)
        #expect(decoded.lastCheckAt == 0)
        #expect(decoded.lastActionAt == 0)

        let emptyJSON = Data("{}".utf8)
        let unbound = try JSONDecoder().decode(HotWaterWidgetSnapshot.self, from: emptyJSON)
        #expect(unbound.isBound == false)
        #expect(unbound.phase == .unbound)

        let tmpDir = FileManager.default.temporaryDirectory.appendingPathComponent("goke-hw-snap-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tmpDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tmpDir) }

        let fileURL = tmpDir.appendingPathComponent(HotWaterWidgetSnapshot.defaultFileName)
        let full = HotWaterWidgetSnapshot(
            isBound: true,
            dormLabel: "西四 408",
            canUseAmount: 2450,
            isArrearage: false,
            phase: .valveOpen,
            lastCheckAt: 1_700_000_000_000,
            lastActionAt: 1_700_000_001_000
        )
        try full.save(to: fileURL)
        let loaded = try #require(HotWaterWidgetSnapshot.load(from: fileURL))
        #expect(loaded == full)
    }
}
