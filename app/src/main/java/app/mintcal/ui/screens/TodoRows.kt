package app.mintcal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
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
import app.mintcal.ui.Route
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
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
    /** 长按：进入多选并勾上本行；combinedClickable 消费掉长按后不再触发 onClick。 */
    onLongClick: (() -> Unit)? = null,
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
        modifier =
            modifier.combinedClickable(
                onLongClick = onLongClick,
                onClick = { if (onClick != null) onClick() else nav.openTask(task.id) },
            ),
        shape = MaterialTheme.shapes.medium,
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
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue && !task.done) colors.removed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded != null && onToggleExpand != null) {
                val rot by animateFloatAsState(if (expanded) 0f else -90f, MintTheme.spec(), label = "sub-chevron")
                IconButton(onClick = onToggleExpand, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ExpandMore, stringResource(R.string.todo_toggle_subtasks), Modifier.rotate(rot), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 行手势：右滑完成/恢复；左滑不再直接删，而是弹「改期」抽屉（今天/明天/…/不做了/删除）。
 * 选择模式下关闭滑动。lambda 里一律读最新的 task / snap，避免行复用后拿到旧值。
 */
@Composable
internal fun TodoRow(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    swipeEnabled: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    swapSwipe: Boolean = false,
    expanded: Boolean?,
    onToggleExpand: (() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
    val toggleDone = {
        val t = taskNow
        toggle(actions, snapNow, t, !t.done, clock, ctx.getString(R.string.done_toast, t.title))
    }
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                when (v) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (swapSwipe) leftNow() else toggleDone()
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (swapSwipe) toggleDone() else leftNow()
                    }

                    SwipeToDismissBoxValue.Settled -> {}
                }
                false
            },
        )
    val shape = RoundedCornerShape(16.dp)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = swipeEnabled,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            // 右滑（StartToEnd）默认显示完成；交换后显示改期
            val right = state.targetValue == SwipeToDismissBoxValue.StartToEnd
            val doneSide = right != swapSwipe
            val bg = if (doneSide) colors.added else MaterialTheme.colorScheme.tertiary
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(bg.copy(alpha = 0.18f))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(if (doneSide) Icons.Rounded.Check else Icons.Rounded.Event, null, tint = bg)
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
            onLongClick = onLongClick,
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
        modifier
            .fillMaxWidth()
            .padding(start = 28.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { nav.openTask(sub.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.mintcal.ui.components
            .MintCheck(sub.done, { d -> toggle(actions, snap, sub, d, now, ctx.getString(R.string.done_toast, sub.title)) })
        Text(
            sub.title,
            Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
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
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rot by animateFloatAsState(if (collapsed) -90f else 0f, MintTheme.spec(), label = "section-chevron")
    val color = if (bucket == Bucket.OVERDUE) MintTheme.colors.removed else MaterialTheme.colorScheme.onSurfaceVariant
    val expansion = stringResource(if (collapsed) R.string.state_collapsed else R.string.state_expanded)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .semantics { stateDescription = expansion }
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = 6.dp),
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
            IconButton(onClick = onAdd, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.todo_add_here), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    val nav = LocalNav.current
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MintTheme.card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.todo_next_up), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (up != null) {
                val o = up.occurrence
                val whenText = (if (o.date == today) "" else ctx.dueLabel(o.date.atStartOfDay(), false, today) + " ") + o.start.hm()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { nav.push(Route.CourseEdit(o.course.id)) }
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        o.course.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(whenText, o.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (up.tasks.isEmpty()) {
                    Text(stringResource(R.string.todo_next_up_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        app.mintcal.ui.components
            .MintCheck(t.done, { d -> toggle(actions, snap, t, d, now, ctx.getString(R.string.done_toast, t.title)) })
        Text(
            t.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
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

/** 无框输入框配色：只留文字与光标。 */
@androidx.compose.runtime.Composable
private fun transparentFieldColors() =
    androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    )
