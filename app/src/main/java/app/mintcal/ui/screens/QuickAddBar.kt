package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
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
import app.mintcal.ui.components.Tag
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 快速添加：右下角加号打开，输入区贴在键盘上方。
 * 点「确定」（或键盘完成键）才落库；点空白 / 返回收起即丢弃草稿。
 * 日期、优先级、标签、清单在工具条上；详细编辑、粘贴、课程收进更多。
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
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val haptics = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    // 日期面板：日期/时间段 + 提醒 + 重复一次选好；设了就压过文本里识别出的日期
    var whenPanel by remember { mutableStateOf(false) }
    var panelWhen by remember { mutableStateOf<WhenPick?>(null) }
    var panelRemind by remember { mutableStateOf<Int?>(null) }
    var panelPersistent by remember { mutableStateOf(false) }
    var panelFreq by remember { mutableStateOf<Freq?>(null) }
    var panelInterval by remember { mutableIntStateOf(1) }
    var panelUntil by remember { mutableStateOf<LocalDate?>(null) }
    var panelByDays by remember { mutableStateOf(emptySet<Int>()) }
    val settings = settingsState()
    // 工具栏弹出的菜单都不抢焦点：键盘保持打开，浮层不跟着上下跳
    val noFocusPopup = remember { PopupProperties(focusable = false) }
    var batch by remember { mutableStateOf<List<QuickBatchItem>?>(null) }
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }

    /** 已有标签按使用次数排序：标签按钮直接点选，不用手打 @ */
    val knownTags =
        remember(snap.tasks) {
            snap.tasks
                .flatMap { it.tags }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
        }
    var tagMenu by remember { mutableStateOf(false) }
    // 三个 Tab 各挂一份浮层，只有可见的那份向外报状态；隐藏的那份不写，免得互相覆盖。
    DisposableEffect(visible) {
        if (visible) nav.composerOpen = true
        onDispose { if (visible) nav.composerOpen = false }
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val highlight =
        remember(parsed, scheme, colors) {
            TokenHighlight(parsed?.tokens.orEmpty()) { kind ->
                when (kind) {
                    QuickTokenKind.DATE, QuickTokenKind.TIME, QuickTokenKind.COURSE -> scheme.primary

                    // 优先级高亮走语义色（低灰绿/中金/高红），不再一律错误红
                    QuickTokenKind.PRIORITY -> colors.priorityColor(parsed?.priority ?: 3) ?: scheme.error

                    QuickTokenKind.LIST -> scheme.tertiary

                    QuickTokenKind.TAG -> scheme.secondary
                }
            }
        }

    LaunchedEffect(focusSignal) { if (focusSignal > 0) focus.requestFocus() }
    // 打开即弹键盘。日期不写进输入框，避免「今天」占掉标题。
    LaunchedEffect(visible) {
        if (visible) {
            focus.requestFocus()
        } else {
            focusManager.clearFocus(force = true)
        }
    }

    /** 去掉某类片段后（可选）追加一个新片段，用于快捷条与标签点按。 */
    fun rewrite(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        var t = text
        parsed?.tokens?.filter { it.kind in kinds }?.forEach { t = t.replace(it.text, " ") }
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

    /** 追加一个已有标签；已经在输入里的标签不重复加。 */
    fun addTag(name: String) {
        if (parsed?.tags?.any { it.equals(name, ignoreCase = true) } == true) return
        val t = text.replace(Regex("\\s+"), " ").trim()
        text = if (t.isEmpty()) "@$name " else "$t @$name"
        focus.requestFocus()
    }

    /** 新建标签：只落一个 @，后面继续打字就是新标签名。 */
    fun tagMarker() {
        val t = text.trimEnd()
        text = if (t.isEmpty()) "@" else "$t @"
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
                    repeat = panelFreq?.let { f -> Recurrence(f, panelInterval.coerceAtLeast(1), if (f == Freq.WEEKLY) panelByDays else emptySet(), panelUntil) },
                )
            } else {
                null
            }
        actions.commit(buildQuickTasks(snap, placed, listFilter, defaultBucket, now, desc, ov, note), msg)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        note = ""
    }

    /** 收起即丢弃：没点「确定」的草稿不落库。 */
    fun discard() {
        text = ""
        note = ""
        panelWhen = null
        panelRemind = null
        panelPersistent = false
        panelFreq = null
        panelUntil = null
        panelByDays = emptySet()
        onDismiss()
    }

    // 返回 = 收起并丢弃草稿（只有可见的那个浮层认领返回事件）
    BackHandler(enabled = visible) { discard() }

    /** 点「确定」（或键盘完成键）：写了内容就落库，没写就是取消。 */
    fun finish() {
        if (parsed == null) {
            onDismiss()
            return
        }
        commitItems(listOf(QuickBatchItem(parsed, null)))
        text = ""
        panelWhen = null
        panelRemind = null
        panelPersistent = false
        panelFreq = null
        panelUntil = null
        panelByDays = emptySet()
        onDismiss()
    }

    /** 外部来的文字（粘贴 / 分享）：多行进批量预览，单行放进输入框。 */
    fun take(raw: String) {
        val items = QuickAdd.parseLines(raw, now)
        when {
            items.size > 1 -> {
                batch = items
            }

            items.size == 1 -> {
                text = raw.trim().replace(Regex("\\s+"), " ")
                focus.requestFocus()
            }
        }
    }

    val pending = nav.pendingAddText
    LaunchedEffect(pending) {
        if (acceptShare && pending != null) {
            nav.pendingAddText = null
            take(pending)
        }
    }

    fun openDetail() {
        val draft = text.ifBlank { null }
        val draftNote = note.ifBlank { null }
        // 草稿移交编辑页：浮层清空收起，回来不残留旧文字
        text = ""
        note = ""
        onDismiss()
        nav.push(Route.TodoEdit(text = draft, note = draftNote))
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
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MintTheme.spec()),
        exit = fadeOut(MintTheme.spec()),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩：点空白 = 收起并丢弃草稿。压浅一点，列表还看得见。
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .pointerInput(Unit) { detectTapGestures { discard() } },
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
                    // 备注：详细编辑同款素面输入，可留空
                    Box(Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                        if (note.isEmpty()) {
                            Text(
                                stringResource(R.string.field_note),
                                Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        Box {
                            IconButton(onClick = { priorityMenu = true }) {
                                Icon(
                                    Icons.Rounded.Flag,
                                    stringResource(R.string.label_priority),
                                    tint = colors.priorityColor(parsed?.priority ?: 0) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(priorityMenu, { priorityMenu = false }, properties = noFocusPopup) {
                                listOf(
                                    Triple(3, R.string.priority_3, colors.priorityColor(3)),
                                    Triple(2, R.string.priority_2, colors.priorityColor(2)),
                                    Triple(1, R.string.priority_1, colors.priorityColor(1)),
                                ).forEach { (_, label, color) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label) + stringResource(R.string.label_priority)) },
                                        leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = {
                                            priorityMenu = false
                                            rewrite(setOf(QuickTokenKind.PRIORITY), "!" + ctx.getString(label))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.priority_none)) },
                                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        priorityMenu = false
                                        rewrite(setOf(QuickTokenKind.PRIORITY), null)
                                    },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { tagMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Label,
                                    stringResource(R.string.field_tags),
                                    tint = if (parsed?.tags?.isNotEmpty() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(tagMenu, { tagMenu = false }, properties = noFocusPopup) {
                                knownTags.forEach { name ->
                                    DropdownMenuItem(text = { Text("@$name") }, onClick = {
                                        tagMenu = false
                                        addTag(name)
                                    })
                                }
                                if (knownTags.isNotEmpty()) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.quick_tag_new)) }, onClick = {
                                    tagMenu = false
                                    tagMarker()
                                })
                            }
                        }
                        if (snap.lists.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { listMenu = true }) {
                                    Icon(Icons.Rounded.Inbox, stringResource(R.string.label_list), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(listMenu, { listMenu = false }, properties = noFocusPopup) {
                                    snap.lists.sortedBy { it.sort }.forEach { l ->
                                        DropdownMenuItem(text = { Text(l.name) }, onClick = {
                                            listMenu = false
                                            rewrite(setOf(QuickTokenKind.LIST), "#" + l.name)
                                        })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { openDetail() }) {
                            Icon(Icons.Rounded.OpenInFull, stringResource(R.string.detail_editor_full_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 确定：点了才落库；没写内容置灰
                        val canAdd = parsed != null
                        Surface(
                            onClick = { finish() },
                            enabled = canAdd,
                            shape = CircleShape,
                            color = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Check,
                                    stringResource(R.string.confirm),
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
                        visible = parsed?.tokens?.isNotEmpty() == true || panelWhenLabel != null || panelRemind != null || panelFreq != null,
                        enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                        exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                    ) {
                        val p = parsed
                        if (p != null) {
                            FlowRow(
                                Modifier.padding(end = 6.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                p.due?.let {
                                    Tag(
                                        ctx.dueLabel(it, p.dueHasTime, now.toLocalDate()) + " ×",
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                        Modifier.clickable { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.TIME), null) },
                                    )
                                }
                                p.courseName?.let { name ->
                                    val c = matchCourse(snap, name)
                                    Tag(
                                        stringResource(R.string.detail_editor_before_class, c?.name ?: name) + " ×",
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                        Modifier.clickable { rewrite(setOf(QuickTokenKind.COURSE), null) },
                                    )
                                }
                                if (p.priority > 0) {
                                    val label =
                                        when (p.priority) {
                                            3 -> R.string.priority_3
                                            2 -> R.string.priority_2
                                            else -> R.string.priority_1
                                        }
                                    // chip 与旗子/高亮同源取优先级语义色，低透明度底 + 实色字
                                    val prio = colors.priorityColor(p.priority) ?: MaterialTheme.colorScheme.secondary
                                    Tag(
                                        "!" + stringResource(label) + " ×",
                                        prio.copy(alpha = 0.14f),
                                        prio,
                                        Modifier.clickable { rewrite(setOf(QuickTokenKind.PRIORITY), null) },
                                    )
                                }
                                p.listName?.let {
                                    Tag(
                                        "#$it ×",
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                        Modifier.clickable { rewrite(setOf(QuickTokenKind.LIST), null) },
                                    )
                                }
                                val tagTokens = p.tokens.filter { it.kind == QuickTokenKind.TAG }
                                p.tags.forEachIndexed { i, tag ->
                                    Tag(
                                        "@$tag ×",
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        Modifier.clickable {
                                            tagTokens.getOrNull(i)?.let { tok ->
                                                text = text.replace(tok.text, " ").replace(Regex("\\s+"), " ").trim()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (panelWhenLabel != null || panelRemind != null || panelFreq != null) {
                            FlowRow(
                                Modifier.padding(end = 6.dp, top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (panelWhenLabel != null) {
                                    Tag(
                                        "$panelWhenLabel ×",
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                        Modifier.clickable { panelWhen = null },
                                    )
                                }
                                if (panelRemind != null) {
                                    Tag(
                                        remindLabel(panelRemind) + " ×",
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                        Modifier.clickable { panelRemind = null },
                                    )
                                }
                                if (panelFreq != null) {
                                    Tag(
                                        freqLabel(panelFreq, panelUntil, panelInterval, panelByDays, panelWhen?.date) + " ×",
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                        Modifier.clickable {
                                            panelFreq = null
                                            panelUntil = null
                                            panelByDays = emptySet()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    batch?.let { items ->
        BatchPreviewDialog(items, snap, now, onDismiss = { batch = null }) {
            commitItems(items)
            batch = null
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
                whenPanel = false
            },
        )
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
private class TokenHighlight(
    private val tokens: List<QuickToken>,
    private val colorOf: (QuickTokenKind) -> Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text.text)
        for (t in tokens) {
            val i = text.text.indexOf(t.text)
            if (i < 0 || t.text.isEmpty()) continue
            val c = colorOf(t.kind)
            b.addStyle(SpanStyle(color = c, fontWeight = FontWeight.Medium, background = c.copy(alpha = 0.12f)), i, i + t.text.length)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
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
