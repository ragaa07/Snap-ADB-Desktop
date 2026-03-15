package com.ragaa.snapadb.feature.shell.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ragaa.snapadb.core.adb.model.LogLevel
import com.ragaa.snapadb.core.adb.model.LogcatEntry
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import kotlinx.coroutines.delay
import java.io.File
import javax.swing.JFileChooser

@Composable
fun LogcatScreen(viewModel: LogcatViewModel) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is LogcatState.NoDevice -> NoDeviceState("Connect a device to view logcat")
            is LogcatState.Error -> ErrorState(s.message, onRetry = { viewModel.onIntent(LogcatIntent.Retry) })
            is LogcatState.Streaming -> StreamingContent(
                state = s,
                onIntent = viewModel::onIntent,
            )
        }

        // Snackbar for action results
        actionResult?.let { message ->
            LaunchedEffect(message) {
                delay(3000)
                viewModel.onIntent(LogcatIntent.DismissActionResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onIntent(LogcatIntent.DismissActionResult) }) {
                        Text("Dismiss")
                    }
                },
            ) {
                Text(message)
            }
        }
    }
}

@Composable
private fun StreamingContent(
    state: LogcatState.Streaming,
    onIntent: (LogcatIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadMenu by remember { mutableStateOf(false) }
    val collapsedCrashGroups = remember { mutableStateMapOf<Long, Boolean>() }

    // Build crash entry lookup
    val crashEntryIds = remember(state.crashGroups) {
        state.crashGroups.flatMap { it.entryIds }.toSet()
    }
    val crashGroupByStartId = remember(state.crashGroups) {
        state.crashGroups.associateBy { it.startEntryId }
    }
    // Entries that are stack trace lines (not the start) of collapsed groups
    val hiddenEntryIds = remember(state.crashGroups, collapsedCrashGroups.toMap()) {
        val hidden = mutableSetOf<Long>()
        for (group in state.crashGroups) {
            if (collapsedCrashGroups[group.startEntryId] == true) {
                hidden.addAll(group.entryIds - group.startEntryId)
            }
        }
        hidden
    }

    val visibleEntries = remember(state.entries, hiddenEntryIds) {
        if (hiddenEntryIds.isEmpty()) state.entries
        else state.entries.filter { it.id !in hiddenEntryIds }
    }

    LaunchedEffect(visibleEntries.size, state.isPaused) {
        if (!state.isPaused && visibleEntries.isNotEmpty()) {
            listState.animateScrollToItem(visibleEntries.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onIntent(LogcatIntent.TogglePause) }) {
                Icon(
                    if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                )
            }
            IconButton(onClick = { onIntent(LogcatIntent.ClearLog) }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear")
            }
            IconButton(onClick = {
                javax.swing.SwingUtilities.invokeLater {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Export logcat"
                        selectedFile = File("logcat_export.txt")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onIntent(LogcatIntent.ExportToFile(chooser.selectedFile))
                    }
                }
            }) {
                Icon(Icons.Outlined.SaveAs, contentDescription = "Export")
            }

            // Copy to clipboard
            IconButton(onClick = { onIntent(LogcatIntent.CopyToClipboard) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy to clipboard")
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Save filter
            IconButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Outlined.Save, contentDescription = "Save filter")
            }

            // Load filter
            Box {
                TextButton(onClick = { showLoadMenu = true }) {
                    Text("Presets")
                }
                DropdownMenu(expanded = showLoadMenu, onDismissRequest = { showLoadMenu = false }) {
                    if (state.savedFilters.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No saved filters") },
                            onClick = { showLoadMenu = false },
                            enabled = false,
                        )
                    }
                    state.savedFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(filter.name)
                                    IconButton(
                                        onClick = {
                                            onIntent(LogcatIntent.DeleteFilter(filter.id))
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onIntent(LogcatIntent.LoadFilter(filter.id))
                                showLoadMenu = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Bookmarks filter chip
            FilterChip(
                selected = state.showBookmarksOnly,
                onClick = { onIntent(LogcatIntent.ToggleShowBookmarksOnly) },
                label = {
                    val count = state.bookmarkedIds.size
                    Text(if (count > 0) "Bookmarks ($count)" else "Bookmarks", fontSize = 12.sp)
                },
                leadingIcon = {
                    Icon(
                        if (state.showBookmarksOnly) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "${state.entries.size} / ${state.totalCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Level chips
            LogLevel.entries.filter { it != LogLevel.SILENT }.forEach { level ->
                FilterChip(
                    selected = state.minLevel == level,
                    onClick = { onIntent(LogcatIntent.SetMinLevel(level)) },
                    label = { Text(level.label, fontSize = 12.sp) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTextField(
                value = state.tagFilter,
                onValueChange = { onIntent(LogcatIntent.SetTagFilter(it)) },
                placeholder = "Tags (comma-separated)",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = state.packageFilter,
                onValueChange = { onIntent(LogcatIntent.SetPackageFilter(it)) },
                placeholder = "Package/PID",
                modifier = Modifier.weight(1f),
            )

            // Search field with regex toggle
            val searchBorderColor = if (state.isRegexSearch && !state.isRegexValid) {
                SnapAdbTheme.colors.logError
            } else {
                Color.Unspecified
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactTextField(
                    value = state.searchText,
                    onValueChange = { onIntent(LogcatIntent.SetSearchText(it)) },
                    placeholder = if (state.isRegexSearch) "Regex search" else "Search",
                    modifier = Modifier.weight(1f).then(
                        if (searchBorderColor != Color.Unspecified) {
                            Modifier.border(1.dp, searchBorderColor, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        }
                    ),
                )
                // Regex toggle button
                TextButton(
                    onClick = { onIntent(LogcatIntent.ToggleRegexSearch) },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(
                        ".*",
                        fontWeight = if (state.isRegexSearch) FontWeight.Bold else FontWeight.Normal,
                        color = if (state.isRegexSearch) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Log output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(8.dp),
                )
                .padding(8.dp),
        ) {
            itemsIndexed(visibleEntries, key = { _, entry -> entry.id }) { _, entry ->
                val isBookmarked = entry.id in state.bookmarkedIds
                val isCrashEntry = entry.id in crashEntryIds
                val crashGroup = crashGroupByStartId[entry.id]
                val isCollapsed = collapsedCrashGroups[entry.id] == true

                LogcatRow(
                    entry = entry,
                    isBookmarked = isBookmarked,
                    isCrashEntry = isCrashEntry,
                    crashGroup = crashGroup,
                    isCollapsed = isCollapsed,
                    onToggleBookmark = { onIntent(LogcatIntent.ToggleBookmark(entry.id)) },
                    onToggleCollapse = crashGroup?.let {
                        {
                            collapsedCrashGroups[entry.id] = !isCollapsed
                        }
                    },
                )
            }
        }
    }

    if (showSaveDialog) {
        SaveFilterDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onIntent(LogcatIntent.SaveFilter(name))
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun LogcatRow(
    entry: LogcatEntry,
    isBookmarked: Boolean,
    isCrashEntry: Boolean,
    crashGroup: CrashGroup?,
    isCollapsed: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleCollapse: (() -> Unit)?,
) {
    val colors = SnapAdbTheme.colors
    val levelColor = when (entry.level) {
        LogLevel.VERBOSE -> colors.logVerbose
        LogLevel.DEBUG -> colors.logDebug
        LogLevel.INFO -> colors.logInfo
        LogLevel.WARN -> colors.logWarn
        LogLevel.ERROR -> colors.logError
        LogLevel.FATAL -> colors.logFatal
        LogLevel.SILENT -> colors.logVerbose
    }

    val crashBg = if (isCrashEntry) colors.logError.copy(alpha = 0.1f) else Color.Transparent
    val bookmarkBorderColor = if (isBookmarked) Color(0xFFFFC107) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(crashBg)
            .then(
                if (isBookmarked) {
                    Modifier.drawBehind {
                        drawLine(
                            color = bookmarkBorderColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = if (isBookmarked) 4.dp else 0.dp, top = 0.5.dp, bottom = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Crash group expand/collapse chevron
        if (onToggleCollapse != null) {
            Icon(
                if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (isCollapsed) "Expand crash" else "Collapse crash",
                modifier = Modifier.size(16.dp).clickable { onToggleCollapse() },
                tint = colors.logError,
            )
        }

        // Bookmark toggle
        Icon(
            if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
            modifier = Modifier.size(14.dp).clickable { onToggleBookmark() },
            tint = if (isBookmarked) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )

        Spacer(modifier = Modifier.width(4.dp))

        val displayText = if (crashGroup != null && isCollapsed) {
            "${entry.timestamp} ${entry.level.label}/${entry.tag}: ${crashGroup.summary} [${crashGroup.entryIds.size} lines]"
        } else {
            "${entry.timestamp} ${entry.level.label}/${entry.tag}(${entry.pid}): ${entry.message}"
        }

        Text(
            text = displayText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = levelColor,
            ),
            modifier = Modifier.weight(1f),
            maxLines = 3,
        )
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        singleLine = true,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
}

@Composable
private fun SaveFilterDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Filter Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Filter name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
