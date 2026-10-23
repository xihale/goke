package app.mintcal.core

import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.Event
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.recurrence.EventExpander
import app.mintcal.core.recurrence.RecurrenceEngine
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeAndRecurrenceTest {
    private val cal = SemesterCalendar(TestUtil.semester)

    @Test fun `week number is floor of days over 7 plus 1`() {
        assertEquals(1, cal.weekOf(LocalDate.of(2026, 8, 31)))
        assertEquals(1, cal.weekOf(LocalDate.of(2026, 9, 6)))
        assertEquals(2, cal.weekOf(LocalDate.of(2026, 9, 7)))
        assertEquals(0, cal.weekOf(LocalDate.of(2026, 8, 30)))
        assertEquals(8, cal.weekOf(LocalDate.of(2026, 10, 23)))
        assertEquals(LocalDate.of(2026, 9, 2), cal.dateOf(1, 3))
        assertEquals(LocalDate.of(2026, 10, 23), cal.dateOf(8, 5))
    }

    @Test fun `current week is clamped`() {
        assertEquals(1, cal.currentWeek(LocalDate.of(2026, 7, 1)))
        assertEquals(20, cal.currentWeek(LocalDate.of(2027, 6, 1)))
    }

    @Test fun `period lookup`() {
        assertEquals(LocalTime.of(8, 30), cal.startTime(1))
        assertEquals(LocalTime.of(20, 55), cal.endTime(12))
        assertEquals(3, cal.periodAtOrBefore(LocalTime.of(10, 25)))
        assertEquals(4, cal.periodEndingAtOrAfter(LocalTime.of(12, 0)))
    }

    @Test fun `week set format and parse round trip`() {
        assertEquals("1-16", WeekSets.format(WeekSets.all(16)))
        assertEquals("1,3,5-9", WeekSets.format(setOf(1, 3, 5, 6, 7, 8, 9)))
        assertEquals("1,2", WeekSets.format(setOf(1, 2)))
        assertEquals(setOf(1, 3, 5, 6, 7), WeekSets.parse("1,3,5-7"))
        assertEquals(setOf(1, 3, 5, 7), WeekSets.parse("1-8单"))
        assertEquals(setOf(2, 4, 6, 8), WeekSets.parse("1-8双"))
    }

    @Test fun `daily recurrence with count and exdate`() {
        val r = Recurrence(Freq.DAILY, interval = 2, count = 4, exDates = setOf(LocalDate.of(2026, 9, 3)))
        val d = RecurrenceEngine.dates(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 1))
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7)), d)
    }

    @Test fun `weekly recurrence by days until`() {
        val r = Recurrence(Freq.WEEKLY, byDays = setOf(1, 3), until = LocalDate.of(2026, 9, 16))
        val d = RecurrenceEngine.dates(r, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31))
        assertEquals(
            listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)),
            d,
        )
    }

    @Test fun `monthly on the 31st skips short months`() {
        val r = Recurrence(Freq.MONTHLY, count = 3)
        val d = RecurrenceEngine.dates(r, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)), d)
    }

    @Test fun `next occurrence is strictly after`() {
        val r = Recurrence(Freq.WEEKLY)
        assertEquals(LocalDate.of(2026, 9, 9), RecurrenceEngine.next(r, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2)))
        assertNull(RecurrenceEngine.next(Recurrence(Freq.DAILY, until = LocalDate.of(2026, 9, 2)), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)))
    }

    private val course = Course(
        id = "c1", semesterId = "sem1", name = "数据结构", room = "教2-321", dayOfWeek = 2,
        startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2, 3),
    )

    @Test fun `course expands by week`() {
        val occ = CourseExpander.occurrencesInWeek(TestUtil.semester, listOf(course), emptyList(), 2)
        assertEquals(1, occ.size)
        assertEquals(LocalDateTime.of(2026, 9, 8, 8, 30), occ[0].start)
        assertEquals(LocalDateTime.of(2026, 9, 8, 10, 5), occ[0].end)
        assertTrue(CourseExpander.occurrencesInWeek(TestUtil.semester, listOf(course), emptyList(), 4).isEmpty())
    }

    @Test fun `cancel and reschedule exceptions`() {
        val ex = listOf(
            CourseException("e1", "c1", week = 1, type = ExceptionType.CANCEL),
            CourseException("e2", "c1", week = 2, type = ExceptionType.RESCHEDULE, newDayOfWeek = 4, newStartPeriod = 5, newEndPeriod = 6, newRoom = "教1-101"),
        )
        assertTrue(CourseExpander.occurrencesInWeek(TestUtil.semester, listOf(course), ex, 1).isEmpty())
        val w2 = CourseExpander.occurrencesInWeek(TestUtil.semester, listOf(course), ex, 2)
        assertEquals(1, w2.size)
        assertEquals(LocalDateTime.of(2026, 9, 10, 13, 50), w2[0].start)
        assertEquals("教1-101", w2[0].room)
        assertTrue(w2[0].rescheduled)
    }

    @Test fun `event expansion across range`() {
        val e = Event(
            "ev1", "社团例会", LocalDateTime.of(2026, 9, 2, 20, 0), LocalDateTime.of(2026, 9, 2, 21, 0),
            recurrence = Recurrence(Freq.WEEKLY, count = 3),
        )
        val occ = EventExpander.occurrences(e, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(3, occ.size)
        assertEquals(LocalDate.of(2026, 9, 16), occ.last().date)
    }

    @Test fun `multi day event is visible on later day`() {
        val e = Event("ev2", "出差", LocalDateTime.of(2026, 9, 2, 9, 0), LocalDateTime.of(2026, 9, 4, 18, 0))
        assertEquals(1, EventExpander.occurrences(e, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3)).size)
        assertEquals(0, EventExpander.occurrences(e, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5)).size)
    }
}
