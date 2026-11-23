package app.goke.notify

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import app.goke.MainActivity
import app.goke.R

/**
 * 上课自动静音：
 * - Android 10+ (API 29+) 使用显式 [AutomaticZenRule] + `setAutomaticZenRuleState`：
 *   避开 Android 15/16 `targetSdk >= 35` 下旧版 `setInterruptionFilter` 产生隐式规则并因
 *   `OVERRIDE_DEACTIVATE` 卡死的问题；下课无条件置为 `STATE_FALSE`，由系统按优先级自动恢复原有状态。
 * - Android 8–9 (< Q) 保留 `setInterruptionFilter` 与 `SharedPreferences` 恢复逻辑。
 */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handleViaZenRule(context, nm, sp, intent.action)
        } else {
            handleLegacyFilter(nm, sp, intent.action)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleViaZenRule(
        context: Context,
        nm: NotificationManager,
        sp: SharedPreferences,
        action: String?,
    ) {
        // 迁移：旧版 setInterruptionFilter 在 Android 15+ 会留下卡死的 implicit_<pkg> 规则；
        // 首次切到显式 ZenRule 时置一次 ALL（仅释放本应用的隐式规则，不影响用户手动勿扰）。
        if (!sp.getBoolean(KEY_MIGRATED_IMPLICIT, false)) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            sp.edit().putBoolean(KEY_MIGRATED_IMPLICIT, true).apply()
        }

        val ruleId = ensureZenRule(context, nm, sp)
        if (ruleId == null) {
            handleLegacyFilter(nm, sp, action)
            return
        }

        val summary = context.getString(R.string.auto_mute_rule_summary)
        when (action) {
            ACTION_MUTE -> {
                // 先置 FALSE 再置 TRUE：清除上次用户手动滑闭或异常中断留下的 OVERRIDE_DEACTIVATE。
                val ok =
                    runCatching {
                        nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_FALSE))
                        nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_TRUE))
                    }.isSuccess
                if (!ok) {
                    sp.edit().remove(KEY_RULE_ID).apply()
                    handleLegacyFilter(nm, sp, action)
                }
            }

            ACTION_UNMUTE -> {
                runCatching {
                    nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_FALSE))
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ensureZenRule(
        context: Context,
        nm: NotificationManager,
        sp: SharedPreferences,
    ): String? {
        val savedId = sp.getString(KEY_RULE_ID, null)
        if (savedId != null && nm.getAutomaticZenRule(savedId) != null) return savedId

        val existingId =
            runCatching {
                nm.automaticZenRules.entries
                    .firstOrNull { it.value.conditionId == CONDITION_ID }
                    ?.key
            }.getOrNull()
        if (existingId != null) {
            sp.edit().putString(KEY_RULE_ID, existingId).apply()
            return existingId
        }

        val rule =
            AutomaticZenRule(
                context.getString(R.string.auto_mute_rule_name),
                null,
                ComponentName(context, MainActivity::class.java),
                CONDITION_ID,
                null,
                NotificationManager.INTERRUPTION_FILTER_NONE,
                true,
            )
        val createdId = runCatching { nm.addAutomaticZenRule(rule) }.getOrNull() ?: return null
        sp.edit().putString(KEY_RULE_ID, createdId).apply()
        return createdId
    }

    private fun handleLegacyFilter(
        nm: NotificationManager,
        sp: SharedPreferences,
        action: String?,
    ) {
        when (action) {
            ACTION_MUTE -> {
                if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE &&
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                ) {
                    sp.edit().putInt(PREV_FILTER, nm.currentInterruptionFilter).apply()
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }

            ACTION_UNMUTE -> {
                val prev = sp.getInt(PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                sp.edit().remove(PREV_FILTER).apply()
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    nm.setInterruptionFilter(prev)
                }
            }
        }
    }

    companion object {
        const val ACTION_MUTE = "app.goke.action.MUTE"
        const val ACTION_UNMUTE = "app.goke.action.UNMUTE"
        private const val PREFS = "mute"
        private const val PREV_FILTER = "prev_filter"
        private const val KEY_RULE_ID = "zen_rule_id"
        private const val KEY_MIGRATED_IMPLICIT = "migrated_implicit_rule"
        val CONDITION_ID: Uri = Uri.parse("goke://automute")
    }
}
