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
import app.goke.ui.theme.GokeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
                R.string.parcel_title,
                R.string.widgets_title,
                R.string.appearance,
                R.string.cal_sync_title,
                R.string.sync_title,
                R.string.data_title,
                R.string.trash_title,
                R.string.history_title,
                R.string.reliability_title,
                R.string.lan_web_title,
                R.string.mcp_title,
                R.string.about_title,
            )

        for (titleRes in subpages) {
            val label = app.getString(titleRes)
            rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(label))
            if (titleRes == R.string.reliability_title || titleRes == R.string.lan_web_title || titleRes == R.string.mcp_title || titleRes == R.string.about_title) {
                rule.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
            }
            rule.waitForIdle()
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
        rule.onNodeWithText(app.getString(R.string.parcel_title)).assertIsDisplayed()
    }
}
