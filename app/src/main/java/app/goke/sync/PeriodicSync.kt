package app.goke.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.goke.gokeApp
import java.util.concurrent.TimeUnit

/**
 * 后台周期同步：
 * 1. 云端账号登录状态下每 15 分钟拉一次服务器（网络可用时）；
 * 2. 系统日历双向同步开启时每 15 分钟对账一次本机 CalendarContract（无需网络约束）。
 */
object PeriodicSync {
    private const val WORK_NAME = "goke-periodic-sync"
    private const val CAL_WORK_NAME = "goke-periodic-cal-sync"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** 跟随登录状态排 / 撤后台任务。KEEP：App 每次启动不重置既定节奏。 */
    fun follow(
        context: Context,
        loggedIn: Boolean,
    ) {
        val wm = WorkManager.getInstance(context)
        if (loggedIn) {
            val req =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                    .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    /** 跟随系统日历同步开关排 / 撤后台对账任务。 */
    fun followCalendar(
        context: Context,
        enabled: Boolean,
    ) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val req =
                PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                    .build()
            wm.enqueueUniquePeriodicWork(CAL_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(CAL_WORK_NAME)
        }
    }
}

/** 后台云同步暂未启用：清理遗留周期任务并直接返回成功。 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PeriodicSync.follow(applicationContext, false)
        return Result.success()
    }
}

/** 一次后台系统日历对账：无需网络，确保桌面或系统日历里的改动定期同步进 Goke。 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.gokeApp
        if (!app.calendarSync.state.value.prefs.enabled) return Result.success()
        runCatching { app.calendarSync.syncNow() }
        return Result.success()
    }
}
