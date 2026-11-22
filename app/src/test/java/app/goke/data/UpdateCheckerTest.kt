package app.goke.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.goke.SmokeTestApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
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

    @Test
    fun `落地页更新清单字段保存与下载校验闭环`(): Unit =
        runBlocking {
            val payload = "fake-apk-bytes-v3".toByteArray()
            val sha =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(payload)
                    .joinToString("") { "%02x".format(it) }
            val manifest =
                """
                [
                  {
                    "tag_name": "v0.0.1-beta.3",
                    "html_url": "https://goke.xeed.ink/",
                    "prerelease": true,
                    "draft": false,
                    "body": "ARMv8 仅 3.8 MiB",
                    "apk_url": "https://goke.xeed.ink/downloads/Goke-0.0.1-beta.3-arm64-v8a.apk",
                    "apk_size": ${payload.size},
                    "apk_sha256": "$sha"
                  }
                ]
                """.trimIndent()

            var downloadCalls = 0
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.2" },
                    fetchReleasesJson = { manifest },
                    openDownloadStream = {
                        downloadCalls++
                        RemoteStream(ByteArrayInputStream(payload), payload.size.toLong())
                    },
                )

            val res = checker.checkNow(nowMs = 5_000L)
            assertTrue(res is UpdateCheckResult.Newer)
            val info = (res as UpdateCheckResult.Newer).info
            assertEquals("https://goke.xeed.ink/downloads/Goke-0.0.1-beta.3-arm64-v8a.apk", info.apkUrl)
            assertEquals(payload.size.toLong(), info.apkSize)
            assertEquals(sha, info.apkSha256)
            assertEquals(sha, store.value.pendingUpdateApkSha256)

            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val updatesDir = File(ctx.cacheDir, "updates-test").apply { deleteRecursively() }
            val downloaded = checker.downloadApk(info, updatesDir)
            assertNotNull(downloaded)
            assertTrue(downloaded!!.exists())
            assertEquals(1, downloadCalls)
            assertTrue(checker.downloadState.value is UpdateDownloadState.ReadyToInstall)

            // 命中本地已校验包时直接复用，不重复请求网络
            val cached = checker.downloadApk(info, updatesDir)
            assertEquals(downloaded.absolutePath, cached?.absolutePath)
            assertEquals(1, downloadCalls)
            updatesDir.deleteRecursively()
        }

    @Test
    fun `安装包 SHA256 不匹配时删除残包并标记失败`(): Unit =
        runBlocking {
            val payload = "corrupted-apk".toByteArray()
            val checker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.0.1-beta.2" },
                    fetchReleasesJson = { "[]" },
                    openDownloadStream = { RemoteStream(ByteArrayInputStream(payload), payload.size.toLong()) },
                )
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val updatesDir = File(ctx.cacheDir, "updates-bad").apply { deleteRecursively() }
            val info =
                ReleaseInfo(
                    version = "0.0.1-beta.3",
                    url = UpdateChecker.LANDING_URL,
                    notes = null,
                    apkUrl = "https://goke.xeed.ink/downloads/Goke-0.0.1-beta.3-arm64-v8a.apk",
                    apkSize = payload.size.toLong(),
                    apkSha256 = "0".repeat(64),
                )
            val result = checker.downloadApk(info, updatesDir)
            assertNull(result)
            val st = checker.downloadState.value
            assertTrue(st is UpdateDownloadState.Failed && st.error == DownloadError.VERIFY)
            assertTrue(updatesDir.listFiles().isNullOrEmpty())
            updatesDir.deleteRecursively()
        }

    @Test
    fun `调起系统安装器处理未知来源权限与 FileProvider Intent`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "Goke-0.0.1-beta.3-arm64-v8a.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val checker = UpdateChecker(settings = store)
        val shadowPm = shadowOf(ctx.packageManager)

        shadowPm.setCanRequestPackageInstalls(false)
        assertEquals(InstallLaunchResult.NEEDS_PERMISSION, checker.launchSystemInstaller(ctx, "0.0.1-beta.3", apk))
        val permIntent: Intent = shadowOf(ctx as android.app.Application).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permIntent.action)

        shadowPm.setCanRequestPackageInstalls(true)
        assertEquals(InstallLaunchResult.LAUNCHED, checker.launchSystemInstaller(ctx, "0.0.1-beta.3", apk))
        val installIntent: Intent = shadowOf(ctx).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, installIntent.action)
        assertEquals("application/vnd.android.package-archive", installIntent.type)
        assertEquals("content", installIntent.data?.scheme)
        assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        updatesDir.deleteRecursively()
    }

    @Test
    fun `latest_json 按正式版与预发布通道选择目标版本`(): Unit =
        runBlocking {
            val latestJson =
                """
                {
                  "tag_name": "v1.0.0",
                  "prerelease": false,
                  "apk_url": "https://goke.xeed.ink/downloads/Goke-1.0.0-arm64-v8a.apk",
                  "apk_size": 4011000,
                  "stable": {
                    "tag_name": "v1.0.0",
                    "prerelease": false,
                    "apk_url": "https://goke.xeed.ink/downloads/Goke-1.0.0-arm64-v8a.apk",
                    "apk_size": 4011000
                  },
                  "latest_any": {
                    "tag_name": "v1.0.1-beta.1",
                    "prerelease": true,
                    "apk_url": "https://goke.xeed.ink/downloads/Goke-1.0.1-beta.1-arm64-v8a.apk",
                    "apk_size": 4012000
                  }
                }
                """.trimIndent()

            val stableChecker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "0.9.0" },
                    fetchReleasesJson = { latestJson },
                )
            val stableRes = stableChecker.checkNow(nowMs = 10_000L) as UpdateCheckResult.Newer
            assertEquals("1.0.0", stableRes.info.version)

            val betaChecker =
                UpdateChecker(
                    settings = store,
                    currentVersionProvider = { "1.0.0-beta.2" },
                    fetchReleasesJson = { latestJson },
                )
            val betaRes = betaChecker.checkNow(nowMs = 20_000L) as UpdateCheckResult.Newer
            assertEquals("1.0.1-beta.1", betaRes.info.version)
        }
}
