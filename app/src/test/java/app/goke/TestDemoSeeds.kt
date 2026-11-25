package app.goke

import app.goke.GokeApp
import app.goke.core.jxfw.ClassroomBuilding
import app.goke.core.jxfw.ClassroomOccupancy
import app.goke.core.jxfw.ClassroomRoom
import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.OccupancyType
import app.goke.core.jxfw.TzcsItem
import app.goke.core.jxfw.TzcsParse
import app.goke.core.jxfw.TzcsYearReport
import app.goke.core.jxfw.XkConflict
import app.goke.core.jxfw.XkConflictSource
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.jxfw.XkCreditWallet
import app.goke.core.jxfw.XkScheduleSummary
import app.goke.core.jxfw.XkWindowStatus
import app.goke.core.model.Attachment
import app.goke.data.Attachments
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.jxfw.JxfwGradesState
import app.goke.jxfw.JxfwManager
import app.goke.jxfw.JxfwState
import app.goke.jxfw.TzcsManager
import app.goke.jxfw.TzcsState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

internal fun JxfwManager.seedClassroomDemoForTest(weekStart: LocalDate) {
    val now = System.currentTimeMillis()
    val state =
        JxfwState(
            username = "3123004200",
            term = "2025-2026-2",
            lastFullAt = now,
            lastResult = "已同步",
        )
    val buildings =
        listOf(
            ClassroomBuilding(code = "0005", number = "05", name = "教学三号楼(城)", campusCode = "1", campusName = "大学城校区"),
            ClassroomBuilding(code = "0012", number = "12", name = "实验二号楼(城)", campusCode = "1", campusName = "大学城校区"),
            ClassroomBuilding(code = "0008", number = "08", name = "工学一号馆", campusCode = "1", campusName = "大学城校区"),
        )
    val rooms =
        listOf(
            ClassroomRoom(code = "011030101", name = "教3-101", capacity = 223),
            ClassroomRoom(code = "011030102", name = "教3-102", capacity = 180),
            ClassroomRoom(code = "011030201", name = "教3-201", capacity = 160),
            ClassroomRoom(code = "011030202", name = "教3-202", capacity = 120),
            ClassroomRoom(code = "011030204", name = "教3-204", capacity = 160),
        )

    fun occ(
        id: String,
        roomCode: String,
        roomName: String,
        dayOffset: Int,
        periods: List<Int>,
        courseName: String,
        teacher: String,
        type: OccupancyType,
        note: String = "",
    ) = ClassroomOccupancy(
        id = id,
        roomCode = roomCode,
        roomName = roomName,
        periods = periods,
        periodCode = periods.joinToString("") { it.toString().padStart(2, '0') },
        courseName = courseName,
        teacher = teacher,
        className = "计算机23(1)-(3)",
        note = note,
        dayOfWeek = dayOffset + 1,
        week = 10,
        type = type,
        count = 110,
    )

    val weekRecords =
        listOf(
            0 to occ("o1", "011030101", "教3-101", 0, listOf(1, 2), "高等数学A(二)", "陈老师", OccupancyType.COURSE),
            0 to occ("o2", "011030101", "教3-101", 0, listOf(6, 7), "数据结构与算法", "林老师", OccupancyType.COURSE),
            0 to occ("o3", "011030102", "教3-102", 0, listOf(3, 4), "大学英语(四)", "张老师", OccupancyType.COURSE),
            1 to occ("o4", "011030101", "教3-101", 1, listOf(3, 4), "操作系统", "黄老师", OccupancyType.COURSE),
            1 to occ("o5", "011030101", "教3-101", 1, listOf(10, 11, 12), "ACM 算法竞赛集训", "李同学", OccupancyType.BORROWED, "社团借用"),
            1 to occ("o6", "011030201", "教3-201", 1, listOf(1, 2), "线性代数", "周老师", OccupancyType.COURSE),
            2 to occ("o7", "011030101", "教3-101", 2, listOf(1, 2), "计算机网络", "王老师", OccupancyType.COURSE),
            2 to occ("o8", "011030202", "教3-202", 2, listOf(6, 7), "概率论与数理统计", "吴老师", OccupancyType.COURSE),
            3 to occ("o9", "011030101", "教3-101", 3, listOf(8, 9), "软件工程", "赵老师", OccupancyType.COURSE),
            4 to occ("o10", "011030101", "教3-101", 4, listOf(3, 4), "高等数学期中考试", "教务处", OccupancyType.EXAM, "期中测验"),
            4 to occ("o11", "011030204", "教3-204", 4, listOf(6, 7), "创新创业讲座", "学工办", OccupancyType.BORROWED, "院系活动"),
            5 to occ("o12", "011030101", "教3-101", 5, listOf(6, 7), "程序设计基础补考", "教务处", OccupancyType.EXAM),
            6 to occ("o13", "011030101", "教3-101", 6, listOf(10, 11), "辩论队例会", "校辩论队", OccupancyType.BORROWED, "学生活动"),
        )
    val occByDay =
        (0..6).associate { offset ->
            weekStart.plusDays(offset.toLong()) to weekRecords.filter { it.first == offset }.map { it.second }
        }
    val nextWeekOcc = (0..6).associate { offset -> weekStart.plusWeeks(1).plusDays(offset.toLong()) to emptyList<ClassroomOccupancy>() }
    val grades =
        JxfwGradesState(
            fetchedAt = now,
            grades =
                listOf(
                    JxfwGrade("2025-2026-1", "COMP2010", "数据结构与算法", 4.0, "93", 4.3, "必修", "初修", "正常考试"),
                    JxfwGrade("2025-2026-1", "COMP2022", "计算机组成原理", 3.5, "89", 3.9, "必修", "初修", "正常考试"),
                    JxfwGrade("2025-2026-1", "COMP3015", "移动应用开发", 2.0, "95", 4.5, "专业选修", "初修", "正常考试"),
                    JxfwGrade("2025-2026-1", "GENE1088", "岭南文化与建筑赏析", 1.5, "91", 4.1, "通识选修", "初修", "正常考试"),
                    JxfwGrade("2024-2025-2", "MATH1002", "高等数学A(二)", 5.0, "86", 3.6, "必修", "初修", "正常考试"),
                    JxfwGrade("2024-2025-2", "COMP1008", "面向对象程序设计", 3.0, "92", 4.2, "必修", "初修", "正常考试"),
                ),
        )
    seedStateForTest(
        state = state,
        buildings = buildings,
        roomsByBuilding = mapOf("0005" to rooms),
        weekOccupancy =
            mapOf(
                ("0005" to weekStart) to occByDay,
                ("0005" to weekStart.plusWeeks(1)) to nextWeekOcc,
            ),
        gradesState = grades,
    )
}

