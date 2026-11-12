package app.mintcal.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mintcal.R
import app.mintcal.script.ScriptOutcome
import app.mintcal.script.ScriptSdk
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.Tab
import app.mintcal.ui.components.ChangeList
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.components.SegmentedPill
import app.mintcal.ui.components.Tag
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.theme.MintTheme

private data class Example(
    val label: Int,
    val desc: Int,
    val code: String,
)

private val EXAMPLES =
    listOf(
        Example(
            R.string.example_room,
            R.string.example_room_desc,
            "// 把教室里的「教3」统一改成「教三」\nlet n = 0;\nmc.courses.query({ room: '教3' }).forEach(c => {\n  mc.courses.update(c.id, { room: c.room.replace('教3', '教三') });\n  n++;\n});\nconsole.log('共修改 ' + n + ' 门课的教室');",
        ),
        Example(
            R.string.example_cancel,
            R.string.example_cancel_desc,
            "// 第 8 周周一的课全部停课（如放假）\nconst week = 8;\nlet n = 0;\nmc.courses.query({ dayOfWeek: 1, weeks: week }).forEach(c => { mc.courses.cancel(c, week); n++; });\nconsole.log('第 ' + week + ' 周周一共停课 ' + n + ' 门');",
        ),
        Example(
            R.string.example_trim,
            R.string.example_trim_desc,
            "// 删除第 16 周之后才有的周次（学期缩短）\nlet n = 0;\nmc.courses.query().forEach(c => {\n  const kept = c.weeks.filter(w => w <= 16);\n  if (kept.length === 0) { mc.courses.remove(c); n++; }\n  else if (kept.length !== c.weeks.length) { mc.courses.update(c.id, { weeks: kept }); n++; }\n});\nconsole.log('调整了 ' + n + ' 门课');",
        ),
        Example(
            R.string.example_remind,
            R.string.example_remind_desc,
            "// 给所有名字含「实验」的课加上课前 30 分钟提醒\nconst list = mc.courses.query({ name: '实验' });\nlist.forEach(c => mc.courses.update(c.id, { remindMinutes: 30 }));\nconsole.log('共 ' + list.length + ' 门实验课已设置提醒');",
        ),
    )

/** 默认入口：底部抽屉，人话变更列表为主，代码折叠。 */
@Composable
fun AdvancedSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val snap = liveSnapshot()
    val st by app.advanced.state.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Code, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.advanced_title),
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Tag(
                    stringResource(R.string.preview_then_apply),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            // 横向胶囊贴边出血，避免右侧胶囊在容器内硬切
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.width(12.dp))
                EXAMPLES.forEach { ex ->
                    val labelText = stringResource(ex.label)
                    Text(
                        labelText,
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .mintClickable(pressedScale = 0.96f) {
                                app.advanced.setCode(ex.code)
                                app.advanced.dryRun()
                            }.semantics {
                                role = Role.Button
                                contentDescription = labelText
                            }.wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = st.running,
                    enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
                AdvancedMessage(st.message)

                when (val o = st.outcome) {
                    is ScriptOutcome.Failure -> {
                        FailureCard(o)
                    }

                    is ScriptOutcome.Success -> {
                        val set = o.changeSet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (set.isEmpty) stringResource(R.string.no_changes) else stringResource(R.string.changes_count, set.changes.size),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            )
                            if (set.warnings.isEmpty()) {
                                Text(stringResource(R.string.no_conflict), color = MintTheme.colors.success, style = MaterialTheme.typography.labelLarge)
                            } else {
                                Text(
                                    stringResource(R.string.warnings_count, set.warnings.size),
                                    color = MintTheme.colors.removed,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                        if (!set.isEmpty) ChangeList(set, snap)
                        WarningsAndLogs(set.warnings, set.logs)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = {
                                    app.advanced.setPreviewOnTimetable(true)
                                    nav.tab = Tab.TIMETABLE
                                    nav.clearToRoot()
                                    onDismiss()
                                },
                                Modifier.weight(1f).heightIn(min = 48.dp),
                                enabled = !set.isEmpty,
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) { Text(stringResource(R.string.preview_on_timetable)) }
                            Button(
                                onClick = {
                                    app.advanced.apply { actions.toast(ctx.getString(R.string.applied_toast, it.changes.size)) }
                                    onDismiss()
                                },
                                Modifier.weight(1f).heightIn(min = 48.dp),
                                enabled = !set.isEmpty,
                            ) { Text(stringResource(R.string.apply_changes)) }
                        }
                    }

                    null -> {}
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        nav.push(Route.AdvancedFull)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.advanced_open_editor))
                }
            }
        }
    }
}

