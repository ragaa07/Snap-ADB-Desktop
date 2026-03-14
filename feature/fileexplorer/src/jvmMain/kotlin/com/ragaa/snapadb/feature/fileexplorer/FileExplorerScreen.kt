package com.ragaa.snapadb.feature.fileexplorer

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.FileEntry
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun FileExplorerScreen(viewModel: FileExplorerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    when (val s = state) {
        is FileExplorerState.NoDevice -> NoDeviceState()
        is FileExplorerState.Loading -> LoadingState(s.path)
        is FileExplorerState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(FileExplorerIntent.Refresh) })
        is FileExplorerState.Loaded -> LoadedContent(s, actionResult, searchQuery, viewModel)
    }
}

@Composable
private fun LoadedContent(
    state: FileExplorerState.Loaded,
    actionResult: FileActionResult?,
    searchQuery: String,
    viewModel: FileExplorerViewModel,
) {
    var showMkdirDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }

    val filteredFiles = remember(state.files, searchQuery) {
        if (searchQuery.isBlank()) state.files
        else state.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("File Explorer", style = MaterialTheme.typography.headlineMedium)
                Row {
                    IconButton(onClick = { viewModel.onIntent(FileExplorerIntent.NavigateUp) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Up")
                    }
                    IconButton(onClick = { showPushDialog(viewModel::onIntent) }) {
                        Icon(Icons.Outlined.Upload, contentDescription = "Push file")
                    }
                    IconButton(onClick = { showMkdirDialog = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = { viewModel.onIntent(FileExplorerIntent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumbs
            BreadcrumbBar(state.breadcrumbs, viewModel::onIntent)
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onIntent(FileExplorerIntent.SetSearchQuery(it)) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                placeholder = { Text("Search files...", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onIntent(FileExplorerIntent.SetSearchQuery("")) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // File list
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No files matching \"$searchQuery\"" else "Empty directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredFiles, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            currentPath = state.currentPath,
                            onNavigate = { path -> viewModel.onIntent(FileExplorerIntent.NavigateTo(path)) },
                            onPull = { showSaveDialog(file, viewModel::onIntent) },
                            onDelete = { deleteTarget = file },
                            resolveSymlink = { linkedTo ->
                                viewModel.resolveSymlinkTarget(state.currentPath, linkedTo)
                            },
                        )
                    }
                }
            }
        }

        // Action result snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                viewModel.onIntent(FileExplorerIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onIntent(FileExplorerIntent.DismissResult) }) {
                        Text("Dismiss")
                    }
                },
                containerColor = when (result) {
                    is FileActionResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is FileActionResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is FileActionResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is FileActionResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is FileActionResult.Success -> result.message
                        is FileActionResult.Failure -> result.message
                    }
                )
            }
        }

        // Mkdir dialog
        if (showMkdirDialog) {
            MkdirDialog(
                onConfirm = { name ->
                    showMkdirDialog = false
                    viewModel.onIntent(FileExplorerIntent.MakeDirectory(name))
                },
                onDismiss = { showMkdirDialog = false },
            )
        }

        // Delete confirmation dialog
        deleteTarget?.let { file ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete") },
                text = {
                    Text("Are you sure you want to delete \"${file.name}\"?" +
                            if (file.isDirectory) " This will delete all contents." else "")
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        viewModel.onIntent(FileExplorerIntent.Delete(file.path))
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(breadcrumbs: List<Breadcrumb>, onIntent: (FileExplorerIntent) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(breadcrumbs, key = { _, b -> b.path }) { index, crumb ->
            if (index > 0) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onIntent(FileExplorerIntent.NavigateToBreadcrumb(crumb.path)) }) {
                Text(
                    crumb.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: FileEntry,
    currentPath: String,
    onNavigate: (String) -> Unit,
    onPull: () -> Unit,
    onDelete: () -> Unit,
    resolveSymlink: (String) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (file.isDirectory) {
                    onNavigate(file.path)
                } else if (file.isSymlink && file.linkedTo.isNotEmpty()) {
                    onNavigate(resolveSymlink(file.linkedTo))
                }
            }
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                file.isSymlink -> Icons.Outlined.Link
                file.isDirectory -> Icons.Outlined.Folder
                else -> Icons.Outlined.Description
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                file.isDirectory -> MaterialTheme.colorScheme.primary
                file.isSymlink -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.displaySize.isNotEmpty()) {
                    Text(
                        file.displaySize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    file.permissions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (file.lastModified.isNotEmpty()) {
                    Text(
                        file.lastModified,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!file.isDirectory) {
            IconButton(onClick = onPull) {
                Icon(Icons.Outlined.Download, contentDescription = "Pull", modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MkdirDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Folder name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun showSaveDialog(file: FileEntry, onIntent: (FileExplorerIntent) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save ${file.name}"
            selectedFile = java.io.File(file.name)
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            onIntent(FileExplorerIntent.PullFile(file.path, chooser.selectedFile.absolutePath))
        }
    }
}

private fun showPushDialog(onIntent: (FileExplorerIntent) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select file to push"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onIntent(FileExplorerIntent.PushFile(chooser.selectedFile.absolutePath))
        }
    }
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Connect a device to browse files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState(path: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Loading $path",
                style = MaterialTheme.typography.bodySmall,
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
            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
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
