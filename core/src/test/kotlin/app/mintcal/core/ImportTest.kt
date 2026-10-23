package app.mintcal.core

import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.importer.IcsParser
import app.mintcal.core.importer.IcsWriter
import app.mintcal.core.importer.XlsxTimetableImporter
import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.util.SequentialIds
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportTest {
    private val sem = TestUtil.semester

    private fun gdutCourses(): List<Course> {
        val text = String(TestUtil.resource("/samples/gdut.ics"), Charsets.UTF_8)
        val parsed = IcsParser.parse(text)
        assertTrue(parsed.ok)
        val cal = parsed.value!!
        assertEquals(31, cal.events.size)
        assertTrue(IcsImporter.looksLikeTimetable(cal))
        assertEquals(LocalDate.of(2026, 8, 31), IcsImporter.suggestWeek1Monday(cal))
        val res = IcsImporter.toCourses(cal, sem, now = 1L, ids = SequentialIds())
        assertTrue(res.ok, res.errors.toString())
        assertTrue(res.value!!.skipped.isEmpty())
        return res.value!!.courses
    }

    @Test fun `wakeup ics merges week segments into course rules`() {
        val courses = gdutCourses()
        val prob = courses.filter { it.name == "概率论与数理统计" }
        assertEquals(2, prob.size) // 周一 + 周五
        val mon = prob.first { it.dayOfWeek == 1 }
        assertEquals((1..14).toSet(), mon.weeks)
        assertEquals(1, mon.startPeriod)
        assertEquals(2, mon.endPeriod)
        assertEquals("教3-204", mon.room)
        assertEquals("涂钰青", mon.teacher)
        assertEquals(18, mon.remindMinutes)
        // 同一门课的多个周次段按「星期 + 节次」分组合并
        val clinicMon = courses.single { it.name == "校医院助理" && it.dayOfWeek == 1 && it.startPeriod == 3 }
        assertEquals(setOf(1), clinicMon.weeks)
        val clinicFri = courses.single { it.name == "校医院助理" && it.dayOfWeek == 5 }
        assertEquals((3..9).toSet(), clinicFri.weeks)
        // 劳动教育：1、5、9、13 周
        val labor = courses.single { it.name == "劳动教育" }
        assertEquals(setOf(1, 5, 9, 13), labor.weeks)
        // 房间与教师含逗号 / 空格的不被拆坏
        val phys = courses.single { it.name == "大学物理实验" }
        assertTrue(phys.room.contains("实4-407A"))
        assertEquals("陈峻", phys.teacher)
        assertEquals("教 2-225", courses.single { it.name == "数字逻辑与系统设计" && it.dayOfWeek == 1 }.room)
        val aesthetics = courses.single { it.name == "大学美育(2)" }
        assertEquals("刘乐舟、彭凯、唐靓", aesthetics.teacher)
        val photo = courses.single { it.name == "讲座拍照" }
        assertEquals("文化活动中心 演艺厅", photo.room)
        assertEquals("", photo.teacher)
        // 同名同色
        assertEquals(
            1,
            courses
                .filter { it.name == "数据结构" }
                .map { it.colorIndex }
                .toSet()
                .size,
        )
    }

    @Test fun `xlsx timetable import aggregates 20 sheets`() {
        val res = XlsxTimetableImporter.import(TestUtil.resource("/samples/timetable.xlsx"), sem, now = 1L, ids = SequentialIds())
        assertTrue(res.ok, res.errors.toString())
        val courses = res.value!!.courses
        assertEquals(28, courses.size)
        val mon = courses.single { it.name == "概率论与数理统计" && it.dayOfWeek == 1 }
        assertEquals((1..14).toSet(), mon.weeks)
        assertEquals(1, mon.startPeriod)
        assertEquals(2, mon.endPeriod)
        assertEquals("教3-204", mon.room)
        assertEquals(setOf(1, 5, 9, 13), courses.single { it.name == "劳动教育" }.weeks)
        val print3d = courses.single { it.name == "3D打印综合训练" }
        assertEquals(10, print3d.startPeriod)
        assertEquals(12, print3d.endPeriod)
        assertEquals(8, courses.count { it.name == "大学物理实验" })
        assertEquals(18, res.value!!.maxWeek)
    }

    @Test fun `ics and xlsx agree on shared courses`() {
        val ics = gdutCourses()
        val xlsx = XlsxTimetableImporter.import(TestUtil.resource("/samples/timetable.xlsx"), sem, 1L).value!!.courses
        val a = ics.single { it.name == "3D打印综合训练" }
        val b = xlsx.single { it.name == "3D打印综合训练" }
        assertEquals(a.weeks, b.weeks)
        assertEquals(a.dayOfWeek, b.dayOfWeek)
        assertEquals(a.room, b.room)
    }

    @Test fun `writer output can be parsed back`() {
        val ev =
            Event(
                "e1",
                "组会, 讨论;进度",
                LocalDateTime.of(2026, 9, 2, 14, 0),
                LocalDateTime.of(2026, 9, 2, 15, 30),
                location = "实验室",
                note = "第一行\n第二行",
                remindMinutes = 10,
            )
        val allDay = Event("e2", "考试周", LocalDateTime.of(2026, 12, 28, 0, 0), LocalDateTime.of(2026, 12, 30, 0, 0), allDay = true)
        val task = Task("t1", "交作业", due = LocalDateTime.of(2026, 9, 5, 18, 0), dueHasTime = true, priority = 3)
        val snap = Snapshot(semesters = listOf(sem), courses = gdutCourses().take(3), events = listOf(ev, allDay), tasks = listOf(task))
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val text = IcsWriter.write(snap, zone, 0L)
        assertTrue(text.lines().all { it.toByteArray(Charsets.UTF_8).size <= 76 })
        val parsed = IcsParser.parse(text, zone)
        assertTrue(parsed.ok)
        val cal = parsed.value!!
        val e1 = cal.events.single { it.summary == "组会, 讨论;进度" }
        assertEquals(LocalDateTime.of(2026, 9, 2, 14, 0), e1.start)
        assertEquals("第一行\n第二行", e1.description)
        assertEquals(10, e1.alarmMinutes)
        val e2 = cal.events.single { it.summary == "考试周" }
        assertTrue(e2.allDay)
        assertEquals(1, cal.todos.size)
        assertEquals(LocalDateTime.of(2026, 9, 5, 18, 0), cal.todos[0].due)
        val back = IcsImporter.toEvents(cal, 0L).value!!.single { it.title == "考试周" }
        assertEquals(LocalDateTime.of(2026, 12, 30, 0, 0), back.end)
        // 课程导出后再导入应得到相同的课程规则
        val courseCal = IcsParser.parse(text, zone).value!!
        val reCourses = IcsImporter.toCourses(courseCal, sem, 0L).value!!.courses
        assertNotNull(reCourses.firstOrNull { it.name == snap.courses[0].name })
    }
}
