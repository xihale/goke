package app.goke

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.goke.core.model.Course
import app.goke.core.model.EventKind
import app.goke.core.model.Freq
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.todo.QuickAdd
import app.goke.core.todo.QuickTokenKind
import app.goke.data.Settings
import app.goke.ui.screens.QuickAddComposer
import app.goke.ui.screens.QuickPanel
import app.goke.ui.screens.WhenPick
import app.goke.ui.screens.rewriteQuickField
import app.goke.ui.theme.GokeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class QuickAddComposerTest {
    @get:Rule val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Test
    fun rewriteQuickFieldPlacesCursorAtEnd() {
        val r1 = rewriteQuickField("", emptyList(), setOf(QuickTokenKind.LIST), "#")
        assertEquals("#", r1.text)
        assertEquals(TextRange(1), r1.selection)

        val src2 = "写作业 !中"
        val p2 = QuickAdd.parse(src2, LocalDateTime.of(2026, 11, 26, 16, 0))
        val r2 = rewriteQuickField(src2, p2.tokens, setOf(QuickTokenKind.LIST), "#")
        assertEquals("写作业 !中 #", r2.text)
        assertEquals(TextRange(8), r2.selection)

        val r3 = rewriteQuickField("写作业 #", emptyList(), setOf(QuickTokenKind.LIST), "#")
        assertEquals("写作业 #", r3.text)
        assertEquals(TextRange(5), r3.selection)

        val src4 = "写作业 #旧"
        val p4 = QuickAdd.parse(src4, LocalDateTime.of(2026, 11, 26, 16, 0))
        val r4 = rewriteQuickField(src4, p4.tokens, setOf(QuickTokenKind.LIST), "#")
        assertEquals("写作业 #", r4.text)
        assertEquals(TextRange(5), r4.selection)

        val r5 = rewriteQuickField("写报告", emptyList(), setOf(QuickTokenKind.COURSE), "/大学物理实验")
        assertEquals("写报告 /大学物理实验 ", r5.text)
        assertEquals(TextRange(12), r5.selection)
    }

    @Test
    fun clickingInboxWithEmptyListsInsertsHashBeforeTyping() {
        val now = LocalDateTime.of(2026, 11, 26, 16, 0)
        var latest = ""
        rule.setContent {
            GokeTheme(Settings()) {
                var field by remember { mutableStateOf(TextFieldValue("")) }
                var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
                val focus = remember { FocusRequester() }
                val parsed = remember(field.text) { if (field.text.isBlank()) null else QuickAdd.parse(field.text, now) }
                latest = field.text
                Column {
                    QuickAddComposer(
                        snap = Snapshot(),
                        now = now,
                        field = field,
                        onFieldChange = { field = it },
                        parsed = parsed,
                        highlight = VisualTransformation.None,
                        focusRequester = focus,
                        toolPanel = toolPanel,
                        onToolPanelChange = { toolPanel = it },
                        panelWhen = null,
                        panelRemindList = emptyList(),
                        panelFreq = null,
                        panelInterval = 1,
                        panelByDays = emptySet(),
                        panelByMonths = emptySet(),
                        expandLabel = app.getString(R.string.detail_editor_full_edit),
                        onExpand = {},
                        submitLabel = app.getString(R.string.confirm),
                        canSubmit = true,
                        onSubmit = {},
                        onOpenWhenPanel = {},
                        onRewriteToken = { kinds, add ->
                            field = rewriteQuickField(field.text, parsed?.tokens.orEmpty(), kinds, add)
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.label_list)).performClick()
        rule.waitForIdle()
        assertEquals("#", latest)

        rule.onNode(hasSetTextAction()).performTextInput("作业")
        rule.waitForIdle()
        assertEquals("#作业", latest)
    }

    @Test
    fun coursePanelDeduplicatesCoursesAndExcludesExamsAndOtherSemesters() {
        val now = LocalDateTime.of(2026, 11, 26, 16, 0)
        val semCur = Semester(id = "sem-cur", name = "2026-1", week1Monday = LocalDate.of(2026, 8, 31), isCurrent = true)
        val semOld = Semester(id = "sem-old", name = "2025-2", week1Monday = LocalDate.of(2026, 3, 2), isCurrent = false)
        val snap =
            Snapshot(
                semesters = listOf(semCur, semOld),
                courses =
                    listOf(
                        Course(id = "c1", semesterId = semCur.id, name = "大学物理实验", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1)),
                        Course(id = "c2", semesterId = semCur.id, name = "大学物理实验", dayOfWeek = 3, startPeriod = 5, endPeriod = 6, weeks = setOf(2)),
                        Course(id = "c3", semesterId = semCur.id, name = "大学物理实验", dayOfWeek = 5, startPeriod = 9, endPeriod = 10, weeks = setOf(3)),
                        Course(id = "c4", semesterId = semCur.id, name = "高等数学期末考试", dayOfWeek = 2, startPeriod = 1, endPeriod = 2, weeks = setOf(18), kind = EventKind.EXAM),
                        Course(id = "c5", semesterId = semOld.id, name = "旧学期课程", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1)),
                    ),
            )
        var latest = ""
        rule.setContent {
            GokeTheme(Settings()) {
                var field by remember { mutableStateOf(TextFieldValue("交实验报告")) }
                var toolPanel by remember { mutableStateOf<QuickPanel?>(null) }
                val focus = remember { FocusRequester() }
                val parsed = remember(field.text) { if (field.text.isBlank()) null else QuickAdd.parse(field.text, now) }
                latest = field.text
                Column {
                    QuickAddComposer(
                        snap = snap,
                        now = now,
                        field = field,
                        onFieldChange = { field = it },
                        parsed = parsed,
                        highlight = VisualTransformation.None,
                        focusRequester = focus,
                        toolPanel = toolPanel,
                        onToolPanelChange = { toolPanel = it },
                        panelWhen = null,
                        panelRemindList = emptyList(),
                        panelFreq = null,
                        panelInterval = 1,
                        panelByDays = emptySet(),
                        panelByMonths = emptySet(),
                        expandLabel = app.getString(R.string.detail_editor_full_edit),
                        onExpand = {},
                        submitLabel = app.getString(R.string.confirm),
                        canSubmit = true,
                        onSubmit = {},
                        onOpenWhenPanel = {},
                        onRewriteToken = { kinds, add ->
                            field = rewriteQuickField(field.text, parsed?.tokens.orEmpty(), kinds, add)
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.quick_add_link_course)).performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText("大学物理实验").assertCountEquals(1)
        rule.onAllNodesWithText("/大学物理实验").assertCountEquals(0)
        rule.onAllNodesWithText("高等数学期末考试").assertCountEquals(0)
        rule.onAllNodesWithText("旧学期课程").assertCountEquals(0)

        rule.onNodeWithText("大学物理实验").performClick()
        rule.waitForIdle()
        assertEquals("交实验报告 /大学物理实验 ", latest)
    }

    @Test
    fun sendButtonStaysVisibleAndClickableOnNarrowWidth() {
        val now = LocalDateTime.of(2026, 11, 26, 16, 0)
        var submitted = false
        rule.setContent {
            GokeTheme(Settings()) {
                val field = remember { TextFieldValue("交实验报告") }
                val focus = remember { FocusRequester() }
                Column(Modifier.width(360.dp)) {
                    QuickAddComposer(
                        snap = Snapshot(),
                        now = now,
                        field = field,
                        onFieldChange = {},
                        parsed = QuickAdd.parse(field.text, now),
                        highlight = VisualTransformation.None,
                        focusRequester = focus,
                        toolPanel = null,
                        onToolPanelChange = {},
                        panelWhen = WhenPick(now.toLocalDate(), LocalTime.of(18, 0)),
                        panelRemindList = listOf(15),
                        panelFreq = Freq.WEEKLY,
                        panelInterval = 1,
                        panelByDays = emptySet(),
                        panelByMonths = emptySet(),
                        defaultListLabel = "一个非常非常长的默认清单名称",
                        expandLabel = app.getString(R.string.detail_editor_full_edit),
                        onExpand = {},
                        submitLabel = app.getString(R.string.confirm),
                        canSubmit = true,
                        onSubmit = { submitted = true },
                        onOpenWhenPanel = {},
                        onRewriteToken = { _, _ -> },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.confirm)).assertIsDisplayed().performClick()
        assertTrue(submitted)
    }
}
