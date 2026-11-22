package app.goke.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.model.Freq
import app.goke.ui.components.DatePickerDlg
import app.goke.ui.components.MinutesDialSheet
import app.goke.ui.components.MonthPager
import app.goke.ui.components.TimeDialSheet
import app.goke.ui.components.mintClickable
import app.goke.ui.components.rememberModalSheetState
import app.goke.ui.hm
import app.goke.ui.monthDayWeekday
import app.goke.ui.theme.MintTheme
import app.goke.ui.weekdayFull
import app.goke.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 编辑页里「何时」：哪一天 +（可选）几点。[minutes] 为空是待办——有时刻就是截止时刻，
 * 没有时刻就是只有日期；填了持续就是日程，按开始 + 时长排进日历。
 */
internal data class WhenPick(
    val date: LocalDate,
    val start: LocalTime?,
    val minutes: Int? = null,
) {
    /** 持续非空即日程（时间块）。 */
    val block: Boolean get() = start != null && minutes != null

    val end: LocalTime? get() = start?.let { s -> minutes?.let { m -> s.plusMinutes(m.toLong()) } }
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
    val byMonths: Set<Int> = emptySet(),
)

/**
 * 「日期&持续」面板，快捷添加条和编辑页共用：
 * 快捷芯片（今天/明天/下周一/今天晚上）+ 月历 + 时间 / 持续 / 提醒 / 重复一行一项。
 * 时间弹圆盘选时刻；持续留空是待办，填了就按开始 + 时长排进日历。
 * 底部固定 清除 · 取消 · 确定；往下拉面板跟手，松手过四分之一高度即收起。
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
    initialByMonths: Set<Int> = emptySet(),
    semesterEnd: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (WhenPanelResult) -> Unit,
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var startMin by remember { mutableIntStateOf(initial?.start?.let { it.hour * 60 + it.minute } ?: -1) }
    var minutes by remember { mutableStateOf(initial?.minutes) }
    var remind by remember { mutableStateOf(initialRemind) }
    var persistent by remember { mutableStateOf(initialPersistent && initialRemind != null) }
    var freq by remember { mutableStateOf(initialFreq) }
    var interval by remember { mutableIntStateOf(initialInterval) }
    var until by remember { mutableStateOf(initialUntil) }
    var byDays by remember { mutableStateOf(initialByDays) }
    var byMonths by remember { mutableStateOf(initialByMonths) }
    var datePicked by remember { mutableStateOf(initial != null) }
    var timeDlg by remember { mutableStateOf(false) }
    var durDlg by remember { mutableStateOf(false) }
    var wantDur by remember { mutableStateOf(false) }
    var remindDlg by remember { mutableStateOf(false) }
    var repeatDlg by remember { mutableStateOf(false) }
    var endDlg by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val start = if (startMin >= 0) LocalTime.of(startMin / 60 % 24, startMin % 60) else null
    val result = WhenPick(date, start, minutes.takeIf { start != null })

    fun close(whenPick: WhenPick?) {
        onPick(
            if (whenPick == null) {
                WhenPanelResult(
                    whenPick = null,
                    remindMinutes = null,
                    remindPersistent = false,
                    freq = null,
                    interval = 1,
                    until = null,
                    byDays = emptySet(),
                    byMonths = emptySet(),
                )
            } else {
                WhenPanelResult(
                    whenPick = whenPick,
                    remindMinutes = remind,
                    remindPersistent = remind != null && persistent,
                    freq = freq,
                    interval = interval,
                    until = if (freq == null) null else until,
                    byDays = if (freq == Freq.WEEKLY || freq == Freq.MONTHLY || freq == Freq.YEARLY) byDays else emptySet(),
                    byMonths = if (freq == Freq.YEARLY) byMonths else emptySet(),
                )
            },
        )
    }
    val m = minutes
    val timeLabel = start?.hm() ?: stringResource(R.string.none)
    val durText =
        when {
            start == null || m == null -> stringResource(R.string.none)
            m % 60 == 0 -> stringResource(R.string.dur_h, m / 60)
            else -> stringResource(R.string.minutes_n, m)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState())) {
                val isTonight = date == today && startMin == 20 * 60 && minutes == null
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    QuickChip(Icons.Rounded.CalendarToday, stringResource(R.string.today), date == today && !isTonight, Modifier.weight(1f)) {
                        date = today
                        datePicked = true
                        if (isTonight) startMin = -1
                    }
                    QuickChip(Icons.Rounded.WbSunny, stringResource(R.string.tomorrow), date == today.plusDays(1), Modifier.weight(1f)) {
                        date = today.plusDays(1)
                        datePicked = true
                    }
                    QuickChip(Icons.Rounded.DateRange, stringResource(R.string.date_next_monday), date == nextMonday(today), Modifier.weight(1f)) {
                        date = nextMonday(today)
                        datePicked = true
                    }
                    QuickChip(Icons.Rounded.Bedtime, stringResource(R.string.tonight), isTonight, Modifier.weight(1f)) {
                        date = today
                        datePicked = true
                        startMin = 20 * 60
                        minutes = null
                    }
                }
                MonthPager(selected = date, initial = YearMonth.from(date), onPick = {
                    date = it
                    datePicked = true
                })
                Spacer(Modifier.height(10.dp))
                SettingRow(Icons.Rounded.Schedule, stringResource(R.string.label_time), timeLabel, start != null, onClear = {
                    startMin = -1
                    minutes = null
                    timeDlg = false
                }) { timeDlg = true }
                // 持续常驻：没选开始时间时点它先弹时刻，选完接着弹时长
                SettingRow(Icons.Rounded.Timer, stringResource(R.string.label_lasting), durText, minutes != null, onClear = { minutes = null }) {
                    if (start != null) {
                        durDlg = true
                    } else {
                        timeDlg = true
                        wantDur = true
                    }
                }
                if (start != null && (m ?: 0) > 180) {
                    Text(
                        text = stringResource(R.string.timetable_over_3h_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
                    )
                }
                SettingRow(Icons.Rounded.Alarm, stringResource(R.string.label_remind), remindLabel(remind), remind != null, onClear = { remind = null }) { remindDlg = true }
                SettingRow(
                    Icons.Rounded.Repeat,
                    stringResource(R.string.label_repeat),
                    freqLabel(freq, until, interval, byDays, date, withUntil = false, byMonths = byMonths),
                    freq != null,
                    onClear = {
                        freq = null
                        until = null
                        byDays = emptySet()
                        byMonths = emptySet()
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
            }
            val hasAnySet = datePicked || startMin >= 0 || minutes != null || remind != null || freq != null
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasAnySet) {
                    TextButton(onClick = { close(null) }) {
                        Text(stringResource(R.string.clear_time), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { close(result) }) {
                    Text(stringResource(R.string.ok), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (timeDlg) {
        TimeDialSheet(
            initial = start ?: LocalTime.of(18, 0),
            onDismiss = {
                timeDlg = false
                wantDur = false
            },
        ) { t ->
            startMin = t.hour * 60 + t.minute
            timeDlg = false
            // 点的是「持续」但还没有开始时间：选完时刻接着弹时长
            if (wantDur) {
                wantDur = false
                durDlg = true
            }
        }
    }
    if (durDlg) {
        // 从 00:00 起拨；确认 0 视为未填（仍是待办）
        MinutesDialSheet(initialMinutes = minutes ?: 0, onDismiss = { durDlg = false }) { v ->
            minutes = v.takeIf { it > 0 }
            durDlg = false
        }
    }
    if (remindDlg) {
        RemindPickDialog(remind, start != null, persistent, { remindDlg = false }) { minutes, nag ->
            remind = minutes
            persistent = nag
            remindDlg = false
        }
    }
    if (repeatDlg) {
        RepeatPickDialog(date, freq, interval, byDays, byMonths, until, semesterEnd, onDismiss = { repeatDlg = false }) { f, i, days, months, u ->
            freq = f
            interval = i
            byDays = days
            byMonths = months
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
            .semantics {
                role = Role.RadioButton
                selected = active
            }.mintClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** 应用统一的弹层：M3 ModalBottomSheet、28dp 顶角、拖拽把手、导航栏避让，与改期/晨间整理一致。 */
