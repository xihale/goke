package app.mintcal.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.mintcal.R

/** 待办小组件的列表数据源（RemoteViews 集合）。 */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(
        private val ctx: Context,
    ) : RemoteViewsFactory {
        private var rows = emptyList<TodoWidgetProvider.Row>()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            rows = TodoWidgetProvider.rows(ctx)
        }

        override fun onDestroy() {}

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_todo_item)
            val v = RemoteViews(ctx.packageName, R.layout.widget_todo_item)
            v.setTextViewText(R.id.item_title, r.title)
            v.setTextViewText(R.id.item_due, r.due.orEmpty())
            v.setTextColor(R.id.item_due, ctx.getColor(if (r.overdue) R.color.widget_overdue else R.color.widget_muted))
            v.setOnClickFillInIntent(R.id.item_check, fillIn(TodoWidgetProvider.OP_DONE, r.id))
            val open = fillIn(TodoWidgetProvider.OP_OPEN, r.id)
            v.setOnClickFillInIntent(R.id.item_title, open)
            v.setOnClickFillInIntent(R.id.item_due, open)
            return v
        }

        private fun fillIn(
            op: String,
            id: String,
        ) = Intent()
            .putExtra(TodoWidgetProvider.EXTRA_OP, op)
            .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, id)

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
