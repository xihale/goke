package app.goke.debug

import android.content.Context
import androidx.compose.runtime.Composable
import app.goke.jxfw.JwcwxClient
import app.goke.jxfw.JxfwClient
import app.goke.jxfw.TzcsClient

object DebugHooks {
    fun init(ctx: Context) = Unit

    fun newJxfwClient(): JxfwClient = JxfwClient()

    fun newJwcwxClient(): JwcwxClient = JwcwxClient()

    fun newTzcsClient(): TzcsClient = TzcsClient()

    @Composable
    fun DataScreenExtra() = Unit

    @Composable
    fun SettingsDebugSection() = Unit

    @Composable
    fun JxfwDebugBanner() = Unit

    @Composable
    fun CourseSelectionDebugBanner(onRefresh: () -> Unit) = Unit
}
