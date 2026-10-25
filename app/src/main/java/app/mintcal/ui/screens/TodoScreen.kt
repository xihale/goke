package app.mintcal.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.NextUps
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.todo.TodoSearch
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNow
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

private val DROPPABLE = listOf(Bucket.TODAY, Bucket.TOMORROW, Bucket.THIS_WEEK, Bucket.LATER, Bucket.INBOX)

/** 拖动回调里读最新数据用（pointerInput 只在 key 变化时重启，不能直接捕获快照）。 */
private class DragEnv(
    val snap: Snapshot,
    val taskById: Map<String, Task>,
    val bucketOfTask: Map<String, Bucket>,
    val today: LocalDate,
    val dropBuckets: List<Bucket>,
)

/**
 * 待办页：一条时间线（逾期 / 今天 / 明天 / 本周 / 之后 / 收集箱 / 已完成），分区可折叠，
 * 顶部有「下一件事」与逾期整理；右滑完成、左滑改期、长按拖到别的分区改期（拖到行右侧 = 变子任务）、
 * 顶栏按钮进入多选，搜索支持 `#清单 @标签 !高`。
 */
@Composable
fun TodoScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val snap = liveSnapshot()
    val now by rememberNow()
    val today = now.toLocalDate()

    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var queryText by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    var collapsedCsv by rememberSaveable { mutableStateOf("LATER,DONE") }
    var expandedCsv by rememberSaveable { mutableStateOf("") }
    var addTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var focusSignal by rememberSaveable { mutableIntStateOf(0) }
    var rescheduleFor by remember { mutableStateOf<List<String>?>(null) }
    var tidy by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    val drag = remember { TodoDrag() }
    val searchFocus = remember { FocusRequester() }

    val q = remember(queryText) { TodoSearch.parse(queryText) }
    val visible =
        remember(snap, listFilter, q) {
            snap.tasks.filter { t ->
                t.deletedAt == null && t.parentId == null && (listFilter == null || t.listId == listFilter) &&
                    TodoSearch.matches(q, t, TaskLogic.subtasks(snap.tasks, t.id), snap.lists.firstOrNull { it.id == t.listId }?.name)
            }
        }
    val sections =
        remember(snap, visible, now) {
            TaskLogic.timeline(visible, now) { TaskLogic.effectiveDue(it, snap.currentSemester, snap.courses, snap.exceptions, now) }
        }
    val taskById = remember(snap) { snap.tasks.associateBy { it.id } }
    val bucketOfTask = remember(sections) { sections.flatMap { s -> s.tasks.map { it.id to s.bucket } }.toMap() }
    val overdueTasks = sections.firstOrNull { it.bucket == Bucket.OVERDUE }?.tasks.orEmpty()
    val nextUp = remember(snap, now) { NextUps.find(snap, now) }
    val urgent = overdueTasks.firstOrNull() ?: sections.firstOrNull { it.bucket == Bucket.TODAY }?.tasks?.firstOrNull()
    val dropBuckets = DROPPABLE.filter { TaskLogic.dropDate(it, today) != TaskLogic.NO_DROP }
    val env by rememberUpdatedState(DragEnv(snap, taskById, bucketOfTask, today, dropBuckets))

    val expanded = remember(expandedCsv) { csvToSet(expandedCsv) }
    val collapsed = remember(collapsedCsv) { csvToSet(collapsedCsv) }
    val selected = remember(selectedCsv) { csvToSet(selectedCsv) }

    fun toggleIn(
        csv: String,
        key: String,
    ): String {
        val s = csvToSet(csv)
        return (if (key in s) s - key else s + key).toCsv()
    }

    fun exitSelect() {
        selecting = false
        selectedCsv = ""
    }

    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }

    fun hitKey(y: Float): String? =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y >= it.offset && y < it.offset + it.size }
            ?.key as? String

    fun updateHover() {
        val e = env
        val dragged = drag.task ?: return
        val info = listState.layoutInfo
        val key = hitKey(drag.pointer.y)
        var bucket: Bucket? = null
        var sub: String? = null
        if (key != null) {
            if (key.startsWith("h-") || key.startsWith("e-")) {
                bucket = Bucket.valueOf(key.substring(2))
            } else {
                val b = e.bucketOfTask[key]
                val target = e.taskById[key]
                if (b != null && target != null) {
                    val rightHalf = drag.pointer.x > info.viewportSize.width * 0.6f
                    if (rightHalf && target.id != dragged.id && !target.done && !Edits.makeSubtask(e.snap, dragged, target, "", 0).isEmpty) {
                        sub = target.id
                    } else {
                        bucket = b
                    }
                }
            }
        }
        // 落回自己所在的分区 = 不动
        if (bucket != null && (bucket !in e.dropBuckets || bucket == e.bucketOfTask[dragged.id])) bucket = null
        drag.overBucket = bucket
        drag.overTaskId = sub
    }

    fun finishDrag() {
        val e = env
        val t = drag.task
        val sub = drag.overTaskId
        val bucket = drag.overBucket
        drag.reset()
        if (t == null) return
        val ms = System.currentTimeMillis()
        if (sub != null) {
            val parent = e.taskById[sub] ?: return
            val cs = Edits.makeSubtask(e.snap, t, parent, ctx.getString(R.string.todo_subtask_desc, t.title), ms)
            if (!cs.isEmpty) actions.commit(cs, ctx.getString(R.string.todo_subtask_toast, t.title, parent.title))
        } else if (bucket != null) {
            val d = TaskLogic.dropDate(bucket, e.today)
            if (d == TaskLogic.NO_DROP) return
            val cs = Edits.reschedule(listOf(t), d, ctx.getString(R.string.todo_reschedule_desc, t.title), ms)
            if (!cs.isEmpty) actions.commit(cs, ctx.getString(R.string.todo_moved_to_toast, ctx.getString(bucketRes(bucket))))
        }
    }

    // 拖动期间：靠边自动滚动，并随滚动刷新落点
    LaunchedEffect(drag.active) {
        while (drag.active) {
            val h = listState.layoutInfo.viewportSize.height
            val edge = with(density) { 96.dp.toPx() }
            val bottomEdge = h - with(density) { 220.dp.toPx() }
            val y = drag.pointer.y
            val speed =
                when {
                    y < edge -> -((edge - y) / edge).coerceIn(0f, 1f) * 28f
                    y > bottomEdge -> ((y - bottomEdge) / edge).coerceIn(0f, 1f) * 28f
                    else -> 0f
                }
            if (speed != 0f) listState.scrollBy(speed)
            updateHover()
            delay(16)
        }
    }

    val selectedTasks = selected.mapNotNull { taskById[it] }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        val anim: @Composable LazyItemScope.() -> Modifier = {
            Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec())
        }

        fun LazyListScope.taskRows(
            t: Task,
            bucket: Bucket,
        ) {
            val subs = TaskLogic.subtasks(snap.tasks, t.id)
            val open = t.id in expanded && subs.isNotEmpty()
            item(key = t.id) {
                TodoRow(
                    task = t,
                    snap = snap,
                    now = now,
                    swipeEnabled = !selecting && !drag.active,
                    selectMode = selecting,
                    selected = t.id in selected,
                    dim = drag.task?.id == t.id,
                    subTarget = drag.overTaskId == t.id,
                    tint = drag.active && drag.overBucket == bucket,
                    expanded = if (subs.isNotEmpty()) open else null,
                    onToggleExpand = { expandedCsv = toggleIn(expandedCsv, t.id) },
                    onClick = {
                        if (!drag.active) {
                            if (selecting) selectedCsv = toggleIn(selectedCsv, t.id) else nav.openTask(t.id)
                        }
                    },
                    onSwipeLeft = { rescheduleFor = listOf(t.id) },
                    modifier = anim(),
                )
            }
            if (open) {
                subs.forEach { s -> item(key = "s-${s.id}") { SubRow(s, snap, now, anim()) } }
                item(key = "a-${t.id}") { InlineSubAdd(t, snap, anim()) }
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = !drag.active,
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(selecting) {
                        if (selecting) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                val t = hitKey(off.y)?.let { env.taskById[it] }
                                if (t != null && !t.done && t.parentId == null && env.bucketOfTask.containsKey(t.id)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.start(t, off)
                                    updateHover()
                                }
                            },
                            onDrag = { change, _ ->
                                if (drag.active) {
                                    change.consume()
                                    drag.pointer = change.position
                                }
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { drag.reset() },
                        )
                    },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "top") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Text(stringResource(R.string.todo_selected_count, selected.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedCsv = sections.filter { it.bucket != Bucket.DONE }.flatMap { s -> s.tasks.map { it.id } }.toSet().toCsv() }) {
                            Text(stringResource(R.string.todo_select_all))
                        }
                        TextButton(onClick = { exitSelect() }) { Text(stringResource(R.string.cancel)) }
                    } else {
                        Text(stringResource(R.string.tab_todo), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) queryText = ""
                        }) { Icon(Icons.Rounded.Search, stringResource(R.string.todo_search)) }
                        IconButton(onClick = { selecting = true }) { Icon(Icons.Rounded.Checklist, stringResource(R.string.todo_multi_select)) }
                        IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                    }
                }
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searching) {
                        OutlinedTextField(
                            queryText,
                            { queryText = it },
                            Modifier.fillMaxWidth().focusRequester(searchFocus),
                            placeholder = { Text(stringResource(R.string.todo_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            trailingIcon = {
                                if (queryText.isNotEmpty()) {
                                    IconButton(onClick = { queryText = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.todo_clear)) }
                                }
                            },
                        )
                    }
                    if (snap.lists.isNotEmpty()) {
                        Box {
                            Surface(onClick = { listMenu = true }, shape = CircleShape, color = MintTheme.card) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        snap.lists.firstOrNull { it.id == listFilter }?.name ?: stringResource(R.string.todo_all_lists),
                                        Modifier.padding(horizontal = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(listMenu, { listMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.todo_all_lists)) }, onClick = {
                                    listMenu = false
                                    listFilter = null
                                })
                                snap.lists.sortedBy { it.sort }.forEach { l ->
                                    DropdownMenuItem(text = { Text(l.name) }, onClick = {
                                        listMenu = false
                                        listFilter = l.id
                                    })
                                }
                            }
                        }
                    }
                }
            }
            if (!selecting && !searching && !drag.active) {
                if (nextUp != null || urgent != null) {
                    item(key = "next") { NextUpCard(nextUp, urgent, snap, now, anim()) }
                }
                if (overdueTasks.size >= 2) {
                    item(key = "overdue-card") { OverdueCard(overdueTasks.size, { tidy = true }, anim()) }
                }
            }
            if (sections.isEmpty() && !drag.active) {
                item(key = "empty") { EmptyState(stringResource(if (searching && !q.isEmpty) R.string.todo_search_empty else R.string.todo_all_empty)) }
            }
            val byBucket = sections.associateBy { it.bucket }
            Bucket.entries.forEach { b ->
                val sec = byBucket[b]
                val droppable = drag.active && b in dropBuckets
                if (sec == null && !droppable) return@forEach
                val isCollapsed = b.name in collapsed
                item(key = "h-${b.name}") {
                    SectionHeader(
                        bucket = b,
                        count = sec?.tasks?.size ?: 0,
                        collapsed = isCollapsed,
                        hot = drag.active && drag.overBucket == b,
                        onToggle = { collapsedCsv = toggleIn(collapsedCsv, b.name) },
                        onAdd =
                            if (b in dropBuckets && !selecting) {
                                {
                                    addTarget = b.name
                                    focusSignal++
                                }
                            } else {
                                null
                            },
                        modifier = anim(),
                    )
                }
                if (!isCollapsed) {
                    if (sec == null) {
                        item(key = "e-${b.name}") { DropPlaceholder(drag.overBucket == b, anim()) }
                    } else {
                        sec.tasks.take(if (b == Bucket.DONE) 100 else Int.MAX_VALUE).forEach { t -> taskRows(t, b) }
                    }
                }
            }
        }

        if (drag.active) {
            val t = drag.task
            Surface(
                Modifier
                    .offset {
                        IntOffset(
                            (drag.pointer.x - 48.dp.toPx()).roundToInt().coerceAtLeast(0),
                            (drag.pointer.y - 72.dp.toPx()).roundToInt(),
                        )
                    }.widthIn(max = 260.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 10.dp,
            ) {
                Text(
                    t?.title.orEmpty(),
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        QuickAddBar(
            snap = snap,
            now = now,
            listFilter = listFilter,
            defaultBucket = addTarget?.let { runCatching { Bucket.valueOf(it) }.getOrNull() },
            onClearBucket = { addTarget = null },
            focusSignal = focusSignal,
            hidden = selecting || drag.active,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (selecting) {
            BulkBar(
                snap = snap,
                onReschedule = { if (selected.isNotEmpty()) rescheduleFor = selected.toList() },
                onList = { id ->
                    if (selectedTasks.isNotEmpty()) {
                        actions.commit(
                            Edits.updateTasks(selectedTasks, ctx.getString(R.string.todo_bulk_desc, selectedTasks.size), System.currentTimeMillis()) { it.copy(listId = id) },
                            ctx.getString(R.string.todo_bulk_toast, selectedTasks.size),
                        )
                        exitSelect()
                    }
                },
                onPriority = { p ->
                    if (selectedTasks.isNotEmpty()) {
                        actions.commit(
                            Edits.updateTasks(selectedTasks, ctx.getString(R.string.todo_bulk_desc, selectedTasks.size), System.currentTimeMillis()) { it.copy(priority = p) },
                            ctx.getString(R.string.todo_bulk_toast, selectedTasks.size),
                        )
                        exitSelect()
                    }
                },
                onDone = {
                    val todo = selectedTasks.filter { !it.done }
                    if (todo.isNotEmpty()) {
                        val ms = System.currentTimeMillis()
                        val desc = ctx.getString(R.string.todo_bulk_done_desc, todo.size)
                        actions.commit(
                            mergeChangeSets(desc, todo.map { Edits.toggleTask(snap, it, true, ms, now, desc) }),
                            ctx.getString(R.string.todo_bulk_toast, todo.size),
                        )
                        exitSelect()
                    }
                },
                onDelete = {
                    if (selectedTasks.isNotEmpty()) {
                        val ms = System.currentTimeMillis()
                        val desc = ctx.getString(R.string.todo_bulk_delete_desc, selectedTasks.size)
                        actions.commit(
                            mergeChangeSets(desc, selectedTasks.map { Edits.delete(snap, it, desc, ms) }),
                            ctx.getString(R.string.deleted_toast),
                        )
                        exitSelect()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            )
        }
    }

    rescheduleFor?.let { ids ->
        val ts = ids.mapNotNull { taskById[it] }
        if (ts.isEmpty()) {
            rescheduleFor = null
        } else {
            RescheduleSheet(ts, snap, onDismiss = { rescheduleFor = null }, onDone = { if (selecting) exitSelect() })
        }
    }
    if (tidy) MorningSheet(overdueTasks, now, onDismiss = { tidy = false })
}
