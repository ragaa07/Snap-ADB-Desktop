package com.ragaa.snapadb.core.adb.model

data class NetworkIoInfo(
    val rxBytes: Long,
    val txBytes: Long,
    val timestampMs: Long,
)

data class NetworkIoRate(
    val rxBytesPerSec: Float,
    val txBytesPerSec: Float,
)
