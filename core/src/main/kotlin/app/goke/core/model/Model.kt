package app.goke.core.model

import app.goke.core.util.LocalDateSerializer
import app.goke.core.util.LocalDateTimeSerializer
import app.goke.core.util.LocalTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 数据来源，用于标记与审计。 */
@Serializable
enum class Source { MANUAL, CODE, AI, IMPORT, PARCEL }

@Serializable
enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

@Serializable
enum class EventKind { NORMAL, EXAM, COUNTDOWN }

@Serializable
enum class ExceptionType { CANCEL, RESCHEDULE }

/** 提前提醒分钟数列表归一化：合并单值兼容字段与列表，去重并升序排列。 */
object Reminders {
    fun normalize(
        primary: Int?,
        list: List<Int>,
    ): List<Int> = (listOfNotNull(primary) + list).filter { it >= 0 }.distinct().sorted()
}

/** 所有实体的公共基类：UUID 主键、updatedAt、软删除、可空 userId，为同步预留。 */
@Serializable
sealed interface Record {
    val id: String
    val updatedAt: Long
    val deletedAt: Long?
    val userId: String?

    fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ): Record
}

@Serializable
data class PeriodTime(
    val index: Int,
    @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val end: LocalTime,
)

@Serializable
@SerialName("semester")
data class Semester(
    override val id: String,
    val name: String,
    @Serializable(with = LocalDateSerializer::class) val week1Monday: LocalDate,
    val totalWeeks: Int = 20,
    val periods: List<PeriodTime> = GdutPeriods.default,
    val isCurrent: Boolean = true,
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
) : Record {
    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

@Serializable
@SerialName("course")
data class Course(
    override val id: String,
    val semesterId: String,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1=周一 … 7=周日 */
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
    val colorIndex: Int = 0,
    val remindMinutes: Int? = null,
    val note: String = "",
    val source: Source = Source.MANUAL,
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
    val remindMinutesList: List<Int> = emptyList(),
    val kind: EventKind = EventKind.NORMAL,
    val remindPersistent: Boolean = false,
    @Serializable(with = LocalTimeSerializer::class) val startTime: LocalTime? = null,
    @Serializable(with = LocalTimeSerializer::class) val endTime: LocalTime? = null,
) : Record {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)

    val isExam: Boolean
        get() = kind == EventKind.EXAM

    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

@Serializable
@SerialName("courseException")
data class CourseException(
    override val id: String,
    val courseId: String,
    val week: Int,
    val type: ExceptionType,
    val newWeek: Int? = null,
    val newDayOfWeek: Int? = null,
    val newStartPeriod: Int? = null,
    val newEndPeriod: Int? = null,
    val newRoom: String? = null,
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
) : Record {
    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

@Serializable
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    /**
     * 复现的「日」：
     * WEEKLY → 1=周一…7=周日；MONTHLY / YEARLY → 1…31 日（该月没有这一天就跳过）。
     * 为空表示跟随起始日（同一星期几 / 同一日）。
     */
    val byDays: Set<Int> = emptySet(),
    @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    val count: Int? = null,
    val exDates: Set<
        @Serializable(with = LocalDateSerializer::class)
        LocalDate,
    > = emptySet(),
    /** 仅 YEARLY 使用：1…12 月；与 [byDays] 取笛卡尔积（跨月多选时为每月的那几天）。为空表示跟随起始月。 */
    val byMonths: Set<Int> = emptySet(),
)

@Serializable
@SerialName("event")
data class Event(
    override val id: String,
    val title: String,
    @Serializable(with = LocalDateTimeSerializer::class) val start: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class) val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String = "",
    val note: String = "",
    val colorIndex: Int = 4,
    val category: String = "",
    val remindMinutes: Int? = null,
    val recurrence: Recurrence? = null,
    val kind: EventKind = EventKind.NORMAL,
    val source: Source = Source.MANUAL,
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
    val remindMinutesList: List<Int> = emptyList(),
) : Record {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)

    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

@Serializable
@SerialName("taskList")
data class TaskList(
    override val id: String,
    val name: String,
    val colorIndex: Int = 1,
    val sort: Int = 0,
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
) : Record {
    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

/** 待办附件：文件本体复制进应用私有目录，这里只记文件名与显示名。 */
@Serializable
data class Attachment(
    val name: String,
    val mime: String,
    /** 私有 attachments 目录下的文件名（不含路径）；换设备同步时文件本体不会跟过来。 */
    val file: String,
)

@Serializable
@SerialName("task")
data class Task(
    override val id: String,
    val title: String,
    val note: String = "",
    val listId: String? = null,
    val tags: List<String> = emptyList(),
    /** 0 无 / 1 低 / 2 中 / 3 高 */
    val priority: Int = 0,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(with = LocalDateTimeSerializer::class) val due: LocalDateTime? = null,
    val dueHasTime: Boolean = false,
    val estimateMinutes: Int? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    /** 「不做了」：放弃而非完成。仍记 done=true（所有「已结束」逻辑复用），只是展示与统计上区分。 */
    val dropped: Boolean = false,
    /** 子任务（仅一层） */
    val parentId: String? = null,
    val repeat: Recurrence? = null,
    val remindMinutes: Int? = null,
    /** 持续提醒：通知铃声一直循环，直到完成、稍后或关掉这条提醒。 */
    val remindPersistent: Boolean = false,
    /** 关联课程；与 dueBeforeNextClass 配合，截止为「下节课前」 */
    val courseId: String? = null,
    val dueBeforeNextClass: Boolean = false,
    /** 排入日历的时间块 */
    @Serializable(with = LocalDateTimeSerializer::class) val blockStart: LocalDateTime? = null,
    val blockMinutes: Int? = null,
    val sortOrder: Int = 0,
    val source: Source = Source.MANUAL,
    /**
     * 扩展字段：
     * - 快递：`code` / `place` / `company` / `windowStart` / `windowEnd`（兼容旧键 `pickupCode` / `pickupPlace`）
     * - 日程/考试/倒数日：`kind`（`EXAM` / `COUNTDOWN` / `NORMAL`）、`location`、`checkable`（`"false"` 表示不可勾选）
     */
    val extra: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    override val updatedAt: Long = 0,
    override val deletedAt: Long? = null,
    override val userId: String? = null,
    val remindMinutesList: List<Int> = emptyList(),
) : Record {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)

    val isExam: Boolean
        get() = extra["kind"] == EventKind.EXAM.name || id.startsWith("jxfw-exam/") || id.startsWith("jxfw-course-exam/")

    override fun withMeta(
        updatedAt: Long,
        deletedAt: Long?,
    ) = copy(updatedAt = updatedAt, deletedAt = deletedAt)
}

/** 广工全校统一作息节次时间（08:30 / 13:50 / 18:30，见学校作息时间表）。 */
object GdutPeriods {
    val default: List<PeriodTime> =
        listOf(
            "08:30" to "09:15",
            "09:20" to "10:05",
            "10:25" to "11:10",
            "11:15" to "12:00",
            "13:50" to "14:35",
            "14:40" to "15:25",
            "15:30" to "16:15",
            "16:30" to "17:15",
            "17:20" to "18:05",
            "18:30" to "19:15",
            "19:20" to "20:05",
            "20:10" to "20:55",
        ).mapIndexed { i, (s, e) -> PeriodTime(i + 1, LocalTime.parse(s), LocalTime.parse(e)) }

    /** 第 1 周周一（需求文档 6.2）。 */
    val week1Monday: LocalDate = LocalDate.of(2026, 8, 31)
}
