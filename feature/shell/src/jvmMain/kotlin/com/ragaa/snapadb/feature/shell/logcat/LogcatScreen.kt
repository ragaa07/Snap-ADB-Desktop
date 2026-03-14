package com.ragaa.snapadb.feature.shell.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ragaa.snapadb.core.adb.model.LogLevel
import com.ragaa.snapadb.core.adb.model.LogcatEntry
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import java.io.File
import javax.swing.JFileChooser

@Composable
fun LogcatScreen(viewModel: LogcatViewModel) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is LogcatState.NoDevice -> NoDeviceState()
        is LogcatState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(LogcatIntent.Retry) })
        is LogcatState.Streaming -> StreamingContent(
            state = s,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun StreamingContent(
    state: LogcatState.Streaming,
    onIntent: (LogcatIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.entries.size, state.isPaused) {
        if (!state.isPaused && state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onIntent(LogcatIntent.TogglePause) }) {
                Icon(
                    if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                )
            }
            IconButton(onClick = { onIntent(LogcatIntent.ClearLog) }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear")
            }
            IconButton(onClick = {
                javax.swing.SwingUtilities.invokeLater {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Export logcat"
                        selectedFile = File("logcat_export.txt")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onIntent(LogcatIntent.ExportToFile(chooser.selectedFile))
                    }
                }
            }) {
                Icon(Icons.Outlined.SaveAs, contentDescription = "Export")
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Save filter
            IconButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Outlined.Save, contentDescription = "Save filter")
            }

            // Load filter
            Box {
                TextButton(onClick = { showLoadMenu = true }) {
                    Text("Presets")
                }
                DropdownMenu(expanded = showLoadMenu, onDismissRequest = { showLoadMenu = false }) {
                    if (state.savedFilters.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No saved filters") },
                            onClick = { showLoadMenu = false },
                            enabled = false,
                        )
                    }
                    state.savedFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(filter.name)
                                    IconButton(
                                        onClick = {
                                            onIntent(LogcatIntent.DeleteFilter(filter.id))
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onIntent(LogcatIntent.LoadFilter(filter.id))
                                showLoadMenu = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "${state.entries.size} / ${state.totalCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Level chips
            LogLevel.entries.filter { it != LogLevel.SILENT }.forEach { level ->
                FilterChip(
                    selected = state.minLevel == level,
                    onClick = { onIntent(LogcatIntent.SetMinLevel(level)) },
                    label = { Text(level.label, fontSize = 12.sp) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTextField(
                value = state.tagFilter,
                onValueChange = { onIntent(LogcatIntent.SetTagFilter(it)) },
                placeholder = "Tag",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = state.packageFilter,
                onValueChange = { onIntent(LogcatIntent.SetPackageFilter(it)) },
                placeholder = "Package/PID",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = state.searchText,
                onValueChange = { onIntent(LogcatIntent.SetSearchText(it)) },
                placeholder = "Search",
                modifier = Modifier.weight(1f),
            )
        }

        // Log output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(8.dp),
                )
                .padding(8.dp),
        ) {
            itemsIndexed(state.entries, key = { index, _ -> index }) { _, entry ->
                LogcatRow(entry)
            }
        }
    }

    if (showSaveDialog) {
        SaveFilterDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onIntent(LogcatIntent.SaveFilter(name))
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun LogcatRow(entry: LogcatEntry) {
    val colors = SnapAdbTheme.colors
    val levelColor = when (entry.level) {
        LogLevel.VERBOSE -> colors.logVerbose
        LogLevel.DEBUG -> colors.logDebug
        LogLevel.INFO -> colors.logInfo
        LogLevel.WARN -> colors.logWarn
        LogLevel.ERROR -> colors.logError
        LogLevel.FATAL -> colors.logFatal
        LogLevel.SILENT -> colors.logVerbose
    }

    Text(
        text = "${entry.timestamp} ${entry.level.label}/${entry.tag}(${entry.pid}): ${entry.message}",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = levelColor,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 0.5.dp),
        maxLines = 3,
    )
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(40.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        singleLine = true,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
}

@Composable
private fun SaveFilterDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Filter Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Filter name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NoDeviceState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.DeviceUnknown,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No device selected", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connect a device to view logcat",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Logcat stream error", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
