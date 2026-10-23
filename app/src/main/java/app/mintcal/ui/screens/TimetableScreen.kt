package app.mintcal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mintcal.R
import app.mintcal.core.agenda.CellKind
import app.mintcal.core.agenda.TimetableCell
import app.mintcal.core.agenda.TimetableDiff
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.ExceptionType
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.rememberNow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull
import app.mintcal.ui.weekdayShort
import java.time.LocalDate
import kotlinx.coroutines.launch

private val TimeColW = 34.dp
private val RowH = 60.dp
private val GroupGap = 14.dp
private val HeaderH = 46.dp

private fun groupBoundaries(periodCount: Int) = listOf(4, 8).filter { it < periodCount }

private fun periodTop(p: Int, periodCount: Int): Dp {
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
    val sem = snap.currentSemester ?: return
    val cal = remember(sem) { SemesterCalendar(sem) }
    val today = now.toLocalDate()
    val currentWeek = cal.weekOf(today)
    val startPage = (currentWeek.coerceIn(1, sem.totalWeeks)) - 1
    val pager = rememberPagerState(initialPage = startPage, pageCount = { sem.totalWeeks })
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var selected by remember { mutableStateOf<TimetableCell?>(null) }

    // 「在课表上预览」：把 Dry Run 变更集应用到快照副本上，课表叠加显示新增 / 修改 / 删除
    val previewCs = if (adv.previewOnTimetable) adv.changeSet else null
    val afterSnap = remember(previewCs, snap) { previewCs?.let { snap.apply(it, 0).live() } }

    // 预览开启时跳到第一个有变化的周
    LaunchedEffect(previewCs?.id) {
        if (afterSnap != null) {
            val firstWeek = (1..sem.totalWeeks).firstOrNull { w -> TimetableDiff.week(snap, afterSnap, w).any { it.kind != CellKind.NORMAL } }
            if (firstWeek != null) pager.animateScrollToPage(firstWeek - 1)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ctx.getString(R.string.timetable_title, sem.name.take(14), pager.currentPage + 1),
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val w = pager.currentPage + 1
                Text(
                    "${ctx.getString(R.string.fmt_range_md, cal.weekStart(w).monthValue, cal.weekStart(w).dayOfMonth, cal.weekEnd(w).monthValue, cal.weekEnd(w).dayOfMonth)}" +
                        if (w == currentWeek) " · ${stringResource(R.string.this_week)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pager.currentPage + 1 != currentWeek && currentWeek in 1..sem.totalWeeks) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(currentWeek - 1) } }) { Text(stringResource(R.string.back_to_this_week)) }
            }
            IconButton(onClick = { nav.push(Route.EditTimetable) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_timetable)) }
            IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        }

        if (previewCs != null) PreviewBanner(previewCs, onApply = { app.advanced.apply { actions.toast(ctx.getString(R.string.applied_toast, it.changes.size)) } }, onDiscard = { app.advanced.discard() })

        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { it }) { page ->
            val week = page + 1
            val cells = remember(snap, afterSnap, week) { TimetableDiff.week(snap, afterSnap, week) }
            WeekGrid(sem, cal, week, today, cells, settings.showTeacher, scroll, onClick = { selected = it })
        }
        Spacer(Modifier.height(86.dp))
    }

    selected?.let { cell ->
        CourseSheet(cell, snap, sem, onDismiss = { selected = null })
    }
}

