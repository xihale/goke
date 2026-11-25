package app.goke.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.goke.core.jxfw.XkOccupiedSlot
import app.goke.core.jxfw.XkOpenPlan
import app.goke.core.jxfw.XkScheduleSummary
import app.goke.core.jxfw.XkSessionSlot
import app.goke.core.jxfw.XkSlotConflict
import app.goke.core.jxfw.XkWindowStatus
import app.goke.data.DarkMode
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class CourseSelectionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Composable
    private fun TestHost(
        darkMode: DarkMode = DarkMode.LIGHT,
        content: @Composable () -> Unit,
    ) {
        val st by app.settings.flow.collectAsState()
        val scope = rememberCoroutineScope()
        val nav = rememberNav()
        val actions = remember { Actions(app, scope, nav) }
        GokeTheme(st.copy(darkMode = darkMode)) {
            CompositionLocalProvider(
                LocalApp provides app,
                LocalActions provides actions,
                LocalNav provides nav,
            ) {
                content()
            }
        }
    }

    private fun saveShot(
        name: String,
        preferDialog: Boolean = false,
    ) {
        val dir = File("build/shots").apply { mkdirs() }
        val dialogs = if (preferDialog) rule.onAllNodes(isDialog()).fetchSemanticsNodes() else emptyList()
        val bmp =
            if (dialogs.isNotEmpty()) {
                rule
                    .onAllNodes(isDialog())
                    .onLast()
                    .captureToImage()
                    .asAndroidBitmap()
            } else {
                rule.onRoot().captureToImage().asAndroidBitmap()
            }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
        rule.onNodeWithText(app.getString(R.string.xk_exp_badge)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_exp_disclaimer)).assertIsDisplayed()
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
                sessions =
                    listOf(
                        XkSessionSlot(
                            kcrwdm = "rw1",
                            week = 1,
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            room = "教3-201",
                        ),
                        XkSessionSlot(
                            kcrwdm = "rw1",
                            week = 2,
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            room = "教3-201",
                        ),
                        XkSessionSlot(
                            kcrwdm = "rw1",
                            week = 3,
                            dayOfWeek = 3,
                            periods = listOf(5, 6),
                            room = "教3-201",
                        ),
                    ),
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
                slotConflicts =
                    listOf(
                        XkSlotConflict(
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            weeks = setOf(1, 2),
                            courseName = "高等数学",
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
                occupiedSlots =
                    listOf(
                        XkOccupiedSlot(1, 1, 1, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(1, 1, 2, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(2, 1, 1, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(2, 1, 2, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(1, 2, 3, "大学物理", XkConflictSource.LOCAL_TIMETABLE),
                        XkOccupiedSlot(1, 2, 4, "大学物理", XkConflictSource.LOCAL_TIMETABLE),
                    ),
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

        // Wallet, EXP notice & conflict badge visible
        rule.onNodeWithText(app.getString(R.string.xk_exp_badge)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_exp_disclaimer)).assertIsDisplayed()
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
        rule.onAllNodesWithText(app.getString(R.string.xk_detail_label_teacher)).onLast().assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_preview_section)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_preview_legend_existing)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.xk_preview_legend_candidate)).assertIsDisplayed()
        rule.onAllNodesWithText(app.getString(R.string.xk_preview_legend_conflict)).onFirst().assertIsDisplayed()
        rule.onNodeWithText("介绍人工智能基础理论与应用。").performScrollTo().assertIsDisplayed()

        // Enroll from detail sheet -> triggers conflict ConfirmDialog
        rule
            .onAllNodesWithText(app.getString(R.string.xk_action_enroll))
            .onLast()
            .performScrollTo()
            .performClick()
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
        rule.onNodeWithContentDescription(app.getString(R.string.xk_search_cd)).assertDoesNotExist()
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
        rule.onNodeWithContentDescription(app.getString(R.string.xk_search_cd)).performClick()
        rule.waitForIdle()
        assertEquals("teaxm", lastKey)
        assertEquals("数学", lastValue)

        rule.onNodeWithText(app.getString(R.string.xk_search_key_category)).performClick()
        rule.onNodeWithContentDescription(app.getString(R.string.xk_search_cd)).performClick()
        rule.waitForIdle()
        assertEquals("kcflmc", lastKey)
    }

    @Test
    fun captureCourseSelectionScreenshots() {
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
                sessions =
                    listOf(
                        XkSessionSlot("rw1", 1, 1, listOf(1, 2), "教3-201"),
                        XkSessionSlot("rw1", 2, 1, listOf(1, 2), "教3-201"),
                        XkSessionSlot("rw1", 1, 3, listOf(5, 6), "教3-201"),
                        XkSessionSlot("rw1", 2, 3, listOf(5, 6), "教3-201"),
                    ),
                scheduleSummary =
                    listOf(
                        XkScheduleSummary(1, listOf(1, 2), setOf(1, 2), "教3-201", "大学城"),
                        XkScheduleSummary(3, listOf(5, 6), setOf(1, 2), "教3-201", "大学城"),
                    ),
                conflicts =
                    listOf(
                        XkConflict("高等数学", 1, listOf(1, 2), setOf(1, 2), XkConflictSource.JXFW),
                    ),
                slotConflicts =
                    listOf(
                        XkSlotConflict(1, listOf(1, 2), setOf(1, 2), "高等数学", XkConflictSource.JXFW),
                    ),
            )
        val cleanItem =
            XkCourseItem(
                kcrwdm = "rw2",
                kcdm = "ART202",
                name = "音乐鉴赏",
                credit = 1.5,
                teacher = "李老师",
                capacity = 40,
                enrolledCount = 32,
                category = "艺术素养",
                campusHint = "大学城",
                sessions = listOf(XkSessionSlot("rw2", 1, 4, listOf(9, 10), "实验楼-105")),
                scheduleSummary = listOf(XkScheduleSummary(4, listOf(9, 10), setOf(1, 2), "实验楼-105", "大学城")),
            )
        val openState =
            JxfwCourseSelectionState(
                window = XkWindowStatus(xkjd = "1", xnxqdm = "202601", isCreditWallet = true, canDrop = true, maxCourses = 3),
                wallet = XkCreditWallet(totalCredits = 10.0, usedCredits = 2.0),
                available = listOf(conflictItem, cleanItem),
                availableTotal = 2,
                occupiedSlots =
                    listOf(
                        XkOccupiedSlot(1, 1, 1, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(1, 1, 2, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(2, 1, 1, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(2, 1, 2, "高等数学", XkConflictSource.JXFW),
                        XkOccupiedSlot(1, 2, 3, "大学物理", XkConflictSource.LOCAL_TIMETABLE),
                        XkOccupiedSlot(1, 2, 4, "大学物理", XkConflictSource.LOCAL_TIMETABLE),
                    ),
            )
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

        var darkModeState = androidx.compose.runtime.mutableStateOf(DarkMode.LIGHT)
        var xkStateHolder = androidx.compose.runtime.mutableStateOf(closedState)

        rule.setContent {
            TestHost(darkMode = darkModeState.value) {
                CourseSelectionContent(
                    loggedIn = true,
                    xkState = xkStateHolder.value,
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
                    onEnroll = {},
                    onDrop = {},
                )
            }
        }

        // 1. Closed window light (no detail sheet open)
        rule.waitForIdle()
        saveShot("course_selection_closed_light")

        // 2. Open list light
        xkStateHolder.value = openState
        rule.waitForIdle()
        saveShot("course_selection_open_light")

        // 3. Open list dark
        darkModeState.value = DarkMode.DARK
        rule.waitForIdle()
        saveShot("course_selection_open_dark")

        // 4. Detail conflict sheet dark
        rule.onNodeWithText("人工智能导论").performClick()
        rule.waitForIdle()
        saveShot("course_selection_detail_conflict_dark", preferDialog = true)

        // 5. Detail conflict sheet light
        darkModeState.value = DarkMode.LIGHT
        rule.waitForIdle()
        saveShot("course_selection_detail_conflict_light", preferDialog = true)
    }
}
