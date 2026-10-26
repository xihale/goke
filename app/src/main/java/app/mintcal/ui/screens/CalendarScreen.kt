package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.AgendaItem
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.AgendaRow
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.fullDate
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

/** 月份标题打开日期选择，左右滑动翻页，选中日期旁提供回今天入口。 */
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val resources = LocalResources.current
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
            scope.launch {
                // 直接滑动始终可用；程序化跳转才尊重「减少动效」
                if (reduce) pager.scrollToPage(p) else pager.animateScrollToPage(p)
            }
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    var previewDate by remember { mutableStateOf<LocalDate?>(null) }

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.date_pick)) { showPicker = true }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                        .clip(MaterialTheme.shapes.large)
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
                    ) { page ->
                        val start = periodStart(page)
                        val rows = if (mode == CalMode.MONTH) 6 else 1
                        val inMonthOf = if (mode == CalMode.MONTH) anchor.plusMonths((page - PAGE_CENTER).toLong()).month else null
                        val dayMarks =
                            remember(snap, start, settings.defaultBlockMinutes, rows) {
                                Agenda
                                    .range(snap, start, start.plusDays(rows * 7L - 1), settings.defaultBlockMinutes)
                                    .mapValues { (_, entries) -> CalendarMarks.from(entries) }
                            }
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
                                            marks = dayMarks[d] ?: CalendarMarks.Empty,
                                            modifier = Modifier.weight(1f),
                                            onClick = { goto(it) },
                                            onPreview = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                previewDate = it
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MarkerLegend(CalendarMarks(1, 0, 0), stringResource(R.string.calendar_mark_schedule))
                        MarkerLegend(CalendarMarks(0, 1, 0), stringResource(R.string.calendar_mark_pending))
                        MarkerLegend(CalendarMarks(0, 0, 1), stringResource(R.string.calendar_mark_done))
                    }
                    Text(
                        stringResource(R.string.calendar_preview_hint),
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "h-day") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.monthDayWeekday(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AnimatedVisibility(visible = selected != today, enter = fadeIn(MintTheme.spec()), exit = fadeOut(MintTheme.spec())) {
                        Text(
                            stringResource(R.string.back_today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        goto(today)
                                    }.padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                    Text(
                        stringResource(
                            R.string.calendar_day_totals,
                            dayItems.count { it is CourseItem || it is EventItem },
                            dayItems.count { it is TaskItem || it is BlockItem },
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.new_entry)) }
        if (showPicker) {
            DatePickerDlg(selected, { showPicker = false }) {
                showPicker = false
                goto(it)
            }
        }
        previewDate?.let { date ->
            val entries = remember(snap, date, settings.defaultBlockMinutes) { Agenda.forDay(snap, date, settings.defaultBlockMinutes) }
            val marks = remember(entries) { CalendarMarks.from(entries) }
            ModalBottomSheet(
                onDismissRequest = { previewDate = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
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
                        items(entries, key = { agendaKey(it) }) { entry ->
                            AgendaRow(
                                entry,
                                now,
                                settings.showTeacher,
                                onOpenCourse = {
                                    previewDate = null
                                    nav.push(Route.CourseEdit(it))
                                },
                                onOpenEvent = {
                                    previewDate = null
                                    nav.push(Route.TodoEdit(it))
                                },
                                onOpenTask = {
                                    previewDate = null
                                    nav.openTask(it)
                                },
                                onToggleTask = { task, done -> toggle(actions, snap, task, done, now, resources.getString(R.string.done_toast, task.title)) },
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
    Row(
        Modifier
            .clip(CircleShape)
            .background(MintTheme.card)
            .padding(3.dp)
            .selectableGroup(),
    ) {
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
                        .selectable(
                            selected = sel,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!sel) {
                                onChange(m)
                            }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
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
    marks: CalendarMarks,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit,
    onPreview: (LocalDate) -> Unit,
) {
    val ctx = LocalContext.current
    val dayDescription = stringResource(R.string.calendar_day_detail, marks.scheduled, marks.pending, marks.done)
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
    Column(
        modifier
            .height(48.dp)
            .combinedClickable(
                // 按下不做浅背景高亮：选中态交给圆点本身，避免整格闪底色
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { onClick(d) },
                onLongClickLabel = stringResource(R.string.calendar_preview_day),
                onLongClick = { onPreview(d) },
            ).semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = ctx.fullDate(d) + ", " + dayDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(discBg)
                .then(
                    if (today && !selected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("${d.dayOfMonth}", color = fg, fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(2.dp))
        CalendarIndicators(marks, inPeriod)
    }
}

private data class CalendarMarks(
    val scheduled: Int,
    val pending: Int,
    val done: Int,
) {
    companion object {
        val Empty = CalendarMarks(0, 0, 0)

        fun from(entries: List<AgendaItem>): CalendarMarks {
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
            return CalendarMarks(scheduled, pending, done)
        }
    }
}

@Composable
private fun CalendarIndicators(
    marks: CalendarMarks,
    inPeriod: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val schedule = MaterialTheme.colorScheme.primary.copy(alpha = if (inPeriod) 1f else 0.45f)
    val pending = MaterialTheme.colorScheme.tertiary.copy(alpha = if (inPeriod) 1f else 0.45f)
    val done = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inPeriod) 0.85f else 0.4f)
    Canvas(modifier.size(width = 36.dp, height = 9.dp)) {
        val scheduledWidth = if (marks.scheduled > 0) (3 + minOf(marks.scheduled, 3) * 3).dp.toPx() else 0f
        val taskSlots = minOf(marks.pending + marks.done, 3)
        val tasksWidth = if (taskSlots > 0) taskSlots * 7.dp.toPx() - 1.dp.toPx() else 0f
        val gap = if (scheduledWidth > 0 && tasksWidth > 0) 3.dp.toPx() else 0f
        var x = (size.width - scheduledWidth - tasksWidth - gap) / 2f
        if (scheduledWidth > 0) {
            drawRoundRect(schedule, Offset(x, 3.5.dp.toPx()), Size(scheduledWidth, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            x += scheduledWidth + gap
        }
        repeat(taskSlots) { index ->
            val center = Offset(x + 3.dp.toPx(), size.height / 2f)
            if (index < marks.pending) {
                drawCircle(pending, 2.5.dp.toPx(), center)
            } else {
                // 已完成用实心灰点，不再画空心圈
                drawCircle(done, 2.5.dp.toPx(), center)
            }
            x += 7.dp.toPx()
        }
    }
}

@Composable
private fun MarkerLegend(
    marks: CalendarMarks,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CalendarIndicators(marks, modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
