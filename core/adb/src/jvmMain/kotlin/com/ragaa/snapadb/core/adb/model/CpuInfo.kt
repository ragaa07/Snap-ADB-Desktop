package com.ragaa.snapadb.core.adb.model

data class CpuTicks(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
) {
    val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
    val active: Long get() = total - idle - iowait
}

data class CpuRawReading(
    val total: CpuTicks,
    val perCore: List<CpuTicks>,
)

data class CpuInfo(
    val overallPercent: Float,
    val perCorePercent: List<Float>,
)
