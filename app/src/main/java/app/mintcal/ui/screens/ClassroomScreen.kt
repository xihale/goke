package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.TonalField
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.components.parcelCode
import app.mintcal.ui.components.rememberModalSheetState
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.reduceMotion
import app.mintcal.ui.rememberToday
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayShort
import java.time.LocalDate
import kotlin.math.roundToInt

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
    private val TeacherRoleTagRegex = Regex("""\s*\[(?:主讲|辅讲)]""")

    fun cleanTeacherLabel(teacher: String): String = teacher.replace(TeacherRoleTagRegex, "").trim()

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

    // Silently prefetch next week's occupancy so horizontal week swiping feels instant
    LaunchedEffect(jxfwState.loggedIn, activeBuilding?.code, selectedWeek, maxWeeks) {
        val bCode = activeBuilding?.code ?: return@LaunchedEffect
        if (!jxfwState.loggedIn || selectedWeek >= maxWeeks) return@LaunchedEffect
        val nextStart = weekStartFor(selectedWeek + 1)
        val key = bCode to nextStart
        if (!occupancyByKey.containsKey(key)) {
            runCatching {
                app.jxfw.fetchClassroomWeekOccupancy(bCode, nextStart, forceRefresh = false)
            }.onSuccess { occMap ->
                occupancyByKey = occupancyByKey + (key to occMap)
            }
        }
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

        val isLoading = buildingsLoading || roomLoading
        val reduce = reduceMotion()
        val slideSpec = MintTheme.spec<IntOffset>()
        val fadeSpec = MintTheme.spec<Float>()

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedRoom != null && activeBuilding != null,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)
                    } else if (targetState) {
                        (slideInHorizontally(slideSpec) { it / 4 } + fadeIn(fadeSpec)) togetherWith
                            (slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec))
                    } else {
                        (slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec)) togetherWith
                            (slideOutHorizontally(slideSpec) { it / 4 } + fadeOut(fadeSpec))
                    }
                },
                label = "classroom-mode",
            ) { showingSchedule ->
                val room = selectedRoom
                val building = activeBuilding
                if (showingSchedule && room != null && building != null) {
                    ClassroomRoomScheduleView(
                        building = building,
                        room = room,
                        fallbackCampus = fallbackCampus,
                        week = selectedWeek,
                        maxWeeks = maxWeeks,
                        weekStartFor = ::weekStartFor,
                        today = today,
                        snap = snap,
                        occupancyForWeek = { ws -> occupancyByKey[building.code to ws].orEmpty() },
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
                        onSelectBox = { boxWeekStart, days, firstP, lastP, occByDay, personalByDay ->
                            val sel =
                                ClassroomGridHelper.resolveClassroomBoxSelection(
                                    buildingCampusCode = building.campusCode,
                                    fallbackCampus = fallbackCampus,
                                    weekStart = boxWeekStart,
                                    days = days,
                                    firstPeriod = firstP,
                                    lastPeriod = lastP,
                                    roomName = room.name,
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
                    val freeRoomsCount =
                        remember(currentRooms, dayOccupancies, targetPeriods) {
                            currentRooms.count { r ->
                                ClassroomGridHelper.isRoomFree(dayOccupancies, r.code, targetPeriods)
                            }
                        }
                    val displayedRooms =
                        remember(currentRooms, dayOccupancies, targetPeriods, onlyFree) {
                            if (!onlyFree) {
                                currentRooms
                            } else {
                                currentRooms.filter { r ->
                                    ClassroomGridHelper.isRoomFree(dayOccupancies, r.code, targetPeriods)
                                }
                            }
                        }

                    ClassroomFinderView(
                        campuses = campuses,
                        activeCampus = activeCampus,
                        allBuildings = buildings,
                        activeBuilding = activeBuilding,
                        onSelectBuilding = { b ->
                            if (b.campusName.isNotBlank()) {
                                selectedCampus = b.campusName
                            }
                            selectedBuildingCode = b.code
                            selectedRoomCode = null
                        },
                        week = selectedWeek,
                        maxWeeks = maxWeeks,
                        weekStart = weekStart,
                        today = today,
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
                        totalRoomsCount = currentRooms.size,
                        freeRoomsCount = freeRoomsCount,
                        dayOccupancies = dayOccupancies,
                        targetPeriods = targetPeriods,
                        loading = isLoading,
                        error = buildingsError ?: roomError,
                        errorKind = buildingsErrorKind ?: roomErrorKind,
                        onRetry = {
                            forceRefreshNext = true
                            reloadToken++
                        },
                        onOpenJxfw = { nav.push(Route.Jxfw) },
                        onSelectRoom = { r -> selectedRoomCode = r.code },
                    )
                }
            }

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(fadeSpec),
                exit = fadeOut(fadeSpec),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = Color.Transparent,
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassroomFinderView(
    campuses: List<String>,
    activeCampus: String?,
    allBuildings: List<ClassroomBuilding>,
    activeBuilding: ClassroomBuilding?,
    onSelectBuilding: (ClassroomBuilding) -> Unit,
    week: Int,
    maxWeeks: Int,
    weekStart: LocalDate,
    today: LocalDate,
    onWeekChange: (Int) -> Unit,
    selectedDayOfWeek: Int,
    onSelectDayOfWeek: (Int) -> Unit,
    selectedPeriodGroups: Set<PeriodGroup>,
    onTogglePeriodGroup: (PeriodGroup) -> Unit,
    onlyFree: Boolean,
    onToggleOnlyFree: () -> Unit,
    rooms: List<ClassroomRoom>,
    totalRoomsCount: Int,
    freeRoomsCount: Int,
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
    val scheme = MaterialTheme.colorScheme
    val weekdayLabels = remember(ctx) { (1..7).map { ctx.weekdayShort(it) } }
    val dateSubStyle =
        remember {
            androidx.compose.ui.text
                .TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum")
        }
    var showBuildingSheet by remember { mutableStateOf(false) }
    val weekEnd = weekStart.plusDays(6)

    val selectorLabel =
        when {
            activeCampus != null && activeBuilding != null -> {
                stringResource(R.string.classroom_campus_building, activeCampus, activeBuilding.name)
            }

            activeBuilding != null -> {
                activeBuilding.name
            }

            else -> {
                stringResource(R.string.classroom_select_building)
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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

        // Single clean selector row: left building picker pill + right compact week capsule
        item(key = "top-selector-row") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .semantics { role = Role.Button }
                            .mintClickable(pressedScale = 0.97f) {
                                if (allBuildings.isNotEmpty()) showBuildingSheet = true
                            },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.MeetingRoom,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            selectorLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.classroom_building_picker_cd),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                CompactWeekCapsule(
                    label = stringResource(R.string.classroom_week_label, week),
                    canPrev = week > 1,
                    canNext = week < maxWeeks,
                    onPrev = { onWeekChange(week - 1) },
                    onNext = { onWeekChange(week + 1) },
                )
            }
        }

        // Compact control panel MintCard: 7-col equal-width weekday strip + period quick-filter row
        item(key = "control-panel") {
            MintCard(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(22.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (dow in 1..7) {
                        val date = weekStart.plusDays((dow - 1).toLong())
                        val isSelected = dow == selectedDayOfWeek
                        val isToday = date == today
                        val pillBg by animateColorAsState(
                            if (isSelected) scheme.primaryContainer else Color.Transparent,
                            MintTheme.spec(),
                            label = "day-pill-bg",
                        )
                        val textColor =
                            when {
                                isSelected -> scheme.onPrimaryContainer
                                isToday -> scheme.primary
                                else -> scheme.onSurfaceVariant
                            }
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(pillBg)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                }.mintClickable(pressedScale = 0.96f) { onSelectDayOfWeek(dow) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                weekdayLabels[dow - 1],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                            )
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}",
                                style = dateSubStyle,
                                color = textColor.copy(alpha = if (isSelected) 0.92f else 0.72f),
                                maxLines = 1,
                            )
                            if (isToday) {
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) scheme.onPrimaryContainer else scheme.primary),
                                )
                            } else {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        }

        // Compact count summary row
        if (activeBuilding != null && totalRoomsCount > 0) {
            item(key = "summary-row") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.classroom_summary_count, totalRoomsCount, freeRoomsCount),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(
                            R.string.classroom_summary_week_range,
                            week,
                            weekStart.monthValue,
                            weekStart.dayOfMonth,
                            weekEnd.monthValue,
                            weekEnd.dayOfMonth,
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!loading && allBuildings.isEmpty() && error == null) {
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
                    modifier = Modifier.animateItem(),
                    onClick = { onSelectRoom(room) },
                )
            }
        }
    }

    if (showBuildingSheet) {
        ClassroomBuildingPickerSheet(
            campuses = campuses,
            activeCampus = activeCampus,
            allBuildings = allBuildings,
            activeBuildingCode = activeBuilding?.code,
            onSelect = { b ->
                onSelectBuilding(b)
                showBuildingSheet = false
            },
            onDismiss = { showBuildingSheet = false },
        )
    }
}

