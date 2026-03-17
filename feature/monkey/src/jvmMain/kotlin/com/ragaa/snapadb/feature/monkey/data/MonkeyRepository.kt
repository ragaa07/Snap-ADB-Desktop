package com.ragaa.snapadb.feature.monkey.data

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.database.SnapAdbDatabase
import com.ragaa.snapadb.feature.monkey.model.MonkeyConfig
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunStatus
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunSummary
import kotlinx.coroutines.withContext

class MonkeyRepository(
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun getAllConfigs(): List<MonkeyConfig> = withContext(dispatchers.io) {
        database.snapAdbQueries.getAllMonkeyConfigs().executeAsList().map { row ->
            MonkeyConfig(
                id = row.id,
                name = row.name,
                packageName = row.package_name,
                eventCount = row.event_count.toInt(),
                seed = row.seed?.toInt(),
                throttleMs = row.throttle_ms.toInt(),
                categories = row.categories.split(",").filter { it.isNotBlank() },
                verbosity = row.verbosity.toInt(),
                restrictToApp = row.restrict_to_app != 0L,
            )
        }
    }

    suspend fun saveConfig(config: MonkeyConfig): Long = withContext(dispatchers.io) {
        if (config.id == 0L) {
            var insertedId = 0L
            database.snapAdbQueries.transaction {
                database.snapAdbQueries.insertMonkeyConfig(
                    name = config.name,
                    package_name = config.packageName,
                    event_count = config.eventCount.toLong(),
                    seed = config.seed?.toLong(),
                    throttle_ms = config.throttleMs.toLong(),
                    categories = config.categories.joinToString(","),
                    verbosity = config.verbosity.toLong(),
                    restrict_to_app = if (config.restrictToApp) 1L else 0L,
                    created_at = System.currentTimeMillis(),
                )
                insertedId = database.snapAdbQueries.lastInsertRowId().executeAsOne()
            }
            insertedId
        } else {
            database.snapAdbQueries.updateMonkeyConfig(
                name = config.name,
                package_name = config.packageName,
                event_count = config.eventCount.toLong(),
                seed = config.seed?.toLong(),
                throttle_ms = config.throttleMs.toLong(),
                categories = config.categories.joinToString(","),
                verbosity = config.verbosity.toLong(),
                restrict_to_app = if (config.restrictToApp) 1L else 0L,
                id = config.id,
            )
            config.id
        }
    }

    suspend fun deleteConfig(id: Long) = withContext(dispatchers.io) {
        database.snapAdbQueries.deleteMonkeyConfig(id)
    }

    suspend fun getAllRuns(): List<MonkeyRunSummary> = withContext(dispatchers.io) {
        database.snapAdbQueries.getAllMonkeyRuns().executeAsList().map { row ->
            MonkeyRunSummary(
                id = row.id,
                configName = row.config_name,
                packageName = row.package_name,
                deviceSerial = row.device_serial,
                startedAt = row.started_at,
                endedAt = row.ended_at,
                totalEvents = row.total_events.toInt(),
                injectedEvents = row.injected_events.toInt(),
                status = try {
                    MonkeyRunStatus.valueOf(row.status)
                } catch (_: Exception) {
                    MonkeyRunStatus.Completed
                },
                crashLog = row.crash_log,
                seed = row.seed?.toInt(),
            )
        }
    }

    suspend fun insertRun(
        configName: String,
        packageName: String,
        deviceSerial: String,
        totalEvents: Int,
        seed: Int?,
    ): Long = withContext(dispatchers.io) {
        var insertedId = 0L
        database.snapAdbQueries.transaction {
            database.snapAdbQueries.insertMonkeyRun(
                config_name = configName,
                package_name = packageName,
                device_serial = deviceSerial,
                started_at = System.currentTimeMillis(),
                total_events = totalEvents.toLong(),
                seed = seed?.toLong(),
            )
            insertedId = database.snapAdbQueries.lastInsertRowId().executeAsOne()
        }
        insertedId
    }

    suspend fun updateRunCompletion(
        id: Long,
        injectedEvents: Int,
        status: MonkeyRunStatus,
        crashLog: String?,
    ) = withContext(dispatchers.io) {
        database.snapAdbQueries.updateMonkeyRunCompletion(
            ended_at = System.currentTimeMillis(),
            injected_events = injectedEvents.toLong(),
            status = status.name,
            crash_log = crashLog,
            id = id,
        )
    }

    suspend fun deleteRun(id: Long) = withContext(dispatchers.io) {
        database.snapAdbQueries.deleteMonkeyRun(id)
    }
}
