package app.mintcal.script

import app.mintcal.core.change.Change
import app.mintcal.core.change.apply
import app.mintcal.core.change.undo
import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.util.SequentialIds
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ScriptRunnerTest {
    private val sem = Semester("sem", "2026-2027 第一学期", LocalDate.of(2026, 8, 31))
    private fun course(id: String, name: String, day: Int, s: Int, e: Int, weeks: Set<Int> = (1..16).toSet()) =
        Course(id, "sem", name, "老师", "A101", day, s, e, weeks)

    private val base = Snapshot(
        semesters = listOf(sem),
        courses = listOf(course("c1", "高等数学", 1, 1, 2), course("c2", "大学英语", 2, 3, 4), course("c3", "线性代数", 3, 5, 6)),
    )
    private val opts = ScriptOptions(today = LocalDate.of(2026, 9, 2), nowMillis = 1000)
    private val runner = ScriptRunner(SequentialIds("cs"))

    private fun run(code: String, snap: Snapshot = base, o: ScriptOptions = opts): ScriptOutcome =
        runBlocking { runner.dryRun(code, snap, o, "测试") }

    private fun ok(code: String, snap: Snapshot = base): app.mintcal.core.change.ChangeSet {
        val r = run(code, snap)
        if (r is ScriptOutcome.Failure) error("script failed: ${r.message} line=${r.line}")
        return (r as ScriptOutcome.Success).changeSet
    }

    @Test fun modifyRoomByQuery() {
        val cs = ok("mc.courses.query({ name: '数学' }).forEach(c => mc.courses.update(c.id, { room: '工1-201' }))")
        assertEquals(1, cs.changes.size)
        val m = assertIs<Change.Modify>(cs.changes[0])
        assertEquals("工1-201", (m.after as Course).room)
        assertEquals("A101", (m.before as Course).room)
    }

    @Test fun noOpProducesEmptyChangeSet() {
        val cs = ok("const x = mc.courses.query(); console.log('共', x.length, '门')")
        assertTrue(cs.isEmpty)
        assertEquals(listOf("共 3 门"), cs.logs)
    }

    @Test fun createAndConflictWarning() {
        val cs = ok("mc.courses.create({ name: '体育', day: 1, startPeriod: 2, endPeriod: 3, weeks: '1-16' })")
        assertEquals(1, cs.addCount)
        assertTrue(cs.warnings.any { it.contains("体育") && it.contains("冲突") }, cs.warnings.toString())
    }

    @Test fun weekExpressionsAndColors() {
        val cs = ok("mc.courses.create({ name: '实验', day: 5, startPeriod: 1, weeks: '2-10双', room: 'B' })")
        val c = (cs.changes.single() as Change.Add).after as Course
        assertEquals(setOf(2, 4, 6, 8, 10), c.weeks)
        assertEquals(1, c.endPeriod)
        assertEquals(Source.CODE, c.source)
    }

    @Test fun removeCascadesExceptions() {
        val cs = ok("""
            const c = mc.courses.query({ name: '英语' })[0];
            mc.courses.cancel(c, 3);
            mc.courses.remove(c);
        """.trimIndent())
        // 先加停课再删课程：停课记录与课程一并消失，只剩一条删除
        assertEquals(1, cs.changes.size)
        assertIs<Change.Remove>(cs.changes[0])
    }

    @Test fun cancelAndRescheduleCreateExceptions() {
        val cs = ok("""
            mc.courses.cancel('c1', 3);
            mc.courses.reschedule('c2', 5, { day: 4, startPeriod: 7, room: '临时教室' });
            return mc.courses.query().length;
        """.trimIndent())
        assertEquals(2, cs.addCount)
        assertEquals("3", cs.returnValue)
    }

    @Test fun applyAndUndoRestoreExactly() {
        val cs = ok("""
            mc.courses.update('c1', { name: '高数A' });
            mc.courses.remove('c3');
            mc.events.create({ title: '期中考试', start: '2026-10-20 09:00', kind: 'EXAM' });
        """.trimIndent())
        assertEquals(3, cs.changes.size)
        val applied = base.apply(cs, 2000)
        assertEquals("高数A", applied.live().courses.first { it.id == "c1" }.name)
        assertEquals(2, applied.live().courses.size)
        assertEquals(1, applied.live().events.size)
        val ev: Event = applied.live().events.single()
        assertEquals("2026-10-20T10:00", ev.end.toString())
        val back = applied.undo(cs, 3000).live()
        assertEquals(base.live().courses.map { it.copy(updatedAt = 0) }.sortedBy { it.id }, back.courses.map { it.copy(updatedAt = 0) }.sortedBy { it.id })
        assertTrue(back.events.isEmpty())
    }

    @Test fun dryRunDoesNotMutateInput() {
        val before = base.toString()
        ok("mc.courses.removeWhere(() => true)")
        assertEquals(before, base.toString())
    }

    @Test fun tasksAndLists() {
        val cs = ok("""
            const l = mc.lists.create({ name: '期末复习' });
            mc.tasks.create({ title: '复习高数', listId: l.id, due: '2026-12-20', priority: 3 });
            const t = mc.tasks.create({ title: '交作业', dueHasTime: true, due: '2027-01-02 23:00' });
            mc.tasks.complete(t);
        """.trimIndent())
        assertEquals(3, cs.addCount)
    }

    @Test fun readOnlyHelpers() {
        val cs = ok("console.log(mc.currentWeek, mc.dateOf(2, 3), mc.weekOf('2026-09-14'), mc.periods.length, mc.today)")
        assertEquals(listOf("1 2026-09-09 3 12 2026-09-02"), cs.logs)
    }

    @Test fun syntaxErrorReported() {
        val r = run("let = = 1;")
        val f = assertIs<ScriptOutcome.Failure>(r)
        assertTrue(f.message.isNotBlank())
    }

    @Test fun runtimeErrorHasLineAndLogs() {
        val r = run("console.log('开始');\nconsole.log('第二行');\nthrow new Error('出错了');")
        val f = assertIs<ScriptOutcome.Failure>(r)
        assertTrue(f.message.contains("出错了"), f.message)
        assertEquals(3, f.line)
        assertEquals(listOf("开始", "第二行"), f.logs)
    }

    @Test fun validationErrorThrown() {
        val f = assertIs<ScriptOutcome.Failure>(run("mc.courses.create({ name: 'x', day: 9, startPeriod: 1, weeks: [1] })"))
        assertTrue(f.message.contains("dayOfWeek"), f.message)
    }

    @Test fun infiniteLoopTimesOut() {
        val t0 = System.currentTimeMillis()
        val f = assertIs<ScriptOutcome.Failure>(run("while (true) {}", o = opts.copy(timeoutMillis = 800)))
        assertTrue(f.message.contains("超过"), f.message)
        assertTrue(System.currentTimeMillis() - t0 < 5000)
    }

    @Test fun memoryBombStopped() {
        val f = assertIs<ScriptOutcome.Failure>(run("const a = []; while (true) a.push('x'.repeat(100000));", o = opts.copy(memoryLimitBytes = 16L * 1024 * 1024, timeoutMillis = 4000)))
        assertNotNull(f.message)
    }

    @Test fun sandboxHasNoIo() {
        val f = run("return [typeof fetch, typeof require, typeof XMLHttpRequest, typeof setTimeout].join(',')")
        val cs = (f as ScriptOutcome.Success).changeSet
        assertEquals("\"undefined,undefined,undefined,undefined\"", cs.returnValue)
    }

    @Test fun aiSourceIsRecorded() {
        val r = runBlocking { runner.dryRun("mc.events.create({ title: 'a', start: '2026-09-03T08:00' })", base, opts.copy(source = Source.AI)) }
        val cs = (r as ScriptOutcome.Success).changeSet
        assertEquals(Source.AI, cs.source)
        assertEquals(Source.AI, ((cs.changes.single() as Change.Add).after as Event).source)
    }
}
