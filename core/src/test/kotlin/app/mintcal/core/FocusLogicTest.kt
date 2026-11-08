package app.mintcal.core

import app.mintcal.core.focus.FocusLogic
import app.mintcal.core.model.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FocusLogicTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun session(
        id: String,
        startEpoch: Long,
        endEpoch: Long,
        taskId: String? = "t1",
        paused: Long = 0,
    ) = FocusSession(id, taskId, startEpoch, endEpoch, pausedMillis = paused)

    /** 2026-11-05 14:00 (+08:00) 起。 */
    private val base = Instant.parse("2026-11-05T06:00:00Z").toEpochMilli()

    @Test fun `focusMillis excludes paused time`() {
        assertEquals(60_000, session("a", 0, 120_000, paused = 60_000).focusMillis)
        assertEquals(0, session("b", 0, 30_000, paused = 60_000).focusMillis)
    }

    @Test fun `ofTask filters and sorts newest first`() {
        val s = listOf(session("old", base, base + 1), session("new", base + 5, base + 6), session("free", base, base + 1, taskId = null))
        assertEquals(listOf("new", "old"), FocusLogic.ofTask(s, "t1").map { it.id })
        assertEquals(listOf("free"), FocusLogic.ofTask(s, null).map { it.id })
    }

    @Test fun `totalMillis sums focus time only`() {
        val s =
            listOf(
                session("a", 0, 25 * 60_000),
                session("b", 0, 25 * 60_000, paused = 5 * 60_000),
                session("c", 0, 10 * 60_000, taskId = null),
            )
        assertEquals(55 * 60_000L, FocusLogic.totalMillis(s))
    }

    @Test fun `byDay groups desc and splits midnight`() {
        val day2 = base
        val day1 = base - 24 * 3600_000L
        val s =
            listOf(
                session("d1a", day1 + 3_600_000, day1 + 7_200_000),
                session("d1b", day1, day1 + 1_800_000),
                session("d2", day2, day2 + 1_500_000),
            )
        val byDay = FocusLogic.byDay(s, zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4)), byDay.keys.toList())
        assertEquals(listOf("d1a", "d1b"), byDay[LocalDate.of(2026, 11, 4)]!!.map { it.id })
    }

    @Test fun `byHour buckets by start hour and sums focus millis`() {
        val s =
            listOf(
                session("a", base, base + 20 * 60_000), // 14 点
                session("b", base + 3600_000, base + 3600_000 + 10 * 60_000), // 15 点
            )
        val hist = FocusLogic.byHour(s, zone)
        assertEquals(20 * 60_000L, hist[14])
        assertEquals(10 * 60_000L, hist[15])
        assertEquals(0L, hist[13])
    }
}
