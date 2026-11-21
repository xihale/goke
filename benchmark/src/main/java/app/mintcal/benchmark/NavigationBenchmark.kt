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

/** 压栈导航与子页面转场：设置 / 外观 / 数据 / 课程与待办编辑页。 */
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun settingsFlow() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_ME)
            },
        ) {
            device.wait(Until.hasObject(By.text("外观")), 3_000L)
            if (tapByText("外观")) {
                settle(250L)
                device.pressBack()
                settle(250L)
            }
            if (tapByText("数据与备份")) {
                settle(250L)
                device.pressBack()
                settle(250L)
            }
        }

    @Test
    fun editScreens() =
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
            if (tapByText(SEED_MARKER)) {
                if (tapByText("编辑")) {
                    settle(300L)
                    device.pressBack()
                    settle(250L)
                } else {
                    device.pressBack()
                    settle(200L)
                }
            }
            tapTab(TAB_TODO)
            if (tapByText("交高数作业")) {
                settle(300L)
                device.pressBack()
                settle(250L)
            }
        }
}
