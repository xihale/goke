package app.mintcal.core

import app.mintcal.core.agenda.NextUp
import app.mintcal.core.agenda.NextUps
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.model.Course
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.TaskLogic
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoV2Test {
    // 2026-10-23 周五
    private val now = LocalDateTime.of(2026, 10, 23, 9, 0)
    private val today = now.toLocalDate()

    private fun t(
        id: String,
        due: LocalDateTime? = null,
        hasTime: Boolean = false,
        done: Boolean = false,
        start: LocalDate? = null,
    ) = Task(id, id, due = due, dueHasTime = hasTime, done = done, startDate = start)

    @Test fun `timeline groups by bucket in order`() {
        val tasks =
            listOf(
                t("late", today.minusDays(2).atTime(23, 59)),
                t("today", today.atTime(23, 59)),
                t("tomorrow", today.plusDays(1).atTime(23, 59)),
                t("sun", today.plusDays(2).atTime(23, 59)), // 周日，仍是本周
                t("later", today.plusDays(10).atTime(23, 59)),
                t("inbox"),
                t("done", done = true),
                t("startToday", start = today),
                t("timedPast", today.atTime(8, 0), hasTime = true),
            )
        val sections = TaskLogic.timeline(tasks, now)
        assertEquals(
            listOf(Bucket.OVERDUE, Bucket.TODAY, Bucket.TOMORROW, Bucket.THIS_WEEK, Bucket.LATER, Bucket.INBOX, Bucket.DONE),
            sections.map { it.bucket },
        )
        assertEquals(
            setOf("late", "timedPast"),
            sections
                .first { it.bucket == Bucket.OVERDUE }
                .tasks
                .map { it.id }
                .toSet(),
        )
        assertTrue(
            sections
                .first { it.bucket == Bucket.TODAY }
                .tasks
                .map { it.id }
                .containsAll(listOf("today", "startToday")),
        )
        assertEquals(listOf("sun"), sections.first { it.bucket == Bucket.THIS_WEEK }.tasks.map { it.id })
    }

    @Test fun `drop dates per bucket`() {
        assertEquals(today, TaskLogic.dropDate(Bucket.TODAY, today))
        assertEquals(today.plusDays(1), TaskLogic.dropDate(Bucket.TOMORROW, today))
        assertEquals(LocalDate.of(2026, 10, 25), TaskLogic.dropDate(Bucket.THIS_WEEK, today))
        assertEquals(today.plusDays(7), TaskLogic.dropDate(Bucket.LATER, today))
        assertNull(TaskLogic.dropDate(Bucket.INBOX, today))
        assertEquals(TaskLogic.NO_DROP, TaskLogic.dropDate(Bucket.OVERDUE, today))
        // 周六：本周只剩周日，凑不出「明天之后」的日子 → 不可落点
        assertEquals(TaskLogic.NO_DROP, TaskLogic.dropDate(Bucket.THIS_WEEK, LocalDate.of(2026, 10, 24)))
    }

    @Test fun `reschedule keeps time and clears`() {
        val a = t("a", today.atTime(15, 30), hasTime = true)
        val b = t("b")
        val cs = Edits.reschedule(listOf(a, b), today.plusDays(1), "改期", 1L)
        val snap = Snapshot(tasks = listOf(a, b)).apply(cs).live()
        assertEquals(LocalDateTime.of(2026, 10, 24, 15, 30), snap.tasks.first { it.id == "a" }.due)
        val nb = snap.tasks.first { it.id == "b" }
        assertEquals(LocalDateTime.of(2026, 10, 24, 23, 59), nb.due)
        assertFalse(nb.dueHasTime)
        val clear = Edits.reschedule(listOf(nb), null, "清除", 2L)
        assertNull(
            Snapshot(tasks = listOf(nb))
                .apply(clear)
                .live()
                .tasks
                .single()
                .due,
        )
    }

    @Test fun `reschedule to today clears past time when requested`() {
        val now = today.atTime(14, 0)
        val pastToday = t("past", today.atTime(9, 0), hasTime = true)
        val futureToday = t("future", today.atTime(18, 0), hasTime = true)
        val cs = Edits.reschedule(listOf(pastToday, futureToday), today, "移到今天", 3L, clearTimeIfPast = now)
        val snap = Snapshot(tasks = listOf(pastToday, futureToday)).apply(cs).live()
        val p = snap.tasks.first { it.id == "past" }
        val f = snap.tasks.first { it.id == "future" }
        assertFalse(p.dueHasTime)
        assertEquals(today.atTime(23, 59), p.due)
        assertTrue(f.dueHasTime)
        assertEquals(today.atTime(18, 0), f.due)
    }

    @Test fun `drop task and reopen`() {
        val a = t("a", today.atTime(23, 59))
        val cs = Edits.dropTasks(listOf(a), "不做了", 5L)
        val dropped =
            Snapshot(tasks = listOf(a))
                .apply(cs)
                .live()
                .tasks
                .single()
        assertTrue(dropped.done)
        assertTrue(dropped.dropped)
        val reopened = TaskLogic.reopen(dropped, 6L)
        assertFalse(reopened.done)
        assertFalse(reopened.dropped)
    }

    @Test fun `make subtask rules`() {
        val p = t("p")
        val c = t("c")
        val grand = Task("g", "g", parentId = "c")
        val snap = Snapshot(tasks = listOf(p, c, grand))
        val ok = Edits.makeSubtask(snap, p, c, "x", 1L)
        assertEquals(
            "c",
            snap
                .apply(ok)
                .live()
                .tasks
                .first { it.id == "p" }
                .parentId,
        )
        // 被拖的自带子任务 → 拒绝
        assertTrue(Edits.makeSubtask(snap, c, p, "x", 1L).isEmpty)
        // 目标本身是子任务 → 拒绝
        assertTrue(Edits.makeSubtask(snap, p, grand, "x", 1L).isEmpty)
        // 自己拖自己 → 拒绝
        assertTrue(Edits.makeSubtask(snap, p, p, "x", 1L).isEmpty)
    }

    @Test fun `quick add course syntax`() {
        val r = QuickAdd.parse("交作业 /概率论 !高", now)
        assertEquals("概率论", r.courseName)
        assertEquals("交作业", r.title)
        assertEquals(3, r.priority)
        assertTrue(r.tokens.any { it.kind == QuickTokenKind.COURSE && it.text == "/概率论" })
        // 日期里的斜杠不是课程
        val d = QuickAdd.parse("10/26 交表", now)
        assertNull(d.courseName)
        assertEquals(LocalDate.of(2026, 10, 26), d.due!!.toLocalDate())
    }

    @Test fun `parse lines with subtasks and numbering`() {
        val text = "1. 周三交实验报告\n  - 数据处理\n  - 画图\n2、周五前读第三章 #概率论\n\n- 买电池"
        val items = QuickAdd.parseLines(text, now)
        assertEquals(5, items.size)
        assertEquals("交实验报告", items[0].result.title)
        assertNull(items[0].parentIndex)
        assertEquals(0, items[1].parentIndex)
        assertEquals(0, items[2].parentIndex)
        assertNull(items[3].parentIndex)
        assertEquals("概率论", items[3].result.listName)
        // 空行后的「- 买电池」缩进为 0 但以 - 开头，挂到上一个顶层条目（第 2 条）
        assertEquals(3, items[4].parentIndex)
    }

    private val sem = TestUtil.semester // 2026-08-31 为第 1 周周一

    @Test fun `next up finds course and related tasks`() {
        val course = Course("c", sem.id, "高数", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(9))
        val task = Task("t", "交作业", courseId = "c", dueBeforeNextClass = true)
        val other = Task("o", "无关")
        val snap = Snapshot(semesters = listOf(sem), courses = listOf(course), tasks = listOf(task, other))
        val up = NextUps.find(snap, LocalDateTime.of(2026, 10, 25, 20, 0))
        assertNotNull(up)
        assertEquals("高数", up.occurrence.course.name)
        assertEquals(listOf("t"), up.tasks.map { it.id })
        assertNull(NextUps.find(snap, LocalDateTime.of(2026, 12, 30, 20, 0)))
    }
}

