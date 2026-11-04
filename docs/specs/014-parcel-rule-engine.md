# S014 快递规则引擎（规则可编辑）

## 背景与问题

S009 的快递识别是硬编码（`core/parcel/ParcelParser.kt`：关键词闸 + 取件码/地址/截止各几条正则）。对 2026-11 从真机导出的 496 条真实快递短信语料回放验证，硬编码规则有系统性缺陷：

| 缺陷 | 语料证据 |
|---|---|
| 漏「取件码为」「取货码后无分隔」等变体 | 兔喜 37 条全漏（`取件码为20-1-2545`） |
| 「凭X到/取/至」后缀枚举不全 | 递管家 `凭11-2-1054前往` 漏 |
| 地址后缀白名单太窄（驿站/快递柜/代收点…） | 韵达、妈妈驿站、蜜罐（岗亭）、顺丰摊位的真实地址全丢 |
| `验证码` 正则当取件码用 | 微快递/菜鸟验证码短信误判成取件任务 |
| 截止时间不认识「N小时内免费」 | 丰巢 216 条全部丢截止 |

快递公司模板长年稳定，但**每个用户的驿站措辞不同**（不同校区/小区各有一套）。规则必须是数据，让用户补自己的模板，而不是改代码。

## 语料基线（规则种子的依据）

语料：`~/sms-export/delivery_raw.txt`（496 条，adb 导出，仅用于离线调规则；App 本身仍只读通知，不读短信）。回放脚本：`~/sms-export/rules_harness.py`。

最终模板集回放结果：

| 分类 | 条数 | 说明 |
|---|---|---|
| 命中取件（可生成待办） | 374 | 覆盖丰巢 175 / 快宝 54 / 妈妈 44 / 兔喜 37 / 凭码取尾号 38 / 其余 26 |
| 噪声闸拦截 | 46 | 京东营销、会员续费、验证码、寄件码、拒收取回 |
| 正确忽略 | 76 | 无码物流状态（揽收/投递中/超 2 日催取）、反诈、账单；其中 5 条丰巢码在短链里，无码可提取 |

## 规则模型（core，纯 Kotlin）

```kotlin
@Serializable
data class ParcelRule(
    val id: String,
    val name: String,                 // "丰巢"
    val template: String,             // "凭取件码{code}至{place}取件，{n}小时内免费"
    val enabled: Boolean = true,
    val company: String? = null,      // 覆盖快递公司名；空则从【】标签取
    val deadline: DeadlineSpec = DeadlineSpec.None,
    val builtin: Boolean = false,     // 内置种子：可停用，编辑即 fork（见迁移）
    val order: Int = 0,               // 用户区内的顺序
)

@Serializable
sealed interface DeadlineSpec {
    @Serializable @SerialName("none") data object None
    @Serializable @SerialName("at") data class AtTime(val cap: String = "time")     // {time}前：当天 HH:mm，过点保留今天（显示逾期）
    @Serializable @SerialName("hours") data class AddHours(val cap: String = "n")   // {n}小时：到达时间 + n 小时
    @Serializable @SerialName("const") data class Const(val hours: Int)             // 固定值：如丰巢默认 +18h
    @Serializable @SerialName("closing") data class Closing(val cap: String = "hours") // {hours} 时段末尾：当天营业结束，已过点滚到明天
}
```

`at` 过点不滚明天（顺丰「请于18:00前」是当天硬截止，过了就是逾期，如实显示）；`closing` 滚明天（营业时段每天循环）。两者语义不同，不是疏漏。

## 模板 DSL

字面量原样匹配（自动 quote），占位符是唯一语法：

| 占位符 | 含义 | 正则 |
|---|---|---|
| `{code}` | 取件码（必需） | `[A-Za-z0-9\-]{3,20}` |
| `{place}` | 地址/驿站 | `[^，。,;；!！\n]{2,40}?`（惰性） |
| `{n}` | 纯数字（小时数、尾号） | `\d{1,20}` |
| `{num}` | 运单号（可含字母） | `[A-Za-z0-9]{5,25}` |
| `{time}` | 时刻 | `\d{1,2}[:：]\d{2}` |
| `{hours}` | 时段 | `{time}-{time}` |
| `{company}` | 公司名 | `[\u4e00-\u9fa5A-Za-z]{2,8}?` |
| `{skip}` | 任意垫字 | `[\s\S]*?`（惰性） |

校验（编辑器实时 + 引擎加载时）：
- 必须含恰好一个 `{code}`（没有码的通知不生成待办）；
- 占位符不得重复出现（命名捕获组冲突）；
- `deadline` 引用的 `cap` 必须是模板里存在的占位符；
- `{skip}` 标警告（易过宽），保存时要求模板能匹配用户粘贴的示例。

编译结果按 `template` 哈希缓存，避免每条通知重复编译。

## 匹配管线

```text
parse(text, now, rules):
  1. 噪声闸：rejects = ["验证码","会员","续费","寄往","回复R","寄件码","补交"] 任一出现 → null（词表用户可编辑）
  2. 用户规则（按 order）→ 内置规则（固定顺序），first-match-wins
  3. 命中：code 必须非空 → ParcelInfo(code, place, company, deadline, raw, ruleId)
  4. 全部不中 → null →（可选）AI 兜底
```

用户规则排在内置之前：用户加规则就是因为内置没覆盖，他的规则必须赢。`company` 优先级：规则覆盖 > `{company}` 捕获 > 【】标签推断。

`ParcelParser.parse(text, now)` 签名不变，内部换成引擎调用，`ParcelListenerService` / `ParcelTasks` 零改动。

## 内置规则种子（15 条语料种子 + 2 条通知兜底）

