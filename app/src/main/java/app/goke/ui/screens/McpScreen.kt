package app.goke.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.goke.R
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.FormSection
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SwitchRow
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.mintClickable
import app.goke.ui.theme.MintTheme

@Composable
fun McpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val state by app.mcp.state.collectAsState()
    var showToken by remember { mutableStateOf(false) }
    var editPort by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        app.mcp.refreshNetwork()
    }

    fun copyText(
        label: String,
        text: String,
        toastRes: Int,
    ) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        actions.toast(ctx.getString(toastRes))
    }

    val enableSubtitle =
        when {
            state.lastError != null -> {
                state.lastError
            }

            state.running && state.requestCount > 0 -> {
                stringResource(R.string.mcp_running_requests, state.endpointHost, state.boundPort, state.requestCount)
            }

            state.running -> {
                stringResource(R.string.mcp_running_summary, state.endpointHost, state.boundPort)
            }

            else -> {
                null
            }
        }

    val lanSubtitle =
        if (state.prefs.allowLan && state.lanIp == null) {
            stringResource(R.string.mcp_lan_ip_unavailable)
        } else {
            stringResource(R.string.mcp_allow_lan_desc)
        }

    MintScreen(title = stringResource(R.string.mcp_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            FormSection {
                SwitchRow(
                    title = stringResource(R.string.mcp_enable),
                    subtitle = enableSubtitle,
                    checked = state.prefs.enabled,
                    onChange = { app.mcp.setEnabled(it) },
                )
            }

            FormSection(stringResource(R.string.mcp_section_connection)) {
                SwitchRow(
                    title = stringResource(R.string.mcp_allow_lan),
                    subtitle = lanSubtitle,
                    checked = state.prefs.allowLan,
                    onChange = { app.mcp.setAllowLan(it) },
                )
                AnimatedVisibility(
                    visible = state.prefs.allowLan,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Text(
                        stringResource(R.string.mcp_lan_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                }
                ValueRow(
                    label = stringResource(R.string.mcp_port),
                    value = state.prefs.port.toString(),
                    onClick = { editPort = true },
                )
                TabularActionRow(
                    label = stringResource(R.string.mcp_endpoint),
                    value = state.endpointUrl,
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { copyText("MCP URL", state.endpointUrl, R.string.mcp_copied_url) },
                )
                TabularActionRow(
                    label = stringResource(R.string.mcp_token),
                    value = if (showToken) state.token else maskToken(state.token),
                    icon = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    onClick = { showToken = !showToken },
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { copyText("MCP JSON", app.mcp.clientConfigJson(), R.string.mcp_copied_config) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mcp_copy_config), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { confirmRegen = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mcp_regen_token), maxLines = 1)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.mcp_footer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mcp_keepalive_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (editPort) {
        PortDialog(
            initial = state.prefs.port,
            onDismiss = { editPort = false },
            onConfirm = { port ->
                app.mcp.setPort(port)
                editPort = false
            },
        )
    }

    if (confirmRegen) {
        ConfirmDialog(
            title = stringResource(R.string.mcp_regen_confirm_title),
            text = stringResource(R.string.mcp_regen_confirm_desc),
            confirm = stringResource(R.string.mcp_regen_token),
            onConfirm = {
                app.mcp.regenerateToken()
                actions.toast(ctx.getString(R.string.mcp_token_regenerated))
            },
            onDismiss = { confirmRegen = false },
        )
    }
}

/** 无框纸面属性行：统一 Noto Sans SC + tnum 等宽读数，与 ValueRow 严守同一行高与尾部 20dp 图标槽位。 */
@Composable
private fun TabularActionRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
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
            Modifier.padding(end = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}

@Composable
private fun PortDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1024..65535
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mcp_port)) },
        text = {
            TonalField(
                label = "",
                value = text,
                onChange = { t -> if (t.length <= 5 && t.all(Char::isDigit)) text = t },
                number = true,
                isError = !valid,
                supporting = if (!valid) stringResource(R.string.detail_minutes_invalid, 1024, 65535) else null,
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(parsed!!) },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

private fun maskToken(token: String): String {
    if (token.length <= 12) return "••••"
    return token.take(7) + "••••" + token.takeLast(4)
}
