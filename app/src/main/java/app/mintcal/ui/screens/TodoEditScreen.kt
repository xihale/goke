package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.change.clashesOf
import app.mintcal.core.change.repeatInstances
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Tab
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.rememberNow
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 唯一的编辑页：新建和修改都是它。
 * 只有标题、备注（附件就在备注里，图片直接预览）、时间；其余收进「更多」。
 * 标题里识别到的日期时间就地高亮，点一下表示认错了。
 */
@Composable
fun TodoEditScreen(
    id: String?,
    dateArg: String? = null,
    startArg: String? = null,
    endArg: String? = null,
    textDraft: String? = null,
    courseArg: String? = null,
    extraDatesArg: String? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val now by rememberNow()
    val today = now.toLocalDate()
    val existing = remember(id) { id?.let { i -> snap.tasks.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val argDate = remember { dateArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val initStart = remember { startArg?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    val initEnd = remember { endArg?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    val initWhen =
        remember(existing) {
            existing?.let { t ->
                val b = t.blockStart
                when {
                    b != null -> WhenPick(b.toLocalDate(), b.toLocalTime(), t.blockMinutes ?: 60)
                    t.due != null -> WhenPick(t.due!!.toLocalDate(), null)
                    else -> null
                }
            } ?: initStart?.let { s ->
                WhenPick(
                    s.toLocalDate(),
                    s.toLocalTime(),
                    initEnd?.let {
                        java.time.Duration
                            .between(s, it)
                            .toMinutes()
                            .toInt()
                            .coerceIn(15, 24 * 60)
                    } ?: 60,
                )
            }
        }

    var field by remember { mutableStateOf(TextFieldValue(existing?.title ?: textDraft.orEmpty())) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var attachments by remember { mutableStateOf(existing?.attachments.orEmpty()) }
    var picked by remember { mutableStateOf(initWhen) }
    var cleared by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var freq by remember { mutableStateOf(existing?.repeat?.freq) }
    var until by remember { mutableStateOf(existing?.repeat?.until) }
    var listId by remember { mutableStateOf(existing?.listId) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 0) }
    var ignored by remember { mutableStateOf(setOf<String>()) }
    var timeSheet by remember { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) titleFocus.requestFocus() }

    val text = field.text
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val tokens = remember(parsed) { parsed?.tokens?.filter { it.kind == QuickTokenKind.DATE || it.kind == QuickTokenKind.TIME }.orEmpty() }
    val masked =
        remember(text, ignored) {
            var out = text
            ignored.forEach { ig -> if (ig.isNotEmpty()) out = out.replace(ig, "·".repeat(ig.length)) }
            out
        }
    val kept = remember(masked, now) { if (masked.isBlank()) null else QuickAdd.parse(masked, now) }
    val textWhen = remember(kept) { kept?.due?.let { WhenPick(it.toLocalDate(), if (kept.dueHasTime) it.toLocalTime() else null, 60) } }
    val whenPick =
        when {
            cleared -> null
            picked != null -> picked
            textWhen != null -> textWhen
            argDate != null -> WhenPick(argDate, null)
            else -> null
        }
    val ranges =
        remember(text, tokens, ignored) {
            if (text.isEmpty()) {
                emptyList()
            } else {
                tokens
                    .mapNotNull { t ->
                        val i = text.indexOf(t.text)
                        if (i < 0 || t.text in ignored) null else i until (i + t.text.length)
                    }.sortedBy { it.first }
            }
        }
    val nav = LocalNav.current
    // 时间块与课程 / 其它已排待办的重叠：重复规则一并展开，只报前几条
    val clashes =
        remember(whenPick?.date, whenPick?.start, whenPick?.minutes, freq, until, snap, existing?.id) {
            val w = whenPick
            if (w?.start == null) {
                emptyList()
            } else {
                clashesOf(snap, existing?.id, repeatInstances(w.date, freq, until), w.start, w.minutes)
            }
        }
    val title = text.trim()
    val desc = stringResource(if (existing == null) R.string.add_task_desc else R.string.edit_task_desc, title)

    fun commit() {
        if (title.isEmpty()) return
        val ms = System.currentTimeMillis()
        val base =
            existing ?: Task(
                Ids.next(),
                "",
                courseId = courseArg,
                dueBeforeNextClass = courseArg != null,
                sortOrder = (snap.tasks.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
        val w = whenPick
        val startAt = w?.start?.let { LocalDateTime.of(w.date, it) }
        val next =
            base.copy(
                title = title,
                note = note,
                attachments = attachments,
                listId = listId,
                priority = priority,
                blockStart = startAt,
                blockMinutes = if (startAt != null) w.minutes else null,
                due =
                    if (startAt != null) {
                        startAt.plusMinutes(w.minutes.toLong())
                    } else {
                        w?.date?.atTime(23, 59)
                    },
                dueHasTime = startAt != null,
                remindMinutes = remind,
                repeat = freq?.let { f -> (base.repeat?.takeIf { it.freq == f } ?: Recurrence(f)).copy(freq = f, until = until) },
            )
        // 周历上框选的其它日期：同一时间段各建一条（重复规则一致）
        val extras =
            extraDatesArg
                .orEmpty()
                .split(',')
                .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
                .filter { it != w?.date }
        val changes =
            listOf(Edits.save(snap, next, desc, ms)) +
                extras.mapNotNull { d ->
                    val s = w?.start?.let { LocalDateTime.of(d, it) } ?: return@mapNotNull null
                    val t2 =
                        next.copy(
                            id = Ids.next(),
                            blockStart = s,
                            due = s.plusMinutes(w.minutes.toLong()),
                            sortOrder = next.sortOrder + 1,
                        )
                    Edits.save(snap, t2, ctx.getString(R.string.add_task_desc, t2.title), ms)
                }
        actions.commit(ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes.flatMap { it.changes }), ctx.getString(R.string.saved_toast))
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            if (existing != null) {
                MintCheck(existing.done, { d -> toggle(actions, snap, existing, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, existing.title)) })
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
            }
            TextButton(onClick = { commit() }, enabled = title.isNotEmpty()) { Text(stringResource(R.string.save)) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            SmartTitle(field, { field = it }, ranges, titleFocus) { range ->
                ignored = ignored + text.substring(range.first, range.last + 1)
            }
            Spacer(Modifier.height(8.dp))
            WhenRow(whenPick, today) { timeSheet = true }
            if (clashes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    clashes.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    // 先保存再看：跳过去只是查看，别把刚改的丢了
                                    nav.focusDate = c.at.toLocalDate()
                                    nav.tab = Tab.TIMETABLE
                                    if (title.isNotEmpty()) commit() else onBack()
                                }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    if (c.course) R.string.clash_row_course else R.string.clash_row_task,
                                    c.title,
                                    c.at.hm(),
                                ),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (whenPick?.date == null || c.at.toLocalDate() != whenPick.date) {
                                Text(
                                    ctx.monthDayWeekday(c.at.toLocalDate()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            FlatField(note, stringResource(R.string.label_note), onChange = { note = it }, singleLine = false)
            Spacer(Modifier.height(6.dp))
            AttachmentEditor(attachments, onChange = { attachments = it }, onPasteText = { note = if (note.isBlank()) it else "$note\n$it" })
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showMore = !showMore }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.section_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val rot by animateFloatAsState(if (showMore) 180f else 0f, MintTheme.spec(), label = "more-rot")
                Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = rot })
            }
            AnimatedVisibility(
                visible = showMore,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_list), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipChoice(
                        listOf<Pair<String?, String>>(null to stringResource(R.string.none)) + snap.lists.map { it.id to it.name },
                        listId,
                        { listId = it },
                    )
                    Text(stringResource(R.string.label_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipChoice(
                        listOf(
                            0 to stringResource(R.string.priority_0),
                            1 to stringResource(R.string.priority_1),
                            2 to stringResource(R.string.priority_2),
                            3 to stringResource(R.string.priority_3),
                        ),
                        priority,
                        { priority = it },
                    )
                    if (existing != null) {
                        val subs = TaskLogic.subtasks(snap.tasks, existing.id)
                        if (subs.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                subs.forEach { s ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        MintCheck(s.done, { d -> toggle(actions, snap, s, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, s.title)) })
                                        Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        IconButton(onClick = {
                                            actions.commit(
                                                Edits.delete(snap, s, ctx.getString(R.string.delete_desc, s.title), System.currentTimeMillis()),
                                                ctx.getString(R.string.deleted_toast),
                                            )
                                        }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlatField(newSub, stringResource(R.string.add_subtask), onChange = { newSub = it })
                            TextButton(
                                enabled = newSub.isNotBlank(),
                                onClick = {
                                    val sub = Task(Ids.next(), newSub.trim(), parentId = existing.id, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: 0) + 1, listId = existing.listId)
                                    actions.commit(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, sub.title), System.currentTimeMillis()))
                                    newSub = ""
                                },
                            ) { Text(stringResource(R.string.add)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (timeSheet) {
        EventTimeSheet(
            initial = whenPick,
            remindInitial = remind,
            freqInitial = freq,
            untilInitial = until,
            defaultDate = whenPick?.date ?: argDate ?: today,
            onDismiss = { timeSheet = false },
            onPick = { r ->
                if (r == null) {
                    picked = null
                    cleared = true
                    remind = null
                    freq = null
                    until = null
                } else {
                    picked = r.whenPick
                    cleared = r.whenPick == null
                    remind = r.remind
                    freq = r.freq
                    until = r.until
                }
                timeSheet = false
            },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete_task_title),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                actions.commit(
                    Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.title), System.currentTimeMillis()),
                    ctx.getString(R.string.deleted_toast),
                )
                onBack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

/** 标题输入：识别到的日期时间就地染色，点一下取消识别（文字保留）。 */
@Composable
private fun SmartTitle(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    ranges: List<IntRange>,
    focus: FocusRequester,
    onCancelRange: (IntRange) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    var layoutState by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .pointerInput(ranges) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val layout = layoutState ?: return@awaitEachGesture
                            if (layout.layoutInput.text.isEmpty()) return@awaitEachGesture
                            val off = layout.getOffsetForPosition(down.position)
                            val hit = ranges.firstOrNull { off in it }
                            if (hit != null) {
                                down.consume()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelRange(hit)
                            }
                        }
                    }.padding(vertical = 12.dp),
            textStyle =
                MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
            cursorBrush = SolidColor(accent),
            visualTransformation = TokenPaint(ranges, accent),
            onTextLayout = { layoutState = it },
        )
        if (value.text.isEmpty()) {
            Text(
                stringResource(R.string.entry_hint),
                modifier = Modifier.padding(vertical = 12.dp),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

private class TokenPaint(
    private val ranges: List<IntRange>,
    private val color: androidx.compose.ui.graphics.Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled =
            buildAnnotatedString {
                append(text)
                ranges.forEach { r ->
                    if (r.first >= 0 && r.last < text.length) {
                        addStyle(
                            SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.12f)),
                            r.first,
                            r.last + 1,
                        )
                    }
                }
            }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is TokenPaint && other.ranges == ranges && other.color == color

    override fun hashCode(): Int = ranges.hashCode() * 31 + color.hashCode()
}

/** 「何时」一行：有时间就是占一段时间，只有日期就是这天要做，什么都没有就是收集箱。 */
@Composable
private fun WhenRow(
    whenPick: WhenPick?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val start = whenPick?.start
    val label =
        when {
            whenPick == null -> stringResource(R.string.add_date)
            start == null -> ctx.dueLabel(whenPick.date.atStartOfDay(), false, today)
            else -> "${ctx.dueLabel(whenPick.date.atTime(start), true, today)} – ${start.plusMinutes(whenPick.minutes.toLong()).hm()}"
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Schedule,
            null,
            tint = if (start != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (whenPick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FlatField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    var v by remember { mutableStateOf(TextFieldValue(value)) }
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        BasicTextField(
            value = v,
            onValueChange = {
                v = it
                onChange(it.text)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = singleLine,
        )
        if (v.text.isEmpty()) {
            Text(
                placeholder,
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
