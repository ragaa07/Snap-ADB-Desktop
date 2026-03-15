package com.ragaa.snapadb.feature.appdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.ListSharedPrefsFiles
import com.ragaa.snapadb.core.adb.command.ReadSharedPrefsFile
import com.ragaa.snapadb.core.adb.command.WriteSharedPrefsFile
import com.ragaa.snapadb.core.adb.command.DeleteSharedPrefsFile
import com.ragaa.snapadb.core.adb.model.SharedPrefEntry
import com.ragaa.snapadb.core.adb.model.SharedPrefType
import com.ragaa.snapadb.core.adb.parser.SharedPrefsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDataViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<AppDataState>(AppDataState.NoDevice)
    val state: StateFlow<AppDataState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<AppDataResult?>(null)
    val actionResult: StateFlow<AppDataResult?> = _actionResult.asStateFlow()

    private val _packageSuggestions = MutableStateFlow<List<String>>(emptyList())
    val packageSuggestions: StateFlow<List<String>> = _packageSuggestions.asStateFlow()

    // Fix #1: Replace var with MutableStateFlow for concurrency safety
    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                _currentSerial.value = device?.serial
                _installedPackages.value = emptyList()
                _actionResult.value = null
                _packageSuggestions.value = emptyList()
                if (device == null) {
                    _state.value = AppDataState.NoDevice
                } else {
                    _state.value = AppDataState.NoApp
                    loadInstalledPackages(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: AppDataIntent) {
        when (intent) {
            is AppDataIntent.SetPackageName -> setPackageName(intent.packageName)
            is AppDataIntent.SelectFile -> selectFile(intent.fileName)
            is AppDataIntent.EditEntry -> editEntry(intent.key, intent.newValue, intent.type)
            is AppDataIntent.AddEntry -> addEntry(intent.key, intent.value, intent.type)
            is AppDataIntent.DeleteEntry -> deleteEntry(intent.key)
            is AppDataIntent.SaveChanges -> saveChanges()
            is AppDataIntent.Refresh -> refresh()
            is AppDataIntent.DeleteFile -> deleteFile(intent.fileName)
            is AppDataIntent.DismissResult -> _actionResult.value = null
            is AppDataIntent.UpdatePackageQuery -> updatePackageQuery(intent.query)
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
        // Fix #1: Read from StateFlow (thread-safe)
        val serial = _currentSerial.value ?: return
        _packageSuggestions.value = emptyList()
        _state.value = AppDataState.Loading
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ListSharedPrefsFiles(packageName), serial)
            }.onSuccess { files ->
                _state.value = AppDataState.Loaded(
                    packageName = packageName,
                    files = files,
                    selectedFile = null,
                    entries = emptyList(),
                    editedEntries = null,
                )
            }.onFailure { e ->
                val message = e.message ?: "Unknown error"
                // Fix #5: Preserve packageName in Error state for retry
                _state.value = if ("not debuggable" in message || "is not debuggable" in message ||
                    "run-as: Package" in message
                ) {
                    AppDataState.Error(
                        "App '$packageName' is not debuggable. Only debug builds can be inspected.",
                        retryPackageName = packageName,
                    )
                } else {
                    AppDataState.Error(message, retryPackageName = packageName)
                }
            }
        }
    }

    private fun selectFile(fileName: String) {
        val serial = _currentSerial.value ?: return
        val current = _state.value as? AppDataState.Loaded ?: return
        // Fix #3: Capture packageName before launching coroutine
        val packageName = current.packageName
        _state.value = current.copy(selectedFile = fileName, entries = emptyList(), editedEntries = null)

        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ReadSharedPrefsFile(packageName, fileName), serial)
            }.onSuccess { entries ->
                // Fix #3: Use captured values, update state atomically
                val loaded = _state.value as? AppDataState.Loaded ?: return@launch
                if (loaded.packageName == packageName && loaded.selectedFile == fileName) {
                    _state.value = loaded.copy(entries = entries, editedEntries = null)
                }
            }.onFailure { e ->
                _actionResult.value = AppDataResult.Failure("Failed to read file: ${e.message}")
            }
        }
    }

    // Fix #8: Accept type for validation
    private fun editEntry(key: String, newValue: String, type: SharedPrefType) {
        val error = validateValue(newValue, type)
        if (error != null) {
            _actionResult.value = AppDataResult.Failure(error)
            return
        }
        val current = _state.value as? AppDataState.Loaded ?: return
        val base = current.editedEntries ?: current.entries
        val updated = base.map { entry ->
            if (entry.key == key) entry.copy(value = newValue) else entry
        }
        _state.value = current.copy(editedEntries = updated)
    }

    private fun addEntry(key: String, value: String, type: SharedPrefType) {
        val error = validateValue(value, type)
        if (error != null) {
            _actionResult.value = AppDataResult.Failure(error)
            return
        }
        val current = _state.value as? AppDataState.Loaded ?: return
        val base = current.editedEntries ?: current.entries
        if (base.any { it.key == key }) {
            _actionResult.value = AppDataResult.Failure("Key '$key' already exists")
            return
        }
        val entry = if (type == SharedPrefType.STRING_SET) {
            SharedPrefEntry(key, "", type, setValues = value.split("\n").filter { it.isNotBlank() })
        } else {
            SharedPrefEntry(key, value, type)
        }
        _state.value = current.copy(editedEntries = base + entry)
    }

    private fun deleteEntry(key: String) {
        val current = _state.value as? AppDataState.Loaded ?: return
        val base = current.editedEntries ?: current.entries
        val updated = base.filter { it.key != key }
        _state.value = current.copy(editedEntries = updated)
    }

    private fun saveChanges() {
        // Fix #1 & #3: Capture all mutable state as locals before launching
        val serial = _currentSerial.value ?: return
        val current = _state.value as? AppDataState.Loaded ?: return
        val fileName = current.selectedFile ?: return
        val entries = current.editedEntries ?: return
        val packageName = current.packageName

        viewModelScope.launch {
            val xml = SharedPrefsParser.toXml(entries)
            withContext(dispatchers.io) {
                adbClient.execute(WriteSharedPrefsFile(packageName, fileName, xml), serial)
            }.onSuccess {
                // Fix #3: Use captured state, don't re-read _state.value
                _state.value = current.copy(entries = entries, editedEntries = null)
                _actionResult.value = AppDataResult.Success("Changes saved to $fileName")
            }.onFailure { e ->
                _actionResult.value = AppDataResult.Failure("Failed to save: ${e.message}")
            }
        }
    }

    // Fix #5: Handle Error state in refresh
    private fun refresh() {
        when (val current = _state.value) {
            is AppDataState.Loaded -> {
                if (current.selectedFile != null) {
                    selectFile(current.selectedFile)
                } else {
                    setPackageName(current.packageName)
                }
            }
            is AppDataState.Error -> {
                val pkg = current.retryPackageName
                if (pkg != null) {
                    setPackageName(pkg)
                } else {
                    _state.value = AppDataState.NoApp
                }
            }
            else -> {}
        }
    }

    private fun deleteFile(fileName: String) {
        // Fix #1 & #3: Capture all mutable state before launch
        val serial = _currentSerial.value ?: return
        val current = _state.value as? AppDataState.Loaded ?: return
        val packageName = current.packageName

        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(DeleteSharedPrefsFile(packageName, fileName), serial)
            }.onSuccess {
                _actionResult.value = AppDataResult.Success("Deleted $fileName")
                setPackageName(packageName)
            }.onFailure { e ->
                _actionResult.value = AppDataResult.Failure("Failed to delete: ${e.message}")
            }
        }
    }

    fun getExportContent(): String? {
        val current = _state.value as? AppDataState.Loaded ?: return null
        val entries = current.editedEntries ?: current.entries
        if (entries.isEmpty()) return null
        return SharedPrefsParser.toXml(entries)
    }

    fun importXml(xml: String) {
        val current = _state.value as? AppDataState.Loaded ?: return
        if (current.selectedFile == null) {
            _actionResult.value = AppDataResult.Failure("Select a file first before importing")
            return
        }
        try {
            val entries = SharedPrefsParser.parse(xml)
            if (entries.isEmpty()) {
                _actionResult.value = AppDataResult.Failure("No valid entries found in imported XML")
                return
            }
            _state.value = current.copy(editedEntries = entries)
            _actionResult.value = AppDataResult.Success("Imported ${entries.size} entries — save to apply")
        } catch (e: Exception) {
            _actionResult.value = AppDataResult.Failure("Invalid XML: ${e.message}")
        }
    }

    fun reportError(message: String) {
        _actionResult.value = AppDataResult.Failure(message)
    }

    fun reportSuccess(message: String) {
        _actionResult.value = AppDataResult.Success(message)
    }

    companion object {
        // Fix #8: Type validation for typed SharedPref values
        fun validateValue(value: String, type: SharedPrefType): String? = when (type) {
            SharedPrefType.INT -> {
                if (value.toIntOrNull() == null) "Invalid integer value: '$value'" else null
            }
            SharedPrefType.LONG -> {
                if (value.toLongOrNull() == null) "Invalid long value: '$value'" else null
            }
            SharedPrefType.FLOAT -> {
                if (value.toFloatOrNull() == null) "Invalid float value: '$value'" else null
            }
            SharedPrefType.BOOLEAN -> {
                if (value != "true" && value != "false") "Boolean must be 'true' or 'false'" else null
            }
            SharedPrefType.STRING, SharedPrefType.STRING_SET -> null
        }
    }
}

