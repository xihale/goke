package app.mintcal.core

import app.mintcal.core.parcel.DeadlineSpec
import app.mintcal.core.parcel.ParcelRule
import app.mintcal.core.parcel.ParcelRuleEngine
import app.mintcal.core.parcel.RuleTemplate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 内置规则种子逐条 fixture（结构照 496 条真机语料回放，地址已匿名化）+ 噪声闸 + 截止边界 + DSL 校验。
 * 语料：~/sms-export（docs/specs/014）。
 */
class ParcelRuleTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 3, 10, 0)

    private fun parse(text: String) = ParcelRuleEngine.parse(text, now)

    // ---------- 内置种子逐条 ----------

    @Test
    fun fengchaoFreeHours() {
        val r = parse("【丰巢】凭取件码45678213至梅苑三号楼一层快递柜取件，18小时内免费。有疑问联系快递员")!!
        assertEquals("fengchao_free", r.ruleId)
        assertEquals("45678213", r.code)
        assertEquals("梅苑三号楼一层快递柜", r.place)
        assertEquals("丰巢", r.company)
        assertEquals(now.plusHours(18), r.deadline)
    }

    @Test
    fun fengchaoGeneric() {
        val r = parse("【丰巢】凭取件码68721345至翠湖苑南门岗亭旁丰巢柜取件。快递员及畅存规则p.fcbox.com/aBcDe")!!
        assertEquals("fengchao", r.ruleId)
        assertEquals("68721345", r.code)
        assertEquals("翠湖苑南门岗亭旁丰巢柜", r.place)
        assertEquals(now.plusHours(18), r.deadline)
    }

    @Test
    fun fengchaoExpiryWarning() {
        val r = parse("【丰巢】您的快递6小时后将超18小时免费期限，超时后0.5元/12小时，3元封顶。取件码87654321")!!
        assertEquals("fengchao_warn", r.ruleId)
        assertEquals("87654321", r.code)
        assertEquals(now.plusHours(6), r.deadline)
    }

    @Test
    fun kuaidabao() {
        val r =
            parse("【快宝驿站】凭取件码739826，前往梅苑三饭一层B柜免费18小时超时0.5元起封顶3.0元快递柜333格口取您的包裹")!!
        assertEquals("kuaidabao", r.ruleId)
        assertEquals("739826", r.code)
        assertEquals("梅苑三饭一层B柜", r.place)
        assertEquals(now.plusHours(18), r.deadline)
    }

    @Test
    fun shunfengDeadline() {
        val r = parse("【顺丰速运】您的包裹已到梅苑东门顺丰摊位，请于19:30前凭2-2-4128取件。有问题请联系顺丰小哥。")!!
        assertEquals("shunfeng", r.ruleId)
        assertEquals("2-2-4128", r.code)
        assertEquals("梅苑东门顺丰摊位", r.place)
        assertEquals(now.toLocalDate().atTime(19, 30), r.deadline)
    }

    @Test
    fun mamaStation() {
        val r = parse("【妈妈驿站】取货码49-3-4180，您有包裹已到梅苑三号楼一层妈妈驿站，请10:00-22:00到店取件")!!
        assertEquals("mama", r.ruleId)
        assertEquals("49-3-4180", r.code)
        assertEquals("梅苑三号楼一层妈妈驿站", r.place)
        assertEquals(now.toLocalDate().atTime(22, 0), r.deadline)
    }

    @Test
    fun mamaNag() {
        val r = parse("【妈妈驿站】取货码Q700-132，您的包裹尾号2412存放多日，请尽快到梅苑三号楼一层驿站前领取")!!
        assertEquals("mama_nag", r.ruleId)
        assertEquals("Q700-132", r.code)
        assertEquals("梅苑三号楼一层驿站", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun tuxi() {
        val r = parse("【兔喜生活】您有包裹已到达梅苑三饭1号店，取件码为20-1-2545")!!
        assertEquals("tuxi", r.ruleId)
        assertEquals("20-1-2545", r.code)
        assertEquals("梅苑三饭1号店", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun arrivedCodeWithBracketCompany() {
        // 蜜罐类消息被「已到-取件码」吃掉，公司名回落到【】标签
        val r = parse("【蜜罐】您单号JT5289999467662的极兔快递包裹已到首创禧瑞阅府1号岗亭保安室旁，取件码63550878")!!
        assertEquals("arrived_code", r.ruleId)
        assertEquals("63550878", r.code)
        assertEquals("首创禧瑞阅府1号岗亭保安室旁", r.place)
        assertEquals("蜜罐", r.company)
        assertNull(r.deadline)
    }

    @Test
    fun codeWithTail() {
        val r = parse("【申通快递】凭C4-2-5120到梅苑西区驿站取运单尾号0734包裹")!!
        assertEquals("code_tail", r.ruleId)
        assertEquals("C4-2-5120", r.code)
        assertEquals("梅苑西区驿站", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun yundaWithHours() {
        val r = parse("【韵达超市】您的韵达快递包裹已到梅苑西门驿站门口面，韵达快递，请于09:00-19:30凭15-1-279取件")!!
        assertEquals("yunda_hours", r.ruleId)
        assertEquals("15-1-279", r.code)
        assertEquals("梅苑西门驿站门口", r.place)
        assertEquals("韵达", r.company)
        assertEquals(now.toLocalDate().atTime(19, 30), r.deadline)
    }

    @Test
    fun yundaGeneric() {
        val r = parse("【韵达超市】您的韵达快递包裹已到梅苑西门驿站门口面韵达，请凭43-1-859取件")!!
        assertEquals("yunda", r.ruleId)
        assertEquals("43-1-859", r.code)
        assertEquals("梅苑西门驿站门口", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun shelfCounter() {
        val r = parse("【递管家】您的快递:*68622已到翠湖苑东门代收点，请凭11-2-1054前往人工货架领取")!!
        assertEquals("shelf", r.ruleId)
        assertEquals("11-2-1054", r.code)
        assertEquals("翠湖苑东门代收点", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun codeGenericWithAddressSuffix() {
        val r = parse("【极兔速递】请凭B3-3-1020到梅苑西区妈妈驿站取件，地址：翠湖路12号")!!
        assertEquals("code_generic", r.ruleId)
        assertEquals("B3-3-1020", r.code)
        assertEquals("梅苑西区妈妈驿站", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun privacyNumberAsCode() {
        val r = parse("【取件提醒】您的韵达快递包裹已到梅苑西门送档口，请凭收货隐私号17138339765取件，快递员王强")!!
        assertEquals("privacy_code", r.ruleId)
        assertEquals("17138339765", r.code)
        assertEquals("梅苑西门送档口", r.place)
        assertNull(r.deadline)
    }

    @Test
    fun notificationFallbacks() {
        // App 通知句式（非短信语料）：兜底规则保证不漏任务；地址 / 截止没有可靠模板，不硬凑
        val colon = parse("您的快递已放入丰巢智能柜，取件码：A7K9Q2，请今日18:00前取件")!!
        assertEquals("code_colon", colon.ruleId)
        assertEquals("A7K9Q2", colon.code)
        assertEquals("", colon.place)
        assertNull(colon.deadline)
        val spaced = parse("【菜鸟驿站】您的包裹已到大学城北门菜鸟驿站，凭取件码 5-3-2081 取件")!!
        assertEquals("code_space", spaced.ruleId)
        assertEquals("5-3-2081", spaced.code)
        assertEquals("菜鸟驿站", spaced.company)
    }

    // ---------- 噪声闸与无码忽略 ----------

    @Test
    fun noiseGateRejects() {
        assertNull(parse("【微快递】验证码209193，您正在进行手机号验证，若非本人操作请忽略"))
        assertNull(parse("【丰巢】您的会员即将到期，续费可继续享受免费存放权益"))
        assertNull(parse("【菜鸟裹裹】快递员将在今天9:00-11:00上门取件，寄件码6315。寄快递，用菜鸟裹裹"))
        assertNull(parse("【丰巢】您寄往南京市的快递776241027761556因其他原因拒收，凭取件码64543885取回，运费3个工作日退回"))
        assertNull(parse("【京东】您的订单已发货，拒收请回复R退订"))
        assertNull(parse("【丰巢】您有一票寄往南京市的顺丰速运快件增加了包装服务，需补交5.00元包材费"))
    }

    @Test
    fun noticesWithoutCodeIgnored() {
        assertNull(parse("【极兔速递】您尾号3740的包裹在梅苑东区驿站等候超2日，请速取。"))
        assertNull(parse("【丰巢】您的包裹即将超过18小时免费期限，超时后0.5元/12小时，3元封顶。点击查看取件码acs.fcbox.com/s/S6"))
        assertNull(parse("【中国邮政】您的包裹330885404956，将由快递员投递，电话13800138000。"))
    }

    // ---------- 截止时间边界 ----------

    @Test
    fun atTimePastStaysToday() {
        // 顺丰「请于18:00前」是当天硬截止，通知来晚了如实显示逾期，不滚到明天
        val late = LocalDateTime.of(2026, 11, 3, 20, 0)
        val r = ParcelRuleEngine.parse("【顺丰速运】您的包裹已到梅苑东门，请于18:00前凭1-2-7082取件", late)!!
        assertEquals(late.toLocalDate().atTime(18, 0), r.deadline)
    }

    @Test
    fun closingAfterNowRollsToTomorrow() {
        val late = LocalDateTime.of(2026, 11, 3, 23, 0)
        val r =
            ParcelRuleEngine.parse("【妈妈驿站】取货码49-3-4180，您有包裹已到梅苑三号楼一层妈妈驿站，请10:00-22:00到店取件", late)!!
        assertEquals(late.toLocalDate().plusDays(1).atTime(22, 0), r.deadline)
    }

    // ---------- 规则优先级与配置 ----------

    @Test
    fun userRuleWinsOverBuiltin() {
        val mine = ParcelRule("mine", "我的丰巢", "凭取件码{code}至{place}取件", company = "丰巢")
        val r = ParcelRuleEngine.parse("【丰巢】凭取件码45678213至梅苑三号楼一层快递柜取件，18小时内免费", now, userRules = listOf(mine))!!
        assertEquals("mine", r.ruleId)
        assertEquals("梅苑三号楼一层快递柜", r.place)
    }

    @Test
    fun disabledBuiltinFallsThrough() {
        // 停掉「免费时长」后，12 小时消息落到通用规则（+18h 常量），证明停用生效
        val text = "【丰巢】凭取件码45678213至梅苑三号楼一层快递柜取件，12小时内免费"
        assertEquals(now.plusHours(12), parse(text)!!.deadline)
        val r = ParcelRuleEngine.parse(text, now, disabledBuiltins = setOf("fengchao_free"))!!
        assertEquals("fengchao", r.ruleId)
        assertEquals(now.plusHours(18), r.deadline)
    }

    @Test
    fun invalidUserRuleSkipped() {
        val broken = ParcelRule("broken", "坏规则", "没有取件码占位符的模板")
        val r = ParcelRuleEngine.parse("【兔喜生活】您有包裹已到达梅苑三饭1号店，取件码为20-1-2545", now, userRules = listOf(broken))!!
        assertEquals("tuxi", r.ruleId)
    }

    @Test
    fun customRejectsOverride() {
        val text = "【妈妈驿站】取货码Q700-132，您的包裹停放在梅苑三号楼一层驿站多日，请尽快领取"
        assertNull(ParcelRuleEngine.parse(text, now, rejects = listOf("停放在")))
        assertNull(parse(text.replace("停放在", "存放在")))
    }

    // ---------- 模板 DSL 校验 ----------

    @Test
    fun templateValidation() {
        assertIs<RuleTemplate.Error.Empty>(RuleTemplate.validate("", DeadlineSpec.None))
        assertIs<RuleTemplate.Error.NoCode>(RuleTemplate.validate("没有占位符的模板", DeadlineSpec.None))
        assertIs<RuleTemplate.Error.Duplicate>(RuleTemplate.validate("{code}X{code}", DeadlineSpec.None))
        assertIs<RuleTemplate.Error.Unknown>(RuleTemplate.validate("{code}{foo}", DeadlineSpec.None))
        assertIs<RuleTemplate.Error.BadCap>(RuleTemplate.validate("{code}", DeadlineSpec.AtTime("time")))
        assertNull(RuleTemplate.validate("{code}{time}", DeadlineSpec.AtTime("time")))
        assertNull(RuleTemplate.validate("{code}{hours}", DeadlineSpec.Closing("hours")))
    }

    @Test
    fun fromSampleRoundTrip() {
        val sample = "【顺丰速运】您的包裹已到梅苑东门顺丰摊位，请于19:30前凭2-2-4128取件。有问题请联系顺丰小哥。"
        val tpl = RuleTemplate.fromSample(sample)
        assertTrue("{code}" in tpl && "{time}" in tpl && "{place}" in tpl, tpl)
        assertNull(RuleTemplate.validate(tpl, DeadlineSpec.AtTime("time")), "生成的初稿必须能过校验")
        val m = ParcelRuleEngine.regexFor(tpl).find(sample)
        assertEquals("2-2-4128", m?.groups?.get("code")?.value)
    }
}
