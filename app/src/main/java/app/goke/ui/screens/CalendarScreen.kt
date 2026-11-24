package app.goke.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.AgendaItem
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.TaskItem
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.ui.Actions
import app.goke.ui.LocalActions
import app.goke.ui.LocalNav
import app.goke.ui.Nav
import app.goke.ui.Route
import app.goke.ui.Tab
import app.goke.ui.components.AgendaRow
import app.goke.ui.components.DatePickerDlg
import app.goke.ui.components.EmptyState
import app.goke.ui.components.EventDetailSheet
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintFab
import app.goke.ui.components.NavClearance
import app.goke.ui.components.NavListInset
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.mintClickable
import app.goke.ui.components.mintPressScale
import app.goke.ui.icons.ExpandMore
import app.goke.ui.liveSnapshot
import app.goke.ui.monthDayWeekday
import app.goke.ui.reduceMotion
import app.goke.ui.rememberNowGated
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import app.goke.ui.weekdayShort
import app.goke.ui.yearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private enum class CalMode { MONTH, WEEK }

/** 翻页中心页：向前后各留出 3000 个月/周，滑不到头。 */
private const val PAGE_CENTER = 3000

private val DayNumberBoxWithMarksModifier = Modifier.padding(bottom = 2.dp).size(28.dp)
private val DayNumberBoxNoMarksModifier = Modifier.padding(bottom = 11.dp).size(28.dp)
private val IndicatorsCanvasModifier = Modifier.size(width = 36.dp, height = 9.dp)
private val PrefetchOffsets = intArrayOf(-1, 1, -2, 2, -3, 3)

private val selectedDateSaver =
    Saver<MutableState<LocalDate>, String>(
        save = { it.value.toString() },
        restore = { mutableStateOf(LocalDate.parse(it)) },
    )

private val localDateSaver =
    Saver<LocalDate, String>(
        save = { it.toString() },
        restore = { LocalDate.parse(it) },
    )

