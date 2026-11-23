package app.goke.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.goke.SmokeTestApp
import app.goke.core.jxfw.JxfwGrade
import app.goke.data.Settings
import app.goke.jxfw.JxfwGradesState
import app.goke.ui.screens.GradesContent
import app.goke.ui.theme.GokeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class GradesFilterTest {
    @get:Rule val rule = createComposeRule()

    private val sampleGrades =
        listOf(
            JxfwGrade(
                term = "202501",
                code = "CS101",
                name = "高等数学",
                credit = 4.0,
                score = "90",
                gpa = 4.0,
                category = "必修",
                studyMode = "初修",
            ),
            JxfwGrade(
                term = "202501",
                code = "GE102",
                name = "音乐鉴赏",
                credit = 2.0,
                score = "70",
                gpa = 2.0,
                category = "通识",
                studyMode = "初修",
            ),
            JxfwGrade(
                term = "202501",
                code = "PE103",
                name = "体育",
                credit = 1.0,
                score = "50",
                gpa = 0.0,
                category = "必修",
                studyMode = "重修",
            ),
        )

    @Test
    fun categoryFilterAndRowExclusionUpdateGpaAndEarnedCredits() {
        rule.setContent {
            GokeTheme(settings = Settings()) {
                GradesContent(
                    loggedIn = true,
                    gradesState = JxfwGradesState(grades = sampleGrades),
                    onBack = {},
                    onOpenJxfw = {},
                    onRefresh = {},
                )
            }
        }

        // Initial: earned = 4 + 2 = 6 (PE failed), weighted GPA = (4*4 + 2*2 + 1*0) / 7 = 20/7 = 2.86
        rule.onNodeWithText("加权均绩 (GPA)").assertIsDisplayed()
        rule.onNodeWithText("2.86").assertIsDisplayed()
        rule.onNodeWithText("已修学分 6", substring = true).assertIsDisplayed()

        // Exclude "通识" category -> remaining: 高等数学(4, 4.0) + 体育(1, 0.0) -> GPA = 16/5 = 3.20
        rule.onNodeWithText("通识").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("筛选均绩 (GPA)").assertIsDisplayed()
        rule.onNodeWithText("3.20").assertIsDisplayed()
        rule.onNodeWithText("已修学分 4", substring = true).assertIsDisplayed()
        rule.onNodeWithText("已排除 1 门").assertIsDisplayed()
        rule.onNodeWithText("重置筛选").assertIsDisplayed()

        // Also exclude "体育" row -> remaining: 高等数学(4, 4.0) -> GPA = 4.00, excluded 2
        rule.onNodeWithText("体育").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("4.00").assertIsDisplayed()
        rule.onNodeWithText("已排除 2 门").assertIsDisplayed()

        // Tap "体育" again to restore it -> back to 3.20, excluded 1
        rule.onNodeWithText("体育").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("3.20").assertIsDisplayed()
        rule.onNodeWithText("已排除 1 门").assertIsDisplayed()

        // Reset filter -> back to 2.86, "加权均绩 (GPA)"
        rule.onNodeWithText("重置筛选").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("加权均绩 (GPA)").assertIsDisplayed()
        rule.onNodeWithText("2.86").assertIsDisplayed()
        rule.onNodeWithText("已修学分 6", substring = true).assertIsDisplayed()
    }
}
