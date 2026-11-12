package app.mintcal.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.mintcal.R

/** 「待取快递」小组件列表数据源。 */
class ParcelWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(
        private val ctx: Context,
    ) : RemoteViewsFactory {
        private var rows = emptyList<ParcelWidgetProvider.Row>()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            rows = ParcelWidgetProvider.rows(ctx)
        }

        override fun onDestroy() {}

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_parcel_item)
            return itemView(ctx, r)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            rows
                .getOrNull(position)
                ?.id
                ?.hashCode()
                ?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }

    companion object {
        internal fun itemView(
            ctx: Context,
            r: ParcelWidgetProvider.Row,
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_parcel_item)
            v.setTextViewText(R.id.parcel_item_code, r.code)
            v.setTextViewText(R.id.parcel_item_place, r.place)
            if (!r.due.isNullOrBlank()) {
                v.setViewVisibility(R.id.parcel_item_due, View.VISIBLE)
                v.setTextViewText(R.id.parcel_item_due, r.due)
                v.setTextColor(R.id.parcel_item_due, ctx.getColor(if (r.overdue) R.color.widget_overdue else R.color.widget_muted))
            } else {
                v.setViewVisibility(R.id.parcel_item_due, View.GONE)
            }
            v.setInt(R.id.parcel_item_check, "setColorFilter", WidgetKit.priorityColor(ctx, 2, r.overdue))
            v.setOnClickFillInIntent(
                R.id.parcel_item_check,
                Intent()
                    .putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_DONE)
                    .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.id),
            )
            val open =
                Intent()
                    .putExtra(TodoWidgetProvider.EXTRA_OP, TodoWidgetProvider.OP_OPEN)
                    .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, r.id)
            v.setOnClickFillInIntent(R.id.parcel_item_root, open)
            v.setOnClickFillInIntent(R.id.parcel_item_body, open)
            return v
        }
    }
}
