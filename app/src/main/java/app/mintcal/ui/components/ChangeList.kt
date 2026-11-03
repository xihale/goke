package app.mintcal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.ChangeOp
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.ChangeView
import app.mintcal.core.change.ChangeViews
import app.mintcal.core.change.FieldDiff
import app.mintcal.core.change.FieldKey
import app.mintcal.core.model.EntityType
import app.mintcal.core.model.Snapshot
import app.mintcal.core.util.parseDateTimeLenient
import app.mintcal.ui.hm
import app.mintcal.ui.monthDay
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull

private fun fieldLabel(k: FieldKey): Int =
    when (k) {
        FieldKey.NAME -> R.string.field_name
        FieldKey.TEACHER -> R.string.field_teacher
        FieldKey.ROOM -> R.string.field_room
        FieldKey.DAY -> R.string.field_day
        FieldKey.PERIODS -> R.string.field_periods
        FieldKey.WEEKS -> R.string.field_weeks
        FieldKey.COLOR -> R.string.field_color
        FieldKey.REMIND -> R.string.field_remind
        FieldKey.NOTE -> R.string.field_note
        FieldKey.START -> R.string.field_start
        FieldKey.END -> R.string.field_end
        FieldKey.ALL_DAY -> R.string.field_all_day
        FieldKey.LOCATION -> R.string.field_location
        FieldKey.CATEGORY -> R.string.field_category
        FieldKey.RECURRENCE -> R.string.field_recurrence
        FieldKey.LIST -> R.string.field_list
        FieldKey.PRIORITY -> R.string.field_priority
        FieldKey.DUE -> R.string.field_due
        FieldKey.ESTIMATE -> R.string.field_estimate
        FieldKey.DONE -> R.string.field_done
        FieldKey.BLOCK -> R.string.field_block
        FieldKey.TAGS -> R.string.field_tags
        FieldKey.SEMESTER -> R.string.field_semester
    }

private fun entityLabel(t: EntityType): Int =
    when (t) {
        EntityType.COURSE -> R.string.entity_course
        EntityType.EXCEPTION -> R.string.entity_exception
        EntityType.EVENT -> R.string.entity_event
        EntityType.TASK -> R.string.entity_task
        EntityType.LIST -> R.string.entity_list
        EntityType.SEMESTER -> R.string.entity_semester
    }

private fun fieldValue(
    ctx: android.content.Context,
    d: FieldDiff,
    v: String?,
    snap: Snapshot,
): String {
    if (v == null) return ""
    return when (d.key) {
        FieldKey.DAY -> {
            v.toIntOrNull()?.let { ctx.weekdayFull(it) } ?: v
        }

        FieldKey.PERIODS -> {
            ctx.getString(R.string.periods_value, v)
        }

        FieldKey.WEEKS -> {
            v.toIntOrNull()?.let { ctx.getString(R.string.week_n, it) } ?: ctx.getString(R.string.weeks_value, v)
        }

        FieldKey.START, FieldKey.END, FieldKey.DUE, FieldKey.BLOCK -> {
            runCatching {
                val dt = parseDateTimeLenient(v)
                "${ctx.monthDay(dt.toLocalDate())} ${dt.hm()}"
            }.getOrDefault(v)
        }

        FieldKey.ALL_DAY, FieldKey.DONE -> {
            if (v == "true") ctx.getString(R.string.yes) else ctx.getString(R.string.no)
        }

        FieldKey.REMIND -> {
            v.toIntOrNull()?.let { ctx.getString(R.string.remind_minutes_value, it) } ?: v
        }

        FieldKey.LIST -> {
            snap.lists.firstOrNull { it.id == v }?.name ?: v
        }

        FieldKey.PRIORITY -> {
            ctx.resources.getStringArray(R.array.priority_names).getOrElse(v.toIntOrNull() ?: 0) { v }
        }

        FieldKey.ESTIMATE -> {
            ctx.getString(R.string.minutes_n, v.toIntOrNull() ?: 0)
        }

        FieldKey.COLOR -> {
            ctx.getString(R.string.color_n, (v.toIntOrNull() ?: 0) + 1)
        }

        else -> {
            v
        }
    }
}

/** 变更集的「人话」列表：新增 / 修改（旧值划线 → 新值）/ 删除。 */
@Composable
fun ChangeList(
    cs: ChangeSet,
    snap: Snapshot,
    modifier: Modifier = Modifier,
    limit: Int = 30,
) {
    val views = remember(cs) { ChangeViews.of(cs) }
    var expanded by remember(cs.id) { mutableStateOf(false) }
    val shown = if (expanded) views else views.take(limit)
    Column(modifier.fillMaxWidth().animateContentSize(MintTheme.spec()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { ChangeCard(it, snap) }
        AnimatedVisibility(visible = views.size > limit && !expanded, enter = fadeIn(MintTheme.spec()) + expandVertically(MintTheme.spec()), exit = fadeOut(MintTheme.spec()) + shrinkVertically(MintTheme.spec())) {
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.show_more_changes, views.size - limit)) }
        }
    }
}

@Composable
private fun ChangeCard(
    v: ChangeView,
    snap: Snapshot,
) {
    val ctx = LocalContext.current
    val colors = MintTheme.colors
    val bar =
        when (v.op) {
            ChangeOp.ADD -> colors.added
            ChangeOp.MODIFY -> colors.modified
            ChangeOp.REMOVE -> colors.removed
        }
    val opLabel =
        when (v.op) {
            ChangeOp.ADD -> R.string.op_add
            ChangeOp.MODIFY -> R.string.op_modify
            ChangeOp.REMOVE -> R.string.op_remove
        }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(MintTheme.card),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(bar))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "${stringResource(entityLabel(v.type))} · ${v.title}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (v.op == ChangeOp.REMOVE) TextDecoration.LineThrough else null,
                )
                Text(stringResource(opLabel), color = if (v.op == ChangeOp.MODIFY) colors.onModified else bar, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            when (v.op) {
                ChangeOp.MODIFY -> {
                    v.summary.forEach { d ->
                        Row {
                            Text(stringResource(fieldLabel(d.key)) + "  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!d.before.isNullOrEmpty()) Text(fieldValue(ctx, d, d.before, snap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                            Text("  →  ", style = MaterialTheme.typography.bodySmall)
                            Text(if (d.after.isNullOrEmpty()) stringResource(R.string.none) else fieldValue(ctx, d, d.after, snap), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> {
                    val keys = v.summary.filter { it.key != FieldKey.NAME && it.key != FieldKey.COLOR }.take(4)
                    if (keys.isNotEmpty()) {
                        Text(
                            keys.joinToString("  ·  ") { d -> fieldValue(ctx, d, d.after ?: d.before, snap) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
