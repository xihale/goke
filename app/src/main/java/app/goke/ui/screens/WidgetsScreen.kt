package app.goke.ui.screens

import android.app.AppOpsManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.CourseItem
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.todo.SmartList
import app.goke.core.todo.TaskLogic
import app.goke.data.Settings
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.Tag
import app.goke.ui.components.mintClickable
import app.goke.ui.dueLabel
import app.goke.ui.hm
import app.goke.ui.liveSnapshot
import app.goke.ui.monthDayWeekday
import app.goke.ui.rememberNow
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import app.goke.widget.NextWidgetProvider
import app.goke.widget.ParcelWidgetProvider
import app.goke.widget.ScheduleWidgetProvider
import app.goke.widget.TodayWidgetProvider
import app.goke.widget.TodoWidgetProvider
import app.goke.widget.WidgetKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import android.provider.Settings as AndroidSettings

private const val ACTION_WIDGET_PINNED = "app.goke.widget.PINNED"
private const val EXTRA_WIDGET_NAME = "widget_name"

internal enum class WidgetSpec(
    val labelRes: Int,
    val descRes: Int,
    val sizeLabel: String,
    val providerClass: Class<out AppWidgetProvider>,
) {
    TODAY(
        labelRes = R.string.widget_today_name,
        descRes = R.string.widget_today_desc,
        sizeLabel = "4×3",
        providerClass = TodayWidgetProvider::class.java,
    ),
    SCHEDULE(
        labelRes = R.string.widget_schedule_name,
        descRes = R.string.widget_schedule_desc,
        sizeLabel = "4×3",
        providerClass = ScheduleWidgetProvider::class.java,
    ),
    TODO(
        labelRes = R.string.widget_todo_name,
        descRes = R.string.widget_todo_desc,
        sizeLabel = "3×3",
        providerClass = TodoWidgetProvider::class.java,
    ),
    PARCEL(
        labelRes = R.string.widget_parcel_name,
        descRes = R.string.widget_parcel_desc,
        sizeLabel = "3×2",
        providerClass = ParcelWidgetProvider::class.java,
    ),
    NEXT(
        labelRes = R.string.widget_next_name,
        descRes = R.string.widget_next_desc,
        sizeLabel = "4×1",
        providerClass = NextWidgetProvider::class.java,
    ),
}

internal enum class WidgetPinOutcome {
    REQUESTED,
    UNSUPPORTED,
}

private enum class PinFallbackReason {
    UNSUPPORTED,
    SILENTLY_BLOCKED,
}

private data class PendingPin(
    val spec: WidgetSpec,
    val beforeCount: Int,
    val seq: Int,
)

internal fun addedWidgetCount(
    ctx: Context,
    spec: WidgetSpec,
): Int =
    runCatching {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return 0
        mgr.getAppWidgetIds(ComponentName(ctx, spec.providerClass)).size
    }.getOrDefault(0)

internal fun totalAddedWidgets(ctx: Context): Int = WidgetSpec.entries.sumOf { addedWidgetCount(ctx, it) }

internal fun requestPinWidget(
    ctx: Context,
    spec: WidgetSpec,
    successCallback: PendingIntent? = null,
): WidgetPinOutcome {
    val mgr = AppWidgetManager.getInstance(ctx) ?: return WidgetPinOutcome.UNSUPPORTED
    if (!mgr.isRequestPinAppWidgetSupported) return WidgetPinOutcome.UNSUPPORTED
    val provider = ComponentName(ctx, spec.providerClass)
    // 不传 EXTRA_APPWIDGET_PREVIEW：部分系统桌面在跨进程反序列化含 RemoteAdapter / PendingIntent 的预览时会静默丢弃请求
    val accepted =
        runCatching {
            mgr.requestPinAppWidget(provider, null, successCallback)
        }.getOrDefault(false)
    return if (accepted) WidgetPinOutcome.REQUESTED else WidgetPinOutcome.UNSUPPORTED
}

/**
 * 提前检测小米 / HyperOS 的「桌面快捷方式」权限（AppOps 10017）。
 * 返回 true = 已允许，false = 明确未授权，null = 非小米机型或系统限制无法读取。
 */
