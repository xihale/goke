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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.semantics.stateDescription
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
import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.CourseNotes
import app.goke.core.model.ExceptionType
import app.goke.core.model.PeriodTime
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.time.CourseProgress
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.TimePhase
import app.goke.core.time.WeekSets
import app.goke.core.todo.TaskLogic
import app.goke.core.util.Ids
import app.goke.data.Settings
import app.goke.share.Sharing
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.LocalTabVisible
import app.goke.ui.Route
import app.goke.ui.Tab
import app.goke.ui.components.ConflictCard
import app.goke.ui.components.CourseNoteBox
import app.goke.ui.components.CourseProgressAxis
import app.goke.ui.components.EmptyState
import app.goke.ui.components.FieldText
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintCheck
import app.goke.ui.components.NavListInset
import app.goke.ui.components.NavRow
import app.goke.ui.components.Tag
import app.goke.ui.components.TaskRow
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.courseMutedBg
import app.goke.ui.components.courseProgressBackground
import app.goke.ui.components.mintClickable
import app.goke.ui.components.mintPressable
import app.goke.ui.components.parcelCode
import app.goke.ui.components.rememberCourseProgressFg
import app.goke.ui.dueLabel
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.icons.CalendarViewWeek
import app.goke.ui.icons.Cloud
import app.goke.ui.icons.ExpandMore
import app.goke.ui.icons.GridView
import app.goke.ui.icons.Image
import app.goke.ui.icons.Inventory2
import app.goke.ui.icons.IosShare
import app.goke.ui.icons.Link
import app.goke.ui.icons.StickyNote2
import app.goke.ui.icons.Visibility
import app.goke.ui.icons.VisibilityOff
import app.goke.ui.liveSnapshot
import app.goke.ui.monthDay
import app.goke.ui.monthDayWeekday
import app.goke.ui.reduceMotion
import app.goke.ui.rememberNowGated
import app.goke.ui.settingsState
import app.goke.ui.theme.MintColors
import app.goke.ui.theme.MintTheme
import app.goke.ui.weekdayFull
import app.goke.ui.weekdayShort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val BaseTimeColW = 34.dp
private val BaseRowH = 60.dp
private val GroupGap = 14.dp
private val HeaderH = 46.dp

/** 并排分道时每道至少要这么宽（两个 11sp 汉字 + 内边距），再窄就不并排了。 */
private val MinLaneW = 30.dp

/** 叠放时最多在背面露出两层卡片（加正面共 3 层）。 */
private const val MAX_STACK_BACK_LAYERS = 2

/** 叠放正面卡稍微左移，保持完整列宽（零宽度牺牲）同时让背面层自然向右下方错位。 */
private val StackFrontShiftX = 1.dp

/** 计算叠放背面第 [depth] 层相对正面卡左上角的 3D 偏移 `(dx, dy)`。 */
internal fun stackPeekOffset(
    depth: Int,
    maxDepth: Int,
): Pair<Dp, Dp> =
    if (maxDepth <= 1) {
        2.5.dp to 2.25.dp
    } else {
        when {
            depth <= 1 -> 1.5.dp to 1.75.dp
            else -> 2.75.dp to 3.25.dp
        }
    }

private val BlockCornerRadius = 12.dp
private val BlockOuterInset = 1.5.dp
private val NowBarOuterInset = 3.dp
private val HeaderDayShape = RoundedCornerShape(14.dp)
private val CourseBlockShape = RoundedCornerShape(BlockCornerRadius)
private val StickyTabShape = RoundedCornerShape(6.dp)
private val TaskBlockShape = RoundedCornerShape(BlockCornerRadius)
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

/**
 * 将当日时刻 [time] 映射到课表纵轴位置（单位 Dp）：
 * 节次内线性插值；课间与午/晚休大间隙按间隔线性插值；早于第一节开始或晚于最后一节结束返回 `null`。
 */
internal fun calcTimetableNowY(
    periods: List<PeriodTime>,
    time: LocalTime,
    rowH: Dp = BaseRowH,
): Dp? {
    val n = periods.size
    if (n == 0) return null
    val ordered =
        if (periods.zipWithNext().all { (a, b) -> a.index <= b.index }) {
            periods
        } else {
            periods.sortedBy { it.index }
        }
    val first = ordered.first()
    val last = ordered.last()
    if (time.isBefore(first.start) || time.isAfter(last.end)) return null
    for (i in ordered.indices) {
        val p = ordered[i]
        val rowTop = calcPeriodTop(p.index, n, rowH)
        if (!time.isBefore(p.start) && !time.isAfter(p.end)) {
            val totalSec = ChronoUnit.SECONDS.between(p.start, p.end).coerceAtLeast(1L)
            val elapsedSec = ChronoUnit.SECONDS.between(p.start, time).coerceIn(0L, totalSec)
            return rowTop + rowH * (elapsedSec.toFloat() / totalSec.toFloat())
        }
        if (i + 1 < n) {
            val next = ordered[i + 1]
            if (time.isAfter(p.end) && time.isBefore(next.start)) {
                val gapStart = rowTop + rowH
                val gapEnd = calcPeriodTop(next.index, n, rowH)
                if (gapEnd <= gapStart) return gapStart
                val gapTotal = ChronoUnit.SECONDS.between(p.end, next.start).coerceAtLeast(1L)
                val gapElapsed = ChronoUnit.SECONDS.between(p.end, time).coerceIn(0L, gapTotal)
                return gapStart + (gapEnd - gapStart) * (gapElapsed.toFloat() / gapTotal.toFloat())
            }
        }
    }
    return null
}

internal data class NowBarBlock(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float,
    val stackedFront: Boolean = false,
)

/**
 * 计算今日当前时间横条在纵坐标 [y] 处的起止横坐标：
 * 空白区域维持列内默认起止 [defaultStartX]..[defaultEndX]；
 * 若横条落入卡片上下圆角带内，则沿内缩 [edgeInset] 的同心圆角收窄端点，避免横条穿出圆角。
 */
