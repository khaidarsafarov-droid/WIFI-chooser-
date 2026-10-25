package com.wifipriority.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wifipriority.R
import com.wifipriority.service.PriorityStatus
import com.wifipriority.wifi.WifiTarget
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val form by viewModel.form.collectAsState()
    val status by viewModel.status.collectAsState()
    val warnings by viewModel.warnings.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        StatusCard(status)
        if (warnings.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.warnings_title), style = MaterialTheme.typography.titleMedium)
                    warnings.forEach { warning ->
                        Text(text = warning, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.enable_priority))
            Switch(checked = form.enabled, onCheckedChange = viewModel::onEnabled)
        }
        CredentialFields(
            ssidLabel = stringResource(R.string.phone_ssid),
            passwordLabel = stringResource(R.string.phone_password),
            ssid = form.phoneSsid,
            password = form.phonePassword,
            reveal = form.revealPhonePassword,
            onSsid = viewModel::onPhoneSsid,
            onPassword = viewModel::onPhonePassword,
            onToggleReveal = viewModel::togglePhonePassword,
        )
        CredentialFields(
            ssidLabel = stringResource(R.string.truck_ssid),
            passwordLabel = stringResource(R.string.truck_password),
            ssid = form.truckSsid,
            password = form.truckPassword,
            reveal = form.revealTruckPassword,
            onSsid = viewModel::onTruckSsid,
            onPassword = viewModel::onTruckPassword,
            onToggleReveal = viewModel::toggleTruckPassword,
        )
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.save))
        }
        form.message?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(status: PriorityStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.status_network,
                    status.connectedSsid ?: stringResource(R.string.target_none),
                ),
            )
            Text(
                text = stringResource(
                    R.string.status_target,
                    targetLabel(status.target),
                ),
            )
            Text(
                text = stringResource(
                    R.string.status_switch,
                    formatSwitch(status.lastSwitchTimeMillis),
                ),
            )
        }
    }
}

@Composable
private fun CredentialFields(
    ssidLabel: String,
    passwordLabel: String,
    ssid: String,
    password: String,
    reveal: Boolean,
    onSsid: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleReveal: () -> Unit,
) {
    OutlinedTextField(
        value = ssid,
        onValueChange = onSsid,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(ssidLabel) },
        singleLine = true,
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(passwordLabel) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = onToggleReveal) {
                Text(text = stringResource(if (reveal) R.string.hide_password else R.string.show_password))
            }
        },
    )
}

@Composable
private fun targetLabel(target: WifiTarget): String = when (target) {
    WifiTarget.PHONE -> stringResource(R.string.target_phone)
    WifiTarget.TRUCK -> stringResource(R.string.target_truck)
    WifiTarget.NONE -> stringResource(R.string.target_none)
}

private fun formatSwitch(millis: Long?): String {
    if (millis == null) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
