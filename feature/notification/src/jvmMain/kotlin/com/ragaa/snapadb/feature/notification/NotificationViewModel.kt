package com.ragaa.snapadb.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.CancelNotification
import com.ragaa.snapadb.core.adb.command.PostNotification
import com.ragaa.snapadb.database.SnapAdbDatabase
import com.ragaa.snapadb.feature.notification.model.NotificationPayload
import com.ragaa.snapadb.feature.notification.model.NotificationTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NotificationViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<NotificationState>(NotificationState.NoDevice)
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<NotificationResult?>(null)
    val actionResult: StateFlow<NotificationResult?> = _actionResult.asStateFlow()

    private var currentSerial: String? = null
    private val mutex = Mutex()
    private var cachedTemplates = emptyList<NotificationTemplate>()

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                mutex.withLock {
                    cachedTemplates = emptyList()
                    currentSerial = device?.serial
                }
                _actionResult.value = null
                if (device == null) {
                    _state.value = NotificationState.NoDevice
                } else {
                    loadTemplates()
                }
            }
        }
    }

    fun onIntent(intent: NotificationIntent) {
        when (intent) {
            is NotificationIntent.Send -> sendNotification(intent.tag, intent.title, intent.text)
            is NotificationIntent.Cancel -> cancelNotification(intent.tag)
            is NotificationIntent.SaveTemplate -> saveTemplate(intent.name, intent.tag, intent.title, intent.text)
            is NotificationIntent.LoadTemplate -> loadTemplate(intent.id)
            is NotificationIntent.DeleteTemplate -> deleteTemplate(intent.id)
            is NotificationIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            val templates = withContext(dispatchers.io) {
                database.snapAdbQueries.getAllTemplates().executeAsList().map { row ->
                    val payload = NotificationPayload.fromJson(row.payload_json)
                    NotificationTemplate(
                        id = row.id,
                        name = row.name,
                        payload = payload,
                        createdAt = row.created_at,
                    )
                }
            }
            mutex.withLock { cachedTemplates = templates }
            _state.value = NotificationState.Ready(templates = templates)
        }
    }

    private fun sendNotification(tag: String, title: String, text: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(PostNotification(tag, title, text), serial)
            }
                .onSuccess { _actionResult.value = NotificationResult.Success("Notification posted: $tag") }
                .onFailure { e -> _actionResult.value = NotificationResult.Failure("Failed: ${e.message}") }
        }
    }

    private fun cancelNotification(tag: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(CancelNotification(tag), serial)
            }
                .onSuccess { _actionResult.value = NotificationResult.Success("Notification cancelled: $tag") }
                .onFailure { e -> _actionResult.value = NotificationResult.Failure("Failed: ${e.message}") }
        }
    }

    private fun saveTemplate(name: String, tag: String, title: String, text: String) {
        viewModelScope.launch {
            val payload = NotificationPayload(tag, title, text)
            withContext(dispatchers.io) {
                database.snapAdbQueries.insertTemplate(
                    name = name,
                    payload_json = payload.toJson(),
                    created_at = System.currentTimeMillis(),
                )
            }
            _actionResult.value = NotificationResult.Success("Template saved: $name")
            loadTemplates()
        }
    }

    private fun loadTemplate(id: Long) {
        val template = cachedTemplates.find { it.id == id } ?: return
        _state.value = NotificationState.Ready(
            templates = cachedTemplates,
            loadedPayload = template.payload,
        )
    }

    private fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                database.snapAdbQueries.deleteTemplate(id)
            }
            _actionResult.value = NotificationResult.Success("Template deleted")
            loadTemplates()
        }
    }
}

sealed class NotificationState {
    data object NoDevice : NotificationState()
    data class Ready(
        val templates: List<NotificationTemplate>,
        val loadedPayload: NotificationPayload? = null,
    ) : NotificationState()
}

sealed class NotificationIntent {
    data class Send(val tag: String, val title: String, val text: String) : NotificationIntent()
    data class Cancel(val tag: String) : NotificationIntent()
    data class SaveTemplate(val name: String, val tag: String, val title: String, val text: String) : NotificationIntent()
    data class LoadTemplate(val id: Long) : NotificationIntent()
    data class DeleteTemplate(val id: Long) : NotificationIntent()
    data object DismissResult : NotificationIntent()
}

sealed class NotificationResult {
    data class Success(val message: String) : NotificationResult()
    data class Failure(val message: String) : NotificationResult()
}
