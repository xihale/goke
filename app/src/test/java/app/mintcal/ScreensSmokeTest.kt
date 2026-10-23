package app.mintcal

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.change.Edits
import app.mintcal.core.importer.IcsImporter
import app.mintcal.core.importer.IcsParser
import app.mintcal.core.importer.ImportPlanner
import app.mintcal.core.model.Event
import app.mintcal.core.model.EventKind
import app.mintcal.core.model.Source
import app.mintcal.core.model.Task
import app.mintcal.core.model.TaskList
import app.mintcal.core.util.Ids
import app.mintcal.data.DarkMode
import app.mintcal.data.ThemeSource
import app.mintcal.ui.Actions
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Nav
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
import app.mintcal.ui.screens.TaskDetailScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.theme.MintCalTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 在 JVM 上用 Robolectric 渲染每个页面：确认不崩溃，并输出截图到 app/build/shots 供肉眼检查。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = MintCalApp::class)
class ScreensSmokeTest {
    @get:Rule val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MintCalApp>()

    @Before
    fun seed() = runBlocking {
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
        val tasks = listOf(
            Task(Ids.next(), "交高数作业", listId = list.id, priority = 3, due = today.atTime(18, 0), dueHasTime = true, remindMinutes = 30),
            Task(Ids.next(), "整理笔记", listId = list.id, due = today.plusDays(2).atStartOfDay()),
            Task(Ids.next(), "逾期的任务", due = today.minusDays(1).atTime(12, 0), dueHasTime = true),
            Task(Ids.next(), "排入日历的任务", blockStart = today.atTime(20, 0), blockMinutes = 90),
        )
        val ev = Event(Ids.next(), "社团例会", today.atTime(19, 0), today.atTime(20, 30), location = "活动室", remindMinutes = 15)
        val exam = Event(Ids.next(), "期末考试", today.plusDays(10).atTime(9, 0), today.plusDays(10).atTime(11, 0), kind = EventKind.EXAM)
        app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf<app.mintcal.core.model.Record>(list) + tasks + ev + exam, "seed", now))
    }

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        val st = app.settings.value
        val nav = remember { Nav() }
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val actions = remember { Actions(app, snack, scope, nav) }
        MintCalTheme(st) {
            CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
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
        val first = app.repo.snapshot.value.courses.first()
        val task = app.repo.snapshot.value.tasks.first()
        val event = app.repo.snapshot.value.events.first()
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
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

    @Test
    fun darkThemeRenders() {
        app.settings.update { it.copy(darkMode = DarkMode.DARK, themeSource = ThemeSource.PRESET) }
        val idx = mutableIntStateOf(0)
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
            "dark_today" to { TodayScreen() }, "dark_timetable" to { TimetableScreen() }, "dark_todo" to { TodoScreen() },
        )
        rule.setContent { Host { key(idx.intValue) { screens[idx.intValue].second() } } }
        screens.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { idx.intValue = i }
            rule.waitForIdle()
            shot(name)
        }
    }
}
