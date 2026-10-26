package app.mintcal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mintcal.R
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 圆形时间表盘：与编辑页 TimePickerDlg 的 M3 圆盘同构——大数字读数（激活段高亮）、
 * 时 / 分切换、圆盘上拨选，选完小时自动进入分钟。
 * [MinutesDialSheet] 选时长（小时+分钟 → 总分钟数），[TimeDialSheet] 选时刻。
 */
private const val DIAL_SIZE = 260

/** 24 小时制双环数字：外环 12 起头，内环 0 与 13–23，下标即表盘 30° 一格的位置。 */
private val HOUR_OUTER = intArrayOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
private val HOUR_INNER = intArrayOf(0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)

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

    fun clamp(h: Int, m: Int): Pair<Int, Int> = if (h * 60 + m > maxMinutes) maxMinutes / 60 to maxMinutes % 60 else h to m

    DialDialog(
        hours = hours,
        minutes = minutes,
        mode = mode,
        onMode = { mode = it },
        onHours = { hours = clamp(it, minutes).first },
        onMinutes = { minutes = clamp(hours, it).second },
        onHourPicked = { mode = DialMode.MINUTE },
        a11y = hourMinuteDesc(hours, minutes),
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
        onDismiss = onDismiss,
        onConfirm = {
            onPick(LocalTime.of(hours, minutes))
            onDismiss()
        },
    )
}

@Composable
private fun hourMinuteDesc(hours: Int, minutes: Int): String =
    "${stringResource(R.string.dial_hours)} $hours，${stringResource(R.string.dial_minutes)} $minutes"

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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.semantics { contentDescription = a11y },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Digit("%02d".format(hours), mode == DialMode.HOUR) { onMode(DialMode.HOUR) }
                    Text(":", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Digit("%02d".format(minutes), mode == DialMode.MINUTE) { onMode(DialMode.MINUTE) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModePill(stringResource(R.string.dial_hours), mode == DialMode.HOUR) { onMode(DialMode.HOUR) }
                    ModePill(stringResource(R.string.dial_minutes), mode == DialMode.MINUTE) { onMode(DialMode.MINUTE) }
                }
                DialFace(
                    mode = mode,
                    selected = if (mode == DialMode.HOUR) hours else minutes,
                    onSelect = { v -> if (mode == DialMode.HOUR) onHours(v) else onMinutes(v) },
                    onSelectDone = { if (mode == DialMode.HOUR) onHourPicked() },
                )
            }
        },
    )
}

@Composable
private fun Digit(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        style = MaterialTheme.typography.displaySmall,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ModePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (mode == DialMode.MINUTE) {
                // 分钟面：每分钟的刻度点（5 的倍数画数字，不画点）
                for (s in 0..59) {
                    if (s % 5 != 0) {
                        drawCircle(
                            cs.onSurfaceVariant.copy(alpha = 0.5f),
                            radius = 1.dp.toPx(),
                            center = polar(s * 6.0, labelR.toPx(), c),
                        )
                    }
                }
            }
            val pos =
                when (mode) {
                    DialMode.HOUR -> {
                        val (i, outer) = hourSlot(selected)
                        polar(i * 30.0, (if (outer) labelR else innerR).toPx(), c)
                    }

                    DialMode.MINUTE -> polar(selected * 6.0, labelR.toPx(), c)
                }
            drawLine(cs.primary, c, pos, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(cs.primary, radius = 4.dp.toPx(), center = c)
        }
        val c = size / 2
        when (mode) {
            DialMode.HOUR -> {
                HOUR_OUTER.forEachIndexed { i, v -> hourLabel(i, v, selected = v == selected, c, labelR) }
                HOUR_INNER.forEachIndexed { i, v -> hourLabel(i, v, selected = v == selected, c, innerR) }
            }

            DialMode.MINUTE -> {
                for (s in 0..59 step 5) {
                    dialLabel("%02d".format(s), s == selected, c, labelR, s * 6.0)
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
) = dialLabel(value.toString(), selected, c, radius, idx * 30.0)

@Composable
private fun dialLabel(
    text: String,
    selected: Boolean,
    c: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    angleDeg: Double,
) {
    val rad = Math.toRadians(angleDeg - 90.0)
    val x = c + (radius * cos(rad).toFloat()) - 18.dp
    val y = c + (radius * sin(rad).toFloat()) - 18.dp
    Box(
        Modifier
            .absoluteOffset(x = x, y = y)
            .size(36.dp)
            .then(if (selected) Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
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

        DialMode.MINUTE -> (deg / 6.0).roundToInt().mod(60)
    }
}
