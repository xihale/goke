package app.mintcal.data

import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Attachment
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Record
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.util.Ids
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 演示数据：一次提交写入的变更集，用真实内容量看界面在各状态下的表现。
 * 记录 id 固定为 `demo-*`，重复填入只覆盖同一批记录，不会叠成两份；整批在操作记录里一步撤销。
 * 日期全部相对「今天」生成，任何时候填入都能同时看到逾期 / 今天 / 明天 / 本周 / 之后 / 收集箱。
 */
object DemoData {
    /** [replace] 为 true 时，先把现有记录全部软删除（进回收站、可撤销），只留演示数据。 */
    fun build(
        now: LocalDateTime,
        semesterId: String,
        replace: Boolean = false,
        existing: List<Record> = emptyList(),
    ): ChangeSet {
        val today = now.toLocalDate()
        val zone = ZoneId.systemDefault()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()

        fun id(k: String) = "demo-$k"

        fun at(
            days: Long,
            time: LocalTime = LocalTime.of(9, 0),
        ): LocalDateTime = LocalDateTime.of(today.plusDays(days), time)

        fun atMs(
            days: Long,
            time: LocalTime = LocalTime.of(9, 0),
        ): Long = at(days, time).atZone(zone).toInstant().toEpochMilli()

        fun everyWeek(vararg d: Int) = d.toSet()

        fun weeks(
            a: Int,
            b: Int,
        ) = (a..b).toSet()

        fun everyOtherWeek(
            a: Int,
            b: Int,
        ) = (a..b).filter { it % 2 == 0 }.toSet()

        val records = mutableListOf<Record>()

        // ── 清单 ──
        records += TaskList(id("list-study"), "学习", 1, 0)
        records += TaskList(id("list-life"), "生活", 2, 1)
        records += TaskList(id("list-club"), "社团", 3, 2)

        // ── 课程：周一到周六铺满，含单双周、短学期、晚间课 ──
        fun course(
            k: String,
            name: String,
            teacher: String,
            room: String,
            dow: Int,
            start: Int,
            end: Int,
            wk: Set<Int>,
            color: Int,
            note: String = "",
        ) = Course(
            id = id(k),
            semesterId = semesterId,
            name = name,
            teacher = teacher,
            room = room,
            dayOfWeek = dow,
            startPeriod = start,
            endPeriod = end,
            weeks = wk,
            colorIndex = color,
            remindMinutes = 10,
            note = note,
        )
        records += course("c-gaoshu", "高等数学 A", "张明远", "教1-201", 1, 1, 2, weeks(1, 16), 0)
        records += course("c-english", "大学英语（三）", "陈雪", "外语楼302", 1, 3, 4, weeks(1, 12), 1)
        records += course("c-ds", "数据结构", "李建国", "计算机楼401", 2, 3, 4, weeks(1, 16), 2, "带计算器与草稿纸")
        records += course("c-dslab", "数据结构实验", "李建国", "计算机楼402", 2, 5, 6, everyOtherWeek(2, 16), 2)
        records += course("c-physics", "大学物理", "王芳", "理科楼105", 3, 1, 2, weeks(1, 16), 3)
        records += course("c-pe", "体育（篮球）", "赵鹏", "东区篮球场", 3, 7, 8, weeks(1, 16), 4)
        records += course("c-history", "中国近现代史纲要", "刘洋", "人文楼201", 4, 5, 6, weeks(1, 16), 5)
        records += course("c-photo", "摄影基础", "孙唯", "艺术楼107", 4, 9, 10, weeks(1, 8), 3, "选修，第 8 周结课")
        records += course("c-os", "操作系统", "周敏", "计算机楼305", 5, 1, 2, weeks(1, 16), 0)
        records += course("c-prob", "概率论与数理统计", "何静", "教2-104", 5, 3, 4, weeks(1, 16), 1)
        records += course("c-plab", "大学物理实验", "王芳", "物理实验中心", 6, 3, 4, everyOtherWeek(3, 13), 4)

        // ── 课程例外：一次停课、一次调课到大教室 ──
        records += CourseException(id("e-ds-cancel"), id("c-ds"), 9, ExceptionType.CANCEL)
        records +=
            CourseException(
                id = id("e-physics-move"),
                courseId = id("c-physics"),
                week = 9,
                type = ExceptionType.RESCHEDULE,
                newWeek = 9,
                newDayOfWeek = 5,
                newStartPeriod = 9,
                newEndPeriod = 10,
                newRoom = "理科楼305",
            )

        fun event(
            k: String,
            title: String,
            start: LocalDateTime,
            end: LocalDateTime,
            allDay: Boolean = false,
            location: String = "",
            note: String = "",
            color: Int = 4,
            category: String = "",
            kind: EventKind = EventKind.NORMAL,
            recurrence: Recurrence? = null,
            remind: Int? = null,
        ) = Event(id("ev-$k"), title, start, end, allDay, location, note, color, category, remind, recurrence, kind)

        // ── 日程：考试、倒计时、例会、全天事项 ──
        records +=
            event(
                "exam-math",
                "期末考试 · 高等数学 A",
                at(18, LocalTime.of(9, 0)),
                at(18, LocalTime.of(11, 0)),
                location = "教1-201",
                note = "闭卷，带学生证",
                color = 0,
                category = "考试",
                kind = EventKind.EXAM,
                remind = 60,
            )
        records +=
            event(
                "quiz-prob",
                "概率论小测",
                at(6, LocalTime.of(10, 0)),
                at(6, LocalTime.of(11, 0)),
                location = "教2-104",
                color = 1,
                category = "考试",
                kind = EventKind.EXAM,
                remind = 30,
            )
        records +=
            event(
                "cet4",
                "英语四级考试",
                at(30, LocalTime.of(9, 0)),
                at(30, LocalTime.of(11, 20)),
                location = "外语楼",
                color = 2,
                category = "考试",
                kind = EventKind.COUNTDOWN,
            )
        records +=
            event(
                "club-meet",
                "摄影社例会",
                at(2, LocalTime.of(19, 0)),
                at(2, LocalTime.of(21, 0)),
                location = "艺术楼 210",
                color = 3,
                category = "社团",
                recurrence = Recurrence(Freq.WEEKLY, byDays = everyWeek(4)),
                remind = 15,
            )
        records +=
            event(
                "birthday",
                "室友生日",
                at(4, LocalTime.of(0, 0)),
                at(5, LocalTime.of(0, 0)),
                allDay = true,
                color = 5,
                category = "生活",
            )
        records +=
            event(
                "hospital",
                "校医院复诊",
                at(2, LocalTime.of(15, 30)),
                at(2, LocalTime.of(16, 10)),
                location = "校医院三楼",
                color = 4,
                category = "生活",
                remind = 60,
            )
        records +=
            event(
                "lecture",
                "讲座：如何准备保研",
                at(1, LocalTime.of(19, 0)),
                at(1, LocalTime.of(20, 30)),
                location = "图书馆报告厅",
                note = "带简历，现场改",
                color = 2,
                category = "讲座",
                remind = 30,
            )
        records +=
            event(
                "return-book",
                "图书馆还书",
                at(3, LocalTime.of(0, 0)),
                at(4, LocalTime.of(0, 0)),
                allDay = true,
                color = 1,
                remind = 120,
            )

        fun task(
            k: String,
            title: String,
            note: String = "",
            list: String? = null,
            tags: List<String> = emptyList(),
            priority: Int = 0,
            due: LocalDateTime? = null,
            dueHasTime: Boolean = due != null,
            startDate: LocalDate? = null,
            done: Boolean = false,
            doneAt: Long? = null,
            dropped: Boolean = false,
            parentId: String? = null,
            repeat: Recurrence? = null,
            remind: Int? = null,
            courseId: String? = null,
            beforeNextClass: Boolean = false,
            blockStart: LocalDateTime? = null,
            blockMinutes: Int? = null,
            estimate: Int? = null,
            source: Source = Source.MANUAL,
            extra: Map<String, String> = emptyMap(),
            attachments: List<Attachment> = emptyList(),
        ) = Task(
            id = id(k),
            title = title,
            note = note,
            listId = list?.let { id(it) },
            tags = tags,
            priority = priority,
            startDate = startDate,
            due = due,
            dueHasTime = dueHasTime,
            estimateMinutes = estimate,
            done = done,
            doneAt = doneAt,
            dropped = dropped,
            parentId = parentId?.let { id(it) },
            repeat = repeat,
            remindMinutes = remind,
            courseId = courseId?.let { id(it) },
            dueBeforeNextClass = beforeNextClass,
            blockStart = blockStart,
            blockMinutes = blockMinutes,
            sortOrder = records.size,
            source = source,
            extra = extra,
            attachments = attachments,
        )

        // ── 逾期 ──
        records +=
            task(
                "t-math-hw",
                "交高数作业（第 5 章）",
                list = "list-study",
                tags = listOf("作业"),
                priority = 3,
                due = at(-1, LocalTime.of(23, 59)),
                note = "习题 5-3 第 7、9、12 题，写在草稿本上拍照上传",
                remind = 30,
                estimate = 40,
            )
        records += task("t-return", "还图书馆借的书", list = "list-life", priority = 2, due = LocalDateTime.of(today.minusDays(3), LocalTime.of(12, 0)), dueHasTime = false)
        records += task("t-email", "给导师发周报邮件", list = "list-study", priority = 2, due = at(-2, LocalTime.of(9, 0)), dueHasTime = false)

        // ── 今天 ──
        records +=
            task(
                "t-ds-report",
                "数据结构实验报告",
                list = "list-study",
                tags = listOf("作业", "实验"),
                priority = 3,
                // 今天到期的固定排在夜里 23:30：任何时刻填入都还在「今天」区，不会一上来就掉进逾期
                due = at(0, LocalTime.of(23, 30)),
                note = "至少含一张性能对比图，结论写清时间复杂度",
                remind = 60,
                estimate = 120,
            )
        records += task("t-ds-1", "整理实验数据", parentId = "t-ds-report", done = true, doneAt = atMs(0, LocalTime.of(15, 10)), list = "list-study")
        records += task("t-ds-2", "画性能对比图", parentId = "t-ds-report", list = "list-study")
        records += task("t-ds-3", "写实验结论", parentId = "t-ds-report", list = "list-study")
        records += task("t-battery", "买 3 号电池", list = "list-life", tags = listOf("生活"), priority = 1, due = LocalDateTime.of(today, LocalTime.of(20, 0)), dueHasTime = false)
        records +=
            task(
                "t-parcel",
                "取快递",
                list = "list-life",
                priority = 2,
                due = at(0, LocalTime.of(23, 0)),
                source = Source.PARCEL,
                extra = mapOf("code" to "8-2-3045", "place" to "菜鸟驿站（南门）"),
                remind = 30,
            )
        records +=
            task(
                "t-review",
                "复习线性代数",
                list = "list-study",
                priority = 2,
                blockStart = at(0, LocalTime.of(19, 0)),
                blockMinutes = 90,
                estimate = 90,
            )

        // ── 明天 / 本周 / 之后 ──
        records += task("t-essay", "英语作文：Technology and Us", list = "list-study", priority = 2, due = at(1, LocalTime.of(18, 0)), estimate = 45)
        records += task("t-prob-hw", "概率论习题 4.1–4.6", list = "list-study", tags = listOf("作业"), priority = 2, due = at(2, LocalTime.of(10, 0)))
        records += task("t-safety", "实验室安全考核", list = "list-study", priority = 1, due = LocalDateTime.of(today.plusDays(3), LocalTime.of(12, 0)), dueHasTime = false)
        records +=
            task(
                "t-os-project",
                "操作系统课程设计：实现一个简易调度器",
                list = "list-study",
                tags = listOf("课程设计", "重要"),
                priority = 3,
                due = at(5, LocalTime.of(23, 0)),
                note = "优先做时间片轮转，再补优先级调度",
                estimate = 600,
            )
        records += task("t-ticket", "抢寒假回家的火车票", list = "list-life", priority = 3, due = at(20, LocalTime.of(8, 0)), remind = 15)
        records += task("t-tidy", "整理宿舍桌面", list = "list-life", priority = 0, due = LocalDateTime.of(today.plusDays(1), LocalTime.of(20, 0)), dueHasTime = false)
        records += task("t-weekly", "每周三交周报", list = "list-club", priority = 2, due = at(3, LocalTime.of(21, 0)), repeat = Recurrence(Freq.WEEKLY, byDays = everyWeek(3)))

        // ── 收集箱（无日期） ──
        records += task("t-book", "读《设计心理学》第 3 章", list = "list-life", priority = 1, note = "借来的书 3 周后到期")
        records += task("t-mom", "给妈妈打电话", priority = 2)
        records += task("t-long", "把上学期所有课程笔记扫描成 PDF 并按章节命名归档到网盘，顺便清理重复文件——这条用来检查长标题在两处的换行与截断")

        // ── 已完成 / 已放弃 ──
        records += task("t-run", "晨跑 3 公里", list = "list-life", done = true, doneAt = atMs(0, LocalTime.of(7, 20)), extra = mapOf("place" to "东区操场"))
        records += task("t-checkin", "体育课签到", list = "list-study", done = true, doneAt = atMs(-3, LocalTime.of(15, 45)))
        records += task("t-photo-club", "报名摄影社", list = "list-club", done = true, dropped = true, doneAt = atMs(-6, LocalTime.of(21, 0)), note = "时间冲突，这学期不去")

        // ── 关联课程：下节课前 / 预习 ──
        records +=
            task(
                "t-rbtree",
                "预习红黑树（第 9 周课前）",
                list = "list-study",
                courseId = "c-ds",
                beforeNextClass = true,
                priority = 2,
            )

        records += task("t-photo-done", "摄影作业：校园光影 5 张", list = "list-study", priority = 1, done = true, doneAt = atMs(-2, LocalTime.of(19, 30)), tags = listOf("作业"))

        // 现有数据先删后建：同一个变更集里先 Remove 再 Add，结果只有演示数据，
        // 整批在操作记录里一步撤销就全回来了。
        val cleared =
            if (!replace) {
                emptyList()
            } else {
                existing.filter { it !is app.mintcal.core.model.Semester }.map { Change.Remove(it) }
            }
        return ChangeSet(
            id = Ids.next(),
            description = if (replace) "替换为演示数据" else "填入演示数据",
            source = Source.IMPORT,
            createdAt = nowMs,
            changes = cleared + records.map { Change.Add(it) },
        )
    }
}
