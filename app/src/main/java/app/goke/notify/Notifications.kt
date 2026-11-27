package app.goke.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.goke.DeepLinks
import app.goke.MainActivity
import app.goke.R
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.widget.WidgetOverlayActivity

/** 通知渠道与发送。 */
object Notifications {
    const val CHANNEL_REMINDER = "reminder_v3"
    const val CHANNEL_DIGEST = "digest_reminder"
    const val CHANNEL_EXAM = "exam_reminder_v3"
    private val LEGACY_CHANNELS = listOf("reminder", "reminder_v2", "exam_reminder", "exam_reminder_v2")
    const val CHANNEL_PARCEL = "parcel"
    const val CHANNEL_HOT_WATER = "hot_water"
    const val CHANNEL_QUICK = "quickadd"
    const val CHANNEL_LAN_WEB = "lan_web"
    const val NOTIFY_QUICK_ID = 9002
    const val NOTIFY_LAN_WEB_ID = 9003
    private const val BURST_COOLDOWN_MS = 3_000L

    @Volatile
    private var lastOrdinaryAlertAtMs: Long = 0L

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // 清理旧渠道，确保升级用户也能切换至新版现代提示音配置
        runCatching {
            for (legacyId in LEGACY_CHANNELS) {
                nm.deleteNotificationChannel(legacyId)
            }
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, ctx.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_reminder_desc)
                setSound(normalChimeUri(ctx), notifAudioAttributes())
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 140L)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, ctx.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_digest_desc)
                setSound(digestChimeUri(ctx), notifAudioAttributes())
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 80L)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EXAM, ctx.getString(R.string.channel_exam), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_exam_desc)
                setSound(urgentChimeUri(ctx), alarmAudioAttributes())
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 500L, 250L, 500L)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PARCEL, ctx.getString(R.string.channel_parcel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HOT_WATER, ctx.getString(R.string.channel_hot_water), NotificationManager.IMPORTANCE_DEFAULT),
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

    fun formatHotWaterDuration(
        ctx: Context,
        durationSec: Long,
    ): String =
        when {
            durationSec < 60L -> ctx.getString(R.string.hotwater_duration_sec, durationSec.coerceAtLeast(1L))
            durationSec % 60L == 0L -> ctx.getString(R.string.hotwater_duration_min, durationSec / 60L)
            else -> ctx.getString(R.string.hotwater_duration_min_sec, durationSec / 60L, durationSec % 60L)
        }

    /** 热水关水结算后发送本次洗浴消费、出水量与时长通知，点击直达智慧热水页。 */
    fun postHotWaterSummary(
        ctx: Context,
        record: HotWaterShowerRecord,
    ) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val notifId = ("hotwater:${record.consumeId}:${record.startTime}").hashCode()
        val open =
            PendingIntent.getActivity(
                ctx,
                notifId,
                Intent(Intent.ACTION_VIEW, DeepLinks.hotWaterUri(), ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val durationText = formatHotWaterDuration(ctx, record.durationSec)
        val text = ctx.getString(R.string.hotwater_summary_notif_text, record.costYuan, record.liters, durationText)
        val n =
            NotificationCompat
                .Builder(ctx, CHANNEL_HOT_WATER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.hotwater_summary_notif_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        runCatching { NotificationManagerCompat.from(ctx).notify(notifId, n.build()) }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun buildReminderNotification(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        taskId: String? = null,
        insistent: Boolean = false,
        important: Boolean = false,
        courseId: String? = null,
        courseDate: String? = null,
        courseStartPeriod: Int? = null,
        courseEndPeriod: Int? = null,
        silentForService: Boolean = false,
        targetAtMs: Long? = null,
    ): Notification {
        ensureChannels(ctx)
        val openIntent =
            when {
                taskId != null -> {
                    WidgetOverlayActivity
                        .reminderTaskIntent(
                            ctx = ctx,
                            taskId = taskId,
                            notifId = id,
                            title = title,
                            text = text,
                            persistent = insistent,
                            important = important,
                            targetAtMs = targetAtMs,
                        ).setData(Uri.parse("goke://notif/$id/open"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                courseId != null -> {
                    WidgetOverlayActivity
                        .courseIntent(
                            ctx = ctx,
                            courseId = courseId,
                            dateStr = courseDate,
                            startPeriod = courseStartPeriod,
                            endPeriod = courseEndPeriod,
                        ).setData(Uri.parse("goke://notif/$id/open"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                else -> {
                    Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }.putExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID, id)
        val open =
            PendingIntent.getActivity(
                ctx,
                id,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun actionIntent(op: String): Intent =
            Intent(ctx, TaskActionReceiver::class.java)
                .setAction(op)
                .setData(Uri.parse("goke://notif/$id/$op"))
                .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, insistent)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, important)
                .apply {
                    if (targetAtMs != null && targetAtMs > 0L) {
                        putExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, targetAtMs)
                    }
                }

        fun actionPi(op: String): PendingIntent = PendingIntent.getBroadcast(ctx, id, actionIntent(op), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val dismissPi = actionPi(TaskActionReceiver.ACTION_DISMISS)
        val n =
            NotificationCompat
                .Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setDeleteIntent(dismissPi)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(if (important || insistent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (important || insistent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .apply {
                    if (silentForService) {
                        // 持续提醒服务或并发退避静默时避免重叠提示音
                        setSilent(true)
                    } else {
                        setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
                        setSound(soundForChannel(ctx, channel, important))
                    }
                }
        if (taskId != null) {
            n.addAction(NotificationCompat.Action.Builder(0, ctx.getString(R.string.notif_action_done), actionPi(TaskActionReceiver.ACTION_DONE)).build())
            n.addAction(NotificationCompat.Action.Builder(0, ctx.getString(R.string.notif_action_snooze), actionPi(TaskActionReceiver.ACTION_SNOOZE)).build())
        }
        if (insistent) {
            n.addAction(NotificationCompat.Action.Builder(0, ctx.getString(R.string.notif_action_stop), dismissPi).build())
        }
        val built = n.build()
        if (insistent && !silentForService) built.flags = built.flags or Notification.FLAG_INSISTENT
        return built
    }

    fun post(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        taskId: String? = null,
        insistent: Boolean = false,
        important: Boolean = false,
        courseId: String? = null,
        courseDate: String? = null,
        courseStartPeriod: Int? = null,
        courseEndPeriod: Int? = null,
        targetAtMs: Long? = null,
    ) {
        if (!canPost(ctx)) return
        val now = System.currentTimeMillis()
        // 同一时刻多个普通提醒齐发时，仅首条发声，后续 3 秒内静音弹出，避免提示音碰撞
        val throttled = !insistent && !important && (now - lastOrdinaryAlertAtMs) in 0 until BURST_COOLDOWN_MS
        if (!insistent && !important && !throttled) {
            lastOrdinaryAlertAtMs = now
        }
        val built =
            buildReminderNotification(
                ctx = ctx,
                id = id,
                channel = channel,
                title = title,
                text = text,
                taskId = taskId,
                insistent = insistent,
                important = important,
                courseId = courseId,
                courseDate = courseDate,
                courseStartPeriod = courseStartPeriod,
                courseEndPeriod = courseEndPeriod,
                silentForService = throttled,
                targetAtMs = targetAtMs,
            )
        runCatching { NotificationManagerCompat.from(ctx).notify(id, built) }
        if (!insistent && !throttled) {
            playFallbackSoundIfChannelSilenced(ctx, channel, important)
        }
    }

    internal fun resetAlertCooldownForTest() {
        lastOrdinaryAlertAtMs = 0L
    }

    private fun playFallbackSoundIfChannelSilenced(
        ctx: Context,
        channel: String,
        important: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val ch = nm.getNotificationChannel(channel) ?: return
        if (ch.importance == NotificationManager.IMPORTANCE_NONE) return
        if (ch.sound != null && ch.importance >= NotificationManager.IMPORTANCE_DEFAULT) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val dndBlocked = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && !ch.canBypassDnd()
        if (dndBlocked) return
        val uri = soundForChannel(ctx, channel, important)
        runCatching {
            RingtoneManager.getRingtone(ctx, uri)?.apply {
                audioAttributes = if (important) alarmAudioAttributes() else notifAudioAttributes()
                play()
            }
        }
    }

    internal fun rawUri(
        ctx: Context,
        resId: Int,
    ): Uri = Uri.parse("android.resource://${ctx.packageName}/$resId")

    internal fun normalChimeUri(ctx: Context): Uri = rawUri(ctx, R.raw.goke_chime_normal)

    internal fun digestChimeUri(ctx: Context): Uri = rawUri(ctx, R.raw.goke_chime_digest)

    internal fun urgentChimeUri(ctx: Context): Uri = rawUri(ctx, R.raw.goke_chime_urgent)

    internal fun loopChimeUri(ctx: Context): Uri = rawUri(ctx, R.raw.goke_chime_loop)

    private fun soundForChannel(
        ctx: Context,
        channel: String,
        important: Boolean,
    ): Uri =
        when {
            important || channel == CHANNEL_EXAM -> urgentChimeUri(ctx)
            channel == CHANNEL_DIGEST -> digestChimeUri(ctx)
            else -> normalChimeUri(ctx)
        }

    internal fun fallbackAlarmSound(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun notifAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
}
