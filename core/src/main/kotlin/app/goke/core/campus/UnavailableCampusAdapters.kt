package app.goke.core.campus

import app.goke.core.jxfw.ClassroomBuilding
import app.goke.core.jxfw.ClassroomOccupancy
import app.goke.core.jxfw.ClassroomRoom
import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.JxfwNoticeSource
import app.goke.core.jxfw.JxfwParse
import app.goke.core.jxfw.JxfwPlan
import app.goke.core.jxfw.TzcsYearReport
import app.goke.core.jxfw.XkActionResult
import app.goke.core.jxfw.XkCourseDetail
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.jxfw.XkFailureKind
import app.goke.hotwater.HotWaterApiResult
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterOpResult
import app.goke.hotwater.HotWaterOrderPage
import app.goke.hotwater.HotWaterPhase
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.hotwater.HotWaterState
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.jxfw.JxfwException
import app.goke.jxfw.JxfwGradesState
import app.goke.jxfw.JxfwNoticesState
import app.goke.jxfw.JxfwState
import app.goke.jxfw.TzcsException
import app.goke.jxfw.TzcsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** 开源构建回退工厂：不包含私有协议实现，仅保留 UI 预览与测试注入能力。 */
object UnavailableCampusAdapterFactory : CampusAdapterFactory {
    override fun createJxfw(
        context: Any,
        host: CampusHost,
    ): JxfwAdapter = UnavailableJxfwAdapter(host)

    override fun createTzcs(
        context: Any,
        host: CampusHost,
    ): TzcsAdapter = UnavailableTzcsAdapter(host)

    override fun createHotWater(
        context: Any,
        host: CampusHost,
    ): HotWaterAdapter = UnavailableHotWaterAdapter()
}

