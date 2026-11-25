package app.goke.core.jxfw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JxfwCourseSelectionTest {
    @Test
    fun summarizeAndFormatWeeksRangeWorks() {
        val slots =
            listOf(
                XkSessionSlot("1", week = 1, dayOfWeek = 2, periods = listOf(3, 4), room = "教3-201(城)", roomCode = "011030201"),
                XkSessionSlot("1", week = 2, dayOfWeek = 2, periods = listOf(3, 4), room = "教3-201(城)", roomCode = "011030201"),
                XkSessionSlot("1", week = 3, dayOfWeek = 2, periods = listOf(3, 4), room = "教3-201(城)", roomCode = "011030201"),
                XkSessionSlot("1", week = 5, dayOfWeek = 2, periods = listOf(3, 4), room = "教3-201(城)", roomCode = "011030201"),
            )
        val summary = JxfwXkParse.summarizeSchedule(slots)
        assertEquals(1, summary.size)
        assertEquals(setOf(1, 2, 3, 5), summary[0].weeks)
        assertEquals("大学城", summary[0].campusHint)
        assertEquals("1-3,5周", JxfwXkParse.formatWeeksRange(summary[0].weeks))
    }

    @Test
    fun mergeOccupiedSlotsAndSlotConflictsAndBuildSchedulePreview() {
        val jxfwOccupied =
            listOf(
                XkOccupiedSlot(week = 1, dayOfWeek = 1, period = 1, courseName = "高等数学", source = XkConflictSource.JXFW),
                XkOccupiedSlot(week = 1, dayOfWeek = 1, period = 2, courseName = "高等数学", source = XkConflictSource.JXFW),
                XkOccupiedSlot(week = 5, dayOfWeek = 1, period = 7, courseName = "高等数学", source = XkConflictSource.JXFW),
                XkOccupiedSlot(week = 5, dayOfWeek = 1, period = 8, courseName = "高等数学", source = XkConflictSource.JXFW),
            )
        val localOccupied =
            listOf(
                XkOccupiedSlot(week = 1, dayOfWeek = 1, period = 1, courseName = "高等数学 ", source = XkConflictSource.LOCAL_TIMETABLE),
                XkOccupiedSlot(week = 2, dayOfWeek = 3, period = 3, courseName = "大学物理", source = XkConflictSource.LOCAL_TIMETABLE),
                XkOccupiedSlot(week = 2, dayOfWeek = 3, period = 4, courseName = "大学物理", source = XkConflictSource.LOCAL_TIMETABLE),
            )

        val unified = JxfwXkParse.mergeOccupiedSlots(jxfwOccupied, localOccupied)
        assertEquals(6, unified.size)
        assertEquals(
            XkConflictSource.JXFW,
            unified.first { it.week == 1 && it.dayOfWeek == 1 && it.period == 1 }.source,
        )

        val candidate = XkCourseItem(kcrwdm = "900", name = "工程伦理", capacity = 50, enrolledCount = 10)
        val slots =
            listOf(
                XkSessionSlot("900", week = 1, dayOfWeek = 1, periods = listOf(1, 2), room = "教1-101"),
                XkSessionSlot("900", week = 2, dayOfWeek = 1, periods = listOf(1, 2), room = "教1-101"),
                XkSessionSlot("900", week = 5, dayOfWeek = 1, periods = listOf(7, 8), room = "教1-101"),
                XkSessionSlot("900", week = 6, dayOfWeek = 1, periods = listOf(7, 8), room = "教1-101"),
            )

        val merged =
            JxfwXkParse.mergeCourseData(
                items = listOf(candidate),
                enrolledIds = emptySet(),
                slotsByTask = mapOf("900" to slots),
                jxfwOccupied = jxfwOccupied,
                localOccupied = localOccupied,
            )
        val slotConflicts = merged[0].slotConflicts
        assertEquals(2, slotConflicts.size)
        assertEquals(
            XkSlotConflict(
                dayOfWeek = 1,
                periods = listOf(1, 2),
                weeks = setOf(1),
                courseName = "高等数学",
                source = XkConflictSource.JXFW,
            ),
            slotConflicts[0],
        )
        assertEquals(
            XkSlotConflict(
                dayOfWeek = 1,
                periods = listOf(7, 8),
                weeks = setOf(5),
                courseName = "高等数学",
                source = XkConflictSource.JXFW,
            ),
            slotConflicts[1],
        )

        val preview =
            JxfwXkParse.buildSchedulePreview(
                occupiedSlots = unified,
                candidate = candidate,
                calendarOverride = slots,
            )
        assertEquals(setOf(1, 2, 5, 6), preview.candidateWeeks)
        assertEquals(10, preview.maxPeriod)
        assertEquals(2, preview.candidateBlocks.size)
        assertTrue(preview.candidateBlocks.all { it.isCandidate && it.hasConflict })
        assertEquals(3, preview.occupiedBlocks.size)
        assertTrue(preview.occupiedBlocks.none { it.isCandidate })
        assertEquals(setOf(1 to 1, 1 to 2, 1 to 7, 1 to 8), preview.conflictCells.keys)
    }

    @Test
    fun buildSchedulePreviewOrdersOverlappingOccupiedLastAndScopesCandidateConflictsByWeeks() {
        val occupied =
            listOf(
                XkOccupiedSlot(week = 1, dayOfWeek = 2, period = 3, courseName = "重合周课程", source = XkConflictSource.JXFW),
                XkOccupiedSlot(week = 1, dayOfWeek = 2, period = 4, courseName = "重合周课程", source = XkConflictSource.JXFW),
                XkOccupiedSlot(week = 9, dayOfWeek = 2, period = 3, courseName = "非重合周课程", source = XkConflictSource.LOCAL_TIMETABLE),
                XkOccupiedSlot(week = 9, dayOfWeek = 2, period = 4, courseName = "非重合周课程", source = XkConflictSource.LOCAL_TIMETABLE),
            )
        val candidate = XkCourseItem(kcrwdm = "901", name = "专题研讨")
        val slots =
            listOf(
                XkSessionSlot("901", week = 1, dayOfWeek = 2, periods = listOf(3, 4), room = "教A-101"),
                XkSessionSlot("901", week = 2, dayOfWeek = 2, periods = listOf(3, 4), room = "教B-202"),
            )

        val preview =
            JxfwXkParse.buildSchedulePreview(
                occupiedSlots = occupied,
                candidate = candidate,
                calendarOverride = slots,
            )

        assertEquals(listOf("非重合周课程", "重合周课程"), preview.occupiedBlocks.map { it.title })

        val blockA = preview.candidateBlocks.first { it.room == "教A-101" }
        val blockB = preview.candidateBlocks.first { it.room == "教B-202" }
        assertTrue(blockA.hasConflict)
        assertFalse(blockB.hasConflict)
    }
}
