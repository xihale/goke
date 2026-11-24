package app.goke.notify

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import app.goke.MainActivity
import app.goke.R

/**
 * 上课/考试自动静音：
 * - 普通课程（[MODE_VIBRATE]）：保持震动（`AudioManager.RINGER_MODE_VIBRATE`），不开启完全勿扰以免压掉震动。
 * - 考试（[MODE_SILENT]）：无声且无震动（`AudioManager.RINGER_MODE_SILENT` + `INTERRUPTION_FILTER_NONE`）。
 * - Android 10+ (API 29+) 勿扰使用显式 [AutomaticZenRule] + `setAutomaticZenRuleState`：
 *   避开 Android 15/16 `targetSdk >= 35` 下旧版 `setInterruptionFilter` 产生隐式规则并因
 *   `OVERRIDE_DEACTIVATE` 卡死的问题；下课/考试结束无条件置为 `STATE_FALSE`，并恢复先前的响铃模式。
 * - Android 8–9 (< Q) 保留 `setInterruptionFilter` 与 `SharedPreferences` 恢复逻辑。
 */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val action = intent.action
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SILENT
        when (action) {
            ACTION_MUTE -> {
                if (!sp.getBoolean(KEY_ACTIVE_MUTE, false)) {
                    sp
                        .edit()
                        .putBoolean(KEY_ACTIVE_MUTE, true)
                        .putInt(KEY_PREV_RINGER_MODE, am.ringerMode)
                        .apply()
                }
                if (mode == MODE_VIBRATE) {
                    setZenActive(context, nm, sp, active = false)
                    runCatching { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                    sp.edit().putInt(KEY_EXPECTED_RINGER_MODE, AudioManager.RINGER_MODE_VIBRATE).apply()
                } else {
                    setZenActive(context, nm, sp, active = true)
                    runCatching { am.ringerMode = AudioManager.RINGER_MODE_SILENT }
                    sp.edit().putInt(KEY_EXPECTED_RINGER_MODE, AudioManager.RINGER_MODE_SILENT).apply()
                }
            }

            ACTION_UNMUTE -> {
                setZenActive(context, nm, sp, active = false)
                if (sp.getBoolean(KEY_ACTIVE_MUTE, false)) {
                    val prev = sp.getInt(KEY_PREV_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    val expected = sp.getInt(KEY_EXPECTED_RINGER_MODE, -1)
                    sp
                        .edit()
                        .remove(KEY_ACTIVE_MUTE)
                        .remove(KEY_PREV_RINGER_MODE)
                        .remove(KEY_EXPECTED_RINGER_MODE)
                        .apply()
                    if (expected == -1 || am.ringerMode == expected) {
                        runCatching { am.ringerMode = prev }
                    }
                }
            }
        }
    }

    private fun setZenActive(
        context: Context,
        nm: NotificationManager,
        sp: SharedPreferences,
        active: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handleViaZenRule(context, nm, sp, active)
        } else {
            handleLegacyFilter(nm, sp, active)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleViaZenRule(
        context: Context,
        nm: NotificationManager,
        sp: SharedPreferences,
        active: Boolean,
    ) {
        // 迁移：旧版 setInterruptionFilter 在 Android 15+ 会留下卡死的 implicit_<pkg> 规则；
        // 首次切到显式 ZenRule 时置一次 ALL（仅释放本应用的隐式规则，不影响用户手动勿扰）。
        if (!sp.getBoolean(KEY_MIGRATED_IMPLICIT, false)) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            sp.edit().putBoolean(KEY_MIGRATED_IMPLICIT, true).apply()
        }

        val ruleId = ensureZenRule(context, nm, sp)
        if (ruleId == null) {
            handleLegacyFilter(nm, sp, active)
            return
        }

        val summary = context.getString(R.string.auto_mute_rule_summary)
        if (active) {
            // 先置 FALSE 再置 TRUE：清除上次用户手动滑闭或异常中断留下的 OVERRIDE_DEACTIVATE。
            val ok =
                runCatching {
                    nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_FALSE))
                    nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_TRUE))
                }.isSuccess
            if (!ok) {
                sp.edit().remove(KEY_RULE_ID).apply()
                handleLegacyFilter(nm, sp, true)
            }
        } else {
            runCatching {
                nm.setAutomaticZenRuleState(ruleId, Condition(CONDITION_ID, summary, Condition.STATE_FALSE))
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
        active: Boolean,
    ) {
        if (active) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE &&
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                sp.edit().putInt(PREV_FILTER, nm.currentInterruptionFilter).apply()
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        } else {
            val prev = sp.getInt(PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            sp.edit().remove(PREV_FILTER).apply()
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                nm.setInterruptionFilter(prev)
            }
        }
    }

    companion object {
        const val ACTION_MUTE = "app.goke.action.MUTE"
        const val ACTION_UNMUTE = "app.goke.action.UNMUTE"
        const val EXTRA_MODE = "mute_mode"
        const val MODE_VIBRATE = "vibrate"
        const val MODE_SILENT = "silent"
        private const val PREFS = "mute"
        private const val PREV_FILTER = "prev_filter"
        private const val KEY_RULE_ID = "zen_rule_id"
        private const val KEY_MIGRATED_IMPLICIT = "migrated_implicit_rule"
        private const val KEY_ACTIVE_MUTE = "active_mute"
        private const val KEY_PREV_RINGER_MODE = "prev_ringer_mode"
        private const val KEY_EXPECTED_RINGER_MODE = "expected_ringer_mode"
        val CONDITION_ID: Uri = Uri.parse("goke://automute")
    }
}
