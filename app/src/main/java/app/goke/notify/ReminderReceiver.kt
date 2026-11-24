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
        val important = intent.getBooleanExtra(EXTRA_IMPORTANT, false)
        Notifications.ensureChannels(context)
        Notifications.post(
            ctx = context,
            id = id,
            channel = if (important) Notifications.CHANNEL_EXAM else Notifications.CHANNEL_REMINDER,
            title = title,
            text = text,
            taskId = intent.getStringExtra(EXTRA_TASK_ID),
            insistent = intent.getBooleanExtra(EXTRA_PERSISTENT, false),
            important = important,
            courseId = intent.getStringExtra(EXTRA_COURSE_ID),
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ID = "id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_PERSISTENT = "persistent"
        const val EXTRA_IMPORTANT = "important"
    }
}
