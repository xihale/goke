package app.goke.data

import android.content.Context
import android.content.SharedPreferences
import app.goke.core.model.Reminders
import app.goke.core.todo.QuickAddConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeSource { DYNAMIC, PRESET, CUSTOM }

enum class DarkMode { SYSTEM, LIGHT, DARK }

enum class UpdateInterval { STARTUP, DAILY, WEEKLY, OFF }

data class Settings(
    val themeSource: ThemeSource = ThemeSource.PRESET,
    val preset: String = "mint",
    val seedColor: Int = 0xFF2E776C.toInt(),
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val showTeacher: Boolean = true,
    val defaultBlockMinutes: Int = 30,
    val remindCourse: Boolean = false,
    val courseRemindMinutes: Int = 15,
    val courseRemindMinutesList: List<Int> = listOf(15),
    val remindExam: Boolean = true,
    val examRemindMinutesList: List<Int> = listOf(60, 1440),
    val examRemindPersistent: Boolean = false,
    val remindTask: Boolean = true,
    val remindEvent: Boolean = true,
    /** 「今日安排」摘要通知，默认关；时间由 [digestHour]/[digestMinute] 决定。 */
    val dailyDigest: Boolean = false,
    /** 每日摘要的触发时间（24 小时制），默认 7:30。 */
    val digestHour: Int = 7,
    val digestMinute: Int = 30,
    /** 常驻通知栏的「快速添加待办」入口，默认关。 */
    val quickAddNotif: Boolean = false,
    /** 交换待办滑动方向：左滑完成、右滑改期。 */
    val swapSwipe: Boolean = false,
    /** 完成任务、新建保存等交互的震动反馈，默认开启。 */
    val hapticFeedback: Boolean = true,
    /** 完成任务、新建保存等交互的轻音效反馈，默认开启（随系统静音/震动模式自动静默）。 */
    val soundFeedback: Boolean = true,
    /** 上课期间自动静音（勿扰），下课恢复；需要勿扰权限。 */
    val autoMute: Boolean = false,
    /** 每天自动备份一次全量 JSON 到应用外部目录，默认关。 */
    val autoBackup: Boolean = false,
    val backupKeep: Int = 7,
    val parcelEnabled: Boolean = false,
    /** 快递取件时段开始（当日分钟数，默认 14:00）：识别出的待办排进每天的这段时间。 */
    val parcelWindowStart: Int = 14 * 60,
    /** 取件时段结束（当日分钟数，默认 22:00）。 */
    val parcelWindowEnd: Int = 22 * 60,
    val weekStartsMonday: Boolean = true,
    /** 一次性迁移标记：日程已并入待办。 */
    val unifiedTasks: Boolean = false,
    /** 自动检查更新频率，默认每天检查一次（从落地页获取更新清单）。 */
    val updateInterval: UpdateInterval = UpdateInterval.DAILY,
    val lastUpdateCheckAt: Long = 0L,
    val pendingUpdateVersion: String = "",
    val pendingUpdateUrl: String = "",
    val pendingUpdateNotes: String = "",
    val pendingUpdateApkUrl: String = "",
    val pendingUpdateApkSize: Long = 0L,
    val pendingUpdateApkSha256: String = "",
    /** 已在弹窗中点过「以后再说」的版本，同版本不再重复自动弹窗，仍保留在设置页提示。 */
    val dismissedUpdateVersion: String = "",
    val quickRemindKeyword: String = QuickAddConfig.DEFAULT_REMIND_KEYWORD,
    val quickRemindMinutesList: List<Int> = QuickAddConfig.DEFAULT_REMIND_MINUTES,
    val quickRemindPersistentMinutesList: List<Int> = QuickAddConfig.DEFAULT_REMIND_MINUTES,
    val quickRemindBangPersistent: Boolean = true,
    /** 自托管 DropRelay 资料库地址（公开仓默认空，私有构建可回退校园默认地址）。 */
    val dropRelayShareUrl: String = "",
    /** 自托管 DropRelay 快传地址（公开仓默认空，私有构建可回退校园默认地址）。 */
    val dropRelayDropUrl: String = "",
    /** 是否启用系统分享菜单中的「工课快传」入口（默认关闭，避免污染系统分享菜单）。 */
    val dropShareSheetEnabled: Boolean = false,
) {
    val quickAddConfig: QuickAddConfig
        get() =
            QuickAddConfig(
                remindKeyword = quickRemindKeyword,
                remindMinutesList = quickRemindMinutesList,
                persistentRemindMinutesList = quickRemindPersistentMinutesList,
                bangPersistent = quickRemindBangPersistent,
            )

    val effectiveCourseRemindMinutes: List<Int>
        get() =
            Reminders
                .normalize(if (courseRemindMinutesList.isEmpty()) courseRemindMinutes else null, courseRemindMinutesList)
                .ifEmpty { listOf(courseRemindMinutes.coerceAtLeast(0)) }

    val effectiveExamRemindMinutes: List<Int>
        get() =
            Reminders
                .normalize(null, examRemindMinutesList)
                .ifEmpty { listOf(60, 1440) }
}

