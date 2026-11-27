package app.goke.ui

import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
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
import app.goke.ui.components.ProvideMintModalScope
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
import app.goke.ui.screens.HotWaterBillsScreen
import app.goke.ui.screens.HotWaterScreen
import app.goke.ui.screens.ImportScreen
import app.goke.ui.screens.JxfwScreen
import app.goke.ui.screens.LanWebScreen
import app.goke.ui.screens.McpScreen
import app.goke.ui.screens.ParcelScreen
import app.goke.ui.screens.PeriodsScreen
import app.goke.ui.screens.PhysicalTestScreen
import app.goke.ui.screens.PreferencesScreen
import app.goke.ui.screens.ReliabilityScreen
import app.goke.ui.screens.RemindersScreen
import app.goke.ui.screens.SettingsScreen
import app.goke.ui.screens.SyncScreen
import app.goke.ui.screens.TimetableScreen
import app.goke.ui.screens.TimetableShareScreen
import app.goke.ui.screens.TodayScreen
import app.goke.ui.screens.TodoEditScreen
import app.goke.ui.screens.TodoScreen
import app.goke.ui.screens.TrashScreen
import app.goke.ui.screens.UpdateAvailableDialog
import app.goke.ui.screens.WidgetsScreen
import app.goke.ui.screens.openExternalUrl
import app.goke.ui.theme.LocalReduceMotion
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 允许编辑页在不拦截普通预测性返回手势的前提下，接管确认退出逻辑（例如已有条目修改后自动保存并退栈）。
 * 仅在需要弹出「放弃更改」确认弹窗时才由页面注册 [BackHandler]。
 */
internal class RouteBackHolder {
    var onCommitBack: (() -> Unit)? = null
}

