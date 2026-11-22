package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mintcal.R
import app.mintcal.core.agenda.Agenda
import app.mintcal.core.change.repeatInstances
import app.mintcal.core.jxfw.ClassroomBuilding
import app.mintcal.core.jxfw.ClassroomOccupancy
import app.mintcal.core.jxfw.ClassroomRoom
import app.mintcal.core.jxfw.JwcwxParse
import app.mintcal.core.jxfw.OccupancyType
import app.mintcal.core.model.GdutCampus
import app.mintcal.core.model.GdutPeriods
import app.mintcal.core.model.PeriodTime
import app.mintcal.core.model.Snapshot
import app.mintcal.core.recurrence.CourseExpander
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.jxfw.JxfwException
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.components.parcelCode
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.rememberToday
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate

internal enum class PeriodGroup(
    val code: String,
    val periods: IntRange,
) {
    P0102("0102", 1..2),
    P0304("0304", 3..4),
    P05("05", 5..5),
    P0607("0607", 6..7),
    P0809("0809", 8..9),
    P101112("101112", 10..12),
}

internal data class ClassroomBoxSelection(
    val date: String,
    val start: String,
    val end: String,
    val extraDates: String?,
    val location: String,
    val overlapsOccupancy: Boolean,
    val overlapsPersonal: Boolean,
)

internal data class PersonalOverlayItem(
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val title: String,
)

internal object ClassroomGridHelper {
    private val BoundariesAbove8 = listOf(4, 8)
    private val BoundariesAbove4 = listOf(4)

    fun groupBoundaries(periodCount: Int): List<Int> =
        when {
            periodCount > 8 -> BoundariesAbove8
            periodCount > 4 -> BoundariesAbove4
            else -> emptyList()
        }

    fun calcPeriodTop(
        p: Int,
        periodCount: Int,
        rowH: Dp,
        groupGap: Dp,
    ): Dp {
        var gaps = 0
        if (periodCount > 4 && p > 4) gaps++
        if (periodCount > 8 && p > 8) gaps++
        return rowH * (p - 1) + groupGap * gaps
    }

    fun defaultCampusName(
        buildings: List<ClassroomBuilding>,
        semesterCampus: GdutCampus,
    ): String? {
        if (buildings.isEmpty()) return null
        val matched =
            buildings.firstOrNull { b ->
                b.campusName.isNotBlank() &&
                    JwcwxParse.resolveCampus(b.campusCode, semesterCampus) == semesterCampus
            }
        return matched?.campusName ?: buildings.firstOrNull { it.campusName.isNotBlank() }?.campusName
    }

    fun isRoomFree(
        occupancies: List<ClassroomOccupancy>,
        roomCode: String,
        targetPeriods: Set<Int>,
    ): Boolean {
        val roomOccs = occupancies.filter { it.roomCode == roomCode }
        if (targetPeriods.isEmpty()) {
            val occupiedSet = roomOccs.flatMapTo(HashSet()) { it.periods }
            return (1..12).any { it !in occupiedSet }
        }
        return roomOccs.none { occ -> occ.periods.any { it in targetPeriods } }
    }

    fun roomDayAvailability(
        occupancies: List<ClassroomOccupancy>,
        roomCode: String,
    ): Map<Int, OccupancyType?> {
        val roomOccs = occupancies.filter { it.roomCode == roomCode }
        val result = LinkedHashMap<Int, OccupancyType?>(12)
        for (p in 1..12) {
            result[p] = roomOccs.firstOrNull { p in it.periods }?.type
        }
        return result
    }

