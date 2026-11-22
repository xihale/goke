package app.goke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.CellKind
import app.goke.core.agenda.TimetableCell
import app.goke.core.agenda.TimetableDiff
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.change.apply
import app.goke.core.change.repeatInstances
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.ExceptionType
import app.goke.core.model.PeriodTime
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.WeekSets
import app.goke.core.util.Ids
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.LocalTabVisible
import app.goke.ui.Route
import app.goke.ui.Tab
import app.goke.ui.components.ConflictCard
import app.goke.ui.components.EmptyState
import app.goke.ui.components.FieldText
import app.goke.ui.components.MintCard
import app.goke.ui.components.NavListInset
import app.goke.ui.components.TaskRow
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.mintClickable
import app.goke.ui.components.mintPressable
import app.goke.ui.components.parcelCode
import app.goke.ui.dueLabel
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.liveSnapshot
import app.goke.ui.monthDay
import app.goke.ui.monthDayWeekday
import app.goke.ui.reduceMotion
import app.goke.ui.rememberTodayGated
import app.goke.ui.settingsState
import app.goke.ui.theme.MintColors
import app.goke.ui.theme.MintTheme
import app.goke.ui.weekdayFull
import app.goke.ui.weekdayShort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val BaseTimeColW = 34.dp
private val BaseRowH = 60.dp
private val GroupGap = 14.dp
private val HeaderH = 46.dp

/** 并排分道时每道至少要这么宽（两个 11sp 汉字 + 内边距），再窄就不并排了。 */
private val MinLaneW = 30.dp

/** 叠放簇右侧露出的那道边：宽度，以及和正面卡之间的缝。 */
private val PeekW = 4.dp
private val PeekGap = 1.5.dp

