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
    val defaultBlockMinutes: Int = 60,
    val remindCourse: Boolean = false,
    val courseRemindMinutes: Int = 15,
    val remindTask: Boolean = true,
    val remindEvent: Boolean = true,
    val parcelEnabled: Boolean = false,
    val parcelAiFallback: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini",
    val weekStartsMonday: Boolean = true,
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
            parcelEnabled = sp.getBoolean("parcelEnabled", d.parcelEnabled),
            parcelAiFallback = sp.getBoolean("parcelAiFallback", d.parcelAiFallback),
            aiBaseUrl = sp.getString("aiBaseUrl", d.aiBaseUrl)!!,
            aiModel = sp.getString("aiModel", d.aiModel)!!,
            weekStartsMonday = sp.getBoolean("weekStartsMonday", d.weekStartsMonday),
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
            .putBoolean("parcelEnabled", n.parcelEnabled)
            .putBoolean("parcelAiFallback", n.parcelAiFallback)
            .putString("aiBaseUrl", n.aiBaseUrl)
            .putString("aiModel", n.aiModel)
            .putBoolean("weekStartsMonday", n.weekStartsMonday)
            .apply()
        _flow.value = n
    }
}
