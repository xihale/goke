package app.mintcal.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until

/** benchmark 变体的包名（`:app` 的 benchmark buildType：applicationIdSuffix = ".bench"）。 */
const val TARGET_PACKAGE = "app.mintcal.bench"

/** 底栏四个 Tab 的文案，对应 strings.xml 的 tab_*。 */
const val TAB_TODAY = "今日"
const val TAB_TIMETABLE = "课表"
const val TAB_CALENDAR = "日历"
const val TAB_TODO = "待办"

/** 常用按钮 / 入口文案与 contentDescription。 */
const val DESC_SETTINGS = "设置"
const val DESC_ADD = "添加"
const val DESC_ADD_TASK = "新建待办"
const val DESC_SEARCH = "搜索"

/** BenchSeed 一定会插入的一门课；用来判断种子数据已经落库。 */
const val SEED_MARKER = "高等数学"

/**
 * 首启时 BenchSeed 在 repo.load() 之后异步写库。setupBlock 里等它出现，
 * 避免把首次建库/写库算进被测区间。
 */
fun MacrobenchmarkScope.waitForSeed() {
    device.wait(Until.hasObject(By.text(SEED_MARKER)), 20_000L)
    device.waitForIdle()
}

/** 点底栏 Tab：Compose 的 Text 节点在 UiAutomator 里可见，点它落在 Tab 的 48dp 命中区内。 */
fun MacrobenchmarkScope.tapTab(label: String) {
    device.wait(Until.hasObject(By.text(label)), 5_000L)
    device.findObject(By.text(label))?.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.tapByText(
    text: String,
    timeoutMs: Long = 5_000L,
): Boolean {
    repeat(3) {
        val obj = device.wait(Until.findObject(By.text(text)), timeoutMs) ?: return false
        try {
            val c = obj.visibleCenter
            device.click(c.x, c.y)
            device.waitForIdle()
            return true
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            SystemClock.sleep(80L)
        }
    }
    return false
}

fun MacrobenchmarkScope.tapByDesc(
    desc: String,
    timeoutMs: Long = 5_000L,
): Boolean {
    val obj = device.wait(Until.findObject(By.desc(desc)), timeoutMs) ?: return false
    val c = obj.visibleCenter
    device.click(c.x, c.y)
    device.waitForIdle()
    return true
}

fun MacrobenchmarkScope.tapBySelector(
    selector: BySelector,
    timeoutMs: Long = 5_000L,
): Boolean {
    repeat(3) {
        val obj = device.wait(Until.findObject(selector), timeoutMs) ?: return false
        try {
            val c = obj.visibleCenter
            device.click(c.x, c.y)
            device.waitForIdle()
            return true
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            SystemClock.sleep(80L)
        }
    }
    return false
}

fun MacrobenchmarkScope.swipeHorizontal(
    toNextPage: Boolean,
    yFraction: Float = 0.5f,
    steps: Int = 18,
) {
    val w = device.displayWidth
    val h = device.displayHeight
    val y = (h * yFraction).toInt()
    val fromX = if (toNextPage) w * 4 / 5 else w / 5
    val toX = if (toNextPage) w / 5 else w * 4 / 5
    device.swipe(fromX, y, toX, y, steps)
    device.waitForIdle()
}

fun MacrobenchmarkScope.swipeVertical(
    scrollDown: Boolean,
    steps: Int = 20,
) {
    val w = device.displayWidth
    val h = device.displayHeight
    val fromY = if (scrollDown) h * 3 / 4 else h / 4
    val toY = if (scrollDown) h / 4 else h * 3 / 4
    device.swipe(w / 2, fromY, w / 2, toY, steps)
    device.waitForIdle()
}

fun MacrobenchmarkScope.settle(ms: Long = 300L) {
    SystemClock.sleep(ms)
    device.waitForIdle()
}

/** 清空 benchmark 包的数据，下一次启动会重新执行 BenchSeed（用于需要确定初始状态的用例）。 */
fun MacrobenchmarkScope.clearTargetData() {
    device.executeShellCommand("pm clear $TARGET_PACKAGE")
}
