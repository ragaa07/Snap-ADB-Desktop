package com.ragaa.snapadb.feature.monkey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import com.ragaa.snapadb.feature.monkey.model.MonkeyConfig
import com.ragaa.snapadb.feature.monkey.model.MonkeyOutputLine
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunStatus
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunSummary
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MonkeyScreen(viewModel: MonkeyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is MonkeyState.NoDevice -> NoDeviceState("Connect a device to run monkey tests")
            is MonkeyState.Idle -> IdleContent(
                state = s,
                onIntent = viewModel::onIntent,
            )
            is MonkeyState.Running -> RunningContent(
                state = s,
                onIntent = viewModel::onIntent,
            )
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                viewModel.onIntent(MonkeyIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onIntent(MonkeyIntent.DismissResult) }) {
                        Text("Dismiss")
                    }
                },
                containerColor = when (result) {
                    is MonkeyResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is MonkeyResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is MonkeyResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is MonkeyResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is MonkeyResult.Success -> result.message
                        is MonkeyResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(
    state: MonkeyState.Idle,
    onIntent: (MonkeyIntent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel — config
        ConfigPanel(
            config = state.currentConfig,
            suggestions = state.packageSuggestions,
            configs = state.configs,
            enabled = true,
            onIntent = onIntent,
            modifier = Modifier.weight(0.4f).fillMaxHeight(),
        )

        // Right panel — history
        HistoryPanel(
            runs = state.runs,
            onDeleteRun = { onIntent(MonkeyIntent.DeleteRun(it)) },
            modifier = Modifier.weight(0.6f).fillMaxHeight(),
        )
    }
}

@Composable
private fun RunningContent(
    state: MonkeyState.Running,
    onIntent: (MonkeyIntent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel — config (disabled) + stop button
        Column(
            modifier = Modifier.weight(0.4f).fillMaxHeight().padding(16.dp),
        ) {
            Text("Running Test", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Package: ${state.config.packageName}", style = MaterialTheme.typography.bodyMedium)
            Text("Events: ${state.injectedEvents} / ${state.totalEvents}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onIntent(MonkeyIntent.StopTest) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop & Back")
            }
        }

        // Right panel — live output
        LiveOutputPanel(
            state = state,
            modifier = Modifier.weight(0.6f).fillMaxHeight(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPanel(
    config: MonkeyConfig,
    suggestions: List<String>,
    configs: List<MonkeyConfig>,
    enabled: Boolean,
    onIntent: (MonkeyIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configuration", style = MaterialTheme.typography.titleMedium)

        // Package name with autocomplete
        Box {
            OutlinedTextField(
                value = config.packageName,
                onValueChange = { onIntent(MonkeyIntent.UpdatePackageName(it)) },
                label = { Text("Package Name") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(),
                onDismissRequest = { onIntent(MonkeyIntent.DismissSuggestions) },
            ) {
                suggestions.forEach { pkg ->
                    DropdownMenuItem(
                        text = { Text(pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onIntent(MonkeyIntent.SelectPackage(pkg)) },
                    )
                }
            }
        }

        // Event count
        OutlinedTextField(
            value = config.eventCount.toString(),
            onValueChange = { onIntent(MonkeyIntent.UpdateEventCount(it)) },
            label = { Text("Event Count") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Seed
            OutlinedTextField(
                value = config.seed?.toString() ?: "",
                onValueChange = { onIntent(MonkeyIntent.UpdateSeed(it)) },
                label = { Text("Seed") },
                placeholder = { Text("Random") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            // Throttle
            OutlinedTextField(
                value = config.throttleMs.toString(),
                onValueChange = { onIntent(MonkeyIntent.UpdateThrottle(it)) },
                label = { Text("Throttle (ms)") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // Verbosity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Verbosity:", style = MaterialTheme.typography.bodyMedium)
            (1..3).forEach { level ->
                FilterChip(
                    selected = config.verbosity == level,
                    onClick = { if (enabled) onIntent(MonkeyIntent.UpdateVerbosity(level)) },
                    label = { Text("$level") },
                )
            }
        }

        // Categories
        Text("Categories:", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AVAILABLE_CATEGORIES.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = cat in config.categories,
                        onCheckedChange = { if (enabled) onIntent(MonkeyIntent.ToggleCategory(cat)) },
                        enabled = enabled,
                    )
                    Text(
                        cat.substringAfterLast("."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Restrict to app toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = enabled) {
                onIntent(MonkeyIntent.ToggleRestrictToApp)
            },
        ) {
            Checkbox(
                checked = config.restrictToApp,
                onCheckedChange = { if (enabled) onIntent(MonkeyIntent.ToggleRestrictToApp) },
                enabled = enabled,
            )
            Column {
                Text("Restrict to app", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Disables Home/Back/Switch keys — keeps events inside your app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        // Config name + save
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = config.name,
                onValueChange = { onIntent(MonkeyIntent.UpdateConfigName(it)) },
                label = { Text("Config Name") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onIntent(MonkeyIntent.SaveConfig) },
                enabled = enabled,
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "Save config")
            }
        }

        // Saved configs
        if (configs.isNotEmpty()) {
            Text("Saved Configs:", style = MaterialTheme.typography.bodySmall)
            configs.forEach { savedConfig ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = enabled) { onIntent(MonkeyIntent.LoadConfig(savedConfig)) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(savedConfig.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${savedConfig.packageName} — ${savedConfig.eventCount} events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onIntent(MonkeyIntent.DeleteConfig(savedConfig.id)) },
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete config",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Start button
        Button(
            onClick = { onIntent(MonkeyIntent.StartTest) },
            enabled = enabled && config.packageName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Monkey Test")
        }
    }
}

@Composable
private fun LiveOutputPanel(
    state: MonkeyState.Running,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.outputLines.size) {
        if (state.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(state.outputLines.size - 1)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text("Live Output", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Progress bar
        val progress = if (state.totalEvents > 0) {
            state.injectedEvents.toFloat() / state.totalEvents
        } else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${state.injectedEvents} / ${state.totalEvents} events (${(progress * 100).toInt()}%)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Crash/ANR indicators
        if (state.crashDetected || state.anrDetected) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.crashDetected) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("CRASH") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
                if (state.anrDetected) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("ANR") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Output log (append-only — index keys are stable)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(8.dp),
        ) {
            itemsIndexed(state.outputLines) { _, line ->
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = line.color(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    runs: List<MonkeyRunSummary>,
    onDeleteRun: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("Run History", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (runs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No test runs yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(runs, key = { it.id }) { run ->
                    RunHistoryItem(
                        run = run,
                        onDelete = { onDeleteRun(run.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunHistoryItem(
    run: MonkeyRunSummary,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusBadge(run.status)
                    Text(run.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(
                    "${run.injectedEvents}/${run.totalEvents} events — ${formatDate(run.startedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (run.configName.isNotBlank() && run.configName != "Unnamed") {
                    Text(
                        "Config: ${run.configName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row {
                if (run.crashLog != null) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = "Toggle crash log",
                        )
                    }
                }
                if (showDeleteConfirm) {
                    OutlinedButton(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Confirm")
                    }
                } else {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete run",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (expanded && run.crashLog != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = run.crashLog,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(status: MonkeyRunStatus) {
    val (text, color) = when (status) {
        MonkeyRunStatus.Running -> "Running" to MaterialTheme.colorScheme.primary
        MonkeyRunStatus.Completed -> "Done" to MaterialTheme.colorScheme.primary
        MonkeyRunStatus.Crashed -> "Crash" to MaterialTheme.colorScheme.error
        MonkeyRunStatus.ANR -> "ANR" to MaterialTheme.colorScheme.error
        MonkeyRunStatus.Aborted -> "Aborted" to MaterialTheme.colorScheme.error
        MonkeyRunStatus.Stopped -> "Stopped" to MaterialTheme.colorScheme.tertiary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MonkeyOutputLine.color() = when (this) {
    is MonkeyOutputLine.CrashDetected -> MaterialTheme.colorScheme.error
    is MonkeyOutputLine.ANRDetected -> MaterialTheme.colorScheme.error
    is MonkeyOutputLine.Aborted -> MaterialTheme.colorScheme.error
    is MonkeyOutputLine.Summary -> MaterialTheme.colorScheme.primary
    is MonkeyOutputLine.Event -> MaterialTheme.colorScheme.onSurfaceVariant
    is MonkeyOutputLine.Info -> MaterialTheme.colorScheme.onSurface
}

private fun formatDate(timestamp: Long): String =
    DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp))

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, HH:mm").withZone(ZoneId.systemDefault())

private val AVAILABLE_CATEGORIES = listOf(
    "android.intent.category.LAUNCHER",
    "android.intent.category.MONKEY",
    "android.intent.category.DEFAULT",
)
