package app.goke

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.goke.core.agenda.BlockItem
import app.goke.core.agenda.CourseItem
import app.goke.core.agenda.TaskItem
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.importer.IcsImporter
import app.goke.core.importer.IcsParser
import app.goke.core.importer.ImportPlanner
import app.goke.core.model.Attachment
import app.goke.core.model.Course
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Freq
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.parcel.ParcelInfo
import app.goke.core.recurrence.CourseOccurrence
import app.goke.core.time.SemesterCalendar
import app.goke.core.time.TimePhase
import app.goke.core.util.Ids
import app.goke.data.Attachments
import app.goke.data.DarkMode
import app.goke.data.ThemeSource
import app.goke.debug.DebugHooks
import app.goke.notify.ParcelTasks
import app.goke.ui.Actions
import app.goke.ui.GokeRoot
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.RouteStackHost
import app.goke.ui.Tab
import app.goke.ui.components.AgendaRow
import app.goke.ui.components.AttachmentEditor
import app.goke.ui.components.CuteQrCode
import app.goke.ui.components.ProvideMintModalScope
import app.goke.ui.liveSnapshot
import app.goke.ui.rememberNav
import app.goke.ui.screens.AboutScreen
import app.goke.ui.screens.AppearanceScreen
import app.goke.ui.screens.CalendarScreen
import app.goke.ui.screens.CalendarSyncScreen
import app.goke.ui.screens.ClassroomScreen
import app.goke.ui.screens.CourseEditScreen
import app.goke.ui.screens.CourseSelectionContent
import app.goke.ui.screens.DataScreen
import app.goke.ui.screens.EditTimetableScreen
import app.goke.ui.screens.GradesScreen
import app.goke.ui.screens.HistoryScreen
import app.goke.ui.screens.ImportScreen
import app.goke.ui.screens.JxfwScreen
import app.goke.ui.screens.LanWebScreen
import app.goke.ui.screens.McpScreen
import app.goke.ui.screens.MorningSheet
import app.goke.ui.screens.ParcelScreen
import app.goke.ui.screens.PeriodsScreen
import app.goke.ui.screens.PhysicalTestScreen
import app.goke.ui.screens.PreferencesScreen
import app.goke.ui.screens.ReliabilityScreen
import app.goke.ui.screens.RemindersScreen
import app.goke.ui.screens.RepeatPickDialog
import app.goke.ui.screens.RescheduleSheet
import app.goke.ui.screens.SettingsScreen
import app.goke.ui.screens.SlotDraft
import app.goke.ui.screens.SlotEditorSheet
import app.goke.ui.screens.SyncScreen
import app.goke.ui.screens.TaskBlock
import app.goke.ui.screens.TimetableScreen
import app.goke.ui.screens.TodayScreen
import app.goke.ui.screens.TodoEditScreen
import app.goke.ui.screens.TodoScreen
import app.goke.ui.screens.TrashScreen
import app.goke.ui.screens.WhenPanel
import app.goke.ui.screens.WidgetsScreen
import app.goke.ui.theme.GokeTheme
import app.goke.ui.theme.MintTheme
import app.goke.widget.OverlayTarget
import app.goke.widget.ReminderOverlayContext
import app.goke.widget.WidgetKit
import app.goke.widget.WidgetOverlayRoot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 在 Application.onCreate 之前自动初始化；而应用一启动就排周期同步（GokeApp.onCreate），
 * Robolectric 又会在 @Before 之前泵主线程队列，所以必须在 onCreate 里先装好测试实例。
 */
