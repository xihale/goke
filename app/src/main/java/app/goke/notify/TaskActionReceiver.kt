package app.goke.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.goke.core.change.Edits
import app.goke.gokeApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 待办提醒通知上的「完成 / 1 小时后再提醒」按钮：不打开应用，直接处理。 */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val nid = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (intent.action == ACTION_DISMISS) {
            ReminderAlertService.stop(context, nid)
            if (nid != 0) context.getSystemService(android.app.NotificationManager::class.java).cancel(nid)
            return
        }
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val app = context.gokeApp
        when (intent.action) {
            ACTION_DONE -> {
                val pending = goAsync()
                app.scope.launch {
                    try {
                        app.repo.ready.first { it }
                        val snap = app.repo.snapshot.value
                        val task = snap.tasks.firstOrNull { it.id == taskId && it.deletedAt == null }
                        if (task != null && !task.done) {
                            app.repo.commit(Edits.toggleTask(snap, task, true, System.currentTimeMillis(), LocalDateTime.now(), task.title))
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty()
                val text = intent.getStringExtra(ReminderReceiver.EXTRA_TEXT).orEmpty()
                val targetAtMs = intent.getLongExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, 0L).takeIf { it > 0L }
                SnoozePlanner.scheduleSnoozeAtEpoch(
                    ctx = context,
                    taskId = taskId,
                    notifId = nid,
                    title = title,
                    text = text,
                    persistent = intent.getBooleanExtra(ReminderReceiver.EXTRA_PERSISTENT, false),
                    important = intent.getBooleanExtra(ReminderReceiver.EXTRA_IMPORTANT, false),
                    triggerAtMs = System.currentTimeMillis() + SNOOZE_MS,
                    targetAtMs = targetAtMs,
                )
                return
            }
        }
        ReminderAlertService.stop(context, nid)
        if (nid != 0) context.getSystemService(android.app.NotificationManager::class.java).cancel(nid)
    }

    companion object {
        const val ACTION_DONE = "app.goke.action.TASK_DONE"
        const val ACTION_SNOOZE = "app.goke.action.TASK_SNOOZE"
        const val ACTION_DISMISS = "app.goke.action.TASK_DISMISS"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_ID = "nid"
        const val SNOOZE_MS = SnoozePlanner.DEFAULT_ACTION_SNOOZE_MS
    }
}
