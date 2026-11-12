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
            v.setInt(
                R.id.today_item_root,
                "setBackgroundResource",
                if (r.ongoing) R.drawable.widget_row_now_bg else R.drawable.widget_row_bg,
            )
            v.setInt(R.id.today_item_bar, "setColorFilter", r.barColor)
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
                v.setInt(R.id.today_item_check, "setColorFilter", r.barColor)
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
                    if (r.taskId != null) {
                        putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.taskId)
                    } else {
                        putExtra(TodoWidgetProvider.EXTRA_URI, r.targetUri)
                    }
                }
            v.setOnClickFillInIntent(R.id.today_item_root, open)
            v.setOnClickFillInIntent(R.id.today_item_body, open)
            v.setOnClickFillInIntent(R.id.today_item_time, open)
            return v
        }
    }
}
