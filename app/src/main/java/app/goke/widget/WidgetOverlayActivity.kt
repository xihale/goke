package app.goke.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goke.DeepLinks
import app.goke.GokeApp
import app.goke.MainActivity
import app.goke.R
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.CourseNotes
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.WeekSets
import app.goke.core.todo.QuickAdd
import app.goke.core.todo.QuickBatchItem
import app.goke.core.todo.QuickTokenKind
import app.goke.core.todo.TaskLogic
import app.goke.data.DarkMode
import app.goke.gokeApp
import app.goke.notify.ReminderAlertService
import app.goke.notify.ReminderReceiver
import app.goke.notify.SnoozeOption
import app.goke.notify.SnoozePlan
import app.goke.notify.SnoozePlanner
import app.goke.notify.SnoozeScaleMode
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.CourseNoteBox
import app.goke.ui.components.ErrorReportDialog
import app.goke.ui.components.MintCheck
import app.goke.ui.components.MintSheetOverlay
import app.goke.ui.components.ProvideMintModalScope
import app.goke.ui.components.Tag
import app.goke.ui.components.TimeDialSheet
import app.goke.ui.components.TonalField
import app.goke.ui.components.mintClickable
import app.goke.ui.components.mintPressScale
import app.goke.ui.components.rememberNoFocusPopup
import app.goke.ui.dueLabel
import app.goke.ui.formatAllRooms
import app.goke.ui.hm
import app.goke.ui.icons.Alarm
import app.goke.ui.icons.ContentCopy
import app.goke.ui.icons.DeleteOutline
import app.goke.ui.icons.Flag
import app.goke.ui.icons.Inbox
import app.goke.ui.icons.Notes
import app.goke.ui.icons.OpenInFull
import app.goke.ui.icons.Repeat
import app.goke.ui.icons.Schedule
import app.goke.ui.screens.QuickAddComposer
import app.goke.ui.screens.QuickOverride
import app.goke.ui.screens.QuickPanel
import app.goke.ui.screens.TokenHighlight
import app.goke.ui.screens.WhenPanel
import app.goke.ui.screens.WhenPick
import app.goke.ui.screens.buildQuickTasks
import app.goke.ui.screens.freqLabel
import app.goke.ui.screens.remindSummaryLabel
import app.goke.ui.screens.rewriteQuickField
import app.goke.ui.screens.tokenRanges
import app.goke.ui.theme.GokeTheme
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import app.goke.ui.theme.priorityColor
import app.goke.ui.weekdayShort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import android.graphics.Color as AndroidColor

private val OverlayLeadW = 48.dp
private val OverlayRowMinH = 44.dp

internal data class ReminderOverlayContext(
    val notifId: Int = 0,
    val title: String? = null,
    val text: String? = null,
    val persistent: Boolean = false,
    val important: Boolean = false,
    val targetAtEpochMs: Long? = null,
)

internal sealed interface OverlayTarget {
    data class Add(
        val initialCourseId: String? = null,
        val initialText: String = "",
        val initialCourseWeek: Int? = null,
    ) : OverlayTarget

    data class CourseDetail(
        val courseId: String,
        val dateStr: String? = null,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
    ) : OverlayTarget

    data class TaskDetail(
        val taskId: String,
        val reminder: ReminderOverlayContext? = null,
    ) : OverlayTarget

    data class EventDetail(
        val eventId: String,
    ) : OverlayTarget
}

/**
 * 桌面小组件悬浮快速交互层：
 * 与应用内「薄荷便签」完全统一的贴底薄荷纸抽屉，支持自然语言快速添加、行内直接修改待办、
 * 就地完成/改期/复制取件码，及按需展开进主应用。
 */
class WidgetOverlayActivity : ComponentActivity() {
    private val targetState = mutableStateOf<OverlayTarget>(OverlayTarget.Add())
    private val visibleState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        stopReminderAlertIfRequested(intent)
        targetState.value = parseTarget(intent)
        visibleState.value = true
        val app = gokeApp
        setContent {
            val settings by app.settings.flow.collectAsState()
            val ready by app.repo.ready.collectAsState()
            val snap by app.repo.snapshot.collectAsState()
            val dark =
                when (settings.darkMode) {
                    DarkMode.SYSTEM -> isSystemInDarkTheme()
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
            LaunchedEffect(dark) {
                val style =
                    if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GokeTheme(settings, transparentBackground = true) {
                WidgetOverlayRoot(
                    app = app,
                    ready = ready,
                    snap = snap,
                    target = targetState.value,
                    visible = visibleState.value,
                    onChangeTarget = { targetState.value = it },
                    onOpenInApp = { uri ->
                        startActivity(
                            Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        finishNoAnim()
                    },
                    onDismiss = { visibleState.value = false },
                    onExited = { if (!visibleState.value) finishNoAnim() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stopReminderAlertIfRequested(intent)
        targetState.value = parseTarget(intent)
        visibleState.value = true
    }

    private fun stopReminderAlertIfRequested(intent: Intent?) {
        if (intent?.hasExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID) == true) {
            ReminderAlertService.stop(this, intent.getIntExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID, 0))
        }
    }

    private fun finishNoAnim() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        internal const val EXTRA_MODE = "overlay_mode"
        internal const val EXTRA_COURSE_ID = "course_id"
        private const val EXTRA_EVENT_ID = "event_id"
        internal const val EXTRA_DATE = "date"
        internal const val EXTRA_START_PERIOD = "start_period"
        internal const val EXTRA_END_PERIOD = "end_period"
        private const val EXTRA_FROM_REMINDER = "from_reminder"
        private const val MODE_ADD = "add"
        internal const val MODE_COURSE = "course"
        private const val MODE_TASK = "task"
        private const val MODE_EVENT = "event"

        fun addIntent(
            ctx: Context,
            courseId: String? = null,
        ): Intent =
            Intent(ctx, WidgetOverlayActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_ADD)
                .apply { if (!courseId.isNullOrBlank()) putExtra(EXTRA_COURSE_ID, courseId) }

        fun courseIntent(
            ctx: Context,
            courseId: String,
            dateStr: String? = null,
            startPeriod: Int? = null,
            endPeriod: Int? = null,
        ): Intent =
            Intent(ctx, WidgetOverlayActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_COURSE)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .apply {
                    if (!dateStr.isNullOrBlank()) putExtra(EXTRA_DATE, dateStr)
                    if (startPeriod != null) putExtra(EXTRA_START_PERIOD, startPeriod)
                    if (endPeriod != null) putExtra(EXTRA_END_PERIOD, endPeriod)
                }

        fun taskIntent(
            ctx: Context,
            taskId: String,
        ): Intent =
            Intent(ctx, WidgetOverlayActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_TASK)
                .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, taskId)

        fun reminderTaskIntent(
            ctx: Context,
            taskId: String,
            notifId: Int,
            title: String? = null,
            text: String? = null,
            persistent: Boolean = false,
            important: Boolean = false,
            targetAtMs: Long? = null,
        ): Intent =
            taskIntent(ctx, taskId)
                .putExtra(EXTRA_FROM_REMINDER, true)
                .putExtra(ReminderReceiver.EXTRA_ID, notifId)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, persistent)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, important)
                .apply {
                    if (targetAtMs != null && targetAtMs > 0L) {
                        putExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, targetAtMs)
                    }
                }

        fun eventIntent(
            ctx: Context,
            eventId: String,
        ): Intent =
            Intent(ctx, WidgetOverlayActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_EVENT)
                .putExtra(EXTRA_EVENT_ID, eventId)

