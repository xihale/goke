package app.goke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Course
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.time.SemesterCalendar
import app.goke.core.todo.Bucket
import app.goke.core.todo.QuickAdd
import app.goke.core.todo.QuickAddResult
import app.goke.core.todo.QuickBatchItem
import app.goke.core.todo.QuickToken
import app.goke.core.todo.QuickTokenKind
import app.goke.core.todo.TaskLogic
import app.goke.core.util.Ids
import app.goke.ui.LocalActions
import app.goke.ui.LocalNav
import app.goke.ui.LocalTabVisible
import app.goke.ui.Route
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.MintAlertDialog
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.Tag
import app.goke.ui.components.mintClickable
import app.goke.ui.dueLabel
import app.goke.ui.hm
import app.goke.ui.icons.Event
import app.goke.ui.icons.Flag
import app.goke.ui.icons.Inbox
import app.goke.ui.icons.LocalOffer
import app.goke.ui.icons.NotificationsNone
import app.goke.ui.icons.OpenInFull
import app.goke.ui.icons.Repeat
import app.goke.ui.icons.School
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import app.goke.ui.theme.priorityColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 工具栏就地展开的选择面板（不弹浮层，键盘不动）。 */
internal enum class QuickPanel { PRIORITY, LIST, TAG, COURSE }

/** 优先级档位的快捷语法文字：`!高` / `!中` / `!低`，与 [QuickAdd] 的解析口径一致。 */
internal fun priorityToken(v: Int): Int =
    when (v) {
        3 -> R.string.priority_3
        2 -> R.string.priority_2
        else -> R.string.priority_1
    }

/** 去掉某类片段后（可选）追加新片段，并把光标放到末尾；前缀引导（# / @ / /）后不加多余空格。 */
internal fun rewriteQuickField(
    current: String,
    tokens: List<QuickToken>,
    kinds: Set<QuickTokenKind>,
    add: String?,
): TextFieldValue {
    val removeRanges =
        tokenRanges(current, tokens)
            .filter { it.first.kind in kinds }
            .map { it.second }
            .sortedByDescending { it.first }
    var t = current
    for (r in removeRanges) {
        t = t.substring(0, r.first) + " " + t.substring(r.last + 1)
    }
    t = t.replace(Regex("\\s+"), " ").trim()
    val isPrefixPrompt = add == "#" || add == "@" || add == "/"
    val next =
        when {
            add == null -> t
            isPrefixPrompt && (t == add || t.endsWith(" $add")) -> t
            t.isEmpty() -> if (isPrefixPrompt) add else "$add "
            isPrefixPrompt -> "$t $add"
            else -> "$t $add "
        }
    return TextFieldValue(text = next, selection = TextRange(next.length))
}

