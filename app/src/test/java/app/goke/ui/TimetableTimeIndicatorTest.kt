package app.goke.ui

import androidx.compose.ui.unit.dp
import app.goke.core.model.GdutPeriods
import app.goke.ui.screens.NowBarBlock
import app.goke.ui.screens.calcTimetableNowBarSpan
import app.goke.ui.screens.calcTimetableNowY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import kotlin.math.hypot

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

    @Test
    fun barSpanKeepsDefaultWidthOutsideBlocksAndInStraightBody() {
        val block = NowBarBlock(left = 1.5f, top = 2f, right = 45f, bottom = 58f, cornerRadius = 12f)
        val above = calcTimetableNowBarSpan(3f, 43.5f, 1.5f, 1.5f, listOf(block))
        assertEquals(3f, above.first, 0.01f)
        assertEquals(43.5f, above.second, 0.01f)

        val mid = calcTimetableNowBarSpan(3f, 43.5f, 30f, 1.5f, listOf(block))
        assertEquals(3f, mid.first, 0.01f)
        assertEquals(43.5f, mid.second, 0.01f)

        val below = calcTimetableNowBarSpan(3f, 43.5f, 59f, 1.5f, listOf(block))
        assertEquals(3f, below.first, 0.01f)
        assertEquals(43.5f, below.second, 0.01f)
    }

    @Test
    fun barSpanFollowsConcentricRoundedCornersAtTopAndBottom() {
        val block = NowBarBlock(left = 1.5f, top = 2f, right = 45f, bottom = 58f, cornerRadius = 12f)
        val leftCenter = block.left + block.cornerRadius
        val topCenter = block.top + block.cornerRadius
        val bottomCenter = block.bottom - block.cornerRadius

        // Top flat-edge threshold (top + 1.5f) and inside curved corner (top + 6f)
        val atTopEdge = calcTimetableNowBarSpan(3f, 43.5f, 2f, 1.5f, listOf(block))
        assertEquals(leftCenter, atTopEdge.first, 0.01f)
        assertEquals(block.right - block.cornerRadius, atTopEdge.second, 0.01f)

        val inTopCorner = calcTimetableNowBarSpan(3f, 43.5f, 8f, 1.5f, listOf(block))
        assertTrue(inTopCorner.first > 3f)
        assertTrue(inTopCorner.second < 43.5f)
        assertEquals(10.5f, hypot(leftCenter - inTopCorner.first, topCenter - 8f), 0.02f)

        val inBottomCorner = calcTimetableNowBarSpan(3f, 43.5f, 52f, 1.5f, listOf(block))
        assertEquals(inTopCorner.first, inBottomCorner.first, 0.01f)
        assertEquals(inTopCorner.second, inBottomCorner.second, 0.01f)
        assertEquals(10.5f, hypot(leftCenter - inBottomCorner.first, 52f - bottomCenter), 0.02f)
    }

    @Test
    fun barSpanHandlesStackedFrontAndPeekBlocks() {
        val front = NowBarBlock(left = 1.5f, top = 2f, right = 39.5f, bottom = 58f, cornerRadius = 12f, stackedFront = true)
        val peek = NowBarBlock(left = 11.5f, top = 5f, right = 45f, bottom = 55f, cornerRadius = 12f, stackedFront = false)
        val blocks = listOf(front, peek)

        // Above peek.top: follows front's top-left & top-right corners
        val abovePeek = calcTimetableNowBarSpan(3f, 43.5f, 3f, 1.5f, blocks)
        assertEquals(front.left + 12f, abovePeek.first, 0.01f)
        assertEquals(front.right - 12f, abovePeek.second, 0.01f)

        // Inside peek's top-right corner: left follows front, right follows outer peek corner
        val inPeekCorner = calcTimetableNowBarSpan(3f, 43.5f, 9f, 1.5f, blocks)
        assertTrue(inPeekCorner.first > 3f)
        assertTrue(inPeekCorner.second < 43.5f)
        assertEquals(10.5f, hypot((peek.right - 12f) - inPeekCorner.second, (peek.top + 12f) - 9f), 0.02f)

        // Middle of stacked cluster: full width across front + peek
        val mid = calcTimetableNowBarSpan(3f, 43.5f, 30f, 1.5f, blocks)
        assertEquals(3f, mid.first, 0.01f)
        assertEquals(43.5f, mid.second, 0.01f)
    }
}
