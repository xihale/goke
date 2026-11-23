package app.goke.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.goke.R

/** 「今日课表」小组件可滚动列表数据源。 */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent.data?.getBooleanQueryParameter("compact", false) == true)

    private class Factory(
        private val ctx: Context,
        private val compact: Boolean,
    ) : RemoteViewsFactory {
        private var rows = emptyList<ScheduleWidgetProvider.Row>()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            rows = ScheduleWidgetProvider.rows(ctx)
        }

        override fun onDestroy() {}

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_schedule_item)
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
            r: ScheduleWidgetProvider.Row,
            compact: Boolean = false,
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_schedule_item)
            val fg =
                when (r.phase) {
                    WidgetKit.TimePhase.FINISHED -> {
                        v.setInt(R.id.sched_item_root, "setBackgroundResource", R.drawable.widget_course_done_bg)
                        v.setViewVisibility(R.id.sched_item_progress, View.GONE)
                        ctx.getColor(R.color.widget_course_done_fg)
                    }

                    WidgetKit.TimePhase.ONGOING -> {
                        v.setInt(R.id.sched_item_root, "setBackgroundResource", WidgetKit.courseBgRes(r.colorIndex))
                        v.setViewVisibility(R.id.sched_item_progress, View.VISIBLE)
                        v.setProgressBar(
                            R.id.sched_item_progress,
                            WidgetKit.PROGRESS_MAX,
                            WidgetKit.progressPermille(r.progress),
                            false,
                        )
                        WidgetKit.courseFg(ctx, r.colorIndex)
                    }

                    WidgetKit.TimePhase.UPCOMING -> {
                        v.setInt(R.id.sched_item_root, "setBackgroundResource", WidgetKit.courseBgRes(r.colorIndex))
                        v.setViewVisibility(R.id.sched_item_progress, View.GONE)
                        WidgetKit.courseFg(ctx, r.colorIndex)
                    }
                }
            v.setViewVisibility(R.id.sched_item_time_col, if (compact) View.GONE else View.VISIBLE)
            v.setTextColor(R.id.sched_item_time, fg)
            v.setTextColor(R.id.sched_item_end, WidgetKit.softFg(fg))
            v.setTextColor(R.id.sched_item_name, fg)
            v.setTextColor(R.id.sched_item_meta, WidgetKit.softFg(fg))
            v.setTextViewText(R.id.sched_item_time, r.startTime)
            v.setTextViewText(R.id.sched_item_end, r.endTime)
            v.setTextViewText(R.id.sched_item_name, r.name)
            val meta =
                if (compact) {
                    val span = "${r.startTime}–${r.endTime}"
                    listOfNotNull(span, r.room.takeIf { it.isNotEmpty() }).joinToString(" · ")
                } else {
                    r.room
                }
            v.setTextViewText(R.id.sched_item_meta, meta)
            v.setViewVisibility(R.id.sched_item_meta, if (meta.isNotEmpty()) View.VISIBLE else View.GONE)

            val open =
                Intent().apply {
                    putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_OPEN)
                    putExtra(WidgetActionActivity.EXTRA_COURSE_ID, r.courseId)
                    putExtra(WidgetActionActivity.EXTRA_DATE, r.courseDate)
                    putExtra(WidgetActionActivity.EXTRA_START_PERIOD, r.startPeriod)
                    putExtra(WidgetActionActivity.EXTRA_END_PERIOD, r.endPeriod)
                }
            v.setOnClickFillInIntent(R.id.sched_item_root, open)
            v.setOnClickFillInIntent(R.id.sched_item_content, open)
            v.setOnClickFillInIntent(R.id.sched_item_body, open)
            return v
        }
    }
}
