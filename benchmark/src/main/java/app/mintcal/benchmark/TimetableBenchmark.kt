package app.mintcal.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 课表页：周视图左右翻周、网格纵向滚动与课程详情弹窗的帧耗时。 */
@RunWith(AndroidJUnit4::class)
class TimetableBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun swipeWeeks() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TIMETABLE)
            },
        ) {
            // 连续向后翻 3 周，再向前翻回 3 周（触发 beyondViewportPageCount=0 的新周网格组装）
            repeat(3) {
                swipeHorizontal(toNextPage = true, yFraction = 0.55f)
            }
            repeat(3) {
                swipeHorizontal(toNextPage = false, yFraction = 0.55f)
            }
        }

    @Test
    fun scrollGrid() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TIMETABLE)
            },
        ) {
            repeat(4) { i ->
                swipeVertical(scrollDown = i % 2 == 0)
            }
        }

    @Test
    fun openCourseSheet() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TIMETABLE)
            },
        ) {
            repeat(3) {
                if (tapByText(SEED_MARKER)) {
                    device.wait(Until.hasObject(By.text("编辑")), 3_000L)
                    settle(200L)
                    device.pressBack()
                    settle(250L)
                }
            }
        }
}
