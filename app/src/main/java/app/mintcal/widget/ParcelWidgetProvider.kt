package app.mintcal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import app.mintcal.DeepLinks
import app.mintcal.R
import app.mintcal.core.todo.TaskLogic
import app.mintcal.mintApp
import app.mintcal.ui.dueLabel
import java.time.LocalDateTime

/** 「待取快递」专属桌面小组件：票据式大号取件码、驿站地点、截止时刻，一键勾取。 */
@Suppress("DEPRECATION")
class ParcelWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) manager.updateAppWidget(id, build(context, id))
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.parcel_list)
    }

    data class Row(
        val id: String,
        val code: String,
        val place: String,
        val due: String?,
        val overdue: Boolean,
    )

    companion object {
        private const val MAX_ROWS = 30

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ParcelWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, build(ctx, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.parcel_list)
        }

        fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.mintApp
            if (!app.repo.ready.value) return emptyList()
            val snap =
                app.repo.snapshot.value
                    .live()
            val today = now.toLocalDate()
            return TaskLogic
                .topLevel(snap.tasks)
                .filter { !it.done && WidgetKit.isParcel(it) }
                .sortedWith(
                    compareBy<app.mintcal.core.model.Task>(
                        { it.due == null },
                        { it.due },
                        { it.updatedAt },
                    ),
                ).take(MAX_ROWS)
                .map { t ->
                    val due = TaskLogic.effectiveDue(t, snap.currentSemester, snap.courses, snap.exceptions, now)
                    val overdue = due != null && due.isBefore(now)
                    Row(
                        id = t.id,
                        code = WidgetKit.parcelCode(t) ?: ctx.getString(R.string.widget_parcel_title),
                        place = WidgetKit.cleanTitleWithCode(ctx, t),
                        due = due?.let { ctx.dueLabel(it, t.dueHasTime, today) },
                        overdue = overdue,
                    )
                }
        }

        fun build(
            ctx: Context,
            widgetId: Int,
            now: LocalDateTime = LocalDateTime.now(),
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_parcel)
            val count = rows(ctx, now).size
            if (count > 0) {
                views.setViewVisibility(R.id.parcel_count, View.VISIBLE)
                views.setTextViewText(R.id.parcel_count, count.toString())
            } else {
                views.setViewVisibility(R.id.parcel_count, View.GONE)
            }
            views.setRemoteAdapter(
                R.id.parcel_list,
                Intent(ctx, ParcelWidgetService::class.java).setData(Uri.parse("mintcal://widget/parcel/$widgetId")),
            )
            views.setEmptyView(R.id.parcel_list, R.id.parcel_empty)
            views.setPendingIntentTemplate(
                R.id.parcel_list,
                PendingIntent.getActivity(
                    ctx,
                    300 + widgetId,
                    Intent(ctx, WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setOnClickPendingIntent(R.id.parcel_header, WidgetKit.mainActivityIntent(ctx, 30, DeepLinks.parcelUri()))
            views.setOnClickPendingIntent(R.id.parcel_add, WidgetKit.mainActivityIntent(ctx, 31, DeepLinks.addUri()))
            return views
        }
    }
}
