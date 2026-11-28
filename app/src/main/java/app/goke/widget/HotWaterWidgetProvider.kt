package app.goke.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import app.goke.ui.FeedbackEvent
import app.goke.ui.screens.errorStringRes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                    if (app.hotWater.toggleValve().ok) {
                        app.feedback.play(FeedbackEvent.SUCCESS)
                    }
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
            val sizes = WidgetKit.extractAppWidgetSizes(options)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size == 1) {
                return buildForSize(
                    ctx,
                    state,
                    sizes[0].width.roundToInt(),
                    sizes[0].height.roundToInt(),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
                return RemoteViews(
                    sizes.associateWith { sz ->
                        buildForSize(ctx, state, sz.width.roundToInt(), sz.height.roundToInt())
                    },
                )
            }

            val isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0
            val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            val rawW = if (isLandscape) (if (maxW > 0) maxW else minW) else (if (minW > 0) minW else maxW)
            val rawH = if (isLandscape) (if (minH > 0) minH else maxH) else (if (maxH > 0) maxH else minH)
            val w = if (rawW > 0) rawW else 140
            val h = if (rawH > 0) rawH else 60
            return buildForSize(ctx, state, w, h)
        }

        private fun buildForSize(
            ctx: Context,
            state: HotWaterState,
            w: Int,
            h: Int,
        ): RemoteViews {
            val diameter = minOf(w, h)
            val ratio = maxOf(w, h).toFloat() / diameter.coerceAtLeast(1).toFloat()
            val isCircle = if (h >= w) ratio <= 1.45f else ratio <= 1.35f
            val isVerticalRect = !isCircle && h > w
            val isLarge =
                when {
                    isCircle -> diameter >= 115
                    isVerticalRect -> h >= 145
                    else -> w >= 190
                }
            val isXLarge = isCircle && diameter >= 200
            val isHuge = isCircle && diameter >= 280
            val isTallWide = !isCircle && !isVerticalRect && w >= 240 && h >= 130
            val showMeta =
                when {
                    isCircle -> diameter >= 130
                    isVerticalRect -> h >= 175
                    else -> isLarge
                }

            val layoutRes =
                when {
                    isCircle -> R.layout.widget_hotwater_circle
                    isVerticalRect -> R.layout.widget_hotwater_vertical
                    else -> R.layout.widget_hotwater
                }
            val rv = RemoteViews(ctx.packageName, layoutRes)

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

            if (isCircle) {
                rv.setImageViewResource(R.id.hw_circle_bg, bgRes)
            } else {
                rv.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            }
            rv.setInt(R.id.hw_icon, "setColorFilter", fgColor)
            rv.setTextViewText(R.id.hw_action_label, actionLabel)
            rv.setTextColor(R.id.hw_action_label, fgColor)
            val actionSizeSp =
                when {
                    isCircle -> {
                        when {
                            isHuge -> 30f
                            isXLarge -> 26f
                            isLarge -> 20f
                            diameter < 66 -> 13f
                            else -> 14f
                        }
                    }

                    isVerticalRect -> {
                        if (w >= 130) 20f else 14f
                    }

                    isTallWide -> {
                        24f
                    }

                    else -> {
                        if (isLarge) 20f else 18f
                    }
                }
            rv.setTextViewTextSize(R.id.hw_action_label, TypedValue.COMPLEX_UNIT_SP, actionSizeSp)

            rv.setTextViewText(R.id.hw_room, roomLabel)
            rv.setTextColor(R.id.hw_room, fgColor)
            rv.setViewVisibility(R.id.hw_room, if (isLarge) View.VISIBLE else View.GONE)

            rv.setTextViewText(R.id.hw_meta, metaLabel)
            rv.setTextColor(R.id.hw_meta, subColor)
            rv.setViewVisibility(R.id.hw_meta, if (showMeta) View.VISIBLE else View.GONE)
            when {
                isCircle -> {
                    val roomSizeSp =
                        when {
                            isHuge -> 16f
                            isXLarge -> 14f
                            else -> 12f
                        }
                    val metaSizeSp =
                        when {
                            isHuge -> 14f
                            isXLarge -> 12.5f
                            else -> 11f
                        }
                    rv.setTextViewTextSize(R.id.hw_room, TypedValue.COMPLEX_UNIT_SP, roomSizeSp)
                    rv.setTextViewTextSize(R.id.hw_meta, TypedValue.COMPLEX_UNIT_SP, metaSizeSp)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val iconDp =
                            when {
                                isHuge -> 46f
                                isXLarge -> 38f
                                isLarge -> 30f
                                diameter < 66 -> 20f
                                else -> 22f
                            }
                        rv.setViewLayoutWidth(R.id.hw_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                        rv.setViewLayoutHeight(R.id.hw_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    val extraH = ((w - diameter) / 2f).coerceAtLeast(0f)
                    val padHDp = (extraH + diameter * 0.14f).roundToInt().coerceAtLeast(8)
                    val padVDp = (diameter * 0.10f).roundToInt().coerceAtLeast(6)
                    val padHPx = WidgetKit.dp(ctx, padHDp)
                    val padVPx = WidgetKit.dp(ctx, padVDp)
                    rv.setViewPadding(R.id.hw_content, padHPx, padVPx, padHPx, padVPx)
                }

                isVerticalRect -> {
                    val wideVert = w >= 130
                    rv.setTextViewTextSize(R.id.hw_room, TypedValue.COMPLEX_UNIT_SP, if (wideVert) 13f else 11f)
                    rv.setTextViewTextSize(R.id.hw_meta, TypedValue.COMPLEX_UNIT_SP, if (wideVert) 11.5f else 10f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val iconDp = if (wideVert) 30f else 24f
                        rv.setViewLayoutWidth(R.id.hw_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                        rv.setViewLayoutHeight(R.id.hw_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    val padHPx = WidgetKit.dp(ctx, if (w < 105) 8 else 14)
                    val padVPx = WidgetKit.dp(ctx, 12)
                    rv.setViewPadding(R.id.hw_content, padHPx, padVPx, padHPx, padVPx)
                }

                else -> {
                    rv.setViewVisibility(R.id.hw_info_group, if (isLarge) View.VISIBLE else View.GONE)
                    if (isTallWide) {
                        rv.setTextViewTextSize(R.id.hw_room, TypedValue.COMPLEX_UNIT_SP, 15f)
                        rv.setTextViewTextSize(R.id.hw_meta, TypedValue.COMPLEX_UNIT_SP, 13f)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            rv.setViewLayoutWidth(R.id.hw_icon, 32f, TypedValue.COMPLEX_UNIT_DIP)
                            rv.setViewLayoutHeight(R.id.hw_icon, 32f, TypedValue.COMPLEX_UNIT_DIP)
                        }
                    }
                }
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
