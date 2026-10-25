package app.mintcal.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
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

    @Serializable data object Trash : Route

    @Serializable data object History : Route

    @Serializable data object Parcel : Route

    @Serializable data object AdvancedFull : Route

    @Serializable data object Periods : Route

    @Serializable data object About : Route

    @Serializable data object AiSettings : Route

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
    var tab by mutableStateOf(Tab.TODAY)

    /** 外部分享来的多行文字，待办页的快速添加接手并给出批量预览。 */
    var pendingAddText by mutableStateOf<String?>(null)

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

    fun back(): Boolean = pop() || (tab != Tab.TODAY).also { if (it) tab = Tab.TODAY }
}

@Composable
fun rememberNav(): Nav {
    val controller = rememberNavController()
    return remember { Nav(controller) }
}

/** 页面里发起的写操作:统一走变更集,并给出「撤销」提示。 */
class Actions(
    val app: MintCalApp,
    val snack: SnackbarHostState,
    val scope: CoroutineScope,
    val nav: Nav,
) {
    var undoLabel: String = "撤销"

    /** 当前可撤销的一步:一个圆,点一下回退,到时自动收起。 */
    var undo by mutableStateOf<UndoPrompt?>(null)
        private set

    fun commit(
        cs: ChangeSet,
        message: String? = null,
    ) {
        scope.launch {
            app.repo.commit(cs)
            if (message != null && !cs.isEmpty) {
                // 新操作顶掉旧提示;倒计时由那个圆自己数,走完自动收起
                undo = UndoPrompt(cs.id, message, System.currentTimeMillis())
            }
        }
    }

    /** 点圆:撤销最近这条,按钮立即收起。 */
    fun undoNow() {
        val p = undo ?: return
        undo = null
        scope.launch { app.repo.undo(p.csId) }
    }

    /** 倒计时走完:收起圆(期间已有新操作则保留新的那条)。 */
    fun timeout(seq: Long) {
        if (undo?.seq == seq) undo = null
    }

    fun toast(message: String) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
}

val LocalActions = compositionLocalOf<Actions> { error("no actions") }
val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalApp = compositionLocalOf<MintCalApp> { error("no app") }
