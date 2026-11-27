package app.goke.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import app.goke.R
import app.goke.ui.Tab
import app.goke.ui.icons.CalendarMonth
import app.goke.ui.icons.CalendarViewWeek
import app.goke.ui.icons.GridView
import app.goke.ui.icons.Inbox
import app.goke.ui.icons.Timer
import app.goke.ui.icons.Undo
import app.goke.ui.icons.WbSunny
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
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
    val existingScope = LocalMintModalScope.current
    val body: @Composable () -> Unit = {
        Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    actions()
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 680.dp).fillMaxWidth()) { content() }
            }
        }
    }
    if (existingScope == null) {
        ProvideMintModalScope { body() }
    } else {
        body()
    }
}

/** 暂未开放功能的统一占位页骨架：沿用 [MintScreen] 顶栏与居中图标说明。 */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
) {
    MintScreen(title = title, onBack = onBack) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.feature_coming_soon),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.feature_coming_soon_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MintCard(
    modifier: Modifier = Modifier,
    color: Color = MintTheme.card,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(contentPadding), content = content) }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.mintPressScale(interaction, 0.98f),
            shape = shape,
            color = color,
            border = border,
            interactionSource = interaction,
            content = inner,
        )
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 勾选框：两态保持相同轮廓，命中区保底 48dp；轻量单节点直接绘制圆角框与勾选路径，点击不振动，静止零协程。 */
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
    val scheme = MaterialTheme.colorScheme
    val checkColor = scheme.onSurfaceVariant
    val strokeColor = tint ?: scheme.outline
    val burstColor = MintTheme.colors.added
    val reduceMotion = LocalReduceMotion.current
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .toggleable(
                    value = checked,
                    interactionSource = null,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onChange(!checked) },
                ).then(
                    MintCheckElement(
                        checked = checked,
                        boxSize = size,
                        strokeWidth = strokeWidth,
                        checkColor = checkColor,
                        strokeColor = strokeColor,
                        burstColor = burstColor,
                        reduceMotion = reduceMotion,
                    ),
                ),
    )
}

private data class MintCheckElement(
    val checked: Boolean,
    val boxSize: Dp,
    val strokeWidth: Dp,
    val checkColor: Color,
    val strokeColor: Color,
    val burstColor: Color,
    val reduceMotion: Boolean,
) : ModifierNodeElement<MintCheckNode>() {
    override fun create(): MintCheckNode =
        MintCheckNode(
            checked = checked,
            boxSize = boxSize,
            strokeWidth = strokeWidth,
            checkColor = checkColor,
            strokeColor = strokeColor,
            burstColor = burstColor,
            reduceMotion = reduceMotion,
        )

    override fun update(node: MintCheckNode) {
        node.update(
            checked = checked,
            boxSize = boxSize,
            strokeWidth = strokeWidth,
            checkColor = checkColor,
            strokeColor = strokeColor,
            burstColor = burstColor,
            reduceMotion = reduceMotion,
        )
    }
}

