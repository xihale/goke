package app.goke.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.goke.DeepLinks
import app.goke.GokeApp
import app.goke.MainActivity
import app.goke.R
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.Event
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.WeekSets
import app.goke.core.todo.QuickAdd
import app.goke.core.todo.QuickBatchItem
import app.goke.core.todo.QuickTokenKind
import app.goke.data.DarkMode
import app.goke.gokeApp
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.MintCheck
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.Tag
import app.goke.ui.components.mintClickable
import app.goke.ui.components.mintPressScale
import app.goke.ui.dueLabel
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.screens.QuickAddComposer
import app.goke.ui.screens.QuickOverride
import app.goke.ui.screens.QuickPanel
import app.goke.ui.screens.TokenHighlight
import app.goke.ui.screens.WhenPanel
import app.goke.ui.screens.WhenPick
import app.goke.ui.screens.buildQuickTasks
import app.goke.ui.screens.freqLabel
import app.goke.ui.screens.tokenRanges
import app.goke.ui.theme.GokeTheme
import app.goke.ui.theme.MintTheme
import app.goke.ui.theme.priorityColor
import app.goke.ui.weekdayShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import android.graphics.Color as AndroidColor

private enum class OverlayQuickPanel { PRIORITY, LIST }

internal sealed interface OverlayTarget {
    data class Add(
        val initialCourseId: String? = null,
        val initialText: String = "",
    ) : OverlayTarget

    data class CourseDetail(
        val courseId: String,
        val dateStr: String? = null,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
    ) : OverlayTarget

