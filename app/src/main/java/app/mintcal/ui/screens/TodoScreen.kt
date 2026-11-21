package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.todo.TodoSearch
import app.mintcal.ui.HOME
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Tab
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.MintFab
import app.mintcal.ui.components.NavClearance
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNowGated
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme

private val DROPPABLE = listOf(Bucket.TODAY, Bucket.TOMORROW, Bucket.THIS_WEEK, Bucket.LATER, Bucket.INBOX)
private val SearchBarShape = RoundedCornerShape(14.dp)
private val FilterTrayShape = RoundedCornerShape(12.dp)
private val PriorityOptions =
    listOf(
        0 to R.string.todo_priority_all,
        3 to R.string.priority_3,
        2 to R.string.priority_2,
        1 to R.string.priority_1,
    )

/**
 * 待办页：一条时间线（逾期 / 今天 / 明天 / 本周 / 之后 / 收集箱 / 已完成），分区可折叠，
 * 顶部有「下一件事」与逾期整理；右滑完成、左滑改期、长按进入多选（勾选后底部批量条改期/改清单/完成/删除）、
 * 顶栏按钮也可进入多选，搜索支持 `#清单 @标签 !高`。
 */
@Composable
fun TodoScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now = rememberNowGated()
    val today = now.toLocalDate()

    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityFilter by rememberSaveable { mutableIntStateOf(0) }
    var queryText by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    var collapsedCsv by rememberSaveable { mutableStateOf("LATER,DONE") }
    var expandedCsv by rememberSaveable { mutableStateOf("") }
    var addTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var focusSignal by rememberSaveable { mutableIntStateOf(0) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var rescheduleFor by remember { mutableStateOf<List<String>?>(null) }
    var tidy by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var bulkBarHeight by remember { mutableIntStateOf(0) }
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 底部留白：多选时给批量条让位；否则给底部添加入口让位
    val bottomSpace =
        with(density) {
            (if (selecting) bulkBarHeight else 0).toDp()
        }.coerceAtLeast(if (selecting) 96.dp else 160.dp + navBottomPadding)
    val listContentPadding = remember(bottomSpace) { PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomSpace) }

    // 行级索引：progress/due/清单/课程/子任务一次算好，整页行共享（原来每行各自全量扫描）
    val todoIndex = remember(snap, now) { todoIndexOf(snap, now) }
    val q = remember(queryText) { TodoSearch.parse(queryText) }
    val filtered =
        remember(todoIndex, listFilter, priorityFilter, q) {
            val top = todoIndex.topTasks
            if (listFilter == null && priorityFilter == 0 && q.isEmpty) {
                top
            } else {
                top.filter { t ->
                    (listFilter == null || t.listId == listFilter) &&
                        (priorityFilter == 0 || t.priority == priorityFilter) &&
                        TodoSearch.matches(q, t, todoIndex.subtasksByParent[t.id].orEmpty(), todoIndex.listById[t.listId]?.name)
                }
            }
        }
    val sections =
        remember(snap, filtered, now, todoIndex) {
            TaskLogic.timeline(filtered, now) {
                if (it.id in todoIndex.due) todoIndex.due[it.id] else TaskLogic.effectiveDue(it, snap.currentSemester, snap.courses, snap.exceptions, now)
            }
        }
    val needTaskLookup = selecting || rescheduleFor != null
    val taskById =
        remember(snap, needTaskLookup) {
            if (needTaskLookup) snap.tasks.associateBy { it.id } else emptyMap()
        }
    val overdueTasks =
        remember(sections) {
            sections
                .firstOrNull()
                ?.takeIf { it.bucket == Bucket.OVERDUE }
                ?.tasks
                .orEmpty()
        }
    val dropBuckets =
        remember(today) {
            DROPPABLE.filter { TaskLogic.dropDate(it, today) != TaskLogic.NO_DROP }.toSet()
        }

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

    val currentSnap by rememberUpdatedState(snap)
    val currentNow by rememberUpdatedState(now)
    val onToggleTask: (Task, Boolean) -> Unit =
        remember(actions) {
            { t, d ->
                toggle(actions, currentSnap, t, d, currentNow, ctx.getString(R.string.done_toast, t.title))
            }
        }
    val currentSelecting by rememberUpdatedState(selecting)
    val currentSelectedCsv by rememberUpdatedState(selectedCsv)
    val onTaskClick: (Task) -> Unit =
        remember(nav) {
            { t ->
                if (currentSelecting) {
                    if (!t.done) selectedCsv = toggleIn(currentSelectedCsv, t.id)
                } else {
                    nav.openTask(t.id)
                }
            }
        }
    val currentSelected by rememberUpdatedState(selected)
    val onTaskLongClick: (Task) -> Unit =
        remember {
            { t ->
                if (!t.done) {
                    selecting = true
                    if (t.id !in currentSelected) selectedCsv = (currentSelected + t.id).toCsv()
                }
            }
        }
    val onTaskSwipeLeft: (Task) -> Unit =
        remember {
            { t -> rescheduleFor = listOf(t.id) }
        }
    val currentExpandedCsv by rememberUpdatedState(expandedCsv)
    val onToggleExpand: (String) -> Unit =
        remember {
            { id -> expandedCsv = toggleIn(currentExpandedCsv, id) }
        }
    val currentCollapsedCsv by rememberUpdatedState(collapsedCsv)
    val onToggleSection: (Bucket) -> Unit =
        remember {
            { b -> collapsedCsv = toggleIn(currentCollapsedCsv, b.name) }
        }
    val onAddSection: (Bucket) -> Unit =
        remember {
            { b ->
                addTarget = b.name
                quickAddOpen = true
                focusSignal++
            }
        }
    val sectionToggleCallbacks =
        remember {
            Bucket.entries.associateWith { b -> { onToggleSection(b) } }
        }
    val sectionAddCallbacks =
        remember {
            Bucket.entries.associateWith { b -> { onAddSection(b) } }
        }
    val overdueTidyAction: @Composable () -> Unit =
        remember {
            {
                // 动作跟着分区语义色走，别在红色分区里冒一个蓝色按钮
                TextButton(
                    onClick = { tidy = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintTheme.colors.removed),
                ) { Text(stringResource(R.string.todo_tidy)) }
            }
        }
    val currentSections by rememberUpdatedState(sections)
    val onSelectAll: () -> Unit =
        remember {
            {
                selectedCsv =
                    currentSections
                        .filter { it.bucket != Bucket.DONE }
                        .flatMap { s -> s.tasks.map { it.id } }
                        .toSet()
                        .toCsv()
            }
        }
    val onExitSelect: () -> Unit = remember { { exitSelect() } }
    val currentSearching by rememberUpdatedState(searching)
    val onToggleSearch: () -> Unit =
        remember(focusManager) {
            {
                val next = !currentSearching
                searching = next
                if (!next) {
                    queryText = ""
                    focusManager.clearFocus()
                }
            }
        }
    val onEnterSelect: () -> Unit = remember { { selecting = true } }
    val onQueryChange: (String) -> Unit = remember { { queryText = it } }
    val onClearQuery: () -> Unit = remember { { queryText = "" } }
    val onClearFilters: () -> Unit =
        remember(focusManager) {
            {
                listFilter = null
                priorityFilter = 0
                queryText = ""
                searching = false
                listMenu = false
                priorityMenu = false
                focusManager.clearFocus()
            }
        }
    val onSearchSubmit: () -> Unit = remember(focusManager) { { focusManager.clearFocus() } }
    val currentListMenu by rememberUpdatedState(listMenu)
    val onToggleListMenu: () -> Unit =
        remember {
            {
                val next = !currentListMenu
                listMenu = next
                if (next) priorityMenu = false
            }
        }
    val currentPriorityMenu by rememberUpdatedState(priorityMenu)
    val onTogglePriorityMenu: () -> Unit =
        remember {
            {
                val next = !currentPriorityMenu
                priorityMenu = next
                if (next) listMenu = false
            }
        }
    val onPickListFilter: (String?) -> Unit =
        remember {
            { id ->
                listFilter = id
                listMenu = false
            }
        }
    val onPickPriorityFilter: (Int) -> Unit =
        remember {
            { v ->
                priorityFilter = v
                priorityMenu = false
            }
        }
    val onOpenQuickAddDefault: () -> Unit =
        remember {
            {
                addTarget = null
                quickAddOpen = true
            }
        }
    val onDismissQuickAdd: () -> Unit = remember { { quickAddOpen = false } }
    val defaultAddBucket =
        remember(addTarget) {
            addTarget?.let { runCatching { Bucket.valueOf(it) }.getOrNull() }
        }
    val selectedListName =
        remember(todoIndex.listById, listFilter) {
            listFilter?.let { todoIndex.listById[it]?.name }
        }

    val quickAddVisible = quickAddOpen && !selecting && !searching
    val backStack by nav.controller.currentBackStackEntryAsState()
    BackHandler(enabled = nav.tab == Tab.TODO && backStack?.destination?.route == HOME && !quickAddVisible && (quickAddOpen || selecting || searching || listMenu || priorityMenu)) {
        when {
            quickAddOpen -> {
                quickAddOpen = false
            }

            listMenu -> {
                listMenu = false
            }

            priorityMenu -> {
                priorityMenu = false
            }

            selecting -> {
                exitSelect()
            }

            else -> {
                searching = false
                queryText = ""
                focusManager.clearFocus()
            }
        }
    }

    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    var pendingTidyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(nav.openTodoTidy, nav.tab) {
        if (nav.openTodoTidy && nav.tab == Tab.TODO) {
            nav.openTodoTidy = false
            exitSelect()
            searching = false
            queryText = ""
            listFilter = null
            priorityFilter = 0
            listMenu = false
            priorityMenu = false
            quickAddOpen = false
            focusManager.clearFocus()
            collapsedCsv = (csvToSet(collapsedCsv) - Bucket.OVERDUE.name).toCsv()
            pendingTidyOpen = true
        }
    }
    LaunchedEffect(pendingTidyOpen, listFilter, priorityFilter, queryText, searching, selecting, overdueTasks) {
        if (pendingTidyOpen && listFilter == null && priorityFilter == 0 && queryText.isEmpty() && !searching && !selecting) {
            pendingTidyOpen = false
            if (overdueTasks.isNotEmpty()) {
                tidy = true
            }
        }
    }

    val selectedTasks = if (selecting) selected.mapNotNull { taskById[it] } else emptyList()

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            state = listState,
            // 列表铺满整屏：底部留白放进行内边距，行可以滚到岛下面，滚到底再让出底栏和批量条
            modifier = Modifier.fillMaxSize(),
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "top", contentType = "header_top") {
                TodoTopBar(
                    selecting = selecting,
                    selectedCount = if (selecting) selected.size else 0,
                    searching = searching,
                    onSelectAll = onSelectAll,
                    onExitSelect = onExitSelect,
                    onToggleSearch = onToggleSearch,
                    onEnterSelect = onEnterSelect,
                )
            }
            item(key = "filters", contentType = "filters") {
                TodoFilterBar(
                    searching = searching,
                    queryText = queryText,
                    hasLists = snap.lists.isNotEmpty(),
                    selectedListName = selectedListName,
                    listFilter = listFilter,
                    priorityFilter = priorityFilter,
                    listMenu = listMenu,
                    priorityMenu = priorityMenu,
                    lists = if (listMenu) snap.lists else emptyList(),
                    searchFocus = searchFocus,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onSearchSubmit = onSearchSubmit,
                    onToggleListMenu = onToggleListMenu,
                    onTogglePriorityMenu = onTogglePriorityMenu,
                    onPickListFilter = onPickListFilter,
                    onPickPriorityFilter = onPickPriorityFilter,
                )
            }
            if (sections.isEmpty()) {
                // 有搜索词或清单筛选时是「没匹配到」，否则是真空白；有筛选时优先给「清除筛选」与新建两条出路
                val hasActiveFilter = !q.isEmpty || listFilter != null || priorityFilter != 0
                item(key = "empty", contentType = "empty") {
                    EmptyState(
                        stringResource(if (hasActiveFilter) R.string.todo_search_empty else R.string.todo_all_empty),
                        hint = null,
                        actionLabel = stringResource(if (hasActiveFilter) R.string.todo_clear_filter else R.string.shortcut_add_task),
                        onAction = if (hasActiveFilter) onClearFilters else onOpenQuickAddDefault,
                        secondaryLabel = if (hasActiveFilter) stringResource(R.string.shortcut_add_task) else null,
                        onSecondary = if (hasActiveFilter) onOpenQuickAddDefault else null,
                    )
                }
            }
            sections.forEach { sec ->
                val b = sec.bucket
                val isCollapsed = b.name in collapsed
                item(key = "h-${b.name}", contentType = "section_header") {
                    SectionHeader(
                        bucket = b,
                        count = sec.tasks.size,
                        collapsed = isCollapsed,
                        onToggle = sectionToggleCallbacks.getValue(b),
                        onAdd =
                            if (b in dropBuckets && !selecting) {
                                sectionAddCallbacks[b]
                            } else {
                                null
                            },
                        // 逾期的批量整理挂在分区标题上，不再单独占一张红色提醒卡
                        action =
                            if (b == Bucket.OVERDUE && sec.tasks.size >= 2 && !selecting) {
                                overdueTidyAction
                            } else {
                                null
                            },
                    )
                }
                if (!isCollapsed) {
                    val isDoneTruncated = b == Bucket.DONE && sec.tasks.size > 100
                    val tasksToShow = if (isDoneTruncated) sec.tasks.take(100) else sec.tasks
                    tasksToShow.forEach { t ->
                        val subs = todoIndex.subtasksByParent[t.id].orEmpty()
                        val open = t.id in expanded && subs.isNotEmpty()
                        item(key = t.id, contentType = "todo_row") {
                            val isSelected = t.id in selected
                            val isExpanded = open
                            val model =
                                remember(t, todoIndex.due[t.id], todoIndex.progress[t.id], todoIndex.listById[t.listId]) {
                                    buildTodoRowModel(
                                        task = t,
                                        snap = snap,
                                        now = now,
                                        index = todoIndex,
                                        expanded = false,
                                        selected = false,
                                        context = ctx,
                                    )
                                }
                            TodoRow(
                                model = model,
                                expanded = isExpanded,
                                selected = isSelected,
                                swipeEnabled = !selecting,
                                selectMode = selecting,
                                swapSwipe = settings.swapSwipe,
                                onToggle = onToggleTask,
                                onClick = onTaskClick,
                                onLongClick = onTaskLongClick,
                                onSwipeLeft = onTaskSwipeLeft,
                                onToggleExpand = onToggleExpand,
                            )
                        }
                        if (open) {
                            subs.forEach { s ->
                                item(key = "s-${s.id}", contentType = "sub_row") {
                                    SubRow(
                                        sub = s,
                                        snap = snap,
                                        now = now,
                                    )
                                }
                            }
                            item(key = "a-${t.id}", contentType = "sub_add") {
                                InlineSubAdd(
                                    parent = t,
                                    snap = snap,
                                )
                            }
                        }
                    }
                    if (isDoneTruncated) {
                        item(key = "done-truncated", contentType = "done_truncated") {
                            Text(
                                text = stringResource(R.string.todo_done_truncated, 100, sec.tasks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        val frozenSnap = remember { snap }
        QuickAddSheet(
            snap = if (quickAddVisible || nav.pendingAddText != null) snap else frozenSnap,
            now = now,
            listFilter = listFilter,
            defaultBucket = defaultAddBucket,
            focusSignal = focusSignal,
            visible = quickAddVisible,
            onDismiss = onDismissQuickAdd,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(),
        )
        MintFab(
            visible = !selecting && !searching && !quickAddOpen,
            onClick = onOpenQuickAddDefault,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = NavClearance + navBottomPadding),
            description = stringResource(R.string.add),
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
                            desc,
                        )
                        exitSelect()
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bulkBarHeight = it.height }
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
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

@Composable
private fun TodoTopBar(
    selecting: Boolean,
    selectedCount: Int,
    searching: Boolean,
    onSelectAll: () -> Unit,
    onExitSelect: () -> Unit,
    onToggleSearch: () -> Unit,
    onEnterSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Text(stringResource(R.string.todo_selected_count, selectedCount), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.todo_select_all))
            }
            TextButton(onClick = onExitSelect) { Text(stringResource(R.string.cancel)) }
        } else {
            Text(stringResource(R.string.tab_todo), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleSearch) {
                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(if (searching) R.string.cancel else R.string.todo_search))
            }
            IconButton(onClick = onEnterSelect) { Icon(Icons.Rounded.Checklist, stringResource(R.string.todo_multi_select)) }
        }
    }
}

