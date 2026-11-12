package app.mintcal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.parcel.DeadlineSpec
import app.mintcal.core.parcel.ParcelRule
import app.mintcal.core.parcel.ParcelRuleEngine
import app.mintcal.core.parcel.RuleTemplate
import app.mintcal.core.util.Ids
import app.mintcal.data.ParcelRuleStore
import app.mintcal.ui.components.ChipChoice
import app.mintcal.ui.components.FieldText
import app.mintcal.ui.components.FormSection

/** 正在编辑的规则：originBuiltinId 非空 = 编辑的是内置种子，保存时 fork 成用户规则并停用原条目。 */
data class RuleDraft(
    val rule: ParcelRule,
    val originBuiltinId: String? = null,
    val sample: String = "",
    val isNew: Boolean = false,
)

/** 快递规则管理：自定义规则列表（增改停用）、内置开关、噪声词。 */
@Composable
fun ParcelRulesSection(store: ParcelRuleStore) {
    val cfg by store.flow.collectAsState()
    var draft by remember { mutableStateOf<RuleDraft?>(null) }
    var noiseOpen by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.parcel_rules)) {
        if (cfg.user.isEmpty()) {
            Text(
                stringResource(R.string.parcel_rules_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        cfg.user.forEach { rule ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { draft = RuleDraft(rule) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(rule.name.ifBlank { rule.template.take(12) }, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        rule.template,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(rule.enabled, onCheckedChange = { store.upsert(rule.copy(enabled = it)) })
            }
        }
        TextButton({ draft = RuleDraft(ParcelRule(Ids.next(), "", ""), isNew = true) }) {
            Text(stringResource(R.string.parcel_rule_add))
        }
    }
    FormSection(stringResource(R.string.parcel_rules_builtin)) {
        ParcelRuleEngine.BUILTIN.forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { draft = RuleDraft(b.copy(id = Ids.next(), builtin = false), originBuiltinId = b.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        b.template,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(b.id !in cfg.disabledBuiltins, onCheckedChange = { store.setBuiltinEnabled(b.id, it) })
            }
        }
        TextButton({ store.restoreDefaults() }) { Text(stringResource(R.string.parcel_rules_restore)) }
    }
    FormSection(stringResource(R.string.parcel_noise)) {
        Text(
            cfg.rejects.joinToString("，"),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        TextButton({ noiseOpen = true }) { Text(stringResource(R.string.parcel_noise_edit)) }
    }

    draft?.let { RuleEditSheet(it, store) { draft = null } }
    if (noiseOpen) NoiseWordsSheet(store, cfg.rejects) { noiseOpen = false }
}

/** 规则编辑 sheet：模板 + 截止语义 + 样本实测；内置规则保存即 fork。 */
@Composable
fun RuleEditSheet(
    draft: RuleDraft,
    store: ParcelRuleStore,
    onDismiss: () -> Unit,
) {
    var rule by remember(draft) { mutableStateOf(draft.rule) }
    var sample by remember(draft) { mutableStateOf(draft.sample) }
    val err = RuleTemplate.validate(rule.template, rule.deadline)

    fun sampleCode(): String? {
        if (sample.isBlank() || err != null) return null
        val c = RuleTemplate.compile(rule.template)
        return c
            .find(sample)
            ?.groups
            ?.get("code")
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (draft.isNew || draft.originBuiltinId != null) R.string.parcel_rule_new_title else R.string.parcel_rule_edit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            FieldText(stringResource(R.string.parcel_rule_name), rule.name, { rule = rule.copy(name = it) })
            FieldText(
                stringResource(R.string.parcel_rule_template),
                rule.template,
                { rule = rule.copy(template = it) },
                singleLine = false,
                minLines = 2,
                supporting = stringResource(R.string.parcel_rule_template_hint),
                isError = err != null,
            )
            FieldText(stringResource(R.string.parcel_rule_company), rule.company.orEmpty(), { rule = rule.copy(company = it.ifBlank { null }) })

            Text(stringResource(R.string.parcel_rule_deadline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipChoice(
                options =
                    listOf(
                        DeadlineSpec.None to stringResource(R.string.parcel_deadline_none),
                        DeadlineSpec.AtTime() to stringResource(R.string.parcel_deadline_at),
                        DeadlineSpec.AddHours() to stringResource(R.string.parcel_deadline_hours),
                        DeadlineSpec.Const(rule.deadline.let { (it as? DeadlineSpec.Const)?.hours ?: 18 }) to stringResource(R.string.parcel_deadline_const),
                        DeadlineSpec.Closing() to stringResource(R.string.parcel_deadline_closing),
                    ),
                selected = rule.deadline,
                onSelect = { rule = rule.copy(deadline = it) },
            )
            if (rule.deadline is DeadlineSpec.Const) {
                val c = rule.deadline as DeadlineSpec.Const
                FieldText(
                    stringResource(R.string.parcel_deadline_const_hours),
                    c.hours.toString(),
                    { rule = rule.copy(deadline = DeadlineSpec.Const(it.toIntOrNull()?.coerceIn(1, 96) ?: 18)) },
                    number = true,
                )
            }

            FieldText(
                stringResource(R.string.parcel_rule_sample),
                sample,
                { sample = it },
                singleLine = false,
                minLines = 2,
                isError = sample.isNotBlank() && err == null && sampleCode() == null,
                supporting = if (sample.isNotBlank() && err == null) sampleCode()?.let { stringResource(R.string.parcel_rule_sample_ok, it) } ?: stringResource(R.string.parcel_rule_sample_bad) else null,
            )
            err?.let { e ->
                Text(
                    when (e) {
                        is RuleTemplate.Error.Empty -> stringResource(R.string.parcel_rule_err_empty)
                        is RuleTemplate.Error.NoCode -> stringResource(R.string.parcel_rule_err_no_code)
                        is RuleTemplate.Error.Unknown -> stringResource(R.string.parcel_rule_err_unknown, e.name)
                        is RuleTemplate.Error.Duplicate -> stringResource(R.string.parcel_rule_err_dup, e.name)
                        is RuleTemplate.Error.BadCap -> stringResource(R.string.parcel_rule_err_cap, e.name)
                        is RuleTemplate.Error.NoMatch -> stringResource(R.string.parcel_rule_sample_bad)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row {
                if (draft.originBuiltinId == null) {
                    TextButton({
                        store.remove(rule.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.parcel_rule_delete)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val saved = rule.copy(enabled = true, builtin = false)
                        draft.originBuiltinId?.let { store.setBuiltinEnabled(it, false) }
                        store.upsert(saved)
                        onDismiss()
                    },
                    enabled = err == null && (sample.isBlank() || sampleCode() != null),
                ) { Text(stringResource(R.string.parcel_rule_save)) }
            }
        }
    }
}

/** 噪声词编辑 sheet：逗号分隔，保存时全量替换。 */
@Composable
private fun NoiseWordsSheet(
    store: ParcelRuleStore,
    current: List<String>,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.joinToString("，")) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.parcel_noise_title), style = MaterialTheme.typography.titleMedium)
            FieldText(
                stringResource(R.string.parcel_noise),
                text,
                { text = it },
                singleLine = false,
                minLines = 2,
                supporting = stringResource(R.string.parcel_noise_desc),
            )
            Row {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = {
                    store.setRejects(text.split("，", ","))
                    onDismiss()
                }) { Text(stringResource(R.string.parcel_rule_save)) }
            }
        }
    }
}
