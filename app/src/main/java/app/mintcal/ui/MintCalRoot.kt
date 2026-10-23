package app.mintcal.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.ui.components.FloatingNavBar
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AdvancedFullScreen
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
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TaskDetailScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen

@Composable
fun MintCalRoot(
    app: MintCalApp,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
) {
    val nav = remember { Nav() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(app, snack, scope, nav) }
    actions.undoLabel = stringResource(R.string.undo)
    val ready by app.repo.ready.collectAsState()

    LaunchedEffect(incoming, ready) {
        if (incoming != null && ready) {
            nav.push(Route.Import(incoming.toString()))
            onIncomingHandled()
        }
    }
    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.TODAY) { nav.back() }

    CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (ready) {
                AnimatedContent(
                    targetState = nav.top,
                    transitionSpec = {
                        if (targetState != null && initialState == null) {
                            (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (fadeOut())
                        } else if (targetState == null) {
                            fadeIn() togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
                        } else {
                            (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                        }
                    },
                    label = "route",
                ) { route ->
                    if (route == null) Tabs(nav) else RouteContent(route, nav)
                }
            }
            SnackbarHost(
                snack,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (nav.top == null) 92.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun Tabs(nav: Nav) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(nav.tab, label = "tab") { tab ->
            when (tab) {
                Tab.TODAY -> TodayScreen()
                Tab.TIMETABLE -> TimetableScreen()
                Tab.CALENDAR -> CalendarScreen()
                Tab.TODO -> TodoScreen()
            }
        }
        FloatingNavBar(nav.tab, { nav.tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RouteContent(
    route: Route,
    nav: Nav,
) {
    val back = {
        nav.pop()
        Unit
    }
    when (route) {
        Route.EditTimetable -> EditTimetableScreen(back)
        Route.Appearance -> AppearanceScreen(back)
        Route.Settings -> SettingsScreen(back)
        Route.Trash -> TrashScreen(back)
        Route.History -> HistoryScreen(back)
        Route.Parcel -> ParcelScreen(back)
        Route.AdvancedFull -> AdvancedFullScreen(back)
        Route.Periods -> PeriodsScreen(back)
        Route.About -> AboutScreen(back)
        Route.AiSettings -> AiSettingsScreen(back)
        Route.Data -> DataScreen(back)
        Route.Sync -> SyncScreen(back)
        is Route.Import -> ImportScreen(route.uri, back)
        is Route.CourseEdit -> CourseEditScreen(route.id, back)
        is Route.EventEdit -> EventEditScreen(route.id, route.date, back)
        is Route.TaskDetail -> TaskDetailScreen(route.id, back)
    }
}
