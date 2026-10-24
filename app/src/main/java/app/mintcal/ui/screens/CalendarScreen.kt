package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private enum class CalMode { MONTH, WEEK, DAY }

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

    /** 月份/周切换方向：1 前进、-1 后退，用于决定滑动方向。 */
    var dir by rememberSaveable { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    val selected = LocalDate.parse(selectedStr)
    val first = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    val gridStart =
        when (mode) {
            CalMode.MONTH -> selected.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(first))
            CalMode.WEEK -> selected.with(TemporalAdjusters.previousOrSame(first))
            CalMode.DAY -> selected
        }
    val gridEnd =
        when (mode) {
            CalMode.MONTH -> gridStart.plusDays(41)
            CalMode.WEEK -> gridStart.plusDays(6)
            CalMode.DAY -> selected
        }
    val marks = remember(snap, gridStart, gridEnd) { Agenda.marks(snap, gridStart, gridEnd) }
    val items = remember(snap, selected, settings.defaultBlockMinutes) { Agenda.forDay(snap, selected, settings.defaultBlockMinutes) }

    fun shift(d: Int) {
        dir = d
        selectedStr =
            when (mode) {
                CalMode.MONTH -> selected.plusMonths(d.toLong()).toString()
                CalMode.WEEK -> selected.plusWeeks(d.toLong()).toString()
                CalMode.DAY -> selected.plusDays(d.toLong()).toString()
            }
    }

    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "h-month") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                    AnimatedContent(
                        ctx.yearMonth(selected),
                        transitionSpec = {
                            val sign = if (dir >= 0) 1 else -1
                            (slideInHorizontally(slideSpec) { sign * it / 3 } + fadeIn(fadeSpec)) togetherWith
                                (slideOutHorizontally(slideSpec) { -sign * it / 3 } + fadeOut(fadeSpec))
                        },
                        label = "month-title",
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).clickable { selectedStr = today.toString() })
                    }
                    IconButton(onClick = { shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                }
            }
            item(key = "h-mode") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(Modifier.clip(CircleShape).background(MintTheme.card).padding(3.dp)) {
                        CalMode.entries.forEach { m ->
                            val sel = m == mode
                            val pillBg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MintTheme.spec(), label = "mode-bg")
                            val pillFg by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, MintTheme.spec(), label = "mode-fg")
                            Text(
                                stringResource(
                                    when (m) {
                                        CalMode.MONTH -> R.string.mode_month
                                        CalMode.WEEK -> R.string.mode_week
                                        CalMode.DAY -> R.string.mode_day
                                    },
                                ),
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(pillBg)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            mode = m
                                        }.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = pillFg,
                            )
                        }
                    }
                }
            }
            if (mode != CalMode.DAY) {
                item(key = "grid") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MintTheme.card)
                            .padding(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0..6) {
                                val d = first.plus(i.toLong())
                                Text(ctx.weekdayShort(d.value), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        AnimatedContent(
                            mode to gridStart,
                            transitionSpec = {
                                val sign = if (dir >= 0) 1 else -1
                                (
                                    (slideInHorizontally(slideSpec) { sign * it / 4 } + fadeIn(fadeSpec)) togetherWith
                                        (slideOutHorizontally(slideSpec) { -sign * it / 4 } + fadeOut(fadeSpec))
                                ).using(SizeTransform(clip = false))
                            },
                            label = "cal-grid",
                        ) { (m, start) ->
                            val rows = if (m == CalMode.MONTH) 6 else 1
                            Column {
                                for (r in 0 until rows) {
                                    Row(Modifier.fillMaxWidth()) {
                                        for (c in 0..6) {
                                            val d = start.plusDays((r * 7 + c).toLong())
                                            DayCell(
                                                d,
                                                selected = d == selected,
                                                today = d == today,
                                                inMonth = m != CalMode.MONTH || d.month == selected.month,
                                                marks = marks[d],
                                                modifier = Modifier.weight(1f),
                                            ) { dd ->
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedStr = dd.toString()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "h-day") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.monthDayWeekday(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.day_counts, items.count { it is CourseItem }, items.count { it is EventItem }, items.count { it is TaskItem || it is BlockItem }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty-day") { EmptyState(stringResource(R.string.day_empty)) }
            }
            items(items, key = { agendaKey(it) }, contentType = { it::class.simpleName }) { item ->
                AgendaRow(
                    item,
                    now,
                    settings.showTeacher,
                    onOpenCourse = { nav.push(Route.CourseEdit(it)) },
                    onOpenEvent = { nav.push(Route.EventEdit(it)) },
                    onOpenTask = { nav.push(Route.TaskDetail(it)) },
                    onToggleTask = { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                    modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
                )
            }
        }
        FloatingActionButton(
            onClick = { nav.push(Route.EventEdit(null, selected.toString())) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(22.dp),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.new_event)) }
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
private fun DayCell(
    d: LocalDate,
    selected: Boolean,
    today: Boolean,
    inMonth: Boolean,
    marks: app.mintcal.core.agenda.DayMarks?,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit,
) {
    val discBg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, MintTheme.spec(), label = "cell-bg")
    val fg by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            today -> MaterialTheme.colorScheme.primary
            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        MintTheme.spec(),
        label = "cell-fg",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, MintTheme.spec(), label = "cell-scale")
    Column(modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).clickable { onClick(d) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
        Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (marks != null) {
                AnimatedVisibility(marks.courses > 0, enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    Dot(MaterialTheme.colorScheme.primary)
                }
                AnimatedVisibility(marks.events > 0, enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    Dot(MaterialTheme.colorScheme.tertiary)
                }
                AnimatedVisibility(marks.tasks > 0, enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    Dot(MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}
