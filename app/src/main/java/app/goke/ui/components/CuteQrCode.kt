package app.goke.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import app.goke.R
import app.goke.core.util.QrMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主题自适应的圆润可爱二维码：纯矢量 `Canvas` 绘制，不使用静态图片，
 * 相邻模块以柔和圆角胶囊桥接，中心留白放置工课矢量标志。
 */
@Composable
fun CuteQrCode(
    payload: String,
    contentDesc: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(payload) { QrMatrix.encode(payload) }
    val scheme = MaterialTheme.colorScheme
    // 透明底直接融合宿主卡片背景，使用 M3 高对比主容器前景色对，深浅模式与全部配色均和谐可读
    val inkColor = scheme.onPrimaryContainer
    val badgeMarkTint = scheme.primaryContainer

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { contentDescription = contentDesc }
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = matrix.size
            val cell = size.width / n
            val center = (n - 1) / 2f
            val holeR = n * 0.135f

            fun inFinder(
                r: Int,
                c: Int,
            ): Boolean =
                (r < 7 && c < 7) ||
                    (r < 7 && c >= n - 7) ||
                    (r >= n - 7 && c < 7)

            fun inCenterHole(
                r: Int,
                c: Int,
            ): Boolean {
                val dr = r - center
                val dc = c - center
                return dr * dr + dc * dc <= holeR * holeR
            }

            fun isDataDot(
                r: Int,
                c: Int,
            ): Boolean = r in 0 until n && c in 0 until n && !inFinder(r, c) && !inCenterHole(r, c) && matrix[r][c]

            val dotSize = cell * 0.98f
            val dotRadius = CornerRadius(dotSize * 0.48f, dotSize * 0.48f)
            val dotInset = (cell - dotSize) * 0.5f
            val bridgeThickness = cell * 0.96f
            val bridgeInset = (cell - bridgeThickness) * 0.5f

            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (!isDataDot(r, c)) continue
                    val x = c * cell
                    val y = r * cell
                    drawRoundRect(
                        color = inkColor,
                        topLeft = Offset(x + dotInset, y + dotInset),
                        size = Size(dotSize, dotSize),
                        cornerRadius = dotRadius,
                    )
                    if (isDataDot(r, c + 1)) {
                        drawRect(
                            color = inkColor,
                            topLeft = Offset(x + cell * 0.5f, y + bridgeInset),
                            size = Size(cell, bridgeThickness),
                        )
                    }
                    if (isDataDot(r + 1, c)) {
                        drawRect(
                            color = inkColor,
                            topLeft = Offset(x + bridgeInset, y + cell * 0.5f),
                            size = Size(bridgeThickness, cell),
                        )
                    }
                }
            }

            fun drawFinder(
                top: Int,
                left: Int,
            ) {
                val x = left * cell
                val y = top * cell
                val outerSpan = cell * 6f
                drawRoundRect(
                    color = inkColor,
                    topLeft = Offset(x + cell * 0.5f, y + cell * 0.5f),
                    size = Size(outerSpan, outerSpan),
                    cornerRadius = CornerRadius(cell * 0.45f, cell * 0.45f),
                    style = Stroke(width = cell),
                )
                drawRoundRect(
                    color = inkColor,
                    topLeft = Offset(x + cell * 2f, y + cell * 2f),
                    size = Size(cell * 3f, cell * 3f),
                    cornerRadius = CornerRadius(cell * 0.45f, cell * 0.45f),
                )
            }

            drawFinder(0, 0)
            drawFinder(0, n - 7)
            drawFinder(n - 7, 0)
        }

        // 中心品牌徽章：透明留白外圈 + 主题墨色圆徽 + 矢量工课标志
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(inkColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_goke_mark),
                contentDescription = null,
                tint = badgeMarkTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 关于页「Goke 软件交流群」卡片弹窗：动态主题二维码 + 复制群号 + 保存二维码 + 直达 QQ/TIM 加群。 */
@Composable
fun QqGroupCardDialog(
    groupNumber: String,
    joinUrl: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val scope = rememberCoroutineScope()
    val cardLayer = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }
    val copiedToast = stringResource(R.string.about_qq_copied, groupNumber)
    val savedToast = stringResource(R.string.about_qq_saved)
    val saveFailedToast = stringResource(R.string.about_qq_save_failed)

    Dialog(onDismissRequest = onDismiss) {
        val cardColor = scheme.surfaceContainerHigh
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cardColor,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                // 单独录制卡片主体（含纯色背景与二维码），底部按钮栏不写入保存的图片
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                cardLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(cardLayer)
                            }.background(cardColor)
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.about_qq_group_name),
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_qq_group_number_label, groupNumber),
                        style = typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    CuteQrCode(
                        payload = joinUrl,
                        contentDesc = stringResource(R.string.about_qq_qr_a11y, groupNumber),
                        modifier = Modifier.size(212.dp),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("qq_group", groupNumber))
                            Toast.makeText(ctx, copiedToast, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(R.string.about_qq_copy_action), maxLines = 1)
                    }
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val ok =
                                    runCatching {
                                        val bmp = cardLayer.toImageBitmap().asAndroidBitmap()
                                        withContext(Dispatchers.IO) {
                                            saveQrCardImage(ctx, bmp, "goke_qq_$groupNumber.png")
                                        }
                                    }.getOrDefault(false)
                                saving = false
                                Toast.makeText(ctx, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.about_qq_save_action), maxLines = 1)
                    }
                    TextButton(
                        onClick = { openQqGroup(ctx, groupNumber, joinUrl) },
                    ) {
                        Text(stringResource(R.string.about_qq_join_action), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun saveQrCardImage(
    ctx: Context,
    bitmap: Bitmap,
    fileName: String,
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Goke")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("compress failed")
                }
            } ?: error("openOutputStream null")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrDefault(false)
    }

    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val outFile = File(dir, fileName)
    outFile.outputStream().use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", outFile)
    val share =
        Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(share, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

internal fun openQqGroup(
    ctx: Context,
    groupNumber: String,
    joinUrl: String,
) {
    val candidates =
        listOf(
            "mqqopensdkapi://bizAgent/qm/qr?url=${Uri.encode(joinUrl)}",
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupNumber&card_type=group&source=qrcode",
            joinUrl,
        )
    for (uri in candidates) {
        val ok =
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
        if (ok) return
    }
    Toast.makeText(ctx, R.string.update_open_failed, Toast.LENGTH_SHORT).show()
}
