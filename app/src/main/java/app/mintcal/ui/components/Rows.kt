package app.mintcal.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import app.mintcal.ui.LocalActions
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
            // 排入日历的待办也用正常 todo 行,只把时间段放副标题
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
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    dim: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
    modifier: Modifier = Modifier,
) {
    // 过期置灰随时间平滑过渡（30s 心跳）；只淡背景，文字保持独立可读强度
    val bgAlpha by animateFloatAsState(if (dim) 0.45f else 1f, MintTheme.spec(), label = "row-dim")
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg.copy(alpha = bgAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 40dp 时间槽 + 8dp 间隔：与 TaskRow 的勾选框槽（12+48）同起点，混排时标题左沿对齐
        Text(time, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // 完成整行退灰（与周历格子同款思路，弱于背景色只淡文字）：勾选框、文字、底色一起退，
    // 对齐 RowShell 过期置灰的 0.45 系数，不再占用视觉资源
    val rowAlpha by animateFloatAsState(if (task.done) 0.45f else 1f, MintTheme.spec(), label = "task-done-alpha")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer { alpha = rowAlpha }
            .clip(MaterialTheme.shapes.medium)
            .background(MintTheme.card)
            .clickable { onOpen(task.id) }
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintCheck(task.done, { onToggle(task, it) })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            // 快递待办：取件码单独做成胶囊，标题只留驿站 / 快递名，同一行不念两遍
            val code = task.parcelCode()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (code != null) {
                    ParcelCodeTag(code)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    task.parcelLabel() ?: task.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                )
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 快递待办：取件码非空才算（与桌面小组件同一判定，兼容旧演示字段 pickupCode）。 */
fun Task.parcelCode(): String? = (extra["code"] ?: extra["pickupCode"])?.trim()?.takeIf { it.isNotEmpty() }

/**
 * 取件码已经单独显示成胶囊时，行内标题只留驿站 / 快递名，
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
    return title.replace(code, "").trim(' ', '·', '-', '—', ':', '：').ifEmpty { title }
}

/** 取件码票据胶囊：点一下复制取件码；行本身仍然负责打开待办。 */
@Composable
fun ParcelCodeTag(
    code: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val copied = stringResource(R.string.parcel_code_copied, code)
    val description = stringResource(R.string.parcel_code, code)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .mintClickable(role = Role.Button) {
                // 手打一串 4-2-1086 太费事，点一下就进剪贴板
                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(code, code))
                actions.toast(copied)
            }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Tag(code, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
