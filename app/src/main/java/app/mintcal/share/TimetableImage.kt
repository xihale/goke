package app.mintcal.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import app.mintcal.R
import app.mintcal.core.model.Snapshot
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.data.Settings
import app.mintcal.ui.hm
import app.mintcal.ui.monthDay
import app.mintcal.ui.theme.mintColors
import app.mintcal.ui.weekdayShort

/** 把某一周的课表画成一张 PNG（亮色，便于分享）。 */
object TimetableImage {
    private const val W = 1080
    private const val LEFT = 96
    private const val HEADER = 120
    private const val ROW = 118
    private const val PAD = 36

    fun render(ctx: Context, snap: Snapshot, st: Settings, week: Int): Bitmap? {
        val sem = snap.currentSemester ?: return null
        val cal = SemesterCalendar(sem)
        val periods = sem.periods.sortedBy { it.index }
        val colors = mintColors(false)
        val title = ctx.getString(R.string.image_title, sem.name, week)
        val titleH = 110
        val h = titleH + HEADER + ROW * periods.size + PAD
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF2F7F5.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

        tp.color = 0xFF24312D.toInt(); tp.textSize = 44f; tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(title, PAD.toFloat(), 74f, tp)

        val colW = (W - LEFT - PAD) / 7f
        val top = titleH.toFloat()
        // 星期头
        for (d in 1..7) {
            val x = LEFT + (d - 1) * colW
            tp.typeface = Typeface.DEFAULT_BOLD; tp.textSize = 30f; tp.color = 0xFF24312D.toInt(); tp.textAlign = Paint.Align.CENTER
            c.drawText(ctx.weekdayShort(d), x + colW / 2, top + 50f, tp)
            tp.typeface = Typeface.DEFAULT; tp.textSize = 22f; tp.color = 0xFF7B8B86.toInt()
            c.drawText(ctx.monthDay(cal.dateOf(week, d)), x + colW / 2, top + 88f, tp)
        }
        tp.textAlign = Paint.Align.LEFT
        // 节次与分隔线
        p.color = 0xFFDDE7E3.toInt(); p.strokeWidth = 2f
        for ((i, pt) in periods.withIndex()) {
            val y = top + HEADER + i * ROW
            c.drawLine(PAD.toFloat(), y, (W - PAD).toFloat(), y, p)
            tp.textAlign = Paint.Align.CENTER; tp.typeface = Typeface.DEFAULT_BOLD; tp.textSize = 28f; tp.color = 0xFF24312D.toInt()
            c.drawText("${pt.index}", PAD + (LEFT - PAD) / 2f, y + 46f, tp)
            tp.typeface = Typeface.DEFAULT; tp.textSize = 17f; tp.color = 0xFF7B8B86.toInt()
            c.drawText(pt.start.hm(), PAD + (LEFT - PAD) / 2f, y + 72f, tp)
            c.drawText(pt.end.hm(), PAD + (LEFT - PAD) / 2f, y + 94f, tp)
        }
        tp.textAlign = Paint.Align.LEFT

        val occs = CourseExpander.occurrencesInWeek(sem, snap.courses, snap.exceptions, week)
        for (o in occs) {
            val dow = o.date.dayOfWeek.value
            val si = periods.indexOfFirst { it.index == o.startPeriod }.takeIf { it >= 0 } ?: continue
            val ei = periods.indexOfFirst { it.index == o.endPeriod }.takeIf { it >= 0 } ?: si
            val x = LEFT + (dow - 1) * colW + 3
            val y = top + HEADER + si * ROW + 3
            val r = RectF(x, y, x + colW - 6, top + HEADER + (ei + 1) * ROW - 3)
            p.color = colors.bg(o.course.colorIndex).toArgb()
            c.drawRoundRect(r, 14f, 14f, p)
            val fg = colors.fg(o.course.colorIndex).toArgb()
            val text = buildString {
                append(o.course.name)
                if (o.room.isNotBlank()) append("\n@").append(o.room)
                if (st.showTeacher && o.course.teacher.isNotBlank()) append("\n").append(o.course.teacher)
            }
            tp.color = fg; tp.textSize = 21f; tp.typeface = Typeface.DEFAULT_BOLD
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, (r.width() - 16).toInt())
                .setEllipsize(TextUtils.TruncateAt.END).setMaxLines(((r.height() - 16) / 26).toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            c.save(); c.translate(r.left + 8, r.top + 8); sl.draw(c); c.restore()
        }
        return bmp
    }
}
