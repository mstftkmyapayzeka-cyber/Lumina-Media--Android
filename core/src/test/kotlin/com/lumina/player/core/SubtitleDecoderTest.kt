package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDecoderTest {

    private val sample = "1\n00:00:01,000 --> 00:00:02,000\nĞüşıöç ÇİĞDEM ışığı\n"

    @Test
    fun utf8IsKept() {
        val r = SubtitleDecoder.decode(sample.toByteArray(Charsets.UTF_8))
        assertEquals("UTF-8", r.charset)
        assertEquals(sample, r.text)
    }

    @Test
    fun utf8BomIsStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sample.toByteArray(Charsets.UTF_8)
        assertEquals(sample, SubtitleDecoder.decode(bytes).text)
    }

    @Test
    fun windows1254IsDetected() {
        val r = SubtitleDecoder.decode(sample.toByteArray(charset("windows-1254")))
        assertEquals(sample, r.text)
    }

    @Test
    fun utf16() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + sample.toByteArray(Charsets.UTF_16LE)
        assertEquals(sample, SubtitleDecoder.decode(bytes).text)
        assertTrue(SubtitleDecoder.looksLikeText(bytes))
    }

    @Test
    fun binaryIsNotText() {
        assertFalse(SubtitleDecoder.looksLikeText(byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte())))
    }
}
