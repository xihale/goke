package app.mintcal

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.importer.IcsParser
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.model.Attachment
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Freq
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.parcel.ParcelInfo
import app.mintcal.core.util.Ids
import app.mintcal.data.Attachments
import app.mintcal.data.DarkMode
import app.mintcal.data.ThemeSource
import app.mintcal.notify.ParcelTasks
import app.mintcal.ui.Actions
import app.mintcal.ui.HOME
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.MintCalRoot
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.mintDestinations
import app.mintcal.ui.rememberNav
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AppearanceScreen
import app.mintcal.ui.screens.CalendarScreen
import app.mintcal.ui.screens.CourseEditScreen
import app.mintcal.ui.screens.DataScreen
import app.mintcal.ui.screens.EditTimetableScreen
import app.mintcal.ui.screens.HistoryScreen
import app.mintcal.ui.screens.ImportScreen
import app.mintcal.ui.screens.JxfwScreen
import app.mintcal.ui.screens.McpScreen
import app.mintcal.ui.screens.MorningSheet
import app.mintcal.ui.screens.ParcelScreen
import app.mintcal.ui.screens.PeriodsScreen
import app.mintcal.ui.screens.ReliabilityScreen
import app.mintcal.ui.screens.RepeatPickDialog
import app.mintcal.ui.screens.RescheduleSheet
import app.mintcal.ui.screens.SettingsScreen
import app.mintcal.ui.screens.SlotDraft
import app.mintcal.ui.screens.SlotEditorSheet
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoEditScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.screens.WhenPanel
import app.mintcal.ui.screens.WidgetsScreen
import app.mintcal.ui.theme.MintCalTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Robolectric 不执行 androidx.startup 的 ContentProvider，WorkManager 不会像真机上那样
 * 在 Application.onCreate 之前自动初始化；而应用一启动就排周期同步（MintCalApp.onCreate），
 * Robolectric 又会在 @Before 之前泵主线程队列，所以必须在 onCreate 里先装好测试实例。
 */
class SmokeTestApp : MintCalApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}