    fun resolveClassroomBoxSelection(
        buildingCampusCode: String,
        fallbackCampus: GdutCampus,
        weekStart: LocalDate,
        days: IntRange,
        firstPeriod: Int,
        lastPeriod: Int,
        roomName: String,
        roomOccupanciesByDay: Map<Int, List<ClassroomOccupancy>> = emptyMap(),
        personalBusyByDay: Map<Int, Set<Int>> = emptyMap(),
    ): ClassroomBoxSelection? {
        val campus = JwcwxParse.resolveCampus(buildingCampusCode, fallbackCampus)
        val periods = GdutPeriods.forCampus(campus).sortedBy { it.index }
        val p1 = minOf(firstPeriod, lastPeriod)
        val p2 = maxOf(firstPeriod, lastPeriod)
        val startP = periods.firstOrNull { it.index == p1 } ?: return null
        val endP = periods.firstOrNull { it.index == p2 } ?: return null
        val d1 = minOf(days.first, days.last).coerceIn(1, 7)
        val d2 = maxOf(days.first, days.last).coerceIn(1, 7)
        val dayRange = d1..d2
        val dates = dayRange.map { weekStart.plusDays((it - 1).toLong()) }
        val firstDate = dates.first()
        val extraDates = dates.drop(1).takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }
        val periodRange = p1..p2
        val overlapsOccupancy =
            dayRange.any { d ->
                roomOccupanciesByDay[d].orEmpty().any { occ -> occ.periods.any { it in periodRange } }
            }
        val overlapsPersonal =
            dayRange.any { d ->
                personalBusyByDay[d].orEmpty().any { it in periodRange }
            }
        return ClassroomBoxSelection(
            date = firstDate.toString(),
            start = firstDate.atTime(startP.start).toString(),
            end = firstDate.atTime(endP.end).toString(),
            extraDates = extraDates,
            location = roomName,
            overlapsOccupancy = overlapsOccupancy,
            overlapsPersonal = overlapsPersonal,
        )
    }

    fun formatPeriodRange(periods: List<Int>): String {
        val sorted = periods.distinct().sorted()
        if (sorted.isEmpty()) return ""
        return if (sorted.size == 1) {
            sorted.first().toString()
        } else if (sorted.last() - sorted.first() + 1 == sorted.size) {
            "${sorted.first()}–${sorted.last()}"
        } else {
            sorted.joinToString(",")
        }
    }

    fun personalOverlayForWeek(
        snap: Snapshot,
        week: Int,
        weekStart: LocalDate,
        periods: List<PeriodTime>,
    ): List<PersonalOverlayItem> {
        val live = snap.live()
        val items = mutableListOf<PersonalOverlayItem>()
        val sem = live.currentSemester
        if (sem != null) {
            val occs = CourseExpander.occurrencesInWeek(sem, live.courses, live.exceptions, week)
            occs.forEach { occ ->
                items +=
                    PersonalOverlayItem(
                        dayOfWeek = occ.date.dayOfWeek.value,
                        startPeriod = occ.startPeriod,
                        endPeriod = occ.endPeriod,
                        title = occ.course.name,
                    )
            }
        }
        val weekEnd = weekStart.plusDays(6)
        live.tasks
            .filter { t -> t.parentId == null && t.parcelCode() == null && (t.blockStart ?: t.due) != null }
            .forEach { t ->
                val at = t.blockStart ?: t.due ?: return@forEach
                val days =
                    if (t.blockStart == null) {
                        listOf(at.toLocalDate())
                    } else {
                        repeatInstances(at.toLocalDate(), t.repeat?.freq, t.repeat?.until)
                    }
                val mins = t.blockMinutes ?: 60
                if (t.blockStart != null && mins > 180) return@forEach
                val startT = at.toLocalTime()
                val endT = startT.plusMinutes(if (t.blockStart != null) mins.toLong() else 1L)
                val rows = Agenda.blockRows(periods, startT, endT) ?: return@forEach
                days
                    .filter { d -> !d.isBefore(weekStart) && !d.isAfter(weekEnd) }
                    .forEach { d ->
                        items +=
                            PersonalOverlayItem(
                                dayOfWeek = d.dayOfWeek.value,
                                startPeriod = rows.first,
                                endPeriod = rows.last,
                                title = t.title,
                            )
                    }
            }
        return items
    }
}

private data class ClassroomBoxSel(
    val day1: Int,
    val day2: Int,
    val first: Int,
    val last: Int,
)

