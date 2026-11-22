package app.goke.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goke.R
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 圆形时间表盘：M3 时钟样式——大数字读数（激活段高亮）、时 / 分切换、圆盘上拨选，
 * 选完小时自动进入分钟；以应用统一的底部弹层呈现。
 * [MinutesDialSheet] 选时长（小时+分钟 → 总分钟数），[TimeDialSheet] 选时刻。
 */
private const val DIAL_SIZE = 260

/** 24 小时制双环数字：外环 0 与 13-23，内环 12 起头，下标即表盘 30° 一格的位置。 */
private val HOUR_OUTER = intArrayOf(0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
private val HOUR_INNER = intArrayOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

private enum class DialMode {
    HOUR,
    MINUTE,
}

/** 时长表盘：显示 [initialMinutes]，在盘上拨小时与分钟，确认回调总分钟数（不超过 [maxMinutes]）。 */
@Composable
fun MinutesDialSheet(
    initialMinutes: Int,
    maxMinutes: Int = 24 * 60,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var hours by remember { mutableIntStateOf(initialMinutes.coerceIn(0, maxMinutes) / 60) }
    var minutes by remember { mutableIntStateOf(initialMinutes.coerceIn(0, maxMinutes) % 60) }
    var mode by remember { mutableStateOf(DialMode.HOUR) }

    fun clamp(
        h: Int,
        m: Int,
    ): Pair<Int, Int> = if (h * 60 + m > maxMinutes) maxMinutes / 60 to maxMinutes % 60 else h to m

    val presets =
        remember(maxMinutes) {
            listOf(15, 30, 45, 60, 90).filter { it <= maxMinutes }
        }

    DialDialog(
        hours = hours,
        minutes = minutes,
        mode = mode,
        onMode = { mode = it },
        onHours = { hours = clamp(it, minutes).first },
        onMinutes = { minutes = clamp(hours, it).second },
        onHourPicked = { mode = DialMode.MINUTE },
        a11y = hourMinuteDesc(hours, minutes),
        presets = presets,
        onDismiss = onDismiss,
        onConfirm = {
            onPick(clamp(hours, minutes).let { it.first * 60 + it.second })
            onDismiss()
        },
    )
}

/** 时刻表盘：与 M3 TimePicker 同构的 24 小时制拨盘。 */
@Composable
fun TimeDialSheet(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    var hours by remember { mutableIntStateOf(initial.hour) }
    var minutes by remember { mutableIntStateOf(initial.minute) }
    var mode by remember { mutableStateOf(DialMode.HOUR) }

    DialDialog(
        hours = hours,
        minutes = minutes,
        mode = mode,
        onMode = { mode = it },
        onHours = { hours = it },
        onMinutes = { minutes = it },
        onHourPicked = { mode = DialMode.MINUTE },
        a11y = hourMinuteDesc(hours, minutes),
        presets = null,
        onDismiss = onDismiss,
        onConfirm = {
            onPick(LocalTime.of(hours, minutes))
            onDismiss()
        },
    )
}

@Composable
private fun hourMinuteDesc(
    hours: Int,
    minutes: Int,
): String = "${stringResource(R.string.dial_hours)} $hours，${stringResource(R.string.dial_minutes)} $minutes"

@Composable
private fun DialDialog(
    hours: Int,
    minutes: Int,
    mode: DialMode,
    onMode: (DialMode) -> Unit,
    onHours: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onHourPicked: () -> Unit,
    a11y: String,
    presets: List<Int>? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.semantics { contentDescription = a11y },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Digit(
                    text = "%02d".format(hours),
                    desc = stringResource(R.string.dial_hours),
                    active = mode == DialMode.HOUR,
                ) { onMode(DialMode.HOUR) }
                Text(
                    ":",
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Digit(
                    text = "%02d".format(minutes),
                    desc = stringResource(R.string.dial_minutes),
                    active = mode == DialMode.MINUTE,
                ) { onMode(DialMode.MINUTE) }
            }
            if (presets != null && presets.isNotEmpty()) {
                val currentTotal = hours * 60 + minutes
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    presets.forEach { p ->
                        val sel = currentTotal == p
                        val label =
                            if (p < 60) {
                                "${p}m"
                            } else if (p % 60 == 0) {
                                "${p / 60}h"
                            } else {
                                "${p / 60}h${p % 60}m"
                            }
                        val desc =
                            if (p < 60) {
                                "${p}${stringResource(R.string.dial_minutes)}"
                            } else {
                                "${p / 60}${stringResource(R.string.dial_hours)}" + if (p % 60 > 0) "${p % 60}${stringResource(R.string.dial_minutes)}" else ""
                            }
                        Surface(
                            selected = sel,
                            onClick = {
                                onHours(p / 60)
                                onMinutes(p % 60)
                            },
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics {
                                        role = Role.RadioButton
                                        this.selected = sel
                                        contentDescription = desc
                                    },
                            shape = CircleShape,
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
            DialFace(
                mode = mode,
                selected = if (mode == DialMode.HOUR) hours else minutes,
                onSelect = { if (mode == DialMode.HOUR) onHours(it) else onMinutes(it) },
                onSelectDone = onHourPicked,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun Digit(
    text: String,
    desc: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .mintClickable(pressedScale = 0.94f, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics {
                    role = Role.Tab
                    this.selected = active
                    contentDescription = "$desc $text"
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 表盘本体：背景圆 + 指针 + 数字（选中数字套主题色圆）。点按与拖动都拨盘，拖动结束回调。 */
@Composable
private fun DialFace(
    mode: DialMode,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSelectDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val size = DIAL_SIZE.dp
    val labelR = size * 0.40f
    val innerR = size * 0.275f

    Box(
        Modifier
            .size(size)
            .pointerInput(mode) {
                detectTapGestures { off ->
                    hitSlot(mode, off, this.size.width.toFloat())?.let {
                        onSelect(it)
                        onSelectDone()
                    }
                }
            }.pointerInput(mode) {
                detectDragGestures(
                    onDragStart = { off -> hitSlot(mode, off, this.size.width.toFloat())?.let(onSelect) },
                    onDrag = { change, _ ->
                        change.consume()
                        hitSlot(mode, change.position, this.size.width.toFloat())?.let(onSelect)
                    },
                    onDragEnd = onSelectDone,
                    onDragCancel = onSelectDone,
                )
            },
    ) {
        Canvas(Modifier.size(size)) {
            val c = center
            val r = this.size.width / 2f
            drawCircle(cs.surfaceContainerHighest, radius = r, center = c)
            val pos =
                when (mode) {
                    DialMode.HOUR -> {
                        val (i, outer) = hourSlot(selected)
                        polar(i * 30.0, (if (outer) labelR else innerR).toPx(), c)
                    }

                    DialMode.MINUTE -> {
                        polar(selected * 6.0, labelR.toPx(), c)
                    }
                }
            drawLine(cs.primary, c, pos, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(cs.primary, radius = 4.dp.toPx(), center = c)
        }
        val c = size / 2
        when (mode) {
            DialMode.HOUR -> {
                HOUR_OUTER.forEachIndexed { i, v ->
                    hourLabel(i, v, selected = v == selected, c, labelR, onSelect, onSelectDone)
                }
                HOUR_INNER.forEachIndexed { i, v ->
                    hourLabel(i, v, selected = v == selected, c, innerR, onSelect, onSelectDone)
                }
            }

            DialMode.MINUTE -> {
                for (s in 0..59 step 5) {
                    dialLabel(
                        text = "%02d".format(s),
                        desc = "$s ${stringResource(R.string.dial_minutes)}",
                        selected = s == selected,
                        c = c,
                        radius = labelR,
                        angleDeg = s * 6.0,
                        onClick = {
                            onSelect(s)
                            onSelectDone()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun hourLabel(
    idx: Int,
    value: Int,
    selected: Boolean,
    c: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    onSelect: (Int) -> Unit,
    onSelectDone: () -> Unit,
) = dialLabel(
    text = value.toString(),
    desc = "$value ${stringResource(R.string.dial_hours)}",
    selected = selected,
    c = c,
    radius = radius,
    angleDeg = idx * 30.0,
    onClick = {
        onSelect(value)
        onSelectDone()
    },
)

@Composable
private fun dialLabel(
    text: String,
    desc: String,
    selected: Boolean,
    c: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    angleDeg: Double,
    onClick: () -> Unit,
) {
    val rad = Math.toRadians(angleDeg - 90.0)
    val x = c + (radius * cos(rad).toFloat()) - 18.dp
    val y = c + (radius * sin(rad).toFloat()) - 18.dp
    Box(
        Modifier
            .absoluteOffset(x = x, y = y)
            .size(36.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .mintClickable(pressedScale = 0.94f, onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = desc
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 表盘角度（0 = 正上，顺时针）→ 圆上坐标。 */
private fun polar(
    angleDeg: Double,
    radiusPx: Float,
    center: Offset,
): Offset {
    val rad = Math.toRadians(angleDeg - 90.0)
    return Offset(center.x + (radiusPx * cos(rad)).toFloat(), center.y + (radiusPx * sin(rad)).toFloat())
}

/** 值 → (表盘下标, 是否外环)。 */
private fun hourSlot(v: Int): Pair<Int, Boolean> {
    val outer = HOUR_OUTER.indexOf(v)
    return if (outer >= 0) outer to true else HOUR_INNER.indexOf(v) to false
}

/** 触点 → 槽位值；小时面按内外环区分 1–12 / 0,13–23，分钟面 6° 一格。 */
private fun hitSlot(
    mode: DialMode,
    off: Offset,
    widthPx: Float,
): Int? {
    val dx = off.x - widthPx / 2f
    val dy = off.y - widthPx / 2f
    val dist = hypot(dx, dy)
    // 起点太靠中心：还没有方向，忽略以免跳变
    if (dist < widthPx * 0.08f) return null
    val deg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0
    return when (mode) {
        DialMode.HOUR -> {
            val idx = (deg / 30.0).roundToInt().mod(12)
            // 内外环分界取两环半径中点
            if (dist >= widthPx * 0.3375f) HOUR_OUTER[idx] else HOUR_INNER[idx]
        }

        DialMode.MINUTE -> {
            (deg / 6.0).roundToInt().mod(60)
        }
    }
}
