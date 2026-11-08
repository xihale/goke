package app.mintcal.focus

import android.content.Context
import androidx.core.content.edit
import app.mintcal.core.util.MintJson
import kotlinx.serialization.Serializable

/**
 * 一次进行中的专注计时。只存在于 [FocusStore]（SharedPreferences）与应用进程的 StateFlow 里，
 * 不落记录表——结束（或倒计时到点）才写一条 [app.mintcal.core.model.FocusSession]。
 * 全部用墙上时钟：进程被杀后按时间差恢复，不需要常驻前台服务。
 */
@Serializable
data class FocusRun(
    /** 结束时落库的 FocusSession.id，开工时就生成。 */
    val sessionId: String,
    /** 关联待办；null = 自由专注。 */
    val taskId: String? = null,
    /** true = 正计时；false = 倒计时（[plannedMinutes] 必填）。 */
    val countUp: Boolean,
    val plannedMinutes: Int? = null,
    /** 开始时刻（UnixMillis）。 */
    val startedAt: Long,
    /** 非 null 表示暂停中：暂停开始的时刻。 */
    val pausedAt: Long? = null,
    /** 本次暂停之前累计的暂停毫秒。 */
    val pausedMillis: Long = 0,
)

/** 运行中专注的持久化：单键 JSON，进程重建后由 [FocusController] 恢复。 */
class FocusStore(
    context: Context,
) {
    private val sp = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun load(): FocusRun? = sp.getString(KEY, null)?.let { runCatching { MintJson.decodeFromString(FocusRun.serializer(), it) }.getOrNull() }

    fun save(run: FocusRun?) {
        sp.edit {
            if (run == null) remove(KEY) else putString(KEY, MintJson.encodeToString(FocusRun.serializer(), run))
        }
    }

    companion object {
        private const val KEY = "run"
    }
}
