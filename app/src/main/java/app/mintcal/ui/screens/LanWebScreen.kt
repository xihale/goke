package app.mintcal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mintcal.R
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SwitchRow
import app.mintcal.ui.components.Tag
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.delay

@Composable
fun LanWebScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val state by app.lanWeb.state.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        app.lanWeb.refreshNetwork()
    }

    LaunchedEffect(state.running, state.expiresAtMs) {
        if (state.running && state.expiresAtMs != null) {
            nowMs = System.currentTimeMillis()
            while (true) {
                delay(1_000L)
                nowMs = System.currentTimeMillis()
            }
        }
    }

    fun copyText(
        label: String,
        text: String,
        toastRes: Int,
    ) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        actions.toast(ctx.getString(toastRes))
    }

    val remainingMs = ((state.expiresAtMs ?: 0L) - nowMs).coerceAtLeast(0L)
    val totalSec = (remainingMs / 1000L).toInt()
    val remMin = totalSec / 60
    val remSec = totalSec % 60

    val switchSubtitle =
        if (state.running && state.expiresAtMs != null) {
            stringResource(R.string.lan_web_idle_countdown, remMin, remSec)
        } else {
            stringResource(R.string.lan_web_idle_hint)
        }

    MintScreen(title = stringResource(R.string.lan_web_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MintCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.lan_web_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Tag(
                        text = stringResource(if (state.running) R.string.lan_web_status_on else R.string.lan_web_status_off),
                        bg =
                            if (state.running) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        fg =
                            if (state.running) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.lan_web_enable),
                    subtitle = switchSubtitle,
                    checked = state.running,
                    onChange = { enabled ->
                        if (enabled) app.lanWeb.start() else app.lanWeb.stop()
                    },
                )
            }

            AnimatedVisibility(
                visible = state.lastError != null,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                state.lastError?.let { error ->
                    MintCard(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.running,
                enter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec()),
                exit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec()),
            ) {
                MintCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.lan_web_section_connection),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.lan_web_url_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val lanUrl = state.url
                            if (lanUrl != null) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        lanUrl,
                                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    FilledTonalButton(
                                        onClick = { copyText("LAN Web URL", lanUrl, R.string.lan_web_copied_url) },
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.lan_web_copy_url), maxLines = 1)
                                    }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.lan_web_lan_ip_unavailable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.lan_web_pin_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val formattedPin =
                                if (state.pin.length == 6) {
                                    "${state.pin.take(3)} ${state.pin.drop(3)}"
                                } else {
                                    state.pin
                                }
                            Text(
                                formattedPin,
                                style =
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { copyText("LAN Web PIN", state.pin, R.string.lan_web_copied_pin) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.lan_web_copy_pin), maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        app.lanWeb.regeneratePin()
                                        actions.toast(ctx.getString(R.string.lan_web_pin_regenerated))
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.lan_web_regen_pin), maxLines = 1)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp).size(16.dp),
                            )
                            Text(
                                stringResource(R.string.lan_web_http_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.lan_web_ap_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.lan_web_ap_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.lan_web_tip_router),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.lan_web_tip_hotspot),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.lan_web_tip_adb, state.adbUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        state.adbCommand,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Medium,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { copyText("ADB Command", state.adbCommand, R.string.lan_web_copied_adb) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.lan_web_copy_adb), maxLines = 1)
                        }
                        TextButton(
                            onClick = { copyText("ADB URL", state.adbUrl, R.string.lan_web_copied_url) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.lan_web_copy_adb_url), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
