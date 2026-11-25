package app.goke.notify

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.goke.GokeApp
import app.goke.R
import java.time.LocalTime
import kotlin.math.pow

/**
 * 持续提醒前台服务：循环播放现代合成提示音与渐进式波形震动，
 * 并结合耳机佩戴、夜间时段与前台状态智能调节渐强音量曲线，直到用户完成、稍后、关闭或超时。
 */
class ReminderAlertService : Service() {
    /** 渐强音量曲线参数。 */
    internal data class RampProfile(
        val startVolume: Float,
        val maxVolume: Float,
        val rampDurationMs: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutRunnable: Runnable? = null
    private var rampRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Notifications.ensureChannels(this)

        if (intent?.action == ACTION_STOP) {
            val targetId = intent.getIntExtra(ReminderReceiver.EXTRA_ID, 0)
            if (targetId == 0 || targetId == activeNotifId) {
                stopPlayback()
                activeNotifId = 0
                stopForegroundCompat(removeNotification = true)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val notifId = intent?.getIntExtra(ReminderReceiver.EXTRA_ID, 0)?.takeIf { it != 0 } ?: FALLBACK_NOTIF_ID
        val title = intent?.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_name) }
        val text = intent?.getStringExtra(ReminderReceiver.EXTRA_TEXT).orEmpty()
        val taskId = intent?.getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
        val courseId = intent?.getStringExtra(ReminderReceiver.EXTRA_COURSE_ID)
        val important = intent?.getBooleanExtra(ReminderReceiver.EXTRA_IMPORTANT, false) ?: false
        val channel = if (important) Notifications.CHANNEL_EXAM else Notifications.CHANNEL_REMINDER

        val notification =
            Notifications.buildReminderNotification(
                ctx = this,
                id = notifId,
                channel = channel,
                title = title,
                text = text,
                taskId = taskId,
                insistent = true,
                important = important,
                courseId = courseId,
                silentForService = true,
            )

        val fgsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        runCatching {
            ServiceCompat.startForeground(this, notifId, notification, fgsType)
        }

        stopPlayback()
        activeNotifId = notifId
        acquireWakeLock()
        startLoopingAlert(important)
        scheduleAutoStop()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        activeNotifId = 0
        stopForegroundCompat(removeNotification = true)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "goke:reminder_alert").apply {
                    setReferenceCounted(false)
                    acquire(AUTO_STOP_MS + 5_000L)
                }
        }
    }

    private fun startLoopingAlert(important: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        val am = getSystemService(AudioManager::class.java)
        val inTotalSilence = nm?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        if (inTotalSilence && !important) return

        val inCall = am?.mode == AudioManager.MODE_IN_CALL
        if (!inCall) {
            val profile =
                computeRampProfile(
                    important = important,
                    headphonesConnected = hasHeadphonesOutput(am),
                    quietHours = isQuietHours(LocalTime.now()),
                    inForeground = (applicationContext as? GokeApp)?.uiActive?.value == true,
                )
            startAlarmAudio(am, important, profile)
        }
        startLoopingVibration()
    }

    private fun startAlarmAudio(
        am: AudioManager?,
        important: Boolean,
        profile: RampProfile,
    ) {
        val attrs =
            AudioAttributes
                .Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

        if (am != null) {
            val req =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS) {
                            stopAudio()
                        }
                    }.build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        }

        val candidates =
            listOfNotNull(
                if (important) Notifications.urgentChimeUri(this) else Notifications.loopChimeUri(this),
                if (important) Notifications.loopChimeUri(this) else Notifications.normalChimeUri(this),
                Notifications.fallbackAlarmSound(),
            ).distinct()

        for (uri in candidates) {
            val player = MediaPlayer()
            val started =
                runCatching {
                    player.setAudioAttributes(attrs)
                    player.setDataSource(this, uri)
                    player.isLooping = true
                    player.setVolume(profile.startVolume, profile.startVolume)
                    player.setOnErrorListener { _, _, _ ->
                        stopAudio()
                        true
                    }
                    player.prepare()
                    player.start()
                    true
                }.getOrDefault(false)
            if (started) {
                mediaPlayer = player
                scheduleVolumeRamp(player, profile)
                return
            } else {
                runCatching { player.release() }
            }
        }
    }

    private fun scheduleVolumeRamp(
        player: MediaPlayer,
        profile: RampProfile,
    ) {
        if (profile.maxVolume <= profile.startVolume || profile.rampDurationMs <= 0L) return
        val startedAt = System.currentTimeMillis()
        val task =
            object : Runnable {
                override fun run() {
                    if (mediaPlayer !== player) return
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                    val progress = (elapsed.toFloat() / profile.rampDurationMs.toFloat()).coerceIn(0f, 1f)
                    // 平方缓入曲线：前几秒温和抬升，不吓人；之后快速到达目标音量
                    val eased = progress.pow(1.6f)
                    val vol = profile.startVolume + (profile.maxVolume - profile.startVolume) * eased
                    runCatching { player.setVolume(vol, vol) }
                    if (progress < 1f) {
                        mainHandler.postDelayed(this, RAMP_STEP_MS)
                    }
                }
            }
        rampRunnable = task
        mainHandler.postDelayed(task, RAMP_STEP_MS)
    }

    @Suppress("DEPRECATION")
    private fun startLoopingVibration() {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            } ?: return
        if (!vibrator.hasVibrator()) return

        // 前两下轻柔唤醒脉冲，随后进入稳定清晰的循环节奏
        val effect =
            if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, VIBRATION_REPEAT_INDEX)
            } else {
                VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_REPEAT_INDEX)
            }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs =
                    VibrationAttributes
                        .Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                vibrator.vibrate(effect, attrs)
            } else {
                val attrs =
                    AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                vibrator.vibrate(effect, attrs)
            }
        }
    }

    private fun scheduleAutoStop() {
        val task =
            Runnable {
                stopPlayback()
                activeNotifId = 0
                // 超时自动停响但保留通知卡片在通知栏，方便用户稍后看到错过的提醒
                stopForegroundCompat(removeNotification = false)
                stopSelf()
            }
        timeoutRunnable = task
        mainHandler.postDelayed(task, AUTO_STOP_MS)
    }

    private fun stopPlayback() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        rampRunnable?.let { mainHandler.removeCallbacks(it) }
        rampRunnable = null
        stopAudio()
        stopVibration()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun stopAudio() {
        rampRunnable?.let { mainHandler.removeCallbacks(it) }
        rampRunnable = null
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        mediaPlayer = null
        audioFocusRequest?.let { req ->
            runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(req) }
        }
        audioFocusRequest = null
    }

    @Suppress("DEPRECATION")
    private fun stopVibration() {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }
        runCatching { vibrator?.cancel() }
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        val flags =
            if (removeNotification) {
                ServiceCompat.STOP_FOREGROUND_REMOVE
            } else {
                ServiceCompat.STOP_FOREGROUND_DETACH
            }
        runCatching { ServiceCompat.stopForeground(this, flags) }
    }

    companion object {
        const val ACTION_START = "app.goke.notify.ACTION_ALERT_START"
        const val ACTION_STOP = "app.goke.notify.ACTION_ALERT_STOP"
        const val EXTRA_STOP_ALERT_ID = "stop_alert_id"
        private const val FALLBACK_NOTIF_ID = 9004
        private const val AUTO_STOP_MS = 180_000L
        private const val RAMP_STEP_MS = 400L
        private val VIBRATION_TIMINGS = longArrayOf(0L, 140L, 160L, 260L, 220L, 480L, 340L, 560L, 560L)
        private val VIBRATION_AMPLITUDES = intArrayOf(0, 90, 0, 165, 0, 230, 0, 255, 0)
        private const val VIBRATION_REPEAT_INDEX = 5

        @Volatile
        var activeNotifId: Int = 0
            private set

        internal fun isQuietHours(time: LocalTime): Boolean = time.hour >= 23 || time.hour < 7

        internal fun computeRampProfile(
            important: Boolean,
            headphonesConnected: Boolean,
            quietHours: Boolean,
            inForeground: Boolean,
        ): RampProfile =
            when {
                headphonesConnected -> {
                    RampProfile(
                        startVolume = 0.16f,
                        maxVolume = 0.52f,
                        rampDurationMs = if (important) 6_000L else 9_000L,
                    )
                }

                inForeground -> {
                    RampProfile(
                        startVolume = 0.20f,
                        maxVolume = 0.55f,
                        rampDurationMs = 6_000L,
                    )
                }

                quietHours -> {
                    RampProfile(
                        startVolume = 0.15f,
                        maxVolume = if (important) 1.0f else 0.92f,
                        rampDurationMs = 12_000L,
                    )
                }

                important -> {
                    RampProfile(
                        startVolume = 0.38f,
                        maxVolume = 1.0f,
                        rampDurationMs = 6_000L,
                    )
                }

                else -> {
                    RampProfile(
                        startVolume = 0.26f,
                        maxVolume = 1.0f,
                        rampDurationMs = 9_000L,
                    )
                }
            }

        private fun hasHeadphonesOutput(am: AudioManager?): Boolean {
            if (am == null) return false
            val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull() ?: return false
            return devices.any { dev ->
                when (dev.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    -> true

                    else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            }
        }

        fun start(
            ctx: Context,
            id: Int,
            title: String,
            text: String,
            taskId: String?,
            important: Boolean,
            courseId: String?,
        ): Boolean {
            val intent =
                Intent(ctx, ReminderAlertService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(ReminderReceiver.EXTRA_ID, id)
                    .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                    .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                    .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                    .putExtra(ReminderReceiver.EXTRA_IMPORTANT, important)
                    .putExtra(ReminderReceiver.EXTRA_COURSE_ID, courseId)
            return runCatching {
                ContextCompat.startForegroundService(ctx, intent)
            }.isSuccess
        }

        fun stop(
            ctx: Context,
            id: Int = 0,
        ) {
            if (id == 0 || activeNotifId == id) {
                activeNotifId = 0
                runCatching { ctx.stopService(Intent(ctx, ReminderAlertService::class.java)) }
            }
            if (id != 0) {
                runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(id) }
            }
        }
    }
}
