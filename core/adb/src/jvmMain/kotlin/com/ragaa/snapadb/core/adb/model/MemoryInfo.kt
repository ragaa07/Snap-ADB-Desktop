package com.ragaa.snapadb.core.adb.model

data class MemoryInfo(
    val totalKb: Long,
    val freeKb: Long,
    val availableKb: Long,
    val buffersKb: Long,
    val cachedKb: Long,
) {
    val usedKb: Long get() = totalKb - availableKb
    val usagePercent: Int get() = if (totalKb > 0) ((usedKb * 100) / totalKb).toInt() else 0

    fun totalMb(): String = "%.0f MB".format(totalKb / 1024.0)
    fun usedMb(): String = "%.0f MB".format(usedKb / 1024.0)
    fun availableMb(): String = "%.0f MB".format(availableKb / 1024.0)
}
