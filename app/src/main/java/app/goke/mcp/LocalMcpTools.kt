package app.goke.mcp

import app.goke.advanced.AdvancedController
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.EventItem
import app.goke.core.agenda.TaskItem
import app.goke.core.change.ChangeSet
import app.goke.core.change.ChangeViews
import app.goke.core.change.Edits
import app.goke.core.model.Freq
import app.goke.core.model.Recurrence
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.util.Ids
import app.goke.core.util.MintJson
import app.goke.data.Repository
import app.goke.script.ScriptOptions
import app.goke.script.ScriptOutcome
import app.goke.script.ScriptRunner
import app.goke.script.ScriptSdk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections

internal class McpToolError(
    override val message: String,
) : RuntimeException(message)

internal data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val handler: suspend (JsonObject) -> JsonElement,
)

/**
 * 本地 MCP 工具集：
 * 1. 待办 / 清单 / 日程 / 课表标准工具（与服务端 MCP 对齐，直接读写本地 [Repository] 并记入可撤销历史）；
 * 2. 独占「高级修改」工具（`get_script_sdk`、`preview_script`、`apply_script`、`undo_change`），
 *    在 QuickJS 沙箱中执行批量调课、停课、作息或待办整理脚本，支持课表实时差异预览与一键撤销。
 */
