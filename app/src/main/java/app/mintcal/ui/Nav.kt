package app.mintcal.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.ui.components.UndoPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

enum class Tab { TODAY, TIMETABLE, CALENDAR, TODO }

/** Tab 层目的地（四个 Tab 共用的常驻容器），不参与压栈转场。 */
const val HOME = "home"

@Serializable
sealed interface Route {
    @Serializable data object EditTimetable : Route

    @Serializable data object Appearance : Route

    @Serializable data object Settings : Route

    /** 权限统一入口：通知 / 精确闹钟 / 自启 / 后台，一页管完。 */
    @Serializable data object Permission : Route

    @Serializable data object Trash : Route

    @Serializable data object History : Route

    @Serializable data object Parcel : Route

    @Serializable data object AdvancedFull : Route

    @Serializable data object Periods : Route

    @Serializable data object About : Route

    @Serializable data object Data : Route

    @Serializable data object Sync : Route

    @Serializable data object Jxfw : Route

    @Serializable data object Reliability : Route

    /** uri 为空表示先让用户选择文件。 */
    @Serializable data class Import(
        val uri: String?,
    ) : Route

    @Serializable data class CourseEdit(
        val id: String?,
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
        /** 从课程页「加一条待办」带过来的关联课程。 */
        val courseId: String? = null,
        /** 周历上框选了几天：其余日期（ISO，逗号分隔）跟着一起建。 */
        val extraDates: String? = null,
    ) : Route
}

/**
 * 导航门面：转场与返回栈交给标准 navigation-compose（推入/返回各有转场，
 * 预测性返回手势由库驱动逐帧 seek）。Tab 状态仍在此处（Tab 层常驻组合，切换零重组）。
 */
class Nav(
    val controller: NavHostController,
) {
    var tab by mutableStateOf(Tab.TIMETABLE)

    /** 外部分享来的多行文字，待办页的快速添加接手并给出批量预览。 */
    var pendingAddText by mutableStateOf<String?>(null)

    /** 快速添加浮层展开中。浮层是模态层：底栏与撤销条都要让位，不能再叠在输入区上。 */
    var composerOpen by mutableStateOf(false)

    fun openTask(id: String) {
        push(Route.TodoEdit(id = id))
    }

    /** 要求课表跳到某一天（冲突面板「去查看」用），课表消费后清空。 */
    var focusDate by mutableStateOf<java.time.LocalDate?>(null)

    /** 再次点按当前 Tab 时自增，供各 Tab 内滚动回顶部。 */
    var reselect by mutableIntStateOf(0)

    fun select(t: Tab) {
        if (tab == t) reselect++ else tab = t
    }

    fun push(r: Route) {
        controller.navigate(r) { launchSingleTop = true }
    }

    fun pop(): Boolean = controller.popBackStack()

    /** 关闭全部压栈页，回到 Tab 层（如「预览到课表」）。 */
    fun clearToRoot(): Boolean = controller.popBackStack(HOME, inclusive = false)

    fun back(): Boolean = pop() || (tab != Tab.TIMETABLE).also { if (it) tab = Tab.TIMETABLE }
}

@Composable
fun rememberNav(): Nav {
    val controller = rememberNavController()
    return remember { Nav(controller) }
}

/** 页面里发起的写操作:统一走变更集。可撤销的操作留一条说明，直到下一次操作或用户关掉；不能撤销的提示走系统 Toast。 */
class Actions(
    val app: MintCalApp,
    val scope: CoroutineScope,
    val nav: Nav,
) {
    /** 当前可撤销的一步；非空即显示底部说明条。 */
    var undo by mutableStateOf<UndoPrompt?>(null)
        private set

    fun commit(
        cs: ChangeSet,
        message: String? = null,
    ) {
        scope.launch {
            app.repo.commit(cs)
            // 撤回条只在动了删除时出现；纯新增/修改不打扰
            val removes = cs.changes.any { it is Change.Remove }
            if (message != null && !cs.isEmpty && removes) {
                undo = UndoPrompt(cs.id, System.currentTimeMillis(), message)
            }
        }
    }

    /** 撤销当前提示对应的操作。 */
    fun undoNow() {
        val p = undo ?: return
        undo = null
        scope.launch {
            val ok = app.repo.undo(p.csId)
            toast(app.getString(if (ok) R.string.undone_toast else R.string.history_blocked_toast))
        }
    }

    /** 用户关掉说明条，不撤销。 */
    fun dismissUndo() {
        undo = null
    }

    fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }
}

val LocalActions = compositionLocalOf<Actions> { error("no actions") }
val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalApp = compositionLocalOf<MintCalApp> { error("no app") }
