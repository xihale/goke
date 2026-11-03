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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.sync.SyncAccount
import app.mintcal.sync.SyncClient
import app.mintcal.sync.SyncException
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")

private fun syncStamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SYNC_STAMP)

/** 「账号与同步」：未登录是本地模式；登录自建服务器后开启云端同步。 */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val state by app.sync.state.collectAsState()
    MintScreen(title = stringResource(R.string.sync_title), onBack = onBack) {
        // 下拉刷新：仅登录后有效（未登录 requestSync 不做任何事）
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = { scope.launch { runCatching { app.sync.syncNow() } } },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val account = state.account
                if (account == null) LoginPanel() else AccountPanel(account, state.syncing, state.lastSyncAt, state.lastError)
            }
        }
    }
}

@Composable
private fun LoginPanel() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(app.sync.lastServerUrl) }
    var user by remember { mutableStateOf(app.sync.lastUsername) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    FormSection(stringResource(R.string.sync_mode_local)) {
        Text(stringResource(R.string.sync_local_desc), style = MaterialTheme.typography.bodyMedium)
    }
    FormSection(stringResource(R.string.sync_login_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sync_login_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.sync_server)) },
                placeholder = { Text("https://cal.example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
            )
            if (SyncClient.isInsecure(url.trim().lowercase())) {
                Text(stringResource(R.string.sync_http_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FieldText(stringResource(R.string.sync_username), user, { user = it })
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.sync_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.sync.login(url, user, password)
                            password = ""
                        } catch (e: SyncException) {
                            // 登录本身成功但首次同步失败时，账号已建立；这里只展示错误
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_login))
            }
            Text(stringResource(R.string.sync_no_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountPanel(
    account: SyncAccount,
    syncing: Boolean,
    lastSyncAt: Long?,
    lastError: String?,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showPassword by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.sync_mode_cloud)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sync_cloud_desc), style = MaterialTheme.typography.bodyMedium)
            InfoLine(stringResource(R.string.sync_server), account.serverUrl)
            InfoLine(
                stringResource(R.string.sync_account),
                account.username + if (account.role == "admin") " · " + stringResource(R.string.sync_role_admin) else "",
            )
            InfoLine(
                stringResource(R.string.sync_last),
                when {
                    syncing -> stringResource(R.string.sync_running)
                    lastSyncAt != null -> syncStamp(lastSyncAt)
                    else -> stringResource(R.string.sync_never)
                },
            )
            if (lastError != null) {
                Text(lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !syncing, onClick = {
                    scope.launch {
                        try {
                            val n = app.sync.syncNow()
                            actions.toast(ctx.getString(R.string.sync_done_toast, n))
                        } catch (_: SyncException) {
                            // 错误已显示在状态里
                        }
                    }
                }) {
                    if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_now))
                }
                OutlinedButton(onClick = { showPassword = true }) { Text(stringResource(R.string.sync_change_password)) }
            }
            if (account.role == "admin") {
                OutlinedButton(onClick = { runCatching { uriHandler.openUri(account.serverUrl + "/admin/") } }) {
                    Text(stringResource(R.string.sync_open_admin))
                }
            }
            TextButton(onClick = { showLogout = true }) { Text(stringResource(R.string.sync_logout), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.sync_logout)) },
            text = { Text(stringResource(R.string.sync_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    scope.launch { app.sync.logout() }
                }) { Text(stringResource(R.string.sync_logout)) }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPassword) {
        ChangePasswordDialog(onDismiss = { showPassword = false })
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = old,
                    onValueChange = { old = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_old_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_new_password)) },
                    supportingText = { Text(stringResource(R.string.sync_password_rule)) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = old.isNotEmpty() && new.length >= 8, onClick = {
                scope.launch {
                    try {
                        app.sync.changePassword(old, new)
                        actions.toast(ctx.getString(R.string.sync_password_changed))
                        onDismiss()
                    } catch (e: SyncException) {
                        error = e.message
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