sealed class AppDataState {
    data object NoDevice : AppDataState()
    data object NoApp : AppDataState()
    data object Loading : AppDataState()
    // Fix #5: Error preserves retryPackageName
    data class Error(val message: String, val retryPackageName: String? = null) : AppDataState()
    data class Loaded(
        val packageName: String,
        val files: List<String>,
        val selectedFile: String?,
        val entries: List<SharedPrefEntry>,
        val editedEntries: List<SharedPrefEntry>?,
    ) : AppDataState() {
        val isDirty: Boolean get() = editedEntries != null
        val displayEntries: List<SharedPrefEntry> get() = editedEntries ?: entries
    }
}

sealed class AppDataIntent {
    data class UpdatePackageQuery(val query: String) : AppDataIntent()
    data class SetPackageName(val packageName: String) : AppDataIntent()
    data class SelectFile(val fileName: String) : AppDataIntent()
    // Fix #8: Include type in EditEntry for validation
    data class EditEntry(val key: String, val newValue: String, val type: SharedPrefType) : AppDataIntent()
    data class AddEntry(val key: String, val value: String, val type: SharedPrefType) : AppDataIntent()
    data class DeleteEntry(val key: String) : AppDataIntent()
    data class DeleteFile(val fileName: String) : AppDataIntent()
    data object SaveChanges : AppDataIntent()
    data object Refresh : AppDataIntent()
    data object DismissResult : AppDataIntent()
}

sealed class AppDataResult {
    data class Success(val message: String) : AppDataResult()
    data class Failure(val message: String) : AppDataResult()
}