internal class LocalMcpTools(
    private val repo: Repository,
    private val advanced: AdvancedController? = null,
    private val runner: ScriptRunner = ScriptRunner(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val previews: MutableMap<String, ChangeSet> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, ChangeSet>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChangeSet>?): Boolean = size > 16
            },
        )

    init {
        advanced?.addOnConsumedListener { previewId ->
            previews.remove(previewId)
        }
    }

    val definitions: List<McpToolDef> =
        listOf(
            McpToolDef(
                name = "list_lists",
                description = "列出全部待办清单，包含每个清单的未完成与已完成数量",
                inputSchema = emptyObjectSchema(),
                handler = { listLists() },
            ),
            McpToolDef(
                name = "create_list",
                description = "新建待办清单",
                inputSchema =
                    objectSchema(
                        required = listOf("name"),
                        "name" to prop("string", "清单名称（必填）"),
                        "colorIndex" to prop("integer", "配色序号 0..7，默认 1"),
                    ),
                handler = ::createList,
            ),
            McpToolDef(
                name = "get_task",
                description = "按 id 查看单条待办完整内容及其子任务",
                inputSchema =
                    objectSchema(
                        required = listOf("id"),
                        "id" to prop("string", "待办 id（必填）"),
                    ),
                handler = ::getTask,
            ),
            McpToolDef(
                name = "search_tasks",
                description = "搜索待办：支持关键词、清单、状态（open/done/dropped/all）、日期范围",
                inputSchema =
                    objectSchema(
                        required = emptyList(),
                        "query" to prop("string", "关键词：匹配标题、备注、标签"),
                        "listId" to prop("string", "限定清单 id"),
                        "status" to prop("string", "open / done / dropped / all，默认 open"),
                        "from" to prop("string", "起始日期 YYYY-MM-DD（含）"),
                        "to" to prop("string", "结束日期 YYYY-MM-DD（含）"),
                        "limit" to prop("integer", "最多返回条数，默认 50，上限 200"),
                    ),
                handler = ::searchTasks,
            ),
            McpToolDef(
                name = "create_task",
                description = "新建待办或排入日历的时间块；带具体时刻或 blockStart 即作为日程时间块",
                inputSchema =
                    objectSchema(
                        required = listOf("title"),
                        "title" to prop("string", "标题（必填）"),
                        "note" to prop("string", "备注"),
                        "listId" to prop("string", "清单 id；缺省进入收集箱"),
                        "priority" to prop("integer", "优先级：0 无 / 1 低 / 2 中 / 3 高"),
                        "startDate" to prop("string", "开始日期 YYYY-MM-DD"),
                        "due" to prop("string", "截止日期或时刻：YYYY-MM-DD 或 YYYY-MM-DD HH:MM"),
                        "remindMinutes" to prop("integer", "提前提醒分钟数"),
                        "remindMinutesList" to arrayProp("integer", "多个提前提醒分钟数列表"),
                        "repeat" to repeatSchema("重复规则"),
                        "parentId" to prop("string", "父待办 id（仅支持一层子任务）"),
                        "courseId" to prop("string", "关联课程 id"),
                        "dueBeforeNextClass" to prop("boolean", "是否截止于关联课程的下节课前"),
                        "blockStart" to prop("string", "排入日历开始时刻 YYYY-MM-DD HH:MM"),
                        "blockMinutes" to prop("integer", "排入日历时长（分钟）"),
                        "tags" to arrayProp("string", "标签列表"),
                    ),
                handler = ::createTask,
            ),
            McpToolDef(
                name = "update_task",
                description = "按需更新待办字段；未传字段保持不变，空字符串可清空可空文本/时间字段",
                inputSchema =
                    objectSchema(
                        required = listOf("id"),
                        "id" to prop("string", "待办 id（必填）"),
                        "title" to prop("string", "新标题"),
                        "note" to prop("string", "新备注"),
                        "listId" to prop("string", "清单 id；传空字符串回到收集箱"),
                        "priority" to prop("integer", "0 无 / 1 低 / 2 中 / 3 高"),
                        "startDate" to prop("string", "开始日期 YYYY-MM-DD；传空字符串清除"),
                        "due" to prop("string", "截止 YYYY-MM-DD 或 YYYY-MM-DD HH:MM；传空字符串清除"),
                        "remindMinutes" to prop("integer", "提前提醒分钟数；传 -1 清除提醒"),
                        "remindMinutesList" to arrayProp("integer", "多个提前提醒分钟数列表；传空数组清除"),
                        "repeat" to repeatSchema("重复规则；freq 传 NONE 清除重复"),
                        "courseId" to prop("string", "关联课程 id；传空字符串清除"),
                        "dueBeforeNextClass" to prop("boolean", "是否下节课前截止"),
                        "blockStart" to prop("string", "排入日历开始时刻 YYYY-MM-DD HH:MM；传空字符串清除"),
                        "blockMinutes" to prop("integer", "排入日历时长（分钟）；传 0 清除"),
                        "tags" to arrayProp("string", "整体替换标签列表"),
                    ),
                handler = ::updateTask,
            ),
            McpToolDef(
                name = "complete_task",
                description = "完成、重开或放弃（「不做了」）一条待办；重复任务完成时会自动生成下一期",
                inputSchema =
                    objectSchema(
                        required = listOf("id"),
                        "id" to prop("string", "待办 id（必填）"),
                        "done" to prop("boolean", "true 完成（默认）/ false 重开"),
                        "dropped" to prop("boolean", "true 标记为「不做了」（放弃）"),
                    ),
                handler = ::completeTask,
            ),
            McpToolDef(
                name = "delete_task",
                description = "删除一条待办（移入回收站并支持撤销）",
                inputSchema =
                    objectSchema(
                        required = listOf("id"),
                        "id" to prop("string", "待办 id（必填）"),
                    ),
                handler = ::deleteTask,
            ),
            McpToolDef(
                name = "get_agenda",
                description = "查询日期区间 [from, to] 内每天的完整日程（自动展开学期周次课表、调停课、时间块与到期任务）",
                inputSchema =
                    objectSchema(
                        required = listOf("from", "to"),
                        "from" to prop("string", "起始日期 YYYY-MM-DD（含）"),
                        "to" to prop("string", "结束日期 YYYY-MM-DD（含）"),
                        "includeDone" to prop("boolean", "是否包含已完成待办，默认 false"),
                    ),
                handler = ::getAgenda,
            ),
            McpToolDef(
                name = "list_courses",
                description = "列出学期配置、每周课程表与单周调停课记录（CourseException）",
                inputSchema = emptyObjectSchema(),
                handler = { listCourses() },
            ),
            McpToolDef(
                name = "get_script_sdk",
                description = "【高级修改必读】获取 Goke QuickJS 脚本沙箱的 TypeScript 声明（goke.d.ts）与常用示例。在对课程、学期、调停课或大批量待办做编程式高级修改前请先调用此工具。",
                inputSchema = emptyObjectSchema(),
                handler = { getScriptSdk() },
            ),
            McpToolDef(
                name = "preview_script",
                description = "【高级修改 Dry Run】在沙箱快照上运行 JavaScript 代码，不写入数据库，返回结构化变更预览、冲突警告与 console.log 输出，并可在手机课表上实时高亮差异。",
                inputSchema =
                    objectSchema(
                        required = listOf("code"),
                        "code" to prop("string", "JavaScript 脚本代码（全局对象为 mc，API 见 get_script_sdk）"),
                        "description" to prop("string", "本次修改的简短说明，展示在预览横幅与操作记录中"),
                        "showOnTimetable" to prop("boolean", "是否在手机课表页叠加展示差异预览，默认 true"),
                    ),
                handler = ::previewScript,
            ),
            McpToolDef(
                name = "apply_script",
                description = "【高级修改提交】应用已预览的 previewId 或直接运行并提交 JavaScript 高级修改脚本。修改会原子落库并记入「操作记录」，可随时调用 undo_change 一键撤回。",
                inputSchema =
                    objectSchema(
                        required = emptyList(),
                        "previewId" to prop("string", "preview_script 返回的 previewId（优先使用，确保与预览完全一致）"),
                        "code" to prop("string", "若未传 previewId，可直接传入要执行并提交的 JavaScript 代码"),
                        "description" to prop("string", "写入操作记录的简短说明"),
                    ),
                handler = ::applyScript,
            ),
            McpToolDef(
                name = "undo_change",
                description = "撤销一次修改（默认撤销最近一次未撤销的脚本/MCP 修改，也可指定 changeSetId）",
                inputSchema =
                    objectSchema(
                        required = emptyList(),
                        "changeSetId" to prop("string", "要撤销的变更集 id；缺省撤销最近一条可撤销操作"),
                    ),
                handler = ::undoChange,
            ),
        )

    private val byName = definitions.associateBy { it.name }

    suspend fun call(
        name: String,
        arguments: JsonObject,
    ): JsonElement {
        val tool = byName[name] ?: throw McpToolError("未知工具：$name")
        return tool.handler(arguments)
    }

    private fun listLists(): JsonElement {
        val snap = repo.snapshot.value.live()
        val counts = HashMap<String, IntArray>()
        for (t in snap.tasks) {
            if (t.parentId != null) continue
            val k = t.listId.orEmpty()
            val pair = counts.getOrPut(k) { IntArray(2) }
            if (t.done) pair[1]++ else pair[0]++
        }
        val arr =
            buildJsonArray {
                for (l in snap.lists.sortedBy { it.sort }) {
                    val c = counts[l.id] ?: IntArray(2)
                    add(
                        buildJsonObject {
                            put("id", l.id)
                            put("name", l.name)
                            put("colorIndex", l.colorIndex)
                            put("sort", l.sort)
                            put("open", c[0])
                            put("done", c[1])
                        },
                    )
                }
                val inbox = counts[""] ?: IntArray(2)
                add(
                    buildJsonObject {
                        put("id", "")
                        put("name", "收集箱")
                        put("open", inbox[0])
                        put("done", inbox[1])
                    },
                )
            }
        return buildJsonObject { put("lists", arr) }
    }

    private suspend fun createList(args: JsonObject): JsonElement {
        val name = args.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("清单名称不能为空")
        val color = (args.int("colorIndex") ?: 1).coerceIn(0, 7)
        val snap = repo.snapshot.value.live()
        val sort = (snap.lists.maxOfOrNull { it.sort } ?: 0) + 1
        val now = clock()
        val list = TaskList(id = Ids.next(), name = name, colorIndex = color, sort = sort, updatedAt = now)
        val cs = Edits.save(snap, list, "MCP 新建清单「$name」", now).copy(source = Source.CODE)
        repo.commit(cs)
        return buildJsonObject {
            put("id", list.id)
            put("name", list.name)
            put("colorIndex", list.colorIndex)
            put("changeSetId", cs.id)
        }
    }

    private fun getTask(args: JsonObject): JsonElement {
        val id = args.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("缺少待办 id")
        val snap = repo.snapshot.value.live()
        val task = snap.tasks.firstOrNull { it.id == id } ?: throw McpToolError("未找到待办：$id")
        val listName = task.listId?.let { lid -> snap.lists.firstOrNull { it.id == lid }?.name }.orEmpty()
        val subtasks = snap.tasks.filter { it.parentId == task.id }.sortedBy { it.sortOrder }
        return buildJsonObject {
            put("listName", listName)
            put("task", MintJson.encodeToJsonElement(Task.serializer(), task))
            putJsonArray("subtasks") {
                subtasks.forEach { add(MintJson.encodeToJsonElement(Task.serializer(), it)) }
            }
        }
    }

    private fun searchTasks(args: JsonObject): JsonElement {
        val status = args.str("status")?.lowercase()?.ifBlank { "open" } ?: "open"
        val limit = (args.int("limit") ?: 50).coerceIn(1, 200)
        val from = args.str("from")?.takeIf { it.isNotBlank() }?.let(::parseDateStrict)
        val to = args.str("to")?.takeIf { it.isNotBlank() }?.let(::parseDateStrict)
        val q =
            args
                .str("query")
                ?.trim()
                ?.lowercase()
                .orEmpty()
        val listFilter = args.str("listId")
        val snap = repo.snapshot.value.live()
        val listNames = snap.lists.associate { it.id to it.name }

        val matched =
            snap.tasks
                .filter { t ->
                    val statusOk =
                        when (status) {
                            "open" -> !t.done
                            "done" -> t.done && !t.dropped
                            "dropped" -> t.dropped
                            "all" -> true
                            else -> throw McpToolError("status 仅支持 open / done / dropped / all")
                        }
                    val listOk = listFilter == null || (listFilter.isEmpty() && t.listId == null) || t.listId == listFilter
                    val textOk =
                        q.isEmpty() ||
                            t.title.lowercase().contains(q) ||
                            t.note.lowercase().contains(q) ||
                            t.tags.any { it.lowercase().contains(q) }
                    val anchorDate = t.blockStart?.toLocalDate() ?: t.due?.toLocalDate() ?: t.startDate
                    val dateOk =
                        (from == null && to == null) ||
                            (
                                anchorDate != null &&
                                    (from == null || !anchorDate.isBefore(from)) &&
                                    (to == null || !anchorDate.isAfter(to))
                            )
                    statusOk && listOk && textOk && dateOk
                }.take(limit)

        return buildJsonObject {
            put("total", matched.size)
            putJsonArray("tasks") {
                matched.forEach { t ->
                    add(
                        buildJsonObject {
                            put("id", t.id)
                            put("title", t.title)
                            if (t.note.isNotBlank()) put("note", t.note)
                            put("listId", t.listId)
                            put("listName", t.listId?.let { listNames[it] }.orEmpty())
                            put("priority", t.priority)
                            put("done", t.done)
                            put("dropped", t.dropped)
                            t.due?.let { put("due", if (t.dueHasTime) it.format(DT_FMT) else it.toLocalDate().toString()) }
                            t.blockStart?.let { put("blockStart", it.format(DT_FMT)) }
                            t.blockMinutes?.let { put("blockMinutes", it) }
                            t.courseId?.let { put("courseId", it) }
                            if (t.tags.isNotEmpty()) {
                                putJsonArray("tags") { t.tags.forEach { tag -> add(JsonPrimitive(tag)) } }
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun createTask(args: JsonObject): JsonElement {
        val title = args.str("title")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("待办标题不能为空")
        val snap = repo.snapshot.value.live()
        val listId = args.str("listId")?.takeIf { it.isNotBlank() }
        if (listId != null && snap.lists.none { it.id == listId }) {
            throw McpToolError("清单不存在：$listId")
        }
        val parentId = args.str("parentId")?.takeIf { it.isNotBlank() }
        if (parentId != null) {
            val p = snap.tasks.firstOrNull { it.id == parentId } ?: throw McpToolError("父待办不存在：$parentId")
            if (p.parentId != null) throw McpToolError("仅支持一层子任务")
        }
        val courseId = args.str("courseId")?.takeIf { it.isNotBlank() }
        if (courseId != null && snap.courses.none { it.id == courseId }) {
            throw McpToolError("课程不存在：$courseId")
        }
        val startDate = args.str("startDate")?.takeIf { it.isNotBlank() }?.let(::parseDateStrict)
        val parsedDue = args.str("due")?.takeIf { it.isNotBlank() }?.let(::parseDateOrDateTime)
        val blockStart = args.str("blockStart")?.takeIf { it.isNotBlank() }?.let(::parseDateTimeStrict)
        val blockMinutes = args.int("blockMinutes")?.takeIf { it > 0 }
        val repeat = args["repeat"]?.jsonObject?.let(::parseRepeat)
        val tags = args.strList("tags")
        val now = clock()

        val reminders =
            if ("remindMinutesList" in args) {
                args.intList("remindMinutesList")
            } else {
                listOfNotNull(args.int("remindMinutes")?.takeIf { it >= 0 })
            }
        val task =
            Task(
                id = Ids.next(),
                title = title,
                note = args.str("note").orEmpty(),
                listId = listId,
                tags = tags,
                priority = (args.int("priority") ?: 0).coerceIn(0, 3),
                startDate = startDate,
                due = parsedDue?.first,
                dueHasTime = parsedDue?.second ?: false,
                parentId = parentId,
                repeat = repeat,
                remindMinutes = reminders.firstOrNull(),
                remindMinutesList = reminders,
                courseId = courseId,
                dueBeforeNextClass = args.bool("dueBeforeNextClass") ?: false,
                blockStart = blockStart,
                blockMinutes = if (blockStart != null) (blockMinutes ?: 30) else null,
                source = Source.CODE,
                updatedAt = now,
            )
        val cs = Edits.save(snap, task, "MCP 新建待办「$title」", now).copy(source = Source.CODE)
        repo.commit(cs)
        return buildJsonObject {
            put("id", task.id)
            put("title", task.title)
            put("changeSetId", cs.id)
        }
    }

    private suspend fun updateTask(args: JsonObject): JsonElement {
        val id = args.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("缺少待办 id")
        val snap = repo.snapshot.value.live()
        val cur = snap.tasks.firstOrNull { it.id == id } ?: throw McpToolError("未找到待办：$id")

        var next = cur
        args.str("title")?.let { t ->
            val trimmed = t.trim()
            if (trimmed.isEmpty()) throw McpToolError("标题不能为空")
            next = next.copy(title = trimmed)
        }
        if ("note" in args) next = next.copy(note = args.str("note").orEmpty())
        if ("listId" in args) {
            val lid = args.str("listId")?.takeIf { it.isNotBlank() }
            if (lid != null && snap.lists.none { it.id == lid }) throw McpToolError("清单不存在：$lid")
            next = next.copy(listId = lid)
        }
        args.int("priority")?.let { next = next.copy(priority = it.coerceIn(0, 3)) }
        if ("startDate" in args) {
            val raw = args.str("startDate").orEmpty().trim()
            next = next.copy(startDate = if (raw.isEmpty()) null else parseDateStrict(raw))
        }
        if ("due" in args) {
            val raw = args.str("due").orEmpty().trim()
            if (raw.isEmpty()) {
                next = next.copy(due = null, dueHasTime = false)
            } else {
                val (dt, hasTime) = parseDateOrDateTime(raw)
                next = next.copy(due = dt, dueHasTime = hasTime)
            }
        }
        if ("remindMinutesList" in args) {
            val list = args.intList("remindMinutesList")
            next = next.copy(remindMinutes = list.firstOrNull(), remindMinutesList = list)
        } else if ("remindMinutes" in args) {
            val rm = args.int("remindMinutes")
            val list = if (rm == null || rm < 0) emptyList() else listOf(rm)
            next = next.copy(remindMinutes = list.firstOrNull(), remindMinutesList = list)
        }
        if ("repeat" in args) {
            val repObj = args["repeat"]
            next = next.copy(repeat = if (repObj == null || repObj is JsonNull) null else parseRepeat(repObj.jsonObject))
        }
        if ("courseId" in args) {
            val cid = args.str("courseId")?.takeIf { it.isNotBlank() }
            if (cid != null && snap.courses.none { it.id == cid }) throw McpToolError("课程不存在：$cid")
            next = next.withCourseLink(cid, courseWeek = if (cid == next.courseId) next.courseWeek else null, dueBeforeNextClass = next.dueBeforeNextClass)
        }
        args.bool("dueBeforeNextClass")?.let { next = next.copy(dueBeforeNextClass = it) }
        if ("blockStart" in args) {
            val raw = args.str("blockStart").orEmpty().trim()
            next =
                if (raw.isEmpty()) {
                    next.copy(blockStart = null, blockMinutes = null)
                } else {
                    next.copy(blockStart = parseDateTimeStrict(raw), blockMinutes = next.blockMinutes ?: 30)
                }
        }
        if ("blockMinutes" in args) {
            val bm = args.int("blockMinutes") ?: 0
            next = next.copy(blockMinutes = bm.takeIf { it > 0 })
        }
        if ("tags" in args) {
            next = next.copy(tags = args.strList("tags"))
        }

        val now = clock()
        val cs = Edits.save(snap, next, "MCP 修改待办「${next.title}」", now).copy(source = Source.CODE)
        repo.commit(cs)
        return buildJsonObject {
            put("id", next.id)
            put("changeSetId", cs.id)
            put("task", MintJson.encodeToJsonElement(Task.serializer(), next))
        }
    }

    private suspend fun completeTask(args: JsonObject): JsonElement {
        val id = args.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("缺少待办 id")
        val snap = repo.snapshot.value.live()
        val cur = snap.tasks.firstOrNull { it.id == id } ?: throw McpToolError("未找到待办：$id")
        val dropped = args.bool("dropped") ?: false
        val done = args.bool("done") ?: true
        val now = clock()
        val cs =
            if (dropped) {
                Edits.dropTasks(listOf(cur), "MCP 放弃待办「${cur.title}」", now).copy(source = Source.CODE)
            } else {
                Edits.toggleTask(snap, cur, done, now, LocalDateTime.now(), "MCP 完成待办「${cur.title}」").copy(source = Source.CODE)
            }
        repo.commit(cs)
        return buildJsonObject {
            put("id", cur.id)
            put("done", if (dropped) true else done)
            put("dropped", dropped)
            put("changeSetId", cs.id)
        }
    }

    private suspend fun deleteTask(args: JsonObject): JsonElement {
        val id = args.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpToolError("缺少待办 id")
        val snap = repo.snapshot.value.live()
        val cur = snap.tasks.firstOrNull { it.id == id } ?: throw McpToolError("未找到待办：$id")
        val now = clock()
        val cs = Edits.delete(snap, cur, "MCP 删除待办「${cur.title}」", now).copy(source = Source.CODE)
        repo.commit(cs)
        return buildJsonObject {
            put("deleted", id)
            put("changeSetId", cs.id)
        }
    }

    private fun getAgenda(args: JsonObject): JsonElement {
        val from = parseDateStrict(args.str("from") ?: throw McpToolError("缺少起始日期 from"))
        val to = parseDateStrict(args.str("to") ?: throw McpToolError("缺少结束日期 to"))
        if (to.isBefore(from)) throw McpToolError("结束日期不能早于起始日期")
        if (from.plusDays(92).isBefore(to)) throw McpToolError("单次查询跨度请不超过 92 天")
        val includeDone = args.bool("includeDone") ?: false
        val snap = repo.snapshot.value.live()
        val byDay = Agenda.range(snap, from, to, includeDoneTasks = includeDone)

        val days =
            buildJsonArray {
                for ((date, items) in byDay.entries.sortedBy { it.key }) {
                    add(
                        buildJsonObject {
                            put("date", date.toString())
                            put("dayOfWeek", date.dayOfWeek.value)
                            putJsonArray("items") {
                                for (item in items) {
                                    when (item) {
                                        is CourseItem -> {
                                            val o = item.occ
                                            add(
                                                buildJsonObject {
                                                    put("kind", "course")
                                                    put("courseId", o.course.id)
                                                    put("title", o.course.name)
                                                    put("start", o.start.format(DT_FMT))
                                                    put("end", o.end.format(DT_FMT))
                                                    put("week", o.week)
                                                    put("periods", "${o.startPeriod}-${o.endPeriod}")
                                                    if (o.room.isNotBlank()) put("room", o.room)
                                                    if (o.course.teacher.isNotBlank()) put("teacher", o.course.teacher)
                                                    put("rescheduled", o.rescheduled)
                                                },
                                            )
                                        }

                                        is BlockItem -> {
                                            add(
                                                buildJsonObject {
                                                    put("kind", "block")
                                                    put("taskId", item.task.id)
                                                    put("title", item.task.title)
                                                    put("start", item.start.format(DT_FMT))
                                                    put("end", item.end.format(DT_FMT))
                                                    put("done", item.task.done)
                                                    put("priority", item.task.priority)
                                                },
                                            )
                                        }

                                        is TaskItem -> {
                                            add(
                                                buildJsonObject {
                                                    put("kind", "task")
                                                    put("taskId", item.task.id)
                                                    put("title", item.task.title)
                                                    put("due", if (item.hasTime) item.due.format(DT_FMT) else item.due.toLocalDate().toString())
                                                    put("hasTime", item.hasTime)
                                                    put("done", item.task.done)
                                                    put("priority", item.task.priority)
                                                },
                                            )
                                        }

                                        is EventItem -> {
                                            add(
                                                buildJsonObject {
                                                    put("kind", "event")
                                                    put("eventId", item.event.id)
                                                    put("title", item.event.title)
                                                    put("start", item.start.format(DT_FMT))
                                                    put("end", item.end.format(DT_FMT))
                                                    put("allDay", item.allDay)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        return buildJsonObject {
            put("from", from.toString())
            put("to", to.toString())
            put("days", days)
        }
    }

    private fun listCourses(): JsonElement {
        val snap = repo.snapshot.value.live()
        return buildJsonObject {
            putJsonArray("semesters") {
                for (s in snap.semesters) {
                    add(
                        buildJsonObject {
                            put("id", s.id)
                            put("name", s.name)
                            put("week1Monday", s.week1Monday.toString())
                            put("totalWeeks", s.totalWeeks)
                            put("isCurrent", s.isCurrent)
                            putJsonArray("periods") {
                                for (p in s.periods) {
                                    add(
                                        buildJsonObject {
                                            put("index", p.index)
                                            put("start", p.start.toString())
                                            put("end", p.end.toString())
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            putJsonArray("courses") {
                for (c in snap.courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name }))) {
                    add(
                        buildJsonObject {
                            put("id", c.id)
                            put("semesterId", c.semesterId)
                            put("name", c.name)
                            put("teacher", c.teacher)
                            put("room", c.room)
                            put("dayOfWeek", c.dayOfWeek)
                            put("startPeriod", c.startPeriod)
                            put("endPeriod", c.endPeriod)
                            putJsonArray("weeks") { c.weeks.sorted().forEach { w -> add(JsonPrimitive(w)) } }
                            put("colorIndex", c.colorIndex)
                            c.effectiveRemindMinutes.firstOrNull()?.let { put("remindMinutes", it) }
                            if (c.effectiveRemindMinutes.isNotEmpty()) {
                                putJsonArray("remindMinutesList") { c.effectiveRemindMinutes.forEach { m -> add(JsonPrimitive(m)) } }
                            }
                            if (c.note.isNotBlank()) put("note", c.note)
                        },
                    )
                }
            }
            putJsonArray("exceptions") {
                for (ex in snap.exceptions) {
                    add(
                        buildJsonObject {
                            put("id", ex.id)
                            put("courseId", ex.courseId)
                            put("week", ex.week)
                            put("type", ex.type.name)
                            ex.newWeek?.let { put("newWeek", it) }
                            ex.newDayOfWeek?.let { put("newDayOfWeek", it) }
                            ex.newStartPeriod?.let { put("newStartPeriod", it) }
                            ex.newEndPeriod?.let { put("newEndPeriod", it) }
                            ex.newRoom?.let { put("newRoom", it) }
                        },
                    )
                }
            }
        }
    }

    private fun getScriptSdk(): JsonElement =
        buildJsonObject {
            put("workflow", "推荐流程：1) 调用 preview_script 先在沙箱运行并检查返回的 changes 与 warnings（冲突提醒）；2) 确认无误后把 previewId 传给 apply_script 落库；若需撤回可调 undo_change。")
            put("declarations", ScriptSdk.declarations)
            putJsonArray("examples") {
                add(
                    buildJsonObject {
                        put("title", "批量替换教室名称")
                        put(
                            "code",
                            "mc.courses.query({ room: '教3' }).forEach(c => mc.courses.update(c.id, { room: c.room.replace('教3', '教三') }));",
                        )
                    },
                )
                add(
                    buildJsonObject {
                        put("title", "某周某天全部停课（如节假日放假）")
                        put(
                            "code",
                            "mc.courses.query({ dayOfWeek: 1, weeks: 8 }).forEach(c => mc.courses.cancel(c, 8));",
                        )
                    },
                )
                add(
                    buildJsonObject {
                        put("title", "单次调课到新周次/节次/教室")
                        put(
                            "code",
                            "const c = mc.courses.query({ name: '高等数学', dayOfWeek: 2 })[0]; if (c) mc.courses.reschedule(c, 6, { week: 7, dayOfWeek: 5, startPeriod: 3, endPeriod: 4, room: '教2-301' });",
                        )
                    },
                )
                add(
                    buildJsonObject {
                        put("title", "给所有实验课统一设置提前 30 分钟提醒")
                        put(
                            "code",
                            "mc.courses.query({ name: '实验' }).forEach(c => mc.courses.update(c.id, { remindMinutes: 30 }));",
                        )
                    },
                )
            }
        }

    private suspend fun previewScript(args: JsonObject): JsonElement {
        val code = args.str("code")?.takeIf { it.isNotBlank() } ?: throw McpToolError("脚本代码 code 不能为空")
        val desc =
            args
                .str("description")
                ?.trim()
                .orEmpty()
                .ifEmpty { "MCP 高级修改" }
        val showOnTimetable = args.bool("showOnTimetable") ?: true
        val snap = repo.snapshot.value
        val outcome =
            runner.dryRun(
                code = code,
                before = snap,
                options = ScriptOptions(source = Source.CODE, nowMillis = clock()),
                description = desc,
            )
        return when (outcome) {
            is ScriptOutcome.Failure -> {
                val loc = outcome.line?.let { "（第 $it 行）" }.orEmpty()
                val logSuffix = if (outcome.logs.isNotEmpty()) "\nLogs:\n" + outcome.logs.joinToString("\n") else ""
                throw McpToolError("脚本执行失败$loc：${outcome.message}$logSuffix")
            }

            is ScriptOutcome.Success -> {
                val cs = outcome.changeSet
                previews[cs.id] = cs
                if (showOnTimetable) {
                    advanced?.stagePreview(cs, showOnTimetable = true)
                }
                formatChangeSetResult(cs, committed = false, stagedOnTimetable = showOnTimetable && !cs.isEmpty)
            }
        }
    }

    private suspend fun applyScript(args: JsonObject): JsonElement {
        val previewId = args.str("previewId")?.trim()?.takeIf { it.isNotEmpty() }
        val descOverride = args.str("description")?.trim()?.takeIf { it.isNotEmpty() }
        val cs: ChangeSet =
            if (previewId != null) {
                previews.remove(previewId)
                    ?: throw McpToolError("未找到或已过期的 previewId：$previewId，请重新调用 preview_script 或直接传入 code")
            } else {
                val code = args.str("code")?.takeIf { it.isNotBlank() } ?: throw McpToolError("请提供 previewId 或 code")
                val outcome =
                    runner.dryRun(
                        code = code,
                        before = repo.snapshot.value,
                        options = ScriptOptions(source = Source.CODE, nowMillis = clock()),
                        description = descOverride ?: "MCP 高级修改",
                    )
                when (outcome) {
                    is ScriptOutcome.Failure -> {
                        val loc = outcome.line?.let { "（第 $it 行）" }.orEmpty()
                        throw McpToolError("脚本执行失败$loc：${outcome.message}")
                    }

                    is ScriptOutcome.Success -> {
                        outcome.changeSet
                    }
                }
            }

        val finalCs =
            cs.copy(
                id = if (previewId != null) cs.id else Ids.next(),
                source = Source.CODE,
                description = descOverride ?: cs.description.ifBlank { "MCP 高级修改" },
            )
        if (!finalCs.isEmpty) {
            repo.commit(finalCs)
        }
        advanced?.clearIfMatching(cs.id)
        return formatChangeSetResult(finalCs, committed = true, stagedOnTimetable = false)
    }

    private suspend fun undoChange(args: JsonObject): JsonElement {
        repo.ensureHistoryLoaded()
        val requestedId = args.str("changeSetId")?.trim()?.takeIf { it.isNotEmpty() }
        val ops = repo.history.value
        val target =
            if (requestedId != null) {
                ops.firstOrNull { it.id == requestedId } ?: throw McpToolError("未找到操作记录：$requestedId")
            } else {
                ops.firstOrNull { repo.undoable(it) } ?: throw McpToolError("当前没有可撤销的操作")
            }
        val ok = repo.undo(target.id)
        if (!ok) throw McpToolError("无法撤销操作 ${target.id}（后续修改可能已覆盖同一记录）")
        return buildJsonObject {
            put("undone", true)
            put("changeSetId", target.id)
            put("description", target.description)
        }
    }

    private fun formatChangeSetResult(
        cs: ChangeSet,
        committed: Boolean,
        stagedOnTimetable: Boolean,
    ): JsonObject {
        val views = ChangeViews.of(cs)
        return buildJsonObject {
            if (committed) {
                put("committed", true)
                put("changeSetId", cs.id)
            } else {
                put("committed", false)
                put("previewId", cs.id)
                put("stagedOnTimetable", stagedOnTimetable)
            }
            put("description", cs.description)
            put("changeCount", cs.changes.size)
            put("addCount", cs.addCount)
            put("modifyCount", cs.modifyCount)
            put("removeCount", cs.removeCount)
            putJsonArray("changes") {
                views.forEach { v ->
                    add(
                        buildJsonObject {
                            put("op", v.op.name)
                            put("type", v.type.name)
                            put("id", v.recordId)
                            put("title", v.title)
                            putJsonArray("fields") {
                                v.summary.forEach { d ->
                                    add(
                                        buildJsonObject {
                                            put("field", d.key.name)
                                            d.before?.let { put("before", it) }
                                            d.after?.let { put("after", it) }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            putJsonArray("warnings") { cs.warnings.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("logs") { cs.logs.forEach { add(JsonPrimitive(it)) } }
            cs.returnValue?.let { put("result", it) }
        }
    }

    companion object {
        private val DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        private fun emptyObjectSchema(): JsonObject =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }

        private fun objectSchema(
            required: List<String>,
            vararg props: Pair<String, JsonObject>,
        ): JsonObject =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    for ((k, v) in props) put(k, v)
                }
                if (required.isNotEmpty()) {
                    putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                }
            }

        private fun prop(
            type: String,
            desc: String,
        ): JsonObject =
            buildJsonObject {
                put("type", type)
                put("description", desc)
            }

        private fun arrayProp(
            itemType: String,
            desc: String,
        ): JsonObject =
            buildJsonObject {
                put("type", "array")
                put("description", desc)
                putJsonObject("items") { put("type", itemType) }
            }

        private fun repeatSchema(desc: String): JsonObject =
            buildJsonObject {
                put("type", "object")
                put("description", desc)
                putJsonObject("properties") {
                    put("freq", prop("string", "DAILY / WEEKLY / MONTHLY / YEARLY / NONE"))
                    put("interval", prop("integer", "间隔数，默认 1"))
                    put("byDays", arrayProp("integer", "WEEKLY：1=周一 … 7=周日；MONTHLY / YEARLY：1…31 日"))
                    put("byMonths", arrayProp("integer", "仅 YEARLY：1…12 月"))
                    put("until", prop("string", "重复截止日期 YYYY-MM-DD"))
                    put("count", prop("integer", "重复次数"))
                }
            }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

        private fun JsonObject.strList(key: String): List<String> =
            (this[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty()

        private fun JsonObject.intList(key: String): List<Int> =
            (this[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { m -> m >= 0 } }
                ?.distinct()
                ?.sorted()
                .orEmpty()

        private fun parseDateStrict(raw: String): LocalDate =
            runCatching { LocalDate.parse(raw.trim()) }
                .getOrElse { throw McpToolError("日期格式应为 YYYY-MM-DD，收到：$raw") }

        private fun parseDateTimeStrict(raw: String): LocalDateTime {
            val norm = raw.trim().replace(' ', 'T')
            return runCatching { LocalDateTime.parse(norm) }
                .getOrElse { throw McpToolError("时刻格式应为 YYYY-MM-DD HH:MM，收到：$raw") }
        }

        private fun parseDateOrDateTime(raw: String): Pair<LocalDateTime, Boolean> {
            val s = raw.trim().replace(' ', 'T')
            runCatching { LocalDate.parse(s) }.getOrNull()?.let { d ->
                return d.atTime(23, 59) to false
            }
            runCatching { LocalDateTime.parse(s) }.getOrNull()?.let { dt ->
                return dt to true
            }
            throw McpToolError("时间格式应为 YYYY-MM-DD 或 YYYY-MM-DD HH:MM，收到：$raw")
        }

        private fun parseRepeat(obj: JsonObject): Recurrence? {
            val freqRaw = obj.str("freq")?.trim()?.uppercase() ?: return null
            if (freqRaw.isEmpty() || freqRaw == "NONE") return null
            val freq =
                runCatching { Freq.valueOf(freqRaw) }
                    .getOrElse { throw McpToolError("不支持的重复频率：$freqRaw") }
            val interval = (obj.int("interval") ?: 1).coerceIn(1, 365)
            val rawDays =
                (obj["byDays"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                    ?.toSet()
                    .orEmpty()
            // 同一个字段周内指星期几、月/年内指第几天，按频率各自校验
            val byDays =
                when (freq) {
                    Freq.WEEKLY -> rawDays.filter { it in 1..7 }.toSet()
                    Freq.MONTHLY, Freq.YEARLY -> rawDays.filter { it in 1..31 }.toSet()
                    Freq.DAILY -> emptySet()
                }
            val byMonths =
                if (freq == Freq.YEARLY) {
                    (obj["byMonths"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { m -> m in 1..12 } }
                        ?.toSet()
                        .orEmpty()
                } else {
                    emptySet()
                }
            val until = obj.str("until")?.takeIf { it.isNotBlank() }?.let(::parseDateStrict)
            val count = obj.int("count")?.takeIf { it > 0 }
            return Recurrence(freq = freq, interval = interval, byDays = byDays, until = until, count = count, byMonths = byMonths)
        }
    }
}