internal fun TzcsManager.seedPhysicalTestDemoForTest() {
    val now = System.currentTimeMillis()
    val reports =
        listOf(
            TzcsYearReport(
                year = 2025,
                studentName = "林薄荷",
                studentNo = "3123004200",
                totalScore = "86.4",
                totalGrade = "良好",
                items =
                    listOf(
                        TzcsItem("身高 · 体重（BMI）", "174.5 cm / 65.0 kg", "100", "正常", 15.0),
                        TzcsItem("肺活量", "4450 ml", "92", "优秀", 13.8),
                        TzcsItem("50米跑", "6.9 秒", "90", "优秀", 18.0),
                        TzcsItem("坐位体前屈", "19.2 cm", "85", "良好", 8.5),
                        TzcsItem("立定跳远", "252 cm", "84", "良好", 8.4),
                        TzcsItem("引体向上", "13 次", "80", "良好", 8.0),
                        TzcsItem("1000米跑", "3.42 分.秒", "80", "良好", 16.0),
                    ),
                historyYears = listOf(2025, 2024),
            ),
            TzcsYearReport(
                year = 2024,
                studentName = "林薄荷",
                studentNo = "3123004200",
                totalScore = "82.1",
                totalGrade = "良好",
                items =
                    listOf(
                        TzcsItem("身高 · 体重（BMI）", "174.0 cm / 64.5 kg", "100", "正常", 15.0),
                        TzcsItem("肺活量", "4200 ml", "86", "良好", 12.9),
                        TzcsItem("50米跑", "7.1 秒", "84", "良好", 16.8),
                        TzcsItem("坐位体前屈", "17.8 cm", "80", "良好", 8.0),
                        TzcsItem("立定跳远", "245 cm", "80", "良好", 8.0),
                        TzcsItem("引体向上", "11 次", "72", "及格", 7.2),
                        TzcsItem("1000米跑", "3.50 分.秒", "75", "及格", 15.0),
                    ),
                historyYears = listOf(2025, 2024),
            ),
        )
    seedStateForTest(
        TzcsState(
            username = "3123004200",
            hasSavedPassword = true,
            reports = TzcsParse.filterApplicableItems(reports),
            fetchedAt = now,
        ),
    )
}

