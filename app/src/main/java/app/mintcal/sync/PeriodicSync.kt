package app.mintcal.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mintcal.mintApp
import java.util.concurrent.TimeUnit

/**
 * 后台周期同步：登录状态下每 15 分钟拉一次服务器（网络可用时）。
 * 服务器不会主动推送——外部 AI 助手经 MCP 写入、或另一台设备的改动，都在这里到达本机；
 * 不加这个的话，App 只有在打开或本地数据变化时才会看到它们。
 * 退出登录自动取消；手动 / 打开 App / 数据变化触发的同步照旧。
 */
object PeriodicSync {
    private const val WORK_NAME = "mintcal-periodic-sync"
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
}

/** 一次后台同步：未登录时直接成功；失败按退避重试。 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.mintApp
        if (app.sync.state.value.account == null) return Result.success()
        return try {
            app.sync.syncNow()
            Result.success()
        } catch (_: SyncException) {
            Result.retry()
        }
    }
}
