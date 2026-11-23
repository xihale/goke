package app.goke.core.sync

import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.importer.IcsImporter
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Reminders
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.recurrence.CourseExpander
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
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
    val remindMinutesList: List<Int> = emptyList(),
) {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)
}

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
    val remindMinutesList: List<Int> = emptyList(),
) {
    val effectiveRemindMinutes: List<Int>
        get() = Reminders.normalize(remindMinutes, remindMinutesList)
}

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
 * Goke ⇄ Android 系统日历纯函数对账引擎：
 * 1. 支持日程块（blockStart）、带时刻待办（due+dueHasTime）与全天截止待办（allDay UTC 零点）双向互转；
 * 2. 基于双端内容指纹（taskHash / eventHash）精确识别单边修改，彻底阻断回声死循环（Self-Echo）；
 * 3. 用户取消勾选某个日历账本时只解除本地映射，绝不误删本地任务。
 */
object CalendarBridge {
    const val TASK_URI_PREFIX = "goke://task/"
    const val COURSE_URI_PREFIX = "goke://course/"
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
        ignoredRemoteKeys: Set<Pair<Long, Long>> = emptySet(),
        ignoredCourseDedupKeys: Set<String> = emptySet(),
    ): BidirectionalPlan {
        if (selectedCalendarIds.isEmpty()) {
            return BidirectionalPlan(null, emptyList(), emptyList(), emptyList(), emptyList())
        }

        val tasksById = snapshot.tasks.associateBy { it.id }
        val activeRemote =
            remoteEvents.filter { ev ->
                ev.calendarId in selectedCalendarIds &&
                    ev.customAppUri?.startsWith(COURSE_URI_PREFIX) != true &&
                    (ev.calendarId to ev.eventId) !in ignoredRemoteKeys &&
                    "${ev.calendarId}|${eventDedupKey(ev, zone)}" !in ignoredCourseDedupKeys
            }
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
                    val curEventHash = eventHash(liveRemote, zone, defaultBlockMinutes)
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

        // 1.5 内容去重匹配：两端都存在同标题+同时刻但未配对的事件（例如重装、云同步多端、厂商日历清除 customAppUri），
        // 直接建立映射而不是互推/互拉出双份重复
        val unlinkedRemotesByDedup = HashMap<String, MutableList<SystemEvent>>()
        val sortedUnlinkedRemotes =
            activeRemote
                .filter { !it.deleted && (it.calendarId to it.eventId) !in handledRemoteKeys }
                .sortedWith(compareBy({ if (it.calendarId == primaryCalendarId) 0 else 1 }, { it.calendarId }, { it.eventId }))
        for (remote in sortedUnlinkedRemotes) {
            unlinkedRemotesByDedup.getOrPut(eventDedupKey(remote, zone)) { mutableListOf() } += remote
        }
        for (task in snapshot.tasks) {
            if (task.id in handledTaskIds) continue
            if (task.deletedAt != null || task.dropped || !isSyncableTask(task)) continue
            val dKey = taskDedupKey(task, defaultBlockMinutes, zone) ?: continue
            val candidates = unlinkedRemotesByDedup[dKey] ?: continue
            val matched = candidates.firstOrNull { (it.calendarId to it.eventId) !in handledRemoteKeys } ?: continue
            candidates.remove(matched)
            handledTaskIds += task.id
            handledRemoteKeys += (matched.calendarId to matched.eventId)
            nextLinks +=
                CalendarTaskLink(
                    taskId = task.id,
                    calendarId = matched.calendarId,
                    eventId = matched.eventId,
                    taskHash = taskHash(task, defaultBlockMinutes, zone),
                    eventHash = eventHash(matched, zone, defaultBlockMinutes),
                    syncedAt = nowMillis,
                )
        }

        // 2. 本地新增的未配对日程任务 → 推向主同步日历（已完成的历史旧任务不主动首次推送，避免刷屏）
        val targetCalId = primaryCalendarId?.takeIf { it in selectedCalendarIds } ?: selectedCalendarIds.firstOrNull()
        val seenPushDedupKeys = HashSet<String>()
        if (targetCalId != null) {
            for (r in activeRemote) {
                if (!r.deleted && r.calendarId == targetCalId) {
                    seenPushDedupKeys += eventDedupKey(r, zone)
                }
            }
            for (tid in handledTaskIds) {
                tasksById[tid]?.let { t ->
                    taskDedupKey(t, defaultBlockMinutes, zone)?.let { seenPushDedupKeys += it }
                }
            }
            for (task in snapshot.tasks) {
                if (task.id in handledTaskIds) continue
                if (task.deletedAt != null || task.dropped || task.done || !isSyncableTask(task)) continue
                val dKey = taskDedupKey(task, defaultBlockMinutes, zone)
                if (dKey != null && !seenPushDedupKeys.add(dKey)) {
                    handledTaskIds += task.id
                    continue
                }
                val draft = toDraft(task, targetCalId, defaultBlockMinutes, zone) ?: continue
                val tHash = taskHash(task, defaultBlockMinutes, zone)
                remoteInserts += RemoteInsert(task.id, tHash, draft)
                handledTaskIds += task.id
            }
        }

        // 3. 系统日历新增的未配对事件 → 拉入 Goke（多账本含重复内容或本地已有同内容时只拉一份）
        val seenPullDedupKeys = HashSet<String>()
        for (tid in handledTaskIds) {
            tasksById[tid]?.let { t ->
                taskDedupKey(t, defaultBlockMinutes, zone)?.let { seenPullDedupKeys += it }
            }
        }
        for ((calId, evId) in handledRemoteKeys) {
            remoteByKey[calId to evId]?.let { seenPullDedupKeys += eventDedupKey(it, zone) }
        }
        for (remote in activeRemote) {
            val key = remote.calendarId to remote.eventId
            if (key in handledRemoteKeys || remote.deleted) continue
            // 若带 goke://task/ 标记但本地任务已被删除，则顺手清理远端残留而不是复活它
            val orphanTaskId = remote.customAppUri?.takeIf { it.startsWith(TASK_URI_PREFIX) }?.removePrefix(TASK_URI_PREFIX)
            if (orphanTaskId != null && tasksById[orphanTaskId]?.deletedAt != null) {
                remoteDeletes += RemoteDelete(remote.calendarId, remote.eventId)
                continue
            }
            val dKey = eventDedupKey(remote, zone)
            if (!seenPullDedupKeys.add(dKey)) {
                handledRemoteKeys += key
                continue
            }
            val created = toNewTask(remote, defaultBlockMinutes, zone, nowMillis, ids)
            localChanges += Change.Add(created)
            handledRemoteKeys += key
            nextLinks +=
                CalendarTaskLink(
                    taskId = created.id,
                    calendarId = remote.calendarId,
                    eventId = remote.eventId,
                    taskHash = taskHash(created, defaultBlockMinutes, zone),
                    eventHash = eventHash(remote, zone, defaultBlockMinutes),
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

    /** 列出已启用课程导出时应被双向任务同步忽略的内容指纹（防止厂商日历清除 customAppUri 后把课表事件回流为待办）。 */
    fun courseExportDedupKeys(
        snapshot: Snapshot,
        targetCalendarId: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<String> {
        if (targetCalendarId == null) return emptySet()
        val live = snapshot.live()
        val sem = live.currentSemester ?: return emptySet()
        val cal =
            app.goke.core.time
                .SemesterCalendar(sem)
        val occs = CourseExpander.occurrences(sem, live.courses, live.exceptions, cal.weekStart(1), cal.weekEnd(sem.totalWeeks))
        return occs.mapTo(HashSet()) { occ ->
            val startMs =
                occ.start
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            val titleKey =
                occ.course.name
                    .trim()
                    .lowercase()
            "$targetCalendarId|$titleKey|${startMs / 60_000L}|0"
        }
    }

    /** 将当前学期课程表（含停课与调课）差分导出到指定系统日历账本；若 enabled=false 则清理已导出的全部课程事件。 */
    fun planCourseExport(
        snapshot: Snapshot,
        enabled: Boolean,
        targetCalendarId: Long?,
        existingLinks: List<CourseExportLink>,
        remoteEvents: List<SystemEvent>,
        zone: ZoneId = ZoneId.systemDefault(),
        excludedRemoteKeys: Set<Pair<Long, Long>> = emptySet(),
    ): CourseExportPlan {
        val allLiveRemoteByKey =
            remoteEvents
                .filter { !it.deleted }
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
            app.goke.core.time
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
            val reminders = occ.course.effectiveRemindMinutes
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
                    remindMinutes = reminders.firstOrNull(),
                    customAppUri = COURSE_URI_PREFIX + key,
                    remindMinutesList = reminders,
                )
        }

        val inserts = mutableListOf<CourseInsert>()
        val updates = mutableListOf<CourseUpdate>()
        val deletes = mutableListOf<RemoteDelete>()
        val unchanged = mutableListOf<CourseExportLink>()

        val existingBySlot = existingLinks.associateBy { it.slotKey }
        val claimedKeys = HashSet<Pair<Long, Long>>()
        // 预先登记仍然有效的已有链接，避免后续内容匹配抢占已配对事件
        for ((slotKey, _) in desired) {
            val prev = existingBySlot[slotKey]
            if (prev != null && prev.calendarId == targetCalendarId && (prev.calendarId to prev.eventId) in allLiveRemoteByKey) {
                claimedKeys += (prev.calendarId to prev.eventId)
            }
        }
        val targetRemotes =
            remoteEvents.filter {
                !it.deleted && it.calendarId == targetCalendarId &&
                    (it.calendarId to it.eventId) !in excludedRemoteKeys &&
                    (it.calendarId to it.eventId) !in claimedKeys
            }
        val adoptableByUri = targetRemotes.filter { it.customAppUri != null }.associateBy { it.customAppUri!! }
        val adoptableByContent = HashMap<String, MutableList<SystemEvent>>()
        for (r in targetRemotes) {
            adoptableByContent.getOrPut(eventDedupKey(r, zone)) { mutableListOf() } += r
        }

        for ((slotKey, draft) in desired) {
            val hash = draftHash(draft)
            val prev = existingBySlot[slotKey]
            val remotePresent = prev != null && (prev.calendarId to prev.eventId) in allLiveRemoteByKey
            when {
                prev != null && prev.calendarId == targetCalendarId && remotePresent -> {
                    if (prev.slotHash != hash) {
                        updates += CourseUpdate(slotKey, prev.calendarId, prev.eventId, hash, draft)
                    } else {
                        unchanged += prev
                    }
                }

                else -> {
                    if (prev != null && prev.calendarId != targetCalendarId && remotePresent) {
                        deletes += RemoteDelete(prev.calendarId, prev.eventId)
                    }
                    val draftKey = "${draft.title.trim().lowercase()}|${draft.dtStartMillis / 60_000L}|0"
                    val adopted =
                        adoptableByUri[draft.customAppUri]?.takeIf { (it.calendarId to it.eventId) !in claimedKeys }
                            ?: adoptableByContent[draftKey]?.firstOrNull { (it.calendarId to it.eventId) !in claimedKeys }
                    if (adopted != null) {
                        claimedKeys += (adopted.calendarId to adopted.eventId)
                        if (eventMatchesCourseDraft(adopted, draft)) {
                            unchanged += CourseExportLink(slotKey, adopted.calendarId, adopted.eventId, hash)
                        } else {
                            updates += CourseUpdate(slotKey, adopted.calendarId, adopted.eventId, hash, draft)
                        }
                    } else {
                        inserts += CourseInsert(slotKey, hash, draft)
                    }
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

    private fun eventMatchesCourseDraft(
        event: SystemEvent,
        draft: SystemEventDraft,
    ): Boolean =
        event.title.trim() == draft.title.trim() &&
            event.description.trimEnd() == draft.description.trimEnd() &&
            event.location.trim() == draft.location.trim() &&
            event.dtStartMillis / 60_000L == draft.dtStartMillis / 60_000L &&
            (event.dtEndMillis ?: 0L) / 60_000L == (draft.dtEndMillis ?: 0L) / 60_000L &&
            event.effectiveRemindMinutes == draft.effectiveRemindMinutes

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

        val reminders = task.effectiveRemindMinutes
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
            remindMinutes = reminders.firstOrNull(),
            customAppUri = TASK_URI_PREFIX + task.id,
            remindMinutesList = reminders,
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
            remindMinutesList = draft.effectiveRemindMinutes,
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
            reminders = draft.effectiveRemindMinutes,
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
            reminders = event.effectiveRemindMinutes,
        )
    }

    private fun draftHash(draft: SystemEventDraft): String =
        listOf(
            draft.title,
            draft.description,
            draft.location,
            draft.dtStartMillis.toString(),
            (draft.dtEndMillis ?: 0L).toString(),
            draft.effectiveRemindMinutes.joinToString(","),
        ).joinToString("|")

    private fun canonicalSignature(
        title: String,
        description: String,
        location: String,
        dtStart: Long,
        durationMin: Int,
        allDay: Boolean,
        rrule: String?,
        reminders: List<Int>,
    ): String =
        listOf(
            title.trim(),
            description.trimEnd(),
            location.trim(),
            (dtStart / 60_000L).toString(),
            durationMin.toString(),
            if (allDay) "1" else "0",
            rrule.orEmpty(),
            reminders.joinToString(","),
        ).joinToString("|")

    private fun taskDedupKey(
        task: Task,
        defaultBlockMinutes: Int,
        zone: ZoneId,
    ): String? {
        val draft = toDraft(task, 0L, defaultBlockMinutes, zone) ?: return null
        val cleanTitle =
            task.title
                .trim()
                .removePrefix(DONE_PREFIX)
                .trim()
                .lowercase()
        return if (draft.allDay) {
            val dateStr = eventAllDayDate(draft.dtStartMillis).toString()
            "$cleanTitle|$dateStr|1"
        } else {
            "$cleanTitle|${draft.dtStartMillis / 60_000L}|0"
        }
    }

    private fun eventDedupKey(
        event: SystemEvent,
        zone: ZoneId,
    ): String {
        val cleanTitle =
            event.title
                .trim()
                .removePrefix(DONE_PREFIX)
                .trim()
                .lowercase()
        return if (event.allDay) {
            val dateStr = eventAllDayDate(event.dtStartMillis).toString()
            "$cleanTitle|$dateStr|1"
        } else {
            "$cleanTitle|${normalizeStartMillis(event, zone) / 60_000L}|0"
        }
    }

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

        val reminders = remote.effectiveRemindMinutes
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
                remindMinutes = reminders.firstOrNull(),
                remindMinutesList = reminders,
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
                remindMinutes = reminders.firstOrNull(),
                remindMinutesList = reminders,
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
        if (r.freq == Freq.YEARLY && r.byMonths.isNotEmpty()) parts += "BYMONTH=" + r.byMonths.sorted().joinToString(",")
        if ((r.freq == Freq.MONTHLY || r.freq == Freq.YEARLY) && r.byDays.isNotEmpty()) {
            parts += "BYMONTHDAY=" + r.byDays.sorted().joinToString(",")
        }
        r.until?.let {
            val endUtc = LocalDateTime.of(it, LocalTime.of(23, 59, 59)).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
            parts += "UNTIL=${endUtc.format(UTC_DT)}"
        }
        r.count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }
}
