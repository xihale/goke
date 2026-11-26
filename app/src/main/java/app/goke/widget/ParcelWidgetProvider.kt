package app.goke.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
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
        var hasMini = false
        for (id in appWidgetIds) {
            val size = WidgetKit.sizeClass(context, manager, id)
            if (size.isMini) hasMini = true
            manager.updateAppWidget(
                id,
                WidgetKit.buildResponsive(context, manager, id) { s ->
                    build(context, id, size = s)
                },
            )
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.parcel_list)
        if (hasMini && context.gokeApp.repo.ready.value) {
            NextWidgetProvider.scheduleTick(context, context.gokeApp.repo.snapshot.value, context.gokeApp.settings.value)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val size = WidgetKit.sizeClass(context, newOptions)
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetKit.buildResponsive(context, newOptions) { s ->
                build(context, appWidgetId, size = s)
            },
        )
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.parcel_list)
        if (size.isMini && context.gokeApp.repo.ready.value) {
            NextWidgetProvider.scheduleTick(context, context.gokeApp.repo.snapshot.value, context.gokeApp.settings.value)
        }
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
                mgr.updateAppWidget(
                    id,
                    WidgetKit.buildResponsive(ctx, mgr, id) { size ->
                        build(ctx, id, size = size)
                    },
                )
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.parcel_list)
        }

        fun refreshMini(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ParcelWidgetProvider::class.java))
            for (id in ids) {
                val size = WidgetKit.sizeClass(ctx, mgr, id)
                if (size.isMini) {
                    mgr.updateAppWidget(
                        id,
                        WidgetKit.buildResponsive(ctx, mgr, id) { s ->
                            build(ctx, id, size = s)
                        },
                    )
                }
            }
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

        internal fun build(
            ctx: Context,
            widgetId: Int,
            now: LocalDateTime = LocalDateTime.now(),
            size: WidgetKit.SizeClass = WidgetKit.SizeClass(),
        ): RemoteViews {
            if (size.isMini) {
                val app = ctx.gokeApp
                val snap = if (app.repo.ready.value) app.repo.snapshot.value else null
                return NextWidgetProvider.build(ctx, snap, app.settings.value, now, size)
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_parcel)
            views.setTextViewText(
                R.id.parcel_title,
                ctx.getString(if (size.compactWidth) R.string.widget_parcel_title else R.string.widget_parcel_name),
            )
            views.setTextViewTextSize(R.id.parcel_title, TypedValue.COMPLEX_UNIT_SP, if (size.compactWidth) 14f else 15f)
            views.setViewPadding(
                R.id.parcel_header,
                WidgetKit.dp(ctx, if (size.compactWidth) 12 else 16),
                0,
                WidgetKit.dp(ctx, if (size.compactWidth) 10 else 12),
                WidgetKit.dp(ctx, 6),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val addDp = if (size.compactWidth) 28f else 32f
                views.setViewLayoutWidth(R.id.parcel_add, addDp, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.parcel_add, addDp, TypedValue.COMPLEX_UNIT_DIP)
            }
            val count = rows(ctx, now).size
            if (count > 0) {
                views.setViewVisibility(R.id.parcel_count, View.VISIBLE)
                views.setTextViewText(R.id.parcel_count, count.toString())
            } else {
                views.setViewVisibility(R.id.parcel_count, View.GONE)
            }
            views.setRemoteAdapter(
                R.id.parcel_list,
                Intent(ctx, ParcelWidgetService::class.java)
                    .setData(Uri.parse("goke://widget/parcel/$widgetId?compact=${size.compactWidth}")),
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
