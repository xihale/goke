package app.goke.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动基准：`StartupTimingMetric` 给 timeToInitialDisplay / timeToFullDisplay。
 *
 * 覆盖：
 * - coldStart（Partial = Baseline Profile AOT）
 * - coldStartNoProfile（None = 无 AOT 预编译，对照验证 baseline-prof.txt 收益）
 * - warmStart（Partial）
 * - hotStart（Partial = 测 onResume + decorView.invalidate 路径）
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private var seeded = false

    private fun ensureSeeded(scope: androidx.benchmark.macro.MacrobenchmarkScope) {
        scope.pressHome()
        if (!seeded) {
            scope.startActivityAndWait()
            scope.waitForSeed()
            scope.pressHome()
            seeded = true
        }
    }

    @Test
    fun coldStart() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(),
            setupBlock = { ensureSeeded(this) },
        ) {
            startActivityAndWait()
        }

    @Test
    fun coldStartNoProfile() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            setupBlock = { ensureSeeded(this) },
        ) {
            startActivityAndWait()
        }

    @Test
    fun warmStart() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                pressHome()
            },
        ) {
            startActivityAndWait()
        }

    @Test
    fun hotStart() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.HOT,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                startActivityAndWait()
                waitForSeed()
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
}
