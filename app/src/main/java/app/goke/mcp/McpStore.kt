package app.goke.mcp

import android.content.Context
import android.util.Base64
import app.goke.data.McpTokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class McpPrefs(
    val enabled: Boolean = false,
    val allowLan: Boolean = false,
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 17530
    }
}

/** 本地 MCP 服务配置与鉴权令牌存储。令牌优先走 Keystore（[McpTokenVault]），单元测试无 Keystore 时回退本机私有偏好。 */
class McpStore(
    context: Context,
    private val vault: McpTokenVault? = null,
    prefsName: String = "mcp",
) {
    private val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<McpPrefs> = _prefs.asStateFlow()

    private fun read(): McpPrefs =
        McpPrefs(
            enabled = sp.getBoolean("enabled", false),
            allowLan = sp.getBoolean("allowLan", false),
            port = sp.getInt("port", McpPrefs.DEFAULT_PORT).coerceIn(1024, 65535),
        )

    fun update(block: (McpPrefs) -> McpPrefs) {
        val next = block(_prefs.value).let { it.copy(port = it.port.coerceIn(1024, 65535)) }
        sp
            .edit()
            .putBoolean("enabled", next.enabled)
            .putBoolean("allowLan", next.allowLan)
            .putInt("port", next.port)
            .apply()
        _prefs.value = next
    }

    fun ensureToken(): String {
        val existing = readToken()
        if (!existing.isNullOrBlank()) return existing
        return regenerateToken()
    }

    fun regenerateToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = "mc_" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        writeToken(token)
        return token
    }

    private fun readToken(): String? {
        val fromVault = runCatching { vault?.mcpToken() }.getOrNull()
        if (!fromVault.isNullOrBlank()) return fromVault
        return sp.getString("fallback_token", null)
    }

    private fun writeToken(token: String) {
        val v = vault
        val savedInVault =
            if (v != null) {
                runCatching {
                    v.setMcpToken(token)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        if (savedInVault) {
            sp.edit().remove("fallback_token").apply()
        } else {
            sp.edit().putString("fallback_token", token).apply()
        }
    }
}
