package app.mintcal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.mintcal.MintCalApp
import app.mintcal.SmokeTestApp
import app.mintcal.core.jxfw.ClassroomOccupancy
import app.mintcal.core.jxfw.OccupancyType
import app.mintcal.core.model.GdutCampus
import app.mintcal.data.Settings
import app.mintcal.ui.screens.ClassroomGridHelper
import app.mintcal.ui.screens.TodoEditScreen
import app.mintcal.ui.theme.MintCalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class ClassroomGridHelperTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun boxSelectionMapsPeriodsToCampusDatetimesAndExtraDates() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val sel =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                buildingCampusCode = "1",
                fallbackCampus = GdutCampus.LONGDONG,
                weekStart = weekStart,
                days = 2..4,
                firstPeriod = 1,
                lastPeriod = 2,
                roomName = "教3-101",
            )!!
        assertEquals("2026-09-08", sel.date)
        assertEquals("2026-09-08T08:30", sel.start)
        assertEquals("2026-09-08T10:05", sel.end)
        assertEquals("2026-09-09,2026-09-10", sel.extraDates)
        assertEquals("教3-101", sel.location)
        assertFalse(sel.overlapsOccupancy)
        assertFalse(sel.overlapsPersonal)
    }

    @Test
    fun boxSelectionUsesCampusBellAndFallsBackForUnknownCampus() {
        val weekStart = LocalDate.of(2026, 9, 7)
        // 龙洞校区下午第 5 节 13:30–14:15
        val longdong =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                buildingCampusCode = "2",
                fallbackCampus = GdutCampus.HEMC,
                weekStart = weekStart,
                days = 1..1,
                firstPeriod = 5,
                lastPeriod = 5,
                roomName = "实验楼-202",
            )!!
        assertEquals("2026-09-07T13:30", longdong.start)
        assertEquals("2026-09-07T14:15", longdong.end)
        assertNull(longdong.extraDates)

        // 番禺校区("4")未知作息，回退到 fallbackCampus (东风路 14:00–14:45)
        val fallback =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                buildingCampusCode = "4",
                fallbackCampus = GdutCampus.DONGFENG,
                weekStart = weekStart,
                days = 3..3,
                firstPeriod = 5,
                lastPeriod = 5,
                roomName = "番禺-101",
            )!!
        assertEquals("2026-09-09T14:00", fallback.start)
        assertEquals("2026-09-09T14:45", fallback.end)
    }

    @Test
    fun boxSelectionDetectsOccupancyAndPersonalOverlap() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val occ =
            ClassroomOccupancy(
                id = "occ-1",
                roomCode = "R1",
                periods = listOf(3, 4),
                type = OccupancyType.BORROWED,
            )
        val sel =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                buildingCampusCode = "1",
                fallbackCampus = GdutCampus.HEMC,
                weekStart = weekStart,
                days = 2..2,
                firstPeriod = 2,
                lastPeriod = 3,
                roomName = "教1-101",
                roomOccupanciesByDay = mapOf(2 to listOf(occ)),
                personalBusyByDay = mapOf(2 to setOf(2)),
            )!!
        assertTrue(sel.overlapsOccupancy)
        assertTrue(sel.overlapsPersonal)
    }

    @Test
    fun freeRoomFilteringTreatsAllOccupancyTypesAsBusy() {
        val occupancies =
            listOf(
                ClassroomOccupancy(id = "1", roomCode = "R_COURSE", periods = listOf(1, 2), type = OccupancyType.COURSE),
                ClassroomOccupancy(id = "2", roomCode = "R_BORROWED", periods = listOf(3, 4), type = OccupancyType.BORROWED),
                ClassroomOccupancy(id = "3", roomCode = "R_LOCKED", periods = listOf(5), type = OccupancyType.LOCKED),
                ClassroomOccupancy(id = "4", roomCode = "R_EXAM", periods = listOf(6, 7), type = OccupancyType.EXAM),
            )
        assertFalse(ClassroomGridHelper.isRoomFree(occupancies, "R_COURSE", setOf(1, 2)))
        assertTrue(ClassroomGridHelper.isRoomFree(occupancies, "R_COURSE", setOf(3, 4)))

        assertFalse(ClassroomGridHelper.isRoomFree(occupancies, "R_BORROWED", setOf(3)))
        assertFalse(ClassroomGridHelper.isRoomFree(occupancies, "R_LOCKED", setOf(5)))
        assertFalse(ClassroomGridHelper.isRoomFree(occupancies, "R_EXAM", setOf(6, 7)))
        assertTrue(ClassroomGridHelper.isRoomFree(occupancies, "R_FREE", setOf(1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun todoEditPrefillsLocationWhenCreatingNewEvent() {
        val app = ApplicationProvider.getApplicationContext<MintCalApp>()
        val route = Route.TodoEdit(id = null, location = "教3-101")
        rule.setContent {
            val nav = rememberNav()
            val scope = rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            MintCalTheme(settings = Settings()) {
                CompositionLocalProvider(
                    LocalApp provides app,
                    LocalActions provides actions,
                    LocalNav provides nav,
                ) {
                    TodoEditScreen(
                        id = route.id,
                        locationArg = route.location,
                        onBack = {},
                    )
                }
            }
        }
        rule.onNodeWithText("教3-101").assertIsDisplayed()
    }
}
