package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.time.WeekSets
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.fullDate
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun EditTimetableScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val sem =
        snap.currentSemester ?: run {
            onBack()
            return
        }

    var name by remember(sem.id) { mutableStateOf(sem.name) }
    var week1 by remember(sem.id) { mutableStateOf(sem.week1Monday) }
    var total by remember(sem.id) { mutableIntStateOf(sem.totalWeeks) }
    var pickDate by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var copyDlg by remember { mutableStateOf(false) }

    // 离开页面时自动保存学期信息（有改动才写）
    val latest by rememberUpdatedState(Triple(name, week1, total))
    val semNow by rememberUpdatedState(sem)
    val snapNow by rememberUpdatedState(snap)
    DisposableEffect(sem.id) {
        onDispose {
            val (n, w, t) = latest
            val s = semNow
            if (n.isNotBlank() && (n != s.name || w != s.week1Monday || t != s.totalWeeks)) {
                val upd = s.copy(name = n.trim(), week1Monday = w.with(java.time.DayOfWeek.MONDAY), totalWeeks = t)
                actions.commit(Edits.save(snapNow, upd, ctx.getString(R.string.edit_semester_desc), System.currentTimeMillis()))
            }
        }
    }

    val courses =
        remember(snap, sem) {
            snap.courses.filter { it.semesterId == sem.id }.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name }))
        }

    MintScreen(title = stringResource(R.string.edit_timetable), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormSection(stringResource(R.string.section_semester)) {
                FieldText(stringResource(R.string.label_semester_name), name, { name = it })
                ValueRow(stringResource(R.string.label_week1_monday), ctx.fullDate(week1), { pickDate = true })
                Stepper(stringResource(R.string.label_total_weeks), total, 1, 30, { ctx.getString(R.string.week_n_total, it) }) { total = it }
                val weeksNow = ChronoUnit.WEEKS.between(week1.with(java.time.DayOfWeek.MONDAY), LocalDate.now()) + 1
                Text(
                    ctx.getString(R.string.semester_progress_hint, weeksNow.coerceIn(0, 99).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(stringResource(R.string.section_courses), trailing = ctx.getString(R.string.courses_count, courses.size))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                courses.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MintTheme.card)
                            .clickable { nav.push(Route.CourseEdit(c.id)) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(MintTheme.colors.fg(c.colorIndex)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                ctx.getString(R.string.course_line, ctx.weekdayShort(c.dayOfWeek), c.startPeriod, c.endPeriod, WeekSets.format(c.weeks)) +
                                    c.room
                                        .takeIf { it.isNotBlank() }
                                        ?.let { " · $it" }
                                        .orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { nav.push(Route.CourseEdit(null)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_course))
                }
            }

            FormSection(stringResource(R.string.section_import)) {
                NavRow(stringResource(R.string.import_excel), stringResource(R.string.import_excel_desc), Icons.Rounded.UploadFile) { nav.push(Route.Import(null)) }
                NavRow(stringResource(R.string.import_ics), stringResource(R.string.import_ics_desc), Icons.Rounded.UploadFile) { nav.push(Route.Import(null)) }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_jw), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.import_jw_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FormSection(stringResource(R.string.section_more)) {
                NavRow(stringResource(R.string.edit_periods), stringResource(R.string.edit_periods_desc, sem.periods.size), Icons.Rounded.Schedule) { nav.push(Route.Periods) }
                NavRow(stringResource(R.string.copy_next_semester), stringResource(R.string.copy_next_semester_desc), Icons.Rounded.ContentCopy) { copyDlg = true }
            }

            val dashColor = MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showAdvanced = true }
                    .drawBehind {
                        drawRoundRect(
                            dashColor,
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = dashColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.titleMedium, color = dashColor)
                    Text(stringResource(R.string.advanced_row_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (pickDate) DatePickerDlg(week1, { pickDate = false }) { week1 = it.with(java.time.DayOfWeek.MONDAY) }
    if (showAdvanced) AdvancedSheet { showAdvanced = false }
    if (copyDlg) {
        var newName by remember { mutableStateOf("") }
        var newStart by remember { mutableStateOf(week1.plusWeeks(total.toLong() + 8)) }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { copyDlg = false },
            title = { Text(stringResource(R.string.copy_next_semester)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldText(stringResource(R.string.label_semester_name), newName, { newName = it })
                    ValueRow(stringResource(R.string.label_week1_monday), ctx.fullDate(newStart), { pick = true })
                }
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    actions.commit(
                        Edits.copyToNextSemester(snap, sem, newName.trim(), newStart.with(java.time.DayOfWeek.MONDAY), System.currentTimeMillis(), ctx.getString(R.string.copy_semester_desc, newName.trim())),
                        ctx.getString(R.string.copied_toast),
                    )
                    copyDlg = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { copyDlg = false }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
        if (pick) DatePickerDlg(newStart, { pick = false }) { newStart = it }
    }
}

@Composable
fun PeriodsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val sem =
        snap.currentSemester ?: run {
            onBack()
            return
        }
    var periods by remember(sem.id) { mutableStateOf(sem.periods.sortedBy { it.index }) }
    var editing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (index, isStart)

    MintScreen(
        title = stringResource(R.string.edit_periods),
        onBack = onBack,
        actions = {
            TextButton(onClick = { periods = app.mintcal.core.model.GdutPeriods.default }) { Text(stringResource(R.string.reset_default)) }
            TextButton(onClick = {
                val desc = ctx.getString(R.string.edit_periods_op)
                actions.commit(Edits.save(snap, sem.copy(periods = periods), desc, System.currentTimeMillis()), desc)
                onBack()
            }) { Text(stringResource(R.string.save)) }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.periods_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormSection {
                periods.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ctx.getString(R.string.period_n, p.index), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { editing = i to true }) { Text(p.start.hm()) }
                        Text("–", Modifier.padding(horizontal = 8.dp))
                        OutlinedButton(onClick = { editing = i to false }) { Text(p.end.hm()) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val last = periods.last()
                        periods = periods +
                            app.mintcal.core.model
                                .PeriodTime(last.index + 1, last.end.plusMinutes(5), last.end.plusMinutes(50))
                    }, enabled = periods.size < 16) { Text(stringResource(R.string.add_period)) }
                    OutlinedButton(onClick = { periods = periods.dropLast(1) }, enabled = periods.size > 1) { Text(stringResource(R.string.remove_last_period)) }
                }
            }
        }
    }
    editing?.let { (i, isStart) ->
        val p = periods[i]
        app.mintcal.ui.components.TimePickerDlg(if (isStart) p.start else p.end, { editing = null }) { t ->
            periods = periods.toMutableList().also { it[i] = if (isStart) p.copy(start = t) else p.copy(end = t) }
        }
    }
}
