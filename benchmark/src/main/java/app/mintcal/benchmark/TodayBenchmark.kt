package app.mintcal.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 今日页：时间线滚动、已完成折叠区展开收起与快速添加浮层开关的帧耗时。 */
@RunWith(AndroidJUnit4::class)
class TodayBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollAndInteract() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TODAY)
            },
        ) {
            repeat(4) { i ->
                swipeVertical(scrollDown = i % 2 == 0)
            }
            if (tapBySelector(By.textContains("已完成"), timeoutMs = 2_000L)) {
                settle(250L)
                tapBySelector(By.textContains("已完成"), timeoutMs = 2_000L)
                settle(200L)
            }
            if (tapByDesc(DESC_ADD_TASK)) {
                settle(250L)
                device.pressBack()
                settle(200L)
                if (device.hasObject(By.text("准备做什么？"))) {
                    device.pressBack()
                    device.waitForIdle()
                }
            }
        }
}
