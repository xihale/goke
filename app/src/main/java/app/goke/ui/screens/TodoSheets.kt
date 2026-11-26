package app.goke.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import app.goke.ui.LocalActions
import app.goke.ui.components.DatePickerDlg
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.mintClickable
import app.goke.ui.components.rememberNoFocusPopup
import app.goke.ui.dueLabel
import app.goke.ui.icons.Block
import app.goke.ui.icons.CalendarMonth
import app.goke.ui.icons.Event
import app.goke.ui.icons.Flag
import app.goke.ui.icons.FolderOpen
import app.goke.ui.icons.Inbox
import app.goke.ui.icons.Today
import app.goke.ui.icons.Upcoming
import app.goke.ui.icons.WbSunny
import app.goke.ui.icons.Weekend
import app.goke.ui.theme.MintTheme
import app.goke.ui.theme.priorityColor
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** 改期抽屉：左滑与多选「改期」共用。一次操作 = 一个变更集（一次撤销）。 */
@Composable
internal fun RescheduleSheet(
    tasks: List<Task>,
    snap: Snapshot,
    onDismiss: () -> Unit,
    onDone: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val today = LocalDate.now()
    var pickDate by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val title = if (tasks.size == 1) tasks[0].title else ctx.getString(R.string.todo_selected_count, tasks.size)

    fun finish(
        cs: ChangeSet,
        msg: String,
    ) {
        if (submitting) return
        submitting = true
        actions.commit(cs, msg)
        onDone()
        onDismiss()
    }

    fun moveTo(d: LocalDate?) {
        val desc =
            if (tasks.size > 1) {
                ctx.getString(R.string.todo_reschedule_bulk_desc, tasks.size)
            } else {
                ctx.getString(R.string.todo_reschedule_desc, title)
            }
        val nowDt = LocalDateTime.now()
        finish(
            Edits.reschedule(tasks, d, desc, System.currentTimeMillis(), clearTimeIfPast = if (d == today) nowDt else null),
            ctx.getString(R.string.todo_moved_toast, tasks.size),
        )
    }

    MintBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tasks.size == 1) {
                    tasks[0].due?.let { due ->
                        Text(
                            ctx.dueLabel(due, tasks[0].dueHasTime, today),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            SheetOption(Icons.Rounded.Today, stringResource(R.string.today)) { moveTo(today) }
            SheetOption(Icons.Rounded.WbSunny, stringResource(R.string.tomorrow)) { moveTo(today.plusDays(1)) }
            SheetOption(Icons.Rounded.Weekend, stringResource(R.string.date_weekend)) { moveTo(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))) }
            SheetOption(Icons.Rounded.Upcoming, stringResource(R.string.date_next_monday)) { moveTo(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))) }
            SheetOption(Icons.Rounded.CalendarMonth, stringResource(R.string.date_pick)) { if (!submitting) pickDate = true }
            SheetOption(Icons.Rounded.Inbox, stringResource(R.string.todo_no_date)) { moveTo(null) }
            // 普通操作与危险操作靠纵向留白呼吸隔开
            Spacer(Modifier.height(12.dp))
            SheetOption(Icons.Rounded.Block, stringResource(R.string.todo_drop)) {
                finish(
                    Edits.dropTasks(tasks, ctx.getString(R.string.todo_drop_desc, title), System.currentTimeMillis()),
                    ctx.getString(R.string.todo_dropped_toast, tasks.size),
                )
            }
            SheetOption(Icons.Rounded.Delete, stringResource(R.string.delete), MaterialTheme.colorScheme.error) {
                val now = System.currentTimeMillis()
                val desc = ctx.getString(R.string.delete_desc, title)
                finish(mergeChangeSets(desc, tasks.map { Edits.delete(snap, it, desc, now) }), desc)
            }
        }
    }
    if (pickDate) {
        DatePickerDlg(today, { pickDate = false }) { moveTo(it) }
    }
}

