package com.lumina.player.core

/**
 * Doğal sıralama: "Bölüm 2", "Bölüm 10"dan önce gelir.
 * Türkçe büyük/küçük harf dönüşümü (I → ı, İ → i) dikkate alınır.
 */
object NaturalSort {

    private val CHUNK = Regex("\\d+|\\D+")

    fun turkishLower(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'I' -> 'ı'
                    'İ' -> 'i'
                    else -> c.lowercaseChar()
                }
            )
        }
        return sb.toString()
    }

    fun compare(a: String, b: String): Int {
        val ca = CHUNK.findAll(turkishLower(a)).map { it.value }.toList()
        val cb = CHUNK.findAll(turkishLower(b)).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val xNum = x[0].isDigit()
            val yNum = y[0].isDigit()
            val r = when {
                xNum && yNum -> compareNumbers(x, y)
                xNum -> -1 // sayılar metinden önce
                yNum -> 1
                else -> TURKISH_COLLATOR.compare(x, y)
            }
            if (r != 0) return r
        }
        return ca.size.compareTo(cb.size)
    }

    private fun compareNumbers(x: String, y: String): Int {
        val xs = x.trimStart('0')
        val ys = y.trimStart('0')
        if (xs.length != ys.length) return xs.length.compareTo(ys.length)
        val r = xs.compareTo(ys)
        return if (r != 0) r else x.length.compareTo(y.length)
    }

    /** Türk alfabesi sırası: a b c ç d e f g ğ h ı i j k l m n o ö p r s ş t u ü v y z */
    private val TURKISH_COLLATOR = Comparator<String> { a, b ->
        for (i in 0 until minOf(a.length, b.length)) {
            val r = weight(a[i]).compareTo(weight(b[i]))
            if (r != 0) return@Comparator r
        }
        a.length.compareTo(b.length)
    }

    private const val ALPHABET = "abcçdefgğhıijklmnoöpqrsştuüvwxyz"

    private fun weight(c: Char): Double {
        val idx = ALPHABET.indexOf(c)
        return if (idx >= 0) 'a'.code + idx + 0.5 else c.code.toDouble()
    }

    val comparator: Comparator<String> = Comparator { a, b -> compare(a, b) }
}
