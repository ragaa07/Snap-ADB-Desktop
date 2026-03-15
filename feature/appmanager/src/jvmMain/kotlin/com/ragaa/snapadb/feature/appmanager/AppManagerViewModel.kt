package com.ragaa.snapadb.feature.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ClearAppData
import com.ragaa.snapadb.core.adb.command.ForceStopApp
import com.ragaa.snapadb.core.adb.command.GetAppPermissions
import com.ragaa.snapadb.core.adb.LabelResolver
import com.ragaa.snapadb.core.adb.command.GetPackageInfo
import com.ragaa.snapadb.core.adb.command.GrantPermission
import com.ragaa.snapadb.core.adb.command.InstallApp
import com.ragaa.snapadb.core.adb.command.LaunchApp
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.RevokePermission
import com.ragaa.snapadb.core.adb.command.UninstallApp
import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.model.AppPermission
import com.ragaa.snapadb.core.adb.model.AppSort
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppManagerViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
    private val labelResolver: LabelResolver,
) : ViewModel() {

    private val _state = MutableStateFlow<AppManagerState>(AppManagerState.NoDevice)
    val state: StateFlow<AppManagerState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sort = MutableStateFlow(AppSort.NAME_ASC)
    val sort: StateFlow<AppSort> = _sort.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _permissions = MutableStateFlow<List<AppPermission>>(emptyList())
    val permissions: StateFlow<List<AppPermission>> = _permissions.asStateFlow()

    private val mutex = Mutex()
    private var allApps = emptyList<AppInfo>()
    private var currentSerial: String? = null

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                mutex.withLock {
                    allApps = emptyList()
                    currentSerial = device?.serial
                    _actionResult.value = null
                    _selectionMode.value = false
                    _selectedPackages.value = emptySet()
                    _permissions.value = emptyList()
                }
                if (device == null) {
                    _state.value = AppManagerState.NoDevice
                } else {
                    loadApps(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: AppManagerIntent) {
        when (intent) {
            is AppManagerIntent.Refresh -> refresh()
            is AppManagerIntent.UpdateSearch -> {
                _searchQuery.value = intent.query
                applyFilters()
            }
            is AppManagerIntent.UpdateSort -> {
                _sort.value = intent.sort
                applyFilters()
            }
            is AppManagerIntent.Uninstall -> uninstallApp(intent.packageName)
            is AppManagerIntent.ForceStop -> forceStopApp(intent.packageName)
            is AppManagerIntent.ClearData -> clearAppData(intent.packageName)
            is AppManagerIntent.Install -> installApp(intent.localApkPath, intent.allowDowngrade)
            is AppManagerIntent.Launch -> launchApp(intent.packageName)
            is AppManagerIntent.GetDetails -> getAppDetails(intent.packageName)
            is AppManagerIntent.DismissResult -> _actionResult.value = null
            is AppManagerIntent.DismissDetails -> {
                val current = _state.value as? AppManagerState.Loaded ?: return
                _state.value = current.copy(selectedAppDetails = null)
                _permissions.value = emptyList()
            }
            is AppManagerIntent.ToggleSelectionMode -> toggleSelectionMode()
            is AppManagerIntent.ToggleSelection -> toggleSelection(intent.packageName)
            is AppManagerIntent.SelectAll -> selectAllVisible()
            is AppManagerIntent.ClearSelection -> {
                _selectedPackages.value = emptySet()
                _selectionMode.value = false
            }
            is AppManagerIntent.BatchUninstall -> batchUninstall()
            is AppManagerIntent.BatchForceStop -> batchForceStop()
            is AppManagerIntent.BatchClearData -> batchClearData()
            is AppManagerIntent.LoadPermissions -> loadPermissions(intent.packageName)
            is AppManagerIntent.TogglePermission -> togglePermission(intent.packageName, intent.permission)
        }
    }

    private fun refresh() {
        val serial = currentSerial ?: return
        viewModelScope.launch { loadApps(serial) }
    }

    private suspend fun loadApps(serial: String) {
        _state.value = AppManagerState.Loading
        withContext(dispatchers.io) {
            adbClient.execute(ListPackages(), serial)
        }
            .onSuccess { apps ->
                mutex.withLock {
                    allApps = apps.sortedBy { it.packageName.lowercase() }
                }
                applyFilters()
                fetchLabelsInBackground(serial)
            }
            .onFailure { e ->
                _state.value = AppManagerState.Error(e.message ?: "Failed to load apps")
            }
    }

    private fun fetchLabelsInBackground(serial: String) {
        if (!labelResolver.isAvailable()) return

        viewModelScope.launch {
            val appsSnapshot = mutex.withLock { allApps.toList() }
            // Only fetch labels for apps with APK paths, prioritize user apps
            val appsToResolve = appsSnapshot
                .filter { it.apkPath.isNotEmpty() && it.appLabel.isEmpty() }
                .sortedBy { it.isSystemApp } // user apps first

            appsToResolve.chunked(10).forEach { batch ->
                val results = withContext(dispatchers.io) {
                    batch.map { app ->
                        async {
                            val label = labelResolver.resolveLabel(app.apkPath, serial)
                            app.packageName to label
                        }
                    }.awaitAll()
                }

                val labelMap = results.filter { it.second.isNotEmpty() }.toMap()
                if (labelMap.isNotEmpty()) {
                    mutex.withLock {
                        allApps = allApps.map { app ->
                            labelMap[app.packageName]?.let { label -> app.copy(appLabel = label) } ?: app
                        }
                    }
                    applyFilters()
                }
            }
        }
    }

    private fun applyFilters() {
        val query = _searchQuery.value.lowercase()
        val sortType = _sort.value

        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.packageName.lowercase().contains(query) ||
                        app.appLabel.lowercase().contains(query)
            }
        }

        val sorted = when (sortType) {
            AppSort.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            AppSort.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            AppSort.RECENTLY_INSTALLED -> filtered.sortedByDescending { it.firstInstallTime }
        }

        val selectedDetails = (_state.value as? AppManagerState.Loaded)?.selectedAppDetails

        _state.value = AppManagerState.Loaded(
            apps = sorted,
            totalCount = allApps.size,
            selectedAppDetails = selectedDetails,
        )
    }

    private fun launchApp(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(LaunchApp(packageName), serial)
            }
                .onSuccess { _actionResult.value = ActionResult.Success("Launched $packageName") }
                .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to launch: ${e.message}") }
        }
    }

    private fun uninstallApp(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(UninstallApp(packageName), serial)
            }
                .onSuccess {
                    _actionResult.value = ActionResult.Success("Uninstalled $packageName")
                    loadApps(serial)
                }
                .onFailure { e ->
                    _actionResult.value = ActionResult.Failure("Failed to uninstall: ${e.message}")
                }
        }
    }

    private fun forceStopApp(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ForceStopApp(packageName), serial)
            }
                .onSuccess { _actionResult.value = ActionResult.Success("Force stopped $packageName") }
                .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to force stop: ${e.message}") }
        }
    }

    private fun clearAppData(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ClearAppData(packageName), serial)
            }
                .onSuccess { _actionResult.value = ActionResult.Success("Cleared data for $packageName") }
                .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to clear data: ${e.message}") }
        }
    }

    private fun installApp(localApkPath: String, allowDowngrade: Boolean) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            _actionResult.value = ActionResult.Success("Installing...")
            withContext(dispatchers.io) {
                adbClient.execute(InstallApp(localApkPath, allowDowngrade), serial)
            }
                .onSuccess {
                    _actionResult.value = ActionResult.Success("Installed successfully")
                    loadApps(serial)
                }
                .onFailure { e ->
                    _actionResult.value = ActionResult.Failure("Install failed: ${e.message}")
                }
        }
    }

    private fun getAppDetails(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(GetPackageInfo(packageName), serial)
            }
                .onSuccess { info ->
                    val current = _state.value as? AppManagerState.Loaded ?: return@launch
                    _state.value = current.copy(selectedAppDetails = info)
                }
                .onFailure { e ->
                    _actionResult.value = ActionResult.Failure("Failed to get details: ${e.message}")
                }
        }
    }

    private fun toggleSelectionMode() {
        val newMode = !_selectionMode.value
        _selectionMode.value = newMode
        if (!newMode) {
            _selectedPackages.value = emptySet()
        }
    }

    private fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
    }

    private fun selectAllVisible() {
        val loaded = _state.value as? AppManagerState.Loaded ?: return
        _selectedPackages.value = loaded.apps.map { it.packageName }.toSet()
    }

    private fun batchUninstall() {
        val serial = currentSerial ?: return
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                withContext(dispatchers.io) {
                    adbClient.execute(UninstallApp(pkg), serial)
                }
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            _selectionMode.value = false
            _selectedPackages.value = emptySet()
            _actionResult.value = ActionResult.Success("Uninstalled $successCount apps" +
                    if (failCount > 0) ", $failCount failed" else "")
            loadApps(serial)
        }
    }

    private fun batchForceStop() {
        val serial = currentSerial ?: return
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            for (pkg in packages) {
                withContext(dispatchers.io) {
                    adbClient.execute(ForceStopApp(pkg), serial)
                }.onSuccess { successCount++ }
            }
            _selectionMode.value = false
            _selectedPackages.value = emptySet()
            _actionResult.value = ActionResult.Success("Force stopped $successCount apps")
        }
    }

    private fun batchClearData() {
        val serial = currentSerial ?: return
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            for (pkg in packages) {
                withContext(dispatchers.io) {
                    adbClient.execute(ClearAppData(pkg), serial)
                }.onSuccess { successCount++ }
            }
            _selectionMode.value = false
            _selectedPackages.value = emptySet()
            _actionResult.value = ActionResult.Success("Cleared data for $successCount apps")
        }
    }

    private fun loadPermissions(packageName: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(GetAppPermissions(packageName), serial)
            }
                .onSuccess { _permissions.value = it }
                .onFailure { _permissions.value = emptyList() }
        }
    }

    private fun togglePermission(packageName: String, permission: AppPermission) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            val command = if (permission.isGranted) {
                RevokePermission(packageName, permission.name)
            } else {
                GrantPermission(packageName, permission.name)
            }
            withContext(dispatchers.io) {
                adbClient.execute(command, serial)
            }
                .onSuccess {
                    _actionResult.value = ActionResult.Success(
                        if (permission.isGranted) "Revoked ${permission.name}" else "Granted ${permission.name}"
                    )
                    loadPermissions(packageName)
                }
                .onFailure { e ->
                    _actionResult.value = ActionResult.Failure("Failed: ${e.message}")
                }
        }
    }
}

