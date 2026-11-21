package app.mintcal.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.mintcal.R

/** 「今日安排」统一小组件列表数据源。 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(
        private val ctx: Context,
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
            return itemView(ctx, r)
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
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_today_item)
            if (r.taskId != null) {
                // 宣纸行规则：待办行无框直落宣纸底（与 App TaskRow / web .task 一致）
                v.setInt(R.id.today_item_root, "setBackgroundResource", 0)
                v.setTextColor(R.id.today_item_time, ctx.getColor(R.color.widget_muted))
                v.setTextColor(R.id.today_item_title, ctx.getColor(R.color.widget_text))
                v.setTextColor(R.id.today_item_sub, ctx.getColor(R.color.widget_muted))
            } else {
                // 时间块穿植物色软彩底；正在进行的按设计改用薄荷容器高亮
                v.setInt(
                    R.id.today_item_root,
                    "setBackgroundResource",
                    if (r.ongoing) R.drawable.widget_row_now_bg else WidgetKit.courseBgRes(r.colorIndex),
                )
                val fg = ctx.getColor(if (r.ongoing) R.color.widget_now_fg else WidgetKit.courseFgRes(r.colorIndex))
                v.setTextColor(R.id.today_item_time, fg)
                v.setTextColor(R.id.today_item_title, fg)
                v.setTextColor(R.id.today_item_sub, WidgetKit.softFg(fg))
            }
            v.setTextViewText(R.id.today_item_time, r.time)
            if (r.code != null) {
                v.setViewVisibility(R.id.today_item_code, View.VISIBLE)
                v.setTextViewText(R.id.today_item_code, r.code)
            } else {
                v.setViewVisibility(R.id.today_item_code, View.GONE)
            }
            v.setTextViewText(R.id.today_item_title, r.title)
            if (!r.sub.isNullOrBlank()) {
                v.setViewVisibility(R.id.today_item_sub, View.VISIBLE)
                v.setTextViewText(R.id.today_item_sub, r.sub)
            } else {
                v.setViewVisibility(R.id.today_item_sub, View.GONE)
            }

            if (r.taskId != null) {
                v.setViewVisibility(R.id.today_item_check, View.VISIBLE)
                v.setInt(R.id.today_item_check, "setColorFilter", WidgetKit.priorityColor(ctx, r.priority, r.overdue))
                v.setOnClickFillInIntent(
                    R.id.today_item_check,
                    Intent()
                        .putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_DONE)
                        .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.taskId),
                )
            } else {
                v.setViewVisibility(R.id.today_item_check, View.GONE)
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
            v.setOnClickFillInIntent(R.id.today_item_time, open)
            return v
        }
    }
}
