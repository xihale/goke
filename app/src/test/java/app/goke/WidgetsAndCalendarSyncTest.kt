package app.goke

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.goke.calendar.CalendarSyncPrefs
import app.goke.calendar.CalendarSyncStore
import app.goke.core.change.Change
import app.goke.core.change.ChangeSet
import app.goke.core.change.Edits
import app.goke.core.model.Course
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.parcel.ParcelInfo
import app.goke.core.sync.CalendarTaskLink
import app.goke.core.sync.CourseExportLink
import app.goke.core.util.Ids
import app.goke.notify.ParcelTasks
import app.goke.ui.screens.WidgetPinOutcome
import app.goke.ui.screens.WidgetSpec
import app.goke.ui.screens.requestPinWidget
import app.goke.ui.screens.totalAddedWidgets
import app.goke.widget.NextWidgetProvider
import app.goke.widget.ParcelWidgetProvider
import app.goke.widget.ParcelWidgetService
import app.goke.widget.ScheduleWidgetProvider
import app.goke.widget.TodayWidgetProvider
import app.goke.widget.TodayWidgetService
import app.goke.widget.TodoWidgetProvider
import app.goke.widget.TodoWidgetService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class WidgetsAndCalendarSyncTest {
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    private fun renderRemoteViews(
        name: String,
        rv: RemoteViews,
        widthDp: Int,
        heightDp: Int,
        listViewId: Int? = null,
        emptyViewId: Int? = null,
        items: List<RemoteViews> = emptyList(),
    ): View {
        val density = app.resources.displayMetrics.density
        val wPx = (widthDp * density).toInt()
        val hPx = (heightDp * density).toInt()
        val host = FrameLayout(app)
        val view = rv.apply(app, host)
        if (listViewId != null) {
            val lv = view.findViewById<ListView>(listViewId)
            emptyViewId?.let { eid -> view.findViewById<View>(eid)?.let { lv.emptyView = it } }
            lv.adapter =
                object : BaseAdapter() {
                    override fun getCount(): Int = items.size

                    override fun getItem(position: Int): Any = items[position]

                    override fun getItemId(position: Int): Long = position.toLong()

                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ): View = items[position].apply(app, parent)
                }
        }
        host.addView(view, FrameLayout.LayoutParams(wPx, hPx))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, wPx, hPx)
        // ListView 需要二次 measure/layout 才能在离线 Canvas 绘制子项
        host.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, wPx, hPx)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        host.draw(canvas)
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return view
    }

    @Test
    fun allFiveWidgetsInflateAndExtractDataCleanly(): Unit =
        runBlocking {
            app.repo.load()
            val today = LocalDate.of(2026, 9, 14) // 周一
            val now = LocalDateTime.of(today, java.time.LocalTime.of(8, 45))
            val snap0 = app.repo.snapshot.value
            val sem0 = snap0.currentSemester!!
            val sem = sem0.copy(week1Monday = today.minusWeeks(2).with(DayOfWeek.MONDAY))
            val ts = System.currentTimeMillis()
            app.repo.commit(ChangeSet("sem", "sem", Source.MANUAL, ts, listOf(Change.Modify(sem0, sem))))

            val course1 =
                Course(
                    id = Ids.next(),
                    semesterId = sem.id,
                    name = "高等数学",
                    room = "教3-204",
                    teacher = "林老师",
                    dayOfWeek = 1,
                    startPeriod = 1,
                    endPeriod = 2,
                    weeks = (1..16).toSet(),
                    colorIndex = 0,
                )
            val course2 =
                Course(
                    id = Ids.next(),
                    semesterId = sem.id,
                    name = "操作系统",
                    room = "实验楼B301",
                    teacher = "陈老师",
                    dayOfWeek = 1,
                    startPeriod = 5,
                    endPeriod = 6,
                    weeks = (1..16).toSet(),
                    colorIndex = 1,
                )
            val parcel =
                ParcelTasks.build(
                    app,
                    ParcelInfo(
                        code = "4-2-1086",
                        place = "东门菜鸟驿站",
                        company = "顺丰",
                        deadline = today.atTime(22, 0),
                        raw = "【菜鸟驿站】取件码4-2-1086",
                        ruleId = "builtin",
                    ),
                )
            val parcel2 =
                ParcelTasks.build(
                    app,
                    ParcelInfo(
                        code = "7-1-3209",
                        place = "南区邮政驿站",
                        company = "京东",
                        deadline = today.atTime(21, 0),
                        raw = "【邮政驿站】取件码7-1-3209",
                        ruleId = "builtin",
                    ),
                )
            val task =
                Task(
                    id = Ids.next(),
                    title = "提交操作系统实验报告",
                    priority = 3,
                    due = today.atTime(18, 0),
                    dueHasTime = true,
                )
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(course1, course2, parcel, parcel2, task), "seed-widgets", ts))

            val snap = app.repo.snapshot.value
            val st = app.settings.value

            // 1. 待取快递小组件
            val parcelRows = ParcelWidgetProvider.rows(app, now)
            assertEquals(2, parcelRows.size)
            assertTrue(parcelRows.any { it.code == "4-2-1086" && it.place == "东门菜鸟驿站 · 顺丰" })
            val parcelView =
                renderRemoteViews(
                    name = "widget_parcel",
                    rv = ParcelWidgetProvider.build(app, 1, now),
                    widthDp = 260,
                    heightDp = 185,
                    listViewId = R.id.parcel_list,
                    emptyViewId = R.id.parcel_empty,
                    items = parcelRows.map { ParcelWidgetService.itemView(app, it) },
                )
            assertEquals("2", parcelView.findViewById<TextView>(R.id.parcel_count).text.toString())

            // 2. 待办小组件（含取件码胶囊提取）
            val todoRows = TodoWidgetProvider.rows(app, now)
            assertTrue(todoRows.any { it.code == "4-2-1086" && it.title == "东门菜鸟驿站 · 顺丰" })
            assertTrue(todoRows.any { it.title == "提交操作系统实验报告" && it.code == null })
            renderRemoteViews(
                name = "widget_todo",
                rv = TodoWidgetProvider.build(app, 2, now),
                widthDp = 260,
                heightDp = 235,
                listViewId = R.id.widget_list,
                emptyViewId = R.id.widget_empty,
                items = todoRows.map { TodoWidgetService.itemView(app, it) },
            )

            // 3. 今日安排统一小组件（含课程 ID 直达悬浮详情）
            val todayRows = TodayWidgetProvider.buildRows(app, snap, st, now)
            assertTrue(todayRows.any { it.title == "高等数学" && it.ongoing && it.courseId == course1.id })
            assertTrue(todayRows.any { it.code == "4-2-1086" })
            assertTrue(todayRows.any { it.title == "提交操作系统实验报告" })
            val todayView =
                renderRemoteViews(
                    name = "widget_today",
                    rv = TodayWidgetProvider.build(app, 3, snap, st, now),
                    widthDp = 320,
                    heightDp = 270,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = todayRows.map { TodayWidgetService.itemView(app, it) },
                )
            assertEquals("第 3 周", todayView.findViewById<TextView>(R.id.today_week).text.toString())
            assertEquals(null, todayView.findViewById<TextView>(R.id.today_week).background)
            assertTrue(todayView.findViewById<View>(R.id.widget_root).hasOnClickListeners())
            assertTrue(todayView.findViewById<View>(R.id.today_empty).hasOnClickListeners())

            // 4. 今日课表小组件（含无课日点击背景直达课表）
            val schedView = renderRemoteViews("widget_schedule", ScheduleWidgetProvider.build(app, snap, st, now), 320, 210)
            assertEquals("第 3 周", schedView.findViewById<TextView>(R.id.sched_week).text.toString())
            assertEquals(null, schedView.findViewById<TextView>(R.id.sched_week).background)
            assertEquals("高等数学", schedView.findViewById<TextView>(R.id.sched_name0).text.toString())
            assertTrue(schedView.findViewById<View>(R.id.widget_root).hasOnClickListeners())
            val emptySchedView = renderRemoteViews("widget_schedule_empty", ScheduleWidgetProvider.build(app, snap, st, now.plusDays(1)), 320, 210)
            assertTrue(emptySchedView.findViewById<View>(R.id.sched_empty).hasOnClickListeners())
            assertTrue(emptySchedView.findViewById<View>(R.id.widget_root).hasOnClickListeners())

            // 5. 下一件事 4×1 胶囊小组件 + 深链路由
            val nextSummary = NextWidgetProvider.pickSummary(app, snap, st, now)
            assertEquals(course1.id, nextSummary.courseId)
            assertTrue(nextSummary.ongoing)
            assertTrue(nextSummary.progress > 0f && nextSummary.progress < 1f)
            val nextView = renderRemoteViews("widget_next", NextWidgetProvider.build(app, snap, st, now), 340, 68)
            assertEquals("–10:05", nextView.findViewById<TextView>(R.id.next_badge).text.toString())
            assertEquals(View.VISIBLE, nextView.findViewById<ProgressBar>(R.id.next_progress).visibility)
            assertEquals("高等数学", nextView.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals(DeepLink.Course("c1"), DeepLinks.parseUri(DeepLinks.courseUri("c1")))
            assertEquals(DeepLink.Add("买牛奶", "c1"), DeepLinks.parseUri(DeepLinks.addUri("买牛奶", "c1")))

            // 6. 两格窄宽（compact = true）与已上课程直接消失 / 进行中渐进置灰验证
            val midDay = LocalDateTime.of(today, java.time.LocalTime.of(14, 45)) // course1 已结束消失，course2 进行中
            val schedMid = renderRemoteViews("widget_schedule_progress", ScheduleWidgetProvider.build(app, snap, st, midDay), 320, 210)
            assertEquals("操作系统", schedMid.findViewById<TextView>(R.id.sched_name0).text.toString())
            assertEquals(View.VISIBLE, schedMid.findViewById<ProgressBar>(R.id.sched_progress0).visibility)
            assertTrue(schedMid.findViewById<ProgressBar>(R.id.sched_progress0).progress in 40..980)
            assertEquals(View.GONE, schedMid.findViewById<View>(R.id.sched_row1).visibility)

            val schedCompact =
                renderRemoteViews("widget_schedule_2cell", ScheduleWidgetProvider.build(app, snap, st, midDay, compact = true), 140, 210)
            assertEquals(View.GONE, schedCompact.findViewById<View>(R.id.sched_week).visibility)
            assertEquals(View.GONE, schedCompact.findViewById<View>(R.id.sched_time_col0).visibility)

            val todayMidRows = TodayWidgetProvider.buildRows(app, snap, st, midDay)
            assertTrue(todayMidRows.none { it.title == "高等数学" })
            assertTrue(todayMidRows.any { it.title == "操作系统" && it.ongoing && !it.finished && it.progress > 0f })
            val todayCompact =
                renderRemoteViews(
                    name = "widget_today_2cell",
                    rv = TodayWidgetProvider.build(app, 3, snap, st, midDay, compact = true),
                    widthDp = 140,
                    heightDp = 240,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = todayMidRows.map { TodayWidgetService.itemView(app, it, compact = true) },
                )
            assertEquals(View.GONE, todayCompact.findViewById<View>(R.id.today_week).visibility)

            val nextCompact =
                renderRemoteViews("widget_next_2cell", NextWidgetProvider.build(app, snap, st, midDay, compact = true), 140, 68)
            assertEquals(View.GONE, nextCompact.findViewById<View>(R.id.next_add).visibility)
            assertEquals(View.GONE, nextCompact.findViewById<View>(R.id.next_bar).visibility)
            assertEquals(View.VISIBLE, nextCompact.findViewById<ProgressBar>(R.id.next_progress).visibility)

            val parcelCompact =
                renderRemoteViews(
                    name = "widget_parcel_2cell",
                    rv = ParcelWidgetProvider.build(app, 1, now, compact = true),
                    widthDp = 140,
                    heightDp = 185,
                    listViewId = R.id.parcel_list,
                    emptyViewId = R.id.parcel_empty,
                    items = parcelRows.map { ParcelWidgetService.itemView(app, it) },
                )
            assertEquals(app.getString(R.string.widget_parcel_title), parcelCompact.findViewById<TextView>(R.id.parcel_title).text.toString())
            Unit
        }

    @Test
    fun calendarSyncStoreRoundTripsAndNoPermissionSyncIsSafe(): Unit =
        runBlocking {
            val store = CalendarSyncStore(app)
            store.writePrefs(
                CalendarSyncPrefs(
                    enabled = true,
                    selectedCalendarIds = setOf(11L, 22L),
                    primaryCalendarId = 11L,
                    exportCourses = true,
                    lastSyncAt = 123456L,
                ),
            )
            val readPrefs = store.readPrefs()
            assertTrue(readPrefs.enabled)
            assertEquals(setOf(11L, 22L), readPrefs.selectedCalendarIds)
            assertEquals(11L, readPrefs.primaryCalendarId)
            assertTrue(readPrefs.exportCourses)

            store.saveTaskLinks(listOf(CalendarTaskLink("t1", 11L, 99L, "th1", "eh1", 1000L)))
            assertEquals(1, store.loadTaskLinks().size)
            assertEquals(99L, store.loadTaskLinks()[0].eventId)

            store.saveCourseLinks(listOf(CourseExportLink("c1_1", 11L, 200L, "ch1")))
            assertEquals(1, store.loadCourseLinks().size)
            assertEquals(200L, store.loadCourseLinks()[0].eventId)

            // 未授予日历权限时，syncNow 应安全返回 0 不崩溃
            assertEquals(0, app.calendarSync.syncNow())
        }

    @Test
    fun widgetSpecsCoverAllProvidersAndPinHelperDegradesGracefully(): Unit =
        runBlocking {
            app.repo.load()
            assertEquals(
                setOf(
                    TodayWidgetProvider::class.java,
                    ScheduleWidgetProvider::class.java,
                    TodoWidgetProvider::class.java,
                    ParcelWidgetProvider::class.java,
                    NextWidgetProvider::class.java,
                ),
                WidgetSpec.entries.map { it.providerClass }.toSet(),
            )
            assertEquals(0, totalAddedWidgets(app))
            for (spec in WidgetSpec.entries) {
                assertEquals(WidgetPinOutcome.UNSUPPORTED, requestPinWidget(app, spec))
            }
        }
}
