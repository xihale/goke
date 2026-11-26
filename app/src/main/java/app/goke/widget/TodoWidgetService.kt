package app.goke.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.goke.R

/** 待办小组件的列表数据源（RemoteViews 集合）。 */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent.data?.getBooleanQueryParameter("compact", false) == true)

    private class Factory(
        private val ctx: Context,
        private val compact: Boolean,
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
            r: TodoWidgetProvider.Row,
            compact: Boolean = false,
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_todo_item)
            v.setViewPadding(
                R.id.item_root,
                WidgetKit.dp(ctx, if (compact) 2 else 4),
                WidgetKit.dp(ctx, 3),
                WidgetKit.dp(ctx, if (compact) 6 else 8),
                WidgetKit.dp(ctx, 3),
            )
            v.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP, if (compact) 13.5f else 14f)
            val muted = ctx.getColor(R.color.widget_muted)
            v.setInt(R.id.item_check, "setColorFilter", WidgetKit.priorityColor(ctx, r.priority, r.overdue))
            v.setInt(R.id.item_remind, "setColorFilter", muted)
            v.setInt(R.id.item_repeat, "setColorFilter", muted)
            val hasDue = r.code == null && !r.due.isNullOrBlank()
            val showRemind = !compact && r.code == null && r.hasRemind
            val showRepeat = !compact && r.code == null && r.repeating
            val showList = !compact && r.listName != null
            if (r.code != null) {
                v.setViewVisibility(R.id.item_code, View.VISIBLE)
                v.setTextViewText(R.id.item_code, r.code)
                v.setViewVisibility(R.id.item_title, View.GONE)
                v.setViewVisibility(R.id.item_note, View.VISIBLE)
                v.setTextViewText(R.id.item_note, r.title)
                v.setViewVisibility(R.id.item_due, View.GONE)
            } else {
                v.setViewVisibility(R.id.item_code, View.GONE)
                v.setViewVisibility(R.id.item_title, View.VISIBLE)
                v.setTextViewText(R.id.item_title, r.title)
                if (!r.note.isNullOrBlank()) {
                    v.setViewVisibility(R.id.item_note, View.VISIBLE)
                    v.setTextViewText(R.id.item_note, r.note)
                } else {
                    v.setViewVisibility(R.id.item_note, View.GONE)
                }
                if (hasDue) {
                    v.setViewVisibility(R.id.item_due, View.VISIBLE)
                    v.setTextViewText(R.id.item_due, r.due)
                    v.setTextColor(R.id.item_due, ctx.getColor(if (r.overdue) R.color.widget_overdue else R.color.widget_accent))
                } else {
                    v.setViewVisibility(R.id.item_due, View.GONE)
                }
            }
            v.setViewVisibility(R.id.item_remind, if (showRemind) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.item_repeat, if (showRepeat) View.VISIBLE else View.GONE)
            if (showList) {
                v.setViewVisibility(R.id.item_list, View.VISIBLE)
                v.setTextViewText(R.id.item_list, r.listName)
            } else {
                v.setViewVisibility(R.id.item_list, View.GONE)
            }
            v.setViewVisibility(R.id.item_meta, if (hasDue || showRemind || showRepeat || showList) View.VISIBLE else View.GONE)
            v.setOnClickFillInIntent(R.id.item_check, fillIn(TodoWidgetProvider.OP_DONE, r.id))
            val open = fillIn(TodoWidgetProvider.OP_OPEN, r.id)
            v.setOnClickFillInIntent(R.id.item_root, open)
            v.setOnClickFillInIntent(R.id.item_body, open)
            v.setOnClickFillInIntent(R.id.item_title, open)
            v.setOnClickFillInIntent(R.id.item_code, open)
            v.setOnClickFillInIntent(R.id.item_note, open)
            v.setOnClickFillInIntent(R.id.item_meta, open)
            v.setOnClickFillInIntent(R.id.item_due, open)
            v.setOnClickFillInIntent(R.id.item_list, open)
            return v
        }

        private fun fillIn(
            op: String,
            id: String,
        ) = Intent()
            .putExtra(TodoWidgetProvider.EXTRA_OP, op)
            .putExtra(TodoWidgetProvider.EXTRA_TASK_ID, id)
    }
}
