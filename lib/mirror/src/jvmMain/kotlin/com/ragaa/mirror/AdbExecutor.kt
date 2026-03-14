package com.ragaa.mirror

interface AdbExecutor {
    suspend fun exec(args: List<String>): ByteArray
    suspend fun execText(args: List<String>): String
}
