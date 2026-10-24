package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.Tag
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDate
import java.time.LocalDateTime

private const val F_TODAY = "today"
private const val F_PLANNED = "planned"
private const val F_ALL = "all"
private const val F_DONE = "done"

@Composable
fun TodoScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val now by rememberNow()
    val today = now.toLocalDate()
    var filter by rememberSaveable { mutableStateOf(F_TODAY) }
    val sem = snap.currentSemester
    val colors = MintTheme.colors

    val listFilter = filter.removePrefix("list:").takeIf { filter.startsWith("list:") }
    val top = TaskLogic.topLevel(snap.tasks)

    val overdue = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now) }
    // 已完成的任务保留在「今日」区末尾（划线沉底），勾选后不消失
    val todayList = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.TODAY, now).filter { it !in overdue } }

    Box(Modifier.fillMaxSize()) {
        val reduce = reduceMotion()
        val listState = rememberLazyListState()
        LaunchedEffect(filter, listFilter, nav.reselect, reduce) {
            if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 240.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tab_todo), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val base = listOf(F_TODAY to R.string.filter_today, F_PLANNED to R.string.filter_planned, F_ALL to R.string.filter_all, F_DONE to R.string.filter_done)
                    items(base) { (k, label) -> FilterPill(stringResource(label), filter == k) { filter = k } }
                    items(snap.lists.sortedBy { it.sort }) { l -> FilterPill(l.name, filter == "list:${l.id}") { filter = "list:${l.id}" } }
                }
            }
            when {
                filter == F_TODAY -> {
                    if (overdue.isNotEmpty()) {
                        item(key = "h-overdue") { SectionLabel(stringResource(R.string.overdue_count, overdue.size), trailingColor = colors.removed) }
                        items(overdue, key = { it.id }) { t ->
                            TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                        }
                    }
                    item(key = "h-today") { SectionLabel(stringResource(R.string.today_count, todayList.size)) }
                    if (todayList.isEmpty()) item(key = "empty-today") { EmptyState(stringResource(R.string.todo_today_empty)) }
                    items(todayList, key = { it.id }) { t ->
                        TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                    }
                }

                filter == F_PLANNED -> {
                    val planned = TaskLogic.filter(snap.tasks, SmartList.PLANNED, now)
                    if (planned.isEmpty()) {
                        item(key = "empty-planned") { EmptyState(stringResource(R.string.todo_planned_empty)) }
                    }
                    planned.groupBy { it.due!!.toLocalDate() }.forEach { (d, ts) ->
                        item(key = "h-p$d") { SectionLabel(ctx.dueLabel(d.atStartOfDay(), false, today)) }
                        items(ts, key = { it.id }) { t ->
                            TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                        }
                    }
                }

                filter == F_ALL -> {
                    val all = TaskLogic.filter(snap.tasks, SmartList.ALL, now)
                    if (all.isEmpty()) item(key = "empty-all") { EmptyState(stringResource(R.string.todo_all_empty)) }
                    items(all, key = { it.id }) { t ->
                        TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                    }
                }

                filter == F_DONE -> {
                    val done = TaskLogic.filter(snap.tasks, SmartList.DONE, now).sortedByDescending { it.doneAt ?: 0L }
                    if (done.isEmpty()) item(key = "empty-done") { EmptyState(stringResource(R.string.todo_done_empty)) }
                    items(done, key = { it.id }) { t ->
                        TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                    }
                }

                listFilter != null -> {
                    val inList = top.filter { it.listId == listFilter }.sortedWith(compareBy({ it.done }, { it.due == null }, { it.due }, { it.sortOrder }))
                    if (inList.isEmpty()) item(key = "empty-list") { EmptyState(stringResource(R.string.todo_all_empty)) }
                    items(inList, key = { it.id }) { t ->
                        TodoRow(t, snap, now, Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()))
                    }
                }
            }
        }
        QuickAddBar(
            snap,
            now,
            filter,
            listFilter,
            Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MintTheme.card, MintTheme.spec(), label = "pill-bg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, MintTheme.spec(), label = "pill-fg")
    Box(
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(bg)
                .selectable(selected = selected, role = Role.Tab, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                })
                .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun TodoCard(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val settings = settingsState()
    val colors = MintTheme.colors
    val sem = snap.currentSemester
    val due = TaskLogic.effectiveDue(task, sem, snap.courses, snap.exceptions, now)
    val overdue = TaskLogic.isOverdue(task, now)
    val (done, total) = TaskLogic.progress(snap.tasks, task.id)
    val list = snap.lists.firstOrNull { it.id == task.listId }
    val course = snap.courses.firstOrNull { it.id == task.courseId }
    val titleColor by animateColorAsState(if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, MintTheme.spec(), label = "todo-title")
    val subProgress by animateFloatAsState(done.toFloat() / total, MintTheme.spec(), label = "todo-sub-progress")
    MintCard(modifier = modifier, onClick = { nav.push(Route.TaskDetail(task.id)) }, shape = RoundedCornerShape(24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            MintCheck(task.done, { d -> toggle(actions, snap, task, d, now, ctx.getString(R.string.done_toast, task.title)) })
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = titleColor,
                )
                val subtitle =
                    when {
                        due == null -> null
                        task.dueBeforeNextClass && course != null -> stringResource(R.string.before_next_class, course.name, ctx.dueLabel(due, true, now.toLocalDate()))
                        overdue -> stringResource(R.string.overdue_since_full, ctx.dueLabel(due, task.dueHasTime, now.toLocalDate()))
                        else -> ctx.dueLabel(due, task.dueHasTime, now.toLocalDate())
                    }
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (overdue) colors.removed else MaterialTheme.colorScheme.onSurfaceVariant)
                val bStart = task.blockStart
                if (bStart != null) {
                    val end = TaskLogic.blockEnd(task, settings.defaultBlockMinutes)!!
                    Text(stringResource(R.string.block_in_calendar, bStart.hm(), end.hm()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { subProgress }, Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
                val hasTags = list != null || task.priority >= 2 || total > 0 || task.tags.isNotEmpty()
                if (hasTags) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (list != null) Tag(list.name, colors.bg(list.colorIndex), colors.fg(list.colorIndex))
                        task.tags.take(3).forEach { Tag(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (task.priority >= 2) Tag(stringResource(if (task.priority == 3) R.string.priority_high_tag else R.string.priority_mid_tag), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        if (total > 0) Tag(stringResource(R.string.subtasks_progress, done, total), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

/** TickTick 式手势：右滑完成/恢复，左滑删除（都走变更集，可撤销）；行不滑走，靠列表状态反馈。 */
@Composable
private fun TodoRow(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val colors = MintTheme.colors
    val haptics = LocalHapticFeedback.current
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                when (v) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggle(actions, snap, task, !task.done, now, ctx.getString(R.string.done_toast, task.title))
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.commit(
                            Edits.delete(snap, task, ctx.getString(R.string.delete_desc, task.title), System.currentTimeMillis()),
                            ctx.getString(R.string.deleted_toast),
                        )
                    }

                    SwipeToDismissBoxValue.Settled -> {}
                }
                false
            },
        )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val done = state.targetValue == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background((if (done) colors.added else colors.removed).copy(alpha = 0.18f))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (done) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (done) Icons.Rounded.Check else Icons.Rounded.Delete,
                    null,
                    tint = if (done) colors.added else colors.removed,
                )
            }
        },
    ) {
        TodoCard(task, snap, now)
    }
}

/**
 * 常驻的一行添加（如「明天下午3点交作业 #概率论 !高 @期末」）：输入即解析，
 * 添加后保留焦点与当前清单，可连续录入；「更多选项」把已解析的内容带进详情页，不用重输。
 */
@Composable
private fun QuickAddBar(
    snap: Snapshot,
    now: LocalDateTime,
    filter: String,
    listFilter: String?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val haptics = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val focus = remember { FocusRequester() }
    // 键盘弹起时贴着键盘，收起时悬在底栏之上
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    fun save() {
        val p = parsed ?: return
        // shortcut: 「今天」页无日期时用 startDate 落在今天，避免次日变成红色伪逾期
        val start = if (filter == F_TODAY && p.due == null) now.toLocalDate() else null
        val where = p.due?.let { " · " + ctx.dueLabel(it, p.dueHasTime, now.toLocalDate()) }
        actions.commit(
            buildQuickTask(snap, p, listFilter, now, ctx.getString(R.string.add_task_desc, p.title), start),
            ctx.getString(R.string.task_added_toast, p.title) + (where ?: ""),
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        text = ""
        focus.requestFocus()
    }

    fun openDetail() {
        val p = parsed ?: return
        nav.push(
            Route.TaskDetail(
                id = null,
                title = p.title,
                due = p.due?.toString(),
                dueHasTime = p.dueHasTime,
                priority = p.priority,
                listId = p.listName?.let { name -> snap.lists.firstOrNull { it.name == name }?.id },
                tags = p.tags.joinToString(" "),
                startDate = if (filter == F_TODAY && p.due == null) now.toLocalDate().toString() else null,
            ),
        )
    }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = if (ime > 0.dp) 8.dp else 96.dp),
        shape = RoundedCornerShape(26.dp),
        color = MintTheme.card,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.quick_add_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                IconButton(onClick = { save() }, enabled = parsed != null) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.add))
                }
            }
            AnimatedVisibility(
                visible = parsed != null,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                val p = parsed
                if (p != null) {
                    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Tag(p.title, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
                            p.due?.let { Tag(ctx.dueLabel(it, p.dueHasTime, now.toLocalDate()), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
                            if (p.priority > 0) {
                                Tag(
                                    stringResource(
                                        when (p.priority) {
                                            3 -> R.string.priority_3
                                            2 -> R.string.priority_2
                                            else -> R.string.priority_1
                                        },
                                    ),
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            p.listName?.let { Tag("#$it", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                            p.tags.forEach { Tag("@$it", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(onClick = { openDetail() }) { Text(stringResource(R.string.more_options)) }
                    }
                }
            }
        }
    }
}

fun buildQuickTask(
    snap: Snapshot,
    p: app.mintcal.core.todo.QuickAddResult,
    defaultListId: String?,
    now: LocalDateTime,
    desc: String,
    startDate: LocalDate? = null,
): ChangeSet {
    val changes = mutableListOf<Change>()
    var listId = defaultListId
    if (p.listName != null) {
        val existing = snap.lists.firstOrNull { it.name == p.listName }
        if (existing != null) {
            listId = existing.id
        } else {
            val l = TaskList(Ids.next(), p.listName.orEmpty(), colorIndex = snap.lists.size % 8, sort = snap.lists.size)
            changes += Change.Add(l)
            listId = l.id
        }
    }
    val order = (snap.tasks.maxOfOrNull { it.sortOrder } ?: 0) + 1
    changes += Change.Add(Task(Ids.next(), p.title, listId = listId, tags = p.tags, priority = p.priority, due = p.due, dueHasTime = p.dueHasTime, startDate = startDate, sortOrder = order, source = Source.MANUAL))
    return ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes)
}