@Composable
private fun CompactWeekCapsule(
    label: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHighest,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 48.dp)
                    .clip(CircleShape)
                    .mintClickable(enabled = canPrev, pressedScale = 0.92f, role = Role.Button, onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.classroom_prev_week_cd),
                    tint = if (canPrev) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Box(
                Modifier
                    .size(width = 36.dp, height = 48.dp)
                    .clip(CircleShape)
                    .mintClickable(enabled = canNext, pressedScale = 0.92f, role = Role.Button, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.classroom_next_week_cd),
                    tint = if (canNext) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassroomBuildingPickerSheet(
    campuses: List<String>,
    activeCampus: String?,
    allBuildings: List<ClassroomBuilding>,
    activeBuildingCode: String?,
    onSelect: (ClassroomBuilding) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var sheetCampus by remember(activeCampus) { mutableStateOf(activeCampus ?: campuses.firstOrNull()) }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(allBuildings, sheetCampus, query) {
            val q = query.trim()
            if (q.isNotEmpty()) {
                allBuildings.filter {
                    it.name.contains(q, ignoreCase = true) || it.campusName.contains(q, ignoreCase = true)
                }
            } else if (sheetCampus != null) {
                allBuildings.filter { it.campusName == sheetCampus }
            } else {
                allBuildings
            }
        }
    val rows = remember(filtered) { filtered.chunked(2) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.classroom_select_building),
                style = MaterialTheme.typography.titleLarge,
            )
            TonalField(
                label = "",
                value = query,
                onChange = { query = it },
                placeholder = stringResource(R.string.classroom_building_search_hint),
            )
            if (campuses.size > 1 && query.isBlank()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(campuses, key = { it }) { campus ->
                        ChipPill(
                            label = campus,
                            on = campus == sheetCampus,
                            onClick = { sheetCampus = campus },
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.classroom_building_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(rows, key = { pair -> pair.joinToString(",") { it.code } }) { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { b ->
                                val isSelected = b.code == activeBuildingCode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .semantics {
                                                role = Role.RadioButton
                                                selected = isSelected
                                            }.mintClickable(pressedScale = 0.96f) { onSelect(b) },
                                ) {
                                    Column(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            b.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (query.isNotBlank() && b.campusName.isNotBlank()) {
                                            Text(
                                                b.campusName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassroomRoomRow(
    room: ClassroomRoom,
    dayOccupancies: List<ClassroomOccupancy>,
    targetPeriods: Set<Int>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isFree =
        remember(dayOccupancies, room.code, targetPeriods) {
            ClassroomGridHelper.isRoomFree(dayOccupancies, room.code, targetPeriods)
        }
    val availability =
        remember(dayOccupancies, room.code) {
            ClassroomGridHelper.roomDayAvailability(dayOccupancies, room.code)
        }
    val captionStyle =
        remember {
            androidx.compose.ui.text
                .TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum")
        }

    MintCard(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
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

        Spacer(Modifier.height(10.dp))

        // 12-period day availability strip (grouped 1-4, 5-9, 10-12)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in 1..12) {
                val type = availability[p]
                val barColor =
                    when (type) {
                        null -> scheme.primaryContainer.copy(alpha = 0.65f)
                        OccupancyType.COURSE -> scheme.surfaceContainerHighest
                        OccupancyType.BORROWED -> scheme.secondary.copy(alpha = 0.45f)
                        OccupancyType.EXAM -> scheme.tertiary.copy(alpha = 0.45f)
                        OccupancyType.LOCKED -> scheme.error.copy(alpha = 0.40f)
                        OccupancyType.OTHER -> scheme.outline.copy(alpha = 0.30f)
                    }
                val highlighted = p in targetPeriods
                Box(
                    Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(barColor)
                        .then(
                            if (highlighted) {
                                Modifier.border(1.dp, scheme.primary, RoundedCornerShape(3.5.dp))
                            } else {
                                Modifier
                            },
                        ),
                )
                if (p == 4 || p == 9) {
                    Spacer(Modifier.width(4.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.classroom_timeline_morning),
                style = captionStyle,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(4f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.classroom_timeline_afternoon),
                style = captionStyle,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(5f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.classroom_timeline_evening),
                style = captionStyle,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(3f),
            )
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
    weekStartFor: (Int) -> LocalDate,
    today: LocalDate,
    snap: Snapshot,
    occupancyForWeek: (LocalDate) -> Map<LocalDate, List<ClassroomOccupancy>>,
    error: String?,
    errorKind: JxfwException.Kind?,
    overlayPersonal: Boolean,
    onToggleOverlay: () -> Unit,
    onWeekChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenJxfw: () -> Unit,
    onTapOccupancy: (ClassroomOccupancy) -> Unit,
    onSelectBox: (
        boxWeekStart: LocalDate,
        days: IntRange,
        firstPeriod: Int,
        lastPeriod: Int,
        occByDay: Map<Int, List<ClassroomOccupancy>>,
        personalByDay: Map<Int, Set<Int>>,
    ) -> Unit,
) {
    val ctx = LocalContext.current
    val reduce = reduceMotion()
    val campus = remember(building.campusCode, fallbackCampus) { JwcwxParse.resolveCampus(building.campusCode, fallbackCampus) }
    val periods = remember(campus) { GdutPeriods.forCampus(campus).sortedBy { it.index } }
    val pageCount = maxWeeks.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = (week - 1).coerceIn(0, pageCount - 1), pageCount = { pageCount })
    val scroll = rememberScrollState()

    LaunchedEffect(week, reduce) {
        val target = (week - 1).coerceIn(0, pageCount - 1)
        if (pager.currentPage != target && !pager.isScrollInProgress) {
            if (reduce) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager.settledPage) {
        val settledWeek = pager.settledPage + 1
        if (settledWeek != week) {
            onWeekChange(settledWeek)
        }
    }

    val currentWeekStart = weekStartFor(week)
    val currentWeekEnd = currentWeekStart.plusDays(6)

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val fallbackWidth =
        with(density) {
            val dmWidth = ctx.resources.displayMetrics.widthPixels
            if (dmWidth > 0) dmWidth.toDp() else screenWidthDp
        }
    var measuredWidthPx by remember { mutableIntStateOf(0) }
    val gridWidth = if (measuredWidthPx > 0) with(density) { measuredWidthPx.toDp() } else fallbackWidth

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactWeekCapsule(
                label =
                    stringResource(
                        R.string.classroom_week_capsule_range,
                        week,
                        currentWeekStart.monthValue,
                        currentWeekStart.dayOfMonth,
                        currentWeekEnd.monthValue,
                        currentWeekEnd.dayOfMonth,
                    ),
                canPrev = week > 1,
                canNext = week < maxWeeks,
                onPrev = { onWeekChange(week - 1) },
                onNext = { onWeekChange(week + 1) },
                modifier = Modifier.weight(1f),
            )
            ChipPill(
                label = stringResource(R.string.classroom_overlay_personal),
                on = overlayPersonal,
                role = Role.Checkbox,
                onClick = onToggleOverlay,
            )
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

        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { measuredWidthPx = it.width },
        ) { page ->
            val pageWeek = page + 1
            val pageWeekStart = remember(pageWeek) { weekStartFor(pageWeek) }
            val pageWeekOccupancy = occupancyForWeek(pageWeekStart)
            val occByDay =
                remember(pageWeekOccupancy, room.code, pageWeekStart) {
                    (1..7).associateWith { dow ->
                        val date = pageWeekStart.plusDays((dow - 1).toLong())
                        pageWeekOccupancy[date].orEmpty().filter { it.roomCode == room.code }
                    }
                }
            val personalItems =
                remember(snap, pageWeek, pageWeekStart, periods, overlayPersonal) {
                    if (!overlayPersonal) {
                        emptyList()
                    } else {
                        ClassroomGridHelper.personalOverlayForWeek(snap, pageWeek, pageWeekStart, periods)
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

            ClassroomWeekGrid(
                periods = periods,
                weekStart = pageWeekStart,
                today = today,
                occByDay = occByDay,
                personalItems = personalItems,
                gridWidth = gridWidth,
                scroll = scroll,
                modifier = Modifier.fillMaxSize(),
                onTapOccupancy = onTapOccupancy,
                onSelectBox = { days, firstP, lastP ->
                    onSelectBox(pageWeekStart, days, firstP, lastP, occByDay, personalBusyByDay)
                },
            )
        }
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
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    onTapOccupancy: (ClassroomOccupancy) -> Unit,
    onSelectBox: (days: IntRange, firstPeriod: Int, lastPeriod: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val localDensity = LocalDensity.current
    val fontScale = localDensity.fontScale.coerceIn(1f, 1.35f)
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
                        val slotLineH = 0.5.dp.toPx()
                        val groupLineH = 1.dp.toPx()
                        val slotLineColor = outline.copy(alpha = 0.25f)
                        val groupLineColor = outline.copy(alpha = 0.65f)
                        for (p in 1 until n) {
                            if (p !in boundaries) {
                                val y = (ClassroomGridHelper.calcPeriodTop(p, n, rowH, groupGap) + rowH).toPx()
                                drawRect(
                                    color = slotLineColor,
                                    topLeft = Offset(startX, y),
                                    size = Size(lineW, slotLineH),
                                )
                            }
                        }
                        boundaries.forEach { b ->
                            val y = (ClassroomGridHelper.calcPeriodTop(b, n, rowH, groupGap) + rowH + groupGap / 2).toPx()
                            drawRect(
                                color = groupLineColor,
                                topLeft = Offset(startX, y),
                                size = Size(lineW, groupLineH),
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
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    box = ClassroomBoxSel(anchorDay, anchorDay, anchorSlot, anchorSlot)
                                }
                            },
                            onDrag = { change, _ ->
                                val b = box ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                val d = colOf(change.position.x)
                                val e = slotOf(change.position.y).coerceIn(1, n)
                                val first = minOf(anchorSlot, e)
                                val last = maxOf(anchorSlot, e)
                                if (d != b.day2 || first != b.first || last != b.last) {
                                    box = b.copy(day2 = d, first = first, last = last)
                                }
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
                                .background(scheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                .border(0.8.dp, scheme.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
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
                        val cleanedTeacher = remember(occ.teacher) { ClassroomGridHelper.cleanTeacherLabel(occ.teacher) }
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
                            if (cleanedTeacher.isNotBlank()) {
                                Text(
                                    cleanedTeacher,
                                    style = blockSubStyle,
                                    color = fg.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // BoxSel highlight + floating time preview badge positioned via IntOffset
                box?.let { b ->
                    val boxAlpha by animateFloatAsState(1f, MintTheme.spec(), label = "classroom-box-sel")
                    val top = ClassroomGridHelper.calcPeriodTop(b.first, n, rowH, groupGap) + 2.dp
                    val bottom = ClassroomGridHelper.calcPeriodTop(b.last, n, rowH, groupGap) + rowH - 2.dp
                    val d1 = minOf(b.day1, b.day2)
                    val d2 = maxOf(b.day1, b.day2)
                    val boxW = colW * (d2 - d1 + 1) - 3.dp
                    val boxH = bottom - top
                    val boxXPx = with(localDensity) { (timeColW + colW * (d1 - 1) + 1.5.dp).toPx() }.roundToInt()
                    val boxYPx = with(localDensity) { top.toPx() }.roundToInt()

                    Box(
                        Modifier
                            .offset { IntOffset(boxXPx, boxYPx) }
                            .graphicsLayer { alpha = boxAlpha }
                            .width(boxW)
                            .height(boxH)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primary.copy(alpha = 0.28f))
                            .border(2.dp, scheme.primary, RoundedCornerShape(14.dp)),
                    )

                    val startP = periods.firstOrNull { it.index == b.first }
                    val endP = periods.firstOrNull { it.index == b.last }
                    if (startP != null && endP != null) {
                        val periodStr = if (b.first == b.last) "${b.first}" else "${b.first}–${b.last}"
                        val badgeText =
                            if (d1 == d2) {
                                ctx.getString(
                                    R.string.classroom_box_preview_single,
                                    weekdayShorts[d1 - 1],
                                    periodStr,
                                    startP.start.hm(),
                                    endP.end.hm(),
                                )
                            } else {
                                ctx.getString(
                                    R.string.classroom_box_preview_multi,
                                    weekdayShorts[d1 - 1],
                                    weekdayShorts[d2 - 1],
                                    periodStr,
                                    startP.start.hm(),
                                    endP.end.hm(),
                                )
                            }
                        val gridWPx = with(localDensity) { gridWidth.toPx() }.roundToInt()
                        val timeColPx = with(localDensity) { timeColW.toPx() }.roundToInt()
                        val boxWPx = with(localDensity) { boxW.toPx() }.roundToInt()
                        val boxHPx = with(localDensity) { boxH.toPx() }.roundToInt()
                        val gapPx = with(localDensity) { 6.dp.toPx() }.roundToInt()
                        var badgeW by remember { mutableIntStateOf(0) }
                        var badgeH by remember { mutableIntStateOf(0) }
                        val badgeX =
                            (boxXPx + (boxWPx - badgeW) / 2)
                                .coerceIn(timeColPx, (gridWPx - badgeW).coerceAtLeast(timeColPx))
                        val badgeY =
                            if (b.first > 1 && boxYPx - badgeH - gapPx >= 0) {
                                boxYPx - badgeH - gapPx
                            } else {
                                boxYPx + boxHPx + gapPx
                            }

                        Surface(
                            shape = CircleShape,
                            color = scheme.primary,
                            shadowElevation = 4.dp,
                            modifier =
                                Modifier
                                    .offset { IntOffset(badgeX, badgeY) }
                                    .onSizeChanged {
                                        badgeW = it.width
                                        badgeH = it.height
                                    },
                        ) {
                            Text(
                                badgeText,
                                style = labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun occupancyColorsAndBadge(type: OccupancyType): Triple<Color, Color, Int> {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        OccupancyType.COURSE -> Triple(scheme.primaryContainer.copy(alpha = 0.85f), scheme.onPrimaryContainer, R.string.classroom_badge_course)
        OccupancyType.BORROWED -> Triple(scheme.secondaryContainer.copy(alpha = 0.85f), scheme.onSecondaryContainer, R.string.classroom_badge_borrowed)
        OccupancyType.EXAM -> Triple(scheme.tertiaryContainer.copy(alpha = 0.85f), scheme.onTertiaryContainer, R.string.classroom_badge_exam)
        OccupancyType.LOCKED -> Triple(scheme.errorContainer.copy(alpha = 0.85f), scheme.onErrorContainer, R.string.classroom_badge_locked)
        OccupancyType.OTHER -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, R.string.classroom_badge_other)
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
