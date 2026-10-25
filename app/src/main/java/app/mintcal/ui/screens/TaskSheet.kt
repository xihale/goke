package app.mintcal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Task
import app.mintcal.ui.components.ChipChoice
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.components.MintCheck
import app.mintcal.ui.components.TimePickerDlg
import app.mintcal.ui.dueLabel
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 点待办弹出的底部抽屉：常用字段一屏改完，关闭时自动保存（一次编辑只产生一条可撤销记录）。
 * 要改重复 / 提醒 / 附件等再点「展开全部」进整页。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(
    taskId: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val snap = liveSnapshot()
    val snapNow = rememberUpdatedState(snap)
    val existing = snap.tasks.firstOrNull { it.id == taskId && it.deletedAt == null }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var dueDate by remember { mutableStateOf(existing?.due?.toLocalDate()) }
    var dueTime by remember { mutableStateOf(existing?.takeIf { it.dueHasTime }?.due?.toLocalTime()) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 0) }
    var listId by remember { mutableStateOf(existing?.listId) }
    var courseId by remember { mutableStateOf(existing?.courseId) }
    var beforeClass by remember { mutableStateOf(existing?.dueBeforeNextClass ?: false) }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString(" ").orEmpty()) }
    var estimate by remember { mutableStateOf(existing?.estimateMinutes) }
    var repeat by remember { mutableStateOf(existing?.repeat?.freq) }
    var remind by remember { mutableStateOf(existing?.remindMinutes) }
    var newSub by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<String?>(null) }
    var courseMenu by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    // 以「此刻最新的」任务为底做字段覆盖，期间别处的改动（如勾完成）不会被冲掉
    fun buildNext(cur: Task): Task {
        val due = dueDate?.let { d -> LocalDateTime.of(d, dueTime ?: LocalTime.of(23, 59)) }
        return cur.copy(
            title = title.trim(),
            note = note,
            due = due,
            dueHasTime = dueDate != null && dueTime != null,
            priority = priority,
            listId = listId,
            courseId = courseId,
            dueBeforeNextClass = beforeClass && courseId != null,
            tags = tags.split(' ', ',', '，', '#').map { it.trim() }.filter { it.isNotEmpty() },
            estimateMinutes = estimate,
            // 重复规则没改就原样保留（间隔 / 星期 / 截止等细节在整页里改）
            repeat = if (repeat == cur.repeat?.freq) cur.repeat else repeat?.let { Recurrence(it) },
            remindMinutes = remind,
        )
    }

    fun saveOnce() {
        if (saved) return
        saved = true
        val s = snapNow.value
        val cur = s.tasks.firstOrNull { it.id == taskId && it.deletedAt == null } ?: return
        if (title.isBlank()) return
        val next = buildNext(cur)
        if (next == cur) return
        actions.commit(
            Edits.save(s, next, ctx.getString(R.string.edit_task_desc, next.title), System.currentTimeMillis()),
            ctx.getString(R.string.saved_toast),
        )
    }

    DisposableEffect(Unit) { onDispose { saveOnce() } }

    fun close(then: () -> Unit = {}) {
        scope.launch {
            saveOnce()
            sheet.hide()
            onDismiss()
            then()
        }
    }

    if (existing == null) {
        // 记录已被删除（如从别处删掉）：直接收起
        DisposableEffect(Unit) {
            onDismiss()
            onDispose { }
        }
        return
    }

    val today = LocalDate.now()
    ModalBottomSheet(
        onDismissRequest = {
            saveOnce()
            onDismiss()
        },
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MintCheck(existing.done, { d -> toggle(actions, snap, existing, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, existing.title)) })
                TextField(
                    title,
                    { title = it },
                    Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleMedium,
                    placeholder = { Text(stringResource(R.string.label_title)) },
                    colors = transparentFieldColors(),
                )
            }

            // 日期：一排快捷，不必先开日历
            Text(stringResource(R.string.label_due_date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun setDate(d: LocalDate?) {
                    dueDate = d
                    if (d == null) dueTime = null
                    beforeClass = false
                }
                DateChip(stringResource(R.string.today), dueDate == today && !beforeClass) { setDate(today) }
                DateChip(stringResource(R.string.tomorrow), dueDate == today.plusDays(1) && !beforeClass) { setDate(today.plusDays(1)) }
                val weekend = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                DateChip(stringResource(R.string.date_weekend), dueDate == weekend && !beforeClass) { setDate(weekend) }
                val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                DateChip(stringResource(R.string.date_next_monday), dueDate == monday && !beforeClass) { setDate(monday) }
                val custom = dueDate != null && dueDate !in listOf(today, today.plusDays(1), weekend, monday) && !beforeClass
                DateChip(
                    if (custom) ctx.dueLabel(dueDate!!.atStartOfDay(), false, today) else stringResource(R.string.date_pick),
                    custom,
                ) { pick = "date" }
                if (dueDate != null) {
                    DateChip(dueTime?.hm() ?: stringResource(R.string.date_set_time), dueTime != null) { pick = "time" }
                }
                if (snap.courses.isNotEmpty()) {
                    Box {
                        val courseName = snap.courses.firstOrNull { it.id == courseId }?.name
                        DateChip(
                            if (beforeClass && courseName != null) stringResource(R.string.before_next_class_short, courseName) else stringResource(R.string.date_before_class),
                            beforeClass && courseId != null,
                        ) { courseMenu = true }
                        DropdownMenu(courseMenu, { courseMenu = false }) {
                            snap.courses.distinctBy { it.name }.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                    courseId = c.id
                                    beforeClass = true
                                    courseMenu = false
                                })
                            }
                        }
                    }
                }
                if (dueDate != null || beforeClass) {
                    DateChip(stringResource(R.string.date_none), false) {
                        dueDate = null
                        dueTime = null
                        beforeClass = false
                    }
                }
            }

            Text(stringResource(R.string.label_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to R.string.priority_0, 1 to R.string.priority_1, 2 to R.string.priority_2, 3 to R.string.priority_3).forEach { (v, label) ->
                    DateChip(stringResource(label), priority == v) { priority = v }
                }
            }

            if (snap.lists.isNotEmpty()) {
                Text(stringResource(R.string.label_list), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChip(stringResource(R.string.none), listId == null) { listId = null }
                    snap.lists.sortedBy { it.sort }.forEach { l -> DateChip(l.name, listId == l.id) { listId = l.id } }
                }
            }

            Text(stringResource(R.string.label_estimate), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val presets = listOf(15, 30, 60, 120)
            ChipChoice(
                (listOf<Int?>(null) + presets + listOfNotNull(estimate?.takeIf { it !in presets })).map { v ->
                    v to (v?.let { ctx.getString(R.string.minutes_n, it) } ?: ctx.getString(R.string.none))
                },
                estimate,
                { estimate = it },
            )
            Text(stringResource(R.string.label_repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipChoice(
                listOf<Pair<Freq?, String>>(
                    null to stringResource(R.string.repeat_none),
                    Freq.DAILY to stringResource(R.string.repeat_daily),
                    Freq.WEEKLY to stringResource(R.string.repeat_weekly),
                    Freq.MONTHLY to stringResource(R.string.repeat_monthly),
                ),
                repeat,
                { repeat = it },
            )
            Text(stringResource(R.string.label_remind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipChoice(
                listOf<Pair<Int?, String>>(
                    null to stringResource(R.string.remind_none),
                    0 to stringResource(R.string.remind_at_due),
                    10 to stringResource(R.string.remind_10m),
                    60 to stringResource(R.string.remind_1h),
                    1440 to stringResource(R.string.remind_1d),
                ),
                remind,
                { remind = it },
            )
            TextField(
                tags,
                { tags = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.label_tags) + " · " + stringResource(R.string.tags_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = transparentFieldColors(),
            )

            TextField(
                note,
                { note = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.label_note)) },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
                colors = transparentFieldColors(),
            )

            val subs = TaskLogic.subtasks(snap.tasks, existing.id)
            if (subs.isNotEmpty()) {
                Column {
                    subs.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MintCheck(s.done, { d -> toggle(actions, snap, s, d, LocalDateTime.now(), ctx.getString(R.string.done_toast, s.title)) })
                            Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    newSub,
                    { newSub = it },
                    Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.add_subtask)) },
                    singleLine = true,
                    colors = transparentFieldColors(),
                )
                TextButton(enabled = newSub.isNotBlank(), onClick = {
                    val sub = Task(Ids.next(), newSub.trim(), parentId = existing.id, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: 0) + 1)
                    actions.commit(Edits.save(snap, sub, ctx.getString(R.string.add_task_desc, sub.title), System.currentTimeMillis()))
                    newSub = ""
                }) { Text(stringResource(R.string.add)) }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    saved = true
                    actions.commit(
                        Edits.delete(snap, existing, ctx.getString(R.string.delete_desc, existing.title), System.currentTimeMillis()),
                        ctx.getString(R.string.deleted_toast),
                    )
                    close()
                }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                if (!existing.done) {
                    TextButton(onClick = {
                        val ms = System.currentTimeMillis()
                        val cur = snapNow.value.tasks.firstOrNull { it.id == taskId } ?: existing
                        saved = true
                        actions.commit(
                            Edits.save(snap, buildNext(cur).copy(done = true, dropped = true, doneAt = ms), ctx.getString(R.string.todo_drop_desc, existing.title), ms),
                            ctx.getString(R.string.todo_dropped_toast, 1),
                        )
                        close()
                    }) { Text(stringResource(R.string.todo_drop)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    close { nav.push(Route.TaskDetail(existing.id)) }
                }) {
                    Icon(Icons.Rounded.OpenInFull, null, Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.sheet_expand))
                }
            }
        }
    }

    when (pick) {
        "date" -> {
            DatePickerDlg(dueDate ?: today, { pick = null }) {
                dueDate = it
                beforeClass = false
            }
        }

        "time" -> {
            TimePickerDlg(dueTime ?: LocalTime.of(18, 0), { pick = null }) { dueTime = it }
        }
    }
}

@Composable
private fun DateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) }, shape = CircleShape)
}

@Composable
internal fun transparentFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