        private fun parseTarget(intent: Intent?): OverlayTarget {
            intent ?: return OverlayTarget.Add()
            val mode = intent.getStringExtra(EXTRA_MODE)
            val taskId = intent.getStringExtra(TodoWidgetProvider.EXTRA_TASK_ID)
            val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
            val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
            val reminderCtx =
                if (intent.getBooleanExtra(EXTRA_FROM_REMINDER, false)) {
                    ReminderOverlayContext(
                        notifId = intent.getIntExtra(ReminderReceiver.EXTRA_ID, 0),
                        title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE),
                        text = intent.getStringExtra(ReminderReceiver.EXTRA_TEXT),
                        persistent = intent.getBooleanExtra(ReminderReceiver.EXTRA_PERSISTENT, false),
                        important = intent.getBooleanExtra(ReminderReceiver.EXTRA_IMPORTANT, false),
                        targetAtEpochMs = intent.getLongExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, 0L).takeIf { it > 0L },
                    )
                } else {
                    null
                }
            return when {
                mode == MODE_COURSE && !courseId.isNullOrBlank() -> {
                    val sp = intent.getIntExtra(EXTRA_START_PERIOD, -1).takeIf { it > 0 }
                    val ep = intent.getIntExtra(EXTRA_END_PERIOD, -1).takeIf { it > 0 }
                    OverlayTarget.CourseDetail(
                        courseId = courseId,
                        dateStr = intent.getStringExtra(EXTRA_DATE),
                        startPeriod = sp,
                        endPeriod = ep,
                    )
                }

                mode == MODE_TASK && !taskId.isNullOrBlank() -> {
                    OverlayTarget.TaskDetail(taskId, reminderCtx)
                }

                mode == MODE_EVENT && !eventId.isNullOrBlank() -> {
                    OverlayTarget.EventDetail(eventId)
                }

                !taskId.isNullOrBlank() -> {
                    OverlayTarget.TaskDetail(taskId, reminderCtx)
                }

                !courseId.isNullOrBlank() && mode != MODE_ADD -> {
                    OverlayTarget.CourseDetail(courseId)
                }

                !eventId.isNullOrBlank() -> {
                    OverlayTarget.EventDetail(eventId)
                }

                else -> {
                    OverlayTarget.Add(initialCourseId = courseId)
                }
            }
        }
    }
}

