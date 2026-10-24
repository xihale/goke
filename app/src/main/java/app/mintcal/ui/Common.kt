package app.mintcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.mintcal.core.model.Snapshot
import app.mintcal.data.Settings
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** 过滤掉已删除记录的当前快照。 */
@Composable
fun liveSnapshot(): Snapshot {
    val app = LocalApp.current
    val s by app.repo.snapshot.collectAsState()
    return remember(s) { s.live() }
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
