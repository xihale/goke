package app.mintcal.ui.screens

import app.mintcal.core.change.Change
import app.mintcal.core.model.Course
import app.mintcal.core.model.GdutCampus
import app.mintcal.core.model.GdutPeriods
import app.mintcal.core.model.Semester
import app.mintcal.core.model.effectivePeriods
import app.mintcal.core.time.SemesterCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 跨校区课程作息覆盖：新增/修改 campusOverride 与 SemesterCalendar/effectivePeriods 时段解析。 */
class CourseCampusOverrideTest {
    private val semester =
        Semester(
            id = "sem1",
            name = "2026–2027 学年第一学期",
            week1Monday = LocalDate.of(2026, 9, 7),
            totalWeeks = 20,
            periods = GdutPeriods.hemc,
            isCurrent = true,
        )

    @Test
    fun `buildSlotChanges persists campusOverride on add and modify`() {
        val newSlot =
            SlotDraft(
                id = "c1",
                base = null,
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(1, 2),
                room = "龙洞教学楼",
                remindMinutes = null,
            )

        val addChanges =
            buildSlotChanges(
                semesterId = semester.id,
                name = "高等数学",
                teacher = "李四",
                colorIndex = 0,
                note = "",
                campusOverride = GdutCampus.LONGDONG,
                initialSlots = emptyList(),
                slots = listOf(newSlot),
                liveExceptions = emptyList(),
            )
        assertEquals(1, addChanges.size)
        val added = (addChanges.single() as Change.Add).after as Course
        assertEquals(GdutCampus.LONGDONG, added.campusOverride)

        val modifyChanges =
            buildSlotChanges(
                semesterId = semester.id,
                name = added.name,
                teacher = added.teacher,
                colorIndex = added.colorIndex,
                note = added.note,
                campusOverride = null,
                initialSlots = listOf(newSlot.copy(base = added)),
                slots = listOf(newSlot.copy(base = added)),
                liveExceptions = emptyList(),
            )
        assertEquals(1, modifyChanges.size)
        val updated = (modifyChanges.single() as Change.Modify).after as Course
        assertNull(updated.campusOverride)
    }

    @Test
    fun `course effectivePeriods and SemesterCalendar resolve override bell times`() {
        val longdongCourse =
            Course(
                id = "c2",
                semesterId = semester.id,
                name = "跨校区选修",
                teacher = "",
                room = "",
                dayOfWeek = 2,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(1),
                colorIndex = 1,
                campusOverride = GdutCampus.LONGDONG,
            )
        val cal = SemesterCalendar(semester)

        assertEquals(GdutPeriods.longdong.first().start, cal.startTime(longdongCourse, 1))
        assertEquals(GdutPeriods.longdong[1].end, cal.endTime(longdongCourse, 2))
        assertEquals(GdutPeriods.longdong, longdongCourse.effectivePeriods(semester))
        assertEquals(GdutPeriods.hemc.first().start, cal.startTime(1))
    }
}