@Composable
private fun AdvancedMessage(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
        exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
    ) {
        val text = message
        if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FailureCard(o: ScriptOutcome.Failure) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MintTheme.colors.conflictBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.script_error), color = MintTheme.colors.removed, style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(o.message + (o.line?.let { "  " + stringResource(R.string.at_line, it) } ?: ""), style = MaterialTheme.typography.bodySmall) }
        if (o.logs.isNotEmpty()) Text(o.logs.joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WarningsAndLogs(
    warnings: List<String>,
    logs: List<String>,
) {
    if (warnings.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MintTheme.colors.conflictBg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            warnings.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (warnings.size > 6) Text(stringResource(R.string.show_more_changes, warnings.size - 6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (logs.isNotEmpty()) {
        Text(
            logs.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp),
        )
    }
}

/** 全屏展开：代码 / 预览 双段切换。 */
@Composable
fun AdvancedFullScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val snap = liveSnapshot()
    val st by app.advanced.state.collectAsState()
    var seg by remember { mutableIntStateOf(0) }
    var showApi by remember { mutableStateOf(false) }
    val cs = st.changeSet
    val slideSpec = MintTheme.spec<IntOffset>()
    val fadeSpec = MintTheme.spec<Float>()

    MintScreen(stringResource(R.string.advanced_title), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SegmentedPill(
                options =
                    listOf(
                        0 to stringResource(R.string.seg_code),
                        1 to stringResource(R.string.seg_preview),
                    ),
                selected = seg,
                onSelect = { seg = it },
            )
            Spacer(Modifier.height(12.dp))
            // 每个 segment 独立滚动位置；切换时带方向滑动
            val bodyScroll = remember(seg) { ScrollState(0) }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(bodyScroll)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedContent(
                    targetState = seg,
                    transitionSpec = {
                        val sign = if (targetState > initialState) 1 else -1
                        (
                            (slideInHorizontally(slideSpec) { sign * it / 4 } + fadeIn(fadeSpec)) togetherWith
                                (slideOutHorizontally(slideSpec) { -sign * it / 4 } + fadeOut(fadeSpec))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "adv-seg",
                ) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (s) {
                            0 -> {
                                CodeEditorField(
                                    value = st.code,
                                    onChange = app.advanced::setCode,
                                    placeholder = stringResource(R.string.advanced_code_placeholder),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            app.advanced.dryRun()
                                            seg = 1
                                        },
                                        enabled = st.code.isNotBlank() && !st.running,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text(stringResource(R.string.dry_run)) }
                                    FilledTonalButton(
                                        onClick = { showApi = true },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        colors =
                                            ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface,
                                            ),
                                    ) { Text(stringResource(R.string.api_reference)) }
                                    AnimatedVisibility(
                                        visible = st.running,
                                        enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                                        exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                                    ) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    }
                                }
                                AdvancedMessage(st.message)
                                SectionLabel(
                                    stringResource(R.string.examples),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                EXAMPLES.forEach { ex ->
                                    val labelText = stringResource(ex.label)
                                    val descText = stringResource(ex.desc)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                            .mintClickable(pressedScale = 0.98f) {
                                                app.advanced.setCode(ex.code)
                                                app.advanced.dryRun()
                                                seg = 1
                                            }.semantics {
                                                role = Role.Button
                                                contentDescription = descText
                                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                labelText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                descText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            else -> {
                                AdvancedMessage(st.message)
                                AnimatedVisibility(
                                    visible = st.running,
                                    enter = scaleIn(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                                    exit = scaleOut(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                                ) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                }
                                when (val o = st.outcome) {
                                    null -> {
                                        if (!st.running) {
                                            Text(
                                                stringResource(R.string.preview_empty_hint),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                            )
                                        }
                                    }

                                    is ScriptOutcome.Failure -> {
                                        FailureCard(o)
                                    }

                                    is ScriptOutcome.Success -> {
                                        val set = o.changeSet
                                        Text(
                                            if (set.isEmpty) stringResource(R.string.no_changes) else stringResource(R.string.changes_count, set.changes.size),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                        )
                                        if (!set.isEmpty) ChangeList(set, snap)
                                        WarningsAndLogs(set.warnings, set.logs)
                                        if (set.returnValue != null) {
                                            Text(stringResource(R.string.return_value, set.returnValue!!), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = cs != null,
                enter = slideInVertically(slideSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideSpec) { it } + fadeOut(fadeSpec),
            ) {
                val bar = cs
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            app.advanced.setPreviewOnTimetable(true)
                            nav.tab = Tab.TIMETABLE
                            nav.clearToRoot()
                        },
                        Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = bar?.isEmpty == false,
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) { Text(stringResource(R.string.preview_on_timetable)) }
                    Button(
                        onClick = {
                            app.advanced.apply { actions.toast(ctx.getString(R.string.applied_toast, it.changes.size)) }
                            onBack()
                        },
                        Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = bar?.isEmpty == false,
                    ) { Text(stringResource(R.string.apply_changes)) }
                }
            }
        }
    }
    if (showApi) {
        AlertDialog(
            onDismissRequest = { showApi = false },
            confirmButton = { TextButton(onClick = { showApi = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.api_reference)) },
            text = {
                Box(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(ScriptSdk.declarations, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)) }
                }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun CodeEditorField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (focused) scheme.primary else Color.Transparent,
        MintTheme.spec(),
        label = "code-border",
    )
    val textStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = scheme.onSurface,
        )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = textStyle,
        minLines = 11,
        cursorBrush = SolidColor(scheme.primary),
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainerHighest)
                    .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = scheme.onSurfaceVariant.copy(alpha = 0.65f)),
                    )
                }
                inner()
            }
        },
    )
}
