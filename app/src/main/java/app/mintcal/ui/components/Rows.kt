package app.mintcal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.agenda.AgendaItem
import app.mintcal.core.agenda.BlockItem
import app.mintcal.core.agenda.CourseItem
import app.mintcal.core.agenda.EventItem
import app.mintcal.core.agenda.TaskItem
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Task
import app.mintcal.ui.hm
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDate
import java.time.LocalDateTime

/** 时间线 / 日程列表里的一行；按条目类型渲染成课程块、日程块、待办或虚线时间块。 */
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
    val colors = MintTheme.colors
    when (item) {
        is CourseItem -> {
            val o = item.occ
            val past = o.end.isBefore(now)
            RowShell(
                modifier = modifier,
                time = o.start.hm(),
                bg = colors.bg(o.course.colorIndex),
                fg = colors.fg(o.course.colorIndex),
                title = o.course.name,
                subtitle = listOf(o.room, if (showTeacher) o.course.teacher else "").filter { it.isNotBlank() }.joinToString(" · "),
                dim = past,
                onClick = { onOpenCourse(o.course.id) },
                tag = if (o.rescheduled) stringResource(R.string.rescheduled) else null,
            )
        }

        is EventItem -> {
            val e = item.event
            val past = !item.allDay && item.end.isBefore(now)
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
                bg = colors.bg(e.colorIndex),
                fg = colors.fg(e.colorIndex),
                title = e.title,
                subtitle = e.location,
                dim = past,
                onClick = { onOpenEvent(e.id) },
                tag = kindTag,
            )
        }

        is TaskItem -> {
            TaskRow(item.task, onToggleTask, onOpenTask, subtitle = if (item.hasTime) item.due.hm() else null, modifier = modifier)
        }

        is BlockItem -> {
            BlockRow(item, onToggleTask, onOpenTask, modifier = modifier)
        }
    }
}

@Composable
private fun RowShell(
    time: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    dim: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
    modifier: Modifier = Modifier,
) {
    // 过期置灰随时间平滑过渡（30s 心跳）
    val alpha by animateFloatAsState(if (dim) 0.55f else 1f, MintTheme.spec(), label = "row-dim")
    Row(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null) Tag(tag, fg.copy(alpha = 0.15f), fg)
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
    val titleColor by animateColorAsState(if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, MintTheme.spec(), label = "task-title")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MintTheme.card)
            .clickable { onOpen(task.id) }
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintCheck(task.done, { onToggle(task, it) })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = titleColor,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BlockRow(
    item: BlockItem,
    onToggle: (Task, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpen(item.task.id) }
            .drawBehind {
                drawRoundRect(
                    color = line,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.start.hm(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.task.done) TextDecoration.LineThrough else null,
            )
            Text(
                stringResource(
                    R.string.block_subtitle,
                    (
                        java.time.Duration
                            .between(item.start, item.end)
                            .toMinutes() / 60.0
                    ).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MintCheck(item.task.done, { onToggle(item.task, it) })
    }
}
