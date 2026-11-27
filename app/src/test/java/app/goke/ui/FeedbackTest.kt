package app.goke.ui

import android.media.AudioManager
import app.goke.core.model.Snapshot
import app.goke.core.model.Task
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FeedbackTest {
    @Test
    fun ringerModeGatesSoundAndHaptics() {
        assertTrue(FeedbackEngine.shouldPlaySound(enabled = true, ringerMode = AudioManager.RINGER_MODE_NORMAL))
        assertFalse(FeedbackEngine.shouldPlaySound(enabled = true, ringerMode = AudioManager.RINGER_MODE_VIBRATE))
        assertFalse(FeedbackEngine.shouldPlaySound(enabled = true, ringerMode = AudioManager.RINGER_MODE_SILENT))
        assertFalse(FeedbackEngine.shouldPlaySound(enabled = false, ringerMode = AudioManager.RINGER_MODE_NORMAL))

        assertTrue(FeedbackEngine.shouldVibrate(enabled = true, ringerMode = AudioManager.RINGER_MODE_NORMAL))
        assertTrue(FeedbackEngine.shouldVibrate(enabled = true, ringerMode = AudioManager.RINGER_MODE_VIBRATE))
        assertFalse(FeedbackEngine.shouldVibrate(enabled = true, ringerMode = AudioManager.RINGER_MODE_SILENT))
        assertFalse(FeedbackEngine.shouldVibrate(enabled = false, ringerMode = AudioManager.RINGER_MODE_NORMAL))
    }

    @Test
    fun soundAndWaveformMappingsMatchEventSemantics() {
        assertNotNull(FeedbackEngine.soundFor(FeedbackEvent.TASK_COMPLETE))
        assertNotNull(FeedbackEngine.soundFor(FeedbackEvent.CELEBRATE))
        assertNotNull(FeedbackEngine.soundFor(FeedbackEvent.SUCCESS))
        assertNotNull(FeedbackEngine.soundFor(FeedbackEvent.UNDO))
        assertNull(FeedbackEngine.soundFor(FeedbackEvent.SOFT_TICK))
        assertNull(FeedbackEngine.soundFor(FeedbackEvent.DELETE))

        FeedbackEvent.entries.forEach { event ->
            val (timings, amplitudes) = FeedbackEngine.waveformFor(event)
            assertTrue("waveform timings non-empty for $event", timings.isNotEmpty())
            assertTrue("timings and amplitudes match length for $event", timings.size == amplitudes.size)
        }
    }

    @Test
    fun detectsClearingLastPendingTodayTask() {
        val now = LocalDateTime.of(2026, 11, 27, 14, 0)
        val today = LocalDate.of(2026, 11, 27)
        val t1 = Task(id = "t1", title = "写作业", due = today.atTime(18, 0), done = false)
        val t2 = Task(id = "t2", title = "交实验报告", due = today.atTime(20, 0), done = false)
        val doneTask = Task(id = "t3", title = "已完成", due = today.atTime(9, 0), done = true)
        val snap = Snapshot(tasks = listOf(t1, t2, doneTask))

        assertFalse(isClearingTodayTasks(snap, setOf("t1"), now))
        assertTrue(isClearingTodayTasks(snap, setOf("t1", "t2"), now))

        val snapOneLeft = Snapshot(tasks = listOf(t1.copy(done = true), t2, doneTask))
        assertTrue(isClearingTodayTasks(snapOneLeft, setOf("t2"), now))
    }

    @Test
    fun overdueTaskMustAlsoBeClearedForCelebration() {
        val now = LocalDateTime.of(2026, 11, 27, 14, 0)
        val today = LocalDate.of(2026, 11, 27)
        val overdue = Task(id = "od", title = "昨天逾期", due = today.minusDays(1).atTime(18, 0), done = false)
        val todayTask = Task(id = "t1", title = "今天任务", due = today.atTime(18, 0), done = false)
        val snap = Snapshot(tasks = listOf(overdue, todayTask))

        assertFalse(isClearingTodayTasks(snap, setOf("t1"), now))
        assertTrue(isClearingTodayTasks(snap, setOf("od", "t1"), now))
    }
}
