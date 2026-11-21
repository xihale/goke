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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.BuildConfig
import app.mintcal.R
import app.mintcal.data.DarkMode
import app.mintcal.data.ThemeSource
import app.mintcal.notify.Notifications
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.MinutesDialSheet
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.SegmentedPill
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.TimeDialSheet
import app.mintcal.ui.components.TonalField
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import android.provider.Settings as AndroidSettings

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val st = settingsState()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx)) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // 权限入口行与桌面小组件行需要实时状态：从系统设置或桌面回来后在异步协程中重算
    var permOkCount by remember { mutableIntStateOf(4) }
    var widgetAddedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun refreshStatus() {
        scope.launch {
            val (count, widgets) =
                withContext(Dispatchers.IO) {
                    permGrantedCount(ctx) to totalAddedWidgets(ctx)
                }
            if (permOkCount != count) permOkCount = count
            if (widgetAddedCount != widgets) widgetAddedCount = widgets
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshStatus() }

    MintScreen(title = stringResource(R.string.settings), onBack = onBack) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "appearance", contentType = "nav_row") {
                val themeSource =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && st.themeSource == ThemeSource.DYNAMIC) {
                        ThemeSource.PRESET
                    } else {
                        st.themeSource
                    }
                val sourceLabel =
                    when (themeSource) {
                        ThemeSource.DYNAMIC -> stringResource(R.string.theme_dynamic)
                        ThemeSource.PRESET -> stringResource(Preset.entries.firstOrNull { it.key == st.preset }?.label ?: R.string.theme_preset)
                        ThemeSource.CUSTOM -> stringResource(R.string.theme_custom)
                    }
                Box(Modifier.padding(top = 4.dp)) {
                    NavRow(stringResource(R.string.appearance), null, Icons.Rounded.Palette, trailing = sourceLabel) { nav.push(Route.Appearance) }
                }
            }
            item(key = "widgets", contentType = "nav_row") {
                NavRow(
                    stringResource(R.string.widgets_title),
                    null,
                    Icons.Rounded.Widgets,
                    trailing =
                        if (widgetAddedCount > 0) {
                            stringResource(R.string.widgets_added_count, widgetAddedCount)
                        } else {
                            stringResource(R.string.widgets_available_count, WidgetSpec.entries.size)
                        },
                ) { nav.push(Route.Widgets) }
            }
            item(key = "display_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.section_display), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "display_show_teacher", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.show_teacher), null, st.showTeacher) { v -> app.settings.update { it.copy(showTeacher = v) } }
            }
            item(key = "display_week_starts_monday", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.week_starts_monday), null, st.weekStartsMonday) { v -> app.settings.update { it.copy(weekStartsMonday = v) } }
            }
            item(key = "display_swap_swipe", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.swap_swipe), stringResource(R.string.swap_swipe_desc), st.swapSwipe) { v -> app.settings.update { it.copy(swapSwipe = v) } }
            }
            item(key = "display_default_block", contentType = "value_row") {
                var blockDial by remember { mutableStateOf(false) }
                ValueRow(stringResource(R.string.default_block_minutes), durationLabel(st.defaultBlockMinutes), onClick = { blockDial = true })
                if (blockDial) {
                    MinutesDialSheet(st.defaultBlockMinutes, onDismiss = { blockDial = false }) { v ->
                        app.settings.update { it.copy(defaultBlockMinutes = v) }
                    }
                }
            }
            item(key = "reminders_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.section_reminders), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "remind_courses", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.remind_courses), stringResource(R.string.remind_courses_desc), st.remindCourse) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindCourse = v) }
                }
            }
            item(key = "remind_courses_minutes", contentType = "expandable_value_row") {
                AnimatedVisibility(
                    visible = st.remindCourse,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Column {
                        var remindDial by remember { mutableStateOf(false) }
                        ValueRow(
                            stringResource(R.string.remind_courses),
                            if (st.courseRemindMinutes == 0) {
                                stringResource(R.string.settings_remind_at_start)
                            } else {
                                stringResource(R.string.settings_remind_early_n, st.courseRemindMinutes)
                            },
                            onClick = { remindDial = true },
                        )
                        if (remindDial) {
                            MinutesDialSheet(st.courseRemindMinutes, onDismiss = { remindDial = false }) { v ->
                                app.settings.update { it.copy(courseRemindMinutes = v) }
                            }
                        }
                    }
                }
            }
            item(key = "remind_tasks", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.remind_tasks), null, st.remindTask) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindTask = v) }
                }
            }
            item(key = "remind_events", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.remind_events), null, st.remindEvent) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindEvent = v) }
                }
            }
            item(key = "remind_digest", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.remind_digest), stringResource(R.string.remind_digest_desc), st.dailyDigest) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(dailyDigest = v) }
                }
            }
            item(key = "remind_digest_time", contentType = "expandable_value_row") {
                AnimatedVisibility(
                    visible = st.dailyDigest,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    var pickTime by remember { mutableStateOf(false) }
                    Column {
                        ValueRow(
                            stringResource(R.string.digest_time),
                            "%02d:%02d".format(st.digestHour, st.digestMinute),
                            onClick = { pickTime = true },
                        )
                        if (pickTime) {
                            TimeDialSheet(LocalTime.of(st.digestHour, st.digestMinute), onDismiss = { pickTime = false }) { time ->
                                app.settings.update { it.copy(digestHour = time.hour, digestMinute = time.minute) }
                            }
                        }
                    }
                }
            }
            item(key = "remind_quick_notif", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.quick_notif_row), stringResource(R.string.quick_notif_desc), st.quickAddNotif) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(quickAddNotif = v) }
                }
            }
            item(key = "remind_auto_mute", contentType = "switch_row") {
                SwitchRow(stringResource(R.string.auto_mute), stringResource(R.string.auto_mute_desc), st.autoMute) { v ->
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (v && !nm.isNotificationPolicyAccessGranted) {
                        runCatching {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    app.settings.update { it.copy(autoMute = v) }
                }
            }
            item(key = "group_sync_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_sync), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_sync", contentType = "nav_row") {
                SyncNavRow { nav.push(Route.Sync) }
            }
            item(key = "more_jxfw", contentType = "nav_row") {
                JxfwNavRow { nav.push(Route.Jxfw) }
            }
            item(key = "group_tools_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_tools), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_lan_web", contentType = "nav_row") {
                LanWebNavRow { nav.push(Route.LanWeb) }
            }
            item(key = "more_mcp", contentType = "nav_row") {
                McpNavRow { nav.push(Route.Mcp) }
            }
            item(key = "more_parcel", contentType = "nav_row") {
                NavRow(
                    stringResource(R.string.parcel_title),
                    null,
                    Icons.Rounded.LocalShipping,
                    trailing = stringResource(if (st.parcelEnabled) R.string.parcel_status_on else R.string.parcel_status_off),
                ) { nav.push(Route.Parcel) }
            }
            item(key = "group_manage_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_manage), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_reliability", contentType = "nav_row") {
                ReliabilityNavRow(permOkCount = { permOkCount }) { nav.push(Route.Reliability) }
            }
            item(key = "more_data", contentType = "nav_row") {
                NavRow(stringResource(R.string.data_title), null, Icons.Rounded.SwapVert) { nav.push(Route.Data) }
            }
            item(key = "more_history", contentType = "nav_row") {
                NavRow(stringResource(R.string.history_title), null, Icons.Rounded.History) { nav.push(Route.History) }
            }
            item(key = "more_trash", contentType = "nav_row") {
                TrashNavRow { nav.push(Route.Trash) }
            }
            item(key = "group_about_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_about), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_about", contentType = "nav_row") {
                NavRow(stringResource(R.string.about_title), null, Icons.Rounded.Info) { nav.push(Route.About) }
            }
        }
    }
}

