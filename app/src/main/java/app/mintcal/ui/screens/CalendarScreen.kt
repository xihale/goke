package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.AgendaRow
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import app.mintcal.ui.yearMonth
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private enum class CalMode { MONTH, WEEK }

/** 翻页中心页：向前后各留出 3000 个月/周，滑不到头。 */
private const val PAGE_CENTER = 3000

/**
 * 日历：顶部只有一行——期间标题（点一下回今天）+ 月/周切换。
 * 翻页靠左右滑动网格（或横向拖标题），没有左右箭头。
 */
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now by rememberNow()
    val today = now.toLocalDate()
    var mode by rememberSaveable { mutableStateOf(CalMode.MONTH) }
    var selectedStr by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedStr)
    val first = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val haptics = LocalHapticFeedback.current
    val reduce = reduceMotion()
    val scope = rememberCoroutineScope()

    // 翻页锚点：进入某个模式时，以当时选中日期所在月/周为准
    val anchor =
        LocalDate.parse(
            rememberSaveable(mode) {
                (if (mode == CalMode.MONTH) selected.withDayOfMonth(1) else selected.with(TemporalAdjusters.previousOrSame(first))).toString()
            },
        )
    val pager = key(mode) { rememberPagerState(initialPage = PAGE_CENTER) { PAGE_CENTER * 2 } }
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
        selectedStr = d.toString()
        val p = pageOf(d)
        if (p != pager.currentPage) {
            lastPage = p
            scope.launch { pager.animateScrollToPage(p) }
        }
    }

    // 翻到哪一页，选中日期就跟到那个月/周的同名日子（不打断下面列表的语义）
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            val delta = p - lastPage
            lastPage = p
            if (delta != 0) {
                val cur = LocalDate.parse(selectedStr)
                selectedStr =
                    when (mode) {
                        CalMode.MONTH -> cur.plusMonths(delta.toLong())
                        CalMode.WEEK -> cur.plusWeeks(delta.toLong())
                    }.toString()
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        if (nav.reselect > 0) goto(today)
    }

    val dayItems = remember(snap, selected, settings.defaultBlockMinutes) { Agenda.forDay(snap, selected, settings.defaultBlockMinutes) }
    val title =
        when (mode) {
            CalMode.MONTH -> {
                ctx.yearMonth(anchor.plusMonths((pager.currentPage - PAGE_CENTER).toLong()))
            }

            CalMode.WEEK -> {
                val s = periodStart(pager.currentPage)
                val e = s.plusDays(6)
                if (s.month == e.month) {
                    stringResource(R.string.week_range_same_month, s.monthValue, s.dayOfMonth, e.dayOfMonth)
                } else {
                    stringResource(R.string.week_range, s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth)
                }
            }
        }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { goto(today) }
                                .pointerInput(mode, pager) {
                                    var acc = 0f
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            val threshold = 48.dp.toPx()
                                            when {
                                                acc > threshold -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                                                acc < -threshold -> scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                                            }
                                            acc = 0f
                                        },
                                        onDragCancel = { acc = 0f },
                                    ) { _, drag -> acc += drag }
                                }.padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                    ModeSwitch(mode) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        mode = it
                    }
                }
            }
            item(key = "grid") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MintTheme.card)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0..6) {
                            val d = first.plus(i.toLong())
                            Text(
                                ctx.weekdayShort(d.value),
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxWidth(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !reduce,
                    ) { page ->
                        val start = periodStart(page)
                        val rows = if (mode == CalMode.MONTH) 6 else 1
                        val inMonthOf = if (mode == CalMode.MONTH) anchor.plusMonths((page - PAGE_CENTER).toLong()).month else null
                        Column {
                            for (r in 0 until rows) {
                                Row(Modifier.fillMaxWidth()) {
                                    for (c in 0..6) {
                                        val d = start.plusDays((r * 7 + c).toLong())
                                        DayCell(
                                            d,
                                            selected = d == selected,
                                            today = d == today,
                                            inPeriod = inMonthOf == null || d.month == inMonthOf,
                                            modifier = Modifier.weight(1f),
                                        ) { dd -> goto(dd) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "h-day") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.monthDayWeekday(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(
                            R.string.day_counts,
                            dayItems.count { it is CourseItem },
                            dayItems.count { it is TaskItem || it is BlockItem || it is EventItem },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (dayItems.isEmpty()) {
                item(key = "empty-day") { EmptyState(stringResource(R.string.day_empty)) }
            }
            items(dayItems, key = { agendaKey(it) }, contentType = { it::class.simpleName }) { item ->
                AgendaRow(
                    item,
                    now,
                    settings.showTeacher,
                    onOpenCourse = { nav.push(Route.CourseEdit(it)) },
                    onOpenEvent = { nav.push(Route.TodoEdit(it)) },
                    onOpenTask = { nav.openTask(it) },
                    onToggleTask = { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                    modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
                )
            }
        }
        FloatingActionButton(
            onClick = { nav.push(Route.TodoEdit(null, selected.toString())) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(22.dp),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.new_entry)) }
    }
}

private fun agendaKey(i: app.mintcal.core.agenda.AgendaItem): String =
    when (i) {
        is CourseItem -> "c-${i.occ.course.id}-${i.occ.date}-${i.occ.startPeriod}"
        is EventItem -> "e-${i.event.id}-${i.start}"
        is TaskItem -> "t-${i.task.id}"
        is BlockItem -> "b-${i.task.id}"
    }

@Composable
private fun ModeSwitch(
    mode: CalMode,
    onChange: (CalMode) -> Unit,
) {
    Row(Modifier.clip(CircleShape).background(MintTheme.card).padding(3.dp)) {
        CalMode.entries.forEach { m ->
            val sel = m == mode
            val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MintTheme.spec(), label = "mode-bg")
            val fg by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, MintTheme.spec(), label = "mode-fg")
            Text(
                stringResource(if (m == CalMode.MONTH) R.string.mode_month else R.string.mode_week),
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(bg)
                        .clickable(enabled = !sel) { onChange(m) }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = fg,
            )
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate,
    selected: Boolean,
    today: Boolean,
    inPeriod: Boolean,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit,
) {
    val discBg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, MintTheme.spec(), label = "cell-bg")
    val fg by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            today -> MaterialTheme.colorScheme.primary
            !inPeriod -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        MintTheme.spec(),
        label = "cell-fg",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, MintTheme.spec(), label = "cell-scale")
    Column(
        modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick(d) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(CircleShape)
                .background(discBg),
            contentAlignment = Alignment.Center,
        ) {
            Text("${d.dayOfMonth}", color = fg, fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