@Composable
private fun PickerSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
            content = content,
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
            .mintClickable(pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (set) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            // Close 图标笔画比 20dp 外框内收约 4dp，未设置的值跟着内收，三条右边才齐
            Modifier.padding(end = if (set) 0.dp else 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        // 清除钮贴右缘，和未设置时的文字同一条边；不用 IconButton，它自带 12dp 内边距，会把这一行撑高、值往左挤
        if (set) {
            Box(
                Modifier
                    .width(48.dp)
                    .heightIn(min = 48.dp)
                    .mintClickable(pressedScale = 0.92f, role = Role.Button, onClick = onClear),
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
        else -> if (m % 60 == 0) stringResource(R.string.remind_hours_value, m / 60) else stringResource(R.string.remind_minutes_value, m)
    }

@Composable
internal fun freqLabel(
    f: Freq?,
    until: LocalDate?,
    interval: Int = 1,
    byDays: Set<Int> = emptySet(),
    anchor: LocalDate? = null,
    withUntil: Boolean = true,
    byMonths: Set<Int> = emptySet(),
): String {
    val ctx = LocalContext.current
    val sep = stringResource(R.string.list_sep)
    val days = byDays.sorted().joinToString(sep) { ctx.weekdayFull(it) }
    // 每月 / 每年多选：把天数、月日拼成「1、15日」「3月5日、10月12日」
    val monthDaysText = byDays.sorted().joinToString(sep)
    val yearDates = mutableListOf<String>()
    if (f == Freq.YEARLY) {
        val ms = (byMonths.ifEmpty { anchor?.let { setOf(it.monthValue) } ?: emptySet() }).sorted()
        val ds = (byDays.ifEmpty { anchor?.let { setOf(it.dayOfMonth) } ?: emptySet() }).sorted()
        for (mo in ms) {
            for (d in ds) yearDates += stringResource(R.string.repeat_month_day, mo, d)
        }
    }
    val period = if (interval == 1) null else stringResource(R.string.detail_editor_every_n, interval, freqUnit(f ?: Freq.WEEKLY))
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

            f == Freq.MONTHLY && byDays.size > 1 -> {
                stringResource(
                    R.string.repeat_on_days,
                    period ?: stringResource(R.string.repeat_monthly),
                    stringResource(R.string.repeat_days_of_month, monthDaysText),
                )
            }

            f == Freq.MONTHLY && byDays.size == 1 -> {
                if (interval == 1) {
                    stringResource(R.string.repeat_monthly_on, byDays.first())
                } else {
                    stringResource(
                        R.string.repeat_on_days,
                        period.orEmpty(),
                        stringResource(R.string.repeat_days_of_month, monthDaysText),
                    )
                }
            }

            f == Freq.MONTHLY && interval == 1 && anchor != null -> {
                stringResource(R.string.repeat_monthly_on, anchor.dayOfMonth)
            }

            f == Freq.YEARLY && yearDates.isNotEmpty() -> {
                stringResource(
                    R.string.repeat_on_days,
                    period ?: stringResource(R.string.repeat_yearly),
                    yearDates.joinToString(sep),
                )
            }

            f == Freq.YEARLY && interval == 1 && anchor != null -> {
                stringResource(R.string.repeat_yearly_on, anchor.monthValue, anchor.dayOfMonth)
            }

            interval > 1 -> {
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
    noneLabel: String = stringResource(R.string.none),
    showPersistent: Boolean = true,
    onPick: (Int?, Boolean) -> Unit,
) {
    val presets: List<Pair<Int?, String>> =
        if (hasTime) {
            listOf(
                null to noneLabel,
                0 to stringResource(R.string.remind_at_due),
                5 to stringResource(R.string.remind_5m),
                10 to stringResource(R.string.remind_10m),
                15 to stringResource(R.string.remind_15m),
                30 to stringResource(R.string.remind_30m),
                60 to stringResource(R.string.remind_1h),
                1440 to stringResource(R.string.remind_1d),
            )
        } else {
            listOf(
                null to noneLabel,
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
    PickerSheet(onDismiss) {
        Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            if (customPage) {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { customPage = false }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.detail_editor_remind_custom), style = MaterialTheme.typography.titleMedium)
                }
                app.goke.ui.components.TonalField(
                    label = stringResource(R.string.detail_editor_remind_minutes),
                    value = customText,
                    onChange = { customText = it.filter { ch -> ch.isDigit() }.take(5) },
                    number = true,
                    isError = customText.isNotEmpty() && !customValid,
                    supporting =
                        if (customText.isNotEmpty() && !customValid) {
                            stringResource(R.string.detail_minutes_invalid, 0, 10080)
                        } else {
                            stringResource(R.string.detail_remind_zero_hint)
                        },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    stringResource(R.string.label_remind),
                    Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
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
            if (showPersistent) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
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
            }
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(enabled = canConfirm, onClick = { onPick(if (sel == -1) customValue!! else sel, nag && chosen != null) }) {
                    Text(
                        stringResource(R.string.ok),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            .semantics {
                role = if (chevron) Role.Button else Role.RadioButton
                this.selected = selected
            }.mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 4.dp),
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
    byMonths: Set<Int>,
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
            if (byDays.isEmpty()) RepeatPreset.MONTHLY else RepeatPreset.CUSTOM
        }

        Freq.YEARLY -> {
            if (byMonths.isEmpty() && byDays.isEmpty()) RepeatPreset.YEARLY else RepeatPreset.CUSTOM
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

/** 重复。点一项就生效；自定义里选间隔、星期几 / 月内哪几天 / 每年哪几月哪几天。结束日不在这里，面板上单独一行。 */
@Composable
internal fun RepeatPickDialog(
    anchor: LocalDate,
    freqInitial: Freq?,
    intervalInitial: Int,
    byDaysInitial: Set<Int>,
    byMonthsInitial: Set<Int>,
    untilInitial: LocalDate?,
    semesterEnd: LocalDate?,
    /** 直接打开自定义页（截图与回访校验用）。 */
    startCustomPage: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (Freq?, Int, Set<Int>, Set<Int>, LocalDate?) -> Unit,
) {
    val ctx = LocalContext.current
    val dow = anchor.dayOfWeek.value
    val preset = whichPreset(freqInitial, intervalInitial, byDaysInitial, byMonthsInitial, untilInitial, anchor, semesterEnd)
    val semesterOk = semesterEnd != null && !anchor.isAfter(semesterEnd)
    var customPage by remember { mutableStateOf(startCustomPage) }
    var unit by remember { mutableStateOf(freqInitial ?: Freq.WEEKLY) }
    var every by remember { mutableIntStateOf(intervalInitial.coerceIn(1, 99)) }
    // 三套「日」各管一段：星期几、月内哪几天、每年哪几月
    var weekdays by remember { mutableStateOf(if (freqInitial == Freq.WEEKLY && byDaysInitial.isNotEmpty()) byDaysInitial else setOf(dow)) }
    var monthDays by remember {
        mutableStateOf(if ((freqInitial == Freq.MONTHLY || freqInitial == Freq.YEARLY) && byDaysInitial.isNotEmpty()) byDaysInitial else setOf(anchor.dayOfMonth))
    }
    var months by remember { mutableStateOf(byMonthsInitial.ifEmpty { setOf(anchor.monthValue) }) }
    val pickedDays =
        when (unit) {
            Freq.WEEKLY -> weekdays
            Freq.MONTHLY, Freq.YEARLY -> monthDays
            Freq.DAILY -> emptySet()
        }
    val pickedMonths = if (unit == Freq.YEARLY) months else emptySet()

    fun toggle(
        set: Set<Int>,
        d: Int,
        apply: (Set<Int>) -> Unit,
    ) {
        apply(if (d in set && set.size > 1) set - d else set + d)
    }
    PickerSheet(onDismiss) {
        Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            if (customPage) {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { customPage = false }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.repeat_custom), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    freqLabel(unit, null, every, pickedDays, anchor, withUntil = false, byMonths = pickedMonths),
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                // 31 天 + 12 月的网格可能超过一屏，局部滚动，底部按钮始终看得见
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.repeat_freq),
                        Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.repeat_every), style = MaterialTheme.typography.bodyLarge)
                        StepPill(Icons.Rounded.Remove, stringResource(R.string.repeat_interval_down), every > 1) { every -= 1 }
                        Text(
                            every.toString(),
                            Modifier.width(40.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.Center,
                        )
                        StepPill(Icons.Rounded.Add, stringResource(R.string.repeat_interval_up), every < 99) { every += 1 }
                        listOf(Freq.DAILY, Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY).forEach { f ->
                            DayPill(freqUnit(f), unit == f, Modifier.weight(1f)) { unit = f }
                        }
                    }
                    if (unit == Freq.WEEKLY) {
                        GridLabel(stringResource(R.string.weekday_label))
                        PillRow((1..7).toList()) { d ->
                            DayPill(ctx.weekdayShort(d), d in weekdays, Modifier.weight(1f)) { toggle(weekdays, d) { weekdays = it } }
                        }
                    }
                    if (unit == Freq.MONTHLY || unit == Freq.YEARLY) {
                        if (unit == Freq.YEARLY) {
                            GridLabel(stringResource(R.string.repeat_pick_months))
                            PillRow((1..12).toList(), perRow = 6) { m ->
                                DayPill(stringResource(R.string.repeat_month_n, m), m in months, Modifier.weight(1f)) {
                                    toggle(months, m) { months = it }
                                }
                            }
                        }
                        GridLabel(
                            if (unit == Freq.MONTHLY) {
                                stringResource(R.string.repeat_pick_month_days)
                            } else {
                                stringResource(R.string.repeat_pick_also_month_days)
                            },
                        )
                        PillRow((1..31).toList(), perRow = 7) { d ->
                            DayPill(d.toString(), d in monthDays, Modifier.weight(1f)) { toggle(monthDays, d) { monthDays = it } }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            } else {
                Text(
                    stringResource(R.string.label_repeat),
                    Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                RemindOption(stringResource(R.string.none), preset == RepeatPreset.NONE) { onPick(null, 1, emptySet(), emptySet(), null) }
                RemindOption(stringResource(R.string.repeat_daily), preset == RepeatPreset.DAILY) { onPick(Freq.DAILY, 1, emptySet(), emptySet(), untilInitial) }
                RemindOption(stringResource(R.string.repeat_weekly_on, ctx.weekdayFull(dow)), preset == RepeatPreset.WEEKLY) {
                    onPick(Freq.WEEKLY, 1, emptySet(), emptySet(), untilInitial)
                }
                RemindOption(stringResource(R.string.repeat_monthly_on, anchor.dayOfMonth), preset == RepeatPreset.MONTHLY) {
                    onPick(Freq.MONTHLY, 1, emptySet(), emptySet(), untilInitial)
                }
                RemindOption(stringResource(R.string.repeat_yearly_on, anchor.monthValue, anchor.dayOfMonth), preset == RepeatPreset.YEARLY) {
                    onPick(Freq.YEARLY, 1, emptySet(), emptySet(), untilInitial)
                }
                Spacer(Modifier.height(6.dp))
                RemindOption(
                    stringResource(R.string.repeat_weekdays) + "（" + stringResource(R.string.repeat_weekdays_span) + "）",
                    preset == RepeatPreset.WORKDAYS,
                ) { onPick(Freq.WEEKLY, 1, WORKDAYS, emptySet(), untilInitial) }
                if (semesterOk) {
                    Spacer(Modifier.height(6.dp))
                    RemindOption(stringResource(R.string.repeat_semester, ctx.weekdayFull(dow)), preset == RepeatPreset.SEMESTER) {
                        onPick(Freq.WEEKLY, 1, emptySet(), emptySet(), semesterEnd)
                    }
                }
                Spacer(Modifier.height(6.dp))
                RemindOption(stringResource(R.string.repeat_custom), preset == RepeatPreset.CUSTOM, chevron = true) { customPage = true }
            }
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                if (customPage) {
                    TextButton(onClick = { customPage = false }) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = {
                        onPick(unit, every, pickedDays, pickedMonths, untilInitial)
                    }) { Text(stringResource(R.string.ok), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
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
    PickerSheet(onDismiss) {
        Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.repeat_end),
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            RemindOption(stringResource(R.string.repeat_end_never), until == null) { onPick(null) }
            if (semesterOk) {
                RemindOption(stringResource(R.string.repeat_end_semester), until == semesterEnd) { onPick(semesterEnd) }
            }
            RemindOption(stringResource(R.string.repeat_end_pick), until != null && until != semesterEnd, chevron = true) { pick = true }
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (pick) DatePickerDlg(until ?: anchor.plusMonths(3), { pick = false }) { onPick(it) }
}

/** 小标题：网格上方的一行弱化文字。 */
@Composable
private fun GridLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 等宽胶囊网格：每行 [perRow] 个，不足补弹簧占位。 */
@Composable
private fun PillRow(
    items: List<Int>,
    perRow: Int = 7,
    content: @Composable RowScope.(Int) -> Unit,
) {
    items.chunked(perRow).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.forEach { content(it) }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 步进小圆钮：− / +，禁用时降透明度。 */
@Composable
private fun StepPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else 0.4f))
            .mintClickable(enabled = enabled, pressedScale = 0.94f, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .mintClickable(pressedScale = 0.95f, onClick = onClick)
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
