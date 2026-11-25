package app.goke.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import app.goke.R
import app.goke.core.model.Attachment
import app.goke.data.Attachments
import app.goke.ui.Actions
import app.goke.ui.LocalActions
import app.goke.ui.icons.Image
import app.goke.ui.icons.UploadFile
import app.goke.ui.reduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 待办附件：图片附件以展开网格展示（长按任意图片拖动可平滑缩放并磁吸到 1×1 / 2×2 / 3×3 排布，
 * 点击打开全屏查看器）；非图片文件保持紧凑胶囊样式放在下方。
 */
@Composable
fun AttachmentEditor(
    items: List<Attachment>,
    onChange: (List<Attachment>) -> Unit,
    imageColumns: Int? = null,
    onImageColumnsChange: ((Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val images = remember(items) { items.filter { it.mime.startsWith("image/") } }
    val files = remember(items) { items.filterNot { it.mime.startsWith("image/") } }

    var localCols by remember { mutableStateOf<Int?>(null) }
    val defaultCols =
        when {
            images.size <= 1 -> 1
            images.size <= 4 -> 2
            else -> 3
        }
    val activeCols = (imageColumns ?: localCols ?: defaultCols).coerceIn(1, 3)
    val updateCols: (Int) -> Unit = { next ->
        val clamped = next.coerceIn(1, 3)
        if (onImageColumnsChange != null) {
            onImageColumnsChange(clamped)
        } else {
            localCols = clamped
        }
    }

    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        if (images.isNotEmpty()) {
            ImageAttachmentGrid(
                images = images,
                cols = activeCols,
                onColsChange = updateCols,
                onOpen = { index ->
                    val target = images.getOrNull(index) ?: return@ImageAttachmentGrid
                    val f = Attachments.path(ctx, target)
                    if (f.exists()) {
                        viewerIndex = index
                    } else {
                        actions.toast(ctx.getString(R.string.attachment_missing))
                    }
                },
                onRemove = { target ->
                    Attachments.delete(ctx, target)
                    onChange(items - target)
                },
            )
        }

        if (images.isNotEmpty() && files.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
        }

        if (files.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                files.forEach { a ->
                    AttachmentChip(
                        a = a,
                        onOpen = { open(ctx, a, actions) },
                        onRemove = {
                            Attachments.delete(ctx, a)
                            onChange(items - a)
                        },
                    )
                }
            }
        }
    }

    viewerIndex?.let { startIndex ->
        ImageViewerDialog(
            images = images,
            initialIndex = startIndex,
            onDismiss = { viewerIndex = null },
            onDelete = { target ->
                Attachments.delete(ctx, target)
                onChange(items - target)
            },
        )
    }
}

internal fun tileWidthForColumns(
    containerWidth: Float,
    cols: Int,
    gapPx: Float,
): Float {
    val c = cols.coerceIn(1, 3)
    return (containerWidth - gapPx * (c - 1)).coerceAtLeast(0f) / c
}

internal fun snapColumnsForWidth(
    widthPx: Float,
    containerWidth: Float,
    currentCols: Int,
    gapPx: Float,
): Int {
    if (containerWidth <= 0f) return currentCols.coerceIn(1, 3)
    val w1 = tileWidthForColumns(containerWidth, 1, gapPx)
    val w2 = tileWidthForColumns(containerWidth, 2, gapPx)
    val w3 = tileWidthForColumns(containerWidth, 3, gapPx)
    val m12 = (w1 + w2) / 2f
    val m23 = (w2 + w3) / 2f
    val h = containerWidth * 0.035f
    return when (currentCols.coerceIn(1, 3)) {
        1 -> {
            when {
                widthPx < m23 - h -> 3
                widthPx < m12 - h -> 2
                else -> 1
            }
        }

        2 -> {
            when {
                widthPx > m12 + h -> 1
                widthPx < m23 - h -> 3
                else -> 2
            }
        }

        else -> {
            when {
                widthPx > m12 + h -> 1
                widthPx > m23 + h -> 2
                else -> 3
            }
        }
    }
}

