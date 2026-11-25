package app.goke.core.jxfw

import app.goke.core.model.Course
import kotlinx.serialization.Serializable

/** 开放中的限选课计划时间窗。 */
@Serializable
data class XkOpenPlan(
    val jxjhdm: String,
    val jhfxdm: String = "",
    val planType: String = "",
    val planName: String,
    val startTime: String = "",
    val endTime: String = "",
)

/** 选课门户窗口状态。 */
@Serializable
data class XkWindowStatus(
    val xkjd: String,
    val xnxqdm: String,
    val isCreditWallet: Boolean = false,
    val canDrop: Boolean = false,
    val maxCourses: Int? = null,
    val headerNotice: String = "",
    val openPlans: List<XkOpenPlan> = emptyList(),
) {
    /** 当前是否处于开放选课阶段。 */
    val isOpen: Boolean
        get() = xkjd.isNotBlank() && xkjd != "0" && xnxqdm.isNotBlank() && xnxqdm != "-1"
}

/** 学分钱包额度。 */
@Serializable
data class XkCreditWallet(
    val totalCredits: Double,
    val usedCredits: Double,
) {
    /** 剩余可用学分。 */
    val availableCredits: Double
        get() = (totalCredits - usedCredits).coerceAtLeast(0.0)
}

/** 单次排课明细。 */
@Serializable
data class XkSessionSlot(
    val kcrwdm: String,
    val week: Int,
    val dayOfWeek: Int,
    val periods: List<Int>,
    val room: String = "",
    val roomType: String = "",
    val roomCode: String = "",
    val teacher: String = "",
    val teachingType: String = "",
    val classGroup: String = "",
    val content: String = "",
    val departmentCode: String = "",
    val hours: String = "",
)

/** 按星期/节次/地点归并后的排课摘要（用于课程卡片展示）。 */
@Serializable
data class XkScheduleSummary(
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: Set<Int>,
    val room: String = "",
    val campusHint: String = "",
)

/** 冲突占用来源。 */
@Serializable
enum class XkConflictSource {
    /** 来自远程课表的已排课。 */
    JXFW,

    /** 来自本地课表当前学期 `Course`。 */
    LOCAL_TIMETABLE,
}

/** 单个课节的已占用记录（用于冲突检测）。 */
@Serializable
data class XkOccupiedSlot(
    val week: Int,
    val dayOfWeek: Int,
    val period: Int,
    val courseName: String,
    val source: XkConflictSource,
)

/** 选课时间冲突条目（按课程、星期与来源聚合）。 */
@Serializable
data class XkConflict(
    val courseName: String,
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: Set<Int>,
    val source: XkConflictSource,
)

/** 选课单时段冲突明细（保留精确 `(dayOfWeek, periods)` 与 `weeks` 映射）。 */
@Serializable
data class XkSlotConflict(
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: Set<Int>,
    val courseName: String,
    val source: XkConflictSource,
)

/** 课表预览网格中的单个连续时段块（已有占课或待选课）。 */
data class XkPreviewBlock(
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val title: String,
    val weeks: Set<Int>,
    val room: String = "",
    val isCandidate: Boolean = false,
    val conflicts: List<XkSlotConflict> = emptyList(),
) {
    val hasConflict: Boolean get() = conflicts.isNotEmpty()
}

/** 选课详情页迷你周课表预览模型。 */
data class XkSchedulePreview(
    val occupiedBlocks: List<XkPreviewBlock>,
    val candidateBlocks: List<XkPreviewBlock>,
    val conflictCells: Map<Pair<Int, Int>, List<XkSlotConflict>>,
    val maxPeriod: Int,
    val candidateWeeks: Set<Int>,
)