@Composable
private fun SheetOption(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val iconTint = if (color == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip12()
            .mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconTint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

private fun Modifier.clip12(): Modifier = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

/** 逾期整理：每条一键改到今天 / 明天 / 不做了，也可整批处理。 */
@Composable
internal fun MorningSheet(
    tasks: List<Task>,
    now: LocalDateTime,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val today = now.toLocalDate()
    var lastTasks by remember { mutableStateOf(tasks) }
    var handledIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tasks) {
        if (tasks.isNotEmpty()) lastTasks = tasks
    }
    val displayTasks = if (tasks.isNotEmpty()) tasks else lastTasks
    val remainingTasks = remember(tasks, handledIds) { tasks.filter { it.id !in handledIds } }

    // 全部处理完就自动收起
    LaunchedEffect(tasks.isEmpty()) {
        if (tasks.isEmpty()) {
            onDismiss()
        }
    }

    fun move(
        list: List<Task>,
        d: LocalDate,
    ) {
        val pending = list.filter { it.id !in handledIds }
        if (pending.isEmpty()) return
        handledIds = handledIds + pending.map { it.id }
        val desc =
            if (pending.size > 1) {
                ctx.getString(R.string.todo_reschedule_bulk_desc, pending.size)
            } else {
                ctx.getString(R.string.todo_reschedule_desc, pending.first().title)
            }
        val clearedPastTime =
            d == today &&
                pending.any { t ->
                    t.dueHasTime && t.due?.toLocalTime()?.let { LocalDateTime.of(d, it).isBefore(now) } == true
                }
        actions.commit(
            Edits.reschedule(pending, d, desc, System.currentTimeMillis(), clearTimeIfPast = if (d == today) now else null),
            if (clearedPastTime) {
                ctx.getString(R.string.todo_today_untimed_toast)
            } else {
                ctx.getString(R.string.todo_moved_to_toast, ctx.dueLabel(d.atStartOfDay(), false, today))
            },
        )
    }

    fun drop(list: List<Task>) {
        val pending = list.filter { it.id !in handledIds }
        if (pending.isEmpty()) return
        handledIds = handledIds + pending.map { it.id }
        val desc =
            if (pending.size > 1) {
                ctx.getString(R.string.todo_drop_bulk_desc, pending.size)
            } else {
                ctx.getString(R.string.todo_drop_desc, pending.first().title)
            }
        actions.commit(
            Edits.dropTasks(pending, desc, System.currentTimeMillis()),
            ctx.getString(R.string.todo_dropped_toast, pending.size),
        )
    }

    MintBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.todo_tidy_title, displayTasks.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (displayTasks.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { move(remainingTasks, today) },
                        enabled = remainingTasks.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) { Text(stringResource(R.string.todo_all_to_today)) }
                    FilledTonalButton(
                        onClick = { drop(remainingTasks) },
                        enabled = remainingTasks.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) { Text(stringResource(R.string.todo_all_drop)) }
                }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayTasks, key = { it.id }) { t ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            t.due?.let {
                                Text(
                                    ctx.dueLabel(it, t.dueHasTime, today),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MintTheme.colors.removed,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            val itemEnabled = tasks.isNotEmpty() && t.id !in handledIds
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TidyActionPill(
                                    label = stringResource(R.string.today),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    bold = true,
                                    enabled = itemEnabled,
                                ) { move(listOf(t), today) }
                                TidyActionPill(
                                    label = stringResource(R.string.tomorrow),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    enabled = itemEnabled,
                                ) { move(listOf(t), today.plusDays(1)) }
                                TidyActionPill(
                                    label = stringResource(R.string.todo_drop),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    enabled = itemEnabled,
                                ) { drop(listOf(t)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TidyActionPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    bold: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(containerColor)
                .mintClickable(enabled = enabled, pressedScale = 0.96f, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Medium else null,
            color = contentColor,
        )
    }
}

/** 批量条动作按钮：图标 + 简洁单行文字，触控区保底 48dp 并带按压回弹。 */
@Composable
private fun BulkActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(min = 56.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(18.dp))
                .mintClickable(pressedScale = 0.92f, role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 多选时的底部操作条。 */
@Composable
internal fun BulkBar(
    snap: Snapshot,
    onReschedule: () -> Unit,
    onList: (String?) -> Unit,
    onPriority: (Int) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    val noFocusPopup = rememberNoFocusPopup()
    Surface(modifier, shape = CircleShape, color = MintTheme.card, shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BulkActionButton(Icons.Rounded.Event, stringResource(R.string.todo_reschedule), onClick = onReschedule)
            Box {
                BulkActionButton(Icons.Rounded.FolderOpen, stringResource(R.string.label_list)) { listMenu = true }
                DropdownMenu(listMenu, { listMenu = false }, properties = noFocusPopup) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = {
                        listMenu = false
                        onList(null)
                    })
                    snap.lists.sortedBy { it.sort }.forEach { l ->
                        DropdownMenuItem(text = { Text(l.name) }, onClick = {
                            listMenu = false
                            onList(l.id)
                        })
                    }
                }
            }
            Box {
                BulkActionButton(Icons.Rounded.Flag, stringResource(R.string.label_priority)) { priorityMenu = true }
                DropdownMenu(priorityMenu, { priorityMenu = false }, properties = noFocusPopup) {
                    listOf(3 to R.string.priority_3, 2 to R.string.priority_2, 1 to R.string.priority_1, 0 to R.string.priority_0).forEach { (v, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = {
                                // 旗子色与行内/圈粗同源；0 档无语义色，用次要文字色
                                Icon(Icons.Rounded.Flag, null, tint = MintTheme.colors.priorityColor(v) ?: MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = {
                                priorityMenu = false
                                onPriority(v)
                            },
                        )
                    }
                }
            }
            BulkActionButton(Icons.Rounded.Check, stringResource(R.string.todo_complete), onClick = onDone)
            BulkActionButton(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, onClick = onDelete)
        }
    }
}
