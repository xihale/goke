package app.goke.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goke.R
import app.goke.core.jxfw.JxfwParse
import app.goke.core.jxfw.JxfwXkParse
import app.goke.core.jxfw.XkActionResult
import app.goke.core.jxfw.XkConflict
import app.goke.core.jxfw.XkConflictSource
import app.goke.core.jxfw.XkCourseDetail
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.jxfw.XkCreditWallet
import app.goke.core.jxfw.XkFailureKind
import app.goke.core.jxfw.XkOccupiedSlot
import app.goke.core.jxfw.XkOpenPlan
import app.goke.core.jxfw.XkScheduleSummary
import app.goke.core.jxfw.XkSlotConflict
import app.goke.core.jxfw.XkWindowStatus
import app.goke.debug.DebugHooks
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.ui.FeedbackEvent
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.Route
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.ConflictCard
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.Tag
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Checklist
import app.goke.ui.icons.Schedule
import app.goke.ui.icons.School
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private enum class XkTab { AVAILABLE, ENROLLED }

private enum class XkConfirmMode { ENROLL, DROP }

private data class XkConfirmRequest(
    val item: XkCourseItem,
    val mode: XkConfirmMode,
)

/** 「选课」：直连广工教务选课门户，支持窗口状态、学分钱包、时间冲突检测与选退课。 */
@Composable
fun CourseSelectionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    val jxfwState by app.jxfw.state.collectAsState()
    val xkState by app.jxfw.xkState.collectAsState()

    LaunchedEffect(jxfwState.loggedIn) {
        if (jxfwState.loggedIn && xkState.window == null && !xkState.loading) {
            app.jxfw.loadCourseSelection()
        }
    }

    CourseSelectionContent(
        loggedIn = jxfwState.loggedIn,
        xkState = xkState,
        onBack = onBack,
        onOpenJxfw = { nav.push(Route.Jxfw) },
        onRefresh = {
            scope.launch {
                app.jxfw
                    .loadCourseSelection(
                        searchKey = xkState.searchKey.ifBlank { "kcmc" },
                        searchValue = xkState.searchValue,
                        page = 1,
                    ).onSuccess {
                        actions.feedback(FeedbackEvent.SUCCESS)
                    }
            }
        },
        onSearch = { key, value ->
            scope.launch {
                app.jxfw.loadCourseSelection(
                    searchKey = key,
                    searchValue = value,
                    page = 1,
                )
            }
        },
        onLoadMore = {
            scope.launch {
                app.jxfw.loadCourseSelection(
                    searchKey = xkState.searchKey.ifBlank { "kcmc" },
                    searchValue = xkState.searchValue,
                    page = xkState.currentPage + 1,
                )
            }
        },
        onLoadDetail = { item -> app.jxfw.loadCourseDetail(item) },
        onEnroll = { item ->
            scope.launch {
                app.jxfw
                    .enrollCourse(item)
                    .onSuccess { res ->
                        when (res) {
                            is XkActionResult.Success -> {
                                actions.feedback(FeedbackEvent.SUCCESS)
                                actions.toast(ctx.getString(R.string.xk_enroll_success, item.displayName))
                            }

                            is XkActionResult.Failure -> {
                                actions.toast(formatXkFailure(ctx, res.kind, res.reason))
                            }
                        }
                    }.onFailure { err ->
                        actions.toast(err.message ?: ctx.getString(R.string.xk_err_other_default))
                    }
            }
        },
        onDrop = { item ->
            scope.launch {
                app.jxfw
                    .dropCourse(item)
                    .onSuccess { res ->
                        when (res) {
                            is XkActionResult.Success -> {
                                actions.feedback(FeedbackEvent.SUCCESS)
                                actions.toast(ctx.getString(R.string.xk_drop_success, item.displayName))
                            }

                            is XkActionResult.Failure -> {
                                actions.toast(formatXkFailure(ctx, res.kind, res.reason))
                            }
                        }
                    }.onFailure { err ->
                        actions.toast(err.message ?: ctx.getString(R.string.xk_err_other_default))
                    }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseSelectionContent(
    loggedIn: Boolean,
    xkState: JxfwCourseSelectionState,
    onBack: () -> Unit,
    onOpenJxfw: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onLoadDetail: suspend (XkCourseItem) -> Result<XkCourseDetail>,
    onEnroll: (XkCourseItem) -> Unit,
    onDrop: (XkCourseItem) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(XkTab.AVAILABLE) }
    var searchKey by rememberSaveable { mutableStateOf(xkState.searchKey.ifBlank { "kcmc" }) }
    var searchQuery by rememberSaveable { mutableStateOf(xkState.searchValue) }
    var onlyHasSeats by rememberSaveable { mutableStateOf(false) }
    var onlyNoConflict by rememberSaveable { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<XkCourseItem?>(null) }
    var confirmRequest by remember { mutableStateOf<XkConfirmRequest?>(null) }

    LaunchedEffect(xkState.searchKey, xkState.searchValue) {
        if (xkState.searchKey.isNotBlank()) {
            searchKey = xkState.searchKey
        }
        searchQuery = xkState.searchValue
    }

    val visibleAvailable =
        remember(xkState.available, onlyHasSeats, onlyNoConflict) {
            xkState.available.filter { item ->
                (!onlyHasSeats || !item.isFull) &&
                    (!onlyNoConflict || !item.hasConflict)
            }
        }

    MintScreen(
        title = stringResource(R.string.xk_title),
        onBack = onBack,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tag(
                    text = stringResource(R.string.xk_exp_badge),
                    bg = MaterialTheme.colorScheme.tertiaryContainer,
                    fg = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                if (loggedIn) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !xkState.loading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.xk_refresh_cd))
                    }
                }
            }
        },
    ) {
        if (!loggedIn) {
            EmptyState(
                title = stringResource(R.string.xk_not_logged_in_title),
                hint = stringResource(R.string.xk_not_logged_in_hint),
                icon = Icons.Rounded.School,
                actionLabel = stringResource(R.string.jxfw_card_bind_action),
                onAction = onOpenJxfw,
                modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
            return@MintScreen
        }

        if (xkState.window == null && xkState.loading && xkState.lastError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.xk_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@MintScreen
        }

        val window = xkState.window
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "debug-banner") {
                DebugHooks.CourseSelectionDebugBanner(onRefresh = onRefresh)
            }
            if (xkState.lastError != null) {
                item(key = "error") {
                    XkErrorBanner(
                        xkState = xkState,
                        onRetry = onRefresh,
                        onOpenJxfw = onOpenJxfw,
                    )
                }
            }

            item(key = "exp-disclaimer") {
                XkExpDisclaimerBanner()
            }

            if (window == null) return@LazyColumn

            if (!window.isOpen) {
                item(key = "closed-window") {
                    XkClosedWindowCard(window = window)
                }
                if (window.openPlans.isNotEmpty()) {
                    item(key = "open-plans-label") {
                        SectionLabel(stringResource(R.string.xk_open_plans_section))
                    }
                    items(window.openPlans, key = { "${it.jxjhdm}-${it.jhfxdm}-${it.planName}" }) { plan ->
                        XkOpenPlanCard(plan = plan)
                    }
                }
                if (xkState.enrolled.isNotEmpty()) {
                    item(key = "closed-enrolled-label") {
                        SectionLabel(stringResource(R.string.xk_tab_enrolled_count, xkState.enrolled.size))
                    }
                    items(xkState.enrolled, key = { "enrolled-${it.kcrwdm}" }) { item ->
                        XkCourseCard(
                            item = item,
                            canDrop = window.canDrop,
                            busy = xkState.actionBusyId == item.kcrwdm,
                            actionsDisabled = xkState.actionBusyId != null,
                            onClick = { selectedItem = item },
                            onDropClick = { confirmRequest = XkConfirmRequest(item, XkConfirmMode.DROP) },
                        )
                    }
                }
                return@LazyColumn
            }

            if (window.isOpen) {
                item(key = "open-summary") {
                    XkOpenSummaryCard(
                        window = window,
                        wallet = xkState.wallet,
                        loading = xkState.loading,
                    )
                }
            }

            item(key = "tabs") {
                val availableLabel =
                    if (xkState.availableTotal > 0) {
                        stringResource(R.string.xk_tab_available_count, xkState.availableTotal)
                    } else {
                        stringResource(R.string.xk_tab_available)
                    }
                val enrolledLabel = stringResource(R.string.xk_tab_enrolled_count, xkState.enrolled.size)
                SegmentedPill(
                    options = listOf(XkTab.AVAILABLE to availableLabel, XkTab.ENROLLED to enrolledLabel),
                    selected = tab,
                    onSelect = { tab = it },
                )
            }

            if (tab == XkTab.AVAILABLE) {
                item(key = "search-filters") {
                    XkSearchAndFilterPanel(
                        searchKey = searchKey,
                        searchQuery = searchQuery,
                        onlyHasSeats = onlyHasSeats,
                        onlyNoConflict = onlyNoConflict,
                        onSearchKeyChange = { searchKey = it },
                        onSearchQueryChange = { searchQuery = it },
                        onSubmitSearch = { onSearch(searchKey, searchQuery.trim()) },
                        onClearSearch = {
                            searchQuery = ""
                            onSearch(searchKey, "")
                        },
                        onToggleHasSeats = { onlyHasSeats = !onlyHasSeats },
                        onToggleNoConflict = { onlyNoConflict = !onlyNoConflict },
                    )
                }

                if (visibleAvailable.isEmpty() && !xkState.loading) {
                    item(key = "empty-available") {
                        EmptyState(
                            title = stringResource(R.string.xk_empty_available_title),
                            hint = stringResource(R.string.xk_empty_available_hint),
                            icon = Icons.Rounded.Checklist,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else {
                    items(visibleAvailable, key = { "avail-${it.kcrwdm}" }) { item ->
                        XkCourseCard(
                            item = item,
                            canDrop = window.canDrop,
                            busy = xkState.actionBusyId == item.kcrwdm,
                            actionsDisabled = xkState.actionBusyId != null,
                            onClick = { selectedItem = item },
                            onDropClick = { confirmRequest = XkConfirmRequest(item, XkConfirmMode.DROP) },
                        )
                    }
                    if (xkState.hasMore) {
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilledTonalButton(
                                    onClick = onLoadMore,
                                    enabled = !xkState.loadingMore && !xkState.loading,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    if (xkState.loadingMore) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.xk_loading_more))
                                    } else {
                                        Text(stringResource(R.string.xk_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (xkState.enrolled.isEmpty() && !xkState.loading) {
                    item(key = "empty-enrolled") {
                        EmptyState(
                            title = stringResource(R.string.xk_empty_enrolled_title),
                            hint = stringResource(R.string.xk_empty_enrolled_hint),
                            icon = Icons.Rounded.Checklist,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else {
                    items(xkState.enrolled, key = { "enrolled-${it.kcrwdm}" }) { item ->
                        XkCourseCard(
                            item = item,
                            canDrop = window.canDrop,
                            busy = xkState.actionBusyId == item.kcrwdm,
                            actionsDisabled = xkState.actionBusyId != null,
                            onClick = { selectedItem = item },
                            onDropClick = { confirmRequest = XkConfirmRequest(item, XkConfirmMode.DROP) },
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        XkCourseDetailSheet(
            item = item,
            occupiedSlots = xkState.occupiedSlots,
            canDrop = xkState.window?.canDrop ?: false,
            windowOpen = xkState.window?.isOpen ?: false,
            busy = xkState.actionBusyId == item.kcrwdm,
            onDismiss = { selectedItem = null },
            onLoadDetail = onLoadDetail,
            onEnrollClick = {
                selectedItem = null
                confirmRequest = XkConfirmRequest(item, XkConfirmMode.ENROLL)
            },
            onDropClick = {
                selectedItem = null
                confirmRequest = XkConfirmRequest(item, XkConfirmMode.DROP)
            },
        )
    }

    confirmRequest?.let { req ->
        val ctx = LocalContext.current
        val (title, body, confirmLabel, danger) =
            when (req.mode) {
                XkConfirmMode.ENROLL -> {
                    val bodyText =
                        if (req.item.hasConflict) {
                            val lines = req.item.conflicts.joinToString("\n") { formatConflictLine(ctx, it) }
                            stringResource(R.string.xk_confirm_enroll_conflict_body, req.item.displayName, lines)
                        } else {
                            stringResource(R.string.xk_confirm_enroll_body, req.item.displayName)
                        }
                    ConfirmMeta(
                        title = stringResource(R.string.xk_confirm_enroll_title),
                        body = bodyText,
                        confirm = stringResource(R.string.xk_action_enroll),
                        danger = req.item.hasConflict,
                    )
                }

                XkConfirmMode.DROP -> {
                    ConfirmMeta(
                        title = stringResource(R.string.xk_confirm_drop_title),
                        body = stringResource(R.string.xk_confirm_drop_body, req.item.displayName),
                        confirm = stringResource(R.string.xk_action_drop),
                        danger = true,
                    )
                }
            }
        ConfirmDialog(
            title = title,
            text = body,
            confirm = confirmLabel,
            danger = danger,
            onConfirm = {
                when (req.mode) {
                    XkConfirmMode.ENROLL -> onEnroll(req.item)
                    XkConfirmMode.DROP -> onDrop(req.item)
                }
            },
            onDismiss = { confirmRequest = null },
        )
    }
}

private data class ConfirmMeta(
    val title: String,
    val body: String,
    val confirm: String,
    val danger: Boolean,
)

@Composable
private fun XkExpDisclaimerBanner() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.xk_exp_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun XkErrorBanner(
    xkState: JxfwCourseSelectionState,
    onRetry: () -> Unit,
    onOpenJxfw: () -> Unit,
) {
    MintCard(color = MaterialTheme.colorScheme.errorContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            JxfwErrorGuidance(xkState.lastErrorKind, xkState.lastError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.xk_retry), color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onOpenJxfw,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.xk_relogin_jxfw), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun XkClosedWindowCard(window: XkWindowStatus) {
    MintCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.xk_window_closed_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                window.headerNotice.ifBlank { stringResource(R.string.xk_window_closed_hint) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            XkWindowBadges(window = window)
        }
    }
}

@Composable
private fun XkOpenPlanCard(plan: XkOpenPlan) {
    MintCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    plan.planName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (plan.planType.isNotBlank()) {
                    Tag(
                        text = plan.planType,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        fg = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (plan.startTime.isNotBlank() || plan.endTime.isNotBlank()) {
                Text(
                    stringResource(R.string.xk_plan_time_range, plan.startTime, plan.endTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun XkOpenSummaryCard(
    window: XkWindowStatus,
    wallet: XkCreditWallet?,
    loading: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var noticeExpanded by rememberSaveable(window.headerNotice) { mutableStateOf(false) }
    MintCard(color = scheme.primaryContainer.copy(alpha = 0.42f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    JxfwParse.termLabel(window.xnxqdm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Tag(
                    text = stringResource(R.string.xk_window_open_badge),
                    bg = scheme.primaryContainer,
                    fg = scheme.onPrimaryContainer,
                )
            }
            val metaLine =
                listOfNotNull(
                    stringResource(if (window.canDrop) R.string.xk_can_drop else R.string.xk_cannot_drop),
                    window.maxCourses?.let { stringResource(R.string.xk_max_courses, it) },
                ).joinToString(" · ")
            Text(
                metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (wallet != null) {
                val ratio =
                    if (wallet.totalCredits > 0.0) {
                        (wallet.usedCredits / wallet.totalCredits).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.xk_wallet_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(
                                R.string.xk_wallet_summary,
                                formatNumber(wallet.availableCredits),
                                formatNumber(wallet.usedCredits),
                                formatNumber(wallet.totalCredits),
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                }
            }
            if (window.headerNotice.isNotBlank()) {
                Text(
                    window.headerNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (noticeExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .mintClickable(pressedScale = 0.99f) { noticeExpanded = !noticeExpanded },
                )
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XkWindowBadges(window: XkWindowStatus) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tag(
            text = stringResource(if (window.canDrop) R.string.xk_can_drop else R.string.xk_cannot_drop),
            bg = if (window.canDrop) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            fg = if (window.canDrop) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        )
        window.maxCourses?.let { maxCount ->
            Tag(
                text = stringResource(R.string.xk_max_courses, maxCount),
                bg = scheme.surfaceContainerHighest,
                fg = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun XkSearchAndFilterPanel(
    searchKey: String,
    searchQuery: String,
    onlyHasSeats: Boolean,
    onlyNoConflict: Boolean,
    onSearchKeyChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onToggleHasSeats: () -> Unit,
    onToggleNoConflict: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) scheme.primary.copy(alpha = 0.65f) else Color.Transparent
    val fieldShape = RoundedCornerShape(12.dp)
    val searchLabel = stringResource(R.string.xk_search_label)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            interactionSource = interaction,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = searchLabel },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(fieldShape)
                        .background(scheme.surfaceContainerHighest)
                        .border(1.5.dp, borderColor, fieldShape)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                stringResource(R.string.xk_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.xk_search_clear_cd),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onSubmitSearch,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.xk_search_cd),
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                listOf(
                    "kcmc" to R.string.xk_search_key_name,
                    "teaxm" to R.string.xk_search_key_teacher,
                    "kcflmc" to R.string.xk_search_key_category,
                ),
                key = { it.first },
            ) { (key, labelRes) ->
                ChipPill(
                    label = stringResource(labelRes),
                    on = searchKey == key,
                    onClick = { onSearchKeyChange(key) },
                )
            }
            item(key = "filter-divider") {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(scheme.outlineVariant.copy(alpha = 0.6f)),
                )
            }
            item(key = "only-seats") {
                ChipPill(
                    label = stringResource(R.string.xk_filter_has_seats),
                    on = onlyHasSeats,
                    onClick = onToggleHasSeats,
                )
            }
            item(key = "no-conflict") {
                ChipPill(
                    label = stringResource(R.string.xk_filter_no_conflict),
                    on = onlyNoConflict,
                    onClick = onToggleNoConflict,
                )
            }
        }
    }
}

@Composable
private fun XkCourseCard(
    item: XkCourseItem,
    canDrop: Boolean,
    busy: Boolean,
    actionsDisabled: Boolean,
    onClick: () -> Unit,
    onDropClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    MintCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val seatTag =
                    if (item.capacity != null && item.enrolledCount != null) {
                        val rem = item.remainingSeats ?: 0
                        if (item.isFull) {
                            stringResource(R.string.xk_seats_full, item.enrolledCount!!, item.capacity!!)
                        } else {
                            stringResource(R.string.xk_seats_remaining, rem, item.capacity!!)
                        }
                    } else {
                        null
                    }
                if (seatTag != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(if (item.isFull) scheme.surfaceContainerHighest else scheme.secondaryContainer)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = seatTag,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = if (item.isFull) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val metaParts =
                listOfNotNull(
                    item.teacher.takeIf { it.isNotBlank() },
                    item.credit?.let { stringResource(R.string.xk_credit_value, formatNumber(it)) },
                    item.campusHint.takeIf { it.isNotBlank() },
                )
            if (metaParts.isNotEmpty()) {
                Text(
                    metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val primarySchedule =
                    if (item.scheduleSummary.isEmpty()) {
                        stringResource(R.string.xk_schedule_tba)
                    } else {
                        formatScheduleSummary(ctx, item.scheduleSummary.first())
                    }
                Text(
                    primarySchedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (item.enrolled) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tag(
                            text = stringResource(R.string.xk_action_enrolled_tag),
                            bg = scheme.primaryContainer,
                            fg = scheme.onPrimaryContainer,
                        )
                        FilledTonalButton(
                            onClick = onDropClick,
                            enabled = canDrop && !actionsDisabled,
                            modifier = Modifier.heightIn(min = 36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.errorContainer,
                                    contentColor = scheme.error,
                                ),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    stringResource(if (canDrop) R.string.xk_action_drop else R.string.xk_cannot_drop),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                } else if (item.hasConflict) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MintTheme.colors.conflictBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MintTheme.colors.removed,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.xk_conflict_badge, item.conflicts.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MintTheme.colors.removed,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun XkCourseDetailSheet(
    item: XkCourseItem,
    occupiedSlots: List<XkOccupiedSlot>,
    canDrop: Boolean,
    windowOpen: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLoadDetail: suspend (XkCourseItem) -> Result<XkCourseDetail>,
    onEnrollClick: () -> Unit,
    onDropClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var loading by remember(item.kcrwdm) { mutableStateOf(true) }
    var detail by remember(item.kcrwdm) { mutableStateOf<XkCourseDetail?>(null) }

    LaunchedEffect(item.kcrwdm) {
        loading = true
        detail = onLoadDetail(item).getOrNull()
        loading = false
    }

    val preview =
        remember(occupiedSlots, item, detail?.calendar) {
            JxfwXkParse.buildSchedulePreview(
                occupiedSlots = occupiedSlots,
                candidate = item,
                calendarOverride = detail?.calendar.orEmpty(),
            )
        }

    MintBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                detail?.extra?.englishName?.takeIf { it.isNotBlank() }?.let { enName ->
                    Text(
                        enName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.credit?.let {
                    Tag(
                        text = stringResource(R.string.xk_credit_value, formatNumber(it)),
                        bg = scheme.primaryContainer,
                        fg = scheme.onPrimaryContainer,
                    )
                }
                if (item.capacity != null && item.enrolledCount != null) {
                    val rem = item.remainingSeats ?: 0
                    val seatLabel =
                        if (item.isFull) {
                            stringResource(R.string.xk_seats_full, item.enrolledCount!!, item.capacity!!)
                        } else {
                            stringResource(R.string.xk_seats_remaining, rem, item.capacity!!)
                        }
                    Tag(
                        text = seatLabel,
                        bg = if (item.isFull) scheme.surfaceContainerHighest else scheme.secondaryContainer,
                        fg = if (item.isFull) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                    )
                }
                if (item.enrolled) {
                    Tag(
                        text = stringResource(R.string.xk_action_enrolled_tag),
                        bg = scheme.primaryContainer,
                        fg = scheme.onPrimaryContainer,
                    )
                }
            }

            val code = detail?.extra?.courseCode?.takeIf { it.isNotBlank() } ?: item.kcdm
            val hours = detail?.extra?.plannedHours.orEmpty()
            val classGroup = detail?.extra?.classGroup?.takeIf { it.isNotBlank() } ?: item.classGroup
            val platform = detail?.extra?.platformName?.takeIf { it.isNotBlank() } ?: item.category
            val studyMode = detail?.extra?.studyMode.orEmpty()
            val gradingMode = detail?.extra?.gradingMode.orEmpty()
            val metaRows =
                listOfNotNull(
                    item.teacher.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_teacher) to it },
                    classGroup.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_class_group) to it },
                    code.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_code) to it },
                    hours.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_hours) to it },
                    platform.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_platform) to it },
                    studyMode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_study_mode) to it },
                    gradingMode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.xk_detail_label_grading_mode) to it },
                )
            if (metaRows.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        metaRows.forEachIndexed { index, (label, value) ->
                            XkDetailMetaRow(label = label, value = value)
                            if (index < metaRows.lastIndex) {
                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }

            if (preview.candidateBlocks.isNotEmpty()) {
                XkTimetablePreview(preview = preview)
            }

            val previewSlotConflicts =
                remember(preview.conflictCells) {
                    preview.conflictCells.values
                        .flatten()
                        .distinct()
                }
            val displaySlotConflicts = previewSlotConflicts.ifEmpty { item.slotConflicts }
            if (!item.enrolled && (displaySlotConflicts.isNotEmpty() || item.conflicts.isNotEmpty())) {
                ConflictCard(title = stringResource(R.string.xk_detail_conflicts_section)) {
                    if (displaySlotConflicts.isNotEmpty()) {
                        displaySlotConflicts.forEach { slotConflict ->
                            Text(
                                formatSlotConflictLine(ctx, slotConflict),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurface,
                            )
                        }
                    } else {
                        item.conflicts.forEach { conflict ->
                            Text(
                                formatConflictLine(ctx, conflict),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurface,
                            )
                        }
                    }
                }
            }

            val summaries =
                remember(item.scheduleSummary, detail?.calendar) {
                    val cal = detail?.calendar.orEmpty()
                    if (cal.isNotEmpty()) JxfwXkParse.summarizeSchedule(cal) else item.scheduleSummary
                }
            SectionLabel(stringResource(R.string.xk_detail_schedule_section))
            if (summaries.isEmpty()) {
                Text(
                    stringResource(R.string.xk_schedule_tba),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summaries.forEach { summary ->
                        Text(
                            formatScheduleSummary(ctx, summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.xk_detail_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                val zh = detail?.zhSyllabus.orEmpty()
                val en = detail?.enSyllabus.orEmpty()
                if (zh.isNotBlank()) {
                    SectionLabel(stringResource(R.string.xk_detail_syllabus_zh))
                    Text(zh, style = MaterialTheme.typography.bodyMedium)
                }
                if (en.isNotBlank()) {
                    SectionLabel(stringResource(R.string.xk_detail_syllabus_en))
                    Text(en, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                if (zh.isBlank() && en.isBlank()) {
                    Text(
                        stringResource(R.string.xk_detail_syllabus_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (windowOpen) {
                Spacer(Modifier.height(4.dp))
                if (item.enrolled) {
                    FilledTonalButton(
                        onClick = onDropClick,
                        enabled = canDrop && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.errorContainer,
                                contentColor = scheme.error,
                            ),
                    ) {
                        Text(stringResource(if (canDrop) R.string.xk_action_drop else R.string.xk_cannot_drop))
                    }
                } else {
                    Button(
                        onClick = onEnrollClick,
                        enabled = !item.isFull && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.xk_action_enroll))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun XkDetailMetaRow(
    label: String,
    value: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun XkTimetablePreview(preview: app.goke.core.jxfw.XkSchedulePreview) {
    val scheme = MaterialTheme.colorScheme
    val weekdays = stringArrayResource(R.array.weekday_short)
    val candidateDays = remember(preview) { preview.candidateBlocks.map { it.dayOfWeek }.toSet() }
    val conflictDays =
        remember(preview) {
            preview.conflictCells.keys
                .map { it.first }
                .toSet()
        }
    val conflictRuns =
        remember(preview) {
            preview.conflictCells.keys
                .groupBy { it.first }
                .flatMap { (day, cells) ->
                    val sorted = cells.map { it.second }.distinct().sorted()
                    val runs = mutableListOf<Pair<Int, Int>>()
                    var start = -1
                    var prev = -1
                    for (p in sorted) {
                        if (start == -1) {
                            start = p
                            prev = p
                        } else if (p == prev + 1) {
                            prev = p
                        } else {
                            runs += start to prev
                            start = p
                            prev = p
                        }
                    }
                    if (start != -1) runs += start to prev
                    runs.map { Triple(day, it.first, it.second) }
                }
        }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.xk_preview_section))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XkLegendItem(
                label = stringResource(R.string.xk_preview_legend_existing),
                bg = scheme.surfaceContainerHighest,
                border = null,
            )
            XkLegendItem(
                label = stringResource(R.string.xk_preview_legend_candidate),
                bg = scheme.primaryContainer,
                border = scheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MintTheme.colors.conflictBg)
                        .border(1.5.dp, MintTheme.colors.removed, RoundedCornerShape(3.dp)),
                )
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MintTheme.colors.removed,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    stringResource(R.string.xk_preview_legend_conflict),
                    style = MaterialTheme.typography.labelSmall,
                    color = MintTheme.colors.removed,
                )
            }
        }

        if (preview.candidateWeeks.isNotEmpty()) {
            val weeksText = JxfwXkParse.formatWeeksRange(preview.candidateWeeks)
            if (weeksText.isNotBlank()) {
                Text(
                    stringResource(R.string.xk_preview_week_scope, weeksText),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
                val axisW = 24.dp * fontScale
                val colW = ((maxWidth - axisW) / 7f).coerceAtLeast(16.dp)
                val rowH = 22.dp * fontScale
                val groupGap = 6.dp
                val gridLineColor = scheme.outlineVariant.copy(alpha = 0.35f)
                val groupLineColor = scheme.outlineVariant.copy(alpha = 0.7f)

                fun periodTop(period: Int): Dp {
                    val p = (period - 1).coerceAtLeast(0)
                    val gap =
                        when {
                            period > 8 -> groupGap * 2
                            period > 4 -> groupGap
                            else -> 0.dp
                        }
                    return rowH * p + gap
                }

                fun periodBottom(period: Int): Dp = periodTop(period) + rowH

                val totalGridH = periodBottom(preview.maxPeriod)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = axisW, bottom = 4.dp),
                    ) {
                        for (day in 1..7) {
                            val hasConflict = day in conflictDays
                            val isCandidate = day in candidateDays
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = weekdays.getOrElse(day - 1) { day.toString() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (hasConflict || isCandidate) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                        when {
                                            hasConflict -> MintTheme.colors.removed
                                            isCandidate -> scheme.primary
                                            else -> scheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(totalGridH)
                                .drawBehind {
                                    val axisPx = axisW.toPx()
                                    for (p in 2..preview.maxPeriod) {
                                        if (p == 5 || p == 9) {
                                            val y = (periodTop(p) - groupGap / 2).toPx()
                                            drawLine(
                                                color = groupLineColor,
                                                start = Offset(axisPx, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = 1.dp.toPx(),
                                            )
                                        } else {
                                            val y = periodTop(p).toPx()
                                            drawLine(
                                                color = gridLineColor,
                                                start = Offset(axisPx, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = 0.5.dp.toPx(),
                                            )
                                        }
                                    }
                                },
                    ) {
                        for (p in 1..preview.maxPeriod) {
                            Box(
                                modifier =
                                    Modifier
                                        .absoluteOffset(x = 0.dp, y = periodTop(p))
                                        .size(width = axisW, height = rowH),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = p.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }

                        preview.occupiedBlocks.forEach { block ->
                            if (block.dayOfWeek in 1..7) {
                                val top = periodTop(block.startPeriod)
                                val bottom = periodBottom(block.endPeriod)
                                val span = (block.endPeriod - block.startPeriod + 1).coerceAtLeast(1)
                                Box(
                                    modifier =
                                        Modifier
                                            .absoluteOffset(
                                                x = axisW + colW * (block.dayOfWeek - 1) + 1.dp,
                                                y = top + 1.dp,
                                            ).size(
                                                width = (colW - 2.dp).coerceAtLeast(1.dp),
                                                height = (bottom - top - 2.dp).coerceAtLeast(1.dp),
                                            ).clip(RoundedCornerShape(6.dp))
                                            .background(scheme.surfaceContainerHighest.copy(alpha = 0.85f))
                                            .padding(horizontal = 2.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Text(
                                        text = block.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                        color = scheme.onSurfaceVariant,
                                        maxLines = span.coerceAtMost(2),
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        preview.candidateBlocks.forEach { block ->
                            if (block.dayOfWeek in 1..7) {
                                val top = periodTop(block.startPeriod)
                                val bottom = periodBottom(block.endPeriod)
                                val span = (block.endPeriod - block.startPeriod + 1).coerceAtLeast(1)
                                Box(
                                    modifier =
                                        Modifier
                                            .absoluteOffset(
                                                x = axisW + colW * (block.dayOfWeek - 1) + 1.dp,
                                                y = top + 1.dp,
                                            ).size(
                                                width = (colW - 2.dp).coerceAtLeast(1.dp),
                                                height = (bottom - top - 2.dp).coerceAtLeast(1.dp),
                                            ).clip(RoundedCornerShape(6.dp))
                                            .background(scheme.primaryContainer)
                                            .border(1.5.dp, scheme.primary, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 2.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Text(
                                        text = block.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onPrimaryContainer,
                                        maxLines = span.coerceAtMost(2),
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        conflictRuns.forEach { (day, startP, endP) ->
                            if (day in 1..7) {
                                val top = periodTop(startP)
                                val bottom = periodBottom(endP)
                                Box(
                                    modifier =
                                        Modifier
                                            .absoluteOffset(
                                                x = axisW + colW * (day - 1) + 1.dp,
                                                y = top + 1.dp,
                                            ).size(
                                                width = (colW - 2.dp).coerceAtLeast(1.dp),
                                                height = (bottom - top - 2.dp).coerceAtLeast(1.dp),
                                            ).clip(RoundedCornerShape(6.dp))
                                            .background(MintTheme.colors.conflictBg)
                                            .border(2.dp, MintTheme.colors.removed, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 2.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = MintTheme.colors.removed,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.xk_preview_conflict_tag),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = MintTheme.colors.removed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XkLegendItem(
    label: String,
    bg: Color,
    border: Color?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val baseMod =
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg)
        Box(if (border != null) baseMod.border(1.dp, border, RoundedCornerShape(3.dp)) else baseMod)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSlotConflictLine(
    ctx: Context,
    conflict: XkSlotConflict,
): String {
    val weekday = weekdayLabel(ctx, conflict.dayOfWeek)
    val periods = ctx.getString(R.string.xk_schedule_periods, formatPeriods(conflict.periods))
    val weeks = JxfwXkParse.formatWeeksRange(conflict.weeks)
    val source =
        ctx.getString(
            when (conflict.source) {
                XkConflictSource.JXFW -> R.string.xk_conflict_source_jxfw
                XkConflictSource.LOCAL_TIMETABLE -> R.string.xk_conflict_source_local
            },
        )
    return ctx.getString(R.string.xk_conflict_line, conflict.courseName, weekday, periods, weeks, source)
}

private fun formatScheduleSummary(
    ctx: Context,
    summary: XkScheduleSummary,
): String {
    val parts = mutableListOf<String>()
    val weekday = weekdayLabel(ctx, summary.dayOfWeek)
    val periods = formatPeriods(summary.periods)
    if (weekday.isNotEmpty() || periods.isNotEmpty()) {
        val periodStr = if (periods.isNotEmpty()) ctx.getString(R.string.xk_schedule_periods, periods) else ""
        parts += listOf(weekday, periodStr).filter { it.isNotEmpty() }.joinToString(" ")
    }
    val weeks = JxfwXkParse.formatWeeksRange(summary.weeks)
    if (weeks.isNotEmpty()) parts += weeks
    if (summary.room.isNotBlank()) parts += summary.room
    if (summary.campusHint.isNotBlank() && !summary.room.contains(summary.campusHint)) {
        parts += summary.campusHint
    }
    return parts.joinToString(" · ")
}

private fun formatConflictLine(
    ctx: Context,
    conflict: XkConflict,
): String {
    val weekday = weekdayLabel(ctx, conflict.dayOfWeek)
    val periods = ctx.getString(R.string.xk_schedule_periods, formatPeriods(conflict.periods))
    val weeks = JxfwXkParse.formatWeeksRange(conflict.weeks)
    val source =
        ctx.getString(
            when (conflict.source) {
                XkConflictSource.JXFW -> R.string.xk_conflict_source_jxfw
                XkConflictSource.LOCAL_TIMETABLE -> R.string.xk_conflict_source_local
            },
        )
    return ctx.getString(R.string.xk_conflict_line, conflict.courseName, weekday, periods, weeks, source)
}

private fun weekdayLabel(
    ctx: Context,
    dayOfWeek: Int,
): String =
    when (dayOfWeek) {
        1 -> ctx.getString(R.string.xk_weekday_mon)
        2 -> ctx.getString(R.string.xk_weekday_tue)
        3 -> ctx.getString(R.string.xk_weekday_wed)
        4 -> ctx.getString(R.string.xk_weekday_thu)
        5 -> ctx.getString(R.string.xk_weekday_fri)
        6 -> ctx.getString(R.string.xk_weekday_sat)
        7 -> ctx.getString(R.string.xk_weekday_sun)
        else -> ""
    }

private fun formatPeriods(periods: List<Int>): String {
    val sorted = periods.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val isContiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (isContiguous && sorted.size > 1) {
        "${sorted.first()}-${sorted.last()}"
    } else {
        sorted.joinToString(",")
    }
}

private fun formatNumber(value: Double): String {
    val fmt = DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT))
    return fmt.format(value)
}

internal fun formatXkFailure(
    ctx: Context,
    kind: XkFailureKind,
    reason: String,
): String =
    when (kind) {
        XkFailureKind.FULL -> {
            ctx.getString(R.string.xk_err_full)
        }

        XkFailureKind.CONFLICT -> {
            ctx.getString(R.string.xk_err_conflict)
        }

        XkFailureKind.CREDIT_LIMIT -> {
            ctx.getString(R.string.xk_err_credit_limit)
        }

        XkFailureKind.OUT_OF_WINDOW -> {
            ctx.getString(R.string.xk_err_out_of_window)
        }

        XkFailureKind.ALREADY_ENROLLED -> {
            ctx.getString(R.string.xk_err_already_enrolled)
        }

        XkFailureKind.NOT_DROPPABLE -> {
            ctx.getString(R.string.xk_err_not_droppable)
        }

        XkFailureKind.OTHER -> {
            if (reason.isNotBlank()) {
                ctx.getString(R.string.xk_err_other, reason)
            } else {
                ctx.getString(R.string.xk_err_other_default)
            }
        }
    }
