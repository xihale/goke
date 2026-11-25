package app.goke.core.jxfw

import kotlinx.serialization.Serializable

/** 教学楼（空教室查询 UI 模型）。 */
@Serializable
data class ClassroomBuilding(
    val code: String,
    val number: String = "",
    val name: String,
    val campusCode: String = "",
    val campusName: String = "",
)

/** 教室（空教室查询 UI 模型）。 */
@Serializable
data class ClassroomRoom(
    val code: String,
    val name: String,
    val capacity: Int? = null,
)

/** 教室占用类型。 */
@Serializable
enum class OccupancyType {
    COURSE,
    BORROWED,
    LOCKED,
    EXAM,
    OTHER,
}

/** 教室占用记录（空教室查询 UI 模型）。 */
@Serializable
data class ClassroomOccupancy(
    val id: String,
    val roomCode: String,
    val roomName: String = "",
    val periods: List<Int>,
    val periodCode: String = "",
    val courseName: String = "",
    val teacher: String = "",
    val className: String = "",
    val note: String = "",
    val dayOfWeek: Int? = null,
    val week: Int? = null,
    val type: OccupancyType = OccupancyType.OTHER,
    val count: Int? = null,
)
