package app.goke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.ui.reduceMotion
import app.goke.ui.weekdayShort
import app.goke.ui.yearMonth
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 翻页中心页：向前后各留出 3000 个月，滑不到头。 */
private const val PAGE_CENTER = 3000

/**
 * 月份选择面板：标题 + 左右箭头 + 可横向滑动的月历，翻页锚点与主日历同款。
 * 标题带年份——滑动能跨年，只写月份会有歧义。
 */
@Composable
fun MonthPager(
    selected: LocalDate?,
    initial: YearMonth,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = reduceMotion()
    val pager = rememberPagerState(initialPage = PAGE_CENTER) { PAGE_CENTER * 2 }

    fun monthOf(page: Int): YearMonth = initial.plusMonths((page - PAGE_CENTER).toLong())

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ctx.yearMonth(monthOf(pager.currentPage).atDay(1)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch {
                        val target = pager.currentPage - 1
                        if (reduce) pager.scrollToPage(target) else pager.animateScrollToPage(target)
                    }
                },
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.a11y_prev_month)) }
            IconButton(
                onClick = {
                    scope.launch {
                        val target = pager.currentPage + 1
                        if (reduce) pager.scrollToPage(target) else pager.animateScrollToPage(target)
                    }
                },
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.a11y_next_month)) }
        }
        // 月历页高恒定（星期头 + 6 行）。Pager 在有界容器（对话框/底部面板）里会按约束高
        // 定尺寸而不是按内容，第六行会被裁掉——先隐藏组装一份量出自然高度，再给 pager 定高。
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val natural =
                subcompose("measure") {
                    MonthGrid(selected = null, month = monthOf(PAGE_CENTER), onPick = {})
                }.first().measure(Constraints(maxWidth = constraints.maxWidth)).height
            val pageSize = natural.toDp()
            val page =
                subcompose("pager") {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(pageSize)) { page ->
                        MonthGrid(selected = selected, month = monthOf(page), onPick = onPick)
                    }
                }.map { it.measure(Constraints(maxWidth = constraints.maxWidth, minHeight = natural, maxHeight = natural)) }
            layout(page.maxOf { it.width }, natural) { page.forEach { it.place(0, 0) } }
        }
    }
}

@Composable
private fun MonthGrid(
    selected: LocalDate?,
    month: YearMonth,
    onPick: (LocalDate) -> Unit,
) {
    val ctx = LocalContext.current
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val today = LocalDate.now()
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            for (i in 0..6) {
                Text(
                    ctx.weekdayShort(i + 1),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val d = first.plusDays((r * 7 + c).toLong())
                    val sel = d == selected
                    val inMonth = YearMonth.from(d) == month
                    val isToday = d == today
                    val desc =
                        buildString {
                            append(ctx.getString(R.string.fmt_full_date, d.year, d.monthValue, d.dayOfMonth, ctx.weekdayShort(d.dayOfWeek.value)))
                            if (isToday) append("，").append(ctx.getString(R.string.today))
                        }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .mintClickable(pressedScale = 0.96f, role = Role.Button) { onPick(d) }
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                this.selected = sel
                                contentDescription = desc
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    when {
                                        sel -> MaterialTheme.colorScheme.onPrimary
                                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
