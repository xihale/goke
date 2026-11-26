package app.goke.share

import android.content.Context
import app.goke.data.ShareTokenVault

/** 课表快速分享非敏感状态的本机持久化；管理密钥另存于 [ShareTokenVault]。 */
class TimetableShareStore(
    context: Context,
    private val secrets: ShareTokenVault,
    prefsName: String = "timetable_share",
) {
    private val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun manageToken(): String? = secrets.shareManageToken()

    fun setManageToken(plain: String) = secrets.setShareManageToken(plain)

    var serverUrl: String
        get() = sp.getString("serverUrl", "") ?: ""
        set(v) = sp.edit().putString("serverUrl", v).apply()

    var displayName: String
        get() = sp.getString("displayName", "") ?: ""
        set(v) = sp.edit().putString("displayName", v).apply()

    var manualStudentId: String
        get() = sp.getString("manualStudentId", "") ?: ""
        set(v) = sp.edit().putString("manualStudentId", v).apply()

    var code: String?
        get() = sp.getString("code", null)
        set(v) = sp.edit().apply { if (v == null) remove("code") else putString("code", v) }.apply()

    var shareUrl: String?
        get() = sp.getString("shareUrl", null)
        set(v) = sp.edit().apply { if (v == null) remove("shareUrl") else putString("shareUrl", v) }.apply()

    var boundStudentId: String?
        get() = sp.getString("boundStudentId", null)
        set(v) = sp.edit().apply { if (v == null) remove("boundStudentId") else putString("boundStudentId", v) }.apply()

    var semesterName: String?
        get() = sp.getString("semesterName", null)
        set(v) = sp.edit().apply { if (v == null) remove("semesterName") else putString("semesterName", v) }.apply()

    var updatedAt: Long?
        get() = if (sp.contains("updatedAt")) sp.getLong("updatedAt", 0L) else null
        set(v) = sp.edit().apply { if (v == null) remove("updatedAt") else putLong("updatedAt", v) }.apply()

    var expiresAt: Long?
        get() = if (sp.contains("expiresAt")) sp.getLong("expiresAt", 0L) else null
        set(v) = sp.edit().apply { if (v == null) remove("expiresAt") else putLong("expiresAt", v) }.apply()

    var expiryPresetDays: Int
        get() = sp.getInt("expiryPresetDays", 30)
        set(v) = sp.edit().putInt("expiryPresetDays", v).apply()

    fun saveActive(
        serverUrl: String,
        displayName: String,
        boundStudentId: String,
        code: String,
        shareUrl: String,
        semesterName: String?,
        updatedAt: Long,
        expiresAt: Long,
        manageToken: String?,
    ) {
        if (!manageToken.isNullOrBlank()) {
            secrets.setShareManageToken(manageToken)
        }
        sp
            .edit()
            .putString("serverUrl", serverUrl)
            .putString("displayName", displayName)
            .putString("boundStudentId", boundStudentId)
            .putString("code", code)
            .putString("shareUrl", shareUrl)
            .apply {
                if (semesterName != null) putString("semesterName", semesterName) else remove("semesterName")
            }.putLong("updatedAt", updatedAt)
            .putLong("expiresAt", expiresAt)
            .apply()
    }

    fun clearActive() {
        secrets.setShareManageToken("")
        sp
            .edit()
            .remove("code")
            .remove("shareUrl")
            .remove("boundStudentId")
            .remove("semesterName")
            .remove("updatedAt")
            .remove("expiresAt")
            .apply()
    }
}
