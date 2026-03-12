package com.ragaa.snapadb.feature.deeplink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.FireDeepLink
import com.ragaa.snapadb.database.SnapAdbDatabase
import com.ragaa.snapadb.feature.deeplink.model.DeepLinkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DeepLinkViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<DeepLinkState>(DeepLinkState.NoDevice)
    val state: StateFlow<DeepLinkState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<DeepLinkResult?>(null)
    val actionResult: StateFlow<DeepLinkResult?> = _actionResult.asStateFlow()

    private var currentSerial: String? = null
    private val mutex = Mutex()
    private var cachedLinks = emptyList<DeepLinkItem>()

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                mutex.withLock {
                    cachedLinks = emptyList()
                    currentSerial = device?.serial
                }
                _actionResult.value = null
                if (device == null) {
                    _state.value = DeepLinkState.NoDevice
                } else {
                    loadLinks()
                }
            }
        }
    }

    fun onIntent(intent: DeepLinkIntent) {
        when (intent) {
            is DeepLinkIntent.Fire -> fireLink(intent.uri, intent.packageName)
            is DeepLinkIntent.FireExisting -> fireExistingLink(intent.id)
            is DeepLinkIntent.Save -> saveLink(intent.uri, intent.label, intent.packageName)
            is DeepLinkIntent.Delete -> deleteLink(intent.id)
            is DeepLinkIntent.ToggleFavorite -> toggleFavorite(intent.id)
            is DeepLinkIntent.ShowFavoritesOnly -> {
                emitReady(intent.show)
            }
            is DeepLinkIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadLinks() {
        viewModelScope.launch {
            val links = withContext(dispatchers.io) {
                database.snapAdbQueries.getAllDeepLinks().executeAsList().map { row ->
                    DeepLinkItem(
                        id = row.id,
                        uri = row.uri,
                        label = row.label,
                        targetPackage = row.target_package,
                        isFavorite = row.is_favorite != 0L,
                        lastUsed = row.last_used,
                        createdAt = row.created_at,
                    )
                }
            }
            mutex.withLock { cachedLinks = links }
            emitReady(showFavoritesOnly = (_state.value as? DeepLinkState.Ready)?.showFavoritesOnly ?: false)
        }
    }

    private fun emitReady(showFavoritesOnly: Boolean) {
        val links = cachedLinks
        val filtered = if (showFavoritesOnly) links.filter { it.isFavorite } else links
        _state.value = DeepLinkState.Ready(
            links = filtered,
            showFavoritesOnly = showFavoritesOnly,
        )
    }

    private fun fireLink(uri: String, packageName: String?) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(FireDeepLink(uri, packageName.takeIf { !it.isNullOrBlank() }), serial)
            }
                .onSuccess {
                    _actionResult.value = DeepLinkResult.Success("Deep link fired: $uri")
                }
                .onFailure { e ->
                    _actionResult.value = DeepLinkResult.Failure("Failed to fire link: ${e.message}")
                }
        }
    }

    private fun fireExistingLink(id: Long) {
        val link = cachedLinks.find { it.id == id } ?: return
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                database.snapAdbQueries.updateDeepLinkLastUsed(System.currentTimeMillis(), id)
            }
            withContext(dispatchers.io) {
                adbClient.execute(
                    FireDeepLink(link.uri, link.targetPackage.takeIf { !it.isNullOrBlank() }),
                    serial,
                )
            }
                .onSuccess {
                    _actionResult.value = DeepLinkResult.Success("Fired: ${link.uri}")
                    loadLinks()
                }
                .onFailure { e ->
                    _actionResult.value = DeepLinkResult.Failure("Failed: ${e.message}")
                }
        }
    }

    private fun saveLink(uri: String, label: String?, packageName: String?) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                database.snapAdbQueries.insertDeepLink(
                    uri = uri,
                    label = label.takeIf { !it.isNullOrBlank() },
                    target_package = packageName.takeIf { !it.isNullOrBlank() },
                    is_favorite = 0L,
                    created_at = System.currentTimeMillis(),
                )
            }
            _actionResult.value = DeepLinkResult.Success("Link saved")
            loadLinks()
        }
    }

    private fun deleteLink(id: Long) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                database.snapAdbQueries.deleteDeepLink(id)
            }
            _actionResult.value = DeepLinkResult.Success("Link deleted")
            loadLinks()
        }
    }

    private fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                database.snapAdbQueries.toggleDeepLinkFavorite(id)
            }
            loadLinks()
        }
    }
}

sealed class DeepLinkState {
    data object NoDevice : DeepLinkState()
    data class Ready(
        val links: List<DeepLinkItem>,
        val showFavoritesOnly: Boolean = false,
    ) : DeepLinkState()
}

sealed class DeepLinkIntent {
    data class Fire(val uri: String, val packageName: String? = null) : DeepLinkIntent()
    data class FireExisting(val id: Long) : DeepLinkIntent()
    data class Save(val uri: String, val label: String?, val packageName: String?) : DeepLinkIntent()
    data class Delete(val id: Long) : DeepLinkIntent()
    data class ToggleFavorite(val id: Long) : DeepLinkIntent()
    data class ShowFavoritesOnly(val show: Boolean) : DeepLinkIntent()
    data object DismissResult : DeepLinkIntent()
}

sealed class DeepLinkResult {
    data class Success(val message: String) : DeepLinkResult()
    data class Failure(val message: String) : DeepLinkResult()
}