@Composable
internal fun WidgetOverlayRoot(
    app: GokeApp,
    ready: Boolean,
    snap: Snapshot,
    target: OverlayTarget,
    onChangeTarget: (OverlayTarget) -> Unit,
    onOpenInApp: (Uri) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean = true,
    onExited: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val settings by app.settings.flow.collectAsState()
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var dismissHook by remember { mutableStateOf<Pair<Boolean, () -> Unit>?>(null) }

    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(target) {
        dismissHook = null
        closing = false
    }

    fun commitAndRefresh(
        cs: ChangeSet,
        toast: String? = null,
        closeAfter: Boolean = false,
        thenOpenUri: Uri? = null,
    ) {
        if (closing) return
        if (closeAfter || thenOpenUri != null) closing = true
        scope.launch {
            try {
                app.repo.commit(cs)
                app.scope.launch(Dispatchers.Default) {
                    WidgetKit.refreshAllWidgets(app)
                }
                if (!toast.isNullOrBlank()) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
                    }
                }
                when {
                    thenOpenUri != null -> onOpenInApp(thenOpenUri)
                    closeAfter -> onDismiss()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                app.errors.record("WidgetOverlay", e)
            } finally {
                closing = false
            }
        }
    }

    fun scheduleReminderSnooze(
        task: Task,
        reminder: ReminderOverlayContext,
        triggerAt: LocalDateTime,
        targetAt: LocalDateTime?,
    ) {
        if (closing) return
        closing = true
        val title = reminder.title?.takeIf { it.isNotBlank() } ?: task.title
        val text = reminder.text?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.remind_task_text)
        SnoozePlanner.scheduleSnooze(
            ctx = ctx,
            taskId = task.id,
            notifId = reminder.notifId,
            title = title,
            text = text,
            persistent = reminder.persistent || task.remindPersistent,
            important = reminder.important || task.isExam,
            triggerAt = triggerAt,
            targetAt = targetAt,
        )
        val today = LocalDate.now()
        val whenText = ctx.dueLabel(triggerAt, true, today)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, ctx.getString(R.string.notif_snooze_scheduled_toast, whenText), Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    val isAdd = target is OverlayTarget.Add
    val sheetShape = RoundedCornerShape(topStart = if (isAdd) 24.dp else 28.dp, topEnd = if (isAdd) 24.dp else 28.dp)
    val slideSpec = MintTheme.spec<androidx.compose.ui.unit.IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()
    var hasNestedModal by remember { mutableStateOf(false) }

    ProvideMintModalScope(
        active = visible,
        onModalOpenChange = { hasNestedModal = it },
    ) {
        Box(Modifier.fillMaxSize()) {
            MintSheetOverlay(
                visible = shown && ready && visible,
                onDismissRequest = {
                    if (!closing) {
                        val hook = dismissHook
                        if (hook != null && !hook.first) {
                            hook.second()
                        } else {
                            onDismiss()
                        }
                    }
                },
                scrimAlpha = 0.26f,
                canDragDismiss = !closing && !hasNestedModal,
                predictiveBackEnabled = visible && !hasNestedModal,
                needsConfirmOnDismiss = dismissHook?.first == true,
                onDismissConfirmRequest = { dismissHook?.second?.invoke() },
                onExited = onExited,
                sheetModifier =
                    Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                shape = sheetShape,
                containerColor = scheme.surfaceContainerLow,
                maxWidth = 560.dp,
                dragHandle = !isAdd,
            ) {
                val live = remember(snap) { snap.live() }
                if (isAdd) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
                AnimatedContent(
                    targetState = target,
                    contentKey = { t ->
                        when (t) {
                            is OverlayTarget.Add -> "add:${t.initialCourseId}:${t.initialText}"
                            is OverlayTarget.CourseDetail -> "course:${t.courseId}:${t.dateStr}:${t.startPeriod}:${t.endPeriod}"
                            is OverlayTarget.TaskDetail -> "task:${t.taskId}:${t.reminder?.notifId ?: 0}"
                            is OverlayTarget.EventDetail -> "event:${t.eventId}"
                        }
                    },
                    transitionSpec = {
                        if (reduce) {
                            fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)
                        } else {
                            (slideInHorizontally(slideSpec) { it / 6 } + fadeIn(fadeSpec)) togetherWith
                                (slideOutHorizontally(slideSpec) { -it / 6 } + fadeOut(fadeSpec)) using
                                SizeTransform(clip = false)
                        }
                    },
                    label = "overlay-target",
                ) { activeTarget ->
                    val targetIsAdd = activeTarget is OverlayTarget.Add
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = if (targetIsAdd) 10.dp else 6.dp,
                                    bottom = if (targetIsAdd) 10.dp else 16.dp,
                                ),
                    ) {
                        when (activeTarget) {
                            is OverlayTarget.Add -> {
                                OverlayQuickAddCard(
                                    snap = live,
                                    defaultBlockMinutes = settings.defaultBlockMinutes,
                                    initialCourseId = activeTarget.initialCourseId,
                                    initialText = activeTarget.initialText,
                                    initialCourseWeek = activeTarget.initialCourseWeek,
                                    registerDismissHook = { dismissHook = it },
                                    onExpand = { draft ->
                                        onOpenInApp(DeepLinks.addUri(draft.ifBlank { null }, activeTarget.initialCourseId))
                                    },
                                    onCommit = { cs, msg -> commitAndRefresh(cs, msg, closeAfter = true) },
                                    onDismiss = onDismiss,
                                )
                            }

                            is OverlayTarget.CourseDetail -> {
                                val course = live.courses.firstOrNull { it.id == activeTarget.courseId }
                                if (course == null) {
                                    LaunchedEffect(Unit) { onOpenInApp(DeepLinks.timetableUri()) }
                                } else {
                                    OverlayCourseCard(
                                        snap = live,
                                        course = course,
                                        dateStr = activeTarget.dateStr,
                                        startPeriod = activeTarget.startPeriod,
                                        endPeriod = activeTarget.endPeriod,
                                        onToggleTask = { task, done ->
                                            val nowDt = LocalDateTime.now()
                                            val desc = ctx.getString(R.string.edit_task_desc, task.title)
                                            val toast =
                                                if (done) ctx.getString(R.string.widget_overlay_task_done_toast, task.title) else null
                                            commitAndRefresh(
                                                Edits.toggleTask(live, task, done, System.currentTimeMillis(), nowDt, desc),
                                                toast = toast,
                                                closeAfter = false,
                                            )
                                        },
                                        onOpenTask = { task ->
                                            onChangeTarget(OverlayTarget.TaskDetail(task.id))
                                        },
                                        onAddCourseTask = { cw ->
                                            onChangeTarget(OverlayTarget.Add(initialCourseId = course.id, initialCourseWeek = cw))
                                        },
                                        onExpand = { onOpenInApp(DeepLinks.courseUri(course.id)) },
                                    )
                                }
                            }

                            is OverlayTarget.TaskDetail -> {
                                val task = live.tasks.firstOrNull { it.id == activeTarget.taskId }
                                val reminder = activeTarget.reminder
                                if (task == null) {
                                    LaunchedEffect(Unit) { onDismiss() }
                                } else if (WidgetKit.isParcel(task)) {
                                    OverlayParcelCard(
                                        snap = live,
                                        defaultBlockMinutes = settings.defaultBlockMinutes,
                                        task = task,
                                        reminder = reminder,
                                        onSnooze = { triggerAt, targetAt ->
                                            if (reminder != null) {
                                                scheduleReminderSnooze(task, reminder, triggerAt, targetAt)
                                            }
                                        },
                                        onMarkDone = {
                                            val msg = ctx.getString(R.string.widget_overlay_parcel_done_toast, task.title)
                                            commitAndRefresh(
                                                Edits.toggleTask(live, task, true, System.currentTimeMillis(), LocalDateTime.now(), task.title),
                                                toast = msg,
                                                closeAfter = true,
                                            )
                                        },
                                        onCommit = { cs, msg -> commitAndRefresh(cs, msg, closeAfter = false) },
                                        onExpand = { onOpenInApp(DeepLinks.taskUri(task.id)) },
                                    )
                                } else {
                                    OverlayTaskCard(
                                        snap = live,
                                        defaultBlockMinutes = settings.defaultBlockMinutes,
                                        task = task,
                                        reminder = reminder,
                                        onSnooze = { triggerAt, targetAt ->
                                            if (reminder != null) {
                                                scheduleReminderSnooze(task, reminder, triggerAt, targetAt)
                                            }
                                        },
                                        registerDismissHook = { dismissHook = it },
                                        onToggleDone = { t, done, closeAfter ->
                                            val desc = ctx.getString(R.string.edit_task_desc, t.title)
                                            val msg =
                                                if (done) ctx.getString(R.string.widget_overlay_task_done_toast, t.title) else null
                                            commitAndRefresh(
                                                Edits.toggleTask(live, t, done, System.currentTimeMillis(), LocalDateTime.now(), desc),
                                                toast = msg,
                                                closeAfter = closeAfter,
                                            )
                                        },
                                        onCommitEdit = { updated, msg, closeAfter, thenOpenUri ->
                                            val desc = ctx.getString(R.string.edit_task_desc, updated.title)
                                            commitAndRefresh(
                                                Edits.save(live, updated, desc, System.currentTimeMillis()),
                                                toast = msg,
                                                closeAfter = closeAfter,
                                                thenOpenUri = thenOpenUri,
                                            )
                                        },
                                        onDelete = {
                                            commitAndRefresh(
                                                Edits.delete(
                                                    live,
                                                    task,
                                                    ctx.getString(R.string.delete_desc, task.title),
                                                    System.currentTimeMillis(),
                                                ),
                                                toast = ctx.getString(R.string.widget_overlay_deleted_toast, task.title),
                                                closeAfter = true,
                                            )
                                        },
                                        onOpenInApp = { onOpenInApp(DeepLinks.taskUri(task.id)) },
                                        onDismiss = onDismiss,
                                    )
                                }
                            }

                            is OverlayTarget.EventDetail -> {
                                val event = live.events.firstOrNull { it.id == activeTarget.eventId }
                                if (event == null) {
                                    LaunchedEffect(Unit) { onDismiss() }
                                } else {
                                    OverlayEventCard(
                                        event = event,
                                        onOpenInApp = { onOpenInApp(DeepLinks.todayUri()) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val pendingError by app.errors.pendingModal.collectAsState()
            val activeError = pendingError
            if (activeError != null) {
                ErrorReportDialog(
                    report = activeError,
                    onDismiss = { app.errors.dismissPending() },
                )
            }
        }
    }
}

@Composable
private fun OverlayQuickAddCard(
    snap: Snapshot,
    defaultBlockMinutes: Int,
    initialCourseId: String?,
    initialText: String,
    initialCourseWeek: Int? = null,
    registerDismissHook: ((Pair<Boolean, () -> Unit>?) -> Unit),
    onExpand: (String) -> Unit,
    onCommit: (ChangeSet, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val now = remember { LocalDateTime.now() }
    val linkedCourse = remember(snap, initialCourseId) { initialCourseId?.let { id -> snap.courses.firstOrNull { it.id == id } } }

    var field by remember(initialText) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    val text = field.text
    var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
    var whenPanel by remember { mutableStateOf(false) }
    var panelWhen by remember { mutableStateOf<WhenPick?>(null) }
    var panelRemindList by remember { mutableStateOf<List<Int>>(emptyList()) }
    var panelPersistent by remember { mutableStateOf(false) }
    var panelFreq by remember { mutableStateOf<Freq?>(null) }
    var panelInterval by remember { mutableIntStateOf(1) }
    var panelUntil by remember { mutableStateOf<LocalDate?>(null) }
    var panelByDays by remember { mutableStateOf(emptySet<Int>()) }
    var panelByMonths by remember { mutableStateOf(emptySet<Int>()) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val tokens = parsed?.tokens.orEmpty()
    val highlight =
        remember(tokens, scheme.primary, scheme.error, scheme.tertiary, scheme.secondary, colors) {
            if (tokens.isEmpty()) {
                VisualTransformation.None
            } else {
                val prioColor = colors.priorityColor(parsed?.priority ?: 3) ?: scheme.error
                TokenHighlight(tokens, scheme.primary, prioColor, scheme.tertiary, scheme.secondary)
            }
        }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val hasDraft = text.isNotBlank() || panelWhen != null || panelRemindList.isNotEmpty() || panelFreq != null
    LaunchedEffect(hasDraft) {
        registerDismissHook(if (hasDraft) (true to { confirmDiscard = true }) else null)
    }

    fun rewriteToken(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        field = rewriteQuickField(field.text, parsed?.tokens.orEmpty(), kinds, add)
        focusRequester.requestFocus()
    }

    fun submit() {
        val items = QuickAdd.parseLines(text, now)
        if (items.isEmpty()) return
        val first = items.first().result
        val desc = ctx.getString(R.string.add_task_desc, first.title)
        val where =
            first.due
                ?.takeIf { items.size == 1 }
                ?.let { " · " + ctx.dueLabel(it, first.dueHasTime, now.toLocalDate()) }
                .orEmpty()
        val msg =
            if (items.size == 1) {
                ctx.getString(R.string.task_added_toast, first.title) + where
            } else {
                ctx.getString(R.string.todo_batch_toast, items.size)
            }
        val ov =
            if (panelWhen != null || panelRemindList.isNotEmpty() || panelFreq != null) {
                QuickOverride(
                    whenPick = panelWhen,
                    remindList = panelRemindList,
                    persistent = panelPersistent,
                    repeat =
                        panelFreq?.let { f ->
                            Recurrence(
                                freq = f,
                                interval = panelInterval.coerceAtLeast(1),
                                byDays = if (f == Freq.DAILY) emptySet() else panelByDays,
                                until = panelUntil,
                                byMonths = if (f == Freq.YEARLY) panelByMonths else emptySet(),
                            )
                        },
                )
            } else {
                null
            }
        var cs = buildQuickTasks(snap, items, defaultListId = null, defaultBucket = null, now = now, desc = desc, override = ov)
        if (linkedCourse != null) {
            cs =
                cs.copy(
                    changes =
                        cs.changes.map { ch ->
                            if (ch is Change.Add && ch.after is Task) {
                                val t = ch.after as Task
                                if (t.courseId == null && t.parentId == null) {
                                    Change.Add(
                                        t.withCourseLink(
                                            courseId = linkedCourse.id,
                                            courseWeek = initialCourseWeek,
                                            dueBeforeNextClass = t.due == null,
                                        ),
                                    )
                                } else {
                                    ch
                                }
                            } else {
                                ch
                            }
                        },
                )
        }
        onCommit(cs, msg)
    }

    val hasBodyText = parsed != null && !(parsed.tokens.isNotEmpty() && parsed.title == text.trim())

    QuickAddComposer(
        snap = snap,
        now = now,
        field = field,
        onFieldChange = { field = it },
        parsed = parsed,
        highlight = highlight,
        focusRequester = focusRequester,
        toolPanel = toolPanel,
        onToolPanelChange = { toolPanel = it },
        panelWhen = panelWhen,
        panelRemindList = panelRemindList,
        panelFreq = panelFreq,
        panelInterval = panelInterval,
        panelByDays = panelByDays,
        panelByMonths = panelByMonths,
        linkedCourse = linkedCourse,
        expandLabel = stringResource(R.string.widget_overlay_expand_app),
        onExpand = { onExpand(text) },
        submitLabel = stringResource(R.string.widgets_add_action),
        canSubmit = hasBodyText,
        onSubmit = { submit() },
        onOpenWhenPanel = { whenPanel = true },
        onRewriteToken = ::rewriteToken,
    )

    if (whenPanel) {
        WhenPanel(
            initial =
                panelWhen ?: parsed?.due?.let { d ->
                    WhenPick(d.toLocalDate(), if (parsed.dueHasTime) d.toLocalTime() else null)
                },
            defaultDate = now.toLocalDate(),
            defaultMinutes = defaultBlockMinutes,
            initialRemindList = panelRemindList,
            initialPersistent = panelPersistent,
            initialFreq = panelFreq,
            initialInterval = panelInterval,
            initialUntil = panelUntil,
            initialByDays = panelByDays,
            initialByMonths = panelByMonths,
            semesterEnd = snap.currentSemester?.let { SemesterCalendar(it).weekEnd(it.totalWeeks) },
            onDismiss = { whenPanel = false },
            onPick = { r ->
                panelWhen = r.whenPick
                panelRemindList = r.remindMinutesList
                panelPersistent = r.remindPersistent
                panelFreq = r.freq
                panelInterval = r.interval
                panelUntil = r.until
                panelByDays = r.byDays
                panelByMonths = r.byMonths
                if (r.whenPick == null && r.remindMinutesList.isEmpty() && r.freq == null) {
                    rewriteToken(setOf(QuickTokenKind.DATE, QuickTokenKind.TIME), null)
                }
                whenPanel = false
            },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.detail_editor_discard_title),
            text = stringResource(R.string.detail_editor_discard_desc),
            confirm = stringResource(R.string.detail_editor_discard),
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCourseCard(
    snap: Snapshot,
    course: Course,
    dateStr: String?,
    startPeriod: Int?,
    endPeriod: Int?,
    onToggleTask: (Task, Boolean) -> Unit,
    onOpenTask: (Task) -> Unit,
    onAddCourseTask: (Int?) -> Unit,
    onExpand: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val sem = snap.currentSemester
    val cal = remember(sem) { sem?.let { SemesterCalendar(it) } }
    val date = remember(dateStr) { dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val sp = startPeriod ?: course.startPeriod
    val ep = endPeriod ?: course.endPeriod
    val dow = date?.dayOfWeek?.value ?: course.dayOfWeek
    val timeText =
        buildString {
            append(stringResource(R.string.weekday_prefix, ctx.weekdayShort(dow)))
            val customClock = sp == course.startPeriod && ep == course.endPeriod
            val st = ((if (customClock) course.startTime else null) ?: cal?.startTime(sp))?.hm()
            val et = ((if (customClock) course.endTime else null) ?: cal?.endTime(ep))?.hm()
            if (st != null && et != null) append(" $st–$et")
            append(" · ")
            append(stringResource(R.string.periods_range, sp, ep))
        }
    val metaLine =
        listOfNotNull(
            course.room.takeIf { it.isNotBlank() }?.let(::formatAllRooms),
            course.teacher.takeIf { it.isNotBlank() },
            WeekSets.format(course.weeks).takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    val targetOcc =
        remember(sem, course, date, sp, ep, snap.exceptions) {
            if (sem == null) return@remember null
            val targetDate = date ?: LocalDate.now()
            val occs = CourseExpander.occurrences(sem, listOf(course), snap.exceptions, targetDate, targetDate)
            occs.firstOrNull { it.startPeriod == sp && it.endPeriod == ep } ?: occs.firstOrNull()
        }
    val now = remember { LocalDateTime.now() }
    val courseTasks =
        remember(snap.tasks, snap.courses, snap.exceptions, sem, course, targetOcc, now) {
            if (targetOcc != null && sem != null) {
                TaskLogic.forOccurrence(snap.tasks, sem, snap.courses, snap.exceptions, targetOcc, now)
            } else {
                TaskLogic.forCourse(snap.tasks, snap.courses, course)
            }
        }

    val week =
        remember(cal, date) {
            val targetDate = date ?: LocalDate.now()
            cal?.weekOf(targetDate)?.takeIf { it > 0 }
        }
    val parsedNote = remember(course.note, week) { CourseNotes.parse(course.note, week) }

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.fg(course.colorIndex)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            course.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (course.isExam) {
            Spacer(Modifier.width(8.dp))
            Tag(
                stringResource(R.string.kind_exam),
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )
        }
    }

    Column(Modifier.padding(start = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            timeText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurfaceVariant,
        )
        if (metaLine.isNotEmpty()) {
            Text(
                metaLine,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
        if (course.note.isNotBlank()) {
            CourseNoteBox(
                note = course.note,
                week = week,
                parsedNote = parsedNote,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    if (courseTasks.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.widget_overlay_course_tasks),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        courseTasks.take(4).forEach { t ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .mintClickable(pressedScale = 0.98f) { onOpenTask(t) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MintCheck(
                    checked = t.done,
                    onChange = { onToggleTask(t, it) },
                    size = 20.dp,
                    tint = colors.priorityColor(t.priority),
                )
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    val expandInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalButton(
            onClick = onExpand,
            interactionSource = expandInteraction,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).mintPressScale(expandInteraction, 0.96f),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ),
        ) {
            Icon(Icons.Rounded.OpenInFull, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.widget_overlay_course_detail))
        }
        Button(
            onClick = { onAddCourseTask(targetOcc?.originalWeek ?: week) },
            interactionSource = addInteraction,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).mintPressScale(addInteraction, 0.96f),
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.course_todos_add))
        }
    }
}

@Composable
private fun OverlayParcelCard(
    snap: Snapshot,
    defaultBlockMinutes: Int,
    task: Task,
    reminder: ReminderOverlayContext? = null,
    onSnooze: (LocalDateTime, LocalDateTime?) -> Unit = { _, _ -> },
    onMarkDone: () -> Unit,
    onCommit: (ChangeSet, String) -> Unit,
    onExpand: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val code = WidgetKit.parcelCode(task) ?: stringResource(R.string.widget_parcel_title)
    val place = WidgetKit.cleanTitleWithCode(ctx, task)
    val due = task.due?.let { ctx.dueLabel(it, task.dueHasTime, today) }
    val overdue = task.due != null && task.due!!.isBefore(now)
    var whenPanel by remember { mutableStateOf(false) }

    val showNote =
        remember(task.note, code, place) {
            val n = task.note.trim()
            n.isNotEmpty() && (!n.contains(code) || n.length > code.length + place.length + 14)
        }

    fun copyCode() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("parcel_code", code))
        Toast.makeText(ctx, ctx.getString(R.string.parcel_code_copied, code), Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            code,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .mintClickable(pressedScale = 0.98f, role = Role.Button) { copyCode() }
                    .padding(vertical = 2.dp),
        )
        OverlayIconBtn(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.parcel_copy_code, code),
            tint = scheme.primary,
            iconSize = 20.dp,
            onClick = { copyCode() },
        )
        OverlayIconBtn(
            icon = Icons.Rounded.OpenInFull,
            contentDescription = stringResource(R.string.widget_overlay_expand_app),
            tint = scheme.onSurfaceVariant,
            iconSize = 20.dp,
            onClick = onExpand,
        )
    }

    val extraLocation = task.extra["location"]?.trim().orEmpty()
    Text(
        place,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = scheme.onSurface,
        modifier = Modifier.padding(top = 2.dp),
    )
    if (extraLocation.isNotEmpty() && !place.contains(extraLocation)) {
        Text(
            extraLocation,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (due != null) {
        Text(
            due,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = if (overdue) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (showNote) {
        Text(
            task.note.trim(),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (reminder != null && !task.done) {
        Spacer(Modifier.height(10.dp))
        QuickSnoozeSection(
            snap = snap,
            task = task,
            reminder = reminder,
            onSnooze = onSnooze,
        )
    }

    Spacer(Modifier.height(16.dp))

    val reschedInteraction = remember { MutableInteractionSource() }
    val doneInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalButton(
            onClick = { whenPanel = true },
            interactionSource = reschedInteraction,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).mintPressScale(reschedInteraction, 0.96f),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ),
        ) {
            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.widget_overlay_reschedule))
        }
        Button(
            onClick = onMarkDone,
            interactionSource = doneInteraction,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).mintPressScale(doneInteraction, 0.96f),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.widget_overlay_parcel_done))
        }
    }

    if (whenPanel) {
        val initPick =
            remember(task) {
                task.blockStart?.let { WhenPick(it.toLocalDate(), it.toLocalTime(), task.blockMinutes ?: defaultBlockMinutes) }
                    ?: task.due?.let { WhenPick(it.toLocalDate(), if (task.dueHasTime) it.toLocalTime() else null) }
            }
        WhenPanel(
            initial = initPick,
            defaultDate = today,
            defaultMinutes = defaultBlockMinutes,
            initialRemindList = task.effectiveRemindMinutes,
            initialPersistent = task.remindPersistent,
            initialFreq = task.repeat?.freq,
            initialInterval = task.repeat?.interval ?: 1,
            initialUntil = task.repeat?.until,
            initialByDays = task.repeat?.byDays ?: emptySet(),
            initialByMonths = task.repeat?.byMonths ?: emptySet(),
            semesterEnd = snap.currentSemester?.let { SemesterCalendar(it).weekEnd(it.totalWeeks) },
            onDismiss = { whenPanel = false },
            onPick = { r ->
                val ms = System.currentTimeMillis()
                val updated = applyWhenResult(task, r, ms)
                val desc = ctx.getString(R.string.edit_task_desc, updated.title)
                onCommit(Edits.save(snap, updated, desc, ms), ctx.getString(R.string.widget_overlay_saved_toast, updated.title))
                whenPanel = false
            },
        )
    }
}

private fun applyWhenResult(
    task: Task,
    r: app.goke.ui.screens.WhenPanelResult,
    ms: Long,
): Task {
    val w = r.whenPick
    var blockStartAt: LocalDateTime? = null
    var dueAt: LocalDateTime? = null
    var blockMinutes: Int? = null
    var hasTime = false
    if (w != null) {
        val dur = w.minutes
        if (w.start != null) {
            val at = LocalDateTime.of(w.date, w.start)
            if (dur != null) {
                blockStartAt = at
                blockMinutes = dur
                dueAt = at.plusMinutes(dur.toLong())
            } else {
                dueAt = at
            }
            hasTime = true
        } else {
            dueAt = w.date.atTime(23, 59)
        }
    }
    return task.copy(
        blockStart = blockStartAt,
        blockMinutes = blockMinutes,
        due = dueAt,
        dueHasTime = hasTime,
        remindMinutes = r.remindMinutesList.firstOrNull(),
        remindMinutesList = r.remindMinutesList,
        remindPersistent = r.remindMinutesList.isNotEmpty() && r.remindPersistent,
        repeat =
            r.freq?.let { f ->
                (task.repeat?.takeIf { it.freq == f } ?: Recurrence(f)).copy(
                    freq = f,
                    interval = r.interval.coerceAtLeast(1),
                    byDays = if (f == Freq.DAILY) emptySet() else r.byDays,
                    byMonths = if (f == Freq.YEARLY) r.byMonths else emptySet(),
                    until = r.until,
                )
            },
        updatedAt = ms,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayTaskCard(
    snap: Snapshot,
    defaultBlockMinutes: Int,
    task: Task,
    reminder: ReminderOverlayContext? = null,
    onSnooze: (LocalDateTime, LocalDateTime?) -> Unit = { _, _ -> },
    registerDismissHook: ((Pair<Boolean, () -> Unit>?) -> Unit),
    onToggleDone: (Task, Boolean, Boolean) -> Unit,
    onCommitEdit: (Task, String?, Boolean, Uri?) -> Unit,
    onDelete: () -> Unit,
    onOpenInApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val subtasks = remember(snap.tasks, task.id) { snap.tasks.filter { it.parentId == task.id } }

    val initWhen =
        remember(task.id) {
            task.blockStart?.let { WhenPick(it.toLocalDate(), it.toLocalTime(), task.blockMinutes ?: defaultBlockMinutes) }
                ?: task.due?.let { WhenPick(it.toLocalDate(), if (task.dueHasTime) it.toLocalTime() else null) }
        }
    var title by remember(task.id) { mutableStateOf(task.title) }
    var location by remember(task.id) { mutableStateOf(task.extra["location"].orEmpty()) }
    var note by remember(task.id) { mutableStateOf(task.note) }
    var priority by remember(task.id) { mutableIntStateOf(task.priority) }
    var listId by remember(task.id) { mutableStateOf(task.listId) }
    var done by remember(task.id, task.done) { mutableStateOf(task.done) }
    var whenPick by remember(task.id) { mutableStateOf(initWhen) }
    var remindList by remember(task.id) { mutableStateOf(task.effectiveRemindMinutes) }
    var persistent by remember(task.id) { mutableStateOf(task.remindPersistent) }
    var freq by remember(task.id) { mutableStateOf(task.repeat?.freq) }
    var interval by remember(task.id) { mutableIntStateOf(task.repeat?.interval ?: 1) }
    var until by remember(task.id) { mutableStateOf(task.repeat?.until) }
    var byDays by remember(task.id) { mutableStateOf(task.repeat?.byDays ?: emptySet()) }
    var byMonths by remember(task.id) { mutableStateOf(task.repeat?.byMonths ?: emptySet()) }

    var listPanel by remember { mutableStateOf(false) }
    var whenPanel by remember { mutableStateOf(false) }

    val listName = remember(snap.lists, listId) { snap.lists.firstOrNull { it.id == listId }?.name }
    val course = remember(snap.courses, task.courseId) { snap.courses.firstOrNull { it.id == task.courseId } }

    val whenChanged =
        whenPick?.date != initWhen?.date || whenPick?.start != initWhen?.start || whenPick?.minutes != initWhen?.minutes
    val repeatChanged =
        freq != task.repeat?.freq ||
            (
                freq != null &&
                    (
                        interval != (task.repeat?.interval ?: 1) ||
                            until != task.repeat?.until ||
                            byDays != (task.repeat?.byDays ?: emptySet<Int>()) ||
                            byMonths != (task.repeat?.byMonths ?: emptySet<Int>())
                    )
            )
    val isDirty =
        title.trim() != task.title ||
            location.trim() != task.extra["location"].orEmpty() ||
            note != task.note ||
            priority != task.priority ||
            listId != task.listId ||
            done != task.done ||
            remindList != task.effectiveRemindMinutes ||
            persistent != task.remindPersistent ||
            whenChanged ||
            repeatChanged

    fun buildUpdated(ms: Long): Task {
        val w = whenPick
        var blockStartAt: LocalDateTime? = null
        var dueAt: LocalDateTime? = null
        var blockMinutes: Int? = null
        var hasTime = false
        if (w != null) {
            val dur = w.minutes
            if (w.start != null) {
                val at = LocalDateTime.of(w.date, w.start)
                if (dur != null) {
                    blockStartAt = at
                    blockMinutes = dur
                    dueAt = at.plusMinutes(dur.toLong())
                } else {
                    dueAt = at
                }
                hasTime = true
            } else {
                dueAt = w.date.atTime(23, 59)
            }
        }
        val nextExtra =
            task.extra.toMutableMap().apply {
                val loc = location.trim()
                if (loc.isNotEmpty()) put("location", loc) else remove("location")
            }
        return task.copy(
            title = title.trim(),
            note = note,
            extra = nextExtra,
            priority = priority,
            listId = listId,
            done = done,
            doneAt = if (done) (task.doneAt ?: ms) else null,
            dropped = if (!done) false else task.dropped,
            blockStart = blockStartAt,
            blockMinutes = blockMinutes,
            due = dueAt,
            dueHasTime = hasTime,
            remindMinutes = remindList.firstOrNull(),
            remindMinutesList = remindList,
            remindPersistent = remindList.isNotEmpty() && persistent,
            repeat =
                freq?.let { f ->
                    (task.repeat?.takeIf { it.freq == f } ?: Recurrence(f)).copy(
                        freq = f,
                        interval = interval.coerceAtLeast(1),
                        byDays = if (f == Freq.DAILY) emptySet() else byDays,
                        byMonths = if (f == Freq.YEARLY) byMonths else emptySet(),
                        until = until,
                    )
                },
            updatedAt = ms,
        )
    }

    fun saveAndMaybe(
        closeAfter: Boolean,
        thenOpenUri: Uri? = null,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || !isDirty) {
            if (thenOpenUri != null) {
                onOpenInApp()
            } else if (closeAfter) {
                onDismiss()
            }
            return
        }
        val ms = System.currentTimeMillis()
        val updated = buildUpdated(ms)
        val msg = if (closeAfter && thenOpenUri == null) ctx.getString(R.string.widget_overlay_saved_toast, updated.title) else null
        onCommitEdit(updated, msg, closeAfter, thenOpenUri)
    }

    LaunchedEffect(
        isDirty,
        title,
        location,
        note,
        priority,
        listId,
        done,
        whenPick,
        remindList,
        persistent,
        freq,
        interval,
        until,
        byDays,
        byMonths,
    ) {
        registerDismissHook(
            if (isDirty && title.trim().isNotEmpty()) {
                false to { saveAndMaybe(closeAfter = true) }
            } else {
                null
            },
        )
    }

    val whenText =
        remember(whenPick, today) {
            val w = whenPick
            when {
                w == null -> {
                    null
                }

                w.start != null && w.minutes != null -> {
                    val start = LocalDateTime.of(w.date, w.start)
                    val end = start.plusMinutes(w.minutes.toLong())
                    "${ctx.dueLabel(start, true, today)}–${end.hm()}"
                }

                w.start != null -> {
                    ctx.dueLabel(LocalDateTime.of(w.date, w.start), true, today)
                }

                else -> {
                    ctx.dueLabel(w.date.atStartOfDay(), false, today)
                }
            }
        }
    val freqShort =
        if (freq != null) {
            freqLabel(freq, until, interval, byDays, whenPick?.date, withUntil = false, byMonths = byMonths)
        } else {
            null
        }
    val extrasText =
        listOfNotNull(
            freqShort,
            remindList.takeIf { it.isNotEmpty() }?.let { remindSummaryLabel(it) },
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
    val whenPrimary = whenText ?: extrasText ?: stringResource(R.string.detail_editor_pick_date)
    val whenSecondary = if (whenText != null) extrasText else null
    val hasWhenInfo = whenText != null || extrasText != null
    val overdue =
        whenPick?.let { w ->
            !done &&
                when {
                    w.start != null -> LocalDateTime.of(w.date, w.start).isBefore(now)
                    else -> w.date.isBefore(today)
                }
        } == true

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MintCheck(
            checked = done,
            onChange = { checked ->
                done = checked
                if (!isDirty && checked && !task.done) {
                    onToggleDone(task, true, true)
                }
            },
            tint = colors.priorityColor(priority),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .alpha(if (done) 0.45f else 1f),
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                cursorBrush = SolidColor(scheme.primary),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAndMaybe(closeAfter = true) }),
            )
            if (title.isEmpty()) {
                Text(
                    stringResource(R.string.quick_add_prompt),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        if (task.isExam) {
            Spacer(Modifier.width(8.dp))
            Tag(
                stringResource(R.string.kind_exam),
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )
        }
    }

    if (reminder != null && !done) {
        QuickSnoozeSection(
            snap = snap,
            task = task,
            reminder = reminder,
            onSnooze = onSnooze,
        )
    }

    OverlayFieldRow(
        icon = Icons.Rounded.Schedule,
        iconTint =
            when {
                overdue -> scheme.error
                hasWhenInfo -> scheme.primary
                else -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
        onClick = { whenPanel = true },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                whenPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (hasWhenInfo) FontWeight.Medium else FontWeight.Normal,
                color =
                    when {
                        overdue -> scheme.error
                        hasWhenInfo -> scheme.primary
                        else -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
            )
            if (whenSecondary != null) {
                Text(
                    whenSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }

    val locationHint = stringResource(R.string.field_location)
    OverlayFieldRow(
        icon = Icons.Rounded.Place,
        iconTint = if (location.isNotBlank()) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.6f),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = location,
                onValueChange = { location = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = locationHint },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
            )
            if (location.isEmpty()) {
                Text(
                    locationHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }

    val noteHint = stringResource(R.string.label_note)
    OverlayFieldRow(
        icon = Icons.Rounded.Notes,
        iconTint = if (note.isNotBlank()) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.6f),
        alignTop = true,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = noteHint },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant),
                cursorBrush = SolidColor(scheme.primary),
            )
            if (note.isEmpty()) {
                Text(
                    noteHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }

    var prioMenu by remember { mutableStateOf(false) }
    val noFocusPopup = rememberNoFocusPopup()
    val prioOptions = listOf(3 to R.string.priority_3, 2 to R.string.priority_2, 1 to R.string.priority_1, 0 to R.string.priority_0)
    Box {
        OverlayFieldRow(
            icon = Icons.Rounded.Flag,
            iconTint = colors.priorityColor(priority) ?: scheme.onSurfaceVariant.copy(alpha = 0.6f),
            onClick = { prioMenu = true },
        ) {
            Text(
                if (priority > 0) stringResource(prioOptions.first { it.first == priority }.second) else stringResource(R.string.label_priority),
                style = MaterialTheme.typography.bodyMedium,
                color = if (priority > 0) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(prioMenu, { prioMenu = false }, offset = DpOffset(OverlayLeadW, 0.dp), properties = noFocusPopup) {
            prioOptions.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = colors.priorityColor(v) ?: scheme.onSurfaceVariant) },
                    trailingIcon = if (v == priority) ({ Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }) else null,
                    onClick = {
                        priority = v
                        prioMenu = false
                    },
                )
            }
        }
    }

    if (snap.lists.isNotEmpty()) {
        OverlayFieldRow(
            icon = Icons.Rounded.Inbox,
            iconTint = if (listName != null) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
            onClick = { listPanel = !listPanel },
        ) {
            Text(
                listName?.let { "#$it" } ?: stringResource(R.string.label_list),
                style = MaterialTheme.typography.bodyMedium,
                color = if (listName != null) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(
            visible = listPanel,
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 132.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = OverlayLeadW, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipPill(stringResource(R.string.widget_overlay_no_list), on = listId == null) {
                    listId = null
                    listPanel = false
                }
                snap.lists.sortedBy { it.sort }.forEach { l ->
                    ChipPill("#${l.name}", on = listId == l.id) {
                        listId = l.id
                        listPanel = false
                    }
                }
            }
        }
    }

    if (course != null) {
        OverlayFieldRow(
            icon = null,
            iconTint = Color.Unspecified,
            lead = {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.fg(course.colorIndex)),
                )
            },
        ) {
            Text(
                course.name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (subtasks.isNotEmpty()) {
        Text(
            stringResource(R.string.widget_overlay_subtasks),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = OverlayLeadW, top = 8.dp, bottom = 2.dp),
        )
        subtasks.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().alpha(if (sub.done) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MintCheck(
                    checked = sub.done,
                    onChange = { onToggleDone(sub, it, false) },
                    tint = colors.priorityColor(sub.priority),
                )
                Text(
                    sub.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    val primaryInteraction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OverlayIconBtn(
            icon = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.delete),
            tint = scheme.error,
            iconSize = 20.dp,
            onClick = onDelete,
        )
        Spacer(Modifier.weight(1f))
        OverlayIconBtn(
            icon = Icons.Rounded.OpenInFull,
            contentDescription = stringResource(R.string.widget_overlay_expand_app),
            tint = scheme.onSurfaceVariant,
            iconSize = 20.dp,
            onClick = { saveAndMaybe(closeAfter = false, thenOpenUri = DeepLinks.taskUri(task.id)) },
        )
        AnimatedVisibility(
            visible = isDirty,
            enter = fadeIn(MintTheme.spec()) + expandHorizontally(MintTheme.spec()),
            exit = fadeOut(MintTheme.spec()) + shrinkHorizontally(MintTheme.spec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                val canSave = title.trim().isNotEmpty()
                Button(
                    onClick = { saveAndMaybe(closeAfter = true) },
                    enabled = canSave,
                    interactionSource = primaryInteraction,
                    modifier = Modifier.heightIn(min = 44.dp).mintPressScale(primaryInteraction, 0.96f, enabled = canSave),
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (whenPanel) {
        WhenPanel(
            initial = whenPick,
            defaultDate = today,
            defaultMinutes = defaultBlockMinutes,
            initialRemindList = remindList,
            initialPersistent = persistent,
            initialFreq = freq,
            initialInterval = interval,
            initialUntil = until,
            initialByDays = byDays,
            initialByMonths = byMonths,
            semesterEnd = snap.currentSemester?.let { SemesterCalendar(it).weekEnd(it.totalWeeks) },
            onDismiss = { whenPanel = false },
            onPick = { r ->
                whenPick = r.whenPick
                remindList = r.remindMinutesList
                persistent = r.remindPersistent
                freq = r.freq
                interval = r.interval
                until = r.until
                byDays = r.byDays
                byMonths = r.byMonths
                whenPanel = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSnoozeSection(
    snap: Snapshot,
    task: Task,
    reminder: ReminderOverlayContext,
    onSnooze: (LocalDateTime, LocalDateTime?) -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val openedAt = remember(task.id, reminder.notifId, reminder.targetAtEpochMs) { LocalDateTime.now() }
    val targetAt =
        remember(task, snap, openedAt, reminder.targetAtEpochMs) {
            SnoozePlanner.resolveTargetAt(task, snap, openedAt, reminder.targetAtEpochMs)
        }
    val plan: SnoozePlan =
        remember(openedAt, targetAt) {
            SnoozePlanner.plan(openedAt, targetAt)
        }
    var customOpen by remember(task.id) { mutableStateOf(false) }
    var customText by remember(task.id) { mutableStateOf("") }
    var timeDialOpen by remember { mutableStateOf(false) }

    val customMinutes = customText.toIntOrNull()
    val customValid = customMinutes != null && customMinutes in 1..SnoozePlanner.MAX_CUSTOM_MINUTES

    val modeCaption =
        when (plan.mode) {
            SnoozeScaleMode.LINEAR -> {
                val span = formatSnoozeSpan(ctx, plan.remainingMinutes ?: 1)
                stringResource(R.string.notif_snooze_mode_linear, span)
            }

            SnoozeScaleMode.EXPONENTIAL -> {
                val span = formatSnoozeSpan(ctx, plan.remainingMinutes ?: 1)
                stringResource(R.string.notif_snooze_mode_exp, span)
            }

            SnoozeScaleMode.STANDARD -> {
                if (plan.targetAt != null) {
                    stringResource(R.string.notif_snooze_mode_past)
                } else {
                    stringResource(R.string.notif_snooze_mode_standard)
                }
            }
        }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OverlayFieldRow(
            icon = Icons.Rounded.Alarm,
            iconTint = scheme.primary,
        ) {
            Text(
                stringResource(R.string.notif_quick_snooze_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                modeCaption,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = OverlayLeadW, top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            plan.options.forEach { opt ->
                val label = formatSnoozeOptionLabel(opt)
                val desc = "$label（${ctx.dueLabel(opt.triggerAt, true, today)}）"
                ChipPill(
                    label = label,
                    on = false,
                    role = Role.Button,
                    modifier = Modifier.semantics { contentDescription = desc },
                ) {
                    val liveNow = LocalDateTime.now()
                    val fireAt =
                        when {
                            opt.isAtTarget || opt.isTomorrowMorning -> {
                                if (opt.triggerAt.isAfter(liveNow)) opt.triggerAt else liveNow.plusMinutes(1)
                            }

                            else -> {
                                liveNow.plusMinutes(opt.delayMinutes.toLong())
                            }
                        }
                    onSnooze(fireAt, targetAt)
                }
            }
            ChipPill(
                label = stringResource(R.string.detail_editor_remind_custom),
                on = customOpen,
                role = Role.Button,
            ) {
                customOpen = !customOpen
            }
        }

        AnimatedVisibility(
            visible = customOpen,
            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
        ) {
            val previewText =
                if (customValid) {
                    val previewAt = openedAt.plusMinutes(customMinutes.toLong())
                    val whenStr = ctx.dueLabel(previewAt, true, today)
                    val remainAfter = targetAt?.let { ChronoUnit.MINUTES.between(previewAt, it).toInt() }
                    if (remainAfter != null && remainAfter > 0) {
                        stringResource(R.string.notif_snooze_preview_before_start, whenStr, formatSnoozeSpan(ctx, remainAfter))
                    } else {
                        stringResource(R.string.notif_snooze_preview, whenStr)
                    }
                } else if (customText.isNotEmpty()) {
                    stringResource(R.string.notif_snooze_custom_invalid, SnoozePlanner.MAX_CUSTOM_MINUTES)
                } else {
                    stringResource(R.string.notif_snooze_custom_hint, SnoozePlanner.MAX_CUSTOM_MINUTES)
                }

            Column(
                modifier = Modifier.fillMaxWidth().padding(start = OverlayLeadW, top = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TonalField(
                    label = stringResource(R.string.notif_snooze_custom_minutes_label),
                    value = customText,
                    onChange = { customText = it.filter { ch -> ch.isDigit() }.take(5) },
                    number = true,
                    isError = customText.isNotEmpty() && !customValid,
                    supporting = previewText,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val timePickInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = { timeDialOpen = true },
                        interactionSource = timePickInteraction,
                        modifier = Modifier.heightIn(min = 44.dp).mintPressScale(timePickInteraction, 0.96f),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.surfaceContainerHighest,
                                contentColor = scheme.onSurface,
                            ),
                    ) {
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.notif_snooze_pick_time))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { customOpen = false }) {
                        Text(stringResource(R.string.cancel), color = scheme.onSurfaceVariant)
                    }
                    val okInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            val m = customMinutes ?: return@Button
                            onSnooze(LocalDateTime.now().plusMinutes(m.toLong()), targetAt)
                        },
                        enabled = customValid,
                        interactionSource = okInteraction,
                        modifier = Modifier.heightIn(min = 44.dp).mintPressScale(okInteraction, 0.96f, enabled = customValid),
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.45f),
        )
    }

    if (timeDialOpen) {
        TimeDialSheet(
            initial = (targetAt ?: openedAt.plusMinutes(30)).toLocalTime(),
            onDismiss = { timeDialOpen = false },
        ) { picked ->
            val liveNow = LocalDateTime.now()
            val todayAt = liveNow.toLocalDate().atTime(picked)
            val resolved = if (todayAt.isAfter(liveNow)) todayAt else todayAt.plusDays(1)
            timeDialOpen = false
            onSnooze(resolved, targetAt)
        }
    }
}

@Composable
private fun formatSnoozeOptionLabel(opt: SnoozeOption): String =
    when {
        opt.isAtTarget -> stringResource(R.string.notif_snooze_at_target, opt.triggerAt.hm())
        opt.isTomorrowMorning -> stringResource(R.string.notif_snooze_tomorrow_morning)
        opt.delayMinutes < 60 -> stringResource(R.string.notif_snooze_after_minutes, opt.delayMinutes)
        opt.delayMinutes % 1440 == 0 -> stringResource(R.string.notif_snooze_after_days, opt.delayMinutes / 1440)
        opt.delayMinutes % 60 == 0 -> stringResource(R.string.notif_snooze_after_hours, opt.delayMinutes / 60)
        else -> stringResource(R.string.notif_snooze_after_minutes, opt.delayMinutes)
    }

private fun formatSnoozeSpan(
    ctx: Context,
    minutes: Int,
): String =
    when {
        minutes < 60 -> {
            ctx.getString(R.string.dur_min, minutes)
        }

        minutes < 1440 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) ctx.getString(R.string.dur_h, h) else ctx.getString(R.string.dur_h_m, h, m)
        }

        else -> {
            val d = minutes / 1440
            val h = (minutes % 1440) / 60
            if (h == 0) ctx.getString(R.string.notif_snooze_span_days, d) else ctx.getString(R.string.notif_snooze_span_days_hours, d, h)
        }
    }

@Composable
private fun OverlayFieldRow(
    icon: ImageVector?,
    iconTint: Color,
    onClick: (() -> Unit)? = null,
    alignTop: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val rowMod =
        Modifier
            .fillMaxWidth()
            .heightIn(min = OverlayRowMinH)
            .let { mod ->
                if (onClick != null) {
                    mod
                        .clip(RoundedCornerShape(12.dp))
                        .mintClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick)
                } else {
                    mod
                }
            }
    Row(
        modifier = rowMod,
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(OverlayLeadW).heightIn(min = OverlayRowMinH),
            contentAlignment = Alignment.Center,
        ) {
            if (lead != null) {
                lead()
            } else if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun OverlayIconBtn(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun OverlayEventCard(
    event: Event,
    onOpenInApp: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val today = remember { LocalDate.now() }
    val startDay = event.start.toLocalDate()
    val timeText =
        if (event.allDay) {
            stringResource(R.string.all_day)
        } else {
            "${ctx.dueLabel(event.start, true, today)}–${event.end.hm()}"
        }
    val locationText =
        event.location
            .takeIf { it.isNotBlank() }
            ?.let(::formatAllRooms)
            .orEmpty()
    val repeatText =
        event.recurrence?.let { r ->
            freqLabel(r.freq, r.until, r.interval, r.byDays, startDay, byMonths = r.byMonths)
        }
    val kindLabel =
        when (event.kind) {
            EventKind.EXAM -> stringResource(R.string.kind_exam)
            EventKind.COUNTDOWN -> stringResource(R.string.kind_countdown)
            EventKind.NORMAL -> null
        }

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.fg(event.colorIndex)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            event.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (kindLabel != null) {
            Spacer(Modifier.width(8.dp))
            Tag(kindLabel, colors.bg(event.colorIndex), colors.fg(event.colorIndex))
        }
    }
    Column(
        modifier = Modifier.padding(start = 20.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(15.dp), tint = scheme.onSurfaceVariant)
            Text(
                timeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
            )
        }
        if (locationText.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.Place,
                    null,
                    modifier = Modifier.padding(top = 2.dp).size(15.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
            }
        }
        if (repeatText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Repeat, null, modifier = Modifier.size(15.dp), tint = scheme.onSurfaceVariant)
                Text(
                    repeatText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (event.note.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.Notes,
                    null,
                    modifier = Modifier.padding(top = 2.dp).size(15.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    event.note.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val openInteraction = remember { MutableInteractionSource() }
    Button(
        onClick = onOpenInApp,
        interactionSource = openInteraction,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).mintPressScale(openInteraction, 0.96f),
    ) {
        Icon(Icons.Rounded.OpenInFull, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.widget_overlay_open_in_app))
    }
}
