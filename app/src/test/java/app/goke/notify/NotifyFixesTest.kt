package app.goke.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.media.AudioManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.agenda.Agenda
import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.EventKind
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.DayOfWeek
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SmokeTestApp::class)
class NotifyFixesTest {
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
        }
        app
            .getSharedPreferences("mute", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun cancelMutesActuallyCancelsScheduledMuteAndUnmuteAlarms() {
        val now =
            LocalDateTime
                .now()
                .withHour(8)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        val sem = app.repo.snapshot.value.currentSemester!!
        val course =
            Course(
                id = "c-mute-1",
                semesterId = sem.id,
                name = "编译原理",
                dayOfWeek = now.dayOfWeek.value,
                startPeriod = 3,
                endPeriod = 4,
                weeks = (1..20).toSet(),
            )
        val monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val sem2 = sem.copy(week1Monday = monday)
        runBlocking {
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(sem2, course), "seed", 1L))
        }
        val scheduler = ReminderScheduler(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        runBlocking {
            scheduler.reschedule(app.repo.snapshot.value, app.settings.value.copy(autoMute = true), now)
        }
        val piMuteBefore =
            PendingIntent.getBroadcast(
                app,
                ReminderScheduler.MUTE_BASE,
                Intent(app, MuteReceiver::class.java).setAction(MuteReceiver.ACTION_MUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
        val piUnmuteBefore =
            PendingIntent.getBroadcast(
                app,
                ReminderScheduler.MUTE_BASE + 1,
                Intent(app, MuteReceiver::class.java).setAction(MuteReceiver.ACTION_UNMUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
        assertNotNull(piMuteBefore)
        assertNotNull(piUnmuteBefore)
        assertTrue(shadowOf(am).scheduledAlarms.isNotEmpty())

        // 关闭 autoMute 后再次排期：必须真正取消之前的 MUTE 与 UNMUTE PendingIntent
        runBlocking {
            scheduler.reschedule(app.repo.snapshot.value, app.settings.value.copy(autoMute = false), now)
        }
        val piMuteAfter =
            PendingIntent.getBroadcast(
                app,
                ReminderScheduler.MUTE_BASE,
                Intent(app, MuteReceiver::class.java).setAction(MuteReceiver.ACTION_MUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
        val piUnmuteAfter =
            PendingIntent.getBroadcast(
                app,
                ReminderScheduler.MUTE_BASE + 1,
                Intent(app, MuteReceiver::class.java).setAction(MuteReceiver.ACTION_UNMUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
        assertNull(piMuteAfter)
        assertNull(piUnmuteAfter)
    }

    @Test
    fun muteReceiverUsesAutomaticZenRuleOnApi29PlusAndUnmutesUnconditionally() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationPolicyAccessGranted(true)

        val receiver = MuteReceiver()
        receiver.onReceive(app, Intent(MuteReceiver.ACTION_MUTE))

        val ruleId = app.getSharedPreferences("mute", Context.MODE_PRIVATE).getString("zen_rule_id", null)
        assertNotNull(ruleId)
        assertEquals(1, nm.automaticZenRules.size)
        val rule = nm.getAutomaticZenRule(ruleId!!)
        assertNotNull(rule)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, rule!!.interruptionFilter)

        // 即使当前处于 PRIORITY 或用户手动取消了全局勿扰，下课广播也必须无条件执行而不卡死
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        receiver.onReceive(app, Intent(MuteReceiver.ACTION_UNMUTE))
        // 若不崩溃且能继续响应下一节课 MUTE 即通过（ShadowNotificationManager 对规则状态追踪有限）
        receiver.onReceive(app, Intent(MuteReceiver.ACTION_MUTE))
        assertEquals(ruleId, app.getSharedPreferences("mute", Context.MODE_PRIVATE).getString("zen_rule_id", null))
    }

    @Test
    fun parcelCaptureDeduplicatesAndDetectsRedactedNotification() {
        app.settings.update { it.copy(parcelEnabled = true) }

        assertTrue(ParcelCapture.isRedactedText("敏感通知内容已隐藏"))
        assertTrue(ParcelCapture.isRedactedText("Sensitive notification content hidden"))
        assertFalse(ParcelCapture.isRedactedText("【菜鸟驿站】取件码4-2-1086，请到东门菜鸟驿站领取"))

        val sample = "【菜鸟驿站】您的快递已到东门菜鸟驿站，凭取件码 4-2-1086 领取。"
        val added1 = runBlocking { ParcelCapture.ingest(app, sample) }
        val added2 = runBlocking { ParcelCapture.ingest(app, sample) }
        assertTrue(added1)
        assertFalse(added2)

        val parcelTasks =
            app.repo.snapshot.value.tasks
                .filter { it.source == Source.PARCEL && it.extra["code"] == "4-2-1086" }
        assertEquals(1, parcelTasks.size)
    }

    @Test
    fun courseNotificationOmitsKaikeAndFollowsShowTeacherDefault() {
        assertTrue(Settings().showTeacher)

        val now =
            LocalDateTime
                .now()
                .withHour(8)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        val sem = app.repo.snapshot.value.currentSemester!!
        val monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val sem2 = sem.copy(week1Monday = monday)
        val course =
            Course(
                id = "c-notif-1",
                semesterId = sem.id,
                name = "操作系统",
                teacher = "张老师",
                room = "实4-407A",
                dayOfWeek = now.dayOfWeek.value,
                startPeriod = 5,
                endPeriod = 6,
                weeks = (1..20).toSet(),
            )
        runBlocking {
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(sem2, course), "seed", 1L))
        }

        val snap = app.repo.snapshot.value
        val withTeacher = ReminderScheduler.compute(app, snap, app.settings.value.copy(remindCourse = true, showTeacher = true), now)
        val r1 = withTeacher.first { it.title == "操作系统" }
        assertFalse(r1.text.contains("开课"))
        assertTrue(r1.text.endsWith("· 实4-407A · 张老师"))

        val withoutTeacher = ReminderScheduler.compute(app, snap, app.settings.value.copy(remindCourse = true, showTeacher = false), now)
        val r2 = withoutTeacher.first { it.title == "操作系统" }
        assertFalse(r2.text.contains("开课"))
        assertFalse(r2.text.contains("张老师"))
        assertTrue(r2.text.endsWith("· 实4-407A"))
    }

    @Test
    fun examCourseGeneratesImportantPersistentRemindersEvenWhenNormalCourseRemindOff() {
        val now =
            LocalDateTime
                .now()
                .withHour(8)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        val sem = app.repo.snapshot.value.currentSemester!!
        val monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val sem2 = sem.copy(week1Monday = monday)
        val examCourse =
            Course(
                id = "c-exam-1",
                semesterId = sem.id,
                name = "大学物理实验",
                teacher = "牟中飞",
                room = "实4-403",
                dayOfWeek = now.dayOfWeek.value,
                startPeriod = 5,
                endPeriod = 7,
                weeks = setOf(1),
                note = "第1周：实验十一、期末实验考核",
                kind = EventKind.EXAM,
                remindPersistent = true,
            )
        val examTask =
            Task(
                id = "jxfw-exam/202601/E99",
                title = "考试：高等数学",
                note = "闭卷 · 教1-201 · 座位号 12",
                tags = listOf("考试"),
                priority = 3,
                startDate = now.toLocalDate().plusDays(1),
                due = now.toLocalDate().plusDays(1).atTime(11, 0),
                dueHasTime = true,
                blockStart = now.toLocalDate().plusDays(1).atTime(9, 0),
                blockMinutes = 120,
                remindPersistent = true,
                source = Source.IMPORT,
                extra = mapOf("kind" to EventKind.EXAM.name, "checkable" to "false", "location" to "教1-201"),
            )
        runBlocking {
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(sem2, examCourse, examTask), "seed", 1L))
        }

        val snap = app.repo.snapshot.value
        val st = app.settings.value.copy(remindCourse = false, remindExam = true, examRemindMinutesList = listOf(30, 1440), examRemindPersistent = true)
        val reminders = ReminderScheduler.compute(app, snap, st, now)

        val courseReminders = reminders.filter { it.courseId == examCourse.id }
        assertTrue(courseReminders.isNotEmpty())
        assertTrue(courseReminders.all { it.important })
        assertTrue(courseReminders.all { it.title.contains("考试") })
        assertTrue(courseReminders.any { it.text.contains("实验十一、期末实验考核") })
        assertTrue(courseReminders.any { it.persistent })

        val taskReminders = reminders.filter { it.taskId == examTask.id }
        assertTrue(taskReminders.isNotEmpty())
        assertTrue(taskReminders.all { it.important })
        assertTrue(taskReminders.any { it.text.contains("教1-201") })

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val receiver = ReminderReceiver()
        val first = courseReminders.first { it.persistent }
        receiver.onReceive(
            app,
            Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, first.title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, first.text)
                .putExtra(ReminderReceiver.EXTRA_ID, 777)
                .putExtra(ReminderReceiver.EXTRA_COURSE_ID, first.courseId)
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, first.persistent)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, first.important),
        )
        val serviceIntent = shadowOf(app).nextStartedService
        assertNotNull(serviceIntent)
        assertEquals(ReminderAlertService::class.java.name, serviceIntent.component?.className)
        assertEquals(ReminderAlertService.ACTION_START, serviceIntent.action)
        val serviceController = Robolectric.buildService(ReminderAlertService::class.java, serviceIntent).create().startCommand(0, 0)
        assertEquals(777, ReminderAlertService.activeNotifId)

        val posted = shadowOf(nm).getNotification(777)
        assertNotNull(posted)
        assertEquals(Notifications.CHANNEL_EXAM, posted!!.channelId)
        assertEquals(NotificationCompat.CATEGORY_ALARM, posted.category)
        assertNotNull(posted.deleteIntent)
        assertTrue(posted.actions.any { it.title == app.getString(R.string.notif_action_stop) })

        TaskActionReceiver().onReceive(
            app,
            Intent(app, TaskActionReceiver::class.java)
                .setAction(TaskActionReceiver.ACTION_DISMISS)
                .putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, 777),
        )
        assertEquals(0, ReminderAlertService.activeNotifId)
        assertNull(shadowOf(nm).getNotification(777))
        serviceController.destroy()
    }

    @Test
    fun ordinaryReminderUsesAudibleChannelAndPersistentStopClearsTaskAlert() {
        Notifications.resetAlertCooldownForTest()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(app)

        val reminderChannel = nm.getNotificationChannel(Notifications.CHANNEL_REMINDER)
        assertNotNull(reminderChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, reminderChannel!!.importance)
        assertEquals(Notifications.normalChimeUri(app), reminderChannel.sound)
        assertTrue(reminderChannel.shouldVibrate())

        val digestChannel = nm.getNotificationChannel(Notifications.CHANNEL_DIGEST)
        assertNotNull(digestChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, digestChannel!!.importance)
        assertEquals(Notifications.digestChimeUri(app), digestChannel.sound)

        val examChannel = nm.getNotificationChannel(Notifications.CHANNEL_EXAM)
        assertNotNull(examChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, examChannel!!.importance)
        assertEquals(Notifications.urgentChimeUri(app), examChannel.sound)
        assertTrue(examChannel.shouldVibrate())
        assertTrue(examChannel.canBypassDnd())

        ReminderReceiver().onReceive(
            app,
            Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "普通待办提醒")
                .putExtra(ReminderReceiver.EXTRA_TEXT, "待办到时间了")
                .putExtra(ReminderReceiver.EXTRA_ID, 888)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, "t-ordinary-1")
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, false)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, false),
        )
        assertNull(shadowOf(app).nextStartedService)
        val ordinary = shadowOf(nm).getNotification(888)
        assertNotNull(ordinary)
        assertEquals(Notifications.CHANNEL_REMINDER, ordinary!!.channelId)
        assertEquals(NotificationCompat.CATEGORY_REMINDER, ordinary.category)
        assertEquals(2, ordinary.actions.size)

        // 连续 3 秒内的第二条普通提醒自动静默避让，避免重叠刺耳
        ReminderReceiver().onReceive(
            app,
            Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "普通待办提醒 2")
                .putExtra(ReminderReceiver.EXTRA_TEXT, "待办到时间了")
                .putExtra(ReminderReceiver.EXTRA_ID, 889)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, "t-ordinary-2")
                .putExtra(ReminderReceiver.EXTRA_PERSISTENT, false)
                .putExtra(ReminderReceiver.EXTRA_IMPORTANT, false),
        )
        val burstSecond = shadowOf(nm).getNotification(889)
        assertNotNull(burstSecond)
        assertNull(burstSecond!!.sound)

        // 每日摘要走独立温和渠道，且不受普通提醒退避影响
        Notifications.resetAlertCooldownForTest()
        ReminderReceiver().onReceive(
            app,
            Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, "今日安排")
                .putExtra(ReminderReceiver.EXTRA_TEXT, "今天 2 节课 · 1 项待办")
                .putExtra(ReminderReceiver.EXTRA_ID, 890)
                .putExtra(ReminderReceiver.EXTRA_DIGEST, true),
        )
        val digestNotif = shadowOf(nm).getNotification(890)
        assertNotNull(digestNotif)
        assertEquals(Notifications.CHANNEL_DIGEST, digestNotif!!.channelId)
    }

    @Test
    fun smartAlertRampAdaptsToHeadphonesForegroundAndQuietHours() {
        val headphones =
            ReminderAlertService.computeRampProfile(
                important = true,
                headphonesConnected = true,
                quietHours = false,
                inForeground = false,
            )
        assertTrue(headphones.maxVolume <= 0.55f)
        assertTrue(headphones.startVolume < headphones.maxVolume)

        val foreground =
            ReminderAlertService.computeRampProfile(
                important = false,
                headphonesConnected = false,
                quietHours = false,
                inForeground = true,
            )
        assertTrue(foreground.maxVolume <= 0.6f)

        val night =
            ReminderAlertService.computeRampProfile(
                important = false,
                headphonesConnected = false,
                quietHours = true,
                inForeground = false,
            )
        val dayExam =
            ReminderAlertService.computeRampProfile(
                important = true,
                headphonesConnected = false,
                quietHours = false,
                inForeground = false,
            )
        assertTrue(night.startVolume < dayExam.startVolume)
        assertTrue(night.rampDurationMs > dayExam.rampDurationMs)
        assertEquals(1.0f, dayExam.maxVolume, 0.001f)
    }

    @Test
    fun normalCourseMutesToVibrateWhileExamMutesToSilentWithoutVibration() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        am.ringerMode = AudioManager.RINGER_MODE_NORMAL

        val receiver = MuteReceiver()
        // 普通课程：静音但保持震动
        receiver.onReceive(
            app,
            Intent(MuteReceiver.ACTION_MUTE).putExtra(MuteReceiver.EXTRA_MODE, MuteReceiver.MODE_VIBRATE),
        )
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, am.ringerMode)

        // 下课恢复正常响铃
        receiver.onReceive(app, Intent(MuteReceiver.ACTION_UNMUTE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, am.ringerMode)

        // 考试：完全静音（连震动都关闭）并启用 ZenRule
        receiver.onReceive(
            app,
            Intent(MuteReceiver.ACTION_MUTE).putExtra(MuteReceiver.EXTRA_MODE, MuteReceiver.MODE_SILENT),
        )
        assertEquals(AudioManager.RINGER_MODE_SILENT, am.ringerMode)
        val ruleId = app.getSharedPreferences("mute", Context.MODE_PRIVATE).getString("zen_rule_id", null)
        assertNotNull(ruleId)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, nm.getAutomaticZenRule(ruleId!!)?.interruptionFilter)

        // 考试结束恢复原响铃模式
        receiver.onReceive(app, Intent(MuteReceiver.ACTION_UNMUTE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, am.ringerMode)
    }

    @Test
    fun computeMuteSlotsDistinguishesCourseVibrateAndExamSilentAndPrioritizesExamOnOverlap() {
        val now =
            LocalDateTime
                .now()
                .withHour(8)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        val sem = app.repo.snapshot.value.currentSemester!!
        val monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val sem2 = sem.copy(week1Monday = monday)
        val normalCourse =
            Course(
                id = "c-normal-mute",
                semesterId = sem.id,
                name = "大学物理",
                dayOfWeek = now.dayOfWeek.value,
                startPeriod = 1,
                endPeriod = 4,
                weeks = setOf(1),
                kind = EventKind.NORMAL,
            )
        val examBlock =
            Task(
                id = "jxfw-exam/202601/E55",
                title = "考试：大学物理",
                tags = listOf("考试"),
                startDate = now.toLocalDate(),
                due = now.toLocalDate().atTime(11, 0),
                dueHasTime = true,
                blockStart = now.toLocalDate().atTime(9, 0),
                blockMinutes = 120,
                source = Source.IMPORT,
                extra = mapOf("kind" to EventKind.EXAM.name, "checkable" to "false"),
            )
        runBlocking {
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(sem2, normalCourse, examBlock), "seed", 1L))
        }
        val snap = app.repo.snapshot.value
        val days = Agenda.range(snap, now.toLocalDate(), now.toLocalDate().plusDays(1), 30, includeDoneTasks = false)
        val slots = ReminderScheduler.computeMuteSlots(days, app.settings.value.copy(autoMute = true), now)

        assertEquals(3, slots.size)
        assertEquals(ReminderScheduler.MuteSlot(now.toLocalDate().atTime(8, 30), now.toLocalDate().atTime(9, 0), silent = false), slots[0])
        assertEquals(ReminderScheduler.MuteSlot(now.toLocalDate().atTime(9, 0), now.toLocalDate().atTime(11, 0), silent = true), slots[1])
        assertEquals(ReminderScheduler.MuteSlot(now.toLocalDate().atTime(11, 0), now.toLocalDate().atTime(12, 0), silent = false), slots[2])
    }

    @Test
    fun parcelListenerExtractsInboxAndMessagingStyleCandidates() {
        app.settings.update { it.copy(parcelEnabled = true) }

        val inboxNotif =
            NotificationCompat
                .Builder(app, Notifications.CHANNEL_PARCEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("信息")
                .setContentText("2 条新信息")
                .setStyle(
                    NotificationCompat
                        .InboxStyle()
                        .addLine("验证码 123456")
                        .addLine("【菜鸟驿站】取件码 7-1-3021，请到南门菜鸟驿站领取。"),
                ).build()
        val inboxCandidates = ParcelListenerService.extractCandidates(inboxNotif)
        assertTrue(runBlocking { ParcelCapture.ingestCandidates(app, inboxCandidates) })

        val msgPerson = Person.Builder().setName("菜鸟驿站").build()
        val msgNotif =
            NotificationCompat
                .Builder(app, Notifications.CHANNEL_PARCEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(
                    NotificationCompat
                        .MessagingStyle(msgPerson)
                        .addMessage("敏感通知内容已隐藏", System.currentTimeMillis(), msgPerson)
                        .addMessage("您的快递已到西门菜鸟驿站，凭取件码 8-3-9012 领取。", System.currentTimeMillis(), msgPerson),
                ).build()
        val msgCandidates = ParcelListenerService.extractCandidates(msgNotif)
        assertTrue(runBlocking { ParcelCapture.ingestCandidates(app, msgCandidates) })
        assertTrue(ParcelCapture.redactedDetected.value)

        val tasks =
            app.repo.snapshot.value.tasks
                .filter { it.source == Source.PARCEL }
        assertTrue(tasks.any { it.extra["code"] == "7-1-3021" })
        assertTrue(tasks.any { it.extra["code"] == "8-3-9012" })
    }

    @Test
    fun parcelScanRecentSmsInboxCatchesMissedSmsAndDoesNotResurrectDoneTask() {
        app.settings.update { it.copy(parcelEnabled = true) }
        app
            .getSharedPreferences("parcel_sms_scan", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

        val nowMs = System.currentTimeMillis()
        val smsBody = "【菜鸟驿站】您的快递已到东门菜鸟驿站，凭取件码 5-4-2288 领取。"
        val provider =
            object : ContentProvider() {
                override fun onCreate(): Boolean = true

                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?,
                ): Cursor {
                    val cursor = MatrixCursor(arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE))
                    cursor.addRow(arrayOf<Any>(101L, smsBody, nowMs - 5_000L))
                    return cursor
                }

                override fun getType(uri: Uri): String? = null

                override fun insert(
                    uri: Uri,
                    values: ContentValues?,
                ): Uri? = null

                override fun delete(
                    uri: Uri,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int = 0

                override fun update(
                    uri: Uri,
                    values: ContentValues?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int = 0
            }
        ShadowContentResolver.registerProviderInternal("sms", provider)

        val captured1 = runBlocking { ParcelCapture.scanRecentSmsInbox(app) }
        assertEquals(1, captured1)
        val created =
            app.repo.snapshot.value.tasks
                .firstOrNull { it.source == Source.PARCEL && it.extra["code"] == "5-4-2288" }
        assertNotNull(created)

        // 标记已完成并清空扫描游标：再次扫描同一条短信也不能复活已完成待办
        runBlocking {
            app.repo.commit(Edits.save(app.repo.snapshot.value, created!!.copy(done = true), "done", nowMs))
        }
        app
            .getSharedPreferences("parcel_sms_scan", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val captured2 = runBlocking { ParcelCapture.scanRecentSmsInbox(app) }
        assertEquals(0, captured2)
        val allMatches =
            app.repo.snapshot.value.tasks
                .filter { it.source == Source.PARCEL && it.extra["code"] == "5-4-2288" }
        assertEquals(1, allMatches.size)
        assertTrue(allMatches.single().done)
    }
}
