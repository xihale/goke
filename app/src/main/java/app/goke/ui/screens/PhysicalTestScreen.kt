package app.goke.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.jxfw.TzcsItem
import app.goke.core.jxfw.TzcsParse
import app.goke.core.jxfw.TzcsSummary
import app.goke.core.jxfw.TzcsYearReport
import app.goke.jxfw.TzcsException
import app.goke.jxfw.TzcsState
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.ChipPill
import app.goke.ui.components.ConfirmDialog
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.Tag
import app.goke.ui.components.TonalField
import app.goke.ui.components.mintClickable
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 「体测成绩」：直连广工体质测试平台（`tzcs.gdut.edu.cn`），本地只读缓存各学年明细与总分。 */
@Composable
fun PhysicalTestScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    val tzcsState by app.tzcs.state.collectAsState()
    val jxfwState by app.jxfw.state.collectAsState()
    LaunchedEffect(Unit) { app.tzcs.hydrate() }

    var showVerifyDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val onRefresh: () -> Unit = {
        scope.launch {
            app.tzcs
                .refreshIfSessionActive()
                .onSuccess { list ->
                    actions.toast(ctx.getString(R.string.tzcs_refreshed_toast, list.size))
                }.onFailure { err ->
                    if ((err as? TzcsException)?.kind == TzcsException.Kind.SESSION) {
                        showVerifyDialog = true
                    }
                }
        }
    }

    MintScreen(title = stringResource(R.string.tzcs_title), onBack = onBack) {
        if (!tzcsState.loggedIn) {
            TzcsLoginPanel(
                state = tzcsState,
                defaultStudentId = jxfwState.username.orEmpty(),
            )
            return@MintScreen
        }

        val reports = tzcsState.reports
        val defaultYear = tzcsState.latestSummaryReport?.year
        var selectedYear by rememberSaveable(defaultYear) { mutableStateOf(defaultYear) }
        val showAllYears = reports.size > 1 && selectedYear == ALL_YEARS_FILTER
        val activeReport =
            remember(reports, selectedYear, showAllYears) {
                if (showAllYears) {
                    null
                } else {
                    reports.firstOrNull { it.year == selectedYear } ?: tzcsState.latestSummaryReport
                }
            }
        val historySummary =
            remember(reports) {
                TzcsParse.summarizeReports(reports)
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                TzcsSummaryCard(
                    report = activeReport,
                    historySummary = if (showAllYears) historySummary else null,
                    state = tzcsState,
                    onRefresh = onRefresh,
                )
            }

            if (tzcsState.lastError != null) {
                item(key = "error") {
                    TzcsErrorBanner(
                        message = tzcsState.lastError!!,
                        isNetworkError = tzcsState.lastErrorKind == TzcsException.Kind.NETWORK,
                    )
                }
            }

            if (reports.size > 1) {
                item(key = "years") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "year-all") {
                            ChipPill(
                                label = stringResource(R.string.tzcs_all_years),
                                on = showAllYears,
                                onClick = { selectedYear = ALL_YEARS_FILTER },
                            )
                        }
                        items(reports, key = { it.year }) { report ->
                            ChipPill(
                                label = stringResource(R.string.tzcs_year_chip, report.year),
                                on = !showAllYears && activeReport?.year == report.year,
                                onClick = { selectedYear = report.year },
                            )
                        }
                    }
                }
            }

            if (showAllYears) {
                item(key = "history-header") {
                    SectionLabel(
                        stringResource(R.string.tzcs_history_section, reports.size),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                items(reports, key = { it.year }) { report ->
                    TzcsYearHistoryRow(
                        report = report,
                        onClick = { selectedYear = report.year },
                    )
                }
            } else {
                val items = activeReport?.items.orEmpty()
                if (items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.tzcs_empty_title),
                            icon = Icons.Rounded.FitnessCenter,
                            actionLabel = if (tzcsState.busy) null else stringResource(R.string.tzcs_refresh),
                            onAction = if (tzcsState.busy) null else onRefresh,
                            modifier = Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp),
                        )
                    }
                } else {
                    item(key = "list-header") {
                        SectionLabel(
                            stringResource(R.string.tzcs_items_section, items.size),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    itemsIndexed(
                        items,
                        key = { index, item -> "$index|${item.name}" },
                    ) { _, item ->
                        TzcsItemRow(item)
                    }
                }
            }

            item(key = "account-footer") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.tzcs_bound_id, tzcsState.username.orEmpty()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.tzcs_logout), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showVerifyDialog && tzcsState.username != null) {
        TzcsVerifyRefreshDialog(
            username = tzcsState.username.orEmpty(),
            initialPassword = remember(showVerifyDialog) { app.tzcs.savedPassword().orEmpty() },
            onDismiss = { showVerifyDialog = false },
            onRefreshed = { count ->
                showVerifyDialog = false
                actions.toast(ctx.getString(R.string.tzcs_refreshed_toast, count))
            },
        )
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.tzcs_logout),
            text = stringResource(R.string.tzcs_logout_message),
            confirm = stringResource(R.string.tzcs_logout),
            onConfirm = { scope.launch { app.tzcs.logout() } },
            onDismiss = { showLogoutDialog = false },
            danger = true,
        )
    }
}

