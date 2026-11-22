package app.goke.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.SmokeTestApp
import app.goke.core.jxfw.ClassroomBuilding
import app.goke.core.jxfw.ClassroomOccupancy
import app.goke.core.jxfw.OccupancyType
import app.goke.core.model.GdutPeriods
import app.goke.data.Settings
import app.goke.ui.screens.ClassroomGridHelper
import app.goke.ui.screens.TodoEditScreen
import app.goke.ui.theme.GokeTheme
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
    fun boxSelectionMapsPeriodsToUnifiedDatetimesAndExtraDates() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val sel =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                periods = GdutPeriods.default,
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

        val afternoon =
            ClassroomGridHelper.resolveClassroomBoxSelection(
                periods = GdutPeriods.default,
                weekStart = weekStart,
                days = 1..1,
                firstPeriod = 5,
                lastPeriod = 5,
                roomName = "实验楼-202",
            )!!
        assertEquals("2026-09-07T13:50", afternoon.start)
        assertEquals("2026-09-07T14:35", afternoon.end)
        assertNull(afternoon.extraDates)
    }

    @Test
    fun defaultCampusNamePrefersHemcAndFallsBackToFirstNonBlank() {
        val buildings =
            listOf(
                ClassroomBuilding(code = "0029", name = "教学楼", campusCode = "2", campusName = "龙洞校区"),
                ClassroomBuilding(code = "0005", name = "教学三号楼(城)", campusCode = "1", campusName = "大学城校区"),
            )
        assertEquals("大学城校区", ClassroomGridHelper.defaultCampusName(buildings))
        assertEquals("龙洞校区", ClassroomGridHelper.defaultCampusName(buildings.take(1)))
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
                periods = GdutPeriods.default,
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
        val app = ApplicationProvider.getApplicationContext<GokeApp>()
        val route = Route.TodoEdit(id = null, location = "教3-101")
        rule.setContent {
            val nav = rememberNav()
            val scope = rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            GokeTheme(settings = Settings()) {
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

    @Test
    fun cleanTeacherLabelStripsRoleTags() {
        assertEquals("张三", ClassroomGridHelper.cleanTeacherLabel("张三[主讲]"))
        assertEquals("李四", ClassroomGridHelper.cleanTeacherLabel("李四 [辅讲]"))
        assertEquals("张三, 李四", ClassroomGridHelper.cleanTeacherLabel("张三[主讲], 李四[辅讲]"))
        assertEquals("王五", ClassroomGridHelper.cleanTeacherLabel("王五"))
        assertEquals("", ClassroomGridHelper.cleanTeacherLabel(""))
    }
}
