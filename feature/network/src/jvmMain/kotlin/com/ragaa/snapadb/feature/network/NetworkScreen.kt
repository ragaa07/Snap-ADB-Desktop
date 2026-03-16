package com.ragaa.snapadb.feature.network

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import com.ragaa.snapadb.core.ui.components.SectionCard
import com.ragaa.snapadb.feature.network.model.ProxyPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.awt.FileDialog
import java.awt.Frame

@Composable
fun NetworkScreen(viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is NetworkState.NoDevice -> NoDeviceState("Connect a device to manage proxy")
        is NetworkState.Loading -> LoadingState()
        is NetworkState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(NetworkIntent.Refresh) })
        is NetworkState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyContent(
    state: NetworkState.Ready,
    actionResult: NetworkResult?,
    onIntent: (NetworkIntent) -> Unit,
) {
    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                Text("Network / Proxy", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { onIntent(NetworkIntent.Refresh) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }

            // Description
            SectionCard("About", Icons.Outlined.Info) {
                Text(
                    text = "Configure your device's HTTP proxy to route traffic through debugging tools " +
                        "like Charles, Fiddler, mitmproxy, or Proxyman. Use quick presets for one-tap setup, " +
                        "or enter a custom host and port. For HTTPS inspection, install your tool's CA certificate " +
                        "using the helper below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Current Proxy Status
            SectionCard("Proxy Status", Icons.Outlined.Wifi) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.currentProxy.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                    Text(
                        text = if (state.currentProxy.isNotEmpty()) state.currentProxy else "No proxy set",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.currentProxy.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Quick Presets
            SectionCard("Quick Presets", Icons.Outlined.Speed) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProxyPreset.entries.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                hostInput = preset.defaultHost
                                portInput = preset.defaultPort.toString()
                                onIntent(NetworkIntent.ApplyPreset(preset))
                            },
                            label = { Text("${preset.displayName} (:${preset.defaultPort})") },
                        )
                    }
                }
            }

            // Custom Proxy
            SectionCard("Custom Proxy", Icons.Outlined.Lan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Host (e.g. 127.0.0.1)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(100.dp),
                        placeholder = { Text("Port") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Button(
                        onClick = {
                            val port = portInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: return@Button
                            val host = hostInput.trim()
                            if (host.isNotBlank()) {
                                onIntent(NetworkIntent.SetProxy(host, port))
                            }
                        },
                        enabled = hostInput.isNotBlank() &&
                            portInput.toIntOrNull()?.let { it in 1..65535 } == true,
                    ) { Text("Apply") }
                }
            }

            // Clear Proxy
            OutlinedButton(
                onClick = { onIntent(NetworkIntent.ClearProxy) },
                enabled = state.currentProxy.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Proxy")
            }

            // Certificate Installation Helper
            SectionCard("Certificate Helper", Icons.Outlined.Security) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Install a CA certificate on the device for HTTPS traffic inspection. " +
                            "Select a .pem, .cer, or .crt file from your computer — it will be pushed to the " +
                            "device and the system certificate installer will open automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val path = pickCertFile()
                                    if (path != null) {
                                        onIntent(NetworkIntent.InstallCert(path))
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select Certificate")
                        }

                        OutlinedButton(
                            onClick = { onIntent(NetworkIntent.OpenSecuritySettings) },
                        ) {
                            Text("Open Security Settings")
                        }
                    }
                }
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(NetworkIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(NetworkIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is NetworkResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is NetworkResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is NetworkResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is NetworkResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is NetworkResult.Success -> result.message
                        is NetworkResult.Failure -> result.message
                    },
                )
            }
        }
    }
}

private fun pickCertFile(): String? {
    val dialog = FileDialog(null as Frame?, "Select CA Certificate", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".pem", ignoreCase = true) ||
            name.endsWith(".cer", ignoreCase = true) ||
            name.endsWith(".crt", ignoreCase = true) ||
            name.endsWith(".der", ignoreCase = true)
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    return if (dir != null && file != null) "$dir$file" else null
}
