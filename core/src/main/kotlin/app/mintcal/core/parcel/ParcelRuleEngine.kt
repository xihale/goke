package app.mintcal.core.parcel

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 快递识别引擎（docs/specs/014）：噪声闸 → 用户规则 → 内置规则，first-match-wins。
 * 模板字面量原样匹配，{占位符} 是唯一语法（表见 spec）。种子规则的措辞来自 496 条真机短信语料回放。
 */
object ParcelRuleEngine {
    /** 全局噪声闸：含这些词的通知一律不算取件（营销 / 验证码 / 寄件 / 拒收）。用户可编辑。 */
    val DEFAULT_REJECTS = listOf("验证码", "会员", "续费", "寄往", "回复R", "寄件码", "补交")

    /** 内置种子（spec S014 表，顺序即优先级；语料回放 374 命中 / 46 噪声 / 76 正确忽略）。 */
    val BUILTIN =
        listOf(
            ParcelRule("fengchao_free", "丰巢-免费时长", "凭取件码{code}至{place}取件，{n}小时内免费", company = "丰巢", deadline = DeadlineSpec.AddHours("n"), builtin = true),
            ParcelRule("fengchao", "丰巢", "凭取件码{code}至{place}{skip}取", company = "丰巢", deadline = DeadlineSpec.Const(18), builtin = true),
            ParcelRule("fengchao_warn", "丰巢-超时预警", "您的快递{n}小时后将超{skip}取件码{code}", company = "丰巢", deadline = DeadlineSpec.AddHours("n"), builtin = true),
            ParcelRule("kuaidabao", "快宝驿站", "凭取件码{code}，前往{place}免费{n}小时超时", deadline = DeadlineSpec.AddHours("n"), builtin = true),
            ParcelRule("shunfeng", "顺丰-限时", "已到{place}{skip}，请于{time}前凭{code}取件", company = "顺丰", deadline = DeadlineSpec.AtTime("time"), builtin = true),
            ParcelRule("mama", "妈妈驿站", "取货码{code}，您有包裹已到{place}，请{hours}到店取件", deadline = DeadlineSpec.Closing("hours"), builtin = true),
            ParcelRule("mama_nag", "妈妈驿站-催取", "取货码{code}，您的包裹尾号{n}存放多日，请尽快到{place}前领取", builtin = true),
            ParcelRule("tuxi", "兔喜生活", "您有包裹已到达{place}，取件码为{code}", builtin = true),
            ParcelRule("arrived_code", "已到-取件码", "已到{place}，取件码{code}", builtin = true),
            ParcelRule("code_tail", "凭码取件-尾号", "凭{code}到{place}取{skip}尾号{n}包裹", builtin = true),
            ParcelRule("yunda_hours", "韵达超市-时段", "包裹已到{place}面，韵达快递，请于{hours}凭{code}取件", company = "韵达", deadline = DeadlineSpec.Closing("hours"), builtin = true),
            ParcelRule("yunda", "韵达超市", "包裹已到{place}面{skip}凭{code}取件", company = "韵达", builtin = true),
            ParcelRule("shelf", "代收点-货架", "您的快递:*{num}已到{place}，请凭{code}前往", builtin = true),
            ParcelRule("code_generic", "凭码取件-通用", "凭{code}到{place}取件", builtin = true),
            ParcelRule("privacy_code", "隐私号取件", "已到{place}，请凭收货隐私号{code}取件", builtin = true),
            // 通知兜底：App 通知（菜鸟裹裹等）常用「取件码：X」/「取件码 X」句式；要求分隔符，
            // 避免吞进「取件码acs.fcbox.com/s/xx」这类短链（短信语料 0 命中，非语料种子）。
            ParcelRule("code_colon", "取件码-冒号", "取件码：{code}", builtin = true),
            ParcelRule("code_space", "取件码-空格", "取件码 {code}", builtin = true),
        )

    /** 编译缓存：正则 + 模板里实际出现的占位符名。 */
    private class Compiled(
        val regex: Regex,
        val names: Set<String>,
    )

    private val CACHE = ConcurrentHashMap<String, Compiled>()

    fun regexFor(template: String): Regex = compiledFor(template).regex

    private fun compiledFor(template: String): Compiled = CACHE.computeIfAbsent(template) { Compiled(RuleTemplate.compile(it), RuleTemplate.placeholders(it)) }

    /** 模板没有该占位符时返回 null（JVM 的命名组访问对未知组名会抛异常，必须先查名字）。 */
    private fun MatchResult.group(
        c: Compiled,
        name: String,
    ): String? = if (name in c.names) groups[name]?.value else null

    fun parse(
        text: String,
        now: LocalDateTime,
        userRules: List<ParcelRule> = emptyList(),
        disabledBuiltins: Set<String> = emptySet(),
        rejects: List<String> = DEFAULT_REJECTS,
    ): ParcelInfo? {
        if (text.isBlank() || rejects.any { text.contains(it) }) return null
        for (rule in userRules + BUILTIN) {
            if (!rule.enabled || rule.builtin && rule.id in disabledBuiltins) continue
            if (RuleTemplate.validate(rule.template, rule.deadline) != null) continue
            val c = compiledFor(rule.template)
            val m = c.regex.find(text) ?: continue
            val code = m.group(c, "code")?.takeIf { it.isNotBlank() } ?: continue
            val place = m.group(c, "place")?.trim().orEmpty()
            val company =
                rule.company
                    ?: m.group(c, "company")
                    ?: Regex("【([^】]{1,12})】").find(text)?.groupValues?.get(1)
                    ?: ""
            return ParcelInfo(code, place, company, deadline(rule.deadline, c, m, now), text, rule.id)
        }
        return null
    }

