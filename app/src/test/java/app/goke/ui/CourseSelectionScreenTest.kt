package app.goke.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.jxfw.XkConflict
import app.goke.core.jxfw.XkConflictSource
import app.goke.core.jxfw.XkCourseDetail
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.jxfw.XkCreditWallet
import app.goke.core.jxfw.XkExtraMeta
import app.goke.core.jxfw.XkFailureKind
import app.goke.core.jxfw.XkOpenPlan
import app.goke.core.jxfw.XkScheduleSummary
import app.goke.core.jxfw.XkWindowStatus
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.ui.screens.CourseSelectionContent
import app.goke.ui.screens.formatXkFailure
import app.goke.ui.theme.GokeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class CourseSelectionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Composable
    private fun TestHost(content: @Composable () -> Unit) {
        val st by app.settings.flow.collectAsState()
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()
        val nav = remember(navController) { Nav(navController) }
        val actions = remember { Actions(app, scope, nav) }
        GokeTheme(st) {
            CompositionLocalProvider(
                LocalApp provides app,
                LocalActions provides actions,
                LocalNav provides nav,
            ) {
                content()
            }
        }
    }

    @Test
    fun notLoggedInShowsBindGate() {
        var openedJxfw = false
        rule.setContent {
            TestHost {
                CourseSelectionContent(
                    loggedIn = false,
                    xkState = JxfwCourseSelectionState(),
                    onBack = {},
                    onOpenJxfw = { openedJxfw = true },
                    onRefresh = {},
                    onSearch = { _, _ -> },
                    onLoadMore = {},
                    onLoadDetail = { Result.failure(IllegalStateException("unused")) },
                    onEnroll = {},
                    onDrop = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.xk_not_logged_in_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.jxfw_card_bind_action)).performClick()
        assertTrue(openedJxfw)
    }

    @Test
    fun closedWindowShowsNoticeAndOpenPlans() {
        val closedState =
            JxfwCourseSelectionState(
                window =
                    XkWindowStatus(
                        xkjd = "0",
                        xnxqdm = "-1",
                        canDrop = false,
                        headerNotice = "当前未开放全校公选课",
                        openPlans =
                            listOf(
                                XkOpenPlan(
                                    jxjhdm = "1",
                                    planType = "公选",
                                    planName = "2026秋季通识选修",
                                    startTime = "2026-09-01 09:00",
                                    endTime = "2026-09-05 17:00",
                                ),
                            ),
                    ),
            )
        rule.setContent {
            TestHost {
                CourseSelectionContent(
                    loggedIn = true,
                    xkState = closedState,
                    onBack = {},
                    onOpenJxfw = {},
                    onRefresh = {},
                    onSearch = { _, _ -> },
                    onLoadMore = {},
                    onLoadDetail = { Result.failure(IllegalStateException("unused")) },
                    onEnroll = {},
                    onDrop = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.xk_window_closed_title)).assertIsDisplayed()
        rule.onNodeWithText("当前未开放全校公选课").assertIsDisplayed()
        rule.onNodeWithText("2026秋季通识选修").assertIsDisplayed()
    }

    @Test
    fun openWindowSupportsFiltersDetailSheetAndConfirmDialogs() {
        val conflictItem =
            XkCourseItem(
                kcrwdm = "rw1",
                kcdm = "CS101",
                kcptdm = "pt1",
                name = "人工智能导论",
                credit = 2.0,
                teacher = "张老师",
                capacity = 60,
                enrolledCount = 50,
                category = "通识教育选修",
                classGroup = "26人工智能1班",
                campusHint = "大学城",
                scheduleSummary =
                    listOf(
                        XkScheduleSummary(
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            weeks = setOf(1, 2, 3, 4),
                            room = "教3-201",
                            campusHint = "大学城",
                        ),
                    ),
                conflicts =
                    listOf(
                        XkConflict(
                            courseName = "高等数学",
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            weeks = setOf(1, 2),
                            source = XkConflictSource.JXFW,
                        ),
                    ),
            )
        val fullItem =
            XkCourseItem(
                kcrwdm = "rw2",
                kcdm = "ART202",
                name = "音乐鉴赏",
                credit = 1.5,
                teacher = "李老师",
                capacity = 40,
                enrolledCount = 40,
                category = "艺术素养",
            )
        val enrolledItem =
            XkCourseItem(
                kcrwdm = "rw3",
                kcdm = "HIS101",
                name = "中国近代史纲要",
                credit = 2.0,
                teacher = "王老师",
                enrolled = true,
            )
        val state =
            JxfwCourseSelectionState(
                window =
                    XkWindowStatus(
                        xkjd = "1",
                        xnxqdm = "202601",
                        isCreditWallet = true,
                        canDrop = true,
                        maxCourses = 3,
                    ),
                wallet = XkCreditWallet(totalCredits = 10.0, usedCredits = 2.0),
                available = listOf(conflictItem, fullItem),
                availableTotal = 2,
                enrolled = listOf(enrolledItem),
            )

        var enrolledTarget: XkCourseItem? = null
        var droppedTarget: XkCourseItem? = null

        rule.setContent {
            TestHost {
                CourseSelectionContent(
                    loggedIn = true,
                    xkState = state,
                    onBack = {},
                    onOpenJxfw = {},
                    onRefresh = {},
                    onSearch = { _, _ -> },
                    onLoadMore = {},
                    onLoadDetail = {
                        Result.success(
                            XkCourseDetail(
                                kcrwdm = it.kcrwdm,
                                zhSyllabus = "介绍人工智能基础理论与应用。",
                                enSyllabus = "Introduction to AI.",
                                extra =
                                    XkExtraMeta(
                                        courseCode = "CS101",
                                        englishName = "Introduction to Artificial Intelligence",
                                        studyMode = "选修",
                                        plannedHours = "32",
                                        gradingMode = "考查",
                                    ),
                            ),
                        )
                    },
                    onEnroll = { enrolledTarget = it },
                    onDrop = { droppedTarget = it },
                )
            }
        }
        rule.waitForIdle()

        // Wallet & conflict badge visible
        rule.onNodeWithText(app.getString(R.string.xk_wallet_title)).assertIsDisplayed()
        rule.onNodeWithText("人工智能导论").assertIsDisplayed()
        rule.onNodeWithText("音乐鉴赏").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_conflict_badge, 1)).assertIsDisplayed()

        // Filter: only no conflict hides conflictItem
        rule.onNodeWithText(app.getString(R.string.xk_filter_no_conflict)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("人工智能导论").assertDoesNotExist()
        rule.onNodeWithText("音乐鉴赏").assertIsDisplayed()

        // Reset filter & open detail sheet
        rule.onNodeWithText(app.getString(R.string.xk_filter_no_conflict)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("人工智能导论").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Introduction to Artificial Intelligence").assertIsDisplayed()
        rule.onNodeWithText("介绍人工智能基础理论与应用。").assertIsDisplayed()

        // Enroll from detail sheet -> triggers conflict ConfirmDialog
        rule.onAllNodesWithText(app.getString(R.string.xk_action_enroll)).onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.xk_confirm_enroll_title)).assertIsDisplayed()
        rule.onAllNodesWithText(app.getString(R.string.xk_action_enroll)).onLast().performClick()
        rule.waitForIdle()
        assertEquals("rw1", enrolledTarget?.kcrwdm)

        // Switch to Enrolled tab and trigger drop ConfirmDialog
        rule.onNodeWithText(app.getString(R.string.xk_tab_enrolled_count, 1)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("中国近代史纲要").assertIsDisplayed()
        rule.onAllNodesWithText(app.getString(R.string.xk_action_drop)).onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.xk_confirm_drop_title)).assertIsDisplayed()
        rule.onAllNodesWithText(app.getString(R.string.xk_action_drop)).onLast().performClick()
        rule.waitForIdle()
        assertEquals("rw3", droppedTarget?.kcrwdm)
    }

    @Test
    fun failureKindMapsToClearMessages() {
        assertEquals(app.getString(R.string.xk_err_full), formatXkFailure(app, XkFailureKind.FULL, ""))
        assertEquals(app.getString(R.string.xk_err_conflict), formatXkFailure(app, XkFailureKind.CONFLICT, ""))
        assertEquals(app.getString(R.string.xk_err_credit_limit), formatXkFailure(app, XkFailureKind.CREDIT_LIMIT, ""))
        assertEquals(app.getString(R.string.xk_err_out_of_window), formatXkFailure(app, XkFailureKind.OUT_OF_WINDOW, ""))
        assertEquals(app.getString(R.string.xk_err_already_enrolled), formatXkFailure(app, XkFailureKind.ALREADY_ENROLLED, ""))
        assertEquals(app.getString(R.string.xk_err_not_droppable), formatXkFailure(app, XkFailureKind.NOT_DROPPABLE, ""))
        assertEquals(app.getString(R.string.xk_err_other, "网络超时"), formatXkFailure(app, XkFailureKind.OTHER, "网络超时"))
    }

    @Test
    fun nullWindowErrorHidesOpenTabs() {
        rule.setContent {
            TestHost {
                CourseSelectionContent(
                    loggedIn = true,
                    xkState = JxfwCourseSelectionState(lastError = "网络异常"),
                    onBack = {},
                    onOpenJxfw = {},
                    onRefresh = {},
                    onSearch = { _, _ -> },
                    onLoadMore = {},
                    onLoadDetail = { Result.failure(IllegalStateException("unused")) },
                    onEnroll = {},
                    onDrop = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("网络异常").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_retry)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_tab_available)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.xk_search_action)).assertDoesNotExist()
    }

    @Test
    fun searchKeyUsesSupportedFieldNames() {
        var lastKey: String? = null
        var lastValue: String? = null
        val state =
            JxfwCourseSelectionState(
                window = XkWindowStatus(xkjd = "1", xnxqdm = "202601"),
                searchKey = "kcmc",
                searchValue = "数学",
            )
        rule.setContent {
            TestHost {
                CourseSelectionContent(
                    loggedIn = true,
                    xkState = state,
                    onBack = {},
                    onOpenJxfw = {},
                    onRefresh = {},
                    onSearch = { k, v ->
                        lastKey = k
                        lastValue = v
                    },
                    onLoadMore = {},
                    onLoadDetail = { Result.failure(IllegalStateException("unused")) },
                    onEnroll = {},
                    onDrop = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.xk_search_key_teacher)).performClick()
        rule.onNodeWithText(app.getString(R.string.xk_search_action)).performClick()
        rule.waitForIdle()
        assertEquals("teaxm", lastKey)
        assertEquals("数学", lastValue)

        rule.onNodeWithText(app.getString(R.string.xk_search_key_category)).performClick()
        rule.onNodeWithText(app.getString(R.string.xk_search_action)).performClick()
        rule.waitForIdle()
        assertEquals("kcflmc", lastKey)
    }
}
