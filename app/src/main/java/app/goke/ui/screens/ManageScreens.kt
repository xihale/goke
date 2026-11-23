package app.goke.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.goke.R
import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.CourseException
import app.goke.core.model.EntityType
import app.goke.core.model.Record
import app.goke.core.model.Task
import app.goke.core.model.displayTitle
import app.goke.core.model.entityType
import app.goke.core.parcel.ParcelRule
import app.goke.core.parcel.ParcelRuleEngine
import app.goke.core.parcel.RuleTemplate
import app.goke.core.util.Ids
import app.goke.notify.ParcelTasks
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.EmptyState
import app.goke.ui.components.FormSection
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.NavRow
import app.goke.ui.components.ParcelCodeHeadline
import app.goke.ui.components.SwitchRow
import app.goke.ui.components.TimeDialSheet
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.icons.DeleteOutline
import app.goke.ui.icons.History
import app.goke.ui.icons.Tune
import app.goke.ui.settingsState
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.provider.Settings as AndroidSettings

private val STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun stamp(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(STAMP)

private fun entityName(
    ctx: Context,
    r: Record,
): String =
    ctx.getString(
        when (r.entityType) {
            EntityType.SEMESTER -> R.string.entity_semester
            EntityType.COURSE -> R.string.entity_course
            EntityType.EXCEPTION -> R.string.entity_exception
            EntityType.EVENT -> R.string.entity_event
            EntityType.TASK -> R.string.entity_task
            EntityType.LIST -> R.string.entity_list
        },
    )

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snap by app.repo.snapshot.collectAsState()
    val trashed =
        remember(snap) {
            snap.allRecords().filter { it.deletedAt != null && it !is CourseException }.sortedByDescending { it.deletedAt }
        }
    var confirmEmpty by remember { mutableStateOf(false) }
    var purgeTarget by remember { mutableStateOf<Record?>(null) }

    MintScreen(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = { if (trashed.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_action)) } },
    ) {
        if (trashed.isEmpty()) {
            EmptyState(stringResource(R.string.trash_empty), Modifier.padding(top = 48.dp), icon = Icons.Rounded.DeleteOutline)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trashed, key = { it.id }) { r ->
                    MintCard {
                        Text(r.displayTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${entityName(ctx, r)} · ${stamp(r.deletedAt ?: 0)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                scope.launch {
                                    val deps: List<Record> =
                                        when (r) {
                                            is Course -> snap.exceptions.filter { it.courseId == r.id && it.deletedAt != null }
                                            is Task -> snap.tasks.filter { it.parentId == r.id && it.deletedAt != null }
                                            else -> emptyList()
                                        }
                                    app.repo.restoreFromTrash(r)
                                    deps.forEach { app.repo.restoreFromTrash(it) }
                                }
                            }) { Text(stringResource(R.string.restore)) }
                            TextButton(onClick = { purgeTarget = r }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            stringResource(R.string.trash_empty_action),
            stringResource(R.string.trash_empty_confirm),
            stringResource(R.string.delete_forever),
            { scope.launch { app.repo.purge(snap.allRecords().filter { it.deletedAt != null }) } },
            { confirmEmpty = false },
            danger = true,
        )
    }
    purgeTarget?.let { target ->
        ConfirmDialog(
            stringResource(R.string.delete_forever),
            stringResource(R.string.delete_forever_confirm, target.displayTitle),
            stringResource(R.string.delete_forever),
            {
                scope.launch {
                    val deps: List<Record> =
                        when (target) {
                            is Course -> snap.exceptions.filter { it.courseId == target.id && it.deletedAt != null }
                            is Task -> snap.tasks.filter { it.parentId == target.id && it.deletedAt != null }
                            else -> emptyList()
                        }
                    app.repo.purge(listOf(target) + deps)
                }
            },
            { purgeTarget = null },
            danger = true,
        )
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // oplog 按需加载：冷启动不再读 100 条大 JSON，进入本页时才读
    androidx.compose.runtime.LaunchedEffect(Unit) { app.repo.ensureHistoryLoaded() }
    val history by app.repo.history.collectAsState()
    val snapshot by app.repo.snapshot.collectAsState()

    MintScreen(title = stringResource(R.string.history_title), onBack = onBack) {
        if (history.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty), Modifier.padding(top = 48.dp), icon = Icons.Rounded.History)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { op ->
                    MintCard {
                        Text(op.description.ifBlank { stringResource(R.string.history_unnamed) }, style = MaterialTheme.typography.titleMedium)
                        val src =
                            when (op.source) {
                                "CODE" -> R.string.source_code
                                "AI" -> R.string.source_ai
                                "IMPORT" -> R.string.source_import
                                "PARCEL" -> R.string.source_parcel
                                else -> R.string.source_manual
                            }
                        Text(
                            "${stamp(op.createdAt)} · ${ctx.getString(src)}" + if (op.undoneAt != null) " · " + ctx.getString(R.string.history_undone) else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val canUndo = app.repo.undoable(op, snapshot)
                        val canRedo = app.repo.redoable(op, snapshot)
                        if (canUndo || canRedo) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val ok = if (canUndo) app.repo.undo(op.id) else app.repo.redo(op.id)
                                        val msg =
                                            if (!ok) {
                                                R.string.history_blocked_toast
                                            } else if (canUndo) {
                                                R.string.undone_toast
                                            } else {
                                                R.string.redone_toast
                                            }
                                        actions.toast(ctx.getString(msg))
                                    }
                                }) { Text(stringResource(if (canUndo) R.string.undo else R.string.redo)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun listenerEnabled(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

@Composable
fun ParcelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val st = settingsState()
    val snap =
        app.repo.snapshot
            .collectAsState()
            .value
            .live()
    val rulesCfg by app.parcelRules.flow.collectAsState()
    var sample by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<app.goke.core.parcel.ParcelInfo?>(null) }
    var tried by remember { mutableStateOf(false) }
    var sampleDraft by remember { mutableStateOf<RuleDraft?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(listenerEnabled(ctx)) }
    val enabledCount = rulesCfg.user.count { it.enabled } + ParcelRuleEngine.BUILTIN.count { it.id !in rulesCfg.disabledBuiltins }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = listenerEnabled(ctx)
    }

    fun openListenerSettings() {
        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppDetails() {
        runCatching {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // 二级「短信识别规则」视图的返回手势：模板语法不上主页面，只在全屏子页里编辑
    BackHandler(enabled = showRules) { showRules = false }

    MintScreen(title = stringResource(R.string.parcel_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 段 1：自动识别开关 + 通知使用权状态（无框直落纸底）
            FormSection {
                SwitchRow(stringResource(R.string.parcel_enable), null, st.parcelEnabled) { v ->
                    app.settings.update { it.copy(parcelEnabled = v) }
                    if (v && !granted) openListenerSettings()
                }
                AnimatedVisibility(
                    visible = st.parcelEnabled,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValueRow(
                            stringResource(R.string.parcel_enable_desc),
                            stringResource(if (granted) R.string.settings_perm_allowed else R.string.parcel_grant),
                            valueColor = if (granted) MintTheme.colors.success else MaterialTheme.colorScheme.primary,
                            onClick = { if (!granted) openListenerSettings() },
                        )
                        if (!granted) {
                            MintCard {
                                Text(
                                    stringResource(R.string.parcel_restricted_settings_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { openAppDetails() }) {
                                        Text(stringResource(R.string.parcel_open_app_details))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 段 2：默认取件时段（常驻可见，课表顶栏胶囊同源）
            FormSection(stringResource(R.string.parcel_window_section)) {
                Text(
                    stringResource(R.string.parcel_window_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                var pickStart by remember { mutableStateOf(false) }
                var pickEnd by remember { mutableStateOf(false) }
                ValueRow(
                    stringResource(R.string.parcel_window_start),
                    "%02d:%02d".format(st.parcelWindowStart / 60, st.parcelWindowStart % 60),
                    onClick = { pickStart = true },
                )
                ValueRow(
                    stringResource(R.string.parcel_window_end),
                    "%02d:%02d".format(st.parcelWindowEnd / 60, st.parcelWindowEnd % 60),
                    onClick = { pickEnd = true },
                )
                if (pickStart) {
                    TimeDialSheet(LocalTime.of(st.parcelWindowStart / 60, st.parcelWindowStart % 60), onDismiss = { pickStart = false }) { time ->
                        app.settings.update { it.copy(parcelWindowStart = time.hour * 60 + time.minute) }
                    }
                }
                if (pickEnd) {
                    TimeDialSheet(LocalTime.of(st.parcelWindowEnd / 60, st.parcelWindowEnd % 60), onDismiss = { pickEnd = false }) { time ->
                        app.settings.update { it.copy(parcelWindowEnd = time.hour * 60 + time.minute) }
                    }
                }
            }
            // 段 3：粘贴短信试一试
            FormSection(stringResource(R.string.parcel_try)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalField(
                        stringResource(R.string.parcel_sample_label),
                        sample,
                        {
                            sample = it
                            tried = false
                        },
                        singleLine = false,
                        minLines = 3,
                        placeholder = stringResource(R.string.parcel_test_placeholder),
                    )
                    FilledTonalButton(
                        onClick = {
                            result = rulesCfg.parse(sample, LocalDateTime.now())
                            tried = true
                        },
                        enabled = sample.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.parcel_recognize)) }
                    val r = result
                    if (tried && r == null) {
                        Text(stringResource(R.string.parcel_not_found), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { sampleDraft = RuleDraft(ParcelRule(Ids.next(), "", RuleTemplate.fromSample(sample)), sample = sample, isNew = true) }) {
                            Text(stringResource(R.string.parcel_rule_from_sample))
                        }
                    }
                    if (r != null) {
                        MintCard {
                            ParcelCodeHeadline(
                                code = r.code,
                                done = false,
                                copyEnabled = true,
                            )
                            val subtitle =
                                listOfNotNull(
                                    r.place.takeIf { it.isNotBlank() },
                                    r.company.takeIf { it.isNotBlank() },
                                    r.deadline?.format(STAMP),
                                ).joinToString(" · ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            (rulesCfg.user + ParcelRuleEngine.BUILTIN).firstOrNull { it.id == r.ruleId }?.let {
                                Text(
                                    stringResource(R.string.parcel_test_matched_rule, it.name),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            var addedForResult by remember(r) { mutableStateOf(false) }
                            Button(
                                enabled = !addedForResult,
                                onClick = {
                                    addedForResult = true
                                    val desc = ctx.getString(R.string.parcel_op_desc)
                                    actions.commit(Edits.save(snap, ParcelTasks.build(ctx, r), desc, System.currentTimeMillis()), desc)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text(stringResource(if (addedForResult) R.string.parcel_task_added else R.string.parcel_add_task)) }
                        }
                    }
                }
            }
            // 段 4：识别规则渐进披露入口（模板语法收进二级页）
            NavRow(
                stringResource(R.string.parcel_rules_manage),
                stringResource(R.string.parcel_rules_enabled_count, enabledCount),
                Icons.Rounded.Tune,
            ) { showRules = true }
        }
    }
    if (showRules) {
        MintScreen(
            title = stringResource(R.string.parcel_rules_manage),
            onBack = { showRules = false },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ParcelRulesSection(app.parcelRules)
            }
        }
    }
    sampleDraft?.let { d -> RuleEditSheet(d, app.parcelRules) { sampleDraft = null } }
}
