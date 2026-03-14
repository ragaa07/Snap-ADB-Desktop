package com.ragaa.snapadb.feature.multidevice

import com.ragaa.snapadb.common.ActionResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.DeviceState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MultiDeviceScreen(viewModel: MultiDeviceViewModel = koinViewModel()) {
    val devicesWithNicknames by viewModel.devicesWithNicknames.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()

    var showConnectDialog by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var editingNicknameSerial by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Connected Devices", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${devicesWithNicknames.size} device(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPairDialog = true }) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pair")
                }
                Button(onClick = { showConnectDialog = true }) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devicesWithNicknames.isEmpty()) {
            EmptyDeviceState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(devicesWithNicknames, key = { it.device.serial }) { item ->
                    DeviceCard(
                        item = item,
                        isSelected = item.device.serial == selectedDevice?.serial,
                        onSelect = { viewModel.selectDevice(item.device) },
                        onDisconnect = { viewModel.disconnect(item.device.serial) },
                        onEditNickname = { editingNicknameSerial = item.device.serial },
                    )
                }
            }
        }
    }

    // Dialogs
    if (showConnectDialog) {
        ConnectDialog(
            viewModel = viewModel,
            onDismiss = {
                showConnectDialog = false
                viewModel.clearConnectResult()
            },
        )
    }
    if (showPairDialog) {
        PairDialog(
            viewModel = viewModel,
            onDismiss = {
                showPairDialog = false
                viewModel.clearPairResult()
            },
        )
    }
    editingNicknameSerial?.let { serial ->
        val currentNickname = devicesWithNicknames.find { it.device.serial == serial }?.nickname ?: ""
        NicknameDialog(
            currentNickname = currentNickname,
            onConfirm = { nickname ->
                viewModel.setNickname(serial, nickname)
                editingNicknameSerial = null
            },
            onDismiss = { editingNicknameSerial = null },
        )
    }
}

@Composable
private fun EmptyDeviceState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Devices,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("No devices connected", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Connect a device via USB or use wireless debugging",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceCard(
    item: DeviceWithNickname,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit,
    onEditNickname: () -> Unit,
) {
    val device = item.device
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (item.nickname != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.model.ifEmpty { device.serial },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.serial,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // State badge
            StateBadge(device.state)

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            IconButton(onClick = onEditNickname, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit nickname", modifier = Modifier.size(18.dp))
            }
            if (device.serial.contains(":")) {
                IconButton(onClick = onDisconnect, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = "Disconnect", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: DeviceState) {
    val (text, color) = when (state) {
        DeviceState.DEVICE -> "Online" to com.ragaa.snapadb.core.theme.SnapAdbTheme.colors.connected
        DeviceState.OFFLINE -> "Offline" to MaterialTheme.colorScheme.error
        DeviceState.UNAUTHORIZED -> "Unauthorized" to MaterialTheme.colorScheme.tertiary
        DeviceState.AUTHORIZING -> "Authorizing" to MaterialTheme.colorScheme.tertiary
        DeviceState.NO_PERMISSIONS -> "No Permissions" to MaterialTheme.colorScheme.error
        DeviceState.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Badge(containerColor = color.copy(alpha = 0.15f), contentColor = color) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun ConnectDialog(viewModel: MultiDeviceViewModel, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    val result by viewModel.connectResult.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Wirelessly") },
        text = {
            Column {
                Text(
                    "Enter the device IP address and port from wireless debugging settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("IP:Port") },
                    placeholder = { Text("192.168.1.100:5555") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ResultMessage(result)
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.connect(address.trim()) },
                enabled = address.isNotBlank() && result !is ActionResult.Loading,
            ) {
                if (result is ActionResult.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PairDialog(viewModel: MultiDeviceViewModel, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val result by viewModel.pairResult.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Device") },
        text = {
            Column {
                Text(
                    "Enter the pairing code and address from your device's wireless debugging settings. Requires Android 11+.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("IP:Port") },
                    placeholder = { Text("192.168.1.100:37199") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Pairing Code") },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ResultMessage(result)
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.pair(address.trim(), code.trim()) },
                enabled = address.isNotBlank() && code.length == 6 && result !is ActionResult.Loading,
            ) {
                if (result is ActionResult.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Pair")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NicknameDialog(currentNickname: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var nickname by remember { mutableStateOf(currentNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Nickname") },
        text = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname") },
                placeholder = { Text("My Phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (nickname.isNotEmpty()) {
                        IconButton(onClick = { nickname = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(nickname) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ResultMessage(result: ActionResult?) {
    when (result) {
        is ActionResult.Success -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(result.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        is ActionResult.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        else -> {}
    }
}
