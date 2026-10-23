package app.mintcal.core

import app.mintcal.core.change.Change
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.change.undo
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.util.SequentialIds
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditsTest {
    private val sem = Semester("s", "学期", LocalDate.of(2026, 8, 31))
    private val c = Course("c1", "s", "高数", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2, 3))
    private val ex = CourseException("e1", "c1", 2, ExceptionType.CANCEL)
    private val list = TaskList("l1", "清单")
    private val parent = Task("t1", "父", listId = "l1")
    private val sub = Task("t2", "子", parentId = "t1")
    private val snap = Snapshot(listOf(sem), listOf(c), listOf(ex), emptyList(), listOf(parent, sub), listOf(list))
    private val ids = SequentialIds("x")

    @Test fun saveChoosesAddOrModify() {
        assertIs<Change.Add>(Edits.save(snap, c.copy(id = "new"), "新增", 1, ids).changes.single())
        assertIs<Change.Modify>(Edits.save(snap, c.copy(name = "改"), "修改", 1, ids).changes.single())
    }

    @Test fun deleteCourseCascadesExceptionsAndUndoRestores() {
        val cs = Edits.delete(snap, c, "删除", 5, ids)
        assertEquals(2, cs.removeCount)
        val after = snap.apply(cs, 5).live()
        assertTrue(after.courses.isEmpty() && after.exceptions.isEmpty())
        val back = after.let { snap.apply(cs, 5).undo(cs, 6).live() }
        assertEquals(1, back.courses.size)
        assertEquals(1, back.exceptions.size)
    }

    @Test fun deleteTaskRemovesSubtasks() {
        val cs = Edits.delete(snap, parent, "删除", 5, ids)
        assertTrue(
            snap
                .apply(cs, 5)
                .live()
                .tasks
                .isEmpty(),
        )
    }

    @Test fun deleteListKeepsTasks() {
        val cs = Edits.delete(snap, list, "删除", 5, ids)
        val after = snap.apply(cs, 5).live()
        assertTrue(after.lists.isEmpty())
        assertNull(after.tasks.first { it.id == "t1" }.listId)
    }

    @Test fun repeatingTaskGeneratesNext() {
        val rep = Task("r", "每周", due = LocalDateTime.of(2026, 9, 7, 9, 0), dueHasTime = true, repeat = Recurrence(Freq.WEEKLY))
        val s = snap.put(rep)
        val cs = Edits.toggleTask(s, rep, true, 100, LocalDateTime.of(2026, 9, 7, 10, 0), "完成", ids)
        assertEquals(2, cs.changes.size)
        val a = s.apply(cs, 100).live().tasks
        assertTrue(a.first { it.id == "r" }.done)
        assertTrue(a.any { it.id != "r" && it.title == "每周" && !it.done && it.due!!.toLocalDate() == LocalDate.of(2026, 9, 14) })
    }

    @Test fun copyToNextSemester() {
        val cs = Edits.copyToNextSemester(snap, sem, "下学期", LocalDate.of(2027, 2, 22), 9, "复制", ids)
        val a = snap.apply(cs, 9).live()
        assertEquals(2, a.semesters.size)
        assertEquals("下学期", a.currentSemester!!.name)
        assertEquals(2, a.courses.size)
        assertTrue(a.courses.any { it.semesterId == a.currentSemester!!.id })
    }
}
