package com.lumina.player.core

/** Masaüstü sürümle aynı "kaldığın yerden devam et" kuralları. */
object ResumePolicy {
    const val WATCHED_RATIO = 0.90

    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs > durationMs * WATCHED_RATIO

    /** Video bitmemişse, en az 5 sn izlenmişse ve sonuna 10 sn'den fazla varsa devam edilir. */
    fun resumePosition(positionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0) return null
        if (isWatched(positionMs, durationMs)) return null
        if (positionMs <= 5_000 || durationMs - positionMs <= 10_000) return null
        return positionMs
    }

    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
