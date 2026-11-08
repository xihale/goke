package app.mintcal.core.parcel

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 取件时段落到具体时间块：快递一般下午/晚上下班后再拿，日程自动排进每天的
 * [startMinute, endMinute)（当日分钟数）。通知到达时已过结束时刻则排到明天。
 * 返回 (块开始, 块分钟数)；end <= start 视为配置无效，返回 null（不排块）。
 */
fun parcelWindow(
    now: LocalDateTime,
    startMinute: Int,
    endMinute: Int,
): Pair<LocalDateTime, Int>? {
    val s = startMinute.coerceIn(0, 24 * 60 - 1)
    val e = endMinute.coerceIn(0, 24 * 60)
    if (e <= s) return null
    var day = now.toLocalDate()
    if (!now.toLocalTime().isBefore(LocalTime.of(e / 60, e % 60))) day = day.plusDays(1)
    val start = LocalDateTime.of(day, LocalTime.of(s / 60, s % 60))
    return start to (e - s)
}
