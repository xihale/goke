package app.goke.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Course
import app.goke.core.model.Source
import app.goke.core.util.Ids
import app.goke.ui.theme.GokeTheme
import app.goke.widget.OverlayTarget
import app.goke.widget.WidgetOverlayRoot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 全量二级路由返回导航结构性回归测试：
 * - 通过反射校验 [Route] 的全部 sealed 子类与测试清单 1:1 对齐（新增路由未覆盖即直接失败）。
 * - 逐一验证 23 个二级路由的顶栏返回、系统返回、取消预测性返回后再返回、预测性返回中途点顶栏返回后重入再退。
 * - 验证嵌套栈（Grades -> Jxfw）弹栈顺序，以及 [CourseEditScreen] 非法周次时的放弃退出路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class AllRoutesNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    private val allRoutes: List<Route> =
        listOf(
            Route.EditTimetable,
            Route.Appearance,
            Route.Preferences,
            Route.Reminders,
            Route.Widgets,
            Route.Permission,
            Route.Trash,
            Route.History,
            Route.Parcel,
            Route.HotWater,
            Route.HotWaterBills,
            Route.Mcp,
            Route.LanWeb,
            Route.Periods,
            Route.About,
            Route.Data,
            Route.TimetableShare,
            Route.Sync,
            Route.CalendarSync,
            Route.Jxfw,
            Route.Grades,
            Route.PhysicalTest,
            Route.Classroom,
            Route.CourseSelection,
            Route.Reliability,
            Route.Import(uri = null),
            Route.CourseEdit(),
            Route.TodoEdit(),
        )

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
        }
    }

    @Test
    fun allSealedRouteSubclassesAreCoveredOneToOne() {
        val sealedSubclasses =
            checkNotNull(Route::class.java.permittedSubclasses) {
                "Route must be a sealed interface with JVM permittedSubclasses metadata"
            }.map { it.kotlin }
                .toSet()
        val coveredClasses = allRoutes.map { it::class }.toSet()

        assertEquals(
            "Every Route sealed subclass must appear exactly once in allRoutes",
            allRoutes.size,
            coveredClasses.size,
        )
        assertEquals(
            "allRoutes must have 1:1 coverage with Route sealed subclasses",
            sealedSubclasses,
            coveredClasses,
        )
    }

    @Test
    fun everyRouteExitsCleanlyAcrossAllBackSequences() {
        val nav = Nav(initialTab = Tab.TIMETABLE)
        val backDesc = app.getString(R.string.back)

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE, nav = nav) {}
            }
        }
        rule.waitForIdle()

        for (route in allRoutes) {
            val name = route::class.simpleName ?: route.toString()

            // (a) push -> TopBar Back exits to root
            rule.runOnUiThread { nav.push(route) }
            rule.waitForIdle()
            assertTrue("[$name] expected onRoute after push (a)", nav.onRoute)
            assertEquals(
                "[$name] expected exactly 1 TopBar back button",
                1,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )
            rule.onNodeWithContentDescription(backDesc).performClick()
            rule.waitForIdle()
            assertFalse("[$name] TopBar back failed to exit", nav.onRoute)
            assertEquals(
                "[$name] back button lingered after TopBar exit",
                0,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )

            // (b) push -> System Back exits to root
            rule.runOnUiThread { nav.push(route) }
            rule.waitForIdle()
            assertTrue("[$name] expected onRoute after push (b)", nav.onRoute)
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.onBackPressed()
            }
            rule.waitForIdle()
            assertFalse("[$name] System Back failed to exit", nav.onRoute)
            assertEquals(
                "[$name] back button lingered after System Back exit",
                0,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )

            // (c) push -> cancelled predictive back -> System Back exits
            rule.runOnUiThread { nav.push(route) }
            rule.waitForIdle()
            assertTrue("[$name] expected onRoute after push (c)", nav.onRoute)
            rule.runOnUiThread {
                val d = rule.activity.onBackPressedDispatcher
                d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
                d.dispatchOnBackProgressed(BackEventCompat(60f, 100f, 0.3f, BackEventCompat.EDGE_LEFT))
                d.dispatchOnBackCancelled()
            }
            rule.waitForIdle()
            assertTrue("[$name] cancelled predictive back must not pop route", nav.onRoute)
            rule.runOnUiThread {
                val d = rule.activity.onBackPressedDispatcher
                d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
                d.dispatchOnBackProgressed(BackEventCompat(120f, 100f, 0.7f, BackEventCompat.EDGE_LEFT))
                d.onBackPressed()
            }
            rule.waitForIdle()
            assertFalse("[$name] System Back after cancelled predictive back failed to exit", nav.onRoute)
            assertEquals(
                "[$name] back button lingered after (c)",
                0,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )

            // (d) push -> TopBar Back mid-predictive-back -> cancel -> re-enter -> exit
            rule.runOnUiThread { nav.push(route) }
            rule.waitForIdle()
            assertTrue("[$name] expected onRoute after push (d)", nav.onRoute)
            rule.runOnUiThread {
                val d = rule.activity.onBackPressedDispatcher
                d.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0.0f, BackEventCompat.EDGE_LEFT))
                d.dispatchOnBackProgressed(BackEventCompat(70f, 100f, 0.35f, BackEventCompat.EDGE_LEFT))
            }
            rule.onNodeWithContentDescription(backDesc).performClick()
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            rule.waitForIdle()
            assertFalse("[$name] TopBar back mid-predictive-back failed to exit", nav.onRoute)
            assertEquals(
                "[$name] back button lingered after mid-predictive-back exit",
                0,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )

            // Re-enter and exit cleanly
            rule.runOnUiThread { nav.push(route) }
            rule.waitForIdle()
            assertTrue("[$name] expected onRoute after re-enter", nav.onRoute)
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.onBackPressed()
            }
            rule.waitForIdle()
            assertFalse("[$name] failed to exit after re-enter", nav.onRoute)
            assertEquals(
                "[$name] back button lingered after re-enter exit",
                0,
                rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    fun nestedStackPopsInExactLifoOrder() {
        val nav = Nav(initialTab = Tab.ME)
        val backDesc = app.getString(R.string.back)

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME, nav = nav) {}
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread { nav.push(Route.Grades) }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades), nav.stack.map { it.route })
        assertEquals(1, nav.topEntry?.depth)

        rule.runOnUiThread { nav.push(Route.Jxfw) }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades, Route.Jxfw), nav.stack.map { it.route })
        assertEquals(2, nav.topEntry?.depth)

        // First System Back pops only Jxfw -> Grades remains active
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades), nav.stack.map { it.route })
        assertEquals(1, rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size)

        // Second System Back pops Grades -> back to Tabs root
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue(nav.stack.isEmpty())
        assertEquals(0, rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size)
    }

    @Test
    fun courseEditInvalidWeeksOffersDiscardExitInsteadOfTrapping() {
        val sem = app.repo.snapshot.value.currentSemester!!
        val brokenCourse =
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = "异常周次课程",
                room = "教1-101",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = emptySet(),
                source = Source.MANUAL,
            )
        runBlocking {
            app.repo.commit(
                ChangeSet(
                    Ids.next(),
                    "seed-invalid-week-course",
                    Source.MANUAL,
                    System.currentTimeMillis(),
                    listOf(Change.Add(brokenCourse)),
                ),
            )
        }

        val nav = Nav(initialTab = Tab.TIMETABLE)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE, nav = nav) {}
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread { nav.push(Route.CourseEdit(id = brokenCourse.id)) }
        rule.waitForIdle()
        assertTrue(nav.onRoute)

        // Make form dirty while weeks remain empty (!valid)
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("修改")
        rule.waitForIdle()

        // System Back must open confirm-discard dialog rather than trapping forever
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard_title)).assertIsDisplayed()

        // Confirm discard exits cleanly
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard)).performClick()
        rule.waitForIdle()
        assertFalse(nav.onRoute)
        assertEquals(
            0,
            rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun predictiveBackFromRightEdgePopsNestedStackCleanly() {
        val nav = Nav(initialTab = Tab.ME)
        val backDesc = app.getString(R.string.back)

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME, nav = nav) {}
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            nav.push(Route.Grades)
            nav.push(Route.Jxfw)
        }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades, Route.Jxfw), nav.stack.map { it.route })

        // Right-edge predictive back cancel must keep Jxfw on top
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(400f, 300f, 0.0f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackProgressed(BackEventCompat(320f, 310f, 0.4f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades, Route.Jxfw), nav.stack.map { it.route })

        // Right-edge predictive back commit pops only Jxfw -> Grades remains
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(400f, 300f, 0.0f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackProgressed(BackEventCompat(250f, 315f, 0.65f, BackEventCompat.EDGE_RIGHT))
            d.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(listOf(Route.Grades), nav.stack.map { it.route })
        assertEquals(1, rule.onAllNodesWithContentDescription(backDesc).fetchSemanticsNodes().size)
    }

    @Test
    fun nonDefaultTabPredictiveBackReturnsToTimetableOnCommitOnly() {
        val nav = Nav(initialTab = Tab.ME)

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME, nav = nav) {}
            }
        }
        rule.waitForIdle()
        assertEquals(Tab.ME, nav.tab)

        // Cancelled predictive back keeps Tab.ME
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(80f, 205f, 0.35f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        assertEquals(Tab.ME, nav.tab)

        // Committed predictive back returns to Tab.TIMETABLE
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(160f, 210f, 0.7f, BackEventCompat.EDGE_LEFT))
            d.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(Tab.TIMETABLE, nav.tab)
    }

    @Test
    fun dirtyExistingCourseEditAutoSavesOnPredictiveBackCommitNotCancel() {
        val sem = app.repo.snapshot.value.currentSemester!!
        val course =
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = "编译原理",
                room = "教1-303",
                dayOfWeek = 2,
                startPeriod = 1,
                endPeriod = 2,
                weeks = setOf(1, 2, 3),
                source = Source.MANUAL,
            )
        runBlocking {
            app.repo.commit(
                ChangeSet(
                    Ids.next(),
                    "seed-valid-course",
                    Source.MANUAL,
                    System.currentTimeMillis(),
                    listOf(Change.Add(course)),
                ),
            )
        }

        val nav = Nav(initialTab = Tab.TIMETABLE)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE, nav = nav) {}
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread { nav.push(Route.CourseEdit(id = course.id)) }
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("实验")
        rule.waitForIdle()

        // Cancelled predictive back must not commit or exit
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(60f, 200f, 0.3f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        assertTrue(nav.onRoute)
        assertEquals(
            "编译原理",
            app.repo.snapshot.value.courses
                .first { it.id == course.id }
                .name,
        )

        // Committed predictive back auto-saves edit and pops route
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(140f, 200f, 0.7f, BackEventCompat.EDGE_LEFT))
            d.onBackPressed()
        }
        rule.waitForIdle()
        assertFalse(nav.onRoute)
        rule.waitUntil(3_000) {
            app.repo.snapshot.value.courses
                .first { it.id == course.id }
                .name != "编译原理"
        }
    }

    @Test
    fun widgetOverlayPredictiveBackCancelsCleanlyAndCommitsAnimatedDismiss() {
        val sem = app.repo.snapshot.value.currentSemester!!
        val course =
            app.repo.snapshot.value.courses
                .firstOrNull()
                ?: Course(
                    id = Ids.next(),
                    semesterId = sem.id,
                    name = "操作系统",
                    room = "教1-102",
                    dayOfWeek = 1,
                    startPeriod = 1,
                    endPeriod = 2,
                    weeks = setOf(1, 2, 3),
                    source = Source.MANUAL,
                ).also { c ->
                    runBlocking {
                        app.repo.commit(
                            ChangeSet(Ids.next(), "seed-overlay-course", Source.MANUAL, System.currentTimeMillis(), listOf(Change.Add(c))),
                        )
                    }
                }
        val visible = androidx.compose.runtime.mutableStateOf(true)
        var exited = false

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            val curSnap by app.repo.snapshot.collectAsState()
            GokeTheme(st, transparentBackground = true) {
                WidgetOverlayRoot(
                    app = app,
                    ready = true,
                    snap = curSnap,
                    target = OverlayTarget.CourseDetail(course.id),
                    onChangeTarget = {},
                    onOpenInApp = {},
                    onDismiss = { visible.value = false },
                    visible = visible.value,
                    onExited = { if (!visible.value) exited = true },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(course.name).assertIsDisplayed()

        // Cancelled predictive back keeps widget overlay open
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0.0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(80f, 400f, 0.35f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        assertTrue(visible.value)
        assertFalse(exited)
        rule.onNodeWithText(course.name).assertIsDisplayed()

        // Committed predictive back animates out and invokes onExited
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0.0f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackProgressed(BackEventCompat(320f, 400f, 0.7f, BackEventCompat.EDGE_RIGHT))
            d.onBackPressed()
        }
        rule.waitUntil(3_000) { exited }
        assertFalse(visible.value)
        assertTrue(exited)
    }
}
