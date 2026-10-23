package app.mintcal.sync

import android.content.Context
import app.mintcal.data.SyncTokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 已登录的账号（令牌另存于 [SecretStore]）。 */
data class SyncAccount(
    val serverUrl: String,
    val username: String,
    val role: String,
)

/** 同步状态的本机持久化：账号、服务器游标、本地推送水位线、最近一次结果。令牌加密保存。 */
class SyncStore(
    context: Context,
    private val secrets: SyncTokenVault,
    prefsName: String = "sync",
) {
    private val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<SyncAccount?> = _account.asStateFlow()

    private fun readAccount(): SyncAccount? {
        val url = sp.getString("url", null) ?: return null
        val user = sp.getString("user", null) ?: return null
        if (secrets.syncToken() == null) return null
        return SyncAccount(url, user, sp.getString("role", "user")!!)
    }

    fun token(): String? = secrets.syncToken()

    fun signIn(
        account: SyncAccount,
        token: String,
    ) {
        secrets.setSyncToken(token)
        sp
            .edit()
            .putString("url", account.serverUrl)
            .putString("user", account.username)
            .putString("role", account.role)
            .putLong("cursor", 0)
            .putLong("watermark", 0)
            .remove("lastSyncAt")
            .remove("lastError")
            .apply()
        _account.value = account
    }

    /** 退出登录：清除令牌与同步进度；本地数据保留。 */
    fun signOut() {
        secrets.setSyncToken("")
        sp
            .edit()
            .remove("url")
            .remove("user")
            .remove("role")
            .remove("cursor")
            .remove("watermark")
            .remove("lastSyncAt")
            .remove("lastError")
            .apply()
        _account.value = null
    }

    var cursor: Long
        get() = sp.getLong("cursor", 0)
        set(v) = sp.edit().putLong("cursor", v).apply()

    var watermark: Long
        get() = sp.getLong("watermark", 0)
        set(v) = sp.edit().putLong("watermark", v).apply()

    var lastSyncAt: Long?
        get() = if (sp.contains("lastSyncAt")) sp.getLong("lastSyncAt", 0) else null
        set(v) = sp.edit().apply { if (v == null) remove("lastSyncAt") else putLong("lastSyncAt", v) }.apply()

    var lastError: String?
        get() = sp.getString("lastError", null)
        set(v) = sp.edit().apply { if (v == null) remove("lastError") else putString("lastError", v) }.apply()

    /** 记住上次填写的服务器地址与用户名，方便退出后重新登录。 */
    var lastServerUrl: String
        get() = sp.getString("lastUrl", "") ?: ""
        set(v) = sp.edit().putString("lastUrl", v).apply()

    var lastUsername: String
        get() = sp.getString("lastUser", "") ?: ""
        set(v) = sp.edit().putString("lastUser", v).apply()
}
