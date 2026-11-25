package app.goke.ui.screens

import android.Manifest
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.goke.R
import app.goke.calendar.SystemCalendarSync
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ComingSoonScreen
import app.goke.ui.components.FormSection
import app.goke.ui.components.MintCheck
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SwitchRow
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Cloud
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun syncStamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SYNC_STAMP)

/** 「账号与同步」：自建云端同步功能暂未开放，统一展示敬请期待。 */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    ComingSoonScreen(
        title = stringResource(R.string.sync_title),
        icon = Icons.Rounded.Cloud,
        onBack = onBack,
    )
}

/** 「系统日历同步」：独立的系统日历与桌面账号双向同步设置页（可直接从设置主页进入）。 */
@Composable
fun CalendarSyncScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val calState by app.calendarSync.state.collectAsState()

    MintScreen(title = stringResource(R.string.cal_sync_title), onBack = onBack) {
        PullToRefreshBox(
            isRefreshing = calState.syncing,
            onRefresh = {
                scope.launch {
                    runCatching { app.calendarSync.syncNow() }
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
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(start = 16.dp))
    }
}