/** 月份标题打开日期选择，左右滑动翻页，选中日期旁提供回今天入口。 */
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now = rememberNowGated()
    val today = now.toLocalDate()
    var mode by rememberSaveable { mutableStateOf(CalMode.MONTH) }
    val selectedDateState = rememberSaveable(saver = selectedDateSaver) { mutableStateOf(today) }
    val first = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val reduce = reduceMotion()
    val scope = rememberCoroutineScope()

    // 翻页锚点：进入某个模式或切换周一起始时，以当时选中日期所在月/周为准
    val anchor =
        rememberSaveable(mode, first, saver = localDateSaver) {
            val init = selectedDateState.value
            if (mode == CalMode.MONTH) init.withDayOfMonth(1) else init.with(TemporalAdjusters.previousOrSame(first))
        }
    val pager = key(mode, first) { rememberPagerState(initialPage = PAGE_CENTER) { PAGE_CENTER * 2 } }
    var lastPage by remember(pager) { mutableIntStateOf(pager.currentPage) }

    fun periodStart(page: Int): LocalDate =
        when (mode) {
            CalMode.MONTH -> anchor.plusMonths((page - PAGE_CENTER).toLong()).with(TemporalAdjusters.previousOrSame(first))
            CalMode.WEEK -> anchor.plusWeeks((page - PAGE_CENTER).toLong())
        }

    fun pageOf(d: LocalDate): Int {
        val n =
            when (mode) {
                CalMode.MONTH -> ChronoUnit.MONTHS.between(anchor, d.withDayOfMonth(1))
                CalMode.WEEK -> ChronoUnit.WEEKS.between(anchor, d.with(TemporalAdjusters.previousOrSame(first)))
            }
        return (PAGE_CENTER + n).toInt().coerceIn(0, PAGE_CENTER * 2)
    }

    fun goto(d: LocalDate) {
        selectedDateState.value = d
        val p = pageOf(d)
        if (p != pager.currentPage) {
            lastPage = p
            scope.launch {
                // 直接滑动始终可用；程序化跳转才尊重「减少动效」
                if (reduce) pager.scrollToPage(p) else pager.animateScrollToPage(p)
            }
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var previewDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }

    // 翻到哪一页，选中日期就跟到那个月/周的同名日子（不打断下面列表的语义）
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            val delta = p - lastPage
            lastPage = p
            if (delta != 0) {
                val cur = selectedDateState.value
                selectedDateState.value =
                    when (mode) {
                        CalMode.MONTH -> cur.plusMonths(delta.toLong())
                        CalMode.WEEK -> cur.plusWeeks(delta.toLong())
                    }
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (nav.reselect > 0 && nav.tab == Tab.CALENDAR) {
            if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
            goto(today)
        }
    }

    // 按 Snapshot 与设置缓存月份网格模型及每日 Agenda 列表，点选日期时零重新计算
    val gridCache = remember(snap, settings.defaultBlockMinutes, first, today) { SimpleGridCache(maxSize = 24) }

    val onDateClick = remember<(LocalDate) -> Unit>(pager, reduce, anchor, mode, first) { { d -> goto(d) } }
    val onDatePreview = remember<(LocalDate) -> Unit> { { d -> previewDate = d } }
    val onGotoToday = remember(today) { { goto(today) } }
    val onOpenEvent = remember { { id: String -> selectedEventId = id } }
    val periodStartFn = remember<(Int) -> LocalDate>(mode, anchor, first) { { page -> periodStart(page) } }
    val previewLabel = stringResource(R.string.calendar_preview_day)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = NavListInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .mintClickable(role = Role.Button, pressedScale = 0.98f) { showPicker = true }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalendarTitle(
                            mode = mode,
                            anchor = anchor,
                            first = first,
                            pager = pager,
                            periodStart = periodStartFn,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SegmentedPill(
                        options =
                            listOf(
                                CalMode.MONTH to stringResource(R.string.mode_month),
                                CalMode.WEEK to stringResource(R.string.mode_week),
                            ),
                        selected = mode,
                        onSelect = { mode = it },
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
            item(key = "grid") {
                CalendarGrid(
                    pager = pager,
                    mode = mode,
                    anchor = anchor,
                    first = first,
                    today = today,
                    snap = snap,
                    defaultBlockMinutes = settings.defaultBlockMinutes,
                    gridCache = gridCache,
                    selectedDateState = selectedDateState,
                    onDateClick = onDateClick,
                    onDatePreview = onDatePreview,
                    previewLabel = previewLabel,
                    reduce = reduce,
                )
            }
            item(key = "agenda") {
                DayAgendaSection(
                    selectedDateState = selectedDateState,
                    today = today,
                    gridCache = gridCache,
                    snap = snap,
                    defaultBlockMinutes = settings.defaultBlockMinutes,
                    showTeacher = settings.showTeacher,
                    now = now,
                    actions = actions,
                    nav = nav,
                    onGotoToday = onGotoToday,
                    onOpenEvent = onOpenEvent,
                )
            }
        }
        MintFab(
            visible = !quickAddOpen && previewDate == null,
            onClick = { quickAddOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = NavClearance),
        )
        QuickAddSheet(
            snap = snap,
            now = now,
            listFilter = null,
            defaultBucket = null,
            focusSignal = 0,
            visible = quickAddOpen,
            onDismiss = { quickAddOpen = false },
            anchorDate = selectedDateState.value,
            acceptShare = false,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(),
        )
        if (showPicker) {
            DatePickerDlg(selectedDateState.value, { showPicker = false }) {
                showPicker = false
                goto(it)
            }
        }
        previewDate?.let { date ->
            val entries =
                remember(snap, date, settings.defaultBlockMinutes) {
                    gridCache.getDayItems(date) ?: Agenda.forDay(snap, date, settings.defaultBlockMinutes)
                }
            val marks = remember(entries) { CalendarMarks.from(entries) }
            val sheetListColors = remember(snap) { snap.lists.associate { it.id to it.colorIndex } }

            MintBottomSheet(onDismissRequest = { previewDate = null }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Text(ctx.monthDayWeekday(date), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.calendar_day_detail, marks.scheduled, marks.pending, marks.done),
                        Modifier.padding(top = 4.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entries.isEmpty()) item { EmptyState(stringResource(R.string.day_empty)) }
                        items(entries.size, key = { agendaKey(entries[it]) }, contentType = { agendaType(entries[it]) }) { index ->
                            val entry = entries[index]
                            AgendaRow(
                                item = entry,
                                now = now,
                                showTeacher = settings.showTeacher,
                                onOpenCourse = {
                                    previewDate = null
                                    nav.push(Route.CourseEdit(it))
                                },
                                onOpenEvent = {
                                    previewDate = null
                                    selectedEventId = it
                                },
                                onOpenTask = {
                                    previewDate = null
                                    nav.openTask(it)
                                },
                                onToggleTask = { task, done -> toggle(actions, snap, task, done, now, resources.getString(R.string.done_toast, task.title)) },
                                listColors = sheetListColors,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            previewDate = null
                            nav.push(Route.TodoEdit(null, date.toString()))
                        }) {
                            Text(stringResource(R.string.new_entry))
                        }
                        TextButton(onClick = {
                            previewDate = null
                            goto(date)
                        }) {
                            Text(stringResource(R.string.calendar_view_day))
                        }
                    }
                }
            }
        }
        EventDetailSheet(
            event = selectedEventId?.let { id -> snap.events.firstOrNull { it.id == id } },
            snap = snap,
            onDismiss = { selectedEventId = null },
        )
    }
}

