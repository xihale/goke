# S014 快递规则引擎（规则可编辑）

## 背景与目标

硬编码正则无法覆盖各地驿站与快递柜不断变化的措辞。快递识别规则必须数据化：内置种子开箱即用，同时允许用户在 App 内补充、编辑、启停自己的模板与噪声屏蔽词。

## 规则模型（`:core`，`core/parcel/ParcelRule.kt`）

```kotlin
@Serializable
data class ParcelRule(
    val id: String,
    val name: String,                 // 如 "丰巢"
    val template: String,             // 如 "凭取件码{code}至{place}取件，{n}小时内免费"
    val enabled: Boolean = true,
    val company: String? = null,      // 覆盖快递公司名；空则从 {company} 或【】标签取
    val deadline: DeadlineSpec = DeadlineSpec.None,
    val builtin: Boolean = false,     // 内置种子：可停用，编辑即 fork 成用户规则
)

@Serializable
sealed interface DeadlineSpec {
    @Serializable @SerialName("none") data object None
    @Serializable @SerialName("at") data class AtTime(val cap: String = "time")       // 当天 HH:mm（过点保留今天显示逾期）
    @Serializable @SerialName("hours") data class AddHours(val cap: String = "n")     // 到达时间 + n 小时
    @Serializable @SerialName("const") data class Const(val hours: Int)               // 固定小时数（如丰巢默认 +18h）
    @Serializable @SerialName("closing") data class Closing(val cap: String = "hours") // 营业时段末尾（已过点滚到明天）
}
```

## 模板 DSL（`RuleTemplate`）

字面量自动转义精确匹配，花括号占位符为唯一语法：

| 占位符 | 含义 | 正则 |
|---|---|---|
| `{code}` | 取件码（必需，恰好 1 个） | `[A-Za-z0-9\-]{3,20}` |
| `{place}` | 地址 / 驿站 | `[^，。,;；!！\n]{2,40}?` |
| `{n}` | 纯数字（小时数、尾号） | `\d{1,20}` |
| `{num}` | 运单号 | `[A-Za-z0-9]{5,25}` |
| `{time}` | 时刻 | `\d{1,2}[:：]\d{2}` |
| `{hours}` | 时段 | `{time}-{time}` |
| `{company}` | 快递公司名 | `[\u4e00-\u9fa5A-Za-z]{2,8}?` |
| `{skip}` | 任意垫字（惰性） | `[\s\S]*?` |

校验规则：必须恰好包含 1 个 `{code}`；占位符不可重复；`deadline` 引用的捕获名必须在模板中存在；编译后的 `Regex` 按 `template` 缓存（`ConcurrentHashMap`）。

## 匹配管线（`ParcelRuleEngine.parse`）

1. **噪声闸**：命中 `rejects`（默认：`验证码 / 会员 / 续费 / 寄往 / 回复R / 寄件码 / 补交`，用户可编辑）直接返回 `null`。
2. **规则顺序（first-match-wins）**：用户规则（按列表顺序）→ 内置规则（`ParcelRuleEngine.BUILTIN`，17 条，含 15 条常见快递/驿站模板与 2 条 `取件码：{code}` / `取件码 {code}` 通知兜底）。
3. **输出**：`code` 非空时生成 `ParcelInfo(code, place, company, deadline, raw, ruleId)`；`company` 优先级为 `rule.company > {company} 捕获 > 【】标签`。

## 存储与 UI（`ParcelRuleStore`、`ParcelScreen` / `ParcelRules.kt`）

- 规则与噪声词保存在设备本地 `SharedPreferences`（`parcelRules`、`parcelReject`，v1 不随云端同步，跨设备随「数据与备份」JSON 导出迁移）。
- **编辑即 fork**：编辑内置种子时自动复制为排在首位的用户规则并停用原内置规则；「恢复默认」清空用户规则并重置内置启用状态。
- 「设置 → 快递取件」首屏展示自动识别开关、顺路取件时段与「试一试」样本实测；规则列表、噪声词与模板编辑器收折在二级规则管理中。

## 测试

- `:core`：`ParcelRuleTest` 覆盖全部 17 条内置规则匿名化夹具、噪声拦截、`closing`/`at` 截止边界及 DSL 非法模板校验。
