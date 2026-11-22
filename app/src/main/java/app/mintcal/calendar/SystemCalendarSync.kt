package app.mintcal.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.mintcal.R
import app.mintcal.core.sync.CalendarBridge
import app.mintcal.core.sync.CalendarTaskLink
import app.mintcal.core.sync.CourseExportLink
import app.mintcal.core.sync.SystemEvent
import app.mintcal.core.sync.SystemEventDraft
import app.mintcal.data.Repository
import app.mintcal.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.TimeZone

data class SystemCalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val writable: Boolean,
)

data class CalendarSyncState(
    val prefs: CalendarSyncPrefs = CalendarSyncPrefs(),
    val calendars: List<SystemCalendarAccount> = emptyList(),
    val syncing: Boolean = false,
    val lastError: String? = null,
)

/**
 * Android 系统日历（CalendarContract）双向同步管理器：
 * - 支持读取并关联手机中已登录的 Google / 小米 / 华为 / DAVx5 / Exchange 等系统日历账本；
 * - 支持一键创建「工课」本地日历账本，供系统桌面自带日历小组件、负一屏与智能手表直接展示日程与课表；
 * - 监听本地快照与系统日历 ContentObserver，防抖对账、零回声循环。
 */
class SystemCalendarSync(
    private val context: Context,
    private val repo: Repository,
    private val settings: SettingsStore,
    private val store: CalendarSyncStore,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(CalendarSyncState(prefs = store.readPrefs()))
    val state: StateFlow<CalendarSyncState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    @Volatile
    private var suppressObserverUntil: Long = 0L

    private var observerRegistered = false
    private val contentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (System.currentTimeMillis() < suppressObserverUntil) return
                requestSync(1_500L)
            }
        }

    fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun updateObserving(active: Boolean) {
        val want = active && _state.value.prefs.enabled && hasPermissions()
        if (want && !observerRegistered) {
            runCatching {
                context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, contentObserver)
                observerRegistered = true
            }
        } else if (!want && observerRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(contentObserver) }
            observerRegistered = false
        }
    }

    suspend fun refreshCalendars(): List<SystemCalendarAccount> =
        withContext(Dispatchers.IO) {
            if (!hasPermissions()) {
                _state.value = _state.value.copy(calendars = emptyList())
                return@withContext emptyList()
            }
            val list = queryCalendars()
            val curPrefs = _state.value.prefs
            // 若已开启但还没选主账本，默认选中第一个可写账本
            val nextPrefs =
                if (curPrefs.selectedCalendarIds.isEmpty() && curPrefs.enabled && list.isNotEmpty()) {
                    val def = list.firstOrNull { it.writable } ?: list.first()
                    curPrefs.copy(selectedCalendarIds = setOf(def.id), primaryCalendarId = def.id).also(store::writePrefs)
                } else {
                    curPrefs
                }
            _state.value = _state.value.copy(calendars = list, prefs = nextPrefs)
            list
        }

    fun setEnabled(
        enabled: Boolean,
        uiActive: Boolean,
    ) {
        val cur = _state.value.prefs
        val cals = _state.value.calendars
        val selected =
            if (enabled && cur.selectedCalendarIds.isEmpty() && cals.isNotEmpty()) {
                setOf((cals.firstOrNull { it.writable } ?: cals.first()).id)
            } else {
                cur.selectedCalendarIds
            }
        val primary = cur.primaryCalendarId?.takeIf { it in selected } ?: selected.firstOrNull()
        val next = cur.copy(enabled = enabled, selectedCalendarIds = selected, primaryCalendarId = primary)
        store.writePrefs(next)
        _state.value = _state.value.copy(prefs = next, lastError = null)
        updateObserving(uiActive)
        if (enabled) requestSync(300L)
    }

    fun toggleCalendar(calendarId: Long) {
        val cur = _state.value.prefs
        val nextSet = if (calendarId in cur.selectedCalendarIds) cur.selectedCalendarIds - calendarId else cur.selectedCalendarIds + calendarId
        val writableIds =
            _state.value.calendars
                .filter { it.writable }
                .map { it.id }
                .toSet()
        val nextPrimary =
            cur.primaryCalendarId?.takeIf { it in nextSet && (writableIds.isEmpty() || it in writableIds) }
                ?: nextSet.firstOrNull { writableIds.isEmpty() || it in writableIds }
                ?: nextSet.firstOrNull()
        val next = cur.copy(selectedCalendarIds = nextSet, primaryCalendarId = nextPrimary)
        store.writePrefs(next)
        _state.value = _state.value.copy(prefs = next)
        if (next.enabled) requestSync(500L)
    }

    fun setExportCourses(export: Boolean) {
        val next = _state.value.prefs.copy(exportCourses = export)
        store.writePrefs(next)
        _state.value = _state.value.copy(prefs = next)
        if (next.enabled || !export) requestSync(300L)
    }

    /** 创建本地「工课」系统日历账本，供无云端日历账号的设备及桌面自带日历插件读取。 */
    suspend fun ensureLocalCalendar(): Long? =
        withContext(Dispatchers.IO) {
            if (!hasPermissions()) return@withContext null
            val existing = queryCalendars().firstOrNull { it.accountName == LOCAL_ACCOUNT_NAME && it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
            if (existing != null) {
                refreshCalendars()
                return@withContext existing.id
            }
            val label = context.getString(R.string.cal_sync_local_account_name)
            val values =
                ContentValues().apply {
                    put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
                    put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    put(CalendarContract.Calendars.NAME, "mintcal_local")
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, label)
                    put(CalendarContract.Calendars.CALENDAR_COLOR, MINT_COLOR)
                    put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                    put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                    put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
                }
            val uri =
                CalendarContract.Calendars.CONTENT_URI
                    .buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    .build()
            val inserted = runCatching { context.contentResolver.insert(uri, values) }.getOrNull()
            val id = inserted?.let { ContentUris.parseId(it) }?.takeIf { it >= 0L }
            val cals = queryCalendars()
            if (id != null) {
                val cur = _state.value.prefs
                val nextSet = cur.selectedCalendarIds + id
                val nextPrefs = cur.copy(selectedCalendarIds = nextSet, primaryCalendarId = cur.primaryCalendarId ?: id)
                store.writePrefs(nextPrefs)
                _state.value = _state.value.copy(calendars = cals, prefs = nextPrefs)
                if (nextPrefs.enabled) requestSync(300L)
            } else {
                _state.value = _state.value.copy(calendars = cals)
            }
            id
        }

    fun requestSync(delayMs: Long = 1_500L) {
        val prefs = _state.value.prefs
        if (!prefs.enabled && store.loadCourseLinks().isEmpty()) return
        debounceJob?.cancel()
        debounceJob =
            scope.launch {
                delay(delayMs)
                runCatching { syncNow() }
            }
    }

    suspend fun syncNow(): Int =
        lock.withLock {
            if (!hasPermissions()) return@withLock 0
            val prefs = store.readPrefs()
            if (!prefs.enabled && store.loadCourseLinks().isEmpty()) return@withLock 0
            _state.value = _state.value.copy(syncing = true, lastError = null)
            try {
                withContext(Dispatchers.IO) {
                    val cals = queryCalendars()
                    val validIds = cals.map { it.id }.toSet()
                    val writableIds = cals.filter { it.writable }.map { it.id }.toSet()
                    val selectedIds = prefs.selectedCalendarIds.intersect(validIds)
                    val primaryId =
                        prefs.primaryCalendarId?.takeIf { it in selectedIds && it in writableIds }
                            ?: selectedIds.firstOrNull { it in writableIds }

                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()
                    val snap = repo.snapshot.value
                    val st = settings.value

                    val queryIds = (selectedIds + store.loadCourseLinks().map { it.calendarId }).distinct()
                    val remotes = if (queryIds.isEmpty()) emptyList() else queryEvents(queryIds, now)

                    var changedCount = 0
                    suppressObserverUntil = System.currentTimeMillis() + 5_000L

                    // 1. 双向同步日程与待办
                    if (prefs.enabled && selectedIds.isNotEmpty()) {
                        val existingLinks = store.loadTaskLinks()
                        val plan =
                            CalendarBridge.planBidirectional(
                                snapshot = snap,
                                remoteEvents = remotes,
                                existingLinks = existingLinks,
                                primaryCalendarId = primaryId,
                                selectedCalendarIds = selectedIds,
                                defaultBlockMinutes = st.defaultBlockMinutes,
                                zone = zone,
                                nowMillis = now,
                            )

                        val finalLinks = ArrayList<CalendarTaskLink>(plan.retainedAndPulledLinks)
                        for (del in plan.remoteDeletes) {
                            if (del.calendarId in writableIds && deleteRemoteEvent(del.eventId)) changedCount++
                        }
                        for (upd in plan.remoteUpdates) {
                            if (upd.calendarId in writableIds && updateRemoteEvent(upd.eventId, upd.draft)) {
                                changedCount++
                                val sim = CalendarBridge.draftToSimulatedEvent(upd.eventId, upd.draft)
                                finalLinks += CalendarTaskLink(upd.taskId, upd.calendarId, upd.eventId, upd.taskHash, CalendarBridge.eventHash(sim, zone, st.defaultBlockMinutes), now)
                            }
                        }
                        for (ins in plan.remoteInserts) {
                            if (ins.draft.calendarId in writableIds) {
                                val newId = insertRemoteEvent(ins.draft)
                                if (newId != null) {
                                    changedCount++
                                    val sim = CalendarBridge.draftToSimulatedEvent(newId, ins.draft)
                                    finalLinks += CalendarTaskLink(ins.taskId, ins.draft.calendarId, newId, ins.taskHash, CalendarBridge.eventHash(sim, zone, st.defaultBlockMinutes), now)
                                }
                            }
                        }
                        val localCs = plan.localChanges
                        if (localCs != null && !localCs.isEmpty) {
                            repo.commit(localCs, record = false)
                            changedCount += localCs.changes.size
                        }
                        store.saveTaskLinks(finalLinks)
                    }

                    // 2. 导出或清理学期课表
                    val prevCourseLinks = store.loadCourseLinks()
                    val wantCourses = prefs.enabled && prefs.exportCourses && primaryId != null
                    if (wantCourses || prevCourseLinks.isNotEmpty()) {
                        val cPlan =
                            CalendarBridge.planCourseExport(
                                snapshot = repo.snapshot.value,
                                enabled = wantCourses,
                                targetCalendarId = primaryId,
                                existingLinks = prevCourseLinks,
                                remoteEvents = remotes,
                                zone = zone,
                            )
                        val nextCourseLinks = ArrayList<CourseExportLink>(cPlan.unchangedLinks)
                        for (del in cPlan.deletes) {
                            if (deleteRemoteEvent(del.eventId)) changedCount++
                        }
                        for (upd in cPlan.updates) {
                            if (updateRemoteEvent(upd.eventId, upd.draft)) {
                                changedCount++
                                nextCourseLinks += CourseExportLink(upd.slotKey, upd.calendarId, upd.eventId, upd.slotHash)
                            }
                        }
                        for (ins in cPlan.inserts) {
                            val newId = insertRemoteEvent(ins.draft)
                            if (newId != null) {
                                changedCount++
                                nextCourseLinks += CourseExportLink(ins.slotKey, ins.draft.calendarId, newId, ins.slotHash)
                            }
                        }
                        store.saveCourseLinks(nextCourseLinks)
                    }

                    suppressObserverUntil = System.currentTimeMillis() + 3_000L
                    val nextPrefs = prefs.copy(lastSyncAt = System.currentTimeMillis())
                    store.writePrefs(nextPrefs)
                    _state.value = _state.value.copy(calendars = cals, prefs = nextPrefs, syncing = false, lastError = null)
                    changedCount
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(syncing = false, lastError = e.message)
                0
            }
        }

    private fun queryCalendars(): List<SystemCalendarAccount> {
        val out = mutableListOf<SystemCalendarAccount>()
        val projection =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            )
        runCatching {
            context.contentResolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    "${CalendarContract.Calendars.DELETED} = 0",
                    null,
                    "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val display = c.getString(1).orEmpty().ifBlank { c.getString(2).orEmpty() }
                        val accName = c.getString(2).orEmpty()
                        val accType = c.getString(3).orEmpty()
                        val color = if (c.isNull(4)) MINT_COLOR else c.getInt(4)
                        val access = if (c.isNull(5)) 0 else c.getInt(5)
                        out +=
                            SystemCalendarAccount(
                                id = id,
                                displayName = display,
                                accountName = accName,
                                accountType = accType,
                                color = color,
                                writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            )
                    }
                }
        }
        return out
    }

    private fun queryEvents(
        calendarIds: List<Long>,
        nowMillis: Long,
    ): List<SystemEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val windowStart = nowMillis - 90L * 24L * 3600_000L
        val windowEnd = nowMillis + 365L * 24L * 3600_000L
        val placeholders = calendarIds.joinToString(",") { "?" }
        val selection =
            "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders) AND " +
                "(${CalendarContract.Events.RRULE} IS NOT NULL OR (${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?))"
        val args = (calendarIds.map { it.toString() } + listOf(windowStart.toString(), windowEnd.toString())).toTypedArray()

        val projection =
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.DELETED,
                CalendarContract.Events.CUSTOM_APP_URI,
            )

        val rawEvents = mutableListOf<SystemEvent>()
        runCatching {
            context.contentResolver
                .query(CalendarContract.Events.CONTENT_URI, projection, selection, args, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val evId = c.getLong(0)
                        val calId = c.getLong(1)
                        val title = c.getString(2).orEmpty()
                        val desc = c.getString(3).orEmpty()
                        val loc = c.getString(4).orEmpty()
                        val dtStart = if (c.isNull(5)) continue else c.getLong(5)
                        val dtEnd = if (c.isNull(6)) null else c.getLong(6).takeIf { it > 0L }
                        val dur = c.getString(7)?.takeIf { it.isNotBlank() }
                        val allDay = !c.isNull(8) && c.getInt(8) != 0
                        val rrule = c.getString(9)?.takeIf { it.isNotBlank() }
                        val deleted = !c.isNull(10) && c.getInt(10) != 0
                        val appUri = c.getString(11)?.takeIf { it.isNotBlank() }
                        rawEvents +=
                            SystemEvent(
                                eventId = evId,
                                calendarId = calId,
                                title = title,
                                description = desc,
                                location = loc,
                                dtStartMillis = dtStart,
                                dtEndMillis = dtEnd,
                                durationRfc2445 = dur,
                                allDay = allDay,
                                rrule = rrule,
                                remindMinutes = null,
                                deleted = deleted,
                                customAppUri = appUri,
                            )
                    }
                }
        }
        if (rawEvents.isEmpty()) return emptyList()

        val remindersByEvent = queryReminders(rawEvents.map { it.eventId })
        return rawEvents.map { ev -> ev.copy(remindMinutes = remindersByEvent[ev.eventId]) }
    }

    private fun queryReminders(eventIds: List<Long>): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        for (chunk in eventIds.chunked(200)) {
            val ph = chunk.joinToString(",") { "?" }
            runCatching {
                context.contentResolver
                    .query(
                        CalendarContract.Reminders.CONTENT_URI,
                        arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
                        "${CalendarContract.Reminders.EVENT_ID} IN ($ph)",
                        chunk.map { it.toString() }.toTypedArray(),
                        null,
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val eid = c.getLong(0)
                            val min = c.getInt(1)
                            if (min >= 0 && eid !in out) out[eid] = min
                        }
                    }
            }
        }
        return out
    }

    private fun draftToContentValues(draft: SystemEventDraft): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, draft.calendarId)
            put(CalendarContract.Events.TITLE, draft.title)
            put(CalendarContract.Events.DESCRIPTION, draft.description)
            put(CalendarContract.Events.EVENT_LOCATION, draft.location)
            put(CalendarContract.Events.DTSTART, draft.dtStartMillis)
            if (draft.dtEndMillis != null) {
                put(CalendarContract.Events.DTEND, draft.dtEndMillis)
                putNull(CalendarContract.Events.DURATION)
            } else {
                putNull(CalendarContract.Events.DTEND)
                put(CalendarContract.Events.DURATION, draft.durationRfc2445)
            }
            put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else TimeZone.getDefault().id)
            if (draft.rrule != null) {
                put(CalendarContract.Events.RRULE, draft.rrule)
            } else {
                putNull(CalendarContract.Events.RRULE)
            }
            put(CalendarContract.Events.HAS_ALARM, if (draft.remindMinutes != null) 1 else 0)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, draft.customAppUri)
        }

    private fun insertRemoteEvent(draft: SystemEventDraft): Long? =
        runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, draftToContentValues(draft)) ?: return null
            val eventId = ContentUris.parseId(uri).takeIf { it >= 0L } ?: return null
            syncEventReminder(eventId, draft.remindMinutes)
            eventId
        }.getOrNull()

    private fun updateRemoteEvent(
        eventId: Long,
        draft: SystemEventDraft,
    ): Boolean =
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val updated = context.contentResolver.update(uri, draftToContentValues(draft), null, null)
            syncEventReminder(eventId, draft.remindMinutes)
            updated >= 0
        }.getOrDefault(false)

    private fun deleteRemoteEvent(eventId: Long): Boolean =
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null) >= 0
        }.getOrDefault(false)

    private fun syncEventReminder(
        eventId: Long,
        remindMinutes: Int?,
    ) {
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            if (remindMinutes != null && remindMinutes >= 0) {
                val cv =
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, remindMinutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, cv)
            }
        }
    }

    companion object {
        const val LOCAL_ACCOUNT_NAME = "MintCal"
        private const val MINT_COLOR = 0xFF2E776C.toInt()
    }
}
