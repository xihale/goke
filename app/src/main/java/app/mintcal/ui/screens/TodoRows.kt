package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.ParcelCodeCopyButton
import app.mintcal.ui.components.ParcelCodeHeadline
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.components.parcelCode
import app.mintcal.ui.components.parcelLabel
import app.mintcal.ui.dueLabel
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import java.time.LocalDateTime

private val RowSwipeShape = RoundedCornerShape(16.dp)
private val SubAddFieldShape = RoundedCornerShape(12.dp)

/** 行级查询索引：每页待办只建一次，行内不再对全量任务/清单/课程做线性扫描（200 条待办 × 10 可见行 ≈ 2000 次全扫 → 0）。 */
@androidx.compose.runtime.Immutable
class TodoIndex(
    val progress: Map<String, Pair<Int, Int>>,
    val due: Map<String, LocalDateTime?>,
    val listById: Map<String, app.mintcal.core.model.TaskList>,
    val courseById: Map<String, app.mintcal.core.model.Course>,
    val subtasksByParent: Map<String, List<Task>> = emptyMap(),
    val topTasks: List<Task> = emptyList(),
)

fun todoIndexOf(
    snap: Snapshot,
    now: LocalDateTime,
): TodoIndex {
    val sem = snap.currentSemester
    val tasks = snap.tasks
    val subtasks = HashMap<String, MutableList<Task>>()
    val topTasks = ArrayList<Task>(tasks.size)
    val due = HashMap<String, LocalDateTime?>(tasks.size)
    var nextClassByCourse: HashMap<String, LocalDateTime?>? = null
    for (t in tasks) {
        if (t.deletedAt != null) continue
        val pid = t.parentId
        if (pid != null) {
            subtasks.getOrPut(pid) { ArrayList(4) }.add(t)
        } else {
            topTasks.add(t)
            val cid = t.courseId
            due[t.id] =
                if (t.dueBeforeNextClass && cid != null && sem != null) {
                    val cache = nextClassByCourse ?: HashMap<String, LocalDateTime?>().also { nextClassByCourse = it }
                    val nextStart =
                        if (cache.containsKey(cid)) {
                            cache[cid]
                        } else {
                            TaskLogic.effectiveDue(t.copy(due = null), sem, snap.courses, snap.exceptions, now).also { cache[cid] = it }
                        }
                    nextStart ?: t.due
                } else {
                    t.due
                }
        }
    }
    val progressMap = HashMap<String, Pair<Int, Int>>(subtasks.size)
    for ((pid, subs) in subtasks) {
        if (subs.size > 1) subs.sortBy { it.sortOrder }
        var doneCount = 0
        for (s in subs) {
            if (s.done) doneCount++
        }
        progressMap[pid] = doneCount to subs.size
    }
    return TodoIndex(
        progress = progressMap,
        due = due,
        listById = snap.lists.associateBy { it.id },
        courseById = snap.courses.associateBy { it.id },
        subtasksByParent = subtasks,
        topTasks = topTasks,
    )
}

/** 稳定/不可变的行展示模型，隔离单条勾选对全列表的重组波及。 */
@androidx.compose.runtime.Immutable
data class TodoRowModel(
    val task: Task,
    val listName: String? = null,
    val listColorIndex: Int? = null,
    val courseName: String? = null,
    val dueText: String? = null,
    val isOverdue: Boolean = false,
    val subtasksDone: Int = 0,
    val subtasksTotal: Int = 0,
    val metaText: String = "",
    val hasSubtasks: Boolean = false,
    val expanded: Boolean = false,
    val selected: Boolean = false,
    val parcelCode: String? = null,
    val parcelStation: String? = null,
    val subtitleText: String =
        listOfNotNull(
            parcelStation,
            dueText,
            metaText.takeIf { it.isNotEmpty() },
        ).joinToString(" · "),
)

