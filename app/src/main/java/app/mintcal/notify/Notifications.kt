package app.mintcal.notify

import android.Manifest
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
import app.mintcal.DeepLinks
import app.mintcal.MainActivity
import app.mintcal.R

/** 通知渠道与发送。 */
object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_PARCEL = "parcel"
    const val CHANNEL_QUICK = "quickadd"
    const val NOTIFY_QUICK_ID = 9002

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
                        .setData(Uri.parse("mintcal://notif/$id/$op"))
                        .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                        .putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, id)
                        .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                        .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                return NotificationCompat.Action.Builder(0, ctx.getString(label), pi).build()
            }
            n.addAction(action(TaskActionReceiver.ACTION_DONE, R.string.notif_action_done))
            n.addAction(action(TaskActionReceiver.ACTION_SNOOZE, R.string.notif_action_snooze))
        }
        val built = n.build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, built) }
    }
}
