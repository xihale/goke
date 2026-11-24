package app.goke.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.goke.GokeApp
import app.goke.MainActivity
import app.goke.R
import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Semester
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.time.SemesterCalendar
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** 小组件共用配色、周次文案、进度阶段与多尺寸自适应判断。 */
internal object WidgetKit {
    /** 一格超窄宽阈值（dp）：小于此值时按 1 列极限紧凑布局（收紧边距、隐藏次要操作）。 */
    const val ULTRA_COMPACT_WIDTH_DP = 105

    /** 两格窄宽阈值（dp）：小于此值时启用紧凑布局（折叠周次/左侧时间列/附加按钮）。 */
    const val COMPACT_WIDTH_DP = 180

    /** 单行矮高阈值（dp）：小于此值时折叠顶栏，让列表直接占满 1 行高度。 */
    const val COMPACT_HEIGHT_DP = 100
    const val PROGRESS_MAX = 1000
    const val ONGOING_TICK_MINUTES = 5L

    data class SizeClass(
        val compactWidth: Boolean = false,
        val ultraCompactWidth: Boolean = false,
        val compactHeight: Boolean = false,
    ) {
        /** 1 列或 1 行等迷你尺寸：全部统一收敛为「下一件事」卡片。 */
        val isMini: Boolean get() = ultraCompactWidth || compactHeight
    }

    enum class TimePhase {
        UPCOMING,
        ONGOING,
        FINISHED,
    }

    fun dp(
        ctx: Context,
        value: Int,
    ): Int = (value * ctx.resources.displayMetrics.density).roundToInt()

    fun sizeClass(
        ctx: Context,
        options: Bundle?,
    ): SizeClass {
        if (options == null) return SizeClass()
        val isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val widthDp =
            if (isLandscape) {
                if (maxW > 0) maxW else minW
            } else {
                if (minW > 0) minW else maxW
            }
        val heightDp =
            if (isLandscape) {
                if (minH > 0) minH else maxH
            } else {
                if (maxH > 0) maxH else minH
            }
        return SizeClass(
            compactWidth = widthDp in 1 until COMPACT_WIDTH_DP,
            ultraCompactWidth = widthDp in 1 until ULTRA_COMPACT_WIDTH_DP,
            compactHeight = heightDp in 1 until COMPACT_HEIGHT_DP,
        )
    }

    fun sizeClass(
        ctx: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ): SizeClass = sizeClass(ctx, manager.getAppWidgetOptions(widgetId))

    fun isCompactWidth(options: Bundle?): Boolean {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        return minWidth in 1 until COMPACT_WIDTH_DP
    }

    fun isCompactWidth(
        manager: AppWidgetManager,
        widgetId: Int,
    ): Boolean = isCompactWidth(manager.getAppWidgetOptions(widgetId))

    fun timePhase(
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ): TimePhase =
        when {
            !end.isAfter(start) -> TimePhase.FINISHED
            now.isBefore(start) -> TimePhase.UPCOMING
            !now.isBefore(end) -> TimePhase.FINISHED
            else -> TimePhase.ONGOING
        }

    fun timeProgress(
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ): Float =
        when (timePhase(start, end, now)) {
            TimePhase.UPCOMING -> {
                0f
            }

            TimePhase.FINISHED -> {
                1f
            }

            TimePhase.ONGOING -> {
                val total = Duration.between(start, end).toMillis().toFloat()
                val elapsed = Duration.between(start, now).toMillis().toFloat()
                if (total <= 0f) 1f else (elapsed / total).coerceIn(0f, 1f)
            }
        }

    /** 进行中进度条千分比（保留 4%–98% 可见区间，避免刚开课看不见或未下课就看似满格）。 */
    fun progressPermille(fraction: Float): Int = (fraction.coerceIn(0.04f, 0.98f) * PROGRESS_MAX).toInt()

    /** 8 组植物课表色，取值与 Theme.kt MintColors / web theme.js 一致（深浅模式由资源解析）。 */
    private val COURSE_BG =
        intArrayOf(
            R.drawable.widget_course_bg0,
            R.drawable.widget_course_bg1,
            R.drawable.widget_course_bg2,
            R.drawable.widget_course_bg3,
            R.drawable.widget_course_bg4,
            R.drawable.widget_course_bg5,
            R.drawable.widget_course_bg6,
            R.drawable.widget_course_bg7,
        )
    private val COURSE_FG =
        intArrayOf(
            R.color.widget_course0_fg,
            R.color.widget_course1_fg,
            R.color.widget_course2_fg,
            R.color.widget_course3_fg,
            R.color.widget_course4_fg,
            R.color.widget_course5_fg,
            R.color.widget_course6_fg,
            R.color.widget_course7_fg,
        )