/** 「空教室」：按教学楼与节次查空闲教室，查看单间周课表并框选/点按新建日程。 */
@Composable
fun ClassroomScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    val jxfwState by app.jxfw.state.collectAsState()
    val snap = liveSnapshot()
    val today = rememberToday().value

    val semester = snap.currentSemester
    val cal = remember(semester) { semester?.let { SemesterCalendar(it) } }
    val fallbackCampus = remember(semester) { semester?.let { GdutPeriods.detectCampus(it.periods) } ?: GdutCampus.HEMC }
    val maxWeeks = semester?.totalWeeks ?: 20
    val defaultWeek =
        remember(cal, today, maxWeeks) {
            cal?.weekOf(today)?.coerceIn(1, maxWeeks) ?: 1
        }

    var buildings by remember { mutableStateOf<List<ClassroomBuilding>>(emptyList()) }
    var buildingsLoading by remember { mutableStateOf(false) }
    var buildingsError by remember { mutableStateOf<String?>(null) }
    var buildingsErrorKind by remember { mutableStateOf<JxfwException.Kind?>(null) }

    var selectedCampus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBuildingCode by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWeek by rememberSaveable { mutableIntStateOf(defaultWeek) }
    var selectedDayOfWeek by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }
    var selectedPeriodGroups by remember { mutableStateOf<Set<PeriodGroup>>(emptySet()) }
    var onlyFree by rememberSaveable { mutableStateOf(false) }

    var roomsByBuilding by remember { mutableStateOf<Map<String, List<ClassroomRoom>>>(emptyMap()) }
    var occupancyByKey by remember {
        mutableStateOf<Map<Pair<String, LocalDate>, Map<LocalDate, List<ClassroomOccupancy>>>>(emptyMap())
    }
    var roomLoading by remember { mutableStateOf(false) }
    var roomError by remember { mutableStateOf<String?>(null) }
    var roomErrorKind by remember { mutableStateOf<JxfwException.Kind?>(null) }

    var selectedRoomCode by rememberSaveable { mutableStateOf<String?>(null) }
    var overlayPersonal by rememberSaveable { mutableStateOf(true) }
    var detailOccupancy by remember { mutableStateOf<ClassroomOccupancy?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var forceRefreshNext by remember { mutableStateOf(false) }

    fun weekStartFor(week: Int): LocalDate = cal?.weekStart(week) ?: GdutPeriods.week1Monday.plusWeeks((week - 1).toLong())

    LaunchedEffect(jxfwState.loggedIn, reloadToken) {
        if (!jxfwState.loggedIn) return@LaunchedEffect
        buildingsLoading = true
        buildingsError = null
        buildingsErrorKind = null
        try {
            val list = app.jxfw.fetchClassroomBuildings(forceRefresh = forceRefreshNext)
            buildings = list
            if (selectedCampus == null || list.none { it.campusName == selectedCampus }) {
                selectedCampus = ClassroomGridHelper.defaultCampusName(list, fallbackCampus)
            }
        } catch (e: JxfwException) {
            buildingsError = e.message
            buildingsErrorKind = e.kind
        } finally {
            buildingsLoading = false
        }
    }

    val campuses =
        remember(buildings) {
            buildings.mapNotNull { it.campusName.takeIf { n -> n.isNotBlank() } }.distinct()
        }
    val activeCampus = selectedCampus?.takeIf { it in campuses } ?: campuses.firstOrNull()
    val campusBuildings =
        remember(buildings, activeCampus) {
            if (activeCampus == null) buildings else buildings.filter { it.campusName == activeCampus }
        }
    val activeBuilding =
        remember(campusBuildings, selectedBuildingCode) {
            campusBuildings.firstOrNull { it.code == selectedBuildingCode } ?: campusBuildings.firstOrNull()
        }

    val weekStart = remember(cal, selectedWeek) { weekStartFor(selectedWeek) }

    LaunchedEffect(jxfwState.loggedIn, activeBuilding?.code, weekStart, reloadToken) {
        val bCode = activeBuilding?.code ?: return@LaunchedEffect
        roomLoading = true
        roomError = null
        roomErrorKind = null
        val force = forceRefreshNext
        forceRefreshNext = false
        try {
            if (force || !roomsByBuilding.containsKey(bCode)) {
                val rList = app.jxfw.fetchClassroomRooms(bCode, forceRefresh = force)
                roomsByBuilding = roomsByBuilding + (bCode to rList)
            }
            val key = bCode to weekStart
            if (force || !occupancyByKey.containsKey(key)) {
                val occMap = app.jxfw.fetchClassroomWeekOccupancy(bCode, weekStart, forceRefresh = force)
                occupancyByKey = occupancyByKey + (key to occMap)
            }
        } catch (e: JxfwException) {
            roomError = e.message
            roomErrorKind = e.kind
        } finally {
            roomLoading = false
        }
    }

    val currentRooms =
        remember(activeBuilding, roomsByBuilding) {
            activeBuilding?.let { roomsByBuilding[it.code] }.orEmpty()
        }
    val currentWeekOccupancy =
        remember(activeBuilding, weekStart, occupancyByKey) {
            activeBuilding?.let { occupancyByKey[it.code to weekStart] }.orEmpty()
        }
    val selectedRoom =
        remember(currentRooms, selectedRoomCode) {
            selectedRoomCode?.let { code -> currentRooms.firstOrNull { it.code == code } }
        }

    BackHandler(enabled = selectedRoom != null) {
        selectedRoomCode = null
    }

    val screenTitle =
        if (selectedRoom != null) {
            val cap = selectedRoom.capacity
            if (cap != null) {
                stringResource(R.string.classroom_room_title_with_cap, selectedRoom.name, cap)
            } else {
                selectedRoom.name
            }
        } else {
            stringResource(R.string.classroom_title)
        }

    MintScreen(
        title = screenTitle,
        onBack = {
            if (selectedRoom != null) selectedRoomCode = null else onBack()
        },
        actions = {
            if (jxfwState.loggedIn) {
                IconButton(
                    onClick = {
                        forceRefreshNext = true
                        reloadToken++
                    },
                ) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.classroom_refresh_cd))
                }
            }
        },
    ) {
        if (!jxfwState.loggedIn) {
            EmptyState(
                title = stringResource(R.string.classroom_not_logged_in_title),
                hint = stringResource(R.string.classroom_not_logged_in_hint),
                icon = Icons.Rounded.School,
                actionLabel = stringResource(R.string.jxfw_card_bind_action),
                onAction = { nav.push(Route.Jxfw) },
                modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
            return@MintScreen
        }

        if (selectedRoom != null && activeBuilding != null) {
            ClassroomRoomScheduleView(
                building = activeBuilding,
                room = selectedRoom,
                fallbackCampus = fallbackCampus,
                week = selectedWeek,
                maxWeeks = maxWeeks,
                weekStart = weekStart,
                today = today,
                snap = snap,
                weekOccupancy = currentWeekOccupancy,
                loading = roomLoading,
                error = roomError,
                errorKind = roomErrorKind,
                overlayPersonal = overlayPersonal,
                onToggleOverlay = { overlayPersonal = !overlayPersonal },
                onWeekChange = { selectedWeek = it.coerceIn(1, maxWeeks) },
                onRetry = {
                    forceRefreshNext = true
                    reloadToken++
                },
                onOpenJxfw = { nav.push(Route.Jxfw) },
                onTapOccupancy = { detailOccupancy = it },
                onSelectBox = { days, firstP, lastP, occByDay, personalByDay ->
                    val sel =
                        ClassroomGridHelper.resolveClassroomBoxSelection(
                            buildingCampusCode = activeBuilding.campusCode,
                            fallbackCampus = fallbackCampus,
                            weekStart = weekStart,
                            days = days,
                            firstPeriod = firstP,
                            lastPeriod = lastP,
                            roomName = selectedRoom.name,
                            roomOccupanciesByDay = occByDay,
                            personalBusyByDay = personalByDay,
                        ) ?: return@ClassroomRoomScheduleView
                    if (sel.overlapsOccupancy || sel.overlapsPersonal) {
                        actions.toast(ctx.getString(R.string.classroom_overlap_note))
                    }
                    nav.push(
                        Route.TodoEdit(
                            id = null,
                            date = sel.date,
                            start = sel.start,
                            end = sel.end,
                            location = sel.location,
                            extraDates = sel.extraDates,
                        ),
                    )
                },
            )
        } else {
            val targetDate = weekStart.plusDays((selectedDayOfWeek - 1).toLong())
            val dayOccupancies = currentWeekOccupancy[targetDate].orEmpty()
            val targetPeriods =
                remember(selectedPeriodGroups) {
                    selectedPeriodGroups.flatMapTo(HashSet()) { it.periods }
                }
            val displayedRooms =
                remember(currentRooms, dayOccupancies, targetPeriods, onlyFree) {
                    if (!onlyFree) {
                        currentRooms
                    } else {
                        currentRooms.filter { room ->
                            ClassroomGridHelper.isRoomFree(dayOccupancies, room.code, targetPeriods)
                        }
                    }
                }

            ClassroomFinderView(
                campuses = campuses,
                activeCampus = activeCampus,
                onSelectCampus = {
                    selectedCampus = it
                    selectedBuildingCode = null
                    selectedRoomCode = null
                },
                buildings = campusBuildings,
                activeBuilding = activeBuilding,
                onSelectBuilding = {
                    selectedBuildingCode = it.code
                    selectedRoomCode = null
                },
                week = selectedWeek,
                maxWeeks = maxWeeks,
                weekStart = weekStart,
                onWeekChange = { selectedWeek = it.coerceIn(1, maxWeeks) },
                selectedDayOfWeek = selectedDayOfWeek,
                onSelectDayOfWeek = { selectedDayOfWeek = it },
                selectedPeriodGroups = selectedPeriodGroups,
                onTogglePeriodGroup = { grp ->
                    selectedPeriodGroups =
                        if (grp in selectedPeriodGroups) {
                            selectedPeriodGroups - grp
                        } else {
                            selectedPeriodGroups + grp
                        }
                },
                onlyFree = onlyFree,
                onToggleOnlyFree = { onlyFree = !onlyFree },
                rooms = displayedRooms,
                dayOccupancies = dayOccupancies,
                targetPeriods = targetPeriods,
                loading = buildingsLoading || roomLoading,
                error = buildingsError ?: roomError,
                errorKind = buildingsErrorKind ?: roomErrorKind,
                onRetry = {
                    forceRefreshNext = true
                    reloadToken++
                },
                onOpenJxfw = { nav.push(Route.Jxfw) },
                onSelectRoom = { room -> selectedRoomCode = room.code },
            )
        }
    }

    detailOccupancy?.let { occ ->
        val campus =
            activeBuilding?.let { JwcwxParse.resolveCampus(it.campusCode, fallbackCampus) } ?: fallbackCampus
        val periods = remember(campus) { GdutPeriods.forCampus(campus).sortedBy { it.index } }
        ClassroomOccupancyDialog(
            occ = occ,
            periods = periods,
            onDismiss = { detailOccupancy = null },
        )
    }
}

