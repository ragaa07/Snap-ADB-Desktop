package com.ragaa.snapadb.feature.notification.model

data class NotificationPayload(
    val tag: String,
    val title: String,
    val text: String,
) {
    fun toJson(): String = """{"tag":"${escapeJson(tag)}","title":"${escapeJson(title)}","text":"${escapeJson(text)}"}"""

    companion object {
        fun fromJson(json: String): NotificationPayload {
            val map = mutableMapOf<String, String>()
            val content = json.trim().removeSurrounding("{", "}")
            val pattern = Regex(""""(\w+)":"((?:[^"\\]|\\.)*)"""")
            pattern.findAll(content).forEach { match ->
                map[match.groupValues[1]] = unescapeJson(match.groupValues[2])
            }
            return NotificationPayload(
                tag = map["tag"] ?: "",
                title = map["title"] ?: "",
                text = map["text"] ?: "",
            )
        }

        private fun escapeJson(value: String): String =
            value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        private fun unescapeJson(value: String): String =
            value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
    }
}

data class NotificationTemplate(
    val id: Long,
    val name: String,
    val payload: NotificationPayload,
    val createdAt: Long,
)
