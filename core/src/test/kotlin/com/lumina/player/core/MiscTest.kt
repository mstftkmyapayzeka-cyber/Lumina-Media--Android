package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {

    @Test
    fun timeFormat() {
        assertEquals("0:00", TimeFormat.format(-5))
        assertEquals("1:05", TimeFormat.format(65_000))
        assertEquals("1:02:05", TimeFormat.format(3_725_000))
    }

    @Test
    fun sizeFormat() {
        assertEquals("", TimeFormat.size(0))
        assertEquals("1.5 GB", TimeFormat.size(1_610_612_736).replace(',', '.'))
    }

    @Test
    fun resumePolicy() {
        assertEquals(60_000L, ResumePolicy.resumePosition(60_000, 600_000))
        assertNull(ResumePolicy.resumePosition(3_000, 600_000))      // çok kısa izlendi
        assertNull(ResumePolicy.resumePosition(595_000, 600_000))    // izlendi sayılır
        assertTrue(ResumePolicy.isWatched(550_000, 600_000))
    }
}
