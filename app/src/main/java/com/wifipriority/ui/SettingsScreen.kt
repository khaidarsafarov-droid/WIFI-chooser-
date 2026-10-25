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

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onFix: (BannerId) -> Unit,
) {
    val form by viewModel.form.collectAsState()
    val banners by viewModel.banners.collectAsState()
    val formBanners = passphraseBanners(form.phonePassword, form.truckPassword)
    val shown = (banners.filter { it != BannerId.PHONE_PASSPHRASE && it != BannerId.TRUCK_PASSPHRASE } + formBanners)
        .distinct()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.back))
            }
            Text(text = stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        }
        BannerList(banners = shown, onFix = onFix, onDismissSamsung = viewModel::dismissSamsungHint)
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
