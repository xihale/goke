package app.goke.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CourseProgressTest {
    private val start = LocalDateTime.of(2026, 9, 7, 8, 30)
    private val end = LocalDateTime.of(2026, 9, 7, 10, 5) // 95 minutes

    @Test
    fun `upcoming before start`() {
        val now = LocalDateTime.of(2026, 9, 7, 8, 0)
        assertEquals(TimePhase.UPCOMING, CourseProgress.phase(start, end, now))
        assertEquals(0f, CourseProgress.fraction(start, end, now))
        assertEquals(0, CourseProgress.percent(start, end, now))
    }

    @Test
    fun `ongoing at exact start`() {
        assertEquals(TimePhase.ONGOING, CourseProgress.phase(start, end, start))
        assertEquals(0f, CourseProgress.fraction(start, end, start))
        assertEquals(0, CourseProgress.percent(start, end, start))
    }

    @Test
    fun `ongoing midway computes linear fraction`() {
        val mid = LocalDateTime.of(2026, 9, 7, 9, 17, 30) // 47.5 minutes = 50%
        assertEquals(TimePhase.ONGOING, CourseProgress.phase(start, end, mid))
        assertEquals(0.5f, CourseProgress.fraction(start, end, mid), 1e-4f)
        assertEquals(50, CourseProgress.percent(start, end, mid))
    }

    @Test
    fun `finished at exact end and after`() {
        assertEquals(TimePhase.FINISHED, CourseProgress.phase(start, end, end))
        assertEquals(1f, CourseProgress.fraction(start, end, end))
        assertEquals(100, CourseProgress.percent(start, end, end))

        val later = LocalDateTime.of(2026, 9, 7, 12, 0)
        assertEquals(TimePhase.FINISHED, CourseProgress.phase(start, end, later))
        assertEquals(1f, CourseProgress.fraction(start, end, later))
        assertEquals(100, CourseProgress.percent(start, end, later))
    }

    @Test
    fun `zero or inverted duration handled safely`() {
        assertEquals(TimePhase.UPCOMING, CourseProgress.phase(start, start, start.minusMinutes(1)))
        assertEquals(0f, CourseProgress.fraction(start, start, start.minusMinutes(1)))
        assertEquals(TimePhase.FINISHED, CourseProgress.phase(start, start, start))
        assertEquals(1f, CourseProgress.fraction(start, start, start))
    }
}
