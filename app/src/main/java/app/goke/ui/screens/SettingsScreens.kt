package app.goke.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.goke.BuildConfig
import app.goke.R
import app.goke.core.jxfw.JxfwParse
import app.goke.core.jxfw.TzcsParse
import app.goke.data.DarkMode
import app.goke.data.DownloadError
import app.goke.data.InstallLaunchResult
import app.goke.data.ReleaseInfo
import app.goke.data.ThemeSource
import app.goke.data.UpdateCheckResult
import app.goke.data.UpdateChecker
import app.goke.data.UpdateDownloadState
import app.goke.data.UpdateInterval
import app.goke.notify.Notifications
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.LocalTabVisible
import app.goke.ui.Route
import app.goke.ui.Tab
import app.goke.ui.components.ChipChoice
import app.goke.ui.components.FormSection
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.MinutesDialSheet
import app.goke.ui.components.NavListInset
import app.goke.ui.components.NavRow
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.SwitchRow
import app.goke.ui.components.Tag
import app.goke.ui.components.TimeDialSheet
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Alarm
import app.goke.ui.icons.BatterySaver
import app.goke.ui.icons.Cloud
import app.goke.ui.icons.DeleteSweep
import app.goke.ui.icons.FitnessCenter
import app.goke.ui.icons.Grade
import app.goke.ui.icons.History
import app.goke.ui.icons.IosShare
import app.goke.ui.icons.LaptopMac
import app.goke.ui.icons.LocalShipping
import app.goke.ui.icons.MeetingRoom
import app.goke.ui.icons.NotificationsActive
import app.goke.ui.icons.Palette
import app.goke.ui.icons.RestartAlt
import app.goke.ui.icons.School
import app.goke.ui.icons.SwapVert
import app.goke.ui.icons.Terminal
import app.goke.ui.icons.Widgets
import app.goke.ui.liveSnapshot
import app.goke.ui.reduceMotion
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import app.goke.ui.theme.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale
import android.provider.Settings as AndroidSettings

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val st = settingsState()
    val reduce = reduceMotion()
    val listState = rememberLazyListState()
    val tabVisibleState = LocalTabVisible.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx)) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // 权限入口行与桌面小组件行需要实时状态：切到本 Tab 或从子页/系统设置回来后在异步协程中重算
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
    LaunchedEffect(tabVisibleState) {
        snapshotFlow { tabVisibleState.value }.collect { visible ->
            if (visible) refreshStatus()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (tabVisibleState.value) refreshStatus()
    }
    LaunchedEffect(nav.reselect, reduce) {
        if (nav.reselect > 0 && nav.tab == Tab.ME) {
            if (reduce) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = NavListInset),
        ) {
            item(key = "header", contentType = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.tab_me),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item(key = "campus_hub_card", contentType = "campus_card") {
                CampusHubCard { nav.push(Route.Jxfw) }
            }
            item(key = "campus_section", contentType = "form_section") {
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
                FormSection(stringResource(R.string.me_hub_campus_section)) {
                    GradesNavRow { nav.push(Route.Grades) }
                    PhysicalTestNavRow { nav.push(Route.PhysicalTest) }
                    NavRow(
                        stringResource(R.string.classroom_title),
                        null,
                        Icons.Rounded.MeetingRoom,
                    ) { nav.push(Route.Classroom) }
                    NavRow(
                        stringResource(R.string.parcel_title),
                        null,
                        Icons.Rounded.LocalShipping,
                        trailing = stringResource(if (st.parcelEnabled) R.string.parcel_status_on else R.string.parcel_status_off),
                    ) { nav.push(Route.Parcel) }
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
                    NavRow(stringResource(R.string.appearance), null, Icons.Rounded.Palette, trailing = sourceLabel) { nav.push(Route.Appearance) }
                }
            }
            item(key = "sync_data_section", contentType = "form_section") {
                FormSection(stringResource(R.string.me_hub_sync_section)) {
                    SyncNavRow { nav.push(Route.Sync) }
                    NavRow(stringResource(R.string.data_title), null, Icons.Rounded.SwapVert) { nav.push(Route.Data) }
                    TrashNavRow { nav.push(Route.Trash) }
                    NavRow(stringResource(R.string.history_title), null, Icons.Rounded.History) { nav.push(Route.History) }
                }
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
            item(key = "more_reliability", contentType = "nav_row") {
                ReliabilityNavRow(permOkCount = { permOkCount }) { nav.push(Route.Reliability) }
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
            item(key = "group_exp_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_exp), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_relay", contentType = "nav_row") {
                RelayNavRow { nav.push(Route.Relay) }
            }
            item(key = "group_about_header", contentType = "section_label") {
                SectionLabel(stringResource(R.string.settings_group_about), modifier = Modifier.padding(top = 24.dp))
            }
            item(key = "more_about", contentType = "nav_row") {
                val hasPendingUpdate =
                    st.pendingUpdateVersion.isNotBlank() &&
                        app.updateChecker.isNewerThanCurrent(st.pendingUpdateVersion)
                NavRow(
                    stringResource(R.string.about_title),
                    null,
                    Icons.Rounded.Info,
                    trailing =
                        if (hasPendingUpdate) {
                            stringResource(R.string.update_new_badge_with_version, st.pendingUpdateVersion)
                        } else {
                            null
                        },
                ) { nav.push(Route.About) }
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
private fun CampusHubCard(onOpen: () -> Unit) {
    val app = LocalApp.current
    val jxfwState by app.jxfw.state.collectAsState()
    val snap = liveSnapshot()
    val studentId = jxfwState.username?.takeIf { it.isNotBlank() }
    val semesterName = snap.currentSemester?.name?.takeIf { it.isNotBlank() }
    val scheme = MaterialTheme.colorScheme

    MintCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.School,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (studentId != null) {
                        stringResource(R.string.me_hub_bound_id, studentId)
                    } else {
                        stringResource(R.string.me_hub_bind_jxfw)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    when {
                        studentId != null && semesterName != null -> semesterName
                        studentId == null -> stringResource(R.string.me_hub_bind_jxfw_sub)
                        else -> null
                    }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradesNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val gradesState by app.jxfw.gradesState.collectAsState()
    val summary =
        remember(gradesState.grades) {
            if (gradesState.grades.isNotEmpty()) JxfwParse.summarizeGrades(gradesState.grades) else null
        }
    val trailing =
        summary?.weightedGpa?.let {
            stringResource(R.string.grades_nav_gpa, String.format(Locale.US, "%.2f", it))
        }
    NavRow(
        stringResource(R.string.grades_title),
        null,
        Icons.Rounded.Grade,
        trailing = trailing,
        onClick = onOpen,
    )
}

@Composable
private fun PhysicalTestNavRow(onOpen: () -> Unit) {
    val app = LocalApp.current
    val tzcsState by app.tzcs.state.collectAsState()
    val latest = tzcsState.latestSummaryReport
    val trailing =
        if (latest != null && TzcsParse.hasRecordedScores(latest) && latest.totalScore.isNotBlank()) {
            stringResource(R.string.tzcs_score_value, latest.totalScore)
        } else {
            null
        }
    NavRow(
        stringResource(R.string.tzcs_title),
        null,
        Icons.Rounded.FitnessCenter,
        trailing = trailing,
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
private fun RelayNavRow(onOpen: () -> Unit) {
    NavRow(
        stringResource(R.string.relay_title),
        stringResource(R.string.relay_nav_desc),
        Icons.Rounded.IosShare,
        trailing = stringResource(R.string.relay_exp_badge),
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
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            presetItems.chunked(3).forEach { rowItems ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowItems.forEach { (p, name) ->
                                        val sel = st.preset == p.key
                                        val checkTint = if (p.seed.luminance() > 0.5f) onSurfaceColor else Color.White
                                        Column(
                                            Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(52.dp)
                                                    .then(if (sel) Modifier.border(2.dp, p.seed, CircleShape) else Modifier)
                                                    .padding(4.dp)
                                                    .clip(CircleShape)
                                                    .background(p.seed)
                                                    .semantics {
                                                        contentDescription = name
                                                        role = Role.RadioButton
                                                        selected = sel
                                                    }.mintClickable(pressedScale = 0.96f) { app.settings.update { it.copy(preset = p.key) } },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = checkTint)
                                            }
                                            Text(name, style = labelSmallStyle, color = onSurfaceVariantColor, maxLines = 1)
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    ThemeSource.CUSTOM -> {
                        val seed = st.seedColor
                        val hsvInit = remember(seed) { FloatArray(3).also { android.graphics.Color.colorToHSV(seed, it) } }
                        val hueState = remember(seed) { SliderState(value = hsvInit[0], trackRange = 0f..360f) }
                        val satState = remember(seed) { SliderState(value = hsvInit[1], trackRange = 0f..1f) }
                        val valueState = remember(seed) { SliderState(value = hsvInit[2], trackRange = 0f..1f) }

                        fun persist(
                            h: Float,
                            s: Float,
                            v: Float,
                        ) {
                            app.settings.update { it.copy(seedColor = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
                        }

                        Text(stringResource(R.string.theme_hue), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            state = hueState,
                            onValueChange = { hueState.value = it },
                            onValueChangeFinished = { persist(hueState.value, satState.value, valueState.value) },
                        )
                        Text(stringResource(R.string.theme_saturation), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            state = satState,
                            onValueChange = { satState.value = it },
                            onValueChangeFinished = { persist(hueState.value, satState.value, valueState.value) },
                        )
                        Text(stringResource(R.string.theme_lightness), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            state = valueState,
                            onValueChange = { valueState.value = it },
                            onValueChangeFinished = { persist(hueState.value, satState.value, valueState.value) },
                        )

                        // 拖动中的即时预览（当前主题真实生效色）
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hueState.value, satState.value, valueState.value)))
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
                                    hueState.value = parts[0]
                                    satState.value = parts[1]
                                    valueState.value = parts[2]
                                    persist(hueState.value, satState.value, valueState.value)
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
    val app = LocalApp.current
    val ctx = LocalContext.current
    val st = settingsState()
    val scope = rememberCoroutineScope()
    val checking by app.updateChecker.checking.collectAsState()
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var dialogInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    val pendingInfo =
        remember(
            st.pendingUpdateVersion,
            st.pendingUpdateUrl,
            st.pendingUpdateNotes,
            st.pendingUpdateApkUrl,
            st.pendingUpdateApkSize,
            st.pendingUpdateApkSha256,
        ) {
            if (st.pendingUpdateVersion.isNotBlank() &&
                app.updateChecker.isNewerThanCurrent(st.pendingUpdateVersion)
            ) {
                ReleaseInfo(
                    version = st.pendingUpdateVersion,
                    url = st.pendingUpdateUrl.ifBlank { UpdateChecker.LANDING_URL },
                    notes = st.pendingUpdateNotes,
                    apkUrl = st.pendingUpdateApkUrl,
                    apkSize = st.pendingUpdateApkSize,
                    apkSha256 = st.pendingUpdateApkSha256,
                )
            } else {
                null
            }
        }

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
                        painter = painterResource(R.drawable.ic_goke_mark),
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
            FormSection(title = stringResource(R.string.update_section_title)) {
                AnimatedVisibility(
                    visible = pendingInfo != null,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    pendingInfo?.let { info ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .mintClickable(pressedScale = 0.98f, role = Role.Button) {
                                    dialogInfo = info
                                }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.update_new_version_title, info.version),
                                Modifier.weight(1f).padding(end = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Tag(
                                stringResource(R.string.update_new_badge),
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .mintClickable(enabled = !checking, pressedScale = 0.98f, role = Role.Button) {
                            scope.launch {
                                when (val res = app.updateChecker.checkNow()) {
                                    is UpdateCheckResult.Newer -> {
                                        dialogInfo = res.info
                                    }

                                    UpdateCheckResult.UpToDate -> {
                                        Toast.makeText(ctx, R.string.update_uptodate, Toast.LENGTH_SHORT).show()
                                    }

                                    UpdateCheckResult.Error -> {
                                        Toast.makeText(ctx, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                                    }

                                    UpdateCheckResult.Idle -> {}
                                }
                            }
                        }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.update_check),
                        Modifier.weight(1f).padding(end = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NavRow(
                    title = stringResource(R.string.update_auto_check),
                    trailing = stringResource(st.updateInterval.labelRes()),
                    onClick = { showIntervalDialog = true },
                )
                NavRow(
                    title = stringResource(R.string.update_project_home),
                    onClick = { openExternalUrl(ctx, UpdateChecker.PROJECT_URL) },
                )
            }
            FormSection(title = stringResource(R.string.about_license_section)) {
                NavRow(
                    title = stringResource(R.string.about_license_title),
                    trailing = "AGPL-3.0",
                    onClick = { showLicenseDialog = true },
                )
            }
            FormSection(title = stringResource(R.string.about_ack_section)) {
                ACKNOWLEDGMENTS.forEach { ack ->
                    NavRow(
                        title = stringResource(ack.nameRes),
                        subtitle = stringResource(ack.descRes),
                        onClick = { openExternalUrl(ctx, ack.url) },
                    )
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.about_ack_community_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.about_ack_community_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showLicenseDialog) {
        OssLicensesDialog(onDismiss = { showLicenseDialog = false })
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.update_auto_check_dialog_title)) },
            text = {
                Column {
                    UpdateInterval.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .mintClickable(pressedScale = 0.98f, role = Role.RadioButton) {
                                    app.settings.update { it.copy(updateInterval = option) }
                                    showIntervalDialog = false
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = option == st.updateInterval, onClick = null)
                            Text(stringResource(option.labelRes()))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    dialogInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            updateChecker = app.updateChecker,
            downloadScope = app.scope,
            onDismiss = {
                app.updateChecker.dismissUpdatePrompt(info.version)
                dialogInfo = null
            },
            onOpenUrl = { url -> openExternalUrl(ctx, url) },
        )
    }
}

private fun UpdateInterval.labelRes(): Int =
    when (this) {
        UpdateInterval.STARTUP -> R.string.update_freq_startup
        UpdateInterval.DAILY -> R.string.update_freq_daily
        UpdateInterval.WEEKLY -> R.string.update_freq_weekly
        UpdateInterval.OFF -> R.string.update_freq_off
    }

private data class AckEntry(
    val nameRes: Int,
    val descRes: Int,
    val url: String,
)

private val ACKNOWLEDGMENTS =
    listOf(
        AckEntry(
            nameRes = R.string.lic_jetpack_name,
            descRes = R.string.about_ack_jetpack_desc,
            url = "https://developer.android.com/compose",
        ),
        AckEntry(
            nameRes = R.string.lic_kotlin_name,
            descRes = R.string.about_ack_kotlin_desc,
            url = "https://github.com/JetBrains/kotlin",
        ),
        AckEntry(
            nameRes = R.string.lic_kolor_name,
            descRes = R.string.about_ack_kolor_desc,
            url = "https://github.com/jordond/material-kolor",
        ),
        AckEntry(
            nameRes = R.string.lic_quickjs_name,
            descRes = R.string.about_ack_quickjs_desc,
            url = "https://github.com/dokar3/quickjs-kt",
        ),
    )

private data class OssLicenseEntry(
    val nameRes: Int,
    val license: String,
    val url: String,
    val textRes: Int,
)

private val OSS_LICENSES =
    listOf(
        OssLicenseEntry(
            nameRes = R.string.lic_app_name,
            license = "AGPL-3.0",
            url = "github.com/xihale/goke",
            textRes = R.raw.license_agpl_3,
        ),
        OssLicenseEntry(
            nameRes = R.string.lic_jetpack_name,
            license = "Apache-2.0",
            url = "developer.android.com/jetpack",
            textRes = R.raw.license_apache_2,
        ),
        OssLicenseEntry(
            nameRes = R.string.lic_kotlin_name,
            license = "Apache-2.0",
            url = "github.com/JetBrains/kotlin",
            textRes = R.raw.license_apache_2,
        ),
        OssLicenseEntry(
            nameRes = R.string.lic_kolor_name,
            license = "MIT",
            url = "github.com/jordond/material-kolor",
            textRes = R.raw.license_mit,
        ),
        OssLicenseEntry(
            nameRes = R.string.lic_quickjs_name,
            license = "MIT",
            url = "github.com/dokar3/quickjs-kt",
            textRes = R.raw.license_mit,
        ),
    )

@Composable
private fun OssLicensesDialog(onDismiss: () -> Unit) {
    var viewing by remember { mutableStateOf<OssLicenseEntry?>(null) }
    val entry = viewing
    if (entry == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.about_license_title)) },
            text = {
                Column {
                    OSS_LICENSES.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .mintClickable(pressedScale = 0.98f, role = Role.Button) {
                                    viewing = item
                                }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(stringResource(item.nameRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${item.license} · ${item.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            },
        )
    } else {
        LicenseTextDialog(entry = entry, onBack = { viewing = null })
    }
}

@Composable
private fun LicenseTextDialog(
    entry: OssLicenseEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val text =
        remember(entry.textRes) {
            context.resources
                .openRawResource(entry.textRes)
                .bufferedReader()
                .use { it.readText() }
        }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Column {
                Text(stringResource(entry.nameRes))
                Text(
                    entry.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        },
    )
}

@Composable
internal fun UpdateAvailableDialog(
    info: ReleaseInfo,
    updateChecker: UpdateChecker,
    downloadScope: CoroutineScope,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val dlState by updateChecker.downloadState.collectAsState()
    var waitingInstallPermission by remember(info.version) { mutableStateOf(false) }

    fun triggerDownload() {
        if (info.apkUrl.isBlank()) {
            onOpenUrl(info.url.ifBlank { UpdateChecker.LANDING_URL })
            onDismiss()
            return
        }
        downloadScope.launch {
            when (updateChecker.downloadAndInstall(ctx.applicationContext, info)) {
                InstallLaunchResult.LAUNCHED -> onDismiss()
                InstallLaunchResult.NEEDS_PERMISSION -> waitingInstallPermission = true
                InstallLaunchResult.FAILED -> Unit
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!waitingInstallPermission) return@LifecycleEventEffect
        waitingInstallPermission = false
        val st = updateChecker.downloadState.value
        if (st is UpdateDownloadState.ReadyToInstall &&
            st.version == info.version &&
            ctx.packageManager.canRequestPackageInstalls()
        ) {
            when (updateChecker.launchSystemInstaller(ctx.applicationContext, info.version, st.file)) {
                InstallLaunchResult.LAUNCHED -> {
                    onDismiss()
                }

                InstallLaunchResult.FAILED -> {
                    Toast.makeText(ctx, R.string.update_install_launch_failed, Toast.LENGTH_SHORT).show()
                }

                InstallLaunchResult.NEEDS_PERMISSION -> {}
            }
        }
    }

    val stateForVersion =
        when (val s = dlState) {
            is UpdateDownloadState.Downloading if s.version == info.version -> s
            is UpdateDownloadState.ReadyToInstall if s.version == info.version -> s
            is UpdateDownloadState.Failed if s.version == info.version -> s
            else -> UpdateDownloadState.Idle
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_new_version_title, info.version)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    info.notes?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.update_new_version_body),
                )
                if (info.apkSize > 0L) {
                    Text(
                        text = stringResource(R.string.update_apk_size_hint, formatMiB(info.apkSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (stateForVersion) {
                    is UpdateDownloadState.Downloading -> {
                        if (stateForVersion.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (stateForVersion.bytesRead.toFloat() / stateForVersion.totalBytes)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text =
                                    stringResource(
                                        R.string.update_download_progress,
                                        formatMiB(stateForVersion.bytesRead),
                                        formatMiB(stateForVersion.totalBytes),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = formatMiB(stateForVersion.bytesRead),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        if (stateForVersion.needsInstallPermission && !ctx.packageManager.canRequestPackageInstalls()) {
                            Text(
                                text = stringResource(R.string.update_install_permission_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    is UpdateDownloadState.Failed -> {
                        val errRes =
                            if (stateForVersion.error == DownloadError.VERIFY) {
                                R.string.update_verify_failed
                            } else {
                                R.string.update_download_failed
                            }
                        Text(
                            text = stringResource(errRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    UpdateDownloadState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (stateForVersion) {
                is UpdateDownloadState.Downloading -> {
                    TextButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.update_downloading))
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    TextButton(onClick = {
                        when (updateChecker.launchSystemInstaller(ctx.applicationContext, info.version, stateForVersion.file)) {
                            InstallLaunchResult.LAUNCHED -> {
                                onDismiss()
                            }

                            InstallLaunchResult.NEEDS_PERMISSION -> {
                                waitingInstallPermission = true
                            }

                            InstallLaunchResult.FAILED -> {
                                Toast.makeText(ctx, R.string.update_install_launch_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.update_install_now))
                    }
                }

                is UpdateDownloadState.Failed -> {
                    TextButton(onClick = { triggerDownload() }) {
                        Text(stringResource(R.string.update_install_retry))
                    }
                }

                UpdateDownloadState.Idle -> {
                    TextButton(onClick = { triggerDownload() }) {
                        Text(stringResource(R.string.update_go))
                    }
                }
            }
        },
        dismissButton = {
            if (stateForVersion is UpdateDownloadState.Failed) {
                TextButton(onClick = {
                    onOpenUrl(info.url.ifBlank { UpdateChecker.LANDING_URL })
                    onDismiss()
                }) {
                    Text(stringResource(R.string.update_open_landing))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        },
    )
}

private fun formatMiB(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.2f MiB", mib)
}

internal fun openExternalUrl(
    context: Context,
    url: String,
) {
    val opened =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    if (!opened) {
        Toast.makeText(context, R.string.update_open_failed, Toast.LENGTH_SHORT).show()
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
