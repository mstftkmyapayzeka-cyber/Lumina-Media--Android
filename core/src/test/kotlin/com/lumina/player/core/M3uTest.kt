package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {

    @Test
    fun roundTrip() {
        val items = listOf(
            MediaItem("content://media/external/audio/media/5", "Şarkı – Güzel", durationMs = 125_000),
            MediaItem("https://example.com/live.m3u8", "Canlı"),
        )
        val parsed = M3u.parse(M3u.export(items))
        assertEquals(2, parsed.size)
        assertEquals("content://media/external/audio/media/5", parsed[0].location)
        assertEquals("Şarkı – Güzel", parsed[0].title)
        assertEquals(125_000L, parsed[0].durationMs)
        assertEquals(0L, parsed[1].durationMs)
    }

    @Test
    fun relativeEntriesUseResolver() {
        val text = "﻿#EXTM3U\n#EXTINF:10 tvg-id=\"x\",Kanal\nalt/klasor.mp4\n/sdcard/a.mp3\n"
        val parsed = M3u.parse(text) { "/root/$it" }
        assertEquals("/root/alt/klasor.mp4", parsed[0].location)
        assertEquals("Kanal", parsed[0].title)
        assertEquals("/sdcard/a.mp3", parsed[1].location)
        assertNull(parsed[1].title)
        assertEquals(1, M3u.parse("rel.mp4\nhttp://x/y").size)
    }
}
