package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.TimePickerDlg
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun EventEditScreen(
    id: String?,
    dateArg: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val existing = remember(id) { id?.let { i -> snap.events.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        // 记录已被删除：composition 期间不能改导航状态，延后到 effect 里返回
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // 新建时自动聚焦标题
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) titleFocus.requestFocus() }
    val initDate = remember { dateArg?.let { LocalDate.parse(it) } ?: LocalDate.now() }

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var allDay by remember { mutableStateOf(existing?.allDay ?: false) }
    var startDate by remember { mutableStateOf(existing?.start?.toLocalDate() ?: initDate) }
    var startTime by remember { mutableStateOf(existing?.start?.toLocalTime() ?: LocalTime.of(14, 0)) }
    var endDate by remember { mutableStateOf(existing?.end?.toLocalDate() ?: initDate) }
    var endTime by remember { mutableStateOf(existing?.end?.toLocalTime() ?: LocalTime.of(15, 0)) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorIndex ?: 4) }
    var kind by remember { mutableStateOf(existing?.kind ?: EventKind.NORMAL) }
    var freq by remember { mutableStateOf(existing?.recurrence?.freq) }
    var until by remember { mutableStateOf(existing?.recurrence?.until) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): Event {
        val s = if (allDay) startDate.atStartOfDay() else LocalDateTime.of(startDate, startTime)
        var e = if (allDay) maxOf(endDate, startDate).atStartOfDay() else LocalDateTime.of(endDate, endTime)
        if (e.isBefore(s)) e = s
        val base = existing ?: Event(Ids.next(), "", s, e)
        return base.copy(
            title = title.trim(),
            start = s,
            end = e,
            allDay = allDay,
            location = location.trim(),
            note = note,
            category = category.trim(),
            colorIndex = color,
            kind = kind,
            remindMinutes = remind,
            recurrence = freq?.let { f -> (base.recurrence ?: Recurrence(f)).copy(freq = f, until = until) },
        )
    }
    val preview =
        remember(title, allDay, startDate, startTime, endDate, endTime, freq) {
            if (title.isBlank() || allDay) {
                emptyList()
            } else {
                val e = build()
                Conflicts.detect(snap.apply(Edits.save(snap, e, "", 0), 0), setOf(e.id))
            }
        }

    MintScreen(
        title = stringResource(if (existing == null) R.string.new_event else R.string.edit_event),
        onBack = onBack,
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
            Button(
                onClick = {
                    val e = build()
                    actions.commit(Edits.save(snap, e, ctx.getString(if (existing == null) R.string.add_event_desc else R.string.edit_event_desc, e.title), System.currentTimeMillis()), ctx.getString(R.string.saved_toast))
                    onBack()
                },
                enabled = title.isNotBlank(),
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
            FieldText(stringResource(R.string.label_title), title, { title = it }, modifier = Modifier.focusRequester(titleFocus))
            Text(stringResource(R.string.label_kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipChoice(listOf(EventKind.NORMAL to stringResource(R.string.kind_normal), EventKind.EXAM to stringResource(R.string.kind_exam), EventKind.COUNTDOWN to stringResource(R.string.kind_countdown)), kind, { kind = it })

            FormSection(stringResource(R.string.section_when)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.all_day), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(allDay, { allDay = it })
                }
                ValueRow(stringResource(R.string.label_start), if (allDay) ctx.monthDayWeekday(startDate) else "${ctx.monthDayWeekday(startDate)}  ${startTime.hm()}", { pick = "sd" })
                AnimatedVisibility(visible = !allDay, enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    ValueRow(stringResource(R.string.label_start_time), startTime.hm(), { pick = "st" })
                }
                ValueRow(stringResource(R.string.label_end), if (allDay) ctx.monthDayWeekday(endDate) else "${ctx.monthDayWeekday(endDate)}  ${endTime.hm()}", { pick = "ed" })
                AnimatedVisibility(visible = !allDay, enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    ValueRow(stringResource(R.string.label_end_time), endTime.hm(), { pick = "et" })
                }
                Text(stringResource(R.string.label_repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(
                    listOf<Pair<Freq?, String>>(null to stringResource(R.string.repeat_none), Freq.DAILY to stringResource(R.string.repeat_daily), Freq.WEEKLY to stringResource(R.string.repeat_weekly), Freq.MONTHLY to stringResource(R.string.repeat_monthly), Freq.YEARLY to stringResource(R.string.repeat_yearly)),
                    freq,
                    { freq = it },
                )
                AnimatedVisibility(visible = freq != null, enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()), exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec())) {
                    ValueRow(stringResource(R.string.label_until), until?.let { ctx.monthDayWeekday(it) } ?: stringResource(R.string.never), { pick = "until" })
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_remind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(
                    listOf<Pair<Int?, String>>(null to stringResource(R.string.remind_none), 0 to stringResource(R.string.remind_at_start), 10 to stringResource(R.string.remind_10m), 60 to stringResource(R.string.remind_1h), 1440 to stringResource(R.string.remind_1d)),
                    remind,
                    { remind = it },
                )
            }
            FormSection(stringResource(R.string.section_more)) {
                FieldText(stringResource(R.string.label_location), location, { location = it })
                Spacer(Modifier.height(8.dp))
                FieldText(stringResource(R.string.label_category), category, { category = it })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(color) { color = it }
                Spacer(Modifier.height(8.dp))
                FieldText(stringResource(R.string.label_note), note, { note = it }, singleLine = false, minLines = 2)
            }
            if (preview.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .androidxClipBg(MintTheme.colors.conflictBg)
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

    when (pick) {
        "sd" -> {
            DatePickerDlg(startDate, { pick = null }) {
                startDate = it
                if (endDate.isBefore(it)) endDate = it
            }
        }

        "ed" -> {
            DatePickerDlg(endDate, { pick = null }) { endDate = it }
        }

        "st" -> {
            TimePickerDlg(startTime, { pick = null }) {
                startTime = it
                if (startDate == endDate && endTime.isBefore(it)) endTime = it.plusHours(1)
            }
        }

        "et" -> {
            TimePickerDlg(endTime, { pick = null }) { endTime = it }
        }

        "until" -> {
            DatePickerDlg(until ?: startDate.plusMonths(3), { pick = null }) { until = it }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete_event_title),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                actions.commit(Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.title), System.currentTimeMillis()), ctx.getString(R.string.deleted_toast))
                onBack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

private fun Modifier.androidxClipBg(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        Modifier
            .clip(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(20.dp),
            ).background(color),
    )
