package app.goke.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.export.ShareExpiry
import app.goke.core.export.ShareExpiryPreset
import app.goke.share.ShareException
import app.goke.share.TimetableShareManager
import app.goke.sync.SyncClient
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ChipChoice
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.CuteQrCode
import app.goke.ui.components.MintAlertDialog
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.TonalField
import app.goke.ui.components.ValueRow
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.ContentCopy
import app.goke.ui.icons.DeleteOutline
import app.goke.ui.icons.GridView
import app.goke.ui.icons.OpenInNew
import app.goke.ui.liveSnapshot
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SHARE_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatShareDate(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SHARE_DATE_FMT)

@Composable
private fun presetLabel(key: String): String =
    when (key) {
        "1d" -> stringResource(R.string.share_expiry_1d)
        "7d" -> stringResource(R.string.share_expiry_7d)
        "30d" -> stringResource(R.string.share_expiry_30d)
        "semesterEnd" -> stringResource(R.string.share_expiry_semester_end)
        else -> stringResource(R.string.share_expiry_next_sem_start)
    }

/** 「课表快速分享」：生成/更新/删除在线课表分享链接与二维码。 */
@Composable
fun TimetableShareScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    val snap = liveSnapshot()
    val sem = snap.currentSemester
    val state by app.timetableShare.state.collectAsState()
    val jxfwState by app.jxfw.state.collectAsState()

    var displayName by remember { mutableStateOf(state.displayName) }
    var manualId by remember { mutableStateOf(state.manualStudentId) }
    LaunchedEffect(state.displayName) {
        if (state.displayName != displayName) displayName = state.displayName
    }
    LaunchedEffect(state.manualStudentId) {
        if (state.manualStudentId != manualId) manualId = state.manualStudentId
    }

    var showQr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf("") }

    val presets: List<ShareExpiryPreset> =
        remember(sem?.id, sem?.week1Monday, sem?.totalWeeks) {
            if (sem == null) emptyList() else ShareExpiry.presets(sem, LocalDate.now())
        }
    var selectedExpiresAt by remember(presets) {
        val defaultPreset = presets.firstOrNull { it.key == "semesterEnd" } ?: presets.lastOrNull()
        mutableLongStateOf(defaultPreset?.expiresAt ?: 0L)
    }

    LaunchedEffect(jxfwState.username) {
        app.timetableShare.syncFromEnvironment()
    }
    LaunchedEffect(state.hasActiveShare) {
        if (state.hasActiveShare) {
            app.timetableShare.refreshStatus()
        }
    }

    fun submitPublish(isUpdate: Boolean) {
        if (sem == null) {
            actions.toast(ctx.getString(R.string.share_no_semester))
            return
        }
        val effectiveId = jxfwState.username?.takeIf { it.isNotBlank() } ?: manualId.trim()
        if (effectiveId.isBlank()) {
            actions.toast(ctx.getString(R.string.share_empty_id_err))
            return
        }
        val effectiveServerUrl = state.effectiveServerUrl
        if (effectiveServerUrl.isBlank()) {
            actions.toast(ctx.getString(R.string.share_empty_server_err))
            return
        }
        val expiresAt = selectedExpiresAt.takeIf { it > 0L } ?: ShareExpiry.maxMillis(sem)
        scope.launch {
            try {
                app.timetableShare.publishOrUpdate(
                    displayName = displayName,
                    studentIdOverride = manualId,
                    expiresAt = expiresAt,
                    serverUrlOverride = effectiveServerUrl,
                )
                actions.toast(ctx.getString(if (isUpdate) R.string.share_updated_toast else R.string.share_published_toast))
            } catch (e: ShareException) {
                actions.toast(e.message ?: ctx.getString(R.string.export_failed))
            }
        }
    }

    MintScreen(title = stringResource(R.string.share_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.hasActiveShare) {
                val activeUrl = state.shareUrl.orEmpty()
                MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .mintClickable(pressedScale = 0.98f, role = Role.Button) {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                activeUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = stringResource(R.string.share_open_in_browser),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        state.expiresAt?.let { exp ->
                            Text(
                                stringResource(R.string.share_expires_at, formatShareDate(exp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(ClipData.newPlainText("goke-share", activeUrl))
                                    actions.toast(ctx.getString(R.string.share_copied_toast))
                                },
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.share_copy_link))
                            }
                            FilledTonalButton(
                                onClick = {
                                    val send =
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, activeUrl)
                                    val chooser =
                                        Intent
                                            .createChooser(send, ctx.getString(R.string.share_send_chooser))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(chooser)
                                },
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.share_system_share))
                            }
                            OutlinedButton(onClick = { showQr = !showQr }) {
                                Icon(Icons.Rounded.GridView, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(if (showQr) R.string.share_qr_hide else R.string.share_qr_show))
                            }
                        }

                        AnimatedVisibility(
                            visible = showQr,
                            enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                            exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CuteQrCode(
                                    payload = activeUrl,
                                    contentDesc = stringResource(R.string.share_qr_desc),
                                    modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth(),
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { submitPublish(isUpdate = true) },
                                enabled = !state.busy && sem != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(R.string.share_update_cloud),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                enabled = !state.busy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.share_delete_cloud))
                            }
                        }
                    }
                }
            }

            TonalField(
                label = stringResource(R.string.share_display_name),
                value = displayName,
                onChange = {
                    displayName = it
                    app.timetableShare.updateDraft(displayName = it)
                },
                placeholder = stringResource(R.string.share_display_name_placeholder),
            )

            val boundJxfwId = jxfwState.username?.takeIf { it.isNotBlank() }
            if (boundJxfwId != null) {
                Column(
                    Modifier.padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.share_student_id_bound, boundJxfwId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.share_anon_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TonalField(
                        label = stringResource(R.string.share_student_id),
                        value = manualId,
                        onChange = {
                            manualId = it
                            app.timetableShare.updateDraft(manualStudentId = it)
                        },
                        placeholder = stringResource(R.string.share_student_id_placeholder),
                    )
                    Text(
                        stringResource(R.string.share_anon_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            if (presets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.share_expiry_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selectedExpiresAt > 0L) {
                            Text(
                                stringResource(R.string.share_expiry_until, formatShareDate(selectedExpiresAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ChipChoice(
                        options = presets.map { it.expiresAt to presetLabel(it.key) },
                        selected = selectedExpiresAt,
                        onSelect = { selectedExpiresAt = it },
                    )
                }
            } else {
                Text(
                    stringResource(R.string.share_no_semester),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val serverValueText =
                if (state.isCustomServer) {
                    state.serverUrl.removePrefix("https://").removePrefix("http://")
                } else {
                    stringResource(R.string.share_server_default)
                }
            ValueRow(
                label = stringResource(R.string.share_server),
                value = serverValueText,
                onClick = {
                    serverDraft = if (state.isCustomServer) state.serverUrl else ""
                    showServerDialog = true
                },
            )

            state.lastError?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!state.hasActiveShare) {
                Button(
                    onClick = { submitPublish(isUpdate = false) },
                    enabled = !state.busy && sem != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.share_publish_action))
                }
            } else if (state.busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    if (showServerDialog) {
        MintAlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text(stringResource(R.string.share_server_dialog_title)) },
            text = {
                TonalField(
                    label = "",
                    value = serverDraft,
                    onChange = { serverDraft = it },
                    placeholder = TimetableShareManager.DEFAULT_SERVER_URL,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        app.timetableShare.updateDraft(serverUrl = "")
                        showServerDialog = false
                    },
                ) {
                    Text(stringResource(R.string.share_server_reset))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = serverDraft.trim()
                        if (trimmed.isEmpty()) {
                            app.timetableShare.updateDraft(serverUrl = "")
                            showServerDialog = false
                        } else {
                            val normalized = SyncClient.normalizeUrl(trimmed)
                            if (normalized == null) {
                                actions.toast(ctx.getString(R.string.share_empty_server_err))
                            } else {
                                app.timetableShare.updateDraft(serverUrl = normalized)
                                showServerDialog = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.share_delete_confirm_title),
            text = stringResource(R.string.share_delete_confirm_text),
            confirm = stringResource(R.string.share_delete_cloud),
            danger = true,
            onConfirm = {
                scope.launch {
                    try {
                        app.timetableShare.deleteCloud(serverUrlOverride = state.effectiveServerUrl)
                        showQr = false
                        actions.toast(ctx.getString(R.string.share_deleted_toast))
                    } catch (e: ShareException) {
                        actions.toast(e.message ?: ctx.getString(R.string.export_failed))
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
