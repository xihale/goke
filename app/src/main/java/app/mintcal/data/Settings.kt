package app.mintcal.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeSource { DYNAMIC, PRESET, CUSTOM }

enum class DarkMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeSource: ThemeSource = ThemeSource.DYNAMIC,
    val preset: String = "mint",
    val seedColor: Int = 0xFF4FA796.toInt(),
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val showTeacher: Boolean = false,
    val defaultBlockMinutes: Int = 30,
    val remindCourse: Boolean = false,
    val courseRemindMinutes: Int = 15,
    val remindTask: Boolean = true,
    val remindEvent: Boolean = true,
    /** 每天早上 7:30 的「今日安排」摘要通知，默认关。 */
    val dailyDigest: Boolean = false,
    /** 常驻通知栏的「快速添加待办」入口，默认关。 */
    val quickAddNotif: Boolean = false,
    /** 交换待办滑动方向：左滑完成、右滑改期。 */
    val swapSwipe: Boolean = false,
    /** 上课期间自动静音（勿扰），下课恢复；需要勿扰权限。 */
    val autoMute: Boolean = false,
    /** 每天自动备份一次全量 JSON 到应用外部目录，默认关。 */
    val autoBackup: Boolean = false,
    val backupKeep: Int = 7,
    val parcelEnabled: Boolean = false,
    val parcelAiFallback: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini",
    val weekStartsMonday: Boolean = true,
    /** 一次性迁移标记：日程已并入待办。 */
    val unifiedTasks: Boolean = false,
)

/** 轻量设置存储（SharedPreferences + StateFlow）。AI Key 另存于 [SecretStore]，不进此处。 */
class SettingsStore(
    context: Context,
) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            themeSource = runCatching { ThemeSource.valueOf(sp.getString("themeSource", d.themeSource.name)!!) }.getOrDefault(d.themeSource),
            preset = sp.getString("preset", d.preset)!!,
            seedColor = sp.getInt("seedColor", d.seedColor),
            darkMode = runCatching { DarkMode.valueOf(sp.getString("darkMode", d.darkMode.name)!!) }.getOrDefault(d.darkMode),
            showTeacher = sp.getBoolean("showTeacher", d.showTeacher),
            defaultBlockMinutes = sp.getInt("defaultBlockMinutes", d.defaultBlockMinutes),
            remindCourse = sp.getBoolean("remindCourse", d.remindCourse),
            courseRemindMinutes = sp.getInt("courseRemindMinutes", d.courseRemindMinutes),
            remindTask = sp.getBoolean("remindTask", d.remindTask),
            remindEvent = sp.getBoolean("remindEvent", d.remindEvent),
            dailyDigest = sp.getBoolean("dailyDigest", d.dailyDigest),
            quickAddNotif = sp.getBoolean("quickAddNotif", d.quickAddNotif),
            swapSwipe = sp.getBoolean("swapSwipe", d.swapSwipe),
            autoMute = sp.getBoolean("autoMute", d.autoMute),
            autoBackup = sp.getBoolean("autoBackup", d.autoBackup),
            backupKeep = sp.getInt("backupKeep", d.backupKeep),
            parcelEnabled = sp.getBoolean("parcelEnabled", d.parcelEnabled),
            parcelAiFallback = sp.getBoolean("parcelAiFallback", d.parcelAiFallback),
            aiBaseUrl = sp.getString("aiBaseUrl", d.aiBaseUrl)!!,
            aiModel = sp.getString("aiModel", d.aiModel)!!,
            weekStartsMonday = sp.getBoolean("weekStartsMonday", d.weekStartsMonday),
            unifiedTasks = sp.getBoolean("unifiedTasks", d.unifiedTasks),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val n = block(_flow.value)
        sp
            .edit()
            .putString("themeSource", n.themeSource.name)
            .putString("preset", n.preset)
            .putInt("seedColor", n.seedColor)
            .putString("darkMode", n.darkMode.name)
            .putBoolean("showTeacher", n.showTeacher)
            .putInt("defaultBlockMinutes", n.defaultBlockMinutes)
            .putBoolean("remindCourse", n.remindCourse)
            .putInt("courseRemindMinutes", n.courseRemindMinutes)
            .putBoolean("remindTask", n.remindTask)
            .putBoolean("remindEvent", n.remindEvent)
            .putBoolean("dailyDigest", n.dailyDigest)
            .putBoolean("quickAddNotif", n.quickAddNotif)
            .putBoolean("swapSwipe", n.swapSwipe)
            .putBoolean("autoMute", n.autoMute)
            .putBoolean("autoBackup", n.autoBackup)
            .putInt("backupKeep", n.backupKeep)
            .putBoolean("parcelEnabled", n.parcelEnabled)
            .putBoolean("parcelAiFallback", n.parcelAiFallback)
            .putString("aiBaseUrl", n.aiBaseUrl)
            .putString("aiModel", n.aiModel)
            .putBoolean("weekStartsMonday", n.weekStartsMonday)
            .putBoolean("unifiedTasks", n.unifiedTasks)
            .apply()
        _flow.value = n
    }
}