internal fun checkShortcutPermission(ctx: Context): Boolean? {
    val mfr = Build.MANUFACTURER.lowercase()
    if (mfr !in listOf("xiaomi", "redmi", "blackshark", "poco")) return null
    return runCatching {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val method =
            AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
        val mode = method.invoke(appOps, 10017, Process.myUid(), ctx.packageName) as Int
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrNull()
}

internal fun openWidgetPermissionSettings(ctx: Context) {
    val candidates =
        listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", ctx.packageName),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", ctx.packageName),
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${ctx.packageName}"),
            ),
        )
    for (intent in candidates) {
        val ok = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
}

@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val snap = liveSnapshot()
    val st = settingsState()
    val now by rememberNow()
    val scope = rememberCoroutineScope()

    var counts by remember { mutableStateOf<Map<WidgetSpec, Int>>(emptyMap()) }
    var shortcutPermGranted by remember { mutableStateOf<Boolean?>(null) }
    var fallbackReason by remember { mutableStateOf<PinFallbackReason?>(null) }
    var pendingPin by remember { mutableStateOf<PendingPin?>(null) }
    var pinSeq by remember { mutableStateOf(0) }
    var focusLostAtMs by remember { mutableStateOf(0L) }
    var unfocusedDurationMs by remember { mutableStateOf(0L) }
    var hadFocusBounce by remember { mutableStateOf(false) }
    var pinConfirmed by remember { mutableStateOf(false) }

    fun refreshStatus() {
        scope.launch {
            val (nextCounts, perm) =
                withContext(Dispatchers.IO) {
                    WidgetSpec.entries.associateWith { addedWidgetCount(ctx, it) } to checkShortcutPermission(ctx)
                }
            counts = nextCounts
            shortcutPermGranted = perm
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshStatus() }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            if (focusLostAtMs == 0L) focusLostAtMs = SystemClock.elapsedRealtime()
        } else {
            if (focusLostAtMs > 0L) {
                unfocusedDurationMs += SystemClock.elapsedRealtime() - focusLostAtMs
                focusLostAtMs = 0L
                hadFocusBounce = true
            }
            refreshStatus()
        }
    }

    DisposableEffect(ctx) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val name = intent?.getStringExtra(EXTRA_WIDGET_NAME).orEmpty()
                    pinConfirmed = true
                    pendingPin = null
                    refreshStatus()
                    if (name.isNotEmpty()) {
                        Toast.makeText(ctx, ctx.getString(R.string.widgets_pin_confirmed_toast, name), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(ACTION_WIDGET_PINNED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    // 兜底检测：无法提前探测权限的机型上，若焦点未离开或闪退回应用（<250ms），快速弹出权限引导
    LaunchedEffect(pendingPin, windowInfo.isWindowFocused) {
        val pending = pendingPin ?: return@LaunchedEffect
        if (!windowInfo.isWindowFocused) return@LaunchedEffect
        delay(if (hadFocusBounce) 80L else 280L)
        if (pendingPin?.seq != pending.seq || !windowInfo.isWindowFocused) return@LaunchedEffect
        val afterCount = withContext(Dispatchers.IO) { addedWidgetCount(ctx, pending.spec) }
        if (afterCount > pending.beforeCount && !pinConfirmed) {
            refreshStatus()
            val msg = ctx.getString(R.string.widgets_pin_confirmed_toast, ctx.getString(pending.spec.labelRes))
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        } else if (!pinConfirmed && unfocusedDurationMs < 250L) {
            fallbackReason = PinFallbackReason.SILENTLY_BLOCKED
        }
        pendingPin = null
    }

    fun pin(spec: WidgetSpec) {
        if (shortcutPermGranted == false) {
            Toast.makeText(ctx, ctx.getString(R.string.widgets_perm_toast), Toast.LENGTH_SHORT).show()
            openWidgetPermissionSettings(ctx)
            return
        }
        val label = ctx.getString(spec.labelRes)
        val beforeCount = counts[spec] ?: addedWidgetCount(ctx, spec)
        val callback =
            PendingIntent.getBroadcast(
                ctx,
                600 + spec.ordinal,
                Intent(ACTION_WIDGET_PINNED).setPackage(ctx.packageName).putExtra(EXTRA_WIDGET_NAME, label),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (requestPinWidget(ctx, spec, callback) == WidgetPinOutcome.UNSUPPORTED) {
            fallbackReason = PinFallbackReason.UNSUPPORTED
            return
        }
        focusLostAtMs = 0L
        unfocusedDurationMs = 0L
        hadFocusBounce = false
        pinConfirmed = false
        val nextSeq = pinSeq + 1
        pinSeq = nextSeq
        pendingPin = PendingPin(spec, beforeCount, nextSeq)
    }

    MintScreen(title = stringResource(R.string.widgets_title), onBack = onBack) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (shortcutPermGranted == false) {
                item(key = "perm_banner") {
                    MintCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.widgets_perm_needed_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.widgets_perm_needed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { openWidgetPermissionSettings(ctx) }) {
                                Text(stringResource(R.string.widgets_perm_go_enable))
                            }
                        }
                    }
                }
            }
            items(WidgetSpec.entries, key = { it.name }) { spec ->
                WidgetItemBlock(
                    spec = spec,
                    addedCount = counts[spec] ?: 0,
                    snap = snap,
                    st = st,
                    now = now,
                    onPin = { pin(spec) },
                )
            }
        }
    }

    when (fallbackReason) {
        PinFallbackReason.UNSUPPORTED -> {
            AlertDialog(
                onDismissRequest = { fallbackReason = null },
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text(stringResource(R.string.widgets_unsupported_title)) },
                text = { Text(stringResource(R.string.widgets_unsupported_desc)) },
                dismissButton = {
                    TextButton(onClick = { fallbackReason = null }) { Text(stringResource(R.string.ok)) }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            fallbackReason = null
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_HOME)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.widgets_go_home))
                    }
                },
            )
        }

        PinFallbackReason.SILENTLY_BLOCKED -> {
            AlertDialog(
                onDismissRequest = { fallbackReason = null },
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text(stringResource(R.string.widgets_blocked_title)) },
                text = { Text(stringResource(R.string.widgets_blocked_desc)) },
                dismissButton = {
                    TextButton(onClick = { fallbackReason = null }) { Text(stringResource(R.string.ok)) }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            fallbackReason = null
                            openWidgetPermissionSettings(ctx)
                        },
                    ) {
                        Text(stringResource(R.string.widgets_open_app_settings))
                    }
                },
            )
        }

        null -> {}
    }
}

