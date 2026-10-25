package app.mintcal.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mintcal.R
import app.mintcal.core.model.Freq
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

/** 编辑页里「何时」：哪一天 +（可选）几点到几点。没有时刻就是一条只有日期的待办。 */
internal data class WhenPick(
    val date: LocalDate,
    val start: LocalTime?,
    val minutes: Int = 60,
) {
    val end: LocalTime? get() = start?.let { it.plusMinutes(minutes.toLong()) }
}

/** 时间/提醒/重复三行的结果；when 为 null 表示连日期都不要。 */
internal data class TimeResult(
    val whenPick: WhenPick?,
    val remind: Int?,
    val freq: Freq?,
    val until: LocalDate?,
)

private const val SLOT_MIN = 30
private const val SLOTS = 24 * 60 / SLOT_MIN

/**
 * 时间设置（对齐 TickTick）：上面「日期 | 时间段」两个页签，日期页是月历，
 * 时间段页是半小时块状图（点=1 小时，长按拖动=框选）；下面固定三行时间 / 提醒 / 重复。
 */
@Composable
internal fun EventTimeSheet(
    initial: WhenPick?,
    remindInitial: Int?,
    freqInitial: Freq?,
    untilInitial: LocalDate?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onPick: (TimeResult?) -> Unit,
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startMin by remember { mutableIntStateOf(initial?.start?.let { it.hour * 60 + it.minute } ?: -1) }
    var endMin by remember { mutableIntStateOf(initial?.let { p -> p.start?.let { it.hour * 60 + it.minute + p.minutes } } ?: -1) }
    var remind by remember { mutableStateOf(remindInitial) }
    var freq by remember { mutableStateOf(freqInitial) }
    var until by remember { mutableStateOf(untilInitial) }
    var tab by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var timeDlg by remember { mutableStateOf(false) }
    var untilDlg by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    val start = if (startMin >= 0) LocalTime.of(startMin / 60, startMin % 60) else null
    val result =
        TimeResult(
            whenPick = start?.let { WhenPick(date, it, (endMin - startMin).coerceAtLeast(SLOT_MIN)) } ?: WhenPick(date, null),
            remind = remind,
            freq = freq,
            until = until,
        )

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
                            SettingRow(Icons.Rounded.Schedule, stringResource(R.string.sheet_time), start?.hm() ?: stringResource(R.string.none), start != null, onClear = {
                                startMin = -1
                                endMin = -1
                            }) { timeDlg = true }
                            SettingRow(Icons.Rounded.Alarm, stringResource(R.string.label_remind), remindLabel(remind), remind != null, onClear = { remind = null }) { remind = nextRemind(remind) }
                            SettingRow(Icons.Rounded.Repeat, stringResource(R.string.label_repeat), freqLabel(freq, until), freq != null, onClear = {
                                freq = null
                                until = null
                            }) { freq = nextFreq(freq) }
                            if (freq != null) {
                                TextButton(onClick = { untilDlg = true }) {
                                    Text(stringResource(R.string.repeat_until_value, until?.let { ctx.monthDayWeekday(it) } ?: stringResource(R.string.never)))
                                }
                            }
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
                                    if (start != null) "${start.hm()} – ${result.whenPick?.end?.hm().orEmpty()}" else stringResource(R.string.pick_time_first),
                                    modifier = Modifier.weight(1f).clickable { dateDlg = true },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                            }
                            Text(
                                stringResource(R.string.long_press_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HalfHourGrid(
                                startMin = startMin,
                                endMin = endMin,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onChange = { s, e ->
                                    startMin = s
                                    endMin = e
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

    if (timeDlg) {
        TimePickerDlg(start ?: LocalTime.of(18, 0), { timeDlg = false }) { t ->
            val dur = (endMin - startMin).takeIf { it >= SLOT_MIN } ?: 60
            startMin = t.hour * 60 + t.minute
            endMin = (startMin + dur).coerceAtMost(24 * 60)
        }
    }
    if (untilDlg) {
        DatePickerDlg(until ?: date.plusMonths(3), { untilDlg = false }) { until = it }
    }
    if (dateDlg) {
        DatePickerDlg(date, { dateDlg = false }) { date = it }
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

@Composable
private fun SettingRow(
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
private fun remindLabel(m: Int?): String =
    when (m) {
        null -> stringResource(R.string.none)
        0 -> stringResource(R.string.remind_at_due)
        10 -> stringResource(R.string.remind_10m)
        60 -> stringResource(R.string.remind_1h)
        1440 -> stringResource(R.string.remind_1d)
        else -> if (m % 60 == 0) stringResource(R.string.dur_h, m / 60) else stringResource(R.string.minutes_n, m)
    }

private fun nextRemind(m: Int?): Int? =
    when (m) {
        null -> 0
        0 -> 10
        10 -> 60
        60 -> 1440
        else -> null
    }

@Composable
private fun freqLabel(
    f: Freq?,
    until: LocalDate?,
): String =
    when (f) {
        null -> stringResource(R.string.repeat_none)
        Freq.DAILY -> stringResource(R.string.repeat_daily)
        Freq.WEEKLY -> stringResource(R.string.repeat_weekly)
        Freq.MONTHLY -> stringResource(R.string.repeat_monthly)
        Freq.YEARLY -> stringResource(R.string.repeat_yearly)
    }.let { if (until != null) "$it · ${stringResource(R.string.repeat_until_short)}" else it }

private fun nextFreq(f: Freq?): Freq? =
    when (f) {
        null -> Freq.WEEKLY
        Freq.WEEKLY -> Freq.DAILY
        Freq.DAILY -> Freq.MONTHLY
        Freq.MONTHLY -> Freq.YEARLY
        Freq.YEARLY -> null
    }

/** 半小时一格的纵向时间块图：点=一小时，长按拖动=框选。 */
@Composable
private fun HalfHourGrid(
    startMin: Int,
    endMin: Int,
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
                    onChange(s * SLOT_MIN, ((s + 2).coerceAtMost(SLOTS)) * SLOT_MIN)
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
