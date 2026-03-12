package com.ragaa.snapadb.core.adb.model

data class BatteryInfo(
    val level: Int,
    val status: BatteryStatus,
    val health: String,
    val plugged: PlugType,
    val temperature: Float,
)

enum class BatteryStatus {
    CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN;

    companion object {
        fun fromInt(value: Int): BatteryStatus = when (value) {
            2 -> CHARGING
            3 -> DISCHARGING
            4 -> NOT_CHARGING
            5 -> FULL
            else -> UNKNOWN
        }
    }
}

enum class PlugType {
    AC, USB, WIRELESS, NONE, UNKNOWN;

    companion object {
        fun fromInt(value: Int): PlugType = when (value) {
            0 -> NONE
            1 -> AC
            2 -> USB
            4 -> WIRELESS
            else -> UNKNOWN
        }
    }
}
