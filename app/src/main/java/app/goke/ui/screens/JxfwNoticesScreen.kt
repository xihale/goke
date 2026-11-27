package app.goke.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.JxfwNoticeSource
import app.goke.jxfw.JxfwNoticesState
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.NotificationsNone
import app.goke.ui.icons.OpenInNew
import kotlinx.coroutines.launch

/** 「教务通知」：极简只读列表展示教务处公开公告与站内消息，点击标记已读并在外部浏览器打开原文。 */
@Composable
fun JxfwNoticesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val state by app.jxfw.noticesState.collectAsState()

    LaunchedEffect(Unit) { app.jxfw.hydrateNotices() }

    val onRefresh: () -> Unit = {
        scope.launch {
            app.jxfw.refreshNotices(notify = false)
        }
    }

    JxfwNoticesContent(
        state = state,
        onBack = onBack,
        onRefresh = onRefresh,
        onMarkAllRead = { app.jxfw.markAllNoticesRead() },
        onNoticeClick = { notice ->
            app.jxfw.markNoticeRead(notice.id)
            if (notice.url.isNotBlank()) {
                val opened =
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(notice.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess
                if (!opened) {
                    actions.toast(ctx.getString(R.string.jxfw_notices_open_failed))
                }
            }
        },
    )
}

private enum class NoticeFilter {
    ALL,
    INTERNAL,
    PUBLIC,
}

@Composable
internal fun JxfwNoticesContent(
    state: JxfwNoticesState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNoticeClick: (JxfwNotice) -> Unit,
) {
    val defaultFilter =
        when {
            !state.publicEnabled && state.internalEnabled -> NoticeFilter.INTERNAL
            state.publicEnabled && !state.internalEnabled -> NoticeFilter.PUBLIC
            else -> NoticeFilter.ALL
        }
    var filter by remember(state.publicEnabled, state.internalEnabled) { mutableStateOf(defaultFilter) }
    val activeUnreadIds = state.activeUnreadIds
    val filterOptions =
        listOf(
            NoticeFilter.ALL to stringResource(R.string.jxfw_notices_filter_all),
            NoticeFilter.INTERNAL to stringResource(R.string.jxfw_notices_filter_internal),
            NoticeFilter.PUBLIC to stringResource(R.string.jxfw_notices_filter_public),
        )
    val selectedSourceDisabled =
        when (filter) {
            NoticeFilter.ALL -> !state.publicEnabled && !state.internalEnabled
            NoticeFilter.INTERNAL -> !state.internalEnabled
            NoticeFilter.PUBLIC -> !state.publicEnabled
        }
    val visibleNotices =
        if (selectedSourceDisabled) {
            emptyList()
        } else {
            when (filter) {
                NoticeFilter.ALL -> state.notices.filter { state.isSourceEnabled(it.source) }
                NoticeFilter.INTERNAL -> state.notices.filter { it.source == JxfwNoticeSource.INTERNAL }
                NoticeFilter.PUBLIC -> state.notices.filter { it.source == JxfwNoticeSource.PUBLIC }
            }
        }

    MintScreen(
        title = stringResource(R.string.jxfw_notices_title),
        onBack = onBack,
        actions = {
            if (activeUnreadIds.isNotEmpty()) {
                TextButton(onClick = onMarkAllRead) {
                    Text(stringResource(R.string.jxfw_notices_mark_all_read))
                }
            }
            IconButton(
                enabled = !state.busy && (state.publicEnabled || state.internalEnabled),
                onClick = onRefresh,
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.jxfw_notices_refresh),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentedPill(
                options = filterOptions,
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (visibleNotices.isEmpty() && !state.busy) {
                val emptyTitle =
                    when {
                        selectedSourceDisabled && filter == NoticeFilter.PUBLIC -> stringResource(R.string.jxfw_notices_disabled_public)
                        selectedSourceDisabled && filter == NoticeFilter.INTERNAL -> stringResource(R.string.jxfw_notices_disabled_internal)
                        selectedSourceDisabled -> stringResource(R.string.jxfw_notices_disabled_all)
                        filter == NoticeFilter.INTERNAL -> stringResource(R.string.jxfw_notices_empty_internal)
                        filter == NoticeFilter.PUBLIC -> stringResource(R.string.jxfw_notices_empty_public)
                        else -> stringResource(R.string.jxfw_notices_empty)
                    }
                val emptyHint =
                    when {
                        selectedSourceDisabled -> stringResource(R.string.jxfw_notices_disabled_hint)
                        filter == NoticeFilter.INTERNAL -> stringResource(R.string.jxfw_notices_empty_internal_hint)
                        else -> stringResource(R.string.jxfw_notices_empty_hint)
                    }
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JxfwErrorGuidance(state.lastErrorKind, state.lastError)
                    EmptyState(
                        title = emptyTitle,
                        hint = emptyHint,
                        icon = Icons.Rounded.NotificationsNone,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                if (state.fetchedAt != null || state.lastError != null) {
                    item(key = "meta") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.fetchedAt?.let { ts ->
                                Text(
                                    text = stringResource(R.string.jxfw_notices_last_fetched, jxfwStamp(ts)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            JxfwErrorGuidance(state.lastErrorKind, state.lastError)
                        }
                    }
                }

                items(visibleNotices, key = { it.id }) { notice ->
                    val unread = notice.id in activeUnreadIds
                    NoticeRow(
                        notice = notice,
                        unread = unread,
                        onClick = { onNoticeClick(notice) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    notice: JxfwNotice,
    unread: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .mintClickable(pressedScale = 0.99f, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (unread) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 7.dp, end = 10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isInternal = notice.source == JxfwNoticeSource.INTERNAL
                Surface(
                    color = if (isInternal) scheme.secondaryContainer.copy(alpha = 0.7f) else scheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (isInternal) R.string.jxfw_notices_internal_tag else R.string.jxfw_notices_public_tag,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isInternal) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (notice.date.isNotBlank()) {
                    Text(
                        text = notice.date,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (notice.url.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
