package app.mintcal.core

import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgendaTest {
    private val sem = Semester("s", "学期", LocalDate.of(2026, 8, 31))

    // 周一 1-2 节 (8:30)，第 1-3 周
    private val c = Course("c1", "s", "高数", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2, 3))
    private val mon = LocalDate.of(2026, 8, 31)

    @Test fun mixedDayIsOrdered() {
        val snap =
            Snapshot(
                semesters = listOf(sem),
                courses = listOf(c),
                events =
                    listOf(
                        Event("e1", "社团例会", LocalDateTime.of(2026, 8, 31, 20, 0), LocalDateTime.of(2026, 8, 31, 21, 0)),
                        Event("e2", "校庆", mon.atStartOfDay(), mon.atStartOfDay(), allDay = true),
                    ),
                tasks =
                    listOf(
                        Task("t1", "交作业", due = LocalDateTime.of(2026, 8, 31, 23, 59)),
                        Task("t2", "写报告", blockStart = LocalDateTime.of(2026, 8, 31, 16, 30), blockMinutes = 60),
                        Task("t3", "已完成", due = LocalDateTime.of(2026, 8, 31, 0, 0), done = true),
                    ),
            )
        val items = Agenda.forDay(snap, mon)
        assertIs<EventItem>(items[0]) // 全天排最前
        assertTrue((items[0] as EventItem).allDay)
        assertIs<CourseItem>(items[1])
        assertIs<BlockItem>(items[2])
        assertIs<EventItem>(items[3])
        assertTrue(items.drop(4).all { it is TaskItem })
        assertEquals(6, items.size)
    }

    @Test fun multiDayEventSpansDays() {
        val e = Event("e", "实训", mon.atStartOfDay(), mon.plusDays(2).atStartOfDay(), allDay = true)
        val m = Agenda.range(Snapshot(semesters = listOf(sem), events = listOf(e)), mon, mon.plusDays(6))
        assertEquals(setOf(mon, mon.plusDays(1), mon.plusDays(2)), m.keys)
    }

    @Test fun marksCountOpenTasksOnly() {
        val snap =
            Snapshot(
                semesters = listOf(sem),
                courses = listOf(c),
                tasks = listOf(Task("a", "a", due = mon.atTime(9, 0), dueHasTime = true), Task("b", "b", due = mon.atTime(9, 0), done = true)),
            )
        val mk = Agenda.marks(snap, mon, mon)[mon]!!
        assertEquals(1, mk.courses)
        assertEquals(1, mk.tasks)
    }
}
