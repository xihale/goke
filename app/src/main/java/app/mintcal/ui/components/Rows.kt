package app.mintcal.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
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
import app.mintcal.ui.Actions
import app.mintcal.ui.LocalActions
import app.mintcal.ui.formatRooms
import app.mintcal.ui.hm
import app.mintcal.ui.theme.MintTheme
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
            val past = o.end.isBefore(now)
            val roomText = ctx.formatRooms(o.room)
            RowShell(
                modifier = modifier,
                time = o.start.hm(),
                bg = colors.bg(o.course.colorIndex),
                fg = colors.fg(o.course.colorIndex),
                title = o.course.name,
                subtitle = listOf(roomText, if (showTeacher) o.course.teacher else "").filter { it.isNotBlank() }.joinToString(" · "),
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
                subtitle = ctx.formatRooms(e.location),
                dim = past,
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
            .mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 40dp 时间槽 + 8dp 间隔：与 TaskRow 的勾选框槽（8+48+4=60）同起点，混排时标题左沿对齐
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
    val code = task.parcelCode()
    val titleColor by animateColorAsState(
        if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        MintTheme.spec(),
        label = "task-title",
    )
    val rowAlpha by animateFloatAsState(if (task.done) 0.45f else 1f, MintTheme.spec(), label = "task-done-alpha")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer { alpha = rowAlpha }
            .mintClickable(pressedScale = 0.98f) { onOpen(task.id) }
            .padding(start = 8.dp, end = if (code != null) 4.dp else 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintCheck(task.done, { onToggle(task, it) })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            if (code != null) {
                ParcelCodeHeadline(code = code, done = task.done, copyEnabled = true)
                val detail = listOfNotNull(task.parcelLabel(), subtitle).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = titleColor,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        if (code != null) {
            ParcelCodeCopyButton(code)
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
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val color by animateColorAsState(
        if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        MintTheme.spec(),
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
        color = color,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 行尾复制取件码按钮：安静次级图标 + 40dp 触控热区，一键复制不抢主标题焦点。 */
@Composable
fun ParcelCodeCopyButton(
    code: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val description = stringResource(R.string.parcel_copy_code, code)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .mintClickable(pressedScale = 0.96f, role = Role.Button) { copyParcelCode(ctx, actions, code) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
