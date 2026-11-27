package app.goke.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.goke.GokeApp
import app.goke.R
import app.goke.core.change.ChangeSet
import app.goke.ui.components.UndoPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

enum class Tab { TODAY, TIMETABLE, CALENDAR, TODO, ME }

/** 返回栈单条记录：唯一 [id] 供转场 key、SaveableStateHolder 隔离与防重入 pop 使用；[depth] 决定入栈/出栈方向。 */
data class BackEntry(
    val id: Long,
    val route: Route,
    val depth: Int,
)

@Serializable
sealed interface Route {
    @Serializable data object EditTimetable : Route

    @Serializable data object Appearance : Route

    @Serializable data object Preferences : Route

    @Serializable data object Reminders : Route

    @Serializable data object Widgets : Route

    /** 权限与可靠性自检入口（Route.Permission 路由已统一指向 ReliabilityScreen）。 */
    @Serializable data object Permission : Route

    @Serializable data object Trash : Route

    @Serializable data object History : Route

    @Serializable data object Parcel : Route

    @Serializable data object HotWater : Route

    @Serializable data object HotWaterBills : Route

    @Serializable data object Mcp : Route

    @Serializable data object LanWeb : Route

    @Serializable data object Periods : Route

    @Serializable data object About : Route

    @Serializable data object Data : Route

    @Serializable data object TimetableShare : Route

    @Serializable data object Sync : Route

    @Serializable data object CalendarSync : Route

    @Serializable data object Jxfw : Route

    @Serializable data object Grades : Route

    @Serializable data object PhysicalTest : Route

    @Serializable data object Classroom : Route

    @Serializable data object CourseSelection : Route

    @Serializable data object Reliability : Route

    /** uri 为空表示先让用户选择文件。 */
    @Serializable data class Import(
        val uri: String?,
    ) : Route

    @Serializable data class CourseEdit(
        val id: String? = null,
        val initialDow: Int? = null,
        val initialPeriod: Int? = null,
    ) : Route

    /** 唯一的编辑页：新建 / 修改共用；[id] 为空时 [text] 是带过来的草稿标题。 */
    @Serializable data class TodoEdit(
        val id: String? = null,
        val date: String? = null,
        // 课表格子上框选带来的起止时刻（ISO），新建时预填
        val start: String? = null,
        val end: String? = null,
        val text: String? = null,
        /** 快速添加带过来的备注草稿。 */
        val note: String? = null,
        /** 从空教室等入口预填地点。 */
        val location: String? = null,
        /** 从课程页「加一条待办」带过来的关联课程。 */
        val courseId: String? = null,
        /** 钉死到某一节具体课时的原教学周（`CourseOccurrence.originalWeek`）。 */
        val courseWeek: Int? = null,
        /** 周历上框选了几天：其余日期（ISO，逗号分隔）跟着一起建。 */
        val extraDates: String? = null,
        val listId: String? = null,
        val remindMinutes: Int? = null,
        val remindList: String? = null,
        val remindPersistent: Boolean = false,
        val repeatFreq: String? = null,
        val repeatInterval: Int = 1,
        val repeatUntil: String? = null,
        val repeatByDays: String? = null,
        val repeatByMonths: String? = null,
    ) : Route
}

/**
 * 导航门面：由状态驱动的返回栈（Nav3 风格）替代 NavHostController，避免上游
 * NavHost 内置 PredictiveBackHandler 导致二级页返回锁死。
 * Tab 状态仍在此处（Tab 层常驻组合，切换零重组）。
 */
