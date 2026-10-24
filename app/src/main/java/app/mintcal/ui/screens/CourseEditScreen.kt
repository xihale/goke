package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Conflicts
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.model.Course
import app.mintcal.core.model.Source
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull

@Composable
fun CourseEditScreen(
    id: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val sem = snap.currentSemester
    if (sem == null) {
        // 没有学期数据：composition 期间不能改导航状态，延后到 effect 里返回
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val existing = remember(id) { id?.let { i -> snap.courses.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        // 记录已被删除：延后返回
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // 新建时自动聚焦名称
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) nameFocus.requestFocus() }
    val periodCount = sem.periods.size

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(existing?.teacher.orEmpty()) }
    var room by remember { mutableStateOf(existing?.room.orEmpty()) }
    var dow by remember { mutableIntStateOf(existing?.dayOfWeek ?: 1) }
    var sp by remember { mutableIntStateOf(existing?.startPeriod ?: 1) }
    var ep by remember { mutableIntStateOf(existing?.endPeriod ?: 2) }
    var weeksText by remember { mutableStateOf(WeekSets.format(existing?.weeks ?: WeekSets.all(sem.totalWeeks))) }
    var color by remember { mutableIntStateOf(existing?.colorIndex ?: (snap.courses.size % 8)) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val weeks = remember(weeksText) { WeekSets.parse(weeksText, sem.totalWeeks).filter { it >= 1 }.toSet() }
    val valid = name.isNotBlank() && weeks.isNotEmpty() && ep >= sp

    fun build(): Course =
        (existing ?: Course(Ids.next(), sem.id, "", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = emptySet(), source = Source.MANUAL)).copy(
            name = name.trim(),
            teacher = teacher.trim(),
            room = room.trim(),
            dayOfWeek = dow,
            startPeriod = sp,
            endPeriod = ep,
            weeks = weeks,
            colorIndex = color,
            remindMinutes = remind,
            note = note,
        )
    val preview =
        remember(name, teacher, room, dow, sp, ep, weeks, color, remind, note) {
            if (!valid) {
                null
            } else {
                val c = build()
                Conflicts.detect(snap.apply(Edits.save(snap, c, "", 0), 0), setOf(c.id))
            }
        }

    MintScreen(
        title = stringResource(if (existing == null) R.string.new_course else R.string.edit_course),
        onBack = onBack,
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
            Button(
                onClick = {
                    val c = build()
                    actions.commit(Edits.save(snap, c, ctx.getString(if (existing == null) R.string.add_course_desc else R.string.edit_course_desc, c.name), System.currentTimeMillis()), ctx.getString(R.string.saved_toast))
                    onBack()
                },
                enabled = valid,
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldText(stringResource(R.string.label_course_name), name, { name = it }, modifier = Modifier.focusRequester(nameFocus))
            FieldText(stringResource(R.string.label_room), room, { room = it })
            FieldText(stringResource(R.string.label_teacher), teacher, { teacher = it })

            FormSection(stringResource(R.string.section_time)) {
                Text(stringResource(R.string.weekday_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice((1..7).map { it to ctx.weekdayFull(it) }, dow, { dow = it })
                Spacer(Modifier.height(8.dp))
                Stepper(stringResource(R.string.start_period), sp, 1, periodCount) {
                    sp = it
                    if (ep < sp) ep = sp
                }
                Stepper(stringResource(R.string.end_period), ep, sp, periodCount) { ep = it }
                val cal = remember(sem) { SemesterCalendar(sem) }
                Text(
                    "${cal.startTime(sp)?.toString().orEmpty()}–${cal.endTime(ep)?.toString().orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormSection(stringResource(R.string.section_weeks)) {
                FieldText(stringResource(R.string.label_weeks), weeksText, { weeksText = it }, placeholder = stringResource(R.string.weeks_hint))
                ChipChoice(
                    listOf(
                        "all" to stringResource(R.string.weeks_all),
                        "odd" to stringResource(R.string.weeks_odd),
                        "even" to stringResource(R.string.weeks_even),
                    ),
                    "",
                    {
                        weeksText =
                            WeekSets.format(
                                when (it) {
                                    "odd" -> WeekSets.odd(sem.totalWeeks)
                                    "even" -> WeekSets.even(sem.totalWeeks)
                                    else -> WeekSets.all(sem.totalWeeks)
                                },
                            )
                    },
                )
                Text(
                    if (weeks.isEmpty()) stringResource(R.string.weeks_invalid) else stringResource(R.string.weeks_total, weeks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (weeks.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormSection(stringResource(R.string.section_more)) {
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(color) { color = it }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_remind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(
                    listOf<Pair<Int?, String>>(null to stringResource(R.string.remind_default), 5 to stringResource(R.string.remind_5m), 10 to stringResource(R.string.remind_10m), 15 to stringResource(R.string.remind_15m), 30 to stringResource(R.string.remind_30m)),
                    remind,
                    { remind = it },
                )
                Spacer(Modifier.height(8.dp))
                FieldText(stringResource(R.string.label_note), note, { note = it }, singleLine = false, minLines = 2)
            }
            if (!preview.isNullOrEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(20.dp),
                        ).background(MintTheme.colors.conflictBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.conflict_title), color = MintTheme.colors.removed, style = MaterialTheme.typography.labelLarge)
                    preview.take(4).forEach { Text(stringResource(R.string.conflict_line, it.aTitle, it.bTitle, it.start.toLocalDate().toString()), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete_course_title, existing.name),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                actions.commit(Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.name), System.currentTimeMillis()), ctx.getString(R.string.deleted_toast))
                onBack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

@Composable
fun ColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MintTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until 8) {
            val sel = i == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.bg(i))
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) colors.fg(i) else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { if (sel) Icon(Icons.Rounded.Check, null, tint = colors.fg(i), modifier = Modifier.size(18.dp)) }
        }
    }
}
