package app.mintcal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.dp
import app.mintcal.ui.theme.MintTheme

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
        MintTheme.spec(),
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
    val thumbAlpha by animateFloatAsState(if (rawIndex >= 0) 1f else 0f, MintTheme.spec(), label = "seg-thumb-alpha")
    val haptics = LocalHapticFeedback.current
    val travel = remember { Animatable(selectedIndex.toFloat()) }
    val spec = MintTheme.spec<Float>()
    LaunchedEffect(rawIndex) {
        if (rawIndex >= 0) travel.animateTo(rawIndex.toFloat(), spec)
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val track = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerHighest
    val thumb = if (dark) lerp(track, scheme.onSurface, 0.16f) else scheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (thumbAlpha > 0.01f) {
                val inset = 3.dp.toPx()
                val slot = size.width / options.size
                val w = (slot - inset * 2).coerceAtLeast(0f)
                val h = (size.height - inset * 2).coerceAtLeast(0f)
                val left = travel.value * slot + inset
                drawRoundRect(
                    color = thumb.copy(alpha = thumbAlpha),
                    topLeft = Offset(left, inset),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            options.forEach { (v, label) ->
                val on = v == selected
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.96f else 1f, MintTheme.spec(), label = "seg-press")
                val fg by animateColorAsState(
                    if (on) scheme.primary else scheme.onSurfaceVariant,
                    MintTheme.spec(),
                    label = "seg-fg",
                )
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
                            onClick = {
                                if (!on) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(v)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        style = MaterialTheme.typography.labelLarge,
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
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, label) ->
            val on = v == selected
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
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = on
                    }.mintClickable(pressedScale = 0.96f) { onSelect(v) }
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
