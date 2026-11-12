package app.mintcal.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mintcal.MainActivity
import app.mintcal.R
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.time.SemesterCalendar
import java.time.LocalDate

/** 小组件共用配色、周次文案与快递标题清洗。 */
internal object WidgetKit {
    val COURSE_PALETTE =
        intArrayOf(
            0xFF4FA796.toInt(),
            0xFF5B8DEF.toInt(),
            0xFFE0A030.toInt(),
            0xFFD9695F.toInt(),
            0xFF9B7BD0.toInt(),
            0xFF4DB6D0.toInt(),
            0xFFE07FA8.toInt(),
            0xFF8AA64A.toInt(),
        )

    fun courseColor(colorIndex: Int): Int = COURSE_PALETTE[Math.floorMod(colorIndex, COURSE_PALETTE.size)]

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
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
