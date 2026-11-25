package app.goke.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.goke.R
import app.goke.ui.components.ComingSoonScreen
import app.goke.ui.icons.LaptopMac

@Composable
fun LanWebScreen(onBack: () -> Unit) {
    ComingSoonScreen(
        title = stringResource(R.string.lan_web_title),
        icon = Icons.Rounded.LaptopMac,
        onBack = onBack,
    )
}
