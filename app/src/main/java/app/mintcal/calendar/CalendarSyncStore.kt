package app.mintcal.calendar

import android.content.Context
import android.content.SharedPreferences
import app.mintcal.core.sync.CalendarTaskLink
import app.mintcal.core.sync.CourseExportLink
import app.mintcal.core.util.MintJson
import kotlinx.serialization.builtins.ListSerializer

data class CalendarSyncPrefs(
    val enabled: Boolean = false,
    val selectedCalendarIds: Set<Long> = emptySet(),
    val primaryCalendarId: Long? = null,
    val exportCourses: Boolean = false,
    val lastSyncAt: Long? = null,
)

/** 系统日历同步配置与本地映射表存储（不进入云端 Snapshot，避免跨设备日历 ID 冲突）。 */
class CalendarSyncStore(
    context: Context,
    prefsName: String = "calendar_sync",
) {
    private val sp: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun readPrefs(): CalendarSyncPrefs {
        val ids =
            sp
                .getStringSet("selected_ids", emptySet())
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        val primary = sp.getLong("primary_id", -1L).takeIf { it >= 0L }
        val lastAt = sp.getLong("last_sync_at", -1L).takeIf { it > 0L }
        return CalendarSyncPrefs(
            enabled = sp.getBoolean("enabled", false),
            selectedCalendarIds = ids,
            primaryCalendarId = primary,
            exportCourses = sp.getBoolean("export_courses", false),
            lastSyncAt = lastAt,
        )
    }

    fun writePrefs(prefs: CalendarSyncPrefs) {
        sp
            .edit()
            .putBoolean("enabled", prefs.enabled)
            .putStringSet("selected_ids", prefs.selectedCalendarIds.map { it.toString() }.toSet())
            .putLong("primary_id", prefs.primaryCalendarId ?: -1L)
            .putBoolean("export_courses", prefs.exportCourses)
            .putLong("last_sync_at", prefs.lastSyncAt ?: -1L)
            .apply()
    }

    fun loadTaskLinks(): List<CalendarTaskLink> {
        val raw = sp.getString("task_links", null) ?: return emptyList()
        return runCatching {
            MintJson.decodeFromString(ListSerializer(CalendarTaskLink.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveTaskLinks(links: List<CalendarTaskLink>) {
        val raw = MintJson.encodeToString(ListSerializer(CalendarTaskLink.serializer()), links)
        sp.edit().putString("task_links", raw).apply()
    }

    fun loadCourseLinks(): List<CourseExportLink> {
        val raw = sp.getString("course_links", null) ?: return emptyList()
        return runCatching {
            MintJson.decodeFromString(ListSerializer(CourseExportLink.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveCourseLinks(links: List<CourseExportLink>) {
        val raw = MintJson.encodeToString(ListSerializer(CourseExportLink.serializer()), links)
        sp.edit().putString("course_links", raw).apply()
    }
}
