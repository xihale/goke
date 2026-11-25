package app.goke.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.goke.R
import app.goke.ui.components.ComingSoonScreen
import app.goke.ui.icons.Terminal

@Composable
fun McpScreen(onBack: () -> Unit) {
    ComingSoonScreen(
        title = stringResource(R.string.mcp_title),
        icon = Icons.Rounded.Terminal,
        onBack = onBack,
    )
}
