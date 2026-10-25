package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Task
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.TimePickerDlg
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.monthDayWeekday
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 「快速添加」带进详情页的草稿：已输入/已解析的字段不重输。 */
data class TaskDraft(
    val title: String = "",
    val due: LocalDateTime? = null,
    val dueHasTime: Boolean = false,
    val priority: Int = 0,
    val listId: String? = null,
    val tags: String = "",
    val startDate: LocalDate? = null,
    val courseId: String? = null,
    val beforeClass: Boolean = false,
)

@Composable
fun TaskDetailScreen(
    id: String?,
    draft: TaskDraft? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val settings = settingsState()
    // 不 remember：勾完成/撤销后要跟着快照刷新，而不是停在进场时的旧值
    val existing = id?.let { i -> snap.tasks.firstOrNull { it.id == i } }
    if (id != null && existing == null) {
        // 记录已被删除：composition 期间不能改导航状态，延后到 effect 里返回
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // 新建时自动聚焦标题
    val titleFocus = remember { FocusRequester() }

    var title by remember { mutableStateOf(existing?.title ?: draft?.title.orEmpty()) }
    // 标题已由草稿预填时不抢焦点弹键盘
    LaunchedEffect(existing == null) { if (existing == null && title.isBlank()) titleFocus.requestFocus() }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var listId by remember { mutableStateOf(existing?.listId ?: draft?.listId) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: draft?.priority ?: 0) }
    var dueDate by remember { mutableStateOf(existing?.due?.toLocalDate() ?: draft?.due?.toLocalDate()) }
    var dueTime by remember {
        mutableStateOf(
            existing?.due?.toLocalTime().takeIf { existing?.dueHasTime == true }
                ?: draft?.due?.toLocalTime().takeIf { draft?.dueHasTime == true },
        )
    }
    var estimate by remember { mutableStateOf(existing?.estimateMinutes?.toString().orEmpty()) }
    var repeat by remember { mutableStateOf(existing?.repeat?.freq) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var courseId by remember { mutableStateOf(existing?.courseId ?: draft?.courseId) }
    var beforeClass by remember { mutableStateOf(existing?.dueBeforeNextClass ?: draft?.beforeClass ?: false) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: draft?.startDate) }
    var tags by remember {
        mutableStateOf(
            existing
                ?.tags
                ?.joinToString(" ")
                .orEmpty()
                .ifEmpty { draft?.tags.orEmpty() },
        )
    }
    var attachments by remember { mutableStateOf(existing?.attachments.orEmpty()) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }

    fun buildTask(): Task {
        val base = existing ?: Task(Ids.next(), "", sortOrder = (snap.tasks.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        val due = dueDate?.let { d -> LocalDateTime.of(d, dueTime ?: LocalTime.of(23, 59)) }
        return base.copy(
            title = title.trim(),
            note = note,
            listId = listId,
            priority = priority,
            due = due,
            dueHasTime = dueDate != null && dueTime != null,
            estimateMinutes = estimate.toIntOrNull(),
            repeat = repeat?.let { Recurrence(it) },
            remindMinutes = remind,
            courseId = courseId,
            dueBeforeNextClass = beforeClass && courseId != null,
            // 时间块不再手动排:有时间的待办由课表/今日直接按 due 上格子,这里只保留旧数据不动
            blockStart = existing?.blockStart,
            blockMinutes = existing?.blockMinutes,
            tags = tags.split(' ', ',', '，', '#').map { it.trim() }.filter { it.isNotEmpty() },
            attachments = attachments,
            startDate = startDate,
        )
    }

    // 有未保存修改时拦截返回（含系统返回手势），先确认再放弃；保存按钮直接走 onBack 不经此路
    var confirmDiscard by remember { mutableStateOf(false) }

    fun isDirty(): Boolean =
        if (existing == null) {
            title.isNotBlank() || note.isNotBlank() || dueDate != null || tags.isNotBlank() || priority != 0 || estimate.isNotBlank()
        } else {
            buildTask() != existing
        }

    fun goBack() {
        if (isDirty()) confirmDiscard = true else onBack()
    }
    BackHandler { goBack() }

    MintScreen(
        title = stringResource(if (existing == null) R.string.new_task else R.string.task_detail),
        onBack = { goBack() },
        actions = {
            if (existing != null) {
                MintCheck(existing.done, { d -> toggle(actions, snap, existing, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, existing.title)) })
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
            }
            Button(
                onClick = {
                    val t = buildTask()
                    actions.commit(Edits.save(snap, t, ctx.getString(if (existing == null) R.string.add_task_desc else R.string.edit_task_desc, t.title), System.currentTimeMillis()), ctx.getString(R.string.saved_toast))
                    onBack()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldText(stringResource(R.string.label_title), title, { title = it }, modifier = Modifier.focusRequester(titleFocus))
            FieldText(stringResource(R.string.label_note), note, { note = it }, singleLine = false, minLines = 2)

            FormSection(stringResource(R.string.section_when)) {
                ValueRow(stringResource(R.string.label_due_date), dueDate?.let { ctx.monthDayWeekday(it) } ?: stringResource(R.string.none), { pick = "due" })
                AnimatedVisibility(
                    visible = dueDate != null,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Column {
                        ValueRow(stringResource(R.string.label_due_time), dueTime?.hm() ?: stringResource(R.string.none), { pick = "dueTime" })
                        TextButton(onClick = {
                            dueDate = null
                            dueTime = null
                        }) { Text(stringResource(R.string.clear_due)) }
                    }
                }
                Text(stringResource(R.string.label_repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(
                    listOf<Pair<Freq?, String>>(null to stringResource(R.string.repeat_none), Freq.DAILY to stringResource(R.string.repeat_daily), Freq.WEEKLY to stringResource(R.string.repeat_weekly), Freq.MONTHLY to stringResource(R.string.repeat_monthly)),
                    repeat,
                    { repeat = it },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_remind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(
                    listOf<Pair<Int?, String>>(null to stringResource(R.string.remind_none), 0 to stringResource(R.string.remind_at_due), 10 to stringResource(R.string.remind_10m), 60 to stringResource(R.string.remind_1h), 1440 to stringResource(R.string.remind_1d)),
                    remind,
                    { remind = it },
                )
            }

            FormSection(stringResource(R.string.section_course)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_related_course), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { courseMenu = true }) { Text(snap.courses.firstOrNull { it.id == courseId }?.name ?: stringResource(R.string.none)) }
                        DropdownMenu(courseMenu, { courseMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = {
                                courseId = null
                                beforeClass = false
                                courseMenu = false
                            })
                            snap.courses.distinctBy { it.name }.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                    courseId = c.id
                                    courseMenu = false
                                })
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = courseId != null,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.due_before_next_class), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        androidx.compose.material3.Switch(beforeClass, { beforeClass = it })
                    }
                }
            }

            FormSection(stringResource(R.string.section_organize)) {
                FieldText(stringResource(R.string.label_estimate), estimate, { estimate = it.filter(Char::isDigit) }, number = true)
                Text(stringResource(R.string.label_list), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(listOf<Pair<String?, String>>(null to stringResource(R.string.none)) + snap.lists.map { it.id to it.name }, listId, { listId = it })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipChoice(listOf(0 to stringResource(R.string.priority_0), 1 to stringResource(R.string.priority_1), 2 to stringResource(R.string.priority_2), 3 to stringResource(R.string.priority_3)), priority, { priority = it })
                Spacer(Modifier.height(8.dp))
                FieldText(stringResource(R.string.label_tags), tags, { tags = it }, placeholder = stringResource(R.string.tags_hint))
            }

            FormSection(stringResource(R.string.section_attachments)) {
                AttachmentEditor(
                    attachments,
                    onChange = { attachments = it },
                    onPasteText = { note = if (note.isBlank()) it else "$note\n$it" },
                )
            }

            if (existing != null) {
                val subs = TaskLogic.subtasks(snap.tasks, existing.id)
                FormSection(stringResource(R.string.section_subtasks)) {
                    Column(Modifier.animateContentSize(MintTheme.spec()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        subs.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                MintCheck(s.done, { d -> toggle(actions, snap, s, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, s.title)) })
                                Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { actions.commit(Edits.delete(snap, s, ctx.getString(R.string.delete_desc, s.title), System.currentTimeMillis()), ctx.getString(R.string.deleted_toast)) }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        FieldText(stringResource(R.string.add_subtask), newSub, { newSub = it }, Modifier.weight(1f))
                        TextButton(onClick = {
                            val sub = Task(Ids.next(), newSub.trim(), parentId = existing.id, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: 0) + 1)
                            actions.commit(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, sub.title), System.currentTimeMillis()))
                            newSub = ""
                        }, enabled = newSub.isNotBlank()) { Text(stringResource(R.string.add)) }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (pick) {
        "due" -> DatePickerDlg(dueDate ?: LocalDate.now(), { pick = null }) { dueDate = it }
        "dueTime" -> TimePickerDlg(dueTime ?: LocalTime.of(18, 0), { pick = null }) { dueTime = it }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            stringResource(R.string.discard_title),
            stringResource(R.string.discard_text),
            stringResource(R.string.discard),
            {
                confirmDiscard = false
                onBack()
            },
            { confirmDiscard = false },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete_task_title),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                actions.commit(Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.title), System.currentTimeMillis()), ctx.getString(R.string.deleted_toast))
                onBack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}
