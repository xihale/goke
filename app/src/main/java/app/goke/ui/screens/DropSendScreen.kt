package app.goke.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.goke.R
import app.goke.core.campus.DropRelayLogic
import app.goke.droprelay.DropSeedService
import app.goke.ui.LocalApp
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintScreen
import app.goke.ui.icons.IosShare
import app.goke.ui.icons.OpenInNew
import app.goke.ui.settingsState

/**
 * Phase 2 应用内快传受控 WebView 页：
 * - 加载 drop 站 `?client=goke`，自动接管系统分享传入的 `Uri[]` 并通过 `onShowFileChooser` 注入；
 * - 驻留期间启动 [DropSeedService] 前台保活通知，防止切后台中断 WebRTC 做种，离开页面自动停止。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DropSendScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val st = settingsState()
    val dropUrl = app.dropRelay.effectiveDropUrl(st.dropRelayDropUrl)
    val initialSharedUris = remember { app.dropRelay.consumePendingSendUris() }
    var pendingInjectUris by remember { mutableStateOf(initialSharedUris) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val cb = pendingFileCallback
            pendingFileCallback = null
            cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        }

    LaunchedEffect(Unit) {
        DropSeedService.stopEvents.collect {
            onBack()
        }
    }

    DisposableEffect(dropUrl) {
        if (dropUrl.isNotBlank()) {
            DropSeedService.start(ctx)
        }
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            DropSeedService.stop(ctx)
        }
    }

    MintScreen(
        title = stringResource(R.string.droprelay_send_title),
        onBack = onBack,
        actions = {
            if (dropUrl.isNotBlank()) {
                IconButton(onClick = { openExternalUrl(ctx, dropUrl) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.droprelay_send_open_browser),
                    )
                }
            }
        },
    ) {
        if (dropUrl.isBlank()) {
            EmptyState(
                title = stringResource(R.string.droprelay_unconfigured_title),
                hint = stringResource(R.string.droprelay_unconfigured_hint),
                icon = Icons.Rounded.IosShare,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            )
        } else {
            val targetUrl = remember(dropUrl) { DropRelayLogic.dropSendWebViewUrl(dropUrl) }
            val allowedHost = remember(dropUrl) { runCatching { Uri.parse(dropUrl).host }.getOrNull() }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.droprelay_send_keepalive_banner),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowContentAccess = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val uri = request?.url ?: return false
                                        if (allowedHost != null && uri.host.equals(allowedHost, ignoreCase = true)) {
                                            return false
                                        }
                                        openExternalUrl(context, uri.toString())
                                        return true
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        if (pendingInjectUris.isNotEmpty()) {
                                            // 自动触发页面文件选择输入，由 onShowFileChooser 注入系统分享传入的 Uri[]
                                            view?.evaluateJavascript(
                                                "(function(){var i=document.querySelector('input[type=file]');if(i){i.click();}})();",
                                                null,
                                            )
                                        }
                                    }
                                }
                            webChromeClient =
                                object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?,
                                    ): Boolean {
                                        if (filePathCallback == null) return false
                                        val shared = pendingInjectUris
                                        if (shared.isNotEmpty()) {
                                            pendingInjectUris = emptyList()
                                            filePathCallback.onReceiveValue(shared.toTypedArray())
                                            return true
                                        }
                                        pendingFileCallback?.onReceiveValue(null)
                                        pendingFileCallback = filePathCallback
                                        filePicker.launch(arrayOf("*/*"))
                                        return true
                                    }
                                }
                            loadUrl(targetUrl)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    },
                )
            }
        }
    }
}
