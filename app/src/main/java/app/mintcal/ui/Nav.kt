package app.mintcal.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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

    @Serializable data class EventEdit(
        val id: String?,
        val date: String? = null,
        // 课表格子上框选带来的起止时刻（ISO），新建时预填
        val start: String? = null,
        val end: String? = null,
    ) : Route

    @Serializable data class TaskDetail(
        val id: String?,
        // 从「快速添加」带过来的草稿（id 为 null 时生效），免得进详情还要重输
        val title: String? = null,
        val due: String? = null,
        val dueHasTime: Boolean = false,
        val priority: Int = 0,
        val listId: String? = null,
        val tags: String? = null,
        val startDate: String? = null,
        val courseId: String? = null,
        val beforeClass: Boolean = false,
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

    /** 当前打开的待办底部抽屉（全局一个，由 MintCalRoot 承载）。 */
    var sheetTaskId by mutableStateOf<String?>(null)

    /** 外部分享来的多行文字，待办页的快速添加接手并给出批量预览。 */
    var pendingAddText by mutableStateOf<String?>(null)

    fun openTask(id: String) {
        sheetTaskId = id
    }

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

    fun commit(
        cs: ChangeSet,
        message: String? = null,
    ) {
        scope.launch {
            app.repo.commit(cs)
            if (message != null && !cs.isEmpty) {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long, withDismissAction = true)
                if (r == SnackbarResult.ActionPerformed) app.repo.undo(cs.id)
            }
        }
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