/**
 * 选中日的日程列表区域。
 * 将选中日状态读取与 Agenda.forDay 真正隔离下沉到此局部作用域，避免点选日期引起外层 CalendarScreen / LazyColumn 根重组；
 * 优先复用月份网格 range 计算并缓存好的每日条目映射，消除反复执行全学期排课/周期性展开计算。
 */
@Composable
private fun DayAgendaSection(
    selectedDateState: State<LocalDate>,
    today: LocalDate,
    gridCache: SimpleGridCache,
    snap: Snapshot,
    defaultBlockMinutes: Int,
    showTeacher: Boolean,
    now: LocalDateTime,
    actions: Actions,
    nav: Nav,
    onGotoToday: () -> Unit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val selected = selectedDateState.value
    val dayItems =
        remember(selected, snap, defaultBlockMinutes) {
            gridCache.getDayItems(selected) ?: Agenda.forDay(snap, selected, defaultBlockMinutes)
        }
    val marks = remember(dayItems) { CalendarMarks.from(dayItems) }
    val listColors = remember(snap) { snap.lists.associate { it.id to it.colorIndex } }
    val dateTitle = remember(selected) { ctx.monthDayWeekday(selected) }
    val onOpenCourse = remember(nav) { { id: String -> nav.push(Route.CourseEdit(id)) } }
    val onOpenTask = remember(nav) { { id: String -> nav.openTask(id) } }
    val onToggleTask =
        remember(actions, snap, now, ctx) {
            { task: Task, done: Boolean ->
                toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title))
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    dateTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detailParts =
                    buildList {
                        if (marks.scheduled > 0) add(stringResource(R.string.cal_day_events_count, marks.scheduled))
                        if (marks.pending > 0) add(stringResource(R.string.cal_day_todo_count, marks.pending))
                        if (marks.done > 0) add(stringResource(R.string.cal_day_done_count, marks.done))
                    }
                if (detailParts.isNotEmpty()) {
                    Text(
                        detailParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(
                visible = selected != today,
                enter = fadeIn(MintTheme.spec()),
                exit = fadeOut(MintTheme.spec()),
            ) {
                Text(
                    stringResource(R.string.back_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .mintClickable(role = Role.Button, pressedScale = 0.96f) {
                                onGotoToday()
                            }.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
        if (dayItems.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.day_empty),
            )
        } else {
            for (i in dayItems.indices) {
                val item = dayItems[i]
                val rowKey = if (item is CourseItem) "c-$i" else agendaKey(item)
                key(rowKey) {
                    AgendaRow(
                        item = item,
                        now = now,
                        showTeacher = showTeacher,
                        onOpenCourse = onOpenCourse,
                        onOpenEvent = onOpenEvent,
                        onOpenTask = onOpenTask,
                        onToggleTask = onToggleTask,
                        listColors = listColors,
                    )
                }
            }
        }
    }
}

/**
 * 月历网格容器。
 * 将高频点选时的 selectedDate 仅以稳定 State 引用透传，使整个网格卡片、星期标题与 MonthPage 均能跳过重组；
 * 同时将高度动画收拢在容器级，杜绝每页独立 animateDpAsState 引发的多轮嵌套测量抖动。
 */
@Composable
private fun CalendarGrid(
    pager: PagerState,
    mode: CalMode,
    anchor: LocalDate,
    first: DayOfWeek,
    today: LocalDate,
    snap: Snapshot,
    defaultBlockMinutes: Int,
    gridCache: SimpleGridCache,
    selectedDateState: State<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
    onDatePreview: (LocalDate) -> Unit,
    previewLabel: String,
    reduce: Boolean,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var beyondViewport by remember(mode) { mutableIntStateOf(0) }
    LaunchedEffect(mode) {
        delay(350L)
        beyondViewport = 1
    }
    LaunchedEffect(pager.settledPage, mode, gridCache) {
        val settled = pager.settledPage
        withContext(Dispatchers.Default) {
            for (offset in PrefetchOffsets) {
                val p = (settled + offset).coerceIn(0, PAGE_CENTER * 2 - 1)
                if (p == settled) continue
                val start =
                    if (mode == CalMode.MONTH) {
                        anchor.plusMonths((p - PAGE_CENTER).toLong()).with(TemporalAdjusters.previousOrSame(first))
                    } else {
                        anchor.plusWeeks((p - PAGE_CENTER).toLong())
                    }
                val monthOf = anchor.plusMonths((p - PAGE_CENTER).toLong())
                val cacheKey =
                    if (mode == CalMode.MONTH) {
                        GridCacheKey.Month(YearMonth.from(monthOf))
                    } else {
                        GridCacheKey.Week(start)
                    }
                getOrComputeGridData(
                    cache = gridCache,
                    key = cacheKey,
                    mode = mode,
                    start = start,
                    monthOf = monthOf,
                    first = first,
                    today = today,
                    snap = snap,
                    defaultBlockMinutes = defaultBlockMinutes,
                    ctx = ctx,
                )
            }
            if (mode == CalMode.MONTH) {
                val weekStart = today.with(TemporalAdjusters.previousOrSame(first))
                getOrComputeGridData(
                    cache = gridCache,
                    key = GridCacheKey.Week(weekStart),
                    mode = CalMode.WEEK,
                    start = weekStart,
                    monthOf = weekStart,
                    first = first,
                    today = today,
                    snap = snap,
                    defaultBlockMinutes = defaultBlockMinutes,
                    ctx = ctx,
                )
            }
        }
    }

    val currentMonthOf =
        remember(pager.currentPage, anchor, mode) {
            if (mode == CalMode.MONTH) {
                anchor.plusMonths((pager.currentPage - PAGE_CENTER).toLong())
            } else {
                anchor
            }
        }
    val currentRows =
        remember(currentMonthOf, mode, first) {
            if (mode == CalMode.MONTH) {
                val start = currentMonthOf.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(first))
                val end = currentMonthOf.with(TemporalAdjusters.lastDayOfMonth()).with(TemporalAdjusters.nextOrSame(first.plus(6)))
                ((ChronoUnit.DAYS.between(start, end) + 1) / 7).toInt()
            } else {
                1
            }
        }
    val targetHeight = (48 * currentRows).dp
    val animatedHeightState =
        animateDpAsState(
            targetValue = targetHeight,
            animationSpec = MintTheme.press<Dp>(),
            label = "grid-height",
        )
    val weekdayLabels =
        remember(first) {
            (0..6).map { i -> ctx.weekdayShort(first.plus(i.toLong()).value) }
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MintTheme.card)
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (label in weekdayLabels) {
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val hDp = if (reduce) targetHeight else animatedHeightState.value
                        val hPx = hDp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
                        val placeable = measurable.measure(constraints.copy(minHeight = hPx, maxHeight = hPx))
                        layout(placeable.width, hPx) {
                            placeable.placeRelative(0, 0)
                        }
                    }.clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            HorizontalPager(
                state = pager,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .requiredHeight(targetHeight),
                beyondViewportPageCount = beyondViewport,
                key = { it },
            ) { page ->
                val gridData =
                    remember(page, mode, anchor, first, today, snap, defaultBlockMinutes, gridCache) {
                        val start =
                            if (mode == CalMode.MONTH) {
                                anchor.plusMonths((page - PAGE_CENTER).toLong()).with(TemporalAdjusters.previousOrSame(first))
                            } else {
                                anchor.plusWeeks((page - PAGE_CENTER).toLong())
                            }
                        val monthOf = anchor.plusMonths((page - PAGE_CENTER).toLong())
                        val cacheKey =
                            if (mode == CalMode.MONTH) {
                                GridCacheKey.Month(YearMonth.from(monthOf))
                            } else {
                                GridCacheKey.Week(start)
                            }
                        getOrComputeGridData(
                            cache = gridCache,
                            key = cacheKey,
                            mode = mode,
                            start = start,
                            monthOf = monthOf,
                            first = first,
                            today = today,
                            snap = snap,
                            defaultBlockMinutes = defaultBlockMinutes,
                            ctx = ctx,
                        )
                    }

                MonthPage(
                    gridData = gridData,
                    selectedDateState = selectedDateState,
                    onDateClick = onDateClick,
                    onDatePreview = onDatePreview,
                    previewLabel = previewLabel,
                )
            }
        }
    }
}

