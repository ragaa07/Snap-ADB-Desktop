package com.ragaa.snapadb.feature.bugreporter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import com.ragaa.snapadb.feature.bugreporter.model.BugReport
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image as SkiaImage
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun BugReporterScreen(viewModel: BugReporterViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val suggestions by viewModel.packageSuggestions.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is BugReporterState.NoDevice -> NoDeviceState("Connect a device to create bug reports")
            is BugReporterState.Ready -> ReadyContent(
                key = s,
                suggestions = suggestions,
                onQueryChanged = { viewModel.onIntent(BugReporterIntent.UpdatePackageQuery(it)) },
                onPackageSelected = { viewModel.onIntent(BugReporterIntent.SetAppPackage(it)) },
                onCapture = { viewModel.onIntent(BugReporterIntent.CaptureReport) },
            )
            is BugReporterState.Capturing -> CapturingContent(s.status)
            is BugReporterState.Editing -> EditingContent(
                report = s.report,
                onUpdateTitle = { viewModel.onIntent(BugReporterIntent.UpdateTitle(it)) },
                onUpdateDescription = { viewModel.onIntent(BugReporterIntent.UpdateDescription(it)) },
                onUpdateReproSteps = { viewModel.onIntent(BugReporterIntent.UpdateReproSteps(it)) },
                onExportZip = { viewModel.onIntent(BugReporterIntent.ExportZip(it)) },
                onCopyMarkdown = { viewModel.onIntent(BugReporterIntent.CopyMarkdown) },
                onNewReport = { viewModel.onIntent(BugReporterIntent.Reset) },
            )
            is BugReporterState.Error -> ErrorState(
                message = s.message,
                onRetry = { viewModel.onIntent(BugReporterIntent.CaptureReport) },
            )
        }

        // Snackbar with auto-dismiss
        val result = actionResult
        if (result != null) {
            LaunchedEffect(result) {
                delay(4000)
                viewModel.onIntent(BugReporterIntent.DismissResult)
            }

            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onIntent(BugReporterIntent.DismissResult) }) {
                        Text("Dismiss")
                    }
                },
                containerColor = when (result) {
                    is BugReporterResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is BugReporterResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is BugReporterResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is BugReporterResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is BugReporterResult.Success -> result.message
                        is BugReporterResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    key: Any,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onPackageSelected: (String) -> Unit,
    onCapture: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).width(400.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Summarize,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Bug Reporter", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Capture screenshot, logcat, and device info in one click",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Optional package field — keyed on state identity to reset on device switch
            var packageQuery by remember(key) { mutableStateOf("") }
            Box {
                OutlinedTextField(
                    value = packageQuery,
                    onValueChange = {
                        packageQuery = it
                        onQueryChanged(it)
                    },
                    label = { Text("App Package (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = suggestions.isNotEmpty(),
                    onDismissRequest = { },
                ) {
                    suggestions.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                packageQuery = pkg
                                onPackageSelected(pkg)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (packageQuery.isNotBlank()) onPackageSelected(packageQuery)
                    onCapture()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capture Report")
            }
        }
    }
}

@Composable
private fun CapturingContent(status: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EditingContent(
    report: BugReport,
    onUpdateTitle: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onUpdateReproSteps: (String) -> Unit,
    onExportZip: (File) -> Unit,
    onCopyMarkdown: () -> Unit,
    onNewReport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit Report", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(onClick = onNewReport) {
                Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Report")
            }
            OutlinedButton(onClick = onCopyMarkdown) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Markdown")
            }
            Button(onClick = {
                SwingUtilities.invokeLater {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Export Bug Report"
                        fileFilter = FileNameExtensionFilter("ZIP files", "zip")
                        selectedFile = File("bug-report.zip")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        var file = chooser.selectedFile
                        if (!file.name.endsWith(".zip")) file = File(file.absolutePath + ".zip")
                        onExportZip(file)
                    }
                }
            }) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export ZIP")
            }
        }

        HorizontalDivider()

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            OutlinedTextField(
                value = report.title,
                onValueChange = onUpdateTitle,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Description
            OutlinedTextField(
                value = report.description,
                onValueChange = onUpdateDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                minLines = 3,
            )

            // Repro steps
            OutlinedTextField(
                value = report.reproSteps,
                onValueChange = onUpdateReproSteps,
                label = { Text("Reproduction Steps") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )

            // Collapsible: Device Info
            CollapsibleSection("Device Info") {
                InfoTable(
                    listOf(
                        "Model" to report.deviceInfo.model,
                        "Manufacturer" to report.deviceInfo.manufacturer,
                        "Android" to report.deviceInfo.androidVersion,
                        "SDK" to report.deviceInfo.sdkVersion,
                        "Build" to report.deviceInfo.buildId,
                        "Serial" to report.deviceInfo.serial,
                    )
                )
            }

            // Collapsible: App Info (if present)
            if (report.appInfo != null) {
                CollapsibleSection("App Info") {
                    InfoTable(
                        listOf(
                            "Package" to report.appInfo.packageName,
                            "Version" to "${report.appInfo.versionName} (${report.appInfo.versionCode})",
                            "APK Path" to report.appInfo.apkPath,
                            "System App" to "${report.appInfo.isSystemApp}",
                        )
                    )
                }
            }

            // Collapsible: Battery
            if (report.batteryInfo != null) {
                CollapsibleSection("Battery") {
                    InfoTable(
                        listOf(
                            "Level" to "${report.batteryInfo.level}%",
                            "Status" to "${report.batteryInfo.status}",
                            "Health" to report.batteryInfo.health,
                            "Temperature" to "${"%.1f".format(report.batteryInfo.temperature)}°C",
                        )
                    )
                }
            }

            // Collapsible: Memory
            if (report.memoryInfo != null) {
                CollapsibleSection("Memory") {
                    InfoTable(
                        listOf(
                            "Total" to report.memoryInfo.totalMb(),
                            "Used" to report.memoryInfo.usedMb(),
                            "Available" to report.memoryInfo.availableMb(),
                            "Usage" to "${report.memoryInfo.usagePercent}%",
                        )
                    )
                }
            }

            // Collapsible: Screenshot preview
            if (report.screenshot != null) {
                CollapsibleSection("Screenshot") {
                    val bitmap = remember(report.screenshot) {
                        try {
                            SkiaImage.makeFromEncoded(report.screenshot.bytes).toComposeImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Screenshot",
                            modifier = Modifier.heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text("Failed to decode screenshot", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Collapsible: Logcat preview
            if (report.logcat.isNotBlank()) {
                val logcatLines = remember(report.logcat) { report.logcat.lines() }
                CollapsibleSection("Logcat (${logcatLines.size} lines)") {
                    val previewLines = remember(logcatLines) { logcatLines.takeLast(50) }
                    Text(
                        text = previewLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                            .heightIn(max = 300.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoTable(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