/** 在 JVM 上用 Robolectric 渲染每个页面：确认不崩溃，并输出截图到 app/build/shots 供肉眼检查。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class ScreensSmokeTest {
    @get:Rule val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MintCalApp>()

    @Before
    fun seed() {
        runBlocking {
            app.repo.load()
            val snap = app.repo.snapshot.value
            val sem = snap.currentSemester!!
            val ics = File("../core/src/test/resources/samples/gdut.ics").readText()
            val cal = IcsParser.parse(ics).value!!
            val sem2 = sem.copy(week1Monday = IcsImporter.suggestWeek1Monday(cal) ?: sem.week1Monday)
            val now = System.currentTimeMillis()
            app.repo.commit(ChangeSet("sem", "sem", Source.MANUAL, now, listOf(Change.Modify(sem, sem2))))
            val courses = IcsImporter.toCourses(cal, sem2, now).value!!.courses
            app.repo.commit(ImportPlanner.planCourses(app.repo.snapshot.value, sem2, courses, now, "导入课表"))
            val list = TaskList(Ids.next(), "学习", 1, 0)
            val today = LocalDate.now()
            val tasks =
                listOf(
                    Task(Ids.next(), "交高数作业", listId = list.id, priority = 3, due = today.atTime(18, 0), dueHasTime = true, remindMinutes = 30),
                    Task(Ids.next(), "整理笔记", listId = list.id, due = today.plusDays(2).atStartOfDay()),
                    Task(Ids.next(), "逾期的任务", due = today.minusDays(1).atTime(12, 0), dueHasTime = true),
                    Task(Ids.next(), "排入日历的任务", blockStart = today.atTime(20, 0), blockMinutes = 90),
                )
            val meeting = Task(Ids.next(), "社团例会", listId = list.id, blockStart = today.atTime(19, 0), blockMinutes = 90, remindMinutes = 15)
            val exam = Task(Ids.next(), "考试：期末考试", blockStart = today.plusDays(10).atTime(9, 0), blockMinutes = 120, tags = listOf("考试"))
            val parcel =
                ParcelTasks.build(
                    app,
                    ParcelInfo(
                        code = "4-2-1086",
                        place = "东门菜鸟驿站",
                        company = "顺丰",
                        deadline = today.atTime(22, 0),
                        raw = "【菜鸟驿站】取件码 4-2-1086",
                        ruleId = "builtin",
                    ),
                )
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf<app.mintcal.core.model.Record>(list) + tasks + meeting + exam + parcel, "seed", now))
        }
    }

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        val st by app.settings.flow.collectAsState()
        val nav = rememberNav()
        val scope = rememberCoroutineScope()
        val actions = remember { Actions(app, scope, nav) }
        MintCalTheme(st) {
            CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(nav.controller, startDestination = HOME, modifier = Modifier.fillMaxSize()) {
                        composable(HOME) { content() }
                        mintDestinations(nav) {
                            nav.pop()
                            Unit
                        }
                    }
                }
            }
        }
    }

    private fun shot(name: String) {
        val dir = File("build/shots").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun allScreensRender() {
        val first =
            app.repo.snapshot.value.courses
                .first()
        val task =
            app.repo.snapshot.value.tasks
                .first()
        val screens: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "today" to { TodayScreen() },
                "timetable" to { TimetableScreen() },
                "calendar" to { CalendarScreen() },
                "todo" to { TodoScreen() },
                "course_edit" to { CourseEditScreen(first.id) {} },
                "course_new" to { CourseEditScreen(null) {} },
                "slot_sheet" to {
                    val sem = app.repo.snapshot.value.currentSemester!!
                    SlotEditorSheet(
                        periods = sem.periods,
                        totalWeeks = sem.totalWeeks,
                        slot =
                            SlotDraft(
                                id = first.id,
                                base = first,
                                dayOfWeek = first.dayOfWeek,
                                startPeriod = first.startPeriod,
                                endPeriod = first.endPeriod,
                                weeks = first.weeks,
                                room = first.room,
                                remindMinutes = first.remindMinutes,
                            ),
                        onChange = {},
                        onDismiss = {},
                    )
                },
                "task_edit" to { TodoEditScreen(task.id) {} },
                "task_new" to { TodoEditScreen(null) {} },
                "time_sheet" to { WhenPanel(null, LocalDate.now(), 30, onDismiss = {}, onPick = {}) },
                "repeat_monthly" to {
                    RepeatPickDialog(
                        anchor = LocalDate.now().withDayOfMonth(1),
                        freqInitial = Freq.MONTHLY,
                        intervalInitial = 1,
                        byDaysInitial = setOf(1, 15, 28),
                        byMonthsInitial = emptySet(),
                        untilInitial = null,
                        semesterEnd = null,
                        startCustomPage = true,
                        onDismiss = {},
                        onPick = { _, _, _, _, _ -> },
                    )
                },
                "repeat_yearly" to {
                    RepeatPickDialog(
                        anchor = LocalDate.now().withDayOfMonth(5),
                        freqInitial = Freq.YEARLY,
                        intervalInitial = 1,
                        byDaysInitial = setOf(5, 20),
                        byMonthsInitial = setOf(3, 10),
                        untilInitial = null,
                        semesterEnd = null,
                        startCustomPage = true,
                        onDismiss = {},
                        onPick = { _, _, _, _, _ -> },
                    )
                },
                "edit_timetable" to { EditTimetableScreen {} },
                "periods" to { PeriodsScreen {} },
                "appearance" to { AppearanceScreen {} },
                "widgets" to { WidgetsScreen {} },
                "settings" to { SettingsScreen {} },
                "data" to { DataScreen {} },
                "sync" to { SyncScreen {} },
                "jxfw" to { JxfwScreen {} },
                "reliability" to { ReliabilityScreen {} },
                "reschedule_sheet" to { RescheduleSheet(listOf(task), liveSnapshot(), onDismiss = {}) },
                "morning_sheet" to { MorningSheet(listOf(task), java.time.LocalDateTime.now()) {} },
                "import" to { ImportScreen(null) {} },
                "trash" to { TrashScreen {} },
                "history" to { HistoryScreen {} },
                "parcel" to { ParcelScreen {} },
                "about" to { AboutScreen {} },
                "mcp" to { McpScreen {} },
                "root_shell" to { MintCalRoot(app, null) {} },
            )
        val idx = mutableIntStateOf(0)
        rule.setContent { Host { key(idx.intValue) { screens[idx.intValue].second() } } }
        val failures = mutableListOf<String>()
        screens.forEachIndexed { i, (name, _) ->
            try {
                rule.runOnIdle { idx.intValue = i }
                rule.waitForIdle()
                shot(name)
            } catch (t: Throwable) {
                failures += "$name: ${t::class.simpleName}: ${t.message?.take(300)}"
                t.printStackTrace()
            }
        }
        try {
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.runOnIdle { idx.intValue = 0 }
            rule.waitForIdle()
            shot("today_dark")
            rule.runOnIdle { idx.intValue = 4 }
            rule.waitForIdle()
            shot("course_edit_dark")
            val widgetsIdx = screens.indexOfFirst { it.first == "widgets" }
            rule.runOnIdle { idx.intValue = widgetsIdx }
            rule.waitForIdle()
            shot("widgets_dark")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
        }
        assertTrue("渲染失败的页面：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** 快速添加：点右下角加号开浮层，回车即入库并收起浮层；再开浮层录入下一条。 */
    @Test
    fun quickAddImeCommitsAndCloses() {
        rule.setContent { Host { TodoScreen() } }
        // 右下角 + 打开快速添加浮层（静息时唯一带「添加」描述的节点就是这颗 FAB）
        rule.onNodeWithContentDescription(app.getString(R.string.add)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performTextInput("交实验报告 !高 #学习")
        rule.waitForIdle()
        shot("todo_typing")
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performImeAction()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "交实验报告" }
        }
        val task =
            app.repo.snapshot.value.tasks
                .first { it.title == "交实验报告" }
        assertEquals(3, task.priority)
        rule.waitForIdle()
        // 提交后浮层关闭，原文不再出现在任何节点
        rule.onAllNodesWithText("交实验报告 !高 #学习").assertCountEquals(0)
        // 再开浮层，键盘完成键等价于「确定」，直接提交第二条
        rule.onNodeWithContentDescription(app.getString(R.string.add)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performTextInput("买牛奶")
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performImeAction()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "买牛奶" }
        }
    }

    /** 「更多选项」把快速添加解析出来的内容带进编辑页：文本里的时刻是待办截止，不带持续。新建：返回丢弃、点「确定」才落库。 */
    @Test
    fun quickAddDraftTimeStaysTodo() {
        val existingIds =
            app.repo.snapshot.value.tasks
                .map { it.id }
                .toSet()
        app.settings.update { it.copy(defaultBlockMinutes = 37) }
        rule.setContent {
            Host {
                TodoEditScreen(null, textDraft = "交实验报告 明天 18:00") {}
            }
        }
        // 新建草稿：点返回即丢弃，不落库
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitForIdle()
        assertEquals(
            existingIds,
            app.repo.snapshot.value.tasks
                .map { it.id }
                .toSet(),
        )
        // 点顶栏「确定」才落库
        rule.onNodeWithText(app.getString(R.string.confirm)).performClick()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.id !in existingIds }
        }
        val task =
            app.repo.snapshot.value.tasks
                .first { it.id !in existingIds }
        // 持续留空 = todo：只有截止时刻，不排进日历
        assertEquals(LocalDate.now().plusDays(1).atTime(18, 0), task.due)
        assertTrue(task.dueHasTime)
        assertEquals(null, task.blockStart)
        assertEquals(null, task.blockMinutes)
    }

    /** 自定义月重复：选「月」+ 几个日期，确定后按 byDays 带出，不写死成每月同一天。 */
    @Test
    fun customMonthlyRepeatPicksDays() {
        var pickedDays: Set<Int>? = null
        var pickedFreq: Freq? = null
        rule.setContent {
            Host {
                RepeatPickDialog(
                    anchor = LocalDate.of(2026, 3, 1),
                    freqInitial = null,
                    intervalInitial = 1,
                    byDaysInitial = emptySet(),
                    byMonthsInitial = emptySet(),
                    untilInitial = null,
                    semesterEnd = null,
                    onDismiss = {},
                    onPick = { f, _, days, _, _ ->
                        pickedFreq = f
                        pickedDays = days
                    },
                )
            }
        }
        rule.onNodeWithText(app.getString(R.string.repeat_custom)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("月").performClick()
        rule.onNodeWithText("15").performClick()
        rule.onNodeWithText("28").performClick()
        rule.waitForIdle()
        shot("repeat_custom_month")
        rule.onNodeWithText(app.getString(R.string.ok)).performClick()
        assertEquals(Freq.MONTHLY, pickedFreq)
        // 起始日是 1 号，默认就在选中的日期里
        assertEquals(setOf(1, 15, 28), pickedDays)
    }

    @Test
    fun deletingOpenTaskReturnsToHome() {
        val task =
            app.repo.snapshot.value.tasks
                .first()
        lateinit var nav: app.mintcal.ui.Nav
        rule.setContent {
            Host {
                nav = LocalNav.current
                Text("主页")
            }
        }
        rule.runOnIdle { nav.openTask(task.id) }
        rule.onNodeWithContentDescription("更多选项").performClick()
        rule.onNodeWithText("删除").performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.id == task.id && it.deletedAt != null }
        }
        rule.waitForIdle()
        rule.onNodeWithText("主页").assertIsDisplayed()
    }

    @Test
    fun settingsOpensWidgetsScreenAndHandlesUnsupportedPin() {
        rule.setContent { Host { SettingsScreen {} } }
        rule.onNodeWithText(app.getString(R.string.widgets_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.widget_today_name)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.widget_schedule_name)).assertIsDisplayed()
        val addTodayDesc = app.getString(R.string.widgets_add_to_home_a11y, app.getString(R.string.widget_today_name))
        rule.onAllNodes(hasContentDescription(addTodayDesc))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.widgets_unsupported_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.ok)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.widgets_unsupported_title)).assertCountEquals(0)
        rule.onNode(hasScrollAction()).performScrollToIndex(4)
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.widget_next_name)).assertIsDisplayed()
    }
}
