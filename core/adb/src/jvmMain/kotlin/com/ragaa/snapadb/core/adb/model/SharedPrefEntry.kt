package com.ragaa.snapadb.core.adb.model

enum class SharedPrefType(val xmlTag: String) {
    STRING("string"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    BOOLEAN("boolean"),
    STRING_SET("set"),
}

data class SharedPrefEntry(
    val key: String,
    val value: String,
    val type: SharedPrefType,
    val setValues: List<String> = emptyList(),
) {
    val displayValue: String
        get() = if (type == SharedPrefType.STRING_SET) {
            setValues.joinToString(", ")
        } else {
            value
        }
}
