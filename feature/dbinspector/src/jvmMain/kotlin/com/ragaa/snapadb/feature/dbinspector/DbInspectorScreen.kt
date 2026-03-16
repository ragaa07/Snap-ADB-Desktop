package com.ragaa.snapadb.feature.dbinspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.ragaa.snapadb.core.adb.command.DatabaseFileInfo
import com.ragaa.snapadb.core.ui.components.ErrorState
import com.ragaa.snapadb.core.ui.components.LoadingState
import com.ragaa.snapadb.core.ui.components.NoDeviceState
import com.ragaa.snapadb.feature.dbinspector.model.ColumnInfo
import com.ragaa.snapadb.feature.dbinspector.model.QueryResult
import com.ragaa.snapadb.feature.dbinspector.model.TableInfo
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DbInspectorScreen(viewModel: DbInspectorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val suggestions by viewModel.packageSuggestions.collectAsState()

    when (val s = state) {
        is DbInspectorState.NoDevice -> NoDeviceState("Connect a device to inspect databases")
        is DbInspectorState.NoApp -> PackageInputScreen(
            suggestions = suggestions,
            onQueryChanged = { viewModel.onIntent(DbInspectorIntent.UpdatePackageQuery(it)) },
            onPackageSelected = { viewModel.onIntent(DbInspectorIntent.SetPackageName(it)) },
        )
        is DbInspectorState.Loading -> LoadingState()
        is DbInspectorState.Error -> ErrorState(
            message = s.message,
            onRetry = { viewModel.onIntent(DbInspectorIntent.Refresh) },
        )
        is DbInspectorState.Loaded -> LoadedContent(
            state = s,
            actionResult = actionResult,
            suggestions = suggestions,
            onQueryChanged = { viewModel.onIntent(DbInspectorIntent.UpdatePackageQuery(it)) },
            onIntent = viewModel::onIntent,
            onExportResult = { viewModel.getExportResult() },
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
            Text("Database Inspector", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter a debuggable app's package name to inspect its SQLite databases",
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
                    onDismissRequest = { onQueryChanged("") },
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
                Text("Load Databases")
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: DbInspectorState.Loaded,
    actionResult: DbInspectorResult?,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onIntent: (DbInspectorIntent) -> Unit,
    onExportResult: () -> QueryResult?,
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
                onExportResult = onExportResult,
                onReportError = onReportError,
                onReportSuccess = onReportSuccess,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Content
            Row(modifier = Modifier.fillMaxSize()) {
                // Left panel: database list + table list
                LeftPanel(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )

                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp))

                // Right panel
                RightPanel(
                    state = state,
                    onIntent = onIntent,
                    onExportResult = onExportResult,
                    onReportError = onReportError,
                    onReportSuccess = onReportSuccess,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Snackbar
        actionResult?.let { result ->
            LaunchedEffect(result) {
                delay(4000)
                onIntent(DbInspectorIntent.DismissResult)
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { onIntent(DbInspectorIntent.DismissResult) }) { Text("Dismiss") }
                },
                containerColor = when (result) {
                    is DbInspectorResult.Success -> MaterialTheme.colorScheme.primaryContainer
                    is DbInspectorResult.Failure -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when (result) {
                    is DbInspectorResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is DbInspectorResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    when (result) {
                        is DbInspectorResult.Success -> result.message
                        is DbInspectorResult.Failure -> result.message
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    state: DbInspectorState.Loaded,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onIntent: (DbInspectorIntent) -> Unit,
    onExportResult: () -> QueryResult?,
    onReportError: (String) -> Unit,
    onReportSuccess: (String) -> Unit,
) {
    var packageQuery by remember(state.packageName) { mutableStateOf(state.packageName) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.selectedDb != null) {
            IconButton(onClick = { onIntent(DbInspectorIntent.GoBack) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
            }
        }

        Text("DB Inspector", style = MaterialTheme.typography.headlineMedium)

        if (state.selectedDb != null) {
            Text(
                "/ ${state.selectedDb}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Export CSV — available on all tabs when there's data
        if (state.selectedDb != null) {
            IconButton(onClick = {
                val result = onExportResult() ?: return@IconButton
                if (result.rows.isEmpty()) {
                    onReportError("No data to export")
                    return@IconButton
                }
                SwingUtilities.invokeLater {
                    try {
                        val defaultName = when (state.activeTab) {
                            DbInspectorTab.SCHEMA -> "${state.selectedTable ?: "schema"}_schema.csv"
                            DbInspectorTab.DATA -> "${state.selectedTable ?: "data"}.csv"
                            DbInspectorTab.QUERY -> "query_result.csv"
                        }
                        val chooser = JFileChooser().apply {
                            fileFilter = FileNameExtensionFilter("CSV files", "csv")
                            selectedFile = java.io.File(defaultName)
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            CsvExporter.export(result, chooser.selectedFile)
                            onReportSuccess("Exported to ${chooser.selectedFile.name}")
                        }
                    } catch (e: Exception) {
                        onReportError("Export failed: ${e.message}")
                    }
                }
            }) {
                Icon(Icons.Outlined.Download, contentDescription = "Export CSV", modifier = Modifier.size(20.dp))
            }
        }

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
                            onIntent(DbInspectorIntent.SetPackageName(pkg))
                        },
                    )
                }
            }
        }

        IconButton(onClick = {
            if (state.selectedDb != null) onIntent(DbInspectorIntent.RefreshDatabase)
            else onIntent(DbInspectorIntent.SetPackageName(packageQuery))
        }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LeftPanel(
    state: DbInspectorState.Loaded,
    onIntent: (DbInspectorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Database list
        Text(
            "Databases (${state.databases.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (state.databases.isEmpty()) {
            Text(
                "No databases found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = if (state.selectedDb != null) Modifier.weight(0.4f) else Modifier.weight(1f),
            ) {
                items(state.databases, key = { it.name }) { db ->
                    DatabaseRow(
                        db = db,
                        isSelected = db.name == state.selectedDb,
                        onClick = { onIntent(DbInspectorIntent.SelectDatabase(db.name)) },
                    )
                }
            }
        }

        // Table list (when a database is selected)
        if (state.selectedDb != null && state.tables.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tables (${state.tables.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(0.6f),
            ) {
                items(state.tables, key = { it.name }) { table ->
                    TableRow(
                        table = table,
                        isSelected = table.name == state.selectedTable,
                        onClick = { onIntent(DbInspectorIntent.SelectTable(table.name)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseRow(
    db: DatabaseFileInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
            Icons.Outlined.Storage,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                db.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                formatSize(db.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TableRow(
    table: TableInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.TableChart,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            table.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
        Text(
            "${table.rowCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RightPanel(
    state: DbInspectorState.Loaded,
    onIntent: (DbInspectorIntent) -> Unit,
    onExportResult: () -> QueryResult?,
    onReportError: (String) -> Unit,
    onReportSuccess: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 8.dp)) {
        if (state.selectedDb == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.databases.isEmpty()) "No databases found for this app"
                    else "Select a database to inspect",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        if (state.selectedTable == null && state.activeTab != DbInspectorTab.QUERY) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.tables.isEmpty()) "No tables in this database"
                    else "Select a table to view its data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        // Tab row
        TabRow(
            selectedTabIndex = state.activeTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DbInspectorTab.entries.forEach { tab ->
                Tab(
                    selected = state.activeTab == tab,
                    onClick = { onIntent(DbInspectorIntent.SwitchTab(tab)) },
                    text = { Text(tab.name) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoadingData) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        when (state.activeTab) {
            DbInspectorTab.SCHEMA -> SchemaTab(state)
            DbInspectorTab.DATA -> DataTab(state, onIntent)
            DbInspectorTab.QUERY -> QueryTab(
                state = state,
                onIntent = onIntent,
                onExportResult = onExportResult,
                onReportError = onReportError,
                onReportSuccess = onReportSuccess,
            )
        }
    }
}

@Composable
private fun SchemaTab(state: DbInspectorState.Loaded) {
    val tableInfo = state.tables.find { it.name == state.selectedTable } ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Table: ${tableInfo.name} (${tableInfo.rowCount} rows)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Column header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("#", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
            Text("Name", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold)
            Text("Not Null", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold)
            Text("Default", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold)
            Text("PK", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        }

        LazyColumn {
            items(tableInfo.columns, key = { it.cid }) { col ->
                SchemaRow(col)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun SchemaRow(col: ColumnInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${col.cid}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            col.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (col.primaryKey) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            col.type.ifEmpty { "ANY" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            if (col.notNull) "YES" else "no",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(70.dp),
            color = if (col.notNull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            col.defaultValue ?: "NULL",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (col.primaryKey) "PK" else "",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DataTab(
    state: DbInspectorState.Loaded,
    onIntent: (DbInspectorIntent) -> Unit,
) {
    val data = state.tableData
    if (data == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data loaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val tableInfo = state.tables.find { it.name == state.selectedTable }
    val totalRows = tableInfo?.rowCount ?: 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "${data.rowCount} of $totalRows rows loaded",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        DataGrid(result = data, modifier = Modifier.weight(1f))

        if (data.rowCount < totalRows) {
            Button(
                onClick = { onIntent(DbInspectorIntent.LoadMoreData) },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) {
                Text("Load More")
            }
        }
    }
}

@Composable
private fun QueryTab(
    state: DbInspectorState.Loaded,
    onIntent: (DbInspectorIntent) -> Unit,
    onExportResult: () -> QueryResult?,
    onReportError: (String) -> Unit,
    onReportSuccess: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // SQL input
        OutlinedTextField(
            value = state.queryText,
            onValueChange = { onIntent(DbInspectorIntent.UpdateQueryText(it)) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("SELECT * FROM table_name LIMIT 100") },
            label = { Text("SQL Query") },
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onIntent(DbInspectorIntent.ExecuteQuery) },
                enabled = state.queryText.isNotBlank(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Execute")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Query result
        val queryResult = state.queryResult
        if (queryResult != null) {
            if (queryResult.error != null) {
                Text(
                    "Error: ${queryResult.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Text(
                    "${queryResult.rowCount} row(s) in ${queryResult.executionTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                DataGrid(result = queryResult, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DataGrid(result: QueryResult, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.horizontalScroll(scrollState)) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                result.columns.forEach { col ->
                    Text(
                        col,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(150.dp).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Data rows
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(result.rows.size) { rowIndex ->
                    val row = result.rows[rowIndex]
                    val bgColor = if (rowIndex % 2 == 0) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                    Row(
                        modifier = Modifier
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        row.forEach { value ->
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(150.dp).padding(horizontal = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = when {
                                    value == "NULL" -> MaterialTheme.colorScheme.onSurfaceVariant
                                    value.startsWith("[BLOB:") -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}
