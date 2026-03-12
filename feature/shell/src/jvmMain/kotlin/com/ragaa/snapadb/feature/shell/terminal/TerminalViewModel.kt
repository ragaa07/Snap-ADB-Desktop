package com.ragaa.snapadb.feature.shell.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ShellExec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class TerminalViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<TerminalState>(TerminalState.NoDevice)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val outputMutex = Mutex()
    private val outputLines = mutableListOf<OutputLine>()
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                if (device == null) {
                    _state.value = TerminalState.NoDevice
                } else {
                    outputMutex.withLock {
                        outputLines.clear()
                    }
                    commandHistory.clear()
                    historyIndex = -1
                    _state.value = TerminalState.Ready(
                        deviceSerial = device.serial,
                        output = emptyList(),
                    )
                }
            }
        }
    }

    fun onIntent(intent: TerminalIntent) {
        when (intent) {
            is TerminalIntent.UpdateInput -> {
                _input.value = intent.text
                historyIndex = -1
            }
            is TerminalIntent.ExecuteCommand -> executeCommand()
            is TerminalIntent.ClearOutput -> clearOutput()
            is TerminalIntent.NavigateHistory -> navigateHistory(intent.direction)
        }
    }

    private fun executeCommand() {
        val command = _input.value.trim()
        if (command.isBlank()) return
        val current = state.value as? TerminalState.Ready ?: return
        if (current.isExecuting) return
        val serial = current.deviceSerial

        val shellExec = try {
            ShellExec(command)
        } catch (e: IllegalArgumentException) {
            viewModelScope.launch {
                outputMutex.withLock { appendOutput(OutputLine(text = "Error: ${e.message}", type = OutputType.STDERR)) }
                emitReady(serial)
            }
            return
        }

        commandHistory.add(0, command)
        if (commandHistory.size > MAX_HISTORY) commandHistory.removeLast()
        historyIndex = -1
        _input.value = ""

        viewModelScope.launch {
            outputMutex.withLock { appendOutput(OutputLine(text = "$ $command", type = OutputType.COMMAND)) }
            emitReady(serial, isExecuting = true)

            val result = adbClient.execute(shellExec, serial)
            outputMutex.withLock {
                result.onSuccess { output ->
                    if (output.isNotBlank()) {
                        output.lines().forEach { line ->
                            appendOutput(OutputLine(text = line, type = OutputType.STDOUT))
                        }
                    }
                }.onFailure { error ->
                    appendOutput(OutputLine(text = "Error: ${error.message}", type = OutputType.STDERR))
                }
            }
            emitReady(serial)
        }
    }

    private fun clearOutput() {
        val serial = (state.value as? TerminalState.Ready)?.deviceSerial ?: return
        viewModelScope.launch {
            outputMutex.withLock { outputLines.clear() }
            emitReady(serial)
        }
    }

    private fun navigateHistory(direction: HistoryDirection) {
        if (commandHistory.isEmpty()) return
        when (direction) {
            HistoryDirection.UP -> {
                if (historyIndex < commandHistory.lastIndex) {
                    historyIndex++
                    _input.value = commandHistory[historyIndex]
                }
            }
            HistoryDirection.DOWN -> {
                if (historyIndex > 0) {
                    historyIndex--
                    _input.value = commandHistory[historyIndex]
                } else if (historyIndex == 0) {
                    historyIndex = -1
                    _input.value = ""
                }
            }
        }
    }

    private fun appendOutput(line: OutputLine) {
        outputLines.add(line)
        if (outputLines.size > MAX_OUTPUT_LINES) {
            outputLines.removeFirst()
        }
    }

    private suspend fun emitReady(serial: String, isExecuting: Boolean = false) {
        val snapshot = outputMutex.withLock { outputLines.toList() }
        _state.value = TerminalState.Ready(
            deviceSerial = serial,
            output = snapshot,
            isExecuting = isExecuting,
        )
    }

    companion object {
        private const val MAX_OUTPUT_LINES = 1000
        private const val MAX_HISTORY = 100
    }
}

sealed class TerminalState {
    data object NoDevice : TerminalState()
    data class Ready(
        val deviceSerial: String,
        val output: List<OutputLine>,
        val isExecuting: Boolean = false,
    ) : TerminalState()
}

sealed class TerminalIntent {
    data class UpdateInput(val text: String) : TerminalIntent()
    data object ExecuteCommand : TerminalIntent()
    data object ClearOutput : TerminalIntent()
    data class NavigateHistory(val direction: HistoryDirection) : TerminalIntent()
}

enum class HistoryDirection { UP, DOWN }

data class OutputLine(
    val id: Long = outputIdCounter.getAndIncrement(),
    val text: String,
    val type: OutputType,
) {
    companion object {
        private val outputIdCounter = AtomicLong(0)
    }
}

enum class OutputType { COMMAND, STDOUT, STDERR }
