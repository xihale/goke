package app.goke

import androidx.test.core.app.ApplicationProvider
import app.goke.core.model.Course
import app.goke.data.DemoData
import app.goke.mcp.LocalMcpServer
import app.goke.mcp.LocalMcpTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = SmokeTestApp::class, sdk = [35])
class LocalMcpServerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var app: SmokeTestApp
    private lateinit var server: LocalMcpServer
    private val token = "mc_test_secret_token_1234567890"

    @Before
    fun setUp() =
        runBlocking {
            app = ApplicationProvider.getApplicationContext()
            app.repo.load()
            val sem = app.repo.snapshot.value.currentSemester!!
            val demo =
                DemoData.build(
                    LocalDateTime.of(2026, 9, 14, 9, 0),
                    sem.id,
                    replace = true,
                    existing =
                        app.repo.snapshot.value
                            .allRecords(),
                )
            app.repo.commit(demo)
            app.advanced.discard()
            val tools = LocalMcpTools(app.repo, app.advanced)
            server =
                LocalMcpServer(
                    tools = tools,
                    host = "127.0.0.1",
                    port = 0,
                    tokenProvider = { token },
                )
            server.start()
        }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.boundPort}"

    private fun postMcp(
        body: String,
        bearer: String? = token,
    ): Pair<Int, String> {
        val conn = (URL("${baseUrl()}/mcp").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to resp
    }

    private fun callTool(
        name: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ): Pair<Boolean, JsonObject> {
        val req =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                putJsonObject("params") {
                    put("name", name)
                    put("arguments", arguments)
                }
            }
        val (status, raw) = postMcp(req.toString())
        assertEquals(200, status)
        val root = json.parseToJsonElement(raw).jsonObject
        val result =
            requireNotNull(root["result"]) { "Expected JSON-RPC result in: $raw" }
                .jsonObject
        val isError = result["isError"]?.jsonPrimitive?.boolean ?: false
        val text =
            result["content"]!!
                .jsonArray[0]
                .jsonObject["text"]!!
                .jsonPrimitive.content
        val payload =
            if (isError) {
                buildJsonObject { put("error", text) }
            } else {
                json.parseToJsonElement(text).jsonObject
            }
        return isError to payload
    }

    @Test
    fun authAndProtocolLifecycle() {
        // 未带或带错令牌均拒绝
        assertEquals(401, postMcp("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", bearer = null).first)
        assertEquals(401, postMcp("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", bearer = "wrong").first)

        // GET /mcp 按无状态 Streamable HTTP 规范返回 405
        val getConn = (URL("${baseUrl()}/mcp").openConnection() as HttpURLConnection)
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("Authorization", "Bearer $token")
        assertEquals(405, getConn.responseCode)
        getConn.disconnect()

        // initialize -> 200
        val (initCode, initBody) =
            postMcp(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}""",
            )
        assertEquals(200, initCode)
        val initRes =
            json
                .parseToJsonElement(initBody)
                .jsonObject["result"]!!
                .jsonObject
        assertEquals("2025-03-26", initRes["protocolVersion"]!!.jsonPrimitive.content)

        // notifications/initialized -> 202 Accepted 空响应
        val (notifCode, notifBody) = postMcp("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertEquals(202, notifCode)
        assertTrue(notifBody.isEmpty())

        // tools/list 包含 14 个工具且暴露高级修改工具
        val (listCode, listBody) = postMcp("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        assertEquals(200, listCode)
        val toolNames =
            json
                .parseToJsonElement(listBody)
                .jsonObject["result"]!!
                .jsonObject["tools"]!!
                .jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
                .toSet()
        assertTrue("get_script_sdk" in toolNames)
        assertTrue("preview_script" in toolNames)
        assertTrue("apply_script" in toolNames)
        assertTrue("undo_change" in toolNames)
        assertTrue("get_agenda" in toolNames)
    }

    @Test
    fun advancedScriptPreviewApplyAndUndoOverMcp() {
        val (sdkErr, sdk) = callTool("get_script_sdk")
        assertFalse(sdkErr)
        assertTrue(sdk["declarations"]!!.jsonPrimitive.content.contains("courses"))

        val targetCourse: Course =
            app.repo.snapshot.value
                .live()
                .courses
                .first()
        val originalRoom = targetCourse.room

        // 1. Dry Run 预览：数据库未改，但课表预览桥已收到暂存变更集
        val script =
            """
            const c = mc.courses.get('${targetCourse.id}');
            mc.courses.update(c.id, { room: '实验楼B-502' });
            console.log('updated ' + c.name);
            """.trimIndent()
        val (prevErr, prev) =
            callTool(
                "preview_script",
                buildJsonObject {
                    put("code", script)
                    put("description", "改实验教室")
                    put("showOnTimetable", true)
                },
            )
        assertFalse(prevErr)
        val previewId = prev["previewId"]!!.jsonPrimitive.content
        assertEquals(1, prev["changeCount"]!!.jsonPrimitive.int)
        assertEquals(
            originalRoom,
            app.repo.snapshot.value
                .live()
                .courses
                .first { it.id == targetCourse.id }
                .room,
        )
        assertNotNull(app.advanced.state.value.changeSet)
        assertTrue(app.advanced.state.value.previewOnTimetable)

        // 2. Apply 提交：原子写入本地数据库并清除课表暂存预览
        val (applyErr, applied) =
            callTool(
                "apply_script",
                buildJsonObject { put("previewId", previewId) },
            )
        assertFalse(applyErr)
        val csId = applied["changeSetId"]!!.jsonPrimitive.content
        assertEquals(
            "实验楼B-502",
            app.repo.snapshot.value
                .live()
                .courses
                .first { it.id == targetCourse.id }
                .room,
        )
        assertNull(app.advanced.state.value.changeSet)

        // 3. Undo 一键撤回：还原回原教室
        val (undoErr, undone) =
            callTool(
                "undo_change",
                buildJsonObject { put("changeSetId", csId) },
            )
        assertFalse(undoErr)
        assertTrue(undone["undone"]!!.jsonPrimitive.boolean)
        assertEquals(
            originalRoom,
            app.repo.snapshot.value
                .live()
                .courses
                .first { it.id == targetCourse.id }
                .room,
        )
    }

    @Test
    fun createTaskAndQueryAgendaOverMcp() {
        val (createErr, created) =
            callTool(
                "create_task",
                buildJsonObject {
                    put("title", "MCP 联调测试待办")
                    put("due", "2026-09-15 16:00")
                    put("priority", 2)
                },
            )
        assertFalse(createErr)
        val taskId = created["id"]!!.jsonPrimitive.content

        val (agendaErr, agenda) =
            callTool(
                "get_agenda",
                buildJsonObject {
                    put("from", "2026-09-14")
                    put("to", "2026-09-16")
                },
            )
        assertFalse(agendaErr)
        val daysText = agenda["days"].toString()
        assertTrue(daysText.contains(taskId))
        assertTrue(daysText.contains("MCP 联调测试待办"))
    }
}
