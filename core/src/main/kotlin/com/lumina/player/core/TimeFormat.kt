package com.lumina.player.core

object TimeFormat {
    /** 65_000 → "1:05", 3_725_000 → "1:02:05". */
    fun format(ms: Long): String {
        val totalSec = (if (ms < 0) 0 else ms) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Bayt boyutunu okunur biçime çevirir. */
    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return if (unit <= 1) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
    }
}
