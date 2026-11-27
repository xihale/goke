package app.goke.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val id = intent.getIntExtra(EXTRA_ID, title.hashCode())
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val courseDate = intent.getStringExtra(EXTRA_COURSE_DATE)
        val courseStartPeriod = intent.getIntExtra(EXTRA_COURSE_START_PERIOD, -1).takeIf { it > 0 }
        val courseEndPeriod = intent.getIntExtra(EXTRA_COURSE_END_PERIOD, -1).takeIf { it > 0 }
        val insistent = intent.getBooleanExtra(EXTRA_PERSISTENT, false)
        val important = intent.getBooleanExtra(EXTRA_IMPORTANT, false)
        val digest = intent.getBooleanExtra(EXTRA_DIGEST, false)
        val targetAtMs = intent.getLongExtra(EXTRA_TARGET_AT_MS, 0L).takeIf { it > 0L }
        Notifications.ensureChannels(context)
        if (insistent && Notifications.canPost(context)) {
            val started =
                ReminderAlertService.start(
                    ctx = context,
                    id = id,
                    title = title,
                    text = text,
                    taskId = taskId,
                    important = important,
                    courseId = courseId,
                    courseDate = courseDate,
                    courseStartPeriod = courseStartPeriod,
                    courseEndPeriod = courseEndPeriod,
                    targetAtMs = targetAtMs,
                )
            if (started) return
        }
        val channel =
            when {
                important -> Notifications.CHANNEL_EXAM
                digest -> Notifications.CHANNEL_DIGEST
                else -> Notifications.CHANNEL_REMINDER
            }
        Notifications.post(
            ctx = context,
            id = id,
            channel = channel,
            title = title,
            text = text,
            taskId = taskId,
            insistent = insistent,
            important = important,
            courseId = courseId,
            courseDate = courseDate,
            courseStartPeriod = courseStartPeriod,
            courseEndPeriod = courseEndPeriod,
            targetAtMs = targetAtMs,
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ID = "id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_COURSE_DATE = "course_date"
        const val EXTRA_COURSE_START_PERIOD = "course_start_period"
        const val EXTRA_COURSE_END_PERIOD = "course_end_period"
        const val EXTRA_PERSISTENT = "persistent"
        const val EXTRA_IMPORTANT = "important"
        const val EXTRA_DIGEST = "digest"
        const val EXTRA_TARGET_AT_MS = "target_at_ms"
    }
}
