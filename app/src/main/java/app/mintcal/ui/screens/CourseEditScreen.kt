package app.mintcal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Conflicts
import app.mintcal.core.change.Edits
import app.mintcal.core.change.apply
import app.mintcal.core.model.Course
import app.mintcal.core.model.Semester
import app.mintcal.core.model.Source
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.core.time.WeekSets
import app.mintcal.core.util.Ids
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.ConflictCard
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MinutesDialSheet
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.hm
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme
import app.mintcal.ui.weekdayFull
import app.mintcal.ui.weekdayShort
import java.time.LocalDate

/** 一种每周排法（对应一条 Course 记录）。base 为 null 表示本次编辑新建的时间。 */
internal data class PatternDraft(
    val base: Course?,
    val dow: Int,
    val sp: Int,
    val ep: Int,
    val weeks: Set<Int>,
    val remind: Int?,
)

/**
 * 编辑画布的代数：画 / 搬 / 移除都是「画笔周」与各时间「周集」的集合运算，纯函数（可单测）。
 * 周被拿空的草稿直接丢弃；带 base 的留到保存时变成 Remove。
 */
internal object PatternOps {
    private fun prune(ps: List<PatternDraft>) = ps.filter { it.base != null || it.weeks.isNotEmpty() }

    /** 把画笔的周画到 (dow, sp..ep)：同天重叠的时间让出这些周，正好同位置的直接并入。 */
    fun draw(
        patterns: List<PatternDraft>,
        dow: Int,
        sp: Int,
        ep: Int,
        brush: Set<Int>,
    ): List<PatternDraft> {
        val shrunk = patterns.map { p -> if (p.dow == dow && p.sp <= ep && p.ep >= sp) p.copy(weeks = p.weeks - brush) else p }
        val exact = shrunk.indexOfFirst { it.dow == dow && it.sp == sp && it.ep == ep }
        val next =
            if (exact >= 0) {
                shrunk.mapIndexed { i, p -> if (i == exact) p.copy(weeks = p.weeks + brush) else p }
            } else {
                shrunk + PatternDraft(null, dow, sp, ep, brush, patterns.firstOrNull()?.remind)
            }
        return prune(next)
    }

    /** 搬动 [pi] 的画笔周：整段时间都搬就沿用原记录，否则拆出一个新时间。 */
    fun move(
        patterns: List<PatternDraft>,
        pi: Int,
        dow: Int,
        sp: Int,
        ep: Int,
        brush: Set<Int>,
    ): List<PatternDraft> {
        val p = patterns.getOrNull(pi) ?: return patterns
        val mv = p.weeks intersect brush
        if (mv.isEmpty()) return patterns
        val remainder = p.weeks - mv
        val moved =
            if (remainder.isEmpty()) {
                val relocated = patterns.mapIndexed { i, q -> if (i == pi) q.copy(dow = dow, sp = sp, ep = ep) else q }
                // 目标位置正好有自己的另一条时间：并进去，不叠两块
                val exact = relocated.withIndex().firstOrNull { (j, q) -> j != pi && q.dow == dow && q.sp == sp && q.ep == ep }?.index
                if (exact != null) {
                    relocated
                        .mapIndexed { j, q -> if (j == exact) q.copy(weeks = q.weeks + mv) else q }
                        .filterIndexed { j, _ -> j != pi || patterns[pi].base != null }
                } else {
                    relocated
                }
            } else {
                val shrunk = patterns.mapIndexed { i, q -> if (i == pi) q.copy(weeks = remainder) else q }
                val exact = shrunk.indexOfFirst { it.dow == dow && it.sp == sp && it.ep == ep }
                if (exact >= 0) {
                    shrunk.mapIndexed { i, q -> if (i == exact) q.copy(weeks = q.weeks + mv) else q }
                } else {
                    shrunk + PatternDraft(null, dow, sp, ep, mv, p.remind)
                }
            }
        return prune(moved)
    }

    /** 从画笔周里移除这条时间。 */
    fun removeWeeks(
        patterns: List<PatternDraft>,
        pi: Int,
        brush: Set<Int>,
    ): List<PatternDraft> = prune(patterns.mapIndexed { i, q -> if (i == pi) q.copy(weeks = q.weeks - brush) else q })

