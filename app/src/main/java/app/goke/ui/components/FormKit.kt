package app.goke.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.goke.ui.icons.Visibility
import app.goke.ui.icons.VisibilityOff
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 圆角微色阶输入容器（12dp surfaceContainerHighest）：取代底边横线输入，
 * 聚焦时平滑过渡主色细描边，支持密码显隐切换与校验提示。
 */
@Composable
fun TonalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    number: Boolean = false,
    secret: Boolean = false,
    placeholder: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
) {
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealSecret by remember { mutableStateOf(false) }
    val labelColor =
        when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> scheme.onSurfaceVariant
        }
    val borderColor by animateColorAsState(
        when {
            isError -> scheme.error
            focused -> scheme.primary.copy(alpha = 0.65f)
            else -> Color.Transparent
        },
        MintTheme.press(),
        label = "field-border",
    )
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            interactionSource = interaction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { if (label.isNotBlank()) contentDescription = label },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            visualTransformation = if (secret && !revealSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        when {
                            secret -> KeyboardType.Password
                            number -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
                ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(scheme.surfaceContainerHighest)
                        .border(1.5.dp, borderColor, shape)
                        .padding(start = 14.dp, end = if (secret) 6.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        inner()
                    }
                    if (secret && value.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .mintClickable(pressedScale = 0.96f, role = Role.Button) { revealSecret = !revealSecret },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (revealSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/** 兼容现有表单调用点：全部统一走圆角微色阶输入 [TonalField]。 */
@Composable
fun FieldText(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    number: Boolean = false,
    secret: Boolean = false,
    placeholder: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
) {
    TonalField(
        label = label,
        value = value,
        onChange = onChange,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        number = number,
        secret = secret,
        placeholder = placeholder,
        isError = isError,
        supporting = supporting,
    )
}

/**
 * 滑动胶囊分段选择器（2–4 项等宽互斥选项）：滑块走 MintTheme.spec() 空间弹簧，
 * 与底栏胶囊同构，消除裸文字单选的点击边界模糊。
 */
@Composable
fun <T> SegmentedPill(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val rawIndex = options.indexOfFirst { it.first == selected }
    val selectedIndex = rawIndex.coerceAtLeast(0)
    val travel = remember { Animatable(selectedIndex.toFloat()) }
    // 与底栏同构：滑块是手指下的即时状态，走 fast spatial，不拖尾巴
    val spec = MintTheme.press<Float>()
    LaunchedEffect(rawIndex) {
        if (rawIndex >= 0 && travel.value != rawIndex.toFloat()) {
            travel.animateTo(rawIndex.toFloat(), spec)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelLarge
    val activeFg = scheme.primary
    val inactiveFg = scheme.onSurfaceVariant
    val dark = scheme.background.luminance() < 0.5f
    val track = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerHighest
    val thumb = if (dark) lerp(track, scheme.onSurface, 0.16f) else scheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (rawIndex >= 0) {
                val inset = 3.dp.toPx()
                val slot = size.width / options.size
                val w = (slot - inset * 2).coerceAtLeast(0f)
                val h = (size.height - inset * 2).coerceAtLeast(0f)
                val left = travel.value * slot + inset
                drawRoundRect(
                    color = thumb,
                    topLeft = Offset(left, inset),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { (v, label) ->
                val on = v == selected
                val interaction = remember { MutableInteractionSource() }
                val fg = if (on) activeFg else inactiveFg
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = on,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { if (!on) onSelect(v) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        modifier = Modifier.mintPressScale(interaction, 0.96f).padding(horizontal = 6.dp, vertical = 8.dp),
                        style = labelStyle,
                        color = fg,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 流式胶囊单选（5 项及以上或不定宽标签）：清晰底色包裹，不再裸浮文字。 */
@Composable
fun <T> ChipChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, label) ->
            ChipPill(label = label, on = v == selected, onClick = { onSelect(v) })
        }
    }
}

/**
 * 一枚胶囊芯片，芯片视觉的唯一定义：选中走主色容器，未选落清晰底色。
 * [on] 为 null 表示这是一枚「做事」的芯片（如新建），不带选中语义。
 * 单选传 [Role.RadioButton]、多选传 [Role.Checkbox]；选中态同时走语义，不只靠颜色。
 */
@Composable
fun ChipPill(
    label: String,
    on: Boolean?,
    modifier: Modifier = Modifier,
    role: Role = if (on == null) Role.Button else Role.RadioButton,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val on = on == true
    val bg by animateColorAsState(
        if (on) scheme.primaryContainer else scheme.surfaceContainerHighest,
        MintTheme.spec(),
        label = "chip-bg",
    )
    val fg by animateColorAsState(
        if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        MintTheme.spec(),
        label = "chip-fg",
    )
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(bg)
            .semantics {
                this.role = role
                if (role != Role.Button) selected = on
            }.mintClickable(pressedScale = 0.96f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 一行「标签 + 当前值」，点击后由调用方弹出选择器。
 * 标签弱化、值为主体；带 chevron 表达可点。
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .mintClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(20.dp),
        )
    }
}

/** 一组设置：只有小标题，不再套卡片。 */
@Composable
fun FormSection(
    title: String? = null,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = if (title != null) 16.dp else 4.dp, bottom = 4.dp)) {
        if (title != null) SectionLabel(title, trailing = trailing, trailingColor = trailingColor)
        content()
    }
}

internal enum class MintModalKind { SHEET, DIALOG }

@Stable
internal class MintModalEntry(
    val id: Long,
    val kind: MintModalKind,
    val sheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val maxWidth: Dp = Dp.Unspecified,
) {
    var visible by mutableStateOf(true)
    var disposed by mutableStateOf(false)
    var dismissNotified by mutableStateOf(false)
    var onDismissRequest by mutableStateOf<() -> Unit>({})
    var modifier by mutableStateOf<Modifier>(Modifier)
    var dismissOnClickOutside by mutableStateOf(true)
    var dismissOnBackPress by mutableStateOf(true)
    var sheetContent by mutableStateOf<@Composable ColumnScope.() -> Unit>({})
    var dialogContent by mutableStateOf<@Composable () -> Unit>({})
}

/** 当前所属 Tab / Route / 悬浮层子树内的模态弹层容器：保证同窗弹簧动效、预测性返回与焦点隔离。 */
@Stable
internal class MintModalScope {
    val entries = mutableStateListOf<MintModalEntry>()
    private var nextId = 1L

    fun pushSheet(
        shape: Shape,
        maxWidth: Dp,
    ): MintModalEntry =
        MintModalEntry(
            id = nextId++,
            kind = MintModalKind.SHEET,
            sheetShape = shape,
            maxWidth = maxWidth,
        ).also { entries += it }

    fun pushDialog(maxWidth: Dp): MintModalEntry =
        MintModalEntry(
            id = nextId++,
            kind = MintModalKind.DIALOG,
            maxWidth = maxWidth,
        ).also { entries += it }

    fun dismiss(id: Long) {
        entries.firstOrNull { it.id == id }?.let { it.visible = false }
    }

    fun remove(id: Long) {
        entries.removeAll { it.id == id }
    }
}

internal val LocalMintModalScope = compositionLocalOf<MintModalScope?> { null }
internal val LocalMintModalDismiss = compositionLocalOf<(() -> Unit)?> { null }

/** 为所属 Tab / Route / 悬浮层提供同窗模态弹层层，统一弹簧入退场、拖拽与预测性返回。 */
@Composable
internal fun ProvideMintModalScope(
    active: Boolean = true,
    onSheetOpenChange: (Boolean) -> Unit = {},
    onModalOpenChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = remember { MintModalScope() }
    val hasVisibleSheet by remember {
        derivedStateOf { scope.entries.any { it.kind == MintModalKind.SHEET && it.visible } }
    }
    val hasVisibleModal by remember {
        derivedStateOf { scope.entries.any { it.visible } }
    }
    LaunchedEffect(hasVisibleSheet) { onSheetOpenChange(hasVisibleSheet) }
    LaunchedEffect(hasVisibleModal) { onModalOpenChange(hasVisibleModal) }
    DisposableEffect(Unit) {
        onDispose {
            onSheetOpenChange(false)
            onModalOpenChange(false)
        }
    }

    CompositionLocalProvider(LocalMintModalScope provides scope) {
        Box(Modifier.fillMaxSize()) {
            content()
            val topVisibleId = scope.entries.lastOrNull { it.visible }?.id
            scope.entries.forEach { entry ->
                key(entry.id) {
                    val isTopModal = active && entry.visible && entry.id == topVisibleId
                    when (entry.kind) {
                        MintModalKind.SHEET -> {
                            MintSheetOverlay(
                                visible = entry.visible,
                                onDismissRequest = { entry.visible = false },
                                modifier = entry.modifier,
                                scrimAlpha = 0.32f,
                                canDragDismiss = entry.visible,
                                predictiveBackEnabled = isTopModal,
                                onExited = {
                                    scope.remove(entry.id)
                                    if (!entry.disposed && !entry.dismissNotified) {
                                        entry.dismissNotified = true
                                        entry.onDismissRequest()
                                    }
                                },
                                sheetModifier =
                                    Modifier
                                        .statusBarsPadding()
                                        .padding(top = 16.dp)
                                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                                shape = entry.sheetShape,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                maxWidth = entry.maxWidth,
                                dragHandle = true,
                            ) {
                                CompositionLocalProvider(LocalMintModalDismiss provides { entry.visible = false }) {
                                    entry.sheetContent(this)
                                }
                            }
                        }

                        MintModalKind.DIALOG -> {
                            MintDialogOverlay(
                                visible = entry.visible,
                                onDismissRequest = { entry.visible = false },
                                modifier = entry.modifier,
                                dismissOnClickOutside = entry.dismissOnClickOutside,
                                dismissOnBackPress = entry.dismissOnBackPress,
                                predictiveBackEnabled = isTopModal,
                                maxWidth = entry.maxWidth,
                                onExited = {
                                    scope.remove(entry.id)
                                    if (!entry.disposed && !entry.dismissNotified) {
                                        entry.dismissNotified = true
                                        entry.onDismissRequest()
                                    }
                                },
                            ) {
                                CompositionLocalProvider(LocalMintModalDismiss provides { entry.visible = false }) {
                                    entry.dialogContent()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应用统一底部模态抽屉：基于窗口内 [MintSheetOverlay]（28dp 顶角、弹簧入退场、1:1 下拉关闭与预测性返回）。
 * 在当前所属 Tab / Route / 悬浮层的 [ProvideMintModalScope] 中按栈顺序弹出，避免跨窗体 Binder 卡顿与返回失灵。
 */
@Composable
fun MintBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val modalScope = LocalMintModalScope.current
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    if (modalScope == null) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        MintSheetOverlay(
            visible = shown,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            scrimAlpha = 0.32f,
            canDragDismiss = true,
            dragHandle = true,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            maxWidth = maxWidth,
            sheetModifier =
                Modifier
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            content = content,
        )
        return
    }

    val entry =
        remember(modalScope) {
            modalScope.pushSheet(shape = shape, maxWidth = maxWidth)
        }
    SideEffect {
        entry.onDismissRequest = onDismissRequest
        entry.modifier = modifier
        entry.sheetContent = content
    }
    DisposableEffect(modalScope, entry.id) {
        onDispose {
            entry.disposed = true
            modalScope.dismiss(entry.id)
        }
    }
}

/**
 * 居中模态弹窗容器：同窗渲染，提供弹簧缩放+淡入退场、点击遮罩关闭及 1:1 预测性返回手势。
 */
@Composable
fun MintDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    maxWidth: Dp = 420.dp,
    content: @Composable () -> Unit,
) {
    val modalScope = LocalMintModalScope.current
    if (modalScope == null) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        MintDialogOverlay(
            visible = shown,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
            maxWidth = maxWidth,
            content = content,
        )
        return
    }

    val entry = remember(modalScope, maxWidth) { modalScope.pushDialog(maxWidth = maxWidth) }
    SideEffect {
        entry.onDismissRequest = onDismissRequest
        entry.modifier = modifier
        entry.dismissOnClickOutside = dismissOnClickOutside
        entry.dismissOnBackPress = dismissOnBackPress
        entry.dialogContent = content
    }
    DisposableEffect(modalScope, entry.id) {
        onDispose {
            entry.disposed = true
            modalScope.dismiss(entry.id)
        }
    }
}

/** 统一居中提醒 / 确认弹窗：28dp 圆角、surfaceContainerHigh 色阶与 [MintDialog] 一致动效和返回手势。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MintAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    MintDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissOnClickOutside = dismissOnClickOutside,
        dismissOnBackPress = dismissOnBackPress,
        maxWidth = 420.dp,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides scheme.secondary) {
                            icon()
                        }
                    }
                }
                if (title != null) {
                    Box(Modifier.fillMaxWidth().padding(bottom = if (text != null) 16.dp else 24.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(color = scheme.onSurface)) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant)) {
                            text()
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** 同窗居中弹窗物理动效容器：弹簧缩放淡入退场 + 遮罩同步 + 预测性返回手势。 */
@Composable
fun MintDialogOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.36f,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    predictiveBackEnabled: Boolean = visible,
    onExited: () -> Unit = {},
    maxWidth: Dp = 420.dp,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val scrimColor = MaterialTheme.colorScheme.scrim

    var isPresented by remember { mutableStateOf(visible) }
    if (visible && !isPresented) isPresented = true
    if (!visible && !isPresented) return

    val progress = remember { Animatable(0f) }
    val predictiveProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnExited by rememberUpdatedState(onExited)
    val currentCanClickOutside by rememberUpdatedState(dismissOnClickOutside)

    LaunchedEffect(visible, reduce) {
        if (visible) {
            predictiveProgress.snapTo(0f)
            if (reduce) {
                progress.snapTo(1f)
            } else {
                progress.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.90f,
                        stiffness = 680f,
                        visibilityThreshold = 0.001f,
                    ),
                )
            }
        } else {
            if (!reduce && progress.value > 0.001f) {
                progress.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 980f,
                        visibilityThreshold = 0.002f,
                    ),
                )
            } else {
                progress.snapTo(0f)
            }
            predictiveProgress.snapTo(0f)
            isPresented = false
            currentOnExited()
        }
    }

    if (dismissOnBackPress) {
        PredictiveBackHandler(enabled = predictiveBackEnabled) { flow ->
            try {
                flow.collect { event ->
                    swipeEdge = event.swipeEdge
                    predictiveProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                currentOnDismiss()
            } catch (e: CancellationException) {
                scope.launch {
                    if (predictiveProgress.value > 0f && !reduce) {
                        predictiveProgress.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 1400f,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    } else {
                        predictiveProgress.snapTo(0f)
                    }
                }
                throw e
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val pred = predictiveProgress.value.coerceIn(0f, 1f)
                    alpha = p * (1f - pred * 0.65f)
                }.background(scrimColor.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (visible && currentCanClickOutside) currentOnDismiss()
                    }
                },
        )
        Box(
            modifier =
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(min = 280.dp, max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        val pred = predictiveProgress.value.coerceIn(0f, 1f)
                        val dir = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        val sc = (0.90f + 0.10f * p) * (1f - 0.10f * pred)
                        scaleX = sc
                        scaleY = sc
                        translationX = dir * 14.dp.toPx() * pred
                        translationY = 12.dp.toPx() * (1f - p) + 12.dp.toPx() * pred
                        alpha = (p * 1.4f).coerceIn(0f, 1f) * (1f - 0.2f * pred)
                    }.pointerInput(Unit) { detectTapGestures {} },
        ) {
            content()
        }
    }
}

/**
 * 窗口内 / 桌面悬浮层通用底部抽屉物理容器（参考 Apple Sheet 与 Google M3 预测性返回规范）：
 * - 入场/退场使用无回弹阻尼弹簧，遮罩透明度与抽屉位移/手势进度连续同步，无闪烁与 0ms 截断；
 * - 支持 1:1 下拉关闭手势与上拉橡皮筋阻力，释放或快速甩动时从当前位置丝滑退场；
 * - 内置 [PredictiveBackHandler]：边缘返回手势跟手微缩下沉并淡出遮罩，取消平滑回弹，提交连续退场。
 */
@Composable
fun MintSheetOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.28f,
    canDragDismiss: Boolean = true,
    handlePredictiveBack: Boolean = true,
    predictiveBackEnabled: Boolean = visible,
    needsConfirmOnDismiss: Boolean = false,
    onDismissConfirmRequest: (() -> Unit)? = null,
    onExited: () -> Unit = {},
    sheetModifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    maxWidth: Dp = Dp.Unspecified,
    dragHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrimColor = MaterialTheme.colorScheme.scrim

    var isPresented by remember { mutableStateOf(visible) }
    if (visible && !isPresented) isPresented = true
    if (!visible && !isPresented) return

    val progress = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val predictiveProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnConfirm by rememberUpdatedState(onDismissConfirmRequest ?: onDismissRequest)
    val currentOnExited by rememberUpdatedState(onExited)
    val currentNeedsConfirm by rememberUpdatedState(needsConfirmOnDismiss)
    val currentCanDrag by rememberUpdatedState(canDragDismiss)

    LaunchedEffect(visible, reduce) {
        if (visible) {
            dragOffsetY.snapTo(0f)
            predictiveProgress.snapTo(0f)
            if (reduce) {
                progress.snapTo(1f)
            } else {
                progress.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.92f,
                        stiffness = 620f,
                        visibilityThreshold = 0.001f,
                    ),
                )
            }
        } else {
            if (!reduce && progress.value > 0.001f) {
                progress.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 920f,
                        visibilityThreshold = 0.002f,
                    ),
                )
            } else {
                progress.snapTo(0f)
            }
            dragOffsetY.snapTo(0f)
            predictiveProgress.snapTo(0f)
            isPresented = false
            currentOnExited()
        }
    }

    if (handlePredictiveBack) {
        if (needsConfirmOnDismiss) {
            BackHandler(enabled = predictiveBackEnabled) {
                currentOnConfirm()
            }
        } else {
            PredictiveBackHandler(enabled = predictiveBackEnabled) { flow ->
                try {
                    flow.collect { event ->
                        swipeEdge = event.swipeEdge
                        predictiveProgress.snapTo(event.progress.coerceIn(0f, 1f))
                    }
                    currentOnDismiss()
                } catch (e: CancellationException) {
                    scope.launch {
                        if (predictiveProgress.value > 0f && !reduce) {
                            predictiveProgress.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 1400f,
                                    visibilityThreshold = 0.001f,
                                ),
                            )
                        } else {
                            predictiveProgress.snapTo(0f)
                        }
                    }
                    throw e
                }
            }
        }
    }

    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val flingVelocityPx = with(density) { 800.dp.toPx() }
    val maxRubberUpPx = with(density) { 26.dp.toPx() }
    val fallbackSlidePx = with(density) { 320.dp.toPx() }

    fun applyDragDelta(dy: Float): Float {
        if (!currentCanDrag || !visible) return 0f
        val cur = dragOffsetY.value
        val next =
            if (cur + dy >= 0f) {
                cur + dy
            } else {
                (cur + dy * 0.18f).coerceAtLeast(-maxRubberUpPx)
            }
        scope.launch { dragOffsetY.snapTo(next) }
        return dy
    }

    fun finishDrag(velocityY: Float) {
        val cur = dragOffsetY.value
        if (cur == 0f) return
        val shouldDismiss = currentCanDrag && (cur > dismissThresholdPx || (cur > 0f && velocityY > flingVelocityPx))
        if (shouldDismiss) {
            if (currentNeedsConfirm) {
                scope.launch {
                    dragOffsetY.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 750f))
                }
                currentOnConfirm()
            } else {
                currentOnDismiss()
            }
        } else {
            scope.launch {
                if (reduce) {
                    dragOffsetY.snapTo(0f)
                } else {
                    dragOffsetY.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 750f))
                }
            }
        }
    }

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!currentCanDrag || !visible || source != NestedScrollSource.UserInput) return Offset.Zero
                    val cur = dragOffsetY.value
                    if (cur > 0f && available.y < 0f) {
                        val next = (cur + available.y).coerceAtLeast(0f)
                        val consumed = next - cur
                        scope.launch { dragOffsetY.snapTo(next) }
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!currentCanDrag || !visible || source != NestedScrollSource.UserInput || available.y == 0f) {
                        return Offset.Zero
                    }
                    return Offset(0f, applyDragDelta(available.y))
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (dragOffsetY.value != 0f) {
                        finishDrag(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (dragOffsetY.value != 0f) {
                        finishDrag(available.y)
                    }
                    return Velocity.Zero
                }
            }
        }

    fun requestTapDismiss() {
        if (!visible) return
        if (currentNeedsConfirm) currentOnConfirm() else currentOnDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val pred = predictiveProgress.value.coerceIn(0f, 1f)
                    val slidePx = if (sheetHeightPx > 0f) sheetHeightPx else fallbackSlidePx
                    val dragFrac = (dragOffsetY.value / slidePx).coerceIn(0f, 1f)
                    alpha = p * (1f - dragFrac * 0.85f) * (1f - pred * 0.65f)
                }.background(scrimColor.copy(alpha = scrimAlpha))
                .pointerInput(Unit) { detectTapGestures { requestTapDismiss() } },
        )

        val surfaceModifier =
            sheetModifier
                .let { if (maxWidth != Dp.Unspecified) it.widthIn(max = maxWidth) else it }
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val pred = predictiveProgress.value.coerceIn(0f, 1f)
                    val slidePx = if (sheetHeightPx > 0f) sheetHeightPx else fallbackSlidePx
                    val dir = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    val predTx = dir * 16.dp.toPx() * pred
                    val predTy = 24.dp.toPx() * pred
                    val sc =
                        if (pred > 0.001f) {
                            (1f - 0.08f * pred) - (1f - p) * 0.05f
                        } else {
                            1f - (1f - p) * 0.02f
                        }
                    scaleX = sc
                    scaleY = sc
                    translationX = predTx
                    translationY = (1f - p) * slidePx + dragOffsetY.value + predTy
                    alpha =
                        if (pred > 0.01f && !visible) {
                            p
                        } else {
                            (p * 3.5f).coerceIn(0f, 1f)
                        }
                }.pointerInput(Unit) { detectTapGestures {} }

        Surface(
            modifier = surfaceModifier,
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (dragHandle) {
                    val dragState = rememberDraggableState { applyDragDelta(it) }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .draggable(
                                    state = dragState,
                                    orientation = Orientation.Vertical,
                                    enabled = canDragDismiss && visible,
                                    onDragStopped = { finishDrag(it) },
                                ).padding(top = 12.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)),
                        )
                    }
                }
                content()
            }
        }
    }
}
