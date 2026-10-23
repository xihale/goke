package app.mintcal.core.parcel

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ParcelInfo(
    val code: String,
    val place: String,
    val company: String,
    val deadline: LocalDateTime?,
    val raw: String,
)

/**
 * 快递取件通知识别：本地规则（取件码、驿站 / 地址、截止时间）。
 * 只处理通知文本，不读取短信；识别失败返回 null（可选交给 AI，发送前脱敏）。
 */
object ParcelParser {
    private val KEYWORDS = listOf("取件", "取货", "提货", "驿站", "快递柜", "丰巢", "菜鸟", "快递", "代收")
    private val CODE_PATTERNS =
        listOf(
            Regex("取件码[:：\\s]*([A-Za-z0-9\\-]{3,12})"),
            Regex("取货码[:：\\s]*([A-Za-z0-9\\-]{3,12})"),
            Regex("提货码[:：\\s]*([A-Za-z0-9\\-]{3,12})"),
            Regex("凭[「【\\[]?([A-Za-z0-9\\-]{3,12})[」】\\]]?(?:到|取|至)"),
            Regex("(?:验证码|密码)[:：\\s]*([A-Za-z0-9\\-]{3,12})"),
        )
    private val COMPANIES = listOf("菜鸟驿站", "菜鸟", "丰巢", "京东快递", "京东", "顺丰", "中通", "圆通", "韵达", "申通", "邮政", "EMS", "极兔", "德邦", "天猫")
    private val PLACE_PATTERNS =
        listOf(
            Regex("(?:到|至|在|前往)([^，。,.；;！!\\s]{2,30}?(?:驿站|快递柜|丰巢柜|代收点|服务站|超市|门店))"),
            Regex("地址[:：\\s]*([^，。,.；;！!\\n]{2,40})"),
            Regex("([^，。,.；;！!\\s]{2,30}?(?:驿站|快递柜|代收点))"),
        )

    fun parse(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): ParcelInfo? {
        if (KEYWORDS.none { text.contains(it) }) return null
        val code = CODE_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) } ?: return null
        val company = COMPANIES.firstOrNull { text.contains(it) }.orEmpty()
        val place =
            PLACE_PATTERNS
                .firstNotNullOfOrNull {
                    it
                        .find(text)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                }.orEmpty()
        return ParcelInfo(code, place, company, deadline(text, now), text)
    }

    private fun deadline(
        text: String,
        now: LocalDateTime,
    ): LocalDateTime? {
        val today = now.toLocalDate()
        Regex("(\\d{1,2})月(\\d{1,2})[日号]?\\s*(?:(\\d{1,2})[:：点时](\\d{1,2})?)?\\s*前").find(text)?.let { m ->
            return runCatching {
                var d = LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                if (d.isBefore(today.minusDays(30))) d = d.plusYears(1)
                val h = m.groupValues[3].toIntOrNull()
                if (h != null) {
                    LocalDateTime.of(d, LocalTime.of(h.coerceIn(0, 23), m.groupValues[4].toIntOrNull()?.coerceIn(0, 59) ?: 0))
                } else {
                    d.atTime(23, 59)
                }
            }.getOrNull()
        }
        Regex("(今日|今天|明日|明天|后天)\\s*(?:(\\d{1,2})[:：点时](\\d{1,2})?)?\\s*前").find(text)?.let { m ->
            val d =
                when (m.groupValues[1]) {
                    "今日", "今天" -> today
                    "明日", "明天" -> today.plusDays(1)
                    else -> today.plusDays(2)
                }
            val h = m.groupValues[2].toIntOrNull()
            return if (h != null) LocalDateTime.of(d, LocalTime.of(h.coerceIn(0, 23), m.groupValues[3].toIntOrNull()?.coerceIn(0, 59) ?: 0)) else d.atTime(23, 59)
        }
        Regex("(\\d{1,2})[:：](\\d{2})\\s*前").find(text)?.let { m ->
            return runCatching { LocalDateTime.of(today, LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())) }.getOrNull()
        }
        Regex("(\\d+)\\s*(?:天|日)内").find(text)?.let { m -> return today.plusDays(m.groupValues[1].toLong()).atTime(23, 59) }
        return null
    }

    /** 发送给 AI 之前的脱敏：手机号、长数字串（运单号 / 取件码以外）。 */
    fun redact(text: String): String =
        text
            .replace(Regex("1[3-9]\\d{9}"), "[手机号]")
            .replace(Regex("\\d{10,}"), "[长数字]")
}
