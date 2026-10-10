package com.lumina.player.core

/** M3U / M3U8 çalma listesi okuma ve yazma. */
object M3u {

    data class Entry(val location: String, val title: String?, val durationMs: Long)

    fun export(items: List<MediaItem>): String = buildString {
        append("#EXTM3U\n")
        for (item in items) {
            val sec = if (item.durationMs > 0) item.durationMs / 1000 else -1
            append("#EXTINF:").append(sec).append(',').append(item.title.replace('\n', ' ')).append('\n')
            append(item.uri).append('\n')
        }
    }

    /**
     * @param resolveRelative göreli yolları mutlak konuma çevirir; null dönerse girdi atlanır.
     */
    fun parse(text: String, resolveRelative: (String) -> String? = { null }): List<Entry> {
        val entries = mutableListOf<Entry>()
        var title: String? = null
        var durationMs = 0L
        for (raw in text.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                // Ör: #EXTINF:120 tvg-id="x",Sanatçı - Parça Adı
                val body = line.substring(8)
                val comma = body.indexOf(',')
                val durPart = (if (comma >= 0) body.substring(0, comma) else body).trim().substringBefore(' ')
                durationMs = durPart.toDoubleOrNull()?.let { if (it > 0) (it * 1000).toLong() else 0L } ?: 0L
                title = if (comma >= 0) body.substring(comma + 1).trim().ifEmpty { null } else null
            } else if (!line.startsWith("#")) {
                val location = if (isAbsolute(line)) line else resolveRelative(line)
                if (location != null) entries += Entry(location, title, durationMs)
                title = null
                durationMs = 0
            }
        }
        return entries
    }

    private fun isAbsolute(location: String): Boolean =
        location.startsWith("/") || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(location) ||
            location.startsWith("content:", ignoreCase = true) || location.startsWith("file:", ignoreCase = true)
}
