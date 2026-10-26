package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mintcal.R
import app.mintcal.core.model.Freq
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.TimePickerDlg
import app.mintcal.ui.hm
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 编辑页里「何时」：哪一天 +（可选）几点到几点。没有时刻就是一条只有日期的待办。
 * [block] 区分时间块与已有的具体截止时刻，编辑时保留各自语义。
 */
internal data class WhenPick(
    val date: LocalDate,
    val start: LocalTime?,
    val minutes: Int = 60,
    val block: Boolean = true,
) {
    val end: LocalTime? get() = start?.let { it.plusMinutes(minutes.toLong()) }
}

/**
 * 时间设置：上面「日期 | 时间段」两个页签，日期页是月历，时间段页是半小时块状图（长按拖动=框选，
 * 点一下=按默认时长排块）。时间用「开始 / 结束」两个精确选择器，不吸附到半小时。
 * 提醒 / 重复在编辑页里直接选（带全部可见选项），这里只管哪天、几点到几点。
 */
@Composable
internal fun EventTimeSheet(
    initial: WhenPick?,
    defaultDate: LocalDate,
    defaultMinutes: Int,
    onDismiss: () -> Unit,
    onPick: (WhenPick?) -> Unit,
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startMin by remember { mutableIntStateOf(initial?.start?.let { it.hour * 60 + it.minute } ?: -1) }
    var endMin by remember { mutableIntStateOf(initial?.let { p -> p.start?.let { it.hour * 60 + it.minute + p.minutes } } ?: -1) }
    var block by remember { mutableStateOf(initial?.block ?: true) }
    var tab by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var startDlg by remember { mutableStateOf(false) }
    var endDlg by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    val start = if (startMin >= 0) LocalTime.of(startMin / 60, startMin % 60) else null
    val duration = (endMin - startMin).takeIf { startMin >= 0 && endMin > startMin } ?: defaultMinutes
    val result = WhenPick(date, start, duration.coerceIn(1, 24 * 60), block)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    // 顶部：✕ / 页签 / ✓
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                            TabText(stringResource(R.string.tab_date), tab == 0) { tab = 0 }
                            Spacer(Modifier.width(18.dp))
                            TabText(stringResource(R.string.tab_range), tab == 1) { tab = 1 }
                        }
                        IconButton(onClick = { onPick(result) }) { Icon(Icons.Rounded.Check, stringResource(R.string.ok)) }
                    }
                    if (tab == 0) {
                        Column(Modifier.weight(1f)) {
                            MonthHeader(month, onClick = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })
                            MonthGrid(selected = date, month = month) { d ->
                                date = d
                            }
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                ctx.monthDayWeekday(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            TimeRangeRow(
                                start = start,
                                end = result.end,
                                showEnd = block,
                                nextDay = endMin >= 24 * 60,
                                onPickStart = { startDlg = true },
                                onPickEnd = { endDlg = true },
                                onClear = {
                                    startMin = -1
                                    endMin = -1
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onPick(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(stringResource(R.string.clear_time), color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                                Text(
                                    when {
                                        start == null -> stringResource(R.string.pick_time_first)
                                        block -> "${start.hm()} – ${result.end?.hm().orEmpty()}"
                                        else -> start.hm()
                                    },
                                    modifier = Modifier.weight(1f).clickable { dateDlg = true },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                            }
                            Text(
                                stringResource(R.string.detail_settings_grid_hint, defaultMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HalfHourGrid(
                                startMin = startMin,
                                endMin = endMin,
                                defaultMinutes = defaultMinutes,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onChange = { s, e ->
                                    startMin = s
                                    endMin = e
                                    block = true
                                },
                            )
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.clear_time), color = MaterialTheme.colorScheme.error) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    startMin = -1
                                    endMin = -1
                                }) { Text(stringResource(R.string.only_date)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (startDlg) {
        TimePickerDlg(start ?: LocalTime.of(18, 0), { startDlg = false }) { t ->
            val newStart = t.hour * 60 + t.minute
            startMin = newStart
            endMin = newStart + duration.coerceIn(1, 24 * 60)
        }
    }
    if (endDlg) {
        TimePickerDlg(result.end ?: LocalTime.of(19, 0), { endDlg = false }) { t ->
            val newEnd = t.hour * 60 + t.minute
            endMin = if (startMin >= 0 && newEnd <= startMin) newEnd + 24 * 60 else newEnd
        }
    }
    if (dateDlg) {
        DatePickerDlg(date, { dateDlg = false }) { date = it }
    }
}

/** 「时间」一行：开始 / 结束各自精确选择，设了时间就能一眼看到、一键清除。截止模式（不占时间块）不显示结束。 */
@Composable
private fun TimeRangeRow(
    start: LocalTime?,
    end: LocalTime?,
    showEnd: Boolean,
    nextDay: Boolean,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.sheet_time), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (start == null) {
            Text(
                stringResource(R.string.pick_time_first),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onPickStart).padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TimeSegment(stringResource(R.string.detail_editor_start), start.hm(), onPickStart)
            if (showEnd) {
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.detail_editor_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                TimeSegment(stringResource(if (nextDay) R.string.time_end_next_day else R.string.detail_editor_end), end?.hm().orEmpty(), onPickEnd)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TimeSegment(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(if (selected) 22.dp else 0.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onClick: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.month_n, month.monthValue), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
    }
}

@Composable
private fun MonthGrid(
    selected: LocalDate,
    month: YearMonth,
    onPick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val haptics = LocalHapticFeedback.current
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            for (i in 0..6) {
                Text(
                    LocalContext.current.weekdayShort(i + 1),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val d = first.plusDays((r * 7 + c).toLong())
                    val sel = d == selected
                    val inMonth = YearMonth.from(d) == month
                    Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onPick(d)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    when {
                                        sel -> MaterialTheme.colorScheme.onPrimary
                                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        d == LocalDate.now() -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一行「图标 + 标签 + 当前值」，值可直接看见；编辑页的提醒 / 重复用它，点了弹完整选择器。 */
@Composable
internal fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    set: Boolean,
    onClear: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (set) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
internal fun remindLabel(m: Int?): String =
    when (m) {
        null -> stringResource(R.string.none)
        0 -> stringResource(R.string.remind_at_due)
        5 -> stringResource(R.string.remind_5m)
        10 -> stringResource(R.string.remind_10m)
        15 -> stringResource(R.string.remind_15m)
        30 -> stringResource(R.string.remind_30m)
        60 -> stringResource(R.string.remind_1h)
        1440 -> stringResource(R.string.remind_1d)
        else -> if (m % 60 == 0) stringResource(R.string.dur_h, m / 60) else stringResource(R.string.minutes_n, m)
    }

@Composable
internal fun freqLabel(
    f: Freq?,
    until: LocalDate?,
    interval: Int = 1,
): String =
    when {
        f == null -> {
            stringResource(R.string.repeat_none)
        }

        interval > 1 -> {
            stringResource(R.string.detail_editor_every_n, interval, freqUnit(f))
        }

        else -> {
            when (f) {
                Freq.DAILY -> stringResource(R.string.repeat_daily)
                Freq.WEEKLY -> stringResource(R.string.repeat_weekly)
                Freq.MONTHLY -> stringResource(R.string.repeat_monthly)
                Freq.YEARLY -> stringResource(R.string.repeat_yearly)
            }
        }
    }.let { if (until != null) "$it · ${stringResource(R.string.repeat_until_short)}" else it }

@Composable
private fun freqUnit(f: Freq): String =
    stringResource(
        when (f) {
            Freq.DAILY -> R.string.detail_editor_unit_day
            Freq.WEEKLY -> R.string.detail_editor_unit_week
            Freq.MONTHLY -> R.string.detail_editor_unit_month
            Freq.YEARLY -> R.string.detail_editor_unit_year
        },
    )

/** 提醒选择器：全部选项可见，不再点一行轮换；「自定义」支持任意分钟（如 17）。 */
@Composable
internal fun RemindPickDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
) {
    val fixed =
        listOf(
            null to stringResource(R.string.none),
            0 to stringResource(R.string.remind_at_due),
            10 to stringResource(R.string.remind_10m),
            30 to stringResource(R.string.remind_30m),
            60 to stringResource(R.string.remind_1h),
            1440 to stringResource(R.string.remind_1d),
        )
    val customInit = initial != null && fixed.none { it.first == initial }
    var sel by remember { mutableStateOf(if (customInit) -1 else initial) }
    var customText by remember { mutableStateOf(if (customInit) initial.toString() else "") }
    val customValue = customText.toIntOrNull()
    val customValid = customValue != null && customValue in 0..10080
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_remind)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                fixed.forEach { (v, label) ->
                    RadioRow(selected = sel == v, label = label) { sel = v }
                }
                RadioRow(selected = sel == -1, label = stringResource(R.string.detail_editor_remind_custom)) { sel = -1 }
                AnimatedVisibility(visible = sel == -1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.take(6) },
                        label = { Text(stringResource(R.string.detail_editor_remind_minutes)) },
                        singleLine = true,
                        isError = !customValid,
                        supportingText =
                            if (!customValid) {
                                { Text(stringResource(R.string.detail_minutes_invalid, 0, 10080)) }
                            } else {
                                null
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sel != -1 || customValid,
                onClick = {
                    onPick(if (sel == -1) customValue!! else sel)
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 重复选择器：频率可见可选，支持精确间隔（每 N 天/周/月/年）与截止日期。 */
@Composable
internal fun RepeatPickDialog(
    freqInitial: Freq?,
    intervalInitial: Int,
    untilInitial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (Freq?, Int, LocalDate?) -> Unit,
) {
    val ctx = LocalContext.current
    var freq by remember { mutableStateOf(freqInitial) }
    var intervalText by remember { mutableStateOf(intervalInitial.coerceAtLeast(1).toString()) }
    val parsedInterval = intervalText.toIntOrNull()
    val intervalValid = parsedInterval != null && parsedInterval in 1..999
    var until by remember { mutableStateOf(untilInitial) }
    var untilDlg by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_repeat)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChipChoice(
                    listOf(
                        null to stringResource(R.string.repeat_none),
                        Freq.DAILY to stringResource(R.string.repeat_daily),
                        Freq.WEEKLY to stringResource(R.string.repeat_weekly),
                        Freq.MONTHLY to stringResource(R.string.repeat_monthly),
                        Freq.YEARLY to stringResource(R.string.repeat_yearly),
                    ),
                    freq,
                    { freq = it },
                )
                AnimatedVisibility(visible = freq != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.detail_editor_interval), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { intervalText = it.take(4) },
                                singleLine = true,
                                isError = !intervalValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(88.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(freq?.let { freqUnit(it) }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (!intervalValid) {
                            Text(stringResource(R.string.detail_minutes_invalid, 1, 999), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { untilDlg = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.detail_editor_repeat_until), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                until?.let { ctx.monthDayWeekday(it) } ?: stringResource(R.string.never),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (until != null) {
                                IconButton(onClick = { until = null }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = freq == null || intervalValid, onClick = {
                onPick(freq, if (freq == null) 1 else parsedInterval!!, if (freq == null) null else until)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
    if (untilDlg) {
        DatePickerDlg(until ?: LocalDate.now().plusMonths(3), { untilDlg = false }) { until = it }
    }
}

/** 半小时一格的纵向时间块图：长按拖动=框选；点一下=从该半点起排一个默认时长的块（分钟数不被吸附）。 */
@Composable
private fun HalfHourGrid(
    startMin: Int,
    endMin: Int,
    defaultMinutes: Int,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotH = 26.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    LaunchedEffect(Unit) {
        val focusMin = if (startMin >= 0) startMin else LocalTime.now().hour * 60
        val idx = (focusMin / SLOT_MIN - 4).coerceAtLeast(0)
        scroll.scrollTo(with(density) { (slotH * idx).roundToPx() })
    }
    Column(
        modifier
            .verticalScroll(scroll)
            .pointerInput(Unit) {
                val slotPx = slotH.toPx()
                detectTapGestures { off ->
                    val s = (off.y / slotPx).toInt().coerceIn(0, SLOTS - 1)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(s * SLOT_MIN, s * SLOT_MIN + defaultMinutes)
                }
            }.pointerInput(Unit) {
                val slotPx = slotH.toPx()

                fun slotAt(y: Float): Int = (y / slotPx).toInt().coerceIn(0, SLOTS - 1)
                var anchor = 0
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        anchor = slotAt(off.y)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChange(anchor * SLOT_MIN, (anchor + 1) * SLOT_MIN)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val s = slotAt(change.position.y)
                        onChange(minOf(anchor, s) * SLOT_MIN, (maxOf(anchor, s) + 1) * SLOT_MIN)
                    },
                )
            },
    ) {
        for (i in 0 until SLOTS) {
            val a = i * SLOT_MIN
            val b = a + SLOT_MIN
            val on = startMin >= 0 && startMin < b && endMin > a
            val shape =
                RoundedCornerShape(
                    topStart = if (on && startMin >= a) 14.dp else 0.dp,
                    topEnd = if (on && startMin >= a) 14.dp else 0.dp,
                    bottomStart = if (on && endMin <= b) 14.dp else 0.dp,
                    bottomEnd = if (on && endMin <= b) 14.dp else 0.dp,
                )
            Box(Modifier.fillMaxWidth().height(slotH).background(if (on) primary.copy(alpha = 0.16f) else Color.Transparent, shape)) {
                if (i % 2 == 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(outline.copy(alpha = 0.5f)))
                    Text(
                        "${i / 2}:00",
                        modifier = Modifier.padding(start = 14.dp, top = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private const val SLOT_MIN = 30
private const val SLOTS = 24 * 60 / SLOT_MIN
