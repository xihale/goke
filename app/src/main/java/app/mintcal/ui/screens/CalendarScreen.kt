package app.mintcal.ui.screens

import app.mintcal.ui.theme.MintTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
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
    val selected = LocalDate.parse(selectedStr)
    val first = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    val gridStart = when (mode) {
        CalMode.MONTH -> selected.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(first))
        CalMode.WEEK -> selected.with(TemporalAdjusters.previousOrSame(first))
        CalMode.DAY -> selected
    }
    val gridEnd = when (mode) {
        CalMode.MONTH -> gridStart.plusDays(41)
        CalMode.WEEK -> gridStart.plusDays(6)
        CalMode.DAY -> selected
    }
    val marks = remember(snap, gridStart, gridEnd) { Agenda.marks(snap, gridStart, gridEnd) }
    val items = remember(snap, selected, settings.defaultBlockMinutes) { Agenda.forDay(snap, selected, settings.defaultBlockMinutes) }

    fun shift(dir: Int) {
        selectedStr = when (mode) {
            CalMode.MONTH -> selected.plusMonths(dir.toLong()).toString()
            CalMode.WEEK -> selected.plusWeeks(dir.toLong()).toString()
            CalMode.DAY -> selected.plusDays(dir.toLong()).toString()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                    Text(ctx.yearMonth(selected), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).clickable { selectedStr = today.toString() })
                    IconButton(onClick = { shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(Modifier.clip(CircleShape).background(MintTheme.card).padding(3.dp)) {
                        CalMode.entries.forEach { m ->
                            val sel = m == mode
                            Text(
                                stringResource(when (m) { CalMode.MONTH -> R.string.mode_month; CalMode.WEEK -> R.string.mode_week; CalMode.DAY -> R.string.mode_day }),
                                modifier = Modifier.clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { mode = m }.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (mode != CalMode.DAY) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MintTheme.card).padding(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0..6) {
                                val d = first.plus(i.toLong())
                                Text(ctx.weekdayShort(d.value), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val rows = if (mode == CalMode.MONTH) 6 else 1
                        for (r in 0 until rows) {
                            Row(Modifier.fillMaxWidth()) {
                                for (c in 0..6) {
                                    val d = gridStart.plusDays((r * 7 + c).toLong())
                                    DayCell(
                                        d, selected = d == selected, today = d == today,
                                        inMonth = mode != CalMode.MONTH || d.month == selected.month,
                                        marks = marks[d], modifier = Modifier.weight(1f),
                                    ) { selectedStr = d.toString() }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.monthDayWeekday(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.day_counts, items.count { it is CourseItem }, items.count { it is EventItem }, items.count { it is TaskItem || it is BlockItem }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                item { Text(stringResource(R.string.day_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
            }
            items(items, key = { agendaKey(it) }) { item ->
                AgendaRow(
                    item, now, settings.showTeacher,
                    onOpenCourse = { nav.push(Route.CourseEdit(it)) },
                    onOpenEvent = { nav.push(Route.EventEdit(it)) },
                    onOpenTask = { nav.push(Route.TaskDetail(it)) },
                    onToggleTask = { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                )
            }
        }
        FloatingActionButton(
            onClick = { nav.push(Route.EventEdit(null, selected.toString())) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(22.dp),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.new_event)) }
    }
}

private fun agendaKey(i: app.mintcal.core.agenda.AgendaItem): String = when (i) {
    is CourseItem -> "c-${i.occ.course.id}-${i.occ.date}-${i.occ.startPeriod}"
    is EventItem -> "e-${i.event.id}-${i.start}"
    is TaskItem -> "t-${i.task.id}"
    is BlockItem -> "b-${i.task.id}"
}

@Composable
private fun DayCell(
    d: LocalDate, selected: Boolean, today: Boolean, inMonth: Boolean, marks: app.mintcal.core.agenda.DayMarks?,
    modifier: Modifier, onClick: () -> Unit,
) {
    Column(modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val bg = when { selected -> MaterialTheme.colorScheme.primary; else -> Color.Transparent }
        val fg = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            today -> MaterialTheme.colorScheme.primary
            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text("${d.dayOfMonth}", color = fg, fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (marks != null) {
                if (marks.courses > 0) Dot(MaterialTheme.colorScheme.primary)
                if (marks.events > 0) Dot(MaterialTheme.colorScheme.tertiary)
                if (marks.tasks > 0) Dot(MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}
