package app.goke

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.SizeF
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
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.core.model.Task
import app.goke.core.model.TaskList
import app.goke.core.parcel.ParcelInfo
import app.goke.core.sync.CalendarTaskLink
import app.goke.core.sync.CourseExportLink
import app.goke.core.util.Ids
import app.goke.data.Settings
import app.goke.data.SettingsStore
import app.goke.notify.ParcelTasks
import app.goke.notify.ReminderScheduler
import app.goke.ui.screens.WidgetPinOutcome
import app.goke.ui.screens.WidgetSpec
import app.goke.ui.screens.requestPinWidget
import app.goke.ui.screens.totalAddedWidgets
import app.goke.widget.HotWaterWidgetProvider
import app.goke.widget.NextWidgetProvider
import app.goke.widget.ParcelWidgetProvider
import app.goke.widget.ParcelWidgetService
import app.goke.widget.ScheduleWidgetProvider
import app.goke.widget.ScheduleWidgetService
import app.goke.widget.TodayWidgetProvider
import app.goke.widget.TodayWidgetService
import app.goke.widget.TodoWidgetProvider
import app.goke.widget.TodoWidgetService
import app.goke.widget.WidgetKit
import app.goke.widget.WidgetOverlayActivity
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
            assertTrue(todayRows.any { it.title == "高等数学" && it.ongoing && it.courseId == course1.id && it.endTime != null })
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
            val todayCourseItem = todayView.findViewById<ListView>(R.id.today_list).getChildAt(0)
            assertEquals(View.VISIBLE, todayCourseItem.findViewById<View>(R.id.today_item_end).visibility)
            assertTrue(todayView.findViewById<View>(R.id.widget_root).hasOnClickListeners())
            assertTrue(todayView.findViewById<View>(R.id.today_empty).hasOnClickListeners())

            // 4. 今日课表小组件（可滚动列表 + 无课日点击背景直达课表）
            val schedRows = ScheduleWidgetProvider.buildRows(snap, st, now)
            assertEquals(2, schedRows.size)
            val schedView =
                renderRemoteViews(
                    name = "widget_schedule",
                    rv = ScheduleWidgetProvider.build(app, 4, snap, st, now),
                    widthDp = 320,
                    heightDp = 210,
                    listViewId = R.id.sched_list,
                    emptyViewId = R.id.sched_empty,
                    items = schedRows.map { ScheduleWidgetService.itemView(app, it) },
                )
            assertEquals("第 3 周", schedView.findViewById<TextView>(R.id.sched_week).text.toString())
            assertEquals(null, schedView.findViewById<TextView>(R.id.sched_week).background)
            val schedFirstItem = schedView.findViewById<ListView>(R.id.sched_list).getChildAt(0)
            assertEquals("高等数学", schedFirstItem.findViewById<TextView>(R.id.sched_item_name).text.toString())
            assertTrue(schedView.findViewById<View>(R.id.widget_root).hasOnClickListeners())
            val emptySchedRows = ScheduleWidgetProvider.buildRows(snap, st, now.plusDays(1))
            assertTrue(emptySchedRows.isEmpty())
            val emptySchedView =
                renderRemoteViews(
                    name = "widget_schedule_empty",
                    rv = ScheduleWidgetProvider.build(app, 4, snap, st, now.plusDays(1)),
                    widthDp = 320,
                    heightDp = 210,
                    listViewId = R.id.sched_list,
                    emptyViewId = R.id.sched_empty,
                    items = emptySchedRows.map { ScheduleWidgetService.itemView(app, it) },
                )
            assertTrue(emptySchedView.findViewById<View>(R.id.sched_empty).hasOnClickListeners())
            assertTrue(emptySchedView.findViewById<View>(R.id.widget_root).hasOnClickListeners())

            // 5. 下一件事 4×1 胶囊小组件 + 深链路由
            val nextSummary = NextWidgetProvider.pickSummary(app, snap, st, now)
            assertEquals(course1.id, nextSummary.courseId)
            assertTrue(nextSummary.ongoing)
            assertTrue(nextSummary.progress > 0f && nextSummary.progress < 1f)
            val nextView = renderRemoteViews("widget_next", NextWidgetProvider.build(app, snap, st, now), 340, 68)
            assertEquals(View.GONE, nextView.findViewById<View>(R.id.next_badge).visibility)
            assertEquals(View.VISIBLE, nextView.findViewById<View>(R.id.next_time_col).visibility)
            assertEquals("08:30", nextView.findViewById<TextView>(R.id.next_time).text.toString())
            assertEquals("10:05", nextView.findViewById<TextView>(R.id.next_end).text.toString())
            assertEquals("教3-204", nextView.findViewById<TextView>(R.id.next_sub).text.toString())
            assertEquals(View.VISIBLE, nextView.findViewById<ProgressBar>(R.id.next_progress).visibility)
            assertEquals("高等数学", nextView.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals(DeepLink.Course("c1"), DeepLinks.parseUri(DeepLinks.courseUri("c1")))
            assertEquals(DeepLink.Add("买牛奶", "c1"), DeepLinks.parseUri(DeepLinks.addUri("买牛奶", "c1")))

            // 6. 两格窄宽（compactWidth）与 1×1 / 4×1 极小尺寸自适应验证
            val compactSize = WidgetKit.SizeClass(compactWidth = true)
            val oneByOneSize = WidgetKit.SizeClass(compactWidth = true, ultraCompactWidth = true, compactHeight = true)
            val midDay = LocalDateTime.of(today, java.time.LocalTime.of(14, 45)) // course1 已结束消失，course2 进行中
            val schedMidRows = ScheduleWidgetProvider.buildRows(snap, st, midDay)
            assertEquals(1, schedMidRows.size)
            val schedMid =
                renderRemoteViews(
                    name = "widget_schedule_progress",
                    rv = ScheduleWidgetProvider.build(app, 4, snap, st, midDay),
                    widthDp = 320,
                    heightDp = 210,
                    listViewId = R.id.sched_list,
                    emptyViewId = R.id.sched_empty,
                    items = schedMidRows.map { ScheduleWidgetService.itemView(app, it) },
                )
            val schedMidItem = schedMid.findViewById<ListView>(R.id.sched_list).getChildAt(0)
            assertEquals("操作系统", schedMidItem.findViewById<TextView>(R.id.sched_item_name).text.toString())
            assertEquals(View.VISIBLE, schedMidItem.findViewById<ProgressBar>(R.id.sched_item_progress).visibility)
            assertTrue(schedMidItem.findViewById<ProgressBar>(R.id.sched_item_progress).progress in 40..980)

            val schedCompact =
                renderRemoteViews(
                    name = "widget_schedule_2cell",
                    rv = ScheduleWidgetProvider.build(app, 4, snap, st, midDay, size = compactSize),
                    widthDp = 140,
                    heightDp = 210,
                    listViewId = R.id.sched_list,
                    emptyViewId = R.id.sched_empty,
                    items = schedMidRows.map { ScheduleWidgetService.itemView(app, it, compact = true) },
                )
            assertEquals(View.GONE, schedCompact.findViewById<View>(R.id.sched_week).visibility)
            assertEquals(
                View.GONE,
                schedCompact
                    .findViewById<ListView>(R.id.sched_list)
                    .getChildAt(0)
                    .findViewById<View>(R.id.sched_item_time_col)
                    .visibility,
            )
            assertTrue(
                schedCompact
                    .findViewById<ListView>(R.id.sched_list)
                    .getChildAt(0)
                    .findViewById<TextView>(R.id.sched_item_meta)
                    .text
                    .toString()
                    .contains("–"),
            )

            val oneRowSize = WidgetKit.SizeClass(compactHeight = true)
            val schedOneRow =
                renderRemoteViews(
                    "widget_schedule_4x1",
                    ScheduleWidgetProvider.build(app, 4, snap, st, midDay, size = oneRowSize),
                    320,
                    68,
                )
            assertEquals("操作系统", schedOneRow.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals("13:50", schedOneRow.findViewById<TextView>(R.id.next_time).text.toString())
            assertEquals("15:25", schedOneRow.findViewById<TextView>(R.id.next_end).text.toString())
            assertEquals(View.GONE, schedOneRow.findViewById<View>(R.id.next_badge).visibility)

            val schedOneByOne =
                renderRemoteViews(
                    "widget_schedule_1x1",
                    ScheduleWidgetProvider.build(app, 4, snap, st, midDay, size = oneByOneSize),
                    72,
                    72,
                )
            assertEquals("操作系统", schedOneByOne.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals("13:50–15:25", schedOneByOne.findViewById<TextView>(R.id.next_time_inline).text.toString())
            assertEquals(View.GONE, schedOneByOne.findViewById<View>(R.id.next_badge).visibility)

            val oneByTwoSize = WidgetKit.SizeClass(compactWidth = true, ultraCompactWidth = true, compactHeight = false)
            val nextOneByTwo =
                renderRemoteViews(
                    "widget_next_1x2",
                    NextWidgetProvider.build(app, snap, st, now, size = oneByTwoSize),
                    72,
                    152,
                )
            assertEquals("高等数学", nextOneByTwo.findViewById<TextView>(R.id.next_title0).text.toString())
            assertEquals("08:30–10:05", nextOneByTwo.findViewById<TextView>(R.id.next_time0).text.toString())
            assertEquals(View.VISIBLE, nextOneByTwo.findViewById<View>(R.id.next_card1).visibility)
            assertEquals("操作系统", nextOneByTwo.findViewById<TextView>(R.id.next_title1).text.toString())
            assertEquals("13:50–15:25", nextOneByTwo.findViewById<TextView>(R.id.next_time1).text.toString())

            val todayMidRows = TodayWidgetProvider.buildRows(app, snap, st, midDay)
            assertTrue(todayMidRows.none { it.title == "高等数学" })
            assertTrue(todayMidRows.any { it.title == "操作系统" && it.ongoing && !it.finished && it.progress > 0f })
            val todayCompact =
                renderRemoteViews(
                    name = "widget_today_2cell",
                    rv = TodayWidgetProvider.build(app, 3, snap, st, midDay, size = compactSize),
                    widthDp = 140,
                    heightDp = 240,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = todayMidRows.map { TodayWidgetService.itemView(app, it, compact = true) },
                )
            assertEquals(View.GONE, todayCompact.findViewById<View>(R.id.today_week).visibility)
            assertTrue(
                todayCompact
                    .findViewById<ListView>(R.id.today_list)
                    .getChildAt(0)
                    .findViewById<TextView>(R.id.today_item_sub)
                    .text
                    .toString()
                    .contains("–"),
            )

            val todayOneByOne =
                renderRemoteViews(
                    "widget_today_1x1",
                    TodayWidgetProvider.build(app, 3, snap, st, midDay, size = oneByOneSize),
                    72,
                    72,
                )
            assertEquals("操作系统", todayOneByOne.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals(View.GONE, todayOneByOne.findViewById<View>(R.id.next_badge).visibility)

            val nextCompact =
                renderRemoteViews(
                    "widget_next_2cell",
                    NextWidgetProvider.build(app, snap, st, midDay, size = compactSize),
                    140,
                    68,
                )
            assertEquals(View.GONE, nextCompact.findViewById<View>(R.id.next_add).visibility)
            assertEquals(View.GONE, nextCompact.findViewById<View>(R.id.next_bar).visibility)
            assertEquals(View.GONE, nextCompact.findViewById<View>(R.id.next_time_col).visibility)
            assertEquals("实验楼B301 · 13:50–15:25", nextCompact.findViewById<TextView>(R.id.next_sub).text.toString())
            assertEquals(View.VISIBLE, nextCompact.findViewById<ProgressBar>(R.id.next_progress).visibility)

            val nextOneByOne =
                renderRemoteViews(
                    "widget_next_1x1",
                    NextWidgetProvider.build(app, snap, st, midDay, size = oneByOneSize),
                    72,
                    72,
                )
            assertEquals(View.GONE, nextOneByOne.findViewById<View>(R.id.next_badge).visibility)

            val parcelCompact =
                renderRemoteViews(
                    name = "widget_parcel_2cell",
                    rv = ParcelWidgetProvider.build(app, 1, now, size = compactSize),
                    widthDp = 140,
                    heightDp = 185,
                    listViewId = R.id.parcel_list,
                    emptyViewId = R.id.parcel_empty,
                    items = parcelRows.map { ParcelWidgetService.itemView(app, it) },
                )
            assertEquals(app.getString(R.string.widget_parcel_title), parcelCompact.findViewById<TextView>(R.id.parcel_title).text.toString())

            val parcelOneByOne =
                renderRemoteViews(
                    "widget_parcel_1x1",
                    ParcelWidgetProvider.build(app, 1, now, size = oneByOneSize),
                    72,
                    72,
                )
            assertEquals("高等数学", parcelOneByOne.findViewById<TextView>(R.id.next_title).text.toString())

            val todoOneByOne =
                renderRemoteViews(
                    "widget_todo_1x1",
                    TodoWidgetProvider.build(app, 2, now, size = oneByOneSize),
                    72,
                    72,
                )
            assertEquals("高等数学", todoOneByOne.findViewById<TextView>(R.id.next_title).text.toString())

            val nextTwoByTwo =
                renderRemoteViews(
                    "widget_next_2x2",
                    NextWidgetProvider.build(app, snap, st, now, size = WidgetKit.sizeClassFor(160, 192)),
                    160,
                    192,
                )
            assertEquals("高等数学", nextTwoByTwo.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals(View.VISIBLE, nextTwoByTwo.findViewById<View>(R.id.next_upcoming_group).visibility)
            assertEquals("操作系统", nextTwoByTwo.findViewById<TextView>(R.id.next_row1_title).text.toString())
            assertEquals(View.GONE, nextTwoByTwo.findViewById<View>(R.id.next_row2).visibility)

            val nextThreeByThree =
                renderRemoteViews(
                    "widget_next_3x3",
                    NextWidgetProvider.build(app, snap, st, now, size = WidgetKit.sizeClassFor(250, 290)),
                    250,
                    290,
                )
            assertEquals("高等数学", nextThreeByThree.findViewById<TextView>(R.id.next_title).text.toString())
            assertEquals("操作系统", nextThreeByThree.findViewById<TextView>(R.id.next_row1_title).text.toString())
            assertEquals(View.VISIBLE, nextThreeByThree.findViewById<View>(R.id.next_row2).visibility)
            assertEquals("东门菜鸟驿站 · 顺丰", nextThreeByThree.findViewById<TextView>(R.id.next_row2_title).text.toString())

            val nextOneByThree =
                renderRemoteViews(
                    "widget_next_1x3",
                    NextWidgetProvider.build(app, snap, st, now, size = WidgetKit.sizeClassFor(72, 260)),
                    72,
                    260,
                )
            assertEquals(View.VISIBLE, nextOneByThree.findViewById<View>(R.id.next_card0).visibility)
            assertEquals(View.VISIBLE, nextOneByThree.findViewById<View>(R.id.next_card1).visibility)
            assertEquals(View.VISIBLE, nextOneByThree.findViewById<View>(R.id.next_card2).visibility)
            assertEquals("东门菜鸟驿站 · 顺丰", nextOneByThree.findViewById<TextView>(R.id.next_title2).text.toString())

            val sampleTaskRow =
                TodayWidgetProvider.Row(
                    key = "t_sample",
                    time = "待办",
                    title = "提交报告",
                    sub = null,
                    code = null,
                    colorIndex = -1,
                    priority = 1,
                    overdue = false,
                    ongoing = false,
                    taskId = "t_sample",
                    targetUri = "goke://todo",
                    listName = "学习",
                    hasRemind = true,
                    repeating = true,
                )
            val todayTaskCompact = TodayWidgetService.itemView(app, sampleTaskRow, compact = true).apply(app, FrameLayout(app))
            assertEquals(View.GONE, todayTaskCompact.findViewById<View>(R.id.today_item_remind).visibility)
            assertEquals(View.GONE, todayTaskCompact.findViewById<View>(R.id.today_item_repeat).visibility)

            val todoCompactView =
                TodoWidgetService
                    .itemView(
                        app,
                        TodoWidgetProvider.Row(
                            id = "t_sample",
                            title = "提交报告",
                            code = null,
                            due = "今天 18:00",
                            priority = 1,
                            overdue = false,
                            listName = "学习",
                            hasRemind = true,
                            repeating = true,
                        ),
                        compact = true,
                    ).apply(app, FrameLayout(app))
            assertEquals(View.GONE, todoCompactView.findViewById<View>(R.id.item_list).visibility)
            assertEquals(View.GONE, todoCompactView.findViewById<View>(R.id.item_remind).visibility)
            assertEquals(View.GONE, todoCompactView.findViewById<View>(R.id.item_repeat).visibility)
            assertEquals(View.VISIBLE, todoCompactView.findViewById<View>(R.id.item_due).visibility)

            val parcelCompactView =
                ParcelWidgetService
                    .itemView(
                        app,
                        ParcelWidgetProvider.Row(
                            id = "p_sample",
                            code = "4-2-1086",
                            place = "东门菜鸟驿站",
                            due = "今天 18:00",
                            overdue = false,
                        ),
                        compact = true,
                    ).apply(app, FrameLayout(app))
            assertEquals(View.GONE, parcelCompactView.findViewById<View>(R.id.parcel_item_due).visibility)
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
                    HotWaterWidgetProvider::class.java,
                ),
                WidgetSpec.entries.map { it.providerClass }.toSet(),
            )
            assertEquals(0, totalAddedWidgets(app))
            for (spec in WidgetSpec.entries) {
                assertEquals(WidgetPinOutcome.UNSUPPORTED, requestPinWidget(app, spec))
            }
        }

    @Test
    fun allWidgetsAllowFreeResizingAndSizeClassClassifiesBounds() {
        val infoXmls =
            listOf(
                R.xml.widget_schedule_info,
                R.xml.widget_today_info,
                R.xml.widget_todo_info,
                R.xml.widget_parcel_info,
                R.xml.widget_next_info,
                R.xml.widget_hotwater_info,
            )
        val expectedPx = WidgetKit.dp(app, 40).toFloat()
        for (xmlRes in infoXmls) {
            app.resources.getXml(xmlRes).use { parser ->
                var event = parser.eventType
                var found = false
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                        found = true
                        val attrs = android.util.Xml.asAttributeSet(parser)
                        val minW =
                            app.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.minResizeWidth)).let { ta ->
                                try {
                                    ta.getDimension(0, -1f)
                                } finally {
                                    ta.recycle()
                                }
                            }
                        val minH =
                            app.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.minResizeHeight)).let { ta ->
                                try {
                                    ta.getDimension(0, -1f)
                                } finally {
                                    ta.recycle()
                                }
                            }
                        val resizeMode =
                            app.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.resizeMode)).let { ta ->
                                try {
                                    ta.getInt(0, 0)
                                } finally {
                                    ta.recycle()
                                }
                            }
                        assertEquals(expectedPx, minW, 1f)
                        assertEquals(expectedPx, minH, 1f)
                        assertEquals(android.appwidget.AppWidgetProviderInfo.RESIZE_BOTH, resizeMode)
                    }
                    event = parser.next()
                }
                assertTrue(found)
            }
        }

        val oneByOneOpts =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 72)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 84)
            }
        val oneByOne = WidgetKit.sizeClass(app, oneByOneOpts)
        assertTrue(oneByOne.compactWidth)
        assertTrue(oneByOne.ultraCompactWidth)
        assertTrue(oneByOne.compactHeight)

        val twoByTwoOpts =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 145)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 210)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 95)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 165)
            }
        val twoByTwo = WidgetKit.sizeClass(app, twoByTwoOpts)
        assertTrue(twoByTwo.compactWidth)
        assertTrue(!twoByTwo.ultraCompactWidth)
        assertTrue(!twoByTwo.compactHeight)

        val tallOneByOneOpts =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 78)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 92)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 76)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 108)
            }
        val tallOneByOne = WidgetKit.sizeClass(app, tallOneByOneOpts)
        assertTrue(tallOneByOne.compactHeight)
        assertTrue(!tallOneByOne.isVerticalStrip)
        assertTrue(tallOneByOne.isSquare)

        val singleSizeOpts =
            Bundle().apply {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(160f, 192f)))
            }
        val singleSize = WidgetKit.sizeClass(app, singleSizeOpts)
        assertTrue(singleSize.isMultiRowCard)
        assertTrue(singleSize.compactWidth)

        val multiSizeOpts =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(72f, 92f), SizeF(260f, 190f)),
                )
            }
        val multiRv =
            WidgetKit.buildResponsive(app, multiSizeOpts) { sc ->
                NextWidgetProvider.build(app, null, Settings(), size = sc)
            }
        val multiRoot = multiRv.apply(app, FrameLayout(app))
        assertEquals(
            app.getString(R.string.widget_next_empty),
            multiRoot.findViewById<TextView>(R.id.next_title).text.toString(),
        )
    }

    @Test
    fun multipleCourseAndTaskRemindersPersistAndComputeDistinctTriggers(): Unit =
        runBlocking {
            val settingsStore = SettingsStore(app)
            settingsStore.update {
                it.copy(
                    remindCourse = true,
                    courseRemindMinutes = 5,
                    courseRemindMinutesList = listOf(5, 15, 30),
                )
            }
            val reloaded = SettingsStore(app).value
            assertEquals(listOf(5, 15, 30), reloaded.effectiveCourseRemindMinutes)
            assertEquals(5, reloaded.courseRemindMinutes)

            val sem = app.repo.snapshot.value.currentSemester ?: TestUtilCompat.defaultSem()
            val now = sem.week1Monday.atTime(7, 0)
            val course =
                Course(
                    id = "c_multi",
                    semesterId = sem.id,
                    name = "高等数学",
                    dayOfWeek = 1,
                    startPeriod = 1,
                    endPeriod = 2,
                    weeks = setOf(1),
                )
            val task =
                Task(
                    id = "t_multi",
                    title = "交实验报告",
                    due = sem.week1Monday.atTime(18, 0),
                    dueHasTime = true,
                    remindMinutes = 10,
                    remindMinutesList = listOf(10, 60),
                )
            val snap =
                app.repo.snapshot.value
                    .copy(semesters = listOf(sem), courses = listOf(course), tasks = listOf(task))
            val computed = ReminderScheduler.compute(app, snap, reloaded, now)

            val courseReminders = computed.filter { it.title == "高等数学" }
            assertEquals(3, courseReminders.size)
            assertEquals(
                listOf(
                    sem.week1Monday.atTime(8, 0),
                    sem.week1Monday.atTime(8, 15),
                    sem.week1Monday.atTime(8, 25),
                ),
                courseReminders.map { it.triggerAt }.sorted(),
            )

            val taskReminders = computed.filter { it.title == "交实验报告" }
            assertEquals(2, taskReminders.size)
            assertEquals(
                listOf(
                    sem.week1Monday.atTime(17, 0),
                    sem.week1Monday.atTime(17, 50),
                ),
                taskReminders.map { it.triggerAt }.sorted(),
            )
        }

    @Test
    fun scheduledBlockRendersAsCourseCapsuleAndGraysOutAcrossPhases(): Unit =
        runBlocking {
            app.repo.load()
            val today = LocalDate.of(2026, 9, 14)
            val ts = System.currentTimeMillis()
            val list = TaskList(id = Ids.next(), name = "社团", colorIndex = 2, sort = 1)
            val block =
                Task(
                    id = Ids.next(),
                    title = "摄影社例会",
                    listId = list.id,
                    blockStart = today.atTime(10, 0),
                    blockMinutes = 60,
                    due = today.atTime(11, 0),
                    dueHasTime = true,
                    extra = mapOf("location" to "艺术楼 210"),
                )
            val todo =
                Task(
                    id = Ids.next(),
                    title = "交实验报告",
                    due = today.atTime(18, 0),
                    dueHasTime = true,
                )
            app.repo.commit(Edits.saveAll(app.repo.snapshot.value, listOf(list, block, todo), "seed-block-widget", ts))
            val snap = app.repo.snapshot.value
            val st = app.settings.value

            // 1. 未开始：彩色胶囊 + 无勾选框 + 地点副文；普通待办仍无彩底带勾选框
            val upcomingNow = today.atTime(9, 30)
            val upRows = TodayWidgetProvider.buildRows(app, snap, st, upcomingNow)
            val upBlock = upRows.first { it.key == "b_${block.id}" }
            val upTodo = upRows.first { it.key == "t_${todo.id}" }
            assertEquals(2, upBlock.colorIndex)
            assertEquals("艺术楼 210", upBlock.sub)
            assertTrue(!upBlock.ongoing && !upBlock.finished)
            assertEquals(-1, upTodo.colorIndex)

            val upRv =
                renderRemoteViews(
                    name = "widget_today_block_upcoming",
                    rv = TodayWidgetProvider.build(app, 31, snap, st, upcomingNow),
                    widthDp = 320,
                    heightDp = 240,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = upRows.map { TodayWidgetService.itemView(app, it) },
                )
            val upList = upRv.findViewById<ListView>(R.id.today_list)
            val upBlockView = upList.getChildAt(upRows.indexOf(upBlock))
            val upTodoView = upList.getChildAt(upRows.indexOf(upTodo))
            assertEquals(View.GONE, upBlockView.findViewById<View>(R.id.today_item_check).visibility)
            assertEquals(View.GONE, upBlockView.findViewById<View>(R.id.today_item_progress).visibility)
            assertEquals(WidgetKit.courseFg(app, 2), upBlockView.findViewById<TextView>(R.id.today_item_title).currentTextColor)
            assertEquals(View.VISIBLE, upTodoView.findViewById<View>(R.id.today_item_check).visibility)
            assertEquals(null, upTodoView.background)

            val nextUpcoming = NextWidgetProvider.pickSummary(app, snap, st, upcomingNow)
            assertEquals(block.id, nextUpcoming.taskId)
            assertEquals("艺术楼 210", nextUpcoming.sub)
            assertEquals(WidgetKit.courseFg(app, 2), nextUpcoming.barColor)

            // 2. 进行中：胶囊渐进置灰进度条可见
            val ongoingNow = today.atTime(10, 30)
            val onRows = TodayWidgetProvider.buildRows(app, snap, st, ongoingNow)
            val onBlock = onRows.first { it.key == "b_${block.id}" }
            assertTrue(onBlock.ongoing && !onBlock.finished)
            val onRv =
                renderRemoteViews(
                    name = "widget_today_block_ongoing",
                    rv = TodayWidgetProvider.build(app, 32, snap, st, ongoingNow),
                    widthDp = 320,
                    heightDp = 240,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = onRows.map { TodayWidgetService.itemView(app, it) },
                )
            val onBlockView = onRv.findViewById<ListView>(R.id.today_list).getChildAt(onRows.indexOf(onBlock))
            assertEquals(View.GONE, onBlockView.findViewById<View>(R.id.today_item_check).visibility)
            assertEquals(View.VISIBLE, onBlockView.findViewById<ProgressBar>(R.id.today_item_progress).visibility)
            assertTrue(onBlockView.findViewById<ProgressBar>(R.id.today_item_progress).progress in 450..550)

            // 3. 已结束：保持灰色胶囊且不触发逾期待办徽章
            val finishedNow = today.atTime(11, 30)
            val finRows = TodayWidgetProvider.buildRows(app, snap, st, finishedNow)
            val finBlock = finRows.first { it.key == "b_${block.id}" }
            assertTrue(finBlock.finished && !finBlock.ongoing)
            val finRv =
                renderRemoteViews(
                    name = "widget_today_block_finished",
                    rv = TodayWidgetProvider.build(app, 33, snap, st, finishedNow),
                    widthDp = 320,
                    heightDp = 240,
                    listViewId = R.id.today_list,
                    emptyViewId = R.id.today_empty,
                    items = finRows.map { TodayWidgetService.itemView(app, it) },
                )
            val finBlockView = finRv.findViewById<ListView>(R.id.today_list).getChildAt(finRows.indexOf(finBlock))
            assertEquals(View.GONE, finBlockView.findViewById<View>(R.id.today_item_check).visibility)
            assertEquals(View.GONE, finBlockView.findViewById<View>(R.id.today_item_progress).visibility)
            assertEquals(
                app.getColor(R.color.widget_course_done_fg),
                finBlockView.findViewById<TextView>(R.id.today_item_title).currentTextColor,
            )

            val cleanSnap = Snapshot(lists = listOf(list), tasks = listOf(block, todo))
            val nextAfterEnd = NextWidgetProvider.pickSummary(app, cleanSnap, st, finishedNow)
            assertEquals(todo.id, nextAfterEnd.taskId)
        }

    @Test
    fun widgetBlankAreasOpenCorrespondingTabAndListViewsWrapContent() {
        val today = LocalDate.of(2026, 9, 14)
        val now = today.atTime(9, 0)
        val sem = TestUtilCompat.defaultSem()
        val task =
            Task(
                id = Ids.next(),
                title = "复习线性代数",
                due = today.atTime(14, 0),
                dueHasTime = true,
            )
        val snap = Snapshot(semesters = listOf(sem), tasks = listOf(task))
        val st = Settings()

        val listWidgets =
            listOf(
                Triple(TodayWidgetProvider.build(app, 41, snap, st, now), R.id.today_list, DeepLinks.todayUri()),
                Triple(ScheduleWidgetProvider.build(app, 42, snap, st, now), R.id.sched_list, DeepLinks.timetableUri()),
                Triple(TodoWidgetProvider.build(app, 43, now), R.id.widget_list, DeepLinks.todoUri()),
                Triple(ParcelWidgetProvider.build(app, 44, now), R.id.parcel_list, DeepLinks.parcelUri()),
            )
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        listWidgets.forEach { (rv, listId, expectedUri) ->
            val host = FrameLayout(app)
            val root = rv.apply(app, host)
            val lv = root.findViewById<ListView>(listId)
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lv.layoutParams.height)
            assertTrue(root.findViewById<View>(R.id.widget_root).performClick())
            val started = shadowApp.nextStartedActivity
            assertEquals(expectedUri, started?.data)
        }

        // Next widget: blank root opens Timetable, item body/time opens overlay
        val nextHost = FrameLayout(app)
        val nextRoot = NextWidgetProvider.build(app, snap, st, now).apply(app, nextHost)
        assertTrue(nextRoot.findViewById<View>(R.id.widget_root).performClick())
        assertEquals(DeepLinks.timetableUri(), shadowApp.nextStartedActivity?.data)

        assertTrue(nextRoot.findViewById<View>(R.id.next_body).performClick())
        assertEquals(
            WidgetOverlayActivity::class.java.name,
            shadowApp.nextStartedActivity?.component?.className,
        )

        // Next mini stack: blank root opens Timetable, card0 opens overlay
        val miniHost = FrameLayout(app)
        val miniRoot =
            NextWidgetProvider
                .build(app, snap, st, now, WidgetKit.SizeClass(ultraCompactWidth = true, compactHeight = false))
                .apply(app, miniHost)
        assertTrue(miniRoot.findViewById<View>(R.id.widget_root).performClick())
        assertEquals(DeepLinks.timetableUri(), shadowApp.nextStartedActivity?.data)

        assertTrue(miniRoot.findViewById<View>(R.id.next_card0).performClick())
        assertEquals(
            WidgetOverlayActivity::class.java.name,
            shadowApp.nextStartedActivity?.component?.className,
        )
    }
}

private object TestUtilCompat {
    fun defaultSem() =
        app.goke.core.model.Semester(
            id = "sem_test",
            name = "Test",
            week1Monday = java.time.LocalDate.of(2026, 8, 31),
        )
}
