package com.ragaa.snapadb.feature.performance

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
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import com.ragaa.snapadb.feature.performance.chart.ChartConfig
import com.ragaa.snapadb.feature.performance.chart.ChartSeries
import com.ragaa.snapadb.feature.performance.chart.LineChart
import com.ragaa.snapadb.feature.performance.model.ThresholdConfig
import com.ragaa.snapadb.feature.performance.session.PerformanceSessionSummary
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun PerformanceScreen(viewModel: PerformanceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    when (val s = state) {
        is PerformanceState.NoDevice -> NoDeviceContent()
        is PerformanceState.Ready -> ReadyContent(
            state = s,
            actionResult = actionResult,
            onIntent = viewModel::onIntent,
        )
    }
}

@Composable
private fun ReadyContent(
    state: PerformanceState.Ready,
    actionResult: PerformanceResult?,
    onIntent: (PerformanceIntent) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "CPU", "Memory", "Battery", "GPU", "Network")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header + Controls
            ControlBar(state, onIntent)

            // Alert banner
            if (state.activeAlerts.isNotEmpty()) {
                AlertBanner(state, onIntent)
            }

            // Session viewing banner
            state.viewingSessionId?.let { sessionId ->
                val session = state.sessions.firstOrNull { it.id == sessionId }
                SessionViewBanner(session, onIntent)
            }

            // Tabs
            @OptIn(ExperimentalMaterial3Api::class)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            // Tab content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedTab) {
                    0 -> OverviewTab(state)
                    1 -> CpuTab(state)
                    2 -> MemoryTab(state, onIntent)
                    3 -> BatteryTab(state)
                    4 -> GpuTab(state)
                    5 -> NetworkTab(state)
                }
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(PerformanceIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { onIntent(PerformanceIntent.DismissResult) }) { Text("Dismiss") } },
                containerColor = when (result) {
                    is PerformanceResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is PerformanceResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is PerformanceResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is PerformanceResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is PerformanceResult.Success -> result.message
                        is PerformanceResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun AlertBanner(state: PerformanceState.Ready, onIntent: (PerformanceIntent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = state.activeAlerts.joinToString(" | ") {
                val unit = if (it.metric == "Battery Temp") "\u00B0C" else "%"
                "${it.metric}: %.1f$unit (threshold: %.1f$unit)".format(it.currentValue, it.threshold)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onIntent(PerformanceIntent.DismissAlerts) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SessionViewBanner(session: PerformanceSessionSummary?, onIntent: (PerformanceIntent) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = session?.let {
                "Viewing session from ${dateFormat.format(Date(it.startedAt))}"
            } ?: "Viewing historical session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onIntent(PerformanceIntent.ExitSessionView) }) {
            Text("Exit")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlBar(state: PerformanceState.Ready, onIntent: (PerformanceIntent) -> Unit) {
    var intervalExpanded by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showSessionsMenu by remember { mutableStateOf(false) }
    var deleteConfirmSessionId by remember { mutableStateOf<Long?>(null) }
    val intervals = listOf(500L to "500ms", 1000L to "1s", 2000L to "2s", 5000L to "5s")
    val currentLabel = intervals.firstOrNull { it.first == state.pollingIntervalMs }?.second ?: "${state.pollingIntervalMs}ms"
    val isViewingSession = state.viewingSessionId != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Performance", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))

        // Threshold settings
        IconButton(onClick = { showThresholdDialog = true }) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Thresholds")
        }

        // Sessions
        Box {
            IconButton(onClick = { showSessionsMenu = !showSessionsMenu }) {
                Icon(Icons.Outlined.History, contentDescription = "Sessions")
            }
            DropdownMenu(expanded = showSessionsMenu, onDismissRequest = { showSessionsMenu = false }) {
                if (state.sessions.isEmpty()) {
                    DropdownMenuItem(text = { Text("No saved sessions") }, onClick = {})
                } else {
                    state.sessions.forEach { session ->
                        SessionMenuItem(
                            session = session,
                            onLoad = {
                                onIntent(PerformanceIntent.LoadSession(session.id))
                                showSessionsMenu = false
                            },
                            onDelete = { deleteConfirmSessionId = session.id },
                        )
                    }
                }
            }
        }

        // Save session
        IconButton(
            onClick = { onIntent(PerformanceIntent.SaveSession) },
            enabled = !isViewingSession && state.snapshot.cpuPoints.isNotEmpty(),
        ) {
            Icon(Icons.Outlined.Save, contentDescription = "Save Session")
        }

        // Polling interval
        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { if (!isViewingSession) intervalExpanded = it },
            modifier = Modifier.width(120.dp),
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                enabled = !isViewingSession,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                label = { Text("Interval") },
            )
            ExposedDropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
                intervals.forEach { (ms, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntent(PerformanceIntent.SetPollingInterval(ms))
                            intervalExpanded = false
                        },
                    )
                }
            }
        }

        // Start/Stop
        if (!isViewingSession) {
            if (state.isMonitoring) {
                Button(
                    onClick = { onIntent(PerformanceIntent.StopMonitoring) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            } else {
                Button(onClick = { onIntent(PerformanceIntent.StartMonitoring) }) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }
            }
        }

        // Reset
        OutlinedButton(
            onClick = { onIntent(PerformanceIntent.ResetData) },
            enabled = !isViewingSession,
        ) { Text("Reset") }

        // Export
        OutlinedButton(onClick = {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export CSV"
                    fileFilter = FileNameExtensionFilter("CSV Files", "csv")
                    selectedFile = File("performance_${System.currentTimeMillis()}.csv")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    var file = chooser.selectedFile
                    if (!file.name.endsWith(".csv")) file = File(file.absolutePath + ".csv")
                    onIntent(PerformanceIntent.ExportCsv(file))
                }
            }
        }) {
            Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("CSV")
        }
    }

    // Threshold config dialog
    if (showThresholdDialog) {
        ThresholdConfigDialog(
            config = state.thresholdConfig,
            onDismiss = { showThresholdDialog = false },
            onSave = { config ->
                onIntent(PerformanceIntent.SetThresholdConfig(config))
                showThresholdDialog = false
            },
        )
    }

    // Delete confirmation dialog
    deleteConfirmSessionId?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmSessionId = null },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete this session? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onIntent(PerformanceIntent.DeleteSession(sessionId))
                        deleteConfirmSessionId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmSessionId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionMenuItem(
    session: PerformanceSessionSummary,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    "${session.deviceSerial} - ${dateFormat.format(Date(session.startedAt))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    session.appPackage ?: "System-wide",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onLoad,
        trailingIcon = {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun ThresholdConfigDialog(
    config: ThresholdConfig,
    onDismiss: () -> Unit,
    onSave: (ThresholdConfig) -> Unit,
) {
    var cpuEnabled by remember { mutableStateOf(config.cpuPercent != null) }
    var cpuValue by remember { mutableStateOf(config.cpuPercent?.toString() ?: "80") }
    var memEnabled by remember { mutableStateOf(config.memoryPercent != null) }
    var memValue by remember { mutableStateOf(config.memoryPercent?.toString() ?: "90") }
    var tempEnabled by remember { mutableStateOf(config.batteryTempC != null) }
    var tempValue by remember { mutableStateOf(config.batteryTempC?.toString() ?: "40") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Threshold Alerts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThresholdRow("CPU %", cpuEnabled, cpuValue, { cpuEnabled = it }, { cpuValue = it })
                ThresholdRow("Memory %", memEnabled, memValue, { memEnabled = it }, { memValue = it })
                ThresholdRow("Temp \u00B0C", tempEnabled, tempValue, { tempEnabled = it }, { tempValue = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ThresholdConfig(
                        cpuPercent = if (cpuEnabled) cpuValue.toFloatOrNull() else null,
                        memoryPercent = if (memEnabled) memValue.toFloatOrNull() else null,
                        batteryTempC = if (tempEnabled) tempValue.toFloatOrNull() else null,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ThresholdRow(
    label: String,
    enabled: Boolean,
    value: String,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        Text(label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.width(100.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun OverviewTab(state: PerformanceState.Ready) {
    val snap = state.snapshot

    // Summary cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            title = "CPU",
            value = snap.cpuInfo?.let { "%.1f%%".format(it.overallPercent) } ?: "\u2014",
            icon = Icons.Outlined.Speed,
            color = SnapAdbTheme.colors.chartBlue,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "Memory",
            value = snap.memoryInfo?.let { "${it.usagePercent}%" } ?: "\u2014",
            icon = Icons.Outlined.Memory,
            color = SnapAdbTheme.colors.chartGreen,
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "Battery",
            value = snap.batteryInfo?.let { "${it.level}%" } ?: "\u2014",
            icon = Icons.Outlined.Battery5Bar,
            color = SnapAdbTheme.colors.chartOrange,
            modifier = Modifier.weight(1f),
        )
    }

    // GPU + Network summary row
    if (snap.gpuInfo != null || snap.networkIoRate != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            snap.gpuInfo?.let { gpu ->
                SummaryCard(
                    title = "Janky Frames",
                    value = "%.1f%%".format(gpu.jankyPercent),
                    icon = Icons.Outlined.Speed,
                    color = SnapAdbTheme.colors.chartPurple,
                    modifier = Modifier.weight(1f),
                )
            }
            snap.networkIoRate?.let { rate ->
                SummaryCard(
                    title = "Net Down",
                    value = formatBytesPerSec(rate.rxBytesPerSec),
                    icon = Icons.Outlined.Speed,
                    color = SnapAdbTheme.colors.chartCyan,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "Net Up",
                    value = formatBytesPerSec(rate.txBytesPerSec),
                    icon = Icons.Outlined.Speed,
                    color = SnapAdbTheme.colors.chartYellow,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Combined chart
    SectionCard("System Overview") {
        val series = buildList {
            if (snap.cpuPoints.isNotEmpty()) {
                add(ChartSeries("CPU", SnapAdbTheme.colors.chartBlue, snap.cpuPoints))
            }
            if (snap.memoryPoints.isNotEmpty()) {
                add(ChartSeries("Memory", SnapAdbTheme.colors.chartGreen, snap.memoryPoints))
            }
            if (snap.batteryPoints.isNotEmpty()) {
                add(ChartSeries("Battery", SnapAdbTheme.colors.chartOrange, snap.batteryPoints))
            }
        }
        if (series.isEmpty()) {
            EmptyChartPlaceholder()
        } else {
            LineChart(
                series = series,
                config = ChartConfig(maxY = 100f, yAxisLabel = "%"),
                modifier = Modifier.fillMaxWidth().height(250.dp),
            )
        }
        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            LegendItem("CPU", SnapAdbTheme.colors.chartBlue)
            LegendItem("Memory", SnapAdbTheme.colors.chartGreen)
            LegendItem("Battery", SnapAdbTheme.colors.chartOrange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CpuTab(state: PerformanceState.Ready) {
    val snap = state.snapshot

    SectionCard("CPU Usage") {
        if (snap.cpuPoints.isEmpty()) {
            EmptyChartPlaceholder()
        } else {
            LineChart(
                series = listOf(ChartSeries("CPU", SnapAdbTheme.colors.chartBlue, snap.cpuPoints)),
                config = ChartConfig(maxY = 100f, yAxisLabel = "%"),
                modifier = Modifier.fillMaxWidth().height(250.dp),
            )
        }
    }

    // Per-core chips
    snap.cpuInfo?.let { info ->
        SectionCard("Per-Core Usage") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.perCorePercent.forEachIndexed { index, percent ->
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "Core $index: %.1f%%".format(percent),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryTab(state: PerformanceState.Ready, onIntent: (PerformanceIntent) -> Unit) {
    val snap = state.snapshot

    SectionCard("RAM Usage") {
        if (snap.memoryPoints.isEmpty()) {
            EmptyChartPlaceholder()
        } else {
            LineChart(
                series = listOf(ChartSeries("Memory", SnapAdbTheme.colors.chartGreen, snap.memoryPoints)),
                config = ChartConfig(maxY = 100f, yAxisLabel = "%"),
                modifier = Modifier.fillMaxWidth().height(250.dp),
            )
        }
    }

    // Memory stats
    snap.memoryInfo?.let { mem ->
        SectionCard("Memory Stats") {
            StatRow("Total", mem.totalMb())
            StatRow("Used", mem.usedMb())
            StatRow("Available", mem.availableMb())
            StatRow("Usage", "${mem.usagePercent}%")
        }
    }

    // Per-app memory
    SectionCard("App Memory") {
        var input by remember { mutableStateOf(state.appPackageFilter) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Package name (e.g., com.example.app)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = { onIntent(PerformanceIntent.SetAppPackageFilter(input)) },
                enabled = input.isNotBlank(),
            ) { Text("Track") }
            if (state.appPackageFilter.isNotBlank()) {
                OutlinedButton(onClick = {
                    input = ""
                    onIntent(PerformanceIntent.SetAppPackageFilter(""))
                }) { Text("Clear") }
            }
        }

        snap.appMemoryInfo?.let { appMem ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(appMem.packageName, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow("Total PSS", "%.1f MB".format(appMem.totalPssKb / 1024.0))
            StatRow("Java Heap", "%.1f MB".format(appMem.javaHeapKb / 1024.0))
            StatRow("Native Heap", "%.1f MB".format(appMem.nativeHeapKb / 1024.0))
            StatRow("Code", "%.1f MB".format(appMem.codeKb / 1024.0))
            StatRow("Stack", "%.1f MB".format(appMem.stackKb / 1024.0))
            StatRow("Graphics", "%.1f MB".format(appMem.graphicsKb / 1024.0))
            StatRow("System", "%.1f MB".format(appMem.systemKb / 1024.0))
        }
    }
}

@Composable
private fun BatteryTab(state: PerformanceState.Ready) {
    val snap = state.snapshot

    SectionCard("Battery Level") {
        if (snap.batteryPoints.isEmpty()) {
            EmptyChartPlaceholder()
        } else {
            LineChart(
                series = listOf(ChartSeries("Battery", SnapAdbTheme.colors.chartOrange, snap.batteryPoints)),
                config = ChartConfig(maxY = 100f, yAxisLabel = "%"),
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
    }

    SectionCard("Temperature") {
        if (snap.batteryTempPoints.isEmpty()) {
            EmptyChartPlaceholder()
        } else {
            val maxTemp = snap.batteryTempPoints.maxOfOrNull { it.value }?.let { (it + 10f).coerceAtMost(60f) } ?: 60f
            LineChart(
                series = listOf(ChartSeries("Temp", SnapAdbTheme.colors.chartRed, snap.batteryTempPoints)),
                config = ChartConfig(minY = 0f, maxY = maxTemp, yAxisLabel = "\u00B0C", gridLineCount = 3),
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
    }

    // Battery stats
    snap.batteryInfo?.let { batt ->
        SectionCard("Battery Stats") {
            StatRow("Level", "${batt.level}%")
            StatRow("Status", batt.status.name)
            StatRow("Health", batt.health)
            StatRow("Plug", batt.plugged.name)
            StatRow("Temperature", "%.1f \u00B0C".format(batt.temperature))
            if (batt.voltage > 0) StatRow("Voltage", "${batt.voltage} mV")
            if (batt.currentNow != 0) StatRow("Current", "${batt.currentNow} \u00B5A")
        }
    }
}

@Composable
private fun GpuTab(state: PerformanceState.Ready) {
    val snap = state.snapshot

    if (state.appPackageFilter.isBlank()) {
        SectionCard("GPU Rendering") {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Set an app package filter in the Memory tab to view GPU stats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    snap.gpuInfo?.let { gpu ->
        // Summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                title = "Total Frames",
                value = gpu.totalFrames.toString(),
                icon = Icons.Outlined.Speed,
                color = SnapAdbTheme.colors.chartPurple,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Janky Frames",
                value = gpu.jankyFrames.toString(),
                icon = Icons.Outlined.Warning,
                color = SnapAdbTheme.colors.chartRed,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Janky %",
                value = "%.1f%%".format(gpu.jankyPercent),
                icon = Icons.Outlined.Speed,
                color = if (gpu.jankyPercent > 5f) SnapAdbTheme.colors.chartRed else SnapAdbTheme.colors.chartPurple,
                modifier = Modifier.weight(1f),
            )
        }

        SectionCard("Frame Time Percentiles") {
            StatRow("50th percentile", "%.1f ms".format(gpu.percentile50Ms))
            StatRow("90th percentile", "%.1f ms".format(gpu.percentile90Ms))
            StatRow("95th percentile", "%.1f ms".format(gpu.percentile95Ms))
            StatRow("99th percentile", "%.1f ms".format(gpu.percentile99Ms))
        }
    } ?: run {
        SectionCard("GPU Rendering") {
            EmptyChartPlaceholder()
        }
    }
}

@Composable
private fun NetworkTab(state: PerformanceState.Ready) {
    val snap = state.snapshot

    if (state.appPackageFilter.isBlank()) {
        SectionCard("Network I/O") {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Set an app package filter in the Memory tab to view network stats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    SectionCard("Network I/O") {
        val hasData = snap.networkRxPoints.isNotEmpty() || snap.networkTxPoints.isNotEmpty()
        if (!hasData) {
            EmptyChartPlaceholder()
        } else {
            val series = buildList {
                if (snap.networkRxPoints.isNotEmpty()) {
                    add(ChartSeries("RX", SnapAdbTheme.colors.chartCyan, snap.networkRxPoints))
                }
                if (snap.networkTxPoints.isNotEmpty()) {
                    add(ChartSeries("TX", SnapAdbTheme.colors.chartYellow, snap.networkTxPoints))
                }
            }
            val maxVal = maxOf(
                snap.networkRxPoints.maxOfOrNull { it.value } ?: 0f,
                snap.networkTxPoints.maxOfOrNull { it.value } ?: 0f,
                1f,
            ) * 1.2f
            LineChart(
                series = series,
                config = ChartConfig(maxY = maxVal, yAxisLabel = "B/s"),
                modifier = Modifier.fillMaxWidth().height(250.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                LegendItem("Download (RX)", SnapAdbTheme.colors.chartCyan)
                LegendItem("Upload (TX)", SnapAdbTheme.colors.chartYellow)
            }
        }
    }

    // Current rates
    snap.networkIoRate?.let { rate ->
        SectionCard("Current Rates") {
            StatRow("Download", formatBytesPerSec(rate.rxBytesPerSec))
            StatRow("Upload", formatBytesPerSec(rate.txBytesPerSec))
        }
    }
}

// Utility composables

private fun formatBytesPerSec(bytesPerSec: Float): String {
    return when {
        bytesPerSec >= 1_048_576f -> "%.1f MB/s".format(bytesPerSec / 1_048_576f)
        bytesPerSec >= 1024f -> "%.1f KB/s".format(bytesPerSec / 1024f)
        else -> "%.0f B/s".format(bytesPerSec)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyChartPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Start monitoring to see data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoDeviceContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.DeviceUnknown, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No device selected", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Connect a device to monitor performance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
