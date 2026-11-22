package app.goke.lanweb

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.goke.GokeApp
import app.goke.notify.Notifications

/**
 * 局域网电脑端管理的前台保活服务：展示访问地址、连接码与「停止服务」按钮，
 * 避免切后台或息屏时被系统迅速冻结，在停止共享或 20 分钟无操作后自动结束。
 */
class LanWebService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Notifications.ensureChannels(this)
        val app = applicationContext as? GokeApp
        val state = app?.lanWeb?.state?.value ?: LanWebUiState()

        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            app?.lanWeb?.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val url = state.url ?: state.adbUrl
        val notification =
            Notifications.buildLanWebNotification(
                ctx = this,
                url = url,
                pin = state.pin,
                stopIntent = stopPendingIntent(this),
            )
        val fgsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.NOTIFY_LAN_WEB_ID,
                notification,
                fgsType,
            )
        }

        if (!state.running) {
            stopForegroundCompat()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        Notifications.cancelLanWebNotification(this)
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    companion object {
        const val ACTION_START = "app.goke.lanweb.ACTION_START"
        const val ACTION_STOP = "app.goke.lanweb.ACTION_STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LanWebService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, LanWebService::class.java))
            }
        }

        fun stopPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, LanWebService::class.java).setAction(ACTION_STOP)
            return PendingIntent.getService(
                context,
                Notifications.NOTIFY_LAN_WEB_ID + 1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
