package com.ragaa.snapadb.feature.dashboard

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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.BatteryStatus
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.StorageInfo
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is DashboardState.NoDevice -> NoDeviceState()
        is DashboardState.Loading -> LoadingState()
        is DashboardState.Error -> ErrorState(s.message, onRetry = { viewModel.refresh() })
        is DashboardState.Loaded -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 2,
                ) {
                    // Device Info Card
                    InfoCard(
                        title = "Device",
                        icon = Icons.Outlined.PhoneAndroid,
                        modifier = Modifier.weight(1f),
                    ) {
                        InfoRow("Model", s.deviceInfo.model)
                        InfoRow("Manufacturer", s.deviceInfo.manufacturer)
                        InfoRow("Android", "${s.deviceInfo.androidVersion} (SDK ${s.deviceInfo.sdkVersion})")
                        if (s.deviceInfo.buildNumber.isNotEmpty()) {
                            InfoRow("Build", s.deviceInfo.buildNumber)
                        }
                        InfoRow("Serial", s.deviceInfo.serial)
                    }

                    // Battery Card
                    InfoCard(
                        title = "Battery",
                        icon = Icons.Outlined.BatteryChargingFull,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (s.battery != null) {
                            BatteryContent(s.battery)
                        } else {
                            Text("Unavailable", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Storage Card
                    InfoCard(
                        title = "Storage",
                        icon = Icons.Outlined.Storage,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (s.storage.isNotEmpty()) {
                            StorageContent(s.storage)
                        } else {
                            Text("Unavailable", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Memory Card
                    InfoCard(
                        title = "Memory",
                        icon = Icons.Outlined.Memory,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (s.memory != null) {
                            MemoryContent(s.memory)
                        } else {
                            Text("Unavailable", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
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
                "Connect a device to view its dashboard",
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

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BatteryContent(battery: BatteryInfo) {
    // Level bar
    Text(
        "${battery.level}%",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = when {
            battery.level <= 15 -> MaterialTheme.colorScheme.error
            battery.level <= 30 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { battery.level / 100f },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        strokeCap = StrokeCap.Round,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    Spacer(modifier = Modifier.height(8.dp))
    InfoRow("Status", when (battery.status) {
        BatteryStatus.CHARGING -> "Charging"
        BatteryStatus.DISCHARGING -> "Discharging"
        BatteryStatus.FULL -> "Full"
        BatteryStatus.NOT_CHARGING -> "Not Charging"
        BatteryStatus.UNKNOWN -> "Unknown"
    })
    InfoRow("Health", battery.health)
    InfoRow("Temperature", "%.1f\u00B0C".format(battery.temperature))
    InfoRow("Plugged", battery.plugged.name.lowercase().replaceFirstChar { it.uppercase() })
}

@Composable
private fun StorageContent(storage: List<StorageInfo>) {
    storage.forEach { info ->
        val label = when {
            info.mountedOn == "/data" -> "Internal Storage"
            info.mountedOn.startsWith("/storage/") -> "External Storage"
            info.mountedOn == "/" -> "Root"
            info.mountedOn == "/system" -> "System"
            else -> info.mountedOn
        }
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { info.usePercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            color = if (info.usePercent > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        InfoRow("Used / Total", "${info.used} / ${info.size}")
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MemoryContent(memory: MemoryInfo) {
    Text(
        "${memory.usagePercent}%",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = when {
            memory.usagePercent > 85 -> MaterialTheme.colorScheme.error
            memory.usagePercent > 70 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { memory.usagePercent / 100f },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        strokeCap = StrokeCap.Round,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    Spacer(modifier = Modifier.height(8.dp))
    InfoRow("Used", memory.usedMb())
    InfoRow("Available", memory.availableMb())
    InfoRow("Total", memory.totalMb())
}
