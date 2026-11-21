package app.mintcal.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 底栏浮岛切页：`FrameTimingMetric` 给 frameDurationCpuMs 的 P50/P90/P95/P99 与超帧数。
 *
 * 四个 Tab 常驻组合、切页只动绘制层，所以这里主要测「点击 → 色块滑动 + 新页首帧」的帧耗时。
 * 改动前后各跑一次，对比 P90/P99 与 frameOverrunMs 即可判断反馈/动画收敛有没有效果。
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun tabSwitch() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                device.waitForIdle()
            },
        ) {
            // 一圈 12 次切页：今日 → 日历 → 待办 → 课表，重复 3 圈
            repeat(3) {
                tapTab(TAB_TODAY)
                tapTab(TAB_CALENDAR)
                tapTab(TAB_TODO)
                tapTab(TAB_TIMETABLE)
            }
        }
}
