package com.ragaa.snapadb.feature.bugreporter.model

import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.StorageInfo

class ScreenshotData(val bytes: ByteArray) {
    private val cachedHashCode: Int = bytes.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScreenshotData) return false
        return cachedHashCode == other.cachedHashCode && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = cachedHashCode
}

data class BugReport(
    val timestamp: String,
    val title: String,
    val description: String,
    val reproSteps: String,
    val deviceInfo: DeviceSnapshot,
    val batteryInfo: BatteryInfo?,
    val memoryInfo: MemoryInfo?,
    val storageInfo: List<StorageInfo>?,
    val appInfo: AppInfo?,
    val screenshot: ScreenshotData?,
    val logcat: String,
)

data class DeviceSnapshot(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: String,
    val buildId: String,
    val serial: String,
)