internal fun calcTimetableNowBarSpan(
    defaultStartX: Float,
    defaultEndX: Float,
    y: Float,
    edgeInset: Float,
    blocks: List<NowBarBlock>,
): Pair<Float, Float> {
    var leftBound: Float? = null
    var rightBound: Float? = null
    for (b in blocks) {
        if (y < b.top || y > b.bottom) continue
        val w = b.right - b.left
        val h = b.bottom - b.top
        if (w <= 0f || h <= 0f) continue
        val r = minOf(b.cornerRadius, w * 0.5f, h * 0.5f)
        val inset = edgeInset.coerceAtMost(r)
        val rInner = (r - inset).coerceAtLeast(0f)
        val dy =
            when {
                y < b.top + r -> b.top + r - y
                y > b.bottom - r -> y - (b.bottom - r)
                else -> 0f
            }
        val dxInner = if (dy < rInner) sqrt(rInner * rInner - dy * dy) else 0f
        val innerLeft = b.left + r - dxInner
        val innerRight = b.right - r + dxInner
        if (b.left <= defaultStartX) {
            leftBound = if (leftBound == null) innerLeft else minOf(leftBound, innerLeft)
        }
        if (b.right >= defaultEndX || b.stackedFront) {
            rightBound = if (rightBound == null) innerRight else maxOf(rightBound, innerRight)
        }
    }
    val startX = (leftBound?.let { maxOf(defaultStartX, it) } ?: defaultStartX).coerceAtMost(defaultEndX)
    val endX = (rightBound?.let { minOf(defaultEndX, it) } ?: defaultEndX).coerceIn(startX, defaultEndX)
    return startX to endX
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
    // now 走门控派生态：隐藏 Tab 不养 30 秒后台协程；仅本周网格消费 now，其余周不随 30 秒心跳重组
    val now = rememberNowGated()
    val today = remember(now) { now.toLocalDate() }
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
    var showExportSheet by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
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
    val sheetOpen = selected != null || slotPick != null || parcelSheet != null || reschedulePrefill != null || showExportSheet || showWeekPicker
    DisposableEffect(sheetOpen) {
        if (sheetOpen) nav.composerOpen = true
        onDispose { if (sheetOpen) nav.composerOpen = false }
    }

    // 缓存每周的课表差异与待办块，避免翻周与预加载时重复全学期计算
    val weekDiffCache = remember(snap, afterSnap) { mutableMapOf<Int, List<TimetableCell>>() }
    val listColor = remember(snap) { snap.lists.associate { it.id to it.colorIndex } }
    val weekBlocksCache = remember(snap, listColor, sem) { mutableMapOf<Int, List<BlockRow>>() }
    val courseTodoCounts =
        remember(snap.tasks, snap.courses, snap.exceptions, sem, now) {
            TaskLogic.occurrenceCounts(snap.tasks, sem, snap.courses, snap.exceptions, now)
        }

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
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .mintClickable(pressedScale = 0.97f, role = Role.Button) { showWeekPicker = true }
                                    .padding(end = 4.dp, top = 1.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    ctx.getString(R.string.week_n, p + 1),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    contentDescription = stringResource(R.string.timetable_pick_week),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
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
                IconButton(onClick = { showExportSheet = true }) { Icon(Icons.Rounded.IosShare, stringResource(R.string.timetable_export_sheet_title)) }
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
                                        .map { d ->
                                            val colorIdx = if (t.listId == null && t.isExam) 2 else listColor[t.listId] ?: 1
                                            BlockRow(t, colorIdx, LocalDateTime.of(d, at.toLocalTime()), t.blockStart != null)
                                        }
                                }
                        }
                    }
                // clipToBounds:视差 translationX 会把相邻页的内容拖进本页边缘(右侧露出邻页节次列),按页裁掉
                val pickRescheduleTarget: (CourseOccurrence, Int, Int, Int?) -> Unit = { picking, dow, startPeriod, endPeriod ->
                    val maxP = periods.size.coerceAtLeast(1)
                    val sp = startPeriod.coerceIn(1, maxP)
                    val ep =
                        if (endPeriod != null) {
                            endPeriod.coerceIn(sp, maxP)
                        } else {
                            val span = (picking.endPeriod - picking.startPeriod).coerceAtLeast(0)
                            (sp + span).coerceAtMost(maxP)
                        }
                    reschedulePrefill = ReschedulePrefill(picking, week, dow.coerceIn(1, 7), sp, ep, picking.room)
                    pickingRescheduleFor = null
                }
                Box(Modifier.clipToBounds()) {
                    WeekGrid(
                        sem,
                        cal,
                        week,
                        currentWeek,
                        today,
                        if (week == currentWeek) now else null,
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
                        reschedulePicking = pickingRescheduleFor != null,
                        courseTodoCounts = courseTodoCounts,
                        onClick = { cell ->
                            val picking = pickingRescheduleFor
                            if (picking != null) {
                                pickRescheduleTarget(picking, cell.occ.date.dayOfWeek.value, cell.occ.startPeriod, null)
                            } else {
                                selected = cell
                            }
                        },
                        onOpenTask = { nav.openTask(it) },
                        onSlotClick = { entries ->
                            val picking = pickingRescheduleFor
                            val firstCell = entries.firstOrNull()?.cell
                            if (picking != null && firstCell != null) {
                                pickRescheduleTarget(picking, firstCell.occ.date.dayOfWeek.value, firstCell.occ.startPeriod, null)
                            } else {
                                slotPick = entries
                            }
                        },
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
                            val picking = pickingRescheduleFor
                            if (picking != null) {
                                pickRescheduleTarget(picking, days.last, s, e)
                            } else {
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
                            }
                        },
                        onEmptySlotClick = { dow, period ->
                            val picking = pickingRescheduleFor
                            if (picking != null) {
                                pickRescheduleTarget(picking, dow, period, null)
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
            now = now,
            today = today,
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
        TimetableExportSheet(
            visible = showExportSheet,
            onDismiss = { showExportSheet = false },
            snap = snap,
            st = settings,
            week = pager.currentPage + 1,
            onQuickShare = {
                showExportSheet = false
                nav.push(Route.TimetableShare)
            },
        )
        WeekPickerSheet(
            visible = showWeekPicker,
            sem = sem,
            cal = cal,
            selectedWeek = pager.currentPage + 1,
            currentWeek = currentWeek,
            nonEmptyWeeks = {
                (1..sem.totalWeeks).filterTo(HashSet()) { w ->
                    weekDiffCache.getOrPut(w) { TimetableDiff.week(snap, afterSnap, w) }.isNotEmpty()
                }
            },
            onSelectWeek = { w ->
                showWeekPicker = false
                if (w in 1..sem.totalWeeks && w != pager.currentPage + 1) {
                    scope.launch {
                        if (reduce) pager.scrollToPage(w - 1) else pager.animateScrollToPage(w - 1)
                    }
                }
            },
            onDismiss = { showWeekPicker = false },
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
    currentWeek: Int,
    today: LocalDate,
    now: LocalDateTime?,
    cells: List<TimetableCell>,
    blocks: List<BlockRow>,
    defaultBlockMinutes: Int,
    showTeacher: Boolean,
    gridWidth: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    periods: List<PeriodTime> = remember(sem) { sem.periods.sortedBy { it.index } },
    reschedulePicking: Boolean = false,
    courseTodoCounts: Map<Pair<String, Int>, Int> = emptyMap(),
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
    val gridDensity = LocalDensity.current
    val fontScale = gridDensity.fontScale.coerceIn(1f, 1.35f)
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
    val gridHaptics = LocalHapticFeedback.current
    val currentReschedulePicking by rememberUpdatedState(reschedulePicking)
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
                                val kindName = ctx.getString(if (o.course.isExam) R.string.kind_exam else R.string.entity_course)
                                SlotEntry(
                                    o.course.name,
                                    "${o.start.hm()}–${o.end.hm()} · $kindName",
                                    o.course.colorIndex,
                                    o.start,
                                    cells[i],
                                    null,
                                )
                            } else {
                                val b = geoms.second[i - courseCount].first
                                val kindName = ctx.getString(if (b.task.isExam) R.string.kind_exam else R.string.entity_task)
                                SlotEntry(
                                    b.task.title,
                                    "${b.start.hm()}–${b.start.plusMinutes(b.minutes.toLong()).hm()} · $kindName",
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
        val peekPath = remember { Path() }
        val peeksByDepth = remember(slotLayout) { slotLayout.peeks.sortedByDescending { it.depth } }
        val currentSlotLayout by rememberUpdatedState(slotLayout)
        val currentSlotEntries by rememberUpdatedState(slotEntries)
        val currentWeekRef by rememberUpdatedState(week)
        val currentCurrentWeek by rememberUpdatedState(currentWeek)
        val currentNow by rememberUpdatedState(now)
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
                        // 组间分隔线 + 冲突叠放簇 3D 背面层及投影一次画在网格底板上，省掉额外 Box 布局节点
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
                        if (peeksByDepth.isNotEmpty()) {
                            val peekCorner = CornerRadius(BlockCornerRadius.toPx())
                            val blockInsetPx = BlockOuterInset.toPx()
                            val shiftXPx = StackFrontShiftX.toPx()
                            val cardW = (colW - BlockOuterInset * 2).toPx()
                            val scrimColor = scheme.scrim
                            val courseCount = cells.size
                            val wk = currentWeekRef
                            val curWk = currentCurrentWeek
                            val nowDt = currentNow

                            fun isPeekFinished(itemIdx: Int): Boolean =
                                if (itemIdx < courseCount) {
                                    val o = cells[itemIdx].occ
                                    wk < curWk || (wk == curWk && nowDt != null && CourseProgress.phase(o.start, o.end, nowDt) == TimePhase.FINISHED)
                                } else {
                                    val b = geoms.second[itemIdx - courseCount].first
                                    val s = b.start
                                    val e = s.plusMinutes(b.minutes.toLong())
                                    b.task.done || wk < curWk || (wk == curWk && nowDt != null && CourseProgress.phase(s, e, nowDt) == TimePhase.FINISHED)
                                }

                            peeksByDepth.forEach { p ->
                                val colLeftPx = (timeColW + colW * (p.day - 1)).toPx()
                                val frontLeft = colLeftPx + blockInsetPx - shiftXPx
                                val frontTop = p.frontTop.toPx()
                                val frontH = (p.frontBottom - p.frontTop).toPx().coerceAtLeast(0f)
                                val (dxDp, dyDp) = stackPeekOffset(p.depth, p.clusterMaxDepth)
                                val peekLeft = frontLeft + dxDp.toPx()
                                val peekTop = p.top.toPx() + dyDp.toPx()
                                val peekBottom = (p.bottom.toPx() + dyDp.toPx()).coerceAtMost(size.height)
                                val peekH = (peekBottom - peekTop).coerceAtLeast(0f)
                                if (peekH <= 0f) return@forEach

                                val rawBg = mintColors.bg(p.colorIndex)
                                val finished = isPeekFinished(p.itemIndex)
                                val baseBg = if (finished) courseMutedBg(rawBg, scheme) else rawBg
                                val depthBg =
                                    if (mintColors.dark) {
                                        lerp(baseBg, scheme.surface, (0.16f * p.depth).coerceAtMost(0.36f))
                                    } else {
                                        lerp(
                                            baseBg,
                                            if (finished) scheme.onSurfaceVariant else mintColors.fg(p.colorIndex),
                                            (0.09f * p.depth).coerceAtMost(0.20f),
                                        )
                                    }
                                drawRoundRect(
                                    color = depthBg,
                                    topLeft = Offset(peekLeft, peekTop),
                                    size = Size(cardW, peekH),
                                    cornerRadius = peekCorner,
                                )

                                // 投影裁剪在背面卡轮廓内，绝不外溢到空白网格
                                peekPath.reset()
                                peekPath.addRoundRect(
                                    RoundRect(
                                        left = peekLeft,
                                        top = peekTop,
                                        right = peekLeft + cardW,
                                        bottom = peekBottom,
                                        cornerRadius = peekCorner,
                                    ),
                                )
                                clipPath(peekPath) {
                                    if (p.depth >= 2) {
                                        val midAlpha = if (mintColors.dark) 0.20f else 0.11f
                                        val (midDxDp, midDyDp) = stackPeekOffset(1, p.clusterMaxDepth)
                                        val midShift = 0.75.dp.toPx()
                                        val midLeft = frontLeft + midDxDp.toPx()
                                        val midDyPx = midDyDp.toPx()
                                        currentSlotLayout.peeks.forEach { mid ->
                                            if (mid.frontIndex == p.frontIndex && mid.depth == 1) {
                                                val midTop = mid.top.toPx() + midDyPx
                                                val midBottom = (mid.bottom.toPx() + midDyPx).coerceAtMost(size.height)
                                                val midH = (midBottom - midTop).coerceAtLeast(0f)
                                                if (midH > 0f) {
                                                    drawRoundRect(
                                                        color = scrimColor.copy(alpha = midAlpha),
                                                        topLeft = Offset(midLeft + midShift, midTop + midShift),
                                                        size = Size(cardW, midH),
                                                        cornerRadius = peekCorner,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (frontH > 0f) {
                                        drawRoundRect(
                                            color = scrimColor.copy(alpha = if (mintColors.dark) 0.14f else 0.07f),
                                            topLeft = Offset(frontLeft + 1.5.dp.toPx(), frontTop + 1.75.dp.toPx()),
                                            size = Size(cardW, frontH),
                                            cornerRadius = peekCorner,
                                        )
                                        drawRoundRect(
                                            color = scrimColor.copy(alpha = if (mintColors.dark) 0.28f else 0.14f),
                                            topLeft = Offset(frontLeft + 0.6.dp.toPx(), frontTop + 0.8.dp.toPx()),
                                            size = Size(cardW, frontH),
                                            cornerRadius = peekCorner,
                                        )
                                    }
                                }
                            }
                        }
                    }.pointerInput(n, timeColW, rowH, cells, gridBlocks) {
                        val timeColPx = timeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = rowH.toPx()
                        val blockInsetPx = BlockOuterInset.toPx()
                        val shiftXPx = StackFrontShiftX.toPx()
                        val cardWPx = colPx - blockInsetPx * 2f

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
                                    val hitPeek =
                                        currentSlotLayout.peeks
                                            .filter { pk ->
                                                val frontLeft = timeColPx + colPx * (pk.day - 1) + blockInsetPx - shiftXPx
                                                val (dxDp, dyDp) = stackPeekOffset(pk.depth, pk.clusterMaxDepth)
                                                val peekLeft = frontLeft + dxDp.toPx()
                                                val peekTop = pk.top.toPx() + dyDp.toPx()
                                                val peekBottom = (pk.bottom.toPx() + dyDp.toPx()).coerceAtMost(size.height.toFloat())
                                                off.x in peekLeft..(peekLeft + cardWPx) && off.y in peekTop..peekBottom
                                            }.minByOrNull { it.depth }
                                    if (hitPeek != null) {
                                        if (currentReschedulePicking) {
                                            onEmptySlotClick(d, p)
                                        } else {
                                            onSlotClick(currentSlotEntries[hitPeek.frontIndex].orEmpty())
                                        }
                                        return@detectTapGestures
                                    }
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
                                    gridHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    box = BoxSel(anchorDay, anchorDay, anchorSlot, anchorSlot)
                                }
                            },
                            onDrag = { change, _ ->
                                val b = box ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                val d = colOf(change.position.x)
                                val e = slotOf(change.position.y).coerceIn(1, n)
                                val first = minOf(anchorSlot, e)
                                val last = maxOf(anchorSlot, e)
                                box =
                                    if (currentReschedulePicking) {
                                        b.copy(day1 = d, day2 = d, first = first, last = last)
                                    } else {
                                        b.copy(day2 = d, first = first, last = last)
                                    }
                            },
                            onDragEnd = {
                                box?.let { b -> onBoxSelect(minOf(b.day1, b.day2)..maxOf(b.day1, b.day2), b.first, b.last) }
                                box = null
                            },
                            onDragCancel = { box = null },
                        )
                    },
            ) {
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
                val todayColIndex =
                    remember(week, currentWeek, today, cal) {
                        if (week == currentWeek) (1..7).firstOrNull { cal.dateOf(week, it) == today } else null
                    }
                val nowTimeY =
                    remember(now, periods, rowH, todayColIndex) {
                        if (todayColIndex != null && now != null) {
                            calcTimetableNowY(periods, now.toLocalTime(), rowH)
                        } else {
                            null
                        }
                    }
                val todayNowBlocks =
                    remember(geoms, slotLayout, todayColIndex, timeColW, colW, gridDensity, totalH) {
                        if (todayColIndex == null) {
                            emptyList()
                        } else {
                            with(gridDensity) {
                                val colLeftDp = timeColW + colW * (todayColIndex - 1)
                                val cardW = colW - BlockOuterInset * 2
                                val cornerPx = BlockCornerRadius.toPx()
                                buildList {
                                    val allBoxes = geoms.first + geoms.second.map { it.second }
                                    allBoxes.forEachIndexed { idx, box ->
                                        val slot = slotLayout.slots[idx]
                                        if (box.day == todayColIndex && !slot.hidden) {
                                            val (offsetX, blockW) = slotBlockSpan(colW, slot)
                                            val left = colLeftDp + offsetX
                                            add(
                                                NowBarBlock(
                                                    left = left.toPx(),
                                                    top = box.top.toPx(),
                                                    right = (left + blockW).toPx(),
                                                    bottom = box.bottom.toPx(),
                                                    cornerRadius = cornerPx,
                                                    stackedFront = slot.stacked,
                                                ),
                                            )
                                        }
                                    }
                                    slotLayout.peeks.forEach { p ->
                                        if (p.day == todayColIndex) {
                                            val (dx, dy) = stackPeekOffset(p.depth, p.clusterMaxDepth)
                                            val left = colLeftDp + BlockOuterInset - StackFrontShiftX + dx
                                            val top = p.top + dy
                                            val bottom = (p.bottom + dy).coerceAtMost(totalH)
                                            if (bottom > top) {
                                                add(
                                                    NowBarBlock(
                                                        left = left.toPx(),
                                                        top = top.toPx(),
                                                        right = (left + cardW).toPx(),
                                                        bottom = bottom.toPx(),
                                                        cornerRadius = cornerPx,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                // 课程块
                cells.forEachIndexed { ci, cell ->
                    val slot = slotLayout.slots[ci]
                    if (slot.hidden) return@forEachIndexed
                    val o = cell.occ
                    val top = periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp
                    val bottom = periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + rowH - 2.dp
                    val (offsetX, blockW) = slotBlockSpan(colW, slot)
                    val isWide = blockW >= 64.dp
                    val phase =
                        when {
                            week < currentWeek -> TimePhase.FINISHED
                            week > currentWeek -> TimePhase.UPCOMING
                            now != null -> CourseProgress.phase(o.start, o.end, now)
                            else -> TimePhase.UPCOMING
                        }
                    val span = (bottom.value - top.value).takeIf { it > 0f }
                    val progress =
                        when (phase) {
                            TimePhase.UPCOMING -> {
                                0f
                            }

                            TimePhase.FINISHED -> {
                                1f
                            }

                            TimePhase.ONGOING -> {
                                if (week == currentWeek && o.date == today && nowTimeY != null && span != null) {
                                    ((nowTimeY.value - top.value) / span).coerceIn(0f, 1f)
                                } else {
                                    now?.let { CourseProgress.fraction(o.start, o.end, it) } ?: 0f
                                }
                            }
                        }
                    val todoCount =
                        if (cell.kind == CellKind.REMOVED) {
                            0
                        } else {
                            courseTodoCounts[o.course.id to o.originalWeek] ?: 0
                        }
                    CourseBlock(
                        cell,
                        showTeacher,
                        slot.overlap,
                        slot.stacked,
                        todoCount,
                        isWide,
                        phase,
                        progress,
                        mintColors,
                        courseTitleStyle,
                        if (isWide) courseRoomStyleWide else courseRoomStyleNarrow,
                        Modifier
                            .absoluteOffset(timeColW + colW * (o.date.dayOfWeek.value - 1) + offsetX, top)
                            .width(blockW)
                            .height(bottom - top),
                        onClick = { if (reschedulePicking || slot.overlap <= 1) onClick(cell) else onSlotClick(slotEntries[ci].orEmpty()) },
                    )
                }
                // 待办时间块:排入日历的按时长,普通待办画在截止时刻所在的节次;与课程或其它待办重叠时按上面的规则并排或叠放
                geoms.second.forEachIndexed { di, entry ->
                    val (b, tbox) = entry
                    val slot = slotLayout.slots[cells.size + di]
                    if (slot.hidden) return@forEachIndexed
                    val t = b.task
                    val s = b.start
                    val (offsetX, blockW) = slotBlockSpan(colW, slot)
                    val minutes = b.minutes
                    val blockEnd = s.plusMinutes(minutes.toLong())
                    val blockPhase =
                        when {
                            t.done -> TimePhase.FINISHED
                            week < currentWeek -> TimePhase.FINISHED
                            week > currentWeek -> TimePhase.UPCOMING
                            now != null -> CourseProgress.phase(s, blockEnd, now)
                            else -> TimePhase.UPCOMING
                        }
                    val blockSpan = (tbox.bottom.value - tbox.top.value).takeIf { it > 0f }
                    val blockProgress =
                        when (blockPhase) {
                            TimePhase.UPCOMING -> {
                                0f
                            }

                            TimePhase.FINISHED -> {
                                1f
                            }

                            TimePhase.ONGOING -> {
                                if (week == currentWeek && s.toLocalDate() == today && nowTimeY != null && blockSpan != null) {
                                    ((nowTimeY.value - tbox.top.value) / blockSpan).coerceIn(0f, 1f)
                                } else {
                                    now?.let { CourseProgress.fraction(s, blockEnd, it) } ?: 0f
                                }
                            }
                        }
                    TaskBlock(
                        t,
                        b.color,
                        s,
                        minutes,
                        slot.overlap,
                        blockPhase,
                        blockProgress,
                        mintColors,
                        courseTitleStyle,
                        timeStyle,
                        onCommit = { ns, nm -> onMoveTask(t, ns, nm) },
                        allowDrag = !reschedulePicking,
                        stacked = slot.stacked,
                        wide = blockW >= 64.dp,
                        modifier =
                            Modifier
                                .absoluteOffset(timeColW + colW * (s.dayOfWeek.value - 1) + offsetX, tbox.top)
                                .width(blockW)
                                .height(tbox.bottom - tbox.top),
                        onClick = {
                            if (reschedulePicking) {
                                val firstPeriod = Agenda.blockRows(periods, s.toLocalTime(), blockEnd.toLocalTime())?.first ?: 1
                                onEmptySlotClick(s.dayOfWeek.value, firstPeriod)
                            } else if (slot.overlap > 1) {
                                onSlotClick(slotEntries[cells.size + di].orEmpty())
                            } else {
                                onOpenTask(t.id)
                            }
                        },
                    )
                }
                // 今日列当前时间横条：仅限定在「今天」列内，且与正在上课的块置灰前沿严丝合缝
                if (todayColIndex != null && nowTimeY != null) {
                    val barColor = scheme.primary.copy(alpha = 0.52f)
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val colLeft = (timeColW + colW * (todayColIndex - 1)).toPx()
                                val colRight = (colLeft + colW.toPx()).coerceAtMost(size.width)
                                val y = nowTimeY.toPx().coerceIn(0f, size.height)
                                val outerInset = NowBarOuterInset.toPx()
                                val defaultStartX = (colLeft + outerInset).coerceAtMost(colRight)
                                val defaultEndX = (colRight - outerInset).coerceAtLeast(defaultStartX)
                                val edgeInsetPx = (NowBarOuterInset - BlockOuterInset).toPx()
                                val (barStartX, barEndX) =
                                    calcTimetableNowBarSpan(
                                        defaultStartX = defaultStartX,
                                        defaultEndX = defaultEndX,
                                        y = y,
                                        edgeInset = edgeInsetPx,
                                        blocks = todayNowBlocks,
                                    )
                                drawLine(
                                    color = barColor,
                                    start = Offset(barStartX, y),
                                    end = Offset(barEndX, y),
                                    strokeWidth = 1.25.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            },
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
internal data class LaneBox(
    val day: Int,
    val top: Dp,
    val bottom: Dp,
    val colorIndex: Int,
    /** 课程优先当叠放的正面卡：课是固定的锚，待办是可以挪的那一方。 */
    val course: Boolean = false,
)

/** 同时段里一条块怎么摆。 */
internal data class Slot(
    /** 并排时第几道。 */
    val lane: Int = 0,
    /** 一共几道；1 = 整宽。 */
    val lanes: Int = 1,
    /** 列太窄、放不下两道：整簇改叠放，正面卡保持完整列宽并向右下错位露出背面卡。 */
    val stacked: Boolean = false,
    /** 叠放时被压住的成员不单独画。 */
    val hidden: Boolean = false,
    /** 同时段的块数；>1 就是冲突，底栏显示叠放计数。 */
    val overlap: Int = 1,
)

/** 叠放簇里背面露出的成员几何：保留自身实际起止时间 [top]..[bottom]，[depth] 为 1..[MAX_STACK_BACK_LAYERS]。 */
internal data class StackPeek(
    val day: Int,
    val top: Dp,
    val bottom: Dp,
    val colorIndex: Int,
    val depth: Int,
    val clusterMaxDepth: Int,
    val itemIndex: Int,
    val frontIndex: Int,
    val frontTop: Dp,
    val frontBottom: Dp,
)

internal class SlotLayout(
    val slots: List<Slot>,
    /** 叠放簇背面按各自真实时间段错位露出的层次列表。 */
    val peeks: List<StackPeek>,
    /** 冲突簇的成员下标（只有 ≥2 条重叠的才收进来）。 */
    val groups: List<List<Int>>,
)

internal fun slotBlockSpan(
    colW: Dp,
    slot: Slot,
): Pair<Dp, Dp> {
    val laneW = (colW - BlockOuterInset * 2) / slot.lanes
    return when {
        slot.lanes > 1 -> (BlockOuterInset + laneW * slot.lane) to (laneW - 2.dp)
        slot.stacked -> (BlockOuterInset - StackFrontShiftX) to laneW
        else -> BlockOuterInset to laneW
    }
}

/**
 * 冲突摆放：按天把重叠的块聚成一簇。每道宽度够（≥ [MinLaneW]）就并排各占一道；
 * 不够就叠放——正面卡保持完整列宽，其余成员按各自实际时间段排入最多两层背面卡片向右下错位露出。
 */
internal fun layoutSlots(
    boxes: List<LaneBox>,
    colW: Dp,
): SlotLayout {
    val slots = Array(boxes.size) { Slot() }
    val peeks = ArrayList<StackPeek>()
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
                    val frontIdx = ordered.first()
                    val front = boxes[frontIdx]
                    val backOrdered =
                        ordered
                            .drop(1)
                            .sortedWith(compareBy({ boxes[it].top }, { boxes[it].bottom }))
                    val layerEnds = Array(MAX_STACK_BACK_LAYERS) { (-1).dp }
                    val pending = ArrayList<StackPeek>(backOrdered.size)
                    for (itemIdx in backOrdered) {
                        val b = boxes[itemIdx]
                        val layer = layerEnds.indexOfFirst { it <= b.top }
                        if (layer >= 0) {
                            layerEnds[layer] = maxOf(layerEnds[layer], b.bottom)
                            pending +=
                                StackPeek(
                                    day = b.day,
                                    top = b.top,
                                    bottom = b.bottom,
                                    colorIndex = b.colorIndex,
                                    depth = layer + 1,
                                    clusterMaxDepth = 1,
                                    itemIndex = itemIdx,
                                    frontIndex = frontIdx,
                                    frontTop = front.top,
                                    frontBottom = front.bottom,
                                )
                        }
                    }
                    val maxDepth = pending.maxOfOrNull { it.depth } ?: 1
                    pending.forEach { peeks += it.copy(clusterMaxDepth = maxDepth) }
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
    app.goke.ui.components.MintSheetOverlay(
        visible = visible,
        onDismissRequest = onDismiss,
        scrimAlpha = 0.35f,
        canDragDismiss = true,
        predictiveBackEnabled = visible && LocalTabVisible.current.value,
        sheetModifier =
            Modifier
                .statusBarsPadding()
                .padding(top = 16.dp),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        dragHandle = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            content()
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
internal fun TaskBlock(
    task: Task,
    colorIndex: Int,
    start: LocalDateTime,
    minutes: Int,
    /** 同时段块数；>1 时底栏写「N 项」，点开由正面卡列出全部。 */
    overlap: Int,
    phase: TimePhase,
    progress: Float,
    colors: MintColors,
    titleStyle: TextStyle,
    timeStyle: TextStyle,
    onCommit: (LocalDateTime, Int) -> Unit,
    allowDrag: Boolean = true,
    stacked: Boolean = false,
    wide: Boolean = false,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 与 CourseBlock 同款:圆角填色块、渐进/已结束退灰、同字号,无勾选圈;点击进编辑
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

    val effectivePhase = if (active) TimePhase.UPCOMING else phase
    val effectiveProgress = if (active) 0f else progress
    val textFg = rememberCourseProgressFg(fg, effectivePhase)
    val stateDesc =
        when (effectivePhase) {
            TimePhase.ONGOING -> stringResource(R.string.course_progress_ongoing, (effectiveProgress * 100f).toInt().coerceIn(0, 100))
            TimePhase.FINISHED -> stringResource(R.string.course_progress_finished)
            TimePhase.UPCOMING -> null
        }

    val blockBorder =
        when {
            active -> 2.dp to dragBorder
            task.isExam -> 0.8.dp to textFg.copy(alpha = 0.28f)
            stacked -> 0.75.dp to textFg.copy(alpha = 0.14f)
            else -> null
        }
    Box(
        modifier
            .zIndex(if (active) 3f else 0f)
            .onSizeChanged { hPx = it.height.toFloat() }
            .then(layer)
            .mintPressable(pressedScale = if (active) 1f else 0.97f) { onClick() }
            .then(if (stateDesc != null) Modifier.semantics { stateDescription = stateDesc } else Modifier)
            .pointerInput(dur, hPx, allowDrag) {
                if (!allowDrag || hPx <= 0f) return@pointerInput
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
        val examDisplay =
            if (task.isExam && !active) {
                JxfwSync.splitExamDisplay(task.title, stringResource(R.string.kind_exam))
            } else {
                null
            }
        val examLoc =
            if (examDisplay != null) {
                task.extra["location"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { LocalContext.current.formatRooms(it) }
                    .orEmpty()
            } else {
                ""
            }
        if (examDisplay != null && minutes >= 75 && !wide) {
            val tokens = remember(examDisplay.title) { JxfwSync.tokenizeVerticalTitle(examDisplay.title) }
            val maxTokens =
                when {
                    minutes >= 135 -> 9
                    examLoc.isNotEmpty() -> 6
                    else -> 7
                }
            val (vFontSize, vLineHeight) =
                when {
                    tokens.size <= 4 -> 12.sp to 14.sp
                    tokens.size <= 6 -> 11.sp to 12.5.sp
                    else -> 10.sp to 11.2.sp
                }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(TaskBlockShape)
                    .courseProgressBackground(
                        activeBg = bg,
                        phase = effectivePhase,
                        progress = effectiveProgress,
                        axis = CourseProgressAxis.VERTICAL,
                    ).then(if (blockBorder != null) Modifier.border(blockBorder.first, blockBorder.second, TaskBlockShape) else Modifier),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 3.5.dp, end = 3.5.dp, top = 7.dp, bottom = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            Modifier.padding(top = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.5.dp))
                                    .background(textFg.copy(alpha = 0.18f))
                                    .padding(horizontal = 2.5.dp, vertical = 2.5.dp),
                            ) {
                                Text(
                                    JxfwSync.formatVerticalText(examDisplay.badgeShort, 2),
                                    color = textFg,
                                    style = titleStyle.copy(fontSize = 8.5.sp, lineHeight = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                JxfwSync.formatVerticalText(examDisplay.title, maxTokens),
                                color = textFg,
                                style = titleStyle.copy(fontSize = vFontSize, lineHeight = vLineHeight),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = "${examDisplay.badgeFull} ${examDisplay.title}"
                                    },
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 5.dp),
                        thickness = 0.6.dp,
                        color = textFg.copy(alpha = 0.16f),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 3.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (examLoc.isNotEmpty()) {
                            Text(
                                examLoc,
                                modifier = Modifier.fillMaxWidth(),
                                color = textFg.copy(alpha = 0.88f),
                                style = timeStyle.copy(fontSize = 8.5.sp, lineHeight = 10.sp),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            start.hm(),
                            modifier = Modifier.fillMaxWidth(),
                            color = textFg.copy(alpha = 0.72f),
                            style = timeStyle.copy(fontSize = 8.sp, lineHeight = 9.5.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                if (overlap > 1) {
                    OverlapBadge(
                        overlap = overlap,
                        fg = textFg,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp),
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(TaskBlockShape)
                    .courseProgressBackground(
                        activeBg = bg,
                        phase = effectivePhase,
                        progress = effectiveProgress,
                        axis = CourseProgressAxis.VERTICAL,
                    ).then(if (blockBorder != null) Modifier.border(blockBorder.first, blockBorder.second, TaskBlockShape) else Modifier)
                    .padding(horizontal = 5.dp, vertical = 5.dp),
            ) {
                if (overlap > 1 && !active) {
                    Column(Modifier.weight(1f)) {
                        if (examDisplay != null) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(textFg.copy(alpha = 0.18f))
                                    .padding(horizontal = 4.5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    if (wide) examDisplay.badgeFull else examDisplay.badgeShort,
                                    color = textFg,
                                    style = timeStyle.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            examDisplay?.title ?: task.title,
                            modifier = Modifier.weight(1f, fill = false),
                            color = textFg,
                            style = titleStyle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OverlapBadge(
                        overlap = overlap,
                        fg = textFg,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    )
                } else {
                    if (examDisplay != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(textFg.copy(alpha = 0.18f))
                                .padding(horizontal = 4.5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                if (wide) examDisplay.badgeFull else examDisplay.badgeShort,
                                color = textFg,
                                style = timeStyle.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        examDisplay?.title ?: task.title,
                        // 时间那行必须留得下：标题吃掉剩余空间，短块里自动收成省略号
                        modifier = Modifier.weight(1f, fill = false),
                        color = textFg,
                        style = titleStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val label =
                        when {
                            !active -> start.hm()
                            mode == 3 -> previewEnd.hm()
                            else -> previewStart.hm()
                        }
                    Text(
                        label,
                        color = if (active) MaterialTheme.colorScheme.primary else textFg.copy(alpha = 0.85f),
                        style = timeStyle,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
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
    val desc = stringResource(R.string.conflict_count, overlap)
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fg.copy(alpha = 0.16f))
            .semantics { contentDescription = desc }
            .padding(horizontal = 4.5.dp, vertical = 1.dp),
    ) {
        Text(
            "+${overlap - 1}",
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun CourseStickyNoteTab(
    count: Int,
    colors: MintColors,
    finished: Boolean,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.course_card_todos_desc, count)
    Row(
        modifier
            .graphicsLayer {
                rotationZ = -4f
                alpha = if (finished) 0.78f else 1f
                shadowElevation = 2.5.dp.toPx()
                shape = StickyTabShape
                clip = false
            }.background(colors.stickyBg, StickyTabShape)
            .border(0.75.dp, colors.stickyBorder, StickyTabShape)
            .semantics { contentDescription = desc }
            .padding(horizontal = 4.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        Icon(
            Icons.Rounded.StickyNote2,
            null,
            tint = colors.stickyFg,
            modifier = Modifier.size(10.dp),
        )
        Text(
            "$count",
            color = colors.stickyFg,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 10.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
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
    /** 同时段块数；>1 时卡片底角显示叠放计数，点开由这张正面卡列出全部。 */
    overlap: Int,
    stacked: Boolean = false,
    todoCount: Int = 0,
    /** 块宽是否 ≥64dp（宽列加「@」前缀区分教室）。由调用方按已知的块宽传入，
     *  不再每个块起一个 BoxWithConstraints 子组合（一页 30 块 = 30 次 subcompose）。 */
    wide: Boolean,
    phase: TimePhase,
    progress: Float,
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
    val textFg = if (isRemoved) colors.removed else rememberCourseProgressFg(fg, phase)
    val stateDesc =
        if (isRemoved) {
            null
        } else {
            when (phase) {
                TimePhase.ONGOING -> stringResource(R.string.course_progress_ongoing, (progress * 100f).toInt().coerceIn(0, 100))
                TimePhase.FINISHED -> stringResource(R.string.course_progress_finished)
                TimePhase.UPCOMING -> null
            }
        }
    val bgMod =
        if (isRemoved) {
            Modifier.background(colors.conflictBg)
        } else {
            Modifier.courseProgressBackground(
                activeBg = bg,
                phase = phase,
                progress = progress,
                axis = CourseProgressAxis.VERTICAL,
            )
        }
    val resolvedBorder =
        when {
            borderColor != Color.Transparent -> 2.dp to borderColor
            o.course.isExam -> 0.8.dp to textFg.copy(alpha = 0.28f)
            stacked -> 0.75.dp to textFg.copy(alpha = 0.14f)
            else -> null
        }
    Box(
        modifier.mintPressable(pressedScale = 0.97f) { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isRemoved) Modifier.graphicsLayer { alpha = 0.6f } else Modifier)
                .clip(CourseBlockShape)
                .then(bgMod)
                .then(if (stateDesc != null) Modifier.semantics { stateDescription = stateDesc } else Modifier)
                .then(if (resolvedBorder != null) Modifier.border(resolvedBorder.first, resolvedBorder.second, CourseBlockShape) else Modifier),
        ) {
            val strike = if (isRemoved) TextDecoration.LineThrough else null
            if (o.course.isExam) {
                val ctx = LocalContext.current
                val examFallback = stringResource(R.string.kind_exam)
                val seatSuffix = stringResource(R.string.widget_exam_seat_suffix)
                val examDisplay = remember(o.course.name, examFallback) { JxfwSync.splitExamDisplay(o.course.name, examFallback) }
                val roomText = remember(o.room, ctx) { if (o.room.isNotBlank()) ctx.formatRooms(o.room) else "" }
                val seatText = remember(o.course.note, seatSuffix) { JxfwSync.extractExamSeat(o.course.note)?.let { "$it$seatSuffix" } }

                if (rows >= 2 && !wide) {
                    // 宣纸双列竖签：左列竖排印章+教师，右列竖排课名；有地点时底部保持卡片正常底色加细线隔开
                    val tokens = remember(examDisplay.title) { JxfwSync.tokenizeVerticalTitle(examDisplay.title) }
                    val hasFooter = roomText.isNotBlank() || seatText != null
                    val maxTokens =
                        when {
                            rows >= 3 -> if (hasFooter) 9 else 11
                            hasFooter -> if (seatText != null) 6 else 7
                            else -> 7
                        }
                    val (vFontSize, vLineHeight) =
                        when {
                            tokens.size <= 4 -> 12.sp to 14.sp
                            tokens.size <= 6 -> 11.sp to 12.5.sp
                            else -> 10.sp to 11.2.sp
                        }
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 3.5.dp, end = 3.5.dp, top = 7.dp, bottom = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    Modifier.padding(top = 1.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.5.dp))
                                            .background(textFg.copy(alpha = 0.18f))
                                            .padding(horizontal = 2.5.dp, vertical = 2.5.dp),
                                    ) {
                                        Text(
                                            JxfwSync.formatVerticalText(examDisplay.badgeShort, 2),
                                            color = textFg,
                                            style = titleStyle.copy(fontSize = 8.5.sp, lineHeight = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    if (showTeacher && o.course.teacher.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            JxfwSync.formatVerticalText(o.course.teacher, if (rows >= 3) 4 else 3),
                                            color = textFg.copy(alpha = 0.68f),
                                            style = roomStyle.copy(fontSize = 9.sp, lineHeight = 10.5.sp),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    if (o.rescheduled) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            JxfwSync.formatVerticalText(stringResource(R.string.rescheduled), 2),
                                            color = textFg.copy(alpha = 0.9f),
                                            style = roomStyle.copy(fontSize = 8.5.sp, lineHeight = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                Box(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Text(
                                        JxfwSync.formatVerticalText(examDisplay.title, maxTokens),
                                        color = textFg,
                                        style = titleStyle.copy(fontSize = vFontSize, lineHeight = vLineHeight),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        textDecoration = strike,
                                        modifier =
                                            Modifier.semantics {
                                                contentDescription = "${examDisplay.badgeFull} ${examDisplay.title}"
                                            },
                                    )
                                }
                            }
                            if (hasFooter) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 5.dp),
                                    thickness = 0.6.dp,
                                    color = textFg.copy(alpha = 0.16f),
                                )
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 3.5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (roomText.isNotBlank()) {
                                        Text(
                                            roomText,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = textFg.copy(alpha = 0.88f),
                                            style = roomStyle.copy(fontSize = 8.5.sp, lineHeight = 10.sp),
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = strike,
                                        )
                                    }
                                    if (seatText != null) {
                                        Text(
                                            seatText,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = textFg.copy(alpha = 0.72f),
                                            style = roomStyle.copy(fontSize = 8.sp, lineHeight = 9.5.sp),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = strike,
                                        )
                                    }
                                }
                            }
                        }
                        if (overlap > 1) {
                            OverlapBadge(
                                overlap = overlap,
                                fg = textFg,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp),
                            )
                        }
                    }
                } else {
                    // 1 节极矮块或平板宽列：紧凑横排 + 顶部半透胶囊，不重复「考试」
                    val locLine =
                        if (wide) {
                            JxfwSync.formatExamLocation(roomText, o.course.note, seatSuffix).orEmpty()
                        } else {
                            roomText.ifBlank { seatText.orEmpty() }
                        }
                    Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 5.dp)) {
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(textFg.copy(alpha = 0.18f))
                                    .padding(horizontal = 4.5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    if (wide) examDisplay.badgeFull else examDisplay.badgeShort,
                                    color = textFg,
                                    style = roomStyle.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                examDisplay.title,
                                color = textFg,
                                style = titleStyle,
                                fontWeight = FontWeight.Bold,
                                maxLines = if (rows == 1) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = strike,
                            )
                            if (locLine.isNotEmpty()) {
                                Text(
                                    if (wide) "@$locLine" else locLine,
                                    color = textFg.copy(alpha = 0.85f),
                                    style = roomStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = strike,
                                )
                            }
                            if (showTeacher && rows >= 2 && o.course.teacher.isNotBlank()) {
                                Text(
                                    o.course.teacher,
                                    color = textFg.copy(alpha = 0.72f),
                                    style = roomStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (o.rescheduled) {
                                Text(
                                    stringResource(R.string.rescheduled),
                                    color = textFg,
                                    style = roomStyle,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (overlap > 1) {
                            OverlapBadge(
                                overlap = overlap,
                                fg = textFg,
                                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                            )
                        }
                    }
                }
            } else {
                // 一列就 40dp 出头：教室 / 老师照样要显示，靠换行和省略号交代；
                // 把信息藏起来比截断更糟。宽卡片（平板 / 横屏）才加「@」前缀区分教室，窄列里多这一个字符就没地方了。
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        // 字号统一由 WeekGrid 记住传入；时间/教室数字用 tnum
                        Text(
                            o.course.name,
                            color = textFg,
                            style = titleStyle,
                            fontWeight = FontWeight.Bold,
                            maxLines =
                                when {
                                    rows >= 3 -> 4
                                    rows == 2 -> if (overlap > 1) 3 else 4
                                    else -> if (overlap > 1) 1 else 2
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
                                    color = textFg.copy(alpha = 0.85f),
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
                                color = textFg.copy(alpha = 0.75f),
                                style = roomStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (o.rescheduled) {
                            Text(
                                stringResource(R.string.rescheduled),
                                color = textFg,
                                style = roomStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (overlap > 1) {
                        OverlapBadge(
                            overlap = overlap,
                            fg = textFg,
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        if (todoCount > 0) {
            CourseStickyNoteTab(
                count = todoCount,
                colors = colors,
                finished = phase == TimePhase.FINISHED,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp),
            )
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
    now: LocalDateTime,
    today: LocalDate,
    onPickOnGrid: (CourseOccurrence) -> Unit,
    onJumpToWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var lastCell by remember { mutableStateOf(cell) }
    var lastPrefill by remember { mutableStateOf(prefill) }
    var bindingTodos by remember { mutableStateOf(false) }
    var showTodos by remember { mutableStateOf(true) }
    var bindQuery by remember { mutableStateOf("") }
    LaunchedEffect(cell, prefill) {
        if (cell != null) {
            lastCell = cell
            lastPrefill = null
            bindingTodos = false
            showTodos = true
            bindQuery = ""
        } else if (prefill != null) {
            lastPrefill = prefill
            lastCell = null
            bindingTodos = false
            showTodos = true
            bindQuery = ""
        }
    }
    val visible = cell != null || prefill != null
    val activePrefill = prefill ?: if (cell == null) lastPrefill else null
    val activeCell = cell ?: if (activePrefill == null) lastCell else null
    val activeOcc = activePrefill?.occ ?: activeCell?.occ

    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(cell, prefill) {
        if (cell != null || prefill != null) {
            submitted = false
        }
    }
    val canAct = visible && !submitted

    InWindowSheet(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        if (activeOcc == null) return@InWindowSheet
        BackHandler(enabled = visible && activePrefill == null && bindingTodos) {
            bindingTodos = false
        }
        val ctx = LocalContext.current
        val nav = LocalNav.current
        val actions = LocalActions.current
        val o = activeOcc
        val c = o.course
        val liveCourse = snap.courses.firstOrNull { it.id == c.id && it.deletedAt == null } ?: c
        val fadeSpec = MintTheme.spec<Float>()

        fun openEdit() {
            if (!visible || submitted) return
            submitted = true
            onDismiss()
            nav.push(Route.CourseEdit(liveCourse.id))
        }

        fun openNewTodo() {
            if (!visible || submitted) return
            submitted = true
            onDismiss()
            nav.push(Route.TodoEdit(courseId = liveCourse.id, courseWeek = o.originalWeek))
        }

        AnimatedContent(
            targetState = activePrefill != null,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "course-sheet-mode",
        ) { editing ->
            if (!editing && activeCell != null) {
                val courseById = remember(snap.courses) { snap.courses.filter { it.deletedAt == null }.associateBy { it.id } }
                val relatedTodos =
                    remember(snap.tasks, snap.courses, snap.exceptions, sem, o, now) {
                        TaskLogic.forOccurrence(snap.tasks, sem, snap.courses, snap.exceptions, o, now)
                    }
                val todoIndex = remember(snap, now) { todoIndexOf(snap, now) }

                fun toggleBind(
                    task: Task,
                    bind: Boolean,
                ) {
                    if (!visible) return
                    val ms = System.currentTimeMillis()
                    val updated =
                        if (bind) {
                            task.withCourseLink(
                                courseId = liveCourse.id,
                                courseWeek = o.originalWeek,
                                dueBeforeNextClass = task.due == null && task.blockStart == null,
                            )
                        } else {
                            task.withCourseLink(
                                courseId = null,
                                courseWeek = null,
                                dueBeforeNextClass = false,
                            )
                        }
                    val msg =
                        if (bind) {
                            ctx.getString(R.string.course_todos_bound_toast, liveCourse.name)
                        } else {
                            ctx.getString(R.string.course_todos_unbound_toast, task.title)
                        }
                    actions.commit(Edits.save(snap, updated, msg, ms), msg, undoable = true)
                }

                AnimatedContent(
                    targetState = bindingTodos,
                    transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                    label = "course-sheet-todos",
                ) { isBinding ->
                    if (isBinding) {
                        val initialOrder =
                            remember(liveCourse.id, o.originalWeek, isBinding) {
                                if (!isBinding) {
                                    emptyMap()
                                } else {
                                    snap.tasks
                                        .asSequence()
                                        .filter { it.deletedAt == null && it.parentId == null && !it.done && it.parcelCode() == null }
                                        .sortedWith(
                                            compareBy<Task>(
                                                {
                                                    when {
                                                        TaskLogic.matchesOccurrence(it, o, sem, snap.courses, snap.exceptions, now) -> 0
                                                        it.courseId == null -> 1
                                                        else -> 2
                                                    }
                                                },
                                                { it.due ?: it.blockStart ?: LocalDateTime.MAX },
                                                { -it.updatedAt },
                                            ),
                                        ).mapIndexed { idx, t -> t.id to idx }
                                        .toMap()
                                }
                            }
                        val allCandidates =
                            remember(snap.tasks, initialOrder) {
                                snap.tasks
                                    .asSequence()
                                    .filter { it.deletedAt == null && it.parentId == null && !it.done && it.parcelCode() == null }
                                    .sortedWith(compareBy({ initialOrder[it.id] ?: Int.MAX_VALUE }, { -it.updatedAt }))
                                    .toList()
                            }
                        val q = bindQuery.trim()
                        val filtered =
                            if (q.isEmpty()) {
                                allCandidates
                            } else {
                                allCandidates.filter { t ->
                                    t.title.contains(q, ignoreCase = true) || t.note.contains(q, ignoreCase = true)
                                }
                            }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 24.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { bindingTodos = false }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                                }
                                Text(
                                    stringResource(R.string.course_todos_bind_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(enabled = canAct, onClick = ::openNewTodo) {
                                    Icon(Icons.Rounded.Add, stringResource(R.string.course_todos_add))
                                }
                            }
                            if (allCandidates.size > 5) {
                                TonalField(
                                    label = stringResource(R.string.course_todos_bind_search),
                                    value = bindQuery,
                                    onChange = { bindQuery = it },
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            if (filtered.isEmpty()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.course_todos_bind_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FilledTonalButton(enabled = canAct, onClick = ::openNewTodo) {
                                        Text(stringResource(R.string.course_todos_add))
                                    }
                                }
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    filtered.forEach { t ->
                                        val isBound = TaskLogic.matchesOccurrence(t, o, sem, snap.courses, snap.exceptions, now)
                                        val otherCourse = if (!isBound) courseById[t.courseId] else null
                                        val otherOcc =
                                            if (!isBound && otherCourse != null && t.courseWeek != null) {
                                                TaskLogic.targetOccurrence(t, sem, snap.courses, snap.exceptions, now)
                                            } else {
                                                null
                                            }
                                        val timeMeta =
                                            when {
                                                t.dueBeforeNextClass -> stringResource(R.string.course_link_before_class)
                                                t.due != null -> ctx.dueLabel(t.due!!, t.dueHasTime, today)
                                                t.blockStart != null -> ctx.dueLabel(t.blockStart!!, true, today)
                                                else -> null
                                            }
                                        val boundOtherLabel =
                                            otherCourse?.let { oc ->
                                                if (otherOcc != null) {
                                                    stringResource(R.string.course_todos_bound_other_session, oc.name, ctx.monthDay(otherOcc.date))
                                                } else {
                                                    stringResource(R.string.course_todos_bound_other, oc.name)
                                                }
                                            }
                                        val sub =
                                            listOfNotNull(
                                                timeMeta,
                                                boundOtherLabel,
                                            ).joinToString(" · ")
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .mintClickable(pressedScale = 0.98f) { toggleBind(t, !isBound) }
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            MintCheck(
                                                checked = isBound,
                                                onChange = { toggleBind(t, !isBound) },
                                                size = 18.dp,
                                            )
                                            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                                                Text(
                                                    t.title,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (sub.isNotEmpty()) {
                                                    Text(
                                                        sub,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val noteState =
                            remember(liveCourse.note, o.originalWeek, o.week) {
                                val byOrig = CourseNotes.parse(liveCourse.note, o.originalWeek)
                                if (byOrig.currentWeekNotes.isNotEmpty() || o.week == o.originalWeek) {
                                    o.originalWeek to byOrig
                                } else {
                                    o.week to CourseNotes.parse(liveCourse.note, o.week)
                                }
                            }
                        val noteWeek = noteState.first
                        val parsedNote = noteState.second

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val sheetExam =
                                if (liveCourse.isExam) {
                                    JxfwSync.splitExamDisplay(liveCourse.name, stringResource(R.string.kind_exam))
                                } else {
                                    null
                                }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(22.dp)
                                        .clip(CircleShape)
                                        .background(MintTheme.colors.fg(liveCourse.colorIndex)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    sheetExam?.title ?: liveCourse.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (sheetExam != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Tag(
                                        sheetExam.badgeFull,
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            Text(
                                "${ctx.weekdayShort(o.date.dayOfWeek.value).let { stringResource(R.string.weekday_prefix, it) }} ${o.start.hm()}–${o.end.hm()} · ${stringResource(R.string.periods_range, o.startPeriod, o.endPeriod)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 14.dp, bottom = if (o.rescheduled) 2.dp else 8.dp),
                            )
                            if (o.rescheduled) {
                                val origDow = ctx.getString(R.string.weekday_prefix, ctx.weekdayShort(liveCourse.dayOfWeek))
                                val origHint =
                                    if (liveCourse.startPeriod == liveCourse.endPeriod) {
                                        stringResource(R.string.reschedule_from_hint_single, o.originalWeek, origDow, liveCourse.startPeriod)
                                    } else {
                                        stringResource(R.string.reschedule_from_hint, o.originalWeek, origDow, liveCourse.startPeriod, liveCourse.endPeriod)
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
                            if (liveCourse.teacher.isNotBlank()) {
                                ValueRow(stringResource(R.string.label_teacher), liveCourse.teacher, onClick = ::openEdit)
                            }
                            ValueRow(stringResource(R.string.week_label), WeekSets.format(liveCourse.weeks), onClick = ::openEdit)
                            if (activeCell.kind == CellKind.NORMAL) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .mintClickable(enabled = relatedTodos.isNotEmpty(), pressedScale = 0.98f) {
                                                showTodos = !showTodos
                                            }.padding(end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.tab_todo),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (relatedTodos.isNotEmpty()) {
                                            Box(
                                                Modifier
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 7.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    "${relatedTodos.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        enabled = canAct,
                                        onClick = {
                                            bindQuery = ""
                                            bindingTodos = true
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Link,
                                            stringResource(R.string.course_todos_bind),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        enabled = canAct,
                                        onClick = ::openNewTodo,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            stringResource(R.string.course_todos_add),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        enabled = relatedTodos.isNotEmpty(),
                                        onClick = { showTodos = !showTodos },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            if (showTodos && relatedTodos.isNotEmpty()) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            stringResource(if (showTodos && relatedTodos.isNotEmpty()) R.string.course_todos_hide else R.string.course_todos_show),
                                            tint =
                                                when {
                                                    relatedTodos.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                    showTodos -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = showTodos && relatedTodos.isNotEmpty(),
                                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        relatedTodos.forEach { t ->
                                            TodoLine(
                                                task = t,
                                                snap = snap,
                                                now = now,
                                                index = todoIndex,
                                                modifier = Modifier.padding(vertical = 1.dp),
                                                onClick = {
                                                    if (!visible || submitted) return@TodoLine
                                                    submitted = true
                                                    onDismiss()
                                                    nav.openTask(t.id)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (liveCourse.note.isNotBlank()) {
                                CourseNoteBox(
                                    note = liveCourse.note,
                                    week = noteWeek,
                                    parsedNote = parsedNote,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 6.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    onCopied = actions::toast,
                                )
                            }
                            if (activeCell.kind == CellKind.NORMAL) {
                                Spacer(Modifier.height(10.dp))
                                // 小屏放不下多个按钮就换行，主次不变：编辑 > 停课 > 调课
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
                                            val ex = CourseException(Ids.next(), liveCourse.id, origW, ExceptionType.CANCEL)
                                            val old = snap.exceptions.firstOrNull { it.deletedAt == null && it.courseId == liveCourse.id && it.week == origW }
                                            val cs = Edits.save(snap, if (old != null) ex.copy(id = old.id) else ex, ctx.getString(R.string.cancel_class_desc, liveCourse.name), System.currentTimeMillis())
                                            actions.commit(cs, ctx.getString(R.string.cancel_class_toast, liveCourse.name))
                                            onDismiss()
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        colors = secondaryColors,
                                    ) { Text(stringResource(R.string.cancel_class_this_week)) }
                                    FilledTonalButton(
                                        enabled = canAct,
                                        onClick = {
                                            if (!visible || submitted) return@FilledTonalButton
                                            submitted = true
                                            onPickOnGrid(o)
                                        },
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
                                                    .filter { it.deletedAt == null && it.courseId == liveCourse.id && it.week == origW }
                                                    .map { Change.Remove(it) }
                                            if (changes.isNotEmpty()) {
                                                actions.commit(
                                                    ChangeSet(
                                                        Ids.next(),
                                                        ctx.getString(R.string.restore_class_desc, liveCourse.name),
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
                    }
                }
            } else if (activePrefill != null) {
                ReschedulePanel(
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
    prefill: ReschedulePrefill,
    sem: Semester,
    snap: Snapshot,
    cal: SemesterCalendar,
    canAct: Boolean,
    trySubmit: () -> Boolean,
    onPickOnGrid: () -> Unit,
    onCommitted: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val scheme = MaterialTheme.colorScheme
    val occ = prefill.occ
    val c = occ.course
    val origW = occ.originalWeek
    val periodCount = cal.periods.size.coerceAtLeast(1)

    val newWeek = prefill.week.coerceIn(1, cal.totalWeeks)
    val dow = prefill.dow.coerceIn(1, 7)
    val start = prefill.startPeriod.coerceIn(1, periodCount)
    val end = prefill.endPeriod.coerceIn(start, periodCount)
    var room by remember(prefill) { mutableStateOf(prefill.room) }

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
    val sameSlotAsOriginal = newWeek == origW && dow == c.dayOfWeek && start == c.startPeriod && end == c.endPeriod
    val clashes =
        remember(snap, sem, newWeek, dow, start, end, c.id, origW) {
            CourseExpander
                .occurrencesInWeek(sem, snap.courses, snap.exceptions, newWeek)
                .filter { other ->
                    other.date.dayOfWeek.value == dow &&
                        other.startPeriod <= end &&
                        other.endPeriod >= start &&
                        !(other.course.id == c.id && other.originalWeek == origW) &&
                        !(c.isExam && !other.course.isExam && JxfwSync.isSameCourseForExam(c.name, other.course.name))
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

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerLow)
                .mintClickable(pressedScale = 0.99f, role = Role.Button, onClick = onPickOnGrid)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.reschedule_summary_line, newWeek, ctx.weekdayFull(dow), ctx.monthDay(targetDate)),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                periodSummary,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurface,
            )
            if (!sameSlotAsOriginal) {
                val origDow = ctx.getString(R.string.weekday_prefix, ctx.weekdayShort(c.dayOfWeek))
                val origHint =
                    if (c.startPeriod == c.endPeriod) {
                        stringResource(R.string.reschedule_from_hint_single, origW, origDow, c.startPeriod)
                    } else {
                        stringResource(R.string.reschedule_from_hint, origW, origDow, c.startPeriod, c.endPeriod)
                    }
                Text(
                    origHint,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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
                val sameAsOriginal = sameSlotAsOriginal && trimmedRoom == c.room
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

@Composable
private fun TimetableExportSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    snap: Snapshot,
    st: Settings,
    week: Int,
    onQuickShare: () -> Unit,
) {
    if (!visible) return
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val shareState by app.timetableShare.state.collectAsState()
    var busy by remember { mutableStateOf(false) }

    fun exportAndShare(
        mime: String,
        buildFile: suspend () -> java.io.File?,
    ) {
        if (busy) return
        if (snap.live().currentSemester == null) {
            actions.toast(ctx.getString(R.string.timetable_export_no_semester))
            return
        }
        scope.launch {
            busy = true
            try {
                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { buildFile() }
                if (f != null) {
                    onDismiss()
                    Sharing.share(ctx, f, mime)
                } else {
                    actions.toast(ctx.getString(R.string.export_failed))
                }
            } catch (_: Throwable) {
                actions.toast(ctx.getString(R.string.export_failed))
            } finally {
                busy = false
            }
        }
    }

    MintBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.timetable_export_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            NavRow(
                title = stringResource(R.string.timetable_export_quick_share),
                icon = Icons.Rounded.Cloud,
                trailing = stringResource(R.string.share_experimental_tag),
                enabled = !busy,
                onClick = onQuickShare,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(
                title = stringResource(R.string.timetable_export_xlsx),
                icon = Icons.Rounded.GridView,
                enabled = !busy,
            ) {
                exportAndShare(Sharing.MIME_XLSX) {
                    Sharing.exportTimetableXlsx(
                        ctx,
                        snap,
                        showTeacher = st.showTeacher,
                        displayName = shareState.displayName.takeIf { it.isNotBlank() },
                    )
                }
            }
            NavRow(
                title = stringResource(R.string.timetable_export_ics),
                icon = Icons.Rounded.DateRange,
                enabled = !busy,
            ) {
                exportAndShare(Sharing.MIME_ICS) {
                    Sharing.exportTimetableIcs(
                        ctx,
                        snap,
                        displayName = shareState.displayName.takeIf { it.isNotBlank() },
                    )
                }
            }
            NavRow(
                title = stringResource(R.string.timetable_export_json),
                icon = Icons.Rounded.IosShare,
                enabled = !busy,
            ) {
                exportAndShare(Sharing.MIME_JSON) {
                    Sharing.exportTimetableJson(
                        ctx,
                        snap,
                        displayName = shareState.displayName,
                    )
                }
            }
            NavRow(
                title = stringResource(R.string.timetable_export_png),
                icon = Icons.Rounded.Image,
                enabled = !busy,
            ) {
                exportAndShare(Sharing.MIME_PNG) {
                    Sharing.exportTimetableImage(ctx, snap, st, week)
                }
            }
        }
    }
}

@Composable
private fun WeekPickerSheet(
    visible: Boolean,
    sem: Semester,
    cal: SemesterCalendar,
    selectedWeek: Int,
    currentWeek: Int,
    nonEmptyWeeks: () -> Set<Int>,
    onSelectWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val activeWeeks = remember(sem.id, sem.totalWeeks) { nonEmptyWeeks() }
    val scheme = MaterialTheme.colorScheme
    val cols = 5
    val rows = (sem.totalWeeks + cols - 1) / cols

    MintBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.timetable_pick_week),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (selectedWeek != currentWeek && currentWeek in 1..sem.totalWeeks) {
                    Box(
                        Modifier
                            .heightIn(min = 30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(scheme.primaryContainer)
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) { onSelectWeek(currentWeek) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.back_to_this_week),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            val subtitle =
                if (sem.name.isNotBlank()) {
                    stringResource(R.string.timetable_pick_week_subtitle, sem.name, sem.totalWeeks)
                } else {
                    stringResource(R.string.week_n_total, sem.totalWeeks)
                }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 14.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (r in 0 until rows) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (c in 0 until cols) {
                            val w = r * cols + c + 1
                            if (w <= sem.totalWeeks) {
                                val isSelected = w == selectedWeek
                                val isCurrent = w == currentWeek
                                val isEmpty = w !in activeWeeks
                                val bg =
                                    when {
                                        isSelected -> scheme.primary
                                        isCurrent -> scheme.primaryContainer
                                        else -> scheme.surfaceContainerHighest
                                    }
                                val fg =
                                    when {
                                        isSelected -> scheme.onPrimary
                                        isCurrent -> scheme.onPrimaryContainer
                                        else -> scheme.onSurface
                                    }
                                val start = cal.weekStart(w)
                                val sub =
                                    if (isCurrent) {
                                        stringResource(R.string.bucket_this_week)
                                    } else {
                                        "${start.monthValue}/${start.dayOfMonth}"
                                    }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 54.dp)
                                        .alpha(if (isEmpty && !isSelected && !isCurrent) 0.56f else 1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .mintClickable(pressedScale = 0.95f, role = Role.Button) { onSelectWeek(w) }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "$w",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg,
                                    )
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum"),
                                        fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        color = fg.copy(alpha = if (isSelected || isCurrent) 0.92f else 0.72f),
                                        maxLines = 1,
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
    }
}