@Composable
private fun WidgetItemBlock(
    spec: WidgetSpec,
    addedCount: Int,
    snap: Snapshot,
    st: Settings,
    now: LocalDateTime,
    onPin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val name = stringResource(spec.labelRes)
    val a11yAdd = stringResource(R.string.widgets_add_to_home_a11y, name)
    val border = BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.8f))

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(name, style = typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Tag(spec.sizeLabel, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                    if (addedCount > 0) {
                        Tag(
                            stringResource(R.string.widgets_added_badge, addedCount),
                            scheme.primaryContainer,
                            scheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    stringResource(spec.descRes),
                    style = typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = a11yAdd }
                        .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onPin),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp), tint = scheme.onPrimaryContainer)
                    Text(
                        stringResource(R.string.widgets_add_action),
                        style = typography.labelLarge,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
        }

        MintCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .semantics { contentDescription = a11yAdd }
                    .mintClickable(pressedScale = 0.98f, role = Role.Button, onClick = onPin),
            shape = MaterialTheme.shapes.large,
            border = border,
            contentPadding = if (spec == WidgetSpec.NEXT) PaddingValues(0.dp) else PaddingValues(16.dp),
        ) {
            when (spec) {
                WidgetSpec.TODAY -> TodayWidgetPreview(snap, st, now)
                WidgetSpec.SCHEDULE -> ScheduleWidgetPreview(snap, st, now)
                WidgetSpec.TODO -> TodoWidgetPreview(snap, now)
                WidgetSpec.PARCEL -> ParcelWidgetPreview(snap, now)
                WidgetSpec.NEXT -> NextWidgetPreview(snap, st, now)
            }
        }
    }
}

@Composable
private fun PreviewAddPill() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp), tint = scheme.onPrimaryContainer)
    }
}

