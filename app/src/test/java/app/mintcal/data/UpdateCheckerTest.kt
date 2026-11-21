package app.mintcal.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mintcal.SmokeTestApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SmokeTestApp::class)
class UpdateCheckerTest {
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SettingsStore(ctx)
    }

    @Test
    fun `更高版本与预发布递增被判为新版本`() {
        assertTrue(isNewerVersion("0.0.1-beta.2", "0.0.1-beta.1"))
        assertTrue(isNewerVersion("0.0.1-beta.10", "0.0.1-beta.2"))
        assertTrue(isNewerVersion("0.0.1-rc.1", "0.0.1-beta.9"))
        assertTrue(isNewerVersion("0.0.1", "0.0.1-beta.1"))
        assertTrue(isNewerVersion("0.0.2-alpha.1", "0.0.1"))
        assertTrue(isNewerVersion("0.2.0", "0.1.5"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun `相同或更低版本不提示`() {
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.1"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.2"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1"))
        assertFalse(isNewerVersion("0.1.5", "0.2.0"))
    }

    @Test
    fun `本地 describe 与变体后缀按所属版本比较`() {
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.1-3-gabcdef"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.1-dirty"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.1-debug"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "0.0.1-beta.1-3-gabcdef-dirty-debug"))
        assertTrue(isNewerVersion("0.0.1-beta.2", "0.0.1-beta.1-3-gabcdef-debug"))
        assertFalse(isNewerVersion("0.2.0", "0.2.0-3-gabcdef"))
    }

    @Test
    fun `非数字版本段视为不大于`() {
        assertFalse(isNewerVersion("abc", "0.0.1-beta.1"))
        assertFalse(isNewerVersion("0.0.1-beta.1", "abc"))
    }

    @Test
    fun `默认开启每日自动检查`() {
        assertEquals(UpdateInterval.DAILY, store.value.updateInterval)
    }

    @Test
    fun `预发布版追踪更新的预发布与正式版并忽略草稿`() =
        runBlocking {
            val json =
                """
                [
                  {"tag_name":"v0.0.1-beta.3","html_url":"https://example.com/b3","prerelease":true,"draft":true,"body":"draft"},
                  {"tag_name":"v0.0.1-beta.2","html_url":"https://example.com/b2","prerelease":true,"draft":false,"body":"修复若干问题"},
                  {"tag_name":"v0.0.1-beta.1","html_url":"https://example.com/b1","prerelease":true,"draft":false,"body":"initial"}
                ]
                """.trimIndent()
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.1" },
                    fetchReleasesJson = { json },
                )
            val result = checker.checkNow(nowMs = 1_000L)
            assertTrue(result is UpdateCheckResult.Newer)
            val info = (result as UpdateCheckResult.Newer).info
            assertEquals("0.0.1-beta.2", info.version)
            assertEquals("0.0.1-beta.2", store.value.pendingUpdateVersion)
            assertEquals("修复若干问题", store.value.pendingUpdateNotes)
            assertEquals(1_000L, store.value.lastUpdateCheckAt)
        }

    @Test
    fun `正式版忽略预发布仅提醒正式版`() =
        runBlocking {
            val json =
                """
                [
                  {"tag_name":"v0.0.2-beta.1","html_url":"https://example.com/b1","prerelease":true,"draft":false},
                  {"tag_name":"v0.0.1","html_url":"https://example.com/001","prerelease":false,"draft":false}
                ]
                """.trimIndent()
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1" },
                    fetchReleasesJson = { json },
                )
            val result = checker.checkNow(nowMs = 2_000L)
            assertEquals(UpdateCheckResult.UpToDate, result)
            assertEquals("", store.value.pendingUpdateVersion)
        }

    @Test
    fun `无发布记录时视为最新且不报错`() =
        runBlocking {
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.1" },
                    fetchReleasesJson = { "[]" },
                )
            assertEquals(UpdateCheckResult.UpToDate, checker.checkNow())
        }

    @Test
    fun `网络异常降级为 Error 且不破坏已有待更新记录`() =
        runBlocking {
            store.update { it.copy(pendingUpdateVersion = "0.0.1-beta.2", lastUpdateCheckAt = 100L) }
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.1" },
                    fetchReleasesJson = { error("offline") },
                )
            assertEquals(UpdateCheckResult.Error, checker.checkNow(nowMs = 500L))
            assertEquals("0.0.1-beta.2", store.value.pendingUpdateVersion)
            assertEquals(100L, store.value.lastUpdateCheckAt)
        }

    @Test
    fun `升级安装后自动清理失效的待更新标记`() {
        store.update {
            it.copy(
                pendingUpdateVersion = "0.0.1-beta.2",
                pendingUpdateUrl = "https://example.com/b2",
                dismissedUpdateVersion = "0.0.1-beta.2",
            )
        }
        val checker =
            UpdateChecker(
                settings = store,
                currentVersionProvider = { "0.0.1-beta.2" },
                fetchReleasesJson = { "[]" },
            )
        checker.clearStalePendingUpdate()
        assertEquals("", store.value.pendingUpdateVersion)
        assertEquals("", store.value.dismissedUpdateVersion)
    }

    @Test
    fun `自动检查遵循频率设置`() =
        runBlocking {
            var calls = 0
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.1" },
                    fetchReleasesJson = {
                        calls++
                        "[]"
                    },
                )
            val dayMs = TimeUnit.DAYS.toMillis(1)
            // 初次检查执行
            checker.maybeAutoCheck(nowMs = dayMs * 2)
            assertEquals(1, calls)
            // 周期内跳过
            checker.maybeAutoCheck(nowMs = dayMs * 2 + 60_000L)
            assertEquals(1, calls)
            // 超过一天再次检查
            checker.maybeAutoCheck(nowMs = dayMs * 3 + 1_000L)
            assertEquals(2, calls)
            // 关闭后不再自动检查
            store.update { it.copy(updateInterval = UpdateInterval.OFF) }
            assertNull(checker.maybeAutoCheck(nowMs = dayMs * 10))
            assertEquals(2, calls)
        }
}
