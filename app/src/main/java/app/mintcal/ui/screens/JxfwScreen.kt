package app.mintcal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.jxfw.JxfwException
import app.mintcal.jxfw.JxfwState
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SwitchRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun stamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(STAMP)

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
private fun LoginPanel(state: JxfwState) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.jxfw_login_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.jxfw_intro), style = MaterialTheme.typography.bodyMedium)
            FieldText(stringResource(R.string.jxfw_username), user, { user = it })
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.jxfw_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
            )
            Text(stringResource(R.string.jxfw_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorText(state.lastErrorKind, state.lastError)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !busy && user.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
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
            ) {
                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(state.progress ?: stringResource(R.string.jxfw_progress_login))
                    }
                } else {
                    Text(stringResource(R.string.jxfw_login))
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(state: JxfwState) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var showLogout by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.jxfw_account_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoLine(stringResource(R.string.jxfw_account), state.username.orEmpty())
            state.term?.let { InfoLine(stringResource(R.string.jxfw_term), it) }
            InfoLine(stringResource(R.string.jxfw_last_full), state.lastFullAt?.let(::stamp) ?: stringResource(R.string.jxfw_never))
            InfoLine(stringResource(R.string.jxfw_last_weekly), state.lastWeeklyAt?.let(::stamp) ?: stringResource(R.string.jxfw_never))
            if (state.busy) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(state.progress.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                state.lastResult?.let { InfoLine(stringResource(R.string.jxfw_last_result), it) }
            }
            errorText(state.lastErrorKind, state.lastError)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.busy, onClick = {
                    scope.launch {
                        try {
                            app.jxfw.fullSync()
                            actions.toast(ctx.getString(R.string.jxfw_done_toast))
                        } catch (_: JxfwException) {
                            // 错误已写入状态并显示
                        }
                    }
                }) { Text(stringResource(R.string.jxfw_sync_full)) }
                OutlinedButton(enabled = !state.busy, onClick = {
                    scope.launch {
                        try {
                            app.jxfw.weeklySync()
                        } catch (_: JxfwException) {
                            // 同上
                        }
                    }
                }) { Text(stringResource(R.string.jxfw_sync_week)) }
            }
        }
    }
    FormSection(stringResource(R.string.jxfw_auto)) {
        SwitchRow(
            title = stringResource(R.string.jxfw_auto),
            subtitle = stringResource(R.string.jxfw_auto_desc),
            checked = state.autoSync,
            onChange = { app.jxfw.setAutoSync(it) },
        )
        TextButton(onClick = { showLogout = true }) { Text(stringResource(R.string.jxfw_logout), color = MaterialTheme.colorScheme.error) }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.jxfw_logout)) },
            text = { Text(stringResource(R.string.jxfw_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    scope.launch { app.jxfw.logout() }
                }) { Text(stringResource(R.string.jxfw_logout)) }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}