class Nav(
    initialTab: Tab = Tab.TIMETABLE,
) {
    private var nextId = 1L

    var tab by mutableStateOf(initialTab)

    /** 压栈中的二级页返回栈（空表示当前位于底层常驻 Tabs）。 */
    val stack: SnapshotStateList<BackEntry> = mutableStateListOf()

    val topEntry: BackEntry?
        get() = stack.lastOrNull()

    val onRoute: Boolean
        get() = stack.isNotEmpty()

    /** 外部分享来的多行文字，待办页的快速添加接手并给出批量预览。 */
    var pendingAddText by mutableStateOf<String?>(null)

    /** 快速添加浮层展开中。浮层是模态层：底栏与撤销条都要让位，不能再叠在输入区上。 */
    var composerOpen by mutableStateOf(false)

    /** 底部抽屉模态打开中（Tab 级）：底栏与撤销条同步让位。 */
    var sheetOpen by mutableStateOf(false)

    fun openTask(id: String) {
        push(Route.TodoEdit(id = id))
    }

    /** 要求课表跳到某一天（冲突面板「去查看」用），课表消费后清空。 */
    var focusDate by mutableStateOf<java.time.LocalDate?>(null)

    /** 从「今日」逾期摘要跳转待办并立即拉起整理面板（一次性消费）。 */
    var openTodoTidy by mutableStateOf(false)

    /** 再次点按当前 Tab 时自增，供各 Tab 内滚动回顶部。 */
    var reselect by mutableIntStateOf(0)

    fun select(t: Tab) {
        if (tab == t) reselect++ else tab = t
    }

    fun push(r: Route) {
        if (stack.lastOrNull()?.route == r) return
        stack.add(BackEntry(id = nextId++, route = r, depth = stack.size + 1))
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** 按 entryId 安全弹栈：防止退出动画期间双击或重复触发误弹下层页面。 */
    fun pop(entryId: Long): Boolean {
        val idx = stack.indexOfLast { it.id == entryId }
        if (idx < 0) return false
        while (stack.size > idx) {
            stack.removeAt(stack.lastIndex)
        }
        return true
    }

    /** 关闭全部压栈页，回到 Tab 层（如「预览到课表」）。 */
    fun clearToRoot(): Boolean {
        if (stack.isEmpty()) return false
        stack.clear()
        return true
    }

    fun back(): Boolean = pop() || (tab != Tab.TIMETABLE).also { if (it) tab = Tab.TIMETABLE }
}

@Composable
fun rememberNav(initialTab: Tab = Tab.TIMETABLE): Nav = remember { Nav(initialTab) }

/** 页面里发起的写操作:统一走变更集。可撤销的操作留一条说明，直到下一次操作或用户关掉；不能撤销的提示走系统 Toast。 */
class Actions(
    val app: GokeApp,
    val scope: CoroutineScope,
    val nav: Nav,
) {
    /** 当前可撤销的一步；非空即显示底部说明条。 */
    var undo by mutableStateOf<UndoPrompt?>(null)
        private set

    fun commit(
        cs: ChangeSet,
        message: String? = null,
        undoable: Boolean = true,
    ) {
        scope.launch {
            try {
                app.repo.commit(cs)
                // 说明条 = 4.5s 撤销入口：调用方显式给了 message 就出现（完成、改期、拖拽、批量新增都给退路）。
                // 单条新增 / 编辑保存不适合一键撤销，调用方传 undoable=false，退化为 Toast——反馈不再被吞掉。
                if (message != null && !cs.isEmpty) {
                    if (undoable) {
                        undo = UndoPrompt(cs.id, System.currentTimeMillis(), message)
                    } else {
                        toast(message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                app.errors.record("ActionCommit", e)
            }
        }
    }

    /** 触发统一触觉与音效反馈。 */
    fun feedback(event: FeedbackEvent) {
        app.feedback.play(event)
    }

    /** 撤销当前提示对应的操作。 */
    fun undoNow() {
        val p = undo ?: return
        undo = null
        scope.launch {
            try {
                val ok = app.repo.undo(p.csId)
                if (ok) app.feedback.play(FeedbackEvent.UNDO)
                toast(app.getString(if (ok) R.string.undone_toast else R.string.history_blocked_toast))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                app.errors.record("ActionUndo", e)
            }
        }
    }

    /** 用户关掉说明条，不撤销。 */
    fun dismissUndo() {
        undo = null
    }

    fun toast(message: String) {
        // Toast 必须在带 Looper 的线程上创建；组合协程（含 Robolectric）不保证在主线程，投递过去
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }
}

val LocalActions = compositionLocalOf<Actions> { error("no actions") }
val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalApp = compositionLocalOf<GokeApp> { error("no app") }
