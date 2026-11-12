package app.mintcal.core.sync

import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Recurrence
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.util.IdGenerator
import app.mintcal.core.util.Ids
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 系统日历（CalendarContract.Events）读取出的一条事件快照，脱离 Android 依赖以便纯 JVM 测试。 */
data class SystemEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val dtStartMillis: Long,
    val dtEndMillis: Long? = null,
    val durationRfc2445: String? = null,
    val allDay: Boolean = false,
    val rrule: String? = null,
    val remindMinutes: Int? = null,
    val deleted: Boolean = false,
    val customAppUri: String? = null,
)

/** 准备写入系统日历的事件草稿（遵循 CalendarContract 约束：重复事件用 DURATION，单次事件用 DTEND；全天事件用 UTC 零点）。 */
data class SystemEventDraft(
    val calendarId: Long,
    val title: String,
    val description: String,
    val location: String,
    val dtStartMillis: Long,
    val dtEndMillis: Long?,
    val durationRfc2445: String?,
    val allDay: Boolean,
    val rrule: String?,
    val remindMinutes: Int?,
    val customAppUri: String,
)

/** 本地任务与系统日历事件的配对记录（仅存本机，不污染云端同步与操作撤销历史）。 */
@Serializable
data class CalendarTaskLink(
    val taskId: String,
    val calendarId: Long,
    val eventId: Long,
    val taskHash: String,
    val eventHash: String,
    val syncedAt: Long,
)

/** 课程表展开实例与系统日历事件的配对记录。 */
@Serializable
data class CourseExportLink(
    val slotKey: String,
    val calendarId: Long,
    val eventId: Long,
    val slotHash: String,
)

data class RemoteInsert(
    val taskId: String,
    val taskHash: String,
    val draft: SystemEventDraft,
)

data class RemoteUpdate(
    val taskId: String,
    val calendarId: Long,
    val eventId: Long,
    val taskHash: String,
    val draft: SystemEventDraft,
)

data class RemoteDelete(
    val calendarId: Long,
    val eventId: Long,
)

data class PendingPullLink(
    val taskId: String,
    val calendarId: Long,
    val eventId: Long,
    val taskHash: String,
    val eventHash: String,
)

data class BidirectionalPlan(
    val localChanges: ChangeSet?,
    val remoteInserts: List<RemoteInsert>,
    val remoteUpdates: List<RemoteUpdate>,
    val remoteDeletes: List<RemoteDelete>,
    /** 保持不变的配对 + 拉取回本地的新/更新配对（远端 Insert/Update 成功后由调用方补入最终 eventHash）。 */
    val retainedAndPulledLinks: List<CalendarTaskLink>,
) {
    val totalMutations: Int
        get() = (localChanges?.changes?.size ?: 0) + remoteInserts.size + remoteUpdates.size + remoteDeletes.size
}

data class CourseInsert(
    val slotKey: String,
    val slotHash: String,
    val draft: SystemEventDraft,
)

data class CourseUpdate(
    val slotKey: String,
    val calendarId: Long,
    val eventId: Long,
    val slotHash: String,
    val draft: SystemEventDraft,
)

data class CourseExportPlan(
    val inserts: List<CourseInsert>,
    val updates: List<CourseUpdate>,
    val deletes: List<RemoteDelete>,
    val unchangedLinks: List<CourseExportLink>,
) {
    val totalMutations: Int get() = inserts.size + updates.size + deletes.size
}

/**
 * MintCal ⇄ Android 系统日历纯函数对账引擎：
 * 1. 支持日程块（blockStart）、带时刻待办（due+dueHasTime）与全天截止待办（allDay UTC 零点）双向互转；
 * 2. 基于双端内容指纹（taskHash / eventHash）精确识别单边修改，彻底阻断回声死循环（Self-Echo）；
 * 3. 用户取消勾选某个日历账本时只解除本地映射，绝不误删本地任务。
 */
