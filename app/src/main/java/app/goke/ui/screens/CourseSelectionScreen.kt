package app.goke.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import app.goke.core.jxfw.XkOpenPlan
import app.goke.core.jxfw.XkScheduleSummary
import app.goke.core.jxfw.XkWindowStatus
import app.goke.debug.DebugHooks
import app.goke.jxfw.JxfwCourseSelectionState
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.Route
import app.goke.ui.components.ChipChoice
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.TonalField
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
                app.jxfw.loadCourseSelection(
                    searchKey = xkState.searchKey.ifBlank { "kcmc" },
                    searchValue = xkState.searchValue,
                    page = 1,
                )
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
                            is XkActionResult.Success -> actions.toast(ctx.getString(R.string.xk_enroll_success, item.displayName))
                            is XkActionResult.Failure -> actions.toast(formatXkFailure(ctx, res.kind, res.reason))
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
                            is XkActionResult.Success -> actions.toast(ctx.getString(R.string.xk_drop_success, item.displayName))
                            is XkActionResult.Failure -> actions.toast(formatXkFailure(ctx, res.kind, res.reason))
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
            if (loggedIn) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !xkState.loading,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.xk_refresh_cd))
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
                            onEnrollClick = {},
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
                            onEnrollClick = { confirmRequest = XkConfirmRequest(item, XkConfirmMode.ENROLL) },
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
                            onEnrollClick = {},
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
                    XkTagPill(
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
                )
                XkTagPill(
                    text = stringResource(R.string.xk_window_open_badge),
                    bg = scheme.primaryContainer,
                    fg = scheme.onPrimaryContainer,
                )
            }
            XkWindowBadges(window = window)
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
                            style = MaterialTheme.typography.labelMedium,
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
        XkTagPill(
            text = stringResource(if (window.canDrop) R.string.xk_can_drop else R.string.xk_cannot_drop),
            bg = if (window.canDrop) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            fg = if (window.canDrop) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        )
        window.maxCourses?.let { maxCount ->
            XkTagPill(
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TonalField(
                label = stringResource(R.string.xk_search_label),
                value = searchQuery,
                onChange = onSearchQueryChange,
                placeholder = stringResource(R.string.xk_search_placeholder),
                modifier = Modifier.weight(1f),
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.xk_search_clear_cd))
                }
            }
            FilledTonalButton(
                onClick = onSubmitSearch,
                modifier = Modifier.padding(bottom = 4.dp).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.xk_search_action))
            }
        }
        ChipChoice(
            options =
                listOf(
                    "kcmc" to stringResource(R.string.xk_search_key_name),
                    "teaxm" to stringResource(R.string.xk_search_key_teacher),
                    "kcflmc" to stringResource(R.string.xk_search_key_category),
                ),
            selected = searchKey,
            onSelect = onSearchKeyChange,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XkCourseCard(
    item: XkCourseItem,
    canDrop: Boolean,
    busy: Boolean,
    actionsDisabled: Boolean,
    onClick: () -> Unit,
    onEnrollClick: () -> Unit,
    onDropClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    MintCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subLine =
                        listOfNotNull(
                            item.teacher.takeIf { it.isNotBlank() },
                            item.classGroup.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                    if (subLine.isNotBlank()) {
                        Text(
                            subLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                item.credit?.let { credit ->
                    XkTagPill(
                        text = stringResource(R.string.xk_credit_value, formatNumber(credit)),
                        bg = scheme.primaryContainer,
                        fg = scheme.onPrimaryContainer,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.category.isNotBlank()) {
                    XkTagPill(
                        text = item.category,
                        bg = scheme.surfaceContainerHighest,
                        fg = scheme.onSurfaceVariant,
                    )
                }
                if (item.subCategory.isNotBlank() && item.subCategory != item.category) {
                    XkTagPill(
                        text = item.subCategory,
                        bg = scheme.surfaceContainerHighest,
                        fg = scheme.onSurfaceVariant,
                    )
                }
                if (item.campusHint.isNotBlank()) {
                    XkTagPill(
                        text = item.campusHint,
                        bg = scheme.secondaryContainer,
                        fg = scheme.onSecondaryContainer,
                    )
                }
                if (item.hasConflict && !item.enrolled) {
                    XkTagPill(
                        text = stringResource(R.string.xk_conflict_badge, item.conflicts.size),
                        bg = scheme.errorContainer,
                        fg = scheme.error,
                    )
                }
            }

            if (item.scheduleSummary.isEmpty()) {
                Text(
                    stringResource(R.string.xk_schedule_tba),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.scheduleSummary.forEach { summary ->
                        Text(
                            formatScheduleSummary(ctx, summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val seatsText =
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
                if (seatsText != null) {
                    Text(
                        seatsText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.isFull) scheme.error else MintTheme.colors.success,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (item.enrolled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XkTagPill(
                            text = stringResource(R.string.xk_action_enrolled_tag),
                            bg = scheme.primaryContainer,
                            fg = scheme.onPrimaryContainer,
                        )
                        FilledTonalButton(
                            onClick = onDropClick,
                            enabled = canDrop && !actionsDisabled,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.errorContainer,
                                    contentColor = scheme.error,
                                ),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(if (canDrop) R.string.xk_action_drop else R.string.xk_cannot_drop))
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onEnrollClick,
                        enabled = !item.isFull && !actionsDisabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.xk_action_enroll))
                        }
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
                    XkTagPill(
                        text = stringResource(R.string.xk_credit_value, formatNumber(it)),
                        bg = scheme.primaryContainer,
                        fg = scheme.onPrimaryContainer,
                    )
                }
                val code = detail?.extra?.courseCode?.takeIf { it.isNotBlank() } ?: item.kcdm
                if (code.isNotBlank()) {
                    XkTagPill(
                        text = stringResource(R.string.xk_detail_code, code),
                        bg = scheme.surfaceContainerHighest,
                        fg = scheme.onSurfaceVariant,
                    )
                }
                detail?.extra?.plannedHours?.takeIf { it.isNotBlank() }?.let { hours ->
                    XkTagPill(
                        text = stringResource(R.string.xk_detail_hours, hours),
                        bg = scheme.surfaceContainerHighest,
                        fg = scheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.teacher.isNotBlank()) {
                    DetailMetaText(stringResource(R.string.xk_detail_teacher, item.teacher))
                }
                val classGroup = detail?.extra?.classGroup?.takeIf { it.isNotBlank() } ?: item.classGroup
                if (classGroup.isNotBlank()) {
                    DetailMetaText(stringResource(R.string.xk_detail_class_group, classGroup))
                }
                val platform = detail?.extra?.platformName?.takeIf { it.isNotBlank() } ?: item.category
                if (platform.isNotBlank()) {
                    DetailMetaText(stringResource(R.string.xk_detail_platform, platform))
                }
                detail?.extra?.studyMode?.takeIf { it.isNotBlank() }?.let {
                    DetailMetaText(stringResource(R.string.xk_detail_study_mode, it))
                }
                detail?.extra?.gradingMode?.takeIf { it.isNotBlank() }?.let {
                    DetailMetaText(stringResource(R.string.xk_detail_grading_mode, it))
                }
            }

            if (item.hasConflict && !item.enrolled) {
                MintCard(color = scheme.errorContainer) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = scheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.xk_detail_conflicts_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        item.conflicts.forEach { conflict ->
                            Text(
                                formatConflictLine(ctx, conflict),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onErrorContainer,
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
private fun DetailMetaText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun XkTagPill(
    text: String,
    bg: Color,
    fg: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