@Composable
private fun PreviewCheckbox(borderColor: Color) {
    Box(
        modifier =
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun TodayWidgetPreview(
    snap: Snapshot,
    st: Settings,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val mintColors = MintTheme.colors
    val today = now.toLocalDate()
    val weekBadge = remember(ctx, snap.currentSemester, today) { WidgetKit.weekBadge(ctx, snap.currentSemester, today) }
    val realRows = remember(ctx, snap, st, now) { TodayWidgetProvider.buildRows(ctx, snap, st, now).take(3) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.monthDayWeekday(today), style = typography.titleSmall, fontWeight = FontWeight.Bold)
            if (weekBadge != null) {
                Text(
                    " · $weekBadge",
                    style = typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            PreviewAddPill()
        }
        if (realRows.isNotEmpty()) {
            realRows.forEach { row ->
                if (row.colorIndex >= 0) {
                    val bg = if (row.finished) scheme.surfaceVariant else mintColors.bg(row.colorIndex)
                    val fg = if (row.finished) scheme.onSurfaceVariant else mintColors.fg(row.colorIndex)
                    val progress = if (row.ongoing) row.progress else 0f
                    PreviewCourseStrip(row.time, row.endTime, row.title, row.sub, bg, fg, progress)
                } else {
                    PreviewTaskStrip(
                        left = row.time,
                        code = row.code,
                        title = row.title,
                        checkColor = priorityColor(row.priority, row.overdue),
                    )
                }
            }
        } else {
            PreviewCourseStrip(
                time = "08:30",
                endTime = "10:05",
                title = stringResource(R.string.widgets_sample_course_1),
                sub = stringResource(R.string.widgets_sample_course_1_room),
                bg = mintColors.bg(1),
                fg = mintColors.fg(1),
                progress = 0.45f,
            )
            PreviewTaskStrip(
                left = "18:00",
                code = null,
                title = stringResource(R.string.widgets_sample_todo_1),
                checkColor = priorityColor(3, overdue = false),
            )
        }
    }
}

@Composable
private fun ScheduleWidgetPreview(
    snap: Snapshot,
    st: Settings,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val mintColors = MintTheme.colors
    val today = now.toLocalDate()
    val weekBadge = remember(ctx, snap.currentSemester, today) { WidgetKit.weekBadge(ctx, snap.currentSemester, today) }
    val courses =
        remember(snap, st.defaultBlockMinutes, now) {
            Agenda
                .forDay(snap, today, st.defaultBlockMinutes)
                .filterIsInstance<CourseItem>()
                .filter { it.occ.end.isAfter(now) }
                .take(3)
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.monthDayWeekday(today), style = typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (weekBadge != null) {
                Text(
                    weekBadge,
                    style = typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (courses.isNotEmpty()) {
            courses.forEach { item ->
                val o = item.occ
                val phase = WidgetKit.timePhase(o.start, o.end, now)
                val progress = if (phase == WidgetKit.TimePhase.ONGOING) WidgetKit.timeProgress(o.start, o.end, now) else 0f
                val bg = if (phase == WidgetKit.TimePhase.FINISHED) scheme.surfaceVariant else mintColors.bg(o.course.colorIndex)
                val fg = if (phase == WidgetKit.TimePhase.FINISHED) scheme.onSurfaceVariant else mintColors.fg(o.course.colorIndex)
                val meta =
                    listOfNotNull(
                        o.room.takeIf { it.isNotBlank() },
                        o.course.teacher.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifEmpty { null }
                PreviewScheduleCourseBlock(o.start.hm(), o.end.hm(), o.course.name, meta, bg, fg, progress)
            }
        } else {
            PreviewScheduleCourseBlock(
                start = "08:30",
                end = "10:05",
                name = stringResource(R.string.widgets_sample_course_1),
                meta = stringResource(R.string.widgets_sample_course_1_room),
                bg = mintColors.bg(1),
                fg = mintColors.fg(1),
                progress = 0.45f,
            )
            PreviewScheduleCourseBlock(
                start = "10:20",
                end = "11:55",
                name = stringResource(R.string.widgets_sample_course_2),
                meta = stringResource(R.string.widgets_sample_course_2_room),
                bg = mintColors.bg(0),
                fg = mintColors.fg(0),
                progress = 0f,
            )
        }
    }
}

@Composable
private fun TodoWidgetPreview(
    snap: Snapshot,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val today = now.toLocalDate()
    val todoRows =
        remember(snap, now) {
            val overdue = TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now).filter { !it.done }
            val todayList = TaskLogic.filter(snap.tasks, SmartList.TODAY, now).filter { !it.done && it !in overdue }
            val listNames = snap.lists.associate { it.id to it.name }
            (overdue + todayList).take(3).map { t ->
                val due = TaskLogic.effectiveDue(t, snap.currentSemester, snap.courses, snap.exceptions, now)
                val code = WidgetKit.parcelCode(t)
                TodoWidgetProvider.Row(
                    id = t.id,
                    title = if (code != null) WidgetKit.cleanTitleWithCode(ctx, t) else t.title,
                    code = code,
                    due = if (code != null) null else due?.let { ctx.dueLabel(it, t.dueHasTime, today) },
                    priority = t.priority,
                    overdue = t in overdue,
                    note =
                        if (code != null) {
                            null
                        } else {
                            t.note
                                .lineSequence()
                                .map { it.trim() }
                                .firstOrNull { it.isNotEmpty() }
                                ?.take(80)
                        },
                    listName = t.listId?.let { listNames[it] } ?: ctx.getString(R.string.bucket_inbox),
                    hasRemind = t.effectiveRemindMinutes.isNotEmpty(),
                    repeating = t.repeat != null,
                )
            }
        }
    val displayCount = if (todoRows.isNotEmpty()) todoRows.size else 2

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_todo), style = typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Tag(displayCount.toString(), scheme.primaryContainer, scheme.onPrimaryContainer)
            Spacer(Modifier.weight(1f))
            PreviewAddPill()
        }
        if (todoRows.isNotEmpty()) {
            todoRows.forEach { row ->
                PreviewTodoRow(
                    title = row.title,
                    code = row.code,
                    due = row.due,
                    checkColor = priorityColor(row.priority, row.overdue),
                    overdue = row.overdue,
                )
            }
        } else {
            PreviewTodoRow(
                title = stringResource(R.string.widgets_sample_todo_1),
                code = null,
                due = stringResource(R.string.widgets_sample_due_today),
                checkColor = priorityColor(3, overdue = false),
                overdue = false,
            )
            PreviewTodoRow(
                title = stringResource(R.string.widgets_sample_todo_2),
                code = null,
                due = null,
                checkColor = priorityColor(0, overdue = false),
                overdue = false,
            )
        }
    }
}

@Composable
private fun ParcelWidgetPreview(
    snap: Snapshot,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val today = now.toLocalDate()
    val parcelRows =
        remember(snap, now) {
            TaskLogic
                .topLevel(snap.tasks)
                .filter { !it.done && WidgetKit.isParcel(it) }
                .sortedWith(compareBy<Task>({ it.due == null }, { it.due }, { it.updatedAt }))
                .take(2)
                .map { t ->
                    val due = TaskLogic.effectiveDue(t, snap.currentSemester, snap.courses, snap.exceptions, now)
                    ParcelWidgetProvider.Row(
                        id = t.id,
                        code = WidgetKit.parcelCode(t) ?: ctx.getString(R.string.widget_parcel_title),
                        place = WidgetKit.cleanTitleWithCode(ctx, t),
                        due = due?.let { ctx.dueLabel(it, t.dueHasTime, today) },
                        overdue = due != null && due.isBefore(now),
                    )
                }
        }
    val displayCount = if (parcelRows.isNotEmpty()) parcelRows.size else 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.widget_parcel_name), style = typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Tag(displayCount.toString(), scheme.primaryContainer, scheme.onPrimaryContainer)
            Spacer(Modifier.weight(1f))
            PreviewAddPill()
        }
        if (parcelRows.isNotEmpty()) {
            parcelRows.forEach { row ->
                PreviewParcelRow(row.code, row.place, row.due, row.overdue)
            }
        } else {
            PreviewParcelRow(
                code = stringResource(R.string.widgets_sample_parcel_code),
                place = stringResource(R.string.widgets_sample_parcel_place),
                due = stringResource(R.string.widgets_sample_due_parcel),
                overdue = false,
            )
        }
    }
}