/** 轻量设置存储（SharedPreferences + StateFlow）。同步令牌等敏感值另存于 [SecretStore]，不进此处。 */
class SettingsStore(
    context: Context,
) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    private fun readMinutesList(
        key: String,
        fallback: List<Int>,
    ): List<Int> {
        val raw = sp.getString(key, null) ?: return fallback
        val parsed = raw.split(',').mapNotNull { it.trim().toIntOrNull() }
        return Reminders.normalize(null, parsed).ifEmpty { fallback }
    }

    private fun read(): Settings {
        val d = Settings()
        val courseRemind = sp.getInt("courseRemindMinutes", d.courseRemindMinutes).coerceAtLeast(0)
        val parsedRemindList = readMinutesList("courseRemindMinutesList", listOf(courseRemind))
        val parsedExamRemindList = readMinutesList("examRemindMinutesList", d.examRemindMinutesList)
        return Settings(
            themeSource = runCatching { ThemeSource.valueOf(sp.getString("themeSource", d.themeSource.name)!!) }.getOrDefault(d.themeSource),
            preset = sp.getString("preset", d.preset)!!,
            seedColor = sp.getInt("seedColor", d.seedColor),
            darkMode = runCatching { DarkMode.valueOf(sp.getString("darkMode", d.darkMode.name)!!) }.getOrDefault(d.darkMode),
            showTeacher = sp.getBoolean("showTeacher", d.showTeacher),
            defaultBlockMinutes = sp.getInt("defaultBlockMinutes", d.defaultBlockMinutes),
            remindCourse = sp.getBoolean("remindCourse", d.remindCourse),
            courseRemindMinutes = parsedRemindList.first(),
            courseRemindMinutesList = parsedRemindList,
            remindExam = sp.getBoolean("remindExam", d.remindExam),
            examRemindMinutesList = parsedExamRemindList,
            examRemindPersistent = sp.getBoolean("examRemindPersistent", d.examRemindPersistent),
            remindTask = sp.getBoolean("remindTask", d.remindTask),
            remindEvent = sp.getBoolean("remindEvent", d.remindEvent),
            dailyDigest = sp.getBoolean("dailyDigest", d.dailyDigest),
            digestHour = sp.getInt("digestHour", d.digestHour).coerceIn(0, 23),
            digestMinute = sp.getInt("digestMinute", d.digestMinute).coerceIn(0, 59),
            quickAddNotif = sp.getBoolean("quickAddNotif", d.quickAddNotif),
            swapSwipe = sp.getBoolean("swapSwipe", d.swapSwipe),
            hapticFeedback = sp.getBoolean("hapticFeedback", d.hapticFeedback),
            soundFeedback = sp.getBoolean("soundFeedback", d.soundFeedback),
            autoMute = sp.getBoolean("autoMute", d.autoMute),
            autoBackup = sp.getBoolean("autoBackup", d.autoBackup),
            backupKeep = sp.getInt("backupKeep", d.backupKeep),
            parcelEnabled = sp.getBoolean("parcelEnabled", d.parcelEnabled),
            parcelWindowStart = sp.getInt("parcelWindowStart", d.parcelWindowStart).coerceIn(0, 24 * 60 - 1),
            parcelWindowEnd = sp.getInt("parcelWindowEnd", d.parcelWindowEnd).coerceIn(0, 24 * 60),
            weekStartsMonday = sp.getBoolean("weekStartsMonday", d.weekStartsMonday),
            unifiedTasks = sp.getBoolean("unifiedTasks", d.unifiedTasks),
            updateInterval = runCatching { UpdateInterval.valueOf(sp.getString("updateInterval", d.updateInterval.name)!!) }.getOrDefault(d.updateInterval),
            lastUpdateCheckAt = sp.getLong("lastUpdateCheckAt", d.lastUpdateCheckAt),
            pendingUpdateVersion = sp.getString("pendingUpdateVersion", d.pendingUpdateVersion)!!,
            pendingUpdateUrl = sp.getString("pendingUpdateUrl", d.pendingUpdateUrl)!!,
            pendingUpdateNotes = sp.getString("pendingUpdateNotes", d.pendingUpdateNotes)!!,
            pendingUpdateApkUrl = sp.getString("pendingUpdateApkUrl", d.pendingUpdateApkUrl)!!,
            pendingUpdateApkSize = sp.getLong("pendingUpdateApkSize", d.pendingUpdateApkSize),
            pendingUpdateApkSha256 = sp.getString("pendingUpdateApkSha256", d.pendingUpdateApkSha256)!!,
            dismissedUpdateVersion = sp.getString("dismissedUpdateVersion", d.dismissedUpdateVersion)!!,
            quickRemindKeyword = (sp.getString("quickRemindKeyword", d.quickRemindKeyword) ?: d.quickRemindKeyword).trim(),
            quickRemindMinutesList = readMinutesList("quickRemindMinutesList", d.quickRemindMinutesList),
            quickRemindPersistentMinutesList = readMinutesList("quickRemindPersistentMinutesList", d.quickRemindPersistentMinutesList),
            quickRemindBangPersistent = sp.getBoolean("quickRemindBangPersistent", d.quickRemindBangPersistent),
            dropRelayShareUrl = (sp.getString("dropRelayShareUrl", d.dropRelayShareUrl) ?: d.dropRelayShareUrl).trim(),
            dropRelayDropUrl = (sp.getString("dropRelayDropUrl", d.dropRelayDropUrl) ?: d.dropRelayDropUrl).trim(),
            dropShareSheetEnabled = sp.getBoolean("dropShareSheetEnabled", d.dropShareSheetEnabled),
        )
    }

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        val n =
            block(_flow.value).let { raw ->
                val list = raw.effectiveCourseRemindMinutes
                val examList = raw.effectiveExamRemindMinutes
                val qRemind = Reminders.normalize(null, raw.quickRemindMinutesList).ifEmpty { QuickAddConfig.DEFAULT_REMIND_MINUTES }
                val qBangRemind =
                    Reminders.normalize(null, raw.quickRemindPersistentMinutesList).ifEmpty {
                        QuickAddConfig.DEFAULT_REMIND_MINUTES
                    }
                raw.copy(
                    courseRemindMinutes = list.first(),
                    courseRemindMinutesList = list,
                    examRemindMinutesList = examList,
                    quickRemindKeyword = raw.quickRemindKeyword.trim(),
                    quickRemindMinutesList = qRemind,
                    quickRemindPersistentMinutesList = qBangRemind,
                )
            }
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
            .putString("courseRemindMinutesList", n.courseRemindMinutesList.joinToString(","))
            .putBoolean("remindExam", n.remindExam)
            .putString("examRemindMinutesList", n.examRemindMinutesList.joinToString(","))
            .putBoolean("examRemindPersistent", n.examRemindPersistent)
            .putBoolean("remindTask", n.remindTask)
            .putBoolean("remindEvent", n.remindEvent)
            .putBoolean("dailyDigest", n.dailyDigest)
            .putInt("digestHour", n.digestHour)
            .putInt("digestMinute", n.digestMinute)
            .putBoolean("quickAddNotif", n.quickAddNotif)
            .putBoolean("swapSwipe", n.swapSwipe)
            .putBoolean("hapticFeedback", n.hapticFeedback)
            .putBoolean("soundFeedback", n.soundFeedback)
            .putBoolean("autoMute", n.autoMute)
            .putBoolean("autoBackup", n.autoBackup)
            .putInt("backupKeep", n.backupKeep)
            .putBoolean("parcelEnabled", n.parcelEnabled)
            .putInt("parcelWindowStart", n.parcelWindowStart)
            .putInt("parcelWindowEnd", n.parcelWindowEnd)
            .putBoolean("weekStartsMonday", n.weekStartsMonday)
            .putBoolean("unifiedTasks", n.unifiedTasks)
            .putString("updateInterval", n.updateInterval.name)
            .putLong("lastUpdateCheckAt", n.lastUpdateCheckAt)
            .putString("pendingUpdateVersion", n.pendingUpdateVersion)
            .putString("pendingUpdateUrl", n.pendingUpdateUrl)
            .putString("pendingUpdateNotes", n.pendingUpdateNotes)
            .putString("pendingUpdateApkUrl", n.pendingUpdateApkUrl)
            .putLong("pendingUpdateApkSize", n.pendingUpdateApkSize)
            .putString("pendingUpdateApkSha256", n.pendingUpdateApkSha256)
            .putString("dismissedUpdateVersion", n.dismissedUpdateVersion)
            .putString("quickRemindKeyword", n.quickRemindKeyword)
            .putString("quickRemindMinutesList", n.quickRemindMinutesList.joinToString(","))
            .putString("quickRemindPersistentMinutesList", n.quickRemindPersistentMinutesList.joinToString(","))
            .putBoolean("quickRemindBangPersistent", n.quickRemindBangPersistent)
            .putString("dropRelayShareUrl", n.dropRelayShareUrl.trim())
            .putString("dropRelayDropUrl", n.dropRelayDropUrl.trim())
            .putBoolean("dropShareSheetEnabled", n.dropShareSheetEnabled)
            .apply()
        _flow.value = n
    }
}