/** 单月/单周网格页面：行与单元格结构完全静态，点选日期不引起本函数重组。 */
@Composable
private fun MonthPage(
    gridData: MonthGridData,
    selectedDateState: State<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
    onDatePreview: (LocalDate) -> Unit,
    previewLabel: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val onPrimaryColor = colorScheme.onPrimary
    val onSurfaceColor = colorScheme.onSurface
    val onSurfaceVariantColor = colorScheme.onSurfaceVariant
    val outOfPeriodTextColor = onSurfaceVariantColor.copy(alpha = 0.45f)
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val scheduleColor = primaryColor
    val scheduleOutColor = primaryColor.copy(alpha = 0.45f)
    val pendingColor = primaryColor
    val pendingOutColor = primaryColor.copy(alpha = 0.45f)
    val doneColor = onSurfaceVariantColor.copy(alpha = 0.85f)
    val doneOutColor = onSurfaceVariantColor.copy(alpha = 0.4f)

    Column(modifier.fillMaxWidth()) {
        val rows = gridData.rows
        val cells = gridData.cells
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                val cellModifier = Modifier.weight(1f)
                for (c in 0..6) {
                    val index = r * 7 + c
                    if (index < cells.size) {
                        val cell = cells[index]
                        val inPeriod = cell.inPeriod
                        DayCell(
                            cell = cell,
                            selectedDateState = selectedDateState,
                            primaryColor = primaryColor,
                            onPrimaryColor = onPrimaryColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = outOfPeriodTextColor,
                            bodyMedium = bodyMedium,
                            scheduleColor = if (inPeriod) scheduleColor else scheduleOutColor,
                            pendingColor = if (inPeriod) pendingColor else pendingOutColor,
                            doneColor = if (inPeriod) doneColor else doneOutColor,
                            onClick = onDateClick,
                            onPreview = onDatePreview,
                            previewLabel = previewLabel,
                            modifier = cellModifier,
                        )
                    }
                }
            }
        }
    }
}

