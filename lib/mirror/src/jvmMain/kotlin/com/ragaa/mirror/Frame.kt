package com.ragaa.mirror

data class Frame(
    val pngBytes: ByteArray,
    val width: Int,
    val height: Int,
    val timestampMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return timestampMs == other.timestampMs &&
            width == other.width &&
            height == other.height &&
            pngBytes.contentEquals(other.pngBytes)
    }

    override fun hashCode(): Int {
        var result = pngBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
