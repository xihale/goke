package app.goke.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.SmokeTestApp
import app.goke.core.model.Task
import app.goke.widget.TodoWidgetProvider
import app.goke.widget.WidgetOverlayActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SmokeTestApp::class)
class SnoozePlannerTest {
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking { app.repo.load() }
    }

    @Test
    fun closeTargetUsesLinearSegments() {
        val now = LocalDateTime.of(2026, 11, 26, 13, 35, 0)

        val plan25 = SnoozePlanner.plan(now, now.plusMinutes(25))
        assertEquals(SnoozeScaleMode.LINEAR, plan25.mode)
        assertEquals(25, plan25.remainingMinutes)
        assertEquals(listOf(5, 10, 15, 20, 25), plan25.options.map { it.delayMinutes })
        assertTrue(plan25.options.last().isAtTarget)
        assertEquals(now.plusMinutes(25), plan25.options.last().triggerAt)

        val plan60 = SnoozePlanner.plan(now, now.plusMinutes(60))
        assertEquals(SnoozeScaleMode.LINEAR, plan60.mode)
        assertEquals(listOf(15, 30, 45, 60), plan60.options.map { it.delayMinutes })

        val plan120 = SnoozePlanner.plan(now, now.plusMinutes(120))
        assertEquals(SnoozeScaleMode.LINEAR, plan120.mode)
        assertEquals(listOf(20, 40, 60, 80, 100, 120), plan120.options.map { it.delayMinutes })

        val plan1 = SnoozePlanner.plan(now, now.plusMinutes(1))
        assertEquals(SnoozeScaleMode.LINEAR, plan1.mode)
        assertTrue(plan1.options.first().isAtTarget)
        assertEquals(listOf(1, 5, 10, 30), plan1.options.map { it.delayMinutes })
    }

    @Test
    fun farTargetUsesExponentialSegments() {
        val now = LocalDateTime.of(2026, 11, 26, 10, 0, 0)

        val plan6h = SnoozePlanner.plan(now, now.plusHours(6))
        assertEquals(SnoozeScaleMode.EXPONENTIAL, plan6h.mode)
        assertEquals(listOf(15, 30, 60, 120, 240), plan6h.options.map { it.delayMinutes })
        assertFalse(plan6h.options.any { it.isAtTarget })

        val plan1d = SnoozePlanner.plan(now, now.plusDays(1))
        assertEquals(SnoozeScaleMode.EXPONENTIAL, plan1d.mode)
        assertEquals(listOf(30, 60, 120, 240, 480, 720), plan1d.options.map { it.delayMinutes })

        val plan3d = SnoozePlanner.plan(now, now.plusDays(3))
        assertEquals(SnoozeScaleMode.EXPONENTIAL, plan3d.mode)
        assertEquals(listOf(60, 120, 240, 480, 1440, 2880), plan3d.options.map { it.delayMinutes })
    }

    @Test
    fun pastOrNullTargetUsesStandardOptions() {
        val now = LocalDateTime.of(2026, 11, 26, 13, 0, 0)

        val planNull = SnoozePlanner.plan(now, null)
        assertEquals(SnoozeScaleMode.STANDARD, planNull.mode)
        assertNull(planNull.remainingMinutes)
        assertEquals(listOf(10, 30, 60, 180), planNull.options.filterNot { it.isTomorrowMorning }.map { it.delayMinutes })
        assertTrue(planNull.options.any { it.isTomorrowMorning })

        val planPast = SnoozePlanner.plan(now, now.minusMinutes(15))
        assertEquals(SnoozeScaleMode.STANDARD, planPast.mode)
        assertNull(planPast.remainingMinutes)
        assertTrue(planPast.options.isNotEmpty())
    }

    @Test
    fun resolveTargetAtPrefersExplicitOrBlockOrAllDayNineAm() {
        val snap = app.repo.snapshot.value
        val now = LocalDateTime.of(2026, 11, 26, 8, 0, 0)
        val blockStart = LocalDateTime.of(2026, 11, 26, 14, 30, 0)
        val explicitMs =
            blockStart
                .plusHours(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        val blockTask = Task(id = "t-blk", title = "实验", blockStart = blockStart, blockMinutes = 60, due = blockStart.plusHours(1), dueHasTime = true)
        assertEquals(blockStart, SnoozePlanner.resolveTargetAt(blockTask, snap, now))
        assertEquals(blockStart.plusHours(1), SnoozePlanner.resolveTargetAt(blockTask, snap, now, explicitMs))

        val dateOnlyTask = Task(id = "t-day", title = "交表", due = LocalDateTime.of(2026, 11, 27, 23, 59), dueHasTime = false)
        assertEquals(LocalDateTime.of(2026, 11, 27, 9, 0), SnoozePlanner.resolveTargetAt(dateOnlyTask, snap, now))
    }

    @Test
    fun notificationTapOpensWidgetOverlayAndSnoozePreservesTargetAt() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val targetAt = LocalDateTime.of(2026, 11, 26, 16, 0, 0)
        val targetAtMs = targetAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val notif =
            Notifications.buildReminderNotification(
                ctx = app,
                id = 4242,
                channel = Notifications.CHANNEL_REMINDER,
                title = "交实验报告",
                text = "待办到时间了",
                taskId = "task-42",
                insistent = false,
                important = false,
                targetAtMs = targetAtMs,
            )

        val openIntent = shadowOf(notif.contentIntent).savedIntent
        assertEquals(WidgetOverlayActivity::class.java.name, openIntent.component?.className)
        assertEquals("task-42", openIntent.getStringExtra(TodoWidgetProvider.EXTRA_TASK_ID))
        assertEquals(4242, openIntent.getIntExtra(ReminderReceiver.EXTRA_ID, 0))
        assertEquals(4242, openIntent.getIntExtra(ReminderAlertService.EXTRA_STOP_ALERT_ID, 0))
        assertEquals(targetAtMs, openIntent.getLongExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, 0L))

        val fireAt = LocalDateTime.of(2026, 11, 26, 15, 45, 0)
        val fireAtMs = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        SnoozePlanner.scheduleSnooze(
            ctx = app,
            taskId = "task-42",
            notifId = 4242,
            title = "交实验报告",
            text = "待办到时间了",
            persistent = false,
            important = false,
            triggerAt = fireAt,
            targetAt = targetAt,
        )

        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(am).scheduledAlarms.firstOrNull { it.triggerAtTime == fireAtMs }
        assertNotNull(scheduled)
        val saved = shadowOf(scheduled!!.operation).savedIntent
        assertEquals(ReminderReceiver::class.java.name, saved.component?.className)
        assertEquals("task-42", saved.getStringExtra(ReminderReceiver.EXTRA_TASK_ID))
        assertEquals(targetAtMs, saved.getLongExtra(ReminderReceiver.EXTRA_TARGET_AT_MS, 0L))

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNull(shadowOf(nm).getNotification(4242))
    }
}
