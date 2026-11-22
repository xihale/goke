package app.goke.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.NextUps
import app.goke.core.agenda.TaskItem
import app.goke.core.change.Edits
import app.goke.core.model.EventKind
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.time.SemesterCalendar
import app.goke.core.todo.Bucket
import app.goke.core.todo.SmartList
import app.goke.core.todo.TaskLogic
import app.goke.ui.LocalActions
import app.goke.ui.LocalNav
import app.goke.ui.Route
import app.goke.ui.Tab
import app.goke.ui.components.AgendaRow
import app.goke.ui.components.EmptyState
import app.goke.ui.components.EventDetailSheet
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintFab
import app.goke.ui.components.NavClearance
import app.goke.ui.components.NavListInset
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.TaskRow
import app.goke.ui.components.mintClickable
import app.goke.ui.components.parcelCode
import app.goke.ui.dueLabel
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.liveSnapshot
import app.goke.ui.monthDayWeekday
import app.goke.ui.reduceMotion
import app.goke.ui.rememberNowGated
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import java.time.LocalDateTime

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    val now = rememberNowGated()
    val today = now.toLocalDate()
    val items = remember(snap, today, settings.defaultBlockMinutes) { Agenda.forDay(snap, today, settings.defaultBlockMinutes) }
    val overdue = remember(snap, now) { TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now) }
    // 待取快递：未完成、不带父任务，靠来源或取件码认出来（与桌面小组件同一判定）
    val parcels =
        remember(snap) {
            snap.tasks.filter { t ->
                !t.done && t.parentId == null && t.deletedAt == null && (t.source == Source.PARCEL || t.parcelCode() != null)
            }
        }
    val parcelIds = remember(parcels) { parcels.map { it.id }.toSet() }
    // 逾期区已经列出的那条，时间线里不再重复一次
    val overdueIds = remember(overdue) { overdue.map { it.id }.toSet() }
    val timeline =
        remember(items, overdueIds, parcelIds) {
            items.filterNot {
                // 逾期区、待取快递区已经列过的那条（有没有排时间块都一样）不再出现在时间线里
                when (it) {
                    is TaskItem -> it.task.id in overdueIds || it.task.id in parcelIds
                    is BlockItem -> it.task.id in overdueIds || it.task.id in parcelIds
                    else -> false
                }
            }
        }
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
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }

    // 做完的收在后面一栏，默认折叠：时间线只留还没做的
    fun isDone(i: app.goke.core.agenda.AgendaItem): Boolean = (i is TaskItem && i.task.done) || (i is BlockItem && i.task.done)
    val pendingItems = remember(timeline) { timeline.filterNot { isDone(it) } }
    val doneItems = remember(timeline) { timeline.filter { isDone(it) } }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val todayTaskIds =
        remember(items) {
            items
                .mapNotNull {
                    when (it) {
                        is TaskItem -> it.task.id
                        is BlockItem -> it.task.id
                        else -> null
                    }
                }.toSet()
        }
    val doneTaskIds =
        remember(items) {
            items
                .mapNotNull {
                    when {
                        it is TaskItem && it.task.done -> it.task.id
                        it is BlockItem && it.task.done -> it.task.id
                        else -> null
                    }
                }.toSet()
        }
    val courseCount = items.count { it is CourseItem }
    val eventCount = items.count { it is EventItem }
    // 今天的 + 已逾期的都算「手上的待办」，按任务 ID 去重，进度环和文案用同一个基数
    val taskTotal = (todayTaskIds + overdueIds).size
    val taskDone = doneTaskIds.size
    val taskProgress = if (taskTotal == 0) 0f else taskDone.toFloat() / taskTotal
    val doneCount = items.count { (it is CourseItem && it.occ.end.isBefore(now)) || (it is EventItem && !it.allDay && it.end.isBefore(now)) || (it is TaskItem && it.task.done) || (it is BlockItem && it.task.done) }
    val nextUp = remember(snap, now) { NextUps.find(snap, now) }
    val nextToday =
        remember(items, now) {
            items.firstOrNull {
                (it is CourseItem && it.occ.end.isAfter(now)) ||
                    (it is EventItem && !it.allDay && it.end.isAfter(now)) ||
                    (it is BlockItem && !it.task.done && it.end.isAfter(now))
            }
        }
    // 完成判定含已下课的课；进度环只计待办。没有安排时不画这张卡片。
    val allDone = items.isNotEmpty() && doneCount == items.size && overdue.isEmpty()
    val dayEmpty = timeline.isEmpty() && overdue.isEmpty() && parcels.isEmpty()
    // 摘要条：课程、日程与待办全为 0 就不画（今天真的没事，交给空状态）
    val dayQuiet = courseCount == 0 && eventCount == 0 && taskTotal == 0

    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(nav.reselect, reduce) {
        if (nav.reselect > 0 && nav.tab == Tab.TODAY) {
            if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = NavListInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ctx.monthDayWeekday(today), style = MaterialTheme.typography.titleLarge)
                        Text(weekLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!dayQuiet) {
                item {
                    // 一行摘要：左边一句话报数 + 下一节，右边只有进度环；不再重复「待办 x/y」
                    MintCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val remaining = (taskTotal - taskDone).coerceAtLeast(0)
                                val summaryText =
                                    if (allDone) {
                                        stringResource(R.string.today_all_done_title)
                                    } else {
                                        val classPart =
                                            if (courseCount == 0) {
                                                stringResource(R.string.today_no_classes)
                                            } else {
                                                stringResource(R.string.today_summary, courseCount, taskTotal).substringBefore(" · ")
                                            }
                                        val remainingPart =
                                            if (remaining > 0) {
                                                stringResource(R.string.today_summary_remaining, remaining)
                                            } else {
                                                null
                                            }
                                        listOfNotNull(classPart, remainingPart).joinToString(" · ")
                                    }
                                AnimatedContent(
                                    summaryText,
                                    transitionSpec = {
                                        (fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 3 }) togetherWith
                                            (fadeOut(fadeSpec) + slideOutVertically(slideSpec) { -it / 3 })
                                    },
                                    label = "today-summary",
                                ) { t ->
                                    Text(t, style = MaterialTheme.typography.titleMedium)
                                }
                                val sep = stringResource(R.string.list_sep)
                                val nextLine =
                                    when (nextToday) {
                                        is CourseItem -> {
                                            val o = nextToday.occ
                                            stringResource(R.string.today_next_class, o.start.hm(), o.course.name, ctx.formatRooms(o.room)).trim()
                                        }

                                        is EventItem -> {
                                            val e = nextToday.event
                                            val kindPrefix = if (e.kind == EventKind.EXAM) stringResource(R.string.kind_exam) + " · " else ""
                                            val roomPart =
                                                ctx
                                                    .formatRooms(e.location)
                                                    .takeIf { it.isNotBlank() }
                                                    ?.let { " · $it" }
                                                    .orEmpty()
                                            "${stringResource(R.string.widget_next_label)} · $kindPrefix${nextToday.start.hm()} ${e.title}$roomPart"
                                        }

                                        is BlockItem -> {
                                            val t = nextToday.task
                                            val kindPrefix = if (t.extra["kind"] == EventKind.EXAM.name) stringResource(R.string.kind_exam) + " · " else ""
                                            val locPart =
                                                t.extra["location"]
                                                    ?.let { ctx.formatRooms(it) }
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?.let { " · $it" }
                                                    .orEmpty()
                                            "${stringResource(R.string.widget_next_label)} · $kindPrefix${nextToday.start.hm()} ${t.title}$locPart"
                                        }

                                        else -> {
                                            nextUp?.occurrence?.let { o ->
                                                stringResource(
                                                    R.string.today_next_class,
                                                    ctx.dueLabel(o.start, true, today),
                                                    o.course.name,
                                                    ctx.formatRooms(o.room),
                                                ).trim()
                                            }
                                        }
                                    }
                                val dueBeforeClass =
                                    nextUp
                                        ?.takeIf { up ->
                                            up.tasks.isNotEmpty() &&
                                                (nextToday == null || (nextToday is CourseItem && nextToday.occ.course.id == up.occurrence.course.id && nextToday.occ.date == up.occurrence.date) || up.occurrence.date == today)
                                        }?.tasks
                                        ?.joinToString(sep) { it.title }
                                        ?.let { stringResource(R.string.remind_course_tasks, it) }
                                if (nextLine != null) {
                                    Text(
                                        nextLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (dueBeforeClass != null) {
                                    Text(
                                        dueBeforeClass,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (taskTotal > 0) {
                                ProgressRing(
                                    taskProgress,
                                    "$taskDone/$taskTotal",
                                    stringResource(R.string.today_task_progress, taskDone, taskTotal),
                                    Modifier.size(44.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (parcels.isNotEmpty()) {
                item(key = "h-parcel") { SectionLabel(stringResource(R.string.parcel_pickup_section), trailing = parcels.size.toString()) }
                items(parcels, key = { "p-" + it.id }) { t ->
                    TaskRow(
                        task = t,
                        onToggle = { task, done ->
                            toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title))
                        },
                        onOpen = { nav.openTask(it) },
                        subtitle = t.due?.let { ctx.dueLabel(it, t.dueHasTime, now.toLocalDate()) },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = MintTheme.spec(),
                                placementSpec = MintTheme.spec(),
                                fadeOutSpec = MintTheme.spec(),
                            ),
                    )
                }
            }
            if (overdue.size == 1) {
                item(key = "h-overdue") { SectionLabel(stringResource(R.string.bucket_overdue), trailing = overdue.size.toString(), trailingColor = MintTheme.colors.removed) }
                items(overdue, key = { "od-" + it.id }) { t ->
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurfaceVariant = MintTheme.colors.removed)) {
                        TaskRow(
                            t,
                            { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                            { nav.openTask(it) },
                            subtitle = t.due?.let { ctx.getString(R.string.overdue_since, it.toLocalDate().toString().substring(5)) },
                            modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
                        )
                    }
                }
            } else if (overdue.size >= 2) {
                item(key = "overdue-banner") {
                    MintCard(color = MintTheme.colors.conflictBg) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.overdue_count, overdue.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MintTheme.colors.removed,
                                )
                                Text(
                                    stringResource(R.string.today_overdue_tidy_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = {
                                    nav.openTodoTidy = true
                                    nav.select(Tab.TODO)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MintTheme.colors.removed),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.today_overdue_tidy_action), maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (pendingItems.isNotEmpty()) {
                item(key = "h-timeline") { SectionLabel(stringResource(R.string.timeline), trailing = pendingItems.size.toString()) }
            }
            items(pendingItems, key = { itemKey(it) }, contentType = { agendaType(it) }) { item ->
                AgendaRow(
                    item,
                    now,
                    settings.showTeacher,
                    onOpenCourse = { nav.push(Route.CourseEdit(it)) },
                    onOpenEvent = { selectedEventId = it },
                    onOpenTask = { nav.openTask(it) },
                    onToggleTask = { task, done -> toggle(actions, snap, task, done, now, ctx.getString(R.string.done_toast, task.title)) },
                    modifier = Modifier.animateItem(fadeInSpec = MintTheme.spec(), placementSpec = MintTheme.spec(), fadeOutSpec = MintTheme.spec()),
                )
            }
            if (doneItems.isNotEmpty()) {
                item(key = "h-done") {
                    val expansion = stringResource(if (showDone) R.string.state_expanded else R.string.state_collapsed)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .mintClickable(pressedScale = 0.98f) { showDone = !showDone }
                            .semantics { stateDescription = expansion }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.done_count, doneItems.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val rot by animateFloatAsState(if (showDone) 0f else -90f, MintTheme.spec(), label = "done-rot")
                        Icon(
                            Icons.Rounded.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot },
                        )
                    }
                }
                if (showDone) {
                    items(doneItems, key = { itemKey(it) }, contentType = { agendaType(it) }) { item ->
                        val task = if (item is TaskItem) item.task else (item as BlockItem).task
                        val doneMessage = stringResource(R.string.done_toast, task.title)
                        TaskRow(
                            task,
                            { t, done -> toggle(actions, snap, t, done, now, doneMessage) },
                            { nav.openTask(it) },
                        )
                    }
                }
            }
            if (dayEmpty) {
                item(key = "empty") {
                    // 空状态放视口中间，而不是顶着标题留一大片空白
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.today_empty_title),
                            hint = null,
                            modifier = Modifier.padding(top = 16.dp),
                            icon = Icons.Rounded.WbSunny,
                            actionLabel = stringResource(R.string.shortcut_add_task),
                            onAction = { quickAddOpen = true },
                        )
                    }
                }
            }
        }
        MintFab(
            visible = !quickAddOpen && !dayEmpty,
            onClick = { quickAddOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = NavClearance),
        )
        QuickAddSheet(
            snap = snap,
            now = now,
            listFilter = null,
            defaultBucket = Bucket.TODAY,
            focusSignal = 0,
            visible = quickAddOpen,
            onDismiss = { quickAddOpen = false },
            anchorDate = today,
            acceptShare = false,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(),
        )
        EventDetailSheet(
            event = selectedEventId?.let { id -> snap.events.firstOrNull { it.id == id } },
            snap = snap,
            onDismiss = { selectedEventId = null },
        )
    }
}

private fun itemKey(i: app.goke.core.agenda.AgendaItem): String =
    when (i) {
        is CourseItem -> "c-${i.occ.course.id}-${i.occ.date}-${i.occ.startPeriod}"
        is EventItem -> "e-${i.event.id}-${i.start}"
        is TaskItem -> "t-${i.task.id}"
        is BlockItem -> "b-${i.task.id}"
    }

/** 异构行的复用类型：滚动时同类型行直接复用组合，不跨类型重建。 */
private fun agendaType(i: app.goke.core.agenda.AgendaItem): String =
    when (i) {
        is CourseItem -> "course"
        is EventItem -> "event"
        is TaskItem -> "task"
        is BlockItem -> "block"
    }

fun toggle(
    actions: app.goke.ui.Actions,
    snap: app.goke.core.model.Snapshot,
    task: app.goke.core.model.Task,
    done: Boolean,
    now: LocalDateTime,
    msg: String,
) {
    val desc = if (done) msg else task.title
    val cs = Edits.toggleTask(snap, task, done, System.currentTimeMillis(), now, desc)
    actions.commit(cs, desc)
}

@Composable
fun ProgressRing(
    progress: Float,
    center: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), MintTheme.spec(), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val bar = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(bar, -90f, 360f * p.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
