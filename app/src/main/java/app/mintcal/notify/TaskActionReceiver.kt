package app.mintcal.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.mintcal.core.change.Edits
import app.mintcal.mintApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 待办提醒通知上的「完成 / 1 小时后再提醒」按钮：不打开应用，直接处理。 */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val nid = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val app = context.mintApp
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
                val again =
                    Intent(context, ReminderReceiver::class.java)
                        .setData(Uri.parse("mintcal://snooze/$nid"))
                        .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                        .putExtra(ReminderReceiver.EXTRA_TEXT, text)
                        .putExtra(ReminderReceiver.EXTRA_ID, nid)
                        .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                val pi = PendingIntent.getBroadcast(context, 0, again, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val am = context.getSystemService(AlarmManager::class.java)
                val at = System.currentTimeMillis() + SNOOZE_MS
                runCatching {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }
                }
            }
        }
        if (nid != 0) context.getSystemService(android.app.NotificationManager::class.java).cancel(nid)
    }

    companion object {
        const val ACTION_DONE = "app.mintcal.action.TASK_DONE"
        const val ACTION_SNOOZE = "app.mintcal.action.TASK_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_ID = "nid"
        const val SNOOZE_MS = 60 * 60 * 1000L
    }
}
