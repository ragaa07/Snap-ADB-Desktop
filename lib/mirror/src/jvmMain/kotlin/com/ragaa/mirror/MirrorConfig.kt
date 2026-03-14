package com.ragaa.mirror

data class MirrorConfig(
    val refreshIntervalMs: Long = 300,
    val downscale: Int = 2,
)