private class MintCheckNode(
    private var checked: Boolean,
    private var boxSize: Dp,
    private var strokeWidth: Dp,
    private var checkColor: Color,
    private var strokeColor: Color,
    private var burstColor: Color,
    private var reduceMotion: Boolean,
) : Modifier.Node(),
    DrawModifierNode {
    private var progress: Float = if (checked) 1f else 0f
    private var burstProgress: Float = 0f
    private var animatable: Animatable<Float, AnimationVector1D>? = null
    private var burstAnimatable: Animatable<Float, AnimationVector1D>? = null
    private var animJob: Job? = null
    private var burstJob: Job? = null
    private var checkPath: Path? = null

    override fun onAttach() {
        progress = if (checked) 1f else 0f
        burstProgress = 0f
    }

    override fun onDetach() {
        animJob?.cancel()
        animJob = null
        burstJob?.cancel()
        burstJob = null
        progress = if (checked) 1f else 0f
        burstProgress = 0f
    }

    fun update(
        checked: Boolean,
        boxSize: Dp,
        strokeWidth: Dp,
        checkColor: Color,
        strokeColor: Color,
        burstColor: Color,
        reduceMotion: Boolean,
    ) {
        val checkedChanged = this.checked != checked
        val visualChanged =
            this.boxSize != boxSize ||
                this.strokeWidth != strokeWidth ||
                this.checkColor != checkColor ||
                this.strokeColor != strokeColor ||
                this.burstColor != burstColor
        this.checked = checked
        this.boxSize = boxSize
        this.strokeWidth = strokeWidth
        this.checkColor = checkColor
        this.strokeColor = strokeColor
        this.burstColor = burstColor
        this.reduceMotion = reduceMotion

        if (checkedChanged) {
            val target = if (checked) 1f else 0f
            animJob?.cancel()
            animJob = null
            burstJob?.cancel()
            burstJob = null
            burstProgress = 0f
            if (!isAttached || reduceMotion) {
                progress = target
                invalidateDraw()
            } else {
                val anim = animatable ?: Animatable(progress).also { animatable = it }
                animJob =
                    coroutineScope.launch {
                        if (anim.value != progress) {
                            anim.snapTo(progress)
                        }
                        anim.animateTo(target, PressScaleSpec) {
                            progress = value
                            invalidateDraw()
                        }
                        progress = target
                        invalidateDraw()
                    }
                if (checked) {
                    val burst = burstAnimatable ?: Animatable(0f).also { burstAnimatable = it }
                    burstJob =
                        coroutineScope.launch {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing)) {
                                burstProgress = value
                                invalidateDraw()
                            }
                            burstProgress = 0f
                            invalidateDraw()
                        }
                }
            }
        } else if (visualChanged) {
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val t = progress.coerceIn(0f, 1f)
        val b = burstProgress.coerceIn(0f, 1f)
        val boxPx = boxSize.toPx()
        val strokePx = strokeWidth.toPx()
        val halfStroke = strokePx / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val left = cx - boxPx / 2f + halfStroke
        val top = cy - boxPx / 2f + halfStroke
        val rectSize = boxPx - strokePx
        val cornerPx = 4.dp.toPx()
        val currentStrokeColor =
            when {
                t <= 0f -> strokeColor
                t >= 1f -> checkColor
                else -> lerp(strokeColor, checkColor, t)
            }
        val popScale = if (b > 0f && b < 1f) 1f + 0.12f * kotlin.math.sin(b * kotlin.math.PI).toFloat() else 1f
        val drawCheckBox: DrawScope.() -> Unit = {
            drawRoundRect(
                color = currentStrokeColor,
                topLeft = Offset(left, top),
                size = Size(rectSize, rectSize),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )
            if (t > 0f) {
                val checkStrokePx = 2.dp.toPx()
                val boxLeft = cx - boxPx / 2f
                val boxTop = cy - boxPx / 2f
                val path = checkPath ?: Path().also { checkPath = it }
                path.rewind()
                path.moveTo(boxLeft + boxPx * 0.28f, boxTop + boxPx * 0.52f)
                path.lineTo(boxLeft + boxPx * 0.44f, boxTop + boxPx * 0.70f)
                path.lineTo(boxLeft + boxPx * 0.74f, boxTop + boxPx * 0.34f)
                val drawBlock: DrawScope.() -> Unit = {
                    drawPath(
                        path = path,
                        color = if (t >= 1f) checkColor else checkColor.copy(alpha = t),
                        style =
                            Stroke(
                                width = checkStrokePx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
                if (t >= 0.999f) {
                    drawBlock()
                } else {
                    scale(scaleX = t, scaleY = t, pivot = Offset(cx, cy), block = drawBlock)
                }
            }
        }
        if (popScale != 1f) {
            scale(scaleX = popScale, scaleY = popScale, pivot = Offset(cx, cy), block = drawCheckBox)
        } else {
            drawCheckBox()
        }
        if (b > 0f && b < 1f) {
            val dotAlpha = (1f - b).coerceIn(0f, 1f) * 0.85f
            val radius = boxPx * (0.56f + 0.44f * b)
            val dotRadius = (2.1f * (1f - b * 0.72f)).dp.toPx()
            val dotColor = burstColor.copy(alpha = dotAlpha)
            for (i in 0 until 6) {
                val angle = (i * 60.0 - 30.0) * kotlin.math.PI / 180.0
                val dx = (kotlin.math.cos(angle) * radius).toFloat()
                val dy = (kotlin.math.sin(angle) * radius).toFloat()
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(cx + dx, cy + dy))
            }
        }
        drawContent()
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
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .mintClickable(enabled = enabled, pressedScale = 0.98f) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = typography.bodyLarge, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = typography.bodySmall, color = scheme.onSurfaceVariant)
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
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val variantColor = scheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.38f))
            .mintClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = variantColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = typography.bodyLarge, color = if (enabled) scheme.onSurface else variantColor)
            if (subtitle != null) Text(subtitle, style = typography.bodySmall, color = variantColor)
        }
        if (trailing != null) Text(trailing, style = typography.bodyMedium, color = variantColor)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = variantColor)
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
        NavItem(Tab.ME, Icons.Rounded.Person, R.string.tab_me),
    )

