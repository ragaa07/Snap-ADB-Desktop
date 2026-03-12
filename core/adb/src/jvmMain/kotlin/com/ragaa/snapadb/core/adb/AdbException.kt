package com.ragaa.snapadb.core.adb

sealed class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AdbNotFound(message: String = "ADB binary not found") : AdbException(message)
    class DeviceNotFound(serial: String) : AdbException("Device not found: $serial")
    class Timeout(command: String) : AdbException("Command timed out: $command")
    class CommandFailed(command: String, exitCode: Int, stderr: String) :
        AdbException("Command failed ($exitCode): $command\n$stderr")
    class Unauthorized(serial: String) : AdbException("Device unauthorized: $serial")
    class DeviceOffline(serial: String) : AdbException("Device offline: $serial")
}