object CalendarBridge {
    const val TASK_URI_PREFIX = "mintcal://task/"
    const val COURSE_URI_PREFIX = "mintcal://course/"
    private const val DONE_PREFIX = "✓ "
    private val UTC_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun planBidirectional(
        snapshot: Snapshot,
        remoteEvents: List<SystemEvent>,
        existingLinks: List<CalendarTaskLink>,
        primaryCalendarId: Long?,
        selectedCalendarIds: Set<Long>,
        defaultBlockMinutes: Int = 30,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
        ids: IdGenerator = Ids,
    ): BidirectionalPlan {
        if (selectedCalendarIds.isEmpty()) {
            return BidirectionalPlan(null, emptyList(), emptyList(), emptyList(), emptyList())
        }

        val tasksById = snapshot.tasks.associateBy { it.id }
        val activeRemote =
            remoteEvents
                .filter { it.calendarId in selectedCalendarIds && it.customAppUri?.startsWith(COURSE_URI_PREFIX) != true }
        val remoteByKey = activeRemote.associateBy { it.calendarId to it.eventId }

        val localChanges = mutableListOf<Change>()
        val remoteInserts = mutableListOf<RemoteInsert>()
        val remoteUpdates = mutableListOf<RemoteUpdate>()
        val remoteDeletes = mutableListOf<RemoteDelete>()
        val nextLinks = mutableListOf<CalendarTaskLink>()

        val handledTaskIds = HashSet<String>()
        val handledRemoteKeys = HashSet<Pair<Long, Long>>()

        // 1. 先对账已有映射（含 customAppUri 自愈恢复的孤儿配对）
        val mergedLinks = mergeLinksWithCustomUri(existingLinks, activeRemote, tasksById, defaultBlockMinutes, zone, nowMillis)
        for (link in mergedLinks) {
            // 若该日历账本已被用户取消勾选：静默解绑，两边都不删
            if (link.calendarId !in selectedCalendarIds) continue

            val task = tasksById[link.taskId]
            val remote = remoteByKey[link.calendarId to link.eventId]
            handledTaskIds += link.taskId
            if (remote != null) handledRemoteKeys += (link.calendarId to link.eventId)

            val taskGone = task == null || task.deletedAt != null || task.dropped || !isSyncableTask(task)
            val remoteGone = remote == null || remote.deleted

            when {
                taskGone && remoteGone -> {
                    // 双端均已删除/移出日程：自然移除配对
                }

                taskGone && !remoteGone -> {
                    // 本地已删或已清除日期 → 删除系统日历事件
                    remoteDeletes += RemoteDelete(link.calendarId, link.eventId)
                }

                !taskGone && remoteGone -> {
                    // 系统日历事件被用户在系统日历/桌面删掉 → 软删除本地任务
                    localChanges += Change.Remove(checkNotNull(task))
                }

                else -> {
                    val liveTask = checkNotNull(task)
                    val liveRemote = checkNotNull(remote)
                    val curTaskHash = taskHash(liveTask, defaultBlockMinutes, zone)
                    val curEventHash = eventHash(liveRemote, zone)
                    val tChanged = curTaskHash != link.taskHash
                    val eChanged = curEventHash != link.eventHash

                    when {
                        !tChanged && !eChanged -> {
                            nextLinks += link
                        }

                        tChanged && !eChanged -> {
                            val draft = toDraft(liveTask, link.calendarId, defaultBlockMinutes, zone)
                            if (draft != null) {
                                remoteUpdates += RemoteUpdate(liveTask.id, link.calendarId, link.eventId, curTaskHash, draft)
                            } else {
                                remoteDeletes += RemoteDelete(link.calendarId, link.eventId)
                            }
                        }

                        !tChanged && eChanged -> {
                            val updated = applyRemoteToTask(liveTask, liveRemote, defaultBlockMinutes, zone, nowMillis)
                            if (updated != liveTask) {
                                localChanges += Change.Modify(liveTask, updated)
                            }
                            val newTaskHash = taskHash(updated, defaultBlockMinutes, zone)
                            nextLinks += CalendarTaskLink(updated.id, link.calendarId, link.eventId, newTaskHash, curEventHash, nowMillis)
                        }

                        else -> {
                            // 双端同时改动：按本地更新时间与上次同步水位比较（LWW）
                            if (liveTask.updatedAt >= link.syncedAt) {
                                val draft = toDraft(liveTask, link.calendarId, defaultBlockMinutes, zone)
                                if (draft != null) {
                                    remoteUpdates += RemoteUpdate(liveTask.id, link.calendarId, link.eventId, curTaskHash, draft)
                                }
                            } else {
                                val updated = applyRemoteToTask(liveTask, liveRemote, defaultBlockMinutes, zone, nowMillis)
                                if (updated != liveTask) {
                                    localChanges += Change.Modify(liveTask, updated)
                                }
                                val newTaskHash = taskHash(updated, defaultBlockMinutes, zone)
                                nextLinks += CalendarTaskLink(updated.id, link.calendarId, link.eventId, newTaskHash, curEventHash, nowMillis)
                            }
                        }
                    }
                }
            }
        }

        // 2. 本地新增的未配对日程任务 → 推向主同步日历（已完成的历史旧任务不主动首次推送，避免刷屏）
        val targetCalId = primaryCalendarId?.takeIf { it in selectedCalendarIds } ?: selectedCalendarIds.firstOrNull()
        if (targetCalId != null) {
            for (task in snapshot.tasks) {
                if (task.id in handledTaskIds) continue
                if (task.deletedAt != null || task.dropped || task.done || !isSyncableTask(task)) continue
                val draft = toDraft(task, targetCalId, defaultBlockMinutes, zone) ?: continue
                val tHash = taskHash(task, defaultBlockMinutes, zone)
                remoteInserts += RemoteInsert(task.id, tHash, draft)
                handledTaskIds += task.id
            }
        }

        // 3. 系统日历新增的未配对事件 → 拉入 MintCal
        for (remote in activeRemote) {
            val key = remote.calendarId to remote.eventId
            if (key in handledRemoteKeys || remote.deleted) continue
            // 若带 mintcal://task/ 标记但本地任务已被删除，则顺手清理远端残留而不是复活它
            val orphanTaskId = remote.customAppUri?.takeIf { it.startsWith(TASK_URI_PREFIX) }?.removePrefix(TASK_URI_PREFIX)
            if (orphanTaskId != null && tasksById[orphanTaskId]?.deletedAt != null) {
                remoteDeletes += RemoteDelete(remote.calendarId, remote.eventId)
                continue
            }
            val created = toNewTask(remote, defaultBlockMinutes, zone, nowMillis, ids)
            localChanges += Change.Add(created)
            nextLinks +=
                CalendarTaskLink(
                    taskId = created.id,
                    calendarId = remote.calendarId,
                    eventId = remote.eventId,
                    taskHash = taskHash(created, defaultBlockMinutes, zone),
                    eventHash = eventHash(remote, zone),
                    syncedAt = nowMillis,
                )
        }

        val cs =
            if (localChanges.isEmpty()) {
                null
            } else {
                ChangeSet(ids.next(), "系统日历同步", Source.IMPORT, nowMillis, localChanges)
            }

        return BidirectionalPlan(cs, remoteInserts, remoteUpdates, remoteDeletes, nextLinks)
    }

