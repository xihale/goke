package app.goke.core

import app.goke.core.agenda.NextUps
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.ExceptionType
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.todo.TaskLogic
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseTodoLogicTest {
    private val sem = Semester("sem1", "2026秋", LocalDate.of(2026, 8, 31), totalWeeks = 20)
    private val otherSem = Semester("sem2", "2027春", LocalDate.of(2027, 3, 1), totalWeeks = 20)

    @Test
    fun `nextOccurrence matches all weekly slots of same course`() {
        val mon = Course("c-mon", sem.id, "高等数学", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val wed = Course("c-wed", sem.id, "高等数学", dayOfWeek = 3, startPeriod = 3, endPeriod = 4, weeks = (1..16).toSet())

        // 2026-09-01 是第 1 周周二；关联到周一槽位 c-mon 的待办也应解析到周三 c-wed
        val next = CourseExpander.nextOccurrence(sem, listOf(mon, wed), emptyList(), mon.id, LocalDateTime.of(2026, 9, 1, 10, 0))
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 2), next.date)
        assertEquals(wed.id, next.course.id)
    }

    @Test
    fun `forCourse groups same semester slots and isolates other semesters`() {
        val mon = Course("c1", sem.id, "数字逻辑", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2))
        val thu = Course("c2", sem.id, " 数字逻辑 ", dayOfWeek = 4, startPeriod = 3, endPeriod = 4, weeks = setOf(1, 2))
        val nextSemCourse = Course("c3", otherSem.id, "数字逻辑", dayOfWeek = 2, startPeriod = 1, endPeriod = 2, weeks = setOf(1))

        val tMon = Task("t1", "交预习报告", courseId = mon.id)
        val tThu = Task("t2", "带实验板", courseId = thu.id)
        val tDone = Task("t3", "已完成", courseId = mon.id, done = true)
        val tSub = Task("t4", "子任务", parentId = tMon.id, courseId = mon.id)
        val tDeleted = Task("t5", "已删除", courseId = mon.id, deletedAt = 123L)
        val tOtherSem = Task("t6", "下学期", courseId = nextSemCourse.id)

        val courses = listOf(mon, thu, nextSemCourse)
        val tasks = listOf(tMon, tThu, tDone, tSub, tDeleted, tOtherSem)

        val active = TaskLogic.forCourse(tasks, courses, mon)
        assertEquals(listOf("t1", "t2"), active.map { it.id })

        val all = TaskLogic.forCourse(tasks, courses, thu, includeDone = true)
        assertEquals(listOf("t1", "t2", "t3"), all.map { it.id })

        val other = TaskLogic.forCourse(tasks, courses, nextSemCourse)
        assertEquals(listOf("t6"), other.map { it.id })
    }

    @Test
    fun `occurrence expands normal, rescheduled, cancelled and out-of-weeks`() {
        val course = Course("c-prob", sem.id, "概率论与数理统计", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2, 3, 5))
        val exMove =
            CourseException(
                id = "ex-move",
                courseId = course.id,
                week = 3,
                type = ExceptionType.RESCHEDULE,
                newWeek = 4,
                newDayOfWeek = 5,
                newStartPeriod = 3,
                newEndPeriod = 4,
                newRoom = "教3-301",
            )
        val exCancel = CourseException(id = "ex-cancel", courseId = course.id, week = 5, type = ExceptionType.CANCEL)
        val exceptions = listOf(exMove, exCancel)

        val occ2 = CourseExpander.occurrence(sem, listOf(course), exceptions, course.id, 2)
        assertNotNull(occ2)
        assertEquals(2, occ2.week)
        assertEquals(2, occ2.originalWeek)
        assertEquals(LocalDate.of(2026, 9, 7), occ2.date)

        val occ3 = CourseExpander.occurrence(sem, listOf(course), exceptions, course.id, 3)
        assertNotNull(occ3)
        assertEquals(4, occ3.week)
        assertEquals(3, occ3.originalWeek)
        assertEquals(LocalDate.of(2026, 9, 25), occ3.date)
        assertEquals("教3-301", occ3.room)
        assertTrue(occ3.rescheduled)

        assertNull(CourseExpander.occurrence(sem, listOf(course), exceptions, course.id, 5))
        assertNull(CourseExpander.occurrence(sem, listOf(course), exceptions, course.id, 99))
    }

    @Test
    fun `pinned task only matches its pinned slot and week, and follows reschedule`() {
        val mon = Course("c-mon", sem.id, "概率论与数理统计", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val fri = Course("c-fri", sem.id, "概率论与数理统计", dayOfWeek = 5, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val courses = listOf(mon, fri)
        val pinned = Task("t-pin", "交第一批作业").withCourseLink(mon.id, courseWeek = 14, dueBeforeNextClass = true)
        assertEquals(14, pinned.courseWeek)

        val nowWeek14 = LocalDateTime.of(2026, 11, 30, 8, 0)
        val counts = TaskLogic.occurrenceCounts(listOf(pinned), sem, courses, emptyList(), nowWeek14)
        assertEquals(mapOf((mon.id to 14) to 1), counts)

        val occMon14 = CourseExpander.occurrence(sem, courses, emptyList(), mon.id, 14)!!
        val occFri14 = CourseExpander.occurrence(sem, courses, emptyList(), fri.id, 14)!!
        val occMon15 = CourseExpander.occurrence(sem, courses, emptyList(), mon.id, 15)!!
        assertTrue(TaskLogic.matchesOccurrence(pinned, occMon14, sem, courses, emptyList(), nowWeek14))
        assertFalse(TaskLogic.matchesOccurrence(pinned, occFri14, sem, courses, emptyList(), nowWeek14))
        assertFalse(TaskLogic.matchesOccurrence(pinned, occMon15, sem, courses, emptyList(), nowWeek14))

        // 调课到同周周二，且当前时间已经过了第 14 周，仍钉死在原第 14 周这节课
        val resched =
            CourseException(
                id = "ex-r",
                courseId = mon.id,
                week = 14,
                type = ExceptionType.RESCHEDULE,
                newDayOfWeek = 2,
                newStartPeriod = 3,
                newEndPeriod = 4,
            )
        val nowWeek15 = LocalDateTime.of(2026, 12, 7, 10, 0)
        val movedOcc = CourseExpander.occurrence(sem, courses, listOf(resched), mon.id, 14)!!
        assertEquals(LocalDate.of(2026, 12, 1), movedOcc.date)
        assertTrue(TaskLogic.matchesOccurrence(pinned, movedOcc, sem, courses, listOf(resched), nowWeek15))
        assertEquals(movedOcc.start, TaskLogic.effectiveDue(pinned, sem, courses, listOf(resched), nowWeek15))

        // 停课后不再产生课表待办计数
        val cancelled = CourseException(id = "ex-c", courseId = mon.id, week = 14, type = ExceptionType.CANCEL)
        assertEquals(emptyMap(), TaskLogic.occurrenceCounts(listOf(pinned), sem, courses, listOf(cancelled), nowWeek14))
    }

    @Test
    fun `unpinned dueBeforeNextClass task only attaches to single next occurrence`() {
        val mon = Course("c-mon", sem.id, "高等数学", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val fri = Course("c-fri", sem.id, "高等数学", dayOfWeek = 5, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val courses = listOf(mon, fri)
        val unpinned = Task("t-unpinned", "预习", courseId = mon.id, dueBeforeNextClass = true)

        // 2026-09-01 是第 1 周周二 -> 下一次上课是第 1 周周五 c-fri
        val now = LocalDateTime.of(2026, 9, 1, 10, 0)
        val counts = TaskLogic.occurrenceCounts(listOf(unpinned), sem, courses, emptyList(), now)
        assertEquals(mapOf((fri.id to 1) to 1), counts)
    }

    @Test
    fun `NextUps find only attaches tasks matching upcoming occurrence`() {
        val mon = Course("c-mon", sem.id, "概率论与数理统计", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val fri = Course("c-fri", sem.id, "概率论与数理统计", dayOfWeek = 5, startPeriod = 1, endPeriod = 2, weeks = (1..16).toSet())
        val tNext = Task("t-next", "本周五交").withCourseLink(fri.id, courseWeek = 1, dueBeforeNextClass = true)
        val tLater = Task("t-later", "下周一交").withCourseLink(mon.id, courseWeek = 2, dueBeforeNextClass = true)
        val snap = Snapshot(semesters = listOf(sem), courses = listOf(mon, fri), tasks = listOf(tNext, tLater))

        val now = LocalDateTime.of(2026, 9, 1, 10, 0)
        val nextUp = NextUps.find(snap, now)
        assertNotNull(nextUp)
        assertEquals(fri.id, nextUp.occurrence.course.id)
        assertEquals(1, nextUp.occurrence.originalWeek)
        assertEquals(listOf("t-next"), nextUp.tasks.map { it.id })
    }
}
