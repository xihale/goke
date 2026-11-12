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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Course
import app.mintcal.core.model.GdutCampus
import app.mintcal.core.model.GdutPeriods
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
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
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
            JxfwSyncCard(sem)

            FormSection(stringResource(R.string.section_semester)) {
                FieldText(stringResource(R.string.label_semester_name), name, { name = it })
                ValueRow(stringResource(R.string.label_week1_monday), ctx.fullDate(week1), { pickDate = true })
                Stepper(stringResource(R.string.label_total_weeks), total, 1, 30, { ctx.getString(R.string.week_n_total, it) }) { total = it }
                val weeksNow = ChronoUnit.WEEKS.between(week1.with(java.time.DayOfWeek.MONDAY), LocalDate.now()) + 1
                Text(
                    if (weeksNow >= 1) stringResource(R.string.week_n, weeksNow.toInt()) else stringResource(R.string.semester_not_started),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column {
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
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.new_course), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            FormSection(stringResource(R.string.section_import)) {
                NavRow(stringResource(R.string.jxfw_title), icon = Icons.Rounded.UploadFile) { nav.push(Route.Jxfw) }
                NavRow(stringResource(R.string.import_excel), icon = Icons.Rounded.UploadFile) { nav.push(Route.Import(null)) }
                NavRow(stringResource(R.string.import_ics), icon = Icons.Rounded.UploadFile) { nav.push(Route.Import(null)) }
            }

            FormSection(stringResource(R.string.section_more)) {
                NavRow(stringResource(R.string.edit_periods), stringResource(R.string.edit_periods_desc, sem.periods.size), Icons.Rounded.Schedule) { nav.push(Route.Periods) }
                NavRow(stringResource(R.string.copy_next_semester), stringResource(R.string.copy_next_semester_desc), Icons.Rounded.ContentCopy) { copyDlg = true }
                NavRow(stringResource(R.string.advanced_title), stringResource(R.string.advanced_row_desc), Icons.Rounded.AutoAwesome) { showAdvanced = true }
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
            Text(
                ctx.getString(R.string.jxfw_card_bound_meta, state.username.orEmpty(), state.term ?: sem.name),
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
                                // 错误已写入状态，详情页里能看到
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

/** 一门课一行：色点 + 课名 + 时段摘要（多时段折叠成「前两个 · N 个时段」）+ 教室。 */
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
            .heightIn(min = 48.dp)
            .mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MintTheme.colors.fg(first.colorIndex)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(first.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

    MintScreen(
        title = stringResource(R.string.edit_periods),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
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
            FormSection(stringResource(R.string.campus_preset_section)) {
                SegmentedPill(
                    options = campusOptions,
                    selected = campus,
                    onSelect = { picked -> if (picked != null) periods = GdutPeriods.forCampus(picked) },
                )
                Text(
                    stringResource(R.string.edit_periods_desc, periods.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            periods.forEachIndexed { i, p ->
                // 上午 / 下午 / 晚上之间留一口气，12 节不再糊成一条
                val prev = periods.getOrNull(i - 1)
                if (prev != null && (prev.index == 4 || prev.index == 9)) Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.period_n, p.index), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TimeTap(p.start.hm()) { editing = i to true }
                    Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TimeTap(p.end.hm()) { editing = i to false }
                }
            }
            if (periods.size < 16) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .mintClickable(pressedScale = 0.98f) {
                            val last = periods.last()
                            periods = periods +
                                app.mintcal.core.model
                                    .PeriodTime(last.index + 1, last.end.plusMinutes(5), last.end.plusMinutes(50))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_period), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (periods.size > 1) {
                Text(
                    stringResource(R.string.remove_last_period),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .mintClickable(pressedScale = 0.98f) { periods = periods.dropLast(1) }
                            .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
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

@Composable
private fun TimeTap(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .mintClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
