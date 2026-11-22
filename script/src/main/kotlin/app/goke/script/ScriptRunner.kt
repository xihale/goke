package app.goke.script

import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Conflicts
import app.goke.core.change.SnapshotDiff
import app.goke.core.change.apply
import app.goke.core.change.record
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.Event
import app.goke.core.model.Semester
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.util.IdGenerator
import app.goke.core.util.Ids
import app.goke.core.util.MintJson
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/** 沙箱运行参数。 */
data class ScriptOptions(
    val source: Source = Source.CODE,
    val today: LocalDate = LocalDate.now(),
    val nowMillis: Long = System.currentTimeMillis(),
    val timeoutMillis: Long = 5_000,
    val memoryLimitBytes: Long = 64L * 1024 * 1024,
    val maxStackBytes: Long = 512L * 1024,
)

sealed interface ScriptOutcome {
    /** Dry Run 成功：得到变更集（尚未落库）。 */
    data class Success(
        val changeSet: ChangeSet,
    ) : ScriptOutcome

    /** 脚本出错：message 为可读错误，line 为用户代码中的行号（若可知）。 */
    data class Failure(
        val message: String,
        val line: Int?,
        val logs: List<String>,
    ) : ScriptOutcome
}

@Serializable
private data class ScriptPayload(
    val semesters: List<Semester> = emptyList(),
    val courses: List<Course> = emptyList(),
    val exceptions: List<CourseException> = emptyList(),
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val logs: List<String> = emptyList(),
    val result: String? = null,
)

@Serializable
private data class SdkOptions(
    val source: String,
    val today: String,
    val nowMillis: Long,
)

object ScriptSdk {
    val js: String by lazy { read("/goke/sdk.js") }

    /** TypeScript 声明，同时用于 AI 系统提示与代码编辑器提示。 */
    val declarations: String by lazy { read("/goke/goke.d.ts") }

    private fun read(path: String): String =
        ScriptSdk::class.java
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("missing resource $path")
}

/**
 * 高级修改引擎：在 QuickJS 沙箱里运行脚本，只产出「变更集」，不触碰真实数据（Dry Run）。
 * 应用变更集由调用方通过 [app.goke.core.change.apply] 完成，因此预览与应用结果一致。
 */
class ScriptRunner(
    private val ids: IdGenerator = Ids,
) {
    suspend fun dryRun(
        code: String,
        before: Snapshot,
        options: ScriptOptions = ScriptOptions(),
        description: String = "",
    ): ScriptOutcome =
        withContext(Dispatchers.Default) {
            val live = before.live()
            val ordered =
                live.currentSemester?.let { cur ->
                    live.copy(semesters = listOf(cur) + live.semesters.filter { it.id != cur.id })
                } ?: live
            val dataJson = MintJson.encodeToString(Snapshot.serializer(), ordered)
            val optJson =
                MintJson.encodeToString(
                    SdkOptions.serializer(),
                    SdkOptions(options.source.name, options.today.toString(), options.nowMillis),
                )
            var userLogs: List<String> = emptyList()
            try {
                val payloadText =
                    quickJs {
                        memoryLimit = options.memoryLimitBytes
                        maxStackSize = options.maxStackBytes
                        evaluate<Any?>(ScriptSdk.js, "sdk.js")
                        evaluate<Any?>("var __mc = __createSdk(JSON.parse(${lit(dataJson)}), JSON.parse(${lit(optJson)}));", "init.js")
                        evaluationTimeoutMillis = options.timeoutMillis
                        try {
                            // 用户代码与包装函数头在同一行，保证报错行号与用户看到的一致
                            evaluate<Any?>("var __r = (function (mc, console) { $code\n}).call(undefined, __mc, __mc.console);", "script.js")
                        } catch (e: QuickJsException) {
                            userLogs = runCatching { logsOf(evaluate<String>("__mc.finish()")) }.getOrDefault(emptyList())
                            throw e
                        }
                        evaluationTimeoutMillis = 0
                        evaluate<String>("__mc.finish(__r)", "finish.js")
                    }
                val payload = MintJson.decodeFromString(ScriptPayload.serializer(), payloadText)
                val after = Snapshot(payload.semesters, payload.courses, payload.exceptions, payload.events, payload.tasks, payload.lists)
                val changes = SnapshotDiff.diff(ordered, after)
                val warnings = warnings(ordered, after, changes)
                ScriptOutcome.Success(
                    ChangeSet(
                        id = ids.next(),
                        description = description,
                        source = options.source,
                        createdAt = options.nowMillis,
                        changes = changes,
                        warnings = warnings,
                        logs = payload.logs,
                        returnValue = payload.result,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuickJsInterruptedException) {
                ScriptOutcome.Failure("脚本运行超过 ${options.timeoutMillis / 1000} 秒被中止，请检查是否有死循环", null, userLogs)
            } catch (e: QuickJsException) {
                ScriptOutcome.Failure(friendly(e.message), e.lineNumber?.takeIf { it > 0 }, userLogs)
            } catch (e: SerializationException) {
                ScriptOutcome.Failure("脚本产生的数据格式不正确：${e.message?.lineSequence()?.firstOrNull()}", null, userLogs)
            } catch (e: IllegalArgumentException) {
                ScriptOutcome.Failure("脚本产生的数据格式不正确：${e.message?.lineSequence()?.firstOrNull()}", null, userLogs)
            }
        }

    private fun logsOf(payloadText: String): List<String> = MintJson.decodeFromString(ScriptPayload.serializer(), payloadText).logs

    private fun friendly(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("out of memory", ignoreCase = true) -> "脚本占用内存过多，已中止"
            m.contains("stack overflow", ignoreCase = true) -> "脚本调用层级过深（可能是无限递归）"
            m.isBlank() -> "脚本运行出错"
            else -> m
        }
    }

    private fun lit(s: String) = JsonPrimitive(s).toString()

    /** 语义校验与冲突提示：不阻止应用，只提醒。 */
    private fun warnings(
        before: Snapshot,
        after: Snapshot,
        changes: List<Change>,
    ): List<String> {
        val out = mutableListOf<String>()
        val semester = after.currentSemester
        if (semester != null) {
            val periodMax = semester.periods.maxOfOrNull { it.index } ?: 0
            for (c in changes) {
                val r = c.record
                if (c is Change.Remove || r !is Course) continue
                if (r.weeks.any { it !in 1..semester.totalWeeks }) out += "「${r.name}」的周次超出学期范围（1–${semester.totalWeeks} 周）"
                if (periodMax > 0 && r.endPeriod > periodMax) out += "「${r.name}」的节次超出每天 $periodMax 节"
            }
        }
        val focus = Conflicts.focusIds(ChangeSet(id = "", changes = changes))
        if (focus.isNotEmpty()) {
            val applied = before.apply(ChangeSet(id = "", changes = changes), 0)
            for (cf in Conflicts.detect(applied, focus)) {
                out += "「${cf.aTitle}」与「${cf.bTitle}」在 ${cf.start.toLocalDate()} 时间冲突"
            }
        }
        return out
    }
}
