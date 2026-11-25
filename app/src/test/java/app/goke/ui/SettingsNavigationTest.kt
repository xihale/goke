package app.goke.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Course
import app.goke.core.model.ExceptionType
import app.goke.core.model.Source
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.WeekSets
import app.goke.core.util.Ids
import app.goke.ui.theme.GokeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class SettingsNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
        }
    }

    @Test
    fun allSettingsSubpagesExitViaTopBarAndSystemBack() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        val subpages =
            listOf(
                R.string.me_hub_bind_jxfw,
                R.string.grades_title,
                R.string.tzcs_title,
                R.string.classroom_title,
                R.string.xk_title,
                R.string.hotwater_title,
                R.string.parcel_title,
                R.string.appearance,
                R.string.widgets_title,
                R.string.prefs_title,
                R.string.reminders_title,
                R.string.sync_title,
                R.string.cal_sync_title,
                R.string.data_title,
                R.string.trash_title,
                R.string.history_title,
                R.string.lan_web_title,
                R.string.mcp_title,
                R.string.about_title,
            )

        for (titleRes in subpages) {
            val label = app.getString(titleRes)
            val list = rule.onAllNodes(hasScrollAction()).onLast()
            list.performScrollToNode(hasText(label))
            rule.waitForIdle()
            val listBounds = list.fetchSemanticsNode().boundsInRoot
            val nodeBounds = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            if (nodeBounds.bottom > listBounds.bottom - 180f) {
                list.performTouchInput {
                    swipeUp(startY = bottom * 0.6f, endY = bottom * 0.3f)
                }
                rule.waitForIdle()
            }
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()

            // Exit via TopBar back
            val backButtons = rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes()
            assertEquals("Expected back button after clicking $label", 1, backButtons.size)
            rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
            rule.waitForIdle()
            assertEquals("Failed to exit $label via top bar back", 0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)

            // Re-enter and exit via System Back
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.onBackPressed()
            }
            rule.waitForIdle()
            assertEquals("Failed to exit $label via system back", 0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun cancelledPredictiveBackDoesNotTrapUser() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.appearance)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.theme_color)).assertIsDisplayed()

        // Start predictive back and cancel it
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(50f, 100f, 0.3f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()

        // Should still be able to exit via predictive back after a cancelled gesture
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(50f, 100f, 0.7f, BackEventCompat.EDGE_LEFT))
            d.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
    }

    @Test
    fun nestedSettingsSubpagesExitCleanly() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        // Settings -> Grades -> Jxfw -> back -> back -> Settings
        rule.onNodeWithText(app.getString(R.string.grades_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.jxfw_card_bind_action)).performClick()
        rule.waitForIdle()

        // Exit Jxfw via TopBar back -> should return to Grades
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.grades_not_logged_in_title)).assertIsDisplayed()

        // Exit Grades via System back -> should return to Settings
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
        rule.onNodeWithText(app.getString(R.string.grades_title)).assertIsDisplayed()

        // Settings -> Reminders -> Reliability -> back -> back -> Settings
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(app.getString(R.string.reminders_title)))
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.reminders_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.reliability_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.remind_courses)).assertIsDisplayed()
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
    }

    @Test
    fun popDuringPredictiveBackDoesNotWedgeNavHost() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.data_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).assertIsDisplayed()

        // Start predictive back (sets NavHost's internal inPredictiveBack = true)
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(50f, 100f, 0.2f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()

        // Pop via TopBar back while predictive back is active -> drops backStack size to 1
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        // Gesture finishes/cancels when finger lifts
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        rule.waitForIdle()

        // Must exit DataScreen cleanly and NOT wedge NavHost!
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
        rule.onNodeWithText(app.getString(R.string.data_title)).assertIsDisplayed()

        // Re-entering DataScreen -> ImportScreen and exiting both must still work cleanly
        rule.onNodeWithText(app.getString(R.string.data_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.import_file)).onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.import_file)).onLast().assertIsDisplayed()
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
    }

    @Test
    fun subpageLocalBackHandlerTakesPrecedenceOverRouteHost() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.parcel_title)).performClick()
        rule.waitForIdle()

        // Open inner rules view inside ParcelScreen (has its own BackHandler(enabled = showRules))
        rule.onNodeWithText(app.getString(R.string.parcel_rules_manage)).performClick()
        rule.waitForIdle()

        // First system back must close rules overlay, NOT pop ParcelScreen
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.parcel_enable)).assertIsDisplayed()

        // Second system back must pop ParcelScreen back to Settings
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.parcel_enable)).fetchSemanticsNodes().size)
        rule.onNodeWithText(app.getString(R.string.parcel_title)).assertIsDisplayed()
    }

    @Test
    fun courseSheetOmitsQuickNoteAndExamToggleAndCourseEditExitsAfterCancelledBack() {
        val sem = app.repo.snapshot.value.currentSemester!!
        val today = LocalDate.now()
        val week = SemesterCalendar(sem).weekOf(today).coerceIn(1, sem.totalWeeks)
        val course =
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = "软件工程导论",
                teacher = "张老师",
                room = "教1-202",
                dayOfWeek = today.dayOfWeek.value,
                startPeriod = 1,
                endPeriod = 2,
                weeks = WeekSets.all(sem.totalWeeks) + week,
                source = Source.MANUAL,
            )
        runBlocking {
            app.repo.commit(ChangeSet(Ids.next(), "seed-course", Source.MANUAL, System.currentTimeMillis(), listOf(Change.Add(course))))
        }

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
            }
        }
        rule.waitForIdle()

        rule.onAllNodesWithText(course.name)[0].performClick()
        rule.waitForIdle()

        // 快捷预览抽屉不再包含「快速记一条…」与「设为考试」按钮
        rule.onNodeWithText(app.getString(R.string.edit)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("设为考试").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("快速记一条…").fetchSemanticsNodes().size)

        // 进入课程编辑页
        rule.onNodeWithText(app.getString(R.string.edit)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).assertIsDisplayed()

        // 先取消一次预测性返回，再正常返回，必须顺利退出回到课表
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(60f, 100f, 0.3f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(120f, 100f, 0.7f, BackEventCompat.EDGE_LEFT))
            d.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
    }

    @Test
    fun rescheduleEntersGridPickDirectlyAndCommitsTargetSlot() {
        val sem = app.repo.snapshot.value.currentSemester!!
        val today = LocalDate.now()
        val week = SemesterCalendar(sem).weekOf(today).coerceIn(1, sem.totalWeeks)
        val sourceDow = if (today.dayOfWeek.value <= 5) today.dayOfWeek.value else 1
        val targetDow = if (sourceDow < 5) sourceDow + 1 else 2
        val courseA =
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = "线性代数",
                room = "教2-101",
                dayOfWeek = sourceDow,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(week),
                source = Source.MANUAL,
            )
        val courseB =
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = "大学物理",
                room = "教3-301",
                dayOfWeek = targetDow,
                startPeriod = 3,
                endPeriod = 4,
                weeks = setOf(week),
                source = Source.MANUAL,
            )
        runBlocking {
            app.repo.commit(
                ChangeSet(
                    Ids.next(),
                    "seed-reschedule-courses",
                    Source.MANUAL,
                    System.currentTimeMillis(),
                    listOf(Change.Add(courseA), Change.Add(courseB)),
                ),
            )
        }

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
            }
        }
        rule.waitForIdle()

        // 1. 打开课程详情并点「调课」：应直接进入课表选位横幅模式，而非弹繁琐周/节网格弹窗
        rule.onAllNodesWithText(courseA.name)[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.reschedule)).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.reschedule_pick_banner, courseA.name)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.reschedule_confirm)).fetchSemanticsNodes().size)

        // 2. 直接在课表网格上点选目标时段所在的格子（此处用同周另一个课程格子模拟选定目标位置）
        rule.onAllNodesWithText(courseB.name)[0].performClick()
        rule.waitForIdle()

        // 3. 弹出精简确认框，含重选入口、冲突提示与确认调课按钮
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.reschedule_pick_banner, courseA.name)).fetchSemanticsNodes().size)
        rule.onNodeWithText(app.getString(R.string.reschedule_title, courseA.name)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.reschedule_pick_on_grid)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.conflict_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.reschedule_confirm)).performClick()
        rule.waitForIdle()

        val saved =
            app.repo.snapshot.value.exceptions
                .firstOrNull { it.deletedAt == null && it.courseId == courseA.id }
        assertEquals(ExceptionType.RESCHEDULE, saved?.type)
        assertEquals(week, saved?.newWeek)
        assertEquals(targetDow, saved?.newDayOfWeek)
        assertEquals(3, saved?.newStartPeriod)
        assertEquals(4, saved?.newEndPeriod)
    }
}
