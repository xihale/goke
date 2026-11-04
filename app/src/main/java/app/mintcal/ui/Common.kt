package app.mintcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 当前 Tab 是否可见。由 Tabs 用动态 CompositionLocal 提供（只在 [gated]/[liveSnapshot]/
 * [rememberNowGated] 内部读取）——可见性翻转只重组这几个小助手，屏幕 body 的参数没变就整体跳过。
 * 压栈页 / 测试环境没有 provider，默认可见。
 */
val LocalTabVisible = compositionLocalOf { true }

/** 隐藏 Tab 页的「冻结」键：隐藏时不读 [source]，用上次冻结的 key 让 remember 直接复用。 */
private class FrozenKey(
    var v: Any? = null,
    var set: Boolean = false,
)

@Composable
fun <S : Any, T> gated(
    source: () -> S,
    calculate: (S) -> T,
): T {
    val visible = LocalTabVisible.current
    val holder = remember { FrozenKey() }
    val s: S =
        if (visible || !holder.set) {
            source().also {
                holder.v = it
                holder.set = true
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            holder.v as S
        }
    return remember(s) { calculate(s) }
}

/** 过滤掉已删除记录的当前快照。隐藏 Tab（常驻的另一页）冻结在上次结果上，不订阅数据变化。 */
@Composable
fun liveSnapshot(): Snapshot {
    val app = LocalApp.current
    val state = app.repo.snapshot.collectAsState()
    return gated({ state.value }) { it.live() }
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
 * [rememberNow] 的门控版：隐藏 Tab 不读底层 state（不订阅 30 秒跳变），重算只发生在可见时。
 * 返回直接值——调用方在 body 顶层用它，再把重粒度推导的 key 从 now 换成它派生的日期。
 */
@Composable
fun rememberNowGated(): LocalDateTime {
    val nowState = rememberNow()
    return gated({ nowState.value }) { it }
}

/**
 * 「今天」：由 [rememberNow] 派生 + structural equality——同一个日期反复计算不会通知读它
 * 的作用域。传给整张周网格/月网格的 `today` 参数从此只在跨天时才让子树重组。
 */
@Composable
fun rememberToday(): State<java.time.LocalDate> {
    val now = rememberNow()
    return remember(now) { derivedStateOf { now.value.toLocalDate() } }
}
