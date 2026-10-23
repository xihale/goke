package app.mintcal.core

import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.ChangeOp
import app.mintcal.core.change.ChangeViews
import app.mintcal.core.change.Conflicts
import app.mintcal.core.change.SnapshotDiff
import app.mintcal.core.change.apply
import app.mintcal.core.change.canRedo
import app.mintcal.core.change.canUndo
import app.mintcal.core.change.undo
import app.mintcal.core.export.ExportBundle
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.model.Course
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.parcel.ParcelParser
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.SequentialIds
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeAndTodoTest {
    private val sem = TestUtil.semester
    private fun course(id: String, name: String, dow: Int = 1, sp: Int = 1, ep: Int = 2, weeks: Set<Int> = setOf(1, 2)) =
        Course(id, sem.id, name, dayOfWeek = dow, startPeriod = sp, endPeriod = ep, weeks = weeks)

    private val base = Snapshot(semesters = listOf(sem), courses = listOf(course("a", "高数"), course("b", "英语", dow = 2)))

    @Test fun `apply and undo change set`() {
        val modified = course("a", "高等数学")
        val added = course("c", "物理", dow = 3)
        val cs = ChangeSet(
            "cs1", "test", Source.CODE, 100L,
            listOf(Change.Modify(base.courses[0], modified), Change.Add(added), Change.Remove(base.courses[1])),
        )
        assertEquals(1, cs.addCount); assertEquals(1, cs.modifyCount); assertEquals(1, cs.removeCount)
        val after = base.apply(cs)
        val live = after.live()
        assertEquals(listOf("高等数学", "物理"), live.courses.map { it.name })
        assertNotNull(after.courses.single { it.id == "b" }.deletedAt)
        val undone = after.undo(cs, 200L).live()
        assertEquals(listOf("高数", "英语"), undone.courses.sortedBy { it.id }.map { it.name })
        // 幂等
        assertEquals(after.live().courses.map { it.name }, after.apply(cs).live().courses.map { it.name })
    }

    @Test fun `undo multiple steps out of order and redo`() {
        val c1 = ChangeSet("c1", "rename a", Source.MANUAL, 1L, listOf(Change.Modify(base.courses[0], course("a", "高等数学"))))
        val s1 = base.apply(c1, 10L)
        val c2 = ChangeSet("c2", "add c", Source.MANUAL, 2L, listOf(Change.Add(course("c", "物理", dow = 3))))
        val s2 = s1.apply(c2, 20L)
        val c3 = ChangeSet("c3", "rename a again", Source.MANUAL, 3L, listOf(Change.Modify(course("a", "高等数学"), course("a", "微积分"))))
        val s3 = s2.apply(c3, 30L)
        // c1 被 c3 覆盖，不能单独撤销；c2 与其他无关，可以乱序撤销
        assertFalse(s3.canUndo(c1))
        assertTrue(s3.canUndo(c2)); assertTrue(s3.canUndo(c3))
        val noC = s3.undo(c2, 40L)
        assertEquals(listOf("a", "b"), noC.live().courses.map { it.id }.sorted())
        assertTrue(noC.canRedo(c2)); assertFalse(noC.canUndo(c2))
        // 连续撤销 c3 再撤销 c1
        val back3 = noC.undo(c3, 50L)
        assertTrue(back3.canUndo(c1))
        val back1 = back3.undo(c1, 60L)
        assertEquals(listOf("高数", "英语"), back1.live().courses.sortedBy { it.id }.map { it.name })
        // 重做 c1 → c3 → c2
        assertTrue(back1.canRedo(c1)); assertFalse(back1.canRedo(c3))
        val r1 = back1.apply(c1, 70L)
        assertTrue(r1.canRedo(c3))
        val r2 = r1.apply(c3, 80L).apply(c2, 90L)
        assertEquals(listOf("微积分", "英语", "物理"), r2.live().courses.sortedBy { it.id }.map { it.name })
        assertTrue(r2.canUndo(c2)); assertTrue(r2.canUndo(c3))
    }

    @Test fun `undo remove then restore`() {
        val cs = ChangeSet("r", "del b", Source.MANUAL, 1L, listOf(Change.Remove(base.courses[1])))
        val s = base.apply(cs, 10L)
        assertTrue(s.canUndo(cs)); assertFalse(s.canRedo(cs))
        val u = s.undo(cs, 20L)
        assertFalse(u.canUndo(cs)); assertTrue(u.canRedo(cs))
    }

    @Test fun `diff of snapshots`() {
        val after = base.copy(courses = listOf(course("a", "高数", weeks = setOf(1, 2, 3)), course("z", "新课", dow = 5)))
        val diff = SnapshotDiff.diff(base, after)
        assertEquals(1, diff.count { it is Change.Modify }); assertEquals(1, diff.count { it is Change.Add }); assertEquals(1, diff.count { it is Change.Remove })
        val views = ChangeViews.of(ChangeSet("x", changes = diff))
        val mod = views.single { it.op == ChangeOp.MODIFY }
        assertTrue(mod.summary.any { it.after == "1-3" })
    }

    @Test fun `change set json round trip`() {
        val cs = ChangeSet("cs", "d", Source.AI, 5L, listOf(Change.Add(course("n", "新增")), Change.Remove(base.courses[0])), warnings = listOf("w"))
        val back = ChangeSet.fromJson(cs.toJson())
        assertEquals(cs, back)
        val bundle = ExportBundle(exportedAt = 1L, snapshot = base)
        assertEquals(base, ExportBundle.fromJson(bundle.toJson()).snapshot)
    }

    @Test fun `conflict detection`() {
        val snap = base.copy(courses = base.courses + course("c", "冲突课", dow = 1, sp = 2, ep = 3, weeks = setOf(1)))
        val conflicts = Conflicts.detect(snap, setOf("c"))
        assertEquals(1, conflicts.size)
        assertEquals(setOf("高数", "冲突课"), setOf(conflicts[0].aTitle, conflicts[0].bTitle))
        assertTrue(Conflicts.detect(base, setOf("a")).isEmpty())
    }

    @Test fun `import planner updates instead of stacking`() {
        val existing = course("old", "高数").copy(source = Source.IMPORT, room = "A")
        val snap = base.copy(courses = listOf(existing, course("keep", "手动课", dow = 4)))
        val imported = listOf(course("n1", "高数").copy(source = Source.IMPORT, room = "B"), course("n2", "新").copy(source = Source.IMPORT, dayOfWeek = 2))
        val cs = ImportPlanner.planCourses(snap, sem, imported, 1L, "导入", ids = SequentialIds())
        assertEquals(1, cs.modifyCount); assertEquals(1, cs.addCount); assertEquals(0, cs.removeCount)
        val m = cs.changes.filterIsInstance<Change.Modify>().single()
        assertEquals("old", m.after.id)
        assertEquals("B", (m.after as Course).room)
        // 再次导入相同内容：没有变化
        val again = ImportPlanner.planCourses(snap.apply(cs), sem, imported.map { it.copy(id = "x" + it.id) }, 2L, "导入", ids = SequentialIds())
        assertTrue(again.isEmpty, again.changes.toString())
    }

    private val now = LocalDateTime.of(2026, 10, 23, 9, 0)

    @Test fun `quick add parses chinese date time priority list`() {
        val r = QuickAdd.parse("明天下午3点半 交概率论作业 #概率论 !高 @作业", now)
        assertEquals("交概率论作业", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 24, 15, 30), r.due)
        assertTrue(r.dueHasTime)
        assertEquals(3, r.priority); assertEquals("概率论", r.listName); assertEquals(listOf("作业"), r.tags)
        val r2 = QuickAdd.parse("下周三 写实验报告", now)
        assertEquals(LocalDate.of(2026, 10, 28), r2.due!!.toLocalDate())
        assertEquals(false, r2.dueHasTime)
        val r3 = QuickAdd.parse("11月5日 交材料", now)
        assertEquals(LocalDate.of(2026, 11, 5), r3.due!!.toLocalDate())
        val r4 = QuickAdd.parse("买牛奶", now)
        assertNull(r4.due); assertEquals("买牛奶", r4.title)
        assertEquals(LocalDateTime.of(2026, 10, 23, 20, 0), QuickAdd.parse("晚上八点开会", now).due)
    }

    @Test fun `smart lists and recurring completion`() {
        val tasks = listOf(
            Task("t1", "今天的", due = LocalDateTime.of(2026, 10, 23, 18, 0), dueHasTime = true),
            Task("t2", "逾期", due = LocalDateTime.of(2026, 10, 20, 0, 0)),
            Task("t3", "下周", due = LocalDateTime.of(2026, 10, 27, 0, 0)),
            Task("t4", "做完", done = true),
            Task("t5", "子", parentId = "t1"),
        )
        assertEquals(listOf("t2", "t1"), TaskLogic.filter(tasks, SmartList.TODAY, now).map { it.id })
        assertEquals(listOf("t3"), TaskLogic.filter(tasks, SmartList.PLANNED, now).map { it.id })
        assertEquals(listOf("t4"), TaskLogic.filter(tasks, SmartList.DONE, now).map { it.id })
        assertEquals(listOf("t2"), TaskLogic.filter(tasks, SmartList.OVERDUE, now).map { it.id })
        val rec = Task("r", "每周日报", due = LocalDateTime.of(2026, 10, 23, 18, 0), dueHasTime = true, repeat = Recurrence(Freq.WEEKLY))
        val (done, next) = TaskLogic.complete(rec, 1L, now, SequentialIds())
        assertTrue(done.done)
        assertEquals(LocalDateTime.of(2026, 10, 30, 18, 0), next!!.due)
        assertEquals(false, next.done)
        assertNull(TaskLogic.complete(Task("n", "x"), 1L, now).second)
    }

    @Test fun `due before next class`() {
        val c = course("c1", "数据结构", dow = 2, weeks = (1..20).toSet())
        val t = Task("t", "作业", courseId = "c1", dueBeforeNextClass = true)
        val due = TaskLogic.effectiveDue(t, sem, listOf(c), emptyList(), now)
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 30), due)
    }

    @Test fun `parcel parser`() {
        val p = ParcelParser.parse("【菜鸟驿站】您的包裹已到大学城北门菜鸟驿站，凭取件码 5-3-2081 取件，请于10月25日前取走。", now)!!
        assertEquals("5-3-2081", p.code)
        assertEquals("菜鸟驿站", p.company)
        assertEquals(LocalDateTime.of(2026, 10, 25, 23, 59), p.deadline)
        assertTrue(p.place.contains("驿站"))
        assertNull(ParcelParser.parse("今天天气不错", now))
        val q = ParcelParser.parse("您的快递已放入丰巢智能柜，取件码：A7K9Q2，请今日18:00前取件", now)!!
        assertEquals("A7K9Q2", q.code); assertEquals(LocalDateTime.of(2026, 10, 23, 18, 0), q.deadline)
    }
}
