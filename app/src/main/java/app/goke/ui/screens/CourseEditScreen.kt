package app.goke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Conflicts
import app.goke.core.change.apply
import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Course
import app.goke.core.model.EventKind
import app.goke.core.model.ExceptionType
import app.goke.core.model.PeriodTime
import app.goke.core.model.Reminders
import app.goke.core.model.Source
import app.goke.core.time.WeekSets
import app.goke.core.todo.TaskLogic
import app.goke.core.util.Ids
import app.goke.ui.LocalActions
import app.goke.ui.LocalNav
import app.goke.ui.LocalRouteBackHolder
import app.goke.ui.Route
import app.goke.ui.components.ChipChoice
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.ConflictCard
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.mintClickable
import app.goke.ui.components.rememberNoFocusPopup
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.liveSnapshot
import app.goke.ui.reduceMotion
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import app.goke.ui.weekdayShort
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime

/** 一条上课时段（对应一条 Course 记录）：同一门课可以有多个时段，各自带教室与提醒。 */
internal data class SlotDraft(
    val id: String,
    val base: Course?,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
    val room: String,
    val remindMinutes: Int? = null,
    val teacher: String? = null,
    val remindMinutesList: List<Int> = emptyList(),
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
) {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)
}

/** 时段在课表上的真实钟点，如 `08:30–10:05`；若课程自带精确时刻（如考试）则优先使用。 */
private fun clockRange(
    periods: List<PeriodTime>,
    sp: Int,
    ep: Int,
    startTime: LocalTime? = null,
    endTime: LocalTime? = null,
): String {
    if (startTime != null && endTime != null) return "${startTime.hm()}–${endTime.hm()}"
    val start = periods.firstOrNull { it.index == sp }?.start?.hm() ?: return ""
    val end = periods.firstOrNull { it.index == ep }?.end?.hm() ?: return ""
    return "$start–$end"
}

/**
 * 课程编辑：三段式结构化表单（课程身份 → 上课时段与提醒 → 课程信息与关联待办）。
 * 顶栏隐去重复标题，让正文首行大字课程名成为唯一视觉焦点；返回即整体保存为一个变更集。
 */
