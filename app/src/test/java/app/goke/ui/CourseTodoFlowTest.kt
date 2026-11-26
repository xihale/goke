package app.goke.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.model.Course
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.time.SemesterCalendar
import app.goke.core.util.Ids
import app.goke.ui.theme.GokeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class CourseTodoFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    private lateinit var courseA: Course
    private lateinit var courseB: Course
    private lateinit var unboundTask: Task
    private lateinit var datedTask: Task

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
            val snap = app.repo.snapshot.value
            val sem = snap.currentSemester!!
            val cal = SemesterCalendar(sem)
            val today = LocalDate.now()
            val currentWeek = cal.weekOf(today).coerceIn(1, sem.totalWeeks)
            val now = System.currentTimeMillis()
            courseA =
                Course(
                    id = "course-a",
                    semesterId = sem.id,
                    name = "数字逻辑实验",
                    teacher = "王老师",
                    room = "实验1-202",
                    dayOfWeek = today.dayOfWeek.value,
                    startPeriod = 1,
                    endPeriod = 2,
                    weeks = setOf(currentWeek),
                    colorIndex = 1,
                )
            courseB =
                Course(
                    id = "course-b",
                    semesterId = sem.id,
                    name = "信号与系统",
                    teacher = "李老师",
                    room = "教2-301",
                    dayOfWeek = if (today.dayOfWeek.value < 7) today.dayOfWeek.value + 1 else 1,
                    startPeriod = 3,
                    endPeriod = 4,
                    weeks = setOf(currentWeek),
                    colorIndex = 2,
                )
            unboundTask = Task(id = "task-unbound", title = "带万用表")
            datedTask = Task(id = "task-dated", title = "交实验报告", due = today.plusDays(1).atTime(18, 0), dueHasTime = true)
            app.repo.commit(
                ChangeSet(
                    Ids.next(),
                    "seed",
                    Source.MANUAL,
                    now,
                    listOf(
                        Change.Add(courseA),
                        Change.Add(courseB),
                        Change.Add(unboundTask),
                        Change.Add(datedTask),
                    ),
                ),
            )
        }
    }

    @Test
    fun todoEditScreenDisplaysUnifiedCourseLinkRowAndAllowsChangingOrClearingCourse() {
        val existingTask =
            Task(
                id = "task-edit-course",
                title = "预习实验指导书",
                courseId = courseA.id,
                dueBeforeNextClass = true,
            )
        runBlocking {
            app.repo.commit(ChangeSet(Ids.next(), "add", Source.MANUAL, System.currentTimeMillis(), listOf(Change.Add(existingTask))))
        }

        val nav = Nav(initialTab = Tab.TODO)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TODO, nav = nav) {}
            }
        }
        rule.waitForIdle()

        // 1. 新建模式从课程进入：显示统一风格 CourseLinkRow，可切换为另一门课并清除
        rule.runOnUiThread { nav.push(Route.TodoEdit(courseId = courseA.id)) }
        rule.waitForIdle()
        rule.onAllNodesWithText(courseA.name).onLast().assertIsDisplayed()

        rule.onAllNodesWithText(courseA.name).onLast().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(courseB.name).onLast().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(courseB.name).onLast().assertIsDisplayed()

        rule.onNodeWithContentDescription(app.getString(R.string.detail_editor_clear_course)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.quick_add_link_course)).assertIsDisplayed()

        // 返回不留草稿
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()

        // 2. 编辑已有任务：可改绑课程并在返回时自动保存
        rule.runOnUiThread { nav.push(Route.TodoEdit(id = existingTask.id)) }
        rule.waitForIdle()
        rule.onAllNodesWithText(courseA.name).onLast().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(courseB.name).onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitUntil(3_000) {
            app.repo.snapshot.value.tasks
                .firstOrNull { it.id == existingTask.id }
                ?.courseId == courseB.id
        }

        // 3. 再次编辑该任务：可清除课程关联并持久化为 null
        rule.runOnUiThread { nav.push(Route.TodoEdit(id = existingTask.id)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.detail_editor_clear_course)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitUntil(3_000) {
            val saved =
                app.repo.snapshot.value.tasks
                    .firstOrNull { it.id == existingTask.id }
            saved != null && saved.courseId == null && !saved.dueBeforeNextClass
        }
    }

    @Test
    fun courseSheetShowsTodosAndQuickBindsExistingTodoUpdatingTimetableIndicator() {
        val nav = Nav(initialTab = Tab.TIMETABLE)
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.TIMETABLE, nav = nav) {}
            }
        }
        rule.waitForIdle()

        // 点击课表上的课程打开快捷预览 CourseSheet
        rule.onAllNodes(hasText(courseA.name, substring = true)).onFirst().performClick()
        rule.waitForIdle()

        // 进入「关联已有」快捷绑定面板
        rule.onNodeWithText(app.getString(R.string.course_todos_bind)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.course_todos_bind_title)).assertIsDisplayed()

        // 绑定无日期的待办 -> dueBeforeNextClass = true
        rule.onAllNodesWithText(unboundTask.title).onLast().performClick()
        rule.waitUntil(3_000) {
            val saved =
                app.repo.snapshot.value.tasks
                    .firstOrNull { it.id == unboundTask.id }
            saved?.courseId == courseA.id && saved.dueBeforeNextClass
        }

        // 绑定已有具体截止时间的待办 -> 保留原时间，不强覆写 dueBeforeNextClass
        rule.onAllNodesWithText(datedTask.title).onLast().performClick()
        rule.waitUntil(3_000) {
            val saved =
                app.repo.snapshot.value.tasks
                    .firstOrNull { it.id == datedTask.id }
            saved?.courseId == courseA.id && !saved.dueBeforeNextClass
        }

        // 系统返回仅退出「关联已有」，保留 CourseSheet 预览
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onAllNodesWithText(unboundTask.title).onLast().assertIsDisplayed()
        rule.onAllNodesWithText(datedTask.title).onLast().assertIsDisplayed()

        // 取消关联其中一条
        rule.onAllNodes(hasContentDescription(app.getString(R.string.course_todos_unbind))).onFirst().performClick()
        rule.waitUntil(3_000) {
            app.repo.snapshot.value.tasks
                .firstOrNull { it.id == unboundTask.id }
                ?.courseId == null
        }

        // 再次系统返回关闭 CourseSheet 后，课表卡片上应显示剩余 1 项待办的角标提示
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(app.getString(R.string.course_card_todos_desc, 1)).assertIsDisplayed()

        val updatedDated =
            app.repo.snapshot.value.tasks
                .first { it.id == datedTask.id }
        assertEquals(courseA.id, updatedDated.courseId)
        assertFalse(updatedDated.dueBeforeNextClass)
        val updatedUnbound =
            app.repo.snapshot.value.tasks
                .first { it.id == unboundTask.id }
        assertNull(updatedUnbound.courseId)
        assertTrue(!updatedUnbound.dueBeforeNextClass)
    }
}
