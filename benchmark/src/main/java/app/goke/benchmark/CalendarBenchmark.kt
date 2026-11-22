package app.goke.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 日历页：月网格左右翻页、日期点选与月/周模式切换的帧耗时。 */
@RunWith(AndroidJUnit4::class)
class CalendarBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun swipeMonths() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_CALENDAR)
            },
        ) {
            repeat(3) {
                swipeHorizontal(toNextPage = true, yFraction = 0.35f)
            }
            repeat(3) {
                swipeHorizontal(toNextPage = false, yFraction = 0.35f)
            }
        }

    @Test
    fun selectDaysAndMode() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_CALENDAR)
            },
        ) {
            listOf("10", "15", "20", "25").forEach { day ->
                tapByText(day, timeoutMs = 2_000L)
            }
            if (device.hasObject(By.text("回今天"))) {
                tapByText("回今天")
            }
            tapByText("周")
            settle(250L)
            tapByText("月")
            settle(250L)
        }
}
