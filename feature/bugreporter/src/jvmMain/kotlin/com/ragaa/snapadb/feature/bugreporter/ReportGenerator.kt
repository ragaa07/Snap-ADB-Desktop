package com.ragaa.snapadb.feature.bugreporter

import com.ragaa.snapadb.feature.bugreporter.model.BugReport
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportGenerator {

    private fun String.escapeMd(): String = replace("|", "\\|")

    fun generateMarkdown(report: BugReport): String = buildString {
        appendLine("# ${report.title}")
        appendLine()
        appendLine("**Date:** ${report.timestamp}")
        appendLine()

        appendLine("## Description")
        appendLine(report.description.ifBlank { "_No description provided_" })
        appendLine()

        appendLine("## Device Info")
        appendLine("| Property | Value |")
        appendLine("|----------|-------|")
        with(report.deviceInfo) {
            appendLine("| Model | ${model.escapeMd()} |")
            appendLine("| Manufacturer | ${manufacturer.escapeMd()} |")
            appendLine("| Android Version | ${androidVersion.escapeMd()} |")
            appendLine("| SDK Version | ${sdkVersion.escapeMd()} |")
            appendLine("| Build ID | ${buildId.escapeMd()} |")
            appendLine("| Serial | ${serial.escapeMd()} |")
        }
        appendLine()

        if (report.appInfo != null) {
            appendLine("## App Info")
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            with(report.appInfo) {
                appendLine("| Package | ${packageName.escapeMd()} |")
                appendLine("| Version | ${versionName.escapeMd()} ($versionCode) |")
                appendLine("| APK Path | ${apkPath.escapeMd()} |")
                appendLine("| System App | $isSystemApp |")
            }
            appendLine()
        }

        if (report.batteryInfo != null) {
            appendLine("## Battery")
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            with(report.batteryInfo) {
                appendLine("| Level | $level% |")
                appendLine("| Status | $status |")
                appendLine("| Health | ${health.escapeMd()} |")
                appendLine("| Temperature | ${"%.1f".format(temperature)}°C |")
            }
            appendLine()
        }

        if (report.memoryInfo != null) {
            appendLine("## Memory")
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            with(report.memoryInfo) {
                appendLine("| Total | ${totalMb()} |")
                appendLine("| Used | ${usedMb()} |")
                appendLine("| Available | ${availableMb()} |")
                appendLine("| Usage | $usagePercent% |")
            }
            appendLine()
        }

        if (!report.storageInfo.isNullOrEmpty()) {
            appendLine("## Storage")
            appendLine("| Filesystem | Size | Used | Available | Use% | Mount |")
            appendLine("|------------|------|------|-----------|------|-------|")
            report.storageInfo.forEach { s ->
                appendLine("| ${s.filesystem.escapeMd()} | ${s.size.escapeMd()} | ${s.used.escapeMd()} | ${s.available.escapeMd()} | ${s.usePercent}% | ${s.mountedOn.escapeMd()} |")
            }
            appendLine()
        }

        appendLine("## Reproduction Steps")
        appendLine(report.reproSteps)
        appendLine()

        if (report.screenshot != null) {
            appendLine("## Screenshot")
            appendLine("![Screenshot](screenshot.png)")
            appendLine()
        }

        appendLine("## Logcat")
        appendLine("```")
        val lines = report.logcat.lines()
        if (lines.size > 200) {
            appendLine("(showing last 200 lines of ${lines.size} total — full log in logcat.txt)")
            appendLine(lines.takeLast(200).joinToString("\n"))
        } else {
            appendLine(report.logcat)
        }
        appendLine("```")
    }

    fun exportZip(report: BugReport, outputFile: File) {
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("report.md"))
            zip.write(generateMarkdown(report).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("logcat.txt"))
            zip.write(report.logcat.toByteArray())
            zip.closeEntry()

            if (report.screenshot != null) {
                zip.putNextEntry(ZipEntry("screenshot.png"))
                zip.write(report.screenshot.bytes)
                zip.closeEntry()
            }
        }
    }
}
