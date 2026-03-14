package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.CpuInfo
import com.ragaa.snapadb.core.adb.model.CpuRawReading
import com.ragaa.snapadb.core.adb.model.CpuTicks

object CpuParser {

    private val CPU_CORE_PATTERN = Regex("^cpu\\d+.*")
    private val WHITESPACE = Regex("\\s+")

    fun parseRawReading(output: String): CpuRawReading {
        val lines = output.lineSequence().filter { it.startsWith("cpu") }.toList()
        val total = parseCpuLine(lines.firstOrNull { it.startsWith("cpu ") } ?: "cpu 0 0 0 0 0 0 0 0")
        val perCore = lines.filter { it.matches(CPU_CORE_PATTERN) }.map { parseCpuLine(it) }
        return CpuRawReading(total = total, perCore = perCore)
    }

    fun computeUsage(prev: CpuRawReading, curr: CpuRawReading): CpuInfo {
        val overallPercent = computeDelta(prev.total, curr.total)
        val perCorePercent = curr.perCore.mapIndexed { index, currCore ->
            val prevCore = prev.perCore.getOrNull(index) ?: return@mapIndexed 0f
            computeDelta(prevCore, currCore)
        }
        return CpuInfo(overallPercent = overallPercent, perCorePercent = perCorePercent)
    }

    private fun computeDelta(prev: CpuTicks, curr: CpuTicks): Float {
        val totalDelta = curr.total - prev.total
        val activeDelta = curr.active - prev.active
        return if (totalDelta > 0) (activeDelta.toFloat() / totalDelta.toFloat()) * 100f else 0f
    }

    private fun parseCpuLine(line: String): CpuTicks {
        val parts = line.trim().split(WHITESPACE)
        // parts[0] is "cpu" or "cpuN", parts[1..] are tick values
        return CpuTicks(
            user = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            system = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
            softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
            steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L,
        )
    }
}
