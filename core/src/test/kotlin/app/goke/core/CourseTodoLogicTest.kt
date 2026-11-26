package app.goke.core

import app.goke.core.model.Course
import app.goke.core.model.Semester
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.todo.TaskLogic
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
