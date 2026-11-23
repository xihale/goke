package app.goke.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import app.goke.DeepLink
import app.goke.GokeApp
import app.goke.R
import app.goke.data.ReleaseInfo
import app.goke.data.UpdateChecker
import app.goke.ui.components.FloatingNavBar
import app.goke.ui.components.NavClearance
import app.goke.ui.components.mintClickable
import app.goke.ui.screens.AboutScreen
import app.goke.ui.screens.AppearanceScreen
import app.goke.ui.screens.CalendarScreen
import app.goke.ui.screens.ClassroomScreen
import app.goke.ui.screens.CourseEditScreen
import app.goke.ui.screens.DataScreen
import app.goke.ui.screens.EditTimetableScreen
import app.goke.ui.screens.GradesScreen
import app.goke.ui.screens.HistoryScreen
import app.goke.ui.screens.ImportScreen
import app.goke.ui.screens.JxfwScreen
import app.goke.ui.screens.LanWebScreen
import app.goke.ui.screens.McpScreen
import app.goke.ui.screens.ParcelScreen
import app.goke.ui.screens.PeriodsScreen
import app.goke.ui.screens.PhysicalTestScreen
import app.goke.ui.screens.ReliabilityScreen
import app.goke.ui.screens.SettingsScreen
import app.goke.ui.screens.SyncScreen
import app.goke.ui.screens.TimetableScreen
import app.goke.ui.screens.TodayScreen
import app.goke.ui.screens.TodoEditScreen
import app.goke.ui.screens.TodoScreen
import app.goke.ui.screens.TrashScreen
import app.goke.ui.screens.UpdateAvailableDialog
import app.goke.ui.screens.WidgetsScreen
import app.goke.ui.screens.openExternalUrl
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.delay

/**
 * 根级模态宿主接口：供子页面在应用最顶层（覆盖底栏与 NavHost）挂载轻量模态内容（如窗口内底部抽屉），
 * 避免创建独立 Dialog Window 产生跨进程 Binder 阻塞。
 */
interface ModalHost {
    fun show(content: @Composable () -> Unit)

    fun hide()
}

val LocalModalHost =
    compositionLocalOf<ModalHost> {
        object : ModalHost {
            override fun show(content: @Composable () -> Unit) {}

            override fun hide() {}
        }
    }

