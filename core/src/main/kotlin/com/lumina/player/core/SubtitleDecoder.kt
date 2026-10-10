package com.lumina.player.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Altyazı kodlama tespiti: UTF-8 / UTF-16 / Windows-1254 / ISO-8859-9.
 * Türkçe karakterlerin (ğüşıöç) bozulmaması için metin UTF-8'e çevrilir.
 */
object SubtitleDecoder {

    data class Result(val text: String, val charset: String)

    private const val TURKISH_CHARS = "ğüşıöçĞÜŞİÖÇ"

    /** Metin tabanlı altyazı mı (VobSub .idx/.sub gibi ikili biçimler değil)? */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (hasUtf16Bom(bytes)) return true
        val n = minOf(bytes.size, 4096)
        for (i in 0 until n) if (bytes[i].toInt() == 0) return false
        return true
    }

    fun decode(bytes: ByteArray): Result {
        // 1. BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE")
        }
        // 2. Geçerli UTF-8 ise olduğu gibi
        strictDecode(bytes, Charsets.UTF_8)?.let { return Result(it, "UTF-8") }

        // 3. Türkçe Windows-1254 / ISO-8859-9 sezgisi
        val cp1254 = charsetOrNull("windows-1254")
        val iso9 = charsetOrNull("ISO-8859-9")
        val candidates = listOfNotNull(cp1254?.let { it to "windows-1254" }, iso9?.let { it to "ISO-8859-9" })
        var best: Result? = null
        var bestScore = -1
        for ((cs, name) in candidates) {
            val text = String(bytes, cs)
            val score = turkishScore(text)
            if (score > bestScore) {
                best = Result(text, name)
                bestScore = score
            }
        }
        if (best != null && bestScore > 0) return best
        // 4. Varsayılan: Windows-1254 (masaüstü sürümle aynı)
        val fallback = cp1254 ?: Charsets.ISO_8859_1
        return Result(String(bytes, fallback), fallback.name())
    }

    private fun turkishScore(text: String): Int {
        var score = 0
        for (c in text) {
            if (c in TURKISH_CHARS) score += 2
            // 1254'te C1 kontrol aralığına düşen karakterler yanlış kodlamaya işaret eder
            if (c in '\u0080'..'\u009F') score -= 5
        }
        return score
    }

    private fun hasUtf16Bom(bytes: ByteArray): Boolean = bytes.size >= 2 &&
        ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))

    private fun strictDecode(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun charsetOrNull(name: String): Charset? = try {
        Charset.forName(name)
    } catch (_: Exception) {
        null
    }
}