@Composable
private fun TodoFilterBar(
    searching: Boolean,
    queryText: String,
    hasLists: Boolean,
    selectedListName: String?,
    listFilter: String?,
    priorityFilter: Int,
    listMenu: Boolean,
    priorityMenu: Boolean,
    lists: List<TaskList>,
    searchFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchSubmit: () -> Unit,
    onToggleListMenu: () -> Unit,
    onTogglePriorityMenu: () -> Unit,
    onPickListFilter: (String?) -> Unit,
    onPickPriorityFilter: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val onSurfaceVariant = scheme.onSurfaceVariant
    val cardColor = MintTheme.card
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (searching) {
            // 无框搜索：柔色胶囊 + 搜索图标，唯一的描边输入框已经拆掉
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SearchBarShape)
                    .background(scheme.surfaceContainerLow)
                    .heightIn(min = 48.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = onSurfaceVariant)
                BasicTextField(
                    value = queryText,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(searchFocus),
                    textStyle = typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    decorationBox = { inner ->
                        Box {
                            if (queryText.isEmpty()) {
                                Text(
                                    stringResource(R.string.todo_search_hint),
                                    style = typography.bodyLarge,
                                    color = onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) { Icon(Icons.Rounded.Close, stringResource(R.string.todo_clear)) }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasLists) {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(cardColor)
                        .mintClickable(pressedScale = 0.96f, onClick = onToggleListMenu)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp), tint = onSurfaceVariant)
                    Text(
                        selectedListName ?: stringResource(R.string.todo_all_lists),
                        Modifier.padding(horizontal = 6.dp),
                        style = typography.labelMedium,
                    )
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = onSurfaceVariant)
                }
            }
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .mintClickable(pressedScale = 0.96f, onClick = onTogglePriorityMenu)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp), tint = onSurfaceVariant)
                Text(
                    when (priorityFilter) {
                        3 -> stringResource(R.string.priority_3)
                        2 -> stringResource(R.string.priority_2)
                        1 -> stringResource(R.string.priority_1)
                        else -> stringResource(R.string.label_priority)
                    },
                    Modifier.padding(horizontal = 6.dp),
                    style = typography.labelMedium,
                )
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = onSurfaceVariant)
            }
        }
        // 同窗口内轻量展开筛选条，彻底消除弹窗 Window 的 Binder 阻塞
        if (listMenu) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(FilterTrayShape)
                    .background(cardColor)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipPill(
                    label = stringResource(R.string.todo_all_lists),
                    on = listFilter == null,
                ) {
                    onPickListFilter(null)
                }
                lists.sortedBy { it.sort }.forEach { l ->
                    ChipPill(
                        label = l.name,
                        on = listFilter == l.id,
                    ) {
                        onPickListFilter(l.id)
                    }
                }
            }
        }
        if (priorityMenu) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(FilterTrayShape)
                    .background(cardColor)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityOptions.forEach { (v, labelRes) ->
                    ChipPill(
                        label = stringResource(labelRes),
                        on = priorityFilter == v,
                    ) {
                        onPickPriorityFilter(v)
                    }
                }
            }
        }
    }
}
