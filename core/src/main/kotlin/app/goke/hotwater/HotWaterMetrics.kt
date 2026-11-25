package app.goke.hotwater

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** 热水用量、费用与真实洗浴时长工具（按 0.02454 元/L 估算）。 */
object HotWaterMetrics {
    const val DEFAULT_YUAN_PER_LITER = 0.02454

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun durationSecondsBetween(
        startTime: String,
        endTime: String,
    ): Long? {
        val s = parseDateTime(startTime) ?: return null
        val e = parseDateTime(endTime) ?: return null
        return ChronoUnit.SECONDS.between(s, e).coerceAtLeast(0L)
    }

    fun estimateLiters(
        useAmount: Int,
        costYuan: Double,
    ): Double =
        when {
            useAmount > 0 -> useAmount.toDouble()
            costYuan > 0.0 -> costYuan / DEFAULT_YUAN_PER_LITER
            else -> 0.0
        }

    fun estimateCostYuan(useAmount: Int): Double =
        if (useAmount <= 0) {
            0.0
        } else {
            round(useAmount * DEFAULT_YUAN_PER_LITER * 100.0) / 100.0
        }

    fun estimateForOrder(
        order: HotWaterOrder,
        localRecords: List<HotWaterShowerRecord>,
    ): HotWaterOrderMetrics {
        val price = order.usePrice.toDoubleOrNull() ?: 0.0
        val orderTime = parseDateTime(order.createTime)
        val matched =
            localRecords.firstOrNull { rec ->
                if (rec.startTime == order.createTime) {
                    true
                } else if (orderTime != null) {
                    val recTime = parseDateTime(rec.startTime)
                    recTime != null && abs(ChronoUnit.SECONDS.between(recTime, orderTime)) <= 5L
                } else {
                    false
                }
            }
        if (matched != null) {
            val liters = if (matched.liters > 0.0) matched.liters else estimateLiters(0, price)
            return HotWaterOrderMetrics(
                durationSec = matched.durationSec,
                liters = liters,
            )
        }
        val liters = if (price > 0.0) price / DEFAULT_YUAN_PER_LITER else 0.0
        return HotWaterOrderMetrics(
            durationSec = null,
            liters = liters,
        )
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDateTime.parse(trimmed, dateTimeFormatter) }.getOrNull()
    }
}
