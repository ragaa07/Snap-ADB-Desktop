package com.ragaa.snapadb.feature.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ClearAppData
import com.ragaa.snapadb.core.adb.command.ForceStopApp
import com.ragaa.snapadb.core.adb.command.GetAppPermissions
import com.ragaa.snapadb.core.adb.command.GetPackageInfo
import com.ragaa.snapadb.core.adb.command.GrantPermission
import com.ragaa.snapadb.core.adb.command.InstallApp
import com.ragaa.snapadb.core.adb.command.LaunchApp
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.RevokePermission
import com.ragaa.snapadb.core.adb.command.UninstallApp
import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.model.AppPermission
import com.ragaa.snapadb.core.database.AppLabelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
    private val appLabelRepository: AppLabelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppManagerState>(AppManagerState.NoDevice)
    val state: StateFlow<AppManagerState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _permissions = MutableStateFlow<List<AppPermission>>(emptyList())
    val permissions: StateFlow<List<AppPermission>> = _permissions.asStateFlow()

    // Raw apps from device (no labels) — written only by loadApps
    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _currentSerial = MutableStateFlow<String?>(null)

    init {
        // React to device changes
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                _rawApps.value = emptyList()
                _currentSerial.value = device?.serial
                _actionResult.value = null
                _selectionMode.value = false
                _selectedPackages.value = emptySet()
                _permissions.value = emptyList()

                if (device == null) {
                    _state.value = AppManagerState.NoDevice
                } else {
                    loadApps(device.serial)
                }
            }
        }

        // Single reactive pipeline: rawApps + DB labels + searchQuery → UI state
        // Fires whenever ANY of the three sources change
        viewModelScope.launch {
            combine(
                _rawApps,
                appLabelRepository.observeAllLabels(),
                _searchQuery,
            ) { apps, labelsMap, query ->
                Triple(apps, labelsMap, query)
            }.collect { (apps, labelsMap, query) ->
                if (apps.isEmpty()) return@collect

                // 1. Apply labels from DB (single source of truth)
                val labeled = if (labelsMap.isNotEmpty()) {
                    apps.map { app ->
                        labelsMap[app.packageName]?.let { label -> app.copy(appLabel = label) } ?: app
                    }
                } else apps

                // 2. Apply search filter
                val queryLower = query.lowercase()
                val filtered = if (queryLower.isEmpty()) labeled
                else labeled.filter { app ->
                    app.packageName.lowercase().contains(queryLower) ||
                        app.appLabel.lowercase().contains(queryLower)
                }

                // 3. Sort and emit
                val sorted = filtered.sortedBy { it.displayName.lowercase() }
                val selectedDetails = (_state.value as? AppManagerState.Loaded)?.selectedAppDetails

                _state.value = AppManagerState.Loaded(
                    apps = sorted,
                    totalCount = apps.size,
                    selectedAppDetails = selectedDetails,
                )
            }
        }
    }

    fun onIntent(intent: AppManagerIntent) {
        when (intent) {
            is AppManagerIntent.Refresh -> refresh()
            is AppManagerIntent.UpdateSearch -> _searchQuery.value = intent.query
            is AppManagerIntent.Uninstall -> executeAction(intent.packageName) { serial, pkg ->
                adbClient.execute(UninstallApp(pkg), serial)
                    .onSuccess {
                        _actionResult.value = ActionResult.Success("Uninstalled $pkg")
                        loadApps(serial)
                    }
                    .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to uninstall: ${e.message}") }
            }
            is AppManagerIntent.ForceStop -> executeAction(intent.packageName) { serial, pkg ->
                adbClient.execute(ForceStopApp(pkg), serial)
                    .onSuccess { _actionResult.value = ActionResult.Success("Force stopped $pkg") }
                    .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to force stop: ${e.message}") }
            }
            is AppManagerIntent.ClearData -> executeAction(intent.packageName) { serial, pkg ->
                adbClient.execute(ClearAppData(pkg), serial)
                    .onSuccess { _actionResult.value = ActionResult.Success("Cleared data for $pkg") }
                    .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to clear data: ${e.message}") }
            }
            is AppManagerIntent.Install -> installApp(intent.localApkPath, intent.allowDowngrade)
            is AppManagerIntent.Launch -> executeAction(intent.packageName) { serial, pkg ->
                adbClient.execute(LaunchApp(pkg), serial)
                    .onSuccess { _actionResult.value = ActionResult.Success("Launched $pkg") }
                    .onFailure { e -> _actionResult.value = ActionResult.Failure("Failed to launch: ${e.message}") }
            }
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

    // --- Core ---

    private fun refresh() {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch { loadApps(serial) }
    }

    private suspend fun loadApps(serial: String) {
        _state.value = AppManagerState.Loading
        // Reset to empty first so StateFlow always emits on the next set
        _rawApps.value = emptyList()
        withContext(dispatchers.io) {
            adbClient.execute(ListPackages(), serial)
        }
            .onSuccess { apps ->
                _rawApps.value = apps
                // combine pipeline handles labels + filters + state transition
            }
            .onFailure { e ->
                _state.value = AppManagerState.Error(e.message ?: "Failed to load apps")
            }
    }

    // --- Single app actions ---

    private fun executeAction(
        packageName: String,
        block: suspend (serial: String, pkg: String) -> Unit,
    ) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) { block(serial, packageName) }
        }
    }

    private fun installApp(localApkPath: String, allowDowngrade: Boolean) {
        val serial = _currentSerial.value ?: return
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
        val serial = _currentSerial.value ?: return
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

    // --- Selection & batch ---

    private fun toggleSelectionMode() {
        val newMode = !_selectionMode.value
        _selectionMode.value = newMode
        if (!newMode) _selectedPackages.value = emptySet()
    }

    private fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (packageName in current) current - packageName else current + packageName
    }

    private fun selectAllVisible() {
        val loaded = _state.value as? AppManagerState.Loaded ?: return
        _selectedPackages.value = loaded.apps.map { it.packageName }.toSet()
    }

    private fun executeBatch(
        action: suspend (serial: String, pkg: String) -> Result<*>,
        successVerb: String,
        reloadAfter: Boolean = false,
    ) {
        val serial = _currentSerial.value ?: return
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                withContext(dispatchers.io) { action(serial, pkg) }
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            _selectionMode.value = false
            _selectedPackages.value = emptySet()
            _actionResult.value = ActionResult.Success(
                "$successVerb $successCount apps" + if (failCount > 0) ", $failCount failed" else ""
            )
            if (reloadAfter) loadApps(serial)
        }
    }

    private fun batchUninstall() = executeBatch(
        action = { serial, pkg -> adbClient.execute(UninstallApp(pkg), serial) },
        successVerb = "Uninstalled",
        reloadAfter = true,
    )

    private fun batchForceStop() = executeBatch(
        action = { serial, pkg -> adbClient.execute(ForceStopApp(pkg), serial) },
        successVerb = "Force stopped",
    )

    private fun batchClearData() = executeBatch(
        action = { serial, pkg -> adbClient.execute(ClearAppData(pkg), serial) },
        successVerb = "Cleared data for",
    )

    // --- Permissions ---

    private fun loadPermissions(packageName: String) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(GetAppPermissions(packageName), serial)
            }
                .onSuccess { _permissions.value = it }
                .onFailure { _permissions.value = emptyList() }
        }
    }

    private fun togglePermission(packageName: String, permission: AppPermission) {
        val serial = _currentSerial.value ?: return
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