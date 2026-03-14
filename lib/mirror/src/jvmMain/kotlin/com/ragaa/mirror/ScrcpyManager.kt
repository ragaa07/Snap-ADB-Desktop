package com.ragaa.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

class ScrcpyManager(private val installDir: File = defaultInstallDir()) {

    fun resolveScrcpyPath(): String? {
        val binaryName = if (isWindows()) "scrcpy.exe" else "scrcpy"
        val localBinary = File(installDir, binaryName)
        if (localBinary.canExecute()) return localBinary.absolutePath

        // Check system PATH
        return findOnPath(binaryName)
    }

    fun isInstalled(): Boolean = resolveScrcpyPath() != null

    fun download(version: String = SCRCPY_VERSION): Flow<DownloadProgress> = flow {
        installDir.mkdirs()

        val (url, archiveName) = resolveDownloadUrl(version)
        val archiveFile = File(installDir, archiveName)

        try {
            // Download with manual redirect handling (GitHub 302 → CDN)
            val finalConnection = openWithRedirects(url)
            val totalBytes = finalConnection.contentLengthLong

            try {
                var downloaded = 0L
                finalConnection.inputStream.use { input ->
                    FileOutputStream(archiveFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            emit(DownloadProgress(downloaded, totalBytes))
                        }
                    }
                }
            } finally {
                finalConnection.disconnect()
            }

            // Extract
            extract(archiveFile)

            // Set executable on Unix
            if (!isWindows()) {
                val binary = File(installDir, "scrcpy")
                if (binary.exists()) binary.setExecutable(true)

                // macOS: clear quarantine
                if (isMacOs()) {
                    try {
                        ProcessBuilder(listOf("xattr", "-cr", installDir.absolutePath))
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    } catch (_: Exception) {
                        // Non-critical
                    }
                }
            }
        } finally {
            archiveFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun extract(archiveFile: File) {
        if (archiveFile.name.endsWith(".zip")) {
            extractZip(archiveFile)
        } else {
            // tar.gz — use system tar
            val exitCode = ProcessBuilder(
                listOf("tar", "xzf", archiveFile.absolutePath, "-C", installDir.absolutePath)
            )
                .redirectErrorStream(true)
                .start()
                .waitFor()
            require(exitCode == 0) { "tar extraction failed with exit code $exitCode" }
            flattenSingleSubdir()
        }
    }

    private fun extractZip(zipFile: File) {
        val canonicalInstallDir = installDir.canonicalPath + File.separator
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val currentEntry = entry
                val outputFile = File(installDir, currentEntry.name)
                // Zip Slip protection
                require(outputFile.canonicalPath.startsWith(canonicalInstallDir) ||
                    outputFile.canonicalPath == installDir.canonicalPath) {
                    "Zip entry outside target dir: ${currentEntry.name}"
                }
                if (currentEntry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        flattenSingleSubdir()
    }

    private fun flattenSingleSubdir() {
        val children = installDir.listFiles() ?: return
        val subdirs = children.filter { it.isDirectory }
        if (subdirs.size == 1 && children.count { it.isFile } == 0) {
            val subdir = subdirs.first()
            subdir.listFiles()?.forEach { file ->
                val target = File(installDir, file.name)
                if (!file.renameTo(target)) {
                    // Fallback: copy + delete
                    file.copyTo(target, overwrite = true)
                    file.delete()
                }
            }
            subdir.deleteRecursively()
        }
    }

    private fun openWithRedirects(url: String): HttpURLConnection {
        var currentUrl = url
        var redirects = 0
        while (redirects < 10) {
            val conn = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false // Handle manually to support cross-host
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw Exception("Redirect with no Location header")
                conn.disconnect()
                // Resolve relative URLs
                currentUrl = if (location.startsWith("http")) location else URI(currentUrl).resolve(location).toString()
                redirects++
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw Exception("HTTP $code downloading scrcpy from $currentUrl")
            }
        }
        throw Exception("Too many redirects downloading scrcpy")
    }

    private fun resolveDownloadUrl(version: String): Pair<String, String> {
        val base = "https://github.com/Genymobile/scrcpy/releases/download/v$version"
        return when {
            isWindows() -> {
                val name = "scrcpy-win64-v$version.zip"
                Pair("$base/$name", name)
            }
            isMacOs() -> {
                val arch = if (System.getProperty("os.arch") == "aarch64") "aarch64" else "x86_64"
                val name = "scrcpy-macos-$arch-v$version.tar.gz"
                Pair("$base/$name", name)
            }
            else -> {
                val name = "scrcpy-linux-x86_64-v$version.tar.gz"
                Pair("$base/$name", name)
            }
        }
    }

    private fun findOnPath(binaryName: String): String? {
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
        return pathDirs
            .map { File(it, binaryName) }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    companion object {
        const val SCRCPY_VERSION = "3.3.4"

        fun defaultInstallDir(): File = File(System.getProperty("user.home"), ".snapadb/scrcpy")

        private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
        private fun isMacOs(): Boolean = System.getProperty("os.name").lowercase().contains("mac")
    }
}

data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else -1f
}