@Composable
fun GokeRoot(
    app: GokeApp,
    incoming: Uri?,
    initialTab: Tab = Tab.TIMETABLE,
    onIncomingHandled: () -> Unit,
) {
    val nav = rememberNav(initialTab)
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(app, scope, nav) }
    val ready by app.repo.ready.collectAsState()
    val settings by app.settings.flow.collectAsState()
    val pendingUpdate =
        if (ready &&
            settings.pendingUpdateVersion.isNotBlank() &&
            settings.dismissedUpdateVersion != settings.pendingUpdateVersion &&
            app.updateChecker.isNewerThanCurrent(settings.pendingUpdateVersion)
        ) {
            ReleaseInfo(
                version = settings.pendingUpdateVersion,
                url = settings.pendingUpdateUrl.ifBlank { UpdateChecker.LANDING_URL },
                notes = settings.pendingUpdateNotes,
                apkUrl = settings.pendingUpdateApkUrl,
                apkSize = settings.pendingUpdateApkSize,
                apkSha256 = settings.pendingUpdateApkSha256,
            )
        } else {
            null
        }

    // 可撤销的说明条留到下一次操作，或用户自己关掉。不按秒数收起。
    val undoPrompt = actions.undo
    val undoShownState = remember { mutableStateOf(undoPrompt) }
    SideEffect { if (undoPrompt != null) undoShownState.value = undoPrompt }
    val undoShown = undoShownState.value

    // 是否在压栈页（Tab 层 = home 目的地）。转场与返回栈由标准 navigation-compose 接管。
    val backStack by nav.controller.currentBackStackEntryAsState()
    val onRoute = backStack?.destination?.route != null && backStack?.destination?.route != HOME

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

            is DeepLink.Course -> {
                nav.push(Route.CourseEdit(id = l.id))
            }

            DeepLink.Today -> {
                nav.clearToRoot()
                nav.tab = Tab.TODAY
            }

            DeepLink.Todo -> {
                nav.clearToRoot()
                nav.tab = Tab.TODO
            }

            DeepLink.Timetable -> {
                nav.clearToRoot()
                nav.tab = Tab.TIMETABLE
            }

            DeepLink.Parcel -> {
                nav.push(Route.Parcel)
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
                nav.push(Route.TodoEdit(text = l.text, courseId = l.courseId))
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

    val back =
        remember(nav) {
            {
                nav.pop()
                Unit
            }
        }

    var modalContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val modalHost =
        remember {
            object : ModalHost {
                override fun show(content: @Composable () -> Unit) {
                    modalContent = content
                }

                override fun hide() {
                    modalContent = null
                }
            }
        }

    CompositionLocalProvider(
        LocalApp provides app,
        LocalActions provides actions,
        LocalNav provides nav,
        LocalModalHost provides modalHost,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 系统 SplashScreen 负责退场动画；数据未就绪时先挂载空 HOME 的 NavHost，
            // ready 翻真后直接挂载 Tabs，省掉根级 AnimatedVisibility 的离屏 graphicsLayer 与 280ms 缩放动画开销。
            Box(Modifier.fillMaxSize()) {
                if (ready) {
                    // 1. 常驻底层 Tabs：永远不被 NavHost pop/push 销毁。
                    // 二级页推入与弹出时 Tabs 常驻底层，返回时零重建，滚动位置与状态完全保留。
                    Tabs(nav, onRoute)
                }

                // 2. 覆盖层 NavHost：二级页面从右侧推入覆盖常驻 Tabs 之上；
                // HOME 目的地为空占位，在 HOME 时底层 Tabs 自然显露。
                val graph: NavGraphBuilder.() -> Unit =
                    remember(nav, back) {
                        {
                            composable(HOME) {
                                // 首页空占位：由常驻底层 Tabs 负责渲染与交互
                            }
                            gokeDestinations(nav, back)
                        }
                    }
                NavHost(
                    nav.controller,
                    startDestination = HOME,
                    modifier = Modifier.fillMaxSize(),
                    // 纯水平位移转场：二级页从右侧完全推入覆盖，返回时右滑移出揭露底层；
                    // 零整屏离屏 Alpha FBO 硬件层混合开销。
                    enterTransition = { slideInHorizontally(slideIn) { it } },
                    exitTransition = { slideOutHorizontally(slideOut) { -it / 5 } },
                    popEnterTransition = { slideInHorizontally(slideIn) { -it / 5 } },
                    popExitTransition = { slideOutHorizontally(slideOut) { it } },
                    builder = graph,
                )
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

            // 顶层模态插槽（覆盖底栏与 NavHost）
            modalContent?.invoke()

            if (pendingUpdate != null) {
                val context = LocalContext.current
                UpdateAvailableDialog(
                    info = pendingUpdate,
                    updateChecker = app.updateChecker,
                    downloadScope = app.scope,
                    onDismiss = { app.updateChecker.dismissUpdatePrompt(pendingUpdate.version) },
                    onOpenUrl = { url -> openExternalUrl(context, url) },
                )
            }
        }
    }
}

private fun safeBack(
    nav: Nav,
    entry: NavBackStackEntry,
    back: () -> Unit,
): () -> Unit =
    {
        when {
            nav.controller.currentBackStackEntry?.id == entry.id -> {
                back()
            }

            nav.controller.currentBackStack.value
                .any { it.id == entry.id } -> {
                nav.controller.popBackStack(entry.destination.id, inclusive = true)
            }
        }
    }

@Composable
private fun RouteHost(
    nav: Nav,
    entry: NavBackStackEntry,
    back: () -> Unit,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val onBack = remember(nav, entry, back) { safeBack(nav, entry, back) }
    val current by nav.controller.currentBackStackEntryAsState()
    // 拦截系统返回（优先级高于 NavHost 内部 PredictiveBackHandler，低于子页内部 BackHandler）：
    // 规避 navigation-compose 2.9.5 中 PredictiveBackHandler 在栈深降至 1 取消协程时
    // 跳过 `inPredictiveBack = false` 重置，导致 NavHost 过渡永久卡死、所有二级页无法退出的缺陷。
    BackHandler(enabled = current?.id == entry.id) { onBack() }
    content(onBack)
}