/** 选课课程条目（聚合可选/已选列表、排课摘要与冲突信息）。 */
@Serializable
data class XkCourseItem(
    val kcrwdm: String,
    val kcdm: String = "",
    val jxbdm: String = "",
    val kcptdm: String = "",
    val name: String,
    val projectSuffix: String = "",
    val credit: Double? = null,
    val teacher: String = "",
    val capacity: Int? = null,
    val enrolledCount: Int? = null,
    val category: String = "",
    val subCategory: String = "",
    val enrolled: Boolean = false,
    val sessions: List<XkSessionSlot> = emptyList(),
    val scheduleSummary: List<XkScheduleSummary> = emptyList(),
    val classGroup: String = "",
    val campusHint: String = "",
    val conflicts: List<XkConflict> = emptyList(),
    val slotConflicts: List<XkSlotConflict> = emptyList(),
) {
    /** 拼接项目后缀后的展示名称。 */
    val displayName: String
        get() = if (projectSuffix.isNotBlank()) "$name [$projectSuffix]" else name

    /** 剩余名额；容量或已选人数缺失时返回 null。 */
    val remainingSeats: Int?
        get() = if (capacity != null && enrolledCount != null) (capacity - enrolledCount).coerceAtLeast(0) else null

    /** 是否已满员。 */
    val isFull: Boolean
        get() = remainingSeats?.let { it <= 0 } ?: false

    /** 是否存在时间冲突。 */
    val hasConflict: Boolean
        get() = conflicts.isNotEmpty()
}

/** 课程补充元数据。 */
@Serializable
data class XkExtraMeta(
    val courseCode: String = "",
    val englishName: String = "",
    val studyMode: String = "",
    val plannedHours: String = "",
    val gradingMode: String = "",
    val platformName: String = "",
    val classGroup: String = "",
)

/** 课程详情（按需懒加载：中英文大纲、教学日历与补充元数据）。 */
@Serializable
data class XkCourseDetail(
    val kcrwdm: String,
    val kcptdm: String = "",
    val zhSyllabus: String = "",
    val enSyllabus: String = "",
    val calendar: List<XkSessionSlot> = emptyList(),
    val extra: XkExtraMeta? = null,
)

/** 选课/退选失败原因分类。 */
@Serializable
enum class XkFailureKind {
    FULL,
    CONFLICT,
    CREDIT_LIMIT,
    OUT_OF_WINDOW,
    ALREADY_ENROLLED,
    NOT_DROPPABLE,
    OTHER,
}

/** 选课/退选操作结果。 */
sealed interface XkActionResult {
    data class Success(
        val raw: String,
    ) : XkActionResult

    data class Failure(
        val reason: String,
        val kind: XkFailureKind,
    ) : XkActionResult
}

/** 选课 UI 排课归并、周次格式化与冲突预览工具（不含任何协议解析）。 */
object JxfwXkParse {
    private data class ScheduleGroupKey(
        val dayOfWeek: Int,
        val periods: List<Int>,
        val room: String,
    )

    private data class SlotLookupKey(
        val week: Int,
        val dayOfWeek: Int,
        val period: Int,
    )

    private data class ConflictGroupKey(
        val courseName: String,
        val dayOfWeek: Int,
    )

    private data class SlotConflictGroupKey(
        val courseName: String,
        val dayOfWeek: Int,
        val periods: List<Int>,
    )

    private data class OccupiedMergeKey(
        val week: Int,
        val dayOfWeek: Int,
        val period: Int,
        val courseName: String,
    )

    private data class ConflictAccum(
        val periods: MutableSet<Int> = sortedSetOf(),
        val weeks: MutableSet<Int> = sortedSetOf(),
        var source: XkConflictSource = XkConflictSource.LOCAL_TIMETABLE,
    )

    /** 将本地课表当前学期 [Course] 展开为 [XkOccupiedSlot]（自动跳过已软删记录）。 */
    fun localCoursesToOccupiedSlots(courses: List<Course>): List<XkOccupiedSlot> =
        courses.flatMap { course ->
            if (course.deletedAt != null) return@flatMap emptyList()
            if (course.dayOfWeek !in 1..7) return@flatMap emptyList()
            if (course.startPeriod > course.endPeriod) return@flatMap emptyList()
            course.weeks.flatMap { week ->
                (course.startPeriod..course.endPeriod).map { period ->
                    XkOccupiedSlot(
                        week = week,
                        dayOfWeek = course.dayOfWeek,
                        period = period,
                        courseName = course.name,
                        source = XkConflictSource.LOCAL_TIMETABLE,
                    )
                }
            }
        }

