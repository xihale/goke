package app.goke.share

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
import app.goke.R
import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Snapshot
import app.goke.core.recurrence.CourseExpander
import app.goke.core.time.SemesterCalendar
import app.goke.data.Settings
import app.goke.ui.formatRooms
import app.goke.ui.hm
import app.goke.ui.monthDay
import app.goke.ui.theme.mintColors
import app.goke.ui.weekdayShort

/** 把某一周的课表画成一张 PNG（亮色，便于分享）。 */
object TimetableImage {
    private const val W = 1080
    private const val LEFT = 96
    private const val HEADER = 120
    private const val ROW = 118
    private const val PAD = 36

    fun render(
        ctx: Context,
        snap: Snapshot,
        st: Settings,
        week: Int,
    ): Bitmap? {
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

        tp.color = 0xFF24312D.toInt()
        tp.textSize = 44f
        tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(title, PAD.toFloat(), 74f, tp)

        val colW = (W - LEFT - PAD) / 7f
        val top = titleH.toFloat()
        // 星期头
        for (d in 1..7) {
            val x = LEFT + (d - 1) * colW
            tp.typeface = Typeface.DEFAULT_BOLD
            tp.textSize = 30f
            tp.color = 0xFF24312D.toInt()
            tp.textAlign = Paint.Align.CENTER
            c.drawText(ctx.weekdayShort(d), x + colW / 2, top + 50f, tp)
            tp.typeface = Typeface.DEFAULT
            tp.textSize = 22f
            tp.color = 0xFF7B8B86.toInt()
            c.drawText(ctx.monthDay(cal.dateOf(week, d)), x + colW / 2, top + 88f, tp)
        }
        tp.textAlign = Paint.Align.LEFT
        // 节次与分隔线
        p.color = 0xFFDDE7E3.toInt()
        p.strokeWidth = 2f
        for ((i, pt) in periods.withIndex()) {
            val y = top + HEADER + i * ROW
            c.drawLine(PAD.toFloat(), y, (W - PAD).toFloat(), y, p)
            tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT_BOLD
            tp.textSize = 28f
            tp.color = 0xFF24312D.toInt()
            c.drawText("${pt.index}", PAD + (LEFT - PAD) / 2f, y + 46f, tp)
            tp.typeface = Typeface.DEFAULT
            tp.textSize = 17f
            tp.color = 0xFF7B8B86.toInt()
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
            val roomText = if (o.room.isNotBlank()) ctx.formatRooms(o.room) else ""
            val teacherText = if (st.showTeacher) o.course.teacher else ""
            val innerW = (r.width() - 18f).toInt().coerceAtLeast(1)
            var contentTop = r.top + 10f
            val titleText: String
            val extraMeta: List<String>
            if (o.course.isExam) {
                val display = JxfwSync.splitExamDisplay(o.course.name, ctx.getString(R.string.kind_exam))
                val seatText =
                    JxfwSync.extractExamSeat(o.course.note)?.let {
                        "$it${ctx.getString(R.string.widget_exam_seat_suffix)}"
                    }
                val pillText = display.badgeShort
                tp.textSize = 15.5f
                tp.typeface = Typeface.DEFAULT_BOLD
                tp.color = fg
                val pillW = tp.measureText(pillText) + 14f
                p.color = (fg and 0x00FFFFFF) or (46 shl 24)
                c.drawRoundRect(RectF(r.left + 9f, contentTop, r.left + 9f + pillW, contentTop + 23f), 11.5f, 11.5f, p)
                c.drawText(pillText, r.left + 16f, contentTop + 17f, tp)
                contentTop += 29f
                titleText = display.title
                extraMeta =
                    buildList {
                        if (roomText.isNotBlank()) add(roomText)
                        if (seatText != null) add(seatText)
                        if (teacherText.isNotBlank()) add(teacherText)
                    }
            } else {
                titleText = o.course.name
                extraMeta =
                    buildList {
                        if (roomText.isNotBlank()) add("@$roomText")
                        if (teacherText.isNotBlank()) add(teacherText)
                    }
            }

            // 标题微调字号，避免末行只剩 1 个孤字
            tp.color = fg
            tp.typeface = Typeface.DEFAULT_BOLD
            val titleMaxLines = (((r.bottom - contentTop - 10f) / 26f).toInt()).coerceIn(1, 4)
            var chosenSize = 21f
            var titleLayout: StaticLayout? = null
            for (sz in floatArrayOf(21f, 20f, 19f, 18f)) {
                tp.textSize = sz
                val sl =
                    StaticLayout.Builder
                        .obtain(titleText, 0, titleText.length, tp, innerW)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setMaxLines(titleMaxLines)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                chosenSize = sz
                titleLayout = sl
                if (sl.lineCount <= 1) break
                val last = sl.lineCount - 1
                val lastStr = titleText.substring(sl.getLineStart(last), sl.getLineEnd(last)).trim()
                if (lastStr.length != 1) break
            }
            tp.textSize = chosenSize
            val sl = titleLayout!!
            c.save()
            c.translate(r.left + 9f, contentTop)
            sl.draw(c)
            c.restore()

            var metaY = contentTop + sl.height + 4f
            if (extraMeta.isNotEmpty() && metaY + 18f < r.bottom - 6f) {
                val metaStr = extraMeta.joinToString("\n")
                tp.color = (fg and 0x00FFFFFF) or (210 shl 24)
                tp.typeface = Typeface.DEFAULT
                tp.textSize = 17.5f
                val availLines = ((r.bottom - metaY - 8f) / 21f).toInt().coerceAtLeast(1)
                val metaLayout =
                    StaticLayout.Builder
                        .obtain(metaStr, 0, metaStr.length, tp, innerW)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setMaxLines(availLines)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                c.save()
                c.translate(r.left + 9f, metaY)
                metaLayout.draw(c)
                c.restore()
            }
        }
        return bmp
    }
}