fun buildTodoRowModel(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    index: TodoIndex,
    expanded: Boolean = false,
    selected: Boolean = false,
    context: android.content.Context,
): TodoRowModel {
    val due = if (task.id in index.due) index.due[task.id] else TaskLogic.effectiveDue(task, snap.currentSemester, snap.courses, snap.exceptions, now)
    val overdue = TaskLogic.isOverdue(task, now)
    val (done, total) = index.progress[task.id] ?: (0 to 0)
    val list = index.listById[task.listId]
    val course = index.courseById[task.courseId]
    val code = task.parcelCode()
    val station = if (code != null) task.parcelLabel()?.takeIf { it.isNotBlank() } else null
    val meta =
        buildList {
            if (task.dropped) add(context.getString(R.string.todo_dropped_mark))
            list?.let { add(it.name) }
            if (total > 0) add(context.getString(R.string.subtasks_progress, done, total))
            task.tags.take(2).forEach { add("#$it") }
        }.joinToString(" · ")
    val dueText =
        due?.let {
            if (task.dueBeforeNextClass && course != null) {
                context.getString(R.string.before_next_class_short, course.name)
            } else {
                context.dueLabel(it, task.dueHasTime, now.toLocalDate())
            }
        }
    val subtitleText =
        listOfNotNull(
            station,
            dueText,
            meta.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    return TodoRowModel(
        task = task,
        listName = list?.name,
        listColorIndex = list?.colorIndex,
        courseName = course?.name,
        dueText = dueText,
        isOverdue = overdue,
        subtasksDone = done,
        subtasksTotal = total,
        metaText = meta,
        hasSubtasks = total > 0,
        expanded = expanded && total > 0,
        selected = selected,
        parcelCode = code,
        parcelStation = station,
        subtitleText = subtitleText,
    )
}

/** 兼容旧版 Set<String> 签名的重载。 */
fun buildTodoRowModel(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    index: TodoIndex,
    expanded: Set<String>,
    selected: Set<String>,
    context: android.content.Context,
): TodoRowModel =
    buildTodoRowModel(
        task = task,
        snap = snap,
        now = now,
        index = index,
        expanded = task.id in expanded,
        selected = task.id in selected,
        context = context,
    )

/** 拆分后的高性能单行视图：精简冗余动画状态，直接通过 static 颜色与 mintClickable 呈现。 */
@Composable
fun TodoLine(
    model: TodoRowModel,
    modifier: Modifier = Modifier,
    selectMode: Boolean = false,
    selected: Boolean = model.selected,
    expanded: Boolean = model.expanded,
    onToggle: (Task, Boolean) -> Unit = { _, _ -> },
    onClick: (Task) -> Unit = {},
    onLongClick: ((Task) -> Unit)? = null,
    onToggleExpand: ((String) -> Unit)? = null,
) {
    val colors = MintTheme.colors
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val onSurfaceVariant = scheme.onSurfaceVariant
    val bodySmall = typography.bodySmall
    val isDone = model.task.done
    val doneAlpha = 0.65f
    val subColor = if (isDone) onSurfaceVariant.copy(alpha = doneAlpha) else onSurfaceVariant
    val titleColor = if (isDone) subColor else scheme.onSurface
    val ring =
        when (model.task.priority) {
            3 -> 3.5.dp
            2 -> 2.75.dp
            else -> 2.dp
        }
    val code = model.parcelCode
    val station = model.parcelStation
    val isRowSelected = if (selectMode) selected else false
    val rowBgModifier =
        if (isRowSelected) {
            Modifier.background(scheme.primaryContainer.copy(alpha = 0.45f), shape = MaterialTheme.shapes.medium)
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(rowBgModifier)
                .mintClickable(
                    enabled = !(selectMode && isDone),
                    pressedScale = 0.98f,
                    onLongClick =
                        if (onLongClick != null && !isDone) {
                            { onLongClick(model.task) }
                        } else {
                            null
                        },
                ) {
                    onClick(model.task)
                }.heightIn(min = 48.dp)
                .padding(start = 4.dp, end = if (code != null && !selectMode && !model.hasSubtasks) 4.dp else 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isDone) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = scheme.outline.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (selected) scheme.primary else scheme.outline,
                    )
                }
            }
        } else {
            MintCheck(
                isDone,
                { d -> onToggle(model.task, d) },
                tint = model.listColorIndex?.let { colors.fg(it) },
                strokeWidth = ring,
            )
        }
        Column(Modifier.weight(1f)) {
            if (code != null) {
                ParcelCodeHeadline(code = code, done = isDone, copyEnabled = !selectMode)
            } else {
                Text(
                    model.task.title,
                    style = typography.bodyLarge,
                    color = titleColor,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val dueText = model.dueText
            val meta = model.metaText
            val hasSubtitle = model.subtitleText.isNotEmpty()
            if (hasSubtitle) {
                if (model.isOverdue && !isDone && dueText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                        if (station != null) {
                            Text(
                                "$station · ",
                                style = bodySmall,
                                color = onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(
                            dueText,
                            style = bodySmall,
                            color = colors.removed,
                            maxLines = 1,
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                " · $meta",
                                style = bodySmall,
                                color = onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        model.subtitleText,
                        style = bodySmall,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        val showFlag = code == null || (model.task.source != Source.PARCEL && model.task.priority > 0) || model.task.priority >= 3
        if (showFlag) {
            colors.priorityColor(model.task.priority)?.let { prio ->
                val flagTint = if (isDone) prio.copy(alpha = 0.45f) else prio
                Icon(Icons.Rounded.Flag, null, Modifier.padding(end = if (code != null && !selectMode) 2.dp else 8.dp).size(14.dp), tint = flagTint)
            }
        }
        if (code != null && !selectMode) {
            ParcelCodeCopyButton(code)
        }
        if (model.hasSubtasks && onToggleExpand != null) {
            val chevronMod = if (expanded) Modifier else Modifier.rotate(-90f)
            IconButton(onClick = { onToggleExpand(model.task.id) }, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ExpandMore, stringResource(R.string.todo_toggle_subtasks), chevronMod, tint = subColor)
            }
        }
    }
}

/** 兼容调用方（如 CourseEditScreen）的原签名重载。 */
@Composable
fun TodoLine(
    task: Task,
    snap: Snapshot,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    index: TodoIndex? = null,
    selectMode: Boolean = false,
    selected: Boolean = false,
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val actualIndex = index ?: remember(snap, now) { todoIndexOf(snap, now) }
    val isExpanded = expanded == true
    val model =
        remember(task, actualIndex.due[task.id], actualIndex.progress[task.id], actualIndex.listById[task.listId]) {
            buildTodoRowModel(
                task = task,
                snap = snap,
                now = now,
                index = actualIndex,
                expanded = false,
                selected = false,
                context = ctx,
            )
        }
    TodoLine(
        model = model,
        modifier = modifier,
        selectMode = selectMode,
        selected = selected,
        expanded = isExpanded,
        onToggle = { t, d -> toggle(actions, snap, t, d, now, ctx.getString(R.string.done_toast, t.title)) },
        onClick = { if (onClick != null) onClick() else nav.openTask(task.id) },
        onLongClick = onLongClick?.let { l -> { l() } },
        onToggleExpand = onToggleExpand?.let { e -> { e() } },
    )
}

/** 优化的待办行组件：接收稳定行模型，手势与勾选回调解耦。 */
@Suppress("DEPRECATION")
@Composable
internal fun TodoRow(
    model: TodoRowModel,
    swipeEnabled: Boolean,
    selectMode: Boolean,
    swapSwipe: Boolean = false,
    expanded: Boolean = model.expanded,
    selected: Boolean = model.selected,
    onToggle: (Task, Boolean) -> Unit,
    onClick: (Task) -> Unit,
    onLongClick: ((Task) -> Unit)? = null,
    onSwipeLeft: (Task) -> Unit,
    onToggleExpand: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MintTheme.colors
    val haptics = LocalHapticFeedback.current
    val modelNow by rememberUpdatedState(model)
    val onToggleNow by rememberUpdatedState(onToggle)
    val onSwipeLeftNow by rememberUpdatedState(onSwipeLeft)
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                val currentTask = modelNow.task
                when (v) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (swapSwipe) onSwipeLeftNow(currentTask) else onToggleNow(currentTask, !currentTask.done)
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (swapSwipe) onToggleNow(currentTask, !currentTask.done) else onSwipeLeftNow(currentTask)
                    }

                    SwipeToDismissBoxValue.Settled -> {}
                }
                false
            },
        )
    val direction = state.dismissDirection
    val swiping = direction != SwipeToDismissBoxValue.Settled
    SwipeToDismissBox(
        state = state,
        modifier = if (swiping) modifier.clip(RowSwipeShape) else modifier,
        enableDismissFromStartToEnd = swipeEnabled,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            if (swiping) {
                val right = direction == SwipeToDismissBoxValue.StartToEnd
                val doneSide = right != swapSwipe
                val bg = if (doneSide) colors.added else MaterialTheme.colorScheme.tertiary
                val icon = if (doneSide) Icons.Rounded.Check else Icons.Rounded.Event
                val label =
                    stringResource(
                        when {
                            doneSide && model.task.done -> R.string.a11y_mark_undone
                            doneSide -> R.string.todo_complete
                            else -> R.string.todo_reschedule
                        },
                    )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(bg.copy(alpha = 0.18f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (right) {
                            Icon(icon, null, tint = bg, modifier = Modifier.size(20.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = bg)
                        } else {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = bg)
                            Icon(icon, null, tint = bg, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
    ) {
        TodoLine(
            model = model,
            modifier = if (swiping) Modifier.background(MaterialTheme.colorScheme.background, RowSwipeShape) else Modifier,
            selectMode = selectMode,
            selected = selected,
            expanded = expanded,
            onToggle = onToggle,
            onClick = onClick,
            onLongClick = onLongClick,
            onToggleExpand = onToggleExpand,
        )
    }
}

/** 兼容旧版 TodoRow 签名的重载。 */
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
    index: TodoIndex? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val actualIndex = index ?: remember(snap, now) { todoIndexOf(snap, now) }
    val isExpanded = expanded == true
    val model =
        remember(task, actualIndex.due[task.id], actualIndex.progress[task.id], actualIndex.listById[task.listId]) {
            buildTodoRowModel(
                task = task,
                snap = snap,
                now = now,
                index = actualIndex,
                expanded = false,
                selected = false,
                context = ctx,
            )
        }
    TodoRow(
        model = model,
        swipeEnabled = swipeEnabled,
        selectMode = selectMode,
        selected = selected,
        expanded = isExpanded,
        swapSwipe = swapSwipe,
        onToggle = { t, d -> toggle(actions, snap, t, d, now, ctx.getString(R.string.done_toast, t.title)) },
        onClick = { onClick() },
        onLongClick = onLongClick?.let { l -> { l() } },
        onSwipeLeft = { onSwipeLeft() },
        onToggleExpand = onToggleExpand?.let { e -> { e() } },
        modifier = modifier,
    )
}

/** 展开后缩进显示的子任务行：移除冗余 animateFloatAsState 与行级图层。 */
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
    val scheme = MaterialTheme.colorScheme
    val doneAlpha = 0.65f
    val titleColor =
        if (sub.done) {
            scheme.onSurfaceVariant.copy(alpha = doneAlpha)
        } else {
            scheme.onSurface
        }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 40.dp)
            .clip(MaterialTheme.shapes.medium)
            .mintClickable(pressedScale = 0.98f) { nav.openTask(sub.id) }
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintCheck(sub.done, { d -> toggle(actions, snap, sub, d, now, ctx.getString(R.string.done_toast, sub.title)) })
        Text(
            sub.title,
            Modifier.weight(1f).padding(start = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
            textDecoration = if (sub.done) TextDecoration.LineThrough else null,
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
    // 和子任务行同一列：卡片缩进 40 + 内边距 8 + 勾选框 48 = 96dp 的文字列
    // （TextField 带 leadingIcon 时文字自身还有约 2dp 内缩，所以这里给 46）
    TextField(
        text,
        { text = it },
        modifier.fillMaxWidth().padding(start = 46.dp),
        leadingIcon = { Spacer(Modifier.width(48.dp)) },
        placeholder = { Text(stringResource(R.string.add_subtask), style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = SubAddFieldShape,
        colors = transparentFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = { IconButton(onClick = { add() }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, stringResource(R.string.add)) } },
    )
}

/** 分区标题：点击折叠；右侧「＋」把新增默认落到该分区；[action] 是分区级动作（如逾期整理）。 */
@Composable
internal fun SectionHeader(
    bucket: Bucket,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MintTheme.colors
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val smallShape = MaterialTheme.shapes.small
    val onSurfaceVariant = scheme.onSurfaceVariant
    val isOverdue = bucket == Bucket.OVERDUE
    // 逾期桶整组用警示色；已完成弱化；其余正常标题色
    val titleColor =
        when (bucket) {
            Bucket.OVERDUE -> colors.removed
            Bucket.DONE -> onSurfaceVariant
            else -> scheme.onSurface
        }
    val pillFg = if (isOverdue) colors.removed else onSurfaceVariant
    val pillBg = pillFg.copy(alpha = 0.1f)
    val expansion = stringResource(if (collapsed) R.string.state_collapsed else R.string.state_expanded)
    val chevronMod = if (collapsed) Modifier.rotate(-90f).size(20.dp) else Modifier.size(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(smallShape)
            .semantics { stateDescription = expansion }
            .mintClickable(role = Role.Button, pressedScale = 0.98f, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ExpandMore, null, chevronMod, tint = titleColor)
        Text(
            stringResource(bucketRes(bucket)),
            Modifier.weight(1f).padding(start = 6.dp),
            style = typography.labelLarge,
            color = titleColor,
        )
        // 计数做成小胶囊，不再和标题拼字符串
        Text(
            count.toString(),
            Modifier
                .clip(smallShape)
                .background(pillBg)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = typography.labelMedium,
            color = pillFg,
        )
        if (action != null) {
            action()
        }
        if (onAdd != null) {
            IconButton(onClick = onAdd, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.todo_add_here), tint = onSurfaceVariant)
            }
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
