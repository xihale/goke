package app.mintcal.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Unify
import app.mintcal.core.export.ExportBundle
import app.mintcal.core.importer.IcsCalendar
import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.importer.IcsParser
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.importer.XlsxTimetableImporter
import app.mintcal.core.model.Semester
import app.mintcal.core.time.SemesterCalendar
import app.mintcal.share.Sharing
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.Tab
import app.mintcal.ui.components.ChangeList
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.ConfirmDialog
import app.mintcal.ui.components.FormSection
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.NavRow
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.ValueRow
import app.mintcal.ui.fullDate
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.settingsState
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun DataScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val snap = liveSnapshot()
    val st = settingsState()
    var restoreBundle by remember { mutableStateOf<ExportBundle?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pickBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val parsed =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                ExportBundle.fromJson(
                                    ctx.contentResolver
                                        .openInputStream(uri)!!
                                        .use { it.readBytes() }
                                        .toString(Charsets.UTF_8),
                                )
                            }
                        }
                    parsed.onSuccess { restoreBundle = it }.onFailure { actions.toast(ctx.getString(R.string.restore_failed)) }
                }
            }
        }

    MintScreen(title = stringResource(R.string.data_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormSection(stringResource(R.string.section_import)) {
                NavRow(stringResource(R.string.import_file), stringResource(R.string.import_file_desc), Icons.Rounded.UploadFile) { nav.push(Route.Import(null)) }
            }
            FormSection(stringResource(R.string.section_backup)) {
                SwitchRow(stringResource(R.string.auto_backup), stringResource(R.string.auto_backup_desc, st.backupKeep), st.autoBackup) { v ->
                    app.settings.update { it.copy(autoBackup = v) }
                }
                AnimatedVisibility(
                    visible = st.autoBackup,
                    enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                    exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                ) {
                    Column {
                        Text(stringResource(R.string.backup_keep), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        MinutesChoiceRow(listOf(7, 14, 30), st.backupKeep, 1..365, dialogTitle = stringResource(R.string.backup_keep), label = { stringResource(R.string.backup_keep_n, it) }) { v ->
                            app.settings.update { it.copy(backupKeep = v) }
                        }
                    }
                }
            }
            FormSection(stringResource(R.string.section_export)) {
                NavRow(stringResource(R.string.export_ics), stringResource(R.string.export_ics_desc), Icons.Rounded.IosShare, enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            val f = withContext(Dispatchers.IO) { Sharing.exportIcs(ctx, snap) }
                            Sharing.share(ctx, f, "text/calendar")
                        } finally {
                            busy = false
                        }
                    }
                }
                NavRow(stringResource(R.string.export_image), stringResource(R.string.export_image_desc), Icons.Rounded.Image, enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            val sem = snap.currentSemester
                            val week = sem?.let { SemesterCalendar(it).currentWeek(LocalDate.now()) } ?: 1
                            val f = withContext(Dispatchers.Default) { Sharing.exportTimetableImage(ctx, snap, st, week) }
                            if (f != null) Sharing.share(ctx, f, "image/png") else actions.toast(ctx.getString(R.string.export_failed))
                        } finally {
                            busy = false
                        }
                    }
                }
                NavRow(stringResource(R.string.export_json), stringResource(R.string.export_json_desc), Icons.Rounded.IosShare, enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            val f = withContext(Dispatchers.IO) { Sharing.exportJson(ctx, app.repo.snapshot.value) }
                            Sharing.share(ctx, f, "application/json")
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            FormSection(stringResource(R.string.section_restore)) {
                NavRow(stringResource(R.string.restore_json), stringResource(R.string.restore_json_desc), Icons.Rounded.Restore, enabled = !busy) {
                    pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }
            }
        }
    }
    restoreBundle?.let { b ->
        ConfirmDialog(
            stringResource(R.string.restore_json),
            stringResource(R.string.restore_confirm, b.snapshot.courses.size, b.snapshot.events.size, b.snapshot.tasks.size),
            stringResource(R.string.restore_action),
            {
                scope.launch {
                    app.repo.replaceAll(b.snapshot)
                    actions.toast(ctx.getString(R.string.restored_toast))
                }
            },
            { restoreBundle = null },
            danger = true,
        )
    }
}

