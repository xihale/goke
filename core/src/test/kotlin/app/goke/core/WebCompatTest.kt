package app.goke.core

import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.ExceptionType
import app.goke.core.model.Freq
import app.goke.core.model.Semester
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.sync.SyncCodec
import app.goke.core.sync.WireRecord
import app.goke.core.util.MintJson
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 跨端兼容：`web-records.json` 由 Web 端的记录工厂生成（`server/webtest/gen-fixture.mjs`），
 * 必须能被 Android 端的同步解码器原样读出。
 */
class WebCompatTest {
    private val wire: List<WireRecord> by lazy {
        val text = javaClass.getResource("/web-records.json")!!.readText()
        MintJson.decodeFromString(ListSerializer(WireRecord.serializer()), text)
    }

    private val records by lazy { wire.map { assertNotNull(SyncCodec.decode(it), "无法解码 ${it.kind}") } }

    private inline fun <reified T> one(pred: (T) -> Boolean = { true }): T = records.filterIsInstance<T>().first(pred)

    @Test fun `every web record decodes`() {
        assertEquals(11, records.size)
        for ((w, r) in wire.zip(records)) {
            assertEquals(w.id, r.id)
            assertEquals(w.kind, r::class.simpleName)
            assertEquals(w.updatedAt, r.updatedAt)
            assertEquals(w.deletedAt, r.deletedAt)
        }
    }

    @Test fun `semester and course`() {
        val s = one<Semester>()
        assertEquals("大一下", s.name)
        assertEquals(LocalDate.of(2026, 8, 31), s.week1Monday)
        assertEquals(20, s.totalWeeks)
        assertEquals(12, s.periods.size)
        assertTrue(s.isCurrent)
        val c = one<Course>()
        assertEquals(s.id, c.semesterId)
        assertEquals(setOf(1, 2, 3, 5), c.weeks)
        assertEquals(15, c.remindMinutes)
        assertEquals("带计算器", c.note)
        assertEquals(2, c.dayOfWeek)
    }

    @Test fun `course exceptions`() {
        val cancel = one<CourseException> { it.type == ExceptionType.CANCEL }
        assertEquals(2, cancel.week)
        assertNull(cancel.newWeek)
        val moved = one<CourseException> { it.type == ExceptionType.RESCHEDULE }
        assertEquals(4, moved.newWeek)
        assertEquals(5, moved.newDayOfWeek)
        assertEquals("教5-105", moved.newRoom)
    }

    @Test fun `events and recurrence`() {
        val e = one<Event> { it.title == "社团例会" }
        assertEquals(LocalDateTime.of(2026, 10, 23, 20, 0), e.start)
        assertEquals(LocalDateTime.of(2026, 10, 23, 21, 30), e.end)
        assertEquals(EventKind.EXAM, e.kind)
        val r = assertNotNull(e.recurrence)
        assertEquals(Freq.WEEKLY, r.freq)
        assertEquals(2, r.interval)
        assertEquals(setOf(1, 5), r.byDays)
        assertEquals(LocalDate.of(2026, 12, 31), r.until)
        assertEquals(setOf(LocalDate.of(2026, 11, 6)), r.exDates)
        val d = one<Event> { it.title == "生日" }
        assertTrue(d.allDay)
        assertEquals(EventKind.COUNTDOWN, d.kind)
        assertNull(d.recurrence)
    }

    @Test fun `lists and tasks`() {
        val l = one<TaskList>()
        assertEquals("学习", l.name)
        val t = one<Task> { it.title == "交作业" }
        assertEquals(l.id, t.listId)
        assertEquals(listOf("数学", "紧急"), t.tags.toList())
        assertEquals(3, t.priority)
        assertEquals(LocalDate.of(2026, 10, 20), t.startDate)
        assertEquals(LocalDateTime.of(2026, 10, 23, 18, 0), t.due)
        assertTrue(t.dueHasTime)
        assertEquals(90, t.estimateMinutes)
        assertEquals(Freq.DAILY, assertNotNull(t.repeat).freq)
        assertEquals(5, t.repeat!!.count)
        val child = one<Task> { it.title == "子任务" }
        assertEquals(t.id, child.parentId)
        assertEquals(2, child.sortOrder)
        val done = one<Task> { it.title == "已完成" }
        assertNotNull(done.doneAt)
        val gone = one<Task> { it.title == "已删除" }
        assertNotNull(gone.deletedAt)
    }
}
