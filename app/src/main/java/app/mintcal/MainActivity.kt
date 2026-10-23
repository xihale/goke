package app.mintcal

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.mintcal.ui.MintCalRoot
import app.mintcal.ui.theme.MintCalTheme

class MainActivity : ComponentActivity() {
    /** 外部分享 / 打开的文件（ICS、xlsx），交给导入流程处理。 */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val app = mintApp
        setContent {
            val settings by app.settings.flow.collectAsState()
            MintCalTheme(settings) {
                MintCalRoot(app, incoming.value) { incoming.value = null }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        val uri: Uri? =
            when (intent.action) {
                android.content.Intent.ACTION_VIEW -> intent.data
                android.content.Intent.ACTION_SEND -> intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                else -> null
            }
        if (uri != null) incoming.value = uri
    }
}
