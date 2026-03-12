package com.ragaa.snapadb.core.adb.model

data class AdbDevice(
    val serial: String,
    val state: DeviceState,
    val product: String = "",
    val model: String = "",
    val device: String = "",
    val transportId: String = ""
)

enum class DeviceState {
    DEVICE, OFFLINE, UNAUTHORIZED, AUTHORIZING, NO_PERMISSIONS, UNKNOWN;

    companion object {
        fun fromString(value: String): DeviceState = when (value.lowercase()) {
            "device" -> DEVICE
            "offline" -> OFFLINE
            "unauthorized" -> UNAUTHORIZED
            "authorizing" -> AUTHORIZING
            "no permissions" -> NO_PERMISSIONS
            else -> UNKNOWN
        }
    }
}
