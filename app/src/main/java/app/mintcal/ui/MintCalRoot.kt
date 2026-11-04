package app.mintcal.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import app.mintcal.ui.components.NavClearance
import app.mintcal.ui.components.mintClickable
import app.mintcal.ui.screens.AboutScreen
import app.mintcal.ui.screens.AdvancedFullScreen
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
import app.mintcal.ui.screens.PermissionScreen
import app.mintcal.ui.screens.ReliabilityScreen
import app.mintcal.ui.screens.SettingsScreen
import app.mintcal.ui.screens.SyncScreen
import app.mintcal.ui.screens.TimetableScreen
import app.mintcal.ui.screens.TodayScreen
import app.mintcal.ui.screens.TodoEditScreen
import app.mintcal.ui.screens.TodoScreen
import app.mintcal.ui.screens.TrashScreen
import app.mintcal.ui.theme.LocalReduceMotion
import app.mintcal.ui.theme.MintTheme

@Composable
fun MintCalRoot(
    app: MintCalApp,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
) {
    val nav = rememberNav()
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(app, scope, nav) }
    val ready by app.repo.ready.collectAsState()

    // 可撤销的说明条留到下一次操作，或用户自己关掉。不按秒数收起。
    // 旧实现「undoPrompt != null 时 undoShown = undoPrompt」是在组合期写状态（多一次根重组）；
    // 改成 SideEffect 提交，淡出动画期间内容仍保持最后一次的提示。
    val undoPrompt = actions.undo
    val undoShownState = remember { mutableStateOf(undoPrompt) }
    SideEffect { if (undoPrompt != null) undoShownState.value = undoPrompt }
    val undoShown = undoShownState.value

    // 是否在压栈页（Tab 层 = home 目的地）。转场与返回栈由标准 navigation-compose 接管。
    val backStack by nav.controller.currentBackStackEntryAsState()
    val onRoute = backStack?.destination?.route != HOME

    // Tab 层：非默认 Tab（周历）返回回周历。压栈页的返回交给 NavHost 默认处理。
    BackHandler(enabled = ready && !onRoute && nav.tab != Tab.TIMETABLE) { nav.back() }
    // 压栈页兜底：显式注册返回 = 弹一层，绝不把返回事件漏给系统导致整个应用退出
    // （部分 ROM 的预测性返回会在无人认领事件时直接结束 Activity）。
    BackHandler(enabled = ready && onRoute) { nav.pop() }

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

    // 整页位移用定长补间：预测性返回要按手势进度 seek，弹簧没有固定时长。
    // 进入 emphasized decelerate，离开 emphasized accelerate。减少动效时直接切。
    val reduce = LocalReduceMotion.current
    val enterEase = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exitEase = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val slideIn = if (reduce) snap<IntOffset>() else tween<IntOffset>(durationMillis = 260, easing = enterEase)
    val slideOut = if (reduce) snap<IntOffset>() else tween<IntOffset>(durationMillis = 200, easing = exitEase)
    val fadeInSpec = if (reduce) snap<Float>() else tween<Float>(durationMillis = 200, easing = enterEase)
    val fadeOutSpec = if (reduce) snap<Float>() else tween<Float>(durationMillis = 150, easing = exitEase)
    val splash = if (reduce) snap<Float>() else tween<Float>(durationMillis = 220, easing = enterEase)
    val splashScale = if (reduce) snap<Float>() else tween<Float>(durationMillis = 280, easing = enterEase)
    // back 与整张 destination 表都 remember：NavHost 内部按 builder 的函数 identity 记忆图，
    // 每次 MintCalRoot 重组都传新 lambda 会让整张导航图被替换重建
    val back = remember(nav) { { nav.pop(); Unit } }

    CompositionLocalProvider(LocalApp provides app, LocalActions provides actions, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 首屏内容从启动屏底下淡出浮现：ready 后不再是一帧硬切
            AnimatedVisibility(
                visible = ready,
                enter = fadeIn(splash) + scaleIn(initialScale = 0.985f, animationSpec = splashScale),
            ) {
                if (ready) {
                    // HOME 目的地 + 压栈页都在这张表里；表本身 remember，转场参数也在闭包里
                    val graph: NavGraphBuilder.() -> Unit =
                        remember(nav, back, slideIn, slideOut, fadeInSpec, fadeOutSpec) {
                            {
                                composable(HOME) { Tabs(nav) }
                                mintDestinations(nav, back)
                            }
                        }
                    NavHost(
                        nav.controller,
                        startDestination = HOME,
                        modifier = Modifier.fillMaxSize(),
                        // 标准 M3 方向性转场：enter/exit = 推入（新页右入 1/3，底层左让位）；
                        // popEnter/popExit = 返回（顶层右出淡出，底层原位揭露）。
                        // 开启预测性返回后，库按手势进度逐帧 seek pop 组转场。
                        enterTransition = { slideInHorizontally(slideIn) { it / 3 } + fadeIn(fadeInSpec) },
                        exitTransition = { slideOutHorizontally(slideOut) { -it / 10 } },
                        popEnterTransition = { slideInHorizontally(slideIn) { -it / 10 } },
                        popExitTransition = { slideOutHorizontally(slideOut) { it / 3 } + fadeOut(fadeOutSpec) },
                        builder = graph,
                    )
                }
            }
            // 撤销：左下角一个圆钮，3 秒后自己消失。放左边就不和右下角的添加键打架，
            // 也不用一条通栏横条去盖列表内容。
            AnimatedVisibility(
                visible = undoPrompt != null && !nav.composerOpen,
                enter = fadeIn(MintTheme.spec()) + scaleIn(initialScale = 0.8f, animationSpec = MintTheme.spec()),
                exit = fadeOut(MintTheme.spec()) + scaleOut(targetScale = 0.8f, animationSpec = MintTheme.spec()),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(3f)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(start = 16.dp)
                        .padding(bottom = if (!onRoute) NavClearance else 16.dp),
            ) {
                val prompt = undoShown
                if (prompt != null) {
                    UndoButton(prompt.text, prompt.seq, onUndo = { actions.undoNow() }, onTimeout = { actions.dismissUndo() })
                }
            }
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
    composable<Route.Permission> { PermissionScreen(back) }
    composable<Route.Trash> { TrashScreen(back) }
    composable<Route.History> { HistoryScreen(back) }
    composable<Route.Parcel> { ParcelScreen(back) }
    composable<Route.AdvancedFull> { AdvancedFullScreen(back) }
    composable<Route.Periods> { PeriodsScreen(back) }
    composable<Route.About> { AboutScreen(back) }
    composable<Route.Data> { DataScreen(back) }
    composable<Route.Sync> { SyncScreen(back) }
    composable<Route.Jxfw> { JxfwScreen(back) }
    composable<Route.Reliability> { ReliabilityScreen(back) }
    composable<Route.Import> { e -> ImportScreen(e.toRoute<Route.Import>().uri, back) }
    composable<Route.CourseEdit> { e -> CourseEditScreen(e.toRoute<Route.CourseEdit>().id, back) }
    composable<Route.TodoEdit> { e ->
        val r = e.toRoute<Route.TodoEdit>()
        TodoEditScreen(r.id, r.date, r.start, r.end, r.text, r.courseId, r.extraDates, r.note) {
            if (nav.controller.currentBackStackEntry == e) back()
        }
    }
}

