package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickBatchItem
import app.mintcal.core.todo.QuickToken
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.SegmentedPill
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 工具栏就地展开的选择面板（不弹浮层，键盘不动）。 */
private enum class QuickPanel { PRIORITY, LIST }

/** 优先级档位的快捷语法文字：`!高` / `!中` / `!低`，与 [QuickAdd] 的解析口径一致。 */
private fun priorityToken(v: Int): Int =
    when (v) {
        3 -> R.string.priority_3
        2 -> R.string.priority_2
        else -> R.string.priority_1
    }

/**
 * 快速添加：右下角加号打开，输入区贴在键盘上方。
 * 点「确定」（或键盘完成键）才落库；点空白 / 返回收起即丢弃草稿。
 * 日期、优先级、清单在工具条上；详细编辑、粘贴、课程收进更多。
 * [anchorDate] 不写进输入框，只在没写日期时作为这一天的默认落点。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddSheet(
    snap: Snapshot,
    now: LocalDateTime,
    listFilter: String?,
    defaultBucket: Bucket?,
    focusSignal: Int,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorDate: LocalDate? = null,
    acceptShare: Boolean = true,
) {
    val nav = LocalNav.current
    val pending = nav.pendingAddText
    var batch by remember { mutableStateOf<List<QuickBatchItem>?>(null) }

    LaunchedEffect(pending) {
        if (acceptShare && pending != null) {
            nav.pendingAddText = null
            val items = QuickAdd.parseLines(pending, now)
            if (items.size > 1) {
                batch = items
            }
        }
    }

    batch?.let { items ->
        val actions = LocalActions.current
        val ctx = LocalContext.current
        BatchPreviewDialog(items, snap, now, onDismiss = { batch = null }) {
            val placed = placeOnAnchor(items, anchorDate, defaultBucket)
            val first = placed.first().result
            val desc = if (placed.size == 1) ctx.getString(R.string.add_task_desc, first.title) else ctx.getString(R.string.todo_batch_desc, placed.size)
            val msg = if (placed.size == 1) ctx.getString(R.string.task_added_toast, first.title) else ctx.getString(R.string.todo_batch_toast, placed.size)
            actions.commit(buildQuickTasks(snap, placed, listFilter, defaultBucket, now, desc), msg, undoable = placed.size > 1)
            batch = null
        }
    }

    // 未展开时真正早退：跳过内部 UI、BackHandler 与焦点逻辑，
    // 避免今日/日历/待办 3 个 Tab 重复空转与常驻监听。
    if (!visible) return

    val ctx = LocalContext.current
    val actions = LocalActions.current
    var text by rememberSaveable { mutableStateOf("") }
    // 工具栏的优先级 / 清单同样就地展开，不弹浮层（浮层在键盘上方会留一条空、还遮住输入）
    var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
    // 日期面板：日期/时间段 + 提醒 + 重复一次选好；设了就压过文本里识别出的日期
    var whenPanel by remember { mutableStateOf(false) }
    var panelWhen by remember { mutableStateOf<WhenPick?>(null) }
    var panelRemind by remember { mutableStateOf<Int?>(null) }
    var panelPersistent by remember { mutableStateOf(false) }
    var panelFreq by remember { mutableStateOf<Freq?>(null) }
    var panelInterval by remember { mutableIntStateOf(1) }
    var panelUntil by remember { mutableStateOf<LocalDate?>(null) }
    var panelByDays by remember { mutableStateOf(emptySet<Int>()) }
    var panelByMonths by remember { mutableStateOf(emptySet<Int>()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val settings = settingsState()
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }

    // 只有可见时向外报状态
    DisposableEffect(Unit) {
        nav.composerOpen = true
        onDispose { nav.composerOpen = false }
    }
    val focus = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val tokens = parsed?.tokens.orEmpty()
    val highlight =
        remember(tokens, scheme.primary, scheme.error, scheme.tertiary, scheme.secondary, colors) {
            if (tokens.isEmpty()) {
                VisualTransformation.None
            } else {
                val prioColor = colors.priorityColor(parsed?.priority ?: 3) ?: scheme.error
                TokenHighlight(tokens, scheme.primary, prioColor, scheme.tertiary, scheme.secondary)
            }
        }

    LaunchedEffect(focusSignal) { if (focusSignal > 0) focus.requestFocus() }
    // 打开即弹键盘。日期不写进输入框，避免「今天」占掉标题。
    LaunchedEffect(Unit) { focus.requestFocus() }

    /** 去掉某类片段后（可选）追加一个新片段，用于快捷条点按。 */
    fun rewrite(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        val removeRanges =
            tokenRanges(text, parsed?.tokens.orEmpty())
                .filter { it.first.kind in kinds }
                .map { it.second }
                .sortedByDescending { it.first }
        var t = text
        for (r in removeRanges) {
            t = t.substring(0, r.first) + " " + t.substring(r.last + 1)
        }
        t = t.replace(Regex("\\s+"), " ").trim()
        text =
            if (add == null) {
                t
            } else if (t.isEmpty()) {
                "$add "
            } else {
                "$t $add"
            }
        focus.requestFocus()
    }

    fun commitItems(items: List<QuickBatchItem>) {
        if (items.isEmpty()) return
        val placed = placeOnAnchor(items, anchorDate, defaultBucket)
        val first = placed.first().result
        val desc = if (placed.size == 1) ctx.getString(R.string.add_task_desc, first.title) else ctx.getString(R.string.todo_batch_desc, placed.size)
        val where =
            first.due
                ?.takeIf { placed.size == 1 }
                ?.let { " · " + ctx.dueLabel(it, first.dueHasTime, now.toLocalDate()) }
                .orEmpty()
        val msg = if (placed.size == 1) ctx.getString(R.string.task_added_toast, first.title) + where else ctx.getString(R.string.todo_batch_toast, placed.size)
        val ov =
            if (panelWhen != null || panelRemind != null || panelFreq != null) {
                QuickOverride(
                    whenPick = panelWhen,
                    remind = panelRemind,
                    persistent = panelPersistent,
                    repeat =
                        panelFreq?.let { f ->
                            Recurrence(
                                freq = f,
                                interval = panelInterval.coerceAtLeast(1),
                                byDays = if (f == Freq.DAILY) emptySet() else panelByDays,
                                until = panelUntil,
                                byMonths = if (f == Freq.YEARLY) panelByMonths else emptySet(),
                            )
                        },
                )
            } else {
                null
            }
        actions.commit(buildQuickTasks(snap, placed, listFilter, defaultBucket, now, desc, ov), msg, undoable = placed.size > 1)
    }

    fun clearDraft() {
        text = ""
        panelWhen = null
        panelRemind = null
        panelPersistent = false
        panelFreq = null
        panelInterval = 1
        panelUntil = null
        panelByDays = emptySet()
        panelByMonths = emptySet()
    }

    /** 收起即丢弃：没点「确定」的草稿不落库。 */
    fun discard() {
        clearDraft()
        confirmDiscard = false
        onDismiss()
    }

    val hasDraft = text.isNotBlank() || panelWhen != null || panelRemind != null || panelFreq != null

    fun requestDiscard() {
        if (hasDraft) confirmDiscard = true else discard()
    }

    // 返回 = 空白直接收起，有草稿先确认放弃
    BackHandler(enabled = true) { requestDiscard() }

    /** 点「确定」（或键盘完成键）：写了内容就落库，没写就是取消。 */
    fun finish() {
        if (parsed == null) {
            onDismiss()
            return
        }
        commitItems(listOf(QuickBatchItem(parsed, null)))
        clearDraft()
        onDismiss()
    }

    fun openDetail() {
        val draft = text.ifBlank { null }
        val wp = panelWhen
        val fallbackDate =
            anchorDate ?: defaultBucket?.let { b ->
                when (val d = TaskLogic.dropDate(b, now.toLocalDate())) {
                    null, TaskLogic.NO_DROP -> if (b == Bucket.TODAY) now.toLocalDate() else null
                    else -> d
                }
            }
        val dateArg = (wp?.date ?: if (parsed?.due == null) fallbackDate else null)?.toString()
        val startArg = wp?.start?.let { LocalDateTime.of(wp.date, it).toString() }
        val endArg = wp?.let { w -> if (w.start != null && w.minutes != null) LocalDateTime.of(w.date, w.start).plusMinutes(w.minutes.toLong()).toString() else null }
        val resolvedListId = parsed?.listName?.let { n -> snap.lists.firstOrNull { it.name == n }?.id } ?: listFilter
        val remindArg = panelRemind
        val persistentArg = panelPersistent && panelRemind != null
        val freqArg = panelFreq?.name
        val intervalArg = panelInterval.coerceAtLeast(1)
        val untilArg = if (panelFreq == null) null else panelUntil?.toString()
        val byDaysArg =
            panelByDays
                .takeIf { it.isNotEmpty() && (panelFreq == Freq.WEEKLY || panelFreq == Freq.MONTHLY || panelFreq == Freq.YEARLY) }
                ?.sorted()
                ?.joinToString(",")
        val byMonthsArg =
            panelByMonths
                .takeIf { it.isNotEmpty() && panelFreq == Freq.YEARLY }
                ?.sorted()
                ?.joinToString(",")
        // 草稿移交编辑页：浮层清空收起，回来不残留旧文字与面板状态
        clearDraft()
        onDismiss()
        nav.push(
            Route.TodoEdit(
                text = draft,
                date = dateArg,
                start = startArg,
                end = endArg,
                listId = resolvedListId,
                remindMinutes = remindArg,
                remindPersistent = persistentArg,
                repeatFreq = freqArg,
                repeatInterval = intervalArg,
                repeatUntil = untilArg,
                repeatByDays = byDaysArg,
                repeatByMonths = byMonthsArg,
            ),
        )
    }
    val daySet = parsed?.due != null || defaultBucket != null || anchorDate != null || panelWhen != null
    val panelWhenLabel =
        panelWhen?.let { wp ->
            val base = ctx.dueLabel(wp.date.atStartOfDay(), false, now.toLocalDate())
            when {
                wp.start == null -> base
                wp.block -> "$base ${wp.start.hm()}–${wp.end?.hm().orEmpty()}"
                else -> "$base ${wp.start.hm()}"
            }
        }

    Box(modifier.fillMaxSize()) {
        // 遮罩：点空白 = 空白直接收起，有草稿先确认放弃。压浅一点，列表还看得见。
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { requestDiscard() } },
        )
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
                // 标题：详细编辑同款素面大字输入
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .padding(vertical = 12.dp),
                        textStyle =
                            MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Normal,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        visualTransformation = highlight,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish() }),
                    )
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.quick_add_prompt),
                            Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = toolPanel == QuickPanel.PRIORITY,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    SegmentedPill(
                        options =
                            listOf(
                                3 to stringResource(R.string.priority_3),
                                2 to stringResource(R.string.priority_2),
                                1 to stringResource(R.string.priority_1),
                                0 to stringResource(R.string.priority_0),
                            ),
                        selected = parsed?.priority ?: 0,
                        onSelect = { v ->
                            rewrite(setOf(QuickTokenKind.PRIORITY), if (v == 0) null else "!" + ctx.getString(priorityToken(v)))
                            toolPanel = null
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                AnimatedVisibility(
                    visible = toolPanel == QuickPanel.LIST,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        snap.lists.sortedBy { it.sort }.forEach { l ->
                            ChipPill("#" + l.name, on = parsed?.listName == l.name) {
                                rewrite(setOf(QuickTokenKind.LIST), "#" + l.name)
                                toolPanel = null
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { whenPanel = true }) {
                        Icon(
                            Icons.Rounded.Event,
                            stringResource(R.string.detail_editor_pick_date),
                            tint = if (daySet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { toolPanel = if (toolPanel == QuickPanel.PRIORITY) null else QuickPanel.PRIORITY }) {
                        Icon(
                            Icons.Rounded.Flag,
                            stringResource(R.string.label_priority),
                            tint = colors.priorityColor(parsed?.priority ?: 0) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 清单按钮常驻：还没有自定义清单时点它插入 #，现场输名字即建清单
                    IconButton(onClick = {
                        if (snap.lists.isEmpty()) {
                            rewrite(emptySet(), "#")
                        } else {
                            toolPanel = if (toolPanel == QuickPanel.LIST) null else QuickPanel.LIST
                        }
                    }) {
                        Icon(Icons.Rounded.Inbox, stringResource(R.string.label_list), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { openDetail() }) {
                        Icon(Icons.Rounded.OpenInFull, stringResource(R.string.detail_editor_full_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 确定：点了才落库；没写内容置灰
                    val canAdd = parsed != null
                    val confirmLabel = stringResource(R.string.confirm)
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .mintClickable(enabled = canAdd, pressedScale = 0.96f, role = Role.Button) { finish() }
                                .semantics { contentDescription = confirmLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                tint =
                                    if (canAdd) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = panelWhenLabel != null || panelRemind != null || panelFreq != null,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    FlowRow(
                        Modifier.padding(end = 6.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (panelWhenLabel != null) {
                            val removeWhenDesc = stringResource(R.string.quick_add_remove_time, panelWhenLabel)
                            Tag(
                                "$panelWhenLabel ×",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .mintClickable(pressedScale = 0.96f, role = Role.Button) { panelWhen = null }
                                    .semantics {
                                        contentDescription = removeWhenDesc
                                    },
                            )
                        }
                        if (panelRemind != null) {
                            val rLabel = remindLabel(panelRemind)
                            val removeRemindDesc = stringResource(R.string.quick_add_remove_remind, rLabel)
                            Tag(
                                "$rLabel ×",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .mintClickable(pressedScale = 0.96f, role = Role.Button) { panelRemind = null }
                                    .semantics {
                                        contentDescription = removeRemindDesc
                                    },
                            )
                        }
                        if (panelFreq != null) {
                            val fLabel = freqLabel(panelFreq, panelUntil, panelInterval, panelByDays, panelWhen?.date, byMonths = panelByMonths)
                            val removeRepeatDesc = stringResource(R.string.quick_add_remove_repeat, fLabel)
                            Tag(
                                "$fLabel ×",
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                                        panelFreq = null
                                        panelUntil = null
                                        panelByDays = emptySet()
                                        panelByMonths = emptySet()
                                    }.semantics {
                                        contentDescription = removeRepeatDesc
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    if (whenPanel) {
        WhenPanel(
            initial =
                panelWhen ?: parsed?.due?.let { d ->
                    WhenPick(d.toLocalDate(), if (parsed.dueHasTime) d.toLocalTime() else null)
                },
            defaultDate = now.toLocalDate(),
            defaultMinutes = settings.defaultBlockMinutes,
            initialRemind = panelRemind,
            initialPersistent = panelPersistent,
            initialFreq = panelFreq,
            initialInterval = panelInterval,
            initialUntil = panelUntil,
            initialByDays = panelByDays,
            initialByMonths = panelByMonths,
            semesterEnd = snap.currentSemester?.let { SemesterCalendar(it).weekEnd(it.totalWeeks) },
            onDismiss = { whenPanel = false },
            onPick = { r ->
                panelWhen = r.whenPick
                panelRemind = r.remindMinutes
                panelPersistent = r.remindPersistent
                panelFreq = r.freq
                panelInterval = r.interval
                panelUntil = r.until
                panelByDays = r.byDays
                panelByMonths = r.byMonths
                whenPanel = false
            },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.detail_editor_discard_title),
            text = stringResource(R.string.detail_editor_discard_desc),
            confirm = stringResource(R.string.detail_editor_discard),
            onConfirm = { discard() },
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
}

/** 按文本中非重叠位置匹配解析出的 token 区间，避免替换/高亮误伤同名子串。 */
internal fun tokenRanges(
    text: String,
    tokens: List<QuickToken>,
): List<Pair<QuickToken, IntRange>> {
    if (text.isEmpty() || tokens.isEmpty()) return emptyList()
    val used = BooleanArray(text.length)
    return tokens.mapNotNull { token ->
        if (token.text.isEmpty()) return@mapNotNull null
        var from = 0
        var found: IntRange? = null
        while (from <= text.length - token.text.length) {
            val idx = text.indexOf(token.text, startIndex = from)
            if (idx < 0) break
            val end = idx + token.text.length
            val overlaps = (idx until end).any { used[it] }
            if (!overlaps) {
                for (k in idx until end) used[k] = true
                found = idx until end
                break
            }
            from = idx + 1
        }
        found?.let { token to it }
    }
}

/** 没写日期、也没有分区落点时，把任务放到 [anchor] 当天（只有日期，没有钟点）。 */
private fun placeOnAnchor(
    items: List<QuickBatchItem>,
    anchor: LocalDate?,
    bucket: Bucket?,
): List<QuickBatchItem> {
    val day = anchor ?: return items
    if (bucket != null) return items
    return items.map { item ->
        val p = item.result
        if (p.due != null || p.courseName != null || item.parentIndex != null) {
            item
        } else {
            item.copy(result = p.copy(due = LocalDateTime.of(day, LocalTime.of(23, 59)), dueHasTime = false))
        }
    }
}

/** 多行文字的批量添加预览：缩进显示父子关系，确认后一次性添加（一次撤销）。 */
@Composable
private fun BatchPreviewDialog(
    items: List<QuickBatchItem>,
    snap: Snapshot,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_batch_title, items.size)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(items.size) { i ->
                    val item = items[i]
                    val p = item.result
                    val meta =
                        buildList {
                            p.due?.let { d -> add(ctx.dueLabel(d, p.dueHasTime, now.toLocalDate())) }
                            p.courseName?.let { c -> add(matchCourse(snap, c)?.name ?: c) }
                            p.listName?.let { l -> add("#$l") }
                            p.tags.forEach { t -> add("@$t") }
                            if (p.priority > 0) add("!" + ctx.getString(listOf(R.string.priority_1, R.string.priority_2, R.string.priority_3)[p.priority - 1]))
                        }
                    Column(Modifier.padding(start = if (item.parentIndex != null) 20.dp else 0.dp, top = 4.dp, bottom = 4.dp)) {
                        Text((if (item.parentIndex != null) "↳ " else "") + p.title, style = MaterialTheme.typography.bodyLarge)
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.todo_batch_add_all)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 把解析出的日期 / 课程 / 清单 / 优先级 / 标签片段在输入框里着色（不改文字与光标位置）。 */
internal class TokenHighlight(
    val tokens: List<QuickToken>,
    val primary: Color,
    val priorityColor: Color,
    val tertiary: Color,
    val secondary: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (tokens.isEmpty() || raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(raw)
        for ((t, r) in tokenRanges(raw, tokens)) {
            val c =
                when (t.kind) {
                    QuickTokenKind.DATE, QuickTokenKind.TIME, QuickTokenKind.COURSE -> primary
                    QuickTokenKind.PRIORITY -> priorityColor
                    QuickTokenKind.LIST -> tertiary
                    QuickTokenKind.TAG -> secondary
                }
            b.addStyle(SpanStyle(color = c, fontWeight = FontWeight.Medium, background = c.copy(alpha = 0.12f)), r.first, r.last + 1)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenHighlight) return false
        return tokens == other.tokens && primary == other.primary && priorityColor == other.priorityColor && tertiary == other.tertiary && secondary == other.secondary
    }

    override fun hashCode(): Int {
        var result = tokens.hashCode()
        result = 31 * result + primary.hashCode()
        result = 31 * result + priorityColor.hashCode()
        result = 31 * result + tertiary.hashCode()
        result = 31 * result + secondary.hashCode()
        return result
    }
}

/** 「/课名」匹配已有课程：先全名，再包含。 */
internal fun matchCourse(
    snap: Snapshot,
    name: String?,
) = name?.takeIf { it.isNotBlank() }?.let { n ->
    snap.courses.firstOrNull { it.name.replace(" ", "") == n } ?: snap.courses.firstOrNull { it.name.contains(n, ignoreCase = true) }
}

/** 面板给出的覆盖值：时间/时间段 + 提醒 + 重复，只落在第一条顶层任务上。 */
internal data class QuickOverride(
    val whenPick: WhenPick?,
    val remind: Int?,
    val persistent: Boolean = false,
    val repeat: Recurrence?,
)

/**
 * 一批解析结果 → 一个变更集。新清单只建一次；缩进的行挂到父任务（继承其清单）；
 * 没写日期的顶层任务按 [defaultBucket] 落点：今天 → startDate=今天（不会次日变红），其它分区 → 当天截止；收集箱不设日期。
 */
internal fun buildQuickTasks(
    snap: Snapshot,
    items: List<QuickBatchItem>,
    defaultListId: String?,
    defaultBucket: Bucket?,
    now: LocalDateTime,
    desc: String,
    override: QuickOverride? = null,
    note: String = "",
): ChangeSet {
    val changes = mutableListOf<Change>()
    val newLists = mutableMapOf<String, TaskList>()
    var order = snap.tasks.maxOfOrNull { it.sortOrder } ?: 0
    val ids = mutableListOf<String>()
    val listIds = mutableListOf<String?>()
    val today = now.toLocalDate()
    items.forEachIndexed { i, item ->
        val p = item.result
        val parentIdx = item.parentIndex?.takeIf { it in 0 until i }
        var listId = if (parentIdx != null) listIds[parentIdx] else defaultListId
        if (p.listName != null) {
            val existing = snap.lists.firstOrNull { it.name == p.listName } ?: newLists[p.listName]
            if (existing != null) {
                listId = existing.id
            } else {
                val l = TaskList(Ids.next(), p.listName.orEmpty(), colorIndex = (snap.lists.size + newLists.size) % 8, sort = snap.lists.size + newLists.size)
                newLists[l.name] = l
                changes += Change.Add(l)
                listId = l.id
            }
        }
        val course = matchCourse(snap, p.courseName)
        var due = p.due
        var dueHasTime = p.dueHasTime
        var start: java.time.LocalDate? = null
        if (course == null && due == null && parentIdx == null && defaultBucket != null) {
            val d = TaskLogic.dropDate(defaultBucket, today)
            when {
                defaultBucket == Bucket.TODAY -> {
                    start = today
                }

                d != null && d != TaskLogic.NO_DROP -> {
                    due = LocalDateTime.of(d, LocalTime.of(23, 59))
                    dueHasTime = false
                }
            }
        }
        order += 1
        val id = Ids.next()
        ids += id
        listIds += listId
        // 面板覆盖只给第一条顶层任务：日期/时间段、提醒、重复一次带上
        val ov = override?.takeIf { i == 0 && parentIdx == null && course == null }
        val wp = ov?.whenPick
        var blockStart: LocalDateTime? = null
        var blockMinutes: Int? = null
        if (wp != null) {
            val dur = wp.minutes
            if (wp.start != null) {
                if (dur != null) {
                    blockStart = LocalDateTime.of(wp.date, wp.start)
                    blockMinutes = dur
                    due = blockStart.plusMinutes(dur.toLong())
                } else {
                    due = LocalDateTime.of(wp.date, wp.start)
                }
                dueHasTime = true
            } else {
                due = wp.date.atTime(23, 59)
                dueHasTime = false
            }
        }
        changes +=
            Change.Add(
                Task(
                    id,
                    p.title,
                    note = if (i == 0 && parentIdx == null) note else "",
                    listId = listId,
                    tags = p.tags,
                    priority = p.priority,
                    due = if (course != null) null else due,
                    dueHasTime = if (course != null) false else dueHasTime,
                    startDate = start,
                    courseId = course?.id,
                    dueBeforeNextClass = course != null,
                    parentId = parentIdx?.let { ids[it] },
                    sortOrder = order,
                    source = Source.MANUAL,
                    blockStart = blockStart,
                    blockMinutes = blockMinutes,
                    remindMinutes = ov?.remind,
                    remindPersistent = ov?.persistent == true && ov.remind != null,
                    repeat = ov?.repeat,
                ),
            )
    }
    return ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes)
}
