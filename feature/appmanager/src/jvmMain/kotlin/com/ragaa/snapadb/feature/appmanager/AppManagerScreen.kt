package com.ragaa.snapadb.feature.appmanager

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.model.AppPermission
import com.ragaa.snapadb.core.adb.model.AppSort
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val permissions by viewModel.permissions.collectAsState()

    when (val s = state) {
        is AppManagerState.NoDevice -> NoDeviceState("Connect a device to manage apps")
        is AppManagerState.Loading -> LoadingState()
        is AppManagerState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(AppManagerIntent.Refresh) })
        is AppManagerState.Loaded -> LoadedContent(
            state = s,
            searchQuery = searchQuery,
            sort = sort,
            actionResult = actionResult,
            selectionMode = selectionMode,
            selectedPackages = selectedPackages,
            permissions = permissions,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun LoadedContent(
    state: AppManagerState.Loaded,
    searchQuery: String,
    sort: AppSort,
    actionResult: ActionResult?,
    selectionMode: Boolean,
    selectedPackages: Set<String>,
    permissions: List<AppPermission>,
    onIntent: (AppManagerIntent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("App Manager", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${state.apps.size} of ${state.totalCount} apps" +
                                if (selectionMode) " (${selectedPackages.size} selected)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    if (selectionMode) {
                        IconButton(onClick = { onIntent(AppManagerIntent.SelectAll) }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { onIntent(AppManagerIntent.ClearSelection) }) {
                            Icon(Icons.Outlined.Deselect, contentDescription = "Cancel Selection")
                        }
                    } else {
                        IconButton(onClick = { onIntent(AppManagerIntent.ToggleSelectionMode) }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "Select Mode")
                        }
                        IconButton(onClick = { showInstallDialog(onIntent) }) {
                            Icon(Icons.Outlined.InstallMobile, contentDescription = "Install APK")
                        }
                        IconButton(onClick = { onIntent(AppManagerIntent.Refresh) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }

            // Batch action bar
            if (selectionMode && selectedPackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                BatchActionBar(selectedPackages.size, onIntent)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { onIntent(AppManagerIntent.UpdateSearch(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onIntent(AppManagerIntent.UpdateSearch("")) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortDropdown(sort, onIntent)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // App list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRow(app, selectionMode, app.packageName in selectedPackages, onIntent)
                }
            }
        }

        // Action result snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(AppManagerIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { onIntent(AppManagerIntent.DismissResult) }) {
                        Text("Dismiss")
                    }
                },
                containerColor = when (result) {
                    is ActionResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is ActionResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is ActionResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is ActionResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is ActionResult.Success -> result.message
                        is ActionResult.Failure -> result.message
                    }
                )
            }
        }

        // App details dialog
        state.selectedAppDetails?.let { details ->
            AppDetailsDialog(
                app = details,
                permissions = permissions,
                onIntent = onIntent,
                onDismiss = { onIntent(AppManagerIntent.DismissDetails) },
            )
        }
    }
}

