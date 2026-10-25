package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.change.Edits
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.todo.SmartList
import app.mintcal.core.todo.TaskLogic
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.Tab
import app.mintcal.ui.components.AgendaRow
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.TaskRow
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDateTime

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now by rememberNow()
    val today = now.toLocalDate()
    val items = remember(snap, today, settings.defaultBlockMinutes) { Agenda.forDay(snap, today, settings.defaultBlockMinutes) }
    val overdue = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now) }
    val sem = snap.currentSemester
    val weekLabel =
        remember(sem, today) {
            sem
                ?.let {
                    val w = SemesterCalendar(it).weekOf(today)
                    when {
                        w < 1 -> ctx.getString(R.string.semester_not_started)
                        w > it.totalWeeks -> ctx.getString(R.string.semester_ended)
                        else -> ctx.getString(R.string.week_n, w)
                    }
                }.orEmpty()
        }
    var menu by remember { mutableStateOf(false) }

    val courseCount = items.count { it is CourseItem }
    val taskTotal = items.count { it is TaskItem || it is BlockItem }
    val doneCount = items.count { (it is CourseItem && it.occ.end.isBefore(now)) || (it is EventItem && !it.allDay && it.end.isBefore(now)) || (it is TaskItem && it.task.done) || (it is BlockItem && it.task.done) }
    val progress = if (items.isEmpty()) 0f else doneCount.toFloat() / items.size
    val next = items.firstOrNull { it is CourseItem && it.occ.end.isAfter(now) } as? CourseItem

    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    }
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ctx.monthDayWeekday(today), style = MaterialTheme.typography.headlineMedium)
                    Text(weekLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.add)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_event)) }, onClick = {
                            menu = false
                            nav.push(Route.EventEdit(null, today.toString()))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_task)) }, onClick = {
                            menu = false
                            nav.push(Route.TaskDetail(null))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_course)) }, onClick = {
                            menu = false
                            nav.push(Route.CourseEdit(null))
                        })
                    }
                }
                IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            }
        }
        item {
            MintCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress, Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            if (items.isEmpty()) stringResource(R.string.today_empty_title) else stringResource(R.string.today_summary, courseCount, taskTotal),
                            transitionSpec = {
                                (fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 3 }) togetherWith
                                    (fadeOut(fadeSpec) + slideOutVertically(slideSpec) { -it / 3 })
                            },
                            label = "today-summary",
                        ) { t ->
                            Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        AnimatedContent(
                            next,
                            transitionSpec = {
                                (fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 3 }) togetherWith
                                    (fadeOut(fadeSpec) + slideOutVertically(slideSpec) { -it / 3 })
                            },
                            label = "today-next",
                        ) { n ->
                            Text(
                                if (n != null) {
                                    stringResource(R.string.today_next_class, n.occ.start.hm(), n.occ.course.name, n.occ.room)
                                } else {
                                    stringResource(R.string.today_no_more_class)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item(key = "h-overdue") { SectionLabel(stringResource(R.string.overdue_count, overdue.size), trailingColor = MintTheme.colors.removed) }
            items(overdue, key = { "od-" + it.id }) { t ->
                TaskRow(
                    t,
                    { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                    { nav.openTask(it) },
                    subtitle = t.due?.let { ctx.getString(R.string.overdue_since, it.toLocalDate().toString().substring(5)) },
                    modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
                )
            }
        }
        if (items.isNotEmpty()) item(key = "h-timeline") { SectionLabel(stringResource(R.string.timeline)) }
        items(items, key = { itemKey(it) }, contentType = { agendaType(it) }) { item ->
            AgendaRow(
                item,
                now,
                settings.showTeacher,
                onOpenCourse = { nav.push(Route.CourseEdit(it)) },
                onOpenEvent = { nav.push(Route.EventEdit(it)) },
                onOpenTask = { nav.openTask(it) },
                onToggleTask = { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
            )
        }
        if (items.isEmpty() && overdue.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.today_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.FilledTonalButton(onClick = { nav.tab = Tab.TODO }) { Text(stringResource(R.string.go_todo)) }
                }
            }
        }
    }
}

private fun itemKey(i: app.mintcal.core.agenda.AgendaItem): String =
    when (i) {
        is CourseItem -> "c-${i.occ.course.id}-${i.occ.date}-${i.occ.startPeriod}"
        is EventItem -> "e-${i.event.id}-${i.start}"
        is TaskItem -> "t-${i.task.id}"
        is BlockItem -> "b-${i.task.id}"
    }

/** 异构行的复用类型：滚动时同类型行直接复用组合，不跨类型重建。 */
private fun agendaType(i: app.mintcal.core.agenda.AgendaItem): String =
    when (i) {
        is CourseItem -> "course"
        is EventItem -> "event"
        is TaskItem -> "task"
        is BlockItem -> "block"
    }

fun toggle(
    actions: app.mintcal.ui.Actions,
    snap: app.mintcal.core.model.Snapshot,
    task: app.mintcal.core.model.Task,
    done: Boolean,
    now: LocalDateTime,
    msg: String,
) {
    val cs = Edits.toggleTask(snap, task, done, System.currentTimeMillis(), now, if (done) msg else task.title)
    actions.commit(cs, if (done) msg else null)
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val p by animateFloatAsState(progress, MintTheme.spec(), label = "ring")
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val bar = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(bar, -90f, 360f * p, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        AnimatedContent(
            targetState = (p * 100).toInt(),
            transitionSpec = {
                (slideInVertically(slideSpec) { it / 2 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutVertically(slideSpec) { -it / 2 } + fadeOut(fadeSpec))
            },
            label = "ring-pct",
        ) { pct ->
            Text("$pct%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
