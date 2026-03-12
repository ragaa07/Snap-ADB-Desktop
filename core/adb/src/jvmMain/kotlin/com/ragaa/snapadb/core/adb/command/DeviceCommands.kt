package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.AdbDevice
import com.ragaa.snapadb.core.adb.parser.DeviceParser

private val ADDRESS_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9.:\\-]*[a-zA-Z0-9]$")
private val PAIR_CODE_PATTERN = Regex("^\\d{6}$")

class ListDevices : AdbCommand<List<AdbDevice>> {
    override fun args(): List<String> = listOf("devices", "-l")
    override fun parse(result: ProcessResult): List<AdbDevice> = DeviceParser.parse(result.stdout)
}

class ConnectDevice(private val address: String) : AdbCommand<String> {
    init {
        require(address.matches(ADDRESS_PATTERN)) { "Invalid address: $address" }
    }

    override fun args(): List<String> = listOf("connect", address)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class DisconnectDevice(private val address: String) : AdbCommand<String> {
    init {
        require(address.matches(ADDRESS_PATTERN)) { "Invalid address: $address" }
    }

    override fun args(): List<String> = listOf("disconnect", address)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class PairDevice(private val address: String, private val code: String) : AdbCommand<String> {
    init {
        require(address.matches(ADDRESS_PATTERN)) { "Invalid address: $address" }
        require(code.matches(PAIR_CODE_PATTERN)) { "Invalid pairing code: $code" }
    }

    override fun args(): List<String> = listOf("pair", address, code)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
