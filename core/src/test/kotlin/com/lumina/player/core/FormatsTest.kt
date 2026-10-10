package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    @Test
    fun extensionAndKind() {
        assertEquals("mkv", Formats.extOf("Film.Adı.MKV"))
        assertEquals("", Formats.extOf("README"))
        assertEquals("m3u8", Formats.extOf("https://x.com/live/index.m3u8?token=1"))
        assertEquals(MediaKind.VIDEO, Formats.kindByName("a.rmvb"))
        assertEquals(MediaKind.AUDIO, Formats.kindByName("b.FLAC"))
        assertNull(Formats.kindByName("c.srt"))
        assertTrue(Formats.isSubtitle("film.tr.srt"))
        assertFalse(Formats.isSubtitle("notlar.txt"))
        assertTrue(Formats.shouldSniff("bozuk.dat"))
        assertFalse(Formats.shouldSniff("kapak.jpg"))
    }

    @Test
    fun streamUrls() {
        assertTrue(Formats.isStreamUrl(" https://example.com/a.m3u8"))
        assertTrue(Formats.isStreamUrl("RTSP://192.168.1.2/cam"))
        assertFalse(Formats.isStreamUrl("content://media/external/video/1"))
        assertEquals("index.m3u8", Formats.displayName("https://x.com/live/index.m3u8?t=1"))
        assertEquals("Bölüm 1", Formats.displayName("/sdcard/Dizi/Bölüm 1.mkv"))
    }

    private fun head(vararg prefix: Int, size: Int = 64): ByteArray =
        ByteArray(size).also { prefix.forEachIndexed { i, b -> it[i] = b.toByte() } }

    private fun ascii(text: String, offset: Int = 0, size: Int = 64): ByteArray =
        ByteArray(size).also { text.toByteArray(Charsets.ISO_8859_1).copyInto(it, offset) }

    @Test
    fun sniffContainers() {
        assertEquals(MediaKind.VIDEO, Formats.sniff(head(0x1A, 0x45, 0xDF, 0xA3)))
        assertEquals(MediaKind.VIDEO, Formats.sniff(ascii("ftypisom", offset = 4)))
        assertEquals(MediaKind.AUDIO, Formats.sniff(ascii("ftypM4A ", offset = 4)))
        assertEquals(MediaKind.VIDEO, Formats.sniff(ascii("RIFF\u0000\u0000\u0000\u0000AVI ")))
        assertEquals(MediaKind.AUDIO, Formats.sniff(ascii("RIFF\u0000\u0000\u0000\u0000WAVE")))
        assertEquals(MediaKind.AUDIO, Formats.sniff(ascii("ID3\u0004")))
        assertEquals(MediaKind.AUDIO, Formats.sniff(ascii("fLaC")))
        assertEquals(MediaKind.VIDEO, Formats.sniff(ascii("FLV\u0001")))
        assertEquals(MediaKind.VIDEO, Formats.sniff(ascii("OggS....theora")))
        assertEquals(MediaKind.AUDIO, Formats.sniff(ascii("OggS....vorbis")))
        assertNull(Formats.sniff(ascii("%PDF-1.7")))
        assertNull(Formats.sniff(ByteArray(4)))
    }

    @Test
    fun sniffMpegTs() {
        val ts = ByteArray(188 * 3)
        ts[0] = 0x47; ts[188] = 0x47; ts[376] = 0x47
        assertEquals(MediaKind.VIDEO, Formats.sniff(ts))
    }

    @Test
    fun resolutionLabel() {
        assertEquals("1080p", MediaItem("u", "t", width = 1920, height = 1080).resolutionLabel)
        assertEquals("4K", MediaItem("u", "t", width = 3840, height = 2160).resolutionLabel)
        assertEquals("720p", MediaItem("u", "t", width = 720, height = 1280).resolutionLabel)
        assertNull(MediaItem("u", "t").resolutionLabel)
    }
}
