package app.mintcal.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.ui.Tab
import app.mintcal.ui.theme.LocalReduceMotion
import app.mintcal.ui.theme.MintTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** 通用页面骨架：状态栏避让 + 顶栏（返回 / 标题 / 右侧操作）。 */
@Composable
fun MintScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun MintCard(
    modifier: Modifier = Modifier,
    color: Color = MintTheme.card,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = inner)
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) {
            val fade = MintTheme.spec<Float>()
            AnimatedContent(
                trailing,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "section-trailing",
            ) { t ->
                Text(t, style = MaterialTheme.typography.labelMedium, color = trailingColor)
            }
        }
    }
}

@Composable
fun Tag(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 勾选框：两态保持相同轮廓，命中区保底 48dp，使用水波与触觉反馈。 */
@Composable
fun MintCheck(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    /** 圈的颜色（如清单色）；null 用主题默认。 */
    tint: Color? = null,
    /** 圈的粗细（如用粗细表示优先级）。 */
    strokeWidth: Dp = 2.dp,
) {
    val shape = MaterialTheme.shapes.extraSmall
    // 完成态整体退灰：描边与勾都是灰、不再填充清单色/主题色，少占视觉资源
    val stroke by animateColorAsState(if (checked) MaterialTheme.colorScheme.onSurfaceVariant else (tint ?: MaterialTheme.colorScheme.outline), MintTheme.spec(), label = "check-stroke")
    val tick by animateFloatAsState(if (checked) 1f else 0f, MintTheme.spec(), label = "check-tick")
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = {
                    haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onChange(!checked)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .border(strokeWidth, stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (tick > 0.01f) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.7f * tick.coerceIn(0f, 1f)))
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun NavRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 浮动胶囊底栏，Telegram 风格：岛是中性色，选中项是一块中性浅色圆角块，
 * 图标文字切主题强调色（不是整块 primaryContainer 的大色块）。尺寸放大一档，
 * 选中块不顶满格子、四周留白。匀速补间滑动不过冲；按下不画高亮，反馈靠色块滑动 + 触感。
 */
@Composable
fun FloatingNavBar(
    tab: Tab,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Item(
        val tab: Tab,
        val icon: ImageVector,
        val label: Int,
    )
    val items =
        listOf(
            Item(Tab.TODAY, Icons.Rounded.WbSunny, R.string.tab_today),
            Item(Tab.TIMETABLE, Icons.Rounded.CalendarViewWeek, R.string.tab_timetable),
            Item(Tab.CALENDAR, Icons.Rounded.CalendarMonth, R.string.tab_calendar),
            Item(Tab.TODO, Icons.Rounded.CheckCircle, R.string.tab_todo),
        )
    val selectedIndex = items.indexOfFirst { it.tab == tab }.coerceAtLeast(0)
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val travel = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, reduce) {
        if (reduce) {
            travel.snapTo(selectedIndex.toFloat())
        } else {
            // 定速补间：色块跟手滑到目标，不拖尾也不过冲
            travel.animateTo(selectedIndex.toFloat(), tween(durationMillis = 200, easing = FastOutSlowInEasing))
        }
    }
    val index = travel.value
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp).padding(4.dp)) {
            val itemWidth = maxWidth / items.size
            Box(
                Modifier
                    // 用浮点位移，别用 offset{IntOffset(round)}：行程每帧只走 1~2px，
                    // 取整会变成一格一格挪，看着就是抖
                    .graphicsLayer {
                        translationX = index * itemWidth.toPx() + 6.dp.toPx()
                        translationY = ((maxHeight - 56.dp).toPx() / 2).coerceAtLeast(0f)
                    }
                    // 不顶满格子：左右各留 6dp，高度 56dp，四周透气
                    .width(itemWidth - 12.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val selected = item.tab == tab
                    val interaction = remember { MutableInteractionSource() }
                    val fg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        if (reduce) snap() else tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "nav-fg",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                interactionSource = interaction,
                                // 按下不做高亮：选中态由滑动的色块表达，避免闪底色
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onTab(item.tab)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = fg, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(item.label), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
fun DatePickerDlg(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                onPick(LocalDate.now())
                onDismiss()
            }) { Text(stringResource(R.string.today)) }
            TextButton(onClick = {
                onPick(LocalDate.now().plusDays(1))
                onDismiss()
            }) { Text(stringResource(R.string.tomorrow)) }
        }
        DatePicker(state = state)
    }
}

@Composable
fun TimePickerDlg(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    // 只留圆盘：不给键盘输入入口，时间一律在盘上拨
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

/** 统一按压反馈：按下轻微缩放 + 长按触觉；不做背景高亮。用于自绘可点击块（课程格、格子等）。 */
@Composable
fun Modifier.mintPressable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MintTheme.spec(), label = "press-scale")
    val haptics = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

/** 一次可撤销操作：撤销按钮显示期间持有对应变更集。 */
data class UndoPrompt(
    val csId: String,
    val seq: Long,
)

/** 空状态：图标圆底 + 标题 +（可选）说明与操作按钮；出现/消失由调用方用 AnimatedVisibility 包裹。 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
