package app.goke.ui

import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.model.Task
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.DatePickerDlg
import app.goke.ui.components.ProvideMintModalScope
import app.goke.ui.screens.RescheduleSheet
import app.goke.ui.theme.GokeTheme
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class ModalNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking { app.repo.load() }
    }

    @Test
    fun whenPanelNestedTimeDialAndConfirmDialogPopInExactLifoOrderOnBack() {
        val nav = Nav(initialTab = Tab.TODO)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TODO, nav = nav) {}
            }
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            nav.push(Route.TodoEdit(id = null, date = LocalDate.now().toString()))
        }
        rule.waitForIdle()

        // Make form dirty so system back on TodoEdit opens ConfirmDialog
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("物理实验预习")
        rule.waitForIdle()

        // Open WhenPanel
        rule.onNode(hasText(app.getString(R.string.today), substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.label_time)).assertIsDisplayed()

        // Open TimeDialSheet on top of WhenPanel
        rule.onNodeWithText(app.getString(R.string.label_time)).performScrollTo().performClick()
        rule.waitForIdle()
        val dialHour18 = "${app.getString(R.string.dial_hours)} 18"
        rule.onNodeWithContentDescription(dialHour18).assertIsDisplayed()

        // Predictive back cancelled keeps TimeDialSheet open
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.4f, BackEventCompat.EDGE_LEFT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(dialHour18).assertIsDisplayed()

        // First Back closes TimeDialSheet -> WhenPanel remains open
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(dialHour18).fetchSemanticsNodes().size)
        rule.onNodeWithText(app.getString(R.string.label_repeat)).assertIsDisplayed()
        assertTrue(nav.onRoute)

        // Second Back closes WhenPanel -> TodoEditScreen remains open
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.label_repeat)).fetchSemanticsNodes().size)
        assertTrue(nav.onRoute)

        // Third Back triggers dirty discard ConfirmDialog
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard_title)).assertIsDisplayed()

        // Fourth Back closes ConfirmDialog -> stays in TodoEditScreen
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.detail_editor_discard_title)).fetchSemanticsNodes().size)
        assertTrue(nav.onRoute)
    }

    @Test
    fun tabBottomSheetHidesFloatingNavAndDismissesOnBackWithoutLeavingTab() {
        val nav = Nav(initialTab = Tab.TODO)
        val task = Task(id = "t-modal-1", title = "测试待办")
        val openSheet = mutableStateOf(false)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            GokeTheme(st) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalApp provides app,
                    LocalActions provides actions,
                    LocalNav provides nav,
                ) {
                    ProvideMintModalScope(onSheetOpenChange = { nav.sheetOpen = it }) {
                        Box(Modifier.fillMaxSize()) {
                            Text("RootContent")
                            if (openSheet.value) {
                                RescheduleSheet(
                                    tasks = listOf(task),
                                    snap = app.repo.snapshot.value,
                                    onDismiss = { openSheet.value = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertFalse(nav.sheetOpen)

        rule.runOnUiThread { openSheet.value = true }
        rule.waitForIdle()
        assertTrue(nav.sheetOpen)
        rule.onNodeWithText(app.getString(R.string.todo_no_date)).assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse(openSheet.value)
        assertFalse(nav.sheetOpen)
    }

    @Test
    fun datePickerAndConfirmDialogsSupportPredictiveBackCancelAndDismiss() {
        var showDate by mutableStateOf(false)
        var showConfirm by mutableStateOf(false)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                ProvideMintModalScope {
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { showDate = true }) { Text("OpenDate") }
                        if (showDate) {
                            DatePickerDlg(
                                initial = LocalDate.of(2026, 11, 26),
                                onDismiss = { showDate = false },
                                onPick = {},
                            )
                        }
                        if (showConfirm) {
                            ConfirmDialog(
                                title = "确认标题",
                                text = "确认正文",
                                confirm = "确认按钮",
                                onConfirm = {},
                                onDismiss = { showConfirm = false },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("OpenDate").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.today)).assertIsDisplayed()

        // Predictive back cancelled preserves DatePickerDlg
        rule.runOnUiThread {
            val d = rule.activity.onBackPressedDispatcher
            d.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_RIGHT))
            d.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.today)).assertIsDisplayed()

        // Completed back dismisses DatePickerDlg
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse(showDate)

        // Same for ConfirmDialog
        rule.runOnUiThread { showConfirm = true }
        rule.waitForIdle()
        rule.onNodeWithText("确认标题").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse(showConfirm)
    }
}
