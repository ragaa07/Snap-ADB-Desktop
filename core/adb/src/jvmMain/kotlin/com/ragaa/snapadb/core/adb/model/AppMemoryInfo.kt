package com.ragaa.snapadb.core.adb.model

data class AppMemoryInfo(
    val packageName: String,
    val totalPssKb: Long,
    val javaHeapKb: Long,
    val nativeHeapKb: Long,
    val codeKb: Long,
    val stackKb: Long,
    val graphicsKb: Long,
    val systemKb: Long,
)
