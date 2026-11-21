package app.mintcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 当前 Tab 是否处于活跃/可见状态。
 * 由 Tabs 通过稳定的 State<Boolean> 提供（实例引用不变），
 * 避免切 Tab 时由于 CompositionLocal 实例变化导致下游所有 Tab 根作用域无谓失效。
 */
val LocalTabVisible = compositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/** 快捷读取当前 Tab 可见性布尔值。 */
val isTabVisible: Boolean
    @Composable
    get() = LocalTabVisible.current.value

/**
 * 通用门控助手：在隐藏态不更新返回值，避免非当前 Tab 在不可见时触发无谓重组。
 * 兼容旧签名与调用方，同时通过 snapshotFlow 隔离可见性状态读取，不破坏外部根调用者的重组作用域。
 */
@Composable
fun <S : Any, T> gated(
    source: () -> S,
    calculate: (S) -> T,
): T {
    val tabVisibleState = LocalTabVisible.current
    val currentSource = rememberUpdatedState(source)
    val currentCalculate = rememberUpdatedState(calculate)
    val state =
        produceState(
            initialValue = currentCalculate.value(currentSource.value()),
            key1 = tabVisibleState,
        ) {
            snapshotFlow { tabVisibleState.value to currentSource.value() }
                .collect { (visible, s) ->
                    if (visible) {
                        val calc = currentCalculate.value(s)
                        if (value != calc) {
                            value = calc
                        }
                    }
                }
        }
    return state.value
}

/**
 * 过滤掉已删除记录的当前快照。
 * 门控优化：隐藏 Tab（常驻的另一页）取消订阅数据 Flow，不触发后台重组；
 * 仅在当前 Tab 可见且快照真正变更时才发出新快照；切 Tab 时若数据未变则零重组。
 */
@Composable
fun liveSnapshot(): Snapshot {
    val app = LocalApp.current
    val tabVisibleState = LocalTabVisible.current
    val state =
        produceState(
            initialValue =
                app.repo.snapshot.value
                    .live(),
            key1 = app,
            key2 = tabVisibleState,
        ) {
            snapshotFlow { tabVisibleState.value }
                .collectLatest { visible ->
                    if (visible) {
                        app.repo.snapshot.collect { rawSnap ->
                            val liveSnap = rawSnap.live()
                            if (value !== liveSnap && value != liveSnap) {
                                value = liveSnap
                            }
                        }
                    }
                }
        }
    return state.value
}

@Composable
fun settingsState(): Settings {
    val app = LocalApp.current
    val s by app.settings.flow.collectAsState()
    return s
}

/** 系统开启「移除动画」时为 true（配合 MintTheme.spec 使过渡直接切换）。 */
@Composable
fun reduceMotion(): Boolean = app.mintcal.ui.theme.LocalReduceMotion.current

/** 每 30 秒刷新一次的「现在」。 */
@Composable
fun rememberNow(): State<LocalDateTime> =
    produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }

/**
 * [rememberNow] 的门控版：
 * 1. 隐藏 Tab 停止 30 秒轮询协程，零后台 CPU 唤醒；
 * 2. 状态读取下沉至 snapshotFlow，切 Tab 时不让调用方根作用域同步失效；
 * 3. 重新可见时若距上次刷新已达 30 秒或跨天，立即同步校准时间；短时间（如快速切 Tab）复用现有时刻，避免瞬时重组。
 */
@Composable
fun rememberNowGated(): LocalDateTime {
    val tabVisibleState = LocalTabVisible.current
    val state =
        produceState(
            initialValue = remember { LocalDateTime.now() },
            key1 = tabVisibleState,
        ) {
            snapshotFlow { tabVisibleState.value }
                .collectLatest { visible ->
                    if (visible) {
                        val current = LocalDateTime.now()
                        if (value.toLocalDate() != current.toLocalDate() ||
                            Duration.between(value, current).abs().seconds >= 30
                        ) {
                            value = current
                        }
                        while (true) {
                            delay(30_000)
                            value = LocalDateTime.now()
                        }
                    }
                }
        }
    return state.value
}

/**
 * 「今天」：由 [rememberNow] 派生 + structural equality——同一个日期反复计算不会通知读它
 * 的作用域。传给整张周网格/月网格的 `today` 参数从此只在跨天时才让子树重组。
 */
@Composable
fun rememberToday(): State<LocalDate> {
    val now = rememberNow()
    return remember(now) { derivedStateOf { now.value.toLocalDate() } }
}
