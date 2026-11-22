package app.mintcal.ui.screens

import app.mintcal.core.change.Change
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.ExceptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课程结构化时段编辑：分时段独立教室、增删改时段与悬空调课异常清理。 */
class CourseGridOpsTest {
    private fun baseCourse(
        id: String,
        dow: Int = 1,
        sp: Int = 1,
        ep: Int = 2,
        weeks: Set<Int> = (1..16).toSet(),
        room: String = "教3-204",
    ) = Course(
        id = id,
        semesterId = "sem1",
        name = "计算机组成原理",
        teacher = "张三",
        room = room,
        dayOfWeek = dow,
        startPeriod = sp,
        endPeriod = ep,
        weeks = weeks,
        colorIndex = 2,
    )

    private fun slotOf(c: Course) =
        SlotDraft(
            id = c.id,
            base = c,
            dayOfWeek = c.dayOfWeek,
            startPeriod = c.startPeriod,
            endPeriod = c.endPeriod,
            weeks = c.weeks,
            room = c.room,
            remindMinutes = c.remindMinutes,
        )

    @Test
    fun newMultiSlotCoursePreservesPerSlotRoom() {
        val s1 = SlotDraft("c1", null, dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = (1..14).toSet(), room = "教3-204", remindMinutes = null)
        val s2 = SlotDraft("c2", null, dayOfWeek = 4, startPeriod = 5, endPeriod = 7, weeks = setOf(6, 8, 10, 12), room = "实4-401", remindMinutes = 15)
        val changes =
            buildSlotChanges(
                semesterId = "sem1",
                name = " 计算机组成原理 ",
                teacher = " 张三 ",
                colorIndex = 3,
                note = "",
                initialSlots = emptyList(),
                slots = listOf(s1, s2),
                liveExceptions = emptyList(),
            )
        assertEquals(2, changes.size)
        val added = changes.map { (it as Change.Add).after as Course }
        assertEquals("教3-204", added[0].room)
        assertEquals("实4-401", added[1].room)
        assertEquals(15, added[1].remindMinutes)
    }

    @Test
    fun shrinkingWeeksPrunesDanglingExceptionsOnlyForDroppedWeeks() {
        val c1 = baseCourse("c1", weeks = (1..16).toSet())
        val exKeep = CourseException(id = "ex4", courseId = "c1", week = 4, type = ExceptionType.CANCEL)
        val exDrop = CourseException(id = "ex15", courseId = "c1", week = 15, type = ExceptionType.CANCEL)
        val initial = listOf(slotOf(c1))
        val edited = listOf(slotOf(c1).copy(weeks = (1..12).toSet()))
        val changes =
            buildSlotChanges(
                semesterId = "sem1",
                name = c1.name,
                teacher = c1.teacher,
                colorIndex = c1.colorIndex,
                note = c1.note,
                initialSlots = initial,
                slots = edited,
                liveExceptions = listOf(exKeep, exDrop),
            )
        assertTrue(changes.any { it is Change.Modify && (it.after as Course).weeks == (1..12).toSet() })
        assertEquals(listOf("ex15"), changes.filterIsInstance<Change.Remove>().map { it.before.id })
    }

    @Test
    fun deletingOneSlotRemovesCourseAndItsExceptionsWhileKeepingSibling() {
        val c1 = baseCourse("c1", dow = 1, room = "教3-204")
        val c2 = baseCourse("c2", dow = 4, sp = 5, ep = 6, room = "实4-401")
        val ex2 = CourseException(id = "ex-c2", courseId = "c2", week = 3, type = ExceptionType.CANCEL)
        val initial = listOf(slotOf(c1), slotOf(c2))
        val changes =
            buildSlotChanges(
                semesterId = "sem1",
                name = c1.name,
                teacher = c1.teacher,
                colorIndex = c1.colorIndex,
                note = c1.note,
                initialSlots = initial,
                slots = listOf(slotOf(c1)),
                liveExceptions = listOf(ex2),
            )
        val removedIds = changes.filterIsInstance<Change.Remove>().map { it.before.id }.toSet()
        assertEquals(setOf("c2", "ex-c2"), removedIds)
    }

    @Test
    fun unchangedSlotsProduceZeroChanges() {
        val c1 = baseCourse("c1")
        val initial = listOf(slotOf(c1))
        val changes =
            buildSlotChanges(
                semesterId = "sem1",
                name = c1.name,
                teacher = c1.teacher,
                colorIndex = c1.colorIndex,
                note = c1.note,
                initialSlots = initial,
                slots = initial,
                liveExceptions = emptyList(),
            )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun multiTeacherSlotsPreservePerSlotTeacherWhenSummaryUnchanged() {
        val c1 = baseCourse("c1", dow = 1, weeks = setOf(1, 2), room = "实2-301").copy(teacher = "陈老师")
        val c2 = baseCourse("c2", dow = 1, weeks = setOf(3, 4), room = "实2-405").copy(teacher = "林老师")
        val initial = listOf(slotOf(c1).copy(teacher = c1.teacher), slotOf(c2).copy(teacher = c2.teacher))
        val edited = listOf(initial[0], initial[1].copy(room = "实2-406"))
        val changes =
            buildSlotChanges(
                semesterId = "sem1",
                name = c1.name,
                teacher = "陈老师、林老师",
                colorIndex = 5,
                note = "实验",
                initialSlots = initial,
                slots = edited,
                liveExceptions = emptyList(),
            )
        val mods =
            changes
                .filterIsInstance<Change.Modify>()
                .map { it.after as Course }
                .associateBy { it.id }
        assertEquals("陈老师", mods.getValue("c1").teacher)
        assertEquals("林老师", mods.getValue("c2").teacher)
        assertEquals("实2-406", mods.getValue("c2").room)
    }
}
