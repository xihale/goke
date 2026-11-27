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
            if (o.course.isExam) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = (fg and 0x00FFFFFF) or (72 shl 24)
                c.drawRoundRect(r, 14f, 14f, p)
                p.style = Paint.Style.FILL

                val display = JxfwSync.splitExamDisplay(o.course.name, ctx.getString(R.string.kind_exam))
                val roomText = if (o.room.isNotBlank()) ctx.formatRooms(o.room) else ""
                val seatText =
                    JxfwSync.extractExamSeat(o.course.note)?.let {
                        "$it${ctx.getString(R.string.widget_exam_seat_suffix)}"
                    }
                val span = ei - si + 1
                if (span >= 2) {
                    drawExamBookmark(c, r, fg, p, tp, display, roomText, seatText, if (st.showTeacher) o.course.teacher else "", span)
                } else {
                    val pillText = display.badgeShort
                    tp.textSize = 16f
                    tp.typeface = Typeface.DEFAULT_BOLD
                    tp.color = fg
                    val pillW = tp.measureText(pillText) + 16f
                    p.color = (fg and 0x00FFFFFF) or (46 shl 24)
                    c.drawRoundRect(RectF(r.left + 8f, r.top + 8f, r.left + 8f + pillW, r.top + 32f), 12f, 12f, p)
                    c.drawText(pillText, r.left + 16f, r.top + 26f, tp)

                    val text =
                        buildString {
                            append(display.title)
                            val loc = roomText.ifBlank { seatText.orEmpty() }
                            if (loc.isNotBlank()) append("\n").append(loc)
                        }
                    tp.textSize = 20f
                    tp.typeface = Typeface.DEFAULT_BOLD
                    val sl =
                        StaticLayout.Builder
                            .obtain(text, 0, text.length, tp, (r.width() - 16).toInt().coerceAtLeast(1))
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .setMaxLines(2)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .build()
                    c.save()
                    c.translate(r.left + 8f, r.top + 38f)
                    sl.draw(c)
                    c.restore()
                }
            } else {
                val text =
                    buildString {
                        append(o.course.name)
                        if (o.room.isNotBlank()) append("\n@").append(o.room)
                        if (st.showTeacher && o.course.teacher.isNotBlank()) append("\n").append(o.course.teacher)
                    }
                tp.color = fg
                tp.textSize = 21f
                tp.typeface = Typeface.DEFAULT_BOLD
                val sl =
                    StaticLayout.Builder
                        .obtain(text, 0, text.length, tp, (r.width() - 16).toInt())
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setMaxLines(((r.height() - 16) / 26).toInt().coerceAtLeast(1))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                c.save()
                c.translate(r.left + 8, r.top + 8)
                sl.draw(c)
                c.restore()
            }
        }
        return bmp
    }

    private fun drawExamBookmark(
        c: Canvas,
        r: RectF,
        fg: Int,
        p: Paint,
        tp: TextPaint,
        display: JxfwSync.ExamDisplay,
        roomText: String,
        seatText: String?,
        teacher: String,
        span: Int,
    ) {
        val hasFooter = roomText.isNotBlank() || seatText != null
        val footerH =
            if (!hasFooter) {
                0f
            } else if (roomText.isNotBlank() && seatText != null) {
                44f
            } else {
                32f
            }
        // 底部地点不铺额外背景色，保持与卡片正常底色一致，仅用细线分隔
        if (hasFooter) {
            val fy = r.bottom - footerH
            p.color = (fg and 0x00FFFFFF) or (42 shl 24)
            p.strokeWidth = 1.5f
            c.drawLine(r.left + 10f, fy, r.right - 10f, fy, p)

            tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT
            tp.textSize = 16.5f
            tp.color = (fg and 0x00FFFFFF) or (220 shl 24)
            if (roomText.isNotBlank() && seatText != null) {
                val roomStr = TextUtils.ellipsize(roomText, tp, r.width() - 12f, TextUtils.TruncateAt.END).toString()
                c.drawText(roomStr, r.centerX(), fy + 18f, tp)
                tp.textSize = 15f
                tp.color = (fg and 0x00FFFFFF) or (184 shl 24)
                c.drawText(seatText, r.centerX(), fy + 36f, tp)
            } else {
                val only = roomText.ifBlank { seatText.orEmpty() }
                val onlyStr = TextUtils.ellipsize(only, tp, r.width() - 12f, TextUtils.TruncateAt.END).toString()
                c.drawText(onlyStr, r.centerX(), fy + 21f, tp)
            }
            tp.textAlign = Paint.Align.LEFT
        }

        // 左列：竖排印章胶囊 + 竖排教师
        val sideCx = r.left + r.width() * 0.30f
        val sealW = 28f
        val sealH = 48f
        val sealTop = r.top + 14f
        p.color = (fg and 0x00FFFFFF) or (46 shl 24)
        c.drawRoundRect(RectF(sideCx - sealW / 2f, sealTop, sideCx + sealW / 2f, sealTop + sealH), 9f, 9f, p)

        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textSize = 16.5f
        tp.color = fg
        val badgeLines = JxfwSync.formatVerticalText(display.badgeShort, 2).split("\n")
        badgeLines.forEachIndexed { idx, line ->
            c.drawText(line, sideCx, sealTop + 20f + idx * 20f, tp)
        }

        if (teacher.isNotBlank()) {
            tp.typeface = Typeface.DEFAULT
            tp.textSize = 17.5f
            tp.color = (fg and 0x00FFFFFF) or (175 shl 24)
            val teacherLines = JxfwSync.formatVerticalText(teacher, if (span >= 3) 4 else 3).split("\n")
            teacherLines.forEachIndexed { idx, line ->
                c.drawText(line, sideCx, sealTop + sealH + 24f + idx * 21f, tp)
            }
        }

        // 右列：大字竖排课名
        val titleCx = r.left + r.width() * 0.67f
        val tokens = JxfwSync.tokenizeVerticalTitle(display.title)
        val maxTokens =
            when {
                span >= 3 -> if (hasFooter) 9 else 11
                hasFooter -> if (seatText != null) 6 else 7
                else -> 7
            }
        val titleLines = JxfwSync.formatVerticalText(display.title, maxTokens).split("\n")
        val titleSize =
            when {
                tokens.size <= 4 -> 24f
                tokens.size <= 6 -> 21.5f
                else -> 19.5f
            }
        val titleStep = titleSize * 1.16f
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.color = fg
        titleLines.forEachIndexed { idx, tok ->
            tp.textSize = if (tok.length > 1) titleSize * 0.78f else titleSize
            c.drawText(tok, titleCx, sealTop + 22f + idx * titleStep, tp)
        }
        tp.textAlign = Paint.Align.LEFT
    }
}
