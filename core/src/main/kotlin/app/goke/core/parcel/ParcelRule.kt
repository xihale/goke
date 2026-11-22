package app.goke.core.parcel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 快递识别规则：模板 + 截止时间语义（docs/specs/014-parcel-rule-engine.md）。
 * 规则是数据：内置种子开箱即用，用户可增改停排序；匹配时用户规则先于内置，first-match-wins。
 */
@Serializable
data class ParcelRule(
    val id: String,
    val name: String,
    val template: String,
    val enabled: Boolean = true,
    /** 覆盖快递公司名；空则从 {company} 捕获或【】标签推断。 */
    val company: String? = null,
    val deadline: DeadlineSpec = DeadlineSpec.None,
    /** 内置种子：可停用，编辑即 fork 成用户规则并停用原条目（版本升级不打架）。 */
    val builtin: Boolean = false,
)

/**
 * 截止时间如何从命中内容算出。
 * [AtTime] 过点保留今天（顺丰「请于18:00前」是当天硬截止，过了如实显示逾期）；
 * [Closing] 过点滚到明天（驿站营业时段每天循环）。两者语义不同，不是疏漏。
 */
@Serializable
sealed interface DeadlineSpec {
    @Serializable
    @SerialName("none")
    data object None : DeadlineSpec

    /** {cap}（HH:mm）：当天该时刻。 */
    @Serializable
    @SerialName("at")
    data class AtTime(
        val cap: String = "time",
    ) : DeadlineSpec

    /** 到达时间 + {cap}（小时数，如快宝「免费{n}小时」）。 */
    @Serializable
    @SerialName("hours")
    data class AddHours(
        val cap: String = "n",
    ) : DeadlineSpec

    /** 到达时间 + 固定小时数（模板没有可捕获的小时数时用，如丰巢默认免费 18h）。 */
    @Serializable
    @SerialName("const")
    data class Const(
        val hours: Int,
    ) : DeadlineSpec

    /** {cap} 时段的营业结束：当天该时刻，已过点滚到明天。 */
    @Serializable
    @SerialName("closing")
    data class Closing(
        val cap: String = "hours",
    ) : DeadlineSpec
}
