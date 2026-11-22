package app.goke.core

import app.goke.core.agenda.CellKind
import app.goke.core.agenda.TimetableDiff
import app.goke.core.model.Course
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TimetableDiffTest {
    private val sem = Semester("s", "学期", LocalDate.of(2026, 8, 31))
    private val a = Course("a", "s", "高数", room = "A1", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1))
    private val b = Course("b", "s", "英语", room = "B1", dayOfWeek = 2, startPeriod = 3, endPeriod = 4, weeks = setOf(1))
    private val before = Snapshot(listOf(sem), listOf(a, b))

    @Test fun noPreviewAllNormal() {
        assertEquals(listOf(CellKind.NORMAL, CellKind.NORMAL), TimetableDiff.week(before, null, 1).map { it.kind })
    }

    @Test fun addModifyRemove() {
        val after = Snapshot(listOf(sem), listOf(a.copy(room = "A2"), Course("n", "s", "体育", dayOfWeek = 5, startPeriod = 1, endPeriod = 2, weeks = setOf(1))))
        val cells = TimetableDiff.week(before, after, 1)
        val kinds = cells.groupBy { it.kind }.mapValues { it.value.map { c -> c.occ.course.name } }
        assertEquals(listOf("高数", "英语"), kinds[CellKind.REMOVED])
        assertEquals(listOf("高数"), kinds[CellKind.MODIFIED]) // 新位置
        assertEquals(listOf("体育"), kinds[CellKind.ADDED])
    }
}
