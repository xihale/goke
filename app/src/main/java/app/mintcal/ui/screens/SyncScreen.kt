package app.mintcal.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.R
import app.mintcal.calendar.SystemCalendarSync
import app.mintcal.sync.SyncAccount
import app.mintcal.sync.SyncClient
import app.mintcal.sync.SyncException
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.TonalField
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun syncStamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SYNC_STAMP)

/** 「账号与同步」：系统日历（桌面与第三方账号双向互通） + 自建云端服务器同步。 */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val cloudState by app.sync.state.collectAsState()
    val calState by app.calendarSync.state.collectAsState()
    val refreshing = cloudState.syncing || calState.syncing

    MintScreen(title = stringResource(R.string.sync_title), onBack = onBack) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    runCatching { app.calendarSync.syncNow() }
                    runCatching { app.sync.syncNow() }
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val account = cloudState.account
                if (account == null) {
                    LoginPanel()
                } else {
                    AccountPanel(account, cloudState.syncing, cloudState.lastSyncAt, cloudState.lastError, cloudState.needsRelogin)
                    CloudMcpCard(account)
                }
                SystemCalendarSection()
            }
        }
    }
}

@Composable
private fun SystemCalendarSection() {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val state by app.calendarSync.state.collectAsState()
    var hasPerm by remember { mutableStateOf(app.calendarSync.hasPermissions()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPerm = app.calendarSync.hasPermissions()
        if (hasPerm) scope.launch { app.calendarSync.refreshCalendars() }
    }
    LaunchedEffect(hasPerm) {
        if (hasPerm) app.calendarSync.refreshCalendars()
    }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val granted = res[Manifest.permission.READ_CALENDAR] == true && res[Manifest.permission.WRITE_CALENDAR] == true
            hasPerm = granted
            if (granted) {
                scope.launch {
                    app.calendarSync.refreshCalendars()
                    app.calendarSync.setEnabled(true, app.uiActive.value)
                }
            }
        }

    FormSection(stringResource(R.string.cal_sync_section)) {
        if (!hasPerm) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.cal_sync_enable), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.cal_sync_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    },
                ) {
                    Text(stringResource(R.string.cal_sync_grant))
                }
            }
        } else {
            SwitchRow(
                title = stringResource(R.string.cal_sync_enable),
                subtitle = stringResource(R.string.cal_sync_enable_desc),
                checked = state.prefs.enabled,
                onChange = { app.calendarSync.setEnabled(it, app.uiActive.value) },
            )
            if (state.prefs.enabled) {
                SwitchRow(
                    title = stringResource(R.string.cal_sync_courses),
                    subtitle = stringResource(R.string.cal_sync_courses_desc),
                    checked = state.prefs.exportCourses,
                    onChange = { app.calendarSync.setExportCourses(it) },
                )
                Text(
                    stringResource(R.string.cal_sync_duplicate_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )

                Text(
                    stringResource(R.string.cal_sync_accounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )

                if (state.calendars.isEmpty()) {
                    Text(
                        stringResource(R.string.cal_sync_no_calendars),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    state.calendars.forEach { cal ->
                        val checked = cal.id in state.prefs.selectedCalendarIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .mintClickable(role = Role.Checkbox) { app.calendarSync.toggleCalendar(cal.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MintCheck(
                                checked = checked,
                                onChange = { app.calendarSync.toggleCalendar(cal.id) },
                                size = 20.dp,
                            )
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(cal.color or 0xFF000000.toInt())),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cal.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (cal.accountName.isNotBlank() && cal.accountName != cal.displayName) {
                                    Text(
                                        cal.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                val hasLocalMint = state.calendars.any { it.accountName == SystemCalendarSync.LOCAL_ACCOUNT_NAME }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        enabled = !state.syncing && state.prefs.selectedCalendarIds.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val n = app.calendarSync.syncNow()
                                actions.toast(ctx.getString(R.string.cal_sync_done_toast, n))
                            }
                        },
                    ) {
                        if (state.syncing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.sync_now))
                        }
                    }
                    if (!hasLocalMint) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    if (app.calendarSync.ensureLocalCalendar() != null) {
                                        actions.toast(ctx.getString(R.string.cal_sync_local_created))
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.cal_sync_create_local))
                        }
                    }
                }

                state.prefs.lastSyncAt?.let { ts ->
                    InfoLine(stringResource(R.string.sync_last), syncStamp(ts))
                }
                state.lastError?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
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

    FormSection(stringResource(R.string.sync_login_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TonalField(
                stringResource(R.string.sync_server),
                url,
                { url = it },
                placeholder = "https://cal.example.com",
                supporting = if (SyncClient.isInsecure(url.trim().lowercase())) stringResource(R.string.sync_http_warning) else null,
                isError = SyncClient.isInsecure(url.trim().lowercase()),
            )
            TonalField(stringResource(R.string.sync_username), user, { user = it })
            TonalField(stringResource(R.string.sync_password), password, { password = it }, secret = true)
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
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_login))
            }
        }
    }
}

