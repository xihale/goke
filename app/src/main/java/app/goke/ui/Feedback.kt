package app.goke.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.goke.R
import app.goke.core.agenda.Agenda
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.TaskItem
import app.goke.core.model.Snapshot
import app.goke.core.todo.SmartList
import app.goke.core.todo.TaskLogic
import app.goke.data.Settings
import java.time.LocalDateTime

/** 交互反馈语义类型：统一驱动触觉与轻音效，激励用户完成任务与日常操作。 */
enum class FeedbackEvent {
    /** 单条任务完成：清脆双拍震动 + 上扬二音木琴声。 */
    TASK_COMPLETE,

    /** 今日待办全部清空 / 批量完成：三连渐强震动 + 明快四音琶音庆祝。 */
    CELEBRATE,

    /** 新建 / 保存 / 同步成功：利落轻震 + 柔和木琴落音。 */
    SUCCESS,

    /** 撤销 / 还原：轻触反馈 + 下行柔音。 */
    UNDO,

    /** 轻操作确认（取消勾选、复制取件码、批量改属性）：极轻触觉，不出声。 */
    SOFT_TICK,

    /** 删除等破坏性操作：低沉短震，不播愉悦音效。 */
    DELETE,
}

/**
 * 统一触觉与轻音效引擎。
 * - 音效走 `SoundPool`（`USAGE_ASSISTANCE_SONIFICATION`），低延迟且不抢占媒体焦点，仅在普通响铃模式发声；
 * - 触觉在普通与震动模式生效，静音/考试勿扰模式自动静默；
 * - 所有硬件调用均做容错保护，在 Robolectric / 无马达设备上安全降级。
 */
