package app.mintcal.ai

import app.mintcal.core.model.Snapshot
import app.mintcal.core.time.WeekSets
import app.mintcal.script.ScriptSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class AiException(
    message: String,
) : Exception(message)

/**
 * OpenAI 兼容的 chat/completions 客户端。
 * AI 只负责「把自然语言写成 SDK 代码」，结果必须先 Dry Run 预览，用户确认后才会修改数据。
 * 只向用户自己配置的地址发送：任务描述 + 课表摘要（课程名/时间/周次/教室），不含 Key 以外的任何账号信息。
 */
class AiClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateCode(
        baseUrl: String,
        apiKey: String,
        model: String,
        description: String,
        snapshot: Snapshot,
        previousCode: String? = null,
        previousError: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val messages =
                buildJsonArray {
                    add(msg("system", systemPrompt()))
                    add(msg("user", userPrompt(description, snapshot)))
                    if (previousCode != null && previousError != null) {
                        add(msg("assistant", "```js\n$previousCode\n```"))
                        add(msg("user", "上面的代码在预览时出错：$previousError\n请修正后只输出完整的新代码。"))
                    }
                }
            val body =
                buildJsonObject {
                    put("model", model)
                    put("temperature", 0.2)
                    put("messages", messages)
                }
            val content = post(baseUrl, apiKey, body.toString())
            extractCode(content)
        }

    private fun msg(
        role: String,
        content: String,
    ) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private fun post(
        baseUrl: String,
        apiKey: String,
        body: String,
    ): String {
        val url = URL(baseUrl.trim().trimEnd('/') + "/chat/completions")
        val conn =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail =
                    runCatching {
                        json
                            .parseToJsonElement(text)
                            .jsonObject["error"]
                            ?.jsonObject
                            ?.get("message")
                            ?.jsonPrimitive
                            ?.contentOrNull
                    }.getOrNull()
                throw AiException("HTTP $code ${detail ?: text.take(200)}")
            }
            val root = json.parseToJsonElement(text).jsonObject
            return root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: throw AiException("响应里没有内容")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val FENCE = Regex("```(?:js|javascript|JS|typescript|ts)?\\s*\\n([\\s\\S]*?)```")

        fun extractCode(content: String): String =
            FENCE
                .find(content)
                ?.groupValues
                ?.get(1)
                ?.trim() ?: content.trim()

        fun systemPrompt(): String =
            """
            你是「薄荷日程」的代码助手。用户用自然语言描述想对课表 / 日程 / 待办做的批量修改，你要写一段 JavaScript 完成它。
            规则：
            1. 只输出一个 ```js 代码块，不要任何解释文字。
            2. 代码在沙箱里同步运行，只能使用全局对象 mc 与 console（没有网络、文件、定时器、require/import）。
            3. 先用 mc.courses.query / mc.events.query / mc.tasks.query 找到目标，不要凭空编造 id。
            4. 文本字段（name、room 等）的字符串查询是「包含」匹配；需要精确匹配时用函数条件。
            5. 修改用 update / create / remove / cancel / reschedule；脚本只会生成变更预览，用户确认后才会真正应用。
            6. 最后用 console.log 用中文简要说明做了什么（如「共修改 3 门课」）。
            7. 日期用 "YYYY-MM-DD"，日期时间用 "YYYY-MM-DDTHH:mm"；dayOfWeek 1=周一…7=周日；weeks 可写 "1-16"、"1-16单"、"2-16双"、"1,3,5-9" 或数组。
            8. 周次、节次不确定时按课表摘要里的实际数据推断，不要改动用户没提到的内容。

            SDK 类型声明：
            ```ts
            ${ScriptSdk.declarations}
            ```
            """.trimIndent()

        fun userPrompt(
            description: String,
            s: Snapshot,
        ): String {
            val live = s.live()
            val sem = live.currentSemester
            val sb = StringBuilder()
            if (sem != null) {
                sb.append("当前学期：${sem.name}，第 1 周周一 ${sem.week1Monday}，共 ${sem.totalWeeks} 周，每天 ${sem.periods.size} 节。\n")
                sb.append("课程（${live.courses.size} 门）：\n")
                live.courses.take(80).forEach {
                    sb.append("- ${it.name} | 周${it.dayOfWeek} 第${it.startPeriod}-${it.endPeriod}节 | ${WeekSets.format(it.weeks)}周 | ${it.room}${if (it.teacher.isNotBlank()) " | " + it.teacher else ""}\n")
                }
            }
            sb.append("日程 ${live.events.size} 条，待办 ${live.tasks.size} 条，清单：${live.lists.joinToString("、") { it.name }.ifBlank { "无" }}。\n")
            sb.append("今天是 ${java.time.LocalDate.now()}。\n\n用户需求：\n").append(description.trim())
            return sb.toString()
        }
    }
}