private class UnavailableJxfwAdapter(
    private val host: CampusHost,
) : JxfwAdapter {
    override val isSupported: Boolean = false
    override val portalHost: String = ""

    private val _state = MutableStateFlow(JxfwState())
    private val _gradesState = MutableStateFlow(JxfwGradesState())
    private val _noticesState = MutableStateFlow(JxfwNoticesState())
    private val _xkState = MutableStateFlow(JxfwCourseSelectionState())

    private var cachedBuildings: List<ClassroomBuilding> = emptyList()
    private val cachedRooms = mutableMapOf<String, List<ClassroomRoom>>()
    private val cachedWeekOccupancy = mutableMapOf<Pair<String, LocalDate>, Map<LocalDate, List<ClassroomOccupancy>>>()

    override val state: StateFlow<JxfwState> = _state.asStateFlow()
    override val gradesState: StateFlow<JxfwGradesState> = _gradesState.asStateFlow()
    override val noticesState: StateFlow<JxfwNoticesState> = _noticesState.asStateFlow()
    override val xkState: StateFlow<JxfwCourseSelectionState> = _xkState.asStateFlow()

    override fun hydrate() = Unit

    override fun hydrateGrades() = Unit

    override fun hydrateNotices() = Unit

    override fun ensureAlarm() = Unit

    override suspend fun catchUp() = Unit

    override fun currentTerm(): String = JxfwParse.currentTerm(LocalDate.now())

    override fun availableTerms(syncedTerm: String?): List<String> {
        val cur = currentTerm()
        val year = cur.take(4).toIntOrNull()
        val sem = cur.drop(4).toIntOrNull()
        val neighbors =
            if (year != null && (sem == 1 || sem == 2)) {
                val prev = if (sem == 2) "${year}01" else "${year - 1}02"
                val next = if (sem == 1) "${year}02" else "${year + 1}01"
                listOf(prev, cur, next)
            } else {
                listOf(cur)
            }
        return (listOfNotNull(syncedTerm?.takeIf { it.isNotBlank() }) + neighbors).distinct().sorted()
    }

    private fun unsupported(): JxfwException = JxfwException(host.jxfwStrings.errUnsupported, JxfwException.Kind.UNSUPPORTED)

    override suspend fun login(
        username: String,
        password: String,
        termCode: String?,
    ): JxfwPlan {
        val err = unsupported()
        _state.update { it.copy(busy = false, progress = null, lastError = err.message, lastErrorKind = err.kind) }
        throw err
    }

    override suspend fun logout() {
        _state.value = JxfwState()
        _gradesState.value = JxfwGradesState()
        _noticesState.value = JxfwNoticesState()
        _xkState.value = JxfwCourseSelectionState()
        cachedBuildings = emptyList()
        cachedRooms.clear()
        cachedWeekOccupancy.clear()
    }

    override fun setAutoSync(on: Boolean) {
        _state.update { it.copy(autoSync = on) }
    }

    override fun setNoticeEnabled(on: Boolean) {
        _noticesState.update { it.copy(enabled = on) }
    }

    override fun setNoticePublicEnabled(on: Boolean) {
        _noticesState.update { cur ->
            val nextUnread =
                if (on) {
                    cur.unreadIds
                } else {
                    val publicIds = cur.notices.filter { it.source == JxfwNoticeSource.PUBLIC }.mapTo(HashSet()) { it.id }
                    cur.unreadIds - publicIds
                }
            cur.copy(publicEnabled = on, unreadIds = nextUnread)
        }
    }

    override fun setNoticeInternalEnabled(on: Boolean) {
        _noticesState.update { cur ->
            val nextUnread =
                if (on) {
                    cur.unreadIds
                } else {
                    val internalIds = cur.notices.filter { it.source == JxfwNoticeSource.INTERNAL }.mapTo(HashSet()) { it.id }
                    cur.unreadIds - internalIds
                }
            cur.copy(internalEnabled = on, unreadIds = nextUnread)
        }
    }

    override suspend fun fullSync(termCode: String?): JxfwPlan = throw unsupported()

    override suspend fun weeklySync(auto: Boolean): JxfwPlan? = null

    override suspend fun refreshGrades(termCode: String): Result<List<JxfwGrade>> {
        val err = unsupported()
        _gradesState.update { it.copy(busy = false, lastError = err.message, lastErrorKind = err.kind) }
        return Result.failure(err)
    }

    override suspend fun refreshNotices(notify: Boolean): Result<List<JxfwNotice>> {
        val err = unsupported()
        _noticesState.update { it.copy(busy = false, lastError = err.message, lastErrorKind = err.kind) }
        return Result.failure(err)
    }

    override fun markNoticeRead(id: String) {
        _noticesState.update { it.copy(unreadIds = it.unreadIds - id) }
    }

    override fun markAllNoticesRead() {
        _noticesState.update { it.copy(unreadIds = emptySet()) }
    }

    override suspend fun loadCourseSelection(
        searchKey: String,
        searchValue: String,
        page: Int,
    ): Result<Unit> {
        val err = unsupported()
        _xkState.update { it.copy(loading = false, loadingMore = false, lastError = err.message, lastErrorKind = err.kind) }
        return Result.failure(err)
    }

    override suspend fun loadCourseDetail(item: XkCourseItem): Result<XkCourseDetail> = Result.failure(unsupported())

    override suspend fun enrollCourse(item: XkCourseItem): Result<XkActionResult> = Result.success(XkActionResult.Failure(reason = host.jxfwStrings.errUnsupported, kind = XkFailureKind.OTHER))

    override suspend fun dropCourse(item: XkCourseItem): Result<XkActionResult> = Result.success(XkActionResult.Failure(reason = host.jxfwStrings.errUnsupported, kind = XkFailureKind.OTHER))

    override suspend fun fetchClassroomBuildings(forceRefresh: Boolean): List<ClassroomBuilding> {
        if (!forceRefresh && cachedBuildings.isNotEmpty()) return cachedBuildings
        throw unsupported()
    }

    override suspend fun fetchClassroomRooms(
        buildingCode: String,
        forceRefresh: Boolean,
    ): List<ClassroomRoom> {
        if (!forceRefresh) cachedRooms[buildingCode]?.let { return it }
        throw unsupported()
    }

    override suspend fun fetchClassroomWeekOccupancy(
        buildingCode: String,
        weekStart: LocalDate,
        forceRefresh: Boolean,
    ): Map<LocalDate, List<ClassroomOccupancy>> {
        if (!forceRefresh) cachedWeekOccupancy[buildingCode to weekStart]?.let { return it }
        throw unsupported()
    }

    override fun seedStateForTest(
        state: JxfwState,
        buildings: List<ClassroomBuilding>,
        roomsByBuilding: Map<String, List<ClassroomRoom>>,
        weekOccupancy: Map<Pair<String, LocalDate>, Map<LocalDate, List<ClassroomOccupancy>>>,
        gradesState: JxfwGradesState?,
        noticesState: JxfwNoticesState?,
    ) {
        _state.value = state
        cachedBuildings = buildings
        cachedRooms.clear()
        cachedRooms.putAll(roomsByBuilding)
        cachedWeekOccupancy.clear()
        cachedWeekOccupancy.putAll(weekOccupancy)
        if (gradesState != null) {
            _gradesState.value = gradesState
        }
        if (noticesState != null) {
            _noticesState.value = noticesState
        }
    }
}