/**
 * 快速添加：右下角加号打开，输入区贴在键盘上方。
 * 点「确定」（或键盘完成键）才落库；点空白 / 返回收起即丢弃草稿。
 * 日期、优先级、清单与关联课程在工具条上；点右上角可展开详细编辑。
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

    var sheetAlive by remember { mutableStateOf(visible) }
    if (visible && !sheetAlive) sheetAlive = true

    // 未展开且已完全退场时真正早退：跳过内部 UI、BackHandler 与焦点逻辑，
    // 避免今日/日历/待办 3 个 Tab 重复空转与常驻监听。
    if (!visible && !sheetAlive) return

    val ctx = LocalContext.current
    val actions = LocalActions.current
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    // 工具栏的优先级 / 清单同样就地展开，不弹浮层（浮层在键盘上方会留一条空、还遮住输入）
    var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
    // 日期面板：日期/时间段 + 提醒 + 重复一次选好；设了就压过文本里识别出的日期
    var whenPanel by remember { mutableStateOf(false) }
    var panelWhen by remember { mutableStateOf<WhenPick?>(null) }
    var panelRemindList by remember { mutableStateOf<List<Int>>(emptyList()) }
    var panelPersistent by remember { mutableStateOf(false) }
    var panelFreq by remember { mutableStateOf<Freq?>(null) }
    var panelInterval by remember { mutableIntStateOf(1) }
    var panelUntil by remember { mutableStateOf<LocalDate?>(null) }
    var panelByDays by remember { mutableStateOf(emptySet<Int>()) }
    var panelByMonths by remember { mutableStateOf(emptySet<Int>()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val settings = settingsState()
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val isTabVisible = LocalTabVisible.current.value
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) {
            confirmDiscard = false
            currentOnDismiss()
        }
    }

    LaunchedEffect(visible) {
        nav.composerOpen = visible
    }
    DisposableEffect(Unit) {
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

    LaunchedEffect(focusSignal) { if (visible && focusSignal > 0) focus.requestFocus() }
    // 打开即弹键盘。日期不写进输入框，避免「今天」占掉标题。
    LaunchedEffect(visible) { if (visible) focus.requestFocus() }

    /** 去掉某类片段后（可选）追加一个新片段，用于快捷条点按。 */
    fun rewrite(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        field = rewriteQuickField(field.text, parsed?.tokens.orEmpty(), kinds, add)
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
            if (panelWhen != null || panelRemindList.isNotEmpty() || panelFreq != null) {
                QuickOverride(
                    whenPick = panelWhen,
                    remindList = panelRemindList,
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
        field = TextFieldValue("")
        panelWhen = null
        panelRemindList = emptyList()
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

    val hasDraft = text.isNotBlank() || panelWhen != null || panelRemindList.isNotEmpty() || panelFreq != null
    val hasBodyText = parsed != null && !(parsed.tokens.isNotEmpty() && parsed.title == text.trim())

    fun requestDiscard() {
        if (hasDraft) confirmDiscard = true else discard()
    }

    val currentRequestDiscard by rememberUpdatedState(::requestDiscard)

    /** 点「确定」（或键盘完成键）：写了内容就落库，没写就是取消。 */
    fun finish() {
        if (submitted || !hasBodyText) return
        submitted = true
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
        val resolvedCourseId = parsed?.courseName?.let { n -> snap.courses.firstOrNull { it.deletedAt == null && it.name.contains(n, ignoreCase = true) }?.id }
        val remindArg = panelRemindList.firstOrNull()
        val remindListArg = panelRemindList.takeIf { it.isNotEmpty() }?.joinToString(",")
        val persistentArg = panelPersistent && panelRemindList.isNotEmpty()
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
                courseId = resolvedCourseId,
                listId = resolvedListId,
                remindMinutes = remindArg,
                remindList = remindListArg,
                remindPersistent = persistentArg,
                repeatFreq = freqArg,
                repeatInterval = intervalArg,
                repeatUntil = untilArg,
                repeatByDays = byDaysArg,
                repeatByMonths = byMonthsArg,
            ),
        )
    }
    val defaultDateLabel =
        when {
            anchorDate != null -> ctx.dueLabel(anchorDate.atStartOfDay(), false, now.toLocalDate())
            defaultBucket == Bucket.TODAY -> stringResource(R.string.today)
            else -> null
        }
    val defaultListLabel = remember(snap.lists, listFilter) { listFilter?.let { id -> snap.lists.firstOrNull { it.id == id }?.name } }

    app.goke.ui.components.MintSheetOverlay(
        visible = visible,
        onDismissRequest = { discard() },
        modifier = modifier,
        scrimAlpha = 0.22f,
        canDragDismiss = visible && !whenPanel && !confirmDiscard,
        predictiveBackEnabled = visible && isTabVisible && !whenPanel && !confirmDiscard,
        needsConfirmOnDismiss = hasDraft,
        onDismissConfirmRequest = { currentRequestDiscard() },
        onExited = { sheetAlive = false },
        sheetModifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        ) {
            QuickAddComposer(
                snap = snap,
                now = now,
                field = field,
                onFieldChange = { field = it },
                parsed = parsed,
                highlight = highlight,
                focusRequester = focus,
                toolPanel = toolPanel,
                onToolPanelChange = { toolPanel = it },
                panelWhen = panelWhen,
                panelRemindList = panelRemindList,
                panelFreq = panelFreq,
                panelInterval = panelInterval,
                panelByDays = panelByDays,
                panelByMonths = panelByMonths,
                defaultDateLabel = defaultDateLabel,
                defaultListLabel = defaultListLabel,
                expandLabel = stringResource(R.string.detail_editor_full_edit),
                onExpand = { openDetail() },
                submitLabel = stringResource(R.string.confirm),
                canSubmit = hasBodyText && !submitted,
                onSubmit = { finish() },
                onOpenWhenPanel = { whenPanel = true },
                onRewriteToken = ::rewrite,
            )
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
            initialRemindList = panelRemindList,
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
                panelRemindList = r.remindMinutesList
                panelPersistent = r.remindPersistent
                panelFreq = r.freq
                panelInterval = r.interval
                panelUntil = r.until
                panelByDays = r.byDays
                panelByMonths = r.byMonths
                if (r.whenPick == null && r.remindMinutesList.isEmpty() && r.freq == null) {
                    rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.TIME), null)
                }
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

