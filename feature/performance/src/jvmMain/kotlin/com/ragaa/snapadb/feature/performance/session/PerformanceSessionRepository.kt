package com.ragaa.snapadb.feature.performance.session

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.database.SnapAdbDatabase
import com.ragaa.snapadb.feature.performance.model.PerformanceDataPoint
import kotlinx.coroutines.withContext

data class PerformanceSessionSummary(
    val id: Long,
    val deviceSerial: String,
    val deviceName: String?,
    val appPackage: String?,
    val startedAt: Long,
    val endedAt: Long,
    val pollingIntervalMs: Long,
)

class PerformanceSessionRepository(
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun saveSession(
        serial: String,
        deviceName: String?,
        appPackage: String?,
        pollingIntervalMs: Long,
        seriesData: Map<String, List<PerformanceDataPoint>>,
    ): Long = withContext(dispatchers.io) {
        val allPoints = seriesData.values.flatten()
        val startedAt = allPoints.minOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
        val endedAt = allPoints.maxOfOrNull { it.timestampMs } ?: System.currentTimeMillis()

        var sessionId = 0L
        database.snapAdbQueries.transaction {
            database.snapAdbQueries.insertSession(
                device_serial = serial,
                device_name = deviceName,
                app_package = appPackage,
                started_at = startedAt,
                ended_at = endedAt,
                polling_interval_ms = pollingIntervalMs,
            )
            sessionId = database.snapAdbQueries.lastInsertRowId().executeAsOne()

            for ((seriesName, points) in seriesData) {
                for (point in points) {
                    database.snapAdbQueries.insertDataPoint(
                        session_id = sessionId,
                        series_name = seriesName,
                        timestamp_ms = point.timestampMs,
                        value_ = point.value.toDouble(),
                    )
                }
            }
        }
        sessionId
    }

    suspend fun getAllSessions(): List<PerformanceSessionSummary> = withContext(dispatchers.io) {
        database.snapAdbQueries.getAllSessions().executeAsList().map { row ->
            PerformanceSessionSummary(
                id = row.id,
                deviceSerial = row.device_serial,
                deviceName = row.device_name,
                appPackage = row.app_package,
                startedAt = row.started_at,
                endedAt = row.ended_at,
                pollingIntervalMs = row.polling_interval_ms,
            )
        }
    }

    suspend fun loadSession(sessionId: Long): Map<String, List<PerformanceDataPoint>> = withContext(dispatchers.io) {
        database.snapAdbQueries.getSessionDataPoints(sessionId).executeAsList()
            .groupBy { it.series_name }
            .mapValues { (_, rows) ->
                rows.map { PerformanceDataPoint(it.timestamp_ms, it.value_.toFloat()) }
            }
    }

    suspend fun deleteSession(sessionId: Long) = withContext(dispatchers.io) {
        database.snapAdbQueries.transaction {
            database.snapAdbQueries.deleteSessionDataPoints(sessionId)
            database.snapAdbQueries.deleteSession(sessionId)
        }
    }
}
