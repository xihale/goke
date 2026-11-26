package app.goke.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.goke.R

/** 「待取快递」小组件列表数据源。 */
class ParcelWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent.data?.getBooleanQueryParameter("compact", false) == true)

    private class Factory(
        private val ctx: Context,
        private val compact: Boolean,
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
            return itemView(ctx, r, compact = compact)
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
            compact: Boolean = false,
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_parcel_item)
            v.setViewPadding(
                R.id.parcel_item_root,
                WidgetKit.dp(ctx, if (compact) 2 else 4),
                WidgetKit.dp(ctx, if (compact) 4 else 6),
                WidgetKit.dp(ctx, if (compact) 6 else 8),
                WidgetKit.dp(ctx, if (compact) 4 else 6),
            )
            v.setTextViewTextSize(R.id.parcel_item_code, TypedValue.COMPLEX_UNIT_SP, if (compact) 14f else 15f)
            v.setTextViewTextSize(R.id.parcel_item_place, TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
            v.setTextViewText(R.id.parcel_item_code, r.code)
            v.setTextViewText(R.id.parcel_item_place, r.place)
            if (!compact && !r.due.isNullOrBlank()) {
                v.setViewVisibility(R.id.parcel_item_due, View.VISIBLE)
                v.setTextViewText(R.id.parcel_item_due, r.due)
                v.setTextColor(R.id.parcel_item_due, ctx.getColor(if (r.overdue) R.color.widget_overdue else R.color.widget_muted))
            } else {
                v.setViewVisibility(R.id.parcel_item_due, View.GONE)
            }
            v.setInt(R.id.parcel_item_check, "setColorFilter", WidgetKit.priorityColor(ctx, 0, r.overdue))
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
