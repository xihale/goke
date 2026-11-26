package app.goke.core.export

import app.goke.core.importer.IcsParser
import app.goke.core.importer.IcsWriter
import app.goke.core.importer.XlsxReader
import app.goke.core.importer.XlsxTimetableImporter
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.ExceptionType
import app.goke.core.model.GdutPeriods
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.util.SequentialIds
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimetableExportTest {
    private val fallSem =
        Semester(
            id = "sem-fall",
            name = "2026-2027学年第一学期",
            week1Monday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
            periods = GdutPeriods.default,
        )

    private val springSem =
        Semester(
            id = "sem-spring",
            name = "2026-2027学年第二学期",
            week1Monday = LocalDate.of(2027, 3, 1),
            totalWeeks = 18,
            periods = GdutPeriods.default,
        )

    private fun readZipEntries(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    @Test
    fun `xlsx round-trip preserves courses, rooms, weeks, periods, and exceptions`() {
        val courses =
            listOf(
                Course(
                    id = "c1",
                    semesterId = fallSem.id,
                    name = "高等数学",
                    teacher = "张老师",
                    room = "教1-101",
                    dayOfWeek = 1,
                    startPeriod = 1,
                    endPeriod = 2,
                    weeks = setOf(1, 2, 3),
                    colorIndex = 0,
                ),
                Course(
                    id = "c2",
                    semesterId = fallSem.id,
                    name = "大学物理",
                    teacher = "李老师",
                    room = "教2-202",
                    dayOfWeek = 3,
                    startPeriod = 5,
                    endPeriod = 7,
                    weeks = setOf(1, 2),
                    colorIndex = 2,
                ),
            )
        val exceptions =
            listOf(
                // 第 2 周高等数学取消
                CourseException(
                    id = "ex1",
                    courseId = "c1",
                    week = 2,
                    type = ExceptionType.CANCEL,
                ),
                // 第 2 周大学物理调到第 4 周周四 3-4 节
                CourseException(
                    id = "ex2",
                    courseId = "c2",
                    week = 2,
                    type = ExceptionType.RESCHEDULE,
                    newWeek = 4,
                    newDayOfWeek = 4,
                    newStartPeriod = 3,
                    newEndPeriod = 4,
                    newRoom = "实验楼301",
                ),
            )

        val bytes = XlsxTimetableWriter.write(fallSem, courses, exceptions, includeTeacher = true)
        val sheets = XlsxReader.read(bytes)
        assertTrue(sheets.ok, sheets.errors.toString())
        assertEquals(fallSem.totalWeeks, sheets.value!!.size)
        assertEquals("第1周", sheets.value[0].name)
        assertEquals("周一 9/7", sheets.value[0].text(1, 2))

        val imported = XlsxTimetableImporter.import(bytes, fallSem, now = 100L, ids = SequentialIds())
        assertTrue(imported.ok, imported.errors.toString())
        val list = imported.value!!.courses

        val math = list.single { it.name == "高等数学" }
        assertEquals("教1-101", math.room)
        assertEquals("张老师", math.teacher)
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals(setOf(1, 3), math.weeks)

        val physOrig = list.single { it.name == "大学物理" && it.dayOfWeek == 3 }
        assertEquals(setOf(1), physOrig.weeks)
        assertEquals(5, physOrig.startPeriod)
        assertEquals(7, physOrig.endPeriod)
        assertEquals("教2-202", physOrig.room)

        val physMoved = list.single { it.name == "大学物理" && it.dayOfWeek == 4 }
        assertEquals(setOf(4), physMoved.weeks)
        assertEquals(3, physMoved.startPeriod)
        assertEquals(4, physMoved.endPeriod)
        assertEquals("实验楼301", physMoved.room)
    }

    @Test
    fun `xlsx styles include all 8 LightCourse RGBs and collisions emit no overlapping mergeCell`() {
        val courses =
            listOf(
                Course(
                    id = "c1",
                    semesterId = fallSem.id,
                    name = "数据结构",
                    teacher = "王老师",
                    room = "A101",
                    dayOfWeek = 2,
                    startPeriod = 1,
                    endPeriod = 3,
                    weeks = setOf(1),
                    colorIndex = 1,
                ),
                Course(
                    id = "c2",
                    semesterId = fallSem.id,
                    name = "英语口语",
                    teacher = "赵老师",
                    room = "B202",
                    dayOfWeek = 2,
                    startPeriod = 2,
                    endPeriod = 2,
                    weeks = setOf(1),
                    colorIndex = 4,
                ),
            )

        val bytes = XlsxTimetableWriter.write(fallSem, courses, includeTeacher = false)
        val entries = readZipEntries(bytes)

        val stylesXml = entries["xl/styles.xml"]
        assertNotNull(stylesXml)
        val expectedRgbs =
            listOf(
                "FFD9E8EF",
                "FF3B6173",
                "FFBFDCD5",
                "FF2C5B53",
                "FFDAD8EE",
                "FF4F4D7C",
                "FFF3EBCF",
                "FF75662F",
                "FFF7DFD2",
                "FF85503A",
                "FFDFE8DA",
                "FF4B5F49",
                "FFF4D9E0",
                "FF83465A",
                "FFD2D8EA",
                "FF454F7A",
                "FFFCE9E7",
                "FF7A2A22",
            )
        for (rgb in expectedRgbs) {
            assertTrue(stylesXml.contains(rgb), "Missing RGB $rgb in xl/styles.xml")
        }

        val sheet1Xml = entries["xl/worksheets/sheet1.xml"]!!
        val mergeRefs =
            Regex("""<mergeCell ref="([^"]+)"""")
                .findAll(sheet1Xml)
                .map { it.groupValues[1] }
                .toList()
        // 周二(列 C) 第1节(row2)单课, 第2节(row3)冲突(不合并), 第3节(row4)单课 -> 没有任何跨冲突格的重叠合并
        assertTrue(mergeRefs.isEmpty(), "Expected no overlapping merges, got $mergeRefs")

        val parsed = XlsxReader.read(bytes).value!![0]
        val conflictText = parsed.text(3, 3) // row 3 (period 2), col 3 (Tue)
        assertTrue(conflictText.contains("数据结构"))
        assertTrue(conflictText.contains("───"))
        assertTrue(conflictText.contains("英语口语"))
        assertFalse(conflictText.contains("王老师"))
    }

    @Test
    fun `IcsWriter writeTimetable emits timetable headers, excludes events and tasks, and adds rescheduled description`() {
        val course =
            Course(
                id = "c1",
                semesterId = fallSem.id,
                name = "操作系统",
                teacher = "陈老师",
                room = "教3-301",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(1, 2),
            )
        val ex =
            CourseException(
                id = "ex1",
                courseId = "c1",
                week = 2,
                type = ExceptionType.RESCHEDULE,
                newWeek = 2,
                newDayOfWeek = 3,
                newStartPeriod = 5,
                newEndPeriod = 6,
                newRoom = "教4-102",
            )
        val ev =
            Event(
                id = "ev1",
                title = "不要导出的日程",
                start = LocalDateTime.of(2026, 9, 8, 10, 0),
                end = LocalDateTime.of(2026, 9, 8, 11, 0),
            )
        val task = Task(id = "t1", title = "不要导出的待办")
        val snap =
            Snapshot(
                semesters = listOf(fallSem),
                courses = listOf(course),
                exceptions = listOf(ex),
                events = listOf(ev),
                tasks = listOf(task),
            )

        val ics =
            IcsWriter.writeTimetable(
                semester = fallSem,
                courses = snap.courses,
                exceptions = snap.exceptions,
                zone = ZoneId.of("Asia/Shanghai"),
                nowMillis = 0L,
            )

        assertTrue(ics.contains("X-WR-CALNAME:${fallSem.name}"))
        assertTrue(ics.contains("X-GOKE-SEMESTER:${fallSem.name}"))
        assertTrue(ics.contains("X-GOKE-WEEK1-MONDAY:2026-09-07"))
        assertTrue(ics.contains("X-GOKE-TOTAL-WEEKS:18"))
        assertFalse(ics.contains("BEGIN:VTODO"))
        assertFalse(ics.contains("不要导出的日程"))
        assertFalse(ics.contains("不要导出的待办"))

        val parsed = IcsParser.parse(ics)
        assertTrue(parsed.ok, parsed.errors.toString())
        val resched = parsed.value!!.events.single { it.uid == "ex1@goke" }
        assertTrue(resched.description.startsWith("第5 - 6节"))
        assertTrue(resched.description.contains("教4-102"))
    }

    @Test
    fun `SharedTimetable JSON round-trip filters deleted records and excludes sensitive fields`() {
        val liveCourse =
            Course(
                id = "c-live",
                semesterId = fallSem.id,
                name = "编译原理",
                teacher = "周老师",
                room = "教5-201",
                dayOfWeek = 5,
                startPeriod = 3,
                endPeriod = 4,
                weeks = setOf(1, 3, 5),
                colorIndex = 3,
                kind = EventKind.EXAM,
                note = "带计算器",
                userId = "secret-user-id",
            )
        val deletedCourse =
            Course(
                id = "c-del",
                semesterId = fallSem.id,
                name = "已删除课程",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(1),
                deletedAt = 999L,
            )
        val liveEx =
            CourseException(
                id = "ex-live",
                courseId = "c-live",
                week = 3,
                type = ExceptionType.CANCEL,
                userId = "secret-user-id",
            )
        val deletedEx =
            CourseException(
                id = "ex-del",
                courseId = "c-live",
                week = 5,
                type = ExceptionType.CANCEL,
                deletedAt = 888L,
            )
        val snap =
            Snapshot(
                semesters = listOf(fallSem.copy(userId = "secret-user-id")),
                courses = listOf(liveCourse, deletedCourse),
                exceptions = listOf(liveEx, deletedEx),
            )

        val payload =
            SharedTimetable.fromSnapshot(
                snap = snap,
                displayName = "小明的秋学期课表",
                updatedAt = 123456789L,
                expiresAt = 987654321L,
                code = "ABCD12",
            )
        val json = payload.toJson()
        assertFalse(json.contains("studentId"))
        assertFalse(json.contains("userId"))
        assertFalse(json.contains("manageToken"))
        assertFalse(json.contains("secret-user-id"))
        assertFalse(json.contains("已删除课程"))

        val decoded = SharedTimetable.fromJson(json)
        assertEquals("goke.timetable.v1", decoded.schema)
        assertEquals("ABCD12", decoded.code)
        assertEquals("小明的秋学期课表", decoded.name)
        assertEquals("2026-09-07", decoded.semester.week1Monday)
        assertEquals(1, decoded.courses.size)
        assertEquals("编译原理", decoded.courses[0].name)
        assertEquals(listOf(1, 3, 5), decoded.courses[0].weeks)
        assertEquals(1, decoded.exceptions.size)
        assertEquals(0, decoded.exceptions[0].courseIndex)
        assertEquals("CANCEL", decoded.exceptions[0].type)
    }

    @Test
    fun `ShareExpiry computes fall, spring, 210d cap, and expired semester clamping`() {
        // 秋季学期: 2026-09-07 + 18w = 2027-01-11, +6w = 2027-02-22
        assertEquals(LocalDate.of(2027, 1, 11), ShareExpiry.semesterEnd(fallSem))
        assertEquals(LocalDate.of(2027, 2, 22), ShareExpiry.nextSemesterStart(fallSem))
        // 若 today = 2026-09-01, nextSemesterStart (174d) < today + 210d -> 选 2027-02-22
        assertEquals(
            LocalDate.of(2027, 2, 22),
            ShareExpiry.maxDate(fallSem, today = LocalDate.of(2026, 9, 1)),
        )
        // 若 today = 2026-07-01, today + 210d = 2027-01-27 < 2027-02-22 -> 被 210d 截断
        assertEquals(
            LocalDate.of(2027, 1, 27),
            ShareExpiry.maxDate(fallSem, today = LocalDate.of(2026, 7, 1)),
        )

        // 春季学期: 2027-03-01 + 18w = 2027-07-05, +8w = 2027-08-30
        assertEquals(LocalDate.of(2027, 7, 5), ShareExpiry.semesterEnd(springSem))
        assertEquals(LocalDate.of(2027, 8, 30), ShareExpiry.nextSemesterStart(springSem))
        assertEquals(
            LocalDate.of(2027, 8, 30),
            ShareExpiry.maxDate(springSem, today = LocalDate.of(2027, 3, 1)),
        )

        // 学期已结束很久: nextSemesterStart <= today -> 至少 clamped 到 today + 1d
        val todayAfter = LocalDate.of(2027, 6, 1)
        assertEquals(
            todayAfter.plusDays(1),
            ShareExpiry.maxDate(fallSem, today = todayAfter),
        )
        val presetsAfter = ShareExpiry.presets(fallSem, today = todayAfter)
        assertEquals(1, presetsAfter.size)
        assertEquals(todayAfter.plusDays(1), presetsAfter[0].date)

        // 正常学期内 presets 返回去重且递增的预设
        val presets = ShareExpiry.presets(fallSem, today = LocalDate.of(2026, 9, 1))
        assertEquals(listOf("1d", "7d", "30d", "semesterEnd", "nextSemesterStart"), presets.map { it.key })
        assertTrue(presets.all { it.date >= LocalDate.of(2026, 9, 2) && it.date <= LocalDate.of(2027, 2, 22) })
    }
}
