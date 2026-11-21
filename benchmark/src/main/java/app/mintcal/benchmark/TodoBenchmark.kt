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

/** 待办页：列表滚动、勾选、筛选搜索与快速添加输入的帧耗时。 */
@RunWith(AndroidJUnit4::class)
class TodoBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scroll() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TODO)
            },
        ) {
            repeat(4) { i ->
                swipeVertical(scrollDown = i % 2 == 0)
            }
        }

    /**
     * 勾选：点第一行勾选框（坐标点击 + 等待 animateItem 收敛，避免行移出树时触发 StaleObjectException）。
     */
    @Test
    fun toggle() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                clearTargetData()
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TODO)
                device.wait(Until.hasObject(By.checkable(true)), 5_000L)
                settle(500L)
            },
        ) {
            repeat(5) {
                tapBySelector(By.checkable(true))
                settle(500L)
            }
        }

    @Test
    fun searchAndFilter() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TODO)
            },
        ) {
            if (tapByDesc(DESC_SEARCH)) {
                device.wait(Until.findObject(By.focused(true)), 3_000L)?.text = "高数"
                settle(250L)
                tapByDesc("清除")
                settle(200L)
                tapByDesc("取消")
            }
            if (tapByText("全部清单")) {
                tapByText("学习")
                settle(200L)
            }
            if (tapByText("学习")) {
                tapByText("全部清单")
            }
        }

    @Test
    fun quickAddTyping() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                tapTab(TAB_TODO)
            },
        ) {
            if (tapByDesc(DESC_ADD)) {
                settle(300L)
                val input =
                    device.wait(Until.findObject(By.focused(true)), 3_000L)
                        ?: device.findObject(By.text("准备做什么？"))
                input?.text = "测试待办 明天 10:00 @学习 !高"
                settle(300L)
                device.pressBack()
                settle(200L)
                if (device.hasObject(By.textContains("测试待办"))) {
                    device.pressBack()
                    device.waitForIdle()
                }
            }
        }
}
