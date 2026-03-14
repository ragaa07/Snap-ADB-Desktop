package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.AppMemoryInfo

object AppMemoryParser {

    /**
     * Parses output of `adb shell dumpsys meminfo <package>`.
     * Looks for the "App Summary" section with lines like:
     *   Java Heap:    12345
     *   Native Heap:  23456
     *   Code:          3456
     *   Stack:          456
     *   Graphics:      5678
     *   System:        6789
     *   TOTAL PSS:   52340
     * Falls back to TOTAL line if App Summary not found.
     */
    fun parse(output: String, packageName: String): AppMemoryInfo {
        val lines = output.lines()

        // Try to find "App Summary" section
        val summaryStart = lines.indexOfFirst { it.trim().startsWith("App Summary") }

        var totalPss = 0L
        var javaHeap = 0L
        var nativeHeap = 0L
        var code = 0L
        var stack = 0L
        var graphics = 0L
        var system = 0L

        if (summaryStart >= 0) {
            for (i in summaryStart until lines.size) {
                val line = lines[i].trim()
                when {
                    line.startsWith("Java Heap:") -> javaHeap = extractKb(line)
                    line.startsWith("Native Heap:") -> nativeHeap = extractKb(line)
                    line.startsWith("Code:") -> code = extractKb(line)
                    line.startsWith("Stack:") -> stack = extractKb(line)
                    line.startsWith("Graphics:") -> graphics = extractKb(line)
                    line.startsWith("System:") -> system = extractKb(line)
                    line.startsWith("TOTAL PSS:") || line.startsWith("TOTAL:") -> {
                        totalPss = extractKb(line)
                        break
                    }
                }
            }
        } else {
            // Fallback: look for TOTAL line anywhere
            val totalLine = lines.firstOrNull { it.trim().startsWith("TOTAL") && "TOTAL PSS" !in it }
            if (totalLine != null) {
                val parts = totalLine.trim().split(Regex("\\s+"))
                totalPss = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            }
        }

        if (totalPss == 0L) {
            totalPss = javaHeap + nativeHeap + code + stack + graphics + system
        }

        return AppMemoryInfo(
            packageName = packageName,
            totalPssKb = totalPss,
            javaHeapKb = javaHeap,
            nativeHeapKb = nativeHeap,
            codeKb = code,
            stackKb = stack,
            graphicsKb = graphics,
            systemKb = system,
        )
    }

    private fun extractKb(line: String): Long {
        // Extract the last number on the line (which is the KB value)
        val numbers = Regex("\\d+").findAll(line).toList()
        return numbers.lastOrNull()?.value?.toLongOrNull() ?: 0L
    }
}