class SmokeTestApp : GokeApp() {
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

    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

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
            val nowDt = LocalDateTime.now()
            val examStart = if (nowDt.hour < 21) nowDt.plusMinutes(30) else today.atTime(21, 0)
            val examEnd = examStart.plusMinutes(120).let { if (it.toLocalDate() != today) today.atTime(23, 59) else it }
            val examEvent =
                Event(
                    id = Ids.next(),
                    title = "高等数学期末考",
                    start = examStart,
                    end = examEnd,
                    location = "教1-203",
                    note = "座位号 18",
                    kind = EventKind.EXAM,
                    colorIndex = 1,
                    source = Source.MANUAL,
                )
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
            app.repo.commit(
                Edits.saveAll(
                    app.repo.snapshot.value,
                    listOf<app.goke.core.model.Record>(list) + tasks + meeting + exam + examEvent + parcel,
                    "seed",
                    now,
                ),
            )
            val weekStart = SemesterCalendar(sem2).let { sCal -> sCal.weekStart(sCal.weekOf(today).coerceIn(1, sem2.totalWeeks)) }
            app.jxfw.seedClassroomDemoForTest(weekStart)
            app.tzcs.seedPhysicalTestDemoForTest()
        }
    }

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        val st by app.settings.flow.collectAsState()
        val nav = rememberNav()
        val scope = rememberCoroutineScope()
        val actions = remember { Actions(app, scope, nav) }
        GokeTheme(st) {
            CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
                ProvideMintModalScope {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (!nav.onRoute) {
                            content()
                        }
                        RouteStackHost(nav = nav, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun shot(name: String) {
        // CI 不归档截图产物，跳过 PNG 压缩写盘但保留完整渲染校验
        if (System.getenv("CI").equals("true", ignoreCase = true)) {
            rule.onRoot().captureToImage()
            return
        }
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
                "today" to { GokeRoot(app, null, initialTab = Tab.TODAY) {} },
                "timetable" to { GokeRoot(app, null, initialTab = Tab.TIMETABLE) {} },
                "calendar" to { GokeRoot(app, null, initialTab = Tab.CALENDAR) {} },
                "todo" to { GokeRoot(app, null, initialTab = Tab.TODO) {} },
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
                "preferences" to { PreferencesScreen {} },
                "reminders" to { RemindersScreen {} },
                "widgets" to { WidgetsScreen {} },
                "settings" to { GokeRoot(app, null, initialTab = Tab.ME) {} },
                "data" to { DataScreen {} },
                "sync" to { SyncScreen {} },
                "calendar_sync" to { CalendarSyncScreen {} },
                "jxfw" to { JxfwScreen {} },
                "course_selection" to {
                    CourseSelectionContent(
                        loggedIn = true,
                        xkState = demoCourseSelectionStateForTest(),
                        onBack = {},
                        onOpenJxfw = {},
                        onRefresh = {},
                        onSearch = { _, _ -> },
                        onLoadMore = {},
                        onLoadDetail = { Result.failure(IllegalStateException("demo")) },
                        onEnroll = {},
                        onDrop = {},
                    )
                },
                "reliability" to { ReliabilityScreen {} },
                "reschedule_sheet" to { RescheduleSheet(listOf(task), liveSnapshot(), onDismiss = {}) },
                "morning_sheet" to { MorningSheet(listOf(task), java.time.LocalDateTime.now()) {} },
                "import" to { ImportScreen(null) {} },
                "trash" to { TrashScreen {} },
                "history" to { HistoryScreen {} },
                "parcel" to { ParcelScreen {} },
                "about" to { AboutScreen {} },
                "qq_qr" to {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        CuteQrCode("https://qm.qq.com/q/VWUh0uEhKY", "qq", Modifier.size(240.dp))
                    }
                },
                "mcp" to { McpScreen {} },
                "grades" to { GradesScreen {} },
                "classroom" to { ClassroomScreen(onBack = {}) },
                "classroom_grid" to { ClassroomScreen(onBack = {}, initialRoomCode = "011030101") },
                "physical_test" to { PhysicalTestScreen {} },
                "lan_web" to { LanWebScreen {} },
                "root_shell" to { GokeRoot(app, null) {} },
            )
        val idx = mutableIntStateOf(0)
        DebugHooks.suppressUiForShots = true
        rule.setContent { Host { key(idx.intValue) { screens[idx.intValue].second() } } }
        val failures = mutableListOf<String>()
        try {
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
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            val darkTargets =
                listOf(
                    "today" to "today_dark",
                    "timetable" to "timetable_dark",
                    "calendar" to "calendar_dark",
                    "todo" to "todo_dark",
                    "settings" to "settings_dark",
                    "course_edit" to "course_edit_dark",
                    "appearance" to "appearance_dark",
                    "widgets" to "widgets_dark",
                    "data" to "data_dark",
                    "sync" to "sync_dark",
                    "calendar_sync" to "calendar_sync_dark",
                    "jxfw" to "jxfw_dark",
                    "course_selection" to "course_selection_dark",
                    "grades" to "grades_dark",
                    "classroom" to "classroom_dark",
                    "classroom_grid" to "classroom_grid_dark",
                    "physical_test" to "physical_test_dark",
                    "lan_web" to "lan_web_dark",
                    "qq_qr" to "qq_qr_dark",
                )
            darkTargets.forEach { (keyName, shotName) ->
                val targetIdx = screens.indexOfFirst { it.first == keyName }
                rule.runOnIdle { idx.intValue = targetIdx }
                rule.waitForIdle()
                shot(shotName)
            }
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
            DebugHooks.suppressUiForShots = false
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
        // 新建草稿：点返回弹出放弃确认，不落库；取消确认后点顶栏「确定」才落库
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.detail_editor_discard_title)).assertIsDisplayed()
        assertEquals(
            existingIds,
            app.repo.snapshot.value.tasks
                .map { it.id }
                .toSet(),
        )
        rule.onNodeWithText(app.getString(R.string.cancel)).performClick()
        rule.waitForIdle()
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
        lateinit var nav: app.goke.ui.Nav
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
        rule.setContent { Host { SettingsScreen() } }
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
        shot("widgets_bottom")
    }

    @Test
    fun widgetOverlayQuickAddCourseParcelAndTodoWorkInPlace() {
        val snap = app.repo.snapshot.value
        val course = snap.courses.first()
        val parcel = snap.tasks.first { WidgetKit.isParcel(it) && !it.done }
        val todo = snap.tasks.first { !WidgetKit.isParcel(it) && !it.done }
        val target = androidx.compose.runtime.mutableStateOf<OverlayTarget>(OverlayTarget.Add())
        var openedUri: android.net.Uri? = null
        var dismissed = false

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            val curSnap by app.repo.snapshot.collectAsState()
            GokeTheme(st) {
                WidgetOverlayRoot(
                    app = app,
                    ready = true,
                    snap = curSnap,
                    target = target.value,
                    onChangeTarget = { target.value = it },
                    onOpenInApp = { openedUri = it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.waitForIdle()
        shot("overlay_add")

        // 1. 快速添加草稿点放大携带文本与跳转到编辑页，不立即落库
        rule.onNode(hasSetTextAction()).performTextInput("带伞 明天")
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.widget_overlay_expand_app)).performClick()
        assertEquals(DeepLinks.addUri("带伞 明天"), openedUri)

        // 2. 直接提交快速添加落库，不进主应用
        rule.onNodeWithContentDescription(app.getString(R.string.widgets_add_action)).performClick()
        rule.waitUntil(5_000) {
            dismissed &&
                app.repo.snapshot.value.tasks
                    .any { it.title == "带伞" }
        }

        // 3. 课程悬浮卡片：展示详情并可展开至课程详情页
        dismissed = false
        openedUri = null
        rule.runOnIdle { target.value = OverlayTarget.CourseDetail(course.id) }
        rule.waitForIdle()
        shot("overlay_course")
        rule.onNodeWithText(course.name).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.widget_overlay_course_detail)).performClick()
        assertEquals(DeepLinks.courseUri(course.id), openedUri)

        // 4. 快递悬浮卡片：展示取件码并就地标记已取件
        dismissed = false
        rule.runOnIdle { target.value = OverlayTarget.TaskDetail(parcel.id) }
        rule.waitForIdle()
        shot("overlay_parcel")
        rule.onNodeWithText("4-2-1086").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.widget_overlay_parcel_done)).performClick()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .first { it.id == parcel.id }
                .done
        }

        // 5. 待办悬浮卡片：行内修改标题、地点、多行备注与优先级并保存，随后可一键完成
        rule.runOnIdle { target.value = OverlayTarget.TaskDetail(todo.id) }
        rule.waitForIdle()
        shot("overlay_todo")
        val updatedTitle = "${todo.title} 已改"
        val updatedLocation = "教3-204"
        val updatedNote = "第一行备注\n第二行\n第三行\n第四行\n第五行完整显示"
        val nextPriority = if (todo.priority == 2) 3 else 2
        val nextPriorityLabel =
            app.getString(if (nextPriority == 3) R.string.priority_3 else R.string.priority_2)
        rule.onNodeWithText(todo.title).performTextReplacement(updatedTitle)
        rule.onNodeWithContentDescription(app.getString(R.string.field_location)).performTextReplacement(updatedLocation)
        rule.onNodeWithContentDescription(app.getString(R.string.label_note)).performTextReplacement(updatedNote)
        rule.waitForIdle()
        val oldPrioLabel =
            when (todo.priority) {
                3 -> app.getString(R.string.priority_3)
                2 -> app.getString(R.string.priority_2)
                1 -> app.getString(R.string.priority_1)
                else -> app.getString(R.string.label_priority)
            }
        rule.onNodeWithText(oldPrioLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(nextPriorityLabel).performClick()
        rule.waitForIdle()
        dismissed = false
        rule.onNodeWithText(app.getString(R.string.save)).performClick()
        rule.waitUntil(5_000) {
            dismissed &&
                app.repo.snapshot.value.tasks
                    .first { it.id == todo.id }
                    .let {
                        it.title == updatedTitle &&
                            it.extra["location"] == updatedLocation &&
                            it.note == updatedNote &&
                            it.priority == nextPriority
                    }
        }

        // 重新打开后未做新修改，不显示底部「保存」按钮；通过标题勾选框直接完成
        dismissed = false
        rule.runOnIdle { target.value = OverlayTarget.TaskDetail(todo.id) }
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.save)).assertCountEquals(0)
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .first { it.id == todo.id }
                .done
        }

        // 6. 深色模式浮层截图验视
        val anotherTodo =
            app.repo.snapshot.value.tasks
                .first { !WidgetKit.isParcel(it) && !it.done && it.parentId == null }
        try {
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.runOnIdle { target.value = OverlayTarget.Add() }
            rule.waitForIdle()
            shot("overlay_add_dark")
            rule.runOnIdle { target.value = OverlayTarget.TaskDetail(anotherTodo.id) }
            rule.waitForIdle()
            shot("overlay_todo_dark")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
        }
    }

    @Test
    fun scheduledBlockMatchesCourseInAgendaRowAndTimetableProgress() {
        val now = java.time.LocalDateTime.of(2026, 9, 14, 10, 30)
        val today = now.toLocalDate()
        val ongoingTask =
            Task(
                id = "b_ongoing",
                title = "摄影社例会",
                blockStart = today.atTime(10, 0),
                blockMinutes = 60,
                due = today.atTime(11, 0),
                dueHasTime = true,
                extra = mapOf("location" to "艺术楼 210"),
            )
        val finishedTask =
            Task(
                id = "b_finished",
                title = "早晨晨会",
                blockStart = today.atTime(8, 30),
                blockMinutes = 45,
                due = today.atTime(9, 15),
                dueHasTime = true,
            )
        val todoTask =
            Task(
                id = "t_plain",
                title = "交实验报告",
                due = today.atTime(18, 0),
                dueHasTime = true,
            )
        rule.setContent {
            Host {
                Column {
                    AgendaRow(
                        item = BlockItem(ongoingTask, today.atTime(10, 0), today.atTime(11, 0)),
                        now = now,
                        showTeacher = true,
                        onOpenCourse = {},
                        onOpenEvent = {},
                        onOpenTask = {},
                        onToggleTask = { _, _ -> },
                        listColors = mapOf("l1" to 2),
                    )
                    AgendaRow(
                        item = BlockItem(finishedTask, today.atTime(8, 30), today.atTime(9, 15)),
                        now = now,
                        showTeacher = true,
                        onOpenCourse = {},
                        onOpenEvent = {},
                        onOpenTask = {},
                        onToggleTask = { _, _ -> },
                    )
                    AgendaRow(
                        item = TaskItem(todoTask, today.atTime(18, 0), hasTime = true),
                        now = now,
                        showTeacher = true,
                        onOpenCourse = {},
                        onOpenEvent = {},
                        onOpenTask = {},
                        onToggleTask = { _, _ -> },
                    )
                    TaskBlock(
                        task = ongoingTask,
                        colorIndex = 2,
                        start = today.atTime(10, 0),
                        minutes = 60,
                        overlap = 1,
                        phase = TimePhase.ONGOING,
                        progress = 0.4f,
                        colors = MintTheme.colors,
                        titleStyle = MaterialTheme.typography.labelSmall,
                        timeStyle = MaterialTheme.typography.labelSmall,
                        onCommit = { _, _ -> },
                        modifier = Modifier.size(width = 80.dp, height = 64.dp),
                        onClick = {},
                    )
                    TaskBlock(
                        task = finishedTask,
                        colorIndex = 1,
                        start = today.atTime(8, 30),
                        minutes = 45,
                        overlap = 1,
                        phase = TimePhase.FINISHED,
                        progress = 1f,
                        colors = MintTheme.colors,
                        titleStyle = MaterialTheme.typography.labelSmall,
                        timeStyle = MaterialTheme.typography.labelSmall,
                        onCommit = { _, _ -> },
                        modifier = Modifier.size(width = 80.dp, height = 64.dp),
                        onClick = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        // 仅普通待办行渲染勾选框，日程时间块无勾选框
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
        rule.onAllNodes(hasText("10:00")).assertCountEquals(2)
        rule.onNodeWithText("11:00").assertIsDisplayed()
        rule.onNodeWithText("艺术楼 210").assertIsDisplayed()
        rule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    app.getString(R.string.course_progress_ongoing, 50),
                ),
            ).assertIsDisplayed()
        rule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    app.getString(R.string.course_progress_ongoing, 40),
                ),
            ).assertIsDisplayed()
        rule
            .onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    app.getString(R.string.course_progress_finished),
                ),
            ).assertCountEquals(2)
    }

    @Test
    fun overlayCourseNoteTogglesHighlightAndCopiesOnLongPress() {
        val snap = app.repo.snapshot.value
        val sem = snap.currentSemester!!
        val today = LocalDate.now()
        val week = SemesterCalendar(sem).weekOf(today).coerceIn(1, sem.totalWeeks)
        val otherWeek = if (week == 1) 2 else 1
        val trailingLines =
            (3..20)
                .filter { it != week && it != otherWeek }
                .joinToString("\n") { i -> "第${i}周：第${i}周课程拓展内容与实验安排说明" }
        val rawNote =
            listOf(
                "第${otherWeek}周：往周导论内容",
                "第${week}周：本周&ldquo;重点&rdquo;实验",
                "自带实验报告纸",
                trailingLines,
            ).joinToString("\n")
        val decodedNote = rawNote.replace("&ldquo;", "“").replace("&rdquo;", "”")
        val course =
            snap.courses.first().copy(
                id = Ids.next(),
                name = "离散数学实验",
                note = rawNote,
            )
        runBlocking {
            app.repo.commit(ChangeSet(Ids.next(), "seed-note-course", Source.MANUAL, 2L, listOf(Change.Add(course))))
        }

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            val curSnap by app.repo.snapshot.collectAsState()
            GokeTheme(st) {
                WidgetOverlayRoot(
                    app = app,
                    ready = true,
                    snap = curSnap,
                    target = OverlayTarget.CourseDetail(course.id, dateStr = today.toString()),
                    onChangeTarget = {},
                    onOpenInApp = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()

        // 默认仅显示本周与通用备注，且不显示「查看全部备注」按钮，HTML 实体已解码
        rule.onAllNodesWithText("查看全部备注").assertCountEquals(0)
        rule.onNodeWithText("本周“重点”实验\n自带实验报告纸").assertIsDisplayed()

        // 单击备注块展开全部，并高亮当前周目所在行
        rule.onNodeWithText("本周“重点”实验\n自带实验报告纸").performClick()
        rule.waitForIdle()
        val expandedNode = rule.onNodeWithText("往周导论内容", substring = true)
        expandedNode.assertIsDisplayed()
        val annotated = expandedNode.fetchSemanticsNode().config[SemanticsProperties.Text].first()
        val currentLineStart = annotated.text.indexOf("第${week}周：本周“重点”实验")
        assertTrue(currentLineStart >= 0)
        assertTrue(annotated.spanStyles.any { it.start == currentLineStart && it.item.background != androidx.compose.ui.graphics.Color.Unspecified })

        // 超长备注块支持上下滑动
        expandedNode.performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText("第20周：第20周课程拓展内容与实验安排说明", substring = true).assertIsDisplayed()

        // 长按备注块复制全部展示内容
        expandedNode.performTouchInput { longClick() }
        rule.waitForIdle()
        val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(
            decodedNote,
            cm.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString(),
        )

        // 再次单击备注块收起回部分备注
        expandedNode.performClick()
        rule.waitForIdle()
        rule.onNodeWithText("本周“重点”实验\n自带实验报告纸").assertIsDisplayed()
    }

    @Test
    fun agendaRowTimeDoesNotClipAtLargeFontScale() {
        val today = LocalDate.of(2026, 11, 24)
        val now = today.atTime(9, 0)
        val morning =
            Task(
                id = "t_morning",
                title = "数字逻辑与系统设计",
                blockStart = today.atTime(10, 25),
                blockMinutes = 95,
                due = today.atTime(12, 0),
                dueHasTime = true,
            )
        val evening =
            Task(
                id = "t_evening",
                title = "数字逻辑及系统设计实验",
                blockStart = today.atTime(18, 30),
                blockMinutes = 145,
                due = today.atTime(20, 55),
                dueHasTime = true,
            )

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 1.35f)) {
                Host {
                    Column {
                        AgendaRow(
                            item = BlockItem(morning, today.atTime(10, 25), today.atTime(12, 0)),
                            now = now,
                            showTeacher = true,
                            onOpenCourse = {},
                            onOpenEvent = {},
                            onOpenTask = {},
                            onToggleTask = { _, _ -> },
                        )
                        AgendaRow(
                            item = BlockItem(evening, today.atTime(18, 30), today.atTime(20, 55)),
                            now = now,
                            showTeacher = true,
                            onOpenCourse = {},
                            onOpenEvent = {},
                            onOpenTask = {},
                            onToggleTask = { _, _ -> },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        listOf("10:25", "12:00", "18:30", "20:55").forEach { label ->
            val node = rule.onNodeWithText(label).fetchSemanticsNode()
            val action = node.config[SemanticsActions.GetTextLayoutResult]
            val results = mutableListOf<TextLayoutResult>()
            assertTrue("GetTextLayoutResult failed for $label", action.action?.invoke(results) == true)
            assertTrue("Expected TextLayoutResult for $label", results.isNotEmpty())
            val r = results.first()
            assertEquals("Expected single line for $label", 1, r.lineCount)
            assertEquals("Expected full text on line 0 for $label", label.length, r.getLineEnd(0))
            assertTrue(
                "Expected rendered line width (${r.getLineRight(0)}) <= box width (${r.size.width}) for $label",
                r.getLineRight(0) <= r.size.width + 1f,
            )
            assertFalse("Expected no vertical overflow for $label", r.didOverflowHeight)
        }
    }

    /** 补充最近新增功能的深浅截图：待办多图附件网格、课程详情周次备注、课表直接点选调课。 */
    @Test
    fun todoAttachmentsAndCourseDetailShots() {
        val attachments = createDemoAttachmentsForTest(app)
        // 在主线程预热 Robolectric NATIVE 图形库，避免 Dispatchers.IO 首次调用 BitmapFactory 时触发 JniConstants 崩溃
        android.graphics.BitmapFactory
            .decodeFile(File(Attachments.dir(app), "demo_shot_wave.png").path)
            ?.recycle()
        val today = LocalDate.now()
        val richTask =
            Task(
                id = Ids.next(),
                title = "整理物理实验报告",
                note = "附上示波器波形与误差拟合图，按模板排版后提交。",
                priority = 2,
                due = today.atTime(20, 0),
                dueHasTime = true,
                remindMinutes = 15,
                remindMinutesList = listOf(15, 60),
                attachments = attachments,
                extra = mapOf("attachmentColumns" to "2"),
            )
        val labCourse =
            app.repo.snapshot.value.courses
                .first { it.name == "大学物理实验" }
                .copy(
                    note = "第12周：实验九、霍尔效应实验\n第13周：实验十、示波器测量与波形分析（需带坐标纸）\n第14周：实验十一、分光计调整",
                )
        runBlocking {
            app.repo.commit(
                Edits.saveAll(
                    app.repo.snapshot.value,
                    listOf(richTask, labCourse),
                    "seed-extra-shots",
                    System.currentTimeMillis(),
                ),
            )
        }

        val mode = mutableIntStateOf(0)
        rule.setContent {
            Host {
                when (mode.intValue) {
                    0 -> TodoEditScreen(richTask.id) {}
                    else -> GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            listOf("示波器实验波形.png", "数据拟合对比.png").all { label ->
                rule
                    .onAllNodes(hasContentDescription(label))
                    .fetchSemanticsNodes()
                    .any { it.size.width > 120 }
            }
        }
        rule.waitForIdle()
        shot("todo_attachments")

        try {
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("todo_attachments_dark")

            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
            rule.runOnIdle { mode.intValue = 1 }
            rule.waitForIdle()
            rule.onAllNodesWithText("大学物理实验")[0].performClick()
            rule.waitForIdle()
            shot("course_detail")

            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("course_detail_dark")

            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
            rule.waitForIdle()
            rule.onNodeWithText(app.getString(R.string.reschedule)).performClick()
            rule.waitForIdle()
            shot("course_reschedule")

            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("course_reschedule_dark")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
        }
    }

    @Test
    fun reminderNotificationOverlaySupportsQuickSnoozeLinearExponentialAndCustom() {
        val snap = app.repo.snapshot.value
        val todo = snap.tasks.first { !WidgetKit.isParcel(it) && !it.done && it.parentId == null }
        val zone = java.time.ZoneId.systemDefault()
        val closeTargetMs =
            java.time.LocalDateTime
                .now()
                .plusMinutes(25)
                .plusSeconds(20)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val farTargetMs =
            java.time.LocalDateTime
                .now()
                .plusHours(6)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        val target =
            androidx.compose.runtime.mutableStateOf<OverlayTarget>(
                OverlayTarget.TaskDetail(
                    taskId = todo.id,
                    reminder =
                        ReminderOverlayContext(
                            notifId = 9527,
                            title = todo.title,
                            text = app.getString(R.string.remind_task_text),
                            targetAtEpochMs = closeTargetMs,
                        ),
                ),
            )
        var dismissed = false

        rule.setContent {
            val st by app.settings.flow.collectAsState()
            val curSnap by app.repo.snapshot.collectAsState()
            GokeTheme(st) {
                WidgetOverlayRoot(
                    app = app,
                    ready = true,
                    snap = curSnap,
                    target = target.value,
                    onChangeTarget = { target.value = it },
                    onOpenInApp = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.waitForIdle()

        // 1. 距离较近（25 分钟）：线性分段（5/10/15/20 分钟后 + 开始时 + 自定义）
        rule.onNodeWithText(app.getString(R.string.notif_quick_snooze_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_minutes, 5)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_minutes, 10)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_minutes, 15)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_minutes, 20)).assertIsDisplayed()
        shot("overlay_reminder_linear")

        try {
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("overlay_reminder_linear_dark")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
        }
        rule.waitForIdle()

        // 2. 自定义稍后提醒：输入分钟数并确认调度闹钟
        rule.onNodeWithText(app.getString(R.string.detail_editor_remind_custom)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.notif_snooze_custom_minutes_label)).performTextInput("12")
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.ok)).performClick()
        rule.waitUntil(5_000) { dismissed }

        val am = app.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        assertTrue(
            org.robolectric.Shadows
                .shadowOf(am)
                .scheduledAlarms
                .isNotEmpty(),
        )

        // 3. 距离较远（6 小时）：指数分段（15分钟、30分钟、1小时、2小时、4小时）
        dismissed = false
        rule.runOnIdle {
            target.value =
                OverlayTarget.TaskDetail(
                    taskId = todo.id,
                    reminder =
                        ReminderOverlayContext(
                            notifId = 9528,
                            title = todo.title,
                            text = app.getString(R.string.remind_task_text),
                            targetAtEpochMs = farTargetMs,
                        ),
                )
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_minutes, 15)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_hours, 1)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_hours, 4)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.notif_snooze_after_hours, 1)).performClick()
        rule.waitUntil(5_000) { dismissed }
    }

    @Test
    fun examTimetableAndAgendaShots() {
        val snap = app.repo.snapshot.value
        val sem = snap.currentSemester!!
        val cal = SemesterCalendar(sem)
        val curWeek = cal.weekOf(LocalDate.now()).coerceIn(1, sem.totalWeeks)
        val now = System.currentTimeMillis()
        val examCourses =
            listOf(
                Course(
                    id = "exam_pe",
                    semesterId = sem.id,
                    name = "考试：体育（3）",
                    teacher = "李勃",
                    room = "",
                    dayOfWeek = 2,
                    startPeriod = 3,
                    endPeriod = 4,
                    weeks = setOf(curWeek),
                    colorIndex = 2,
                    kind = EventKind.EXAM,
                ),
                Course(
                    id = "exam_prob",
                    semesterId = sem.id,
                    name = "期中考试：概率论",
                    teacher = "王伟",
                    room = "教3-204",
                    dayOfWeek = 3,
                    startPeriod = 3,
                    endPeriod = 4,
                    weeks = setOf(curWeek),
                    colorIndex = 2,
                    note = "大学城校区 · 座位号 18",
                    kind = EventKind.EXAM,
                ),
                Course(
                    id = "exam_ds",
                    semesterId = sem.id,
                    name = "随堂测验：数据结构与算法",
                    teacher = "陈思远",
                    room = "实4-401",
                    dayOfWeek = 4,
                    startPeriod = 3,
                    endPeriod = 4,
                    weeks = setOf(curWeek),
                    colorIndex = 2,
                    kind = EventKind.EXAM,
                ),
                Course(
                    id = "exam_phy",
                    semesterId = sem.id,
                    name = "补考：大学物理（2）",
                    teacher = "张敏",
                    room = "教1-102",
                    dayOfWeek = 5,
                    startPeriod = 1,
                    endPeriod = 1,
                    weeks = setOf(curWeek),
                    colorIndex = 2,
                    note = "座位号 07",
                    kind = EventKind.EXAM,
                ),
            )
        runBlocking {
            val replaceExisting =
                snap.courses
                    .filter {
                        it.semesterId == sem.id &&
                            it.deletedAt == null &&
                            curWeek in it.weeks &&
                            ((it.dayOfWeek in 2..4 && it.startPeriod <= 4 && it.endPeriod >= 3) || (it.dayOfWeek == 5 && it.startPeriod == 1))
                    }.map { Change.Remove(it) }
            app.repo.commit(
                ChangeSet(
                    "exam_shots",
                    "exam_shots",
                    Source.MANUAL,
                    now,
                    replaceExisting + examCourses.map { Change.Add(it) },
                ),
            )
        }

        val page = mutableIntStateOf(0)
        val today = LocalDate.now()
        val occPe =
            CourseOccurrence(
                course = examCourses[0],
                week = curWeek,
                date = today,
                startPeriod = 3,
                endPeriod = 4,
                start = today.atTime(10, 25),
                end = today.atTime(12, 0),
                room = "",
                rescheduled = false,
            )
        val occProb =
            CourseOccurrence(
                course = examCourses[1],
                week = curWeek,
                date = today,
                startPeriod = 3,
                endPeriod = 4,
                start = today.atTime(14, 0),
                end = today.atTime(15, 35),
                room = "教3-204",
                rescheduled = false,
            )
        DebugHooks.suppressUiForShots = true
        try {
            app.settings.update { it.copy(darkMode = DarkMode.LIGHT) }
            rule.setContent {
                Host {
                    when (page.intValue) {
                        0 -> {
                            GokeRoot(app, null, initialTab = Tab.TIMETABLE) {}
                        }

                        else -> {
                            Column(Modifier.padding(16.dp)) {
                                AgendaRow(
                                    item = CourseItem(occPe),
                                    now = today.atTime(9, 0),
                                    showTeacher = true,
                                    onOpenCourse = {},
                                    onOpenEvent = {},
                                    onOpenTask = {},
                                    onToggleTask = { _, _ -> },
                                )
                                AgendaRow(
                                    item = CourseItem(occProb),
                                    now = today.atTime(9, 0),
                                    showTeacher = true,
                                    onOpenCourse = {},
                                    onOpenEvent = {},
                                    onOpenTask = {},
                                    onToggleTask = { _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("考试 体育(3)").assertIsDisplayed()
            rule.onNodeWithContentDescription("期中考试 概率论").assertIsDisplayed()
            rule.onNodeWithContentDescription("随堂测验 数据结构与算法").assertIsDisplayed()
            rule.onNodeWithText("补考").assertIsDisplayed()
            rule.onAllNodesWithText("大学物理(2)")[0].assertIsDisplayed()
            shot("exam_timetable_light")

            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("exam_timetable_dark")

            rule.runOnIdle { page.intValue = 1 }
            rule.waitForIdle()
            rule.onNodeWithText("体育(3)").assertIsDisplayed()
            rule.onNodeWithText("概率论").assertIsDisplayed()
            rule.onNodeWithText("期中考试").assertIsDisplayed()
            rule.onNodeWithText("考试：体育（3）").assertDoesNotExist()
            shot("exam_agenda_dark")

            app.settings.update { it.copy(darkMode = DarkMode.LIGHT) }
            rule.waitForIdle()
            shot("exam_agenda_light")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
            DebugHooks.suppressUiForShots = false
        }
    }
}
