package app.mintcal.ui.screens

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Conflicts
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.model.Course
import app.mintcal.core.model.PeriodTime
import app.mintcal.core.model.Source
import app.mintcal.core.time.WeekSets
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.ConflictCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.TonalField
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.formatRooms
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import java.time.LocalDateTime

/** 一条上课时段（对应一条 Course 记录）：同一门课可以有多个时段，各自带教室与提醒。 */
internal data class SlotDraft(
    val id: String,
    val base: Course?,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
    val room: String,
    val remindMinutes: Int?,
)

/** 时段在课表上的真实钟点，如 `08:30–10:05`；节次越界时留空。 */
private fun clockRange(
    periods: List<PeriodTime>,
    sp: Int,
    ep: Int,
): String {
    val start = periods.firstOrNull { it.index == sp }?.start?.hm() ?: return ""
    val end = periods.firstOrNull { it.index == ep }?.end?.hm() ?: return ""
    return "$start–$end"
}

/**
 * 课程编辑：三段式结构化表单（课程身份 → 上课时段 → 教师 / 提醒 / 备注 / 关联待办）。
 * 一门课的多条排课记录（同名称）合在这里，每个时段各自带教室；返回即整体保存为一个变更集。
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
    var teacher by remember { mutableStateOf(existing?.teacher.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorIndex ?: (snap.courses.size % 8)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    // ── 时段：同名记录即同一门课（与「另一天」的历史约定一致） ──
    val initialSlots =
        remember(id) {
            if (existing != null) {
                snap.courses
                    .filter { it.deletedAt == null && it.semesterId == sem.id && it.name == existing.name }
                    .sortedWith(compareBy({ it.weeks.minOrNull() ?: 99 }, { it.dayOfWeek }, { it.startPeriod }))
                    .map { SlotDraft(it.id, it, it.dayOfWeek, it.startPeriod, it.endPeriod, it.weeks, it.room, it.remindMinutes) }
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
                    ),
                )
            }
        }
    var slots by remember(id) { mutableStateOf(initialSlots) }
    var openSlot by remember { mutableStateOf<String?>(null) }
    var remindDlg by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) nameFocus.requestFocus() }

    val origin = existing

    fun materialize(s: SlotDraft): Course? {
        if (s.weeks.isEmpty()) return null
        val n = name.trim()
        val t = teacher.trim()
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
                remindMinutes = s.remindMinutes,
                note = nt,
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
                remindMinutes = s.remindMinutes,
                note = nt,
                source = Source.MANUAL,
            )
        }
    }

    fun addSlot() {
        val last = slots.lastOrNull()
        val sp = (last?.startPeriod ?: 1).coerceIn(1, periodCount)
        val ep = (last?.endPeriod ?: (sp + 1).coerceAtMost(periodCount)).coerceIn(sp, periodCount)
        val draft =
            SlotDraft(
                id = Ids.next(),
                base = null,
                dayOfWeek = last?.dayOfWeek ?: (initialDow ?: 1).coerceIn(1, 7),
                startPeriod = sp,
                endPeriod = ep,
                weeks = last?.weeks ?: WeekSets.all(totalWeeks),
                room = last?.room.orEmpty(),
                remindMinutes = last?.remindMinutes,
            )
        slots = slots + draft
        openSlot = draft.id
    }

    val valid =
        name.isNotBlank() && slots.isNotEmpty() &&
            slots.all { it.weeks.isNotEmpty() && it.endPeriod >= it.startPeriod }
    val fieldDirty =
        if (origin == null) {
            name.isNotBlank() || teacher.isNotBlank() || note.isNotBlank()
        } else {
            name.trim() != origin.name || teacher.trim() != origin.teacher ||
                color != origin.colorIndex || note.trim() != origin.note
        }
    val dirty = fieldDirty || slots != initialSlots

    val preview =
        remember(snap, valid, dirty, name, teacher, color, note, slots) {
            if (!valid || !dirty) {
                null
            } else {
                val list = slots.mapNotNull { materialize(it) }
                if (list.isEmpty()) {
                    null
                } else {
                    val synthetic = snap.apply(Edits.saveAll(snap, list, "", 0), 0)
                    list
                        .flatMap { c -> Conflicts.detect(synthetic, setOf(c.id)) }
                        .distinctBy { listOf(it.aTitle, it.bTitle, it.start) }
                }
            }
        }

    fun persist(leave: Boolean) {
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
            )
        if (changes.isEmpty()) {
            if (leave) onBack()
            return
        }
        val desc = ctx.getString(if (origin == null) R.string.add_course_desc else R.string.edit_course_desc, name.trim())
        actions.commit(
            ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes),
            desc.takeIf { changes.any { c -> c is Change.Remove } },
        )
        if (leave) onBack()
    }

    fun requestBack() {
        when {
            name.isBlank() -> onBack()

            !valid -> actions.toast(ctx.getString(R.string.weeks_invalid))

            // 新建课程：返回不落库，改过内容先确认放弃；修改照旧自动保存
            origin == null && dirty -> confirmDiscard = true

            dirty -> persist(leave = true)

            else -> onBack()
        }
    }
    BackHandler { requestBack() }

    MintScreen(
        title = origin?.name ?: stringResource(R.string.new_course),
        onBack = { requestBack() },
        actions = {
            if (origin == null) {
                // 新建：明确一个「确定」键；返回 = 放弃（改过的内容会先确认）
                TextButton(enabled = valid, onClick = { persist(leave = true) }) {
                    Text(stringResource(R.string.confirm))
                }
            } else {
                Box {
                    IconButton(onClick = { pageMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(pageMenu, { pageMenu = false }) {
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
            // ── 第一段：课程身份与植物色 ──
            val nameHint = stringResource(R.string.course_name_placeholder)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .focusRequester(nameFocus)
                        .semantics { contentDescription = nameHint }
                        .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                nameHint,
                                modifier = Modifier.clearAndSetSemantics {},
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            ColorPicker(color) { color = it }

            // ── 第二段：上课时段 ──
            SectionLabel(stringResource(R.string.course_slots_section), modifier = Modifier.padding(top = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val deletable = slots.size > 1
                slots.forEach { s ->
                    SlotRow(
                        slot = s,
                        color = color,
                        clock = clockRange(periods, s.startPeriod, s.endPeriod),
                        onOpen = { openSlot = s.id },
                        onDelete = if (deletable) ({ slots = slots.filterNot { it.id == s.id } }) else null,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                        .mintClickable(pressedScale = 0.98f) { addSlot() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.course_add_slot),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(
                visible = !preview.isNullOrEmpty(),
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                ConflictCard(title = stringResource(R.string.conflict_title), modifier = Modifier.padding(top = 8.dp)) {
                    preview.orEmpty().take(4).forEach {
                        Text(
                            stringResource(R.string.conflict_line, it.aTitle, it.bTitle, it.start.toLocalDate().toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // ── 第三段：教师、提醒、备注与关联待办 ──
            Spacer(Modifier.height(16.dp))
            TonalField(
                label = stringResource(R.string.label_teacher),
                value = teacher,
                onChange = { teacher = it },
                placeholder = stringResource(R.string.course_teacher_placeholder),
            )
            val remind = slots.firstOrNull()?.remindMinutes
            ValueRow(
                label = stringResource(R.string.label_remind),
                value = remindLabel(remind),
                onClick = { remindDlg = true },
                valueColor = if (remind != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TonalField(
                label = stringResource(R.string.label_note),
                value = note,
                onChange = { note = it },
                singleLine = false,
                minLines = 2,
                placeholder = stringResource(R.string.course_note_placeholder),
            )

            if (origin != null) {
                val related =
                    snap.tasks.filter { t ->
                        t.deletedAt == null && t.parentId == null && !t.done && t.courseId != null &&
                            (t.courseId == origin.id || snap.courses.firstOrNull { it.id == t.courseId }?.name == origin.name)
                    }
                SectionLabel(stringResource(R.string.course_todos), modifier = Modifier.padding(top = 12.dp))
                if (related.isEmpty()) {
                    Text(
                        stringResource(R.string.course_todos_empty),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                related.forEach { t -> TodoLine(t, snap, LocalDateTime.now(), Modifier.padding(vertical = 3.dp)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .mintClickable(pressedScale = 0.98f) { nav.push(Route.TodoEdit(courseId = origin.id)) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.course_todos_add),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    openSlot?.let { key ->
        val slot = slots.firstOrNull { it.id == key }
        if (slot == null) {
            openSlot = null
        } else {
            SlotEditorSheet(
                periods = periods,
                totalWeeks = totalWeeks,
                slot = slot,
                onChange = { updated -> slots = slots.map { if (it.id == key) updated else it } },
                onDismiss = { openSlot = null },
            )
        }
    }

    if (remindDlg) {
        RemindPickDialog(
            initial = slots.firstOrNull()?.remindMinutes,
            hasTime = true,
            initialPersistent = false,
            onDismiss = { remindDlg = false },
        ) { minutes, _ ->
            slots = slots.map { it.copy(remindMinutes = minutes) }
            remindDlg = false
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            stringResource(R.string.detail_editor_discard_title),
            stringResource(R.string.detail_editor_discard_desc),
            stringResource(R.string.detail_editor_discard),
            onConfirm = { onBack() },
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
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

/** 一条时段：色条 + 「周X 第A–B节 · 第N周」+ 钟点 / 教室；点开编辑，右侧可整条删除。 */
@Composable
private fun SlotRow(
    slot: SlotDraft,
    color: Int,
    clock: String,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MintTheme.card)
            .mintClickable(pressedScale = 0.98f, onClick = onOpen)
            .padding(start = 12.dp, end = if (onDelete != null) 2.dp else 12.dp, top = 10.dp, bottom = 10.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val parts =
                listOfNotNull(
                    clock.takeIf { it.isNotEmpty() },
                    ctx.formatRooms(slot.room).takeIf { it.isNotEmpty() },
                )
            val sub = parts.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.course_pattern_delete),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 时段编辑：星期、节次区间、教室、周次（预设 + 周号网格）。改动即时回写。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotEditorSheet(
    periods: List<PeriodTime>,
    totalWeeks: Int,
    slot: SlotDraft,
    onChange: (SlotDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val n = periods.size
    val clock = clockRange(periods, slot.startPeriod, slot.endPeriod)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 1..7) {
                    val on = slot.dayOfWeek == d
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
                            .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = on) {
                                onChange(slot.copy(dayOfWeek = d))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ctx.weekdayShort(d),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // 节次区间
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.field_periods),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (clock.isNotEmpty()) {
                    Text(
                        clock,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.start_period),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in 1..n) {
                    PeriodChip("$p", p == slot.startPeriod) {
                        onChange(slot.copy(startPeriod = p, endPeriod = slot.endPeriod.coerceAtLeast(p)))
                    }
                }
            }
            Text(
                stringResource(R.string.course_end_period),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in slot.startPeriod..n) {
                    PeriodChip("$p", p == slot.endPeriod) { onChange(slot.copy(endPeriod = p)) }
                }
            }

            // 教室
            Spacer(Modifier.height(8.dp))
            TonalField(
                label = stringResource(R.string.label_room),
                value = slot.room,
                onChange = { onChange(slot.copy(room = it)) },
                placeholder = stringResource(R.string.course_slot_room_hint),
            )

            // 周次
            Text(
                stringResource(R.string.week_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 6.dp),
            )
            ChipChoice(
                options =
                    listOf(
                        WeekSets.all(totalWeeks) to stringResource(R.string.weeks_all),
                        WeekSets.odd(totalWeeks) to stringResource(R.string.weeks_odd),
                        WeekSets.even(totalWeeks) to stringResource(R.string.weeks_even),
                        emptySet<Int>() to stringResource(R.string.course_weeks_clear),
                    ),
                selected = slot.weeks,
                onSelect = { onChange(slot.copy(weeks = it)) },
            )
            Spacer(Modifier.height(10.dp))
            val cols = 6
            val rows = (totalWeeks + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until cols) {
                            val w = r * cols + c + 1
                            if (w <= totalWeeks) {
                                val on = w in slot.weeks
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
                                        .mintClickable(pressedScale = 0.96f, role = Role.Checkbox, selected = on) {
                                            onChange(slot.copy(weeks = if (on) slot.weeks - w else slot.weeks + w))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$w",
                                        style = MaterialTheme.typography.labelMedium,
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
                enabled = slot.weeks.isNotEmpty(),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}

@Composable
private fun PeriodChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = on, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
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
 * 保留分时段独立教室与提醒，并自动清理被移除周次或整条被删时段上的悬空 [app.mintcal.core.model.CourseException]。
 */
internal fun buildSlotChanges(
    semesterId: String,
    name: String,
    teacher: String,
    colorIndex: Int,
    note: String,
    initialSlots: List<SlotDraft>,
    slots: List<SlotDraft>,
    liveExceptions: List<app.mintcal.core.model.CourseException>,
): List<Change> {
    val n = name.trim()
    val t = teacher.trim()
    val nt = note.trim()
    val changes = mutableListOf<Change>()
    val kept = mutableSetOf<String>()

    fun materializeSlot(s: SlotDraft): Course? {
        if (s.weeks.isEmpty()) return null
        val r = s.room.trim()
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
                colorIndex = colorIndex,
                remindMinutes = s.remindMinutes,
                note = nt,
            )
        } else {
            Course(
                id = s.id,
                semesterId = semesterId,
                name = n,
                teacher = t,
                room = r,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                weeks = s.weeks,
                colorIndex = colorIndex,
                remindMinutes = s.remindMinutes,
                note = nt,
                source = Source.MANUAL,
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