/**
 * 快速添加的统一输入体 + 工具栏（应用内浮层与桌面小组件悬浮层共用）：
 * 右上角展开全屏编辑，中部留白输入区带行内语法着色，底栏左侧可横向滑动容纳日期/优先级/标签/清单/课程，右侧固定发送，
 * 选中属性直接体现在底栏图标或文案上，不再在下方堆砌胶囊。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddComposer(
    snap: Snapshot,
    now: LocalDateTime,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    parsed: QuickAddResult?,
    highlight: VisualTransformation,
    focusRequester: FocusRequester,
    toolPanel: QuickPanel?,
    onToolPanelChange: (QuickPanel?) -> Unit,
    panelWhen: WhenPick?,
    panelRemindList: List<Int>,
    panelFreq: Freq?,
    panelInterval: Int,
    panelByDays: Set<Int>,
    panelByMonths: Set<Int>,
    expandLabel: String,
    onExpand: () -> Unit,
    submitLabel: String,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onOpenWhenPanel: () -> Unit,
    onRewriteToken: (Set<QuickTokenKind>, String?) -> Unit,
    defaultDateLabel: String? = null,
    defaultListLabel: String? = null,
    linkedCourse: Course? = null,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val today = now.toLocalDate()
    val text = field.text

    val existingTags =
        remember(snap.tasks) {
            snap.tasks
                .asSequence()
                .filter { it.deletedAt == null }
                .flatMap { it.tags.asSequence() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(12)
                .toList()
        }
    val activeCourses =
        remember(snap.courses, snap.currentSemester) {
            val semId = snap.currentSemester?.id
            val base = snap.courses.filter { it.deletedAt == null && !it.isExam }
            val scoped = if (semId != null) base.filter { it.semesterId == semId }.ifEmpty { base } else base
            scoped
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name }))
                .distinctBy { it.name.trim() }
        }

    val parsedDue = parsed?.due
    val whenLabel =
        when {
            panelWhen != null -> {
                val base = ctx.dueLabel(panelWhen.date.atStartOfDay(), false, today)
                val time = panelWhen.start?.hm()
                if (time != null) "$base $time" else base
            }

            parsedDue != null -> {
                ctx.dueLabel(parsedDue, parsed.dueHasTime, today)
            }

            panelFreq != null -> {
                freqLabel(panelFreq, null, panelInterval, panelByDays, null, withUntil = false, byMonths = panelByMonths)
            }

            panelRemindList.isNotEmpty() -> {
                remindSummaryLabel(panelRemindList)
            }

            else -> {
                defaultDateLabel
            }
        }
    val whenActive = panelWhen != null || parsed?.due != null || panelFreq != null || panelRemindList.isNotEmpty() || defaultDateLabel != null

    val listLabel = parsed?.listName ?: defaultListLabel ?: stringResource(R.string.bucket_inbox)
    val listActive = parsed?.listName != null || defaultListLabel != null

    val tagActive = parsed?.tags?.isNotEmpty() == true
    val courseActive = toolPanel == QuickPanel.COURSE || parsed?.courseName != null

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (linkedCourse != null) {
                Tag(
                    linkedCourse.name,
                    colors.bg(linkedCourse.colorIndex),
                    colors.fg(linkedCourse.colorIndex),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = onFieldChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(top = 6.dp, bottom = 10.dp),
                    textStyle =
                        MaterialTheme.typography.titleLarge.copy(
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Normal,
                        ),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 4,
                    visualTransformation = highlight,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
                )
                if (text.isEmpty()) {
                    Text(
                        stringResource(R.string.quick_add_prompt),
                        Modifier
                            .padding(start = 2.dp, top = 6.dp, bottom = 10.dp)
                            .clearAndSetSemantics {},
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .mintClickable(pressedScale = 0.96f, role = Role.Button) { onExpand() }
                    .semantics { contentDescription = expandLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.OpenInFull,
                null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
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
                onRewriteToken(setOf(QuickTokenKind.PRIORITY), if (v == 0) null else "!" + ctx.getString(priorityToken(v)))
                onToolPanelChange(null)
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
            ChipPill(stringResource(R.string.bucket_inbox), on = parsed?.listName == null && defaultListLabel == null) {
                onRewriteToken(setOf(QuickTokenKind.LIST), null)
                onToolPanelChange(null)
            }
            snap.lists.sortedBy { it.sort }.forEach { l ->
                ChipPill("#${l.name}", on = parsed?.listName == l.name) {
                    onRewriteToken(setOf(QuickTokenKind.LIST), "#${l.name}")
                    onToolPanelChange(null)
                }
            }
            ChipPill(stringResource(R.string.quick_add_new_list), on = false) {
                onRewriteToken(setOf(QuickTokenKind.LIST), "#")
                onToolPanelChange(null)
            }
        }
    }

    AnimatedVisibility(
        visible = toolPanel == QuickPanel.TAG,
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
            existingTags.forEach { tag ->
                val selected = tag in parsed?.tags.orEmpty()
                ChipPill("@$tag", on = selected) {
                    if (selected) {
                        val cleaned = text.replace(Regex("@${Regex.escape(tag)}\\s*"), "").replace(Regex("\\s+"), " ").trimStart()
                        onFieldChange(TextFieldValue(cleaned, TextRange(cleaned.length)))
                    } else {
                        onRewriteToken(emptySet(), "@$tag")
                    }
                    onToolPanelChange(null)
                }
            }
            ChipPill(stringResource(R.string.quick_add_new_tag), on = false) {
                onRewriteToken(emptySet(), "@")
                onToolPanelChange(null)
            }
        }
    }

    AnimatedVisibility(
        visible = toolPanel == QuickPanel.COURSE && linkedCourse == null && activeCourses.isNotEmpty(),
        enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
        exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.quick_add_course_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (parsed?.courseName != null) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                                onRewriteToken(setOf(QuickTokenKind.COURSE), null)
                                onToolPanelChange(null)
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.quick_add_unlink_course),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 176.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                activeCourses.forEach { c ->
                    val shortName = c.name.replace(" ", "")
                    val selected = parsed?.courseName?.equals(shortName, ignoreCase = true) == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                            .semantics { this.selected = selected }
                            .mintClickable(pressedScale = 0.98f, role = Role.RadioButton) {
                                onRewriteToken(setOf(QuickTokenKind.COURSE), if (selected) null else "/$shortName")
                                onToolPanelChange(null)
                            }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .clip(CircleShape)
                                .background(colors.fg(c.colorIndex)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val pickDateDesc = stringResource(R.string.detail_editor_pick_date)
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .mintClickable(pressedScale = 0.96f, role = Role.Button) { onOpenWhenPanel() }
                        .semantics { if (whenLabel == null) contentDescription = pickDateDesc }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Rounded.Event,
                    null,
                    tint = if (whenActive) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
                if (whenLabel != null) {
                    Text(
                        whenLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (whenActive) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
                if (panelFreq != null && panelWhen != null) {
                    Icon(Icons.Rounded.Repeat, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                }
                if (panelRemindList.isNotEmpty() && (panelWhen != null || panelFreq != null)) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                }
            }

            QuickComposerIconBtn(
                icon = Icons.Rounded.Flag,
                contentDescription = stringResource(R.string.label_priority),
                tint = colors.priorityColor(parsed?.priority ?: 0) ?: scheme.onSurfaceVariant,
                onClick = { onToolPanelChange(if (toolPanel == QuickPanel.PRIORITY) null else QuickPanel.PRIORITY) },
            )

            QuickComposerIconBtn(
                icon = Icons.Outlined.LocalOffer,
                contentDescription = stringResource(R.string.field_tags),
                tint = if (tagActive) scheme.secondary else scheme.onSurfaceVariant,
                onClick = {
                    if (existingTags.isEmpty()) {
                        onRewriteToken(emptySet(), "@")
                        onToolPanelChange(null)
                    } else {
                        onToolPanelChange(if (toolPanel == QuickPanel.TAG) null else QuickPanel.TAG)
                    }
                },
            )

            val listDesc = stringResource(R.string.label_list)
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                            if (snap.lists.isEmpty()) {
                                onRewriteToken(setOf(QuickTokenKind.LIST), "#")
                                onToolPanelChange(null)
                            } else {
                                onToolPanelChange(if (toolPanel == QuickPanel.LIST) null else QuickPanel.LIST)
                            }
                        }.semantics { contentDescription = listDesc }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Rounded.Inbox,
                    null,
                    tint = if (listActive) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    listLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (listActive) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 76.dp),
                )
            }

            if (linkedCourse == null && activeCourses.isNotEmpty()) {
                QuickComposerIconBtn(
                    icon = Icons.Rounded.School,
                    contentDescription = stringResource(R.string.quick_add_link_course),
                    tint = if (courseActive) scheme.primary else scheme.onSurfaceVariant,
                    onClick = { onToolPanelChange(if (toolPanel == QuickPanel.COURSE) null else QuickPanel.COURSE) },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .mintClickable(enabled = canSubmit, pressedScale = 0.96f, role = Role.Button) { onSubmit() }
                    .semantics { contentDescription = submitLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                null,
                tint = if (canSubmit) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun QuickComposerIconBtn(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
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
    var submitted by remember(items) { mutableStateOf(false) }
    MintAlertDialog(
        onDismissRequest = { if (!submitted) onDismiss() },
        dismissOnClickOutside = !submitted,
        dismissOnBackPress = !submitted,
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
        confirmButton = {
            TextButton(
                enabled = !submitted,
                onClick = {
                    if (submitted) return@TextButton
                    submitted = true
                    onConfirm()
                },
            ) { Text(stringResource(R.string.todo_batch_add_all)) }
        },
        dismissButton = { TextButton(enabled = !submitted, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

/** 「/课名」匹配已有课程：优先当前学期非考试课，先全名再包含。 */
internal fun matchCourse(
    snap: Snapshot,
    name: String?,
): Course? =
    name?.takeIf { it.isNotBlank() }?.let { n ->
        val semId = snap.currentSemester?.id
        val base = snap.courses.filter { it.deletedAt == null && !it.isExam }
        val scoped = if (semId != null) base.filter { it.semesterId == semId }.ifEmpty { base } else base
        scoped.firstOrNull { it.name.replace(" ", "") == n }
            ?: scoped.firstOrNull { it.name.contains(n, ignoreCase = true) }
            ?: snap.courses.firstOrNull { it.deletedAt == null && it.name.replace(" ", "") == n }
            ?: snap.courses.firstOrNull { it.deletedAt == null && it.name.contains(n, ignoreCase = true) }
    }

/** 面板给出的覆盖值：时间/时间段 + 提醒 + 重复，只落在第一条顶层任务上。 */
internal data class QuickOverride(
    val whenPick: WhenPick?,
    val remindList: List<Int> = emptyList(),
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
                    remindMinutes = ov?.remindList?.firstOrNull(),
                    remindMinutesList = ov?.remindList.orEmpty(),
                    remindPersistent = ov?.persistent == true && ov.remindList.isNotEmpty(),
                    repeat = ov?.repeat,
                ),
            )
    }
    return ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes)
}
