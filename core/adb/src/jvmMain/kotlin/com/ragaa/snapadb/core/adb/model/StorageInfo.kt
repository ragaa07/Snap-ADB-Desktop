package com.ragaa.snapadb.core.adb.model

data class StorageInfo(
    val filesystem: String,
    val size: String,
    val used: String,
    val available: String,
    val usePercent: Int,
    val mountedOn: String,
)
