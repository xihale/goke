package app.mintcal.ai

import app.mintcal.MintCalApp
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Source
import app.mintcal.core.util.Ids
import app.mintcal.script.ScriptOptions
import app.mintcal.script.ScriptOutcome
import app.mintcal.script.ScriptRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdvancedState(
    val description: String = "",
    val code: String = "",
    val codeSource: Source = Source.CODE,
    val running: Boolean = false,
    val generating: Boolean = false,
    val outcome: ScriptOutcome? = null,
    val message: String? = null,
    val previewOnTimetable: Boolean = false,
) {
    val changeSet: ChangeSet? get() = (outcome as? ScriptOutcome.Success)?.changeSet
}

/** 高级修改的状态机：描述 → （AI 生成）代码 → Dry Run 预览 → 应用 / 放弃。抽屉与全屏页共用。 */
class AdvancedController(
    private val app: MintCalApp,
) {
    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()
    private val runner = ScriptRunner()
    private val ai = AiClient()
    private var job: Job? = null

    fun setDescription(text: String) = _state.update { it.copy(description = text) }

    fun setCode(text: String) = _state.update { it.copy(code = text, codeSource = Source.CODE, outcome = null, previewOnTimetable = false) }

    fun setPreviewOnTimetable(on: Boolean) = _state.update { it.copy(previewOnTimetable = on) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun discard() {
        job?.cancel()
        _state.update { it.copy(outcome = null, previewOnTimetable = false, running = false, generating = false, message = null) }
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
                val outcome = runScript(code, _state.value.codeSource)
                _state.update { it.copy(running = false, outcome = outcome) }
            }
    }

    private suspend fun runScript(
        code: String,
        source: Source,
    ): ScriptOutcome {
        val desc = _state.value.description.ifBlank { "高级修改" }
        return runner.dryRun(code, app.repo.snapshot.value, ScriptOptions(source = source), desc.take(60))
    }

    /** 让 AI 根据描述写代码，随后自动 Dry Run；若预览出错最多自动让 AI 修正一次。 */
    fun generate() {
        val st = _state.value
        val key = app.secrets.aiKey()
        val settings = app.settings.value
        if (st.description.isBlank()) return
        if (key.isNullOrBlank()) {
            _state.update { it.copy(message = MSG_NO_KEY) }
            return
        }
        job?.cancel()
        job =
            app.scope.launch {
                _state.update { it.copy(generating = true, outcome = null, message = null) }
                try {
                    var code = ai.generateCode(settings.aiBaseUrl, key, settings.aiModel, st.description, app.repo.snapshot.value)
                    _state.update { it.copy(code = code, codeSource = Source.AI) }
                    var outcome = runScript(code, Source.AI)
                    if (outcome is ScriptOutcome.Failure) {
                        code = ai.generateCode(settings.aiBaseUrl, key, settings.aiModel, st.description, app.repo.snapshot.value, code, outcome.message)
                        _state.update { it.copy(code = code) }
                        outcome = runScript(code, Source.AI)
                    }
                    _state.update { it.copy(generating = false, outcome = outcome) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(generating = false, message = "AI：${e.message ?: e.javaClass.simpleName}") }
                }
            }
    }

    /** 应用当前预览的变更集（与预览完全一致），并保留在操作历史里以便撤销。 */
    fun apply(onDone: (ChangeSet) -> Unit = {}) {
        val cs = _state.value.changeSet ?: return
        if (cs.isEmpty) return
        app.scope.launch {
            app.repo.commit(cs.copy(id = Ids.next(), source = _state.value.codeSource))
            _state.update { it.copy(outcome = null, previewOnTimetable = false) }
            onDone(cs)
        }
    }

    companion object {
        const val MSG_NO_KEY = "NO_KEY"
    }
}
