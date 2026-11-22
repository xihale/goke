package app.goke.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.ui.screens.TodoEditScreen
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
class TodoEditNavigationTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
        }
    }

    @Test
    fun boxSelectOnTimetableOpenWhenPanelAndTimeDialAndConfirmReturns() {
        rule.setContent {
            GokeTheme(app.settings.flow.value) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
            }
        }
        rule.waitForIdle()

        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val start = Offset(rootBounds.width * 0.5f, rootBounds.height * 0.45f)
        val end = Offset(rootBounds.width * 0.5f, rootBounds.height * 0.55f)
        rule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700L)
            moveTo(end)
            up()
        }
        rule.waitForIdle()

        val confirmText = app.getString(R.string.confirm)
        rule.onNodeWithText(confirmText).assertIsDisplayed()

        // Enter title
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("测试日程2")
        rule.waitForIdle()

        // Open WhenPanel
        rule.onNode(hasText("–", substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()

        // Open TimeDialSheet inside WhenPanel
        rule.onNodeWithText(app.getString(R.string.label_time)).performScrollTo().performClick()
        rule.waitForIdle()

        // Confirm TimeDialSheet
        rule.onAllNodesWithText(app.getString(R.string.ok)).onLast().performClick()
        rule.waitForIdle()

        // Confirm WhenPanel
        rule.onAllNodesWithText(app.getString(R.string.ok)).onLast().performClick()
        rule.waitForIdle()

        // Click confirm on TodoEditScreen
        rule.onNodeWithText(confirmText).performClick()
        rule.waitForIdle()
        rule.waitUntil(3_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "测试日程2" }
        }
        rule.waitForIdle()

        assertEquals(
            1,
            app.repo.snapshot.value.tasks
                .count { it.title == "测试日程2" },
        )
        assertEquals(0, rule.onAllNodesWithText(confirmText).fetchSemanticsNodes().size)
    }

    @Test
    fun boxSelectDiscardDialogExitsTimetableEditorCleanly() {
        rule.setContent {
            GokeTheme(app.settings.flow.value) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
            }
        }
        rule.waitForIdle()

        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val start = Offset(rootBounds.width * 0.5f, rootBounds.height * 0.45f)
        val end = Offset(rootBounds.width * 0.5f, rootBounds.height * 0.55f)
        rule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700L)
            moveTo(end)
            up()
        }
        rule.waitForIdle()

        val confirmText = app.getString(R.string.confirm)
        rule.onNodeWithText(confirmText).assertIsDisplayed()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("草稿日程")
        rule.waitForIdle()

        // Open WhenPanel and close via cancel
        rule.onNode(hasText("–", substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.cancel)).performClick()
        rule.waitForIdle()

        // Click back -> ConfirmDialog -> Discard -> returns
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard)).performClick()
        rule.waitForIdle()

        assertEquals(0, rule.onAllNodesWithText(confirmText).fetchSemanticsNodes().size)
        assertEquals(
            0,
            app.repo.snapshot.value.tasks
                .count { it.title == "草稿日程" },
        )
    }

    @Test
    fun committedNewScheduleDoesNotSelfConflictOrPromptDiscardWhileExiting() {
        var backCalls = 0
        val today = LocalDate.now()
        rule.setContent {
            val nav = rememberNav()
            val scope = rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            GokeTheme(app.settings.flow.value) {
                CompositionLocalProvider(
                    LocalApp provides app,
                    LocalActions provides actions,
                    LocalNav provides nav,
                ) {
                    TodoEditScreen(
                        id = null,
                        dateArg = today.toString(),
                        startArg = today.atTime(10, 0).toString(),
                        endArg = today.atTime(11, 30).toString(),
                        onBack = { backCalls++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("防重入日程")
        rule.waitForIdle()

        val confirmText = app.getString(R.string.confirm)
        rule.onNodeWithText(confirmText).performClick()
        rule.waitUntil(3_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "防重入日程" }
        }
        rule.waitForIdle()

        assertEquals(1, backCalls)
        // Must not show self-conflict card after committing
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.conflict_title)).fetchSemanticsNodes().size)
        // Pressing back after commit must exit directly instead of prompting discard
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()
        assertEquals(2, backCalls)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.detail_editor_discard_title)).fetchSemanticsNodes().size)
    }
}
