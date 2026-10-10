package com.lumina.player.core

import kotlin.random.Random

enum class PlayMode { SEQUENTIAL, SHUFFLE, REPEAT_ALL, REPEAT_ONE, STOP_AFTER }

/**
 * Çalma listesi mantığı (çalma modları, karışık sıra, sıralama).
 * Platformdan bağımsızdır; iş parçacığı güvenli değildir (ana iş parçacığında kullanılır).
 */
class Playlist(private val random: Random = Random.Default) {

    private val list = mutableListOf<MediaItem>()
    private var shuffleOrder = mutableListOf<Int>()
    private var shufflePos = -1

    val items: List<MediaItem> get() = list.toList()
    val size: Int get() = list.size
    val isEmpty: Boolean get() = list.isEmpty()

    var currentIndex: Int = -1
        private set

    var mode: PlayMode = PlayMode.SEQUENTIAL
        set(value) {
            field = value
            if (value == PlayMode.SHUFFLE) rebuildShuffle()
        }

    val current: MediaItem? get() = list.getOrNull(currentIndex)

    fun setItems(items: List<MediaItem>, startIndex: Int = 0) {
        list.clear()
        list.addAll(items)
        currentIndex = if (list.isEmpty()) -1 else startIndex.coerceIn(0, list.lastIndex)
        rebuildShuffle()
    }

    fun select(index: Int): MediaItem? {
        if (index !in list.indices) return null
        currentIndex = index
        if (mode == PlayMode.SHUFFLE) {
            val pos = shuffleOrder.indexOf(index)
            if (pos >= 0) shufflePos = pos else rebuildShuffle()
        }
        return current
    }

    /**
     * Sonraki öğe.
     * @param auto true ise medya kendiliğinden bitti (tekrar/bitince dur modları uygulanır);
     *             false ise kullanıcı "sonraki"ye bastı.
     */
    fun next(auto: Boolean = false): MediaItem? {
        if (list.isEmpty()) return null
        if (auto && mode == PlayMode.REPEAT_ONE) return current
        if (auto && mode == PlayMode.STOP_AFTER) return null

        if (mode == PlayMode.SHUFFLE) {
            if (shuffleOrder.size != list.size) rebuildShuffle()
            shufflePos++
            if (shufflePos >= shuffleOrder.size) {
                // Liste bitti: yeniden karıştırıp devam et (son çalınan hemen tekrar gelmesin)
                val last = currentIndex
                shuffleOrder = list.indices.shuffled(random).toMutableList()
                if (shuffleOrder.size > 1 && shuffleOrder[0] == last) {
                    shuffleOrder.add(shuffleOrder.removeAt(0))
                }
                shufflePos = 0
            }
            currentIndex = shuffleOrder[shufflePos]
            return current
        }

        var nextIdx = currentIndex + 1
        if (nextIdx > list.lastIndex) {
            if (mode == PlayMode.REPEAT_ALL) nextIdx = 0 else return null
        }
        currentIndex = nextIdx
        return current
    }

    fun previous(): MediaItem? {
        if (list.isEmpty()) return null
        if (mode == PlayMode.SHUFFLE) {
            if (shufflePos > 0) {
                shufflePos--
                currentIndex = shuffleOrder[shufflePos]
            }
            return current
        }
        var prevIdx = currentIndex - 1
        if (prevIdx < 0) prevIdx = if (mode == PlayMode.REPEAT_ALL) list.lastIndex else 0
        currentIndex = prevIdx
        return current
    }

    fun hasNext(): Boolean = when {
        list.isEmpty() -> false
        mode == PlayMode.SHUFFLE || mode == PlayMode.REPEAT_ALL -> true
        else -> currentIndex < list.lastIndex
    }

    fun add(items: List<MediaItem>) {
        if (items.isEmpty()) return
        list.addAll(items)
        if (currentIndex < 0) currentIndex = 0
        rebuildShuffle()
    }

    /** Öğeleri şu an çalınanın hemen arkasına ekler ("Sonra oynat"). */
    fun insertNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val at = if (currentIndex < 0) list.size else currentIndex + 1
        list.addAll(at, items)
        if (currentIndex < 0) currentIndex = 0
        if (mode == PlayMode.SHUFFLE) {
            // Karışık modda da eklenenler sıradaki olarak çalınsın
            rebuildShuffle()
            val inserted = (at until at + items.size).toList()
            shuffleOrder.removeAll(inserted)
            shuffleOrder.addAll(shufflePos + 1, inserted)
        }
    }

    fun remove(index: Int) {
        if (index !in list.indices) return
        list.removeAt(index)
        when {
            list.isEmpty() -> currentIndex = -1
            index < currentIndex -> currentIndex--
            index == currentIndex && currentIndex > list.lastIndex -> currentIndex = list.lastIndex
        }
        rebuildShuffle()
    }

    fun move(from: Int, to: Int) {
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        currentIndex = when {
            currentIndex == from -> to
            currentIndex in (from + 1)..to -> currentIndex - 1
            currentIndex in to until from -> currentIndex + 1
            else -> currentIndex
        }
        rebuildShuffle()
    }

    fun clear() {
        list.clear()
        currentIndex = -1
        shuffleOrder.clear()
        shufflePos = -1
    }

    fun sortBy(comparator: Comparator<MediaItem>) {
        val cur = current
        list.sortWith(comparator)
        if (cur != null) currentIndex = list.indexOf(cur)
        rebuildShuffle()
    }

    fun sortByName() = sortBy { a, b -> NaturalSort.compare(a.title, b.title) }

    private fun rebuildShuffle() {
        shuffleOrder = list.indices.shuffled(random).toMutableList()
        if (currentIndex in list.indices) {
            shuffleOrder.remove(currentIndex)
            shuffleOrder.add(0, currentIndex)
            shufflePos = 0
        } else {
            shufflePos = -1
        }
    }
}
