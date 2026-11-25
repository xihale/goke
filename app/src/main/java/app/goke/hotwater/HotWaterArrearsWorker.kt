package app.goke.hotwater

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
 * 周期性热水欠费检查 Worker：联网状态下每 6 小时检查一次欠费并在超阈值时自动生成待办。
 */
class HotWaterArrearsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.gokeApp
        val hw = app.hotWater
        hw.ensureHydrated()
        val snap = hw.state.value
        if (!snap.isBound || !snap.config.arrearsTodoEnabled) {
            return Result.success()
        }
        val res = hw.refreshStatusAndCheckArrears()
        return if (res.ok) {
            Result.success()
        } else if (res.errorCode == HotWaterErrorCode.NETWORK_ERROR) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "goke-hotwater-arrears"

        private val networkConstraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        /** 跟随「已绑定 && 欠费自动待办开启」排/撤唯一周期任务（6h，NetworkType.CONNECTED，KEEP）。 */
        fun follow(
            context: Context,
            enabled: Boolean,
        ) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val req =
                    PeriodicWorkRequestBuilder<HotWaterArrearsWorker>(6, TimeUnit.HOURS)
                        .setConstraints(networkConstraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                        .build()
                wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