sealed class AppManagerState {
    data object NoDevice : AppManagerState()
    data object Loading : AppManagerState()
    data class Error(val message: String) : AppManagerState()
    data class Loaded(
        val apps: List<AppInfo>,
        val totalCount: Int,
        val selectedAppDetails: AppInfo? = null,
    ) : AppManagerState()
}

sealed class AppManagerIntent {
    data object Refresh : AppManagerIntent()
    data class UpdateSearch(val query: String) : AppManagerIntent()
    data class UpdateSort(val sort: AppSort) : AppManagerIntent()
    data class Uninstall(val packageName: String) : AppManagerIntent()
    data class ForceStop(val packageName: String) : AppManagerIntent()
    data class ClearData(val packageName: String) : AppManagerIntent()
    data class Install(val localApkPath: String, val allowDowngrade: Boolean = false) : AppManagerIntent()
    data class Launch(val packageName: String) : AppManagerIntent()
    data class GetDetails(val packageName: String) : AppManagerIntent()
    data object DismissResult : AppManagerIntent()
    data object DismissDetails : AppManagerIntent()
    data object ToggleSelectionMode : AppManagerIntent()
    data class ToggleSelection(val packageName: String) : AppManagerIntent()
    data object SelectAll : AppManagerIntent()
    data object ClearSelection : AppManagerIntent()
    data object BatchUninstall : AppManagerIntent()
    data object BatchForceStop : AppManagerIntent()
    data object BatchClearData : AppManagerIntent()
    data class LoadPermissions(val packageName: String) : AppManagerIntent()
    data class TogglePermission(val packageName: String, val permission: AppPermission) : AppManagerIntent()
}

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Failure(val message: String) : ActionResult()
}