class FeedbackEngine(
    context: Context,
    private val settings: () -> Settings,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<Int, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private var pendingPlaySoundId: Int = 0
    private var pendingPlayVolume: Float = 0.6f

    fun play(event: FeedbackEvent) {
        val st = settings()
        val ringerMode = currentRingerMode()
        if (shouldVibrate(st.hapticFeedback, ringerMode)) {
            vibrate(event)
        }
        if (shouldPlaySound(st.soundFeedback, ringerMode)) {
            playSound(event)
        }
    }

    /** 设置页开启触觉开关时的即时试震。 */
    fun previewHaptic() {
        if (shouldVibrate(enabled = true, ringerMode = currentRingerMode())) {
            vibrate(FeedbackEvent.TASK_COMPLETE)
        }
    }

    /** 设置页开启音效开关时的即时试听。 */
    fun previewSound() {
        if (shouldPlaySound(enabled = true, ringerMode = currentRingerMode())) {
            playSound(FeedbackEvent.TASK_COMPLETE)
        }
    }

    private fun currentRingerMode(): Int =
        runCatching {
            appContext.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)

    private fun playSound(event: FeedbackEvent) {
        val (resId, volume) = soundFor(event) ?: return
        runCatching {
            synchronized(lock) {
                val sp = ensurePoolLocked()
                val sid =
                    soundIds.getOrPut(resId) {
                        sp.load(appContext, resId, 1)
                    }
                if (sid == 0) return@synchronized
                if (sid in loadedIds) {
                    sp.play(sid, volume, volume, 1, 0, 1.0f)
                } else {
                    pendingPlaySoundId = sid
                    pendingPlayVolume = volume
                }
            }
        }
    }

    private fun ensurePoolLocked(): SoundPool {
        pool?.let { return it }
        val attrs =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val sp =
            SoundPool
                .Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build()
        sp.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            var volToPlay: Float? = null
            synchronized(lock) {
                loadedIds.add(sampleId)
                if (pendingPlaySoundId == sampleId) {
                    volToPlay = pendingPlayVolume
                    pendingPlaySoundId = 0
                }
            }
            volToPlay?.let { v ->
                runCatching { soundPool.play(sampleId, v, v, 1, 0, 1.0f) }
            }
        }
        pool = sp
        // 顺带预加载全部 4 个轻量音效（总计 <20KB），后续触发零延迟
        listOf(
            R.raw.goke_sfx_complete,
            R.raw.goke_sfx_celebrate,
            R.raw.goke_sfx_pop,
            R.raw.goke_sfx_undo,
        ).forEach { res ->
            soundIds.getOrPut(res) { sp.load(appContext, res, 1) }
        }
        return sp
    }

    private fun vibrate(event: FeedbackEvent) {
        runCatching {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext.getSystemService(Vibrator::class.java)
                } ?: return
            if (!vibrator.hasVibrator()) return
            val effect = buildVibrationEffect(vibrator, event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs =
                    VibrationAttributes
                        .Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build()
                vibrator.vibrate(effect, attrs)
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    private fun buildVibrationEffect(
        vibrator: Vibrator,
        event: FeedbackEvent,
    ): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val composed = composePrimitiveEffect(vibrator, event)
            if (composed != null) return composed
        }
        val (timings, amplitudes) = waveformFor(event)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun composePrimitiveEffect(
        vibrator: Vibrator,
        event: FeedbackEvent,
    ): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (event) {
            FeedbackEvent.TASK_COMPLETE -> {
                if (vibrator.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                    )
                ) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f, 0)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.55f, 55)
                        .compose()
                } else {
                    null
                }
            }

            FeedbackEvent.CELEBRATE -> {
                if (vibrator.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    )
                ) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.55f, 0)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 60)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.95f, 75)
                        .compose()
                } else {
                    null
                }
            }

            FeedbackEvent.SUCCESS -> {
                if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f, 0)
                        .compose()
                } else {
                    null
                }
            }

            FeedbackEvent.UNDO -> {
                if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0)
                        .compose()
                } else {
                    null
                }
            }

            FeedbackEvent.SOFT_TICK -> {
                if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 0)
                        .compose()
                } else {
                    null
                }
            }

            FeedbackEvent.DELETE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                ) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f, 0)
                        .compose()
                } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f, 0)
                        .compose()
                } else {
                    null
                }
            }
        }
    }

    companion object {
        internal fun shouldVibrate(
            enabled: Boolean,
            ringerMode: Int,
        ): Boolean = enabled && ringerMode != AudioManager.RINGER_MODE_SILENT

        internal fun shouldPlaySound(
            enabled: Boolean,
            ringerMode: Int,
        ): Boolean = enabled && ringerMode == AudioManager.RINGER_MODE_NORMAL

        internal fun soundFor(event: FeedbackEvent): Pair<Int, Float>? =
            when (event) {
                FeedbackEvent.TASK_COMPLETE -> R.raw.goke_sfx_complete to 0.68f
                FeedbackEvent.CELEBRATE -> R.raw.goke_sfx_celebrate to 0.76f
                FeedbackEvent.SUCCESS -> R.raw.goke_sfx_pop to 0.55f
                FeedbackEvent.UNDO -> R.raw.goke_sfx_undo to 0.52f
                FeedbackEvent.SOFT_TICK, FeedbackEvent.DELETE -> null
            }

        internal fun waveformFor(event: FeedbackEvent): Pair<LongArray, IntArray> =
            when (event) {
                FeedbackEvent.TASK_COMPLETE -> longArrayOf(0, 18, 45, 28) to intArrayOf(0, 140, 0, 220)
                FeedbackEvent.CELEBRATE -> longArrayOf(0, 18, 50, 24, 55, 38) to intArrayOf(0, 120, 0, 180, 0, 255)
                FeedbackEvent.SUCCESS -> longArrayOf(0, 16) to intArrayOf(0, 160)
                FeedbackEvent.UNDO -> longArrayOf(0, 12) to intArrayOf(0, 110)
                FeedbackEvent.SOFT_TICK -> longArrayOf(0, 10) to intArrayOf(0, 90)
                FeedbackEvent.DELETE -> longArrayOf(0, 22) to intArrayOf(0, 170)
            }
    }
}

/**
 * 判断完成 [completingIds] 后是否刚好清空今日全部待办（含今日时间线任务、时间块与已逾期未完成任务）。
 * 用于将普通完成反馈升级为庆祝反馈，激励用户。
 */
internal fun isClearingTodayTasks(
    snap: Snapshot,
    completingIds: Set<String>,
    now: LocalDateTime,
): Boolean {
    if (completingIds.isEmpty()) return false
    val today = now.toLocalDate()
    val pendingTodayIds = mutableSetOf<String>()
    Agenda.forDay(snap, today).forEach { item ->
        when {
            item is TaskItem && !item.task.done -> pendingTodayIds.add(item.task.id)
            item is BlockItem && !item.task.done -> pendingTodayIds.add(item.task.id)
        }
    }
    TaskLogic.filter(snap.tasks, SmartList.OVERDUE, now).forEach { task ->
        if (!task.done) pendingTodayIds.add(task.id)
    }
    return pendingTodayIds.isNotEmpty() && completingIds.containsAll(pendingTodayIds)
}
