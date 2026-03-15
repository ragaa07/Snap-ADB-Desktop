package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.SharedPrefEntry
import com.ragaa.snapadb.core.adb.model.SharedPrefType

object SharedPrefsParser {

    fun parse(xml: String): List<SharedPrefEntry> {
        val entries = mutableListOf<SharedPrefEntry>()
        val lines = xml.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // <string name="key">value</string> (single-line)
            STRING_PATTERN.find(line)?.let { match ->
                val key = unescapeXml(match.groupValues[1])
                val value = unescapeXml(match.groupValues[2])
                entries.add(SharedPrefEntry(key, value, SharedPrefType.STRING))
                i++
                return@let
            } ?: run {
                // Multi-line string: <string name="key">value\n...\n</string>
                MULTILINE_STRING_OPEN.find(line)?.let { match ->
                    val key = unescapeXml(match.groupValues[1])
                    val valueStart = match.groupValues[2]
                    val sb = StringBuilder(valueStart)
                    i++
                    var found = false
                    while (i < lines.size) {
                        val nextLine = lines[i]
                        val endIdx = nextLine.indexOf("</string>")
                        if (endIdx >= 0) {
                            sb.append("\n").append(nextLine.substring(0, endIdx))
                            entries.add(SharedPrefEntry(key, unescapeXml(sb.toString()), SharedPrefType.STRING))
                            i++
                            found = true
                            break
                        }
                        sb.append("\n").append(nextLine)
                        i++
                    }
                    // Fix #10: If closing tag was never found, add partial entry rather than silently dropping
                    if (!found) {
                        entries.add(SharedPrefEntry(key, unescapeXml(sb.toString()), SharedPrefType.STRING))
                    }
                } ?:

                // <int name="key" value="123" />
                TYPED_PATTERN.find(line)?.let { match ->
                    val tag = match.groupValues[1]
                    val key = unescapeXml(match.groupValues[2])
                    val value = match.groupValues[3]
                    val type = SharedPrefType.entries.find { it.xmlTag == tag }
                    if (type != null) {
                        entries.add(SharedPrefEntry(key, value, type))
                    }
                    i++
                } ?:

                // <set name="key">
                SET_OPEN_PATTERN.find(line)?.let { match ->
                    val key = unescapeXml(match.groupValues[1])
                    val values = mutableListOf<String>()
                    i++
                    while (i < lines.size) {
                        val setLine = lines[i].trim()
                        if (setLine == "</set>") {
                            i++
                            break
                        }
                        SET_VALUE_PATTERN.find(setLine)?.let { vm ->
                            values.add(unescapeXml(vm.groupValues[1]))
                        }
                        i++
                    }
                    // Fix #4: Store set values in dedicated list, not comma-joined string
                    entries.add(SharedPrefEntry(key, "", SharedPrefType.STRING_SET, setValues = values))
                } ?: run {
                    i++
                }
            }
        }
        return entries
    }

    fun toXml(entries: List<SharedPrefEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>""")
        sb.appendLine("<map>")
        for (entry in entries) {
            when (entry.type) {
                SharedPrefType.STRING -> {
                    sb.appendLine("""    <string name="${escapeXml(entry.key)}">${escapeXml(entry.value)}</string>""")
                }
                SharedPrefType.INT -> {
                    sb.appendLine("""    <int name="${escapeXml(entry.key)}" value="${entry.value}" />""")
                }
                SharedPrefType.LONG -> {
                    sb.appendLine("""    <long name="${escapeXml(entry.key)}" value="${entry.value}" />""")
                }
                SharedPrefType.FLOAT -> {
                    sb.appendLine("""    <float name="${escapeXml(entry.key)}" value="${entry.value}" />""")
                }
                SharedPrefType.BOOLEAN -> {
                    sb.appendLine("""    <boolean name="${escapeXml(entry.key)}" value="${entry.value}" />""")
                }
                SharedPrefType.STRING_SET -> {
                    sb.appendLine("""    <set name="${escapeXml(entry.key)}">""")
                    // Fix #4: Use setValues list instead of splitting a flattened string
                    entry.setValues.forEach { v ->
                        sb.appendLine("""        <string>${escapeXml(v)}</string>""")
                    }
                    sb.appendLine("    </set>")
                }
            }
        }
        sb.appendLine("</map>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // Fix #6: Non-greedy (.*?) to correctly match first </string> on the line
    private val STRING_PATTERN = Regex("""<string name="([^"]*)">(.*?)</string>""")
    private val MULTILINE_STRING_OPEN = Regex("""<string name="([^"]*)">(.*)""")
    private val TYPED_PATTERN = Regex("""<(int|long|float|boolean) name="([^"]*)" value="([^"]*)" />""")
    private val SET_OPEN_PATTERN = Regex("""<set name="([^"]*)">""")
    private val SET_VALUE_PATTERN = Regex("""<string>(.*?)</string>""")
}
