package app.mintcal.advanced

import app.mintcal.MintCalApp
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Source
import app.mintcal.core.util.Ids
import app.mintcal.script.ScriptOptions
import app.mintcal.script.ScriptOutcome
import app.mintcal.script.ScriptRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdvancedState(
    val code: String = "",
    val running: Boolean = false,
    val outcome: ScriptOutcome? = null,
    val message: String? = null,
    val previewOnTimetable: Boolean = false,
) {
    val changeSet: ChangeSet? get() = (outcome as? ScriptOutcome.Success)?.changeSet
}

/** 高级修改的状态机：代码 → Dry Run 预览 → 应用 / 放弃。抽屉与全屏页共用。 */
class AdvancedController(
    private val app: MintCalApp,
) {
    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()
    private val runner = ScriptRunner()
    private var job: Job? = null

    fun setCode(text: String) = _state.update { it.copy(code = text, outcome = null, previewOnTimetable = false) }

    fun setPreviewOnTimetable(on: Boolean) = _state.update { it.copy(previewOnTimetable = on) }

    fun discard() {
        job?.cancel()
        _state.update { it.copy(outcome = null, previewOnTimetable = false, running = false, message = null) }
    }

    fun reset() {
        job?.cancel()
        _state.value = AdvancedState()
    }

    /** Dry Run：在快照上运行脚本，只生成变更集，不落库。 */
    fun dryRun() {
        val code = _state.value.code
        if (code.isBlank()) return
        job?.cancel()
        job =
            app.scope.launch {
                _state.update { it.copy(running = true, outcome = null, message = null) }
                val outcome = runScript(code)
                _state.update { it.copy(running = false, outcome = outcome) }
            }
    }

    private suspend fun runScript(code: String): ScriptOutcome = runner.dryRun(code, app.repo.snapshot.value, ScriptOptions(source = Source.CODE))

    /** 应用当前预览的变更集（与预览完全一致），并保留在操作历史里以便撤销。 */
    fun apply(onDone: (ChangeSet) -> Unit = {}) {
        val cs = _state.value.changeSet ?: return
        if (cs.isEmpty) return
        app.scope.launch {
            app.repo.commit(cs.copy(id = Ids.next(), source = Source.CODE))
            onDone(cs)
        }
    }
}