/**
 * 撤销圆钮：一个没文字的图标，3 秒内点就撤销，超时自己消失。
 * 外圈那道弧是剩下的时间——不用读秒，扫一眼就知道还剩多少。
 */
@Composable
private fun UndoButton(
    label: String,
    token: Long,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val remain = remember(token) { Animatable(1f) }
    LaunchedEffect(token) {
        remain.snapTo(1f)
        if (reduce) {
            remain.snapTo(0f)
            onTimeout()
        } else {
            remain.animateTo(0f, tween(durationMillis = UNDO_MS, easing = LinearEasing))
            onTimeout()
        }
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .mintClickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUndo()
            }.semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val ring = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcRect = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * remain.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcRect,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.Undo,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val UNDO_MS = 3000

@Composable
private fun Tabs(nav: Nav) {
    // 首帧只组装当前 Tab，其余三个在首帧之后的空闲帧补齐：冷启动首帧从四屏重活降到一屏，
    // 之后仍然全部常驻——切换只动绘制层（零重组，滚动/筛选/翻页状态不丢），切过去也没有首访尖刺。
    // 切页本身不做任何位移/淡入：内容瞬切、底栏色块滑过去。位移过渡需要在新页首帧之后才把
    // 偏移打进去（一帧跳出去再滑回来），线框多的课表/日历上看着就是抖——平台惯例也是瞬切。
    var staged by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        staged = true
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.tab) { focusManager.clearFocus(force = true) }
    Box(Modifier.fillMaxSize()) {
        Tab.entries.forEach { tab ->
            key(tab) {
                val current = tab == nav.tab
                if (current || staged) {
                    Box(
                        Modifier
                            .zIndex(if (current) 1f else 0f)
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (current) 1f else 0f }
                            .then(if (current) Modifier else Modifier.clearAndSetSemantics {})
                            .blockPointer(!current),
                    ) {
                        // 可见性走动态 CompositionLocal，只在 liveSnapshot/rememberNowGated 内部被读：
                        // 翻转只重组这两个小助手；数据没变时切页零重算，隐藏页也不订阅数据与 30 秒时钟
                        CompositionLocalProvider(LocalTabVisible provides current) {
                            when (tab) {
                                Tab.TODAY -> TodayScreen()
                                Tab.TIMETABLE -> TimetableScreen()
                                Tab.CALENDAR -> CalendarScreen()
                                Tab.TODO -> TodoScreen()
                            }
                        }
                    }
                }
            }
        }
        // zIndex(2f)：底栏必须压在所有 Tab 绘制层之上，否则被全屏 Tab 层挡住点击。
        // 快速添加浮层是模态层：浮层展开时底栏淡出，别让它压住输入框底部。
        AnimatedVisibility(
            visible = !nav.composerOpen,
            enter = fadeIn(MintTheme.spec()) + slideInVertically(MintTheme.spec()) { it / 2 },
            exit = fadeOut(MintTheme.spec()) + slideOutVertically(MintTheme.spec()) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
        ) {
            FloatingNavBar(nav.tab, nav::select, Modifier.align(Alignment.BottomCenter))
        }
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
