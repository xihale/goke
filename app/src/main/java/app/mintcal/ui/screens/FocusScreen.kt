package app.mintcal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mintcal.R
import app.mintcal.core.focus.FocusLogic
import app.mintcal.core.model.Snapshot
import app.mintcal.focus.FocusRun
import app.mintcal.focus.StopResult
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MinutesDialSheet
import app.mintcal.ui.components.NavClearance
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.dueLabel
import app.mintcal.ui.durationLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.rememberNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 专注计时页：正计时 / 倒计时，可挂某条待办；结束时落一条 FocusSession（可撤销、可同步）。
 * 计时状态在应用进程里（FocusController），离开页面继续走，倒计时到点由闹钟补记并通知。
 */
@Composable
fun FocusScreen(
    taskId: String?,
    onBack: () -> Unit,
    inTab: Boolean = false,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val now by rememberNow()
    val today = now.toLocalDate()
    val run by app.focus.run.collectAsState()
    val scope = rememberCoroutineScope()

    // 设置态（没有进行中的会话才显示）
    var countUp by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(25) }
    var pickMinutes by remember { mutableStateOf(false) }

    // 倒计时到点（含页面外补记）提示；手动结束的提示由结束按钮自己负责
    LaunchedEffect(Unit) {
        app.focus.events.collect { actions.toast(ctx.getString(it)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // Tab 层：内容铺满，底部让位给常驻岛；压栈页保持自身避开导航栏
            .then(if (inTab) Modifier else Modifier.navigationBarsPadding()),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (inTab) {
                Spacer(Modifier.width(48.dp))
            } else {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            }
            Text(
                stringResource(R.string.focus_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(48.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val r = run
            if (r == null) {
                SetupBody(
                    countUp = countUp,
                    onCountUp = { countUp = it },
                    minutes = minutes,
                    onMinutes = { minutes = it },
                    onPickMinutes = { pickMinutes = true },
                    onStart = { app.focus.start(taskId?.takeIf { id -> snap.tasks.any { it.id == id && it.deletedAt == null } }, countUp, minutes) },
                )
            } else {
                RunningBody(
                    snap = snap,
                    run = r,
                    onPause = { app.focus.pause() },
                    onResume = { app.focus.resume() },
                    onStop = {
                        scope.launch {
                            val res = app.focus.stop()
                            if (res == StopResult.TOO_SHORT) actions.toast(ctx.getString(R.string.focus_too_short))
                        }
                    },
                )
            }

            HistoryBody(snap = snap, today = today)
            // Tab 层：留出让位的底栏高度，记录能滚到岛上方看全；压栈页只留呼吸空间
            Spacer(
                Modifier.height(
                    if (inTab) {
                        NavClearance + 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    } else {
                        24.dp
                    },
                ),
            )
        }
    }

    if (pickMinutes) {
        MinutesDialSheet(
            initialMinutes = minutes,
            onDismiss = { pickMinutes = false },
            onPick = {
                minutes = it
                pickMinutes = false
            },
        )
    }
}

/** 设置态：模式 / 时长 + 开始按钮。 */
@Composable
private fun SetupBody(
    countUp: Boolean,
    onCountUp: (Boolean) -> Unit,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    onPickMinutes: () -> Unit,
    onStart: () -> Unit,
) {
    val ctx = LocalContext.current
    FormSection(stringResource(R.string.focus_mode)) {
        ChipChoice(
            options = listOf(false to stringResource(R.string.focus_mode_countdown), true to stringResource(R.string.focus_mode_countup)),
            selected = countUp,
            onSelect = onCountUp,
        )
    }
    if (!countUp) {
        ValueRow(stringResource(R.string.focus_pick_minutes), ctx.durationLabel(minutes * 60_000L), onClick = onPickMinutes)
    }
    Button(onClick = onStart, Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(stringResource(R.string.focus_start))
    }
}

/** 进行中：任务名 + 大表读数 + 暂停 / 结束。 */
@Composable
private fun RunningBody(
    snap: Snapshot,
    run: FocusRun,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    // 读数每 250ms 刷新一次；数值本身始终按墙上时钟推算
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(run.sessionId) {
        while (isActive) {
            delay(250)
            tick = System.currentTimeMillis()
        }
    }
    val paused = run.pausedAt != null
    val elapsed = app.focus.elapsedMillis(run, tick)
    val remaining = app.focus.remainingMillis(run, tick)

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            snap.tasks.firstOrNull { it.id == run.taskId }?.title ?: stringResource(R.string.focus_free),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            if (run.countUp) stringResource(R.string.focus_mode_countup) else stringResource(R.string.focus_mode_countdown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val shown =
            if (run.countUp) {
                elapsed
            } else {
                remaining.coerceAtLeast(0L)
            }
        Text(
            formatClock(shown),
            fontSize = 64.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        if (paused) {
            Text(stringResource(R.string.focus_paused), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        } else if (!run.countUp) {
            Text(
                stringResource(R.string.focus_running_of, ctx.durationLabel((run.plannedMinutes ?: 0) * 60_000L)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = if (paused) onResume else onPause) {
                Text(stringResource(if (paused) R.string.focus_resume else R.string.focus_pause))
            }
            Button(onClick = onStop) {
                Text(stringResource(R.string.focus_stop))
            }
        }
    }
}

/** 专注记录：累计 + 按天分组的会话列表（最近 10 天）。 */
@Composable
private fun HistoryBody(
    snap: Snapshot,
    today: java.time.LocalDate,
) {
    val ctx = LocalContext.current
    val sessions = FocusLogic.sorted(snap.focusSessions.filter { it.deletedAt == null })
    FormSection(stringResource(R.string.focus_history)) {
        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.focus_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@FormSection
        }
        Text(
            stringResource(
                R.string.focus_total_all,
                ctx.durationLabel(FocusLogic.totalMillis(sessions)),
            ) + " · " + stringResource(R.string.focus_session_count, sessions.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val zone = ZoneId.systemDefault()
        for ((day, daySessions) in FocusLogic.byDay(sessions, zone).entries.take(10)) {
            Text(
                ctx.dueLabel(day.atStartOfDay(), false, today) + " · " + ctx.durationLabel(FocusLogic.totalMillis(daySessions)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
            )
            for (s in daySessions) {
                val title =
                    when {
                        s.taskId == null -> stringResource(R.string.focus_free)
                        else -> snap.tasks.firstOrNull { it.id == s.taskId && it.deletedAt == null }?.title
                            ?: stringResource(R.string.focus_task_deleted)
                    }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDateTime().hm()}–${Instant.ofEpochMilli(s.endedAt).atZone(zone).toLocalDateTime().hm()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ctx.durationLabel(s.focusMillis),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** mm:ss（超过 1 小时 h:mm:ss）。 */
private fun formatClock(millis: Long): String {
    val total = (millis + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