@Composable
private fun ClassroomFinderView(
    campuses: List<String>,
    activeCampus: String?,
    onSelectCampus: (String) -> Unit,
    buildings: List<ClassroomBuilding>,
    activeBuilding: ClassroomBuilding?,
    onSelectBuilding: (ClassroomBuilding) -> Unit,
    week: Int,
    maxWeeks: Int,
    weekStart: LocalDate,
    onWeekChange: (Int) -> Unit,
    selectedDayOfWeek: Int,
    onSelectDayOfWeek: (Int) -> Unit,
    selectedPeriodGroups: Set<PeriodGroup>,
    onTogglePeriodGroup: (PeriodGroup) -> Unit,
    onlyFree: Boolean,
    onToggleOnlyFree: () -> Unit,
    rooms: List<ClassroomRoom>,
    dayOccupancies: List<ClassroomOccupancy>,
    targetPeriods: Set<Int>,
    loading: Boolean,
    error: String?,
    errorKind: JxfwException.Kind?,
    onRetry: () -> Unit,
    onOpenJxfw: () -> Unit,
    onSelectRoom: (ClassroomRoom) -> Unit,
) {
    val ctx = LocalContext.current
    val weekdayLabels = remember(ctx) { (1..7).map { ctx.weekdayShort(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        if (error != null) {
            item(key = "error") {
                ClassroomErrorBanner(
                    error = error,
                    errorKind = errorKind,
                    onRetry = onRetry,
                    onOpenJxfw = onOpenJxfw,
                )
            }
        }

        if (campuses.isNotEmpty()) {
            item(key = "campuses") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(campuses, key = { it }) { campus ->
                        ChipPill(
                            label = campus,
                            on = campus == activeCampus,
                            onClick = { onSelectCampus(campus) },
                        )
                    }
                }
            }
        }

        if (buildings.isNotEmpty()) {
            item(key = "buildings") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(buildings, key = { it.code }) { b ->
                        ChipPill(
                            label = b.name,
                            on = b.code == activeBuilding?.code,
                            onClick = { onSelectBuilding(b) },
                        )
                    }
                }
            }
        }

        item(key = "week-switcher") {
            WeekSwitcherRow(
                week = week,
                maxWeeks = maxWeeks,
                weekStart = weekStart,
                onWeekChange = onWeekChange,
            )
        }

        item(key = "day-filter") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..7).toList(), key = { it }) { dow ->
                    ChipPill(
                        label = weekdayLabels[dow - 1],
                        on = dow == selectedDayOfWeek,
                        onClick = { onSelectDayOfWeek(dow) },
                    )
                }
            }
        }

        item(key = "period-filter") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "only-free") {
                    ChipPill(
                        label = stringResource(R.string.classroom_only_free),
                        on = onlyFree,
                        role = Role.Checkbox,
                        onClick = onToggleOnlyFree,
                    )
                }
                items(PeriodGroup.entries, key = { it.code }) { grp ->
                    ChipPill(
                        label = grp.code,
                        on = grp in selectedPeriodGroups,
                        role = Role.Checkbox,
                        onClick = { onTogglePeriodGroup(grp) },
                    )
                }
            }
        }

        if (!loading && buildings.isEmpty() && error == null) {
            item(key = "empty-buildings") {
                EmptyState(
                    title = stringResource(R.string.classroom_empty_buildings),
                    icon = Icons.Rounded.MeetingRoom,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else if (!loading && rooms.isEmpty() && activeBuilding != null && error == null) {
            item(key = "empty-rooms") {
                EmptyState(
                    title = stringResource(R.string.classroom_empty_rooms),
                    icon = Icons.Rounded.MeetingRoom,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            items(rooms, key = { it.code }) { room ->
                ClassroomRoomRow(
                    room = room,
                    dayOccupancies = dayOccupancies,
                    targetPeriods = targetPeriods,
                    onClick = { onSelectRoom(room) },
                )
            }
        }
    }
}

@Composable
private fun ClassroomRoomRow(
    room: ClassroomRoom,
    dayOccupancies: List<ClassroomOccupancy>,
    targetPeriods: Set<Int>,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mintColors = MintTheme.colors
    val isFree =
        remember(dayOccupancies, room.code, targetPeriods) {
            ClassroomGridHelper.isRoomFree(dayOccupancies, room.code, targetPeriods)
        }
    val availability =
        remember(dayOccupancies, room.code) {
            ClassroomGridHelper.roomDayAvailability(dayOccupancies, room.code)
        }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                room.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            room.capacity?.let { cap ->
                Text(
                    stringResource(R.string.classroom_capacity_seats, cap),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                )
            }
            Tag(
                text = stringResource(if (isFree) R.string.classroom_status_free else R.string.classroom_status_busy),
                bg = if (isFree) scheme.primaryContainer else scheme.surfaceContainerHighest,
                fg = if (isFree) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }

        // 12-period day availability strip (grouped 1-4, 5-9, 10-12)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in 1..12) {
                val type = availability[p]
                val barColor =
                    when (type) {
                        null -> scheme.surfaceContainerHighest
                        OccupancyType.COURSE -> scheme.primary.copy(alpha = 0.65f)
                        OccupancyType.BORROWED -> scheme.secondary.copy(alpha = 0.65f)
                        OccupancyType.EXAM -> scheme.tertiary.copy(alpha = 0.65f)
                        OccupancyType.LOCKED -> mintColors.removed.copy(alpha = 0.65f)
                        OccupancyType.OTHER -> scheme.outline.copy(alpha = 0.55f)
                    }
                val highlighted = p in targetPeriods
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor)
                        .then(
                            if (highlighted) {
                                Modifier.border(1.dp, scheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                )
                if (p == 4 || p == 9) {
                    Spacer(Modifier.width(3.dp))
                }
            }
        }
    }
}

@Composable
private fun ClassroomRoomScheduleView(
    building: ClassroomBuilding,
    room: ClassroomRoom,
    fallbackCampus: GdutCampus,
    week: Int,
    maxWeeks: Int,
    weekStart: LocalDate,
    today: LocalDate,
    snap: Snapshot,
    weekOccupancy: Map<LocalDate, List<ClassroomOccupancy>>,
    loading: Boolean,
    error: String?,
    errorKind: JxfwException.Kind?,
    overlayPersonal: Boolean,
    onToggleOverlay: () -> Unit,
    onWeekChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenJxfw: () -> Unit,
    onTapOccupancy: (ClassroomOccupancy) -> Unit,
    onSelectBox: (
        days: IntRange,
        firstPeriod: Int,
        lastPeriod: Int,
        occByDay: Map<Int, List<ClassroomOccupancy>>,
        personalByDay: Map<Int, Set<Int>>,
    ) -> Unit,
) {
    val ctx = LocalContext.current
    val campus = remember(building.campusCode, fallbackCampus) { JwcwxParse.resolveCampus(building.campusCode, fallbackCampus) }
    val periods = remember(campus) { GdutPeriods.forCampus(campus).sortedBy { it.index } }

    val occByDay =
        remember(weekOccupancy, room.code, weekStart) {
            (1..7).associateWith { dow ->
                val date = weekStart.plusDays((dow - 1).toLong())
                weekOccupancy[date].orEmpty().filter { it.roomCode == room.code }
            }
        }
    val personalItems =
        remember(snap, week, weekStart, periods, overlayPersonal) {
            if (!overlayPersonal) {
                emptyList()
            } else {
                ClassroomGridHelper.personalOverlayForWeek(snap, week, weekStart, periods)
            }
        }
    val personalBusyByDay =
        remember(personalItems) {
            personalItems
                .groupBy { it.dayOfWeek }
                .mapValues { (_, list) ->
                    list.flatMapTo(HashSet()) { it.startPeriod..it.endPeriod }
                }
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekSwitcherRow(
                week = week,
                maxWeeks = maxWeeks,
                weekStart = weekStart,
                onWeekChange = onWeekChange,
                modifier = Modifier.weight(1f),
            )
            ChipPill(
                label = stringResource(R.string.classroom_overlay_personal),
                on = overlayPersonal,
                role = Role.Checkbox,
                onClick = onToggleOverlay,
            )
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        if (error != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ClassroomErrorBanner(
                    error = error,
                    errorKind = errorKind,
                    onRetry = onRetry,
                    onOpenJxfw = onOpenJxfw,
                )
            }
        }

        Text(
            stringResource(R.string.classroom_grid_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        val density = LocalDensity.current
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
        val fallbackWidth =
            with(density) {
                val dmWidth = ctx.resources.displayMetrics.widthPixels
                if (dmWidth > 0) dmWidth.toDp() else screenWidthDp
            }
        var measuredWidthPx by remember { mutableIntStateOf(0) }
        val gridWidth = if (measuredWidthPx > 0) with(density) { measuredWidthPx.toDp() } else fallbackWidth

        ClassroomWeekGrid(
            periods = periods,
            weekStart = weekStart,
            today = today,
            occByDay = occByDay,
            personalItems = personalItems,
            gridWidth = gridWidth,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { measuredWidthPx = it.width },
            onTapOccupancy = onTapOccupancy,
            onSelectBox = { days, firstP, lastP ->
                onSelectBox(days, firstP, lastP, occByDay, personalBusyByDay)
            },
        )
    }
}

@Composable
private fun ClassroomWeekGrid(
    periods: List<PeriodTime>,
    weekStart: LocalDate,
    today: LocalDate,
    occByDay: Map<Int, List<ClassroomOccupancy>>,
    personalItems: List<PersonalOverlayItem>,
    gridWidth: Dp,
    modifier: Modifier = Modifier,
    onTapOccupancy: (ClassroomOccupancy) -> Unit,
    onSelectBox: (days: IntRange, firstPeriod: Int, lastPeriod: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.35f)
    val rowH = 54.dp * fontScale
    val groupGap = 10.dp
    val timeColW = 36.dp * fontScale
    val colW = (gridWidth - timeColW) / 7
    val n = periods.size
    val boundaries = remember(n) { ClassroomGridHelper.groupBoundaries(n) }
    val totalH = ClassroomGridHelper.calcPeriodTop(n, n, rowH, groupGap) + rowH

    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outlineVariant
    val primaryContainer = scheme.primaryContainer
    val onPrimaryContainer = scheme.onPrimaryContainer
    val color = scheme.onSurface
    val variantColor = scheme.onSurfaceVariant
    val typography = MaterialTheme.typography
    val labelStyle = typography.labelMedium
    val bodySmall = typography.bodySmall
    val labelSmall = typography.labelSmall
    val headerDateStyle = remember(bodySmall) { bodySmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum") }
    val timeStyle = remember(labelSmall) { labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum") }
    val blockTitleStyle = remember(labelSmall) { labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp) }
    val blockSubStyle = remember(labelSmall) { labelSmall.copy(fontSize = 9.5.sp, lineHeight = 11.sp, letterSpacing = 0.sp) }
    val weekdayShorts = remember(ctx) { (1..7).map { ctx.weekdayShort(it) } }
    val scroll = rememberScrollState()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = timeColW)) {
            for (d in 1..7) {
                val date = weekStart.plusDays((d - 1).toLong())
                val isToday = date == today
                val dayTextColor = if (isToday) onPrimaryContainer else variantColor
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) primaryContainer else Color.Transparent),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(weekdayShorts[d - 1], style = labelStyle, color = dayTextColor)
                    Text("${date.monthValue}/${date.dayOfMonth}", style = headerDateStyle, color = dayTextColor)
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            var box by remember { mutableStateOf<ClassroomBoxSel?>(null) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(totalH)
                    .drawBehind {
                        val startX = timeColW.toPx()
                        val lineW = (size.width - startX).coerceAtLeast(0f)
                        val lineH = 1.dp.toPx()
                        boundaries.forEach { b ->
                            val y = (ClassroomGridHelper.calcPeriodTop(b, n, rowH, groupGap) + rowH + groupGap / 2).toPx()
                            drawRect(
                                color = outline,
                                topLeft = Offset(startX, y),
                                size = Size(lineW, lineH),
                            )
                        }
                    }.pointerInput(n, timeColW, rowH, occByDay) {
                        val timeColPx = timeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = rowH.toPx()

                        fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                        fun slotOf(y: Float): Int {
                            for (p in 1..n) {
                                val top = (rowH.value * (p - 1) + groupGap.value * boundaries.count { p > it }) * density
                                if (y < top + rowPx) return p
                            }
                            return n
                        }

                        detectTapGestures(
                            onTap = { off ->
                                if (box == null && off.x >= timeColPx) {
                                    val d = colOf(off.x)
                                    val p = slotOf(off.y)
                                    val occupied = occByDay[d].orEmpty().any { p in it.periods }
                                    if (!occupied) {
                                        onSelectBox(d..d, p, p)
                                    }
                                }
                            },
                        )
                    }.pointerInput(n, timeColW, rowH) {
                        val timeColPx = timeColW.toPx()
                        val colPx = (size.width - timeColPx) / 7f
                        val rowPx = rowH.toPx()

                        fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                        fun slotOf(y: Float): Int {
                            for (p in 1..n) {
                                val top = (rowH.value * (p - 1) + groupGap.value * boundaries.count { p > it }) * density
                                if (y < top + rowPx) return p
                            }
                            return n
                        }

                        var anchorDay = 1
                        var anchorSlot = 1
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                if (off.x >= timeColPx) {
                                    anchorDay = colOf(off.x)
                                    anchorSlot = slotOf(off.y)
                                    box = ClassroomBoxSel(anchorDay, anchorDay, anchorSlot, anchorSlot)
                                }
                            },
                            onDrag = { change, _ ->
                                val b = box ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                val d = colOf(change.position.x)
                                val e = slotOf(change.position.y).coerceIn(1, n)
                                box = b.copy(day2 = d, first = minOf(anchorSlot, e), last = maxOf(anchorSlot, e))
                            },
                            onDragEnd = {
                                box?.let { b ->
                                    onSelectBox(minOf(b.day1, b.day2)..maxOf(b.day1, b.day2), b.first, b.last)
                                }
                                box = null
                            },
                            onDragCancel = { box = null },
                        )
                    },
            ) {
                val periodLabels =
                    remember(periods, timeStyle, labelStyle, color, variantColor) {
                        periods.map { p ->
                            buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontSize = labelStyle.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                    ),
                                ) {
                                    append("${p.index}\n")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = timeStyle.fontSize,
                                        color = variantColor,
                                        fontFeatureSettings = "tnum",
                                    ),
                                ) {
                                    append("${p.start.hm()}\n${p.end.hm()}")
                                }
                            }
                        }
                    }
                periods.forEachIndexed { i, p ->
                    Text(
                        text = periodLabels[i],
                        modifier =
                            Modifier
                                .absoluteOffset(0.dp, ClassroomGridHelper.calcPeriodTop(p.index, n, rowH, groupGap) + 4.dp)
                                .width(timeColW),
                        style = timeStyle,
                        textAlign = TextAlign.Center,
                    )
                }

                // Overlaid personal courses & tasks when room itself is free in those periods
                personalItems.forEach { item ->
                    val roomOccs = occByDay[item.dayOfWeek].orEmpty()
                    val overlapsRoom = roomOccs.any { occ -> occ.periods.any { it in item.startPeriod..item.endPeriod } }
                    if (!overlapsRoom) {
                        val sp = item.startPeriod.coerceIn(1, n)
                        val ep = item.endPeriod.coerceIn(sp, n)
                        val top = ClassroomGridHelper.calcPeriodTop(sp, n, rowH, groupGap) + 2.dp
                        val bottom = ClassroomGridHelper.calcPeriodTop(ep, n, rowH, groupGap) + rowH - 2.dp
                        Box(
                            Modifier
                                .absoluteOffset(timeColW + colW * (item.dayOfWeek - 1) + 1.5.dp, top)
                                .width(colW - 3.dp)
                                .height(bottom - top)
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceContainerHigh.copy(alpha = 0.72f))
                                .border(1.dp, scheme.outline.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.classroom_personal_prefix, item.title),
                                style = blockSubStyle,
                                color = variantColor,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Classroom occupancy blocks
                occByDay.forEach { (dow, occs) ->
                    occs.forEach { occ ->
                        val p1 = occ.periods.minOrNull()?.coerceIn(1, n) ?: return@forEach
                        val p2 = occ.periods.maxOrNull()?.coerceIn(p1, n) ?: return@forEach
                        val top = ClassroomGridHelper.calcPeriodTop(p1, n, rowH, groupGap) + 2.dp
                        val bottom = ClassroomGridHelper.calcPeriodTop(p2, n, rowH, groupGap) + rowH - 2.dp
                        val (bg, fg, badgeRes) = occupancyColorsAndBadge(occ.type)
                        val title = occ.courseName.ifBlank { occ.note.ifBlank { stringResource(R.string.classroom_unnamed_occupancy) } }
                        Column(
                            Modifier
                                .absoluteOffset(timeColW + colW * (dow - 1) + 1.5.dp, top)
                                .width(colW - 3.dp)
                                .height(bottom - top)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .mintClickable(pressedScale = 0.96f) { onTapOccupancy(occ) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    stringResource(badgeRes),
                                    style = blockSubStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = fg,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .background(fg.copy(alpha = 0.14f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                title,
                                style = blockTitleStyle,
                                fontWeight = FontWeight.Medium,
                                color = fg,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (occ.teacher.isNotBlank()) {
                                Text(
                                    occ.teacher,
                                    style = blockSubStyle,
                                    color = fg.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // BoxSel highlight
                box?.let { b ->
                    val boxAlpha by animateFloatAsState(if (box != null) 1f else 0f, MintTheme.spec(), label = "classroom-box-sel")
                    val top = ClassroomGridHelper.calcPeriodTop(b.first, n, rowH, groupGap) + 2.dp
                    val bottom = ClassroomGridHelper.calcPeriodTop(b.last, n, rowH, groupGap) + rowH - 2.dp
                    val d1 = minOf(b.day1, b.day2)
                    val d2 = maxOf(b.day1, b.day2)
                    Box(
                        Modifier
                            .graphicsLayer { alpha = boxAlpha }
                            .absoluteOffset(timeColW + colW * (d1 - 1) + 1.5.dp, top)
                            .width(colW * (d2 - d1 + 1) - 3.dp)
                            .height(bottom - top)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primary.copy(alpha = 0.3f))
                            .border(2.dp, scheme.primary, RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun occupancyColorsAndBadge(type: OccupancyType): Triple<Color, Color, Int> {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        OccupancyType.COURSE -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, R.string.classroom_badge_course)
        OccupancyType.BORROWED -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, R.string.classroom_badge_borrowed)
        OccupancyType.EXAM -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, R.string.classroom_badge_exam)
        OccupancyType.LOCKED -> Triple(scheme.errorContainer, scheme.onErrorContainer, R.string.classroom_badge_locked)
        OccupancyType.OTHER -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, R.string.classroom_badge_other)
    }
}

@Composable
private fun WeekSwitcherRow(
    week: Int,
    maxWeeks: Int,
    weekStart: LocalDate,
    onWeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekEnd = weekStart.plusDays(6)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { onWeekChange(week - 1) },
            enabled = week > 1,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.classroom_prev_week_cd))
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.classroom_week_label, week),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${weekStart.monthValue}/${weekStart.dayOfMonth}–${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onWeekChange(week + 1) },
            enabled = week < maxWeeks,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.classroom_next_week_cd))
        }
    }
}

