package app.goke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.goke.hotwater.HotWaterApiResult
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterMetrics
import app.goke.hotwater.HotWaterOrder
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.notify.Notifications
import app.goke.ui.LocalApp
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.Tag
import kotlinx.coroutines.launch

private enum class HotWaterBillTab {
    CURRENT,
    ALL,
}

private data class BillTabState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val orders: List<HotWaterOrder> = emptyList(),
    val totalAmount: Double? = null,
    val hasMore: Boolean = false,
    val pageIndex: Int = 1,
    val error: HotWaterErrorCode? = null,
)

@Composable
fun HotWaterBillsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(HotWaterBillTab.CURRENT) }
    var currentTab by remember { mutableStateOf(BillTabState()) }
    var allTab by remember { mutableStateOf(BillTabState()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val localRecords = remember(reloadKey) { app.hotWater.cachedShowerRecords() }

    fun tabState(target: HotWaterBillTab): BillTabState = if (target == HotWaterBillTab.CURRENT) currentTab else allTab

    fun setTabState(
        target: HotWaterBillTab,
        value: BillTabState,
    ) {
        if (target == HotWaterBillTab.CURRENT) currentTab = value else allTab = value
    }

    suspend fun loadPage(
        target: HotWaterBillTab,
        pageIndex: Int,
        append: Boolean,
    ) {
        val prev = tabState(target)
        setTabState(target, prev.copy(loading = true, error = null))
        when (val res = app.hotWater.fetchOrders(all = target == HotWaterBillTab.ALL, pageIndex = pageIndex)) {
            is HotWaterApiResult.Ok -> {
                val merged =
                    if (append) {
                        (prev.orders + res.value.orders).distinctBy { it.orderId }
                    } else {
                        res.value.orders
                    }
                setTabState(
                    target,
                    BillTabState(
                        loaded = true,
                        loading = false,
                        orders = merged,
                        totalAmount = res.value.totalAmount ?: prev.totalAmount,
                        hasMore = res.value.hasMore,
                        pageIndex = pageIndex,
                        error = null,
                    ),
                )
            }

            is HotWaterApiResult.Err -> {
                setTabState(
                    target,
                    prev.copy(
                        loaded = prev.loaded,
                        loading = false,
                        error = res.code,
                    ),
                )
            }
        }
    }

    LaunchedEffect(tab, reloadKey) {
        val st = tabState(tab)
        if (!st.loaded && !st.loading) {
            loadPage(tab, pageIndex = 1, append = false)
        }
    }

    val active = tabState(tab)
    val tabOptions =
        listOf(
            HotWaterBillTab.CURRENT to stringResource(R.string.hotwater_bills_title),
            HotWaterBillTab.ALL to stringResource(R.string.hotwater_bills_all),
        )

    MintScreen(
        title = stringResource(R.string.hotwater_bills_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "bill_tabs") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SegmentedPill(
                        options = tabOptions,
                        selected = tab,
                        onSelect = { tab = it },
                    )
                    if (active.loaded && active.orders.isNotEmpty() && tab == HotWaterBillTab.CURRENT) {
                        val total =
                            active.totalAmount
                                ?: active.orders.sumOf { it.usePrice.toDoubleOrNull() ?: 0.0 }
                        Text(
                            text = stringResource(R.string.hotwater_bills_summary, active.orders.size, total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            when {
                active.loading && active.orders.isEmpty() -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                active.error != null && active.orders.isEmpty() -> {
                    item(key = "error") {
                        MintCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = stringResource(active.error.errorStringRes()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                OutlinedButton(
                                    onClick = {
                                        setTabState(tab, active.copy(loaded = false, error = null))
                                        reloadKey++
                                    },
                                ) {
                                    Text(
                                        stringResource(R.string.hotwater_bills_retry),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                active.loaded && active.orders.isEmpty() -> {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.hotwater_bills_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    items(items = active.orders, key = { it.orderId }) { order ->
                        BillRow(
                            order = order,
                            showStatus = tab == HotWaterBillTab.ALL,
                            localRecords = localRecords,
                        )
                    }
                    if (active.hasMore) {
                        item(key = "load_more") {
                            OutlinedButton(
                                onClick = {
                                    if (!active.loading) {
                                        scope.launch {
                                            loadPage(tab, pageIndex = active.pageIndex + 1, append = true)
                                        }
                                    }
                                },
                                enabled = !active.loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.hotwater_bills_load_more),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}

@Composable
private fun BillRow(
    order: HotWaterOrder,
    showStatus: Boolean,
    localRecords: List<HotWaterShowerRecord>,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val metrics = HotWaterMetrics.estimateForOrder(order, localRecords)
    MintCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = order.createTime.ifBlank { order.orderId },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metrics.liters > 0.0) {
                    val durationSec = metrics.durationSec
                    val metricsText =
                        if (durationSec != null) {
                            val durationText = Notifications.formatHotWaterDuration(ctx, durationSec)
                            stringResource(R.string.hotwater_bill_metrics_exact, durationText, metrics.liters)
                        } else {
                            stringResource(R.string.hotwater_bill_metrics_liters, metrics.liters)
                        }
                    Text(
                        text = metricsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val subText =
                    if (order.isPaid && order.payTime.isNotBlank()) {
                        stringResource(R.string.hotwater_bills_pay_time, order.payTime)
                    } else {
                        order.orderId
                    }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showStatus) {
                Tag(
                    text =
                        stringResource(
                            if (order.isPaid) R.string.hotwater_bills_status_paid else R.string.hotwater_bills_status_unpaid,
                        ),
                    bg = if (order.isPaid) scheme.surfaceContainerHighest else scheme.secondaryContainer,
                    fg = if (order.isPaid) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.hotwater_bills_amount, order.usePrice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (order.isPaid) scheme.onSurface else scheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
