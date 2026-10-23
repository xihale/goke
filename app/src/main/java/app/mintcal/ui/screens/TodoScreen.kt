package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.Actions
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.Tag
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.rememberNow
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
    var quickAdd by remember { mutableStateOf(false) }
    val sem = snap.currentSemester
    val colors = MintTheme.colors

    val listFilter = filter.removePrefix("list:").takeIf { filter.startsWith("list:") }
    val top = TaskLogic.topLevel(snap.tasks)

    val overdue = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now) }
    val todayList = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.TODAY, now).filter { it !in overdue } }
    val doneToday =
        remember(snap, today) {
            top.filter {
                it.done && it.doneAt != null && java.time.Instant
                    .ofEpochMilli(it.doneAt!!)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate() == today
            }
        }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
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
                        item { SectionLabel(stringResource(R.string.overdue_count, overdue.size), trailingColor = colors.removed) }
                        items(overdue, key = { "o-" + it.id }) { TodoCard(it, snap, now) }
                    }
                    item { SectionLabel(stringResource(R.string.today_count, todayList.size)) }
                    if (todayList.isEmpty()) item { Text(stringResource(R.string.todo_today_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    items(todayList, key = { "t-" + it.id }) { TodoCard(it, snap, now) }
                    if (doneToday.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.done_count, doneToday.size)) }
                        items(doneToday, key = { "d-" + it.id }) { TodoCard(it, snap, now) }
                    }
                }

                filter == F_PLANNED -> {
                    val planned = TaskLogic.filter(snap.tasks, SmartList.PLANNED, now)
                    if (planned.isEmpty()) item { Text(stringResource(R.string.todo_planned_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    planned.groupBy { it.due!!.toLocalDate() }.forEach { (d, ts) ->
                        item { SectionLabel(ctx.dueLabel(d.atStartOfDay(), false, today)) }
                        items(ts, key = { "p-" + it.id }) { TodoCard(it, snap, now) }
                    }
                }

                filter == F_ALL -> {
                    val all = TaskLogic.filter(snap.tasks, SmartList.ALL, now)
                    if (all.isEmpty()) item { Text(stringResource(R.string.todo_all_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    items(all, key = { "a-" + it.id }) { TodoCard(it, snap, now) }
                }

                filter == F_DONE -> {
                    val done = TaskLogic.filter(snap.tasks, SmartList.DONE, now).sortedByDescending { it.doneAt ?: 0L }
                    items(done, key = { "x-" + it.id }) { TodoCard(it, snap, now) }
                }

                listFilter != null -> {
                    val inList = top.filter { it.listId == listFilter }.sortedWith(compareBy({ it.done }, { it.due == null }, { it.due }, { it.sortOrder }))
                    if (inList.isEmpty()) item { Text(stringResource(R.string.todo_all_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    items(inList, key = { "l-" + it.id }) { TodoCard(it, snap, now) }
                }
            }
        }
        FloatingActionButton(
            onClick = { quickAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(22.dp),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.new_task)) }
    }
    if (quickAdd) QuickAddSheet(snap, now, listFilter, onDismiss = { quickAdd = false })
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MintTheme.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TodoCard(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val colors = MintTheme.colors
    val sem = snap.currentSemester
    val due = TaskLogic.effectiveDue(task, sem, snap.courses, snap.exceptions, now)
    val overdue = TaskLogic.isOverdue(task, now)
    val (done, total) = TaskLogic.progress(snap.tasks, task.id)
    val list = snap.lists.firstOrNull { it.id == task.listId }
    val course = snap.courses.firstOrNull { it.id == task.courseId }
    MintCard(onClick = { nav.push(Route.TaskDetail(task.id)) }, shape = RoundedCornerShape(24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            MintCheck(task.done, { d -> toggle(actions, snap, task, d, now, ctx.getString(R.string.done_toast, task.title)) })
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
                    val end = TaskLogic.blockEnd(task, 60)!!
                    Text(stringResource(R.string.block_in_calendar, bStart.hm(), end.hm()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { done.toFloat() / total }, Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceVariant)
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

/** 快速添加：一句话（如「明天下午3点交作业 #概率论 !高 @期末」）实时解析，可直接保存或进入详情。 */
@Composable
private fun QuickAddSheet(
    snap: Snapshot,
    now: LocalDateTime,
    defaultListId: String?,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        val p = parsed ?: return
        val cs = buildQuickTask(snap, p, defaultListId, now, ctx.getString(R.string.add_task_desc, p.title))
        actions.commit(cs, ctx.getString(R.string.task_added_toast))
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            if (parsed != null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag(parsed.title, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
                    parsed.due?.let { Tag(ctx.dueLabel(it, parsed.dueHasTime, now.toLocalDate()), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
                    if (parsed.priority > 0) Tag(stringResource(R.string.priority_n, parsed.priority), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    parsed.listName?.let { Tag("@$it", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                    parsed.tags.forEach { Tag("#$it", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    onDismiss()
                    nav.push(Route.TaskDetail(null))
                }) { Text(stringResource(R.string.more_options)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { save() }, enabled = parsed != null) { Text(stringResource(R.string.add)) }
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
    changes += Change.Add(Task(Ids.next(), p.title, listId = listId, tags = p.tags, priority = p.priority, due = p.due, dueHasTime = p.dueHasTime, sortOrder = order, source = Source.MANUAL))
    return ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes)
}
