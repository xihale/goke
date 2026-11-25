package app.goke

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.goke.data.DarkMode
import app.goke.notify.ReminderAlertService
import app.goke.ui.GokeRoot
import app.goke.ui.theme.GokeTheme

class MainActivity : ComponentActivity() {
    /** 外部分享 / 打开的文件（ICS、xlsx），交给导入流程处理。 */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !gokeApp.repo.ready.value }
        handleIntent(intent)
        val app = gokeApp
        setContent {
            val settings by app.settings.flow.collectAsState()
            val ready by app.repo.ready.collectAsState()
            // 首帧真实内容（数据就绪 + 首屏组装完成）后上报，logcat 的「Fully drawn」即真实冷启动耗时
            LaunchedEffect(ready) {
                if (ready) reportFullyDrawn()
            }
            // 系统栏图标明暗跟随应用内深色模式设置（而非系统 uiMode），否则强制深色时图标不可见
            val dark =
                when (settings.darkMode) {
                    DarkMode.SYSTEM -> isSystemInDarkTheme()
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
            LaunchedEffect(dark) {
                val style =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GokeTheme(settings) {
                GokeRoot(app, incoming.value) { incoming.value = null }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 前台会话开始：网络同步与教务抓取由 uiActive 收集器触发（后台拉起的进程不碰网络）
        gokeApp.uiActive.value = true
    }

    override fun onStop() {
        super.onStop()
        gokeApp.uiActive.value = false
    }

    override fun onResume() {
        super.onResume()
        // MIUI/HyperOS 偶发:返回手势回桌面后热启动回来,进程存活、事件正常,但 surface 呈现旧空帧(整屏黑)。
        // 回前台时强制标脏走一次绘制,推新 buffer 上屏,自愈该竞态。
        window.decorView.post { window.decorView.invalidate() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        if (intent.hasExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID)) {
            ReminderAlertService.stop(this, intent.getIntExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID, 0))
        }
        DeepLinks.parse(intent)?.let {
            gokeApp.links.value = it
            return
        }
        val uri: Uri? =
            when (intent.action) {
                android.content.Intent.ACTION_VIEW -> intent.data
                android.content.Intent.ACTION_SEND -> intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                else -> null
            }
        if (uri != null) incoming.value = uri
    }
}