internal fun demoCourseSelectionStateForTest(): JxfwCourseSelectionState =
    JxfwCourseSelectionState(
        window =
            XkWindowStatus(
                xkjd = "1",
                xnxqdm = "202601",
                isCreditWallet = true,
                canDrop = true,
                maxCourses = 3,
                headerNotice = "当前开放通识教育选修课选课，支持实时检测个人课表冲突",
            ),
        wallet = XkCreditWallet(totalCredits = 10.0, usedCredits = 4.0),
        available =
            listOf(
                XkCourseItem(
                    kcrwdm = "demo-xk-1",
                    kcdm = "GENE2041",
                    kcptdm = "pt1",
                    name = "岭南建筑与城市文化",
                    credit = 2.0,
                    teacher = "陈教授",
                    capacity = 120,
                    enrolledCount = 96,
                    category = "通识教育选修",
                    classGroup = "通识26-01班",
                    campusHint = "大学城",
                    scheduleSummary =
                        listOf(
                            XkScheduleSummary(
                                dayOfWeek = 4,
                                periods = listOf(6, 7),
                                weeks = (1..16).toSet(),
                                room = "教3-101",
                                campusHint = "大学城",
                            ),
                        ),
                ),
                XkCourseItem(
                    kcrwdm = "demo-xk-2",
                    kcdm = "COMP3092",
                    kcptdm = "pt1",
                    name = "人工智能导论",
                    credit = 2.0,
                    teacher = "张老师",
                    capacity = 80,
                    enrolledCount = 74,
                    category = "通识教育选修",
                    classGroup = "通识26-03班",
                    campusHint = "大学城",
                    scheduleSummary =
                        listOf(
                            XkScheduleSummary(
                                dayOfWeek = 1,
                                periods = listOf(1, 2),
                                weeks = (1..14).toSet(),
                                room = "教3-204",
                                campusHint = "大学城",
                            ),
                        ),
                    conflicts =
                        listOf(
                            XkConflict(
                                courseName = "概率论与数理统计",
                                dayOfWeek = 1,
                                periods = listOf(1, 2),
                                weeks = (1..14).toSet(),
                                source = XkConflictSource.JXFW,
                            ),
                        ),
                ),
                XkCourseItem(
                    kcrwdm = "demo-xk-3",
                    kcdm = "ART1015",
                    kcptdm = "pt1",
                    name = "西方古典音乐鉴赏",
                    credit = 1.5,
                    teacher = "李老师",
                    capacity = 60,
                    enrolledCount = 60,
                    category = "艺术素养",
                    classGroup = "美育26-02班",
                    campusHint = "大学城",
                    scheduleSummary =
                        listOf(
                            XkScheduleSummary(
                                dayOfWeek = 2,
                                periods = listOf(10, 11),
                                weeks = (3..14).toSet(),
                                room = "实验楼B102",
                                campusHint = "大学城",
                            ),
                        ),
                ),
            ),
        availableTotal = 3,
        enrolled =
            listOf(
                XkCourseItem(
                    kcrwdm = "demo-xk-enrolled",
                    kcdm = "HIS1002",
                    name = "中国近现代史纲要",
                    credit = 2.0,
                    teacher = "王老师",
                    enrolled = true,
                ),
            ),
    )

internal fun createDemoAttachmentsForTest(app: GokeApp): List<Attachment> {
    val dir = Attachments.dir(app).apply { mkdirs() }

    fun writeDiagram(
        fileName: String,
        bg: Color,
        card: Color,
        accent: Color,
        accent2: Color,
        drawWave: Boolean,
    ): String {
        val w = 480
        val h = 360
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = bg
        g.fillRect(0, 0, w, h)
        g.color = card
        g.fill(RoundRectangle2D.Double(28.0, 28.0, w - 56.0, h - 56.0, 28.0, 28.0))
        g.color = Color(accent.red, accent.green, accent.blue, 36)
        g.stroke = BasicStroke(2f)
        for (i in 1..4) {
            val y = 68 + i * 52
            g.drawLine(52, y, w - 52, y)
        }
        if (drawWave) {
            g.color = accent
            g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            var prevX = 60
            var prevY = 200
            for (step in 1..24) {
                val x = 60 + step * 15
                val y = (185.0 - kotlin.math.sin(step * 0.45) * 68.0).toInt()
                g.drawLine(prevX, prevY, x, y)
                prevX = x
                prevY = y
            }
            g.color = accent2
            g.fill(RoundRectangle2D.Double(60.0, 52.0, 136.0, 30.0, 15.0, 15.0))
        } else {
            g.color = accent
            g.fill(RoundRectangle2D.Double(64.0, 210.0, 54.0, 82.0, 14.0, 14.0))
            g.color = accent2
            g.fill(RoundRectangle2D.Double(146.0, 150.0, 54.0, 142.0, 14.0, 14.0))
            g.color = accent
            g.fill(RoundRectangle2D.Double(228.0, 108.0, 54.0, 184.0, 14.0, 14.0))
            g.color = accent2
            g.fill(RoundRectangle2D.Double(310.0, 174.0, 54.0, 118.0, 14.0, 14.0))
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, fileName))
        return fileName
    }

    val f1 =
        writeDiagram(
            "demo_shot_wave.png",
            bg = Color(225, 240, 235),
            card = Color(248, 252, 250),
            accent = Color(46, 119, 108),
            accent2 = Color(198, 232, 222),
            drawWave = true,
        )
    val f2 =
        writeDiagram(
            "demo_shot_chart.png",
            bg = Color(228, 236, 244),
            card = Color(249, 251, 254),
            accent = Color(58, 95, 138),
            accent2 = Color(142, 186, 224),
            drawWave = false,
        )
    val pdf = File(dir, "demo_shot_lab.pdf").apply { writeText("PDF demo") }
    return listOf(
        Attachment("示波器实验波形.png", "image/png", f1),
        Attachment("数据拟合对比.png", "image/png", f2),
        Attachment("实验报告模板.pdf", "application/pdf", pdf.name),
    )
}
