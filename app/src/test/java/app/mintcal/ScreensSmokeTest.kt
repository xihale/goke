package app.mintcal

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.importer.IcsParser
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.model.Attachment
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.util.Ids
import app.mintcal.data.Attachments
import app.mintcal.data.DarkMode
import app.mintcal.data.ThemeSource
import app.mintcal.ui.Actions
import app.mintcal.ui.HOME
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.components.AttachmentEditor
import app.mintcal.ui.components.MintSnackbarHost
import app.mintcal.ui.mintDestinations
import app.mintcal.ui.rememberNav
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AdvancedFullScreen
import app.mintcal.ui.screens.AdvancedSheet
import app.mintcal.ui.screens.AiSettingsScreen
import app.mintcal.ui.screens.AppearanceScreen
import app.mintcal.ui.screens.CalendarScreen
import app.mintcal.ui.screens.CourseEditScreen
import app.mintcal.ui.screens.DataScreen
import app.mintcal.ui.screens.EditTimetableScreen
import app.mintcal.ui.screens.EventEditScreen
import app.mintcal.ui.screens.HistoryScreen
import app.mintcal.ui.screens.ImportScreen
import app.mintcal.ui.screens.ParcelScreen
import app.mintcal.ui.screens.PeriodsScreen
import app.mintcal.ui.screens.SettingsScreen
import app.mintcal.ui.screens.JxfwScreen
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TaskSheet
import app.mintcal.ui.screens.TaskDetailScreen
import app.mintcal.ui.screens.TaskDraft
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
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

/** 在 JVM 上用 Robolectric 渲染每个页面：确认不崩溃，并输出截图到 app/build/shots 供肉眼检查。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = MintCalApp::class)
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
            val ev = Event(Ids.next(), "社团例会", today.atTime(19, 0), today.atTime(20, 30), location = "活动室", remindMinutes = 15)
            val exam = Event(Ids.next(), "期末考试", today.plusDays(10).atTime(9, 0), today.plusDays(10).atTime(11, 0), kind = EventKind.EXAM)
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf<app.mintcal.core.model.Record>(list) + tasks + ev + exam, "seed", now))
        }
    }

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        val st by app.settings.flow.collectAsState()
        val nav = rememberNav()
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val actions = remember { Actions(app, snack, scope, nav) }
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
        val event =
            app.repo.snapshot.value.events
                .first()
        val screens: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "today" to { TodayScreen() },
                "timetable" to { TimetableScreen() },
                "calendar" to { CalendarScreen() },
                "todo" to { TodoScreen() },
                "course_edit" to { CourseEditScreen(first.id) {} },
                "course_new" to { CourseEditScreen(null) {} },
                "event_edit" to { EventEditScreen(event.id, null) {} },
                "event_new" to { EventEditScreen(null, null) {} },
                "task_edit" to { TaskDetailScreen(task.id) {} },
                "task_new" to { TaskDetailScreen(null) {} },
                "edit_timetable" to { EditTimetableScreen {} },
                "periods" to { PeriodsScreen {} },
                "appearance" to { AppearanceScreen {} },
                "settings" to { SettingsScreen {} },
                "ai_settings" to { AiSettingsScreen {} },
                "data" to { DataScreen {} },
                "sync" to { SyncScreen {} },
                "jxfw" to { JxfwScreen {} },
                "task_sheet" to { TaskSheet(task.id) {} },
                "import" to { ImportScreen(null) {} },
                "trash" to { TrashScreen {} },
                "history" to { HistoryScreen {} },
                "parcel" to { ParcelScreen {} },
                "about" to { AboutScreen {} },
                "advanced_full" to { AdvancedFullScreen {} },
                "advanced_sheet" to { AdvancedSheet {} },
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
        assertTrue("渲染失败的页面：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** 快速添加：回车即入库并清空输入，可直接接着录下一条。 */
    @Test
    fun quickAddAddsAndKeepsTyping() {
        rule.setContent { Host { TodoScreen() } }
        rule.onNode(hasSetTextAction()).performTextInput("交实验报告 !高 #学习")
        rule.waitForIdle()
        shot("todo_typing")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "交实验报告" }
        }
        val task =
            app.repo.snapshot.value.tasks
                .first { it.title == "交实验报告" }
        assertEquals(3, task.priority)
        rule.waitForIdle()
        // 输入框已清空：标题只剩列表卡片这一处
        rule.onAllNodesWithText("交实验报告").assertCountEquals(1)
        rule.onNode(hasSetTextAction()).performTextInput("买牛奶")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "买牛奶" }
        }
    }

    /** 「更多选项」把快速添加解析出来的内容带进详情页。 */
    @Test
    fun quickAddDraftPrefillsDetail() {
        rule.setContent {
            Host {
                TaskDetailScreen(
                    null,
                    TaskDraft(
                        title = "交实验报告",
                        due = LocalDateTime.of(2026, 10, 25, 18, 0),
                        dueHasTime = true,
                        priority = 3,
                    ),
                ) {}
            }
        }
        rule.onNodeWithText("交实验报告").assertExists()
        rule.onNodeWithText("18:00").assertExists()
        rule.onNodeWithText("高").assertIsSelected()
        rule.onNodeWithText("附件").performScrollTo().assertExists()
        rule.onNodeWithText("图片").assertExists()
        shot("task_attachments")
    }

    @Test
    fun attachmentsEditorRenders() {
        val file = File(Attachments.dir(app), "probe.png")
        file.writeBytes(
            Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                Base64.NO_WRAP,
            ),
        )
        val items = listOf(Attachment("实验图.png", "image/png", "probe.png"), Attachment("报告.pdf", "application/pdf", "gone.pdf"))
        rule.setContent {
            Host {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    AttachmentEditor(items, onChange = {}, onPasteText = {})
                }
            }
        }
        rule.onNodeWithText("实验图.png").assertExists()
        rule.onNodeWithText("报告.pdf").assertExists()
        shot("attachments")
    }

    @Test
    fun snackbarIsCompact() {
        val state = SnackbarHostState()
        rule.setContent {
            Host {
                Box(Modifier.fillMaxSize()) {
                    TodoScreen()
                    LaunchedEffect(Unit) {
                        state.showSnackbar("已删除「交高数作业」", actionLabel = "撤销", withDismissAction = true, duration = SnackbarDuration.Indefinite)
                    }
                    MintSnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 152.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("已删除「交高数作业」").assertExists()
        rule.onNodeWithText("撤销").assertExists()
        shot("snackbar")
        app.settings.update { it.copy(darkMode = DarkMode.DARK, themeSource = ThemeSource.PRESET) }
        rule.waitForIdle()
        shot("snackbar_dark")
    }

    @Test
    fun darkThemeRenders() {
        app.settings.update { it.copy(darkMode = DarkMode.DARK, themeSource = ThemeSource.PRESET) }
        val idx = mutableIntStateOf(0)
        val screens: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "dark_today" to { TodayScreen() },
                "dark_timetable" to { TimetableScreen() },
                "dark_todo" to { TodoScreen() },
            )
        rule.setContent { Host { key(idx.intValue) { screens[idx.intValue].second() } } }
        screens.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { idx.intValue = i }
            rule.waitForIdle()
            shot(name)
        }
    }
}