@Composable
private fun SyncNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val account by app.sync.state.collectAsState()
    NavRow(
        stringResource(R.string.sync_title),
        null,
        Icons.Rounded.Cloud,
        trailing = account.account?.username,
        onClick = onOpen,
    )
}

@Composable
private fun JxfwNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val jxfwState by app.jxfw.state.collectAsState()
    NavRow(
        stringResource(R.string.jxfw_title),
        null,
        Icons.Rounded.School,
        trailing = jxfwState.username,
        onClick = onOpen,
    )
}

@Composable
private fun LanWebNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val lanState by app.lanWeb.state.collectAsState()
    NavRow(
        stringResource(R.string.lan_web_title),
        if (lanState.running) {
            stringResource(R.string.lan_web_nav_running, lanState.port)
        } else {
            stringResource(R.string.lan_web_nav_off)
        },
        Icons.Rounded.LaptopMac,
        onClick = onOpen,
    )
}

@Composable
private fun McpNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val mcpState by app.mcp.state.collectAsState()
    NavRow(
        stringResource(R.string.mcp_title),
        null,
        Icons.Rounded.Terminal,
        trailing = stringResource(if (mcpState.running) R.string.mcp_status_on else R.string.mcp_status_off),
        onClick = onOpen,
    )
}

@Composable
private fun TrashNavRow(onOpen: () -> Unit) {
    val snap = liveSnapshot()
    val trashCount =
        remember(snap) {
            snap.courses.count { it.deletedAt != null } +
                snap.tasks.count { it.deletedAt != null } +
                snap.events.count { it.deletedAt != null } +
                snap.lists.count { it.deletedAt != null }
        }
    NavRow(
        stringResource(R.string.trash_title),
        null,
        Icons.Rounded.DeleteSweep,
        trailing = if (trashCount > 0) stringResource(R.string.conflict_count, trashCount) else null,
        onClick = onOpen,
    )
}

