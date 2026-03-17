package com.ragaa.snapadb.feature.devoptions

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import com.ragaa.snapadb.core.ui.components.SectionCard
import com.ragaa.snapadb.core.ui.components.normalizeScale
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsIntent
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsResult
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsState
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevOptionsScreen(viewModel: DevOptionsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is DevOptionsState.NoDevice -> NoDeviceState("Connect a device to manage developer options")
        is DevOptionsState.Loading -> LoadingState()
        is DevOptionsState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(DevOptionsIntent.Refresh) })
        is DevOptionsState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: DevOptionsState.Ready,
    actionResult: DevOptionsResult?,
    onIntent: (DevOptionsIntent) -> Unit,
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
                Text("Developer Options", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { onIntent(DevOptionsIntent.Refresh) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            DebugOverlaysSection(state, onIntent)
            RenderingSection(state, onIntent)
            AnimationScalesSection(state, onIntent)
            DisplaySection(state, onIntent)
            MultiWindowSection(state, onIntent)
            ProcessManagementSection(state, onIntent)
            NetworkSection(state, onIntent)
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(DevOptionsIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(DevOptionsIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is DevOptionsResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is DevOptionsResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is DevOptionsResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is DevOptionsResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is DevOptionsResult.Success -> result.message
                        is DevOptionsResult.Failure -> result.message
                    },
                )
            }
        }
    }
}

// region Debug Overlays

