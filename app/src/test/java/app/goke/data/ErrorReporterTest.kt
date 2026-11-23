package app.goke.data

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.ui.GokeRoot
import app.goke.ui.theme.GokeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class ErrorReporterTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()
    private var savedHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        savedHandler = Thread.getDefaultUncaughtExceptionHandler()
        File(ctx.filesDir, "error_reports.json").delete()
        app.errors.clearHistory()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(savedHandler)
        File(ctx.filesDir, "error_reports.json").delete()
    }

    @Test
    fun `异常记录持久化并在重启加载后恢复待展示弹窗`() {
        var now = 1_700_000_000_000L
        val first = ErrorReporter { now }
        first.attach(ctx)

        first.record("Sync", IllegalStateException("boom state"), fatal = true, threadName = "main")
        val recorded = first.pendingModal.value
        assertNotNull(recorded)
        assertTrue(recorded!!.fatal)
        assertTrue(recorded.detail.contains("IllegalStateException: boom state"))
        assertTrue(recorded.detail.contains("Source: Sync (fatal)"))

        // 模拟应用崩溃后重启：新实例读取磁盘上的未确认记录
        val restarted = ErrorReporter { now + 5_000L }
        restarted.attach(ctx)
        assertEquals(1, restarted.reports.value.size)
        assertEquals(recorded.id, restarted.pendingModal.value?.id)

        restarted.dismissPending()
        assertNull(restarted.pendingModal.value)
        assertEquals(1, restarted.reports.value.size)
        assertTrue(
            restarted.reports.value
                .first()
                .acknowledged,
        )

        restarted.clearHistory()
        assertTrue(restarted.reports.value.isEmpty())
    }

    @Test
    fun `协程异常处理器记录普通异常并忽略 CancellationException`() {
        val reporter = ErrorReporter { 1_700_000_001_000L }
        reporter.attach(ctx)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + reporter.coroutineExceptionHandler)
        scope.launch { throw CancellationException("cancelled") }
        assertTrue(reporter.reports.value.isEmpty())

        scope.launch { throw IllegalArgumentException("bad arg") }
        assertEquals(1, reporter.reports.value.size)
        val item = reporter.pendingModal.value
        assertNotNull(item)
        assertFalse(item!!.fatal)
        assertTrue(item.detail.contains("IllegalArgumentException: bad arg"))
    }

    @Test
    fun `根组件弹出异常 Modal 支持一键复制堆栈并关闭`() {
        runBlocking { app.repo.load() }
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null) {}
            }
        }
        rule.waitForIdle()

        app.errors.record("Jxfw", RuntimeException("unexpected parser crash"), fatal = false)
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.error_modal_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.error_modal_copy)).performClick()
        rule.waitForIdle()

        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied =
            cm.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
                .orEmpty()
        assertTrue(copied.contains("RuntimeException: unexpected parser crash"))
        assertTrue(copied.contains("=== Goke Error Report ==="))

        rule.onNodeWithText(app.getString(R.string.close)).performClick()
        rule.waitForIdle()
        assertNull(app.errors.pendingModal.value)
    }
}
