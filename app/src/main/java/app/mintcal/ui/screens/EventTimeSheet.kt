package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.platform.LocalContext
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

/** 面板结果：日期/时间段 + 提醒 + 重复一次带出；[whenPick] 为 null 表示清掉时间。 */
internal data class WhenPanelResult(
    val whenPick: WhenPick?,
    val remindMinutes: Int? = null,
    val freq: Freq? = null,
    val interval: Int = 1,
    val until: LocalDate? = null,
)

/**
 * 「日期 | 时间段」面板，快捷添加条和编辑页共用：
 * 日期页 = 快捷芯片（今天/明天/下周一/今天晚上）+ 月历 + 时间（截止时刻）/ 提醒 / 重复；
 * 时间段页 = 开始 / 结束 / 时长。底部固定 清除 · 取消 · 确定，一眼看到所有可选项。
 */
@Composable
internal fun WhenPanel(
    initial: WhenPick?,
    defaultDate: LocalDate,
    defaultMinutes: Int,
    initialRemind: Int? = null,
    initialFreq: Freq? = null,
    initialInterval: Int = 1,
    initialUntil: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (WhenPanelResult) -> Unit,
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startMin by remember { mutableIntStateOf(initial?.start?.let { it.hour * 60 + it.minute } ?: -1) }
    var endMin by remember { mutableIntStateOf(initial?.let { p -> p.start?.let { it.hour * 60 + it.minute + p.minutes } } ?: -1) }
    var tab by remember { mutableIntStateOf(if (initial?.block == true && initial.start != null) 1 else 0) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var remind by remember { mutableStateOf(initialRemind) }
    var freq by remember { mutableStateOf(initialFreq) }
    var interval by remember { mutableIntStateOf(initialInterval) }
    var until by remember { mutableStateOf(initialUntil) }
    var startDlg by remember { mutableStateOf(false) }
    var endDlg by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    var remindDlg by remember { mutableStateOf(false) }
    var repeatDlg by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val start = if (startMin >= 0) LocalTime.of(startMin / 60 % 24, startMin % 60) else null
    val end = if (endMin >= 0) LocalTime.of(endMin / 60 % 24, endMin % 60) else null
    val duration = (endMin - startMin).takeIf { startMin >= 0 && endMin > startMin }?.coerceIn(1, 24 * 60) ?: defaultMinutes
    val result =
        if (tab == 1) {
            WhenPick(date, start, duration, block = true)
        } else {
            WhenPick(date, start, defaultMinutes, block = false)
        }

    fun close(whenPick: WhenPick?) {
        onPick(WhenPanelResult(whenPick, remind, freq, interval, if (freq == null) null else until))
    }
    val timeLabel =
        when {
            start == null -> stringResource(R.string.none)
            tab == 1 -> "${start.hm()} – ${end?.hm().orEmpty()}"
            else -> start.hm()
        }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                        TabText(stringResource(R.string.tab_date), tab == 0) { tab = 0 }
                        Spacer(Modifier.width(18.dp))
                        TabText(stringResource(R.string.tab_range), tab == 1) { tab = 1 }
                    }
                    if (tab == 0) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                QuickChip(Icons.Rounded.CalendarToday, stringResource(R.string.today), date == today && start == null, Modifier.weight(1f)) {
                                    date = today
                                }
                                QuickChip(Icons.Rounded.WbSunny, stringResource(R.string.tomorrow), date == today.plusDays(1) && start == null, Modifier.weight(1f)) {
                                    date = today.plusDays(1)
                                }
                                QuickChip(Icons.Rounded.DateRange, stringResource(R.string.date_next_monday), date == nextMonday(today) && start == null, Modifier.weight(1f)) {
                                    date = nextMonday(today)
                                }
                                QuickChip(Icons.Rounded.Bedtime, stringResource(R.string.tonight), date == today && startMin == 20 * 60, Modifier.weight(1f)) {
                                    date = today
                                    startMin = 20 * 60
                                    endMin = 21 * 60
                                }
                            }
                            MonthHeader(month, onClick = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })
                            MonthGrid(selected = date, month = month) { d -> date = d }
                            Spacer(Modifier.height(6.dp))
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.label_time), timeLabel, start != null, onClear = {
                                startMin = -1
                                endMin = -1
                            }) { startDlg = true }
                            SettingRow(Icons.Rounded.Alarm, stringResource(R.string.label_remind), remindLabel(remind), remind != null, onClear = { remind = null }) { remindDlg = true }
                            SettingRow(Icons.Rounded.Repeat, stringResource(R.string.label_repeat), freqLabel(freq, until, interval), freq != null, onClear = {
                                freq = null
                                until = null
                            }) { repeatDlg = true }
                        }
                    } else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                ctx.monthDayWeekday(date),
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { dateDlg = true }
                                    .padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.field_start), start?.hm() ?: stringResource(R.string.none), start != null, onClear = { startMin = -1 }) { startDlg = true }
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.field_end), end?.hm() ?: stringResource(R.string.none), end != null, onClear = { endMin = -1 }) { endDlg = true }
                            Text(
                                stringResource(R.string.label_duration) + " · " +
                                    if (duration % 60 == 0) stringResource(R.string.dur_h, duration / 60) else stringResource(R.string.minutes_n, duration),
                                Modifier.padding(top = 4.dp, start = 32.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            SettingRow(Icons.Rounded.Alarm, stringResource(R.string.label_remind), remindLabel(remind), remind != null, onClear = { remind = null }) { remindDlg = true }
                            SettingRow(Icons.Rounded.Repeat, stringResource(R.string.label_repeat), freqLabel(freq, until, interval), freq != null, onClear = {
                                freq = null
                                until = null
                            }) { repeatDlg = true }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { close(null) }) { Text(stringResource(R.string.clear_time), color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { close(result) }, enabled = tab == 0 || start != null) {
                            Text(stringResource(R.string.ok), color = if (tab == 0 || start != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
        TimePickerDlg(end ?: start?.plusMinutes(duration.toLong()) ?: LocalTime.of(19, 0), { endDlg = false }) { t ->
            val newEnd = t.hour * 60 + t.minute
            endMin = if (startMin >= 0 && newEnd <= startMin) newEnd + 24 * 60 else newEnd
        }
    }
    if (dateDlg) {
        DatePickerDlg(date, { dateDlg = false }) { date = it }
    }
    if (remindDlg) {
        RemindPickDialog(remind, { remindDlg = false }) {
            remind = it
            remindDlg = false
        }
    }
    if (repeatDlg) {
        RepeatPickDialog(freq, interval, until, { repeatDlg = false }) { f, i, u ->
            freq = f
            interval = i
            until = if (f == null) null else u
            repeatDlg = false
        }
    }
}

/** 下一个周一（今天就是周一则顺延一周）。 */
private fun nextMonday(today: LocalDate): LocalDate {
    val m = today.with(DayOfWeek.MONDAY)
    return if (m.isAfter(today)) m else m.plusWeeks(1)
}

/** 快捷芯片：图标在上、文字在下，选中染主题色。 */
@Composable
private fun QuickChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
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
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                IconButton(onClick = { until = null }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
