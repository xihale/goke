package app.mintcal.core

import app.mintcal.core.todo.QuickAdd
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickAddSlashDateTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 26, 9, 26)

    @Test
    fun slashDateOnly() {
        val r = QuickAdd.parse("10/28", now)
        assertEquals("2026-10-28T00:00", r.due?.toString())
        assertTrue(r.dueHasTime.not())
    }

    @Test
    fun slashDateWithTime() {
        val r = QuickAdd.parse("10/28 15:00", now)
        assertEquals("2026-10-28T15:00", r.due?.toString())
        assertEquals(true, r.dueHasTime)
    }

    @Test
    fun slashDateTokenizedOutOfTitle() {
        val r = QuickAdd.parse("10/28 15:00 买牛奶", now)
        assertEquals("买牛奶", r.title)
        assertEquals("2026-10-28T15:00", r.due?.toString())
    }
}
