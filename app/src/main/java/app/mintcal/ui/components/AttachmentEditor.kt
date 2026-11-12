package app.mintcal.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.mintcal.R
import app.mintcal.core.model.Attachment
import app.mintcal.data.Attachments
import app.mintcal.ui.Actions
import app.mintcal.ui.LocalActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 待办附件：相册图片、任意文件、剪贴板粘贴。文件在选中时就复制进私有目录，记录里只留文件名。
 * 本组件只负责展示已添加的附件（缩略图可预览、点开、可删）；添加动作由编辑页底部工具栏触发，
 * 剪贴板是纯文字时交给 [pasteFromClipboard] 的回调写进备注。
 */
@Composable
fun AttachmentEditor(
    items: List<Attachment>,
    onChange: (List<Attachment>) -> Unit,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    var previewFile by remember { mutableStateOf<File?>(null) }
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { a ->
            AttachmentChip(
                a,
                onOpen = {
                    if (a.mime.startsWith("image/")) {
                        val f = Attachments.path(ctx, a)
                        if (f.exists()) previewFile = f else actions.toast(ctx.getString(R.string.attachment_missing))
                    } else {
                        open(ctx, a, actions)
                    }
                },
                onRemove = {
                    Attachments.delete(ctx, a)
                    onChange(items - a)
                },
            )
        }
    }
    previewFile?.let { file ->
        Dialog(
            onDismissRequest = { previewFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp by produceState<ImageBitmap?>(null, file) {
                    value = withContext(Dispatchers.IO) { decodeSampled(file, 1920)?.asImageBitmap() }
                }
                bmp?.let { Image(it, contentDescription = file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                IconButton(
                    onClick = { previewFile = null },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}

/** 选中的内容复制进私有目录并回调；失败时提示。复制是 IO，挂后台后回调（回调里改 Compose 状态，回到主线程）。 */
fun importAttachment(
    ctx: Context,
    uri: Uri,
    actions: Actions,
    onAdded: (Attachment) -> Unit,
) {
    actions.scope.launch {
        val a = Attachments.import(ctx, uri)
        if (a == null) actions.toast(ctx.getString(R.string.attachment_failed)) else onAdded(a)
    }
}

/** 从剪贴板取内容：URI 当附件导入，纯文字交给备注。 */
fun pasteFromClipboard(
    ctx: Context,
    actions: Actions,
    onUri: (Attachment) -> Unit,
    onText: (String) -> Unit,
) {
    val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
    val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    val uri = item?.uri
    if (uri != null) {
        importAttachment(ctx, uri, actions, onUri)
    } else {
        item
            ?.text
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(onText)
    }
}

/** 相册图片附件选择器，返回放进工具栏按钮 onClick 的触发函数。 */
@Composable
fun rememberImageAttachmentPicker(onAdded: (Attachment) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { importAttachment(ctx, it, actions, onAdded) }
        }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** 任意文件附件选择器，返回放进工具栏按钮 onClick 的触发函数。 */
@Composable
fun rememberFileAttachmentPicker(onAdded: (Attachment) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importAttachment(ctx, it, actions, onAdded) }
        }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
private fun AttachmentChip(
    a: Attachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = remember(a.file) { Attachments.path(ctx, a) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(max = 240.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (a.mime.startsWith("image/")) {
                Thumb(file, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                a.name,
                Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 130.dp)
                    .clickable(onClick = onOpen),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, stringResource(R.string.remove_attachment), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Thumb(
    file: File,
    modifier: Modifier,
) {
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, 240)?.asImageBitmap() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** 两趟解码，先读尺寸再按需降采样，避免整张照片进内存。 */
private fun decodeSampled(
    file: File,
    req: Int,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > req * 2) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** 用系统查看器打开（图片、PDF 都能选到合适的应用）；文件不在本机时只提示。 */
private fun open(
    ctx: Context,
    a: Attachment,
    actions: Actions,
) {
    val f = Attachments.path(ctx, a)
    if (!f.exists()) {
        actions.toast(ctx.getString(R.string.attachment_missing))
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { actions.toast(ctx.getString(R.string.attachment_failed)) }
}