    /** 将当前学期课程表（含停课与调课）差分导出到指定系统日历账本；若 enabled=false 则清理已导出的全部课程事件。 */
    fun planCourseExport(
        snapshot: Snapshot,
        enabled: Boolean,
        targetCalendarId: Long?,
        existingLinks: List<CourseExportLink>,
        remoteEvents: List<SystemEvent>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CourseExportPlan {
        val remoteCourseKeys =
            remoteEvents
                .filter { !it.deleted && it.customAppUri?.startsWith(COURSE_URI_PREFIX) == true }
                .associateBy { it.calendarId to it.eventId }

        val sem = snapshot.currentSemester
        if (!enabled || targetCalendarId == null || sem == null) {
            val deletes =
                (
                    existingLinks.map { RemoteDelete(it.calendarId, it.eventId) } +
                        remoteEvents
                            .filter { !it.deleted && it.customAppUri?.startsWith(COURSE_URI_PREFIX) == true }
                            .map { RemoteDelete(it.calendarId, it.eventId) }
                ).distinct()
            return CourseExportPlan(emptyList(), emptyList(), deletes, emptyList())
        }

        val live = snapshot.live()
        val cal =
            app.mintcal.core.time
                .SemesterCalendar(sem)
        val occs = CourseExpander.occurrences(sem, live.courses, live.exceptions, cal.weekStart(1), cal.weekEnd(sem.totalWeeks))
        val desired = LinkedHashMap<String, SystemEventDraft>()
        for (occ in occs) {
            val key = "${occ.course.id}:w${occ.week}:${occ.date}:${occ.startPeriod}-${occ.endPeriod}"
            val startMs =
                occ.start
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            val endMs =
                occ.end
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            val descParts =
                listOfNotNull(
                    "第 ${occ.startPeriod}–${occ.endPeriod} 节 · 第 ${occ.week} 周",
                    occ.course.teacher
                        .takeIf { it.isNotBlank() }
                        ?.let { "教师：$it" },
                    occ.course.note.takeIf { it.isNotBlank() },
                )
            desired[key] =
                SystemEventDraft(
                    calendarId = targetCalendarId,
                    title = occ.course.name,
                    description = descParts.joinToString("\n"),
                    location = occ.room,
                    dtStartMillis = startMs,
                    dtEndMillis = endMs,
                    durationRfc2445 = null,
                    allDay = false,
                    rrule = null,
                    remindMinutes = occ.course.remindMinutes,
                    customAppUri = COURSE_URI_PREFIX + key,
                )
        }

        val inserts = mutableListOf<CourseInsert>()
        val updates = mutableListOf<CourseUpdate>()
        val deletes = mutableListOf<RemoteDelete>()
        val unchanged = mutableListOf<CourseExportLink>()

        val existingBySlot = existingLinks.associateBy { it.slotKey }
        for ((slotKey, draft) in desired) {
            val hash = draftHash(draft)
            val prev = existingBySlot[slotKey]
            val remotePresent = prev != null && (prev.calendarId to prev.eventId) in remoteCourseKeys
            when {
                prev == null || prev.calendarId != targetCalendarId || !remotePresent -> {
                    if (prev != null && prev.calendarId != targetCalendarId) {
                        deletes += RemoteDelete(prev.calendarId, prev.eventId)
                    }
                    inserts += CourseInsert(slotKey, hash, draft)
                }

                prev.slotHash != hash -> {
                    updates += CourseUpdate(slotKey, prev.calendarId, prev.eventId, hash, draft)
                }

                else -> {
                    unchanged += prev
                }
            }
        }

        for (prev in existingLinks) {
            if (prev.slotKey !in desired) {
                deletes += RemoteDelete(prev.calendarId, prev.eventId)
            }
        }

        return CourseExportPlan(inserts, updates, deletes.distinct(), unchanged)
    }

    fun isSyncableTask(task: Task): Boolean = task.parentId == null && (task.blockStart != null || task.due != null)

    fun toDraft(
        task: Task,
        calendarId: Long,
        defaultBlockMinutes: Int = 30,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SystemEventDraft? {
        if (!isSyncableTask(task)) return null
        val rawTitle = task.title.trim().ifEmpty { return null }
        val title = if (task.done) DONE_PREFIX + rawTitle else rawTitle
        val location = task.extra["location"] ?: task.extra["place"].orEmpty()

        val blockStart = task.blockStart
        val due = task.due
        val allDay: Boolean
        val startLocal: LocalDateTime
        val durationMin: Int
        when {
            blockStart != null -> {
                allDay = false
                startLocal = blockStart
                durationMin = (task.blockMinutes ?: defaultBlockMinutes).coerceIn(1, 24 * 60)
            }

            due != null && task.dueHasTime -> {
                allDay = false
                startLocal = due
                durationMin = defaultBlockMinutes.coerceIn(1, 24 * 60)
            }

            due != null -> {
                allDay = true
                startLocal = due.toLocalDate().atStartOfDay()
                durationMin = 24 * 60
            }

            else -> {
                return null
            }
        }

        val dtStart =
            if (allDay) {
                startLocal
                    .toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            } else {
                startLocal.atZone(zone).toInstant().toEpochMilli()
            }
        val rrule = task.repeat?.let { formatRRule(it, zone) }
        val dtEnd: Long?
        val durationStr: String?
        if (rrule != null) {
            dtEnd = null
            durationStr = if (allDay) "P1D" else "PT${durationMin}M"
        } else {
            durationStr = null
            dtEnd =
                if (allDay) {
                    startLocal
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                } else {
                    startLocal
                        .plusMinutes(durationMin.toLong())
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
        }

        return SystemEventDraft(
            calendarId = calendarId,
            title = title,
            description = task.note,
            location = location,
            dtStartMillis = dtStart,
            dtEndMillis = dtEnd,
            durationRfc2445 = durationStr,
            allDay = allDay,
            rrule = rrule,
            remindMinutes = task.remindMinutes,
            customAppUri = TASK_URI_PREFIX + task.id,
        )
    }

    fun draftToSimulatedEvent(
        eventId: Long,
        draft: SystemEventDraft,
    ): SystemEvent =
        SystemEvent(
            eventId = eventId,
            calendarId = draft.calendarId,
            title = draft.title,
            description = draft.description,
            location = draft.location,
            dtStartMillis = draft.dtStartMillis,
            dtEndMillis = draft.dtEndMillis,
            durationRfc2445 = draft.durationRfc2445,
            allDay = draft.allDay,
            rrule = draft.rrule,
            remindMinutes = draft.remindMinutes,
            deleted = false,
            customAppUri = draft.customAppUri,
        )

    fun taskHash(
        task: Task,
        defaultBlockMinutes: Int = 30,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val draft = toDraft(task, 0L, defaultBlockMinutes, zone) ?: return "unscheduled:${task.id}"
        return canonicalSignature(
            title = draft.title,
            description = draft.description,
            location = draft.location,
            dtStart = draft.dtStartMillis,
            durationMin = resolveDurationMinutes(draft.dtStartMillis, draft.dtEndMillis, draft.durationRfc2445, draft.allDay, defaultBlockMinutes),
            allDay = draft.allDay,
            rrule = draft.rrule,
            remindMinutes = draft.remindMinutes,
        )
    }

    fun eventHash(
        event: SystemEvent,
        zone: ZoneId = ZoneId.systemDefault(),
        defaultBlockMinutes: Int = 30,
    ): String {
        if (event.deleted) return "deleted:${event.eventId}"
        // 往返归一化：通过 parseRecurrence -> formatRRule 消除外部日历对字段顺序的微差
        val startLocal = eventStartLocal(event, zone)
        val normRRule =
            event.rrule?.takeIf { it.isNotBlank() }?.let { raw ->
                IcsImporter.parseRecurrence(raw, startLocal, emptySet(), zone)?.let { formatRRule(it, zone) } ?: raw.trim().uppercase()
            }
        return canonicalSignature(
            title = event.title.trim(),
            description = event.description.trimEnd(),
            location = event.location.trim(),
            dtStart = normalizeStartMillis(event, zone),
            durationMin = resolveDurationMinutes(event.dtStartMillis, event.dtEndMillis, event.durationRfc2445, event.allDay, defaultBlockMinutes),
            allDay = event.allDay,
            rrule = normRRule,
            remindMinutes = event.remindMinutes,
        )
    }

    private fun draftHash(draft: SystemEventDraft): String =
        listOf(
            draft.title,
            draft.description,
            draft.location,
            draft.dtStartMillis.toString(),
            (draft.dtEndMillis ?: 0L).toString(),
            (draft.remindMinutes ?: -1).toString(),
        ).joinToString("|")

    private fun canonicalSignature(
        title: String,
        description: String,
        location: String,
        dtStart: Long,
        durationMin: Int,
        allDay: Boolean,
        rrule: String?,
        remindMinutes: Int?,
    ): String =
        listOf(
            title.trim(),
            description.trimEnd(),
            location.trim(),
            (dtStart / 60_000L).toString(),
            durationMin.toString(),
            if (allDay) "1" else "0",
            rrule.orEmpty(),
            (remindMinutes ?: -1).toString(),
        ).joinToString("|")

    private fun mergeLinksWithCustomUri(
        existing: List<CalendarTaskLink>,
        remotes: List<SystemEvent>,
        tasksById: Map<String, Task>,
        defaultBlockMinutes: Int,
        zone: ZoneId,
        nowMillis: Long,
    ): List<CalendarTaskLink> {
        val out = ArrayList<CalendarTaskLink>(existing)
        val knownTasks = existing.mapTo(HashSet()) { it.taskId }
        val knownEvents = existing.mapTo(HashSet()) { it.calendarId to it.eventId }
        for (r in remotes) {
            if (r.deleted || (r.calendarId to r.eventId) in knownEvents) continue
            val taskId = r.customAppUri?.takeIf { it.startsWith(TASK_URI_PREFIX) }?.removePrefix(TASK_URI_PREFIX) ?: continue
            if (taskId in knownTasks) continue
            val task = tasksById[taskId] ?: continue
            val tHash = taskHash(task, defaultBlockMinutes, zone)
            val eHash = eventHash(r, zone, defaultBlockMinutes)
            out += CalendarTaskLink(taskId, r.calendarId, r.eventId, tHash, eHash, nowMillis)
            knownTasks += taskId
            knownEvents += (r.calendarId to r.eventId)
        }
        return out
    }

    private fun toNewTask(
        remote: SystemEvent,
        defaultBlockMinutes: Int,
        zone: ZoneId,
        nowMillis: Long,
        ids: IdGenerator,
    ): Task {
        val base = Task(id = ids.next(), title = "", source = Source.IMPORT, updatedAt = nowMillis)
        return applyRemoteToTask(base, remote, defaultBlockMinutes, zone, nowMillis)
    }

    private fun applyRemoteToTask(
        existing: Task,
        remote: SystemEvent,
        defaultBlockMinutes: Int,
        zone: ZoneId,
        nowMillis: Long,
    ): Task {
        val rawTitle = remote.title.trim()
        val doneFromPrefix = rawTitle.startsWith(DONE_PREFIX)
        val cleanTitle = rawTitle.removePrefix(DONE_PREFIX).trim().ifEmpty { "(无标题)" }
        val done =
            if (doneFromPrefix) {
                true
            } else if (existing.done && !rawTitle.startsWith(DONE_PREFIX)) {
                false
            } else {
                existing.done
            }

        val startLocal = eventStartLocal(remote, zone)
        val durMin = resolveDurationMinutes(remote.dtStartMillis, remote.dtEndMillis, remote.durationRfc2445, remote.allDay, defaultBlockMinutes)
        val recurrence =
            remote.rrule
                ?.takeIf { it.isNotBlank() }
                ?.let { IcsImporter.parseRecurrence(it, startLocal, existing.repeat?.exDates.orEmpty(), zone) }

        val nextExtra = LinkedHashMap(existing.extra)
        if (remote.location.isNotBlank()) {
            nextExtra["location"] = remote.location.trim()
        } else {
            nextExtra.remove("location")
        }

        return if (remote.allDay) {
            val date = eventAllDayDate(remote.dtStartMillis)
            existing.copy(
                title = cleanTitle,
                note = remote.description,
                done = done,
                doneAt = if (done) (existing.doneAt ?: nowMillis) else null,
                blockStart = null,
                blockMinutes = null,
                due = date.atTime(23, 59),
                dueHasTime = false,
                remindMinutes = remote.remindMinutes,
                repeat = recurrence,
                extra = nextExtra,
            )
        } else {
            existing.copy(
                title = cleanTitle,
                note = remote.description,
                done = done,
                doneAt = if (done) (existing.doneAt ?: nowMillis) else null,
                blockStart = startLocal,
                blockMinutes = durMin,
                due = startLocal.plusMinutes(durMin.toLong()),
                dueHasTime = true,
                remindMinutes = remote.remindMinutes,
                repeat = recurrence,
                extra = nextExtra,
            )
        }
    }

    private fun eventAllDayDate(dtStartMillis: Long): LocalDate = Instant.ofEpochMilli(dtStartMillis).atZone(ZoneOffset.UTC).toLocalDate()

    private fun eventStartLocal(
        event: SystemEvent,
        zone: ZoneId,
    ): LocalDateTime =
        if (event.allDay) {
            eventAllDayDate(event.dtStartMillis).atStartOfDay()
        } else {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(event.dtStartMillis), zone).withSecond(0).withNano(0)
        }

    private fun normalizeStartMillis(
        event: SystemEvent,
        zone: ZoneId,
    ): Long =
        if (event.allDay) {
            eventAllDayDate(event.dtStartMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            eventStartLocal(event, zone).atZone(zone).toInstant().toEpochMilli()
        }

    internal fun resolveDurationMinutes(
        dtStartMillis: Long,
        dtEndMillis: Long?,
        durationRfc2445: String?,
        allDay: Boolean,
        defaultBlockMinutes: Int,
    ): Int {
        if (allDay) return 24 * 60
        if (!durationRfc2445.isNullOrBlank()) {
            parseRfc2445DurationMinutes(durationRfc2445)?.let { return it.coerceIn(1, 24 * 60) }
        }
        if (dtEndMillis != null && dtEndMillis > dtStartMillis) {
            return ((dtEndMillis - dtStartMillis) / 60_000L).toInt().coerceIn(1, 24 * 60)
        }
        return defaultBlockMinutes.coerceIn(1, 24 * 60)
    }

    internal fun parseRfc2445DurationMinutes(raw: String): Int? {
        val s = raw.trim().uppercase().removePrefix("+")
        if (!s.startsWith("P")) return null
        val weeks =
            Regex("(\\d+)W")
                .find(s)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val days =
            Regex("(\\d+)D")
                .find(s)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val timePart = s.substringAfter('T', "")
        val hours =
            Regex("(\\d+)H")
                .find(timePart)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val mins =
            Regex("(\\d+)M")
                .find(timePart)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val secs =
            Regex("(\\d+)S")
                .find(timePart)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val total = weeks * 7 * 1440 + days * 1440 + hours * 60 + mins + (secs / 60)
        return total.takeIf { it > 0 }
    }

    fun formatRRule(
        r: Recurrence,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val parts = mutableListOf("FREQ=${r.freq.name}")
        if (r.interval > 1) parts += "INTERVAL=${r.interval}"
        if (r.freq == Freq.WEEKLY && r.byDays.isNotEmpty()) {
            val names = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
            parts += "BYDAY=" + r.byDays.sorted().joinToString(",") { names[it - 1] }
        }
        r.until?.let {
            val endUtc = LocalDateTime.of(it, LocalTime.of(23, 59, 59)).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
            parts += "UNTIL=${endUtc.format(UTC_DT)}"
        }
        r.count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }
}
