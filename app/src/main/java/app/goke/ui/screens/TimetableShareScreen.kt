package app.goke.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.export.ShareExpiry
import app.goke.core.export.ShareExpiryPreset
import app.goke.share.ShareException
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.CuteQrCode
import app.goke.ui.components.FormSection
import app.goke.ui.components.MintScreen
import app.goke.ui.icons.ContentCopy
import app.goke.ui.icons.DeleteOutline
import app.goke.ui.icons.GridView
import app.goke.ui.liveSnapshot
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SHARE_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val SHARE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun formatShareDate(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SHARE_DATE_FMT)

private fun formatShareTime(ms: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(SHARE_TIME_FMT)

@Composable
private fun presetLabel(key: String): String =
    when (key) {
        "1d" -> stringResource(R.string.share_expiry_1d)
        "7d" -> stringResource(R.string.share_expiry_7d)
        "30d" -> stringResource(R.string.share_expiry_30d)
        "semesterEnd" -> stringResource(R.string.share_expiry_semester_end)
        else -> stringResource(R.string.share_expiry_next_sem_start)
    }

/** 「课表快速分享（实验性）」：生成/更新/删除在线课表分享链接与二维码。 */
@OptIn(ExperimentalLayoutApi::class)
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

    var displayName by remember(state.displayName) { mutableStateOf(state.displayName) }
    var manualId by remember(state.manualStudentId) { mutableStateOf(state.manualStudentId) }
    var serverUrl by remember(state.effectiveServerUrl) { mutableStateOf(state.effectiveServerUrl) }
    var showQr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val presets: List<ShareExpiryPreset> =
        remember(sem?.id, sem?.week1Monday, sem?.totalWeeks) {
            if (sem == null) emptyList() else ShareExpiry.presets(sem, LocalDate.now())
        }
    var selectedExpiresAt by remember(presets) {
        val defaultPreset = presets.firstOrNull { it.key == "30d" } ?: presets.lastOrNull()
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
        if (displayName.isBlank()) {
            actions.toast(ctx.getString(R.string.share_empty_name_err))
            return
        }
        val effectiveId = jxfwState.username?.takeIf { it.isNotBlank() } ?: manualId.trim()
        if (effectiveId.isBlank()) {
            actions.toast(ctx.getString(R.string.share_empty_id_err))
            return
        }
        if (serverUrl.isBlank()) {
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
                    serverUrlOverride = serverUrl,
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
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 实验性功能提示卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.share_experimental_badge),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(R.string.share_experimental_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (state.hasActiveShare) {
                val activeUrl = state.shareUrl.orEmpty()
                val activeCode = state.code.orEmpty()
                FormSection(stringResource(R.string.share_active_section)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.share_url_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    stringResource(R.string.share_code_label, activeCode),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Text(
                                activeUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                state.expiresAt?.let { exp ->
                                    Text(
                                        stringResource(R.string.share_expires_at, formatShareDate(exp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                                state.updatedAt?.let { upd ->
                                    Text(
                                        stringResource(R.string.share_updated_at, formatShareTime(upd)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("goke-share", activeUrl))
                                        actions.toast(ctx.getString(R.string.share_copied_toast))
                                    },
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
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
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.share_system_share))
                                }
                                OutlinedButton(onClick = { showQr = !showQr }) {
                                    Icon(Icons.Rounded.GridView, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
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
                                        modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth(),
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
            }

            FormSection(stringResource(R.string.share_config_section)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            app.timetableShare.updateDraft(displayName = it)
                        },
                        label = { Text(stringResource(R.string.share_display_name)) },
                        placeholder = { Text(stringResource(R.string.share_display_name_placeholder)) },
                        supportingText = { Text(stringResource(R.string.share_display_name_note)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val boundJxfwId = jxfwState.username?.takeIf { it.isNotBlank() }
                    if (boundJxfwId != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    stringResource(R.string.share_student_id),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.share_student_id_bound, boundJxfwId),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.share_student_id_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = manualId,
                            onValueChange = {
                                manualId = it
                                app.timetableShare.updateDraft(manualStudentId = it)
                            },
                            label = { Text(stringResource(R.string.share_student_id)) },
                            placeholder = { Text(stringResource(R.string.share_student_id_placeholder)) },
                            supportingText = { Text(stringResource(R.string.share_student_id_note)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (presets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.share_expiry_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                presets.forEach { preset ->
                                    FilterChip(
                                        selected = selectedExpiresAt == preset.expiresAt,
                                        onClick = { selectedExpiresAt = preset.expiresAt },
                                        label = { Text(presetLabel(preset.key)) },
                                    )
                                }
                            }
                            if (selectedExpiresAt > 0L) {
                                Text(
                                    stringResource(R.string.share_expiry_selected_date, formatShareDate(selectedExpiresAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.share_no_semester),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            app.timetableShare.updateDraft(serverUrl = it)
                        },
                        label = { Text(stringResource(R.string.share_server_url)) },
                        placeholder = { Text(stringResource(R.string.share_server_url_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
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
        }
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
                        app.timetableShare.deleteCloud(serverUrlOverride = serverUrl)
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