@Composable
private fun ReliabilityNavRow(
    permOkCount: () -> Int,
    onOpen: () -> Unit,
) {
    NavRow(
        stringResource(R.string.reliability_title),
        null,
        Icons.Rounded.NotificationsActive,
        trailing = permStatusDesc(permOkCount()),
        onClick = onOpen,
    )
}

/** 可检测的四项提醒权限（通知开关 / 提醒渠道 / 精确闹钟 / 电池优化）；自启没有公开 API，不计数。 */
private fun permGrantedCount(ctx: Context): Int {
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            nm.getNotificationChannel(Notifications.CHANNEL_REMINDER)?.importance != NotificationManager.IMPORTANCE_NONE
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    val battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    return listOf(notif, channel, exact, battery).count { it }
}

/** 权限状态短读数：全过 →「已就绪」，否则「N 项待授权」。 */
@Composable
private fun permStatusDesc(granted: Int): String =
    if (granted == 4) {
        stringResource(R.string.settings_perm_desc_ok)
    } else {
        stringResource(R.string.settings_perm_desc_n, 4 - granted)
    }

/** 开机自启没有公开 API 可检测，只能按厂商尝试专页，失败落到应用详情页。 */
private fun openAutoStart(ctx: Context) {
    val pages =
        listOf(
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
    for (page in pages) {
        runCatching {
            ctx.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
}

/** 权限页：提醒送达相关的授权与厂商后台限制，逐项显示状态，点按跳转授权。 */
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var notifOk by remember { mutableStateOf(true) }
    var exactOk by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val (notif, exact, battery) =
                withContext(Dispatchers.IO) {
                    val notif = Build.VERSION.SDK_INT < 33 || Notifications.canPost(ctx)
                    val exact = Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                    val battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                    Triple(notif, exact, battery)
                }
            notifOk = notif
            exactOk = exact
            batteryOk = battery
        }
    }

    LaunchedEffect(Unit) { refresh() }
    // 从系统设置回来后在异步协程中重算，避免状态挂着不消失
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    fun open(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    MintScreen(title = stringResource(R.string.settings_perm_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormSection(stringResource(R.string.settings_perm_section_alerts)) {
                NavRow(
                    stringResource(R.string.reliability_notif),
                    stringResource(if (notifOk) R.string.settings_perm_allowed else R.string.reliability_notif_desc),
                    Icons.Rounded.NotificationsActive,
                ) {
                    open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                NavRow(
                    stringResource(R.string.reliability_exact),
                    stringResource(if (exactOk) R.string.settings_perm_allowed else R.string.reliability_exact_desc),
                    Icons.Rounded.Alarm,
                ) {
                    open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }
            FormSection(stringResource(R.string.settings_perm_section_system)) {
                NavRow(stringResource(R.string.settings_autostart), stringResource(R.string.settings_autostart_desc), Icons.Rounded.RestartAlt) { openAutoStart(ctx) }
                NavRow(
                    stringResource(R.string.settings_battery),
                    stringResource(if (batteryOk) R.string.settings_battery_ok else R.string.settings_battery_bad),
                    Icons.Rounded.BatterySaver,
                ) {
                    if (!batteryOk) {
                        open(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val st = settingsState()
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val sources =
        remember(dynamicOk) {
            buildList {
                if (dynamicOk) add(ThemeSource.DYNAMIC to ctx.getString(R.string.theme_dynamic))
                add(ThemeSource.PRESET to ctx.getString(R.string.theme_preset))
                add(ThemeSource.CUSTOM to ctx.getString(R.string.theme_custom))
            }
        }
    val darkModes =
        remember {
            listOf(
                DarkMode.SYSTEM to ctx.getString(R.string.dark_system),
                DarkMode.LIGHT to ctx.getString(R.string.dark_light),
                DarkMode.DARK to ctx.getString(R.string.dark_dark),
            )
        }
    val presetItems =
        remember {
            Preset.entries.map { it to ctx.getString(it.label) }
        }
    val source = if (!dynamicOk && st.themeSource == ThemeSource.DYNAMIC) ThemeSource.PRESET else st.themeSource
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val onSurfaceColor = scheme.onSurface
    val onSurfaceVariantColor = scheme.onSurfaceVariant
    val labelSmallStyle = typography.labelSmall
    val selectedBorder = remember(onSurfaceColor) { Modifier.border(3.dp, onSurfaceColor, CircleShape) }

    MintScreen(title = stringResource(R.string.appearance), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormSection(stringResource(R.string.theme_color)) {
                SegmentedPill(sources, source, { v -> app.settings.update { it.copy(themeSource = v) } })
                if (!dynamicOk) Text(stringResource(R.string.theme_dynamic_unavailable), style = typography.bodySmall, color = onSurfaceVariantColor, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                when (source) {
                    ThemeSource.DYNAMIC -> {}

                    ThemeSource.PRESET -> {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            presetItems.forEach { (p, name) ->
                                val sel = st.preset == p.key
                                Column(
                                    Modifier.width(84.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(p.seed)
                                            .then(if (sel) selectedBorder else Modifier)
                                            .semantics {
                                                contentDescription = name
                                                role = Role.RadioButton
                                                selected = sel
                                            }.mintClickable(pressedScale = 0.96f) { app.settings.update { it.copy(preset = p.key) } },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = onSurfaceColor)
                                    }
                                    Text(name, style = labelSmallStyle, color = onSurfaceVariantColor, maxLines = 1)
                                }
                            }
                        }
                    }

                    ThemeSource.CUSTOM -> {
                        val seed = st.seedColor
                        val hsvInit = remember(seed) { FloatArray(3).also { android.graphics.Color.colorToHSV(seed, it) } }
                        var hue by remember(seed) { mutableStateOf(hsvInit[0]) }
                        var sat by remember(seed) { mutableStateOf(hsvInit[1]) }
                        var value by remember(seed) { mutableStateOf(hsvInit[2]) }

                        fun persist(
                            h: Float,
                            s: Float,
                            v: Float,
                        ) {
                            app.settings.update { it.copy(seedColor = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
                        }

                        Text(stringResource(R.string.theme_hue), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, onValueChangeFinished = { persist(hue, sat, value) })
                        Text(stringResource(R.string.theme_saturation), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f, onValueChangeFinished = { persist(hue, sat, value) })
                        Text(stringResource(R.string.theme_lightness), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f, onValueChangeFinished = { persist(hue, sat, value) })

                        // 拖动中的即时预览（当前主题真实生效色）
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                            listOf(
                                preview,
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ).forEach { c ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c),
                                )
                            }
                        }

                        // HEX 精确输入：行显示当前值，点开对话框编辑，确定才持久化
                        var editHex by remember(seed) { mutableStateOf(false) }
                        ValueRow(
                            stringResource(R.string.theme_hex),
                            String.format("#%06X", 0xFFFFFF and seed),
                            onClick = { editHex = true },
                        )
                        if (editHex) {
                            HexColorDialog(
                                current = seed,
                                onDismiss = { editHex = false },
                                onConfirm = { rgb ->
                                    val parts = FloatArray(3).also { android.graphics.Color.RGBToHSV((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, it) }
                                    hue = parts[0]
                                    sat = parts[1]
                                    value = parts[2]
                                    persist(hue, sat, value)
                                },
                            )
                        }
                    }
                }
            }
            FormSection(stringResource(R.string.dark_mode)) {
                SegmentedPill(
                    darkModes,
                    st.darkMode,
                    { v -> app.settings.update { it.copy(darkMode = v) } },
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    MintScreen(title = stringResource(R.string.about_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FormSection {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.about_license_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "AGPL-3.0",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 时长读数：整小时 →「N小时」，不足一小时 →「N 分钟」，混合 →「N小时M分」。 */
@Composable
private fun durationLabel(m: Int): String =
    when {
        m % 60 == 0 -> stringResource(R.string.dur_h, m / 60)
        m < 60 -> stringResource(R.string.minutes_n, m)
        else -> stringResource(R.string.dur_h_m, m / 60, m % 60)
    }

/** 分钟数设置：常用预设芯片 + 「自定义」芯片，点自定义弹输入框（范围外不可保存，取消不改值）。 */
@Composable
internal fun MinutesChoiceRow(
    presets: List<Int>,
    current: Int,
    range: IntRange,
    dialogTitle: String,
    zeroHint: String? = null,
    label: @Composable (Int) -> String = { stringResource(R.string.minutes_n, it) },
    onSelect: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val custom = current !in presets
    val customLabel =
        if (custom) {
            stringResource(R.string.detail_custom_value, label(current))
        } else {
            stringResource(R.string.detail_custom)
        }
    val options =
        buildList<Pair<Int?, String>> {
            presets.forEach { m -> add(m to label(m)) }
            add(null to customLabel)
        }
    ChipChoice(options, if (custom) null else current, { v -> if (v == null) editing = true else onSelect(v) })
    if (editing) {
        MinutesDialog(range = range, initial = current, title = dialogTitle, zeroHint = zeroHint, onDismiss = { editing = false }, onConfirm = onSelect)
    }
}

@Composable
private fun MinutesDialog(
    range: IntRange,
    initial: Int,
    title: String,
    zeroHint: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TonalField(
                    label = "",
                    value = text,
                    onChange = { t -> if (t.length <= 6 && t.all { it.isDigit() }) text = t },
                    number = true,
                    isError = !valid,
                    supporting =
                        when {
                            !valid -> stringResource(R.string.detail_minutes_invalid, range.first, range.last)
                            zeroHint != null -> zeroHint
                            else -> null
                        },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(parsed!!)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** HEX 精确输入：合法值实时预览，确定才持久化，取消不改。 */
@Composable
private fun HexColorDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and current)) }
    val parsed =
        Regex("^#?([0-9A-Fa-f]{6})$")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toInt(16)
            ?.or(0xFF000000.toInt())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_hex)) },
        text = {
            Column {
                TonalField(
                    label = "",
                    value = text,
                    onChange = { t ->
                        text = t.filter { it == '#' || it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }.take(7)
                    },
                    isError = parsed == null,
                    supporting = if (parsed == null) stringResource(R.string.theme_hex_invalid) else null,
                )
                Spacer(Modifier.height(12.dp))
                // 未输入合法值时用当前色阶托底预览，不硬编码颜色
                val newPreview = if (parsed != null) Color(parsed) else MaterialTheme.colorScheme.surfaceContainerHighest
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(current)),
                        )
                        Text(stringResource(R.string.theme_hex_current), style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(newPreview),
                        )
                        Text(stringResource(R.string.theme_hex_new), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                onConfirm(parsed!!)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