private val HeaderDayShape = RoundedCornerShape(14.dp)
private val CourseBlockShape = RoundedCornerShape(12.dp)
private val TaskBlockShape = RoundedCornerShape(12.dp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val RoomSplitRegex = Regex("[,，/、;；\\s]+")

private val BoundariesAbove8 = listOf(4, 8)
private val BoundariesAbove4 = listOf(4)

private fun groupBoundaries(periodCount: Int): List<Int> =
    when {
        periodCount > 8 -> BoundariesAbove8
        periodCount > 4 -> BoundariesAbove4
        else -> emptyList()
    }

private fun calcPeriodTop(
    p: Int,
    periodCount: Int,
    rowH: Dp = BaseRowH,
): Dp {
    var gaps = 0
    if (periodCount > 4 && p > 4) gaps++
    if (periodCount > 8 && p > 8) gaps++
    return rowH * (p - 1) + GroupGap * gaps
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableScreen() {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    // today 走门控派生态：隐藏 Tab 不养 30 秒后台协程，仅跨天时重组周网格
    val today = rememberTodayGated()
    val adv by app.advanced.state.collectAsState()
    val sem = snap.currentSemester
    // 还没建学期：保留 64dp 头部骨架，给一个入口引导去课表设置
    if (sem == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_timetable), style = MaterialTheme.typography.titleLarge)
                }
            }
            EmptyState(
                title = stringResource(R.string.timetable_empty_title),
                icon = Icons.Rounded.CalendarViewWeek,
                actionLabel = stringResource(R.string.timetable_import_jxfw),
                onAction = { nav.push(Route.Jxfw) },
                secondaryLabel = stringResource(R.string.timetable_create_semester),
                onSecondary = { nav.push(Route.EditTimetable) },
            )
        }
        return
    }
    val cal = remember(sem) { SemesterCalendar(sem) }
    val periods = remember(sem) { sem.periods.sortedBy { it.index } }
    val currentWeek = cal.weekOf(today)
    val startPage = (currentWeek.coerceIn(1, sem.totalWeeks)) - 1
    val pager = rememberPagerState(initialPage = startPage, pageCount = { sem.totalWeeks })
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var selected by remember { mutableStateOf<TimetableCell?>(null) }
    // 调课「课表选位」模式：选定课程后翻周点击目标格，自动带入周/星期/节次进入调课确认面板
    var pickingRescheduleFor by remember { mutableStateOf<CourseOccurrence?>(null) }
    var reschedulePrefill by remember { mutableStateOf<ReschedulePrefill?>(null) }
    BackHandler(enabled = LocalTabVisible.current.value && pickingRescheduleFor != null) { pickingRescheduleFor = null }
    // 同一时段多条安排：先列出让人挑，再进各自的详情
    var slotPick by remember { mutableStateOf<List<SlotEntry>?>(null) }
    var parcelSheet by remember { mutableStateOf<Pair<LocalDate, List<Task>>?>(null) }
    var emptyBannerDismissed by rememberSaveable { mutableStateOf(false) }

    // 启动后延迟开放预加载：初始为 0 避免冷/温启同步测量相邻周导致 TTID 回退；首屏稳定后切为 1 保障切周流畅度
    var beyondViewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(600L)
        beyondViewport = 1
    }

    // 「在课表上预览」：把 Dry Run 变更集应用到快照副本上，课表叠加显示新增 / 修改 / 删除
    val previewCs = if (adv.previewOnTimetable) adv.changeSet else null
    val afterSnap = remember(previewCs, snap) { previewCs?.let { snap.apply(it, 0).live() } }

    // 抽屉展开时隐藏底栏与撤销条，避免与应用内轻量抽屉重叠
    val sheetOpen = selected != null || slotPick != null || parcelSheet != null || reschedulePrefill != null
    DisposableEffect(sheetOpen) {
        if (sheetOpen) nav.composerOpen = true
        onDispose { if (sheetOpen) nav.composerOpen = false }
    }

    // 缓存每周的课表差异与待办块，避免翻周与预加载时重复全学期计算
    val weekDiffCache = remember(snap, afterSnap) { mutableMapOf<Int, List<TimetableCell>>() }
    val listColor = remember(snap) { snap.lists.associate { it.id to it.colorIndex } }
    val weekBlocksCache = remember(snap, listColor, sem) { mutableMapOf<Int, List<BlockRow>>() }

    // 预览开启时跳到第一个有变化的周
    val reduce = reduceMotion()
    LaunchedEffect(previewCs?.id, reduce) {
        if (afterSnap != null) {
            val firstWeek = (1..sem.totalWeeks).firstOrNull { w -> TimetableDiff.week(snap, afterSnap, w).any { it.kind != CellKind.NORMAL } }
            if (firstWeek != null) {
                if (reduce) pager.scrollToPage(firstWeek - 1) else pager.animateScrollToPage(firstWeek - 1)
            }
        }
    }
    // 再次点按「课表」Tab：跳回本周（仅在当前就是课表 Tab 时响应）
    LaunchedEffect(nav.reselect) {
        if (nav.reselect > 0 && nav.tab == Tab.TIMETABLE) {
            pager.scrollToPage(startPage)
        }
    }
    // 冲突面板「去查看」：跳到那一天所在的周并选中它
    LaunchedEffect(nav.focusDate) {
        val d = nav.focusDate ?: return@LaunchedEffect
        val w = cal.weekOf(d)
        if (w in 1..sem.totalWeeks) pager.scrollToPage(w - 1)
        nav.focusDate = null
    }

    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedContent(
                        targetState = pager.currentPage,
                        modifier = Modifier.weight(1f, fill = false),
                        transitionSpec = {
                            val sign = if (targetState > initialState) 1 else -1
                            (slideInVertically(slideSpec) { sign * it / 2 } + fadeIn(fadeSpec)) togetherWith
                                (slideOutVertically(slideSpec) { -sign * it / 2 } + fadeOut(fadeSpec))
                        },
                        label = "week-title",
                    ) { p ->
                        Column {
                            Text(
                                ctx.getString(R.string.week_n, p + 1),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                ctx.getString(R.string.fmt_range_md, cal.weekStart(p + 1).monthValue, cal.weekStart(p + 1).dayOfMonth, cal.weekEnd(p + 1).monthValue, cal.weekEnd(p + 1).dayOfMonth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = pager.currentPage + 1 != currentWeek && currentWeek in 1..sem.totalWeeks,
                        enter = fadeIn(MintTheme.spec()) + expandHorizontally(MintTheme.spec()),
                        exit = fadeOut(MintTheme.spec()) + shrinkHorizontally(MintTheme.spec()),
                    ) {
                        Box(
                            Modifier
                                .heightIn(min = 32.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                                    scope.launch { if (reduce) pager.scrollToPage(currentWeek - 1) else pager.animateScrollToPage(currentWeek - 1) }
                                }.padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.back_to_this_week),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                IconButton(onClick = { nav.push(Route.CourseEdit(null)) }) { Icon(Icons.Rounded.Add, stringResource(R.string.new_course)) }
                IconButton(onClick = { nav.push(Route.EditTimetable) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_timetable)) }
            }

            AnimatedVisibility(
                visible = previewCs != null,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                previewCs?.let {
                    PreviewBanner(
                        it,
                        onApply = { app.advanced.apply { actions.toast(ctx.getString(R.string.applied_toast, it.changes.size)) } },
                        onDiscard = { app.advanced.discard() },
                    )
                }
            }

            AnimatedVisibility(
                visible = pickingRescheduleFor != null,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                pickingRescheduleFor?.let { occ ->
                    ReschedulePickBanner(
                        courseName = occ.course.name,
                        onCancel = { pickingRescheduleFor = null },
                    )
                }
            }

            AnimatedVisibility(
                visible = snap.courses.isEmpty() && previewCs == null && !emptyBannerDismissed,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                EmptyTimetableBanner(
                    onImportJxfw = { nav.push(Route.Jxfw) },
                    onImportFile = { nav.push(Route.Import(null)) },
                    onDismiss = { emptyBannerDismissed = true },
                )
            }
            AnimatedVisibility(
                visible = snap.courses.isEmpty() && previewCs == null && emptyBannerDismissed,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                Text(
                    stringResource(R.string.timetable_empty_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 宽度测量提到 HorizontalPager 外层：首帧直接取窗口像素宽度（兜底 screenWidthDp），
            // 容器实测后同步更新，让每页 WeekGrid 彻底摆脱 BoxWithConstraints 子组合
            val density = LocalDensity.current
            val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
            val fallbackWidth =
                with(density) {
                    val dmWidth = ctx.resources.displayMetrics.widthPixels
                    if (dmWidth > 0) dmWidth.toDp() else screenWidthDp
                }
            var measuredWidthPx by remember { mutableIntStateOf(0) }
            val gridWidth = if (measuredWidthPx > 0) with(density) { measuredWidthPx.toDp() } else fallbackWidth

            // beyondViewportPageCount：启动后延迟开放预加载，消除切周滑入瞬间一次性组装数十个文本节点导致的卡顿
            HorizontalPager(
                pager,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { measuredWidthPx = it.width },
                beyondViewportPageCount = beyondViewport,
                key = { it },
            ) { page ->
                val week = page + 1
                val cells =
                    remember(snap, afterSnap, week) {
                        weekDiffCache.getOrPut(week) { TimetableDiff.week(snap, afterSnap, week) }
                    }
                val weekStart = remember(cal, week) { cal.weekStart(week) }
                val weekEnd = remember(cal, week) { cal.weekEnd(week) }
                // 重复的待办按周展开：同一时间块每周都画（结束日到了就停）
                val blocks =
                    remember(snap, week, listColor, weekStart, weekEnd) {
                        weekBlocksCache.getOrPut(week) {
                            snap.tasks
                                .filter { t -> t.parentId == null && (t.blockStart ?: t.due) != null }
                                .flatMap { t ->
                                    val at = t.blockStart ?: t.due!!
                                    val days =
                                        if (t.blockStart == null) {
                                            listOf(at.toLocalDate())
                                        } else {
                                            repeatInstances(at.toLocalDate(), t.repeat?.freq, t.repeat?.until)
                                        }
                                    days
                                        .filter { d -> !d.isBefore(weekStart) && !d.isAfter(weekEnd) }
                                        .map { d -> BlockRow(t, listColor[t.listId] ?: 1, LocalDateTime.of(d, at.toLocalTime()), t.blockStart != null) }
                                }
                        }
                    }
                // clipToBounds:视差 translationX 会把相邻页的内容拖进本页边缘(右侧露出邻页节次列),按页裁掉
                Box(Modifier.clipToBounds()) {
                    WeekGrid(
                        sem,
                        cal,
                        week,
                        today,
                        cells,
                        blocks,
                        settings.defaultBlockMinutes,
                        settings.showTeacher,
                        gridWidth,
                        scroll,
                        modifier =
                            Modifier.graphicsLayer {
                                // 滑动视差：偏移符号与旧 calculateCurrentOffsetForPage 相反（相邻页反向平移）
                                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                translationX = off * 12.dp.toPx()
                            },
                        periods = periods,
                        onClick = { cell ->
                            val picking = pickingRescheduleFor
                            if (picking != null) {
                                val span = (picking.endPeriod - picking.startPeriod).coerceAtLeast(0)
                                val sp = cell.occ.startPeriod.coerceIn(1, periods.size)
                                val ep = (sp + span).coerceAtMost(periods.size)
                                reschedulePrefill = ReschedulePrefill(picking, week, cell.occ.date.dayOfWeek.value, sp, ep, picking.room)
                                pickingRescheduleFor = null
                            } else {
                                selected = cell
                            }
                        },
                        onOpenTask = { nav.openTask(it) },
                        onSlotClick = { slotPick = it },
                        onParcelClick = { d, list -> parcelSheet = d to list },
                        onMoveTask = { task, s, m ->
                            val end = s.plusMinutes(m.toLong())
                            actions.commit(
                                Edits.save(
                                    snap,
                                    task.copy(blockStart = s, blockMinutes = m, due = end, dueHasTime = true),
                                    ctx.getString(R.string.edit_task_desc, task.title),
                                    System.currentTimeMillis(),
                                ),
                                ctx.getString(R.string.edit_task_desc, task.title),
                            )
                        },
                        onBoxSelect = { days, s, e ->
                            val startP = periods.firstOrNull { it.index == s } ?: return@WeekGrid
                            val endP = periods.firstOrNull { it.index == e } ?: return@WeekGrid
                            val dates = days.map { cal.dateOf(week, it) }
                            val first = dates.first()
                            nav.push(
                                Route.TodoEdit(
                                    id = null,
                                    date = first.toString(),
                                    start = first.atTime(startP.start).toString(),
                                    end = first.atTime(endP.end).toString(),
                                    extraDates = dates.drop(1).joinToString(",") { it.toString() },
                                ),
                            )
                        },
                        onEmptySlotClick = { dow, period ->
                            val picking = pickingRescheduleFor
                            if (picking != null) {
                                val span = (picking.endPeriod - picking.startPeriod).coerceAtLeast(0)
                                val sp = period.coerceIn(1, periods.size)
                                val ep = (sp + span).coerceAtMost(periods.size)
                                reschedulePrefill = ReschedulePrefill(picking, week, dow, sp, ep, picking.room)
                                pickingRescheduleFor = null
                            } else {
                                nav.push(Route.CourseEdit(id = null, initialDow = dow, initialPeriod = period))
                            }
                        },
                    )
                }
            }
        }

        CourseSheet(
            cell = selected,
            prefill = reschedulePrefill,
            sem = sem,
            snap = snap,
            cal = cal,
            onPickOnGrid = { occ ->
                selected = null
                reschedulePrefill = null
                pickingRescheduleFor = occ
            },
            onJumpToWeek = { targetWeek ->
                if (targetWeek in 1..sem.totalWeeks && targetWeek != pager.currentPage + 1) {
                    scope.launch {
                        if (reduce) pager.scrollToPage(targetWeek - 1) else pager.animateScrollToPage(targetWeek - 1)
                    }
                }
            },
            onDismiss = {
                selected = null
                reschedulePrefill = null
            },
        )
        SlotSheet(
            slotPick,
            onDismiss = { slotPick = null },
            onOpen = { e ->
                slotPick = null
                e.cell?.let { selected = it }
                e.task?.let { nav.openTask(it.id) }
            },
        )
        ParcelDaySheet(
            dayParcels = parcelSheet,
            snap = snap,
            onDismiss = { parcelSheet = null },
            onOpenTodoTab = {
                parcelSheet = null
                nav.tab = Tab.TODO
            },
            onOpenTask = {
                parcelSheet = null
                nav.openTask(it)
            },
        )
    }
}

@Composable
private fun EmptyTimetableBanner(
    onImportJxfw: () -> Unit,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    MintCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.timetable_empty_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.timetable_empty_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImportJxfw, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.timetable_import_jxfw))
            }
            FilledTonalButton(onClick = onImportFile, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.import_file))
            }
        }
    }
}