private fun agendaKey(i: AgendaItem): String =
    when (i) {
        is CourseItem -> "c-${i.occ.course.id}-${i.occ.date}-${i.occ.startPeriod}"
        is EventItem -> "e-${i.event.id}-${i.start}"
        is TaskItem -> "t-${i.task.id}"
        is BlockItem -> "b-${i.task.id}"
    }

/** 异构行的复用类型：滚动时同类型行直接复用组合，不跨类型重建。 */
private fun agendaType(i: AgendaItem): String =
    when (i) {
        is CourseItem -> "course"
        is EventItem -> "event"
        is TaskItem -> "task"
        is BlockItem -> "block"
    }

/**
 * 单元格：彻底消除 animateColorAsState，改为无状态颜色计算；
 * 选中态通过 derivedStateOf 仅在自身状态翻转时重组，未选中的 40 格完全跳过；
 * 选中底圈与今天边框通过 drawBehind 绘制，移除 clip/background/border 节点分配。
 */
@Composable
private fun DayCell(
    cell: MonthGridCell,
    selectedDateState: State<LocalDate>,
    primaryColor: Color,
    onPrimaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    bodyMedium: TextStyle,
    scheduleColor: Color,
    pendingColor: Color,
    doneColor: Color,
    onClick: (LocalDate) -> Unit,
    onPreview: (LocalDate) -> Unit,
    previewLabel: String,
    modifier: Modifier = Modifier,
) {
    val isSelected by remember(cell.date) {
        derivedStateOf { selectedDateState.value == cell.date }
    }

    val fgColor =
        when {
            isSelected -> onPrimaryColor
            cell.isToday -> primaryColor
            !cell.inPeriod -> onSurfaceVariantColor
            else -> onSurfaceColor
        }

    val boxBaseModifier =
        if (cell.marks.hasMarks) {
            DayNumberBoxWithMarksModifier
        } else {
            DayNumberBoxNoMarksModifier
        }
    val boxModifier =
        if (isSelected || cell.isToday) {
            boxBaseModifier.drawBehind {
                if (isSelected) {
                    drawCircle(primaryColor)
                } else {
                    drawCircle(
                        color = primaryColor,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        } else {
            boxBaseModifier
        }

    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .height(48.dp)
                .mintPressScale(interaction, 0.96f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = { onClick(cell.date) },
                    onLongClickLabel = previewLabel,
                    onLongClick = { onPreview(cell.date) },
                ).semantics(mergeDescendants = true) {
                    this.selected = isSelected
                    contentDescription = cell.fullDateDescription
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = boxModifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.dayText,
                color = fgColor,
                fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                style = bodyMedium,
            )
        }
        CalendarIndicators(
            marks = cell.marks,
            scheduleColor = scheduleColor,
            pendingColor = pendingColor,
            doneColor = doneColor,
        )
    }
}

@Immutable
private data class MonthGridCell(
    val date: LocalDate,
    val dayText: String,
    val inPeriod: Boolean,
    val isToday: Boolean,
    val marks: CalendarMarks,
    val fullDateDescription: String,
)

@Immutable
private data class MonthGridData(
    val rows: Int,
    val cells: List<MonthGridCell>,
    val agendaMap: Map<LocalDate, List<AgendaItem>>,
)

@Immutable
private sealed interface GridCacheKey {
    data class Month(
        val yearMonth: YearMonth,
    ) : GridCacheKey

    data class Week(
        val weekStart: LocalDate,
    ) : GridCacheKey
}

private class SimpleGridCache(
    private val maxSize: Int = 24,
) {
    private val map =
        object : LinkedHashMap<GridCacheKey, MonthGridData>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<GridCacheKey, MonthGridData>?): Boolean = size > maxSize
        }

    @Synchronized
    operator fun get(key: GridCacheKey): MonthGridData? = map[key]

    @Synchronized
    operator fun set(
        key: GridCacheKey,
        value: MonthGridData,
    ) {
        map[key] = value
    }

    @Synchronized
    fun getDayItems(date: LocalDate): List<AgendaItem>? {
        for (data in map.values) {
            val items = data.agendaMap[date]
            if (items != null) return items
        }
        return null
    }
}

