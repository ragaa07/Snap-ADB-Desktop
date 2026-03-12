package com.ragaa.snapadb.feature.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ClearAppData
import com.ragaa.snapadb.core.adb.command.ForceStopApp
import com.ragaa.snapadb.core.adb.command.GetPackageInfo
import com.ragaa.snapadb.core.adb.command.InstallApp
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.UninstallApp
import com.ragaa.snapadb.core.adb.model.AppFilter
import com.ragaa.snapadb.core.adb.model.AppInfo
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
) : ViewModel() {

    private val _state = MutableStateFlow<AppManagerState>(AppManagerState.NoDevice)
    val state: StateFlow<AppManagerState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(AppFilter.ALL)
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

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
            is AppManagerIntent.UpdateFilter -> {
                _filter.value = intent.filter
                applyFilters()
            }
            is AppManagerIntent.Uninstall -> uninstallApp(intent.packageName)
            is AppManagerIntent.ForceStop -> forceStopApp(intent.packageName)
            is AppManagerIntent.ClearData -> clearAppData(intent.packageName)
            is AppManagerIntent.Install -> installApp(intent.localApkPath)
            is AppManagerIntent.GetDetails -> getAppDetails(intent.packageName)
            is AppManagerIntent.DismissResult -> _actionResult.value = null
            is AppManagerIntent.DismissDetails -> {
                val current = _state.value as? AppManagerState.Loaded ?: return
                _state.value = current.copy(selectedAppDetails = null)
            }
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
            }
            .onFailure { e ->
                _state.value = AppManagerState.Error(e.message ?: "Failed to load apps")
            }
    }

    private fun applyFilters() {
        val query = _searchQuery.value.lowercase()
        val filterType = _filter.value

        val filtered = allApps.filter { app ->
            val matchesSearch = query.isEmpty() || app.packageName.lowercase().contains(query)
            val matchesFilter = when (filterType) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
            }
            matchesSearch && matchesFilter
        }

        val selectedDetails = (_state.value as? AppManagerState.Loaded)?.selectedAppDetails

        _state.value = AppManagerState.Loaded(
            apps = filtered,
            totalCount = allApps.size,
            selectedAppDetails = selectedDetails,
        )
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

    private fun installApp(localApkPath: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            _actionResult.value = ActionResult.Success("Installing...")
            withContext(dispatchers.io) {
                adbClient.execute(InstallApp(localApkPath), serial)
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
    data class UpdateFilter(val filter: AppFilter) : AppManagerIntent()
    data class Uninstall(val packageName: String) : AppManagerIntent()
    data class ForceStop(val packageName: String) : AppManagerIntent()
    data class ClearData(val packageName: String) : AppManagerIntent()
    data class Install(val localApkPath: String) : AppManagerIntent()
    data class GetDetails(val packageName: String) : AppManagerIntent()
    data object DismissResult : AppManagerIntent()
    data object DismissDetails : AppManagerIntent()
}

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Failure(val message: String) : ActionResult()
}