class TodoSearchTest {
    private val t =
        app.mintcal.core.model
            .Task("1", "写概率论作业", tags = listOf("期末"), priority = 3)

    @kotlin.test.Test
    fun `parses operators and words`() {
        val q =
            app.mintcal.core.todo.TodoSearch
                .parse("#学习 @期末 !高 作业")
        kotlin.test.assertEquals(listOf("学习"), q.lists)
        kotlin.test.assertEquals(listOf("期末"), q.tags)
        kotlin.test.assertEquals(3, q.priority)
        kotlin.test.assertEquals(listOf("作业"), q.words)
    }

    @kotlin.test.Test
    fun `matches all conditions`() {
        val s = app.mintcal.core.todo.TodoSearch
        kotlin.test.assertTrue(s.matches(s.parse("#学习 @期末 !高 作业"), t, emptyList(), "学习"))
        kotlin.test.assertFalse(s.matches(s.parse("#生活"), t, emptyList(), "学习"))
        kotlin.test.assertFalse(s.matches(s.parse("!低"), t, emptyList(), null))
        kotlin.test.assertTrue(s.matches(s.parse("买菜"), t, listOf(t.copy(id = "2", title = "买菜")), null))
        kotlin.test.assertTrue(s.matches(s.parse("  "), t, emptyList(), null))
    }
}
