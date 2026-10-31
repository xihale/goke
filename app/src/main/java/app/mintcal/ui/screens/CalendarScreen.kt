package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.AgendaItem
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.todo.Bucket
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.AgendaRow
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.MintFab
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
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 176.dp),
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
                            style = MaterialTheme.typography.titleMedium,
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
                        // 月视图按当月实际周数 5/6 行排，避免 5 周的月份底部空一行
                        val monthOf = anchor.plusMonths((page - PAGE_CENTER).toLong())
                        val rows =
                            if (mode == CalMode.MONTH) {
                                // 覆盖整月：从本月 1 号所在的周一，到月末所在的周日，按天数除以 7
                                val end = monthOf.with(TemporalAdjusters.lastDayOfMonth()).with(TemporalAdjusters.nextOrSame(first.plus(6)))
                                ((ChronoUnit.DAYS.between(start, end) + 1) / 7).toInt()
                            } else {
                                1
                            }
                        // 翻月/切换时高度平滑过渡，不跳变
                        val gridH by animateDpAsState(48.dp * rows, MintTheme.spec<Dp>(), label = "grid-h")
                        val inMonthOf = if (mode == CalMode.MONTH) monthOf.month else null
                        val dayMarks =
                            remember(snap, start, settings.defaultBlockMinutes, rows) {
                                Agenda
                                    .range(snap, start, start.plusDays(rows * 7L - 1), settings.defaultBlockMinutes)
                                    .mapValues { (_, entries) -> CalendarMarks.from(entries) }
                            }
                        Column(Modifier.height(gridH)) {
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
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(ctx.monthDayWeekday(selected), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AnimatedVisibility(visible = selected != today, enter = fadeIn(MintTheme.spec()), exit = fadeOut(MintTheme.spec())) {
                            Text(
                                stringResource(R.string.back_today),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button) {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            goto(today)
                                        }.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
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
        MintFab(
            visible = !quickAddOpen && previewDate == null,
            onClick = { quickAddOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 92.dp),
        )
        QuickAddSheet(
            snap = snap,
            now = now,
            listFilter = null,
            defaultBucket = if (selected == today) Bucket.TODAY else null,
            focusSignal = 0,
            visible = quickAddOpen,
            onDismiss = { quickAddOpen = false },
            anchorDate = selected,
            acceptShare = false,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(),
        )
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
    // 与底栏同款的滑动色块：选中块从当前项滑到目标项，而不是两边各自淡入淡出
    val sel = if (mode == CalMode.MONTH) 0 else 1
    val travel = remember { Animatable(sel.toFloat()) }
    val spec = MintTheme.spec<Float>()
    LaunchedEffect(sel) { travel.animateTo(sel.toFloat(), spec) }
    val pill = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier
            .clip(CircleShape)
            .background(MintTheme.card)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val slot = size.width / CalMode.entries.size
            drawRoundRect(
                color = pill,
                topLeft = Offset(travel.value * slot, 0f),
                size = Size(slot, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
        Row {
            CalMode.entries.forEach { m ->
                val isSel = m == mode
                val fg by animateColorAsState(if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, MintTheme.spec(), label = "mode-fg")
                Text(
                    stringResource(if (m == CalMode.MONTH) R.string.mode_month else R.string.mode_week),
                    modifier =
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .selectable(
                                selected = isSel,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSel) {
                                    onChange(m)
                                }
                            }.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                )
            }
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
    val haptics = LocalHapticFeedback.current
    // 按下缩放 + 点击触觉：全局 NoIndication 下自绘可点区域的统一反馈
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, MintTheme.spec(), label = "cell-press")
    val dayDescription = stringResource(R.string.calendar_day_detail, marks.scheduled, marks.pending, marks.done)
    val discBg by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary

            // 今天是描边圈，选中是实心圈：两者都填色就分不出哪一个是「今天」
            else -> Color.Transparent
        },
        MintTheme.spec(),
        label = "cell-bg",
    )
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.height(48.dp)
            .combinedClickable(
                // 按下不做浅背景高亮：缩放反馈由 graphicsLayer 提供，选中态交给圆点本身
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick(d)
                },
                onLongClickLabel = stringResource(R.string.calendar_preview_day),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onPreview(d)
                },
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
                .let { if (today && !selected) it.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else it },
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
    val pending = MaterialTheme.colorScheme.primary.copy(alpha = if (inPeriod) 1f else 0.45f)
    val done = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inPeriod) 0.85f else 0.4f)
    // 形状编码：实心短条=有课，空心环=还有待办，小实心点=已完成。
    // （之前三种都是同样的小胶囊，缩到 3dp 高以后完全分不出来）
    Canvas(modifier.size(width = 36.dp, height = 9.dp)) {
        val barW = 6.dp.toPx()
        val barH = 3.dp.toPx()
        val ringR = 2.6.dp.toPx()
        val dotR = 2.dp.toPx()
        val gap = 3.dp.toPx()
        val items = ArrayList<Int>(3) // 0=日程 1=待办 2=已完成
        if (marks.scheduled > 0) items.add(0)
        repeat(minOf(marks.pending, 3 - items.size)) { items.add(1) }
        repeat(minOf(marks.done, 3 - items.size)) { items.add(2) }
        if (items.isEmpty()) return@Canvas

        fun widthOf(k: Int) = if (k == 0) barW else (if (k == 1) ringR * 2 else dotR * 2)
        val total = items.sumOf { widthOf(it).toDouble() }.toFloat() + gap * (items.size - 1)
        var x = (size.width - total) / 2f
        val midY = size.height / 2f
        items.forEach { k ->
            when (k) {
                0 -> drawRoundRect(schedule, Offset(x, midY - barH / 2f), Size(barW, barH), CornerRadius(barH / 2f))
                1 -> drawCircle(pending, ringR, Offset(x + ringR, midY), style = Stroke(1.dp.toPx()))
                else -> drawCircle(done, dotR, Offset(x + dotR, midY))
            }
            x += widthOf(k) + gap
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
