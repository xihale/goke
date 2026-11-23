package app.goke.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.goke.DeepLinks
import app.goke.R
import app.goke.core.todo.TaskLogic
import app.goke.gokeApp
import app.goke.ui.dueLabel
import java.time.LocalDateTime

/** 「待取快递」专属桌面小组件：票据式大号取件码、驿站地点、截止时刻，一键勾取。 */
@Suppress("DEPRECATION")
class ParcelWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            val compact = WidgetKit.isCompactWidth(manager, id)
            manager.updateAppWidget(id, build(context, id, compact = compact))
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.parcel_list)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            build(context, appWidgetId, compact = WidgetKit.isCompactWidth(newOptions)),
        )
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.parcel_list)
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
            for (id in ids) {
                val compact = WidgetKit.isCompactWidth(mgr, id)
                mgr.updateAppWidget(id, build(ctx, id, compact = compact))
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.parcel_list)
        }

        fun rows(
            ctx: Context,
            now: LocalDateTime = LocalDateTime.now(),
        ): List<Row> {
            val app = ctx.gokeApp
            if (!app.repo.ready.value) return emptyList()
            val snap =
                app.repo.snapshot.value
                    .live()
            val today = now.toLocalDate()
            return TaskLogic
                .topLevel(snap.tasks)
                .filter { !it.done && WidgetKit.isParcel(it) }
                .sortedWith(
                    compareBy<app.goke.core.model.Task>(
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
            compact: Boolean = false,
        ): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_parcel)
            views.setTextViewText(
                R.id.parcel_title,
                ctx.getString(if (compact) R.string.widget_parcel_title else R.string.widget_parcel_name),
            )
            val count = rows(ctx, now).size
            if (count > 0) {
                views.setViewVisibility(R.id.parcel_count, View.VISIBLE)
                views.setTextViewText(R.id.parcel_count, count.toString())
            } else {
                views.setViewVisibility(R.id.parcel_count, View.GONE)
            }
            views.setRemoteAdapter(
                R.id.parcel_list,
                Intent(ctx, ParcelWidgetService::class.java).setData(Uri.parse("goke://widget/parcel/$widgetId")),
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
            val openParcel = WidgetKit.mainActivityIntent(ctx, 30, DeepLinks.parcelUri())
            views.setOnClickPendingIntent(R.id.parcel_header, openParcel)
            views.setOnClickPendingIntent(R.id.parcel_empty, openParcel)
            views.setOnClickPendingIntent(R.id.widget_root, openParcel)
            views.setOnClickPendingIntent(R.id.parcel_add, WidgetKit.overlayIntent(ctx, 31, WidgetOverlayActivity.addIntent(ctx)))
            return views
        }
    }
}
