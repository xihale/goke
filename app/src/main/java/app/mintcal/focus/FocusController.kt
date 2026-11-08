package app.mintcal.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.core.change.Edits
import app.mintcal.core.model.FocusSession
import app.mintcal.core.util.Ids
import app.mintcal.notify.Notifications
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 手动结束的结果：正常落库 / 太短丢弃。 */
enum class StopResult { SAVED, TOO_SHORT }

/**
 * 专注计时控制器：应用级单例（[MintCalApp.focus]）。
 * 计时全部按墙上时钟推算（startedAt + 暂停累计），UI 每秒读一次即可，
 * 不用常驻服务；倒计时到点靠精确闹钟（[FocusReceiver]）补记，进程被杀也不丢。
 */
class FocusController(
    private val app: MintCalApp,
) {
    private val store = FocusStore(app)
    private val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _run = MutableStateFlow<FocusRun?>(null)
    val run: StateFlow<FocusRun?> = _run.asStateFlow()

    /** 界面提示事件（字符串资源 id）：倒计时到点时发「专注完成」，避免与手动结束的提示竞态。 */
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    init {
        // 进程重建（被杀后闹钟拉起 / 冷启动）：恢复运行中的计时；倒计时已过期就按到点补记
        val restored = store.load()
        _run.value = restored
        if (restored != null) {
            if (!restored.countUp && restored.pausedAt == null && remainingMillis(restored) <= 0) {
                app.scope.launch { completeByAlarm() }
            } else {
                rearm()
            }
        }
    }

    // ── 推算 ────────────────────────────────────────────────────────────────

    /** 已专注毫秒（扣除暂停）。 */
    fun elapsedMillis(
        run: FocusRun,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val until = run.pausedAt ?: now
        return (until - run.startedAt - run.pausedMillis).coerceAtLeast(0)
    }

    /** 倒计时剩余毫秒（可为负 = 已超时未补记）。 */
    fun remainingMillis(
        run: FocusRun,
        now: Long = System.currentTimeMillis(),
    ): Long =
        run.plannedMinutes?.let { planned -> planned * 60_000L - elapsedMillis(run, now) }
            ?: Long.MAX_VALUE

    // ── 状态迁移 ────────────────────────────────────────────────────────────

    fun start(
        taskId: String?,
        countUp: Boolean,
        plannedMinutes: Int?,
    ) {
        if (_run.value != null) return
        val run =
            FocusRun(
                sessionId = Ids.next(),
                taskId = taskId,
                countUp = countUp,
                plannedMinutes = if (countUp) null else (plannedMinutes ?: 25).coerceAtLeast(1),
                startedAt = System.currentTimeMillis(),
            )
        _run.value = run
        store.save(run)
        if (!countUp) scheduleFinish(run)
    }

    fun pause() {
        val run = _run.value ?: return
        if (run.pausedAt != null) return
        val paused = run.copy(pausedAt = System.currentTimeMillis())
        _run.value = paused
        store.save(paused)
        cancelFinishAlarm()
    }

    fun resume() {
        val run = _run.value ?: return
        val pausedAt = run.pausedAt ?: return
        val now = System.currentTimeMillis()
        val resumed = run.copy(pausedMillis = run.pausedMillis + (now - pausedAt), pausedAt = null)
        _run.value = resumed
        store.save(resumed)
        if (!resumed.countUp) scheduleFinish(resumed)
    }

    /** 手动结束并落库（倒计时中途结束记 completed=false；不足一分钟不记）。挂起：等仓库就绪。 */
    suspend fun stop(): StopResult {
        val run = _run.value ?: return StopResult.TOO_SHORT
        val now = System.currentTimeMillis()
        val endedAt = run.pausedAt ?: now
        val session =
            FocusSession(
                id = run.sessionId,
                taskId = run.taskId,
                startedAt = run.startedAt,
                endedAt = endedAt,
                pausedMillis = run.pausedMillis + (run.pausedAt?.let { now - it } ?: 0L),
                plannedMinutes = run.plannedMinutes,
                completed = false,
            )
        cancelFinishAlarm()
        if (session.focusMillis < MIN_RECORD_MILLIS) {
            clear()
            return StopResult.TOO_SHORT
        }
        commitSession(session, notification = false)
        clear()
        return StopResult.SAVED
    }

    /** 倒计时到点（闹钟触发或冷启动发现已过期）：按目标时刻补记 completed=true。 */
    suspend fun completeByAlarm() {
        val run = _run.value ?: return
        if (run.countUp || run.pausedAt != null) return
        val endedAt = run.startedAt + run.pausedMillis + (run.plannedMinutes ?: 0) * 60_000L
        val session =
            FocusSession(
                id = run.sessionId,
                taskId = run.taskId,
                startedAt = run.startedAt,
                endedAt = endedAt,
                pausedMillis = run.pausedMillis,
                plannedMinutes = run.plannedMinutes,
                completed = true,
            )
        commitSession(session, notification = true, announce = true)
        clear()
    }

    // ── 闹钟 ────────────────────────────────────────────────────────────────

    /** 重新武装倒计时闹钟（暂停恢复 / 进程重建后调用）。 */
    fun rearm() {
        val run = _run.value ?: return
        if (run.countUp || run.pausedAt != null) return
        val remaining = remainingMillis(run)
        if (remaining <= 0) {
            app.scope.launch { completeByAlarm() }
            return
        }
        scheduleFinishAt(run, System.currentTimeMillis() + remaining)
    }

    private fun scheduleFinish(run: FocusRun) {
        scheduleFinishAt(run, System.currentTimeMillis() + remainingMillis(run))
    }

    private fun scheduleFinishAt(
        run: FocusRun,
        at: Long,
    ) {
        val pi = finishPendingIntent()
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private fun cancelFinishAlarm() {
        val pi = finishPendingIntent()
        am.cancel(pi)
        pi.cancel()
    }

    private fun finishPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            REQUEST_FINISH,
            Intent(app, FocusReceiver::class.java).setAction(FocusReceiver.ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ── 落库 ────────────────────────────────────────────────────────────────

    private suspend fun commitSession(
        session: FocusSession,
        notification: Boolean,
        announce: Boolean = false,
    ) {
        app.repo.ready.first { it }
        val snap = app.repo.snapshot.value
        val task = session.taskId?.let { id -> snap.tasks.firstOrNull { it.id == id } }
        val desc =
            app.getString(
                if (session.completed) R.string.focus_session_done_desc else R.string.focus_session_desc,
                task?.title ?: app.getString(R.string.focus_free),
            )
        app.repo.commit(Edits.save(snap, session, desc, System.currentTimeMillis()))
        if (announce) events.tryEmit(R.string.focus_done_title)
        // 前台时不弹通知（界面自己会提示），只在后台到点时提醒
        if (notification && !app.uiActive.value && Notifications.canPost(app)) {
            val minutes = (session.focusMillis + 59_999) / 60_000
            Notifications.post(
                app,
                NOTIFY_ID,
                Notifications.CHANNEL_REMINDER,
                app.getString(R.string.focus_done_title),
                if (task != null) {
                    app.getString(R.string.focus_done_text_task, task.title, minutes)
                } else {
                    app.getString(R.string.focus_done_text_free, minutes)
                },
                taskId = session.taskId,
            )
        }
    }

    private fun clear() {
        _run.value = null
        store.save(null)
    }

    companion object {
        private const val REQUEST_FINISH = 1
        private const val MIN_RECORD_MILLIS = 60_000L

        /** 专注完成通知的固定 id（与提醒/快递/快速添加的 id 空间隔离）。 */
        const val NOTIFY_ID = 9003
    }
}
