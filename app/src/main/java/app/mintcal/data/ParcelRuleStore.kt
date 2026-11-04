package app.mintcal.data

import android.content.Context
import app.mintcal.core.parcel.ParcelInfo
import app.mintcal.core.parcel.ParcelRule
import app.mintcal.core.parcel.ParcelRuleEngine
import app.mintcal.core.util.MintJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime

/** 快递规则的生效配置：用户规则（含 fork 出的内置副本）→ 停用的内置 → 噪声词。 */
data class ParcelRuleConfig(
    val user: List<ParcelRule> = emptyList(),
    val disabledBuiltins: Set<String> = emptySet(),
    val rejects: List<String> = ParcelRuleEngine.DEFAULT_REJECTS,
) {
    fun parse(
        text: String,
        now: LocalDateTime,
    ): ParcelInfo? = ParcelRuleEngine.parse(text, now, user, disabledBuiltins, rejects)
}

/**
 * 快递规则的本地存储（docs/specs/014）：SharedPreferences 三个 JSON key。
 * 规则是设备本地配置，v1 不进同步；跨设备用数据页的 JSON 导出 / 导入。
 */
class ParcelRuleStore(
    context: Context,
) {
    private val sp = context.getSharedPreferences("parcel_rules", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<ParcelRuleConfig> = _flow.asStateFlow()
    val value: ParcelRuleConfig get() = _flow.value

    private fun read(): ParcelRuleConfig =
        ParcelRuleConfig(
            user = runCatching { MintJson.decodeFromString<List<ParcelRule>>(sp.getString(KEY_USER, "[]")!!) }.getOrDefault(emptyList()),
            disabledBuiltins =
                runCatching { MintJson.decodeFromString<List<String>>(sp.getString(KEY_DISABLED, "[]")!!) }
                    .getOrDefault(emptyList())
                    .toSet(),
            rejects =
                runCatching { MintJson.decodeFromString<List<String>>(sp.getString(KEY_REJECTS, "[]")!!) }
                    .getOrDefault(emptyList())
                    .ifEmpty { ParcelRuleEngine.DEFAULT_REJECTS },
        )

    private fun write(c: ParcelRuleConfig) {
        sp
            .edit()
            .putString(KEY_USER, MintJson.encodeToString(c.user))
            .putString(KEY_DISABLED, MintJson.encodeToString(c.disabledBuiltins.toList()))
            .putString(KEY_REJECTS, MintJson.encodeToString(c.rejects))
            .apply()
        _flow.value = c
    }

    /** 新增或更新用户规则（id 已存在则原位更新，保持排序）。 */
    fun upsert(rule: ParcelRule) {
        val cur = value.user.toMutableList()
        val i = cur.indexOfFirst { it.id == rule.id }
        if (i >= 0) cur[i] = rule else cur.add(rule)
        write(value.copy(user = cur))
    }

    fun remove(id: String) = write(value.copy(user = value.user.filterNot { it.id == id }))

    /** 用户区内上移 / 下移（delta=-1 / +1），保持 first-match 顺序可调。 */
    fun move(
        id: String,
        delta: Int,
    ) {
        val cur = value.user.toMutableList()
        val i = cur.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= cur.size) return
        cur[i] = cur[j].also { cur[j] = cur[i] }
        write(value.copy(user = cur))
    }

    fun setBuiltinEnabled(
        id: String,
        enabled: Boolean,
    ) = write(value.copy(disabledBuiltins = if (enabled) value.disabledBuiltins - id else value.disabledBuiltins + id))

    /** 噪声词全量替换；空项剔除，全部为空时回落到默认词表。 */
    fun setRejects(words: List<String>) =
        write(
            value.copy(
                rejects =
                    words
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { ParcelRuleEngine.DEFAULT_REJECTS },
            ),
        )

    /** 恢复默认：清空用户规则、重新启用全部内置、噪声词复位。 */
    fun restoreDefaults() = write(ParcelRuleConfig())

    private companion object {
        const val KEY_USER = "user"
        const val KEY_DISABLED = "disabled"
        const val KEY_REJECTS = "rejects"
    }
}