@Composable
fun CourseEditScreen(
    id: String?,
    initialDow: Int? = null,
    initialPeriod: Int? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val nav = LocalNav.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val sem = snap.currentSemester
    if (sem == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val existing = remember(id) { id?.let { i -> snap.courses.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val periodCount = sem.periods.size
    val totalWeeks = sem.totalWeeks
    val periods = remember(sem) { sem.periods.sortedBy { it.index } }

    // ── 课程级字段：一门课只有一套，保存时统一落到全部时段上 ──
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorIndex ?: (snap.courses.size % 8)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var kind by remember(id) { mutableStateOf(existing?.kind ?: EventKind.NORMAL) }
    var manualKindOverride by remember(id) { mutableStateOf(false) }
    var remindPersistent by remember(id) { mutableStateOf(existing?.remindPersistent ?: false) }

    fun applySmartExamCheck(
        nextName: String,
        nextNote: String,
    ) {
        if (!manualKindOverride && kind == EventKind.NORMAL && JxfwSync.shouldClassifyCourseAsExam(nextName, nextNote)) {
            kind = EventKind.EXAM
            remindPersistent = true
        }
    }

    // ── 时段：同名记录即同一门课（与「另一天」的历史约定一致） ──
    val initialSlots =
        remember(id) {
            if (existing != null) {
                snap.courses
                    .filter { it.deletedAt == null && it.semesterId == sem.id && it.name == existing.name }
                    .sortedWith(compareBy({ it.weeks.minOrNull() ?: 99 }, { it.dayOfWeek }, { it.startPeriod }))
                    .map {
                        SlotDraft(
                            it.id,
                            it,
                            it.dayOfWeek,
                            it.startPeriod,
                            it.endPeriod,
                            it.weeks,
                            it.room,
                            it.remindMinutes,
                            it.teacher,
                            it.effectiveRemindMinutes,
                            it.startTime,
                            it.endTime,
                        )
                    }
            } else {
                val sp = (initialPeriod ?: 1).coerceIn(1, periodCount)
                val ep = (sp + 1).coerceAtMost(periodCount).coerceAtLeast(sp)
                listOf(
                    SlotDraft(
                        id = Ids.next(),
                        base = null,
                        dayOfWeek = (initialDow ?: 1).coerceIn(1, 7),
                        startPeriod = sp,
                        endPeriod = ep,
                        weeks = WeekSets.all(totalWeeks),
                        room = "",
                        remindMinutes = null,
                        teacher = "",
                        remindMinutesList = emptyList(),
                    ),
                )
            }
        }
    val initialTeacher =
        remember(id) {
            if (existing != null) {
                initialSlots
                    .mapNotNull { it.teacher?.trim()?.takeIf { t -> t.isNotEmpty() } }
                    .distinct()
                    .joinToString("、")
            } else {
                ""
            }
        }
    var teacher by remember(id) { mutableStateOf(initialTeacher) }
    var slots by remember(id) { mutableStateOf(initialSlots) }
    var openSlot by remember { mutableStateOf<String?>(null) }
    var pendingNewSlot by remember { mutableStateOf<SlotDraft?>(null) }
    var committed by remember { mutableStateOf(false) }
    var remindDlg by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val noFocusPopup = rememberNoFocusPopup()
    val nameFocus = remember { FocusRequester() }
    val reduce = reduceMotion()
    LaunchedEffect(existing == null) {
        if (existing == null) {
            // 延迟到路由进入动画完成后再拉起软键盘，避免首帧触发同步 IMM/WMS Binder 交易与窗口重排
            delay(if (reduce) 16L else 280L)
            nameFocus.requestFocus()
        }
    }

    val origin = existing

    fun materialize(s: SlotDraft): Course? {
        if (s.weeks.isEmpty()) return null
        val n = name.trim()
        val t = s.teacher?.trim() ?: teacher.trim()
        val r = s.room.trim()
        val nt = note.trim()
        val b = s.base
        return if (b != null) {
            b.copy(
                name = n,
                teacher = t,
                room = r,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                weeks = s.weeks,
                colorIndex = color,
                remindMinutes = s.effectiveRemindMinutes.firstOrNull(),
                remindMinutesList = s.effectiveRemindMinutes,
                note = nt,
                startTime = s.startTime,
                endTime = s.endTime,
            )
        } else {
            Course(
                id = s.id,
                semesterId = sem.id,
                name = n,
                teacher = t,
                room = r,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                weeks = s.weeks,
                colorIndex = color,
                remindMinutes = s.effectiveRemindMinutes.firstOrNull(),
                remindMinutesList = s.effectiveRemindMinutes,
                note = nt,
                source = Source.MANUAL,
                startTime = s.startTime,
                endTime = s.endTime,
            )
        }
    }

    fun addSlot() {
        val last = slots.lastOrNull()
        val sp = (last?.startPeriod ?: 1).coerceIn(1, periodCount)
        val ep = (last?.endPeriod ?: (sp + 1).coerceAtMost(periodCount)).coerceIn(sp, periodCount)
        pendingNewSlot =
            SlotDraft(
                id = Ids.next(),
                base = null,
                dayOfWeek = last?.dayOfWeek ?: (initialDow ?: 1).coerceIn(1, 7),
                startPeriod = sp,
                endPeriod = ep,
                weeks = last?.weeks ?: WeekSets.all(totalWeeks),
                room = last?.room.orEmpty(),
                remindMinutes = last?.effectiveRemindMinutes?.firstOrNull(),
                teacher = last?.teacher ?: teacher,
                remindMinutesList = last?.effectiveRemindMinutes.orEmpty(),
            )
    }

    val valid =
        name.isNotBlank() && slots.isNotEmpty() &&
            slots.all { it.weeks.isNotEmpty() && it.endPeriod >= it.startPeriod }
    val fieldDirty =
        if (origin == null) {
            name.isNotBlank() || teacher.isNotBlank() || note.isNotBlank() || kind != EventKind.NORMAL || remindPersistent
        } else {
            name.trim() != origin.name || teacher.trim() != initialTeacher ||
                color != origin.colorIndex || note.trim() != origin.note ||
                kind != origin.kind || remindPersistent != origin.remindPersistent
        }
    val dirty = fieldDirty || slots != initialSlots

    // 冲突检测只与已排课表、学期及当前时段配置（星期/节次/周次）相关，
    // 普通课程名称/教师/颜色/备注变动不改变冲突关系，避免表单打字时每帧触发全学期排课展开；
    // 考试类型课程需携带名称以识别同课程随堂考升层，避免误报冲突。
    val conflictMatches =
        remember(snap, slots, committed, kind, if (kind == EventKind.EXAM) name.trim() else "") {
            if (committed) return@remember emptyList()
            val activeIds = slots.filter { it.weeks.isNotEmpty() }.map { it.id }
            if (activeIds.isEmpty()) {
                emptyList()
            } else {
                val changes =
                    buildSlotChanges(
                        semesterId = sem.id,
                        name = if (kind == EventKind.EXAM) name.trim() else "",
                        teacher = "",
                        colorIndex = 0,
                        note = "",
                        initialSlots = initialSlots,
                        slots = slots,
                        liveExceptions = snap.exceptions,
                        kind = kind,
                    )
                val synthetic =
                    if (changes.isEmpty()) {
                        snap
                    } else {
                        snap.apply(ChangeSet("", "", Source.MANUAL, 0L, changes), 0L)
                    }
                activeIds
                    .flatMap { cid -> Conflicts.detect(synthetic, setOf(cid)) }
                    .distinctBy { listOf(it.aId, it.bId, it.start) }
            }
        }

    val preview =
        remember(conflictMatches, valid, dirty, name) {
            if (!valid || !dirty || conflictMatches.isEmpty()) {
                null
            } else {
                val displayName = name.trim()
                conflictMatches.map { c ->
                    if (displayName.isNotBlank()) c.copy(aTitle = displayName) else c
                }
            }
        }

    fun persist(leave: Boolean) {
        if (committed) return
        if (leave) committed = true
        val ms = System.currentTimeMillis()
        val changes =
            buildSlotChanges(
                semesterId = sem.id,
                name = name,
                teacher = teacher,
                colorIndex = color,
                note = note,
                initialSlots = initialSlots,
                slots = slots,
                liveExceptions = snap.exceptions,
                kind = kind,
                remindPersistent = remindPersistent,
            )
        if (changes.isEmpty()) {
            if (leave) onBack()
            return
        }
        val desc = ctx.getString(if (origin == null) R.string.add_course_desc else R.string.edit_course_desc, name.trim())
        actions.commit(
            ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes),
            desc,
        )
        if (leave) onBack()
    }

    fun requestBack() {
        when {
            committed -> {
                onBack()
            }

            name.isBlank() && dirty -> {
                confirmDiscard = true
            }

            name.isBlank() -> {
                onBack()
            }

            // 新建课程：返回不落库，改过内容先确认放弃；修改照旧自动保存
            origin == null && dirty -> {
                confirmDiscard = true
            }

            origin == null -> {
                onBack()
            }

            !dirty -> {
                onBack()
            }

            !valid -> {
                actions.toast(ctx.getString(R.string.weeks_invalid))
                confirmDiscard = true
            }

            else -> {
                persist(leave = true)
            }
        }
    }
    val needsDiscardConfirm = (name.isBlank() && dirty) || (origin == null && dirty) || (origin != null && dirty && !valid)
    BackHandler(enabled = !committed && needsDiscardConfirm) { requestBack() }
    val backHolder = LocalRouteBackHolder.current
    SideEffect {
        backHolder?.onCommitBack = if (!committed && !needsDiscardConfirm) ::requestBack else null
    }
    DisposableEffect(backHolder) {
        onDispose { backHolder?.onCommitBack = null }
    }

    val defaultRemindSummary =
        stringResource(
            R.string.remind_follow_default,
            if (settings.remindCourse) remindSummaryLabel(settings.effectiveCourseRemindMinutes) else stringResource(R.string.none),
        )

    MintScreen(
        title = "",
        onBack = { requestBack() },
        actions = {
            if (origin == null) {
                // 新建：明确一个「确定」键；返回 = 放弃（改过的内容会先确认）
                TextButton(enabled = !committed && valid, onClick = { persist(leave = true) }) {
                    Text(stringResource(R.string.confirm))
                }
            } else {
                Box {
                    IconButton(onClick = { pageMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(pageMenu, { pageMenu = false }, properties = noFocusPopup) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                pageMenu = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── 第一段：课程身份与植物色（无边框纸面标题，对齐待办详情） ──
            val nameHint = stringResource(R.string.course_name_placeholder)
            BasicTextField(
                value = name,
                onValueChange = {
                    name = it
                    applySmartExamCheck(it, note)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .focusRequester(nameFocus)
                        .semantics { contentDescription = nameHint }
                        .padding(top = 4.dp, bottom = 8.dp),
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                nameHint,
                                modifier = Modifier.clearAndSetSemantics {},
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            ColorPicker(color) { color = it }
            Spacer(Modifier.height(10.dp))
            SegmentedPill(
                options =
                    listOf(
                        EventKind.NORMAL to stringResource(R.string.course_kind_normal),
                        EventKind.EXAM to stringResource(R.string.course_kind_exam),
                    ),
                selected = kind,
                onSelect = { next ->
                    manualKindOverride = true
                    kind = next
                    if (next == EventKind.EXAM) {
                        remindPersistent = true
                    }
                },
            )
            AnimatedVisibility(
                visible = kind == EventKind.EXAM,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                Text(
                    stringResource(R.string.course_exam_remind_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }

            // ── 第二段：上课时段与提醒 ──
            SectionLabel(
                stringResource(R.string.course_slots_section),
                trailing = if (slots.size > 1) ctx.getString(R.string.courses_slots_count, slots.size) else null,
                modifier = Modifier.padding(top = 14.dp),
            )
            val showSlotTeacher =
                remember(slots) {
                    slots.mapNotNull { it.teacher?.trim()?.takeIf { t -> t.isNotEmpty() } }.distinct().size > 1
                }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { s ->
                    SlotRow(
                        slot = s,
                        color = color,
                        clock = clockRange(periods, s.startPeriod, s.endPeriod, s.startTime, s.endTime),
                        showTeacher = showSlotTeacher,
                        onOpen = { openSlot = s.id },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .mintClickable(pressedScale = 0.98f) { addSlot() }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.course_add_slot),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = !preview.isNullOrEmpty(),
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                ConflictCard(title = stringResource(R.string.conflict_title), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    preview.orEmpty().take(4).forEach {
                        Text(
                            stringResource(R.string.conflict_line, it.aTitle, it.bTitle, it.start.toLocalDate().toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            val remindList = slots.firstOrNull()?.effectiveRemindMinutes.orEmpty()
            val remindBase = if (remindList.isEmpty()) defaultRemindSummary else remindSummaryLabel(remindList)
            val remindValue =
                if (remindPersistent) {
                    "$remindBase · ${stringResource(R.string.remind_persistent)}"
                } else {
                    remindBase
                }
            ValueRow(
                label = stringResource(if (kind == EventKind.EXAM) R.string.remind_exams else R.string.remind_courses),
                value = remindValue,
                onClick = { remindDlg = true },
                valueColor = if (remindList.isNotEmpty() || remindPersistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 第三段：课程信息与关联待办 ──
            SectionLabel(stringResource(R.string.course_info_section), modifier = Modifier.padding(top = 12.dp))
            TonalField(
                label = stringResource(R.string.label_teacher),
                value = teacher,
                onChange = { next ->
                    teacher = next
                    slots = slots.map { s -> s.copy(teacher = next) }
                },
                placeholder = stringResource(R.string.course_teacher_placeholder),
            )
            Spacer(Modifier.height(4.dp))
            TonalField(
                label = stringResource(R.string.label_note),
                value = note,
                onChange = {
                    note = it
                    applySmartExamCheck(name, it)
                },
                singleLine = false,
                minLines = 2,
                placeholder = stringResource(R.string.course_note_placeholder),
            )

            if (origin != null) {
                val courseIds =
                    remember(snap.courses, sem.id, origin) {
                        snap.courses
                            .filter { it.deletedAt == null && it.semesterId == sem.id && it.name == origin.name }
                            .map { it.id }
                            .toSet() + origin.id
                    }
                val courseExceptions =
                    remember(snap.exceptions, courseIds) {
                        snap.exceptions
                            .filter { it.deletedAt == null && it.courseId in courseIds }
                            .sortedWith(compareBy({ it.week }, { it.id }))
                    }
                if (courseExceptions.isNotEmpty()) {
                    val coursesById = remember(snap.courses) { snap.courses.associateBy { it.id } }
                    SectionLabel(
                        stringResource(R.string.course_exceptions_section),
                        trailing = courseExceptions.size.toString(),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    courseExceptions.forEach { ex ->
                        val baseCourse = coursesById[ex.courseId] ?: origin
                        val summary =
                            if (ex.type == ExceptionType.CANCEL) {
                                stringResource(R.string.course_exception_cancel, ex.week)
                            } else {
                                val targetWeek = ex.newWeek ?: ex.week
                                val targetDow = ex.newDayOfWeek ?: baseCourse.dayOfWeek
                                val sp = ex.newStartPeriod ?: baseCourse.startPeriod
                                val ep = ex.newEndPeriod ?: baseCourse.endPeriod
                                val dowLabel = ctx.getString(R.string.weekday_prefix, ctx.weekdayShort(targetDow))
                                val baseText =
                                    if (sp == ep) {
                                        stringResource(R.string.course_exception_move_single, ex.week, targetWeek, dowLabel, sp)
                                    } else {
                                        stringResource(R.string.course_exception_move, ex.week, targetWeek, dowLabel, sp, ep)
                                    }
                                val roomNote = ex.newRoom?.takeIf { it.isNotBlank() && it != baseCourse.room }
                                if (roomNote != null) "$baseText · $roomNote" else baseText
                            }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                actions.commit(
                                    ChangeSet(
                                        Ids.next(),
                                        ctx.getString(R.string.restore_class_desc, origin.name),
                                        Source.MANUAL,
                                        System.currentTimeMillis(),
                                        listOf(Change.Remove(ex)),
                                    ),
                                    ctx.getString(R.string.restore_class_toast),
                                )
                            }) { Text(stringResource(R.string.course_exception_restore)) }
                        }
                    }
                }
                val related =
                    remember(snap.tasks, snap.courses, origin) {
                        TaskLogic.forCourse(snap.tasks, snap.courses, origin)
                    }
                SectionLabel(
                    stringResource(R.string.course_todos),
                    trailing = related.size.takeIf { it > 0 }?.toString(),
                    modifier = Modifier.padding(top = 16.dp),
                )
                related.forEach { t -> TodoLine(t, snap, LocalDateTime.now(), Modifier.padding(vertical = 2.dp)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .mintClickable(pressedScale = 0.98f) { nav.push(Route.TodoEdit(courseId = origin.id)) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.course_todos_add),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    val editingPending = pendingNewSlot
    val editingSlot = editingPending ?: openSlot?.let { key -> slots.firstOrNull { it.id == key } }
    if (openSlot != null && editingSlot == null) {
        openSlot = null
    }
    if (editingSlot != null) {
        SlotEditorSheet(
            periods = periods,
            totalWeeks = totalWeeks,
            slot = editingSlot,
            onChange = { updated ->
                val nextSlots =
                    if (editingPending != null) {
                        pendingNewSlot = null
                        slots + updated
                    } else {
                        slots.map { if (it.id == editingSlot.id) updated else it }
                    }
                slots = nextSlots
                teacher =
                    nextSlots
                        .mapNotNull { it.teacher?.trim()?.takeIf { t -> t.isNotEmpty() } }
                        .distinct()
                        .joinToString("、")
            },
            onDismiss = {
                if (editingPending != null) {
                    pendingNewSlot = null
                } else {
                    openSlot = null
                }
            },
            onDelete =
                if (editingPending == null && slots.size > 1) {
                    {
                        val nextSlots = slots.filterNot { it.id == editingSlot.id }
                        slots = nextSlots
                        teacher =
                            nextSlots
                                .mapNotNull { it.teacher?.trim()?.takeIf { t -> t.isNotEmpty() } }
                                .distinct()
                                .joinToString("、")
                        openSlot = null
                    }
                } else {
                    null
                },
        )
    }

    if (remindDlg) {
        RemindPickDialog(
            initial = slots.firstOrNull()?.effectiveRemindMinutes.orEmpty(),
            hasTime = true,
            initialPersistent = remindPersistent,
            onDismiss = { remindDlg = false },
            noneLabel = defaultRemindSummary,
            showPersistent = true,
        ) { list, persistent ->
            slots = slots.map { it.copy(remindMinutes = list.firstOrNull(), remindMinutesList = list) }
            remindPersistent = persistent
            remindDlg = false
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            stringResource(R.string.detail_editor_discard_title),
            stringResource(R.string.detail_editor_discard_desc),
            stringResource(R.string.detail_editor_discard),
            onConfirm = {
                if (!committed) {
                    committed = true
                    confirmDiscard = false
                    onBack()
                }
            },
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
    if (confirmDelete && origin != null) {
        ConfirmDialog(
            stringResource(R.string.delete_course_title, origin.name),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                if (!committed) {
                    committed = true
                    confirmDelete = false
                    val desc = ctx.getString(R.string.delete_desc, origin.name)
                    val changes =
                        buildList {
                            initialSlots.mapNotNull { it.base }.forEach { b ->
                                add(Change.Remove(b))
                                snap.exceptions.filter { it.deletedAt == null && it.courseId == b.id }.forEach { add(Change.Remove(it)) }
                            }
                        }
                    actions.commit(ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes), desc)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

/** 一条时段：植物色竖条 + 「周X 第A–B节 · 第N周」+ 等宽钟点 / 教室 + 右箭头。 */
@Composable
private fun SlotRow(
    slot: SlotDraft,
    color: Int,
    clock: String,
    showTeacher: Boolean = false,
    onOpen: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.65f))
            .mintClickable(pressedScale = 0.98f, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(colors.fg(color)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ctx.getString(
                    R.string.course_line,
                    ctx.weekdayShort(slot.dayOfWeek),
                    slot.startPeriod,
                    slot.endPeriod,
                    WeekSets.format(slot.weeks),
                ),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val parts =
                listOfNotNull(
                    clock.takeIf { it.isNotEmpty() },
                    ctx.formatRooms(slot.room).takeIf { it.isNotEmpty() },
                    slot.teacher?.trim()?.takeIf { showTeacher && it.isNotEmpty() },
                )
            val sub = parts.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 时段编辑抽屉：星期胶囊 → 单一 6×2 节次区间网格（一点起点、二点终点）→ 教室 → 5×4 周次网格。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlotEditorSheet(
    periods: List<PeriodTime>,
    totalWeeks: Int,
    slot: SlotDraft,
    onChange: (SlotDraft) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val n = periods.size
    var draft by remember(slot) { mutableStateOf(slot) }
    val clock = clockRange(periods, draft.startPeriod, draft.endPeriod, draft.startTime, draft.endTime)
    var rangeAnchor by remember(slot) { mutableStateOf<Int?>(null) }

    MintBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.course_slot_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )

            // 星期
            Text(
                stringResource(R.string.weekday_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 1..7) {
                    val on = draft.dayOfWeek == d
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(CircleShape)
                            .background(if (on) scheme.primary else scheme.surfaceContainerHighest)
                            .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = on) {
                                draft = draft.copy(dayOfWeek = d)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ctx.weekdayShort(d),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // 节次区间：单网格连选（第 1 下定单节/起点，第 2 下拉成区间）
            val periodSpanText =
                if (draft.startPeriod == draft.endPeriod) {
                    stringResource(R.string.period_n, draft.startPeriod)
                } else {
                    stringResource(R.string.periods_range, draft.startPeriod, draft.endPeriod)
                }
            val periodSummary = listOfNotNull(periodSpanText, clock.takeIf { it.isNotEmpty() }).joinToString(" · ")
            Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.field_periods),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                Text(
                    if (rangeAnchor != null) stringResource(R.string.course_period_range_hint) else periodSummary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            val pCols = 6
            val pRows = (n + pCols - 1) / pCols
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until pRows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until pCols) {
                            val p = r * pCols + c + 1
                            if (p <= n) {
                                val inRange = p in draft.startPeriod..draft.endPeriod
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (inRange) scheme.primary else scheme.surfaceContainerHighest)
                                        .mintClickable(pressedScale = 0.96f, role = Role.Button, selected = inRange) {
                                            val anchor = rangeAnchor
                                            if (anchor == null) {
                                                rangeAnchor = p
                                                draft = draft.copy(startPeriod = p, endPeriod = p, startTime = null, endTime = null)
                                            } else {
                                                rangeAnchor = null
                                                draft = draft.copy(startPeriod = minOf(anchor, p), endPeriod = maxOf(anchor, p), startTime = null, endTime = null)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$p",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        color = if (inRange) scheme.onPrimary else scheme.onSurfaceVariant,
                                        fontWeight = if (inRange) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 教室与教师
            Spacer(Modifier.height(10.dp))
            TonalField(
                label = stringResource(R.string.label_room),
                value = draft.room,
                onChange = { draft = draft.copy(room = it) },
            )
            Spacer(Modifier.height(10.dp))
            TonalField(
                label = stringResource(R.string.label_teacher),
                value = draft.teacher.orEmpty(),
                onChange = { draft = draft.copy(teacher = it) },
                placeholder = stringResource(R.string.course_teacher_placeholder),
            )

            // 周次（5 列矩阵：20 周正好 4×5 满行对齐）
            Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.week_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                if (draft.weeks.isNotEmpty()) {
                    Text(
                        stringResource(R.string.weeks_value, WeekSets.format(draft.weeks)),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            }
            ChipChoice(
                options =
                    listOf(
                        WeekSets.all(totalWeeks) to stringResource(R.string.weeks_all),
                        WeekSets.odd(totalWeeks) to stringResource(R.string.weeks_odd),
                        WeekSets.even(totalWeeks) to stringResource(R.string.weeks_even),
                        emptySet<Int>() to stringResource(R.string.course_weeks_clear),
                    ),
                selected = draft.weeks,
                onSelect = { draft = draft.copy(weeks = it) },
            )
            Spacer(Modifier.height(8.dp))
            val cols = 5
            val rows = (totalWeeks + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until cols) {
                            val w = r * cols + c + 1
                            if (w <= totalWeeks) {
                                val on = w in draft.weeks
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
                                        .mintClickable(pressedScale = 0.96f, role = Role.Checkbox, selected = on) {
                                            draft = draft.copy(weeks = if (on) draft.weeks - w else draft.weeks + w)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$w",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = draft.weeks.isNotEmpty(),
                onClick = {
                    onChange(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ok))
            }
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.course_slot_delete))
                }
            }
        }
    }
}

/** 8 组植物课表色：36dp 圆片，选中时描同色系深色边并落一个勾。 */
@Composable
fun ColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MintTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 8) {
            val sel = i == selected
            val ring by animateColorAsState(
                if (sel) colors.fg(i) else Color.Transparent,
                MintTheme.spec(),
                label = "color-ring",
            )
            val name = stringResource(R.string.color_n, i + 1)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = sel) { onSelect(i) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.bg(i))
                        .border(2.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) Icon(Icons.Rounded.Check, null, tint = colors.fg(i), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * 将结构化时段列表 [slots] 与原始时段 [initialSlots] 比对生成最小变更列表：
 * 保留分时段独立教室与提醒，并自动清理被移除周次或整条被删时段上的悬空 [app.goke.core.model.CourseException]。
 */
internal fun buildSlotChanges(
    semesterId: String,
    name: String,
    teacher: String,
    colorIndex: Int,
    note: String,
    initialSlots: List<SlotDraft>,
    slots: List<SlotDraft>,
    liveExceptions: List<app.goke.core.model.CourseException>,
    kind: EventKind = EventKind.NORMAL,
    remindPersistent: Boolean = false,
): List<Change> {
    val n = name.trim()
    val t = teacher.trim()
    val nt = note.trim()
    val changes = mutableListOf<Change>()
    val kept = mutableSetOf<String>()

    fun materializeSlot(s: SlotDraft): Course? {
        if (s.weeks.isEmpty()) return null
        val r = s.room.trim()
        val slotTeacher = s.teacher?.trim() ?: t
        val b = s.base
        return if (b != null) {
            b.copy(
                name = n,
                teacher = slotTeacher,
                room = r,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                weeks = s.weeks,
                colorIndex = colorIndex,
                remindMinutes = s.effectiveRemindMinutes.firstOrNull(),
                remindMinutesList = s.effectiveRemindMinutes,
                note = nt,
                kind = kind,
                remindPersistent = remindPersistent,
                startTime = s.startTime,
                endTime = s.endTime,
            )
        } else {
            Course(
                id = s.id,
                semesterId = semesterId,
                name = n,
                teacher = slotTeacher,
                room = r,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                weeks = s.weeks,
                colorIndex = colorIndex,
                remindMinutes = s.effectiveRemindMinutes.firstOrNull(),
                remindMinutesList = s.effectiveRemindMinutes,
                note = nt,
                source = Source.MANUAL,
                kind = kind,
                remindPersistent = remindPersistent,
                startTime = s.startTime,
                endTime = s.endTime,
            )
        }
    }

    slots.forEach { s ->
        val b = s.base
        val c = materializeSlot(s)
        if (b != null) kept += b.id
        when {
            c == null && b != null -> {
                changes += Change.Remove(b)
                liveExceptions.filter { it.deletedAt == null && it.courseId == b.id }.forEach { changes += Change.Remove(it) }
            }

            c != null && b == null -> {
                changes += Change.Add(c)
            }

            c != null && b != null && c.copy(updatedAt = b.updatedAt, deletedAt = b.deletedAt, userId = b.userId) != b -> {
                changes += Change.Modify(b, c)
            }
        }
        if (b != null && c != null) {
            val droppedWeeks = b.weeks - s.weeks
            if (droppedWeeks.isNotEmpty()) {
                liveExceptions
                    .filter { it.deletedAt == null && it.courseId == b.id && it.week in droppedWeeks }
                    .forEach { changes += Change.Remove(it) }
            }
        }
    }

    initialSlots.mapNotNull { it.base }.filterNot { it.id in kept }.forEach { b ->
        changes += Change.Remove(b)
        liveExceptions.filter { it.deletedAt == null && it.courseId == b.id }.forEach { changes += Change.Remove(it) }
    }
    return changes
}
