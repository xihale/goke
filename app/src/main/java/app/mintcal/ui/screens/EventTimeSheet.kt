package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mintcal.R
import app.mintcal.core.model.Freq
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.MonthPager
import app.mintcal.ui.components.TimeWheels
import app.mintcal.ui.hm
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

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
    val remindPersistent: Boolean = false,
    val freq: Freq? = null,
    val interval: Int = 1,
    val until: LocalDate? = null,
    val byDays: Set<Int> = emptySet(),
)

private enum class WheelSlot { START, END }

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
    initialPersistent: Boolean = false,
    initialFreq: Freq? = null,
    initialInterval: Int = 1,
    initialUntil: LocalDate? = null,
    initialByDays: Set<Int> = emptySet(),
    semesterEnd: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (WhenPanelResult) -> Unit,
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startMin by remember { mutableIntStateOf(initial?.start?.let { it.hour * 60 + it.minute } ?: -1) }
    var endMin by remember { mutableIntStateOf(initial?.let { p -> p.start?.let { it.hour * 60 + it.minute + p.minutes } } ?: -1) }
    var tab by remember { mutableIntStateOf(if (initial?.block == true && initial.start != null) 1 else 0) }
    var remind by remember { mutableStateOf(initialRemind) }
    var persistent by remember { mutableStateOf(initialPersistent && initialRemind != null) }
    var freq by remember { mutableStateOf(initialFreq) }
    var interval by remember { mutableIntStateOf(initialInterval) }
    var until by remember { mutableStateOf(initialUntil) }
    var byDays by remember { mutableStateOf(initialByDays) }
    var wheel by remember { mutableStateOf<WheelSlot?>(null) }
    var dateDlg by remember { mutableStateOf(false) }
    var remindDlg by remember { mutableStateOf(false) }
    var repeatDlg by remember { mutableStateOf(false) }
    var endDlg by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val start = if (startMin >= 0) LocalTime.of(startMin / 60 % 24, startMin % 60) else null
    val end = if (endMin >= 0) LocalTime.of(endMin / 60 % 24, endMin % 60) else null
    val duration = (endMin - startMin).takeIf { startMin >= 0 && endMin > startMin }?.coerceIn(1, 24 * 60) ?: defaultMinutes

    fun applyStart(
        h: Int,
        m: Int,
    ) {
        val dur = (endMin - startMin).takeIf { startMin >= 0 && endMin > startMin }?.coerceIn(1, 24 * 60) ?: defaultMinutes
        val newStart = h * 60 + m
        startMin = newStart
        endMin = newStart + dur
    }

    fun applyEnd(
        h: Int,
        m: Int,
    ) {
        val newEnd = h * 60 + m
        endMin = if (startMin >= 0 && newEnd <= startMin) newEnd + 24 * 60 else newEnd
    }

    fun openWheel(slot: WheelSlot) {
        if (wheel == slot) {
            wheel = null
            return
        }
        if (slot == WheelSlot.START && startMin < 0) applyStart(18, 0)
        if (slot == WheelSlot.END && endMin < 0) {
            if (startMin < 0) {
                applyStart(18, 0)
            } else {
                val base = startMin + defaultMinutes.coerceIn(1, 24 * 60)
                applyEnd((base / 60) % 24, base % 60)
            }
        }
        wheel = slot
    }
    LaunchedEffect(tab) {
        if (tab == 0 && wheel == WheelSlot.END) wheel = null
    }
    val result =
        if (tab == 1) {
            WhenPick(date, start, duration, block = true)
        } else {
            WhenPick(date, start, defaultMinutes, block = false)
        }

    fun close(whenPick: WhenPick?) {
        onPick(
            WhenPanelResult(
                whenPick = whenPick,
                remindMinutes = remind,
                remindPersistent = remind != null && persistent,
                freq = freq,
                interval = interval,
                until = if (freq == null) null else until,
                byDays = if (freq == Freq.WEEKLY) byDays else emptySet(),
            ),
        )
    }
    val timeLabel =
        when {
            start == null -> stringResource(R.string.none)
            tab == 1 -> "${start.hm()} – ${end?.hm().orEmpty()}"
            else -> start.hm()
        }

    val reduce = reduceMotion()
    val riseSpec = MintTheme.spec<Float>()
    val rise = remember { Animatable(if (reduce) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!reduce) rise.animateTo(0f, riseSpec)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .graphicsLayer { translationY = rise.value * size.height },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                        TabText(stringResource(R.string.tab_date), tab == 0) { tab = 0 }
                        Spacer(Modifier.width(18.dp))
                        TabText(stringResource(R.string.tab_range), tab == 1) { tab = 1 }
                    }
                    if (tab == 0) {
                        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState())) {
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
                            MonthPager(selected = date, initial = YearMonth.from(date), onPick = { date = it })
                        }
                    } else {
                        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState())) {
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
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.field_start), start?.hm() ?: stringResource(R.string.none), start != null, onClear = {
                                startMin = -1
                                if (wheel == WheelSlot.START) wheel = null
                            }) { openWheel(WheelSlot.START) }
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.field_end), end?.hm() ?: stringResource(R.string.none), end != null, onClear = {
                                endMin = -1
                                if (wheel == WheelSlot.END) wheel = null
                            }) { openWheel(WheelSlot.END) }
                            Text(
                                stringResource(R.string.label_duration) + " · " +
                                    if (duration % 60 == 0) stringResource(R.string.dur_h, duration / 60) else stringResource(R.string.minutes_n, duration),
                                Modifier.padding(top = 4.dp, start = 32.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    )
                    if (tab == 0) {
                        SettingRow(Icons.Rounded.Schedule, stringResource(R.string.label_time), timeLabel, start != null, onClear = {
                            startMin = -1
                            endMin = -1
                            wheel = null
                        }) { openWheel(WheelSlot.START) }
                    }
                    SettingRow(Icons.Rounded.Alarm, stringResource(R.string.label_remind), remindLabel(remind), remind != null, onClear = { remind = null }) { remindDlg = true }
                    SettingRow(
                        Icons.Rounded.Repeat,
                        stringResource(R.string.label_repeat),
                        freqLabel(freq, until, interval, byDays, date, withUntil = false),
                        freq != null,
                        onClear = {
                            freq = null
                            until = null
                            byDays = emptySet()
                            interval = 1
                        },
                    ) { repeatDlg = true }
                    if (freq != null) {
                        val endLabel =
                            when {
                                until == null -> stringResource(R.string.repeat_end_never)
                                semesterEnd != null && until == semesterEnd -> stringResource(R.string.repeat_end_semester)
                                else -> ctx.monthDayWeekday(until ?: date)
                            }
                        SettingRow(Icons.Rounded.DateRange, stringResource(R.string.repeat_end), endLabel, until != null, onClear = { until = null }) { endDlg = true }
                    }
                    val slot = wheel
                    if (slot != null) {
                        Spacer(Modifier.height(8.dp))
                        val shown = if (slot == WheelSlot.END) endMin.coerceAtLeast(0) % (24 * 60) else startMin.coerceAtLeast(0)
                        Text(
                            stringResource(
                                when {
                                    slot == WheelSlot.END -> R.string.field_end
                                    tab == 0 -> R.string.label_time
                                    else -> R.string.field_start
                                },
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TimeWheels(
                            hour = shown / 60,
                            minute = shown % 60,
                            onHour = { h -> if (slot == WheelSlot.START) applyStart(h, shown % 60) else applyEnd(h, shown % 60) },
                            onMinute = { m -> if (slot == WheelSlot.START) applyStart(shown / 60, m) else applyEnd(shown / 60, m) },
                        )
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

    if (dateDlg) {
        DatePickerDlg(date, { dateDlg = false }) { date = it }
    }
    if (remindDlg) {
        RemindPickDialog(remind, start != null, persistent, { remindDlg = false }) { minutes, nag ->
            remind = minutes
            persistent = nag
            remindDlg = false
        }
    }
    if (repeatDlg) {
        RepeatPickDialog(date, freq, interval, byDays, until, semesterEnd, { repeatDlg = false }) { f, i, days, u ->
            freq = f
            interval = i
            byDays = days
            until = u
            repeatDlg = false
        }
    }
    if (endDlg) {
        EndRepeatDialog(until, semesterEnd, date, { endDlg = false }) {
            until = it
            endDlg = false
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            // Close 图标笔画比 20dp 外框内收约 4dp，未设置的值跟着内收，三条右边才齐
            Modifier.padding(end = if (set) 0.dp else 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 清除钮贴右缘，和未设置时的文字同一条边；不用 IconButton，它自带 12dp 内边距，会把这一行撑高、值往左挤
        if (set) {
            Box(
                Modifier
                    .width(36.dp)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_time), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
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
        2880 -> stringResource(R.string.remind_2d)
        4320 -> stringResource(R.string.remind_3d)
        10080 -> stringResource(R.string.remind_1w)
        else -> if (m % 60 == 0) stringResource(R.string.dur_h, m / 60) else stringResource(R.string.minutes_n, m)
    }

@Composable
internal fun freqLabel(
    f: Freq?,
    until: LocalDate?,
    interval: Int = 1,
    byDays: Set<Int> = emptySet(),
    anchor: LocalDate? = null,
    withUntil: Boolean = true,
): String {
    val ctx = LocalContext.current
    val days = byDays.sorted().joinToString("、") { ctx.weekdayFull(it) }
    val base =
        when {
            f == null -> {
                stringResource(R.string.repeat_none)
            }

            f == Freq.WEEKLY && byDays == WORKDAYS -> {
                if (interval == 1) {
                    stringResource(R.string.repeat_weekdays)
                } else {
                    stringResource(R.string.repeat_on_days, stringResource(R.string.detail_editor_every_n, interval, freqUnit(f)), days)
                }
            }

            f == Freq.WEEKLY && byDays.size > 1 -> {
                stringResource(
                    R.string.repeat_on_days,
                    if (interval == 1) stringResource(R.string.repeat_weekly) else stringResource(R.string.detail_editor_every_n, interval, freqUnit(f)),
                    days,
                )
            }

            f == Freq.WEEKLY && byDays.size == 1 -> {
                if (interval == 1) {
                    stringResource(R.string.repeat_weekly_on, ctx.weekdayFull(byDays.first()))
                } else {
                    stringResource(R.string.repeat_on_days, stringResource(R.string.detail_editor_every_n, interval, freqUnit(f)), days)
                }
            }

            f == Freq.WEEKLY && anchor != null -> {
                if (interval == 1) {
                    stringResource(R.string.repeat_weekly_on, ctx.weekdayFull(anchor.dayOfWeek.value))
                } else {
                    stringResource(R.string.detail_editor_every_n, interval, freqUnit(f))
                }
            }

            f == Freq.MONTHLY && interval == 1 && anchor != null -> {
                stringResource(R.string.repeat_monthly_on, anchor.dayOfMonth)
            }

            f == Freq.YEARLY && interval == 1 && anchor != null -> {
                stringResource(R.string.repeat_yearly_on, anchor.monthValue, anchor.dayOfMonth)
            }

            interval > 1 && f != null -> {
                stringResource(R.string.detail_editor_every_n, interval, freqUnit(f))
            }

            f == Freq.DAILY -> {
                stringResource(R.string.repeat_daily)
            }

            f == Freq.MONTHLY -> {
                stringResource(R.string.repeat_monthly)
            }

            f == Freq.YEARLY -> {
                stringResource(R.string.repeat_yearly)
            }

            else -> {
                stringResource(R.string.repeat_weekly)
            }
        }
    return if (withUntil && until != null) "$base · ${stringResource(R.string.repeat_until_short)}" else base
}

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

/** 提醒。有时刻：提前多少。只有日期：对齐 TickTick，锚在当天 09:00。自定义另开一页填分钟。 */
@Composable
internal fun RemindPickDialog(
    initial: Int?,
    hasTime: Boolean,
    initialPersistent: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int?, Boolean) -> Unit,
) {
    val presets: List<Pair<Int?, String>> =
        if (hasTime) {
            listOf(
                null to stringResource(R.string.none),
                0 to stringResource(R.string.remind_at_due),
                5 to stringResource(R.string.remind_5m),
                10 to stringResource(R.string.remind_10m),
                30 to stringResource(R.string.remind_30m),
                60 to stringResource(R.string.remind_1h),
                1440 to stringResource(R.string.remind_1d),
            )
        } else {
            listOf(
                null to stringResource(R.string.none),
                0 to stringResource(R.string.remind_same_day),
                1440 to stringResource(R.string.remind_before_days, 1),
                2880 to stringResource(R.string.remind_before_days, 2),
                4320 to stringResource(R.string.remind_before_days, 3),
                10080 to stringResource(R.string.remind_before_week),
            )
        }
    val customInit = initial != null && presets.none { it.first == initial }
    var sel by remember { mutableStateOf(if (customInit) -1 else initial) }
    var nag by remember { mutableStateOf(initialPersistent) }
    var customText by remember { mutableStateOf(if (customInit) initial.toString() else "") }
    var customPage by remember { mutableStateOf(false) }
    val customValue = customText.toIntOrNull()
    val customValid = customValue != null && customValue in 0..10080
    val canConfirm = sel != -1 || customValid
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                if (customPage) {
                    Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { customPage = false }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.back))
                        }
                        Text(stringResource(R.string.detail_editor_remind_custom), style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.detail_editor_remind_minutes)) },
                        singleLine = true,
                        isError = customText.isNotEmpty() && !customValid,
                        supportingText =
                            if (customText.isNotEmpty() && !customValid) {
                                { Text(stringResource(R.string.detail_minutes_invalid, 0, 10080)) }
                            } else {
                                null
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.label_remind),
                        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    presets.forEach { (v, label) ->
                        RemindOption(label, selected = sel == v) { sel = v }
                    }
                    RemindOption(stringResource(R.string.detail_editor_remind_custom), selected = sel == -1, chevron = true) {
                        sel = -1
                        customPage = true
                    }
                }
                val chosen = if (sel == -1) customValue.takeIf { customValid } else sel
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.remind_persistent), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.remind_persistent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = nag && chosen != null,
                        onCheckedChange = { nag = it },
                        enabled = chosen != null,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary) }
                    TextButton(enabled = canConfirm, onClick = { onPick(if (sel == -1) customValue!! else sel, nag && chosen != null) }) {
                        Text(stringResource(R.string.ok), color = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindOption(
    label: String,
    selected: Boolean,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else if (selected) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

private val WORKDAYS = setOf(1, 2, 3, 4, 5)

private enum class RepeatPreset { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, WORKDAYS, SEMESTER, CUSTOM }

private fun followsAnchor(
    byDays: Set<Int>,
    anchor: LocalDate,
): Boolean = byDays.isEmpty() || byDays == setOf(anchor.dayOfWeek.value)

private fun whichPreset(
    freq: Freq?,
    interval: Int,
    byDays: Set<Int>,
    until: LocalDate?,
    anchor: LocalDate,
    semesterEnd: LocalDate?,
): RepeatPreset {
    if (freq == null) return RepeatPreset.NONE
    if (interval != 1) return RepeatPreset.CUSTOM
    return when (freq) {
        Freq.DAILY -> {
            RepeatPreset.DAILY
        }

        Freq.MONTHLY -> {
            RepeatPreset.MONTHLY
        }

        Freq.YEARLY -> {
            RepeatPreset.YEARLY
        }

        Freq.WEEKLY -> {
            when {
                byDays == WORKDAYS -> RepeatPreset.WORKDAYS
                followsAnchor(byDays, anchor) && semesterEnd != null && until == semesterEnd -> RepeatPreset.SEMESTER
                followsAnchor(byDays, anchor) -> RepeatPreset.WEEKLY
                else -> RepeatPreset.CUSTOM
            }
        }
    }
}

/** 重复。点一项就生效；自定义里选间隔和星期。结束日不在这里，面板上单独一行。 */
@Composable
internal fun RepeatPickDialog(
    anchor: LocalDate,
    freqInitial: Freq?,
    intervalInitial: Int,
    byDaysInitial: Set<Int>,
    untilInitial: LocalDate?,
    semesterEnd: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (Freq?, Int, Set<Int>, LocalDate?) -> Unit,
) {
    val ctx = LocalContext.current
    val dow = anchor.dayOfWeek.value
    val preset = whichPreset(freqInitial, intervalInitial, byDaysInitial, untilInitial, anchor, semesterEnd)
    val semesterOk = semesterEnd != null && !anchor.isAfter(semesterEnd)
    var customPage by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(freqInitial ?: Freq.WEEKLY) }
    var every by remember { mutableIntStateOf(intervalInitial.coerceIn(1, 99)) }
    var days by remember { mutableStateOf(byDaysInitial.ifEmpty { setOf(dow) }) }
    val numbers = (1..8).let { base -> if (every in base) base.toList() else (base.toList() + every).sorted() }

    fun toggleDay(d: Int) {
        days = if (d in days && days.size > 1) days - d else days + d
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                if (customPage) {
                    Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { customPage = false }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.back))
                        }
                        Text(stringResource(R.string.repeat_custom), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        freqLabel(unit, null, every, if (unit == Freq.WEEKLY) days else emptySet(), anchor, withUntil = false),
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.repeat_freq),
                        Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        numbers.forEach { n ->
                            DayPill(n.toString(), every == n, Modifier.weight(1f)) { every = n }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Freq.DAILY, Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY).forEach { f ->
                            DayPill(freqUnit(f), unit == f, Modifier.weight(1f)) { unit = f }
                        }
                    }
                    if (unit == Freq.WEEKLY) {
                        Text(
                            stringResource(R.string.weekday_label),
                            Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { d -> DayPill(ctx.weekdayFull(d), d in days, Modifier.weight(1f)) { toggleDay(d) } }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (5..7).forEach { d -> DayPill(ctx.weekdayFull(d), d in days, Modifier.weight(1f)) { toggleDay(d) } }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.label_repeat),
                        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    RemindOption(stringResource(R.string.none), preset == RepeatPreset.NONE) { onPick(null, 1, emptySet(), null) }
                    RemindOption(stringResource(R.string.repeat_daily), preset == RepeatPreset.DAILY) { onPick(Freq.DAILY, 1, emptySet(), untilInitial) }
                    RemindOption(stringResource(R.string.repeat_weekly_on, ctx.weekdayFull(dow)), preset == RepeatPreset.WEEKLY) {
                        onPick(Freq.WEEKLY, 1, emptySet(), untilInitial)
                    }
                    RemindOption(stringResource(R.string.repeat_monthly_on, anchor.dayOfMonth), preset == RepeatPreset.MONTHLY) {
                        onPick(Freq.MONTHLY, 1, emptySet(), untilInitial)
                    }
                    RemindOption(stringResource(R.string.repeat_yearly_on, anchor.monthValue, anchor.dayOfMonth), preset == RepeatPreset.YEARLY) {
                        onPick(Freq.YEARLY, 1, emptySet(), untilInitial)
                    }
                    Hairline()
                    RemindOption(
                        stringResource(R.string.repeat_weekdays) + "（" + stringResource(R.string.repeat_weekdays_span) + "）",
                        preset == RepeatPreset.WORKDAYS,
                    ) { onPick(Freq.WEEKLY, 1, WORKDAYS, untilInitial) }
                    if (semesterOk) {
                        Hairline()
                        RemindOption(stringResource(R.string.repeat_semester, ctx.weekdayFull(dow)), preset == RepeatPreset.SEMESTER) {
                            onPick(Freq.WEEKLY, 1, emptySet(), semesterEnd)
                        }
                    }
                    Hairline()
                    RemindOption(stringResource(R.string.repeat_custom), preset == RepeatPreset.CUSTOM, chevron = true) { customPage = true }
                }
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                    if (customPage) {
                        TextButton(onClick = { customPage = false }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary) }
                        TextButton(onClick = {
                            onPick(unit, every, if (unit == Freq.WEEKLY) days else emptySet(), untilInitial)
                        }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary) }
                    } else {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

/** 结束重复：不结束、本学期最后一天，或指定一天。 */
@Composable
private fun EndRepeatDialog(
    until: LocalDate?,
    semesterEnd: LocalDate?,
    anchor: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate?) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    val semesterOk = semesterEnd != null && !anchor.isAfter(semesterEnd)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.repeat_end),
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                RemindOption(stringResource(R.string.repeat_end_never), until == null) { onPick(null) }
                if (semesterOk) {
                    RemindOption(stringResource(R.string.repeat_end_semester), until == semesterEnd) { onPick(semesterEnd) }
                }
                RemindOption(stringResource(R.string.repeat_end_pick), until != null && until != semesterEnd, chevron = true) { pick = true }
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
    if (pick) DatePickerDlg(until ?: anchor.plusMonths(3), { pick = false }) { onPick(it) }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun DayPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