@Composable
private fun ParcelDaySheet(
    dayParcels: Pair<LocalDate, List<Task>>?,
    snap: Snapshot,
    onDismiss: () -> Unit,
    onOpenTodoTab: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    var lastPair by remember { mutableStateOf(dayParcels) }
    LaunchedEffect(dayParcels) { if (dayParcels != null) lastPair = dayParcels }
    val active = dayParcels ?: lastPair
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val now = remember { LocalDateTime.now() }
    val liveTasks =
        remember(active, snap) {
            active?.let { (d, list) ->
                val ids = list.map { it.id }.toSet()
                d to snap.tasks.filter { it.id in ids && !it.done && it.deletedAt == null }
            }
        }

    InWindowSheet(
        visible = dayParcels != null,
        onDismiss = onDismiss,
    ) {
        val current = liveTasks ?: return@InWindowSheet
        val (date, parcels) = current
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.parcel_pickup_section), style = MaterialTheme.typography.titleMedium)
                    Text(
                        ctx.monthDayWeekday(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenTodoTab, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.go_todo))
                }
            }
            if (parcels.isEmpty()) {
                Text(
                    stringResource(R.string.today_all_done_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                parcels.forEach { t ->
                    TaskRow(
                        task = t,
                        onToggle = { task, done ->
                            toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title))
                        },
                        onOpen = onOpenTask,
                        subtitle = t.due?.let { ctx.dueLabel(it, t.dueHasTime, now.toLocalDate()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBanner(
    cs: ChangeSet,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MintTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.preview_mode), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("+${cs.addCount}", color = colors.added, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("~${cs.modifyCount}", color = colors.modified, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("−${cs.removeCount}", color = colors.removed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
        Button(onClick = onApply, enabled = !cs.isEmpty) { Text(stringResource(R.string.apply_changes)) }
    }
}

@Composable
private fun WeekGrid(
    sem: Semester,
    cal: SemesterCalendar,
    week: Int,
    today: LocalDate,
    cells: List<TimetableCell>,
    blocks: List<BlockRow>,
    defaultBlockMinutes: Int,
    showTeacher: Boolean,
    gridWidth: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    periods: List<PeriodTime> = remember(sem) { sem.periods.sortedBy { it.index } },
    onClick: (TimetableCell) -> Unit,
    onOpenTask: (String) -> Unit,
    /** 叠放的正面卡被点开：同一时段的全部安排，由调用方列出让用户挑。 */
    onSlotClick: (List<SlotEntry>) -> Unit,
    onParcelClick: (LocalDate, List<Task>) -> Unit,
    onMoveTask: (Task, LocalDateTime, Int) -> Unit,
    onBoxSelect: (days: IntRange, startPeriod: Int, endPeriod: Int) -> Unit,
    onEmptySlotClick: (dow: Int, period: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.35f)
    val rowH = BaseRowH * fontScale
    val timeColW = BaseTimeColW * fontScale
    val colW = (gridWidth - timeColW) / 7

    fun periodTop(
        p: Int,
        periodCount: Int,
    ): Dp = calcPeriodTop(p, periodCount, rowH)
    val n = periods.size
    val boundaries = remember(n) { groupBoundaries(n) }
    val totalH = periodTop(n, n) + rowH
    val mintColors = MintTheme.colors
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outlineVariant
    val primaryContainer = scheme.primaryContainer
    val onPrimaryContainer = scheme.onPrimaryContainer
    val color = scheme.onSurface
    val variantColor = scheme.onSurfaceVariant
    val typography = MaterialTheme.typography
    val labelStyle = typography.labelMedium
    val bodySmall = typography.bodySmall
    val labelSmall = typography.labelSmall
    val headerDateStyle =
        remember(bodySmall) {
            bodySmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum")
        }
    val weekdayShorts =
        remember(ctx) {
            (1..7).map { ctx.weekdayShort(it) }
        }
    val timeStyle =
        remember(labelSmall) {
            labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum")
        }
    val courseTitleStyle =
        remember(labelSmall) {
            labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp)
        }
    val courseRoomStyleWide =
        remember(labelSmall) {
            labelSmall.copy(fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 0.sp)
        }
    val courseRoomStyleNarrow =
        remember(labelSmall) {
            labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp)
        }
    // 主网格只画真正占住专注时间的块：无具体时刻、取件待办、跨度 >3 小时的柔性时间窗一律不拉色柱
    val gridBlocks =
        remember(blocks, defaultBlockMinutes) {
            blocks.filter { b ->
                b.timed && b.task.parcelCode() == null && (b.task.blockMinutes ?: defaultBlockMinutes) <= 180
            }
        }
    val parcelsByDay =
        remember(blocks) {
            blocks
                .asSequence()
                .filter { !it.task.done && (it.task.parcelCode() != null || it.task.source == Source.PARCEL) }
                .distinctBy { it.task.id to it.start.toLocalDate() }
                .groupBy({ it.start.toLocalDate() }, { it.task })
        }
    // 冲突摆放：同一列里重叠的课程 / 待办按簇处理。
    // 并排分道是日历的主流做法，但 7 天网格一列只有 40 来 dp——分两道后每道连一个字都放不下，
    // 标题被挤成竖排碎片（旧版就是这样）。所以只有每道 ≥ MinLaneW 才并排（平板 / 横屏），
    // 再窄就叠放：课程优先当正面卡，其余只在右侧露一道边，底栏写「N 项」，
    // 点开列出这段时间的全部安排自己挑——Google 日历 / Outlook 在窄列里也是这么收敛的。
    val geoms =
        remember(cells, gridBlocks, n, rowH) {
            val courseBoxes =
                cells.map { c ->
                    val o = c.occ
                    LaneBox(
                        o.date.dayOfWeek.value,
                        periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp,
                        periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + rowH - 2.dp,
                        o.course.colorIndex,
                        course = true,
                    )
                }
            val drawable =
                gridBlocks.mapNotNull { b ->
                    val s = b.start
                    val e = if (b.timed) s.plusMinutes(b.minutes.toLong()) else s.plusMinutes(1)
                    Agenda.blockRows(periods, s.toLocalTime(), e.toLocalTime())?.let { rows ->
                        b to LaneBox(s.dayOfWeek.value, periodTop(rows.first, n) + 2.dp, periodTop(rows.last, n) + rowH - 2.dp, b.color)
                    }
                }
            courseBoxes to drawable
        }
    val slotLayout = remember(geoms, colW) { layoutSlots(geoms.first + geoms.second.map { it.second }, colW) }
    // 冲突簇的成员清单：点正面卡时列出来，用户自己挑要看哪条
    val slotEntries =
        remember(cells, geoms, slotLayout) {
            val courseCount = cells.size
            buildMap {
                slotLayout.groups.forEach { g ->
                    val items =
                        g.map { i ->
                            if (i < courseCount) {
                                val o = cells[i].occ
                                val from = cal.startTime(o.startPeriod)
                                SlotEntry(
                                    o.course.name,
                                    "${from?.hm().orEmpty()}–${cal.endTime(o.endPeriod)?.hm().orEmpty()} · ${ctx.getString(R.string.entity_course)}",
                                    o.course.colorIndex,
                                    o.date.atTime(from ?: java.time.LocalTime.MIN),
                                    cells[i],
                                    null,
                                )
                            } else {
                                val b = geoms.second[i - courseCount].first
                                SlotEntry(
                                    b.task.title,
                                    "${b.start.hm()}–${b.start.plusMinutes(b.minutes.toLong()).hm()} · ${ctx.getString(R.string.entity_task)}",
                                    b.color,
                                    b.start,
                                    null,
                                    b.task,
                                )
                            }
                        }
                    g.forEach { put(it, items) }
                }
            }
        }
    Column(modifier.fillMaxSize()) {
        // 星期头：今天垫一块 primaryContainer 并换强调色文字；有待取快递的日期在列顶渲染紧凑胶囊。
        Row(Modifier.fillMaxWidth().heightIn(min = HeaderH).padding(start = timeColW)) {
            for (d in 1..7) {
                val date = cal.dateOf(week, d)
                val isToday = date == today
                val dayTextColor = if (isToday) onPrimaryContainer else variantColor
                val dayParcels = parcelsByDay[date].orEmpty()
                val parcelDesc =
                    if (dayParcels.isNotEmpty()) {
                        "${stringResource(R.string.parcel_pickup_section)} ${dayParcels.size}"
                    } else {
                        null
                    }
                val clickMod =
                    if (dayParcels.isNotEmpty()) {
                        Modifier
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) { onParcelClick(date, dayParcels) }
                            .semantics { parcelDesc?.let { contentDescription = it } }
                    } else {
                        Modifier
                    }
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = HeaderH)
                        .padding(2.dp)
                        .clip(HeaderDayShape)
                        .background(if (isToday) primaryContainer else Color.Transparent)
                        .then(clickMod)
                        .padding(vertical = if (parcelsByDay.isNotEmpty()) 3.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(weekdayShorts[d - 1], style = labelStyle, color = dayTextColor)
                    Text("${date.monthValue}/${date.dayOfMonth}", style = headerDateStyle, color = dayTextColor)
                    if (dayParcels.isNotEmpty()) {
                        val pillBg = if (isToday) scheme.surface.copy(alpha = 0.9f) else primaryContainer
                        val pillFg = if (isToday) scheme.primary else onPrimaryContainer
                        Row(
                            Modifier
                                .padding(top = 2.dp)
                                .clip(CircleShape)
                                .background(pillBg)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(Icons.Rounded.Inventory2, null, tint = pillFg, modifier = Modifier.size(10.dp))
                            Text(
                                "${dayParcels.size}",
                                style = labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = pillFg,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // 和「今日」一样：网格铺满，底栏浮在上面，末尾留一段空白让最后一节也能滚到岛上方。
        Box(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = NavListInset),
        ) {
            // 长按空白处拖动框选：横跨几天、纵跨几节 → 一次建多条（重复/冲突在编辑页里定）
            var box by remember { mutableStateOf<BoxSel?>(null) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(totalH)
                    .drawBehind {
                        // 组间分隔线 + 冲突叠放簇右侧露边一次画在网格底板上，省掉额外 Box 布局节点
                        val startX = timeColW.toPx()
                        val lineW = (size.width - startX).coerceAtLeast(0f)
                        val lineH = 1.dp.toPx()
                        boundaries.forEach { b ->
                            val y = (periodTop(b, n) + rowH + GroupGap / 2).toPx()
                            drawRect(
                                color = outline,
                                topLeft = Offset(startX, y),
                                size = Size(lineW, lineH),
                            )
                        }
                        if (slotLayout.peeks.isNotEmpty()) {
                            val peekCorner = CornerRadius(12.dp.toPx())
                            val blockInsetPx = 1.5.dp.toPx()
                            val cardW = (colW - 3.dp).toPx()
                            val backOverlapOffset = 10.dp.toPx().coerceAtMost(cardW * 0.4f)
                            slotLayout.peeks.forEach { p ->
                                val colLeft = (timeColW + colW * (p.day - 1)).toPx() + blockInsetPx
                                val x = colLeft + backOverlapOffset
                                val w = (cardW - backOverlapOffset).coerceAtLeast(PeekW.toPx())
                                val y = p.top.toPx()
                                val h = (p.bottom - p.top).toPx()
                                drawRoundRect(
                                    color = mintColors.bg(p.colorIndex),
                                    topLeft = Offset(x, y),
                                    size = Size(w, h),
                                    cornerRadius = peekCorner,
                                )
                            }
                        }
                    }.pointerInput(n, timeColW, rowH, cells, gridBlocks) {
                        val timeColPx = timeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = rowH.toPx()

                        fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                        fun slotOf(y: Float): Int {
                            for (p in 1..n) {
                                val top = (rowH.value * (p - 1) + GroupGap.value * boundaries.count { p > it }) * density
                                if (y < top + rowPx) return p
                            }
                            return n
                        }

                        detectTapGestures(
                            onTap = { off ->
                                if (box == null && off.x >= timeColPx) {
                                    val d = colOf(off.x)
                                    val p = slotOf(off.y)
                                    val hasCourse = cells.any { it.occ.date.dayOfWeek.value == d && p in it.occ.startPeriod..it.occ.endPeriod }
                                    val hasTask =
                                        gridBlocks.any { b ->
                                            val s = b.start
                                            val e = if (b.timed) s.plusMinutes(b.minutes.toLong()) else s.plusMinutes(1)
                                            s.dayOfWeek.value == d && Agenda.blockRows(periods, s.toLocalTime(), e.toLocalTime())?.let { p in it } == true
                                        }
                                    if (!hasCourse && !hasTask) {
                                        onEmptySlotClick(d, p)
                                    }
                                }
                            },
                        )
                    }.pointerInput(n, timeColW, rowH) {
                        val timeColPx = timeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = rowH.toPx()

                        fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                        fun slotOf(y: Float): Int {
                            for (p in 1..n) {
                                val top = (rowH.value * (p - 1) + GroupGap.value * boundaries.count { p > it }) * density
                                if (y < top + rowPx) return p
                            }
                            return n
                        }
                        var anchorDay = 1
                        var anchorSlot = 1
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                if (off.x >= timeColPx) {
                                    anchorDay = colOf(off.x)
                                    anchorSlot = slotOf(off.y)
                                    box = BoxSel(anchorDay, anchorDay, anchorSlot, anchorSlot)
                                }
                            },
                            onDrag = { change, _ ->
                                val b = box ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                val d = colOf(change.position.x)
                                val e = slotOf(change.position.y).coerceIn(1, n)
                                box = b.copy(day2 = d, first = minOf(anchorSlot, e), last = maxOf(anchorSlot, e))
                            },
                            onDragEnd = {
                                box?.let { b -> onBoxSelect(minOf(b.day1, b.day2)..maxOf(b.day1, b.day2), b.first, b.last) }
                                box = null
                            },
                            onDragCancel = { box = null },
                        )
                    },
            ) {
                // 不画「当前时间」的线和圆点：7 天网格里一列只有 40 来 dp，
                // 那条线横穿在一整列色块上，多一根线只是噪声；今天靠星期头的色块指认。
                // 左侧节次：开始 / 结束时间都给，两行 tnum 数字
                val periodLabels =
                    remember(periods, timeStyle, labelStyle, color, variantColor) {
                        periods.map { p ->
                            buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = labelStyle.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                    ),
                                ) {
                                    append("${p.index}\n")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = timeStyle.fontSize,
                                        color = variantColor,
                                        fontFeatureSettings = "tnum",
                                    ),
                                ) {
                                    append("${p.start.hm()}\n${p.end.hm()}")
                                }
                            }
                        }
                    }
                periods.forEachIndexed { i, p ->
                    Text(
                        text = periodLabels[i],
                        modifier =
                            Modifier
                                .absoluteOffset(0.dp, periodTop(p.index, n) + 5.dp)
                                .width(timeColW),
                        style = timeStyle,
                        textAlign = TextAlign.Center,
                    )
                }
                // 课程块
                cells.forEachIndexed { ci, cell ->
                    val slot = slotLayout.slots[ci]
                    if (slot.hidden) return@forEachIndexed
                    val o = cell.occ
                    val top = periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp
                    val bottom = periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + rowH - 2.dp
                    val laneW = (colW - 3.dp) / slot.lanes
                    // 块宽在这里算一次：传给 Modifier.width 和 CourseBlock 的 wide 判定（省掉每块一个子组合）
                    val blockW =
                        when {
                            slot.lanes > 1 -> laneW - 2.dp
                            slot.stacked -> laneW - PeekW - PeekGap
                            else -> laneW
                        }
                    val isWide = blockW >= 64.dp
                    CourseBlock(
                        cell,
                        showTeacher,
                        slot.overlap,
                        isWide,
                        mintColors,
                        courseTitleStyle,
                        if (isWide) courseRoomStyleWide else courseRoomStyleNarrow,
                        Modifier
                            .absoluteOffset(timeColW + colW * (o.date.dayOfWeek.value - 1) + 1.5.dp + laneW * slot.lane, top)
                            .width(blockW)
                            .height(bottom - top),
                        onClick = { if (slot.overlap > 1) onSlotClick(slotEntries[ci].orEmpty()) else onClick(cell) },
                    )
                }
                // 待办时间块:排入日历的按时长,普通待办画在截止时刻所在的节次;与课程或其它待办重叠时按上面的规则并排或叠放
                geoms.second.forEachIndexed { di, entry ->
                    val (b, tbox) = entry
                    val slot = slotLayout.slots[cells.size + di]
                    if (slot.hidden) return@forEachIndexed
                    val t = b.task
                    val s = b.start
                    val laneW = (colW - 3.dp) / slot.lanes
                    val minutes = b.minutes
                    TaskBlock(
                        t,
                        b.color,
                        s,
                        minutes,
                        slot.overlap,
                        mintColors,
                        courseTitleStyle,
                        timeStyle,
                        onCommit = { ns, nm -> onMoveTask(t, ns, nm) },
                        modifier =
                            Modifier
                                .absoluteOffset(timeColW + colW * (s.dayOfWeek.value - 1) + 1.5.dp + laneW * slot.lane, tbox.top)
                                .width(
                                    when {
                                        slot.lanes > 1 -> laneW - 2.dp
                                        slot.stacked -> laneW - PeekW - PeekGap
                                        else -> laneW
                                    },
                                ).height(tbox.bottom - tbox.top),
                        onClick = { if (slot.overlap > 1) onSlotClick(slotEntries[cells.size + di].orEmpty()) else onOpenTask(t.id) },
                    )
                }
                // 框选高亮：出现带一拍淡入，不突兀
                box?.let { b ->
                    val boxAlpha by animateFloatAsState(if (box != null) 1f else 0f, MintTheme.spec(), label = "box-sel")
                    val top = periodTop(b.first, n) + 2.dp
                    val bottom = periodTop(b.last, n) + rowH - 2.dp
                    val d1 = minOf(b.day1, b.day2)
                    val d2 = maxOf(b.day1, b.day2)
                    Box(
                        Modifier
                            .graphicsLayer { alpha = boxAlpha }
                            .absoluteOffset(timeColW + colW * (d1 - 1) + 1.5.dp, top)
                            .width(colW * (d2 - d1 + 1) - 3.dp)
                            .height(bottom - top)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    }
}

/** 课表格子里的一条待办：同一条重复待办每周各算一条。 */
private data class BlockRow(
    val task: Task,
    val color: Int,
    val start: LocalDateTime,
    val timed: Boolean,
) {
    val minutes: Int get() = task.blockMinutes ?: 60
}

/** 课表框选：day 1-7，first/last 为节次序号（升序）。 */
private data class BoxSel(
    val day1: Int,
    val day2: Int,
    val first: Int,
    val last: Int,
)

/** 一周网格里的占位矩形：并排 / 叠放计算用（day 1-7，top/bottom 为格内纵坐标，colorIndex 是这条的底色）。 */
private data class LaneBox(
    val day: Int,
    val top: Dp,
    val bottom: Dp,
    val colorIndex: Int,
    /** 课程优先当叠放的正面卡：课是固定的锚，待办是可以挪的那一方。 */
    val course: Boolean = false,
)

/** 同时段里一条块怎么摆。 */
private data class Slot(
    /** 并排时第几道。 */
    val lane: Int = 0,
    /** 一共几道；1 = 整宽。 */
    val lanes: Int = 1,
    /** 列太窄、放不下两道：整簇改叠放，正面卡右侧留出 PeekW 的露边。 */
    val stacked: Boolean = false,
    /** 叠放时被压住的成员不单独画。 */
    val hidden: Boolean = false,
    /** 同时段的块数；>1 就是冲突，底栏写「N 项」。 */
    val overlap: Int = 1,
)

private class SlotLayout(
    val slots: List<Slot>,
    /** 叠放簇右侧露出的那道边：纵向取整簇并集，颜色取被压住那条的底色。 */
    val peeks: List<LaneBox>,
    /** 冲突簇的成员下标（只有 ≥2 条重叠的才收进来）。 */
    val groups: List<List<Int>>,
)

/**
 * 冲突摆放：按天把重叠的块聚成一簇。每道宽度够（≥ [MinLaneW]）就并排各占一道；
 * 不够就叠放——最早开始的那条正面整宽，其余只在右侧露一道边。两条路都保证谁也不压住谁的字。
 */
private fun layoutSlots(
    boxes: List<LaneBox>,
    colW: Dp,
): SlotLayout {
    val slots = Array(boxes.size) { Slot() }
    val peeks = ArrayList<LaneBox>()
    val groups = ArrayList<List<Int>>()
    for (day in 1..7) {
        val dayBoxes =
            boxes
                .withIndex()
                .filter { it.value.day == day }
                .sortedWith(compareBy({ it.value.top }, { it.value.bottom }))
        var cluster = ArrayList<Int>(2)
        var end = 0.dp

        fun flush() {
            if (cluster.size > 1) {
                // 课程在前、再按开始时间、再按长短：正面卡露出来的应该是那条最像「锚」的
                val ordered =
                    cluster.sortedWith(
                        Comparator { a, b ->
                            val ca = boxes[a]
                            val cb = boxes[b]
                            when {
                                ca.course != cb.course -> if (ca.course) -1 else 1
                                ca.top != cb.top -> ca.top.compareTo(cb.top)
                                else -> cb.bottom.compareTo(ca.bottom)
                            }
                        },
                    )
                groups += ordered
                if ((colW - 3.dp) / cluster.size >= MinLaneW) {
                    ordered.forEachIndexed { lane, i -> slots[i] = Slot(lane = lane, lanes = cluster.size, overlap = cluster.size) }
                } else {
                    ordered.forEachIndexed { idx, i -> slots[i] = Slot(stacked = true, hidden = idx > 0, overlap = cluster.size) }
                    val front = boxes[ordered.first()]
                    val inset = if (front.bottom - front.top > 20.dp) 3.dp else 0.dp
                    peeks +=
                        LaneBox(
                            front.day,
                            front.top + inset,
                            front.bottom - inset,
                            boxes[ordered[1]].colorIndex,
                        )
                }
            }
            cluster = ArrayList(2)
        }
        for ((i, b) in dayBoxes) {
            if (cluster.isNotEmpty() && b.top >= end) flush()
            end = if (cluster.isEmpty()) b.bottom else maxOf(end, b.bottom)
            cluster += i
        }
        if (cluster.isNotEmpty()) flush()
    }
    return SlotLayout(slots.toList(), peeks, groups)
}

/** 冲突簇里的一条：叠放的正面卡点开后列出来，用户自己挑要看哪条。 */
private data class SlotEntry(
    val title: String,
    val detail: String,
    val colorIndex: Int,
    /** 排序用：同一簇里按实际开始时间排，和网格里的先后一致。 */
    val start: LocalDateTime,
    val cell: TimetableCell?,
    val task: Task?,
)

/** 应用窗口内轻量抽屉：避免 ModalBottomSheet 新建独立 Dialog Window 带来的 14 次同步 Binder 与窗口重排。 */
@Composable
private fun InWindowSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = visible && LocalTabVisible.current.value) { onDismiss() }
    val reduce = reduceMotion()
    val enterSpec = if (reduce) snap() else MintTheme.spec<IntOffset>()
    val exitSpec = if (reduce) snap() else MintTheme.spec<IntOffset>()
    val fadeSpec = if (reduce) snap() else MintTheme.spec<Float>()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(fadeSpec),
        exit = fadeOut(fadeSpec),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(enterSpec) { it },
        exit = slideOutVertically(exitSpec) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures {}
                        },
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    Box(
                        Modifier
                            .padding(top = 12.dp, bottom = 8.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally),
                    )
                    content()
                }
            }
        }
    }
}