    private fun deadline(
        spec: DeadlineSpec,
        c: Compiled,
        m: MatchResult,
        now: LocalDateTime,
    ): LocalDateTime? =
        when (spec) {
            is DeadlineSpec.None -> {
                null
            }

            is DeadlineSpec.AtTime -> {
                m.group(c, spec.cap)?.split(':', '：')?.takeIf { it.size == 2 }?.let { (h, mi) ->
                    runCatching { now.toLocalDate().atTime(h.toInt(), mi.toInt()) }.getOrNull()
                }
            }

            is DeadlineSpec.AddHours -> {
                m.group(c, spec.cap)?.toLongOrNull()?.let { now.plusHours(it) }
            }

            is DeadlineSpec.Const -> {
                now.plusHours(spec.hours.toLong())
            }

            is DeadlineSpec.Closing -> {
                val cap = m.group(c, spec.cap) ?: return null
                val last = Regex("(\\d{1,2})[:：](\\d{2})").findAll(cap).lastOrNull() ?: return null
                runCatching {
                    val end = now.toLocalDate().atTime(last.groupValues[1].toInt(), last.groupValues[2].toInt())
                    if (!end.isAfter(now)) end.plusDays(1) else end
                }.getOrNull()
            }
        }
}

/** 模板 DSL：编译、校验、从样本生成初稿。 */
object RuleTemplate {
    private val PATTERNS =
        mapOf(
            "code" to "[A-Za-z0-9\\-]{3,20}",
            "place" to "[^，。,.；;！!\\n]{2,40}",
            "n" to "\\d{1,20}",
            "num" to "[A-Za-z0-9]{5,25}",
            "time" to "(?:[01]?\\d|2[0-3])[:：][0-5]\\d",
            "hours" to "(?:(?:[01]?\\d|2[0-3])[:：][0-5]\\d(?:\\s*[-—~至]\\s*(?:[01]?\\d|2[0-3])[:：][0-5]\\d)?)",
            "company" to "[\\u4e00-\\u9fa5A-Za-z]{2,8}?",
            "skip" to "[\\s\\S]*?",
        )

    /** 校验失败原因（UI 据此出文案）。 */
    sealed interface Error {
        data object Empty : Error

        data object NoCode : Error

        data class Unknown(
            val name: String,
        ) : Error

        data class Duplicate(
            val name: String,
        ) : Error

        data class BadCap(
            val name: String,
        ) : Error

        data class NoMatch(
            val sample: String,
        ) : Error
    }

    private val PLACE = Regex("\\{(\\w+)\\}")

    /** 模板里实际出现的占位符名。 */
    fun placeholders(template: String): Set<String> = PLACE.findAll(template).mapTo(mutableSetOf()) { it.groupValues[1] }

    fun validate(
        template: String,
        deadline: DeadlineSpec = DeadlineSpec.None,
    ): Error? {
        if (template.isBlank()) return Error.Empty
        val seen = mutableSetOf<String>()
        var codes = 0
        for (m in PLACE.findAll(template)) {
            val name = m.groupValues[1]
            if (name !in PATTERNS) return Error.Unknown(name)
            if (name == "code") codes++
            if (!seen.add(name)) return Error.Duplicate(name)
        }
        if (codes != 1) return Error.NoCode
        val cap =
            when (val d = deadline) {
                is DeadlineSpec.None -> null
                is DeadlineSpec.AtTime -> d.cap
                is DeadlineSpec.AddHours -> d.cap
                is DeadlineSpec.Const -> null
                is DeadlineSpec.Closing -> d.cap
            }
        if (cap != null && cap !in seen) return Error.BadCap(cap)
        return null
    }

    fun compile(template: String): Regex {
        val sb = StringBuilder()
        var i = 0
        for (m in PLACE.findAll(template)) {
            sb.append(Pattern.quote(template.substring(i, m.range.first)))
            sb
                .append("(?<")
                .append(m.groupValues[1])
                .append('>')
                .append(PATTERNS.getValue(m.groupValues[1]))
                .append(')')
            i = m.range.last + 1
        }
        sb.append(Pattern.quote(template.substring(i)))
        return Regex(sb.toString())
    }

    /**
     * 从样本生成模板初稿：码 / 时段 / 时刻 / 地址段掩码成占位符。
     * 保守掩码，保存前引擎仍要求模板能匹配原样本。
     */
    fun fromSample(sample: String): String {
        var t = sample.trim()
        t = Regex("((?:取件码|取货码|提货码)[:：\\s]*)[「【\\[]?[A-Za-z0-9\\-]{3,20}[」】\\]]?").replace(t) { it.groupValues[1] + "{code}" }
        // 保留「凭」字面量，只把码换成 {code}（replaceFirst 无 lambda 重载，手写只换第一处）
        Regex("凭[「【\\[]?[A-Za-z0-9\\-]{3,20}[」】\\]]?").find(t)?.let { m -> t = t.replaceRange(m.range, "凭{code}") }
        // 掩码用的 hours 必须是「真时段」（带分隔符），否则单独的时刻会被错标成 {hours}
        t = Regex("(?:[01]?\\d|2[0-3])[:：][0-5]\\d\\s*[-—~至]\\s*(?:[01]?\\d|2[0-3])[:：][0-5]\\d").replaceFirst(t, "{hours}")
        t = Regex(PATTERNS.getValue("time")).replaceFirst(t, "{time}")
        Regex("(?:已到|到达|已至|前往)「?「?([^，。,.；;！!\\n]{2,40})").find(t)?.let { m ->
            t = t.replaceRange(m.range, m.value.dropLast(m.groupValues[1].length) + "{place}")
        }
        return t
    }
}
