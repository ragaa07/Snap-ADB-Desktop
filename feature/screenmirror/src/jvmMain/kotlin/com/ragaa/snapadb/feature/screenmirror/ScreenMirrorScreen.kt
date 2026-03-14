package com.ragaa.snapadb.feature.screenmirror

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.feature.screenmirror.model.ScrcpyConfig
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun ScreenMirrorScreen(viewModel: ScreenMirrorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is ScreenMirrorState.NoDevice -> NoDeviceState()
        is ScreenMirrorState.Loading -> LoadingState()
        is ScreenMirrorState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(ScreenMirrorIntent.Retry) })
        is ScreenMirrorState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: ScreenMirrorState.Ready,
    actionResult: ScreenMirrorResult?,
    onIntent: (ScreenMirrorIntent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Screen Mirror", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { onIntent(ScreenMirrorIntent.Retry) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            ScreenshotSection(state, onIntent)
            RecordingSection(state, onIntent)
            ScrcpySection(state, onIntent)
            QuickActionsSection(state, onIntent)
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(ScreenMirrorIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { onIntent(ScreenMirrorIntent.DismissResult) }) {
                        Text("Dismiss")
                    }
                },
                containerColor = when (result) {
                    is ScreenMirrorResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is ScreenMirrorResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is ScreenMirrorResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is ScreenMirrorResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is ScreenMirrorResult.Success -> result.message
                        is ScreenMirrorResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotSection(state: ScreenMirrorState.Ready, onIntent: (ScreenMirrorIntent) -> Unit) {
    SectionCard("Screenshot", Icons.Outlined.CameraAlt) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onIntent(ScreenMirrorIntent.CaptureScreenshot) },
                enabled = !state.isCapturing,
            ) {
                if (state.isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isCapturing) "Capturing..." else "Capture")
            }

            if (state.screenshotBytes != null) {
                OutlinedButton(onClick = {
                    SwingUtilities.invokeLater {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Save Screenshot"
                            fileFilter = FileNameExtensionFilter("PNG Image", "png")
                            selectedFile = File("screenshot_${System.currentTimeMillis()}.png")
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val file = chooser.selectedFile.let {
                                if (!it.name.endsWith(".png")) File(it.absolutePath + ".png") else it
                            }
                            onIntent(ScreenMirrorIntent.SaveScreenshot(file))
                        }
                    }
                }) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        // Preview
        state.screenshotBitmap?.let { bitmap ->
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                bitmap = bitmap,
                contentDescription = "Screenshot preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
private fun RecordingSection(state: ScreenMirrorState.Ready, onIntent: (ScreenMirrorIntent) -> Unit) {
    var timeLimitInput by remember { mutableStateOf("180") }

    SectionCard("Screen Recording", Icons.Outlined.Videocam) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.isRecording) {
                OutlinedTextField(
                    value = timeLimitInput,
                    onValueChange = { timeLimitInput = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.width(120.dp),
                    label = { Text("Time (sec)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Button(
                    onClick = {
                        val limit = timeLimitInput.toIntOrNull()?.coerceIn(1, 180) ?: 180
                        onIntent(ScreenMirrorIntent.StartRecording(timeLimitSecs = limit))
                    },
                ) {
                    Icon(Icons.Outlined.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Record")
                }
            } else {
                // Recording in progress
                Text(
                    text = formatElapsed(state.recordingElapsedSecs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onIntent(ScreenMirrorIntent.StopRecording) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }

        if (state.lastRecordingLocalPath != null && !state.isRecording) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                SwingUtilities.invokeLater {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Save Recording"
                        fileFilter = FileNameExtensionFilter("MP4 Video", "mp4")
                        selectedFile = File("recording_${System.currentTimeMillis()}.mp4")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile.let {
                            if (!it.name.endsWith(".mp4")) File(it.absolutePath + ".mp4") else it
                        }
                        onIntent(ScreenMirrorIntent.SaveRecording(file))
                    }
                }
            }) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Recording")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScrcpySection(state: ScreenMirrorState.Ready, onIntent: (ScreenMirrorIntent) -> Unit) {
    var maxResolution by remember { mutableStateOf("1024") }
    var bitrate by remember { mutableStateOf("8") }
    var maxFps by remember { mutableStateOf("60") }
    var borderless by remember { mutableStateOf(true) }
    var alwaysOnTop by remember { mutableStateOf(false) }
    var showTouches by remember { mutableStateOf(false) }
    var stayAwake by remember { mutableStateOf(true) }
    var turnScreenOff by remember { mutableStateOf(false) }

    SectionCard("scrcpy (Live Mirror)", Icons.AutoMirrored.Outlined.ScreenShare) {
        if (!state.scrcpyAvailable) {
            Text(
                "scrcpy not found. Install it to use live mirroring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (!state.scrcpyRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = maxResolution,
                        onValueChange = { maxResolution = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max Resolution") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = bitrate,
                        onValueChange = { bitrate = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Bitrate (Mbps)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = maxFps,
                        onValueChange = { maxFps = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max FPS") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CheckboxOption("Borderless", borderless) { borderless = it }
                    CheckboxOption("Always on Top", alwaysOnTop) { alwaysOnTop = it }
                    CheckboxOption("Show Touches", showTouches) { showTouches = it }
                    CheckboxOption("Stay Awake", stayAwake) { stayAwake = it }
                    CheckboxOption("Screen Off", turnScreenOff) { turnScreenOff = it }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.scrcpyRunning) {
                    Button(onClick = {
                        val config = ScrcpyConfig(
                            maxResolution = maxResolution.toIntOrNull() ?: 1024,
                            bitrate = (bitrate.toIntOrNull() ?: 8) * 1_000_000,
                            maxFps = maxFps.toIntOrNull() ?: 60,
                            borderless = borderless,
                            alwaysOnTop = alwaysOnTop,
                            showTouches = showTouches,
                            stayAwake = stayAwake,
                            turnScreenOff = turnScreenOff,
                        )
                        onIntent(ScreenMirrorIntent.LaunchScrcpy(config))
                    }) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Launch scrcpy")
                    }
                } else {
                    Button(
                        onClick = { onIntent(ScreenMirrorIntent.StopScrcpy) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop scrcpy")
                    }
                    Text(
                        "scrcpy is running",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsSection(state: ScreenMirrorState.Ready, onIntent: (ScreenMirrorIntent) -> Unit) {
    SectionCard("Quick Actions", Icons.Outlined.ScreenRotation) {
        // Auto-Rotate Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Auto-Rotate", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.autoRotateEnabled,
                onCheckedChange = { onIntent(ScreenMirrorIntent.ToggleAutoRotate(it)) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Rotation buttons
        Text("Manual Rotation", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("0\u00B0" to 0, "90\u00B0" to 1, "180\u00B0" to 2, "270\u00B0" to 3).forEach { (label, rotation) ->
                SuggestionChip(
                    onClick = { onIntent(ScreenMirrorIntent.SetRotation(rotation)) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Stay Awake Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Stay Awake", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Keep screen on while plugged in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.stayAwakeEnabled,
                onCheckedChange = { onIntent(ScreenMirrorIntent.ToggleStayAwake(it)) },
            )
        }
    }
}

// Helper composables

@Composable
private fun CheckboxOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
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
private fun NoDeviceState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.DeviceUnknown, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No device selected", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Connect a device to use screen mirror", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
