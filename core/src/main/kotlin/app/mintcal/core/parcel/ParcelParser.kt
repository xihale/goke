package app.mintcal.core.parcel

import java.time.LocalDateTime

data class ParcelInfo(
    val code: String,
    val place: String,
    val company: String,
    val deadline: LocalDateTime?,
    val raw: String,
    /** 命中的规则 id（内置或用户规则），用于展示与排查。 */
    val ruleId: String,
)

/**
 * 快递取件识别已迁到 [ParcelRuleEngine]（规则 = 数据，docs/specs/014）。
 * 此处仅保留发送给 AI 前的脱敏。
 */
object ParcelParser {
    /** 发送给 AI 之前的脱敏：手机号、长数字串（运单号 / 取件码以外）。 */
    fun redact(text: String): String =
        text
            .replace(Regex("1[3-9]\\d{9}"), "[手机号]")
            .replace(Regex("\\d{10,}"), "[长数字]")
}
