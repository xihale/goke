package app.mintcal.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import app.mintcal.DeepLink
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.ui.components.FloatingNavBar
import app.mintcal.ui.components.MintSnackbarHost
import app.mintcal.ui.components.MintUndoButton
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AdvancedFullScreen
import app.mintcal.ui.screens.AiSettingsScreen
import app.mintcal.ui.screens.AppearanceScreen
import app.mintcal.ui.screens.CalendarScreen
import app.mintcal.ui.screens.CourseEditScreen
import app.mintcal.ui.screens.DataScreen
import app.mintcal.ui.screens.EditTimetableScreen
import app.mintcal.ui.screens.HistoryScreen
import app.mintcal.ui.screens.ImportScreen
import app.mintcal.ui.screens.JxfwScreen
import app.mintcal.ui.screens.ParcelScreen
import app.mintcal.ui.screens.PeriodsScreen
import app.mintcal.ui.screens.ReliabilityScreen
import app.mintcal.ui.screens.SettingsScreen
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoEditScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun MintCalRoot(
    app: MintCalApp,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
) {
    val nav = rememberNav()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(app, snack, scope, nav) }
    actions.undoLabel = stringResource(R.string.undo)
    val ready by app.repo.ready.collectAsState()

    // 是否在压栈页（Tab 层 = home 目的地）。转场与返回栈由标准 navigation-compose 接管。
    val backStack by nav.controller.currentBackStackEntryAsState()
    val onRoute = backStack?.destination?.route != HOME

    BackHandler(enabled = ready && !onRoute && nav.tab != Tab.TODAY) { nav.back() }

    LaunchedEffect(incoming, ready) {
        if (incoming != null && ready) {
            nav.push(Route.Import(incoming.toString()))
            onIncomingHandled()
        }
    }

    // 外部入口：通知 / 小组件 / 桌面快捷方式 / 分享 / 划词
    val link by app.links.collectAsState()
    LaunchedEffect(link, ready) {
        val l = link
        if (l == null || !ready) return@LaunchedEffect
        app.links.value = null
        when (l) {
            is DeepLink.Task -> {
                nav.openTask(l.id)
            }

            DeepLink.Today -> {
                nav.clearToRoot()
                nav.tab = Tab.TODAY
            }

            DeepLink.Todo -> {
                nav.clearToRoot()
                nav.tab = Tab.TODO
            }

            is DeepLink.Add -> {
                val lines = l.text.lines().count { it.isNotBlank() }
                if (lines > 1) {
                    // 多行：交给待办页快速添加做批量预览
                    nav.clearToRoot()
                    nav.tab = Tab.TODO
                    nav.pendingAddText = l.text
                    return@LaunchedEffect
                }
                nav.push(Route.TodoEdit(text = l.text))
            }
        }
    }

    // 转场用定长补间，不能用主题里的低刚度 spring：整页滑入过冲再弹回就是肉眼可见的抖
    val slide = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
    val fade = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val back: () -> Unit = {
        nav.pop()
        Unit
    }

    CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (ready) {
                NavHost(
                    nav.controller,
                    startDestination = HOME,
                    modifier = Modifier.fillMaxSize(),
                    // 标准 M3 方向性转场：enter/exit = 推入（新页右入 1/3，底层左让位）；
                    // popEnter/popExit = 返回（顶层右出淡出，底层原位揭露）。
                    // 开启预测性返回后，库按手势进度逐帧 seek pop 组转场。
                    enterTransition = { slideInHorizontally(slide) { it / 3 } + fadeIn(fade) },
                    exitTransition = { slideOutHorizontally(slide) { -it / 10 } },
                    popEnterTransition = { slideInHorizontally(slide) { -it / 10 } },
                    popExitTransition = { slideOutHorizontally(slide) { it / 3 } + fadeOut(fade) },
                ) {
                    composable(HOME) { Tabs(nav) }
                    mintDestinations(nav, back)
                }
            }
            MintUndoButton(
                prompt = actions.undo,
                label = actions.undoLabel,
                onUndo = actions::undoNow,
                onTimeout = { actions.timeout(it.seq) },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 20.dp)
                        .padding(bottom = if (!onRoute) 168.dp else 96.dp),
            )
            MintSnackbarHost(
                snack,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (!onRoute) 152.dp else 88.dp),
            )
        }
    }
}

/** 全部压栈页目的地；Tab 层（home）由宿主自行 composable 定义（正式 App 是 Tabs，测试是内容桩）。 */
fun NavGraphBuilder.mintDestinations(
    nav: Nav,
    back: () -> Unit,
) {
    composable<Route.EditTimetable> { EditTimetableScreen(back) }
    composable<Route.Appearance> { AppearanceScreen(back) }
    composable<Route.Settings> { SettingsScreen(back) }
    composable<Route.Trash> { TrashScreen(back) }
    composable<Route.History> { HistoryScreen(back) }
    composable<Route.Parcel> { ParcelScreen(back) }
    composable<Route.AdvancedFull> { AdvancedFullScreen(back) }
    composable<Route.Periods> { PeriodsScreen(back) }
    composable<Route.About> { AboutScreen(back) }
    composable<Route.AiSettings> { AiSettingsScreen(back) }
    composable<Route.Data> { DataScreen(back) }
    composable<Route.Sync> { SyncScreen(back) }
    composable<Route.Jxfw> { JxfwScreen(back) }
    composable<Route.Reliability> { ReliabilityScreen(back) }
    composable<Route.Import> { e -> ImportScreen(e.toRoute<Route.Import>().uri, back) }
    composable<Route.CourseEdit> { e -> CourseEditScreen(e.toRoute<Route.CourseEdit>().id, back) }
    composable<Route.TodoEdit> { e ->
        val r = e.toRoute<Route.TodoEdit>()
        TodoEditScreen(r.id, r.date, r.start, r.end, r.text, r.courseId, r.extraDates, back)
    }
}

@Composable
private fun Tabs(nav: Nav) {
    // 全部 Tab 常驻组合：切换只动绘制层（零重组，滚动/筛选/翻页状态永不丢），
    // 且四个页在启动时一次性组装——这帧重活落在启动屏底下，之后任何切换都不再有首访组装尖刺。
    // 切页本身不做任何位移/淡入：内容瞬切、底栏色块滑过去。位移过渡需要在新页首帧之后才把
    // 偏移打进去（一帧跳出去再滑回来），线框多的课表/日历上看着就是抖——平台惯例也是瞬切。
    val visited = remember { Tab.entries.toList() }
    Box(Modifier.fillMaxSize()) {
        visited.forEach { tab ->
            key(tab) {
                val current = tab == nav.tab
                Box(
                    Modifier
                        .zIndex(if (current) 1f else 0f)
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (current) 1f else 0f }
                        .blockPointer(!current),
                ) {
                    when (tab) {
                        Tab.TODAY -> TodayScreen()
                        Tab.TIMETABLE -> TimetableScreen()
                        Tab.CALENDAR -> CalendarScreen()
                        Tab.TODO -> TodoScreen()
                    }
                }
            }
        }
        // zIndex(2f)：底栏必须压在所有 Tab 绘制层之上，否则被全屏 Tab 层挡住点击
        FloatingNavBar(nav.tab, nav::select, Modifier.align(Alignment.BottomCenter).zIndex(2f))
    }
}

/** 隐藏页面拦截所有指针事件（Initial 消费），防止穿透到下层 Tab 的滚动/点击。 */
private fun Modifier.blockPointer(block: Boolean): Modifier =
    pointerInput(block) {
        if (!block) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