/** 全部压栈页目的地；Tab 层（home）由宿主自行 composable 定义（正式 App 是 Tabs，测试是内容桩）。 */
fun NavGraphBuilder.gokeDestinations(
    nav: Nav,
    back: () -> Unit,
) {
    composable<Route.EditTimetable> { e -> RouteHost(nav, e, back) { EditTimetableScreen(it) } }
    composable<Route.Appearance> { e -> RouteHost(nav, e, back) { AppearanceScreen(it) } }
    composable<Route.Widgets> { e -> RouteHost(nav, e, back) { WidgetsScreen(it) } }
    composable<Route.Permission> { e -> RouteHost(nav, e, back) { ReliabilityScreen(it) } }
    composable<Route.Trash> { e -> RouteHost(nav, e, back) { TrashScreen(it) } }
    composable<Route.History> { e -> RouteHost(nav, e, back) { HistoryScreen(it) } }
    composable<Route.Parcel> { e -> RouteHost(nav, e, back) { ParcelScreen(it) } }
    composable<Route.Mcp> { e -> RouteHost(nav, e, back) { McpScreen(it) } }
    composable<Route.LanWeb> { e -> RouteHost(nav, e, back) { LanWebScreen(it) } }
    composable<Route.Periods> { e -> RouteHost(nav, e, back) { PeriodsScreen(it) } }
    composable<Route.About> { e -> RouteHost(nav, e, back) { AboutScreen(it) } }
    composable<Route.Data> { e -> RouteHost(nav, e, back) { DataScreen(it) } }
    composable<Route.Sync> { e -> RouteHost(nav, e, back) { SyncScreen(it) } }
    composable<Route.Jxfw> { e -> RouteHost(nav, e, back) { JxfwScreen(it) } }
    composable<Route.Grades> { e -> RouteHost(nav, e, back) { GradesScreen(it) } }
    composable<Route.PhysicalTest> { e -> RouteHost(nav, e, back) { PhysicalTestScreen(it) } }
    composable<Route.Classroom> { e -> RouteHost(nav, e, back) { ClassroomScreen(it) } }
    composable<Route.Reliability> { e -> RouteHost(nav, e, back) { ReliabilityScreen(it) } }
    composable<Route.Import> { e -> RouteHost(nav, e, back) { ImportScreen(e.toRoute<Route.Import>().uri, it) } }
    composable<Route.CourseEdit> { e ->
        val r = e.toRoute<Route.CourseEdit>()
        RouteHost(nav, e, back) { CourseEditScreen(r.id, r.initialDow, r.initialPeriod, it) }
    }
    composable<Route.TodoEdit> { e ->
        val r = e.toRoute<Route.TodoEdit>()
        RouteHost(nav, e, back) { onBack ->
            TodoEditScreen(
                id = r.id,
                dateArg = r.date,
                startArg = r.start,
                endArg = r.end,
                textDraft = r.text,
                courseArg = r.courseId,
                extraDatesArg = r.extraDates,
                noteDraft = r.note,
                locationArg = r.location,
                listArg = r.listId,
                remindArg = r.remindMinutes,
                remindPersistentArg = r.remindPersistent,
                repeatFreqArg = r.repeatFreq,
                repeatIntervalArg = r.repeatInterval,
                repeatUntilArg = r.repeatUntil,
                repeatByDaysArg = r.repeatByDays,
                repeatByMonthsArg = r.repeatByMonths,
                onBack = onBack,
            )
        }
    }
}

/**
 * 撤销浮囊：有提示文案时展示紧凑胶囊（图标 + 单行截断说明 +「撤销」），无文案时退化为 56dp 圆钮；
 * 最大宽度限制为 220dp，保证左侧留足净空，不与右下角 MintFab 或居中 BulkBar 碰撞。到 UNDO_MS 自动收起。
 */
