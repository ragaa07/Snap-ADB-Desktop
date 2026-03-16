package com.ragaa.snapadb.feature.performance.model

data class ThresholdConfig(
    val cpuPercent: Float? = null,
    val memoryPercent: Float? = null,
    val batteryTempC: Float? = null,
)

data class ThresholdAlert(
    val metric: String,
    val currentValue: Float,
    val threshold: Float,
    val timestampMs: Long,
)
