package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Course
import app.mintcal.core.model.GdutCampus
import app.mintcal.core.model.GdutPeriods
import app.mintcal.core.model.PeriodTime
import app.mintcal.core.model.Semester
import app.mintcal.core.time.WeekSets
import app.mintcal.jxfw.JxfwException
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.SegmentedPill
import app.mintcal.ui.components.TimePickerDlg
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.components.rememberNoFocusPopup
import app.mintcal.ui.formatRooms
import app.mintcal.ui.fullDate
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun campusLabel(campus: GdutCampus?): String =
    when (campus) {
        GdutCampus.HEMC -> stringResource(R.string.campus_hemc)
        GdutCampus.LONGDONG -> stringResource(R.string.campus_longdong)
        GdutCampus.DONGFENG -> stringResource(R.string.campus_dongfeng)
        GdutCampus.JIEYANG -> stringResource(R.string.campus_jieyang)
        null -> stringResource(R.string.semester_schedule_custom)
    }

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
    var copyDlg by remember { mutableStateOf(false) }

    // 离开页面时自动保存学期信息（有改动才写）
    val latest by rememberUpdatedState(Triple(name, week1, total))
    val semNow by rememberUpdatedState(sem)
    val snapNow by rememberUpdatedState(snap)
    DisposableEffect(sem.id) {
        onDispose {
            val (n, w, t) = latest
            val s = semNow
            val trimmed = n.trim()
            val monday = w.with(java.time.DayOfWeek.MONDAY)
            if (trimmed.isNotBlank() && (trimmed != s.name || monday != s.week1Monday || t != s.totalWeeks)) {
                val upd = s.copy(name = trimmed, week1Monday = monday, totalWeeks = t)
                val desc = ctx.getString(R.string.edit_semester_desc)
                actions.commit(Edits.save(snapNow, upd, desc, System.currentTimeMillis()), desc)
            }
        }
    }

    // 同名课程 = 同一门课的多个时段：按名称聚合成一行，别让 26 条重复排课刷屏
    val groupedCourses =
        remember(snap, sem) {
            snap.courses
                .filter { it.semesterId == sem.id }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name }))
                .groupBy { it.name }
                .values
                .toList()
        }

    val weeksNow = ChronoUnit.WEEKS.between(week1.with(java.time.DayOfWeek.MONDAY), LocalDate.now()) + 1
    val weekTrailing =
        when {
            weeksNow < 1 -> stringResource(R.string.semester_not_started)
            weeksNow > total -> stringResource(R.string.semester_ended)
            else -> stringResource(R.string.week_n, weeksNow.toInt())
        }
    val detectedCampus = remember(sem.periods) { GdutPeriods.detectCampus(sem.periods) }
    val scheduleSummary = stringResource(R.string.semester_schedule_value, campusLabel(detectedCampus), sem.periods.size)

    MintScreen(title = stringResource(R.string.edit_timetable), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JxfwSyncCard(sem)

            FormSection(
                title = stringResource(R.string.section_semester),
                trailing = weekTrailing,
                trailingColor = MaterialTheme.colorScheme.primary,
            ) {
                FieldText(stringResource(R.string.label_semester_name), name, { name = it })
                ValueRow(stringResource(R.string.label_week1_monday), ctx.fullDate(week1), { pickDate = true })
                Stepper(stringResource(R.string.label_total_weeks), total, 1, 30, { ctx.getString(R.string.week_n_total, it) }) { total = it }
                ValueRow(
                    label = stringResource(R.string.semester_schedule_label),
                    value = scheduleSummary,
                    onClick = { nav.push(Route.Periods) },
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }

            Column(Modifier.padding(top = 4.dp)) {
                SectionLabel(
                    stringResource(R.string.section_courses),
                    trailing = ctx.getString(R.string.courses_count, groupedCourses.size),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    groupedCourses.forEach { group ->
                        CourseGroupRow(group) { nav.push(Route.CourseEdit(group.first().id)) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .mintClickable(pressedScale = 0.98f) { nav.push(Route.CourseEdit(null)) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.new_course),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            FormSection(stringResource(R.string.section_more)) {
                NavRow(
                    stringResource(R.string.import_timetable_file),
                    stringResource(R.string.import_timetable_file_desc),
                    Icons.Rounded.UploadFile,
                ) { nav.push(Route.Import(null)) }
                NavRow(
                    stringResource(R.string.copy_next_semester),
                    stringResource(R.string.copy_next_semester_desc),
                    Icons.Rounded.ContentCopy,
                ) { copyDlg = true }
            }
        }
    }

    if (pickDate) DatePickerDlg(week1, { pickDate = false }) { week1 = it.with(java.time.DayOfWeek.MONDAY) }
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

/**
 * 教务同步入口卡：未登录时一句说明 + 登录键；已登录时学号 / 学期 + 立即同步。
 * 教务是广工的第一公民，所以它排在学期设置之上。
 */
@Composable
private fun JxfwSyncCard(sem: Semester) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val state by app.jxfw.state.collectAsState()

    MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.jxfw_card_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!state.loggedIn) {
            Text(
                stringResource(R.string.jxfw_card_unbound_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { nav.push(Route.Jxfw) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.jxfw_card_bind_action))
            }
        } else {
            val termText = state.term?.let { jxfwTermLabel(ctx, it) } ?: sem.name
            Text(
                ctx.getString(R.string.jxfw_card_bound_meta, state.username.orEmpty(), termText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy && state.progress != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(state.progress.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            jxfwCardErrorText(state.lastErrorKind, state.lastError)?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    enabled = !state.busy,
                    onClick = {
                        scope.launch {
                            try {
                                app.jxfw.fullSync()
                                actions.toast(ctx.getString(R.string.jxfw_done_toast))
                            } catch (_: JxfwException) {
                                // 错误已写入 state.lastError / lastErrorKind 并在卡片内联展示
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.jxfw_card_sync_now))
                }
                TextButton(onClick = { nav.push(Route.Jxfw) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.jxfw_title))
                }
            }
        }
    }
}

@Composable
private fun jxfwCardErrorText(
    kind: JxfwException.Kind?,
    raw: String?,
): String? =
    when (kind) {
        null -> raw
        JxfwException.Kind.CREDENTIALS -> stringResource(R.string.jxfw_err_credentials)
        JxfwException.Kind.CAPTCHA -> stringResource(R.string.jxfw_err_captcha)
        JxfwException.Kind.SMS -> stringResource(R.string.jxfw_err_sms)
        JxfwException.Kind.NETWORK -> stringResource(R.string.jxfw_err_network)
        else -> raw
    }

/** 一门课一行：植物色竖条 + 课名 + 时段摘要（多时段折叠成「前两个 · N 个时段」）+ 教室 + 右箭头。 */
@Composable
private fun CourseGroupRow(
    group: List<Course>,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val first = group.first()
    val rooms = group.map { it.room }.filter { it.isNotBlank() }.distinct()

    fun line(c: Course): String = ctx.getString(R.string.course_line, ctx.weekdayShort(c.dayOfWeek), c.startPeriod, c.endPeriod, WeekSets.format(c.weeks))

    val summary =
        buildString {
            append(group.take(2).joinToString(" · ") { line(it) })
            if (group.size > 2) append(" · ").append(ctx.getString(R.string.courses_slots_count, group.size))
            if (rooms.isNotEmpty()) {
                val folded = ctx.formatRooms(rooms.joinToString(" / "))
                if (folded.isNotEmpty()) append(" · ").append(folded)
            }
        }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .mintClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(MintTheme.colors.fg(first.colorIndex)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(first.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
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
    var editing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val noFocusPopup = rememberNoFocusPopup()
    val latest by rememberUpdatedState(periods)
    val semNow by rememberUpdatedState(sem)
    val snapNow by rememberUpdatedState(snap)
    DisposableEffect(sem.id) {
        onDispose {
            val next = latest
            val s = semNow
            if (next != s.periods) {
                val desc = ctx.getString(R.string.edit_periods_op)
                actions.commit(Edits.save(snapNow, s.copy(periods = next), desc, System.currentTimeMillis()), desc)
            }
        }
    }

    // 广工四校区作息：点一下就整套换掉，省得逐节改时间
    val campus = GdutPeriods.detectCampus(periods)
    val campusOptions: List<Pair<GdutCampus?, String>> =
        listOf(
            GdutCampus.HEMC to stringResource(R.string.campus_hemc),
            GdutCampus.LONGDONG to stringResource(R.string.campus_longdong),
            GdutCampus.DONGFENG to stringResource(R.string.campus_dongfeng),
            GdutCampus.JIEYANG to stringResource(R.string.campus_jieyang),
        )
    val presetTrailing =
        if (campus != null) {
            stringResource(R.string.edit_periods_desc, periods.size)
        } else {
            stringResource(R.string.periods_custom_desc, periods.size)
        }

    MintScreen(
        title = stringResource(R.string.edit_periods),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }, properties = noFocusPopup) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reset_default)) },
                        onClick = {
                            menu = false
                            periods = GdutPeriods.default
                        },
                    )
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            FormSection(
                title = stringResource(R.string.campus_preset_section),
                trailing = presetTrailing,
            ) {
                SegmentedPill(
                    options = campusOptions,
                    selected = campus,
                    onSelect = { picked -> if (picked != null) periods = GdutPeriods.forCampus(picked) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            periods.forEachIndexed { i, p ->
                val prev = periods.getOrNull(i - 1)
                when {
                    i == 0 -> SectionLabel(stringResource(R.string.periods_morning), modifier = Modifier.padding(top = 12.dp))
                    prev != null && prev.index == 4 -> SectionLabel(stringResource(R.string.periods_afternoon), modifier = Modifier.padding(top = 14.dp))
                    prev != null && prev.index == 9 -> SectionLabel(stringResource(R.string.periods_evening), modifier = Modifier.padding(top = 14.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ctx.getString(R.string.period_n, p.index),
                        Modifier.weight(1f).padding(start = 4.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    )
                    TimeTap(p.start.hm()) { editing = i to true }
                    Text(
                        "–",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    TimeTap(p.end.hm()) { editing = i to false }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (periods.size < 16) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                                val last = periods.last()
                                periods = periods + PeriodTime(last.index + 1, last.end.plusMinutes(5), last.end.plusMinutes(50))
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.add_period),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (periods.size > 1) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) {
                                periods = periods.dropLast(1)
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.remove_last_period),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
    editing?.let { (i, isStart) ->
        val p = periods[i]
        TimePickerDlg(if (isStart) p.start else p.end, { editing = null }) { t ->
            periods = periods.toMutableList().also { it[i] = if (isStart) p.copy(start = t) else p.copy(end = t) }
        }
    }
}

@Composable
private fun TimeTap(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
