package app.goke.core.export

import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 结构化课表分享载荷（`goke.timetable.v1`），不含任何账号/凭证字段。 */
@Serializable
data class SharedTimetablePayload(
    val schema: String = SCHEMA_V1,
    val code: String? = null,
    val name: String,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val semester: SharedSemester,
    val courses: List<SharedCourse>,
    val exceptions: List<SharedCourseException> = emptyList(),
) {
    fun toJson(): String = SharedTimetable.toJson(this)

    companion object {
        const val SCHEMA_V1 = "goke.timetable.v1"
    }
}

@Serializable
data class SharedSemester(
    val name: String,
    val week1Monday: String,
    val totalWeeks: Int,
    val periods: List<SharedPeriodTime>,
)

@Serializable
data class SharedPeriodTime(
    val index: Int,
    val start: String,
    val end: String,
)

@Serializable
data class SharedCourse(
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val colorIndex: Int,
    val kind: String,
    val note: String,
)

@Serializable
data class SharedCourseException(
    val courseIndex: Int,
    val week: Int,
    val type: String,
    val newWeek: Int? = null,
    val newDayOfWeek: Int? = null,
    val newStartPeriod: Int? = null,
    val newEndPeriod: Int? = null,
    val newRoom: String? = null,
)

object SharedTimetable {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun fromSnapshot(
        snap: Snapshot,
        displayName: String,
        updatedAt: Long = System.currentTimeMillis(),
        expiresAt: Long? = null,
        code: String? = null,
    ): SharedTimetablePayload {
        val live = snap.live()
        val sem = live.currentSemester ?: error("no_current_semester")
        val liveCourses = live.courses.filter { it.semesterId == sem.id && it.deletedAt == null }
        val courseIndexById = liveCourses.withIndex().associate { (idx, c) -> c.id to idx }
        val liveExceptions =
            live.exceptions.mapNotNull { ex ->
                if (ex.deletedAt != null) return@mapNotNull null
                val idx = courseIndexById[ex.courseId] ?: return@mapNotNull null
                SharedCourseException(
                    courseIndex = idx,
                    week = ex.week,
                    type = ex.type.name,
                    newWeek = ex.newWeek,
                    newDayOfWeek = ex.newDayOfWeek,
                    newStartPeriod = ex.newStartPeriod,
                    newEndPeriod = ex.newEndPeriod,
                    newRoom = ex.newRoom,
                )
            }
        return SharedTimetablePayload(
            schema = SharedTimetablePayload.SCHEMA_V1,
            code = code,
            name = displayName,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
            semester =
                SharedSemester(
                    name = sem.name,
                    week1Monday = sem.week1Monday.toString(),
                    totalWeeks = sem.totalWeeks,
                    periods =
                        sem.periods
                            .sortedBy { it.index }
                            .map { SharedPeriodTime(it.index, it.start.toString(), it.end.toString()) },
                ),
            courses =
                liveCourses.map { c ->
                    SharedCourse(
                        name = c.name,
                        teacher = c.teacher,
                        room = c.room,
                        dayOfWeek = c.dayOfWeek,
                        startPeriod = c.startPeriod,
                        endPeriod = c.endPeriod,
                        weeks = c.weeks.sorted(),
                        colorIndex = c.colorIndex,
                        kind = c.kind.name,
                        note = c.note,
                    )
                },
            exceptions = liveExceptions,
        )
    }

    fun toJson(payload: SharedTimetablePayload): String = json.encodeToString(payload)

    fun fromJson(raw: String): SharedTimetablePayload = json.decodeFromString(raw)
}

data class ShareExpiryPreset(
    val key: String,
    val date: LocalDate,
    val expiresAt: Long,
)

/** 课表分享有效期上限与预设计算。 */
object ShareExpiry {
    const val DEFAULT_MAX_DAYS: Long = 210L

    fun semesterEnd(sem: Semester): LocalDate = sem.week1Monday.plusWeeks(sem.totalWeeks.toLong())

    fun nextSemesterStart(sem: Semester): LocalDate {
        val semEnd = semesterEnd(sem)
        val gapWeeks = if (sem.week1Monday.monthValue in 8..12) 6L else 8L
        return semEnd.plusWeeks(gapWeeks)
    }

    fun maxDate(
        sem: Semester,
        today: LocalDate = LocalDate.now(),
        maxDays: Long = DEFAULT_MAX_DAYS,
    ): LocalDate {
        val cap = today.plusDays(maxDays)
        val minAllowed = today.plusDays(1)
        return minOf(nextSemesterStart(sem), cap).coerceAtLeast(minAllowed).coerceAtMost(cap)
    }

    fun endOfDayMillis(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long =
        date
            .atTime(LocalTime.of(23, 59, 59, 999_000_000))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    fun maxMillis(
        sem: Semester,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        maxDays: Long = DEFAULT_MAX_DAYS,
    ): Long = endOfDayMillis(maxDate(sem, today, maxDays), zone)

    fun presets(
        sem: Semester,
        today: LocalDate = LocalDate.now(),
        maxDays: Long = DEFAULT_MAX_DAYS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ShareExpiryPreset> {
        val minDate = today.plusDays(1)
        val max = maxDate(sem, today, maxDays)
        val candidates =
            listOf(
                "1d" to today.plusDays(1),
                "7d" to today.plusDays(7),
                "30d" to today.plusDays(30),
                "semesterEnd" to semesterEnd(sem),
                "nextSemesterStart" to nextSemesterStart(sem),
            )
        val seen = HashSet<LocalDate>()
        val out = mutableListOf<ShareExpiryPreset>()
        for ((key, rawDate) in candidates) {
            val clamped = rawDate.coerceIn(minDate, max)
            if (seen.add(clamped)) {
                out += ShareExpiryPreset(key = key, date = clamped, expiresAt = endOfDayMillis(clamped, zone))
            }
        }
        return out
    }
}