internal fun magneticDisplayWidth(
    rawWidthPx: Float,
    containerWidth: Float,
    snappedCols: Int,
    gapPx: Float,
): Float {
    if (containerWidth <= 0f) return rawWidthPx.coerceAtLeast(0f)
    val minW = tileWidthForColumns(containerWidth, 3, gapPx) * 0.86f
    val maxW = tileWidthForColumns(containerWidth, 1, gapPx) * 1.03f
    val clamped = rawWidthPx.coerceIn(minW, maxW)
    val snapW = tileWidthForColumns(containerWidth, snappedCols, gapPx)
    val pullZone = containerWidth * 0.14f
    val dist = abs(clamped - snapW)
    return if (dist < pullZone && pullZone > 0f) {
        val strength = 0.28f * (1f - dist / pullZone)
        clamped + (snapW - clamped) * strength
    } else {
        clamped
    }
}

@Composable
private fun ImageAttachmentGrid(
    images: List<Attachment>,
    cols: Int,
    onColsChange: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onRemove: (Attachment) -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reduce = reduceMotion()

    val aspectRatios = remember { mutableStateMapOf<String, Float>() }

    var isResizing by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    var startCols by remember { mutableIntStateOf(cols) }
    var targetCols by remember { mutableIntStateOf(cols) }
    var rawDragWidthPx by remember { mutableFloatStateOf(0f) }

    val currentColsRef by rememberUpdatedState(cols)
    val onColsChangeRef by rememberUpdatedState(onColsChange)
    val isResizingRef by rememberUpdatedState(isResizing)
    val onOpenRef by rememberUpdatedState(onOpen)

    val displayCols = if (isResizing) targetCols else cols

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val gapDp = if (displayCols == 3) 6.dp else 8.dp
        val gapPx = with(density) { gapDp.toPx() }
        val cornerDp = if (displayCols == 3) 12.dp else 16.dp
        val cornerPx = with(density) { cornerDp.toPx() }
        val minSingleHPx = with(density) { 160.dp.toPx() }
        val maxSingleHPx = with(density) { 360.dp.toPx() }

        val slotW = tileWidthForColumns(containerWidthPx, displayCols, gapPx)

        val tileHeightsPx =
            remember(images, displayCols, containerWidthPx, aspectRatios.toMap()) {
                images.map { img ->
                    if (displayCols == 1) {
                        val ar = (aspectRatios[img.file] ?: (4f / 3f)).coerceIn(0.75f, 1.75f)
                        (slotW / ar).coerceIn(minSingleHPx, maxSingleHPx)
                    } else {
                        slotW
                    }
                }
            }

        val rowYs =
            remember(tileHeightsPx, displayCols, gapPx) {
                val rows = (images.size + displayCols - 1) / displayCols
                val ys = ArrayList<Float>(rows + 1)
                var y = 0f
                for (r in 0 until rows) {
                    ys.add(y)
                    val start = r * displayCols
                    val end = (start + displayCols).coerceAtMost(images.size)
                    var rowH = 0f
                    for (idx in start until end) {
                        rowH = maxOf(rowH, tileHeightsPx[idx])
                    }
                    y += rowH + gapPx
                }
                val total = if (rows == 0) 0f else (y - gapPx).coerceAtLeast(0f)
                ys to total
            }

        val tileSlots =
            remember(images, displayCols, slotW, gapPx, tileHeightsPx, rowYs) {
                images.indices.map { i ->
                    val r = i / displayCols
                    val c = i % displayCols
                    TileRect(
                        x = c * (slotW + gapPx),
                        y = rowYs.first.getOrElse(r) { 0f },
                        w = slotW,
                        h = tileHeightsPx.getOrElse(i) { slotW },
                    )
                }
            }
        val tileSlotsRef by rememberUpdatedState(tileSlots)

        val totalHeightDp by animateDpAsState(
            targetValue = with(density) { rowYs.second.toDp() },
            animationSpec =
                if (reduce) {
                    snap()
                } else {
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                },
            label = "attachment-grid-height",
        )

        val guideAlpha by animateFloatAsState(
            targetValue = if (isResizing) 1f else 0f,
            animationSpec = MintTheme.spec(),
            label = "attachment-guide-alpha",
        )
        val primaryColor = MaterialTheme.colorScheme.primary

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(totalHeightDp)
                    .pointerInput(images.map { it.file }, containerWidthPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                val hitIdx =
                                    tileSlotsRef.indexOfFirst { r ->
                                        startOffset.x >= r.x &&
                                            startOffset.x <= r.x + r.w &&
                                            startOffset.y >= r.y &&
                                            startOffset.y <= r.y + r.h
                                    }
                                if (hitIdx >= 0) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val startC = currentColsRef
                                    startCols = startC
                                    targetCols = startC
                                    activeIndex = hitIdx
                                    rawDragWidthPx = tileWidthForColumns(containerWidthPx, startC, gapPx)
                                    isResizing = true
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (!isResizingRef) return@detectDragGesturesAfterLongPress
                                change.consume()
                                rawDragWidthPx += dragAmount.x + dragAmount.y * 0.75f
                                val nextCols =
                                    snapColumnsForWidth(
                                        widthPx = rawDragWidthPx,
                                        containerWidth = containerWidthPx,
                                        currentCols = targetCols,
                                        gapPx = gapPx,
                                    )
                                if (nextCols != targetCols) {
                                    targetCols = nextCols
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                            onDragEnd = {
                                if (!isResizingRef) return@detectDragGesturesAfterLongPress
                                val finalCols = targetCols
                                if (finalCols != startCols) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                                onColsChangeRef(finalCols)
                                isResizing = false
                                activeIndex = -1
                            },
                            onDragCancel = {
                                if (!isResizingRef) return@detectDragGesturesAfterLongPress
                                onColsChangeRef(targetCols)
                                isResizing = false
                                activeIndex = -1
                            },
                        )
                    },
        ) {
            if (guideAlpha > 0.01f) {
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(0f),
                ) {
                    val guideSlotW = tileWidthForColumns(size.width, targetCols, gapPx)
                    val strokePx = 1.5.dp.toPx()
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f)
                    for (c in 0 until targetCols) {
                        val x = c * (guideSlotW + gapPx)
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.07f * guideAlpha),
                            topLeft = Offset(x, 0f),
                            size = Size(guideSlotW, size.height),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                        )
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.42f * guideAlpha),
                            topLeft = Offset(x, 0f),
                            size = Size(guideSlotW, size.height),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx, pathEffect = dashEffect),
                        )
                    }
                }
            }

            images.forEachIndexed { index, attachment ->
                val file = remember(attachment.file) { Attachments.path(ctx, attachment) }
                val row = index / displayCols
                val col = index % displayCols
                val slotX = col * (slotW + gapPx)
                val slotY = rowYs.first.getOrElse(row) { 0f }
                val slotH = tileHeightsPx.getOrElse(index) { slotW }

                val isActive = isResizing && index == activeIndex
                val targetW =
                    if (isActive) {
                        magneticDisplayWidth(rawDragWidthPx, containerWidthPx, targetCols, gapPx)
                    } else {
                        slotW
                    }
                val targetH =
                    if (isActive && slotW > 0f) {
                        (slotH * (targetW / slotW)).coerceIn(
                            with(density) { 80.dp.toPx() },
                            maxSingleHPx * 1.05f,
                        )
                    } else {
                        slotH
                    }
                val targetX =
                    if (isActive) {
                        (slotX + (slotW - targetW) / 2f).coerceIn(0f, (containerWidthPx - targetW).coerceAtLeast(0f))
                    } else {
                        slotX
                    }

                val posSpec =
                    if (reduce) {
                        snap<Float>()
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    }
                val sizeSpec =
                    if (isActive || reduce) {
                        snap<Float>()
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    }

                val animX by animateFloatAsState(targetX, posSpec, label = "tile-x-$index")
                val animY by animateFloatAsState(slotY, posSpec, label = "tile-y-$index")
                val animW by animateFloatAsState(targetW, sizeSpec, label = "tile-w-$index")
                val animH by animateFloatAsState(targetH, sizeSpec, label = "tile-h-$index")
                val tileAlpha by animateFloatAsState(
                    targetValue = if (isResizing && !isActive) 0.78f else 1f,
                    animationSpec = MintTheme.spec(),
                    label = "tile-alpha-$index",
                )
                val activeLift by animateFloatAsState(
                    targetValue = if (isActive) 1.02f else 1f,
                    animationSpec = MintTheme.spec(),
                    label = "tile-lift-$index",
                )

                val interactionSource = remember { MutableInteractionSource() }
                val shape = RoundedCornerShape(cornerDp)
                val removeDesc = stringResource(R.string.remove_attachment)

                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(animX.roundToInt(), animY.roundToInt()) }
                            .size(
                                width = with(density) { animW.toDp() },
                                height = with(density) { animH.toDp() },
                            ).zIndex(if (isActive) 2f else 1f)
                            .graphicsLayer {
                                scaleX = activeLift
                                scaleY = activeLift
                            }.alpha(tileAlpha)
                            .then(
                                if (isActive) {
                                    Modifier.shadow(10.dp, shape)
                                } else {
                                    Modifier
                                },
                            ).clip(shape)
                            .then(
                                if (isActive) {
                                    Modifier.border(2.dp, primaryColor, shape)
                                } else {
                                    Modifier
                                },
                            ).mintPressScale(interactionSource, pressedScale = 0.97f, enabled = !isResizing)
                            .pointerInput(attachment.file) {
                                detectTapGestures(
                                    onPress = { pressOffset ->
                                        if (!isResizingRef) {
                                            val press = PressInteraction.Press(pressOffset)
                                            interactionSource.emit(press)
                                            tryAwaitRelease()
                                            interactionSource.emit(PressInteraction.Release(press))
                                        }
                                    },
                                    onTap = {
                                        if (!isResizingRef) onOpenRef(index)
                                    },
                                )
                            },
                ) {
                    ExpandedImageTile(
                        file = file,
                        name = attachment.name,
                        maxDecodePx = if (displayCols == 1) 1280 else 720,
                        onAspectRatio = { ratio -> aspectRatios[attachment.file] = ratio },
                    )

                    if (!isResizing) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .mintClickable(
                                        pressedScale = 0.88f,
                                        role = Role.Button,
                                        onClick = { onRemove(attachment) },
                                    ).semantics { contentDescription = removeDesc },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.54f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedImageTile(
    file: File,
    name: String,
    maxDecodePx: Int,
    onAspectRatio: (Float) -> Unit,
) {
    val onAspectRatioRef by rememberUpdatedState(onAspectRatio)
    val decoded by produceState<DecodedImage?>(null, file, maxDecodePx) {
        val result = withContext(Dispatchers.IO) { decodeImageWithMeta(file, maxDecodePx) }
        if (result != null) onAspectRatioRef(result.aspectRatio)
        value = result
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val img = decoded
        if (img != null) {
            Image(
                bitmap = img.bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Image,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp),
            )
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(max = 240.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                a.name,
                Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 140.dp)
                    .mintClickable(pressedScale = 0.97f, onClick = onOpen),
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

internal data class DecodedImage(
    val bitmap: ImageBitmap,
    val aspectRatio: Float,
)

private data class TileRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/** 两趟解码并按 EXIF 方向校正，避免整张照片进内存或竖拍横置。 */
internal fun decodeImageWithMeta(
    file: File,
    maxDim: Int,
): DecodedImage? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val maxOut = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxOut / sample > maxDim * 2) sample *= 2
    val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val oriented = applyExifOrientation(file, raw)
    val ratio = oriented.width.toFloat() / oriented.height.toFloat().coerceAtLeast(1f)
    return DecodedImage(oriented.asImageBitmap(), ratio)
}

private fun applyExifOrientation(
    file: File,
    bitmap: Bitmap,
): Bitmap {
    val orientation =
        runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.postRotate(90f)
        }

        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.postRotate(180f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.postRotate(270f)
        }

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.postScale(1f, -1f)
        }

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }

        else -> {
            return bitmap
        }
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
            if (rotated !== bitmap) bitmap.recycle()
        }
    }.getOrDefault(bitmap)
}

/** 用系统查看器打开（PDF 等文件）；文件不在本机时只提示。 */
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
