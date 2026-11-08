package app.mintcal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.dp

/** 无框输入：标签在上，字直接落在页面上，和待办标题同一套。 */
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
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val labelColor = if (isError) scheme.error else scheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
                    .semantics { contentDescription = label },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
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
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = labelColor)
    }
}

/** 单选：选中只改成主色，不套芯片。 */
@Composable
fun <T> ChipChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                modifier =
                    Modifier
                        .heightIn(min = 40.dp)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = on
                        }.mintClickable { onSelect(v) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            )
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
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .mintClickable(role = Role.Button, onClick = onClick)
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
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = if (title != null) 16.dp else 4.dp, bottom = 4.dp)) {
        if (title != null) SectionLabel(title)
        content()
    }
}
