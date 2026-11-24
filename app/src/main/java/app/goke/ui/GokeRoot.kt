package app.goke.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.goke.DeepLink
import app.goke.GokeApp
import app.goke.R
import app.goke.data.ReleaseInfo
import app.goke.data.UpdateChecker
import app.goke.ui.components.ErrorReportDialog
import app.goke.ui.components.FloatingNavBar
import app.goke.ui.components.NavClearance
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Undo
import app.goke.ui.screens.AboutScreen
import app.goke.ui.screens.AppearanceScreen
import app.goke.ui.screens.CalendarScreen
import app.goke.ui.screens.CalendarSyncScreen
import app.goke.ui.screens.ClassroomScreen
import app.goke.ui.screens.CourseEditScreen
import app.goke.ui.screens.CourseSelectionScreen
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

@Composable
fun GokeRoot(
    app: GokeApp,
    incoming: Uri?,
    initialTab: Tab = Tab.TIMETABLE,
    nav: Nav = rememberNav(initialTab),
    onIncomingHandled: () -> Unit,
) {
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

    val onRoute = nav.onRoute

    // Tab 层：非默认 Tab（周历）返回回周历。压栈页的返回由 RouteHost / 子页 BackHandler 接管。
    BackHandler(enabled = ready && !onRoute && nav.tab != Tab.TIMETABLE) { nav.tab = Tab.TIMETABLE }
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

    CompositionLocalProvider(
        LocalApp provides app,
        LocalActions provides actions,
        LocalNav provides nav,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 系统 SplashScreen 负责退场动画；ready 翻真后直接挂载常驻 Tabs，
            // 二级页由 RouteStackHost 从右侧推入覆盖，返回时底层 Tabs 零重建、状态完全保留。
            Box(Modifier.fillMaxSize()) {
                if (ready) {
                    Tabs(nav, onRoute)
                }
                RouteStackHost(nav = nav, modifier = Modifier.fillMaxSize())
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

            val pendingError by app.errors.pendingModal.collectAsState()
            val activeError = pendingError
            if (activeError != null) {
                ErrorReportDialog(
                    report = activeError,
                    onDismiss = { app.errors.dismissPending() },
                )
            } else if (pendingUpdate != null) {
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

/**
 * 二级页返回栈宿主（Nav3 风格状态驱动）：
 * - 以 [Nav.topEntry] 驱动 [AnimatedContent] 纯水平位移动画（零整屏 Alpha FBO 开销）。
 * - [rememberSaveableStateHolder] 按 entry.id 隔离状态，页面真正弹栈后在 onDispose 中回收。
 * - [RouteHost] 用 [rememberNavigationEventDispatcherOwner] 按 `isTop` 结构性隔离返回分发，
 *   退场中或被覆盖的二级页无法截获系统返回。
 * - [RouteContent] 对 [Route] sealed interface 做穷尽 `when` 分发，新增路由缺漏即编译期报错。
 */
@Composable
fun RouteStackHost(
    nav: Nav,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val enterEase = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exitEase = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val slideIn = if (reduce) snap<IntOffset>() else tween<IntOffset>(durationMillis = 260, easing = enterEase)
    val slideOut = if (reduce) snap<IntOffset>() else tween<IntOffset>(durationMillis = 200, easing = exitEase)
    val saveableHolder = rememberSaveableStateHolder()

    AnimatedContent(
        targetState = nav.topEntry,
        modifier = modifier,
        contentKey = { it?.id },
        transitionSpec = {
            val pushing = (targetState?.depth ?: 0) >= (initialState?.depth ?: 0)
            if (pushing) {
                (slideInHorizontally(slideIn) { it }) togetherWith
                    (slideOutHorizontally(slideOut) { -it / 5 }) using
                    SizeTransform(clip = false)
            } else {
                (slideInHorizontally(slideIn) { -it / 5 }) togetherWith
                    (slideOutHorizontally(slideOut) { it }) using
                    SizeTransform(clip = false)
            }.apply {
                targetContentZIndex = if (pushing) 1f else -1f
            }
        },
        label = "RouteStackHost",
    ) { entry ->
        if (entry == null) {
            return@AnimatedContent
        }
        val isTop = nav.topEntry?.id == entry.id
        DisposableEffect(entry.id) {
            onDispose {
                if (nav.stack.none { it.id == entry.id }) {
                    saveableHolder.removeState(entry.id)
                }
            }
        }
        saveableHolder.SaveableStateProvider(entry.id) {
            RouteHost(nav = nav, entry = entry, isTop = isTop) { onBack ->
                RouteContent(route = entry.route, onBack = onBack)
            }
        }
    }
}

@Composable
private fun RouteHost(
    nav: Nav,
    entry: BackEntry,
    isTop: Boolean,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val onBack =
        remember(nav, entry.id) {
            {
                nav.pop(entry.id)
                Unit
            }
        }
    val rootOwner = LocalNavigationEventDispatcherOwner.current
    val scopedOwner = rememberNavigationEventDispatcherOwner(enabled = isTop, parent = rootOwner)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides scopedOwner) {
        BackHandler(enabled = isTop) { onBack() }
        content(onBack)
    }
}

@Composable
private fun RouteContent(
    route: Route,
    onBack: () -> Unit,
) {
    when (route) {
        Route.EditTimetable -> {
            EditTimetableScreen(onBack)
        }

        Route.Appearance -> {
            AppearanceScreen(onBack)
        }

        Route.Widgets -> {
            WidgetsScreen(onBack)
        }

        Route.Permission -> {
            ReliabilityScreen(onBack)
        }

        Route.Trash -> {
            TrashScreen(onBack)
        }

        Route.History -> {
            HistoryScreen(onBack)
        }

        Route.Parcel -> {
            ParcelScreen(onBack)
        }

        Route.Mcp -> {
            McpScreen(onBack)
        }

        Route.LanWeb -> {
            LanWebScreen(onBack)
        }

        Route.Periods -> {
            PeriodsScreen(onBack)
        }

        Route.About -> {
            AboutScreen(onBack)
        }

        Route.Data -> {
            DataScreen(onBack)
        }

        Route.Sync -> {
            SyncScreen(onBack)
        }

        Route.CalendarSync -> {
            CalendarSyncScreen(onBack)
        }

        Route.Jxfw -> {
            JxfwScreen(onBack)
        }

        Route.Grades -> {
            GradesScreen(onBack)
        }

        Route.PhysicalTest -> {
            PhysicalTestScreen(onBack)
        }

        Route.Classroom -> {
            ClassroomScreen(onBack)
        }

        Route.CourseSelection -> {
            CourseSelectionScreen(onBack)
        }

        Route.Reliability -> {
            ReliabilityScreen(onBack)
        }

        is Route.Import -> {
            ImportScreen(route.uri, onBack)
        }

        is Route.CourseEdit -> {
            CourseEditScreen(route.id, route.initialDow, route.initialPeriod, onBack)
        }

        is Route.TodoEdit -> {
            TodoEditScreen(
                id = route.id,
                dateArg = route.date,
                startArg = route.start,
                endArg = route.end,
                textDraft = route.text,
                courseArg = route.courseId,
                extraDatesArg = route.extraDates,
                noteDraft = route.note,
                locationArg = route.location,
                listArg = route.listId,
                remindArg = route.remindMinutes,
                remindListArg = route.remindList,
                remindPersistentArg = route.remindPersistent,
                repeatFreqArg = route.repeatFreq,
                repeatIntervalArg = route.repeatInterval,
                repeatUntilArg = route.repeatUntil,
                repeatByDaysArg = route.repeatByDays,
                repeatByMonthsArg = route.repeatByMonths,
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

    val rootOwner = LocalNavigationEventDispatcherOwner.current

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

                    // 结构性隔离非活跃 Tab 的返回事件分发：隐藏态或被二级页覆盖时，
                    // 即使 Tab 内部页面漏检 LocalTabVisible 也绝不可能截获系统返回。
                    val tabNavEventOwner =
                        rememberNavigationEventDispatcherOwner(enabled = isTabActive, parent = rootOwner)

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
                        CompositionLocalProvider(
                            LocalTabVisible provides visibleState,
                            LocalNavigationEventDispatcherOwner provides tabNavEventOwner,
                        ) {
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