internal val LocalRouteBackHolder = staticCompositionLocalOf<RouteBackHolder?> { null }

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
    val reduce = LocalReduceMotion.current
    val motion = remember { RouteStackMotion(nav.topEntry) }

    // Tab 层：非默认 Tab（周历）返回回周历，带轻量预测性返回手势反馈。
    PredictiveBackHandler(enabled = ready && !onRoute && nav.tab != Tab.TIMETABLE) { progress ->
        var startY: Float? = null
        try {
            progress.collect { event ->
                val y0 = startY ?: event.touchY.also { startY = it }
                motion.tabSwipeEdge = event.swipeEdge
                motion.tabBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                motion.tabDeltaY.snapTo(event.touchY - y0)
            }
            val hadProgress = motion.tabBackProgress.value > 0.01f
            nav.tab = Tab.TIMETABLE
            motion.recoverTabBack(scope = scope, animate = hadProgress && !reduce)
        } catch (e: CancellationException) {
            motion.recoverTabBack(scope = scope, animate = motion.tabBackProgress.value > 0f && !reduce)
            throw e
        }
    }
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

            DeepLink.HotWater -> {
                nav.push(Route.HotWater)
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
            ProvideMintModalScope(active = true) {
                Box(Modifier.fillMaxSize()) {
                    if (ready) {
                        Tabs(nav, onRoute, motion)
                    }
                    RouteStackHost(nav = nav, modifier = Modifier.fillMaxSize(), motion = motion)
                }

                // 撤销：左下角一个圆钮，3 秒后自己消失。放左边就不和右下角的添加键打架，
                // 也不用一条通栏横条去盖列表内容。
                AnimatedVisibility(
                    visible = undoPrompt != null && !nav.composerOpen && !nav.sheetOpen,
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
}

private data class PredictiveExitSnapshot(
    val progress: Float,
    val swipeEdge: Int,
    val deltaY: Float,
)

@Stable
private class RouteStackMotion(
    initialEntry: BackEntry?,
) {
    var currentEntry by mutableStateOf(initialEntry)
    var outgoingEntry by mutableStateOf<BackEntry?>(null)
    var inTransition by mutableStateOf(false)
    var pushing by mutableStateOf(true)
    var predictiveExit by mutableStateOf<PredictiveExitSnapshot?>(null)
    var transitionSeq by mutableIntStateOf(0)
    var anim by mutableStateOf(Animatable(1f))

    var livePredictiveId by mutableStateOf<Long?>(null)
    val livePredictiveProgress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var liveSwipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    val liveDeltaY: Animatable<Float, AnimationVector1D> = Animatable(0f)

    val tabBackProgress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var tabSwipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    val tabDeltaY: Animatable<Float, AnimationVector1D> = Animatable(0f)

    fun syncTarget(
        targetTop: BackEntry?,
        reduce: Boolean,
    ) {
        val prevCurrent = currentEntry
        if (targetTop?.id == prevCurrent?.id) return
        val prevOutgoing = outgoingEntry
        val wasInTransition = inTransition
        val prevAnimVal = anim.value
        val isPush = (targetTop?.depth ?: 0) >= (prevCurrent?.depth ?: 0)

        val liveP = if (prevCurrent != null && livePredictiveId == prevCurrent.id) livePredictiveProgress.value else 0f
        val exitSnapshot =
            if (!isPush && prevCurrent != null && liveP > 0.01f && !reduce) {
                PredictiveExitSnapshot(
                    progress = liveP.coerceIn(0f, 1f),
                    swipeEdge = liveSwipeEdge,
                    deltaY = liveDeltaY.value,
                )
            } else {
                null
            }

        val isReverse =
            wasInTransition &&
                exitSnapshot == null &&
                prevOutgoing?.id == targetTop?.id &&
                prevAnimVal < 0.99f &&
                pushing != isPush
        val startProgress =
            when {
                reduce -> 1f
                isReverse -> (1f - prevAnimVal).coerceIn(0f, 1f)
                else -> 0f
            }

        if (prevCurrent != null && livePredictiveId == prevCurrent.id) {
            livePredictiveId = null
        }
        outgoingEntry = if (reduce) null else prevCurrent
        inTransition = !reduce
        pushing = isPush
        predictiveExit = exitSnapshot
        currentEntry = targetTop
        transitionSeq += 1
        anim = Animatable(startProgress)
    }

    suspend fun onPredictiveProgress(
        entryId: Long,
        progress: Float,
        swipeEdge: Int,
        deltaY: Float,
    ) {
        livePredictiveId = entryId
        liveSwipeEdge = swipeEdge
        livePredictiveProgress.snapTo(progress.coerceIn(0f, 1f))
        liveDeltaY.snapTo(deltaY)
    }

    fun cancelPredictive(
        entryId: Long,
        scope: CoroutineScope,
        reduce: Boolean,
    ) {
        if (livePredictiveId != entryId) return
        scope.launch {
            if (livePredictiveId != entryId) return@launch
            liveDeltaY.snapTo(0f)
            if (livePredictiveProgress.value > 0f && !reduce) {
                livePredictiveProgress.animateTo(0f, cancelSpringSpec())
            } else {
                livePredictiveProgress.snapTo(0f)
            }
            if (livePredictiveId == entryId) {
                livePredictiveId = null
            }
        }
    }

    fun recoverTabBack(
        scope: CoroutineScope,
        animate: Boolean,
    ) {
        scope.launch {
            tabDeltaY.snapTo(0f)
            if (animate && tabBackProgress.value > 0f) {
                tabBackProgress.animateTo(0f, cancelSpringSpec())
            } else {
                tabBackProgress.snapTo(0f)
            }
        }
    }
}

private fun stackSpringSpec() =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 750f,
        visibilityThreshold = 0.0005f,
    )

private fun cancelSpringSpec() =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f,
        visibilityThreshold = 0.001f,
    )

private enum class LayerRole {
    ACTIVE,
    OUTGOING,
    PEEK,
}

private data class StackLayer(
    val entry: BackEntry,
    val role: LayerRole,
)

