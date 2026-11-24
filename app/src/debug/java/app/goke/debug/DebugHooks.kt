package app.goke.debug

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.goke.R
import app.goke.core.change.Change
import app.goke.core.model.Semester
import app.goke.data.DemoData
import app.goke.jxfw.JwcwxClient
import app.goke.jxfw.JxfwClient
import app.goke.jxfw.TzcsClient
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.components.FormSection
import kotlinx.coroutines.launch
import java.time.LocalDateTime

object DebugHooks {
    fun init(ctx: Context) {
        DebugMockStore.init(ctx)
    }

    fun newJxfwClient(): JxfwClient = MockJxfwClient()

    fun newJwcwxClient(): JwcwxClient = MockJwcwxClient()

    fun newTzcsClient(): TzcsClient = MockTzcsClient()

    @Composable
    fun DataScreenExtra() {
        DemoDataSection()
    }

    @Composable
    fun SettingsDebugSection() {
        SettingsDebugMockSection()
    }

    @Composable
    fun JxfwDebugBanner() {
        JxfwDebugMockBanner()
    }

    @Composable
    fun CourseSelectionDebugBanner(onRefresh: () -> Unit) {
        CourseSelectionDebugMockBanner(onRefresh = onRefresh)
    }
}

/** 填入一批真实含量的演示数据：整批一次提交，可在操作记录里一步撤销。 */
@Composable
private fun DemoDataSection() {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val snap =
        app.repo.snapshot
            .collectAsState()
            .value
    val semester = snap.currentSemester
    val existing = snap.allRecords().count { it !is Semester && it.deletedAt == null }

    fun fill() {
        val sem = semester ?: return
        busy = true
        val all = snap.allRecords()
        scope.launch {
            val cs = DemoData.build(LocalDateTime.now(), sem.id, replace = true, existing = all)
            app.repo.commit(cs)
            busy = false
            actions.toast(ctx.getString(R.string.demo_data_done, cs.changes.count { it is Change.Add }))
        }
    }

    FormSection(stringResource(R.string.demo_data_title)) {
        FilledTonalButton(
            enabled = !busy && semester != null,
            onClick = { if (existing > 0) confirm = true else fill() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (busy) R.string.demo_data_running else R.string.demo_data_replace))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.demo_data_confirm_title)) },
            text = { Text(stringResource(R.string.demo_data_confirm_text, existing)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    fill()
                }) { Text(stringResource(R.string.demo_data_confirm_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
