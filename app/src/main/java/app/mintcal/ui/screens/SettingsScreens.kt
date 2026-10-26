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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.BuildConfig
import app.mintcal.R
import app.mintcal.data.DarkMode
import app.mintcal.data.ThemeSource
import app.mintcal.notify.Notifications
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.MinutesDialSheet
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.TimeDialSheet
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.Preset
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
    // 从系统设置回来后重算权限/闹钟状态，避免提示挂着不消失
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || Notifications.canPost(ctx)) }
    var exactOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) }
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOk = Build.VERSION.SDK_INT < 33 || Notifications.canPost(ctx)
        exactOk = Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    // 开机自启没有公开 API 可检测，只能按厂商尝试专页，失败落到应用详情页
    fun openAutoStart() {
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
    val needExact = !exactOk

    MintScreen(title = stringResource(R.string.settings), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormSection {
                NavRow(stringResource(R.string.appearance), stringResource(R.string.appearance_desc), Icons.Rounded.Palette) { nav.push(Route.Appearance) }
            }
            FormSection(stringResource(R.string.section_display)) {
                SwitchRow(stringResource(R.string.show_teacher), stringResource(R.string.show_teacher_desc), st.showTeacher) { v -> app.settings.update { it.copy(showTeacher = v) } }
                SwitchRow(stringResource(R.string.week_starts_monday), null, st.weekStartsMonday) { v -> app.settings.update { it.copy(weekStartsMonday = v) } }
                SwitchRow(stringResource(R.string.swap_swipe), stringResource(R.string.swap_swipe_desc), st.swapSwipe) { v -> app.settings.update { it.copy(swapSwipe = v) } }
                var blockDial by remember { mutableStateOf(false) }
                ValueRow(stringResource(R.string.default_block_minutes), durationLabel(st.defaultBlockMinutes), onClick = { blockDial = true })
                if (blockDial) {
                    MinutesDialSheet(st.defaultBlockMinutes, onDismiss = { blockDial = false }) { v ->
                        app.settings.update { it.copy(defaultBlockMinutes = v) }
                    }
                }
            }
            FormSection(stringResource(R.string.section_reminders)) {
                if (!notifOk) {
                    Text(stringResource(R.string.notif_blocked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text(stringResource(R.string.notif_grant)) }
                }
                SwitchRow(stringResource(R.string.remind_courses), stringResource(R.string.remind_courses_desc), st.remindCourse) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindCourse = v) }
                }
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
                SwitchRow(stringResource(R.string.remind_tasks), null, st.remindTask) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindTask = v) }
                }
                SwitchRow(stringResource(R.string.remind_events), null, st.remindEvent) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(remindEvent = v) }
                }
                SwitchRow(stringResource(R.string.remind_digest), stringResource(R.string.remind_digest_desc), st.dailyDigest) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(dailyDigest = v) }
                }
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
                SwitchRow(stringResource(R.string.quick_notif_row), stringResource(R.string.quick_notif_desc), st.quickAddNotif) { v ->
                    if (v) ensureNotifPermission()
                    app.settings.update { it.copy(quickAddNotif = v) }
                }
                SwitchRow(stringResource(R.string.auto_mute), stringResource(R.string.auto_mute_desc), st.autoMute) { v ->
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (v && !nm.isNotificationPolicyAccessGranted) {
                        runCatching {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    app.settings.update { it.copy(autoMute = v) }
                }
                AnimatedVisibility(
                    visible = needExact,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Column {
                        Text(stringResource(R.string.exact_alarm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = {
                            runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(stringResource(R.string.exact_alarm_grant)) }
                    }
                }
            }
            FormSection(stringResource(R.string.settings_perm_title)) {
                NavRow(stringResource(R.string.settings_autostart), stringResource(R.string.settings_autostart_desc), Icons.Rounded.RestartAlt) { openAutoStart() }
                NavRow(
                    stringResource(R.string.settings_battery),
                    stringResource(if (batteryOk) R.string.settings_battery_ok else R.string.settings_battery_bad),
                    Icons.Rounded.BatterySaver,
                ) {
                    if (!batteryOk) {
                        runCatching {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            FormSection(stringResource(R.string.section_more)) {
                NavRow(
                    stringResource(R.string.sync_title),
                    stringResource(R.string.sync_row_desc),
                    Icons.Rounded.Cloud,
                ) { nav.push(Route.Sync) }
                NavRow(stringResource(R.string.jxfw_title), stringResource(R.string.jxfw_row_desc), Icons.Rounded.School) { nav.push(Route.Jxfw) }
                NavRow(stringResource(R.string.reliability_title), stringResource(R.string.reliability_row_desc), Icons.Rounded.NotificationsActive) { nav.push(Route.Reliability) }
                NavRow(stringResource(R.string.ai_settings), stringResource(R.string.ai_settings_desc), Icons.Rounded.AutoAwesome) { nav.push(Route.AiSettings) }
                NavRow(stringResource(R.string.data_title), stringResource(R.string.data_desc), Icons.Rounded.SwapVert) { nav.push(Route.Data) }
                NavRow(stringResource(R.string.parcel_title), stringResource(R.string.parcel_row_desc), Icons.Rounded.LocalShipping) { nav.push(Route.Parcel) }
                NavRow(stringResource(R.string.history_title), null, Icons.Rounded.History) { nav.push(Route.History) }
                NavRow(stringResource(R.string.trash_title), stringResource(R.string.trash_row_desc), Icons.Rounded.DeleteSweep) { nav.push(Route.Trash) }
                NavRow(stringResource(R.string.about_title), null, Icons.Rounded.Info) { nav.push(Route.About) }
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
        buildList {
            if (dynamicOk) add(ThemeSource.DYNAMIC to ctx.getString(R.string.theme_dynamic))
            add(ThemeSource.PRESET to ctx.getString(R.string.theme_preset))
            add(ThemeSource.CUSTOM to ctx.getString(R.string.theme_custom))
        }
    val source = if (!dynamicOk && st.themeSource == ThemeSource.DYNAMIC) ThemeSource.PRESET else st.themeSource

    MintScreen(title = stringResource(R.string.appearance), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormSection(stringResource(R.string.theme_color)) {
                ChipChoice(sources, source, { v -> app.settings.update { it.copy(themeSource = v) } })
                if (!dynamicOk) Text(stringResource(R.string.theme_dynamic_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                when (source) {
                    ThemeSource.DYNAMIC -> {
                        Text(stringResource(R.string.theme_dynamic_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    ThemeSource.PRESET -> {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Preset.entries.forEach { p ->
                                val sel = st.preset == p.key
                                val name = stringResource(p.label)
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
                                            .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                            .semantics {
                                                contentDescription = name
                                                role = Role.RadioButton
                                                selected = sel
                                            }.clickable { app.settings.update { it.copy(preset = p.key) } },
                                    )
                                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                ChipChoice(
                    listOf(DarkMode.SYSTEM to ctx.getString(R.string.dark_system), DarkMode.LIGHT to ctx.getString(R.string.dark_light), DarkMode.DARK to ctx.getString(R.string.dark_dark)),
                    st.darkMode,
                    { v -> app.settings.update { it.copy(darkMode = v) } },
                )
            }
        }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val st = settingsState()
    var baseUrl by remember { mutableStateOf(st.aiBaseUrl) }
    var model by remember { mutableStateOf(st.aiModel) }
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(app.secrets.hasAiKey()) }

    MintScreen(title = stringResource(R.string.ai_settings), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.ai_settings_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FieldText(stringResource(R.string.ai_base_url), baseUrl, { baseUrl = it })
            FieldText(stringResource(R.string.ai_model), model, { model = it })
            androidx.compose.material3.OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.ai_key)) },
                placeholder = { Text(if (hasKey) stringResource(R.string.ai_key_saved) else stringResource(R.string.ai_key_none)) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(18.dp),
            )
            Text(stringResource(R.string.ai_key_storage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    app.settings.update { it.copy(aiBaseUrl = baseUrl.trim().trimEnd('/'), aiModel = model.trim()) }
                    if (key.isNotBlank()) {
                        app.secrets.setAiKey(key.trim())
                        key = ""
                        hasKey = true
                    }
                    actions.toast(ctx.getString(R.string.saved_toast))
                }) { Text(stringResource(R.string.save)) }
                if (hasKey) {
                    OutlinedButton(onClick = {
                        app.secrets.setAiKey("")
                        hasKey = false
                    }) { Text(stringResource(R.string.ai_key_clear)) }
                }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MintCard {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
            }
            MintCard {
                Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { m ->
            FilterChip(
                selected = current == m,
                onClick = { onSelect(m) },
                label = { Text(label(m)) },
                shape = CircleShape,
            )
        }
        FilterChip(
            selected = custom,
            onClick = { editing = true },
            label = { Text(if (custom) stringResource(R.string.detail_custom_value, label(current)) else stringResource(R.string.detail_custom)) },
            shape = CircleShape,
        )
    }
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> if (t.length <= 6 && t.all { it.isDigit() }) text = t },
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        when {
                            !valid -> Text(stringResource(R.string.detail_minutes_invalid, range.first, range.last))
                            zeroHint != null -> Text(zeroHint)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(18.dp),
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        text = t.filter { it == '#' || it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }.take(7)
                    },
                    singleLine = true,
                    isError = parsed == null,
                    supportingText =
                        if (parsed == null) {
                            { Text(stringResource(R.string.theme_hex_invalid)) }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.height(12.dp))
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
                                .background(Color(parsed ?: 0xFF888888.toInt())),
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