    /** 将散落排课课次按 `(dayOfWeek, periods, room)` 归并，并推导校区提示。 */
    fun summarizeSchedule(slots: List<XkSessionSlot>): List<XkScheduleSummary> =
        slots
            .groupBy { ScheduleGroupKey(it.dayOfWeek, it.periods, it.room.trim()) }
            .map { (key, group) ->
                val sampleCode = group.firstNotNullOfOrNull { it.roomCode.takeIf(String::isNotBlank) }.orEmpty()
                XkScheduleSummary(
                    dayOfWeek = key.dayOfWeek,
                    periods = key.periods,
                    weeks = group.map { it.week }.toSortedSet(),
                    room = key.room,
                    campusHint = inferCampusHint(sampleCode, key.room),
                )
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.periods.firstOrNull() ?: 0 }, { it.room }))

    /** 将周次集合格式化为紧凑区间，如 `{1,2,3,5,7,8}` → `"1-3,5,7-8周"`。 */
    fun formatWeeksRange(weeks: Set<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            val w = sorted[i]
            if (w == end + 1) {
                end = w
            } else {
                parts += if (start == end) "$start" else "$start-$end"
                start = w
                end = w
            }
        }
        parts += if (start == end) "$start" else "$start-$end"
        return parts.joinToString(",") + "周"
    }

    /** 合并远程与本地课表已占用时段，同槽位同课程名时优先保留 [XkConflictSource.JXFW]。 */
    fun mergeOccupiedSlots(
        jxfwOccupied: List<XkOccupiedSlot>,
        localOccupied: List<XkOccupiedSlot>,
    ): List<XkOccupiedSlot> {
        val merged = linkedMapOf<OccupiedMergeKey, XkOccupiedSlot>()
        for (slot in jxfwOccupied + localOccupied) {
            val normalizedName = slot.courseName.trim()
            if (normalizedName.isEmpty()) continue
            val key = OccupiedMergeKey(slot.week, slot.dayOfWeek, slot.period, normalizedName)
            val existing = merged[key]
            if (existing == null || (existing.source != XkConflictSource.JXFW && slot.source == XkConflictSource.JXFW)) {
                merged[key] = slot.copy(courseName = normalizedName)
            }
        }
        return merged.values.sortedWith(
            compareBy({ it.dayOfWeek }, { it.period }, { it.week }, { it.courseName }),
        )
    }

    /** 构建课程详情页迷你周课表预览模型。 */
    fun buildSchedulePreview(
        occupiedSlots: List<XkOccupiedSlot>,
        candidate: XkCourseItem,
        calendarOverride: List<XkSessionSlot> = emptyList(),
    ): XkSchedulePreview {
        val slots = calendarOverride.ifEmpty { candidate.sessions }
        val candidateWeeks = slots.map { it.week }.filter { it > 0 }.toSortedSet()

        val filteredOccupied =
            if (candidate.enrolled) {
                occupiedSlots.filterNot { it.courseName.trim() == candidate.name.trim() }
            } else {
                occupiedSlots
            }

        val occupiedBlocks =
            filteredOccupied
                .groupBy { it.dayOfWeek to it.courseName.trim() }
                .flatMap { (key, daySlots) ->
                    val (dayOfWeek, title) = key
                    val weeksByPeriod = mutableMapOf<Int, MutableSet<Int>>()
                    for (s in daySlots) {
                        weeksByPeriod.getOrPut(s.period) { sortedSetOf() } += s.week
                    }
                    contiguousRuns(weeksByPeriod.keys.sorted()).map { run ->
                        val weeks = sortedSetOf<Int>()
                        for (p in run) {
                            weeks += weeksByPeriod[p].orEmpty()
                        }
                        XkPreviewBlock(
                            dayOfWeek = dayOfWeek,
                            startPeriod = run.first(),
                            endPeriod = run.last(),
                            title = title,
                            weeks = weeks,
                            isCandidate = false,
                        )
                    }
                }.sortedWith(
                    compareBy<XkPreviewBlock> {
                        candidateWeeks.isNotEmpty() && it.weeks.any { w -> w in candidateWeeks }
                    }.thenBy { it.dayOfWeek }.thenBy { it.startPeriod }.thenBy { it.title },
                )

        val occupiedIndex = filteredOccupied.groupBy { SlotLookupKey(it.week, it.dayOfWeek, it.period) }
        val slotConflicts =
            if (!candidate.enrolled && slots.isNotEmpty()) {
                detectSlotConflicts(candidate.name, slots, occupiedIndex)
            } else {
                emptyList()
            }

        val conflictCells = linkedMapOf<Pair<Int, Int>, MutableList<XkSlotConflict>>()
        for (conflict in slotConflicts) {
            for (period in conflict.periods) {
                val cell = conflict.dayOfWeek to period
                val list = conflictCells.getOrPut(cell) { mutableListOf() }
                if (conflict !in list) list += conflict
            }
        }

        val candidateGroupKey = mutableMapOf<Triple<Int, List<Int>, String>, MutableSet<Int>>()
        for (slot in slots) {
            val room = slot.room.trim()
            for (run in contiguousRuns(slot.periods)) {
                val key = Triple(slot.dayOfWeek, run, room)
                candidateGroupKey.getOrPut(key) { sortedSetOf() } += slot.week
            }
        }
        val candidateBlocks =
            candidateGroupKey
                .map { (key, weeks) ->
                    val (dayOfWeek, run, room) = key
                    val start = run.first()
                    val end = run.last()
                    val matchingConflicts =
                        slotConflicts.filter { c ->
                            c.dayOfWeek == dayOfWeek &&
                                c.periods.any { it in start..end } &&
                                c.weeks.any { it in weeks }
                        }
                    XkPreviewBlock(
                        dayOfWeek = dayOfWeek,
                        startPeriod = start,
                        endPeriod = end,
                        title = candidate.displayName,
                        weeks = weeks,
                        room = room,
                        isCandidate = true,
                        conflicts = matchingConflicts,
                    )
                }.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.room }))

        val maxPeriod =
            maxOf(
                10,
                occupiedBlocks.maxOfOrNull { it.endPeriod } ?: 0,
                candidateBlocks.maxOfOrNull { it.endPeriod } ?: 0,
            )

        return XkSchedulePreview(
            occupiedBlocks = occupiedBlocks,
            candidateBlocks = candidateBlocks,
            conflictCells = conflictCells,
            maxPeriod = maxPeriod,
            candidateWeeks = candidateWeeks,
        )
    }

    /** 聚合课程列表、已选状态、排课明细与双重时间冲突。已选课程自身不标记冲突。 */
    fun mergeCourseData(
        items: List<XkCourseItem>,
        enrolledIds: Set<String>,
        slotsByTask: Map<String, List<XkSessionSlot>>,
        jxfwOccupied: List<XkOccupiedSlot>,
        localOccupied: List<XkOccupiedSlot>,
    ): List<XkCourseItem> {
        val occupiedIndex =
            mergeOccupiedSlots(jxfwOccupied, localOccupied)
                .groupBy { SlotLookupKey(it.week, it.dayOfWeek, it.period) }
        return items.map { item ->
            val isEnrolled = item.enrolled || item.kcrwdm in enrolledIds
            val slots = slotsByTask[item.kcrwdm].orEmpty()
            val summary = summarizeSchedule(slots)
            val teacher =
                item.teacher.ifBlank {
                    slots
                        .map { it.teacher.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString(",")
                }
            val classGroup =
                item.classGroup.ifBlank {
                    slots.firstOrNull { it.classGroup.isNotBlank() }?.classGroup.orEmpty()
                }
            val campusHint =
                item.campusHint.ifBlank {
                    summary.firstOrNull { it.campusHint.isNotBlank() }?.campusHint.orEmpty()
                }
            val conflicts =
                if (isEnrolled || slots.isEmpty()) {
                    emptyList()
                } else {
                    detectConflicts(item.name, slots, occupiedIndex)
                }
            val slotConflicts =
                if (isEnrolled || slots.isEmpty()) {
                    emptyList()
                } else {
                    detectSlotConflicts(item.name, slots, occupiedIndex)
                }
            item.copy(
                enrolled = isEnrolled,
                teacher = teacher,
                sessions = slots,
                scheduleSummary = summary,
                classGroup = classGroup,
                campusHint = campusHint,
                conflicts = conflicts,
                slotConflicts = slotConflicts,
            )
        }
    }

    private fun detectSlotConflicts(
        courseName: String,
        slots: List<XkSessionSlot>,
        occupiedIndex: Map<SlotLookupKey, List<XkOccupiedSlot>>,
    ): List<XkSlotConflict> {
        val grouped = linkedMapOf<SlotConflictGroupKey, ConflictAccum>()
        for (slot in slots) {
            val hitsByCourse = linkedMapOf<String, Pair<MutableSet<Int>, XkConflictSource>>()
            for (period in slot.periods) {
                val hits = occupiedIndex[SlotLookupKey(slot.week, slot.dayOfWeek, period)].orEmpty()
                for (hit in hits) {
                    if (hit.courseName == courseName) continue
                    val entry =
                        hitsByCourse.getOrPut(hit.courseName) {
                            sortedSetOf<Int>() to hit.source
                        }
                    entry.first += period
                    if (hit.source == XkConflictSource.JXFW && entry.second != XkConflictSource.JXFW) {
                        hitsByCourse[hit.courseName] = entry.first to XkConflictSource.JXFW
                    }
                }
            }
            for ((hitCourse, pair) in hitsByCourse) {
                val (collidingPeriods, source) = pair
                for (run in contiguousRuns(collidingPeriods.toList())) {
                    val key = SlotConflictGroupKey(hitCourse, slot.dayOfWeek, run)
                    val accum = grouped.getOrPut(key) { ConflictAccum(source = source) }
                    if (source == XkConflictSource.JXFW) {
                        accum.source = XkConflictSource.JXFW
                    }
                    accum.weeks += slot.week
                }
            }
        }
        return grouped
            .map { (key, accum) ->
                XkSlotConflict(
                    dayOfWeek = key.dayOfWeek,
                    periods = key.periods,
                    weeks = accum.weeks,
                    courseName = key.courseName,
                    source = accum.source,
                )
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.periods.firstOrNull() ?: 0 }, { it.courseName }))
    }

    private fun contiguousRuns(periods: List<Int>): List<List<Int>> {
        val sorted = periods.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val runs = mutableListOf<List<Int>>()
        var current = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            if (sorted[i] == current.last() + 1) {
                current += sorted[i]
            } else {
                runs += current
                current = mutableListOf(sorted[i])
            }
        }
        runs += current
        return runs
    }

    private fun detectConflicts(
        courseName: String,
        slots: List<XkSessionSlot>,
        occupiedIndex: Map<SlotLookupKey, List<XkOccupiedSlot>>,
    ): List<XkConflict> {
        val grouped = linkedMapOf<ConflictGroupKey, ConflictAccum>()
        for (slot in slots) {
            for (period in slot.periods) {
                val hits = occupiedIndex[SlotLookupKey(slot.week, slot.dayOfWeek, period)].orEmpty()
                for (hit in hits) {
                    if (hit.courseName == courseName) continue
                    val key = ConflictGroupKey(hit.courseName, slot.dayOfWeek)
                    val accum = grouped.getOrPut(key) { ConflictAccum(source = hit.source) }
                    if (hit.source == XkConflictSource.JXFW) {
                        accum.source = XkConflictSource.JXFW
                    }
                    accum.periods += period
                    accum.weeks += slot.week
                }
            }
        }
        return grouped
            .map { (key, accum) ->
                XkConflict(
                    courseName = key.courseName,
                    dayOfWeek = key.dayOfWeek,
                    periods = accum.periods.toList(),
                    weeks = accum.weeks,
                    source = accum.source,
                )
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.periods.firstOrNull() ?: 0 }, { it.courseName }))
    }

    private fun inferCampusHint(
        roomCode: String,
        roomName: String,
    ): String {
        val code = roomCode.trim()
        val name = roomName.trim()
        return when {
            code.startsWith("01") || name.contains("(城)") -> "大学城"
            code.startsWith("02") || name.contains("(龙)") -> "龙洞"
            code.startsWith("03") || name.contains("(东)") -> "东风路"
            code.startsWith("04") || name.contains("(番)") -> "番禺"
            code.startsWith("108291461") || name.contains("(揭)") || name.contains("揭阳") -> "揭阳"
            else -> ""
        }
    }
}
