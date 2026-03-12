package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.FileEntry

object FileParser {

    // ls -la output: permissions links owner group size date time name [-> target]
    // Example: drwxrwx--x  5 root sdcard_rw 4096 2024-01-15 10:30 Android
    // Example: lrwxrwxrwx  1 root root       15 2024-01-15 10:30 sdcard -> /storage/emulated/0
    private val LS_LINE = Regex(
        """^([dlcbps\-][rwxsStT\-]{9})\s+\d+\s+(\S+)\s+(\S+)\s+([\d,]+)\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})\s+(.+)$"""
    )

    fun parse(output: String, parentPath: String): List<FileEntry> =
        output.lineSequence()
            .mapNotNull { line -> parseLine(line.trim(), parentPath) }
            .filter { it.name != "." && it.name != ".." }
            .toList()

    private fun parseLine(line: String, parentPath: String): FileEntry? {
        val match = LS_LINE.matchEntire(line) ?: return null
        val (permissions, owner, group, sizeStr, dateTime, nameField) = match.destructured

        val isDirectory = permissions.startsWith('d')
        val isSymlink = permissions.startsWith('l')

        val (name, linkedTo) = if (isSymlink && " -> " in nameField) {
            val parts = nameField.split(" -> ", limit = 2)
            parts[0] to parts[1]
        } else {
            nameField to ""
        }

        val normalizedParent = parentPath.trimEnd('/')
        val path = "$normalizedParent/$name"

        return FileEntry(
            name = name,
            path = path,
            isDirectory = isDirectory,
            isSymlink = isSymlink,
            size = sizeStr.replace(",", "").toLongOrNull() ?: 0,
            permissions = permissions,
            owner = owner,
            group = group,
            lastModified = dateTime,
            linkedTo = linkedTo,
        )
    }
}
