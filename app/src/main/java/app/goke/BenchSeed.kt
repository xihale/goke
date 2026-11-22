package app.goke

import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.Record
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.util.Ids
import app.goke.data.Repository
import java.time.LocalDate

/**
 * 仅 benchmark 变体（`BuildConfig.BENCHMARK == true`）调用：首启塞一份可滚动的真实感数据，
 * 让「标签切换 / 列表滚动 / 勾选」这类基准跑在有内容的界面上，而不是空态。
 *
 * release 与 debug 里 `BENCHMARK` 是编译期常量 false，分支被折叠后 R8 会把这个对象整个删掉。
 */
object BenchSeed {
    suspend fun seed(repo: Repository) {
        val snap = repo.snapshot.value
        if (snap.tasks.isNotEmpty() || snap.courses.isNotEmpty()) return
        val sem = snap.currentSemester ?: return
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val weeks = (1..sem.totalWeeks).toSet()

        val study = TaskList(Ids.next(), "学习", 1, 0)
        val life = TaskList(Ids.next(), "生活", 4, 1)

        val courses =
            listOf(
                Course(Ids.next(), sem.id, "高等数学", "张伟", "教3-204", 1, 1, 2, weeks, 0),
                Course(Ids.next(), sem.id, "大学英语", "李娜", "教1-101", 1, 3, 4, weeks, 1),
                Course(Ids.next(), sem.id, "数据结构", "王强", "实验楼A-305", 2, 1, 2, weeks, 2),
                Course(Ids.next(), sem.id, "大学物理", "陈静", "教2-208", 3, 5, 6, weeks, 3),
                Course(Ids.next(), sem.id, "体育（篮球）", "刘洋", "东区球场", 4, 3, 4, weeks, 4),
                Course(Ids.next(), sem.id, "程序设计实验", "赵敏", "机房B-402", 5, 7, 8, weeks, 5),
            )

        fun task(
            title: String,
            list: TaskList? = study,
            priority: Int = 0,
            due: java.time.LocalDateTime? = null,
            dueHasTime: Boolean = false,
            blockStart: java.time.LocalDateTime? = null,
            blockMinutes: Int? = null,
            tags: List<String> = emptyList(),
            done: Boolean = false,
            parentId: String? = null,
            note: String = "",
        ) = Task(
            Ids.next(),
            title,
            note = note,
            listId = list?.id,
            tags = tags,
            priority = priority,
            due = due,
            dueHasTime = dueHasTime,
            blockStart = blockStart,
            blockMinutes = blockMinutes,
            done = done,
            doneAt = if (done) now else null,
            parentId = parentId,
            updatedAt = now,
        )

        val homework = task("交高数作业", priority = 3, due = today.atTime(18, 0), dueHasTime = true, tags = listOf("作业"))
        val report = task("写物理实验报告", priority = 2, due = today.plusDays(1).atStartOfDay(), tags = listOf("实验"))
        val records =
            buildList<Record> {
                add(study)
                add(life)
                addAll(courses)
                // 逾期区
                add(task("补交上周的英语作文", priority = 3, due = today.minusDays(1).atTime(12, 0), dueHasTime = true))
                add(task("归还图书馆的书", list = life, due = today.minusDays(2).atTime(9, 0), dueHasTime = true))
                add(task("整理上月的实验数据", due = today.minusDays(3).atStartOfDay()))
                // 今天：带截止时刻、带时间块、带子任务
                add(homework)
                add(report)
                add(task("背 60 个单词", due = today.atTime(22, 0), dueHasTime = true, tags = listOf("英语")))
                add(task("复习数据结构第三章", priority = 2, blockStart = today.atTime(19, 0), blockMinutes = 90))
                add(task("小组会议：课程设计分工", priority = 1, blockStart = today.atTime(21, 0), blockMinutes = 60, tags = listOf("课程设计")))
                add(task("取快递", list = life, due = today.atTime(20, 30), dueHasTime = true))
                add(task("跑步 3 公里", list = life))
                add(task("给家里打电话", list = life, done = true))
                add(task("预习明天的课", done = true))
                add(task("交高数作业", parentId = homework.id, done = true))
                add(task("检查第 3 题", parentId = homework.id))
                add(task("上传 PDF", parentId = homework.id))
                add(task("收集组员分工", parentId = report.id))
                // 明天
                add(task("准备课堂展示", priority = 2, due = today.plusDays(1).atTime(10, 0), dueHasTime = true))
                add(task("打印实验报告", list = life, due = today.plusDays(1).atTime(8, 0), dueHasTime = true))
                add(task("复习线性代数", due = today.plusDays(1).atStartOfDay()))
                add(task("买实验耗材", list = life, due = today.plusDays(1).atStartOfDay()))
                // 本周
                add(task("完成课程设计初稿", priority = 3, due = today.plusDays(3).atStartOfDay(), tags = listOf("课程设计")))
                add(task("整理笔记：数据结构", due = today.plusDays(4).atStartOfDay()))
                add(task("社团例会", blockStart = today.plusDays(2).atTime(19, 0), blockMinutes = 90))
                add(task("预约体检", list = life, due = today.plusDays(5).atStartOfDay()))
                // 之后 / 收集箱
                add(task("准备英语六级", due = today.plusDays(12).atStartOfDay()))
                add(task("期末复习计划", due = today.plusDays(20).atStartOfDay()))
                add(task("换宿舍申请", list = life, due = today.plusDays(25).atStartOfDay()))
                add(task("看一部电影", list = life))
                add(task("整理云盘照片", list = life))
                add(task("读完《代码整洁之道》", note = "每天 20 页"))
            }
        repo.commit(Edits.saveAll(snap, records, "bench-seed", now))
    }
}
