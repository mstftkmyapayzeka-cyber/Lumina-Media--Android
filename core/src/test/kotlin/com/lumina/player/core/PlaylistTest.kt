package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaylistTest {

    private fun items(n: Int) = (1..n).map { MediaItem("uri$it", "Öğe $it") }

    @Test
    fun sequentialStopsAtEnd() {
        val p = Playlist().apply { setItems(items(3)) }
        assertEquals("uri2", p.next()?.uri)
        assertEquals("uri3", p.next()?.uri)
        assertNull(p.next())
        assertEquals(2, p.currentIndex)
    }

    @Test
    fun repeatAllWraps() {
        val p = Playlist().apply { setItems(items(2), 1); mode = PlayMode.REPEAT_ALL }
        assertEquals("uri1", p.next()?.uri)
        assertEquals("uri2", p.previous()?.uri)
    }

    @Test
    fun repeatOneOnlyWhenAuto() {
        val p = Playlist().apply { setItems(items(3)); mode = PlayMode.REPEAT_ONE }
        assertEquals("uri1", p.next(auto = true)?.uri)
        assertEquals("uri2", p.next(auto = false)?.uri)
    }

    @Test
    fun stopAfterCurrent() {
        val p = Playlist().apply { setItems(items(3)); mode = PlayMode.STOP_AFTER }
        assertNull(p.next(auto = true))
        assertEquals("uri2", p.next(auto = false)?.uri)
    }

    @Test
    fun shuffleVisitsEveryItemOnce() {
        val p = Playlist(Random(42)).apply { setItems(items(10), 3); mode = PlayMode.SHUFFLE }
        val seen = mutableSetOf(p.current!!.uri)
        repeat(9) { seen += p.next()!!.uri }
        assertEquals(10, seen.size)
    }

    @Test
    fun shufflePreviousFollowsHistory() {
        val p = Playlist(Random(7)).apply { setItems(items(6)); mode = PlayMode.SHUFFLE }
        val first = p.current
        val second = p.next()
        p.next()
        assertEquals(second, p.previous())
        assertEquals(first, p.previous())
        assertEquals(first, p.previous())
    }

    @Test
    fun removeKeepsCurrentIndexConsistent() {
        val p = Playlist().apply { setItems(items(4), 2) }
        p.remove(0)
        assertEquals("uri3", p.current?.uri)
        p.remove(p.currentIndex)
        assertEquals("uri4", p.current?.uri)
        p.remove(p.currentIndex)
        assertEquals("uri2", p.current?.uri)
    }

    @Test
    fun moveAndInsertNext() {
        val p = Playlist().apply { setItems(items(4), 1) }
        p.move(1, 3)
        assertEquals(3, p.currentIndex)
        p.move(0, 3)
        assertEquals(2, p.currentIndex)
        p.insertNext(listOf(MediaItem("x", "X")))
        assertEquals("x", p.next()?.uri)
    }

    @Test
    fun sortKeepsCurrent() {
        val p = Playlist().apply {
            setItems(listOf(MediaItem("a", "Bölüm 10"), MediaItem("b", "Bölüm 2"), MediaItem("c", "Bölüm 1")), 0)
        }
        p.sortByName()
        assertEquals(listOf("c", "b", "a"), p.items.map { it.uri })
        assertEquals("a", p.current?.uri)
        assertTrue(p.hasNext().not())
    }
}
