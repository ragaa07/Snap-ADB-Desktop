package com.ragaa.snapadb.core.adb

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class AdbClient(
    private val adbPath: AdbPath,
    private val adbProcess: AdbProcess
) {

    private fun resolveAdbPath(): String =
        adbPath.resolve() ?: throw AdbException.AdbNotFound()

    private fun buildArgs(commandArgs: List<String>, deviceSerial: String?): List<String> = buildList {
        add(resolveAdbPath())
        if (deviceSerial != null) {
            add("-s")
            add(deviceSerial)
        }
        addAll(commandArgs)
    }

    suspend fun <T> execute(command: AdbCommand<T>, deviceSerial: String? = null): Result<T> = runCatching {
        val args = buildArgs(command.args(), deviceSerial)
        val result = adbProcess.execute(args)

        if (result.exitCode != 0) {
            throw AdbException.CommandFailed(
                command = args.joinToString(" "),
                exitCode = result.exitCode,
                stderr = result.stderr
            )
        }

        command.parse(result)
    }

    suspend fun <T> execute(command: AdbCommand<T>, deviceSerial: String? = null, timeoutMs: Long): Result<T> = runCatching {
        val args = buildArgs(command.args(), deviceSerial)
        val result = adbProcess.execute(args, timeout = timeoutMs)

        if (result.exitCode != 0) {
            throw AdbException.CommandFailed(
                command = args.joinToString(" "),
                exitCode = result.exitCode,
                stderr = result.stderr
            )
        }

        command.parse(result)
    }

    fun <T> stream(command: StreamingAdbCommand<T>, deviceSerial: String? = null): Flow<T> {
        val args = buildArgs(command.args(), deviceSerial)
        return adbProcess.stream(args).mapNotNull { line ->
            command.parseLine(line)
        }
    }
}
