package app.mintcal.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.mintcal.core.parcel.ParcelParser
import app.mintcal.data.Repository
import app.mintcal.notify.ParcelTasks
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.settingsState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
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

    MintScreen(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = { if (trashed.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_action)) } },
    ) {
        if (trashed.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.trash_hint, Repository.TRASH_DAYS), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text(stringResource(R.string.trash_hint, Repository.TRASH_DAYS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                            TextButton(onClick = { scope.launch { app.repo.purge(listOf(r)) } }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
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
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val history by app.repo.history.collectAsState()
    val snapshot by app.repo.snapshot.collectAsState()

    MintScreen(title = stringResource(R.string.history_title), onBack = onBack) {
        if (history.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        } else {
                            Text(
                                stringResource(if (op.undoneAt == null) R.string.history_blocked_undo else R.string.history_blocked_redo),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
    var sample by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<app.mintcal.core.parcel.ParcelInfo?>(null) }
    var tried by remember { mutableStateOf(false) }
    val granted = listenerEnabled(ctx)

    MintScreen(title = stringResource(R.string.parcel_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.parcel_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormSection {
                SwitchRow(stringResource(R.string.parcel_enable), stringResource(R.string.parcel_enable_desc), st.parcelEnabled) { v -> app.settings.update { it.copy(parcelEnabled = v) } }
                if (st.parcelEnabled && !granted) {
                    Text(stringResource(R.string.parcel_need_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text(stringResource(R.string.parcel_grant)) }
                }
                if (st.parcelEnabled && granted) Text(stringResource(R.string.parcel_access_ok), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            FormSection(stringResource(R.string.parcel_try)) {
                FieldText(stringResource(R.string.parcel_sample_label), sample, {
                    sample = it
                    tried = false
                }, singleLine = false, minLines = 3, placeholder = stringResource(R.string.parcel_sample_hint))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    result = ParcelParser.parse(sample, LocalDateTime.now())
                    tried = true
                }, enabled = sample.isNotBlank()) { Text(stringResource(R.string.parcel_recognize)) }
                val r = result
                if (tried && r == null) Text(stringResource(R.string.parcel_not_found), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                if (r != null) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.parcel_code, r.code), style = MaterialTheme.typography.titleMedium)
                        if (r.place.isNotBlank()) Text(stringResource(R.string.parcel_place, r.place), style = MaterialTheme.typography.bodyMedium)
                        if (r.company.isNotBlank()) Text(stringResource(R.string.parcel_company, r.company), style = MaterialTheme.typography.bodyMedium)
                        r.deadline?.let { Text(stringResource(R.string.parcel_deadline, it.format(STAMP)), style = MaterialTheme.typography.bodyMedium) }
                        Button(onClick = {
                            actions.commit(Edits.save(snap, ParcelTasks.build(ctx, r), ctx.getString(R.string.parcel_op_desc), System.currentTimeMillis()), ctx.getString(R.string.saved_toast))
                        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.parcel_add_task)) }
                    }
                }
            }
            Text(stringResource(R.string.parcel_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
