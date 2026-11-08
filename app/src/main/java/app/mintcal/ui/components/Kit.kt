package app.mintcal.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.ui.Tab
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
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
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 底栏岛高 68，上下各 12，浮层让位 92；列表再多留 84，末行才能滚到岛上方。 */
val NavIslandHeight = 68.dp
val NavIslandPadding = 12.dp
val NavClearance = NavIslandHeight + NavIslandPadding * 2
val NavListInset = NavClearance + 84.dp

/** 浮动胶囊底栏。岛和选中块是同一套胶囊：选中块四周等距内缩，端头圆弧和岛同心。选中块滑动，按下只缩放图标。 */
private data class NavItem(
    val tab: Tab,
    val icon: ImageVector,
    val label: Int,
)

private val NAV_ITEMS =
    listOf(
        NavItem(Tab.TODAY, Icons.Rounded.WbSunny, R.string.tab_today),
        NavItem(Tab.TIMETABLE, Icons.Rounded.CalendarViewWeek, R.string.tab_timetable),
        NavItem(Tab.CALENDAR, Icons.Rounded.CalendarMonth, R.string.tab_calendar),
        NavItem(Tab.TODO, Icons.Rounded.CheckCircle, R.string.tab_todo),
        NavItem(Tab.FOCUS, Icons.Rounded.Timer, R.string.focus_title),
    )

@Composable
fun FloatingNavBar(
    tab: Tab,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = NAV_ITEMS
    val selectedIndex = items.indexOfFirst { it.tab == tab }
    val haptics = LocalHapticFeedback.current
    val travel = remember { Animatable(selectedIndex.toFloat()) }
    val spec = MintTheme.spec<Float>()
    LaunchedEffect(selectedIndex) {
        travel.animateTo(selectedIndex.toFloat(), spec)
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val bar = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer
    // 深色里 surfaceContainerHighest 和岛几乎同色，选中块会糊成一块污渍；往文字色混一档，仍是中性色
    val thumb = if (dark) lerp(bar, scheme.onSurface, 0.16f) else scheme.surfaceContainerHighest
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = NavIslandPadding).fillMaxWidth(),
        shape = CircleShape,
        color = bar,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxWidth().height(NavIslandHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                // travel 只在 draw 里读：滑动动画每帧只重绘 Canvas，不再让整条底栏逐帧重组
                val index = travel.value
                val inset = 4.dp.toPx()
                val slot = size.width / items.size
                val width = slot - inset * 2
                val left = index * slot + inset
                val top = inset
                val height = size.height - inset * 2
                drawRoundRect(
                    color = thumb,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2f, height / 2f),
                )
            }
            Row(Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val selected = item.tab == tab
                    val interaction = remember { MutableInteractionSource() }
                    val fg by animateColorAsState(
                        if (selected) scheme.primary else scheme.onSurfaceVariant,
                        MintTheme.spec(),
                        label = "nav-fg",
                    )
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        if (pressed) 0.96f else 1f,
                        MintTheme.spec(),
                        label = "nav-press",
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
                                    // 重选当前 Tab 也给触觉回执（回顶动效是视觉回执），避免「点了没反应」
                                    haptics.performHapticFeedback(if (selected) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick)
                                    onTab(item.tab)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                null,
                                tint = fg,
                                modifier =
                                    Modifier
                                        .graphicsLayer {
                                            scaleX = pressScale
                                            scaleY = pressScale
                                        }.size(24.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
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
    var picked by remember { mutableStateOf(initial ?: LocalDate.now()) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(picked)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        Column {
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
            MonthPager(selected = picked, initial = YearMonth.from(initial ?: LocalDate.now()), onPick = { picked = it })
        }
    }
}

@Composable
fun TimePickerDlg(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(hour, minute))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimeWheels(hour, minute, { hour = it }, { minute = it }) },
        shape = RoundedCornerShape(28.dp),
    )
}

/**
 * 时刻滚轮：时、分两列，中间一行是当前值。
 * 不用 Material 圆盘——24 小时双环挤在一个表盘上，读数和拨动都差。
 */
