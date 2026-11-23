package app.goke.ui

import androidx.compose.ui.unit.dp
import app.goke.core.model.GdutPeriods
import app.goke.ui.screens.calcTimetableNowY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class TimetableTimeIndicatorTest {
    private val periods = GdutPeriods.default
    private val rowH = 60.dp

    @Test
    fun returnsNullOutsideFirstStartAndLastEnd() {
        assertNull(calcTimetableNowY(periods, LocalTime.of(8, 29), rowH))
        assertNull(calcTimetableNowY(periods, LocalTime.of(21, 41), rowH))
        assertNull(calcTimetableNowY(emptyList(), LocalTime.of(10, 0), rowH))
    }

    @Test
    fun interpolatesLinearlyInsidePeriod() {
        val yStart = calcTimetableNowY(periods, LocalTime.of(8, 30), rowH)
        val yMid = calcTimetableNowY(periods, LocalTime.of(8, 52, 30), rowH)
        val yEnd = calcTimetableNowY(periods, LocalTime.of(9, 15), rowH)

        assertEquals(0f, yStart!!.value, 0.01f)
        assertEquals(30f, yMid!!.value, 0.5f)
        assertEquals(60f, yEnd!!.value, 0.01f)
    }

    @Test
    fun interpolatesAcrossLunchGroupGap() {
        val p4End = periods[3].end // 12:05
        val p5Start = periods[4].start // 13:50
        val midMinutes = Duration.between(p4End, p5Start).toMinutes() / 2
        val midLunch = p4End.plusMinutes(midMinutes)

        val yAtP4End = calcTimetableNowY(periods, p4End, rowH)
        val yAtMidLunch = calcTimetableNowY(periods, midLunch, rowH)
        val yAtP5Start = calcTimetableNowY(periods, p5Start, rowH)

        assertNotNull(yAtP4End)
        assertNotNull(yAtMidLunch)
        assertNotNull(yAtP5Start)
        assertEquals(240f, yAtP4End!!.value, 0.01f)
        assertEquals(247f, yAtMidLunch!!.value, 0.2f)
        assertEquals(254f, yAtP5Start!!.value, 0.01f)
    }

    @Test
    fun pinsShortNonBoundaryBreakToBoundary() {
        val p1End = periods[0].end // 09:15
        val p2Start = periods[1].start // 09:20
        val breakMid = p1End.plusMinutes(2)
        assertEquals(60f, calcTimetableNowY(periods, breakMid, rowH)!!.value, 0.01f)
        assertEquals(60f, calcTimetableNowY(periods, p2Start, rowH)!!.value, 0.01f)
    }
}
