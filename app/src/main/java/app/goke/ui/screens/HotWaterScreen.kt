package app.goke.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.goke.R
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterPhase
import app.goke.ui.FeedbackEvent
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.Route
import app.goke.ui.components.ChipChoice
import app.goke.ui.components.MintAlertDialog
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.NavRow
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.SegmentedPill
import app.goke.ui.components.SwitchRow
import app.goke.ui.components.Tag
import app.goke.ui.components.TonalField
import app.goke.ui.icons.History
import kotlinx.coroutines.launch

private enum class HotWaterBindMode {
    PASSWORD,
    SMS,
    USER_ID,
}

@Composable
fun HotWaterScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.hotWater.state.collectAsState()

    var bindMode by remember { mutableStateOf(HotWaterBindMode.PASSWORD) }
    var phone by remember { mutableStateOf(state.config.phone) }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var smsSent by remember { mutableStateOf(false) }
    var userIdInput by remember { mutableStateOf("") }
    var uuidInput by remember { mutableStateOf("") }
    var confirmUnbind by remember { mutableStateOf(false) }
    var hasBlePerm by remember { mutableStateOf(checkBlePermissions(ctx)) }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasBlePerm = checkBlePermissions(ctx)
        }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    MintScreen(
        title = stringResource(R.string.hotwater_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isBound) {
                if (!app.hotWater.isSupported) {
                    item(key = "adapter_unavailable") {
                        MintCard {
                            Text(
                                stringResource(R.string.adapter_unavailable_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "bind_card") {
                    val modeOptions =
                        listOf(
                            HotWaterBindMode.PASSWORD to stringResource(R.string.hotwater_mode_password),
                            HotWaterBindMode.SMS to stringResource(R.string.hotwater_mode_sms),
                            HotWaterBindMode.USER_ID to stringResource(R.string.hotwater_mode_userid),
                        )
                    val canSubmit =
                        when (bindMode) {
                            HotWaterBindMode.PASSWORD -> phone.isNotBlank() && password.isNotBlank()
                            HotWaterBindMode.SMS -> phone.isNotBlank() && smsCode.isNotBlank()
                            HotWaterBindMode.USER_ID -> userIdInput.isNotBlank()
                        }
                    MintCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SegmentedPill(
                                options = modeOptions,
                                selected = bindMode,
                                onSelect = { bindMode = it },
                            )
                            when (bindMode) {
                                HotWaterBindMode.PASSWORD -> {
                                    TonalField(
                                        label = stringResource(R.string.hotwater_phone_label),
                                        value = phone,
                                        onChange = { phone = it },
                                        number = true,
                                        placeholder = stringResource(R.string.hotwater_phone_placeholder),
                                    )
                                    TonalField(
                                        label = stringResource(R.string.hotwater_password_label),
                                        value = password,
                                        onChange = { password = it },
                                        secret = true,
                                        placeholder = stringResource(R.string.hotwater_password_placeholder),
                                    )
                                }

                                HotWaterBindMode.SMS -> {
                                    TonalField(
                                        label = stringResource(R.string.hotwater_phone_label),
                                        value = phone,
                                        onChange = { phone = it },
                                        number = true,
                                        placeholder = stringResource(R.string.hotwater_phone_placeholder),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(Modifier.weight(1f)) {
                                            TonalField(
                                                label = stringResource(R.string.hotwater_sms_code_label),
                                                value = smsCode,
                                                onChange = { smsCode = it },
                                                number = true,
                                                placeholder = stringResource(R.string.hotwater_sms_code_placeholder),
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val res = app.hotWater.sendSmsCode(phone.trim())
                                                    if (res.ok) smsSent = true
                                                }
                                            },
                                            enabled = !state.busy && phone.isNotBlank(),
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (smsSent) R.string.hotwater_sms_sent else R.string.hotwater_send_sms,
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                HotWaterBindMode.USER_ID -> {
                                    TonalField(
                                        label = stringResource(R.string.hotwater_userid_label),
                                        value = userIdInput,
                                        onChange = { userIdInput = it },
                                        placeholder = stringResource(R.string.hotwater_userid_placeholder),
                                    )
                                    TonalField(
                                        label = stringResource(R.string.hotwater_uuid_label),
                                        value = uuidInput,
                                        onChange = { uuidInput = it },
                                        placeholder = stringResource(R.string.hotwater_uuid_placeholder),
                                    )
                                }
                            }

                            if (state.errorCode != HotWaterErrorCode.NONE) {
                                Text(
                                    text = hotWaterErrorMessage(state.errorCode, state.errorDetail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val res =
                                            when (bindMode) {
                                                HotWaterBindMode.PASSWORD -> {
                                                    app.hotWater.loginWithPassword(phone.trim(), password)
                                                }

                                                HotWaterBindMode.SMS -> {
                                                    app.hotWater.loginWithSms(phone.trim(), smsCode.trim())
                                                }

                                                HotWaterBindMode.USER_ID -> {
                                                    app.hotWater.bindWithUserId(userIdInput.trim(), uuidInput.trim())
                                                }
                                            }
                                        if (res.ok) actions.feedback(FeedbackEvent.SUCCESS)
                                    }
                                },
                                enabled = !state.busy && canSubmit,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.hotwater_bind_submit),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.hotwater_bind_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item(key = "control_card") {
                    val scheme = MaterialTheme.colorScheme
                    val phaseLabel =
                        when (state.phase) {
                            HotWaterPhase.UNBOUND -> stringResource(R.string.hotwater_status_unbound)
                            HotWaterPhase.IDLE -> stringResource(R.string.hotwater_status_idle)
                            HotWaterPhase.WORKING -> stringResource(R.string.hotwater_status_working)
                            HotWaterPhase.VALVE_OPEN -> stringResource(R.string.hotwater_status_open)
                            HotWaterPhase.ERROR -> stringResource(R.string.hotwater_status_error)
                        }
                    val phaseBg =
                        when (state.phase) {
                            HotWaterPhase.VALVE_OPEN -> scheme.primaryContainer
                            HotWaterPhase.ERROR -> scheme.errorContainer
                            else -> scheme.surfaceContainerHighest
                        }
                    val phaseFg =
                        when (state.phase) {
                            HotWaterPhase.VALVE_OPEN -> scheme.onPrimaryContainer
                            HotWaterPhase.ERROR -> scheme.error
                            else -> scheme.onSurfaceVariant
                        }
                    val isWorking = state.phase == HotWaterPhase.WORKING || state.busy

                    MintCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = state.config.dormLabel.ifBlank { stringResource(R.string.hotwater_title) },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text =
                                            if (state.config.machineSn.isNotBlank()) {
                                                stringResource(R.string.hotwater_meter_sn, state.config.machineSn)
                                            } else {
                                                stringResource(R.string.hotwater_no_meter)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Tag(text = phaseLabel, bg = phaseBg, fg = phaseFg)
                            }

                            val balanceYuan = state.canUseAmount / 100.0
                            val (balanceBg, balanceFg) =
                                when {
                                    state.isArrearage || balanceYuan <= 3.0 -> scheme.errorContainer to scheme.error
                                    balanceYuan < 10.0 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                                    else -> scheme.primaryContainer to scheme.onPrimaryContainer
                                }
                            Tag(
                                text = stringResource(R.string.hotwater_can_use_amount, balanceYuan),
                                bg = balanceBg,
                                fg = balanceFg,
                            )

                            if (!hasBlePerm) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(scheme.errorContainer)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.hotwater_ble_perm_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { permLauncher.launch(requiredBlePermissions()) }) {
                                        Text(stringResource(R.string.hotwater_ble_perm_grant))
                                    }
                                }
                            }

                            if (state.errorCode != HotWaterErrorCode.NONE) {
                                Text(
                                    text = hotWaterErrorMessage(state.errorCode, state.errorDetail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                )
                            }

                            Button(
                                onClick = {
                                    app.scope.launch {
                                        if (app.hotWater.toggleValve().ok) {
                                            actions.feedback(FeedbackEvent.SUCCESS)
                                        }
                                    }
                                },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isWorking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = scheme.onPrimary,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        stringResource(R.string.hotwater_valve_working_btn),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        stringResource(
                                            if (state.phase == HotWaterPhase.VALVE_OPEN) {
                                                R.string.hotwater_valve_close_btn
                                            } else {
                                                R.string.hotwater_valve_open_btn
                                            },
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    app.scope.launch {
                                        if (app.hotWater.refreshStatusAndCheckArrears(probeBle = true).ok) {
                                            actions.feedback(FeedbackEvent.SUCCESS)
                                        }
                                    }
                                },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.hotwater_refresh_status),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                item(key = "bills_card") {
                    MintCard {
                        NavRow(
                            title = stringResource(R.string.hotwater_bills_title),
                            subtitle = stringResource(R.string.hotwater_bills_entry_desc),
                            icon = Icons.Rounded.History,
                        ) {
                            nav.push(Route.HotWaterBills)
                        }
                    }
                }

                item(key = "arrears_card") {
                    val thresholdChoices =
                        listOf(3, 5, 8, 10).map { yuan ->
                            yuan to stringResource(R.string.hotwater_arrears_threshold_yuan, yuan)
                        }
                    MintCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionLabel(stringResource(R.string.hotwater_arrears_section))
                            SwitchRow(
                                title = stringResource(R.string.hotwater_arrears_enable),
                                subtitle = stringResource(R.string.hotwater_arrears_enable_desc),
                                checked = state.config.arrearsTodoEnabled,
                                onChange = { enabled ->
                                    app.hotWater.updateArrearsConfig(enabled, state.config.arrearsThresholdYuan)
                                },
                            )
                            Text(
                                text = stringResource(R.string.hotwater_arrears_threshold),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ChipChoice(
                                options = thresholdChoices,
                                selected = state.config.arrearsThresholdYuan,
                                onSelect = { yuan ->
                                    app.hotWater.updateArrearsConfig(state.config.arrearsTodoEnabled, yuan)
                                },
                            )
                            SwitchRow(
                                title = stringResource(R.string.hotwater_notify_enable),
                                subtitle = stringResource(R.string.hotwater_notify_enable_desc),
                                checked = state.config.showerSummaryNotifyEnabled,
                                onChange = { enabled ->
                                    app.hotWater.updateShowerNotifyEnabled(enabled)
                                    if (enabled &&
                                        Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    ) {
                                        askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            )
                        }
                    }
                }

                item(key = "account_card") {
                    MintCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(stringResource(R.string.hotwater_account_section))
                            Text(
                                text = stringResource(R.string.hotwater_account_userid, state.userIdMasked),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { confirmUnbind = true }) {
                                Text(
                                    stringResource(R.string.hotwater_unbind),
                                    color = MaterialTheme.colorScheme.error,
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

    if (confirmUnbind) {
        MintAlertDialog(
            onDismissRequest = { confirmUnbind = false },
            title = { Text(stringResource(R.string.hotwater_unbind_confirm_title)) },
            text = { Text(stringResource(R.string.hotwater_unbind_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnbind = false
                        app.hotWater.unbind()
                    },
                ) {
                    Text(
                        stringResource(R.string.hotwater_unbind_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnbind = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun hotWaterErrorMessage(
    code: HotWaterErrorCode,
    detail: String = "",
): String {
    if (code == HotWaterErrorCode.NONE) return ""
    if (code == HotWaterErrorCode.AUTH_FAILED && detail.isNotBlank() && detail.any { it.code > 127 }) {
        return detail
    }
    return stringResource(code.errorStringRes())
}

internal fun HotWaterErrorCode.errorStringRes(): Int =
    when (this) {
        HotWaterErrorCode.NONE -> R.string.hotwater_status_error
        HotWaterErrorCode.NOT_BOUND -> R.string.hotwater_err_not_bound
        HotWaterErrorCode.NO_METER_INFO -> R.string.hotwater_err_no_meter
        HotWaterErrorCode.NO_BLE_PERMISSION -> R.string.hotwater_err_no_ble_perm
        HotWaterErrorCode.BLUETOOTH_DISABLED -> R.string.hotwater_err_bt_disabled
        HotWaterErrorCode.DEVICE_NOT_FOUND -> R.string.hotwater_err_device_not_found
        HotWaterErrorCode.DEVICE_DISABLED -> R.string.hotwater_err_device_disabled
        HotWaterErrorCode.AUTH_FAILED -> R.string.hotwater_err_auth_failed
        HotWaterErrorCode.NETWORK_ERROR -> R.string.hotwater_err_network
        HotWaterErrorCode.BLE_ERROR -> R.string.hotwater_err_ble
        HotWaterErrorCode.BUSY -> R.string.hotwater_err_busy
        HotWaterErrorCode.UNSUPPORTED -> R.string.hotwater_err_unsupported
    }

private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun checkBlePermissions(ctx: Context): Boolean =
    requiredBlePermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
