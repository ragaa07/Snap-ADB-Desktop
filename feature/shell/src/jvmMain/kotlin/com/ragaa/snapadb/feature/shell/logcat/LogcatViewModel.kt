package com.ragaa.snapadb.feature.shell.logcat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ClearLogcat
import com.ragaa.snapadb.core.adb.command.LogcatStream
import com.ragaa.snapadb.core.adb.model.LogLevel
import com.ragaa.snapadb.core.adb.model.LogcatEntry
import com.ragaa.snapadb.database.SnapAdbDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class LogcatViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<LogcatState>(LogcatState.NoDevice)
    val state: StateFlow<LogcatState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

    private val entriesMutex = Mutex()
    private val allEntries = mutableListOf<LogcatEntry>()
    private var streamJob: Job? = null
    private var emitJob: Job? = null
    private var isPaused = false
    private var currentSerial: String? = null
    private var dirty = false

    // Filter state
    private var minLevel: LogLevel = LogLevel.VERBOSE
    private var tagFilter: String = ""
    private var packageFilter: String = ""
    private var searchText: String = ""

    // Regex search state
    private val isRegexSearch = MutableStateFlow(false)
    private val cachedRegex = AtomicReference<Regex?>(null)

    // Bookmark state
    private val bookmarkedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val showBookmarksOnly = MutableStateFlow(false)

    // Cached saved filters — only refreshed on save/delete/load
    private var cachedSavedFilters: List<SavedFilter> = emptyList()

    init {
        viewModelScope.launch(dispatchers.io) {
            cachedSavedFilters = loadSavedFiltersFromDb()
        }

        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                stopStream()
                entriesMutex.withLock { allEntries.clear() }
                bookmarkedIds.value = emptySet()
                if (device == null) {
                    currentSerial = null
                    _state.value = LogcatState.NoDevice
                } else {
                    currentSerial = device.serial
                    isPaused = false
                    startStream(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: LogcatIntent) {
        when (intent) {
            is LogcatIntent.TogglePause -> togglePause()
            is LogcatIntent.ClearLog -> clearLog()
            is LogcatIntent.SetMinLevel -> {
                minLevel = intent.level
                dirty = true
            }
            is LogcatIntent.SetTagFilter -> {
                tagFilter = intent.tag
                dirty = true
            }
            is LogcatIntent.SetPackageFilter -> {
                packageFilter = intent.packageName
                dirty = true
            }
            is LogcatIntent.SetSearchText -> {
                searchText = intent.text
                recompileRegex()
                dirty = true
            }
            is LogcatIntent.ToggleRegexSearch -> {
                isRegexSearch.value = !isRegexSearch.value
                recompileRegex()
                dirty = true
            }
            is LogcatIntent.ToggleBookmark -> {
                val current = bookmarkedIds.value
                bookmarkedIds.value = if (intent.entryId in current) {
                    current - intent.entryId
                } else {
                    current + intent.entryId
                }
                if (showBookmarksOnly.value) dirty = true
            }
            is LogcatIntent.ToggleShowBookmarksOnly -> {
                showBookmarksOnly.value = !showBookmarksOnly.value
                dirty = true
            }
            is LogcatIntent.CopyToClipboard -> copyToClipboard()
            is LogcatIntent.DismissActionResult -> _actionResult.value = null
            is LogcatIntent.ExportToFile -> exportToFile(intent.file)
            is LogcatIntent.SaveFilter -> saveFilter(intent.name)
            is LogcatIntent.LoadFilter -> loadFilter(intent.id)
            is LogcatIntent.DeleteFilter -> deleteFilter(intent.id)
            is LogcatIntent.RefreshFilters -> viewModelScope.launch { emitStreamingState() }
            is LogcatIntent.Retry -> {
                currentSerial?.let { startStream(it) }
            }
        }
    }

    private fun recompileRegex() {
        if (isRegexSearch.value && searchText.isNotBlank()) {
            cachedRegex.set(
                try {
                    Regex(searchText, RegexOption.IGNORE_CASE)
                } catch (_: Exception) {
                    null
                }
            )
        } else {
            cachedRegex.set(null)
        }
    }

    private fun startStream(serial: String) {
        streamJob = viewModelScope.launch(dispatchers.io) {
            adbClient.stream(LogcatStream(), serial)
                .catch { e ->
                    _state.value = LogcatState.Error(e.message ?: "Stream failed")
                }
                .collect { entry ->
                    entriesMutex.withLock {
                        allEntries.add(entry)
                        if (allEntries.size > MAX_ENTRIES) {
                            val removed = allEntries.removeFirst()
                            // Clean up stale bookmark
                            val bm = bookmarkedIds.value
                            if (removed.id in bm) {
                                bookmarkedIds.value = bm - removed.id
                            }
                        }
                    }
                    dirty = true
                }
        }

        // Throttled emission: at most ~10 updates/sec
        emitJob = viewModelScope.launch {
            // Initial emit to show empty streaming state
            emitStreamingState()
            while (true) {
                delay(EMIT_INTERVAL_MS)
                if (dirty && !isPaused) {
                    dirty = false
                    emitStreamingState()
                }
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        emitJob?.cancel()
        emitJob = null
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (!isPaused) {
            dirty = true
        } else {
            val current = _state.value
            if (current is LogcatState.Streaming) {
                _state.value = current.copy(isPaused = true)
            }
        }
    }

    private fun clearLog() {
        val serial = currentSerial ?: return
        viewModelScope.launch(dispatchers.io) {
            entriesMutex.withLock { allEntries.clear() }
            adbClient.execute(ClearLogcat(), serial)
        }
        bookmarkedIds.value = emptySet()
        viewModelScope.launch { emitStreamingState() }
    }

    private suspend fun applyFilters(): List<LogcatEntry> {
        val regexMode = isRegexSearch.value
        val regex = cachedRegex.get()
        val tags = tagFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val bookmarksOnly = showBookmarksOnly.value
        val bookmarks = bookmarkedIds.value

        return entriesMutex.withLock {
            allEntries.filter { entry ->
                entry.level.ordinal >= minLevel.ordinal &&
                    (tags.isEmpty() || tags.any { t -> entry.tag.contains(t, ignoreCase = true) }) &&
                    (packageFilter.isBlank() || entry.pid == packageFilter || entry.message.contains(packageFilter, ignoreCase = true)) &&
                    matchesSearch(entry, regexMode, regex) &&
                    (!bookmarksOnly || entry.id in bookmarks)
            }
        }
    }

    private fun matchesSearch(entry: LogcatEntry, regexMode: Boolean, regex: Regex?): Boolean {
        if (searchText.isBlank()) return true
        if (regexMode) {
            return regex?.let {
                it.containsMatchIn(entry.message) || it.containsMatchIn(entry.tag)
            } ?: false
        }
        return entry.message.contains(searchText, ignoreCase = true) ||
            entry.tag.contains(searchText, ignoreCase = true)
    }

    private fun detectCrashGroups(entries: List<LogcatEntry>): List<CrashGroup> {
        val groups = mutableListOf<CrashGroup>()
        var currentGroup: MutableCrashGroup? = null

        for (entry in entries) {
            if (CRASH_START.containsMatchIn(entry.message)) {
                // Finalize previous group
                currentGroup?.let { groups.add(it.toGroup()) }
                currentGroup = MutableCrashGroup(
                    startEntryId = entry.id,
                    entryIds = mutableSetOf(entry.id),
                    summary = entry.message.take(120),
                )
            } else if (currentGroup != null) {
                if (STACK_TRACE_LINE.containsMatchIn(entry.message) ||
                    EXCEPTION_LINE.containsMatchIn(entry.message)
                ) {
                    currentGroup.entryIds.add(entry.id)
                } else {
                    // End of crash group
                    groups.add(currentGroup.toGroup())
                    currentGroup = null
                }
            }
        }
        currentGroup?.let { groups.add(it.toGroup()) }
        return groups
    }

    private suspend fun emitStreamingState() {
        val serial = currentSerial ?: return
        val filtered = applyFilters()
        val totalCount = entriesMutex.withLock { allEntries.size }
        val crashGroups = detectCrashGroups(filtered)
        val regexValid = !isRegexSearch.value || searchText.isBlank() || cachedRegex.get() != null

        _state.value = LogcatState.Streaming(
            deviceSerial = serial,
            entries = filtered,
            totalCount = totalCount,
            isPaused = isPaused,
            minLevel = minLevel,
            tagFilter = tagFilter,
            packageFilter = packageFilter,
            searchText = searchText,
            savedFilters = cachedSavedFilters,
            isRegexSearch = isRegexSearch.value,
            isRegexValid = regexValid,
            bookmarkedIds = bookmarkedIds.value,
            showBookmarksOnly = showBookmarksOnly.value,
            crashGroups = crashGroups,
        )
    }

    private fun copyToClipboard() {
        viewModelScope.launch(dispatchers.io) {
            val filtered = applyFilters()
            val text = filtered.joinToString("\n") { entry ->
                "${entry.timestamp} ${entry.level.label}/${entry.tag}(${entry.pid}): ${entry.message}"
            }
            javax.swing.SwingUtilities.invokeLater {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            }
            _actionResult.value = "Copied ${filtered.size} entries to clipboard"
        }
    }

    private fun exportToFile(file: File) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val filtered = applyFilters()
                file.bufferedWriter().use { writer ->
                    filtered.forEach { entry ->
                        writer.appendLine("${entry.timestamp} ${entry.pid} ${entry.tid} ${entry.level.label}/${entry.tag}: ${entry.message}")
                    }
                }
            } catch (_: Exception) {
                // File write failed — silently ignored for now
            }
        }
    }

    private fun saveFilter(name: String) {
        viewModelScope.launch(dispatchers.io) {
            database.snapAdbQueries.insertFilter(
                name = name,
                tag_pattern = tagFilter.ifBlank { null },
                min_level = minLevel.name,
                package_name = packageFilter.ifBlank { null },
                search_text = searchText.ifBlank { null },
            )
            cachedSavedFilters = loadSavedFiltersFromDb()
            emitStreamingState()
        }
    }

    private fun loadFilter(id: Long) {
        viewModelScope.launch(dispatchers.io) {
            val filter = database.snapAdbQueries.getFilterById(id).executeAsOneOrNull() ?: return@launch
            minLevel = filter.min_level?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: LogLevel.VERBOSE
            tagFilter = filter.tag_pattern ?: ""
            packageFilter = filter.package_name ?: ""
            searchText = filter.search_text ?: ""
            recompileRegex()
            emitStreamingState()
        }
    }

    private fun deleteFilter(id: Long) {
        viewModelScope.launch(dispatchers.io) {
            database.snapAdbQueries.deleteFilter(id)
            cachedSavedFilters = loadSavedFiltersFromDb()
            emitStreamingState()
        }
    }

    private fun loadSavedFiltersFromDb(): List<SavedFilter> {
        return try {
            database.snapAdbQueries.getAllFilters().executeAsList().map { row ->
                SavedFilter(id = row.id, name = row.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }

    companion object {
        private const val MAX_ENTRIES = 10_000
        private const val EMIT_INTERVAL_MS = 100L

        val CRASH_START = Regex("FATAL EXCEPTION|ANR in|FATAL SIGNAL")
        val STACK_TRACE_LINE = Regex("""^\s+at\s+\S+|^\s+Caused by:\s+|^\s+\.\.\.\s+\d+\s+more""")
        val EXCEPTION_LINE = Regex("""^[\w.$]+Exception|^[\w.$]+Error""")
    }
}

private class MutableCrashGroup(
    val startEntryId: Long,
    val entryIds: MutableSet<Long>,
    val summary: String,
) {
    fun toGroup() = CrashGroup(startEntryId, entryIds.toSet(), summary)
}

data class CrashGroup(
    val startEntryId: Long,
    val entryIds: Set<Long>,
    val summary: String,
)

sealed class LogcatState {
    data object NoDevice : LogcatState()
    data class Error(val message: String) : LogcatState()
    data class Streaming(
        val deviceSerial: String,
        val entries: List<LogcatEntry>,
        val totalCount: Int,
        val isPaused: Boolean,
        val minLevel: LogLevel,
        val tagFilter: String,
        val packageFilter: String,
        val searchText: String,
        val savedFilters: List<SavedFilter>,
        val isRegexSearch: Boolean,
        val isRegexValid: Boolean,
        val bookmarkedIds: Set<Long>,
        val showBookmarksOnly: Boolean,
        val crashGroups: List<CrashGroup>,
    ) : LogcatState()
}

sealed class LogcatIntent {
    data object TogglePause : LogcatIntent()
    data object ClearLog : LogcatIntent()
    data object Retry : LogcatIntent()
    data class SetMinLevel(val level: LogLevel) : LogcatIntent()
    data class SetTagFilter(val tag: String) : LogcatIntent()
    data class SetPackageFilter(val packageName: String) : LogcatIntent()
    data class SetSearchText(val text: String) : LogcatIntent()
    data object ToggleRegexSearch : LogcatIntent()
    data class ToggleBookmark(val entryId: Long) : LogcatIntent()
    data object ToggleShowBookmarksOnly : LogcatIntent()
    data object CopyToClipboard : LogcatIntent()
    data object DismissActionResult : LogcatIntent()
    data class ExportToFile(val file: File) : LogcatIntent()
    data class SaveFilter(val name: String) : LogcatIntent()
    data class LoadFilter(val id: Long) : LogcatIntent()
    data class DeleteFilter(val id: Long) : LogcatIntent()
    data object RefreshFilters : LogcatIntent()
}

data class SavedFilter(
    val id: Long,
    val name: String,
)