private fun getOrComputeGridData(
    cache: SimpleGridCache,
    key: GridCacheKey,
    mode: CalMode,
    start: LocalDate,
    monthOf: LocalDate,
    first: DayOfWeek,
    today: LocalDate,
    snap: Snapshot,
    defaultBlockMinutes: Int,
    ctx: Context,
): MonthGridData {
    cache[key]?.let { return it }

    val rows =
        if (mode == CalMode.MONTH) {
            val end = monthOf.with(TemporalAdjusters.lastDayOfMonth()).with(TemporalAdjusters.nextOrSame(first.plus(6)))
            ((ChronoUnit.DAYS.between(start, end) + 1) / 7).toInt()
        } else {
            1
        }
    val totalDays = rows * 7
    val endDate = start.plusDays((totalDays - 1).toLong())
    val marksMap =
        Agenda
            .range(snap, start, endDate, defaultBlockMinutes)

    val inMonth = if (mode == CalMode.MONTH) monthOf.month else null
    val cells = ArrayList<MonthGridCell>(totalDays)
    val weekdayFullArray = ctx.resources.getStringArray(R.array.weekday_full)
    val emptyDetail = ctx.getString(R.string.calendar_day_detail, 0, 0, 0)

    for (i in 0 until totalDays) {
        val d = start.plusDays(i.toLong())
        val entries = marksMap[d].orEmpty()
        val marks = CalendarMarks.from(entries)
        val detail =
            if (!marks.hasMarks) {
                emptyDetail
            } else {
                ctx.getString(
                    R.string.calendar_day_detail,
                    marks.scheduled,
                    marks.pending,
                    marks.done,
                )
            }
        val fullDateStr =
            ctx.getString(
                R.string.fmt_full_date,
                d.year,
                d.monthValue,
                d.dayOfMonth,
                weekdayFullArray[(d.dayOfWeek.value - 1).coerceIn(0, 6)],
            )
        val desc = "$fullDateStr, $detail"
        cells.add(
            MonthGridCell(
                date = d,
                dayText = d.dayOfMonth.toString(),
                inPeriod = inMonth == null || d.month == inMonth,
                isToday = d == today,
                marks = marks,
                fullDateDescription = desc,
            ),
        )
    }
    val data = MonthGridData(rows = rows, cells = cells, agendaMap = marksMap)
    cache[key] = data
    return data
}

