package app.mintcal.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.Preset
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
    var exactOk by remember { mutableStateOf(!(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOk = Build.VERSION.SDK_INT < 33 || Notifications.canPost(ctx)
        exactOk = !(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
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
                Text(stringResource(R.string.default_block_minutes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                ChipChoice(listOf(30, 45, 60, 90, 120).map { it to ctx.getString(R.string.minutes_n, it) }, st.defaultBlockMinutes, { v -> app.settings.update { it.copy(defaultBlockMinutes = v) } })
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
                        ChipChoice(listOf(5, 10, 15, 30, 60).map { it to ctx.getString(R.string.remind_before, it) }, st.courseRemindMinutes, { v -> app.settings.update { it.copy(courseRemindMinutes = v) } })
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
            FormSection(stringResource(R.string.section_more)) {
                NavRow(
                    stringResource(R.string.sync_title),
                    stringResource(R.string.sync_row_desc),
                    Icons.Rounded.Cloud,
                ) { nav.push(Route.Sync) }
                NavRow(stringResource(R.string.jxfw_title), stringResource(R.string.jxfw_row_desc), Icons.Rounded.School) { nav.push(Route.Jxfw) }
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
            MintCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.appearance_preview_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            FormSection(stringResource(R.string.theme_color)) {
                ChipChoice(sources, source, { v -> app.settings.update { it.copy(themeSource = v) } })
                if (!dynamicOk) Text(stringResource(R.string.theme_dynamic_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                when (source) {
                    ThemeSource.DYNAMIC -> {
                        Text(stringResource(R.string.theme_dynamic_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    ThemeSource.PRESET -> {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Preset.entries.forEach { p ->
                                val sel = st.preset == p.key
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(p.seed)
                                        .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                        .clickable { app.settings.update { it.copy(preset = p.key) } },
                                )
                            }
                        }
                    }

                    ThemeSource.CUSTOM -> {
                        val hsv = remember(st.seedColor) { FloatArray(3).also { android.graphics.Color.colorToHSV(st.seedColor, it) } }
                        var hue by remember { mutableStateOf(hsv[0]) }
                        Text(stringResource(R.string.theme_hue), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, onValueChangeFinished = {
                            val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.75f))
                            app.settings.update { it.copy(seedColor = c) }
                        })
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.75f)))),
                        )
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
