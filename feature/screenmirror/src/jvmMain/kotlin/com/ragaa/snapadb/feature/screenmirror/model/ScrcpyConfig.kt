package com.ragaa.snapadb.feature.screenmirror.model

data class ScrcpyConfig(
    val maxResolution: Int = 1024,
    val bitrate: Int = 8_000_000,
    val maxFps: Int = 60,
    val borderless: Boolean = true,
    val alwaysOnTop: Boolean = false,
    val showTouches: Boolean = false,
    val stayAwake: Boolean = true,
    val turnScreenOff: Boolean = false,
) {
    fun toArgs(): List<String> = buildList {
        add("--max-size=$maxResolution")
        add("--video-bit-rate=$bitrate")
        add("--max-fps=$maxFps")
        if (borderless) add("--window-borderless")
        if (alwaysOnTop) add("--always-on-top")
        if (showTouches) add("--show-touches")
        if (stayAwake) add("--stay-awake")
        if (turnScreenOff) add("--turn-screen-off")
    }
}