/**
 * 二级页返回栈宿主（Nav3 风格状态驱动）：
 * - 以 [Nav.topEntry] 驱动同步水平位移与预测性返回手势（缩放/圆角/视差与底栏同步，零整屏 Alpha FBO 常驻开销）。
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
    val motion = remember { RouteStackMotion(nav.topEntry) }
    RouteStackHost(nav = nav, modifier = modifier, motion = motion)
}

@Composable
private fun RouteStackHost(
    nav: Nav,
    modifier: Modifier,
    motion: RouteStackMotion,
) {
    val reduce = LocalReduceMotion.current
    val targetTop = nav.topEntry
    motion.syncTarget(targetTop, reduce)

    LaunchedEffect(motion.transitionSeq, reduce) {
        if (motion.transitionSeq > 0) {
            motion.livePredictiveId = null
            motion.livePredictiveProgress.snapTo(0f)
            motion.liveDeltaY.snapTo(0f)
            if (!reduce) {
                motion.anim.animateTo(1f, stackSpringSpec())
            } else {
                motion.anim.snapTo(1f)
            }
            motion.inTransition = false
            motion.outgoingEntry = null
            motion.predictiveExit = null
        }
    }

    val saveableHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val scrimColor = MaterialTheme.colorScheme.scrim

    val current = motion.currentEntry
    val outgoing = motion.outgoingEntry
    val inTransition = motion.inTransition
    val peekEntry =
        if (!inTransition &&
            current != null &&
            motion.livePredictiveId == current.id &&
            motion.livePredictiveProgress.value > 0.001f &&
            nav.stack.size >= 2
        ) {
            nav.stack.getOrNull(nav.stack.lastIndex - 1)
        } else {
            null
        }

    val layers =
        buildList {
            if (peekEntry != null && peekEntry.id != current?.id) {
                add(StackLayer(peekEntry, LayerRole.PEEK))
            }
            if (outgoing != null && outgoing.id != current?.id) {
                add(StackLayer(outgoing, LayerRole.OUTGOING))
            }
            if (current != null) {
                add(StackLayer(current, LayerRole.ACTIVE))
            }
        }

    Box(modifier) {
        for (layer in layers) {
            val entry = layer.entry
            key(entry.id) {
                val isTop = layer.role == LayerRole.ACTIVE && nav.topEntry?.id == entry.id
                val blockedState = rememberUpdatedState(!isTop)
                val visibleState = rememberUpdatedState(isTop)

                DisposableEffect(entry.id) {
                    onDispose {
                        if (nav.stack.none { it.id == entry.id }) {
                            saveableHolder.removeState(entry.id)
                        }
                    }
                }

                val layerZ =
                    when (layer.role) {
                        LayerRole.PEEK -> 0f
                        LayerRole.OUTGOING -> if (motion.pushing) 0f else 2f
                        LayerRole.ACTIVE -> if (inTransition && !motion.pushing) 0f else 2f
                    }

                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(layerZ)
                        .graphicsLayer {
                            val w = size.width
                            val maxTy = 20.dp.toPx()
                            when (layer.role) {
                                LayerRole.PEEK -> {
                                    val p = motion.livePredictiveProgress.value.coerceIn(0f, 1f)
                                    val rem = 1f - p
                                    val sc = 1f - 0.04f * rem
                                    scaleX = sc
                                    scaleY = sc
                                    translationX = -w * 0.03f * rem
                                    clip = false
                                    shadowElevation = 0f
                                }

                                LayerRole.OUTGOING -> {
                                    val t = motion.anim.value.coerceIn(0f, 1f)
                                    val pred = motion.predictiveExit
                                    when {
                                        motion.pushing -> {
                                            translationX = -w * 0.20f * t
                                            clip = false
                                            shadowElevation = 0f
                                        }

                                        pred != null -> {
                                            val p0 = pred.progress
                                            val dir = if (pred.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                                            val sc = (1f - 0.10f * p0) - 0.08f * t
                                            val startTx = dir * w * 0.035f * p0
                                            val startTy = (pred.deltaY * 0.10f).coerceIn(-maxTy, maxTy)
                                            val corner = 28f * (p0 + (1f - p0) * t)
                                            val a = (1f - t * 1.15f).coerceIn(0f, 1f)
                                            scaleX = sc
                                            scaleY = sc
                                            translationX = startTx + dir * w * 0.10f * t
                                            translationY = startTy * (1f - 0.3f * t)
                                            alpha = a
                                            clip = true
                                            shape = RoundedCornerShape(corner.dp)
                                            shadowElevation = (12f * (p0 + (1f - p0) * t) * a).dp.toPx()
                                        }

                                        else -> {
                                            translationX = w * t
                                            clip = false
                                            shape = RectangleShape
                                            shadowElevation = (8f * (1f - t)).dp.toPx()
                                        }
                                    }
                                }

                                LayerRole.ACTIVE -> {
                                    val t = motion.anim.value.coerceIn(0f, 1f)
                                    val pred = motion.predictiveExit
                                    val liveP =
                                        if (!inTransition && motion.livePredictiveId == entry.id) {
                                            motion.livePredictiveProgress.value.coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                    when {
                                        inTransition && motion.pushing -> {
                                            translationX = w * (1f - t)
                                            clip = false
                                            shape = RectangleShape
                                            shadowElevation = (8f * (1f - t)).dp.toPx()
                                        }

                                        inTransition && !motion.pushing && pred != null -> {
                                            val rem = (1f - pred.progress) * (1f - t)
                                            val sc = 1f - 0.04f * rem
                                            scaleX = sc
                                            scaleY = sc
                                            translationX = -w * 0.03f * rem
                                            clip = false
                                            shadowElevation = 0f
                                        }

                                        inTransition && !motion.pushing -> {
                                            translationX = -w * 0.20f * (1f - t)
                                            clip = false
                                            shadowElevation = 0f
                                        }

                                        liveP > 0.001f -> {
                                            val dir = if (motion.liveSwipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                                            val sc = 1f - 0.10f * liveP
                                            scaleX = sc
                                            scaleY = sc
                                            translationX = dir * w * 0.035f * liveP
                                            translationY = (motion.liveDeltaY.value * 0.10f).coerceIn(-maxTy, maxTy)
                                            clip = true
                                            shape = RoundedCornerShape((28f * liveP).dp)
                                            shadowElevation = (12f * liveP).dp.toPx()
                                        }

                                        else -> {
                                            scaleX = 1f
                                            scaleY = 1f
                                            translationX = 0f
                                            translationY = 0f
                                            alpha = 1f
                                            clip = false
                                            shadowElevation = 0f
                                        }
                                    }
                                }
                            }
                        }.drawWithContent {
                            drawContent()
                            val scrimAlpha =
                                when (layer.role) {
                                    LayerRole.PEEK -> {
                                        0.14f * (1f - motion.livePredictiveProgress.value.coerceIn(0f, 1f))
                                    }

                                    LayerRole.OUTGOING -> {
                                        if (motion.pushing) 0.14f * motion.anim.value.coerceIn(0f, 1f) else 0f
                                    }

                                    LayerRole.ACTIVE -> {
                                        if (inTransition && !motion.pushing) {
                                            val t = motion.anim.value.coerceIn(0f, 1f)
                                            val pred = motion.predictiveExit
                                            if (pred != null) 0.14f * (1f - pred.progress) * (1f - t) else 0.14f * (1f - t)
                                        } else {
                                            0f
                                        }
                                    }
                                }
                            if (scrimAlpha > 0.001f) {
                                drawRect(scrimColor.copy(alpha = scrimAlpha))
                            }
                        }.tabSemantics(visibleState)
                        .blockPointer(blockedState),
                ) {
                    saveableHolder.SaveableStateProvider(entry.id) {
                        RouteHost(
                            nav = nav,
                            entry = entry,
                            isTop = isTop,
                            motion = motion,
                            scope = scope,
                            reduce = reduce,
                        ) { onBack ->
                            RouteContent(route = entry.route, onBack = onBack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteHost(
    nav: Nav,
    entry: BackEntry,
    isTop: Boolean,
    motion: RouteStackMotion,
    scope: CoroutineScope,
    reduce: Boolean,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val onBack =
        remember(nav, entry.id) {
            {
                nav.pop(entry.id)
                Unit
            }
        }
    val backHolder = remember(entry.id) { RouteBackHolder() }
    val rootOwner = LocalNavigationEventDispatcherOwner.current
    val scopedOwner = rememberNavigationEventDispatcherOwner(enabled = isTop, parent = rootOwner)
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides scopedOwner,
        LocalRouteBackHolder provides backHolder,
    ) {
        PredictiveBackHandler(enabled = isTop) { progress ->
            var startY: Float? = null
            try {
                progress.collect { event ->
                    val y0 = startY ?: event.touchY.also { startY = it }
                    motion.onPredictiveProgress(
                        entryId = entry.id,
                        progress = event.progress,
                        swipeEdge = event.swipeEdge,
                        deltaY = event.touchY - y0,
                    )
                }
                val commit = backHolder.onCommitBack ?: onBack
                commit()
                if (nav.topEntry?.id == entry.id) {
                    motion.cancelPredictive(entry.id, scope, reduce)
                }
            } catch (e: CancellationException) {
                motion.cancelPredictive(entry.id, scope, reduce)
                throw e
            }
        }
        ProvideMintModalScope(active = isTop) {
            content(onBack)
        }
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

        Route.Preferences -> {
            PreferencesScreen(onBack)
        }

        Route.Reminders -> {
            RemindersScreen(onBack)
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

        Route.HotWater -> {
            HotWaterScreen(onBack)
        }

        Route.HotWaterBills -> {
            HotWaterBillsScreen(onBack)
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

        Route.TimetableShare -> {
            TimetableShareScreen(onBack)
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
                courseWeekArg = route.courseWeek,
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
    motion: RouteStackMotion,
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
    val scrimColor = MaterialTheme.colorScheme.scrim

    val rootOwner = LocalNavigationEventDispatcherOwner.current

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val w = size.width
                val outgoing = motion.outgoingEntry
                val current = motion.currentEntry
                val inTransition = motion.inTransition
                val t = motion.anim.value.coerceIn(0f, 1f)
                val pred = motion.predictiveExit
                val liveP =
                    if (!inTransition && current != null && current.depth == 1 && motion.livePredictiveId == current.id) {
                        motion.livePredictiveProgress.value.coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                when {
                    !inTransition && current != null && current.depth == 1 && liveP > 0.001f -> {
                        val rem = 1f - liveP
                        val sc = 1f - 0.04f * rem
                        scaleX = sc
                        scaleY = sc
                        translationX = -w * 0.03f * rem
                    }

                    // Pushing from Tabs (depth 0) -> depth 1
                    inTransition && motion.pushing && current != null && current.depth == 1 && outgoing == null -> {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = -w * 0.20f * t
                    }

                    // Popping back to Tabs (current == null)
                    inTransition && !motion.pushing && current == null -> {
                        if (pred != null) {
                            val rem = (1f - pred.progress) * (1f - t)
                            val sc = 1f - 0.04f * rem
                            scaleX = sc
                            scaleY = sc
                            translationX = -w * 0.03f * rem
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                            translationX = -w * 0.20f * (1f - t)
                        }
                    }

                    else -> {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                    }
                }
            }.drawWithContent {
                drawContent()
                val outgoing = motion.outgoingEntry
                val current = motion.currentEntry
                val inTransition = motion.inTransition
                val t = motion.anim.value.coerceIn(0f, 1f)
                val pred = motion.predictiveExit
                val liveP =
                    if (!inTransition && current != null && current.depth == 1 && motion.livePredictiveId == current.id) {
                        motion.livePredictiveProgress.value.coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val scrimAlpha =
                    when {
                        !inTransition && current != null && current.depth == 1 && liveP > 0.001f -> {
                            0.14f * (1f - liveP)
                        }

                        inTransition && motion.pushing && current != null && current.depth == 1 && outgoing == null -> {
                            0.14f * t
                        }

                        inTransition && !motion.pushing && current == null -> {
                            if (pred != null) 0.14f * (1f - pred.progress) * (1f - t) else 0.14f * (1f - t)
                        }

                        else -> {
                            0f
                        }
                    }
                if (scrimAlpha > 0.001f) {
                    drawRect(scrimColor.copy(alpha = scrimAlpha))
                }
            }.tabSemantics(notOnRouteState)
            .blockPointer(onRouteState),
    ) {
        val tabSheetOpen = remember { mutableStateMapOf<Tab, Boolean>() }
        val activeSheetOpen = !onRoute && tabSheetOpen[nav.tab] == true
        LaunchedEffect(activeSheetOpen) {
            nav.sheetOpen = activeSheetOpen
        }
        DisposableEffect(Unit) {
            onDispose { nav.sheetOpen = false }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val tp = motion.tabBackProgress.value.coerceIn(0f, 1f)
                    if (tp > 0.001f && !onRoute) {
                        val dir = if (motion.tabSwipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        val maxTy = 16.dp.toPx()
                        val sc = 1f - 0.05f * tp
                        scaleX = sc
                        scaleY = sc
                        translationX = dir * size.width * 0.03f * tp
                        translationY = (motion.tabDeltaY.value * 0.08f).coerceIn(-maxTy, maxTy)
                        alpha = 1f - 0.15f * tp
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        translationY = 0f
                        alpha = 1f
                    }
                },
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
                                ProvideMintModalScope(
                                    active = isTabActive,
                                    onSheetOpenChange = { open -> tabSheetOpen[tab] = open },
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
            }
        }

        // zIndex(2f)：底栏必须压在所有 Tab 绘制层之上，否则被全屏 Tab 层挡住点击。
        // 仅在快速添加或底部抽屉模态层展开时让位，路由推入/返回时随底层 Tabs 一起平顺视差移动。
        AnimatedVisibility(
            visible = !nav.composerOpen && !nav.sheetOpen,
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