    /** 删掉整条时间。 */
    fun remove(
        patterns: List<PatternDraft>,
        pi: Int,
    ): List<PatternDraft> = patterns.filterIndexed { i, _ -> i != pi }
}

/** 网格拖拽中的状态：mode=draw 画新块，mode=move 搬 [pi] 的选中周。 */
private data class GridDrag(
    val move: Boolean,
    val day1: Int,
    val day2: Int,
    val first: Int,
    val last: Int,
    val pi: Int? = null,
)

/** 网格几何与周历一致：34dp 时间列、60dp 节次行、上午/下午/晚上之间 14dp 留白。 */
private val GridTimeColW = 34.dp
private val GridRowH = 60.dp
private val GridGroupGap = 14.dp
private val GridHeaderH = 46.dp

private fun gridGroupBoundaries(periodCount: Int) = listOf(4, 8).filter { it < periodCount }

private fun gridPeriodTop(
    p: Int,
    periodCount: Int,
): Dp = GridRowH * (p - 1) + GridGroupGap * gridGroupBoundaries(periodCount).count { p > it }

/**
 * 课程编辑。画笔是「周次」：先选要编辑的周，下面就是和周历同一张课表——
 * 按住空白拖动 = 给选中周画这段时间；按住已有色块拖动 = 把它的选中周搬走；
 * 点色块 = 看周次、改提醒、从选中周移除或删掉整个时间。
 * 同一门课的多条排课记录（同名称）合在这里，返回即整体保存为一个变更集。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditScreen(
    id: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val nav = app.mintcal.ui.LocalNav.current
    val snap = liveSnapshot()
    val sem = snap.currentSemester
    if (sem == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val existing = remember(id) { id?.let { i -> snap.courses.firstOrNull { it.id == i } } }
    if (id != null && existing == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val cal = remember(sem) { SemesterCalendar(sem) }
    val periodCount = sem.periods.size
    val totalWeeks = sem.totalWeeks

    // ── 课程级字段：一门课只有一套，保存时统一落到全部时间上 ──
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(existing?.teacher.orEmpty()) }
    var room by remember { mutableStateOf(existing?.room.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorIndex ?: (snap.courses.size % 8)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    // ── 时间模式：进入页面时冻结；同名记录即同一门课（与「另一天」的历史约定一致） ──
    var patterns by remember(id) {
        mutableStateOf(
            if (existing != null) {
                snap.courses
                    .filter { it.deletedAt == null && it.semesterId == sem.id && it.name == existing.name }
                    .sortedWith(compareBy({ it.weeks.minOrNull() ?: 99 }, { it.dayOfWeek }, { it.startPeriod }))
                    .map { PatternDraft(it, it.dayOfWeek, it.startPeriod, it.endPeriod, it.weeks, it.remindMinutes) }
            } else {
                listOf(PatternDraft(null, 1, 1, 2.coerceAtMost(periodCount), WeekSets.all(totalWeeks), null))
            },
        )
    }
    // 周次挪走后，挂在上面的调课/停课记录就成了悬空引用，保存时一并删掉
    var dropExceptions by remember { mutableStateOf(setOf<Pair<String, Int>>()) }

    // 画笔：当前正在编辑哪些周。已有课默认全选自己的周，新课默认整学期。
    var brush by remember(id) {
        mutableStateOf(patterns.fold(emptySet<Int>()) { acc, p -> acc + p.weeks }.ifEmpty { WeekSets.all(totalWeeks) })
    }
    var openBlock by remember { mutableStateOf<Int?>(null) }
    var remindCustom by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(existing == null) { if (existing == null) nameFocus.requestFocus() }

    val origin = existing
    val baseIds = remember(patterns) { patterns.mapNotNull { it.base?.id }.toSet() }
    val liveEx = snap.exceptions.filter { it.deletedAt == null && it.courseId in baseIds }

    /** 周次归属改动：失掉的周把挂在上面的覆盖一并抹掉（否则周历里会残留幽灵停课）。 */
    fun commitPatterns(
        old: List<PatternDraft>,
        new: List<PatternDraft>,
    ) {
        patterns = new
        val dropped = mutableSetOf<Pair<String, Int>>()
        old.forEach { o ->
            val bid = o.base?.id ?: return@forEach
            val n = new.firstOrNull { it.base === o.base }
            val lost =
                when (n) {
                    null -> o.weeks
                    else -> o.weeks - n.weeks
                }
            lost.forEach { dropped += bid to it }
        }
        if (dropped.isNotEmpty()) dropExceptions = dropExceptions + dropped
    }

    /** 把画笔的周画到 (dow, sp..ep)：同天重叠的时间让出这些周，正好同位置的直接并入。 */
    fun drawSlot(
        dow: Int,
        sp: Int,
        ep: Int,
    ) {
        commitPatterns(patterns, PatternOps.draw(patterns, dow, sp, ep, brush))
    }

    /** 搬动 [pi]：只搬画笔选中的周；整段时间都搬就沿用原记录，否则拆出一个新时间。 */
    fun movePattern(
        pi: Int,
        dow: Int,
        sp: Int,
        ep: Int,
    ) {
        commitPatterns(patterns, PatternOps.move(patterns, pi, dow, sp, ep, brush))
    }

    /** 从画笔周里移除这条时间；周被拿空的时间保存时删除。 */
    fun removeFromBrush(pi: Int) {
        openBlock = null
        commitPatterns(patterns, PatternOps.removeWeeks(patterns, pi, brush))
    }

    fun deletePattern(pi: Int) {
        openBlock = null
        commitPatterns(patterns, PatternOps.remove(patterns, pi))
    }

    fun materialize(p: PatternDraft): Course? {
        if (p.weeks.isEmpty()) return null
        val b = p.base
        return if (b != null) {
            b.copy(
                name = name.trim(),
                teacher = teacher.trim(),
                room = room.trim(),
                dayOfWeek = p.dow,
                startPeriod = p.sp,
                endPeriod = p.ep,
                weeks = p.weeks,
                colorIndex = color,
                remindMinutes = p.remind,
                note = note.trim(),
            )
        } else {
            Course(
                id = Ids.next(),
                semesterId = sem.id,
                name = name.trim(),
                teacher = teacher.trim(),
                room = room.trim(),
                dayOfWeek = p.dow,
                startPeriod = p.sp,
                endPeriod = p.ep,
                weeks = p.weeks,
                colorIndex = color,
                remindMinutes = p.remind,
                note = note.trim(),
                source = Source.MANUAL,
            )
        }
    }

    /** 内容是否一致（忽略元数据），决定是否真的需要一条 Modify。 */
    fun contentMatches(
        base: Course,
        c: Course,
    ): Boolean = c.copy(updatedAt = base.updatedAt, deletedAt = base.deletedAt, userId = base.userId) == base

    val valid = name.isNotBlank() && patterns.any { it.weeks.isNotEmpty() } && patterns.all { it.ep >= it.sp }
    val fieldDirty =
        origin == null ||
            name.trim() != origin.name ||
            teacher.trim() != origin.teacher ||
            room.trim() != origin.room ||
            color != origin.colorIndex ||
            note.trim() != origin.note
    val dirty =
        fieldDirty ||
            dropExceptions.isNotEmpty() ||
            patterns.any { p ->
                val b = p.base
                b == null ||
                    p.weeks.isEmpty() ||
                    b.dayOfWeek != p.dow ||
                    b.startPeriod != p.sp ||
                    b.endPeriod != p.ep ||
                    b.weeks != p.weeks ||
                    b.remindMinutes != p.remind ||
                    !contentMatches(b, materialize(p)!!)
            }

    val preview =
        remember(dirty, valid, name, teacher, room, color, note, patterns) {
            if (!valid || !dirty) {
                null
            } else {
                val list = patterns.mapNotNull { materialize(it) }
                if (list.isEmpty()) {
                    null
                } else {
                    val synthetic = snap.apply(Edits.saveAll(snap, list, "", 0), 0)
                    list
                        .flatMap { c -> Conflicts.detect(synthetic, setOf(c.id)) }
                        .distinctBy { listOf(it.aTitle, it.bTitle, it.start) }
                }
            }
        }

    fun persist(leave: Boolean) {
        val ms = System.currentTimeMillis()
        val changes = mutableListOf<Change>()
        patterns.forEach { p ->
            val b = p.base
            val c = materialize(p)
            when {
                c == null && b != null -> {
                    // 周次清空 = 删掉这条时间（连带它的调课停课记录）
                    changes += Change.Remove(b)
                    snap.exceptions.filter { it.deletedAt == null && it.courseId == b.id }.forEach { changes += Change.Remove(it) }
                }

                c != null && b == null -> {
                    changes += Change.Add(c)
                }

                c != null && b != null && !contentMatches(b, c) -> {
                    changes += Change.Modify(b, c)
                }
            }
        }
        dropExceptions.forEach { (bid, w) ->
            liveEx.firstOrNull { it.courseId == bid && it.week == w }?.let { changes += Change.Remove(it) }
        }
        if (changes.isEmpty()) {
            if (leave) onBack()
            return
        }
        val desc = ctx.getString(if (origin == null) R.string.add_course_desc else R.string.edit_course_desc, name.trim())
        actions.commit(
            ChangeSet(Ids.next(), desc, Source.MANUAL, ms, changes),
            desc.takeIf { changes.any { c -> c is Change.Remove } },
        )
        if (leave) onBack()
    }

    fun requestBack() {
        when {
            name.isBlank() -> onBack()

            !valid -> actions.toast(ctx.getString(R.string.weeks_invalid))

            // 新建课程：返回不落库，画过的格子先确认放弃；修改照旧自动保存
            origin == null && dirty -> confirmDiscard = true

            dirty -> persist(leave = true)

            else -> onBack()
        }
    }
    BackHandler { requestBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                if (origin == null) {
                    // 新建：明确一个「确定」键；返回 = 放弃（画过的格子会先确认）
                    TextButton(enabled = name.isNotBlank() && valid, onClick = { persist(leave = true) }) {
                        Text(stringResource(R.string.confirm))
                    }
                } else {
                    Box {
                        IconButton(onClick = { pageMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(pageMenu, { pageMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = {
                                    pageMenu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val courseNameHint = stringResource(R.string.label_course_name)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .focusRequester(nameFocus)
                        .semantics { contentDescription = courseNameHint }
                        .padding(vertical = 12.dp),
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                courseNameHint,
                                modifier = Modifier.clearAndSetSemantics {},
                                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            FlatLine(room, stringResource(R.string.label_room)) { room = it }
            FlatLine(teacher, stringResource(R.string.label_teacher)) { teacher = it }

            FormSection(title = stringResource(R.string.course_weeks_pick)) {
                WeekPicker(
                    brush = brush,
                    totalWeeks = totalWeeks,
                    currentWeek = remember(cal) { cal.currentWeek(LocalDate.now()) },
                    onChange = { brush = it },
                )
            }

            FormSection(title = stringResource(R.string.course_semester_grid)) {
                EditorGrid(
                    sem = sem,
                    cal = cal,
                    patterns = patterns,
                    others = snap.courses.filter { it.deletedAt == null && it.semesterId == sem.id && (origin == null || it.name != origin.name) },
                    exceptions = liveEx,
                    brush = brush,
                    color = color,
                    today = LocalDate.now(),
                    onDraw = ::drawSlot,
                    onMove = ::movePattern,
                    onOpenBlock = { openBlock = it },
                )
            }

            AnimatedVisibility(
                visible = !preview.isNullOrEmpty(),
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                ConflictCard(title = stringResource(R.string.conflict_title), modifier = Modifier.padding(top = 4.dp)) {
                    preview.orEmpty().take(4).forEach {
                        Text(
                            stringResource(R.string.conflict_line, it.aTitle, it.bTitle, it.start.toLocalDate().toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlatLine(note, stringResource(R.string.label_note), singleLine = false) { note = it }
            Spacer(Modifier.height(12.dp))
            ColorPicker(color) { color = it }
            if (origin != null) {
                val related =
                    snap.tasks.filter { t ->
                        t.deletedAt == null && t.parentId == null && !t.done && t.courseId != null &&
                            (t.courseId == origin.id || snap.courses.firstOrNull { it.id == t.courseId }?.name == origin.name)
                    }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.course_todos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (related.isEmpty()) {
                    Text(
                        stringResource(R.string.course_todos_empty),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                related.forEach { t -> TodoLine(t, snap, java.time.LocalDateTime.now(), Modifier.padding(vertical = 3.dp)) }
                TextButton(onClick = { nav.push(Route.TodoEdit(courseId = origin.id)) }, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.course_todos_add))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    openBlock?.let { pi ->
        val p = patterns.getOrNull(pi)
        if (p == null) {
            openBlock = null
        } else {
            BlockSheet(
                sem = sem,
                pattern = p,
                brush = brush,
                onDismiss = { openBlock = null },
                onRemoveBrush = { removeFromBrush(pi) },
                onDelete = { deletePattern(pi) },
                onRemind = { r ->
                    val old = patterns
                    patterns = old.mapIndexed { i, q -> if (i == pi) q.copy(remind = r) else q }
                    commitPatterns(old, patterns)
                },
            )
            if (remindCustom) {
                MinutesDialSheet(p.remind ?: 10, maxMinutes = 24 * 60, onDismiss = { remindCustom = false }) { r ->
                    remindCustom = false
                    val old = patterns
                    patterns = old.mapIndexed { i, q -> if (i == pi) q.copy(remind = r) else q }
                    commitPatterns(old, patterns)
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            stringResource(R.string.detail_editor_discard_title),
            stringResource(R.string.detail_editor_discard_desc),
            stringResource(R.string.detail_editor_discard),
            onConfirm = { onBack() },
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
    if (confirmDelete && origin != null) {
        ConfirmDialog(
            stringResource(R.string.delete_course_title, origin.name),
            stringResource(R.string.delete_to_trash_hint),
            stringResource(R.string.delete),
            onConfirm = {
                val desc = ctx.getString(R.string.delete_desc, origin.name)
                val changes =
                    buildList {
                        patterns.forEach { p -> p.base?.let { add(Change.Remove(it)) } }
                        patterns.forEach { p ->
                            p.base?.let { b ->
                                snap.exceptions.filter { it.deletedAt == null && it.courseId == b.id }.forEach { add(Change.Remove(it)) }
                            }
                        }
                    }
                actions.commit(ChangeSet(Ids.next(), desc, Source.MANUAL, System.currentTimeMillis(), changes), desc)
                onBack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

/** 周次画笔：点选要编辑的周；预设一键全部/单周/双周/清空。 */
@Composable
private fun WeekPicker(
    brush: Set<Int>,
    totalWeeks: Int,
    currentWeek: Int,
    onChange: (Set<Int>) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            stringResource(R.string.weeks_all) to WeekSets.all(totalWeeks),
            stringResource(R.string.weeks_odd) to WeekSets.odd(totalWeeks),
            stringResource(R.string.weeks_even) to WeekSets.even(totalWeeks),
            stringResource(R.string.course_weeks_clear) to emptySet(),
        ).forEach { (label, weeks) ->
            val sel = brush == weeks
            Text(
                label,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .mintClickable(
                            role = Role.Button,
                            selected = sel,
                        ) { onChange(weeks) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (w in 1..totalWeeks) {
            val on = w in brush
            val today = w == currentWeek
            val weekDesc = "${stringResource(R.string.week_n, w)}${if (today) "，本周" else ""}"
            Box(
                modifier =
                    Modifier
                        .sizeIn(minWidth = 36.dp, minHeight = 48.dp)
                        .mintClickable(
                            role = Role.Checkbox,
                            selected = on,
                        ) { onChange(if (on) brush - w else brush + w) }
                        .semantics {
                            contentDescription = weekDesc
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp, 30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(if (today && !on) 1.5.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$w",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** 编辑画布：和周历同一张课表（同列宽/行高/组间留白/配色）。 */
@Composable
private fun EditorGrid(
    sem: Semester,
    cal: SemesterCalendar,
    patterns: List<PatternDraft>,
    others: List<Course>,
    exceptions: List<app.mintcal.core.model.CourseException>,
    brush: Set<Int>,
    color: Int,
    today: LocalDate,
    onDraw: (dow: Int, sp: Int, ep: Int) -> Unit,
    onMove: (pi: Int, dow: Int, sp: Int, ep: Int) -> Unit,
    onOpenBlock: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val colors = MintTheme.colors
    val periods = sem.periods.sortedBy { it.index }
    val n = periods.size
    val singleWeek = brush.singleOrNull()
    val outline = MaterialTheme.colorScheme.outlineVariant

    // 画笔周内的块：自己的时间（实心、可点）、别人的课（半透明背景层）
    val ownCells =
        remember(patterns, exceptions, brush) {
            patterns.mapIndexed { pi, p ->
                val active = p.weeks intersect brush
                val cancelledN = p.base?.id?.let { bid -> cancelledCount(exceptions, bid, active) } ?: 0
                EditorGridCell(
                    dow = p.dow,
                    sp = p.sp,
                    ep = p.ep,
                    pi = pi,
                    colorIndex = -1,
                    text = if (p.weeks == brush) "" else WeekSets.format(p.weeks) + "周",
                    dimmed = active.isEmpty(),
                    cancelled = active.isNotEmpty() && cancelledN >= active.size,
                )
            }
        }
    val otherCells =
        remember(others, brush) {
            others
                .filter { (it.weeks intersect brush).isNotEmpty() }
                .map { c -> EditorGridCell(c.dayOfWeek, c.startPeriod, c.endPeriod, null, c.colorIndex, c.name, dimmed = true, cancelled = false) }
        }
    // 同一天里互相压住的时间分道摆放，谁也不盖住谁（instance 比较：值相等的两条也是两条）
    val laneOf =
        remember(ownCells) {
            ownCells.map { b ->
                val cluster = ownCells.filter { q -> q.dow == b.dow && q.sp <= b.ep && q.ep >= b.sp }
                val idx = cluster.sortedBy { it.sp * 100 + it.ep }.indexOfFirst { it === b }
                (if (idx < 0) 0 else idx) to cluster.size
            }
        }

    val totalH = gridPeriodTop(n, n) + GridRowH
    Column(Modifier.fillMaxWidth()) {
        // 星期头：画笔只选一周时显示真实日期，多周时只留星期名
        Row(Modifier.fillMaxWidth().height(GridHeaderH).padding(start = GridTimeColW)) {
            for (d in 1..7) {
                val date = singleWeek?.let { cal.dateOf(it, d) }
                val isToday = date == today
                Column(
                    Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        ctx.weekdayShort(d),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (date != null) {
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // 拖拽容器：按住空白 = 画；按住自己的色块 = 搬它的选中周。
        // pointerInput 挂在容器上（同周历）：子级的点击走 child pass 先响应，
        // 长按由容器接管 —— 色块上按住拖动是搬动，空白上按住拖动是画。
        var drag by remember { mutableStateOf<GridDrag?>(null) }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(totalH)
                .pointerInput(patterns, brush, n) {
                    val timeColPx = GridTimeColW.toPx()
                    val colPx = (size.width - timeColPx) / 7f
                    val rowPx = GridRowH.toPx()
                    val gapPx = GridGroupGap.toPx()
                    val bounds = gridGroupBoundaries(n)

                    fun colOf(x: Float): Int = (((x - timeColPx) / colPx).toInt().coerceIn(0, 6)) + 1

                    fun slotOf(y: Float): Int {
                        for (p in 1..n) {
                            val top = rowPx * (p - 1) + gapPx * bounds.count { p > it }
                            if (y < top + rowPx) return p
                        }
                        return n
                    }

                    fun blockAt(
                        day: Int,
                        slot: Int,
                    ): Int? =
                        patterns
                            .indexOfFirst { p ->
                                p.dow == day && p.sp <= slot && p.ep >= slot && (p.weeks intersect brush).isNotEmpty()
                            }.takeIf { it >= 0 }

                    var anchorDay = 1
                    var anchorSlot = 1
                    detectDragGesturesAfterLongPress(
                        onDragStart = { off ->
                            if (off.x >= timeColPx) {
                                anchorDay = colOf(off.x)
                                anchorSlot = slotOf(off.y)
                                val pi = blockAt(anchorDay, anchorSlot)
                                drag =
                                    if (pi != null) {
                                        val p = patterns[pi]
                                        GridDrag(true, anchorDay, anchorDay, p.sp, p.ep, pi)
                                    } else {
                                        GridDrag(false, anchorDay, anchorDay, anchorSlot, anchorSlot)
                                    }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, _ ->
                            val cur = drag ?: return@detectDragGesturesAfterLongPress
                            val d = colOf(change.position.x)
                            val e = slotOf(change.position.y).coerceIn(1, n)
                            change.consume()
                            drag =
                                if (cur.move) {
                                    // 搬动：整段时间跟着手指平移（按住段的哪一节，就带着哪一节走）
                                    val p = patterns[cur.pi!!]
                                    val span = p.ep - p.sp
                                    val first = (e - (anchorSlot - p.sp)).coerceIn(1, n - span)
                                    GridDrag(true, d, d, first, first + span, cur.pi)
                                } else {
                                    GridDrag(false, cur.day1, d, minOf(anchorSlot, e), maxOf(anchorSlot, e))
                                }
                        },
                        onDragEnd = {
                            drag?.let { b ->
                                if (b.move) {
                                    b.pi?.let { pi -> onMove(pi, b.day2, b.first, b.last) }
                                } else {
                                    for (d in minOf(b.day1, b.day2)..maxOf(b.day1, b.day2)) onDraw(d, b.first, b.last)
                                }
                            }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            val colW = (maxWidth - GridTimeColW) / 7
            // 左侧节次
            periods.forEach { p ->
                Column(
                    Modifier
                        .absoluteOffset(0.dp, gridPeriodTop(p.index, n))
                        .width(GridTimeColW)
                        .height(GridRowH)
                        .padding(top = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${p.index}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        p.start.hm(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            gridGroupBoundaries(n).forEach { b ->
                Box(
                    Modifier
                        .absoluteOffset(GridTimeColW, gridPeriodTop(b, n) + GridRowH + GridGroupGap / 2)
                        .width(maxWidth - GridTimeColW)
                        .height(1.dp)
                        .background(outline),
                )
            }

            // 别人的课：半透明铺在底层
            otherCells.forEach { b ->
                CourseCell(
                    block = b,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .absoluteOffset(GridTimeColW + colW * (b.dow - 1), gridPeriodTop(b.sp, n) + 2.dp)
                            .width(cellWidth(colW))
                            .height(cellHeightBySpan(b.sp, b.ep, n))
                            .alpha(0.4f),
                )
            }
            // 自己的时间：实心，可点开；互相重叠的分道
            ownCells.forEachIndexed { i, b ->
                val pi = b.pi ?: return@forEachIndexed
                val (lane, lanes) = laneOf[i]
                val laneW = cellWidth(colW) / lanes
                CourseCell(
                    block = b,
                    colorIndex = color,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .absoluteOffset(GridTimeColW + colW * (b.dow - 1) + laneW * lane, gridPeriodTop(b.sp, n) + 2.dp)
                            .width(laneW)
                            .height(cellHeightBySpan(b.sp, b.ep, n))
                            .alpha(if (b.dimmed) 0.5f else 1f)
                            .mintClickable { onOpenBlock(pi) }
                            .semantics {
                                contentDescription =
                                    ctx.weekdayFull(b.dow) + " " + ctx.periodSpan(b.sp, b.ep) +
                                    (b.text.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
                            },
                )
            }

            // 拖拽幽灵框（画/搬的目标预览）
            drag?.let { b ->
                val d1 = minOf(b.day1, b.day2)
                val d2 = maxOf(b.day1, b.day2)
                Box(
                    Modifier
                        .absoluteOffset(GridTimeColW + colW * (d1 - 1), gridPeriodTop(b.first, n) + 2.dp)
                        .width(colW * (d2 - d1 + 1) - 4.dp)
                        .height(cellHeightBySpan(b.first, b.last, n))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

/** 画笔周内这条时间在选中周里全部停课时：空心 + 停 角标。 */
private fun cancelledCount(
    exceptions: List<app.mintcal.core.model.CourseException>,
    bid: String,
    weeks: Set<Int>,
): Int = exceptions.count { it.courseId == bid && it.type == app.mintcal.core.model.ExceptionType.CANCEL && it.week in weeks }

/** 网格里的一格：pi 非空 = 自己的时间（pi 是 patterns 下标），空 = 别人的课（背景层）。 */
private data class EditorGridCell(
    val dow: Int,
    val sp: Int,
    val ep: Int,
    val pi: Int?,
    val colorIndex: Int,
    val text: String,
    val dimmed: Boolean,
    val cancelled: Boolean,
)

private fun cellWidth(colW: Dp): Dp = colW - 4.dp

private fun cellHeightBySpan(
    sp: Int,
    ep: Int,
    n: Int,
): Dp {
    val bottom =
        if (ep >= n) gridPeriodTop(n, n) + GridRowH else gridPeriodTop(ep + 1, n)
    return bottom - gridPeriodTop(sp, n) - 4.dp
}

@Composable
private fun CourseCell(
    block: EditorGridCell,
    shape: RoundedCornerShape,
    modifier: Modifier,
    colorIndex: Int = block.colorIndex,
) {
    val colors = MintTheme.colors
    val fg = colors.fg(colorIndex)
    Box(
        modifier
            .clip(shape)
            .background(if (block.cancelled) MaterialTheme.colorScheme.surfaceVariant else colors.bg(colorIndex))
            .border(if (block.cancelled) 1.5.dp else 0.dp, if (block.cancelled) MaterialTheme.colorScheme.outlineVariant else Color.Transparent, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            if (block.text.isNotEmpty()) {
                Text(
                    block.text,
                    color = fg,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (block.cancelled) {
                Text(
                    stringResource(R.string.course_cancel_badge),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 点开自己的时间：周次、提醒、从选中周移除 / 删除整条。 */
@Composable
private fun BlockSheet(
    sem: Semester,
    pattern: PatternDraft,
    brush: Set<Int>,
    onDismiss: () -> Unit,
    onRemoveBrush: () -> Unit,
    onDelete: () -> Unit,
    onRemind: (Int?) -> Unit,
) {
    val ctx = LocalContext.current
    var alarm by remember(pattern.base?.id, pattern.dow, pattern.sp) { mutableStateOf(pattern.remind) }
    var remindDlg by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "${ctx.weekdayFull(pattern.dow)} ${ctx.periodSpan(pattern.sp, pattern.ep)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.label_weeks_value, WeekSets.format(pattern.weeks)) +
                    WeekSets
                        .format(pattern.weeks intersect brush)
                        .takeIf { (pattern.weeks intersect brush).isNotEmpty() }
                        ?.let { " · " + stringResource(R.string.course_brush_weeks, it) }
                        .orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hairline()
            ValueRow(
                label = stringResource(R.string.label_remind),
                value = remindLabel(alarm),
                onClick = { remindDlg = true },
                valueColor = if (alarm != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hairline()
            Row {
                TextButton(onClick = onRemoveBrush) {
                    Text(stringResource(R.string.course_block_remove_brush), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.course_pattern_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (remindDlg) {
        RemindPickDialog(
            initial = alarm,
            hasTime = true,
            initialPersistent = false,
            onDismiss = { remindDlg = false },
        ) { minutes, _ ->
            alarm = minutes
            onRemind(minutes)
            remindDlg = false
        }
    }
}

@Composable
private fun FlatLine(
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = placeholder }
                .padding(vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun ColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MintTheme.colors
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 8) {
            val sel = i == selected
            val name = stringResource(R.string.color_n, i + 1)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .mintClickable(
                        role = Role.RadioButton,
                        selected = sel,
                    ) { onSelect(i) }
                    .semantics {
                        contentDescription = name
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.bg(i))
                        .border(if (sel) 2.dp else 1.dp, if (sel) colors.fg(i) else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Rounded.Check, null, tint = colors.fg(i), modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** 「第3-4节」/「第3节」这种紧凑时段文案。 */
private fun android.content.Context.periodSpan(
    sp: Int,
    ep: Int,
): String =
    if (sp == ep) {
        getString(app.mintcal.R.string.period_n, sp)
    } else {
        getString(app.mintcal.R.string.periods_range, sp, ep)
    }
