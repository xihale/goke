package app.mintcal.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 同步令牌的保管处：真机上由 [SecretStore]（Keystore 加密）实现，测试里可用内存实现。 */
interface SyncTokenVault {
    fun syncToken(): String?

    fun setSyncToken(plain: String)
}

/** 用 Android Keystore 里的 AES-GCM 密钥加密保存同步令牌与教务密码；明文永远不落盘、不进备份与日志。 */
class SecretStore(
    context: Context,
) : SyncTokenVault {
    private val sp = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    // Keystore 的 load + getKey 与解密结果按进程缓存：冷启动路径上只做一次加解密，
    // 之后 syncToken()/jxfwPassword() 的重复调用（同步、教务、状态流重建）零开销。
    @Volatile
    private var cachedKey: SecretKey? = null

    @Volatile
    private var cache: MutableMap<String, String?>? = null

    private fun cacheGet(name: String): String? {
        cache?.let { return it[name] }
        synchronized(this) {
            if (cache == null) cache = HashMap()
            return cache!![name]
        }
    }

    private fun cachePut(
        name: String,
        value: String?,
    ) {
        synchronized(this) {
            (cache ?: HashMap<String, String?>().also { cache = it })[name] = value
        }
    }

    init {
        // 清掉历史版本遗留的 AI Key（内置 AI 已移除，改用自建服务的 MCP 入口）
        sp.edit().remove("ai").apply()
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let {
            cachedKey = it
            return it
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey().also { cachedKey = it }
    }

    private fun put(
        name: String,
        plain: String,
    ) {
        if (plain.isBlank()) {
            sp.edit().remove(name).apply()
            cachePut(name, null)
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = c.iv + enc
        sp.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        cachePut(name, plain)
    }

    private fun get(name: String): String? {
        cacheGet(name)?.let { return it }
        val s = sp.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull().also { cachePut(name, it) }
    }

    /** 同步服务器的设备令牌（登录后由服务器签发，退出登录时清除）。 */
    override fun setSyncToken(plain: String) = put("sync_token", plain)

    override fun syncToken(): String? = get("sync_token")

    /** 教务系统（CAS）密码：仅为定时登录取课表保存，加密落盘，退出教务登录时清除。 */
    fun setJxfwPassword(plain: String) = put("jxfw_password", plain)

    fun jxfwPassword(): String? = get("jxfw_password")

    companion object {
        // 历史命名（曾是 AI Key 的别名）；换名会生成新密钥，导致旧密文（同步令牌/教务密码）无法解密
        private const val ALIAS = "mintcal_ai_key"
    }
}
