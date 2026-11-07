package com.osintpocket.app.data.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Exportación del historial. Sin dependencias de Android: testeable en JVM. */
object HistoryExporter {

    private val HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun human(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        HUMAN.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun toJson(entries: List<HistoryEntry>, appVersion: String, now: Long = System.currentTimeMillis()): String =
        buildString {
            append("{\n")
            append("  \"app\": \"OSINT Pocket\",\n")
            append("  \"version\": ").append(str(appVersion)).append(",\n")
            append("  \"exportedAt\": ").append(str(Instant.ofEpochMilli(now).toString())).append(",\n")
            append("  \"entries\": [")
            entries.forEachIndexed { i, e ->
                append(if (i == 0) "\n" else ",\n")
                append("    {")
                append("\"id\": ").append(e.id).append(", ")
                append("\"module\": ").append(str(e.module)).append(", ")
                append("\"query\": ").append(str(e.query)).append(", ")
                append("\"createdAt\": ").append(str(Instant.ofEpochMilli(e.createdAt).toString())).append(", ")
                append("\"summary\": ").append(str(e.summary)).append(", ")
                append("\"details\": ").append(str(e.details))
                append("}")
            }
            append(if (entries.isEmpty()) "]\n" else "\n  ]\n")
            append("}\n")
        }

    fun toMarkdown(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("# Historial de OSINT Pocket\n\n")
        if (entries.isEmpty()) append("_Sin entradas._\n")
        entries.forEach { e ->
            append("## ").append(e.module).append(" · ").append(mdInline(e.query)).append('\n')
            append('\n').append("*").append(human(e.createdAt, zone)).append("* — ").append(mdInline(e.summary)).append("\n\n")
            if (e.details.isNotBlank()) {
                val fence = if (e.details.contains("```")) "~~~~" else "```"
                append(fence).append('\n').append(e.details.trimEnd()).append('\n').append(fence).append("\n\n")
            }
        }
    }

    /** Cadena JSON con escape completo (comillas, barras, controles y separadores de línea Unicode). */
    fun str(s: String): String = buildString(s.length + 2) {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun mdInline(s: String) = s.replace("\n", " ").replace("|", "\\|").replace("`", "'")
}