private class UnavailableTzcsAdapter(
    private val host: CampusHost,
) : TzcsAdapter {
    override val isSupported: Boolean = false
    private val _state = MutableStateFlow(TzcsState())
    override val state: StateFlow<TzcsState> = _state.asStateFlow()

    override fun hydrate() = Unit

    override fun savedPassword(): String? = null

    private fun unsupported(): TzcsException = TzcsException(host.tzcsStrings.errUnsupported, TzcsException.Kind.UNSUPPORTED)

    override suspend fun loadCaptcha(): Result<ByteArray> = Result.failure(unsupported())

    override suspend fun loginAndFetch(
        username: String,
        password: String,
        vercode: String,
    ): Result<List<TzcsYearReport>> {
        val err = unsupported()
        _state.update { it.copy(busy = false, lastError = err.message, lastErrorKind = err.kind) }
        return Result.failure(err)
    }

    override suspend fun refreshIfSessionActive(): Result<List<TzcsYearReport>> = Result.failure(unsupported())

    override suspend fun logout() {
        _state.value = TzcsState()
    }

    override fun seedStateForTest(state: TzcsState) {
        _state.value = state
    }
}

private class UnavailableHotWaterAdapter : HotWaterAdapter {
    override val isSupported: Boolean = false
    private val _state = MutableStateFlow(HotWaterState())
    override val state: StateFlow<HotWaterState> = _state.asStateFlow()

    override fun hydrate() = Unit

    override suspend fun ensureHydrated() = Unit

    private fun unsupportedOp(): HotWaterOpResult =
        HotWaterOpResult(
            ok = false,
            phase = _state.value.phase,
            errorCode = HotWaterErrorCode.UNSUPPORTED,
        )

    override suspend fun sendSmsCode(phone: String): HotWaterOpResult = unsupportedOp()

    override suspend fun loginWithPassword(
        phone: String,
        password: String,
    ): HotWaterOpResult = unsupportedOp()

    override suspend fun loginWithSms(
        phone: String,
        code: String,
    ): HotWaterOpResult = unsupportedOp()

    override suspend fun bindWithUserId(
        userId: String,
        uuid: String,
    ): HotWaterOpResult = unsupportedOp()

    override fun unbind() {
        _state.value = HotWaterState(phase = HotWaterPhase.UNBOUND)
    }

    override fun updateArrearsConfig(
        enabled: Boolean,
        thresholdYuan: Int,
    ) = Unit

    override fun updateShowerNotifyEnabled(enabled: Boolean) = Unit

    override fun cachedShowerRecords(): List<HotWaterShowerRecord> = emptyList()

    override suspend fun refreshStatusAndCheckArrears(probeBle: Boolean): HotWaterOpResult = unsupportedOp()

    override suspend fun fetchOrders(
        all: Boolean,
        pageIndex: Int,
    ): HotWaterApiResult<HotWaterOrderPage> = HotWaterApiResult.Err(HotWaterErrorCode.UNSUPPORTED)

    override suspend fun toggleValve(): HotWaterOpResult = unsupportedOp()
}
