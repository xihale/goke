package app.goke.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.BuildConfig
import app.goke.R
import app.goke.data.ErrorReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 异常堆栈诊断弹窗：展示异常摘要与完整堆栈，支持一键复制到剪贴板并发给开发者。
 */
@Composable
fun ErrorReportDialog(
    report: ErrorReport,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var copied by remember(report.id) { mutableStateOf(false) }
    val copiedToast = stringResource(R.string.error_modal_copied)

    MintAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(if (report.fatal) R.string.error_modal_title_crash else R.string.error_modal_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.error_modal_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(12.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = report.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyErrorText(ctx, report.detail, copiedToast)
                    copied = true
                },
            ) {
                Text(stringResource(if (copied) R.string.error_modal_copy_done else R.string.error_modal_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/**
 * 历史异常记录列表弹窗（入口在「关于」页）：方便用户在错失实时弹窗后随时查看、复制或清理堆栈记录。
 */
@Composable
fun ErrorLogListDialog(
    reports: List<ErrorReport>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<ErrorReport?>(null) }
    val active = selected
    if (active != null) {
        ErrorReportDialog(report = active, onDismiss = { selected = null })
        return
    }

    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    MintAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.error_log_title)) },
        text = {
            if (reports.isEmpty()) {
                Text(
                    stringResource(R.string.error_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    reports.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .mintClickable(pressedScale = 0.98f, role = Role.Button) {
                                    selected = item
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "${item.source} · ${formatter.format(Date(item.timestampMs))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (item.fatal) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton =
            if (reports.isNotEmpty()) {
                {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.error_log_clear))
                    }
                }
            } else {
                null
            },
    )
}

@Composable
internal fun CopyErrorButton(
    errorSummary: String,
    reports: List<ErrorReport>,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var copied by remember(errorSummary, reports.firstOrNull()?.id) { mutableStateOf(false) }
    val copiedToast = stringResource(R.string.error_modal_copied)
    TextButton(
        onClick = {
            val matched =
                reports.firstOrNull {
                    errorSummary.isNotBlank() && (errorSummary in it.detail || errorSummary in it.summary)
                }
            val payload =
                matched?.detail ?: buildString {
                    appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim())
                    appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
                    append("error=").append(errorSummary)
                }
            copyErrorText(ctx, payload, copiedToast)
            copied = true
        },
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(stringResource(if (copied) R.string.error_modal_copy_done else R.string.error_modal_copy))
    }
}

internal fun copyErrorText(
    ctx: Context,
    detail: String,
    toastText: String,
) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("goke_error_report", detail))
    Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show()
}
