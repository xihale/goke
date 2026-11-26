package app.goke.ui

import androidx.compose.ui.unit.dp
import app.goke.ui.screens.LaneBox
import app.goke.ui.screens.Slot
import app.goke.ui.screens.layoutSlots
import app.goke.ui.screens.slotBlockSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableStackLayoutTest {
    @Test
    fun stackedFrontPreservesFullColumnWidth() {
        val colW = 46.dp
        val normal = slotBlockSpan(colW, Slot(stacked = false))
        val stacked = slotBlockSpan(colW, Slot(stacked = true, overlap = 2))

        assertEquals(normal.second, stacked.second)
        assertEquals(43f, stacked.second.value, 0.01f)
        assertEquals(0.5f, stacked.first.value, 0.01f)
    }

    @Test
    fun backCardsPreserveActualTimeSpans() {
        val colW = 46.dp
        val front = LaneBox(day = 1, top = 2.dp, bottom = 238.dp, colorIndex = 2, course = true)
        val p1Back = LaneBox(day = 1, top = 2.dp, bottom = 58.dp, colorIndex = 1, course = false)
        val layout1 = layoutSlots(listOf(front, p1Back), colW)

        assertEquals(1, layout1.peeks.size)
        assertEquals(2.dp, layout1.peeks[0].top)
        assertEquals(58.dp, layout1.peeks[0].bottom)
        assertEquals(1, layout1.peeks[0].depth)
        assertEquals(1, layout1.peeks[0].clusterMaxDepth)

        val p34Back = LaneBox(day = 1, top = 122.dp, bottom = 238.dp, colorIndex = 4, course = false)
        val layout2 = layoutSlots(listOf(front, p34Back), colW)

        assertEquals(1, layout2.peeks.size)
        assertEquals(122.dp, layout2.peeks[0].top)
        assertEquals(238.dp, layout2.peeks[0].bottom)

        val shortFront = LaneBox(day = 2, top = 2.dp, bottom = 118.dp, colorIndex = 0, course = true)
        val longBack = LaneBox(day = 2, top = 62.dp, bottom = 238.dp, colorIndex = 3, course = false)
        val layout3 = layoutSlots(listOf(shortFront, longBack), colW)

        assertEquals(1, layout3.peeks.size)
        assertEquals(62.dp, layout3.peeks[0].top)
        assertEquals(238.dp, layout3.peeks[0].bottom)
    }

    @Test
    fun depthLayersPackDisjointIntervalsAndCapAtTwoBackLayers() {
        val colW = 46.dp
        val front = LaneBox(day = 1, top = 2.dp, bottom = 238.dp, colorIndex = 2, course = true)
        val backMorning1 = LaneBox(day = 1, top = 2.dp, bottom = 58.dp, colorIndex = 1, course = false)
        val backMorning2 = LaneBox(day = 1, top = 122.dp, bottom = 238.dp, colorIndex = 4, course = false)

        val disjointLayout = layoutSlots(listOf(front, backMorning1, backMorning2), colW)
        assertEquals(2, disjointLayout.peeks.size)
        assertTrue(disjointLayout.peeks.all { it.depth == 1 && it.clusterMaxDepth == 1 })

        val overlap1 = LaneBox(day = 3, top = 2.dp, bottom = 118.dp, colorIndex = 1, course = false)
        val overlap2 = LaneBox(day = 3, top = 2.dp, bottom = 118.dp, colorIndex = 3, course = false)
        val overlap3 = LaneBox(day = 3, top = 2.dp, bottom = 118.dp, colorIndex = 5, course = false)
        val overlapLayout = layoutSlots(listOf(front.copy(day = 3, bottom = 118.dp), overlap1, overlap2, overlap3), colW)

        assertEquals(2, overlapLayout.peeks.size)
        assertEquals(listOf(1, 2), overlapLayout.peeks.map { it.depth })
        assertTrue(overlapLayout.peeks.all { it.clusterMaxDepth == 2 })
        assertEquals(4, overlapLayout.slots[0].overlap)
        assertEquals(1, overlapLayout.groups.size)
        assertEquals(4, overlapLayout.groups[0].size)
    }

    @Test
    fun wideColumnStillUsesSideBySideLanes() {
        val colW = 90.dp
        val first = LaneBox(day = 1, top = 2.dp, bottom = 118.dp, colorIndex = 0, course = true)
        val second = LaneBox(day = 1, top = 2.dp, bottom = 58.dp, colorIndex = 1, course = false)

        val layout = layoutSlots(listOf(first, second), colW)

        assertTrue(layout.peeks.isEmpty())
        assertEquals(2, layout.slots[0].lanes)
        assertEquals(false, layout.slots[0].stacked)
        assertEquals(0, layout.slots[0].lane)
        assertEquals(1, layout.slots[1].lane)
    }
}
