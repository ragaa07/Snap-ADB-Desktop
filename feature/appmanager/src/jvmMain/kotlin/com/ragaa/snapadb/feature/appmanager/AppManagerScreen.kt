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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.AppFilter
import com.ragaa.snapadb.core.adb.model.AppInfo
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is AppManagerState.NoDevice -> NoDeviceState()
        is AppManagerState.Loading -> LoadingState()
        is AppManagerState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(AppManagerIntent.Refresh) })
        is AppManagerState.Loaded -> LoadedContent(
            state = s,
            searchQuery = searchQuery,
            filter = filter,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun LoadedContent(
    state: AppManagerState.Loaded,
    searchQuery: String,
    filter: AppFilter,
    actionResult: ActionResult?,
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
                        "${state.apps.size} of ${state.totalCount} apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = { showInstallDialog(onIntent) }) {
                        Icon(Icons.Outlined.InstallMobile, contentDescription = "Install APK")
                    }
                    IconButton(onClick = { onIntent(AppManagerIntent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { onIntent(AppManagerIntent.UpdateSearch(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search packages...") },
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

            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { onIntent(AppManagerIntent.UpdateFilter(f)) },
                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // App list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRow(app, onIntent)
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
            AppDetailsDialog(details, onDismiss = { onIntent(AppManagerIntent.DismissDetails) })
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onIntent: (AppManagerIntent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onIntent(AppManagerIntent.GetDetails(app.packageName)) }
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (app.isSystemApp) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.isSystemApp) {
                Text(
                    "System",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!app.isSystemApp) {
            IconButton(onClick = { onIntent(AppManagerIntent.ForceStop(app.packageName)) }) {
                Icon(Icons.Outlined.Stop, contentDescription = "Force Stop", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onIntent(AppManagerIntent.ClearData(app.packageName)) }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Data", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onIntent(AppManagerIntent.Uninstall(app.packageName)) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Uninstall", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AppDetailsDialog(app: AppInfo, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .width(400.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("App Details", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Package", app.packageName)
            if (app.versionName.isNotEmpty()) DetailRow("Version", app.versionName)
            if (app.versionCode > 0) DetailRow("Version Code", app.versionCode.toString())
            DetailRow("Type", if (app.isSystemApp) "System" else "User")
            DetailRow("Enabled", if (app.isEnabled) "Yes" else "No")
            if (app.installerPackageName.isNotEmpty()) DetailRow("Installer", app.installerPackageName)
            if (app.apkPath.isNotEmpty()) DetailRow("APK Path", app.apkPath)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
                "Connect a device to manage apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
