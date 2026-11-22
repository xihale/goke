package app.goke.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.goke.DeepLinks
import app.goke.MainActivity
import app.goke.R

/** 通知渠道与发送。 */
object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_PARCEL = "parcel"
    const val CHANNEL_QUICK = "quickadd"
    const val CHANNEL_LAN_WEB = "lan_web"
    const val NOTIFY_QUICK_ID = 9002
    const val NOTIFY_LAN_WEB_ID = 9003

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, ctx.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PARCEL, ctx.getString(R.string.channel_parcel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUICK, ctx.getString(R.string.channel_quick), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LAN_WEB, ctx.getString(R.string.channel_lan_web), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    /** 常驻通知栏的「快速添加待办」入口：点按进新建页。 */
    fun postQuickAdd(ctx: Context) {
        if (!canPost(ctx)) return
        val open =
            PendingIntent.getActivity(
                ctx,
                NOTIFY_QUICK_ID,
                Intent(Intent.ACTION_VIEW, DeepLinks.addUri(), ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val n =
            NotificationCompat
                .Builder(ctx, CHANNEL_QUICK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.quick_notif_title))
                .setContentText(ctx.getString(R.string.quick_notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFY_QUICK_ID, n.build()) }
    }

    fun cancelQuickAdd(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIFY_QUICK_ID)
    }

    /** 局域网电脑端管理运行时的常驻前台通知，展示访问地址、连接码与「停止」按钮。 */
    fun buildLanWebNotification(
        ctx: Context,
        url: String,
        pin: String,
        stopIntent: PendingIntent,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                ctx,
                NOTIFY_LAN_WEB_ID,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text = ctx.getString(R.string.lan_web_notif_text, url, pin)
        return NotificationCompat
            .Builder(ctx, CHANNEL_LAN_WEB)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.lan_web_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, ctx.getString(R.string.lan_web_notif_stop), stopIntent)
            .build()
    }

    fun updateLanWebNotification(
        ctx: Context,
        url: String,
        pin: String,
        stopIntent: PendingIntent,
    ) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        runCatching {
            NotificationManagerCompat.from(ctx).notify(
                NOTIFY_LAN_WEB_ID,
                buildLanWebNotification(ctx, url, pin, stopIntent),
            )
        }
    }

    fun cancelLanWebNotification(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFY_LAN_WEB_ID) }
    }

    /** 识别到取件通知时在快递渠道发一条静默可划掉的确认通知，点击直达对应待办。 */
    fun postParcelCaptured(
        ctx: Context,
        taskId: String,
        code: String,
        placeOrCompany: String,
    ) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val notifId = ("parcel:$taskId").hashCode()
        val open =
            PendingIntent.getActivity(
                ctx,
                notifId,
                Intent(Intent.ACTION_VIEW, DeepLinks.taskUri(taskId), ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val detail = placeOrCompany.ifBlank { ctx.getString(R.string.parcel_title) }
        val n =
            NotificationCompat
                .Builder(ctx, CHANNEL_PARCEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.parcel_notif_captured_title))
                .setContentText(ctx.getString(R.string.parcel_notif_captured_text, code, detail))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        runCatching { NotificationManagerCompat.from(ctx).notify(notifId, n.build()) }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        taskId: String? = null,
        insistent: Boolean = false,
    ) {
        if (!canPost(ctx)) return
        val open =
            PendingIntent.getActivity(
                ctx,
                id,
                (
                    if (taskId != null) {
                        Intent(Intent.ACTION_VIEW, DeepLinks.taskUri(taskId), ctx, MainActivity::class.java)
                    } else {
                        Intent(ctx, MainActivity::class.java)
                    }
                ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val n =
            NotificationCompat
                .Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (taskId != null) {
            fun action(
                op: String,
                label: Int,
            ): NotificationCompat.Action {
                val i =
                    Intent(ctx, TaskActionReceiver::class.java)
                        .setAction(op)
                        .setData(Uri.parse("goke://notif/$id/$op"))
                        .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                        .putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, id)
                        .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                        .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                        .putExtra(ReminderReceiver.EXTRA_PERSISTENT, insistent)
                val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                return NotificationCompat.Action.Builder(0, ctx.getString(label), pi).build()
            }
            n.addAction(action(TaskActionReceiver.ACTION_DONE, R.string.notif_action_done))
            n.addAction(action(TaskActionReceiver.ACTION_SNOOZE, R.string.notif_action_snooze))
        }
        val built = n.build()
        if (insistent) built.flags = built.flags or Notification.FLAG_INSISTENT
        runCatching { NotificationManagerCompat.from(ctx).notify(id, built) }
    }
}