private enum class IcsMode { COURSES, EVENTS }

private sealed interface ImportState {
    data object Pick : ImportState

    data object Loading : ImportState

    data class Failed(
        val message: String,
    ) : ImportState

    data class Ready(
        val kind: Kind,
        val bytes: ByteArray,
        val text: String?,
    ) : ImportState

    enum class Kind { XLSX, ICS }
}

private fun readName(
    ctx: Context,
    uri: Uri,
): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

@Composable
fun ImportScreen(
    uri: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val snap = liveSnapshot()
    val sem = snap.currentSemester
    var state by remember { mutableStateOf<ImportState>(if (uri == null) ImportState.Pick else ImportState.Loading) }
    var fileName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(IcsMode.COURSES) }
    var useSuggestedWeek1 by remember { mutableStateOf(false) }

    fun load(u: Uri) {
        state = ImportState.Loading
        scope.launch {
            val res =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val name = readName(ctx, u)
                        val bytes = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
                        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                        name to
                            if (isZip) {
                                ImportState.Ready(ImportState.Kind.XLSX, bytes, null)
                            } else {
                                ImportState.Ready(ImportState.Kind.ICS, bytes, bytes.toString(Charsets.UTF_8))
                            }
                    }
                }
            res
                .onSuccess { (name, s) ->
                    fileName = name
                    state = s
                }.onFailure { state = ImportState.Failed(it.message.orEmpty()) }
        }
    }
    LaunchedEffect(uri) { if (uri != null) load(Uri.parse(uri)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) load(u) }

    MintScreen(title = stringResource(R.string.import_file), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (sem == null) {
                Text(stringResource(R.string.import_no_semester))
                return@Column
            }
            when (val s = state) {
                ImportState.Pick -> {
                    Text(stringResource(R.string.import_pick_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { picker.launch(arrayOf("text/calendar", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*")) }) {
                        Text(stringResource(R.string.import_pick))
                    }
                }

                ImportState.Loading -> {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }

                is ImportState.Failed -> {
                    Text(stringResource(R.string.import_failed), style = MaterialTheme.typography.titleMedium)
                    Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { state = ImportState.Pick }) { Text(stringResource(R.string.import_pick_again)) }
                }

                is ImportState.Ready -> {
                    val now = System.currentTimeMillis()
                    val cal: IcsCalendar? =
                        remember(s) {
                            if (s.kind == ImportState.Kind.ICS) IcsParser.parse(s.text.orEmpty()).value else null
                        }
                    val suggested: LocalDate? = remember(cal) { cal?.let { IcsImporter.suggestWeek1Monday(it) } }
                    LaunchedEffect(cal) {
                        if (cal != null) mode = if (IcsImporter.looksLikeTimetable(cal)) IcsMode.COURSES else IcsMode.EVENTS
                    }
                    val usedSem: Semester = if (useSuggestedWeek1 && suggested != null) sem.copy(week1Monday = suggested) else sem
                    val desc = ctx.getString(R.string.import_op_desc, fileName)
                    // 计算变更集（预览与最终应用用同一个）；XLSX/ICS 解析放在后台，避免大文件卡主线程
                    var plan by remember { mutableStateOf<PlanResult?>(null) }
                    LaunchedEffect(s, mode, usedSem, snap, desc) {
                        plan =
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    when {
                                        s.kind == ImportState.Kind.XLSX -> {
                                            val r = XlsxTimetableImporter.import(s.bytes, usedSem, now)
                                            PlanResult(r.value?.let { ImportPlanner.planCourses(snap, usedSem, it.courses, now, desc) }, r.warnings, r.errors, r.value?.skipped.orEmpty(), r.value?.maxWeek ?: 0)
                                        }

                                        cal == null -> {
                                            PlanResult(null, emptyList(), listOf(ctx.getString(R.string.import_parse_ics_failed)), emptyList(), 0)
                                        }

                                        mode == IcsMode.COURSES -> {
                                            val r = IcsImporter.toCourses(cal, usedSem, now)
                                            PlanResult(r.value?.let { ImportPlanner.planCourses(snap, usedSem, it.courses, now, desc) }, r.warnings, r.errors, r.value?.skipped.orEmpty(), r.value?.maxWeek ?: 0)
                                        }

                                        else -> {
                                            val ev = IcsImporter.toEvents(cal, now)
                                            val tk = IcsImporter.toTasks(cal, now)
                                            // 日程并进待办：导入的日历事件也落成待办
                                            val recs = ev.value.orEmpty().map { Unify.eventToTask(it, it.id, 0) } + tk.value.orEmpty()
                                            PlanResult(ImportPlanner.planAdds(recs, now, desc), ev.warnings + tk.warnings, ev.errors + tk.errors, emptyList(), 0)
                                        }
                                    }
                                }.getOrElse { PlanResult(null, emptyList(), listOf(it.message.orEmpty()), emptyList(), 0) }
                            }
                    }
                    val finalCs: ChangeSet? =
                        plan?.cs?.let { cs ->
                            if (usedSem != sem && (s.kind == ImportState.Kind.XLSX || mode == IcsMode.COURSES)) {
                                cs.copy(changes = listOf<Change>(Change.Modify(sem, usedSem)) + cs.changes)
                            } else {
                                cs
                            }
                        }

                    MintCard {
                        Text(fileName.ifBlank { stringResource(R.string.import_file) }, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (s.kind == ImportState.Kind.XLSX) R.string.import_kind_xlsx else R.string.import_kind_ics), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.kind == ImportState.Kind.ICS && cal != null) {
                        ChipChoice(listOf(IcsMode.COURSES to ctx.getString(R.string.import_as_courses), IcsMode.EVENTS to ctx.getString(R.string.import_as_events)), mode, { mode = it })
                    }
                    if (suggested != null && suggested != sem.week1Monday && (s.kind == ImportState.Kind.XLSX || mode == IcsMode.COURSES)) {
                        if (s.kind == ImportState.Kind.ICS) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.import_use_week1, ctx.fullDate(suggested)), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.import_current_week1, ctx.fullDate(sem.week1Monday)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(useSuggestedWeek1, { useSuggestedWeek1 = it })
                            }
                        }
                    }
                    val p = plan
                    if (p == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        if (p.errors.isNotEmpty()) {
                            MintCard(color = MaterialTheme.colorScheme.errorContainer) {
                                p.errors.forEach { Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        if (p.maxWeek > usedSem.totalWeeks) Text(stringResource(R.string.import_weeks_exceed, p.maxWeek, usedSem.totalWeeks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (p.skipped.isNotEmpty()) Text(stringResource(R.string.import_skipped, p.skipped.size, p.skipped.take(3).joinToString("、")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.warnings.take(5).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                        val cs = finalCs
                        if (cs != null) {
                            if (cs.isEmpty) {
                                Text(stringResource(R.string.import_nothing_new), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(stringResource(R.string.changes_breakdown, cs.addCount, cs.modifyCount, cs.removeCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChangeList(cs, snap)
                                Spacer(Modifier.height(4.dp))
                                Button(onClick = {
                                    actions.commit(cs, ctx.getString(R.string.imported_toast))
                                    nav.tab = Tab.TIMETABLE
                                    onBack()
                                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_changes)) }
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        state = ImportState.Pick
                        useSuggestedWeek1 = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_pick_again)) }
                }
            }
        }
    }
}

private data class PlanResult(
    val cs: ChangeSet?,
    val warnings: List<String>,
    val errors: List<String>,
    val skipped: List<String>,
    val maxWeek: Int,
)
