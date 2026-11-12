package app.mintcal.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
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
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.Course
import app.mintcal.core.model.CourseException
import app.mintcal.core.model.EntityType
import app.mintcal.core.model.Record
import app.mintcal.core.model.Task
import app.mintcal.core.model.displayTitle
import app.mintcal.core.model.entityType
import app.mintcal.core.parcel.ParcelRule
import app.mintcal.core.parcel.ParcelRuleEngine
import app.mintcal.core.parcel.RuleTemplate
import app.mintcal.core.util.Ids
import app.mintcal.notify.ParcelTasks
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.TimeDialSheet
import app.mintcal.ui.components.TonalField
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
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
                        Text("${entityName(ctx, r)} · ${stamp(r.deletedAt ?: 0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            { scope.launch { app.repo.purge(listOf(target)) } },
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
                            style = MaterialTheme.typography.bodySmall,
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
    var result by remember { mutableStateOf<app.mintcal.core.parcel.ParcelInfo?>(null) }
    var tried by remember { mutableStateOf(false) }
    var sampleDraft by remember { mutableStateOf<RuleDraft?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    val granted = listenerEnabled(ctx)
    val enabledCount = rulesCfg.user.count { it.enabled } + ParcelRuleEngine.BUILTIN.count { it.id !in rulesCfg.disabledBuiltins }

    fun openListenerSettings() {
        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
            // 卡 1：自动识别开关 + 通知使用权状态
            MintCard {
                SwitchRow(stringResource(R.string.parcel_enable), null, st.parcelEnabled) { v -> app.settings.update { it.copy(parcelEnabled = v) } }
                AnimatedVisibility(
                    visible = st.parcelEnabled,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    ValueRow(
                        stringResource(R.string.parcel_enable_desc),
                        stringResource(if (granted) R.string.settings_perm_allowed else R.string.parcel_grant),
                        valueColor = if (granted) MintTheme.colors.success else MaterialTheme.colorScheme.primary,
                        onClick = { if (!granted) openListenerSettings() },
                    )
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
                        placeholder = stringResource(R.string.parcel_sample_hint),
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
                            Text(stringResource(R.string.parcel_code, r.code), style = MaterialTheme.typography.titleMedium)
                            (rulesCfg.user + ParcelRuleEngine.BUILTIN).firstOrNull { it.id == r.ruleId }?.let {
                                Text(stringResource(R.string.parcel_rule_matched, it.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.place.isNotBlank()) Text(stringResource(R.string.parcel_place, r.place), style = MaterialTheme.typography.bodyMedium)
                            if (r.company.isNotBlank()) Text(stringResource(R.string.parcel_company, r.company), style = MaterialTheme.typography.bodyMedium)
                            r.deadline?.let { Text(stringResource(R.string.parcel_deadline, it.format(STAMP)), style = MaterialTheme.typography.bodyMedium) }
                            Button(
                                onClick = {
                                    val desc = ctx.getString(R.string.parcel_op_desc)
                                    actions.commit(Edits.save(snap, ParcelTasks.build(ctx, r), desc, System.currentTimeMillis()), desc)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text(stringResource(R.string.parcel_add_task)) }
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
