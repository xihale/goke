package app.mintcal.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mintcal.MintCalApp
import app.mintcal.core.change.ChangeSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Tab { TODAY, TIMETABLE, CALENDAR, TODO }

sealed interface Route {
    data object EditTimetable : Route

    data object Appearance : Route

    data object Settings : Route

    data object Trash : Route

    data object History : Route

    data object Parcel : Route

    data object AdvancedFull : Route

    data object Periods : Route

    data object About : Route

    data object AiSettings : Route

    data object Data : Route

    /** uri 为空表示先让用户选择文件。 */
    data class Import(
        val uri: String?,
    ) : Route

    data class CourseEdit(
        val id: String?,
    ) : Route

    data class EventEdit(
        val id: String?,
        val date: String? = null,
    ) : Route

    data class TaskDetail(
        val id: String?,
    ) : Route
}

/** 极简导航：底部四个 Tab + 一个压栈页面。 */
class Nav {
    var tab by mutableStateOf(Tab.TODAY)
    val stack = mutableStateListOf<Route>()
    val top: Route? get() = stack.lastOrNull()

    fun push(r: Route) {
        if (top != r) stack.add(r)
    }

    fun pop(): Boolean =
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
            true
        } else {
            false
        }

    fun back(): Boolean = pop() || (tab != Tab.TODAY).also { if (it) tab = Tab.TODAY }
}

/** 页面里发起的写操作：统一走变更集，并给出「撤销」提示。 */
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
                val r = snack.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
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
