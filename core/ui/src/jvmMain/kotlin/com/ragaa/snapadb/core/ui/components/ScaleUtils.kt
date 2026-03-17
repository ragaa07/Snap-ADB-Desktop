package com.ragaa.snapadb.core.ui.components

fun normalizeScale(value: String): String {
    val f = value.toFloatOrNull() ?: return value
    return if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()
}
