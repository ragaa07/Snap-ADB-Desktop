package com.ragaa.snapadb.core.adb.model

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val size: Long = 0,
    val permissions: String = "",
    val owner: String = "",
    val group: String = "",
    val lastModified: String = "",
    val linkedTo: String = "",
) {
    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "%.1fKB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1fMB".format(size / (1024.0 * 1024))
            else -> "%.1fGB".format(size / (1024.0 * 1024 * 1024))
        }
}
