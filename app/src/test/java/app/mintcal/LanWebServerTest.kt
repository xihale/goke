package app.mintcal

import androidx.test.core.app.ApplicationProvider
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.sync.SyncCodec
import app.mintcal.core.sync.WireRecord
import app.mintcal.core.util.Ids
import app.mintcal.core.util.MintJson
import app.mintcal.lanweb.LanWebServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(application = SmokeTestApp::class, sdk = [34])
class LanWebServerTest {
    private lateinit var app: SmokeTestApp
    private lateinit var server: LanWebServer
    private val fakeClock = AtomicLong(1_700_000_000_000L)
    private val activityCount = AtomicInteger(0)
    private var currentPin = "482913"

    private val testAssets =
        mapOf(
            "lanweb/app/index.html" to "<!doctype html><html></html>".toByteArray(),
            "lanweb/app/app.css" to "body{}".toByteArray(),
            "lanweb/app/js/main.js" to "console.log('ok');".toByteArray(),
            "lanweb/app/icon.svg" to "<svg/>".toByteArray(),
            "lanweb/app/icon.png" to byteArrayOf(1, 2, 3),
            "lanweb/app/manifest.webmanifest" to "{}".toByteArray(),
        )

    @Before
    fun setUp() =
        runBlocking {
            app = ApplicationProvider.getApplicationContext()
            app.repo.load()
            server =
                LanWebServer(
                    repo = app.repo,
                    assetOpener = { path -> testAssets[path]?.let(::ByteArrayInputStream) },
                    host = "127.0.0.1",
                    port = 0,
                    pinProvider = { currentPin },
                    onActivity = { activityCount.incrementAndGet() },
                    clock = { fakeClock.get() },
                )
            server.start()
        }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.boundPort}"

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        bearer: String? = null,
    ): HttpResult {
        val conn = (URL("${baseUrl()}$path").openConnection() as HttpURLConnection)
        conn.requestMethod = method
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val headers = conn.headerFields.filterKeys { it != null }.mapKeys { it.key!!.lowercase() }
        conn.disconnect()
        return HttpResult(code, headers, text)
    }

    private fun login(pin: String = currentPin): String {
        val res = request("POST", "/api/v1/login", """{"username":"lan","password":"$pin"}""")
        assertEquals(200, res.code)
        val obj = MintJson.parseToJsonElement(res.body).jsonObject
        return obj["token"]!!.jsonPrimitive.content
    }

    @Test
    fun modeAndStaticAssetsWithTraversalGuard() {
        val mode = request("GET", "/api/v1/mode")
        assertEquals(200, mode.code)
        val modeObj = MintJson.parseToJsonElement(mode.body).jsonObject
        assertEquals("lan", modeObj["mode"]!!.jsonPrimitive.content)
        assertEquals("pin", modeObj["auth"]!!.jsonPrimitive.content)

        val index = request("GET", "/")
        assertEquals(200, index.code)
        assertEquals("text/html; charset=utf-8", index.header("content-type"))
        assertEquals("nosniff", index.header("x-content-type-options"))
        assertEquals("DENY", index.header("x-frame-options"))
        assertEquals("no-cache", index.header("cache-control"))

        assertEquals("application/javascript; charset=utf-8", request("GET", "/app/js/main.js").header("content-type"))
        assertEquals("text/css; charset=utf-8", request("GET", "/app/app.css").header("content-type"))
        assertEquals("image/svg+xml", request("GET", "/app/icon.svg").header("content-type"))
        assertEquals("image/png", request("GET", "/app/icon.png").header("content-type"))
        assertEquals("application/manifest+json; charset=utf-8", request("GET", "/app/manifest.webmanifest").header("content-type"))

        // 路径穿越尝试必须被拒绝（使用原始 Socket 绕过 HttpURLConnection 客户端规范化）
        for (evil in listOf("/app/../secret.txt", "/app/js/../../secret.txt", "/app/%2e%2e/secret.txt", "/app/..\\secret")) {
            Socket("127.0.0.1", server.boundPort).use { sock ->
                val out = sock.getOutputStream()
                out.write("GET $evil HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                val statusLine = sock.getInputStream().bufferedReader().readLine()
                assertTrue("Expected 403 for $evil but got: $statusLine", statusLine.contains(" 403 "))
            }
        }
    }

    @Test
    fun pinAuthLockoutMePasswordAndLogout() {
        assertEquals(401, request("GET", "/api/v1/me").code)

        // 连续 5 次错误连接码均返回 401，第 6 次触发 30 秒 429 锁定
        repeat(5) {
            val bad = request("POST", "/api/v1/login", """{"password":"000000"}""")
            assertEquals(401, bad.code)
        }
        val locked = request("POST", "/api/v1/login", """{"password":"$currentPin"}""")
        assertEquals(429, locked.code)
        assertEquals(
            "too_many_attempts",
            MintJson
                .parseToJsonElement(locked.body)
                .jsonObject["error"]!!
                .jsonPrimitive.content,
        )

        // 超过 30 秒后解锁，正确连接码可正常登录
        fakeClock.addAndGet(30_001L)
        val loginRes = request("POST", "/api/v1/login", """{"username":"lan","password":"$currentPin"}""")
        assertEquals(200, loginRes.code)
        val loginJson = MintJson.parseToJsonElement(loginRes.body).jsonObject
        val token = loginJson["token"]!!.jsonPrimitive.content
        val user = loginJson["user"]!!.jsonObject
        assertTrue(user["id"]!!.jsonPrimitive.long > 0L)
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        assertEquals("lan", user["mode"]!!.jsonPrimitive.content)

        val beforeActivity = activityCount.get()
        val meRes = request("GET", "/api/v1/me", bearer = token)
        assertEquals(200, meRes.code)
        val meJson = MintJson.parseToJsonElement(meRes.body).jsonObject
        assertEquals("本机 (局域网)", meJson["username"]!!.jsonPrimitive.content)
        assertEquals("user", meJson["role"]!!.jsonPrimitive.content)
        assertEquals("lan", meJson["mode"]!!.jsonPrimitive.content)
        assertTrue(activityCount.get() > beforeActivity)

        val pwRes = request("POST", "/api/v1/me/password", """{"oldPassword":"a","newPassword":"b"}""", bearer = token)
        assertEquals(400, pwRes.code)
        assertEquals(
            "unsupported",
            MintJson
                .parseToJsonElement(pwRes.body)
                .jsonObject["error"]!!
                .jsonPrimitive.content,
        )

        val logoutRes = request("POST", "/api/v1/logout", bearer = token)
        assertEquals(204, logoutRes.code)
        assertEquals(401, request("GET", "/api/v1/me", bearer = token).code)
    }

    @Test
    fun syncPullPaginationAndUndoTombstone() =
        runBlocking {
            val t1 = Task(Ids.next(), "待办一")
            val t2 = Task(Ids.next(), "待办二")
            val cs1 = ChangeSet(Ids.next(), "本地新增", Source.MANUAL, fakeClock.get(), listOf(Change.Add(t1), Change.Add(t2)))
            app.repo.commit(cs1)

            val token = login()
            val page1 = request("GET", "/api/v1/sync/pull?since=0&limit=2", bearer = token)
            assertEquals(200, page1.code)
            val p1 = MintJson.parseToJsonElement(page1.body).jsonObject
            assertEquals(2, p1["records"]!!.jsonArray.size)
            assertTrue(p1["more"]!!.jsonPrimitive.boolean)
            val c1 = p1["cursor"]!!.jsonPrimitive.long

            val page2 = request("GET", "/api/v1/sync/pull?since=$c1&limit=10", bearer = token)
            assertEquals(200, page2.code)
            val p2 = MintJson.parseToJsonElement(page2.body).jsonObject
            assertFalse(p2["more"]!!.jsonPrimitive.boolean)
            val c2 = p2["cursor"]!!.jsonPrimitive.long
            assertTrue(c2 > c1)

            // 无新变更时，cursor 保持等于 since
            val page3 = request("GET", "/api/v1/sync/pull?since=$c2&limit=10", bearer = token)
            val p3 = MintJson.parseToJsonElement(page3.body).jsonObject
            assertEquals(0, p3["records"]!!.jsonArray.size)
            assertEquals(c2, p3["cursor"]!!.jsonPrimitive.long)

            // 手机端撤销新增后，pull 应发出对应记录的墓碑
            assertTrue(app.repo.undo(cs1.id))
            val afterUndo = request("GET", "/api/v1/sync/pull?since=$c2&limit=50", bearer = token)
            val undoArr = MintJson.parseToJsonElement(afterUndo.body).jsonObject["records"]!!.jsonArray
            val tombIds =
                undoArr
                    .filter { it.jsonObject["deletedAt"]?.let { d -> d.toString() != "null" } == true }
                    .map { it.jsonObject["id"]!!.jsonPrimitive.content }
                    .toSet()
            assertTrue(t1.id in tombIds)
            assertTrue(t2.id in tombIds)
        }

    @Test
    fun syncPushLwwCreatesUndoableChangeSet() =
        runBlocking {
            val token = login()
            val newTask = Task(id = "web-task-1", title = "来自电脑的新待办", updatedAt = fakeClock.get() + 1000L)
            val wireNew = SyncCodec.encode(newTask)
            val pushBody = """{"records":[${MintJson.encodeToString(WireRecord.serializer(), wireNew)}]}"""

            val res1 = request("POST", "/api/v1/sync/push", pushBody, bearer = token)
            assertEquals(200, res1.code)
            val r1 = MintJson.parseToJsonElement(res1.body).jsonObject
            assertEquals(listOf("web-task-1"), r1["accepted"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(0, r1["rejected"]!!.jsonArray.size)
            assertTrue(r1["cursor"]!!.jsonPrimitive.long > 0L)

            val saved =
                app.repo.snapshot.value
                    .find("web-task-1") as? Task
            assertNotNull(saved)
            assertEquals("来自电脑的新待办", saved!!.title)

            // 验证产生了可撤销的「电脑端编辑」历史记录
            app.repo.ensureHistoryLoaded()
            val op =
                app.repo.history.value
                    .firstOrNull { it.description == "电脑端编辑" }
            assertNotNull(op)
            assertTrue(app.repo.undoable(op!!))

            // 推送旧时间戳记录应被 LWW 拒绝并返回服务端当前版本
            val staleTask = saved.copy(title = "过期修改", updatedAt = saved.updatedAt - 500L)
            val staleBody = """{"records":[${MintJson.encodeToString(WireRecord.serializer(), SyncCodec.encode(staleTask))}]}"""
            val res2 = request("POST", "/api/v1/sync/push", staleBody, bearer = token)
            assertEquals(200, res2.code)
            val r2 = MintJson.parseToJsonElement(res2.body).jsonObject
            assertEquals(0, r2["accepted"]!!.jsonArray.size)
            assertEquals(1, r2["rejected"]!!.jsonArray.size)
            assertEquals(
                "来自电脑的新待办",
                (
                    app.repo.snapshot.value
                        .find("web-task-1") as Task
                ).title,
            )

            // 在手机端撤销该次电脑端编辑后，记录被移除
            assertTrue(app.repo.undo(op.id))
            assertNull(
                app.repo.snapshot.value
                    .find("web-task-1"),
            )
        }

    private data class HttpResult(
        val code: Int,
        val headers: Map<String, List<String>>,
        val body: String,
    ) {
        fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
    }
}