@Composable
private fun SortDropdown(sort: AppSort, onIntent: (AppManagerIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                when (sort) {
                    AppSort.NAME_ASC -> Icons.Outlined.ArrowUpward
                    AppSort.NAME_DESC -> Icons.Outlined.ArrowDownward
                    AppSort.RECENTLY_INSTALLED -> Icons.Outlined.Schedule
                },
                contentDescription = "Sort",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Name (A-Z)") },
                onClick = { onIntent(AppManagerIntent.UpdateSort(AppSort.NAME_ASC)); expanded = false },
                leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Name (Z-A)") },
                onClick = { onIntent(AppManagerIntent.UpdateSort(AppSort.NAME_DESC)); expanded = false },
                leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Recently Installed") },
                onClick = { onIntent(AppManagerIntent.UpdateSort(AppSort.RECENTLY_INSTALLED)); expanded = false },
                leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun BatchActionBar(selectedCount: Int, onIntent: (AppManagerIntent) -> Unit) {
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$selectedCount selected",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showConfirmDialog = "force_stop" }) {
            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Force Stop")
        }
        TextButton(onClick = { showConfirmDialog = "clear_data" }) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear Data")
        }
        TextButton(onClick = { showConfirmDialog = "uninstall" }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Uninstall", color = MaterialTheme.colorScheme.error)
        }
    }

    showConfirmDialog?.let { action ->
        val (title, message) = when (action) {
            "uninstall" -> "Uninstall $selectedCount apps?" to "This will permanently remove the selected apps and their data."
            "clear_data" -> "Clear data for $selectedCount apps?" to "This will delete all app data for the selected apps."
            "force_stop" -> "Force stop $selectedCount apps?" to "This will force stop all selected apps."
            else -> return@let
        }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = null
                    when (action) {
                        "uninstall" -> onIntent(AppManagerIntent.BatchUninstall)
                        "clear_data" -> onIntent(AppManagerIntent.BatchClearData)
                        "force_stop" -> onIntent(AppManagerIntent.BatchForceStop)
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    selectionMode: Boolean,
    isSelected: Boolean,
    onIntent: (AppManagerIntent) -> Unit,
) {
    var showUninstallConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (selectionMode && !app.isSystemApp) {
                    onIntent(AppManagerIntent.ToggleSelection(app.packageName))
                } else {
                    onIntent(AppManagerIntent.GetDetails(app.packageName))
                }
            }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onIntent(AppManagerIntent.ToggleSelection(app.packageName)) },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.appLabel.isNotEmpty()) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!selectionMode) {
            IconButton(onClick = { onIntent(AppManagerIntent.Launch(app.packageName)) }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Launch", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onIntent(AppManagerIntent.ForceStop(app.packageName)) }) {
                Icon(Icons.Outlined.Stop, contentDescription = "Force Stop", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onIntent(AppManagerIntent.ClearData(app.packageName)) }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Data", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showUninstallConfirm = true }) {
                Icon(
                    Icons.Outlined.Delete, contentDescription = "Uninstall", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showUninstallConfirm) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text("Uninstall ${app.displayName}?") },
            text = { Text("This will permanently remove the app and its data.") },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallConfirm = false
                    onIntent(AppManagerIntent.Uninstall(app.packageName))
                }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AppDetailsDialog(
    app: AppInfo,
    permissions: List<AppPermission>,
    onIntent: (AppManagerIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(app.packageName) {
        onIntent(AppManagerIntent.LoadPermissions(app.packageName))
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .width(500.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val clipboardManager = LocalClipboardManager.current
                        var copied by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(app.packageName))
                                copied = true
                            },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy package name",
                                modifier = Modifier.size(14.dp),
                                tint = if (copied) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (copied) {
                            LaunchedEffect(Unit) { delay(2000); copied = false }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Info section
                DetailSection("Info") {
                    if (app.versionName.isNotEmpty()) DetailRow("Version", app.versionName)
                    if (app.versionCode > 0) DetailRow("Version Code", app.versionCode.toString())
                    DetailRow("Enabled", if (app.isEnabled) "Yes" else "No")
                    if (app.installerPackageName.isNotEmpty()) DetailRow("Installer", app.installerPackageName)
                    if (app.apkPath.isNotEmpty()) DetailRow("Path", app.apkPath)
                }

                // Storage section
                if (app.codeSize >= 0 || app.dataSize >= 0 || app.cacheSize >= 0) {
                    DetailSection("Storage") {
                        if (app.codeSize >= 0) DetailRow("Code", formatSize(app.codeSize))
                        if (app.dataSize >= 0) DetailRow("Data", formatSize(app.dataSize))
                        if (app.cacheSize >= 0) DetailRow("Cache", formatSize(app.cacheSize))
                        if (app.totalSize > 0) DetailRow("Total", formatSize(app.totalSize))
                    }
                }

                // Permissions section
                if (permissions.isNotEmpty()) {
                    PermissionsSection(
                        permissions = permissions,
                        onToggle = { perm ->
                            onIntent(AppManagerIntent.TogglePermission(app.packageName, perm))
                        },
                    )
                }
            }

            // Action buttons
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = { onIntent(AppManagerIntent.Launch(app.packageName)) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Launch")
                }
                TextButton(onClick = { onIntent(AppManagerIntent.ForceStop(app.packageName)) }) {
                    Text("Force Stop")
                }
                TextButton(onClick = { onIntent(AppManagerIntent.ClearData(app.packageName)) }) {
                    Text("Clear Data")
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun PermissionsSection(
    permissions: List<AppPermission>,
    onToggle: (AppPermission) -> Unit,
) {
    val granted = permissions.filter { it.isGranted }
    val denied = permissions.filter { !it.isGranted }

    Column {
        Text(
            "Permissions (${granted.size}/${permissions.size} granted)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (granted.isNotEmpty()) {
                granted.forEachIndexed { index, perm ->
                    PermissionRow(perm) { onToggle(perm) }
                    if (index < granted.lastIndex || denied.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            if (denied.isNotEmpty()) {
                if (granted.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Denied",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                denied.forEachIndexed { index, perm ->
                    PermissionRow(perm) { onToggle(perm) }
                    if (index < denied.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: AppPermission, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatPermissionName(permission.name),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = permission.isGranted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(width = 36.dp, height = 20.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatPermissionName(fullName: String): String {
    val short = fullName.substringAfterLast('.')
    return short.split('_')
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun showInstallDialog(onIntent: (AppManagerIntent) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select APK"
            fileFilter = FileNameExtensionFilter("APK files", "apk")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onIntent(AppManagerIntent.Install(chooser.selectedFile.absolutePath))
        }
    }
}
