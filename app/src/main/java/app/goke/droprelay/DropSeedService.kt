package app.goke.droprelay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.goke.MainActivity
import app.goke.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * DropRelay Phase 2 应用内快传 WebView 做种前台保活服务：
 * WebRTC 做种期间保持常驻通知（「正在做种，保持运行」），防止切后台被系统冻结中断传输，并提供一键停止。
 */
class DropSeedService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            _stopEvents.tryEmit(Unit)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val openIntent =
            PendingIntent.getActivity(
                this,
                NOTIFY_ID,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                NOTIFY_ID + 1,
                Intent(this, DropSeedService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.droprelay_seed_notif_title))
                .setContentText(getString(R.string.droprelay_seed_notif_text))
                .setContentIntent(openIntent)
                .addAction(0, getString(R.string.droprelay_seed_notif_stop), stopIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        val fgsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFY_ID, notification, fgsType)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.droprelay_seed_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    companion object {
        const val CHANNEL_ID = "droprelay_seed"
        const val NOTIFY_ID = 9006
        const val ACTION_START = "app.goke.droprelay.ACTION_START"
        const val ACTION_STOP = "app.goke.droprelay.ACTION_STOP"

        private val _stopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopEvents: SharedFlow<Unit> = _stopEvents.asSharedFlow()

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DropSeedService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DropSeedService::class.java))
            }
        }
    }
}