@Composable
private fun UndoButton(
    label: String,
    token: Long,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
) {
    LaunchedEffect(token) {
        delay(UNDO_MS.toLong())
        onTimeout()
    }
    val undoText = stringResource(R.string.undo)
    val a11yDesc = if (label.isNotBlank()) "$label，$undoText" else undoText
    val baseModifier =
        if (label.isBlank()) {
            Modifier.size(56.dp)
        } else {
            Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp, max = 220.dp)
        }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier =
            baseModifier
                .mintClickable(role = Role.Button) { onUndo() }
                .semantics(mergeDescendants = true) { contentDescription = a11yDesc },
    ) {
        if (label.isBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    undoText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val UNDO_MS = 4500

@Composable
private fun Tabs(
    nav: Nav,
    onRoute: Boolean,
) {
    // 首次访问再懒挂载 + 空闲期静默预热：
    // 首屏冷启动只挂载默认 Tab（TIMETABLE），保证冷启 TTID/TTFD 零额外负担；
    // 启动后避开首屏关键路径（500ms 后），在后台每 150ms 逐个静默预热未挂载 Tab，
    // 使后续切 Tab 几乎零阻塞，消除 TabSwitch 尾帧 OVR P99 尖峰。
    val visited = remember { mutableStateMapOf<Tab, Boolean>().apply { put(nav.tab, true) } }
    SideEffect {
        visited[nav.tab] = true
    }
    LaunchedEffect(Unit) {
        delay(500)
        for (tab in Tab.entries) {
            if (visited[tab] != true) {
                visited[tab] = true
                delay(150)
            }
        }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.tab) { focusManager.clearFocus(force = true) }

    val onRouteState = rememberUpdatedState(onRoute)
    val notOnRouteState = rememberUpdatedState(!onRoute)

    Box(
        Modifier
            .fillMaxSize()
            .tabSemantics(notOnRouteState)
            .blockPointer(onRouteState),
    ) {
        Tab.entries.forEach { tab ->
            val isCurrentTab = tab == nav.tab
            if (visited[tab] == true || isCurrentTab) {
                key(tab) {
                    val isTabActive = isCurrentTab && !onRoute
                    val visibleState = remember(tab) { mutableStateOf(isTabActive) }
                    visibleState.value = isTabActive

                    val blockedState = remember(tab) { mutableStateOf(!isTabActive) }
                    blockedState.value = !isTabActive

                    // 保持 Modifier 链结构绝对稳定：
                    // - graphicsLayer 在 draw 阶段读取透明度，切 Tab 时不触发子树重组与 measure/layout
                    // - tabSemantics 保持 Modifier 节点结构稳定，仅在内部设置 invisibleToUser
                    // - blockPointer 保持 pointerInput 节点稳定，在触摸事件处理时读取状态并拦截
                    Box(
                        Modifier
                            .zIndex(if (isCurrentTab) 1f else 0f)
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isCurrentTab) 1f else 0f }
                            .tabSemantics(visibleState)
                            .blockPointer(blockedState),
                    ) {
                        CompositionLocalProvider(LocalTabVisible provides visibleState) {
                            when (tab) {
                                Tab.TODAY -> TodayScreen()
                                Tab.TIMETABLE -> TimetableScreen()
                                Tab.CALENDAR -> CalendarScreen()
                                Tab.TODO -> TodoScreen()
                                Tab.ME -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }

        // zIndex(2f)：底栏必须压在所有 Tab 绘制层之上，否则被全屏 Tab 层挡住点击。
        // 快速添加浮层是模态层，二级路由已推入覆盖时底栏退场。
        AnimatedVisibility(
            visible = !nav.composerOpen && !onRoute,
            enter = fadeIn(MintTheme.spec()) + slideInVertically(MintTheme.spec()) { it / 2 },
            exit = fadeOut(MintTheme.spec()) + slideOutVertically(MintTheme.spec()) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
        ) {
            FloatingNavBar(nav.tab, nav::select, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 隐藏页面拦截所有指针事件（Initial 消费），防止穿透到下层 Tab 的滚动/点击。保持 pointerInput 结构稳定。 */
private fun Modifier.blockPointer(shouldBlockState: State<Boolean>): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (shouldBlockState.value) {
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/** 语义门控：隐藏态设置 hideFromAccessibility，保持 Modifier 节点稳定不动态增删。 */
private fun Modifier.tabSemantics(visibleState: State<Boolean>): Modifier =
    semantics {
        if (!visibleState.value) {
            hideFromAccessibility()
        }
    }
