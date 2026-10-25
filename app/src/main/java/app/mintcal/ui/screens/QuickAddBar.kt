package app.mintcal.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.todo.Bucket
import app.mintcal.core.todo.QuickAdd
import app.mintcal.core.todo.QuickBatchItem
import app.mintcal.core.todo.QuickToken
import app.mintcal.core.todo.QuickTokenKind
import app.mintcal.core.todo.TaskLogic
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.Tag
import app.mintcal.ui.dueLabel
import app.mintcal.ui.theme.MintTheme
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 常驻的一行添加（如「明天下午3点交作业 #概率论 !高 @期末 /高数」）：输入即解析并在输入框内高亮识别到的片段，
 * 下方标签点一下就去掉对应片段；输入框上方有日期 / 课程 / 清单 / 优先级 / 粘贴 快捷条，不会打字也能录。
 * 多行文字（从别处复制 / 分享来的清单）会先给预览，再一次性全部添加，缩进行成为上一条的子任务。
 * 「更多」把已解析的内容带进详情页，不用重输。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddBar(
    snap: Snapshot,
    now: LocalDateTime,
    listFilter: String?,
    defaultBucket: Bucket?,
    onClearBucket: () -> Unit,
    focusSignal: Int,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val haptics = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf<List<QuickBatchItem>?>(null) }
    val parsed = remember(text, now) { if (text.isBlank()) null else QuickAdd.parse(text, now) }
    val focus = remember { FocusRequester() }
    // 键盘弹起时贴着键盘，收起时悬在底栏之上
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val scheme = MaterialTheme.colorScheme
    val highlight =
        remember(parsed, scheme) {
            TokenHighlight(parsed?.tokens.orEmpty()) { kind ->
                when (kind) {
                    QuickTokenKind.DATE, QuickTokenKind.TIME, QuickTokenKind.COURSE -> scheme.primary
                    QuickTokenKind.PRIORITY -> scheme.error
                    QuickTokenKind.LIST -> scheme.tertiary
                    QuickTokenKind.TAG -> scheme.secondary
                }
            }
        }

    LaunchedEffect(focusSignal) { if (focusSignal > 0) focus.requestFocus() }

    /** 去掉某类片段后（可选）追加一个新片段，用于快捷条与标签点按。 */
    fun rewrite(
        kinds: Set<QuickTokenKind>,
        add: String?,
    ) {
        var t = text
        parsed?.tokens?.filter { it.kind in kinds }?.forEach { t = t.replace(it.text, " ") }
        t = t.replace(Regex("\\s+"), " ").trim()
        text =
            if (add == null) {
                t
            } else if (t.isEmpty()) {
                "$add "
            } else {
                "$t $add"
            }
        focus.requestFocus()
    }

    fun commitItems(items: List<QuickBatchItem>) {
        if (items.isEmpty()) return
        val first = items.first().result
        val desc = if (items.size == 1) ctx.getString(R.string.add_task_desc, first.title) else ctx.getString(R.string.todo_batch_desc, items.size)
        val where = first.due?.takeIf { items.size == 1 }?.let { " · " + ctx.dueLabel(it, first.dueHasTime, now.toLocalDate()) }.orEmpty()
        val msg = if (items.size == 1) ctx.getString(R.string.task_added_toast, first.title) + where else ctx.getString(R.string.todo_batch_toast, items.size)
        actions.commit(buildQuickTasks(snap, items, listFilter, defaultBucket, now, desc), msg)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun save() {
        val p = parsed ?: return
        commitItems(listOf(QuickBatchItem(p, null)))
        text = ""
        focus.requestFocus()
    }

    /** 外部来的文字（粘贴 / 分享）：多行进批量预览，单行放进输入框。 */
    fun take(raw: String) {
        val items = QuickAdd.parseLines(raw, now)
        when {
            items.size > 1 -> batch = items
            items.size == 1 -> {
                text = raw.trim().replace(Regex("\\s+"), " ")
                focus.requestFocus()
            }
        }
    }

    val pending = nav.pendingAddText
    LaunchedEffect(pending) {
        if (pending != null) {
            nav.pendingAddText = null
            take(pending)
        }
    }

    val speech =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val spoken =
                r.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
            if (r.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
                text = (text + " " + spoken).trim()
                focus.requestFocus()
            }
        }

    fun startVoice() {
        val i =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.todo_voice_prompt))
        try {
            speech.launch(i)
        } catch (_: ActivityNotFoundException) {
            actions.toast(ctx.getString(R.string.todo_voice_unavailable))
        }
    }

    fun paste() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw =
            cm
                ?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(ctx)
                ?.toString()
        if (raw.isNullOrBlank()) actions.toast(ctx.getString(R.string.todo_clipboard_empty)) else take(raw)
    }

    fun openDetail() {
        val p = parsed
        if (p == null) {
            nav.push(Route.TaskDetail(id = null))
            return
        }
        val course = matchCourse(snap, p.courseName)
        nav.push(
            Route.TaskDetail(
                id = null,
                title = p.title,
                due = p.due?.toString(),
                dueHasTime = p.dueHasTime,
                priority = p.priority,
                listId = p.listName?.let { name -> snap.lists.firstOrNull { it.name == name }?.id },
                tags = p.tags.joinToString(" "),
                startDate = if (defaultBucket == Bucket.TODAY && p.due == null) now.toLocalDate().toString() else null,
                courseId = course?.id,
                beforeClass = course != null,
            ),
        )
    }

    AnimatedVisibility(
        visible = !hidden,
        modifier = modifier,
        enter = fadeIn(MintTheme.spec()),
        exit = fadeOut(MintTheme.spec()),
    ) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = if (ime > 0.dp) 8.dp else 96.dp),
        shape = RoundedCornerShape(26.dp),
        color = MintTheme.card,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            AnimatedVisibility(
                visible = focused || text.isNotBlank(),
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                LazyRow(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { QuickChip(stringResource(R.string.today)) { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.COURSE), "今天") } }
                    item { QuickChip(stringResource(R.string.tomorrow)) { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.COURSE), "明天") } }
                    item { QuickChip(stringResource(R.string.date_weekend)) { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.COURSE), "本周末") } }
                    item { QuickChip(stringResource(R.string.date_next_monday)) { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.COURSE), "下周一") } }
                    if (snap.courses.isNotEmpty()) {
                        item {
                            Box {
                                QuickChip("🎓 " + stringResource(R.string.date_before_class)) { courseMenu = true }
                                DropdownMenu(courseMenu, { courseMenu = false }) {
                                    snap.courses.distinctBy { it.name }.forEach { c ->
                                        DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                            courseMenu = false
                                            rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.TIME, QuickTokenKind.COURSE), "/" + c.name.replace(" ", ""))
                                        })
                                    }
                                }
                            }
                        }
                    }
                    if (snap.lists.isNotEmpty()) {
                        item {
                            Box {
                                QuickChip("#" + stringResource(R.string.label_list)) { listMenu = true }
                                DropdownMenu(listMenu, { listMenu = false }) {
                                    snap.lists.sortedBy { it.sort }.forEach { l ->
                                        DropdownMenuItem(text = { Text(l.name) }, onClick = {
                                            listMenu = false
                                            rewrite(setOf(QuickTokenKind.LIST), "#" + l.name)
                                        })
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Box {
                            QuickChip("!" + stringResource(R.string.label_priority)) { priorityMenu = true }
                            DropdownMenu(priorityMenu, { priorityMenu = false }) {
                                listOf("高" to R.string.priority_3, "中" to R.string.priority_2, "低" to R.string.priority_1).forEach { (word, label) ->
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                        priorityMenu = false
                                        rewrite(setOf(QuickTokenKind.PRIORITY), "!$word")
                                    })
                                }
                            }
                        }
                    }
                    item { QuickChip("📋 " + stringResource(R.string.todo_paste)) { paste() } }
                    item { QuickChip(stringResource(R.string.more_options)) { openDetail() } }
                }
            }
            if (defaultBucket != null) {
                Row(Modifier.padding(bottom = 4.dp)) {
                    Tag(
                        stringResource(R.string.todo_add_to, stringResource(bucketRes(defaultBucket))) + " ×",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        Modifier.clickable { onClearBucket() },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.quick_add_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    visualTransformation = highlight,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                if (text.isBlank()) {
                    IconButton(onClick = { startVoice() }) { Icon(Icons.Rounded.Mic, stringResource(R.string.todo_voice)) }
                } else {
                    IconButton(onClick = { save() }, enabled = parsed != null) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.add))
                    }
                }
            }
            AnimatedVisibility(
                visible = parsed?.tokens?.isNotEmpty() == true,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                val p = parsed
                if (p != null) {
                    FlowRow(
                        Modifier.padding(end = 6.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        p.due?.let {
                            Tag(
                                ctx.dueLabel(it, p.dueHasTime, now.toLocalDate()) + " ×",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                Modifier.clickable { rewrite(setOf(QuickTokenKind.DATE, QuickTokenKind.TIME), null) },
                            )
                        }
                        p.courseName?.let { name ->
                            val c = matchCourse(snap, name)
                            Tag(
                                "🎓 " + ctx.getString(R.string.before_next_class_short, c?.name ?: name) + " ×",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                Modifier.clickable { rewrite(setOf(QuickTokenKind.COURSE), null) },
                            )
                        }
                        if (p.priority > 0) {
                            val label =
                                when (p.priority) {
                                    3 -> R.string.priority_3
                                    2 -> R.string.priority_2
                                    else -> R.string.priority_1
                                }
                            Tag(
                                "!" + stringResource(label) + " ×",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                Modifier.clickable { rewrite(setOf(QuickTokenKind.PRIORITY), null) },
                            )
                        }
                        p.listName?.let {
                            Tag(
                                "#$it ×",
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                                Modifier.clickable { rewrite(setOf(QuickTokenKind.LIST), null) },
                            )
                        }
                        val tagTokens = p.tokens.filter { it.kind == QuickTokenKind.TAG }
                        p.tags.forEachIndexed { i, tag ->
                            Tag(
                                "@$tag ×",
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                Modifier.clickable {
                                    tagTokens.getOrNull(i)?.let { tok ->
                                        text = text.replace(tok.text, " ").replace(Regex("\\s+"), " ").trim()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    batch?.let { items ->
        BatchPreviewDialog(items, snap, now, onDismiss = { batch = null }) {
            commitItems(items)
            batch = null
        }
    }
}

/** 多行文字的批量添加预览：缩进显示父子关系，确认后一次性添加（一次撤销）。 */
@Composable
private fun BatchPreviewDialog(
    items: List<QuickBatchItem>,
    snap: Snapshot,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_batch_title, items.size)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(items.size) { i ->
                    val item = items[i]
                    val p = item.result
                    val meta =
                        buildList {
                            p.due?.let { d -> add(ctx.dueLabel(d, p.dueHasTime, now.toLocalDate())) }
                            p.courseName?.let { c -> add("🎓 " + (matchCourse(snap, c)?.name ?: c)) }
                            p.listName?.let { l -> add("#$l") }
                            p.tags.forEach { t -> add("@$t") }
                            if (p.priority > 0) add("!" + ctx.getString(listOf(R.string.priority_1, R.string.priority_2, R.string.priority_3)[p.priority - 1]))
                        }
                    Column(Modifier.padding(start = if (item.parentIndex != null) 20.dp else 0.dp, top = 4.dp, bottom = 4.dp)) {
                        Text((if (item.parentIndex != null) "↳ " else "") + p.title, style = MaterialTheme.typography.bodyLarge)
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.todo_batch_add_all)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QuickChip(
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(onClick = onClick, label = { Text(label, maxLines = 1) }, shape = CircleShape)
}

/** 把解析出的日期 / 课程 / 清单 / 优先级 / 标签片段在输入框里着色（不改文字与光标位置）。 */
private class TokenHighlight(
    private val tokens: List<QuickToken>,
    private val colorOf: (QuickTokenKind) -> Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text.text)
        for (t in tokens) {
            val i = text.text.indexOf(t.text)
            if (i < 0 || t.text.isEmpty()) continue
            val c = colorOf(t.kind)
            b.addStyle(SpanStyle(color = c, fontWeight = FontWeight.Medium, background = c.copy(alpha = 0.12f)), i, i + t.text.length)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** 「/课名」匹配已有课程：先全名，再包含。 */
internal fun matchCourse(
    snap: Snapshot,
    name: String?,
) = name?.takeIf { it.isNotBlank() }?.let { n ->
    snap.courses.firstOrNull { it.name.replace(" ", "") == n } ?: snap.courses.firstOrNull { it.name.contains(n, ignoreCase = true) }
}

/**
 * 一批解析结果 → 一个变更集。新清单只建一次；缩进的行挂到父任务（继承其清单）；
 * 没写日期的顶层任务按 [defaultBucket] 落点：今天 → startDate=今天（不会次日变红），其它分区 → 当天截止；收集箱不设日期。
 */
fun buildQuickTasks(
    snap: Snapshot,
    items: List<QuickBatchItem>,
    defaultListId: String?,
    defaultBucket: Bucket?,
    now: LocalDateTime,
    desc: String,
): ChangeSet {
    val changes = mutableListOf<Change>()
    val newLists = mutableMapOf<String, TaskList>()
    var order = snap.tasks.maxOfOrNull { it.sortOrder } ?: 0
    val ids = mutableListOf<String>()
    val listIds = mutableListOf<String?>()
    val today = now.toLocalDate()
    items.forEachIndexed { i, item ->
        val p = item.result
        val parentIdx = item.parentIndex?.takeIf { it in 0 until i }
        var listId = if (parentIdx != null) listIds[parentIdx] else defaultListId
        if (p.listName != null) {
            val existing = snap.lists.firstOrNull { it.name == p.listName } ?: newLists[p.listName]
            if (existing != null) {
                listId = existing.id
            } else {
                val l = TaskList(Ids.next(), p.listName.orEmpty(), colorIndex = (snap.lists.size + newLists.size) % 8, sort = snap.lists.size + newLists.size)
                newLists[l.name] = l
                changes += Change.Add(l)
                listId = l.id
            }
        }
        val course = matchCourse(snap, p.courseName)
        var due = p.due
        var dueHasTime = p.dueHasTime
        var start: java.time.LocalDate? = null
        if (course == null && due == null && parentIdx == null && defaultBucket != null) {
            val d = TaskLogic.dropDate(defaultBucket, today)
            when {
                defaultBucket == Bucket.TODAY -> start = today
                d != null && d != TaskLogic.NO_DROP -> {
                    due = LocalDateTime.of(d, LocalTime.of(23, 59))
                    dueHasTime = false
                }
            }
        }
        order += 1
        val id = Ids.next()
        ids += id
        listIds += listId
        changes +=
            Change.Add(
                Task(
                    id,
                    p.title,
                    listId = listId,
                    tags = p.tags,
                    priority = p.priority,
                    due = if (course != null) null else due,
                    dueHasTime = if (course != null) false else dueHasTime,
                    startDate = start,
                    courseId = course?.id,
                    dueBeforeNextClass = course != null,
                    parentId = parentIdx?.let { ids[it] },
                    sortOrder = order,
                    source = Source.MANUAL,
                ),
            )
    }
    return ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes)
}
