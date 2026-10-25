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

/** 用 Android Keystore 里的 AES-GCM 密钥加密保存 AI Key 与同步令牌；明文永远不落盘、不进备份与日志。 */
class SecretStore(
    context: Context,
) : SyncTokenVault {
    private val sp = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun put(
        name: String,
        plain: String,
    ) {
        if (plain.isBlank()) {
            sp.edit().remove(name).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = c.iv + enc
        sp.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun get(name: String): String? {
        val s = sp.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun setAiKey(plain: String) = put("ai", plain)

    fun hasAiKey(): Boolean = sp.contains("ai")

    fun aiKey(): String? = get("ai")

    /** 同步服务器的设备令牌（登录后由服务器签发，退出登录时清除）。 */
    override fun setSyncToken(plain: String) = put("sync_token", plain)

    override fun syncToken(): String? = get("sync_token")

    /** 教务系统（CAS）密码：仅为定时登录取课表保存，加密落盘，退出教务登录时清除。 */
    fun setJxfwPassword(plain: String) = put("jxfw_password", plain)

    fun jxfwPassword(): String? = get("jxfw_password")

    companion object {
        private const val ALIAS = "mintcal_ai_key"
    }
}
