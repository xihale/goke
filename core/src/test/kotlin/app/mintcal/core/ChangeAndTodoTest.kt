package app.mintcal.core

import app.mintcal.core.agenda.Agenda
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeOp
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.ChangeViews
import app.mintcal.core.change.Conflicts
import app.mintcal.core.change.SnapshotDiff
import app.mintcal.core.change.Unify
import app.mintcal.core.change.apply
import app.mintcal.core.change.canRedo
import app.mintcal.core.change.canUndo
import app.mintcal.core.change.undo
import app.mintcal.core.export.ExportBundle
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.model.Attachment
import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Freq
import app.mintcal.core.model.PeriodTime
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.MintJson
import app.mintcal.core.util.SequentialIds
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeAndTodoTest {
    private val sem = TestUtil.semester

    private fun course(
        id: String,
        name: String,
        dow: Int = 1,
        sp: Int = 1,
        ep: Int = 2,
        weeks: Set<Int> = setOf(1, 2),
    ) = Course(id, sem.id, name, dayOfWeek = dow, startPeriod = sp, endPeriod = ep, weeks = weeks)

    private val base = Snapshot(semesters = listOf(sem), courses = listOf(course("a", "高数"), course("b", "英语", dow = 2)))

    @Test fun `apply and undo change set`() {
        val modified = course("a", "高等数学")
        val added = course("c", "物理", dow = 3)
        val cs =
            ChangeSet(
                "cs1",
                "test",
                Source.CODE,
                100L,
                listOf(Change.Modify(base.courses[0], modified), Change.Add(added), Change.Remove(base.courses[1])),
            )
        assertEquals(1, cs.addCount)
        assertEquals(1, cs.modifyCount)
        assertEquals(1, cs.removeCount)
        val after = base.apply(cs)
        val live = after.live()
        assertEquals(listOf("高等数学", "物理"), live.courses.map { it.name })
        assertNotNull(after.courses.single { it.id == "b" }.deletedAt)
        val undone = after.undo(cs, 200L).live()
        assertEquals(listOf("高数", "英语"), undone.courses.sortedBy { it.id }.map { it.name })
        // 幂等
        assertEquals(
            after.live().courses.map { it.name },
            after
                .apply(cs)
                .live()
                .courses
                .map { it.name },
        )
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
        assertTrue(s3.canUndo(c2))
        assertTrue(s3.canUndo(c3))
        val noC = s3.undo(c2, 40L)
        assertEquals(
            listOf("a", "b"),
            noC
                .live()
                .courses
                .map { it.id }
                .sorted(),
        )
        assertTrue(noC.canRedo(c2))
        assertFalse(noC.canUndo(c2))
        // 连续撤销 c3 再撤销 c1
        val back3 = noC.undo(c3, 50L)
        assertTrue(back3.canUndo(c1))
        val back1 = back3.undo(c1, 60L)
        assertEquals(
            listOf("高数", "英语"),
            back1
                .live()
                .courses
                .sortedBy { it.id }
                .map { it.name },
        )
        // 重做 c1 → c3 → c2
        assertTrue(back1.canRedo(c1))
        assertFalse(back1.canRedo(c3))
        val r1 = back1.apply(c1, 70L)
        assertTrue(r1.canRedo(c3))
        val r2 = r1.apply(c3, 80L).apply(c2, 90L)
        assertEquals(
            listOf("微积分", "英语", "物理"),
            r2
                .live()
                .courses
                .sortedBy { it.id }
                .map { it.name },
        )
        assertTrue(r2.canUndo(c2))
        assertTrue(r2.canUndo(c3))
    }

    @Test fun `undo remove then restore`() {
        val cs = ChangeSet("r", "del b", Source.MANUAL, 1L, listOf(Change.Remove(base.courses[1])))
        val s = base.apply(cs, 10L)
        assertTrue(s.canUndo(cs))
        assertFalse(s.canRedo(cs))
        val u = s.undo(cs, 20L)
        assertFalse(u.canUndo(cs))
        assertTrue(u.canRedo(cs))
    }

    @Test fun `diff of snapshots`() {
        val after = base.copy(courses = listOf(course("a", "高数", weeks = setOf(1, 2, 3)), course("z", "新课", dow = 5)))
        val diff = SnapshotDiff.diff(base, after)
        assertEquals(1, diff.count { it is Change.Modify })
        assertEquals(1, diff.count { it is Change.Add })
        assertEquals(1, diff.count { it is Change.Remove })
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

    @Test fun `attachments survive json and old records still decode`() {
        // 老记录没有 attachments 字段，必须还能读
        val old = MintJson.decodeFromString(Task.serializer(), """{"id":"t1","title":"旧记录"}""")
        assertTrue(old.attachments.isEmpty())
        val a = Attachment("实验图.png", "image/png", "abc.png")
        val back = MintJson.decodeFromString(Task.serializer(), MintJson.encodeToString(Task.serializer(), old.copy(attachments = listOf(a))))
        assertEquals(listOf(a), back.attachments)
    }

    @Test fun `block rows snap a time window onto periods`() {
        val periods =
            listOf(
                PeriodTime(1, LocalTime.of(8, 30), LocalTime.of(9, 15)),
                PeriodTime(2, LocalTime.of(9, 20), LocalTime.of(10, 5)),
                PeriodTime(3, LocalTime.of(10, 25), LocalTime.of(11, 10)),
            )
        assertEquals(2..2, Agenda.blockRows(periods, LocalTime.of(9, 30), LocalTime.of(10, 0)))
        assertEquals(1..3, Agenda.blockRows(periods, LocalTime.of(8, 40), LocalTime.of(11, 0)))
        // 作息之外吸附到最近一节，课表上仍然看得见
        assertEquals(1..1, Agenda.blockRows(periods, LocalTime.of(7, 0), LocalTime.of(7, 30)))
        assertEquals(3..3, Agenda.blockRows(periods, LocalTime.of(22, 0), LocalTime.of(22, 30)))
    }

    @Test fun `block without duration defaults to 30 minutes`() {
        val t = Task("t", "写报告", blockStart = LocalDateTime.of(2026, 10, 23, 14, 0))
        assertEquals(LocalDateTime.of(2026, 10, 23, 14, 30), TaskLogic.blockEnd(t))
        assertEquals(LocalDateTime.of(2026, 10, 23, 15, 0), TaskLogic.blockEnd(t.copy(estimateMinutes = 60)))
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
        assertEquals(1, cs.modifyCount)
        assertEquals(1, cs.addCount)
        assertEquals(0, cs.removeCount)
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
        assertEquals(3, r.priority)
        assertEquals("概率论", r.listName)
        assertEquals(listOf("作业"), r.tags)
        // 被识别的原文片段按类别返回，供输入框高亮
        assertEquals(
            listOf("!高" to QuickTokenKind.PRIORITY, "#概率论" to QuickTokenKind.LIST, "@作业" to QuickTokenKind.TAG, "明天" to QuickTokenKind.DATE, "下午3点半" to QuickTokenKind.TIME),
            r.tokens.map { it.text to it.kind },
        )
        assertTrue(QuickAdd.parse("买牛奶", now).tokens.isEmpty())
        val r2 = QuickAdd.parse("下周三 写实验报告", now)
        assertEquals(LocalDate.of(2026, 10, 28), r2.due!!.toLocalDate())
        assertEquals(false, r2.dueHasTime)
        val r3 = QuickAdd.parse("11月5日 交材料", now)
        assertEquals(LocalDate.of(2026, 11, 5), r3.due!!.toLocalDate())
        val r4 = QuickAdd.parse("买牛奶", now)
        assertNull(r4.due)
        assertEquals("买牛奶", r4.title)
        assertEquals(LocalDateTime.of(2026, 10, 23, 20, 0), QuickAdd.parse("晚上八点开会", now).due)
        val r5 = QuickAdd.parse("27号18点 数学作业", now)
        assertEquals(LocalDateTime.of(2026, 10, 27, 18, 0), r5.due)
        assertEquals("数学作业", r5.title)
        // 本月已过的「N号」顺延到下个月
        val r6 = QuickAdd.parse("3号 交房租", now)
        assertEquals(LocalDate.of(2026, 11, 3), r6.due!!.toLocalDate())
        assertEquals("交房租", r6.title)
        // 「3D」的 3 不是「点」的分钟
        val r7 = QuickAdd.parse("后天早上 10 点 3D 打印", now)
        assertEquals(LocalDateTime.of(2026, 10, 25, 10, 0), r7.due)
        assertEquals("3D 打印", r7.title)
    }

    @Test fun `quick add understands more date forms`() {
        fun due(s: String) = QuickAdd.parse(s, now).due

        assertEquals("买菜", QuickAdd.parse("周末 买菜", now).title)
        assertEquals(LocalDate.of(2026, 10, 24), due("周末 买菜")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 31), due("下周末 买菜")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 26), due("下周 交表")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 26), due("3天后 交表")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 31), due("月底 结账")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 26), due("10/26 交表")!!.toLocalDate())
        // 「今晚8点」不能当成早上八点；显式时刻仍以写出来的为准
        assertEquals(LocalDateTime.of(2026, 10, 23, 20, 0), due("今晚8点 跑步"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 45), due("明早九点三刻 跑步"))
        // 清单名后面的中文标点不算进名字
        assertEquals("课程", QuickAdd.parse("#课程，复习", now).listName)
        assertEquals("复习", QuickAdd.parse("#课程，复习", now).title)
    }

    @Test fun `smart lists and recurring completion`() {
        val tasks =
            listOf(
                Task("t1", "今天的", due = LocalDateTime.of(2026, 10, 23, 18, 0), dueHasTime = true),
                Task("t2", "逾期", due = LocalDateTime.of(2026, 10, 20, 0, 0)),
                Task("t3", "下周", due = LocalDateTime.of(2026, 10, 27, 0, 0)),
                Task("t4", "做完", done = true),
                Task("t5", "子", parentId = "t1"),
                Task("t6", "今天已完成", due = LocalDateTime.of(2026, 10, 23, 9, 0), dueHasTime = true, done = true),
            )
        assertEquals(listOf("t2", "t1", "t6"), TaskLogic.filter(tasks, SmartList.TODAY, now).map { it.id })
        assertEquals(listOf("t3"), TaskLogic.filter(tasks, SmartList.PLANNED, now).map { it.id })
        assertEquals(listOf("t6", "t4"), TaskLogic.filter(tasks, SmartList.DONE, now).map { it.id })
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

    @Test fun `export bundle imports legacy mintcal-web json`() {
        val list = TaskList("l1", "学习")
        val task = Task("t1", "复习", listId = "l1")
        val c = course("c1", "线代")
        val recordsJson =
            listOf(sem, c, list, task).joinToString(",") {
                MintJson.encodeToString(
                    app.mintcal.core.model.Record
                        .serializer(),
                    it,
                )
            }
        val iso = "2026-11-21T08:00:00.000Z"
        val legacy = """{"app":"mintcal-web","exportedAt":"$iso","records":[$recordsJson]}"""
        val parsed = ExportBundle.fromJson(legacy)
        assertEquals(Instant.parse(iso).toEpochMilli(), parsed.exportedAt)
        assertEquals(listOf(sem), parsed.snapshot.semesters)
        assertEquals(listOf(c), parsed.snapshot.courses)
        assertEquals(listOf(list), parsed.snapshot.lists)
        assertEquals(listOf(task), parsed.snapshot.tasks)
    }

    @Test fun `unify eventToTask preserves kind location checkable tags and timing`() {
        val exam =
            Event(
                id = "e1",
                title = "高数期末",
                start = LocalDateTime.of(2026, 11, 27, 9, 0),
                end = LocalDateTime.of(2026, 11, 27, 11, 0),
                location = "教1-201",
                note = "闭卷",
                category = "考试",
                remindMinutes = 60,
                kind = EventKind.EXAM,
            )
        val examTask = Unify.eventToTask(exam, "t-exam", 1)
        assertEquals("闭卷", examTask.note)
        assertEquals(listOf("考试"), examTask.tags)
        assertEquals("EXAM", examTask.extra["kind"])
        assertEquals("false", examTask.extra["checkable"])
        assertEquals("教1-201", examTask.extra["location"])
        assertEquals(LocalDateTime.of(2026, 11, 27, 9, 0), examTask.blockStart)
        assertEquals(120, examTask.blockMinutes)
        assertEquals(LocalDateTime.of(2026, 11, 27, 11, 0), examTask.due)
        assertTrue(examTask.dueHasTime)

        val countdown =
            Event(
                id = "e2",
                title = "四级考试",
                start = LocalDateTime.of(2026, 12, 12, 0, 0),
                end = LocalDateTime.of(2026, 12, 13, 0, 0),
                allDay = true,
                category = "英语",
                kind = EventKind.COUNTDOWN,
            )
        val countdownTask = Unify.eventToTask(countdown, "t-cd", 2)
        assertEquals(listOf("倒数日", "英语"), countdownTask.tags)
        assertEquals("COUNTDOWN", countdownTask.extra["kind"])
        assertEquals("false", countdownTask.extra["checkable"])
        assertNull(countdownTask.blockStart)
        assertFalse(countdownTask.dueHasTime)

        val rec = Recurrence(Freq.WEEKLY, byDays = setOf(4))
        val normal =
            Event(
                id = "e3",
                title = "社团例会",
                start = LocalDateTime.of(2026, 11, 26, 19, 0),
                end = LocalDateTime.of(2026, 11, 26, 20, 30),
                location = "艺术楼",
                category = "社团",
                recurrence = rec,
            )
        val normalTask = Unify.eventToTask(normal, "t-norm", 3)
        assertEquals(listOf("社团"), normalTask.tags)
        assertNull(normalTask.extra["kind"])
        assertNull(normalTask.extra["checkable"])
        assertEquals("艺术楼", normalTask.extra["location"])
        assertEquals(rec, normalTask.repeat)
    }
}