@Composable
private fun ClassroomErrorBanner(
    error: String,
    errorKind: JxfwException.Kind?,
    onRetry: () -> Unit,
    onOpenJxfw: () -> Unit,
) {
    MintCard(color = MintTheme.colors.conflictBg) {
        JxfwErrorGuidance(errorKind, error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.classroom_retry))
            }
            if (errorKind == JxfwException.Kind.CREDENTIALS || errorKind == JxfwException.Kind.SESSION) {
                TextButton(
                    onClick = onOpenJxfw,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.grades_relogin_jxfw))
                }
            }
        }
    }
}

@Composable
private fun ClassroomOccupancyDialog(
    occ: ClassroomOccupancy,
    periods: List<PeriodTime>,
    onDismiss: () -> Unit,
) {
    val (bg, fg, _) = occupancyColorsAndBadge(occ.type)
    val typeLabelRes =
        when (occ.type) {
            OccupancyType.COURSE -> R.string.classroom_type_course
            OccupancyType.BORROWED -> R.string.classroom_type_borrowed
            OccupancyType.LOCKED -> R.string.classroom_type_locked
            OccupancyType.EXAM -> R.string.classroom_type_exam
            OccupancyType.OTHER -> R.string.classroom_type_other
        }
    val periodRangeStr = ClassroomGridHelper.formatPeriodRange(occ.periods)
    val startT = occ.periods.minOrNull()?.let { p -> periods.firstOrNull { it.index == p }?.start }
    val endT = occ.periods.maxOrNull()?.let { p -> periods.firstOrNull { it.index == p }?.end }
    val periodLine =
        if (startT != null && endT != null) {
            stringResource(R.string.classroom_detail_periods, periodRangeStr, startT.hm(), endT.hm())
        } else {
            stringResource(R.string.classroom_detail_periods_only, periodRangeStr)
        }
    val title = occ.courseName.ifBlank { occ.note.ifBlank { stringResource(typeLabelRes) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(text = stringResource(typeLabelRes), bg = bg, fg = fg)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (periodRangeStr.isNotEmpty()) {
                    Text(
                        periodLine,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                if (occ.teacher.isNotBlank()) {
                    Text(
                        stringResource(R.string.classroom_detail_teacher, occ.teacher),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (occ.className.isNotBlank()) {
                    Text(
                        stringResource(R.string.classroom_detail_class, occ.className),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (occ.note.isNotBlank() && occ.note != title) {
                    Text(
                        stringResource(R.string.classroom_detail_note, occ.note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.classroom_detail_close))
            }
        },
    )
}