@Composable
fun FloatingNavBar(
    tab: Tab,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = NAV_ITEMS
    val selectedIndex = items.indexOfFirst { it.tab == tab }
    val travel = remember { Animatable(selectedIndex.toFloat()) }
    val reduce = LocalReduceMotion.current
    // 色块是「手指下的即时状态」，用 fast spatial：跨 3 格的滑动约 150ms 到位，不再拖尾巴
    val spec = MintTheme.press<Float>()
    LaunchedEffect(selectedIndex, reduce) {
        if (reduce) {
            travel.snapTo(selectedIndex.toFloat())
        } else {
            travel.animateTo(selectedIndex.toFloat(), spec)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val bar = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer
    // 深色里 surfaceContainerHighest 和岛几乎同色，选中块会糊成一块污渍；往文字色混一档，仍是中性色
    val thumb = if (dark) lerp(bar, scheme.onSurface, 0.16f) else scheme.surfaceContainerHighest
    val activeColor = scheme.primary
    val inactiveColor = scheme.onSurfaceVariant

    Surface(
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = NavIslandPadding)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
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
                    NavBarItemView(
                        item = item,
                        selected = selected,
                        primaryColor = activeColor,
                        unselectedColor = inactiveColor,
                        onTab = onTab,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavBarItemView(
    item: NavItem,
    selected: Boolean,
    primaryColor: Color,
    unselectedColor: Color,
    onTab: (Tab) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val fg = if (selected) primaryColor else unselectedColor
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // 按下不做高亮：选中态由滑动的色块表达；也不振动——
                // 标签切换是导航，不是手势，系统组件同样不在这里给触觉
                indication = null,
                role = Role.Tab,
                onClick = { onTab(item.tab) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            Icon(
                item.icon,
                null,
                tint = fg,
                modifier = Modifier.mintPressScale(interaction, 0.96f).size(24.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(item.label),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
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
    var handled by remember { mutableStateOf(false) }
    MintAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                enabled = !handled,
                onClick = {
                    if (handled) return@TextButton
                    handled = true
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

/**
 * 日期选择弹窗：用 [MintDialog] 包裹精简 [MonthPager]，提供「今天/明天」单手直达且展示农历/周次标记，
 * 同时统一弹簧入退场动效与预测性返回手势。
 */
@Composable
fun DatePickerDlg(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    var picked by remember { mutableStateOf(initial ?: LocalDate.now()) }
    var handled by remember { mutableStateOf(false) }

    fun confirm(date: LocalDate) {
        if (handled) return
        handled = true
        onPick(date)
        onDismiss()
    }
    MintDialog(onDismissRequest = onDismiss, maxWidth = 380.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) {
                Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !handled, onClick = { confirm(LocalDate.now()) }) { Text(stringResource(R.string.today)) }
                    TextButton(enabled = !handled, onClick = { confirm(LocalDate.now().plusDays(1)) }) { Text(stringResource(R.string.tomorrow)) }
                }
                Box(Modifier.padding(horizontal = 8.dp)) {
                    MonthPager(selected = picked, initial = YearMonth.from(initial ?: LocalDate.now()), onPick = { picked = it })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(enabled = !handled, onClick = { confirm(picked) }) { Text(stringResource(R.string.ok)) }
                }
            }
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
    var handled by remember { mutableStateOf(false) }
    MintAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !handled,
                onClick = {
                    if (handled) return@TextButton
                    handled = true
                    onPick(LocalTime.of(hour, minute))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ok)) }
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

/**
 * 统一按压反馈：按下轻微缩放，不做背景高亮。用于自绘可点击块（课程格、格子等）。
 * 触觉只留给手势本身（长按启动拖动、拖动吸附），普通点击一律不振动。
 */
@Composable
fun Modifier.mintPressable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .mintPressScale(source, pressedScale, enabled)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

private val PressScaleSpec = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/**
 * 按压几何回弹：基于 DrawModifierNode 按需监听与绘制，未按下时零协程、零动画对象、零硬件图层；
 * 仅在按压与回弹期间通过 Canvas scale 缩放绘制内容，静止态直接 drawContent()。
 */
fun Modifier.mintPressScale(
    source: InteractionSource,
    pressedScale: Float,
    enabled: Boolean = true,
): Modifier = this then PressScaleElement(source, pressedScale, enabled)

private data class PressScaleElement(
    val source: InteractionSource,
    val pressedScale: Float,
    val enabled: Boolean,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(source, pressedScale, enabled)

    override fun update(node: PressScaleNode) {
        node.update(source, pressedScale, enabled)
    }
}

private class PressScaleNode(
    private var source: InteractionSource,
    private var pressedScale: Float,
    private var enabled: Boolean,
) : Modifier.Node(),
    DrawModifierNode {
    private var currentScale: Float = 1f
    private var collectJob: Job? = null
    private var animJob: Job? = null
    private var animatable: Animatable<Float, AnimationVector1D>? = null

    override fun onAttach() {
        syncCollector()
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
        animJob?.cancel()
        animJob = null
        currentScale = 1f
    }

    fun update(
        source: InteractionSource,
        pressedScale: Float,
        enabled: Boolean,
    ) {
        val sourceChanged = this.source != source
        val enabledChanged = this.enabled != enabled
        this.source = source
        this.pressedScale = pressedScale
        this.enabled = enabled
        if (!enabled) {
            collectJob?.cancel()
            collectJob = null
            animJob?.cancel()
            animJob = null
            if (currentScale != 1f) {
                currentScale = 1f
                invalidateDraw()
            }
        } else if (sourceChanged || enabledChanged) {
            if (sourceChanged) {
                animJob?.cancel()
                animJob = null
                if (currentScale != 1f) {
                    currentScale = 1f
                    invalidateDraw()
                }
            }
            syncCollector()
        }
    }

    private fun syncCollector() {
        collectJob?.cancel()
        collectJob = null
        if (!isAttached || !enabled) return
        val currentSource = source
        collectJob =
            coroutineScope.launch {
                val activePresses = mutableListOf<PressInteraction.Press>()
                var isPressed = false
                currentSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> activePresses.add(interaction)
                        is PressInteraction.Release -> activePresses.remove(interaction.press)
                        is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                    }
                    val nowPressed = activePresses.isNotEmpty()
                    if (nowPressed != isPressed) {
                        isPressed = nowPressed
                        animateToScale(if (nowPressed && enabled) pressedScale else 1f)
                    }
                }
            }
    }

    private fun animateToScale(target: Float) {
        animJob?.cancel()
        if (!isAttached) {
            currentScale = target
            return
        }
        val anim = animatable ?: Animatable(currentScale).also { animatable = it }
        animJob =
            coroutineScope.launch {
                if (anim.value != currentScale) {
                    anim.snapTo(currentScale)
                }
                anim.animateTo(target, PressScaleSpec) {
                    currentScale = value
                    invalidateDraw()
                }
                currentScale = target
                invalidateDraw()
            }
    }

    override fun ContentDrawScope.draw() {
        val s = currentScale
        if (s >= 0.999f) {
            drawContent()
        } else {
            scale(scaleX = s, scaleY = s, pivot = center) {
                this@draw.drawContent()
            }
        }
    }
}

/**
 * mintPressable 的通用版：可选长按、角色语义。
 * 全局 NoIndication 下所有自绘可点区域统一走这里，保证「点了有反应」的基线不丢。
 * 长按的触觉由 combinedClickable 自己发，这里不再叠加一次。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.mintClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val gesture =
        if (onLongClick != null) {
            Modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick, onLongClick = onLongClick)
        } else {
            Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
        }
    val semanticsMod =
        if (selected != null) {
            Modifier.semantics { this.selected = selected }
        } else {
            Modifier
        }
    return this
        .mintPressScale(source, pressedScale, enabled)
        .then(gesture)
        .then(semanticsMod)
}

/**
 * 工具栏 / 底栏上的菜单统一用这份 Popup 属性：菜单不抢焦点，键盘保持打开，
 * 浮层位置也不会跟着 IME 的升降重新算——否则会飘在正文上、底下留一条空。
 */
@Composable
fun rememberNoFocusPopup(): PopupProperties = remember { PopupProperties(focusable = false) }

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
        val interaction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.mintPressScale(interaction, 0.96f),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation =
                FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 3.dp,
                    focusedElevation = 3.dp,
                    hoveredElevation = 3.dp,
                ),
            interactionSource = interaction,
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