    fun courseBgRes(colorIndex: Int): Int = COURSE_BG[Math.floorMod(colorIndex, COURSE_BG.size)]

    fun courseFgRes(colorIndex: Int): Int = COURSE_FG[Math.floorMod(colorIndex, COURSE_FG.size)]

    fun courseFg(
        ctx: Context,
        colorIndex: Int,
    ): Int = ctx.getColor(courseFgRes(colorIndex))

    /** 彩底上的副文：与 web `.item-meta`（opacity .8）/ App `fg.copy(alpha = .8f)` 同一档。 */
    fun softFg(color: Int): Int = ColorUtils.setAlphaComponent(color, 204)

    fun priorityColor(
        ctx: Context,
        priority: Int,
        overdue: Boolean = false,
    ): Int =
        when {
            overdue || priority >= 3 -> ctx.getColor(R.color.widget_overdue)
            priority == 2 -> ctx.getColor(R.color.widget_priority_mid)
            priority == 1 -> ctx.getColor(R.color.widget_priority_low)
            else -> ctx.getColor(R.color.widget_accent)
        }

    fun weekBadge(
        ctx: Context,
        semester: Semester?,
        today: LocalDate,
    ): String? {
        val sem = semester ?: return null
        val cal = SemesterCalendar(sem)
        val w = cal.weekOf(today)
        return if (w in 1..sem.totalWeeks) ctx.getString(R.string.week_n, w) else null
    }

    fun eventLocation(
        ctx: Context,
        event: Event,
    ): String? =
        if (event.kind == EventKind.EXAM) {
            JxfwSync.formatExamLocation(
                event.location,
                event.note,
                ctx.getString(R.string.widget_exam_seat_suffix),
            )
        } else {
            event.location.trim().takeIf { it.isNotEmpty() }
        }

    fun taskLocation(
        ctx: Context,
        task: Task,
    ): String? {
        val loc = task.extra["location"]?.trim().orEmpty()
        return if (task.extra["kind"] == EventKind.EXAM.name) {
            JxfwSync.formatExamLocation(
                loc,
                task.note,
                ctx.getString(R.string.widget_exam_seat_suffix),
            )
        } else {
            loc.takeIf { it.isNotEmpty() }
        }
    }

    fun isParcel(task: Task): Boolean = task.source == Source.PARCEL || parcelCode(task) != null

    fun parcelCode(task: Task): String? = (task.extra["code"] ?: task.extra["pickupCode"])?.trim()?.takeIf { it.isNotEmpty() }

    /** 当取件码已单独渲染为胶囊徽章时，去掉标题里重复的「取件 xxx · 」前缀，只留驿站 / 快递名。 */
    fun cleanTitleWithCode(
        ctx: Context,
        task: Task,
    ): String {
        val code = parcelCode(task) ?: return task.title
        val place = (task.extra["place"] ?: task.extra["pickupPlace"])?.trim().orEmpty()
        val company = task.extra["company"]?.trim().orEmpty()
        val combined =
            listOfNotNull(
                place.takeIf { it.isNotEmpty() },
                company.takeIf { it.isNotEmpty() && !place.contains(it) },
            ).joinToString(" · ")
        if (combined.isNotEmpty()) return combined
        val stripped =
            task.title
                .replace(code, "")
                .replace("取件", "")
                .trim(' ', '·', '-', '—', ':', '：')
        return stripped.ifEmpty { ctx.getString(R.string.widget_parcel_station) }
    }

    fun mainActivityIntent(
        ctx: Context,
        requestCode: Int,
        uri: Uri,
    ): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            requestCode,
            Intent(Intent.ACTION_VIEW, uri, ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun overlayIntent(
        ctx: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun refreshAllWidgets(app: GokeApp) {
        val snap = app.repo.snapshot.value
        val st = app.settings.value
        TodoWidgetProvider.refreshAll(app)
        ParcelWidgetProvider.refreshAll(app)
        TodayWidgetProvider.refreshAll(app, snap, st)
        ScheduleWidgetProvider.refreshAll(app, snap, st)
        NextWidgetProvider.refreshAll(app, snap, st)
    }
}
