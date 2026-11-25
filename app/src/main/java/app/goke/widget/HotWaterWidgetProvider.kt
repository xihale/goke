package app.goke.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.goke.DeepLinks
import app.goke.MainActivity
import app.goke.R
import app.goke.gokeApp
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterPhase
import app.goke.hotwater.HotWaterState
import app.goke.ui.screens.errorStringRes
import kotlinx.coroutines.launch

/** 桌面智慧热水一键开关小组件：自适应圆角矩形与圆形触感大按钮，大尺寸时展开宿舍与余额信息。 */
class HotWaterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = context.gokeApp.hotWater.state.value
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, build(context, state, manager.getAppWidgetOptions(id)))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val state = context.gokeApp.hotWater.state.value
        appWidgetManager.updateAppWidget(appWidgetId, build(context, state, newOptions))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val app = context.gokeApp
            val pending = goAsync()
            app.scope.launch {
                try {
                    app.hotWater.ensureHydrated()
                    val state = app.hotWater.state.value
                    if (!state.isBound || !hasBlePermission(context)) {
                        val launch =
                            Intent(Intent.ACTION_VIEW, DeepLinks.hotWaterUri(), context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(launch)
                        return@launch
                    }
                    app.hotWater.toggleValve()
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.goke.widget.HOTWATER_TOGGLE"
        private const val RC_OPEN_SCREEN = 3101
        private const val RC_TOGGLE = 3102

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HotWaterWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val state = ctx.gokeApp.hotWater.state.value
            for (id in ids) {
                mgr.updateAppWidget(id, build(ctx, state, mgr.getAppWidgetOptions(id)))
            }
        }

        private fun hasBlePermission(ctx: Context): Boolean {
            val perms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            return perms.all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        internal fun build(
            ctx: Context,
            state: HotWaterState,
            options: Bundle? = null,
        ): RemoteViews {
            val rawW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val rawH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val w = if (rawW > 0) rawW else 140
            val h = if (rawH > 0) rawH else 60
            val ratio = maxOf(w, h).toFloat() / minOf(w, h).coerceAtLeast(1).toFloat()
            val isCircle = ratio <= 1.28f
            val isLarge = if (isCircle) minOf(w, h) >= 115 else (w >= 190 || h >= 115)
            val showMeta = if (isCircle) (isLarge && h >= 135) else isLarge

            val rv = RemoteViews(ctx.packageName, if (isCircle) R.layout.widget_hotwater_circle else R.layout.widget_hotwater)

            val isWorking = state.isBound && (state.phase == HotWaterPhase.WORKING || state.busy)
            val isOpen = state.isBound && state.phase == HotWaterPhase.VALVE_OPEN
            val isError = state.isBound && state.phase == HotWaterPhase.ERROR

            val bgRes =
                when {
                    isWorking -> if (isCircle) R.drawable.widget_hw_circle_working else R.drawable.widget_hw_rect_working
                    isOpen -> if (isCircle) R.drawable.widget_hw_circle_open else R.drawable.widget_hw_rect_open
                    isError -> if (isCircle) R.drawable.widget_hw_circle_error else R.drawable.widget_hw_rect_error
                    else -> if (isCircle) R.drawable.widget_hw_circle_idle else R.drawable.widget_hw_rect_idle
                }
            val fgColor =
                ContextCompat.getColor(
                    ctx,
                    when {
                        isWorking -> R.color.widget_text
                        isOpen -> R.color.widget_hw_open_fg
                        isError -> R.color.widget_overdue
                        else -> R.color.widget_hw_idle_fg
                    },
                )
            val subColor =
                ContextCompat.getColor(
                    ctx,
                    when {
                        isWorking -> R.color.widget_muted
                        isOpen -> R.color.widget_hw_open_sub
                        isError -> R.color.widget_overdue
                        else -> R.color.widget_hw_idle_sub
                    },
                )

            val actionLabel =
                when {
                    !state.isBound -> ctx.getString(R.string.widget_hotwater_btn_bind)
                    isWorking -> ctx.getString(R.string.widget_hotwater_btn_busy)
                    isOpen -> ctx.getString(R.string.widget_hotwater_btn_close)
                    else -> ctx.getString(R.string.widget_hotwater_btn_open)
                }
            val roomLabel =
                if (!state.isBound) {
                    ctx.getString(R.string.widget_hotwater_name)
                } else {
                    state.config.dormLabel.ifBlank { ctx.getString(R.string.widget_hotwater_name) }
                }
            val metaLabel =
                when {
                    !state.isBound -> {
                        ctx.getString(R.string.widget_hotwater_unbound_sub)
                    }

                    isWorking -> {
                        ctx.getString(R.string.hotwater_status_working)
                    }

                    isOpen -> {
                        ctx.getString(R.string.hotwater_status_open)
                    }

                    isError -> {
                        val res =
                            if (state.errorCode == HotWaterErrorCode.NONE) {
                                R.string.hotwater_status_error
                            } else {
                                state.errorCode.errorStringRes()
                            }
                        ctx.getString(res)
                    }

                    state.lastCheckAt > 0L -> {
                        ctx.getString(R.string.hotwater_can_use_amount, state.canUseAmount / 100.0)
                    }

                    else -> {
                        ctx.getString(R.string.hotwater_status_idle)
                    }
                }

            rv.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            rv.setInt(R.id.hw_icon, "setColorFilter", fgColor)
            rv.setTextViewText(R.id.hw_action_label, actionLabel)
            rv.setTextColor(R.id.hw_action_label, fgColor)
            val actionSizeSp = if (isCircle) (if (isLarge) 22f else 14f) else (if (isLarge) 20f else 18f)
            rv.setTextViewTextSize(R.id.hw_action_label, TypedValue.COMPLEX_UNIT_SP, actionSizeSp)

            rv.setTextViewText(R.id.hw_room, roomLabel)
            rv.setTextColor(R.id.hw_room, fgColor)
            rv.setViewVisibility(R.id.hw_room, if (isLarge) View.VISIBLE else View.GONE)

            rv.setTextViewText(R.id.hw_meta, metaLabel)
            rv.setTextColor(R.id.hw_meta, subColor)
            rv.setViewVisibility(R.id.hw_meta, if (showMeta) View.VISIBLE else View.GONE)
            if (!isCircle) {
                rv.setViewVisibility(R.id.hw_info_group, if (isLarge) View.VISIBLE else View.GONE)
            }

            val openIntent = WidgetKit.mainActivityIntent(ctx, RC_OPEN_SCREEN, DeepLinks.hotWaterUri())
            val togglePi =
                PendingIntent.getBroadcast(
                    ctx,
                    RC_TOGGLE,
                    Intent(ctx, HotWaterWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            rv.setOnClickPendingIntent(R.id.widget_root, if (state.isBound) togglePi else openIntent)
            return rv
        }
    }
}
