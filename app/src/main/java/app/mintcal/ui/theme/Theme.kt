package app.mintcal.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.R
import app.mintcal.data.DarkMode
import app.mintcal.data.Settings
import app.mintcal.data.ThemeSource
import com.materialkolor.dynamicColorScheme

/** 预设主题：薄荷为品牌主题（手写 Token），其余由种子色生成。 */
enum class Preset(
    val key: String,
    val seed: Color,
    val label: Int,
) {
    MINT("mint", Color(0xFF4FA796), R.string.preset_mint),
    PEACH("peach", Color(0xFFE8836B), R.string.preset_peach),
    LAVENDER("lavender", Color(0xFF7A6CC4), R.string.preset_lavender),
    SKY("sky", Color(0xFF4A90D9), R.string.preset_sky),
    ROSE("rose", Color(0xFFD65C7A), R.string.preset_rose),
    LEMON("lemon", Color(0xFFC9A227), R.string.preset_lemon),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MINT
    }
}

@Immutable
class MintColors(
    val courseBg: List<Color>,
    val courseFg: List<Color>,
    val added: Color,
    val modified: Color,
    val onModified: Color,
    val removed: Color,
    val conflictBg: Color,
    val success: Color,
    val dark: Boolean,
) {
    fun bg(i: Int) = courseBg[((i % 8) + 8) % 8]

    fun fg(i: Int) = courseFg[((i % 8) + 8) % 8]
}

private val LightCourse =
    listOf(
        0xFFD9E8EF to 0xFF3B6173,
        0xFFBFDCD5 to 0xFF2C5B53,
        0xFFDAD8EE to 0xFF4F4D7C,
        0xFFF3EBCF to 0xFF75662F,
        0xFFF7DFD2 to 0xFF85503A,
        0xFFDFE8DA to 0xFF4B5F49,
        0xFFF4D9E0 to 0xFF83465A,
        0xFFD2D8EA to 0xFF454F7A,
    )
private val DarkCourse =
    listOf(
        0xFF2B4350 to 0xFFBFE0F0,
        0xFF24463F to 0xFFB2E3D8,
        0xFF38355A to 0xFFD3D0F5,
        0xFF4A4224 to 0xFFF0E5B6,
        0xFF503629 to 0xFFF5D2BF,
        0xFF33442F to 0xFFCFE5C8,
        0xFF56323E to 0xFFF4CBD7,
        0xFF303A5E to 0xFFC9D3F5,
    )

fun mintColors(dark: Boolean): MintColors {
    val c = if (dark) DarkCourse else LightCourse
    return MintColors(
        courseBg = c.map { Color(it.first) },
        courseFg = c.map { Color(it.second) },
        added = Color(0xFF46A171),
        modified = Color(0xFFD5A03B),
        onModified = if (dark) Color(0xFFF1CF8A) else Color(0xFF9A6B12),
        removed = Color(0xFFE56458),
        conflictBg = if (dark) Color(0xFF4A2A27) else Color(0xFFFCE9E7),
        success = Color(0xFF46A171),
        dark = dark,
    )
}

val LocalMintColors = compositionLocalOf { mintColors(false) }
val LocalReduceMotion = compositionLocalOf { false }

/** 无按压高亮：clickable/selectable/Surface(onClick) 的默认 indication 落到这里 = 不画任何东西。 */
private object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.Modifier.Node =
        object : androidx.compose.ui.Modifier.Node() {}

    override fun equals(other: Any?) = other === this

    override fun hashCode() = javaClass.hashCode()
}

object MintTheme {
    val colors: MintColors
        @Composable @ReadOnlyComposable
        get() = LocalMintColors.current

    /** 普通内容统一使用低层容器，主色留给选中态与操作。 */
    val card: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLow

    /** 弹簧动效（阻尼 0.7）；系统「移除动画」时直接切换。 */
    @Composable
    fun <T> spec(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

private fun brandLight() =
    lightColorScheme(
        primary = Color(0xFF4FA796),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDEBE3),
        onPrimaryContainer = Color(0xFF1B473F),
        secondary = Color(0xFFB4694B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFBE3D6),
        onSecondaryContainer = Color(0xFF7A4630),
        tertiary = Color(0xFF6C63B5),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE4E0F6),
        onTertiaryContainer = Color(0xFF3D3873),
        background = Color(0xFFF2F7F5),
        onBackground = Color(0xFF24312D),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF24312D),
        surfaceVariant = Color(0xFFE9F1EE),
        onSurfaceVariant = Color(0xFF7B8B86),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFF7FAF9),
        surfaceContainerHighest = Color(0xFFE9F1EE),
        outline = Color(0xFF9AABA6),
        outlineVariant = Color(0xFFE4ECE9),
        error = Color(0xFFE56458),
        onError = Color.White,
        errorContainer = Color(0xFFFCE9E7),
        onErrorContainer = Color(0xFF7A2A22),
    )

