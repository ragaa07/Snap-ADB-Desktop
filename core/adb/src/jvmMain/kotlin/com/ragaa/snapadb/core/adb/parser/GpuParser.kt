package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.GpuInfo

object GpuParser {

    private val TOTAL_FRAMES = Regex("Total frames rendered:\\s*(\\d+)")
    private val JANKY_FRAMES = Regex("Janky frames:\\s*(\\d+)")
    private val PERCENTILE_50 = Regex("50th percentile:\\s*([\\d.]+)ms")
    private val PERCENTILE_90 = Regex("90th percentile:\\s*([\\d.]+)ms")
    private val PERCENTILE_95 = Regex("95th percentile:\\s*([\\d.]+)ms")
    private val PERCENTILE_99 = Regex("99th percentile:\\s*([\\d.]+)ms")

    fun parse(output: String): GpuInfo? {
        val totalFrames = TOTAL_FRAMES.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val jankyFrames = JANKY_FRAMES.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val p50 = PERCENTILE_50.find(output)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val p90 = PERCENTILE_90.find(output)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val p95 = PERCENTILE_95.find(output)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val p99 = PERCENTILE_99.find(output)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

        return GpuInfo(
            totalFrames = totalFrames,
            jankyFrames = jankyFrames,
            percentile50Ms = p50,
            percentile90Ms = p90,
            percentile95Ms = p95,
            percentile99Ms = p99,
        )
    }
}