    data class TaskDetail(
        val taskId: String,
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        targetState.value = parseTarget(intent)
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
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            GokeTheme(settings, transparentBackground = true) {
                WidgetOverlayRoot(
                    app = app,
                    ready = ready,
                    snap = snap,
                    target = targetState.value,
                    onChangeTarget = { targetState.value = it },
                    onOpenInApp = { uri ->
                        startActivity(
                            Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        finishNoAnim()
                    },
                    onDismiss = { finishNoAnim() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetState.value = parseTarget(intent)
    }

    private fun finishNoAnim() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_MODE = "overlay_mode"
        private const val EXTRA_COURSE_ID = "course_id"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_START_PERIOD = "start_period"
        private const val EXTRA_END_PERIOD = "end_period"
        private const val MODE_ADD = "add"
        private const val MODE_COURSE = "course"
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
                    OverlayTarget.TaskDetail(taskId)
                }

                mode == MODE_EVENT && !eventId.isNullOrBlank() -> {
                    OverlayTarget.EventDetail(eventId)
                }

                !taskId.isNullOrBlank() -> {
                    OverlayTarget.TaskDetail(taskId)
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
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val settings by app.settings.flow.collectAsState()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var externalDismissHook by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(target) {
        externalDismissHook = null
        closing = false
    }

    fun handleBackdropDismiss() {
        if (closing) return
        val hook = externalDismissHook
        if (hook != null) hook() else onDismiss()
    }

    BackHandler { handleBackdropDismiss() }

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
            } finally {
                closing = false
            }
        }
    }

    val isAdd = target is OverlayTarget.Add
    val sheetShape = RoundedCornerShape(topStart = if (isAdd) 24.dp else 28.dp, topEnd = if (isAdd) 24.dp else 28.dp)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(target) { detectTapGestures { handleBackdropDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = shown && ready,
            enter = fadeIn(tween(140)) + slideInVertically(MintTheme.spec()) { it / 4 },
            exit = fadeOut(tween(100)) + slideOutVertically(MintTheme.spec()) { it / 4 },
        ) {
            val live = remember(snap) { snap.live() }
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .pointerInput(Unit) { detectTapGestures {} },
                shape = sheetShape,
                color = scheme.surfaceContainerLow,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    if (!isAdd) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 36.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(scheme.outlineVariant.copy(alpha = 0.65f)),
                            )
                        }
                    }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = if (isAdd) 10.dp else 8.dp,
                                    bottom = if (isAdd) 10.dp else 16.dp,
                                ),
                    ) {
                        when (target) {
                            is OverlayTarget.Add -> {
                                OverlayQuickAddCard(
                                    snap = live,
                                    defaultBlockMinutes = settings.defaultBlockMinutes,
                                    initialCourseId = target.initialCourseId,
                                    initialText = target.initialText,
                                    registerDismissHook = { externalDismissHook = it },
                                    onExpand = { draft ->
                                        onOpenInApp(DeepLinks.addUri(draft.ifBlank { null }, target.initialCourseId))
                                    },
                                    onCommit = { cs, msg -> commitAndRefresh(cs, msg, closeAfter = true) },
                                    onDismiss = onDismiss,
                                )
                            }

                            is OverlayTarget.CourseDetail -> {
                                val course = live.courses.firstOrNull { it.id == target.courseId }
                                if (course == null) {
                                    LaunchedEffect(Unit) { onOpenInApp(DeepLinks.timetableUri()) }
                                } else {
                                    OverlayCourseCard(
                                        snap = live,
                                        course = course,
                                        dateStr = target.dateStr,
                                        startPeriod = target.startPeriod,
                                        endPeriod = target.endPeriod,
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
                                        onAddCourseTask = {
                                            onChangeTarget(OverlayTarget.Add(initialCourseId = course.id))
                                        },
                                        onExpand = { onOpenInApp(DeepLinks.courseUri(course.id)) },
                                    )
                                }
                            }

                            is OverlayTarget.TaskDetail -> {
                                val task = live.tasks.firstOrNull { it.id == target.taskId }
                                if (task == null) {
                                    LaunchedEffect(Unit) { onDismiss() }
                                } else if (WidgetKit.isParcel(task)) {
                                    OverlayParcelCard(
                                        snap = live,
                                        defaultBlockMinutes = settings.defaultBlockMinutes,
                                        task = task,
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
                                        registerDismissHook = { externalDismissHook = it },
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
                                val event = live.events.firstOrNull { it.id == target.eventId }
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
        }
    }
}

@Composable
private fun OverlayQuickAddCard(
    snap: Snapshot,
    defaultBlockMinutes: Int,
    initialCourseId: String?,
    initialText: String,
    registerDismissHook: ((() -> Unit)?) -> Unit,
    onExpand: (String) -> Unit,
    onCommit: (ChangeSet, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val now = remember { LocalDateTime.now() }
    val linkedCourse = remember(snap, initialCourseId) { initialCourseId?.let { id -> snap.courses.firstOrNull { it.id == id } } }

    var text by remember(initialText) { mutableStateOf(initialText) }
    var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
    var whenPanel by remember { mutableStateOf(false) }
    var panelWhen by remember { mutableStateOf<WhenPick?>(null) }
    var panelRemind by remember { mutableStateOf<Int?>(null) }
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

    val hasDraft = text.isNotBlank() || panelWhen != null || panelRemind != null || panelFreq != null
    LaunchedEffect(hasDraft) {
        registerDismissHook {
            if (hasDraft) confirmDiscard = true else onDismiss()
        }
    }

    fun rewriteToken(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        val removeRanges =
            tokenRanges(text, parsed?.tokens.orEmpty())
                .filter { it.first.kind in kinds }
                .map { it.second }
                .sortedByDescending { it.first }
        var t = text
        for (r in removeRanges) {
            t = t.substring(0, r.first) + " " + t.substring(r.last + 1)
        }
        t = t.replace(Regex("\\s+"), " ").trim()
        text =
            when {
                add == null -> t
                t.isEmpty() -> "$add "
                else -> "$t $add"
            }
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
            if (panelWhen != null || panelRemind != null || panelFreq != null) {
                QuickOverride(
                    whenPick = panelWhen,
                    remind = panelRemind,
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
                                        t.copy(courseId = linkedCourse.id, dueBeforeNextClass = t.due == null),
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
        text = text,
        onTextChange = { text = it },
        parsed = parsed,
        highlight = highlight,
        focusRequester = focusRequester,
        toolPanel = toolPanel,
        onToolPanelChange = { toolPanel = it },
        panelWhen = panelWhen,
        panelRemind = panelRemind,
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
        onClearWhenOverrides = {
            panelWhen = null
            panelRemind = null
            panelPersistent = false
            panelFreq = null
            panelUntil = null
            panelByDays = emptySet()
            panelByMonths = emptySet()
        },
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
            initialRemind = panelRemind,
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
                panelRemind = r.remindMinutes
                panelPersistent = r.remindPersistent
                panelFreq = r.freq
                panelInterval = r.interval
                panelUntil = r.until
                panelByDays = r.byDays
                panelByMonths = r.byMonths
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
    onAddCourseTask: () -> Unit,
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
            if (cal != null) {
                val st = cal.startTime(sp)?.hm()
                val et = cal.endTime(ep)?.hm()
                if (st != null && et != null) append(" $st–$et")
            }
            append(" · ")
            append(stringResource(R.string.periods_range, sp, ep))
        }
    val metaLine =
        listOfNotNull(
            course.room.takeIf { it.isNotBlank() }?.let { ctx.formatRooms(it, maxInline = 4) },
            course.teacher.takeIf { it.isNotBlank() },
            WeekSets.format(course.weeks).takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    val courseTasks =
        remember(snap.tasks, course.id) {
            snap.tasks.filter { !it.done && it.parentId == null && it.courseId == course.id }
        }

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
            Text(
                course.note,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
            onClick = onAddCourseTask,
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

    Text(
        place,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = scheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
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
            task.note,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
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
            initialRemind = task.remindMinutes,
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
        remindMinutes = r.remindMinutes,
        remindPersistent = r.remindMinutes != null && r.remindPersistent,
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
    registerDismissHook: ((() -> Unit)?) -> Unit,
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
    var note by remember(task.id) { mutableStateOf(task.note) }
    var priority by remember(task.id) { mutableIntStateOf(task.priority) }
    var listId by remember(task.id) { mutableStateOf(task.listId) }
    var done by remember(task.id, task.done) { mutableStateOf(task.done) }
    var whenPick by remember(task.id) { mutableStateOf(initWhen) }
    var remind by remember(task.id) { mutableStateOf(task.remindMinutes) }
    var persistent by remember(task.id) { mutableStateOf(task.remindPersistent) }
    var freq by remember(task.id) { mutableStateOf(task.repeat?.freq) }
    var interval by remember(task.id) { mutableIntStateOf(task.repeat?.interval ?: 1) }
    var until by remember(task.id) { mutableStateOf(task.repeat?.until) }
    var byDays by remember(task.id) { mutableStateOf(task.repeat?.byDays ?: emptySet()) }
    var byMonths by remember(task.id) { mutableStateOf(task.repeat?.byMonths ?: emptySet()) }

    var toolPanel by remember { mutableStateOf<OverlayQuickPanel?>(null) }
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
            note != task.note ||
            priority != task.priority ||
            listId != task.listId ||
            done != task.done ||
            remind != task.remindMinutes ||
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
        return task.copy(
            title = title.trim(),
            note = note,
            priority = priority,
            listId = listId,
            done = done,
            doneAt = if (done) (task.doneAt ?: ms) else null,
            dropped = if (!done) false else task.dropped,
            blockStart = blockStartAt,
            blockMinutes = blockMinutes,
            due = dueAt,
            dueHasTime = hasTime,
            remindMinutes = remind,
            remindPersistent = remind != null && persistent,
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

    LaunchedEffect(isDirty, title, note, priority, listId, done, whenPick, remind, persistent, freq, interval, until, byDays, byMonths) {
        registerDismissHook {
            if (isDirty && title.trim().isNotEmpty()) saveAndMaybe(closeAfter = true) else onDismiss()
        }
    }

    val whenText =
        remember(whenPick, freq, interval, until, byDays, byMonths, today) {
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
    val whenChipText =
        listOfNotNull(whenText, freqShort).joinToString(" · ").ifEmpty { stringResource(R.string.detail_editor_pick_date) }
    val overdue =
        whenPick?.let { w ->
            !done &&
                when {
                    w.start != null -> LocalDateTime.of(w.date, w.start).isBefore(now)
                    else -> w.date.isBefore(today)
                }
        } == true

    val prioText =
        when (priority) {
            3 -> stringResource(R.string.priority_3)
            2 -> stringResource(R.string.priority_2)
            1 -> stringResource(R.string.priority_1)
            else -> stringResource(R.string.label_priority)
        }
    val prioFg = colors.priorityColor(priority) ?: scheme.onSurfaceVariant

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
                    .padding(vertical = 4.dp)
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
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 2.dp),
    ) {
        BasicTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant),
            cursorBrush = SolidColor(scheme.primary),
            maxLines = 4,
        )
        if (note.isEmpty()) {
            Text(
                stringResource(R.string.label_note),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OverlayActionChip(
            icon = Icons.Rounded.Event,
            label = whenChipText,
            bg =
                when {
                    overdue -> scheme.errorContainer
                    whenText != null || freq != null -> scheme.primaryContainer
                    else -> scheme.surfaceContainerHighest
                },
            fg =
                when {
                    overdue -> scheme.error
                    whenText != null || freq != null -> scheme.onPrimaryContainer
                    else -> scheme.onSurfaceVariant
                },
            onClick = { whenPanel = true },
        )
        OverlayActionChip(
            icon = Icons.Rounded.Flag,
            label = prioText,
            bg = scheme.surfaceContainerHighest,
            fg = prioFg,
            onClick = { toolPanel = if (toolPanel == OverlayQuickPanel.PRIORITY) null else OverlayQuickPanel.PRIORITY },
        )
        if (snap.lists.isNotEmpty()) {
            OverlayActionChip(
                icon = Icons.Rounded.Inbox,
                label = listName?.let { "#$it" } ?: stringResource(R.string.label_list),
                bg = if (listName != null) scheme.primaryContainer else scheme.surfaceContainerHighest,
                fg = if (listName != null) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                onClick = { toolPanel = if (toolPanel == OverlayQuickPanel.LIST) null else OverlayQuickPanel.LIST },
            )
        }
        if (course != null) {
            Tag(course.name, colors.bg(course.colorIndex), colors.fg(course.colorIndex))
        }
    }

    AnimatedVisibility(
        visible = toolPanel == OverlayQuickPanel.PRIORITY,
        enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
        exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
    ) {
        SegmentedPill(
            options =
                listOf(
                    3 to stringResource(R.string.priority_3),
                    2 to stringResource(R.string.priority_2),
                    1 to stringResource(R.string.priority_1),
                    0 to stringResource(R.string.priority_0),
                ),
            selected = priority,
            onSelect = { v ->
                priority = v
                toolPanel = null
            },
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    AnimatedVisibility(
        visible = toolPanel == OverlayQuickPanel.LIST && snap.lists.isNotEmpty(),
        enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
        exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
    ) {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 132.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipPill(stringResource(R.string.widget_overlay_no_list), on = listId == null) {
                listId = null
                toolPanel = null
            }
            snap.lists.sortedBy { it.sort }.forEach { l ->
                ChipPill("#${l.name}", on = listId == l.id) {
                    listId = l.id
                    toolPanel = null
                }
            }
        }
    }

    if (subtasks.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.widget_overlay_subtasks),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
        )
        subtasks.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().alpha(if (sub.done) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MintCheck(
                    checked = sub.done,
                    onChange = { onToggleDone(sub, it, false) },
                    size = 18.dp,
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

    Spacer(Modifier.height(14.dp))

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
        Spacer(Modifier.width(6.dp))
        val canSave = title.trim().isNotEmpty()
        val primaryEnabled = if (isDirty) canSave else true
        Button(
            onClick = {
                if (isDirty) {
                    saveAndMaybe(closeAfter = true)
                } else {
                    onToggleDone(task, !task.done, !task.done)
                }
            },
            enabled = primaryEnabled,
            interactionSource = primaryInteraction,
            modifier = Modifier.heightIn(min = 44.dp).mintPressScale(primaryInteraction, 0.96f, enabled = primaryEnabled),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(
                    when {
                        isDirty -> R.string.save
                        task.done -> R.string.widget_overlay_task_reopen
                        else -> R.string.widget_overlay_task_done
                    },
                ),
            )
        }
    }

    if (whenPanel) {
        WhenPanel(
            initial = whenPick,
            defaultDate = today,
            defaultMinutes = defaultBlockMinutes,
            initialRemind = remind,
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
                remind = r.remindMinutes
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
private fun OverlayActionChip(
    icon: ImageVector,
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .background(bg)
                .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = fg)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    val timeText =
        if (event.allDay) {
            stringResource(R.string.all_day)
        } else {
            "${ctx.dueLabel(event.start, true, today)}–${event.end.hm()}"
        }
    val metaLine =
        listOfNotNull(
            timeText,
            event.location.takeIf { it.isNotBlank() }?.let { ctx.formatRooms(it, maxInline = 4) },
        ).joinToString(" · ")

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
    }
    Text(
        metaLine,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
    )
    if (event.note.isNotBlank()) {
        Text(
            event.note,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
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