@Composable
private fun NextWidgetPreview(
    snap: Snapshot,
    st: Settings,
    now: LocalDateTime,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val summary = remember(ctx, snap, st, now) { NextWidgetProvider.pickSummary(ctx, snap, st, now) }
    val emptyTitle = stringResource(R.string.widget_next_empty)
    val useSample = summary.title == emptyTitle
    val startTime = if (useSample) "08:30" else summary.startTime
    val endTime = if (useSample) "10:05" else summary.endTime
    val badge = if (useSample) "" else summary.badge
    val badgeTone = if (useSample) NextWidgetProvider.BadgeTone.MUTED else summary.badgeTone
    val sub = if (useSample) stringResource(R.string.widgets_sample_course_1_room) else summary.sub
    val title = if (useSample) stringResource(R.string.widgets_sample_course_1) else summary.title
    val barColor = if (useSample) scheme.primary else Color(summary.barColor)
    val progress =
        if (useSample) {
            0.45f
        } else if (summary.ongoing) {
            summary.progress
        } else {
            0f
        }
    val wipeColor = scheme.surfaceContainerHighest

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (progress > 0f) {
                    drawRect(wipeColor, size = Size(size.width * progress.coerceIn(0.04f, 0.98f), size.height))
                }
            }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(barColor),
        )
        if (startTime != null) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(min = 48.dp), horizontalAlignment = Alignment.End) {
                Text(
                    startTime,
                    style = typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                )
                if (endTime != null) {
                    Text(
                        endTime,
                        style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                )
                if (badge.isNotEmpty()) {
                    when (badgeTone) {
                        NextWidgetProvider.BadgeTone.CODE -> {
                            Tag(badge, scheme.primaryContainer, scheme.onPrimaryContainer)
                        }

                        NextWidgetProvider.BadgeTone.OVERDUE -> {
                            Text(
                                badge,
                                style = typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.error,
                                maxLines = 1,
                            )
                        }

                        NextWidgetProvider.BadgeTone.MUTED -> {
                            Text(
                                badge,
                                style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (!sub.isNullOrBlank()) {
                Text(
                    sub,
                    style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PreviewAddPill()
    }
}

@Composable
private fun PreviewCourseStrip(
    time: String,
    endTime: String?,
    title: String,
    sub: String?,
    bg: Color,
    fg: Color,
    progress: Float = 0f,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val wipeColor = scheme.surfaceVariant.copy(alpha = 0.65f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .drawBehind {
                    if (progress > 0f) {
                        drawRect(wipeColor, size = Size(size.width * progress.coerceIn(0.04f, 0.98f), size.height))
                    }
                }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 44.dp)) {
            Text(time, style = typography.labelMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = fg)
            if (endTime != null) {
                Text(endTime, style = typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = fg.copy(alpha = 0.78f))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) {
                Text(sub, style = typography.labelSmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PreviewScheduleCourseBlock(
    start: String,
    end: String,
    name: String,
    meta: String?,
    bg: Color,
    fg: Color,
    progress: Float = 0f,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val wipeColor = scheme.surfaceVariant.copy(alpha = 0.65f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .drawBehind {
                    if (progress > 0f) {
                        drawRect(wipeColor, size = Size(size.width * progress.coerceIn(0.04f, 0.98f), size.height))
                    }
                }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 48.dp), horizontalAlignment = Alignment.End) {
            Text(start, style = typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = fg)
            Text(end, style = typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = fg.copy(alpha = 0.78f))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = typography.bodyMedium, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!meta.isNullOrBlank()) {
                Text(meta, style = typography.labelSmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PreviewTaskStrip(
    left: String,
    code: String?,
    title: String,
    checkColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left,
            style = typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            if (code != null) {
                Text(
                    code,
                    style = typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title,
                    style = typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    title,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        PreviewCheckbox(checkColor)
    }
}

@Composable
private fun PreviewTodoRow(
    title: String,
    code: String?,
    due: String?,
    checkColor: Color,
    overdue: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewCheckbox(checkColor)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (code != null) {
                Text(
                    code,
                    style = typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title,
                    style = typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    title,
                    style = typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!due.isNullOrBlank()) {
                    Text(
                        due,
                        style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (overdue) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewParcelRow(
    code: String,
    place: String,
    due: String?,
    overdue: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewCheckbox(if (overdue) scheme.error else scheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    code,
                    style = typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                if (!due.isNullOrBlank()) {
                    Text(
                        due,
                        style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (overdue) scheme.error else scheme.onSurfaceVariant,
                    )
                }
            }
            Text(place, style = typography.bodySmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun priorityColor(
    priority: Int,
    overdue: Boolean,
): Color {
    val scheme = MaterialTheme.colorScheme
    val mintColors = MintTheme.colors
    return when {
        overdue || priority >= 3 -> mintColors.priority[2]
        priority == 2 -> mintColors.priority[1]
        priority == 1 -> mintColors.priority[0]
        else -> scheme.outline
    }
}
