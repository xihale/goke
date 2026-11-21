package app.mintcal.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.R
import app.mintcal.notify.Notifications
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.Settings as AndroidSettings

private data class ReliabilityState(
    val notifOn: Boolean = true,
    val channelOn: Boolean = true,
    val exactOn: Boolean = true,
    val batteryOn: Boolean = true,
    val parcelListenerOn: Boolean = false,
    val parcelChannelOn: Boolean = true,
    val dndOn: Boolean = false,
    val calendarPermOn: Boolean = false,
)

/**
 * 提醒可靠性自检：逐项检查会让提醒「悄悄失效」的系统设置，并给出一键跳转；
 * 国产系统（小米 / MIUI 等）的自启动与后台限制没有公开 API 可检测，只能给出操作指引。
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    var state by remember { mutableStateOf(ReliabilityState()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val s =
                withContext(Dispatchers.IO) {
                    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channel =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                            nm.getNotificationChannel(Notifications.CHANNEL_REMINDER)?.importance != NotificationManager.IMPORTANCE_NONE
                    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                    val battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                    val parcelListener = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
                    val parcelChannel =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                            nm.getNotificationChannel(Notifications.CHANNEL_PARCEL)?.importance != NotificationManager.IMPORTANCE_NONE
                    val dnd = nm.isNotificationPolicyAccessGranted
                    val calendar = app.calendarSync.hasPermissions()
                    ReliabilityState(notif, channel, exact, battery, parcelListener, parcelChannel, dnd, calendar)
                }
            state = s
        }
    }

    val calPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    LaunchedEffect(Unit) { refresh() }
    // 从系统设置页回来后在异步协程中重新检测
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val notifOn = state.notifOn
    val channelOn = state.channelOn
    val exactOn = state.exactOn
    val batteryOn = state.batteryOn
    // 已授权的项不再显示一行「已授权」，只把还没过的项列出来；全过时整节隐藏
    val anyIssue = !(notifOn && channelOn && exactOn && batteryOn)

    fun open(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val appDetails = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    // 厂商识别：只展开本机对应的品牌指引，其余收进「查看其他系统设置说明」
    val mfr = Build.MANUFACTURER.lowercase()
    val (vendorTitle, vendorDesc, vendorMatched) =
        when (mfr) {
            in listOf("xiaomi", "redmi", "blackshark") -> Triple(R.string.reliability_vendor_xiaomi, R.string.reliability_vendor_xiaomi_desc, true)
            in listOf("huawei", "honor") -> Triple(R.string.reliability_vendor_huawei, R.string.reliability_vendor_huawei_desc, true)
            in listOf("oppo", "realme", "oneplus") -> Triple(R.string.reliability_vendor_oppo, R.string.reliability_vendor_oppo_desc, true)
            in listOf("vivo", "iqoo") -> Triple(R.string.reliability_vendor_vivo, R.string.reliability_vendor_vivo_desc, true)
            else -> Triple(R.string.reliability_vendor, R.string.reliability_vendor_desc, false)
        }
    var showOtherVendors by remember { mutableStateOf(false) }

    MintScreen(title = stringResource(R.string.reliability_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!anyIssue) {
                // 四项全部就绪时的安心状态卡，避免整屏看起来像坏了
                FormSection(stringResource(R.string.reliability_checks)) {
                    MintCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(24.dp), tint = MintTheme.colors.success)
                            Text(stringResource(R.string.settings_perm_desc_ok), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (anyIssue) {
                FormSection(stringResource(R.string.reliability_checks)) {
                    if (!notifOn) {
                        CheckRow(stringResource(R.string.reliability_notif), stringResource(R.string.reliability_notif_desc), notifOn, stringResource(R.string.reliability_fix)) {
                            open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                    }
                    if (!channelOn) {
                        CheckRow(stringResource(R.string.reliability_channel), stringResource(R.string.reliability_channel_desc), channelOn, stringResource(R.string.reliability_fix)) {
                            open(
                                Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_REMINDER),
                            )
                        }
                    }
                    if (!exactOn) {
                        CheckRow(stringResource(R.string.reliability_exact), stringResource(R.string.reliability_exact_desc), exactOn, stringResource(R.string.reliability_fix)) {
                            open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                        }
                    }
                    if (!batteryOn) {
                        CheckRow(stringResource(R.string.reliability_battery), stringResource(R.string.reliability_battery_desc), batteryOn, stringResource(R.string.reliability_fix)) {
                            open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                }
            }
            FormSection(stringResource(vendorTitle)) {
                MintCard {
                    Text(stringResource(vendorDesc), style = MaterialTheme.typography.bodyMedium)
                    if (vendorMatched) {
                        TextButton(onClick = { showOtherVendors = !showOtherVendors }) { Text(stringResource(R.string.reliability_vendor_all_toggle)) }
                        AnimatedVisibility(
                            visible = showOtherVendors,
                            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                        ) {
                            Text(
                                stringResource(R.string.reliability_vendor_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open(appDetails) }) { Text(stringResource(R.string.reliability_open_details)) }
                    }
                }
            }
            FormSection(stringResource(R.string.reliability_section_optional)) {
                val parcelReady = state.parcelListenerOn && state.parcelChannelOn
                OptionalCheckRow(
                    title = stringResource(R.string.reliability_parcel_listener),
                    desc = stringResource(R.string.reliability_parcel_listener_desc),
                    ready = parcelReady,
                    onAction = {
                        when {
                            !state.parcelListenerOn -> {
                                open(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }

                            !state.parcelChannelOn -> {
                                open(
                                    Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_PARCEL),
                                )
                            }

                            else -> {
                                open(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }
                    },
                )
                OptionalCheckRow(
                    title = stringResource(R.string.reliability_dnd_policy),
                    desc = stringResource(R.string.reliability_dnd_policy_desc),
                    ready = state.dndOn,
                    onAction = { open(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                )
                OptionalCheckRow(
                    title = stringResource(R.string.reliability_calendar_perm),
                    desc = stringResource(R.string.reliability_calendar_perm_desc),
                    ready = state.calendarPermOn,
                    onAction = {
                        if (!state.calendarPermOn) {
                            calPermLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        } else {
                            open(appDetails)
                        }
                    },
                )
            }
            FormSection(stringResource(R.string.reliability_test)) {
                Button(onClick = {
                    val ok = app.reminders.scheduleTest(ctx.getString(R.string.reliability_test_title), ctx.getString(R.string.reliability_test_text), 60)
                    actions.toast(ctx.getString(if (ok) R.string.reliability_test_scheduled else R.string.reliability_test_failed))
                }) { Text(stringResource(R.string.reliability_test_button)) }
            }
        }
    }
}

@Composable
private fun OptionalCheckRow(
    title: String,
    desc: String,
    ready: Boolean,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            null,
            Modifier.size(24.dp),
            tint = if (ready) MintTheme.colors.added else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (!ready) {
                    Text(
                        stringResource(R.string.reliability_status_not_set),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction) {
            Text(
                stringResource(if (ready) R.string.reliability_status_ready else R.string.reliability_fix),
                color = if (ready) MintTheme.colors.added else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    desc: String,
    ok: Boolean,
    fixLabel: String,
    onFix: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            null,
            Modifier.size(24.dp),
            tint = if (ok) MintTheme.colors.added else MintTheme.colors.removed,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) TextButton(onClick = onFix) { Text(fixLabel) }
    }
}
