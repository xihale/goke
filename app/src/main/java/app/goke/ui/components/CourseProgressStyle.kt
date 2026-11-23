package app.goke.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.goke.core.time.TimePhase
import app.goke.ui.theme.MintTheme

/** 课程渐进置灰的推进方向：时间线卡片从左向右推进，课表纵向色块从上向下推进。 */
enum class CourseProgressAxis {
    HORIZONTAL,
    VERTICAL,
}

/**
 * 计算已过 / 已结束课程的退灰底色：向当前主题的 `surfaceContainerHighest` 混合，
 * 在浅色宣纸与深色松石夜色下都保持柔和去饱和，且零硬编码颜色。
 */
fun courseMutedBg(
    activeBg: Color,
    scheme: ColorScheme,
): Color = lerp(activeBg, scheme.surfaceContainerHighest, 0.78f)

/**
 * 计算课程正文前景色：已结束时平滑向 `onSurfaceVariant` 退灰（保持 ≥4.5:1 可读对比度），
 * 进行中与未开始保持原始植物深/浅前景色。
 */
@Composable
fun rememberCourseProgressFg(
    activeFg: Color,
    phase: TimePhase,
): Color {
    val scheme = MaterialTheme.colorScheme
    val target =
        if (phase == TimePhase.FINISHED) {
            lerp(activeFg, scheme.onSurfaceVariant, 0.76f)
        } else {
            activeFg
        }
    val animated by animateColorAsState(target, MintTheme.press(), label = "course-fg")
    return animated
}

/**
 * 课程进度置灰背景绘制器（需放在 `.clip(shape)` 之后）：
 * - [TimePhase.UPCOMING]：完整植物色底；
 * - [TimePhase.ONGOING]：已过部分平滑退灰、剩余部分保持植物色，交界处做极轻柔的色调过渡；
 * - [TimePhase.FINISHED]：整块退灰并轻降透明度，将视觉焦点让位给进行中与未开始事项。
 *
 * 动画值在 draw 阶段读取，避免逐帧重组子树；系统开启「移除动画」时自动走 `snap()`。
 */
@Composable
fun Modifier.courseProgressBackground(
    activeBg: Color,
    phase: TimePhase,
    progress: Float,
    axis: CourseProgressAxis,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val mutedBg = courseMutedBg(activeBg, scheme)
    val visualTarget =
        when (phase) {
            TimePhase.UPCOMING -> 0f
            TimePhase.FINISHED -> 1f
            TimePhase.ONGOING -> progress.coerceIn(0f, 1f)
        }
    val animatedProgress = animateFloatAsState(visualTarget, MintTheme.spec(), label = "course-progress")
    val finishedAlpha = animateFloatAsState(if (phase == TimePhase.FINISHED) 0.72f else 1f, MintTheme.press(), label = "course-finished-alpha")

    return drawBehind {
        val p = animatedProgress.value.coerceIn(0f, 1f)
        val alpha = finishedAlpha.value
        when {
            phase == TimePhase.UPCOMING && p <= 0.001f -> {
                drawRect(color = activeBg)
            }

            phase == TimePhase.FINISHED && p >= 0.999f -> {
                drawRect(color = mutedBg.copy(alpha = alpha))
            }

            axis == CourseProgressAxis.HORIZONTAL -> {
                val splitX = (size.width * p).coerceIn(0f, size.width)
                drawRect(color = activeBg)
                if (splitX > 0f) {
                    drawRect(
                        color = mutedBg,
                        topLeft = Offset.Zero,
                        size = Size(splitX, size.height),
                    )
                    val blendW = minOf(8.dp.toPx(), (size.width - splitX).coerceAtLeast(0f))
                    if (blendW > 0.5f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors = listOf(mutedBg, Color.Transparent),
                                    startX = splitX,
                                    endX = splitX + blendW,
                                ),
                            topLeft = Offset(splitX, 0f),
                            size = Size(blendW, size.height),
                        )
                    }
                }
            }

            else -> {
                val splitY = (size.height * p).coerceIn(0f, size.height)
                drawRect(color = activeBg)
                if (splitY > 0f) {
                    drawRect(
                        color = mutedBg,
                        topLeft = Offset.Zero,
                        size = Size(size.width, splitY),
                    )
                }
            }
        }
    }
}
