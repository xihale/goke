package app.mintcal.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import app.mintcal.MintCalApp
import app.mintcal.R
import app.mintcal.ui.components.FloatingNavBar
import app.mintcal.ui.components.MintSnackbarHost
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
import app.mintcal.ui.screens.JxfwScreen
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TaskDetailScreen
import app.mintcal.ui.screens.TaskDraft
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.theme.LocalReduceMotion
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

    val slide = MintTheme.spec<IntOffset>()
    val fade = MintTheme.spec<Float>()
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
    composable<Route.Import> { e -> ImportScreen(e.toRoute<Route.Import>().uri, back) }
    composable<Route.CourseEdit> { e -> CourseEditScreen(e.toRoute<Route.CourseEdit>().id, back) }
    composable<Route.EventEdit> { e ->
        val r = e.toRoute<Route.EventEdit>()
        EventEditScreen(r.id, r.date, r.start, r.end, back)
    }
    composable<Route.TaskDetail> { e ->
        val r = e.toRoute<Route.TaskDetail>()
        TaskDetailScreen(
            id = r.id,
            draft =
                r.title?.let {
                    TaskDraft(
                        title = it,
                        due = r.due?.let { iso -> runCatching { LocalDateTime.parse(iso) }.getOrNull() },
                        dueHasTime = r.dueHasTime,
                        priority = r.priority,
                        listId = r.listId,
                        tags = r.tags.orEmpty(),
                        startDate = r.startDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                    )
                },
            onBack = back,
        )
    }
}

@Composable
private fun Tabs(nav: Nav) {
    // 全部 Tab 常驻组合：切换是纯绘制层过渡（零重组，滚动/筛选/翻页状态永不丢），
    // 且四个页在启动时一次性组装——这帧重活落在启动屏底下，之后任何切换都不再有首访组装尖刺。
    // 未选中的 Tab 在绘制层下透明且拦截指针，避免误触。
    val visited = remember { Tab.entries.toList() }
    val reduce = LocalReduceMotion.current
    val animSpec = MintTheme.spec<Float>()
    val alphas =
        remember {
            mutableMapOf<Tab, Animatable<Float, AnimationVector1D>>().apply { put(nav.tab, Animatable(1f)) }
        }
    val prev = remember { mutableStateOf(nav.tab) }
    var direction by remember { mutableStateOf(1f) }
    LaunchedEffect(nav.tab) {
        val old = prev.value
        if (old == nav.tab) return@LaunchedEffect
        direction = if (Tab.entries.indexOf(nav.tab) >= Tab.entries.indexOf(old)) 1f else -1f
        prev.value = nav.tab
        // 驱动所有 Tab 到目标透明度：连点中断时不会留下半透明残页
        Tab.entries.forEach { t ->
            val anim = alphas.getOrPut(t) { Animatable(0f) }
            val target = if (t == nav.tab) 1f else 0f
            if (reduce) {
                anim.snapTo(target)
            } else {
                launch { anim.animateTo(target, animSpec) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        visited.forEach { tab ->
            key(tab) {
                val current = tab == nav.tab
                Box(
                    Modifier
                        .zIndex(if (current) 1f else 0f)
                        .fillMaxSize()
                        .graphicsLayer {
                            val a = alphas[tab]?.value ?: if (current) 1f else 0f
                            alpha = a
                            // 共享轴 X 方向感：新页随进度滑入，旧页反向轻微让位
                            translationX =
                                if (current) {
                                    direction * (1f - a) * 0.05f * size.width
                                } else {
                                    -direction * (1f - a) * 0.03f * size.width
                                }
                        }.blockPointer(!current),
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
