package app.goke.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.campus.DropRelayFile
import app.goke.core.campus.DropRelayLogic
import app.goke.core.campus.ShareBrowseEntry
import app.goke.droprelay.DropRelayState
import app.goke.ui.LocalApp
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintBottomSheet
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.Tag
import app.goke.ui.components.TonalField
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Download
import app.goke.ui.icons.FolderOpen
import app.goke.ui.icons.OpenInNew
import app.goke.ui.icons.Tune
import app.goke.ui.settingsState
import kotlinx.coroutines.launch

/** 校园资料库与文件快传主入口页：资料库原生浏览/搜索/下载 + 快传浏览器一键收发。 */
@Composable
fun CampusResourcesScreen(
    initialTab: Int = 0,
    initialQuery: String = "",
    initialCode: String = "",
    onBack: () -> Unit,
) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = settingsState()
    val state by app.dropRelay.state.collectAsState()

    val shareUrl = app.dropRelay.effectiveShareUrl(st.dropRelayShareUrl)
    val dropUrl = app.dropRelay.effectiveDropUrl(st.dropRelayDropUrl)
    val hasShare = shareUrl.isNotBlank()
    val hasDrop = dropUrl.isNotBlank()

    var selectedTab by remember(initialTab, hasShare, hasDrop) {
        mutableIntStateOf(
            when {
                hasShare && hasDrop -> initialTab.coerceIn(0, 1)
                hasShare -> 0
                hasDrop -> 1
                else -> 0
            },
        )
    }
    var searchQuery by remember(initialQuery) { mutableStateOf(initialQuery) }
    var currentDir by remember { mutableStateOf("") }
    var codeInput by remember(initialCode) { mutableStateOf(initialCode) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var p2pConfirmFile by remember { mutableStateOf<DropRelayFile?>(null) }

    LaunchedEffect(shareUrl) {
        if (shareUrl.isNotBlank() && state.index == null && !state.loadingIndex) {
            app.dropRelay.refreshIndex(force = false)
        }
    }
    LaunchedEffect(initialCode, dropUrl) {
        if (initialCode.isNotBlank() && dropUrl.isNotBlank()) {
            selectedTab = 1
        }
    }

    MintScreen(
        title = stringResource(R.string.droprelay_title),
        onBack = onBack,
        actions = {
            if (hasShare && selectedTab == 0) {
                IconButton(
                    onClick = { scope.launch { app.dropRelay.refreshIndex(force = true) } },
                    enabled = !state.loadingIndex,
                ) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.droprelay_action_refresh))
                }
            }
            IconButton(onClick = { showConfigSheet = true }) {
                Icon(Icons.Rounded.Tune, stringResource(R.string.droprelay_action_config))
            }
        },
    ) {
        if (!hasShare && !hasDrop) {
            EmptyState(
                title = stringResource(R.string.droprelay_unconfigured_title),
                hint = stringResource(R.string.droprelay_unconfigured_hint),
                icon = Icons.Rounded.FolderOpen,
                actionLabel = stringResource(R.string.droprelay_unconfigured_action),
                onAction = { showConfigSheet = true },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                if (hasShare && hasDrop) {
                    SegmentedPill(
                        options =
                            listOf(
                                0 to stringResource(R.string.droprelay_tab_share),
                                1 to stringResource(R.string.droprelay_tab_drop),
                            ),
                        selected = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (selectedTab == 0 && hasShare) {
                    ShareLibraryTab(
                        shareUrl = shareUrl,
                        state = state,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        currentDir = currentDir,
                        onNavigateDir = { currentDir = it },
                        onRetry = { scope.launch { app.dropRelay.refreshIndex(force = true) } },
                        onOpenShareBrowser = { openExternalUrl(ctx, shareUrl) },
                        onDownloadDirZip = { dir ->
                            val zipUrl = DropRelayLogic.dirZipUrl(shareUrl, dir.path)
                            val res = app.dropRelay.enqueueDownload(zipUrl, "${dir.name}.zip")
                            val toastText =
                                if (res.isSuccess) {
                                    ctx.getString(R.string.droprelay_download_started, "${dir.name}.zip")
                                } else {
                                    ctx.getString(R.string.droprelay_download_failed)
                                }
                            Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show()
                        },
                        onClickFile = { file ->
                            if (file.size > DropRelayLogic.DIRECT_DOWNLOAD_LIMIT_BYTES) {
                                p2pConfirmFile = file
                            } else {
                                val url = DropRelayLogic.fileWebseedUrl(shareUrl, file.normalizedPath)
                                val res = app.dropRelay.enqueueDownload(url, file.displayName)
                                val toastText =
                                    if (res.isSuccess) {
                                        ctx.getString(R.string.droprelay_download_started, file.displayName)
                                    } else {
                                        ctx.getString(R.string.droprelay_download_failed)
                                    }
                                Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                } else if (hasDrop) {
                    DropTransferTab(
                        dropUrl = dropUrl,
                        codeInput = codeInput,
                        onCodeInputChange = { codeInput = it },
                        onOpenReceiveBrowser = { code ->
                            openExternalUrl(ctx, DropRelayLogic.dropRoomUrl(dropUrl, code))
                        },
                        onOpenSendBrowser = {
                            openExternalUrl(ctx, dropUrl)
                        },
                    )
                }
            }
        }
    }

    val confirmFile = p2pConfirmFile
    if (confirmFile != null && hasShare) {
        MintBottomSheet(onDismissRequest = { p2pConfirmFile = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.droprelay_p2p_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${confirmFile.displayName} · ${DropRelayLogic.formatFileSize(confirmFile.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.droprelay_p2p_sheet_reason_size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        p2pConfirmFile = null
                        openExternalUrl(ctx, DropRelayLogic.fileBrowserUrl(shareUrl, confirmFile.normalizedPath))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.droprelay_p2p_browser_action))
                }
                OutlinedButton(
                    onClick = {
                        p2pConfirmFile = null
                        val url = DropRelayLogic.fileWebseedUrl(shareUrl, confirmFile.normalizedPath)
                        val res = app.dropRelay.enqueueDownload(url, confirmFile.displayName)
                        val toastText =
                            if (res.isSuccess) {
                                ctx.getString(R.string.droprelay_download_started, confirmFile.displayName)
                            } else {
                                ctx.getString(R.string.droprelay_download_failed)
                            }
                        Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.droprelay_p2p_direct_action))
                }
            }
        }
    }

    if (showConfigSheet) {
        DropRelayConfigSheet(
            initialShareUrl = st.dropRelayShareUrl,
            initialDropUrl = st.dropRelayDropUrl,
            defaultShareUrl = app.dropRelay.campusDefaults.shareUrl,
            defaultDropUrl = app.dropRelay.campusDefaults.dropUrl,
            hasDefaults = app.dropRelay.hasCampusDefaults(),
            onDismiss = { showConfigSheet = false },
            onSave = { newShare, newDrop ->
                app.settings.update {
                    it.copy(
                        dropRelayShareUrl = newShare.trim(),
                        dropRelayDropUrl = newDrop.trim(),
                    )
                }
                showConfigSheet = false
                Toast.makeText(ctx, R.string.droprelay_config_saved, Toast.LENGTH_SHORT).show()
                if (app.dropRelay.effectiveShareUrl(newShare).isNotBlank()) {
                    scope.launch { app.dropRelay.refreshIndex(force = true) }
                }
            },
        )
    }
}

@Composable
private fun ShareLibraryTab(
    shareUrl: String,
    state: DropRelayState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentDir: String,
    onNavigateDir: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenShareBrowser: () -> Unit,
    onDownloadDirZip: (ShareBrowseEntry.Dir) -> Unit,
    onClickFile: (DropRelayFile) -> Unit,
) {
    val files = state.index?.files.orEmpty()
    val searching = searchQuery.isNotBlank()
    val searchResults =
        remember(files, searchQuery) {
            if (searching) DropRelayLogic.searchFiles(files, searchQuery) else emptyList()
        }
    val dirEntries =
        remember(files, currentDir, searching) {
            if (searching) emptyList() else DropRelayLogic.listDirectory(files, currentDir)
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TonalField(
                label = "",
                value = searchQuery,
                onChange = onSearchQueryChange,
                placeholder = stringResource(R.string.droprelay_search_placeholder),
                modifier = Modifier.weight(1f),
            )
            if (searching) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.cancel))
                }
            }
        }

        if (state.indexError != null && state.index != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(
                    text = stringResource(R.string.droprelay_offline_cache_banner),
                    bg = MaterialTheme.colorScheme.secondaryContainer,
                    fg = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (!searching && currentDir.isNotBlank()) {
            BreadcrumbRow(
                currentDir = currentDir,
                onNavigate = onNavigateDir,
            )
        }

        when {
            state.loadingIndex && state.index == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }

            state.index == null && state.indexError != null -> {
                val baseHint = stringResource(R.string.droprelay_offline_hint)
                val hint =
                    buildString {
                        append(baseHint)
                        state.indexErrorDetail?.takeIf { it.isNotBlank() }?.let {
                            append("\n")
                            append(it)
                        }
                    }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = stringResource(R.string.droprelay_offline_title),
                        hint = hint,
                        icon = Icons.Rounded.FolderOpen,
                        actionLabel = stringResource(R.string.droprelay_retry),
                        onAction = onRetry,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenShareBrowser) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.droprelay_open_in_browser))
                    }
                }
            }

            searching && searchResults.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.droprelay_empty_title),
                    hint = stringResource(R.string.droprelay_empty_hint),
                    icon = Icons.Rounded.FolderOpen,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }

            !searching && dirEntries.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.droprelay_empty_title),
                    hint = shareUrl,
                    icon = Icons.Rounded.FolderOpen,
                    actionLabel = stringResource(R.string.droprelay_retry),
                    onAction = onRetry,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }

            searching -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(searchResults, key = { it.normalizedPath }) { file ->
                        ShareFileCard(
                            file = file,
                            showPath = true,
                            onClick = { onClickFile(file) },
                        )
                    }
                    item(key = "nav_spacer") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        dirEntries,
                        key = {
                            when (it) {
                                is ShareBrowseEntry.Dir -> "d:${it.path}"
                                is ShareBrowseEntry.FileItem -> "f:${it.file.normalizedPath}"
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is ShareBrowseEntry.Dir -> {
                                MintCard(
                                    onClick = { onNavigateDir(entry.path) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.droprelay_dir_meta,
                                                        entry.fileCount,
                                                        DropRelayLogic.formatFileSize(entry.totalBytes),
                                                    ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(onClick = { onDownloadDirZip(entry) }) {
                                            Icon(
                                                Icons.Rounded.Download,
                                                contentDescription = stringResource(R.string.droprelay_dir_zip_desc),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            is ShareBrowseEntry.FileItem -> {
                                ShareFileCard(
                                    file = entry.file,
                                    showPath = false,
                                    onClick = { onClickFile(entry.file) },
                                )
                            }
                        }
                    }
                    item(key = "nav_spacer") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShareFileCard(
    file: DropRelayFile,
    showPath: Boolean,
    onClick: () -> Unit,
) {
    val parentPath = file.normalizedPath.substringBeforeLast('/', "")
    MintCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    if (showPath && parentPath.isNotBlank()) {
                        "$parentPath · ${DropRelayLogic.formatFileSize(file.size)}"
                    } else {
                        DropRelayLogic.formatFileSize(file.size)
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BreadcrumbRow(
    currentDir: String,
    onNavigate: (String) -> Unit,
) {
    val segments = remember(currentDir) { currentDir.split('/').filter { it.isNotBlank() } }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.droprelay_root_dir),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .mintClickable(pressedScale = 0.96f) { onNavigate("") }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        var acc = ""
        segments.forEachIndexed { idx, seg ->
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            val target = acc
            val isLast = idx == segments.lastIndex
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = seg,
                style = MaterialTheme.typography.labelLarge,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                modifier =
                    Modifier
                        .mintClickable(enabled = !isLast, pressedScale = 0.96f) { onNavigate(target) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DropTransferTab(
    dropUrl: String,
    codeInput: String,
    onCodeInputChange: (String) -> Unit,
    onOpenReceiveBrowser: (String) -> Unit,
    onOpenSendBrowser: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboardCode =
        remember(dropUrl) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText =
                cm
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
                    .orEmpty()
            DropRelayLogic.extractDropCode(clipText, dropUrl)
        }
    val parsedCode = remember(codeInput, dropUrl) { DropRelayLogic.extractDropCode(codeInput, dropUrl) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "drop_receive") {
            MintCard {
                Text(
                    text = stringResource(R.string.droprelay_drop_receive_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                TonalField(
                    label = stringResource(R.string.droprelay_drop_code_label),
                    value = codeInput,
                    onChange = onCodeInputChange,
                    placeholder = stringResource(R.string.droprelay_drop_code_placeholder),
                )
                if (clipboardCode != null && !clipboardCode.equals(codeInput.trim(), ignoreCase = true)) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.mintClickable(pressedScale = 0.97f) {
                            onCodeInputChange(clipboardCode)
                            onOpenReceiveBrowser(clipboardCode)
                        },
                    ) {
                        Tag(
                            text = stringResource(R.string.droprelay_drop_clipboard_hint, clipboardCode),
                            bg = MaterialTheme.colorScheme.primaryContainer,
                            fg = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { parsedCode?.let(onOpenReceiveBrowser) },
                    enabled = parsedCode != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.droprelay_drop_receive_browser))
                }
            }
        }

        item(key = "drop_send") {
            MintCard {
                Text(
                    text = stringResource(R.string.droprelay_drop_send_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.droprelay_drop_send_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenSendBrowser,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.droprelay_drop_send_browser))
                }
            }
        }
        item(key = "nav_spacer") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }
}

@Composable
private fun DropRelayConfigSheet(
    initialShareUrl: String,
    initialDropUrl: String,
    defaultShareUrl: String,
    defaultDropUrl: String,
    hasDefaults: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var shareInput by remember(initialShareUrl) { mutableStateOf(initialShareUrl) }
    var dropInput by remember(initialDropUrl) { mutableStateOf(initialDropUrl) }

    MintBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.droprelay_config_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TonalField(
                label = stringResource(R.string.droprelay_config_share_label),
                value = shareInput,
                onChange = { shareInput = it },
                placeholder = defaultShareUrl.ifBlank { stringResource(R.string.droprelay_config_url_placeholder) },
            )
            TonalField(
                label = stringResource(R.string.droprelay_config_drop_label),
                value = dropInput,
                onChange = { dropInput = it },
                placeholder = defaultDropUrl.ifBlank { stringResource(R.string.droprelay_config_url_placeholder) },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasDefaults && (shareInput.isNotBlank() || dropInput.isNotBlank())) {
                    TextButton(onClick = {
                        shareInput = ""
                        dropInput = ""
                    }) {
                        Text(stringResource(R.string.droprelay_config_reset))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { onSave(shareInput, dropInput) }) {
                    Text(stringResource(R.string.droprelay_config_save))
                }
            }
        }
    }
}
