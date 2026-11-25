package app.goke.core.campus

import app.goke.core.change.ChangeSet
import app.goke.core.jxfw.ClassroomBuilding
import app.goke.core.jxfw.ClassroomOccupancy
import app.goke.core.jxfw.ClassroomRoom
import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.JxfwPlan
import app.goke.core.jxfw.TzcsYearReport
import app.goke.core.jxfw.XkActionResult
import app.goke.core.jxfw.XkCourseDetail
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.model.Snapshot
import app.goke.hotwater.HotWaterApiResult
import app.goke.hotwater.HotWaterOpResult
import app.goke.hotwater.HotWaterOrderPage
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.hotwater.HotWaterState
import app.goke.hotwater.HotWaterTaskCopy
import app.goke.hotwater.HotWaterVault
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.jxfw.JxfwGradesState
import app.goke.jxfw.JxfwState
import app.goke.jxfw.TzcsState
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** 校园适配器凭据存储契约（真机由 SecretStore 实现）。 */
interface CampusVault : HotWaterVault {
    fun jxfwPassword(): String?

    fun setJxfwPassword(plain: String)

    fun tzcsPassword(): String?

    fun setTzcsPassword(plain: String)

    fun tzcsCookies(): String?

    fun setTzcsCookies(plain: String)
}

/** 仓库访问契约，避免适配器模块直接依赖 app 模块实现类。 */
interface CampusRepoPort {
    val snapshot: StateFlow<Snapshot>

    suspend fun awaitReady()

    suspend fun loadIfEmpty()

    suspend fun ensureDefaultSemester()

    suspend fun commit(changeSet: ChangeSet)
}

/** 教务同步文案回调（由宿主从 strings.xml 注入）。 */
interface JxfwStrings {
    val progressLogin: String
    val progressSaving: String
    val resultSemesterOver: String
    val notifyTitle: String
    val errNoCredentials: String
    val errNetwork: String
    val errUnsupported: String

    fun progressCourses(
        done: Int,
        total: Int,
    ): String

    fun progressRefineWeeks(
        done: Int,
        total: Int,
    ): String

    fun progressWeek(week: Int): String

    fun resultFull(
        courses: Int,
        exams: Int,
    ): String

    fun resultNoChange(week: Int): String

    fun resultChanged(
        week: Int,
        changes: Int,
    ): String
}

/** 体测同步文案回调（由宿主从 strings.xml 注入）。 */
interface TzcsStrings {
    val errUnknown: String
    val errNetwork: String
    val sessionExpired: String
    val errUnsupported: String
}

/** 宿主向校园/热水适配器提供的运行时端口。 */
interface CampusHost {
    val vault: CampusVault
    val repo: CampusRepoPort
    val jxfwStrings: JxfwStrings
    val tzcsStrings: TzcsStrings

    fun recordError(
        tag: String,
        throwable: Throwable,
    )

    fun postWeeklyJxfwNotification(
        title: String,
        body: String,
    )

    fun updateWeeklyJxfwAlarm(enabled: Boolean)

    fun followHotWaterArrearsWorker(enabled: Boolean)

    fun hotWaterTaskCopy(arrearsYuan: Double): HotWaterTaskCopy

    fun onHotWaterWidgetRefresh()

    fun onHotWaterShowerCompleted(record: HotWaterShowerRecord)
}

/** 教务 / 空教室 / 选课 UI 门面接口：开源构建回退为桩实现，完整协议实现位于私有模块。 */
interface JxfwAdapter {
    val isSupported: Boolean
    val portalHost: String
    val state: StateFlow<JxfwState>
    val gradesState: StateFlow<JxfwGradesState>
    val xkState: StateFlow<JxfwCourseSelectionState>

    fun hydrate()

    fun hydrateGrades()

    fun ensureAlarm()

    suspend fun catchUp()

    fun currentTerm(): String

    fun availableTerms(syncedTerm: String? = state.value.term): List<String>

    suspend fun login(
        username: String,
        password: String,
        termCode: String? = null,
    ): JxfwPlan