@Composable
fun TimeWheels(
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(WHEEL_ITEM * WHEEL_VISIBLE), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(WHEEL_ITEM)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelColumn(24, hour.coerceIn(0, 23), stringResource(R.string.dial_hours), onHour)
            Text(
                ":",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WheelColumn(60, minute.coerceIn(0, 59), stringResource(R.string.dial_minutes), onMinute)
        }
    }
}

private val WHEEL_ITEM = 36.dp
private const val WHEEL_VISIBLE = 5
private const val WHEEL_LOOPS = 40

@Composable
private fun WheelColumn(
    count: Int,
    selected: Int,
    label: String,
    onSelect: (Int) -> Unit,
) {
    val selectedCoerced = selected.coerceIn(0, count - 1)
    val anchor = WHEEL_LOOPS / 2 * count + selectedCoerced
    val state = rememberLazyListState(initialFirstVisibleItemIndex = anchor)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { wheelCenter(state) }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to wheelCenter(state) }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, idx) ->
                val value = idx?.let { it % count }
                if (value != null && value != selectedCoerced) onSelect(value)
            }
    }
    LaunchedEffect(selectedCoerced) {
        val center = wheelCenter(state)
        if (center == null || center % count != selectedCoerced) state.scrollToItem(anchor)
    }
    LazyColumn(
        state = state,
        modifier =
            Modifier
                .width(72.dp)
                .height(WHEEL_ITEM * WHEEL_VISIBLE)
                .semantics { contentDescription = "$label $selectedCoerced" },
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = WHEEL_ITEM * (WHEEL_VISIBLE / 2)),
    ) {
        items(WHEEL_LOOPS * count) { i ->
            val value = i % count
            val raw = abs(value - selectedCoerced)
            val dist = if (raw > count / 2) count - raw else raw
            Box(Modifier.fillMaxWidth().height(WHEEL_ITEM), contentAlignment = Alignment.Center) {
                Text(
                    "%02d".format(value),
                    textAlign = TextAlign.Center,
                    style =
                        (if (dist == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).copy(
                            fontFeatureSettings = "tnum",
                        ),
                    fontWeight = if (dist == 0) FontWeight.Medium else FontWeight.Normal,
                    color =
                        when {
                            dist == 0 -> MaterialTheme.colorScheme.onSurface
                            dist == 1 -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.outline
                        },
                )
            }
        }
    }
}

private fun wheelCenter(state: LazyListState): Int? {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index
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

/**
 * mintPressable 的通用版：可选长按、角色语义、触觉开关。
 * 全局 NoIndication 下所有自绘可点区域统一走这里，保证「点了有反应」的基线不丢。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.mintClickable(
    enabled: Boolean = true,
    role: Role? = null,
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, MintTheme.spec(), label = "press-scale")
    val haptics = LocalHapticFeedback.current
    val click: () -> Unit = {
        if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
    val gesture =
        if (onLongClick != null) {
            Modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = click, onLongClick = onLongClick)
        } else {
            Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = click)
        }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.then(gesture)
}

/** 一次可撤销操作：条还在时持有对应变更集和当时的说明。 */
data class UndoPrompt(
    val csId: String,
    val seq: Long,
    val text: String,
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
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
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
            if (secondaryLabel != null && onSecondary != null) {
                // 用 primary 实心按钮：secondaryContainer 在本主题里偏棕，和整体强调色不是一家
                Button(onClick = onAction) { Text(actionLabel) }
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** 悬浮添加键：出现 / 消失带缩放和淡入，条件渲染时不再凭空蹦出或瞬间消失。 */
@Composable
fun MintFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.shortcut_add_task),
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MintTheme.spec()) + scaleIn(initialScale = 0.6f, animationSpec = MintTheme.spec()),
        exit = fadeOut(MintTheme.spec()) + scaleOut(targetScale = 0.6f, animationSpec = MintTheme.spec()),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.Rounded.Add, description)
        }
    }
}

/**
 * 冲突卡：「时间冲突」类提示的统一形态——警示图标 + 标题 + 可点行，
 * 底色用 conflictBg，编辑页 / 待办编辑页共用同一套视觉。
 */
@Composable
fun ConflictCard(
    title: String,
    modifier: Modifier = Modifier,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MintTheme.colors.conflictBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Warning, null, tint = MintTheme.colors.removed, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MintTheme.colors.removed, fontWeight = FontWeight.Bold)
        }
        rows()
    }
}