private fun brandDark() =
    darkColorScheme(
        primary = Color(0xFF7CCDBA),
        onPrimary = Color(0xFF0B2A24),
        primaryContainer = Color(0xFF24413B),
        onPrimaryContainer = Color(0xFFBFEDE2),
        secondary = Color(0xFFF2B79B),
        onSecondary = Color(0xFF4A2410),
        secondaryContainer = Color(0xFF4A3329),
        onSecondaryContainer = Color(0xFFF6D5C3),
        tertiary = Color(0xFFC9C3F5),
        onTertiary = Color(0xFF2F2C45),
        tertiaryContainer = Color(0xFF2F2C45),
        onTertiaryContainer = Color(0xFFE4E0F6),
        background = Color(0xFF141A19),
        onBackground = Color(0xFFE6EEEB),
        surface = Color(0xFF1E2624),
        onSurface = Color(0xFFE6EEEB),
        surfaceVariant = Color(0xFF2A3431),
        onSurfaceVariant = Color(0xFF93A39E),
        surfaceContainerLowest = Color(0xFF141A19),
        surfaceContainerLow = Color(0xFF1E2624),
        surfaceContainer = Color(0xFF1E2624),
        surfaceContainerHigh = Color(0xFF26302D),
        surfaceContainerHighest = Color(0xFF2A3431),
        outline = Color(0xFF6C7C77),
        outlineVariant = Color(0xFF2A3431),
        error = Color(0xFFFF8F84),
        onError = Color(0xFF3B0A06),
        errorContainer = Color(0xFF4A2A27),
        onErrorContainer = Color(0xFFFCE9E7),
    )

val MintShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private val MintTypography =
    Typography().let { t ->
        t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
            bodyLarge = t.bodyLarge.copy(letterSpacing = 0.sp),
            bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp),
            bodySmall = t.bodySmall.copy(letterSpacing = 0.sp),
            labelLarge = t.labelLarge.copy(letterSpacing = 0.sp),
            labelMedium = t.labelMedium.copy(letterSpacing = 0.sp),
            labelSmall = t.labelSmall.copy(letterSpacing = 0.sp),
        )
    }

/** 解析最终配色：动态取色（Android 12+）→ 预设 → 自定义种子色；动态取色不可用时回退薄荷。 */
@Composable
fun resolveColorScheme(
    settings: Settings,
    dark: Boolean,
): ColorScheme {
    val ctx = LocalContext.current
    return remember(settings.themeSource, settings.preset, settings.seedColor, dark) {
        when {
            settings.themeSource == ThemeSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }

            settings.themeSource == ThemeSource.CUSTOM -> {
                dynamicColorScheme(seedColor = Color(settings.seedColor), isDark = dark)
            }

            settings.themeSource == ThemeSource.PRESET && Preset.of(settings.preset) != Preset.MINT -> {
                dynamicColorScheme(seedColor = Preset.of(settings.preset).seed, isDark = dark)
            }

            else -> {
                if (dark) brandDark() else brandLight()
            }
        }
    }
}

@Composable
fun MintCalTheme(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val dark =
        when (settings.darkMode) {
            DarkMode.SYSTEM -> isSystemInDarkTheme()
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
        }
    // 每次回到前台时重读系统「移除动画」开关（ValueAnimator.areAnimatorsEnabled 覆盖缩放为 0 之外的情况）
    var reduce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        reduce = runCatching { !android.animation.ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
    }
    val scheme = resolveColorScheme(settings, dark)
    // 全应用关闭按压浅背景高亮（ripple/状态层里的可点底色）：点击反馈交给按下缩放、滑动、
    // 勾选动画等既有交互表达，不再闪底色。显式传 indication = ripple() 的调用点也已清理。
    CompositionLocalProvider(
        LocalMintColors provides mintColors(dark),
        LocalReduceMotion provides reduce,
        // 本项目 Compose 版本没有 Indication.None，这里给等价的无高亮实现
        LocalIndication provides NoIndication,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = MintShapes, typography = MintTypography) {
            // 提供默认内容色：没有 Surface 包裹时文字会是黑色，深色模式下看不见
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
