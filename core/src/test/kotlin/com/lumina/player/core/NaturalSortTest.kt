package com.lumina.player.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalSortTest {

    @Test
    fun numbersAreComparedByValue() {
        val sorted = listOf("Bölüm 10", "Bölüm 2", "Bölüm 1", "Bölüm 21").sortedWith(NaturalSort.comparator)
        assertEquals(listOf("Bölüm 1", "Bölüm 2", "Bölüm 10", "Bölüm 21"), sorted)
    }

    @Test
    fun turkishAlphabetOrder() {
        val sorted = listOf("Zeytin", "çiçek", "Can", "Işık", "ılık", "iz", "Şeker", "Su", "ötesi", "Ok")
            .sortedWith(NaturalSort.comparator)
        assertEquals(listOf("Can", "çiçek", "ılık", "Işık", "iz", "Ok", "ötesi", "Su", "Şeker", "Zeytin"), sorted)
    }

    @Test
    fun turkishLower() {
        assertEquals("ıi", NaturalSort.turkishLower("Iİ"))
    }

    @Test
    fun leadingZeros() {
        val sorted = listOf("S01E10", "S01E02", "S01E1").sortedWith(NaturalSort.comparator)
        assertEquals(listOf("S01E1", "S01E02", "S01E10"), sorted)
    }
}