/** 同一时段有多条安排：列出来让用户挑，而不是把彼此挤成看不动的碎片。 */
@Composable
private fun SlotSheet(
    entries: List<SlotEntry>?,
    onDismiss: () -> Unit,
    onOpen: (SlotEntry) -> Unit,
) {
    val visible = entries != null
    var lastEntries by remember { mutableStateOf(entries) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(entries) {
        if (entries != null) {
            lastEntries = entries
            submitted = false
        }
    }
    val active = entries ?: lastEntries

    InWindowSheet(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        if (active == null) return@InWindowSheet
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.conflict_slot_title, active.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // 扁平行（52dp 起步，与其它抽屉的操作行一致），不套卡片：四条卡叠起来太吵
            active.sortedBy { it.start }.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .mintClickable(enabled = visible && !submitted, pressedScale = 0.98f) {
                            if (!visible || submitted) return@mintClickable
                            submitted = true
                            onOpen(e)
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 32.dp)
                            .clip(CircleShape)
                            .background(MintTheme.colors.bg(e.colorIndex)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(e.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TaskBlock(
    task: Task,
    colorIndex: Int,
    start: LocalDateTime,
    minutes: Int,
    /** 同时段块数；>1 时底栏写「N 项」，点开由正面卡列出全部。 */
    overlap: Int,
    colors: MintColors,
    titleStyle: TextStyle,
    timeStyle: TextStyle,
    onCommit: (LocalDateTime, Int) -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 与 CourseBlock 同款:圆角填色块、同字号,无勾选圈;点击进编辑
    val bg = colors.bg(colorIndex)
    val fg = colors.fg(colorIndex)
    val haptics = LocalHapticFeedback.current
    var hPx by remember { mutableFloatStateOf(0f) }
    // 0 不动 / 1 整体移动 / 2 拖上边 / 3 拖下边
    var mode by remember { mutableIntStateOf(0) }
    // 拖动中的目标偏移，已经按 15 分钟吸附：视觉跟数值一致，不是松手才算
    var step by remember { mutableIntStateOf(0) }
    val active = mode != 0
    val dur = minutes.coerceAtLeast(15)
    val pxPerMin = if (hPx > 0f) hPx / dur.toFloat() else 0f
    val dy = step * pxPerMin

    // 拖动中的预览：起点、时长、终点
    val previewStart = if (mode == 2) start.plusMinutes(step.toLong()) else start
    val previewMin =
        when (mode) {
            2 -> dur - step
            3 -> dur + step
            else -> dur
        }
    val previewEnd = previewStart.plusMinutes(previewMin.toLong())

    // 拖动描边无须常驻动画协程，仅拖动态激活
    val dragBorder = if (active) MaterialTheme.colorScheme.primary else Color.Transparent

    val layer =
        if (mode == 2) {
            Modifier.graphicsLayer {
                translationY = dy
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = ((hPx - dy) / hPx.coerceAtLeast(1f)).coerceIn(0.1f, 6f)
            }
        } else if (mode == 3) {
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = ((hPx + dy) / hPx.coerceAtLeast(1f)).coerceIn(0.1f, 6f)
            }
        } else if (mode == 1) {
            Modifier.graphicsLayer { translationY = dy }
        } else {
            Modifier
        }

    Box(
        modifier
            .zIndex(if (active) 3f else 0f)
            .onSizeChanged { hPx = it.height.toFloat() }
            .then(layer)
            .mintPressable(pressedScale = if (active) 1f else 0.97f) { onClick() }
            .pointerInput(dur, hPx) {
                if (hPx <= 0f) return@pointerInput
                val pxPerMinute = hPx / dur.toFloat()
                val minResizePx = 48.dp.toPx()
                val edgePx = minOf(16.dp.toPx(), hPx * 0.25f)
                var dragDeltaPx = 0f

                /** 把手指位移换算成吸附后的分钟数，并按模式夹住上下限 */
                fun snapTo(rawMin: Float): Int {
                    val s = (rawMin / 15f).roundToInt() * 15
                    return when (mode) {
                        1 -> {
                            // 整天内随便挪：00:00 ~ 24:00 之间放得下就行
                            val maxUp = -start.toLocalTime().toSecondOfDay() / 60
                            val maxDown = 24 * 60 - dur - start.toLocalTime().toSecondOfDay() / 60
                            s.coerceIn(maxUp, maxDown)
                        }

                        2 -> {
                            s.coerceIn(15 - dur, dur - 15)
                        }

                        else -> {
                            s.coerceAtLeast(15 - dur)
                        }
                    }
                }

                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        val canResize = hPx >= minResizePx
                        mode =
                            when {
                                canResize && off.y < edgePx -> 2
                                canResize && off.y > hPx - edgePx -> 3
                                else -> 1
                            }
                        dragDeltaPx = 0f
                        step = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDeltaPx += amount.y
                        val next = snapTo(dragDeltaPx / pxPerMinute)
                        if (next != step) {
                            step = next
                            // 拖到新的 15 分钟档才给一声刻度音，而不是逐像素连续振
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                    onDragEnd = {
                        if (step != 0) {
                            when (mode) {
                                1 -> onCommit(start.plusMinutes(step.toLong()), dur)
                                2 -> onCommit(start.plusMinutes(step.toLong()), dur - step)
                                3 -> onCommit(start, dur + step)
                            }
                        }
                        dragDeltaPx = 0f
                        step = 0
                        mode = 0
                    },
                    onDragCancel = {
                        dragDeltaPx = 0f
                        step = 0
                        mode = 0
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(TaskBlockShape)
                .background(if (task.done) bg.copy(alpha = 0.5f) else bg)
                .then(if (active) Modifier.border(2.dp, dragBorder, TaskBlockShape) else Modifier)
                .padding(horizontal = 5.dp, vertical = 5.dp),
        ) {
            // 失效态语法与「已调课」一致：底色降饱和；完成的不再画删除线，退灰就够
            Text(
                task.title,
                // 时间那行必须留得下：标题吃掉剩余空间，短块里自动收成省略号
                modifier = Modifier.weight(1f, fill = false),
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else fg,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            // 一格只有 40 来 dp 宽，放不下「10:15–11:15」；拖动时只报正在变的那一头。
            // 同时段还有别的安排时，这行改成「N 项」——冲突比几点更要紧，点开就能看全部。
            if (overlap > 1 && !active) {
                OverlapBadge(
                    overlap = overlap,
                    fg = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else fg,
                )
            } else {
                val label =
                    when {
                        !active -> start.hm()
                        mode == 3 -> previewEnd.hm()
                        else -> previewStart.hm()
                    }
                Text(
                    label,
                    color =
                        when {
                            active -> MaterialTheme.colorScheme.primary
                            task.done -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> fg.copy(alpha = 0.85f)
                        },
                    style = timeStyle,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        // 拖起来才出现的抓手：上边 = 改开始时间，下边 = 改结束时间；缩放淡入出现
        AnimatedVisibility(
            visible = active,
            enter = scaleIn(initialScale = 0.5f, animationSpec = MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = scaleOut(targetScale = 0.5f, animationSpec = MintTheme.spec()) + fadeOut(MintTheme.spec()),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                Handle(Modifier.padding(top = 2.dp), fg)
                Handle(Modifier.padding(bottom = 2.dp), fg)
            }
        }
    }
}

@Composable
private fun Handle(
    modifier: Modifier,
    fg: Color,
) {
    Box(
        modifier
            .size(width = 22.dp, height = 4.dp)
            .clip(CircleShape)
            .background(fg.copy(alpha = 0.9f)),
    )
}

@Composable
private fun OverlapBadge(
    overlap: Int,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(fg.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            stringResource(R.string.conflict_count, overlap),
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun CourseBlock(
    cell: TimetableCell,
    showTeacher: Boolean,
    /** 同时段块数；>1 时卡片底部写「N 项」，点开由这张正面卡列出全部。 */
    overlap: Int,
    /** 块宽是否 ≥64dp（宽列加「@」前缀区分教室）。由调用方按已知的块宽传入，
     *  不再每个块起一个 BoxWithConstraints 子组合（一页 30 块 = 30 次 subcompose）。 */
    wide: Boolean,
    colors: MintColors,
    titleStyle: TextStyle,
    roomStyle: TextStyle,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val o = cell.occ
    val bg = colors.bg(o.course.colorIndex)
    val fg = colors.fg(o.course.colorIndex)
    val rows = (o.endPeriod - o.startPeriod + 1).coerceAtLeast(1)
    val isRemoved = cell.kind == CellKind.REMOVED
    val borderColor =
        when (cell.kind) {
            CellKind.ADDED -> colors.added
            CellKind.MODIFIED -> colors.modified
            CellKind.REMOVED -> colors.removed
            CellKind.NORMAL -> Color.Transparent
        }
    val cellBg = if (isRemoved) colors.conflictBg else bg
    Box(
        modifier
            .mintPressable(pressedScale = 0.97f) { onClick() }
            .then(if (isRemoved) Modifier.graphicsLayer { alpha = 0.6f } else Modifier)
            .clip(CourseBlockShape)
            .background(cellBg)
            .then(if (borderColor != Color.Transparent) Modifier.border(2.dp, borderColor, CourseBlockShape) else Modifier),
    ) {
        // 一列就 40dp 出头：教室 / 老师照样要显示，靠换行和省略号交代；
        // 把信息藏起来比截断更糟。宽卡片（平板 / 横屏）才加「@」前缀区分教室，窄列里多这一个字符就没地方了。
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val strike = if (isRemoved) TextDecoration.LineThrough else null
            // 字号统一由 WeekGrid 记住传入；时间/教室数字用 tnum
            Text(
                o.course.name,
                color = if (isRemoved) colors.removed else fg,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines =
                    when {
                        // 冲突时得给底部的「N 项」留一行，标题少排一行也够认
                        overlap > 1 -> 2

                        // 教室占一行（两行内截断），标题剩几行按块高算
                        rows >= 2 -> 4

                        else -> 2
                    },
                overflow = TextOverflow.Ellipsis,
                textDecoration = strike,
            )
            // 多教室按 DESIGN 规则折叠（超过 2 处折叠为「首间 等 N 处」），完整列表留在 CourseSheet 中查看。
            if (o.room.isNotBlank()) {
                val ctx = LocalContext.current
                val roomText = remember(o.room, ctx) { ctx.formatRooms(o.room) }
                val teacherLine = if (showTeacher && rows >= 2 && o.course.teacher.isNotBlank()) 1 else 0
                val roomMaxLines =
                    (BaseRowH * rows - 16.dp - 26.dp - 12.dp * teacherLine).div(12.dp).toInt().coerceAtLeast(2)
                if (roomText.isNotEmpty()) {
                    Text(
                        if (wide) "@" + roomText else roomText,
                        modifier = Modifier.weight(1f, fill = false),
                        color = (if (isRemoved) colors.removed else fg).copy(alpha = 0.85f),
                        // 窄卡与宽卡字号：至少 10sp，保证 TalkBack 及大字号无障碍要求
                        style = roomStyle,
                        maxLines = roomMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = strike,
                    )
                }
            }
            if (showTeacher && rows >= 2 && o.course.teacher.isNotBlank()) {
                Text(
                    o.course.teacher,
                    color = fg.copy(alpha = 0.75f),
                    style = roomStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (o.rescheduled) {
                Text(
                    stringResource(R.string.rescheduled),
                    color = fg,
                    style = roomStyle,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (overlap > 1) {
                OverlapBadge(
                    overlap = overlap,
                    fg = if (isRemoved) colors.removed else fg,
                )
            }
        }
    }
}

private data class ReschedulePrefill(
    val occ: CourseOccurrence,
    val week: Int,
    val dow: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val room: String,
)

@Composable
private fun ReschedulePickBanner(
    courseName: String,
    onCancel: () -> Unit,
) {
    MintCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.reschedule_pick_banner, courseName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseSheet(
    cell: TimetableCell?,
    prefill: ReschedulePrefill?,
    sem: Semester,
    snap: Snapshot,
    cal: SemesterCalendar,
    onPickOnGrid: (CourseOccurrence) -> Unit,
    onJumpToWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var lastCell by remember { mutableStateOf(cell) }
    var lastPrefill by remember { mutableStateOf(prefill) }
    LaunchedEffect(cell, prefill) {
        if (cell != null) {
            lastCell = cell
            lastPrefill = null
        } else if (prefill != null) {
            lastPrefill = prefill
            lastCell = null
        }
    }
    val visible = cell != null || prefill != null
    val activePrefill = prefill ?: if (cell == null) lastPrefill else null
    val activeCell = cell ?: if (activePrefill == null) lastCell else null
    val activeOcc = activePrefill?.occ ?: activeCell?.occ

    var inReschedule by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(cell, prefill) {
        if (cell != null || prefill != null) {
            inReschedule = prefill != null
            submitted = false
        }
    }
    val canAct = visible && !submitted

    InWindowSheet(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        BackHandler(enabled = visible && LocalTabVisible.current.value && inReschedule && prefill == null) {
            inReschedule = false
        }
        if (activeOcc == null) return@InWindowSheet
        val ctx = LocalContext.current
        val nav = LocalNav.current
        val actions = LocalActions.current
        val o = activeOcc
        val c = o.course
        val fadeSpec = MintTheme.spec<Float>()

        fun openEdit() {
            if (!visible || submitted) return
            submitted = true
            onDismiss()
            nav.push(Route.CourseEdit(c.id))
        }

        AnimatedContent(
            targetState = inReschedule,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "course-sheet-mode",
        ) { editing ->
            if (!editing && activeCell != null) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(22.dp)
                                .clip(CircleShape)
                                .background(MintTheme.colors.fg(c.colorIndex)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "${ctx.weekdayShort(o.date.dayOfWeek.value).let { stringResource(R.string.weekday_prefix, it) }} ${cal.startTime(o.startPeriod)?.hm().orEmpty()}–${cal.endTime(o.endPeriod)?.hm().orEmpty()} · ${stringResource(R.string.periods_range, o.startPeriod, o.endPeriod)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, bottom = if (o.rescheduled) 2.dp else 8.dp),
                    )
                    if (o.rescheduled) {
                        val origDow = ctx.getString(R.string.weekday_prefix, ctx.weekdayShort(c.dayOfWeek))
                        val origHint =
                            if (c.startPeriod == c.endPeriod) {
                                stringResource(R.string.reschedule_from_hint_single, o.originalWeek, origDow, c.startPeriod)
                            } else {
                                stringResource(R.string.reschedule_from_hint, o.originalWeek, origDow, c.startPeriod, c.endPeriod)
                            }
                        Text(
                            origHint,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
                        )
                    }
                    if (o.room.isNotBlank()) {
                        val fullRoom =
                            remember(o.room) {
                                o.room
                                    .split(RoomSplitRegex)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / ")
                                    .ifBlank { o.room }
                            }
                        ValueRow(stringResource(R.string.label_room), fullRoom, onClick = ::openEdit)
                    }
                    if (c.teacher.isNotBlank()) {
                        ValueRow(stringResource(R.string.label_teacher), c.teacher, onClick = ::openEdit)
                    }
                    ValueRow(stringResource(R.string.week_label), WeekSets.format(c.weeks), onClick = ::openEdit)
                    if (c.note.isNotBlank()) {
                        Text(
                            c.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    if (activeCell.kind == CellKind.NORMAL) {
                        Spacer(Modifier.height(10.dp))
                        // 小屏放不下三个按钮就换行，主次不变：编辑 > 停课 > 调课
                        val secondaryColors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                enabled = canAct,
                                onClick = ::openEdit,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.edit)) }
                            FilledTonalButton(
                                enabled = canAct,
                                onClick = {
                                    if (!visible || submitted) return@FilledTonalButton
                                    submitted = true
                                    val origW = o.originalWeek
                                    val ex = CourseException(Ids.next(), c.id, origW, ExceptionType.CANCEL)
                                    val old = snap.exceptions.firstOrNull { it.deletedAt == null && it.courseId == c.id && it.week == origW }
                                    val cs = Edits.save(snap, if (old != null) ex.copy(id = old.id) else ex, ctx.getString(R.string.cancel_class_desc, c.name), System.currentTimeMillis())
                                    actions.commit(cs, ctx.getString(R.string.cancel_class_toast, c.name))
                                    onDismiss()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = secondaryColors,
                            ) { Text(stringResource(R.string.cancel_class_this_week)) }
                            FilledTonalButton(
                                enabled = canAct,
                                onClick = { if (visible && !submitted) inReschedule = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = secondaryColors,
                            ) { Text(stringResource(R.string.reschedule)) }
                        }
                        if (o.rescheduled) {
                            TextButton(
                                enabled = canAct,
                                onClick = {
                                    if (!visible || submitted) return@TextButton
                                    submitted = true
                                    val origW = o.originalWeek
                                    val changes =
                                        snap.exceptions
                                            .filter { it.deletedAt == null && it.courseId == c.id && it.week == origW }
                                            .map { Change.Remove(it) }
                                    if (changes.isNotEmpty()) {
                                        actions.commit(
                                            ChangeSet(
                                                Ids.next(),
                                                ctx.getString(R.string.restore_class_desc, c.name),
                                                Source.MANUAL,
                                                System.currentTimeMillis(),
                                                changes,
                                            ),
                                            ctx.getString(R.string.restore_class_toast),
                                        )
                                        onJumpToWeek(origW)
                                    }
                                    onDismiss()
                                },
                            ) { Text(stringResource(R.string.restore_class)) }
                        }
                    }
                }
            } else {
                ReschedulePanel(
                    occ = o,
                    prefill = activePrefill,
                    sem = sem,
                    snap = snap,
                    cal = cal,
                    canAct = canAct,
                    trySubmit = {
                        if (!visible || submitted) {
                            false
                        } else {
                            submitted = true
                            true
                        }
                    },
                    onBack = {
                        if (!visible || submitted) return@ReschedulePanel
                        if (activePrefill != null) {
                            submitted = true
                            onDismiss()
                        } else {
                            inReschedule = false
                        }
                    },
                    onPickOnGrid = {
                        if (!visible || submitted) return@ReschedulePanel
                        submitted = true
                        onPickOnGrid(o)
                    },
                    onCommitted = { targetWeek ->
                        onDismiss()
                        onJumpToWeek(targetWeek)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReschedulePanel(
    occ: CourseOccurrence,
    prefill: ReschedulePrefill?,
    sem: Semester,
    snap: Snapshot,
    cal: SemesterCalendar,
    canAct: Boolean,
    trySubmit: () -> Boolean,
    onBack: () -> Unit,
    onPickOnGrid: () -> Unit,
    onCommitted: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val scheme = MaterialTheme.colorScheme
    val c = occ.course
    val origW = occ.originalWeek
    val periodCount = cal.periods.size.coerceAtLeast(1)

    val seedKey = prefill ?: occ
    var newWeek by remember(seedKey) { mutableIntStateOf((prefill?.week ?: occ.week).coerceIn(1, cal.totalWeeks)) }
    var dow by remember(seedKey) { mutableIntStateOf((prefill?.dow ?: occ.date.dayOfWeek.value).coerceIn(1, 7)) }
    var start by remember(seedKey) { mutableIntStateOf((prefill?.startPeriod ?: occ.startPeriod).coerceIn(1, periodCount)) }
    var end by remember(seedKey) { mutableIntStateOf((prefill?.endPeriod ?: occ.endPeriod).coerceIn(start, periodCount)) }
    var room by remember(seedKey) { mutableStateOf(prefill?.room ?: occ.room) }
    var rangeAnchor by remember(seedKey) { mutableStateOf<Int?>(null) }

    val nextOrPrevWeek =
        when {
            origW < cal.totalWeeks -> origW + 1
            origW > 1 -> origW - 1
            else -> origW
        }
    var weekPickerOpen by remember(seedKey) { mutableStateOf(newWeek != origW && newWeek != nextOrPrevWeek) }

    val targetDate = remember(cal, newWeek, dow) { cal.dateOf(newWeek, dow) }
    val clockText =
        remember(cal, start, end) {
            val s = cal.startTime(start)?.hm()
            val e = cal.endTime(end)?.hm()
            if (s != null && e != null) "$s–$e" else ""
        }
    val periodSpanText =
        if (start == end) {
            stringResource(R.string.period_n, start)
        } else {
            stringResource(R.string.periods_range, start, end)
        }
    val periodSummary = listOfNotNull(periodSpanText, clockText.takeIf { it.isNotEmpty() }).joinToString(" · ")
    val clashes =
        remember(snap, sem, newWeek, dow, start, end, c.id, origW) {
            CourseExpander
                .occurrencesInWeek(sem, snap.courses, snap.exceptions, newWeek)
                .filter { other ->
                    other.date.dayOfWeek.value == dow &&
                        other.startPeriod <= end &&
                        other.endPeriod >= start &&
                        !(other.course.id == c.id && other.originalWeek == origW)
                }
        }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.reschedule_title, c.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPickOnGrid) {
                Icon(Icons.Rounded.GridView, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reschedule_pick_on_grid))
            }
        }

        Text(
            stringResource(R.string.reschedule_summary_line, newWeek, ctx.weekdayFull(dow), ctx.monthDay(targetDate)),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = scheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 10.dp),
        )

        // 周次：原周 / 邻周快捷选择 + 展开 5 列周次网格
        Text(
            stringResource(R.string.week_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RescheduleWeekChip(
                label = stringResource(R.string.reschedule_orig_week_chip, origW),
                selected = newWeek == origW && !weekPickerOpen,
                onClick = {
                    newWeek = origW
                    weekPickerOpen = false
                },
                modifier = Modifier.weight(1f),
            )
            if (nextOrPrevWeek != origW) {
                val adjLabel =
                    if (nextOrPrevWeek > origW) {
                        stringResource(R.string.reschedule_next_week_chip, nextOrPrevWeek)
                    } else {
                        stringResource(R.string.reschedule_prev_week_chip, nextOrPrevWeek)
                    }
                RescheduleWeekChip(
                    label = adjLabel,
                    selected = newWeek == nextOrPrevWeek && !weekPickerOpen,
                    onClick = {
                        newWeek = nextOrPrevWeek
                        weekPickerOpen = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            val customSelected = weekPickerOpen || (newWeek != origW && newWeek != nextOrPrevWeek)
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (customSelected) scheme.primaryContainer else scheme.surfaceContainerHighest)
                    .mintClickable(pressedScale = 0.96f, role = Role.Button, selected = customSelected) {
                        weekPickerOpen = !weekPickerOpen
                    }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (newWeek != origW && newWeek != nextOrPrevWeek) {
                        stringResource(R.string.week_n, newWeek)
                    } else {
                        stringResource(R.string.reschedule_other_week)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (customSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    fontWeight = if (customSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Icon(
                    if (weekPickerOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (customSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = weekPickerOpen,
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            val cols = 5
            val rows = (cal.totalWeeks + cols - 1) / cols
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until cols) {
                            val w = r * cols + col + 1
                            if (w <= cal.totalWeeks) {
                                val on = w == newWeek
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) scheme.primary else scheme.surfaceContainerHighest)
                                        .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = on) {
                                            newWeek = w
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$w",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 星期
        Text(
            stringResource(R.string.weekday_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..7) {
                val on = dow == d
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
                        .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = on) {
                            dow = d
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ctx.weekdayShort(d),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        // 节次区间（与 SlotEditorSheet 同款交互：第 1 下定起点，第 2 下拉成区间）
        Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.field_periods),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
            )
            Text(
                if (rangeAnchor != null) stringResource(R.string.course_period_range_hint) else periodSummary,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        val pCols = 6
        val pRows = (periodCount + pCols - 1) / pCols
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until pRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until pCols) {
                        val p = r * pCols + col + 1
                        if (p <= periodCount) {
                            val inRange = p in start..end
                            val isEndpoint = p == start || p == end
                            val bg =
                                when {
                                    isEndpoint -> scheme.primary
                                    inRange -> scheme.primaryContainer
                                    else -> scheme.surfaceContainerHighest
                                }
                            val fg =
                                when {
                                    isEndpoint -> scheme.onPrimary
                                    inRange -> scheme.onPrimaryContainer
                                    else -> scheme.onSurfaceVariant
                                }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .mintClickable(pressedScale = 0.96f, role = Role.Button, selected = inRange) {
                                        val anchor = rangeAnchor
                                        if (anchor == null) {
                                            rangeAnchor = p
                                            start = p
                                            end = p
                                        } else {
                                            rangeAnchor = null
                                            start = minOf(anchor, p)
                                            end = maxOf(anchor, p)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$p",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                    color = fg,
                                    fontWeight = if (inRange) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        TonalField(
            label = stringResource(R.string.label_room),
            value = room,
            onChange = { room = it },
        )

        AnimatedVisibility(
            visible = clashes.isNotEmpty(),
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            ConflictCard(
                title = stringResource(R.string.conflict_title),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                clashes.take(4).forEach { other ->
                    val clashText =
                        if (other.startPeriod == other.endPeriod) {
                            stringResource(R.string.reschedule_clash_item_single, other.course.name, other.startPeriod)
                        } else {
                            stringResource(R.string.reschedule_clash_item, other.course.name, other.startPeriod, other.endPeriod)
                        }
                    Text(clashText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            enabled = canAct,
            onClick = {
                if (!trySubmit()) return@Button
                val trimmedRoom = room.trim()
                val old = snap.exceptions.firstOrNull { it.deletedAt == null && it.courseId == c.id && it.week == origW }
                val sameAsOriginal =
                    newWeek == origW &&
                        dow == c.dayOfWeek &&
                        start == c.startPeriod &&
                        end == c.endPeriod &&
                        trimmedRoom == c.room
                if (sameAsOriginal) {
                    if (old != null) {
                        actions.commit(
                            ChangeSet(
                                Ids.next(),
                                ctx.getString(R.string.restore_class_desc, c.name),
                                Source.MANUAL,
                                System.currentTimeMillis(),
                                listOf(Change.Remove(old)),
                            ),
                            ctx.getString(R.string.restore_class_toast),
                        )
                        onCommitted(origW)
                    } else {
                        actions.toast(ctx.getString(R.string.reschedule_unchanged_toast))
                        onCommitted(origW)
                    }
                } else {
                    val ex =
                        CourseException(
                            old?.id ?: Ids.next(),
                            c.id,
                            origW,
                            ExceptionType.RESCHEDULE,
                            newWeek,
                            dow,
                            start,
                            end,
                            trimmedRoom.takeIf { it != c.room },
                        )
                    actions.commit(
                        Edits.save(snap, ex, ctx.getString(R.string.reschedule_desc, c.name), System.currentTimeMillis()),
                        ctx.getString(R.string.reschedule_toast, c.name),
                    )
                    onCommitted(newWeek)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.reschedule_confirm))
        }
    }
}

@Composable
private fun RescheduleWeekChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .mintClickable(pressedScale = 0.96f, role = Role.RadioButton, selected = selected, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    display: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepperCircleButton("−", enabled = value > min) {
            onChange(value - 1)
        }
        AnimatedContent(
            value,
            transitionSpec = {
                val sign = if (targetState > initialState) 1 else -1
                (slideInVertically(slideSpec) { sign * it / 2 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutVertically(slideSpec) { -sign * it / 2 } + fadeOut(fadeSpec))
            },
            label = "stepper",
        ) { v ->
            Text(
                display(v),
                Modifier.defaultMinSize(minWidth = 44.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
        }
        StepperCircleButton("+", enabled = value < max) {
            onChange(value + 1)
        }
    }
}

@Composable
private fun StepperCircleButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .then(
                if (enabled) {
                    Modifier.mintClickable(pressedScale = 0.92f, onClick = onClick)
                } else {
                    Modifier.alpha(0.35f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
            )
        }
    }
}
