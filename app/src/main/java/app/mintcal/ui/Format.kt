package app.mintcal.ui

import android.content.Context
import app.mintcal.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Context.weekdayShort(dow: Int): String = resources.getStringArray(R.array.weekday_short)[(dow - 1).coerceIn(0, 6)]
fun Context.weekdayFull(dow: Int): String = resources.getStringArray(R.array.weekday_full)[(dow - 1).coerceIn(0, 6)]

fun Context.monthDay(d: LocalDate): String = getString(R.string.fmt_month_day, d.monthValue, d.dayOfMonth)
fun Context.monthDayWeekday(d: LocalDate): String = getString(R.string.fmt_month_day_weekday, d.monthValue, d.dayOfMonth, weekdayFull(d.dayOfWeek.value))
fun Context.yearMonth(d: LocalDate): String = getString(R.string.fmt_year_month, d.year, d.monthValue)
fun Context.fullDate(d: LocalDate): String = getString(R.string.fmt_full_date, d.year, d.monthValue, d.dayOfMonth, weekdayFull(d.dayOfWeek.value))

private val HM = DateTimeFormatter.ofPattern("HH:mm")
fun LocalTime.hm(): String = format(HM)
fun LocalDateTime.hm(): String = format(HM)

/** 列表里的紧凑日期：今天 / 明天 / 10月22日，带时间则追加 HH:mm。 */
fun Context.dueLabel(dt: LocalDateTime, hasTime: Boolean, today: LocalDate): String {
    val d = dt.toLocalDate()
    val day = when (d) {
        today -> getString(R.string.today)
        today.plusDays(1) -> getString(R.string.tomorrow)
        today.minusDays(1) -> getString(R.string.yesterday)
        else -> monthDay(d)
    }
    return if (hasTime) "$day ${dt.hm()}" else day
}