    suspend fun logout()

    fun setAutoSync(on: Boolean)

    suspend fun fullSync(termCode: String? = null): JxfwPlan

    suspend fun weeklySync(auto: Boolean = false): JxfwPlan?

    suspend fun refreshGrades(termCode: String = ""): Result<List<JxfwGrade>>

    suspend fun loadCourseSelection(
        searchKey: String = "",
        searchValue: String = "",
        page: Int = 1,
    ): Result<Unit>

    suspend fun loadCourseDetail(item: XkCourseItem): Result<XkCourseDetail>

    suspend fun enrollCourse(item: XkCourseItem): Result<XkActionResult>

    suspend fun dropCourse(item: XkCourseItem): Result<XkActionResult>

    suspend fun fetchClassroomBuildings(forceRefresh: Boolean = false): List<ClassroomBuilding>

    suspend fun fetchClassroomRooms(
        buildingCode: String,
        forceRefresh: Boolean = false,
    ): List<ClassroomRoom>

    suspend fun fetchClassroomWeekOccupancy(
        buildingCode: String,
        weekStart: LocalDate,
        forceRefresh: Boolean = false,
    ): Map<LocalDate, List<ClassroomOccupancy>>

    fun seedStateForTest(
        state: JxfwState,
        buildings: List<ClassroomBuilding> = emptyList(),
        roomsByBuilding: Map<String, List<ClassroomRoom>> = emptyMap(),
        weekOccupancy: Map<Pair<String, LocalDate>, Map<LocalDate, List<ClassroomOccupancy>>> = emptyMap(),
        gradesState: JxfwGradesState? = null,
    )
}

/** 体测查询 UI 门面接口：开源构建回退为桩实现，完整协议实现位于私有模块。 */
interface TzcsAdapter {
    val isSupported: Boolean
    val state: StateFlow<TzcsState>

    fun hydrate()

    fun savedPassword(): String?

    suspend fun loadCaptcha(): Result<ByteArray>

    suspend fun loginAndFetch(
        username: String,
        password: String,
        vercode: String,
    ): Result<List<TzcsYearReport>>

    suspend fun refreshIfSessionActive(): Result<List<TzcsYearReport>>

    suspend fun logout()

    fun seedStateForTest(state: TzcsState)
}

/** 智慧热水 UI 与小组件门面接口：开源构建回退为桩实现，完整协议实现位于私有模块。 */
interface HotWaterAdapter {
    val isSupported: Boolean
    val state: StateFlow<HotWaterState>

    fun hydrate()

    suspend fun ensureHydrated()

    suspend fun sendSmsCode(phone: String): HotWaterOpResult

    suspend fun loginWithPassword(
        phone: String,
        password: String,
    ): HotWaterOpResult

    suspend fun loginWithSms(
        phone: String,
        code: String,
    ): HotWaterOpResult

    suspend fun bindWithUserId(
        userId: String,
        uuid: String = "",
    ): HotWaterOpResult

    fun unbind()

    fun updateArrearsConfig(
        enabled: Boolean,
        thresholdYuan: Int,
    )

    fun updateShowerNotifyEnabled(enabled: Boolean)

    fun cachedShowerRecords(): List<HotWaterShowerRecord>

    suspend fun refreshStatusAndCheckArrears(probeBle: Boolean = false): HotWaterOpResult

    suspend fun fetchOrders(
        all: Boolean,
        pageIndex: Int = 1,
    ): HotWaterApiResult<HotWaterOrderPage>

    suspend fun toggleValve(): HotWaterOpResult
}

/** 私有适配器工厂契约（由私有模块通过固定类名实现，开源构建缺失时自动回退至桩实现）。 */
interface CampusAdapterFactory {
    fun createJxfw(
        context: Any,
        host: CampusHost,
    ): JxfwAdapter

    fun createTzcs(
        context: Any,
        host: CampusHost,
    ): TzcsAdapter

    fun createHotWater(
        context: Any,
        host: CampusHost,
    ): HotWaterAdapter
}
