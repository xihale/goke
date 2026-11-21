package app.mintcal.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.jxfw.JxfwClient
import app.mintcal.jxfw.JxfwException
import app.mintcal.jxfw.JxfwState
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.TonalField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

internal fun jxfwStamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(STAMP)

/** 「教务系统」：登录学校教务 → 自动导入课表 / 考试，每周日晚增量同步。 */
@Composable
fun JxfwScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val state by app.jxfw.state.collectAsState()
    MintScreen(title = stringResource(R.string.jxfw_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loggedIn) AccountPanel(state) else LoginPanel(state)
        }
    }
}

@Composable
private fun errorText(
    kind: JxfwException.Kind?,
    raw: String?,
): String? =
    when (kind) {
        null -> raw
        JxfwException.Kind.CREDENTIALS -> stringResource(R.string.jxfw_err_credentials)
        JxfwException.Kind.CAPTCHA -> stringResource(R.string.jxfw_err_captcha)
        JxfwException.Kind.SMS -> stringResource(R.string.jxfw_err_sms)
        JxfwException.Kind.NETWORK -> stringResource(R.string.jxfw_err_network)
        else -> raw
    }

@Composable
internal fun JxfwErrorGuidance(
    kind: JxfwException.Kind?,
    raw: String?,
) {
    val msg = errorText(kind, raw) ?: return
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (kind == JxfwException.Kind.CAPTCHA || kind == JxfwException.Kind.SMS) {
            TextButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(JxfwClient.HOST))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(JxfwClient.HOST.removePrefix("https://"))
            }
        }
    }
}

@Composable
private fun LoginPanel(state: JxfwState) {
    val app = LocalApp.current
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.jxfw_login_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        TonalField(
            label = stringResource(R.string.jxfw_username),
            value = user,
            onChange = { user = it },
            number = true,
        )
        TonalField(
            label = stringResource(R.string.jxfw_password),
            value = password,
            onChange = { password = it },
            secret = true,
        )
        JxfwErrorGuidance(state.lastErrorKind, state.lastError)
        Spacer(Modifier.height(4.dp))
        Button(
            enabled = !busy && user.isNotBlank() && password.isNotEmpty(),
            onClick = {
                busy = true
                app.scope.launch {
                    try {
                        app.jxfw.login(user, password)
                        password = ""
                    } catch (_: JxfwException) {
                        // 错误已写入状态并显示
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        ) {
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                    Text(state.progress ?: stringResource(R.string.jxfw_progress_login))
                }
            } else {
                Text(stringResource(R.string.jxfw_login))
            }
        }
        Text(
            stringResource(R.string.jxfw_keystore_reassurance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AccountPanel(state: JxfwState) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var showLogout by remember { mutableStateOf(false) }
    val currentTerm = remember { app.jxfw.currentTerm() }
    val terms = remember(currentTerm, state.term) { app.jxfw.availableTerms(state.term) }
    var selectedTerm by remember(state.term) { mutableStateOf(state.term ?: currentTerm) }

    MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.jxfw_account_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoLine(stringResource(R.string.jxfw_account), state.username.orEmpty())
            state.term?.let { InfoLine(stringResource(R.string.jxfw_term), jxfwTermLabel(ctx, it)) }
            InfoLine(stringResource(R.string.jxfw_last_full), state.lastFullAt?.let(::jxfwStamp) ?: stringResource(R.string.jxfw_never))
            InfoLine(stringResource(R.string.jxfw_last_weekly), state.lastWeeklyAt?.let(::jxfwStamp) ?: stringResource(R.string.jxfw_never))
            if (state.busy) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(state.progress.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } else {
                state.lastResult?.let { InfoLine(stringResource(R.string.jxfw_last_result), it) }
            }
            JxfwErrorGuidance(state.lastErrorKind, state.lastError)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !state.busy,
                onClick = {
                    scope.launch {
                        try {
                            app.jxfw.fullSync(selectedTerm)
                            actions.toast(ctx.getString(R.string.jxfw_done_toast))
                        } catch (_: JxfwException) {
                            // 错误已写入状态并显示
                        }
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.jxfw_sync_full), maxLines = 1) }
            FilledTonalButton(
                enabled = !state.busy,
                onClick = {
                    scope.launch {
                        try {
                            app.jxfw.weeklySync()
                        } catch (_: JxfwException) {
                            // 同上
                        }
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) { Text(stringResource(R.string.jxfw_sync_week), maxLines = 1) }
        }
    }

    FormSection {
        NavRow(
            title = stringResource(R.string.grades_title),
            subtitle = stringResource(R.string.grades_jxfw_entry_desc),
            icon = Icons.Rounded.Grade,
            onClick = { nav.push(Route.Grades) },
        )
    }

    FormSection(stringResource(R.string.jxfw_term_picker)) {
        val currentLabel = stringResource(R.string.jxfw_term_current)
        ChipChoice(
            options =
                terms.map { code ->
                    val label = jxfwTermLabel(ctx, code)
                    code to if (code == currentTerm) "$label · $currentLabel" else label
                },
            selected = selectedTerm,
            onSelect = { selectedTerm = it },
        )
    }

    FormSection {
        SwitchRow(
            title = stringResource(R.string.jxfw_auto),
            checked = state.autoSync,
            onChange = { app.jxfw.setAutoSync(it) },
        )
    }
    Text(
        stringResource(R.string.jxfw_keystore_reassurance),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    TextButton(onClick = { showLogout = true }) {
        Text(stringResource(R.string.jxfw_logout), color = MaterialTheme.colorScheme.error)
    }

    if (showLogout) {
        ConfirmDialog(
            stringResource(R.string.jxfw_logout),
            stringResource(R.string.jxfw_logout_message),
            stringResource(R.string.jxfw_logout),
            onConfirm = { scope.launch { app.jxfw.logout() } },
            onDismiss = { showLogout = false },
            danger = true,
        )
    }
}

/** 把教务学期代码（如 "202601"）格式化为「2026–2027 学年第一学期」。 */
internal fun jxfwTermLabel(
    ctx: Context,
    code: String,
): String {
    val year = code.take(4).toIntOrNull()
    val sem = code.drop(4).toIntOrNull()
    return when {
        year != null && sem == 1 -> ctx.getString(R.string.jxfw_term_first, year, year + 1)
        year != null && sem == 2 -> ctx.getString(R.string.jxfw_term_second, year, year + 1)
        else -> code
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
