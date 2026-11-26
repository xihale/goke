package app.goke.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.goke.R
import app.goke.core.model.Attachment
import app.goke.data.Attachments
import app.goke.ui.Actions
import app.goke.ui.LocalActions
import app.goke.ui.icons.DeleteOutline
import app.goke.ui.icons.Download
import app.goke.ui.icons.IosShare
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * 全屏图片附件查看器：支持左右翻页、双指缩放/平移、双击放大、显隐控制栏，以及保存到相册、分享与删除。
 */
@Composable
internal fun ImageViewerDialog(
    images: List<Attachment>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (Attachment) -> Unit,
) {
    LaunchedEffect(images.isEmpty()) {
        if (images.isEmpty()) onDismiss()
    }
    if (images.isEmpty()) return

    val ctx = LocalContext.current
    val actions = LocalActions.current
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )
    var showChrome by remember { mutableStateOf(true) }
    val pageScales = remember { mutableStateMapOf<String, Float>() }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val currentImage = images.getOrNull(pagerState.currentPage) ?: images.first()
    val currentScale = pageScales[currentImage.file] ?: 1f

    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val dragDismissY = remember { Animatable(0f) }
    val predictiveProgress = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event ->
                    predictiveProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                onDismiss()
            } catch (e: CancellationException) {
                scope.launch {
                    if (predictiveProgress.value > 0f && !reduce) {
                        predictiveProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f))
                    } else {
                        predictiveProgress.snapTo(0f)
                    }
                }
                throw e
            }
        }
        val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 110.dp.toPx() }
        val maxPullScalePx = with(androidx.compose.ui.platform.LocalDensity.current) { 360.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dragFrac = (abs(dragDismissY.value) / maxPullScalePx).coerceIn(0f, 1f)
                    val pred = predictiveProgress.value.coerceIn(0f, 1f)
                    alpha = (1f - dragFrac * 0.65f) * (1f - pred * 0.45f)
                }.background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(currentScale <= 1.02f) {
                            if (currentScale > 1.02f) return@pointerInput
                            detectVerticalDragGestures(
                                onDragCancel = {
                                    scope.launch {
                                        if (reduce) dragDismissY.snapTo(0f) else dragDismissY.animateTo(0f, spring(0.88f, 750f))
                                    }
                                },
                                onDragEnd = {
                                    if (abs(dragDismissY.value) > dismissThresholdPx) {
                                        onDismiss()
                                    } else {
                                        scope.launch {
                                            if (reduce) dragDismissY.snapTo(0f) else dragDismissY.animateTo(0f, spring(0.88f, 750f))
                                        }
                                    }
                                },
                            ) { _, dragAmount ->
                                scope.launch { dragDismissY.snapTo(dragDismissY.value + dragAmount) }
                            }
                        }.graphicsLayer {
                            val dragFrac = (abs(dragDismissY.value) / maxPullScalePx).coerceIn(0f, 1f)
                            val pred = predictiveProgress.value.coerceIn(0f, 1f)
                            val sc = (1f - dragFrac * 0.18f) * (1f - pred * 0.10f)
                            scaleX = sc
                            scaleY = sc
                            translationY = dragDismissY.value
                        },
                userScrollEnabled = currentScale <= 1.01f && abs(dragDismissY.value) < 4f,
                key = { idx -> images[idx].file },
            ) { page ->
                val item = images[page]
                val file = remember(item.file) { Attachments.path(ctx, item) }
                ZoomableImagePage(
                    file = file,
                    name = item.name,
                    resetTrigger = if (page == pagerState.currentPage) resetTrigger else 0,
                    onScaleChanged = { s -> pageScales[item.file] = s },
                    onTap = { showChrome = !showChrome },
                )
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(MintTheme.spec()),
                exit = fadeOut(MintTheme.spec()),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                            ),
                        ).statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
                    }

                    Text(
                        text =
                            if (images.size > 1) {
                                stringResource(R.string.attachment_page_counter, pagerState.currentPage + 1, images.size)
                            } else {
                                currentImage.name
                            },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 72.dp),
                    )

                    if (currentScale > 1.05f) {
                        val resetZoomDesc = stringResource(R.string.attachment_zoom_reset)
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(999.dp),
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .mintClickable(
                                        pressedScale = 0.94f,
                                        role = Role.Button,
                                    ) {
                                        resetTrigger++
                                    }.semantics { contentDescription = resetZoomDesc },
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f×", currentScale),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(MintTheme.spec()),
                exit = fadeOut(MintTheme.spec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                ),
                            ).navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerActionItem(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.attachment_save_gallery),
                        enabled = !saving,
                    ) {
                        saving = true
                        actions.scope.launch {
                            val ok = Attachments.saveToGallery(ctx, currentImage)
                            saving = false
                            actions.toast(
                                ctx.getString(
                                    if (ok) R.string.attachment_saved_gallery else R.string.attachment_save_failed,
                                ),
                            )
                        }
                    }

                    ViewerActionItem(
                        icon = Icons.Rounded.IosShare,
                        label = stringResource(R.string.attachment_share),
                    ) {
                        shareImage(ctx, currentImage, actions)
                    }

                    ViewerActionItem(
                        icon = Icons.Rounded.DeleteOutline,
                        label = stringResource(R.string.delete),
                    ) {
                        val removingLast = images.size <= 1
                        onDelete(currentImage)
                        if (removingLast) onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    file: File,
    name: String,
    resetTrigger: Int,
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
) {
    val decoded by produceState<DecodedImage?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeImageWithMeta(file, 2560) }
    }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember(file) { Animatable(1f) }
    val offsetAnim = remember(file) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val onTapRef by rememberUpdatedState(onTap)
    val onScaleChangedRef by rememberUpdatedState(onScaleChanged)
    val scaleSpec by rememberUpdatedState(MintTheme.spec<Float>())
    val offsetSpec by rememberUpdatedState(MintTheme.spec<Offset>())

    LaunchedEffect(scaleAnim.value) {
        onScaleChangedRef(scaleAnim.value)
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            launch { scaleAnim.animateTo(1f, scaleSpec) }
            launch { offsetAnim.animateTo(Offset.Zero, offsetSpec) }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(file) {
                    detectTapGestures(
                        onTap = { onTapRef() },
                        onDoubleTap = { tap ->
                            scope.launch {
                                if (scaleAnim.value > 1.05f) {
                                    launch { scaleAnim.animateTo(1f, scaleSpec) }
                                    launch { offsetAnim.animateTo(Offset.Zero, offsetSpec) }
                                } else {
                                    val targetScale = 2.5f
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val rawOffset = (center - tap) * (targetScale - 1f)
                                    val maxX = size.width * (targetScale - 1f) / 2f
                                    val maxY = size.height * (targetScale - 1f) / 2f
                                    val clamped =
                                        Offset(
                                            rawOffset.x.coerceIn(-maxX, maxX),
                                            rawOffset.y.coerceIn(-maxY, maxY),
                                        )
                                    launch { scaleAnim.animateTo(targetScale, scaleSpec) }
                                    launch { offsetAnim.animateTo(clamped, offsetSpec) }
                                }
                            }
                        },
                    )
                }.pointerInput(file) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            val pressedCount = event.changes.count { it.pressed }
                            val currentScale = scaleAnim.value
                            if (!canceled && (pressedCount >= 2 || currentScale > 1.01f)) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                val zoomFactor = if (currentScale > 0f) newScale / currentScale else 1f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val maxX = size.width * (newScale - 1f) / 2f
                                val maxY = size.height * (newScale - 1f) / 2f
                                val nextOffset =
                                    if (newScale <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        val candidate =
                                            offsetAnim.value * zoomFactor +
                                                (center - centroid) * (zoomFactor - 1f) +
                                                panChange
                                        Offset(
                                            candidate.x.coerceIn(-maxX, maxX),
                                            candidate.y.coerceIn(-maxY, maxY),
                                        )
                                    }
                                scope.launch {
                                    scaleAnim.snapTo(newScale)
                                    offsetAnim.snapTo(nextOffset)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (scaleAnim.value <= 1.03f && (scaleAnim.value != 1f || offsetAnim.value != Offset.Zero)) {
                            scope.launch {
                                launch { scaleAnim.animateTo(1f, scaleSpec) }
                                launch { offsetAnim.animateTo(Offset.Zero, offsetSpec) }
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        decoded?.let { img ->
            Image(
                bitmap = img.bitmap,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                            translationX = offsetAnim.value.x
                            translationY = offsetAnim.value.y
                        },
            )
        }
    }
}

@Composable
private fun ViewerActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .mintClickable(
                    enabled = enabled,
                    pressedScale = 0.92f,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        )
    }
}

private fun shareImage(
    ctx: Context,
    a: Attachment,
    actions: Actions,
) {
    val file = Attachments.path(ctx, a)
    if (!file.exists()) {
        actions.toast(ctx.getString(R.string.attachment_missing))
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val mime = a.mime.takeIf { it.startsWith("image/") } ?: "image/*"
        val share =
            Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(share, a.name))
    }.onFailure {
        actions.toast(ctx.getString(R.string.attachment_failed))
    }
}
