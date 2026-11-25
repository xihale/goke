package app.goke.core.jxfw

import app.goke.core.change.ChangeSet
import app.goke.core.model.CourseNotes

/** 一次同步的结果：要落库的变更集，加上给用户看的统计。 */
data class JxfwPlan(
    val changeSet: ChangeSet,
    val courseCount: Int = 0,
    val examCount: Int = 0,
    val exceptionCount: Int = 0,
    val extraCount: Int = 0,
    val skippedReason: String? = null,
) {
    val isNoop: Boolean get() = changeSet.isEmpty
}

/** 通用课程考试识别规则（本地编辑与同步共用，不含任何协议细节）。 */
object JxfwSync {
    const val COURSE_PREFIX = "jxfw-course/"
    const val EXCEP_PREFIX = "jxfw-excep/"
    const val EXAM_PREFIX = "jxfw-exam/"
    const val COURSE_EXAM_PREFIX = "jxfw-course-exam/"

    private val EXAM_REMARK_INCLUDE_RE =
        Regex(
            """考试|测验|小测|机考|机试|笔试|口试|补考|重考|缓考|随堂考|结课考|实验考|(?:期中|期末|随堂|结课)(?:测试|考核)|实验考核|\b(?:midterm|final\s+exam|quiz)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val EXAM_REMARK_EXCLUDE_RE =
        Regex("""复习|答疑|辅导|讲评|讲解|动员|不考|免考|无考|取消""")

    /** 判断课程备注是否代表考试安排（如期中考试、随堂测验等），同时排除考前复习/答疑/讲评及软件测试等课程主题。 */
    fun isExamRemark(remark: String): Boolean {
        val text = remark.trim()
        if (text.isEmpty()) return false
        if (!EXAM_REMARK_INCLUDE_RE.containsMatchIn(text)) return false
        return !EXAM_REMARK_EXCLUDE_RE.containsMatchIn(text)
    }

    /** 根据课程名称或备注/详情判断是否应自动归为考试类型。 */
    fun shouldClassifyCourseAsExam(
        name: String,
        note: String,
    ): Boolean {
        if (isExamRemark(name)) return true
        if (note.isBlank()) return false
        return note.lines().any { line ->
            val content = CourseNotes.stripWeekPrefix(line)
            content.isNotEmpty() && isExamRemark(content)
        }
    }

    private val EXAM_SEAT_RE = Regex("""座位号\s*([^\s·,，;；]+)""")

    fun extractExamSeat(note: String): String? =
        EXAM_SEAT_RE
            .find(note)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun formatExamLocation(
        location: String,
        note: String,
        seatSuffix: String,
    ): String? {
        val loc = location.trim()
        val seat =
            extractExamSeat(note)?.let { s ->
                val suffix = seatSuffix.trim()
                if (suffix.isEmpty()) s else "$s$suffix"
            }
        return when {
            loc.isNotEmpty() && seat != null -> "$loc · $seat"
            loc.isNotEmpty() -> loc
            seat != null -> seat
            else -> null
        }
    }
}
