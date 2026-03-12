package com.ragaa.snapadb.feature.fileexplorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.DeleteFile
import com.ragaa.snapadb.core.adb.command.ListFiles
import com.ragaa.snapadb.core.adb.command.MakeDirectory
import com.ragaa.snapadb.core.adb.command.PullFile
import com.ragaa.snapadb.core.adb.command.PushFile
import com.ragaa.snapadb.core.adb.model.FileEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileExplorerViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<FileExplorerState>(FileExplorerState.NoDevice)
    val state: StateFlow<FileExplorerState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<FileActionResult?>(null)
    val actionResult: StateFlow<FileActionResult?> = _actionResult.asStateFlow()

    private var currentSerial: String? = null

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                currentSerial = device?.serial
                _actionResult.value = null
                if (device == null) {
                    _state.value = FileExplorerState.NoDevice
                } else {
                    navigateTo("/sdcard")
                }
            }
        }
    }

    fun onIntent(intent: FileExplorerIntent) {
        when (intent) {
            is FileExplorerIntent.NavigateTo -> navigateTo(intent.path)
            is FileExplorerIntent.NavigateUp -> navigateUp()
            is FileExplorerIntent.NavigateToBreadcrumb -> navigateTo(intent.path)
            is FileExplorerIntent.Refresh -> refresh()
            is FileExplorerIntent.PullFile -> pullFile(intent.remotePath, intent.localPath)
            is FileExplorerIntent.PushFile -> pushFile(intent.localPath)
            is FileExplorerIntent.Delete -> deleteFile(intent.remotePath)
            is FileExplorerIntent.MakeDirectory -> makeDirectory(intent.name)
            is FileExplorerIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun navigateTo(path: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            val current = _state.value
            val prevPath = (current as? FileExplorerState.Loaded)?.currentPath
            _state.value = FileExplorerState.Loading(path)

            withContext(dispatchers.io) {
                adbClient.execute(ListFiles(path), serial)
            }
                .onSuccess { files ->
                    val sorted = files.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    _state.value = FileExplorerState.Loaded(
                        currentPath = path,
                        files = sorted,
                        breadcrumbs = buildBreadcrumbs(path),
                    )
                }
                .onFailure { e ->
                    _state.value = FileExplorerState.Error(
                        message = e.message ?: "Failed to list files",
                        path = prevPath ?: "/sdcard",
                    )
                }
        }
    }

    private fun navigateUp() {
        val current = _state.value as? FileExplorerState.Loaded ?: return
        if (current.currentPath == "/") return
        val parentPath = current.currentPath.substringBeforeLast('/', "/").ifEmpty { "/" }
        navigateTo(parentPath)
    }

    fun resolveSymlinkTarget(currentPath: String, linkedTo: String): String {
        if (linkedTo.startsWith("/")) return linkedTo
        // Resolve relative symlink against current directory
        return "${currentPath.trimEnd('/')}/$linkedTo"
    }

    private fun refresh() {
        val path = when (val s = _state.value) {
            is FileExplorerState.Loaded -> s.currentPath
            is FileExplorerState.Error -> s.path
            else -> "/sdcard"
        }
        navigateTo(path)
    }

    private fun pullFile(remotePath: String, localPath: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(PullFile(remotePath, localPath), serial)
            }
                .onSuccess { _actionResult.value = FileActionResult.Success("Pulled to $localPath") }
                .onFailure { e -> _actionResult.value = FileActionResult.Failure("Pull failed: ${e.message}") }
        }
    }

    private fun pushFile(localPath: String) {
        val serial = currentSerial ?: return
        val currentPath = (_state.value as? FileExplorerState.Loaded)?.currentPath ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(PushFile(localPath, currentPath), serial)
            }
                .onSuccess {
                    _actionResult.value = FileActionResult.Success("Pushed successfully")
                    navigateTo(currentPath)
                }
                .onFailure { e -> _actionResult.value = FileActionResult.Failure("Push failed: ${e.message}") }
        }
    }

    private fun deleteFile(remotePath: String) {
        val serial = currentSerial ?: return
        val currentPath = (_state.value as? FileExplorerState.Loaded)?.currentPath ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(DeleteFile(remotePath), serial)
            }
                .onSuccess {
                    _actionResult.value = FileActionResult.Success("Deleted")
                    navigateTo(currentPath)
                }
                .onFailure { e -> _actionResult.value = FileActionResult.Failure("Delete failed: ${e.message}") }
        }
    }

    private fun makeDirectory(name: String) {
        val serial = currentSerial ?: return
        val currentPath = (_state.value as? FileExplorerState.Loaded)?.currentPath ?: return
        val newPath = "${currentPath.trimEnd('/')}/$name"
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(MakeDirectory(newPath), serial)
            }
                .onSuccess {
                    _actionResult.value = FileActionResult.Success("Created directory $name")
                    navigateTo(currentPath)
                }
                .onFailure { e -> _actionResult.value = FileActionResult.Failure("mkdir failed: ${e.message}") }
        }
    }

    private fun buildBreadcrumbs(path: String): List<Breadcrumb> {
        if (path == "/") return listOf(Breadcrumb("/", "/"))
        val parts = path.split("/").filter { it.isNotEmpty() }
        val breadcrumbs = mutableListOf(Breadcrumb("/", "/"))
        var accumulated = ""
        for (part in parts) {
            accumulated += "/$part"
            breadcrumbs.add(Breadcrumb(part, accumulated))
        }
        return breadcrumbs
    }
}

data class Breadcrumb(val label: String, val path: String)

sealed class FileExplorerState {
    data object NoDevice : FileExplorerState()
    data class Loading(val path: String) : FileExplorerState()
    data class Error(val message: String, val path: String) : FileExplorerState()
    data class Loaded(
        val currentPath: String,
        val files: List<FileEntry>,
        val breadcrumbs: List<Breadcrumb>,
    ) : FileExplorerState()
}

sealed class FileExplorerIntent {
    data class NavigateTo(val path: String) : FileExplorerIntent()
    data object NavigateUp : FileExplorerIntent()
    data class NavigateToBreadcrumb(val path: String) : FileExplorerIntent()
    data object Refresh : FileExplorerIntent()
    data class PullFile(val remotePath: String, val localPath: String) : FileExplorerIntent()
    data class PushFile(val localPath: String) : FileExplorerIntent()
    data class Delete(val remotePath: String) : FileExplorerIntent()
    data class MakeDirectory(val name: String) : FileExplorerIntent()
    data object DismissResult : FileExplorerIntent()
}

sealed class FileActionResult {
    data class Success(val message: String) : FileActionResult()
    data class Failure(val message: String) : FileActionResult()
}
