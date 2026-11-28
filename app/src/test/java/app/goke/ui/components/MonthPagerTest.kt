package app.goke.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.ui.theme.GokeTheme
import app.goke.ui.weekdayShort
import app.goke.ui.yearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class MonthPagerTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Test
    fun pickingDayAfterPagingDoesNotShiftMonthAgain() {
        val startDate = LocalDate.of(2026, 11, 28)
        var pickedDate = startDate

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            var selected by remember { mutableStateOf(startDate) }
            GokeTheme(st) {
                // Deliberately pass reactive YearMonth.from(selected) like EventTimeSheet previously did.
                MonthPager(
                    selected = selected,
                    initial = YearMonth.from(selected),
                    onPick = {
                        selected = it
                        pickedDate = it
                    },
                )
            }
        }
        rule.waitForIdle()

        val novHeader = app.yearMonth(LocalDate.of(2026, 11, 1))
        val octHeader = app.yearMonth(LocalDate.of(2026, 10, 1))
        val decHeader = app.yearMonth(LocalDate.of(2026, 12, 1))
        rule.onNodeWithText(novHeader).assertIsDisplayed()

        // Page to previous month (October) and pick Oct 15 — header must stay on October.
        rule.onNodeWithContentDescription(app.getString(R.string.a11y_prev_month)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(octHeader).assertIsDisplayed()

        val oct15 = LocalDate.of(2026, 10, 15)
        val oct15Desc = app.getString(R.string.fmt_full_date, oct15.year, oct15.monthValue, oct15.dayOfMonth, app.weekdayShort(oct15.dayOfWeek.value))
        rule.onNodeWithContentDescription(oct15Desc).performClick()
        rule.waitForIdle()

        assertEquals(oct15, pickedDate)
        rule.onNodeWithText(octHeader).assertIsDisplayed()

        // Page forward twice to December and pick Dec 10 — header must stay on December.
        rule.onNodeWithContentDescription(app.getString(R.string.a11y_next_month)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.a11y_next_month)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(decHeader).assertIsDisplayed()

        val dec10 = LocalDate.of(2026, 12, 10)
        val dec10Desc = app.getString(R.string.fmt_full_date, dec10.year, dec10.monthValue, dec10.dayOfMonth, app.weekdayShort(dec10.dayOfWeek.value))
        rule.onNodeWithContentDescription(dec10Desc).performClick()
        rule.waitForIdle()

        assertEquals(dec10, pickedDate)
        rule.onNodeWithText(decHeader).assertIsDisplayed()
    }

    @Test
    fun scrollSignalReturnsToSelectedMonthEvenWhenDateUnchanged() {
        val startDate = LocalDate.of(2026, 11, 28)

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            var selected by remember { mutableStateOf(startDate) }
            var signal by remember { mutableIntStateOf(0) }
            GokeTheme(st) {
                androidx.compose.foundation.layout.Column {
                    Button(onClick = {
                        selected = startDate
                        signal++
                    }) { Text("JumpToday") }
                    MonthPager(
                        selected = selected,
                        initial = YearMonth.from(startDate),
                        scrollSignal = signal,
                        onPick = { selected = it },
                    )
                }
            }
        }
        rule.waitForIdle()

        val novHeader = app.yearMonth(LocalDate.of(2026, 11, 1))
        val octHeader = app.yearMonth(LocalDate.of(2026, 10, 1))

        // Swipe/page away to October without changing selected date.
        rule.onNodeWithContentDescription(app.getString(R.string.a11y_prev_month)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(octHeader).assertIsDisplayed()

        // Triggering JumpToday with the same date should scroll back to November.
        rule.onNodeWithText("JumpToday").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(novHeader).assertIsDisplayed()
    }
}