@Composable
private fun TzcsLoginPanel(
    state: TzcsState,
    defaultStudentId: String,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    var user by rememberSaveable { mutableStateOf(defaultStudentId) }
    var password by remember { mutableStateOf("") }
    var vercode by remember { mutableStateOf("") }
    var captchaBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }
    var captchaError by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var localBusy by remember { mutableStateOf(false) }

    LaunchedEffect(defaultStudentId) {
        if (user.isBlank() && defaultStudentId.isNotBlank()) {
            user = defaultStudentId
        }
    }

    val reloadCaptcha: () -> Unit = {
        if (!captchaLoading) {
            captchaLoading = true
            captchaError = null
            scope.launch {
                app.tzcs
                    .loadCaptcha()
                    .onSuccess { bytes ->
                        captchaBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        if (captchaBitmap == null) {
                            captchaError = ctx.getString(R.string.tzcs_captcha_retry)
                        }
                    }.onFailure { err ->
                        captchaError = err.message ?: ctx.getString(R.string.tzcs_err_network)
                    }
                captchaLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { reloadCaptcha() }

    val busy = localBusy || state.busy
    val displayError = localError ?: state.lastError

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tzcs_login_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TzcsCampusNetHint()
        TonalField(
            label = stringResource(R.string.tzcs_username),
            value = user,
            onChange = { user = it },
            number = true,
        )
        TonalField(
            label = stringResource(R.string.tzcs_password),
            value = password,
            onChange = { password = it },
            secret = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TonalField(
                label = stringResource(R.string.tzcs_captcha),
                value = vercode,
                onChange = { vercode = it.take(6) },
                modifier = Modifier.weight(1f),
            )
            CaptchaImageTile(
                bitmap = captchaBitmap,
                loading = captchaLoading,
                error = captchaError != null,
                onRefresh = reloadCaptcha,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (!displayError.isNullOrBlank()) {
            Text(
                displayError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            enabled = !busy && user.isNotBlank() && password.isNotEmpty() && vercode.isNotBlank(),
            onClick = {
                localBusy = true
                localError = null
                scope.launch {
                    app.tzcs
                        .loginAndFetch(user, password, vercode)
                        .onSuccess { list ->
                            password = ""
                            vercode = ""
                            actions.toast(ctx.getString(R.string.tzcs_refreshed_toast, list.size))
                        }.onFailure { err ->
                            localError = err.message
                            vercode = ""
                            reloadCaptcha()
                        }
                    localBusy = false
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        ) {
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                    Text(stringResource(R.string.tzcs_logging_in))
                }
            } else {
                Text(stringResource(R.string.tzcs_login_action))
            }
        }
    }
}

@Composable
private fun CaptchaImageTile(
    bitmap: ImageBitmap?,
    loading: Boolean,
    error: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.tzcs_captcha_desc)
    Box(
        modifier =
            modifier
                .width(116.dp)
                .heightIn(min = 48.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics {
                    role = Role.Button
                    contentDescription = desc
                }.mintClickable(pressedScale = 0.96f, onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.tzcs_captcha_loading),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = desc,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Text(
                    stringResource(if (error) R.string.tzcs_captcha_retry else R.string.tzcs_captcha_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (error) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TzcsSummaryCard(
    report: TzcsYearReport?,
    historySummary: TzcsSummary?,
    state: TzcsState,
    onRefresh: () -> Unit,
) {
    val dash = stringResource(R.string.tzcs_dash)
    val fetchedText =
        state.fetchedAt?.let {
            stringResource(R.string.tzcs_last_fetched, jxfwStamp(it))
        } ?: stringResource(R.string.tzcs_never_fetched)

    val titleLabel: String
    val scoreDisplay: String
    val badgeText: String?
    val badgeIsBest: Boolean
    val countText: String

    if (historySummary != null) {
        titleLabel = stringResource(R.string.tzcs_best_score_label)
        scoreDisplay =
            historySummary.bestScore?.let {
                stringResource(R.string.tzcs_score_value, formatTzcsDecimal(it))
            } ?: dash
        badgeText = historySummary.bestYear?.let { stringResource(R.string.tzcs_best_year_tag, it) }
        badgeIsBest = true
        countText = stringResource(R.string.tzcs_history_count, historySummary.recordedYearCount, historySummary.yearCount)
    } else {
        val hasRecorded = report != null && TzcsParse.hasRecordedScores(report)
        titleLabel =
            if (report != null) {
                stringResource(R.string.tzcs_total_score_year_label, report.year)
            } else {
                stringResource(R.string.tzcs_total_score_label)
            }
        scoreDisplay =
            if (report != null && hasRecorded && report.totalScore.isNotBlank()) {
                stringResource(R.string.tzcs_score_value, report.totalScore)
            } else {
                dash
            }
        badgeText = report?.totalGrade?.takeIf { it.isNotBlank() }
        badgeIsBest = false
        val items = report?.items.orEmpty()
        val recordedCount = items.count { it.rawValue.isNotBlank() || it.itemScore.isNotBlank() || it.grade.isNotBlank() }
        countText = stringResource(R.string.tzcs_recorded_count, recordedCount, items.size)
    }

    MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    titleLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        scoreDisplay,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (badgeText != null) {
                        val (bg, fg) =
                            if (badgeIsBest) {
                                MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.primary
                            } else {
                                gradeBadgeColors(badgeText)
                            }
                        Tag(text = badgeText, bg = bg, fg = fg)
                    }
                }
            }
            FilledTonalButton(
                enabled = !state.busy,
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.tzcs_refreshing), maxLines = 1)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tzcs_refresh), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "$countText · $fetchedText",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.tzcs_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TzcsCampusNetHint() {
    MintCard(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.tzcs_campus_net_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TzcsErrorBanner(
    message: String,
    isNetworkError: Boolean = false,
) {
    MintCard(color = MintTheme.colors.conflictBg) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (isNetworkError) {
                Text(
                    stringResource(R.string.tzcs_campus_net_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TzcsItemRow(item: TzcsItem) {
    val dash = stringResource(R.string.tzcs_dash)
    val rawDisplay = item.rawValue.ifBlank { stringResource(R.string.tzcs_item_unrecorded) }
    val scoreDisplay =
        when {
            item.itemScore.isNotBlank() && item.grade.isNotBlank() -> {
                "${stringResource(R.string.tzcs_score_value, item.itemScore)} · ${item.grade}"
            }

            item.itemScore.isNotBlank() -> {
                stringResource(R.string.tzcs_score_value, item.itemScore)
            }

            item.grade.isNotBlank() -> {
                item.grade
            }

            else -> {
                dash
            }
        }
    val scoreColor =
        when {
            item.itemScore.isBlank() && item.grade.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
            "不及格" in item.grade -> MintTheme.colors.removed
            "优秀" in item.grade || "良好" in item.grade || "正常" in item.grade -> MintTheme.colors.success
            else -> MaterialTheme.colorScheme.primary
        }
    val weightedDisplay =
        item.weightedScore?.let { w ->
            val base = stringResource(R.string.tzcs_weighted_points, formatTzcsDecimal(w))
            val bonus = item.bonusScore?.let { " " + stringResource(R.string.tzcs_bonus_points, formatTzcsDecimal(it)) }.orEmpty()
            base + bonus
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MintTheme.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rawDisplay,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    scoreDisplay,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor,
                    maxLines = 1,
                )
                if (weightedDisplay != null) {
                    Text(
                        weightedDisplay,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TzcsVerifyRefreshDialog(
    username: String,
    initialPassword: String,
    onDismiss: () -> Unit,
    onRefreshed: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf(initialPassword) }
    var vercode by remember { mutableStateOf("") }
    // 已存密码时只要求填验证码（密码静默复用）；无存密码或密码被服务端拒绝时才露出密码框
    var revealPassword by remember { mutableStateOf(initialPassword.isBlank()) }
    var captchaBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }
    var captchaError by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val reloadCaptcha: () -> Unit = {
        if (!captchaLoading) {
            captchaLoading = true
            captchaError = null
            scope.launch {
                app.tzcs
                    .loadCaptcha()
                    .onSuccess { bytes ->
                        captchaBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        if (captchaBitmap == null) {
                            captchaError = ctx.getString(R.string.tzcs_captcha_retry)
                        }
                    }.onFailure { err ->
                        captchaError = err.message ?: ctx.getString(R.string.tzcs_err_network)
                    }
                captchaLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { reloadCaptcha() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.tzcs_verify_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.tzcs_verify_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (revealPassword) {
                    TonalField(
                        label = stringResource(R.string.tzcs_password),
                        value = password,
                        onChange = { password = it },
                        secret = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TonalField(
                        label = stringResource(R.string.tzcs_captcha),
                        value = vercode,
                        onChange = { vercode = it.take(6) },
                        modifier = Modifier.weight(1f),
                    )
                    CaptchaImageTile(
                        bitmap = captchaBitmap,
                        loading = captchaLoading,
                        error = captchaError != null,
                        onRefresh = reloadCaptcha,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty() && vercode.isNotBlank(),
                onClick = {
                    busy = true
                    errorMsg = null
                    scope.launch {
                        app.tzcs
                            .loginAndFetch(username, password, vercode)
                            .onSuccess { list ->
                                onRefreshed(list.size)
                            }.onFailure { err ->
                                errorMsg = err.message
                                if ((err as? TzcsException)?.kind == TzcsException.Kind.CREDENTIALS) {
                                    revealPassword = true
                                }
                                vercode = ""
                                reloadCaptcha()
                            }
                        busy = false
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.tzcs_verify_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TzcsYearHistoryRow(
    report: TzcsYearReport,
    onClick: () -> Unit,
) {
    val dash = stringResource(R.string.tzcs_dash)
    val hasRecorded = TzcsParse.hasRecordedScores(report)
    val recordedCount = report.items.count { it.rawValue.isNotBlank() || it.itemScore.isNotBlank() || it.grade.isNotBlank() }
    val subtitle =
        if (hasRecorded) {
            stringResource(R.string.tzcs_recorded_count, recordedCount, report.items.size)
        } else {
            stringResource(R.string.tzcs_year_unrecorded)
        }
    val scoreText =
        if (hasRecorded && report.totalScore.isNotBlank()) {
            stringResource(R.string.tzcs_score_value, report.totalScore)
        } else {
            dash
        }
    val scoreColor =
        when {
            !hasRecorded -> MaterialTheme.colorScheme.onSurfaceVariant
            "不及格" in report.totalGrade -> MintTheme.colors.removed
            "优秀" in report.totalGrade || "良好" in report.totalGrade -> MintTheme.colors.success
            else -> MaterialTheme.colorScheme.primary
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MintTheme.card,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .mintClickable(pressedScale = 0.98f, onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.tzcs_year_label, report.year),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    scoreText,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor,
                )
                if (report.totalGrade.isNotBlank()) {
                    Text(
                        report.totalGrade,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val ALL_YEARS_FILTER = -1

@Composable
private fun gradeBadgeColors(grade: String) =
    if ("不及格" in grade) {
        MintTheme.colors.conflictBg to MintTheme.colors.removed
    } else {
        MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.primary
    }

private fun formatTzcsDecimal(value: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(value)
