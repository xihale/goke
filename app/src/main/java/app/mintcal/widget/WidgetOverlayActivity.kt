package app.mintcal.widget

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.mintcal.DeepLinks
import app.mintcal.MainActivity
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Course
import app.mintcal.core.model.Event
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickBatchItem
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.data.DarkMode
import app.mintcal.mintApp
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.dueLabel
import app.mintcal.ui.formatRooms
import app.mintcal.ui.hm
import app.mintcal.ui.screens.TokenHighlight
import app.mintcal.ui.screens.buildQuickTasks
import app.mintcal.ui.screens.tokenRanges
import app.mintcal.ui.theme.MintCalTheme
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
import app.mintcal.ui.weekdayShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import android.graphics.Color as AndroidColor

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
 * 直接浮在桌面之上，支持快速添加、查看课程/快递/待办详情并就地完成、复制取件码、顺延改期；
 * 点击右上角「展开」再跳进主应用对应页面。
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
        val app = mintApp
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
            MintCalTheme(settings, transparentBackground = true) {
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
    app: MintCalApp,
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
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    BackHandler { onDismiss() }

    fun commitAndRefresh(
        cs: ChangeSet,
        toast: String? = null,
        closeAfter: Boolean = false,
    ) {
        scope.launch {
            app.repo.commit(cs)
            app.scope.launch(Dispatchers.Default) {
                WidgetKit.refreshAllWidgets(app)
            }
            if (!toast.isNullOrBlank()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
                }
            }
            if (closeAfter) onDismiss()
        }
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.34f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        .clickable(interactionSource = cardInteraction, indication = null, onClick = {}),
                shape = RoundedCornerShape(28.dp),
                color = MintTheme.card,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    when (target) {
                        is OverlayTarget.Add -> {
                            OverlayQuickAddCard(
                                snap = live,
                                initialCourseId = target.initialCourseId,
                                initialText = target.initialText,
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
                                    onAddCourseTask = {
                                        onChangeTarget(OverlayTarget.Add(initialCourseId = course.id))
                                    },
                                    onExpand = { onOpenInApp(DeepLinks.courseUri(course.id)) },
                                    onDismiss = onDismiss,
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
                                    task = task,
                                    onMarkDone = {
                                        val msg = ctx.getString(R.string.widget_overlay_parcel_done_toast, task.title)
                                        commitAndRefresh(
                                            Edits.toggleTask(live, task, true, System.currentTimeMillis(), LocalDateTime.now(), task.title),
                                            toast = msg,
                                            closeAfter = true,
                                        )
                                    },
                                    onPostponeTomorrow = {
                                        val tomorrow = LocalDate.now().plusDays(1)
                                        commitAndRefresh(
                                            Edits.reschedule(
                                                listOf(task),
                                                tomorrow,
                                                ctx.getString(R.string.edit_task_desc, task.title),
                                                System.currentTimeMillis(),
                                            ),
                                            toast = ctx.getString(R.string.widget_overlay_postpone_toast),
                                            closeAfter = true,
                                        )
                                    },
                                    onExpand = { onOpenInApp(DeepLinks.taskUri(task.id)) },
                                    onDismiss = onDismiss,
                                )
                            } else {
                                OverlayTaskCard(
                                    snap = live,
                                    task = task,
                                    onToggleDone = { t, done ->
                                        val desc = ctx.getString(R.string.edit_task_desc, t.title)
                                        val isMain = t.id == task.id
                                        val msg =
                                            if (done) ctx.getString(R.string.widget_overlay_task_done_toast, t.title) else null
                                        commitAndRefresh(
                                            Edits.toggleTask(live, t, done, System.currentTimeMillis(), LocalDateTime.now(), desc),
                                            toast = msg,
                                            closeAfter = isMain && done,
                                        )
                                    },
                                    onCyclePriority = {
                                        val nextP = (task.priority + 1) % 4
                                        val cs =
                                            Edits.updateTasks(
                                                listOf(task),
                                                ctx.getString(R.string.edit_task_desc, task.title),
                                                System.currentTimeMillis(),
                                            ) { it.copy(priority = nextP, updatedAt = System.currentTimeMillis()) }
                                        commitAndRefresh(cs, closeAfter = false)
                                    },
                                    onPostponeTomorrow = {
                                        val tomorrow = LocalDate.now().plusDays(1)
                                        commitAndRefresh(
                                            Edits.reschedule(
                                                listOf(task),
                                                tomorrow,
                                                ctx.getString(R.string.edit_task_desc, task.title),
                                                System.currentTimeMillis(),
                                            ),
                                            toast = ctx.getString(R.string.widget_overlay_postpone_toast),
                                            closeAfter = true,
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
                                    onExpand = { onOpenInApp(DeepLinks.taskUri(task.id)) },
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
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayHeaderActions(
    expandDescription: String,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onExpand) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = expandDescription, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayQuickAddCard(
    snap: Snapshot,
    initialCourseId: String?,
    initialText: String,
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
        var cs = buildQuickTasks(snap, items, defaultListId = null, defaultBucket = null, now = now, desc = desc)
        if (linkedCourse != null) {
            cs =
                cs.copy(
                    changes =
                        cs.changes.map { ch ->
                            if (ch is app.mintcal.core.change.Change.Add && ch.after is Task) {
                                val t = ch.after as Task
                                if (t.courseId == null && t.parentId == null) {
                                    app.mintcal.core.change.Change.Add(
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.widget_overlay_quick_add), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (linkedCourse != null) {
                Spacer(Modifier.height(4.dp))
                Tag(linkedCourse.name, colors.bg(linkedCourse.colorIndex), colors.fg(linkedCourse.colorIndex))
            }
        }
        OverlayHeaderActions(
            expandDescription = stringResource(R.string.widget_overlay_expand_app),
            onExpand = { onExpand(text) },
            onDismiss = onDismiss,
        )
    }

    Spacer(Modifier.height(8.dp))

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (text.isEmpty()) {
            Text(
                stringResource(R.string.quick_add_prompt),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            visualTransformation = highlight,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            maxLines = 4,
        )
    }

    Spacer(Modifier.height(10.dp))

    val todayToken = stringResource(R.string.today)
    val tomorrowToken = stringResource(R.string.tomorrow)
    val prioHighToken = "!${stringResource(R.string.priority_3)}"
    val prioMidToken = "!${stringResource(R.string.priority_2)}"
    val prioHighLabel = stringResource(R.string.overlay_priority_high)
    val prioMidLabel = stringResource(R.string.overlay_priority_medium)
    val hasToday = parsed?.due?.toLocalDate() == now.toLocalDate()
    val hasTomorrow = parsed?.due?.toLocalDate() == now.toLocalDate().plusDays(1)
    val prio = parsed?.priority ?: 0

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipPill(todayToken, on = hasToday) {
            rewriteToken(setOf(QuickTokenKind.DATE), if (hasToday) null else todayToken)
        }
        ChipPill(tomorrowToken, on = hasTomorrow) {
            rewriteToken(setOf(QuickTokenKind.DATE), if (hasTomorrow) null else tomorrowToken)
        }
        ChipPill(prioHighLabel, on = prio == 3) {
            rewriteToken(setOf(QuickTokenKind.PRIORITY), if (prio == 3) null else prioHighToken)
        }
        ChipPill(prioMidLabel, on = prio == 2) {
            rewriteToken(setOf(QuickTokenKind.PRIORITY), if (prio == 2) null else prioMidToken)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { submit() },
            enabled = text.isNotBlank(),
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.widgets_add_action))
        }
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
    onAddCourseTask: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
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
    val courseTasks =
        remember(snap.tasks, course.id) {
            snap.tasks.filter { !it.done && it.parentId == null && it.courseId == course.id }
        }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(22.dp)
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
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
        }
    }

    Text(
        timeText,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 2.dp),
    )

    Spacer(Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (course.room.isNotBlank()) {
            OverlayInfoRow(stringResource(R.string.label_room), ctx.formatRooms(course.room, maxInline = 4))
        }
        if (course.teacher.isNotBlank()) {
            OverlayInfoRow(stringResource(R.string.label_teacher), course.teacher)
        }
        OverlayInfoRow(stringResource(R.string.week_label), WeekSets.format(course.weeks))
        if (course.note.isNotBlank()) {
            Text(
                course.note,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }

    if (courseTasks.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.widget_overlay_course_tasks),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        courseTasks.take(4).forEach { t ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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

    Spacer(Modifier.height(14.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onAddCourseTask, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.course_todos_add))
        }
        FilledTonalButton(
            onClick = onExpand,
            modifier = Modifier.heightIn(min = 48.dp),
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayParcelCard(
    snap: Snapshot,
    task: Task,
    onMarkDone: () -> Unit,
    onPostponeTomorrow: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val code = WidgetKit.parcelCode(task) ?: stringResource(R.string.widget_parcel_title)
    val place = WidgetKit.cleanTitleWithCode(ctx, task)
    val due = task.due?.let { ctx.dueLabel(it, task.dueHasTime, today) }
    val overdue = task.due != null && task.due!!.isBefore(now)

    fun copyCode() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("parcel_code", code))
        Toast.makeText(ctx, ctx.getString(R.string.parcel_code_copied, code), Toast.LENGTH_SHORT).show()
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalShipping, null, modifier = Modifier.size(20.dp), tint = scheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.widget_parcel_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        OverlayHeaderActions(
            expandDescription = stringResource(R.string.widget_overlay_expand_app),
            onExpand = onExpand,
            onDismiss = onDismiss,
        )
    }

    Spacer(Modifier.height(8.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.primaryContainer.copy(alpha = 0.45f))
                .mintClickable(pressedScale = 0.98f, role = Role.Button) { copyCode() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
            Text(
                place,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (due != null) {
                Text(
                    due,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = { copyCode() }) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.parcel_copy_code, code),
                tint = scheme.primary,
            )
        }
    }

    if (task.note.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            task.note,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }

    Spacer(Modifier.height(14.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onMarkDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.widget_overlay_parcel_done))
        }
        FilledTonalButton(
            onClick = onPostponeTomorrow,
            modifier = Modifier.heightIn(min = 48.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ),
        ) {
            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.widget_overlay_postpone_tomorrow))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayTaskCard(
    snap: Snapshot,
    task: Task,
    onToggleDone: (Task, Boolean) -> Unit,
    onCyclePriority: () -> Unit,
    onPostponeTomorrow: () -> Unit,
    onDelete: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = MintTheme.colors
    val today = remember { LocalDate.now() }
    val subtasks = remember(snap.tasks, task.id) { snap.tasks.filter { it.parentId == task.id } }
    val listName = remember(snap.lists, task.listId) { snap.lists.firstOrNull { it.id == task.listId }?.name }
    val course = remember(snap.courses, task.courseId) { snap.courses.firstOrNull { it.id == task.courseId } }

    val whenText =
        remember(task, today) {
            when {
                task.blockStart != null -> {
                    val start = task.blockStart!!
                    val end = start.plusMinutes((task.blockMinutes ?: 30).toLong())
                    "${ctx.dueLabel(start, true, today)}–${end.hm()}"
                }

                task.due != null -> {
                    ctx.dueLabel(task.due!!, task.dueHasTime, today)
                }

                else -> {
                    null
                }
            }
        }
    val prioLabel =
        when (task.priority) {
            3 -> stringResource(R.string.priority_3)
            2 -> stringResource(R.string.priority_2)
            1 -> stringResource(R.string.priority_1)
            else -> stringResource(R.string.widget_overlay_priority_none)
        }
    val prioFg = colors.priorityColor(task.priority) ?: scheme.onSurfaceVariant

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MintCheck(
            checked = task.done,
            onChange = { onToggleDone(task, it) },
            tint = colors.priorityColor(task.priority),
        )
        Text(
            task.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OverlayHeaderActions(
            expandDescription = stringResource(R.string.widget_overlay_expand_app),
            onExpand = onExpand,
            onDismiss = onDismiss,
        )
    }

    Spacer(Modifier.height(6.dp))

    FlowRow(
        modifier = Modifier.padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (whenText != null) {
            Tag(whenText, scheme.primaryContainer, scheme.onPrimaryContainer)
        }
        Box(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .mintClickable(pressedScale = 0.96f, role = Role.Button, onClick = onCyclePriority),
        ) {
            Tag(prioLabel, scheme.surfaceContainerHighest, prioFg)
        }
        if (listName != null) {
            Tag("#$listName", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        }
        if (course != null) {
            Tag(course.name, colors.bg(course.colorIndex), colors.fg(course.colorIndex))
        }
    }

    if (task.note.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            task.note,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }

    if (subtasks.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.widget_overlay_subtasks),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MintCheck(
                    checked = sub.done,
                    onChange = { onToggleDone(sub, it) },
                    size = 18.dp,
                    tint = colors.priorityColor(sub.priority),
                )
                Text(
                    sub.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sub.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onToggleDone(task, !task.done) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.widget_overlay_task_done))
        }
        FilledTonalButton(
            onClick = onPostponeTomorrow,
            modifier = Modifier.heightIn(min = 48.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ),
        ) {
            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.widget_overlay_postpone_tomorrow))
        }
        TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.delete), color = scheme.error)
        }
    }
}

@Composable
private fun OverlayEventCard(
    event: Event,
    onOpenInApp: () -> Unit,
    onDismiss: () -> Unit,
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(22.dp)
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
        OverlayHeaderActions(
            expandDescription = stringResource(R.string.widget_overlay_open_in_app),
            onExpand = onOpenInApp,
            onDismiss = onDismiss,
        )
    }
    Text(
        timeText,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 4.dp),
    )
    if (event.location.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        OverlayInfoRow(stringResource(R.string.label_room), ctx.formatRooms(event.location, maxInline = 4))
    }
    if (event.note.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            event.note,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(onClick = onOpenInApp, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.OpenInFull, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.widget_overlay_open_in_app))
    }
}

@Composable
private fun OverlayInfoRow(
    label: String,
    value: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
