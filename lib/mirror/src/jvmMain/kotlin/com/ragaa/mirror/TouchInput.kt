package com.ragaa.mirror

sealed class TouchInput {
    data class Tap(val x: Float, val y: Float) : TouchInput()
    data class Swipe(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val durationMs: Long = 300,
    ) : TouchInput()
    data class KeyEvent(val code: Int) : TouchInput() {
        companion object {
            const val KEYCODE_BACK = 4
            const val KEYCODE_HOME = 3
            const val KEYCODE_RECENTS = 187
        }
    }
}
