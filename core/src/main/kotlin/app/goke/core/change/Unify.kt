package app.goke.core.change

import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import java.time.Duration
import java.time.LocalTime

/**
 * 「日程」并进「待办」：一条日程就是一条带时间的待办。
 * 有时间的把时间落到 blockStart（日历/课表上占一段），截止取结束时刻；
 * 全天日程退化成只有日期的待办。
 */
object Unify {
    const val MIN_BLOCK = 15

    fun eventToTask(
        e: Event,
        id: String,
        sortOrder: Int,
    ): Task {
        val timed = !e.allDay
        val location = e.location.trim()
        val minutes =
            Duration
                .between(e.start, e.end)
                .toMinutes()
                .toInt()
                .coerceIn(MIN_BLOCK, 24 * 60)
        val tags =
            buildList {
                when (e.kind) {
                    EventKind.EXAM -> add("考试")
                    EventKind.COUNTDOWN -> add("倒数日")
                    EventKind.NORMAL -> Unit
                }
                val cat = e.category.trim()
                if (cat.isNotEmpty() && cat !in this) add(cat)
            }
        val extra =
            buildMap {
                if (e.kind == EventKind.EXAM || e.kind == EventKind.COUNTDOWN) {
                    put("kind", e.kind.name)
                    put("checkable", "false")
                }
                if (location.isNotEmpty()) put("location", location)
            }
        return Task(
            id = id,
            title = e.title,
            note = e.note.trim(),
            tags = tags,
            due = if (timed) e.end else e.start.toLocalDate().atTime(LocalTime.of(23, 59)),
            dueHasTime = timed,
            blockStart = if (timed) e.start else null,
            blockMinutes = if (timed) minutes else null,
            remindMinutes = e.effectiveRemindMinutes.firstOrNull(),
            remindMinutesList = e.effectiveRemindMinutes,
            repeat = e.recurrence?.copy(),
            sortOrder = sortOrder,
            source = e.source,
            extra = extra,
        )
    }

    /** 把快照里所有未删除的日程转成待办；没有日程时返回 null。 */
    fun changeSet(
        s: Snapshot,
        now: Long,
        ids: IdGenerator = Ids,
    ): ChangeSet? {
        val events = s.events.filter { it.deletedAt == null }
        if (events.isEmpty()) return null
        var order = s.tasks.maxOfOrNull { it.sortOrder } ?: 0
        val changes = mutableListOf<Change>()
        events.forEach { e ->
            order += 1
            changes += Change.Add(eventToTask(e, ids.next(), order))
            changes += Change.Remove(e)
        }
        return ChangeSet(ids.next(), "日程并进待办", Source.MANUAL, now, changes)
    }
}