@Composable
private fun DebugOverlaysSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    SectionCard("Debug Overlays", Icons.Outlined.Layers) {
        ToggleRow("Show Layout Bounds", state.showLayoutBounds) {
            onIntent(DevOptionsIntent.ToggleLayoutBounds(it))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow("GPU Overdraw", state.gpuOverdraw) {
            onIntent(DevOptionsIntent.ToggleGpuOverdraw(it))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow("Show Touches", state.showTouches) {
            onIntent(DevOptionsIntent.ToggleShowTouches(it))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow("Pointer Location", state.pointerLocation) {
            onIntent(DevOptionsIntent.TogglePointerLocation(it))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow("Strict Mode Visual", state.strictModeVisual) {
            onIntent(DevOptionsIntent.ToggleStrictMode(it))
        }
    }
}

// endregion

// region Rendering

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderingSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    val gpuModes = listOf(
        "false" to "Off",
        "visual_bars" to "Visual Bars",
    )

    SectionCard("Rendering", Icons.Outlined.DisplaySettings) {
        Text("Profile GPU Rendering", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gpuModes.forEach { (value, label) ->
                FilterChip(
                    selected = state.profileGpuRendering == value,
                    onClick = { onIntent(DevOptionsIntent.SetProfileGpuRendering(value)) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ToggleRow("Force 4x MSAA", state.force4xMsaa) {
            onIntent(DevOptionsIntent.ToggleForce4xMsaa(it))
        }
    }
}

// endregion

// region Animation Scales

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationScalesSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    val scalePresets = listOf("0" to "Off", "0.5" to "0.5x", "1.0" to "1x", "2.0" to "2x", "5.0" to "5x", "10.0" to "10x")

    SectionCard("Animation Scales", Icons.Outlined.Animation) {
        AnimationScaleRow("Window Animation", state.windowAnimationScale, scalePresets) {
            onIntent(DevOptionsIntent.SetWindowAnimationScale(it))
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimationScaleRow("Transition Animation", state.transitionAnimationScale, scalePresets) {
            onIntent(DevOptionsIntent.SetTransitionAnimationScale(it))
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimationScaleRow("Animator Duration", state.animatorDurationScale, scalePresets) {
            onIntent(DevOptionsIntent.SetAnimatorDurationScale(it))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationScaleRow(
    label: String,
    currentScale: String,
    presets: List<Pair<String, String>>,
    onScaleSelected: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (value, text) ->
            val isSelected = normalizeScale(currentScale) == normalizeScale(value)
            FilterChip(
                selected = isSelected,
                onClick = { onScaleSelected(value) },
                label = { Text(text, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// endregion

// region Display

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    val stayAwakeModes = listOf(
        0 to "Off",
        1 to "USB",
        2 to "AC",
        3 to "USB + AC",
        4 to "Wireless",
        7 to "All sources",
    )
    val colorSpaceModes = listOf(
        -1 to "Disabled",
        0 to "Monochromacy",
        11 to "Deuteranomaly (red-green)",
        12 to "Protanomaly (red-green)",
        13 to "Tritanomaly (blue-yellow)",
    )
    var stayAwakeExpanded by remember { mutableStateOf(false) }
    var colorSpaceExpanded by remember { mutableStateOf(false) }
    var peakInput by remember(state.peakRefreshRate) { mutableStateOf(state.peakRefreshRate) }
    var minInput by remember(state.minRefreshRate) { mutableStateOf(state.minRefreshRate) }

    SectionCard("Display", Icons.Outlined.ScreenRotation) {
        // Stay Awake
        Text("Stay Awake While Charging", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = stayAwakeExpanded,
            onExpandedChange = { stayAwakeExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = stayAwakeModes.find { it.first == state.stayAwake }?.second ?: "Off",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stayAwakeExpanded) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = stayAwakeExpanded, onDismissRequest = { stayAwakeExpanded = false }) {
                stayAwakeModes.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntent(DevOptionsIntent.SetStayAwake(value))
                            stayAwakeExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Force RTL
        ToggleRow("Force RTL Layout", state.forceRtl) {
            onIntent(DevOptionsIntent.ToggleForceRtl(it))
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Simulate Color Space
        Text("Simulate Color Space", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = colorSpaceExpanded,
            onExpandedChange = { colorSpaceExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = colorSpaceModes.find { it.first == state.simulateColorSpace }?.second ?: "Disabled",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(colorSpaceExpanded) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = colorSpaceExpanded, onDismissRequest = { colorSpaceExpanded = false }) {
                colorSpaceModes.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntent(DevOptionsIntent.SetSimulateColorSpace(value))
                            colorSpaceExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Refresh Rate
        Text("Refresh Rate (Hz)", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = peakInput,
                onValueChange = { peakInput = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Peak") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = minInput,
                onValueChange = { minInput = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Min") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = {
                    if (peakInput.isNotBlank()) onIntent(DevOptionsIntent.SetPeakRefreshRate(peakInput))
                    if (minInput.isNotBlank()) onIntent(DevOptionsIntent.SetMinRefreshRate(minInput))
                },
                enabled = peakInput.isNotBlank() || minInput.isNotBlank(),
            ) { Text("Set") }
        }
    }
}

// endregion

// region Multi-window

@Composable
private fun MultiWindowSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    SectionCard("Multi-window", Icons.Outlined.Window) {
        ToggleRow("Force Activities Resizable", state.forceResizableActivities) {
            onIntent(DevOptionsIntent.ToggleForceResizable(it))
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow("Enable Freeform Windows", state.freeformWindows) {
            onIntent(DevOptionsIntent.ToggleFreeformWindows(it))
        }
    }
}

// endregion

// region Process Management

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessManagementSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    val processLimits = listOf(
        -1 to "Standard",
        0 to "No background processes",
        1 to "At most 1 process",
        2 to "At most 2 processes",
        3 to "At most 3 processes",
        4 to "At most 4 processes",
    )
    var expanded by remember { mutableStateOf(false) }

    SectionCard("Process Management", Icons.Outlined.Tune) {
        Text("Background Process Limit", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = processLimits.find { it.first == state.backgroundProcessLimit }?.second ?: "Standard",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                processLimits.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntent(DevOptionsIntent.SetBackgroundProcessLimit(value))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// endregion

// region Network

@Composable
private fun NetworkSection(
    state: DevOptionsState.Ready,
    onIntent: (DevOptionsIntent) -> Unit,
) {
    SectionCard("Network", Icons.Outlined.Wifi) {
        ToggleRow("WiFi Verbose Logging", state.wifiVerboseLogging) {
            onIntent(DevOptionsIntent.ToggleWifiVerboseLogging(it))
        }
    }
}

// endregion

// region Common components

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// endregion
