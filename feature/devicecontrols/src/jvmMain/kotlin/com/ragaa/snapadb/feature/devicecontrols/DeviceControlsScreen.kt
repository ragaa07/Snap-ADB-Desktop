package com.ragaa.snapadb.feature.devicecontrols

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.feature.devicecontrols.model.KeyEvent
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeviceControlsScreen(viewModel: DeviceControlsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is DeviceControlsState.NoDevice -> NoDeviceState("Connect a device to use controls")
        is DeviceControlsState.Loading -> LoadingState()
        is DeviceControlsState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(DeviceControlsIntent.RefreshDisplay) })
        is DeviceControlsState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: DeviceControlsState.Ready,
    actionResult: DeviceControlsResult?,
    onIntent: (DeviceControlsIntent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Device Controls", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { onIntent(DeviceControlsIntent.RefreshDisplay) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            InputSection(onIntent)
            DisplaySection(state, onIntent)
            DeveloperOptionsSection(state, onIntent)
            NetworkSection(onIntent)
            SystemSection(onIntent)
            SettingsSection(onIntent)
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(DeviceControlsIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(DeviceControlsIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is DeviceControlsResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is DeviceControlsResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is DeviceControlsResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is DeviceControlsResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is DeviceControlsResult.Success -> result.message
                        is DeviceControlsResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputSection(onIntent: (DeviceControlsIntent) -> Unit) {
    var textInput by remember { mutableStateOf("") }
    var tapX by remember { mutableStateOf("") }
    var tapY by remember { mutableStateOf("") }

    SectionCard("Input", Icons.Outlined.Keyboard) {
        // Key events
        Text("Key Events", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyEvent.entries.forEach { key ->
                SuggestionChip(
                    onClick = { onIntent(DeviceControlsIntent.SendKey(key.code)) },
                    label = { Text(key.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Text input
        Text("Text Input", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type text to send...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = {
                    if (textInput.isNotEmpty()) {
                        onIntent(DeviceControlsIntent.SendText(textInput))
                        textInput = ""
                    }
                },
                enabled = textInput.isNotEmpty(),
            ) { Text("Send") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Tap
        Text("Tap", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = tapX,
                onValueChange = { tapX = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("X") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = tapY,
                onValueChange = { tapY = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Y") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = {
                    val x = tapX.toIntOrNull() ?: return@Button
                    val y = tapY.toIntOrNull() ?: return@Button
                    onIntent(DeviceControlsIntent.SendTap(x, y))
                },
                enabled = tapX.toIntOrNull() != null && tapY.toIntOrNull() != null,
            ) {
                Icon(Icons.Outlined.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tap")
            }
        }
    }
}

@Composable
private fun DisplaySection(state: DeviceControlsState.Ready, onIntent: (DeviceControlsIntent) -> Unit) {
    var dpiInput by remember { mutableStateOf("") }
    var widthInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }

    SectionCard("Display", Icons.Outlined.ScreenshotMonitor) {
        Text("Current: ${state.currentDensity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Current: ${state.currentSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        // Density
        Text("Screen Density (DPI)", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = dpiInput,
                onValueChange = { dpiInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g., 420") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = { dpiInput.toIntOrNull()?.let { onIntent(DeviceControlsIntent.SetDensity(it)) } },
                enabled = dpiInput.toIntOrNull()?.let { it in 100..800 } == true,
            ) { Text("Set") }
            OutlinedButton(onClick = { onIntent(DeviceControlsIntent.ResetDensity) }) { Text("Reset") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Size
        Text("Screen Size Override", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = widthInput,
                onValueChange = { widthInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Width") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = heightInput,
                onValueChange = { heightInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Height") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = {
                    val w = widthInput.toIntOrNull() ?: return@Button
                    val h = heightInput.toIntOrNull() ?: return@Button
                    onIntent(DeviceControlsIntent.SetScreenSize(w, h))
                },
                enabled = widthInput.toIntOrNull()?.let { it in 100..10000 } == true &&
                    heightInput.toIntOrNull()?.let { it in 100..10000 } == true,
            ) { Text("Set") }
            OutlinedButton(onClick = { onIntent(DeviceControlsIntent.ResetScreenSize) }) { Text("Reset") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperOptionsSection(
    state: DeviceControlsState.Ready,
    onIntent: (DeviceControlsIntent) -> Unit,
) {
    val animationScales = listOf("0" to "Off", "0.5" to "0.5x", "1.0" to "1x", "2.0" to "2x", "5.0" to "5x", "10.0" to "10x")
    val fontScales = listOf("0.85" to "0.85x", "1.0" to "1.0x", "1.15" to "1.15x", "1.3" to "1.3x", "1.5" to "1.5x", "2.0" to "2.0x")
    val commonLocales = listOf(
        "" to "Select locale...",
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "ar-EG" to "Arabic (Egypt)",
        "ar-SA" to "Arabic (Saudi)",
        "fr-FR" to "French",
        "de-DE" to "German",
        "es-ES" to "Spanish",
        "ja-JP" to "Japanese",
        "ko-KR" to "Korean",
        "zh-CN" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "pt-BR" to "Portuguese (Brazil)",
        "hi-IN" to "Hindi",
        "tr-TR" to "Turkish",
        "ru-RU" to "Russian",
    )
    var localeExpanded by remember { mutableStateOf(false) }
    var customLocale by remember { mutableStateOf("") }

    SectionCard("Developer Options", Icons.Outlined.Code) {
        // Animation Speed
        Text("Animation Speed", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            animationScales.forEach { (value, label) ->
                val isSelected = normalizeScale(state.animationScale) == normalizeScale(value)
                FilterChip(
                    selected = isSelected,
                    onClick = { onIntent(DeviceControlsIntent.SetAnimationSpeed(value)) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Dark Mode
        Text("Dark Mode", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.darkMode == false,
                onClick = { onIntent(DeviceControlsIntent.ToggleDarkMode(false)) },
                label = { Text("Light") },
                leadingIcon = { Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = state.darkMode == true,
                onClick = { onIntent(DeviceControlsIntent.ToggleDarkMode(true)) },
                label = { Text("Dark") },
                leadingIcon = { Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Don't Keep Activities
        Text("Don't Keep Activities", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.dontKeepActivities,
                onClick = { onIntent(DeviceControlsIntent.ToggleDontKeepActivities(false)) },
                label = { Text("Disabled") },
            )
            FilterChip(
                selected = state.dontKeepActivities,
                onClick = { onIntent(DeviceControlsIntent.ToggleDontKeepActivities(true)) },
                label = { Text("Enabled") },
                colors = if (state.dontKeepActivities) FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                ) else FilterChipDefaults.filterChipColors(),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Font Scale
        Text("Font Scale", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fontScales.forEach { (value, label) ->
                val isSelected = normalizeScale(state.fontScale) == normalizeScale(value)
                FilterChip(
                    selected = isSelected,
                    onClick = { onIntent(DeviceControlsIntent.SetFontScale(value)) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Locale
        Text("Locale", style = MaterialTheme.typography.titleSmall)
        if (state.locale.isNotBlank()) {
            Text(
                "Current: ${state.locale}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = localeExpanded,
                onExpandedChange = { localeExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = commonLocales.find { it.first == customLocale }?.second ?: customLocale.ifBlank { "Select locale..." },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(localeExpanded) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                ExposedDropdownMenu(expanded = localeExpanded, onDismissRequest = { localeExpanded = false }) {
                    commonLocales.drop(1).forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text("$label ($code)") },
                            onClick = {
                                customLocale = code
                                localeExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = customLocale,
                onValueChange = { customLocale = it },
                modifier = Modifier.width(120.dp),
                placeholder = { Text("e.g. en-US") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = {
                    if (customLocale.isNotBlank()) {
                        onIntent(DeviceControlsIntent.SetLocale(customLocale))
                    }
                },
                enabled = customLocale.isNotBlank(),
            ) { Text("Set") }
        }
    }
}

private fun normalizeScale(value: String): String {
    val f = value.toFloatOrNull() ?: return value
    return if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()
}

@Composable
private fun NetworkSection(onIntent: (DeviceControlsIntent) -> Unit) {
    SectionCard("Network", Icons.Outlined.NetworkWifi) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionButton(
                label = "WiFi On",
                icon = Icons.Outlined.NetworkWifi,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleWifi(true)) },
            )
            ActionButton(
                label = "WiFi Off",
                icon = Icons.Outlined.NetworkWifi,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleWifi(false)) },
            )
            ActionButton(
                label = "Data On",
                icon = Icons.Outlined.SignalCellularAlt,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleMobileData(true)) },
            )
            ActionButton(
                label = "Data Off",
                icon = Icons.Outlined.SignalCellularAlt,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleMobileData(false)) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionButton(
                label = "Airplane On",
                icon = Icons.Outlined.AirplanemodeActive,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleAirplaneMode(true)) },
            )
            ActionButton(
                label = "Airplane Off",
                icon = Icons.Outlined.AirplanemodeActive,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceControlsIntent.ToggleAirplaneMode(false)) },
            )
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun SystemSection(onIntent: (DeviceControlsIntent) -> Unit) {
    var showRebootConfirmation by remember { mutableStateOf<String?>(null) }

    SectionCard("System", Icons.Outlined.RestartAlt) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { showRebootConfirmation = "normal" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reboot")
            }
            OutlinedButton(
                onClick = { showRebootConfirmation = "bootloader" },
                modifier = Modifier.weight(1f),
            ) { Text("Bootloader") }
            OutlinedButton(
                onClick = { showRebootConfirmation = "recovery" },
                modifier = Modifier.weight(1f),
            ) { Text("Recovery") }
        }
    }

    showRebootConfirmation?.let { mode ->
        val modeLabel = if (mode == "normal") "reboot" else "reboot to $mode"
        AlertDialog(
            onDismissRequest = { showRebootConfirmation = null },
            title = { Text("Confirm Reboot") },
            text = { Text("Are you sure you want to $modeLabel the device?") },
            confirmButton = {
                Button(
                    onClick = {
                        onIntent(DeviceControlsIntent.Reboot(if (mode == "normal") null else mode))
                        showRebootConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Reboot") }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirmation = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(onIntent: (DeviceControlsIntent) -> Unit) {
    var namespace by remember { mutableStateOf("global") }
    var settingKey by remember { mutableStateOf("") }
    var settingValue by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val namespaces = listOf("system", "secure", "global")

    SectionCard("Settings", Icons.Outlined.Settings) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.width(140.dp),
            ) {
                OutlinedTextField(
                    value = namespace,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    namespaces.forEach { ns ->
                        DropdownMenuItem(
                            text = { Text(ns) },
                            onClick = { namespace = ns; expanded = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = settingKey,
                onValueChange = { settingKey = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Key") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = settingValue,
                onValueChange = { settingValue = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Value (for put)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onIntent(DeviceControlsIntent.GetSettingValue(namespace, settingKey)) },
                enabled = settingKey.isNotBlank(),
            ) { Text("Get") }
            Button(
                onClick = { onIntent(DeviceControlsIntent.PutSettingValue(namespace, settingKey, settingValue)) },
                enabled = settingKey.isNotBlank() && settingValue.isNotBlank(),
            ) { Text("Put") }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

