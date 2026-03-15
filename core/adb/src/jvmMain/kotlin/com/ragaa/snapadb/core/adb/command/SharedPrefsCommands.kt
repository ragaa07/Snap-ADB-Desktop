package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.SharedPrefEntry
import com.ragaa.snapadb.core.adb.parser.SharedPrefsParser

private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")
private val SAFE_FILENAME_PATTERN = Regex("^[a-zA-Z0-9_.-]+\\.xml$")

// Shell-quote a string by wrapping in single quotes and escaping embedded single quotes.
// Safe for all input since packageName/fileName are regex-validated, but applied as defense-in-depth.
private fun shellQuote(s: String): String = "'${s.replace("'", "'\\''")}'"

class ListSharedPrefsFiles(private val packageName: String) : AdbCommand<List<String>> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> = listOf(
        "shell", "run-as $packageName ls /data/data/$packageName/shared_prefs/"
    )

    override fun parse(result: ProcessResult): List<String> =
        result.stdout.lines()
            .map { it.trim() }
            .filter { it.endsWith(".xml") }
}

class ReadSharedPrefsFile(
    private val packageName: String,
    private val fileName: String,
) : AdbCommand<List<SharedPrefEntry>> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        require(fileName.matches(SAFE_FILENAME_PATTERN)) { "Invalid file name: $fileName" }
    }

    override fun args(): List<String> = listOf(
        "shell", "run-as $packageName cat /data/data/$packageName/shared_prefs/$fileName"
    )

    override fun parse(result: ProcessResult): List<SharedPrefEntry> =
        SharedPrefsParser.parse(result.stdout)
}

class ReadSharedPrefsFileRaw(
    private val packageName: String,
    private val fileName: String,
) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        require(fileName.matches(SAFE_FILENAME_PATTERN)) { "Invalid file name: $fileName" }
    }

    override fun args(): List<String> = listOf(
        "shell", "run-as $packageName cat /data/data/$packageName/shared_prefs/$fileName"
    )

    override fun parse(result: ProcessResult): String = result.stdout
}

class WriteSharedPrefsFile(
    private val packageName: String,
    private val fileName: String,
    private val content: String,
) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        require(fileName.matches(SAFE_FILENAME_PATTERN)) { "Invalid file name: $fileName" }
    }

    override fun args(): List<String> {
        // Base64 encode to safely transfer content through shell.
        // Use NO_WRAP to avoid newlines in the encoded output.
        val encoded = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
        val quotedEncoded = shellQuote(encoded)
        val destPath = "/data/data/$packageName/shared_prefs/$fileName"
        return listOf(
            "shell",
            "echo $quotedEncoded | base64 -d | run-as $packageName sh -c 'cat > $destPath'"
        )
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class DeleteSharedPrefsFile(
    private val packageName: String,
    private val fileName: String,
) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        require(fileName.matches(SAFE_FILENAME_PATTERN)) { "Invalid file name: $fileName" }
    }

    override fun args(): List<String> = listOf(
        "shell", "run-as $packageName rm /data/data/$packageName/shared_prefs/$fileName"
    )

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
