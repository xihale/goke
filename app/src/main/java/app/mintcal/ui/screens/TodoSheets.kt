package app.mintcal.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.ui.LocalActions
import app.mintcal.ui.components.DatePickerDlg
import app.mintcal.ui.dueLabel
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.theme.priorityColor
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var pickDate by remember { mutableStateOf(false) }
    val title = if (tasks.size == 1) tasks[0].title else ctx.getString(R.string.todo_selected_count, tasks.size)

    fun finish(
        cs: ChangeSet,
        msg: String,
    ) {
        actions.commit(cs, msg)
        onDone()
        scope.launch {
            sheet.hide()
            onDismiss()
        }
    }

    fun moveTo(d: LocalDate?) {
        val desc = ctx.getString(R.string.todo_reschedule_desc, title)
        finish(Edits.reschedule(tasks, d, desc, System.currentTimeMillis()), ctx.getString(R.string.todo_moved_toast, tasks.size))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
            SheetOption(Icons.Rounded.Today, stringResource(R.string.today)) { moveTo(today) }
            SheetOption(Icons.Rounded.WbSunny, stringResource(R.string.tomorrow)) { moveTo(today.plusDays(1)) }
            SheetOption(Icons.Rounded.Weekend, stringResource(R.string.date_weekend)) { moveTo(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))) }
            SheetOption(Icons.Rounded.Upcoming, stringResource(R.string.date_next_monday)) { moveTo(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))) }
            SheetOption(Icons.Rounded.CalendarMonth, stringResource(R.string.date_pick)) { pickDate = true }
            SheetOption(Icons.Rounded.Inbox, stringResource(R.string.todo_no_date)) { moveTo(null) }
            // 普通操作与危险操作隔开
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            SheetOption(Icons.Rounded.Block, stringResource(R.string.todo_drop)) {
                finish(
                    Edits.dropTasks(tasks, ctx.getString(R.string.todo_drop_desc, title), System.currentTimeMillis()),
                    ctx.getString(R.string.todo_dropped_toast, tasks.size),
                )
            }
            SheetOption(Icons.Rounded.Delete, stringResource(R.string.delete), MaterialTheme.colorScheme.error) {
                val now = System.currentTimeMillis()
                val desc = ctx.getString(R.string.delete_desc, title)
                finish(mergeChangeSets(desc, tasks.map { Edits.delete(snap, it, desc, now) }), ctx.getString(R.string.deleted_toast))
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip12()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = color)
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = now.toLocalDate()

    // 全部处理完就自动收起
    LaunchedEffect(tasks.isEmpty()) {
        if (tasks.isEmpty()) {
            sheet.hide()
            onDismiss()
        }
    }

    fun move(
        list: List<Task>,
        d: LocalDate,
    ) {
        actions.commit(Edits.reschedule(list, d, ctx.getString(R.string.todo_reschedule_desc, list.first().title), System.currentTimeMillis()))
    }

    fun drop(list: List<Task>) {
        actions.commit(Edits.dropTasks(list, ctx.getString(R.string.todo_drop_desc, list.first().title), System.currentTimeMillis()))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.todo_tidy_title, tasks.size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.todo_tidy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            if (tasks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { move(tasks, today) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.todo_all_to_today)) }
                    FilledTonalButton(onClick = { drop(tasks) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.todo_all_drop)) }
                }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { t ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        t.due?.let {
                            Text(ctx.dueLabel(it, t.dueHasTime, today), style = MaterialTheme.typography.bodySmall, color = MintTheme.colors.removed)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { move(listOf(t), today) }) { Text(stringResource(R.string.today)) }
                            TextButton(onClick = { move(listOf(t), today.plusDays(1)) }) { Text(stringResource(R.string.tomorrow)) }
                            TextButton(onClick = { drop(listOf(t)) }) { Text(stringResource(R.string.todo_drop)) }
                        }
                    }
                }
            }
        }
    }
}

/** 批量条图标按钮：全局无水波，这里自己补按下缩放。 */
@Composable
private fun BulkIconButton(
    icon: ImageVector,
    desc: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, MintTheme.spec(), label = "bulk-press")
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Icon(icon, desc, tint = tint)
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
    Surface(modifier, shape = CircleShape, color = MintTheme.card, shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BulkIconButton(Icons.Rounded.Event, stringResource(R.string.todo_reschedule), onClick = onReschedule)
            Box {
                BulkIconButton(Icons.Rounded.FolderOpen, stringResource(R.string.label_list)) { listMenu = true }
                DropdownMenu(listMenu, { listMenu = false }) {
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
                BulkIconButton(Icons.Rounded.Flag, stringResource(R.string.label_priority)) { priorityMenu = true }
                DropdownMenu(priorityMenu, { priorityMenu = false }) {
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
            BulkIconButton(Icons.Rounded.Check, stringResource(R.string.todo_complete), onClick = onDone)
            BulkIconButton(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, onClick = onDelete)
        }
    }
}
