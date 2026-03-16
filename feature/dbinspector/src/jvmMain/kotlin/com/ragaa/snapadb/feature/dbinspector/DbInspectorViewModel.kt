package com.ragaa.snapadb.feature.dbinspector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.CleanupTmpDb
import com.ragaa.snapadb.core.adb.command.CopyDbToTmp
import com.ragaa.snapadb.core.adb.command.DatabaseFileInfo
import com.ragaa.snapadb.core.adb.command.ListDatabases
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.PullFile
import com.ragaa.snapadb.feature.dbinspector.model.QueryResult
import com.ragaa.snapadb.feature.dbinspector.model.TableInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class DbInspectorViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<DbInspectorState>(DbInspectorState.NoDevice)
    val state: StateFlow<DbInspectorState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<DbInspectorResult?>(null)
    val actionResult: StateFlow<DbInspectorResult?> = _actionResult.asStateFlow()

    private val _packageSuggestions = MutableStateFlow<List<String>>(emptyList())
    val packageSuggestions: StateFlow<List<String>> = _packageSuggestions.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())

    private val inspector = SqliteInspector()
    private val tempFiles = CopyOnWriteArrayList<File>()

    init {
        // Clean stale temp files from previous sessions
        viewModelScope.launch(dispatchers.io) {
            val dir = File(System.getProperty("java.io.tmpdir"), "snapadb_dbinspector")
            dir.listFiles()?.forEach { it.delete() }
        }

        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                // Cleanup on device switch — inspector access confined to IO dispatcher
                withContext(dispatchers.io) { inspector.close() }
                deleteTempFiles()
                _currentSerial.value = device?.serial
                _installedPackages.value = emptyList()
                _actionResult.value = null
                _packageSuggestions.value = emptyList()
                if (device == null) {
                    _state.value = DbInspectorState.NoDevice
                } else {
                    _state.value = DbInspectorState.NoApp
                    loadInstalledPackages(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: DbInspectorIntent) {
        when (intent) {
            is DbInspectorIntent.UpdatePackageQuery -> updatePackageQuery(intent.query)
            is DbInspectorIntent.SetPackageName -> setPackageName(intent.packageName)
            is DbInspectorIntent.SelectDatabase -> selectDatabase(intent.dbName)
            is DbInspectorIntent.SelectTable -> selectTable(intent.tableName)
            is DbInspectorIntent.SwitchTab -> switchTab(intent.tab)
            is DbInspectorIntent.UpdateQueryText -> updateQueryText(intent.sql)
            is DbInspectorIntent.ExecuteQuery -> executeQuery()
            is DbInspectorIntent.RefreshDatabase -> refreshDatabase()
            is DbInspectorIntent.Refresh -> refresh()
            is DbInspectorIntent.DismissResult -> _actionResult.value = null
            is DbInspectorIntent.GoBack -> goBack()
            is DbInspectorIntent.LoadMoreData -> loadMoreData()
        }
    }

    private fun loadInstalledPackages(serial: String) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ListPackages(), serial)
            }.onSuccess { apps ->
                _installedPackages.value = apps.map { it.packageName }.sorted()
            }
        }
    }

    private fun updatePackageQuery(query: String) {
        val packages = _installedPackages.value
        _packageSuggestions.value = if (query.length >= 2) {
            packages.filter { it.contains(query, ignoreCase = true) }.take(10)
        } else {
            emptyList()
        }
    }

    private fun setPackageName(packageName: String) {
        val serial = _currentSerial.value ?: return
        _packageSuggestions.value = emptyList()
        _state.value = DbInspectorState.Loading
        viewModelScope.launch {
            val result = withContext(dispatchers.io) {
                try {
                    adbClient.execute(ListDatabases(packageName), serial)
                } catch (e: IllegalArgumentException) {
                    Result.failure(e)
                }
            }
            result.onSuccess { databases ->
                _state.value = DbInspectorState.Loaded(
                    packageName = packageName,
                    databases = databases,
                )
            }.onFailure { e ->
                val message = e.message ?: "Unknown error"
                _state.value = if ("not debuggable" in message || "is not debuggable" in message ||
                    "run-as: Package" in message
                ) {
                    DbInspectorState.Error(
                        "App '$packageName' is not debuggable. Only debug builds can be inspected.",
                        retryPackageName = packageName,
                    )
                } else if ("No such file" in message || "does not exist" in message) {
                    DbInspectorState.Loaded(
                        packageName = packageName,
                        databases = emptyList(),
                    )
                } else if (e is IllegalArgumentException) {
                    DbInspectorState.Error(
                        "Invalid package name: $packageName",
                        retryPackageName = null,
                    )
                } else {
                    DbInspectorState.Error(message, retryPackageName = packageName)
                }
            }
        }
    }

    private fun selectDatabase(dbName: String) {
        val serial = _currentSerial.value ?: return
        val current = _state.value as? DbInspectorState.Loaded ?: return
        val packageName = current.packageName

        _state.value = current.copy(isLoadingData = true)

        viewModelScope.launch {
            val result = withContext(dispatchers.io) {
                pullAndOpenDatabase(serial, packageName, dbName)
            }
            result.onSuccess { tables ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                _state.value = loaded.copy(
                    selectedDb = dbName,
                    tables = tables,
                    selectedTable = null,
                    tableData = null,
                    queryResult = null,
                    activeTab = DbInspectorTab.SCHEMA,
                    isLoadingData = false,
                )
            }.onFailure { e ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                _state.value = loaded.copy(isLoadingData = false)
                _actionResult.value = DbInspectorResult.Failure("Failed to open database: ${e.message}")
            }
        }
    }

    // Must be called from dispatchers.io — all inspector + ADB calls happen here
    private suspend fun pullAndOpenDatabase(
        serial: String,
        packageName: String,
        dbName: String,
    ): Result<List<TableInfo>> {
        return try {
            val tmpName = "snapadb_${UUID.randomUUID()}.db"

            // Step 1: Copy db to /data/local/tmp/ via cat through run-as
            adbClient.execute(CopyDbToTmp(packageName, dbName, tmpName), serial)
                .getOrThrow()

            // Step 2: Pull to local temp dir
            val localDir = File(System.getProperty("java.io.tmpdir"), "snapadb_dbinspector")
            localDir.mkdirs()
            val localFile = File(localDir, tmpName)
            localFile.deleteOnExit()
            tempFiles.add(localFile)

            val remotePath = "/data/local/tmp/$tmpName"
            adbClient.execute(PullFile(remotePath, localFile.absolutePath), serial)
                .getOrThrow()

            // Also pull WAL/SHM (best effort)
            try {
                val walFile = File(localDir, "$tmpName-wal")
                walFile.deleteOnExit()
                tempFiles.add(walFile)
                adbClient.execute(PullFile("$remotePath-wal", walFile.absolutePath), serial)
            } catch (_: Exception) {
            }
            try {
                val shmFile = File(localDir, "$tmpName-shm")
                shmFile.deleteOnExit()
                tempFiles.add(shmFile)
                adbClient.execute(PullFile("$remotePath-shm", shmFile.absolutePath), serial)
            } catch (_: Exception) {
            }

            // Step 3: Cleanup remote tmp
            try {
                adbClient.execute(CleanupTmpDb(tmpName), serial)
            } catch (_: Exception) {
            }

            // Step 4: Open locally (already on IO dispatcher)
            inspector.open(localFile)

            // Step 5: List tables with info
            val tableNames = inspector.listTables()
            val tables = tableNames.map { inspector.getTableInfo(it) }

            Result.success(tables)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun selectTable(tableName: String) {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        current.tables.find { it.name == tableName } ?: return

        _state.value = current.copy(
            selectedTable = tableName,
            isLoadingData = true,
        )

        viewModelScope.launch {
            val data = withContext(dispatchers.io) {
                try {
                    Result.success(inspector.getTableData(tableName, limit = 200, offset = 0))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            data.onSuccess { result ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                _state.value = loaded.copy(
                    tableData = result,
                    isLoadingData = false,
                )
            }.onFailure { e ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                _state.value = loaded.copy(isLoadingData = false)
                _actionResult.value = DbInspectorResult.Failure("Failed to load table data: ${e.message}")
            }
        }
    }

    private fun switchTab(tab: DbInspectorTab) {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        _state.value = current.copy(activeTab = tab)
    }

    private fun updateQueryText(sql: String) {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        _state.value = current.copy(queryText = sql)
    }

    private fun executeQuery() {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        val sql = current.queryText.trim()
        if (sql.isEmpty()) return

        _state.value = current.copy(isLoadingData = true)

        viewModelScope.launch {
            val result = withContext(dispatchers.io) {
                inspector.executeQuery(sql)
            }
            val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
            _state.value = loaded.copy(
                queryResult = result,
                isLoadingData = false,
            )
            if (result.error != null) {
                _actionResult.value = DbInspectorResult.Failure(result.error)
            }
        }
    }

    private fun loadMoreData() {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        if (current.isLoadingData) return // prevent duplicate loads
        val tableName = current.selectedTable ?: return
        val existingData = current.tableData ?: return
        val currentOffset = existingData.rows.size

        _state.value = current.copy(isLoadingData = true)

        viewModelScope.launch {
            val result = withContext(dispatchers.io) {
                try {
                    Result.success(inspector.getTableData(tableName, limit = 200, offset = currentOffset))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.onSuccess { newData ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                val merged = QueryResult(
                    columns = existingData.columns,
                    rows = existingData.rows + newData.rows,
                    rowCount = existingData.rows.size + newData.rows.size,
                    executionTimeMs = newData.executionTimeMs,
                )
                _state.value = loaded.copy(tableData = merged, isLoadingData = false)
            }.onFailure { e ->
                val loaded = _state.value as? DbInspectorState.Loaded ?: return@launch
                _state.value = loaded.copy(isLoadingData = false)
                _actionResult.value = DbInspectorResult.Failure("Failed to load more data: ${e.message}")
            }
        }
    }

    private fun refreshDatabase() {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        val dbName = current.selectedDb ?: return
        selectDatabase(dbName)
    }

    private fun refresh() {
        when (val current = _state.value) {
            is DbInspectorState.Loaded -> setPackageName(current.packageName)
            is DbInspectorState.Error -> {
                val pkg = current.retryPackageName
                if (pkg != null) setPackageName(pkg) else _state.value = DbInspectorState.NoApp
            }
            else -> {}
        }
    }

    private fun goBack() {
        val current = _state.value as? DbInspectorState.Loaded ?: return
        if (current.selectedTable != null) {
            _state.value = current.copy(
                selectedTable = null,
                tableData = null,
                queryResult = null,
            )
        } else if (current.selectedDb != null) {
            viewModelScope.launch {
                withContext(dispatchers.io) { inspector.close() }
            }
            _state.value = current.copy(
                selectedDb = null,
                tables = emptyList(),
                selectedTable = null,
                tableData = null,
                queryResult = null,
            )
        }
    }

    fun getExportResult(): QueryResult? {
        val current = _state.value as? DbInspectorState.Loaded ?: return null
        return when (current.activeTab) {
            DbInspectorTab.QUERY -> current.queryResult
            DbInspectorTab.DATA -> current.tableData
            DbInspectorTab.SCHEMA -> {
                val table = current.tables.find { it.name == current.selectedTable }
                if (table != null) {
                    QueryResult(
                        columns = listOf("cid", "name", "type", "notnull", "default_value", "pk"),
                        rows = table.columns.map { col ->
                            listOf(
                                col.cid.toString(),
                                col.name,
                                col.type,
                                if (col.notNull) "1" else "0",
                                col.defaultValue ?: "NULL",
                                if (col.primaryKey) "1" else "0",
                            )
                        },
                        rowCount = table.columns.size,
                        executionTimeMs = 0,
                    )
                } else null
            }
        }
    }

    fun reportError(message: String) {
        _actionResult.value = DbInspectorResult.Failure(message)
    }

    fun reportSuccess(message: String) {
        _actionResult.value = DbInspectorResult.Success(message)
    }

    private fun deleteTempFiles() {
        val files = ArrayList(tempFiles)
        tempFiles.clear()
        files.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        inspector.close()
        deleteTempFiles()
    }
}

sealed class DbInspectorState {
    data object NoDevice : DbInspectorState()
    data object NoApp : DbInspectorState()
    data object Loading : DbInspectorState()
    data class Error(val message: String, val retryPackageName: String? = null) : DbInspectorState()
    data class Loaded(
        val packageName: String,
        val databases: List<DatabaseFileInfo>,
        val selectedDb: String? = null,
        val tables: List<TableInfo> = emptyList(),
        val selectedTable: String? = null,
        val tableData: QueryResult? = null,
        val queryText: String = "",
        val queryResult: QueryResult? = null,
        val activeTab: DbInspectorTab = DbInspectorTab.SCHEMA,
        val isLoadingData: Boolean = false,
    ) : DbInspectorState()
}

enum class DbInspectorTab { SCHEMA, DATA, QUERY }

sealed class DbInspectorIntent {
    data class UpdatePackageQuery(val query: String) : DbInspectorIntent()
    data class SetPackageName(val packageName: String) : DbInspectorIntent()
    data class SelectDatabase(val dbName: String) : DbInspectorIntent()
    data class SelectTable(val tableName: String) : DbInspectorIntent()
    data class SwitchTab(val tab: DbInspectorTab) : DbInspectorIntent()
    data class UpdateQueryText(val sql: String) : DbInspectorIntent()
    data object ExecuteQuery : DbInspectorIntent()
    data object RefreshDatabase : DbInspectorIntent()
    data object Refresh : DbInspectorIntent()
    data object DismissResult : DbInspectorIntent()
    data object GoBack : DbInspectorIntent()
    data object LoadMoreData : DbInspectorIntent()
}

sealed class DbInspectorResult {
    data class Success(val message: String) : DbInspectorResult()
    data class Failure(val message: String) : DbInspectorResult()
}
