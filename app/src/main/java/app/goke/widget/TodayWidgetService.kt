package app.goke.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.goke.R

/** 「今日安排」统一小组件列表数据源。 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent.data?.getBooleanQueryParameter("compact", false) == true)

    private class Factory(
        private val ctx: Context,
        private val compact: Boolean,
    ) : RemoteViewsFactory {
        private var rows = emptyList<TodayWidgetProvider.Row>()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            rows = TodayWidgetProvider.rows(ctx)
        }

        override fun onDestroy() {}

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_today_item)
            return itemView(ctx, r, compact = compact)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            rows
                .getOrNull(position)
                ?.key
                ?.hashCode()
                ?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }

    companion object {
        internal fun itemView(
            ctx: Context,
            r: TodayWidgetProvider.Row,
            compact: Boolean = false,
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_today_item)
            if (r.colorIndex < 0) {
                // 宣纸行规则：待办行无框直落宣纸底（与 App TaskRow / web .task 一致）
                v.setInt(R.id.today_item_root, "setBackgroundResource", 0)
                v.setViewVisibility(R.id.today_item_progress, View.GONE)
                v.setTextColor(R.id.today_item_time, ctx.getColor(R.color.widget_muted))
                v.setTextColor(R.id.today_item_end, ctx.getColor(R.color.widget_muted))
                v.setTextColor(R.id.today_item_title, ctx.getColor(if (r.code != null) R.color.widget_accent else R.color.widget_text))
                v.setTextColor(R.id.today_item_sub, ctx.getColor(R.color.widget_muted))
            } else {
                // 课程/日程：已结束置灰；进行中按已过时长渐进置灰；未开始穿植物色软彩底
                val fg =
                    when {
                        r.finished -> {
                            v.setInt(R.id.today_item_root, "setBackgroundResource", R.drawable.widget_course_done_bg)
                            v.setViewVisibility(R.id.today_item_progress, View.GONE)
                            ctx.getColor(R.color.widget_course_done_fg)
                        }

                        r.ongoing -> {
                            v.setInt(R.id.today_item_root, "setBackgroundResource", WidgetKit.courseBgRes(r.colorIndex))
                            v.setViewVisibility(R.id.today_item_progress, View.VISIBLE)
                            v.setProgressBar(
                                R.id.today_item_progress,
                                WidgetKit.PROGRESS_MAX,
                                WidgetKit.progressPermille(r.progress),
                                false,
                            )
                            WidgetKit.courseFg(ctx, r.colorIndex)
                        }

                        else -> {
                            v.setInt(R.id.today_item_root, "setBackgroundResource", WidgetKit.courseBgRes(r.colorIndex))
                            v.setViewVisibility(R.id.today_item_progress, View.GONE)
                            WidgetKit.courseFg(ctx, r.colorIndex)
                        }
                    }
                v.setTextColor(R.id.today_item_time, fg)
                v.setTextColor(R.id.today_item_end, WidgetKit.softFg(fg))
                v.setTextColor(R.id.today_item_title, fg)
                v.setTextColor(R.id.today_item_sub, WidgetKit.softFg(fg))
            }
            v.setViewVisibility(R.id.today_item_time_col, if (compact) View.GONE else View.VISIBLE)
            v.setTextViewText(R.id.today_item_time, r.time)
            if (r.endTime != null) {
                v.setViewVisibility(R.id.today_item_end, View.VISIBLE)
                v.setTextViewText(R.id.today_item_end, r.endTime)
            } else {
                v.setViewVisibility(R.id.today_item_end, View.GONE)
            }
            v.setViewVisibility(R.id.today_item_code, View.GONE)
            if (r.code != null) {
                v.setTextViewText(R.id.today_item_title, r.code)
                v.setViewVisibility(R.id.today_item_sub, View.VISIBLE)
                v.setTextViewText(R.id.today_item_sub, r.title)
            } else {
                v.setTextViewText(R.id.today_item_title, r.title)
                val timeSpan =
                    if (r.endTime != null) {
                        "${r.time}–${r.endTime}"
                    } else {
                        r.time
                    }
                val subText =
                    if (compact) {
                        listOfNotNull(timeSpan.takeIf { it.isNotBlank() }, r.sub?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    } else {
                        r.sub.orEmpty()
                    }
                if (subText.isNotBlank()) {
                    v.setViewVisibility(R.id.today_item_sub, View.VISIBLE)
                    v.setTextViewText(R.id.today_item_sub, subText)
                } else {
                    v.setViewVisibility(R.id.today_item_sub, View.GONE)
                }
            }

            if (r.colorIndex < 0) {
                v.setViewVisibility(R.id.today_item_check, View.VISIBLE)
                v.setInt(R.id.today_item_check, "setColorFilter", WidgetKit.priorityColor(ctx, r.priority, r.overdue))
                v.setOnClickFillInIntent(
                    R.id.today_item_check,
                    Intent()
                        .putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_DONE)
                        .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.taskId),
                )
                val muted = ctx.getColor(R.color.widget_muted)
                v.setViewVisibility(R.id.today_item_remind, if (r.hasRemind) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.today_item_repeat, if (r.repeating) View.VISIBLE else View.GONE)
                v.setInt(R.id.today_item_remind, "setColorFilter", muted)
                v.setInt(R.id.today_item_repeat, "setColorFilter", muted)
                if (!compact && r.listName != null) {
                    v.setViewVisibility(R.id.today_item_list, View.VISIBLE)
                    v.setTextViewText(R.id.today_item_list, r.listName)
                } else {
                    v.setViewVisibility(R.id.today_item_list, View.GONE)
                }
            } else {
                v.setViewVisibility(R.id.today_item_check, View.GONE)
                v.setViewVisibility(R.id.today_item_remind, View.GONE)
                v.setViewVisibility(R.id.today_item_repeat, View.GONE)
                v.setViewVisibility(R.id.today_item_list, View.GONE)
            }

            val open =
                Intent().apply {
                    putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_OPEN)
                    when {
                        r.taskId != null -> {
                            putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.taskId)
                        }

                        r.courseId != null -> {
                            putExtra(WidgetActionActivity.EXTRA_COURSE_ID, r.courseId)
                            if (r.courseDate != null) putExtra(WidgetActionActivity.EXTRA_DATE, r.courseDate)
                            if (r.startPeriod != null) putExtra(WidgetActionActivity.EXTRA_START_PERIOD, r.startPeriod)
                            if (r.endPeriod != null) putExtra(WidgetActionActivity.EXTRA_END_PERIOD, r.endPeriod)
                        }

                        r.eventId != null -> {
                            putExtra(WidgetActionActivity.EXTRA_EVENT_ID, r.eventId)
                        }

                        else -> {
                            putExtra(TodoWidgetProvider.EXTRA_URI, r.targetUri)
                        }
                    }
                }
            v.setOnClickFillInIntent(R.id.today_item_root, open)
            v.setOnClickFillInIntent(R.id.today_item_body, open)
            v.setOnClickFillInIntent(R.id.today_item_time_col, open)
            return v
        }
    }
}
