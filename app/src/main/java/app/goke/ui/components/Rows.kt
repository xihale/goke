package app.goke.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.agenda.AgendaItem
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.TaskItem
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.change.Unify
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.time.CourseProgress
import app.goke.core.time.TimePhase
import app.goke.core.util.Ids
import app.goke.ui.Actions
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.icons.ContentCopy
import app.goke.ui.monthDayWeekday
import app.goke.ui.screens.freqLabel
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** 时间线 / 日程列表里的一行；按条目类型渲染成课程块、日程块或纯留白呼吸待办行。 */
@Composable
fun AgendaRow(
    item: AgendaItem,
    now: LocalDateTime,
    showTeacher: Boolean,
    onOpenCourse: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
    day: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val colors = MintTheme.colors
    when (item) {
        is CourseItem -> {
            val o = item.occ
            val phase = CourseProgress.phase(o.start, o.end, now)
            val progress = CourseProgress.fraction(o.start, o.end, now)
            val roomText = ctx.formatRooms(o.room)
            RowShell(
                modifier = modifier,
                time = o.start.hm(),
                endTime = o.end.hm(),
                bg = colors.bg(o.course.colorIndex),
                fg = colors.fg(o.course.colorIndex),
                title = o.course.name,
                subtitle = listOf(roomText, if (showTeacher) o.course.teacher else "").filter { it.isNotBlank() }.joinToString(" · "),
                phase = phase,
                progress = progress,
                onClick = { onOpenCourse(o.course.id) },
                tag = if (o.rescheduled) stringResource(R.string.rescheduled) else null,
            )
        }

        is EventItem -> {
            val e = item.event
            val phase = if (item.allDay) TimePhase.UPCOMING else CourseProgress.phase(item.start, item.end, now)
            val progress = if (item.allDay) 0f else CourseProgress.fraction(item.start, item.end, now)
            val timeLabel = if (item.allDay) stringResource(R.string.all_day) else item.start.hm()
            val kindTag =
                when (e.kind) {
                    EventKind.EXAM -> stringResource(R.string.kind_exam)
                    EventKind.COUNTDOWN -> stringResource(R.string.kind_countdown)
                    else -> null
                }
            RowShell(
                modifier = modifier,
                time = timeLabel,
                endTime = if (!item.allDay && item.end.isAfter(item.start)) item.end.hm() else null,
                bg = colors.bg(e.colorIndex),
                fg = colors.fg(e.colorIndex),
                title = e.title,
                subtitle = ctx.formatRooms(e.location),
                phase = phase,
                progress = progress,
                onClick = { onOpenEvent(e.id) },
                tag = kindTag,
            )
        }

        is TaskItem -> {
            TaskRow(item.task, onToggleTask, onOpenTask, subtitle = if (item.hasTime) item.due.hm() else null, modifier = modifier)
        }

        is BlockItem -> {
            // 排入日历的待办也用无底框呼吸待办行，时间段放副标题
            TaskRow(
                item.task,
                onToggleTask,
                onOpenTask,
                subtitle = stringResource(R.string.block_subtitle_short, item.start.hm(), item.end.hm()),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RowShell(
    time: String,
    endTime: String? = null,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    phase: TimePhase,
    progress: Float,
    onClick: () -> Unit,
    tag: String? = null,
    modifier: Modifier = Modifier,
) {
    // 进行中按时间比例从左向右渐进置灰，已结束整行退灰；文字保持可读对比度
    val textFg = rememberCourseProgressFg(fg, phase)
    val stateDesc =
        when (phase) {
            TimePhase.ONGOING -> stringResource(R.string.course_progress_ongoing, (progress * 100f).toInt().coerceIn(0, 100))
            TimePhase.FINISHED -> stringResource(R.string.course_progress_finished)
            TimePhase.UPCOMING -> null
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .courseProgressBackground(
                activeBg = bg,
                phase = phase,
                progress = progress,
                axis = CourseProgressAxis.HORIZONTAL,
            ).then(
                if (stateDesc != null) {
                    Modifier.semantics { stateDescription = stateDesc }
                } else {
                    Modifier
                },
            ).mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 40dp 时间槽 + 8dp 间隔：与 TaskRow 的勾选框槽（8+48+4=60）同起点，混排时标题左沿对齐
        Column(Modifier.width(40.dp)) {
            Text(
                time,
                color = textFg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            if (endTime != null) {
                Text(
                    endTime,
                    color = textFg.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textFg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = textFg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null) Tag(tag, textFg.copy(alpha = 0.15f), textFg)
    }
}

@Composable
fun TaskRow(
    task: Task,
    onToggle: (Task, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val code = task.parcelCode()
    val titleColor by animateColorAsState(
        if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        MintTheme.press(),
        label = "task-title",
    )
    val rowAlpha by animateFloatAsState(if (task.done) 0.45f else 1f, MintTheme.press(), label = "task-done-alpha")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .mintClickable(pressedScale = 0.98f) { onOpen(task.id) }
            .padding(start = 8.dp, end = if (code != null) 4.dp else 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintCheck(task.done, { onToggle(task, it) })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            if (code != null) {
                ParcelCodeHeadline(code = code, done = task.done, copyEnabled = true, alpha = rowAlpha)
                val detail = listOfNotNull(task.parcelLabel(), subtitle).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            } else {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor.copy(alpha = rowAlpha),
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        if (code != null) {
            ParcelCodeCopyButton(code, alpha = rowAlpha)
        }
    }
}

/** 快递待办：取件码非空才算（与桌面小组件同一判定，兼容旧演示字段 pickupCode）。 */
fun Task.parcelCode(): String? = (extra["code"] ?: extra["pickupCode"])?.trim()?.takeIf { it.isNotEmpty() }

/**
 * 取件码升格为主标题时，副标题只留驿站 / 快递名，
 * 不再把「取件 xxx」原样念第二遍；两者都缺时退回原标题（去掉码本身）。
 */
fun Task.parcelLabel(): String? {
    val code = parcelCode() ?: return null
    val place = (extra["place"] ?: extra["pickupPlace"])?.trim().orEmpty()
    val company = extra["company"]?.trim().orEmpty()
    val placeAndCompany =
        listOfNotNull(
            place.takeIf { it.isNotEmpty() },
            company.takeIf { it.isNotEmpty() && !place.contains(it) },
        ).joinToString(" · ")
    if (placeAndCompany.isNotEmpty()) return placeAndCompany
    return title
        .replace(code, "")
        .replace("取件", "")
        .trim(' ', '·', '-', '—', ':', '：')
}

fun copyParcelCode(
    ctx: Context,
    actions: Actions,
    code: String,
) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(code, code))
    actions.toast(ctx.getString(R.string.parcel_code_copied, code))
}

/** 取件码主标题：森林薄荷色半粗等宽数字，与普通待办严格同左轴；轻触码本身即可复制。 */
@Composable
fun ParcelCodeHeadline(
    code: String,
    done: Boolean,
    copyEnabled: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = if (done) 0.45f else 1f,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val color by animateColorAsState(
        if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        MintTheme.press(),
        label = "parcel-code-color",
    )
    val clickMod =
        if (copyEnabled) {
            Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .mintClickable(pressedScale = 0.96f, role = Role.Button) { copyParcelCode(ctx, actions, code) }
        } else {
            Modifier
        }
    Text(
        code,
        modifier = modifier.then(clickMod),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        color = color.copy(alpha = alpha),
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 行尾复制取件码按钮：安静次级图标 + 48dp 触控热区，一键复制不抢主标题焦点。 */
@Composable
fun ParcelCodeCopyButton(
    code: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val description = stringResource(R.string.parcel_copy_code, code)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .mintClickable(pressedScale = 0.96f, role = Role.Button) { copyParcelCode(ctx, actions, code) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

/**
 * 日程详情底部抽屉：展示标题、考试/倒数日徽标、时间（或全天）、地点、重复规则与备注；
 * 支持「转为待办编辑」（统一迁移至待办）与「删除」（带撤销）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: Event?,
    snap: Snapshot,
    onDismiss: () -> Unit,
) {
    val e = event ?: return
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var converting by remember(e.id) { mutableStateOf(false) }

    val colors = MintTheme.colors
    val startDay = e.start.toLocalDate()
    val endDay = e.end.toLocalDate()
    val timeText =
        if (e.allDay) {
            val dateRange =
                if (startDay == endDay) {
                    ctx.monthDayWeekday(startDay)
                } else {
                    "${ctx.monthDayWeekday(startDay)} – ${ctx.monthDayWeekday(endDay)}"
                }
            "$dateRange · ${stringResource(R.string.event_detail_all_day)}"
        } else if (startDay == endDay) {
            "${ctx.monthDayWeekday(startDay)} ${e.start.hm()}–${e.end.hm()}"
        } else {
            "${ctx.monthDayWeekday(startDay)} ${e.start.hm()} – ${ctx.monthDayWeekday(endDay)} ${e.end.hm()}"
        }
    val kindLabel =
        when (e.kind) {
            EventKind.EXAM -> stringResource(R.string.kind_exam)
            EventKind.COUNTDOWN -> stringResource(R.string.kind_countdown)
            EventKind.NORMAL -> null
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(colors.fg(e.colorIndex)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    e.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (kindLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Tag(kindLabel, colors.bg(e.colorIndex), colors.fg(e.colorIndex))
                }
            }
            Text(
                timeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
            if (e.location.isNotBlank()) {
                EventInfoRow(stringResource(R.string.field_location), e.location.trim())
            }
            e.recurrence?.let { r ->
                EventInfoRow(
                    stringResource(R.string.event_detail_repeat),
                    freqLabel(r.freq, r.until, r.interval, r.byDays, startDay, byMonths = r.byMonths),
                )
            }
            if (e.note.isNotBlank()) {
                Text(
                    e.note.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !converting,
                    onClick = {
                        if (converting) return@TextButton
                        converting = true
                        val desc = ctx.getString(R.string.delete_desc, e.title)
                        actions.commit(Edits.delete(snap, e, desc, System.currentTimeMillis()), desc)
                        onDismiss()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    enabled = !converting,
                    onClick = {
                        if (converting) return@Button
                        converting = true
                        val newId = Ids.next()
                        val sortOrder = (snap.tasks.maxOfOrNull { it.sortOrder } ?: 0) + 1
                        val task = Unify.eventToTask(e, newId, sortOrder)
                        val desc = ctx.getString(R.string.event_detail_convert)
                        val cs =
                            ChangeSet(
                                Ids.next(),
                                desc,
                                Source.MANUAL,
                                System.currentTimeMillis(),
                                listOf(Change.Add(task), Change.Remove(e)),
                            )
                        actions.commit(cs, ctx.getString(R.string.event_detail_converted_toast), undoable = false)
                        scope.launch {
                            app.repo.snapshot.first { s -> s.tasks.any { it.id == newId } }
                            onDismiss()
                            nav.openTask(newId)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.event_detail_convert))
                }
            }
        }
    }
}

@Composable
private fun EventInfoRow(
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