@Immutable
private data class CalendarMarks(
    val scheduled: Int,
    val pending: Int,
    val done: Int,
    val indicators: List<Int> =
        computeIndicators(
            scheduled,
            pending,
            done,
        ),
) {
    val hasMarks: Boolean get() = scheduled > 0 || pending > 0 || done > 0

    companion object {
        val Empty = CalendarMarks(0, 0, 0, emptyList())

        private fun computeIndicators(
            scheduled: Int,
            pending: Int,
            done: Int,
        ): List<Int> {
            if (scheduled == 0 && pending == 0 && done == 0) return emptyList()
            return buildList(3) {
                if (scheduled > 0) add(0)
                repeat(minOf(pending, 3 - size)) { add(1) }
                repeat(minOf(done, 3 - size)) { add(2) }
            }
        }

        fun from(entries: List<AgendaItem>): CalendarMarks {
            if (entries.isEmpty()) return Empty
            var scheduled = 0
            var pending = 0
            var done = 0
            for (entry in entries) {
                when (entry) {
                    is CourseItem, is EventItem -> scheduled++
                    is TaskItem -> if (entry.task.done) done++ else pending++
                    is BlockItem -> if (entry.task.done) done++ else pending++
                }
            }
            if (scheduled == 0 && pending == 0 && done == 0) return Empty
            return CalendarMarks(
                scheduled = scheduled,
                pending = pending,
                done = done,
            )
        }
    }
}

/**
 * 月份/周标题。pager 当前页在这里面读：翻页停稳只重组这一小块文本，
 * 不再让整个 CalendarScreen body（含三页月网格）跟着每页落定重跑。
 */
@Composable
private fun CalendarTitle(
    mode: CalMode,
    anchor: LocalDate,
    first: DayOfWeek,
    pager: PagerState,
    periodStart: (Int) -> LocalDate,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val currentPage = pager.currentPage
    val title =
        remember(mode, anchor, first, currentPage, periodStart) {
            when (mode) {
                CalMode.MONTH -> {
                    ctx.yearMonth(anchor.plusMonths((currentPage - PAGE_CENTER).toLong()))
                }

                CalMode.WEEK -> {
                    val s = periodStart(currentPage)
                    val e = s.plusDays(6)
                    if (s.month == e.month) {
                        ctx.getString(R.string.week_range_same_month, s.monthValue, s.dayOfMonth, e.dayOfMonth)
                    } else {
                        ctx.getString(R.string.week_range, s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth)
                    }
                }
            }
        }
    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun CalendarIndicators(
    marks: CalendarMarks,
    scheduleColor: Color,
    pendingColor: Color,
    doneColor: Color,
) {
    if (!marks.hasMarks) return
    val indicators = marks.indicators

    Canvas(IndicatorsCanvasModifier) {
        val barW = 6.dp.toPx()
        val barH = 3.dp.toPx()
        val ringR = 2.6.dp.toPx()
        val dotR = 2.dp.toPx()
        val gap = 3.dp.toPx()

        var total = 0f
        for (i in indicators.indices) {
            val k = indicators[i]
            total += if (k == 0) barW else (if (k == 1) ringR * 2 else dotR * 2)
        }
        total += gap * (indicators.size - 1)

        var x = (size.width - total) / 2f
        val midY = size.height / 2f
        val ringStroke = Stroke(1.dp.toPx())
        for (i in indicators.indices) {
            val k = indicators[i]
            val w = if (k == 0) barW else (if (k == 1) ringR * 2 else dotR * 2)
            when (k) {
                0 -> drawRoundRect(scheduleColor, Offset(x, midY - barH / 2f), Size(barW, barH), CornerRadius(barH / 2f))
                1 -> drawCircle(pendingColor, ringR, Offset(x + ringR, midY), style = ringStroke)
                else -> drawCircle(doneColor, dotR, Offset(x + dotR, midY))
            }
            x += w + gap
        }
    }
}
