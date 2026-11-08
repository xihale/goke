package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.change.clashesOf
import app.mintcal.core.focus.FocusLogic
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.recurrence.RecurrenceEngine
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.Tab
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.ConflictCard
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.rememberFileAttachmentPicker
import app.mintcal.ui.components.rememberImageAttachmentPicker
import app.mintcal.ui.dueLabel
import app.mintcal.ui.durationLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime

/**
 * 唯一的编辑页：新建和修改都是它。版式对齐 TickTick 详情：
 * 勾选在顶栏右侧，标题从左缘起，日期、备注、附件依次全宽铺在页面里。
 * 底部左侧是清单名，右侧是子任务、优先级、图片和附件；子任务点清单图标在栏上方展开。
 * 修改返回即保存（空标题等于放弃）；新建要点顶栏「确定」才落库，返回 / 手势退出等于丢弃草稿。
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
    noteDraft: String? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now by rememberNow()
    val today = now.toLocalDate()
    val existing = remember(id, snap.tasks) { id?.let { i -> snap.tasks.firstOrNull { it.id == i } } }
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
                    // 排入日历的时间块：开始 + 时长原样读回（精确到分）
                    b != null -> WhenPick(b.toLocalDate(), b.toLocalTime(), t.blockMinutes ?: settings.defaultBlockMinutes)

                    // 旧待办的具体截止时刻：不丢时分，持续留空保持待办
                    t.due != null -> WhenPick(t.due!!.toLocalDate(), t.due!!.toLocalTime().takeIf { t.dueHasTime })

                    else -> null
                }
            } ?: initStart?.let { s ->
                WhenPick(
                    s.toLocalDate(),
                    s.toLocalTime(),
                    initEnd?.let {
                        Duration
                            .between(s, it)
                            .toMinutes()
                            .toInt()
                            .coerceIn(1, 24 * 60)
                    } ?: settings.defaultBlockMinutes,
                )
            }
        }

    var field by remember { mutableStateOf(TextFieldValue(existing?.title ?: textDraft.orEmpty())) }
    var note by remember { mutableStateOf(existing?.note ?: noteDraft.orEmpty()) }
    var attachments by remember { mutableStateOf(existing?.attachments.orEmpty()) }
    var picked by remember { mutableStateOf(initWhen) }
    var cleared by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var persistent by remember { mutableStateOf(existing?.remindPersistent == true) }
    var freq by remember { mutableStateOf(existing?.repeat?.freq) }
    var interval by remember { mutableIntStateOf(existing?.repeat?.interval ?: 1) }
    var until by remember { mutableStateOf(existing?.repeat?.until) }
    var byDays by remember { mutableStateOf(existing?.repeat?.byDays ?: emptySet<Int>()) }
    var listId by remember { mutableStateOf(existing?.listId) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 0) }
    var ignored by remember { mutableStateOf(setOf<String>()) }
    var timeSheet by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }
    var subReset by remember { mutableIntStateOf(0) }
    var subsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) titleFocus.requestFocus() }
    // 附件：图片和文件都在工具栏上，点了直接选
    val pickImage = rememberImageAttachmentPicker { a -> attachments = attachments + a }
    val pickFile = rememberFileAttachmentPicker { a -> attachments = attachments + a }

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
    val textWhen =
        remember(kept) {
            kept?.due?.let { WhenPick(it.toLocalDate(), if (kept.dueHasTime) it.toLocalTime() else null) }
        }
    val whenPick =
        when {
            cleared -> null
            picked != null -> picked
            textWhen != null -> textWhen
            argDate != null -> WhenPick(argDate, null)
            else -> null
        }
    val initialWhen = remember { whenPick }
    val whenChanged =
        whenPick?.date != initialWhen?.date || whenPick?.start != initialWhen?.start || whenPick?.minutes != initialWhen?.minutes
    val hasChanges =
        // 新建时草稿就是内容本身：带着草稿进来点返回也该落成一条，不能当作「没改过」
        (existing == null && text.isNotBlank()) || text != existing?.title.orEmpty() || note != existing?.note.orEmpty() ||
            attachments != existing?.attachments.orEmpty() || listId != existing?.listId || priority != (existing?.priority ?: 0) ||
            remind != existing?.remindMinutes || persistent != (existing?.remindPersistent == true) || freq != existing?.repeat?.freq ||
            (freq != null && (interval != (existing?.repeat?.interval ?: 1) || until != existing?.repeat?.until || byDays != (existing?.repeat?.byDays ?: emptySet<Int>()))) ||
            whenChanged || newSub.isNotBlank()

    // 退出即保存：有内容且有改动就静默落盘；空标题等于放弃这条
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
    // 时间块与课程 / 其它已排待办的重叠：重复规则（含精确间隔）一并展开，只报前几条
    val clashInstances =
        remember(whenPick?.date, whenPick?.start, whenPick?.block, freq, interval, until) {
            val w = whenPick
            val f = freq
            val iv = interval.coerceAtLeast(1)
            when {
                w == null || w.start == null || !w.block -> emptyList()
                f == null -> listOf(w.date)
                else -> RecurrenceEngine.dates(Recurrence(f, iv, until = until), w.date, w.date, until ?: w.date.plusMonths(6))
            }
        }
    val clashes =
        remember(clashInstances, whenPick?.start, whenPick?.minutes, snap, existing?.id) {
            val ws = whenPick?.start
            val wm = whenPick?.minutes
            if (ws == null || wm == null || clashInstances.isEmpty()) emptyList() else clashesOf(snap, existing?.id, clashInstances, ws, wm)
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
        // 日程：blockStart + blockMinutes；待办带时刻：只写 due（带时分）；仅日期：当天 23:59、不带时间
        var blockStartAt: LocalDateTime? = null
        var dueAt: LocalDateTime? = null
        var blockMinutes: Int? = null
        var hasTime = false
        if (w != null) {
            val dur = w.minutes
            if (w.start != null) {
                val at = LocalDateTime.of(w.date, w.start)
                if (dur != null) {
                    blockStartAt = at
                    blockMinutes = dur
                    dueAt = at.plusMinutes(dur.toLong())
                } else {
                    dueAt = at
                }
                hasTime = true
            } else {
                dueAt = w.date.atTime(23, 59)
            }
        }
        val next =
            base.copy(
                title = title,
                note = note,
                attachments = attachments,
                listId = listId,
                priority = priority,
                blockStart = blockStartAt,
                blockMinutes = blockMinutes,
                due = dueAt,
                dueHasTime = hasTime,
                remindMinutes = remind,
                remindPersistent = remind != null && persistent,
                repeat =
                    freq?.let { f ->
                        (base.repeat?.takeIf { it.freq == f } ?: Recurrence(f)).copy(
                            freq = f,
                            interval = interval.coerceAtLeast(1),
                            byDays = if (f == Freq.WEEKLY) byDays else emptySet(),
                            until = until,
                        )
                    },
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
                    val ww = w
                    if (ww == null || !ww.block || ww.start == null) return@mapNotNull null
                    val dur = ww.minutes ?: return@mapNotNull null
                    val s = LocalDateTime.of(d, ww.start)
                    val t2 =
                        next.copy(
                            id = Ids.next(),
                            blockStart = s,
                            due = s.plusMinutes(dur.toLong()),
                            sortOrder = next.sortOrder + 1,
                        )
                    Edits.save(snap, t2, ctx.getString(R.string.add_task_desc, t2.title), ms)
                }
        val feedback =
            when {
                existing != null -> desc
                changes.size > 1 -> resources.getString(R.string.todo_batch_toast, changes.size)
                w != null -> resources.getString(R.string.detail_editor_added_date, ctx.dueLabel(w.date.atStartOfDay(), false, today))
                else -> resources.getString(R.string.detail_editor_added_inbox)
            }
        actions.commit(ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes.flatMap { it.changes }), feedback)
        onBack()
    }

    // 修改：返回即保存（有改动就落盘）；新建：只有点「确定」才落库，返回一律丢弃草稿
    fun requestBack() {
        if (existing != null && title.isNotEmpty() && hasChanges) commit() else onBack()
    }
    BackHandler { requestBack() }

    var pageMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            if (existing != null) {
                MintCheck(existing.done, { d -> toggle(actions, snap, existing, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, existing.title)) }, Modifier, 18.dp)
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
            } else {
                // 新建：明确一个「确定」键，返回就是丢弃
                TextButton(enabled = title.isNotEmpty(), onClick = { commit() }) { Text(stringResource(R.string.confirm)) }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SmartTitle(field, { field = it }, ranges, titleFocus, done = existing?.done == true) { range ->
                ignored = ignored + text.substring(range.first, range.last + 1)
            }
            WhenRow(whenPick, today, remind, freq, interval, until, byDays, onClear = {
                picked = null
                cleared = true
                remind = null
                freq = null
                until = null
                byDays = emptySet()
            }) { timeSheet = true }
            AnimatedVisibility(
                visible = clashes.isNotEmpty(),
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                ConflictCard(title = stringResource(R.string.conflict_title), modifier = Modifier.padding(top = 4.dp)) {
                    clashes.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    nav.focusDate = c.at.toLocalDate()
                                    nav.tab = Tab.TIMETABLE
                                    // 修改照旧落盘跳转；新建草稿没有「确定」不落库
                                    if (existing != null && title.isNotEmpty()) commit() else onBack()
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
                            )
                            Text(
                                ctx.monthDayWeekday(c.at.toLocalDate()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlatField(note, stringResource(R.string.label_note), onChange = { note = it }, singleLine = false)
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentEditor(attachments, onChange = { attachments = it })
            }
            if (existing != null) {
                Spacer(Modifier.height(24.dp))
                FocusSection(snap, existing.id, today)
            }
            Spacer(Modifier.height(24.dp))
        }
        if (existing != null) {
            val subs = TaskLogic.subtasks(snap.tasks, existing.id)
            AnimatedVisibility(
                visible = subsOpen,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    subs.forEach { s ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            MintCheck(s.done, { d -> toggle(actions, snap, s, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, s.title)) })
                            Text(
                                s.title,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (s.done) TextDecoration.LineThrough else null,
                                color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = {
                                actions.commit(
                                    Edits.delete(snap, s, ctx.getString(R.string.delete_desc, s.title), System.currentTimeMillis()),
                                    ctx.getString(R.string.delete_desc, s.title),
                                )
                            }) { Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        FlatField(newSub, stringResource(R.string.add_subtask), onChange = { newSub = it }, reset = subReset, modifier = Modifier.weight(1f))
                        if (newSub.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val sub = Task(Ids.next(), newSub.trim(), parentId = existing.id, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: 0) + 1, listId = existing.listId)
                                    actions.commit(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, sub.title), System.currentTimeMillis()))
                                    newSub = ""
                                    subReset++
                                },
                            ) { Text(stringResource(R.string.add)) }
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val currentList = snap.lists.firstOrNull { it.id == listId }
            val listTint = currentList?.let { MintTheme.colors.fg(it.colorIndex) } ?: MaterialTheme.colorScheme.outline
            Box {
                Row(
                    Modifier
                        .widthIn(max = 180.dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { listMenu = true }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(listTint))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        currentList?.name ?: stringResource(R.string.bucket_inbox),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(listMenu, { listMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bucket_inbox)) },
                        leadingIcon = {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outline))
                            }
                        },
                        trailingIcon = { if (listId == null) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                        onClick = {
                            listMenu = false
                            listId = null
                        },
                    )
                    snap.lists.sortedBy { it.sort }.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l.name) },
                            leadingIcon = {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(MintTheme.colors.fg(l.colorIndex)))
                                }
                            },
                            trailingIcon = { if (listId == l.id) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                            onClick = {
                                listMenu = false
                                listId = l.id
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (existing != null) {
                IconButton(onClick = { subsOpen = !subsOpen }) {
                    Icon(
                        Icons.Rounded.Checklist,
                        stringResource(R.string.add_subtask),
                        tint = if (subsOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            Box {
                IconButton(onClick = { priorityMenu = true }) {
                    Icon(Icons.Rounded.Flag, stringResource(R.string.label_priority), tint = priorityColor(priority))
                }
                DropdownMenu(priorityMenu, { priorityMenu = false }) {
                    listOf(3 to R.string.priority_3, 2 to R.string.priority_2, 1 to R.string.priority_1, 0 to R.string.priority_0).forEach { (v, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = priorityColor(v), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                priorityMenu = false
                                priority = v
                            },
                        )
                    }
                }
            }
            IconButton(onClick = pickImage) { Icon(Icons.Rounded.Image, stringResource(R.string.attach_image)) }
            IconButton(onClick = pickFile) { Icon(Icons.Rounded.UploadFile, stringResource(R.string.attach_file)) }
        }
    }

    if (timeSheet) {
        WhenPanel(
            initial = whenPick,
            defaultDate = whenPick?.date ?: argDate ?: today,
            defaultMinutes = settings.defaultBlockMinutes,
            initialRemind = remind,
            initialPersistent = persistent,
            initialFreq = freq,
            initialInterval = interval,
            initialUntil = until,
            initialByDays = byDays,
            semesterEnd = snap.currentSemester?.let { SemesterCalendar(it).weekEnd(it.totalWeeks) },
            onDismiss = { timeSheet = false },
            onPick = { r ->
                val wp = r.whenPick
                if (wp == null) {
                    picked = null
                    cleared = true
                    remind = null
                    persistent = false
                    freq = null
                    until = null
                    byDays = emptySet()
                } else {
                    picked = wp
                    cleared = false
                    remind = r.remindMinutes
                    persistent = r.remindPersistent
                    freq = r.freq
                    interval = r.interval
                    until = r.until
                    byDays = r.byDays
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
                    ctx.getString(R.string.delete_desc, existing.title),
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
    done: Boolean = false,
    onCancelRange: (IntRange) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    var layoutState by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
                MaterialTheme.typography.titleLarge.copy(
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
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
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
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

/** 一行「日期&重复」：时间 + 重复 + 提醒合成一句，点开同一个面板。 */
@Composable
private fun WhenRow(
    whenPick: WhenPick?,
    today: LocalDate,
    remind: Int?,
    freq: Freq?,
    interval: Int,
    until: LocalDate?,
    byDays: Set<Int>,
    onClear: () -> Unit,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val start = whenPick?.start
    val dateLabel =
        when {
            whenPick == null -> {
                null
            }

            start == null -> {
                ctx.dueLabel(whenPick.date.atStartOfDay(), false, today)
            }

            whenPick.minutes == null -> {
                "${ctx.dueLabel(whenPick.date.atTime(start), true, today)} · ${stringResource(R.string.detail_editor_deadline)}"
            }

            else -> {
                val dur = whenPick.minutes ?: 0
                val end = whenPick.end?.hm().orEmpty()
                val endLabel = if (start.hour * 60 + start.minute + dur >= 24 * 60) stringResource(R.string.time_next_day, end) else end
                "${ctx.dueLabel(whenPick.date.atTime(start), true, today)} – $endLabel"
            }
        }
    val extras =
        buildList {
            if (freq != null) add(freqLabel(freq, until, interval, byDays, whenPick?.date))
            if (remind != null) add(remindLabel(remind))
        }
    val empty = dateLabel == null && extras.isEmpty()
    val primaryLine =
        when {
            empty -> stringResource(R.string.date_and_repeat)
            dateLabel != null -> dateLabel
            else -> extras.joinToString(" · ")
        }
    val secondary = if (dateLabel != null && extras.isNotEmpty()) extras.joinToString(" · ") else null
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Schedule, null, tint = if (empty) muted else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    primaryLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (empty) muted else accent,
                    fontWeight = if (empty) FontWeight.Normal else FontWeight.Medium,
                )
                if (secondary != null) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
        }
        if (!empty) {
            Box(
                Modifier.width(36.dp).heightIn(min = 48.dp).clickable(onClick = onClear),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.detail_editor_clear_plan), tint = muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FlatField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    /** 变化时清空输入（如添加子任务后）。 */
    reset: Any? = null,
    modifier: Modifier = Modifier,
) {
    var v by remember(reset) { mutableStateOf(TextFieldValue(value)) }
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp)) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 这条待办的专注统计：累计 + 最近几段起止，入口「开始专注」与「查看全部」。 */
@Composable
private fun FocusSection(
    snap: Snapshot,
    taskId: String,
    today: LocalDate,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val sessions = FocusLogic.ofTask(snap.focusSessions.filter { it.deletedAt == null }, taskId)
    FormSection(stringResource(R.string.focus_editor_section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sessions.isEmpty()) {
                    stringResource(R.string.focus_empty)
                } else {
                    stringResource(R.string.focus_total_all, ctx.durationLabel(FocusLogic.totalMillis(sessions))) +
                        " · " + stringResource(R.string.focus_session_count, sessions.size)
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { nav.push(Route.Focus(taskId = taskId)) }) {
                Text(stringResource(R.string.focus_editor_start))
            }
        }
        if (sessions.isNotEmpty()) {
            val zone = ZoneId.systemDefault()
            sessions.take(8).forEach { s ->
                val day = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
                val startAt = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime()
                val endAt = Instant.ofEpochMilli(s.endedAt).atZone(zone).toLocalTime()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ctx.dueLabel(day.atStartOfDay(), false, today)} ${startAt.hm()}–${endAt.hm()}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        ctx.durationLabel(s.focusMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (sessions.size > 8) {
                TextButton(onClick = { nav.push(Route.Focus(taskId = taskId)) }) { Text(stringResource(R.string.focus_editor_more)) }
            }
        }
    }
}

/**
 * 优先级旗子颜色：走 MintTheme.colors 语义色（低灰绿 / 中金 / 高红，深浅主题各有一档），
 * 与待办行、批量菜单同源；0 档回退中性灰。
 */
@Composable
private fun priorityColor(p: Int): Color = MintTheme.colors.priorityColor(p) ?: MaterialTheme.colorScheme.outline
