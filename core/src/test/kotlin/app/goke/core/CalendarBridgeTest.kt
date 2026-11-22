package app.goke.core

import app.goke.core.change.apply
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.ExceptionType
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.sync.CalendarBridge
import app.goke.core.sync.CalendarTaskLink
import app.goke.core.sync.CourseExportLink
import app.goke.core.sync.SystemEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarBridgeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val sem = TestUtil.semester

    @Test
    fun `push local scheduled task then second run is zero-echo idempotent`() {
        val start = LocalDateTime.of(2026, 11, 12, 15, 0)
        val task =
            Task(
                id = "t1",
                title = "组会汇报",
                note = "带上实验数据",
                blockStart = start,
                blockMinutes = 60,
                due = start.plusMinutes(60),
                dueHasTime = true,
                remindMinutes = 15,
                extra = mapOf("location" to "工学一号馆 402"),
                updatedAt = 1_000L,
            )
        val snap = Snapshot(semesters = listOf(sem), tasks = listOf(task))

        val p1 =
            CalendarBridge.planBidirectional(
                snapshot = snap,
                remoteEvents = emptyList(),
                existingLinks = emptyList(),
                primaryCalendarId = 10L,
                selectedCalendarIds = setOf(10L),
                zone = zone,
                nowMillis = 2_000L,
            )
        assertNull(p1.localChanges)
        assertEquals(1, p1.remoteInserts.size)
        val ins = p1.remoteInserts.single()
        assertEquals("组会汇报", ins.draft.title)
        assertEquals("工学一号馆 402", ins.draft.location)
        assertEquals(15, ins.draft.remindMinutes)

        val simRemote = CalendarBridge.draftToSimulatedEvent(501L, ins.draft)
        val links =
            listOf(
                CalendarTaskLink(
                    taskId = ins.taskId,
                    calendarId = 10L,
                    eventId = 501L,
                    taskHash = ins.taskHash,
                    eventHash = CalendarBridge.eventHash(simRemote, zone),
                    syncedAt = 2_000L,
                ),
            )

        // 第二次立即同步：双端无改动，0 写入（防回声循环）
        val p2 =
            CalendarBridge.planBidirectional(
                snapshot = snap,
                remoteEvents = listOf(simRemote),
                existingLinks = links,
                primaryCalendarId = 10L,
                selectedCalendarIds = setOf(10L),
                zone = zone,
                nowMillis = 3_000L,
            )
        assertEquals(0, p2.totalMutations)
        assertEquals(1, p2.retainedAndPulledLinks.size)
    }

    @Test
    fun `pull timed all-day and recurring events from system calendar and propagate edits and deletes`() {
        val timedStart =
            LocalDateTime
                .of(2026, 11, 13, 10, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val timedEnd =
            LocalDateTime
                .of(2026, 11, 13, 11, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val allDayStart =
            LocalDate
                .of(2026, 11, 15)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val allDayEnd =
            LocalDate
                .of(2026, 11, 16)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

        val r1 =
            SystemEvent(
                eventId = 101L,
                calendarId = 7L,
                title = "牙医预约",
                description = "复查",
                location = "校医院",
                dtStartMillis = timedStart,
                dtEndMillis = timedEnd,
                allDay = false,
                remindMinutes = 30,
            )
        val r2 =
            SystemEvent(
                eventId = 102L,
                calendarId = 7L,
                title = "校庆日",
                dtStartMillis = allDayStart,
                dtEndMillis = allDayEnd,
                allDay = true,
            )
        val r3 =
            SystemEvent(
                eventId = 103L,
                calendarId = 7L,
                title = "晨跑",
                dtStartMillis = timedStart,
                dtEndMillis = null,
                durationRfc2445 = "PT45M",
                allDay = false,
                rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            )

        val p1 =
            CalendarBridge.planBidirectional(
                snapshot = Snapshot(semesters = listOf(sem)),
                remoteEvents = listOf(r1, r2, r3),
                existingLinks = emptyList(),
                primaryCalendarId = 7L,
                selectedCalendarIds = setOf(7L),
                zone = zone,
                nowMillis = 5_000L,
            )
        val cs1 = assertNotNull(p1.localChanges)
        assertEquals(3, cs1.changes.size)
        val snap1 = Snapshot(semesters = listOf(sem)).apply(cs1, 5_000L)

        val tDentist = snap1.tasks.first { it.title == "牙医预约" }
        assertEquals(LocalDateTime.of(2026, 11, 13, 10, 0), tDentist.blockStart)
        assertEquals(90, tDentist.blockMinutes)
        assertEquals("校医院", tDentist.extra["location"])

        val tAllDay = snap1.tasks.first { it.title == "校庆日" }
        assertNull(tAllDay.blockStart)
        assertEquals(LocalDate.of(2026, 11, 15).atTime(23, 59), tAllDay.due)
        assertEquals(false, tAllDay.dueHasTime)

        val tRun = snap1.tasks.first { it.title == "晨跑" }
        assertEquals(45, tRun.blockMinutes)
        assertEquals(Recurrence(Freq.WEEKLY, byDays = setOf(1, 3, 5)), tRun.repeat)

        // 二次同步幂等
        val p2 =
            CalendarBridge.planBidirectional(
                snapshot = snap1,
                remoteEvents = listOf(r1, r2, r3),
                existingLinks = p1.retainedAndPulledLinks,
                primaryCalendarId = 7L,
                selectedCalendarIds = setOf(7L),
                zone = zone,
                nowMillis = 6_000L,
            )
        assertEquals(0, p2.totalMutations)

        // 系统日历侧改名 + 删除全天事件 → 本地同步跟进
        val r1Edited = r1.copy(title = "牙医复诊")
        val p3 =
            CalendarBridge.planBidirectional(
                snapshot = snap1,
                remoteEvents = listOf(r1Edited, r3),
                existingLinks = p2.retainedAndPulledLinks,
                primaryCalendarId = 7L,
                selectedCalendarIds = setOf(7L),
                zone = zone,
                nowMillis = 7_000L,
            )
        val cs3 = assertNotNull(p3.localChanges)
        val snap3 = snap1.apply(cs3, 7_000L)
        val liveTitles =
            snap3
                .live()
                .tasks
                .map { it.title }
                .toSet()
        assertEquals(setOf("牙医复诊", "晨跑"), liveTitles)
    }

    @Test
    fun `unselecting calendar account drops links without deleting local tasks`() {
        val start = LocalDateTime.of(2026, 11, 12, 9, 0)
        val task = Task(id = "t9", title = "面试", blockStart = start, blockMinutes = 45, due = start.plusMinutes(45), dueHasTime = true)
        val link =
            CalendarTaskLink(
                taskId = "t9",
                calendarId = 88L,
                eventId = 900L,
                taskHash = CalendarBridge.taskHash(task, 30, zone),
                eventHash = "h",
                syncedAt = 1_000L,
            )
        // 用户切换到只勾选 99L 账本（取消了 88L）
        val plan =
            CalendarBridge.planBidirectional(
                snapshot = Snapshot(semesters = listOf(sem), tasks = listOf(task)),
                remoteEvents = emptyList(),
                existingLinks = listOf(link),
                primaryCalendarId = 99L,
                selectedCalendarIds = setOf(99L),
                zone = zone,
                nowMillis = 2_000L,
            )
        // 本地任务绝不被误删，而是推入新的目标日历 99L
        assertNull(plan.localChanges)
        assertEquals(1, plan.remoteInserts.size)
        assertEquals(
            99L,
            plan.remoteInserts
                .single()
                .draft.calendarId,
        )
    }

    @Test
    fun `course export expands semester skips cancelled weeks and cleans up when disabled`() {
        val c = Course("c1", sem.id, "操作系统", teacher = "张老师", room = "教2-301", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2, 3))
        val cancelW2 = CourseException("ex1", "c1", week = 2, type = ExceptionType.CANCEL)
        val snap = Snapshot(semesters = listOf(sem), courses = listOf(c), exceptions = listOf(cancelW2))

        val p1 = CalendarBridge.planCourseExport(snap, enabled = true, targetCalendarId = 12L, existingLinks = emptyList(), remoteEvents = emptyList(), zone = zone)
        assertEquals(2, p1.inserts.size) // 第 1、3 周，第 2 周停课跳过
        assertTrue(p1.inserts.all { it.draft.title == "操作系统" && it.draft.location == "教2-301" })

        val simulatedRemotes = p1.inserts.mapIndexed { idx, ci -> CalendarBridge.draftToSimulatedEvent(1000L + idx, ci.draft) }
        val links = p1.inserts.mapIndexed { idx, ci -> CourseExportLink(ci.slotKey, 12L, 1000L + idx, ci.slotHash) }

        // 二次运行 0 改动，且双向任务同步会自动忽略 goke://course/* 事件
        val p2 = CalendarBridge.planCourseExport(snap, enabled = true, targetCalendarId = 12L, existingLinks = links, remoteEvents = simulatedRemotes, zone = zone)
        assertEquals(0, p2.totalMutations)

        val bi = CalendarBridge.planBidirectional(snap, simulatedRemotes, emptyList(), 12L, setOf(12L), zone = zone)
        assertEquals(0, bi.totalMutations)

        // 关闭课程导出时清理所有已导出课程事件
        val pOff = CalendarBridge.planCourseExport(snap, enabled = false, targetCalendarId = 12L, existingLinks = links, remoteEvents = simulatedRemotes, zone = zone)
        assertEquals(2, pOff.deletes.size)
    }
}