@Composable
private fun AccountPanel(
    account: SyncAccount,
    syncing: Boolean,
    lastSyncAt: Long?,
    lastError: String?,
    needsRelogin: Boolean,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showPassword by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showRelogin by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.sync_mode_cloud)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (needsRelogin) {
                MintCard(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.sync_relogin_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        stringResource(R.string.sync_relogin_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    Button(onClick = { showRelogin = true }) {
                        Text(stringResource(R.string.sync_relogin))
                    }
                }
            } else if (lastError != null) {
                Text(lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !syncing && !needsRelogin, onClick = {
                    scope.launch {
                        try {
                            val n = app.sync.syncNow()
                            actions.toast(ctx.getString(R.string.sync_done_toast, n))
                        } catch (_: SyncException) {
                            // 错误展示在状态行
                        }
                    }
                }) {
                    if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_now))
                }
                FilledTonalButton(onClick = { showPassword = true }) { Text(stringResource(R.string.sync_change_password)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (account.role == "admin") {
                    TextButton(onClick = { runCatching { uriHandler.openUri(account.serverUrl + "/admin/") } }) {
                        Text(stringResource(R.string.sync_open_admin))
                    }
                }
                TextButton(onClick = { showLogout = true }) { Text(stringResource(R.string.sync_logout), color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showRelogin) {
        ReloginDialog(account = account, onDismiss = { showRelogin = false })
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
private fun CloudMcpCard(account: SyncAccount) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val endpoint = app.sync.cloudMcpEndpoint() ?: "${account.serverUrl.trimEnd('/')}/mcp"

    FormSection(stringResource(R.string.sync_cloud_mcp_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.sync_cloud_mcp_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ValueRow(
                stringResource(R.string.sync_cloud_mcp_endpoint),
                endpoint,
                onClick = {
                    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Cloud MCP Endpoint", endpoint))
                    actions.toast(ctx.getString(R.string.sync_copied_mcp_config))
                },
            )
            FilledTonalButton(
                onClick = {
                    val cfg = app.sync.cloudMcpConfigJson() ?: return@FilledTonalButton
                    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Cloud MCP JSON", cfg))
                    actions.toast(ctx.getString(R.string.sync_copied_mcp_config))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sync_copy_mcp_config))
            }
        }
    }
}

@Composable
private fun ReloginDialog(
    account: SyncAccount,
    onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.sync_relogin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine(stringResource(R.string.sync_server), account.serverUrl)
                InfoLine(stringResource(R.string.sync_account), account.username)
                FieldText(stringResource(R.string.sync_password), password, { password = it }, secret = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.sync.login(account.serverUrl, account.username, password)
                            onDismiss()
                        } catch (e: SyncException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_relogin))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.sync_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldText(stringResource(R.string.sync_old_password), old, { old = it }, secret = true)
                FieldText(
                    stringResource(R.string.sync_new_password),
                    new,
                    { new = it },
                    secret = true,
                    supporting = stringResource(R.string.sync_password_rule),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && old.isNotEmpty() && new.length >= 8,
                onClick = {
                    if (busy) return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.sync.changePassword(old, new)
                            actions.toast(ctx.getString(R.string.sync_password_changed))
                            onDismiss()
                        } catch (e: SyncException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(start = 16.dp))
    }
}
