package app.goke.advanced

import app.goke.GokeApp
import app.goke.core.change.ChangeSet
import app.goke.core.model.Source
import app.goke.core.util.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

data class AdvancedState(
    val changeSet: ChangeSet? = null,
    val previewOnTimetable: Boolean = false,
)

/**
 * 高级修改预览桥：高级修改仅通过本地 MCP 向 AI Agent 暴露；
 * 当 Agent 调用 `preview_script` 时，可将 Dry Run 变更集暂存于此，在课表页叠加展示差异预览，
 * 支持用户在课表顶栏直接点「应用 / 放弃」，或由 Agent 调用 `apply_script` 提交。
 */
class AdvancedController(
    private val app: GokeApp,
) {
    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()
    private val onConsumedListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun addOnConsumedListener(listener: (String) -> Unit): () -> Unit {
        onConsumedListeners += listener
        return { onConsumedListeners -= listener }
    }

    fun stagePreview(
        changeSet: ChangeSet,
        showOnTimetable: Boolean = true,
    ) {
        _state.value = AdvancedState(changeSet = changeSet, previewOnTimetable = showOnTimetable && !changeSet.isEmpty)
    }

    fun discard() {
        val cs = _state.value.changeSet
        _state.value = AdvancedState()
        if (cs != null) notifyConsumed(cs.id)
    }

    fun clearIfMatching(previewId: String) {
        if (_state.value.changeSet?.id == previewId) {
            _state.value = AdvancedState()
        }
    }

    /** 应用当前暂存的预览变更集，并记入操作历史以便一键撤销。 */
    fun apply(onDone: (ChangeSet) -> Unit = {}) {
        val cs = _state.value.changeSet ?: return
        if (cs.isEmpty) {
            discard()
            return
        }
        _state.value = AdvancedState()
        notifyConsumed(cs.id)
        app.scope.launch {
            val committed = app.repo.commit(cs.copy(id = Ids.next(), source = Source.CODE))
            onDone(committed)
        }
    }

    private fun notifyConsumed(previewId: String) {
        onConsumedListeners.forEach { it(previewId) }
    }
}