@Composable
private fun PreviewBanner(cs: ChangeSet, onApply: () -> Unit, onDiscard: () -> Unit) {
    val colors = MintTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.preview_mode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("+${cs.addCount}", color = colors.added, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("~${cs.modifyCount}", color = colors.onModified, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("−${cs.removeCount}", color = colors.removed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
        Button(onClick = onApply, enabled = !cs.isEmpty) { Text(stringResource(R.string.apply_changes)) }
    }
}

@Composable
private fun WeekGrid(
    sem: Semester, cal: SemesterCalendar, week: Int, today: LocalDate, cells: List<TimetableCell>,
    showTeacher: Boolean, scroll: androidx.compose.foundation.ScrollState, onClick: (TimetableCell) -> Unit,
) {
    val ctx = LocalContext.current
    val periods = sem.periods.sortedBy { it.index }
    val n = periods.size
    val totalH = periodTop(n, n) + RowH
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        // 星期头
        Row(Modifier.fillMaxWidth().height(HeaderH).padding(start = TimeColW)) {
            for (d in 1..7) {
                val date = cal.dateOf(week, d)
                val isToday = date == today
                Column(
                    Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(ctx.weekdayShort(d), style = MaterialTheme.typography.labelMedium, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 10.sp, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(totalH)) {
                val colW = (maxWidth - TimeColW) / 7
                // 左侧节次
                periods.forEach { p ->
                    Column(
                        Modifier.absoluteOffset(0.dp, periodTop(p.index, n)).width(TimeColW).height(RowH),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${p.index}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(p.start.hm(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 今日列高亮
                val todayDow = if (cal.weekOf(today) == week) today.dayOfWeek.value else null
                if (todayDow != null) {
                    Box(
                        Modifier.absoluteOffset(TimeColW + colW * (todayDow - 1), 0.dp).width(colW).height(totalH)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
                    )
                }
                // 组间分隔（上午 / 下午 / 晚上之间的留白）
                groupBoundaries(n).forEach { b ->
                    Box(Modifier.absoluteOffset(TimeColW, periodTop(b, n) + RowH + GroupGap / 2).width(maxWidth - TimeColW).height(1.dp).background(outline))
                }
                // 课程块
                cells.forEach { cell ->
                    val o = cell.occ
                    val top = periodTop(o.startPeriod.coerceAtMost(n), n) + 2.dp
                    val bottom = periodTop(o.endPeriod.coerceIn(o.startPeriod, n), n) + RowH - 2.dp
                    CourseBlock(
                        cell, showTeacher,
                        Modifier.absoluteOffset(TimeColW + colW * (o.date.dayOfWeek.value - 1) + 1.5.dp, top)
                            .width(colW - 3.dp).height(bottom - top),
                        onClick = { onClick(cell) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(cell: TimetableCell, showTeacher: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val o = cell.occ
    val colors = MintTheme.colors
    val bg = colors.bg(o.course.colorIndex)
    val fg = colors.fg(o.course.colorIndex)
    val borderColor = when (cell.kind) {
        CellKind.ADDED -> colors.added
        CellKind.MODIFIED -> colors.modified
        CellKind.REMOVED -> colors.removed
        CellKind.NORMAL -> null
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.alpha(if (cell.kind == CellKind.REMOVED) 0.6f else 1f).clip(shape)
            .background(if (cell.kind == CellKind.REMOVED) colors.conflictBg else bg)
            .let { if (borderColor != null) it.border(2.dp, borderColor, shape) else it }
            .clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        val strike = if (cell.kind == CellKind.REMOVED) TextDecoration.LineThrough else null
        Text(
            o.course.name, color = if (cell.kind == CellKind.REMOVED) colors.removed else fg, fontSize = 11.sp, lineHeight = 13.sp,
            fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis, textDecoration = strike,
        )
        if (o.room.isNotBlank()) Text("@" + o.room, color = (if (cell.kind == CellKind.REMOVED) colors.removed else fg).copy(alpha = 0.85f), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = strike)
        if (showTeacher && o.course.teacher.isNotBlank()) Text(o.course.teacher, color = fg.copy(alpha = 0.75f), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (o.rescheduled) Text(stringResource(R.string.rescheduled), color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CourseSheet(cell: TimetableCell, snap: Snapshot, sem: Semester, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val o = cell.occ
    val c = o.course
    val cal = remember(sem) { SemesterCalendar(sem) }
    var reschedule by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${ctx.weekdayShort(o.date.dayOfWeek.value).let { stringResource(R.string.weekday_prefix, it) }} ${cal.startTime(o.startPeriod)?.hm().orEmpty()}–${cal.endTime(o.endPeriod)?.hm().orEmpty()} · ${stringResource(R.string.periods_range, o.startPeriod, o.endPeriod)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (o.room.isNotBlank()) Text(stringResource(R.string.label_room_value, o.room), style = MaterialTheme.typography.bodyMedium)
            if (c.teacher.isNotBlank()) Text(stringResource(R.string.label_teacher_value, c.teacher), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.label_weeks_value, WeekSets.format(c.weeks)), style = MaterialTheme.typography.bodyMedium)
            if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (cell.kind == CellKind.NORMAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDismiss(); nav.push(Route.CourseEdit(c.id)) }) { Text(stringResource(R.string.edit)) }
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
        RescheduleDialog(c, o.date, cal, snap, onDismiss = { reschedule = false; onDismiss() })
    }
}

/** 课程的「原始周次」：调课后课程出现在别的周，需要找回对应的原周。 */
private fun origWeek(snap: Snapshot, c: Course, date: LocalDate, cal: SemesterCalendar): Int {
    val shown = cal.weekOf(date)
    if (shown in c.weeks) return shown
    return snap.exceptions.firstOrNull { it.courseId == c.id && it.type == ExceptionType.RESCHEDULE && (it.newWeek ?: it.week) == shown }?.week ?: shown
}

@Composable
private fun RescheduleDialog(c: Course, date: LocalDate, cal: SemesterCalendar, snap: Snapshot, onDismiss: () -> Unit) {
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
fun Stepper(label: String, value: Int, min: Int, max: Int, display: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Text("−") }
        Text(display(value), Modifier.defaultMinSize(minWidth = 48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        TextButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

