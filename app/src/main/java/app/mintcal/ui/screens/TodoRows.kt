package app.mintcal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.NextUp
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDateTime

@Composable
fun TodoLine(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    selectMode: Boolean = false,
    selected: Boolean = false,
    /** 有子任务时：null 不显示展开箭头；否则显示并反映当前展开状态。 */
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val colors = MintTheme.colors
    val due = TaskLogic.effectiveDue(task, snap.currentSemester, snap.courses, snap.exceptions, now)
    val overdue = TaskLogic.isOverdue(task, now)
    val (done, total) = TaskLogic.progress(snap.tasks, task.id)
    val list = snap.lists.firstOrNull { it.id == task.listId }
    val course = snap.courses.firstOrNull { it.id == task.courseId }
    val titleColor by animateColorAsState(if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, MintTheme.spec(), label = "todo-title")
    // 圈色 = 清单色，圈粗 = 优先级：不占一整行也能一眼分辨
    val ring =
        when (task.priority) {
            3 -> 3.5.dp
            2 -> 2.75.dp
            else -> 2.dp
        }
    val meta =
        buildList {
            if (task.dropped) add(ctx.getString(R.string.todo_dropped_mark))
            list?.let { add(it.name) }
            if (total > 0) add(ctx.getString(R.string.subtasks_progress, done, total))
            task.tags.take(2).forEach { add("#$it") }
        }
    Surface(
        onClick = { if (onClick != null) onClick() else nav.openTask(task.id) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MintTheme.card,
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(start = 4.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                app.mintcal.ui.components.MintCheck(
                    task.done,
                    { d -> toggle(actions, snap, task, d, now, ctx.getString(R.string.done_toast, task.title)) },
                    tint = list?.let { colors.fg(it.colorIndex) },
                    strokeWidth = ring,
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (due != null) {
                val label =
                    if (task.dueBeforeNextClass && course != null) {
                        stringResource(R.string.before_next_class_short, course.name)
                    } else {
                        ctx.dueLabel(due, task.dueHasTime, now.toLocalDate())
                    }
                Text(
                    label,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue && !task.done) colors.removed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (expanded != null && onToggleExpand != null) {
                val rot by animateFloatAsState(if (expanded) 0f else -90f, MintTheme.spec(), label = "sub-chevron")
                IconButton(onClick = onToggleExpand, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.ExpandMore, stringResource(R.string.todo_toggle_subtasks), Modifier.rotate(rot), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 行手势：右滑完成/恢复；左滑不再直接删，而是弹「改期」抽屉（今天/明天/…/不做了/删除）。
 * 选择模式或拖动中关闭滑动。lambda 里一律读最新的 task / snap，避免行复用后拿到旧值。
 */
@Composable
internal fun TodoRow(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    swipeEnabled: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    dim: Boolean,
    subTarget: Boolean,
    tint: Boolean,
    expanded: Boolean?,
    onToggleExpand: (() -> Unit)?,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val colors = MintTheme.colors
    val haptics = LocalHapticFeedback.current
    val taskNow by rememberUpdatedState(task)
    val snapNow by rememberUpdatedState(snap)
    val clock by rememberUpdatedState(now)
    val leftNow by rememberUpdatedState(onSwipeLeft)
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                when (v) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val t = taskNow
                        toggle(actions, snapNow, t, !t.done, clock, ctx.getString(R.string.done_toast, t.title))
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        leftNow()
                    }

                    SwipeToDismissBoxValue.Settled -> {}
                }
                false
            },
        )
    val shape = RoundedCornerShape(16.dp)
    SwipeToDismissBox(
        state = state,
        modifier =
            modifier
                .alpha(if (dim) 0.35f else 1f)
                .clip(shape)
                .then(if (tint) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else Modifier)
                .then(if (subTarget) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier),
        enableDismissFromStartToEnd = swipeEnabled,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            val right = state.targetValue == SwipeToDismissBoxValue.StartToEnd
            val bg = if (right) colors.added else MaterialTheme.colorScheme.tertiary
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(bg.copy(alpha = 0.18f))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(if (right) Icons.Rounded.Check else Icons.Rounded.Event, null, tint = bg)
            }
        },
    ) {
        TodoLine(
            task,
            snap,
            now,
            selectMode = selectMode,
            selected = selected,
            expanded = expanded,
            onToggleExpand = onToggleExpand,
            onClick = onClick,
        )
    }
}

/** 展开后缩进显示的子任务行。 */
@Composable
internal fun SubRow(
    sub: Task,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    Row(
        modifier.fillMaxWidth().padding(start = 28.dp).clip(RoundedCornerShape(12.dp)).clickable { nav.openTask(sub.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.mintcal.ui.components.MintCheck(sub.done, { d -> toggle(actions, snap, sub, d, now, ctx.getString(R.string.done_toast, sub.title)) })
        Text(
            sub.title,
            Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (sub.done) TextDecoration.LineThrough else null,
            color = if (sub.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行内「＋子任务」输入。 */
@Composable
internal fun InlineSubAdd(
    parent: Task,
    snap: Snapshot,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    var text by remember { mutableStateOf("") }

    fun add() {
        val title = text.trim()
        if (title.isEmpty()) return
        val order = (TaskLogic.subtasks(snap.tasks, parent.id).maxOfOrNull { it.sortOrder } ?: 0) + 1
        val sub = Task(Ids.next(), title, listId = parent.listId, parentId = parent.id, sortOrder = order)
        actions.commit(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, title), System.currentTimeMillis()))
        text = ""
    }
    TextField(
        text,
        { text = it },
        modifier.fillMaxWidth().padding(start = 28.dp),
        placeholder = { Text(stringResource(R.string.add_subtask), style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = transparentFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = { IconButton(onClick = { add() }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, stringResource(R.string.add)) } },
    )
}

/** 分区标题：点击折叠；右侧「＋」把新增默认落到该分区。 */
@Composable
internal fun SectionHeader(
    bucket: Bucket,
    count: Int,
    collapsed: Boolean,
    hot: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rot by animateFloatAsState(if (collapsed) -90f else 0f, MintTheme.spec(), label = "section-chevron")
    val bg by animateColorAsState(if (hot) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, MintTheme.spec(), label = "section-hot")
    val color = if (bucket == Bucket.OVERDUE) MintTheme.colors.removed else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onToggle).heightIn(min = 44.dp).padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rot).size(20.dp), tint = color)
        Text(
            stringResource(bucketRes(bucket)) + "  " + count,
            Modifier.weight(1f).padding(start = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd, Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.todo_add_here), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 拖动时空分区的落点。 */
@Composable
internal fun DropPlaceholder(
    hot: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (hot) MaterialTheme.colorScheme.primaryContainer else MintTheme.card.copy(alpha = 0.5f), MintTheme.spec(), label = "drop-hot")
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.todo_drop_here), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 顶部「下一件事」：下一节课 + 要在课前交的待办；没课时回退为最紧急的一条。 */
@Composable
internal fun NextUpCard(
    up: NextUp?,
    urgent: Task?,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val today = now.toLocalDate()
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.todo_next_up), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            if (up != null) {
                val o = up.occurrence
                val whenText = (if (o.date == today) "" else ctx.dueLabel(o.date.atStartOfDay(), false, today) + " ") + o.start.hm()
                Text(
                    listOf(o.course.name + " " + whenText, o.room.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (up.tasks.isEmpty()) {
                    Text(stringResource(R.string.todo_next_up_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
                up.tasks.take(3).forEach { t -> NextUpTask(t, snap, now) }
            } else if (urgent != null) {
                NextUpTask(urgent, snap, now)
            }
        }
    }
}

@Composable
private fun NextUpTask(
    t: Task,
    snap: Snapshot,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().clickable { nav.openTask(t.id) }, verticalAlignment = Alignment.CenterVertically) {
        app.mintcal.ui.components.MintCheck(t.done, { d -> toggle(actions, snap, t, d, now, ctx.getString(R.string.done_toast, t.title)) })
        Text(
            t.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textDecoration = if (t.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 逾期整理入口。 */
@Composable
internal fun OverdueCard(
    count: Int,
    onTidy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MintTheme.colors.removed.copy(alpha = 0.12f)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.todo_overdue_card, count), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onTidy) { Text(stringResource(R.string.todo_tidy)) }
        }
    }
}
