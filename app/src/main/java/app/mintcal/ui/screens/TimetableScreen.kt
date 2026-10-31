package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.CellKind
import app.mintcal.core.agenda.TimetableCell
import app.mintcal.core.agenda.TimetableDiff
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.change.repeatInstances
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.mintPressable
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull
import app.mintcal.ui.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val TimeColW = 34.dp
private val RowH = 60.dp
private val GroupGap = 14.dp
private val HeaderH = 46.dp

/** 并排分道时每道至少要这么宽（两个 11sp 汉字 + 内边距），再窄就不并排了。 */
private val MinLaneW = 30.dp

/** 叠放簇右侧露出的那道边：宽度，以及和正面卡之间的缝。 */
private val PeekW = 6.dp
private val PeekGap = 2.dp

private fun groupBoundaries(periodCount: Int) = listOf(4, 8).filter { it < periodCount }

private fun periodTop(
    p: Int,
    periodCount: Int,
): Dp {
    val gaps = groupBoundaries(periodCount).count { p > it }
    return RowH * (p - 1) + GroupGap * gaps
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
    val now by rememberNow()
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
                stringResource(R.string.timetable_empty_title),
                hint = stringResource(R.string.timetable_empty_hint),
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
    val today = now.toLocalDate()
    val currentWeek = cal.weekOf(today)
    val startPage = (currentWeek.coerceIn(1, sem.totalWeeks)) - 1
    val pager = rememberPagerState(initialPage = startPage, pageCount = { sem.totalWeeks })
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var selected by remember { mutableStateOf<TimetableCell?>(null) }
    // 同一时段多条安排：先列出让人挑，再进各自的详情
    var slotPick by remember { mutableStateOf<List<SlotEntry>?>(null) }

    // 「在课表上预览」：把 Dry Run 变更集应用到快照副本上，课表叠加显示新增 / 修改 / 删除
    val previewCs = if (adv.previewOnTimetable) adv.changeSet else null
    val afterSnap = remember(previewCs, snap) { previewCs?.let { snap.apply(it, 0).live() } }

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
    // 再次点按「课表」Tab：跳回本周
    LaunchedEffect(nav.reselect) { pager.scrollToPage(startPage) }
    // 冲突面板「去查看」：跳到那一天所在的周并选中它
    LaunchedEffect(nav.focusDate) {
        val d = nav.focusDate ?: return@LaunchedEffect
        val w = cal.weekOf(d)
        if (w in 1..sem.totalWeeks) pager.scrollToPage(w - 1)
        nav.focusDate = null
    }

    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = pager.currentPage,
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
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = pager.currentPage + 1 != currentWeek && currentWeek in 1..sem.totalWeeks,
                enter = fadeIn(MintTheme.spec()) + expandHorizontally(MintTheme.spec()),
                exit = fadeOut(MintTheme.spec()) + shrinkHorizontally(MintTheme.spec()),
            ) {
                TextButton(onClick = { scope.launch { if (reduce) pager.scrollToPage(currentWeek - 1) else pager.animateScrollToPage(currentWeek - 1) } }) { Text(stringResource(R.string.back_to_this_week)) }
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

        // beyondViewportPageCount = 0：邻页只在滑到时才组装。WeekGrid 每页上百个格子，
        // 预组装邻页是启动后白白多烧两倍组装；滑动起点那一两帧的组装成本远小于此。
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0, key = { it }) { page ->
            val week = page + 1
            val cells = remember(snap, afterSnap, week) { TimetableDiff.week(snap, afterSnap, week) }
            val weekStart = cal.weekStart(week)
            val weekEnd = cal.weekEnd(week)
            // 待办块:blockStart ?: due 落在本周的就画上格子(子任务除外),颜色取所属清单色,和课程块同构
            val listColor = remember(snap) { snap.lists.associate { it.id to it.colorIndex } }
            // 重复的待办按周展开：同一时间块每周都画（结束日到了就停）
            val blocks =
                remember(snap, week, listColor, weekStart, weekEnd) {
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
            // clipToBounds:视差 translationX 会把相邻页的内容拖进本页边缘(右侧露出邻页节次列),按页裁掉
            Box(Modifier.clipToBounds()) {
                // 有学期但一门课都没有：空网格给一条明路，而不是让人对着节次发呆
                if (snap.courses.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.timetable_empty_title),
                        icon = Icons.Rounded.CalendarViewWeek,
                        actionLabel = stringResource(R.string.timetable_import_jxfw),
                        onAction = { nav.push(Route.Jxfw) },
                        secondaryLabel = stringResource(R.string.new_course),
                        onSecondary = { nav.push(Route.CourseEdit(null)) },
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }
                if (snap.courses.isEmpty()) return@HorizontalPager
                WeekGrid(
                    sem,
                    cal,
                    week,
                    today,
                    cells,
                    blocks,
                    settings.defaultBlockMinutes,
                    settings.showTeacher,
                    scroll,
                    modifier =
                        Modifier.graphicsLayer {
                            // 滑动视差：偏移符号与旧 calculateCurrentOffsetForPage 相反（相邻页反向平移并淡出）
                            val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                            alpha = 1f - (kotlin.math.abs(off) * 0.18f).coerceIn(0f, 0.18f)
                            translationX = off * 16.dp.toPx()
                        },
                    onClick = { selected = it },
                    onOpenTask = { nav.openTask(it) },
                    onSlotClick = { slotPick = it },
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
                        val ps = sem.periods.sortedBy { it.index }
                        val startP = ps.firstOrNull { it.index == s } ?: return@WeekGrid
                        val endP = ps.firstOrNull { it.index == e } ?: return@WeekGrid
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
                )
            }
        }
    }

    selected?.let { cell ->
        CourseSheet(cell, snap, sem, onDismiss = { selected = null })
    }
    slotPick?.let { entries ->
        SlotSheet(
            entries,
            onDismiss = { slotPick = null },
            onOpen = { e ->
                slotPick = null
                e.cell?.let { selected = it }
                e.task?.let { nav.openTask(it.id) }
            },
        )
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
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    onClick: (TimetableCell) -> Unit,
    onOpenTask: (String) -> Unit,
    /** 叠放的正面卡被点开：同一时段的全部安排，由调用方列出让用户挑。 */
    onSlotClick: (List<SlotEntry>) -> Unit,
    onMoveTask: (Task, LocalDateTime, Int) -> Unit,
    onBoxSelect: (days: IntRange, startPeriod: Int, endPeriod: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val periods = sem.periods.sortedBy { it.index }
    val n = periods.size
    val totalH = periodTop(n, n) + RowH
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxSize()) {
        // 星期头：今天垫一块 primaryContainer 并换强调色文字，一眼能扫到今天在哪一列。
        Row(Modifier.fillMaxWidth().height(HeaderH).padding(start = TimeColW)) {
            for (d in 1..7) {
                val date = cal.dateOf(week, d)
                val isToday = date == today
                Column(
                    Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(ctx.weekdayShort(d), style = MaterialTheme.typography.labelMedium, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${date.monthValue}/${date.dayOfMonth}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"), color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // 和「今日」一样：网格铺满，底栏浮在上面，末尾留一段空白让最后一节也能滚到岛上方。
        Box(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = 176.dp),
        ) {
            // 长按空白处拖动框选：横跨几天、纵跨几节 → 一次建多条（重复/冲突在编辑页里定）
            var box by remember { mutableStateOf<BoxSel?>(null) }
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(totalH)
                    .pointerInput(n) {
                        val timeColPx = TimeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = RowH.toPx()

                        fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                        fun slotOf(y: Float): Int {
                            for (p in 1..n) {
                                val top = (RowH.value * (p - 1) + GroupGap.value * groupBoundaries(n).count { p > it }) * density
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
                val colW = (maxWidth - TimeColW) / 7
                // 不画「当前时间」的线和圆点：7 天网格里一列只有 40 来 dp，
                // 那条线横穿在一整列色块上，多一根线只是噪声；今天靠星期头的色块指认。
                // 左侧节次
                periods.forEach { p ->
                    Column(
                        Modifier
                            .absoluteOffset(0.dp, periodTop(p.index, n))
                            .width(TimeColW)
                            .height(RowH)
                            .padding(top = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text("${p.index}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            p.start.hm(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 组间分隔（上午 / 下午 / 晚上之间的留白）
                groupBoundaries(n).forEach { b ->
                    Box(
                        Modifier
                            .absoluteOffset(TimeColW, periodTop(b, n) + RowH + GroupGap / 2)
                            .width(maxWidth - TimeColW)
                            .height(1.dp)
                            .background(outline),
                    )
                }
                // 冲突摆放：同一列里重叠的课程 / 待办按簇处理。
                // 并排分道是日历的主流做法，但 7 天网格一列只有 40 来 dp——分两道后每道连一个字都放不下，
                // 标题被挤成竖排碎片（旧版就是这样）。所以只有每道 ≥ MinLaneW 才并排（平板 / 横屏），
                // 再窄就叠放：课程优先当正面卡，其余只在右侧露一道边，底栏写「N 项」，
                // 点开列出这段时间的全部安排自己挑——Google 日历 / Outlook 在窄列里也是这么收敛的。
                val geoms =
                    remember(cells, blocks, n) {
                        val courseBoxes =
                            cells.map { c ->
                                val o = c.occ
                                LaneBox(
                                    o.date.dayOfWeek.value,
                                    periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp,
                                    periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + RowH - 2.dp,
                                    o.course.colorIndex,
                                    course = true,
                                )
                            }
                        val drawable =
                            blocks.mapNotNull { b ->
                                val s = b.start
                                val e = if (b.timed) s.plusMinutes(b.minutes.toLong()) else s.plusMinutes(1)
                                Agenda.blockRows(periods, s.toLocalTime(), e.toLocalTime())?.let { rows ->
                                    b to LaneBox(s.dayOfWeek.value, periodTop(rows.first, n) + 2.dp, periodTop(rows.last, n) + RowH - 2.dp, b.color)
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
                // 叠放簇右侧露出的那道边：先画，被正面卡压住一角
                slotLayout.peeks.forEach { p ->
                    Box(
                        Modifier
                            .absoluteOffset(
                                TimeColW + colW * (p.day - 1) + 1.5.dp + (colW - 3.dp) - PeekW,
                                p.top,
                            ).width(PeekW)
                            .height(p.bottom - p.top)
                            .clip(RoundedCornerShape(PeekW / 2))
                            .background(MintTheme.colors.bg(p.colorIndex).copy(alpha = 0.8f)),
                    )
                }
                // 课程块
                cells.forEachIndexed { ci, cell ->
                    val slot = slotLayout.slots[ci]
                    if (slot.hidden) return@forEachIndexed
                    val o = cell.occ
                    val top = periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp
                    val bottom = periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + RowH - 2.dp
                    val laneW = (colW - 3.dp) / slot.lanes
                    CourseBlock(
                        cell,
                        showTeacher,
                        slot.overlap,
                        Modifier
                            .absoluteOffset(TimeColW + colW * (o.date.dayOfWeek.value - 1) + 1.5.dp + laneW * slot.lane, top)
                            .width(
                                when {
                                    slot.lanes > 1 -> laneW - 2.dp
                                    slot.stacked -> laneW - PeekW - PeekGap
                                    else -> laneW
                                },
                            ).height(bottom - top),
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
                        onCommit = { ns, nm -> onMoveTask(t, ns, nm) },
                        modifier =
                            Modifier
                                .absoluteOffset(TimeColW + colW * (s.dayOfWeek.value - 1) + 1.5.dp + laneW * slot.lane, tbox.top)
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
                    val bottom = periodTop(b.last, n) + RowH - 2.dp
                    val d1 = minOf(b.day1, b.day2)
                    val d2 = maxOf(b.day1, b.day2)
                    Box(
                        Modifier
                            .graphicsLayer { alpha = boxAlpha }
                            .absoluteOffset(TimeColW + colW * (d1 - 1) + 1.5.dp, top)
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
                    peeks +=
                        LaneBox(
                            boxes[ordered.first()].day,
                            cluster.minOf { boxes[it].top },
                            cluster.maxOf { boxes[it].bottom },
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

/** 同一时段有多条安排：列出来让用户挑，而不是把彼此挤成看不动的碎片。 */
@Composable
private fun SlotSheet(
    entries: List<SlotEntry>,
    onDismiss: () -> Unit,
    onOpen: (SlotEntry) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.conflict_slot_title, entries.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // 扁平行（52dp 起步，与其它抽屉的操作行一致），不套卡片：四条卡叠起来太吵
            entries.sortedBy { it.start }.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(e) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
    onCommit: (LocalDateTime, Int) -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 与 CourseBlock 同款:圆角填色块、同字号,无勾选圈;点击进编辑
    val colors = MintTheme.colors
    val bg = colors.bg(colorIndex)
    val fg = colors.fg(colorIndex)
    val shape = RoundedCornerShape(12.dp)
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
    val edgePx = with(LocalDensity.current) { 16.dp.toPx() }

    // 拖动中的预览：起点、时长、终点
    val previewStart = if (mode == 2) start.plusMinutes(step.toLong()) else start
    val previewMin =
        when (mode) {
            2 -> dur - step
            3 -> dur + step
            else -> dur
        }
    val previewEnd = previewStart.plusMinutes(previewMin.toLong())

    // 拖动描边从透明渐显，抓手缩放淡入：进入拖动态不再瞬切
    val dragBorder by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, MintTheme.spec(), label = "drag-border")

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
        } else {
            Modifier.graphicsLayer { translationY = if (mode == 1) dy else 0f }
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
                        mode =
                            when {
                                off.y < edgePx -> 2
                                off.y > hPx - edgePx -> 3
                                else -> 1
                            }
                        step = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val next = snapTo((step + amount.y / pxPerMinute))
                        if (next != step) {
                            step = next
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        step = 0
                        mode = 0
                    },
                    onDragCancel = {
                        step = 0
                        mode = 0
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (task.done) bg.copy(alpha = 0.5f) else bg)
                .border(2.dp, dragBorder, shape)
                .padding(horizontal = 5.dp, vertical = 5.dp),
        ) {
            // 时间数字统一 tnum；标题走 labelSmall token 局部 copy
            val titleStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp)
            val timeStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp, fontFeatureSettings = "tnum")
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
                Text(
                    stringResource(R.string.conflict_count, overlap),
                    color = colors.removed,
                    style = timeStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
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
private fun CourseBlock(
    cell: TimetableCell,
    showTeacher: Boolean,
    /** 同时段块数；>1 时卡片底部写「N 项」，点开由这张正面卡列出全部。 */
    overlap: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val o = cell.occ
    val colors = MintTheme.colors
    val bg = colors.bg(o.course.colorIndex)
    val fg = colors.fg(o.course.colorIndex)
    val rows = (o.endPeriod - o.startPeriod + 1).coerceAtLeast(1)
    // 预览开关联动：新增/修改/删除的描边、底色、透明度从常态平滑过渡，不是一帧硬切
    val kindBorder =
        when (cell.kind) {
            CellKind.ADDED -> colors.added
            CellKind.MODIFIED -> colors.modified
            CellKind.REMOVED -> colors.removed
            CellKind.NORMAL -> Color.Transparent
        }
    val borderColor by animateColorAsState(kindBorder, MintTheme.spec(), label = "cell-kind-border")
    val cellBg by animateColorAsState(if (cell.kind == CellKind.REMOVED) colors.conflictBg else bg, MintTheme.spec(), label = "cell-kind-bg")
    val cellAlpha by animateFloatAsState(if (cell.kind == CellKind.REMOVED) 0.6f else 1f, MintTheme.spec(), label = "cell-kind-alpha")
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier
            .mintPressable(pressedScale = 0.97f) { onClick() }
            .alpha(cellAlpha)
            .clip(shape)
            .background(cellBg)
            .border(2.dp, borderColor, shape),
    ) {
        // 一列就 40dp 出头：教室 / 老师照样要显示，靠换行和省略号交代；
        // 把信息藏起来比截断更糟。宽卡片（平板 / 横屏）才加「@」前缀区分教室，窄列里多这一个字符就没地方了。
        val wide = this.maxWidth >= 64.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val strike = if (cell.kind == CellKind.REMOVED) TextDecoration.LineThrough else null
            // 字号统一走 typography token 局部 copy；时间/教室数字用 tnum
            Text(
                o.course.name,
                color = if (cell.kind == CellKind.REMOVED) colors.removed else fg,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
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
            if (o.room.isNotBlank()) {
                Text(
                    if (wide) "@" + o.room else o.room,
                    modifier = Modifier.weight(1f, fill = false),
                    color = (if (cell.kind == CellKind.REMOVED) colors.removed else fg).copy(alpha = 0.85f),
                    // 窄列只有 30 来 dp：9sp 的「教3-204」放不下会折成「教 / 3-204」，窄卡降到 8sp 一行放完
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (wide) 9.sp else 8.sp,
                            lineHeight = 11.sp,
                            letterSpacing = 0.sp,
                        ),
                    maxLines = if (wide && rows >= 2) 2 else 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = strike,
                )
            }
            if (showTeacher && rows >= 2 && o.course.teacher.isNotBlank()) {
                Text(
                    o.course.teacher,
                    color = fg.copy(alpha = 0.75f),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (wide) 9.sp else 8.sp,
                            lineHeight = 11.sp,
                            letterSpacing = 0.sp,
                        ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (o.rescheduled) {
                Text(
                    stringResource(R.string.rescheduled),
                    color = fg,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (overlap > 1) {
                Text(
                    stringResource(R.string.conflict_count, overlap),
                    color = colors.removed,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseSheet(
    cell: TimetableCell,
    snap: Snapshot,
    sem: Semester,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val o = cell.occ
    val c = o.course
    val cal = remember(sem) { SemesterCalendar(sem) }
    var reschedule by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${ctx.weekdayShort(o.date.dayOfWeek.value).let { stringResource(R.string.weekday_prefix, it) }} ${cal.startTime(o.startPeriod)?.hm().orEmpty()}–${cal.endTime(o.endPeriod)?.hm().orEmpty()} · ${stringResource(R.string.periods_range, o.startPeriod, o.endPeriod)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (o.room.isNotBlank()) Text(stringResource(R.string.label_room_value, o.room), style = MaterialTheme.typography.bodyMedium)
            if (c.teacher.isNotBlank()) Text(stringResource(R.string.label_teacher_value, c.teacher), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.label_weeks_value, WeekSets.format(c.weeks)), style = MaterialTheme.typography.bodyMedium)
            if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (cell.kind == CellKind.NORMAL) {
                // 小屏放不下三个按钮就换行，主次不变：编辑 > 停课 > 调课
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onDismiss()
                        nav.push(Route.CourseEdit(c.id))
                    }) { Text(stringResource(R.string.edit)) }
                    FilledTonalButton(onClick = {
                        val ex = CourseException(Ids.next(), c.id, origWeek(snap, c, o.date, cal), ExceptionType.CANCEL)
                        val old = snap.exceptions.firstOrNull { it.courseId == c.id && it.week == ex.week }
                        val cs = Edits.save(snap, if (old != null) ex.copy(id = old.id) else ex, ctx.getString(R.string.cancel_class_desc, c.name), System.currentTimeMillis())
                        actions.commit(cs, ctx.getString(R.string.cancel_class_toast, c.name))
                        onDismiss()
                    }) { Text(stringResource(R.string.cancel_class_this_week)) }
                    OutlinedButton(onClick = { reschedule = true }) { Text(stringResource(R.string.reschedule)) }
                }
                if (o.rescheduled) {
                    TextButton(onClick = {
                        val w = origWeek(snap, c, o.date, cal)
                        snap.exceptions.filter { it.courseId == c.id && it.week == w }.forEach { ex ->
                            actions.commit(Edits.delete(snap, ex, ctx.getString(R.string.restore_class_desc, c.name), System.currentTimeMillis()), ctx.getString(R.string.restore_class_toast))
                        }
                        onDismiss()
                    }) { Text(stringResource(R.string.restore_class)) }
                }
            } else {
                Text(stringResource(R.string.preview_cell_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (reschedule) {
        RescheduleDialog(c, o.date, cal, snap, onDismiss = {
            reschedule = false
            onDismiss()
        })
    }
}

/** 课程的「原始周次」：调课后课程出现在别的周，需要找回对应的原周。 */
private fun origWeek(
    snap: Snapshot,
    c: Course,
    date: LocalDate,
    cal: SemesterCalendar,
): Int {
    val shown = cal.weekOf(date)
    if (shown in c.weeks) return shown
    return snap.exceptions.firstOrNull { it.courseId == c.id && it.type == ExceptionType.RESCHEDULE && (it.newWeek ?: it.week) == shown }?.week ?: shown
}

@Composable
private fun RescheduleDialog(
    c: Course,
    date: LocalDate,
    cal: SemesterCalendar,
    snap: Snapshot,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val week = origWeek(snap, c, date, cal)
    var newWeek by remember { mutableIntStateOf(cal.weekOf(date)) }
    var dow by remember { mutableIntStateOf(date.dayOfWeek.value) }
    var start by remember { mutableIntStateOf(c.startPeriod) }
    var room by remember { mutableStateOf(c.room) }
    val len = c.endPeriod - c.startPeriod
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reschedule_title, c.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(stringResource(R.string.week_label), newWeek, 1, cal.totalWeeks) { newWeek = it }
                Stepper(stringResource(R.string.weekday_label), dow, 1, 7, display = { ctx.weekdayFull(it) }) { dow = it }
                Stepper(stringResource(R.string.start_period), start, 1, (cal.periods.size - len).coerceAtLeast(1)) { start = it }
                androidx.compose.material3.OutlinedTextField(room, { room = it }, label = { Text(stringResource(R.string.label_room)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val old = snap.exceptions.firstOrNull { it.courseId == c.id && it.week == week }
                val ex = CourseException(old?.id ?: Ids.next(), c.id, week, ExceptionType.RESCHEDULE, newWeek, dow, start, start + len, room.takeIf { it != c.room })
                actions.commit(Edits.save(snap, ex, ctx.getString(R.string.reschedule_desc, c.name), System.currentTimeMillis()), ctx.getString(R.string.reschedule_toast, c.name))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
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
    val haptics = LocalHapticFeedback.current
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onChange(value - 1)
        }, enabled = value > min) { Text("−") }
        AnimatedContent(
            value,
            transitionSpec = {
                val sign = if (targetState > initialState) 1 else -1
                (slideInVertically(slideSpec) { sign * it / 2 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutVertically(slideSpec) { -sign * it / 2 } + fadeOut(fadeSpec))
            },
            label = "stepper",
        ) { v ->
            Text(display(v), Modifier.defaultMinSize(minWidth = 48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onChange(value + 1)
        }, enabled = value < max) { Text("+") }
    }
}
