package com.ragaa.snapadb.feature.performance.model

data class PerformanceDataPoint(
    val timestampMs: Long,
    val value: Float,
)

class RollingTimeSeries(private val maxSize: Int = 60) {
    private val data = ArrayDeque<PerformanceDataPoint>(maxSize)

    fun add(point: PerformanceDataPoint) {
        if (data.size >= maxSize) data.removeFirst()
        data.addLast(point)
    }

    fun snapshot(): List<PerformanceDataPoint> = data.toList()

    fun clear() = data.clear()

    val size: Int get() = data.size
}
