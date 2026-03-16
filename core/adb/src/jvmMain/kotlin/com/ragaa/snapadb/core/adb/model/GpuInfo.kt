package com.ragaa.snapadb.core.adb.model

data class GpuInfo(
    val totalFrames: Long,
    val jankyFrames: Long,
    val percentile50Ms: Float,
    val percentile90Ms: Float,
    val percentile95Ms: Float,
    val percentile99Ms: Float,
) {
    val jankyPercent: Float
        get() = if (totalFrames > 0) (jankyFrames.toFloat() / totalFrames.toFloat()) * 100f else 0f
}