| # | 名称 | 模板 | 截止 | 命中 |
|---|---|---|---|---|
| 1 | 丰巢-免费时长 | `凭取件码{code}至{place}取件，{n}小时内免费` | +{n}h | 30 |
| 2 | 丰巢-通用 | `凭取件码{code}至{place}{skip}取` | +18h（常量） | 145 |
| 3 | 丰巢-超时预警 | `您的快递{n}小时后将超{skip}取件码{code}` | +{n}h | 1 |
| 4 | 快宝驿站 | `凭取件码{code}，前往{place}免费{n}小时超时` | +{n}h | 54 |
| 5 | 顺丰-限时 | `已到{place}{skip}，请于{time}前凭{code}取件` | {time} 当天 | 3 |
| 6 | 妈妈驿站 | `取货码{code}，您有包裹已到{place}，请{hours}到店取件` | 营业结束 | 43 |
| 7 | 妈妈驿站-催取 | `取货码{code}，您的包裹尾号{n}存放多日，请尽快到{place}前领取` | 无 | 1 |
| 8 | 兔喜生活 | `您有包裹已到达{place}，取件码为{code}` | 无 | 37 |
| 9 | 已到-取件码 | `已到{place}，取件码{code}` | 无 | 9 |
| 10 | 凭码取件-尾号 | `凭{code}到{place}取{skip}尾号{n}包裹` | 无 | 38 |
| 11 | 韵达超市-时段 | `包裹已到{place}面，韵达快递，请于{hours}凭{code}取件` | 营业结束 | 3 |
| 12 | 韵达超市 | `包裹已到{place}面{skip}凭{code}取件` | 无 | 6 |
| 13 | 代收点-货架 | `您的快递:*{num}已到{place}，请凭{code}前往` | 无 | 1 |
| 14 | 凭码取件-通用 | `凭{code}到{place}取件` | 无 | 6 |
| 15 | 隐私号取件 | `已到{place}，请凭收货隐私号{code}取件` | 无 | 1 |
| 16 | 取件码-冒号 | `取件码：{code}` | 无 | 0（通知兜底） |
| 17 | 取件码-空格 | `取件码 {code}` | 无 | 0（通知兜底） |

顺序即优先级（specific 在前）。蜜罐类「您单号…已到X，取件码Y」由「已到-取件码」吃掉（公司名回落到【蜜罐】标签），「您单号{num}的{company}快递包裹…」模板被它完全遮蔽（语料命中 0），故不收录。丰巢「再次提醒」按 +18h 常量算会偏长（时钟从首条通知起走）——消息里没有首条时间，接受此误差。

末尾两条「取件码-冒号 / 空格」是给 App 实际消费的通知文本（菜鸟裹裹等 App 通知句式，短信语料之外）兜底的：只保证取件码不漏，地址 / 截止没有可靠模板就不硬凑；必须带分隔符，否则会吞进「取件码acs.fcbox.com/s/xx」这类短链、把链接碎片当码。

## 存储

- 本地优先：`SharedPreferences` 两个 JSON key（`parcelRules`、`parcelReject`），kotlinx.serialization。规则是设备本地配置，**v1 不进同步**——现有 ChangeSet 同步的是任务实体，为规则加实体要动 server schema；跨设备先用数据页的 JSON 导出/导入。后续若要跟账号走，再加服务端 settings blob。
- 内置规则的「编辑即 fork」：编辑某条 builtin 时，复制成用户规则插入用户区首位，原 builtin 置 `enabled=false`；「恢复默认」清空用户区 + 重置内置启用位。这样内置种子随版本升级而更新，不会和用户改动打架。

## UI（ParcelScreen 扩展）

- 现有开关/授权区不动，下方加「规则」区：用户规则列表（开关、↑↓、点进编辑）+ 内置规则折叠列表（开关、恢复默认）+ 噪声词编辑入口。
- 编辑 sheet：名称、模板（多行，占位符点击插入 + 说明）、截止类型选择（无/时刻/+n小时/固定n小时/营业结束，选项随模板占位符动态可用）、公司覆盖。保存前校验 + 用「试一试」样本实测。
- 「试一试」升级：显示命中的规则名和各字段；识别失败时若开了 AI 兜底，出「按此样本新建规则」——把样本里的码/时间/地址段掩码成 `{code}/{time}/{place}` 预填模板，必须仍匹配原样本才能保存。
- AI 兜底只做单条解析（现有 `parcelAiFallback` 路径），产出可一键沉淀为规则；规则才是持久路径，AI 不进常驻链路。

## 测试（:core 单测）

- 15 条内置规则各一条 fixture（结构照语料、**匿名化**，真实地址不入库）→ 断言 code/place/company/deadline 全字段；
- 噪声 fixture（验证码、会员续费、寄件码、京东营销、反诈、拒收取回）→ 断言 null；
- 截止边界：`closing` 过点滚明天、`at` 过点保留今天、跨年「12月31日」；
- DSL 校验：缺 `{code}`、重复占位符、deadline 引用不存在的捕获 → 拒绝。
- 真机链路（通知 → 待办）维持 S009 验收方式。

## 迁移

`ParcelParser` 的 `KEYWORDS / CODE_PATTERNS / PLACE_PATTERNS / deadline()` 全部删除，成为引擎的种子数据来源（上表 15 条即由语料回放产出，非旧正则翻译）。`ParcelInfo` 增加 `ruleId` 字段；`redact()` 保留不动。

## 开放问题

1. `closing` 已过点滚明天 vs 如实显示逾期——现取滚明天（驿站明晚还开门），有异议再改。
2. 用户规则要不要跟账号同步（跟随设备 vs 跟随人）——v1 本地，等有真实多设备需求再上。
