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
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
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
import app.mintcal.ui.liveSnapshot
import app.mintcal.ui.mintDestinations
import app.mintcal.ui.rememberNav
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AdvancedFullScreen
import app.mintcal.ui.screens.AdvancedSheet
import app.mintcal.ui.screens.AppearanceScreen
import app.mintcal.ui.screens.CalendarScreen
import app.mintcal.ui.screens.CourseEditScreen
import app.mintcal.ui.screens.DataScreen
import app.mintcal.ui.screens.EditTimetableScreen
import app.mintcal.ui.screens.HistoryScreen
import app.mintcal.ui.screens.ImportScreen
import app.mintcal.ui.screens.JxfwScreen
import app.mintcal.ui.screens.MorningSheet
import app.mintcal.ui.screens.ParcelScreen
import app.mintcal.ui.screens.PeriodsScreen
import app.mintcal.ui.screens.ReliabilityScreen
import app.mintcal.ui.screens.RescheduleSheet
import app.mintcal.ui.screens.SettingsScreen
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoEditScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.screens.WhenPanel
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
            val meeting = Task(Ids.next(), "社团例会", listId = list.id, blockStart = today.atTime(19, 0), blockMinutes = 90, remindMinutes = 15)
            val exam = Task(Ids.next(), "考试：期末考试", blockStart = today.plusDays(10).atTime(9, 0), blockMinutes = 120, tags = listOf("考试"))
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf<app.mintcal.core.model.Record>(list) + tasks + meeting + exam, "seed", now))
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
                "task_edit" to { TodoEditScreen(task.id) {} },
                "task_new" to { TodoEditScreen(null) {} },
                "time_sheet" to { WhenPanel(null, LocalDate.now(), 30, onDismiss = {}, onPick = {}) },
                "edit_timetable" to { EditTimetableScreen {} },
                "periods" to { PeriodsScreen {} },
                "appearance" to { AppearanceScreen {} },
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
        // 收起即结算：提交后浮层关闭，原文不再出现在任何节点
        rule.onAllNodesWithText("交实验报告 !高 #学习").assertCountEquals(0)
        // 没有确认键：再开浮层，键盘完成直接提交第二条
        rule.onNodeWithContentDescription(app.getString(R.string.add)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performTextInput("买牛奶")
        rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done)).performImeAction()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.title == "买牛奶" }
        }
    }

    /** 「更多选项」把快速添加解析出来的内容带进编辑页：文本里的时刻是待办截止，不带持续。 */
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
        // 编辑页没有独立的保存键：点返回即静默落盘（空标题才等于放弃）
        rule.onNodeWithContentDescription("返回").performClick()
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
        // 确认对话框的删除按钮
        rule.onNodeWithText("删除").performClick()
        rule.waitUntil(5_000) {
            app.repo.snapshot.value.tasks
                .any { it.id == task.id && it.deletedAt != null }
        }
        rule.waitForIdle()
        rule.onNodeWithText("主页").assertIsDisplayed()
    }
}
