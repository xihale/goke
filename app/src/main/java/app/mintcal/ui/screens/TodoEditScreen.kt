package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import app.mintcal.ui.Tab
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.ConflictCard
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.SegmentedPill
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.components.rememberFileAttachmentPicker
import app.mintcal.ui.components.rememberImageAttachmentPicker
import app.mintcal.ui.components.rememberNoFocusPopup
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberToday
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 底栏上方就地展开的选择面板（不弹浮层，键盘不动）。 */
private enum class BarPanel { LIST, PRIORITY }

/**
 * 唯一的编辑页：新建和修改都是它。版式对齐 TickTick 详情：
 * 勾选在顶栏右侧，标题从左缘起，日期、备注、附件依次全宽铺在页面里。
 * 底部左侧是清单名，右侧是子任务、优先级、图片和附件；子任务点清单图标在栏上方展开。
 * 修改返回即保存（空标题等于放弃）；新建要点顶栏「确定」才落库，返回先确认放弃（空白草稿直接走）。
 * 标题里识别到的日期时间就地高亮。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    locationArg: String? = null,
    listArg: String? = null,
    remindArg: Int? = null,
    remindPersistentArg: Boolean = false,
    repeatFreqArg: String? = null,
    repeatIntervalArg: Int = 1,
    repeatUntilArg: String? = null,
    repeatByDaysArg: String? = null,
    repeatByMonthsArg: String? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val baseNow = remember { LocalDateTime.now() }
    val today = rememberToday().value
    val existing = remember(id, snap.tasks) { id?.let { i -> snap.tasks.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val argDate = remember { dateArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val initStart = remember { startArg?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    val initEnd = remember { endArg?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    val initFreq = remember { repeatFreqArg?.let { runCatching { Freq.valueOf(it) }.getOrNull() } }
    val initUntil = remember { repeatUntilArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val initByDays =
        remember {
            repeatByDaysArg
                .orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }
    val initByMonths =
        remember {
            repeatByMonthsArg
                .orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }
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
                    },
                )
            }
        }

    var field by remember { mutableStateOf(TextFieldValue(existing?.title ?: textDraft.orEmpty())) }
    var note by remember { mutableStateOf(existing?.note ?: noteDraft.orEmpty()) }
    var location by remember { mutableStateOf(existing?.extra?.get("location") ?: locationArg.orEmpty()) }
    var attachments by remember { mutableStateOf(existing?.attachments.orEmpty()) }
    var picked by remember { mutableStateOf(initWhen) }
    var cleared by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(if (existing != null) existing.remindMinutes else remindArg) }
    var persistent by remember { mutableStateOf(if (existing != null) existing.remindPersistent else (remindPersistentArg && remindArg != null)) }
    var freq by remember { mutableStateOf(if (existing != null) existing.repeat?.freq else initFreq) }
    var interval by remember { mutableIntStateOf(if (existing != null) (existing.repeat?.interval ?: 1) else repeatIntervalArg.coerceAtLeast(1)) }
    var until by remember { mutableStateOf(if (existing != null) existing.repeat?.until else initUntil) }
    var byDays by remember { mutableStateOf(if (existing != null) (existing.repeat?.byDays ?: emptySet()) else initByDays) }
    var byMonths by remember { mutableStateOf(if (existing != null) (existing.repeat?.byMonths ?: emptySet()) else initByMonths) }
    var listId by remember { mutableStateOf(if (existing != null) existing.listId else listArg) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 0) }
    val linkedCourseId = existing?.courseId ?: courseArg
    val linkedCourse = remember(linkedCourseId, snap.courses) { linkedCourseId?.let { cid -> snap.courses.firstOrNull { it.id == cid } } }
    var dueBeforeNextClass by remember { mutableStateOf(existing?.dueBeforeNextClass ?: (courseArg != null)) }
    var timeSheet by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }
    var subReset by remember { mutableIntStateOf(0) }
    var subsOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    // 底栏的清单 / 优先级就地展开，不弹浮层：键盘不动，也不会浮层飘在正文上留一块空
    var barPanel by remember { mutableStateOf<BarPanel?>(null) }
    // 顶栏「更多」仍在原位弹菜单，但它不抢焦点
    val noFocusPopup = rememberNoFocusPopup()
    val titleFocus = remember { FocusRequester() }
    val reduce = reduceMotion()
    LaunchedEffect(existing == null) {
        if (existing == null) {
            // 延迟到路由进入动画完成后再拉起软键盘，避免首帧触发同步 IMM/WMS Binder 交易与窗口重排
            delay(if (reduce) 16L else 280L)
            titleFocus.requestFocus()
        }
    }
    // 附件：图片和文件都在工具栏上，点了直接选
    val pickImage = rememberImageAttachmentPicker { a -> attachments = attachments + a }
    val pickFile = rememberFileAttachmentPicker { a -> attachments = attachments + a }

    val text = field.text
    val parsed = remember(text) { if (text.isBlank()) null else QuickAdd.parse(text, baseNow) }
    val tokens = remember(parsed) { parsed?.tokens?.filter { it.kind == QuickTokenKind.DATE || it.kind == QuickTokenKind.TIME }.orEmpty() }
    val textWhen =
        remember(parsed, existing, text) {
            // 已有无日期任务刚打开时，不因标题自带日期词而擅自套上日期；仅用户修改标题后才识别
            if (existing != null && existing.due == null && existing.blockStart == null && text == existing.title) {
                null
            } else {
                parsed?.due?.let { WhenPick(it.toLocalDate(), if (parsed.dueHasTime) it.toLocalTime() else null) }
            }
        }
    val whenPick =
        when {
            cleared -> null
            picked != null -> picked
            textWhen != null -> textWhen
            argDate != null -> WhenPick(argDate, null)
            else -> null
        }
    // 周历框选带来的其它日期：时间面板切走时间段后不再生效，与 commit 的口径一致
    val extraDays =
        remember(extraDatesArg, whenPick) {
            if (whenPick?.start == null) {
                emptyList()
            } else {
                extraDatesArg
                    .orEmpty()
                    .split(',')
                    .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
                    .filter { it != whenPick.date }
            }
        }
    val initialWhen = remember { whenPick }
    val whenChanged =
        whenPick?.date != initialWhen?.date || whenPick?.start != initialWhen?.start || whenPick?.minutes != initialWhen?.minutes
    val hasChanges =
        text != existing?.title.orEmpty() || note != existing?.note.orEmpty() ||
            location.trim() != existing?.extra?.get("location").orEmpty() ||
            attachments != existing?.attachments.orEmpty() || listId != existing?.listId || priority != (existing?.priority ?: 0) ||
            remind != existing?.remindMinutes || persistent != (existing?.remindPersistent == true) || freq != existing?.repeat?.freq ||
            dueBeforeNextClass != (existing?.dueBeforeNextClass ?: false) ||
            (
                freq != null &&
                    (
                        interval != (existing?.repeat?.interval ?: 1) || until != existing?.repeat?.until ||
                            byDays != (existing?.repeat?.byDays ?: emptySet<Int>()) || byMonths != (existing?.repeat?.byMonths ?: emptySet<Int>())
                    )
            ) ||
            whenChanged || newSub.isNotBlank()

    // 退出即保存：有内容且有改动就静默落盘；空标题等于放弃这条
    val ranges =
        remember(text, tokens) {
            if (text.isEmpty() || tokens.isEmpty()) {
                emptyList()
            } else {
                tokenRanges(text, tokens).map { it.second }.sortedBy { it.first }
            }
        }
    val nav = LocalNav.current
    // 时间块与课程 / 其它已排待办的重叠：重复规则（含精确间隔）一并展开，只报前几条
    val clashInstances =
        remember(whenPick?.date, whenPick?.start, whenPick?.block, freq, interval, until, byDays, byMonths) {
            val w = whenPick
            val f = freq
            val iv = interval.coerceAtLeast(1)
            val rule =
                f?.let {
                    Recurrence(
                        freq = it,
                        interval = iv,
                        byDays = if (it == Freq.DAILY) emptySet() else byDays,
                        until = until,
                        byMonths = if (it == Freq.YEARLY) byMonths else emptySet(),
                    )
                }
            when {
                w == null || w.start == null || !w.block -> emptyList()
                rule == null -> listOf(w.date)
                else -> RecurrenceEngine.dates(rule, w.date, w.date, until ?: w.date.plusMonths(6))
            }
        }
    val clashes =
        remember(clashInstances, whenPick?.start, whenPick?.minutes, snap, existing?.id, committed) {
            val ws = whenPick?.start
            val wm = whenPick?.minutes
            if (committed || ws == null || wm == null || clashInstances.isEmpty()) emptyList() else clashesOf(snap, existing?.id, clashInstances, ws, wm)
        }
    val title = text.trim()
    val desc = stringResource(if (existing == null) R.string.add_task_desc else R.string.edit_task_desc, title)

    fun commit() {
        if (committed) {
            onBack()
            return
        }
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
        val nextExtra =
            base.extra.toMutableMap().apply {
                val loc = location.trim()
                if (loc.isNotEmpty()) put("location", loc) else remove("location")
            }
        val next =
            base.copy(
                title = title,
                note = note,
                extra = nextExtra,
                attachments = attachments,
                listId = listId,
                priority = priority,
                dueBeforeNextClass = linkedCourseId != null && dueBeforeNextClass,
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
                            byDays = if (f == Freq.DAILY) emptySet() else byDays,
                            byMonths = if (f == Freq.YEARLY) byMonths else emptySet(),
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
        val mainChanged = existing == null || next != existing
        val mainChanges =
            if (mainChanged) {
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
            } else {
                emptyList()
            }
        val subTrimmed = newSub.trim()
        val subChanges =
            if (existing != null && subTrimmed.isNotEmpty()) {
                val existingSubs = TaskLogic.subtasks(snap.tasks, existing.id)
                val sub =
                    Task(
                        id = Ids.next(),
                        title = subTrimmed,
                        listId = listId,
                        parentId = existing.id,
                        sortOrder = (existingSubs.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                    )
                listOf(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, sub.title), ms))
            } else {
                emptyList()
            }
        val changes = mainChanges + subChanges
        committed = true
        if (changes.isEmpty()) {
            onBack()
            return
        }
        val feedback =
            when {
                existing != null && mainChanged -> desc
                existing != null && subChanges.isNotEmpty() -> resources.getString(R.string.add_task_desc, subTrimmed)
                existing != null -> desc
                changes.size > 1 -> resources.getString(R.string.todo_batch_toast, changes.size)
                w != null -> resources.getString(R.string.detail_editor_added_date, ctx.dueLabel(w.date.atStartOfDay(), false, today))
                else -> resources.getString(R.string.detail_editor_added_inbox)
            }
        actions.commit(ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes.flatMap { it.changes }), feedback, undoable = true)
        onBack()
    }

    // 修改：返回即保存（空标题等于放弃）；新建：返回一律不落库——带内容的草稿先确认放弃，空白直接走
    fun requestBack() {
        when {
            committed -> onBack()
            existing != null -> if (title.isNotBlank() && hasChanges) commit() else onBack()
            title.isBlank() -> onBack()
            else -> confirmDiscard = true
        }
    }
    BackHandler { requestBack() }

    var pageMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                if (existing != null) {
                    MintCheck(
                        checked = existing.done,
                        onChange = { d -> toggle(actions, snap, existing, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, existing.title)) },
                        modifier =
                            Modifier.semantics {
                                contentDescription = ctx.getString(if (existing.done) R.string.a11y_mark_undone else R.string.a11y_mark_done)
                            },
                        size = 18.dp,
                    )
                    Box {
                        IconButton(onClick = { pageMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(pageMenu, { pageMenu = false }, properties = noFocusPopup) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = {
                                    pageMenu = false
                                    if (committed) return@DropdownMenuItem
                                    committed = true
                                    // 删除不做二次确认：落库后出撤回浮钮（4.5s），回收站也能恢复
                                    actions.commit(
                                        Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.title), System.currentTimeMillis()),
                                        ctx.getString(R.string.delete_desc, existing.title),
                                    )
                                    onBack()
                                },
                            )
                        }
                    }
                } else {
                    // 新建：明确一个「确定」键，返回就是丢弃
                    TextButton(enabled = !committed && title.isNotEmpty(), onClick = { commit() }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SmartTitle(
                field,
                { field = it },
                ranges,
                titleFocus,
                done = existing?.done == true,
            )
            WhenRow(whenPick, today, remind, freq, interval, until, byDays, byMonths, onClear = {
                picked = null
                cleared = true
                remind = null
                persistent = false
                freq = null
                interval = 1
                until = null
                byDays = emptySet()
                byMonths = emptySet()
            }) { timeSheet = true }
            if (linkedCourse != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChipPill(
                        label = stringResource(R.string.detail_editor_before_class, linkedCourse.name),
                        on = dueBeforeNextClass,
                        modifier = Modifier.heightIn(min = 48.dp),
                        role = Role.Checkbox,
                    ) {
                        dueBeforeNextClass = !dueBeforeNextClass
                    }
                }
            }
            if (extraDays.isNotEmpty()) {
                // 周历框选带进来的其它日期：明说会建几条，不再静默生成
                Text(
                    stringResource(R.string.detail_editor_extra_dates, extraDays.size + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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
                                .mintClickable(pressedScale = 0.98f) {
                                    if (existing == null && hasChanges) {
                                        // 新建草稿尚未落库：走确认提示，不静默丢弃草稿
                                        confirmDiscard = true
                                    } else {
                                        nav.focusDate = c.at.toLocalDate()
                                        nav.tab = Tab.TIMETABLE
                                        if (existing != null && title.isNotEmpty()) commit()
                                        nav.clearToRoot()
                                    }
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
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            )
                            Text(
                                ctx.monthDayWeekday(c.at.toLocalDate()),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
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
            FlatField(location, stringResource(R.string.field_location), onChange = { location = it }, singleLine = true)
            FlatField(note, stringResource(R.string.label_note), onChange = { note = it }, singleLine = false)
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentEditor(attachments, onChange = { attachments = it })
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
                                    val sub = Task(Ids.next(), newSub.trim(), parentId = existing.id, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: 0) + 1, listId = listId)
                                    val subDesc = ctx.getString(R.string.add_task_desc, sub.title)
                                    actions.commit(Edits.save(snap, sub, subDesc, System.currentTimeMillis()), subDesc, undoable = true)
                                    newSub = ""
                                    subReset++
                                },
                            ) { Text(stringResource(R.string.add)) }
                        }
                    }
                }
            }
        }
        // 底部快捷操作条直接落在纸面底色上，不再抬高一层白
        // 清单 / 优先级在栏上方就地展开（与子任务同一套展开方式），不弹浮层
        AnimatedVisibility(
            visible = barPanel == BarPanel.PRIORITY,
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            SegmentedPill(
                options =
                    listOf(3 to R.string.priority_3, 2 to R.string.priority_2, 1 to R.string.priority_1, 0 to R.string.priority_0)
                        .map { it.first to stringResource(it.second) },
                selected = priority,
                onSelect = {
                    priority = it
                    barPanel = null
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = barPanel == BarPanel.LIST,
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 132.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipPill(stringResource(R.string.bucket_inbox), on = listId == null) {
                    listId = null
                    barPanel = null
                }
                snap.lists.sortedBy { it.sort }.forEach { l ->
                    ChipPill(l.name, on = listId == l.id) {
                        listId = l.id
                        barPanel = null
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentList = snap.lists.firstOrNull { it.id == listId }
                val listTint = currentList?.let { MintTheme.colors.fg(it.colorIndex) } ?: MaterialTheme.colorScheme.outline
                Row(
                    Modifier
                        .widthIn(max = 180.dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .mintClickable(pressedScale = 0.95f, role = Role.Button) {
                            barPanel = if (barPanel == BarPanel.LIST) null else BarPanel.LIST
                        }.padding(horizontal = 8.dp),
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
                IconButton(onClick = { barPanel = if (barPanel == BarPanel.PRIORITY) null else BarPanel.PRIORITY }) {
                    Icon(Icons.Rounded.Flag, stringResource(R.string.label_priority), tint = priorityColor(priority))
                }
                IconButton(onClick = pickImage) { Icon(Icons.Rounded.Image, stringResource(R.string.attach_image)) }
                IconButton(onClick = pickFile) { Icon(Icons.Rounded.UploadFile, stringResource(R.string.attach_file)) }
            }
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
            initialByMonths = byMonths,
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
                    byMonths = emptySet()
                } else {
                    picked = wp
                    cleared = false
                    if (linkedCourse != null) dueBeforeNextClass = false
                    remind = r.remindMinutes
                    persistent = r.remindPersistent
                    freq = r.freq
                    interval = r.interval
                    until = r.until
                    byDays = r.byDays
                    byMonths = r.byMonths
                }
                timeSheet = false
            },
        )
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
}

/** 标题输入：识别到的日期时间就地染色。 */
@Composable
private fun SmartTitle(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    ranges: List<IntRange>,
    focus: FocusRequester,
    done: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val hint = stringResource(R.string.entry_hint)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusRequester(focus)
                .semantics { contentDescription = hint }
                .padding(vertical = 12.dp),
        textStyle =
            MaterialTheme.typography.titleLarge.copy(
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            ),
        cursorBrush = SolidColor(accent),
        visualTransformation = TokenPaint(ranges, accent),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        hint,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                            ),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                innerTextField()
            }
        },
    )
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
    byMonths: Set<Int>,
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
                val dur = whenPick.minutes
                val end = whenPick.end?.hm().orEmpty()
                val endLabel = if (start.hour * 60 + start.minute + dur >= 24 * 60) stringResource(R.string.time_next_day, end) else end
                "${ctx.dueLabel(whenPick.date.atTime(start), true, today)} – $endLabel"
            }
        }
    val extras =
        buildList {
            if (freq != null) add(freqLabel(freq, until, interval, byDays, whenPick?.date, byMonths = byMonths))
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
            Modifier.weight(1f).heightIn(min = 48.dp).mintClickable(pressedScale = 0.98f, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Schedule, null, tint = if (empty) muted else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    primaryLine,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (empty) muted else accent,
                    fontWeight = if (empty) FontWeight.Normal else FontWeight.Medium,
                )
                if (secondary != null) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = muted)
                }
            }
        }
        if (!empty) {
            Box(
                Modifier.size(48.dp).mintClickable(pressedScale = 0.95f, role = Role.Button, onClick = onClear),
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
    BasicTextField(
        value = v,
        onValueChange = {
            v = it
            onChange(it.text)
        },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = placeholder }
                .padding(vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        decorationBox = { innerTextField ->
            Box {
                if (v.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * 优先级旗子颜色：走 MintTheme.colors 语义色（低灰绿 / 中金 / 高红，深浅主题各有一档），
 * 与待办行、批量菜单同源；0 档回退中性灰。
 */
@Composable
private fun priorityColor(p: Int): Color = MintTheme.colors.priorityColor(p) ?: MaterialTheme.colorScheme.outline
