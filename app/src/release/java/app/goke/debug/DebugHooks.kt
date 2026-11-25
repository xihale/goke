package app.goke.debug

import android.content.Context
import androidx.compose.runtime.Composable

object DebugHooks {
    internal var suppressUiForShots: Boolean = false

    fun init(ctx: Context) = Unit

    @Composable
    fun DataScreenExtra() = Unit

    @Composable
    fun SettingsDebugSection() = Unit

    @Composable
    fun JxfwDebugBanner() = Unit

    @Composable
    fun CourseSelectionDebugBanner(onRefresh: () -> Unit) = Unit
}
