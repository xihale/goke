package app.goke.hotwater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotWaterMetricsTest {
    @Test
    fun durationSecondsBetweenHandlesValidReversedAndInvalidInputs() {
        assertEquals(
            325L,
            HotWaterMetrics.durationSecondsBetween("2026-11-25 18:00:00", "2026-11-25 18:05:25"),
        )
        assertEquals(
            0L,
            HotWaterMetrics.durationSecondsBetween("2026-11-25 18:05:25", "2026-11-25 18:00:00"),
        )
        assertNull(HotWaterMetrics.durationSecondsBetween("", "2026-11-25 18:00:00"))
        assertNull(HotWaterMetrics.durationSecondsBetween("2026-11-25 18:00:00", "invalid"))
    }

    @Test
    fun estimateLitersAndCostYuanHandlePositiveAndZeroValues() {
        assertEquals(25.0, HotWaterMetrics.estimateLiters(25, 0.99), 1e-6)
        assertEquals(
            1.0 / HotWaterMetrics.DEFAULT_YUAN_PER_LITER,
            HotWaterMetrics.estimateLiters(0, 1.0),
            1e-6,
        )
        assertEquals(0.0, HotWaterMetrics.estimateLiters(0, 0.0), 1e-6)

        assertEquals(0.0, HotWaterMetrics.estimateCostYuan(0), 1e-6)
        assertEquals(0.0, HotWaterMetrics.estimateCostYuan(-3), 1e-6)
        assertEquals(0.02, HotWaterMetrics.estimateCostYuan(1), 1e-6)
        assertEquals(0.05, HotWaterMetrics.estimateCostYuan(2), 1e-6)
        assertEquals(0.49, HotWaterMetrics.estimateCostYuan(20), 1e-6)
    }

    @Test
    fun estimateForOrderMatchesExactAndFiveSecondWindowAndFallsBackWhenUnmatched() {
        val local =
            HotWaterShowerRecord(
                consumeId = 101L,
                startTime = "2026-11-25 18:00:00",
                endTime = "2026-11-25 18:05:00",
                durationSec = 300L,
                useAmount = 24,
                costYuan = 0.58,
                liters = 24.0,
            )

        val exactOrder = HotWaterOrder("O1", "0.58", 1, "2026-11-25 18:00:00", "2026-11-25 18:05:01")
        val exactMetrics = HotWaterMetrics.estimateForOrder(exactOrder, listOf(local))
        assertEquals(300L, exactMetrics.durationSec)
        assertEquals(24.0, exactMetrics.liters, 1e-6)

        val nearOrder = HotWaterOrder("O2", "0.58", 1, "2026-11-25 18:00:05", "2026-11-25 18:05:05")
        val nearMetrics = HotWaterMetrics.estimateForOrder(nearOrder, listOf(local))
        assertEquals(300L, nearMetrics.durationSec)

        val farOrder = HotWaterOrder("O3", "0.49", 1, "2026-11-25 18:00:06", "2026-11-25 18:05:06")
        val farMetrics = HotWaterMetrics.estimateForOrder(farOrder, listOf(local))
        assertNull(farMetrics.durationSec)
        assertEquals(0.49 / HotWaterMetrics.DEFAULT_YUAN_PER_LITER, farMetrics.liters, 1e-6)

        val zeroLitersLocal = local.copy(liters = 0.0)
        val fallbackLitersMetrics = HotWaterMetrics.estimateForOrder(exactOrder, listOf(zeroLitersLocal))
        assertEquals(0.58 / HotWaterMetrics.DEFAULT_YUAN_PER_LITER, fallbackLitersMetrics.liters, 1e-6)

        val zeroOrder = HotWaterOrder("O4", "bad", 0, "2026-11-25 19:00:00", "")
        val zeroMetrics = HotWaterMetrics.estimateForOrder(zeroOrder, emptyList())
        assertNull(zeroMetrics.durationSec)
        assertEquals(0.0, zeroMetrics.liters, 1e-6)
    }
}
