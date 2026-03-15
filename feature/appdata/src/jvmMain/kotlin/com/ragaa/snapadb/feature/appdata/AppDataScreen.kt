package com.ragaa.snapadb.feature.appdata

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.SharedPrefEntry
import com.ragaa.snapadb.core.adb.model.SharedPrefType
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun AppDataScreen(viewModel: AppDataViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val suggestions by viewModel.packageSuggestions.collectAsState()

    when (val s = state) {
        is AppDataState.NoDevice -> NoDeviceState("Connect a device to inspect app data")
        is AppDataState.NoApp -> PackageInputScreen(
            suggestions = suggestions,
            onQueryChanged = { viewModel.onIntent(AppDataIntent.UpdatePackageQuery(it)) },
            onPackageSelected = { viewModel.onIntent(AppDataIntent.SetPackageName(it)) },
        )
        is AppDataState.Loading -> LoadingState()
        is AppDataState.Error -> ErrorState(
            message = s.message,
            onRetry = { viewModel.onIntent(AppDataIntent.Refresh) },
        )
        is AppDataState.Loaded -> LoadedContent(
            state = s,
            actionResult = actionResult,
            suggestions = suggestions,
            onQueryChanged = { viewModel.onIntent(AppDataIntent.UpdatePackageQuery(it)) },
            onIntent = viewModel::onIntent,
            onExport = { viewModel.getExportContent() },
            onImport = { viewModel.importXml(it) },
            onReportError = { viewModel.reportError(it) },
            onReportSuccess = { viewModel.reportSuccess(it) },
        )
    }
}

