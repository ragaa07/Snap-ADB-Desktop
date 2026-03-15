package com.ragaa.snapadb.core.adb

import com.ragaa.snapadb.common.DispatcherProvider
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LabelResolver(
    private val adbPath: AdbPath,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Resolves labels in batches, calling [onBatchResolved] after each batch
     * so the UI can update progressively without position shifts.
     *
     * Pipeline per batch:
     * 1. Push+execute shell script to extract AndroidManifest.xml + resources.arsc
     * 2. Compress with tar+gzip on device, pull single file
     * 3. Extract locally, create mini APKs, parse with apk-parser library (no aapt2 needed)
     */
    suspend fun resolveLabels(
        apps: List<Pair<String, String>>,
        deviceSerial: String,
        batchSize: Int = 15,
        onBatchResolved: suspend (Map<String, String>) -> Unit = {},
    ): Map<String, String> {
        val adb = adbPath.resolve() ?: return emptyMap()
        if (apps.isEmpty()) return emptyMap()

        val allLabels = mutableMapOf<String, String>()

        return withContext(dispatchers.io) {
            for (batch in apps.chunked(batchSize)) {
                val batchLabels = resolveBatch(batch, deviceSerial, adb)
                if (batchLabels.isNotEmpty()) {
                    allLabels.putAll(batchLabels)
                    onBatchResolved(batchLabels)
                }
            }
            allLabels
        }
    }

    private fun resolveBatch(
        apps: List<Pair<String, String>>,
        deviceSerial: String,
        adb: String,
    ): Map<String, String> {
        val deviceTmpDir = "/data/local/tmp/_snapadb_labels"
        val deviceScript = "/data/local/tmp/_snapadb_extract.sh"
        val deviceArchive = "/data/local/tmp/_snapadb_labels.tar.gz"
        val localTmpDir = File.createTempFile("snapadb_", "_labels").apply { delete() }
        val localArchive = File.createTempFile("snapadb_", ".tar.gz")

        try {
            // Step 1: Push + execute extract script
            val localScript = File.createTempFile("snapadb_extract_", ".sh")
            try {
                localScript.writeText(buildString {
                    appendLine("#!/bin/sh")
                    appendLine("rm -rf $deviceTmpDir $deviceArchive")
                    appendLine("mkdir -p $deviceTmpDir")
                    for ((pkg, apkPath) in apps) {
                        val safeApk = apkPath.replace("'", "'\\''")
                        appendLine("mkdir -p $deviceTmpDir/$pkg && unzip -o '$safeApk' AndroidManifest.xml resources.arsc -d $deviceTmpDir/$pkg >/dev/null 2>&1")
                    }
                    appendLine("cd /data/local/tmp && tar cf - _snapadb_labels | gzip > $deviceArchive")
                    appendLine("echo DONE")
                })
                exec(adb, "-s", deviceSerial, "push", localScript.absolutePath, deviceScript)
            } finally {
                localScript.delete()
            }

            exec(adb, "-s", deviceSerial, "shell", "sh $deviceScript")

            // Step 2: Pull compressed archive
            exec(adb, "-s", deviceSerial, "pull", deviceArchive, localArchive.absolutePath)

            // Cleanup device
            ProcessBuilder(adb, "-s", deviceSerial, "shell", "rm -rf $deviceTmpDir $deviceScript $deviceArchive")
                .redirectErrorStream(true).start()

            // Step 3: Extract locally
            localTmpDir.mkdirs()
            val tarProcess = ProcessBuilder("tar", "xzf", localArchive.absolutePath, "-C", localTmpDir.absolutePath)
                .redirectErrorStream(true).start()
            tarProcess.inputStream.bufferedReader().readText()
            tarProcess.waitFor()

            val pkgParentDir = File(localTmpDir, "_snapadb_labels").takeIf { it.isDirectory } ?: localTmpDir

            // Step 4: Parse labels with in-JVM apk-parser (no aapt2 needed)
            val labels = mutableMapOf<String, String>()
            for ((pkg, _) in apps) {
                val label = resolveFromExtracted(pkgParentDir, pkg)
                if (label.isNotEmpty()) labels[pkg] = label
            }
            return labels
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            localTmpDir.deleteRecursively()
            localArchive.delete()
        }
    }

    private fun exec(vararg args: String): String {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    private fun resolveFromExtracted(parentDir: File, pkg: String): String {
        val pkgDir = File(parentDir, pkg)
        val manifest = File(pkgDir, "AndroidManifest.xml")
        val resources = File(pkgDir, "resources.arsc")
        if (!manifest.exists() || !resources.exists()) return ""

        val miniApk = File.createTempFile("snapadb_mini_", ".apk")
        try {
            // Create a minimal APK containing only manifest + resources
            ZipOutputStream(FileOutputStream(miniApk)).use { zos ->
                for (file in listOf(manifest, resources)) {
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            if (miniApk.length() == 0L) return ""

            // Parse label using in-JVM library — no aapt2 process needed
            ApkFile(miniApk).use { apkFile ->
                apkFile.preferredLocale = Locale.getDefault()
                return apkFile.apkMeta.label ?: ""
            }
        } catch (_: Exception) {
            return ""
        } finally {
            miniApk.delete()
        }
    }

    fun isAvailable(): Boolean = true
}