@Composable
private fun PackageInputScreen(
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onPackageSelected: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(400.dp),
        ) {
            Text("SharedPreferences Viewer", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter a debuggable app's package name to inspect its SharedPreferences",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChanged(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("com.example.myapp") },
                    label = { Text("Package Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                DropdownMenu(
                    expanded = suggestions.isNotEmpty(),
                    onDismissRequest = {},
                    modifier = Modifier.width(400.dp),
                ) {
                    suggestions.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                query = pkg
                                onQueryChanged("")
                                onPackageSelected(pkg)
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onPackageSelected(query) },
                enabled = query.isNotBlank(),
            ) {
                Text("Load SharedPreferences")
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: AppDataState.Loaded,
    actionResult: AppDataResult?,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onIntent: (AppDataIntent) -> Unit,
    onExport: () -> String?,
    onImport: (String) -> Unit,
    onReportError: (String) -> Unit,
    onReportSuccess: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            HeaderBar(
                state = state,
                suggestions = suggestions,
                onQueryChanged = onQueryChanged,
                onIntent = onIntent,
                onExport = onExport,
                onImport = onImport,
                onReportError = onReportError,
                onReportSuccess = onReportSuccess,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Content: file list + entries panel
            Row(modifier = Modifier.fillMaxSize()) {
                // Left panel: file list
                FileListPanel(
                    files = state.files,
                    selectedFile = state.selectedFile,
                    onSelectFile = { onIntent(AppDataIntent.SelectFile(it)) },
                    onDeleteFile = { onIntent(AppDataIntent.DeleteFile(it)) },
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )

                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp))

                // Right panel: entries
                if (state.selectedFile == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.files.isEmpty()) "No SharedPreferences files found"
                            else "Select a file to view its entries",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    EntriesPanel(
                        entries = state.displayEntries,
                        isDirty = state.isDirty,
                        onEditEntry = { key, value, type -> onIntent(AppDataIntent.EditEntry(key, value, type)) },
                        onDeleteEntry = { key -> onIntent(AppDataIntent.DeleteEntry(key)) },
                        onAddEntry = { key, value, type -> onIntent(AppDataIntent.AddEntry(key, value, type)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(AppDataIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { onIntent(AppDataIntent.DismissResult) }) { Text("Dismiss") }
                },
                containerColor = when (result) {
                    is AppDataResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is AppDataResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is AppDataResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is AppDataResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is AppDataResult.Success -> result.message
                        is AppDataResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    state: AppDataState.Loaded,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onIntent: (AppDataIntent) -> Unit,
    onExport: () -> String?,
    onImport: (String) -> Unit,
    onReportError: (String) -> Unit,
    onReportSuccess: (String) -> Unit,
) {
    var packageQuery by remember(state.packageName) { mutableStateOf(state.packageName) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SharedPreferences", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.weight(1f))

        // Package switch
        Box {
            OutlinedTextField(
                value = packageQuery,
                onValueChange = {
                    packageQuery = it
                    onQueryChanged(it)
                },
                modifier = Modifier.width(280.dp),
                label = { Text("Package") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(),
                onDismissRequest = { onQueryChanged("") },
                modifier = Modifier.width(280.dp),
            ) {
                suggestions.forEach { pkg ->
                    DropdownMenuItem(
                        text = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            packageQuery = pkg
                            onQueryChanged("")
                            onIntent(AppDataIntent.SetPackageName(pkg))
                        },
                    )
                }
            }
        }

        IconButton(onClick = { onIntent(AppDataIntent.SetPackageName(packageQuery)) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
        }

        if (state.isDirty) {
            Button(onClick = { onIntent(AppDataIntent.SaveChanges) }) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        }

        // Export — Fix #7: Error handling for file I/O
        IconButton(onClick = {
            val content = onExport() ?: return@IconButton
            SwingUtilities.invokeLater {
                try {
                    val chooser = JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("XML files", "xml")
                        selectedFile = java.io.File(state.selectedFile ?: "shared_prefs.xml")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        chooser.selectedFile.writeText(content)
                        onReportSuccess("Exported to ${chooser.selectedFile.name}")
                    }
                } catch (e: Exception) {
                    onReportError("Export failed: ${e.message}")
                }
            }
        }) {
            Icon(Icons.Outlined.Download, contentDescription = "Export", modifier = Modifier.size(20.dp))
        }

        // Import — Fix #7: Error handling for file I/O
        IconButton(onClick = {
            SwingUtilities.invokeLater {
                try {
                    val chooser = JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("XML files", "xml")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val xml = chooser.selectedFile.readText()
                        onImport(xml)
                    }
                } catch (e: Exception) {
                    onReportError("Import failed: ${e.message}")
                }
            }
        }) {
            Icon(Icons.Outlined.Upload, contentDescription = "Import", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FileListPanel(
    files: List<String>,
    selectedFile: String?,
    onSelectFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "Files (${files.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (files.isEmpty()) {
            Text(
                "No shared_prefs files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(files, key = { it }) { fileName ->
                    FileRow(
                        fileName = fileName,
                        isSelected = fileName == selectedFile,
                        onClick = { onSelectFile(fileName) },
                        onDelete = { onDeleteFile(fileName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    fileName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            fileName.removeSuffix(".xml"),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
        IconButton(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete '$fileName'? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onDelete()
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EntriesPanel(
    entries: List<SharedPrefEntry>,
    isDirty: Boolean,
    onEditEntry: (String, String, SharedPrefType) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onAddEntry: (String, String, SharedPrefType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(start = 8.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Entries (${entries.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            if (isDirty) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "unsaved changes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Entry", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Key", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Value", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(64.dp)) // actions
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No entries in this file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.key }) { entry ->
                    EntryRow(
                        entry = entry,
                        onEdit = { newValue -> onEditEntry(entry.key, newValue, entry.type) },
                        onDelete = { onDeleteEntry(entry.key) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { key, value, type ->
                onAddEntry(key, value, type)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun EntryRow(
    entry: SharedPrefEntry,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.key,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
        )
        // Fix #4: Use displayValue which handles STRING_SET properly
        Text(
            entry.displayValue,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        TypeBadge(
            type = entry.type,
            modifier = Modifier.width(80.dp),
        )
        IconButton(
            onClick = { showEditDialog = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showEditDialog) {
        EditEntryDialog(
            entry = entry,
            onDismiss = { showEditDialog = false },
            onSave = { newValue ->
                onEdit(newValue)
                showEditDialog = false
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Entry") },
            text = { Text("Delete key '${entry.key}'?") },
            confirmButton = {
                Button(onClick = {
                    onDelete()
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TypeBadge(type: SharedPrefType, modifier: Modifier = Modifier) {
    val label = when (type) {
        SharedPrefType.STRING -> "String"
        SharedPrefType.INT -> "Int"
        SharedPrefType.LONG -> "Long"
        SharedPrefType.FLOAT -> "Float"
        SharedPrefType.BOOLEAN -> "Bool"
        SharedPrefType.STRING_SET -> "Set"
    }
    val color = when (type) {
        SharedPrefType.STRING -> MaterialTheme.colorScheme.primary
        SharedPrefType.INT, SharedPrefType.LONG -> MaterialTheme.colorScheme.tertiary
        SharedPrefType.FLOAT -> MaterialTheme.colorScheme.secondary
        SharedPrefType.BOOLEAN -> MaterialTheme.colorScheme.error
        SharedPrefType.STRING_SET -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

// Fix #8: Type validation in edit dialog
@Composable
private fun EditEntryDialog(
    entry: SharedPrefEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember {
        mutableStateOf(
            if (entry.type == SharedPrefType.STRING_SET) entry.setValues.joinToString("\n")
            else entry.value
        )
    }
    val validationError = remember(value) { AppDataViewModel.validateValue(value, entry.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit '${entry.key}'") },
        text = {
            Column {
                Text("Type: ${entry.type.name}", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (entry.type == SharedPrefType.STRING_SET) "Values (one per line)"
                            else "Value"
                        )
                    },
                    singleLine = entry.type != SharedPrefType.STRING && entry.type != SharedPrefType.STRING_SET,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(value) },
                enabled = validationError == null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// Fix #8: Type validation in add dialog
@Composable
private fun AddEntryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, SharedPrefType) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SharedPrefType.STRING) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    val validationError = remember(value, selectedType) {
        if (value.isBlank()) null else AppDataViewModel.validateValue(value, selectedType)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (selectedType == SharedPrefType.STRING_SET) "Values (one per line)"
                            else "Value"
                        )
                    },
                    singleLine = selectedType != SharedPrefType.STRING && selectedType != SharedPrefType.STRING_SET,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(12.dp),
                )
                Box {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().clickable { showTypeDropdown = true },
                        label = { Text("Type") },
                        readOnly = true,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false },
                    ) {
                        SharedPrefType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    showTypeDropdown = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(key, value, selectedType) },
                enabled = key.isNotBlank() && validationError == null,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
